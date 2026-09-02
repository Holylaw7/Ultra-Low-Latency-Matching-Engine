package com.ultralatency.matching.qualification.ga.soak;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Qualification-owned one-Hz resource sampler for a single physical execution.
 *
 * <p>Only declared snapshot temporary files are counted as transient files.  WAL, durable
 * snapshots and evidence files are intentionally excluded from this hard-gate observation.</p>
 */
public final class GaSoakResourceSampler implements AutoCloseable {

    /** Classification used for owned runtime files. */
    public enum FileClassification {
        DURABLE,
        TRANSIENT,
        EVIDENCE,
        UNKNOWN
    }

    private static final Pattern WAL_SEGMENT = Pattern.compile("wal-[0-9]{20}\\.log");
    private static final Pattern SNAPSHOT_DURABLE =
            Pattern.compile("snapshot-[^/\\\\]+\\.bin");
    private static final Pattern SNAPSHOT_TRANSIENT =
            Pattern.compile("snapshot-[^/\\\\]+\\.tmp");

    private final String physicalExecutionId;
    private final GaSoakMatrix.Stage stage;
    private final Path physicalRoot;
    private final Path snapshotRoot;
    private final ScheduledExecutorService executor;
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> collectors =
            ManagementFactory.getGarbageCollectorMXBeans();
    private final List<GaSoakResourceSample> samples = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean unknownOwnedFiles = new AtomicBoolean();
    private final AtomicBoolean samplingFailure = new AtomicBoolean();
    private long sequence;

    /** Starts one-Hz sampling immediately and records the first sample synchronously. */
    public GaSoakResourceSampler(
            final String physicalExecutionId,
            final GaSoakMatrix.Stage stage,
            final Path snapshotRoot) {
        this(physicalExecutionId, stage, Objects.requireNonNull(snapshotRoot, "snapshotRoot")
                .toAbsolutePath().normalize().getParent(), snapshotRoot);
    }

