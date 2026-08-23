# Phase 8 Blueprint — Snapshot Checkpoint and Online Recovery Bootstrap

## 1. Executive Status

| Field | Value |
| --- | --- |
| Phase | `Phase 8 — Snapshot Checkpoint and Online Recovery Bootstrap` |
| Blueprint Status | `Approved — Human Blueprint Approval recorded` |
| Owner | Human Developer |
| Architect | Architect / Sol High |
| Created | `2026-08-22` |
| Baseline | `v0.6.0-engineering-baseline` at `6473365` |
| Proposal Branch | `docs/phase8-snapshot-recovery-blueprint` |
| Implementation Branch | `feature/phase8-snapshot-online-recovery` |
| Planned Tasks | `TASK-20260822-029` through `TASK-20260822-034` |
| Implementation | `Authorized in dependency order` |
| Current Task | `TASK-20260822-034` — Recovery benchmark, documentation and Closure Proposal |
| Next Gate | `Sol High Phase 8 Closure Review` |

## 2. Phase Objective

Add an opt-in recovery bootstrap that restores a deterministic MatchingEngine
from either the complete authoritative WAL or a strictly validated Snapshot
plus WAL tail, then hands that recovered state to the existing live durable
runtime before binding the network listener.

The selected design preserves WAL as the sole recovery authority. Snapshot is
a derivative acceleration checkpoint, not a second source of truth.

## 3. Discovery and Alternatives

### Option A — Pure WAL replay only

```text
strict WAL scan -> genesis replay -> live handoff
```

Advantages: minimal persisted formats and strongest reference semantics.
Limitations: restart cost grows with complete history and provides no state
checkpoint boundary.

### Option B — Snapshot plus WAL-tail replay only

```text
Snapshot restore -> WAL tail replay -> live handoff
```

Advantages: less matching replay work. Limitations: without retained full WAL
and a reference path, Snapshot can drift toward a second authority and make
equivalence harder to prove.

### Selected — Both explicit modes, one authority

Support `PURE_WAL` as the correctness/reference path and
`SNAPSHOT_THEN_WAL` as the normal accelerated path when a Snapshot exists. WAL
remains retained from Sequence 1 and authoritative. A corrupt selected Snapshot
fails closed; it does not silently trigger fallback.

## 4. Non-Goals

- hot or concurrent Snapshot capture;
- WAL retention, prefix deletion, compaction or segment reclamation;
- automatic fallback from a corrupt published Snapshot;
- Protocol v1 or WAL v1 format/semantic changes;
- reconnect, retry deduplication, idempotency or exactly-once behavior;
- multiple active sessions, request pipelining or new sequence arbitration;
- replication, HA, TLS/security expansion, deployment or Product Release;
- performance optimization or production RTO/availability claims.

`.vscode/` is unrelated user state and remains untouched.

## 5. ADR and Decision Matrix

| Decision | Source | Proposed Result |
| --- | --- | --- |
| D1-D14 | [`ADR-0016`](../../docs/adr/ADR-0016-snapshot-checkpoint-and-online-recovery-bootstrap.md) | Explicit pure-WAL and Snapshot-tail recovery, WAL authority, offline checkpoint, strict format/publication/corruption, listener-last handoff and narrow additive APIs |

All decisions and file exceptions were approved by the Human Phase 8 Blueprint
Approval recorded below. Execution remains limited to the listed Tasks and
Evidence Gates.

## 6. Architecture and Responsibility Boundaries

```text
Snapshot generation (offline, exclusive recovery lease held)

closed WAL v1
    -> strict reader
    -> genesis CommandWalReplayer
    -> canonical MatchingEngineCheckpoint
    -> Snapshot v1 codec/store
    -> immutable published snapshot

Online restart

startup
    -> exclusive recovery ownership
    -> final-torn-tail repair when explicitly allowed
    -> strict complete WAL scan
    -> recovery planner
         ├─ PURE_WAL
         └─ SNAPSHOT_THEN_WAL
    -> recovered MatchingEngine
    -> exact counter/sequence convergence
    -> recovered-engine MatchingEnginePipeline
    -> seeded DurableCommandCoordinator
    -> durable Netty runtime
    -> bind listener last
```

Ownership remains:

- WAL commands are authoritative.
- Snapshot codec/store owns only derived checkpoint files.
- Recovery bootstrap owns startup sequencing and resources until handoff.
- Pipeline consumer owns the recovered MatchingEngine after start.
- Netty event loop remains the one live producer and request-correlation owner.

