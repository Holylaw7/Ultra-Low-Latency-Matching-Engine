# Phase 11 — TASK-048 Evidence Report

## Status

`In Progress — G9 32856372581 PASS/qualifying/frozen; fresh G11
32955619875 PASS/qualifying/frozen under OFFLINE_SUPPLY_CHAIN_SECURITY_V1;
prior G11 failures remain preserved; TASK-048 final evidence review pending;
no additional G11 execution authorized.`

TASK-048 implements the approved qualification-only reproducibility and
security preflight boundary. It does not qualify the candidate, authorize a
campaign, mutate `v0.9.0-rc.1`, or grant release authority.

## Fixed technical input

| Item | Value |
| --- | --- |
| Candidate tag | `v0.9.0-rc.1` |
| Annotated tag object | `dfd38c08e80aed9035bf1c2d7c8faf8bae99c356` |
| Peeled production SHA | `e2828f563ee41316c062385c0244ac1336731359` |
| Historical toolchain policy | `ga-security-toolchain-v1.properties` / NVD-backed runs only / preserved unchanged |
| Current G11 toolchain policy | `ga-security-toolchain-v2.properties` / `OFFLINE_SUPPLY_CHAIN_SECURITY_V1` |
| Current policy SHA-256 | `2b9ee7de9aee3e153d76ded1118434e8bc93807b2d329442e4593839b8e4b87f`; G11 disposition manifest `0854c43f9138d8073f640fe1e37f97c7d482f01bcbe3e8280534ee3cbc70466c`; candidate path-contract remediation `f6db140` Evidence Gate PASS (Standard `32954953854`, Quick `32954953801`); fresh G11 `32955619875` PASS/qualifying/frozen; no additional G11 execution authorized |
| JDK archive | `microsoft-jdk-21.0.12-linux-x64.tar.gz` / `linux-x64` |
| JDK archive SHA-256 | `f2a84ad31ebeaf3a26252dd86a4a8e1b74aefb6bfc8e55fd20190110d1353c0f` |
| Artifact publication action | `actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` (v7.0.1) |
| G9/G11 artifact contract | One gate-specific artifact; `if-no-files-found=error`; 14-day retention; compression 6; overwrite false; hidden files excluded; `always()` publication |

## Implementation

- `GaSecurityPolicy` preserves the exact historical v1/NVD contract.
  `GaOfflineSupplyChainPolicy` separately requires the approved v2 SHA-256,
  ASCII/LF bytes, sorted unique exact keys, no BOM/CR/unknown property and the
  exact G11/gate/evidence identities.
- `GaSecurityFindingEvaluator` classifies scanner outage as `ABORTED`, runtime
  High/Critical or CVSS >= 7 findings as blockers, verified/unresolved secrets
  as blockers, and runtime licenses outside the approved SPDX set as blockers.
  Test/tool findings remain reported but do not become runtime blockers.
- `ga-qualification.yml` uses the approved pinned checkout/JDK actions, full
  history, exact annotated/peeled candidate identity, two detached candidate
  worktrees, isolated Maven repositories, the normative build command, byte
  comparison and repository/JAR/source evidence hashes.
- `ga-security.yml` uses the approved pinned actions and candidate identity,
  rebuilds and hashes the application JAR, generates a CycloneDX SBOM and an
  independent runtime dependency list, enforces exact coordinate/license
  consistency, generates the root-reactor license report, runs pinned
  full-history and candidate-bound Gitleaks, evaluates both reports against the
  exact v2 disposition manifest, then strictly hashes and publishes the offline
  evidence. It does not invoke Dependency-Check or NVD.
- The approved B3 amendment replaces only JDK provisioning: both new workflows
  verify the Microsoft 21.0.12 archive sidecar and archive SHA-256 before a
  fresh extraction, then record Java/Maven runtime identity. Candidate,
  production and existing workflows remain outside this change.
- The Human-approved policy amendment does not rewrite any NVD-backed failure.
  It creates a new v2 contract and fresh gate-specific evidence schema while
  retaining the top-level GA schemas. Current CVE/NVD database evaluation is
  explicitly outside the portfolio-release boundary; no vulnerability-clean
  or Dependency-Check-passed claim is permitted.

