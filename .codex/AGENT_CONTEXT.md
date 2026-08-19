# AGENT_CONTEXT - Matching Engine

> Last Updated: 2026-08-19
> Project Status: Phase 2 - Basic OrderBook (Implementation - Pending Approval)
> Owner: Human Developer
> Primary Agent: Codex

---

## 1. Project Identity

Project:

Ultra-Low-Latency Matching Engine

Type:

Single-node high-performance matching engine.

Primary Goals:

1. Build a correct matching engine.
2. Establish deterministic execution and replay.
3. Measure throughput, latency, allocation, GC, and recovery behavior.
4. Use evidence-based performance engineering.

---

## 2. Engineering Philosophy

The project follows:

```text
Human Architecture
    +
Codex Implementation
    +
Automated Testing
    +
Benchmark
    +
Profiling
    +
Evidence-based Optimization
```

Human owns architecture and final decisions.
Codex owns implementation assistance.

---

## 3. Current Status

### Phase

Phase 2 - Basic OrderBook (Implementation)

### Completed

- [x] Repository initialized
- [x] Maven project
- [x] Java 21
- [x] JUnit
- [x] JMH
- [x] CI
- [x] Documentation structure
- [x] Task workspace and plan-first workflow
- [x] Domain model and correctness baseline
- [x] ADR linkage recorded for the domain decision
- [x] ADR-first decision and phase approval governance

### Current Task

`TASK-20260819-004` - Establish Basic OrderBook baseline (`In Progress`,
`Implementation - BidBook / AskBook` completed; pending approval).

### Next Task

Phase 2 - Basic OrderBook implementation. The `BidBook / AskBook` sub-stage is
complete and awaiting approval. `OrderBook`, active-index aggregation and
matching remain unauthorized until the next approval gate.

Current task plan:
[`tasks/active/TASK-20260819-004-basic-orderbook.md`](../tasks/active/TASK-20260819-004-basic-orderbook.md).

Current ADR:
[`docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md`](../docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md)
(`Accepted with constraints`).

Current Phase 2 report:
[`tasks/reports/PHASE-2-adr-decision.md`](../tasks/reports/PHASE-2-adr-decision.md).

Current implementation sub-stage report:
[`tasks/reports/PHASE-2-implementation-bidbook-askbook.md`](../tasks/reports/PHASE-2-implementation-bidbook-askbook.md).

Phase 1 report:
[`tasks/reports/PHASE-1-domain-model.md`](../tasks/reports/PHASE-1-domain-model.md).

The Phase 1 implementation and verification are complete. Human Developer
approved the Phase 1 hand-off on `2026-08-19`. Human Developer approved
ADR-0007 and TASK-20260819-004 on `2026-08-19`, authorizing Phase 2
implementation within the recorded constraints. The current implementation
sub-stage `OrderNode + OrderQueue + PriceLevel` was approved on `2026-08-19`.
Human Developer then authorized and the implementation completed the
`BidBook / AskBook` sub-stage. Its completion report and next approval gate are
now pending Human approval.

---

## 4. Planned Architecture

```text
Client
    |
    v
Netty
    |
    v
Decoder
    |
    v
Ingress
    |
    v
Disruptor / RingBuffer
    |
    v
Matching Engine
    |
    v
OrderBook
    |
    +---- Bid
    +---- Ask
    |
    v
Trade Event
    |
    +---- WAL
    +---- Output
    +---- Metrics
```

---

## 5. Core Domain

Planned entities:

- Order
- Trade
- Execution
- Price
- Quantity
- OrderId
- Sequence

Planned enums:

- Side
- OrderType
- OrderStatus

Implemented Phase 1 baseline:

- Positive `long`-backed identifiers, prices, quantities, and sequences.
- Limit and market orders with controlled lifecycle transitions.
- Deterministic `Trade` and `Execution` value objects.
- Correctness tests for boundaries, terminal states, idempotent cancellation,
  and equal-input determinism.

---

## 6. Matching Rules

Primary matching rule:

Price-Time Priority.

Buy:

Higher price first.

Sell:

Lower price first.

Same price:

Earlier sequence first.

---

## 7. OrderBook Design

Initial implementation:

```text
TreeMap + intrusive order queue + OrderId index
```

