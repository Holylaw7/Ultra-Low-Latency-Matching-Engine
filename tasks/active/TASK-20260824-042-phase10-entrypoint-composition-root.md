# Task Plan — TASK-20260824-042

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260824-042` |
| Title | Application entrypoint and owned composition root |
| Status | `Authorized / Next` |
| Implementer | Main Codex / Luna Max — only writer after approval |
| Related ADR | [`ADR-0018`](../../docs/adr/ADR-0018-release-candidate-runtime-boundary.md) |
| Blueprint | [`Phase 10 Blueprint`](../blueprints/PHASE-10-release-candidate-runtime-assembly-blueprint.md) |
| Dependency | TASK-041 Evidence Gate PASS |
| Next Gate | TASK-042 Evidence Gate |

## 2. Goal

Replace the application stub with a thin CLI/bootstrap entrypoint and one
composition root that directly owns the Protocol server and management server
in the approved startup/shutdown order. The Protocol server continues to own
the recovered runtime, which continues to own recovery, WAL, Pipeline and
durable coordinator resources.

## 3. Scope and Boundaries

Authorized after approval: `MatchingEngineApplication`, new `app/**`, and only
the Blueprint-listed additive lifecycle extensions to
`RecoverableDurableMatchingEngineTcpServer`.

Frozen: matching, Protocol/WAL/Snapshot formats, durability, recovery authority,
SPSC ownership, single-session behavior and Product Release.

## 4. Acceptance Criteria

- [ ] Recovery and sequence convergence complete before Protocol bind.
- [ ] Readiness becomes true only after all required resources/listeners exist.
- [ ] Startup failure rolls back acquired resources in reverse order.
- [ ] The composition root closes each direct child exactly once; each child
  remains sole owner of its transitive resources.
- [ ] Empty-WAL and Snapshot-plus-tail startup use existing public recovery.
- [ ] No hidden executor, second producer or unbounded queue is introduced.
- [ ] Existing runtime APIs remain source/binary compatible except for the
  explicitly additive lifecycle surface.
- [ ] Integration/regression/static/reviewer/exact-SHA CI gates pass.

## 5. Test Plan

Test successful startup, empty/recovered state, lease contention, corrupt
storage, recovery failure, sequence-convergence failure, gateway bind failure,
partial-construction rollback and repeated close. Use deterministic boundaries,
not `Thread.sleep`, reflection or production-only seams.

## 6. Evidence Gate

```text
focused composition/lifecycle tests
 -> existing recovery/network regression
 -> mvn verify / Checkstyle / diff audit
 -> verifier + docs-auditor PASS
 -> exact-SHA CI PASS
 -> TASK-043 authorized
```

## 7. Exception Gate / Rollback

Stop if another existing production file, recovery/format semantic, producer,
threading model or public protocol must change. Roll back the task commit and
retain `v0.8.0` as the runnable component baseline.

## 8. Approval

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-24 | Human Developer | Approved / Inherited | Phase 10 Blueprint approval plus TASK-041 Evidence Gate PASS; TASK-042 implementation now authorized |

## 9. Current Implementation and Detailed Design

The existing Protocol server creates/owns `RecoverableDurableRuntime`, its
Protocol event loops/listener/session, and exposes `start()`/`shutdown()`.
`RecoverableDurableRuntime` owns lease, WAL, Pipeline and coordinator. This
ownership remains unchanged.

`ReleaseCandidateRuntime` owns the Protocol server and later ManagementServer.
It injects `RuntimeAvailability::isReady` and a terminal-failure observer through
a compatible additive Protocol-server constructor. Legacy construction delegates
to always-admission-open behavior. The only new server operations are
`stopAdmission()` and `awaitInFlight(Duration)`.

Directly extracting the recovered runtime from the server and making the new
root own all internals was rejected because it duplicates ownership and breaks
Phase 8 lifecycle evidence.

## 10. Planned Files

| Path | Change |
| --- | --- |
| `src/main/java/com/ultralatency/matching/MatchingEngineApplication.java` | thin CLI/bootstrap delegation |
| `src/main/java/com/ultralatency/matching/app/ReleaseCandidateRuntime.java` | direct-child lifecycle owner |
| `src/main/java/com/ultralatency/matching/network/netty/recovery/RecoverableDurableMatchingEngineTcpServer.java` | compatible admission/failure constructor and drain operations |
| corresponding `src/test/java/**` | ownership/startup/rollback tests |
| `tasks/reports/PHASE-10-task-042.md` | report/evidence |

## 11. Verification Commands

```text
mvn -pl core -am -Dtest='*ReleaseCandidateRuntime*,*RecoverableDurableMatchingEngineTcpServer*' test
mvn verify
git diff --check
git diff --name-only v0.8.0-engineering-baseline...HEAD
```

Benchmark/profile: not applicable. Lifecycle evidence is correctness evidence.

## 12. Stages / Git / CI

| Stage | Evidence | Next |
| --- | --- | --- |
| server compatibility | old/new constructor tests | composition |
| composition/startup | recovery/bind/rollback tests | verification |
| verification/docs | regression + reviewer + exact-SHA CI | TASK-043 |

Planned commit: `feat(runtime): assemble owned application runtime`. Push the
Task checkpoint only after all gates pass; no merge/tag.

## 13. Risks / Rollback

| Risk | Mitigation |
| --- | --- |
| admission before readiness | single injected atomic availability predicate |
| duplicate close | direct-child-only ownership tests |
| callback blocks engine/event loop | callback only records first failure and signals main thread |

Rollback reverts the additive constructor/operations and composition class.
No storage or protocol migration occurs.

## 14. Implementation Log / Checklist

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-24 | Proposed | Ownership model frozen in Blueprint | docs only |

- [ ] dependency gate PASS
- [ ] exact files only
- [ ] legacy behavior compatible
- [ ] focused/full/static gates PASS
- [ ] verifier/docs-auditor and exact-SHA CI PASS
- [ ] TASK-043 synchronized
