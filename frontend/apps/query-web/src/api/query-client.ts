export interface LoginCredentials {
  tenantCode: string;
  loginName: string;
  password: string;
}

export interface QueryAssistant {
  assistantId: string;
  displayName: string;
  assistantVersionId: string;
  versionNumber: number;
  publishedAt: string;
}

export interface QueryAssistantPage {
  items: QueryAssistant[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
}

export interface QueryCitation {
  sequence: number;
  dataSourceId: string;
  contentVersion: number;
  chunkId: string;
  ordinal: number;
  quote: string;
  score: number;
}

export interface ModelUsage {
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
}

export interface QueryEvent {
  sequence: number;
  type: string;
  status: string;
  answer: string | null;
  refusalReason: string | null;
  citations: QueryCitation[];
  usage: ModelUsage;
  occurredAt: string;
  terminal: boolean;
}

export interface QueryRun {
  id: string;
  assistantVersionId: string;
  status: string;
}

interface Session {
  tokenName: string;
  tokenValue: string;
  expiresInSeconds: number;
}

interface ApiErrorBody {
  code: string;
  message: string;
  traceId: string;
}

/**
 * 查询站点同源客户端。会话令牌只存在于当前实例内存。
 */
export class QueryClient {
  private tokenValue: string | undefined;
  private sessionRevision = 0;

  constructor(
    private readonly fetcher: typeof fetch = (...args) => fetch(...args),
    private readonly baseUrl = "/api/v1",
  ) {}

  async login(
    credentials: LoginCredentials,
    signal?: AbortSignal,
  ): Promise<void> {
    const revision = ++this.sessionRevision;
    this.tokenValue = undefined;
    const session = await this.request<Session>(
      "/auth/sessions",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(credentials),
        signal,
      },
      false,
    );
    if (revision !== this.sessionRevision) {
      return;
    }
    if (session.tokenValue.trim().length === 0) {
      throw new QueryApiError(
        502,
        "INVALID_SESSION_RESPONSE",
        "登录服务返回了无效会话",
        "",
      );
    }
    this.tokenValue = session.tokenValue;
  }

  async logout(): Promise<void> {
    const token = this.tokenValue;
    this.sessionRevision += 1;
    if (token === undefined) {
      return;
    }
    try {
      const response = await this.fetcher(
        `${this.baseUrl}/auth/sessions/current`,
        {
          method: "DELETE",
          headers: {
            Accept: "application/json",
            Authorization: `Bearer ${token}`,
          },
        },
      );
      if (!response.ok) {
        throw await this.toRequestError(response, token);
      }
    } finally {
      if (this.tokenValue === token) {
        this.tokenValue = undefined;
      }
    }
  }

  discardSession(): void {
    this.sessionRevision += 1;
    this.tokenValue = undefined;
  }

  async listAssistants(
    signal?: AbortSignal,
  ): Promise<QueryAssistantPage> {
    return this.request<QueryAssistantPage>(
      "/query-assistants?pageNumber=1&pageSize=20",
      { signal },
    );
  }

