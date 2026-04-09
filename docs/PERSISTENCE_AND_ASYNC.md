# 持久化与异步

Updated: 2026-04-09

## 持久化基线

当前系统已经清晰分层：

- PostgreSQL
  - 业务主记录
  - LangGraph4j checkpoint
- 文件系统
  - 上传文件
  - artifacts
  - 文档分块与抽取产物
  - 研究输出文件
- Redis
  - 热缓存
  - 上传会话状态
- RocketMQ
  - 可选异步分发

## PostgreSQL 里有什么

主干表/领域大致包括：

- `workspaces`
- `threads`
- `messages`
- `research_drafts`
- `tasks`
- `run_events`
- `thread_memory_snapshots`
- `long_term_memory`
- `memory_extraction_jobs`
- `research_task_snapshots`
- 用户配置相关表
  - `user_model_provider_config`
  - `user_skill_config`
  - `user_web_search_config`

含义上要注意：

- `messages / research_drafts / tasks` 是业务上游主记录
- `thread_memory_snapshots` 是短期记忆投影视图
- `research_task_snapshots` 是研究执行期的结构化中间态
- `long_term_memory` 是统一后的长期记忆库

## Checkpoint

应用运行时的 checkpoint 策略已经固定：

- 使用 PostgreSQL
- 缺失 `DataSource` 时启动失败
- `DataSource` 不是 PostgreSQL 时启动失败

`MemorySaver` 只适用于隔离测试，不是应用回退路径。

## 文件系统职责

文件系统不再承载主元数据，只承载文件型内容：

- 上传原文件
- workspace 产物
- 研究报告及附属 JSON 输出
- 文档 OCR / fulltext / chunk / page 等派生产物

默认根目录来自：

- `platform.data-root=./data`

## Redis 职责

Redis 现在只承担“快”，不承担“准”。

已在用的主要场景：

- `ThreadMemoryView` 热缓存
- 分片上传 session 状态

不是 Redis 的职责：

- 主记录数据库
- checkpoint 存储
- 长期记忆持久化

## 异步模式

### `platform.async.mode=local`

适合本地开发：

- `TaskDispatcher` 走本地线程池
- 长期记忆抽取任务也走本地 dispatcher

### `platform.async.mode=rocketmq`

适合需要把异步执行和 API 进程解耦的场景：

- task dispatch 进入 RocketMQ
- memory events 可通过 RocketMQ 传递
- 长期记忆抽取任务也可独立消费

业务语义不变，只是异步执行通道变化。

## Memory 事件链路

短期记忆更新已经不是“未来计划”，而是现有实现：

- message / task / draft 相关事件会触发 memory event
- projector 会更新 `thread_memory_snapshots`
- 同时刷新 Redis 热缓存

典型事件包括：

- `message.completed`
- `research.brief.updated`
- `task.created`
- `task.completed`
- `task.failed`
- `task.cancelled`

## 长期记忆异步链路

长期记忆不是聊天时同步写入，而是：

1. `CHAT` assistant 消息完成
2. `LongTermMemoryJobScheduler` 判断是否值得抽取
3. 创建 `memory_extraction_jobs`
4. 通过 local 或 RocketMQ 分发
5. `LongTermMemoryJobProcessor` 执行
6. `LongTermMemoryExtractionService` 调模型生成 upsert/delete
7. 更新 `long_term_memory`

## 当前判断原则

如果一个东西要求“正确性与恢复能力”，看 PostgreSQL。

如果一个东西要求“文件结果可见性”，看文件系统。

如果一个东西要求“性能与临时状态”，看 Redis。

如果一个东西要求“异步解耦”，看 RocketMQ。
