# Phase 8 — TASK-20260822-032 / Recoverable Live Runtime Handoff

## Executive Status

| Item | Status |
| --- | --- |
| Phase | Phase 8 — Snapshot Checkpoint and Online Recovery Bootstrap |
| Task | `TASK-20260822-032` — Recoverable Live Runtime Handoff |
| Authorization | Human Phase 8 Blueprint Approval; TASK-031 Evidence Gate PASS |
| Limited remediation | Exception Gate approved: continuous `RecoveryLease` ownership |
| Scope | Recovery-to-live handoff, sequence convergence and listener-last startup |
| Implementation | Complete; Evidence Gate PASS |
| Branch | `feature/phase8-snapshot-online-recovery` |
| Implementation checkpoint | `81a80ce` — initial handoff implementation |
| Remediation checkpoint | `22568e6` — lease continuity remediation |
| Exact-SHA CI | [32613235358](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32613235358) PASS |
| Working tree | Tracked tree clean; pre-existing `.vscode/` remains untouched |
| Next gate | TASK-033 crash, corruption and determinism verification |

## Delivered

- Added an opt-in `RecoverableDurableRuntime` with explicit
  `NEW -> RECOVERING -> RECOVERED -> STARTING -> RUNNING` lifecycle and
  first-cause terminal failure handling.
- Added recovered-engine Pipeline construction and validated coordinator
  sequence seeding while preserving legacy constructors and behavior.
- Added listener-last Netty composition: strict recovery, sequence convergence,
  Pipeline/coordinator startup and resource construction complete before bind.
- Verified pure-WAL and Snapshot-tail handoff; the first live command continues
  at `WAL end + 1`, while a new TCP session starts RequestId at `1`.
- Added the approved Exception Gate remediation: `RecoveryPlanner` accepts an
  externally owned held lease without acquiring or closing it. The runtime now
  acquires the lease before strict scan and retains it through live shutdown.

## Verification Evidence

```text
Focused TASK-032/recovery tests: 16 passed, 0 failures
mvn verify: BUILD SUCCESS; 191 tests, 0 failures
Checkstyle: 0 violations
git diff --check: PASS
Approved-path audit: PASS; only TASK-032 paths plus the approved RecoveryPlanner
lease-ownership exception were changed
Frozen-path audit: PASS; Domain/OrderBook/MatchingEngine/WAL/Protocol/Pipeline
semantics remain unchanged
Read-only verifier: PASS
Exact-SHA CI: 22568e6 -> 32613235358 PASS
```

## Exception Gate Remediation

The verifier correctly identified that the initial implementation released the
planner lease before the live runtime reacquired it. This violated ADR-0016 D9
because a scan-to-live ownership gap existed.

The approved remediation is intentionally narrow:

- `recover(mode)` retains its standalone acquire/release behavior;
- `recover(mode, externallyOwnedLease)` validates a held lease and never closes
  it;
- `RecoverableDurableRuntime` acquires before calling the planner and remains
  responsible for release after shutdown or startup failure;
- no WAL/Protocol format, recovery mode, retry, session or thread semantics
  changed.

The external-lease tests prove that the planner does not close caller-owned
leases, that a running recovered runtime prevents a second lease acquisition,
and that startup failure releases the lease for a subsequent owner. The
runtime wiring establishes the acquire-before-scan ordering without adding a
production test seam.

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
- [x] Approved continuous `RecoveryLease` ownership is held from before scan
  through live shutdown.

## Frozen Boundary and Non-Goals

No Domain, OrderBook, WAL v1, Snapshot v1, Protocol v1, matching algorithm,
reconnect/deduplication, multiple-session, retry or recovery-mode semantics
were changed. The only existing-file exception is the additive
`RecoveryPlanner` external-lease overload authorized by the TASK-032 Exception
Gate. TASK-032 does not perform TASK-033 crash/corruption matrix work or
TASK-034 benchmark/closure work.

## Gate Status

`TASK-032 Evidence Gate PASS at 22568e6 / CI 32613235358. At this historical
checkpoint TASK-033 was the next authorized task and TASK-034 remained
conditionally gated. The current Phase 8 state is TASK-029..034 Evidence Gate
PASS with technical Closure input c59d7c0 / CI 32616802595; Human Phase 8
Closure Approval is recorded and merge/tag execution is authorized. Phase 9 and
Product Release remain unauthorized.`
