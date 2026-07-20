export type RoleKey = 'platform-admin' | 'tenant-admin' | 'business-user';

export interface ApiEnvelope<T> {
  header: { code: string; message: string; traceId?: string };
  body: T;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

export interface ConsoleMenu {
  code: string;
  name: string;
  platform: string;
  path: string;
  routeName: string;
  componentKey: string;
  permission?: string;
  sort: number;
}

export interface CurrentUser {
  tenantId: string;
  userId: string;
  loginName: string;
  displayName: string;
  roleCodes: string[];
  permissionCodes: string[];
  menus: ConsoleMenu[];
  defaultRoute: string;
  accessTokenExpiresAt: string;
}

export interface TenantFeature {
  featureCode: string;
  featureName: string;
  enabled: boolean;
  source: string;
}

export interface IamTenant {
  tenantId: string;
  code: string;
  name: string;
  status: string;
  planCode?: string;
}

export interface IamUserItem {
  userId: string;
  username: string;
  displayName: string;
  email?: string;
  status: string;
  roleCodes: string[];
}

export interface DelegatedTenantActionResult {
  delegationActionId: string;
  status: string;
  auditEventId: string;
}

export interface ApprovalTask {
  taskId: string;
  approvalRequestId: string;
  approvalType?: string;
  title: string;
  riskLevel: string;
  riskLevelLabel: string;
  status: string;
  statusLabel: string;
  sourceContextLabel: string;
  sourceContext: string;
  sourceResourceType: string;
  sourceResourceId: string;
  requesterId: string;
  approverId?: string;
  traceId?: string;
  evidenceSchema?: string | null;
  evidenceDigest?: string | null;
  targetDigest?: string | null;
  evidence?: Record<string, unknown> | null;
}

export interface ModelProviderItem {
  providerId: string; providerCode: string; providerName: string; endpoint: string; providerKind?: string; status: string;
  approvalRequestId?: string; version: number;
}

export interface ModelConfigItem {
  providerId: string; modelConfigId: string; modelCode: string; modelName: string; modelType: string;
  contextWindow?: number; maxOutputTokens?: number; status: string; connectivityStatus: string; connectivityMessage?: string;
  approvalRequestId?: string; version: number;
}

export interface AiApplicationItem {
  applicationId: string; name: string; description?: string; applicationType: string; status: string;
  modelConfigId?: string; knowledgeBaseIds?: string[]; systemPrompt?: string;
}

export interface UserInvitationItem {
  invitationId: string; userId: string; email: string; status: string; statusLabel: string;
  deliveryStatus: string; deliveryStatusLabel: string; deliveryAttempts: number; deliveryError?: string;
  expiresAt: string; createdAt: string;
}

export interface ApprovalDecisionResult {
  taskId: string;
  approvalRequestId: string;
  status: string;
  sourceContext: string;
  sourceResourceType: string;
  sourceResourceId: string;
  evidenceSchema: string;
  evidenceDigest: string;
  targetDigest: string;
}

export interface IamRole {
  roleId: string;
  code: string;
  name: string;
  type: string;
  defaultMenuCode?: string;
  status: string;
  description?: string;
}

export interface IamPermission {
  permissionId: string;
  code: string;
  name: string;
  resourceType: string;
  actionCode: string;
  description?: string;
  status: string;
}

export interface PublishPreflightIssue {
  code: string;
  message: string;
  resourceType?: string;
  resourceId?: string;
}

export interface PublishPreflightResult {
  applicationId: string;
  publishRequestId: string;
  passed: boolean;
  blockers: PublishPreflightIssue[];
  warnings: PublishPreflightIssue[];
  traceId: string;
  checkedAt: string;
}

export interface ChatCitation {
  knowledgeBaseId: string;
  documentId: string;
  chunkId: string;
  title: string;
  snippet: string;
  locator: string;
  score: number;
}

export interface ChatMessageHistoryItem {
  messageId: string;
  sessionId: string;
  applicationId: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  citations: ChatCitation[];
  citationCount: number;
  modelInvocationLogId?: string;
  traceId?: string;
  createdAt: string;
}

export interface RetrievalEvidence {
  knowledgeBaseId: string;
  documentId: string;
  chunkId: string;
  score: number;
  retrievalSignal: string;
  indexVersion: string;
}

export interface SendChatMessageResult {
  messageId: string;
  userMessageId: string;
  assistantMessageId: string;
  sessionId: string;
  applicationId: string;
  answer: string;
  answerable: boolean;
  citations: ChatCitation[];
  filteredCitationCount: number;
  evidences: RetrievalEvidence[];
  noAnswerReason?: string;
  traceId?: string;
  modelInvocationLogId?: string;
  providerCode?: string;
  modelCode?: string;
  decisionSource?: string;
  invocationStatus?: string;
  inputTokens?: number;
  outputTokens?: number;
  latencyMs?: number;
  errorCode?: string;
  errorMessage?: string;
  createdAt: string;
}

export interface ChatMessageFeedbackResult {
  feedbackId: string;
  status: string;
  rating: 'HELPFUL' | 'UNHELPFUL';
  updatedAt: string;
}

export interface PermissionRequestItem {
  requestId: string;
  requesterId: string;
  resourceType: string;
  resourceId: string;
  permissionCode: string;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewerId?: string;
  decisionComment?: string;
  createdAt: string;
  updatedAt: string;
}

export interface LoginResponse {
  token: string;
  userId: string;
  tenantId: string;
  displayName: string;
  roleCodes: string[];
  tokenExpiresAt: string;
}

export interface SummaryResult {
  tenantId?: string;
  asOf: string;
  freshness: string;
  partial: boolean;
  failedMetrics: string[];
  traceId: string;
  enabledFeatures?: string[];
  tenantHealth?: Record<string, unknown>;
  modelHealth?: Record<string, unknown>;
  approvalTodos?: Record<string, unknown>;
  ragSecurity?: Record<string, unknown>;
  appPublishBlocks?: Record<string, unknown>;
  userRoleGaps?: Record<string, unknown>;
  knowledgeHealth?: Record<string, unknown>;
  appHealth?: Record<string, unknown>;
  permissionTodos?: Record<string, unknown>;
}

export type RequestStatus = 'idle' | 'loading' | 'success' | 'empty' | 'partial' | 'error';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string,
    public readonly traceId?: string,
    public readonly body?: unknown,
  ) {
    super(message);
  }
}
