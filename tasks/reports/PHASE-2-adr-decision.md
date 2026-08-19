# Phase 2 Report - ADR / Decision

## 1. Report Metadata

| Field | Value |
| --- | --- |
| Phase | `Phase 2 - Basic OrderBook` |
| Stage | `ADR / Decision` |
| Task | `TASK-20260819-004-basic-orderbook` |
| ADR | [`ADR-0007-basic-orderbook-structure-and-boundaries.md`](../../docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md) |
| Report Date | `2026-08-19` |
| Stage Status | `Completed - Proposed`
| Next Approval Gate | `Pending Human Approval` |

## 2. Objective

在不修改生产代码和测试代码的前提下，完成 Basic OrderBook 的架构发现，
明确数据结构、行为边界、测试计划和 ADR/任务方案关联。

## 3. Reviewed Context

- `docs/architecture/overview.md`
- `docs/architecture/order-book.md`
- `docs/architecture/matching-engine.md`
- `docs/adr/ADR-0001-matching-model.md`
- `docs/adr/ADR-0002-orderbook-structure.md`
- `docs/adr/ADR-0005-domain-model-and-correctness-baseline.md`
- Phase 1 的 `Order`、值对象、Trade/Execution 和领域测试。
- `docs/benchmark/orderbook.md` 与 `docs/benchmark/matching.md`。

## 4. Findings

- ADR-0002 的 TreeMap、intrusive FIFO 和 OrderId index 方向与当前架构一致，
  可以作为 correctness baseline。
- ADR-0002 尚未定义 Bid/Ask 的具体侧边界、Best Price 读取、空价格层清理、
  取消幂等和结构化撮合结果。
- Phase 1 的 `Order` 已支持限价/市价、部分成交、完全成交和取消，但
  Market Order 没有价格，不能进入价格层。
- 当前没有 OrderBook 生产类、测试类、匹配事件输出或性能实现。
- benchmark 文档要求后续覆盖插入、Best Price、OrderId Cancel、单层/多层
  撮合和空价格层清理，尚未产生任何性能结果。

## 5. Proposed Decision

`ADR-0007` 提议：

- `OrderBook` 聚合 `BidBook` 和 `AskBook`。
- 两侧使用 side-specific `TreeMap<Price, PriceLevel>`。
- `PriceLevel` 使用 intrusive FIFO `OrderQueue` 和 `OrderNode`。
- `OrderBook` 维护 active `OrderId -> OrderNode` 索引。
- Best Bid/Ask 使用缓存，价格索引保持权威。
- Cancel 通过节点 unlink，重复或不存在的取消是幂等 no-op。
- `match` 只处理限价单，可跨多个价格层，按 maker price 生成结构化匹配片段。
- Market Order、MatchingEngine、TradeId/事件分配、网络和持久化均延后。

ADR-0007 当前状态仍为 `Proposed`，没有任何实现授权。

## 6. Scope and Deviations

本阶段只创建和同步：

- ADR-0007 草案；
- `TASK-20260819-004` Proposed 任务方案；
- Phase 2 ADR/Decision 阶段报告；
- 当前阶段、任务和审批门禁文档。

未修改：

- `src/main/java/`；
- `src/test/java/`；
- Maven 构建配置；
- OrderBook 运行时行为；
- ADR-0002 的历史内容。

没有超出 Phase 1 批准中“只进入 Phase 2 ADR / Decision 阶段”的约束。

## 7. Risks and Open Review Points

请 Human 重点审查：

1. TreeMap 与 side-specific Best Price cache 是否接受为 Phase 2 baseline。
2. active-only OrderId index 与上层负责历史/global 唯一性的边界。
3. `OrderBookMatch` 结构化结果和 maker-price 执行价语义。
4. `add` 作为非交叉 resting primitive、`match` 作为限价撮合入口的边界。
5. Market Order 是否明确延后到 Phase 3。

实现前不进行性能优化，也不将 TreeMap baseline 描述为最终性能方案。

## 8. Verification Evidence

- 已完成 Git 状态、分支、最近提交和工作区检查。
- 已完成架构、ADR、Phase 1 领域源码和测试的只读审查。
- ADR-0007 与 `TASK-20260819-004` 已互相链接，决策摘要和范围边界对齐。
- 未运行新增代码测试，因为本阶段没有新增生产代码或测试代码。

## 9. Approval Request

请 Human Developer 审批：

- `ADR-0007-basic-orderbook-structure-and-boundaries.md` 的 Proposed Decision；
- `TASK-20260819-004-basic-orderbook.md` 的 Proposed 范围、验收标准和测试计划；
- 允许进入 Phase 2 的 Task Approval / Implementation 阶段。

在审批记录完成前，Phase 2 保持：

```text
ADR / Decision: Completed - Proposed
Next Gate: Pending Human Approval
Production Code: Not Authorized
Production Tests: Not Authorized
```
