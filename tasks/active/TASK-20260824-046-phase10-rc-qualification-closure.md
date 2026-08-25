# Task Plan — TASK-20260824-046

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260824-046` |
| Title | Release-candidate assembled-runtime qualification and Closure Proposal |
| Status | `In Progress — Characterization Evidence PASS; Sol High delta-only review pending` |
| Implementer | Main Codex / Luna Max — only writer after approval |
| Related ADR | [`ADR-0018`](../../docs/adr/ADR-0018-release-candidate-runtime-boundary.md) |
| Blueprint | [`Phase 10 Blueprint`](../blueprints/PHASE-10-release-candidate-runtime-assembly-blueprint.md) |
| Dependency | TASK-045 Evidence Gate PASS (`f024aef`; Standard CI `32728038236`; Quick Lane `32728038263`) |
| Final Gate | Sol High Closure Review, then Human Phase 10 Closure Approval |

## 2. Goal

Qualify the packaged application through its public Protocol v1 and management
boundaries, reconcile all Phase 10 evidence and prepare—but do not approve—the
Phase 10 Closure Proposal.

## 3. Required Evidence

- [x] Configuration/startup/shutdown/failure matrix pre-campaign coverage passes
  for the packaged process.
- [x] Empty and Snapshot-plus-tail startup converge through the packaged public
  Protocol v1 and management boundaries.
- [x] At least 60 minutes and 1,000,000 accepted commands pass through the
  assembled runtime under the frozen workload/configuration in both authorized
  independent Full Runs.
- [x] Pre-campaign restart/approved termination evidence is deterministic;
  the separately approved Phase 10 Full Campaign completed exactly two runs.
- [x] Startup-to-ready, shutdown, live latency and management overhead retain
  full distributions, raw artifacts, hashes and environment metadata.
- [x] JFR/GC/resource evidence is recorded without filtering or retry-until-pass.
- [ ] verifier, benchmark-reviewer and docs-auditor final sign-off for the
  complete task is pending the final read-only Evidence Gate.
- [ ] All TASK-041 through TASK-046 final Closure references agree.
- [ ] Known limitations and prohibited claims remain explicit.

## 4. Claim Boundary

Evidence may support only a reproducible single-node release-candidate runtime
assembly on the recorded environment. It does not support Product Release,
Production Ready, Internet-safe, SLA/RTO, exactly-once, multi-session, HA,
bounded WAL disk or hardware power-loss safety.

## 5. Forbidden Scope

No production optimization, semantic/default/threshold change, new dependency,
new feature, evidence filtering, baseline-tag movement, merge, RC tag or Product
TASK-046 prepares qualification evidence only; Product Release and any RC tag
remain separately gated by Human Phase 10 Closure Approval.

## 6. Evidence and Closure Gate

```text
assembled-runtime full qualification
 -> immutable manifests / hashes
 -> full regression / Checkstyle / diff audit
 -> verifier + benchmark-reviewer + docs-auditor PASS
 -> exact-SHA Standard/qualification CI PASS
 -> Phase 10 Closure Proposal
 -> STOP
 -> Sol High Final Closure Review
 -> Human Phase 10 Closure Approval
