# 记忆并行加载与首 Token 延迟说明

Updated: 2026-04-09

## 这份文档讲什么

这份文档专门解释两件事：

1. 之前把长短期记忆等上下文加载改成并行，代码里到底是怎么实现的。
2. 为了让回复更早吐出首个 token，现在已经做了哪些优化，后面还可以继续做什么。

重点只放在主聊天链路，不展开所有 memory 细节。

## 一句话结论

这次“并行加载”最核心的做法，不是在业务代码里到处手写 `CompletableFuture`，而是把 interaction graph 的起点拆成了三条互不依赖的支路：

- `loadShortTermMemory`
- `loadLongTermMemory`
- `loadDraftContext`

这三条支路同时开始，全部完成后才进入 `routeInteraction`。
所以它本质上是图结构层面的并行，不是单个 service 里的并行。

## 1. 并行加载到底是怎么做的

### 1.1 并行点在 graph，不在 memory service

关键代码在：

- `backend/platform-core/src/main/java/com/xg/platform/graph/GraphRuntimeFactory.java`

interaction graph 的起点是这样接的：

- `START -> loadShortTermMemory`
- `START -> loadLongTermMemory`
- `START -> loadDraftContext`

然后再分别接到：

- `loadShortTermMemory -> routeInteraction`
- `loadLongTermMemory -> routeInteraction`
- `loadDraftContext -> routeInteraction`

对应代码位置：

- `GraphRuntimeFactory.java:77`
- `GraphRuntimeFactory.java:78`
- `GraphRuntimeFactory.java:79`
- `GraphRuntimeFactory.java:80`
- `GraphRuntimeFactory.java:81`
- `GraphRuntimeFactory.java:82`

说人话就是：

- 以前像一个人先查短期记忆，再查长期记忆，再查 draft
- 现在像三个人同时去查，查完再汇总

### 1.2 三个节点分别做什么

#### `loadShortTermMemory`

代码在：

- `backend/platform-core/src/main/java/com/xg/platform/agent/core/interaction/InteractionGraphNodeService.java:171`

它会调用：

- `ConversationMemoryService.threadMemoryView(userId, threadId)`

返回：

- `MEMORY_VIEW`
- `SESSION_SUMMARY`

也就是说，它负责把短期记忆视图装进 state。

#### `loadLongTermMemory`

代码在：

- `backend/platform-core/src/main/java/com/xg/platform/agent/core/interaction/InteractionGraphNodeService.java:182`

它会做两件事：

1. `longTermMemoryRepository.listActive(userId)`
2. `memoryContextFormatter.formatLongTermMemory(..., threadId)`

最后把格式化后的长期记忆文本塞进：

- `LONG_TERM_MEMORY`

#### `loadDraftContext`

代码在：

- `backend/platform-core/src/main/java/com/xg/platform/agent/core/interaction/InteractionGraphNodeService.java:191`

它负责读取当前线程上的 research draft。

如果没有 draft，或者 draft 已经进入 `STARTED`，就直接返回空。

### 1.3 为什么说这是真的并行

因为这不是“代码顺序上先后调用三个函数”，而是 graph 从 `START` 同时发出三条边，再在 `routeInteraction` 汇合。

单测里也验证了这一点：

- `backend/platform-core/src/test/java/com/xg/platform/graph/GraphRuntimeFactoryTest.java:140`
- `backend/platform-core/src/test/java/com/xg/platform/graph/GraphRuntimeFactoryTest.java:141`
- `backend/platform-core/src/test/java/com/xg/platform/graph/GraphRuntimeFactoryTest.java:142`

这些断言表达的意思就是：

- `routeInteraction` 必须发生在三个 load 节点之后

所以这三段 load 是 graph fan-out / fan-in 结构，而不是串行直线流程。

## 2. 短期记忆为什么现在读得更快

### 2.1 不只是并行，还因为“写时预处理，读时快取”

短期记忆提速，不只是因为 graph 起点并行。

更重要的是：

