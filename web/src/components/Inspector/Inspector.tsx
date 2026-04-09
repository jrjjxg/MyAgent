import React, { useMemo } from "react";
import { useWorkspace } from "../../context/WorkspaceContext";
import { useChat } from "../../context/ChatContext";
import {
  formatTaskStage, formatEventType, eventSummary, formatTime,
  readPlanSummary, readResearchSite, HIDDEN_RECENT_EVENT_TYPES
} from "../../utils/formatters";
import { MarkdownBlock } from "../Chat/MessageBlocks";

export function Inspector() {
  const { selectedThreadId, threads, profileMemory, stableFacts } = useWorkspace();
  const {
    messagesByThread, tasksByThread, documentsByThread, eventsByThread,
    draftsByThread, threadMemoryByThread, streamingByThread
  } = useChat();

  const selectedThread = threads.find((thread) => thread.threadId === selectedThreadId) || null;
  const selectedMessages = selectedThreadId ? messagesByThread[selectedThreadId] || [] : [];
  const selectedTasks = selectedThreadId ? tasksByThread[selectedThreadId] || [] : [];
  const selectedDocuments = selectedThreadId ? documentsByThread[selectedThreadId] || [] : [];
  const selectedEvents = selectedThreadId ? eventsByThread[selectedThreadId] || [] : [];
  const selectedDraft = selectedThreadId ? draftsByThread[selectedThreadId] || null : null;
  const selectedThreadMemory = selectedThreadId ? threadMemoryByThread[selectedThreadId] || null : null;
  const streamingMessage = selectedThreadId ? streamingByThread[selectedThreadId] || null : null;

  const visibleMessages = useMemo(() => (streamingMessage ? [...selectedMessages, streamingMessage] : selectedMessages), [selectedMessages, streamingMessage]);

  const activeResearchTask = useMemo(
    () => selectedTasks.find((task) => task.kind === "RESEARCH" && (task.status === "QUEUED" || task.status === "RUNNING")) || null,
    [selectedTasks]
  );

  const approvedPlan = useMemo(() => selectedEvents.map((event) => readPlanSummary(event)).find(Boolean) || null, [selectedEvents]);
  const researchSites = useMemo(() => selectedEvents.map((event) => readResearchSite(event)).filter(Boolean).slice(0, 8) as any[], [selectedEvents]);
  const recentEvents = useMemo(
    () => selectedEvents.filter((event) => !HIDDEN_RECENT_EVENT_TYPES.has(event.eventType)).slice(0, 16),
    [selectedEvents]
  );

  return (
    <aside className="inspector-shell">
      <section className="inspector-card">
        <div className="section-title-row">
          <strong>概览</strong>
          <span>{selectedThread ? "活跃" : "等待中"}</span>
        </div>
        <div className="inspector-list">
          <div><span>消息</span><strong>{visibleMessages.length}</strong></div>
          <div><span>文档</span><strong>{selectedDocuments.length}</strong></div>
          <div><span>任务</span><strong>{activeResearchTask ? formatTaskStage(activeResearchTask.stage) : "无"}</strong></div>
        </div>
        {selectedThreadMemory?.summary ? (
          <div className="summary-box">
            <span>会话摘要</span>
            <p>{selectedThreadMemory.summary}</p>
          </div>
        ) : null}
      </section>

      <section className="inspector-card">
        <div className="section-title-row">
          <strong>计划</strong>
          <span>{selectedDraft ? `v${selectedDraft.revision}` : approvedPlan ? "已批准" : "空"}</span>
        </div>
        {selectedDraft ? (
          <div className="stack-list">
            <h3>{selectedDraft.title}</h3>
            <MarkdownBlock content={selectedDraft.brief} />
            {selectedDraft.planSteps.length > 0 ? (
              <ol className="plan-list">
                {selectedDraft.planSteps.map((step) => (
                  <li key={step.stepId}>
                    <strong>{step.title}</strong>
                    <span>{step.outputFocus}</span>
                  </li>
                ))}
              </ol>
            ) : null}
          </div>
        ) : approvedPlan ? (
          <div className="stack-list">
            <h3>{approvedPlan.title}</h3>
            <p>{approvedPlan.summary || "研究计划已批准。"}</p>
            {approvedPlan.steps.length > 0 ? (
              <ol className="plan-list">
                {approvedPlan.steps.map((step) => (
                  <li key={step.stepId}>
                    <strong>{step.title}</strong>
                    <span>{step.outputFocus}</span>
                  </li>
                ))}
              </ol>
            ) : null}
          </div>
        ) : (
          <div className="empty-card">暂无研究计划。</div>
        )}
      </section>

      <section className="inspector-card">
        <div className="section-title-row">
          <strong>来源站点</strong>
          <span>{researchSites.length}</span>
        </div>
        {researchSites.length === 0 ? (
          <div className="empty-card">发现的研究站点将显示在这里。</div>
        ) : (
          <ul className="site-list">
            {researchSites.map((site) => (
              <li key={`${site.sourceType}-${site.url}`}>
                <a href={site.url} target="_blank" rel="noreferrer">{site.title}</a>
                <span>{site.domain || site.url}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="inspector-card">
        <div className="section-title-row">
          <strong>事件</strong>
          <span>{recentEvents.length}</span>
        </div>
        {recentEvents.length === 0 ? (
          <div className="empty-card">运行事件将显示在这里。</div>
        ) : (
          <ul className="event-list">
            {recentEvents.map((event) => (
              <li key={`${event.runId}-${event.timestamp}-${event.eventType}`}>
                <strong>{formatEventType(event.eventType)}</strong>
                <span>{eventSummary(event) || formatTime(event.timestamp)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="inspector-card">
        <div className="section-title-row">
          <strong>文档</strong>
          <span>{selectedDocuments.length}</span>
        </div>
        {selectedDocuments.length === 0 ? (
          <div className="empty-card">此会话暂无上传的文档。</div>
        ) : (
          <div className="stack-list">
            {selectedDocuments.map((document) => (
              <div key={document.documentId} className="resource-row">
                <strong>{document.name}</strong>
                <span>{document.status}</span>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="inspector-card">
        <div className="section-title-row">
          <strong>Profile Memory</strong>
          <span>{profileMemory ? "1" : "0"}</span>
        </div>
        {profileMemory ? (
          <div className="summary-box">
            <p>
              {profileMemory.notes
                || profileMemory.displayName
                || profileMemory.preferredLanguage
                || (profileMemory.preferredOutputStyles.length > 0 ? profileMemory.preferredOutputStyles.join(" · ") : "")
                || (profileMemory.projectTags.length > 0 ? profileMemory.projectTags.join(" · ") : "")
                || "已保存用户画像信息。"}
            </p>
          </div>
        ) : (
          <div className="empty-card">暂无 Profile Memory。</div>
        )}
      </section>

      <section className="inspector-card">
        <div className="section-title-row">
          <strong>Stable Facts</strong>
          <span>{stableFacts.length}</span>
        </div>
        {stableFacts.length === 0 ? (
          <div className="empty-card">暂无 Stable Facts。</div>
        ) : (
          <ul className="event-list">
            {stableFacts.map((fact) => (
              <li key={fact.memoryId}>
                <strong>{fact.title || fact.content || fact.factType || fact.memoryId}</strong>
                <span>{fact.factType || fact.status}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </aside>
  );
}
