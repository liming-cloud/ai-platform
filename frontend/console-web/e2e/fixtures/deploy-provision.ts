import { execFileSync } from 'node:child_process';
import { request, type APIRequestContext } from '@playwright/test';
import {
  APPROVAL_TASK_DISCOVERY_GRACE_MS,
  approvalTaskDiscoveryExpired,
  assertConsistentApprovalDecision,
  buildDeployCreationCommand,
  modelConfigApprovalTitles,
  modelConfigRecoveryAction,
  observableModelDefinitionMatches,
  providerRecoveryAction,
  selectExactPendingApprovalTask,
  type ApprovalDecisionContract,
  type ApprovalTaskExpectation,
  type PendingApprovalTaskContract,
} from '../../src/deploy-provision-contract';

type RuntimeEnvironment = Readonly<Record<string, string | undefined>>;
declare const process: { readonly env: RuntimeEnvironment };

type JsonRecord = Record<string, unknown>;

interface ApiEnvelope<T> {
  header?: {
    code?: string;
    message?: string;
    traceId?: string;
  };
  body?: T;
}

class DeployApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly traceId: string,
  ) {
    super(message);
    this.name = 'DeployApiError';
  }
}

interface LoginResult {
  token: string;
  userId: string;
  tenantId: string;
  displayName: string;
  roleCodes: string[];
  tokenExpiresAt: string;
}

interface AuthenticatedIdentity extends LoginResult {
  username: string;
}

interface ModelProvider {
  providerId: string;
  providerCode: string;
  providerName: string;
  endpoint: string;
  status: string;
  approvalRequestId?: string | null;
  version: number;
}

interface CreatedModelProvider {
  providerId: string;
  status: string;
  approvalRequestId: string;
}

interface ModelConfig {
  providerId: string;
  modelConfigId: string;
  modelCode: string;
  modelName: string;
  modelType: string;
  contextWindow: number;
  embeddingDimension?: number | null;
  inputPricePer1k: string | number;
  outputPricePer1k: string | number;
  providerKind: string;
  temperature?: string | number | null;
  topP?: string | number | null;
  maxTokens?: number | null;
  status: string;
  approvalRequestId?: string | null;
  connectivityStatus: string;
  connectivityMessage?: string | null;
  version: number;
}

interface CreatedModelConfig {
  providerId: string;
  modelConfigId: string;
  modelCode: string;
  modelType: string;
  status: string;
  approvalRequestId: string;
  connectivityStatus: string;
}

interface ConnectivityResult {
  providerId: string;
  modelConfigId: string;
  status: string;
  connected: boolean;
  connectivityStatus: string;
  message: string;
  version: number;
}

interface ApprovalTask extends PendingApprovalTaskContract {
  requesterId: string;
  approverId?: string | null;
  status: string;
  traceId: string;
}

type ApprovalDecision = ApprovalDecisionContract;

interface PageResult<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

interface KnowledgeBase {
  knowledgeBaseId: string;
  name: string;
  visibility: string;
  status: string;
  embeddingModelConfigId: string;
}

interface CreatedKnowledgeBase {
  knowledgeBaseId: string;
  status: string;
}

interface AiApplication {
  applicationId: string;
  applicationCode: string;
  name: string;
  description?: string;
  applicationType?: string;
  modelConfigId: string;
  knowledgeBaseIds: string[];
  systemPrompt?: string;
  publicationStatus: string;
  activeReleaseId?: string | null;
  activeReleaseVersion?: number | null;
}

interface PublishPreflight {
  applicationId: string;
  publishRequestId: string;
  passed: boolean;
  blockers: unknown[];
  warnings: unknown[];
  traceId: string;
}

interface ModelConfigDefinition {
  modelCode: string;
  modelName: string;
  modelType: 'CHAT' | 'EMBEDDING';
  contextWindow: number;
  embeddingDimension: number | null;
  inputPricePer1k: number;
  outputPricePer1k: number;
  options: JsonRecord;
}

const SUCCESS_CODE = 'SUCCESS';
const MODEL_PROVIDER_NAME = 'E2E 本地确定性运行时 v1';
const MODEL_PROVIDER_ENDPOINT = 'local://deterministic';
const MODEL_PROVIDER_KIND = 'local-deterministic';
const MODEL_PROVIDER_FIXTURE_KEY = 'local-deterministic-v1';
const CHAT_MODEL_CODE = 'e2e-local-chat-v1';
const EMBEDDING_MODEL_CODE = 'e2e-local-embedding-v1';
const DEFAULT_TIMEOUT_MS = 60_000;
const POLL_INTERVAL_MS = 1_000;

export const DEPLOY_KNOWLEDGE_BASE_NAME = 'E2E 业务用户公开知识库 v1';
export const DEPLOY_APPLICATION_NAME = 'E2E 业务知识问答 v1';
export const DEPLOY_ROLE_JOURNEY_RUN_ID = environmentText(process.env, 'E2E_ROLE_JOURNEY_RUN_ID')
  || 'role-journey-unconfigured';
