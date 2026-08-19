# Phase 2 Report - Profiling ADR / Decision Proposal

## 1. Report Metadata

| Field | Value |
| --- | --- |
| Phase | `Phase 2 - Basic OrderBook` |
| Stage | `Profiling ADR / Decision` |
| Task | `TASK-20260819-004-basic-orderbook` |
| ADR | [`ADR-0009-performance-profiling-evidence.md`](../../docs/adr/ADR-0009-performance-profiling-evidence.md) |
| Report Date | `2026-08-19` |
| Stage Status | `Proposed - Pending Human Approval` |
| Next Approval Gate | `Human Approval - Profiling ADR / Task Plan` |

## 2. Authorization

The Human Developer approved Phase 2 Verification, the OrderBook Benchmark
Baseline and Documentation Synchronization on `2026-08-19`. The next stage was
authorized as a Profiling ADR / Decision stage only.

This report proposes the profiling boundary and does not authorize running a
profiler, changing production code, changing benchmark semantics or beginning
optimization.

## 3. Proposed Objective

Use controlled JVM profiling to identify CPU hot paths, allocation sites and
garbage-collection observations in the approved Phase 2 OrderBook baseline.
The primary workload is `multiLevelMatch`; one-level matching and cancellation
workloads provide supporting context.

## 4. Proposed Decision

ADR-0009 proposes:

- required first profile: JFR on the existing Java 21 baseline;
- optional supplementary profile: async-profiler only when available and
  versioned;
- fixed JMH workload and parameters from the approved benchmark baseline;
- raw recordings stored locally under an ignored profile-results directory;
- a report containing commands, environment, workload, tool versions,
  hotspot observations and limitations;
- no optimization or production behavior change in this stage.

## 5. Proposed Scope

### In Scope After ADR Approval

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

The decision stage is read-only with respect to production behavior. Verify:

```text
git diff --check
git status --short --branch
review ADR-0009 and the synchronized task plan
```

After Human approval of ADR-0009, a separate profiling stage must record:

- exact command and timestamp;
- OS, CPU, cores, memory, JDK/JVM and profiler versions;
- JMH forks, threads, warmup, measurement and workload shape;
- raw recording paths;
- CPU hot paths;
- allocation and GC observations when collected;
- profiling overhead and limitations.

## 8. Approval Request

请求 Human Developer 审查 ADR-0009 和本 Profiling ADR / Decision 方案。
批准后才可以执行 JFR profiling；本阶段不授权 async-profiler 的强制使用、
任何优化或 Phase 3。

```text
Profiling ADR / Decision:
    Proposed - Pending Human Approval

Profiling Execution:
    Not Authorized

Optimization:
    Not Authorized

Phase 3:
    Not Authorized
```

## 9. Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | `Authorized` | Profiling may enter the ADR / Decision stage. Profiling execution, optimization and Phase 3 remain unauthorized. |
|  |  | `Pending` | ADR-0009 and the profiling task scope await Human Approval. |
