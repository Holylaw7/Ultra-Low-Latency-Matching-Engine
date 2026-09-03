package com.ultralatency.matching.qualification.ga.soak;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Independently reconstructs one paced Quick from its immutable raw artifacts. */
public final class GaPacedEvidenceVerifier {

    private static final String PACING_HEADER =
            "slotOrdinal,scheduledMonotonicNanos,actualOfferMonotonicNanos,status";
    private static final String CAPACITY_HEADER =
            "requestId,commandSequence,offeredNanos,responseCompletedNanos,"
                    + "capacityReleaseNanos,schedulerConsumedNanos,releaseDelayNanos";
    private static final String WAKE_HEADER = "wakeOrdinal,wakeMonotonicNanos";
    private static final List<String> TERMINAL_KEYS = List.of(
            "g6Outcome", "g6FailureCode", "g8Outcome", "g8FailureCode",
            "acceptedCommands", "completedResponses", "gracefulShutdown");
    private static final List<String> SHARED_KEYS = List.of(
            "physicalExecution.id", "controller.gitSha", "candidate.applicationJarSha256",
            "qualification.jarSha256", "invocation.identitySha256", "configuration.identitySha256",
            "run.protocolV2Window", "evidence.measurementStartNanos",
            "evidence.measurementEndNanos", "evidence.measurementDurationNanos",
            "evidence.capacity.maxInFlight", "evidence.capacity.maxPendingWire",
            "evidence.capacity.maxCompletedUndrained", "evidence.capacity.readerWakeCount",
            "evidence.capacity.releaseCount", "evidence.capacity.releaseDelayP50Nanos",
            "evidence.capacity.releaseDelayP90Nanos", "evidence.capacity.releaseDelayP99Nanos",
            "evidence.capacity.releaseDelayMaxNanos");

    private GaPacedEvidenceVerifier() {
    }

    /** Reconstructed values from one run-level evidence root. */
    public record Report(
            Path root,
            int configuredWindow,
            long nominalOfferOpportunities,
            long actualOfferedCommands,
            long missedSchedulerLate,
            long missedWindowFull,
            long acceptedCommands,
            long completedResponses,
            int maximumObservedInFlight,
            int maximumObservedPendingWire,
            int maximumObservedCompletedUndrained,
            long readerWakeCount,
            long capacityReleaseCount,
            long measurementStartNanos,
            long measurementEndNanos,
            long measurementDurationNanos,
            String invocationIdentitySha256) {
    }

