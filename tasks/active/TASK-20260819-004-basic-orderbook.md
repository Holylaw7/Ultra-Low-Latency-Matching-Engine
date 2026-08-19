# Task Plan - TASK-20260819-004

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260819-004` |
| Title | Establish Basic OrderBook baseline |
| Status | `In Progress` |
| Owner | Human Developer |
| Implementer | Codex |
| Created | `2026-08-19` |
| Updated | `2026-08-19` |
| Related Phase | `Phase 2 - Basic OrderBook` |
| Related ADR | [`ADR-0007-basic-orderbook-structure-and-boundaries.md`](../../docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md) |
| Current Stage | `Implementation - OrderBook + active OrderId index (Completed)` |
| Next Approval Gate | `Pending Human Approval` |

## 2. Background

Phase 1 已完成并经 Human Developer 批准，提供了稳定的 `Order`、价格、
数量、序列和状态迁移语义。现有 `ADR-0002` 提供了 TreeMap、intrusive
FIFO 和 OrderId 索引的初始方向，但还没有覆盖 Basic OrderBook 所需的
取消、Best Bid/Ask、空价格层和结构化撮合边界。

本方案根据 Phase 2 架构审查创建，已于 `2026-08-19` 获得 Human Developer
批准。实现必须严格遵守 ADR-0007 的范围和约束，并按子阶段完成、报告和审批。

## 3. Goal

建立一个清晰、可测试、确定性的 Basic OrderBook 基线，支持：

- `BidBook` 和 `AskBook`；
- `PriceLevel` 和 intrusive FIFO `OrderQueue`；
- active `OrderId -> OrderNode` 取消索引；
- Add、Cancel、Best Bid、Best Ask；
- 跨一个或多个价格层的限价结构化撮合；
- 空价格层清理和最终状态不变量验证。

## 4. Non-Goals

- 不实现完整 `MatchingEngine` 事件编排。
- 不确定或实现 Market Order 策略。
- 不引入 Netty、Disruptor/RingBuffer、WAL、Snapshot、Recovery 或网络协议。
- 不实现 Custom Tree、SkipList、Radix、Price Array、off-heap 或 cache-line
  优化。
- 不分配全局 `OrderId` 或 `Sequence`。
- 不产生未经 Benchmark 证明的性能结论。
- 不处理 benchmark uber-jar 的已知 Shade Plugin 警告。

## 5. Requirements and Acceptance Criteria

### Requirements

- [x] `BidBook` 只接受买方限价单，按高价优先。
- [x] `AskBook` 只接受卖方限价单，按低价优先。
- [x] 同价订单按输入顺序和 `Sequence` 语义保持 FIFO。
- [x] `PriceLevel` 维护价格、队列、订单数量和剩余数量总和。
- [x] 已知节点的取消不扫描整个订单队列。
- [x] 取消后立即清理空价格层并更新 Best Bid/Ask。
- [x] `BestBid` 是最高存活买价，`BestAsk` 是最低存活卖价。
- [ ] 结构化限价撮合支持部分成交、完全成交和跨多个价格层。
- [ ] 撮合后剩余限价单按原有状态和优先级进入队列。
- [ ] 相同输入序列产生相同匹配片段和最终 OrderBook 状态。

### Acceptance Criteria

- [x] 生产代码只位于 OrderBook 领域包，不依赖网络、持久化或线程调度。
- [x] 非法 side/type/status、重复 active OrderId 和无效残量被显式拒绝。
- [x] 取消操作幂等，重复取消不改变状态且不抛出业务异常。
- [x] 价格层、队列、active index 和 Best Price 缓存的一致性有测试证明。
- [ ] 一层和多层撮合、同价 FIFO、部分成交、完全成交和空层删除测试通过。
- [x] 根 Maven 构建、JUnit 和 Checkstyle 通过。
- [x] 实现前后 ADR、任务方案、架构文档和 `AGENT_CONTEXT.md` 保持一致。
- [ ] 相关基线 Benchmark 只在实现获批后执行，并保留可重复参数和结果。

## 6. Current Implementation and Scope

### Current Implementation

当前已完成 `OrderNode`、`OrderQueue`、`PriceLevel`、`BidBook / AskBook` 和
`OrderBook + active OrderId index` 子阶段。Phase 1 的 `Order` 已经提供：

- limit/market 工厂方法；
- `Side`、`OrderType`、`OrderStatus`；
- `remainingQuantityUnits()`；
- 受控 `applyExecution()` 和幂等 `cancel()`。

当前尚未实现结构化撮合结果和限价撮合遍历。

### In Scope

- `src/main/java/com/ultralatency/matching/orderbook/`
- `src/test/java/com/ultralatency/matching/orderbook/`
- 与已批准决策一致的 OrderBook ADR 和架构文档同步。
- `.codex/AGENT_CONTEXT.md` 和本任务的阶段报告。

### Out of Scope

- `MatchingEngine`、事件输入、Trade/Execution 外发。
- Market Order、网络、Pipeline、WAL、Snapshot、Recovery。
- 性能替代结构和最终内存布局。

## 7. Design Proposal

### Proposed Design

采用 ADR-0007 的基线：

```text
OrderBook
    +-- BidBook: TreeMap<Price, PriceLevel> descending
    +-- AskBook: TreeMap<Price, PriceLevel> ascending
    +-- active OrderId -> OrderNode
