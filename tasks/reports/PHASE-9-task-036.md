# Phase 9 — TASK-20260823-036 / Public-Boundary Qualification Harness

## Status

| Field | Value |
| --- | --- |
| Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Task | `TASK-20260823-036` |
| Stage | Implementation / Verification |
| Result | Changes Required — Evidence Remediation |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Branch | `feature/phase9-system-qualification` |
| Commit | `c7df983` |
| Tests | `13 qualification + 195 core` passed; explicit quick lane PASS |
| CI | Standard `32627014499` PASS; quick `32627014583` PASS |
| Next Gate | TASK-036 Evidence Rerun |

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
  suffix result digests.
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
standard exact-SHA CI                                            # 32627014499 PASS
quick-lane exact-SHA CI                                          # 32627014583 PASS
```

`.vscode/` remains untouched/untracked.

## Gate

TASK-036 implementation and automated Evidence Gates passed at `c7df983`, but
read-only verification found that the checkpoint and public-probe digest
labels overstated their evidence. Limited remediation now computes the actual
recovered MatchingEngine checkpoint digest, records the WAL command digest
separately, and canonicalizes a fixed two-exchange public Protocol v1 suffix.
TASK-037 remains locked until the remediation rerun and read-only audits pass.
Phase 9 Closure remains unauthorized.
