# OrderBook Benchmark

## Status

Phase 2 OrderBook baseline implemented, measured and approved by the Human
Developer on `2026-08-19`. ADR-0009 was approved by the Human Developer on
`2026-08-19`; profiling execution was completed under ADR-0009 and approved as
evidence collection. ADR-0010 was approved on `2026-08-19`; measurement
isolation, evidence review and Phase 2 Final Closure completed. No
optimization has been executed.

## Planned Comparisons

- Price-level insert
- Best bid and ask lookup
- Cancel by `OrderId`
- Matching across one price level
- Matching across multiple price levels
- Empty price-level cleanup

## Comparison Rule

All alternatives must process the same generated event stream and validate the same final state.

## Current Baseline Workload

The current single-threaded JMH baseline measures:

- one fresh price-level insertion;
- Best Bid and Best Ask lookup on 64 levels per side;
- cancellation by active `OrderId` while retaining the price level;
- cancellation that removes the last order and empty price level;
- one-level exact matching;
- a deterministic 64-level multi-level ask sweep.

The workload uses fixed positive integer prices and quantities, deterministic
sequences, two forks, three one-second warmup iterations and five one-second
measurement iterations. Throughput is reported in `ops/us`; `SampleTime` is
reported in `us/op` with JMH percentiles. Allocation and GC were not measured
in this run.

See the full environment, command, raw result path and table in
[`PHASE-2-benchmark-orderbook-baseline.md`](../../tasks/reports/PHASE-2-benchmark-orderbook-baseline.md).

The profiling decision and execution evidence are recorded in
[`ADR-0009-performance-profiling-evidence.md`](../adr/ADR-0009-performance-profiling-evidence.md)
and [`PHASE-2-profiling-execution.md`](../../tasks/reports/PHASE-2-profiling-execution.md).
The isolation harness and evidence are recorded in
[`PHASE-2-measurement-isolation.md`](../../tasks/reports/PHASE-2-measurement-isolation.md).