    /** Reads either one physical run root or a parent containing exactly one physical run. */
    public static Report verify(final Path supplied) throws IOException {
        final Path root = normalizeRoot(supplied);
        final Map<String, String> g6 = readManifest(root.resolve("g6-run-manifest-v1.txt"));
        final Map<String, String> g8 = readManifest(root.resolve("g8-run-manifest-v1.txt"));
        if (!"G6".equals(g6.get("gate.id")) || !"G8".equals(g8.get("gate.id"))) {
            throw new IOException("paced run does not contain G6/G8 manifests");
        }
        for (String key : SHARED_KEYS) {
            requireSame(g6, g8, key);
            requirePresent(g6, key);
        }
        final int window = positiveInt(g6, "run.protocolV2Window");
        final Path invocationPath = root.resolve("invocation-v1.properties");
        final Map<String, String> invocation = GaQuickInvocation.requireCompletePaced(
                GaQuickInvocation.read(invocationPath));
        final String invocationIdentity = GaQuickInvocation.identity(invocation);
        if (!invocationIdentity.equals(g6.get("invocation.identitySha256"))) {
            throw new IOException("invocation identity does not match manifest");
        }
        requireInvocationBinding(invocation, g6, window);
        final GaSoakMatrix quick = GaSoakMatrix.quick();
        final String expectedConfiguration = quick.configurationIdentitySha256(window);
        if (!expectedConfiguration.equals(g6.get("configuration.identitySha256"))) {
            throw new IOException("Quick configuration identity does not bind window");
        }

        final Boundary boundary = readBoundary(root.resolve("measurement-boundary-v1.txt"));
        if (boundary.durationNanos != quick.duration().toNanos()) {
            throw new IOException("measurement boundary does not represent frozen Quick duration");
        }
        requireLong(g6, "evidence.measurementStartNanos", boundary.startNanos);
        requireLong(g6, "evidence.measurementEndNanos", boundary.endNanos);
        requireLong(g6, "evidence.measurementDurationNanos", boundary.durationNanos);

        final PacingCounts pacing = readPacing(root.resolve("pacing-evidence-v1.csv"), boundary,
                quick);
        final long expectedNominal = Math.multiplyExact(quick.duration().getSeconds(),
                (long) quick.offeredRatePerSecond());
        if (pacing.nominal != expectedNominal
                || pacing.offered + pacing.schedulerLate + pacing.windowFull != pacing.nominal) {
            throw new IOException("pacing evidence does not account for frozen Quick schedule");
        }
        final CapacityData capacity = readCapacity(root.resolve("capacity-evidence-v1.csv"));
        final TerminalData terminal = readTerminal(root.resolve("terminal-evidence-v1.txt"));
        if (!Objects.equals(terminal.g6Outcome, g6.get("evidence.outcome"))
                || !Objects.equals(terminal.g6FailureCode, g6.get("evidence.failureCode"))
                || !Objects.equals(terminal.g8Outcome, g8.get("evidence.outcome"))
                || !Objects.equals(terminal.g8FailureCode, g8.get("evidence.failureCode"))) {
            throw new IOException("terminal result does not match G6/G8 manifests");
        }
        final long manifestAccepted = longValue(g6, "run.commandCount");
        if (manifestAccepted != longValue(g8, "run.commandCount")
                || terminal.acceptedCommands != manifestAccepted
                || terminal.acceptedCommands > pacing.offered
                || capacity.releases.size() != terminal.acceptedCommands) {
            throw new IOException("terminal accepted count is not bound to raw evidence");
        }
        final long readerWakeCount = readReaderWakes(
                root.resolve("reader-wake-evidence-v1.csv"));
        if (capacity.releases.size() != longValue(g6, "evidence.capacity.releaseCount")
                || readerWakeCount != longValue(g6, "evidence.capacity.readerWakeCount")) {
            throw new IOException("raw capacity counts do not match the manifest");
        }
        final int recomputedWire = recomputeWireMaximum(pacing, capacity.releases);
        final int recomputedCompleted = recomputeCompletedMaximum(capacity.releases);
        if (recomputedWire != positiveOrZeroInt(g6, "evidence.capacity.maxInFlight")
                || recomputedWire != positiveOrZeroInt(g6, "evidence.capacity.maxPendingWire")
                || recomputedCompleted != positiveOrZeroInt(g6,
                "evidence.capacity.maxCompletedUndrained")) {
            throw new IOException("raw capacity timeline does not match manifest maxima");
        }
        final List<Long> delays = capacity.releases.stream()
                .map(ReleaseRow::releaseDelayNanos).sorted().toList();
        requireLong(g6, "evidence.capacity.releaseDelayP50Nanos", percentile(delays, 50));
        requireLong(g6, "evidence.capacity.releaseDelayP90Nanos", percentile(delays, 90));
        requireLong(g6, "evidence.capacity.releaseDelayP99Nanos", percentile(delays, 99));
        requireLong(g6, "evidence.capacity.releaseDelayMaxNanos",
                delays.isEmpty() ? 0L : delays.get(delays.size() - 1));

        verifyBinding(root, g6, g8);
        verifyArtifacts(root, g6);
        return new Report(root, window, pacing.nominal, pacing.offered, pacing.schedulerLate,
                pacing.windowFull, terminal.acceptedCommands, terminal.completedResponses,
                recomputedWire, recomputedWire, recomputedCompleted, readerWakeCount,
                capacity.releases.size(), boundary.startNanos, boundary.endNanos,
                boundary.durationNanos, invocationIdentity);
    }

