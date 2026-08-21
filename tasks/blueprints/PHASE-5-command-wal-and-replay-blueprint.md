# Phase 5 Blueprint — Command WAL and Deterministic Replay Foundation

## 1. Executive Status

| Field | Value |
| --- | --- |
| Phase | `Phase 5 — Command WAL and Deterministic Replay Foundation` |
| Blueprint Status | `Approved — Implementation Authorized` |
| Owner | Human Developer |
| Architect | Codex / Sol high |
| Created | `2026-08-21` |
| Updated | `2026-08-21` |
| Baseline | `v0.3.0-engineering-baseline` -> `d28abbe` |
| Proposal Base HEAD | `fbcbe53` |
| Blueprint Branch | `docs/phase5-command-wal-replay-blueprint` |
| Planned Tasks | `TASK-20260821-014` through `TASK-20260821-018` |
| Next Gate | `Human Phase 5 Closure Approval` |

## 2. Discovery and Phase Goal

### Discovery Result

Phase 5 selects Command WAL and deterministic offline replay before the
Network/Protocol phase.

The decision follows the already accepted architecture:

- ADR-0011 makes the command stream the future replay authority;
- Phase 4 provides a bounded execution pipeline but explicitly has no crash
  recovery;
- no network protocol exists, so persistence can remain independent of wire
  framing, connection identity and retries;
- a versioned format, durability boundary and corruption policy must be frozen
  before a WAL implementation can be credible.

### Phase Goal

Deliver a correctness-first, project-owned persistence component that can:

1. encode the current immutable engine commands into a stable binary format;
2. append them in exact sequence to bounded segments with explicit durability;
3. detect invalid headers, lengths, checksums, segments and sequence order;
4. reopen after an incomplete final write using a narrowly defined torn-tail
   policy;
5. replay an intact closed WAL into a genesis `MatchingEngine`;
6. prove ordered result and future-behavior equivalence;
7. record honest component-level append and replay evidence.

The Phase produces a persistence/replay engineering baseline, not an
operationally recoverable product.

## 3. Non-Goals and Frozen Boundaries

### Explicit Non-Goals

- live Phase 4 pipeline/WAL integration;
- durable client or producer acknowledgement semantics;
- Network, Netty, decoder or external binary protocol;
- Snapshot format, snapshot load or incremental restore;
- online restart orchestration or automatic service recovery;
- replication, high availability, quorum or consensus;
- multi-symbol partitioning or WAL routing;
- Market Order or new command type;
- output/event WAL, database, MQ or egress publication;
- compression, encryption, direct-I/O, memory mapping or off-heap buffers;
- thread affinity, custom filesystem provider or production tuning;
- product Release or production durability claim.

### Frozen Baseline Boundaries

The annotated `v0.3.0-engineering-baseline` remains immutable. Phase 5 has zero
permitted modifications to existing production files under:

```text
src/main/java/com/ultralatency/matching/domain/**
src/main/java/com/ultralatency/matching/orderbook/**
src/main/java/com/ultralatency/matching/engine/**
src/main/java/com/ultralatency/matching/pipeline/**
```

New production code is limited to:

```text
src/main/java/com/ultralatency/matching/persistence/wal/**
src/main/java/com/ultralatency/matching/recovery/**
```

No Phase 5 evidence may be described as full state restore, end-to-end durable
latency, product recovery time, production throughput or a power-loss
guarantee beyond the documented Java/OS file-force boundary.

## 4. Current State and Dependencies

### Verified Baseline

| Capability | State |
| --- | --- |
| Domain and structural matching | frozen in Phase 1/2 |
| Synchronous MatchingEngine | frozen at `v0.2.0-engineering-baseline` |
| Deterministic ordered EngineResult | implemented and verified |
| Bounded SPSC pipeline | frozen at `v0.3.0-engineering-baseline` |
| Command types | submit-limit and cancel only |
| Command Sequence | upstream-owned, positive, contiguous |
| TradeId / EventSequence | engine-owned, derived during replay |
| WAL / Replay / Snapshot | not implemented |
| Current full regression | 83 tests / Checkstyle 0 / Phase 4 CI PASS |

