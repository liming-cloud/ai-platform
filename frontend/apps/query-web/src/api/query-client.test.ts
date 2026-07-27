import { describe, expect, it } from "vitest";
import {
  QueryApiError,
  QueryClient,
} from "./query-client";

describe("QueryClient", () => {
  it("登录后只在内存中使用令牌读取已发布助手并创建查询", async () => {
    const requests: Request[] = [];
    const responses = [
      jsonResponse({
        tokenName: "Authorization",
        tokenValue: "query-token",
        expiresInSeconds: 1800,
      }, 201),
      jsonResponse({
        items: [{
          assistantId: "410000000000000001",
          displayName: "门店导购助手",
          assistantVersionId: "420000000000000001",
          versionNumber: 2,
          publishedAt: "2026-07-27T06:00:00Z",
        }],
        nextCursor: null,
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
    }, "http://localhost/api/v1");

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
      "http://localhost/api/v1/auth/sessions",
      "http://localhost/api/v1/query-assistants?limit=20",
      "http://localhost/api/v1/query-runs",
    ]);
    expect(requests[1]?.headers.get("Authorization")).toBe(
      "Bearer query-token",
    );
    expect(requests[2]?.headers.get("Idempotency-Key")).toBe(
      "query-key-001",
    );
    expect(await requests[2]?.json()).toEqual({
      assistantVersionId: assistants.items[0]!.assistantVersionId,
      question: "牛奶在哪里？",
    });
  });

  it("按最后事件编号携带认证头并解析分块事件流", async () => {
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

    expect(request?.headers.get("Authorization")).toBe("Bearer query-token");
    expect(request?.headers.get("Last-Event-ID")).toBe("1");
    expect(events).toHaveLength(1);
    expect(events[0]?.sequence).toBe(2);
    expect(events[0]?.answer).toBe("牛奶位于一层。");
  });

  it("统一错误只保留安全字段且会话失效后清除令牌", async () => {
    const client = await authenticatedClient(async () => jsonResponse({
      code: "AUTHENTICATION_REQUIRED",
      message: "会话已失效",
      traceId: "trace-001",
      details: { internal: "不能进入前端错误" },
    }, 401));

    await expect(client.listAssistants()).rejects.toEqual(
      new QueryApiError(
        401,
        "AUTHENTICATION_REQUIRED",
        "会话已失效",
        "trace-001",
      ),
    );
    await expect(client.listAssistants()).rejects.toMatchObject({
      code: "AUTHENTICATION_REQUIRED",
    });
  });

  it("旧身份迟到的未认证响应不能清除新身份令牌", async () => {
    const requests: Request[] = [];
    let completeOldRequest:
      | ((response: Response) => void)
      | undefined;
    const oldResponse = new Promise<Response>((resolve) => {
      completeOldRequest = resolve;
    });
    let loginCount = 0;
    const client = new QueryClient(async (input, init) => {
      const request = new Request(input, init);
      requests.push(request);
      if (request.url.endsWith("/auth/sessions")) {
        loginCount += 1;
        return jsonResponse({
          tokenName: "Authorization",
          tokenValue: loginCount === 1 ? "old-token" : "new-token",
          expiresInSeconds: 1800,
        }, 201);
      }
      if (requests.length === 2) {
        return oldResponse;
      }
      return jsonResponse({ items: [], nextCursor: null });
    }, "http://localhost/api/v1");

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
      code: "AUTHENTICATION_REQUIRED",
      message: "旧会话已失效",
      traceId: "trace-old",
    }, 401));
    await expect(oldRequest).rejects.toMatchObject({
      code: "AUTHENTICATION_REQUIRED",
    });

    await client.listAssistants();
    expect(requests.at(-1)?.headers.get("Authorization")).toBe(
      "Bearer new-token",
    );
  });

  it("旧登录迟到的成功响应不能覆盖新身份令牌", async () => {
    const requests: Request[] = [];
    let completeOldLogin:
      | ((response: Response) => void)
      | undefined;
    const oldLoginResponse = new Promise<Response>((resolve) => {
      completeOldLogin = resolve;
    });
    let loginCount = 0;
    const client = new QueryClient(async (input, init) => {
      const request = new Request(input, init);
      requests.push(request);
      if (request.url.endsWith("/auth/sessions")) {
        loginCount += 1;
        if (loginCount === 1) {
          return oldLoginResponse;
        }
        return jsonResponse({
          tokenName: "Authorization",
          tokenValue: "new-token",
          expiresInSeconds: 1800,
        }, 201);
      }
      return jsonResponse({ items: [], nextCursor: null });
    }, "http://localhost/api/v1");

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
    completeOldLogin?.(jsonResponse({
      tokenName: "Authorization",
      tokenValue: "old-token",
      expiresInSeconds: 1800,
    }, 201));
    await oldLogin;

    await client.listAssistants();
    expect(requests.at(-1)?.headers.get("Authorization")).toBe(
      "Bearer new-token",
    );
  });
});

async function authenticatedClient(fetcher: typeof fetch) {
  let authenticated = false;
  const client = new QueryClient(
    async (input, init) => {
      if (!authenticated) {
        authenticated = true;
        return jsonResponse({
          tokenName: "Authorization",
          tokenValue: "query-token",
          expiresInSeconds: 1800,
        }, 201);
      }
      return fetcher(input, init);
    },
    "http://localhost/api/v1",
  );
  await client.login({
    tenantCode: "demo-store",
    loginName: "store-admin",
    password: "demo-password",
  });
  return client;
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
