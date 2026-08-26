package com.ultralatency.matching.qualification.ga.correctness;

import io.netty.buffer.PooledByteBufAllocator;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Captures the runtime fields required by the canonical GA run manifest. */
final class GaCorrectnessRuntimeProvenance {

    private GaCorrectnessRuntimeProvenance() {
    }

    /** Captures environment fields without volatile process identity. */
    static Map<String, String> capture(final Path evidenceDirectory) throws IOException {
        Objects.requireNonNull(evidenceDirectory, "evidenceDirectory");
        final Path directory = evidenceDirectory.toAbsolutePath().normalize();
        final java.nio.file.FileStore fileStore = Files.getFileStore(directory);
        final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("runtime.cpuModel", firstNonBlank(
                System.getenv("PROCESSOR_IDENTIFIER"),
                System.getenv("HOSTTYPE"),
                System.getProperty("os.arch", "unknown")));
        values.put("runtime.filesystem", valueOrUnknown(fileStore.type()));
        values.put("runtime.gcCollectors", ManagementFactory.getGarbageCollectorMXBeans()
                .stream().map(GarbageCollectorMXBean::getName).sorted()
                .collect(Collectors.joining(",")));
        values.put("runtime.heapMaxBytes", Long.toString(Math.max(
                0L, memory.getHeapMemoryUsage().getMax())));
        values.put("runtime.javaRuntimeVersion", property("java.runtime.version"));
        values.put("runtime.javaVendor", property("java.vendor"));
        values.put("runtime.javaVmArguments", vmArguments());
        values.put("runtime.javaVmName", property("java.vm.name"));
        values.put("runtime.javaVmVersion", property("java.vm.version"));
        values.put("runtime.logicalProcessors",
                Integer.toString(Runtime.getRuntime().availableProcessors()));
        values.put("runtime.nettyAllocator", PooledByteBufAllocator.DEFAULT
                .getClass().getName());
        values.put("runtime.osArch", property("os.arch"));
        values.put("runtime.osName", property("os.name"));
        values.put("runtime.osVersion", property("os.version"));
        values.put("runtime.storageIdentity", valueOrUnknown(
                fileStore.name() + ":" + fileStore.type()));
        return Map.copyOf(values);
    }

    private static String vmArguments() {
        final String arguments = String.join(" ",
                ManagementFactory.getRuntimeMXBean().getInputArguments());
        return arguments.isBlank() ? "<none>" : arguments;
    }

    private static String property(final String name) {
        return valueOrUnknown(System.getProperty(name));
    }

    private static String firstNonBlank(final String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    private static String valueOrUnknown(final String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
