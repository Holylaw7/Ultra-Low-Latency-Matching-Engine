package com.ultralatency.matching.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Bootstrap benchmark used to verify that the independent JMH module is wired correctly.
 */
@State(Scope.Thread)
public class BootstrapBenchmark {

    private long value;

    @Benchmark
    public long increment() {
        return ++value;
    }
}
