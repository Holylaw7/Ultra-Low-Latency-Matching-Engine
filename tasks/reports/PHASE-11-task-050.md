# Phase 11 — TASK-050 Harness Implementation Checkpoint

## Governance state

| Item | State |
| --- | --- |
| TASK-049 | Human closed; G1/G2 PASS / QUALIFYING / FROZEN |
| TASK-050 scope | Human approved |
| Harness implementation | Round 3.1 checkout-contract remediation implemented locally; commit/remote Evidence Gate pending |
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
mvn verify: 225 core tests + 102 qualification tests
failures: 0
expected skips: 2 (100 qualification tests executed)
Checkstyle violations: 0
```

## Round 3 evidence-contract remediation

The approved Round 3 remediation was committed and pushed at
`ee31e9a82cea9eb302024eb76f1179002e6b4508`.  It remains qualification-only
and does not authorize the formal G3/G7 campaign.

The canonical durability publisher now binds every manifest to the complete
run inventory: each payload and the inventory itself has an adjacent
SHA-256 sidecar, every inventory entry is rehashed during validation, the
manifest inventory must exactly match `SHA256SUMS`, and unlisted or symbolic
filesystem entries fail closed.  Candidate and controller identities are
validated against the execution context before a campaign or gate can be
published.

The existing `ga-run-manifest-v1` contract already supports `ABORTED`; the
publisher now exposes an explicit aborted-run path and propagates that
outcome through campaign and gate evidence.  An aborted member can never be
evaluated as a PASS, while its raw evidence remains published for audit.

The qualification context also performs a deterministic frozen-boundary
check over the repository production source, tests, root POM and core POM
between the approved production commit and the controller commit.  Any
unexpected change aborts qualification; no production or candidate file was
modified by this remediation.

Round 3 regression coverage includes missing/tampered payload and inventory
evidence, candidate/controller mismatch, explicit ABORTED propagation and
frozen-boundary identity checks.  These tests are qualification fixtures and
are not formal G3/G7 campaign evidence.

Two earlier full-run observations reproduced the already-known transient
`MatchingEnginePipelineFailureTest` failure. Each focused rerun passed 6/6, and
the final full reactor run passed without any production or test change.  That
historical symptom is distinct from the current Standard CI failure below.

## Round 3.1 Standard checkout-contract remediation

Standard CI run `33374002293` evaluated the exact Round 3 SHA and failed in
`GaFrozenBoundaryVerifierTest.checksTheCurrentRepositoryWithoutChangingIt`.
The verifier could not resolve the immutable production baseline
`e2828f563ee41316c062385c0244ac1336731359` because the Standard workflow's
checkout was shallow.  The failure is classified as a deterministic B2
qualification/test--CI integration defect; no candidate or production defect
was observed.  Quick Lane `33374002245` passed the same SHA but runs only the
quick smoke test.

The Human-approved Round 3.1 remediation adds an explicit full-history
checkout contract to Standard CI.  The change is implemented locally and its
commit/remote Evidence Gate remains pending; no formal G3/G7 campaign is
authorized.

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
started by this implementation checkpoint. Round 3 is preserved at
`ee31e9a82cea9eb302024eb76f1179002e6b4508`; Round 3.1 is currently a local
qualification-only change awaiting its separately authorized commit/push and
remote Evidence Gate. Formal G3/G7 execution remains separately
Human-authorized.