No production source/test, POM, dependency, candidate source, G9 workflow or
runtime path was modified. Only the new GA G11 workflow changed under the
approved qualification amendment. The Windows development host did not execute
G11; GitHub G9/G11 results and immutable artifacts are recorded below.

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
| Focused security/policy tests | PASS — Java policy/security tests plus 3 Python disposition tests |
| `mvn -pl qualification -am test "-Dtest=*GaSecurity*,*Reproducib*"` | PASS |
| Root-selector qualification build | PASS — `mvn -B -ntp -pl core -am package -DskipTests`; root-relative Checkstyle 0; `core/target/matching-engine-rc.jar` present |
| v2 policy digest / command contract | PASS — current SHA-256 `2b9ee7de9aee3e153d76ded1118434e8bc93807b2d329442e4593839b8e4b87f`; disposition manifest SHA-256 `0854c43f9138d8073f640fe1e37f97c7d482f01bcbe3e8280534ee3cbc70466c`; license goal uses root `-pl core -am`, report path `target/reports/aggregate-third-party-report.html`, and candidate Gitleaks `dir .` path contract |
| Checkstyle during focused build | 0 violations |
| G9/G11 execution | G9 `32856372581` PASS/qualifying/frozen; offline G11 `32925783003` FAIL/B2/preserved before SBOM; B2 remediation `e1464ed` / CI `32927818204` / Quick `32927818172` PASS; fresh G11 `32955619875` PASS/qualifying/frozen; prior failures preserved; no additional G11 execution authorized |
| Full `mvn verify` | PASS — 225 core + 72 qualification tests; 2 expected skips |
| Workflow YAML parse | PASS — both new workflows parse successfully |
| Workflow Bash syntax | PASS — all workflow `run` blocks parse with Bash 5.3 |
| G9 packager simulation | PASS — nested evidence directories inventory and verify atomically |
| Secret-leak scan | PASS — remediation diff contains no credential material |
| `git diff --check` | PASS |
| Frozen-path audit | PASS — no production, production-test, POM, dependency, benchmark or G9 workflow change |
| B2/B3 remediation static verification | PASS — YAML/Bash validation, G9 packager simulation, secret-leak scan and docs/evidence audit |
| Current B3 remediation Standard CI | [32862255686](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32862255686) — PASS at `bdceeb588f163465040b315da2ae1fa4a444bc31` |
| Current B3 remediation Quick Lane | [32862256047](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32862256047) — PASS at `bdceeb588f163465040b315da2ae1fa4a444bc31` |
| Standard exact-SHA CI | [32831047004](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32831047004) — PASS |
| Qualification Quick Lane | [32831046928](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32831046928) — PASS |
| Final remediation Standard CI | [32845529323](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32845529323) — PASS |
| Final remediation Quick Lane | [32845529342](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32845529342) — PASS |
| Controller docs checkpoint | [32835631051](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32835631051) — FAIL at `Verify`; Quick [32835630967](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32835630967) — PASS |
| Local rerun after controller checkpoint | `mvn --batch-mode --no-transfer-progress verify` — PASS; 225 core + 61 qualification, 2 expected skips, Checkstyle 0 |

## Evidence and claim boundary

This report records implementation evidence only. Under the current offline
G11 contract, toolchain/candidate mismatch, invalid or inconsistent SBOM,
dependency/license evidence, verified or unresolved secret, missing artifact
or hash/publication failure remains a blocker; none may be converted to PASS
by omission or substitution.

The first B2/B3 remediation Evidence Gate at `b44fc4d` was PASS, and the fresh
G9 run `32856372581` is qualifying/frozen. Every NVD-backed G11 attempt,
including latest run `32870534485`, remains preserved under its original
contract as FAIL/B3/non-qualifying. The Human-approved policy amendment
preserved those results and authorized the separate fresh offline G11
`32955619875`, which passed under the amended contract. TASK-049 remains
locked pending TASK-048 final review. Full campaigns, `v1.0.0`, GitHub Release
and GA remain unauthorized.

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
later Data-Feed Amendment has a new canonical digest recorded below.
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
Data-Feed Amendment supersedes the API credential-mode attempt while retaining
the same scanner, freshness and severity policy. No replacement execution is
authorized by either remediation.

