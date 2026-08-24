package com.ultralatency.matching.qualification;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Runtime-independent and runtime-comparability identities for Full evidence. */
public final class QualificationIdentity {

    /** Identity pair written to every v2 manifest. */
    public record Pair(String configurationIdentitySha256, String comparabilityIdentitySha256) {
        public Pair {
            requireDigest(configurationIdentitySha256, "configurationIdentitySha256");
            requireDigest(comparabilityIdentitySha256, "comparabilityIdentitySha256");
        }
    }

    private QualificationIdentity() {
    }

    /** Computes both identities from approved configuration and observed runtime provenance. */
    public static Pair forRun(
            final QualificationFullConfiguration configuration,
            final Map<String, String> runtimeProvenance) {
        return forRun(configuration, runtimeProvenance, "working-tree", "UNSPECIFIED");
    }

    /** Computes identities while binding source commit and approved baseline tag. */
    public static Pair forRun(
            final QualificationFullConfiguration configuration,
            final Map<String, String> runtimeProvenance,
            final String gitSha,
            final String baselineTag) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(runtimeProvenance, "runtimeProvenance");
        return new Pair(
                digest(configurationFields(configuration, gitSha, baselineTag)),
                digest(comparabilityFields(runtimeProvenance)));
    }

    /** Returns the canonical qualification-definition fields; volatile values are excluded. */
    public static Map<String, String> configurationFields(
            final QualificationFullConfiguration configuration) {
        return configurationFields(configuration, "working-tree", "UNSPECIFIED");
    }

    /** Returns qualification-definition fields bound to the source and baseline identity. */
    public static Map<String, String> configurationFields(
            final QualificationFullConfiguration configuration,
            final String gitSha,
            final String baselineTag) {
        Objects.requireNonNull(configuration, "configuration");
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("identity.schema", "qualification-identity-v1");
        values.put("source.gitSha", requireText(gitSha, "gitSha"));
        values.put("source.baselineTag", requireText(baselineTag, "baselineTag"));
        values.put("workload.profile", configuration.profile().name());
        values.put("workload.version", workloadVersion(configuration.profile()));
        values.put("workload.seed", Long.toString(configuration.seed()));
        values.put("workload.commandCount", Integer.toString(configuration.commandCount()));
        values.put("qualification.lane", configuration.lane().name());
        values.put("qualification.minimumDuration", configuration.minimumDuration().toString());
        values.put("qualification.commandTimeout", configuration.commandTimeout().toString());
        values.put("qualification.sampleInterval", configuration.sampleInterval().toString());
        values.put("qualification.minimumPostGcSamples",
                Integer.toString(configuration.minimumPostGcSamples()));
        values.put("qualification.minimumRuns",
                Integer.toString(QualificationFullConfiguration.CAMPAIGN_MINIMUM_RUNS));
        values.put("qualification.minimumCampaignSamples",
                Integer.toString(QualificationFullConfiguration.CAMPAIGN_MINIMUM_POST_GC_SAMPLES));
        values.put("qualification.fullMinimumCommands",
                Integer.toString(QualificationFullConfiguration.FULL_MINIMUM_COMMANDS));
        values.put("qualification.publicProbeSuffixLength",
                Integer.toString(QualificationRunner.PUBLIC_PROBE_SUFFIX_LENGTH));
        values.put("qualification.heapGuardAlgorithm", "chronological-post-gc-v2");
        values.put("qualification.heapGuardAllowance", "approved-default");
        values.put("qualification.memoryActiveOrderBound",
                Integer.toString(QualificationWorkloadV1.MEMORY_STEADY_STATE_MAX_ACTIVE_ORDERS));
        values.put("protocol.version", "Protocol-v1");
        values.put("protocol.singleSession", "true");
        values.put("protocol.singleInFlight", "true");
        values.put("durability.mode", "SYNC_EACH_APPEND");
        values.put("wal.format", "WAL-v1");
        values.put("pipeline.capacity", "approved-default");
        values.put("pipeline.waitMode", "BLOCKING");
        values.put("recovery.modes", "PURE_WAL,SNAPSHOT_THEN_WAL");
        return Map.copyOf(values);
    }

    /** Returns only the runtime provenance fields used for comparability. */
    public static Map<String, String> comparabilityFields(
            final Map<String, String> runtimeProvenance) {
        Objects.requireNonNull(runtimeProvenance, "runtimeProvenance");
        final Map<String, String> values = new LinkedHashMap<>();
        runtimeProvenance.forEach((key, value) -> {
            if (key.startsWith("runtime.mustMatch.")) {
                values.put(key, value);
            }
        });
        return Map.copyOf(values);
    }

    /** Captures stable runtime provenance without including paths, PIDs or outcomes. */
    public static Map<String, String> runtimeProvenance(final Path walDirectory) throws java.io.IOException {
        Objects.requireNonNull(walDirectory, "walDirectory");
        final Map<String, String> values = new LinkedHashMap<>();
        put(values, "runtime.mustMatch.java.vendor", System.getProperty("java.vendor", "UNAVAILABLE"));
        put(values, "runtime.mustMatch.java.runtimeVersion",
                System.getProperty("java.runtime.version", System.getProperty("java.version", "UNAVAILABLE")));
        put(values, "runtime.mustMatch.java.vmName", System.getProperty("java.vm.name", "UNAVAILABLE"));
        put(values, "runtime.mustMatch.java.vmVersion",
                System.getProperty("java.vm.version", "UNAVAILABLE"));
        put(values, "runtime.mustMatch.java.vmInputArguments", inputArguments());
        put(values, "runtime.mustMatch.gcCollectors", ManagementFactory.getGarbageCollectorMXBeans()
                .stream().map(GarbageCollectorMXBean::getName).sorted().collect(Collectors.joining(",")));
        final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        put(values, "runtime.mustMatch.heapMaxBytes",
                Long.toString(memory.getHeapMemoryUsage().getMax()));
        put(values, "runtime.mustMatch.processors", Integer.toString(Runtime.getRuntime().availableProcessors()));
        put(values, "runtime.mustMatch.fileEncoding", System.getProperty("file.encoding", "UNAVAILABLE"));
        put(values, "runtime.mustMatch.timezone", java.util.TimeZone.getDefault().getID());
        put(values, "runtime.mustMatch.locale", java.util.Locale.getDefault().toLanguageTag());
        put(values, "runtime.mustMatch.osName", System.getProperty("os.name", "UNAVAILABLE"));
        put(values, "runtime.mustMatch.osVersion", System.getProperty("os.version", "UNAVAILABLE"));
        put(values, "runtime.mustMatch.osArch", System.getProperty("os.arch", "UNAVAILABLE"));
        put(values, "runtime.mustMatch.filesystem", Files.getFileStore(walDirectory).type());
        put(values, "runtime.mustMatch.nettyVersion", packageVersion("io.netty.channel.Channel"));
        put(values, "runtime.mustMatch.disruptorVersion",
                packageVersion("com.lmax.disruptor.RingBuffer"));
        put(values, "runtime.mustMatch.jfrConfiguration", "jdk-default-qualification-v1");
        put(values, "runtime.recordOnly.host", System.getenv().getOrDefault("COMPUTERNAME", "UNAVAILABLE"));
        put(values, "runtime.recordOnly.pid", Long.toString(ProcessHandle.current().pid()));
        put(values, "runtime.recordOnly.startTime", java.time.Instant.now().toString());
        return Map.copyOf(values);
    }

    /** Computes a digest over sorted identity fields. */
    public static String digest(final Map<String, String> fields) {
        Objects.requireNonNull(fields, "fields");
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            fields.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                update(digest, entry.getKey());
                update(digest, entry.getValue());
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private static String workloadVersion(final QualificationProfile profile) {
        return profile == QualificationProfile.MEMORY_STEADY_STATE_V1
                ? QualificationWorkloadV1.MEMORY_STEADY_STATE_VERSION
                : QualificationWorkloadV1.VERSION;
    }

    private static void update(final MessageDigest digest, final String value) {
        final byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String packageVersion(final String className) {
        try {
            final Class<?> type = Class.forName(className);
            final String version = type.getPackageName().isBlank()
                    ? null : type.getPackage().getImplementationVersion();
            return version == null || version.isBlank() ? "UNAVAILABLE" : version;
        } catch (final ClassNotFoundException exception) {
            return "UNAVAILABLE";
        }
    }

    private static String inputArguments() {
        final String value = String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments());
        return value.isBlank() ? "<none>" : value;
    }

    private static void put(final Map<String, String> values, final String key, final String value) {
        values.put(key, value == null || value.isBlank() ? "UNAVAILABLE" : value);
    }

    private static void requireDigest(final String value, final String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }

    private static String requireText(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
