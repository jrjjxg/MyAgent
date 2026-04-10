import React, { useState, useEffect } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { RunEvent } from "../../types";
import {
  formatTime, formatEventType, eventSummary, readString, HIDDEN_PROCESS_EVENT_TYPES, readToolName
} from "../../utils/formatters";

export function MarkdownBlock({ content }: { content: string }) {
  return (
    <div className="markdown-body">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={{ a: ({ ...props }) => <a {...props} target="_blank" rel="noreferrer" /> }}>
        {content}
      </ReactMarkdown>
    </div>
  );
}

export function AssistantMessageBlock({ content, sources, pending }: { content: string; sources: any[]; pending?: boolean }) {
  const resolvedContent = content.trim() ? content : pending ? "*思考中...*" : "";
  return (
    <div className="message-body-shell">
      <MarkdownBlock content={resolvedContent} />
      {sources.length > 0 ? (
        <div className="source-strip">
          <span className="message-evidence-tag">来源 {sources.length}</span>
          <div className="source-chip-list">
            {sources.map((source, index) => (
              <a key={source.id} className="source-chip" href={source.url} target="_blank" rel="noreferrer">
                <span className="source-chip-index">{index + 1}</span>
                <span className="source-chip-meta">
                  <strong className="source-chip-label">{source.title}</strong>
                  <span className="source-chip-host">{source.domain || source.url}</span>
                </span>
              </a>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

export function AgentStepTrace({ events }: { events: RunEvent[] }) {
  const hasNativeThinking = events.some((event) =>
    event.eventType === "model.thinking.started"
    || event.eventType === "model.thinking.delta"
    || event.eventType === "model.thinking.completed"
    || event.eventType === "model.thinking"
  );
  const hasThinkingCompleted = events.some((event) => event.eventType === "model.thinking.completed");
  const startedEventType = hasNativeThinking ? "model.thinking.started" : "agent.step.started";
  const deltaEventType = hasNativeThinking ? "model.thinking.delta" : "agent.step.delta";
  const completedEventTypes = hasNativeThinking
    ? new Set(hasThinkingCompleted ? ["model.thinking.completed"] : ["model.thinking"])
    : new Set(["agent.step.completed"]);
  const orderedEvents = [...events].reverse();
  const blocks: string[] = [];
  let current = "";
  let started = false;
  let completed = false;

  for (const event of orderedEvents) {
    if (event.eventType === startedEventType) {
      started = true;
      if (current.trim()) {
        blocks.push(current.trim());
        current = "";
      }
      completed = false;
      continue;
    }
    if (event.eventType === deltaEventType) {
      const payload = event.payload as { delta?: string } | undefined;
      current += typeof payload?.delta === "string" ? payload.delta : "";
      continue;
    }
    if (completedEventTypes.has(event.eventType)) {
      const payload = event.payload as Record<string, unknown> | undefined;
      const content = readString(payload?.content) || current;
      if (content.trim()) {
        blocks.push(content.trim());
      }
      current = "";
      completed = true;
    }
  }

  if (current.trim()) {
    blocks.push(current.trim());
  }

  const [collapsed, setCollapsed] = useState(completed);

  useEffect(() => {
    setCollapsed(completed);
  }, [completed, blocks.join("\n\n")]);

  if (!started && blocks.length === 0) return null;

  return (
    <div className={`thinking-shell${completed ? " completed" : ""}`}>
      <button
        type="button"
        className="thinking-toggle"
        onClick={() => setCollapsed((currentState) => !currentState)}
        aria-expanded={!collapsed}
      >
        <span>{completed ? "Thinking" : "Thinking..."}</span>
        <span className="thinking-toggle-hint">{collapsed ? "Expand" : "Collapse"}</span>
      </button>
      {!collapsed ? (
        <div className="thinking-body">
          {blocks.length > 0
            ? blocks.map((block, index) => <MarkdownBlock key={`${index}-${block.length}`} content={block} />)
            : <MarkdownBlock content="*Thinking...*" />}
        </div>
      ) : null}
    </div>
  );
}

export function UnifiedExecutionTrace({ events }: { events: RunEvent[] }) {
  const processEvents = events
    .filter((event) => !HIDDEN_PROCESS_EVENT_TYPES.has(event.eventType))
    .reverse();

  if (processEvents.length === 0) return null;

  const isCompleted = events.some((event) => event.eventType === "run.completed");
  const latestEvent = processEvents[processEvents.length - 1];

  let statusText = "正在处理...";
  if (isCompleted) {
    statusText = "思考与执行过程";
  } else if (latestEvent?.eventType === "tool.started") {
    statusText = `正在调用工具: ${readToolName(latestEvent) || "未知"}...`;
  } else if (latestEvent?.eventType === "tool.completed") {
    statusText = `工具调用完成: ${readToolName(latestEvent) || "未知"}`;
  } else if (latestEvent?.eventType === "tool.failed") {
    statusText = `工具调用失败: ${readToolName(latestEvent) || "未知"}`;
  } else if (latestEvent?.eventType === "research.site.discovered") {
    statusText = "正在搜索资料...";
  } else if (latestEvent?.eventType === "route.selected") {
    statusText = "正在规划路径...";
  }

  return (
    <details className="execution-process-details" open={!isCompleted} style={{
      marginBottom: "16px",
      backgroundColor: "var(--bg-hover)",
      borderRadius: "var(--radius-md)",
      padding: "12px 16px",
      fontSize: "0.85rem",
      color: "var(--ink-muted)",
      border: "1px solid var(--border)"
    }}>
      <summary style={{
        cursor: "pointer",
        fontWeight: 500,
        outline: "none",
        userSelect: "none",
        display: "flex",
        alignItems: "center",
        gap: "8px",
        color: "var(--ink-main)"
      }}>
        {!isCompleted ? <span style={{ color: "var(--accent)", fontWeight: 500 }}>[运行中]</span> : null}
        {statusText}
      </summary>
      <div style={{
        marginTop: "12px",
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        borderTop: "1px solid var(--border)",
        paddingTop: "12px",
        maxHeight: "300px",
        overflowY: "auto"
      }}>
        {processEvents.map((event, index) => (
          <div key={`${event.runId}-${event.timestamp}-${index}`} style={{ display: "flex", gap: "12px", lineHeight: "1.5" }}>
            <span style={{ color: "var(--ink-muted)", flexShrink: 0, fontSize: "0.85em", fontFamily: "var(--font-mono)" }}>{formatTime(event.timestamp)}</span>
            <div style={{ display: "flex", flexDirection: "column" }}>
              <strong style={{ color: "var(--ink-main)", fontWeight: 500 }}>{formatEventType(event.eventType)}</strong>
              {eventSummary(event) ? <span style={{ color: "var(--ink-muted)", fontSize: "0.9em", wordBreak: "break-word" }}>{eventSummary(event)}</span> : null}
            </div>
          </div>
        ))}
      </div>
    </details>
  );
}