### Existing Decisions

| Decision | Current effect |
| --- | --- |
| ADR-0005 R1-R6 | command Sequence, TradeId and EventSequence are separate domains |
| ADR-0011 D6 | command log is canonical replay input; outputs are derived |
| ADR-0012 | Phase 4 pipeline is fail-stop and contains no persistence I/O |
| ADR-0004 | high-level sequential WAL baseline; superseded by ADR-0013 only after approval |

### Required Platform

- Java 21 `FileChannel`, `ByteBuffer`, `CRC32C` and `MessageDigest`;
- current Maven/JUnit/Checkstyle/CI toolchain;
- temporary test directories owned by each test;
- no new runtime dependency.

## 5. ADR Set and Decision Matrix

Required draft:

- [`ADR-0013-command-wal-and-deterministic-replay.md`](../../docs/adr/ADR-0013-command-wal-and-deterministic-replay.md)

| Decision ID | ADR | Proposed Decision | Scope / Constraint | Approval Result |
| --- | --- | --- | --- | --- |
| D1 | ADR-0013 | WAL/replay foundation before Network | persistence not derived from wire protocol | Approved |
| D2 | ADR-0013 | commands are authoritative; results derived | no Trade/Execution WAL | Approved |
| D3 | ADR-0013 | exact version-1 big-endian segmented format | only submit-limit and cancel records | Approved |
| D4 | ADR-0013 | strict format, CRC32C and exact sequence validation | no skip/sort/best-effort decode | Approved |
| D5 | ADR-0013 | synchronous caller-owned single writer | no internal thread, queue or concurrent writer | Approved |
| D6 | ADR-0013 | `SYNC_EACH_APPEND` default, `BUFFERED` evidence-only | benchmark cannot change default | Approved |
| D7 | ADR-0013 | explicit final torn-tail truncation only | complete-record corruption always fails closed | Approved |
| D8 | ADR-0013 | strict closed-WAL replay into genesis engine | ordered transcript/digest/probe equality | Approved |
| D9 | ADR-0013 | existing core/pipeline production files frozen | no live durable integration | Approved |
| D10 | ADR-0013 | JDK-only; Snapshot/Recovery/Network deferred | no product recovery claim | Approved |

Human Blueprint Approval accepted ADR-0013 D1-D10 and TASK-014 through
TASK-018 together. Implementation is authorized only in strict dependency order
and remains subject to every automated Evidence Gate and Exception Gate.

## 6. Target Architecture

```text
Valid EngineCommand stream
    -> WalCommandCodec
    -> CommandWalWriter
         -> segment header
         -> bounded command records + CRC32C
         -> force according to explicit durability mode
    -> closed segmented WAL
    -> CommandWalReader (strict scan)
    -> CommandWalReplayer
    -> new genesis MatchingEngine
    -> ordered EngineResult transcript
    -> SHA-256 replay transcript digest
    -> fixed public-command probe suffix
```

The live Phase 4 path remains unchanged and separate:

```text
Caller -> MatchingEnginePipeline -> MatchingEngine -> EngineResultHandler
```

### Responsibility Map

| Component | Owns | Must Not Own |
| --- | --- | --- |
| `WalCommandCodec` | exact version-1 bytes and validation | file lifecycle, engine calls |
| `WalConfiguration` | directory, segment bound, durability mode | global/default mutable state |
| `CommandWalWriter` | single-writer append, rotation, force, explicit reopen repair | thread, queue, matching logic |
| `CommandWalReader` | strict closed-file scan and ordered command reconstruction | silent repair, replay policy |
| `CommandWalReplayer` | genesis engine application and ordered transcript | file mutation, snapshot restore |
| `ReplayTranscriptDigest` | canonical SHA-256 over ordered public result values | internal object identity/state claim |
| Frozen engine/pipeline | existing matching and execution behavior | WAL format or file I/O |

### Persistence Format

ADR-0013 D3 is normative. The implementation must use exact golden-byte tests;
Java record/class layout is never the persistence format.

### Sequence Domains

