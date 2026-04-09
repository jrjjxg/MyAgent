# Skill & Tools

## 这份文档解决什么问题

这次改造的目标，是把 `myagent` 的 `skill` / `tool` 关系收敛到更接近 `openclaw` 的思路：

- 有明显匹配的 `skill`：先看 skill，再决定怎么做
- 没有足够合适的 `skill`：直接走通用 `tools` + 普通推理
- skill 试到一半发现不适合：允许退出 skill，回到通用模式

重点不是给系统再加很多层状态，而是把决定权尽量交给主对话模型，同时保留少量稳定的硬护栏。

## 当前已经落地的设计

### 1. 路由不再额外调用小模型

主聊天链路已经去掉“先跑一次 router model，再进入主对话模型”的做法。

现在 `ChatRouterService` 是一个纯本地、确定性的护栏路由器：

- `DEEP_RESEARCH` 仍由上游显式进入 research 流程
- 显式选择了文档：走 `DOCUMENT_QA`
- 有文档且用户问题带明显文档问答意图：走 `DOCUMENT_QA`
- 其他情况：都走 `CHAT`

这样做的目的很简单：

- 少一次模型调用
- 降低延迟
- 不再让两个模型在“先路由、再回答”这件事上互相拖慢

也就是说，真正的“这轮要不要先用 skill、要不要直接用 tool、要不要查文档”，现在主要交给主对话模型自己决定。

### 2. 主对话 prompt 改成了 skill-first，但不是 skill-only

`CHAT` 模式下的 prompt 现在明确要求模型：

- 先扫描 `<available_skills>`
- 如果只有一个 skill 明显适合，先 `load_skill`
- 如果没有明显 skill，不要硬凑，直接走通用 tools
- 如果 loaded skill 中途不再适合，允许退回通用模式
- 如果线程里的文档明显相关，即使还在 `CHAT` 路由，也可以直接用文档工具

这点很重要：  
这里不是“skill 强制门禁”，而是“skill 优先决策，tools 负责执行，必要时可回退”。

### 3. skill catalog 现在会把真正关键的决策信息给到模型

`available_skills` 现在不再只是一个简单目录，而是会把下面这些字段直接渲染进 prompt：

- `triggers`
- `preferredTools`
- `allowedTools`
- `requiresWeb`
- `requiresDocuments`
- `invocation`
- `execution`

这意味着主模型在看到 skill 时，已经能同时知道：

- 哪类请求更像命中这个 skill
- 命中后优先该用哪些工具
- 这个 skill 有哪些边界和依赖

### 4. loaded skill 不再只剩一个空壳摘要

这次没有新增新的持久化结构，也没有把整篇 `SKILL.md` 一直塞回 prompt。

现在的做法是：

- 继续复用已有的 `activeSkillIds`
- 在渲染 `<loaded_skills>` 时，直接从 `SkillDefinition.body()` 中提炼一个 `workflowSummary`
- 同时把 `preferredTools` 一起带出来

这样做的效果是：

- 第一轮 `load_skill` 后看到的 workflow，不会在下一轮完全丢失
- 模型会知道“这个 skill 现在仍然建议我优先怎么做”
- 但如果任务已经变了，它也能自然回退，不会被硬锁住

### 5. active skill 现在会软性影响工具顺序

这次故意没有走“全局硬过滤工具”的重设计，而是先做更轻、更接近 `openclaw` 的版本。

主 graph 现在会：

1. 先拿到全量工具
2. 根据 `activeSkillIds` 解析当前 loaded skills
3. 用这些 skill 的 `preferredTools` 对工具顺序做软排序
4. 把排好序的工具交给 prompt 和模型

也就是说：

- skill 会把更合适的工具顶到前面
- 但不会把通用工具整体藏掉
- 所以 agent 仍然有回退空间

这个取舍是有意为之。  
如果第一版就全量按 `allowedTools` 硬限制，agent 反而容易被 skill 绑死，不够灵活。

## 现在的关系，用一句人话怎么理解

在当前实现里：

- `skill` 更像“优先工作方法”和“执行建议”
- `tool` 更像“真正干活的接口”

它们的关系不是：

- 一个 skill 对应一个 tool

而更像：

- skill 告诉模型这类任务应该先怎么做
- tool 负责把 skill 里的建议真正执行出来

## 以 weather 为例，现在的目标行为

如果用户说：

- “帮我看下天津明天天气”

理想路径是：

1. 主模型在 `<available_skills>` 里发现 `weather` 明显匹配
2. 先 `load_skill("weather")`
3. 后续轮次里，`weather` skill 的 `workflowSummary` 和 `preferredTools` 还在 prompt 中
4. 工具顺序会优先把 `weather` 相关首选工具排到前面
5. 如果结构化天气工具不够，模型仍然可以退回 `web_search / web_fetch`

这就是“先 skill，再 tool，必要时回退”的目标语义。

## 为什么这次没有继续加很多状态层

这次有意没有引入下面这些更重的结构：

- `recommendedSkillIds`
- `selectedSkillIds`
- `lockedSkillIds`
- 专门的 skill planner / skill router model
- 全局硬性 tool policy engine

原因是当前最重要的不是“层次更多”，而是：

- 主模型能不能自己先看 skill
- loaded skill 能不能在多轮里保留足够的 workflow 上下文
- skill 能不能对工具使用产生真实但不过度的影响

如果这些基础能力没立住，先堆很多状态只会让系统更重、更难维护。

## 当前保留的硬护栏

为了避免系统完全失控，还是保留了少量确定性规则：

- 文档明确选中时，直接进 `DOCUMENT_QA`
- 文档意图非常明显时，直接进 `DOCUMENT_QA`
- deep research 继续走专门的 research 流程

这些护栏的目的不是替模型做细决策，而是避免明显场景被误走到普通 chat。

## 仍然没有做的事

当前版本已经把设计方向扳正了，但还没有做到“完全由 skill 定义工具边界”。

目前仍然是：

- `allowedTools` 已经会进 prompt，但没有在主聊天 graph 里做全局硬限制
- skill 选择仍主要依赖主模型阅读 prompt，不是独立 runtime 状态机
- 还没有拆出 `selectedSkillIds` / `activeSkillIds` 这样的双层状态

这是刻意保守，不是遗漏。  
当前版本优先保证：

- 速度更快
- 架构更轻
- agent 自主性更高
- 技能命中失败时，仍能自然回退

## 后续如果还要继续演进，优先顺序是什么

如果未来还要继续往前推，建议按这个顺序：

1. 先观察真实对话里 skill 命中率和误命中率
2. 只对少数高风险 skill 增加 `allowedTools` 硬限制
3. 只有在确实需要时，再补轻量的 skill-selection runtime 状态

不要一开始就把系统改成多层 skill 状态机。

## 给后人一句话

这次改造的核心，不是“让 skill 管死 tools”，而是“让主对话模型先尊重 skill，再自由调用 tools，并且随时可以回退”。  
如果以后继续改，请优先守住这个方向，不要又回到“先多跑一个路由模型”或者“再堆一层复杂状态”的老路上。
