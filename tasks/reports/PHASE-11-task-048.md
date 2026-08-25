# Phase 11 — TASK-048 Evidence Report

## Status

`In Progress — implementation checkpoint complete; external G9/G11 preflight
execution and exact-SHA CI evidence pending.`

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
| Standard/Quick exact-SHA CI | Pending final checkpoint |

## Evidence and claim boundary

This report records implementation evidence only. Scanner outage, toolchain
identity mismatch, non-reproducible JAR, vulnerability, verified secret or
prohibited runtime license remains `ABORTED`/blocker according to ADR-0019;
none may be converted to PASS by omission or tool substitution.

TASK-049 remains dependency-locked until the TASK-048 Evidence Gate passes.
Full campaigns, `v1.0.0`, GitHub Release and GA remain unauthorized.

## Completion log

| Date | Stage | Result |
| --- | --- | --- |
| 2026-08-25 | Human Blueprint inheritance | TASK-048 authorized after TASK-047 PASS |
| 2026-08-25 | Implementation checkpoint | Qualification policy/evaluator, pinned workflows and tests added |