```text
Command Sequence       = logical WAL and replay order
Segment Id / Offset    = physical storage metadata
Disruptor ring sequence= unchanged infrastructure metadata
TradeId/EventSequence  = regenerated MatchingEngine output
```

No conversion among these domains is permitted.

### Failure Semantics

- writer reports an append only after the full record is written and the
  configured durability action succeeds;
- a failed write, force or rotation is never reported as a successful logical
  append and makes the writer terminal; complete bytes may nevertheless exist
  physically, so strict scan/reopen determines the valid persisted boundary;
- a write/force/rotation failure makes that writer instance terminal;
- strict reader failure includes segment and byte offset;
- only an incomplete last physical record is an eligible torn tail;
- checksum mismatch or any earlier corruption is never auto-truncated;
- replay fails on decode, sequence or engine rejection and never skips input;
- partial Phase work can be rolled back without touching baseline tags or
  migrating user data because Phase 5 has no production deployment.

## 7. Task Decomposition

| Order | Task | Goal | Depends On | Authorized Scope | Report |
| ---: | --- | --- | --- | --- | --- |
| 1 | [`TASK-014`](../active/TASK-20260821-014-phase5-wal-format-codec.md) | freeze format types, configuration and exact codec | Blueprint approval | new WAL contracts/codec/tests | `tasks/reports/PHASE-5-command-wal-replay.md` |
| 2 | [`TASK-015`](../active/TASK-20260821-015-phase5-segmented-wal-storage.md) | implement synchronous segmented writer/reader/reopen | TASK-014 evidence | new WAL storage implementation/tests | same cumulative report |
| 3 | [`TASK-016`](../active/TASK-20260821-016-phase5-deterministic-replay.md) | replay closed WAL through genesis engine and digest transcript | TASK-015 evidence | new recovery package/tests | same cumulative report |
| 4 | [`TASK-017`](../active/TASK-20260821-017-phase5-corruption-recovery-verification.md) | prove corruption, torn-tail, sequence and failure boundaries | TASK-016 evidence | tests/fixtures only; fixes within new packages | same cumulative report |
| 5 | [`TASK-018`](../active/TASK-20260821-018-phase5-wal-benchmark-docs.md) | record component append/replay evidence and synchronize docs | TASK-017 evidence | benchmark/docs/evidence only | cumulative report plus Closure Report |

Dependencies are strict. Each completed Task must pass its evidence gate and
record an exact-SHA CI result before the next Task begins. No routine Human
approval is required between approved Tasks unless an Exception Gate fires.

## 8. Stage Authorization Matrix

| Task / Stage | Files or Modules | Deliverable | Evidence Gate | Manual Gate? |
| --- | --- | --- | --- | --- |
| TASK-014 Format/Foundation | new WAL contracts/codec and focused tests | exact version-1 bytes and validated configuration | golden/round-trip/invalid tests + `mvn verify` + frozen diff + CI | No |
| TASK-015 Storage | new WAL writer/reader/reopen tests | bounded segments, force policy and strict scan | filesystem integration matrix + repeated tests + full regression + CI | No |
| TASK-016 Replay | new recovery types/tests | ordered genesis replay, digest and probes | direct-vs-replay equality + full regression + CI | No |
| TASK-017 Failure Verification | WAL/recovery tests and deterministic fixtures | crash/corruption/torn-tail/sequence evidence | repeated failure matrix + diff audit + CI | No |
| TASK-018 Benchmark | benchmark module and recovery benchmark report | component append/replay measurements | JMH smoke/full run + result review + CI | No |
| TASK-018 Documentation | ADR/architecture/README/context/report | synchronized evidence and closure proposal | link/scope/diff checks + full verify + CI | No |
| Phase Closure | Closure report only | reviewable freeze proposal | all Task evidence + exact-SHA CI | Yes |

## 9. Phase Acceptance Criteria and Invariants

### Functional / Correctness

- [ ] submit-limit and cancel commands have exact stable version-1 bytes;
- [ ] encode/decode round trips preserve public value equality;
- [ ] writer accepts only exact-next positive command sequence;
- [ ] segment rotation never splits a record;
- [ ] reopen resumes from the last fully validated record;
- [ ] strict reader reconstructs all commands across ordered segments;
- [ ] append after close or terminal failure is rejected;
- [ ] exclusive writer ownership prevents concurrent writers;
- [ ] no record or command reference is silently dropped or substituted.

