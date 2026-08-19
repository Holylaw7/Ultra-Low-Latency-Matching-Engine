# Task Plan — TASK-20260819-002

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260819-002` |
| Title | Establish domain model and correctness baseline |
| Status | `Proposed` |
| Owner | Human Developer |
| Implementer | Codex |
| Created | `2026-08-19` |
| Updated | `2026-08-19` |
| Related Phase | Phase 1 - Domain Model |
| Related ADR | `None` |

## 2. Background

Phase 0 工程骨架已经建立，但撮合领域模型尚未实现。后续 OrderBook、Matching Engine、WAL 和 Replay 都需要稳定、可验证的领域对象和交易语义作为基础。

## 3. Goal

建立最小领域模型和正确性基线，为后续 OrderBook 实现提供明确的类型、状态和不变量。

## 4. Non-Goals

- 不实现 OrderBook。
- 不实现撮合算法。
- 不引入 Netty、Disruptor 或 WAL。
- 不进行性能优化或发布性能结论。

## 5. Requirements and Acceptance Criteria

### Requirements

- [ ] 定义 `Side`、`OrderType` 和 `OrderStatus`。
- [ ] 定义不可变或受控变更的订单标识、价格、数量和序列语义。
- [ ] 定义 `Order`、`Trade` 和必要的执行结果类型。
- [ ] 明确非法价格、数量、标识和状态转换的处理方式。
- [ ] 建立领域模型单元测试。

### Acceptance Criteria

- [ ] 所有领域类型具有明确的不变量。
- [ ] 订单状态转换测试覆盖正常、非法和重复操作。
- [ ] 相同输入不会依赖系统时间或线程调度产生不同结果。
- [ ] 根 Maven 构建、测试和 Checkstyle 通过。
- [ ] 不引入未审批的架构变化或第三方依赖。

## 6. Current Implementation and Scope

### Current Implementation

当前仓库只有应用占位类和基础测试，尚无订单、成交和执行领域类型。

### In Scope

- `core/src/main/java/`
- `core/src/test/java/`
- 必要的领域架构文档

### Out of Scope

- `OrderBook`
- `MatchingEngine`
- 网络、Pipeline、WAL、Snapshot 和 Recovery
- 任何性能优化

## 7. Design Proposal

### Proposed Design

先定义最小领域类型和状态不变量，再由测试固定交易语义。领域类型应避免承担网络、持久化和基础设施职责；数值边界和状态变更必须显式表达。

### Alternatives Considered

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
| 直接使用原始 `long` 和字符串 | 初始代码少 | 语义不明确，边界容易泄漏 | Pending |
| 使用领域值对象和明确状态类型 | 约束强，可测试，便于后续扩展 | 类型数量增加 | Pending |
| 先实现 OrderBook 再补领域模型 | 可以快速看到撮合流程 | 语义不稳定，容易返工 | Rejected |

### Decision

Pending Human approval. This task must remain `Proposed` until the design and acceptance criteria are explicitly approved.

### Architecture Impact

- [x] No architecture change expected
- [ ] ADR required
- [ ] Human architecture decision required

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `core/src/main/java/.../domain/` | 新增领域类型 | 建立稳定的领域边界 |
| `core/src/test/java/.../domain/` | 新增单元测试 | 固定不变量和状态语义 |
| `docs/architecture/` | 必要时补充领域约束 | 保持架构文档同步 |
| `.codex/AGENT_CONTEXT.md` | 实现完成后更新状态 | 支持会话恢复 |

## 9. Test Plan

### Unit Tests

- [ ] 值对象边界和非法输入。
- [ ] 订单创建和基础字段。
- [ ] 订单状态转换。
- [ ] Trade 和 Execution 的确定性。

### Integration or System Tests

- [ ] Not applicable

### Failure and Boundary Tests

- [ ] 零或负价格。
- [ ] 零或负数量。
- [ ] 重复 OrderId。
- [ ] 非法状态转换。

### Determinism or Replay Tests

- [ ] 验证领域对象的 equals、状态和序列不依赖时间或线程。

## 10. Benchmark and Profile Plan

- Benchmark: `Not applicable`
- Profile: `Not applicable`
- Dataset and distribution: `Not applicable`
- Metrics: `Not applicable`
- Baseline: `Not applicable`

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| 过早设计复杂值对象 | 增加初始复杂度 | 只实现当前阶段所需的最小语义 |
| 领域状态与后续撮合不兼容 | 返工 | 先阅读现有架构文档并用测试固定不变量 |
| 方案未审批就实现 | 范围和设计失控 | 保持 `Proposed`，审批前不修改生产代码 |

## 12. Rollback Plan

删除本任务新增领域类型、测试和文档变更即可，不影响现有 Phase 0 骨架。

## 13. Verification Commands

```text
mvn -pl core -am test
mvn verify
git diff --check
```

## 14. Git Commit Plan

计划提交信息：

```text
feat(domain): establish matching domain model
```

提交边界和拆分策略：

- 领域类型和测试属于一个逻辑完整变更。
- 若需要先引入公共基础类型，应拆成独立、可验证的提交。

## 15. Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | Pending | 等待方案确认 |

## 16. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-19 | Proposed | 完成领域模型和正确性基线方案 | 尚未开始实现 |

## 17. Completion Checklist

- [ ] Scope and acceptance criteria satisfied
- [ ] Tests added or updated
- [ ] Build passed
- [ ] Static or format checks passed
- [ ] Benchmark or profile completed when applicable
- [ ] Documentation updated
- [ ] `AGENT_CONTEXT.md` updated
- [ ] Diff reviewed
- [ ] Commit created
- [ ] Post-commit Git status confirmed
