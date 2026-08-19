# Profiling Methodology

## Status

No profile has been recorded.

## Workflow

```text
Reproduce
    -> Benchmark
    -> Profile
    -> Identify Hot Path
    -> Form Hypothesis
    -> Optimize
    -> Re-benchmark
```

## Tools

- JFR for JVM-level recordings
- async-profiler for CPU and allocation profiles
- GC logs for pause and allocation behavior
- Linux `perf` when the environment supports it

Profile artifacts must include the command, workload, environment, and timestamp.
