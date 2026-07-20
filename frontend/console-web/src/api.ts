import type {
  ApiEnvelope,
  ApprovalDecisionResult,
  ApprovalTask,
  CurrentUser,
  ChatMessageFeedbackResult,
  DelegatedTenantActionResult,
  IamPermission,
  IamRole,
  IamTenant,
  IamUserItem,
  LoginResponse,
  PermissionRequestItem,
  PublishPreflightResult,
  SendChatMessageResult,
  SummaryResult,
  TenantFeature,
  PageResponse,
  ModelProviderItem,
  ModelConfigItem,
  AiApplicationItem,
  UserInvitationItem,
} from './types';
import { ApiError } from './types';

export interface ChatRunDeadLetterItem {
  runId: string;
  sessionId: string;
  applicationId: string;
  originalActorId: string;
  finalizationAttemptCount: number;
  finalizationCycleAttemptCount: number;
  manualReplayCount: number;
  failureCode: string;
  failureSummary: string;
  failureTraceId: string;
  updatedAt: string;
}

export interface ChatRunManualReplayResult {
  runId: string;
  replayRequestId: string;
  status: 'COMPLETED' | 'RETRY_SCHEDULED' | 'DEAD_LETTER' | string;
}

const deadLetterStringFields = [
  'runId', 'sessionId', 'applicationId', 'originalActorId', 'failureCode',
  'failureSummary', 'failureTraceId', 'updatedAt',
] as const;
const deadLetterNumberFields = [
  'finalizationAttemptCount', 'finalizationCycleAttemptCount', 'manualReplayCount',
] as const;

function parseChatRunDeadLetters(value: unknown): ChatRunDeadLetterItem[] {
  const invalid = !Array.isArray(value) || value.some((item) => {
    if (!item || typeof item !== 'object') return true;
    const row = item as Record<string, unknown>;
    return deadLetterStringFields.some((field) => typeof row[field] !== 'string' || !row[field])
      || deadLetterNumberFields.some((field) => (
        typeof row[field] !== 'number' || !Number.isSafeInteger(row[field]) || row[field] < 0
      ));
  });
  if (invalid) throw new Error('死信响应无效');
  return value as ChatRunDeadLetterItem[];
}

const TOKEN_KEY = 'aip.access-token';
const AUTH_SESSION_KEY = 'aip.auth-session';
const AUTH_SESSION_VERSION = 1;
export const SESSION_EXPIRED_EVENT = 'aip:session-expired';

export interface SessionIdentity {
  tenantId: string;
  userId: string;
}

export interface SessionSnapshot {
  token: string | null;
  generation: string;
  identity: SessionIdentity | null;
  expiresAt: string | null;
}

type StoredSession = SessionSnapshot & { version: typeof AUTH_SESSION_VERSION };

type ActiveRequest = {
  controller: AbortController;
  snapshot: SessionSnapshot;
};

const activeRequests = new Set<ActiveRequest>();
const localSessionObservers = new Set<(snapshot: SessionSnapshot) => void>();
let generationSequence = 0;
const legacyGeneration = nextGeneration('legacy');
let lastExpiredSessionKey: string | undefined;

function nextGeneration(reason: string) {
  generationSequence += 1;
  const random = typeof globalThis.crypto?.randomUUID === 'function'
    ? globalThis.crypto.randomUUID()
    : `${Date.now().toString(36)}-${generationSequence.toString(36)}`;
  return `${reason}:${random}`;
}

function validIdentity(value: unknown): value is SessionIdentity {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Partial<SessionIdentity>;
  return typeof candidate.tenantId === 'string'
    && candidate.tenantId.trim() !== ''
    && typeof candidate.userId === 'string'
    && candidate.userId.trim() !== '';
}

function readStoredSession(): SessionSnapshot | undefined {
  const serialized = localStorage.getItem(AUTH_SESSION_KEY);
  if (serialized === null) return undefined;
  try {
    const candidate = JSON.parse(serialized) as Partial<StoredSession>;
    if (candidate.version !== AUTH_SESSION_VERSION
      || (candidate.token !== null && typeof candidate.token !== 'string')
      || typeof candidate.generation !== 'string'
      || !candidate.generation
      || (candidate.identity !== null && !validIdentity(candidate.identity))
      || (candidate.expiresAt !== null && typeof candidate.expiresAt !== 'string')) return undefined;
    return {
      token: candidate.token,
      generation: candidate.generation,
      identity: candidate.identity,
      expiresAt: candidate.expiresAt,
    } as SessionSnapshot;
  } catch {
    return undefined;
  }
}

