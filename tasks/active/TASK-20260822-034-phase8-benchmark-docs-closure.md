# Task Plan — TASK-20260822-034

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-034` / Recovery Benchmark, Documentation and Closure Proposal |
| Phase / ADR / Blueprint | Phase 8 / ADR-0016 / `PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md` |
| Status | Completed — Evidence Gate preparation complete; final read-only audits and Sol High Closure Review pending |
| Depends on | TASK-033 Evidence Gate PASS |
| Manual Gate | Stops at Human Phase 8 Closure Review |
| Planned report | `tasks/reports/PHASE-8-task-034.md` |

## Goal

Record separated recovery-component measurements, synchronize Phase 8 evidence
and limitations, prepare the Closure Proposal and stop without merge or tag.

## Authorized Scope After Approval

Benchmark module, ignored local raw benchmark output, committed benchmark
summary, ADR/Blueprint/Task reports, recovery architecture docs, README and
`.codex/AGENT_CONTEXT.md`.

## Benchmark Matrix

- pure-WAL genesis replay;
- Snapshot decode and state restore;
- Snapshot plus WAL-tail recovery;
- offline Snapshot creation; and
- process bootstrap to listener-ready.

Record total commands, active orders, Snapshot Sequence, tail length, segment
count/bytes, CPU, OS/filesystem/storage, JDK/JVM/GC, heap/JVM arguments,
forks/warmup/measurement, allocation/GC, throughput where meaningful and
SampleTime P50/P95/P99/P999.

## Claim Boundary

Measurements are component/local-host evidence. They do not prove production
RTO, availability SLA, power-loss safety, exactly-once/client outcome recovery,
capacity or production readiness. Benchmark results cannot change correctness
defaults or authorize optimization.

## Acceptance Criteria

- [x] Required benchmark boundaries, dimensions, metadata, heap,
  allocation/GC evidence and percentiles are
  recorded and internally consistent with raw evidence.
- [ ] verifier, benchmark-reviewer and docs-auditor independently report PASS.
- [ ] ADR, Blueprint, all task reports, recovery docs, README and context share
  the final exact-SHA evidence and known limitations.
- [x] Phase 8 Closure Proposal is complete but does not claim Human approval.
- [x] Merge, `v0.7.0-engineering-baseline`, Phase 9 and Product Release remain
  locked.

## Final Evidence Gate

Focused recovery/benchmark smoke, full `mvn verify`, Checkstyle 0,
`git diff --check`, approved/frozen-path audit, all three read-only audits PASS,
logical commit, normal push and exact-SHA CI PASS.

Then STOP at Sol High Phase 8 Closure Review and Human Closure Approval.

## Exception Gate

Stop for benchmark-driven production change, missing Blueprint evidence,
unsupported recovery claim, stale/falsified evidence, new dependency or scope
expansion.
