package com.ultralatency.matching.qualification;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Samples process resources without forcing GC or using timing as a correctness oracle.
 */
public final class QualificationResourceSampler implements AutoCloseable {

    private static final String SAMPLER_THREAD_PREFIX = "qualification-resource-sampler";

    private final Duration interval;
    private final int minimumPostGcSamples;
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> collectors =
            ManagementFactory.getGarbageCollectorMXBeans();
    private final ScheduledExecutorService executor;
    private final List<QualificationResourceSample> samples = new ArrayList<>();
    private final Set<String> baselineRuntimeThreads;
    private final long baselineThreadCount;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile long lastGcCollections;

    /** Starts a sampler immediately; the scheduler is a qualification-owned resource. */
    public QualificationResourceSampler(
            final Duration interval,
            final int minimumPostGcSamples) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        if (minimumPostGcSamples < 0) {
            throw new IllegalArgumentException("minimumPostGcSamples must not be negative");
        }
        this.interval = interval;
        this.minimumPostGcSamples = minimumPostGcSamples;
        this.baselineThreadCount = threadBean.getThreadCount();
        this.baselineRuntimeThreads = runtimeThreadNames();
        this.lastGcCollections = totalGcCollections();
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, SAMPLER_THREAD_PREFIX);
            thread.setDaemon(true);
            return thread;
        });
        sampleNow();
        final long periodNanos = Math.max(1L, interval.toNanos());
        executor.scheduleAtFixedRate(this::sample, periodNanos, periodNanos, TimeUnit.NANOSECONDS);
    }

    /** Stops sampling and returns immutable evidence. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        sample();
    }

    /** Returns evidence after close; live sampling state is otherwise still safe to inspect. */
    public QualificationResourceEvidence evidence() {
        final List<QualificationResourceSample> postGcSamples =
                QualificationHeapGuard.naturalPostGcSamples(samples);
        final List<Long> postGc = postGcSamples.stream()
                .map(QualificationResourceSample::naturalPostGcHeapBytes)
                .toList();
        final boolean assessed = postGc.size() >= minimumPostGcSamples
                && minimumPostGcSamples > 0;
        final boolean heapPassed = assessed
                && QualificationHeapGuard.passes(samples, minimumPostGcSamples);
        final List<String> baseline = baselineRuntimeThreads.stream().sorted().toList();
        final List<String> current = runtimeThreadNames().stream().sorted().toList();
        return new QualificationResourceEvidence(
                List.copyOf(samples),
                postGc,
                baselineThreadCount,
                threadBean.getThreadCount(),
                baseline,
                current,
                baseline.equals(current),
                assessed,
                heapPassed);
    }

    private void sample() {
        if (closed.get()) {
            return;
        }
        sampleNow();
    }

    private void sampleNow() {
        final long collections = totalGcCollections();
        final Long postGc = collections > lastGcCollections
                ? Math.max(0L, memoryBean.getHeapMemoryUsage().getUsed())
                : null;
        lastGcCollections = Math.max(lastGcCollections, collections);
        samples.add(new QualificationResourceSample(
                Instant.now(),
                Math.max(0, threadBean.getThreadCount()),
                Math.max(0, threadBean.getPeakThreadCount()),
                collections,
                totalGcTimeMillis(),
                Math.max(0L, memoryBean.getHeapMemoryUsage().getUsed()),
                postGc));
    }

    private long totalGcCollections() {
        return collectors.stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .filter(value -> value >= 0)
                .sum();
    }

    private long totalGcTimeMillis() {
        return collectors.stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .filter(value -> value >= 0)
                .sum();
    }

    private Set<String> runtimeThreadNames() {
        return Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(QualificationResourceSampler::isRuntimeThread)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static boolean isRuntimeThread(final String name) {
        return !name.startsWith(SAMPLER_THREAD_PREFIX)
                && (name.contains("nioEventLoopGroup")
                || name.contains("disruptor")
                || name.contains("matching-engine")
                || name.contains("matchingEngine"));
    }

}
