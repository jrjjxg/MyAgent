import React, { createContext, useContext, useState, useEffect, ReactNode, useMemo } from "react";
import {
  MessageRecord, TaskRecord, ArtifactRecord, DocumentRecord, RunEvent,
  ResearchDraftRecord, ThreadMemoryView, StreamingAssistantMessage, InteractionMode,
  UpdateResearchDraftRequest
} from "../types";
import {
  listMessages, listTasks, listArtifacts, listDocuments, listEvents,
  getResearchDraft, getThreadMemory, streamMessage, startResearch,
  discardResearchDraft, updateResearchDraft, updateResearchTask, cancelResearchTask
} from "../api";
import { useWorkspace } from "./WorkspaceContext";
import { useResearchTaskData } from "../hooks/useResearchTaskData";

interface ChatContextType {
  messagesByThread: Record<string, MessageRecord[]>;
  tasksByThread: Record<string, TaskRecord[]>;
  artifactsByThread: Record<string, ArtifactRecord[]>;
  documentsByThread: Record<string, DocumentRecord[]>;
  eventsByThread: Record<string, RunEvent[]>;
  draftsByThread: Record<string, ResearchDraftRecord | null>;
  threadMemoryByThread: Record<string, ThreadMemoryView | null>;
  streamingByThread: Record<string, StreamingAssistantMessage | null>;
  pendingImageArtifactsByThread: Record<string, ArtifactRecord[]>;
  setPendingImageArtifactsByThread: React.Dispatch<React.SetStateAction<Record<string, ArtifactRecord[]>>>;
  composerMode: InteractionMode;
  setComposerMode: (mode: InteractionMode) => void;
  messageDraft: string;
  setMessageDraft: (draft: string) => void;
  threadLoading: boolean;
  uploading: boolean;
  setUploading: (uploading: boolean) => void;
  reloadThreadData: (threadId: string) => Promise<void>;
  handlePostMessage: (interactionMode: InteractionMode) => Promise<void>;
  handleStartResearch: () => Promise<void>;
  handleDiscardDraft: () => Promise<void>;
  handleSaveDraft: (request: UpdateResearchDraftRequest) => Promise<void>;
  handleUpdateResearch: () => Promise<void>;
  handleCancelResearchTask: () => Promise<void>;
  handleSend: () => Promise<void>;
  researchReportByTask: Record<string, any>;
  researchPlanByTask: Record<string, any>;
  researchIterationsByTask: Record<string, any>;
  researchFindingsByTask: Record<string, any>;
  researchSourcesByTask: Record<string, any>;
  researchCitationsByTask: Record<string, any>;
}

const ChatContext = createContext<ChatContextType | undefined>(undefined);