    /** Starts sampling with an explicit owned physical root and snapshot root. */
    public GaSoakResourceSampler(
            final String physicalExecutionId,
            final GaSoakMatrix.Stage stage,
            final Path physicalRoot,
            final Path snapshotRoot) {
        if (physicalExecutionId == null || physicalExecutionId.isBlank()) {
            throw new IllegalArgumentException("physicalExecutionId must not be blank");
        }
        this.physicalExecutionId = physicalExecutionId;
        this.stage = Objects.requireNonNull(stage, "stage");
        this.physicalRoot = Objects.requireNonNull(physicalRoot, "physicalRoot")
                .toAbsolutePath().normalize();
        this.snapshotRoot = Objects.requireNonNull(snapshotRoot, "snapshotRoot")
                .toAbsolutePath().normalize();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "ga-soak-resource-sampler");
            thread.setDaemon(true);
            return thread;
        });
        sampleNow();
        executor.scheduleAtFixedRate(this::sample, 1L, 1L, TimeUnit.SECONDS);
    }

    /** Stops sampling; no sample is synthesized after close. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(5L, TimeUnit.SECONDS);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** Returns an immutable chronological copy of captured samples. */
    public List<GaSoakResourceSample> samples() {
        synchronized (samples) {
            return List.copyOf(samples);
        }
    }

    /** Returns whether an unclassified file was observed in an owned runtime root. */
    public boolean hasUnknownOwnedFiles() {
        return unknownOwnedFiles.get();
    }

    /** Returns whether filesystem access prevented a trustworthy resource sample. */
    public boolean samplingFailed() {
        return samplingFailure.get();
    }

    /** Returns whether all currently owned transient files have been removed. */
    public boolean transientFilesCleanAfterShutdown() {
        try {
            final OwnedFiles ownedFiles = scanOwnedFiles();
            if (!ownedFiles.unknownFiles().isEmpty()) {
                unknownOwnedFiles.set(true);
            }
            return ownedFiles.transientFiles().isEmpty() && ownedFiles.unknownFiles().isEmpty();
        } catch (final IOException failure) {
            samplingFailure.set(true);
            return false;
        }
    }

    /** Classifies one path according to the frozen TASK-052 owned-file contract. */
    public static FileClassification classifyOwnedPath(
            final Path physicalRoot,
            final Path path) {
        Objects.requireNonNull(physicalRoot, "physicalRoot");
        Objects.requireNonNull(path, "path");
        final Path root = physicalRoot.toAbsolutePath().normalize();
        final Path candidate = path.toAbsolutePath().normalize();
        if (!candidate.startsWith(root)
                || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("path is not an owned regular file");
        }
        final Path relative = root.relativize(candidate);
        if (relative.getNameCount() < 2) {
            return FileClassification.UNKNOWN;
        }
        final String directory = relative.getName(0).toString();
        final String name = relative.getFileName().toString();
        if ("wal".equals(directory)) {
            if (relative.getNameCount() != 2) {
                return FileClassification.UNKNOWN;
            }
            return "recovery.lock".equals(name) || WAL_SEGMENT.matcher(name).matches()
                    ? FileClassification.DURABLE : FileClassification.UNKNOWN;
        }
        if ("snapshots".equals(directory)) {
            if (relative.getNameCount() != 2) {
                return FileClassification.UNKNOWN;
            }
            if (SNAPSHOT_DURABLE.matcher(name).matches()) {
                return FileClassification.DURABLE;
            }
            if (SNAPSHOT_TRANSIENT.matcher(name).matches()) {
                return FileClassification.TRANSIENT;
            }
            return FileClassification.UNKNOWN;
        }
        if ("process-evidence".equals(directory) || "evidence".equals(directory)) {
            return FileClassification.EVIDENCE;
        }
        return FileClassification.UNKNOWN;
    }

    private void sample() {
        if (!closed.get()) {
            sampleNow();
        }
    }

    private void sampleNow() {
        final long heap = Math.max(0L, memory.getHeapMemoryUsage().getUsed());
        final long transientCount;
        final long transientBytes;
        try {
            final OwnedFiles ownedFiles = scanOwnedFiles();
            final List<Path> files = ownedFiles.transientFiles();
            if (!ownedFiles.unknownFiles().isEmpty()) {
                unknownOwnedFiles.set(true);
            }
            transientCount = files.size();
            transientBytes = files.stream().mapToLong(this::fileSize).sum();
        } catch (final IOException failure) {
            // Do not turn an unreadable root into a fabricated zero sample.  The resulting
            // missing interval is handled as an aborted/insufficient observation by the guard.
            samplingFailure.set(true);
            return;
        }
        final Long postGc = null;
        synchronized (samples) {
            samples.add(new GaSoakResourceSample(physicalExecutionId, stage, sequence++,
                    System.nanoTime(), Math.max(0L, threads.getThreadCount()), transientCount,
                    transientBytes, heap, postGc));
        }
    }

    private OwnedFiles scanOwnedFiles() throws IOException {
        final List<Path> transientFiles = new ArrayList<>();
        final List<Path> unknownFiles = new ArrayList<>();
        if (Files.isDirectory(physicalRoot)) {
            try (java.util.stream.Stream<Path> paths = Files.list(physicalRoot)) {
                paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .forEach(path -> unknownFiles.add(path));
            }
        }
        if (Files.isDirectory(snapshotRoot)) {
            try (java.util.stream.Stream<Path> paths = Files.list(snapshotRoot)) {
                paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .forEach(path -> {
                    final FileClassification classification = classifyOwnedPath(physicalRoot, path);
                    if (classification == FileClassification.TRANSIENT) {
                        transientFiles.add(path);
                    } else if (classification == FileClassification.UNKNOWN) {
                        unknownFiles.add(path);
                    }
                });
            }
        }
        final Path walRoot = physicalRoot.resolve("wal");
        if (Files.isDirectory(walRoot) && !walRoot.equals(snapshotRoot)) {
            try (java.util.stream.Stream<Path> paths = Files.list(walRoot)) {
                paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .forEach(path -> {
                    if (classifyOwnedPath(physicalRoot, path) == FileClassification.UNKNOWN) {
                        unknownFiles.add(path);
                    }
                });
            }
        }
        transientFiles.sort(Comparator.comparing(Path::toString));
        unknownFiles.sort(Comparator.comparing(Path::toString));
        return new OwnedFiles(List.copyOf(transientFiles), List.copyOf(unknownFiles));
    }

    private long fileSize(final Path path) {
        try {
            return Math.max(0L, Files.size(path));
        } catch (final IOException failure) {
            samplingFailure.set(true);
            return 0L;
        }
    }

    private record OwnedFiles(List<Path> transientFiles, List<Path> unknownFiles) {
    }
}
