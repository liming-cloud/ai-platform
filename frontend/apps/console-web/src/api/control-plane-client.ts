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
      "Idempotency-Key": idempotencyKey ?? this.identifierFactory(),
    };
  }

  private async request<T>(
    path: string,
    init: RequestInit = {},
    authenticated = true,
  ): Promise<T> {
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    if (authenticated) {
      if (this.tokenValue === undefined) {
        throw new ApiRequestError(
          401,
          "PLATFORM_SECURITY.AUTHENTICATION.0001",
          "需要先登录",
          "",
        );
      }
      headers.set("Authorization", `Bearer ${this.tokenValue}`);
    }
    const response = await this.fetcher(`${this.baseUrl}${path}`, {
      ...init,
      headers,
    });
    if (!response.ok) {
      throw await toApiRequestError(response);
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
