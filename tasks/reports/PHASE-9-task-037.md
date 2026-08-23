# Phase 9 — TASK-20260823-037 / Full Soak and Resource Qualification

## Status

| Field | Value |
| --- | --- |
| Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Task | `TASK-20260823-037` |
| Stage | Limited Qualification-Only Remediation / Evidence Gate |
| Result | Remediation Evidence Gate PASS — Human Full Campaign approval pending; no new Full Campaign authorized |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Branch | `feature/phase9-system-qualification` |
| Implementation | Prior harness commits plus bounded remediation `82112b2`, continuous-lane fixes `2501c71`/`3b5b451`, public-state/manifest fixes `6fc813b`/`23ca7f0` |
| Duration-gate remediation | `5a3917d` |
| Standard CI | `32645549709` PASS for remediation checkpoint `c420313` |
| Quick Lane CI | `32645549694` PASS; no new Full Campaign started |
| Next Gate | Separate Human Full Campaign decision |

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
- bounded streaming command/transcript aggregation with a fixed public-probe
  suffix during the heap measurement window;
- separately versioned `MEMORY_STEADY_STATE_V1` bounded-state lane over the
  public Protocol v1 path.

The bounded remediation implementation checkpoint is `23ca7f0` (preceded by
the public-state implementation `6fc813b`, continuous-lane checkpoint
`3b5b451` and bounded aggregation checkpoint `82112b2`). Local verification
after that checkpoint reports 36 qualification tests (2 designed skips), 195
core tests, Checkstyle 0 and `mvn verify` PASS. The final remediation checkpoint
is `c420313`; standard and Quick Lane exact-SHA CI pass, and verifier/docs-auditor
are PASS. The Evidence Gate is complete; a separate Human decision is still
required before any new Full Campaign.

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
its original raw resource CSV passes. The Limited Qualification-Only Amendment
does not authorize a new Full run; it first requires bounded-streaming and
`MEMORY_STEADY_STATE_V1` Evidence Gate completion, followed by a separate
Human Full Campaign decision. Both preserved runs remain non-qualifying.

### Run #2 Chronological Re-evaluation

The original Run #2 raw artifact was re-read without changing its bytes or
manifest flags:

```text
artifact:
qualification-full-326c125e-5c4a-45f8-9690-ad736a81ffc3/resource-evidence.csv
sha256:
2b2c29606d9b96f09d4d28eb7415662aeec3e1e2809e2f7030c881b4e8ec290a
natural samples:
202657720 -> 238773760 -> 270519184 -> 314464104 bytes
heapGuardAssessed: true
heapGuardPassed: false
```

The values are monotonically increasing in timestamp order, so Run #2 cannot
participate in the qualifying campaign. Its original manifest's
`heapGuardAssessed=false` remains historical evidence from the former
single-run five-sample criterion; it was not overwritten. No Full run was
started after this result. Because neither preserved run currently qualifies,
 no new Full run is authorized until the Limited Qualification-Only Amendment
 Evidence Gate passes and a separate Human decision is recorded.

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

- a passing campaign (Run #2 chronological re-evaluation and any new qualifying
  Full runs remain outstanding and unauthorized);
- restart/forced-termination campaign (TASK-038);
- JMH/JFR performance characterization beyond required soak capture
  (TASK-039);
- production source, protocol, WAL, Snapshot or recovery changes.

## Evidence Plan

```text
mvn -pl qualification -am test       # PASS (36 qualification incl. 2 skips; 195 core)
mvn verify                            # PASS
git diff --check                      # PASS
frozen-path audit                     # PASS (0 production-path changes)
duration-gate remediation CI          # 32636481415 PASS
duration-gate quick-lane CI            # 32636481409 PASS
qualification teardown fix CI          # 32640760008 PASS
qualification teardown quick lane CI   # 32640759989 PASS
campaign criterion remediation         # 913022b / CI 32642352145 PASS
campaign quick lane                    # 913022b / CI 32642352146 PASS
Run #2 chronological re-evaluation     # FAIL; original raw artifact preserved
full run #1                            # preserved threshold failure
full run #2                            # preserved natural-sample failure
current remediation checkpoint         # c420313; standard CI 32645549709 PASS
Quick Lane                              # 32645549694 PASS
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
evidence, no retry/filtering and complete raw artifact metadata. The amendment
Evidence Gate is now PASS; a new Full Campaign can only be considered after a
separate Human approval.

### Limited Qualification-Only Remediation

Human approval on 2026-08-23 authorizes only qualification-layer changes:

- stream command/transcript counters and retain a fixed public-probe suffix;
- keep post-run WAL materialization and offline recovery outside the heap
  measurement window;
- add the separately versioned `MEMORY_STEADY_STATE_V1` bounded-state lane;
- keep a future Memory Steady-State Full run continuously processing its
  deterministic bounded cycle until the duration and command-count gates are
  satisfied, rather than idling after the minimum prefix;
- prove bounded retention, deterministic golden/digest equivalence, public-path
  integration and manifest metadata with focused tests and exact-SHA CI;
- derive the memory-lane maximum/final active-order counts from public request /
  response observations and reconcile the final count with recovered state;
- record the actual persisted command-prefix length in any continuous memory
  lane manifest.

No new Full run is authorized by this remediation. The next gate is a Human
decision on a new independent Memory Steady-State Full Campaign after the
remediation Evidence Gate passes.

## Gate

TASK-037 remediation Evidence Gate is PASS. Run #1 cannot participate because
its duration was short, and Run #2 fails the corrected chronological heap guard.
No new Full run was started. The next gate is separate Human approval for a
new `MEMORY_STEADY_STATE_V1` Full Campaign. TASK-038, Phase 9 Closure, merge and
`v0.8.0-engineering-baseline` remain unauthorized.
