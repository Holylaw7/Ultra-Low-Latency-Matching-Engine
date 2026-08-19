# Task Plan - TASK-20260819-002

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
| Approval Gate | `Pending Human approval` |

## 2. Background

Phase 0 工程骨架已经建立，但撮合领域模型尚未实现。根 `pom.xml` 管理 `core` 和 `benchmark` 模块，`core/pom.xml` 将根目录 `src/main/java` 和 `src/test/java` 配置为模块源码目录；当前只有应用占位类和基础测试。

后续 OrderBook、Matching Engine、WAL 和 Replay 都需要稳定、可验证的领域对象和交易语义作为基础。现有架构文档已经明确 Phase 1 的顺序是先建立 Domain Model，再建立 Correctness Baseline。

## 3. Goal

建立最小领域模型和正确性基线，为后续 OrderBook 实现提供明确的类型、状态、不变量和可重复的验证入口。

本任务的交付物是领域类型、状态转换测试和确定性测试；不交付撮合算法、网络服务或持久化实现。

## 4. Non-Goals

- 不实现 OrderBook。
- 不实现撮合算法。
- 不引入 Netty、Disruptor 或 WAL。
- 不进行性能优化或发布性能结论。
- 不改变现有 Maven 模块结构。
- 不修改核心事件顺序、网络协议、WAL 或 Snapshot 格式。

## 5. Requirements and Acceptance Criteria

### Requirements

- [ ] 定义 `Side`、`OrderType` 和 `OrderStatus`。
- [ ] 定义 `OrderId`、`Price`、`Quantity` 和 `Sequence` 的表示、范围和比较语义。
- [ ] 定义 `Order`、`Trade` 和 `Execution` 的字段、相等性和状态边界。
- [ ] 明确非法价格、数量、标识、序列和状态转换的处理方式。
- [ ] 明确订单状态的合法迁移和终态。
- [ ] 建立领域模型单元测试、边界测试和确定性测试。
- [ ] 保持领域类型不依赖网络、持久化、Metrics 或线程调度。

### Acceptance Criteria

- [ ] 所有领域类型具有明确的不变量。
- [ ] `OrderId`、`Price`、`Quantity` 和 `Sequence` 的非法输入会被显式拒绝。
- [ ] 订单状态转换测试覆盖正常、非法、终态和重复操作。
- [ ] `Trade` 和 `Execution` 的结果只由输入字段决定，不依赖系统时间、随机数或线程调度。
- [ ] 领域模型不引入网络、WAL、Snapshot、数据库或第三方依赖。
- [ ] 生产源码位于 `src/main/java/com/ultralatency/matching/domain/`，测试位于 `src/test/java/com/ultralatency/matching/domain/`。
- [ ] 根 Maven 构建、测试和 Checkstyle 通过。
- [ ] 不引入未审批的架构变化或第三方依赖。

## 6. Current Implementation and Scope

### Current Implementation

当前仓库只有 `src/main/java/com/ultralatency/matching/MatchingEngineApplication.java` 应用占位类和 `src/test/java/com/ultralatency/matching/MatchingEngineApplicationTest.java` 基础测试，尚无订单、成交和执行领域类型。

`core/pom.xml` 通过 `${project.basedir}/../src/main/java` 和 `${project.basedir}/../src/test/java` 复用根目录源码布局。方案中的文件路径以仓库根目录为基准。

### In Scope

- `src/main/java/com/ultralatency/matching/domain/`
- `src/test/java/com/ultralatency/matching/domain/`
- 必要时更新 `docs/architecture/matching-engine.md`
- 实现完成后更新 `.codex/AGENT_CONTEXT.md`

### Out of Scope

- `OrderBook`
- `MatchingEngine`
- 网络、Pipeline、WAL、Snapshot 和 Recovery
- 任何性能优化
- 生产环境部署配置

## 7. Design Proposal

### Proposed Design

先定义最小领域类型和状态不变量，再由测试固定交易语义。领域类型应避免承担网络、持久化和基础设施职责；数值边界和状态变更必须显式表达。

拟采用以下基线：

- `OrderId`、`Price`、`Quantity` 和 `Sequence` 使用有明确范围校验的值类型，底层表示和精度语义在实现前固定并写入测试。
- `Order` 的身份字段保持稳定，剩余数量和状态只能通过受控方法变化。
- `Trade` 和 `Execution` 表达一次确定的执行结果，不读取当前时间，也不生成随机标识。
- 订单状态至少覆盖可接受、部分成交、完全成交和取消等语义；具体枚举值和迁移表以实现前的测试设计为准。
- 领域对象不直接依赖 `OrderBook`、网络、WAL 或线程模型。

### Input and Output

输入：

- 领域对象构造参数。
- 明确的价格、数量、标识和序列值。
- 受控的成交、剩余数量和状态变更请求。

输出：

- 可供 OrderBook 使用的领域对象。
- 可验证的 `Trade` 和 `Execution` 结果。
- 可重复执行的状态转换和异常行为。