```

每个 `PriceLevel` 拥有一个 intrusive FIFO `OrderQueue`。`OrderNode` 持有
`Order`、所属价格层、`prev` 和 `next`。`OrderBook` 维护每侧的 Best Price
缓存，价格层索引仍是唯一权威来源。

`add` 是非交叉限价单的 resting primitive；可能成交的 incoming limit order
通过 `match` 进入。`match` 使用价格优先、同价 FIFO，按 maker price 产生
结构化匹配片段，填满或取消的订单离开 live queue，剩余限价单重新进入
合适的价格层。

### Alternatives Considered

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
| TreeMap + intrusive queue + active OrderId index | 清晰、可验证、与 ADR-0002 一致 | 对象和 O(log P) 价格层变更不是最终性能方案 | Accepted with constraints |
| Custom balanced tree / SkipList | 可能改善局部性或分配 | 实现复杂，尚无基准证明 | Deferred |
| Price Array / Radix | 密集价格区间可能更快 | 需要固定范围或额外映射，当前没有该约束 | Deferred |

### Decision

已接受 ADR-0007 的 Decision，状态为 `Accepted with constraints`。Human
Developer 于 `2026-08-19` 批准进入 Implementation 阶段，确认：

1. TreeMap 和 side-specific best cache 是否作为 Phase 2 baseline。
2. active-only OrderId index 和由上层负责全局唯一性的边界。
3. `OrderBookMatch` 结构化匹配片段以及 maker-price 语义。
4. Market Order、MatchingEngine、Trade/Execution 创建或发布、WAL、网络、
   Disruptor、lock-free、off-heap、Custom Tree、SkipList、Radix 和未经
   Benchmark 证明的性能优化均不在本任务范围内。
5. 如需修改 Phase 1 `Order.applyExecution()`，必须暂停并重新审查
   ADR-0005，不得在本任务中直接修改。

### ADR Linkage

| Field | Value |
| --- | --- |
| ADR | [`docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md`](../../docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md) |
| Status | `Accepted with constraints` |
| Decision Summary | 使用 side-specific TreeMap、PriceLevel intrusive FIFO、active OrderId -> OrderNode 索引和 Best Price 缓存；支持 Add、Cancel、Best Bid/Ask 和结构化限价撮合；Market Order 与 MatchingEngine 延后。 |
| Scope Boundary | 仅允许 Basic OrderBook 结构、取消、最佳价、限价结构化撮合和正确性测试；禁止 MatchingEngine、Market Order、网络、Pipeline、WAL、Recovery、性能替代结构和性能结论。 |

该 ADR 草案先于本任务的技术决策和任务审批创建。ADR 与本方案的决策摘要、
范围边界和验证计划必须保持一致；任何差异都必须暂停实现并重新审批。

### Architecture Impact

- [ ] No architecture change
- [x] ADR required: `ADR-0007-basic-orderbook-structure-and-boundaries.md`
- [x] Human architecture decision required

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `src/main/java/com/ultralatency/matching/orderbook/` | 新增 OrderBook、BidBook、AskBook、PriceLevel、OrderQueue、OrderNode 和匹配结果类型 | 建立 Phase 2 基线结构 |
| `src/test/java/com/ultralatency/matching/orderbook/` | 新增结构、边界、取消和撮合测试 | 固定价格时间优先级和状态不变量 |
| `docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md` | 记录 OrderBook 决策 | 在实现前冻结可审查的结构和边界 |
| `docs/architecture/order-book.md` | ADR 获批后同步实现边界和不变量 | 保持架构文档与 ADR 一致 |
| `.codex/AGENT_CONTEXT.md` | 每阶段完成后同步当前任务和审批状态 | 支持会话恢复 |
| `tasks/reports/PHASE-2-*.md` | 记录 ADR/Decision、实现、验证和同步阶段报告 | 建立逐步审批 hand-off |

## 9. Test Plan

### Unit Tests

- [x] PriceLevel queue append, head/tail, count and total quantity.
- [x] Bid price priority and Ask price priority.
- [x] Same-price FIFO using accepted input sequence.
- [x] Active index lookup and node unlink behavior.
- [x] Best Bid/Ask after add, cancel, fill and empty-level cleanup.

### Integration or System Tests

- [ ] OrderBook add/cancel/match across both sides.
- [ ] One-level and multi-level limit matching.
- [ ] Residual order resting and final non-crossed state.

### Failure and Boundary Tests

- [x] Reject market orders from resting `add`.
- [x] Reject wrong-side, duplicate-active-id, terminal-order and zero-residual inputs.
- [x] Verify absent and repeated cancellation are no-op and non-throwing.
- [x] Verify empty-book and empty-side best-price results.
- [x] Verify no stale PriceLevel remains after final removal.

### Determinism or Replay Tests

- [ ] Same ordered inputs produce equal match fragments and final state.
- [ ] State summary/hash is independent of HashMap iteration or object identity.

## 10. Benchmark and Profile Plan

- Benchmark: `Not executed in ADR / Decision stage`
- Profile: `Not applicable before an approved implementation`
- Dataset and distribution: identical generated event streams for all baselines
- Metrics: price insert, best lookup, cancel, one-level match, multi-level match,
  empty-level cleanup; later include throughput, latency, allocation and GC
- Baseline: TreeMap + intrusive queue + active OrderId index

No performance claim is authorized by this task plan. Any alternative structure
requires a separate evidence-backed decision or an approved scope update.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Best-price cache becomes stale | Wrong match or quote | Keep TreeMap authoritative and test every add/remove boundary |
| Active-only index cannot prove historical ID uniqueness | Duplicate IDs across terminal history | Make global uniqueness an explicit MatchingEngine/event-source responsibility |
| OrderBookMatch diverges from Trade/Execution | Phase 3 integration rework | Test field mapping and keep TradeId/sequence allocation outside OrderBook |
| TreeMap baseline is too allocation-heavy | Performance ceiling | Measure before considering custom structures; do not optimize in this task |
| Market-order semantics leak into Basic OrderBook | Scope and architecture drift | Reject market resting and defer market matching to Phase 3 |

## 12. Rollback Plan

Before implementation, delete this Proposed task and ADR only if Human rejects or
cancels the design, preserving the existing ADR-0002 baseline. After
implementation, revert the single logical OrderBook commit without changing
Phase 1 domain semantics or persistence formats.

## 13. Verification Commands

Decision stage:

```text
git status --short --branch
git diff --check
review ADR-0002, ADR-0005, order-book architecture, matching architecture,
Phase 1 domain source and tests
```

Implementation stage after approval:

```text
mvn -pl core -am test
mvn verify
git diff --check
```

Verification stage after implementation:

```text
mvn -pl core -am test
mvn verify
run the approved OrderBook baseline benchmark with recorded parameters
git diff --check
git status --short --branch
```

## 14. Git Commit Plan

Decision-stage proposal:

```text
docs(orderbook): propose basic orderbook baseline
```

Implementation:

```text
feat(orderbook): implement basic orderbook baseline
```

Tests may be included in the implementation commit when they form one
independently verifiable logical change. Benchmark and documentation results
must not be mixed into an unrelated implementation commit.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
| 2026-08-19 | Human Developer | Phase 1 hand-off | `Approved` | Authorized Phase 2 ADR / Decision stage only. No Phase 2 production code or tests authorized yet. |
| 2026-08-19 | Human Developer | ADR / Decision and Task Plan | `Approved` | Approved ADR-0007 and TASK-20260819-004 for implementation. TreeMap + intrusive FIFO + active OrderId index + best-price cache is the Phase 2 baseline. Market Order, MatchingEngine, WAL, network, Disruptor, lock-free, off-heap, custom tree, SkipList, radix, and unproven performance optimization remain out of scope. |
| 2026-08-19 | Human Developer | Implementation Sub-stage 1 | `Approved` | OrderNode, OrderQueue and PriceLevel baseline accepted. FIFO, O(1) unlink, cancel, partial/full fill, quantity invariants, tests and build verification passed. Shade Plugin overlap warnings remain deferred technical debt. BidBook / AskBook implementation authorized within ADR-0007 scope. |
| 2026-08-19 | Human Developer | Implementation Sub-stage 2 | `Approved` | SideBook, BidBook and AskBook accepted. Price ordering, Best Bid/Ask, FIFO preservation, empty-level cleanup, validation, tests and Maven verification passed. OrderBook aggregate and active OrderId index authorized. |

## 16. Phase Reports and Approval Gates

| Stage | Report Location | Status | Next Approval Gate | Human Approval |
| --- | --- | --- | --- | --- |
| ADR / Decision | [`tasks/reports/PHASE-2-adr-decision.md`](../reports/PHASE-2-adr-decision.md) | `Completed` | `Approved` | Approved 2026-08-19 |
| Task Approval |  | `Completed` | `Approved` | Approved 2026-08-19 |
| Implementation - Sub-stage 1 | [`tasks/reports/PHASE-2-implementation-ordernode-queue-pricelevel.md`](../reports/PHASE-2-implementation-ordernode-queue-pricelevel.md) | `Completed` | `Implementation - Sub-stage 2` | Approved 2026-08-19 |
| Implementation - Sub-stage 2 | [`tasks/reports/PHASE-2-implementation-bidbook-askbook.md`](../reports/PHASE-2-implementation-bidbook-askbook.md) | `Completed` | `Approved` | Approved 2026-08-19 |
| Implementation - Sub-stage 3 | [`tasks/reports/PHASE-2-implementation-orderbook-active-index.md`](../reports/PHASE-2-implementation-orderbook-active-index.md) | `Completed - Pending Human Approval` | `Pending Human Approval` | Pending |
| Verification |  | `Pending` | `Pending Human Approval` |  |
| Documentation and Synchronization |  | `Pending` | `Pending Human Approval` |  |

本阶段报告已由 Human Developer 审批，允许进入 Implementation 阶段。实现仍须
按子阶段输出报告，并在下一阶段前等待 Human approval。当前
`OrderBook / active OrderId index` 子阶段已完成，等待审批后才能进入
结构化限价撮合子阶段。

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-19 | Proposed | 完成 ADR-0002、ADR-0005、OrderBook 架构、Matching Engine 架构和 Phase 1 API 审查；创建 ADR-0007 草案和本任务方案 | 只读审查完成；未修改生产代码或测试代码 |
| 2026-08-19 | Completed - Pending Human Approval | Human Developer 已批准 ADR-0007 和本任务方案；完成子阶段 `OrderNode + OrderQueue + PriceLevel` | `mvn verify` 成功；21 tests，0 failures，Checkstyle 0 |
| 2026-08-19 | In Progress | Human Developer 批准 Sub-stage 1，并授权 `BidBook / AskBook` 子阶段 | 待完成价格层索引、最佳价、空层清理及阶段验证 |
| 2026-08-19 | Completed - Pending Human Approval | 完成 `BidBook / AskBook` 价格层索引、Best Price 和空层清理 | `mvn verify` 成功；28 tests，0 failures，Checkstyle 0 |
| 2026-08-19 | Completed - Pending Human Approval | Human Developer 批准 Sub-stage 2；完成 `OrderBook` 聚合、active `OrderId -> OrderNode` 索引、OrderBook 级 add/cancel/lookup 和受控执行状态同步 | `mvn -pl core -am test` 与 `mvn verify` 成功；34 tests，0 failures，Checkstyle 0；`git diff --cached --check` 通过；提交 `feat(orderbook): add orderbook aggregate and active index` |

## 18. Completion Checklist

- [ ] Scope and acceptance criteria satisfied
- [x] Tests added or updated
- [x] Build passed
- [x] Static or format checks passed
- [ ] Benchmark or profile completed when applicable
- [x] Documentation updated
- [x] Decision and ADR linkage recorded
- [x] ADR existed before the technical decision and task approval
- [x] Every completed stage has a phase report
- [x] Human approval is recorded before each next stage
- [x] ADR, task plan, rules, project documents, and `AGENT_CONTEXT.md` are synchronized
- [x] `AGENT_CONTEXT.md` updated
- [x] Diff reviewed
- [x] Commit created
- [x] Post-commit Git status confirmed
