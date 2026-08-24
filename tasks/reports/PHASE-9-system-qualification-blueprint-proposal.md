# Phase 9 Complete Blueprint Proposal — System Qualification and Long-Run Reliability

## Status

| Field | Value |
| --- | --- |
| Phase | Phase 9 |
| Decision | Human Blueprint Approval — Approved |
| ADR | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) |
| Blueprint | [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md) |
| Baseline | `v0.7.0-engineering-baseline` |
| Implementation | TASK-035 authorized / next |
| Phase Closure | Not authorized |

## Discovery Decision

Phase 9 is intentionally an evidence-first qualification phase. The current
baseline already has the durable command path and online recovery bootstrap;
the remaining engineering gap is reproducible long-run, restart and full-path
performance evidence. Production optimization is deferred until a measured
hotspot has a separate Optimization ADR and approval.

## Approved Scope

ADR-0017 D1-D16 and TASK-20260823-035 through TASK-20260823-040 are approved in
strict dependency order. Existing production runtime, protocol, persistence,
recovery and baseline tags remain frozen.

## Approval Boundary

Full Qualification is an immutable evidence unit. Workload, seed, JVM,
filesystem, durability, pipeline and threshold configuration cannot change
mid-run. Failures and outliers remain in the evidence. Quick CI evidence cannot
replace the required 60-minute/1,000,000-command Full lane.

## Next Gate

Main Luna Max may start TASK-035. After each automated Evidence Gate PASS, the
next Blueprint-authorized Task may continue. TASK-040 must stop for Sol High
Phase 9 Closure Review and Human Closure Approval.