Exclusive recovery/runtime ownership uses a fixed `recovery.lock` file in the
WAL directory and a JDK `FileLock` held from before repair/scan through live
runtime shutdown. Failure to acquire it fails closed. The file has no recovery
semantics and may remain after shutdown.

## 7. Frozen Boundaries and Approved Exceptions Requested

The tag `v0.6.0-engineering-baseline` is immutable. These semantics remain
frozen with no exception:

```text
WAL v1 bytes, strict scan and corruption policy
Protocol v1 bytes
Domain identifiers and Sequence domains
matching outcomes and price-time priority
Phase 7 WAL-before-execute and fail-stop semantics
single-session / one-request-in-flight topology
```

Phase 8 requests Human approval for only these additive changes:

1. OrderBook canonical checkpoint export/restore;
2. MatchingEngine checkpoint export/restore;
3. MatchingEnginePipeline construction from a recovered engine;
4. DurableCommandCoordinator initialization with a validated next Command
   Sequence; and
5. new `persistence/snapshot`, `recovery/online`, `integration/recovery` and
   `network/netty/recovery` packages.

Existing constructor behavior and historical launchers must remain compatible.

## 8. Snapshot v1 Contract

The exact binary layout, strict validation and corruption rules are frozen by
ADR-0016 D5-D7. Key invariants are:

- big-endian fixed 128-byte header;
- fixed 48-byte canonical active-order records;
- exact file size `132 + activeOrderCount * 48`;
- SHA-256 binding to WAL records `1..N`;
- SHA-256 canonical checkpoint digest covering counters and state payload;
- CRC32C across header and payload;
- configuration limits checked before allocation;
- immutable final files, required same-directory atomic publication;
- latest selected published corruption/mismatch fails closed.

The WAL-prefix digest is the SHA-256 of the exact WAL v1 record envelope bytes
(`total record length` through record CRC32C) in Command Sequence order,
excluding segment headers and unused segment bytes. The canonical checkpoint
digest hashes, in big-endian order, checkpoint Sequence, next TradeId, next
EventSequence, active-order count, record length and the exact concatenated
48-byte order payload. Snapshot Sequence must be at least 1; an empty WAL uses
genesis recovery without a Snapshot. The file name suffix and header checkpoint
Sequence must match.

## 9. Task Breakdown and Dependency Order

| Order | Task | Goal | Dependency | Planned report |
| ---: | --- | --- | --- | --- |
| 1 | `TASK-20260822-029` | Canonical engine checkpoint export/restore | Baseline | Completed / Evidence Gate PASS at `66fc9d2` / CI `32577713667`; `tasks/reports/PHASE-8-task-029.md` |
| 2 | `TASK-20260822-030` | Snapshot v1 codec and atomic store | TASK-029 | Completed / Evidence Gate PASS at `6907391` / CI `32579065372`; `tasks/reports/PHASE-8-task-030.md` |
| 3 | `TASK-20260822-031` | Recovery planner and replay executor | TASK-030 | Completed / Evidence Gate PASS at `eaed8b8` / CI `32580018903`; `tasks/reports/PHASE-8-task-031.md` |
| 4 | `TASK-20260822-032` | Recoverable live runtime handoff | TASK-031 | Completed / Evidence Gate PASS at `22568e6` / CI `32613235358`; `tasks/reports/PHASE-8-task-032.md` |
| 5 | `TASK-20260822-033` | Crash, corruption and determinism verification | TASK-032 | Completed / Evidence Gate PASS at `eff5955` / CI `32614610701`; `tasks/reports/PHASE-8-task-033.md` |
| 6 | `TASK-20260822-034` | Recovery benchmark, documentation and Closure Proposal | TASK-033 | Completed / Evidence Gate PASS at `9835624` / CI `32616029460`; docs/evidence sync `030545a` / CI `32616620054`; `tasks/reports/PHASE-8-task-034.md` |

After Blueprint approval, Tasks may execute continuously in this exact order.
Each preceding Task requires its full Evidence Gate and exact-SHA CI PASS. No
routine intermediate Human approval is required unless an Exception Gate fires.

## 10. Authorized File Matrix After Approval

