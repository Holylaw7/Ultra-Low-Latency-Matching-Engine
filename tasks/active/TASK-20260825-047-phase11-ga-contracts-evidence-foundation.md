# Task Plan — TASK-20260825-047

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-047` — GA Contracts and Evidence Foundation |
| Status | `Completed / Evidence Gate PASS` |
| Owner / Implementer | Human Developer / Main Luna Max after approval |
| Created | `2026-08-25` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Approved |
| Baseline | `v0.9.0-rc.1` tag object `dfd38c0`, peeled `e2828f5` |
| Next Gate | TASK-048 Evidence Gate / implementation authorized by Blueprint |

## 2. Goal

After approval, implement qualification-only candidate verification,
`ga-gate-result-v1`, `ga-run-manifest-v1`, `ga-campaign-summary-v1`,
`ga-release-manifest-v1`, blocker classification and the G1-G12 state model.

## 3. Scope

In scope: `qualification/**`, qualification tests, Phase 11 evidence docs and
new schema fixtures. Out of scope: candidate/production/build/dependency
changes, campaign execution, existing workflow changes, tag/release actions.

## 4. Acceptance Criteria

- [ ] Candidate verifier checks tag object, peeled SHA, production tree and JAR.
- [ ] Canonical schemas have golden bytes/hashes and fail-closed malformed tests.
- [ ] PASS/FAIL/ABORTED and B0-B4 rules are deterministic.
- [ ] Atomic immutable publication and artifact-reference validation pass.
- [ ] Candidate and controller identities cannot be conflated.
- [ ] Existing evidence cannot be backfilled or rewritten.

## 5. Test / Evidence Plan

Focused schema/hash/atomic-publication tests; `mvn verify`; Checkstyle;
`git diff --check`; production/build/tag audit; verifier and docs-auditor;
exact-SHA Standard and GA Quick CI. Benchmark: not applicable.

## 6. Risks, Rollback and Exception Gate

Schema ambiguity can invalidate every later Gate; freeze canonical form before
execution. Revert the Task commit on failure. Stop for any production/build
change, weaker evidence rule, new dependency or RC mutation.

## 7. Git / Approval

Planned commit: `test(qualification): add GA evidence contracts`. No merge,
tag or release. Human Blueprint Approval has authorized TASK-047; later Tasks
remain dependency-gated.

## 8. Background and Current Implementation

Phase 10 has `qualification-manifest-v2` evidence but no GA Gate/result/release
contracts or exact candidate verifier. Existing manifests are historical input,
not implementations of the new schema.

## 9. Requirements, Inputs and Outputs

Inputs: annotated tag/ref, peeled commit, clean candidate tree/JAR and the
normative GA Evidence Schemas. Outputs: qualification-only codecs/validators,
candidate identity result, schema golden fixtures and TASK-047 report. Non-
goals: scanning, correctness campaigns, SLO runs and release publication.

### Non-Goals

No scanner execution, campaign execution, candidate mutation, workflow change,
tag or release action.

## 10. Design, Alternatives and Decision

Selected: strict ASCII key/value canonical format aligned with existing
qualification evidence and fully frozen by ADR-0019. Rejected: JSON (new
canonicalization decisions) and reuse of v2 by extension (unknown-field and
release semantics would remain ambiguous). ADR/Blueprint status remains
The approved architecture impact is qualification-only.

## 11. Planned File Changes

| Path | Change |
| --- | --- |
| `qualification/src/main/**/ga/evidence/**` | schema codecs, validators, candidate verifier |
| `qualification/src/test/**/ga/evidence/**` | golden/malformed/identity/publication tests |
| `qualification/src/test/resources/ga/evidence/**` | small deterministic fixtures |
| `tasks/reports/PHASE-11-task-047.md` | cumulative evidence |

## 12. Detailed Test and Benchmark Plan

Unit: every scalar bound, family gap, unknown field/version, percent encoding,
path traversal and hash mismatch. Integration: temp/force/read-back/atomic move,
existing target and candidate tag peeling. Failure: truncated/oversized/
duplicate/noncanonical inputs. Determinism: repeated bytes/hash exact.
Benchmark/profile: not applicable.

## 13. Verification Commands

```powershell
mvn -pl qualification -am test "-Dtest=*GaEvidence*,*CandidateVerifier*" "-Dsurefire.failIfNoSpecifiedTests=false"
mvn verify
git diff --check
git diff --name-only "v0.9.0-rc.1^{}" -- src/main pom.xml core/pom.xml
git rev-parse v0.9.0-rc.1
git rev-parse "v0.9.0-rc.1^{}"
```

Then verifier/docs-auditor and exact-SHA Standard/GA Quick CI must PASS.

## 14. Phase Gates, Approval Record and Implementation Log

| Stage | Status | Next Gate |
| --- | --- | --- |
| ADR/Blueprint | Approved | TASK-047 implementation |
| Implementation | Completed | TASK-048 Evidence Gate |
| Verification/docs | Completed | TASK-048 Evidence Gate |

| Date | Reviewer | Decision | Constraints |
| --- | --- | --- | --- |
| 2026-08-25 | Human Developer | Approved | TASK-047 authorized; later Tasks dependency-gated |

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-25 | Paused | Schema identity-width contradiction discovered before commit | Human amendment required |
| 2026-08-25 | Resumed | Limited Schema Amendment approved; explicit Git SHA-1/SHA-256 typing | focused verification |
| 2026-08-25 | Completed / Evidence Gate PASS | Added GA codec/store/evaluator/candidate verifier and field-type tests; no production changes | `d25eac6`; Standard CI `32828665352` PASS; Quick `32828665372` PASS |

### Implementation Log

The table above is the implementation log; it remains design-only.
