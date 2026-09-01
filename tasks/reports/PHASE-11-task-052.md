# Phase 11 — TASK-052 Soak and Observability Foundation

## Current governance state

| Item | State |
| --- | --- |
| TASK-050 | Human closed; G3/G7 PASS / QUALIFYING / FROZEN |
| TASK-051 | Human closed; G4/G5 pre-campaign foundation complete |
| TASK-052 | IN PROGRESS / CHANGES REQUIRED; qualification-only scope approved |
| Implementation | COMMITTED / PUSHED at `9140ab8215c2b170b925aadd02502d4d131a2f99` |
| Technical local evidence | PASS |
| Final shared Quick | PASS / `QUICK_READINESS_ONLY` |
| Pre-Soak Evidence Gate | NOT PASSED |
| Formal Stage A / Stage B | NOT RUN / NOT AUTHORIZED |
| TASK-054 | NOT AUTHORIZED |
| Candidate | `v0.9.0-rc.1` immutable |

TASK-052 owns the qualification-only G6/G8 pre-campaign foundation. Its
implementation, tests, child-JVM JFR inspection path, shared Quick lifecycle,
canonical evidence publication and task-specific physical binding are present
in the pushed qualification object above. TASK-052 does not execute or claim
the formal two-hour or six-hour soak campaigns.

## Qualification scope and frozen boundaries

The implementation is limited to qualification-side soak, observability,
evidence, binding, CLI dispatch and corresponding tests/resources. It uses
the existing `ga-run-manifest-v1` and `ga-gate-result-v1` contracts without
changing global schemas. G6 and G8 receive distinct canonical run IDs from one
shared physical Quick execution and publish independent gate results. The
task-specific `ga-g6-g8-physical-run-binding-v1` payload records their common
physical execution and does not replace either canonical manifest.

The frozen Quick contract is:

```text
one shared physical execution
stage QUICK
offered rate 200 commands/s
accepted floor 10,000
sampling 1 Hz
outcome QUICK_READINESS_ONLY
```

Formal Stage A remains a fresh independent run of at least two hours and
1,440,000 accepted commands. Formal Stage B remains a different fresh
independent run of at least six hours and 4,320,000 accepted commands. Neither
formal run was executed by TASK-052; Stage B is not an extension of Stage A.

Production sources and semantics, POMs/dependencies, workflows, candidate
bytes/tag, global evidence schemas, TASK-050/TASK-051 evidence and `.vscode/`
remain outside the implementation scope and unchanged.

## Local implementation evidence

The qualification test and evidence checks completed before the implementation
commit:

```text
GaJfrEvidenceTest: 5/5 PASS
focused G6/G8 direct matrix: 30/30 PASS
mvn verify: 225 core + 163 qualification; 0 failures/errors; 2 expected skips
Checkstyle: PASS / 0 violations
git diff --check: PASS
candidate/production/POM/dependency/workflow frozen-boundary audit: PASS
```

The JFR child-inspection contract is qualification-only. The child owns
`RecordingFile` construction, parsing and close; the parent validates a strict
bounded result and does not reopen the recording. Deterministic tests cover
constructor failure, later read failure, valid recording, missing recording
and malformed child output, including child exit and immediate parent
delete/move cleanup. Corrupt-recording semantics remain `complete=false` and
`failureCode=B3`; parser/writer defects remain classified under the frozen
B0–B3 rules.

The implementation preserves the frozen management/client split: existing
public management fields are parsed as management evidence, while completed
responses, client errors, timeouts, mismatches and response boundaries come
from qualification-client evidence. Windows process handles are diagnostic
only; declared owned transient file count/bytes and shutdown cleanup are the
resource-retention predicates.

## Controller-bound Quick evidence

The final Human-authorized replacement Quick was run exactly once with the
validated Java 21 executable. The earlier Attempt 1 used Java 17, produced no
physical execution and is preserved as `ABORTED / B3`. Automatic retry count is
zero; Human-authorized replacement count is one.

```text
output: qualification-results/g6-g8-quick-9e099d77-63ba-47d6-9fb1-b309c1cec98d/
controller: 9140ab8215c2b170b925aadd02502d4d131a2f99
candidate: v0.9.0-rc.1
physicalExecutionId: 5267817a-6e35-40b3-98d7-8a4755a1914f
G6 run.id: 3395ec7e-991d-4d54-b052-a656fee3609e
G8 run.id: 8b37e161-e6d0-4293-baf6-3a0c1924f9a5
G6 outcome: PASS / QUICK_READINESS_ONLY
G8 outcome: PASS / QUICK_READINESS_ONLY
```

The G6 and G8 run IDs are distinct. The binding, both manifests and both gate
results carry the implementation controller, frozen candidate identity,
profile `MEMORY_STEADY_STATE_V1`, seed `20260823`, stage `QUICK` and the same
configuration identity. The replacement evidence contains four raw inventory
payloads, five canonical payloads and ten adjacent sidecars; the hash,
inventory and binding audit passed. Quick intentionally publishes no formal
campaign summary.

The prior local pre-commit Quick evidence remains historical and untouched:

```text
physical: 842dc252-6d86-40c9-8738-25d0dfeea5d2
G6:       fc01002f-a7b9-4e22-b961-df68be9f9398
G8:       ef7de4f9-3701-4a0b-aad7-62eba31f02e1
```

It is not substituted for the final controller-bound replacement evidence.

## Provenance and reviewer boundary

The repository uses three distinct provenance layers:

```text
qualification implementation/evidence controller:
9140ab8215c2b170b925aadd02502d4d131a2f99

repository governance checkpoint:
this post-commit synchronization object; its SHA is established only by its
actual commit and is not a qualification controller

post-checkpoint reviewer execution:
append-only execution evidence, not a value to predict or continuously write
back into immutable qualification provenance
```

The exact-SHA CI results for the qualification implementation object were:

```text
Standard 33521246687: PASS / head_sha exact / attempt 1
Quick Lane 33521246406: PASS / head_sha exact / attempt 1
automatic retry: 0
```

The aggregate reviewer gate and Pre-Soak Evidence Gate remain open in this
checkpoint. Reviewer verdicts are recorded only as execution evidence after
they actually run; no future verdict is asserted here.

## Historical and formal boundaries

The earlier Java-17 Attempt 1 and all pre-commit local Quick outputs remain
immutable historical evidence. No historical evidence, TASK-050 G3/G7
campaign evidence or TASK-051 evidence was regenerated, backfilled, deleted or
reclassified. TASK-050 remains closed with G3/G7 `PASS / QUALIFYING / FROZEN`;
the post-closure harness exception remains disposed without reopening its
campaign. TASK-051 remains closed with its G4/G5 pre-campaign foundation
accepted.

TASK-052 closure would mean only that the G6/G8 pre-soak foundation is ready.
It does not mean formal G6/G8 qualification, two-hour or six-hour stability,
memory-leak absence, long-term resource/latency qualification, TASK-054
authorization or release approval.

## Next gate

`Human TASK-052 Implementation Evidence Review` / subsequent Pre-Soak Evidence
Gate. Until that Human review, TASK-052 remains `IN PROGRESS / CHANGES
REQUIRED`, formal Stage A/B and TASK-054 remain unauthorized, and no release,
merge, tag or GA action is authorized.