- 短期记忆投影已经搬到异步链路
- 请求进来时尽量读现成的 snapshot / cache
- 避免每次请求现场全量重算

关键代码：

- `backend/platform-core/src/main/java/com/xg/platform/agent/core/application/ConversationMemoryService.java`
- `backend/platform-core/src/main/java/com/xg/platform/agent/core/application/ShortTermMemoryProjectionService.java`

### 2.2 写路径已经是后台异步

当消息完成后，会发 memory event。

相关代码：

- `backend/platform-core/src/main/java/com/xg/platform/agent/core/interaction/InteractionGraphNodeService.java:552`
- `backend/platform-api/src/main/java/com/xg/platform/api/runtime/LocalMemoryEventPublisher.java:21`
- `backend/platform-core/src/main/java/com/xg/platform/agent/core/application/DefaultMemoryEventProcessor.java:40`

`DefaultMemoryEventProcessor` 收到 `message.completed` 后，会做两件事：

1. `scheduleProjection(...)`
2. `longTermMemoryJobScheduler.schedule(...)`

也就是说：

- 短期记忆投影在后台刷新
- 长期记忆抽取也在后台调度

它们都不挡用户这一轮回答。

### 2.3 还有 debounce，避免反复重算

`DefaultMemoryEventProcessor` 里有：

- `projectorDebounceMs`

代码位置：

- `DefaultMemoryEventProcessor.java:21`
- `DefaultMemoryEventProcessor.java:57`

默认值在配置里是：

- `PlatformProperties.java:819`

默认：

- `projectorDebounceMs = 1000`

意思是：

- 同一个线程短时间内连着来几次事件
- 不会每次都立刻重跑 projection
- 会稍微等一等，合并成一次

这是为了减少后台抖动和重复计算。

### 2.4 读路径现在优先走 snapshot + recent window + Redis

`ConversationMemoryService.threadMemoryView(...)` 的核心逻辑是：

- 如果 `readModelAsync = false`，就直接现场重投影
- 如果 `readModelAsync = true`，就优先走轻量读取路径

相关代码：

- `ConversationMemoryService.java:45`

默认配置：

- `PlatformProperties.java:818`

默认：

- `readModelAsync = true`

此时它会：

1. 读最近消息窗口 `listRecentMessages(...)`
2. 读 `thread_memory_snapshots`
3. 尝试读 Redis 缓存
4. 如果 snapshot 和 cache 一致，就直接拼成 `ThreadMemoryView`
5. 如果 recent window 比 snapshot 新一点，只桥接差量 pending messages
6. 只有必要时才回源重建

所以它更像“读现成结果 + 小范围补差”，而不是“每次现算整份短期记忆”。

### 2.5 Redis 热缓存也已经接上

相关代码：

- `backend/platform-api/src/main/java/com/xg/platform/api/cache/redis/RedisThreadMemoryViewCache.java`
- `backend/platform-api/src/main/java/com/xg/platform/api/config/MemoryConfig.java`

读缓存：

- `RedisThreadMemoryViewCache.java:34`

写缓存：

- `RedisThreadMemoryViewCache.java:55`

读失败时不会打断主链，而是回退到 source of truth：

- `RedisThreadMemoryViewCache.java:36`

默认 TTL：

- `PlatformProperties.java:912`

也就是：

- `ttlHours = 24`

### 2.6 recent window 也做了预算控制

默认短期记忆窗口大小：

- `PlatformProperties.java:817`

默认：

- `windowSize = 20`

这意味着：

- prompt 里不会永远堆整条历史
- 最近 20 条消息保留原文
- 更早历史进入 summary 或 pending historical messages

这本身就是对首 token 有帮助的，因为 prompt 更短，准备更轻。

## 3. 长期记忆为什么没有挡住首 token

### 3.1 长期记忆抽取不是同步执行

长期记忆抽取不在用户当前回答路径里同步跑。

它是：

