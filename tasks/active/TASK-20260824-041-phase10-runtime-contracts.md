# Task Plan — TASK-20260824-041

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260824-041` |
| Title | Release-candidate runtime contracts and lifecycle model |
| Status | `Completed / Evidence Gate PASS` |
| Owner | Human Developer |
| Implementer | Main Codex / Luna Max — only writer after approval |
| Related Phase | Phase 10 — Release-Candidate Runtime Assembly |
| Related ADR | [`ADR-0018`](../../docs/adr/ADR-0018-release-candidate-runtime-boundary.md) |
| Blueprint | [`Phase 10 Blueprint`](../blueprints/PHASE-10-release-candidate-runtime-assembly-blueprint.md) |
| Dependency | Human Phase 10 Blueprint Approval |
| Next Gate | TASK-042 Evidence Gate |

## 2. Goal

Create the additive, project-owned contracts for immutable runtime
configuration, lifecycle state, exit outcomes, status snapshots and resource
ownership. Freeze the exact key schema and typed validation rules before file
parsing or composition code.

## 3. Scope

In scope: new `app/**` and `operations/**` contracts, typed construction,
cross-field validation, canonical sanitized rendering and focused tests. File
syntax/CLI parsing belongs to TASK-043. No thread, listener, Pipeline, WAL or
engine runtime is started by this task.

Out of scope: entrypoint composition, management server, packaging,
qualification, Product Release and all frozen core semantics.

## 4. Acceptance Criteria

- [x] Configuration is immutable, typed and exposes only the approved key set.
- [x] Relative paths resolve against the config-file directory.
- [x] Unknown/malformed/unsafe/cross-field-invalid values fail closed.
- [x] Lifecycle states and ADR-0018 exit codes are exhaustive.
- [x] Status snapshots contain only immutable operational data and monotonic
  boundary counters.
- [x] No dependency, producer, executor or production runtime is introduced.
- [x] Focused tests, `mvn verify`, Checkstyle, diff audit and exact-SHA CI pass.

## 5. Planned Files

| Path | Change |
| --- | --- |
| `src/main/java/com/ultralatency/matching/app/**` | config schema/lifecycle/exit contracts |
| `src/main/java/com/ultralatency/matching/operations/**` | immutable status contracts |
| `src/test/java/**/app/**`, `src/test/java/**/operations/**` | boundary/golden tests |
| `tasks/reports/PHASE-10-task-041.md` | evidence report |

## 6. Verification and Evidence Gate

```text
focused contract tests
    -> mvn verify / Checkstyle
    -> git diff --check
    -> approved/frozen path audit
    -> verifier + docs-auditor read-only PASS
    -> exact-SHA CI PASS
    -> TASK-042 authorized
```

## 7. Exception Gate / Rollback

Stop for any new dependency, existing production-file modification, mutable
engine observation, environment-per-key config fallback or change to an
ADR-0018 contract. Rollback is removal of the additive contracts/tests; no data
or protocol migration exists.

## 8. Git and Approval

Planned commit: `feat(runtime): add release-candidate runtime contracts`.
No merge, tag or release is authorized. Approval is inherited only after Human
Phase 10 Blueprint Approval.

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-24 | Human Developer | Approved / Inherited | ADR-0018 D1-D16 and TASK-041 exact boundary; no runtime start/listener/WAL/Pipeline |

## 9. Current Implementation and Design

`MatchingEngineApplication` is a print-only stub. Existing configuration is
distributed across immutable `WalConfiguration`, `PipelineConfiguration`,
`DurableConfiguration` and `RecoverableNetworkConfiguration`; there is no
application-level parser, availability model or stable exit contract.

The selected design adds `RuntimeConfiguration`,
`RuntimeConfigurationSchema`, `RuntimeLifecycleState`, `RuntimeFailureCode`,
`RuntimeExitCode`, `RuntimeAvailability` and `RuntimeStatusSnapshot`. It adapts
to existing component configurations without changing them. Using mutable maps,
environment precedence and reusing component state enums were rejected because
they leave validation/ownership ambiguous.

### ADR / Blueprint linkage

| Field | Value |
| --- | --- |
| ADR decisions | ADR-0018 D1-D9 |
| Blueprint status | Approved / Human Approval 2026-08-24 |
| Authorized stages after approval | contracts -> focused verification -> evidence sync |
| Architecture impact | additive runtime boundary; frozen component semantics |

## 10. Detailed Test / Benchmark Plan

Unit tests cover every normative key/default/bound, typed cross-field/path
validation, canonical output, state transitions, counter overflow behavior and
exhaustive exit mapping. Duplicate/unknown UTF-8 file syntax belongs to
TASK-043. Integration/system testing and benchmark profiling are not applicable
until later Tasks.

## 11. Verification Commands

```text
mvn -pl core -am -Dtest='*RuntimeConfiguration*,*RuntimeAvailability*' test
mvn verify
git diff --check
git diff --name-only v0.8.0-engineering-baseline...HEAD
```

## 12. Stage / Report / Git Plan

| Stage | Report | Gate |
| --- | --- | --- |
| contract implementation | `tasks/reports/PHASE-10-task-041.md` | focused tests |
| verification | same | regression/static/read-only review |
| evidence sync | same | exact-SHA CI |

Branch: `feature/phase10-release-candidate-runtime`. Push only after the Task
commit and local gates pass. Do not merge, tag or rewrite history.

## 13. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| parser accepts ambiguous Java-properties syntax | strict properties-v1 golden rejection tests |
| runtime config silently changes component default | exact mapping assertions |
| status becomes state authority | one atomic availability owner; snapshots read-only |

## 14. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-24 | Proposed | Complete plan prepared; no implementation | docs verification only |
| 2026-08-24 | Implemented | Added immutable runtime contracts, typed schema validation, lifecycle availability and stable exit/status contracts. No runtime is started and no frozen component is modified. | Focused 10/10 PASS; full reactor 205 core + 46 qualification tests, 2 skipped, 0 failures; Checkstyle 0 |

## 15. Completion Checklist

- [x] Human Blueprint Approval inherited
- [x] planned files only
- [x] requirements and tests pass
- [x] no frozen semantic/dependency change
- [x] report, diff, reviewers and exact-SHA CI PASS
- [x] TASK-042 status synchronized
