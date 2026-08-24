# Phase 10 Complete Blueprint Proposal — Release-Candidate Runtime Assembly

## Status

| Field | Value |
| --- | --- |
| Phase | Phase 10 |
| Decision | `Proposed — Human Blueprint Approval Pending` |
| Baseline | `v0.8.0-engineering-baseline` → `ef73f60` |
| ADR | [`ADR-0018`](../../docs/adr/ADR-0018-release-candidate-runtime-boundary.md) |
| Blueprint | [`PHASE-10-release-candidate-runtime-assembly-blueprint.md`](../blueprints/PHASE-10-release-candidate-runtime-assembly-blueprint.md) |
| Tasks | `TASK-20260824-041` through `TASK-20260824-046` — Proposed |
| Proposal checkpoint | `541ef28` |
| Standard CI | `32716540931` — PASS |
| Qualification Quick Lane | `32716540939` — PASS |
| Implementation | Not authorized |
| Product Release | Not authorized |

## Discovery Result

Sol High selected release-candidate runtime assembly as the next coherent
engineering boundary. The existing baseline has qualified internal components
but lacks one real application entrypoint and owned operational lifecycle.

WAL retention/compaction, multi-session sequencing and production optimization
were considered and deferred. Each changes a larger architectural boundary and
must not be smuggled into runtime assembly.

## Proposed Capability

```text
strict immutable configuration
        -> Snapshot + WAL-tail recovery
        -> recovered Pipeline / durable coordinator
        -> Protocol v1 listener
        -> loopback management boundary
        -> readiness
        -> bounded shutdown and explicit exit outcome
```

The proposed application reuses the frozen Protocol v1, WAL v1, Snapshot v1,
WAL-before-execute and SPSC semantics. It adds composition and operational
ownership, not new trading or delivery semantics.

The ownership hierarchy is explicit: the application runtime directly owns the
Protocol server and bounded loopback management server; the Protocol server
continues to own the recovered runtime and its transitive lease/WAL/Pipeline/
coordinator resources. Configuration keys, management wire/schema bounds,
cooperative shutdown semantics and the `RC_ASSEMBLED_RUNTIME_V1` qualification
manifest are frozen in the Blueprint before approval.

## Planned Tasks

| Task | Deliverable |
| --- | --- |
| TASK-041 | runtime contracts, immutable config and lifecycle/status model |
| TASK-042 | real entrypoint and composition root |
| TASK-043 | strict config validation and reproducible packaging |
| TASK-044 | bounded health/readiness/status/counter boundary |
| TASK-045 | shutdown and terminal-failure hardening evidence |
| TASK-046 | assembled-runtime qualification, documentation and Closure Proposal |

TASK-046 implementation/Quick/lifecycle evidence inherits Blueprint authority,
but its two 60-minute Full runs require a separate Human Full Campaign Approval
after the pre-campaign exact-SHA Evidence Gate passes.

## Claim Boundary

Phase 10 may support a statement that the recorded single-node runtime assembly
passed the approved release-candidate engineering qualification on the recorded
environment. It must not claim Production Ready, Internet-safe, exactly-once,
multi-session, HA, bounded disk, hardware power-loss safety, SLA or RTO.

## Current Gate

```text
ADR-0018: Proposed
Phase 10 Blueprint: Proposed
TASK-041 through TASK-046: Proposed
Implementation: Not Authorized
Existing v0.8.0 tag: Frozen / Unchanged
Next: Human Phase 10 Blueprint Approval
```

`541ef28` and its two passing workflows are the fixed technical proposal input.
Any later docs-only synchronization SHA is external validation and does not
replace that input.
