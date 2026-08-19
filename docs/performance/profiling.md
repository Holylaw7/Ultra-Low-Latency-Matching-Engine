# Profiling Methodology

## Status

The Phase 2 OrderBook benchmark baseline was approved on `2026-08-19`.
`ADR-0009-performance-profiling-evidence.md` and its task-plan proposal are
currently awaiting Human Approval. No profile has been recorded or authorized
yet.

## Workflow

```text
Reproduce
    -> Benchmark
    -> Profiling ADR / Decision
    -> Profile
    -> Identify Hot Path
    -> Form Hypothesis
    -> Optimize
    -> Re-benchmark
```

## Proposed First Profile

The proposed first profile uses the committed `OrderBookBaselineBenchmark`
without changing production behavior or benchmark semantics. The primary
workload is `multiLevelMatch`, with `oneLevelMatch`, `cancelByOrderId` and
`cancelAndCleanEmptyLevel` as supporting workloads.

JFR is the required first tool because it is included with the approved Java 21
runtime. async-profiler is optional supplementary evidence when it is available
and its version and command are recorded. The profile stage must not implement
an optimization.

See [`ADR-0009-performance-profiling-evidence.md`](../adr/ADR-0009-performance-profiling-evidence.md)
and [`PHASE-2-profiling-adr-decision.md`](../../tasks/reports/PHASE-2-profiling-adr-decision.md).

## Tools

- JFR for JVM-level recordings
- async-profiler for CPU and allocation profiles
- GC logs for pause and allocation behavior
- Linux `perf` when the environment supports it

Profile artifacts must include the command, workload, environment, tool
versions, timestamp and raw artifact paths. A profile observation does not
authorize an optimization; any optimization requires a separate ADR / Decision
and Human Approval.
