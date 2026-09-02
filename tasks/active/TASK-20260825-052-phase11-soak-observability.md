# Task Plan — TASK-20260825-052

## 1. Metadata

| Field           | Value                                                                                                    |
| --------------- | -------------------------------------------------------------------------------------------------------- |
| Task ID / Title | `TASK-20260825-052` — Soak and Observability Foundation                                                  |
| Status          | `In Progress — Remediated Implementation Committed/Pushed; Controller Evidence PASS; Governance Checkpoint Established; Reviewer Validation Pending` |
| Phase / ADR     | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint       | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Human scope reviewed |
| Depends On      | TASK-051 pre-campaign Evidence Gate PASS — satisfied                                                    |
| Gates           | G6, G8 pre-campaign                                                                                      |
| Manual Gate     | Stop before two-hour soak                                                                                |

## 2. Goal

Implement qualification-only staged soak and observability runners, immutable
manifests, chronological resource guards, JFR/GC/counter evidence and Quick
lifecycle validation. Do not execute the two-hour or six-hour runs.

## 3. Frozen Campaign

`MEMORY_STEADY_STATE_V1`, seed 20260823, packaged public path, 200 commands/s.
Stage A: 2h and >=1,440,000 accepted. Stage B: 6h and >=4,320,000 accepted. Stage B
requires a new Human approval after Stage A review.

## 4. Acceptance Criteria

- [ ] Run configuration is immutable once started.
- [ ] Checkpoint/replay/transcript/probe and resource guards are independent.
- [ ] Chronological post-GC, thread/file/temp and first/final P99 drift guards
      are deterministic; drift threshold is <=20%.
- [ ] Management/JFR/GC/exit evidence never reads mutable engine state.
- [ ] Quick/smoke evidence cannot substitute for a soak.
- [ ] Pre-soak reviewers and exact-SHA CI PASS, then stop.

## 5. Evidence Gate

Focused guard/manifest/abort tests, Quick lifecycle run, full regression,
Checkstyle, candidate/frozen audit, verifier, mandatory benchmark-reviewer,
docs-auditor and exact-SHA CI.

## Current Governance State

```text
TASK-051 dependency:
SATISFIED

Human scope:
APPROVED

TASK-052:
IN PROGRESS

Implementation:
QUALIFICATION-ONLY / COMMITTED / PUSHED

Qualification controller:
7b00fdf571fb8fa4918b7138210693b9e79f346c

Remediated Implementation Evidence Review:
PASS

Technical local Evidence:
PASS

Controller-bound Quick:
PASS / QUICK_READINESS_ONLY

Controller exact-SHA CI:
Standard PASS (33585928074); Quick Lane PASS (33585928038)

Governance checkpoint:
ESTABLISHED

Reviewer validation:
PENDING

Pre-Soak Evidence Gate:
NOT PASSED

TASK-053:
NOT AUTHORIZED

Formal Stage A/B:
NOT AUTHORIZED

TASK-054:
NOT AUTHORIZED
```

## 6. Exception / Rollback / Approval

No System.gc(), workload pressure, threshold change or automatic extension to
force PASS. B2 allows qualification-only remediation; candidate defects are
B1. The remediated qualification implementation is committed and pushed at
`7b00fdf571fb8fa4918b7138210693b9e79f346c`; the post-commit governance
synchronization is a separate object and does not replace that controller.

## 7. Background / Current Implementation

Phase 9/10 provide one-hour campaigns and management/JFR evidence, not the
pre-declared two-/six-hour GA stability Gate. TASK-052's staged GA soak and
G8 foundation is committed/pushed at the remediated controller above and has
passed its qualification test, focused-matrix, controller-bound Quick and
exact-controller CI validation. The pre-soak Evidence Gate is not passed, and
no formal soak has been executed.

## 8. Requirements, Inputs, Outputs and Non-Goals

Inputs: candidate, fixed rate/profile/seed, resource guards and schemas.
Outputs: soak/observability runner, Quick evidence and immutable pre-soak
manifest. Non-goals: actual 2h/6h run, System.gc(), load/threshold tuning,
production instrumentation or memory-leak-free claim.

## 9. Design / Alternatives / Decision

Selected: bounded-state workload with public path, chronological natural-GC
guard, first/final latency windows and staged Human gates. Rejected: raw heap
under growing business state, fixed-GC-count stop and one ungated eight-hour
run.

## 10. Planned File Changes

| Path                                   | Change                                 |
| -------------------------------------- | -------------------------------------- |
| `qualification/**/ga/soak/**`          | staged runner, resource/latency guards |
| `qualification/**/ga/observability/**` | management/JFR/GC evidence evaluator   |
| qualification tests/resources          | clock/window/abort/identity fixtures   |
| `tasks/reports/PHASE-11-task-052.md`   | pre-soak report                        |

## 11. Detailed Test / Profile Plan

Unit: duration/count conjunction, chronological samples, P99 drift, resource
baseline and ABORTED. Integration Quick: public process/recovery/readiness/
shutdown plus JFR/GC parsing. Profile outputs include heap/RSS/allocation/GC/
CPU/threads/files/latency without production seams.

## 12. Verification Commands

```powershell
mvn -pl qualification -am test "-Dtest=*GaSoak*,*GaObservability*" "-Dsurefire.failIfNoSpecifiedTests=false"
java -jar qualification/target/matching-engine-qualification.jar ga-soak --lane quick
mvn verify
git diff --check
git diff --name-only "v0.9.0-rc.1^{}" -- src/main pom.xml core/pom.xml
```

Mandatory benchmark-reviewer, verifier/docs-auditor and exact-SHA CI.

## 13. Rollback, Gates, Approval and Log

Rollback B2 runner only and retain evidence. Production/resource defect blocks
GA. Blueprint approval does not authorize either soak.

### Risks

Harness retention, artificial GC or cross-window sample mixing could hide drift;
bounded workload and chronological per-run guards prevent this.

| Stage                | Status            | Gate                           |
| -------------------- | ----------------- | ------------------------------ |
| Implementation/Quick | Remediated implementation committed/pushed; controller-bound Quick PASS / QUICK_READINESS_ONLY; exact-controller CI PASS | TASK-051 PASS; Human scope approved |
| 2h soak              | Not Authorized    | explicit Human approval        |
| 6h soak              | Not Authorized    | 2h review + new Human approval |

| Date       | Reviewer / status | Record                      |
| ---------- | ----------------- | --------------------------- |
| 2026-08-25 | Human / Pending   | Blueprint Approval required |
| 2026-08-25 | Proposed          | No soak execution           |

### Implementation Log

The 2026-08-25 rows above are historical planning checkpoints. Current state:
Human scope approved; the remediated qualification-only implementation is
committed and pushed at `7b00fdf571fb8fa4918b7138210693b9e79f346c`; the
Implementation Evidence Review, controller-bound Quick and exact-controller
CI are PASS; the TASK-052 governance checkpoint is established and reviewer
validation is pending; the pre-soak Evidence Gate and both formal soak
campaigns remain unauthorized.
