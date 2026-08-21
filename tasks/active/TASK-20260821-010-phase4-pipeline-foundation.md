# Task Plan — TASK-20260821-010

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260821-010` |
| Title | Add Phase 4 Event Pipeline Foundation |
| Status | `Completed` |
| Owner | Human Developer |
| Implementer | Codex |
| Created / Updated | `2026-08-21` |
| Related Phase | Phase 4 — Event Pipeline |
| Related ADR | [`ADR-0012`](../../docs/adr/ADR-0012-event-pipeline-execution-and-backpressure.md) — Approved |
| Phase Blueprint | [`PHASE-4 Blueprint`](../blueprints/PHASE-4-event-pipeline-blueprint.md) — Approved |
| Authorization Mode | `Blueprint inherited Human approval` |
| Current Stage | `Implementation` |
| Next Gate | `Automated Evidence Gate` |
| Branch | `feature/phase4-event-pipeline` |
| Baseline HEAD | approved blueprint commit `4c92278` |
| Remote / CI | `origin/feature/phase4-event-pipeline`; exact-SHA CI [32456991709](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32456991709) PASS |

## 2. Background

The Phase 3 engine is synchronous and dependency-free. Phase 4 needs a new
pipeline package and an explicit dependency/configuration boundary before
concurrent lifecycle code is introduced.

## 3. Goal

Add the approved Disruptor dependency and project-owned immutable pipeline API
foundation with validation tests, without starting a consumer or invoking the
MatchingEngine.

## 4. Non-Goals

- no running pipeline, consumer thread or engine invocation;
- no existing Engine, Domain or OrderBook production modification;
- no Network, WAL, Replay, output ring or Benchmark.

## 5. Requirements and Acceptance Criteria

### Requirements

- [ ] pin `com.lmax:disruptor:4.0.0` in repeatable Maven configuration;
- [ ] record purpose, Apache 2.0 license and Java compatibility;
- [ ] add project-owned configuration, wait mode, publish outcome, state and
  result-handler contracts;
- [ ] keep LMAX types out of existing public packages;
- [ ] validate nulls, capacity bounds and power-of-two capacity.

### Acceptance Criteria

- [ ] new contracts are immutable or stateless as appropriate;
- [ ] `BLOCKING` is the default and only approved wait modes are represented;
- [ ] invalid configuration fails before resource/thread construction;
- [ ] focused tests and full `mvn verify` pass;
- [ ] existing Engine/Domain/OrderBook production diff is zero;
- [ ] exact-SHA CI passes.

## 6. Current Implementation and Scope

The core module has no pipeline package or concurrency dependency.

### In Scope

- `pom.xml`, `core/pom.xml`;
- new `src/main/java/com/ultralatency/matching/pipeline/` contract files;
- focused tests under `src/test/java/.../pipeline/`;
- cumulative Phase 4 report checkpoint.

### Out of Scope

Existing production files under `domain`, `engine` and `orderbook`, runtime
pipeline implementation and benchmarks.

## 7. Design Proposal

Add a parent property for Disruptor 4.0.0, a core dependency and project-owned
contracts defined by ADR-0012. Configuration exposes capacity and wait mode
without leaking an LMAX `WaitStrategy`.

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
| project-owned contract around Disruptor | dependency isolation | adapter code | Selected |
| expose LMAX types publicly | less wrapper code | public coupling | Rejected |
| custom ring | no dependency | concurrency proof burden | Rejected |

### ADR and Blueprint Linkage

| Field | Value |
| --- | --- |
| ADR | ADR-0012 D1, D4, D7 — `Approved` |
| Decision Summary | Disruptor 4.0.0 behind validated project-owned contracts |
| Scope Boundary | dependency/API foundation only; no runtime consumer |
| Blueprint | `tasks/blueprints/PHASE-4-event-pipeline-blueprint.md` — `Approved` |
| Authorized Stage | TASK-010 only after Blueprint approval |
| Exception Gates | dependency/version change, API expansion, existing production-file change |

Architecture impact: ADR and Human architecture decision required.

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `pom.xml`, `core/pom.xml` | version and dependency | repeatable adapter dependency |
| `src/main/java/.../pipeline/PipelineConfiguration.java` | validated configuration | capacity/wait contract |
| `src/main/java/.../pipeline/PipelineWaitMode.java` | approved modes | no LMAX public leak |
| `src/main/java/.../pipeline/PipelinePublishOutcome.java` | `ACCEPTED`/`FULL` | explicit backpressure |
| `src/main/java/.../pipeline/PipelineState.java` | lifecycle states | observable state |
| `src/main/java/.../pipeline/EngineResultHandler.java` | result contract | pipeline/core separation |
| `src/test/java/.../pipeline/*Test.java` | contract tests | boundary evidence |

No additional public concept is authorized.

## 9. Test Plan

- valid capacities and approved wait modes;
- non-power-of-two, too-small and null configuration rejection;
- validation before thread/resource creation;
- no integration/replay test in this Task; TASK-012 owns those gates.

## 10. Benchmark and Profile Plan

- Benchmark / Profile: `Not applicable`
- Baseline: `v0.2.0-engineering-baseline`

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| dependency leaks through API | future coupling | project-owned types only |
| configuration over-design | unnecessary surface | freeze exact concepts in Blueprint |
| incompatible dependency | build failure | focused compile plus full Maven CI |

## 12. Rollback Plan

Revert the dependency, new contract files and tests. No runtime or data state
exists; Phase 3 remains independently buildable.

## 13. Verification Commands

```text
mvn -pl core -am test
mvn verify
git diff --check
git diff --name-only v0.2.0-engineering-baseline -- frozen production paths
```

## 14. Git Plan

Commit: `feat(pipeline): add event pipeline foundation`.

One dependency/API/test commit; push to the Phase branch and require exact-SHA
CI success before TASK-011.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Phase Blueprint Approval | `Approved` | TASK-010 authorized; dependency/API foundation only; automated evidence gate required |

## 16. Phase Reports and Approval Gates

| Stage | Report | Status | Next Gate | Authorization |
| --- | --- | --- | --- | --- |
| ADR / Decision | ADR-0012 / Blueprint | Approved | Implementation | Blueprint inherited |
| Foundation | `tasks/reports/PHASE-4-event-pipeline.md` | Pending | evidence gate | Blueprint |
| Verification | same report | Pending | TASK-011 | Blueprint |
| Benchmark | same report | Not applicable | TASK-011 | Blueprint |
| Completion | same report | Pending | TASK-011 | Blueprint |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Proposed | Foundation scope prepared | Awaiting Blueprint Approval |
| 2026-08-21 | Approved | Blueprint approval recorded; TASK-010 may begin | Automated evidence gate |
| 2026-08-21 | In Progress | Disruptor dependency and project-owned pipeline contracts implemented; runtime pipeline intentionally absent | Focused core tests PASS; full verify pending |
| 2026-08-21 | Completed | Foundation implementation and verification accepted; runtime pipeline remains deferred to TASK-011 | commit `c9a797e`; exact-SHA CI `32456991709` PASS |

## 18. Completion Checklist

- [x] Blueprint approval inherited and recorded
- [x] dependency/API scope implemented
- [x] focused and full tests plus Checkstyle pass
- [x] frozen production boundaries unchanged
- [x] report/context synchronized
- [x] logical commit pushed and exact-SHA CI recorded
- [x] no Exception Gate unresolved
