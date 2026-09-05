package com.ultralatency.matching.qualification.ga;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/** Strict canonical codec and schema validator for Phase 11 GA evidence. */
public final class GaEvidenceCodec {

    /** Supported GA evidence schema families. */
    public enum Schema {
        /** Per-run manifest. */
        RUN("ga-run-manifest-v1"),
        /** Per-gate result. */
        GATE("ga-gate-result-v1"),
        /** Multi-run campaign summary. */
        CAMPAIGN("ga-campaign-summary-v1"),
        /** Draft release manifest. */
        RELEASE("ga-release-manifest-v1");

        private final String version;

        Schema(final String versionValue) {
            version = versionValue;
        }

        /** Returns the wire schema identifier. */
        public String version() {
            return version;
        }
    }

    private static final int MAX_DOCUMENT_BYTES = 1_048_576;
    private static final int MAX_FIELDS = 4096;
    private static final int MAX_VALUE_BYTES = 4096;
    private static final int MAX_LINE_BYTES = 12_290;
    private static final Pattern KEY = Pattern.compile(
            "[a-z][a-zA-Z0-9]*(\\.[a-zA-Z0-9]+)*");
    private static final Pattern GIT_SHA1 = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern INTEGER = Pattern.compile("0|[1-9][0-9]*");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Set<String> GATES = Set.of(
            "G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8", "G9", "G10", "G11", "G12");

    private GaEvidenceCodec() {
    }

    /** Encodes and validates one evidence document. */
    public static byte[] encode(final Schema schema, final Map<String, String> fields) {
        Objects.requireNonNull(schema, "schema");
        final Map<String, String> copy = new TreeMap<>(Objects.requireNonNull(fields, "fields"));
        validate(schema, copy);
        return encodeCanonical(copy);
    }

    /** Decodes, validates and returns one canonical evidence document. */
    public static Map<String, String> decode(final Schema schema, final byte[] bytes) {
        Objects.requireNonNull(schema, "schema");
        final byte[] input = Objects.requireNonNull(bytes, "bytes");
        if (input.length > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("evidence document is too large");
        }
        final Map<String, String> fields = parseCanonical(input);
        validate(schema, fields);
        if (!MessageDigest.isEqual(input, encodeCanonical(new TreeMap<>(fields)))) {
            throw new IllegalArgumentException("evidence document is not canonical");
        }
        return Map.copyOf(fields);
    }

    /** Returns the lowercase SHA-256 of a validated canonical document. */
    public static String sha256(final Schema schema, final Map<String, String> fields) {
        return digest(encode(schema, fields));
    }

    /** Returns the lowercase SHA-256 of canonical bytes after strict validation. */
    public static String sha256Bytes(final Schema schema, final byte[] bytes) {
        decode(schema, bytes);
        return digest(bytes);
    }

    /** Returns a copy with the schema field set to the selected schema version. */
    public static Map<String, String> withSchema(
            final Schema schema, final Map<String, String> fields) {
        final Map<String, String> result = new TreeMap<>(Objects.requireNonNull(fields, "fields"));
        result.put("schema.version", schema.version());
        return Map.copyOf(result);
    }

    private static void validate(final Schema schema, final Map<String, String> fields) {
        if (fields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("evidence field count exceeds limit");
        }
        if (!schema.version().equals(fields.get("schema.version"))) {
            throw new IllegalArgumentException("unexpected evidence schema version");
        }
        fields.forEach((key, value) -> {
            validateKey(key);
            validateValue(value);
        });
        switch (schema) {
            case RUN -> validateRun(fields);
            case GATE -> validateGate(fields);
            case CAMPAIGN -> validateCampaign(fields);
            case RELEASE -> validateRelease(fields);
        }
    }

