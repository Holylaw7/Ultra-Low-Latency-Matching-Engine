# Phase 11 — TASK-052 Soak and Observability Foundation

## Current governance state

| Item | State |
| --- | --- |
| TASK-050 | Human closed; G3/G7 PASS / QUALIFYING / FROZEN |
| TASK-051 | Human closed; G4/G5 pre-campaign foundation complete |
| TASK-052 | IN PROGRESS; qualification-only scope approved; Remediated Implementation Evidence Review PASS |
| Implementation | COMMITTED / PUSHED at `7b00fdf571fb8fa4918b7138210693b9e79f346c` (technical-only controller) |
| Technical local evidence | PASS; six verifier findings 6/6 resolved; core and qualification validation PASS |
| Final controller-bound Quick | PASS / `QUICK_READINESS_ONLY` |
| Controller exact-SHA CI | Standard `33585928074` PASS; Quick Lane `33585928038` PASS |
| Governance checkpoint | TASK-052-only synchronization pending; exact checkpoint SHA is external provenance |
| Pre-Soak Evidence Gate | NOT PASSED |
| Formal Stage A / Stage B | NOT RUN / NOT AUTHORIZED |
| TASK-054 | NOT AUTHORIZED |
| Candidate | `v0.9.0-rc.1` immutable |

TASK-052 owns the qualification-only G6/G8 pre-campaign foundation. Its
remediated implementation, tests, child-JVM JFR inspection path, shared Quick
lifecycle, canonical evidence publication and task-specific physical binding
are present in the pushed technical controller above. TASK-052 does not
execute or claim the formal two-hour or six-hour soak campaigns.

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

The qualification test and evidence checks completed before the remediated
implementation commit:

```text
Independent Verifier findings: 6/6 resolved
direct remediation matrix: 32/32 PASS
GaJfrEvidenceTest: 6/6 PASS
focused G6/G8 direct matrix: 30/30 PASS
MatchingEnginePipelineFailureTest: 6/6 PASS
GaFrozenBoundaryVerifierTest: 6/6 PASS
mvn verify: 225 core + 177 qualification; 0 failures/errors; 2 expected skips
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

The first final controller-bound Quick for the remediated controller was run
exactly once with the validated Java 21 executable. The earlier Java-17
Attempt 1 and the prior controller-bound Quick are preserved as historical
non-qualifying evidence. Automatic retry count remains zero; each replacement
was explicitly Human-authorized.

```text
output: qualification-results/task052-remediated-controller-quick-20260902/g6-g8-quick-c12f6c09-75cb-43c9-9a8d-f1f87f0e5fb1/
controller: 7b00fdf571fb8fa4918b7138210693b9e79f346c
candidate: v0.9.0-rc.1
physicalExecutionId: 9db6e511-1cf2-48bb-9f9c-336229d62383
G6 run.id: 3162798a-e446-4b00-8544-098740b8fddf
G8 run.id: 13269302-ba72-45c1-bf22-78cfd60fceb3
G6 outcome: PASS / QUICK_READINESS_ONLY
G8 outcome: PASS / QUICK_READINESS_ONLY
```

The G6 and G8 run IDs are distinct. The binding, both manifests and both gate
results carry the remediated implementation controller, frozen candidate
identity, profile `MEMORY_STEADY_STATE_V1`, seed `20260823`, stage `QUICK` and
the same configuration identity. The evidence contains four raw inventory
payloads, five canonical payloads and ten adjacent sidecars; the hash,
inventory and binding audits passed. Quick intentionally publishes no formal
campaign summary.

The prior local pre-commit Quick evidence remains historical and untouched:

```text
physical: 842dc252-6d86-40c9-8738-25d0dfeea5d2
G6:       fc01002f-a7b9-4e22-b961-df68be9f9398
G8:       ef7de4f9-3701-4a0b-aad7-62eba31f02e1
```

It is not substituted for the final controller-bound replacement evidence.

The previously accepted controller-bound Quick for historical controller
`9140ab8215c2b170b925aadd02502d4d131a2f99` remains preserved but is now
non-qualifying after the six Independent Verifier findings. Its physical run
`5267817a-6e35-40b3-98d7-8a4755a1914f`, G6 run
`3395ec7e-991d-4d54-b052-a656fee3609e` and G8 run
`8b37e161-e6d0-4293-baf6-3a0c1924f9a5` are historical evidence only.

## Provenance and reviewer boundary

The repository uses three distinct provenance layers:

```text
qualification implementation/evidence controller:
7b00fdf571fb8fa4918b7138210693b9e79f346c

previous qualification controller (historical / reviewer-rejected):
9140ab8215c2b170b925aadd02502d4d131a2f99

repository governance checkpoint:
the current docs-only synchronization checkpoint; its exact SHA is external
Git/CI/reviewer provenance and is not embedded in its own content; the prior
post-controller checkpoint was `62a92bc162d4bab67ce50aff07f73da3614be208`;
earlier reviewed checkpoint `dc319560efa93335fe4b5fd074f5d78a21de9361` is
also historical; neither is a qualification controller

governance checkpoint self-reference rule:
a governance checkpoint commit must not be required to contain its own final
Git SHA; recording that SHA belongs to external execution provenance

post-checkpoint reviewer execution:
append-only execution evidence, not a value to predict or continuously write
back into immutable qualification provenance
```

The exact-SHA CI results for the remediated qualification implementation
object were:

```text
Standard 33585928074: PASS / head_sha exact / attempt 1
Quick Lane 33585928038: PASS / head_sha exact / attempt 1
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

## Current and next gate

`Human TASK-052 Remediated Implementation Evidence Review`: **PASS**. The
current gate is the TASK-052-only governance checkpoint and reviewer
validation; reviewer verdicts are external, append-only execution evidence and
are not written back into this checkpoint.
The next Human gate is the `TASK-052 Pre-Soak Evidence Gate Final Review` after
the authorized reviewer sequence. Until that gate, TASK-052 remains `IN
PROGRESS`, formal Stage A/B and TASK-054 remain
unauthorized, and no release, merge, tag or GA action is authorized.
