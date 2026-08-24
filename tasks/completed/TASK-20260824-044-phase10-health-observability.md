# Task Plan — TASK-20260824-044

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260824-044` |
| Title | Bounded health, readiness and operational status boundary |
| Status | `Completed / Evidence Gate PASS` |
| Implementer | Main Codex / Luna Max — only writer after approval |
| Related ADR | [`ADR-0018`](../../docs/adr/ADR-0018-release-candidate-runtime-boundary.md) |
| Blueprint | [`Phase 10 Blueprint`](../blueprints/PHASE-10-release-candidate-runtime-assembly-blueprint.md) |
| Dependency | TASK-043 Evidence Gate PASS |
| Evidence Report | [`PHASE-10-task-044.md`](../reports/PHASE-10-task-044.md) |

## 2. Goal

Expose loopback-default liveness, readiness, immutable runtime status and
bounded monotonic counters without accessing mutable engine state or affecting
the matching ownership path.

## 3. Scope and Design

Use a separate bounded management adapter built from existing Netty. It reads
only immutable `RuntimeStatusSnapshot` values published by the composition
root. No new dependency, unbounded buffering, engine traversal, second producer
or hidden executor is permitted.

## 4. Acceptance Criteria

- [ ] Liveness indicates process/runtime-loop availability only.
- [ ] Readiness is false during recovery/startup and before/through shutdown.
- [ ] Status schema is versioned, deterministic and bounded.
- [ ] Counters are monotonic boundary observations and never trading authority.
- [ ] Slow/invalid management clients cannot block or allocate unbounded state.
- [ ] Management bind failure follows configured required/disabled semantics and
  cannot leave optimistic readiness.
- [ ] Concurrency, bounds, integration, regression and CI evidence pass.

## 5. Evidence Gate

Test lifecycle transitions, concurrent polling, malformed/oversized input,
client disconnect, management bind failure, bounded resource behavior and lack
of matching-state access. Run verifier and benchmark-reviewer if overhead
measurements are added, plus docs-auditor and exact-SHA CI.

## 6. Exception Gate / Rollback

Stop for mutable engine observation, a new dependency, public/untrusted default,
auth/TLS scope, hidden execution or changed readiness semantics. Rollback may
disable/remove the management adapter without changing Protocol v1.

## 7. Approval

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-24 | Human Developer | Approved / Inherited | TASK-043 Evidence Gate PASS; TASK-044 authorized in dependency order |

## 8. Current Implementation and Selected Protocol

The bounded operational endpoint is implemented as an independent loopback
management adapter. Existing runtime state accessors remain limited to immutable
status snapshots for direct composition/tests and are not a mutable
matching-state API.

The selected management protocol is the Blueprint §8.1 bounded loopback TCP
protocol: one ASCII command (`LIVE`, `READY`, `STATUS`, `METRICS`), one
canonical JSON-line response, then close. One owned Netty event-loop thread,
32-byte request, 2048-byte response, backlog 16, default 16/max 64 connections
and 1000 ms timeout are frozen. JDK `HttpServer`, a new HTTP dependency and
logging/metrics frameworks were rejected because they add unresolved executor,
dependency or schema boundaries.

ADR-0018 D7-D8 and the Blueprint status schema are normative.

## 9. Planned Files

| Path | Change |
| --- | --- |
| `src/main/java/com/ultralatency/matching/operations/ManagementServer.java` | bounded listener/owner |
| `src/main/java/com/ultralatency/matching/operations/ManagementProtocol.java` | strict decode/canonical encode |
| `src/main/java/com/ultralatency/matching/operations/RuntimeStatusSnapshot.java` | schema-v1 immutable view |
| `src/main/java/com/ultralatency/matching/app/ReleaseCandidateRuntime.java` | start/close direct management child |
| corresponding tests/report/docs | protocol/lifecycle/evidence |

## 10. Tests / Benchmark / Verification

Unit/golden tests cover exact bytes/field order/bounds. Integration tests cover
startup/ready/stopping/failed status, 16/17 connection pressure, timeout,
oversize/multiple/unknown request, disconnect and bind failure. A bounded
focused overhead smoke records behavior but no performance claim; Full overhead
characterization belongs to TASK-046.

```text
mvn -pl core -am -Dtest='*Management*,*ReleaseCandidateRuntime*' test
mvn verify
git diff --check
git diff --name-only v0.8.0-engineering-baseline...HEAD
```

## 11. Stages / Git / Risks

protocol codec -> bounded listener -> runtime wiring -> failure/bounds tests ->
read-only reviewer -> exact-SHA CI. Planned commit:
`feat(operations): add bounded runtime status boundary`.

Risk of operational-path latency contamination is controlled by immutable
snapshots, no engine traversal and later paired characterization. Risk of idle
connection exhaustion is controlled by cap/timeout/one-request-close.
Rollback removes the independent adapter; Protocol v1 remains untouched.

## 12. Completion Checklist / Log

- [x] dependency gate and inherited approval
- [x] exact management wire/schema tests PASS
- [x] bounded concurrency/timeout evidence PASS
- [x] no new dependency, engine access or hidden executor
- [x] full/static/read-only implementation audit/exact-SHA CI PASS
- [x] TASK-045 synchronized

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-24 | Proposed | Management contract frozen | docs only |
| 2026-08-24 | Completed / Evidence Gate PASS | Added bounded loopback management adapter, immutable status wiring and lifecycle integration. | Focused 16/16 PASS; `mvn verify` PASS; Standard CI `32726203105`; Quick Lane `32726203076` |

## 13. Final Governance State

```text
TASK-044: Completed / Evidence Gate PASS
TASK-045: Authorized / Next
TASK-046: Dependency Locked
Phase 10 Closure: Not Authorized
Merge / v0.9.0-rc.1: Not Authorized
Product Release: Not Authorized
```
