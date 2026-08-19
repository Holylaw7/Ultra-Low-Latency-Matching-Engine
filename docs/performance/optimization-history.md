# Optimization History

No optimization has been accepted yet.

`ADR-0010-optimization-decision-after-profiling.md` is proposed. Its current
decision proposal defers production optimization until setup and profiler
overhead are isolated with a separately approved measurement plan.

Each entry must use this format:

```text
## YYYY-MM-DD - Short title

### Hypothesis

### Baseline

### Change

### Measurement

### Decision

### Risks
```

An optimization is accepted only when correctness remains green and the measured result justifies the added complexity.
