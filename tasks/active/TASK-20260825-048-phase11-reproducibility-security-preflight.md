# Task Plan — TASK-20260825-048

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-048` — Reproducibility and Security Preflight |
| Status | `Proposed — Dependency Locked` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Proposed |
| Depends On | TASK-047 Evidence Gate PASS after Human Blueprint Approval |
| Next Gate | Human Phase 11 Blueprint Approval |

## 2. Goal

Establish G9/G11 before costly execution: two clean reproducible builds,
candidate/source/toolchain/SBOM hashes, runtime dependency/license audit,
full-history secret scan, scanner provenance and release-permission checks.

## 3. Scope and Non-Goals

After approval: qualification/security tooling, evidence, and new
`.github/workflows/ga-qualification.yml` / `ga-security.yml`. Existing
workflows, poms, candidate dependencies, Maven publication, signing and
production files remain frozen.

## 4. Acceptance Criteria

- [ ] Two independent clean builds produce byte-identical candidate JARs.
- [ ] The controller checkout is non-shallow with exact tag object/peeled SHA;
      both candidate builds use fresh detached worktrees and isolated Maven
      repositories under the normative command/environment contract.
- [ ] Source tree, toolchain, JAR, SBOM and `SHA256SUMS` bind correctly.
- [ ] No Critical/High runtime vulnerability or verified committed secret.
- [ ] Runtime license inventory contains no Human-prohibited license.
- [ ] Scanner version/config/output hashes are recorded; outage is ABORTED.
- [ ] Apache-2.0 and GitHub-binary release choices are explicitly Human-resolved.

## 5. Evidence Gate

Focused provenance tests; clean-build comparison; pinned security scans;
`mvn verify`; Checkstyle; diff/frozen audit; verifier and docs-auditor;
exact-SHA Standard/GA CI. Preserve every scan result.

## 6. Risks / Exception Gate

A vulnerability, secret, license conflict, non-reproducible JAR or need to
change build/dependencies is a blocker, not a docs fix. Candidate repair is B1
and requires rc.2. Roll back qualification tooling only if it is defective.

## 7. Git / Approval

Planned commit: `test(ga): add reproducibility and security preflight`.
Implementation and workflow creation remain unauthorized until Blueprint
Approval and TASK-047 PASS.

## 8. Background / Current Implementation

The candidate builds in CI but has no GA SBOM, pinned vulnerability/license
audit or full-history secret result. Existing workflows use floating major
action tags and are not themselves GA security evidence.

## 9. Requirements, Inputs, Outputs and Non-Goals

Inputs: candidate, TASK-047 contracts and the normative GA Security Toolchain.
Outputs: two clean-build manifests, JAR/SBOM/SHA256SUMS, dependency/license/
secret reports, hashes and G9/G11 results. Non-goals: dependency upgrade,
candidate pom edit, suppression without Human approval, signing/Maven Central.

## 10. Design / Alternatives / Decision

Selected: new read-only Phase-11 workflows invoke exact pinned tools outside
candidate poms. Rejected: Dependabot alerts alone (not reproducible artifact),
floating Actions (tool drift), or modifying poms (candidate mutation). Tool and
policy details are frozen in ADR-0019, not chosen during implementation.

## 11. Planned File Changes

| Path | Change |
| --- | --- |
| `.github/workflows/ga-qualification.yml` | new read-only reproducibility workflow |
| `.github/workflows/ga-security.yml` | new pinned G11 workflow |
| `qualification/**/ga/security/**` | report normalization and license policy validator |
| qualification tests/resources | tool-manifest/hash/policy fixtures |
| `docs/release/ga-security-toolchain-v1.properties` | consume approved canonical tool options; no implementation-time choice |
| `tasks/reports/PHASE-11-task-048.md` | evidence report |

## 12. Detailed Test / Profile Plan

Unit: severity/scope/license/suppression/tool-manifest rules. Integration: the
exact non-shallow checkout, tag-object/peeled verification, two fresh detached
candidate worktrees, isolated Maven repositories, normative clean-build
command, byte comparison, plugin JAR hash verification, SBOM and full-history
scan. Failure: shallow history, shared repository, dirty source diff, stale NVD,
network/tool identity mismatch, missing artifact and finding all produce
FAIL/ABORTED as specified. Benchmark/profile: not applicable.

## 13. Verification Commands

```powershell
mvn -pl qualification -am test "-Dtest=*GaSecurity*,*Reproducib*" "-Dsurefire.failIfNoSpecifiedTests=false"
mvn verify
git diff --check
git diff --name-only "v0.9.0-rc.1^{}" -- src/main pom.xml core/pom.xml
# Exact scanner invocations and hashes are normative in GA-SECURITY-TOOLCHAIN.md.
```

Verify scanner/SBOM/JAR/SHA256SUMS hashes, run verifier/docs-auditor and require
exact-SHA Standard plus both new GA workflow results.

## 14. Rollback, Compatibility, Gates and Log

Rollback removes only new workflows/qualification tooling and retains failed
reports. Candidate/runtime compatibility is unchanged. A runtime finding or
non-reproducible candidate is not rolled back; it blocks GA.

| Stage | Status | Gate |
| --- | --- | --- |
| Blueprint | Proposed | Human approval |
| Implementation | Dependency locked | TASK-047 PASS |
| Preflight | Locked | G9/G11 PASS or blocker review |

| Date | Reviewer | Decision / log |
| --- | --- | --- |
| 2026-08-25 | Human Developer | Pending Blueprint Approval |
| 2026-08-25 | Design | Toolchain frozen; no execution |

### Implementation Log

No implementation has begun; the preceding dated rows are the initial log.
