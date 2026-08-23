# Phase 9 — TASK-20260823-037 / Full Soak and Resource Qualification

## Status

| Field | Value |
| --- | --- |
| Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Task | `TASK-20260823-037` |
| Stage | Implementation / Verification |
| Result | In Progress — Limited campaign-criterion remediation; independent Full Run pending |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Branch | `feature/phase9-system-qualification` |
| Implementation | `b80e12e` + `0ee094c` + evidence-boundary fix `db18eac` + qualification teardown fix `63e54f9` |
| Duration-gate remediation | `5a3917d` |
| Standard CI | `32636481415` PASS |
| Quick Lane CI | `32636481409` PASS |
| Next Gate | Campaign Evidence Gate and one independent qualifying Full Run |

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

The transient qualification CI failures were diagnosed as a harness teardown
ordering race, not a production runtime failure. The runner closed the
Protocol v1 client before shutting down the server; the server was still
`RUNNING` when the client's `channelInactive` callback correctly applied the
active-session disconnect terminal rule. Both qualification runners now shut
down the server before closing the client (`63e54f9`). This preserves the
production disconnect semantics and makes test teardown deterministic.

The repair was verified by the focused full-runner test, five repeated local
runs, `mvn verify`, and exact-SHA CI `32640760008` PASS. Qualification Quick
Lane CI `32640759989` also passed. Earlier failed CI runs remain retained as
historical transient evidence and are not reclassified as passing gates.

The explicit manual entry point is:

```text
mvn -pl qualification -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dqualification.full=true \
  -Dqualification.output=qualification-results \
  -Dtest=QualificationFullCampaignTest test
```

The short TEST lane reports harness success separately from
`fullCriteriaPassed`; it never produces a Full Qualification claim.

### Limited Criterion Amendment — Human Approved

The single-run requirement of five natural post-GC samples was amended after
two identical-config runs each produced four natural samples. The approved
campaign criterion is:

```text
At least two independently qualifying Full runs.
Each run: >=60 minutes, >=1,000,000 accepted commands, >=2 natural
post-GC samples, chronological per-run heap guard PASS, and all correctness,
recovery and resource checks PASS.
Campaign: >=5 cumulative natural samples.
```

Natural observations remain time-ordered within each run. They are not merged
into a synthetic cross-run series, and all failed artifacts remain preserved.
The remediation also fixes the previous heap-guard implementation, which
sorted heap values numerically before selecting quartiles and therefore could
not prove time-directional retained-heap growth. Raw CSV re-evaluation now
uses timestamp order through `QualificationResourceEvidenceReader`.

The first run cannot participate because it did not reach 60 minutes. The
second run may participate only after chronological guard recalculation from
its original raw resource CSV passes. Human approval authorizes exactly one
additional independent Full run after this remediation Evidence Gate passes.

### Full Qualification Attempts (Preserved Failure Evidence)

The first explicit Full lane was executed with the approved immutable
configuration. It was not retried or filtered:

```text
run: qualification-full-5346263e-ad6f-4dee-8798-92fe017311ef
acceptedCommands: 1,000,000
elapsed: 1,916,630 ms (31:56.630)
minimumDuration: 3,600,000 ms (60 minutes)
naturalPostGcSampleCount: 4 / 5 required under the original criterion
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

The approved qualification-only duration remediation was then applied in
`5a3917d`: a FULL lane remains active until both the command-count and
minimum-duration criteria are satisfied. The first run remains preserved, and
the follow-up run was executed with a new immutable run id under the same
configuration:

```text
run: qualification-full-326c125e-5c4a-45f8-9690-ad736a81ffc3
acceptedCommands: 1,000,000
elapsed: 3,609,294 ms (60:09.294)
minimumDuration: 3,600,000 ms (60 minutes)
naturalPostGcSampleCount: 4 / 5 required under the original criterion
heapGuardAssessed: false under the original single-run criterion
fullCriteriaPassed: false
listenerRebound: true
leaseReacquired: true
threadBaselineRestored: true
temporaryFileCount: 0
walFileCount: 612
walBytes: 40,019,552
```

This follow-up confirms that the duration gate now has the approved AND
semantics. Under the amended campaign criterion, Run #2 is eligible for
participation only after its original resource CSV is recalculated using the
chronological guard. No `System.gc()`, retry-until-pass behavior, sample
filtering, JVM/GC tuning, threshold relaxation, production change or
workload/configuration drift was introduced. Both raw runs remain valid
failure evidence; no production/runtime failure was observed.

## Explicitly Not Implemented

- a passing campaign (Run #2 chronological re-evaluation and one additional
  independent qualifying Full run remain outstanding);
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
duration-gate remediation CI          # 32636481415 PASS
duration-gate quick-lane CI            # 32636481409 PASS
qualification teardown fix CI          # 32640760008 PASS
qualification teardown quick lane CI   # 32640759989 PASS
campaign criterion remediation         # current checkpoint; exact-SHA CI pending
full run #1                            # preserved threshold failure
full run #2                            # preserved natural-sample failure
current report checkpoint              # this docs commit; exact-SHA CI required
verifier + docs-auditor               # PASS
```

The first standard CI for the preceding report checkpoint `af2eef0`
(`32639448577`) ended with a generic runner exit and no test annotations; its
Quick Lane (`32639448552`) passed. The failed standard run is retained as
transient CI evidence and is not represented as a passing gate. The current
report checkpoint must obtain its own exact-SHA CI PASS before TASK-037 can
advance.

For the current report checkpoint `331dbb4`, standard CI
`32639589940` also ended with a generic runner exit and no test annotations;
it is not a passing gate. Its Qualification Quick Lane
`32639589960` passed. This is retained as transient CI evidence; TASK-037
still requires a standard exact-SHA CI PASS before it can advance.

The later docs-only checkpoint `03123fa` produced CI run `32632261832` with a
generic runner exit and no test annotations; it is not used as Evidence Gate
proof. The follow-up docs checkpoint `9a8e3d2` was verified by standard CI
`32632329094 PASS` and Quick Lane `32632329103 PASS`.

The short lane proves harness composition only. A Full Qualification campaign
claim requires at least two immutable qualifying runs, each satisfying both 60
minutes and 1,000,000 accepted commands, with chronological per-run heap
evidence, no retry/filtering and complete raw artifact metadata.

## Gate

TASK-037 remains in progress because the campaign Evidence Gate is not yet
complete. Run #1 cannot participate because its duration was short; Run #2
requires chronological raw-evidence re-evaluation, and one additional
independent Full run is required. TASK-038, Phase 9 Closure, merge and
`v0.8.0-engineering-baseline` remain unauthorized until the approved campaign
criterion produces a passing immutable evidence set.
