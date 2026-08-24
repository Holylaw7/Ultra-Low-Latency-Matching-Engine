# Phase 9 — TASK-20260823-038 / Restart, Forced Termination and Recovery Convergence

## Status

| Field | Value |
| --- | --- |
| Task | `TASK-20260823-038` |
| Result | `In Progress — focused child-process implementation complete; Full campaign pending` |
| Dependency | TASK-037 Human Closure Approved |
| Branch | `feature/phase9-system-qualification` |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Next Gate | Focused Evidence Gate / exact-SHA CI; TASK-039 remains locked |

## Implemented Scope

TASK-038 now contains a qualification-only child JVM entry point and parent
campaign runner. The parent drives commands through the real Protocol v1 TCP
client. A graceful cycle sends an explicit shutdown command after complete
response acknowledgement. A forced cycle calls process termination only after
the complete response boundary, so no in-flight outcome is upgraded to an
exactly-once claim.

After every cycle, the runner strictly reads the WAL prefix and runs offline
`PURE_WAL` recovery. It checks the WAL end sequence, next command sequence and
recovered checkpoint convergence before publishing an immutable cycle artifact.
The campaign summary references cycle artifact SHA-256 values and publishes a
separate artifact-hash sidecar. Runtime claims remain qualification-only.

## Focused Evidence

The focused test executes two graceful and two forced child-process cycles over
the public Protocol v1 boundary and repeats the bounded campaign. Both campaigns
converge on checkpoint, transcript, public-probe and WAL command digests.

```text
Focused test:
  QualificationRestartCampaignRunnerTest: 1 passed
Full reactor tests:
  core: 195 passed
  qualification: 46 tests, 0 failures, 2 explicitly skipped
  mvn verify: PASS
Checkstyle: 0 violations
git diff --check: PASS
```

The approved Full campaign remains a separate evidence unit requiring 20
graceful restart cycles and 10 acknowledged-boundary forced terminations. It
has not been started automatically by this implementation checkpoint.

The local Evidence Gate is therefore limited to the bounded implementation
proof above. The 20/10 campaign remains pending and is not represented as a
passing qualification result.

## Claim Boundary

This task does not claim hardware power-loss safety, reconnect/deduplication,
exactly-once client outcomes, production availability, RTO/SLA or Product
Release. An acknowledged-boundary forced termination proves only that the
durable WAL prefix and subsequent offline recovery converge; an unacknowledged
in-flight outcome remains ambiguous.

## Frozen Boundary

No production source, production tests, dependency, Protocol v1, WAL v1,
Snapshot v1 or recovery semantic files were modified. `.vscode/` remains
untouched and untracked. TASK-039, Phase 9 Closure, merge and baseline tagging
remain unauthorized.

## Evidence Gate

Before TASK-039 can unlock, run focused and full verification, frozen-path
audit, `git diff --check`, verifier/docs-auditor read-only review and exact-SHA
CI. Any production boundary change, recovery semantic change, new dependency,
workload/threshold tuning, or weakened ambiguous-outcome claim triggers the
Exception Gate.
