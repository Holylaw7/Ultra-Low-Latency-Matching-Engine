# Task Plan — TASK-20260825-055

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-055` — GA Documentation and Release Preparation |
| Status | `Proposed — Dependency Locked` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Proposed |
| Depends On | TASK-053 and TASK-054 Evidence Gates PASS |
| Gates | G10 plus G9/G11 release reconciliation |

## 2. Goal

Prepare third-party-operable build/run/configuration/recovery/failure/rollback
documentation, limitation/security reports, release notes/checklist and a draft
`ga-release-manifest-v1`. This Task does not tag or publish.

## 3. Acceptance Criteria

- [ ] Clean-checkout build and launch instructions reproduce the qualified JAR.
- [ ] Supported topology/commands/network/durability/recovery scope is explicit.
- [ ] Recovery, failure diagnosis, safe shutdown and rollback runbooks execute.
- [ ] Performance/capacity/soak reports retain environment and claim boundaries.
- [ ] SBOM, license/security results and SHA256SUMS are linked and hash-valid.
- [ ] Draft manifest distinguishes production SHA and release-source SHA.
- [ ] No Production Ready/GA claim or publication action occurs.

## 4. Planned Paths

`docs/release/**`, `docs/operations/**`, Phase 11 reports/status documents.
Production/build/dependencies and existing raw evidence remain frozen.

## 5. Evidence Gate

Runbook smoke from clean checkout where safe; Markdown/link/hash/stale-state
checks; diff/candidate audit; verifier and docs-auditor; security/license
reconciliation; exact-SHA CI.

## 6. Risks / Approval

Unsupported instructions or license/security conflict are B4/B0, not cosmetic.
If Maven Central or artifact metadata change is required, rc.2 is required.
Planned commit: `docs(release): prepare GA operations and release evidence`.

## 7. Background / Current Implementation

README/Phase reports document an engineering RC but there is no consolidated
third-party GA operations package, security report, release checklist or GA
release-manifest draft.

## 8. Requirements, Inputs, Outputs and Non-Goals

Inputs: accepted G1-G11 evidence, candidate/JAR/SBOM/SHA256SUMS and Human
license/channel choices. Outputs: exact `docs/release/**`, `docs/operations/**`
package and draft manifest. Non-goals: code/build changes, installer, signing,
Maven Central, tag, GitHub Release or GA claim.

## 9. Design / Alternatives / Decision

Selected: versioned Markdown runbooks plus hash-bound release assets. Rejected:
README-only instructions and automatic release workflow, which violates release
authority separation.

## 10. Planned File Changes

| Path | Change |
| --- | --- |
| `docs/release/GA-QUALIFICATION.md` and reports | Gate results and limitations |
| `docs/release/RELEASE-CHECKLIST.md` / notes | controlled Human procedure |
| `docs/operations/*.md` | build/run/recovery/failure/rollback runbooks |
| `tasks/reports/PHASE-11-task-055.md` | G10 evidence |

## 11. Detailed Test / Benchmark Plan

Test clean-checkout commands, configuration examples, hash verification,
recovery/rollback procedure and all local Markdown links. Benchmark is report-
only reconciliation; no new performance run.

## 12. Verification Commands

```powershell
mvn clean verify
java -jar core/target/matching-engine-rc.jar --help
java -jar core/target/matching-engine-rc.jar --validate-config --config <documented-config>
git diff --check
git diff --name-only "v0.9.0-rc.1^{}" -- src/main pom.xml core/pom.xml
# Run repository Markdown/link/hash/stale-evidence validators.
```

## 13. Rollback, Gates, Approval and Log

Docs-only changes may be reverted without candidate impact; misleading docs or
license/security conflict blocks G10/G11/G12.

| Stage | Status | Gate |
| --- | --- | --- |
| Documentation | Dependency locked | TASK-053/054 PASS |
| Validation | Locked | G10 + G9/G11 reconciliation |
| Publication | Not Authorized | separate Human release authority |

| Date | Reviewer / status | Record |
| --- | --- | --- |
| 2026-08-25 | Human / Pending | Blueprint and prior Task gates required |
| 2026-08-25 | Proposed | No release assets/publication |

### Implementation Log

No documentation implementation or release action has begun.
