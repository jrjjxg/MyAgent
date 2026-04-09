import React, { createContext, useContext, useState, useEffect, ReactNode } from "react";
import { WorkspaceRecord, ThreadRecord, UserProfileMemoryRecord, StableFactMemoryRecord } from "../types";
import { listWorkspaces, listThreads, createWorkspace, createThreadInWorkspace, getProfileMemory, listStableFacts } from "../api";

const USER_KEY = "myagent.userId";
const API_KEY = "myagent.apiBase";
const DISPLAY_NAME_KEY = "myagent.displayName";
const PROVIDER_KEY = "myagent.providerId";

interface WorkspaceContextType {
  userId: string;
  setUserId: (id: string) => void;
  apiBase: string;
  setApiBase: (base: string) => void;
  displayName: string;
  setDisplayName: (name: string) => void;
  selectedProviderId: string;
  setSelectedProviderId: (id: string) => void;
  loginForm: { displayName: string; userId: string; apiBase: string };
  setLoginForm: React.Dispatch<React.SetStateAction<{ displayName: string; userId: string; apiBase: string }>>;
  workspaces: WorkspaceRecord[];
  setWorkspaces: React.Dispatch<React.SetStateAction<WorkspaceRecord[]>>;
  threads: ThreadRecord[];
  setThreads: React.Dispatch<React.SetStateAction<ThreadRecord[]>>;
  selectedWorkspaceId: string | null;
  setSelectedWorkspaceId: (id: string | null) => void;
  selectedThreadId: string | null;
  setSelectedThreadId: (id: string | null) => void;
  authed: boolean;
  pageError: string | null;
  setPageError: (error: string | null) => void;
  busy: boolean;
  setBusy: (busy: boolean) => void;
  profileMemory: UserProfileMemoryRecord | null;
  stableFacts: StableFactMemoryRecord[];
  reloadSidebar: (preferredWorkspaceId?: string | null, preferredThreadId?: string | null) => Promise<void>;
  handleLogin: () => void;
  handleLogout: () => void;
  handleCreateThread: () => Promise<void>;
  handleCreateThreadInWorkspace: (workspaceId?: string | null) => Promise<void>;
  ensureThread: (seed: string) => Promise<string>;
}

