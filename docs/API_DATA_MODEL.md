# API 与数据模型

Updated: 2026-04-09

## 总体约定

- 当前后端接口没有统一 `/api/v1` 前缀
- 用户身份通过 `X-User-Id` 传入
- 聊天主接口是 SSE
- Deep Research 的执行态数据既有数据库快照，也有文件输出

## Workspace / Thread

### `POST /workspaces`

创建工作区。

请求：

```json
{
  "title": "Market Research Workspace"
}
```

### `GET /workspaces`

列出当前用户所有工作区。

### `GET /workspaces/{workspaceId}`

读取单个工作区。

### `POST /workspaces/{workspaceId}/threads`

在指定工作区下创建线程。

### `GET /workspaces/{workspaceId}/threads`

列出工作区下的线程。

### `POST /threads`

直接创建线程，但 `workspaceId` 必填。

### `GET /threads`

列出当前用户所有线程。

### `DELETE /threads/{threadId}`

删除线程。

## Upload / Document / Artifact

### 简单上传

- `POST /threads/{threadId}/uploads`
- `POST /workspaces/{workspaceId}/uploads`

返回：

- `artifact`
- `documentId`
- `ingestTaskId`

### 分片上传

线程级：

- `POST /threads/{threadId}/uploads/sessions`
- `GET /threads/{threadId}/uploads/sessions/{uploadId}`
- `PUT /threads/{threadId}/uploads/sessions/{uploadId}/chunks/{chunkIndex}`
- `POST /threads/{threadId}/uploads/sessions/{uploadId}/complete`

工作区级：

- `POST /workspaces/{workspaceId}/uploads/sessions`
- `GET /workspaces/{workspaceId}/uploads/sessions/{uploadId}`
- `PUT /workspaces/{workspaceId}/uploads/sessions/{uploadId}/chunks/{chunkIndex}`
- `POST /workspaces/{workspaceId}/uploads/sessions/{uploadId}/complete`

### 文档列表

- `GET /threads/{threadId}/documents`
- `GET /workspaces/{workspaceId}/documents`

注意：

- 线程级 documents/artifacts 列表实际是按线程所属 `workspace` 汇总读取，不仅限于该线程单独上传的文件

### 制品列表

- `GET /threads/{threadId}/artifacts`
- `GET /workspaces/{workspaceId}/artifacts`

可选参数：

- `includeInternal=true|false`

## Message / Event

### `GET /threads/{threadId}/messages`

返回 `MessageRecord[]`。

### `POST /threads/{threadId}/messages`

SSE 主入口。

请求：

```json
{
  "content": "Research the AI chip market",
  "interactionMode": "CHAT | DEEP_RESEARCH",
  "providerId": "gemini",
  "imageArtifactIds": [],
  "documentIds": []
}
```

说明：

- `CHAT`
  - 普通对话、文档问答、Web 辅助聊天
- `DEEP_RESEARCH`
  - 先进入 research scoping，产出或更新 draft

常见事件：

- `run.started`
- `route.selected`
- `message.delta`
- `message.completed`
- `run.completed`
- `run.failed`
- `research.questions.requested`
- `research.brief.updated`
- `research.plan.preview.updated`

### `GET /threads/{threadId}/events?limit=80`

返回 `RunEvent[]`。

## Research Draft

### `GET /threads/{threadId}/research-draft`

读取当前线程的活动 draft。

### `PUT /threads/{threadId}/research-draft`

显式编辑 draft。

可更新字段包括：

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

### `DELETE /threads/{threadId}/research-draft`

丢弃 draft。

### `POST /threads/{threadId}/research-draft/start`

把当前 draft 启动为异步研究任务。

请求：

```json
{
  "providerId": "gemini",
  "draftRevision": 2
}
```

## Task

### `GET /threads/{threadId}/tasks`

列出线程任务。

### `GET /threads/{threadId}/tasks/{taskId}`

读取单个任务。

### `POST /threads/{threadId}/tasks/{taskId}/updates`

