# 记忆实现说明

Updated: 2026-03-26

## 1. 当前短期记忆的 Redis 实现

### 1.1 设计目标

当前短期记忆不再把 Redis 拆成两份缓存:

- 一份 recent window
- 一份 session summary

现在改成了 thread 级整体缓存，也就是直接缓存一整份 `ThreadMemoryView` 对应的数据。

这样做的目的有两个:

- 读取 prompt 上下文时更快，不需要分别查消息窗口、summary、draft、task
- 结构更统一，后续短期记忆字段继续增加时，不需要再拆分多份缓存

### 1.2 Redis 里到底缓存了什么

当前 Redis 缓存的对象是 `CachedThreadMemoryRecord`，字段包括:

- `threadId`
- `summary`
- `recentMessages`
- `activeDraftId`
- `activeTaskId`
- `taskStage`
- `lastCompactedMessageId`

也就是说，Redis 里存的是某个 thread 当前完整的短期记忆结果，而不是原始业务数据。

这里面的 `summary` 也已经不再是简单的 recent window 字符串拼接。
当前实现改成了“保留最近原文窗口 + 压缩更早历史”的模式:

- `recentMessages` 保留最近窗口原文
- `summary` 承接更早历史的滚动压缩结果

对应代码:

- `backend/platform-contracts/src/main/java/com/xg/platform/contracts/memory/CachedThreadMemoryRecord.java`
- `backend/platform-core/src/main/java/com/xg/platform/runtime/ThreadMemoryViewCache.java`
- `backend/platform-api/src/main/java/com/xg/platform/api/cache/redis/RedisThreadMemoryViewCache.java`

### 1.3 写入链路

短期记忆的权威生成逻辑仍然在 `ShortTermMemoryProjectionService`。

它会从下面几类业务数据里重建 thread memory:

- `messages`
- `research_drafts`
- `tasks`

然后整理出:

- 最近消息窗口
- summary
- 当前活跃 draft
- 当前活跃 task
- task stage

接着做两件事:

1. 把整理结果写入 PostgreSQL 的 `thread_memory_snapshots`
2. 把同一份结果写入 Redis 的 `ThreadMemoryViewCache`

所以 Redis 不是单独生成数据，而是缓存 PostgreSQL snapshot 对应的 thread memory 结果。

对应代码:

- `backend/platform-core/src/main/java/com/xg/platform/agent/core/application/ShortTermMemoryProjectionService.java`

### 1.3.1 summary 现在怎么生成

当前 summary 的实现，参考的是比较成熟的“rolling summary buffer”思路:

- 最近窗口消息保留原文，不做压缩
- 超出窗口的更早消息，交给小模型做结构化滚动摘要
- 每次只有从 recent window 滑出窗口的那部分消息，才继续并入已有 summary

这意味着 summary 和 recent messages 的分工已经分开了:

- `recentMessages` 负责高保真上下文
- `summary` 负责更早历史的压缩上下文

当前 summary compressor 支持两种实现:

- `LlmConversationSummaryCompressor`
- `SimpleConversationSummaryCompressor`

生产配置里默认会优先走 LLM compressor。它会把更早历史按 chunk 分段，用小模型维护一个滚动摘要；如果模型调用失败，再回退到简单摘要实现。

默认配置项:

- `platform.memory.short-term.summary.enabled`
- `platform.memory.short-term.summary.provider`
- `platform.memory.short-term.summary.model`
- `platform.memory.short-term.summary.max-messages-per-chunk`
- `platform.memory.short-term.summary.max-chars-per-chunk`
- `platform.memory.short-term.summary.max-words`

默认模型现在配置成了 `gemini-3-flash-preview`，用于低成本做 summary 压缩。

### 1.4 读取链路

读取 thread memory 时，入口在 `ConversationMemoryService`。

当前流程是:

1. 先确认 thread 存在
2. 读取 PostgreSQL 中的 `thread_memory_snapshots`
3. 读取该 thread 最新 messageId
4. 如果 snapshot 不存在，或者 `lastCompactedMessageId` 已经过期，就直接回源重建
5. 如果 snapshot 还是新的，再去读 Redis 里的整份 `CachedThreadMemoryRecord`
6. 如果 Redis 没命中，或者缓存内容和 snapshot 对不上，也回源重建
7. 只有 snapshot 新鲜且 Redis 内容一致时，才直接把 Redis 缓存转成 `ThreadMemoryView` 返回

