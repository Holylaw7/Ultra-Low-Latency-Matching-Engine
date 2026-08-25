# Phase 10 — TASK-044 Evidence Report

## Executive Status

| Field | Value |
| --- | --- |
| Phase | Phase 10 — Release-Candidate Runtime Assembly |
| Task | `TASK-20260824-044` |
| Result | `Completed / Evidence Gate PASS` |
| Baseline | `v0.8.0-engineering-baseline` / `ef73f60` |
| Branch | `feature/phase10-release-candidate-runtime` |
| Scope | Bounded loopback liveness, readiness, status and management counters |
| Technical checkpoint | `c3f0883` |
| Standard CI | `32726203105` PASS |
| Qualification Quick Lane | `32726203076` PASS |
| Next Gate | TASK-045 Evidence Gate |
| Product Release | Not Authorized |

## 1. Implementation Boundary

TASK-044 adds an independent operational adapter owned directly by
`ReleaseCandidateRuntime`. It exposes only the approved loopback TCP management
protocol and reads immutable `RuntimeStatusSnapshot` values from the shared
availability owner. It does not traverse mutable matching state, enter the
Pipeline, add a producer, or create a second execution path.

The protocol accepts exactly one bounded ASCII command per connection:
`LIVE`, `READY`, `STATUS` or `METRICS`, each terminated by `\\n`. Requests are
limited to 32 bytes, responses are canonical UTF-8 JSON lines limited to 2048
bytes, and the connection closes after one response. Invalid, oversized,
multiple and timed-out requests receive the bounded invalid-request response
when it is safe to write, then close. A one-thread Netty event loop, backlog 16,
configured connection cap and scheduled request timeout provide bounded
resource behavior.

Readiness remains closed during recovery/startup and shutdown. The Protocol
listener is marked bound first; the Management listener is then bound; only an
explicit `publishReady()` after both required listeners are available opens
admission. Management bind failure maps to `MANAGEMENT_BIND`, preserves
readiness=false and rolls back the owned children. Disabled management creates
no listener or event-loop thread.

## 2. Acceptance Evidence

| Criterion | Evidence |
| --- | --- |
| Liveness/readiness semantics | `RuntimeAvailabilityTest`, `ReleaseCandidateRuntimeTest` and integration lifecycle assertions cover STARTING, protocol-bound/not-ready, READY, FAILED and shutdown states |
| Deterministic wire contract | `ManagementProtocolTest` covers strict ASCII decoding, exact commands, canonical field order, metrics suffix, bounds and non-echoing invalid response |
| Bounded integration behavior | `ManagementServerIntegrationTest` covers LIVE/READY/STATUS/METRICS, invalid/oversized/multiple requests, one-request close and bounded max-connection rejection |
| Failure semantics | Integration test covers management port collision and `MANAGEMENT_BIND` fail-closed startup |
| Immutable status/counters | Management encoding consumes `RuntimeStatusSnapshot`; counters are monotonic bounded observations and never trading authority |
| No new dependency or frozen-core access | Diff audit contains only approved `app/**`, `operations/**` and focused tests; Domain, OrderBook, MatchingEngine, Pipeline, WAL, Snapshot, Recovery and Protocol v1 production paths are unchanged by TASK-044 |

## 3. Changed Files

```text
src/main/java/com/ultralatency/matching/app/ReleaseCandidateRuntime.java
src/main/java/com/ultralatency/matching/app/RuntimeCommandLine.java
src/main/java/com/ultralatency/matching/operations/ManagementProtocol.java
src/main/java/com/ultralatency/matching/operations/ManagementServer.java
src/main/java/com/ultralatency/matching/operations/RuntimeAvailability.java
src/test/java/com/ultralatency/matching/app/ManagementServerIntegrationTest.java
src/test/java/com/ultralatency/matching/app/ReleaseCandidateRuntimeTest.java
src/test/java/com/ultralatency/matching/operations/ManagementProtocolTest.java
src/test/java/com/ultralatency/matching/operations/RuntimeAvailabilityTest.java
```

`.vscode/settings.json` remains local, untracked and untouched; it is not a
Phase 10 artifact and is intentionally excluded from the commit.

## 4. Verification

```text
Focused TASK-044 suite:
  16 tests, 0 failures

Full reactor mvn verify:
  core: 223 tests, 0 failures
  qualification: 46 tests, 0 failures, 2 intentionally skipped
  benchmark: no tests

Checkstyle:
  0 violations in all Maven modules

git diff --check:
  PASS

Approved-path audit:
  PASS; no TASK-044 changes under frozen Domain, OrderBook, MatchingEngine,
  Pipeline, WAL, Snapshot, Recovery or Protocol v1 production paths

Exact-SHA CI:
  Standard CI `32726203105` PASS
  Qualification Quick Lane `32726203076` PASS
```

The implementation uses existing Netty only and introduces no dependency,
authentication/TLS, public bind, management framework or performance claim.

## 5. Scope Notes and Deferred Work

TASK-045 remains responsible for shutdown and terminal-failure convergence,
including bounded drain and first-cause behavior. TASK-046 remains responsible
for assembled-runtime qualification, lifecycle matrix and the separately
Human-gated Full Campaign.

This task does not claim public-network safety, TLS/authentication,
Production Ready status, SLA/RTO, exactly-once delivery, hardware power-loss
safety or a Product Release.

## 6. Governance State

```text
TASK-041: Completed / Evidence Gate PASS
TASK-042: Completed / Evidence Gate PASS
TASK-043: Completed / Evidence Gate PASS
TASK-044: Completed / Evidence Gate PASS
TASK-045: Authorized / Next
TASK-046: Dependency Locked
Phase 10 Closure: Not Authorized
Merge / v0.9.0-rc.1: Not Authorized
Product Release: Not Authorized
```

## 7. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-24 | Implemented | Added strict bounded management protocol, one-thread loopback listener, immutable status wiring and lifecycle integration. | Focused 16/16 PASS; full reactor PASS |
| 2026-08-24 | Evidence Gate PASS | Technical checkpoint `c3f0883` pushed; Standard CI `32726203105` and Quick Lane `32726203076` PASS. | Checkstyle, diff, approved-path audit and exact-SHA CI PASS |

## 8. Completion Checklist

- [x] Human Blueprint Approval inherited
- [x] TASK-043 dependency Evidence Gate PASS
- [x] strict management wire/schema tests PASS
- [x] bounded concurrency, timeout and invalid-input evidence PASS
- [x] readiness and management-bind failure evidence PASS
- [x] no new dependency, engine access or hidden executor
- [x] full/static/focused/diff gates PASS
- [x] exact-SHA Standard/Quick CI PASS
- [x] TASK-045 synchronized as Authorized / Next

**Blueprint Authorized — continue with TASK-045.**
