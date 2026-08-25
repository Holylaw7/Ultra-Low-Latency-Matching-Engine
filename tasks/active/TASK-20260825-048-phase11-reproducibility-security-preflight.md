# Task Plan — TASK-20260825-048

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-048` — Reproducibility and Security Preflight |
| Status | `In Progress — Limited Data-Feed Amendment implementation; G9 32856372581 PASS/qualifying/frozen; G11 32863465378 FAIL/B3/preserved; no replacement execution authorized` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Approved |
| Depends On | TASK-047 Evidence Gate PASS after Human Blueprint Approval |
| Next Gate | Data-Feed remediation Evidence Gate; then separate Human G11 Replacement Execution Gate; no automatic retry |

## 2. Goal

Establish G9/G11 before costly execution: two clean reproducible builds,
candidate/source/toolchain/SBOM hashes, runtime dependency/license audit,
full-history secret scan, scanner provenance and release-permission checks.

## 3. Scope and Non-Goals

After approval: qualification/security tooling, evidence publication, and new
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
Human Phase 11 Blueprint Approval and TASK-047 Evidence Gate PASS authorize
this qualification-only implementation. Full G9/G11 campaigns remain subject
to their own evidence and Human gates.

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
| `.github/workflows/ga-qualification.yml` | new read-only reproducibility workflow with approved Microsoft JDK archive provisioning |
| `.github/workflows/ga-security.yml` | new pinned G11 workflow with approved Microsoft JDK archive provisioning and official NVD JSON 2.0 feed validation |
| `qualification/**/ga/security/**` | report normalization and license policy validator |
| qualification tests/resources | tool-manifest/hash/policy fixtures |
| `docs/release/ga-security-toolchain-v1.properties` | consume approved canonical tool options, official feed template and frozen JDK archive digest; no implementation-time choice |
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
| Blueprint | Approved | Human Phase 11 Blueprint Approval |
| Implementation | In Progress | TASK-047 Evidence Gate PASS |
| Preflight | Limited Data-Feed Amendment in progress; separate G11 replacement gate pending | G9 `32856372581` PASS/qualifying/frozen; G11 `32863465378` FAIL/B3/preserved; no G11 rerun |

| Date | Reviewer | Decision / log |
| --- | --- | --- |
| 2026-08-25 | Human Developer | Blueprint approved; TASK-048 authorized after TASK-047 PASS |
| 2026-08-25 | Implementation | Qualification-only policy/evaluator and pinned workflows added; scans not executed locally |
| 2026-08-25 | Evidence checkpoint | `b64a399`; Standard CI `32831047004` PASS; Quick `32831046928` PASS; G9/G11 scan execution pending |
| 2026-08-25 | G9/G11 execution attempt | GitHub `workflow_dispatch` returned HTTP 404 because new workflows are not on default branch; no scan artifact; Human execution-path decision required |
| 2026-08-25 | Approved default-branch installation | master merge `0575c76`; only `ga-qualification.yml` and `ga-security.yml`; CI `32835193395` / Quick `32835193084` PASS |
| 2026-08-25 | G9/G11 run result | `32835408168` / `32835411241` ABORTED/B3: pinned Microsoft OpenJDK `21.0.12` unavailable (21.0.11 newest); no version substitution or retry |
| 2026-08-25 | Controller docs checkpoint | `1ca088f`; Quick `32835630967` PASS; Standard `32835631051` failed at `Verify`; clean local rerun PASS; diagnosis pending |
| 2026-08-25 | Human Limited B3 Amendment | Approved official archive provisioning; `microsoft-jdk-21.0.12-linux-x64.tar.gz`, SHA-256 `f2a84ad31ebeaf3a26252dd86a4a8e1b74aefb6bfc8e55fd20190110d1353c0f`; replacement execution remains separately locked |
| 2026-08-25 | Human Limited B2/B3 Remediation | G9 recursive evidence packager and G11 protected NVD credential binding authorized; replacement failures `32842119210` / `32842122498` preserved |
| 2026-08-25 | B2/B3 Remediation Evidence Gate | Implementation `b44fc4d`; final docs/status `c01977a`; Standard `32845529323` PASS; Quick `32845529342` PASS; replacement G9/G11 execution requires new Human approval |
| 2026-08-25 | Human-authorized replacement execution | G9 `32847427690` workflow PASS but artifact publication incomplete; G11 `32847442506` FAIL/B3 due absent `NVD_API_KEY`; final Evidence Gate CHANGES REQUIRED; no third run |
| 2026-08-25 | Human Limited optional-NVD-key amendment (historical / superseded) | API key was made optional for an API-mode attempt; the official JSON 2.0 Data-Feed Amendment supersedes that path; no replacement execution |
| 2026-08-25 | Human Limited B3 environment-isolation remediation | Authorized to keep the secret out of the anonymous scanner step and invoke it with `env -u NVD_API_KEY`; G9 `32856372581` PASS/frozen; G11 `32856384325` preserved FAIL; no G11 rerun |
| 2026-08-25 | B3 remediation Evidence Gate | PASS — `bdceeb588f163465040b315da2ae1fa4a444bc31`; Standard `32862255686` PASS; Quick `32862256047` PASS; G11 replacement remains separately Human-gated |
| 2026-08-25 | Human Limited Data-Feed Amendment | Approved Dependency-Check `13.0.0` plus official NVD JSON 2.0 feed; API key no longer required; feed metadata/archive integrity and <=24h freshness remain mandatory; no G11 execution |

### Implementation Log

The current limited remediation is qualification-only. It may change the new
G11 workflow feed acquisition/validation path, the corresponding qualification
tests and evidence/status documents, but it may not change the candidate,
production code, root/core POM, Dependency-Check version, policy thresholds or
existing failed artifacts. The official NVD JSON 2.0 feed is validated before
the scanner; Dependency-Check remains the only vulnerability analyzer. The
validator records metadata/archive/content digests, sizes, `lastModifiedDate`
and measured age, while the scanner receives the approved feed template. This
remediation does not execute G9/G11, reclassify prior failures or unlock
TASK-049.
