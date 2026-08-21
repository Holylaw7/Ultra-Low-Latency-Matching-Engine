# Task Plan — TASK-20260821-011

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260821-011` |
| Title | Implement Bounded Single-Consumer Matching Pipeline |
| Status | `Completed` |
| Owner / Implementer | Human Developer / Codex |
| Created / Updated | `2026-08-21` |
| Related Phase | Phase 4 — Event Pipeline |
| Related ADR | [`ADR-0012`](../../docs/adr/ADR-0012-event-pipeline-execution-and-backpressure.md) — Approved |
| Phase Blueprint | [`PHASE-4 Blueprint`](../blueprints/PHASE-4-event-pipeline-blueprint.md) — Approved |
| Authorization Mode | `Blueprint inherited Human approval` |
| Current Stage | `Implementation (dependency-gated)` |
| Next Gate | `TASK-012 Implementation` |
| Branch | planned `feature/phase4-event-pipeline` |
| Baseline HEAD | TASK-010 evidence commit `565f649` |
| Remote / CI | `origin/feature/phase4-event-pipeline`; exact-SHA CI [32457723272](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32457723272) PASS |

## 2. Background

TASK-010 will establish dependency and public contracts. This Task implements
the actual Disruptor adapter while treating the Phase 3 engine as a frozen
external dependency.

## 3. Goal

Implement a bounded single-producer/single-consumer pipeline with explicit
admission, lifecycle, result handoff, slot clearing and terminal failure
semantics.

## 4. Non-Goals

- no multi-producer support or sequence arbitration;
- no Network, WAL, Replay, Snapshot, output ring or I/O;
- no existing Engine, Domain or OrderBook production modification;
- no benchmark-driven optimization or production performance claim.

## 5. Requirements and Acceptance Criteria

### Requirements

- [ ] use `ProducerType.SINGLE` and one pipeline-owned consumer thread;
- [ ] own one `MatchingEngine` exclusively while running;
- [ ] expose non-blocking `tryPublish` with `ACCEPTED`/`FULL`;
- [ ] preserve command publication order and command Sequence values;
- [ ] invoke the result handler synchronously after successful application;
- [ ] clear each consumed event slot in `finally`;
- [ ] implement NEW/RUNNING/DRAINING/STOPPED/FAILED lifecycle;
- [ ] record first terminal cause and reject later publication;
- [ ] perform bounded graceful drain with explicit timeout result.

### Acceptance Criteria

- [ ] accepted commands are consumed at most once and in order;
- [ ] `FULL` neither mutates nor loses the submitted command;
- [ ] foreign-producer publication is rejected before slot claim;
- [ ] handler/engine/consumer failure is fail-stop and observable;
- [ ] no hidden retry, drop, overwrite, sort or sequence rewrite exists;
- [ ] Disruptor event slots do not retain consumed command references;
- [ ] focused integration tests and full `mvn verify` pass;
- [ ] frozen production paths have zero diff and exact-SHA CI passes.

## 6. Current Implementation and Scope

Current Phase 3 exposes only synchronous `MatchingEngine.process`. TASK-010 is
expected to provide project-owned configuration and state contracts.

### In Scope

- new implementation files under `src/main/java/.../pipeline/`;
- pipeline integration tests under `src/test/java/.../pipeline/`;
- cumulative Phase 4 report/context checkpoint.

### Out of Scope

POM changes beyond TASK-010, existing production packages, benchmarks and all
future infrastructure.

## 7. Design Proposal

### Proposed Design

`MatchingEnginePipeline` constructs the Disruptor with preallocated mutable
command slots, maps the approved wait mode internally, owns its consumer
thread and invokes one owned engine. Its facade validates lifecycle and
producer ownership before using `tryPublishEvent`.

The consumer processes one command, hands off its immutable result, records
terminal errors, and always clears the slot. A result-handler exception is
terminal because engine state has already changed.

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
| synchronous handler on matching consumer | minimal ordered boundary | handler may stall/fail | Selected with fail-stop contract |
| output ring | isolates egress | second backpressure domain | Deferred |
| caller polling results | no callback | requires second storage/ownership model | Deferred |

### ADR and Blueprint Linkage

| Field | Value |
| --- | --- |
| ADR | ADR-0012 D2-D7 — `Approved` |
| Decision Summary | bounded SPSC Disruptor adapter with explicit lifecycle and fail-stop |
| Scope Boundary | new pipeline package; frozen core is dependency only |
| Blueprint | `tasks/blueprints/PHASE-4-event-pipeline-blueprint.md` — `Approved` |
| Authorized Stage | TASK-011 only after TASK-010 evidence |
| Exception Gates | need for output ring, multi-producer, core API change or recovery |

Architecture impact: ADR and Human architecture decision required.

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `src/main/java/.../pipeline/MatchingEnginePipeline.java` | facade/lifecycle/admission | public pipeline boundary |
| `src/main/java/.../pipeline/CommandEvent.java` | package-private reusable slot | preallocated handoff |
| `src/main/java/.../pipeline/MatchingEventHandler.java` | engine consumer | ordered application |
| `src/main/java/.../pipeline/PipelineExceptionHandler.java` | terminal failure bridge | observable fail-stop |
| `src/test/java/.../pipeline/MatchingEnginePipelineTest.java` | integration/lifecycle tests | correctness evidence |

Names of package-private helpers may change mechanically; their
responsibilities may not expand.

## 9. Test Plan

### Unit / Integration

- start once and publish only while RUNNING;
- one no-match, one match and cancellation result handoff;
- ordered multiple commands and graceful drain;
- wait-mode mapping and capacity wrap;
- consumed slot clearing.

### Failure and Boundary

- null command, foreign producer, full ring and invalid lifecycle;
- invalid command sequence and result-handler exception terminal failure;
- bounded drain timeout and post-failure publication rejection;
- deterministic latches/barriers; no arbitrary sleep as correctness oracle.

### Determinism or Replay

Basic ordered equality is covered here; extended equivalence belongs to
TASK-012. No WAL Replay subsystem is authorized.

## 10. Benchmark and Profile Plan

- Benchmark / Profile: `Not applicable` in this Task.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| callback stalls consumer | throughput/liveness loss | non-blocking contract and later output Phase |
| failure after engine mutation | uncertain continuation | terminal state; never continue |
| shutdown races | loss/false completion | explicit DRAINING state and bounded tests |
| stale slot references | memory retention | clear in `finally` and wrap test |

## 12. Rollback Plan

Revert TASK-011 implementation/tests while retaining or separately reverting
the unused TASK-010 foundation. Phase 3 remains runnable and unchanged.

## 13. Verification Commands

```text
mvn -pl core -am -Dtest=MatchingEnginePipelineTest test
mvn verify
git diff --check
frozen production path diff audit
```

## 14. Git Plan

Commit: `feat(pipeline): implement bounded matching pipeline`.

One implementation/test commit; push and require exact-SHA CI success before
TASK-012.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Phase Blueprint Approval | `Approved` | TASK-011 authorized after TASK-010 evidence; frozen engine and OrderBook remain unchanged |

## 16. Phase Reports and Approval Gates

| Stage | Report | Status | Next Gate | Authorization |
| --- | --- | --- | --- | --- |
| ADR / Decision | ADR-0012 / Blueprint | Approved | TASK-010 evidence | Blueprint inherited |
| Core Implementation | `tasks/reports/PHASE-4-event-pipeline.md` | Pending | integration evidence | Blueprint |
| Verification | same report | Pending | TASK-012 | Blueprint |
| Benchmark | same report | Not applicable | TASK-012 | Blueprint |
| Completion | same report | Pending | TASK-012 | Blueprint |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Proposed | Bounded pipeline implementation scope prepared | Awaiting Blueprint Approval |
| 2026-08-21 | Approved | Blueprint approval recorded; TASK-011 is dependency-gated | TASK-010 evidence gate |
| 2026-08-21 | In Progress | Bounded Disruptor adapter, lifecycle and failure-boundary implementation started | TASK-010 evidence CI PASS; focused TASK-011 verification pending |
| 2026-08-21 | Completed | Bounded SPSC adapter, lifecycle, result handoff, backpressure and terminal failure behavior implemented | commit `a3986df`; 77 tests; exact-SHA CI `32457723272` PASS |

## 18. Completion Checklist

- [x] Blueprint approval inherited and TASK-010 evidence confirmed
- [x] pipeline implementation and tests complete
- [x] focused/full tests and Checkstyle pass
- [x] deterministic backpressure/lifecycle evidence recorded
- [x] frozen production boundaries unchanged
- [x] report/context synchronized
- [x] commit pushed and exact-SHA CI recorded
- [x] no Exception Gate unresolved
