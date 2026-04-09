# Deep Research Evidence And Visibility Spec

Updated: 2026-04-09

## 目标

deep research 的用户体验必须满足两件事：

1. 最终报告里的来源要清楚
2. 研究运行过程要可见

这份文档描述当前代码已经实现到什么程度，以及还缺什么。

## 当前已经落地的部分

### 1. 结构化来源模型

研究来源已经不是简单字符串，而是正式 `ResearchSourceRecord`。

当前可读字段包括：

- `sourceId`
- `kind`
- `title`
- `uri`
- `locator`
- `snippet`
- `domain`
- `citationLabel`
- `iterationNo`
- `evidenceStatus`
- `verificationMethod`
- `supportingFindingIds`
- `citationIds`

### 2. 结构化 findings / iterations / citations

当前已经有正式读取接口：

- `GET .../plan`
- `GET .../iterations`
- `GET .../findings`
- `GET .../sources`
- `GET .../citations`

这意味着来源、发现、迭代不再只是 future spec。

### 3. 报告读取不只是 markdown

`GET .../report` 返回的是 `ResearchReportView`：

- `markdown`
- `blocks`

`blocks` 是根据报告段落和 citation label 生成的块级视图，方便前端做 report drill-down。

### 4. 运行中可视化

研究运行过程中，系统会持续写入：

- `run_events`
- `research_task_snapshots`

所以前端可以看到：

- 当前阶段
- 迭代摘要
- 发现的站点
- 研究活动
- 最新 findings / sources / citations

## 当前的可见性分层

### 时间线层

由事件与迭代构成，回答“研究过程发生了什么”。

### 证据层

由 `ResearchSourceRecord` 构成，回答“找到了哪些材料”。

### 结论层

由 `ResearchFindingRecord` 和 `ReportCitation` 构成，回答“哪些来源支撑了哪些结论”。

### 报告层

由 `ResearchReportView` 构成，回答“最终给用户看的文本是什么，以及文本里用了哪些引用”。

## 当前来源语义

当前系统里，至少要区分三件事：

- 搜索发现
- 可引用证据
- 模型中间推理

最终报告应尽量建立在可引用证据上，而不是搜索结果摘要或模型自行推断。

## 已实现的前端承载

当前前端研究面板已经有：

- `Plan`
- `Timeline`
- `Report`

并且有 `SourceDrawer` 作为来源明细入口。

Inspector 里还会补充：

- 研究站点
- 最近事件

## 仍然存在的缺口

### 1. 引用定位还不够细

当前已经有：

- `paragraphId`
- `blockId`
- `anchorText`

但还没有真正的字符级 offset。

### 2. 来源内容读取闭环不完整

当前有来源列表和引用关系，但没有完整 artifact/download/content API。

### 3. Source drawer 信息仍可增强

当前前端已经能展示来源与其支持的 findings，但还可以继续补：

- 更多 locator 信息
- 更完整的 snippet / excerpt
- 与 report block 的反向跳转

## 结论

当前 deep research 已经具备“来源可见、过程可见、结果可拆读”的基本骨架。

接下来的工作重点不是“从零定义 spec”，而是把现有结构化结果继续做细、做顺手、做闭环。
