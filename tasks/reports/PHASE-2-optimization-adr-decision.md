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
| Stage Status | `Proposed - Pending Human Approval` |
| Next Approval Gate | `Human Approval - Optimization ADR / Decision` |

## 2. Authorization and Objective

Human Developer approved Profiling Execution on `2026-08-19`. This stage is
limited to reviewing the accepted JFR evidence and preparing an Optimization
ADR / Decision proposal.

The objective is to determine whether the current profile supports a
production optimization target. No production code, test code, benchmark
semantics, JVM arguments or GC settings are changed in this stage.

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

The proposed decision is:

```text
Production optimization:
    Deferred

Next evidence step:
    Measurement isolation plan and execution, separately approved

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

After Human approval of ADR-0010, the next stage may prepare a
measurement-isolation task plan. That plan must preserve the approved B0
workload and correctness semantics, and it must receive its own stage approval
before execution.

This report does not authorize:

- production code changes;
- benchmark redesign or workload replacement;
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

## 9. Approval Request

Human Developer is requested to review ADR-0010 and this decision-stage report.

```text
Optimization ADR / Decision:
    Proposed - Pending Human Approval

Production Optimization:
    Not Authorized

Measurement-Isolation Execution:
    Not Authorized

Phase 3:
    Not Authorized
```

## 10. Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | `Pending` | Evidence review completed. The proposed decision defers production optimization until setup and profiler overhead are isolated under a separately approved measurement plan. |
