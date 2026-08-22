# Phase 7 Blueprint — Live Durable Command Pipeline Integration

## 1. Executive Status

| Field | Value |
| --- | --- |
| Phase | `Phase 7 — Live Durable Command Pipeline Integration` |
| Blueprint Status | `Approved` |
| Owner | Human Developer |
| Architect | Architect / Sol High |
| Created | `2026-08-22` |
| Updated | `2026-08-22` |
| Baseline | `v0.5.0-engineering-baseline` at `b7cf68e` |
| Planned Tasks | `TASK-20260822-024` through `TASK-20260822-028` |
| Next Gate | `TASK-025 Evidence Gate` |

## 2. Phase Goal

Compose the frozen Gateway, Command WAL, Event Pipeline and MatchingEngine into
one opt-in live command path in which successful `SYNC_EACH_APPEND` durability
precedes pipeline publication and execution. Preserve existing Protocol v1,
WAL v1, offline replay, SPSC ownership and deterministic Engine semantics.

## 3. Non-Goals and Frozen Boundaries

This Phase does not include Snapshot, online Recovery, restart from a non-empty
WAL, reconnect, duplicate suppression, multiple sessions, request pipelining,
Protocol/WAL format changes, `BUFFERED` live durability, batching, replication,
TLS, optimization, deployment or Product Release.

The following existing production paths are frozen and must remain unchanged:

```text
src/main/java/com/ultralatency/matching/domain/**
src/main/java/com/ultralatency/matching/orderbook/**
src/main/java/com/ultralatency/matching/engine/**
src/main/java/com/ultralatency/matching/persistence/wal/**
src/main/java/com/ultralatency/matching/recovery/**
```

Phase 7 may add new integration and durable-network packages only. `.vscode/`
remains unrelated and untouched.

## 4. Current State and Dependencies

- `v0.3.0-engineering-baseline`: bounded single-producer Event Pipeline;
- `v0.4.0-engineering-baseline`: versioned Command WAL and strict offline replay;
- `v0.5.0-engineering-baseline`: Protocol v1 and single-session Netty Gateway;
- existing live components are intentionally not persistence-integrated;
- Phase 5 `SYNC_EACH_APPEND` is the only permitted live durability mode.

## 5. ADR Set and Decision Matrix

| Decision ID | ADR | Proposed Decision | Approval Result |
| --- | --- | --- | --- |
| D1-D12 | [`ADR-0015`](../../docs/adr/ADR-0015-live-durable-command-pipeline-integration.md) | Additive durable composition; WAL-before-execute; strict identity, durability and failure boundaries | Approved |

## 6. Target Architecture

```text
Protocol v1 request
    -> durable single-session Gateway
    -> request validation / client identity
    -> DurableCommandCoordinator
    -> CommandWalWriter.append + force(true)
    -> MatchingEnginePipeline.tryPublish
    -> frozen Pipeline consumer
    -> frozen MatchingEngine
    -> EngineResult
    -> owning Netty EventLoop
    -> Protocol v1 response frames

Closed WAL -> frozen strict offline replay -> genesis Engine verification
```

The durable coordinator owns the candidate logical Command Sequence. The
Gateway owns client request correlation. Ring sequence, EventSequence, TradeId
and WAL file positions remain separate domains.

## 7. Task Decomposition

| Order | Task | Goal | Depends On | Report |
| ---: | --- | --- | --- | --- |
| 1 | `TASK-20260822-024` | Durable integration contracts and configuration | Baseline | `tasks/reports/PHASE-7-task-024.md` |
| 2 | `TASK-20260822-025` | WAL-before-pipeline coordinator and lifecycle | TASK-024 | `tasks/reports/PHASE-7-task-025.md` |
| 3 | `TASK-20260822-026` | Additive durable Netty composition | TASK-025 | `tasks/reports/PHASE-7-task-026.md` |
| 4 | `TASK-20260822-027` | Failure, disconnect and replay verification | TASK-026 | `tasks/reports/PHASE-7-task-027.md` |
| 5 | `TASK-20260822-028` | Benchmark, documentation and closure evidence | TASK-027 | `tasks/reports/PHASE-7-task-028.md` |

All Tasks are `Manual Gate: No` after Blueprint approval. Each still requires
its focused evidence gate, exact-SHA CI and Exception Gate check before the
next explicitly listed Task begins.

## 8. Stage Authorization Matrix

| Task | Authorized files after approval | Deliverable | Evidence |
| --- | --- | --- | --- |
| 024 | New `integration/durable/**` contracts/tests and task docs | immutable boundaries/configuration | unit tests, diff audit, CI |
| 025 | New durable coordinator production/tests | append-force-publish lifecycle | ordering/failure tests, `mvn verify`, CI |
| 026 | New `network/netty/durable/**` production/tests | opt-in single-session durable server | loopback integration, legacy regression, CI |
| 027 | Tests/fixtures/reports only | failure/disconnect/replay proof | focused matrix, replay digest, CI |
| 028 | Benchmark/docs/reports/context | component evidence and closure proposal | benchmark review, docs audit, CI |

No Task may modify the frozen paths listed in Section 3.

## 9. Acceptance Criteria and Invariants

### Functional / Correctness

- [ ] Every applied live command has a preceding successful WAL append and
  `force(true)` completion.
- [ ] Append, force or rotation failure never reaches pipeline publication.
- [ ] Durable append followed by pipeline `FULL` is terminal and is never
  reported as retryable `BACKPRESSURE_FULL`.
- [ ] The service rejects a non-empty WAL at startup.

