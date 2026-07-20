import { apiRequest } from './api';

export type StudioApplicationAccessMode = 'OWNER' | 'BUSINESS_USER' | 'GOVERNANCE_READ_ONLY';

export interface StudioApplicationCapabilities {
  canEditConfiguration: boolean;
  canRunPublishPreflight: boolean;
  canPublish: boolean;
  canRollback: boolean;
  canManageAccessPolicy: boolean;
  canReadReleaseEvidence: boolean;
}

export interface StudioApplication {
  applicationId: string;
  applicationCode: string;
  name: string;
  description: string | null;
  applicationType: string | null;
  modelConfigId: string | null;
  knowledgeBaseIds: string[];
  systemPrompt: string | null;
  publicationStatus: string;
  activeReleaseId?: string | null;
  activeReleaseVersion?: number | null;
  createdAt?: string;
  updatedAt?: string | null;
  accessMode: StudioApplicationAccessMode;
  capabilities: StudioApplicationCapabilities;
}

export interface ApplicationRelease {
  releaseId: string;
  version: number;
  active: boolean;
  operation: 'PUBLISH' | 'ROLLBACK';
  sourceReleaseId: string | null;
  modelConfigId: string;
  knowledgeBaseIds: string[];
  systemPromptSummary: string | null;
  systemPromptSha256: string;
  allowedUserCount: number;
  publishedBy: string;
  reason: string;
  traceId: string;
  createdAt: string;
}

export interface ApplicationConfigurationInput {
  name: string;
  description: string;
  modelConfigId: string;
  knowledgeBaseIds: string[];
  systemPrompt: string;
}

export interface CreateApplicationInput extends ApplicationConfigurationInput {
  applicationType: string;
}

export interface CreateApplicationResult {
  applicationId: string;
  applicationCode: string;
  name: string;
  modelConfigId: string;
  knowledgeBaseIds: string[];
  publicationStatus: string;
  createdAt?: string;
  accessMode: StudioApplicationAccessMode;
  capabilities: StudioApplicationCapabilities;
}

export const APPLICATION_CAPABILITY_KEYS: Array<keyof StudioApplicationCapabilities> = [
  'canEditConfiguration',
  'canRunPublishPreflight',
  'canPublish',
  'canRollback',
  'canManageAccessPolicy',
  'canReadReleaseEvidence',
];

export function parseApplicationCapabilities(value: unknown): StudioApplicationCapabilities | null {
  if (!value || typeof value !== 'object') return null;
  const capabilities = value as Record<keyof StudioApplicationCapabilities, unknown>;
  return APPLICATION_CAPABILITY_KEYS.some((key) => typeof capabilities[key] !== 'boolean')
    ? null
    : capabilities as StudioApplicationCapabilities;
}

function parseCreateApplicationResult(value: unknown): CreateApplicationResult {
  const row = value as Record<string, unknown>;
  if (!value
    || typeof value !== 'object'
    || row.accessMode !== 'OWNER'
    || !parseApplicationCapabilities(row.capabilities)
    || typeof row.applicationId !== 'string'
    || !row.applicationId.trim()) {
    throw new Error('服务端未确认访问能力，应用未写入。');
  }
  return row as unknown as CreateApplicationResult;
}

export interface AccessPolicyInput {
  userIds: string[];
  roleIds: string[];
  reason: string;
}

export interface AccessPolicyResult {
  applicationId: string;
  authorizedUserCount: number;
  authorizedRoleCount: number;
  auditEventId: string;
  updatedAt: string;
}

export interface PublishPreflightIssue {
  code: string;
  message: string;
  resourceType: string;
  resourceId: string;
}

export interface StudioPublishPreflight {
  applicationId: string;
  publishRequestId: string;
  passed: boolean;
  blockers: PublishPreflightIssue[];
  warnings: PublishPreflightIssue[];
  traceId: string;
  checkedAt: string;
}

export interface StudioConfigurationOptions {
  models: Array<{ id: string; label: string }>;
  knowledgeBases: Array<{ id: string; label: string }>;
}

export const listStudioApplications = (signal?: AbortSignal) =>
  apiRequest<Array<Partial<StudioApplication> & { status?: string }>>('/api/v1/ai-applications', { signal })
    .then((response) => response.body);

export async function listStudioConfigurationOptions(signal?: AbortSignal): Promise<StudioConfigurationOptions> {
  const [providers, knowledgeBases] = await Promise.all([
    apiRequest<Array<{ providerId?: string; providerName?: string; status?: string }>>('/api/v1/model-providers', { signal })
      .then((response) => response.body),
    apiRequest<Array<{ knowledgeBaseId?: string; name?: string; status?: string }>>('/api/v1/knowledge-bases', { signal })
      .then((response) => response.body),
  ]);
  const enabledProviders = providers.filter((provider) => (
    provider.providerId && String(provider.status || '').toUpperCase() === 'ACTIVE'
  ));
  const modelGroups = await Promise.all(enabledProviders.map((provider) => (
    apiRequest<Array<{
      modelConfigId?: string;
      modelName?: string;
      modelCode?: string;
      modelType?: string;
      status?: string;
    }>>(`/api/v1/model-providers/${encodeURIComponent(String(provider.providerId))}/models`, { signal })
      .then((response) => response.body.map((model) => ({ ...model, providerName: provider.providerName })))
  )));
  const models = modelGroups.flat()
    .filter((model) => (
      model.modelConfigId
      && String(model.status || '').toUpperCase() === 'ACTIVE'
      && ['CHAT', 'REASONING'].includes(String(model.modelType || 'CHAT').toUpperCase())
    ))
    .map((model) => ({
      id: String(model.modelConfigId),
      label: [model.providerName, model.modelName || model.modelCode, model.modelConfigId].filter(Boolean).join(' / '),
    }));
  return {
    models,
    knowledgeBases: knowledgeBases
      .filter((knowledgeBase) => (
        knowledgeBase.knowledgeBaseId
        && String(knowledgeBase.status || '').toUpperCase() === 'ACTIVE'
      ))
      .map((knowledgeBase) => ({
        id: String(knowledgeBase.knowledgeBaseId),
        label: `${knowledgeBase.name || '未命名知识库'} / ${knowledgeBase.knowledgeBaseId}`,
      })),
  };
}

