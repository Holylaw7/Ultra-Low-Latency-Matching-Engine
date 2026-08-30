# Phase 11 — TASK-050 Harness Implementation Checkpoint

## Governance state

| Item | State |
| --- | --- |
| TASK-049 | Human closed; G1/G2 PASS / QUALIFYING / FROZEN |
| TASK-050 scope | Human approved |
| Harness implementation | Limited remediation implemented; Remediation Evidence Gate pending Human review |
| Formal G3/G7 campaign | Not authorized and not run |
| Candidate | `v0.9.0-rc.1` immutable |
| TASK-051 | Locked |
| Product release / GA | Not authorized |

## Implemented qualification boundary

The qualification module now contains bounded, deterministic G3 and G7
harnesses only. They do not modify the production runtime, POM, dependency
graph, candidate tag or previously frozen G1/G2/G9/G11 evidence.

The focused matrices and their canonical-publication assertions are local
test fixtures only. They are not qualifying G3/G7 campaign evidence and must
not be promoted to a Gate PASS before a separately authorized formal campaign
and final evidence review.

G3 covers the approved lifecycle and WAL corruption fixture vocabulary,
including strict reader rejection, final-tail handling, sequence/metadata
validation, snapshot/WAL corruption and rotation-path collision observation.
The approved matrix uses the legal 4,128-byte production minimum rather than
the removed 4,096-byte value. Each focused run publishes raw evidence, an
inventory, sidecars and a canonical `ga-run-manifest-v1`; the gate and campaign
results use the existing global GA evidence codec. The forced lifecycle lane
uses a real child-JVM process boundary after a completed response and does not
claim arbitrary in-flight exactly-once or hardware power-loss safety.

G7 covers the bounded public/runtime boundaries available without a production
seam: repeated second-session rejection, coalesced request handling, protocol
frame limits, bounded pipeline-full admission, management request limits,
durable terminal-full behavior and runtime/evidence resource bounds. Each
scenario records its expected observable contract; arbitrary timeout, close or
lack of progress is not converted into a passing bound claim.

## Focused verification

The following focused tests pass locally:

```text
GaDurabilityMatrixTest
GaDurabilityRunnerTest
GaOverloadMatrixTest
GaOverloadRunnerTest
```

The focused G3 test uses two lifecycle cycles and four corruption fixtures; the
focused G7 test exercises all seven bounded probes (session, pipelined, frame,
pipeline-full, management, durable-full and resource). Both verify canonical
gate publication and immutable sidecars. The qualification CLI now accepts:

```text
ga-durability --matrix <ga-g3-g7-v1|ga-g3-g7-test-v1> [--output <dir>]
ga-overload --matrix <ga-g7-overload-v1|ga-g7-overload-test-v1> [--output <dir>]
```

The final local reactor verification also passes:

```text
mvn verify: 225 core tests + 94 qualification tests
failures: 0
expected skips: 2
Checkstyle violations: 0
```

Two full-run observations have reproduced the already-known transient
`MatchingEnginePipelineFailureTest` failure. Each focused rerun passed 6/6, and
the final full reactor run passed without any production or test change.

## Explicit pending items

The Human-approved A1 amendment removed the invalid 4,096-byte WAL segment
value. The minimum qualifying value is now the frozen production
`WalCommandCodec.MIN_SEGMENT_SIZE_BYTES` (4,128 bytes); the runner rejects any
lower value rather than mutating the production contract.

Formal G3/G7 execution remains a separate one-shot Human gate. This checkpoint
does not produce qualifying campaign evidence and does not unlock TASK-051.

## Boundary audit

```text
production source diff: 0
root/core POM diff: 0
candidate/tag mutation: 0
G1/G2/G9/G11 evidence mutation: 0
.vscode/: untouched and untracked
```

Historical failures and all earlier qualifying evidence remain preserved under
their original contracts. No formal G3/G7 campaign or release action was
started by this implementation checkpoint.
