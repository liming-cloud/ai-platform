import {
  BrowserSessionClient,
  type BrowserLoginCredentials,
  type BrowserSessionGateway,
} from "@foundation/browser-session";

export type LoginCredentials = BrowserLoginCredentials;

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

/** 用户可提交的受控查询反馈结果。 */
export type QueryFeedbackOutcome = "POSITIVE" | "NEGATIVE";

/** 用户可提交的受控查询反馈原因。 */
export type QueryFeedbackReason =
  | "HELPFUL"
  | "INCORRECT"
  | "INSUFFICIENT"
  | "IRRELEVANT";

interface ApiErrorBody {
  code: string;
  message: string;
  traceId: string;
}

/**
 * 查询站点同源客户端。会话凭据只由 HttpOnly cookie 承载。
 */
export class QueryClient {
  private sessionActive = false;
  private sessionRevision = 0;
  private readonly browserSession: BrowserSessionGateway;

  constructor(
    private readonly fetcher: typeof fetch = (...args) => fetch(...args),
    private readonly baseUrl = "/api/v1",
    browserSession?: BrowserSessionGateway,
  ) {
    this.browserSession = browserSession ?? new BrowserSessionClient(fetcher, baseUrl);
    this.sessionActive = browserSession !== undefined;
  }

  async login(
    credentials: LoginCredentials,
    signal?: AbortSignal,
  ): Promise<void> {
    const revision = ++this.sessionRevision;
    this.sessionActive = false;
    if (signal?.aborted) {
      return;
    }
    await this.browserSession.login(credentials);
    if (revision !== this.sessionRevision) {
      return;
    }
    this.sessionActive = true;
  }

  async logout(): Promise<void> {
    this.sessionRevision += 1;
    try {
      await this.browserSession.logout();
    } finally {
      this.sessionActive = false;
    }
  }

  discardSession(): void {
    this.sessionRevision += 1;
    this.sessionActive = false;
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
      throw await this.toRequestError(response, authenticated.revision);
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

  async recordQueryFeedback(
    queryRunId: string,
    outcome: QueryFeedbackOutcome,
    reason: QueryFeedbackReason,
    signal?: AbortSignal,
  ): Promise<void> {
    return this.request<void>(
      `/query-runs/${encodeURIComponent(queryRunId)}/feedback`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ outcome, reason }),
        signal,
      },
    );
  }

  private async request<T>(
    path: string,
    init: RequestInit = {},
    authenticated = true,
  ): Promise<T> {
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    let response: Response;
    let requestRevision: number | undefined;
    if (authenticated) {
      const result = await this.authenticatedFetch(path, { ...init, headers });
      response = result.response;
      requestRevision = result.revision;
    } else {
      response = await this.fetcher(
        `${this.baseUrl}${path}`,
        { ...init, headers },
      );
    }
    if (!response.ok) {
      throw await this.toRequestError(response, requestRevision);
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
  ): Promise<{ response: Response; revision: number }> {
    if (!this.sessionActive) {
      throw new QueryApiError(
        401,
        "PLATFORM_SECURITY.AUTHENTICATION.0001",
        "需要先登录",
        "",
      );
    }
    const revision = this.sessionRevision;
    const response = await this.browserSession.request(path, init);
    return { response, revision };
  }

  private async toRequestError(
    response: Response,
    requestRevision?: number,
  ): Promise<QueryApiError> {
    const body = await safeErrorBody(response);
    if (
      response.status === 401
      && requestRevision !== undefined
      && requestRevision === this.sessionRevision
    ) {
      this.sessionRevision += 1;
      this.sessionActive = false;
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
