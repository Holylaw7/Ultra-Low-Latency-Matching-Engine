# Phase 2 Report - Profiling Execution

## 1. Report Metadata

| Field | Value |
| --- | --- |
| Phase | `Phase 2 - Basic OrderBook` |
| Stage | `Profiling Execution` |
| Task | `TASK-20260819-004-basic-orderbook` |
| ADR | [`ADR-0009-performance-profiling-evidence.md`](../../docs/adr/ADR-0009-performance-profiling-evidence.md) |
| Report Date | `2026-08-19` |
| Stage Status | `Completed - Approved` |
| Next Approval Gate | `Optimization ADR / Decision` |

## 2. Authorization and Objective

Human Developer approved ADR-0009 and the Profiling ADR / Decision stage on
`2026-08-19`. Profiling execution was authorized against the committed Phase 2
OrderBook baseline.

The objective was to collect controlled JFR evidence for:

- CPU hot paths;
- sampled allocation sites;
- garbage-collection observations; and
- sampled monitor-contention observations.

The primary workload was `multiLevelMatch`. `oneLevelMatch`,
`cancelByOrderId` and `cancelAndCleanEmptyLevel` were collected as supporting
workloads.

No production code, test code, benchmark semantics or JVM/GC tuning was
changed during this stage. Optimization and Phase 3 remain unauthorized.

## 3. Environment

| Item | Value |
| --- | --- |
| OS | Windows 11 x64, build `10.0.26200` |
| CPU | 13th Gen Intel Core i9-13900H |
| Benchmark hardware metadata | 14 physical cores / 20 logical processors |
| JFR System Information metadata | 10 cores / 20 hardware threads |
| RAM | 33,968,349,184 bytes reported by Windows |
| JDK | Microsoft OpenJDK `21.0.12` |
| JVM | `E:\Java\microsoft-jdk-21\bin\java.exe` |
| Maven | Apache Maven `3.9.16` |
| JMH | `1.37` |
| JFR | JDK Flight Recorder `1.0` |
| async-profiler | Not available in this environment |
| JMH forks | `2` |
| JMH threads | `1` |
| Warmup | `3 x 1 s` |
| Measurement | `5 x 1 s` |
| JVM arguments | No additional tuning arguments |

The core-count difference is retained as a metadata limitation. It was not
resolved by changing the benchmark or JVM configuration.

## 4. Method and Command Pattern

The committed `OrderBookBaselineBenchmark` was profiled with the JMH built-in
JFR profiler. The same fork, thread, warmup and measurement parameters as the
approved baseline were used. Because the workspace path contains non-ASCII
characters, the profiling commands used a temporary `X:` drive mapping during
collection.

Command pattern:

```text
subst X: "E:\学习\Ultra-Low-Latency Matching Engine"
java -jar X:\benchmark\target\matching-engine-benchmark-0.1.0-SNAPSHOT.jar \
  OrderBookBaselineBenchmark.<workload> \
  -f 2 -wi 3 -i 5 -w 1s -r 1s -t 1 \
  "-prof" "jfr:dir=X:\profiler-results\jfr-<workload>;configName=profile;verbose=true" \
  -rf json \
  -rff X:\profiler-results\jfr-<workload>.json
```

The mapping was removed after collection with:

```text
subst X: /d
```

The profile recordings include JFR measurement overhead and are not directly
comparable with the unprofiled benchmark baseline.

## 5. Workloads and Raw Artifacts

| Workload | Workload shape | JMH JSON | JFR recordings |
| --- | --- | --- | --- |
| `multiLevelMatch` | 64 contiguous ask levels swept by one crossing buy order | `profiler-results/jfr-multi-level.json` | `profiler-results/jfr-multi-level/` |
| `oneLevelMatch` | One ask maker at 100 x 1 and one crossing buy taker | `profiler-results/jfr-one-level.json` | `profiler-results/jfr-one-level/` |
| `cancelByOrderId` | Two same-price buy orders; cancel the first while retaining the level | `profiler-results/jfr-cancel-by-id.json` | `profiler-results/jfr-cancel-by-id/` |
| `cancelAndCleanEmptyLevel` | One buy order; cancel it and remove the empty level | `profiler-results/jfr-cancel-clean.json` | `profiler-results/jfr-cancel-clean/` |

