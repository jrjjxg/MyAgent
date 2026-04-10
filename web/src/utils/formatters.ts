import { ArtifactRecord, RunEvent, ResearchSiteRecord, ResearchPlanStep, ResearchUpgradeSuggestion, ExecutionSource, ChatRouteKind, ModelProviderStatusRecord } from "../types";

export function isImageArtifact(artifact: ArtifactRecord) {
  return artifact.contentType?.toLowerCase().startsWith("image/") ?? false;
}

export function describeProviderReadiness(provider: ModelProviderStatusRecord | null | undefined) {
  if (!provider) return "";
  if (!provider.enabled) return `模型提供商 ${provider.displayName} 已禁用。请打开模型设置并启用它。`;
  if (!provider.apiKeyConfigured) return `模型提供商 ${provider.displayName} 尚未配置：缺少 API 密钥。请先打开模型设置保存 API 密钥。`;
  if (!provider.effectiveModel) return `模型提供商 ${provider.displayName} 尚未配置：缺少模型。请先打开模型设置设置模型。`;
  if (!provider.ready) return `模型提供商 ${provider.displayName} 尚未就绪。请打开模型设置完成设置。`;
  return "";
}

export function formatTime(value?: string) {
  if (!value) return "--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}

export function formatTaskStage(stage?: string | null) {
  switch (stage) {
    case "plan": return "计划已批准";
    case "researching": return "研究中";
    case "synthesizing": return "综合分析中";
    case "writing": return "撰写报告中";
    case "completed": return "已完成";
    default: return stage || "--";
  }
}

export function formatEventType(eventType: string) {
  switch (eventType) {
    case "route.selected": return "选择路由";
    case "run.started": return "运行开始";
    case "run.completed": return "运行完成";
    case "model.thinking.started": return "模型思考";
    case "model.thinking.delta": return "模型思考";
    case "model.thinking": return "模型思考";
    case "model.thinking.completed": return "思考完成";
    case "research.upgrade.suggested": return "建议深度研究";
    case "research.plan.approved": return "研究计划已批准";
    case "research.site.discovered": return "发现站点";
    case "research.activity": return "研究活动";
    case "tool.started": return "工具开始";
    case "tool.completed": return "工具完成";
    case "tool.failed": return "工具失败";
    case "agent.step.started": return "Step started";
    case "agent.step.delta": return "Step output";
    case "agent.step.completed": return "Step completed";
    case "task.created": return "任务已创建";
    default: return eventType;
  }
}

export function truncate(value: string, maxLength = 80) {
  const normalized = value.replace(/\s+/g, " ").trim();
  return normalized.length > maxLength ? `${normalized.slice(0, maxLength)}...` : normalized;
}

export function readString(value: unknown) {
  return typeof value === "string" ? value : "";
}

export function readPlanSummary(event?: RunEvent | null): { title: string; summary: string; steps: ResearchPlanStep[] } | null {
  if (!event || event.eventType !== "research.plan.approved" || typeof event.payload !== "object" || !event.payload) return null;
  const payload = event.payload as { title?: unknown; brief?: unknown; planSummary?: unknown; planSteps?: unknown };
  if (typeof payload.title !== "string") return null;
  return {
    title: payload.title,
    summary: readString(payload.planSummary) || readString(payload.brief),
    steps: Array.isArray(payload.planSteps)
      ? payload.planSteps.filter((value): value is ResearchPlanStep => typeof value === "object" && value !== null && typeof (value as ResearchPlanStep).title === "string")
      : []
  };
}

export function readResearchSite(event: RunEvent): ResearchSiteRecord | null {
  if (event.eventType !== "research.site.discovered" || typeof event.payload !== "object" || !event.payload) return null;
  const payload = event.payload as Partial<ResearchSiteRecord>;
  if (typeof payload.url !== "string" || typeof payload.title !== "string") return null;
  return {
    stepId: readString(payload.stepId),
    url: payload.url,
    title: payload.title,
    domain: readString(payload.domain),
    sourceType: readString(payload.sourceType)
  };
}

export function readResearchUpgradeSuggestion(event?: RunEvent | null): ResearchUpgradeSuggestion | null {
  if (!event || event.eventType !== "research.upgrade.suggested" || typeof event.payload !== "object" || !event.payload) return null;
  const payload = event.payload as Partial<ResearchUpgradeSuggestion>;
  if (typeof payload.reason !== "string" || typeof payload.suggestedBrief !== "string") return null;
  return {
    reason: payload.reason,
    suggestedTitle: readString(payload.suggestedTitle),
    suggestedBrief: payload.suggestedBrief
  };
}

export function readRouteKind(event?: RunEvent | null): ChatRouteKind | null {
  if (!event || typeof event.payload !== "object" || !event.payload) return null;
  const payload = event.payload as { routeKind?: unknown };
  switch (payload.routeKind) {
    case "GENERAL_CHAT":
    case "TOOL_ASSISTED_CHAT":
    case "REALTIME_LOOKUP":
    case "DOCUMENT_QA":
    case "RESEARCH_DRAFT":
      return payload.routeKind;
    default:
      return null;
  }
}

export function readExecutionSources(event?: RunEvent | null): ExecutionSource[] {
  if (!event || event.eventType !== "run.completed" || typeof event.payload !== "object" || !event.payload) return [];
  const payload = event.payload as { sources?: unknown };
  if (!Array.isArray(payload.sources)) return [];
  return payload.sources
    .filter((value): value is Record<string, unknown> => typeof value === "object" && value !== null)
    .map((value) => ({
      kind: readString(value.kind),
      title: readString(value.title),
      domain: readString(value.domain),
      url: readString(value.url),
      verified: Boolean(value.verified),
      usedInAnswer: value.usedInAnswer !== false
    }))
    .filter((value) => value.title && value.url);
}

export function readToolName(event?: RunEvent | null) {
  if (!event || typeof event.payload !== "object" || !event.payload) return "";
  const payload = event.payload as { toolName?: unknown };
  return readString(payload.toolName);
}

export function fallbackDomain(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return "";
  }
}