export function ChatProvider({ children }: { children: ReactNode }) {
  const {
    userId, apiBase, selectedThreadId, selectedProviderId,
    authed, setPageError, busy, setBusy, ensureThread, reloadSidebar
  } = useWorkspace();

  const [messagesByThread, setMessagesByThread] = useState<Record<string, MessageRecord[]>>({});
  const [tasksByThread, setTasksByThread] = useState<Record<string, TaskRecord[]>>({});
  const [artifactsByThread, setArtifactsByThread] = useState<Record<string, ArtifactRecord[]>>({});
  const [documentsByThread, setDocumentsByThread] = useState<Record<string, DocumentRecord[]>>({});
  const [eventsByThread, setEventsByThread] = useState<Record<string, RunEvent[]>>({});
  const [draftsByThread, setDraftsByThread] = useState<Record<string, ResearchDraftRecord | null>>({});
  const [threadMemoryByThread, setThreadMemoryByThread] = useState<Record<string, ThreadMemoryView | null>>({});
  const [streamingByThread, setStreamingByThread] = useState<Record<string, StreamingAssistantMessage | null>>({});
  const [pendingImageArtifactsByThread, setPendingImageArtifactsByThread] = useState<Record<string, ArtifactRecord[]>>({});
  
  const [composerMode, setComposerMode] = useState<InteractionMode>("CHAT");
  const [messageDraft, setMessageDraft] = useState("");
  const [threadLoading, setThreadLoading] = useState(false);
  const [uploading, setUploading] = useState(false);

  const {
    researchReportByTask,
    researchPlanByTask,
    researchIterationsByTask,
    researchFindingsByTask,
    researchSourcesByTask,
    researchCitationsByTask,
    reloadResearchTaskData
  } = useResearchTaskData(apiBase, userId);

  const selectedTasks = selectedThreadId ? tasksByThread[selectedThreadId] || [] : [];
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
  const selectedDraft = selectedThreadId ? draftsByThread[selectedThreadId] || null : null;
  const readyToStart = Boolean(selectedDraft && !activeResearchTask && selectedDraft.ready);

  useEffect(() => {
    if (selectedThreadId && authed) void reloadThreadData(selectedThreadId);
  }, [authed, selectedThreadId]);

  useEffect(() => {
    if (!selectedThreadId || !activeResearchTask) return;
    const handle = window.setInterval(() => {
      void reloadThreadData(selectedThreadId);
    }, 3000);
    return () => window.clearInterval(handle);
  }, [activeResearchTask, selectedThreadId]);

  useEffect(() => {
    if (!selectedThreadId || !latestResearchTask || latestResearchTask.status === "FAILED" || latestResearchTask.status === "CANCELLED") return;
    void reloadResearchTaskData(selectedThreadId, latestResearchTask.taskId, latestResearchTask.status === "COMPLETED").then(err => {
      if (err) setPageError(err);
    });
  }, [latestResearchTask, selectedThreadId, reloadResearchTaskData]);

  async function reloadThreadData(threadId: string) {
    const shouldMarkLoading = !messagesByThread[threadId];
    if (shouldMarkLoading) setThreadLoading(true);
    try {
      const [messages, tasks, artifacts, documents, events, draft, threadMemory] = await Promise.all([
        listMessages(apiBase, userId, threadId),
        listTasks(apiBase, userId, threadId),
        listArtifacts(apiBase, userId, threadId),
        listDocuments(apiBase, userId, threadId),
        listEvents(apiBase, userId, threadId),
        getResearchDraft(apiBase, userId, threadId),
        getThreadMemory(apiBase, userId, threadId)
      ]);
      setMessagesByThread((current) => ({ ...current, [threadId]: messages }));
      setTasksByThread((current) => ({ ...current, [threadId]: tasks }));
      setArtifactsByThread((current) => ({ ...current, [threadId]: artifacts }));
      setDocumentsByThread((current) => ({ ...current, [threadId]: documents }));
      setEventsByThread((current) => ({ ...current, [threadId]: events }));
      setDraftsByThread((current) => ({ ...current, [threadId]: draft }));
      setThreadMemoryByThread((current) => ({ ...current, [threadId]: threadMemory }));
      setPageError(null);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      if (shouldMarkLoading) setThreadLoading(false);
    }
  }

  function isResearchStartIntent(value: string) {
    const normalized = value.trim().toLowerCase().replace(/[\s"'`~!@#$%^&*()\-_=+\[\]{}\\|;:,.<>/?，。！；：“”‘’（）【】、】【、]+/g, "");
    return normalized.length > 0 && new Set(["start", "startresearch", "beginresearch", "research"]).has(normalized);
  }

  async function handlePostMessage(interactionMode: InteractionMode) {
    const draftContent = messageDraft.trim();
    const attachedImages = selectedThreadId ? pendingImageArtifactsByThread[selectedThreadId] || [] : [];
    if (!draftContent && attachedImages.length === 0) return;
    
    const content = draftContent || "请描述附件中的图片。";
    let threadIdForSend = selectedThreadId;
    setBusy(true);
    try {
      const threadId = await ensureThread(content);
      threadIdForSend = threadId;
      setMessageDraft("");
      setPendingImageArtifactsByThread((current) => ({ ...current, [threadId]: [] }));
      
      const optimisticMessage: MessageRecord = {
        messageId: `temp-${crypto.randomUUID()}`,
        threadId,
        role: "USER",
        content: attachedImages.length > 0 ? `${content}\n\n[附加图片: ${attachedImages.map(img => img.name || "image").join(", ")}]` : content,
        interactionMode,
        createdAt: new Date().toISOString()
      };

      setMessagesByThread((current) => ({
        ...current,
        [threadId]: [...(current[threadId] || []), optimisticMessage]
      }));
      setStreamingByThread((current) => ({
        ...current,
        [threadId]: {
          id: `stream-pending-${threadId}`,
          role: "ASSISTANT",
          content: "",
          timestamp: new Date().toISOString(),
          runId: `pending-${threadId}`,
          status: "streaming"
        }
      }));
      
      await streamMessage(apiBase, userId, threadId, {
        content,
        interactionMode,
        providerId: selectedProviderId,
        imageArtifactIds: attachedImages.map((artifact) => artifact.artifactId)
      }, (event) => {
        setEventsByThread((current) => ({ ...current, [threadId]: [event, ...(current[threadId] || [])].slice(0, 120) }));
        setStreamingByThread((current) => {
          const existing = current[threadId];
          if (!existing) return current;

          let nextMessage: StreamingAssistantMessage = existing.runId === event.runId && existing.id === `stream-${event.runId}`
            ? existing
            : {
                ...existing,
                id: `stream-${event.runId}`,
                runId: event.runId,
                timestamp: existing.timestamp || event.timestamp
              };

          if (event.eventType === "message.delta") {
            const payload = event.payload as { delta?: string } | undefined;
            const delta = typeof payload?.delta === "string" ? payload.delta : "";
            nextMessage = {
              ...nextMessage,
              content: `${nextMessage.content}${delta}`
            };
          } else if (event.eventType === "message.failed" || event.eventType === "run.failed") {
            nextMessage = {
              ...nextMessage,
              status: "error"
            };
          } else if (event.eventType === "message.completed" || event.eventType === "run.completed") {
            nextMessage = {
              ...nextMessage,
              status: "done"
            };
          }

          if (nextMessage === existing) {
            return current;
          }

          return {
            ...current,
            [threadId]: nextMessage
          };
        });
      });
      setStreamingByThread((current) => ({ ...current, [threadId]: null }));
      await Promise.all([reloadThreadData(threadId), reloadSidebar(undefined, threadId)]);
      setPageError(null);
    } catch (error) {
      const restoreThreadId = threadIdForSend || "";
      if (restoreThreadId) {
        setPendingImageArtifactsByThread((current) => ({
          ...current,
          [restoreThreadId]: [
            ...(current[restoreThreadId] || []),
            ...attachedImages.filter((artifact) => !(current[restoreThreadId] || []).some((item) => item.artifactId === artifact.artifactId))
          ]
        }));
      }
      setPageError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleStartResearch() {
    if (!selectedThreadId || !selectedDraft) return;
    setBusy(true);
    try {
      await startResearch(apiBase, userId, selectedThreadId, { providerId: selectedProviderId, draftRevision: selectedDraft.revision });
      setMessageDraft("");
      await reloadThreadData(selectedThreadId);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleDiscardDraft() {
    if (!selectedThreadId) return;
    setBusy(true);
    try {
      await discardResearchDraft(apiBase, userId, selectedThreadId);
      await reloadThreadData(selectedThreadId);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleSaveDraft(request: UpdateResearchDraftRequest) {
    if (!selectedThreadId) return;
    setBusy(true);
    try {
      const updated = await updateResearchDraft(apiBase, userId, selectedThreadId, request);
      setDraftsByThread((current) => ({ ...current, [selectedThreadId]: updated }));
      setPageError(null);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleUpdateResearch() {
    if (!selectedThreadId || !activeResearchTask || !messageDraft.trim()) return;
    setBusy(true);
    try {
      await updateResearchTask(apiBase, userId, selectedThreadId, activeResearchTask.taskId, { content: messageDraft.trim() });
      setMessageDraft("");
      await reloadThreadData(selectedThreadId);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleSend() {
    if (composerMode === "CHAT") {
      await handlePostMessage("CHAT");
      return;
    }
    if (activeResearchTask) {
      await handleUpdateResearch();
      return;
    }
    if (readyToStart && (!messageDraft.trim() || isResearchStartIntent(messageDraft))) {
      await handleStartResearch();
      return;
    }
    await handlePostMessage("DEEP_RESEARCH");
  }

  async function handleCancelResearchTask() {
    if (!selectedThreadId || !activeResearchTask) return;
    setBusy(true);
    try {
      await cancelResearchTask(apiBase, userId, selectedThreadId, activeResearchTask.taskId);
      await reloadThreadData(selectedThreadId);
    } catch (error) {
      setPageError((error as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <ChatContext.Provider
      value={{
        messagesByThread, tasksByThread, artifactsByThread, documentsByThread,
        eventsByThread, draftsByThread, threadMemoryByThread, streamingByThread,
        pendingImageArtifactsByThread, setPendingImageArtifactsByThread,
        composerMode, setComposerMode, messageDraft, setMessageDraft,
        threadLoading, uploading, setUploading, reloadThreadData,
        handlePostMessage, handleStartResearch, handleDiscardDraft, handleSaveDraft,
        handleUpdateResearch, handleCancelResearchTask, handleSend,
        researchReportByTask, researchPlanByTask, researchIterationsByTask,
        researchFindingsByTask, researchSourcesByTask, researchCitationsByTask
      }}
    >
      {children}
    </ChatContext.Provider>
  );
}

export function useChat() {
  const context = useContext(ChatContext);
  if (context === undefined) {
    throw new Error("useChat must be used within a ChatProvider");
  }
  return context;
}
