# Task Workspace

`tasks/` 是项目开发方案的唯一工作区。所有需要修改代码、测试、构建配置、Benchmark、文档或运行时行为的任务，都必须先在这里建立任务方案。

## 目录结构

```text
tasks/
├── README.md
├── TEMPLATE.md
├── active/
│   └── TASK-*.md
├── completed/
│   └── TASK-*.md
├── reports/
│   └── PHASE-*.md（每个完成阶段的必需报告）
└── archive/
    └── TASK-*.md
```

## 任务生命周期

```text
Proposed
    -> Approved
    -> In Progress
    -> Completed
```

异常结束时可以进入：

```text
Proposed / Approved / In Progress
    -> Cancelled
```

状态说明：

- `Proposed`：方案已创建，等待 Human 审批。
- `Approved`：方案已审批，可以开始实现。
- `In Progress`：已开始实现、测试或 Benchmark。
- `Completed`：验收标准已满足，任务已完成。
- `Cancelled`：任务被明确取消，并记录取消原因。

任务状态之外，每个任务还必须维护当前开发阶段和下一审批门禁。阶段完成后，任务可以保持 `In Progress`，但下一门禁必须是 `Pending Human Approval`，审批前不得进入下一阶段。

## 使用规则

1. 从 `TEMPLATE.md` 创建唯一的 `Task ID`。
2. 方案必须写清目标、非目标、设计、文件范围、验收标准、验证命令、风险、阶段划分和审批门禁。
3. 需要长期保留的技术决策必须先创建或更新 `Proposed` ADR 草案，再进行技术决策和任务审批。
4. `Proposed` 状态的方案未获 Human 确认前，不得修改生产代码。
5. 方案获批后才能将状态改为 `Approved`，开始实现时改为 `In Progress`。
6. 每个开发阶段完成后必须在 `tasks/reports/` 输出独立阶段报告、将下一门禁设为 `Pending Human Approval` 并停止；Human 审批记录完成后才能进入下一阶段。
7. 实现过程中如果范围、架构、协议、数据格式或验收标准发生变化，必须先更新方案并重新审批；如果影响技术决策，必须同步 ADR。
8. 完成后必须同步 ADR、任务方案、规范、相关项目文档和 `AGENT_CONTEXT.md`，再将任务文件移动到 `completed/`。
9. 长期保留的历史任务可以移动到 `archive/`，不得删除已完成方案作为清理手段。
10. 一个任务只对应一个逻辑主题，不得用一个方案承载无关功能。
11. 任务方案的 `Decision` 小节必须写入对应 ADR 的具体路径、状态、决策摘要和范围边界；不需要 ADR 时必须记录理由。
12. 每个任务必须按需包含 `Benchmark / Profile` 和 `Completion` 阶段；不适用时必须明确记录 `Not applicable`，不能静默跳过。
13. 报告和任务必须记录 Branch、Commit、Remote、Push、CI 和 Working Tree 的真实状态；没有配置或无法观察时记录 `Not configured` / `Unavailable` / `Pending`。

## 命名规则

```text
TASK-YYYYMMDD-NNN-short-description.md
```

示例：

```text
TASK-20260819-002-domain-model-and-correctness-baseline.md
```

## 审批原则

方案审批不是形式步骤。审批前必须确认：

- 任务是否属于当前阶段。
- 需要长期保留的技术决策是否已有 `Proposed` ADR 草案。
- 设计是否符合现有架构和交易语义。
- 影响范围是否可控。
- 测试和验收是否可执行。
- 是否存在需要 Human 决策的架构问题。

没有明确审批记录的方案，默认视为 `Proposed`。

## 决策与 ADR

任务方案与 ADR 的职责不同：

- 任务方案记录本次工作的执行范围、验收标准和实施过程。
- ADR 记录需要长期保留的架构、数据结构、并发、协议、持久化或运行时决策。

当任务包含需要长期保留的决策时，必须先创建或更新 `docs/adr/` 下的对应文档，并在任务方案的 `Decision` 和 `Related ADR` 中写入具体路径。任务方案与 ADR 的决策内容不一致时，任务不得继续实现，必须先完成同步并重新审批。

ADR 草案必须先记录 Context、Problem、Options、Proposed Decision、Scope Boundary、Consequences 和验证计划。Human Review 和技术决策只能发生在 ADR 草案存在之后；决策结果必须回写 ADR 状态。ADR 不是实现完成后的总结，而是决策前的输入和评审依据。

## 阶段报告与逐步审批

任务至少划分为以下阶段：

```text
ADR / Decision
    -> Task Approval
    -> Implementation
    -> Verification
    -> Benchmark / Profile（适用时）
    -> Documentation and Synchronization
    -> Completion
```

每个阶段完成后，必须在任务方案的 `Phase Reports and Approval Gates` 中记录：

- 阶段目标和实际完成内容。
- 修改文件和范围。
- 测试、构建、Benchmark 或其他验证证据。
- 与已批准方案的偏差、风险、限制和未验证内容。
- 下一阶段目标和需要 Human 审批的事项。

每个完成阶段必须在 `tasks/reports/` 创建可独立阅读的 `PHASE-*.md`，并从任务方案链接。报告开头必须提供包含 Phase、Task、Stage、Result、Tests、Build、CI、Commit 和 Next Gate 的状态面板；正文记录变更、范围、验证、性能证据（适用时）、ADR 对齐、Git 证据、风险、项目影响、下一阶段及显式审批请求。阶段报告完成后，下一门禁必须标记为 `Pending Human Approval`，并暂停后续开发。审批结果必须记录日期、审批人、决定、约束和备注。

正常、非破坏性的 push 属于已经批准且完成阶段的仓库同步步骤；force push、共享历史改写、远程分支或标签删除、默认/保护分支修改和 Release 发布始终需要 Human 明确授权。未配置 remote 时不得声称已同步，CI 未观察到结果时不得声称通过。

审批被拒绝、增加约束或发现范围变化时，必须先更新任务方案；如果影响技术决策，必须先创建或更新 ADR，再重新申请审批。所有阶段完成后必须同步 ADR、任务方案、规范、相关项目文档和 `AGENT_CONTEXT.md`，同步完成前不得将任务标记为 `Completed`。
