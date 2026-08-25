# Task Plan — TASK-20260825-053

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-053` — Performance and Capacity Campaign |
| Status | `Proposed — Human Campaign Gate Locked` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Proposed |
| Depends On | TASK-051/052 pre-campaign Evidence Gates plus explicit Human approval |
| Gates | G4, G5 |

## 2. Goal

Execute exactly the approved performance SLO and capacity campaigns against the
frozen candidate and publish immutable run/campaign evidence.

## 3. Execution Contract

The controller/candidate SHAs, JDK/JVM/GC/heap, storage, allocator, workload,
seed, thresholds and measurement boundary freeze before Run 1. Run outcomes
are PASS/FAIL/ABORTED. Any failure stops; no automatic replacement run.

## 4. Acceptance Criteria

- [ ] Three independent ten-minute SLO runs each meet every G4 threshold.
- [ ] Sixty lifecycle samples and paired management comparison pass.
- [ ] 100k/250k/500k/1M capacity points pass G5.
- [ ] 1M point accepts all commands, recovers >=166k active orders and converges.
- [ ] Raw samples, manifests, artifact hashes, JFR/GC and environment remain.
- [ ] Campaign summaries reference immutable manifest hashes.

## 5. Evidence Gate

Artifact/hash/identity checks; campaign evaluator; focused and full regression;
frozen audit; verifier; mandatory benchmark-reviewer; docs-auditor; exact-SHA
CI. The result cannot authorize optimization.

## 6. Failure / Rollback / Approval

FAIL is retained and stops TASK-054. ABORTED replacement requires Human
approval. Candidate defect requires rc.2. No code is expected in this Task
beyond separately approved B2 remediation. Campaign execution is not
authorized by Blueprint Approval alone.

## 7. Background / Current Implementation

TASK-051 will produce only tested runners and pre-campaign evidence. No GA SLO
or capacity result exists until this separately authorized immutable campaign.

## 8. Requirements, Inputs, Outputs and Non-Goals

Inputs: exact approved runner/controller SHA, candidate/reference environment
and frozen campaign manifest. Outputs: all run manifests, raw/JFR/resource
artifacts, campaign summaries and G4/G5 results. Non-goals: code changes,
replacement run, optimization, different host or maximum-capacity claim.

## 9. Design / Alternatives / Decision

Selected: execute SLO runs first, stop on non-PASS, then fixed ascending
capacity scales. Rejected: interleaving/tuning runs, best-of-N and reusing Phase
10 characterization as GA PASS.

## 10. Planned File Changes

No implementation file is expected. Ignored immutable `qualification-results/`
artifacts plus `tasks/reports/PHASE-11-task-053.md` and Phase 11 evidence/status
documents are the only outputs. Any code change requires B2/B1 review.

## 11. Detailed Test / Benchmark Plan

Benchmark: exact G4 public-client latency/SLO matrix and G5 capacity scales.
Profile: JFR, GC, CPU, allocation, heap/RSS and storage. Correctness/replay
checks remain Gate criteria. All raw samples retained.

## 12. Verification Commands

```powershell
java -jar qualification/target/matching-engine-qualification.jar ga-performance --lane full --campaign ga-g4-v1
java -jar qualification/target/matching-engine-qualification.jar ga-capacity --lane full --campaign ga-g5-v1
java -jar qualification/target/matching-engine-qualification.jar ga-evidence-verify --gate G4,G5
mvn verify
git diff --check
git diff --name-only "v0.9.0-rc.1^{}" -- src/main pom.xml core/pom.xml
```

Run commands are locked until explicit Human Campaign Approval.

## 13. Rollback, Gates, Approval and Log

There is no result rollback. Preserve FAIL/ABORTED and stop. A B2 remediation
uses a new controller/evidence ID and reruns affected Gates after approval.

### Risks

Environmental drift or retry-until-pass would invalidate the campaign; identity
verification and the no-replacement rule stop execution.

| Stage | Status | Gate |
| --- | --- | --- |
| Campaign | Not Authorized | Human performance/capacity approval |
| Review | Locked | verifier + benchmark + docs + CI |
| Completion | Locked | G4/G5 PASS |

| Date | Reviewer / status | Record |
| --- | --- | --- |
| 2026-08-25 | Human / Pending | Campaign not approved by Blueprint alone |
| 2026-08-25 | Proposed | No runs |

### Implementation Log

No campaign has begun; the preceding dated rows are the initial log.
