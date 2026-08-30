package com.ultralatency.matching.qualification.ga.durability;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalCommandCodec;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.persistence.snapshot.OfflineSnapshotGenerator;
import com.ultralatency.matching.persistence.snapshot.SnapshotStore;
import com.ultralatency.matching.qualification.QualificationConfiguration;
import com.ultralatency.matching.qualification.QualificationChildProcess;
import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import com.ultralatency.matching.qualification.QualificationIdentity;
import com.ultralatency.matching.qualification.QualificationProfile;
import com.ultralatency.matching.qualification.ProtocolV1QualificationClient;
import com.ultralatency.matching.qualification.QualificationWorkloadV1;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import com.ultralatency.matching.recovery.online.RecoveryPlanner;
import com.ultralatency.matching.recovery.online.RecoveryResult;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

/** Qualification-only G3 durability, corruption and restart harness. */
public final class GaDurabilityRunner {

    private static final String GATE = "G3";
    private static final String GATE_VERSION = "g3-v1";
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private final GaCorrectnessCanonicalContext configuredContext;

    /** Creates a runner that verifies the frozen candidate for the approved matrix. */
    public GaDurabilityRunner() {
        this(null);
    }

    /** Creates a runner with an explicit identity context for focused tests. */
    public GaDurabilityRunner(final GaCorrectnessCanonicalContext context) {
        configuredContext = context;
    }

    /** Runs the approved G3 matrix. Formal execution remains Human-gated by governance. */
    public GaDurabilityCampaignResult run(final Path outputDirectory) throws IOException {
        return run(GaDurabilityMatrix.approved(), outputDirectory);
    }

    /** Runs one explicit matrix and publishes canonical G3 evidence. */
    public GaDurabilityCampaignResult run(
            final GaDurabilityMatrix matrix,
            final Path outputDirectory) throws IOException {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        final GaCorrectnessCanonicalContext context = configuredContext == null
                ? GaCorrectnessCanonicalContext.fromSystem() : configuredContext;
        if (matrix.isApproved() && !context.isApprovedCandidate()) {
            throw new IOException("approved G3 matrix requires the frozen candidate context");
        }
        final Path root = createRoot(outputDirectory);
        final Instant started = Instant.now();
        final List<GaDurabilityEvidence.RunReference> runs = new ArrayList<>();
        final String matrixConfiguration = matrixConfigurationIdentity(matrix);
        final boolean packCorruption = matrix.lifecycleExecutionCount()
                + matrix.corruptionExecutionCount() > 100;
        CorruptionPack corruptionPack = null;
        for (int cycle = 1; cycle <= matrix.lifecycleCycles(); cycle++) {
            final int segmentSize = matrix.segmentSizeForLifecycleCycle(cycle);
            CorruptionPack currentPack = null;
            if (packCorruption && cycle == 1) {
                final Path cycleDirectory = lifecycleDirectory(root, segmentSize, cycle,
                        cycle > matrix.gracefulCycles());
                corruptionPack = runCorruptionPack(matrix, cycleDirectory);
                currentPack = corruptionPack;
            }
            runs.add(runLifecycleCycle(matrix, root, segmentSize, cycle, context,
                    matrixConfiguration, currentPack));
        }
        if (!packCorruption) {
            for (int segmentSize : matrix.walSegmentSizes()) {
                if (segmentSize < WalCommandCodec.MIN_SEGMENT_SIZE_BYTES) {
                    throw new IOException("approved segment size is below the frozen WAL minimum: "
                            + segmentSize);
                }
                runs.addAll(runCorruption(matrix, root, segmentSize, context,
                        matrixConfiguration));
            }
        }
        final int observedCorruption = packCorruption
                ? corruptionPack == null ? 0 : corruptionPack.executionCount()
                : countCorruptionRuns(root, runs);
        final boolean corruptionPassed = packCorruption
                ? corruptionPack != null && corruptionPack.passed()
                : observedCorruption == matrix.corruptionExecutionCount();
        final boolean passed = runs.size() == (packCorruption
                ? matrix.lifecycleExecutionCount()
                : matrix.lifecycleExecutionCount() + matrix.corruptionExecutionCount())
                && observedCorruption == matrix.corruptionExecutionCount()
                && corruptionPassed
                && runs.stream().allMatch(GaDurabilityEvidence.RunReference::passed);
        final int expectedCampaignRuns = packCorruption
                ? matrix.lifecycleExecutionCount()
                : matrix.lifecycleExecutionCount() + matrix.corruptionExecutionCount();
        final List<GaDurabilityEvidence.Criterion> criteria = List.of(
                new GaDurabilityEvidence.Criterion(
                        "lifecycle-execution-count",
                        Integer.toString(countLifecycleRuns(root, runs)),
                        "EXACT",
                        Integer.toString(matrix.lifecycleExecutionCount()),
                        countLifecycleRuns(root, runs) == matrix.lifecycleExecutionCount()),
                new GaDurabilityEvidence.Criterion(
                        "corruption-fixture-count",
                        Integer.toString(observedCorruption),
                        "EXACT",
                        Integer.toString(matrix.corruptionExecutionCount()),
                        observedCorruption == matrix.corruptionExecutionCount()),
                new GaDurabilityEvidence.Criterion(
                        "all-restart-and-corruption-outcomes",
                        Boolean.toString(runs.stream().allMatch(
                                GaDurabilityEvidence.RunReference::passed)),
                        "EQ",
                        "true",
                        runs.stream().allMatch(GaDurabilityEvidence.RunReference::passed)));
        final Path gate = GaDurabilityEvidence.publishGate(
                root,
                GATE,
                GATE_VERSION,
                runs,
                context,
                started,
                Instant.now(),
                criteria,
                List.of(
                        GaDurabilityEvidence.limitationDurability(),
                        GaDurabilityEvidence.limitationExactlyOnce()));
        final Path campaign = GaDurabilityEvidence.publishCampaign(
                root,
                GATE,
                runs,
                context,
                started,
                Instant.now(),
                expectedCampaignRuns,
                matrixConfiguration,
                passed);
        GaDurabilityEvidence.publishSummary(root, matrix.version(), runs, gate, passed);
        return new GaDurabilityCampaignResult(matrix, runs, passed, root, gate, campaign);
    }

