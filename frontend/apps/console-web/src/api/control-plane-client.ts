import {
  BrowserSessionClient,
  type BrowserLoginCredentials,
  type BrowserSessionCapability,
  type BrowserSessionGateway,
} from "@foundation/browser-session";

export type LoginCredentials = BrowserLoginCredentials;

/** 平台租户生命周期命令的最小结果，初始管理员只作为已建立资源的回显。 */
export interface TenantLifecycle {
  tenantId: string;
  tenantCode: string;
  displayName: string;
  initialAdministratorSubjectId: string;
  status: "PROVISIONING" | "ACTIVE" | "SUSPENDED";
  version: number;
}

/** 平台工作区的服务端权威读取投影，不包含初始管理员、成员、权限或正文。 */
export interface TenantLifecycleSummary {
  tenantId: string;
  tenantCode: string;
  displayName: string;
  status: "PROVISIONING" | "ACTIVE" | "SUSPENDED";
  version: number;
  createdAt: string;
  lastChangedAt: string;
}

/** 平台账户目录的最小安全摘要，不携带凭据、会话或权限快照。 */
export interface PlatformAccountSummary {
  subjectId: string;
  loginName: string;
  status: "ACTIVE" | "DISABLED";
  version: number;
  createdAt: string;
  activeRoleCodes: string[];
}

/** 平台角色写操作的最小回显，不包含权限快照或会话信息。 */
export interface PlatformRoleBinding {
  bindingId: string;
  subjectId: string;
  roleCode: string;
  status: "ACTIVE" | "REVOKED";
  version: number;
  createdAt: string;
  revokedAt: string | null;
}

/** 当前租户成员目录的最小安全投影，不包含凭据、会话或完整权限集合。 */
export interface TenantMemberSummary {
  membershipId: string;
  loginName: string;
  membershipRole: string;
  status: "ENABLED" | "DISABLED";
  permissionVersion: number;
  /** 已绑定的内置角色最多为当前发布目录大小，不属于无界数据平铺。 */
  builtInRoles: string[];
  createdAt: string;
}

/** 平台建立租户只接受已存在主体这一业务资源目标。 */
export interface TenantProvisioningInput {
  tenantCode: string;
  displayName: string;
  initialAdministratorSubjectId: string;
}

export interface KnowledgeSpace {
  id: string;
  name: string;
  description: string | null;
  status: "DRAFT" | "VERIFIED" | "PUBLISHED" | "DISABLED";
  version: number;
  createdAt: string;
}

export interface TextDataSource {
  id: string;
  knowledgeSpaceId: string;
  name: string;
  status: "DRAFT" | "VERIFIED" | "PUBLISHED" | "DISABLED";
  version: number;
  contentVersion: number;
  publishedContentVersion: number;
  createdAt: string;
}

export interface GovernedDataSourceLifecycleResult {
  dataSourceId: string;
  knowledgeSpaceId: string;
  status: string;
  version: number;
  failedOperation: "PROBE" | "SYNCHRONIZATION" | null;
  syncArtifactId: string | null;
  qualityStatus: "PASS" | "WARN" | "BLOCK" | null;
}

/** 结构化数据集登记时冻结的字段映射；对象位置始终不属于浏览器契约。 */
export interface StructuredDatasetFieldInput {
  fieldName: string;
  logicalType: "TEXT" | "NUMBER" | "BOOLEAN" | "DATE" | "DATETIME";
  sensitivityLevel: "PUBLIC" | "INTERNAL" | "CONFIDENTIAL" | "RESTRICTED";
}

/** 结构化数据集私有 CSV 登记请求；同步与质量规则不由本契约决定。 */
export interface StructuredDatasetRegistrationInput {
  knowledgeSpaceId: string;
  name: string;
  mappingVersion: string;
  contentField: string;
  titleField: string | null;
  fields: StructuredDatasetFieldInput[];
  rowFilter: "ALL_ROWS" | "NON_BLANK_CONTENT";
}

/** FILE 来源任务恢复所需的非敏感摘要，不包含对象位置、正文、摘要或凭据。 */
export interface GovernedFileDataSourceSummary {
  dataSourceId: string;
  knowledgeSpaceId: string;
  displayName: string;
  status: string;
  version: number;
  failedOperation: "PROBE" | "SYNCHRONIZATION" | null;
  publishedArtifactId: string | null;
  publishedQualityStatus: "PASS" | "WARN" | null;
  publishedAt: string | null;
}

export interface IndexVersion {
  id: string;
  knowledgeSpaceId: string;
  sourceSnapshotDigest: string;
  chunkPolicy: string;
  embeddingBindingType: "GOVERNED_CONFIGURATION" | "LEGACY_DEPLOYMENT";
  embeddingModelConfigurationId: string | null;
  embeddingModelConfigurationRevision: number | null;
  embeddingCatalogEntryId: string | null;
  embeddingCatalogEntryRevision: number | null;
  embeddingDimension: number;
  status: "BUILDING" | "READY" | "ACTIVE" | "SUPERSEDED" | "FAILED";
  version: number;
  buildDigest: string | null;
  chunkCount: number;
  failureCode: string | null;
  createdAt: string;
  readyAt: string | null;
  activatedAt: string | null;
  supersededAt: string | null;
}

export interface ModelDeployment {
  id: string;
  providerConnectionId: string;
  providerConnectionRevision: number;
  modelCode: string;
  modelType: "CHAT" | "EMBEDDING";
  displayName: string;
  capabilities: string[];
  contextWindow: number;
  outputLimit: number | null;
  verificationExpiresAt: string | null;
  status: "DRAFT" | "VERIFIED" | "PUBLISHED" | "DISABLED";
  availability: "HEALTHY" | "DEGRADED" | "UNAVAILABLE";
  nextStep: "VERIFY_CONNECTION" | "PROBE_MODEL" | "REPROBE_MODEL" | "PUBLISH" | "NONE";
  version: number;
  embeddingDimension: number | null;
  createdAt: string;
}

export interface ModelConfiguration {
  id: string;
  scope: "PLATFORM" | "TENANT" | "PERSONAL";
  displayName: string;
  catalogEntryId: string;
  modelType: "CHAT" | "EMBEDDING";
  status: "DRAFT" | "ACTIVE" | "DISABLED";
  version: number;
  credentialConfigured: boolean;
  createdAt: string;
}

/** 平台发布后可由成员选择的模型目录事实，不包含凭据或配置所有权。 */
export interface ControlledModelCatalogEntry {
  id: string;
  providerKind: string;
  modelCode: string;
  modelType: "CHAT" | "EMBEDDING";
  contextWindow: number;
  outputLimit: number | null;
  embeddingDimension: number | null;
  status: "DRAFT" | "PUBLISHED";
  version: number;
  createdAt: string;
}

