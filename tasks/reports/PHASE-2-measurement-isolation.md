# Phase 2 Report - Measurement Isolation

## 1. Report Metadata

| Field | Value |
| --- | --- |
| Phase | `Phase 2 - Basic OrderBook` |
| Stage | `Measurement-Isolation Execution` |
| Task | `TASK-20260819-004-basic-orderbook` |
| ADR | [`ADR-0010-optimization-decision-after-profiling.md`](../../docs/adr/ADR-0010-optimization-decision-after-profiling.md) |
| Input Evidence | [`PHASE-2-profiling-execution.md`](PHASE-2-profiling-execution.md) |
| Report Date | `2026-08-19` |
| Stage Status | `Completed - Pending Human Approval` |
| Next Approval Gate | `Human Approval - Steady-State Evidence Review` |

## 2. Authorization and Objective

Human Developer approved ADR-0010 on `2026-08-19` and authorized
Measurement-Isolation Execution. The objective was to separate benchmark
state-preparation cost from the measured structural matching path before
selecting any production optimization.

This stage does not replace or overwrite the approved B0 benchmark. It adds a
separate experiment and records its results as isolation evidence only.

## 3. Scope

The authorized changes are limited to:

- a benchmark harness for prebuilt, independent matching cases;
- separate lifecycle/preparation measurements;
- JFR collection for the isolated matching workloads;
- raw-result and documentation synchronization.

The following remained unchanged:

- `OrderBook`, `OrderNode`, `OrderQueue`, `PriceLevel`, `SideBook` and
  `MatchFragment`;
- TreeMap, HashMap and matching semantics;
- the approved B0 benchmark and its raw result;
- JVM and GC configuration;
- MatchingEngine, Trade/Execution, WAL, Network and Phase 3 scope.

## 4. Implementation

Added:

`benchmark/src/main/java/com/ultralatency/matching/benchmark/OrderBookMeasurementIsolationBenchmark.java`

The benchmark defines:

- `M1 steadyStateSingleLevelMatch`;
- `M2 steadyStateMultiLevelMatch`;
- `M3 lifecycleSingleLevelPreparation`;
- `M3 lifecycleMultiLevelPreparation`.

The matching cases are constructed in `@Setup(Level.Iteration)` as an array of
independent cases. Each case is consumed once by the measured invocation, so a
completed order book is not reused. The measured matching methods execute the
real `OrderBook.matchLimit()` path and consume the result through JMH
`Blackhole`.

The lifecycle methods construct the same case shapes separately. They are
reported as preparation-cost evidence, not as matching latency.

## 5. Environment and Commands

The existing approved environment was retained:

| Item | Value |
| --- | --- |
| OS | Windows 11 x64, build `10.0.26200` |
| CPU | 13th Gen Intel Core i9-13900H |
| Physical cores | 14 |
| Logical processors | 20 |
| RAM | 33,968,349,184 bytes reported by Windows |
| JDK | Microsoft OpenJDK `21.0.12` |
| JVM | `E:\Java\microsoft-jdk-21\bin\java.exe` |
| JMH | `1.37` |
| Forks | `2` |
| Threads | `1` |
| Warmup | `3 x 1 s` |
| Measurement | `5 x 1 s` |
| Benchmark mode | `SingleShotTime` |
| Operations per invocation | `1024` |

The workspace contains non-ASCII path components. The commands used an
ASCII `X:` drive mapping for JFR output. The mapping changes path resolution
only; it does not change the source or workload.

Benchmark commands:

```text
E:\Java\microsoft-jdk-21\bin\java.exe -jar \
  X:\benchmark\target\matching-engine-benchmark-0.1.0-SNAPSHOT-shaded.jar \
  OrderBookMeasurementIsolationBenchmark.steadyStateSingleLevelMatch \
  -f 2 -wi 3 -i 5 -w 1s -r 1s -t 1 -rf json \
  -rff X:\benchmark-results\orderbook-isolation-m1.json

E:\Java\microsoft-jdk-21\bin\java.exe -jar \
  X:\benchmark\target\matching-engine-benchmark-0.1.0-SNAPSHOT-shaded.jar \
  OrderBookMeasurementIsolationBenchmark.steadyStateMultiLevelMatch \
  -f 2 -wi 3 -i 5 -w 1s -r 1s -t 1 -rf json \
  -rff X:\benchmark-results\orderbook-isolation-m2.json

E:\Java\microsoft-jdk-21\bin\java.exe -jar \
  X:\benchmark\target\matching-engine-benchmark-0.1.0-SNAPSHOT-shaded.jar \
  OrderBookMeasurementIsolationBenchmark.lifecycleSingleLevelPreparation \
  -f 2 -wi 3 -i 5 -w 1s -r 1s -t 1 -rf json \
  -rff X:\benchmark-results\orderbook-isolation-m3.json
```

