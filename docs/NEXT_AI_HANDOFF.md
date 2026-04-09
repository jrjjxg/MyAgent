# Next AI Handoff

Updated: 2026-04-09

## 先确认这些事实

- 当前正式模块是 `platform-contracts / platform-core / platform-api`
- `CHAT` 与 `DEEP_RESEARCH` 共用消息入口
- `RESEARCH` 才是后台异步任务
- PostgreSQL 是运行时必需项
- 研究结果查询 API 已经落地，不要再把它们当成 future work

## 建议先读的文件

1. `../README.md`
2. `ARCHITECTURE.md`
3. `API_DATA_MODEL.md`
4. `STATUS.md`
5. `../backend/platform-api/src/main/java/com/xg/platform/api/config/GraphConfig.java`
6. `../backend/platform-core/src/main/java/com/xg/platform/graph/GraphRuntimeFactory.java`
7. `../backend/platform-core/src/main/java/com/xg/platform/agent/core/AgentExecutionService.java`
8. `../backend/platform-api/src/main/java/com/xg/platform/api/service/TaskResearchReadService.java`

## 现在最值得做的事

1. 修正前端遗留旧路径：
   - `/api/v1/users/...`
   - 图片附件上传
   - artifact 预览
2. 补 artifact content/download API，让前端预览与报告产物读取闭环。
3. 清理前端中文乱码，恢复可读 UI 文案。
4. 继续增强研究结果 drill-down：
   - citation offset
   - paragraph/block 级精确映射
   - 更好的 source drawer 数据

## 不要倒退回去的方向

- 不要回到 file-backed metadata persistence
- 不要重新引入自定义 provider 协议
- 不要把 Redis 当主存储
- 不要优先做 swarm / multi-agent / 并行研究单元
