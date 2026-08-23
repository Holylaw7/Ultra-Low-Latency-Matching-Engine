# Phase 9 — TASK-20260823-037 / Full Soak and Resource Qualification

## Status

| Field | Value |
| --- | --- |
| Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Task | `TASK-20260823-037` |
| Stage | Implementation / Verification |
| Result | In Progress — Full Qualification attempt failed; review pending |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Branch | `feature/phase9-system-qualification` |
| Implementation | `b80e12e` + `0ee094c` + evidence-boundary fix `db18eac` |
| Standard CI | `32630209329` PASS |
| Quick Lane CI | `32630209194` PASS |
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
- persisted artifact hash sidecar and WAL/Snapshot storage inventory;
- focused configuration and short-lane integration tests.

The short-lane implementation evidence is complete at `0ee094c`. The real
60-minute / 1,000,000-command Full lane is an explicit manual evidence unit;
it has not been claimed or substituted by the short lane.

The explicit manual entry point is:

```text
mvn -pl qualification -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dqualification.full=true \
  -Dqualification.output=qualification-results \
  -Dtest=QualificationFullCampaignTest test
```

The short TEST lane reports harness success separately from
`fullCriteriaPassed`; it never produces a Full Qualification claim.

### Full Qualification Attempt (Preserved Failure Evidence)

The explicit Full lane was executed once with the approved immutable
configuration. It was not retried or filtered:

```text
run: qualification-full-5346263e-ad6f-4dee-8798-92fe017311ef
acceptedCommands: 1,000,000
elapsed: 1,916,630 ms (31:56.630)
minimumDuration: 3,600,000 ms (60 minutes)
naturalPostGcSampleCount: 4 / 5 required
fullCriteriaPassed: false
listenerRebound: true
leaseReacquired: true
temporaryFileCount: 0
walFileCount: 612
walBytes: 40,019,552
```

Raw artifacts are retained under the ignored `qualification-results/`
directory. The persisted artifact hash sidecar records the JFR, manifest and
resource-evidence hashes. This run is valid failure evidence, not a Full
Qualification claim; no production defect, retry, threshold change or
configuration drift was introduced.

## Explicitly Not Implemented

- a passing 60-minute / 1,000,000-command campaign (the preserved attempt
  reached the command threshold but failed the duration and natural-sample
  gates);
- restart/forced-termination campaign (TASK-038);
- JMH/JFR performance characterization beyond required soak capture
  (TASK-039);
- production source, protocol, WAL, Snapshot or recovery changes.

## Evidence Plan

```text
mvn -pl qualification -am test       # PASS (19 qualification incl. 2 skips; 195 core)
mvn verify                            # PASS
git diff --check                      # PASS
frozen-path audit                     # PASS (0 production-path changes)
standard exact-SHA CI (implementation) # 32630209329 PASS
quick-lane exact-SHA CI (implementation) # 32630209194 PASS
final docs checkpoint                # f481433 / 32632130698 PASS
verifier + docs-auditor               # pending final task review
```

The short lane proves harness composition only. A Full Qualification claim
requires one immutable run satisfying both 60 minutes and 1,000,000 accepted
commands, with no retry/filtering and complete raw artifact metadata.

## Gate

TASK-037 remains in progress because the preserved Full lane attempt did not
meet all gates. TASK-038, Phase 9 Closure, merge and
`v0.8.0-engineering-baseline` remain unauthorized until a separately approved
qualification decision produces a passing immutable run.
