# Task Plan — TASK-20260825-049

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-049` — GA Correctness and Deterministic Recovery |
| Status | `Completed / Human Closed; G1/G2 PASS / QUALIFYING / FROZEN` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Human Approved |
| Depends On | TASK-048 Human Closure Approved |
| Gates | G1, G2 |

## 2. Goal

Prove supported matching correctness and exact convergence among live runtime,
strict WAL scan, PURE_WAL recovery and Snapshot-tail recovery.

## 3. Authorized Design After Approval

Qualification-only public-boundary runners use `LIFECYCLE_MIX`,
`CROSSING_MULTI_MATCH`, `RESTING_DEPTH` and `MEMORY_STEADY_STATE_V1`, seeds
20260823/24/25, with supported SubmitLimit/Cancel commands only. No production
or golden-semantic change is permitted.

## 4. Acceptance Criteria

- [x] Ordered EngineResult/trade output is exact for every profile/seed.
- [x] Canonical checkpoint and command/transcript/WAL/probe digests agree.
- [x] TradeId and EventSequence suffixes agree across recovery modes.
- [x] Invalid trade, lost command, gap and divergence counts are zero.
- [x] Snapshot results for prefix 1..N are not fabricated or re-emitted.
- [x] Every physical case publishes independent G1/G2 `ga-run-manifest-v1`
  views, a physical-run binding, and both canonical gate results; the fresh
  matrix produced 24/24 paired views and independent gate results.

## 5. Evidence Gate

Focused and existing deterministic suites; public packaged integration;
`mvn verify`; Checkstyle; diff/candidate audit; verifier/docs-auditor; exact-SHA
CI. Any mismatch is B0/B1, not a threshold exception.

## 6. Risks / Rollback / Approval

Harness mismeasurement is B2 and reruns G1/G2/G12 after remediation.
Candidate divergence requires rc.2 or stops GA. Planned commit:
`test(ga): qualify correctness and deterministic recovery`. Closed after the
fresh canonical matrix and Human Final Evidence Review; no additional matrix is
authorized.

## 7. Background / Current Implementation

Phase 8/9 prove component and selected campaign convergence. The preserved
TASK-049 matrix is a valid technical observation, but it lacks the frozen
canonical run/gate contract and cannot be backfilled. Human-approved
remediation keeps the fixed 4-profile × 3-seed × 2-repeat public-path matrix
at 24 physical executions, with paired G1/G2 views per case.

## 8. Requirements, Inputs, Outputs and Non-Goals

Input: exact candidate/JAR, fixed profiles/seeds/counts/prefixes and schema.
Output: immutable per-physical-case raw results, paired canonical G1/G2 run
views, physical-run bindings and independent G1/G2 Gate results. Non-goals: new
workload semantics, Market/Amend, candidate tests/code changes or performance.

## 9. Design / Alternatives / Decision

Selected: 100k commands/profile/seed, WAL 65536, repetitions 2, PURE_WAL and
Snapshot prefixes 25k/50k/75k. Rejected: tiny smoke vectors, random counts and
internal-engine-only execution. ADR-0019 D9 is normative.

## 10. Planned File Changes

| Path | Change |
| --- | --- |
| `qualification/**/ga/correctness/**` | canonical writer, physical binding and evaluator integration |
| qualification tests/resources | matrix/golden/failure fixtures |
| qualification result directories | ignored immutable raw evidence |
| `tasks/reports/PHASE-11-task-049.md` | hashes and G1/G2 summary |

## 11. Detailed Test / Benchmark Plan

Unit: matrix cardinality, prefix/suffix and identity rules. Integration:
packaged live -> WAL -> PURE_WAL/Snapshot-tail. Failure: corruption/mismatch
cannot yield PASS. Replay: all 96 recovery observations and public probes exact.
Benchmark/profile: not applicable; elapsed time is record-only.

## 12. Verification Commands

```powershell
mvn -pl qualification -am test "-Dtest=*GaCorrectness*,*GaDeterministicRecovery*" "-Dsurefire.failIfNoSpecifiedTests=false"
java -jar qualification/target/matching-engine-qualification.jar ga-correctness --matrix ga-g1-g2-v1
mvn verify
git diff --check
git diff --name-only "v0.9.0-rc.1^{}" -- src/main pom.xml core/pom.xml
```

Validate all sidecars, then verifier/docs-auditor and exact-SHA CI.

## 13. Rollback, Gates, Approval and Log

Rollback qualification runner only; never delete FAIL evidence. Candidate
divergence is B0/B1 and has no in-Task fix.

| Stage | Status | Gate |
| --- | --- | --- |
| Implementation | Completed | TASK-048 Human Closure Approved |
| Matrix execution | PASS / evidence frozen | controller `b3df93d`; 24/24 physical cases; 96 recovery observations; paired canonical evidence and gate results |
| Canonical remediation | Completed | paired G1/G2 views, physical bindings, independent gate results and exact-SHA replay validation |
| Completion | Human Closed | post-sync verifier/docs-auditor and Human TASK-049 Closure approved |

| Date | Reviewer / status | Record |
| --- | --- | --- |
| 2026-08-25 | Human / Approved | Phase 11 Blueprint and TASK-049 dependency authorization approved |
| 2026-08-26 | Human / Authorized | TASK-048 closed; TASK-049 implementation and matrix execution authorized |
| 2026-08-26 | Main Luna Max / Evidence | Exact-controller matrix `d75a3a0`: 24/24 cases PASS; 96 recovery observations; sidecar 4,322/4,322 validated |
| 2026-08-26 | CI / Evidence validation | Checkpoint `c3659fa`; Standard `32976467453` PASS; Quick `32976467177` PASS; final read-only Evidence Gate remains pending |
| 2026-08-29 | Main Luna Max / Evidence | Human-authorized replacement matrix under controller `b3df93d`: 24/24 physical cases, 96/96 recovery observations, paired G1/G2 canonical evidence and gate results PASS; evidence frozen |
| 2026-08-29 | Read-only verifier | 24 G1 manifests, 24 G2 manifests, 24 bindings, 48 unique run IDs, 8,840 root inventory entries and complete sidecars — PASS |
| 2026-08-29 | Human / Final Evidence Review and Closure | TASK-049 CLOSED; G1/G2 PASS / QUALIFYING / FROZEN; TASK-050 unlocked for its separate Human scope gate |

### Implementation Log

Implementation is qualification-only and remains bounded by the approved
Phase 11 Blueprint. The original exact-controller G1/G2 matrix remains a
preserved technical observation, not canonical qualifying evidence. The
remediation adds a during-run canonical writer for paired gate views and
physical execution bindings; focused verification and exact-SHA validation are
passing. The single Human-authorized fresh matrix completed 24 physical cases
with 24 G1 views, 24 G2 views, 24 bindings and independent PASS gate results.
Evidence is frozen after Human Closure; no additional matrix execution is
authorized. TASK-050 is unlocked only for its separately approved
qualification-only implementation scope; its formal G3/G7 campaign remains
Human-gated.