The historical Human-approved B2/B3 remediation was limited to the publication
and credential-mode contracts. G9 uses the full immutable `actions/upload-artifact` commit
`043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` with one deterministic artifact,
`if-no-files-found=error`, 14-day retention, compression level 6,
`overwrite=false`, hidden files excluded and `if: always()`. Each workflow
records the action's artifact ID, URL and SHA-256 digest in the run summary.
At that historical checkpoint G11 remained blocked until repository-level
credential provisioning. Later amendments superseded that acquisition path;
the statement is retained only to explain the historical evidence and is not
the current v2 prerequisite.

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

## Historical / superseded NVD JSON 2.0 data-feed amendment

The selected acquisition path under that historical contract was the official
NVD JSON 2.0 data feed. Before Dependency-Check, the workflow was required to
validate the `modified` feed metadata/archive, 24-hour freshness, sizes, gzip
integrity and uncompressed JSON SHA-256. Metadata, archive/content digests,
source URLs, sizes and measured age were intended as immutable evidence.
Dependency-Check 13.0.0 was the analyzer; no custom matcher or API key was
introduced.

This qualification-only amendment does not modify the candidate, production
code, POM/dependency graph, scanner version, thresholds, prior evidence or G9.
Missing, malformed, stale or unusable feed data, scanner errors and missing
JSON/SARIF reports were fail-closed B3 outcomes. This section is retained only
to interpret historical evidence and is superseded by the offline G11 policy
below; TASK-049 remains locked.

The final historical NVD-backed v1 policy bytes hash to
`c677eaa8c09b17d6212f578830fa5e483f9b5bd961b8f477585d9d576ab5700e`; this
hash remains an interpretation input for v1 artifacts and does not qualify a
G9/G11 run under the amended v2 policy.

## Human-approved offline G11 policy amendment

The latest NVD-backed G11 run `32870534485` failed in official-feed preflight
before Dependency-Check analysis. Artifact `9571906279`, digest
`13db7d0f2e0915d4435e039baf4f9ff70215e4aef6712d5d2bf41dec538ad6a1`,
is preserved FAIL/B3/non-qualifying. No candidate defect was observed.

Human then approved `OFFLINE_SUPPLY_CHAIN_SECURITY_V1`. The historical v1
policy remains unchanged; at that pre-disposition checkpoint the v2 policy
hash was `f7329011958aa9c52eb6886aaadabbab8d26ff3b150f1a96d9aceead1f013114`.
Dependency-Check/NVD/CVE/CVSS are outside the new normative portfolio gate and
no cleanliness claim is permitted. This amendment implementation does not
execute G11 or authorize TASK-049.

## Latest offline G11 execution and current B2 remediation

The first Human-authorized fresh execution under the offline policy was run
once as `32925783003`. It failed before SBOM/dependency/license evidence at
the Maven build step because the workflow invoked `mvn -f core/pom.xml`.
That changed Maven's project base to `core/`, so the frozen root-relative
Checkstyle path resolved under `core/config/` instead of the actual
repository-root `config/checkstyle/checkstyle.xml`. Artifact `9591451565`,
digest `2511c2276f40f83868db27591a2eb7afc644c4eeb7621a1cda8aa17af3cb40cf`,
and the complete failed result remain preserved as `FAIL / B2 /
NON-QUALIFYING`; no candidate defect was observed and no retry was started.

