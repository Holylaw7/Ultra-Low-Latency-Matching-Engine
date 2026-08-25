# Phase 11 — TASK-048 Evidence Report

## Status

`In Progress — B2/B3 remediation Evidence Gate PASS; G9/G11 replacement
execution remains separately Human-gated.`

TASK-048 implements the approved qualification-only reproducibility and
security preflight boundary. It does not qualify the candidate, authorize a
campaign, mutate `v0.9.0-rc.1`, or grant release authority.

## Fixed technical input

| Item | Value |
| --- | --- |
| Candidate tag | `v0.9.0-rc.1` |
| Annotated tag object | `dfd38c08e80aed9035bf1c2d7c8faf8bae99c356` |
| Peeled production SHA | `e2828f563ee41316c062385c0244ac1336731359` |
| Approved toolchain policy | `ga-security-toolchain-v1.properties` (amended) |
| Policy SHA-256 | `6abe66f22ac58b29a45287cf99402045f04b6e2d37fcdb1d144eef215b649397` |
| JDK archive | `microsoft-jdk-21.0.12-linux-x64.tar.gz` / `linux-x64` |
| JDK archive SHA-256 | `f2a84ad31ebeaf3a26252dd86a4a8e1b74aefb6bfc8e55fd20190110d1353c0f` |

## Implementation

- `GaSecurityPolicy` requires the exact approved policy SHA-256, ASCII LF
  bytes, sorted unique keys, exact key set, and no BOM/CR/unknown property.
- `GaSecurityFindingEvaluator` classifies scanner outage as `ABORTED`, runtime
  High/Critical or CVSS >= 7 findings as blockers, verified/unresolved secrets
  as blockers, and runtime licenses outside the approved SPDX set as blockers.
  Test/tool findings remain reported but do not become runtime blockers.
- `ga-qualification.yml` uses the approved pinned checkout/JDK actions, full
  history, exact annotated/peeled candidate identity, two detached candidate
  worktrees, isolated Maven repositories, the normative build command, byte
  comparison and repository/JAR/source evidence hashes.
- `ga-security.yml` uses the approved pinned actions and candidate identity,
  resolves and verifies pinned scanner entry JARs, invokes the normative
  CycloneDX/Dependency-Check/license/Gitleaks commands, and records output
  hashes. It is `workflow_dispatch` only and has `contents: read` permission.
- The approved B3 amendment replaces only JDK provisioning: both new workflows
  verify the Microsoft 21.0.12 archive sidecar and archive SHA-256 before a
  fresh extraction, then record Java/Maven runtime identity. Candidate,
  production and existing workflows remain outside this change.
- The Human-approved limited B2/B3 remediation repairs only the G9 final
  evidence packager and G11 protected NVD credential binding. G9 now performs
  recursive regular-file enumeration with normalized deterministic paths,
  fail-closed filesystem checks and complete inventory validation. G11 binds
  `NVD_API_KEY` only to the Dependency-Check step and records non-secret NVD
  update/database provenance. Reproducibility, scanner, threshold, freshness,
  policy and candidate inputs remain unchanged.

Existing workflows, POMs, dependencies, candidate source and production paths
were not modified. No scanner, full-history build or campaign was executed on
the Windows development host; therefore no G9/G11 PASS is claimed here.

The approved default-branch workflow installation was deliberately narrow: the
master merge `0575c76` contains only the two new Phase 11 workflow files. Their
working-byte SHA-256 values were verified against the controller branch before
installation: `ga-qualification.yml` =
`43ee036c4ccafbe004869ee4019e8c33b9e3941dc01d509618266a3801c1780d` and
`ga-security.yml` =
`226b0a128d9cd4a89dc2778c5bd2600d0fd4a992ec90f2b62cb9e478c8905778`.
Master CI `32835193395` and Quick Lane `32835193084` both passed. No candidate
tag, production source, existing workflow, dependency or runtime input changed.

## Local verification

| Check | Result |
| --- | --- |
| Focused security/reproducibility tests | PASS — 5 tests |
| `mvn -pl qualification -am test "-Dtest=*GaSecurity*,*Reproducib*"` | PASS |
| Checkstyle during focused build | 0 violations |
| G9/G11 scanner execution | Not run locally; replacement execution not authorized |
| Full `mvn verify` | PASS — 225 core + 61 qualification tests; 2 expected skips |
| Workflow YAML parse | PASS — both new workflows parse successfully |
| Workflow Bash syntax | PASS — all workflow `run` blocks parse with Bash 5.3 |
| G9 packager simulation | PASS — nested evidence directories inventory and verify atomically |
| Secret-leak scan | PASS — remediation diff contains no credential material |
| `git diff --check` | PASS |
| Frozen production/existing-workflow audit | PASS — no production, POM or existing workflow change |
| B2/B3 remediation static verification | PASS — YAML/Bash validation, G9 packager simulation, secret-leak scan and docs/evidence audit |
| Standard exact-SHA CI | [32831047004](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32831047004) — PASS |
| Qualification Quick Lane | [32831046928](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32831046928) — PASS |
| Final remediation Standard CI | [32845529323](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32845529323) — PASS |
| Final remediation Quick Lane | [32845529342](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32845529342) — PASS |
| Controller docs checkpoint | [32835631051](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32835631051) — FAIL at `Verify`; Quick [32835630967](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32835630967) — PASS |
| Local rerun after controller checkpoint | `mvn --batch-mode --no-transfer-progress verify` — PASS; 225 core + 61 qualification, 2 expected skips, Checkstyle 0 |

## Evidence and claim boundary

