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

## 使用规则

1. 从 `TEMPLATE.md` 创建唯一的 `Task ID`。
2. 方案必须写清目标、非目标、设计、文件范围、验收标准、验证命令和风险。
3. `Proposed` 状态的方案未获 Human 确认前，不得修改生产代码。
4. 方案获批后才能将状态改为 `Approved`，开始实现时改为 `In Progress`。
5. 实现过程中如果范围、架构、协议、数据格式或验收标准发生变化，必须先更新方案并重新审批。
6. 完成后更新测试、Benchmark、文档和 `AGENT_CONTEXT.md`，将任务文件移动到 `completed/`。
7. 长期保留的历史任务可以移动到 `archive/`，不得删除已完成方案作为清理手段。
8. 一个任务只对应一个逻辑主题，不得用一个方案承载无关功能。

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
- 设计是否符合现有架构和交易语义。
- 影响范围是否可控。
- 测试和验收是否可执行。
- 是否需要创建或更新 ADR。
- 是否存在需要 Human 决策的架构问题。

没有明确审批记录的方案，默认视为 `Proposed`。