export const DEPLOY_CANDIDATE_ROLE_NAME = `D09 业务观察员 ${DEPLOY_ROLE_JOURNEY_RUN_ID}`;
export const DEPLOY_CANDIDATE_APPROVER_USERNAME = `d09-approver-${DEPLOY_ROLE_JOURNEY_RUN_ID}`;
export const DEPLOY_CANDIDATE_INVITATION_EMAIL = `d09-${DEPLOY_ROLE_JOURNEY_RUN_ID}@example.invalid`;
export const DEPLOY_KNOWLEDGE_RECOVERY_FILE_NAME = `D09-FAILED-${DEPLOY_ROLE_JOURNEY_RUN_ID}.txt`;
export const DEPLOY_KNOWLEDGE_CANCELLATION_FILE_NAME = `D09-CANCEL-${DEPLOY_ROLE_JOURNEY_RUN_ID}.txt`;
export const DEPLOY_KNOWLEDGE_CANCELLATION_REASON = `D09 固定验收取消 ${DEPLOY_ROLE_JOURNEY_RUN_ID}`;
export const DEPLOY_KNOWLEDGE_EVIDENCE_FILE_NAME = `D09-EVIDENCE-${DEPLOY_ROLE_JOURNEY_RUN_ID}.txt`;
export const DEPLOY_BUSINESS_QUESTION = `D09EVIDENCE ${DEPLOY_ROLE_JOURNEY_RUN_ID} enterprise continuity`;

const CHAT_MODEL: ModelConfigDefinition = {
  modelCode: CHAT_MODEL_CODE,
  modelName: 'E2E 本地确定性对话模型 v1',
  modelType: 'CHAT',
  contextWindow: 8192,
  embeddingDimension: null,
  inputPricePer1k: 0,
  outputPricePer1k: 0,
  options: {
    providerKind: MODEL_PROVIDER_KIND,
    temperature: 0,
    maxTokens: 512,
  },
};

const EMBEDDING_MODEL: ModelConfigDefinition = {
  modelCode: EMBEDDING_MODEL_CODE,
  modelName: 'E2E 本地确定性向量模型 v1',
  modelType: 'EMBEDDING',
  contextWindow: 8192,
  embeddingDimension: 64,
  inputPricePer1k: 0,
  outputPricePer1k: 0,
  options: {
    providerKind: MODEL_PROVIDER_KIND,
    dimensions: 64,
  },
};

const APPLICATION_DEFINITION = {
  applicationType: 'KNOWLEDGE_QA',
  name: DEPLOY_APPLICATION_NAME,
  description: '隔离 deploy 基线的业务用户真实问答验收应用。',
  systemPrompt: '只根据当前用户有权访问的知识范围回答；没有依据时明确说明无法回答。',
} as const;

function environmentText(env: RuntimeEnvironment, key: string) {
  const value = env[key]?.trim();
  return value || undefined;
}

function provisionTimeout(env: RuntimeEnvironment) {
  const raw = environmentText(env, 'E2E_PROVISION_TIMEOUT_MS');
  if (!raw) return DEFAULT_TIMEOUT_MS;
  const parsed = Number(raw);
  if (!Number.isInteger(parsed) || parsed < 5_000 || parsed > 300_000) {
    throw new Error('E2E_PROVISION_TIMEOUT_MS must be an integer between 5000 and 300000.');
  }
  return parsed;
}

function summarized(value: unknown) {
  const serialized = JSON.stringify(value);
  if (!serialized) return String(value);
  return serialized.length > 1_500 ? `${serialized.slice(0, 1_500)}…` : serialized;
}

function roleJourneyFixtureInvocation(env: RuntimeEnvironment) {
  const required = environmentText(env, 'E2E_REQUIRE_ROLE_JOURNEYS');
  if (required !== 'true') return undefined;
  const project = environmentText(env, 'E2E_CANDIDATE_PROJECT');
  const envFile = environmentText(env, 'E2E_DEPLOY_ENV_FILE');
  const fixture = environmentText(env, 'E2E_ROLE_JOURNEY_FIXTURE');
  const runId = environmentText(env, 'E2E_ROLE_JOURNEY_RUN_ID');
  const tenantCode = environmentText(env, 'E2E_TENANT_CODE');
  if (!project || !envFile || !fixture || !runId || !tenantCode) {
    throw new Error(
      'D-09 candidate provisioning requires E2E_CANDIDATE_PROJECT, E2E_DEPLOY_ENV_FILE, '
      + 'E2E_ROLE_JOURNEY_FIXTURE, E2E_ROLE_JOURNEY_RUN_ID and E2E_TENANT_CODE.',
    );
  }
  return { fixture, project, envFile, runId, tenantCode };
}

