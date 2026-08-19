# Phase 2 Report - OrderBook Baseline Benchmark

## 1. Report Metadata

| Field | Value |
| --- | --- |
| Phase | `Phase 2 - Basic OrderBook` |
| Stage | `Benchmark Baseline - OrderBook` |
| Task | `TASK-20260819-004-basic-orderbook` |
| ADR | [`ADR-0007-basic-orderbook-structure-and-boundaries.md`](../../docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md); [`ADR-0008-structural-limit-matching.md`](../../docs/adr/ADR-0008-structural-limit-matching.md) |
| Report Date | `2026-08-19` |
| Stage Status | `Completed - Pending Human Approval` |
| Next Approval Gate | `Pending Human Approval` |

## 2. Authorization and Objective

Human Developer explicitly authorized the Phase 2 OrderBook baseline benchmark
on `2026-08-19`. The objective was to measure the approved TreeMap +
intrusive FIFO + active OrderId index implementation with reproducible JMH
parameters. This stage does not authorize profiling, optimization, alternative
data structures or Phase 3 work.

## 3. Environment

| Item | Value |
| --- | --- |
| OS | Windows 11 x64, build `10.0.26200` |
| CPU | 13th Gen Intel Core i9-13900H |
| Physical cores | 14 |
| Logical processors | 20 |
| RAM | 33,968,349,184 bytes reported by Windows |
| JDK | Microsoft OpenJDK `21.0.12` |
| JVM | `E:\Java\microsoft-jdk-21\bin\java.exe` |
| Maven | Apache Maven `3.9.16` |
| JMH | `1.37` |
| Benchmark module | `matching-engine-benchmark-0.1.0-SNAPSHOT` |

## 4. Method

JMH configuration:

```text
Forks: 2
Threads: 1
Warmup: 3 iterations x 1 second
Measurement: 5 iterations x 1 second
Modes: Throughput, SampleTime
Output unit: microseconds
```

Command:

```text
java -jar benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar \
  OrderBookBaselineBenchmark \
  -f 2 -wi 3 -i 5 -w 1s -r 1s -t 1 -rf json \
  -rff benchmark-results/orderbook-baseline.json
```

The command was run with the Java 21 executable listed above. The raw JSON
result is local at `benchmark-results/orderbook-baseline.json` and is ignored
by Git; it must not be hand-edited.

## 5. Workloads

| Benchmark | Fixed workload |
| --- | --- |
| `priceLevelInsertion` | Fresh book, one BUY order at price 100, quantity 1 |
| `bestBidLookup` | 64 bid levels and 64 ask levels, lookup on populated book |
| `bestAskLookup` | 64 bid levels and 64 ask levels, lookup on populated book |
| `cancelByOrderId` | Two same-price BUY orders, cancel the first, retain level |
| `cancelAndCleanEmptyLevel` | One BUY order, cancel it and remove the level |
| `oneLevelMatch` | One SELL maker at 100 x 1, BUY taker at 101 x 1 |
| `multiLevelMatch` | 64 contiguous SELL levels at 100..163, BUY taker consumes all |

All inputs use positive integer domain units and deterministic sequences. Fresh
state is created for mutating cases at invocation setup. Benchmark methods
consume operation results with JMH `Blackhole`.

## 6. Results

### Throughput

JMH reports throughput in `ops/us`. The error is the JMH reported error for the
two-fork result; it is not a production confidence guarantee.

| Benchmark | Score (ops/us) | Error |
| --- | ---: | ---: |
| `bestAskLookup` | 438.150231 | +/- 39.239889 |
| `bestBidLookup` | 395.358693 | +/- 35.816348 |
| `cancelAndCleanEmptyLevel` | 19.212765 | +/- 1.269741 |
| `cancelByOrderId` | 19.042879 | +/- 1.214885 |
| `multiLevelMatch` | 0.212470 | +/- 0.011274 |
| `oneLevelMatch` | 11.392665 | +/- 1.014064 |
| `priceLevelInsertion` | 15.769354 | +/- 1.286026 |

### Sample Time

JMH reports `SampleTime` in `us/op`. Percentiles below are operation-time
percentiles from the raw JSON result.

| Benchmark | Mean | P50 | P95 | P99 | P999 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `bestAskLookup` | 0.042359 | 0.0 | 0.1 | 0.1 | 0.2 |
| `bestBidLookup` | 0.047769 | 0.0 | 0.1 | 0.1 | 0.2 |
| `cancelAndCleanEmptyLevel` | 0.062510 | 0.1 | 0.1 | 0.1 | 0.2 |
| `cancelByOrderId` | 0.058047 | 0.0 | 0.1 | 0.1 | 0.1 |
| `multiLevelMatch` | 4.552203 | 3.4 | 8.592 | 10.592 | 80.832896 |
| `oneLevelMatch` | 0.103360 | 0.1 | 0.2 | 0.2 | 2.1 |
| `priceLevelInsertion` | 0.075610 | 0.1 | 0.1 | 0.2 | 3.7 |

The very small lookup samples include timer-resolution quantization in the
reported percentiles. They should be treated as baseline observations, not as
final latency claims.

## 7. Verification and Scope

Before recording the benchmark, the repository passed:

```text
mvn verify
45 tests, 0 failures, 0 errors, 0 skipped
Checkstyle: 0 violations
BUILD SUCCESS

git diff --check
PASS
```

The benchmark source is:

`benchmark/src/main/java/com/ultralatency/matching/benchmark/OrderBookBaselineBenchmark.java`

No custom tree, SkipList, radix, price array, object pool, off-heap storage,
Disruptor, lock-free mutation, profiler or GC tuning was introduced.

## 8. Limitations and Known Technical Debt

- This is one machine, one JVM configuration, one thread and one fixed
  micro-workload set.
- No allocation-rate, GC, CPU-utilization or JFR/async-profiler evidence was
  collected.
- No comparison with an alternative data structure was authorized.
- No production throughput, production P99, or million-orders-per-second
  conclusion is authorized.
- Maven Shade Plugin duplicate class/resource warnings remain known packaging
  technical debt and did not block this baseline.
- The raw result is local and ignored by Git; this report records its path and
  the command used to produce it.

## 9. Documentation Synchronization

Synchronized after the benchmark run:

- `README.md`;
- `docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md`;
- `docs/adr/ADR-0008-structural-limit-matching.md`;
- `docs/architecture/order-book.md`;
- `docs/architecture/matching-engine.md`;
- `docs/benchmark/baseline.md`;
- `docs/benchmark/orderbook.md`;
- `.codex/AGENT_CONTEXT.md`;
- `.codex/MASTER_PROMPT.md`;
- `tasks/active/TASK-20260819-004-basic-orderbook.md`.

## 10. Approval Request

请求 Human Developer 审查并批准本 Benchmark baseline 及同步文档。批准后
才可以讨论 Profiling、Optimization 或 Phase 3；本报告本身不授权这些工作。

```text
Benchmark Baseline:
    Completed - Pending Human Approval

Documentation and Synchronization:
    Completed - Pending Human Approval

Next Gate:
    Pending Human Approval
```

## 11. Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
|  |  | `Pending` | Baseline evidence and synchronized documentation await Human Approval. |
