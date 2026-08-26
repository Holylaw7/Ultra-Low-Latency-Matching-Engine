# AGENT_CONTEXT — Matching Engine Current State

> Last Updated: 2026-08-26
> Purpose: compact current-state index; detailed history lives in Tasks, Stage
> Reports, ADRs and Git.

## Project Dashboard

| Item | Current State |
| --- | --- |
| Project | Ultra-Low-Latency Matching Engine |
| Product scope | Single-node deterministic matching engine with additive pipeline, WAL and protocol boundaries |
| Phase | Phase 11 — GA Qualification (`Blueprint Approved; TASK-048 false-positive disposition remediation Evidence Gate PASS`) |
| Latest product task | [`TASK-20260825-048`](../tasks/active/TASK-20260825-048-phase11-reproducibility-security-preflight.md) — In Progress; G9 `32856372581` PASS/qualifying/frozen; fresh G11 `32947367541` FAIL/non-qualifying/preserved after two read-only-confirmed documentation false positives; exact disposition remediation Evidence Gate PASS at `7be2b61` (Standard `32951233073`, Quick `32951233014`); no fresh G11 authorized |
| Latest architecture decision | [`ADR-0019`](../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) — Accepted |
| Current planning task | TASK-20260825-048 — In Progress; offline G11 exact Gitleaks false-positive disposition amendment Evidence Gate PASS at `7be2b61` (Standard `32951233073`, Quick `32951233014`); current policy `ga-security-toolchain-v2.properties` hash `02225e9d08f458f3d593747ad9937bd7c777b6787f52b253328213d7a8ebc117`; fresh G11 remains separately Human-gated |
| Governance mode | Phase Blueprint Mode completed, approved and active for future multi-task Phases |
| Product stage | Phase 9 frozen at `v0.8.0-engineering-baseline`; Phase 10 frozen at `v0.9.0-rc.1`; Phase 11 GA qualification in progress; Product Release separately governed |
| Product approval | Phase 9 Human Closure Approved; merge `ef73f60` / Master CI `32711512036` PASS; `v0.8.0-engineering-baseline` / Tag CI `32711649980` PASS; Closure Input `8e5d39d` / Standard CI `32709188522` / Quick Lane `32709188327`; remediation `5f3b1c5` / Standard CI `32710712341` / Quick Lane `32710712428` PASS |
| Latest infrastructure task | [`TASK-20260820-006`](../tasks/completed/TASK-20260820-006-repository-remote-ci-setup.md) — Completed |
| Branch | `docs/phase11-ga-qualification-blueprint` |
| Engineering baseline commit | `e2828f5` (Phase 10 merge; Master CI `32816928409` PASS) |
| Engineering baseline tag | `v0.9.0-rc.1` (Tag CI `32817075147` / Quick `32817075152` PASS) |
| Remote | `origin` — `https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine.git` |
| Remote sync | `origin/master` remains at workflow-installation merge `0575c76`; the Phase 11 controller branch carries the immutable candidate references and current offline G11 policy amendment; `v0.9.0-rc.1` remains unchanged; `.vscode/` remains untouched |
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
| Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability | Completed / Human Approved / Baseline Frozen at `v0.8.0-engineering-baseline` | [`Blueprint`](../tasks/blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md); [`ADR-0017`](../docs/adr/ADR-0017-system-qualification-performance-reliability.md); [`TASK-040 report`](../tasks/reports/PHASE-9-task-040.md); merge `ef73f60`; Master CI `32711512036`; Tag CI `32711649980` |
| Phase 10 — Release-Candidate Runtime Assembly | Completed / Human Approved / RC baseline frozen at `v0.9.0-rc.1` | [`Blueprint`](../tasks/blueprints/PHASE-10-release-candidate-runtime-assembly-blueprint.md); [`ADR-0018`](../docs/adr/ADR-0018-release-candidate-runtime-boundary.md); [`TASK-046 report`](../tasks/reports/PHASE-10-task-046.md); merge `e2828f5`, Master CI `32816928409`, Tag CI `32817075147`, campaign 2/2 PASS |
| Phase 11+ / Product Release | Future Work | separate Discovery, Blueprint and Human Approval required |

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
  -> TASK-20260823-037 [Completed / Archived; Human Closure Approved]
  -> TASK-20260823-038 [Completed; Full 20/10 campaign PASS; Evidence Gate PASS]
  -> TASK-20260823-039 [Completed / Evidence Gate PASS]
  -> TASK-20260823-040 [Completed / Archived; Human Phase 9 Closure Approved]
  -> Phase 9 merge `ef73f60` / Master CI `32711512036` PASS
  -> `v0.8.0-engineering-baseline` / Tag CI `32711649980` PASS
  -> Phase 9 [Baseline Frozen]
  -> Phase 10 Discovery / ADR / Complete Blueprint [Prepared]
  -> Human Phase 10 Blueprint Approval [Approved 2026-08-24]
  -> TASK-041 [Completed / Evidence Gate PASS; `cc9a957` / CI `32718394177` / Quick `32718394269`]
  -> TASK-042 [Completed / Evidence Gate PASS; `1eba2c5` / CI `32720292382` / Quick `32720292393`]
  -> TASK-043 [Completed / Evidence Gate PASS; `247d526` / CI `32724123762` / Quick `32724123745`]
  -> TASK-044 [Completed / Evidence Gate PASS; `c3f0883` / CI `32726203105` / Quick `32726203076`]
  -> TASK-045 [Completed / Evidence Gate PASS; `f024aef` / CI `32728038236` / Quick `32728038263`]
  -> TASK-046 [Completed / Archived; Full Campaign PASS; v4 characterization PASS; Human Closure Approved]
  -> Phase 10 merge `e2828f5` / Master CI `32816928409` PASS
  -> `v0.9.0-rc.1` / Tag CI `32817075147` PASS
  -> Phase 10 [RC baseline frozen; final docs/archive synchronized]
  -> Phase 11 Blueprint / ADR-0019 [Human Approved 2026-08-25]
  -> GA Evidence Schemas v1 / Matrix / Security Toolchain [Approved / Frozen]
  -> Apache-2.0 repository policy [Human accepted]
  -> GitHub binary distribution without Maven Central [Human accepted]
  -> TASK-047 [Completed / Evidence Gate PASS; implementation `d25eac6`, status `2521500`; CI `32828844611` / Quick `32828844541`]
  -> TASK-048 [In Progress; `OFFLINE_SUPPLY_CHAIN_SECURITY_V1` amendment; root-selector remediation `e1464ed` / CI `32927818204` / Quick `32927818172` PASS; license-report remediation `30c89c4` / CI `32932454011` / Quick `32932454009` PASS; fresh G11 `32943456313` FAIL/B2/preserved on undeclared step-local `REPO`; shell-scope remediation `eced533` / CI `32945056542` / Quick `32945056508` PASS; evidence/status sync `e51db47` / CI `32945333516` / Quick `32945333468` PASS; docs reconciliation `2c20f4f` / CI `32945964869` / Quick `32945964878` PASS; final B2 audit checkpoint `688d955` / CI `32946223271` / Quick `32946223268` PASS; G9 `32856372581` PASS/qualifying/frozen; fresh G11 separately gated]
  -> TASK-048 false-positive disposition amendment [Human approved; Evidence Gate PASS at `7be2b61`; fresh G11 not authorized]
  -> TASK-049 through TASK-056 [Dependency ordered; locked]
  -> Phase 11 Full Campaigns / Closure / v1.0.0 / GitHub Release / GA [Not Authorized]
  -> Product Release [Not Authorized]
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
the prior bounded TASK-037 remediation passed at `c420313` with standard CI
`32645549709` and Quick Lane `32645549694`. The current v2 provenance and
campaign-summary remediation `e678a98` passed standard CI `32683768373` and
Quick Lane `32683768370`. Final pre-campaign docs/status synchronization is
recorded at `dcfcccd`, with standard CI `32684036767` and Quick Lane
`32684036758` PASS. Human subsequently approved exactly two independent v2
Full Runs; both passed all run gates. The controlled ignored evidence root
`qualification/qualification-results-v2-campaign-20260824/` contains the
immutable manifests and atomic campaign summary SHA-256
`5bf1b84b30226807d79f5a0a4950ae649c3a72a860d6d6b13edd9fa715e24112`, recording
two qualifying runs and 44 cumulative natural samples. TASK-037 Human Evidence /
Closure Approval is complete; TASK-038 completed its Full 20/10
restart/termination campaign and Evidence Gate. TASK-039 completed its full
JMH matrix and representative GC/JFR profile with read-only reviewer and
exact-SHA CI PASS. Phase 9 Closure, merge, baseline tag and final archive
synchronization are complete. Phase 10 Discovery and Complete Blueprint
Proposal are approved. TASK-041 runtime contracts are implemented within the
additive boundary and passed exact-SHA CI at `cc9a957`. TASK-042 composition
assembly is complete at `1eba2c5` with Standard CI `32720292382` and Quick Lane
`32720292393` PASS; TASK-043 completed at `247d526` with Standard CI
`32724123762` and Quick Lane `32724123745` PASS; TASK-044 completed at
`c3f0883` with Standard CI `32726203105` and Quick Lane `32726203076` PASS;
TASK-045 completed at `f024aef` with Standard CI `32728038236` and Quick Lane
`32728038263` PASS; TASK-046 pre-campaign is complete at `0a96593` with
Standard CI `32730760419`, Quick Lane `32730760501` and a 30/30 packaged
lifecycle matrix. Human then authorized exactly two assembled Full Runs; runner
`1a02e66` passed Standard CI `32734798459` and Quick Lane `32734798461`, both
runs passed and the campaign summary records `2/2` qualifying with
`campaign.result=true`. Qualification-only characterization then produced
30/30 empty-WAL and 30/30 Snapshot-tail lifecycle samples, raw response and
management distributions, two fixed 10-minute trials, 62 JFR files and 62
resource CSV files. Summary SHA-256 is
`6204f190e70415b4aa8bfc48a43824b211530d315bd5cc29e00ec8da0d29f4d6`;
manifest SHA-256 is `3b093b39dc765172ac8b97ee22beda5798f7e96d0bd7da694608318915508212`;
the recursive 919-entry sidecar is `fd8432ccd7b4f064b7766cb5397e32741405a7a043267612caf5efc17c46d7b1`.
The source checkpoint's initial Standard CI `32754918129` exposed an
unrelated flaky core-test assertion. Fixed technical docs/evidence input
`dfe1f7d` passed Standard CI `32813393216` and Quick Lane `32813393127`; the
docs-only status correction `b7530f6` passed Standard CI `32813640675` and Quick
Lane `32813640754` as external validation. Benchmark review found the v3 timer
included Protocol connection setup; the qualification-only measurement-boundary
correction was completed at `7566814` and v4 evidence is PASS. Verifier,
benchmark-reviewer and docs-auditor sign-off plus Sol High delta-only Closure
Review were subsequently approved. Final docs/evidence sync validation `eb9a4ab` passed
Standard CI `32814053468` and Quick Lane `32814053459`; this is external
validation only and the fixed Closure Input remains `dfe1f7d`.
Production optimization
and Product Release remain unauthorized.
Reconnect/deduplication,
multi-session support and Product Release remain outside the proposal.

