# Recovery Architecture

## Status

Planned. WAL and snapshot formats are not yet implemented.

## Recovery Flow

```text
Snapshot
    -> Record Snapshot Position
    -> Restart
    -> Load Snapshot
    -> Replay WAL Records
    -> Verify State Hash
```

## Required Properties

- WAL records have monotonic sequence numbers.
- Records contain enough data for deterministic replay.
- Corrupt or partial records are detected.
- Replay is idempotent only where the event semantics define it.
- Recovered state matches the state produced by the original input stream.

## Verification

Recovery tests must compare:

- Order count
- Order status
- Remaining quantity
- Price
- Last sequence
- Trade count
- State hash
