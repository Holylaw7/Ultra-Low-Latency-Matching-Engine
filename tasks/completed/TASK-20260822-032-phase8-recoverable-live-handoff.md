# Task Plan — TASK-20260822-032

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-032` / Recoverable Live Runtime Handoff |
| Phase / ADR / Blueprint | Phase 8 / ADR-0016 / `PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md` |
| Status | Completed — Evidence Gate PASS |
| Depends on | TASK-031 Evidence Gate PASS |
| Manual Gate | No after Blueprint approval; Exception Gate remediation completed |
| Evidence | `22568e6` / CI `32613235358` PASS |
| Report | [`PHASE-8-task-032.md`](../reports/PHASE-8-task-032.md) |

## Goal

Compose an opt-in recovering durable runtime that finishes strict recovery,
converges all next-sequence owners, starts the Pipeline/coordinator and binds
the Netty listener last.

## Delivered Scope

- Added recovered-runtime lifecycle and listener-independent composition.
- Added recovered-engine Pipeline construction and validated coordinator seed.
- Added listener-last recovered Netty server with new-session RequestId reset.
- Added pure-WAL and Snapshot-tail live handoff tests, startup failure tests,
  lease contention tests and first live command continuation tests.
- Applied the approved Exception Gate remediation for continuous
  `RecoveryLease` ownership from before scan through runtime shutdown.

## Acceptance Checklist

- [x] Lifecycle is `NEW -> RECOVERING -> RECOVERED -> STARTING -> RUNNING`,
  with first-cause terminal `FAILED` from any pre-running stage.
- [x] No listener is bound and no request is admitted before recovery and
  handoff complete.
- [x] Engine, WAL writer and coordinator agree on `WAL end + 1`.
- [x] Pipeline consumer exclusively owns the recovered engine.
- [x] First live Submit and Cancel continue Command Sequence exactly.
- [x] New TCP session RequestId starts at 1 while engine result counters
  continue from recovered state.
- [x] Bind/start/resource failure releases resources, retains first cause and
  rejects later admission.
- [x] Legacy Phase 6/7 construction and behavior remain compatible.
- [x] External recovery lease is validated, not closed by the planner, and is
  retained by the runtime through live shutdown.

## Evidence Gate

```text
Focused TASK-032/recovery tests: 16 passed, 0 failures
mvn verify: 191 tests, 0 failures
Checkstyle: 0 violations
git diff --check: PASS
Read-only verifier: PASS
Frozen/approved-path audit: PASS
Exact-SHA CI: 32613235358 PASS
```

## Exception Gate Record

The initial implementation reacquired `recovery.lock` after the planner had
released it. Human Exception Gate approval authorized the narrow additive
`RecoveryPlanner.recover(mode, externallyOwnedLease)` overload and runtime
wiring change. The standalone `recover(mode)` API remains acquire/release
compatible; no WAL, Snapshot, Protocol, session, retry or thread semantics
changed.

## Non-Goals

No new Protocol frame, replay response, reconnect/dedup, more sessions,
request pipelining, new producer/thread/queue, hot Snapshot or recovery-time
optimization. TASK-033 crash/corruption verification and TASK-034 benchmark/
documentation closure work remain separate tasks.

## Gate Status

`TASK-032` is complete and archived after Evidence Gate PASS at `22568e6` /
CI `32613235358`; at that historical checkpoint `TASK-033` was the next
authorized task. The current Phase 8 state is TASK-029..034 Evidence Gate PASS
with technical Closure input `c59d7c0` / CI `32616802595`; docs/evidence-only
remediation is authorized. Phase 8 Closure, merge, `v0.7.0-engineering-baseline`
and Phase 9 remain unauthorized.
