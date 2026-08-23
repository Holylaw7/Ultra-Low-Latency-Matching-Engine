# Phase 9 — TASK-20260823-036 / Public-Boundary Qualification Harness

## Status

| Field | Value |
| --- | --- |
| Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Task | `TASK-20260823-036` |
| Stage | Implementation / Verification |
| Result | In Progress — Evidence Gate pending |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Branch | `feature/phase9-system-qualification` |
| Commit | Pending |
| Tests | Pending final checkpoint |
| CI | Pending exact-SHA checkpoint |
| Next Gate | TASK-036 Evidence Gate; then TASK-037 if PASS |

## Goal

Drive the frozen durable/recovery runtime through Protocol v1 TCP using a
qualification-only client and deterministic three-session runner. The harness
does not call internal coordinator, pipeline or engine methods.

## Implemented Scope

Pending final implementation checkpoint.

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
mvn verify                                                       # pending final checkpoint
git diff --check                                                 # pending final checkpoint
frozen-path audit                                                # pending final checkpoint
exact-SHA CI                                                     # pending final checkpoint
```

`.vscode/` remains untouched/untracked.

## Gate

TASK-036 remains in progress until the final implementation/evidence commit,
exact-SHA CI and read-only verifier/docs-auditor PASS. TASK-037 is locked until
that gate passes. Phase 9 Closure remains unauthorized.
