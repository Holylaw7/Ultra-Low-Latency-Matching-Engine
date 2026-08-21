# AGENT_CONTEXT — Matching Engine Current State

> Last Updated: 2026-08-21
> Purpose: compact current-state index; detailed history lives in Tasks, Stage
> Reports, ADRs and Git.

## Project Dashboard

| Item | Current State |
| --- | --- |
| Project | Ultra-Low-Latency Matching Engine |
| Product scope | Single-node, in-memory, deterministic matching engine |
| Phase | Phase 3 — MatchingEngine (`Completed / Approved / Baseline Frozen`) |
| Latest product task | [`TASK-20260820-008`](../tasks/completed/TASK-20260820-008-phase3-matching-engine-implementation.md) — Completed |
| Latest architecture task | [`TASK-20260820-007`](../tasks/completed/TASK-20260820-007-phase3-matching-engine-adr-decision.md) — Completed |
| Current planning task | [`Phase 4 Event Pipeline Blueprint`](../tasks/blueprints/PHASE-4-event-pipeline-blueprint.md) with TASK-010 through TASK-013 — Proposed |
| Governance mode | Phase Blueprint Mode completed, approved and active for future multi-task Phases |
| Product stage | Phase 3 Closed / Baseline Frozen; Phase 4 Blueprint Proposed |
| Product approval | Phase 4 implementation is locked pending one Human Phase Blueprint Approval |
| Latest infrastructure task | [`TASK-20260820-006`](../tasks/completed/TASK-20260820-006-repository-remote-ci-setup.md) — Completed |
| Branch | `docs/phase4-event-pipeline-blueprint` |
| Engineering baseline commit | `9281124` |
| Engineering baseline tag | `v0.2.0-engineering-baseline` |
| Remote | `origin` — `git@github.com:Holylaw7/Ultra-Low-Latency-Matching-Engine.git` |
| Remote sync | Phase 4 proposal content pushed at `f0f18ff`; branch tracks `origin/docs/phase4-event-pipeline-blueprint`; `v0.2.0-engineering-baseline` remains frozen at `9281124` |
| CI | Phase 4 proposal content [run 32455576290](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32455576290) PASS for exact SHA `f0f18ff`; local baseline `mvn verify` PASS (61 tests, 0 Checkstyle violations) |

## Project Progress

| Phase | Status | Evidence |
| --- | --- | --- |
| Phase 0 — Bootstrap | Completed | Maven reactor, Java 21, JUnit 5, JMH, Checkstyle and CI workflow |
| Phase 1 — Domain Model | Completed / Approved | [`PHASE-1-domain-model.md`](../tasks/reports/PHASE-1-domain-model.md) |
| Phase 2 — Basic OrderBook | Completed / Approved | `v0.1.0-engineering-baseline`, 45 tests, JMH/JFR evidence and passing master CI |
| Phase 3 — Matching Engine | Completed / Approved / Baseline Frozen | [`Final Closure`](../tasks/reports/PHASE-3-matching-engine-closure-authorization.md); `v0.2.0-engineering-baseline` |
| Governance — Phase Blueprint Mode | Completed / Approved / Active | [`TASK-009`](../tasks/completed/TASK-20260821-009-phase-blueprint-governance.md); master CI PASS |
| Phase 4 — Event Pipeline | Complete Blueprint Proposed / Human Approval Pending | [`Blueprint Proposal`](../tasks/reports/PHASE-4-event-pipeline-blueprint-proposal.md); implementation locked |
| Phase 5+ — Network, recovery and performance evolution | Future Work | Future Blueprints, ADRs and Tasks |

## Current Product Gate

Phase 2 is closed and frozen at `v0.1.0-engineering-baseline`; Phase 3 is closed
and frozen at `v0.2.0-engineering-baseline`. ADR-0005 R1-R6 and ADR-0011 D1-D7
are finally approved. The current gate is:

```text
ADR-0011 Final Approved
    -> TASK-20260820-008 Approved
    -> Stage 1 Domain/API Foundation
    -> Human Stage 1 completion approval [Completed]
    -> Stage 2 authorization [Approved]
    -> Stage 2.1-2.3 implementation [Completed]
    -> Human Stage 2 completion approval [Completed]
    -> Stage 3 authorization [Approved]
    -> Stage 3 verification execution [Completed]
    -> Human Stage 3 completion review [Completed]
    -> Phase 3 Closure proposal [Prepared]
    -> Human Phase 3 Closure approval [Approved]
    -> normal merge / master verification [Completed / PASS]
    -> v0.2.0-engineering-baseline / tag CI [Completed / PASS]
    -> TASK-20260820-008 [Completed]
    -> Phase 4 complete Blueprint proposal [Prepared]
    -> Human Phase 4 Blueprint approval [Pending]
    -> Phase 4 implementation [Not Authorized]
```

