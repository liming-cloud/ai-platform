import { describe, expect, it } from "vitest";
import {
  ApiRequestError,
  ControlPlaneClient,
} from "./control-plane-client";

describe("ControlPlaneClient", () => {
  it("登录后为写请求携带会话和幂等键", async () => {
    const requests: Request[] = [];
    const responses = [
      new Response(
        JSON.stringify({
          tokenName: "Authorization",
          tokenValue: "demo-token",
          expiresInSeconds: 1800,
        }),
        { status: 201, headers: { "Content-Type": "application/json" } },
      ),
      new Response(
        JSON.stringify({
          id: "210000000000000001",
          name: "门店商品知识",
          description: "演示数据",
          status: "DRAFT",
          version: 0,
          createdAt: "2026-07-27T00:00:00Z",
        }),
        { status: 201, headers: { "Content-Type": "application/json" } },
      ),
    ];
    const client = new ControlPlaneClient(
      async (input, init) => {
        requests.push(new Request(input, init));
        return responses.shift() ?? new Response(null, { status: 500 });
      },
      () => "request-key-001",
      "http://localhost/api/v1",
    );

    await client.login({
      tenantCode: "demo-store",
      loginName: "store-admin",
      password: "demo-password",
    });
    await client.createKnowledgeSpace({
      name: "门店商品知识",
      description: "演示数据",
    });

    expect(requests).toHaveLength(2);
    expect(requests[1]?.headers.get("Authorization")).toBe(
      "Bearer demo-token",
    );
    expect(requests[1]?.headers.get("Idempotency-Key")).toBe(
      "request-key-001",
    );
  });

  it("上传文本时保留严格媒体类型和乐观版本", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(
        JSON.stringify({
          id: "220000000000000001",
          knowledgeSpaceId: "210000000000000001",
          name: "商品说明",
          status: "CONTENT_READY",
          version: 1,
          contentVersion: 1,
          publishedContentVersion: 0,
          createdAt: "2026-07-27T00:00:00Z",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    });

    await client.uploadTextContent(
      "210000000000000001",
      "220000000000000001",
      0,
      "货品位于一层。",
    );

    expect(request?.headers.get("Content-Type")).toBe(
      "text/plain; charset=UTF-8",
    );
    expect(request?.url).toContain("expectedVersion=0");
    expect(await request?.text()).toBe("货品位于一层。");
  });

  it("把统一错误转换为不泄漏响应体的前端错误", async () => {
    const client = new ControlPlaneClient(async () => new Response(
      JSON.stringify({
        code: "KNOWLEDGE_DATA.AUTHORIZATION.0001",
        message: "没有执行该操作的权限",
        traceId: "trace-001",
        details: {},
      }),
      { status: 403, headers: { "Content-Type": "application/json" } },
    ), undefined, "http://localhost/api/v1");
    client.restoreSession("demo-token");

    await expect(client.listKnowledgeSpaces()).rejects.toEqual(
      new ApiRequestError(
        403,
        "KNOWLEDGE_DATA.AUTHORIZATION.0001",
        "没有执行该操作的权限",
        "trace-001",
      ),
    );
  });

  it("按知识空间创建、查询并发布索引版本", async () => {
    const requests: Request[] = [];
    const indexVersion = {
      id: "230000000000000001",
      knowledgeSpaceId: "210000000000000001",
      sourceSnapshotDigest: "sha256:source",
      chunkPolicy: "paragraph-window-v1",
      embeddingBindingType: "GOVERNED_CONFIGURATION",
      embeddingModelConfigurationId: "320000000000000001",
      embeddingModelConfigurationRevision: 1,
      embeddingCatalogEntryId: "330000000000000001",
      embeddingCatalogEntryRevision: 1,
      embeddingDimension: 128,
      status: "READY",
      version: 1,
      buildDigest: "sha256:build",
      chunkCount: 3,
      failureCode: null,
      createdAt: "2026-07-27T00:00:00Z",
      readyAt: "2026-07-27T00:00:01Z",
      activatedAt: null,
      supersededAt: null,
    };
    const responses = [
      new Response(JSON.stringify({ ...indexVersion, status: "BUILDING" }), {
        status: 202,
        headers: { "Content-Type": "application/json" },
      }),
      new Response(
        JSON.stringify({
          items: [indexVersion],
          pageNumber: 1,
          pageSize: 20,
          totalElements: 1,
          totalPages: 1,
          hasPrevious: false,
          hasNext: false,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
      new Response(
        JSON.stringify({
          ...indexVersion,
          status: "ACTIVE",
          version: 2,
          activatedAt: "2026-07-27T00:00:02Z",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    ];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return responses.shift() ?? new Response(null, { status: 500 });
    });

    await client.createIndexVersion(
      indexVersion.knowledgeSpaceId,
      indexVersion.embeddingModelConfigurationId,
    );
    await client.listIndexVersions(indexVersion.knowledgeSpaceId);
    await client.publishIndexVersion(
      indexVersion.knowledgeSpaceId,
      indexVersion.id,
      1,
    );

    expect(requests.map((request) => request.method)).toEqual([
      "POST",
      "GET",
      "POST",
    ]);
    expect(requests[0]?.url).toContain(
      `/knowledge-spaces/${indexVersion.knowledgeSpaceId}/index-versions`,
    );
    expect(requests[1]?.url).toContain("pageNumber=1&pageSize=20");
    expect(await requests[0]?.json()).toEqual({
      embeddingModelConfigurationId: indexVersion.embeddingModelConfigurationId,
    });
    expect(await requests[2]?.json()).toEqual({ expectedVersion: 1 });
    expect(requests[2]?.headers.get("Idempotency-Key")).toBe(
      "request-key-002",
    );
  });

  it("分页读取可用模型配置时不请求或接收密钥材料", async () => {
    const client = authenticatedClient(async () => new Response(
      JSON.stringify({
        items: [{
          id: "320000000000000001",
          scope: "TENANT",
          displayName: "租户嵌入模型",
          catalogEntryId: "330000000000000001",
          modelType: "EMBEDDING",
          status: "ACTIVE",
          version: 1,
          credentialConfigured: true,
          createdAt: "2026-07-28T00:00:00Z",
        }],
        pageNumber: 1,
        pageSize: 20,
        totalElements: 1,
        totalPages: 1,
        hasPrevious: false,
        hasNext: false,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ));

    const page = await client.listModelConfigurations();

    expect(page.items).toHaveLength(1);
    expect(JSON.stringify(page.items[0])).not.toMatch(
      /api.?key|ciphertext|nonce|fingerprint/i,
    );
  });

  it("按租户会话读取用量和脱敏审计分页", async () => {
    const requests: Request[] = [];
    const responses = [
      new Response(
        JSON.stringify({
          from: "2026-07-27T00:00:00Z",
          to: "2026-07-28T00:00:00Z",
          acceptedQueries: 2,
          answeredQueries: 1,
          refusedQueries: 1,
          failedQueries: 0,
          runningQueries: 0,
          inputTokens: 5,
          outputTokens: 8,
          totalTokens: 13,
          dailyQuota: {
            used: 2,
            limit: 100,
            resetsAt: "2026-07-28T00:00:00Z",
          },
          concurrentQuota: { used: 0, limit: 4 },
          generatedAt: "2026-07-27T08:00:00Z",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
      new Response(
        JSON.stringify({
          items: [],
          pageNumber: 2,
          pageSize: 5,
          totalElements: 7,
          totalPages: 2,
          hasPrevious: true,
          hasNext: false,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    ];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return responses.shift() ?? new Response(null, { status: 500 });
    });

    const usage = await client.getQueryUsage(
      "2026-07-27T00:00:00Z",
      "2026-07-28T00:00:00Z",
    );
    const audit = await client.listQueryAuditRecords(2, 5);

    expect(client.hasSession()).toBe(true);
    expect(usage.dailyQuota.limit).toBe(100);
    expect(audit.pageNumber).toBe(2);
    expect(audit.hasNext).toBe(false);
    expect(requests[0]?.url).toContain(
      "from=2026-07-27T00%3A00%3A00Z",
    );
    expect(requests[1]?.url).toContain("pageNumber=2");
    expect(requests[1]?.url).toContain("pageSize=5");
    expect(requests.every((request) =>
      request.headers.get("Authorization") === "Bearer demo-token"
    )).toBe(true);
  });
});

function authenticatedClient(fetcher: typeof fetch) {
  const client = new ControlPlaneClient(
    fetcher,
    () => "request-key-002",
    "http://localhost/api/v1",
  );
  client.restoreSession("demo-token");
  return client;
}