    /** Returns whether all supplied validated runs differ materially only by the window N. */
    public static boolean onlyWindowVaries(final List<Path> runRoots) throws IOException {
        Objects.requireNonNull(runRoots, "runRoots");
        final List<Map<String, String>> invocations = new ArrayList<>();
        for (Path runRoot : runRoots) {
            invocations.add(GaQuickInvocation.requireCompletePaced(GaQuickInvocation.read(
                    normalizeRoot(runRoot).resolve("invocation-v1.properties"))));
        }
        return GaQuickInvocation.onlyWindowVaries(invocations);
    }

    private static void requireInvocationBinding(
            final Map<String, String> invocation,
            final Map<String, String> manifest,
            final int window) throws IOException {
        final List<String> equalKeys = List.of(
                "controller.gitSha", "candidate.applicationJarSha256", "qualification.jarSha256");
        for (String key : equalKeys) {
            if (!Objects.equals(invocation.get(key), manifest.get(key))) {
                throw new IOException("invocation does not bind " + key);
            }
        }
        if (!Integer.toString(window).equals(invocation.get("protocolV2.window"))) {
            throw new IOException("invocation window does not match manifest");
        }
        if (!Objects.equals(manifest.get("configuration.identitySha256"),
                invocation.get("qualification.configurationIdentitySha256"))) {
            throw new IOException("invocation configuration identity does not match manifest");
        }
        final GaSoakMatrix matrix = GaSoakMatrix.quick();
        final Map<String, String> expected = Map.ofEntries(
                Map.entry("quick.version", matrix.version()),
                Map.entry("quick.profile", matrix.profile()),
                Map.entry("quick.seed", Long.toString(matrix.seed())),
                Map.entry("quick.duration", matrix.duration().toString()),
                Map.entry("quick.offeredRatePerSecond",
                        Integer.toString(matrix.offeredRatePerSecond())),
                Map.entry("quick.acceptedFloor", Long.toString(matrix.acceptedFloor())),
                Map.entry("quick.sampleRateHz", Integer.toString(matrix.sampleRateHz())),
                Map.entry("quick.nominalOfferOpportunities", Long.toString(Math.multiplyExact(
                        matrix.duration().getSeconds(), (long) matrix.offeredRatePerSecond()))),
                Map.entry("protocol.version", "v2"),
                Map.entry("protocol.singleSession", "true"),
                Map.entry("protocol.singleProducer", "true"));
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!entry.getValue().equals(invocation.get(entry.getKey()))) {
                throw new IOException("invocation does not bind frozen Quick field "
                        + entry.getKey());
            }
        }
    }

    private static void verifyBinding(
            final Path root,
            final Map<String, String> g6,
            final Map<String, String> g8) throws IOException {
        final GaG6G8PhysicalRunBinding.Fields binding = GaG6G8PhysicalRunBinding.verify(
                root.resolve("ga-g6-g8-physical-run-binding-v1.txt"));
        if (!binding.physicalExecutionId().equals(g6.get("physicalExecution.id"))
                || !binding.g6RunId().equals(g6.get("run.id"))
                || !binding.g8RunId().equals(g8.get("run.id"))
                || !binding.controllerGitSha().equals(g6.get("controller.gitSha"))
                || !binding.candidateApplicationJarSha256().equals(
                g6.get("candidate.applicationJarSha256"))
                || !binding.configurationIdentitySha256().equals(
                g6.get("configuration.identitySha256"))) {
            throw new IOException("physical binding does not match run manifests");
        }
    }

    private static void verifyArtifacts(
            final Path root,
            final Map<String, String> manifest) throws IOException {
        final Path inventory = resolveRelative(root, manifest.get("artifact.inventory.path"));
        final String inventoryHash = QualificationArtifactHasher.sha256(inventory);
        if (!inventoryHash.equals(manifest.get("artifact.inventory.sha256"))) {
            throw new IOException("inventory digest mismatch");
        }
        final Map<String, String> inventoryEntries = readInventory(inventory, root);
        final Map<String, String> manifestEntries = new TreeMap<>();
        for (int index = 1; ; index++) {
            final String prefix = String.format("artifact.%04d", index);
            final String path = manifest.get(prefix + ".path");
            if (path == null) {
                break;
            }
            final Path artifact = resolveRelative(root, path);
            final String digest = QualificationArtifactHasher.sha256(artifact);
            if (!digest.equals(manifest.get(prefix + ".sha256"))
                    || Files.size(artifact) != Long.parseLong(manifest.get(prefix + ".size"))) {
                throw new IOException("raw artifact does not match manifest: " + path);
            }
            if (!digest.equals(inventoryEntries.get(path))) {
                throw new IOException("raw artifact does not match inventory: " + path);
            }
            verifyArtifactSidecar(artifact, digest);
            manifestEntries.put(path, digest);
        }
        if (!manifestEntries.equals(inventoryEntries)) {
            throw new IOException("manifest artifact set does not match inventory");
        }
        final Map<String, String> inventorySidecar = GaEvidenceStore.readArtifactSidecar(
                inventory.resolveSibling(inventory.getFileName() + ".sha256"));
        if (!Map.of(inventory.getFileName().toString(), inventoryHash).equals(inventorySidecar)) {
            throw new IOException("inventory sidecar does not match inventory");
        }
    }

    private static void verifyArtifactSidecar(final Path artifact, final String digest)
            throws IOException {
        final Path sidecar = artifact.resolveSibling(artifact.getFileName() + ".sha256");
        final Map<String, String> values = GaEvidenceStore.readArtifactSidecar(sidecar);
        if (!Map.of(artifact.getFileName().toString(), digest).equals(values)) {
            throw new IOException("raw artifact sidecar mismatch: " + artifact);
        }
    }

    private static Map<String, String> readInventory(final Path inventory, final Path root)
            throws IOException {
        final List<String> lines = Files.readAllLines(inventory, StandardCharsets.UTF_8);
        final Map<String, String> result = new TreeMap<>();
        final List<InventoryEntry> entries = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        for (String line : lines) {
            if (line.length() < 68 || line.charAt(64) != ' ' || line.charAt(65) != ' ') {
                throw new IOException("malformed artifact inventory");
            }
            final String digest = line.substring(0, 64);
            final String path = line.substring(66);
            if (!digest.matches("[0-9a-f]{64}") || path.isBlank()
                    || result.put(path, digest) != null) {
                throw new IOException("invalid artifact inventory entry");
            }
            resolveRelative(root, path);
            final String name = path.substring(path.lastIndexOf('/') + 1);
            if (!names.add(name)) {
                throw new IOException("artifact inventory contains duplicate basenames");
            }
            entries.add(new InventoryEntry(path, digest, name));
        }
        entries.sort(Comparator.comparing(InventoryEntry::path));
        final StringBuilder canonical = new StringBuilder();
        for (InventoryEntry entry : entries) {
            canonical.append(entry.digest()).append("  ").append(entry.path()).append('\n');
        }
        if (!canonical.toString().equals(Files.readString(inventory, StandardCharsets.UTF_8))) {
            throw new IOException("artifact inventory is not canonical");
        }
        return Map.copyOf(result);
    }

    private static Boundary readBoundary(final Path path) throws IOException {
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() != 4 || !lines.get(0).equals(
                "measurement.schema=qualification-measurement-boundary-v1")) {
            throw new IOException("measurement boundary is malformed");
        }
        final long start = keyLong(lines.get(1), "measurement.startNanos");
        final long end = keyLong(lines.get(2), "measurement.endNanos");
        final long duration = keyLong(lines.get(3), "measurement.durationNanos");
        if (start < 0L || end < start || duration != end - start) {
            throw new IOException("measurement boundary chronology is invalid");
        }
        return new Boundary(start, end, duration);
    }

    private static PacingCounts readPacing(
            final Path path,
            final Boundary boundary,
            final GaSoakMatrix quick) throws IOException {
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !PACING_HEADER.equals(lines.get(0))) {
            throw new IOException("pacing evidence header is invalid");
        }
        long expectedOrdinal = 0L;
        long offered = 0L;
        long schedulerLate = 0L;
        long windowFull = 0L;
        final List<Long> offerTimes = new ArrayList<>();
        long previousOffer = -1L;
        final long slotPeriod = 1_000_000_000L / quick.offeredRatePerSecond();
        for (int index = 1; index < lines.size(); index++) {
            final String[] values = lines.get(index).split(",", -1);
            final long ordinal;
            final long scheduled;
            try {
                ordinal = Long.parseLong(values.length == 4 ? values[0] : "-1");
                scheduled = Long.parseLong(values.length == 4 ? values[1] : "-1");
            } catch (NumberFormatException exception) {
                throw new IOException("pacing evidence row contains a non-integer", exception);
            }
            if (values.length != 4 || ordinal != expectedOrdinal) {
                throw new IOException("pacing evidence row is malformed or out of order");
            }
            final long expectedScheduled;
            try {
                expectedScheduled = Math.addExact(boundary.startNanos,
                        Math.multiplyExact(expectedOrdinal, slotPeriod));
            } catch (ArithmeticException exception) {
                throw new IOException("pacing schedule overflows its measurement boundary",
                        exception);
            }
            if (scheduled != expectedScheduled || scheduled < boundary.startNanos
                    || scheduled >= boundary.endNanos) {
                throw new IOException("pacing schedule does not match measurement boundary");
            }
            switch (values[3]) {
                case "OFFERED" -> {
                    if (values[2].isBlank()) {
                        throw new IOException("offered row has no actual timestamp");
                    }
                    final long actual;
                    try {
                        actual = Long.parseLong(values[2]);
                    } catch (NumberFormatException exception) {
                        throw new IOException("offered row timestamp is not an integer", exception);
                    }
                    if (actual < scheduled || actual >= boundary.endNanos
                            || actual < previousOffer) {
                        throw new IOException("offer timestamp is outside its slot boundary");
                    }
                    offerTimes.add(actual);
                    previousOffer = actual;
                    offered++;
                }
                case "MISSED_SCHEDULER_LATE", "MISSED_WINDOW_FULL" -> {
                    if (!values[2].isBlank()) {
                        throw new IOException("missed pacing row contains an offer timestamp");
                    }
                    if ("MISSED_SCHEDULER_LATE".equals(values[3])) {
                        schedulerLate++;
                    } else {
                        windowFull++;
                    }
                }
                default -> throw new IOException("unknown pacing status");
            }
            expectedOrdinal++;
        }
        return new PacingCounts(expectedOrdinal, offered, schedulerLate, windowFull, offerTimes);
    }

    private static CapacityData readCapacity(final Path path) throws IOException {
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !CAPACITY_HEADER.equals(lines.get(0))) {
            throw new IOException("capacity evidence header is invalid");
        }
        final List<ReleaseRow> releases = new ArrayList<>();
        final Set<Long> requestIds = new HashSet<>();
        final Set<Long> sequences = new HashSet<>();
        for (int index = 1; index < lines.size(); index++) {
            final String[] values = lines.get(index).split(",", -1);
            if (values.length != 7) {
                throw new IOException("capacity evidence row is malformed");
            }
            final ReleaseRow row;
            try {
                row = new ReleaseRow(Long.parseLong(values[0]), Long.parseLong(values[1]),
                        Long.parseLong(values[2]), Long.parseLong(values[3]),
                        Long.parseLong(values[4]), Long.parseLong(values[5]),
                        Long.parseLong(values[6]));
            } catch (NumberFormatException exception) {
                throw new IOException("capacity evidence contains a non-integer", exception);
            }
            if (!requestIds.add(row.requestId) || !sequences.add(row.commandSequence)
                    || row.requestId <= 0L || row.commandSequence <= 0L
                    || row.offeredNanos < 0L || row.responseCompletedNanos < row.offeredNanos
                    || row.capacityReleaseNanos < row.responseCompletedNanos
                    || row.schedulerConsumedNanos < row.capacityReleaseNanos
                    || row.releaseDelayNanos != row.capacityReleaseNanos - row.responseCompletedNanos) {
                throw new IOException("capacity evidence chronology or identity is invalid");
            }
            releases.add(row);
        }
        return new CapacityData(List.copyOf(releases));
    }

    private static TerminalData readTerminal(final Path path) throws IOException {
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() != TERMINAL_KEYS.size()) {
            throw new IOException("terminal evidence has an unexpected field count");
        }
        final Map<String, String> values = new TreeMap<>();
        for (int index = 0; index < lines.size(); index++) {
            final String line = lines.get(index);
            final String key = TERMINAL_KEYS.get(index);
            final String prefix = key + "=";
            if (!line.startsWith(prefix) || line.indexOf('=') != line.lastIndexOf('=')) {
                throw new IOException("terminal evidence is not canonical");
            }
            final String value = line.substring(prefix.length());
            if (value.isBlank() || values.put(key, value) != null) {
                throw new IOException("terminal evidence contains a blank/duplicate field");
            }
        }
        final long accepted;
        final long responses;
        try {
            accepted = Long.parseLong(values.get("acceptedCommands"));
            responses = Long.parseLong(values.get("completedResponses"));
        } catch (NumberFormatException exception) {
            throw new IOException("terminal counts are not integers", exception);
        }
        if (accepted < 0L || responses < 0L
                || !("true".equals(values.get("gracefulShutdown"))
                || "false".equals(values.get("gracefulShutdown")))) {
            throw new IOException("terminal counts or shutdown state are invalid");
        }
        return new TerminalData(values.get("g6Outcome"), values.get("g6FailureCode"),
                values.get("g8Outcome"), values.get("g8FailureCode"), accepted, responses);
    }

    private static long readReaderWakes(final Path path) throws IOException {
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !WAKE_HEADER.equals(lines.get(0))) {
            throw new IOException("reader wake evidence header is invalid");
        }
        long expectedOrdinal = 1L;
        long previous = -1L;
        for (int index = 1; index < lines.size(); index++) {
            final String[] values = lines.get(index).split(",", -1);
            final long ordinal;
            final long timestamp;
            try {
                ordinal = Long.parseLong(values.length == 2 ? values[0] : "-1");
                timestamp = Long.parseLong(values.length == 2 ? values[1] : "-1");
            } catch (NumberFormatException exception) {
                throw new IOException("reader wake evidence contains a non-integer", exception);
            }
            if (values.length != 2 || ordinal != expectedOrdinal) {
                throw new IOException("reader wake evidence row is malformed");
            }
            if (timestamp < previous || timestamp < 0L) {
                throw new IOException("reader wake evidence is out of order");
            }
            previous = timestamp;
            expectedOrdinal++;
        }
        return expectedOrdinal - 1L;
    }

    private static int recomputeWireMaximum(
            final PacingCounts pacing,
            final List<ReleaseRow> releases) throws IOException {
        final List<CounterEvent> events = new ArrayList<>();
        for (long timestamp : pacing.offerTimes) {
            events.add(new CounterEvent(timestamp, 1));
        }
        for (ReleaseRow release : releases) {
            events.add(new CounterEvent(release.capacityReleaseNanos, -1));
        }
        events.sort(Comparator.comparingLong(CounterEvent::timestamp)
                .thenComparingInt(CounterEvent::delta));
        return sweep(events, "wire capacity");
    }

    private static int recomputeCompletedMaximum(final List<ReleaseRow> releases)
            throws IOException {
        final List<CounterEvent> events = new ArrayList<>();
        for (ReleaseRow release : releases) {
            events.add(new CounterEvent(release.capacityReleaseNanos, 1));
            events.add(new CounterEvent(release.schedulerConsumedNanos, -1));
        }
        events.sort(Comparator.comparingLong(CounterEvent::timestamp)
                .thenComparing((left, right) -> Integer.compare(right.delta, left.delta)));
        return sweep(events, "completed evidence");
    }

    private static int sweep(final List<CounterEvent> events, final String name) throws IOException {
        int current = 0;
        int maximum = 0;
        for (CounterEvent event : events) {
            current += event.delta;
            if (current < 0) {
                throw new IOException(name + " became negative");
            }
            maximum = Math.max(maximum, current);
        }
        return maximum;
    }

    private static long percentile(final List<Long> sorted, final int percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        final int rank = Math.max(1, (int) Math.ceil(sorted.size() * percentile / 100.0));
        return sorted.get(rank - 1);
    }

    private static Map<String, String> readManifest(final Path path) throws IOException {
        return GaEvidenceStore.read(path, GaEvidenceCodec.Schema.RUN);
    }

    private static Path normalizeRoot(final Path supplied) throws IOException {
        final Path path = Objects.requireNonNull(supplied, "run root")
                .toAbsolutePath().normalize();
        if (Files.isRegularFile(path.resolve("g6-run-manifest-v1.txt"))) {
            return path;
        }
        final List<Path> children;
        try (var stream = Files.list(path)) {
            children = stream.filter(Files::isDirectory)
                    .filter(item -> item.getFileName().toString().startsWith("g6-g8-quick-"))
                    .toList();
        }
        if (children.size() != 1) {
            throw new IOException("run root must identify exactly one physical Quick: " + path);
        }
        return children.get(0);
    }

    private static Path resolveRelative(final Path root, final String value) throws IOException {
        if (value == null || value.isBlank() || value.startsWith("/") || value.contains("\\")) {
            throw new IOException("invalid relative evidence path");
        }
        final Path resolved = root.resolve(value).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            throw new IOException("evidence path is missing or escapes run root: " + value);
        }
        return resolved;
    }

    private static void requirePresent(final Map<String, String> fields, final String key)
            throws IOException {
        if (!fields.containsKey(key)) {
            throw new IOException("manifest is missing " + key);
        }
    }

    private static void requireSame(
            final Map<String, String> first,
            final Map<String, String> second,
            final String key) throws IOException {
        if (!Objects.equals(first.get(key), second.get(key))) {
            throw new IOException("G6/G8 mismatch for " + key);
        }
    }

    private static void requireLong(
            final Map<String, String> fields,
            final String key,
            final long expected) throws IOException {
        if (longValue(fields, key) != expected) {
            throw new IOException("manifest value does not match raw evidence: " + key);
        }
    }

    private static int positiveInt(final Map<String, String> fields, final String key)
            throws IOException {
        final int value = positiveOrZeroInt(fields, key);
        if (value <= 0) {
            throw new IOException(key + " must be positive");
        }
        return value;
    }

    private static int positiveOrZeroInt(final Map<String, String> fields, final String key)
            throws IOException {
        final long value = longValue(fields, key);
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw new IOException(key + " is outside integer bounds");
        }
        return (int) value;
    }

    private static long longValue(final Map<String, String> fields, final String key)
            throws IOException {
        try {
            return Long.parseLong(Objects.requireNonNull(fields.get(key), key));
        } catch (NumberFormatException | NullPointerException exception) {
            throw new IOException(key + " is not a long", exception);
        }
    }

    private static long keyLong(final String line, final String key) throws IOException {
        final String prefix = key + "=";
        if (!line.startsWith(prefix)) {
            throw new IOException("measurement boundary is not canonical");
        }
        try {
            return Long.parseLong(line.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            throw new IOException("measurement boundary value is not a long", exception);
        }
    }

    private record Boundary(long startNanos, long endNanos, long durationNanos) {
    }

    private record PacingCounts(
            long nominal,
            long offered,
            long schedulerLate,
            long windowFull,
            List<Long> offerTimes) {
    }

    private record CapacityData(List<ReleaseRow> releases) {
    }

    private record TerminalData(
            String g6Outcome,
            String g6FailureCode,
            String g8Outcome,
            String g8FailureCode,
            long acceptedCommands,
            long completedResponses) {
    }

    private record ReleaseRow(
            long requestId,
            long commandSequence,
            long offeredNanos,
            long responseCompletedNanos,
            long capacityReleaseNanos,
            long schedulerConsumedNanos,
            long releaseDelayNanos) {
    }

    private record CounterEvent(long timestamp, int delta) {
    }

    private record InventoryEntry(String path, String digest, String basename) {
    }
}