| Task | Authorized paths | Boundary |
| --- | --- | --- |
| 029 | additive checkpoint classes; narrowly listed `orderbook`/`engine` files; focused tests | export/restore only, no matching algorithm change |
| 030 | new `persistence/snapshot/**` and tests | format, codec, strict reader, atomic store, offline generator |
| 031 | new `recovery/online/**` and tests | explicit recovery modes, strict planning, restore/tail replay |
| 032 | additive `MatchingEnginePipeline` and `DurableCommandCoordinator` construction; new recovery integration/network packages and tests | listener-last recovered live handoff only; Exception Gate additionally authorized the additive externally-owned-lease overload in `RecoveryPlanner.java` |
| 033 | Phase 8 recovery tests/fixtures/reports | dynamic crash/corruption/equivalence evidence only |
| 034 | benchmark module, recovery benchmark docs, task/ADR/Blueprint/README/context | measurement and Closure preparation only |

TASK-029 may modify only the specific internal production files required for
canonical export/restore:

```text
src/main/java/com/ultralatency/matching/orderbook/OrderBook.java
src/main/java/com/ultralatency/matching/orderbook/SideBook.java
src/main/java/com/ultralatency/matching/orderbook/PriceLevel.java
src/main/java/com/ultralatency/matching/orderbook/OrderNode.java
src/main/java/com/ultralatency/matching/engine/MatchingEngine.java
```

`domain/Order.java` and all Domain value semantics remain unchanged. TASK-032
may modify only:

```text
src/main/java/com/ultralatency/matching/pipeline/MatchingEnginePipeline.java
src/main/java/com/ultralatency/matching/integration/durable/DurableCommandCoordinator.java
```

for the approved additive constructors/factories. The TASK-032 Exception Gate
also authorized only the additive `RecoveryPlanner.recover(mode, lease)`
ownership overload; any other existing production-file change triggers the
Exception Gate.

## 11. Acceptance Criteria

### Snapshot correctness

- [x] Snapshot v1 golden bytes match the exact ADR layout.
- [x] Canonical engine checkpoint round-trips active state, original/remaining
  quantity, bid/ask priority, FIFO order and engine counters.
- [x] Malformed checkpoint or Snapshot fails without partial mutation.
- [x] Snapshot WAL-prefix and canonical checkpoint digests validate exactly.
- [x] Offline generation holds the exclusive recovery lease from before scan
  through atomic publication and rejects an unavailable lease or changed WAL
  segment inventory/file size.

### Recovery correctness

- [x] Empty WAL starts from genesis.
- [x] Non-empty WAL recovers through explicitly selected `PURE_WAL`.
- [x] Snapshot N plus WAL tail `N+1..M` restores the same final checkpoint as
  pure WAL replay.
- [x] Snapshot at WAL end restores without duplicate application.
- [x] First live command after recovery uses `WAL end + 1`.
- [x] Recovery replay outputs are not sent to a client.

### Failure and corruption

- [x] Only WAL v1's approved final torn-tail repair may modify WAL; TASK-031
  adds no WAL mutation or truncation.
- [x] Hard WAL corruption, gaps, invalid Snapshot, checksum/digest mismatch,
  Snapshot newer than WAL and incompatible formats fail closed.
- [x] Temporary Snapshot files are ignored; published corruption is not.
- [x] Any startup/handoff failure occurs before listener bind, preserves first
  cause and rejects later admission.

### Determinism and identity

- [x] Pure WAL and Snapshot-tail recovery produce an equal complete canonical
  checkpoint digest and fixed public probe.
- [x] For Snapshot N and WAL end M, ordered EngineResult, TradeId and
  EventSequence are equal for the common replay suffix `N+1..M`; prefix results
  `1..N` are not reconstructed or emitted by Snapshot restore.
- [x] RequestId restarts at 1 for a new TCP session; Command Sequence, TradeId
  and EventSequence continue exactly.
- [x] RequestId, Command Sequence, WAL position, ring sequence, EventSequence
  and TradeId remain distinct.

### Compatibility and scope

- [x] WAL v1 and Protocol v1 bytes/semantics remain unchanged.
- [x] Matching outcomes and price-time priority remain unchanged.
- [x] Existing constructors and Phase 2-7 tests remain compatible.
- [x] No new critical dependency, hot Snapshot, WAL retention, reconnect,
  dedup, multiple session or production release behavior is added.

## 12. Test and Evidence Strategy

