# ADR-0010 - Phase 2 Optimization Decision After Profiling

## Status

Approved

## Decision Record

| Field | Value |
| --- | --- |
| Proposal date | `2026-08-19` |
| Reviewer | `Human Developer` |
| Decision date | `2026-08-19` |
| Decision | `Approved` |

Related decisions:

- [`ADR-0007-basic-orderbook-structure-and-boundaries.md`](ADR-0007-basic-orderbook-structure-and-boundaries.md)
- [`ADR-0008-structural-limit-matching.md`](ADR-0008-structural-limit-matching.md)
- [`ADR-0009-performance-profiling-evidence.md`](ADR-0009-performance-profiling-evidence.md)

## Context

Phase 2 OrderBook correctness verification, the component benchmark baseline
and JFR profiling execution were approved by the Human Developer on
`2026-08-19`. The profiling report records CPU, sampled allocation, GC and
monitor-contention observations for the fixed OrderBook workloads.

The profiling execution was accepted as evidence collection only. It was not
an unprofiled production measurement: JFR overhead was present, and JMH
`@Setup(Level.Invocation)` contributed order construction, book construction
and state-reset work to the recordings.

The current implementation remains the approved TreeMap side-book,
intrusive-FIFO and active `OrderId -> OrderNode` baseline. No production
change has been made in response to the profile.

Human Developer approved this ADR on `2026-08-19` and authorized the
measurement-isolation execution. The isolation experiment adds only a
benchmark harness and evidence-collection boundary; it does not change the
approved B0 workload result, production matching code or JVM configuration.

## Problem

The profile identifies several possible hotspots, but it does not yet isolate
the cost of the steady-state matching operation from benchmark setup and
profiler overhead. A production optimization selected from the current sample
shares could therefore optimize the harness or introduce design complexity
without proving a benefit on the matching core.

The project needs an explicit decision about whether the current evidence
justifies an optimization implementation.

## Evidence Review

The reviewed evidence is recorded in
[`PHASE-2-profiling-execution.md`](../../tasks/reports/PHASE-2-profiling-execution.md).

| Observation | Recorded evidence | Decision relevance |
| --- | --- | --- |
| Price-index path | `NaturalOrderComparator.compare` approximately `9.4% - 10.1%`; `TreeMap.getEntryUsingComparator` approximately `6.8% - 9.5%`; `TreeMap.put` approximately `5.9% - 6.1%` in `multiLevelMatch` samples | Suggests a candidate for future study, but does not establish that TreeMap replacement improves isolated matching cost |
| Active-order/state path | `HashMap.getNode` approximately `11.3% - 11.4%` in `multiLevelMatch` samples | Indicates active-index activity, but does not justify changing the approved index contract |
| Result allocation | `MatchFragment` approximately `37.61%` of the sampled allocation class view in `oneLevelMatch` | A possible candidate, but sampled allocation and invocation setup prevent an implementation decision |
| Setup construction | `Order.<init>` approximately `5.6% - 5.8%` in sampled CPU observations | Confirms that setup is present in the profile and must be isolated before interpreting production hotspots |
| GC | G1 events and pauses were observed, but values include setup and JFR overhead | Insufficient evidence for GC or JVM tuning |
| Synchronization | No sampled Java monitor-contention events in inspected recordings | Does not authorize a claim of zero synchronization cost or a concurrency change |

The profile throughput and tail samples are not replacements for the approved
unprofiled benchmark baseline. The evidence also covers one machine, one JVM
configuration, one matching-owner thread and four fixed component workloads.

## Options Considered

### Option A - Optimize `MatchFragment` or result-list allocation now

This could reduce allocation if result construction is a genuine steady-state
hotspot. The current evidence is not sufficient because the allocation view is
sampled, the one-level workload is tiny, and invocation setup is included.

**Result: Deferred.**

### Option B - Replace TreeMap or change the price index now

The profile shows comparator and TreeMap paths, but it does not compare the
approved implementation with an alternative or isolate price-index work from
state construction. A replacement would also enlarge the correctness and
benchmark surface.

**Result: Deferred.**

### Option C - Tune JVM or GC settings now

The observed GC activity includes JMH setup and JFR overhead, and no controlled
JVM configuration comparison exists.

**Result: Rejected.**