Each JFR directory contains separate `Throughput` and `SampleTime`
recordings. The raw recordings are local ignored evidence and are not
hand-edited.

## 6. Profiling Measurements

The following values are from the profiling runs and include JFR overhead.
They are evidence for profile interpretation only, not replacements for the
approved unprofiled baseline.

| Workload | Throughput (ops/us) | Sample mean (us/op) | P99 | P99.9 | P99.99 | Max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `oneLevelMatch` | `19.994 +/- 3.143` | `0.082` | `0.100` | `7.748` | Not reported | `262.144` |
| `multiLevelMatch` | `0.358 +/- 0.065` | `3.210` | `25.299` | `141.829` | `399.889` | `1284.096` |
| `cancelByOrderId` | `34.130 +/- 1.932` | `0.045` | `0.100` | `0.900` | `28.786` | `243.200` |
| `cancelAndCleanEmptyLevel` | `33.394 +/- 2.091` | `0.058` | `0.100` | `4.800` | `55.205` | `280.576` |

The recorded JFR output is not a latency claim. Tail samples are especially
sensitive to profiler overhead, fork selection and setup activity.

## 7. CPU Hotspot Observations

Across the `multiLevelMatch` Throughput and SampleTime recordings, the main
sampled CPU observations were:

| Method or path | Approximate sampled share |
| --- | ---: |
| `HashMap.getNode` | `11.3% - 11.4%` |
| `NaturalOrderComparator.compare` | `9.4% - 10.1%` |
| `TreeMap.getEntryUsingComparator` | `6.8% - 9.5%` |
| `TreeMap.put` | `5.9% - 6.1%` |
| `Order.<init>` | `5.6% - 5.8%` |
| `OrderBook.matchLimit` | approximately `3.9%` |

Other observed paths include TreeMap balancing/navigation, first-entry
lookup, removal, `OrderBook.applyExecution` and
`SideBook.removeEmptyLevel`.

These are profile observations, not optimization decisions. JMH
`@Setup(Level.Invocation)` is included in the recordings, so `Order.<init>`,
book construction and related setup samples cannot be treated as pure
production matching-core cost.

## 8. Allocation Observations

JFR allocation views in this stage are sampled allocation observations, not
precise allocation-rate measurements.

- In `multiLevelMatch`, Throughput recordings prominently sampled
  `OrderNode`, `HashMap$Node`, `OrderQueue` and `PriceLevel.add`; SampleTime
  recordings prominently sampled `Optional`, `OrderNode` and
  `MatchFragment`.
- In `oneLevelMatch`, `MatchFragment` represented approximately `37.61%` of
  the sampled allocation class view, and the `OrderBook.matchLimit` site
  represented approximately `37.62%` of sampled allocation sites.
- Cancellation recordings are materially affected by invocation setup and
  book construction. They do not establish the isolated allocation cost of
  cancel, unlink or empty-level cleanup.

The observations identify possible future investigation candidates only.
Object reuse, result-buffer changes, collection changes and allocation
reductions are not authorized by this report.

## 9. GC and Synchronization Observations

The recordings used the default G1 collector. Approximate observations were:

| Workload | GC events | Aggregate pause | Maximum observed pause |
| --- | ---: | ---: | ---: |
| `multiLevelMatch` | `41` | `35.1 - 35.5 ms` | `approximately 1.23 - 1.55 ms` |
| `oneLevelMatch` | `57 - 61` | `46.9 - 50.5 ms` | `approximately 1.23 - 1.55 ms` |
| `cancelByOrderId` | `60 - 61` | `48.1 - 51.4 ms` | `approximately 1.23 - 1.55 ms` |
| `cancelAndCleanEmptyLevel` | `54 - 58` | `46.3 - 46.5 ms` | `approximately 1.23 - 1.55 ms` |