Stage 1 Domain/API Foundation and Stage 2 MatchingEngine Core are completed and
approved. Stage 3 verification-only execution and Human completion review are
complete with ordered result comparison, public-API state probes and no
production test hooks. Phase 3 is closed at the annotated and CI-verified
`v0.2.0-engineering-baseline`. Release, next-phase ADR/implementation,
production optimization and history rewrite remain unauthorized. OrderBook
remains the frozen Phase 2 dependency.

Phase Blueprint Mode is the active governance standard. ADR-0012, the complete
Phase 4 Event Pipeline Blueprint and TASK-010 through TASK-013 are `Proposed`.
They request one Human Blueprint Approval for a bounded single-producer /
single-consumer event pipeline, deterministic result handling, backpressure,
lifecycle, verification and component evidence. Proposal preparation does not
authorize implementation. Existing Domain, OrderBook and MatchingEngine
production files remain frozen.

Current proposal:
[`PHASE-4-event-pipeline-blueprint.md`](../tasks/blueprints/PHASE-4-event-pipeline-blueprint.md).

Current proposal report:
[`PHASE-4-event-pipeline-blueprint-proposal.md`](../tasks/reports/PHASE-4-event-pipeline-blueprint-proposal.md).

Latest completed plan:
[`TASK-20260820-008-phase3-matching-engine-implementation.md`](../tasks/completed/TASK-20260820-008-phase3-matching-engine-implementation.md).

Latest completed governance task:
[`TASK-20260821-009-phase-blueprint-governance.md`](../tasks/completed/TASK-20260821-009-phase-blueprint-governance.md).

Current evidence:

- [`PHASE-3-matching-engine-implementation-planning.md`](../tasks/reports/PHASE-3-matching-engine-implementation-planning.md)
  — TASK-008 plan approved; Stage 1 completed and approved.
- [`PHASE-3-matching-engine-domain-api-foundation.md`](../tasks/reports/PHASE-3-matching-engine-domain-api-foundation.md)
  — Stage 1 approved evidence; no MatchingEngine or OrderBook integration.
- [`PHASE-3-matching-engine-core-authorization.md`](../tasks/reports/PHASE-3-matching-engine-core-authorization.md)
  — Stage 2 authorization approval and frozen scope.
- [`PHASE-3-matching-engine-core-implementation.md`](../tasks/reports/PHASE-3-matching-engine-core-implementation.md)
  — Stage 2 completed and approved; CI evidence recorded.
- [`PHASE-3-matching-engine-determinism-authorization.md`](../tasks/reports/PHASE-3-matching-engine-determinism-authorization.md)
  — Stage 3 verification-only scope approved; execution authorized.
- [`PHASE-3-matching-engine-determinism-verification.md`](../tasks/reports/PHASE-3-matching-engine-determinism-verification.md)
  — Stage 3 completed and approved evidence: 256 commands and 61 core tests.
- [`PHASE-3-matching-engine-closure-authorization.md`](../tasks/reports/PHASE-3-matching-engine-closure-authorization.md)
  — final closure, frozen boundary, limitations, master CI and tag CI evidence.
- [`PHASE-3-matching-engine-adr-decision.md`](../tasks/reports/PHASE-3-matching-engine-adr-decision.md)
  — completed; ADR-0011 final approval recorded and architecture frozen.
- [`PHASE-2-measurement-isolation.md`](../tasks/reports/PHASE-2-measurement-isolation.md)
  — completed and accepted as Phase 2 closure evidence.
- [`PHASE-2-repository-remote-ci-setup.md`](../tasks/reports/PHASE-2-repository-remote-ci-setup.md)
  — completed and approved; remote CI established.
- [`PHASE-2-final-closure-review.md`](../tasks/reports/PHASE-2-final-closure-review.md)
  — approved and completed; Phase 2 baseline frozen.
- [`PHASE-2-profiling-execution.md`](../tasks/reports/PHASE-2-profiling-execution.md)
  — completed and approved as evidence collection.
- [`PHASE-2-benchmark-orderbook-baseline.md`](../tasks/reports/PHASE-2-benchmark-orderbook-baseline.md)
  — approved component-level baseline.
- [`PHASE-2-verification-structural-limit-matching.md`](../tasks/reports/PHASE-2-verification-structural-limit-matching.md)
  — approved correctness evidence.

## Accepted Decisions

