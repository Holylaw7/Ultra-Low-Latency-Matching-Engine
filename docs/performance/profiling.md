# Profiling Methodology

## Status

The Phase 2 OrderBook benchmark baseline and documentation synchronization were
approved on `2026-08-19`. ADR-0009 and its profiling task-plan stage were
approved on `2026-08-19`; profiling execution was completed on `2026-08-19`
and approved on `2026-08-19`. ADR-0010 was approved on `2026-08-19`;
measurement-isolation execution completed and is pending Human Approval.
No production optimization or Phase 3 work is authorized.

## Workflow

```text
Reproduce
    -> Benchmark
    -> Profiling ADR / Decision
    -> Profile
    -> Identify Hot Path
    -> Isolate Measurement
    -> Form Hypothesis
    -> Optimize
    -> Re-benchmark
```

## Authorized First Profile

The authorized first profile uses the committed `OrderBookBaselineBenchmark`
without changing production behavior or benchmark semantics. The primary
workload is `multiLevelMatch`, with `oneLevelMatch`, `cancelByOrderId` and
`cancelAndCleanEmptyLevel` as supporting workloads.

JFR is the required first tool because it is included with the approved Java 21
runtime. async-profiler is optional supplementary evidence when it is available
and its version and command are recorded. The profile stage must not implement
an optimization.

See [`ADR-0009-performance-profiling-evidence.md`](../adr/ADR-0009-performance-profiling-evidence.md)
and [`PHASE-2-profiling-execution.md`](../../tasks/reports/PHASE-2-profiling-execution.md).

The profiling stage produced a report and was approved as evidence collection.
A profile observation does not authorize an optimization; any optimization
requires a separate ADR / Decision and Human Approval.

## Current Evidence

JFR recordings were collected for `multiLevelMatch`, `oneLevelMatch`,
`cancelByOrderId` and `cancelAndCleanEmptyLevel` using the approved JMH
parameters. The primary observations include TreeMap navigation and mutation,
HashMap lookup, order construction and match-fragment allocation. The
recordings include JFR overhead and JMH invocation setup, so they are not
baseline or production latency measurements.

The full environment, commands, raw artifact directories, hotspot observations,
GC observations and limitations are recorded in
[`PHASE-2-profiling-execution.md`](../../tasks/reports/PHASE-2-profiling-execution.md).

Current gate:

```text
Profiling Execution:
    Completed - Approved

Optimization ADR / Decision:
    Approved

Measurement-Isolation:
    Completed - Pending Human Approval

Phase 3:
    Not Authorized
```

## Optimization Decision

The current JFR evidence did not isolate steady-state matching cost from
`@Setup(Level.Invocation)` and JFR overhead. ADR-0010 therefore deferred
production optimization and authorized a separate measurement-isolation
experiment. The experiment is recorded in
[`PHASE-2-measurement-isolation.md`](../../tasks/reports/PHASE-2-measurement-isolation.md).
Its short JFR recordings remain insufficient for selecting a production
optimization.

See [`ADR-0010-optimization-decision-after-profiling.md`](../adr/ADR-0010-optimization-decision-after-profiling.md)
and [`PHASE-2-optimization-adr-decision.md`](../../tasks/reports/PHASE-2-optimization-adr-decision.md).

## Tools

- JFR for JVM-level recordings
- async-profiler for CPU and allocation profiles
- GC logs for pause and allocation behavior
- Linux `perf` when the environment supports it

Profile artifacts must include the command, workload, environment, tool
versions, timestamp and raw artifact paths. A profile observation does not
authorize an optimization; any optimization requires a separate ADR / Decision
and Human Approval.