### Determinism / Ordering

- [ ] identical commands produce byte-identical WAL contents for equal
  configuration except where the segment-size parameter intentionally changes
  physical boundaries;
- [ ] read order equals command Sequence order and physical record order;
- [ ] at least 1,024 fixed commands produce equal direct and replayed ordered
  `EngineResult` values;
- [ ] TradeId, EventSequence and result collection order are significant;
- [ ] canonical replay transcript digests are equal across independent runs;
- [ ] a fixed public command suffix produces equal results after direct and WAL
  replay execution;
- [ ] no clock, random identity, hash iteration or scheduler order defines
  persisted or replayed behavior.

### Failure / Recovery

- [ ] invalid magic/version/header/length/type/flags/reserved bytes fail closed;
- [ ] CRC mismatch reports the exact segment/offset and is never auto-repaired;
- [ ] segment gaps, duplicate segments and command-sequence gaps fail closed;
- [ ] incomplete final length/body/checksum is detected as a torn tail;
- [ ] explicit reopen truncates only the final torn tail to the last validated
  position and preserves all prior valid records;
- [ ] an earlier torn/corrupt record is never truncated as a recovery shortcut;
- [ ] writer I/O or force failure is terminal and cannot be reported as success;
- [ ] a failed force is treated as logical append failure, while physical tail
  state is determined only by strict scan/reopen;
- [ ] a well-formed command rejected by MatchingEngine makes replay fail at its
  command Sequence without applying later records;
- [ ] Snapshot restore and online recovery are explicitly not claimed.

### Compatibility / Boundary

- [ ] frozen Domain, OrderBook, Engine and Pipeline production diff is zero;
- [ ] no runtime dependency is added;
- [ ] persisted bytes do not contain Java class names or serialization metadata;
- [ ] no LMAX type, ring sequence or network concept enters the WAL format;
- [ ] `SYNC_EACH_APPEND` remains the default regardless of benchmark result;
- [ ] no current public API or matching/event-ordering semantic changes;
- [ ] v0.3.0 tag is not moved or modified.

### Completion Evidence

- [x] TASK-014 through TASK-018 completed in dependency order;
- [x] focused, integration, replay and corruption suites pass repeatedly;
- [x] full `mvn verify` and Checkstyle pass;
- [x] exact-SHA remote CI passes at each Task checkpoint;
- [x] benchmark method/results/limitations are committed;
- [x] frozen-path diff audit equals zero;
- [x] documentation and `AGENT_CONTEXT` are synchronized;
- [ ] Phase Closure Report receives separate Human approval.

## 10. Verification Strategy

| Layer | Required Evidence | Command / Method | Pass Condition |
| --- | --- | --- | --- |
| Codec unit | golden bytes, round-trip, every invalid code/length/version | focused JUnit tests | exact bytes and explicit failures |
| Configuration unit | directory/segment/durability validation | focused JUnit tests | invalid values rejected before I/O |
| Storage integration | append, rotate, reopen, lock, close | `@TempDir` filesystem tests | exact files/order/positions |
| Determinism / Replay | direct versus two independent WAL replays | fixed >=1,024-command stream | ordered results/digests/probes equal |
| Failure / Recovery | truncation at every byte boundary, CRC/header/sequence corruption | deterministic parameterized tests | only final torn tail recoverable |
| Repeatability | concurrency-free storage/replay suite repeated | Surefire repeat command defined by TASK-017 | zero flaky outcome |
| Static / Build | all modules and Checkstyle | `mvn verify` | build success / 0 violations |
| Boundary | tag-to-HEAD path audit | `git diff --name-only v0.3.0-engineering-baseline...HEAD -- <frozen paths>` | zero output |
| Diff | whitespace and scope review | `git diff --check`, staged diff review | no error/unrelated file |
| CI | exact pushed commit | GitHub Actions | completed / success |