const WorkspaceContext = createContext<WorkspaceContextType | undefined>(undefined);

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const [displayName, setDisplayName] = useState(() => localStorage.getItem(DISPLAY_NAME_KEY) || "");
  const [userId, setUserId] = useState(() => localStorage.getItem(USER_KEY) || "");
  const [apiBase, setApiBase] = useState(() => localStorage.getItem(API_KEY) || "");
  const [selectedProviderId, setSelectedProviderId] = useState(() => localStorage.getItem(PROVIDER_KEY) || "gemini");
  const [loginForm, setLoginForm] = useState({
    displayName: localStorage.getItem(DISPLAY_NAME_KEY) || "",
    userId: localStorage.getItem(USER_KEY) || "",
    apiBase: localStorage.getItem(API_KEY) || ""
  });
  const [workspaces, setWorkspaces] = useState<WorkspaceRecord[]>([]);
  const [threads, setThreads] = useState<ThreadRecord[]>([]);
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | null>(null);
  const [selectedThreadId, setSelectedThreadId] = useState<string | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [profileMemory, setProfileMemory] = useState<UserProfileMemoryRecord | null>(null);
  const [stableFacts, setStableFacts] = useState<StableFactMemoryRecord[]>([]);

  const authed = Boolean(userId.trim());

  useEffect(() => {
    if (!authed) return;
    localStorage.setItem(USER_KEY, userId);
    localStorage.setItem(API_KEY, apiBase);
    localStorage.setItem(DISPLAY_NAME_KEY, displayName || userId);
  }, [apiBase, authed, displayName, userId]);

  useEffect(() => {
    localStorage.setItem(PROVIDER_KEY, selectedProviderId);
  }, [selectedProviderId]);

  async function reloadSidebar(preferredWorkspaceId?: string | null, preferredThreadId?: string | null) {
    try {
      const [nextWorkspaces, nextThreads, nextProfileMemory, nextStableFacts] = await Promise.all([
        listWorkspaces(apiBase, userId),
        listThreads(apiBase, userId),
        getProfileMemory(apiBase, userId),
        listStableFacts(apiBase, userId)
      ]);
      setWorkspaces(nextWorkspaces);
      setThreads(nextThreads);
      setProfileMemory(nextProfileMemory);
      setStableFacts(nextStableFacts);
      const nextSelectedThreadId = preferredThreadId !== undefined
        ? preferredThreadId
        : selectedThreadId && nextThreads.some((thread) => thread.threadId === selectedThreadId)
          ? selectedThreadId
          : nextThreads.find((thread) => thread.workspaceId === selectedWorkspaceId)?.threadId || nextThreads[0]?.threadId || null;
      const threadWorkspaceId = nextSelectedThreadId
        ? nextThreads.find((thread) => thread.threadId === nextSelectedThreadId)?.workspaceId || null
        : null;
      const nextSelectedWorkspaceId = preferredWorkspaceId !== undefined
        ? preferredWorkspaceId
        : threadWorkspaceId
          || (selectedWorkspaceId && nextWorkspaces.some((workspace) => workspace.workspaceId === selectedWorkspaceId) ? selectedWorkspaceId : null)
          || nextWorkspaces[0]?.workspaceId
          || null;
      setSelectedWorkspaceId(nextSelectedWorkspaceId);
      setSelectedThreadId(nextSelectedThreadId);
      setPageError(null);
    } catch (error) {
      setPageError((error as Error).message);
    }
  }

  useEffect(() => {
    if (authed) void reloadSidebar();
  }, [authed, apiBase, userId]);

  function handleLogin() {
    if (!loginForm.userId.trim()) {
      setPageError("需要输入用户 ID。");
      return;
    }
    setDisplayName(loginForm.displayName.trim() || loginForm.userId.trim());
    setUserId(loginForm.userId.trim());
    setApiBase(loginForm.apiBase.trim());
    setSelectedWorkspaceId(null);
    setSelectedThreadId(null);
    setWorkspaces([]);
    setThreads([]);
    setPageError(null);
  }

  function handleLogout() {
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(API_KEY);
    localStorage.removeItem(DISPLAY_NAME_KEY);
    localStorage.removeItem(PROVIDER_KEY);
    setDisplayName("");
    setUserId("");
    setApiBase("");
    setLoginForm({ displayName: "", userId: "", apiBase: "" });
    setSelectedProviderId("gemini");
    setSelectedWorkspaceId(null);
    setSelectedThreadId(null);
    setWorkspaces([]);
    setThreads([]);
    setPageError(null);
  }

  async function handleCreateThread() {
    setBusy(true);
    try {
      const createdWorkspace = await createWorkspace(apiBase, userId, "New workspace");
      await reloadSidebar(createdWorkspace.workspaceId, null);
      setPageError(null);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleCreateThreadInWorkspace(workspaceId = selectedWorkspaceId) {
    if (!workspaceId) {
      await handleCreateThread();
      return;
    }
    setBusy(true);
    try {
      const created = await createThreadInWorkspace(apiBase, userId, workspaceId, "New chat");
      await reloadSidebar(workspaceId, created.threadId);
      setPageError(null);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function ensureThread(seed: string) {
    if (selectedThreadId) return selectedThreadId;
    let workspaceId = selectedWorkspaceId;
    if (!workspaceId) {
      const createdWorkspace = await createWorkspace(apiBase, userId, seed.slice(0, 24) || "New workspace");
      workspaceId = createdWorkspace.workspaceId;
      setWorkspaces((current) => [createdWorkspace, ...current]);
      setSelectedWorkspaceId(workspaceId);
    }
    const createdThread = await createThreadInWorkspace(apiBase, userId, workspaceId, seed.slice(0, 24) || "New chat");
    setThreads((current) => [createdThread, ...current]);
    setSelectedWorkspaceId(workspaceId);
    setSelectedThreadId(createdThread.threadId);
    return createdThread.threadId;
  }

  return (
    <WorkspaceContext.Provider
      value={{
        userId, setUserId, apiBase, setApiBase, displayName, setDisplayName,
        selectedProviderId, setSelectedProviderId, loginForm, setLoginForm,
        workspaces, setWorkspaces, threads, setThreads,
        selectedWorkspaceId, setSelectedWorkspaceId, selectedThreadId, setSelectedThreadId,
        authed, pageError, setPageError, busy, setBusy,
        profileMemory, stableFacts,
        reloadSidebar, handleLogin, handleLogout, handleCreateThread, handleCreateThreadInWorkspace, ensureThread
      }}
    >
      {children}
    </WorkspaceContext.Provider>
  );
}

export function useWorkspace() {
  const context = useContext(WorkspaceContext);
  if (context === undefined) {
    throw new Error("useWorkspace must be used within a WorkspaceProvider");
  }
  return context;
}