export function eventSummary(event: RunEvent) {
  if (typeof event.payload !== "object" || !event.payload) return "";
  const payload = event.payload as Record<string, unknown>;
  if (event.eventType === "research.upgrade.suggested") {
    return readString(payload.reason) || readString(payload.suggestedTitle) || readString(payload.suggestedBrief);
  }
  if (event.eventType === "model.thinking.started") return "Thinking started";
  if (event.eventType === "model.thinking.delta") return readString(payload.delta);
  if (event.eventType === "model.thinking") return readString(payload.content) || readString(payload.summary);
  if (event.eventType === "model.thinking.completed") return readString(payload.summary) || readString(payload.content);
  if (event.eventType === "agent.step.delta") return readString(payload.delta);
  if (event.eventType === "agent.step.completed") return readString(payload.content) || readString(payload.summary);
  if (event.eventType === "tool.started") return `Calling ${readToolName(event) || "tool"}`;
  if (event.eventType === "tool.completed") return `Completed ${readToolName(event) || "tool"}`;
  if (event.eventType === "tool.failed") {
    const toolName = readToolName(event) || "tool";
    const error = readString(payload.error);
    return error ? `${toolName} failed: ${error}` : `${toolName} failed`;
  }
  if (typeof payload.summary === "string") return payload.summary;
  if (typeof payload.error === "string") return payload.error;
  if (typeof payload.toolName === "string") return `Tool: ${payload.toolName}`;
  if (typeof payload.url === "string") return payload.url;
  return "";
}

export const HIDDEN_PROCESS_EVENT_TYPES = new Set([
  "message.delta",
  "message.accepted",
  "message.completed",
  "model.thinking.started",
  "model.thinking.delta",
  "model.thinking",
  "model.thinking.completed",
  "agent.step.started",
  "agent.step.delta",
  "agent.step.completed"
]);

export const HIDDEN_RECENT_EVENT_TYPES = new Set([
  ...HIDDEN_PROCESS_EVENT_TYPES
]);
