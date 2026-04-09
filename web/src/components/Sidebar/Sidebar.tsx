import React, { useState } from "react";
import { useWorkspace } from "../../context/WorkspaceContext";
import { useChat } from "../../context/ChatContext";

export function Sidebar({
  skillsLoading, setSkillsPanelOpen, handleLogout
}: {
  skillsLoading: boolean;
  setSkillsPanelOpen: (open: boolean) => void;
  handleLogout: () => void;
}) {
  const {
    displayName, userId, workspaces, threads, selectedWorkspaceId, setSelectedWorkspaceId,
    selectedThreadId, setSelectedThreadId, busy, handleCreateThread, handleCreateThreadInWorkspace
  } = useWorkspace();
  const { messagesByThread } = useChat();
  const [threadSearch, setThreadSearch] = useState("");
  const [collapsedWorkspaces, setCollapsedWorkspaces] = useState<Record<string, boolean>>({});

  const selectedWorkspace = workspaces.find((w) => w.workspaceId === selectedWorkspaceId) || null;

  const filteredWorkspaceGroups = workspaces
    .map((workspace) => ({
      workspace,
      threads: threads.filter((thread) => thread.workspaceId === workspace.workspaceId)
    }))
    .map((group) => {
      const keyword = threadSearch.trim().toLowerCase();
      if (!keyword) return group;
      const workspaceMatched = group.workspace.title.toLowerCase().includes(keyword);
      if (workspaceMatched) return group;
      return {
        ...group,
        threads: group.threads.filter((thread) => {
          const preview = messagesByThread[thread.threadId]?.at(-1)?.content || "";
          return `${thread.title} ${preview}`.toLowerCase().includes(keyword);
        })
      };
    })
    .filter((group) => {
      const keyword = threadSearch.trim().toLowerCase();
      if (!keyword) return true;
      return group.workspace.title.toLowerCase().includes(keyword) || group.threads.length > 0;
    });

  function handleSelectWorkspace(workspaceId: string) {
    setSelectedWorkspaceId(workspaceId);
    const nextSelectedThread = selectedThreadId
      ? threads.find((thread) => thread.threadId === selectedThreadId && thread.workspaceId === workspaceId) || null
      : null;
    const firstThread = threads.find((thread) => thread.workspaceId === workspaceId) || null;
    setSelectedThreadId(nextSelectedThread?.threadId || firstThread?.threadId || null);
  }

  function handleSelectThread(threadId: string) {
    const thread = threads.find((item) => item.threadId === threadId) || null;
    setSelectedThreadId(threadId);
    if (thread) setSelectedWorkspaceId(thread.workspaceId);
  }

  function truncate(value: string, maxLength = 80) {
    const normalized = value.replace(/\s+/g, " ").trim();
    return normalized.length > maxLength ? `${normalized.slice(0, maxLength)}...` : normalized;
  }

  return (
    <aside className="sidebar-shell">
      <div className="sidebar-brand">
        <div className="brand-mark">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M2 17L12 22L22 17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>
        <div>
          <strong>MyAgent</strong>
          <span>Workspace</span>
        </div>
        <button className="ghost-button" style={{ marginLeft: 'auto', padding: '6px' }} disabled={busy} onClick={() => void handleCreateThread()} title="New workspace">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 5V19M5 12H19" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>
      </div>
      <div style={{ padding: '0 16px 12px' }}>
        <input 
          value={threadSearch} 
          onChange={(event) => setThreadSearch(event.target.value)} 
          placeholder="搜索会话..." 
          style={{ width: '100%', padding: '8px 12px', fontSize: '0.85rem' }}
        />
      </div>
      <div className="thread-list">
        {filteredWorkspaceGroups.length === 0 ? <div className="empty-card">No workspaces yet.</div> : null}
        {filteredWorkspaceGroups.map(({ workspace, threads: workspaceThreads }) => {
          const isWorkspaceActive = selectedWorkspace?.workspaceId === workspace.workspaceId;
          const isCollapsed = collapsedWorkspaces[workspace.workspaceId];
          return (
            <section key={workspace.workspaceId} className={`workspace-group ${isWorkspaceActive ? "active" : ""}`}>
              <div
                className={`workspace-header ${isWorkspaceActive ? "active" : ""}`}
                onClick={() => handleSelectWorkspace(workspace.workspaceId)}
              >
                <button
                  className="workspace-toggle"
                  onClick={(e) => {
                    e.stopPropagation();
                    setCollapsedWorkspaces((prev) => ({ ...prev, [workspace.workspaceId]: !prev[workspace.workspaceId] }));
                  }}
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className={`chevron ${isCollapsed ? "collapsed" : ""}`}>
                    <path d="M6 9L12 15L18 9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </button>
                <span className="workspace-title">{workspace.title}</span>
                <span className="workspace-count">{workspaceThreads.length}</span>
                <button
                  className="workspace-add-btn"
                  disabled={busy}
                  onClick={(e) => {
                    e.stopPropagation();
                    void handleCreateThreadInWorkspace(workspace.workspaceId);
                  }}
                  title="在此工作区新建会话"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M12 5V19M5 12H19" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </button>
              </div>
              {!isCollapsed && (
                <div className="workspace-thread-list">
                  {workspaceThreads.length === 0 ? <div className="workspace-empty">暂无会话。</div> : null}
                  {workspaceThreads.map((thread) => {
                    const preview = messagesByThread[thread.threadId]?.at(-1)?.content || "";
                    return (
                      <button
                        key={thread.threadId}
                        type="button"
                        className={`thread-item ${selectedThreadId === thread.threadId ? "active" : ""}`}
                        onClick={() => handleSelectThread(thread.threadId)}
                      >
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className="thread-icon">
                          <path d="M21 11.5C21 16.1944 16.9706 20 12 20C10.6606 20 9.38883 19.7289 8.25 19.2426L3 21L4.5 16.5C3.5413 15.0882 3 13.3632 3 11.5C3 6.80558 7.02944 3 12 3C16.9706 3 21 6.80558 21 11.5Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                        <div className="thread-item-content">
                          <strong>{thread.title}</strong>
                          <p>{preview ? truncate(preview, 40) : "新会话"}</p>
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </section>
          );
        })}
      </div>
      <div className="sidebar-user">
        <div>
          <strong>{displayName || userId}</strong>
          <span>{userId}</span>
        </div>
        <div className="sidebar-user-actions">
          <button
            className="ghost-button"
            onClick={() => setSkillsPanelOpen(true)}
          >
            {skillsLoading ? "加载中..." : "技能"}
          </button>
          <button className="ghost-button" onClick={handleLogout}>退出</button>
        </div>
      </div>
    </aside>
  );
}