对应代码:

- `backend/platform-core/src/main/java/com/xg/platform/agent/core/application/ConversationMemoryService.java`

### 1.5 一致性校验怎么做

Redis 缓存不是无条件信任的。

当前会校验这些字段是否和 PostgreSQL snapshot 一致:

- `lastCompactedMessageId`
- `summary`
- `activeDraftId`
- `activeTaskId`
- `taskStage`

只要有任意一项对不上，就认为缓存可能脏了，直接重新投影并回写缓存。

这意味着当前策略是:

- PostgreSQL 负责准
- Redis 负责快

### 1.6 Redis key 和 TTL

当前 Redis key 格式:

- `memory:thread:{userId}:{threadId}:view`

缓存值是 `CachedThreadMemoryRecord` 的 JSON。

TTL 由配置项 `platform.memory.redis.ttl-hours` 控制。

对应代码:

- `backend/platform-api/src/main/java/com/xg/platform/api/cache/redis/RedisThreadMemoryViewCache.java`
- `backend/platform-api/src/main/java/com/xg/platform/api/config/MemoryConfig.java`

### 1.7 Redis 宕机后的降级方案

当前实现已经做了 fail-open 降级。

读 Redis 失败时:

- 捕获运行时异常
- 打 warning 日志
- 返回空缓存
- 后续自动回 PostgreSQL snapshot + 业务表重建

写 Redis 失败时:

- 捕获运行时异常
- 打 warning 日志
- 不影响主流程提交

因此 Redis 宕机不会阻断主功能，只会让 thread memory 读取退化成回源重建，代价是延迟上升，但不会影响正确性。

### 1.8 已移除的旧实现

原来的拆分式缓存已经移除:

- `SessionWindowCache`
- `SessionSummaryCache`
- 对应的 Redis/no-op 实现

现在主干只保留 thread 级整体缓存 `ThreadMemoryViewCache`。

## 2. 当前长期记忆是怎么注入 prompt 的

### 2.1 不是把所有历史消息都直接塞进 prompt

长期记忆不是把全部历史消息原样拼进去。

当前链路是:

1. 在 `message.completed` 后触发长期记忆抽取任务
2. `LongTermMemoryExtractionService` 只从完成的 chat assistant 消息里抽取稳定信息
3. 抽取出的内容写入 `long_term_memory`
4. prompt 构造时，再读取 `long_term_memory` 里的 active 记录

也就是说，进入 prompt 的不是原始聊天历史，而是已经被抽取过的一层结构化长期记忆。

对应代码:

- `backend/platform-core/src/main/java/com/xg/platform/agent/core/application/LongTermMemoryExtractionService.java`

### 2.2 当前是否“全部拼接”

按当前实现，prompt 读取长期记忆时，确实是:

- 读取该用户全部 `ACTIVE` 状态的长期记忆
- 按 `updated_at desc` 排序
- 格式化成文本
- 直接放进 prompt 的 `<memory_context>` 里

当前没有做:

- top-k 召回
- 相关性过滤
- 向量检索
- token budget 裁剪

所以更准确地说:

- 不是把所有历史原始内容都拼 prompt
- 但会把当前所有 active 的长期记忆项一起拼进 prompt

对应代码:

- `backend/platform-api/src/main/java/com/xg/platform/api/persistence/mybatisplus/repository/MybatisLongTermMemoryRepository.java`
- `backend/platform-core/src/main/java/com/xg/platform/agent/core/shared/MemoryContextFormatter.java`
- `backend/platform-core/src/main/java/com/xg/platform/agent/core/chat/ChatGraphNodeService.java`
- `backend/platform-core/src/main/java/com/xg/platform/agent/core/research/scoping/ResearchScopingGraphNodeService.java`
- `backend/platform-core/src/main/java/com/xg/platform/agent/core/research/execution/ResearchExecutionGraphNodeService.java`
- `backend/platform-api/src/main/java/com/xg/platform/api/ai/SpringAiPromptService.java`

### 2.3 当前实现的特点

优点:

- 简单直接，工程复杂度低
- 用户偏好和长期背景能稳定进入 prompt

缺点:

- 长期记忆一多，prompt 会越来越长
- 没有按当前问题做相关性筛选
- 后续很可能需要做 top-k 或预算控制

所以这块现在更像“先把长期记忆机制跑通”，还不是成熟的 memory recall 系统。