1. assistant 消息完成
2. `LongTermMemoryJobScheduler.schedule(...)`
3. 达到门槛才创建 job
4. 异步 dispatcher 执行
5. `LongTermMemoryExtractionService` 再去调用模型做抽取

相关代码：

- `backend/platform-core/src/main/java/com/xg/platform/agent/core/application/LongTermMemoryJobScheduler.java`
- `backend/platform-core/src/main/java/com/xg/platform/agent/core/application/LongTermMemoryExtractionService.java`

### 3.2 而且不是每轮都抽

默认配置：

- `turnInterval = 5`

位置：

- `PlatformProperties.java:937`

调度逻辑：

- `LongTermMemoryJobScheduler.java:47`
- `LongTermMemoryJobScheduler.java:52`

意思是：

- 不是每条 assistant 消息都触发一次长期记忆抽取
- 默认累计一定轮次后才调度

所以这块已经做过节流。

### 3.3 但长期记忆读取本身还有优化空间

当前长期记忆读法仍然比较直接：

1. `listActive(userId)`
2. 全量 `formatLongTermMemory(...)`

对应代码：

- `InteractionGraphNodeService.java:187`
- `backend/platform-core/src/main/java/com/xg/platform/agent/core/shared/MemoryContextFormatter.java`

所以现在是：

- 长期记忆写入已经异步化
- 但长期记忆读取和拼 prompt 还不算特别轻

## 4. 从用户发消息到首 token，真实链路是什么

### 第 1 步：HTTP 立刻返回 SSE

入口在：

- `backend/platform-api/src/main/java/com/xg/platform/api/controller/MessageController.java`

关键点：

- `SseEmitter(0L)`：`MessageController.java:54`
- 禁用缓冲：`MessageController.java:51`、`MessageController.java:52`
- 真正执行丢到线程池：`MessageController.java:59`

所以前端不会等整个回答结束后才拿到连接。

### 第 2 步：后台线程执行消息

入口：

- `AgentExecutionService.executeMessage(...)`

位置：

- `backend/platform-core/src/main/java/com/xg/platform/agent/core/AgentExecutionService.java:90`

这里先做：

- `prepareMessageExecution(...)`

然后才进 graph：

- `AgentExecutionService.java:110`

### 第 3 步：graph 起点并行装上下文

也就是前面说的三条并行支路：

- 短期记忆
- 长期记忆
- draft

### 第 4 步：路由

现在主聊天链路已经去掉独立 router 模型，改成 deterministic 护栏路由。

代码：

- `backend/platform-core/src/main/java/com/xg/platform/agent/core/chat/ChatRouterService.java`

这对首 token 是明确提速，因为少了一次额外模型调用。

### 第 5 步：`prepareAgentStep` 仍有一段同步准备

这一步仍然发生在模型开始吐第一个 token 之前，而且现在还不算轻。

代码：

- `InteractionGraphNodeService.java:285`

它会顺序做这些事：

1. 读 artifacts / uploaded files / input images
2. 如果是文档路由，装文档
3. 非文档路由时做 chunk retrieval
4. 先持久化用户消息
5. 解析 provider
6. 列出 available tools
7. 列出 available skills
8. 读取 persisted activeSkillIds
9. 发 `MESSAGE_ACCEPTED / RUN_STARTED / AGENT_SELECTED / ROUTE_SELECTED`
10. 最后 render prompt

其中比较重的点至少包括：

- `persistMessage(...)`：`InteractionGraphNodeService.java:301`
- `resolveProviderId(...)`：`InteractionGraphNodeService.java:310`
- `availableTools(...)`：`InteractionGraphNodeService.java:311`
- `skillRegistry.listDescriptors(...)`：`InteractionGraphNodeService.java:312`
- `loadPersistedActiveSkillIds(...)`：`InteractionGraphNodeService.java:313`
- `renderPrompt(...)`：`InteractionGraphNodeService.java:660`

### 第 6 步：真正开始模型 streaming

主聊天链路现在走的是 `runSingleStep(...)`。

代码入口：

