# Phase 10 — TASK-042 Evidence Report

## Executive Status

| Field | Value |
| --- | --- |
| Phase | Phase 10 — Release-Candidate Runtime Assembly |
| Task | `TASK-20260824-042` |
| Result | `Completed / Evidence Gate PASS` |
| Baseline | `v0.8.0-engineering-baseline` / `ef73f60` |
| Branch | `feature/phase10-release-candidate-runtime` |
| Scope | Real application bootstrap, owned Protocol composition root and additive admission/drain lifecycle surface |
| Technical checkpoint | `1eba2c5` |
| Standard CI | `32720292382` PASS |
| Qualification Quick Lane | `32720292393` PASS |
| Next Gate | TASK-043 Evidence Gate |
| Product Release | Not Authorized |

## 1. Implementation Boundary

TASK-042 replaces the application stub with a thin bootstrap API and adds one
composition root. `ReleaseCandidateRuntime` directly owns only the Protocol
server at this stage; the Protocol server remains the sole owner of the
recovered runtime, recovery lease, WAL, Pipeline and durable coordinator.
The future ManagementServer remains TASK-044 scope.

The Protocol server received only the approved additive lifecycle surface:
the compatibility constructor accepting a shared readiness predicate and
first-failure observer, plus `stopAdmission()` and bounded
`awaitInFlight(Duration)`. The legacy constructor retains its previous
always-admission-open behavior after a successful bind.

No Protocol v1, WAL v1, Snapshot v1, recovery, matching, sequence, durability,
producer or dependency semantics were changed.

## 2. Acceptance Evidence

| Criterion | Evidence |
| --- | --- |
| Recovery before listener | `ReleaseCandidateRuntimeTest` starts the existing public recovery path and observes the Protocol server only after recovery reaches `RUNNING` and a listener is bound |
| Readiness boundary | Shared `RuntimeAvailability::isReady` keeps Protocol admission closed during `STARTING`; readiness cannot be published while required management is enabled but unbound |
| Failure rollback | Snapshot recovery failure remains `FAILED`, not ready and listener-unbound; owned Protocol resources are closed on startup failure |
| Direct-child ownership | Root closes only the directly owned Protocol server; transitive recovered resources remain owned by that server/runtime |
| Shutdown/drain | `stopAdmission()` closes new admission, `awaitInFlight(Duration)` provides a bounded drain observation, and repeated root shutdown is idempotent |
| Lifecycle robustness | STARTING-to-stop and close-before-start transitions are covered without optimistic readiness |
| Compatibility | Existing constructor behavior and existing recovery/network tests remain green |

## 3. Changed Files

```text
src/main/java/com/ultralatency/matching/MatchingEngineApplication.java
src/main/java/com/ultralatency/matching/app/ReleaseCandidateRuntime.java
src/main/java/com/ultralatency/matching/app/RuntimeStatusSnapshot.java
src/main/java/com/ultralatency/matching/network/netty/recovery/RecoverableDurableMatchingEngineTcpServer.java
src/main/java/com/ultralatency/matching/operations/RuntimeAvailability.java
src/test/java/com/ultralatency/matching/app/ReleaseCandidateRuntimeTest.java
src/test/java/com/ultralatency/matching/app/RuntimeStatusSnapshotTest.java
src/test/java/com/ultralatency/matching/operations/RuntimeAvailabilityTest.java
```

The `.vscode/` directory remains local, untracked and untouched; it is not a
Phase 10 artifact.

## 4. Verification

```text
Focused TASK-042 suite:
  14 tests, 0 failures

Full reactor mvn verify:
  core: 210 tests, 0 failures
  qualification: 46 tests, 0 failures, 2 intentionally skipped
  benchmark: no tests

Checkstyle:
  0 violations in all Maven modules

git diff --check:
  PASS

Frozen core audit relative to v0.8.0:
  Domain / OrderBook / MatchingEngine / WAL / Snapshot / Pipeline /
  Protocol model production diff = 0
```

Read-only verifier and documentation/evidence review passed. The feature
branch push and exact-SHA CI passed at `1eba2c5`: Standard CI
`32720292382` and Qualification Quick Lane `32720292393`.

## 5. Scope Notes and Deferred Work

`MatchingEngineApplication.main` remains a minimal name-printing entrypoint;
strict properties parsing, CLI actions and executable packaging are TASK-043.
The ManagementServer and its required listener are TASK-044. Therefore the
root only publishes READY for the explicit management-disabled composition in
TASK-042; when management is enabled, `publishReady()` rejects until the
management child is bound by the later task. Shutdown hardening and terminal
failure convergence remain TASK-045.

No Full Campaign, merge, candidate tag or Product Release action is
authorized by this Task.

## 6. Governance State

```text
TASK-041: Completed / Evidence Gate PASS
TASK-042: Completed / Evidence Gate PASS
TASK-043: Authorized / Next
TASK-044~046: Dependency Locked
Phase 10 Closure: Not Authorized
Merge / v0.9.0-rc.1: Not Authorized
Product Release: Not Authorized
```

## 7. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-24 | Implemented | Added the thin application bootstrap, owned Protocol composition root, readiness/failure observer wiring, bounded admission drain operations and lifecycle rollback tests. | Focused 14/14 PASS; full reactor 210 core + 46 qualification tests, 2 skipped, 0 failures; Checkstyle 0 |
| 2026-08-24 | Evidence Gate PASS | Technical checkpoint `1eba2c5` pushed; Standard CI `32720292382` and Quick Lane `32720292393` PASS. | Frozen-path audit and diff/reviewer checks PASS |

## 8. Completion Checklist

- [x] Human Blueprint Approval inherited
- [x] TASK-041 dependency Evidence Gate PASS
- [x] planned production/test files only
- [x] recovery-before-readiness and listener-last evidence PASS
- [x] legacy constructor compatibility preserved
- [x] no frozen semantic/dependency change
- [x] focused/full/static/diff gates PASS
- [x] verifier/docs-auditor and exact-SHA CI PASS
- [x] TASK-043 synchronized as Authorized / Next