Tests must not use reflection, production test hooks, wall-clock sleeps or
arbitrary scheduling assumptions. File fixtures must be generated in isolated
temporary directories or committed as small exact golden vectors.

## 11. Benchmark and Evidence Strategy

TASK-018 records component evidence only after correctness gates pass.

### Required Comparisons

| Benchmark | Parameters | Meaning |
| --- | --- | --- |
| `walAppend` | `SYNC_EACH_APPEND`, `BUFFERED`; submit/cancel mix; segment sizes | component append cost |
| `walReplay` | command count and segment size | closed-WAL decode + engine replay cost |
| `walScan` | command count and segment size | strict validation/decode cost without engine |

### Required Evidence

- Java/JDK, OS, CPU, storage volume/type as observable, JVM args and GC;
- JMH version, warmup, measurement, forks, threads and dataset;
- throughput and sample-time percentiles where meaningful;
- total bytes, commands, segments and durability mode;
- allocation/GC observation when reproducible;
- raw-result local path, generation command and committed summary;
- explicit filesystem-cache and force-semantics limitations.

### Claim Boundaries

- append latency is not client acknowledgement or trade latency;
- replay throughput is not service recovery time;
- `BUFFERED` is not durable throughput;
- `SYNC_EACH_APPEND` results are host/storage-specific;
- no benchmark result may change format or default durability without a new
  approved decision and evidence review;
- profiling/optimization is deferred unless the Blueprint hits an Exception
  Gate and Human approval extends scope.

## 12. Planned Repository Changes

| File or Directory | Task / Stage | Planned Change | Boundary |
| --- | --- | --- | --- |
| `docs/adr/ADR-0013-*.md` | Proposal / TASK-018 | decision status/evidence sync | no approval inferred |
| `tasks/blueprints/PHASE-5-*.md` | Approval / all | authoritative Phase scope/checkpoints | Approved; execution dependency-gated |
| `tasks/active/TASK-...-014..018-*.md` | Approval / each Task | executable plans | Approved; execute only in dependency order |
| `src/main/java/.../persistence/wal/**` | TASK-014/015 | format, codec, writer, reader and failures | new package only |
| `src/test/java/.../persistence/wal/**` | TASK-014/015/017 | unit/integration/corruption tests | temp dirs and public contracts |
| `src/main/java/.../recovery/**` | TASK-016 | offline replay and transcript digest | no snapshot/online recovery |
| `src/test/java/.../recovery/**` | TASK-016/017 | replay/determinism/failure evidence | public engine behavior only |
| `benchmark/src/main/java/.../WalBenchmark.java` | TASK-018 | component JMH evidence | no benchmark-only production path |
| `docs/architecture/recovery.md` | TASK-018 | actual implemented boundary/limitations | distinguish replay from recovery |
| `docs/benchmark/recovery.md` | TASK-018 | method, evidence and limitations | component claims only |
| `docs/architecture/overview.md`, `README.md` | TASK-018 | Phase status/framework sync | no product claim |
| `.codex/AGENT_CONTEXT.md` | Proposal / checkpoints / closure | current-state index | concise, live Git authoritative |
| `tasks/reports/PHASE-5-command-wal-replay.md` | all Tasks | cumulative evidence | exact commit/CI only |
| `tasks/reports/PHASE-5-command-wal-replay-closure.md` | TASK-018 | Closure proposal | separate Human gate |

No `pom.xml` change is planned. Any new dependency triggers an Exception Gate.

## 13. Exception Gates

Execution must stop for Human review when any of these occurs:

- ADR-0013 or a Blueprint invariant conflicts with implementation evidence;
- format bytes, type codes, version, checksum coverage or file naming must
  change;
- a new command type or Snapshot/WAL compatibility rule is required;
- live pipeline integration or durable acknowledgement becomes necessary;
- an existing Domain/OrderBook/Engine/Pipeline production file must change;
- a public API outside the new WAL/Recovery packages must change;
- corruption can only be handled by best-effort skipping or broad truncation;
- a new runtime dependency, memory mapping, off-heap/direct-I/O or background
  thread is proposed;
- exact-next sequence enforcement cannot be maintained;
- replay equality needs reflection, a production test hook or a weakened
  observable-state criterion;