| Layer | Required evidence | Pass condition |
| --- | --- | --- |
| Checkpoint unit | canonical export/restore and malformed values | exact round-trip; no partial mutation |
| Format unit | golden bytes, length/count/limit/reserved/digest/CRC cases | strict accept/reject matrix |
| Store integration | temp write, force, read-back, atomic move, no overwrite | only valid immutable final files selected |
| Snapshot ownership | lease contention and changed WAL inventory/size | generator rejects concurrent/non-quiescent input |
| Recovery integration | empty, pure WAL, Snapshot+tail, at-tail, repeated restart | exact next sequence and final state |
| Corruption/crash | all ADR crash windows and strict failure cases | fail closed or approved final-tail repair only |
| Live handoff | listener-last, first Submit/Cancel, new RequestId domain | no pre-recovery admission; exact continuation |
| Determinism | pure vs Snapshot-tail plus fixed public probe | ordered results/counters/digests equal |
| Regression | focused suites, `mvn verify`, Checkstyle, diff audits | all PASS |
| CI | exact commit status | required workflow PASS |

Tests must use deterministic barriers/futures where concurrency is relevant.
Reflection, `Thread.sleep` as a correctness oracle and production-only test
seams are prohibited.

## 13. Benchmark and Evidence Plan

TASK-034 measures separate boundaries:

- pure-WAL genesis replay;
- Snapshot decode and state restore;
- Snapshot plus WAL-tail recovery;
- offline Snapshot creation; and
- full process bootstrap to listener-ready.

Dimensions include total commands, active orders, Snapshot Sequence, tail
length, segment count and total WAL bytes. Record Java/JVM/GC, CPU, OS,
filesystem/storage, heap, forks/warmup/measurement, throughput where meaningful,
SampleTime P50/P95/P99/P999 and allocation/GC evidence.

Reports must call these component/local-host measurements. They cannot claim
production RTO, availability SLA, power-loss safety, client outcome recovery or
production readiness. Results cannot alter correctness defaults.

## 14. Evidence Gate Per Task

Every Task requires:

```text
focused tests
mvn verify
Checkstyle 0
git diff --check
approved-path and frozen-path audit
logical commit
normal push
exact-SHA CI PASS
Exception Gate check
Task/report synchronization
```

TASK-033 additionally requires the repeated recovery/crash matrix and read-only
`verifier` PASS. TASK-034 additionally requires read-only `verifier`,
`benchmark-reviewer` and `docs-auditor` PASS.

## 15. Risks and Limitations

- Full WAL is retained and scanned; Snapshot reduces matching replay work but
  does not claim to remove all WAL I/O.
- Offline Snapshot generation requires a closed WAL and does not establish a
  live checkpoint latency.
- The lease is cooperative among Phase 8 components. A legacy writer that does
  not participate must be stopped before its WAL is used as offline input.
- Published Snapshot corruption fails startup by design.
- Client outcome for a durable pre-crash command remains ambiguous.
- `force(true)` and `ATOMIC_MOVE` do not prove hardware power-loss safety.
- File-lock and filesystem behavior are host-specific and require explicit
  environment evidence.
- Checksums/digests detect mismatch but do not authenticate Snapshot/WAL files
  against a malicious filesystem writer.

## 16. Rollback and Compatibility

- Use a new opt-in recovery launcher; the Phase 7 fresh-WAL launcher remains
  compatible.
- Revert TASK-034 through TASK-029 in reverse order.
- Snapshot files are additive; rollback never deletes or rewrites WAL.
- Do not run a partially completed Phase 8 composition as an online service.
- Any Snapshot format change after approval requires a new ADR/version.

## 17. Git and CI Strategy

- Proposal branch: `docs/phase8-snapshot-recovery-blueprint`.
- Planned implementation branch after Human approval:
  `feature/phase8-snapshot-online-recovery`.
- One logical implementation/evidence checkpoint per Task.
- Normal pushes and exact-SHA CI; no force push, squash or history rewrite.
- Merge to master, Task archival and tag require separate Human Phase 8 Closure
  Approval.
- Candidate tag after verified master merge:
  `v0.7.0-engineering-baseline`.

## 18. Documentation Plan

Synchronize ADR-0016, this Blueprint, TASK-029 through TASK-034 plans/reports,
Snapshot/recovery architecture and benchmark docs, README and
`.codex/AGENT_CONTEXT.md`. Record exact commits, CI runs, test counts, known
limitations and the current authorization gate without stronger claims.

## 19. Exception Gates

Stop for Human review if implementation requires:

- WAL v1 or Protocol v1 byte/semantic changes;
- WAL deletion, retention, compaction or unapproved truncation;
- hot/concurrent Snapshot capture;
- Snapshot authority or automatic corruption fallback;
- listener bind or request admission before recovery completes;
- counters synthesized without validated WAL/Snapshot evidence;
- matching semantic changes;
- a new producer, session, queue, retry or thread-ownership model;
- reconnect, deduplication, idempotency or exactly-once behavior;
- a new critical dependency;
- a production-only test seam, reflection or sleep-based correctness;
- weakened corruption/digest/determinism criteria;
- performance-driven semantic/default changes; or
- any unlisted existing production file/API/scope change.

