# 交互模型

Updated: 2026-04-09

## 1. 顶层容器

当前前端以 `Workspace -> Thread` 组织交互。

- `Workspace`
  - 知识与线程的容器
- `Thread`
  - 用户对话、research draft 和 research task 的共同容器

## 2. 一个线程里会同时出现什么

同一线程里可能同时存在：

- 普通消息流
- `ResearchDraft` 编辑面板
- 最新 research task 面板
- Inspector 中的 memory / documents / events / sites 信息

线程不是单一路径，而是同一上下文下的多种交互形态。

## 3. 两种 compose 模式

### `CHAT`

发送后进入普通对话路径。

可能被路由成：

- 普通回答
- 文档问答
- Web 辅助回答

### `DEEP_RESEARCH`

发送后先进入 research scoping，而不是直接启动后台任务。

结果通常是：

- assistant 对研究目标的理解
- draft 更新
- 更明确的研究计划

## 4. Deep Research 的两阶段模型

### 阶段 A：Draft

用户先把问题推进成可执行计划：

- title
- brief
- objective
- scope
- outputFormat
- constraints
- questions
- planSummary
- planSteps

当前前端已经支持直接编辑这些字段并保存。

### 阶段 B：Task

只有 draft `ready=true` 时，才能正式启动：

- `POST /threads/{threadId}/research-draft/start`

启动后：

- 创建 `RESEARCH` task
- 后台异步执行
- 线程中展示研究任务面板

## 5. 输入框语义

### 当 composer 在 `CHAT`

消息被视为普通对话。

### 当 composer 在 `DEEP_RESEARCH` 且还没有活跃任务

消息被视为 draft refinement/scoping 输入。

如果 draft 已 ready，且输入是空或类似 `start`，前端会直接调用 start research。

### 当已经有活跃 `RESEARCH` task

消息不再是 draft 编辑，而是任务 refinement：

- `POST /threads/{threadId}/tasks/{taskId}/updates`

## 6. 线程是否阻塞

不阻塞。

但语义有边界：

- 线程可以继续聊天
- 研究任务可以继续跑
- 活跃任务更新与普通聊天是两条不同入口

## 7. Inspector 的角色

Inspector 当前不是日志墙，而是线程状态总览：

- 会话摘要
- 当前或最近计划
- 研究站点
- 最近事件
- 文档列表
- Profile Memory
- Stable Facts

## 8. 当前实现边界

- 一个线程只能有一个活跃研究任务
- draft 和 task 是同线程共存，但不会并行启动多个研究任务
- 前端已经暴露图片附件入口，但相关上传/预览路径仍有旧接口遗留
- 分片上传后端已支持，前端主交互暂未完全接上