    private static void validateRun(final Map<String, String> fields) {
        final Set<String> fixed = Set.of(
                "campaign.id",
                "candidate.applicationJarSha256", "candidate.productionSha",
                "candidate.productionTreeSha256", "candidate.tag", "candidate.tagObjectSha",
                "comparability.identitySha256", "configuration.identitySha256",
                "controller.gitSha", "evidence.completedAtUtc", "evidence.elapsedMillis",
                "evidence.failureCode", "evidence.failureDigestSha256", "evidence.outcome",
                "evidence.capacity.maxCompletedUndrained", "evidence.capacity.maxInFlight",
                "evidence.capacity.maxPendingWire", "evidence.capacity.readerWakeCount",
                "evidence.capacity.releaseCount", "evidence.capacity.releaseDelayP50Nanos",
                "evidence.capacity.releaseDelayP90Nanos", "evidence.capacity.releaseDelayP99Nanos",
                "evidence.capacity.releaseDelayMaxNanos", "evidence.measurementStartNanos",
                "evidence.measurementEndNanos", "evidence.measurementDurationNanos",
                "evidence.startedAtUtc", "gate.id", "gate.version", "run.commandCount",
                "run.formal", "run.id", "run.loadModel", "run.measurementDurationNanos",
                "run.profile", "run.protocol", "run.protocolV2Window", "run.walMode",
                "run.warmupDurationNanos", "physicalExecution.id",
                "qualification.jarSha256", "invocation.identitySha256", "run.seed", "runtime.cpuModel",
                "runtime.filesystem", "runtime.gcCollectors", "runtime.heapMaxBytes",
                "runtime.javaRuntimeVersion", "runtime.javaVendor", "runtime.javaVmArguments",
                "runtime.javaVmName", "runtime.javaVmVersion", "runtime.logicalProcessors",
                "runtime.nettyAllocator", "runtime.osArch", "runtime.osName",
                "runtime.osVersion", "runtime.storageIdentity", "workload.version",
                "schema.version", "artifact.inventory.path", "artifact.inventory.sha256",
                "artifact.inventory.size");
        rejectUnknownFields(fields, fixed, Set.of("artifact."));
        final int artifactMembers = requireExactBaseAndFamily(
                fields, fixed, "artifact.", Set.of("path", "sha256", "size"), 1, 1000);
        requireSha256(fields, "candidate.applicationJarSha256");
        requireGitSha1(fields, "candidate.productionSha");
        requireSha256(fields, "candidate.productionTreeSha256");
        requireGitSha1(fields, "candidate.tagObjectSha");
        requireSha256(fields, "comparability.identitySha256");
        requireSha256(fields, "configuration.identitySha256");
        requireGitSha1(fields, "controller.gitSha");
        requireSha256(fields, "evidence.failureDigestSha256");
        requireSha256(fields, "artifact.inventory.sha256");
        requireInteger(fields, "artifact.inventory.size");
        rejectRelativePath(fields.get("artifact.inventory.path"));
        requireSize(fields, "artifact.inventory.size");
        for (int index = 1; index <= artifactMembers; index++) {
            final String prefix = familyPrefix("artifact", index);
            rejectRelativePath(fields.get(prefix + ".path"));
            requireSha256(fields, prefix + ".sha256");
            requireInteger(fields, prefix + ".size");
            requireSize(fields, prefix + ".size");
        }
        requireEnum(fields, "evidence.outcome", Set.of("PASS", "FAIL", "ABORTED"));
        requireEnum(fields, "evidence.failureCode", Set.of("NONE", "B0", "B1", "B2", "B3", "B4"));
        requireEnum(fields, "gate.id", GATES);
        requireToken(fields, "gate.version");
        requireEnum(fields, "run.profile", Set.of(
                "LIFECYCLE_MIX", "CROSSING_MULTI_MATCH", "RESTING_DEPTH",
                "MEMORY_STEADY_STATE_V1"));
        requireInteger(fields, "evidence.elapsedMillis");
        requireInteger(fields, "run.commandCount");
        requireInteger(fields, "run.seed");
        requireUuid(fields, "run.id");
        requireInstant(fields, "evidence.startedAtUtc");
        requireInstant(fields, "evidence.completedAtUtc");
        requireOptionalUuid(fields, "physicalExecution.id");
        requireOptionalUuid(fields, "campaign.id");
        requireOptionalSha256(fields, "qualification.jarSha256");
        requireOptionalSha256(fields, "invocation.identitySha256");
        requireOptionalInteger(fields, "run.protocolV2Window");
        requireOptionalInteger(fields, "run.measurementDurationNanos");
        requireOptionalInteger(fields, "run.warmupDurationNanos");
        requireOptionalBoolean(fields, "run.formal");
        for (String key : Set.of(
                "evidence.capacity.maxCompletedUndrained",
                "evidence.capacity.maxInFlight", "evidence.capacity.maxPendingWire",
                "evidence.capacity.readerWakeCount", "evidence.capacity.releaseCount",
                "evidence.capacity.releaseDelayP50Nanos", "evidence.capacity.releaseDelayP90Nanos",
                "evidence.capacity.releaseDelayP99Nanos", "evidence.capacity.releaseDelayMaxNanos",
                "evidence.measurementStartNanos", "evidence.measurementEndNanos",
                "evidence.measurementDurationNanos")) {
            requireOptionalInteger(fields, key);
        }
        if ("PASS".equals(fields.get("evidence.outcome"))) {
            if (!"NONE".equals(fields.get("evidence.failureCode"))) {
                throw new IllegalArgumentException("PASS run must use failureCode NONE");
            }
        } else if ("NONE".equals(fields.get("evidence.failureCode"))) {
            throw new IllegalArgumentException("failed run must have a failure code");
        }
    }