## 20. Closure Plan

After TASK-034 Evidence Gate PASS:

```text
STOP
    -> Sol High Phase 8 Closure Review
    -> Human Phase 8 Closure Approval
    -> normal --no-ff merge to master
    -> local master verification and approved-path audit
    -> master exact-SHA CI PASS
    -> annotated v0.7.0-engineering-baseline
    -> tag CI PASS
    -> TASK-029..034 archive and final status sync
    -> final exact-SHA CI PASS
    -> Phase 8 Frozen
```

Phase 9 and Product Release remain unauthorized.

## 21. Human Phase 8 Blueprint Approval

| Date | Reviewer | Decision | Approved ADRs / Tasks | Constraints |
| --- | --- | --- | --- | --- |
| 2026-08-22 | Human Developer | Approved | ADR-0016 D1-D14; TASK-029..034 | Strict dependency order; per-Task Evidence Gates and Exception Gates; merge/tag/Phase 9/Product Release remain unauthorized |
| 2026-08-22 | Evidence Gate | TASK-029 PASS | Canonical checkpoint export/restore, focused tests, full verification and exact-SHA CI `32577713667` accepted; TASK-030 authorized | No Exception Gate |
| 2026-08-22 | Evidence Gate | TASK-030 PASS | Snapshot v1 codec/store, strict validation, atomic publication, offline generator, lease/inventory checks and focused/full verification accepted at `6907391` / CI `32579065372`; TASK-031 is next | No Exception Gate |
| 2026-08-22 | Evidence Gate | TASK-031 PASS | Explicit PURE_WAL and SNAPSHOT_THEN_WAL offline recovery, strict prefix binding, WAL-tail replay, convergence and fail-closed matrix accepted at `eaed8b8` / CI `32580018903`; status sync `ce3f22b` / CI `32580536044` PASS; TASK-032 is next | No Exception Gate |
| 2026-08-23 | Human Exception Gate | TASK-032 limited remediation approved | Additive externally-owned `RecoveryLease` overload in `RecoveryPlanner.java`; runtime ownership remains continuous from pre-scan through shutdown. No format, protocol, recovery-mode, retry or session changes. | Restricted to lease ownership/handoff |
| 2026-08-23 | Evidence Gate | TASK-032 PASS | Listener-last handoff, sequence convergence, first live Submit/Cancel, RequestId reset, failure-before-bind and continuous lease ownership accepted at `22568e6` / CI `32613235358`; report and task archived. | No Exception Gate remains |
| 2026-08-23 | Evidence Gate | TASK-033 PASS | Repeated PURE_WAL/SNAPSHOT_THEN_WAL convergence, fixed public probe, strict corruption/temp-file behavior, listener-last failure and first-live-command evidence accepted at `eff5955` / CI `32614610701`; force/move fault injection remains an explicit limitation with no production seam. TASK-034 followed under the approved dependency gate. | No Exception Gate remains |
| 2026-08-23 | Evidence Gate | TASK-034 PASS | RecoveryBenchmark full 20-case matrix, heap metadata, JMH GC-profiler allocation evidence, environment/workload metadata, Throughput `ops/ms`, SampleTime P50/P95/P99/P999 evidence and component claim limits accepted at `9835624` / CI `32616029460`; verifier, benchmark-reviewer and docs-auditor PASS; final docs sync `030545a` / CI `32616620054` PASS. | Merge/tag/Phase 9 remain unauthorized |

```text
Phase 8 Discovery: Completed
ADR-0016: Approved
Complete Blueprint: Approved
TASK-029: Completed / Evidence Gate PASS
TASK-030: Completed / Evidence Gate PASS at `6907391` / CI `32579065372`
TASK-031: Completed / Evidence Gate PASS at `eaed8b8` / CI `32580018903`
TASK-032: Completed / Evidence Gate PASS at `22568e6` / CI `32613235358`
TASK-033: Completed / Evidence Gate PASS
TASK-034: Completed / Evidence Gate PASS at `9835624` / CI `32616029460`; docs/evidence sync `030545a` / CI `32616620054` PASS
Implementation: Authorized
Merge / v0.7.0-engineering-baseline: Not Authorized
Next Gate: Sol High Phase 8 Closure Review after final Evidence Gate
```
