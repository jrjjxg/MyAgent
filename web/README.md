# Web

Updated: 2026-04-09

`myagent/web` 是当前项目的独立 React + Vite 前端。

## 运行方式

先启动后端：

```powershell
cd d:\deepagents\myagent
. .\.local\postgres-env.ps1
powershell -ExecutionPolicy Bypass -File .\ops\run-platform-api.ps1
```

再启动前端：

```powershell
cd d:\deepagents\myagent\web
npm install
npm run dev
```

默认地址：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`

如果后端不在默认地址，可通过 `VITE_API_TARGET` 覆盖代理目标。

## 当前代理配置

Vite 当前代理这些路径到后端：

- `/workspaces`
- `/threads`
- `/memory`
- `/skills`
- `/model-settings`
- `/web-settings`

## 当前前端范围

- 用户登录态（本地存储 userId / apiBase / provider）
- 工作区与线程侧栏
- 普通聊天 SSE
- 工作区级/线程级文件上传
- Deep Research draft editor
- 研究任务 plan / timeline / report 面板
- Inspector
- 模型、技能、Web 搜索设置面板

## 当前已知缺口

- 部分中文 UI 文案存在编码问题
- 图片附件上传和图片预览仍残留旧 `/api/v1/users/...` 路径
- 当前后端没有对应 artifact content/download 接口，因此这部分体验尚未闭环
- 后端已经支持分片上传，但前端主上传流程还没有完整接入