function executeRoleJourneyFixture(
  env: RuntimeEnvironment,
  mode: 'prepare-approver' | 'inject',
) {
  const invocation = roleJourneyFixtureInvocation(env);
  if (!invocation) return undefined;
  const output = execFileSync('sh', [
    invocation.fixture,
    mode,
    invocation.project,
    invocation.envFile,
    invocation.runId,
    invocation.tenantCode,
  ], {
    encoding: 'utf8',
    maxBuffer: 1_048_576,
  });
  const serialized = output.trim().split('\n').filter(Boolean).at(-1);
  if (!serialized) throw new Error('D-09 candidate fixture returned no evidence.');
  let evidence: JsonRecord;
  try {
    evidence = JSON.parse(serialized) as JsonRecord;
  } catch {
    throw new Error(`D-09 candidate fixture returned invalid JSON: ${serialized.slice(0, 1_000)}`);
  }
  return { evidence, runId: invocation.runId };
}

function provisionRoleJourneyApprover(env: RuntimeEnvironment) {
  const result = executeRoleJourneyFixture(env, 'prepare-approver');
  if (!result) return undefined;
  const { evidence, runId } = result;
  if (
    evidence.runId !== runId
    || evidence.approverUsername !== `d09-approver-${runId}`
    || typeof evidence.approverUserId !== 'string'
    || !evidence.approverUserId.match(/^[1-9][0-9]*$/)
    || evidence.prepared !== true
  ) {
    throw new Error(`D-09 candidate approver fixture returned incomplete evidence: ${summarized(evidence)}`);
  }
  return {
    approverUsername: evidence.approverUsername,
    approverUserId: evidence.approverUserId,
  };
}

function provisionRoleJourneyCandidate(env: RuntimeEnvironment) {
  const result = executeRoleJourneyFixture(env, 'inject');
  if (!result) return undefined;
  const { evidence, runId } = result;
  if (
    evidence.runId !== runId
    || evidence.approverUsername !== `d09-approver-${runId}`
    || typeof evidence.approverUserId !== 'string'
    || typeof evidence.roleId !== 'string'
    || typeof evidence.failedDocumentId !== 'string'
    || typeof evidence.cancellableDocumentId !== 'string'
    || typeof evidence.cancellableJobId !== 'string'
    || evidence.cancellableFileName !== DEPLOY_KNOWLEDGE_CANCELLATION_FILE_NAME
    || typeof evidence.evidenceDocumentId !== 'string'
  ) {
    throw new Error(`D-09 candidate fixture returned incomplete evidence: ${summarized(evidence)}`);
  }
  return evidence;
}

async function apiRequest<T>(
  context: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  data?: unknown,
) {
  const response = await context.fetch(path, {
    method,
    data,
    failOnStatusCode: false,
  });
  const raw = await response.text();
  let envelope: ApiEnvelope<T>;
  try {
    envelope = JSON.parse(raw) as ApiEnvelope<T>;
  } catch {
    throw new Error(`${method} ${path} returned non-JSON HTTP ${response.status()}: ${raw.slice(0, 1_000)}`);
  }
  const code = envelope.header?.code;
  if (!response.ok() || code !== SUCCESS_CODE) {
    throw new DeployApiError(
      `${method} ${path} failed: HTTP ${response.status()}, code=${code || 'missing'}, `
      + `message=${envelope.header?.message || 'missing'}, trace=${envelope.header?.traceId || 'missing'}`,
      code || 'missing',
      envelope.header?.traceId || 'missing',
    );
  }
  if (!Object.prototype.hasOwnProperty.call(envelope, 'body')) {
    throw new Error(`${method} ${path} returned SUCCESS without a body; trace=${envelope.header?.traceId || 'missing'}`);
  }
  return envelope.body as T;
}

async function login(
  context: APIRequestContext,
  tenantCode: string,
  username: string,
  password: string,
  expectedUserId: string,
) {
  const result = await apiRequest<LoginResult>(context, 'POST', '/api/v1/auth/login', {
    tenantCode,
    username,
    password,
  });
  if (!result.token || result.userId !== expectedUserId) {
    throw new Error(
      `Deploy identity ${username} resolved to userId=${result.userId || 'missing'}; expected ${expectedUserId}.`,
    );
  }
  return { ...result, username };
}

async function authenticatedContext(baseURL: string, identity: AuthenticatedIdentity) {
  return request.newContext({
    baseURL,
    timeout: 15_000,
    extraHTTPHeaders: {
      Accept: 'application/json',
      Authorization: `Bearer ${identity.token}`,
    },
  });
}