The same parameters were used for the multi-level lifecycle preparation
method. The executed result files were split by workload:

```text
benchmark-results/orderbook-isolation-m1.json
benchmark-results/orderbook-isolation-m2.json
benchmark-results/orderbook-isolation-m3.json
```

JFR commands:

```text
E:\Java\microsoft-jdk-21\bin\java.exe -jar \
  X:\benchmark\target\matching-engine-benchmark-0.1.0-SNAPSHOT-shaded.jar \
  OrderBookMeasurementIsolationBenchmark.steadyStateSingleLevelMatch \
  -f 2 -wi 3 -i 5 -w 1s -r 1s -t 1 -rf json \
  -rff X:\profiler-results\isolation-m1\result.json \
  -prof "jfr:dir=X:\profiler-results\isolation-m1;configName=profile;verbose=true"

E:\Java\microsoft-jdk-21\bin\java.exe -jar \
  X:\benchmark\target\matching-engine-benchmark-0.1.0-SNAPSHOT-shaded.jar \
  OrderBookMeasurementIsolationBenchmark.steadyStateMultiLevelMatch \
  -f 2 -wi 3 -i 5 -w 1s -r 1s -t 1 -rf json \
  -rff X:\profiler-results\isolation-m2\result.json \
  -prof "jfr:dir=X:\profiler-results\isolation-m2;configName=profile;verbose=true"
```

## 6. Isolation Benchmark Results

The unprofiled isolation measurements are:

| Workload | Mean | Error | Unit |
| --- | ---: | ---: | --- |
| `M1 steadyStateSingleLevelMatch` | `0.423779` | `+/- 0.127744` | `us/op` |
| `M2 steadyStateMultiLevelMatch` | `5.590215` | `+/- 1.206605` | `us/op` |
| `M3 lifecycleSingleLevelPreparation` | `0.459092` | `+/- 0.094683` | `us/op` |
| `M3 lifecycleMultiLevelPreparation` | `5.772109` | `+/- 1.136706` | `us/op` |

These values are not directly comparable to B0's Throughput or SampleTime
tables. The isolation benchmark uses `SingleShotTime`, batches 1024
independent cases per invocation and reports the per-operation value. B0
remains the approved baseline and is not modified.

The multi-level preparation result is close to the isolated multi-level
matching result in this experiment. That observation confirms that setup was
large enough to warrant isolation, but it is not a proof that either path is
the production bottleneck.

## 7. JFR Artifact and Evidence Review

The JFR recordings were successfully written through the ASCII path mapping:

```text
profiler-results/isolation-m1/
  com.ultralatency.matching.benchmark.OrderBookMeasurementIsolationBenchmark.steadyStateSingleLevelMatch-SingleShotTime/profile.jfr

profiler-results/isolation-m2/
  com.ultralatency.matching.benchmark.OrderBookMeasurementIsolationBenchmark.steadyStateMultiLevelMatch-SingleShotTime/profile.jfr
```

JFR summary observations:

| Recording | Execution samples | Allocation samples | GC events | Java monitor events |
| --- | ---: | ---: | ---: | ---: |
| M1 | `0` | `27` | `0` | `0` |
| M2 | `14` | `120` | `0` | `0` |

M1 has no `ExecutionSample` events, so it does not support a CPU-hotspot
conclusion. M2 has a small execution-sample population. Its top sampled
method was `OrderBook.matchLimit(Order)` with `6/14` samples (`42.86%`).
Other sampled methods included TreeMap deletion/navigation and
`MultiLevelState.setUp()`, each with one sample.