Human then authorized a limited qualification-workflow remediation. It was
implemented at `e1464ed`; Standard CI `32927818204` and Qualification Quick
Lane `32927818172` both passed. Current
v2 Maven commands execute from the repository root: the lifecycle build uses
`-pl core -am package -DskipTests`, while SBOM, runtime-dependency and
license-report goals use `-pl core`. The candidate POM, Checkstyle files,
production source, dependency graph and G9 workflow remain unchanged. The v2
policy properties digest was recomputed as
`e834d18b0cb51624edbac40e6294bf575ebf73bab3a8cbf469423fba150de4fc`.
This remediation only repairs the qualification invocation boundary. Its
Evidence Gate is PASS, but it does not authorize another G11 execution; a
fresh G11 still requires a separate Human execution approval.

## Current license-report B2 remediation

The current Human-approved limited remediation preserves the plugin goal and
version but corrects its Maven execution-root contract. The workflow now runs
the aggregator goal from the candidate repository root with `-pl core -am`,
freezes `license.executeOnlyOnRootModule=true`, and expects the non-empty source
artifact at `target/reports/aggregate-third-party-report.html`. The old
`core/target/reports` path is rejected. The copied report is required at
`license/plugin-reports/aggregate-third-party-report.html` and is included in
the immutable inventory. Candidate/POM/production/G9 inputs remain unchanged.

At the pre-disposition license-report checkpoint the v2 policy digest was
`f7329011958aa9c52eb6886aaadabbab8d26ff3b150f1a96d9aceead1f013114`; the
current disposition amendment uses the updated policy hash recorded above.
The repository-root isolated smoke generated a 15,394-byte report at the
approved path and no legacy core report. The workflow now validates UTF-8 HTML
structure, the pinned plugin marker, dependency headings and exact coordinate
reconciliation against the runtime inventory, then writes a SHA-256/count
validation sidecar into the immutable inventory. Focused policy/workflow tests,
full `mvn verify`, Checkstyle, diff/frozen-path audits and exact-SHA Standard /
Quick CI passed at `30c89c4` / `32932454011` / `32932454009`. This is the
remediation Evidence Gate only; a fresh G11 execution remains separately
Human-gated and unauthorized.

The latest fresh offline G11 run, `32929258318`, remains preserved exactly as
`FAIL / B2 / NON-QUALIFYING`: candidate build and Checkstyle completed, but
`license-maven-plugin:2.7.1:aggregate-third-party-report` was skipped and the
required report artifact was absent. Its GitHub artifact is `9592633595` with
digest `4382fcb02c8a97c42d66e7617e9276e470f772def42a02a9c1067cebd5cb7c4b`.
No candidate defect was observed and no retry was started.

The next Human-authorized fresh G11 run, `32943456313`, is also preserved as
`FAIL / B2 / NON-QUALIFYING`. Candidate identity, JDK/tool resolution,
candidate build, SBOM input generation and dependency input generation
completed. The license-report validation step then referenced `REPO` without
declaring it in that independent shell block, and `set -u` terminated the
workflow before the remaining G11 criteria. Its failure artifact is `9597396741`
with digest `bc3ad708418d0194c05e695d4990daa1e9319480b6787493e4968f132fc17689`.
A read-only cross-step shell-variable audit found no second undeclared local
variable. The limited workflow-only remediation is implemented at `eced533`,
with Standard CI `32945056542` and Quick Lane `32945056508` passing; a new G11
execution still requires separate Human approval.

## Current G11 false-positive disposition amendment

Run `32947367541` reached the pinned full-history Gitleaks scan and found two
generic-api-key results in documentation prose. The results are preserved as
`FAIL / NON-QUALIFYING / PRESERVED`; candidate defect was not observed and the
candidate-bound scan did not execute because the original shell step stopped on
the history exit code. Read-only review classified both findings as
demonstrably non-secret: one historical environment-variable documentation
reference at commit `993c2477`, and one properties-schema prose line at commit
`d753af0b`. The latter is also bound to the candidate blob identity without
claiming that the skipped candidate-bound scan passed.

