# Task Plan — TASK-20260825-048

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-048` — Reproducibility and Security Preflight |
| Status | `In Progress — fresh G11 32947367541 FAIL/non-qualifying/preserved after two read-only-confirmed documentation false positives; exact Gitleaks disposition amendment Evidence Gate PASS at 7be2b61 (Standard 32951233073, Quick 32951233014); G9 32856372581 PASS/qualifying/frozen; no fresh G11 authorized` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Approved |
| Depends On | TASK-047 Evidence Gate PASS after Human Blueprint Approval |
| Next Gate | Human fresh G11 Execution Gate; no automatic execution |

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
- [ ] Full-history and candidate-bound secret scans pass with no unresolved or
      verified committed secret; demonstrable non-secret findings require an
      exact approved disposition manifest match.
- [ ] CycloneDX SBOM and independent runtime dependency inventory are non-empty
      and exactly consistent.
- [ ] Runtime license inventory contains no Human-prohibited license.
- [ ] Root-reactor license report is non-empty, parseable, reconciled with the
      runtime inventory, and published under the approved evidence path.
- [ ] Pinned generator/scanner identities and all output hashes are recorded;
      missing/malformed/inconsistent evidence fails closed.
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

The candidate builds in CI but initially had no GA SBOM, independent runtime
dependency/license inventory or full-history secret result. Historical
NVD-backed G11 executions remain preserved non-qualifying evidence. The
Human-approved amendment explicitly places current external CVE/NVD evaluation
outside the portfolio-release boundary rather than converting those failures
to PASS.

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
| `.github/workflows/ga-security.yml` | pinned `OFFLINE_SUPPLY_CHAIN_SECURITY_V1` workflow |
| `qualification/**/ga/security/**` | v2 policy, inventory consistency and fail-closed evidence validators |
| qualification tests/resources | tool-manifest/hash/policy fixtures |
| `docs/release/ga-security-toolchain-v1.properties` | preserved unchanged for historical NVD-backed evidence |
| `docs/release/ga-security-toolchain-v2.properties` | new canonical offline G11 policy |
| `tasks/reports/PHASE-11-task-048.md` | evidence report |

## 12. Detailed Test / Profile Plan

Unit: severity/scope/license/suppression/tool-manifest rules. Integration: the
exact non-shallow checkout, tag-object/peeled verification, two fresh detached
candidate worktrees, isolated Maven repositories, normative clean-build
command, byte comparison, plugin JAR hash verification, SBOM/dependency/license
consistency and both full-history/candidate-bound secret scans. Failure:
shallow history, shared repository, dirty source diff, tool identity mismatch,
missing/malformed/inconsistent artifact or secret finding all fail closed.
Benchmark/profile: not applicable.

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
| Preflight | Offline supply-chain policy and exact Gitleaks disposition amendment Evidence Gate PASS at `7be2b61`; fresh G11 separately gated | G9 `32856372581` PASS/qualifying/frozen; G11 `32947367541` FAIL/non-qualifying/preserved; no additional G11 execution |

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
| 2026-08-26 | Human G11 Qualification Policy Amendment | Approved `OFFLINE_SUPPLY_CHAIN_SECURITY_V1`; NVD/Dependency-Check removed from normative portfolio G11; historical failures preserved; fresh G11 not authorized |
| 2026-08-26 | Human-authorized G11 execution | Offline G11 `32925783003` FAIL/B2/preserved before SBOM due `mvn -f core/pom.xml` Checkstyle path resolution; artifact `9591451565` / SHA-256 `2511c2276f40f83868db27591a2eb7afc644c4eeb7621a1cda8aa17af3cb40cf`; candidate defect not observed; no retry |
| 2026-08-26 | Human Limited G11 B2 Remediation | Authorized qualification-workflow-only switch to repository-root `-pl core -am` / `-pl core` Maven selectors; v2 policy hash recomputation; no candidate/POM/production change and no new G11 execution |
| 2026-08-26 | G11 B2 Remediation Evidence Gate | PASS — implementation `e1464ed`; Standard CI `32927818204`; Quick Lane `32927818172`; root-selector smoke, focused/full tests, YAML/Bash, policy hash and frozen-path audits passed; fresh G11 remains separately Human-gated |
| 2026-08-26 | Human Limited G11 license-report B2 Remediation | AUTHORIZED / IN PROGRESS | Preserve plugin `2.7.1`; use root `-pl core -am`; require `target/reports/aggregate-third-party-report.html`; update v2 policy/validator/tests; fresh G11 not authorized |
| 2026-08-26 | Human-authorized offline G11 execution | FAIL / B2 / PRESERVED | `32929258318`; artifact `9592633595`; digest `4382fcb02c8a97c42d66e7617e9276e470f772def42a02a9c1067cebd5cb7c4b`; license report goal skipped before required artifact publication; no candidate defect or retry |
| 2026-08-26 | License-report B2 remediation Evidence Gate | PASS | `30c89c4`; Standard `32932454011`; Quick `32932454009`; HTML parseability, report/runtime-coordinate reconciliation, Maven module-annotation handling and validation-sidecar inventory checks added; no fresh G11 execution |
| 2026-08-26 | Human-authorized fresh G11 execution | FAIL / B2 / PRESERVED | `32943456313`; artifact `9597396741`; digest `bc3ad708418d0194c05e695d4990daa1e9319480b6787493e4968f132fc17689`; license-report validation referenced undeclared step-local `REPO`; no candidate defect or retry |
| 2026-08-26 | Sol High B2 cross-step scope review | CONFIRMED | Only the license validation step omitted `REPO`; limited workflow-only remediation approved; policy/candidate/POM unchanged |
| 2026-08-26 | G11 shell-scope B2 remediation Evidence Gate | PASS | `eced533`; Standard `32945056542`; Quick `32945056508`; step-local variable audit, YAML/Bash, focused/full regression and frozen-boundary checks passed; fresh G11 remains Human-gated |
| 2026-08-26 | TASK-048/Phase 11 status synchronization | PASS | `e51db47`; Standard `32945333516`; Quick `32945333468`; latest failure/remediation state synchronized; fresh G11 remains Human-gated |
| 2026-08-26 | Final B2 remediation audit checkpoint | PASS | `688d955`; Standard `32946223271`; Quick `32946223268`; docs/evidence state synchronized; fresh G11 remains Human-gated |
| 2026-08-26 | Human false-positive disposition amendment | APPROVED / IN PROGRESS | Run `32947367541` preserved FAIL/non-qualifying after two documentation findings; exact fingerprint/path/commit-or-candidate/blob/rule/line disposition manifest and mandatory candidate-bound evaluation authorized; no fresh G11 execution |

### Implementation Log

The current limited remediation is qualification/docs-only. It replaces the
normative G11 path with a mandatory offline gate covering candidate/JAR
identity, CycloneDX SBOM, independent runtime dependency inventory, license
disposition, full-history and candidate-bound secret scans, tool provenance
and strict immutable artifact publication. The v1 NVD policy and every failed
run remain preserved. This remediation does not execute G9/G11, reclassify
prior failures, claim CVE/NVD cleanliness or unlock TASK-049.

The subsequent limited B2 remediation addresses only the workflow shell scope:
the license-report validation step now declares its own
`REPO="$RUNNER_TEMP/ga-security-cache/m2"`, matching the resolver and build
steps without changing the Maven command or policy identity. A static audit
checks every GA security `run:` block for undeclared shell variables. Commit
`eced533` passed Standard CI `32945056542` and Quick Lane `32945056508`; the
fresh G11 execution remains separately Human-gated.

The current limited amendment adds the exact manifest
`docs/release/ga-gitleaks-false-positive-dispositions-v1.properties` and a
qualification-only evaluator. The workflow keeps both raw Gitleaks reports,
captures each scanner exit code without short-circuiting the candidate-bound
scan, rejects unknown or changed findings, and publishes the manifest plus a
safe evaluation sidecar in the immutable inventory. This mechanism is not a
broad suppression and does not reinterpret run `32947367541`; the amendment
Evidence Gate must pass before a separate Human fresh G11 execution can be
considered.
