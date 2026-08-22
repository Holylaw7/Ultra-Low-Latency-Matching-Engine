# Task Plan — TASK-20260822-028

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-028` / Benchmark, Documentation and Closure Evidence |
| Phase / ADR / Blueprint | Phase 7 / ADR-0015 / `PHASE-7-live-durable-command-pipeline-blueprint.md` |
| Status | Implementation complete; Evidence Gate pending read-only review |
| Scope | Component/loopback benchmark, reports, README, architecture and context |
| Next Gate | verifier / benchmark-reviewer / docs-auditor, exact-SHA Evidence Gate; then Phase 7 Closure Review |
| Branch | `feature/phase7-live-durable-command-pipeline` |
| Current HEAD | `c4be5b9` pre-TASK-028 checkpoint; final commit pending |
| Remote / push | `origin` configured; TASK-028 push pending |
| Working tree | TASK-028 changes present; `.vscode/` pre-existing and untouched |
| Exact-SHA CI | Pending commit/push and Evidence Gate |

## Acceptance

- [x] Append/force, append-plus-publish, local-result-write and sequential
  loopback measurements are separated.
- [x] CPU, storage, JDK/JVM/GC, Netty allocator, workload, warmup, forks and
  percentile metadata are recorded.
- [x] Claims remain engineering/component-level; no durable ACK, power-loss,
  recovery or production-readiness claim is made.
- [x] Closure Proposal, task status, Blueprint and `AGENT_CONTEXT` are synchronized;
  final exact-SHA Evidence Gate remains pending.

## Implementation Log

- Added `DurablePipelineBenchmark` with separate synchronous WAL append/force,
  coordinator append-plus-publish, local response encoding and sequential
  durable loopback methods.
- Added explicit Submit and Cancel coordinator parameters and an alternating
  Submit/Cancel loopback vector. The benchmark uses real WAL, pipeline,
  protocol and durable server adapters; no production source path changed.
- Recorded the successful Java 21 full matrix in
  [`docs/benchmark/durable-pipeline.md`](../../docs/benchmark/durable-pipeline.md).
- Prepared the cumulative report and Closure Proposal. `benchmark-results/`
  remains local and ignored by Git.

## Evidence Gate Preparation

```text
mvn -pl benchmark -am test: PASS; core 158 tests, 0 failures
Checkstyle: 0 violations
shaded benchmark package: PASS
Java 21 full matrix: PASS
read-only verifier / benchmark-reviewer / docs-auditor: pending
exact-SHA CI for TASK-028 checkpoint: pending
```

Phase 7 Closure, merge to `master`, `v0.6.0-engineering-baseline` and Phase 8
remain unauthorized until the final read-only Evidence Gate and separate Human
Closure Review.
