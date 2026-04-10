import type {
  ArtifactRecord,
  CreateStableFactRequest,
  DocumentRecord,
  MessageRecord,
  ModelProviderStatusRecord,
  ModelProviderStatusResponse,
  PostMessageRequest,
  ResearchDraftRecord,
  ReportCitation,
  ResearchFindingRecord,
  ResearchIterationRecord,
  ResearchReportSection,
  ResearchReportView,
  ResearchSourceRecord,
  RunEvent,
  SkillStatusRecord,
  SkillStatusResponse,
  StableFactMemoryRecord,
  StartResearchRequest,
  TaskRecord,
  ThreadMemoryView,
  ThreadRecord,
  UpdateResearchDraftRequest,
  WorkspaceRecord,
  UpdateWebSearchSettingsRequest,
  UpdateResearchTaskRequest,
  UpdateModelProviderConfigRequest,
  UpdateSkillConfigRequest,
  UpdateStableFactRequest,
  UpsertUserProfileMemoryRequest,
  WebSearchSettingsRecord,
  WebSearchSettingsResponse,
  UploadResponse,
  UserProfileMemoryRecord
} from "./types";

const DEFAULT_HEADERS = {
  Accept: "application/json"
};

function buildUrl(apiBase: string, path: string): string {
  if (!apiBase.trim()) {
    return path;
  }
  return `${apiBase.replace(/\/+$/, "")}${path}`;
}

async function readProblem(response: Response): Promise<string> {
  const contentType = response.headers.get("content-type") || "";
  if (contentType.includes("application/json") || contentType.includes("problem+json")) {
    const body = await response.json();
    if (typeof body?.detail === "string" && body.detail) {
      return body.detail;
    }
    if (typeof body?.message === "string" && body.message) {
      return body.message;
    }
  }
  return `${response.status} ${response.statusText}`;
}