    private static void validateGate(final Map<String, String> fields) {
        final Set<String> fixed = Set.of(
                "blocker.classification", "candidate.applicationJarSha256",
                "candidate.productionSha", "candidate.productionTreeSha256", "candidate.tag",
                "candidate.tagObjectSha", "campaign.id", "campaign.gate",
                "comparability.identitySha256",
                "configuration.identitySha256", "controller.gitSha", "criterion.count",
                "evidence.completedAtUtc", "evidence.outcome", "evidence.startedAtUtc", "gate.id",
                "gate.version", "limitation.count", "manifest.count", "schema.version");
        rejectUnknownFields(fields, fixed, Set.of("criterion.", "limitation.", "manifest."));
        final int criterionMembers = requireExactBaseAndFamily(fields, fixed, "criterion.",
                Set.of("id", "actual", "operator", "required", "result"), 1, 1000);
        final int limitationMembers = requireExactBaseAndFamily(fields, fixed, "limitation.",
                Set.of("code", "statementDigestSha256"), 0, 1000);
        final int manifestMembers = requireExactBaseAndFamily(fields, fixed, "manifest.",
                Set.of("path", "sha256"), 1, 1000);
        requireSha256(fields, "candidate.applicationJarSha256");
        requireGitSha1(fields, "candidate.productionSha");
        requireSha256(fields, "candidate.productionTreeSha256");
        requireGitSha1(fields, "candidate.tagObjectSha");
        requireSha256(fields, "comparability.identitySha256");
        requireSha256(fields, "configuration.identitySha256");
        requireGitSha1(fields, "controller.gitSha");
        requireOptionalUuid(fields, "campaign.id");
        requireOptionalToken(fields, "campaign.gate");
        requireInteger(fields, "criterion.count");
        requireInteger(fields, "limitation.count");
        requireInteger(fields, "manifest.count");
        if (integer(fields, "criterion.count") != criterionMembers
                || integer(fields, "limitation.count") != limitationMembers
                || integer(fields, "manifest.count") != manifestMembers) {
            throw new IllegalArgumentException("schema family count does not match field count");
        }
        requireEnum(fields, "blocker.classification", Set.of("NONE", "B0", "B1", "B2", "B3", "B4"));
        requireEnum(fields, "evidence.outcome", Set.of("PASS", "FAIL", "ABORTED"));
        requireEnum(fields, "gate.id", GATES);
        requireToken(fields, "gate.version");
        requireInstant(fields, "evidence.startedAtUtc");
        requireInstant(fields, "evidence.completedAtUtc");
        for (int index = 1; index <= integer(fields, "criterion.count"); index++) {
            final String prefix = familyPrefix("criterion", index);
            requireEnum(fields, prefix + ".operator",
                    Set.of("EQ", "NE", "LT", "LE", "GT", "GE", "EXACT", "ZERO"));
            requireEnum(fields, prefix + ".result", Set.of("PASS", "FAIL"));
        }
        if ("PASS".equals(fields.get("evidence.outcome"))) {
            if (!"NONE".equals(fields.get("blocker.classification"))) {
                throw new IllegalArgumentException("PASS gate must use blocker NONE");
            }
            for (int index = 1; index <= integer(fields, "criterion.count"); index++) {
                if (!"PASS".equals(fields.get(familyPrefix("criterion", index) + ".result"))) {
                    throw new IllegalArgumentException("PASS gate has failing criterion");
                }
            }
        } else if ("NONE".equals(fields.get("blocker.classification"))) {
            throw new IllegalArgumentException("failed gate must have a blocker classification");
        }
    }