### Option D - Accept no production optimization and require measurement
isolation before selecting a candidate

This preserves the correctness baseline and requires a controlled follow-up
that separates operation cost from invocation setup before any production
change is proposed.

**Result: Accepted.**

## Decision

1. Do not authorize a production optimization from the current JFR evidence.
2. Keep the approved TreeMap, intrusive FIFO, active-index, `List<MatchFragment>`
   and single-threaded mutation design unchanged.
3. Treat `MatchFragment`/result construction, TreeMap price-index activity and
   active-index lookup as investigation candidates only, not accepted
   optimization targets.
4. Authorize a measurement-isolation execution that separates steady-state
   operation cost from `@Setup(Level.Invocation)` and profiler overhead while
   preserving the approved workload semantics.
5. Any subsequent production change requires a new or updated Optimization ADR,
   explicit Human Approval, the existing correctness gates, and a before/after
   benchmark using comparable workloads.

This ADR authorizes measurement-isolation execution only. It does not
authorize production optimization, benchmark replacement, JVM/GC tuning or
Phase 3 work.

## Measurement-Isolation Requirements

The authorized measurement-isolation stage must:

- preserve the existing B0 benchmark result and workload definitions;
- report setup and operation timing separately where the harness permits;
- avoid changing production semantics or OrderBook invariants;
- record whether the result list, `MatchFragment`, TreeMap and active-index
  paths remain material after setup is isolated;
- use the same Java/JMH configuration where comparison to B0 is claimed;
- provide a new profile or benchmark report before proposing a production
  optimization.

The measurement-isolation stage must not silently become an allocation
optimization, data-structure replacement or JVM tuning exercise.

## Scope Boundary

Authorized by this approved ADR:

- review and classification of the approved JFR evidence;
- benchmark-harness measurement isolation;
- steady-state single-level and multi-level matching measurements;
- separate lifecycle/preparation measurements;
- JFR evidence collection for the isolated matching workloads;
- documentation and phase-report synchronization.

Not authorized:

- changing `OrderBook`, `OrderNode`, `OrderQueue`, `PriceLevel`, `SideBook`,
  `TreeMap`, `HashMap`, `MatchFragment` or result-list implementation;
- changing benchmark workload semantics or replacing the B0 baseline;
- object pools, result buffers, allocation reuse, custom trees, SkipLists,
  radix indexes, price arrays, off-heap storage, Disruptor, lock-free
  mutation or cache-layout changes;
- JVM arguments, GC selection, GC tuning, CPU affinity or OS tuning;
- Trade/Execution, MatchingEngine, WAL, Network or Phase 3 work.

## Consequences and Risks

Positive consequences:

- avoids converting setup-contaminated samples into unsupported optimization
  claims;
- preserves the approved correctness and determinism baseline;
- creates a clear experiment boundary for future optimization evidence;
- keeps TreeMap and `MatchFragment` decisions reversible until isolated data
  exists.

Costs and risks:

- no immediate throughput or allocation improvement is delivered;
- an additional measurement stage is required before production optimization;
- the current profile cannot answer the final steady-state cost question;
- future candidates may be rejected after the additional experiment.

## Verification Plan

For this ADR / Decision and Measurement-Isolation stage:

- review the profiling execution report and raw artifact paths;
- confirm that no production matching code, B0 result, or JVM configuration
  changes;
- verify the isolated benchmark keeps setup outside the measured matching
  invocation;
- record the exact isolation results, JFR paths and profiler limitations;
- run `git diff --check`;
- run `mvn verify` as the repository quality gate;
- verify that the task plan, ADR, phase report, performance documents and
  `.codex` context describe the same post-isolation approval gate.

No production optimization benchmark or implementation is authorized by this
stage.

## Current Gate

The approved decision and completed experiment have the following status:

```text
Optimization ADR / Decision:
    Approved

Production Optimization:
    Not Authorized

Measurement-Isolation Execution:
    Completed - Pending Human Approval

Phase 3:
    Not Authorized
```

## Human Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | `Approved` | ADR-0010 accepted. Current JFR evidence is insufficient to justify production optimization because benchmark setup and steady-state matching costs are not isolated. Measurement-Isolation Execution is authorized. Production optimization and Phase 3 remain unauthorized. |