- `InteractionGraphNodeService.java:374`
- `backend/platform-api/src/main/java/com/xg/platform/api/ai/ChatClientAgentTurnExecutionSupport.java`
- `backend/platform-api/src/main/java/com/xg/platform/api/ai/SpringAiAgentTurnExecutionSupport.java`

模型层会优先尝试 streaming。

在抽象基类里，还有明确的首个 delta 埋点：

- `AbstractSpringAiAgentTurnExecutionSupport.java:314`
- `AbstractSpringAiAgentTurnExecutionSupport.java:364`
- `AbstractSpringAiAgentTurnExecutionSupport.java:412`

也就是说，首 token 的真实耗时 = 模型真正开始出流之前的所有准备时间 + provider 首包时间。

## 5. 现在已经做过的首 token 优化

下面这些都已经是现状，不是建议。

### 5.1 interaction graph 起点并行加载短期记忆、长期记忆、draft

这是这次并行改造的主体。

### 5.2 短期记忆投影改成后台事件驱动

收益：

- 不需要在用户请求现场全量重建 thread memory

### 5.3 短期记忆读取优先走 snapshot + recent window + Redis

收益：

- 热路径从“重算”变成“读现成结果 + 桥接差量”

### 5.4 summary 压缩不在用户请求现场做

配置和装配在：

- `backend/platform-api/src/main/java/com/xg/platform/api/config/MemoryConfig.java:60`
- `PlatformProperties.java:855`

默认 summary 小模型：

- `gemini-3-flash-preview`

但它是在后台 memory projection 链路里使用，不在每次用户发消息时现算。

### 5.5 长期记忆抽取异步化，而且不是每轮都抽

收益：

- 避免 assistant 回复完后同步再跑一轮抽取模型

### 5.6 去掉了独立 router model

现在不是“小模型先路由，再主模型回答”，而是“确定性护栏 + 主模型直接决策”。

### 5.7 HTTP 入口已经做了 SSE + 后台线程执行

收益：

- 前端更早拿到连接，体感更快

### 5.8 模型层已经优先走 streaming

收益：

- provider 一旦开始出流，系统就会立刻往前推 delta

## 6. 后面还能做哪些优化

下面按收益和优先级排序。

### 6.1 最高优先级：不要为了拿 `providerId` 重复构造 `ChatModel`

这是当前最值得先改的点。

现在至少有三处会走 provider resolve：

1. `AgentExecutionService.prepareMessageExecution(...)`
   - `AgentExecutionService.java:139`
2. `InteractionGraphNodeService.prepareAgentStep(...)`
   - `InteractionGraphNodeService.java:310`
3. 真正模型执行时
   - `runSingleStep(...)`

问题在于：

- `resolveProviderId(...)` 表面上像“只拿 providerId”
- 但实现上会走到 `resolveProvider(...)`
- `resolveProvider(...)` 又会走到 `ConfiguredProviderClientResolver.resolve(...)`
- 最后会进入 `ModelProviderConfigService.resolveProvider(...)`
- 而那里会真的 `buildChatModel(...)`

相关代码：

- `backend/platform-api/src/main/java/com/xg/platform/api/ai/AbstractSpringAiAgentTurnExecutionSupport.java:102`
- `backend/platform-api/src/main/java/com/xg/platform/api/ai/ConfiguredProviderClientResolver.java:18`
- `backend/platform-api/src/main/java/com/xg/platform/api/model/ModelProviderConfigService.java:127`
- `ModelProviderConfigService.java:132`
- `ModelProviderConfigService.java:148`

说人话就是：

- 现在只是为了“确认 provider 合法不合法”
- 很可能已经把真正的 `ChatModel` 建出来了

而且这一轮里还可能建不止一次。

最推荐的改法：

1. 把“校验 provider 配置是否可用”和“构造 `ChatModel`”拆成两步
2. `prepareMessageExecution()` 只做轻量校验
3. 真正进入模型执行时再构造，或者直接缓存构造结果

