# Task Plan — TASK-20260825-051

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-051` — Performance SLO and Capacity Foundation |
| Status | `Closed — Human TASK-051 Pre-Campaign Evidence Gate PASS` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Approved |
| Depends On | TASK-050 Evidence Gate PASS |
| Gates | G4, G5 pre-campaign |
| Manual Gate | Stop after pre-campaign Evidence Gate |

## 2. Goal

Implement qualification-only fixed-SLO and capacity runners, provenance,
Quick/smoke validation and campaign commands. Do not execute the Full campaign.

## 3. Frozen Criteria

Three ten-minute runs: >=500 accepted/s, P50 <=2.5ms, P99 <=5ms,
P99.9 <=10ms, zero errors/timeouts/mismatch; 60 lifecycle samples with startup
and shutdown P99 <=1.25s; paired STATUS@1Hz regression <=10%. Capacity scales
100k/250k/500k/1M with >=166k recovered active orders at 1M.

## 4. Acceptance Criteria

- [ ] Measurement boundary is qualification client -> Protocol TCP -> response.
- [ ] Full raw distribution and resource/provenance data are retained.
- [ ] Capacity reports state support envelope, not maximum.
- [ ] Golden/smoke tests prove thresholds and no filtering/retry behavior.
- [ ] Configuration freezes into immutable manifests before execution.
- [ ] Pre-campaign reviewers and exact-SHA CI PASS, then execution stops.

## 5. Evidence Gate

Focused runner/percentile/identity tests; Quick smoke only; `mvn verify`;
Checkstyle; diff/candidate audit; verifier, mandatory benchmark-reviewer and
docs-auditor; exact-SHA Standard/GA Quick CI.

## 6. Exception / Rollback / Approval

No threshold, workload, JVM/GC or candidate change to obtain PASS. The TASK-051
qualification remediation/review object is committed and pushed at
`1bdab634de6c580327b1c9677a45fb08526331f1`; final Standard CI
`33501819205` and Quick Lane `33501819240` passed with exact-SHA binding.
Current canonical Quick readiness is established for G4 run
`c8954804-fe13-46fb-af45-d88097f0930d` and G5 run
`013ca2c2-2f31-48b5-aa6b-9fd98147c331`, both bound to `1bdab634...`.
The intermediate `5b4998d8855d4e418b2e897129571c8c16de700d` controller and
historical `638d893a3f830c89ffe99914897094968de6bbd4` Quick manifests remain
preserved as historical evidence. Full G4/G5 campaign execution remains
separately Human-gated.

The current review object also contains the separately recorded TASK-050
post-closure qualification-harness B2 exception in ADR-0019. It does not
change TASK-051 criteria or reopen TASK-050.

## 7. Background / Current Implementation

Phase 10 has complete characterization but no pre-declared product SLO or GA
capacity support envelope. Current qualification provides reusable public-path
and resource measurement foundations only.

## 8. Requirements, Inputs, Outputs and Non-Goals

Inputs: exact candidate/reference environment, D11/D12 thresholds and v1
schemas. Outputs: runner, Quick evidence and a Human-reviewable frozen campaign
manifest. Non-goals: Full run, optimization, arbitrary host comparison, maximum
capacity or threshold tuning.

## 9. Design / Alternatives / Decision

Selected: fixed Phase 10 host identity, three ten-minute SLO runs, 60 lifecycle
samples, paired management trials and 4 capacity scales. Rejected: fastest
available host, single best run, JMH substitution or throughput-only Gate.

## 10. Planned File Changes

| Path | Change |
| --- | --- |
| `qualification/**/ga/performance/**` | SLO/lifecycle/paired-trial runner |
| `qualification/**/ga/capacity/**` | scale runner and envelope evaluator |
| qualification tests/resources | percentile, identity, threshold and abort tests |
| `tasks/reports/PHASE-11-task-051.md` | pre-campaign evidence |

## 11. Detailed Test / Profile Plan

Unit: nearest-rank percentile, boundaries, all-run conjunction, environment
identity and no replacement. Integration/Quick: short public-path trials for
measurement correctness only. Profile: JFR default qualification config, GC,
CPU, allocation, RSS, filesystem/storage and allocator metadata.

## 12. Verification Commands

```powershell
mvn -pl qualification -am test "-Dtest=*GaPerformance*,*GaCapacity*" "-Dsurefire.failIfNoSpecifiedTests=false"
java -jar qualification/target/matching-engine-qualification.jar ga-performance --lane quick
java -jar qualification/target/matching-engine-qualification.jar ga-capacity --lane quick
mvn verify
git diff --check
git diff --name-only "v0.9.0-rc.1^{}" -- src/main pom.xml core/pom.xml
```

Mandatory benchmark-reviewer plus verifier/docs-auditor and exact-SHA CI.

## 13. Rollback, Gates, Approval and Log

Rollback qualification runner only. Environment mismatch is B3; production
shortfall is a blocker, not optimization authority.

### Risks

Host substitution, percentile bugs or threshold tuning could manufacture PASS;
exact environment identity, raw samples and benchmark review mitigate them.

| Stage | Status | Gate |
| --- | --- | --- |
| Implementation/Quick | Complete; committed/pushed; canonical G4/G5 Quick PASS / QUICK_READINESS_ONLY | Human TASK-051 Pre-Campaign Evidence Gate PASS |
| Reviewer remediation | Committed / pushed at `1bdab634...`; bounded remediation Evidence Gate PASS | Human bounded reviewer remediation Evidence Gate |
| Governance synchronization | Historical checkpoint `55be8de...`; reviewer snapshot is time-scoped and later executions are separate historical records | Human TASK-051 closure review |
| Full campaign | Not Authorized | explicit Human approval |

| Date | Reviewer / status | Record |
| --- | --- | --- |
| 2026-08-25 | Human / Approved | Blueprint and TASK-051 implementation scope approved |
| 2026-09-01 | Governance checkpoint | `55be8de37ad3143c4222ccdf8b24b815f66a9aee` committed/pushed; checkpoint snapshot records Docs Auditor `CHANGES REQUIRED`, Independent Verifier and Benchmark Reviewer `NO FORMAL VERDICT / STOPPED`; qualification object remains `1bdab634...` | Reviewer-state representation review |
| 2026-09-01 | Post-checkpoint reviewer execution | Historical execution record: Docs Auditor `PASS`; Independent Verifier `CHANGES REQUIRED`; Benchmark Reviewer `NOT RUN` (gated after verifier); checkpoint-time aggregate reviewer gate was `IN PROGRESS / CHANGES REQUIRED` | Reviewer-state representation review |

### Implementation Log

Formal G4/G5 execution has not begun. TASK-051 implementation and bounded
reviewer remediation are committed and pushed at the qualification object
`1bdab634...`; current-controller Quick readiness is preserved. The
documentation checkpoint `55be8de...` records the reviewer state observed when
that checkpoint was created. The subsequent reviewer execution is a separate
historical execution record (Docs Auditor `PASS`, Independent Verifier
`CHANGES REQUIRED`, Benchmark Reviewer `NOT RUN` after the verifier finding),
not a replacement for the checkpoint snapshot. The later bounded remediation,
exact-SHA validation and reviewer evidence were accepted by Human. TASK-051
and its Pre-Campaign Evidence Gate are therefore closed; formal G4/G5
execution remains separately Human-gated and the qualification object remains
unchanged.
