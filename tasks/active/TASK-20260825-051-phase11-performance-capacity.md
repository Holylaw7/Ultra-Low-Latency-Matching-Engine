# Task Plan — TASK-20260825-051

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-051` — Performance SLO and Capacity Foundation |
| Status | `In Progress — Bounded Reviewer Remediation Local Complete / Pending Commit` |
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

No threshold, workload, JVM/GC or candidate change to obtain PASS. Revert
qualification-only patch on B2. The TASK-051 implementation is committed and
pushed in the current review history at
`5b4998d8855d4e418b2e897129571c8c16de700d`; Standard CI `33491317454` and
Quick Lane `33491317436` passed with exact-SHA binding. The current target
Quick artifacts are not yet established: existing local Quick manifests bind
historical controller `638d893a3f830c89ffe99914897094968de6bbd4` and remain
historical only. Full G4/G5 campaign execution remains separately Human-gated.

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
| Implementation/Quick | Committed / pushed; final-target canonical Quick pending | Human TASK-051 implementation authorization |
| Reviewer remediation | Local complete / pending commit | Human bounded reviewer remediation Evidence Gate |
| Full campaign | Not Authorized | explicit Human approval |

| Date | Reviewer / status | Record |
| --- | --- | --- |
| 2026-08-25 | Human / Approved | Blueprint and TASK-051 implementation scope approved |
| 2026-09-01 | Human / In progress | Implementation review object `5b4998d`; Standard `33491317454` and Quick `33491317436` PASS; reviewer reconciliation accepted; bounded coverage and governance remediation local validation PASS; final commit and controller-bound Quick evidence pending |

### Implementation Log

Formal G4/G5 execution has not begun. TASK-051 implementation is committed
and pushed, and the bounded reviewer remediation is locally complete but
uncommitted. The pre-campaign Evidence Gate remains open pending the final
remediation commit and target-controller Quick evidence.