    private static void validateCampaign(final Map<String, String> fields) {
        final Set<String> fixed = Set.of(
                "campaign.completedAtUtc", "campaign.configurationIdentityEqual", "campaign.id",
                "campaign.outcome", "campaign.requiredRunCount", "campaign.startedAtUtc",
                "campaign.validRunCount", "candidate.applicationJarSha256", "candidate.productionSha",
                "candidate.tag", "candidate.tagObjectSha", "comparability.policy",
                "controller.gitSha", "gate.id", "gate.version", "run.count", "schema.version");
        rejectUnknownFields(fields, fixed, Set.of("run."));
        final int runMembers = requireCampaignRunFamily(fields, fixed, 1, 100);
        requireSha256(fields, "candidate.applicationJarSha256");
        requireGitSha1(fields, "candidate.productionSha");
        requireGitSha1(fields, "candidate.tagObjectSha");
        requireGitSha1(fields, "controller.gitSha");
        requireInteger(fields, "campaign.requiredRunCount");
        requireInteger(fields, "campaign.validRunCount");
        requireInteger(fields, "run.count");
        if (integer(fields, "run.count") != runMembers) {
            throw new IllegalArgumentException("run.count does not match run family");
        }
        if (integer(fields, "campaign.requiredRunCount") < 1
                || integer(fields, "campaign.requiredRunCount") > 100
                || integer(fields, "campaign.validRunCount") < 0
                || integer(fields, "campaign.validRunCount")
                > integer(fields, "campaign.requiredRunCount")) {
            throw new IllegalArgumentException("campaign run counts are out of range");
        }
        requireBoolean(fields, "campaign.configurationIdentityEqual");
        requireEnum(fields, "campaign.outcome", Set.of("PASS", "FAIL", "ABORTED"));
        requireEnum(fields, "gate.id", GATES);
        requireOptionalToken(fields, "gate.version");
        requireInstant(fields, "campaign.startedAtUtc");
        requireInstant(fields, "campaign.completedAtUtc");
        for (int index = 1; index <= integer(fields, "run.count"); index++) {
            final String prefix = familyPrefix("run", index);
            requireSha256(fields, prefix + ".comparabilityIdentitySha256");
            requireSha256(fields, prefix + ".configurationIdentitySha256");
            requireSha256(fields, prefix + ".manifestSha256");
            requireUuid(fields, prefix + ".id");
            requireEnum(fields, prefix + ".outcome", Set.of("PASS", "FAIL", "ABORTED"));
            requireOptionalUuid(fields, prefix + ".physicalExecutionId");
            rejectRelativePath(fields.get(prefix + ".manifestPath"));
        }
    }

    private static int requireCampaignRunFamily(
            final Map<String, String> fields,
            final Set<String> fixed,
            final int minimum,
            final int maximum) {
        final Set<String> required = Set.of(
                "comparabilityIdentitySha256", "configurationIdentitySha256", "id",
                "manifestPath", "manifestSha256", "outcome");
        final Set<String> allowed = Set.of(
                "comparabilityIdentitySha256", "configurationIdentitySha256", "id",
                "manifestPath", "manifestSha256", "outcome", "physicalExecutionId");
        final Map<Integer, Set<String>> members = new TreeMap<>();
        for (String key : fields.keySet()) {
            if (fixed.contains(key)) {
                continue;
            }
            if (!key.startsWith("run.")) {
                throw new IllegalArgumentException("unknown campaign field: " + key);
            }
            final String remainder = key.substring("run.".length());
            final int dot = remainder.indexOf('.');
            if (dot <= 1 || dot != remainder.lastIndexOf('.')) {
                throw new IllegalArgumentException("malformed campaign run family: " + key);
            }
            final String number = remainder.substring(0, dot);
            if (!number.matches("[0-9]{4}")) {
                throw new IllegalArgumentException("invalid campaign run index: " + key);
            }
            final String suffix = remainder.substring(dot + 1);
            if (!allowed.contains(suffix)) {
                throw new IllegalArgumentException("unknown campaign run field: " + key);
            }
            members.computeIfAbsent(Integer.parseInt(number), ignored -> new java.util.HashSet<>())
                    .add(suffix);
        }
        if (members.size() < minimum || members.size() > maximum) {
            throw new IllegalArgumentException("campaign run family cardinality out of range");
        }
        int expected = 1;
        for (Map.Entry<Integer, Set<String>> member : members.entrySet()) {
            final Set<String> actual = member.getValue();
            if (member.getKey() != expected
                    || !(actual.equals(required)
                    || (actual.size() == allowed.size() && actual.containsAll(allowed)))) {
                throw new IllegalArgumentException("campaign run family has gap or wrong members");
            }
            expected++;
        }
        return members.size();
    }