```

Only a later explicit approval may authorize `--no-ff` merge and a candidate
tag. `v0.9.0-rc.1` is not a Product Release.

## 7. Exception Gate / Rollback

Any need to tune production/defaults, alter evidence criteria, filter outliers,
change a frozen semantic or expand claims stops execution. Qualification-only
changes can be reverted while `v0.8.0-engineering-baseline` remains frozen.

## 8. Approval

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-24 | Human Developer | Authorized / Inherited | TASK-045 Evidence Gate PASS; pre-campaign implementation is authorized; the two Full Runs remain separately Human-gated |
| 2026-08-24 | Human Developer | Full Campaign Approved | Exactly two independent `RC_ASSEMBLED_RUNTIME_V1` Full Runs authorized with frozen implementation/configuration; no replacement run permitted |

## 9. Current Implementation and Frozen Qualification Manifest

Phase 9 qualifies the component-composed public path but not a packaged
application/config/management lifecycle. TASK-046 adds qualification-only
orchestration; it does not modify accepted Phase 9 artifacts.

`RC_ASSEMBLED_RUNTIME_V1` uses exactly:

- packaged Phase 10 child process and canonical effective-config SHA-256;
- `MEMORY_STEADY_STATE_V1`, seed `20260823`;
- two new independent runs, each at least 60 minutes AND 1,000,000 accepted;
- command timeout/sample interval 5 seconds;
- at least two natural post-GC samples and per-run
  `chronological-post-gc-v2` PASS; campaign cumulative at least five;
- `SYNC_EACH_APPEND`, WAL segment 65536, Pipeline 1024/BLOCKING;
- loopback Protocol, management enabled, one STATUS request/5 seconds;
- run-manifest-v2 identity/provenance plus artifact/config hashes and atomic
  campaign-summary-v1 references.

Exactly two runs were authorized and completed. Any FAIL/ABORTED/invalid run
would have stopped the campaign without a replacement. Phase 9 runs cannot
participate.

## 10A. Full Campaign Evidence

The two immutable local evidence directories are ignored artifacts under
`qualification-results/phase10-rc-full/`:

| Run | Directory | Manifest SHA-256 | Status | Elapsed | Accepted | Natural GC | Heap guard |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
| A | `rc-assembled-full-e9503064-52b5-4659-b8ef-806a4514b679` | `f65a395256a919fe5a576c8858c2c5a6cd8f8c996bd5a9c2af367a51a33a1fcc` | PASS | `3,601,045 ms` | `1,799,401` | `8` | PASS |
| B | `rc-assembled-full-e0cc3192-bdbe-4ea9-a98f-c9e7fa9b69f5` | `60f24746c23222fa23209117eee1300bc0c0aac1a3a497f8aa23d756ce83a596` | PASS | `3,601,029 ms` | `1,848,908` | `9` | PASS |

Both manifests report listener rebound, recovery lease reacquired, stable WAL
inventory, restored thread baseline and zero temporary files. Their
`configurationIdentitySha256` is
`7367f0cf2b3543f591f24abe3adb386f457ad6433cd8105ede08ad3bd3c5710d`; their
`comparabilityIdentitySha256` is
`ea09a2676ff21fc5350404dbf15eb2613384265d95881edf17fa1941c2766cbe`.
Manifest-declared artifact relative paths and SHA-256 values were verified
against the preserved files.

The immutable campaign summary is under
`qualification-results/phase10-rc-campaign/rc-assembled-campaign-72ea9c3f-0619-41b5-9d90-3dbb3ec9eaf6/`:

| Artifact | SHA-256 |
| --- | --- |
| `qualification-campaign-summary-v1.txt` | `89799b16f317f0cb083821368dcfe005dbbe508964adf8de234a1be61db78ae6` |
| `artifact-hashes-v1.txt` | `b422e1f680a5007ef858528ab277674651b536d3a1dbe7352e43726a79e6a10d` |

The summary records `requiredRunCount=2`, `qualifyingRunCount=2`,
`cumulativeNaturalPostGcSamples=17`, equal configuration/comparability
identities and `campaign.result=true`.

The Full Run result artifacts contain counts, digests, resource CSV and JFR
evidence. The separate live startup/shutdown/response percentile distribution
and management-overhead evidence is provided by the qualification-only
characterization remediation. The immutable result directory is
`qualification-results/phase10-characterization-v3/rc-characterization-60bac1ef-bbe1-4df1-adb0-fa5ab310464b/`.
It records 30/30 empty-WAL and 30/30 Snapshot-tail lifecycle samples, raw
response/management samples, 62 JFR files, 62 resource CSV files, 62 non-zero
allocation summaries and the
following immutable summary SHA-256:
`6204f190e70415b4aa8bfc48a43824b211530d315bd5cc29e00ec8da0d29f4d6`.
No production performance, RTO, SLA or Production Ready claim is made.

## 10. Planned Files

| Path | Change |
| --- | --- |
| `qualification/src/main/java/**/releasecandidate/**` | child-process lifecycle/campaign runner |
| `qualification/src/test/java/**/releasecandidate/**` | quick/golden/malformed/summary tests |
| `.github/workflows/**` | only if the Blueprint-defined bounded Quick lane needs dispatch; otherwise no change |
| `docs/operations/**`, `docs/benchmark/**` | config/runbook/characterization evidence |
| `tasks/reports/PHASE-10-task-046.md` | task evidence and Closure Proposal |
| ADR/Blueprint/README/context | final status synchronization |

Workflow change is conditional and requires it to remain bounded; a dependency,
Full campaign in ordinary CI or changed trigger is an Exception Gate.

## 11. Detailed Test / Performance Plan

Before Full runs: quick 10,000-command process lane, 10 PURE_WAL starts, 10
Snapshot-tail starts, 10 acknowledged-boundary forced terminations and the full
TASK-041–045 failure matrix. Each lifecycle cycle has immutable hashes.

Full runs retain response P50/P95/P99/P99.9/max, throughput, startup-to-ready,
shutdown, JFR/GC/resource and status-request latency. A separate paired 10-minute
management-idle vs STATUS-1Hz characterization uses identical build/config;
greater than 10% throughput or P99 change triggers Human Evidence Review rather
than optimization.

## 12. Verification Commands

```text
mvn -pl qualification -am test
mvn verify
git diff --check
git diff --name-only v0.8.0-engineering-baseline...HEAD
java -jar qualification/target/matching-engine-qualification.jar rc-full \
  --artifact core/target/matching-engine-rc.jar \
  --config <absolute-frozen-config> --output <new-empty-run-dir> \
  --run-id <unique-id> --git-sha <exact-sha> \
  --baseline-tag v0.8.0-engineering-baseline
java -jar qualification/target/matching-engine-qualification.jar rc-campaign \
  --run-manifest <run-a-manifest-v2> --run-manifest <run-b-manifest-v2> \
  --output <new-empty-campaign-dir>
java -jar qualification/target/matching-engine-qualification.jar characterize \
  --artifact qualification/target/matching-engine-qualification.jar \
  --output qualification-results/phase10-characterization \
  --git-sha <exact-sha> --baseline-tag v0.8.0-engineering-baseline
```

The qualification module packages the runner at exactly
`qualification/target/matching-engine-qualification.jar`. Thresholds, workload,
seed, JVM/GC and sampling settings are not CLI-overridable. The Full
command/manifest must be committed and exact-SHA CI PASS before Human
authorization to spend the two 60-minute evidence units. TASK-046 requires a
separate explicit Human Full Campaign approval, matching Phase 9 evidence
governance. The `characterize` command is a separate bounded evidence unit and
does not start or replace an `RC_ASSEMBLED_RUNTIME_V1` Full Run.

## 13. ADR / Alternatives / Risks

ADR-0018 D10-D13/D16 governs. Reusing only Phase 9 component evidence was
rejected because it bypasses the packaged composition; inventing a new command
distribution was rejected to preserve comparability. Main risks are provenance
drift, retry-until-pass and claim inflation; immutable manifests, exactly-two
authorization and independent reviewers mitigate them.

Rollback removes qualification-only code/docs and retains all failed artifacts
under the approved ignored evidence policy. No production format rolls back.

## 14. Stages / Git / CI

quick harness -> lifecycle matrix -> pre-campaign Evidence Gate -> Human Full
Campaign authorization -> exactly two runs -> campaign evaluation -> three
read-only auditors -> exact-SHA CI -> Closure Proposal -> STOP.

Planned commits: `test(runtime): qualify release-candidate assembly` and
`docs(runtime): prepare phase10 closure evidence`. No merge/tag/release.

## 15. Completion Checklist / Log

- [x] TASK-045 PASS and inherited approval (`f024aef` / Standard `32728038236` / Quick `32728038263`)
- [x] pre-campaign implementation/CI (`0a96593`; Standard `32730760419`;
  Quick `32730760501`)
- [x] lifecycle matrix PASS — 10 `PURE_WAL` + 10 Snapshot-tail + 10 approved
  post-response forced terminations (30/30)
- [x] exactly two Full runs and campaign PASS, or preserved failure + STOP
- [x] characterization artifacts/hashes/provenance published; 30/30 + 30/30
  lifecycle samples and two fixed 10-minute trials PASS
- [x] claim boundaries and Closure input synchronized
- [ ] Sol High Closure Review pending after proposal
- [ ] Human Closure, merge, candidate tag and Product Release not self-authorized

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-24 | Proposed | Qualification manifest and stop gates frozen | docs only |
| 2026-08-24 | Authorized / Next | TASK-045 completed; begin pre-campaign assembled-runtime qualification implementation. | TASK-045 exact-SHA Standard/Quick CI PASS |
| 2026-08-24 | Pre-Campaign Evidence PASS | Packaged Java 21 lifecycle command passed 30/30 cycles; Full Campaign remains Human-gated. | `0a96593`; Standard CI `32730760419`; Quick Lane `32730760501`; summary SHA `71862f5e49ec554c2344f0836785d6e737e1457fc06083d755c2d98e10564bc6` |
| 2026-08-24 | Full Campaign Evidence PASS | Exactly two independent assembled-runtime Full Runs passed; campaign evaluator recorded 2/2 qualifying and `campaign.result=true`. Final percentile/profile evidence reconciliation and Closure Review remain pending. | runner `1a02e66`; Standard CI `32734798459`; Quick Lane `32734798461`; manifests `f65a3952...` / `60f24746...`; campaign summary `89799b16...` |
| 2026-08-25 | Characterization Remediation Evidence PASS | Qualification-only characterization produced 30 empty-WAL + 30 Snapshot-tail lifecycle samples, raw response/management distributions, paired 10-minute management trials, 62 JFR/resource/allocation artifacts and immutable hashes. | source checkpoint `7ba7ed0`; summary `6204f190...`; manifest `3b093b39...`; sidecar `fd8432cc...`; initial CI `32754918129` flaky-test failure, rerun `a0747bb` / `32802089849` PASS; Sol High delta-only review pending |