/**
 * 模型发现历史的最小安全摘要。候选明细、Endpoint、凭据引用和厂商原始响应不属于列表契约。
 */
export interface ProviderModelDiscoveryHistory {
  discoveryId: string;
  providerConnectionRevision: number;
  succeeded: boolean;
  candidateCount: number;
  resultCode: string;
  elapsed: string;
  discoveredAt: string;
  expiresAt: string;
  nextStep: "DISCOVER_MODELS" | "CREATE_DEPLOYMENT";
}

/** 供应商连接的安全读取摘要；浏览器不能获得 Endpoint 或凭据引用。 */
export interface ProviderConnectionSummary {
  id: string;
  providerKind: string;
  displayName: string;
  region: string;
  authenticationType: string;
  credentialConfigured: boolean;
  status: string;
  version: number;
  lastProbeSucceeded: boolean | null;
  lastProbeCode: string | null;
  lastProbedAt: string | null;
  createdAt: string;
}

/** 创建供应商连接时浏览器唯一允许提交的业务字段与一次性 API Key。 */
export interface ProviderConnectionCreateInput {
  providerKind: "DETERMINISTIC" | "OPENAI_COMPATIBLE" | "OLLAMA";
  displayName: string;
  endpoint: string;
  region: string;
  authenticationType: "API_KEY";
  apiKey: string;
}

export type PlatformModelDiscoveryProfile =
  | "DETERMINISTIC_CATALOG"
  | "OPENAI_MODELS_API"
  | "VOLCENGINE_ARK_CONTROL_PLANE"
  | "NONE";

/** 平台连接必须显式冻结权威模型发现协议。 */
export interface PlatformProviderConnectionCreateInput extends ProviderConnectionCreateInput {
  discoveryProfile: PlatformModelDiscoveryProfile;
  discoveryAccessKeyId?: string;
  discoverySecretAccessKey?: string;
}

/** 平台范围厂商连接的安全摘要；端点和凭据永不返回浏览器。 */
export type PlatformProviderConnectionSummary = ProviderConnectionSummary & {
  discoveryProfile: PlatformModelDiscoveryProfile;
};

/** 平台模型发现的安全摘要；候选只用于后续受控部署选择。 */
export interface PlatformProviderModelDiscoverySummary {
  succeeded: boolean;
  models: Array<{
    modelCode: string;
    modelType: "CHAT" | "EMBEDDING" | null;
    capabilities: string[];
    classification: "CONFIRMED_BY_PROVIDER" | "CONFIRMED_BY_CONTROLLED_MAPPING" | "CONFIRMED_BY_ADMIN" | "REQUIRES_ADMIN_CONFIRMATION";
  }>;
}

/** 平台连接当前修订中可创建部署的安全模型候选。 */
export interface PlatformProviderModelCandidate {
  modelCode: string;
  modelType: "CHAT" | "EMBEDDING" | null;
  capabilities: string[];
  classification: "CONFIRMED_BY_PROVIDER" | "CONFIRMED_BY_CONTROLLED_MAPPING" | "CONFIRMED_BY_ADMIN" | "REQUIRES_ADMIN_CONFIRMATION";
}

/** 平台模型部署的安全摘要；端点、凭据和供应商原始响应永不进入浏览器。 */
export interface PlatformModelDeploymentSummary {
  id: string;
  providerConnectionId: string;
  providerConnectionRevision: number;
  modelCode: string;
  displayName: string;
  modelType: "CHAT" | "EMBEDDING";
  capabilities: string[];
  contextWindow: number;
  outputLimit: number | null;
  embeddingDimension: number | null;
  verificationExpiresAt: string | null;
  status: "DRAFT" | "VERIFIED" | "PUBLISHED" | "DISABLED";
  availability: "HEALTHY" | "DEGRADED" | "UNAVAILABLE";
  nextStep: "VERIFY_CONNECTION" | "PROBE_MODEL" | "REPROBE_MODEL" | "PUBLISH" | "NONE";
  version: number;
  createdAt: string;
}

/** 平台管理员从已发现候选创建部署时可提交的最小业务字段。 */
export interface PlatformModelDeploymentCreateInput {
  providerConnectionId: string;
  expectedConnectionVersion: number;
  modelCode: string;
  displayName: string;
  modelType: "CHAT" | "EMBEDDING";
  capabilities: string[];
  contextWindow: number;
  outputLimit: number | null;
  embeddingDimension: number | null;
}

/** 平台模型探测的安全结果。 */
export interface PlatformModelDeploymentProbeResult {
  deployment: PlatformModelDeploymentSummary;
  verified: boolean;
  observedCapabilities: string[];
  elapsed: string;
  resultCode: string;
  probedAt: string;
}

/** 连接探测只返回安全摘要与受控能力，不回传端点、凭据或厂商原始响应。 */
export interface ProviderConnectionProbeResult {
  providerConnection: ProviderConnectionSummary;
  reachable: boolean;
  capabilities: string[];
  elapsed: string;
  detailCode: string;
  probedAt: string;
}

/** 发现命令只向浏览器返回完成状态，候选模型明细留在受控后端流程中。 */
export interface ProviderModelDiscoveryResult {
  discoveryId: string;
  succeeded: boolean;
  resultCode: string;
  elapsed: string;
  discoveredAt: string;
  expiresAt: string;
}

/** 当前连接修订且尚未过期的模型候选；候选不属于发现历史列表。 */
export interface ProviderModelCandidate {
  modelCode: string;
  modelType: "CHAT" | "EMBEDDING";
  capabilities: string[];
}

/** 创建草稿部署只允许选择已发现候选及其受控参数。 */
export interface ModelDeploymentCreateInput {
  providerConnectionId: string;
  modelCode: string;
  displayName: string;
  modelType: "CHAT" | "EMBEDDING";
  capabilities: string[];
  contextWindow: number;
  outputLimit: number | null;
  embeddingDimension: number | null;
}

/** 最小真实能力探测的安全结果，不携带供应商原始响应。 */
export interface ModelDeploymentProbeResult {
  modelDeployment: ModelDeployment;
  reachable: boolean;
  capabilities: string[];
  elapsed: string;
  detailCode: string;
  probedAt: string;
}