export const createStudioApplication = (input: CreateApplicationInput, signal?: AbortSignal) =>
  apiRequest<unknown>('/api/v1/ai-applications', {
    method: 'POST',
    body: JSON.stringify(input),
    signal,
  }).then((response) => parseCreateApplicationResult(response.body));

export const updateStudioApplication = (
  applicationId: string,
  input: ApplicationConfigurationInput,
  signal?: AbortSignal,
) => apiRequest<StudioApplication>(`/api/v1/ai-applications/${encodeURIComponent(applicationId)}`, {
  method: 'PUT',
  body: JSON.stringify(input),
  signal,
}).then((response) => response.body);

export const replaceStudioAccessPolicy = (
  applicationId: string,
  input: AccessPolicyInput,
  signal?: AbortSignal,
) => apiRequest<AccessPolicyResult>(`/api/v1/ai-applications/${encodeURIComponent(applicationId)}/access-policy`, {
  method: 'PUT',
  body: JSON.stringify(input),
  signal,
}).then((response) => response.body);

export const runStudioPublishPreflight = (
  applicationId: string,
  publishRequestId: string,
  signal?: AbortSignal,
) => apiRequest<StudioPublishPreflight>(
  `/api/v1/ai-applications/${encodeURIComponent(applicationId)}/publish/preflight`,
  { method: 'POST', body: JSON.stringify({ publishRequestId }), signal },
).then((response) => response.body);

export const publishStudioApplication = (
  applicationId: string,
  publishRequestId: string,
  reason: string,
  signal?: AbortSignal,
) => apiRequest<Partial<StudioApplication> & { status?: string }>(
  `/api/v1/ai-applications/${encodeURIComponent(applicationId)}/publish`,
  { method: 'POST', body: JSON.stringify({ publishRequestId, reason }), signal },
).then((response) => response.body);

function normalizeRelease(value: unknown): ApplicationRelease {
  const row = value && typeof value === 'object' ? value as Record<string, unknown> : {};
  const text = (field: string) => typeof row[field] === 'string' ? String(row[field]).trim() : '';
  const count = (field: string) => Number.isInteger(row[field]) && Number(row[field]) >= 0 ? Number(row[field]) : -1;
  const version = count('version');
  const operation = text('operation');
  const sourceReleaseId = row.sourceReleaseId === null ? null : text('sourceReleaseId');
  const systemPromptSummary = row.systemPromptSummary === null ? null : text('systemPromptSummary');
  const knowledgeBaseIds = Array.isArray(row.knowledgeBaseIds)
    && row.knowledgeBaseIds.every((item) => typeof item === 'string' && item.trim())
    ? row.knowledgeBaseIds.map((item) => String(item)) : null;
  const required = [
    'releaseId', 'modelConfigId',
    'systemPromptSha256', 'publishedBy', 'reason', 'traceId', 'createdAt',
  ];
  if (
    required.some((field) => !text(field))
    || version < 1
    || typeof row.active !== 'boolean'
    || !['PUBLISH', 'ROLLBACK'].includes(operation)
    || (row.sourceReleaseId !== null && !sourceReleaseId)
    || (row.systemPromptSummary !== null && !systemPromptSummary)
    || !knowledgeBaseIds
    || count('allowedUserCount') < 0
  ) throw new Error('发布版本证据无效，已禁止回滚。');
  return {
    releaseId: text('releaseId'), version, active: row.active, operation: operation as ApplicationRelease['operation'],
    sourceReleaseId, modelConfigId: text('modelConfigId'),
    knowledgeBaseIds,
    systemPromptSummary,
    systemPromptSha256: text('systemPromptSha256'),
    allowedUserCount: count('allowedUserCount'), publishedBy: text('publishedBy'),
    reason: text('reason'), traceId: text('traceId'), createdAt: text('createdAt'),
  };
}

export async function listApplicationReleases(applicationId: string, signal?: AbortSignal): Promise<ApplicationRelease[]> {
  const response = await apiRequest<unknown>(`/api/v1/ai-applications/${encodeURIComponent(applicationId)}/releases`, { signal });
  if (!Array.isArray(response.body)) throw new Error('发布版本证据无效，已禁止回滚。');
  return response.body.map(normalizeRelease);
}

export const rollbackApplicationRelease = (
  applicationId: string,
  releaseId: string,
  input: { rollbackRequestId: string; reason: string },
  signal?: AbortSignal,
) => apiRequest<Partial<StudioApplication> & { status?: string }>(
  `/api/v1/ai-applications/${encodeURIComponent(applicationId)}/releases/${encodeURIComponent(releaseId)}/rollback`,
  { method: 'POST', body: JSON.stringify(input), signal },
).then((response) => response.body);