    private static void validateRelease(final Map<String, String> fields) {
        final Set<String> fixed = Set.of(
                "artifact.applicationJarPath", "artifact.applicationJarSha256", "artifact.sbomPath",
                "artifact.sbomSha256", "artifact.sha256sumsPath", "artifact.sha256sumsSha256",
                "candidate.productionSha", "candidate.productionTreeSha256", "candidate.tag",
                "candidate.tagObjectSha", "evidence.gateCount", "release.channel",
                "release.knownLimitationCount", "release.product", "release.releaseSourceSha",
                "release.version", "schema.version");
        rejectUnknownFields(fields, fixed, Set.of("evidence.gate.", "release.knownLimitation."));
        final int gateMembers = requireExactBaseAndFamily(fields, fixed, "evidence.gate.",
                Set.of("id", "path", "sha256"), 12, 12);
        requireExactBaseAndFamily(fields, fixed, "release.knownLimitation.",
                Set.of("code", "statementDigestSha256"), 0, 1000);
        requireSha256(fields, "artifact.applicationJarSha256");
        requireSha256(fields, "artifact.sbomSha256");
        requireSha256(fields, "artifact.sha256sumsSha256");
        requireGitSha1(fields, "candidate.productionSha");
        requireSha256(fields, "candidate.productionTreeSha256");
        requireGitSha1(fields, "candidate.tagObjectSha");
        requireGitSha1(fields, "release.releaseSourceSha");
        requireInteger(fields, "evidence.gateCount");
        if (integer(fields, "evidence.gateCount") != gateMembers) {
            throw new IllegalArgumentException("evidence.gateCount does not match gate family");
        }
        requireInteger(fields, "release.knownLimitationCount");
        requireEnum(fields, "release.channel", Set.of("GITHUB_BINARY"));
        requireEnum(fields, "release.product", Set.of("ULTRA_LOW_LATENCY_MATCHING_ENGINE"));
        if (!"1.0.0".equals(fields.get("release.version"))) {
            throw new IllegalArgumentException("release version must be 1.0.0");
        }
        for (int index = 1; index <= 12; index++) {
            final String prefix = String.format("evidence.gate.%02d", index);
            if (!("G" + index).equals(fields.get(prefix + ".id"))) {
                throw new IllegalArgumentException("gate IDs must be G1..G12");
            }
            rejectRelativePath(fields.get(prefix + ".path"));
            requireSha256(fields, prefix + ".sha256");
        }
    }

    private static int requireExactBaseAndFamily(
            final Map<String, String> fields,
            final Set<String> fixed,
            final String family,
            final Set<String> suffixes,
            final int minimum,
            final int maximum) {
        final Map<Integer, Set<String>> members = new TreeMap<>();
        for (String key : fields.keySet()) {
            if (fixed.contains(key)) {
                continue;
            }
            if (key.startsWith(family)) {
                final String remainder = key.substring(family.length());
                final int dot = remainder.indexOf('.');
                if (dot <= 1 || dot != remainder.lastIndexOf('.')) {
                    throw new IllegalArgumentException("malformed schema family: " + key);
                }
                final String number = remainder.substring(0, dot);
                if (!number.matches("[0-9]{4}") && !(family.equals("evidence.gate.")
                        && number.matches("[0-9]{2}"))) {
                    throw new IllegalArgumentException("invalid schema family index: " + key);
                }
                final int index = Integer.parseInt(number);
                members.computeIfAbsent(index, ignored -> new java.util.HashSet<>())
                        .add(remainder.substring(dot + 1));
            }
        }
        if (members.size() < minimum || members.size() > maximum) {
            throw new IllegalArgumentException("schema family cardinality out of range");
        }
        int expected = 1;
        for (Map.Entry<Integer, Set<String>> member : members.entrySet()) {
            if (member.getKey() != expected || !member.getValue().equals(suffixes)) {
                throw new IllegalArgumentException("schema family has gap or wrong members");
            }
            expected++;
        }
        return members.size();
    }

