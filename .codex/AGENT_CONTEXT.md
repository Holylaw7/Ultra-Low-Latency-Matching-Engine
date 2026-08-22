# AGENT_CONTEXT — Matching Engine Current State

> Last Updated: 2026-08-22
> Purpose: compact current-state index; detailed history lives in Tasks, Stage
> Reports, ADRs and Git.

## Project Dashboard

| Item | Current State |
| --- | --- |
| Project | Ultra-Low-Latency Matching Engine |
| Product scope | Single-node, in-memory, deterministic matching engine |
| Phase | Phase 7 — Live Durable Command Pipeline Integration (`Blueprint Approved / Execution Active`) |
| Latest product task | [`TASK-20260821-023`](../tasks/completed/TASK-20260821-023-phase6-network-benchmark-docs.md) — Completed / Evidence PASS |
| Latest architecture decision | [`ADR-0015`](../docs/adr/ADR-0015-live-durable-command-pipeline-integration.md) — Approved |
| Current planning task | [`TASK-20260822-025`](../tasks/active/TASK-20260822-025-phase7-wal-before-pipeline-coordinator.md) — Authorized / next |
| Governance mode | Phase Blueprint Mode completed, approved and active for future multi-task Phases |
| Product stage | Phase 6 Baseline Frozen; Phase 7 execution active; Product Release separately governed |
| Product approval | Phase 7 Blueprint Approved; `v0.5.0-engineering-baseline` remains frozen; Phase Closure and Product Release not authorized |
| Latest infrastructure task | [`TASK-20260820-006`](../tasks/completed/TASK-20260820-006-repository-remote-ci-setup.md) — Completed |
| Branch | `feature/phase7-live-durable-command-pipeline` |
| Engineering baseline commit | `b7cf68e` |
| Engineering baseline tag | `v0.5.0-engineering-baseline` |
| Remote | `origin` — `git@github.com:Holylaw7/Ultra-Low-Latency-Matching-Engine.git` |
| Remote sync | `origin/feature/phase7-live-durable-command-pipeline` at `93be16638a20cfc2ed16cf3ecd6d5d0b07c885e5`; `origin/master` remains at `2591042`; `v0.5.0-engineering-baseline` remains at the verified Phase 6 merge commit |
| CI | Phase 7 TASK-024 exact-SHA run [32562594583](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32562594583) PASS; Phase 6 master merge [32495076976](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32495076976) PASS; baseline tag [32495218654](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32495218654) PASS; native subagent configuration CI [32497229680](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32497229680) PASS |

## Project Progress