function sessionSnapshot(): SessionSnapshot {
  const serialized = localStorage.getItem(AUTH_SESSION_KEY);
  const stored = readStoredSession();
  if (stored) return stored;
  return {
    token: serialized === null ? localStorage.getItem(TOKEN_KEY) : null,
    generation: legacyGeneration,
    identity: null,
    expiresAt: null,
  };
}

function identityKey(identity: SessionIdentity | null) {
  return identity ? `${identity.tenantId}:${identity.userId}` : '';
}

function sessionKey(snapshot: SessionSnapshot) {
  return JSON.stringify([
    snapshot.token,
    snapshot.generation,
    identityKey(snapshot.identity),
    snapshot.expiresAt,
  ]);
}

function sameSession(left: SessionSnapshot, right: SessionSnapshot) {
  return sessionKey(left) === sessionKey(right);
}

function sessionChangedAbort() {
  return new DOMException('身份会话已变更。', 'AbortError');
}

function abortStaleRequests(current: SessionSnapshot) {
  for (const request of activeRequests) {
    if (!sameSession(request.snapshot, current) && !request.controller.signal.aborted) {
      request.controller.abort(sessionChangedAbort());
    }
  }
}

function dispatchSessionExpired(snapshot: SessionSnapshot) {
  const key = sessionKey(snapshot);
  if (lastExpiredSessionKey === key) return;
  lastExpiredSessionKey = key;
  window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT, {
    detail: {
      generation: snapshot.generation,
      identity: snapshot.identity,
    },
  }));
}

function writeSession(snapshot: SessionSnapshot) {
  const stored: StoredSession = { version: AUTH_SESSION_VERSION, ...snapshot };
  localStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(stored));
  if (snapshot.token) localStorage.setItem(TOKEN_KEY, snapshot.token);
  else localStorage.removeItem(TOKEN_KEY);
  localSessionObservers.forEach((observe) => observe(snapshot));
  abortStaleRequests(snapshot);
  return snapshot;
}

function clearSession() {
  const current = sessionSnapshot();
  const cleared = current.token || current.identity || current.expiresAt
    ? writeSession({
      token: null,
      generation: nextGeneration('clear'),
      identity: null,
      expiresAt: null,
    })
    : current;
  dispatchSessionExpired(cleared);
  return cleared;
}

function expireForMismatch(current = sessionSnapshot()) {
  abortStaleRequests(current);
  dispatchSessionExpired(current);
}

function bindSessionIdentity(
  identity: SessionIdentity,
  expiresAt: string,
  expected: SessionSnapshot,
) {
  const current = sessionSnapshot();
  if (!sameSession(current, expected)) {
    expireForMismatch(current);
    throw sessionChangedAbort();
  }
  const expiresAtEpoch = Date.parse(expiresAt);
  if (!validIdentity(identity)
    || (current.identity && identityKey(current.identity) !== identityKey(identity))
    || !Number.isFinite(expiresAtEpoch)
    || expiresAtEpoch <= Date.now()) {
    clearSession();
    throw sessionChangedAbort();
  }
  const currentExpiry = current.expiresAt ? Date.parse(current.expiresAt) : Number.POSITIVE_INFINITY;
  const effectiveExpiresAt = Number.isFinite(currentExpiry) && currentExpiry < expiresAtEpoch
    ? current.expiresAt
    : expiresAt;
  if (current.identity
    && identityKey(current.identity) === identityKey(identity)
    && current.expiresAt === effectiveExpiresAt) return current;
  return writeSession({
    token: current.token,
    generation: nextGeneration('identity'),
    identity: { tenantId: identity.tenantId.trim(), userId: identity.userId.trim() },
    expiresAt: effectiveExpiresAt,
  });
}