### 6.2 很值得做：缓存 `ResolvedProviderClient / ChatModel`

现在 `ModelProviderConfigService.resolveProvider(...)` 每次都会 `buildChatModel(...)`。

这意味着：

- 同一个用户
- 同一个 provider
- 同一套 key / baseUrl / model

每轮请求都在重复 new 客户端。

推荐做法：

- 用 `(userId, providerId, configFingerprint)` 做 key
- 缓存 `ResolvedProviderClient`
- 配置变化时失效

这不仅能优化首 token，也能减少总延迟和对象创建开销。

### 6.3 很值得做：把 `prepareAgentStep` 里的独立读取继续并行化

graph 起点已经并行了，但 `prepareAgentStep` 里还有一串同步准备。

至少这几项比较适合继续并行：

- `artifactService.listArtifacts(...)`
- `availableTools(userId)`
- `skillRegistry.listDescriptors(userId)`
- `loadPersistedActiveSkillIds(userId, threadId)`

现在瓶颈已经不只是 memory load，而是 step 准备阶段的串行读取。

### 6.4 很值得做：减少模型调用前的同步事件写入

在真正开始调用模型前，目前还会先写：

- 用户消息
- `MESSAGE_ACCEPTED`
- `RUN_STARTED`
- `AGENT_SELECTED`
- `ROUTE_SELECTED`

这些都在首 token 前。

如果事件落库比较重，就会直接推迟首 token。

更好的方向：

- 保留必须同步的关键事件
- 把展示型、可重建型事件改成异步
- 或者合并成批量写

### 6.5 值得做：缓存 skill catalog 和 prompt 静态片段

当前每轮都会：

- 列所有 tools
- 列所有 skills
- 重新 render 整个系统 prompt

如果 skill 越来越多，这部分也会逐渐变重。

可以考虑：

- 缓存 `availableSkills`
- 缓存 skill catalog 的 prompt 片段
- 缓存系统 prompt 的静态部分

### 6.6 值得做：长期记忆不要每轮都全量拼接

当前长期记忆读取方式还是：

- `listActive(userId)`
- 全量格式化

随着 `long_term_memory` 变多，这会同时增加：

- DB 读取开销
- prompt 长度
- 模型首 token 前的准备时间

后续可以考虑：

- 缓存格式化后的长记忆
- 做 top-k recall
- 按 token budget 或条数做裁剪

### 6.7 文档问答路线还可以继续瘦身

当前 `DOCUMENT_QA` 路线天生比普通 chat 更重，因为它会做：

- 装文档
- 读 chunk index
- 推导 section titles
- 初始化 reading plan 和 scratchpad

如果以后继续优化文档问答的首 token，可以考虑：

- 在 ingest 阶段预计算 section titles
- 给 chunk index 热点元数据做缓存
- 避免每次请求都去读 artifact JSON

### 6.8 体感优化：更早发非内容事件

这不是让模型更早出 token，而是让用户更早感觉系统开始工作。

比如可以更早发：

- `RUN_STARTED`
- `AGENT_SELECTED`
- `MODEL_THINKING_STARTED`

这类事件现在已经有一部分了，但还可以更有意识地提前。

## 7. 我最推荐的优化顺序

如果按收益 / 风险比来排，我建议：

1. 先拆开 provider 校验和 `ChatModel` 构造
2. 再缓存 `ResolvedProviderClient / ChatModel`
3. 再并行化 `prepareAgentStep` 里的独立读取
4. 再收缩模型调用前的同步落库事件
5. 最后做长期记忆 top-k / prompt 片段缓存

## 8. 给后人一句话

这次并行改造已经解决了“短期记忆、长期记忆、draft 三段 load 串行”的问题。
现在真正继续拖慢首 token 的，已经更多是：

- provider 解析过重且重复
- `prepareAgentStep` 里还有不少同步准备
- 模型前还有几次同步持久化写入

所以后续优化重点，不应该只是“再给 memory 加并行”，而应该转向“压缩模型调用前的整段同步准备链路”。