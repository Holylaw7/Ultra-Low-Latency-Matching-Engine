# Matching Benchmark

## Status

Pending matching-engine implementation.

## Planned Workloads

- Non-crossing limit orders
- One-level crossing orders
- Multi-level sweeps
- Partial fills
- Market orders
- Cancellation-heavy workload

Correctness checks must run independently of throughput measurement so benchmark code cannot hide semantic failures.