  async createQuery(
    assistantVersionId: string,
    question: string,
    idempotencyKey: string,
    signal?: AbortSignal,
  ): Promise<QueryRun> {
    return this.request<QueryRun>("/query-runs", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify({ assistantVersionId, question }),
      signal,
    });
  }

  async listQueryEvents(
    queryRunId: string,
    afterSequence: number,
    signal?: AbortSignal,
  ): Promise<QueryEvent[]> {
    if (!Number.isSafeInteger(afterSequence) || afterSequence < 0) {
      throw new Error("事件恢复序号不正确");
    }
    const headers = new Headers({ Accept: "text/event-stream" });
    if (afterSequence > 0) {
      headers.set("Last-Event-ID", String(afterSequence));
    }
    const authenticated = await this.authenticatedFetch(
      `/query-runs/${encodeURIComponent(queryRunId)}/events`,
      { headers, signal },
    );
    const response = authenticated.response;
    if (!response.ok) {
      throw await this.toRequestError(response, authenticated.token);
    }
    const contentType = response.headers.get("Content-Type") ?? "";
    if (!contentType.toLowerCase().startsWith("text/event-stream")) {
      throw new QueryApiError(
        502,
        "INVALID_EVENT_STREAM",
        "查询服务返回了无效事件流",
        "",
      );
    }
    return readEventStream(response);
  }

  private async request<T>(
    path: string,
    init: RequestInit = {},
    authenticated = true,
  ): Promise<T> {
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    let response: Response;
    let requestToken: string | undefined;
    if (authenticated) {
      const result = await this.authenticatedFetch(path, { ...init, headers });
      response = result.response;
      requestToken = result.token;
    } else {
      response = await this.fetcher(
        `${this.baseUrl}${path}`,
        { ...init, headers },
      );
    }
    if (!response.ok) {
      throw await this.toRequestError(response, requestToken);
    }
    if (response.status === 204) {
      return undefined as T;
    }
    try {
      return await response.json() as T;
    } catch {
      throw new QueryApiError(
        502,
        "INVALID_API_RESPONSE",
        "服务返回了无法识别的响应",
        "",
      );
    }
  }

  private async authenticatedFetch(
    path: string,
    init: RequestInit,
  ): Promise<{ response: Response; token: string }> {
    const token = this.tokenValue;
    if (token === undefined) {
      throw new QueryApiError(
        401,
        "AUTHENTICATION_REQUIRED",
        "需要先登录",
        "",
      );
    }
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${token}`);
    const response = await this.fetcher(
      `${this.baseUrl}${path}`,
      { ...init, headers },
    );
    return { response, token };
  }

  private async toRequestError(
    response: Response,
    requestToken?: string,
  ): Promise<QueryApiError> {
    const body = await safeErrorBody(response);
    if (
      response.status === 401
      && requestToken !== undefined
      && this.tokenValue === requestToken
    ) {
      this.sessionRevision += 1;
      this.tokenValue = undefined;
    }
    return new QueryApiError(
      response.status,
      body?.code ?? "REQUEST_FAILED",
      body?.message ?? "请求未完成，请稍后重试",
      body?.traceId ?? "",
    );
  }
}

export class QueryApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly traceId: string,
  ) {
    super(message);
    this.name = "QueryApiError";
  }
}

async function safeErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    const value = await response.json() as Partial<ApiErrorBody>;
    return typeof value.code === "string"
        && typeof value.message === "string"
        && typeof value.traceId === "string"
      ? {
          code: value.code,
          message: value.message,
          traceId: value.traceId,
        }
      : null;
  } catch {
    return null;
  }
}

async function readEventStream(response: Response): Promise<QueryEvent[]> {
  if (response.body === null) {
    return [];
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const events: QueryEvent[] = [];
  let buffer = "";
  while (true) {
    const result = await reader.read();
    buffer += decoder.decode(result.value, { stream: !result.done });
    buffer = buffer.replaceAll("\r\n", "\n");
    let separator = buffer.indexOf("\n\n");
    while (separator >= 0) {
      const block = buffer.slice(0, separator);
      buffer = buffer.slice(separator + 2);
      if (block.trim().length > 0) {
        events.push(parseEventBlock(block));
      }
      separator = buffer.indexOf("\n\n");
    }
    if (result.done) {
      break;
    }
  }
  if (buffer.trim().length > 0) {
    events.push(parseEventBlock(buffer));
  }
  return events;
}

function parseEventBlock(block: string): QueryEvent {
  let id = "";
  let eventName = "";
  const data: string[] = [];
  for (const line of block.split("\n")) {
    if (line.startsWith("id:")) {
      id = line.slice(3).trim();
    } else if (line.startsWith("event:")) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      data.push(line.slice(5));
    }
  }
  try {
    const event = JSON.parse(data.join("\n")) as QueryEvent;
    if (
      !Number.isSafeInteger(event.sequence)
      || event.sequence < 1
      || String(event.sequence) !== id
      || event.type !== eventName
      || typeof event.status !== "string"
      || typeof event.terminal !== "boolean"
      || !Array.isArray(event.citations)
    ) {
      throw new Error("事件字段不一致");
    }
    return event;
  } catch {
    throw new QueryApiError(
      502,
      "INVALID_EVENT_STREAM",
      "查询事件格式不正确",
      "",
    );
  }
}