Potential future alternatives:

- Custom Red-Black Tree
- SkipList
- Radix Tree
- Price Array
- Hybrid price index

Any replacement requires benchmark evidence.

---

## 8. Concurrency Model

Default model:

One Matching Thread owns one Symbol OrderBook.

Multiple symbols may later be partitioned across matching workers.

Do not introduce multi-threaded mutation of a single OrderBook without an explicit architecture decision.

---

## 9. Performance Targets

These are targets, not guaranteed results:

- High single-node throughput
- Microsecond-level core latency
- Stable tail latency
- Minimal allocation
- Minimal GC interference

Aspirational target:

> 1M+ orders/s

Actual performance must come from reproducible benchmarks.

---

## 10. Benchmark Strategy

Benchmark layers:

### Level 1

Pure OrderBook.

### Level 2

Pure Matching Engine.

### Level 3

RingBuffer or Disruptor Pipeline.

### Level 4

WAL.

### Level 5

Netty TCP End-to-End.

Every result should report:

- Throughput
- P50
- P95
- P99
- P999
- Allocation
- GC
- CPU

---

## 11. Recovery Model

Planned:

```text
WAL
    v
Snapshot
    v
Restart
    v
Snapshot Load
    v
WAL Replay
    v
State Hash Verification
```

Recovery must be deterministic.

---

## 12. Important Constraints

The project is intentionally:

- Single-node
- In-memory
- Performance-focused
- Deterministic
- JVM-focused

Do not turn this into:

- Microservice system
- Cloud-native system
- Distributed exchange
- CRUD trading platform

unless explicitly requested.

---

## 13. Non-Goals

Not currently planned:

- Real-money operation
- Real exchange connectivity
- User management
- Payment
- KYC
- Web frontend
- Cryptocurrency integration
- Production financial deployment
- Multi-region deployment

This is a research and engineering project, not a real trading platform.

---

## 14. Current Technical Decisions

| Decision | Status | Reason |
| --- | --- | --- |
| Java 21 | Accepted | JVM performance and existing expertise |
| Single-threaded matching core | Accepted | Deterministic mutation |
| Integer domain units | Accepted with constraints | Avoid floating-point rounding in the matching core |
| Domain lifecycle baseline | Accepted with constraints | Fixed order states and controlled transitions |
| Trade/Execution separation | Accepted with constraints | Distinguish one match from one order's execution result |
| Logical event sequence | Accepted with constraints | Deterministic ordering independent of time and scheduling |
| ADR-0005 domain model | Accepted with constraints | Long-term record of Phase 1 domain semantics |
| ADR-0006 governance | Accepted | ADR-first decisions, phase reports, Human approval gates, and document synchronization |
| ADR-0007 Basic OrderBook | Accepted with constraints | TreeMap side books, intrusive FIFO levels, active cancellation index, best-price cache, and limit-order matching boundaries; implementation remains strictly scoped |
| Netty | Planned | High-performance networking |
| Disruptor | Planned | Low-contention event pipeline |
| WAL | Planned | Crash recovery |
| JMH | Accepted | Reliable microbenchmark |
| JFR | Planned | JVM profiling |
| async-profiler | Planned | CPU and allocation profiling |

---

## 15. Open Questions

These must be resolved through experiments rather than assumptions:

1. TreeMap vs custom tree
2. SkipList vs tree
3. Object-based vs flat memory layout
4. Array-based vs linked price levels
5. Disruptor vs custom ring buffer
6. Heap vs off-heap
7. WAL fsync policy
8. Snapshot format
9. Symbol partitioning
10. CPU affinity strategy

---

## 16. Benchmark Evidence

No official benchmark result yet.

Do not claim the following until experimentally verified:

- 1M orders/s
- Microsecond P99
- Zero GC
- Zero-copy
- Lock-free execution

---

## 17. Known Risks

### Risk 1

Over-optimization before correctness.

Mitigation:

Correctness first.

### Risk 2

Benchmark gaming.

Mitigation:

Independent benchmark module and reproducible parameters.

### Risk 3

Excessive complexity.

Mitigation:

Baseline implementation first.

### Risk 4

AI-generated code that cannot be explained.

Mitigation:

Human review and technical verification.

---

## 18. Agent Session Protocol