| Phase | Status | Evidence |
| --- | --- | --- |
| Phase 0 — Bootstrap | Completed | Maven reactor, Java 21, JUnit 5, JMH, Checkstyle and CI workflow |
| Phase 1 — Domain Model | Completed / Approved | [`PHASE-1-domain-model.md`](../tasks/reports/PHASE-1-domain-model.md) |
| Phase 2 — Basic OrderBook | Completed / Approved | `v0.1.0-engineering-baseline`, 45 tests, JMH/JFR evidence and passing master CI |
| Phase 3 — Matching Engine | Completed / Approved / Baseline Frozen | [`Final Closure`](../tasks/reports/PHASE-3-matching-engine-closure-authorization.md); `v0.2.0-engineering-baseline` |
| Governance — Phase Blueprint Mode | Completed / Approved / Active | [`TASK-009`](../tasks/completed/TASK-20260821-009-phase-blueprint-governance.md); master CI PASS |
| Phase 4 — Event Pipeline | Completed / Approved / Baseline Frozen | [`Final Closure`](../tasks/reports/PHASE-4-event-pipeline-closure.md); `v0.3.0-engineering-baseline` |
| Phase 5 — Command WAL and Deterministic Replay Foundation | Completed / Approved / Baseline Frozen | [`Blueprint`](../tasks/blueprints/PHASE-5-command-wal-and-replay-blueprint.md); [`ADR-0013`](../docs/adr/ADR-0013-command-wal-and-deterministic-replay.md); [`Closure`](../tasks/reports/PHASE-5-command-wal-replay-closure.md); `v0.4.0-engineering-baseline` |
| Phase 6 — Binary Network Protocol and Single-Session Gateway | Completed / Approved / Baseline Frozen | [`Blueprint`](../tasks/blueprints/PHASE-6-network-protocol-blueprint.md); [`ADR-0014`](../docs/adr/ADR-0014-network-protocol-and-single-session-gateway.md); [`Closure`](../tasks/reports/PHASE-6-network-protocol-closure.md); `v0.5.0-engineering-baseline` |
| Phase 7 — Live Durable Command Pipeline Integration | Blueprint Approved / Execution Active | [`Blueprint`](../tasks/blueprints/PHASE-7-live-durable-command-pipeline-blueprint.md); [`ADR-0015`](../docs/adr/ADR-0015-live-durable-command-pipeline-integration.md); TASK-024..028 |
| Phase 8+ — Snapshot/recovery integration and performance evolution | Future Work | separately approved future Blueprints |

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
    -> Human Phase 4 Blueprint approval [Approved]
    -> TASK-010 foundation [Completed / evidence PASS]
    -> TASK-011 core [Completed / evidence PASS]
    -> TASK-012 verification [Completed / evidence PASS]
    -> TASK-013 benchmark/docs [Completed / evidence PASS]
    -> Phase 4 Closure [Human approval recorded]
    -> normal merge / master CI / baseline tag [Completed / PASS]
    -> TASK-010 through TASK-013 [Archived]
    -> Phase 4 [Baseline Frozen]
    -> Phase 5 Discovery / ADR / Complete Blueprint [Prepared]
    -> Human Phase 5 Blueprint Approval [Approved]
    -> TASK-014 [Completed / exact-SHA CI PASS]
    -> TASK-015 [Completed / exact-SHA CI PASS]
    -> TASK-016 [Completed / exact-SHA CI PASS]
    -> TASK-017 [Completed / exact-SHA CI PASS]
    -> TASK-018 [Completed / remediation R2 evidence PASS]
    -> Limited Closure Remediation R1-R3 [Completed; exact-SHA CI PASS]
    -> Final Human Phase 5 Closure Review [Approved]
    -> normal merge / master verification / CI [Completed / PASS]
    -> v0.4.0-engineering-baseline / tag CI [Completed / PASS]
    -> TASK-014 through TASK-018 [Archived]
    -> Phase 5 [Baseline Frozen]
    -> Phase 6 Discovery / ADR / Complete Blueprint [Prepared]
    -> Human Phase 6 Blueprint Approval [Approved]
    -> TASK-019 [Completed / exact-SHA CI PASS]
    -> TASK-020 [Completed / exact-SHA CI PASS]
    -> TASK-021 [Completed / exact-SHA CI PASS]
  -> TASK-022 [Completed / exact-SHA CI PASS]
  -> TASK-023 [Completed / exact-SHA CI PASS]
  -> Phase 6 Closure Proposal [Prepared]
  -> Limited Closure Remediation (docs-only) [Completed / exact-SHA CI PASS]
  -> Human Phase 6 Closure Review [Approved]
  -> normal --no-ff merge / master verify / master CI [Completed / PASS]
  -> v0.5.0-engineering-baseline / tag CI [Completed / PASS]
  -> TASK-019 through TASK-023 [Archived]
  -> Phase 6 [Baseline Frozen]
  -> Phase 7 Discovery / ADR-0015 / Complete Blueprint [Prepared]
  -> Human Phase 7 Blueprint Approval [Approved]
  -> TASK-024 [Completed / exact-SHA CI PASS]
  -> TASK-025 [Authorized / Next]
  -> TASK-026 through TASK-028 [Authorized conditionally on Evidence Gates]
  -> Phase 7 implementation [Authorized in dependency order]
