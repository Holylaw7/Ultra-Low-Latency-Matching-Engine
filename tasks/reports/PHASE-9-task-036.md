# Phase 9 — TASK-20260823-036 / Public-Boundary Qualification Harness

## Status

| Field | Value |
| --- | --- |
| Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Task | `TASK-20260823-036` |
| Stage | Implementation / Verification |
| Result | Completed — Evidence Gate PASS |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Branch | `feature/phase9-system-qualification` |
| Commit | `c7df983` |
| Tests | `14 qualification (1 quick skipped) + 195 core` passed; explicit quick lane PASS |
| CI | Remediation standard `32627744868` PASS; quick `32627744878` PASS |
| Next Gate | TASK-037 Authorized / Next |

## Goal

Drive the frozen durable/recovery runtime through Protocol v1 TCP using a
qualification-only client and deterministic three-session runner. The harness
does not call internal coordinator, pipeline or engine methods.

## Implemented Scope

- Added a JDK socket Protocol v1 client with strict frame/header validation.
- Added immutable exchange and match observations with transcript digests.
- Added a public-boundary runner using the real recoverable TCP server.
- Added three-session WAL-authoritative recovery and persisted-command equality
  validation.
- Added deterministic recovered-checkpoint, transcript and fixed public-probe
  suffix result digests; WAL command digest is recorded separately.
- Added an explicit 10,000-command / three-session quick smoke lane and workflow.

## Explicitly Not Implemented

- full soak/resource campaign (TASK-037);
- restart/forced-termination campaign (TASK-038);
- JMH/JFR performance work (TASK-039);
- production source, protocol, WAL, Snapshot or recovery changes.

## Evidence Plan

```text
mvn -pl qualification -am test                                 # PASS
mvn -pl qualification -am -Dqualification.quick=true \
  -Dtest=QualificationQuickSmokeTest test                       # PASS
mvn verify                                                       # PASS
git diff --check                                                 # PASS
frozen-path audit                                                # PASS (0)
initial standard exact-SHA CI                                    # 32627014499 PASS
initial quick-lane exact-SHA CI                                  # 32627014583 PASS
remediation standard exact-SHA CI                                # 32627744868 PASS
remediation quick-lane exact-SHA CI                              # 32627744878 PASS
```

`.vscode/` remains untouched/untracked.

## Gate

TASK-036 implementation and automated Evidence Gates passed at `c7df983`, but
read-only verification found that the checkpoint and public-probe digest
labels overstated their evidence. Limited remediation completed at `f90e42c`,
computing the actual recovered MatchingEngine checkpoint digest, recording the
WAL command digest separately, and canonicalizing a fixed two-exchange public
Protocol v1 suffix. Standard CI `32627744868` and Quick Lane `32627744878`
passed; verifier and docs-auditor returned PASS. TASK-037 is authorized next.
Phase 9 Closure remains unauthorized.