    private static void rejectUnknownFields(
            final Map<String, String> fields,
            final Set<String> fixed,
            final Set<String> families) {
        for (String key : fields.keySet()) {
            if (!fixed.contains(key) && families.stream().noneMatch(key::startsWith)) {
                throw new IllegalArgumentException("unknown schema field: " + key);
            }
        }
    }

    private static String familyPrefix(final String family, final int index) {
        return String.format("%s.%04d", family, index);
    }

    private static int integer(final Map<String, String> fields, final String key) {
        try {
            return Integer.parseInt(fields.get(key));
        } catch (final NumberFormatException | NullPointerException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static void requireInteger(final Map<String, String> fields, final String key) {
        final String value = fields.get(key);
        if (value == null || !INTEGER.matcher(value).matches()) {
            throw new IllegalArgumentException(key + " must be an unsigned integer");
        }
    }

    private static void requireOptionalInteger(
            final Map<String, String> fields, final String key) {
        if (fields.containsKey(key)) {
            requireInteger(fields, key);
        }
    }

    private static void requireOptionalBoolean(
            final Map<String, String> fields, final String key) {
        if (fields.containsKey(key)) {
            requireBoolean(fields, key);
        }
    }

    private static void requireOptionalToken(
            final Map<String, String> fields, final String key) {
        if (fields.containsKey(key)) {
            requireToken(fields, key);
        }
    }

    private static void requireSize(final Map<String, String> fields, final String key) {
        try {
            if (Long.parseLong(fields.get(key)) > 1_099_511_627_776L) {
                throw new IllegalArgumentException(key + " exceeds the artifact size limit");
            }
        } catch (final NumberFormatException | NullPointerException exception) {
            throw new IllegalArgumentException(key + " must be an artifact size", exception);
        }
    }

    private static void requireGitSha1(final Map<String, String> fields, final String key) {
        if (!GIT_SHA1.matcher(Objects.requireNonNull(fields.get(key), key)).matches()) {
            throw new IllegalArgumentException(key + " must be full lowercase Git SHA-1");
        }
    }

    private static void requireSha256(final Map<String, String> fields, final String key) {
        if (!SHA256.matcher(Objects.requireNonNull(fields.get(key), key)).matches()) {
            throw new IllegalArgumentException(key + " must be lowercase SHA-256");
        }
    }

    private static void requireOptionalSha256(
            final Map<String, String> fields, final String key) {
        if (fields.containsKey(key)) {
            requireSha256(fields, key);
        }
    }

    private static void requireUuid(final Map<String, String> fields, final String key) {
        if (!UUID_PATTERN.matcher(Objects.requireNonNull(fields.get(key), key)).matches()
                || !UUID.fromString(fields.get(key)).toString().equals(fields.get(key))) {
            throw new IllegalArgumentException(key + " must be a lowercase UUID");
        }
    }

    private static void requireOptionalUuid(
            final Map<String, String> fields, final String key) {
        if (fields.containsKey(key)) {
            requireUuid(fields, key);
        }
    }

    private static void requireInstant(final Map<String, String> fields, final String key) {
        try {
            final String value = Objects.requireNonNull(fields.get(key), key);
            if (!value.endsWith("Z") || !Instant.parse(value).toString().equals(value)) {
                throw new IllegalArgumentException(key + " must be canonical UTC Instant");
            }
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException(key + " must be canonical UTC Instant", exception);
        }
    }

    private static void requireBoolean(final Map<String, String> fields, final String key) {
        if (!Set.of("true", "false").contains(Objects.requireNonNull(fields.get(key), key))) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
    }

    private static void requireEnum(
            final Map<String, String> fields, final String key, final Set<String> allowed) {
        if (!allowed.contains(Objects.requireNonNull(fields.get(key), key))) {
            throw new IllegalArgumentException(key + " has an invalid enum value");
        }
    }

    private static void requireToken(final Map<String, String> fields, final String key) {
        final String value = Objects.requireNonNull(fields.get(key), key);
        if (!value.matches("[a-z0-9][a-z0-9.-]{0,63}")) {
            throw new IllegalArgumentException(key + " must be a lowercase token");
        }
    }

    private static void rejectRelativePath(final String value) {
        if (value == null || value.isBlank()
                || !Normalizer.normalize(value, Normalizer.Form.NFC).equals(value)
                || value.getBytes(StandardCharsets.UTF_8).length > 240
                || value.startsWith("/") || value.startsWith("\\")
                || value.matches("[A-Za-z]:[\\\\/].*")) {
            throw new IllegalArgumentException("invalid relative evidence path");
        }
        final String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("invalid relative evidence path");
            }
        }
    }

