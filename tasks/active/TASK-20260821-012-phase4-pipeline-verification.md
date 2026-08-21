# Task Plan — TASK-20260821-012

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260821-012` |
| Title | Verify Event Pipeline Determinism and Failure Boundaries |
| Status | `Approved` |
| Owner / Implementer | Human Developer / Codex |
| Created / Updated | `2026-08-21` |
| Related Phase | Phase 4 — Event Pipeline |
| Related ADR | [`ADR-0012`](../../docs/adr/ADR-0012-event-pipeline-execution-and-backpressure.md) — Approved |
| Phase Blueprint | [`PHASE-4 Blueprint`](../blueprints/PHASE-4-event-pipeline-blueprint.md) — Approved |
| Authorization Mode | `Blueprint inherited Human approval` |
| Current Stage | `Implementation (dependency-gated)` |
| Next Gate | `TASK-011 evidence gate` |
| Branch | planned `feature/phase4-event-pipeline` |
| Baseline HEAD | TASK-011 verified commit |
| Remote / CI | `origin` / `Pending` |

## 2. Background

Phase 4 is valuable only if asynchronous scheduling preserves Phase 3's
observable deterministic behavior and failures never silently continue. This
Task adds evidence without changing the approved production design.

## 3. Goal

Prove direct synchronous and pipelined engines produce the same ordered
results for fixed command streams, and prove admission, lifecycle,
backpressure and terminal failure behavior through public contracts.

## 4. Non-Goals

- no new production API, test hook or reflection;
- no implementation refactor unless a correctness defect is found within the
  already approved TASK-011 scope;
- no WAL Replay, crash recovery, Network, Benchmark or optimization.

## 5. Requirements and Acceptance Criteria

### Requirements

- [ ] compare independent direct and pipeline engine instances structurally;
- [ ] execute at least 1,024 deterministic commands with multiple matches;
- [ ] preserve order-significant `EngineResult`, Trade and Execution lists;
- [ ] deterministically force ring saturation and verify retry;
- [ ] verify producer ownership, lifecycle and fail-stop through public API;
- [ ] use latches/barriers and bounded timeouts, not arbitrary sleep;
- [ ] repeat concurrency-sensitive cases enough to reveal obvious flakiness.

### Acceptance Criteria

- [ ] direct and pipeline results are equal in command order;
- [ ] TradeId, EventSequence and all Trade/Execution fields are equal;
- [ ] `FULL` leaves the command retryable and produces no duplicate result;
- [ ] invalid sequence and handler failure stop later publication;
- [ ] graceful drain processes every accepted command exactly once;
- [ ] no existing production file changes unless an Exception Gate is raised;
- [ ] focused suite, repeated suite, full `mvn verify` and exact-SHA CI pass.

## 6. Current Implementation and Scope

TASK-011 is expected to deliver the approved running pipeline and basic tests.

### In Scope

- new/expanded tests under `src/test/java/.../pipeline/`;
- deterministic command fixtures in test source only;
- cumulative Phase 4 evidence report/context checkpoint;
- minimal in-scope TASK-011 defect fix only when it does not alter ADR/API.

### Out of Scope

New production capabilities, reflection, test-only production seams,
benchmarks and external systems.

## 7. Design Proposal

Create two genesis engines. Feed the same immutable command stream directly to
one and through the pipeline to the other. Collect pipeline results in an
ordered in-memory handler, drain, then compare structured values and public
observable follow-up behavior.

Use a gated handler/consumer to fill a deliberately small ring and observe
`FULL` deterministically. Release the gate, retry the same command and verify
one result only.

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
| public structured equality | behavior-focused | requires fixture care | Selected |
| reflection/private counters | easy probing | implementation coupling | Prohibited |
| timing sleeps | simple | flaky/non-deterministic | Prohibited |

### ADR and Blueprint Linkage

| Field | Value |
| --- | --- |
| ADR | ADR-0012 invariants/verification plan — `Approved` |
| Decision Summary | verify observable ordering, equivalence, backpressure and fail-stop |
| Scope Boundary | tests/evidence only; production design frozen |
| Blueprint | `tasks/blueprints/PHASE-4-event-pipeline-blueprint.md` — `Approved` |
| Authorized Stage | TASK-012 only after TASK-011 evidence |
| Exception Gates | production/API change, flaky timing dependency, weakened assertion |

No new architecture decision is permitted in this Task.

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `src/test/java/.../pipeline/MatchingEnginePipelineDeterminismTest.java` | direct/pipeline equality | state evolution evidence |
| `src/test/java/.../pipeline/MatchingEnginePipelineFailureTest.java` | saturation/lifecycle/fail-stop | failure evidence |
| `src/test/java/.../pipeline/*Fixture.java` | package-private deterministic fixture if needed | reduce duplication |
| `tasks/reports/PHASE-4-event-pipeline.md` | cumulative evidence | auditable checkpoint |

## 9. Test Plan

### Determinism

- 1,024+ fixed commands across no-match, single-match, multi-match and cancel;
- two independent runs plus direct-versus-pipeline ordered equality;
- price-time priority, maker price and maker/taker result order preserved;
- result list reordering must fail equality.

### Backpressure / Lifecycle

- gated consumer saturates a small capacity;
- `FULL`, release, retry and exact-once result;
- start once, reject before start/after stop, drain all accepted commands;
- single producer accepted and a different publisher rejected.

### Failure

- invalid command sequence accepted into ring leads to terminal failure;
- result handler throws after one application and pipeline becomes terminal;
- first failure cause remains observable;
- later commands are rejected and no claim of rollback/recovery is made;
- drain timeout reports failure explicitly.

### Repetition

- run focused concurrency-sensitive tests repeatedly with Maven invocation or
  JUnit repetition while keeping deterministic fixture inputs.

## 10. Benchmark and Profile Plan

- Benchmark / Profile: `Not applicable`; TASK-013 owns performance evidence.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| flaky scheduling test | unreliable CI | latches/barriers and bounded diagnostics |
| over-constrained internals | refactor resistance | public structured behavior only |
| false exactly-once claim | architecture overstatement | scope to in-process accepted consumption |
| defect requires design change | hidden drift | trigger Exception Gate |

## 12. Rollback Plan

Revert verification-only files. If tests expose a product defect, fix it under
the existing ADR/Task scope or stop at Exception Gate; never delete/weaken the
test to retain implementation.

## 13. Verification Commands

```text
mvn -pl core -am -Dtest=MatchingEnginePipelineDeterminismTest,MatchingEnginePipelineFailureTest test
repeat focused pipeline tests
mvn verify
git diff --check
production scope diff audit
```

## 14. Git Plan

Commit: `test(pipeline): verify determinism and failure boundaries`.

One verification/evidence commit; push and require exact-SHA CI success before
TASK-013.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Phase Blueprint Approval | `Approved` | TASK-012 authorized after TASK-011 evidence; verification remains public-contract only |

## 16. Phase Reports and Approval Gates

| Stage | Report | Status | Next Gate | Authorization |
| --- | --- | --- | --- | --- |
| ADR / Decision | ADR-0012 / Blueprint | Approved | TASK-011 evidence | Blueprint inherited |
| Verification | `tasks/reports/PHASE-4-event-pipeline.md` | Pending | evidence gate | Blueprint |
| Regression | same report | Pending | TASK-013 | Blueprint |
| Benchmark | same report | Not applicable | TASK-013 | Blueprint |
| Completion | same report | Pending | TASK-013 | Blueprint |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Proposed | Determinism/failure evidence scope prepared | Awaiting Blueprint Approval |
| 2026-08-21 | Approved | Blueprint approval recorded; TASK-012 is dependency-gated | TASK-011 evidence gate |

## 18. Completion Checklist

- [ ] Blueprint approval inherited and prior Task evidence confirmed
- [ ] deterministic/failure tests complete
- [ ] focused repeated/full tests and Checkstyle pass
- [ ] no reflection, test hook or weakened assertion
- [ ] production scope remains frozen
- [ ] report/context synchronized
- [ ] commit pushed and exact-SHA CI recorded
- [ ] no Exception Gate unresolved
