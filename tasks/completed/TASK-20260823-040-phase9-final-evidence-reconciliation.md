# Task Plan — TASK-20260823-040

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260823-040` |
| Title | Phase 9 final qualification evidence reconciliation and Closure Proposal |
| Status | `Completed / Archived / Evidence Gate PASS; Phase 9 Closure Approved` |
| Owner | Human Developer |
| Implementer | Main Codex / Luna Max — only writer |
| Related Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Related ADR | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) |
| Phase Blueprint | [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md) |
| Dependency | TASK-039 Evidence Gate PASS |
| Current Gate | Phase 9 baseline frozen |
| Next Gate | Phase 10 / Product Release require separate authorization |
| Branch | `master` |
| Baseline | `v0.8.0-engineering-baseline` / `ef73f60` |

## 2. Objective

Reconcile the final Phase 9 qualification evidence across TASK-035 through
TASK-039, record the approved claim boundaries and known limitations, and
prepare the Phase 9 Closure Proposal. This task is documentation/evidence-only.
It must not change production, test, benchmark,
dependency, protocol, WAL, Snapshot or recovery semantics.

## 3. Authorized Scope

- add the TASK-040 task plan and report;
- synchronize Phase 9 Blueprint status and evidence references;
- synchronize `README.md`, `.codex/AGENT_CONTEXT.md` and the blueprint index;
- reconcile task status, commit, CI, reviewer and artifact references;
- record the final Closure Proposal and the required stop at Sol High review.

## 4. Forbidden Scope

- any `src/main/**`, `src/test/**`, `core/**`, qualification implementation or
  benchmark implementation change;
- dependency, JVM/GC, workload, threshold or runtime configuration changes;
- rewriting, filtering or regenerating ignored qualification/benchmark/JFR
  artifacts;
- Phase 9 Closure approval, merge, tag, `v0.8.0-engineering-baseline`,
  Product Release or Phase 10 work;
- handling `.vscode/`; the pre-existing directory remains untouched and
  untracked.

## 5. Acceptance Criteria

- [x] One authoritative evidence matrix covers TASK-035 through TASK-039.
- [x] Final commit/CI/reviewer references are internally consistent and no
  superseded TASK-039 pending-status text remains in current documents.
- [x] The TASK-039 JMH/JFR matrix and raw artifact hashes are recorded without
  filtering outliers or upgrading component evidence to a production claim.
- [x] TASK-037 A'/B' campaign and TASK-038 20/10 campaign evidence are kept
  separate and their historical non-qualifying evidence remains preserved.
- [x] Known limitations and explicitly unclaimed guarantees remain visible.
- [x] Closure Proposal was prepared and accepted by Sol High and Human review.
- [x] No production/test/benchmark/dependency diff is introduced.
- [x] `git diff --check`, `mvn verify`, approved-path audit, verifier,
  benchmark-reviewer and docs-auditor all pass.
- [x] Exact-SHA CI passes for the Closure Input `8e5d39d`:
  Standard `32709188522` PASS and Qualification Quick Lane `32709188327` PASS.

## 6. Evidence Gate

```text
TASK-040 docs/evidence changes
        ↓
focused stale-evidence and link checks
        ↓
mvn verify / Checkstyle / git diff --check
        ↓
production, test, benchmark and dependency diff = 0
        ↓
verifier + benchmark-reviewer + docs-auditor (read-only) PASS
        ↓
exact-SHA CI PASS
        ↓
Sol High Phase 9 Final Closure Review — APPROVED
        ↓
Human Phase 9 Closure Approval — APPROVED
        ↓
merge `ef73f60` / Master CI `32711512036` PASS
        ↓
`v0.8.0-engineering-baseline` / Tag CI `32711649980` PASS
        ↓
Phase 9 baseline frozen
```

## 7. Exception Gate

Stop and request Sol High review if reconciliation requires changing a
technical acceptance criterion, production/runtime semantics, benchmark
methodology, frozen format, workload/threshold, dependency or any scope not
listed above.
