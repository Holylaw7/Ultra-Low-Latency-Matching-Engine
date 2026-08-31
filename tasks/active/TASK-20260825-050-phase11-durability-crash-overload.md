# Task Plan — TASK-20260825-050

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-050` — Durability, Crash and Overload Qualification |
| Status | `In Progress / CHANGES REQUIRED — Round 5 remediation committed/pushed at 59453b4f2480b286fa7109368a563bb2e3ef75b6; Standard 33390678107 and Quick 33390678078 PASS exact-SHA; verifier found documentation state drift only; final documentation sync is local/pending commit and Evidence Gate pending` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Human Approved |
| Depends On | TASK-049 Human Closure Approved |
| Gates | G3, G7 |

## 2. Goal

Qualify the approved durability fault model and bounded overload behavior
without expanding delivery or hardware power-loss claims.

## 3. Scope After Approval

Qualification-only deterministic fault runners and evidence for append/force
failure paths, final torn tail, corruption/gap/checksum rejection, 50 graceful
and 50 post-completed-response forced terminations, session/in-flight/frame/
management bounds and durable-FULL fail-stop. The implementation phase may
build and test this harness; the approved G3/G7 campaign remains a separate
Human execution gate.

## 4. Acceptance Criteria

- [ ] All corruption and invalid prefix cases fail closed.
- [ ] Approved torn-tail handling and every restart converge exactly.
- [ ] 50/50 lifecycle cycles retain immutable cycle artifacts and hashes.
- [ ] Second session/in-flight request never gains a second admission.
- [ ] Durable-FULL is terminal and never retryable.
- [ ] Oversized/invalid/management saturation stays bounded.
- [x] Harness preserves the no-exactly-once, arbitrary crash-window and
  hardware power-loss claim boundary.

## 5. Evidence Gate

Focused failure matrix, full regression, Checkstyle, diff/candidate audit,
verifier/docs-auditor, exact-SHA CI. Failures remain evidence; no automatic
replacement cycle/campaign.

## 6. Exception / Rollback / Approval

Any production seam, new crash semantics or candidate defect triggers Sol/Human
review and normally rc.2. Qualification-only defects are B2. Formal campaign
execution is Human-gated; no campaign has been started in this checkpoint.

## 7. Background / Current Implementation

Phases 5-10 retain strict WAL/recovery and bounded-network evidence, including
dynamic rotation failure but not dynamic `force(true)` failure injection. GA
needs one fixed combined matrix without changing that accepted limitation.

## 8. Requirements, Inputs, Outputs and Non-Goals

Inputs: candidate/JAR, exact Matrix fixtures, 3 legal segment sizes (minimum
4,128 bytes), 100 lifecycle cycles. Outputs: immutable G3/G7 results. The
formal campaign remains separately Human-gated. Non-goals: production fault
seam, hardware power-loss, ambiguous in-flight kill, exactly-once or overload
retry.

## 9. Design / Alternatives / Decision

Selected: reuse public/approved deterministic boundaries, 10k commands/cycle,
50 graceful +50 post-response forced and exact corruption byte mutations.
Rejected: force test hook, arbitrary process kill and code-review-only G3.

## 10. Planned File Changes

| Path | Change |
| --- | --- |
| `qualification/**/ga/durability/**` | cycle/corruption/overload runner and evidence |
| `qualification/QualificationChildProcess*.java`, `QualificationRunner.java`, `ReleaseCandidateQualificationMain.java` | child-process lifecycle and CLI integration |
| `qualification/**/ga/correctness/GaCorrectnessCanonicalContext.java` | approved-candidate identity visibility for qualification runners |
| qualification tests/resources | exact corruption, lifecycle and boundedness fixtures |
| ignored result directories | immutable raw cycle evidence |
| `docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md`, `docs/release/GA-QUALIFICATION-MATRIX.md`, Phase 11 Blueprint | Human-approved A1 legal-WAL clarification only |
| `.github/workflows/ci.yml` | Standard checkout history required by the frozen-boundary qualification test |
| `tasks/reports/PHASE-11-task-050.md` | G3/G7 report |

## 11. Detailed Test / Benchmark Plan

Unit: fixture identity and corruption classification. Integration: packaged
process/recovery/session/management/pipeline boundaries. Failure: every Matrix
fixture plus rotation failure, listener/lease/resource checks. Replay exact.
Benchmark/profile: not applicable.

## 12. Verification Commands

```powershell
mvn -pl qualification -am test "-Dtest=*GaDurability*,*GaOverload*" "-Dsurefire.failIfNoSpecifiedTests=false"
java -jar qualification/target/matching-engine-qualification.jar ga-durability --matrix ga-g3-g7-v1
java -jar qualification/target/matching-engine-qualification.jar ga-overload --matrix ga-g7-overload-v1
mvn verify
git diff --check
git diff --name-only "v0.9.0-rc.1^{}" -- src/main pom.xml core/pom.xml
```

Then artifact/hash scan, verifier/docs-auditor and exact-SHA CI.

## 13. Rollback, Gates, Approval and Log

Rollback only B2 runner code; preserve evidence. Production defect requires
rc.2 and requalification.

### Risks

Fault-window overclaim or a test seam could invalidate the candidate boundary;
the exact Matrix and accepted force limitation prevent both.

| Stage | Status | Gate |
| --- | --- | --- |
| Implementation | Round 5 remediation committed/pushed at `59453b4f2480b286fa7109368a563bb2e3ef75b6` with exact-SHA CI PASS; final documentation-only sync is local / commit and remote Evidence Gate pending | TASK-050 Human implementation approval |
| Formal matrix | Not authorized / not run | separate Human G3/G7 execution approval |
| Completion | Locked | reviewers + CI |

| Date | Reviewer / status | Record |
| --- | --- | --- |
| 2026-08-25 | Human / Approved | TASK-050 scope and qualification-only implementation approved; formal campaign remains locked |
| 2026-08-30 | Main Luna Max / Implementation | Added G3 WAL lifecycle/corruption harness, G7 bounded-overload harness, CLI dispatch and focused tests; no formal campaign executed |
| 2026-08-30 | Human / Amendment and remediation scope | Removed illegal 4,096-byte case in favor of frozen production minimum 4,128; required real child-process forced termination, completed G3 coverage, strict G7 observables and canonical `ga-campaign-summary-v1`; formal campaign remains unauthorized |
| 2026-08-31 | Human / Round 3.1 failure triage and remediation | Standard CI `33374002293` failed because the shallow checkout omitted the frozen baseline object; classified as B2 qualification/test--CI integration defect; explicit full-history checkout remediation authorized and implemented at `b04786420bafd838ac4e0b378a674f766430f3bb`; Standard `33377267636` and Quick `33377267660` passed; the exception is recorded in ADR-0019 |
| 2026-08-31 | Independent Verifier / Round 3.1 review | `CHANGES REQUIRED`: dynamic G3/G7 outputs deferred to the formal Human-gated campaign; runner abnormal-path ABORTED wiring and live RESOURCE_BOUND harness readiness required; no candidate or production defect observed |
| 2026-08-31 | Human / Round 4 Limited Remediation | Authorized governance/status synchronization, real runner `ABORTED` lifecycle integration, FAIL-vs-ABORTED distinction and live runtime RESOURCE_BOUND probe; formal G3/G7 campaign remains unauthorized; commit/push awaits a separate Human approval |
| 2026-08-31 | Human / Round 4 Independent Verifier review | `CHANGES REQUIRED`: stale Round 4 status, G7 semantic failures incorrectly mapped to `ABORTED / B3`, pipelined EOF could be accepted without a complete response boundary, and G3 mixed corruption-pack interruption could be classified `ABORTED / B2`; no candidate or production defect observed |
| 2026-08-31 | Human / Round 5 Limited Remediation | Authorized qualification-only correction of FAIL/B2 versus ABORTED/B3 classification, complete pipelined response-boundary enforcement, G3 interruption precedence and Round 4 status synchronization; committed/pushed at `59453b4f2480b286fa7109368a563bb2e3ef75b6` with Standard `33390678107` and Quick `33390678078` exact-SHA PASS; verifier found documentation state drift only; final documentation sync remains local; formal G3/G7 campaign remains unauthorized |

### Implementation Log

The implementation is qualification-only and remains bounded by the approved
Phase 11 Blueprint and its A1 amendment. Focused G3/G7 tests exercise
test-only publication of canonical run/gate/campaign evidence, raw artifacts
and SHA-256 sidecars; these fixtures are not qualifying campaign evidence.
G3 uses the legal production minimum (4,128 bytes), a real child-JVM process
boundary for forced termination, strict corruption/snapshot coverage and
restart convergence checks. G7 records scenario-specific observable contracts
and rejects arbitrary timeout/close outcomes as proof of a bound. No formal
G3/G7 campaign, candidate mutation or production change has been performed.

Round 3 evidence-contract remediation was committed/pushed at `ee31e9a`; its
Standard CI failed at `33374002293` because the default shallow checkout did
not contain the frozen baseline object, while Quick Lane `33374002245` passed
the exact SHA. Round 3.1 adds the explicit Standard checkout history contract
at `b04786420bafd838ac4e0b378a674f766430f3bb`; Standard `33377267636` and
Quick `33377267660` passed with exact-SHA binding. The approved checkout
exception is recorded in ADR-0019. Round 4 now wires runner abnormal and
infrastructure paths to the existing canonical `ABORTED` outcome (B3), keeps
semantic qualification failures as `FAIL`, and adds a live bounded-pipeline
RESOURCE_BOUND probe whose immutable observation is included in the existing
inventory. Every durability payload is covered by an adjacent sidecar and the
validated `SHA256SUMS` inventory; manifest and inventory membership must agree
exactly, and unlisted or symbolic entries fail closed. Campaign/gate
publication validates candidate and controller identity against the execution
context. Round 4 was committed/pushed at `f07d6bab50704eb172e853744ddd72a20d5df025`
with Standard `33387118548` and Quick `33387118673` exact-SHA PASS; its
Independent Verifier returned `CHANGES REQUIRED` for four findings. Round 5
now routes semantic G7 violations to `FAIL / B2`, reserves `ABORTED / B3` for
runner/infrastructure interruptions, requires a complete pipelined response
boundary before PASS, preserves `ABORTED / B3` precedence for interrupted G3
corruption packs, and synchronizes the Round 4 status record. Focused Round 5
tests pass locally; no formal G3/G7 campaign is authorized by this checkpoint.
Round 5 was committed/pushed at `59453b4f2480b286fa7109368a563bb2e3ef75b6`
with Standard `33390678107` and Quick `33390678078` exact-SHA PASS. Its
Independent Verifier returned `CHANGES REQUIRED` solely for documentation
state drift; the final documentation-only sync is local and pending Human
Commit/Push approval, and the Remediation Evidence Gate remains pending.
