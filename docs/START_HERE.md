# MyAgent 文档入口

Updated: 2026-04-09

如果你是第一次接手这个项目，先建立下面几个事实：

- 这是一个多用户工作区产品，不是本地 coding agent
- 当前主线已经是正式运行架构，不是迁移中的半成品
- 深度研究采用 `先起草计划，再异步执行` 的产品模型
- PostgreSQL 是运行时必需品

## 建议阅读顺序

1. `../README.md`
2. `ARCHITECTURE.md`
3. `API_DATA_MODEL.md`
4. `PERSISTENCE_AND_ASYNC.md`
5. `STATUS.md`
6. `LONG_TERM_MEMORY_EXPLAINED.md`

如果你要继续做 deep research 相关工作，再看：

1. `DEEP_RESEARCH_SYSTEM_PLAN.md`
2. `DEEP_RESEARCH_EVIDENCE_VISIBILITY_SPEC.md`
3. `DEEP_RESEARCH_PLAN_EDITING_SPEC.md`

如果你是接着做开发实现，再看：

1. `BACKEND_MODULE_LAYOUT.md`
2. `DECISIONS.md`
3. `NEXT_AI_HANDOFF.md`

## 当前项目定位

`myagent` 现在已经具备一条比较完整的产品主链路：

- 工作区与线程
- 普通聊天
- 文档上传与异步入库
- Deep Research 起草与启动
- 异步研究任务
- 研究报告、来源、引用与迭代信息读取
- 短期记忆与长期记忆
- 用户级模型、技能、Web 搜索配置

## 现在不要再按旧认知理解它

下面这些说法都已经过时：

- “LangGraph4j 迁移还没完成”
- “message transcript 还没持久化”
- “研究来源和 findings 还是未来接口”
- “项目还是文件元数据持久化主导”
- “后端还没有工作区模型”

## 关键现实

- `CHAT` 和 `DEEP_RESEARCH` 共用 `POST /threads/{threadId}/messages`
- `DEEP_RESEARCH` 在启动前先落成 `ResearchDraftRecord`
- 真正后台执行的只有 `RESEARCH` 任务
- 研究运行时数据既有数据库快照，也有落盘输出文件
- 短期记忆来自 `messages / research_drafts / tasks` 的投影
- 长期记忆已经收口到 `long_term_memory`

## 文档维护原则

`docs` 目录现在只保留两类内容：

- 当前实现事实
- 仍然有效的设计约束

明显历史性、面试准备类、已完成迁移计划类文档，已经不再作为主文档维护。
