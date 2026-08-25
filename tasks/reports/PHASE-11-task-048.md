# Phase 11 — TASK-048 Evidence Report

## Status

`In Progress — implementation checkpoint complete; external G9/G11 preflight
execution and final exact-SHA CI evidence pending.`

TASK-048 implements the approved qualification-only reproducibility and
security preflight boundary. It does not qualify the candidate, authorize a
campaign, mutate `v0.9.0-rc.1`, or grant release authority.

## Fixed technical input

| Item | Value |
| --- | --- |
| Candidate tag | `v0.9.0-rc.1` |
| Annotated tag object | `dfd38c08e80aed9035bf1c2d7c8faf8bae99c356` |
| Peeled production SHA | `e2828f563ee41316c062385c0244ac1336731359` |
| Approved toolchain policy | `ga-security-toolchain-v1.properties` |
| Policy SHA-256 | `7c6e36e0bc045fad38255be65a519ee8db19b877d786a5be26d399efbf4e5554` |

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
| G9/G11 scanner execution | Not run locally; workflow evidence pending |
| Full `mvn verify` | PASS — 225 core + 61 qualification tests; 2 expected skips |
| Workflow YAML parse | PASS — both new workflows parse successfully |
| `git diff --check` | PASS |
| Frozen production/existing-workflow audit | PASS — no production, POM or existing workflow change |
| Standard exact-SHA CI | [32831047004](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32831047004) — PASS |
| Qualification Quick Lane | [32831046928](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32831046928) — PASS |
| Controller docs checkpoint | [32835631051](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32835631051) — FAIL at `Verify`; Quick [32835630967](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32835630967) — PASS |
| Local rerun after controller checkpoint | `mvn --batch-mode --no-transfer-progress verify` — PASS; 225 core + 61 qualification, 2 expected skips, Checkstyle 0 |

## Evidence and claim boundary

This report records implementation evidence only. Scanner outage, toolchain
identity mismatch, non-reproducible JAR, vulnerability, verified secret or
prohibited runtime license remains `ABORTED`/blocker according to ADR-0019;
none may be converted to PASS by omission or tool substitution.

TASK-049 remains dependency-locked until the TASK-048 Evidence Gate passes.
Full campaigns, `v1.0.0`, GitHub Release and GA remain unauthorized.

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
performed. A Human/Sol decision is required for an approved environment or a
toolchain amendment before another run.

The later controller documentation checkpoint `1ca088f` had Quick Lane PASS,
but Standard CI `32835631051` failed in the generic `Verify` step. A clean local
`mvn verify` rerun passed; the public check annotation exposes no diagnostic
beyond exit code 1, so this remains an unresolved CI observation rather than a
qualification PASS. No retry, production change or toolchain substitution is
claimed here.

## Completion log

| Date | Stage | Result |
| --- | --- | --- |
| 2026-08-25 | Human Blueprint inheritance | TASK-048 authorized after TASK-047 PASS |
| 2026-08-25 | Implementation checkpoint | Qualification policy/evaluator, pinned workflows and tests added; commit `b64a399` |
| 2026-08-25 | Standard/Quick CI | `32831047004` / `32831046928` PASS; G9/G11 workflows remain manual preflight evidence |
| 2026-08-25 | Approved workflow installation | master merge `0575c76`; CI `32835193395` / Quick `32835193084` PASS; only the two new workflow files installed |
| 2026-08-25 | G9/G11 dispatch attempt | HTTP 404: workflows are not installed on the default branch; no run/artifact created |
| 2026-08-25 | G9/G11 execution | Runs `32835408168` / `32835411241` ABORTED/B3 at pinned JDK setup; no artifacts; no retry |
| 2026-08-25 | Controller docs checkpoint | `1ca088f`; Quick `32835630967` PASS; Standard `32835631051` failed at `Verify`; clean local rerun PASS, CI diagnosis pending |
