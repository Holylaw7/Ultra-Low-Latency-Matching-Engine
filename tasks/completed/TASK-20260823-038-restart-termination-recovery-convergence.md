# Task Plan — TASK-20260823-038

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260823-038` |
| Title | Restart, forced termination and recovery convergence qualification |
| Status | `Completed / Evidence Gate PASS` |
| Owner | Human Developer |
| Implementer | Main Codex / Luna Max — only writer |
| Created | `2026-08-23` |
| Related Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Related ADR | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) |
| Phase Blueprint | [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md) |
| Authorization Mode | `Blueprint` |
| Current Stage | `Full 20/10 restart/termination campaign and read-only evidence review passed` |
| Next Gate | `Phase 9 Closure Approved; baseline frozen at v0.8.0-engineering-baseline` |
| Branch | `master` |
| Baseline | `v0.8.0-engineering-baseline` / `ef73f60` |
| Dependency | `TASK-037 Completed / Archived / Human Closure Approved` |

## 2. Goal

Qualify repeated graceful restart and acknowledged-boundary forced-termination
cycles through the public Protocol v1 boundary, validating WAL-authoritative
recovery convergence without changing production runtime semantics.

## 3. In Scope

- qualification-only child-process lifecycle orchestration;
- 20 graceful restart cycles;
- 10 acknowledged-boundary forced-termination cycles;
- per-cycle WAL/Snapshot/recovery convergence checks;
- Command Sequence, TradeId, EventSequence, checkpoint digest and public-probe
  comparison;
- listener-last startup and recovery-lease lifecycle evidence;
- explicit classification of ambiguous in-flight outcomes;
- immutable per-cycle manifests and aggregate evidence with SHA-256 references;
- focused tests and Phase 9 evidence documentation.

Implemented in the current checkpoint:

- qualification-only child JVM entry point with explicit `READY`/`SHUTDOWN`
  control channel;
- graceful restart and acknowledged-boundary forced-termination orchestration;
- strict WAL-prefix and offline recovery convergence after every cycle;
- immutable cycle artifacts, aggregate summary and artifact-hash sidecar;
- deterministic repeated-campaign digest comparison in focused tests.

## 4. Out of Scope

- production source, API, protocol, WAL, Snapshot or recovery semantic changes;
- reconnect, deduplication, exactly-once or client-outcome recovery claims;
- multiple active sessions or request pipelining;
- online recovery architecture changes;
- JVM/GC/workload/threshold tuning;
- JMH/profile optimization work (TASK-039);
- Phase 9 Closure, merge, tag, `v0.8.0-engineering-baseline` or Product Release.

## 5. Acceptance Criteria

- [x] 20 graceful restart cycles complete with deterministic convergence in the
  approved Full campaign.
- [x] 10 acknowledged-boundary forced-termination cycles complete with
  deterministic convergence in the approved Full campaign.
- [x] Every cycle validates strict WAL scan, recovery mode, checkpoint digest,
  Command Sequence, TradeId/EventSequence and fixed public-probe suffix.
- [x] Listener remains unbound until recovery and runtime handoff succeed; each
  child publishes `READY` only after the frozen recoverable server starts.
- [x] Recovery lease ownership and release are exercised on every cycle; all
  subsequent child starts reacquired the same WAL-backed runtime boundary.
- [x] Ambiguous in-flight outcomes remain explicitly ambiguous and are never
  upgraded to exactly-once or retry claims.
- [x] Every terminal cycle publishes immutable runtime-captured evidence with
  artifact SHA-256 references; failed evidence is preserved.
- [x] No retry-until-pass behavior, sample filtering or threshold changes.
- [x] `mvn verify`, Checkstyle 0, `git diff --check`, frozen-path audit,
  verifier/docs-auditor review and exact-SHA CI pass.

Focused implementation evidence:

- [x] A bounded 2-graceful/2-forced child-process campaign passes through the
  public Protocol v1 boundary.
- [x] Repeated bounded campaigns converge on checkpoint, transcript, probe and
  WAL command digests.
- [x] Full 20/10 campaign started only after explicit authorization; no
  retry-until-pass execution occurred.

Local verification checkpoint:

- `QualificationRestartCampaignRunnerTest`: 1 passed;
- core regression: 195 passed;
- qualification suite: 46 tests, 0 failures, 2 explicitly skipped;
- `mvn verify`: PASS; Checkstyle 0; `git diff --check`: PASS.

Full campaign evidence:

- 20 graceful and 10 acknowledged-boundary forced cycles passed;
- 30/30 cycles reported `convergencePassed=true`;
- summary SHA-256 `d18850bfdcff51722a7431e2d0679f98687577ed5cca8a574bf5c076072e3576`;
- 31 artifact sidecar entries and zero independent hash mismatches;
- technical checkpoint `a7a98cb` / Standard CI `32698925401` PASS /
  Qualification Quick Lane `32698925378` PASS;
- evidence synchronization checkpoint `da5ac1f` / Standard CI `32699178851`
  PASS / Qualification Quick Lane `32699178800` PASS;
- verifier PASS and docs-auditor PASS; TASK-039 subsequently completed its
  JMH/JFR Evidence Gate and TASK-040 completed the dependency-ordered final
  evidence reconciliation.

## 6. Frozen Boundary

No file under `src/main/java/**`, existing production tests, `core/pom.xml`,
Protocol v1, WAL v1, Snapshot v1 or recovery semantics may change. Qualification
code and evidence documentation are the only authorized write areas. `.vscode/`
remains untouched and untracked.

## 7. Evidence Rules

Each cycle is an independent evidence unit. Run configuration, workload
identity, seed, JVM/GC settings and thresholds remain frozen for the campaign.
Graceful restart and forced termination results must remain separately labeled.
Any production/runtime defect, unexplained convergence mismatch, terminal-state
violation or need to alter a frozen contract triggers the Exception Gate.

## 8. Verification Commands

```text
mvn -pl qualification -am test
mvn verify
git diff --check
git status --short --branch
```

## 9. Gate

TASK-038 completed because TASK-037 passed its campaign Evidence Gate, Sol High
Final Campaign Closure Review and Human Evidence / Closure Approval. TASK-039
subsequently completed its JMH/JFR Evidence Gate. TASK-040 completed final
evidence reconciliation with Closure Input `8e5d39d` / Standard CI
`32709188522` PASS / Quick Lane `32709188327` PASS. Phase 9 Closure, merge,
baseline tagging and Product Release remain unauthorized pending Sol High and
Human review.

## 10. Exception Gate

Stop immediately for production-file/API changes, format or recovery semantic
changes, new dependencies, reconnect/deduplication behavior, weakened
convergence criteria, altered JVM/GC/workload/threshold configuration, deleted
or filtered failure evidence, or any scope expansion.
