export interface LoginCredentials {
  tenantCode: string;
  loginName: string;
  password: string;
}

export interface Session {
  tokenName: string;
  tokenValue: string;
  expiresInSeconds: number;
}

export interface KnowledgeSpace {
  id: string;
  name: string;
  description: string | null;
  status: string;
  version: number;
  createdAt: string;
}

export interface TextDataSource {
  id: string;
  knowledgeSpaceId: string;
  name: string;
  status: string;
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
  modelType: "CHAT" | "EMBEDDING";
  displayName: string;
  status: string;
  version: number;
  embeddingDimension: number | null;
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
  endpoint: string;
  modelCode: string;
  modelType: "CHAT" | "EMBEDDING";
  contextWindow: number;
  outputLimit: number | null;
  embeddingDimension: number | null;
  status: "PUBLISHED";
  version: number;
  createdAt: string;
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
  private tokenValue: string | undefined;

  constructor(
    private readonly fetcher: typeof fetch = (...args) => fetch(...args),
    private readonly identifierFactory: IdentifierFactory = () =>
      crypto.randomUUID(),
    private readonly baseUrl = "/api/v1",
  ) {}

  async login(credentials: LoginCredentials): Promise<Session> {
    const session = await this.request<Session>(
      "/auth/sessions",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(credentials),
      },
      false,
    );
    this.tokenValue = session.tokenValue;
    return session;
  }

  restoreSession(tokenValue: string): void {
    if (tokenValue.trim().length === 0) {
      throw new Error("会话令牌不能为空");
    }
    this.tokenValue = tokenValue;
  }

  async logout(): Promise<void> {
    await this.request<void>("/auth/sessions/current", { method: "DELETE" });
    this.tokenValue = undefined;
  }

  hasSession(): boolean {
    return this.tokenValue !== undefined;
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
    const requestToken = this.tokenValue;
    if (authenticated) {
      if (requestToken === undefined) {
        throw new ApiRequestError(
          401,
          "PLATFORM_SECURITY.AUTHENTICATION.0001",
          "需要先登录",
          "",
        );
      }
      headers.set("Authorization", `Bearer ${requestToken}`);
    }
    const response = await this.fetcher(`${this.baseUrl}${path}`, {
      ...init,
      headers,
    });
    if (!response.ok) {
      const error = await toApiRequestError(response);
      if (authenticated && error.status === 401 && this.tokenValue === requestToken) {
        this.tokenValue = undefined;
      }
      throw error;
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return response.json() as Promise<T>;
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

async function toApiRequestError(response: Response): Promise<ApiRequestError> {
  const fallback = new ApiRequestError(
    response.status,
    "REQUEST_FAILED",
    "请求处理失败，请稍后重试",
    "",
  );
  if (!response.headers.get("Content-Type")?.includes("application/json")) {
    return fallback;
  }
  try {
    const body = await response.json() as Partial<ApiErrorBody>;
    if (
      typeof body.code === "string"
      && typeof body.message === "string"
      && typeof body.traceId === "string"
    ) {
      return new ApiRequestError(
        response.status,
        body.code,
        body.message,
        body.traceId,
      );
    }
    return fallback;
  } catch {
    return fallback;
  }
}
