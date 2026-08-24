# Task Plan — TASK-20260824-046

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260824-046` |
| Title | Release-candidate assembled-runtime qualification and Closure Proposal |
| Status | `Dependency Locked` |
| Implementer | Main Codex / Luna Max — only writer after approval |
| Related ADR | [`ADR-0018`](../../docs/adr/ADR-0018-release-candidate-runtime-boundary.md) |
| Blueprint | [`Phase 10 Blueprint`](../blueprints/PHASE-10-release-candidate-runtime-assembly-blueprint.md) |
| Dependency | TASK-045 Evidence Gate PASS |
| Final Gate | Sol High Closure Review, then Human Phase 10 Closure Approval |

## 2. Goal

Qualify the packaged application through its public Protocol v1 and management
boundaries, reconcile all Phase 10 evidence and prepare—but do not approve—the
Phase 10 Closure Proposal.

## 3. Required Evidence

- [ ] Configuration/startup/shutdown/failure matrix passes for the packaged
  process.
- [ ] Empty and Snapshot-plus-tail startup converge with frozen reference
  checkpoint/digest/probe evidence.
- [ ] At least 60 minutes and 1,000,000 accepted commands pass through the
  assembled runtime under the frozen workload/configuration.
- [ ] Restart and approved termination evidence remains deterministic.
- [ ] Startup-to-ready, shutdown, live latency and management overhead retain
  full distributions, raw artifacts, hashes and environment metadata.
- [ ] JFR/GC/resource evidence is recorded without filtering or retry-until-pass.
- [ ] verifier, benchmark-reviewer and docs-auditor report PASS.
- [ ] All TASK-041 through TASK-046 reports and exact-SHA CI references agree.
- [ ] Known limitations and prohibited claims remain explicit.

## 4. Claim Boundary

Evidence may support only a reproducible single-node release-candidate runtime
assembly on the recorded environment. It does not support Product Release,
Production Ready, Internet-safe, SLA/RTO, exactly-once, multi-session, HA,
bounded WAL disk or hardware power-loss safety.

## 5. Forbidden Scope

No production optimization, semantic/default/threshold change, new dependency,
new feature, evidence filtering, baseline-tag movement, merge, RC tag or Product
Release is authorized by TASK-046.

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
| 2026-08-24 | Human Developer | Pending | TASK-046 after TASK-045 PASS; Closure separate |

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

Exactly two runs are authorized by the task. Any FAIL/ABORTED/invalid run stops
without a replacement. Phase 9 runs cannot participate.

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
```

The qualification module packages the runner at exactly
`qualification/target/matching-engine-qualification.jar`. Thresholds, workload,
seed, JVM/GC and sampling settings are not CLI-overridable. The Full
command/manifest must be committed and exact-SHA CI PASS before Human
authorization to spend the two 60-minute evidence units. TASK-046 requires a
separate explicit Human Full Campaign approval, matching Phase 9 evidence
governance.

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

- [ ] TASK-045 PASS and inherited approval
- [ ] pre-campaign implementation/CI and explicit Full authorization
- [ ] lifecycle matrix PASS
- [ ] exactly two Full runs and campaign PASS, or preserved failure + STOP
- [ ] all artifacts/hashes/provenance/reviewers PASS
- [ ] claim boundaries and Closure input synchronized
- [ ] Sol High Closure Review pending after proposal
- [ ] Human Closure, merge, candidate tag and Product Release not self-authorized

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-24 | Proposed | Qualification manifest and stop gates frozen | docs only |