对活跃研究任务追加 refinement。

### `POST /threads/{threadId}/tasks/{taskId}/cancel`

取消研究任务。

### 研究结果读取接口

- `GET /threads/{threadId}/tasks/{taskId}/report`
- `GET /threads/{threadId}/tasks/{taskId}/plan`
- `GET /threads/{threadId}/tasks/{taskId}/iterations`
- `GET /threads/{threadId}/tasks/{taskId}/findings`
- `GET /threads/{threadId}/tasks/{taskId}/sources`
- `GET /threads/{threadId}/tasks/{taskId}/citations`

这些接口在任务运行中会优先读 `research_task_snapshots`，任务结束后会回落到 `outputs/{taskId}/response.*` 文件。

## Memory

### 短期记忆

- `GET /threads/{threadId}/memory`

返回 `ThreadMemoryView`。

### 长期记忆通用接口

- `GET /memory/long-term`
- `POST /memory/long-term`
- `PUT /memory/long-term/{memoryId}`
- `DELETE /memory/long-term/{memoryId}`

### 用户档案接口

- `GET /memory/profile`
- `PUT /memory/profile`

这是基于 `long_term_memory` 封装出的结构化接口，不是独立表。

### Stable Facts 接口

- `GET /memory/facts`
- `POST /memory/facts`
- `PUT /memory/facts/{memoryId}`
- `DELETE /memory/facts/{memoryId}`

同样是 `long_term_memory` 的语义化封装。

## 用户配置接口

### 模型提供商

- `GET /model-settings/providers`
- `PUT /model-settings/providers/{providerId}`

### 技能配置

- `GET /skills/status`
- `PUT /skills/{skillId}/config`

### Web 搜索配置

- `GET /web-settings/search`
- `PUT /web-settings/search`

## 关键数据模型

### `WorkspaceRecord`

```json
{
  "workspaceId": "...",
  "userId": "...",
  "title": "...",
  "status": "ACTIVE",
  "createdAt": "...",
  "updatedAt": "..."
}
```

### `ThreadRecord`

```json
{
  "threadId": "...",
  "userId": "...",
  "workspaceId": "...",
  "title": "...",
  "status": "IDLE | RUNNING | FAILED",
  "createdAt": "...",
  "updatedAt": "..."
}
```

### `ResearchDraftRecord`

关键字段：

- `draftId`
- `threadId`
- `status`
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

### `TaskRecord`

关键字段：

- `taskId`
- `threadId`
- `kind`
- `status`
- `title`
- `summary`
- `stage`
- `progress`
- `linkedDraftId`
- `resultArtifactId`

### `ThreadMemoryView`

```json
{
  "threadId": "...",
  "summary": "...",
  "recentMessages": [],
  "pendingHistoricalMessages": [],
  "activeDraftId": "...",
  "activeTaskId": "...",
  "taskStage": "..."
}
```

### `LongTermMemoryRecord`

```json
{
  "memoryId": "...",
  "userId": "...",
  "memoryType": "PROFILE | SEMANTIC | EPISODIC",
  "canonicalKey": "...",
  "title": "...",
  "content": "...",
  "sourceThreadId": "...",
  "sourceMessageId": "...",
  "sourceTaskId": "...",
  "status": "ACTIVE | DELETED",
  "createdAt": "...",
  "updatedAt": "..."
}
```

### `ResearchSourceRecord`

当前已是正式模型，不再是“计划中”：

- `sourceId`
- `kind`
- `title`
- `uri`
- `locator`
- `snippet`
- `domain`
- `unitId`
- `citationLabel`
- `iterationNo`
- `discoveryQuery`
- `evidenceStatus`
- `verificationMethod`
- `supportingFindingIds`
- `citationIds`

### `ReportCitation`

关键字段：

- `citationId`
- `citationLabel`
- `sourceId`
- `kind`
- `title`
- `uri`
- `locator`
- `usedInReport`
- `occurrenceCount`
- `paragraphId`
- `blockId`
- `anchorText`