export const session = {
  token: () => sessionSnapshot().token,
  generation: () => sessionSnapshot().generation,
  identity: () => sessionSnapshot().identity,
  snapshot: sessionSnapshot,
  isCurrent: (snapshot: SessionSnapshot) => sameSession(snapshot, sessionSnapshot()),
  save: (token: string, identity?: SessionIdentity, expiresAt?: string) => {
    const previous = sessionSnapshot();
    const next = writeSession({
      token,
      generation: nextGeneration('login'),
      identity: identity
        ? { tenantId: identity.tenantId.trim(), userId: identity.userId.trim() }
        : null,
      expiresAt: expiresAt || null,
    });
    lastExpiredSessionKey = undefined;
    if (previous.token) dispatchSessionExpired(next);
    return next;
  },
  bindIdentity: bindSessionIdentity,
  clear: clearSession,
  expireIfNeeded: (now = Date.now()) => {
    const current = sessionSnapshot();
    if (!current.token || !current.expiresAt) return false;
    const expiry = Date.parse(current.expiresAt);
    if (Number.isFinite(expiry) && expiry > now) return false;
    clearSession();
    return true;
  },
  synchronize: () => {
    let observed = sessionKey(sessionSnapshot());
    const observeLocalWrite = (snapshot: SessionSnapshot) => {
      observed = sessionKey(snapshot);
    };
    const synchronize = (event: StorageEvent) => {
      if (event.key !== null && event.key !== AUTH_SESSION_KEY && event.key !== TOKEN_KEY) return;
      const current = sessionSnapshot();
      const next = sessionKey(current);
      if (next === observed) return;
      observed = next;
      expireForMismatch(current);
    };
    localSessionObservers.add(observeLocalWrite);
    window.addEventListener('storage', synchronize);
    return () => {
      localSessionObservers.delete(observeLocalWrite);
      window.removeEventListener('storage', synchronize);
    };
  },
};

function throwIfAborted(signal: AbortSignal | null | undefined) {
  if (!signal?.aborted) return;
  if (signal.reason instanceof Error) throw signal.reason;
  throw new DOMException('The operation was aborted.', 'AbortError');
}

function assertRequestSession(
  requestSession: SessionSnapshot,
  controller: AbortController,
) {
  throwIfAborted(controller.signal);
  if (session.expireIfNeeded()) throwIfAborted(controller.signal);
  if (session.isCurrent(requestSession)) return;
  expireForMismatch();
  controller.abort(sessionChangedAbort());
  throwIfAborted(controller.signal);
}