    private static GaDurabilityEvidence.RunReference runLifecycleCycle(
            final GaDurabilityMatrix matrix,
            final Path root,
            final int segmentSize,
            final int cycle,
            final GaCorrectnessCanonicalContext context,
            final String matrixConfiguration,
            final CorruptionPack corruptionPack) throws IOException {
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX,
                matrix.seed(),
                matrix.commandsPerCycle(),
                COMMAND_TIMEOUT,
                root);
        final int first = 0;
        final int end = matrix.commandsPerCycle();
        final boolean forced = cycle > matrix.gracefulCycles();
        final Path cycleDirectory = lifecycleDirectory(root, segmentSize, cycle, forced);
        final Instant started = Instant.now();
        boolean passed = false;
        String failure = "NONE";
        String raw;
        try {
            final Path walDirectory = cycleDirectory.resolve("wal");
            final Path snapshotDirectory = cycleDirectory.resolve("snapshots");
            Files.createDirectories(snapshotDirectory);
            final WalConfiguration wal = new WalConfiguration(
                    walDirectory, segmentSize, WalDurabilityMode.SYNC_EACH_APPEND);
            final QualificationChildProcess child = QualificationChildProcess.start(
                    walDirectory, snapshotDirectory, segmentSize, COMMAND_TIMEOUT);
            int responseCount = 0;
            boolean responseBoundaryObserved = false;
            try (ProtocolV1QualificationClient client = new ProtocolV1QualificationClient(
                    child.address(), COMMAND_TIMEOUT)) {
                for (int index = 0; index < matrix.commandsPerCycle(); index++) {
                    client.exchange(QualificationWorkloadV1.commandAtForRun(configuration, index),
                            index + 1L);
                    responseCount++;
                }
                responseBoundaryObserved = responseCount == matrix.commandsPerCycle();
                if (forced) {
                    child.forceTerminate(COMMAND_TIMEOUT);
                } else {
                    child.gracefulShutdown(COMMAND_TIMEOUT);
                }
                if (forced != child.forceTerminationObserved()) {
                    throw new IOException("lifecycle termination path did not match the matrix");
                }
            } finally {
                child.close();
            }
            final int processAExitCode = child.exitCode();
            final List<EngineCommand> persisted = forced
                    ? readAfterForcedTermination(wal, COMMAND_TIMEOUT)
                    : CommandWalReader.read(wal);
            final QualificationChildProcess recoveryChild = QualificationChildProcess.start(
                    walDirectory, snapshotDirectory, segmentSize, COMMAND_TIMEOUT);
            final long recoveryPid = recoveryChild.pid();
            if (recoveryPid == child.pid()) {
                throw new IOException("recovery process did not cross a process boundary");
            }
            final int recoveryExitCode;
            try {
                recoveryChild.gracefulShutdown(COMMAND_TIMEOUT);
                recoveryExitCode = recoveryChild.exitCode();
            } finally {
                recoveryChild.close();
            }
            final RecoveryResult recovered = RecoveryPlanner.create(wal, snapshotDirectory)
                    .recover(RecoveryMode.PURE_WAL);
            if (!responseBoundaryObserved || persisted.size() != matrix.commandsPerCycle()
                    || recovered.walEndSequence() != matrix.commandsPerCycle()
                    || recovered.nextCommandSequence() != matrix.commandsPerCycle() + 1L) {
                throw new IOException("restart sequence did not converge at cycle " + cycle);
            }
            passed = corruptionPack == null || corruptionPack.passed();
            raw = lifecycleRaw(matrix, segmentSize, cycle, first, end, forced,
                    recovered, persisted.size(), responseCount, child.pid(), processAExitCode,
                    recoveryPid, recoveryExitCode, responseBoundaryObserved,
                    child.forceTerminationObserved());
            if (corruptionPack != null) {
                raw += corruptionPack.rawSummary();
            }
        } catch (final IOException | RuntimeException failureCause) {
            failure = "B2";
            raw = lifecycleFailureRaw(matrix, segmentSize, cycle, first, end, forced,
                    failureCause);
        }
        return GaDurabilityEvidence.publishRun(
                cycleDirectory,
                GATE,
                GATE_VERSION,
                matrix.seed(),
                matrix.commandsPerCycle(),
                segmentSize,
                QualificationWorkloadV1.VERSION,
                context,
                started,
                Instant.now(),
                passed,
                failure,
                raw,
                matrixConfiguration);
    }

    private static List<GaDurabilityEvidence.RunReference> runCorruption(
            final GaDurabilityMatrix matrix,
            final Path root,
            final int segmentSize,
            final GaCorrectnessCanonicalContext context,
            final String matrixConfiguration) throws IOException {
        final List<GaDurabilityEvidence.RunReference> runs = new ArrayList<>();
        for (GaDurabilityFixture fixture : matrix.corruptionFixtures()) {
            final Path fixtureDirectory = root.resolve(String.format(Locale.ROOT,
                    "segment-%d/corruption-%s", segmentSize,
                    fixture.name().toLowerCase(Locale.ROOT)));
            final Instant started = Instant.now();
            boolean passed = false;
            String failure = "NONE";
            String raw;
            try {
                passed = executeFixture(fixture, fixtureDirectory, segmentSize, matrix.seed());
                if (!passed) {
                    failure = "B0";
                }
                raw = "schemaVersion=ga-g3-corruption-fixture-v1\n"
                        + "fixture=" + fixture + "\n"
                        + "segmentSizeBytes=" + segmentSize + "\n"
                        + "expectedOutcome=FAIL_CLOSED\n"
                        + "observedOutcome=" + (passed ? "FAIL_CLOSED" : "UNEXPECTED_ACCEPT") + "\n";
            } catch (final IOException | RuntimeException failureCause) {
                failure = "B2";
                raw = "schemaVersion=ga-g3-corruption-fixture-v1\n"
                        + "fixture=" + fixture + "\n"
                        + "segmentSizeBytes=" + segmentSize + "\n"
                        + "observedOutcome=ERROR\n"
                        + "failureType=" + failureCause.getClass().getName() + "\n";
            }
            runs.add(GaDurabilityEvidence.publishRun(
                    fixtureDirectory,
                    GATE,
                    GATE_VERSION,
                    matrix.seed(),
                    2,
                    segmentSize,
                    QualificationWorkloadV1.VERSION,
                    context,
                    started,
                    Instant.now(),
                    passed,
                    failure,
                    raw,
                    matrixConfiguration));
        }
        return runs;
    }

    /** Packs the approved corruption matrix into one lifecycle run inventory when the global
     * campaign schema's 100-run bound would otherwise be exceeded. */
    private static CorruptionPack runCorruptionPack(
            final GaDurabilityMatrix matrix,
            final Path lifecycleDirectory) throws IOException {
        final Path packDirectory = lifecycleDirectory.resolve("corruption-pack");
        Files.createDirectories(packDirectory);
        final List<String> observations = new ArrayList<>();
        boolean passed = true;
        int count = 0;
        for (int segmentSize : matrix.walSegmentSizes()) {
            if (segmentSize < WalCommandCodec.MIN_SEGMENT_SIZE_BYTES) {
                throw new IOException("corruption pack segment size is below the WAL minimum");
            }
            for (GaDurabilityFixture fixture : matrix.corruptionFixtures()) {
                count++;
                final Path fixtureDirectory = packDirectory.resolve(String.format(Locale.ROOT,
                        "segment-%d/%s", segmentSize,
                        fixture.name().toLowerCase(Locale.ROOT)));
                boolean fixturePassed = false;
                String failure = "NONE";
                try {
                    fixturePassed = executeFixture(fixture, fixtureDirectory, segmentSize,
                            matrix.seed());
                    if (!fixturePassed) {
                        failure = "B0";
                    }
                } catch (final IOException | RuntimeException exception) {
                    failure = "B2";
                }
                passed &= fixturePassed;
                observations.add("fixture=" + fixture
                        + ";segmentSizeBytes=" + segmentSize
                        + ";outcome=" + (fixturePassed ? "FAIL_CLOSED" : "FAIL")
                        + ";failureCode=" + failure);
            }
        }
        final Path result = packDirectory.resolve("corruption-fixture-results-v1.txt");
        final StringBuilder text = new StringBuilder(
                "schemaVersion=ga-g3-corruption-pack-v1\n")
                .append("matrixVersion=").append(matrix.version()).append('\n')
                .append("expectedFixtureCount=").append(matrix.corruptionExecutionCount())
                .append('\n')
                .append("observedFixtureCount=").append(count).append('\n')
                .append("outcome=").append(passed ? "PASS" : "FAIL").append('\n');
        observations.forEach(value -> text.append(value).append('\n'));
        QualificationEvidencePublication.text(result, text.toString());
        return new CorruptionPack(count, passed,
                "corruptionPack.path=" + relativePath(lifecycleDirectory, packDirectory)
                        + "\ncorruptionPack.result="
                        + relativePath(lifecycleDirectory, result) + "\n"
                        + "corruptionPack.count=" + count + "\n"
                        + "corruptionPack.outcome=" + (passed ? "PASS" : "FAIL") + "\n");
    }

    private static boolean executeFixture(
            final GaDurabilityFixture fixture,
            final Path directory,
            final int segmentSize,
            final long seed) throws IOException {
        Files.createDirectories(directory);
        final Path wal = directory.resolve("wal");
        final WalConfiguration configuration = new WalConfiguration(
                wal, segmentSize, WalDurabilityMode.SYNC_EACH_APPEND);
        final QualificationConfiguration workload = new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX,
                seed,
                2,
                COMMAND_TIMEOUT,
                directory);
        final boolean cancelRecord = fixture.name().endsWith("_CANCEL");
        try (CommandWalWriter writer = CommandWalWriter.open(configuration)) {
            writer.append(fixedSubmit(0));
            writer.append(cancelRecord
                    ? new CancelOrderCommand(Sequence.of(2L), OrderId.of(1L))
                    : fixedSubmit(1));
        }
        final Path segment = onlySegment(wal);
        final byte[] bytes = Files.readAllBytes(segment);
        final int recordOffset = cancelRecord
                ? WalCommandCodec.SEGMENT_HEADER_LENGTH + 52
                : WalCommandCodec.SEGMENT_HEADER_LENGTH;
        switch (fixture) {
            case SEGMENT_FIRST_SEQUENCE_MISMATCH -> {
                final Path renamed = configuration.directory().resolve(
                        "wal-00000000000000000002.log");
                Files.move(segment, renamed, StandardCopyOption.REPLACE_EXISTING);
                return expectReaderFailure(configuration, false);
            }
            case SEGMENT_ID_INVALID -> {
                putLong(bytes, 16, 0L);
                return writeAndExpectFailure(segment, bytes, configuration);
            }
            case SEGMENT_FIRST_SEQUENCE_ZERO -> {
                putLong(bytes, 24, 0L);
                return writeAndExpectFailure(segment, bytes, configuration);
            }
            case SEGMENT_MAGIC -> bytes[0] ^= 0x01;
            case SEGMENT_VERSION -> putInt(bytes, 8, 99);
            case SEGMENT_HEADER_LENGTH -> putInt(bytes, 12, 31);
            case SEGMENT_RESERVED_BYTES -> bytes[31] ^= 0x01;
            case RECORD_LENGTH_TOO_SMALL, RECORD_LENGTH_ZERO, RECORD_LENGTH_ZERO_CANCEL -> putInt(
                    bytes, recordOffset, 0);
            case RECORD_LENGTH_TOO_LARGE, RECORD_LENGTH_MAX_PLUS_ONE,
                    RECORD_LENGTH_MAX_PLUS_ONE_CANCEL -> putInt(
                    bytes, recordOffset,
                    WalCommandCodec.MAX_RECORD_LENGTH + 1);
            case RECORD_LENGTH_27, RECORD_LENGTH_27_CANCEL -> putInt(
                    bytes, recordOffset, 27);
            case RECORD_LENGTH_29, RECORD_LENGTH_29_CANCEL -> putInt(
                    bytes, recordOffset, 29);
            case RECORD_LENGTH_51, RECORD_LENGTH_51_CANCEL -> putInt(
                    bytes, recordOffset, 51);
            case RECORD_LENGTH_53, RECORD_LENGTH_53_CANCEL -> putInt(
                    bytes, recordOffset, 53);
            case RECORD_VERSION_OR_TYPE, RECORD_VERSION -> {
                bytes[recordOffset + 4] = 99;
                refreshChecksum(bytes, recordOffset);
            }
            case RECORD_TYPE -> {
                bytes[recordOffset + 5] = 99;
                refreshChecksum(bytes, recordOffset);
            }
            case RECORD_FLAGS -> {
                putShort(bytes, recordOffset + 6, 1);
                refreshChecksum(bytes, recordOffset);
            }
            case RECORD_INVALID_SIDE -> {
                bytes[recordOffset + 24] = 9;
                refreshChecksum(bytes, recordOffset);
            }
            case RECORD_RESERVED_BYTES -> {
                bytes[recordOffset + 6] = 1;
                refreshChecksum(bytes, recordOffset);
            }
            case RECORD_RESERVED_BYTE_1, RECORD_RESERVED_BYTE_2, RECORD_RESERVED_BYTE_3,
                    RECORD_RESERVED_BYTE_4, RECORD_RESERVED_BYTE_5,
                    RECORD_RESERVED_BYTE_6, RECORD_RESERVED_BYTE_7 -> {
                final int reserved = fixture.ordinal()
                        - GaDurabilityFixture.RECORD_RESERVED_BYTE_1.ordinal();
                bytes[recordOffset + 25 + reserved] = 1;
                refreshChecksum(bytes, recordOffset);
            }
            case RECORD_BODY_CHECKSUM, RECORD_BODY_CHECKSUM_SUBMIT,
                    RECORD_BODY_CHECKSUM_CANCEL -> {
                final int bodyOffset = cancelRecord ? recordOffset + 16 : recordOffset + 32;
                bytes[bodyOffset] ^= 0x01;
            }
            case RECORD_STORED_CHECKSUM, RECORD_STORED_CHECKSUM_SUBMIT,
                    RECORD_STORED_CHECKSUM_CANCEL -> bytes[bytes.length - 1] ^= 0x01;
            case DUPLICATE_SEQUENCE, SEQUENCE_GAP, CROSS_SEGMENT_GAP,
                    NON_FINAL_TORN_TAIL, NON_FINAL_TORN_HEADER, NON_FINAL_TORN_BODY,
                    NON_FINAL_TORN_CHECKSUM, FINAL_TORN_TAIL, FINAL_TORN_AFTER_1,
                    FINAL_TORN_AFTER_27, FINAL_TORN_AFTER_28, FINAL_TORN_AFTER_51,
                    SNAPSHOT_CORRUPTION, SNAPSHOT_MAGIC, SNAPSHOT_VERSION,
                    SNAPSHOT_FLAGS, SNAPSHOT_RESERVED, SNAPSHOT_COUNT, SNAPSHOT_LENGTH,
                    SNAPSHOT_CRC, SNAPSHOT_WAL_PREFIX_DIGEST,
                    SNAPSHOT_CHECKPOINT_DIGEST, SNAPSHOT_DUPLICATE_ORDER,
                    SNAPSHOT_NON_CANONICAL_ORDER, SNAPSHOT_NEWER_THAN_WAL,
                    SNAPSHOT_ORPHAN_TEMP, ROTATION_PATH_COLLISION -> {
                return executeStructuralFixture(fixture, directory, configuration, workload);
            }
            default -> throw new IOException("unhandled G3 fixture: " + fixture);
        }
        final boolean lengthFixture = fixture.name().startsWith("RECORD_LENGTH");
        if (lengthFixture) {
            Files.write(segment, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            final boolean finalTail = cancelRecord && (fixture == GaDurabilityFixture.RECORD_LENGTH_29_CANCEL
                    || fixture == GaDurabilityFixture.RECORD_LENGTH_51_CANCEL
                    || fixture == GaDurabilityFixture.RECORD_LENGTH_53_CANCEL);
            return expectReaderFailure(configuration, finalTail);
        }
        return writeAndExpectFailure(segment, bytes, configuration);
    }

    private static boolean writeAndExpectFailure(
            final Path segment,
            final byte[] bytes,
            final WalConfiguration configuration) throws IOException {
        Files.write(segment, bytes, StandardOpenOption.TRUNCATE_EXISTING);
        return expectReaderFailure(configuration, false);
    }

    private static boolean executeStructuralFixture(
            final GaDurabilityFixture fixture,
            final Path directory,
            final WalConfiguration configuration,
            final QualificationConfiguration workload) throws IOException {
        if (fixture == GaDurabilityFixture.ROTATION_PATH_COLLISION) {
            return rotationPathCollisionFails(
                    directory, workload.seed(), configuration.segmentSizeBytes());
        }
        if (fixture == GaDurabilityFixture.DUPLICATE_SEQUENCE
                || fixture == GaDurabilityFixture.SEQUENCE_GAP) {
            final Path segment = onlySegment(configuration.directory());
            final byte[] bytes = Files.readAllBytes(segment);
            final int second = WalCommandCodec.SEGMENT_HEADER_LENGTH + 52;
            final long sequence = fixture == GaDurabilityFixture.DUPLICATE_SEQUENCE ? 1L : 3L;
            ByteBuffer.wrap(bytes, second + 8, Long.BYTES).order(ByteOrder.BIG_ENDIAN)
                    .putLong(sequence);
            refreshChecksum(bytes, second);
            Files.write(segment, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            return expectReaderFailure(configuration, false);
        }
        if (fixture == GaDurabilityFixture.CROSS_SEGMENT_GAP) {
            return crossSegmentGapFails(configuration);
        }
        if (fixture == GaDurabilityFixture.NON_FINAL_TORN_TAIL
                || fixture == GaDurabilityFixture.NON_FINAL_TORN_HEADER
                || fixture == GaDurabilityFixture.NON_FINAL_TORN_BODY
                || fixture == GaDurabilityFixture.NON_FINAL_TORN_CHECKSUM) {
            deleteTree(configuration.directory());
            Files.createDirectories(configuration.directory());
            final int records = recordsPerSegment(configuration.segmentSizeBytes()) + 2;
            try (CommandWalWriter writer = CommandWalWriter.open(configuration)) {
                for (int index = 0; index < records; index++) {
                    writer.append(fixedSubmit(index));
                }
            }
            final Path segment = firstSegment(configuration.directory());
            final int cut = switch (fixture) {
                case NON_FINAL_TORN_HEADER -> 1;
                case NON_FINAL_TORN_BODY -> WalCommandCodec.SEGMENT_HEADER_LENGTH + 1 + 4;
                case NON_FINAL_TORN_CHECKSUM -> WalCommandCodec.SEGMENT_HEADER_LENGTH + 52 - 1;
                default -> WalCommandCodec.SEGMENT_HEADER_LENGTH + 1;
            };
            try (var channel = java.nio.channels.FileChannel.open(segment,
                    StandardOpenOption.WRITE)) {
                channel.truncate(cut);
            }
            return expectReaderFailure(configuration, false);
        }
        if (fixture == GaDurabilityFixture.FINAL_TORN_TAIL
                || fixture == GaDurabilityFixture.FINAL_TORN_AFTER_1
                || fixture == GaDurabilityFixture.FINAL_TORN_AFTER_27
                || fixture == GaDurabilityFixture.FINAL_TORN_AFTER_28
                || fixture == GaDurabilityFixture.FINAL_TORN_AFTER_51) {
            final Path tail = onlySegment(configuration.directory());
            final int cut = switch (fixture) {
                case FINAL_TORN_AFTER_27 -> 27;
                case FINAL_TORN_AFTER_28 -> 28;
                case FINAL_TORN_AFTER_51 -> 51;
                default -> 1;
            };
            try (var channel = java.nio.channels.FileChannel.open(tail,
                    StandardOpenOption.WRITE)) {
                channel.truncate(WalCommandCodec.SEGMENT_HEADER_LENGTH + 52L + cut);
            }
            try (CommandWalWriter writer = CommandWalWriter.reopen(configuration)) {
                writer.append(fixedSubmit(1));
            }
            return CommandWalReader.read(configuration).size() == 2;
        }
        if (fixture == GaDurabilityFixture.SNAPSHOT_CORRUPTION
                || fixture == GaDurabilityFixture.SNAPSHOT_MAGIC
                || fixture == GaDurabilityFixture.SNAPSHOT_VERSION
                || fixture == GaDurabilityFixture.SNAPSHOT_FLAGS
                || fixture == GaDurabilityFixture.SNAPSHOT_RESERVED
                || fixture == GaDurabilityFixture.SNAPSHOT_COUNT
                || fixture == GaDurabilityFixture.SNAPSHOT_LENGTH
                || fixture == GaDurabilityFixture.SNAPSHOT_CRC
                || fixture == GaDurabilityFixture.SNAPSHOT_WAL_PREFIX_DIGEST
                || fixture == GaDurabilityFixture.SNAPSHOT_CHECKPOINT_DIGEST
                || fixture == GaDurabilityFixture.SNAPSHOT_DUPLICATE_ORDER
                || fixture == GaDurabilityFixture.SNAPSHOT_NON_CANONICAL_ORDER
                || fixture == GaDurabilityFixture.SNAPSHOT_NEWER_THAN_WAL
                || fixture == GaDurabilityFixture.SNAPSHOT_ORPHAN_TEMP) {
            final Path snapshots = directory.resolve("snapshots");
            final Path walDirectory = configuration.directory();
            final OfflineSnapshotGenerator generator = new OfflineSnapshotGenerator(
                    configuration, new SnapshotStore(snapshots));
            generator.generate();
            final Path snapshot = new SnapshotStore(snapshots).readLatest().isPresent()
                    ? latestSnapshot(snapshots) : null;
            if (snapshot == null) {
                return false;
            }
            if (fixture == GaDurabilityFixture.SNAPSHOT_ORPHAN_TEMP) {
                Files.writeString(snapshots.resolve("snapshot-00000000000000000001.tmp"),
                        "orphan", StandardOpenOption.CREATE_NEW);
                return new SnapshotStore(snapshots).readLatest().isPresent();
            }
            if (fixture == GaDurabilityFixture.SNAPSHOT_NEWER_THAN_WAL) {
                final Path newer = snapshots.resolve("snapshot-00000000000000000099.bin");
                Files.move(snapshot, newer, StandardCopyOption.REPLACE_EXISTING);
            } else {
                final byte[] snapshotBytes = Files.readAllBytes(snapshot);
                mutateSnapshot(snapshotBytes, fixture);
                Files.write(snapshot, snapshotBytes, StandardOpenOption.TRUNCATE_EXISTING);
            }
            try {
                RecoveryPlanner.create(configuration, snapshots)
                        .recover(RecoveryMode.SNAPSHOT_THEN_WAL);
                return false;
            } catch (final IOException | RuntimeException expected) {
                return true;
            }
        }
        return false;
    }

    private static boolean rotationPathCollisionFails(
            final Path directory,
            final long seed,
            final int segmentSize) throws IOException {
        final Path walDirectory = directory.resolve("rotation-wal");
        final int recordsPerSegment = (segmentSize - WalCommandCodec.SEGMENT_HEADER_LENGTH)
                / 52;
        if (recordsPerSegment < 1) {
            throw new IOException("segment size cannot hold a WAL record");
        }
        final int commandCount = Math.min(
                QualificationConfiguration.MAX_COMMAND_COUNT,
                recordsPerSegment * 2 + 2);
        final QualificationConfiguration workload = new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX,
                seed,
                commandCount,
                COMMAND_TIMEOUT,
                directory);
        final WalConfiguration rotation = new WalConfiguration(
                walDirectory, segmentSize, WalDurabilityMode.SYNC_EACH_APPEND);
        Path collision = null;
        try (CommandWalWriter writer = CommandWalWriter.open(rotation)) {
            Path previous = null;
            for (int index = 0; index < workload.commandCount(); index++) {
                try {
                    writer.append(fixedSubmit(index));
                } catch (final IOException expected) {
                    boolean terminalRejectsNextAppend = false;
                    try {
                        writer.append(fixedSubmit(index + 1));
                    } catch (final IllegalStateException | IOException terminalFailure) {
                        terminalRejectsNextAppend = writer.isTerminal();
                    }
                    return collision != null && writer.isTerminal() && terminalRejectsNextAppend;
                }
                final Path active = writer.activeSegmentPath();
                if (previous != null && !active.equals(previous) && collision == null) {
                    final long nextSequence = writer.nextCommandSequence()
                            + recordsPerSegment - 1L;
                    collision = walDirectory.resolve(String.format(
                            Locale.ROOT, "wal-%020d.log", nextSequence));
                    Files.writeString(collision, "collision", StandardOpenOption.CREATE_NEW);
                }
                previous = active;
            }
            return false;
        } finally {
            if (collision != null) {
                Files.deleteIfExists(collision);
            }
            deleteTree(walDirectory);
        }
    }

    private static boolean crossSegmentGapFails(
            final WalConfiguration configuration) throws IOException {
        deleteTree(configuration.directory());
        Files.createDirectories(configuration.directory());
        final int recordsPerSegment = recordsPerSegment(configuration.segmentSizeBytes());
        final int records = recordsPerSegment + 2;
        try (CommandWalWriter writer = CommandWalWriter.open(configuration)) {
            for (int index = 0; index < records; index++) {
                writer.append(fixedSubmit(index));
            }
        }
        final Path second = secondSegment(configuration.directory());
        final byte[] bytes = Files.readAllBytes(second);
        putLong(bytes, WalCommandCodec.SEGMENT_HEADER_LENGTH + 8, 80L);
        refreshChecksum(bytes, WalCommandCodec.SEGMENT_HEADER_LENGTH);
        Files.write(second, bytes, StandardOpenOption.TRUNCATE_EXISTING);
        return expectReaderFailure(configuration, false);
    }

    private static int recordsPerSegment(final int segmentSize) {
        return (segmentSize - WalCommandCodec.SEGMENT_HEADER_LENGTH) / 52;
    }

    private static Path secondSegment(final Path directory) throws IOException {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "wal-*.log")) {
            final List<Path> segments = new ArrayList<>();
            paths.forEach(segments::add);
            segments.sort(java.util.Comparator.comparing(path -> path.getFileName().toString()));
            if (segments.size() < 2) {
                throw new IOException("fixture did not create a second WAL segment");
            }
            return segments.get(1);
        }
    }

    private static void mutateSnapshot(
            final byte[] bytes,
            final GaDurabilityFixture fixture) {
        if (fixture == GaDurabilityFixture.SNAPSHOT_DUPLICATE_ORDER) {
            putLong(bytes, 128 + 48, 1L);
            return;
        }
        final int offset = switch (fixture) {
            case SNAPSHOT_MAGIC -> 0;
            case SNAPSHOT_VERSION -> 8;
            case SNAPSHOT_LENGTH -> 16;
            case SNAPSHOT_COUNT -> 48;
            case SNAPSHOT_FLAGS -> 60;
            case SNAPSHOT_WAL_PREFIX_DIGEST -> 64;
            case SNAPSHOT_CHECKPOINT_DIGEST -> 96;
            case SNAPSHOT_RESERVED -> 137;
            // The generated fixture contains two BUY orders.  Turning the first one into
            // an ASK makes the remaining BUY appear after the ASK partition, which is
            // rejected by SnapshotCodec's canonical bid-then-ask ordering check.
            case SNAPSHOT_NON_CANONICAL_ORDER -> 128 + 8;
            case SNAPSHOT_CRC, SNAPSHOT_CORRUPTION -> bytes.length - 1;
            default -> bytes.length - 1;
        };
        bytes[Math.min(bytes.length - 1, offset)] ^= 0x01;
    }

    private static void deleteTree(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (final IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            });
        } catch (final java.io.UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private static EngineCommand fixedSubmit(final int index) {
        return new SubmitLimitCommand(
                Sequence.of(index + 1L),
                OrderId.of(index + 1L),
                Side.BUY,
                Price.of(100L),
                Quantity.of(1L));
    }

    private static boolean expectReaderFailure(
            final WalConfiguration configuration,
            final boolean incompleteTail) throws IOException {
        try {
            CommandWalReader.read(configuration);
            return false;
        } catch (final com.ultralatency.matching.persistence.wal.WalCorruptionException expected) {
            return expected.incompleteTail() == incompleteTail;
        }
    }

    private static List<EngineCommand> readAfterForcedTermination(
            final WalConfiguration configuration,
            final Duration timeout) throws IOException {
        final long deadline = System.nanoTime() + timeout.toNanos();
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                return CommandWalReader.read(configuration);
            } catch (final IOException failure) {
                lastFailure = failure;
                LockSupport.parkNanos(Duration.ofMillis(25).toNanos());
            }
        }
        throw new IOException("WAL did not become readable after forced termination", lastFailure);
    }

    private static Path onlySegment(final Path directory) throws IOException {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "wal-*.log")) {
            final List<Path> segments = new ArrayList<>();
            paths.forEach(segments::add);
            if (segments.size() != 1) {
                throw new IOException("fixture requires exactly one WAL segment");
            }
            return segments.get(0);
        }
    }

    private static Path firstSegment(final Path directory) throws IOException {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "wal-*.log")) {
            Path first = null;
            for (Path path : paths) {
                if (first == null || path.getFileName().toString()
                        .compareTo(first.getFileName().toString()) < 0) {
                    first = path;
                }
            }
            if (first == null) {
                throw new IOException("fixture did not create a WAL segment");
            }
            return first;
        }
    }

    private static Path latestSnapshot(final Path directory) throws IOException {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "snapshot-*.bin")) {
            Path latest = null;
            for (Path path : paths) {
                if (latest == null || path.getFileName().toString()
                        .compareTo(latest.getFileName().toString()) > 0) {
                    latest = path;
                }
            }
            if (latest == null) {
                throw new IOException("fixture did not create a Snapshot");
            }
            return latest;
        }
    }

    private static void putInt(final byte[] bytes, final int offset, final int value) {
        ByteBuffer.wrap(bytes, offset, Integer.BYTES).order(ByteOrder.BIG_ENDIAN).putInt(value);
    }

    private static void putShort(final byte[] bytes, final int offset, final int value) {
        ByteBuffer.wrap(bytes, offset, Short.BYTES).order(ByteOrder.BIG_ENDIAN)
                .putShort((short) value);
    }

    private static void putLong(final byte[] bytes, final int offset, final long value) {
        ByteBuffer.wrap(bytes, offset, Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value);
    }

    private static void refreshChecksum(final byte[] bytes, final int recordOffset) {
        final int recordLength = ByteBuffer.wrap(bytes, recordOffset, Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN).getInt();
        final int checksumOffset = recordOffset + recordLength - Integer.BYTES;
        final java.util.zip.CRC32C checksum = new java.util.zip.CRC32C();
        checksum.update(bytes, recordOffset + Integer.BYTES,
                recordLength - Integer.BYTES - Integer.BYTES);
        ByteBuffer.wrap(bytes, checksumOffset, Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN).putInt((int) checksum.getValue());
    }

    private static String lifecycleRaw(
            final GaDurabilityMatrix matrix,
            final int segmentSize,
            final int cycle,
            final int first,
            final int end,
            final boolean forced,
            final RecoveryResult recovered,
            final int persisted,
            final int responseCount,
            final long processAPid,
            final int processAExitCode,
            final long recoveryPid,
            final int recoveryExitCode,
            final boolean responseBoundaryObserved,
            final boolean forcedTerminationObserved) {
        return "schemaVersion=ga-g3-lifecycle-cycle-v1\n"
                + "matrixVersion=" + matrix.version() + "\n"
                + "segmentSizeBytes=" + segmentSize + "\n"
                + "cycle=" + cycle + "\n"
                + "firstCommandIndex=" + first + "\n"
                + "lastCommandIndex=" + (end - 1) + "\n"
                + "commandsPerCycle=" + matrix.commandsPerCycle() + "\n"
                + "termination=" + (forced ? "FORCED_AFTER_COMPLETED_RESPONSE" : "GRACEFUL") + "\n"
                + "processA.pid=" + processAPid + "\n"
                + "processA.exitCode=" + processAExitCode + "\n"
                + "processB.pid=" + recoveryPid + "\n"
                + "processB.exitCode=" + recoveryExitCode + "\n"
                + "responseBoundaryObserved=" + responseBoundaryObserved + "\n"
                + "responseCount=" + responseCount + "\n"
                + "restartRecoveryReady=true\n"
                + "forcedTerminationObserved=" + forcedTerminationObserved + "\n"
                + "persistedCommands=" + persisted + "\n"
                + "walEndSequence=" + recovered.walEndSequence() + "\n"
                + "nextCommandSequence=" + recovered.nextCommandSequence() + "\n"
                + "checkpointDigestSha256=" + recovered.checkpointDigestHex() + "\n"
                + "claim.exactlyOnce=NOT_CLAIMED\n"
                + "claim.hardwarePowerLoss=NOT_CLAIMED\n";
    }

    private static String lifecycleFailureRaw(
            final GaDurabilityMatrix matrix,
            final int segmentSize,
            final int cycle,
            final int first,
            final int end,
            final boolean forced,
            final Throwable failure) {
        return "schemaVersion=ga-g3-lifecycle-cycle-v1\n"
                + "matrixVersion=" + matrix.version() + "\n"
                + "segmentSizeBytes=" + segmentSize + "\n"
                + "cycle=" + cycle + "\n"
                + "firstCommandIndex=" + first + "\n"
                + "lastCommandIndex=" + (end - 1) + "\n"
                + "termination=" + (forced ? "FORCED_AFTER_COMPLETED_RESPONSE" : "GRACEFUL") + "\n"
                + "status=FAIL\n"
                + "failureType=" + failure.getClass().getName() + "\n"
                + "claim.exactlyOnce=NOT_CLAIMED\n"
                + "claim.hardwarePowerLoss=NOT_CLAIMED\n";
    }

    private static Path createRoot(final Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        return Files.createDirectory(outputDirectory.toAbsolutePath().normalize()
                .resolve("ga-g3-" + UUID.randomUUID()));
    }

    private static Path lifecycleDirectory(
            final Path root,
            final int segmentSize,
            final int cycle,
            final boolean forced) {
        return root.resolve(String.format(Locale.ROOT,
                "segment-%d/lifecycle-%03d-%s", segmentSize, cycle,
                forced ? "forced" : "graceful"));
    }

    private static String relativePath(final Path root, final Path path) {
        return root.toAbsolutePath().normalize().relativize(
                path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String matrixConfigurationIdentity(final GaDurabilityMatrix matrix) {
        final TreeMap<String, String> fields = new TreeMap<>();
        fields.put("gate.id", GATE);
        fields.put("gate.version", GATE_VERSION);
        fields.put("matrix.version", matrix.version());
        fields.put("matrix.segmentSizesBytes", matrix.walSegmentSizes().toString());
        fields.put("matrix.gracefulCycles", Integer.toString(matrix.gracefulCycles()));
        fields.put("matrix.forcedCycles", Integer.toString(matrix.forcedCycles()));
        fields.put("matrix.commandsPerCycle", Integer.toString(matrix.commandsPerCycle()));
        fields.put("matrix.seed", Long.toString(matrix.seed()));
        fields.put("matrix.corruptionFixtures", matrix.corruptionFixtures().toString());
        fields.put("workload.profile", "LIFECYCLE_MIX");
        fields.put("workload.version", QualificationWorkloadV1.VERSION);
        return QualificationIdentity.digest(fields);
    }

    private static int countLifecycleRuns(
            final Path root,
            final List<GaDurabilityEvidence.RunReference> runs) {
        return (int) runs.stream().filter(reference -> {
            final Path relative = root.toAbsolutePath().normalize()
                    .relativize(reference.manifestPath().toAbsolutePath().normalize());
            return relative.getNameCount() > 1
                    && relative.getName(1).toString().startsWith("lifecycle-");
        }).count();
    }

    private static int countCorruptionRuns(
            final Path root,
            final List<GaDurabilityEvidence.RunReference> runs) {
        return (int) runs.stream().filter(reference -> {
            final Path relative = root.toAbsolutePath().normalize()
                    .relativize(reference.manifestPath().toAbsolutePath().normalize());
            return relative.getNameCount() > 1
                    && relative.getName(1).toString().startsWith("corruption-");
        }).count();
    }

    private record CorruptionPack(int executionCount, boolean passed, String rawSummary) {
    }
}
