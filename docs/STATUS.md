# 当前状态

Updated: 2026-04-09

## 已经稳定落地的部分

### 核心产品链路

- 工作区与线程模型已经落地
- 普通对话支持 SSE 流式输出
- 消息、事件、任务、草案都已持久化
- Deep Research 已经采用 `draft -> review/edit -> start task` 模式
- 研究任务完成后可读取报告、计划、迭代、发现、来源、引用

### 后端架构

- 后端已收敛为 3 个 Maven 模块：
  - `platform-contracts`
  - `platform-core`
  - `platform-api`
- `LangGraph4j` 已经是正式 workflow runtime
- interaction graph 与 research graph 都在启动期编译并复用
- `Spring AI` 已进入主执行路径

### 持久化与异步

- PostgreSQL 是运行时唯一正式数据库基线
- 文件系统只负责数据根目录、上传、artifacts、研究输出与文档分块
- Redis 已用于短期记忆缓存与分片上传状态
- RocketMQ 已有独立适配与本地脚本，属于可选异步后端

### 记忆链路

- 短期记忆：
  - `ThreadMemoryView`
  - `thread_memory_snapshots`
  - Redis thread 级缓存
- 长期记忆：
  - `long_term_memory`
  - `memory_extraction_jobs`
  - `PROFILE / SEMANTIC / EPISODIC`
- 用户档案与 stable facts 已有显式 API

### 文档与研究

- 上传文件后会创建 `Artifact` 并调度文档入库
- 后端已支持简单上传与分片上传两条路径
- Deep Research 结果已经有结构化读取接口，不再是纯 markdown 黑盒

### 前端

- 三栏布局已成型
- 工作区/线程切换可用
- draft editor 可直接编辑研究计划
- 研究任务面板可查看 plan/timeline/report
- 模型、技能、Web 搜索设置面板已接入

## 当前明确存在的缺口

### 前端遗留问题

- 多处中文文案存在编码异常，界面里会出现乱码
- 图片附件上传和图片预览仍调用旧 `/api/v1/users/...` 路径
- 当前后端没有对应的 artifact content 接口，因此图片相关体验并不可靠

### 上传路径不完全统一

- 后端已经具备分片上传能力
- 当前前端主流程仍主要走简单 multipart 上传

### 制品读取能力还不完整

- 已有 artifact/document 列表接口
- 但没有独立 artifact content/download controller

### 产品边界仍然明确

- 同一线程只允许一个活跃 `RESEARCH` 任务
- 研究任务更新支持 refinement，但不是并行 swarm/multi-agent 模式
- 私有连接器/MCP 型外部研究源还没有进入正式主线

## 测试基线

- 启动校验测试覆盖 PostgreSQL 缺失和非 PostgreSQL 场景
- `platform-api` 有 PostgreSQL 集成测试
- RocketMQ 有显式集成测试入口
- API 集成测试已经覆盖：
  - 聊天 SSE
  - 上传与文档
  - Deep Research draft/start
  - Memory APIs

## 最值得继续推进的事情

1. 修正前端遗留旧路径：
   - `/api/v1/users/...`
   - 图片附件上传
   - artifact 预览
2. 补 artifact content/download API，让前端预览与报告产物读取闭环。
3. 清理前端中文乱码，恢复可读 UI 文案。
