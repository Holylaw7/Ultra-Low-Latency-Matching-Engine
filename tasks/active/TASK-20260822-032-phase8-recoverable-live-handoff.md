# Task Plan — TASK-20260822-032

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-032` / Recoverable Live Runtime Handoff |
| Phase / ADR / Blueprint | Phase 8 / ADR-0016 / `PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md` |
| Status | Proposed — Not Authorized |
| Depends on | TASK-031 Evidence Gate PASS after Blueprint approval |
| Manual Gate | No after Blueprint approval; Exception Gate remains active |
| Planned report | `tasks/reports/PHASE-8-task-032.md` |

## Goal

Compose an opt-in recovering durable runtime that finishes strict recovery,
converges all next-sequence owners, starts the Pipeline/coordinator and binds
the Netty listener last.

## Authorized Scope After Approval

New production/tests under:

```text
src/main/java/com/ultralatency/matching/integration/recovery/**
src/main/java/com/ultralatency/matching/network/netty/recovery/**
src/test/java/com/ultralatency/matching/integration/recovery/**
src/test/java/com/ultralatency/matching/network/netty/recovery/**
```

Narrow additive construction changes are authorized only in
`MatchingEnginePipeline.java` and `DurableCommandCoordinator.java`.

## Non-Goals

No new Protocol frame, replay response, reconnect/dedup, more sessions,
request pipelining, new producer/thread/queue, hot Snapshot or recovery-time
optimization.

## Acceptance Criteria

- [ ] Lifecycle is `NEW -> RECOVERING -> RECOVERED -> STARTING -> RUNNING`,
  with first-cause terminal `FAILED` from any pre-running stage.
- [ ] No listener is bound and no request admitted before recovery and handoff
  complete.
- [ ] Engine, WAL writer and coordinator agree on `WAL end + 1`.
- [ ] Pipeline consumer exclusively owns the recovered engine.
- [ ] First live Submit and Cancel continue Command Sequence exactly.
- [ ] New TCP session RequestId starts at 1 while engine result counters
  continue from recovered state.
- [ ] Bind/start/resource failure releases resources, retains first cause and
  rejects later admission.
- [ ] Legacy Phase 6/7 construction and behavior remain compatible.

## Evidence Gate

Focused listener-last/handoff loopback tests, full `mvn verify`, Checkstyle 0,
`git diff --check`, approved/frozen-path audit, logical commit, normal push and
exact-SHA CI PASS.

## Exception Gate

Stop for pre-recovery bind/admission, unvalidated sequence seeding, new
producer/session/queue/thread semantics, Protocol/WAL changes, reconnect/retry,
production-only seam or any unlisted existing-file change.
