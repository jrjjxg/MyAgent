# Backend Module Layout

Updated: 2026-04-09

## 当前有效模块

后端已经稳定收敛为 3 个 Maven 模块：

- `platform-contracts`
  - 共享 record、DTO、enum、事件契约
- `platform-core`
  - 业务主逻辑
  - graph runtime
  - chat/research/memory/document/workspace/tooling 核心实现
- `platform-api`
  - Spring Boot 入口
  - controller
  - config
  - MyBatis / Redis / RocketMQ / Spring AI 适配

## 这意味着什么

- 模块拆分重点已经从“按子领域拆很多模块”回到“契约 / 核心 / 适配”三层
- 历史上的若干子模块职责已经并入 `platform-core`
- 现在继续理解代码时，不要再按旧模块名去找主实现

## 代码阅读建议

### 先看 `platform-api`

因为这里最容易看清：

- 暴露了哪些接口
- 配置从哪里进来
- 哪些地方接了 PostgreSQL / Redis / RocketMQ / Spring AI

### 再看 `platform-core`

这里是主业务：

- `AgentExecutionService`
- `GraphRuntimeFactory`
- `InteractionGraphNodeService`
- `ResearchExecutionGraphNodeService`
- memory / ingest / tooling / workspace 相关服务

### `platform-contracts`

最后再看共享类型，帮助你把 controller、frontend 和 core 里的 record 对上。

## 当前不应该再做的误判

- 不要再期待有独立 `platform-agent-docs`、`platform-memory`、`platform-runtime` 作为主入口
- 不要按“迁移尚未完成”的心态理解模块结构
- 当前主问题已经不是模块还没合并，而是如何继续清理边界和补全产品能力
