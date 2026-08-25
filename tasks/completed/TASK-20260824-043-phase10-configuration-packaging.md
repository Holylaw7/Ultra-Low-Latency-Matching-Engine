# Task Plan — TASK-20260824-043

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260824-043` |
| Title | Strict runtime configuration and reproducible packaging |
| Status | `Completed / Evidence Gate PASS` |
| Implementer | Main Codex / Luna Max — only writer after approval |
| Related ADR | [`ADR-0018`](../../docs/adr/ADR-0018-release-candidate-runtime-boundary.md) |
| Blueprint | [`Phase 10 Blueprint`](../blueprints/PHASE-10-release-candidate-runtime-assembly-blueprint.md) |
| Dependency | TASK-042 Evidence Gate PASS |
| Evidence report | [`PHASE-10-task-043.md`](../reports/PHASE-10-task-043.md) |

## 2. Goal

Complete the strict properties-file/CLI contract and produce a reproducible
executable artifact with validated launch examples. Packaging must invoke the
same composition root used by integration tests.

## 3. Scope

In scope: config loader/validator, `--help`, `--version`,
`--validate-config`, sanitized `--print-effective-config`, Maven executable
artifact configuration and child-process launch tests. No new dependency or
installer/container/service-manager integration.

## 4. Acceptance Criteria

- [x] CLI grammar and exit codes match ADR-0018.
- [x] Missing, unknown, duplicate-semantic and invalid configuration is rejected
  before storage/recovery/listener work.
- [x] Safe example config is loopback-only and `SYNC_EACH_APPEND`.
- [x] Effective config output is deterministic and contains no unsafe secrets or
  accidental absolute-path disclosure beyond explicitly requested values.
- [x] Built artifact is exactly `core/target/matching-engine-rc.jar`, declares
  `MatchingEngineApplication` and launches on Java 21 with the recorded command.
- [x] Artifact content/hash and build metadata are recorded reproducibly.
- [x] Full regression, Checkstyle, diff/reviewer and exact-SHA CI pass.

## 5. Tests and Evidence

Golden parser/render tests, cross-field validation, relative-path resolution,
CLI exit matrix, child-process validate/start/stop smoke and artifact inspection.
Benchmarking is not required in this task.

## 6. Exception Gate / Rollback

Stop for a new packaging dependency, config live reload, environment-per-key
fallback, non-loopback default, TLS/auth or changed runtime semantic. Rollback
removes packaging/config additions without touching data formats.

## 7. Approval

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-24 | Human Developer | Approved / Inherited | TASK-042 Evidence Gate PASS; execution authorized in the Phase 10 dependency chain |

## 8. Current Implementation / Design / ADR Linkage

The Maven core artifact is not an operational executable and the application
stub has no CLI. TASK-041 supplies exact config contracts and TASK-042 supplies
the composition root. TASK-043 connects only those approved layers.

The selected design is a strict built-in parser and an executable core artifact
using Maven packaging already approved by the parent build. A full framework,
environment expansion, installer and container image were rejected. ADR-0018
D6, D9 and D10 are normative.

## 9. Planned Files

| Path | Change |
| --- | --- |
| `src/main/java/com/ultralatency/matching/app/RuntimeCommandLine.java` | exact CLI actions |
| `src/main/java/com/ultralatency/matching/app/RuntimeConfigurationLoader.java` | strict-properties-v1 parsing |
| `src/main/java/com/ultralatency/matching/MatchingEngineApplication.java` | exit mapping/run action |
| `core/pom.xml` | executable manifest/package configuration, no dependency |
| `config/release-candidate-example.properties` | safe loopback example |
| corresponding tests/report/docs | golden and child-process evidence |

## 10. Verification Commands

```text
mvn -pl core -am test
mvn verify
java -jar core/target/matching-engine-rc.jar --help
java -jar core/target/matching-engine-rc.jar --config <fixture> --validate-config
git diff --check
```

Benchmark/profile: not applicable. Artifact hash/contents and child-process
startup are packaging evidence.

## 11. Stages / Git / CI

parser/CLI -> packaging -> child-process matrix -> verifier/docs audit ->
exact-SHA CI. Planned commit:
`build(runtime): package validated release-candidate application`.

## 12. Risks / Rollback / Checklist

Shading/manifest mistakes can mask dependencies; inspect artifact contents and
dependency tree. Absolute-path leakage is prevented by golden sanitized output.
Rollback reverts build/config artifacts without touching WAL/Snapshot bytes.

- [x] TASK-042 PASS and approval inherited
- [x] all normative config/CLI cases covered
- [x] reproducible executable launch recorded
- [x] no new dependency or machine-specific artifact
- [x] report/review/exact-SHA CI PASS
- [x] TASK-044 synchronized as Authorized / Next

## 13. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-24 | Proposed | Packaging/config plan frozen | docs only |
| 2026-08-24 | Implemented | Added strict UTF-8 properties-v1 loader, CLI actions/exit mapping, safe loopback example and Java 21 shaded executable packaging. | Focused 12/12 PASS; Java 21 artifact smoke PASS |
| 2026-08-24 | Evidence Gate PASS | Technical checkpoint `247d526`; Standard CI `32724123762` and Qualification Quick Lane `32724123745` PASS. | Full reactor, Checkstyle, diff audit and artifact metadata PASS |
