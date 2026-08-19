# AGENT_CONTEXT - Matching Engine

> Last Updated: 2026-08-19
> Project Status: Bootstrap
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

Phase 0 - Bootstrap

### Completed

- [x] Repository initialized
- [x] Maven project
- [x] Java 21
- [x] JUnit
- [x] JMH
- [x] CI
- [x] Documentation structure

### Current Task

Establish the Phase 0 project bootstrap framework.

### Next Task

Establish the domain model and correctness baseline.

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
4. Check Git status
5. Check current branch
6. Check build status
7. Inspect recent commits
8. Identify current phase
9. Confirm task scope

At the end:

1. Run relevant tests
2. Run relevant benchmark
3. Inspect Git diff
4. Update `AGENT_CONTEXT.md`
5. Report:
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
    -> Scope
    -> Design
    -> Implementation
    -> Verification
    -> Review
    -> Documentation
    -> Commit
    -> Git Status Confirmation
```

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

The bootstrap benchmark is an infrastructure smoke test only. It is not a matching-engine performance result.