The sampled allocation views included benchmark/JMH infrastructure. For
example, M2 reported `ConcurrentHashMap$ValueIterator` at `47.60%` and
`MatchFragment` at `21.27%` of the sampled allocation-class view. This is not
an exact allocation rate and cannot be converted into a claim that
`MatchFragment` consumes `21.27%` of matching latency.

No GC or Java monitor events were recorded in either isolated recording. This
does not prove zero GC or zero synchronization cost; it only records that
these event types were absent from these short recordings.

JMH's JFR profiler output also showed substantial profiler-sensitive outliers:

| Profiled workload | JFR-run mean | JFR-run p50 | JFR-run p95 |
| --- | ---: | ---: | ---: |
| M1 | `1.827 us/op` | `1.661 us/op` | `2.837 us/op` |
| M2 | `25.671 us/op` | `12.846 us/op` | `82.374 us/op` |

These profiled timings are evidence of measurement overhead and variability,
not replacement benchmark results.

JMH uses the same JFR output filename for both forks in each directory. The
artifact retained in each directory is therefore the last fork's recording,
not a merged two-fork profile. The JMH JSON result still records both forks.

## 8. Interpretation

The isolation benchmark successfully moves order-book and case construction
outside the measured matching loop and provides a separate lifecycle
measurement. It therefore addresses the primary measurement-contamination
hypothesis from ADR-0010.

The isolated JFR evidence remains too small and too profiler-sensitive to
select a production optimization:

- M1 has no CPU execution samples;
- M2 has only 14 execution samples;
- allocation samples include JMH/runtime infrastructure;
- JFR-run M2 has large outliers;
- no controlled before/after implementation comparison exists.

The evidence does not justify changing TreeMap, HashMap, `MatchFragment`,
result-list construction, JVM settings or GC settings.

## 9. Verification

The benchmark module and core dependencies passed:

```text
mvn -pl benchmark -am verify
BUILD SUCCESS
45 tests, 0 failures
Checkstyle: 0 violations
```

The repository also passed:

```text
git diff --check
PASS
```

The isolation benchmark is the only tracked source change in this stage.
Production matching code, B0 benchmark source, JVM configuration and GC
configuration were not changed.

## 10. Documentation Synchronization

Synchronized with:

- [`ADR-0010-optimization-decision-after-profiling.md`](../../docs/adr/ADR-0010-optimization-decision-after-profiling.md);
- [`PHASE-2-optimization-adr-decision.md`](PHASE-2-optimization-adr-decision.md);
- [`TASK-20260819-004-basic-orderbook.md`](../completed/TASK-20260819-004-basic-orderbook.md);
- [`README.md`](../../README.md);
- [`docs/performance/profiling.md`](../../docs/performance/profiling.md);
- [`docs/performance/optimization-history.md`](../../docs/performance/optimization-history.md);
- [`docs/benchmark/baseline.md`](../../docs/benchmark/baseline.md);
- [`docs/benchmark/orderbook.md`](../../docs/benchmark/orderbook.md);
- [`.codex/AGENT_CONTEXT.md`](../../.codex/AGENT_CONTEXT.md); and
- [`.codex/MASTER_PROMPT.md`](../../.codex/MASTER_PROMPT.md).

## 11. Approval and Hand-off

```text
Optimization ADR / Decision:
    Approved

Measurement-Isolation Execution:
    Completed - Approved for Phase 2 closure evidence

Steady-State Evidence Review:
    Completed - proceed to Phase 2 Final Closure Review

Production Optimization:
    Not Authorized

Phase 3:
    Not Authorized
```

Human Developer accepted the completed Phase 2 evidence set on `2026-08-20`
as ready for Final Closure Review. This approval freezes the evidence as a
baseline; it does not authorize a production optimization proposal or Phase 3.

## 12. Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | `Pending` | Measurement isolation completed. The measured region now contains the structural matching path only, with lifecycle preparation measured separately; current JFR sample counts and profiler outliers remain insufficient for production optimization. |
| 2026-08-20 | Human Developer | `Approved for Phase 2 closure evidence` | Measurement Isolation accepted as complete. All Phase 2 capability/evidence tracks are at 100%; proceed to Final Closure Review. Production optimization remains unauthorized. |