export interface QueryUsage {
  from: string;
  to: string;
  acceptedQueries: number;
  answeredQueries: number;
  refusedQueries: number;
  failedQueries: number;
  runningQueries: number;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  dailyQuota: {
    used: number;
    limit: number;
    resetsAt: string;
  };
  concurrentQuota: {
    used: number;
    limit: number;
  };
  generatedAt: string;
}

export interface QueryAuditRecord {
  queryRunId: string;
  requestedBy: string;
  assistantVersionId: string;
  questionDigest: string;
  status: "RUNNING" | "ANSWERED" | "REFUSED" | "FAILED";
  refusalReason: string | null;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  createdAt: string;
  completedAt: string | null;
}

export type OperationsRange = "DAY_1" | "DAY_7" | "DAY_30";

export interface ResourceStatusView {
  total: number;
  statuses: Record<string, number>;
}

export interface OperationsRates {
  answerRate: number;
  refusalRate: number;
  failureRate: number;
}

export interface QueryOperationsView {
  accepted: number;
  answered: number;
  refused: number;
  failed: number;
  running: number;
  totalTokens: number;
  rates: OperationsRates;
  daily?: Array<{
    date: string;
    accepted: number;
    answered: number;
    refused: number;
    failed: number;
    totalTokens: number;
  }>;
}

export interface OperationsNoticeView {
  severity: "INFO" | "WARNING" | "CRITICAL";
  title: string;
  description: string;
  targetRoute: string;
}

interface OperationsOverviewBase {
  range: OperationsRange;
  periodFrom: string;
  periodTo: string;
  generatedAt: string;
  staleAfter: string;
  queries: QueryOperationsView;
  notices: OperationsNoticeView[];
}

export interface PlatformOperationsOverviewView extends OperationsOverviewBase {
  tenants: ResourceStatusView;
  platformAdministrators: ResourceStatusView;
  tenantAdministrators: ResourceStatusView;
  tenantUsers: ResourceStatusView;
  knowledgeSpaces: ResourceStatusView;
  dataSources: ResourceStatusView;
  indexes: ResourceStatusView;
  indexJobs: ResourceStatusView;
  platformModelConnections: ResourceStatusView;
  platformModelDeployments: ResourceStatusView;
  attentionTenantCount: number;
}

export interface PlatformTenantOperationsSummaryView {
  tenantReference: string;
  tenantName: string;
  lifecycleStatus: string;
  queries: QueryOperationsView;
  attentionRequired: boolean;
}

export interface TenantOperationsOverviewView extends OperationsOverviewBase {
  members: {
    administrators: ResourceStatusView;
    users: ResourceStatusView;
  };
  knowledgeSpaces: ResourceStatusView;
  dataSources: ResourceStatusView;
  indexes: ResourceStatusView;
  indexJobs: ResourceStatusView;
  modelConnections: ResourceStatusView;
  modelDeployments: ResourceStatusView;
  modelConfigurations: ResourceStatusView;
  assistants: number;
  assistantVersions: ResourceStatusView;
  assistantTestRuns: ResourceStatusView;
  evaluationRuns: ResourceStatusView;
  releaseDecisions: ResourceStatusView;
  quota: {
    dailyUsed: number;
    dailyLimit: number;
    concurrentUsed: number;
    concurrentLimit: number;
  };
}

export interface PlatformAccountCandidate {
  accountReference: string;
  loginName: string;
  status: string;
}

export type TenantMemberCandidate = PlatformAccountCandidate;

export interface EvaluationDatasetCandidate {
  datasetReference: string;
  entryCount: number;
  classification: string;
  createdAt: string;
}

export interface EvaluationAssistantCandidate {
  assistantId: string;
  displayName: string;
  assistantVersionId: string;
  versionNumber: number;
  publishedAt: string;
}

export interface PlatformObjectRangeCandidate {
  rangeReference: string;
  selectionRevision: number;
  displayName: string;
  objectCount: number;
}

/**
 * 已完成评测运行的受限读取投影；不包含数据集引用、输入输出、证据明细或模型路由信息。
 */
export interface EvaluationRun {
  id: string;
  assistantVersionId: string;
  assistantVersionRevision: number;
  datasetId: string;
  datasetVersion: number;
  gatePolicyVersion: string;
  metricsImplementationVersion: string;
  status: "PASSED" | "FAILED";
  expectedEvidenceCount: number;
  retrievedExpectedEvidenceCount: number;
  citationCount: number;
  correctCitationCount: number;
  expectedRefusalCount: number;
  correctRefusalCount: number;
  maximumRetrievedCandidateCount: number;
  failedEntryCount: number;
  unauthorizedEvidence: boolean;
  metricsComputable: boolean;
  recallAt10: number;
  citationCorrectness: number;
  correctRefusalRate: number;
  version: number;
  createdAt: string;
  completedAt: string;
}

export interface PageResult<T> {
  items: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
}

interface ApiErrorBody {
  code: string;
  message: string;
  traceId: string;
}

type IdentifierFactory = () => string;

/**
 * 控制平面的最小浏览器客户端，只暴露当前知识发布任务所需接口。
 */
export class ControlPlaneClient {
  private sessionActive = false;
  private sessionRevision = 0;
  private readonly sessionListeners = new Set<(active: boolean) => void>();
  private readonly browserSession: BrowserSessionGateway;

  constructor(
    private readonly fetcher: typeof fetch = (...args) => fetch(...args),
    private readonly identifierFactory: IdentifierFactory = () =>
      crypto.randomUUID(),
    private readonly baseUrl = "/api/v1",
    browserSession?: BrowserSessionGateway,
  ) {
    this.browserSession = browserSession ?? new BrowserSessionClient(
      fetcher,
      baseUrl,
    );
    this.sessionActive = browserSession !== undefined;
  }

  async login(credentials: LoginCredentials): Promise<BrowserSessionCapability> {
    const revision = ++this.sessionRevision;
    this.updateSessionActive(false);
    const capability = await this.browserSession.login(credentials);
    if (revision === this.sessionRevision) {
      this.updateSessionActive(true);
    }
    return capability;
  }

  async restoreSession(): Promise<BrowserSessionCapability | null> {
    const revision = ++this.sessionRevision;
    const capability = await this.browserSession.restore();
    if (revision === this.sessionRevision) {
      this.updateSessionActive(capability !== null);
    }
    return capability;
  }

  async logout(): Promise<void> {
    this.sessionRevision += 1;
    try {
      await this.browserSession.logout();
    } finally {
      this.updateSessionActive(false);
    }
  }

  /** 接收同源页签失效信号后只清除本页受保护状态，不重新发送注销请求。 */
  discardSession(): void {
    this.sessionRevision += 1;
    this.updateSessionActive(false);
  }