async function requestJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const response = await fetch(input, init);
  if (!response.ok) {
    throw new Error(await readProblem(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export async function listThreads(apiBase: string, userId: string): Promise<ThreadRecord[]> {
  return requestJson<ThreadRecord[]>(buildUrl(apiBase, "/threads"), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function listWorkspaces(apiBase: string, userId: string): Promise<WorkspaceRecord[]> {
  return requestJson<WorkspaceRecord[]>(buildUrl(apiBase, "/workspaces"), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function createWorkspace(apiBase: string, userId: string, title: string): Promise<WorkspaceRecord> {
  return requestJson<WorkspaceRecord>(buildUrl(apiBase, "/workspaces"), {
    method: "POST",
    headers: { ...DEFAULT_HEADERS, "Content-Type": "application/json", "X-User-Id": userId },
    body: JSON.stringify({ title })
  });
}

export async function createThreadInWorkspace(
  apiBase: string,
  userId: string,
  workspaceId: string,
  title: string
): Promise<ThreadRecord> {
  return requestJson<ThreadRecord>(buildUrl(apiBase, `/workspaces/${workspaceId}/threads`), {
    method: "POST",
    headers: { ...DEFAULT_HEADERS, "Content-Type": "application/json", "X-User-Id": userId },
    body: JSON.stringify({ workspaceId, title })
  });
}

export async function createThread(apiBase: string, userId: string, title: string): Promise<ThreadRecord> {
  const workspaceTitle = `${title.trim() || "New chat"} workspace`;
  const workspace = await createWorkspace(apiBase, userId, workspaceTitle);
  return createThreadInWorkspace(apiBase, userId, workspace.workspaceId, title);
}

export async function listTasks(apiBase: string, userId: string, threadId: string): Promise<TaskRecord[]> {
  return requestJson<TaskRecord[]>(buildUrl(apiBase, `/threads/${threadId}/tasks`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function getTask(apiBase: string, userId: string, threadId: string, taskId: string): Promise<TaskRecord> {
  return requestJson<TaskRecord>(buildUrl(apiBase, `/threads/${threadId}/tasks/${taskId}`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function getTaskReport(apiBase: string, userId: string, threadId: string, taskId: string): Promise<ResearchReportView> {
  return requestJson<ResearchReportView>(buildUrl(apiBase, `/threads/${threadId}/tasks/${taskId}/report`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function getTaskPlan(apiBase: string, userId: string, threadId: string, taskId: string): Promise<ResearchReportSection[]> {
  return requestJson<ResearchReportSection[]>(buildUrl(apiBase, `/threads/${threadId}/tasks/${taskId}/plan`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function getTaskIterations(apiBase: string, userId: string, threadId: string, taskId: string): Promise<ResearchIterationRecord[]> {
  return requestJson<ResearchIterationRecord[]>(buildUrl(apiBase, `/threads/${threadId}/tasks/${taskId}/iterations`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function getTaskFindings(apiBase: string, userId: string, threadId: string, taskId: string): Promise<ResearchFindingRecord[]> {
  return requestJson<ResearchFindingRecord[]>(buildUrl(apiBase, `/threads/${threadId}/tasks/${taskId}/findings`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function getTaskSources(apiBase: string, userId: string, threadId: string, taskId: string): Promise<ResearchSourceRecord[]> {
  return requestJson<ResearchSourceRecord[]>(buildUrl(apiBase, `/threads/${threadId}/tasks/${taskId}/sources`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function getTaskCitations(apiBase: string, userId: string, threadId: string, taskId: string): Promise<ReportCitation[]> {
  return requestJson<ReportCitation[]>(buildUrl(apiBase, `/threads/${threadId}/tasks/${taskId}/citations`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function listArtifacts(apiBase: string, userId: string, threadId: string): Promise<ArtifactRecord[]> {
  return requestJson<ArtifactRecord[]>(buildUrl(apiBase, `/threads/${threadId}/artifacts`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function listDocuments(apiBase: string, userId: string, threadId: string): Promise<DocumentRecord[]> {
  return requestJson<DocumentRecord[]>(buildUrl(apiBase, `/threads/${threadId}/documents`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function listEvents(apiBase: string, userId: string, threadId: string): Promise<RunEvent[]> {
  return requestJson<RunEvent[]>(buildUrl(apiBase, `/threads/${threadId}/events?limit=80`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function listMessages(apiBase: string, userId: string, threadId: string): Promise<MessageRecord[]> {
  return requestJson<MessageRecord[]>(buildUrl(apiBase, `/threads/${threadId}/messages`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function getThreadMemory(apiBase: string, userId: string, threadId: string): Promise<ThreadMemoryView> {
  return requestJson<ThreadMemoryView>(buildUrl(apiBase, `/threads/${threadId}/memory`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function getProfileMemory(apiBase: string, userId: string): Promise<UserProfileMemoryRecord | null> {
  const response = await fetch(buildUrl(apiBase, "/memory/profile"), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
  if (response.status === 404 || response.status === 204) {
    return null;
  }
  if (!response.ok) {
    throw new Error(await readProblem(response));
  }
  const text = await response.text();
  return text ? (JSON.parse(text) as UserProfileMemoryRecord) : null;
}

export async function upsertProfileMemory(
  apiBase: string,
  userId: string,
  request: UpsertUserProfileMemoryRequest
): Promise<UserProfileMemoryRecord> {
  return requestJson<UserProfileMemoryRecord>(buildUrl(apiBase, "/memory/profile"), {
    method: "PUT",
    headers: { ...DEFAULT_HEADERS, "Content-Type": "application/json", "X-User-Id": userId },
    body: JSON.stringify(request)
  });
}

export async function listStableFacts(apiBase: string, userId: string): Promise<StableFactMemoryRecord[]> {
  return requestJson<StableFactMemoryRecord[]>(buildUrl(apiBase, "/memory/facts"), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function createStableFact(
  apiBase: string,
  userId: string,
  request: CreateStableFactRequest
): Promise<StableFactMemoryRecord> {
  return requestJson<StableFactMemoryRecord>(buildUrl(apiBase, "/memory/facts"), {
    method: "POST",
    headers: { ...DEFAULT_HEADERS, "Content-Type": "application/json", "X-User-Id": userId },
    body: JSON.stringify(request)
  });
}

export async function updateStableFact(
  apiBase: string,
  userId: string,
  memoryId: string,
  request: UpdateStableFactRequest
): Promise<StableFactMemoryRecord> {
  return requestJson<StableFactMemoryRecord>(buildUrl(apiBase, `/memory/facts/${memoryId}`), {
    method: "PUT",
    headers: { ...DEFAULT_HEADERS, "Content-Type": "application/json", "X-User-Id": userId },
    body: JSON.stringify(request)
  });
}

export async function deleteStableFact(apiBase: string, userId: string, memoryId: string): Promise<void> {
  const response = await fetch(buildUrl(apiBase, `/memory/facts/${memoryId}`), {
    method: "DELETE",
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
  if (!response.ok) {
    throw new Error(await readProblem(response));
  }
}

export async function getResearchDraft(apiBase: string, userId: string, threadId: string): Promise<ResearchDraftRecord | null> {
  const response = await fetch(buildUrl(apiBase, `/threads/${threadId}/research-draft`), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
  if (response.status === 404 || response.status === 204) {
    return null;
  }
  if (!response.ok) {
    throw new Error(await readProblem(response));
  }
  const text = await response.text();
  return text ? (JSON.parse(text) as ResearchDraftRecord) : null;
}

export async function startResearch(
  apiBase: string,
  userId: string,
  threadId: string,
  request: StartResearchRequest
): Promise<TaskRecord> {
  return requestJson<TaskRecord>(buildUrl(apiBase, `/threads/${threadId}/research-draft/start`), {
    method: "POST",
    headers: { ...DEFAULT_HEADERS, "Content-Type": "application/json", "X-User-Id": userId },
    body: JSON.stringify(request || {})
  });
}

export async function updateResearchDraft(
  apiBase: string,
  userId: string,
  threadId: string,
  request: UpdateResearchDraftRequest
): Promise<ResearchDraftRecord> {
  return requestJson<ResearchDraftRecord>(buildUrl(apiBase, `/threads/${threadId}/research-draft`), {
    method: "PUT",
    headers: { ...DEFAULT_HEADERS, "Content-Type": "application/json", "X-User-Id": userId },
    body: JSON.stringify(request)
  });
}

export async function discardResearchDraft(apiBase: string, userId: string, threadId: string): Promise<void> {
  const response = await fetch(buildUrl(apiBase, `/threads/${threadId}/research-draft`), {
    method: "DELETE",
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
  if (!response.ok) {
    throw new Error(await readProblem(response));
  }
}

export async function updateResearchTask(
  apiBase: string,
  userId: string,
  threadId: string,
  taskId: string,
  request: UpdateResearchTaskRequest
): Promise<TaskRecord> {
  return requestJson<TaskRecord>(buildUrl(apiBase, `/threads/${threadId}/tasks/${taskId}/updates`), {
    method: "POST",
    headers: { ...DEFAULT_HEADERS, "Content-Type": "application/json", "X-User-Id": userId },
    body: JSON.stringify(request)
  });
}

export async function cancelResearchTask(apiBase: string, userId: string, threadId: string, taskId: string): Promise<TaskRecord> {
  return requestJson<TaskRecord>(buildUrl(apiBase, `/threads/${threadId}/tasks/${taskId}/cancel`), {
    method: "POST",
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function uploadFile(
  apiBase: string,
  userId: string,
  threadId: string,
  file: File
): Promise<UploadResponse> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await fetch(buildUrl(apiBase, `/threads/${threadId}/uploads`), {
    method: "POST",
    headers: { "X-User-Id": userId },
    body: formData
  });
  if (!response.ok) {
    throw new Error(await readProblem(response));
  }
  return response.json() as Promise<UploadResponse>;
}

export async function uploadFileToWorkspace(
  apiBase: string,
  userId: string,
  workspaceId: string,
  file: File
): Promise<UploadResponse> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await fetch(buildUrl(apiBase, `/workspaces/${workspaceId}/uploads`), {
    method: "POST",
    headers: { "X-User-Id": userId },
    body: formData
  });
  if (!response.ok) {
    throw new Error(await readProblem(response));
  }
  return response.json() as Promise<UploadResponse>;
}

function parseSseChunk(chunk: string): { eventName: string | null; data: string } | null {
  const lines = chunk.replace(/\r/g, "").split("\n").filter((line) => line.length > 0);
  if (lines.length === 0) {
    return null;
  }
  let eventName: string | null = null;
  const dataLines: string[] = [];
  for (const line of lines) {
    if (line.startsWith("event:")) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trim());
    }
  }
  if (dataLines.length === 0) {
    return null;
  }
  return { eventName, data: dataLines.join("\n") };
}

function shouldYieldAfterEvent(event: RunEvent) {
  return event.eventType !== "message.delta"
    && event.eventType !== "agent.step.delta"
    && event.eventType !== "model.thinking.delta";
}

async function yieldForRealtimePaint() {
  if (typeof window !== "undefined" && typeof window.requestAnimationFrame === "function") {
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()));
    return;
  }
  await new Promise<void>((resolve) => setTimeout(resolve, 0));
}

export async function streamMessage(
  apiBase: string,
  userId: string,
  threadId: string,
  request: PostMessageRequest,
  onEvent: (event: RunEvent) => void
): Promise<void> {
  const response = await fetch(buildUrl(apiBase, `/threads/${threadId}/messages`), {
    method: "POST",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
      "X-User-Id": userId
    },
    body: JSON.stringify(request)
  });

  if (!response.ok || !response.body) {
    throw new Error(await readProblem(response));
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const normalizedBuffer = buffer.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
    const chunks = normalizedBuffer.split("\n\n");
    buffer = chunks.pop() || "";
    for (const chunk of chunks) {
      const parsed = parseSseChunk(chunk);
      if (!parsed) {
        continue;
      }
      const event = JSON.parse(parsed.data) as RunEvent;
      onEvent(event);
      if (shouldYieldAfterEvent(event)) {
        await yieldForRealtimePaint();
      }
    }
  }

  const remaining = buffer.trim();
  if (remaining) {
    const parsed = parseSseChunk(remaining);
    if (parsed) {
      const event = JSON.parse(parsed.data) as RunEvent;
      onEvent(event);
      if (shouldYieldAfterEvent(event)) {
        await yieldForRealtimePaint();
      }
    }
  }
}

export async function listSkillStatuses(apiBase: string, userId: string): Promise<SkillStatusResponse> {
  return requestJson<SkillStatusResponse>(buildUrl(apiBase, "/skills/status"), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function updateSkillConfig(
  apiBase: string,
  userId: string,
  skillId: string,
  request: UpdateSkillConfigRequest
): Promise<SkillStatusRecord> {
  return requestJson<SkillStatusRecord>(buildUrl(apiBase, `/skills/${encodeURIComponent(skillId)}/config`), {
    method: "PUT",
    headers: { ...DEFAULT_HEADERS, "Content-Type": "application/json", "X-User-Id": userId },
    body: JSON.stringify(request)
  });
}

export async function listModelProviderStatuses(apiBase: string, userId: string): Promise<ModelProviderStatusResponse> {
  return requestJson<ModelProviderStatusResponse>(buildUrl(apiBase, "/model-settings/providers"), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function updateModelProviderConfig(
  apiBase: string,
  userId: string,
  providerId: string,
  request: UpdateModelProviderConfigRequest
): Promise<ModelProviderStatusRecord> {
  return requestJson<ModelProviderStatusRecord>(buildUrl(apiBase, `/model-settings/providers/${encodeURIComponent(providerId)}`), {
    method: "PUT",
    headers: { ...DEFAULT_HEADERS, "Content-Type": "application/json", "X-User-Id": userId },
    body: JSON.stringify(request)
  });
}

export async function getWebSearchSettings(apiBase: string, userId: string): Promise<WebSearchSettingsResponse> {
  return requestJson<WebSearchSettingsResponse>(buildUrl(apiBase, "/web-settings/search"), {
    headers: { ...DEFAULT_HEADERS, "X-User-Id": userId }
  });
}

export async function updateWebSearchSettings(
  apiBase: string,
  userId: string,
  request: UpdateWebSearchSettingsRequest
): Promise<WebSearchSettingsRecord> {
  return requestJson<WebSearchSettingsRecord>(buildUrl(apiBase, "/web-settings/search"), {
    method: "PUT",
    headers: { ...DEFAULT_HEADERS, "Content-Type": "application/json", "X-User-Id": userId },
    body: JSON.stringify(request)
  });
}
