# 架构总览

Updated: 2026-04-09

## 一句话概括

`myagent` 是一个以 `Workspace` 为边界、以 `Thread` 为交互容器的多用户研究平台。普通对话和深度研究共用同一线程，但只有深度研究会进入异步任务执行链路。

## 系统组成

### 后端

- `Spring Boot`
  - HTTP、SSE、配置装配、运行时入口
- `Spring AI`
  - 模型调用、Provider 解析、tool calling 支撑
- `LangGraph4j`
  - 交互图与研究图的 workflow / state / checkpoint
- `MyBatis Plus + Flyway + PostgreSQL`
  - 元数据、消息、任务、快照、长期记忆、用户配置
- `Redis`
  - 短期记忆热缓存、分片上传会话状态
- `RocketMQ`
  - 可选的异步任务与长期记忆事件分发

### 前端

- `React 18 + Vite`
- 三栏主界面：
  - 左侧：工作区/线程
  - 中间：聊天与研究面板
  - 右侧：Inspector

## 模块边界

### `platform-contracts`

共享契约层：

- DTO
- record
- enum
- 事件负载

### `platform-core`

业务核心层：

- 聊天与研究流程
- `GraphRuntimeFactory`
- 记忆投影与长期记忆抽取
- 文档入库、工作区、工具与研究执行支撑

### `platform-api`

适配与装配层：

- Controller
- Spring 配置
- MyBatis 仓储实现
- Redis / RocketMQ / Spring AI 适配

## 核心业务对象

- `Workspace`
  - 用户的工作空间容器
- `Thread`
  - 对话与研究的交互容器
- `Message`
  - 普通消息与 deep research 起草过程中的消息
- `ResearchDraft`
  - 启动研究前的人可编辑计划
- `Task`
  - 后台异步执行单元；目前最重要的是 `RESEARCH`
- `Artifact`
  - 上传文件、研究输出、报告等可见产物
- `Document`
  - 从上传文件衍生出的可检索文档记录

## 两条正式执行图

### 1. Interaction Graph

入口：

- `POST /threads/{threadId}/messages`

用途：

- 普通聊天
- 文档问答
- Web 辅助聊天
- deep research 起草

关键阶段：

- 读取短期记忆
- 读取长期记忆
- 读取 draft 上下文
- 路由交互
- 执行 agent / tools 或 scoping
- 持久化消息或草案
- 发布事件

### 2. Research Graph

入口：

- `POST /threads/{threadId}/research-draft/start`

用途：

- 正式执行后台研究任务

关键阶段：

- `hydrateTask`
- `normalizePlan`
- `initializeSession`
- `planAgenda`
- `discoverySearch`
- `intermediateSynthesis`
- `gapAnalysis`
- `routeIteration`
- `focusedFollowup`
- `convergeFinalize`
- `writeArtifacts`
- `markTaskCompleted`
- `publishCompletionEvents`

## 三条主业务链路

### 普通对话

`CHAT` 请求进入 interaction graph，返回 SSE 流式事件，并持久化：

- user message
- assistant message
- run events
- 短期记忆事件

### Deep Research 起草

`DEEP_RESEARCH` 请求仍走 interaction graph，但输出重点不是直接答案，而是：

- 研究理解
- `ResearchDraftRecord`
- 计划摘要与 plan steps
- 用户可继续编辑的 draft

### Deep Research 执行

启动研究后：

- 创建 `RESEARCH` task
- 由 `TaskDispatcher` 投递
- `GraphRuntimeFactory` 进入 research graph
- 过程中写入 task snapshot、run events、最终 artifacts
- 完成后可读取报告、来源、findings、citations 等结构化结果

## 记忆模型

### 短期记忆

对外视图：

- `ThreadMemoryView`

上游数据：

- `messages`
- `research_drafts`
- `tasks`

持久化与缓存：

- PostgreSQL：`thread_memory_snapshots`
- Redis：thread 级热缓存

### 长期记忆

统一存储：

- `long_term_memory`

类型：

- `PROFILE`
- `SEMANTIC`
- `EPISODIC`

触发方式：

- assistant 的 `CHAT` 消息完成后
- 通过 `memory_extraction_jobs` 异步抽取

## 持久化分层

- PostgreSQL
  - 业务主记录与 LangGraph4j checkpoint 的唯一运行时基线
- 文件系统
  - 上传文件、workspace artifacts、研究输出、文档分块产物
- Redis
  - 热缓存与上传会话状态，不是主存储
- RocketMQ
  - 可选异步后端，不改变业务语义

## 前端当前呈现

- 工作区与线程侧栏
- 聊天区
- Deep Research draft editor
- 研究任务 panel
- Inspector
- 模型 / 技能 / Web 搜索设置面板

## 当前边界

- 同一线程只允许一个活跃研究任务
- 后端没有独立 artifact content/download controller
- 前端图片附件与图片预览仍有遗留旧路径问题
- 后端已支持分片上传，但前端主上传路径仍以简单上传为主