    private static void validateKey(final String key) {
        if (key == null || key.getBytes(StandardCharsets.US_ASCII).length > 128
                || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("invalid evidence key: " + key);
        }
    }

    private static void validateValue(final String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES) {
            throw new IllegalArgumentException("evidence value exceeds limit");
        }
    }

    private static byte[] encodeCanonical(final Map<String, String> fields) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            final byte[] key = entry.getKey().getBytes(StandardCharsets.US_ASCII);
            final byte[] value = encodeValue(entry.getValue());
            if (key.length + value.length + 2 > MAX_LINE_BYTES) {
                throw new IllegalArgumentException("evidence line exceeds limit");
            }
            output.writeBytes(key);
            output.write('=');
            output.writeBytes(value);
            output.write('\n');
        }
        final byte[] result = output.toByteArray();
        if (result.length > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("evidence document is too large");
        }
        return result;
    }

    private static Map<String, String> parseCanonical(final byte[] bytes) {
        final String text = decodeUtf8(bytes);
        if (text.startsWith("\uFEFF") || text.indexOf('\r') >= 0
                || (!text.isEmpty() && !text.endsWith("\n"))) {
            throw new IllegalArgumentException("evidence must be LF terminated canonical form");
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        if (!text.isEmpty()) {
            final String[] lines = text.split("\\n", -1);
            for (int index = 0; index < lines.length - 1; index++) {
                final String line = lines[index];
                final int separator = line.indexOf('=');
                if (separator <= 0 || separator != line.lastIndexOf('=')) {
                    throw new IllegalArgumentException("malformed evidence field");
                }
                final String key = line.substring(0, separator);
                validateKey(key);
                if (fields.put(key, decodeValue(line.substring(separator + 1))) != null) {
                    throw new IllegalArgumentException("duplicate evidence key: " + key);
                }
            }
        }
        return fields;
    }

    private static byte[] encodeValue(final String value) {
        final byte[] input = value.getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
        for (byte item : input) {
            final int valueByte = item & 0xFF;
            if (isUnreserved(valueByte)) {
                output.write(valueByte);
            } else {
                output.write('%');
                output.write("0123456789ABCDEF".charAt(valueByte >>> 4));
                output.write("0123456789ABCDEF".charAt(valueByte & 0x0F));
            }
        }
        return output.toByteArray();
    }

    private static String decodeValue(final String value) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current == '%') {
                if (index + 2 >= value.length() || !isHex(value.charAt(index + 1))
                        || !isHex(value.charAt(index + 2))) {
                    throw new IllegalArgumentException("invalid percent escape");
                }
                output.write((hex(value.charAt(index + 1)) << 4) | hex(value.charAt(index + 2)));
                index += 2;
            } else if (current < 128 && isUnreserved(current)) {
                output.write(current);
            } else {
                throw new IllegalArgumentException("non-canonical evidence value");
            }
        }
        final byte[] decoded = output.toByteArray();
        if (decoded.length > MAX_VALUE_BYTES) {
            throw new IllegalArgumentException("evidence value exceeds limit");
        }
        return decodeUtf8(decoded);
    }

    private static boolean isUnreserved(final int value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9' || value == '-' || value == '.'
                || value == '_' || value == '~';
    }

    private static boolean isHex(final char value) {
        return value >= '0' && value <= '9' || value >= 'A' && value <= 'F';
    }

    private static int hex(final char value) {
        return value <= '9' ? value - '0' : value - 'A' + 10;
    }

    private static String decodeUtf8(final byte[] bytes) {
        try {
            final CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return chars.toString();
        } catch (final CharacterCodingException exception) {
            throw new IllegalArgumentException("evidence is not valid UTF-8", exception);
        }
    }

    private static String digest(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }
}