本任务不接收网络消息、不写 WAL、不访问数据库，也不依赖系统时间。

### Alternatives Considered

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
| 直接使用原始 `long` 和字符串 | 初始代码少 | 语义不明确，边界容易泄漏 | Rejected |
| 使用领域值对象和明确状态类型 | 约束强，可测试，便于后续扩展 | 类型数量增加 | Proposed |
| 在领域对象中直接处理网络或持久化 | 减少表面上的调用层次 | 破坏模块边界，影响测试和确定性 | Rejected |
| 先实现 OrderBook 再补领域模型 | 可以快速看到撮合流程 | 语义不稳定，容易返工 | Rejected |

### Decision

方案已完成对齐，当前仍等待 Human 审批。本任务必须保持 `Proposed`，直到设计、范围和验收标准被明确批准；审批前不得修改生产代码或生产测试。

### Architecture Impact

- [x] No architecture change expected
- [ ] ADR required
- [ ] Human architecture decision required

如果实现过程中发现需要改变协议、事件顺序、WAL、Snapshot、并发模型或核心数据结构，必须停止当前任务，更新方案并创建或关联 ADR，重新等待审批。

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `src/main/java/com/ultralatency/matching/domain/` | 新增领域类型 | 建立稳定的领域边界 |
| `src/test/java/com/ultralatency/matching/domain/` | 新增单元测试 | 固定不变量和状态语义 |
| `docs/architecture/matching-engine.md` | 必要时补充领域约束 | 保持架构文档同步 |
| `.codex/AGENT_CONTEXT.md` | 实现完成后更新状态 | 支持会话恢复 |

## 9. Test Plan

### Unit Tests

- [ ] 值类型边界、比较和非法输入。
- [ ] 订单创建、身份字段和基础字段。
- [ ] 订单剩余数量变化和状态转换。
- [ ] `Trade` 和 `Execution` 的字段完整性与相等性。

### Integration or System Tests

- [ ] 当前任务不涉及模块间运行时集成，执行根 Maven Reactor 验证。

### Failure and Boundary Tests

- [ ] 零或负价格、数量和序列。
- [ ] 非法或空的订单标识。
- [ ] 剩余数量超过原始数量。
- [ ] 已成交、已取消订单的非法变更。
- [ ] 重复取消和重复终态操作的明确语义。

### Determinism or Replay Tests

- [ ] 验证相同输入产生相同领域对象、状态和执行结果。
- [ ] 验证领域对象的 equals、状态和序列不依赖时间、随机数或线程。

## 10. Benchmark and Profile Plan

- Benchmark: `Not applicable for this task`
- Profile: `Not applicable for this task`
- Dataset and distribution: `Not applicable`
- Metrics: `Correctness and determinism only`
- Baseline: `Existing Maven build and bootstrap test`

本任务不产生吞吐、延迟、分配率或 GC 结论。任何性能结论必须另建任务，并遵循 `Baseline -> Benchmark -> Profile -> Optimize -> Re-benchmark` 流程。

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| 过早设计复杂值对象 | 增加初始复杂度 | 只实现当前阶段所需的最小语义 |
| 领域状态与后续撮合不兼容 | 返工 | 先阅读现有架构文档并用测试固定不变量 |
| 方案未审批就实现 | 范围和设计失控 | 保持 `Proposed`，审批前不修改生产代码 |
| 价格和数量表示不明确 | 后续撮合结果或协议转换不一致 | 在实现前固定单位、范围、比较和转换边界，并使用测试约束 |
| 领域对象混入 Infrastructure | 破坏模块边界和确定性 | 限制包依赖，只允许领域语义和受控状态变化 |
| 测试只验证构造、不验证状态 | 非法状态可能进入 OrderBook | 覆盖状态迁移、终态、重复操作和失败路径 |

## 12. Rollback Plan

删除本任务新增领域类型、测试和文档变更即可，不影响现有 Phase 0 骨架；如果实现期间发现需要改变协议、WAL 或核心架构，停止实现并创建独立 ADR 或任务。

## 13. Verification Commands

实现前基线：

```text
git status --short --branch
git diff --check
mvn verify
```

实现后：

```text
mvn -pl core -am test
mvn verify
git diff --check
git diff --cached --check
git status --short --branch
git log -1 --oneline --decorate
```

## 14. Git Commit Plan

计划提交信息：

```text
feat(domain): establish matching domain model
```

提交边界和拆分策略：

- 领域类型和测试属于一个逻辑完整变更。
- 若需要先引入公共基础类型，应拆成独立、可验证的提交。
- 当前方案对齐本身只修改任务文档，不修改生产代码。

## 15. Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | Pending | 方案已按开发规范和实际工程结构对齐，等待明确审批；审批前不得实现 |

## 16. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-19 | Proposed | 根据实际源码布局、现有架构文档和开发规范完成任务对齐；未修改生产代码 | 本次对齐后执行 `git diff --check` |

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
