# Phase 2 Report - Optimization ADR / Decision

## 1. Report Metadata

| Field | Value |
| --- | --- |
| Phase | `Phase 2 - Basic OrderBook` |
| Stage | `Optimization ADR / Decision` |
| Task | `TASK-20260819-004-basic-orderbook` |
| ADR | [`ADR-0010-optimization-decision-after-profiling.md`](../../docs/adr/ADR-0010-optimization-decision-after-profiling.md) |
| Input Evidence | [`PHASE-2-profiling-execution.md`](PHASE-2-profiling-execution.md) |
| Report Date | `2026-08-19` |
| Stage Status | `Completed - Approved` |
| Next Approval Gate | `Human Approval - Steady-State Evidence Review` |

## 2. Authorization and Objective

Human Developer approved Profiling Execution on `2026-08-19`. This stage is
limited to reviewing the accepted JFR evidence and preparing an Optimization
ADR / Decision proposal.

The objective is to determine whether the current profile supports a
production optimization target. No production code, test code, benchmark
semantics, JVM arguments or GC settings are changed in this stage.

Human Developer approved ADR-0010 on `2026-08-19` and authorized the separate
Measurement-Isolation Execution stage.

## 3. Evidence Reviewed

The reviewed evidence contains four fixed-workload JFR recordings:

- `multiLevelMatch`;
- `oneLevelMatch`;
- `cancelByOrderId`; and
- `cancelAndCleanEmptyLevel`.

The strongest observations are:

| Area | Observation | Limitation |
| --- | --- | --- |
| Price index | Comparator and TreeMap navigation/mutation are visible in multi-level samples | No isolated comparison against an alternative index |
| Active index | `HashMap.getNode` is a material sampled CPU path | Does not prove that the active-index contract or implementation should change |
| Result construction | `MatchFragment` is prominent in one-level sampled allocation views | Allocation is sampled and setup is included |
| Setup | `Order.<init>` and book construction appear in sampled paths | `@Setup(Level.Invocation)` contaminates operation interpretation |
| GC | G1 activity and pauses were recorded | Values include setup and JFR overhead |
| Synchronization | No sampled Java monitor-contention events | Not proof of zero synchronization cost |

The full commands, environment, raw paths, measurements and limitations remain
in the profiling execution report.

## 4. Decision Proposal

The current evidence does **not** justify selecting or implementing a
production optimization.

The approved decision is:

```text
Production optimization:
    Deferred

Next evidence step:
    Measurement isolation execution, separately authorized

Phase 3:
    Not Authorized
```

The candidates `MatchFragment`/result allocation, TreeMap price-index work and
active-index lookup remain investigation candidates only. No candidate is
accepted as an optimization target.

## 5. Rationale

The JFR recordings are useful for locating areas that deserve investigation,
but they mix steady-state operation with benchmark setup and profiler
overhead. In particular:

- sampled allocation percentages cannot be converted into exact allocation
  rates;
- `Order.<init>` and collection construction appear in the profile;
- JFR throughput and tail samples cannot replace the unprofiled B0 baseline;
- TreeMap and HashMap observations do not establish the benefit of replacing
  approved data structures; and
- GC observations do not justify JVM or collector tuning.

Selecting an optimization now would therefore be an inference beyond the
available evidence.

## 6. Authorized Scope Requested

After Human approval of ADR-0010, the next stage is the authorized
measurement-isolation execution. It must preserve the approved B0 workload and
correctness semantics, and it must stop at a separate Human Approval gate
before any production optimization is considered.

This report and its approval do not authorize:

- production code changes;
- replacing or overwriting the B0 benchmark;
- result-buffer or object-pool work;
- TreeMap or active-index replacement;
- JVM/GC tuning;
- performance claims; or
- Phase 3 implementation.

## 7. Verification

The decision-stage work is documentation-only. The expected repository checks
are:

```text
git diff --check
mvn verify
```

The profiling execution baseline already passed:

```text
45 tests
0 failures
0 errors
0 skipped
Checkstyle: 0 violations
```

## 8. Documentation Synchronization

This report is synchronized with:

- [`ADR-0010-optimization-decision-after-profiling.md`](../../docs/adr/ADR-0010-optimization-decision-after-profiling.md);
- [`ADR-0009-performance-profiling-evidence.md`](../../docs/adr/ADR-0009-performance-profiling-evidence.md);
- [`docs/performance/profiling.md`](../../docs/performance/profiling.md);
- [`docs/performance/optimization-history.md`](../../docs/performance/optimization-history.md);
- [`README.md`](../../README.md);
- [`.codex/AGENT_CONTEXT.md`](../../.codex/AGENT_CONTEXT.md);
- [`.codex/MASTER_PROMPT.md`](../../.codex/MASTER_PROMPT.md); and
- [`TASK-20260819-004-basic-orderbook.md`](../active/TASK-20260819-004-basic-orderbook.md).

## 9. Current Handoff

Human Developer approved ADR-0010 on `2026-08-19` and authorized the
Measurement-Isolation Execution stage. That experiment is complete and is
recorded separately in
[`PHASE-2-measurement-isolation.md`](PHASE-2-measurement-isolation.md).

```text
Optimization ADR / Decision:
    Approved

Production Optimization:
    Not Authorized

Measurement-Isolation Execution:
    Completed - Pending Human Approval

Steady-State Evidence Review:
    Next Gate

Phase 3:
    Not Authorized
```

## 10. Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | `Approved` | ADR-0010 accepted. Current JFR evidence is insufficient to justify production optimization because setup and steady-state matching costs are not isolated. Measurement-Isolation Execution is authorized; production optimization and Phase 3 remain unauthorized. |
