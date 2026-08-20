# Project Documentation

This directory contains durable product knowledge. It does not own project
governance or live task state.

## Source-of-Truth Boundaries

| Information | Authoritative Location |
| --- | --- |
| Agent governance | `.codex/MASTER_PROMPT.md` |
| Engineering, test, performance, security and Git rules | `.codex/DEVELOPMENT_RULES.md` |
| Compact current project state | `.codex/AGENT_CONTEXT.md` |
| Approved work, execution state and Stage Reports | `tasks/` |
| Stable architecture and engineering evidence | `docs/` |

## Documentation Map

- `architecture/`: verified structure, module boundaries and explicitly marked
  planned architecture.
- `adr/`: decisions drafted before implementation and their consequences.
- `benchmark/`: reproducible methods, environments, workloads and results.
- `performance/`: profiling evidence, hypotheses and optimization history.

Every statement must be identifiable as one of:

- **Verified Fact** — supported by code, tests, measurements or an approved
  decision.
- **Target** — desired outcome, not a measured result.
- **Hypothesis** — explanation awaiting controlled evidence.
- **Future Work** — planned but not implemented or authorized.

Measured results must include their environment and workload. Microbenchmarks
must not be presented as end-to-end system throughput. Historical ADRs and
Stage Reports remain evidence records; current state belongs in
`AGENT_CONTEXT.md` and active Task plans rather than being duplicated here.