The exact pause distribution depends on the recording and JMH fork. These
values include benchmark setup and JFR overhead.

No Java monitor-contention events were captured in the inspected recordings:

```text
contention-by-class: no events
contention-by-site: no events
contention-by-thread: no events
```

This means no sampled monitor contention was observed in these recordings. It
does not prove that the JVM has zero synchronization cost.

## 10. Limitations

- JFR introduces measurement overhead; profiling throughput and latency must
  not be compared as if they were baseline measurements.
- The retained JFR files are per recorded JMH output and are not a merged
  all-fork profile. The report does not treat them as a population-wide
  profile.
- JMH `@Setup(Level.Invocation)` contributes setup, object construction and
  state-reset samples to the recordings.
- JFR allocation views are sampled and cannot be used as exact allocation
  rates.
- The experiment covers one machine, one JVM configuration, one thread and
  four fixed component workloads.
- async-profiler was unavailable, so no supplementary native-stack profile
  was collected.
- The JFR System Information core count differs from the benchmark environment
  metadata; both values are recorded rather than silently normalized.
- No end-to-end ingress, serialization, event pipeline, WAL, recovery,
  network, MatchingEngine or Trade/Execution workload was profiled.

## 11. Interpretation and Future Candidates

The strongest current observation is that multi-level matching samples both
the price-index path (`TreeMap` navigation, comparator and mutation) and
active-order/state paths (`HashMap`, execution and cleanup). Allocation
observations also identify `MatchFragment` and setup-related objects as
possible investigation candidates.

These observations do not select an optimization. A future optimization
proposal would need to isolate setup from operation cost, define a controlled
hypothesis, create a separate Optimization ADR, obtain Human Approval, and
re-run the same correctness and benchmark gates.

No optimization was implemented. No production code, tests, benchmark
semantics, JVM arguments, GC settings or data structures were changed.

## 12. Verification

The profiling execution itself made no source changes. The final repository
verification for this documentation stage completed successfully:

```text
mvn verify -> BUILD SUCCESS
45 tests, 0 failures, 0 errors, 0 skipped
Checkstyle -> 0 violations
git diff --check
git status --short --branch -> documentation changes pending commit
subst X: /d -> completed
```

The Maven Shade Plugin duplicate class/resource warnings remain known
technical debt and did not fail the build. No source, test or benchmark
semantics changed during verification.

## 13. Documentation Synchronization

This report is synchronized with:

- `docs/adr/ADR-0009-performance-profiling-evidence.md`;
- `docs/performance/profiling.md`;
- `docs/benchmark/baseline.md`;
- `docs/benchmark/orderbook.md`;
- `docs/architecture/order-book.md`;
- `docs/architecture/matching-engine.md`;
- `README.md`;
- `.codex/AGENT_CONTEXT.md`;
- `.codex/MASTER_PROMPT.md`; and
- `tasks/completed/TASK-20260819-004-basic-orderbook.md`.

## 14. Approval and Hand-off

Human Developer approved the Profiling Execution evidence and documentation
synchronization on `2026-08-19`.

```text
Profiling Execution:
    Completed - Approved

Optimization:
    ADR / Decision - Next Gate

Phase 3:
    Not Authorized
```

The next stage is an Optimization ADR / Decision proposal based on the
recorded evidence. This report does not authorize that proposal's
implementation.

## 15. Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | `Approved` | Profiling execution completed using the authorized fixed workloads and JFR evidence collection. The profiling phase is accepted as evidence collection only. Optimization and Phase 3 remain unauthorized pending evidence review and a separate optimization decision. |
