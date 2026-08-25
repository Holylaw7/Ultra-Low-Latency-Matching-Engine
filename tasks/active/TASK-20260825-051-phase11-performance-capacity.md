# Task Plan — TASK-20260825-051

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-051` — Performance SLO and Capacity Foundation |
| Status | `Proposed — Dependency Locked` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Proposed |
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
qualification-only patch on B2. Planned commit:
`test(ga): add performance and capacity qualification`. Full campaign remains
separately Human-gated.

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
| Implementation/Quick | Dependency locked | TASK-050 PASS |
| Pre-campaign review | Locked | reviewers + CI |
| Full campaign | Not Authorized | explicit Human approval |

| Date | Reviewer / status | Record |
| --- | --- | --- |
| 2026-08-25 | Human / Pending | Blueprint Approval required |
| 2026-08-25 | Proposed | No performance/capacity run |

### Implementation Log

No implementation has begun; the preceding dated rows are the initial log.
