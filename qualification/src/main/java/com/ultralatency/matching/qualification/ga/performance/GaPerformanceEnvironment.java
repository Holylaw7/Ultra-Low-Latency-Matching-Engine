package com.ultralatency.matching.qualification.ga.performance;

import com.ultralatency.matching.qualification.QualificationIdentity;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Captures and compares the immutable G4 reference/comparability environment identity. */
public final class GaPerformanceEnvironment {

    private static final Map<String, String> REFERENCE = Map.ofEntries(
            Map.entry("os.name", "Windows 11"),
            Map.entry("os.version", "10.0.26200"),
            Map.entry("os.arch", "amd64"),
            Map.entry("cpu.model", "Intel(R) Core(TM) i9-13900H"),
            Map.entry("logical.processors", "20"),
            Map.entry("filesystem", "NTFS"),
            Map.entry("storage.identity", "E:NVMe"),
            Map.entry("java.vendor", "Microsoft"),
            Map.entry("java.runtime.version", "21.0.12+8-LTS"),
            Map.entry("gc.collectors", "G1 Young Generation,G1 Concurrent GC"),
            Map.entry("java.vm.arguments", "<none>"),
            Map.entry("heap.max.bytes", "8493465600"),
            Map.entry("netty.version", "4.2.17.Final"),
            Map.entry("disruptor.version", "4.0.0"),
            Map.entry("locale", "zh-CN"),
            Map.entry("timezone", "Asia/Hong_Kong"));

    private GaPerformanceEnvironment() {
    }

    /** Returns the frozen reference identity. */
    public static Map<String, String> reference() {
        return REFERENCE;
    }

    /** Captures stable environment fields without PID, timestamps or paths. */
    public static Map<String, String> capture(final Path storageRoot) throws IOException {
        Objects.requireNonNull(storageRoot, "storageRoot");
        final Map<String, String> values = new LinkedHashMap<>();
        final java.nio.file.FileStore store = Files.getFileStore(storageRoot);
        values.put("os.name", System.getProperty("os.name", "UNAVAILABLE"));
        values.put("os.version", System.getProperty("os.version", "UNAVAILABLE"));
        values.put("os.arch", System.getProperty("os.arch", "UNAVAILABLE"));
        values.put("cpu.model", firstNonBlank(
                System.getenv("PROCESSOR_IDENTIFIER"), System.getProperty("os.arch")));
        values.put("logical.processors", Integer.toString(Runtime.getRuntime().availableProcessors()));
        values.put("filesystem", store.type());
        values.put("storage.identity", store.name() + ":" + store.type());
        values.put("java.vendor", System.getProperty("java.vendor", "UNAVAILABLE"));
        values.put("java.vm.name", System.getProperty("java.vm.name", "UNAVAILABLE"));
        values.put("java.vm.version", System.getProperty("java.vm.version", "UNAVAILABLE"));
        values.put("java.runtime.version", System.getProperty("java.runtime.version",
                System.getProperty("java.version", "UNAVAILABLE")));
        values.put("gc.collectors", ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName).sorted().collect(Collectors.joining(",")));
        final String vmArguments = String.join(" ",
                ManagementFactory.getRuntimeMXBean().getInputArguments());
        values.put("java.vm.arguments", vmArguments.isBlank() ? "<none>" : vmArguments);
        values.put("heap.max.bytes", Long.toString(
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax()));
        values.put("netty.version", packageVersion("io.netty.channel.Channel"));
        values.put("disruptor.version", packageVersion("com.lmax.disruptor.RingBuffer"));
        values.put("locale", java.util.Locale.getDefault().toLanguageTag());
        values.put("timezone", java.util.TimeZone.getDefault().getID());
        return Map.copyOf(values);
    }

    /** Returns the keys whose observed values differ from the frozen reference. */
    public static Map<String, String> mismatches(final Map<String, String> observed) {
        Objects.requireNonNull(observed, "observed");
        final Map<String, String> mismatches = new LinkedHashMap<>();
        REFERENCE.forEach((key, expected) -> {
            final String actual = observed.get(key);
            if (!expected.equals(actual)) {
                mismatches.put(key, actual == null ? "MISSING" : actual);
            }
        });
        return Map.copyOf(mismatches);
    }

    /** Returns whether every frozen reference field was observed exactly. */
    public static boolean matchesReference(final Map<String, String> observed) {
        return mismatches(observed).isEmpty();
    }

    /** Returns a deterministic identity digest over the observed environment. */
    public static String identity(final Map<String, String> observed) {
        return QualificationIdentity.digest(Objects.requireNonNull(observed, "observed"));
    }

    private static String firstNonBlank(final String first, final String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static String packageVersion(final String className) {
        try {
            final Class<?> type = Class.forName(className);
            final String value = type.getPackage().getImplementationVersion();
            return value == null || value.isBlank() ? "UNAVAILABLE" : value;
        } catch (final ClassNotFoundException exception) {
            return "UNAVAILABLE";
        }
    }
}
