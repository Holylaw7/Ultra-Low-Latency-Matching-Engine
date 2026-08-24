# Phase 10 — TASK-043 Evidence Report

## Executive Status

| Field | Value |
| --- | --- |
| Phase | Phase 10 — Release-Candidate Runtime Assembly |
| Task | `TASK-20260824-043` |
| Result | `Completed / Evidence Gate PASS` |
| Baseline | `v0.8.0-engineering-baseline` / `ef73f60` |
| Branch | `feature/phase10-release-candidate-runtime` |
| Scope | Strict runtime configuration, CLI validation and reproducible Java 21 packaging |
| Technical checkpoint | `247d526` |
| Standard CI | `32724123762` PASS |
| Qualification Quick Lane | `32724123745` PASS |
| Next Gate | TASK-044 Evidence Gate |
| Product Release | Not Authorized |

## 1. Implementation Boundary

TASK-043 connects the approved Phase 10 configuration contract to the existing
`ReleaseCandidateRuntime` composition root and packages the application as one
executable Java artifact. The implementation is limited to the application
boundary, its tests, the example configuration and Maven packaging.

The loader implements strict UTF-8 properties-v1 semantics: whole-line `#`/`!`
comments and blank lines are accepted; every other line must contain exactly one
`=`; keys are trimmed, duplicate/unknown keys are rejected, escapes and
continuation syntax are rejected, and values are validated by the existing typed
schema. Relative storage paths resolve against the configuration-file directory.
Validation does not create storage directories and rejects existing non-directory
storage paths before recovery or listener work.

The CLI supports `--config <path>`, `--config=<path>`, `--validate-config`,
`--print-effective-config`, `--help` and `--version`. Configuration and command
line rejection return exit code `2`; startup/recovery and Protocol bind failures
retain the approved stable mappings. The effective configuration is sorted,
normalized and sanitized.

Maven packaging uses the existing reactor and produces the exact shaded artifact
`core/target/matching-engine-rc.jar` with
`com.ultralatency.matching.MatchingEngineApplication` as its main class. No
runtime dependency, data format, protocol, recovery, matching, sequence,
durability or ownership semantic was changed.

## 2. Acceptance Evidence

| Criterion | Evidence |
| --- | --- |
| Strict parser and schema | `RuntimeConfigurationLoaderTest`, `RuntimeConfigurationSchemaTest` cover UTF-8 decoding, comments, duplicate/malformed/escaped/unknown keys, defaults, ranges, relative paths and non-directory rejection |
| CLI grammar and exit matrix | `RuntimeCommandLineTest` covers help/version, both config forms, validation/rendering, missing/unknown/conflicting options and stable configuration exit code |
| Safe example | `config/release-candidate-example.properties` uses loopback binds and `SYNC_EACH_APPEND` |
| No validation side effects | Loader test confirms `--validate-config`/load does not create WAL or Snapshot directories |
| Executable packaging | `core/target/matching-engine-rc.jar` contains the manifest and `MatchingEngineApplication.class`; manifest declares `Main-Class: com.ultralatency.matching.MatchingEngineApplication`, `Java-Version: 21` and `Build-Jdk-Spec: 21` |
| Java 21 launch smoke | Java 21 `java -jar` runs `--help`, `--version`, `--config config/release-candidate-example.properties --validate-config` and `--print-effective-config` successfully |
| Artifact identity | SHA-256 `EE62F82855684B6BB2B316653E1C6AA083755DFEC5CD515251C35B4276283BF9` |

The effective-config smoke intentionally prints normalized absolute storage
paths because that action explicitly requests the effective configuration; the
safe checked-in example remains loopback-only and contains no secrets.

## 3. Changed Files

```text
core/pom.xml
config/release-candidate-example.properties
src/main/java/com/ultralatency/matching/MatchingEngineApplication.java
src/main/java/com/ultralatency/matching/app/RuntimeCommandLine.java
src/main/java/com/ultralatency/matching/app/RuntimeConfigurationLoader.java
src/test/java/com/ultralatency/matching/MatchingEngineApplicationTest.java
src/test/java/com/ultralatency/matching/app/RuntimeCommandLineTest.java
src/test/java/com/ultralatency/matching/app/RuntimeConfigurationLoaderTest.java
```

`.vscode/settings.json` remains local, untracked and untouched; it is not a
Phase 10 artifact and is intentionally excluded from the commit.

## 4. Verification

```text
Focused TASK-043 suite:
  12 tests, 0 failures

Full reactor mvn verify:
  core: 217 tests, 0 failures
  qualification: 46 tests, 0 failures, 2 intentionally skipped
  benchmark: no tests

Checkstyle:
  0 violations in all Maven modules

git diff --check:
  PASS

Artifact smoke:
  Java 21 --help / --version / --validate-config / --print-effective-config PASS

Frozen TASK-043 scope audit:
  no TASK-043 changes under Domain, OrderBook, MatchingEngine, Pipeline,
  WAL, Snapshot, Recovery or Protocol production paths
```

The first local full-reactor invocation encountered a transient Windows
file-share error while the pre-existing qualification child-process test read
a WAL segment. The focused test passed immediately, and the subsequent full
`mvn verify` passed without changing qualification code or evidence. The
transient invocation is not used as a successful evidence claim.

The branch was pushed at `247d526`; Standard CI `32724123762` and Qualification
Quick Lane `32724123745` both passed for that exact SHA.

## 5. Scope Notes and Deferred Work

TASK-044 remains responsible for the Management listener and bounded operational
status boundary. TASK-045 remains responsible for shutdown and terminal-failure
hardening. TASK-046 remains responsible for the RC assembled-runtime
qualification harness and separately Human-gated Full Campaign.

This task does not claim public-network safety, TLS/authentication,
Production Ready status, SLA/RTO, exactly-once delivery, hardware power-loss
safety or a Product Release.

## 6. Governance State

```text
TASK-041: Completed / Evidence Gate PASS
TASK-042: Completed / Evidence Gate PASS
TASK-043: Completed / Evidence Gate PASS
TASK-044: Authorized / Next
TASK-045~046: Dependency Locked
Phase 10 Closure: Not Authorized
Merge / v0.9.0-rc.1: Not Authorized
Product Release: Not Authorized
```

## 7. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-24 | Implemented | Added strict UTF-8 loader, CLI boundary, safe config fixture and Java 21 executable packaging. | Focused 12/12 PASS; artifact smoke PASS |
| 2026-08-24 | Evidence Gate PASS | Technical checkpoint `247d526` pushed; Standard CI `32724123762` and Quick Lane `32724123745` PASS. | Full reactor, Checkstyle, diff and artifact audits PASS |

## 8. Completion Checklist

- [x] Human Blueprint Approval inherited
- [x] TASK-042 dependency Evidence Gate PASS
- [x] strict configuration/CLI contract implemented and tested
- [x] safe loopback/SYNC example committed
- [x] exact Java 21 executable artifact and manifest verified
- [x] artifact hash/build metadata recorded
- [x] full/static/focused/diff gates PASS
- [x] exact-SHA Standard/Quick CI PASS
- [x] TASK-044 synchronized as Authorized / Next

**Blueprint Authorized — continue with TASK-044.**
