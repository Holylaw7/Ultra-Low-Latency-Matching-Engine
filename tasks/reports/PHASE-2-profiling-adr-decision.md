# Phase 2 Report - Profiling ADR / Decision

## 1. Report Metadata

| Field | Value |
| --- | --- |
| Phase | `Phase 2 - Basic OrderBook` |
| Stage | `Profiling ADR / Decision` |
| Task | `TASK-20260819-004-basic-orderbook` |
| ADR | [`ADR-0009-performance-profiling-evidence.md`](../../docs/adr/ADR-0009-performance-profiling-evidence.md) |
| Report Date | `2026-08-19` |
| Stage Status | `Completed - Approved` |
| Next Approval Gate | `Profiling Execution` |

## 2. Authorization

The Human Developer approved Phase 2 Verification, the OrderBook Benchmark
Baseline and Documentation Synchronization on `2026-08-19`. The Human Developer
approved ADR-0009 and this Profiling ADR / Decision on `2026-08-19`, authorizing
the profiling execution stage within the recorded scope.

This report records the approved profiling boundary. Profiling execution is
authorized, but production code, tests, benchmark semantics and JVM/GC settings
must remain unchanged. Optimization and Phase 3 remain unauthorized.

## 3. Approved Objective

Use controlled JVM profiling to identify CPU hot paths, allocation sites and
garbage-collection observations in the approved Phase 2 OrderBook baseline.
The primary workload is `multiLevelMatch`; one-level matching and cancellation
workloads provide supporting context.

## 4. Approved Decision

ADR-0009 is approved with the following decision:

- required first profile: JFR on the existing Java 21 baseline;
- optional supplementary profile: async-profiler only when available and
  versioned;
- fixed JMH workload and parameters from the approved benchmark baseline;
- raw recordings stored locally under an ignored profile-results directory;
- a report containing commands, environment, workload, tool versions,
  hotspot observations and limitations;
- no optimization or production behavior change in this stage.

## 5. Proposed Scope

### In Scope During Profiling Execution

- prepare the approved JFR profiling command;
- profile the committed `OrderBookBaselineBenchmark`;
- collect `multiLevelMatch`, `oneLevelMatch`, `cancelByOrderId` and
  `cancelAndCleanEmptyLevel` evidence;
- analyze CPU, allocation and GC observations available from the recording;
- write a profiling report and request Human Approval.

### Out of Scope

- production code or test changes;
- benchmark workload changes intended to improve results;
- custom trees, SkipLists, radix indexes, price arrays or object pools;
- off-heap, lock-free, Disruptor, cache-line or GC tuning;
- MatchingEngine, Trade/Execution, WAL, Network or Phase 3;
- Optimization implementation.

## 6. Planned Artifacts

- `docs/adr/ADR-0009-performance-profiling-evidence.md`
- `docs/performance/profiling.md`
- `tasks/reports/PHASE-2-profiling-adr-decision.md`
- a later profiling report after separate ADR approval;
- local ignored JFR and optional async-profiler recordings.

## 7. Verification Plan

The decision stage was read-only with respect to production behavior. The
following approval-stage checks were completed:

```text
git diff --check
git status --short --branch
reviewed ADR-0009 and the synchronized task plan
```

The authorized profiling stage must record:

- exact command and timestamp;
- OS, CPU, cores, memory, JDK/JVM and profiler versions;
- JMH forks, threads, warmup, measurement and workload shape;
- raw recording paths;
- CPU hot paths;
- allocation and GC observations when collected;
- profiling overhead and limitations.

## 8. Decision and Hand-off

Human Developer 已于 `2026-08-19` 审查并批准 ADR-0009 和本 Profiling
ADR / Decision 方案。现在可以执行 JFR profiling；async-profiler 仍是可选
补充工具，当前环境不可用时不阻塞本阶段。任何优化或 Phase 3 仍未授权。

```text
Profiling ADR / Decision:
    Approved

Profiling Execution:
    Authorized

Optimization:
    Not Authorized

Phase 3:
    Not Authorized
```

## 9. Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | `Authorized` | Profiling may enter the ADR / Decision stage. Profiling execution, optimization and Phase 3 remain unauthorized. |
| 2026-08-19 | Human Developer | `Approved` | ADR-0009 and the profiling task scope approved. JFR-first profiling execution authorized against the fixed benchmark baseline; optimization and Phase 3 remain unauthorized. |