async function parseEnvelope<T>(response: Response): Promise<ApiEnvelope<T>> {
  let candidate: Partial<ApiEnvelope<T>> | undefined;
  try {
    candidate = (await response.json()) as Partial<ApiEnvelope<T>>;
  } catch {
    throw new ApiError('服务返回了无法识别的响应', response.status, 'INVALID_RESPONSE');
  }
  if (!candidate || typeof candidate !== 'object' || !candidate.header || typeof candidate.header.code !== 'string') {
    throw new ApiError('服务返回了无法识别的响应', response.status, 'INVALID_RESPONSE');
  }
  const envelope = candidate as ApiEnvelope<T>;
  if (!response.ok || envelope.header.code !== 'SUCCESS') {
    throw new ApiError(
      envelope.header.message || '请求失败',
      response.status,
      envelope.header.code || 'REQUEST_FAILED',
      envelope.header.traceId,
      envelope.body,
    );
  }
  return envelope;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<ApiEnvelope<T>> {
  throwIfAborted(init.signal);
  if (session.expireIfNeeded()) throw sessionChangedAbort();
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  const requestSession = session.snapshot();
  if (requestSession.token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${requestSession.token}`);
  }

  const controller = new AbortController();
  const abortFromCaller = () => controller.abort(init.signal?.reason);
  if (init.signal?.aborted) abortFromCaller();
  else init.signal?.addEventListener('abort', abortFromCaller, { once: true });
  const activeRequest = { controller, snapshot: requestSession };
  activeRequests.add(activeRequest);

  try {
    const response = await fetch(path, { ...init, headers, signal: controller.signal });
    assertRequestSession(requestSession, controller);
    if (response.status === 401) {
      clearSession();
      throwIfAborted(controller.signal);
    }
    const envelope = await parseEnvelope<T>(response);
    assertRequestSession(requestSession, controller);
    return envelope;
  } finally {
    activeRequests.delete(activeRequest);
    init.signal?.removeEventListener('abort', abortFromCaller);
  }
}

export async function login(
  tenantCode: string,
  username: string,
  password: string,
  signal?: AbortSignal,
): Promise<LoginResponse> {
  const response = await apiRequest<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ tenantCode, username, password }),
    signal,
  });
  throwIfAborted(signal);
  session.save(
    response.body.token,
    { tenantId: response.body.tenantId, userId: response.body.userId },
    response.body.tokenExpiresAt,
  );
  if (session.expireIfNeeded()) throw sessionChangedAbort();
  return response.body;
}

export const activateUser = (token: string, password: string) =>
  apiRequest<{ invitationId: string; userId: string; tenantId: string; status: string }>('/api/v1/auth/activate', {
    method: 'POST', body: JSON.stringify({ token, password }),
  }).then((value) => value.body);

export async function logout(accessToken: string | null, signal?: AbortSignal): Promise<void> {
  const headers = new Headers();
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  await apiRequest('/api/v1/auth/logout', { method: 'POST', headers, signal });
}

export async function getCurrentUser(signal?: AbortSignal) {
  const requestSession = session.snapshot();
  const response = await apiRequest<CurrentUser>('/api/v1/auth/me', { signal });
  session.bindIdentity(
    { tenantId: response.body.tenantId, userId: response.body.userId },
    response.body.accessTokenExpiresAt,
    requestSession,
  );
  return response.body;
}

export const getTenantFeatures = (signal?: AbortSignal) =>
  apiRequest<TenantFeature[]>('/api/v1/iam/tenant-features', { signal }).then((value) => value.body);

export const listPlatformTenants = () => listResource<IamTenant>('/api/v1/iam/tenants');
export const pagePlatformTenants = (page: number, pageSize: number, keyword = '', status = '', signal?: AbortSignal) =>
  listPage<IamTenant>('/api/v1/iam/tenants', { page, pageSize, keyword, status }, signal);
export const createPlatformTenant = (input: { code: string; name: string; planCode: string }) =>
  apiRequest<IamTenant>('/api/v1/iam/tenants', { method: 'POST', body: JSON.stringify(input) }).then((value) => value.body);
export const updatePlatformTenant = (tenantId: string, input: { name: string; planCode: string }) =>
  apiRequest(`/api/v1/iam/tenants/${encodeURIComponent(tenantId)}`, { method: 'PUT', body: JSON.stringify(input) }).then((value) => value.body);
export const changePlatformTenantStatus = (tenantId: string, status: 'ACTIVE' | 'DISABLED', reason: string) =>
  apiRequest(`/api/v1/iam/tenants/${encodeURIComponent(tenantId)}/status`, { method: 'POST', body: JSON.stringify({ status, reason }) }).then((value) => value.body);
export const listPlatformTenantFeatures = (tenantId: string, signal?: AbortSignal) =>
  listResource<TenantFeature>(`/api/v1/iam/tenants/${encodeURIComponent(tenantId)}/features`, signal);
export const updatePlatformTenantFeature = (tenantId: string, featureCode: string, enabled: boolean, reason: string) =>
  apiRequest<{ resourceId: string; status: string }>(`/api/v1/iam/tenants/${encodeURIComponent(tenantId)}/features/${encodeURIComponent(featureCode)}`, {
    method: 'PUT', body: JSON.stringify({ enabled, reason }),
  }).then((value) => value.body);

export const getPlatformSummary = (signal?: AbortSignal) =>
  apiRequest<SummaryResult>('/api/v1/summary/platform-overview', { signal }).then((value) => value.body);

export const getTenantSummary = (signal?: AbortSignal) =>
  apiRequest<SummaryResult>('/api/v1/summary/tenant-workbench', { signal }).then((value) => value.body);

export const listResource = <T = Record<string, unknown>>(path: string, signal?: AbortSignal) =>
  apiRequest<T[]>(path, { signal }).then((value) => value.body);

export const listPage = <T>(
  path: string,
  parameters: Record<string, string | number | undefined>,
  signal?: AbortSignal,
) => {
  const query = new URLSearchParams();
  Object.entries(parameters).forEach(([key, value]) => { if (value !== undefined && value !== '') query.set(key, String(value)); });
  return apiRequest<PageResponse<T> | T[]>(`${path}?${query.toString()}`, { signal }).then((value) => {
    if (!Array.isArray(value.body)) return value.body;
    const page = Number(parameters.page || 1); const pageSize = Number(parameters.pageSize || value.body.length || 10);
    return { items: value.body, page, pageSize, total: value.body.length, totalPages: value.body.length ? 1 : 0 };
  });
};

export const submitDelegatedTenantAction = (
  tenantId: string,
  input: {
    actionType: 'ASSIGN_TENANT_ADMIN' | 'REPLACE_ROLE_PERMISSIONS';
    targetRoleId?: string;
    targetUserId?: string;
    permissionCodes: string[];
    reason: string;
    idempotencyKey: string;
  },
) => apiRequest<DelegatedTenantActionResult>(
  `/api/v1/iam/tenants/${encodeURIComponent(tenantId)}/delegated-admin-actions`,
  { method: 'POST', body: JSON.stringify(input) },
).then((value) => value.body);

export const listApprovalTasks = (
  input: { page: number; pageSize: number; status?: string; riskLevel?: string; sourceContext?: string; keyword?: string },
  signal?: AbortSignal,
) => listPage<ApprovalTask>('/api/v1/approvals/tasks', input, signal);

export const getApprovalTask = (taskId: string) =>
  apiRequest<ApprovalTask>(`/api/v1/approvals/tasks/${encodeURIComponent(taskId)}`).then((value) => value.body);

export const decideApprovalTask = (
  taskId: string,
  action: 'APPROVE' | 'REJECT',
  comment: string,
  expectedEvidenceDigest: string,
) =>
  apiRequest<ApprovalDecisionResult>(`/api/v1/approvals/tasks/${encodeURIComponent(taskId)}/actions`, {
    method: 'POST',
    body: JSON.stringify({ action, comment, expectedEvidenceDigest }),
  }).then((value) => value.body);

export const listIamRoles = (signal?: AbortSignal) => listResource<IamRole>('/api/v1/iam/roles', signal);
export const listIamUsers = (page: number, pageSize: number, signal?: AbortSignal) =>
  listPage<IamUserItem>('/api/v1/iam/users', { page, pageSize }, signal);
export const createUserInvitation = (input: { username: string; displayName: string; email: string; roleId: string }) =>
  apiRequest<{ invitationId: string; userId: string; status: string; deliveryStatus: string; expiresAt: string }>('/api/v1/iam/user-invitations', {
    method: 'POST', body: JSON.stringify(input),
  }).then((value) => value.body);
export const listUserInvitations = (signal?: AbortSignal) => listResource<UserInvitationItem>('/api/v1/iam/user-invitations', signal);
export const resendUserInvitation = (invitationId: string) => apiRequest(`/api/v1/iam/user-invitations/${encodeURIComponent(invitationId)}/resend`, { method: 'POST' }).then((value) => value.body);
export const revokeUserInvitation = (invitationId: string, reason: string) => apiRequest(`/api/v1/iam/user-invitations/${encodeURIComponent(invitationId)}/revoke`, {
  method: 'POST',
  body: JSON.stringify({ reason }),
}).then((value) => value.body);
export const listIamPermissions = () => listResource<IamPermission>('/api/v1/iam/permissions');
export const listRolePermissions = (roleId: string) =>
  listResource<IamPermission>(`/api/v1/iam/roles/${encodeURIComponent(roleId)}/permissions`);
export const replaceRolePermissions = (roleId: string, permissionCodes: string[], reason: string) =>
  apiRequest<{ resourceId: string; status: string }>(`/api/v1/iam/roles/${encodeURIComponent(roleId)}/permissions`, {
    method: 'PUT',
    body: JSON.stringify({ permissionCodes, reason }),
  }).then((value) => value.body);

export const listModelProviders = (signal?: AbortSignal) => listResource<ModelProviderItem>('/api/v1/model-providers', signal);
export const listModelConfigs = (providerId: string, signal?: AbortSignal) => listResource<ModelConfigItem>(`/api/v1/model-providers/${encodeURIComponent(providerId)}/models`, signal);
export const listEmbeddingModelOptions = (signal?: AbortSignal) => listResource<{ modelConfigId: string; label: string; providerName: string; modelCode: string; embeddingDimension?: number }>('/api/v1/model-providers/embedding-options', signal);
type ModelProviderCreateInput = {
  requestId: string;
  providerName: string;
  providerType: string;
  apiEndpoint: string;
  authType: string;
  credentialMaterialInput: string | null;
};
type CreateModelProviderResult = {
  providerId: string;
  status: 'PENDING_APPROVAL';
  approvalRequestId: string;
};
type ModelConfigMutationInput = {
  modelCode: string;
  modelName: string;
  modelType: string;
  contextWindow: number;
  embeddingDimension: number | null;
  inputPricePer1k: string;
  outputPricePer1k: string;
  options: { providerKind: string; maxTokens?: number; temperature?: number; topP?: number };
};
type ModelConfigCreateInput = ModelConfigMutationInput & { requestId: string };
type CreateModelConfigResult = {
  providerId: string;
  modelConfigId: string;
  modelCode: string;
  modelType: string;
  status: 'PENDING_APPROVAL' | 'INACTIVE';
  approvalRequestId?: string | null;
  connectivityStatus: 'NOT_TESTED';
};
type ModelMutationEvidence = {
  providerId: string;
  modelConfigId: string;
  status: string;
  version: number;
};
type ModelConnectivityTestResult = ModelMutationEvidence & {
  connected: boolean;
  connectivityStatus: 'PASSED' | 'FAILED';
  message?: string | null;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === 'string' && value.trim() !== '';
}

function hasApprovalEvidence(body: Record<string, unknown>, status: string) {
  return status === 'PENDING_APPROVAL'
    ? isNonBlankString(body.approvalRequestId)
    : body.approvalRequestId == null || isNonBlankString(body.approvalRequestId);
}

function hasAdvancedVersion(body: Record<string, unknown>, expectedVersion: number) {
  return typeof body.version === 'number'
    && Number.isSafeInteger(body.version)
    && body.version > expectedVersion;
}

function invalidMutationResponse(envelope: ApiEnvelope<unknown>): never {
  throw new ApiError(
    '服务返回了无法识别的操作结果',
    200,
    'INVALID_RESPONSE',
    envelope.header.traceId,
  );
}

function parseCreateModelProviderResult(
  envelope: ApiEnvelope<unknown>,
): CreateModelProviderResult {
  const body = envelope.body;
  if (!isRecord(body)
    || !isNonBlankString(body.providerId)
    || body.status !== 'PENDING_APPROVAL'
    || !isNonBlankString(body.approvalRequestId)) {
    return invalidMutationResponse(envelope);
  }
  return body as CreateModelProviderResult;
}

function parseCreateModelConfigResult(
  envelope: ApiEnvelope<unknown>,
  providerId: string,
  input: ModelConfigCreateInput,
): CreateModelConfigResult {
  const body = envelope.body;
  if (!isRecord(body)
    || body.providerId !== providerId
    || !isNonBlankString(body.modelConfigId)
    || body.modelCode !== input.modelCode
    || body.modelType !== input.modelType
    || (body.status !== 'PENDING_APPROVAL' && body.status !== 'INACTIVE')
    || !hasApprovalEvidence(body, body.status)
    || body.connectivityStatus !== 'NOT_TESTED') {
    return invalidMutationResponse(envelope);
  }
  return body as CreateModelConfigResult;
}

function parseProviderDisableResult(
  envelope: ApiEnvelope<unknown>,
  providerId: string,
  expectedVersion: number,
): ModelProviderItem {
  const body = envelope.body;
  if (!isRecord(body)
    || body.providerId !== providerId
    || body.status !== 'DISABLED'
    || !hasAdvancedVersion(body, expectedVersion)) {
    return invalidMutationResponse(envelope);
  }
  return body as unknown as ModelProviderItem;
}

function parseModelMutationResult(
  envelope: ApiEnvelope<unknown>,
  expected: {
    providerId: string;
    modelConfigId: string;
    expectedVersion: number;
    allowedStatuses: readonly string[];
  },
): ModelMutationEvidence {
  const body = envelope.body;
  if (!isRecord(body)
    || body.providerId !== expected.providerId
    || body.modelConfigId !== expected.modelConfigId
    || !expected.allowedStatuses.includes(String(body.status))
    || !hasAdvancedVersion(body, expected.expectedVersion)) {
    return invalidMutationResponse(envelope);
  }
  return body as ModelMutationEvidence;
}

function parseConnectivityResult(
  envelope: ApiEnvelope<unknown>,
  providerId: string,
  modelConfigId: string,
  expectedVersion: number,
): ModelConnectivityTestResult {
  const body = parseModelMutationResult(envelope, {
    providerId,
    modelConfigId,
    expectedVersion,
    allowedStatuses: ['ACTIVE', 'INACTIVE'],
  });
  const candidate = body as unknown as Record<string, unknown>;
  if (typeof candidate.connected !== 'boolean'
    || (candidate.connectivityStatus !== 'PASSED' && candidate.connectivityStatus !== 'FAILED')
    || (candidate.connected && candidate.connectivityStatus !== 'PASSED')
    || (!candidate.connected && candidate.connectivityStatus !== 'FAILED')
    || (candidate.message != null && typeof candidate.message !== 'string')) {
    return invalidMutationResponse(envelope);
  }
  return candidate as unknown as ModelConnectivityTestResult;
}

export const createModelProvider = (input: ModelProviderCreateInput) =>
  apiRequest<unknown>('/api/v1/model-providers', {
    method: 'POST',
    body: JSON.stringify(input),
  }).then(parseCreateModelProviderResult);

export const disableModelProvider = (providerId: string, reason: string, expectedVersion: number) =>
  apiRequest<unknown>(`/api/v1/model-providers/${encodeURIComponent(providerId)}/disable`, {
    method: 'POST',
    body: JSON.stringify({ reason, expectedVersion }),
  }).then((value) => ({
    body: parseProviderDisableResult(value, providerId, expectedVersion),
    traceId: value.header.traceId,
  }));

export const createModelConfig = (providerId: string, input: ModelConfigCreateInput) =>
  apiRequest<unknown>(`/api/v1/model-providers/${encodeURIComponent(providerId)}/models`, {
    method: 'POST',
    body: JSON.stringify(input),
  }).then((value) => parseCreateModelConfigResult(value, providerId, input));

export const updateModelConfig = (providerId: string, modelConfigId: string, input: ModelConfigMutationInput, expectedVersion: number) =>
  apiRequest<unknown>(`/api/v1/model-providers/${encodeURIComponent(providerId)}/models/${encodeURIComponent(modelConfigId)}`, {
    method: 'PUT',
    body: JSON.stringify({ ...input, expectedVersion }),
  }).then((value) => parseModelMutationResult(value, {
    providerId,
    modelConfigId,
    expectedVersion,
    allowedStatuses: ['PENDING_APPROVAL', 'INACTIVE'],
  }));

export const disableModelConfig = (providerId: string, modelConfigId: string, reason: string, expectedVersion: number) =>
  apiRequest<unknown>(`/api/v1/model-providers/${encodeURIComponent(providerId)}/models/${encodeURIComponent(modelConfigId)}/disable`, {
    method: 'POST',
    body: JSON.stringify({ reason, expectedVersion }),
  }).then((value) => ({
    body: parseModelMutationResult(value, {
      providerId,
      modelConfigId,
      expectedVersion,
      allowedStatuses: ['DEPRECATED'],
    }),
    traceId: value.header.traceId,
  }));

export const testModelConnectivity = (providerId: string, modelConfigId: string, expectedVersion: number) =>
  apiRequest<unknown>(`/api/v1/model-providers/${encodeURIComponent(providerId)}/models/${encodeURIComponent(modelConfigId)}/connectivity-test`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion }),
  }).then((value) => parseConnectivityResult(value, providerId, modelConfigId, expectedVersion));

export const listAiApplications = (signal?: AbortSignal) =>
  apiRequest<AiApplicationItem[]>('/api/v1/ai-applications', { signal }).then((value) => value.body);

export const runApplicationPublishPreflight = (applicationId: string, publishRequestId: string, signal?: AbortSignal) =>
  apiRequest<PublishPreflightResult>(`/api/v1/ai-applications/${encodeURIComponent(applicationId)}/publish/preflight`, {
    method: 'POST',
    body: JSON.stringify({ publishRequestId }),
    signal,
  }).then((value) => value.body);

export const publishApplication = (applicationId: string, publishRequestId: string, reason: string) =>
  apiRequest<Record<string, unknown>>(`/api/v1/ai-applications/${encodeURIComponent(applicationId)}/publish`, {
    method: 'POST',
    body: JSON.stringify({ publishRequestId, reason }),
  }).then((value) => value.body);

export const listChatRunDeadLetters = (limit = 20, signal?: AbortSignal) =>
  apiRequest<ChatRunDeadLetterItem[]>(`/api/v1/chat-runs/dead-letters?limit=${encodeURIComponent(String(limit))}`, {
    signal,
  }).then((value) => parseChatRunDeadLetters(value.body));

export const replayChatRunDeadLetter = (
  runId: string,
  replayRequestId: string,
  reason: string,
  signal?: AbortSignal,
) => apiRequest<ChatRunManualReplayResult>(
  `/api/v1/chat-runs/${encodeURIComponent(runId)}/dead-letter/replay`,
  {
    method: 'POST',
    body: JSON.stringify({ replayRequestId, reason }),
    signal,
  },
).then((value) => value.body);

export const createChatSession = (applicationId: string, signal?: AbortSignal) =>
  apiRequest<{ sessionId: string }>(`/api/v1/ai-applications/${encodeURIComponent(applicationId)}/chat-sessions`, {
    method: 'POST',
    body: JSON.stringify({ title: '新会话' }),
    signal,
  }).then((value) => value.body);

export const listChatMessages = (sessionId: string, signal?: AbortSignal) =>
  listResource<Record<string, unknown>>(`/api/v1/chat-sessions/${encodeURIComponent(sessionId)}/messages`, signal);

export const sendChatMessage = (
  sessionId: string,
  clientMessageId: string,
  content: string,
  signal?: AbortSignal,
) =>
  apiRequest<SendChatMessageResult>(`/api/v1/chat-sessions/${encodeURIComponent(sessionId)}/messages`, {
    method: 'POST',
    body: JSON.stringify({ clientMessageId, content }),
    signal,
  }).then((value) => value.body);

export const getDebugEvidence = (
  applicationId: string,
  assistantMessageId: string,
  signal?: AbortSignal,
) =>
  apiRequest<Record<string, unknown>>(
    `/api/v1/ai-applications/${encodeURIComponent(applicationId)}/debug-runs/${encodeURIComponent(assistantMessageId)}?view=summary`,
    { signal },
  ).then((value) => value.body);

export const submitChatMessageFeedback = (
  sessionId: string,
  assistantMessageId: string,
  rating: 'HELPFUL' | 'UNHELPFUL',
  signal?: AbortSignal,
) => apiRequest<ChatMessageFeedbackResult>(
  `/api/v1/chat-sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(assistantMessageId)}/feedback`,
  { method: 'POST', body: JSON.stringify({ rating, labels: [], comment: null }), signal },
).then((value) => value.body);

export const createPermissionRequest = (input: {
  resourceType: string;
  resourceId: string;
  permissionCode: string;
  reason: string;
}, signal?: AbortSignal) => apiRequest<PermissionRequestItem>('/api/v1/permission-requests', {
  method: 'POST', body: JSON.stringify(input), signal,
}).then((value) => value.body);

export const listPermissionRequests = (
  input: { page: number; pageSize: number; status?: PermissionRequestItem['status'] },
  signal?: AbortSignal,
) => listPage<PermissionRequestItem>('/api/v1/permission-requests', input, signal);

export const decidePermissionRequest = (requestId: string, action: 'APPROVE' | 'REJECT', comment: string) =>
  apiRequest<PermissionRequestItem>(`/api/v1/permission-requests/${encodeURIComponent(requestId)}/actions`, {
    method: 'POST', body: JSON.stringify({ action, comment }),
  }).then((value) => value.body);

export const createKnowledgeBase = (input: { name: string; visibility: string; description: string; embeddingModelConfigId: string }) =>
  apiRequest<Record<string, unknown>>('/api/v1/knowledge-bases', { method: 'POST', body: JSON.stringify(input) }).then((value) => value.body);

export const listKnowledgeDocuments = (knowledgeBaseId: string) =>
  listResource<Record<string, unknown>>(`/api/v1/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/documents`);

export const uploadKnowledgeDocument = (knowledgeBaseId: string, file: File) => {
  const body = new FormData();
  body.set('file', file);
  return apiRequest<Record<string, unknown>>(`/api/v1/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/documents`, { method: 'POST', body }).then((value) => value.body);
};

export const parseKnowledgeDocument = (knowledgeBaseId: string, documentId: string) =>
  apiRequest<Record<string, unknown>>(`/api/v1/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/documents/${encodeURIComponent(documentId)}/parse`, { method: 'POST' }).then((value) => value.body);

export const indexKnowledgeDocument = (knowledgeBaseId: string, documentId: string) =>
  apiRequest<Record<string, unknown>>(`/api/v1/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/documents/${encodeURIComponent(documentId)}/index`, {
    method: 'POST',
  }).then((value) => value.body);

export const cancelKnowledgeDocument = (knowledgeBaseId: string, documentId: string, reason: string) =>
  apiRequest<Record<string, unknown>>(`/api/v1/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/documents/${encodeURIComponent(documentId)}/cancel`, {
    method: 'POST', body: JSON.stringify({ reason }),
  }).then((value) => value.body);

export function errorDestination(error: unknown): string | undefined {
  if (!(error instanceof ApiError)) return undefined;
  if (error.status === 401) return '/login';
  if (error.status === 403) return '/403';
  if (/FEATURE|TENANTFEATURE/i.test(error.code)) return '/feature-disabled';
  return undefined;
}
