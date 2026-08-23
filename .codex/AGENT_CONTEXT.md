# AGENT_CONTEXT — Matching Engine Current State

> Last Updated: 2026-08-23
> Purpose: compact current-state index; detailed history lives in Tasks, Stage
> Reports, ADRs and Git.

## Project Dashboard

| Item | Current State |
| --- | --- |
| Project | Ultra-Low-Latency Matching Engine |
| Product scope | Single-node deterministic matching engine with additive pipeline, WAL and protocol boundaries |
| Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability (`TASK-036 Evidence Gate PASS; TASK-037 Next`) |
| Latest product task | [`TASK-20260822-034`](../tasks/completed/TASK-20260822-034-phase8-benchmark-docs-closure.md) — Completed / Archived / Baseline Frozen |
| Latest architecture decision | [`ADR-0017`](../docs/adr/ADR-0017-system-qualification-performance-reliability.md) — Approved |
| Current planning task | [`TASK-20260823-036`](../tasks/active/TASK-20260823-036-public-boundary-qualification-harness.md) — Completed / Evidence Gate PASS |
| Governance mode | Phase Blueprint Mode completed, approved and active for future multi-task Phases |
| Product stage | Phase 8 Baseline Frozen at `v0.7.0-engineering-baseline`; Phase 9 qualification in progress; Product Release separately governed |
| Product approval | Phase 8 Human Closure Approved; merge `87abbc1` / Master CI `32622722649` PASS; `v0.7.0-engineering-baseline` / Tag CI `32622757607` PASS; Phase 9 Blueprint Approved / TASK-035 authorized |
| Latest infrastructure task | [`TASK-20260820-006`](../tasks/completed/TASK-20260820-006-repository-remote-ci-setup.md) — Completed |
| Branch | `feature/phase9-system-qualification` |
| Engineering baseline commit | `87abbc1` (Phase 8 merge) |
| Engineering baseline tag | `v0.7.0-engineering-baseline` |
| Remote | `origin` — `git@github.com:Holylaw7/Ultra-Low-Latency-Matching-Engine.git` |
| Remote sync | `origin/master` synchronized at Phase 8 merge `87abbc1`; pre-existing `.vscode/` remains untouched; `v0.7.0-engineering-baseline` is frozen at merge `87abbc1` |
| Latest Phase 7 CI | Master merge `6473365` — [32574891113](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32574891113) PASS; tag `v0.6.0-engineering-baseline` — [32574958017](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32574958017) PASS |
| Latest Phase 7 docs CI | TASK-028 evidence checkpoint `9fed6b2` — [32574274905](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32574274905) PASS; final docs sync commits are included in merge `6473365` |
| Latest Phase 8 CI | Technical Closure input `c59d7c0` — [32616802595](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32616802595) PASS; remediation `4bdfb97` — [32620164524](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32620164524) PASS; merge `87abbc1` — [32622722649](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32622722649) PASS; tag — [32622757607](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32622757607) PASS |
| CI | Phase 7 TASK-024 implementation exact-SHA run [32562594583](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32562594583) PASS; Phase 7 docs/status sync exact-SHA run [32562746074](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32562746074) PASS; Phase 7 TASK-025 implementation exact-SHA run [32564005988](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32564005988) PASS; Phase 7 TASK-025 evidence/status sync exact-SHA run [32564290961](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32564290961) PASS; Phase 7 TASK-026 implementation exact-SHA run [32565087793](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32565087793) PASS; Phase 7 TASK-027 baseline verification exact-SHA run [32565591806](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32565591806) PASS; Phase 7 TASK-027 test-remediation exact-SHA run [32566165212](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32566165212) PASS; Phase 7 approved-boundary remediation exact-SHA run [32570890919](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32570890919) PASS; Phase 7 remediation documentation sync exact-SHA run [32571104763](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32571104763) PASS; Phase 6 master merge [32495076976](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32495076976) PASS; baseline tag [32495218654](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32495218654) PASS; native subagent configuration CI [32497229680](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32497229680) PASS |

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
| Phase 7 — Live Durable Command Pipeline Integration | Completed / Approved / Baseline Frozen | [`Blueprint`](../tasks/blueprints/PHASE-7-live-durable-command-pipeline-blueprint.md); [`ADR-0015`](../docs/adr/ADR-0015-live-durable-command-pipeline-integration.md); `v0.6.0-engineering-baseline` |
| Phase 8 — Snapshot Checkpoint and Online Recovery Bootstrap | Completed / Human Approved / Baseline Frozen at `v0.7.0-engineering-baseline` | [`Blueprint`](../tasks/blueprints/PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md); [`ADR-0016`](../docs/adr/ADR-0016-snapshot-checkpoint-and-online-recovery-bootstrap.md); [`TASK-034 report`](../tasks/reports/PHASE-8-task-034.md) |
| Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability | Blueprint Approved / TASK-036 Evidence Gate PASS / TASK-037 Next | [`Blueprint`](../tasks/blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md); [`ADR-0017`](../docs/adr/ADR-0017-system-qualification-performance-reliability.md); [`TASK-036`](../tasks/active/TASK-20260823-036-public-boundary-qualification-harness.md) |
| Phase 10+ — Further recovery evolution and production hardening | Future Work | separately approved future Blueprints |

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
  -> TASK-025 [Completed / exact-SHA CI PASS]
  -> TASK-026 [Completed / exact-SHA CI PASS]
  -> TASK-027 [Limited Remediation Round 2 Completed / Evidence Gate PASS]
  -> TASK-028 [Completed / Evidence Gate PASS at `9fed6b2` / CI `32574274905`]
  -> Phase 7 Closure Proposal [Approved]
  -> normal --no-ff merge / master verify / master CI [Completed / PASS]
  -> v0.6.0-engineering-baseline / tag CI [Completed / PASS]
  -> TASK-024 through TASK-028 [Archived]
  -> Phase 7 [Baseline Frozen]
  -> Phase 8 Discovery / ADR-0016 / Complete Blueprint [Prepared]
  -> Human Phase 8 Blueprint Approval [Approved]
  -> TASK-029 [Completed / Evidence Gate PASS at `66fc9d2` / CI `32577713667`]
  -> TASK-030 [Completed / Evidence Gate PASS at `6907391` / CI `32579065372`]
  -> TASK-031 [Completed / Evidence Gate PASS at `eaed8b8` / CI `32580018903`]
  -> TASK-032 [Completed / Evidence Gate PASS at `22568e6` / CI `32613235358`]
  -> TASK-033 [Completed / Evidence Gate PASS at `eff5955` / CI `32614610701`]
  -> TASK-034 [Completed / Evidence Gate PASS at `9835624` / CI `32616029460`]
  -> Technical Closure input [c59d7c0 / CI `32616802595` PASS; 195 tests / 0 failures / Checkstyle 0]
  -> Phase 8 Closure [Completed / Human Approved]
  -> Merge/master verification/tag/final sync [Completed / PASS]
  -> v0.7.0-engineering-baseline [Frozen]
  -> Phase 9 Blueprint [Approved]
  -> TASK-20260823-035 [Completed / Evidence Gate PASS at `22d13fe` / CI `32625554518`]
  -> TASK-20260823-036 [Completed / Evidence Gate PASS at `f90e42c` / standard CI `32627744868` and quick CI `32627744878`]
  -> TASK-20260823-037 [Authorized / Next]
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
7 has an approved ADR and complete Blueprint for Live Durable Command Pipeline
Integration. TASK-024 through TASK-028 passed their dependency-ordered Evidence
Gates; TASK-028 benchmark/docs evidence is at `9fed6b2` / CI `32574274905`, with
all read-only reviewers PASS. Human Phase 7 Closure is approved. The normal
merge is `6473365` with master CI `32574891113`; the annotated
`v0.6.0-engineering-baseline` tag passed CI `32574958017`. TASK-024 through
TASK-028 are archived. Phase 7 is frozen. Phase 8 Discovery prepared ADR-0016,
a Complete Blueprint and TASK-029 through TASK-034; Human Blueprint Approval is
recorded. TASK-029 canonical checkpoint implementation is complete at
`66fc9d2` / CI `32577713667`; TASK-030 Snapshot v1 codec/store is complete at
`6907391` / CI `32579065372`; TASK-031 offline recovery planner/replay is
complete at `eaed8b8` / CI `32580018903`; TASK-032 recoverable live handoff is
complete at `22568e6` / CI `32613235358`; TASK-033 is complete at `eff5955` /
CI `32614610701`; TASK-034 benchmark and Closure Proposal preparation is
complete at `9835624` / CI `32616029460`. The technical Closure input is
`c59d7c0` / CI `32616802595` PASS with 195 tests, 0 failures and Checkstyle 0.
Human Phase 8 Closure Approval is complete after the docs-only remediation and
Sol High delta review. Merge/master verification/tag/final sync are complete;
Phase 8 is frozen at `v0.7.0-engineering-baseline`. Phase 9 Blueprint
Approval is recorded, TASK-035 Evidence Gate is PASS at `22d13fe` / CI
`32625554518`, and TASK-036 Evidence Gate is PASS after remediation at
`f90e42c` with standard CI `32627744868` and Quick Lane `32627744878` PASS.
The corrected checkpoint/probe evidence and read-only audits are accepted;
TASK-037 is authorized next.
Production optimization,
Phase 10 and Product Release remain locked.
Reconnect/deduplication,
multi-session support and Product Release remain outside the proposal.