### Ordering / Identity

- [ ] Request ID, Command Sequence, WAL order, pipeline publication order,
  engine application order and ordered result correlation are distinct and
  deterministic.
- [ ] One active session, one in-flight request, one WAL writer and one
  pipeline producer/consumer topology remain enforced.

### Failure / Recovery Boundary

- [ ] First terminal cause is retained and later admission is rejected.
- [ ] Disconnect before append, after durable append, after publish and before
  response completion have explicit tested outcomes.
- [ ] Closed live WAL replays to the ordered live transcript, digest and future
  public probe; online restart/recovery is not claimed.

### Compatibility / Boundary

- [ ] Protocol v1 and WAL v1 bytes/semantics remain unchanged.
- [ ] Existing Phase 2-6 tests pass and frozen production diff is zero.
- [ ] No new production-only test seam or critical dependency is added.

### Completion Evidence

- [ ] TASK-024 through TASK-028 completed in order.
- [ ] Focused tests, full `mvn verify`, Checkstyle, diff audit and exact-SHA CI
  pass.
- [ ] Benchmark and claim boundary are reviewed.
- [ ] Phase Closure Proposal is prepared; Human Closure approval remains
  separate.

## 10. Verification Strategy

| Layer | Required Evidence | Pass Condition |
| --- | --- | --- |
| Unit/order | coordinator ports/fakes prove append-success before publish | no publish on append failure |
| Integration | real WAL + pipeline + durable Gateway | ordered Submit/Cancel and result correlation |
| Failure | append/force/rotation, durable-then-FULL, pipeline, handler and write failure | terminal state, first cause, no later admission |
| Disconnect | deterministic barriers at append/publish/write boundaries | explicit ambiguity and ownership semantics |
| Replay | strict scan/replay and future public probe | transcript/EventSequence/TradeId/digest equal |
| Regression | `mvn verify`, Checkstyle, frozen-path audit | all pass, zero frozen diff |
| CI | exact commit status | required workflow PASS |

## 11. Benchmark and Evidence Plan

Measure separately: WAL append/force, append-plus-publish, request-to-local
result write and sequential loopback round trip. Record CPU, OS/filesystem,
storage, JDK/JVM/GC, Netty allocator, WAL/segment settings, pipeline capacity,
wait mode, workload mix, warmup/measurement/forks and P50/P95/P99/P999.

Reports may claim only a single-session, one-in-flight, `SYNC_EACH_APPEND`
engineering component/loopback baseline. They must not claim client receipt,
exactly-once, power-loss durability, online recovery, production throughput or
production readiness. Benchmark results cannot change the durable default.

## 12. Planned Repository Changes

| Directory | Task | Boundary |
| --- | --- | --- |
| `src/main/java/com/ultralatency/matching/integration/durable/**` | 024-025 | new composition layer only |
| `src/main/java/com/ultralatency/matching/network/netty/durable/**` | 026 | new opt-in server/session only |
| `src/test/java/com/ultralatency/matching/integration/durable/**` | 024-027 | verification only |
| `src/test/java/com/ultralatency/matching/network/netty/durable/**` | 026-027 | loopback/failure tests |
| `benchmark/**`, `docs/**`, `tasks/**`, `.codex/AGENT_CONTEXT.md` | 028 | evidence synchronization |

## 13. Exception Gates

Execution must stop for Human review on any frozen-file/API or Protocol/WAL
format change, publication before force, retryable FULL after durable append,
second producer, hidden retry/queue/executor, new dependency, restart/reconnect
semantics, production-only test seam, weakened criterion, optimization-driven
semantic/default change or unlisted scope.

## 14. Git, Commit and CI Strategy

- Branch: `feature/phase7-live-durable-command-pipeline`.
- Use logical commits for contracts, coordinator, durable Gateway, verification
  and benchmark/docs.
- After every Task: focused tests, `mvn verify`, Checkstyle, `git diff --check`,
  frozen-path audit, normal push and exact-SHA CI evidence.
- No force push, squash, history rewrite or destructive cleanup.
- Merge, baseline tag and Task archival require Phase Closure approval.

## 15. Rollback and Compatibility Plan

Disable the opt-in durable launcher or revert Phase 7 commits in reverse order;
keep generated WAL directories for strict offline inspection and never
truncate/reuse them automatically. Legacy in-memory Gateway remains available
without durable claims. No partial production rollout is valid before TASK-027
passes.

## 16. Documentation and Evidence Plan

Synchronize ADR-0015, this Blueprint, all Task plans/reports, architecture
overview, benchmark evidence, README and `.codex/AGENT_CONTEXT.md`. Keep known
limitations explicit, especially force/controller and client-receipt claims.

## 17. Closure and Baseline Plan

- Closure report: `tasks/reports/PHASE-7-live-durable-command-pipeline-closure.md`.
- Human Closure Approval is required before merge.
- Candidate tag after verified master merge: `v0.6.0-engineering-baseline`.
- The tag is not a Product Release.
- Snapshot, online Recovery and later phases remain unauthorized.

## 18. Human Phase Blueprint Approval

| Date | Reviewer | Decision | Approved ADRs / Tasks | Constraints |
| --- | --- | --- | --- | --- |
| 2026-08-22 | Human Developer | Approved | ADR-0015 D1-D12; TASK-024..028 | Frozen baselines; Phase 7 only; no live recovery/release |

```text
Blueprint Status: Approved
Implementation: Authorized in dependency order
Next Gate: TASK-025 Evidence Gate
```