```

Stage 1 Domain/API Foundation and Stage 2 MatchingEngine Core are completed and
approved. Stage 3 verification-only execution and Human completion review are
complete with ordered result comparison, public-API state probes and no
production test hooks. Phase 3 is closed at the annotated and CI-verified
`v0.2.0-engineering-baseline`. Release, next-phase ADR/implementation,
production optimization and history rewrite remain unauthorized. OrderBook
remains the frozen Phase 2 dependency.

Phase Blueprint Mode is the active governance standard. ADR-0012, the complete
Phase 4 Event Pipeline Blueprint and TASK-010 through TASK-013 were approved by
the Human Blueprint Approval. The bounded single-producer/single-consumer
pipeline, deterministic result handling, backpressure, lifecycle, verification
and component evidence are implemented. Automated evidence gates and Exception
Gates remain mandatory. Existing Domain, OrderBook and MatchingEngine
production files remain frozen. Phase 4 is completed and CI-verified at
`v0.3.0-engineering-baseline`. Phase 5 Discovery selected a versioned command
WAL and strict offline deterministic replay foundation before Network. ADR-0013,
the complete Blueprint and TASK-014 through TASK-018 were approved for strict
dependency-ordered execution. All five Tasks now have exact-SHA CI evidence.
Limited Closure Remediation added deterministic rotation-failure evidence and a
complete mixed-command benchmark with environment/workload/P50/P99 metadata.
R3 documentation synchronization is complete at `0e6ac95` with exact-SHA CI
`32482054086` PASS, and the final gate record `a2176d5` passed CI
`32482214913`. Human Phase 5 Closure is approved. The normal merge completed at
`f1e453a`; master CI `32482831419` and annotated baseline tag CI `32482900227`
passed. TASK-014 through TASK-018 were archived by `915c3ac`, whose exact-SHA
CI `32483612937` passed, and Phase 5 is frozen at
`v0.4.0-engineering-baseline`. Phase 6 implements the approved binary TCP
protocol and single-session Netty gateway in dependency order. TASK-019 through
TASK-023 have completed their evidence gates, including deterministic network
verification and Java 21 component/loopback JMH evidence. Human Phase 6 Closure
is approved and the normal merge/tag workflow is complete: merge `b7cf68e`,
master CI `32495076976`, and `v0.5.0-engineering-baseline` tag CI
`32495218654`. TASK-019 through TASK-023 are archived. Gateway FULL identity
preservation, gateway outbound-write failure terminal handling and
pipeline-failure-to-gateway terminal propagation are verified by
implementation-path review and lower-level tests; dynamic gateway fault
injection for those three paths was not performed and is an explicitly
accepted baseline limitation. No production-only test seam was introduced.
Product Release, Snapshot and online Recovery remain separately governed. Phase
7 now has an approved ADR and complete Blueprint for Live Durable Command
Pipeline Integration. TASK-024 contracts/configuration implementation and its
Evidence Gate are complete with exact-SHA CI PASS; TASK-025 is authorized next,
and TASK-026 through TASK-028 remain conditionally authorized after their
preceding Evidence Gates.
Phase Closure, merge and `v0.6.0-engineering-baseline` remain unauthorized.

Current Blueprint Proposal:
[`PHASE-7-live-durable-command-pipeline-blueprint.md`](../tasks/blueprints/PHASE-7-live-durable-command-pipeline-blueprint.md).

Current Phase 7 ADR:
[`ADR-0015-live-durable-command-pipeline-integration.md`](../docs/adr/ADR-0015-live-durable-command-pipeline-integration.md).

Phase 7 Tasks:
`TASK-024` through `TASK-028` under `tasks/active/`; TASK-024 is completed with
exact-SHA CI PASS, TASK-025 is authorized next, and later Tasks inherit
authorization only after their preceding Evidence Gates.

Current Phase 6 Closure Proposal:
[`PHASE-6-network-protocol-closure.md`](../tasks/reports/PHASE-6-network-protocol-closure.md).

Current proposal report:
[`PHASE-6-network-protocol-blueprint-proposal.md`](../tasks/reports/PHASE-6-network-protocol-blueprint-proposal.md).

Current cumulative Phase 6 implementation report:
[`PHASE-6-network-protocol.md`](../tasks/reports/PHASE-6-network-protocol.md).

Current Closure Record:
[`PHASE-5-command-wal-replay-closure.md`](../tasks/reports/PHASE-5-command-wal-replay-closure.md).

Latest completed Blueprint:
[`PHASE-5-command-wal-and-replay-blueprint.md`](../tasks/blueprints/PHASE-5-command-wal-and-replay-blueprint.md).

Blueprint proposal report:
[`PHASE-4-event-pipeline-blueprint-proposal.md`](../tasks/reports/PHASE-4-event-pipeline-blueprint-proposal.md).

Latest completed plan:
[`TASK-20260821-018-phase5-wal-benchmark-docs.md`](../tasks/completed/TASK-20260821-018-phase5-wal-benchmark-docs.md).

Latest completed governance task:
[`TASK-20260821-009-phase-blueprint-governance.md`](../tasks/completed/TASK-20260821-009-phase-blueprint-governance.md).

Native subagent configuration:

- `.codex/config.toml` enables subagents with a maximum of four concurrent
  child threads and Luna/high defaults.
- `architect.toml` is Sol/high and read-only for Blueprint, Closure and
  Exception Gate reviews.
- `implementer.toml` is Luna/max and workspace-write for one authorized writer.
- `verifier.toml` is Luna/max and read-only for correctness/evidence audits.
- `benchmark-reviewer.toml` is Luna/max and read-only for benchmark methodology.
- `docs-auditor.toml` is Luna/medium and read-only for status/evidence sync.

The enforced topology is one writer plus parallel read-only auditors. Subagents
cannot authorize merge, tag, release, destructive Git actions or Phase Closure;
any ADR conflict, frozen-path/API change, scope expansion or weakened criterion
must stop the main agent at the Exception Gate.

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
| Event pipeline execution and backpressure | Approved with Blueprint conditions | [`ADR-0012`](../docs/adr/ADR-0012-event-pipeline-execution-and-backpressure.md) |
| Versioned command WAL and strict offline deterministic replay | Approved / Implemented / Baseline Frozen | [`ADR-0013`](../docs/adr/ADR-0013-command-wal-and-deterministic-replay.md) |
| Binary protocol v1 and single-session Netty gateway | Approved / Implemented / Baseline Frozen | [`ADR-0014`](../docs/adr/ADR-0014-network-protocol-and-single-session-gateway.md); `v0.5.0-engineering-baseline` |

If a Task and linked ADR disagree, stop and synchronize them before work.

## Current Architecture Baseline

| Decision | Status | Source |
| --- | --- | --- |
| Binary protocol v1 and single-session Netty gateway | Approved / TASK-019..023 archived / Baseline Frozen | [`ADR-0014`](../docs/adr/ADR-0014-network-protocol-and-single-session-gateway.md); `v0.5.0-engineering-baseline` |

ADR-0014 approves one active TCP session, one request in flight, gateway-owned
Command Sequence and bounded ordered result frames. TASK-019..023 implement
that boundary and its evidence; the implementation does not include live WAL
integration, multi-client ingress, Snapshot, online Recovery or Release.

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

Market-order execution and Netty protocol remain future work. The approved
Disruptor pipeline is implemented as a bounded component boundary and frozen
at `v0.3.0-engineering-baseline`. Phase 5 now provides a versioned command WAL,
strict segmented scanning, offline genesis replay and corruption/torn-tail
evidence; it does not provide live durability, Snapshot or online Recovery.

## Performance Evidence

Verified fact:

- A component-level JMH OrderBook baseline and JFR/measurement-isolation
  evidence exist under the linked Phase 2 reports.
- Phase 4 component JMH evidence compares direct processing, producer
  admission and verified batch completion across two capacities and three wait
  modes; see [`pipeline.md`](../docs/benchmark/pipeline.md).
- Phase 5 component JMH evidence separates WAL append, strict scan and offline
  replay across durability modes, segment sizes and command counts; see
  [`recovery.md`](../docs/benchmark/recovery.md).
- Phase 6 component/loopback JMH evidence covers fixed protocol decode/encode
  vectors and one-request-in-flight local TCP round trips; see
  [`network.md`](../docs/benchmark/network.md).
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
  -> Netty / Protocol                 [Phase 6 baseline frozen]
  -> Decoder / Validation             [Phase 6 implemented]
  -> Ingress + RingBuffer/Disruptor   [Phase 4 baseline frozen]
  -> MatchingEngine                   [Phase 3 baseline frozen]
  -> OrderBook                        [Phase 2 baseline implemented]
       -> BidBook / AskBook
       -> PriceLevel / OrderQueue
       -> active OrderId index
  -> Trade / Execution results        [Engine generation implemented]
  -> Command WAL / Offline Replay     [Phase 5 baseline frozen]
  -> Snapshot / Online Recovery       [Future Work]
  -> Output / Metrics                 [Future Work]
```

The Phase 4 pipeline gives one consumer thread exclusive ownership of one
MatchingEngine and its symbol OrderBook while running. Any change to matching
semantics, core structure, concurrency, event ordering, protocol, persistence
or recovery requires an approved ADR and enumerated Blueprint Task.

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