  /** 根级工作区据此在会话失效时立即撤下受保护页面。 */
  subscribeSessionChange(listener: (active: boolean) => void): () => void {
    this.sessionListeners.add(listener);
    return () => this.sessionListeners.delete(listener);
  }

  hasSession(): boolean {
    return this.sessionActive;
  }

  async provisionTenant(input: TenantProvisioningInput): Promise<TenantLifecycle> {
    return this.request<TenantLifecycle>("/platform/tenants", {
      method: "POST",
      headers: this.writeHeaders("application/json"),
      body: JSON.stringify(input),
    });
  }

  async listTenantLifecycles(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<TenantLifecycleSummary>> {
    return this.request<PageResult<TenantLifecycleSummary>>(
      `/platform/tenants?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async listPlatformAccounts(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<PlatformAccountSummary>> {
    return this.request<PageResult<PlatformAccountSummary>>(
      `/platform/accounts?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async listPlatformAccountCandidates(
    query = "",
    page = 1,
    size = 20,
  ): Promise<PageResult<PlatformAccountCandidate>> {
    const parameters = new URLSearchParams({
      purpose: "TENANT_INITIAL_ADMINISTRATOR",
      q: query.trim(),
      page: String(page),
      size: String(size),
    });
    return this.request<PageResult<PlatformAccountCandidate>>(
      `/platform/accounts/candidates?${parameters}`,
    );
  }

  async createPlatformAccount(input: { loginName: string; password: string }): Promise<PlatformAccountSummary> {
    return this.request<PlatformAccountSummary>("/platform/accounts", {
      method: "POST",
      headers: this.writeHeaders("application/json"),
      body: JSON.stringify(input),
    });
  }

  async enablePlatformAccount(subjectId: string): Promise<void> {
    return this.request<void>(
      `/platform/accounts/${encodeURIComponent(subjectId)}/enable`,
      { method: "POST" },
    );
  }

  async resetPlatformAccountPassword(subjectId: string, password: string): Promise<void> {
    return this.request<void>(
      `/platform/accounts/${encodeURIComponent(subjectId)}/password-reset`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ password }),
      },
    );
  }

