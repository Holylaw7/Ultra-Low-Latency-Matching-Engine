# Phase 9 — TASK-20260823-038 / Restart, Forced Termination and Recovery Convergence

## Status

| Field | Value |
| --- | --- |
| Task | `TASK-20260823-038` |
| Result | `Completed — Full 20/10 campaign PASS; Evidence Gate PASS` |
| Dependency | TASK-037 Human Closure Approved |
| Branch | `master` |
| Baseline | `v0.8.0-engineering-baseline` / `ef73f60` |
| Next Gate | `Phase 9 Closure Approved; baseline frozen` |

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

The local implementation Evidence Gate is supported by the bounded proof
above. The separately authorized Full campaign is recorded below as an
independent evidence unit; it does not by itself authorize TASK-039 or Phase 9
Closure.

## Full Restart/Termination Campaign Evidence

The separately authorized Full campaign completed once, without retry or
configuration change, using `CROSSING_MULTI_MATCH`, seed `20260824`, 10,000
commands and the JDK 21 runtime configuration used by the focused harness:

```text
20 graceful restart cycles: PASS
10 acknowledged-boundary forced terminations: PASS
30/30 cycles: convergencePassed=true
acceptedCommands: 10,000
responseCount: 16,667
tradeCount: 6,667
campaign.result: true
```

The immutable local evidence directory is:

```text
qualification-results/task038-full-20260824/
  restart-campaign-575e3dec-cfd9-46a2-9ec4-4dee8fb559f8/
```

The raw directory is an ignored local qualification evidence root, not a
tracked source artifact or a CI-generated substitute. It must remain preserved
for the Evidence Gate; the report records its immutable summary and sidecar
hashes rather than committing generated files.

The published summary SHA-256 is
`d18850bfdcff51722a7431e2d0679f98687577ed5cca8a574bf5c076072e3576`.
The artifact sidecar contains 31 entries (summary plus 30 cycle artifacts),
and an independent read-only hash check reported zero mismatches. Graceful
cycles exited with code 0; forced cycles exited with code 1 after the complete
response acknowledgement boundary. Exactly-once, reconnect and hardware
power-loss claims remain explicitly `NOT_CLAIMED`.

The campaign evidence checkpoint was `a7a98cb` with exact-SHA Standard CI
`32698925401` PASS and Qualification Quick Lane `32698925378` PASS. This
checkpoint is the technical input for the current read-only Evidence Gate;
any later documentation-only synchronization has its own external CI result.

The evidence synchronization checkpoint `da5ac1f` passed Standard CI
`32699178851` and Qualification Quick Lane `32699178800`. The verifier and
docs-auditor both returned `PASS`; the task is complete for its approved
restart/termination scope. TASK-039 subsequently completed its JMH/JFR
Evidence Gate, and TASK-040 has completed the final Phase 9 evidence
reconciliation. Sol High review and Human Phase 9 Closure Approval are complete;
merge `ef73f60` and baseline tag `v0.8.0-engineering-baseline` are CI-verified.

## Claim Boundary

This task does not claim hardware power-loss safety, reconnect/deduplication,
exactly-once client outcomes, production availability, RTO/SLA or Product
Release. An acknowledged-boundary forced termination proves only that the
durable WAL prefix and subsequent offline recovery converge; an unacknowledged
in-flight outcome remains ambiguous.

## Frozen Boundary

No production source, production tests, dependency, Protocol v1, WAL v1,
Snapshot v1 or recovery semantic files were modified. `.vscode/` remains
untouched and untracked. Phase 9 Closure is approved; Phase 9 is frozen at
`v0.8.0-engineering-baseline`.

## Evidence Gate

The TASK-038 Evidence Gate is PASS. Any production boundary change, recovery
semantic change, new dependency, workload/threshold tuning, or weakened
ambiguous-outcome claim in later work triggers the Exception Gate. TASK-039 is
complete and TASK-040 Evidence Gate is PASS; current Closure Input is
`8e5d39d` / Standard CI `32709188522` PASS / Quick Lane `32709188327` PASS.
Human Phase 9 Closure Approval is complete; merge `ef73f60` / Master CI
`32711512036` and baseline tag `v0.8.0-engineering-baseline` / Tag CI
`32711649980` are PASS.
