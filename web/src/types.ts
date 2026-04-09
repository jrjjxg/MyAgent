export type ThreadStatus = "IDLE" | "RUNNING" | "FAILED";
export type TaskStatus = "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
export type TaskKind = "INGEST" | "RESEARCH";
export type DocumentStatus = "UPLOADED" | "INGESTING" | "READY" | "FAILED";
export type InteractionMode = "CHAT" | "DEEP_RESEARCH";
export type MessageRole = "USER" | "ASSISTANT" | "SYSTEM";
export type ResearchDraftStatus = "COLLECTING" | "READY" | "STARTED" | "DISCARDED";
export type StableFactStatus = "ACTIVE" | "DELETED";
export type ChatRouteKind = "GENERAL_CHAT" | "TOOL_ASSISTED_CHAT" | "REALTIME_LOOKUP" | "DOCUMENT_QA" | "RESEARCH_DRAFT";
export type WorkspaceStatus = "ACTIVE";

export interface WorkspaceRecord {
  workspaceId: string;
  userId: string;
  title: string;
  status: WorkspaceStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ResearchPlanStep {
  stepId: string;
  title: string;
  objective: string;
  query: string;
  useWeb: boolean;
  useDocuments: boolean;
  outputFocus: string;
}

export interface ThreadRecord {
  threadId: string;
  userId: string;
  workspaceId: string;
  title: string;
  status: ThreadStatus;
  createdAt: string;
  updatedAt: string;
}

export interface TaskRecord {
  taskId: string;
  threadId: string;
  agentId: string;
  kind: TaskKind;
  status: TaskStatus;
  title: string;
  summary: string;
  stage: string;
  progress: number | null;
  linkedDraftId?: string | null;
  resultArtifactId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ArtifactRecord {
  artifactId: string;
  userId: string;
  workspaceId: string;
  sourceThreadId?: string | null;
  name: string;
  type: string;
  visibility: string;
  area: string;
  relativePath: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
}

export interface UploadResponse {
  artifact: ArtifactRecord;
  documentId?: string | null;
  ingestTaskId?: string | null;
}

export interface RunEvent {
  runId: string;
  threadId: string;
  eventType: string;
  timestamp: string;
  payload: unknown;
}

export interface ExecutionSource {
  kind: string;
  title: string;
  domain: string;
  url: string;
  verified: boolean;
  usedInAnswer: boolean;
}

export type ResearchEvidenceStatus = "candidate" | "verified" | "cited";

export interface DocumentRecord {
  documentId: string;
  workspaceId: string;
  sourceThreadId?: string | null;
  sourceArtifactId: string;
  name: string;
  status: DocumentStatus;
  primaryTextArtifactId?: string | null;
  chunkIndexArtifactId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MessageRecord {
  messageId: string;
  threadId: string;
  role: MessageRole;
  content: string;
  interactionMode: InteractionMode;
  runId?: string | null;
  taskId?: string | null;
  imageArtifactIds?: string[];
  createdAt: string;
}

export interface ThreadMemoryView {
  threadId: string;
  summary: string;
  recentMessages: MessageRecord[];
  pendingHistoricalMessages: MessageRecord[];
  activeDraftId?: string | null;
  activeTaskId?: string | null;
  taskStage?: string | null;
}

export interface UserProfileMemoryRecord {
  userId: string;
  displayName?: string | null;
  preferredLanguage?: string | null;
  preferredOutputStyles: string[];
  projectTags: string[];
  notes?: string | null;
  updatedAt: string;
}

export interface UpsertUserProfileMemoryRequest {
  displayName: string;
  preferredLanguage: string;
  preferredOutputStyles: string[];
  projectTags: string[];
  notes: string;
}

export interface StableFactMemoryRecord {
  memoryId: string;
  userId: string;
  factType?: string | null;
  title?: string | null;
  content?: string | null;
  sourceThreadId?: string | null;
  sourceTaskId?: string | null;
  status: StableFactStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateStableFactRequest {
  factType: string;
  title: string;
  content: string;
  sourceThreadId: string;
  sourceTaskId: string;
}

export interface UpdateStableFactRequest {
  factType: string;
  title: string;
  content: string;
  sourceThreadId: string;
  sourceTaskId: string;
}

export interface ResearchDraftRecord {
  draftId: string;
  threadId: string;
  status: ResearchDraftStatus;
  title: string;
  brief: string;
  objective?: string | null;
  scope?: string | null;
  outputFormat?: string | null;
  constraints?: string[];
  questions: string[];
  revision: number;
  planSummary?: string | null;
  planSteps: ResearchPlanStep[];
  ready: boolean;
  lastUserMessageId?: string | null;
  lastAssistantMessageId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PostMessageRequest {
  content: string;
  interactionMode: InteractionMode;
  providerId?: string;
  imageArtifactIds?: string[];
  documentIds?: string[];
}

export interface StartResearchRequest {
  providerId?: string;
  draftRevision?: number;
}

export interface UpdateResearchDraftRequest {
  revision: number;
  title?: string;
  brief?: string;
  objective?: string;
  scope?: string;
  outputFormat?: string;
  constraints?: string[];
  questions?: string[];
  planSummary?: string;
  planSteps?: ResearchPlanStep[];
}

export interface UpdateResearchTaskRequest {
  content: string;
}

export interface StreamingAssistantMessage {
  id: string;
  role: "ASSISTANT";
  content: string;
  imageArtifactIds?: string[];
  timestamp: string;
  runId: string;
  status: "streaming" | "done" | "error";
}

export interface ApprovedResearchPlan {
  draftId: string;
  revision: number;
  title: string;
  brief: string;
  objective?: string | null;
  scope?: string | null;
  outputFormat?: string | null;
  constraints: string[];
  planSummary?: string | null;
  planSteps: ResearchPlanStep[];
}

export interface ResearchSiteRecord {
  stepId?: string | null;
  url: string;
  title: string;
  domain?: string | null;
  sourceType: string;
}

export interface ResearchActivityRecord {
  stepId?: string | null;
  summary: string;
  kind?: string | null;
}

export interface ResearchUpgradeSuggestion {
  reason: string;
  suggestedTitle?: string | null;
  suggestedBrief: string;
}

export interface ResearchReportSection {
  sectionId: string;
  title: string;
  summary: string;
}

export interface ResearchQueryRecord {
  queryId: string;
  iterationNo: number;
  phase: string;
  query: string;
  intent: string;
  quality: string;
  candidateCount: number;
  verifiedCount: number;
}

export interface ResearchFindingRecord {
  findingId: string;
  title: string;
  summary: string;
  confidence: string;
  scopeLimit: string;
  supportingSourceIds: string[];
  usedInReport: boolean;
  reportSectionId?: string | null;
}

export interface ResearchGapRecord {
  gapId: string;
  iterationNo: number;
  topic: string;
  reason: string;
  strategy: string;
  resolved: boolean;
}

export interface ResearchIterationRecord {
  iterationNo: number;
  phase: string;
  summary: string;
  confirmedFindings: string[];
  openQuestions: string[];
  nextSearchIntent: string[];
  queryIds: string[];
  sourceIds: string[];
}

export interface ResearchSourceRecord {
  sourceId: string;
  kind: string;
  title: string;
  uri?: string | null;
  locator?: string | null;
  snippet?: string | null;
  domain?: string | null;
  unitId?: string | null;
  citationLabel?: string | null;
  iterationNo?: number | null;
  discoveryQuery?: string | null;
  evidenceStatus?: ResearchEvidenceStatus | string | null;
  verificationMethod?: string | null;
  supportingFindingIds: string[];
  citationIds: string[];
  anchorText?: string | null;
}

export interface ReportCitation {
  citationLabel: string;
  sourceId: string;
  kind: string;
  title: string;
  uri?: string | null;
  locator?: string | null;
  usedInReport: boolean;
  occurrenceCount: number;
  citationId: string;
  paragraphId?: string | null;
  blockId?: string | null;
  anchorText?: string | null;
  supportingFindingIds: string[];
}

export interface ResearchReportBlock {
  blockId: string;
  paragraphId: string;
  text: string;
  citationIds: string[];
}

export interface ResearchReportView {
  markdown: string;
  blocks: ResearchReportBlock[];
}

export interface SkillStatusRecord {
  skillId: string;
  sourceKey: string;
  description: string;
  summary: string;
  source: string;
  path: string;
  homepage: string;
  enabled: boolean;
  primaryEnv: string;
  requiredEnvs: string[];
  requiresDocuments: boolean;
  requiresWeb: boolean;
  missingEnvs: string[];
  configuredEnvKeys: string[];
  ready: boolean;
}

export interface SkillStatusResponse {
  secretStorageAvailable: boolean;
  skills: SkillStatusRecord[];
}

export interface UpdateSkillConfigRequest {
  enabled?: boolean;
  apiKey?: string;
  env?: Record<string, string>;
}

export interface ModelProviderStatusRecord {
  providerId: string;
  displayName: string;
  enabled: boolean;
  ready: boolean;
  apiKeyConfigured: boolean;
  customApiKeyConfigured: boolean;
  systemApiKeyConfigured: boolean;
  effectiveModel: string;
  customModel: string;
  effectiveBaseUrl: string;
  customBaseUrl: string;
  supportsCustomBaseUrl: boolean;
}

export interface ModelProviderStatusResponse {
  secretStorageAvailable: boolean;
  defaultProviderId: string;
  providers: ModelProviderStatusRecord[];
}

export interface UpdateModelProviderConfigRequest {
  enabled?: boolean;
  apiKey?: string;
  model?: string;
  baseUrl?: string;
}

export interface WebSearchSettingsRecord {
  effectiveProvider: string;
  customProvider: string;
  tavilyApiKeyConfigured: boolean;
  customTavilyApiKeyConfigured: boolean;
  systemTavilyApiKeyConfigured: boolean;
  effectiveSearchApiBaseUrl: string;
  customSearchApiBaseUrl: string;
}

export interface WebSearchSettingsResponse {
  secretStorageAvailable: boolean;
  settings: WebSearchSettingsRecord;
}

export interface UpdateWebSearchSettingsRequest {
  provider?: string;
  tavilyApiKey?: string;
  searchApiBaseUrl?: string;
}