Human Limited Criterion Amendment is approved for TASK-037: heap stability is
campaign-level, with at least two independently qualifying Full runs, at least
two natural post-GC samples and a chronological per-run guard, plus five
cumulative natural samples. Read-only investigation found the prior raw heap
trend confounded by harness retention and intentionally growing business state.
The current Limited Qualification-Only Remediation is authorized to add
streaming/bounded aggregation, the separately versioned
`MEMORY_STEADY_STATE_V1` lane, and v2 immutable provenance/campaign-summary
evidence while preserving the existing workload vectors. Historical Run #1 and
Run #2 remain preserved non-qualifying evidence; the new A'/B' campaign is
separate. TASK-037 is archived after Human Closure Approval; TASK-038 and
TASK-039 have completed their approved Evidence Gates. TASK-040 has completed
its final evidence reconciliation and Closure Proposal. Current Closure Input
is `8e5d39d` with Standard CI `32709188522` PASS and Qualification Quick Lane
`32709188327` PASS; Sol High and Human Phase 9 Closure Approval are complete.
Phase 9 is frozen at `v0.8.0-engineering-baseline` from merge `ef73f60`; Master
CI `32711512036` and Tag CI `32711649980` passed. Phase 10 Discovery and its
Complete Blueprint Proposal are approved; TASK-041 passed its Evidence Gate at
`cc9a957`, TASK-042 passed at `1eba2c5` with Standard CI `32720292382` and Quick
Lane `32720292393`, TASK-043 completed at `247d526` with Standard CI
`32724123762` and Quick Lane `32724123745`; TASK-044 completed at `c3f0883`
with Standard CI `32726203105` and Quick Lane `32726203076`; TASK-045 completed
at `f024aef` with Standard CI `32728038236` and Quick Lane `32728038263`; TASK-046
pre-campaign passed at `0a96593` with Standard CI `32730760419`, Quick Lane
`32730760501` and lifecycle 30/30. The approved assembled Full Campaign then
passed 2/2 at runner `1a02e66` (Standard `32734798459`, Quick `32734798461`).
Final latency/profile evidence reconciliation, read-only Evidence Gate, Sol High
Closure Review and Human Phase 10 Closure are complete. Normal merge `e2828f5`,
Master CI `32816928409`, RC tag `v0.9.0-rc.1` / Tag CI `32817075147` and final
archive are complete; Product Release remains unauthorized.

