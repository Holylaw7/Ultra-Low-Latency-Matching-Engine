# Task Plan — TASK-20260823-035

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260823-035` |
| Title | Qualification contracts, workload manifest and module foundation |
| Status | `In Progress` |
| Owner | Human Developer |
| Implementer | Main Codex / Luna Max — only writer |
| Created | `2026-08-23` |
| Updated | `2026-08-23` |
| Related Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Related ADR | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) |
| Phase Blueprint | [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md) |
| Authorization Mode | `Blueprint` |
| Current Stage | `Implementation` |
| Next Gate | `TASK-035 Evidence Gate` |
| Branch | `feature/phase9-system-qualification` |
| Baseline HEAD | `87abbc1` / `v0.7.0-engineering-baseline` |
| Remote | `origin` |
| CI | Pending |

## 2. Background

Phase 8 froze the durable/recovery runtime at `v0.7.0-engineering-baseline`.
Phase 9 is authorized to add an external qualification layer. No such module
or versioned workload contract exists yet.

## 3. Goal

Create the additive `qualification` Maven module and immutable, JDK-only
contracts for deterministic workload generation, run configuration, manifest
metadata and qualification results. Establish golden vectors for
`QualificationWorkloadV1` without starting a runtime or modifying production
behavior.

## 4. Non-Goals

- Protocol client or server lifecycle (TASK-036).
- Long-run soak or resource sampling (TASK-037).
- Restart/termination campaign (TASK-038).
- JMH/JFR benchmark implementation (TASK-039).
- Production source, tests, WAL, Snapshot, Recovery or Pipeline changes.
- New dependencies.

## 5. Requirements and Acceptance Criteria

### Requirements

- [ ] Add `qualification` to the Maven reactor without changing core semantics.
- [ ] Define immutable configuration, workload, manifest and result contracts.
- [ ] Define deterministic workload profiles and default seed `20260823`.
- [ ] Provide golden vectors covering lifecycle, crossing multi-match and resting depth.
- [ ] Validate bounds before resource/run allocation.
- [ ] Keep raw qualification output outside the repository.

### Acceptance Criteria

- [ ] Repeated generation with identical version/seed/config is structurally equal.
- [ ] Invalid version, seed, command count, timeout or output path is rejected.
- [ ] Manifest records Git SHA/tag, workload, environment placeholders, configuration and result digests.
- [ ] No runtime is started by TASK-035.
- [ ] No file under `src/main/java/**`, `src/test/java/**`, `core/pom.xml` or existing benchmark classes changes.
- [ ] Focused tests, `mvn verify`, Checkstyle 0, `git diff --check`, frozen-path audit and exact-SHA CI pass.

## 6. Current Implementation and Scope

### Current Implementation

The repository has `core` and `benchmark` Maven modules. Qualification code does
not yet have an isolated module or workload manifest contract.

### In Scope

- `qualification/pom.xml` and root reactor registration.
- `qualification/src/main/java/com/ultralatency/matching/qualification/**`.
- `qualification/src/test/java/com/ultralatency/matching/qualification/**`.
- Task/report and Phase 9 governance documentation.

### Out of Scope

All existing production runtime source, existing tests and existing benchmark
classes; network/WAL/Snapshot/Recovery runtime; external dependencies.

## 7. Design Proposal

### Proposed Design

Use Java records/enums and JDK-only value validation:

```text
QualificationConfiguration
    -> QualificationWorkloadV1
    -> QualificationManifest
    -> QualificationResult
```

The workload contract exposes deterministic profile selection, seed, command
count and a generated immutable command vector. The manifest stores run
identity, baseline/workload/configuration metadata and digest placeholders;
later Tasks populate environment and runtime evidence. No class starts a
server, opens a socket, writes a WAL or allocates a long-run resource.

### Alternatives Considered

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
| Put harness in `core` | Fewer modules | Pollutes frozen runtime and production artifact | Rejected |
| Put harness in existing `benchmark` | Reuses JMH module | Couples qualification lifecycle and benchmark-only concerns | Rejected |
| Add isolated JDK-only `qualification` module | Clear boundary, testable contracts, no production dependency | One Maven module | Selected |

### Decision

Select the isolated `qualification` module and immutable deterministic value
contracts. Task 035 must not design or implement the runtime harness.

### ADR Linkage

| Field | Value |
| --- | --- |
| ADR | `docs/adr/ADR-0017-system-qualification-performance-reliability.md` |
| Status | `Accepted` |
| Decision Summary | Qualification is additive, public-boundary based, versioned and evidence-first; production semantics and optimization remain frozen. |
| Scope Boundary | Only qualification foundation files and governance evidence listed by the Phase 9 Blueprint. |

### Phase Blueprint Linkage

| Field | Value |
| --- | --- |
| Blueprint | `tasks/blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md` |
| Blueprint Status | `Approved` |
| Authorized Task / Stages | TASK-035 foundation only; later Tasks remain dependency-locked until this Evidence Gate passes. |
| Exception Gates | Production/runtime changes, new dependency, unlisted API/format, workload/threshold changes or runtime startup. |

### Architecture Impact

- [x] No production architecture change
- [x] ADR recorded and approved by Human Phase 9 Blueprint Approval
- [ ] Human architecture decision required during this Task

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `pom.xml` | Add `qualification` module | Reactor integration |
| `qualification/pom.xml` | New JDK-only module | Isolate qualification layer |
| `qualification/src/main/java/**` | Value/config/workload/manifest contracts | TASK-035 deliverable |
| `qualification/src/test/java/**` | Golden vectors and validation tests | Determinism/boundary evidence |
| `tasks/reports/PHASE-9-task-035.md` | Evidence report | Blueprint checkpoint |

## 9. Test Plan

### Unit Tests

- [x] configuration validation and immutable value semantics;
- [x] workload profile golden vectors;
- [x] repeated generation equality and digest stability;
- [x] manifest required-field validation.

### Integration or System Tests

- [x] Maven reactor compiles the qualification module; no runtime startup.

### Failure and Boundary Tests

- [x] invalid version/seed/count/path/timeout rejected before allocation;
- [x] unsupported profile rejected by the closed profile type;
- [x] output path does not create files in TASK-035.

### Determinism or Replay Tests

- [x] Same seed/version/config produces the same immutable command vector and digest.

## 10. Benchmark and Profile Plan

- Benchmark: `Not applicable` for TASK-035.
- Profile: `Not applicable` for TASK-035.
- Dataset: deterministic workload golden vectors only.
- Metrics: command count and SHA-256/digest equality.
- Baseline: `v0.7.0-engineering-baseline`.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Harness contracts drift into runtime API | Boundary pollution | Isolated module and no production imports |
| Workload generator is nondeterministic | Invalidates later evidence | Fixed version/seed, immutable vectors and golden tests |
| Bounds checked after allocation | Resource abuse | Validate all limits before generation/allocation |
| New dependency added for convenience | Blueprint deviation | JDK-only implementation; Exception Gate on dependency |

## 12. Rollback Plan

Revert the TASK-035 commit and remove only the new qualification module and
reactor entry. No runtime data, wire format, persistence format or baseline
tag is changed.

## 13. Verification Commands

```text
mvn -pl qualification -am test
mvn verify
git diff --check
git diff --name-only v0.7.0-engineering-baseline...HEAD
git status --short --branch
```

Frozen-path audit must show no changes under `src/main/java/**`,
`src/test/java/**`, `core/pom.xml` or existing benchmark classes.

## 14. Git Plan

Commit:

```text
feat(qualification): add deterministic qualification foundation
```

Commit only TASK-035 foundation, tests and its evidence report. Push the
checkpoint and record exact-SHA CI before TASK-036.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
| 2026-08-23 | Human Developer | Phase 9 Blueprint Approval | Approved (Inherited) | ADR-0017 D1-D16 and TASK-035..040 approved; TASK-035 next; runtime and production optimization frozen. |

## 16. Phase Reports and Approval Gates

| Stage | Report Location | Status | Next Gate | Authorization |
| --- | --- | --- | --- | --- |
| ADR / Decision | `docs/adr/ADR-0017-system-qualification-performance-reliability.md` | Completed / Approved | Implementation | Inherited |
| Task Approval | This task | Completed / Approved | Implementation | Inherited |
| Implementation | This task | Completed | Verification | Blueprint |
| Verification | `tasks/reports/PHASE-9-task-035.md` | Evidence collected; CI pending | Evidence Gate | Blueprint |
| Benchmark / Profile | Not applicable | Not applicable | Evidence Gate | Blueprint |
| Documentation and Synchronization | `tasks/reports/PHASE-9-task-035.md` | Synchronized; CI pending | Evidence Gate | Blueprint |
| Completion | This task | Pending | TASK-036 / Exception Gate | Blueprint |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-23 | Completed locally | Added isolated qualification foundation and deterministic golden vectors. | Focused tests and `mvn verify` PASS; exact-SHA CI pending |

## 18. Completion Checklist

- [x] Scope and acceptance criteria satisfied
- [x] Tests added
- [x] Build passed
- [x] Checkstyle 0
- [x] Benchmark/Profile marked Not applicable for TASK-035
- [x] Documentation synchronized
- [x] ADR and Blueprint approval recorded
- [x] Evidence report exists
- [ ] Commit created
- [ ] Remote and CI recorded
- [ ] Post-commit Git status confirmed
