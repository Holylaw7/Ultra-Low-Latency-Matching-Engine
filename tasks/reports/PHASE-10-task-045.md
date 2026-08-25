# Phase 10 — TASK-045 Evidence Report

## Executive Status

| Field | Value |
| --- | --- |
| Phase | Phase 10 — Release-Candidate Runtime Assembly |
| Task | `TASK-20260824-045` |
| Result | `Completed / Evidence Gate PASS` |
| Baseline | `v0.8.0-engineering-baseline` / `ef73f60` |
| Branch | `feature/phase10-release-candidate-runtime` |
| Scope | Bounded shutdown, terminal-failure convergence and process wait/exit wiring |
| Technical checkpoint | `f024aef` |
| Standard CI | `32728038236` PASS |
| Qualification Quick Lane | `32728038263` PASS |
| Next Gate | TASK-046 pre-campaign Evidence Gate |
| Product Release | Not Authorized |

## 1. Implementation Boundary

TASK-045 hardens the already-approved Phase 10 composition without changing
Protocol v1, WAL v1, Snapshot v1, matching, recovery or durability semantics.
The root closes readiness before admission, stops the Protocol listener, waits
for the single allowed in-flight request under one cooperative deadline, then
closes direct and transitive children in reverse ownership order. The root
retains the first sanitized failure code and signals the process waiter on
terminal failure or completed shutdown.

The Protocol server now exposes the additive `stopAdmission()` and
`awaitInFlight(Duration)` operations. Its private runtime close path forwards
the observed remaining duration to the compatible
`RecoverableDurableRuntime.shutdown(Duration)` overload. Existing no-argument
shutdown methods remain compatible. No durable command is rolled back or
retried to manufacture a clean outcome.

## 2. Acceptance Evidence

| Criterion | Evidence |
| --- | --- |
| Readiness closes before admission | `ReleaseCandidateRuntime.shutdown()` transitions shared availability to STOPPING before stopping Protocol admission |
| Bounded in-flight drain | Root and Protocol server use a single deadline and deterministic condition wait; no polling sleep is used |
| Reverse ownership close | Management direct child closes before Protocol; Protocol closes listener/session, recovered runtime and event-loop groups; runtime closes coordinator, Pipeline, WAL writer and lease |
| Timeout/non-clean outcome | Remaining-duration propagation, `SHUTDOWN_TIMEOUT` mapping and exit code 6 are wired through `RuntimeExitCode` |
| First terminal cause and waiter signal | Protocol/Management failure observers retain the first runtime failure and count down the root termination signal; repeated shutdown is idempotent |
| Compatibility and scope | Existing shutdown signatures remain; only Blueprint-approved runtime/server/application/test paths changed |
| No delivery-claim expansion | No rollback, retry, deduplication, reconnect, exactly-once or durable-client acknowledgement semantics were added |

## 3. Changed Files

```text
src/main/java/com/ultralatency/matching/app/ReleaseCandidateRuntime.java
src/main/java/com/ultralatency/matching/app/RuntimeCommandLine.java
src/main/java/com/ultralatency/matching/integration/recovery/RecoverableDurableRuntime.java
src/main/java/com/ultralatency/matching/network/netty/recovery/RecoverableDurableMatchingEngineTcpServer.java
src/test/java/com/ultralatency/matching/app/ReleaseCandidateRuntimeTest.java
```

`.vscode/settings.json` remains local, untracked and untouched. It is not a
Phase 10 artifact and is intentionally excluded from the commit.

## 4. Verification

```text
Focused TASK-045 suite:
  14 tests, 0 failures

Full reactor mvn verify:
  core: 225 tests, 0 failures
  qualification: 46 tests, 0 failures, 2 intentionally skipped
  benchmark: no tests

Checkstyle:
  0 violations in all Maven modules

git diff --check:
  PASS

Approved-path audit:
  PASS; Domain, OrderBook, MatchingEngine, Pipeline, Protocol v1, WAL,
  Snapshot and recovery formats remain unchanged outside the approved runtime
  shutdown adapters.

Exact-SHA CI:
  Standard CI `32728038236` PASS
  Qualification Quick Lane `32728038263` PASS
```

The focused lifecycle tests cover programmatic shutdown signaling and first
terminal runtime failure signaling while preserving the existing listener-last
and recovery tests. The local full verification completed after the final
implementation checkpoint with the same 225 core and 46 qualification test
counts.

## 5. Scope Notes and Deferred Work

The cooperative bound does not claim hard preemption of native file-system
`force` or close operations. A response that was durably accepted before a
shutdown or terminal failure retains the existing ambiguous client outcome;
this task does not add rollback or delivery guarantees.

TASK-046 remains responsible for assembled-runtime qualification, lifecycle
campaign evidence and the Phase 10 Closure Proposal. This task does not claim
Production Ready status, SLA/RTO, exactly-once delivery, hardware power-loss
safety, bounded WAL disk usage or Product Release authorization.

## 6. Governance State

```text
TASK-041: Completed / Evidence Gate PASS
TASK-042: Completed / Evidence Gate PASS
TASK-043: Completed / Evidence Gate PASS
TASK-044: Completed / Evidence Gate PASS
TASK-045: Completed / Evidence Gate PASS
TASK-046: Authorized / Next
Phase 10 Closure: Not Authorized
Merge / v0.9.0-rc.1: Not Authorized
Product Release: Not Authorized
```

## 7. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-24 | Implemented | Added single-deadline admission stop, in-flight drain, terminal waiter signaling, compatible runtime shutdown overload and exit mapping. | Focused 14/14 PASS; full reactor PASS |
| 2026-08-24 | Evidence Gate PASS | Technical checkpoint `f024aef` pushed; Standard CI `32728038236` and Quick Lane `32728038263` PASS. | Checkstyle, diff, approved-path audit and exact-SHA CI PASS |

## 8. Completion Checklist

- [x] Human Blueprint Approval inherited
- [x] TASK-044 dependency Evidence Gate PASS
- [x] Readiness closes before admission and bounded drain is deterministic
- [x] Reverse ownership shutdown and compatible deadline forwarding implemented
- [x] First terminal cause, waiter signal and repeated-shutdown idempotence covered
- [x] No durable rollback or delivery-claim expansion
- [x] Focused/full/static/diff gates PASS
- [x] Exact-SHA Standard/Quick CI PASS
- [x] TASK-046 synchronized as Authorized / Next

**Blueprint Authorized — continue with TASK-046 pre-campaign qualification.**
