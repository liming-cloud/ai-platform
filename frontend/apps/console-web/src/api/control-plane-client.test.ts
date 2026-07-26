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
          id: "20000000-0000-0000-0000-000000000001",
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
          id: "30000000-0000-0000-0000-000000000001",
          knowledgeSpaceId: "20000000-0000-0000-0000-000000000001",
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
      "20000000-0000-0000-0000-000000000001",
      "30000000-0000-0000-0000-000000000001",
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
        code: "ACCESS_DENIED",
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
        "ACCESS_DENIED",
        "没有执行该操作的权限",
        "trace-001",
      ),
    );
  });

  it("按知识空间创建、查询并发布索引版本", async () => {
    const requests: Request[] = [];
    const indexVersion = {
      id: "40000000-0000-0000-0000-000000000001",
      knowledgeSpaceId: "20000000-0000-0000-0000-000000000001",
      sourceSnapshotDigest: "sha256:source",
      chunkPolicy: "paragraph-window-v1",
      embeddingDeploymentId: "00000000-0000-0000-0000-000000000128",
      embeddingDeploymentRevision: 1,
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
        JSON.stringify({ items: [indexVersion], nextCursor: null }),
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
      indexVersion.embeddingDeploymentId,
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
    expect(await requests[0]?.json()).toEqual({
      embeddingDeploymentId: indexVersion.embeddingDeploymentId,
    });
    expect(await requests[2]?.json()).toEqual({ expectedVersion: 1 });
    expect(requests[2]?.headers.get("Idempotency-Key")).toBe(
      "request-key-002",
    );
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
