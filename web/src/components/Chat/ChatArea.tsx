import React, { useMemo, useRef, useEffect } from "react";
import { useWorkspace } from "../../context/WorkspaceContext";
import { useChat } from "../../context/ChatContext";
import type { RunEvent } from "../../types";
import {
  formatTaskStage, formatTime, readRouteKind, readExecutionSources, readResearchUpgradeSuggestion
} from "../../utils/formatters";
import { AgentStepTrace, UnifiedExecutionTrace, AssistantMessageBlock, MarkdownBlock } from "./MessageBlocks";
import ResearchDraftEditor from "../../ResearchDraftEditor";
import ResearchTaskPanel from "../../ResearchTaskPanel";

export function ChatArea({
  modelSettingsLoading, setModelSettingsOpen,
  webSearchSettingsLoading, setWebSearchSettingsOpen,
  inspectorCollapsed, setInspectorCollapsed,
  handleWorkspaceUpload, handleUpload
}: {
  modelSettingsLoading: boolean;
  setModelSettingsOpen: (open: boolean) => void;
  webSearchSettingsLoading: boolean;
  setWebSearchSettingsOpen: (open: boolean) => void;
  inspectorCollapsed: boolean;
  setInspectorCollapsed: React.Dispatch<React.SetStateAction<boolean>>;
  handleWorkspaceUpload: (file: File) => Promise<void>;
  handleUpload: (file: File) => Promise<void>;
}) {
  const {
    userId, apiBase, selectedProviderId, setSelectedProviderId,
    selectedThreadId, threads, pageError, busy, ensureThread
  } = useWorkspace();

  const {
    messagesByThread, tasksByThread, artifactsByThread, eventsByThread,
    draftsByThread, threadMemoryByThread, streamingByThread,
    pendingImageArtifactsByThread, setPendingImageArtifactsByThread,
    composerMode, setComposerMode, messageDraft, setMessageDraft,
    threadLoading, uploading, setUploading, reloadThreadData,
    handleStartResearch, handleDiscardDraft, handleSaveDraft,
    handleCancelResearchTask, handleSend,
    researchReportByTask, researchPlanByTask, researchIterationsByTask,
    researchFindingsByTask, researchSourcesByTask, researchCitationsByTask
  } = useChat();

  const endRef = useRef<HTMLDivElement | null>(null);

  const selectedThread = threads.find((thread) => thread.threadId === selectedThreadId) || null;
  const selectedMessages = selectedThreadId ? messagesByThread[selectedThreadId] || [] : [];
  const selectedTasks = selectedThreadId ? tasksByThread[selectedThreadId] || [] : [];
  const selectedArtifacts = selectedThreadId ? artifactsByThread[selectedThreadId] || [] : [];
  const selectedEvents = selectedThreadId ? eventsByThread[selectedThreadId] || [] : [];
  const selectedDraft = selectedThreadId ? draftsByThread[selectedThreadId] || null : null;
  const selectedThreadMemory = selectedThreadId ? threadMemoryByThread[selectedThreadId] || null : null;
  const streamingMessage = selectedThreadId ? streamingByThread[selectedThreadId] || null : null;
  const pendingImageArtifacts = selectedThreadId ? pendingImageArtifactsByThread[selectedThreadId] || [] : [];

  const selectedImageArtifacts = useMemo(
    () => selectedArtifacts.filter((artifact) => artifact.contentType?.toLowerCase().startsWith("image/")),
    [selectedArtifacts]
  );

  const activeResearchTask = useMemo(
    () => selectedTasks.find((task) => task.kind === "RESEARCH" && (task.status === "QUEUED" || task.status === "RUNNING")) || null,
    [selectedTasks]
  );

  const latestResearchTask = useMemo(
    () => [...selectedTasks]
      .filter((task) => task.kind === "RESEARCH")
      .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())[0] || null,
    [selectedTasks]
  );

  const readyToStart = Boolean(selectedDraft && !activeResearchTask && selectedDraft.ready);
  const visibleMessages = useMemo(() => (streamingMessage ? [...selectedMessages, streamingMessage] : selectedMessages), [selectedMessages, streamingMessage]);

  const researchUpgradeSuggestion = useMemo(
    () => selectedEvents.map((event) => readResearchUpgradeSuggestion(event)).find(Boolean) || null,
    [selectedEvents]
  );

  const routeByRun = useMemo(() => {
    const next: Record<string, any> = {};
    for (const event of [...selectedEvents].reverse()) {
      const routeKind = readRouteKind(event);
      if (routeKind) next[event.runId] = routeKind;
    }
    return next;
  }, [selectedEvents]);

  const sourcesByRun = useMemo(() => {
    const next: Record<string, any[]> = {};
    for (const event of [...selectedEvents].reverse()) {
      const sources = readExecutionSources(event).map((s, i) => ({
        id: `source-${i}-${s.url}`,
        kind: s.kind,
        title: s.title,
        domain: s.domain,
        url: s.url
      }));
      if (sources.length > 0) next[event.runId] = sources;
    }
    return next;
  }, [selectedEvents]);

  const currentRouteKind = useMemo(() => {
    const latestRouteEvent = selectedEvents.find((event) => event.eventType === "route.selected" || event.eventType === "run.started");
    const latestRoute = readRouteKind(latestRouteEvent);
    return latestRoute || (latestRouteEvent ? routeByRun[latestRouteEvent.runId] || null : null);
  }, [routeByRun, selectedEvents]);

  const eventsByRun = useMemo(() => {
    const map: Record<string, RunEvent[]> = {};
    for (const event of selectedEvents) {
      if (!map[event.runId]) map[event.runId] = [];
      map[event.runId].push(event);
    }
    return map;
  }, [selectedEvents]);

  const selectedResearchReport = latestResearchTask ? researchReportByTask[latestResearchTask.taskId] || null : null;
  const selectedResearchPlan = latestResearchTask ? researchPlanByTask[latestResearchTask.taskId] || [] : [];
  const selectedResearchIterations = latestResearchTask ? researchIterationsByTask[latestResearchTask.taskId] || [] : [];
  const selectedResearchFindings = latestResearchTask ? researchFindingsByTask[latestResearchTask.taskId] || [] : [];
  const selectedResearchSources = latestResearchTask ? researchSourcesByTask[latestResearchTask.taskId] || [] : [];
  const selectedResearchCitations = latestResearchTask ? researchCitationsByTask[latestResearchTask.taskId] || [] : [];

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [selectedThreadId, visibleMessages.length, streamingMessage?.content]);

  function isToolAssistedRoute(routeKind: any) {
    return routeKind === "TOOL_ASSISTED_CHAT" || routeKind === "REALTIME_LOOKUP";
  }

  function formatComposerContext() {
    if (composerMode === "CHAT") {
      if (isToolAssistedRoute(currentRouteKind)) return "网络辅助对话";
      if (currentRouteKind === "DOCUMENT_QA") return "文档问答";
      return "对话";
    }
    if (activeResearchTask) return "研究运行中";
    if (selectedDraft || readyToStart) return "研究计划中";
    return "深度研究";
  }

  function composerStatusText() {
    if (composerMode === "CHAT") {
      if (isToolAssistedRoute(currentRouteKind)) return "当需要外部证据时，助手可能会自动调用网络工具。";
      if (currentRouteKind === "DOCUMENT_QA") return "助手优先基于文档回答。";
      return "直接对话模式。";
    }
    if (activeResearchTask) return "您的下一条消息将被视为对研究的改进要求。";
    if (readyToStart) return '发送"开始研究"或点击按钮启动任务。';
    if (selectedDraft) return "您正在编辑研究草案。";
    return "深度研究将首先起草一份计划。";
  }

  function composerPlaceholder() {
    if (composerMode === "CHAT") {
      if (isToolAssistedRoute(currentRouteKind)) return "询问可能需要网络查询或验证的内容...";
      if (currentRouteKind === "DOCUMENT_QA") return "询问关于您上传的文档的内容...";
      return "输入消息。支持 Markdown。";
    }
    if (activeResearchTask) return "添加更多研究要求...";
    return "描述研究任务...";
  }

  async function handleAttachImage(file: File) {
    try {
      const threadId = await ensureThread(file.name);
      setUploading(true);
      const formData = new FormData();
      formData.append("file", file);
      const response = await fetch(`${apiBase}/api/v1/users/${userId}/threads/${threadId}/files`, {
        method: "POST",
        body: formData
      });
      if (!response.ok) throw new Error("上传失败");
      const upload = await response.json();
      if (!upload.artifact?.contentType?.toLowerCase().startsWith("image/")) {
        throw new Error("只能附加图片文件。");
      }
      setPendingImageArtifactsByThread((current) => {
        const existing = current[threadId] || [];
        const next = [...existing.filter((artifact) => artifact.artifactId !== upload.artifact.artifactId), upload.artifact];
        return { ...current, [threadId]: next };
      });
      await reloadThreadData(threadId);
    } catch (error) {
      console.error(error);
    } finally {
      setUploading(false);
    }
  }

  function handleApplyResearchSuggestion() {
    if (!researchUpgradeSuggestion) return;
    setComposerMode("DEEP_RESEARCH");
    setMessageDraft(researchUpgradeSuggestion.suggestedBrief);
  }

  return (
    <main className="conversation-shell">
      <header className="conversation-header">
        <div>
          <span className="conversation-kicker">当前会话</span>
          <h1>{selectedThread?.title || "新会话"}</h1>
          <p>{selectedThread ? selectedThreadMemory?.summary || "在这里继续对话或切换到深度研究。" : "创建一个会话并开始提问。"}</p>
        </div>
        <div className="header-actions">
          <select value={selectedProviderId} onChange={(event) => setSelectedProviderId(event.target.value)}>
            <option value="gemini">Gemini</option>
            <option value="openai">OpenAI</option>
            <option value="deepseek">DeepSeek</option>
          </select>
          <button className="ghost-button" onClick={() => setModelSettingsOpen(true)}>
            {modelSettingsLoading ? "加载中..." : "模型"}
          </button>
          <button className="ghost-button" onClick={() => setWebSearchSettingsOpen(true)}>
            {webSearchSettingsLoading ? "加载中..." : "搜索"}
          </button>
          <button className="ghost-button" onClick={() => setInspectorCollapsed((current) => !current)}>
            {inspectorCollapsed ? "展开面板" : "收起面板"}
          </button>
          <label className="upload-button workspace-upload-btn" title="上传至工作区知识库">
            <input
              type="file"
              style={{ display: "none" }}
              disabled={uploading}
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) void handleWorkspaceUpload(file);
                event.currentTarget.value = "";
              }}
            />
            {uploading ? "上传中..." : "上传至工作区"}
          </label>
          <label className="upload-button" title="上传至当前会话">
            <input
              type="file"
              style={{ display: "none" }}
              disabled={uploading}
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) void handleUpload(file);
                event.currentTarget.value = "";
              }}
            />
            {uploading ? "上传中..." : "上传至会话"}
          </label>
        </div>
      </header>

      {pageError ? <div className="page-error">{pageError}</div> : null}
      
      {selectedDraft ? (
        <ResearchDraftEditor
          draft={selectedDraft}
          busy={busy}
          onSave={handleSaveDraft}
          onStart={handleStartResearch}
          onDiscard={handleDiscardDraft}
        />
      ) : null}
      
      {activeResearchTask ? (
        <section className="inline-banner secondary">
          <div>
            <span className="mini-badge running">研究任务运行中</span>
            <strong>{activeResearchTask.title}</strong>
            <p>{activeResearchTask.summary}</p>
          </div>
          <div className="inline-actions">
            <span className="progress-text">{formatTaskStage(activeResearchTask.stage)} · {activeResearchTask.progress ?? 0}%</span>
            <button className="ghost-button" disabled={busy} onClick={() => void handleCancelResearchTask()}>取消任务</button>
          </div>
        </section>
      ) : null}
      
      {!selectedDraft && !activeResearchTask && researchUpgradeSuggestion ? (
        <section className="inline-banner secondary">
          <div>
            <span className="mini-badge pending">建议深度研究</span>
            <strong>{researchUpgradeSuggestion.suggestedTitle || "此请求可能更适合较长的研究流程。"}</strong>
            <p>{researchUpgradeSuggestion.reason}</p>
          </div>
          <div className="inline-actions">
            <button className="primary-button" disabled={busy} onClick={handleApplyResearchSuggestion}>切换到深度研究</button>
          </div>
        </section>
      ) : null}
      
      {latestResearchTask ? (
        <ResearchTaskPanel
          task={latestResearchTask}
          plan={selectedResearchPlan}
          iterations={selectedResearchIterations}
          findings={selectedResearchFindings}
          report={selectedResearchReport}
          sources={selectedResearchSources}
          citations={selectedResearchCitations}
        />
      ) : null}
      
      <section className="message-stage">
        {!selectedThread && !threadLoading ? (
          <div className="hero-empty">
            <h2>有什么可以帮忙的？</h2>
          </div>
        ) : null}
        {selectedThread && visibleMessages.length === 0 && !threadLoading ? <div className="thread-empty">此会话暂无消息。</div> : null}
        {threadLoading ? <div className="thread-empty">正在加载会话内容...</div> : null}
        <div className="message-list">
          {visibleMessages.map((message) => {
            const normalizedRole = message.role.toUpperCase();
            return (
            <article key={"messageId" in message ? message.messageId : message.id} className={`message-row role-${normalizedRole.toLowerCase()}`}>
              <div className="message-avatar">{normalizedRole === "USER" ? "You" : normalizedRole === "ASSISTANT" ? "AI" : "SYS"}</div>
              <div className={`message-bubble ${"status" in message && message.status === "streaming" ? "streaming" : ""}`}>
                <div className="message-meta">
                  <strong>{normalizedRole === "USER" ? "You" : normalizedRole === "ASSISTANT" ? "MyAgent" : "System"}</strong>
                  <span>{formatTime("createdAt" in message ? message.createdAt : message.timestamp)}</span>
                </div>
                {normalizedRole === "ASSISTANT" ? (
                  <>
                    {"runId" in message && message.runId && eventsByRun[message.runId] && (
                      <>
                        <AgentStepTrace events={eventsByRun[message.runId]} />
                        <UnifiedExecutionTrace events={eventsByRun[message.runId]} />
                      </>
                    )}
                    <AssistantMessageBlock
                      content={message.content}
                      pending={"status" in message && message.status === "streaming"}
                      sources={("runId" in message && message.runId ? sourcesByRun[message.runId] || [] : [])}
                    />
                  </>
                ) : (
                  <>
                    <MarkdownBlock content={message.content} />
                    {message.imageArtifactIds && message.imageArtifactIds.length > 0 && (
                      <div className="message-image-gallery" style={{ display: "flex", gap: "8px", marginTop: "12px", flexWrap: "wrap" }}>
                        {message.imageArtifactIds.map((artifactId) => (
                          <img
                            key={artifactId}
                            src={`${apiBase || ""}/api/v1/users/${userId}/threads/${selectedThreadId}/artifacts/${artifactId}/content`}
                            alt="Attached artifact"
                            style={{ maxWidth: "200px", maxHeight: "200px", borderRadius: "var(--radius-sm)", objectFit: "cover", border: "1px solid var(--border)" }}
                          />
                        ))}
                      </div>
                    )}
                  </>
                )}
              </div>
            </article>
          )})}
          <div ref={endRef} />
        </div>
      </section>

      <section className="composer-shell">
        <div className="composer-toolbar">
          <div className="mode-switch">
            <button className={composerMode === "CHAT" ? "active" : ""} onClick={() => setComposerMode("CHAT")}>对话</button>
            <button className={composerMode === "DEEP_RESEARCH" ? "active" : ""} onClick={() => setComposerMode("DEEP_RESEARCH")}>深度研究</button>
          </div>
          <span className="composer-status">{formatComposerContext()} · {composerStatusText()}</span>
        </div>
        {composerMode === "CHAT" ? (
          <div className="inline-actions">
            <label className="ghost-button upload-button">
              <input
                type="file"
                accept="image/*"
                style={{ display: "none" }}
                disabled={busy || uploading}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void handleAttachImage(file);
                  event.currentTarget.value = "";
                }}
              />
              {uploading ? "上传中..." : "附加图片"}
            </label>
            <span className="composer-status">当前会话有 {selectedImageArtifacts.length} 张图片</span>
          </div>
        ) : null}
        {pendingImageArtifacts.length > 0 ? (
          <div className="composer-thumbnails" style={{ display: "flex", gap: "12px", marginBottom: "12px", overflowX: "auto", paddingBottom: "8px" }}>
            {pendingImageArtifacts.map((artifact) => (
              <div key={artifact.artifactId} style={{ position: "relative", flexShrink: 0 }}>
                <img
                  src={`${apiBase || ""}/api/v1/users/${userId}/threads/${selectedThreadId}/artifacts/${artifact.artifactId}/content`}
                  alt={artifact.name}
                  style={{ width: "64px", height: "64px", objectFit: "cover", borderRadius: "var(--radius-sm)", border: "1px solid var(--border)" }}
                />
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setPendingImageArtifactsByThread((current) => ({
                    ...current,
                    [selectedThreadId || ""]: (current[selectedThreadId || ""] || []).filter((item) => item.artifactId !== artifact.artifactId)
                  }))}
                  style={{
                    position: "absolute", top: "-6px", right: "-6px", background: "var(--ink-main)", color: "white",
                    width: "20px", height: "20px", borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center",
                    fontSize: "12px", padding: 0, border: "none", cursor: "pointer", boxShadow: "var(--shadow-sm)"
                  }}
                  title="移除图片"
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        ) : null}
        <div className="composer-box">
          <textarea
            value={messageDraft}
            onChange={(event) => setMessageDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                if (!busy) void handleSend();
              }
            }}
            placeholder={composerPlaceholder()}
            disabled={busy}
          />
          <button
            className="send-button"
            disabled={busy || (!messageDraft.trim() && pendingImageArtifacts.length === 0 && !(composerMode === "DEEP_RESEARCH" && readyToStart))}
            onClick={() => void handleSend()}
            title={busy ? "发送中..." : composerMode === "CHAT" ? "发送" : activeResearchTask ? "更新研究" : composerMode === "DEEP_RESEARCH" && readyToStart ? "开始研究" : "发送"}
          >
            {busy ? (
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className="animate-spin">
                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeDasharray="16 16" strokeLinecap="round" />
              </svg>
            ) : (
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 4L12 20M12 4L6 10M12 4L18 10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            )}
          </button>
        </div>
      </section>
    </main>
  );
}
