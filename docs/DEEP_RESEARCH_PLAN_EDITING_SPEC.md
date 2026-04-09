# Deep Research Plan Editing Spec

Updated: 2026-04-09

## 说明

这份文档不再描述“未来想做什么”，而是描述当前已经落地的 plan editing 契约。

## 当前目标

在真正启动研究任务之前，用户必须能：

- 看见计划
- 改计划
- 保存计划
- 再决定是否启动研究

## 当前对象

计划编辑围绕 `ResearchDraftRecord` 展开。

主要字段：

- `title`
- `brief`
- `objective`
- `scope`
- `outputFormat`
- `constraints`
- `questions`
- `planSummary`
- `planSteps`
- `revision`
- `ready`

## 当前用户路径

### 1. 通过 `DEEP_RESEARCH` 消息进入 scoping

用户把 composer 切到 `DEEP_RESEARCH`，发送消息后：

- interaction graph 进入 scoping 路径
- 系统生成或更新 draft
- 线程里出现 draft editor

### 2. 通过 draft editor 做显式编辑

前端当前已经支持直接编辑：

- 标题
- brief
- objective
- scope
- output format
- constraints
- questions
- plan summary
- plan steps

保存时调用：

- `PUT /threads/{threadId}/research-draft`

### 3. 版本控制

更新 draft 时必须带 `revision`。

如果 revision 过期，后端会拒绝更新，避免旧版本覆盖新计划。

### 4. 启动研究

当前 draft 满足 `ready=true` 后，用户可以：

- 点击按钮启动
- 或在 composer 中发送空输入/类似 `start` 的意图

后端会校验：

- 只有一个活跃研究任务
- draft 存在
- draft 已 ready
- draftRevision 匹配

## Draft 编辑和 Task 更新的区别

### 启动前

所有 deep research refinement 都应该作用在 draft 上。

### 启动后

不再改 draft，而是调用：

- `POST /threads/{threadId}/tasks/{taskId}/updates`

也就是说，当前系统已经明确区分了：

- pre-start plan editing
- post-start task refinement

## 当前 UI 语义

### Draft editor

是正式编辑面板，不是只读预览卡片。

### Composer

会根据当前状态自动决定：

- 普通聊天
- draft refinement
- 启动研究
- 任务 refinement

### Research task panel

任务启动后，重点从“编辑计划”切换到“查看执行过程和结果”。

## 当前仍然缺少的交互能力

- 更精细的 step reorder / drag-and-drop
- 草案编辑历史对比
- 更强的 plan diff 可视化

但对当前项目来说，plan editing 已经是正式实现，而不是待设计功能。