Current Blueprint:
[`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../tasks/blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md).

Current ADR:
[`ADR-0017-system-qualification-performance-reliability.md`](../docs/adr/ADR-0017-system-qualification-performance-reliability.md).

Current proposal report:
[`PHASE-9-system-qualification-blueprint-proposal.md`](../tasks/reports/PHASE-9-system-qualification-blueprint-proposal.md).

Phase 7 Tasks:
`TASK-024` through `TASK-028` are archived under `tasks/completed/`. All five
Evidence Gates and the final Closure Review are approved. The Phase 7 merge is
`6473365` / CI `32574891113`, and `v0.6.0-engineering-baseline` tag CI is
`32574958017`. The pre-existing `.vscode/` remains untouched and no frozen
production paths were changed by TASK-028 documentation/benchmark work.

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

Native subagent configuration and execution policy:

- `.codex/config.toml` enables subagents with a maximum of four concurrent
  child threads and Luna/high defaults.
- `architect.toml` is Sol/high and read-only for Blueprint, Closure and
  Exception Gate reviews.
- `implementer.toml` is Luna/max and workspace-write for an optional isolated
  writer; it is not the default sequential Task executor.
- `verifier.toml` is Luna/max and read-only for correctness/evidence audits.
- `benchmark-reviewer.toml` is Luna/max and read-only for benchmark methodology.
- `docs-auditor.toml` is Luna/medium and read-only for status/evidence sync.

The enforced topology is **Main Luna Max = orchestrator + default sole writer**,
with parallel read-only auditors when useful. The implementer subagent is an
optional isolated worker only for non-overlapping patches. A stalled writer
delegation is cancelled and resumed by the main agent; it is not an Exception
Gate unless it reveals an architecture, scope or acceptance problem. Subagents
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
| Live durable command pipeline integration | Approved / Implemented / Baseline Frozen | [`ADR-0015`](../docs/adr/ADR-0015-live-durable-command-pipeline-integration.md); `v0.6.0-engineering-baseline` |
| Snapshot checkpoint and online recovery bootstrap | Completed / Human Approved / Baseline Frozen at `v0.7.0-engineering-baseline` | [`ADR-0016`](../docs/adr/ADR-0016-snapshot-checkpoint-and-online-recovery-bootstrap.md); [`TASK-034 report`](../tasks/reports/PHASE-8-task-034.md) |

If a Task and linked ADR disagree, stop and synchronize them before work.

## Current Architecture Baseline

| Decision | Status | Source |
| --- | --- | --- |
| Live durable command pipeline | Approved / TASK-024..028 archived / Baseline Frozen | [`ADR-0015`](../docs/adr/ADR-0015-live-durable-command-pipeline-integration.md); `v0.6.0-engineering-baseline` |
| Snapshot checkpoint and online recovery bootstrap | Completed / Human Approved / Baseline Frozen at `v0.7.0-engineering-baseline` | [`Phase 8 Blueprint`](../tasks/blueprints/PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md) |

ADR-0015 composes the single-session Gateway, Command WAL and Event Pipeline
under WAL-before-execute and fail-stop semantics. TASK-024..028 implement and
verify that boundary at `v0.6.0-engineering-baseline`. ADR-0016 approves
pure-WAL and Snapshot-plus-tail recovery with listener-last handoff. TASK-029
provides the canonical in-memory checkpoint foundation at `66fc9d2` / CI
`32577713667`; TASK-030 provides the Snapshot v1 codec/store at `6907391` /
CI `32579065372`; TASK-031 provides offline recovery planner/replay at
`eaed8b8` / CI `32580018903`; TASK-032 provides listener-last live handoff at
`22568e6` / CI `32613235358`; TASK-033 completed at `eff5955` / CI
`32614610701` with repeated recovery, corruption, listener-last and public
probe evidence. Dynamic `force(true)`/move fault injection remains explicitly
unverified because no production-only seam is authorized. TASK-034 benchmark
and Closure Proposal preparation is complete. Technical Closure input is
`c59d7c0` / CI `32616802595` PASS with 195 tests, 0 failures and Checkstyle 0.
Human Phase 8 Closure Approval is complete; merge/master verification, tag and
final status sync are complete. Phase 8 is frozen at `v0.7.0-engineering-baseline`;
Phase 9 qualification is authorized; later scope remains locked.

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

Market-order execution remains future work. Protocol v1 and the single-session
Netty gateway are frozen at `v0.5.0-engineering-baseline`; Phase 7's opt-in
durable composition is frozen at `v0.6.0-engineering-baseline`. The approved
Disruptor pipeline remains a bounded component boundary frozen at
`v0.3.0-engineering-baseline`. Phase 5 provides a versioned command WAL,
strict segmented scanning, offline genesis replay and corruption/torn-tail
evidence. Phase 8 authorizes canonical Snapshot checkpoints and listener-last
  online recovery bootstrap in dependency order; TASK-029 through TASK-034
  evidence preparation is complete. Dynamic force/move fault injection remains
  explicitly unverified because no production-only seam is authorized.

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
- Phase 7 TASK-028 evidence separates WAL append/force, append-plus-publish,
  local response encoding and one-in-flight alternating Submit/Cancel loopback;
  see [`durable-pipeline.md`](../docs/benchmark/durable-pipeline.md). The
  results are component/local-host observations only and are not durable ACK,
  power-loss, online Recovery or production-readiness claims.
- Phase 8 TASK-034 evidence separates pure-WAL replay, Snapshot decode,
  Snapshot-tail recovery, offline Snapshot creation and bootstrap-to-listener
  over a 20-case matrix. It records heap metadata, JMH GC-profiler allocation
  metrics, Throughput `ops/ms` and SampleTime P50/P95/P99/P999 in
  [`PHASE-8-task-034.md`](../tasks/reports/PHASE-8-task-034.md); the results are
  component/local-host observations only.
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
  -> Snapshot / Online Recovery       [Phase 8 baseline frozen at v0.7.0]
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
