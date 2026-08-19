# Benchmark Baseline

## Status

The Phase 2 OrderBook baseline was executed and approved by the Human Developer
on `2026-08-19`. It is a component-level experimental measurement of the
current correctness baseline, not a production performance claim. Profiling was
executed under approved ADR-0009 and approved as evidence collection on
`2026-08-19`.

## Required Environment

Every published result must record:

- CPU model and core count
- RAM
- Operating system
- JDK and JVM arguments
- Maven and benchmark version
- Warmup and measurement duration
- Fork count
- Thread count
- Dataset size
- Order and price distributions

## Required Metrics

- Throughput
- P50
- P95
- P99
- P999
- Allocation rate, GC activity and CPU utilization when a separately approved
  profiler or measurement configuration is used

## Rules

Do not report a single run as a conclusion. Do not edit raw results manually.
Keep the benchmark command, parameters, environment and raw result location
with the result. Distinguish throughput distributions from operation-latency
percentiles; JMH `SampleTime` is the latency evidence in the current baseline.

Current result:

- Report:
  [`PHASE-2-benchmark-orderbook-baseline.md`](../../tasks/reports/PHASE-2-benchmark-orderbook-baseline.md)
- Raw JSON:
  `benchmark-results/orderbook-baseline.json` (local, ignored by Git)
- Baseline:
  TreeMap + intrusive FIFO + active OrderId index

Next gate:

- Optimization ADR / Decision Human Approval under the proposed
  [`ADR-0010-optimization-decision-after-profiling.md`](../adr/ADR-0010-optimization-decision-after-profiling.md)
  (`Proposed`; production optimization and measurement-isolation execution
  remain unauthorized)

Profiling report:

- [`PHASE-2-profiling-execution.md`](../../tasks/reports/PHASE-2-profiling-execution.md)