Human approved a narrow machine-verifiable disposition amendment. The new
manifest `docs/release/ga-gitleaks-false-positive-dispositions-v1.properties`
binds scanner/rule, scope, canonical path, full commit or candidate production
identity, blob object ID, line range, exact Gitleaks fingerprint, classification
and a fixed non-secret rationale digest. It contains no match, secret, secret
hash or transformed secret. The workflow now runs both scans even when the
history scan returns findings, retains both raw reports, and fails closed on
any finding that is not an exact manifest match. The disposition manifest and
the safe evaluation sidecar are mandatory immutable evidence artifacts.

This is qualification-policy/tooling remediation only. It does not alter the
Gitleaks rules, candidate, production source, G9 evidence or TASK-048 gate
authority. The implementation Evidence Gate must pass focused disposition
tests, malformed/mutated/new-finding fail-closed tests, full regression,
Checkstyle, workflow/static validation, secret-leak scan, frozen-path audit,
verifier/docs-auditor and exact-SHA Standard/Quick CI before a separate Human
fresh G11 execution can be considered.

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
| 2026-08-25 | Human Limited optional-NVD-key amendment (historical / superseded) | API credential-mode attempt preserved as historical; official JSON 2.0 Data-Feed Amendment selected; no G9/G11 execution |
| 2026-08-25 | Human Limited B3 environment-isolation remediation | Authorized to remove `NVD_API_KEY` from the anonymous scanner process environment; G9 `32856372581` remains PASS/qualifying, G11 `32856384325` remains FAIL/B3; no G11 rerun |
| 2026-08-25 | B3 remediation Evidence Gate | PASS — commit `bdceeb588f163465040b315da2ae1fa4a444bc31`; Standard `32862255686` PASS; Quick `32862256047` PASS; G9 remains frozen qualifying evidence; G11 replacement remains separately Human-gated |
| 2026-08-25 | Human Limited Data-Feed Amendment | Approved official NVD JSON 2.0 feed for Dependency-Check `13.0.0`; API key not required; metadata/archive/content integrity and <=24h freshness mandatory; no G11 execution authorized |
| 2026-08-25 | Human Limited Data-Feed Amendment | Approved Dependency-Check `13.0.0` plus official NVD JSON 2.0 feed; API key not required; metadata/archive/content integrity and <=24h freshness remain mandatory; no G11 execution authorized |
| 2026-08-26 | Human-authorized Data-Feed G11 execution | Run `32870534485` FAIL/B3 in feed preflight; artifact `9571906279` / SHA-256 `13db7d0f2e0915d4435e039baf4f9ff70215e4aef6712d5d2bf41dec538ad6a1`; preserved/non-qualifying; no retry |
| 2026-08-26 | Human G11 Qualification Policy Amendment | Approved mandatory `OFFLINE_SUPPLY_CHAIN_SECURITY_V1`; v1/NVD contract and failures preserved; v2 implementation authorized; fresh G11 not authorized |
| 2026-08-26 | Human-authorized offline G11 execution | `32925783003` FAIL/B2/preserved before SBOM because `-f core/pom.xml` changed Checkstyle path resolution; artifact `9591451565` / SHA-256 `2511c2276f40f83868db27591a2eb7afc644c4eeb7621a1cda8aa17af3cb40cf`; candidate defect not observed; no retry |
| 2026-08-26 | Human Limited G11 B2 Remediation | Root-selector Maven remediation authorized (`-pl core -am` / `-pl core`), v2 policy hash recomputed to `e834d18b0cb51624edbac40e6294bf575ebf73bab3a8cbf469423fba150de4fc`; no candidate/POM/production change and no new G11 execution |
| 2026-08-26 | G11 B2 Remediation Evidence Gate | PASS — `e1464ed`; Standard `32927818204`; Quick `32927818172`; focused/full regression, root-selector build, YAML/Bash, policy hash, Markdown links, diff and frozen-boundary audits PASS; fresh G11 remains separately Human-gated |
| 2026-08-26 | Human Limited G11 license-report B2 Remediation | AUTHORIZED / IN PROGRESS | Preserve `license-maven-plugin:2.7.1:aggregate-third-party-report`; switch workflow to root `-pl core -am`; require `target/reports/aggregate-third-party-report.html`; update v2 policy identity and validator; no fresh G11 execution |
| 2026-08-26 | Human-authorized offline G11 execution | FAIL / B2 / PRESERVED | Run `32929258318`; artifact `9592633595` / SHA-256 `4382fcb02c8a97c42d66e7617e9276e470f772def42a02a9c1067cebd5cb7c4b`; license report goal skipped before required artifact publication; candidate defect not observed; no retry |
| 2026-08-26 | License-report B2 remediation Evidence Gate | PASS | `30c89c4`; Standard CI `32932454011`; Quick Lane `32932454009`; root-reactor report parseability, runtime-coordinate reconciliation and Maven module-annotation handling validated; production/candidate/POM/G9 diff 0; fresh G11 remains separately Human-gated |
| 2026-08-26 | Human-authorized fresh G11 execution | FAIL / B2 / PRESERVED | Run `32943456313`; artifact `9597396741` / SHA-256 `bc3ad708418d0194c05e695d4990daa1e9319480b6787493e4968f132fc17689`; license-report validation aborted on undeclared step-local `REPO`; candidate defect not observed; no retry |
| 2026-08-26 | Sol High B2 cross-step scope review | CONFIRMED | Only the license validation step omitted `REPO`; limited workflow-only remediation approved; no policy/candidate/POM change |
| 2026-08-26 | G11 shell-scope B2 remediation Evidence Gate | PASS | `eced533`; Standard CI `32945056542`; Quick Lane `32945056508`; focused/full regression, step-local variable audit, YAML/Bash, diff and frozen-boundary checks PASS; fresh G11 remains separately Human-gated |
| 2026-08-26 | TASK-048/Phase 11 status synchronization | PASS | `e51db47`; Standard CI `32945333516`; Quick Lane `32945333468`; latest failure, remediation and next Human gate reconciled across status documents |
| 2026-08-26 | Final B2 remediation audit checkpoint | PASS | `688d955`; Standard CI `32946223271`; Quick Lane `32946223268`; verifier and frozen-path checks PASS; fresh G11 remains separately Human-gated |
| 2026-08-26 | G11 false-positive disposition amendment Evidence Gate | PASS | `7be2b61`; Standard CI `32951233073`; Quick Lane `32951233014`; 225 core + 72 qualification tests, 2 expected skips, Checkstyle 0, Python disposition tests 3/3, YAML/Bash/static validation, verifier, docs-auditor and frozen-path audits PASS; fresh G11 remains separately Human-gated |
| 2026-08-26 | Human-authorized fresh G11 execution | FAIL / B2 / PRESERVED | Run `32952590543`; candidate-bound Gitleaks emitted absolute `/repo/tasks/reports/PHASE-10-task-043.md`, so canonical disposition evaluation failed; candidate Blob matched approved disposition; no candidate defect or retry |
| 2026-08-26 | Sol High B2 path-contract review | CONFIRMED | Absolute candidate scan target caused non-canonical metadata; native `dir .` from the mounted checkout root selected; evaluator/disposition identity remain unchanged |
| 2026-08-26 | Human Limited G11 B2 Remediation | AUTHORIZED / IN PROGRESS | Workflow-only candidate path-contract correction; v2 policy records `repository-relative-v1` / `working-directory`; no candidate/POM/production/G9 change or fresh G11 execution |
| 2026-08-26 | G11 path-contract B2 Remediation Evidence Gate | PASS | `f6db140`; Standard `32954953854`; Quick `32954953801`; focused/full tests, YAML/Bash, policy hash, raw-report preservation and frozen-boundary audits PASS; fresh G11 remains separately Human-gated |
| 2026-08-26 | Human-authorized fresh G11 execution | PASS / QUALIFYING / FROZEN | Run `32955619875`; artifact `9601871146`; GitHub digest `sha256:5c4a54e3c28ec14d7709b4a5e747d79aa4bb710d4cb80b8ee489e31912cc7afd`; candidate identity, canonical Gitleaks disposition, SBOM/dependency/license/secret/provenance/inventory and SHA256SUMS checks PASS; historical failures preserved; TASK-048 final evidence review pending |