- a benchmark result is used to change format/default durability or justify a
  product claim;
- Network, Snapshot, online Recovery, replication or performance optimization
  enters scope;
- an acceptance criterion cannot be met without weakening it;
- destructive Git, Release or baseline-tag mutation is requested.

When triggered: stop, record impact and alternatives, update ADR/Blueprint if
applicable, and resume only after explicit Human approval.

## 14. Git, Commit and CI Strategy

- Proposal branch: `docs/phase5-command-wal-replay-blueprint` from `fbcbe53`.
- Implementation branch after approval: `feature/phase5-command-wal-replay`
  from the approved proposal commit.
- Planned commit sequence:

```text
docs(phase5): propose command wal and replay blueprint
feat(wal): add versioned command codec
feat(wal): add segmented command storage
feat(recovery): add deterministic command replay
test(recovery): verify wal corruption and torn-tail boundaries
perf(wal): add append and replay component baseline
docs(phase5): synchronize wal replay evidence
docs(phase5): prepare command wal replay closure
```

- Push each logical checkpoint after local gates pass.
- Record exact-SHA CI before beginning the dependent next Task.
- Proposal approval is synchronized in a separate docs commit before code.
- Phase Closure separately authorizes normal `--no-ff` merge and candidate tag.
- Never squash, force-push, rebase shared history, amend published commits or
  move/delete existing tags.
- `.vscode/` remains an unrelated untracked user artifact and must not be
  staged, modified, deleted or added to `.gitignore` by this Phase.

## 15. Rollback and Compatibility Plan

### Per-Task Rollback

- TASK-014: remove the unused new codec/contracts; no persisted production data
  or existing API is affected.
- TASK-015: remove new storage implementation and test data; never modify files
  outside Task-owned temporary/evidence directories.
- TASK-016: remove new replay package; WAL files remain readable by the codec
  but no production deployment depends on them.
- TASK-017: revert tests/fixtures only if the implementation they validate is
  also reverted; never weaken a failing corruption assertion.
- TASK-018: revert benchmark/docs without changing runtime behavior.

### Phase Rollback

Before merge, the feature branch can be abandoned while
`v0.3.0-engineering-baseline` and master remain unchanged. After an authorized
merge, use normal revert commits; do not rewrite history.

### Data Compatibility

Format version 1 is immutable after the Phase 5 baseline is frozen. Any later
format change requires a new ADR and must choose explicit read compatibility,
migration or rejection. Phase 5 creates no deployed user WAL and therefore
requires no production data migration during initial implementation.

## 16. Documentation and Evidence Plan

Required proposal artifacts:

- ADR-0013;
- this complete Blueprint;
- TASK-014 through TASK-018 plans;
- Phase 5 Blueprint Proposal report;
- `AGENT_CONTEXT` current gate synchronization.

Required execution/closure artifacts after approval:

- cumulative `PHASE-5-command-wal-replay.md` evidence report;
- `docs/architecture/recovery.md` rewritten to distinguish implemented WAL,
  offline replay and deferred operational recovery;
- `docs/benchmark/recovery.md` with reproducible component evidence;
- README and architecture overview status updates;
- ADR/Blueprint/Task status and exact commit/CI checkpoints;
- `PHASE-5-command-wal-replay-closure.md` with known limitations;
- raw benchmark artifacts kept local/ignored with paths and commands recorded.

## 17. Closure and Baseline Plan

- Closure report: `tasks/reports/PHASE-5-command-wal-replay-closure.md`.
- Human Closure Approval requires TASK-014..018 completed, exact-SHA CI PASS,
  frozen-path diff zero, corruption/replay evidence accepted and claims within
  scope.
- Authorized closure sequence, if separately approved:

```text
--no-ff merge to master
    -> local master mvn verify
    -> push master
    -> master exact-SHA CI PASS
    -> annotated v0.4.0-engineering-baseline
    -> tag CI PASS
    -> archive TASK-014..018 and final context sync
```

- Candidate tag: `v0.4.0-engineering-baseline`.
- Tag meaning: Command WAL format/storage + deterministic offline replay and
  component evidence.
