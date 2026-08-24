# Task Plan — TASK-20260824-045

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260824-045` |
| Title | Shutdown and terminal-failure convergence hardening |
| Status | `Authorized / Next` |
| Implementer | Main Codex / Luna Max — only writer after approval |
| Related ADR | [`ADR-0018`](../../docs/adr/ADR-0018-release-candidate-runtime-boundary.md) |
| Blueprint | [`Phase 10 Blueprint`](../blueprints/PHASE-10-release-candidate-runtime-assembly-blueprint.md) |
| Dependency | TASK-044 Evidence Gate PASS |

## 2. Goal

Prove that graceful shutdown, shutdown timeout and terminal runtime failures
converge consistently across readiness, admission, Gateway, coordinator,
Pipeline, WAL and recovery lease ownership.

## 3. Acceptance Criteria

- [ ] Readiness becomes false before admission closes.
- [ ] New requests are rejected after admission closes.
- [ ] The one allowed in-flight request is awaited only within the configured
  bound.
- [ ] Clean completion closes resources once in reverse ownership order.
- [ ] Timeout maps to exit code 6 and preserves durable/client ambiguity.
- [ ] Sync/async Gateway, Pipeline, WAL and management failures retain first
  cause and converge on terminal runtime state.
- [ ] Signal and programmatic shutdown are idempotent.
- [ ] No rollback, retry, deduplication or exactly-once behavior is added.

## 4. Failure Matrix

Cover shutdown from startup states, ready/idle, ready/in-flight, response-write
pending, terminal component failure and repeated close. Use controlled futures
and approved composition boundaries; no sleeps as correctness oracle,
reflection or production-only hooks.

## 5. Evidence Gate

Focused deterministic lifecycle/failure tests, child-process exit-code tests,
recovery-after-approved-termination convergence, full regression, Checkstyle,
diff audit, verifier/docs review and exact-SHA CI must pass before TASK-046.

## 6. Exception Gate / Rollback

Stop if clean shutdown requires changing durability/recovery/protocol semantics,
adding delivery guarantees or modifying an unlisted frozen component. Revert the
task commit; existing abrupt-close behavior remains the baseline limitation.

## 7. Approval

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-24 | Human Developer | Approved / Inherited | TASK-044 Evidence Gate PASS; TASK-045 is now the next authorized task |

## 8. Current Implementation and Detailed Design

The current Protocol-server shutdown closes channels/runtime/groups directly
and does not expose an application-level readiness gate or bounded in-flight
drain. The existing runtime ownership remains correct.

TASK-045 adds only the Blueprint-approved server operations:
`stopAdmission()` atomically prevents new session/request admission and closes
the listening channel; `awaitInFlight(Duration)` waits on a deterministic
lifecycle condition and returns completion/timeout without closing transitive
resources. `ReleaseCandidateRuntime` sets shared availability STOPPING, closes
ManagementServer, calls both operations, then invokes existing Protocol-server
shutdown with the remaining cooperative deadline. A compatible
`RecoverableDurableRuntime.shutdown(Duration)` passes that remainder to the
Pipeline drain; existing `shutdown()` stays compatible. The main application
thread, awakened by shutdown hook or first-failure signal, owns blocking
teardown. Native file close/force is explicitly outside a hard preemption claim.
The Protocol server's private `closeRuntime` path forwards its computed
remaining `Duration` to the new overload; the composition root never obtains or
closes the transitive runtime directly.

Polling sleeps, rollback of durable work and direct ownership of recovered
runtime internals were rejected. ADR-0018 D3-D5/D9 are normative.

## 9. Planned Files

| Path | Change |
| --- | --- |
| `src/main/java/com/ultralatency/matching/app/ReleaseCandidateRuntime.java` | bounded shutdown/terminal convergence |
| `src/main/java/com/ultralatency/matching/network/netty/recovery/RecoverableDurableMatchingEngineTcpServer.java` | stop-admission/drain condition and private remaining-duration forwarding only |
| `src/main/java/com/ultralatency/matching/integration/recovery/RecoverableDurableRuntime.java` | compatible cooperative `shutdown(Duration)` overload only |
| `src/main/java/com/ultralatency/matching/MatchingEngineApplication.java` | shutdown hook/exit outcome wiring |
| corresponding tests/report/docs | deterministic failure matrix |

## 10. Verification Commands / Benchmark

```text
mvn -pl core -am -Dtest='*ReleaseCandidateRuntime*,*RecoverableDurableMatchingEngineTcpServer*' test
mvn verify
git diff --check
git diff --name-only v0.8.0-engineering-baseline...HEAD
```

Benchmark/profile is not a pass criterion here; shutdown timing distributions
are captured in TASK-046.

## 11. Stages / Git / Risks

admission gate -> deterministic drain -> terminal convergence -> child-process
exit matrix -> reviewers -> exact-SHA CI. Planned commit:
`fix(runtime): converge shutdown and terminal lifecycle`.

The primary risk is deadlocking teardown from a Netty/Pipeline callback. The
callback only publishes failure/counts down a latch; teardown runs on the main
thread. Timeout/first-cause/idempotency tests mitigate ambiguous close ordering.
Rollback reverts the additive operations and root orchestration.

## 12. Completion Checklist / Log

- [x] TASK-044 PASS / approval inherited
- [ ] clean, timeout, failure and repeat-close matrix PASS
- [ ] Protocol server forwards the observed remaining deadline to the runtime
  overload; legacy shutdown remains compatible
- [ ] no durable rollback or delivery-claim expansion
- [ ] compatibility/full/static/reviewer/CI gates PASS
- [ ] TASK-046 synchronized

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-24 | Proposed | Shutdown model frozen | docs only |