The 2026-08-24 Human Limited Provenance Amendment authorizes only
`qualification-run-manifest-v2`, runtime-captured provenance,
`configurationIdentitySha256` / `comparabilityIdentitySha256`, and an
immutable `qualification-campaign-summary-v1` under `qualification/**`, plus
focused tests and evidence documentation. The configuration identity excludes
run ID, timestamps, PID, paths and outcomes; comparability identity records the
approved JDK/JVM/GC/heap/OS/filesystem/Netty/Disruptor/JFR dimensions. Existing
Run A/B artifacts are never backfilled or repackaged. The next gate after the
approved campaign evidence was Sol High Final Campaign Closure Review followed
by Human TASK-037 Evidence / Closure Approval; TASK-038 is now authorized next.

Current TASK-037 v2 campaign evidence:

```text
Run A' = qualification-full-bfeb2a65-aa89-42bc-a1c3-f473979d79cb
  PASS; 3,619,093 ms; 1,784,601 accepted; 22 natural samples; heap guard PASS
Run B' = qualification-full-934f9d5d-d972-456f-941c-93d1cbdc5bd3
  PASS; 3,620,413 ms; 1,741,681 accepted; 22 natural samples; heap guard PASS
configurationIdentitySha256 = dcec0edae1b407aa1ebef21514cb9de7ad3b95b8cb840969e00ca8ca2b262e29
comparabilityIdentitySha256 = a14d43165e967eb2b8890a6882dea1cacaaf51cfd5602b0b0fb15d2bedfbd643
campaign.result = true
cumulativeNaturalPostGcSamples = 44
summarySha256 = 5bf1b84b30226807d79f5a0a4950ae649c3a72a860d6d6b13edd9fa715e24112
```

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
