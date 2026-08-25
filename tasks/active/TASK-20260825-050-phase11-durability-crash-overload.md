# Task Plan — TASK-20260825-050

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-050` — Durability, Crash and Overload Qualification |
| Status | `Proposed — Dependency Locked` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Proposed |
| Depends On | TASK-049 Evidence Gate PASS |
| Gates | G3, G7 |

## 2. Goal

Qualify the approved durability fault model and bounded overload behavior
without expanding delivery or hardware power-loss claims.

## 3. Scope After Approval

Qualification-only deterministic fault runners and evidence for append/force
failure paths, final torn tail, corruption/gap/checksum rejection, 50 graceful
and 50 post-completed-response forced terminations, session/in-flight/frame/
management bounds and durable-FULL fail-stop.

## 4. Acceptance Criteria

- [ ] All corruption and invalid prefix cases fail closed.
- [ ] Approved torn-tail handling and every restart converge exactly.
- [ ] 50/50 lifecycle cycles retain immutable cycle artifacts and hashes.
- [ ] Second session/in-flight request never gains a second admission.
- [ ] Durable-FULL is terminal and never retryable.
- [ ] Oversized/invalid/management saturation stays bounded.
- [ ] No exactly-once, arbitrary crash-window or hardware power-loss claim.

## 5. Evidence Gate

Focused failure matrix, full regression, Checkstyle, diff/candidate audit,
verifier/docs-auditor, exact-SHA CI. Failures remain evidence; no automatic
replacement cycle/campaign.

## 6. Exception / Rollback / Approval

Any production seam, new crash semantics or candidate defect triggers Sol/Human
review and normally rc.2. Qualification-only defects are B2. Planned commit:
`test(ga): qualify durability crash and overload`. Still dependency locked.

## 7. Background / Current Implementation

Phases 5-10 retain strict WAL/recovery and bounded-network evidence, including
dynamic rotation failure but not dynamic `force(true)` failure injection. GA
needs one fixed combined matrix without changing that accepted limitation.

## 8. Requirements, Inputs, Outputs and Non-Goals

Inputs: candidate/JAR, exact Matrix fixtures, 3 segment sizes, 100 lifecycle
cycles. Outputs: immutable G3/G7 results. Non-goals: production fault seam,
hardware power-loss, ambiguous in-flight kill, exactly-once or overload retry.

## 9. Design / Alternatives / Decision

Selected: reuse public/approved deterministic boundaries, 10k commands/cycle,
50 graceful +50 post-response forced and exact corruption byte mutations.
Rejected: force test hook, arbitrary process kill and code-review-only G3.

## 10. Planned File Changes

| Path | Change |
| --- | --- |
| `qualification/**/ga/durability/**` | cycle/corruption/overload runner |
| qualification tests/resources | exact corruption and boundedness fixtures |
| ignored result directories | immutable raw cycle evidence |
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
| Implementation | Dependency locked | TASK-049 PASS |
| Matrix | Locked | G3/G7 PASS |
| Completion | Locked | reviewers + CI |

| Date | Reviewer / status | Record |
| --- | --- | --- |
| 2026-08-25 | Human / Pending | Blueprint Approval required |
| 2026-08-25 | Proposed | No implementation or cycles |

### Implementation Log

No implementation has begun; the preceding dated rows are the initial log.
