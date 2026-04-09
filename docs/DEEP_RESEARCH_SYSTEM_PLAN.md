# Deep Research System Plan

Updated: 2026-04-09

## 目标

把 deep research 维持为一个清晰、可解释、可编辑、可恢复的系统，而不是一次性长 prompt。

当前正式模型已经确定为：

`Draft first -> Human review/edit -> Async task execution -> Structured outputs`

## 当前正式链路

### 1. 进入 scoping

用户通过：

- `POST /threads/{threadId}/messages`
- `interactionMode=DEEP_RESEARCH`

把请求送入 interaction graph。

这一步不会直接启动后台研究任务，而是先生成或更新 `ResearchDraftRecord`。

### 2. 形成可编辑 draft

当前 draft 是正式产品对象，不再只是临时中间态。

核心字段包括：

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

### 3. 人工审阅与编辑

用户可以：

- 继续发送 `DEEP_RESEARCH` 消息做 scoping refinement
- 直接调用 `PUT /threads/{threadId}/research-draft` 精确修改字段

这是当前 deep research 设计里非常重要的一层：

- 研究前的人类控制
- 版本控制
- 避免把模糊请求直接扔进长任务

### 4. 启动正式研究任务

当 draft `ready=true` 后，调用：

- `POST /threads/{threadId}/research-draft/start`

系统会：

- 生成 `ApprovedResearchPlan`
- 创建 `RESEARCH` task
- 清理活动 draft
- 写入 `research.plan.approved` 等事件
- 通过 `TaskDispatcher` 投递任务

## 研究执行图

当前 research graph 的主节点顺序是：

1. `hydrateTask`
2. `normalizePlan`
3. `initializeSession`
4. `planAgenda`
5. `discoverySearch`
6. `intermediateSynthesis`
7. `gapAnalysis`
8. `routeIteration`
9. `focusedFollowup`
10. `convergeFinalize`
11. `writeArtifacts`
12. `markTaskCompleted`
13. `publishCompletionEvents`

这说明当前 deep research 已经不是“单次搜一下再写报告”，而是带迭代与收敛逻辑的后台流程。

## 输出物设计

当前研究任务完成后，至少会产出：

- `response.md`
- `response.plan.json`
- `response.iterations.json`
- `response.findings.json`
- `response.sources.json`
- `response.citations.json`

同时，运行中或未结束时还有：

- `research_task_snapshots`
- `run_events`

所以 deep research 的输出已经是“结构化结果集”，而不只是一份 markdown。

## UI 读取契约

前端当前通过这些接口读取研究结果：

- `GET /threads/{threadId}/tasks/{taskId}/report`
- `GET /threads/{threadId}/tasks/{taskId}/plan`
- `GET /threads/{threadId}/tasks/{taskId}/iterations`
- `GET /threads/{threadId}/tasks/{taskId}/findings`
- `GET /threads/{threadId}/tasks/{taskId}/sources`
- `GET /threads/{threadId}/tasks/{taskId}/citations`

运行中优先读 snapshot，结束后回退到落盘文件。

## 设计约束

### 1. 一线程一个活跃研究任务

当前不支持同线程并行研究任务。

### 2. 不做 swarm / multi-agent 编排

当前研究执行可以有多轮搜索与反思，但不是独立 worker 群协作模型。

### 3. 来源可见性是硬要求

研究结果必须能回答：

- 结论来自哪里
- 运行过程做了什么
- 哪些来源支持了哪些 finding

### 4. 研究前必须允许人工控制

draft 的存在不是过渡方案，而是正式产品设计的一部分。

## 当前仍值得推进的点

1. artifact content/download API，补上研究结果与图片的直接读取能力。
2. citation offset / paragraph-level drill-down。
3. 更强的 source drawer 信息密度。
4. 私有来源接入能力，但必须在来源可见和安全边界明确后推进。
