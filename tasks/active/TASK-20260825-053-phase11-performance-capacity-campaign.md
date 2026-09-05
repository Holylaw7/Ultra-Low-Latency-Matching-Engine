# Task Plan — TASK-20260825-053

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-053` — Performance and Capacity Campaign |
| Status | `Active — RC2 G4 pre-execution remediation; formal execution Human-gated` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Approved RC2 G4 pre-execution contract |
| Depends On | RC2 Candidate Freeze, Wave-1 G1/G2/G3 PASS, and explicit Human G4 approval |
| Gates | G4 only; G5 is separately rebased and remains locked |

## 2. Goal

Make the RC2 G4 qualification harness execution-ready, then (only after a
separate Human Gate) execute the approved performance, lifecycle and management
campaign against the frozen candidate. G5 is not part of this active lineage.

## 3. Execution Contract

The RC2 tag, production and candidate JAR identities, qualification controller,
JDK/JVM/GC/heap, storage, allocator, workload, seed, thresholds and measurement
boundary freeze before Run 1. Formal G4 uses Protocol v2, window N=8,
`SYNC_EACH_APPEND`, a bounded closed-loop continuous-refill load model, a 60 s
warmup and a 10 minute measurement. Run outcomes are PASS/FAIL/ABORTED. Any
trustworthy blocker stops; no automatic replacement run.

## 4. Acceptance Criteria

- [ ] Three independent RC2 Protocol-v2/N=8 ten-minute SLO runs each meet every
      G4 threshold.
- [ ] Sixty fresh lifecycle cycles produce 60 startup and 60 shutdown samples;
      both P99 values pass.
- [ ] Management Pair A and Pair B pass independently with exactly STATUS@1Hz.
- [ ] G5 capacity points are separately rebased and Human-authorized.
- [ ] Raw samples, manifests, artifact hashes, JFR/GC and environment remain.
- [ ] Campaign summaries reference immutable manifest hashes.

## 5. Evidence Gate

Artifact/hash/identity checks; campaign evaluator; focused and full regression;
frozen audit; verifier; mandatory benchmark-reviewer; docs-auditor; exact-SHA
CI. The result cannot authorize optimization.

## 6. Failure / Rollback / Approval

FAIL is retained and stops later G4 physical executions and TASK-054. ABORTED
replacement requires Human approval. A production defect requires a new RC
candidate; qualification-only B2 remediation uses a new controller/evidence
identity. Formal campaign execution is not authorized by this task plan alone.

## 7. Background / Current Implementation

RC2 Wave 1 established candidate and evidence identity. This task owns only the
active G4 lineage; historical RC1 TASK-053 plans and evidence remain reference
material and are not RC2 qualifying evidence.

## 8. Requirements, Inputs, Outputs and Non-Goals

Inputs: exact RC2 candidate/controller/JAR identities, approved G4 contract and
reference environment. Outputs: run manifests, raw/JFR/resource artifacts,
lifecycle/management evidence, campaign summaries and G4 result. Non-goals:
G5 implementation, candidate mutation, replacement run, optimization, different
host or maximum-capacity claim.

## 9. Design / Alternatives / Decision

Selected: remediate and audit G4 first; execute performance, lifecycle and
management serially with stop-on-first-blocker. G5 is a future separately
authorized lineage. Rejected: interleaving/tuning runs, best-of-N, and reusing
RC1 characterization as RC2 PASS.

## 10. Planned File Changes

Qualification-only runner/evidence changes may be made under the active RC2
remediation Gate. Production files, POM, dependencies, workflows and historical
RC1 artifacts remain immutable. The resulting controller and qualification JAR
must receive new identities.

## 11. Detailed Test / Benchmark Plan

Benchmark: exact RC2 G4 public-client latency/SLO, lifecycle and management
campaign. Profile: JFR, GC, CPU, allocation, heap/RSS and storage. Per-request
offer-to-validated-response samples and all raw trial data are retained.

## 12. Verification Commands

```powershell
java -jar qualification/target/matching-engine-qualification.jar ga-performance --lane full --campaign ga-g4-performance-v2
mvn verify
git diff --check
git diff --name-only "v0.9.0-rc.2^{}" -- src/main pom.xml core/pom.xml
```

Formal run commands remain locked until explicit Human G4 Execution Approval.

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
| Completion | Locked | G4 PASS; G5 separately rebased and closed by its own Human Gate |

| Date | Reviewer / status | Record |
| --- | --- | --- |
| 2026-08-25 | Human / Pending | Campaign not approved by Blueprint alone |
| 2026-08-25 | Proposed | No runs |

### Implementation Log

No campaign has begun; the preceding dated rows are the initial log.