- Explicitly not a Release, live durable matching system, Snapshot or online
  Recovery baseline.
- Network and every later Phase remain unauthorized until a separate complete
  Blueprint receives Human approval.

## 18. Human Phase Blueprint Approval

| Date | Reviewer | Decision | Approved ADRs / Tasks / Stages | Constraints |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | `Proposal Authorized` | Discovery, ADR-0013 draft, TASK-014..018 plans and complete Blueprint Proposal only | No implementation; stop at Human Blueprint Approval |
| 2026-08-21 | Human Developer | `Approved` | ADR-0013 D1-D10; TASK-014 through TASK-018 in strict dependency order | Frozen Domain/OrderBook/Engine/Pipeline; SYNC default; force failure is logical failure plus terminal writer, not proof of physical absence; live integration, Network, Snapshot, online Recovery, optimization, Closure, merge and tag excluded |

```text
Blueprint Status: Approved
Implementation: TASK-014 through TASK-018 completed
Next Gate: Human Phase 5 Closure Approval
```

Approval must explicitly confirm:

- [ ] ADR-0013 D1-D10;
- [ ] TASK-014 through TASK-018 in dependency order;
- [ ] zero changes to frozen Domain/OrderBook/Engine/Pipeline production paths;
- [ ] `SYNC_EACH_APPEND` remains the correctness default;
- [ ] Network, live integration, Snapshot and operational Recovery remain
  deferred;
- [ ] automated evidence gates and Exception Gates apply;
- [ ] Phase Closure remains a separate Human gate.

## 19. Execution Checkpoints

| Date | Task / Stage | Result | Evidence | Next State |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Discovery / Blueprint Proposal | Prepared / CI PASS | content commit `a2a7c75`; exact-SHA CI [32462826593](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32462826593) PASS | Human Phase 5 Blueprint Approval |
| 2026-08-21 | Human Blueprint Approval | Approved / CI PASS | approval sync commit `341dc5e`; exact-SHA CI [32462992039](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32462992039) PASS | TASK-014 Implementation / Evidence Gate |
| 2026-08-21 | TASK-014 Evidence Gate | Completed / CI PASS | codec commit `e5e4c96`; exact-SHA CI [32464648365](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32464648365) PASS; 92 full tests | TASK-015 Implementation / Evidence Gate |
| 2026-08-21 | TASK-015 Evidence Gate | Completed / CI PASS | storage commit `7da0069`; exact-SHA CI [32466198050](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32466198050) PASS; 102 full tests | TASK-016 Implementation / Evidence Gate |
| 2026-08-21 | TASK-016 Evidence Gate | Completed / CI PASS | replay commit `f434431`; exact-SHA CI [32466659845](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32466659845) PASS; 107 full tests | TASK-017 Implementation / Evidence Gate |
| 2026-08-21 | TASK-017 Evidence Gate | Completed / CI PASS | failure matrix commit `16dc957`; exact-SHA CI [32467018067](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32467018067) PASS; 113 full tests | TASK-018 Implementation / Evidence Gate; then STOP |
| 2026-08-21 | TASK-018 Evidence Gate | Completed / CI PASS | benchmark/docs commit `cd6997c`; exact-SHA CI [32467692149](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32467692149) PASS; 113 full tests; full JMH matrix recorded | Phase 5 Closure Proposal; STOP for Human approval |

## 20. Phase Closure Checklist

- [ ] Blueprint approval recorded and synchronized into ADR-0013/Tasks
- [x] TASK-014 format/codec completed
- [x] TASK-015 segmented storage completed
- [x] TASK-016 deterministic replay completed
- [x] TASK-017 corruption/recovery verification completed
- [x] TASK-018 benchmark/documentation completed
- [x] all automated evidence gates and exact-SHA CI pass
- [x] frozen production-path diff is zero
- [x] no unresolved Exception Gate
- [x] architecture and documentation synchronized
- [x] Phase Closure Report prepared
- [ ] Human Phase Closure Approval recorded
- [ ] authorized merge/tag/baseline actions verified
- [ ] active Tasks moved to completed
- [ ] next Phase remains explicitly unauthorized
