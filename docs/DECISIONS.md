# 关键决策

Updated: 2026-04-09

## 1. 产品定位

`myagent` 是多用户工作区研究平台，不是本地 coding agent。

## 2. 工作区优先

顶层容器是 `Workspace`，线程和文档都依附工作区存在。

## 3. Deep Research 必须先 draft，后 task

深度研究不是一句话直接进后台长任务，而是：

1. 先在对话里起草计划
2. 人可编辑和确认
3. 再启动异步研究任务

## 4. 普通聊天不是任务

普通聊天走消息流和 interaction graph，不创建后台 task。

## 5. `LangGraph4j` 只负责 workflow / state / checkpoint

它是正式运行内核，但不承担模型调用层职责。

## 6. `Spring AI` 是模型执行主层

Provider 解析、模型调用、tool calling 统一走这条主线。

## 7. 运行时强制 PostgreSQL

业务元数据与 checkpoint 的正式运行基线都是 PostgreSQL。

## 8. Redis 只做缓存与临时状态

Redis 不是主数据库，也不是 checkpoint 存储。

## 9. 短期记忆采用 thread 级投影视图

- 对外是 `ThreadMemoryView`
- 上游是 `messages / research_drafts / tasks`
- 持久化是 `thread_memory_snapshots`

## 10. 长期记忆统一为单一底座

长期记忆底座是 `long_term_memory`，`profile` 和 `facts` 只是语义化 API，不是独立核心存储。

## 11. 研究结果必须可拆开读取

研究结果不是只有一份最终 markdown，还要能读取：

- plan
- iterations
- findings
- sources
- citations

## 12. 一个线程只允许一个活跃研究任务

当前产品明确不走 swarm / multi-agent / 并行 research units 路线。
