export interface BrowserLoginCredentials {
  tenantCode?: string;
  loginName: string;
  password: string;
}

export interface BrowserSessionCapability {
  workspaceCategories: string[];
  displayRoles: string[];
  maximumSessionAgeSeconds: number;
}

/** 业务客户端依赖的浏览器会话 seam，不包含 token 或可伪造身份事实。 */
export interface BrowserSessionGateway {
  login(credentials: BrowserLoginCredentials): Promise<BrowserSessionCapability>;
  restore(): Promise<BrowserSessionCapability | null>;
  logout(): Promise<void>;
  request(path: string, init?: RequestInit): Promise<Response>;
}

/** 浏览器会话 HTTP 失败只保留安全错误摘要，不包含响应正文。 */
export class BrowserSessionError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "BrowserSessionError";
  }
}

/**
 * 同源浏览器会话客户端。令牌始终由 HttpOnly cookie 承载，JavaScript 只读取非身份 CSRF cookie。
 */
export class BrowserSessionClient implements BrowserSessionGateway {
  constructor(
    private readonly fetcher: typeof fetch = (...args) => fetch(...args),
    private readonly baseUrl = "/api/v1",
    private readonly csrfToken = readCsrfCookie,
  ) {}

  async login(credentials: BrowserLoginCredentials): Promise<BrowserSessionCapability> {
    const csrf = await this.fetcher(`${this.baseUrl}/auth/browser-csrf`, {
      credentials: "same-origin",
    });
    if (!csrf.ok) {
      throw failure(csrf, "无法取得浏览器登录防护值");
    }
    const response = await this.request("/auth/browser-sessions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(credentials),
    });
    if (!response.ok) {
      throw failure(response, "登录未成功");
    }
    return this.restoreRequired();
  }

  async restore(): Promise<BrowserSessionCapability | null> {
    const response = await this.fetcher(`${this.baseUrl}/auth/browser-session`, {
      credentials: "same-origin",
      headers: { Accept: "application/json" },
    });
    if (response.status === 401) {
      return null;
    }
    if (!response.ok) {
      throw failure(response, "无法恢复浏览器会话");
    }
    return parseCapability(response);
  }

  async logout(): Promise<void> {
    const response = await this.request("/auth/browser-session", { method: "DELETE" });
    if (!response.ok) {
      throw failure(response, "注销未完成");
    }
  }

  /** 业务客户端统一通过此方法发送请求，避免单页遗漏 CSRF 或重新拼接 Bearer token。 */
  async request(path: string, init: RequestInit = {}): Promise<Response> {
    const headers = new Headers(init.headers);
    if (isUnsafe(init.method)) {
      const csrf = this.csrfToken();
      if (csrf === undefined) {
        throw new BrowserSessionError(401, "浏览器会话防护值缺失，请重新登录");
      }
      headers.set("X-CSRF-Token", csrf);
    }
    return this.fetcher(`${this.baseUrl}${path}`, {
      ...init,
      headers,
      credentials: "same-origin",
    });
  }

  private async restoreRequired(): Promise<BrowserSessionCapability> {
    const capability = await this.restore();
    if (capability === null) {
      throw new BrowserSessionError(401, "登录后未恢复浏览器会话");
    }
    return capability;
  }
}

function isUnsafe(method: string | undefined): boolean {
  return method !== undefined && ["POST", "PUT", "PATCH", "DELETE"].includes(method.toUpperCase());
}

function readCsrfCookie(): string | undefined {
  if (typeof document === "undefined") {
    return undefined;
  }
  const prefix = "__Host-foundation-csrf=";
  return document.cookie.split(";").map((value) => value.trim())
    .find((value) => value.startsWith(prefix))?.slice(prefix.length);
}

function failure(response: Response, message: string): BrowserSessionError {
  return new BrowserSessionError(response.status, message);
}

async function parseCapability(response: Response): Promise<BrowserSessionCapability> {
  const candidate = await response.json() as Partial<BrowserSessionCapability>;
  const maximumSessionAgeSeconds = candidate.maximumSessionAgeSeconds;
  if (
    !Array.isArray(candidate.workspaceCategories)
    || !candidate.workspaceCategories.every((value) => typeof value === "string")
    || !Array.isArray(candidate.displayRoles)
    || !candidate.displayRoles.every((value) => typeof value === "string")
    || !Number.isSafeInteger(maximumSessionAgeSeconds)
    || maximumSessionAgeSeconds === undefined
    || maximumSessionAgeSeconds <= 0
  ) {
    throw new BrowserSessionError(502, "浏览器会话能力清单格式不正确");
  }
  return {
    workspaceCategories: candidate.workspaceCategories,
    displayRoles: candidate.displayRoles,
    maximumSessionAgeSeconds,
  };
}
