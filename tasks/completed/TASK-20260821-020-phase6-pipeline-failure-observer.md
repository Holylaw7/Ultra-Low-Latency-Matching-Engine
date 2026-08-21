# Task Plan — TASK-20260821-020

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260821-020` / Phase 6 Pipeline Terminal Failure Observer |
| Status | `Completed / Evidence PASS` |
| Owner / Implementer | Human Developer / Codex |
| Created / Updated | `2026-08-21` |
| Phase / ADR / Blueprint | Phase 6 / ADR-0014 / [`PHASE-6`](../blueprints/PHASE-6-network-protocol-blueprint.md) |
| Authorization Mode | Blueprint |
| Current Stage / Next Gate | Archived / Phase 6 baseline frozen; Phase 7 Blueprint only |
| Branch / Baseline | `master` after `--no-ff` merge `b7cf68e` / `v0.5.0-engineering-baseline` |
| Remote / CI | `origin/master` / master CI [32495076976](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32495076976) PASS; tag CI [32495218654](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32495218654) PASS |

## 2. Background

The pipeline exposes terminal state for polling but has no event-driven signal
for a network owner to close channels when the engine/handler fails.

## 3. Goal

Add one project-owned, non-blocking, at-most-once terminal failure observer and
an additive pipeline constructor while preserving every existing contract.

## 4. Non-Goals

- no matching, ring, ordering, lifecycle-state or backpressure redesign;
- no Engine/OrderBook/WAL/Recovery changes;
- no network code or production-only test seam.

## 5. Requirements and Acceptance Criteria

- [ ] `PipelineFailureHandler` receives the first terminal cause at most once;
- [ ] current constructor delegates to a no-op observer and remains compatible;
- [ ] additive constructor accepts explicit result and failure handlers;
- [ ] callback performs no hidden thread switch and is documented non-blocking;
- [ ] callback failure cannot replace the first pipeline cause or restart work;
- [ ] start, engine, result-handler and drain-timeout failure paths notify once;
- [ ] all existing Phase 4 tests pass unchanged.

## 6. Current Implementation and Scope

### Current Implementation

`MatchingEnginePipeline` exposes `state()` and `failureCause()` for polling and
has one constructor accepting configuration and result handler. It has no
terminal event observer.

### In Scope

New `PipelineFailureHandler.java`, minimal `MatchingEnginePipeline.java` wiring
and focused tests.

### Out of Scope

All other existing pipeline and frozen files.

## 7. Design Proposal

Store an immutable observer supplied at construction. On the first successful
transition to `FAILED`, publish the preserved first cause once. The network
implementation will schedule channel closure rather than perform I/O inside
the callback.

| Option | Advantages | Risks | Result |
| --- | --- | --- | --- |
| additive observer | immediate/explicit | public surface grows | selected |
| polling | no API change | latency/races/scheduler | rejected |
| new duplicate pipeline | isolates API | duplicated concurrency logic | rejected |

### ADR / Blueprint Linkage

| Field | Value |
| --- | --- |
| ADR Status | ADR-0014 Approved |
| Decision | D8 additive failure observer only |
| Blueprint | Phase 6 Approved; TASK-020 after TASK-019 CI |
| Exception Gates | behavior break, new failure state/thread, broader API change |

### Architecture Impact

- [x] ADR required
- [x] Human architecture decision required

## 8. Planned File Changes

| File | Change |
| --- | --- |
| `pipeline/PipelineFailureHandler.java` | new public project-owned callback |
| `pipeline/MatchingEnginePipeline.java` | additive constructor/one-shot notification |
| pipeline tests | compatibility and failure matrix |

## 9. Test Plan

Unit/integration tests cover existing constructor, one notification, first
cause, observer exception, start/engine/handler/timeout paths and later publish
rejection. No reflection or sleeps.

## 10. Benchmark and Profile Plan

Not applicable.

## 11. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| callback deadlock | explicit non-blocking contract; no I/O |
| duplicate notification | guarded first transition |
| Phase 4 regression | unchanged constructor tests/full suite |

## 12. Rollback Plan

Revert the additive interface/constructor/wiring. Existing constructor remains
the compatibility anchor throughout implementation.

## 13. Verification Commands

```text
mvn -pl core -am -Dtest=MatchingEnginePipeline* test
mvn verify
git diff --check
```

## 14. Git Plan

`feat(pipeline): expose terminal failure observer`; push and require exact-SHA
CI before TASK-021.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Proposal | Proposal only | no implementation |
| 2026-08-21 | Human Developer | Blueprint | Approved / Conditional | additive observer only; start after TASK-019 exact-SHA CI |

## 16. Phase Reports and Approval Gates

| Stage | Report | Status | Next Gate | Authorization |
| --- | --- | --- | --- | --- |
| Decision/Approval | Phase 6 proposal | Pending | Blueprint Approval | Pending |
| Implementation/Verification | [`PHASE-6-network-protocol.md`](../reports/PHASE-6-network-protocol.md) | Completed / PASS | TASK-021 | Blueprint |
| Benchmark | Not applicable | N/A | completion | Blueprint |
| Completion | cumulative report | Completed / PASS | TASK-021 | Blueprint |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Proposed | additive observer plan | baseline PASS |
| 2026-08-21 | Completed / Evidence PASS | additive observer implemented with constructor compatibility and one-shot notification | 121 tests PASS; Checkstyle 0; frozen diff 0; exact-SHA CI 32488893108 PASS |

## 18. Completion Checklist

- [x] compatibility and failure evidence pass
- [x] full build/static/diff/frozen audit pass
- [x] docs/report/CI synchronized
- [x] no Exception Gate