| Decision | Status | Source |
| --- | --- | --- |
| Domain model and correctness baseline | Accepted with constraints; Phase 3 sequence revision approved | [`ADR-0005`](../docs/adr/ADR-0005-domain-model-and-correctness-baseline.md) |
| ADR-first governance | Accepted | [`ADR-0006`](../docs/adr/ADR-0006-adr-first-decision-governance.md) |
| TreeMap side books, intrusive FIFO and active OrderId index | Accepted with constraints | [`ADR-0007`](../docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md) |
| Structural limit matching and `MatchFragment` boundary | Approved | [`ADR-0008`](../docs/adr/ADR-0008-structural-limit-matching.md) |
| JFR-first profiling evidence | Approved | [`ADR-0009`](../docs/adr/ADR-0009-performance-profiling-evidence.md) |
| Defer production optimization until measurement isolation | Approved | [`ADR-0010`](../docs/adr/ADR-0010-optimization-decision-after-profiling.md) |
| MatchingEngine orchestration model | Approved | [`ADR-0011`](../docs/adr/ADR-0011-matching-engine-orchestration-model.md) |
| Event pipeline execution and backpressure | Proposed / Human Blueprint Approval Pending | [`ADR-0012`](../docs/adr/ADR-0012-event-pipeline-execution-and-backpressure.md) |

If a Task and linked ADR disagree, stop and synchronize them before work.

## Verified Current Implementation

- Positive `long`-backed domain identifiers, price, quantity, input Sequence
  and output EventSequence.
- Controlled limit/market order lifecycle plus deterministic Trade and
  Execution value objects; Trade uses EventSequence rather than input Sequence.
- Immutable engine command/result boundary types: submit-limit, cancel,
  outcome, match aggregate and defensive EngineResult collection.
- `TreeMap` bid/ask price indexes with intrusive FIFO levels.
- Active `OrderId -> OrderNode` cancellation index.
- Deterministic structural limit matching with price-time priority, maker
  price, partial/full fills and one-time residual resting.
- OrderBook-focused correctness, invariant and determinism tests.

Synchronous MatchingEngine orchestration is implemented: exact-next command
validation, immutable outcomes, frozen OrderBook delegation, engine-owned
TradeId/EventSequence allocation, and Trade/Execution result mapping.

Market-order execution, event publication, Disruptor pipeline, Netty protocol,
WAL, snapshot and recovery are not implemented. Phase 4 proposes the Disruptor
pipeline but has not received implementation authorization.

## Performance Evidence

Verified fact:

- A component-level JMH OrderBook baseline and JFR/measurement-isolation
  evidence exist under the linked Phase 2 reports.
- Raw JSON and JFR artifacts are local and ignored; reports record their paths,
  generation commands, summaries and limitations.

Not verified:

- 1M+ orders/s
- Microsecond P99
- Zero GC or zero-copy
- Lock-free/wait-free execution
- End-to-end system throughput

These remain targets or hypotheses, never measured project claims.

## Planned System Framework

```text
Client
  -> Netty / Protocol                 [Future Work]
  -> Decoder / Validation             [Future Work]
  -> Ingress + RingBuffer/Disruptor   [Phase 4 Blueprint Proposed / Locked]
  -> MatchingEngine                   [Phase 3 baseline frozen]
  -> OrderBook                        [Phase 2 baseline implemented]
       -> BidBook / AskBook
       -> PriceLevel / OrderQueue
       -> active OrderId index
  -> Trade / Execution results        [Engine generation implemented]
       -> WAL / Recovery              [Future Work]
       -> Output / Metrics            [Future Work]
```

The proposed Phase 4 pipeline gives one consumer thread exclusive ownership of
one MatchingEngine and its symbol OrderBook while running. Until the Blueprint
is approved and implemented, Phase 3 remains purely synchronous and
caller-owned. Any change to matching semantics, core structure, concurrency,
event ordering, protocol, persistence or recovery requires an approved ADR and
enumerated Blueprint Task.

## Known Risks

- Current benchmark evidence is workload-specific and not end-to-end.
- Windows scheduling and setup/profiler overhead limit performance inference.
- Raw evidence is local; reproducibility depends on committed commands and
  summaries.
- The older `feature/domain-model` branch name predates the broader Phase 2
  work; new infrastructure work uses a dedicated branch.
- Branch protection, merge policy automation and release evidence remain
  Future Work; they were outside `TASK-20260820-006`.
- Counter exhaustion and post-mutation fatal handling are not dynamically
  reachable from the public genesis API without an artificial failure seam;
  Stage 3 records this limitation rather than changing production.

## Session Recovery Checklist

1. Read `MASTER_PROMPT.md`, `DEVELOPMENT_RULES.md`, this file and
   `tasks/README.md`.
2. Read the active Phase Blueprint when one exists, then every relevant
   `tasks/active/*` plan and linked ADR.
3. Run the mandatory Git bootstrap commands from `MASTER_PROMPT.md`.
4. Reconcile live Git state with this index; live Git is authoritative for
   repository state.
5. Confirm the current approval gate before any modification.
