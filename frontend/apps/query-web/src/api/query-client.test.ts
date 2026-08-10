import { describe, expect, it } from "vitest";
import type { BrowserSessionGateway } from "@foundation/browser-session";
import {
  QueryApiError,
  QueryClient,
} from "./query-client";

describe("QueryClient", () => {
  it("当前会话收到 401 时通知根级入口撤下查询工作区", async () => {
    const client = await authenticatedClient(async () => new Response(null, {
      status: 401,
      headers: { "Content-Type": "application/json" },
    }));
    const changes: boolean[] = [];
    client.subscribeSessionChange((active) => changes.push(active));

    await expect(client.listAssistants()).rejects.toBeInstanceOf(QueryApiError);

    expect(client.hasSession()).toBe(false);
    expect(changes).toEqual([false]);
  });

  it("登录后只使用同源浏览器会话读取已发布助手并创建查询", async () => {
    const requests: Request[] = [];
    const responses = [
      jsonResponse({
        items: [{
          assistantId: "410000000000000001",
          displayName: "门店导购助手",
          assistantVersionId: "420000000000000001",
          versionNumber: 2,
          publishedAt: "2026-07-27T06:00:00Z",
        }],
        pageNumber: 1,
        pageSize: 20,
        totalElements: 1,
        totalPages: 1,
        hasPrevious: false,
        hasNext: false,
      }),
      jsonResponse({
        id: "430000000000000001",
        assistantVersionId: "420000000000000001",
        status: "ANSWERED",
      }, 201),
    ];
    const client = new QueryClient(async (input, init) => {
      requests.push(new Request(input, init));
      return responses.shift() ?? new Response(null, { status: 500 });
    }, "http://localhost/api/v1", fakeBrowserSession(async (input, init) => {
      const request = new Request(input, init);
      requests.push(request);
      return responses.shift() ?? new Response(null, { status: 500 });
    }));

    await client.login({
      tenantCode: "demo-store",
      loginName: "store-admin",
      password: "demo-password",
    });
    const assistants = await client.listAssistants();
    await client.createQuery(
      assistants.items[0]!.assistantVersionId,
      "牛奶在哪里？",
      "query-key-001",
    );

    expect(requests.map((request) => request.url)).toEqual([
      "http://localhost/api/v1/query-assistants?pageNumber=1&pageSize=20",
      "http://localhost/api/v1/query-runs",
    ]);
    expect(requests[0]?.headers.get("Authorization")).toBeNull();
    expect(requests[1]?.headers.get("Idempotency-Key")).toBe(
      "query-key-001",
    );
    expect(await requests[1]?.json()).toEqual({
      assistantVersionId: assistants.items[0]!.assistantVersionId,
      question: "牛奶在哪里？",
    });
  });

  it("按最后事件编号使用浏览器会话并解析分块事件流", async () => {
    let request: Request | undefined;
    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode(
          "id:2\nevent:QUERY_ANSWERED\ndata:{\"sequence\":2,\"type\":\"QUERY_",
        ));
        controller.enqueue(encoder.encode(
          "ANSWERED\",\"status\":\"ANSWERED\",\"answer\":\"牛奶位于一层。\",\"refusalReason\":null,\"citations\":[],\"usage\":{\"inputTokens\":1,\"outputTokens\":1,\"totalTokens\":2},\"occurredAt\":\"2026-07-27T06:00:00Z\",\"terminal\":true}\n\n",
        ));
        controller.close();
      },
    });
    const client = await authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(stream, {
        status: 200,
        headers: { "Content-Type": "text/event-stream" },
      });
    });

    const events = await client.listQueryEvents(
      "430000000000000001",
      1,
    );

    expect(request?.headers.get("Authorization")).toBeNull();
    expect(request?.headers.get("Last-Event-ID")).toBe("1");
    expect(events).toHaveLength(1);
    expect(events[0]?.sequence).toBe(2);
    expect(events[0]?.answer).toBe("牛奶位于一层。");
  });

  it("查询反馈只提交受控枚举并沿用浏览器会话", async () => {
    let request: Request | undefined;
    const client = await authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(null, { status: 204 });
    });

    await client.recordQueryFeedback(
      "430000000000000001",
      "NEGATIVE",
      "INSUFFICIENT",
    );

    expect(request?.url).toBe(
      "http://localhost/api/v1/query-runs/430000000000000001/feedback",
    );
    expect(request?.method).toBe("POST");
    expect(request?.headers.get("Authorization")).toBeNull();
    expect(await request?.json()).toEqual({
      outcome: "NEGATIVE",
      reason: "INSUFFICIENT",
    });
  });

  it("统一错误只保留安全字段且会话失效后清除本地状态", async () => {
    const client = await authenticatedClient(async () => jsonResponse({
      code: "PLATFORM_SECURITY.AUTHENTICATION.0001",
      message: "会话已失效",
      traceId: "trace-001",
      details: { internal: "不能进入前端错误" },
    }, 401));

    await expect(client.listAssistants()).rejects.toEqual(
      new QueryApiError(
        401,
        "PLATFORM_SECURITY.AUTHENTICATION.0001",
        "会话已失效",
        "trace-001",
      ),
    );
    await expect(client.listAssistants()).rejects.toMatchObject({
      code: "PLATFORM_SECURITY.AUTHENTICATION.0001",
    });
  });

  it("旧会话迟到的未认证响应不能清除新会话状态", async () => {
    const requests: Request[] = [];
    let completeOldRequest:
      | ((response: Response) => void)
      | undefined;
    const oldResponse = new Promise<Response>((resolve) => {
      completeOldRequest = resolve;
    });
    const client = new QueryClient(async (input, init) => {
      const request = new Request(input, init);
      requests.push(request);
      if (requests.length === 1) {
        return oldResponse;
      }
      return jsonResponse(emptyAssistantPage());
    }, "http://localhost/api/v1", fakeBrowserSession(async (input, init) => {
      const request = new Request(input, init);
      requests.push(request);
      if (requests.length === 1) {
        return oldResponse;
      }
      return jsonResponse(emptyAssistantPage());
    }));

    await client.login({
      tenantCode: "demo-store",
      loginName: "admin",
      password: "old-password",
    });
    const oldRequest = client.listAssistants();
    await client.login({
      tenantCode: "demo-campus",
      loginName: "admin",
      password: "new-password",
    });
    completeOldRequest?.(jsonResponse({
      code: "PLATFORM_SECURITY.AUTHENTICATION.0001",
      message: "旧会话已失效",
      traceId: "trace-old",
    }, 401));
    await expect(oldRequest).rejects.toMatchObject({
      code: "PLATFORM_SECURITY.AUTHENTICATION.0001",
    });

    await client.listAssistants();
    expect(requests.at(-1)?.headers.get("Authorization")).toBeNull();
  });

  it("旧登录迟到的成功结果不能覆盖新会话状态", async () => {
    const requests: Request[] = [];
    let completeOldLogin:
      | (() => void)
      | undefined;
    const oldLoginResponse = new Promise<void>((resolve) => {
      completeOldLogin = resolve;
    });
    let loginCount = 0;
    const execute = async (input: RequestInfo | URL, init?: RequestInit) => {
      const request = new Request(input, init);
      requests.push(request);
      return jsonResponse(emptyAssistantPage());
    };
    const session: BrowserSessionGateway = {
      login: async () => {
        loginCount += 1;
        if (loginCount === 1) {
          await oldLoginResponse;
        }
        return tenantCapability();
      },
      restore: async () => tenantCapability(),
      logout: async () => undefined,
      request: async (path, init) => execute(`http://localhost/api/v1${path}`, init),
    };
    const client = new QueryClient(execute, "http://localhost/api/v1", session);

    const oldLogin = client.login({
      tenantCode: "demo-store",
      loginName: "admin",
      password: "old-password",
    });
    await client.login({
      tenantCode: "demo-campus",
      loginName: "admin",
      password: "new-password",
    });
    completeOldLogin?.();
    await oldLogin;

    await client.listAssistants();
    expect(requests.at(-1)?.headers.get("Authorization")).toBeNull();
  });
});

function emptyAssistantPage() {
  return {
    items: [],
    pageNumber: 1,
    pageSize: 20,
    totalElements: 0,
    totalPages: 0,
    hasPrevious: false,
    hasNext: false,
  };
}

async function authenticatedClient(fetcher: typeof fetch) {
  const client = new QueryClient(
    fetcher,
    "http://localhost/api/v1",
    fakeBrowserSession(fetcher),
  );
  await client.login({
    tenantCode: "demo-store",
    loginName: "store-admin",
    password: "demo-password",
  });
  return client;
}

function fakeBrowserSession(fetcher: typeof fetch): BrowserSessionGateway {
  return {
    login: async () => tenantCapability(),
    restore: async () => tenantCapability(),
    logout: async () => undefined,
    request: async (path, init) => fetcher(`http://localhost/api/v1${path}`, init),
  };
}

function tenantCapability() {
  return {
    workspaceCategories: ["TENANT"],
    displayRoles: ["TENANT_MEMBER"],
    capabilityCodes: ["assistant:query"],
    maximumSessionAgeSeconds: 3600,
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
