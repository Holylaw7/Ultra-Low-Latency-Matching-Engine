# Phase 11 — TASK-048 Evidence Report

## Status

`In Progress — Human-approved optional-NVD-key amendment in progress; no new
G9/G11 execution authorized; final G9/G11 evidence review remains CHANGES
REQUIRED.`

TASK-048 implements the approved qualification-only reproducibility and
security preflight boundary. It does not qualify the candidate, authorize a
campaign, mutate `v0.9.0-rc.1`, or grant release authority.

## Fixed technical input

| Item | Value |
| --- | --- |
| Candidate tag | `v0.9.0-rc.1` |
| Annotated tag object | `dfd38c08e80aed9035bf1c2d7c8faf8bae99c356` |
| Peeled production SHA | `e2828f563ee41316c062385c0244ac1336731359` |
| Approved toolchain policy | `ga-security-toolchain-v1.properties` (optional-NVD-key amendment in progress) |
| Policy SHA-256 | Updated by this amendment Evidence Gate; no G9/G11 execution yet |
| JDK archive | `microsoft-jdk-21.0.12-linux-x64.tar.gz` / `linux-x64` |
| JDK archive SHA-256 | `f2a84ad31ebeaf3a26252dd86a4a8e1b74aefb6bfc8e55fd20190110d1353c0f` |
| Artifact publication action | `actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` (v7.0.1) |
| G9/G11 artifact contract | One gate-specific artifact; `if-no-files-found=error`; 14-day retention; compression 6; overwrite false; hidden files excluded; `always()` publication |

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
  fail-closed filesystem checks and complete inventory validation. The current
  optional-NVD-key amendment makes `NVD_API_KEY` optional: authenticated mode
  binds it only to Dependency-Check with a 3500 ms delay; anonymous mode omits
  the key property and uses an 8000 ms delay. Both modes still require a real
  Dependency-Check 13.0.0 scan, <=24h usable data, reports and provenance.
  Reproducibility, scanner, threshold, freshness, policy and candidate inputs
  remain unchanged.

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
| Focused security/reproducibility tests | PASS — 6 tests |
| `mvn -pl qualification -am test "-Dtest=*GaSecurity*,*Reproducib*"` | PASS |
| Checkstyle during focused build | 0 violations |
| G9/G11 scanner execution | One Human-authorized replacement run each; G9 workflow PASS but no persisted artifact, G11 FAIL/B3 at missing NVD secret |
| Full `mvn verify` | PASS — 225 core + 62 qualification tests; 2 expected skips |
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

The first B2/B3 remediation Evidence Gate at `b44fc4d` was PASS, but the
current publication/secret remediation Evidence Gate remains pending. TASK-049
is dependency-locked because replacement evidence is not yet accepted. Full
campaigns, `v1.0.0`, GitHub Release and GA remain unauthorized.

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
canonical properties digest at that earlier checkpoint was
`6abe66f22ac58b29a45287cf99402045f04b6e2d37fcdb1d144eef215b649397`. The
later optional-NVD-key amendment has a new canonical digest recorded below.
The workflow change remains qualification-only. Replacement G9/G11 execution is
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

The second limited remediation added the pinned artifact publication contract
to both GA workflows. Its interim G11 binding used the repository-level
`NVD_API_KEY`; the secret value is never handled by the agent. The later
optional-NVD-key amendment supersedes the mandatory-presence precondition while
retaining protected authenticated mode when a key is present. No replacement
execution is authorized by either remediation.

The current Human-approved B2/B3 remediation is limited to the publication and
credential-mode contracts. G9 uses the full immutable `actions/upload-artifact` commit
`043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` with one deterministic artifact,
`if-no-files-found=error`, 14-day retention, compression level 6,
`overwrite=false`, hidden files excluded and `if: always()`. Each workflow
records the action's artifact ID, URL and SHA-256 digest in the run summary.
G11 remains blocked until the repository-level secret is securely provisioned;
the value is not present in this repository or handled by the agent. This
remediation does not authorize a replacement run.

## Human-authorized replacement execution

Exactly one fresh execution was authorized for each gate. Both runs used the
approved workflow bytes (unchanged from `c01977a`) and candidate
`v0.9.0-rc.1`; no old run artifacts were reused and no third run was started.

- G9 run `32847427690` concluded `success`: the approved JDK, candidate
  identity, two detached builds and reproducibility workflow completed. The
  run exposed zero persisted GitHub Actions artifacts, so immutable artifact
  and `SHA256SUMS` publication could not be independently validated. Its
  workflow result is therefore recorded as **technical PASS / B2 evidence
  incomplete / non-qualifying**, not as an accepted G9 PASS.
- G11 run `32847442506` concluded `failure` at the protected NVD credential
  presence check. JDK, candidate identity, scanner resolution and SBOM steps
  passed; `NVD_API_KEY` was absent, so Dependency-Check did not run. This is
  preserved as **FAIL / B3 / NON-QUALIFYING**.

The two earlier failures `32842119210` and `32842122498` remain preserved
unchanged. No automatic retry or replacement run is authorized.

## Human-approved optional-NVD-key amendment

The mandatory repository-secret precondition is removed without removing or
weakening G11. `NVD_API_KEY` is optional. A non-empty secret selects
`AUTHENTICATED` mode, records only non-secret credential metadata and passes
`nvdApiKeyEnvironmentVariable=NVD_API_KEY` with the frozen 3500 ms request
delay. An absent or empty secret selects `ANONYMOUS` mode, omits the API-key
property entirely and uses the frozen 8000 ms request delay. The scan must
still complete with Dependency-Check 13.0.0, usable NVD data no older than 24
hours, JSON/SARIF reports, provenance, CVSS 7.0 and all existing policy gates.

This is qualification-only remediation. It does not modify the candidate,
production code, POM/dependency graph, scanner, thresholds or prior evidence.
The G11 workflow also emits a non-secret configuration identity digest whose
canonical input includes the selected mode and approved delay, never the
credential value or any derived form.
No G9/G11 execution is performed here; replacement execution remains a
separate Human gate and TASK-049 remains locked.

The amended canonical policy bytes currently hash to
`e042d191c63ee6f397d6756761f0fc969c3e97a9f5e9357c1c769f43aa2bdff5`; this
hash is the input for the remediation Evidence Gate and does not qualify a
G9/G11 run.

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
| 2026-08-25 | Human-authorized replacement execution | G9 `32847427690` technical workflow PASS but zero persisted artifacts / B2 evidence incomplete; G11 `32847442506` FAIL/B3 because protected `NVD_API_KEY` was absent; no third run |
| 2026-08-25 | Human Limited B2/B3 remediation (historical / superseded) | Authorized G9 publication contract and repository-level `NVD_API_KEY` provisioning; later superseded by the optional-key amendment; replacement execution was not authorized |
| 2026-08-25 | Human Limited optional-NVD-key amendment (current) | `NVD_API_KEY` optional; authenticated/anonymous mode and 3500/8000 ms delays frozen; Dependency-Check/freshness/policy unchanged; no G9/G11 execution |
