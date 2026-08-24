# Task Plan — TASK-20260823-037

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260823-037` |
| Title | Full soak and resource lifecycle qualification |
| Status | `Completed / Archived / Human Closure Approved` |
| Owner | Human Developer |
| Implementer | Main Codex / Luna Max — only writer |
| Created | `2026-08-23` |
| Related Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Related ADR | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) |
| Phase Blueprint | [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md) |
| Authorization Mode | `Blueprint` |
| Current Stage | `v2 MEMORY_STEADY_STATE_V1 Full Campaign complete; Human TASK-037 Closure Approved` |
| Next Gate | `TASK-038 Authorized / Next; Phase 9 Closure remains unauthorized` |
| Branch | `feature/phase9-system-qualification` |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Remediation checkpoint | `e678a98` / standard CI `32683768373` PASS / Quick Lane `32683768370` PASS |
| Final evidence/status synchronization | `de3fae9` / standard CI `32692294939` PASS / Quick Lane `32692294954` PASS |

## 2. Goal

Add an explicit Full Qualification lane that drives the frozen runtime through
the public Protocol v1 TCP boundary and records one immutable soak/resource
evidence unit. The full lane requires both the approved 60-minute duration and
1,000,000 accepted commands; the short test lane is harness evidence only and
must never be reported as Full Qualification.

## 3. In Scope

- immutable Full/Test lane configuration and threshold validation;
- public-boundary full-run orchestration over the real recoverable server;
- JFR and non-invasive GC/thread/heap resource sampling;
- chronological per-run natural post-GC heap guard without `System.gc()`;
- campaign evaluator requiring two qualifying runs and five cumulative natural
  post-GC samples without cross-run timeline concatenation;
- bounded streaming command/transcript aggregation with a fixed public-probe
  suffix during the heap measurement window;
- separately versioned `MEMORY_STEADY_STATE_V1` public-boundary lane with a
  declared active-order bound;
- public Protocol v1 state tracking of maximum/final active orders with
  recovered-checkpoint reconciliation;
- manifest configuration records the actual persisted continuous prefix length;
- listener rebind, recovery lease and WAL inventory evidence;
- raw JFR, resource CSV, manifest and failure artifacts with SHA-256 hashes;
- immutable `qualification-run-manifest-v2` records with runtime provenance,
  separated configuration/comparability identities and artifact references;
- immutable `qualification-campaign-summary-v1` records referencing member
  manifest and artifact-sidecar SHA-256 values;
- focused short-lane tests for the full-run composition and threshold guards.

## 4. Out of Scope

- production source, API, format or dependency changes;
- restart/forced-termination campaign (TASK-038);
- JMH/profile optimization work (TASK-039);
- retries, filtering, deletion of failed evidence or threshold changes after a run;
- any additional Full Campaign beyond the separately approved v2 A'/B' pair;
- direct coordinator, pipeline or MatchingEngine calls from the harness;
- reconnect, deduplication, multiple sessions or request pipelining;
- WAL/Snapshot/Protocol/recovery semantic changes;
- Phase 9 Closure, merge, tag or Product Release.

## 5. Acceptance Criteria

- [ ] Full configuration rejects fewer than 1,000,000 commands, less than 60
  minutes or fewer than two per-run natural post-GC samples.
- [ ] Campaign evaluation requires at least two independently qualifying Full
  runs and at least five cumulative natural post-GC samples.
- [ ] Each run's heap guard uses timestamp order; observations from different
  runs are never merged into a synthetic time series.
- [ ] Full runner uses only the public Protocol v1 boundary and accounts for
  every accepted command/result through streaming digests and counters without
  retry or filtering.
- [ ] Full runner uses bounded streaming counters and retains no million-command
  exchange history during the heap measurement window.
- [ ] `MEMORY_STEADY_STATE_V1` is deterministic, separately versioned and keeps
  active order state within its declared bound through the public boundary; the
  observed maximum and final counts reconcile with recovery.
- [ ] A future Memory Steady-State Full run continues the bounded cycle until
  both duration and command-count gates are satisfied; it does not finish the
  minimum prefix and idle during the declared observation window. The
  qualification-only five-million-command safety bound fails closed if
  exhausted; it cannot lower either approved Full threshold.
- [ ] Existing `QualificationWorkloadV1` golden vectors and preserved Run #1/#2
  artifacts remain unchanged and non-qualifying.
- [ ] Full qualification requires both duration and command-count thresholds;
  the short lane is explicitly non-full evidence.
- [ ] Resource evidence records owned runtime threads, listener/lease state,
  temporary-file/inventory checks and chronological natural post-GC heap
  observations.
- [ ] JFR, resource CSV, manifest and failure artifacts are preserved and
  hashed; environment and immutable run configuration are recorded.
- [ ] Focused qualification tests pass without reflection, sleep-based
  correctness or production-only test seams.
- [ ] `mvn verify`, Checkstyle, `git diff --check`, frozen-path audit and
  exact-SHA CI pass.
- [ ] v2 canonical golden bytes, malformed-input and legacy-v1 rejection tests
  pass.
- [ ] PASS, FAIL and ABORTED manifests, artifact references and identity
  include/exclude behavior are tested.
- [ ] campaign summary publication is atomic, immutable, read-back validated
  and references member manifest SHA-256 values without copying run evidence.
- [x] verifier and docs-auditor return PASS after the approved v2 Full Campaign;
  Human TASK-037 Closure Approval is recorded and TASK-038 is authorized next.

Remediation Evidence Gate is PASS at `c420313`: `mvn -pl qualification -am test`
passes 36 qualification tests (2 intentionally skipped) and 195 core tests;
`mvn verify`, Checkstyle, verifier and docs-auditor pass. Standard exact-SHA CI
`32645549709` and Quick Lane `32645549694` both pass. The subsequently
approved v2 Full Campaign is recorded below.

## 6. Frozen Boundary

No file under `src/main/java/**`, `src/test/java/**`, `core/pom.xml`, existing
benchmark paths, Protocol v1, WAL v1, Snapshot v1 or recovery semantics may
change. `.vscode/` remains untouched/untracked.

## 7. Verification Commands

```text
mvn -pl qualification -am test
mvn verify
git diff --check
git diff --name-only 87abbc1..HEAD -- src/main/java src/test/java core/pom.xml
git status --short --branch
```

The short `QualificationFullRunnerTest` exercises composition quickly. A real
Full lane run remains a manual evidence unit and must not be replaced by the
short test.

At the time of the Human-approved Limited Qualification-Only Amendment, it did
not authorize a new Full run. After bounded-streaming and memory-lane
remediation passed its Evidence Gate, the separately approved campaign was
executed and is recorded below.

## 10. Current Provenance / Campaign-Summary Amendment

Human approval authorizes a qualification-only evidence amendment for
`qualification-run-manifest-v2`, separated `configurationIdentitySha256` and
`comparabilityIdentitySha256`, and `qualification-campaign-summary-v1`.
Runtime provenance is captured while the run is executing; it is not reconstructed
after completion from host state. Configuration identity excludes run ID,
timestamps, PID, paths and outcomes. Comparability identity records the approved
JDK/JVM/GC/heap/OS/filesystem/Netty/Disruptor/JFR environment dimensions.

Each v2 manifest is canonical UTF-8/LF evidence, published once with atomic
move, forced write, read-back validation and an artifact-hash sidecar. Terminal
`PASS`, `FAIL` and `ABORTED` outcomes are representable. The campaign summary
references immutable member manifest SHA-256 values and artifact-sidecar hashes,
is published once, and cannot overwrite an existing summary.

Run A and Run B remain preserved `TECHNICALLY PASS / PRESERVED /
NON-QUALIFYING` evidence. They were not backfilled with v2 fields, repackaged or
included in the approved campaign. The campaign below consists only of the two
new v2 runs.

## 8. Evidence Gate

The v2 provenance and campaign-summary Evidence Gate is PASS: implementation,
focused tests, full regression, resource/artifact semantics, frozen-path audit,
read-only reviewer PASS and exact-SHA CI all agree. Any production defect,
unexpected terminal state, timeout, digest mismatch, resource leak or
configuration drift remains failure evidence and is escalated through the
Exception Gate; it is not repaired inside this task by changing frozen
production code. The separate Human v2 Full Campaign approval and Human
TASK-037 Closure Approval have now been recorded; TASK-038 is authorized next,
while Phase 9 Closure remains separately unauthorized.

## 9. Limited Qualification-Only Amendment Status

Human approval on 2026-08-23 authorizes only `qualification/**`, qualification
workflow lane/metadata changes if required, and evidence/status documentation.
The remediation implements streaming/bounded aggregation and the versioned
`MEMORY_STEADY_STATE_V1` lane. It does not authorize production code, JVM/GC or
workload tuning, threshold relaxation, artificial GC, retry-until-pass, or an
unapproved Full Campaign. Run #1 and Run #2 remain preserved non-qualifying
evidence.

## 11. Human-Approved v2 Full Campaign Evidence

The Human Full Campaign Approval authorized exactly two new independent
`MEMORY_STEADY_STATE_V1` runs under the frozen configuration. Both runs completed
without retry, filtering, tuning or production changes and published immutable
`qualification-run-manifest-v2` plus artifact-hash sidecars at runtime.

| Run | Status | Elapsed | Accepted commands | Natural post-GC samples | Heap guard | Run ID |
| --- | --- | ---: | ---: | ---: | --- | --- |
| A' | PASS | `3,619,093 ms` | `1,784,601` | `22` | `true` | `qualification-full-bfeb2a65-aa89-42bc-a1c3-f473979d79cb` |
| B' | PASS | `3,620,413 ms` | `1,741,681` | `22` | `true` | `qualification-full-934f9d5d-d972-456f-941c-93d1cbdc5bd3` |

Both manifests also report listener rebound, recovery lease reacquisition,
thread-baseline restoration, stable WAL inventory and zero temporary files.
Their `configurationIdentitySha256` is
`dcec0edae1b407aa1ebef21514cb9de7ad3b95b8cb840969e00ca8ca2b262e29`; their
`comparabilityIdentitySha256` is
`a14d43165e967eb2b8890a6882dea1cacaaf51cfd5602b0b0fb15d2bedfbd643`.

The atomically published campaign summary is the local controlled evidence
artifact
`qualification/qualification-results-v2-campaign-20260824/qualification-campaign-summary-v1.txt`
with SHA-256
`5bf1b84b30226807d79f5a0a4950ae649c3a72a860d6d6b13edd9fa715e24112`.
It records `qualifyingRunCount=2`, `campaign.result=true` and
`cumulativeNaturalPostGcSamples=44` (required minimum `5`). The campaign
directory is intentionally ignored by `.gitignore`; raw artifacts remain
immutable and are not committed into the source baseline. Historical Run A/B
artifacts remain untouched and excluded from this campaign.

The campaign evidence was accepted by Sol High Final Campaign Closure Review
and Human TASK-037 Evidence / Closure Approval. TASK-037 is completed and
archived. TASK-038 is authorized as the next dependency-ordered task. Phase 9
Closure, merge, `v0.8.0-engineering-baseline` and Product Release remain
unauthorized.

## Human TASK-037 Closure Approval

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-24 | Human Developer | Approved | Campaign evidence, Sol High final review and known claim limitations accepted; TASK-038 authorized next. Phase 9 Closure, merge, tag and Product Release remain unauthorized. |
