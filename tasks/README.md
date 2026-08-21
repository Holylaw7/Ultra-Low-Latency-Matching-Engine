# Task Workspace

`tasks/` 是项目开发方案的唯一工作区。所有需要修改代码、测试、构建配置、Benchmark、文档或运行时行为的任务，都必须先在这里建立任务方案。

## 目录结构

```text
tasks/
├── README.md
├── TEMPLATE.md
├── PHASE_BLUEPRINT_TEMPLATE.md
├── blueprints/
│   └── PHASE-*-blueprint.md
├── active/
│   └── TASK-*.md
├── completed/
│   └── TASK-*.md
├── reports/
│   └── PHASE-*.md（Task、Blueprint checkpoint 与 Closure 报告）
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
- `Approved`：方案已通过直接 Human 审批，或继承了明确记录的 Human Phase Blueprint Approval，可以开始实现。
- `In Progress`：已开始实现、测试或 Benchmark。
- `Completed`：验收标准已满足，任务已完成。
- `Cancelled`：任务被明确取消，并记录取消原因。

任务状态之外，每个任务还必须维护当前开发阶段和下一门禁。Blueprint 模式
下可以记录下一已授权 checkpoint；严格门禁、Exception Gate 或 Phase
Closure 才标记为 `Pending Human Approval` 并停止。

## 使用规则

1. 从 `TEMPLATE.md` 创建唯一的 `Task ID`。
2. 方案必须写清目标、非目标、设计、文件范围、验收标准、验证命令、风险、阶段划分和审批门禁。
3. 需要长期保留的技术决策必须先创建或更新 `Proposed` ADR 草案，再进行直接 Human Review 或 Phase Blueprint Review。
4. `Proposed` 状态的方案未获直接 Human 审批或明确 Blueprint 继承审批前，不得修改生产代码。
5. 方案通过直接或 Blueprint 继承审批后才能将状态改为 `Approved`，开始实现时改为 `In Progress`。
6. Blueprint 模式下，每个开发阶段完成后运行证据门禁并更新 Blueprint 指定的 Task / checkpoint 报告；下一阶段已授权且无 Exception Gate 时直接继续。严格门禁、异常门禁和 Phase Closure 必须等待 Human。
7. 实现过程中如果范围、架构、协议、数据格式或验收标准发生变化，必须先更新方案并重新审批；如果影响技术决策，必须同步 ADR。
8. 完成后必须同步 ADR、Blueprint、任务方案、规范、相关项目文档和 `AGENT_CONTEXT.md`，再将任务文件移动到 `completed/`。
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

## Phase Blueprint Mode

新的多任务 Phase 默认在 `tasks/blueprints/` 创建一个完整 Blueprint，模板
为 `tasks/PHASE_BLUEPRINT_TEMPLATE.md`。Blueprint 必须一次性列出：

- Phase 目标、非目标和冻结边界；
- 所有必需 ADR 及其决策矩阵；
- Task 列表、依赖关系、子阶段和文件范围；
- 验收标准、测试/恢复/性能证据计划；
- Commit、Branch、Push、CI、Rollback 和文档计划；
- Exception Gate、Closure 和 baseline/tag 计划。

一次 Human Phase Blueprint Approval 可以同时批准决策矩阵中明确列出的
ADR 和 Task。审批结果必须同步进每个 ADR 与 Task；未列出的工作不获得
授权。

Blueprint 执行采用三层 Gate：

```text
Architecture Gate — Human Blueprint Approval
    -> Implementation Gate — automated tests / checks / diff / commit / CI
    -> Closure Gate — Human Phase Closure Approval
```

以下情况触发 Exception Gate 并立即停止：ADR 冲突、范围扩张、未批准的
公共 API 或语义/格式变化、新关键依赖、验证揭示架构问题、需要弱化验收
标准、破坏性 Git / Release 操作，或任何未在 Blueprint 中列出的动作。

独立任务、Blueprint 指定的高风险人工检查点和 Exception Gate 使用严格
逐阶段审批模式。

## 审批原则

方案审批不是形式步骤。审批前必须确认：

- 任务是否属于当前阶段。
- 需要长期保留的技术决策是否已有 `Proposed` ADR 草案。
- 设计是否符合现有架构和交易语义。
- 影响范围是否可控。
- 测试和验收是否可执行。
- 是否存在需要 Human 决策的架构问题。

没有直接审批或可追溯 Blueprint 继承审批记录的方案，默认视为
`Proposed`。

## 决策与 ADR

任务方案与 ADR 的职责不同：

- 任务方案记录本次工作的执行范围、验收标准和实施过程。
- ADR 记录需要长期保留的架构、数据结构、并发、协议、持久化或运行时决策。

当任务包含需要长期保留的决策时，必须先创建或更新 `docs/adr/` 下的对应文档，并在任务方案的 `Decision` 和 `Related ADR` 中写入具体路径。任务方案与 ADR 的决策内容不一致时，任务不得继续实现，必须先完成同步并重新审批。

ADR 草案必须先记录 Context、Problem、Options、Proposed Decision、Scope Boundary、Consequences 和验证计划。直接 Human Review 或 Phase Blueprint Review 只能发生在 ADR 草案存在之后；决策结果必须回写 ADR 状态。ADR 不是实现完成后的总结，而是决策前的输入和评审依据。

## 阶段报告与执行检查点

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
- 下一阶段目标、Blueprint 授权状态和可能的 Exception Gate。

每个完成 Task 和 Blueprint 指定的 evidence checkpoint 必须在
`tasks/reports/` 创建或更新可独立阅读的 `PHASE-*.md`，并从任务方案链接。
多个已授权子阶段可以共享累计报告，避免重复背景。报告开头必须提供包含
Phase、Task、Stage、Result、Tests、Build、CI、Commit 和 Next Gate 的
状态面板；正文记录变更、范围、验证、性能证据（适用时）、ADR 对齐、Git
证据、风险、项目影响和下一状态。

报告结尾必须明确以下之一：

- `Blueprint Authorized — continue`；
- `Exception Gate — Pending Human Approval`；
- `Phase Closure — Pending Human Approval`。

只有后两种状态需要暂停。Blueprint 已授权的普通子阶段在证据门禁通过后
可以继续。

正常、非破坏性的 push 属于已经批准且完成阶段的仓库同步步骤；force push、共享历史改写、远程分支或标签删除、默认/保护分支修改和 Release 发布始终需要 Human 明确授权。未配置 remote 时不得声称已同步，CI 未观察到结果时不得声称通过。

审批被拒绝、增加约束或发现范围变化时，必须先更新任务方案；如果影响技术决策，必须先创建或更新 ADR，再重新申请审批。所有阶段完成后必须同步 ADR、任务方案、规范、相关项目文档和 `AGENT_CONTEXT.md`，同步完成前不得将任务标记为 `Completed`。