At the beginning of every session:

1. Read `MASTER_PROMPT.md`
2. Read `DEVELOPMENT_RULES.md`
3. Read `AGENT_CONTEXT.md`
4. Read `tasks/README.md`
5. Read relevant plans in `tasks/active/`
6. Check Git status
7. Check current branch
8. Check build status
9. Inspect recent commits
10. Identify current phase
11. Confirm task scope and approval status
12. Confirm current development stage, phase report status, and next approval gate

At the end:

1. Run relevant tests
2. Run relevant benchmark
3. Inspect Git diff
4. Update `AGENT_CONTEXT.md`
5. Update the active task plan and move completed plans to `tasks/completed/`
6. If a stage is complete, write its phase report, set the next gate to
   `Pending Human Approval`, and stop before the next stage until Human approval
   is recorded.
7. Report:
   - Changes
   - Tests
   - Benchmarks
   - Risks
   - Next step

---

## 19. Project Evolution

When a major architecture decision is made, create an ADR.

Recommended format:

```text
docs/adr/ADR-NNNN-title.md
```

ADR must contain:

- Context
- Problem
- Options
- Decision
- Consequences
- Benchmark Evidence

---

## 20. Development Governance

All implementation follows this lifecycle:

```text
Requirement
    -> ADR Draft
    -> Human Decision
    -> Scope and Task Approval
    -> Implementation
    -> Phase Report
    -> Human Approval
    -> Verification
    -> Phase Report
    -> Human Approval
    -> Documentation and Synchronization
    -> Phase Report
    -> Human Approval
    -> Commit
    -> Git Status Confirmation
```

Every technical decision must have an ADR draft before the decision is made.
Every completed development stage must have a phase report and an explicit
Human approval before the next stage begins. ADR, task plan, project rules,
related project documents, and this context file must be synchronized before a
task is marked `Completed`.

Git status must be checked:

- At the beginning of every session
- Before editing
- After editing
- Before staging
- After staging
- Before committing
- After committing
- Before the final report

The working tree should be clean after a completed task.

Required repository checks:

```text
git status --short --branch
git branch --show-current
git log --oneline --decorate -5
git diff --stat
git diff --cached --stat
git diff --check
```

Commit requirements:

- One logical topic per commit
- Conventional Commits message
- Tests and applicable quality gates pass
- Staged diff reviewed
- No secrets, generated files, or unrelated changes
- Commit hash and final status reported

Push, rebase, amend, reset, restore, clean, and force push require explicit authorization.

---

## 21. Bootstrap Verification

Verified on 2026-08-19:

- Root Maven reactor builds successfully.
- Core module compiles the root `src/` layout.
- Java 21 release compilation is enforced.
- JUnit 5 test suite runs successfully.
- JMH benchmark module packages successfully.
- Checkstyle runs with zero violations.
- GitHub Actions workflow is defined for `mvn verify`.
- Phase 1 domain tests pass with 12 tests and zero failures.
- Root `mvn verify` passes after the domain model implementation.

The bootstrap benchmark is an infrastructure smoke test only. It is not a matching-engine performance result.

---

## 22. Task Workspace and Plan-First Workflow

All development plans are versioned under `tasks/`.

```text
Proposed
    -> Approved
    -> In Progress
    -> Completed
```

The current completed governance task is:

```text
tasks/completed/TASK-20260819-001-task-workspace-and-plan-first.md
```

The completed domain-model task is:

```text
tasks/completed/TASK-20260819-002-domain-model-and-correctness-baseline.md
```

The completed governance task is:

```text
tasks/completed/TASK-20260819-003-adr-first-phase-approval-governance.md
```

All listed tasks have status `Completed` except the approved
`TASK-20260819-004`, which is currently `In Progress`. No additional production
code or test scope may be added without an updated approved plan and ADR
review where applicable.

Each task plan must record:

- Scope and acceptance criteria
- Design and architecture impact
- Planned file changes
- Test and Benchmark plan
- Risks and rollback
- Verification commands
- Approval and implementation log
- Current stage, phase reports, and approval gates
- Git commit plan

If implementation changes the approved scope or design, update the task plan
and obtain approval again before continuing. If a technical decision changes,
create or update the ADR before requesting the decision again.
