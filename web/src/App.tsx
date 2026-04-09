import React, { useState, useEffect } from "react";
import { WorkspaceProvider, useWorkspace } from "./context/WorkspaceContext";
import { ChatProvider } from "./context/ChatContext";
import { Sidebar } from "./components/Sidebar/Sidebar";
import { ChatArea } from "./components/Chat/ChatArea";
import { Inspector } from "./components/Inspector/Inspector";
import ModelSettingsPanel from "./ModelSettingsPanel";
import SkillSettingsPanel from "./SkillSettingsPanel";
import WebSearchSettingsPanel from "./WebSearchSettingsPanel";
import {
  listModelProviderStatuses, listSkillStatuses, getWebSearchSettings,
  updateModelProviderConfig, updateSkillConfig, updateWebSearchSettings,
  createWorkspace, uploadFileToWorkspace, uploadFile
} from "./api";
import type { ModelProviderStatusResponse, SkillStatusResponse, WebSearchSettingsResponse } from "./types";

function AppContent() {
  const {
    userId, apiBase, authed, loginForm, setLoginForm, pageError, setPageError,
    handleLogin, selectedWorkspaceId, setWorkspaces, setSelectedWorkspaceId,
    selectedThreadId, ensureThread
  } = useWorkspace();

  const [modelProviderResponse, setModelProviderResponse] = useState<ModelProviderStatusResponse | null>(null);
  const [skillStatusResponse, setSkillStatusResponse] = useState<SkillStatusResponse | null>(null);
  const [webSearchSettingsResponse, setWebSearchSettingsResponse] = useState<WebSearchSettingsResponse | null>(null);

  const [skillsLoading, setSkillsLoading] = useState(false);
  const [modelSettingsLoading, setModelSettingsLoading] = useState(false);
  const [webSearchSettingsLoading, setWebSearchSettingsLoading] = useState(false);

  const [skillsPanelOpen, setSkillsPanelOpen] = useState(false);
  const [modelSettingsOpen, setModelSettingsOpen] = useState(false);
  const [webSearchSettingsOpen, setWebSearchSettingsOpen] = useState(false);
  const [inspectorCollapsed, setInspectorCollapsed] = useState(false);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    if (authed) void reloadSkillStatuses();
  }, [authed, apiBase, userId]);

  useEffect(() => {
    if (authed) void reloadModelProviderStatuses();
  }, [authed, apiBase, userId]);

  useEffect(() => {
    if (authed) void reloadWebSearchSettings();
  }, [authed, apiBase, userId]);

  async function reloadSkillStatuses() {
    if (!authed) return;
    try {
      setSkillsLoading(true);
      const response = await listSkillStatuses(apiBase, userId);
      setSkillStatusResponse(response);
      setPageError(null);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setSkillsLoading(false);
    }
  }

  async function reloadModelProviderStatuses() {
    if (!authed) return;
    try {
      setModelSettingsLoading(true);
      const response = await listModelProviderStatuses(apiBase, userId);
      setModelProviderResponse(response);
      setPageError(null);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setModelSettingsLoading(false);
    }
  }

  async function reloadWebSearchSettings() {
    if (!authed) return;
    try {
      setWebSearchSettingsLoading(true);
      const response = await getWebSearchSettings(apiBase, userId);
      setWebSearchSettingsResponse(response);
      setPageError(null);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setWebSearchSettingsLoading(false);
    }
  }

  async function handleSaveSkillConfig(skillId: string, request: { enabled?: boolean; apiKey?: string; env?: Record<string, string> }) {
    await updateSkillConfig(apiBase, userId, skillId, request);
    await reloadSkillStatuses();
  }

  async function handleSaveModelProviderConfig(
    providerId: string,
    request: { enabled?: boolean; apiKey?: string; model?: string; baseUrl?: string }
  ) {
    await updateModelProviderConfig(apiBase, userId, providerId, request);
    await reloadModelProviderStatuses();
  }

  async function handleSaveWebSearchSettings(request: { provider?: string; tavilyApiKey?: string; searchApiBaseUrl?: string }) {
    await updateWebSearchSettings(apiBase, userId, request);
    await reloadWebSearchSettings();
  }

  async function handleWorkspaceUpload(file: File) {
    try {
      let workspaceId = selectedWorkspaceId;
      if (!workspaceId) {
        const createdWorkspace = await createWorkspace(apiBase, userId, file.name.slice(0, 24) || "New workspace");
        workspaceId = createdWorkspace.workspaceId;
        setWorkspaces((current) => [createdWorkspace, ...current]);
        setSelectedWorkspaceId(workspaceId);
      }
      setUploading(true);
      await uploadFileToWorkspace(apiBase, userId, workspaceId, file);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setUploading(false);
    }
  }

  async function handleUpload(file: File) {
    try {
      const threadId = await ensureThread(file.name);
      setUploading(true);
      await uploadFile(apiBase, userId, threadId, file);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setUploading(false);
    }
  }

  if (!authed) {
    return (
      <div className="login-shell">
        <section className="login-hero">
          <span className="login-badge">MyAgent</span>
          <h1>自然对话，深度研究。</h1>
          <p>左侧管理会话，中间进行对话，右侧实时监控任务、来源和事件。</p>
        </section>
        <section className="login-panel">
          <h2>登录</h2>
          <label className="field">
            <span>显示名称</span>
            <input value={loginForm.displayName} onChange={(event) => setLoginForm((current) => ({ ...current, displayName: event.target.value }))} placeholder="例如：Alex" />
          </label>
          <label className="field">
            <span>用户 ID</span>
            <input value={loginForm.userId} onChange={(event) => setLoginForm((current) => ({ ...current, userId: event.target.value }))} placeholder="例如：dev-user" />
          </label>
          <label className="field">
            <span>API 地址</span>
            <input value={loginForm.apiBase} onChange={(event) => setLoginForm((current) => ({ ...current, apiBase: event.target.value }))} placeholder="留空以使用相同源" />
          </label>
          {pageError ? <div className="page-error">{pageError}</div> : null}
          <button className="primary-button large-button" onClick={handleLogin}>进入工作区</button>
        </section>
      </div>
    );
  }

  return (
    <ChatProvider>
      <div className={`workspace-shell ${inspectorCollapsed ? "inspector-collapsed" : ""}`}>
        <Sidebar
          skillsLoading={skillsLoading}
          setSkillsPanelOpen={setSkillsPanelOpen}
          handleLogout={() => {}}
        />

        <ChatArea
          modelSettingsLoading={modelSettingsLoading}
          setModelSettingsOpen={setModelSettingsOpen}
          webSearchSettingsLoading={webSearchSettingsLoading}
          setWebSearchSettingsOpen={setWebSearchSettingsOpen}
          inspectorCollapsed={inspectorCollapsed}
          setInspectorCollapsed={setInspectorCollapsed}
          handleWorkspaceUpload={handleWorkspaceUpload}
          handleUpload={handleUpload}
        />

        <Inspector />

        <SkillSettingsPanel
          open={skillsPanelOpen}
          loading={skillsLoading}
          response={skillStatusResponse}
          onClose={() => setSkillsPanelOpen(false)}
          onRefresh={() => void reloadSkillStatuses()}
          onSave={handleSaveSkillConfig}
        />

        <ModelSettingsPanel
          open={modelSettingsOpen}
          loading={modelSettingsLoading}
          response={modelProviderResponse}
          onClose={() => setModelSettingsOpen(false)}
          onRefresh={() => void reloadModelProviderStatuses()}
          onSave={handleSaveModelProviderConfig}
        />

        <WebSearchSettingsPanel
          open={webSearchSettingsOpen}
          loading={webSearchSettingsLoading}
          response={webSearchSettingsResponse}
          onClose={() => setWebSearchSettingsOpen(false)}
          onRefresh={() => void reloadWebSearchSettings()}
          onSave={handleSaveWebSearchSettings}
        />
      </div>
    </ChatProvider>
  );
}

export default function App() {
  return (
    <WorkspaceProvider>
      <AppContent />
    </WorkspaceProvider>
  );
}
