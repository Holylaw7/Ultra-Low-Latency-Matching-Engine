# Phase 9 — TASK-20260823-037 / Full Soak and Resource Qualification

## Status

| Field | Value |
| --- | --- |
| Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Task | `TASK-20260823-037` |
| Stage | Implementation / Verification |
| Result | In Progress — Evidence Gate pending |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Branch | `feature/phase9-system-qualification` |
| Implementation | `b80e12e` + resource-evidence refinement `0ee094c` |
| Standard CI | `32629749230` PASS |
| Quick Lane CI | `32629749209` PASS |
| Next Gate | TASK-038 remains locked until PASS |

## Goal

Build the Full Qualification lane on top of the public Protocol v1 harness.
The lane freezes workload/configuration metadata, preserves raw evidence and
checks long-run resource behavior without modifying the production runtime.

## Implemented Scope

- immutable FULL and short TEST lane configuration;
- full-run orchestration through the recoverable TCP server;
- JFR capture and non-invasive resource sampling;
- natural post-GC heap evidence and lifecycle guards;
- manifest, resource CSV, JFR and failure-artifact hashing;
- focused configuration and short-lane integration tests.

The short-lane implementation evidence is complete at `0ee094c`. The real
60-minute / 1,000,000-command Full lane is an explicit manual evidence unit;
it has not been claimed or substituted by the short lane.

## Explicitly Not Implemented

- the real 60-minute / 1,000,000-command campaign in CI;
- restart/forced-termination campaign (TASK-038);
- JMH/JFR performance characterization beyond required soak capture
  (TASK-039);
- production source, protocol, WAL, Snapshot or recovery changes.

## Evidence Plan

```text
mvn -pl qualification -am test       # PASS (18 qualification incl. 1 skip; 195 core)
mvn verify                            # PASS
git diff --check                      # PASS
frozen-path audit                     # PASS (0 production-path changes)
standard exact-SHA CI                 # 32629749230 PASS
quick-lane exact-SHA CI               # 32629749209 PASS
verifier + docs-auditor               # pending final task review
```

The short lane proves harness composition only. A Full Qualification claim
requires one immutable run satisfying both 60 minutes and 1,000,000 accepted
commands, with no retry/filtering and complete raw artifact metadata.

## Gate

TASK-037 remains in progress until the automated Evidence Gate and read-only
reviewers pass. TASK-038, Phase 9 Closure, merge and `v0.8.0-engineering-baseline`
remain unauthorized until that point.