async function sleep() {
  await new Promise<void>((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
}

async function listPendingModelHubTasks(context: APIRequestContext) {
  const first = await apiRequest<PageResult<ApprovalTask>>(
    context,
    'GET',
    '/api/v1/approvals/tasks?status=PENDING&sourceContext=model-hub&page=1&pageSize=100',
  );
  const tasks = [...first.items];
  for (let page = 2; page <= first.totalPages; page += 1) {
    const next = await apiRequest<PageResult<ApprovalTask>>(
      context,
      'GET',
      `/api/v1/approvals/tasks?status=PENDING&sourceContext=model-hub&page=${page}&pageSize=100`,
    );
    tasks.push(...next.items);
  }
  return tasks;
}

async function approveAndWaitForStatus<T extends { status: string; approvalRequestId?: string | null }>(params: {
  description: string;
  resourceType: 'model_provider' | 'model_config';
  resourceId: string;
  approvalRequestId?: string | null;
  expectedApprovalType: ApprovalTaskExpectation['approvalType'];
  expectedTitles: readonly string[];
  requester: AuthenticatedIdentity;
  approver: AuthenticatedIdentity;
  approverContext: APIRequestContext;
  readResource: () => Promise<T | undefined>;
  acceptedStatuses: ReadonlySet<string>;
  terminalFailureStatuses: ReadonlySet<string>;
  timeoutMs: number;
}) {
  const deadline = Date.now() + params.timeoutMs;
  let lastStatus = 'MISSING';
  let lastTaskSummary = 'no approval task observed';
  let pendingWithoutTaskSince: number | undefined;

  while (Date.now() < deadline) {
    const resource = await params.readResource();
    lastStatus = resource?.status || 'MISSING';
    if (resource && params.acceptedStatuses.has(resource.status)) return resource;
    if (resource && params.terminalFailureStatuses.has(resource.status)) {
      throw new Error(`${params.description} reached terminal status ${resource.status}.`);
    }

    const tasks = await listPendingModelHubTasks(params.approverContext);
    const expectedApprovalRequestId = params.approvalRequestId?.trim();
    const currentApprovalRequestId = resource?.approvalRequestId?.trim();
    if (resource?.status === 'PENDING_APPROVAL' && !expectedApprovalRequestId) {
      throw new Error(`${params.description} is pending without an expected approval request ID.`);
    }
    if (resource?.status === 'PENDING_APPROVAL' && currentApprovalRequestId !== expectedApprovalRequestId) {
      throw new Error(
        `${params.description} approval request changed from ${expectedApprovalRequestId} `
        + `to ${currentApprovalRequestId || 'missing'}; provisioning is fail-closed.`,
      );
    }
    const expectation: ApprovalTaskExpectation = {
      sourceContext: 'model-hub',
      approvalRequestId: expectedApprovalRequestId || '',
      resourceType: params.resourceType,
      resourceId: params.resourceId,
      approvalType: params.expectedApprovalType,
      titles: params.expectedTitles,
    };
    const task = selectExactPendingApprovalTask(tasks, expectation);
    lastTaskSummary = summarized(tasks.map((candidate) => ({
      taskId: candidate.taskId,
      approvalRequestId: candidate.approvalRequestId,
      title: candidate.title,
      approvalType: candidate.approvalType,
      sourceContext: candidate.sourceContext,
      resourceType: candidate.sourceResourceType,
      resourceId: candidate.sourceResourceId,
      requesterId: candidate.requesterId,
    })));

    if (task) {
      pendingWithoutTaskSince = undefined;
      if (task.requesterId !== params.requester.userId) {
        throw new Error(
          `${params.description} approval requester=${task.requesterId}; expected admin=${params.requester.userId}.`,
        );
      }
      if (task.requesterId === params.approver.userId) {
        throw new Error(
          `${params.description} would self-approve as userId=${params.approver.userId}; provisioning is fail-closed.`,
        );
      }
      if (task.evidenceSchema !== 'model-approval-evidence/v1'
        || !task.evidenceDigest?.match(/^[0-9a-f]{64}$/)
        || !task.targetDigest?.match(/^[0-9a-f]{64}$/)) {
        throw new Error(`${params.description} approval evidence is missing or unsupported; provisioning is fail-closed.`);
      }
      const decision = await apiRequest<ApprovalDecision>(
        params.approverContext,
        'POST',
        `/api/v1/approvals/tasks/${encodeURIComponent(task.taskId)}/actions`,
        {
          action: 'APPROVE',
          comment: `deploy E2E 由 ${params.approver.username} 独立审批 ${params.description}`,
          expectedEvidenceDigest: task.evidenceDigest,
        },
      );
      assertConsistentApprovalDecision(decision, task, expectation);
    } else if (resource?.status === 'PENDING_APPROVAL') {
      const now = Date.now();
      pendingWithoutTaskSince ??= now;
      if (approvalTaskDiscoveryExpired(pendingWithoutTaskSince, now)) {
        throw new Error(
          `${params.description} remained PENDING_APPROVAL without its exact pending approval task for `
          + `${APPROVAL_TASK_DISCOVERY_GRACE_MS}ms; pendingTasks=${lastTaskSummary}`,
        );
      }
    } else {
      pendingWithoutTaskSince = undefined;
    }
    await sleep();
  }

  throw new Error(
    `${params.description} did not reach ${[...params.acceptedStatuses].join('/')} within ${params.timeoutMs}ms; `
    + `lastStatus=${lastStatus}; pendingTasks=${lastTaskSummary}`,
  );
}

async function listProviders(context: APIRequestContext) {
  return apiRequest<ModelProvider[]>(context, 'GET', '/api/v1/model-providers');
}

async function ensureProvider(
  adminContext: APIRequestContext,
  approverContext: APIRequestContext,
  admin: AuthenticatedIdentity,
  approver: AuthenticatedIdentity,
  timeoutMs: number,
) {
  const providers = await listProviders(adminContext);
  const matchingKind = providers.filter((provider) => (
    provider.providerCode.toLowerCase() === MODEL_PROVIDER_KIND
  ));
  if (matchingKind.length > 1) {
    throw new Error(
      `Deploy baseline has multiple ${MODEL_PROVIDER_KIND} providers; clean the baseline before provisioning.`,
    );
  }
  let provider = matchingKind[0];

  if (!provider) {
    const created = await apiRequest<CreatedModelProvider>(
      adminContext,
      'POST',
      '/api/v1/model-providers',
      buildDeployCreationCommand('provider', MODEL_PROVIDER_FIXTURE_KEY, {
        providerName: MODEL_PROVIDER_NAME,
        providerType: MODEL_PROVIDER_KIND,
        apiEndpoint: MODEL_PROVIDER_ENDPOINT,
        authType: 'no_auth',
        credentialMaterialInput: null,
      }),
    );
    provider = {
      providerId: created.providerId,
      providerCode: MODEL_PROVIDER_KIND,
      providerName: MODEL_PROVIDER_NAME,
      endpoint: MODEL_PROVIDER_ENDPOINT,
      status: created.status,
      approvalRequestId: created.approvalRequestId,
      version: 0,
    };
  } else {
    const definitionMatches = provider.providerName === MODEL_PROVIDER_NAME
      && provider.endpoint === MODEL_PROVIDER_ENDPOINT;
    const recoveryAction = providerRecoveryAction(provider.status, definitionMatches);
    if (recoveryAction === 'FAIL_DRIFT') {
      throw new Error(
        `Idempotent E2E provider ${MODEL_PROVIDER_KIND} has a stale name or endpoint; `
        + 'use a clean deploy baseline or increment its fixture version.',
      );
    }
    if (recoveryAction === 'FAIL_TERMINAL') {
      throw new Error(
        `Idempotent E2E provider ${MODEL_PROVIDER_KIND} is DISABLED; `
        + 'use a clean deploy baseline or increment its fixture version.',
      );
    }
    if (recoveryAction === 'FAIL_UNSUPPORTED') {
      throw new Error(
        `Idempotent E2E provider ${MODEL_PROVIDER_KIND} has unsupported status ${provider.status}.`,
      );
    }
  }

  return approveAndWaitForStatus({
    description: `model provider ${provider.providerId}`,
    resourceType: 'model_provider',
    resourceId: provider.providerId,
    approvalRequestId: provider.approvalRequestId,
    expectedApprovalType: 'model_provider_change',
    expectedTitles: [`创建模型供应商 ${MODEL_PROVIDER_NAME}`],
    requester: admin,
    approver,
    approverContext,
    readResource: async () => (await listProviders(adminContext))
      .find((candidate) => candidate.providerId === provider?.providerId),
    acceptedStatuses: new Set(['ACTIVE']),
    terminalFailureStatuses: new Set(['DISABLED']),
    timeoutMs,
  });
}

async function listModelConfigs(context: APIRequestContext, providerId: string) {
  return apiRequest<ModelConfig[]>(
    context,
    'GET',
    `/api/v1/model-providers/${encodeURIComponent(providerId)}/models`,
  );
}

function modelDefinitionMatches(model: ModelConfig, definition: ModelConfigDefinition) {
  return observableModelDefinitionMatches(model, definition);
}

async function ensureModelConfig(
  adminContext: APIRequestContext,
  approverContext: APIRequestContext,
  admin: AuthenticatedIdentity,
  approver: AuthenticatedIdentity,
  providerId: string,
  definition: ModelConfigDefinition,
  timeoutMs: number,
) {
  let models = await listModelConfigs(adminContext, providerId);
  let model = models.find((candidate) => candidate.modelCode === definition.modelCode);
  let expectedApprovalTitles: readonly string[] = [`创建模型配置 ${definition.modelName}`];
  if (!model) {
    const created = await apiRequest<CreatedModelConfig>(
      adminContext,
      'POST',
      `/api/v1/model-providers/${encodeURIComponent(providerId)}/models`,
      buildDeployCreationCommand(
        'model',
        `${MODEL_PROVIDER_FIXTURE_KEY}.${definition.modelCode}`,
        definition,
      ),
    );
    model = {
      ...definition,
      providerId: created.providerId,
      modelConfigId: created.modelConfigId,
      providerKind: MODEL_PROVIDER_KIND,
      status: created.status,
      approvalRequestId: created.approvalRequestId,
      connectivityStatus: created.connectivityStatus,
      version: 0,
    };
  } else {
    const definitionMatches = modelDefinitionMatches(model, definition);
    const recoveryAction = modelConfigRecoveryAction(model.status, definitionMatches);
    expectedApprovalTitles = modelConfigApprovalTitles(recoveryAction, definition.modelName);
    if (recoveryAction === 'FAIL_TERMINAL') {
      throw new Error(
        `Idempotent E2E model ${definition.modelCode} is ${model.status}; use a clean deploy baseline or increment its fixture version.`,
      );
    }
    if (recoveryAction === 'FAIL_PENDING_DRIFT') {
      throw new Error(
        `Idempotent E2E model ${definition.modelCode} is PENDING_APPROVAL with a stale definition; `
        + 'finish or clean that approval before changing the fixture version.',
      );
    }
    if (recoveryAction === 'FAIL_UNSUPPORTED') {
      throw new Error(`Idempotent E2E model ${definition.modelCode} has unsupported status ${model.status}.`);
    }
    if (recoveryAction === 'UPDATE' || recoveryAction === 'RESUBMIT') {
      model = await apiRequest<ModelConfig>(
        adminContext,
        'PUT',
        `/api/v1/model-providers/${encodeURIComponent(providerId)}/models/${encodeURIComponent(model.modelConfigId)}`,
        { ...definition, expectedVersion: model.version },
      );
    }
  }

  const approved = await approveAndWaitForStatus({
    description: `model config ${model.modelCode} (${model.modelConfigId})`,
    resourceType: 'model_config',
    resourceId: model.modelConfigId,
    approvalRequestId: model.approvalRequestId,
    expectedApprovalType: 'model_config_change',
    expectedTitles: expectedApprovalTitles,
    requester: admin,
    approver,
    approverContext,
    readResource: async () => (await listModelConfigs(adminContext, providerId))
      .find((candidate) => candidate.modelConfigId === model?.modelConfigId),
    acceptedStatuses: new Set(['INACTIVE', 'ACTIVE']),
    terminalFailureStatuses: new Set(['REJECTED', 'DEPRECATED', 'DISABLED']),
    timeoutMs,
  });

  if (approved.status === 'ACTIVE' && approved.connectivityStatus === 'PASSED') return approved;
  const connectivity = await apiRequest<ConnectivityResult>(
    adminContext,
    'POST',
    `/api/v1/model-providers/${encodeURIComponent(providerId)}/models/`
      + `${encodeURIComponent(approved.modelConfigId)}/connectivity-test`,
    { expectedVersion: approved.version },
  );
  if (
    !connectivity.connected
    || connectivity.status !== 'ACTIVE'
    || connectivity.connectivityStatus !== 'PASSED'
  ) {
    throw new Error(`Connectivity failed for ${definition.modelCode}: ${summarized(connectivity)}`);
  }

  const deadline = Date.now() + timeoutMs;
  let last: ModelConfig | undefined;
  while (Date.now() < deadline) {
    models = await listModelConfigs(adminContext, providerId);
    last = models.find((candidate) => candidate.modelConfigId === approved.modelConfigId);
    if (last?.status === 'ACTIVE' && last.connectivityStatus === 'PASSED') return last;
    await sleep();
  }
  throw new Error(
    `Model ${definition.modelCode} connectivity result was not query-visible within ${timeoutMs}ms: ${summarized(last)}`,
  );
}

async function ensureKnowledgeBase(
  adminContext: APIRequestContext,
  embeddingModelConfigId: string,
  timeoutMs: number,
) {
  const knowledgeBases = await apiRequest<KnowledgeBase[]>(adminContext, 'GET', '/api/v1/knowledge-bases');
  let knowledgeBase = knowledgeBases.find((candidate) => (
    candidate.name === DEPLOY_KNOWLEDGE_BASE_NAME
    && candidate.status !== 'DISABLED'
  ));
  const definition = {
    name: DEPLOY_KNOWLEDGE_BASE_NAME,
    visibility: 'public',
    description: '隔离 deploy 基线的业务用户公开知识范围；无需外部模型密钥。',
    embeddingModelConfigId,
  };

  if (!knowledgeBase) {
    const deadline = Date.now() + timeoutMs;
    let lastProjectionError: DeployApiError | undefined;
    while (Date.now() < deadline) {
      try {
        const created = await apiRequest<CreatedKnowledgeBase>(
          adminContext,
          'POST',
          '/api/v1/knowledge-bases',
          definition,
        );
        return { ...definition, ...created };
      } catch (error) {
        if (!(error instanceof DeployApiError) || error.code !== 'AIP-KB-EMBEDDINGDENIED') throw error;
        lastProjectionError = error;
        await sleep();
      }
    }
    throw new Error(
      `Embedding-model policy was not projected before knowledge-base creation within ${timeoutMs}ms; `
      + `lastError=${lastProjectionError?.message || 'missing'}`,
    );
  }
  if (
    knowledgeBase.visibility !== definition.visibility
    || knowledgeBase.embeddingModelConfigId !== embeddingModelConfigId
  ) {
    const deadline = Date.now() + timeoutMs;
    let updated = false;
    let lastProjectionError: DeployApiError | undefined;
    while (Date.now() < deadline && !updated) {
      try {
        await apiRequest<JsonRecord>(
          adminContext,
          'PUT',
          `/api/v1/knowledge-bases/${encodeURIComponent(knowledgeBase.knowledgeBaseId)}`,
          definition,
        );
        updated = true;
      } catch (error) {
        if (!(error instanceof DeployApiError) || error.code !== 'AIP-KB-EMBEDDINGDENIED') throw error;
        lastProjectionError = error;
        await sleep();
      }
    }
    if (!updated) {
      throw new Error(
        `Embedding-model policy was not projected before knowledge-base update within ${timeoutMs}ms; `
        + `lastError=${lastProjectionError?.message || 'missing'}`,
      );
    }
    knowledgeBase = { ...knowledgeBase, ...definition };
  }
  return knowledgeBase;
}

function sameKnowledgeBaseIds(actual: string[], expectedId: string) {
  return actual.length === 1 && actual[0] === expectedId;
}

async function ensureApplication(
  adminContext: APIRequestContext,
  businessContext: APIRequestContext,
  businessUserId: string,
  chatModelConfigId: string,
  knowledgeBaseId: string,
  timeoutMs: number,
) {
  const desired = {
    ...APPLICATION_DEFINITION,
    modelConfigId: chatModelConfigId,
    knowledgeBaseIds: [knowledgeBaseId],
  };
  const applications = await apiRequest<AiApplication[]>(adminContext, 'GET', '/api/v1/ai-applications');
  let application = applications.find((candidate) => (
    candidate.name === DEPLOY_APPLICATION_NAME
    && candidate.modelConfigId === chatModelConfigId
    && sameKnowledgeBaseIds(candidate.knowledgeBaseIds, knowledgeBaseId)
  )) || applications.find((candidate) => candidate.name === DEPLOY_APPLICATION_NAME);

  if (!application) {
    application = await apiRequest<AiApplication>(adminContext, 'POST', '/api/v1/ai-applications', desired);
  } else if (
    application.modelConfigId !== chatModelConfigId
    || !sameKnowledgeBaseIds(application.knowledgeBaseIds, knowledgeBaseId)
    || application.description !== APPLICATION_DEFINITION.description
    || application.systemPrompt !== APPLICATION_DEFINITION.systemPrompt
  ) {
    application = await apiRequest<AiApplication>(
      adminContext,
      'PUT',
      `/api/v1/ai-applications/${encodeURIComponent(application.applicationId)}`,
      {
        name: desired.name,
        description: desired.description,
        modelConfigId: desired.modelConfigId,
        knowledgeBaseIds: desired.knowledgeBaseIds,
        systemPrompt: desired.systemPrompt,
      },
    );
  }

  await apiRequest<JsonRecord>(
    adminContext,
    'PUT',
    `/api/v1/ai-applications/${encodeURIComponent(application.applicationId)}/access-policy`,
    {
      userIds: [businessUserId],
      roleIds: [],
      reason: '隔离 deploy E2E 业务用户真实任务授权',
    },
  );

  if (!application.activeReleaseId || !application.activeReleaseVersion) {
    const deadline = Date.now() + timeoutMs;
    let attempt = 0;
    let lastPreflight: PublishPreflight | undefined;
    while (Date.now() < deadline) {
      attempt += 1;
      const publishRequestId = `e2e-${application.applicationId}-${Date.now()}-${attempt}`;
      lastPreflight = await apiRequest<PublishPreflight>(
        adminContext,
        'POST',
        `/api/v1/ai-applications/${encodeURIComponent(application.applicationId)}/publish/preflight`,
        { publishRequestId },
      );
      if (lastPreflight.passed) {
        application = await apiRequest<AiApplication>(
          adminContext,
          'POST',
          `/api/v1/ai-applications/${encodeURIComponent(application.applicationId)}/publish`,
          {
            publishRequestId,
            reason: '隔离 deploy E2E 发布真实业务用户问答应用',
          },
        );
        break;
      }
      await sleep();
    }
    if (application.publicationStatus !== 'PUBLISHED' || !application.activeReleaseId || !application.activeReleaseVersion) {
      throw new Error(
        `Application preflight did not pass within ${timeoutMs}ms: ${summarized(lastPreflight)}`,
      );
    }
  }

  const visibleApplications = await apiRequest<AiApplication[]>(businessContext, 'GET', '/api/v1/ai-applications');
  const visible = visibleApplications.find((candidate) => candidate.applicationId === application.applicationId);
  if (!visible || visible.publicationStatus !== 'PUBLISHED' || !visible.activeReleaseId || !visible.activeReleaseVersion) {
    throw new Error(
      `Published application ${application.applicationId} is not visible to business user ${businessUserId}: `
      + summarized(visibleApplications.map((candidate) => ({
        applicationId: candidate.applicationId,
        name: candidate.name,
        publicationStatus: candidate.publicationStatus,
      }))),
    );
  }
  return application;
}

export async function provisionDeployAcceptance(env: RuntimeEnvironment = process.env) {
  const tenantCode = environmentText(env, 'E2E_TENANT_CODE');
  const password = env.E2E_PASSWORD;
  if (!tenantCode || !password) {
    throw new Error('Deploy E2E provisioning requires E2E_TENANT_CODE and E2E_PASSWORD.');
  }

  const baseURL = environmentText(env, 'E2E_BASE_URL') || 'http://127.0.0.1:5173';
  const timeoutMs = provisionTimeout(env);
  const adminUsername = environmentText(env, 'E2E_PLATFORM_ADMIN_USERNAME')
    || environmentText(env, 'E2E_USERNAME')
    || 'admin';
  const preparedApprover = provisionRoleJourneyApprover(env);
  const approverUsername = preparedApprover?.approverUsername
    || environmentText(env, 'E2E_PLATFORM_APPROVER_USERNAME');
  const approverUserId = preparedApprover?.approverUserId
    || environmentText(env, 'E2E_PLATFORM_APPROVER_USER_ID');
  const approverPassword = preparedApprover
    ? password
    : environmentText(env, 'E2E_PLATFORM_APPROVER_PASSWORD');
  if (!approverUsername || !approverUserId || !approverPassword) {
    throw new Error(
      'Deploy E2E provisioning requires a distinct platform approver from the candidate fixture '
      + 'or E2E_PLATFORM_APPROVER_USERNAME/E2E_PLATFORM_APPROVER_USER_ID/'
      + 'E2E_PLATFORM_APPROVER_PASSWORD.',
    );
  }
  const businessUsername = environmentText(env, 'E2E_BUSINESS_USER_USERNAME') || 'user';
  const contexts: APIRequestContext[] = [];

  try {
    const anonymous = await request.newContext({ baseURL, timeout: 15_000 });
    contexts.push(anonymous);
    const admin = await login(anonymous, tenantCode, adminUsername, password, '1001');
    const approver = await login(
      anonymous,
      tenantCode,
      approverUsername,
      approverPassword,
      approverUserId,
    );
    const business = await login(anonymous, tenantCode, businessUsername, password, '1003');
    if (admin.userId === approver.userId || !approver.roleCodes.includes('platform-admin')) {
      throw new Error(
        `Deploy approver ${approver.username} must be a distinct platform-admin identity.`,
      );
    }
    const adminContext = await authenticatedContext(baseURL, admin);
    const approverContext = await authenticatedContext(baseURL, approver);
    const businessContext = await authenticatedContext(baseURL, business);
    contexts.push(adminContext, approverContext, businessContext);

    const provider = await ensureProvider(adminContext, approverContext, admin, approver, timeoutMs);
    const chatModel = await ensureModelConfig(
      adminContext,
      approverContext,
      admin,
      approver,
      provider.providerId,
      CHAT_MODEL,
      timeoutMs,
    );
    const embeddingModel = await ensureModelConfig(
      adminContext,
      approverContext,
      admin,
      approver,
      provider.providerId,
      EMBEDDING_MODEL,
      timeoutMs,
    );
    const knowledgeBase = await ensureKnowledgeBase(adminContext, embeddingModel.modelConfigId, timeoutMs);
    await ensureApplication(
      adminContext,
      businessContext,
      business.userId,
      chatModel.modelConfigId,
      knowledgeBase.knowledgeBaseId,
      timeoutMs,
    );
    const roleJourneyCandidate = provisionRoleJourneyCandidate(env);
    if (roleJourneyCandidate && roleJourneyCandidate.approverUserId !== approver.userId) {
      throw new Error('D-09 candidate approver changed between early preparation and full provisioning.');
    }
  } finally {
    await Promise.all(contexts.map(async (context) => context.dispose()));
  }
}