This report records implementation evidence only. Scanner outage, toolchain
identity mismatch, non-reproducible JAR, vulnerability, verified secret or
prohibited runtime license remains `ABORTED`/blocker according to ADR-0019;
none may be converted to PASS by omission or tool substitution.

The B2/B3 remediation Evidence Gate is PASS, but TASK-049 remains
dependency-locked until G9/G11 replacement execution produces accepted gate
evidence. Full campaigns, `v1.0.0`, GitHub Release and GA remain unauthorized.

## G9/G11 execution attempt

On 2026-08-25, dispatch of both new `workflow_dispatch` workflows was
attempted against the approved feature-branch ref. GitHub returned HTTP 404
because the workflow files are not present on the repository default branch;
specifying the feature ref cannot dispatch a workflow that is not installed on
that branch. No G9/G11 run or artifact was created. The Windows host has no
equivalent pinned Ubuntu runner (`act`) available. This is an execution
precondition blocker, not a G9/G11 PASS or FAIL result.

The workflow-only default-branch installation was subsequently approved and
performed, but both actual runs then terminated at the pinned JDK setup step.
The controller ref was `docs/phase11-ga-qualification-blueprint` at
`5494263` when dispatched:

| Run | Workflow | Result |
| --- | --- | --- |
| `32835408168` | GA Reproducibility Qualification | `ABORTED / B3` |
| `32835411241` | GA Security Qualification | `ABORTED / B3` |

`actions/setup-java` reported that Microsoft distribution `21.0.12` was not
available; the catalog exposed `21.0.11` as the newest Java 21 version. No
scanner, build, or evidence artifact was produced. The approved workflow and
toolchain were not changed to substitute `21.0.11`, and no automatic retry was
performed.

Human then approved a Limited B3 Environment / Security Toolchain Amendment.
The exact Microsoft archive identity is now frozen as
`microsoft-jdk-21.0.12-linux-x64.tar.gz` with archive SHA-256
`f2a84ad31ebeaf3a26252dd86a4a8e1b74aefb6bfc8e55fd20190110d1353c0f`; the
canonical properties digest is amended to
`6abe66f22ac58b29a45287cf99402045f04b6e2d37fcdb1d144eef215b649397`. The
workflow change remains qualification-only. Replacement G9/G11 execution is
still a separate Human gate and has not been authorized.

The later controller documentation checkpoint `1ca088f` had Quick Lane PASS,
but Standard CI `32835631051` failed in the generic `Verify` step. A clean local
`mvn verify` rerun passed; the public check annotation exposes no diagnostic
beyond exit code 1, so this remains an unresolved CI observation rather than a
qualification PASS. No retry, production change or toolchain substitution is
claimed here.

The first Human-authorized replacement execution was then performed exactly
once for each workflow. G9 run `32842119210` completed both detached builds,
225 tests per build and identical JAR SHA-256 values, but failed its final
evidence package because the old glob passed `build-a/` and `build-b/`
directories to `sha256sum`; it remains immutable `FAIL / B2 / NON-QUALIFYING`.
G11 run `32842122498` completed JDK, candidate, policy, scanner-entry and SBOM
steps, but Dependency-Check received no valid NVD API key/data and failed with
`Invalid API Key` / `NoDataException`; it remains immutable
`FAIL / B3 / NON-QUALIFYING`. No third run was started.

The Human-approved limited remediation is implemented at `b44fc4d`. The final
evidence/status synchronization is `c01977a`; Standard CI `32845529323` and
Qualification Quick Lane `32845529342` both pass. This closes the B2/B3
remediation Evidence Gate only; it does not mark G9 or G11 PASS and does not
authorize another execution.

## Completion log

| Date | Stage | Result |
| --- | --- | --- |
| 2026-08-25 | Human Blueprint inheritance | TASK-048 authorized after TASK-047 PASS |
| 2026-08-25 | Implementation checkpoint | Qualification policy/evaluator, pinned workflows and tests added; commit `b64a399` |
| 2026-08-25 | Standard/Quick CI | `32831047004` / `32831046928` PASS; G9/G11 workflows remain manual preflight evidence |
| 2026-08-25 | Approved workflow installation | master merge `0575c76`; CI `32835193395` / Quick `32835193084` PASS; only the two new workflow files installed |
| 2026-08-25 | G9/G11 dispatch attempt | HTTP 404: workflows are not installed on the default branch; no run/artifact created |
| 2026-08-25 | G9/G11 execution | Runs `32835408168` / `32835411241` ABORTED/B3 at pinned JDK setup; no artifacts; no retry |
| 2026-08-25 | Controller docs checkpoint | `1ca088f`; Quick `32835630967` PASS; Standard `32835631051` failed at `Verify`; clean local rerun PASS |
| 2026-08-25 | Initial B2/B3 remediation checkpoint | Commit `b44fc4d`; Standard `32845289320` PASS; Quick `32845289257` PASS; G9/G11 replacement execution remains separately locked |
| 2026-08-25 | Limited B3 toolchain amendment | Official Microsoft archive SHA-256 frozen; amended policy digest `6abe66f22ac58b29a45287cf99402045f04b6e2d37fcdb1d144eef215b649397`; replacement run remains separately locked |
| 2026-08-25 | Human Limited B2/B3 remediation | G9 packager and G11 protected NVD binding repaired; replacement runs `32842119210` / `32842122498` remain preserved failures; remediation Evidence Gate PASS at `b44fc4d` (`32845289320` / `32845289257`) |
| 2026-08-25 | Final remediation evidence/status synchronization | `c01977a`; Standard `32845529323` PASS; Quick `32845529342` PASS; G9/G11 replacement execution remains separately Human-gated |
