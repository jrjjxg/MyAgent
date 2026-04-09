# MyAgent

Updated: 2026-04-09

`myagent` 是一个面向多用户工作区的对话与深度研究平台，当前形态是：

- `Spring Boot + Spring AI + LangGraph4j` 后端
- `React + Vite` 前端
- `Workspace -> Thread -> Message / Draft / Task` 的交互模型
- 文档上传与异步入库
- 短期记忆、长期记忆、研究报告和来源可视化

## 当前能力

- 普通对话：`CHAT` 模式，SSE 流式返回，消息持久化
- 深度研究：先生成可编辑 `ResearchDraft`，再启动异步 `RESEARCH` 任务
- 文档能力：线程级/工作区级上传，后端已支持分片上传会话
- 研究结果：报告、计划、迭代、发现、来源、引用均可单独读取
- 记忆能力：
  - 短期记忆：`ThreadMemoryView`
  - 长期记忆：`long_term_memory`
  - 显式档案接口：`/memory/profile`、`/memory/facts`
- 配置能力：模型提供商、技能、Web 搜索设置均为用户级配置

## 项目结构

- `backend/platform-contracts`
  - 共享 DTO、枚举、事件与记录类型
- `backend/platform-core`
  - 业务主逻辑、图编排、研究执行、记忆链路、工具与工作区核心实现
- `backend/platform-api`
  - Spring Boot 入口、控制器、配置、MyBatis/Redis/RocketMQ/Spring AI 适配
- `web`
  - React + Vite 前端
- `docs`
  - 维护中的项目文档
- `ops`
  - 本地运行与运维脚本
- `tools`
  - 文档处理脚本等辅助工具
- `skills`
  - 本地技能目录
- `data`
  - 本地数据根目录，默认由后端使用

## 运行要求

- Java 17
- Node.js 18+
- PostgreSQL
- Redis
  - 可选，但当前推荐开启；用于热缓存和分片上传状态
- RocketMQ
  - 可选，仅在 `platform.async.mode=rocketmq` 时需要
- 至少一个可用模型 API Key
  - 默认主路径偏向 `Gemini`

## 快速启动

1. 准备数据库与本地环境变量

```powershell
cd d:\deepagents\myagent
. .\.local\postgres-env.ps1
```

2. 启动后端

```powershell
cd d:\deepagents\myagent
powershell -ExecutionPolicy Bypass -File .\ops\run-platform-api.ps1
```

3. 启动前端

```powershell
cd d:\deepagents\myagent\web
npm install
npm run dev
```

默认情况下：

- 后端：`http://localhost:8080`
- 前端：`http://localhost:5173`

## 关键环境变量

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `GOOGLE_API_KEY`
- `OPENAI_API_KEY`
- `DEEPSEEK_API_KEY`
- `PLATFORM_SKILLS_SECRET_ENCRYPTION_KEY`
- `PLATFORM_ASYNC_MODE`
- `ROCKETMQ_NAME_SERVER`
- `REDIS_HOST`
- `REDIS_PORT`

## 文档入口

先看这些：

1. `docs/START_HERE.md`
2. `docs/ARCHITECTURE.md`
3. `docs/API_DATA_MODEL.md`
4. `docs/PERSISTENCE_AND_ASYNC.md`
5. `docs/STATUS.md`
6. `docs/LONG_TERM_MEMORY_EXPLAINED.md`

补充：

- `docs/DEEP_RESEARCH_SYSTEM_PLAN.md`
- `docs/DEEP_RESEARCH_EVIDENCE_VISIBILITY_SPEC.md`
- `docs/DEEP_RESEARCH_PLAN_EDITING_SPEC.md`
- `docs/NEXT_AI_HANDOFF.md`
- `web/README.md`
- `ops/README.md`

## 当前边界

- 运行时强依赖 PostgreSQL；不是可选项
- 同一线程只允许一个活跃 `RESEARCH` 任务
- 前端已经有图片附件与制品预览入口，但仍残留旧 `/api/v1/...` 路径，和当前后端控制器不完全对齐
- 后端当前没有独立的 artifact content/download 控制器
- 分片上传能力已在后端落地，但前端主路径仍主要使用简单 multipart 上传
