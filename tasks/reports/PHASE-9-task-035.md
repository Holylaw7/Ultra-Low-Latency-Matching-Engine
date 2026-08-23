# Phase 9 — TASK-20260823-035 / Qualification Foundation

## Status

| Field | Value |
| --- | --- |
| Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Task | `TASK-20260823-035` |
| Stage | Implementation / Verification |
| Result | Completed — Evidence Gate PASS |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Branch | `feature/phase9-system-qualification` |
| Commit | `22d13fe` |
| Tests | `12 qualification + 195 core` passed |
| Build | `mvn verify` passed |
| CI | `32625554518` PASS |
| Next Gate | TASK-036 Evidence Remediation |

## Goal

Add the isolated JDK-only qualification module and immutable deterministic
workload/manifest/result contracts required by ADR-0017. This task does not
start a runtime, open a socket, write a WAL or modify production code.

## Implemented Scope

- Added `qualification` to the Maven reactor.
- Added `QualificationConfiguration`, `QualificationProfile`,
  `QualificationWorkload`, `QualificationWorkloadV1`,
  `QualificationManifest` and `QualificationResult`.
- Added deterministic lifecycle, crossing/multi-match and bounded resting-depth
  workload profiles with default seed `20260823`.
- Added configuration bounds, contiguous sequence checks, digest validation,
  immutable collection checks and golden-vector tests.
- Golden digest vectors are fixed for all three version-one profiles.
- Manifest now binds configuration and result digests; result binding remains
  immutable and does not create qualification output.
- Manifest identity binding now rejects workload/configuration/output mismatches.
- Evidence checkpoints `176cff7` and `22d13fe` close the verifier findings from
  the initial `9dc49b5` foundation checkpoint.

## Explicitly Not Implemented

- Protocol v1 client/server lifecycle;
- long-run soak/resource sampling;
- restart/forced-termination campaign;
- JMH/JFR benchmark;
- any production source, test, WAL, Snapshot, Recovery or Pipeline change.

## Evidence Plan

```text
mvn --batch-mode --no-transfer-progress -pl qualification -am test  # PASS
mvn --batch-mode --no-transfer-progress verify                     # PASS
git diff --check                                                    # PASS
frozen-path audit                                                   # PASS (0)
exact-SHA CI                                                        # 32625554518 PASS
```

Qualification raw output is not created by this task and remains outside the
repository. `.vscode/` remains untouched/untracked.

## ADR / Blueprint Alignment

This task inherits Human Phase 9 Blueprint Approval for ADR-0017 D1-D16 and
TASK-035 through TASK-040. It implements only the TASK-035 foundation boundary;
TASK-036 remains dependency-locked until this Evidence Gate passes.

## Gate

`TASK-035 Evidence Gate` PASS at `22d13fe` / CI `32625554518`. TASK-036
implementation is present at `c7df983`, but limited evidence remediation is
required before TASK-037 can unlock.
Phase 9 Closure remains unauthorized.
