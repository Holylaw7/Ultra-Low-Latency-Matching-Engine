# Task Plan — TASK-20260823-037

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260823-037` |
| Title | Full soak and resource lifecycle qualification |
| Status | `In Progress / v2 Evidence Gate PASS; Human Campaign Approval Pending` |
| Owner | Human Developer |
| Implementer | Main Codex / Luna Max — only writer |
| Created | `2026-08-23` |
| Related Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Related ADR | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) |
| Phase Blueprint | [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md) |
| Authorization Mode | `Blueprint` |
| Current Stage | `qualification-run-manifest-v2 and immutable campaign-summary remediation` |
| Next Gate | Human approval for a new v2 Full Campaign; no new run authorized |
| Branch | `feature/phase9-system-qualification` |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Remediation checkpoint | `e678a98` / standard CI `32683768373` PASS / Quick Lane `32683768370` PASS |
| Final docs synchronization | `dcfcccd` / standard CI `32684036767` PASS / Quick Lane `32684036758` PASS |

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
- any additional Full Campaign beyond the separately approved Run A/B pair or
  before provenance remediation is approved;
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
- [ ] verifier and docs-auditor return PASS before a new Full Campaign can be
  considered; TASK-038 remains locked.

Remediation Evidence Gate is PASS at `c420313`: `mvn -pl qualification -am test`
passes 36 qualification tests (2 intentionally skipped) and 195 core tests;
`mvn verify`, Checkstyle, verifier and docs-auditor pass. Standard exact-SHA CI
`32645549709` and Quick Lane `32645549694` both pass. No new Full Campaign has
been started.

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

The Human-approved Limited Qualification-Only Amendment does not authorize a
new Full run. After bounded-streaming and memory-lane remediation passes its
Evidence Gate, execution must stop for a separate Human Full Campaign decision.

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
NON-QUALIFYING` evidence. They are not backfilled with v2 fields, repackaged or
included in a new campaign. No new Full Run is authorized by this amendment.

## 8. Evidence Gate

The v2 provenance and campaign-summary Evidence Gate is PASS: implementation,
focused tests, full regression, resource/artifact semantics, frozen-path audit,
read-only reviewer PASS and exact-SHA CI all agree. Any production defect,
unexpected terminal state, timeout, digest mismatch, resource leak or
configuration drift remains failure evidence and is escalated through the
Exception Gate; it is not repaired inside this task by changing frozen
production code. A new Full Campaign still requires separate Human approval.

## 9. Limited Qualification-Only Amendment Status

Human approval on 2026-08-23 authorizes only `qualification/**`, qualification
workflow lane/metadata changes if required, and evidence/status documentation.
The remediation implements streaming/bounded aggregation and the versioned
`MEMORY_STEADY_STATE_V1` lane. It does not authorize production code, JVM/GC or
workload tuning, threshold relaxation, artificial GC, retry-until-pass, or a
new Full Campaign. Run #1 and Run #2 remain preserved non-qualifying evidence.