  async bindPlatformRole(
    subjectId: string,
    roleCode: string,
  ): Promise<PlatformRoleBinding> {
    return this.request<PlatformRoleBinding>(
      `/platform/accounts/${encodeURIComponent(subjectId)}/role-bindings`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ role: roleCode }),
      },
    );
  }

  async revokePlatformRole(
    subjectId: string,
    roleCode: string,
  ): Promise<PlatformRoleBinding> {
    return this.request<PlatformRoleBinding>(
      `/platform/accounts/${encodeURIComponent(subjectId)}/role-bindings/${encodeURIComponent(roleCode)}`,
      { method: "DELETE" },
    );
  }

  async activateTenant(tenantId: string): Promise<TenantLifecycle> {
    return this.transitionTenant(tenantId, "activate");
  }

  async suspendTenant(tenantId: string): Promise<TenantLifecycle> {
    return this.transitionTenant(tenantId, "suspend");
  }

  async restoreTenant(tenantId: string): Promise<TenantLifecycle> {
    return this.transitionTenant(tenantId, "restore");
  }

  async getTenantOperationsOverview(
    range: OperationsRange = "DAY_7",
  ): Promise<TenantOperationsOverviewView> {
    return this.request<TenantOperationsOverviewView>(
      `/operations/overview?range=${range}`,
    );
  }

  async getPlatformOperationsOverview(
    range: OperationsRange = "DAY_7",
  ): Promise<PlatformOperationsOverviewView> {
    return this.request<PlatformOperationsOverviewView>(
      `/platform/operations/overview?range=${range}`,
    );
  }

  async listPlatformTenantOperations(
    range: OperationsRange = "DAY_7",
    page = 1,
    size = 20,
  ): Promise<PageResult<PlatformTenantOperationsSummaryView>> {
    return this.request<PageResult<PlatformTenantOperationsSummaryView>>(
      `/platform/operations/tenants?range=${range}&page=${page}&size=${size}`,
    );
  }

  async getQueryUsage(from?: string, to?: string): Promise<QueryUsage> {
    const query = new URLSearchParams();
    if (from !== undefined) {
      query.set("from", from);
    }
    if (to !== undefined) {
      query.set("to", to);
    }
    const suffix = query.size === 0 ? "" : `?${query}`;
    return this.request<QueryUsage>(`/usage${suffix}`);
  }

  async listQueryAuditRecords(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<QueryAuditRecord>> {
    return this.request<PageResult<QueryAuditRecord>>(
      `/audit-records?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async listEvaluationDatasetCandidates(
    page = 1,
    size = 20,
  ): Promise<PageResult<EvaluationDatasetCandidate>> {
    return this.request<PageResult<EvaluationDatasetCandidate>>(
      `/evaluation-datasets/candidates?page=${page}&size=${size}`,
    );
  }

  async listEvaluationAssistantCandidates(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<EvaluationAssistantCandidate>> {
    return this.request<PageResult<EvaluationAssistantCandidate>>(
      `/query-assistants?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async listPlatformObjectRangeCandidates(
    knowledgeSpaceId: string,
    page = 1,
    size = 20,
  ): Promise<PageResult<PlatformObjectRangeCandidate>> {
    return this.request<PageResult<PlatformObjectRangeCandidate>>(
      `/knowledge-spaces/${encodeURIComponent(knowledgeSpaceId)}/object-storage-ranges?page=${page}&size=${size}`,
    );
  }

  /**
   * 发起服务端评测运行；浏览器只提交已登记数据集和已发布助手版本的标识。
   */
  async executeEvaluationRun(
    datasetId: string,
    assistantVersionId: string,
  ): Promise<EvaluationRun> {
    return this.request<EvaluationRun>("/evaluation-runs", {
      method: "POST",
      headers: this.writeHeaders("application/json"),
      body: JSON.stringify({ datasetId, assistantVersionId }),
    });
  }

  async listKnowledgeSpaces(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<KnowledgeSpace>> {
    return this.request<PageResult<KnowledgeSpace>>(
      `/knowledge-spaces?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async createKnowledgeSpace(input: {
    name: string;
    description: string;
  }, idempotencyKey?: string): Promise<KnowledgeSpace> {
    return this.request<KnowledgeSpace>("/knowledge-spaces", {
      method: "POST",
      headers: this.writeHeaders("application/json", idempotencyKey),
      body: JSON.stringify(input),
    });
  }

  async createTextDataSource(
    knowledgeSpaceId: string,
    name: string,
    idempotencyKey?: string,
  ): Promise<TextDataSource> {
    return this.request<TextDataSource>("/data-sources", {
      method: "POST",
      headers: this.writeHeaders("application/json", idempotencyKey),
      body: JSON.stringify({ knowledgeSpaceId, name }),
    });
  }

  async uploadTextContent(
    knowledgeSpaceId: string,
    dataSourceId: string,
    expectedVersion: number,
    content: string,
    idempotencyKey?: string,
  ): Promise<TextDataSource> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      expectedVersion: String(expectedVersion),
    });
    return this.request<TextDataSource>(
      `/data-sources/${encodeURIComponent(dataSourceId)}/content?${query}`,
      {
        method: "POST",
        headers: this.writeHeaders(
          "text/plain; charset=UTF-8",
          idempotencyKey,
        ),
        body: content,
      },
    );
  }

  async registerFileDataSource(
    knowledgeSpaceId: string,
    name: string,
    content: string,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const query = new URLSearchParams({ knowledgeSpaceId, name });
    return this.request<GovernedDataSourceLifecycleResult>(
      `/governed-data-sources/files?${query}`,
      {
        method: "POST",
        headers: this.writeHeaders("text/plain; charset=UTF-8"),
        body: content,
      },
    );
  }

  /**
   * 上传平台私有 UTF-8 CSV 及其冻结映射；浏览器不提交对象位置或外部连接参数。
   */
  async registerStructuredDataset(
    input: StructuredDatasetRegistrationInput,
    csv: File,
    idempotencyKey?: string,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const form = new FormData();
    form.append(
      "definition",
      new Blob([JSON.stringify(input)], { type: "application/json" }),
      "definition.json",
    );
    form.append("csv", csv, csv.name);
    return this.request<GovernedDataSourceLifecycleResult>(
      "/governed-data-sources/structured-datasets",
      {
        method: "POST",
        // 浏览器负责生成 multipart boundary；手动固定 Content-Type 会导致服务端无法读取分段。
        headers: this.writeIdempotencyHeaders(idempotencyKey),
        body: form,
      },
    );
  }

  /** 登记部署已签发的对象存储范围，不传递内部位置、对象正文或凭据。 */
  async registerObjectStorageDataSource(
    knowledgeSpaceId: string,
    name: string,
    rangeId: string,
    selectionRevision: number,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      name,
      rangeId,
      selectionRevision: String(selectionRevision),
    });
    return this.request<GovernedDataSourceLifecycleResult>(
      `/governed-data-sources/object-storage?${query}`,
      { method: "POST" },
    );
  }

  async listGovernedFileDataSources(
    knowledgeSpaceId: string,
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<GovernedFileDataSourceSummary>> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      pageNumber: String(pageNumber),
      pageSize: String(pageSize),
    });
    return this.request<PageResult<GovernedFileDataSourceSummary>>(
      `/governed-data-sources/files?${query}`,
    );
  }

  /** 探测登记时冻结的平台对象范围，不提交对象位置、正文或质量结论。 */
  async probeObjectStorageDataSource(
    knowledgeSpaceId: string,
    dataSourceId: string,
    expectedVersion: number,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      expectedVersion: String(expectedVersion),
    });
    return this.request<GovernedDataSourceLifecycleResult>(
      `/governed-data-sources/object-storage/${encodeURIComponent(dataSourceId)}/probe?${query}`,
      { method: "POST" },
    );
  }

  async probeFileDataSource(
    knowledgeSpaceId: string,
    dataSourceId: string,
    expectedVersion: number,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      expectedVersion: String(expectedVersion),
    });
    return this.request<GovernedDataSourceLifecycleResult>(
      `/governed-data-sources/files/${encodeURIComponent(dataSourceId)}/probe?${query}`,
      { method: "POST" },
    );
  }

  /** 同步只使用已冻结的私有 CSV、字段映射和当前来源修订。 */
  async synchronizeStructuredDataset(
    knowledgeSpaceId: string,
    dataSourceId: string,
    expectedVersion: number,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      expectedVersion: String(expectedVersion),
    });
    return this.request<GovernedDataSourceLifecycleResult>(
      `/governed-data-sources/structured-datasets/${encodeURIComponent(dataSourceId)}/synchronization?${query}`,
      { method: "POST" },
    );
  }

  /** 发布只选择已有同步产物，浏览器不能提交质量结论或正文。 */
  async publishStructuredDatasetSyncArtifact(
    knowledgeSpaceId: string,
    dataSourceId: string,
    artifactId: string,
    expectedVersion: number,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      expectedVersion: String(expectedVersion),
    });
    return this.request<GovernedDataSourceLifecycleResult>(
      `/governed-data-sources/structured-datasets/${encodeURIComponent(dataSourceId)}/artifacts/${encodeURIComponent(artifactId)}/publish?${query}`,
      { method: "POST" },
    );
  }

  /** 同步登记时冻结的平台对象集合，不提交对象位置、正文或质量结论。 */
  async synchronizeObjectStorageDataSource(
    knowledgeSpaceId: string,
    dataSourceId: string,
    expectedVersion: number,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      expectedVersion: String(expectedVersion),
    });
    return this.request<GovernedDataSourceLifecycleResult>(
      `/governed-data-sources/object-storage/${encodeURIComponent(dataSourceId)}/synchronization?${query}`,
      { method: "POST" },
    );
  }

  /** 发布指定对象存储同步产物，质量和归属仍由服务端重新核验。 */
  async publishObjectStorageSyncArtifact(
    knowledgeSpaceId: string,
    dataSourceId: string,
    artifactId: string,
    expectedVersion: number,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      expectedVersion: String(expectedVersion),
    });
    return this.request<GovernedDataSourceLifecycleResult>(
      `/governed-data-sources/object-storage/${encodeURIComponent(dataSourceId)}/artifacts/${encodeURIComponent(artifactId)}/publish?${query}`,
      { method: "POST" },
    );
  }

  async synchronizeFileDataSource(
    knowledgeSpaceId: string,
    dataSourceId: string,
    expectedVersion: number,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      expectedVersion: String(expectedVersion),
    });
    return this.request<GovernedDataSourceLifecycleResult>(
      `/governed-data-sources/files/${encodeURIComponent(dataSourceId)}/synchronization?${query}`,
      { method: "POST" },
    );
  }

  async publishFileSyncArtifact(
    knowledgeSpaceId: string,
    dataSourceId: string,
    artifactId: string,
    expectedVersion: number,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      expectedVersion: String(expectedVersion),
    });
    return this.request<GovernedDataSourceLifecycleResult>(
      `/governed-data-sources/files/${encodeURIComponent(dataSourceId)}/artifacts/${encodeURIComponent(artifactId)}/publish?${query}`,
      { method: "POST" },
    );
  }

  async retireFileDataSource(
    knowledgeSpaceId: string,
    dataSourceId: string,
    expectedVersion: number,
  ): Promise<GovernedDataSourceLifecycleResult> {
    const query = new URLSearchParams({
      knowledgeSpaceId,
      expectedVersion: String(expectedVersion),
    });
    return this.request<GovernedDataSourceLifecycleResult>(
      `/governed-data-sources/files/${encodeURIComponent(dataSourceId)}/retire?${query}`,
      { method: "POST" },
    );
  }

  async publishTextDataSource(
    knowledgeSpaceId: string,
    dataSourceId: string,
    expectedVersion: number,
    idempotencyKey?: string,
  ): Promise<TextDataSource> {
    return this.request<TextDataSource>(
      `/data-sources/${encodeURIComponent(dataSourceId)}/publish`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json", idempotencyKey),
        body: JSON.stringify({ knowledgeSpaceId, expectedVersion }),
      },
    );
  }

  async createIndexVersion(
    knowledgeSpaceId: string,
    embeddingModelConfigurationId: string,
    idempotencyKey?: string,
  ): Promise<IndexVersion> {
    return this.request<IndexVersion>(
      `/knowledge-spaces/${encodeURIComponent(knowledgeSpaceId)}/index-versions`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json", idempotencyKey),
        body: JSON.stringify({ embeddingModelConfigurationId }),
      },
    );
  }

  async listModelDeployments(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<ModelDeployment>> {
    return this.request<PageResult<ModelDeployment>>(
      `/model-deployments?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async createModelDeployment(
    input: ModelDeploymentCreateInput,
    idempotencyKey?: string,
  ): Promise<ModelDeployment> {
    return this.request<ModelDeployment>("/model-deployments", {
      method: "POST",
      headers: this.writeHeaders("application/json", idempotencyKey),
      body: JSON.stringify(input),
    });
  }

  async probeModelDeployment(
    modelDeploymentId: string,
    expectedVersion: number,
    timeoutMillis: number,
  ): Promise<ModelDeploymentProbeResult> {
    return this.request<ModelDeploymentProbeResult>(
      `/model-deployments/${encodeURIComponent(modelDeploymentId)}/probe`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ expectedVersion, timeoutMillis }),
      },
    );
  }

  async publishModelDeployment(
    modelDeploymentId: string,
    expectedVersion: number,
  ): Promise<ModelDeployment> {
    return this.request<ModelDeployment>(
      `/model-deployments/${encodeURIComponent(modelDeploymentId)}/publish`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ expectedVersion }),
      },
    );
  }

  async disableModelDeployment(
    modelDeploymentId: string,
    expectedVersion: number,
  ): Promise<ModelDeployment> {
    return this.request<ModelDeployment>(
      `/model-deployments/${encodeURIComponent(modelDeploymentId)}/disable`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ expectedVersion }),
      },
    );
  }

  async listTenantMembers(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<TenantMemberSummary>> {
    return this.request<PageResult<TenantMemberSummary>>(
      `/tenant/members?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  /** 建立成员只指定既有全局主体；租户与操作者始终由浏览器会话恢复。 */
  async establishTenantMember(
    subjectId: string,
  ): Promise<Pick<TenantMemberSummary, "membershipId" | "status" | "permissionVersion">> {
    return this.request(
      "/tenant/members",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ subjectId }),
      },
    );
  }

  async listTenantMemberCandidates(
    query = "",
    page = 1,
    size = 20,
  ): Promise<PageResult<TenantMemberCandidate>> {
    const parameters = new URLSearchParams({
      q: query.trim(),
      page: String(page),
      size: String(size),
    });
    return this.request<PageResult<TenantMemberCandidate>>(
      `/tenant/member-candidates?${parameters}`,
    );
  }

  /** 停用前由页面展示确认弹窗；主体和租户始终由浏览器会话恢复。 */
  async disableTenantMember(membershipId: string): Promise<Pick<TenantMemberSummary, "membershipId" | "status" | "permissionVersion">> {
    return this.request(
      `/tenant/members/${encodeURIComponent(membershipId)}/disable`,
      { method: "POST" },
    );
  }

  /** 启用不会恢复旧会话；后续认证会按当前活动角色重新计算权限。 */
  async enableTenantMember(membershipId: string): Promise<Pick<TenantMemberSummary, "membershipId" | "status" | "permissionVersion">> {
    return this.request(
      `/tenant/members/${encodeURIComponent(membershipId)}/enable`,
      { method: "POST" },
    );
  }

  /** 角色变更目标由路径指定，租户、操作者和会话均由浏览器会话恢复。 */
  async bindTenantMemberRole(
    membershipId: string,
    role: string,
  ): Promise<{ membershipId: string; role: string; permissionVersion: number }> {
    return this.request(
      `/tenant/members/${encodeURIComponent(membershipId)}/role-bindings`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ role }),
      },
    );
  }

  /** 撤销会使目标成员旧租户会话失效，页面必须先取得明确确认。 */
  async revokeTenantMemberRole(
    membershipId: string,
    role: string,
  ): Promise<{ membershipId: string; role: string; permissionVersion: number; revokedAt: string }> {
    return this.request(
      `/tenant/members/${encodeURIComponent(membershipId)}/role-bindings/${encodeURIComponent(role)}`,
      { method: "DELETE" },
    );
  }

  async listModelConfigurations(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<ModelConfiguration>> {
    return this.request<PageResult<ModelConfiguration>>(
      `/model-configurations?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async listControlledModelCatalog(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<ControlledModelCatalogEntry>> {
    return this.request<PageResult<ControlledModelCatalogEntry>>(
      `/controlled-model-catalog?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  /** 平台目录只能引用已发布部署；请求中不含 Endpoint、模型编码或能力。 */
  async createControlledModelCatalogFromDeployment(
    platformDeploymentId: string,
    expectedDeploymentVersion: number,
  ): Promise<ControlledModelCatalogEntry> {
    return this.request<ControlledModelCatalogEntry>("/controlled-model-catalog", {
      method: "POST",
      headers: this.writeHeaders("application/json"),
      body: JSON.stringify({ platformDeploymentId, expectedDeploymentVersion }),
    });
  }

  async publishControlledModelCatalog(entryId: string): Promise<ControlledModelCatalogEntry> {
    return this.request<ControlledModelCatalogEntry>(
      `/controlled-model-catalog/${encodeURIComponent(entryId)}/publish`,
      { method: "POST", headers: this.writeHeaders("application/json") },
    );
  }

  async listProviderModelDiscoveries(
    providerConnectionId: string,
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<ProviderModelDiscoveryHistory>> {
    return this.request<PageResult<ProviderModelDiscoveryHistory>>(
      `/model-providers/${encodeURIComponent(providerConnectionId)}/model-discoveries?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async listCurrentProviderModelCandidates(
    providerConnectionId: string,
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<ProviderModelCandidate>> {
    return this.request<PageResult<ProviderModelCandidate>>(
      `/model-providers/${encodeURIComponent(providerConnectionId)}/current-model-candidates?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async listProviderConnections(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<ProviderConnectionSummary>> {
    return this.request<PageResult<ProviderConnectionSummary>>(
      `/model-providers?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async createProviderConnection(
    input: ProviderConnectionCreateInput,
    idempotencyKey?: string,
  ): Promise<ProviderConnectionSummary> {
    return this.request<ProviderConnectionSummary>("/model-providers", {
      method: "POST",
      headers: this.writeHeaders("application/json", idempotencyKey),
      body: JSON.stringify(input),
    });
  }

  async listPlatformProviderConnections(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<PlatformProviderConnectionSummary>> {
    return this.request<PageResult<PlatformProviderConnectionSummary>>(
      `/platform/model-supply/connections?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async createPlatformProviderConnection(
    input: PlatformProviderConnectionCreateInput,
    idempotencyKey?: string,
  ): Promise<PlatformProviderConnectionSummary> {
    return this.request<PlatformProviderConnectionSummary>("/platform/model-supply/connections", {
      method: "POST",
      headers: this.writeHeaders("application/json", idempotencyKey),
      body: JSON.stringify(input),
    });
  }

  async probePlatformProviderConnection(
    providerConnectionId: string,
    expectedVersion: number,
    modelCode: string,
    timeoutMillis = 10_000,
  ): Promise<PlatformProviderConnectionSummary> {
    return this.request<PlatformProviderConnectionSummary>(
      `/platform/model-supply/connections/${providerConnectionId}/probe`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ expectedVersion, modelCode, timeoutMillis }),
      },
    );
  }

  async discoverPlatformProviderModels(
    providerConnectionId: string,
    expectedVersion: number,
    timeoutMillis = 10_000,
  ): Promise<PlatformProviderModelDiscoverySummary> {
    return this.request<PlatformProviderModelDiscoverySummary>(
      `/platform/model-supply/connections/${providerConnectionId}/model-discovery`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ expectedVersion, timeoutMillis }),
      },
    );
  }

  async listPlatformProviderModelCandidates(
    providerConnectionId: string,
    expectedConnectionVersion: number,
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<PlatformProviderModelCandidate>> {
    const parameters = new URLSearchParams({
      expectedConnectionVersion: String(expectedConnectionVersion),
      pageNumber: String(pageNumber),
      pageSize: String(pageSize),
    });
    return this.request<PageResult<PlatformProviderModelCandidate>>(
      `/platform/model-supply/connections/${encodeURIComponent(providerConnectionId)}/model-candidates?${parameters}`,
    );
  }

  async confirmPlatformProviderModelCandidate(
    providerConnectionId: string,
    expectedConnectionVersion: number,
    modelCode: string,
    modelType: "CHAT" | "EMBEDDING",
  ): Promise<PlatformProviderModelCandidate> {
    return this.request<PlatformProviderModelCandidate>(
      `/platform/model-supply/connections/${encodeURIComponent(providerConnectionId)}/model-candidates/confirmation`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ expectedConnectionVersion, modelCode, modelType }),
      },
    );
  }

  async listPlatformModelDeployments(
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<PlatformModelDeploymentSummary>> {
    return this.request<PageResult<PlatformModelDeploymentSummary>>(
      `/platform/model-supply/deployments?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async createPlatformModelDeployment(
    input: PlatformModelDeploymentCreateInput,
    idempotencyKey?: string,
  ): Promise<PlatformModelDeploymentSummary> {
    return this.request<PlatformModelDeploymentSummary>("/platform/model-supply/deployments", {
      method: "POST",
      headers: this.writeHeaders("application/json", idempotencyKey),
      body: JSON.stringify(input),
    });
  }

  async probePlatformModelDeployment(
    deploymentId: string,
    expectedVersion: number,
    timeoutMillis = 10_000,
  ): Promise<PlatformModelDeploymentProbeResult> {
    return this.request<PlatformModelDeploymentProbeResult>(
      `/platform/model-supply/deployments/${encodeURIComponent(deploymentId)}/probe`,
      { method: "POST", headers: this.writeHeaders("application/json"), body: JSON.stringify({ expectedVersion, timeoutMillis }) },
    );
  }

  async publishPlatformModelDeployment(
    deploymentId: string,
    expectedVersion: number,
  ): Promise<PlatformModelDeploymentSummary> {
    return this.request<PlatformModelDeploymentSummary>(
      `/platform/model-supply/deployments/${encodeURIComponent(deploymentId)}/publish`,
      { method: "POST", headers: this.writeHeaders("application/json"), body: JSON.stringify({ expectedVersion }) },
    );
  }

  async disablePlatformModelDeployment(
    deploymentId: string,
    expectedVersion: number,
  ): Promise<PlatformModelDeploymentSummary> {
    return this.request<PlatformModelDeploymentSummary>(
      `/platform/model-supply/deployments/${encodeURIComponent(deploymentId)}/disable`,
      { method: "POST", headers: this.writeHeaders("application/json"), body: JSON.stringify({ expectedVersion }) },
    );
  }

  async rotateProviderConnectionCredential(
    providerConnectionId: string,
    expectedVersion: number,
    apiKey: string,
  ): Promise<ProviderConnectionSummary> {
    return this.request<ProviderConnectionSummary>(
      `/model-providers/${encodeURIComponent(providerConnectionId)}/credential-rotation`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ expectedVersion, apiKey }),
      },
    );
  }

  async probeProviderConnection(
    providerConnectionId: string,
    expectedVersion: number,
    modelCode: string,
    timeoutMillis: number,
  ): Promise<ProviderConnectionProbeResult> {
    return this.request<ProviderConnectionProbeResult>(
      `/model-providers/${encodeURIComponent(providerConnectionId)}/probe`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ expectedVersion, modelCode, timeoutMillis }),
      },
    );
  }

  async discoverProviderModels(
    providerConnectionId: string,
    expectedVersion: number,
    timeoutMillis: number,
  ): Promise<ProviderModelDiscoveryResult> {
    const result = await this.request<ProviderModelDiscoveryResult>(
      `/model-providers/${encodeURIComponent(providerConnectionId)}/model-discovery`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ expectedVersion, timeoutMillis }),
      },
    );
    return {
      discoveryId: result.discoveryId,
      succeeded: result.succeeded,
      resultCode: result.resultCode,
      elapsed: result.elapsed,
      discoveredAt: result.discoveredAt,
      expiresAt: result.expiresAt,
    };
  }

  /**
   * API Key 只存在于本次写请求；响应契约只返回安全配置状态。
   */
  async createModelConfiguration(input: {
    scope: ModelConfiguration["scope"];
    displayName: string;
    catalogEntryId: string;
    apiKey: string;
  }, idempotencyKey?: string): Promise<ModelConfiguration> {
    return this.request<ModelConfiguration>("/model-configurations", {
      method: "POST",
      headers: this.writeHeaders("application/json", idempotencyKey),
      body: JSON.stringify(input),
    });
  }

  async activateModelConfiguration(
    modelConfigurationId: string,
  ): Promise<ModelConfiguration> {
    return this.request<ModelConfiguration>(
      `/model-configurations/${encodeURIComponent(modelConfigurationId)}/activate`,
      { method: "POST" },
    );
  }

  /**
   * 轮换请求不读取既有 API Key，调用完成后页面必须清空输入值。
   */
  async rotateModelConfigurationCredential(
    modelConfigurationId: string,
    apiKey: string,
  ): Promise<ModelConfiguration> {
    return this.request<ModelConfiguration>(
      `/model-configurations/${encodeURIComponent(modelConfigurationId)}/credential`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json"),
        body: JSON.stringify({ apiKey }),
      },
    );
  }

  async listIndexVersions(
    knowledgeSpaceId: string,
    pageNumber = 1,
    pageSize = 20,
  ): Promise<PageResult<IndexVersion>> {
    return this.request<PageResult<IndexVersion>>(
      `/knowledge-spaces/${encodeURIComponent(knowledgeSpaceId)}/index-versions?pageNumber=${pageNumber}&pageSize=${pageSize}`,
    );
  }

  async publishIndexVersion(
    knowledgeSpaceId: string,
    indexVersionId: string,
    expectedVersion: number,
    idempotencyKey?: string,
  ): Promise<IndexVersion> {
    return this.request<IndexVersion>(
      `/knowledge-spaces/${encodeURIComponent(knowledgeSpaceId)}/index-versions/${encodeURIComponent(indexVersionId)}/publish`,
      {
        method: "POST",
        headers: this.writeHeaders("application/json", idempotencyKey),
        body: JSON.stringify({ expectedVersion }),
      },
    );
  }

  private writeHeaders(
    contentType: string,
    idempotencyKey?: string,
  ): HeadersInit {
    return {
      "Content-Type": contentType,
      ...this.writeIdempotencyHeaders(idempotencyKey),
    };
  }

  private async transitionTenant(
    tenantId: string,
    action: "activate" | "suspend" | "restore",
  ): Promise<TenantLifecycle> {
    return this.request<TenantLifecycle>(
      `/platform/tenants/${encodeURIComponent(tenantId)}/lifecycle/${action}`,
      { method: "POST" },
    );
  }

  private writeIdempotencyHeaders(idempotencyKey?: string): HeadersInit {
    return { "Idempotency-Key": idempotencyKey ?? this.identifierFactory() };
  }

  private async request<T>(
    path: string,
    init: RequestInit = {},
    authenticated = true,
  ): Promise<T> {
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    const requestRevision = this.sessionRevision;
    if (authenticated) {
      if (!this.sessionActive) {
        throw new ApiRequestError(
          401,
          "PLATFORM_SECURITY.AUTHENTICATION.0001",
          "需要先登录",
          "",
        );
      }
    }
    const response = authenticated
      ? await this.browserSession.request(path, { ...init, headers })
      : await this.fetcher(`${this.baseUrl}${path}`, { ...init, headers });
    if (!response.ok) {
      const error = await toApiRequestError(response);
      if (
        authenticated
        && error.status === 401
        && requestRevision === this.sessionRevision
      ) {
        this.sessionRevision += 1;
        this.updateSessionActive(false);
      }
      throw error;
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return response.json() as Promise<T>;
  }

  private updateSessionActive(active: boolean): void {
    if (this.sessionActive === active) {
      return;
    }
    this.sessionActive = active;
    for (const listener of this.sessionListeners) {
      listener(active);
    }
  }
}

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly traceId: string,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

export interface UserFacingError {
  title: string;
  description: string;
}

/**
 * 将服务端故障压缩为面向当前任务的安全提示，原始响应只保留在诊断链路中。
 */
export function presentApiError(error: unknown, operation: string): UserFacingError {
  const status = error instanceof ApiRequestError ? error.status : 500;
  if (status === 401) {
    return { title: "登录状态已失效", description: "请重新登录后继续刚才的操作。" };
  }
  if (status === 403) {
    return { title: "当前账户不能执行此操作", description: "请联系管理员确认您的职责范围。" };
  }
  if (status === 409) {
    return { title: `${operation}未完成`, description: "相关资源已变化或存在同名项，请刷新后核对并重试。" };
  }
  if (status === 429) {
    return { title: "请求过于频繁", description: "请稍等片刻后再试。" };
  }
  if (status === 502 || status === 503 || status === 504) {
    return { title: "依赖服务暂时不可用", description: "请稍后重试；若持续发生，请联系平台管理员。" };
  }
  if (status >= 400 && status < 500) {
    return { title: `${operation}未完成`, description: "请检查页面中标记的信息后重试。" };
  }
  return { title: "服务暂时不可用", description: "请稍后重试。" };
}

async function toApiRequestError(response: Response): Promise<ApiRequestError> {
  const safeMessage = presentApiError(
    new ApiRequestError(response.status, "REQUEST_FAILED", "", ""),
    "请求",
  ).title;
  const fallback = new ApiRequestError(
    response.status,
    "REQUEST_FAILED",
    safeMessage,
    "",
  );
  if (!response.headers.get("Content-Type")?.includes("application/json")) {
    return fallback;
  }
  try {
    const body = await response.json() as Partial<ApiErrorBody>;
    if (
      typeof body.code === "string"
    ) {
      return new ApiRequestError(
        response.status,
        body.code,
        safeMessage,
        "",
      );
    }
    return fallback;
  } catch {
    return fallback;
  }
}
