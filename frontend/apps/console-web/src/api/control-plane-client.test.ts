import { describe, expect, it } from "vitest";
import type { BrowserSessionGateway } from "@foundation/browser-session";
import {
  ApiRequestError,
  ControlPlaneClient,
} from "./control-plane-client";

describe("ControlPlaneClient", () => {
  it("登录后为写请求使用浏览器会话和幂等键", async () => {
    const requests: Request[] = [];
    const responses = [
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
      async () => new Response(null, { status: 500 }),
      () => "request-key-001",
      "http://localhost/api/v1",
      fakeBrowserSession(async (input, init) => {
        requests.push(new Request(input, init));
        return responses.shift() ?? new Response(null, { status: 500 });
      }),
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

    expect(requests).toHaveLength(1);
    expect(requests[0]?.headers.get("Authorization")).toBeNull();
    expect(requests[0]?.headers.get("Idempotency-Key")).toBe(
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

  it("登记结构化数据集时只提交冻结定义和私有 UTF-8 CSV", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({
        dataSourceId: "220000000000000001",
        knowledgeSpaceId: "210000000000000001",
        status: "READY",
        version: 0,
        failedOperation: null,
        syncArtifactId: null,
        qualityStatus: null,
      }), { status: 201, headers: { "Content-Type": "application/json" } });
    });

    await client.registerStructuredDataset({
      knowledgeSpaceId: "210000000000000001",
      name: "门店商品数据集",
      mappingVersion: "dataset-v1",
      contentField: "content",
      titleField: "title",
      fields: [
        { fieldName: "content", logicalType: "TEXT", sensitivityLevel: "PUBLIC" },
        { fieldName: "title", logicalType: "TEXT", sensitivityLevel: "PUBLIC" },
      ],
      rowFilter: "NON_BLANK_CONTENT",
    }, new File(["content,title\\n气泡水,青柚气泡水\\n"], "products.csv", {
      type: "text/csv; charset=utf-8",
    }));

    expect(request?.method).toBe("POST");
    expect(request?.url).toContain("/governed-data-sources/structured-datasets");
    expect(request?.headers.get("Authorization")).toBeNull();
    expect(request?.headers.get("Content-Type")).toContain("multipart/form-data;");
    const form = await request?.formData();
    expect(JSON.parse(await (form?.get("definition") as File).text())).toMatchObject({
      knowledgeSpaceId: "210000000000000001",
      contentField: "content",
      rowFilter: "NON_BLANK_CONTENT",
    });
    const csv = form?.get("csv") as File;
    expect(csv.name).toBe("products.csv");
    expect(csv.type).toBe("text/csv; charset=utf-8");
    expect(await csv.text()).toContain("气泡水");
  });

  it("探测受治理文件来源时携带知识空间和乐观版本", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(
        JSON.stringify({
          dataSourceId: "220000000000000001",
          knowledgeSpaceId: "210000000000000001",
          status: "READY",
          version: 2,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    });

    await client.probeFileDataSource(
      "210000000000000001",
      "220000000000000001",
      1,
    );

    expect(request?.method).toBe("POST");
    expect(request?.url).toContain(
      "/governed-data-sources/files/220000000000000001/probe",
    );
    expect(request?.url).toContain("knowledgeSpaceId=210000000000000001");
    expect(request?.url).toContain("expectedVersion=1");
    expect(request?.headers.get("Authorization")).toBeNull();
  });

  it("同步、发布和退役受治理文件时只提交资源标识和乐观版本", async () => {
    const requests: Request[] = [];
    const lifecycle = {
      dataSourceId: "220000000000000001",
      knowledgeSpaceId: "210000000000000001",
      status: "READY",
      version: 3,
      failedOperation: null,
      syncArtifactId: "230000000000000001",
      qualityStatus: "PASS",
    };
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return new Response(JSON.stringify(lifecycle), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    await client.synchronizeFileDataSource(
      lifecycle.knowledgeSpaceId,
      lifecycle.dataSourceId,
      2,
    );
    await client.publishFileSyncArtifact(
      lifecycle.knowledgeSpaceId,
      lifecycle.dataSourceId,
      lifecycle.syncArtifactId,
      3,
    );
    await client.retireFileDataSource(
      lifecycle.knowledgeSpaceId,
      lifecycle.dataSourceId,
      4,
    );

    expect(requests.map((request) => request.method)).toEqual(["POST", "POST", "POST"]);
    expect(requests[0]?.url).toContain(
      "/governed-data-sources/files/220000000000000001/synchronization",
    );
    expect(requests[1]?.url).toContain(
      "/files/220000000000000001/artifacts/230000000000000001/publish",
    );
    expect(requests[2]?.url).toContain(
      "/governed-data-sources/files/220000000000000001/retire",
    );
    expect(requests.every((request) => request.url.includes("knowledgeSpaceId=210000000000000001"))).toBe(true);
    expect(requests[0]?.url).toContain("expectedVersion=2");
    expect(requests[1]?.url).toContain("expectedVersion=3");
    expect(requests[2]?.url).toContain("expectedVersion=4");
  });

  it("同步并发布结构化数据集时只提交资源标识和乐观版本", async () => {
    const requests: Request[] = [];
    const lifecycle = {
      dataSourceId: "220000000000000001",
      knowledgeSpaceId: "210000000000000001",
      status: "READY",
      version: 2,
      failedOperation: null,
      syncArtifactId: "230000000000000001",
      qualityStatus: "PASS",
    };
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return new Response(JSON.stringify(lifecycle), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    await client.synchronizeStructuredDataset(
      lifecycle.knowledgeSpaceId,
      lifecycle.dataSourceId,
      0,
    );
    await client.publishStructuredDatasetSyncArtifact(
      lifecycle.knowledgeSpaceId,
      lifecycle.dataSourceId,
      lifecycle.syncArtifactId,
      1,
    );

    expect(requests.map((request) => request.method)).toEqual(["POST", "POST"]);
    expect(requests[0]?.url).toContain(
      "/governed-data-sources/structured-datasets/220000000000000001/synchronization",
    );
    expect(requests[1]?.url).toContain(
      "/structured-datasets/220000000000000001/artifacts/230000000000000001/publish",
    );
    expect(requests.every((request) => request.url.includes("knowledgeSpaceId=210000000000000001"))).toBe(true);
    expect(requests[0]?.url).toContain("expectedVersion=0");
    expect(requests[1]?.url).toContain("expectedVersion=1");
  });

  it("登记、探测、同步并发布对象存储来源时只提交平台签发范围与资源标识", async () => {
    const requests: Request[] = [];
    const lifecycle = {
      dataSourceId: "220000000000000001",
      knowledgeSpaceId: "210000000000000001",
      status: "READY",
      version: 2,
      failedOperation: null,
      syncArtifactId: "230000000000000001",
      qualityStatus: "PASS",
    };
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return new Response(JSON.stringify(lifecycle), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    await client.registerObjectStorageDataSource(
      lifecycle.knowledgeSpaceId,
      "门店公告范围",
      "range-store-notices",
      7,
    );
    await client.probeObjectStorageDataSource(
      lifecycle.knowledgeSpaceId,
      lifecycle.dataSourceId,
      0,
    );
    await client.synchronizeObjectStorageDataSource(
      lifecycle.knowledgeSpaceId,
      lifecycle.dataSourceId,
      0,
    );
    await client.publishObjectStorageSyncArtifact(
      lifecycle.knowledgeSpaceId,
      lifecycle.dataSourceId,
      lifecycle.syncArtifactId,
      1,
    );

    expect(requests.map((request) => request.method)).toEqual(["POST", "POST", "POST", "POST"]);
    expect(requests[0]?.url).toContain("/governed-data-sources/object-storage");
    expect(requests[0]?.url).toContain("rangeId=range-store-notices");
    expect(requests[0]?.url).toContain("selectionRevision=7");
    expect(requests[1]?.url).toContain("/object-storage/220000000000000001/probe");
    expect(requests[2]?.url).toContain("/object-storage/220000000000000001/synchronization");
    expect(requests[3]?.url).toContain("/object-storage/220000000000000001/artifacts/230000000000000001/publish");
    expect(requests.every((request) => request.headers.get("Authorization") === null)).toBe(true);
  });

  it("分页读取FILE来源时只接收任务恢复所需的非敏感摘要", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({
        items: [{
          dataSourceId: "220000000000000001",
          knowledgeSpaceId: "210000000000000001",
          displayName: "商品说明",
          status: "READY",
          version: 5,
          failedOperation: null,
          publishedArtifactId: "230000000000000001",
          publishedQualityStatus: "PASS",
          publishedAt: "2026-07-29T02:05:00Z",
        }],
        pageNumber: 1,
        pageSize: 20,
        totalElements: 1,
        totalPages: 1,
        hasPrevious: false,
        hasNext: false,
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });

    const page = await client.listGovernedFileDataSources("210000000000000001");

    expect(request?.url).toContain("/governed-data-sources/files?knowledgeSpaceId=210000000000000001");
    expect(page.items[0]?.publishedArtifactId).toBe("230000000000000001");
    expect(JSON.stringify(page.items[0])).not.toMatch(
      /object.?key|content|digest|api.?key|credential/i,
    );
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
    ), undefined, "http://localhost/api/v1", fakeBrowserSession(async () => new Response(
      JSON.stringify({
        code: "KNOWLEDGE_DATA.AUTHORIZATION.0001",
        message: "没有执行该操作的权限",
        traceId: "trace-001",
        details: {},
      }),
      { status: 403, headers: { "Content-Type": "application/json" } },
    )));

    await expect(client.listKnowledgeSpaces()).rejects.toEqual(
      new ApiRequestError(
        403,
        "KNOWLEDGE_DATA.AUTHORIZATION.0001",
        "没有执行该操作的权限",
        "trace-001",
      ),
    );
  });

  it("当前会话收到畸形 401 后清除令牌", async () => {
    const client = authenticatedClient(async () => new Response("malformed", {
      status: 401,
      headers: { "Content-Type": "text/plain" },
    }));
    const changes: boolean[] = [];
    client.subscribeSessionChange((active) => changes.push(active));

    await expect(client.listKnowledgeSpaces()).rejects.toBeInstanceOf(ApiRequestError);

    expect(client.hasSession()).toBe(false);
    expect(changes).toEqual([false]);
  });

  it("旧会话请求在新登录后返回 401 时保留新令牌", async () => {
    let resolveOldRequest: ((response: Response) => void) | undefined;
    const oldRequest = new Promise<Response>((resolve) => {
      resolveOldRequest = resolve;
    });
    const client = authenticatedClient(async (input) => {
      if (String(input).endsWith("/auth/sessions")) {
        return new Response(JSON.stringify({
          tokenName: "Authorization",
          tokenValue: "new-token",
          expiresInSeconds: 1800,
        }), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        });
      }
      return oldRequest;
    });

    const request = client.listKnowledgeSpaces();
    await client.login({
      tenantCode: "demo-store",
      loginName: "store-admin",
      password: "demo-password",
    });
    resolveOldRequest?.(new Response("expired", { status: 401 }));

    await expect(request).rejects.toBeInstanceOf(ApiRequestError);
    expect(client.hasSession()).toBe(true);
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

  it("从已发布目录创建模型配置时仅在写入请求中提交凭据", async () => {
    const requests: Request[] = [];
    const responses = [
      new Response(JSON.stringify({
        items: [{
          id: "330000000000000001",
          providerKind: "OPENAI_COMPATIBLE",
          endpoint: "https://example.com/v1",
          modelCode: "test-chat-model",
          modelType: "CHAT",
          contextWindow: 32768,
          outputLimit: 4096,
          embeddingDimension: null,
          status: "PUBLISHED",
          version: 1,
          createdAt: "2026-07-28T00:00:00Z",
        }],
        pageNumber: 1,
        pageSize: 20,
        totalElements: 1,
        totalPages: 1,
        hasPrevious: false,
        hasNext: false,
      }), { status: 200, headers: { "Content-Type": "application/json" } }),
      new Response(JSON.stringify({
        id: "320000000000000001",
        scope: "PERSONAL",
        displayName: "我的对话模型",
        catalogEntryId: "330000000000000001",
        modelType: "CHAT",
        status: "DRAFT",
        version: 0,
        credentialConfigured: true,
        createdAt: "2026-07-28T00:00:00Z",
      }), { status: 201, headers: { "Content-Type": "application/json" } }),
    ];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return responses.shift() ?? new Response(null, { status: 500 });
    });

    const catalog = await client.listControlledModelCatalog();
    const created = await client.createModelConfiguration({
      scope: "PERSONAL",
      displayName: "我的对话模型",
      catalogEntryId: catalog.items[0]!.id,
      apiKey: "test-api-key",
    });

    expect(requests[0]?.method).toBe("GET");
    expect(requests[0]?.url).toContain("/controlled-model-catalog?pageNumber=1&pageSize=20");
    expect(requests[1]?.method).toBe("POST");
    expect(await requests[1]?.json()).toEqual({
      scope: "PERSONAL",
      displayName: "我的对话模型",
      catalogEntryId: "330000000000000001",
      apiKey: "test-api-key",
    });
    expect(JSON.stringify(created)).not.toMatch(
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
    expect(requests.every((request) => request.headers.get("Authorization") === null)).toBe(true);
  });
});

function authenticatedClient(fetcher: typeof fetch) {
  const client = new ControlPlaneClient(
    fetcher,
    () => "request-key-002",
    "http://localhost/api/v1",
    fakeBrowserSession(fetcher),
  );
  return client;
}

function fakeBrowserSession(fetcher: typeof fetch): BrowserSessionGateway {
  const capability = {
    workspaceCategories: ["TENANT"],
    displayRoles: ["TENANT_MEMBER"],
    maximumSessionAgeSeconds: 3600,
  };
  return {
    login: async () => capability,
    restore: async () => capability,
    logout: async () => undefined,
    request: async (path, init) => fetcher(`http://localhost/api/v1${path}`, init),
  };
}
