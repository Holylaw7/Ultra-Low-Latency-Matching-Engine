# Phase 11 — TASK-20260825-047 Evidence Foundation Report

## Status

`Implementation complete / Evidence Gate pending exact-SHA CI`. TASK-047 was
resumed under the Human-approved Limited Schema Amendment. The amendment only
typed Git object IDs as full 40-character SHA-1 values; all SHA-256 fields remain
64 lowercase hexadecimal values. No production, candidate, dependency, RC or
release path was changed.

## Authorized implementation

The qualification-only GA package now provides:

* `GaEvidenceCodec` for canonical UTF-8 percent-encoded documents and strict
  `ga-run-manifest-v1`, `ga-gate-result-v1`, `ga-campaign-summary-v1` and
  `ga-release-manifest-v1` validation;
* field-specific `validateGitSha1` and `validateSha256` semantics;
* `GaEvidenceStore` for force/read-back/atomic immutable publication and
  sorted two-space `SHA256SUMS` sidecars;
* `GaCandidateVerifier` for SHA-1 Git object format, annotated tag object,
  peeled production commit, archive tree digest and application JAR digest;
* `GaGateEvaluator` for conjunctive PASS/blocker and campaign decisions.

The codec rejects abbreviated, uppercase, malformed, wrong-width and
cross-typed identities. Candidate tag-object and peeled-production identities
are retained as separate fields and are never inferred from one another.

## Evidence

| Gate | Result |
| --- | --- |
| Focused GA schema/store tests | 6 PASS |
| Full reactor regression | 225 core + 56 qualification tests; 0 failures; 2 expected skips |
| Checkstyle | 0 violations |
| `git diff --check` | PASS |
| Production/frozen-path audit | No production source, build, dependency or RC changes |
| `.vscode/` | untouched / untracked |
| Exact-SHA CI | Pending commit/push |

The focused tests cover 40-character Git SHA-1 acceptance, 39/41/64-width,
uppercase, non-hex and whitespace rejection, SHA-256 cross-typing rejection,
all four schema families, evaluator semantics, canonical bytes, malformed
input, immutable publication and artifact sidecar hashes.

## Frozen boundaries

TASK-048 through TASK-056 remain dependency-locked. Full campaigns, security
execution, `v1.0.0`, GitHub Release, GA declaration, tag mutation and candidate
mutation remain unauthorized. Existing `v0.9.0-rc.1` identity is unchanged:

```text
tag object: dfd38c08e80aed9035bf1c2d7c8faf8bae99c356
production: e2828f563ee41316c062385c0244ac1336731359
```
