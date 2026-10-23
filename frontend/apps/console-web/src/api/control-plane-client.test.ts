import { describe, expect, it } from "vitest";
import type { BrowserSessionGateway } from "@foundation/browser-session";
import {
  ApiRequestError,
  ControlPlaneClient,
  presentApiError,
} from "./control-plane-client";

describe("ControlPlaneClient", () => {
  it("部署名称摘要查询只编码为有界目录的只读参数", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init)); return Response.json({});
    });
    await client.listPlatformModelDeployments(3, 10, "中文 %_\\&");
    const query = new URL(requests[0]!.url).searchParams;
    expect([...query.keys()].sort()).toEqual(["pageNumber", "pageSize", "q"]);
    expect(query.get("q")).toBe("中文 %_\\&");
    expect(query.get("pageNumber")).toBe("3");
    expect(requests[0]!.method).toBe("GET");
    expect(requests[0]!.body).toBeNull();
    expect(requests[0]!.headers.get("X-Tenant-Id")).toBeNull();
  });
  it("将发送阶段网络失败标识为可区分的安全错误，不泄漏底层消息", async () => {
    const client = authenticatedClient(async () => { throw new TypeError("synthetic-network-private-detail"); });
    await expect(client.getPlatformProviderConnectionDirectorySummary()).rejects.toEqual(
      new ApiRequestError(0, "NETWORK_UNAVAILABLE", "请求未完成，请稍后重试。", ""),
    );
    expect(client.hasSession()).toBe(true);
  });

  it("成功响应解析失败不伪装为网络不可用", async () => {
    const response = new Response("invalid-json");
    const client = authenticatedClient(async () => response);
    await expect(client.getPlatformProviderConnectionDirectorySummary()).rejects.toBeInstanceOf(SyntaxError);
    const consumed = Response.json({}); await consumed.json();
    const bodyClient = authenticatedClient(async () => consumed);
    await expect(bodyClient.getPlatformProviderConnectionDirectorySummary()).rejects.not.toBeInstanceOf(ApiRequestError);
  });
  it("连接与候选字面搜索安全编码且发布摘要独立只读", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init)); return Response.json({ publishedConnections: 21 });
    });
    await client.listPlatformProviderConnections(2, 10, "中文 %_\\&");
    await client.listPlatformProviderModelCandidates("610000000000000001", 3, 6, 20, "对话模型");
    expect(new URL(requests[0]!.url).searchParams.get("q")).toBe("中文 %_\\&");
    expect(new URL(requests[1]!.url).searchParams.get("q")).toBe("对话模型");
    expect(new URL(requests[1]!.url).searchParams.get("expectedConnectionVersion")).toBe("3");
    expect(await client.getPlatformProviderConnectionDirectorySummary()).toEqual({ publishedConnections: 21 });
    expect(requests[2]!.url).toBe("http://localhost/api/v1/platform/model-supply/connections/summary");
    expect(requests.every(request => request.method === "GET" && request.body === null && request.headers.get("X-Tenant-Id") === null)).toBe(true);
  });
  it("平台连接详情绑定精确 ID 与版本，发布使用受保护写入且不携带身份范围", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return Response.json({ connectionId: "610000000000000001", connectionRevision: 7, published: true });
    });
    await client.getPlatformProviderConnection("610000000000000001");
    await client.getPlatformProviderConnectionPublication("610000000000000001", 7);
    await client.publishPlatformProviderConnection("610000000000000001", 7);
    expect(requests.map(request => [request.method, request.url])).toEqual([
      ["GET", "http://localhost/api/v1/platform/model-supply/connections/610000000000000001"],
      ["GET", "http://localhost/api/v1/platform/model-supply/connections/610000000000000001/publication?expectedVersion=7"],
      ["POST", "http://localhost/api/v1/platform/model-supply/connections/610000000000000001/publish"],
    ]);
    expect(requests[0]?.body).toBeNull();
    expect(requests[1]?.body).toBeNull();
    expect(await requests[2]?.json()).toEqual({ expectedVersion: 7 });
    expect(requests[2]?.headers.get("Idempotency-Key")).toBe("request-key-002");
    expect(requests[2]?.headers.get("Content-Type")).toContain("application/json");
    expect(requests.every(request => ["Authorization", "X-Tenant-Id", "X-Subject-Id"]
      .every(name => request.headers.get(name) === null))).toBe(true);
  });
  it("成员功能独立读写只携带四布尔值与两个版本，不混入助手或身份范围", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return Response.json({ membershipId: "3382738893456539659", featureVersion: 2 });
    });
    const features = { assistantSelection: false, conversation: true, ownHistory: true, authorizedCitations: false };
    await client.getMemberFeatures("3382738893456539659");
    await client.updateMemberFeatures("3382738893456539659", { conversationFeatures: features, expectedFeatureVersion: 1, expectedTenantFeatureVersion: 4 });
    expect(requests.map(value => [value.method, value.url])).toEqual([
      ["GET", "http://localhost/api/v1/tenant/members/3382738893456539659/features"],
      ["PUT", "http://localhost/api/v1/tenant/members/3382738893456539659/features"],
    ]);
    expect(requests[0]?.body).toBeNull();
    expect(await requests[1]?.json()).toEqual({ conversationFeatures: features, expectedFeatureVersion: 1, expectedTenantFeatureVersion: 4 });
    expect(requests[1]?.headers.get("Authorization")).toBeNull();
  });
  it("工作区名称只读当前会话接口，不发送租户或身份范围", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return Response.json({ displayName: "青岚科技" });
    });
    expect(await client.getTenantWorkspaceContext()).toEqual({ displayName: "青岚科技" });
    expect(requests.map(request => [request.method, request.url])).toEqual([
      ["GET", "http://localhost/api/v1/tenant/workspace-context"],
    ]);
    expect(requests[0]?.body).toBeNull();
  });

  it("租户概览和配额从独立受保护接口读取且保留容量精度", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      const request = new Request(input, init); requests.push(request);
      return Response.json(request.url.endsWith("/quotas") ? { bytesPerKnowledgeBase: "9223372036854775807", version: 0 } : { tenantId: "610000000000000001", displayName: "青岚科技" });
    });
    expect((await client.getTenantLifecycle("610000000000000001")).displayName).toBe("青岚科技");
    expect((await client.getTenantQuotas("610000000000000001")).bytesPerKnowledgeBase).toBe("9223372036854775807");
    expect(requests.map(request => [request.method, request.url])).toEqual([
      ["GET", "http://localhost/api/v1/platform/tenants/610000000000000001"],
      ["GET", "http://localhost/api/v1/platform/tenants/610000000000000001/quotas"],
    ]);
  });
  it("配额用量与编辑使用独立接口、版本和受保护写入头", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      const request = new Request(input, init); requests.push(request);
      return Response.json(request.method === "GET"
        ? { knowledgeBases: 1, documentsPerKnowledgeBase: 3, bytesPerKnowledgeBase: "6888", complete: true }
        : { knowledgeBases: 3, documentsPerKnowledgeBase: 1000, bytesPerKnowledgeBase: "10000000000", maxFileBytes: "50000000", maxTextBytes: "1048576", version: 2 });
    });
    expect((await client.getTenantQuotaUsage("610000000000000001")).bytesPerKnowledgeBase).toBe("6888");
    await client.updateTenantQuotas("610000000000000001", {
      knowledgeBases: 3, documentsPerKnowledgeBase: 1000,
      bytesPerKnowledgeBase: "10000000000", maxFileBytes: "50000000", maxTextBytes: "1048576",
      expectedVersion: 1,
    });
    expect(requests.map(request => [request.method, request.url])).toEqual([
      ["GET", "http://localhost/api/v1/platform/tenants/610000000000000001/quotas/usage"],
      ["PUT", "http://localhost/api/v1/platform/tenants/610000000000000001/quotas"],
    ]);
    expect(await requests[1]?.json()).toMatchObject({ expectedVersion: 1, bytesPerKnowledgeBase: "10000000000" });
    expect(requests[1]?.headers.get("Content-Type")).toContain("application/json");
  });
  it("租户登录安全按独立接口读取并以版本完整保存三项策略", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      const request = new Request(input, init); requests.push(request);
      return Response.json({ sessionHours: 8, failedAttempts: 5, lockMinutes: 15, version: 2 });
    });
    expect((await client.getTenantLoginSecurity("610000000000000001")).sessionHours).toBe(8);
    await client.updateTenantLoginSecurity("610000000000000001", {
      sessionHours: 8, failedAttempts: 5, lockMinutes: 15, expectedVersion: 1,
    });
    expect(requests.map(request => [request.method, request.url])).toEqual([
      ["GET", "http://localhost/api/v1/platform/tenants/610000000000000001/security"],
      ["PUT", "http://localhost/api/v1/platform/tenants/610000000000000001/security"],
    ]);
    expect(await requests[1]?.json()).toEqual({ sessionHours: 8, failedAttempts: 5,
      lockMinutes: 15, expectedVersion: 1 });
    expect(requests[1]?.headers.get("Content-Type")).toContain("application/json");
  });
  it("运营总览和平台租户摘要只使用固定统计范围与服务端分页", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return new Response(JSON.stringify(requests.length === 3 ? {
        items: [], pageNumber: 2, pageSize: 20, totalElements: 0, totalPages: 0,
        hasPrevious: true, hasNext: false,
      } : { range: "DAY_30", notices: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    await client.getTenantOperationsOverview("DAY_30");
    await client.getPlatformOperationsOverview("DAY_30");
    await client.listPlatformTenantOperations("DAY_30", 2, 20);

    expect(requests.map((request) => request.url)).toEqual([
      "http://localhost/api/v1/operations/overview?range=DAY_30",
      "http://localhost/api/v1/platform/operations/overview?range=DAY_30",
      "http://localhost/api/v1/platform/operations/tenants?range=DAY_30&page=2&size=20",
    ]);
  });

  it("账户候选选择器只返回可理解摘要且使用服务端搜索分页", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({
        items: [{ accountReference: "opaque-1", loginName: "tenant.admin", status: "ACTIVE" }],
        pageNumber: 1, pageSize: 20, totalElements: 1, totalPages: 1,
        hasPrevious: false, hasNext: false,
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });

    const result = await client.listPlatformAccountCandidates("tenant admin", 1, 20);

    expect(request?.url).toBe("http://localhost/api/v1/platform/accounts/candidates?purpose=TENANT_INITIAL_ADMINISTRATOR&q=tenant+admin&page=1&size=20");
    expect(result.items[0]).toEqual({
      accountReference: "opaque-1",
      loginName: "tenant.admin",
      status: "ACTIVE",
    });
  });

  it("成员助手候选只读取本租户真实已发布助手并使用服务端分页", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({
        items: [{ accountReference: "opaque-2", loginName: "tenant.user", status: "ACTIVE" }],
        pageNumber: 1, pageSize: 20, totalElements: 1, totalPages: 1,
        hasPrevious: false, hasNext: false,
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });

    await client.listMemberAssistants("tenant user", 1, 20);

    expect(request?.url).toBe("http://localhost/api/v1/tenant/member-assistants?q=tenant+user&pageNumber=1&pageSize=20");
  });

  it("评测资源通过服务端分页安全摘要选择", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return new Response(JSON.stringify({
        items: [], pageNumber: 2, pageSize: 20, totalElements: 0, totalPages: 0,
        hasPrevious: true, hasNext: false,
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });

    await client.listEvaluationDatasetCandidates(2, 20);
    await client.listEvaluationAssistantCandidates(2, 20);

    expect(requests.map((request) => request.url)).toEqual([
      "http://localhost/api/v1/evaluation-datasets/candidates?page=2&size=20",
      "http://localhost/api/v1/query-assistants?pageNumber=2&pageSize=20",
    ]);
  });

  it("对象存储授权范围通过知识空间内的名称化候选选择", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({ items: [], pageNumber: 1, pageSize: 20,
        totalElements: 0, totalPages: 0, hasPrevious: false, hasNext: false }),
      { status: 200, headers: { "Content-Type": "application/json" } });
    });

    await client.listPlatformObjectRangeCandidates("space/one", 1, 20);

    expect(request?.url).toBe("http://localhost/api/v1/knowledge-spaces/space%2Fone/object-storage-ranges?page=1&size=20");
  });

  it("用户提示按故障类型解释下一步且不泄露后端字段", () => {
    const failures = [
      new ApiRequestError(400, "COMMON.REQUEST.0001", "SQL error modelId=42", "trc-secret"),
      new ApiRequestError(401, "AUTH.0001", "token expired", "trc-secret"),
      new ApiRequestError(403, "AUTH.0002", "denied", "trc-secret"),
      new ApiRequestError(409, "TENANT.CONFLICT", "duplicate tenantId=42", "trc-secret"),
      new ApiRequestError(429, "RATE_LIMIT", "vendor raw response", "trc-secret"),
      new ApiRequestError(503, "UPSTREAM", "endpoint failed", "trc-secret"),
      new ApiRequestError(500, "INTERNAL", "stack trace", "trc-secret"),
    ].map((error) => presentApiError(error, "建立租户"));

    expect(failures.map((failure) => failure.title)).toEqual([
      "建立租户未完成",
      "登录状态已失效",
      "当前账户不能执行此操作",
      "建立租户未完成",
      "请求过于频繁",
      "依赖服务暂时不可用",
      "服务暂时不可用",
    ]);
    expect(JSON.stringify(failures)).not.toMatch(/trace|sql|modelId|tenantId|vendor|endpoint|stack|COMMON|AUTH/i);
  });

  it("最后一位租户管理员保护提供可执行的恢复指引", () => {
    const failure = presentApiError(new ApiRequestError(
      409,
      "ORGANIZATION_ACCESS.CONFLICT.0003",
      "不能停用租户最后一个启用管理员",
      "trc-secret",
    ), "停用租户管理员");

    expect(failure).toEqual({
      title: "必须保留一位启用管理员",
      description: "请先分配或恢复另一位管理员，再停用当前管理员。",
    });
    expect(JSON.stringify(failure)).not.toMatch(/ORGANIZATION_ACCESS|trc-secret/i);
  });

  it("建立平台账户只写入登录名和一次性初始口令", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({
        subjectId: "102",
        loginName: "new-account",
        status: "ACTIVE",
        version: 0,
        createdAt: "2026-08-11T04:00:00Z",
        activeRoleCodes: [],
      }), { status: 201, headers: { "Content-Type": "application/json" } });
    });

    await client.createPlatformAccount({ loginName: "new-account", password: "initial-password" });

    expect(request?.method).toBe("POST");
    expect(request?.url).toContain("/platform/accounts");
    expect(request?.headers.get("Authorization")).toBeNull();
    expect(await request?.json()).toEqual({ loginName: "new-account", password: "initial-password" });
  });

  it("重置平台账户口令只写入目标路径和一次性新口令", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(null, { status: 204 });
    });

    await client.resetPlatformAccountPassword("102", "new-password");

    expect(request?.method).toBe("POST");
    expect(request?.url).toContain("/platform/accounts/102/password-reset");
    expect(request?.headers.get("Authorization")).toBeNull();
    expect(await request?.json()).toEqual({ password: "new-password" });
  });

  it("创建和轮换供应商连接时只写入受控业务字段与一次性 API Key", async () => {
    const requests: Request[] = [];
    const connection = {
      id: "9301",
      providerKind: "OPENAI_COMPATIBLE",
      displayName: "企业推理服务",
      region: "cn-beijing",
      authenticationType: "API_KEY",
      credentialConfigured: true,
      status: "DRAFT",
      version: 1,
      lastProbeSucceeded: null,
      lastProbeCode: null,
      lastProbedAt: null,
      createdAt: "2026-08-10T08:00:00Z",
    };
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return new Response(JSON.stringify(connection), {
        status: requests.length === 1 ? 201 : 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    await client.createProviderConnection({
      providerKind: "OPENAI_COMPATIBLE",
      displayName: "企业推理服务",
      endpoint: "https://example.invalid/v1",
      region: "cn-beijing",
      authenticationType: "API_KEY",
      apiKey: "provider-api-key",
    });
    await client.rotateProviderConnectionCredential("9301", 1, "next-api-key");
    await client.probeProviderConnection("9301", 2, "gpt-4o-mini", 10_000);

    expect(requests).toHaveLength(3);
    expect(requests[0]?.url).toContain("/model-providers");
    expect(requests[0]?.headers.get("Idempotency-Key")).not.toBeNull();
    expect(await requests[0]?.json()).toEqual({
      providerKind: "OPENAI_COMPATIBLE",
      displayName: "企业推理服务",
      endpoint: "https://example.invalid/v1",
      region: "cn-beijing",
      authenticationType: "API_KEY",
      apiKey: "provider-api-key",
    });
    expect(requests[1]?.url).toContain("/model-providers/9301/credential-rotation");
    expect(await requests[1]?.json()).toEqual({
      expectedVersion: 1,
      apiKey: "next-api-key",
    });
    expect(requests[2]?.url).toContain("/model-providers/9301/probe");
    expect(await requests[2]?.json()).toEqual({
      expectedVersion: 2,
      modelCode: "gpt-4o-mini",
      timeoutMillis: 10_000,
    });
  });

  it("触发评测运行时只发送数据集和助手版本标识", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({
        id: "9201",
        assistantVersionId: "9101",
        assistantVersionRevision: 7,
        datasetId: "9001",
        datasetVersion: 1,
        gatePolicyVersion: "quality-gate-v1",
        metricsImplementationVersion: "evaluation-metrics-v1",
        status: "PASSED",
        expectedEvidenceCount: 1,
        retrievedExpectedEvidenceCount: 1,
        citationCount: 1,
        correctCitationCount: 1,
        expectedRefusalCount: 1,
        correctRefusalCount: 1,
        maximumRetrievedCandidateCount: 1,
        failedEntryCount: 0,
        unauthorizedEvidence: false,
        metricsComputable: true,
        recallAt10: 1,
        citationCorrectness: 1,
        correctRefusalRate: 1,
        version: 1,
        createdAt: "2026-08-05T08:01:00Z",
        completedAt: "2026-08-05T08:02:00Z",
      }), { status: 201, headers: { "Content-Type": "application/json" } });
    });

    const run = await client.executeEvaluationRun("9001", "9101");

    expect(run.id).toBe("9201");
    expect(request?.method).toBe("POST");
    expect(request?.url).toContain("/evaluation-runs");
    expect(request?.headers.get("Authorization")).toBeNull();
    expect(await request?.json()).toEqual({
      datasetId: "9001",
      assistantVersionId: "9101",
    });
    expect(JSON.stringify(run)).not.toMatch(
      /privateContentReference|input|answer|modelRouteFingerprint/i,
    );
  });

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
        "当前账户不能执行此操作",
        "",
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

  it("收到其他同源页签的失效事实后只清除本页会话状态", () => {
    const client = authenticatedClient(async () => new Response(null, {
      status: 204,
    }));
    const changes: boolean[] = [];
    client.subscribeSessionChange((active) => changes.push(active));

    client.discardSession();

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

  it("平台租户生命周期只提交受管业务目标并使用固定状态迁移路径", async () => {
    const requests: Request[] = [];
    const lifecycle = {
      tenantId: "610000000000000001",
      tenantCode: "demo-campus",
      displayName: "演示校园",
      initialAdministratorSubjectId: "510000000000000001",
      status: "PROVISIONING",
      version: 0,
    };
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return new Response(JSON.stringify(lifecycle), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    await client.provisionTenant({
      tenantCode: lifecycle.tenantCode,
      displayName: lifecycle.displayName,
      administrator: { loginName: "qinglan.admin", password: "仅测试密码" },
      quotas: { knowledgeBases: 3, documentsPerKnowledgeBase: 1000, bytesPerKnowledgeBase: "10000000000", maxFileBytes: "50000000", maxTextBytes: "1048576" },
    });
    await client.activateTenant(lifecycle.tenantId);
    await client.suspendTenant(lifecycle.tenantId);
    await client.restoreTenant(lifecycle.tenantId);

    expect(requests.map((request) => request.url)).toEqual([
      "http://localhost/api/v1/platform/tenants",
      "http://localhost/api/v1/platform/tenants/610000000000000001/lifecycle/activate",
      "http://localhost/api/v1/platform/tenants/610000000000000001/lifecycle/suspend",
      "http://localhost/api/v1/platform/tenants/610000000000000001/lifecycle/restore",
    ]);
    expect(requests.map((request) => request.method)).toEqual(["POST", "POST", "POST", "POST"]);
    expect(await requests[0]?.json()).toEqual({
      tenantCode: lifecycle.tenantCode,
      displayName: lifecycle.displayName,
      administrator: { loginName: "qinglan.admin", password: "仅测试密码" },
      quotas: { knowledgeBases: 3, documentsPerKnowledgeBase: 1000, bytesPerKnowledgeBase: "10000000000", maxFileBytes: "50000000", maxTextBytes: "1048576" },
    });
  });

  it("平台生命周期工作区从服务端分页投影读取而不依赖最近一次写入响应", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({
        items: [{
          tenantId: "610000000000000001",
          tenantCode: "demo-campus",
          displayName: "演示校园",
          status: "ACTIVE",
          version: 2,
          createdAt: "2026-08-03T03:00:00Z",
          lastChangedAt: "2026-08-03T04:00:00Z",
        }],
        pageNumber: 2,
        pageSize: 5,
        totalElements: 6,
        totalPages: 2,
        hasPrevious: true,
        hasNext: false,
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });

    const page = await client.listTenantLifecycles(2, 5);

    expect(request?.url).toBe("http://localhost/api/v1/platform/tenants?pageNumber=2&pageSize=5");
    expect(request?.method).toBe("GET");
    expect(page.items[0]).not.toHaveProperty("initialAdministratorSubjectId");
    expect(page.items[0]?.lastChangedAt).toBe("2026-08-03T04:00:00Z");
  });

  it("租户目录将字面搜索与状态提交给服务端而不在当前页过滤", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({ items: [], pageNumber: 1, pageSize: 10,
        totalElements: 0, totalPages: 0, hasPrevious: false, hasNext: false }), { status: 200 });
    });
    await client.listTenantLifecycles(1, 10, "云杉 %_\\", "SUSPENDED");
    const parameters = new URL(request!.url).searchParams;
    expect(parameters.get("query")).toBe("云杉 %_\\");
    expect(parameters.get("status")).toBe("SUSPENDED");
    expect(parameters.get("pageSize")).toBe("10");
    expect(request?.method).toBe("GET");
  });

  it("平台租户管理员目录和生命周期只提交目标租户与受控资源引用", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return new Response(JSON.stringify({
        items: [], pageNumber: 1, pageSize: 20, totalElements: 0,
        totalPages: 0, hasPrevious: false, hasNext: false,
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });

    await client.listTenantAdministrators("tenant/alpha", 2, 10);
    await client.listTenantAdministratorCandidates("tenant/alpha", "next admin", 3, 10);
    await client.assignTenantAdministrator("tenant/alpha", "801");
    await client.disableTenantAdministrator("tenant/alpha", "701", 4);
    await client.restoreTenantAdministrator("tenant/alpha", "701", 5);
    await client.replaceTenantAdministrator("tenant/alpha", "701", 6, "802");

    expect(requests.map((request) => request.url)).toEqual([
      "http://localhost/api/v1/platform/tenants/tenant%2Falpha/administrators?pageNumber=2&pageSize=10",
      "http://localhost/api/v1/platform/tenants/tenant%2Falpha/administrators/candidates?q=next+admin&pageNumber=3&pageSize=10",
      "http://localhost/api/v1/platform/tenants/tenant%2Falpha/administrators",
      "http://localhost/api/v1/platform/tenants/tenant%2Falpha/administrators/701/disable",
      "http://localhost/api/v1/platform/tenants/tenant%2Falpha/administrators/701/restore",
      "http://localhost/api/v1/platform/tenants/tenant%2Falpha/administrators/701/replace",
    ]);
    expect(await requests[2]?.json()).toEqual({ accountReference: "801" });
    expect(await requests[3]?.json()).toEqual({ expectedVersion: 4 });
    expect(await requests[4]?.json()).toEqual({ expectedVersion: 5 });
    expect(await requests[5]?.json()).toEqual({
      expectedVersion: 6,
      replacementAccountReference: "802",
    });
  });

  it("管理员设置页按服务端条件读取且密码重置只传成员版本", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      const request = new Request(input, init);
      requests.push(request);
      return request.method === "POST"
        ? new Response(null, { status: 204 })
        : new Response(JSON.stringify({ items: [], pageNumber: 2, pageSize: 20,
          totalElements: 0, totalPages: 0, hasPrevious: true, hasNext: false }),
          { status: 200, headers: { "Content-Type": "application/json" } });
    });

    await client.listTenantAdministrators("123", 2, 20, "alice", "DISABLED");
    await client.resetTenantAdministratorPassword("123", "701", 4, "synthetic-only");

    expect(requests[0]?.url).toBe("http://localhost/api/v1/platform/tenants/123/administrators?pageNumber=2&pageSize=20&q=alice&status=DISABLED");
    expect(requests[1]?.url).toBe("http://localhost/api/v1/platform/tenants/123/administrators/701/password-reset");
    expect(await requests[1]?.json()).toEqual({ expectedVersion: 4, password: "synthetic-only" });
  });

  it("平台账户目录从服务端分页投影读取，且不携带浏览器授权头", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({
        items: [{
          subjectId: "510000000000000001",
          loginName: "platform-admin",
          status: "ACTIVE",
          version: 3,
          createdAt: "2026-08-11T03:00:00Z",
        }],
        pageNumber: 3,
        pageSize: 20,
        totalElements: 41,
        totalPages: 3,
        hasPrevious: true,
        hasNext: false,
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });

    const page = await client.listPlatformAccounts(3, 20, "platform", "ACTIVE");

    expect(request?.url).toBe("http://localhost/api/v1/platform/accounts?pageNumber=3&pageSize=20&q=platform&status=ACTIVE");
    expect(request?.method).toBe("GET");
    expect(request?.headers.get("Authorization")).toBeNull();
    expect(page.items[0]).not.toHaveProperty("passwordHash");
    expect(page.items[0]?.loginName).toBe("platform-admin");
  });

  it("平台角色绑定与撤销只提交固定角色代码和路径目标", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return new Response(JSON.stringify({
        bindingId: "3001",
        subjectId: "510000000000000001",
        roleCode: "PLATFORM_ADMINISTRATOR",
        status: requests.length === 1 ? "ACTIVE" : "REVOKED",
        version: requests.length - 1,
        createdAt: "2026-08-11T03:00:00Z",
        revokedAt: requests.length === 1 ? null : "2026-08-11T04:00:00Z",
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });

    await client.bindPlatformRole("510000000000000001", "PLATFORM_ADMINISTRATOR");
    await client.revokePlatformRole("510000000000000001", "PLATFORM_ADMINISTRATOR");

    expect(requests.map((request) => request.url)).toEqual([
      "http://localhost/api/v1/platform/accounts/510000000000000001/role-bindings",
      "http://localhost/api/v1/platform/accounts/510000000000000001/role-bindings/PLATFORM_ADMINISTRATOR",
    ]);
    expect(requests.map((request) => request.method)).toEqual(["POST", "DELETE"]);
    expect(requests[0]?.headers.get("Authorization")).toBeNull();
    expect(await requests[0]?.json()).toEqual({ role: "PLATFORM_ADMINISTRATOR" });
  });

  it("启用平台账户只提交路径目标且不携带 Authorization", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(null, { status: 204 });
    });

    await client.enablePlatformAccount("510000000000000001");

    expect(request?.url).toBe("http://localhost/api/v1/platform/accounts/510000000000000001/enable");
    expect(request?.method).toBe("POST");
    expect(request?.headers.get("Authorization")).toBeNull();
    expect(await request?.text()).toBe("");
  });

  it("普通成员原子开户显式携带初始授权版本且不提交主体角色或租户", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return Response.json({ membershipId: "3382738893456539659", status: "ENABLED", version: 0 });
    });
    const command = { loginName: "member.test", password: "Public-synthetic-test-only!",
      conversationFeatures: { assistantSelection: true, conversation: true, ownHistory: false, authorizedCitations: false },
      assistantIds: [], expectedTenantFeatureVersion: 2, expectedAuthorizationRevision: 3 };
    await client.createTenantMember(command, "member-command-1");
    expect(request?.url).toBe("http://localhost/api/v1/tenant/members");
    expect(request?.method).toBe("POST");
    expect(request?.headers.get("Authorization")).toBeNull();
    expect(request?.headers.get("Idempotency-Key")).toBe("member-command-1");
    expect(await request?.json()).toEqual(command);
  });

  it("模型供应链只读取安全发现摘要，并把服务端分页位置传递给接口", async () => {
    const requests: Request[] = [];
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      return new Response(JSON.stringify({
        items: [{
          discoveryId: "710000000000000001",
          providerConnectionRevision: 4,
          succeeded: true,
          candidateCount: 2,
          resultCode: "DISCOVERY_SUCCEEDED",
          elapsed: "PT0.12S",
          discoveredAt: "2026-08-10T01:00:00Z",
          expiresAt: "2026-08-11T01:00:00Z",
          nextStep: "CREATE_DEPLOYMENT",
        }],
        pageNumber: 2,
        pageSize: 20,
        totalElements: 21,
        totalPages: 2,
        hasPrevious: true,
        hasNext: false,
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });

    const page = await client.listProviderModelDiscoveries("610000000000000001", 2, 20);

    expect(requests).toHaveLength(1);
    expect(requests[0]?.url).toBe(
      "http://localhost/api/v1/model-providers/610000000000000001/model-discoveries?pageNumber=2&pageSize=20",
    );
    expect(requests[0]?.headers.get("Authorization")).toBeNull();
    expect(page.items[0]).not.toHaveProperty("endpoint");
    expect(page.items[0]).not.toHaveProperty("credentialReference");
    expect(page.items[0]?.nextStep).toBe("CREATE_DEPLOYMENT");
  });

  it("供应商连接列表只读取配置状态与最近探测摘要", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({
        items: [{
          id: "610000000000000001",
          providerKind: "ARK",
          displayName: "火山方舟生产连接",
          region: "cn-beijing",
          authenticationType: "API_KEY",
          credentialConfigured: true,
          status: "VERIFIED",
          version: 4,
          lastProbeSucceeded: true,
          lastProbeCode: "PROBE_SUCCEEDED",
          lastProbedAt: "2026-08-10T01:00:00Z",
          createdAt: "2026-08-09T01:00:00Z",
        }],
        pageNumber: 1,
        pageSize: 20,
        totalElements: 1,
        totalPages: 1,
        hasPrevious: false,
        hasNext: false,
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });

    const page = await client.listProviderConnections();

    expect(request?.url).toBe("http://localhost/api/v1/model-providers?pageNumber=1&pageSize=20");
    expect(page.items[0]).not.toHaveProperty("endpoint");
    expect(page.items[0]).not.toHaveProperty("credentialReference");
    expect(page.items[0]?.lastProbeCode).toBe("PROBE_SUCCEEDED");
  });

  it("发起模型发现时只提交连接版本与受控超时，并丢弃候选明细", async () => {
    let request: Request | undefined;
    const client = authenticatedClient(async (input, init) => {
      request = new Request(input, init);
      return new Response(JSON.stringify({
        discoveryId: "710000000000000001",
        succeeded: true,
        models: [{ modelCode: "doubao-pro", modelType: "CHAT" }],
        resultCode: "DISCOVERY_SUCCEEDED",
        elapsed: "PT0.12S",
        discoveredAt: "2026-08-10T01:00:00Z",
        expiresAt: "2026-08-11T01:00:00Z",
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });

    const result = await client.discoverProviderModels("610000000000000001", 4, 10_000);

    expect(request?.url).toBe("http://localhost/api/v1/model-providers/610000000000000001/model-discovery");
    expect(request?.method).toBe("POST");
    expect(await request?.json()).toEqual({ expectedVersion: 4, timeoutMillis: 10_000 });
    expect(result).not.toHaveProperty("models");
    expect(result.succeeded).toBe(true);
  });

  it("模型部署供应链按需分页读取候选，并以受控版本完成创建、能力探测和发布", async () => {
    const requests: Request[] = [];
    const deployment = {
      id: "720000000000000001",
      providerConnectionId: "610000000000000001",
      providerConnectionRevision: 4,
      modelCode: "deterministic-chat",
      displayName: "客服对话模型",
      modelType: "CHAT",
      capabilities: ["CHAT"],
      contextWindow: 8192,
      outputLimit: 1024,
      embeddingDimension: null,
      status: "DRAFT",
      version: 0,
      createdAt: "2026-08-10T02:00:00Z",
    };
    const client = authenticatedClient(async (input, init) => {
      requests.push(new Request(input, init));
      const payload = requests.length === 1
        ? { items: [{ modelCode: "deterministic-chat", modelType: "CHAT", capabilities: ["CHAT"] }], pageNumber: 2, pageSize: 20, totalElements: 21, totalPages: 2, hasPrevious: true, hasNext: false }
        : requests.length === 3
          ? { modelDeployment: { ...deployment, status: "VERIFIED", version: 1 }, reachable: true, capabilities: ["CHAT"], elapsed: "PT0.1S", detailCode: "PROBE_SUCCEEDED", probedAt: "2026-08-10T02:01:00Z" }
          : requests.length === 4
            ? { ...deployment, status: "PUBLISHED", version: 2 }
            : requests.length === 5
              ? { ...deployment, status: "DISABLED", availability: "UNAVAILABLE", nextStep: "NONE", version: 3 }
            : deployment;
      return new Response(JSON.stringify(payload), {
        status: requests.length === 2 ? 201 : 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    const candidates = await client.listCurrentProviderModelCandidates("610000000000000001", 2, 20);
    const created = await client.createModelDeployment({
      providerConnectionId: "610000000000000001",
      modelCode: "deterministic-chat",
      displayName: "客服对话模型",
      modelType: "CHAT",
      capabilities: ["CHAT"],
      contextWindow: 8192,
      outputLimit: 1024,
      embeddingDimension: null,
    });
    const probed = await client.probeModelDeployment(created.id, created.version, 10_000);
    const published = await client.publishModelDeployment(probed.modelDeployment.id, probed.modelDeployment.version);
    const disabled = await client.disableModelDeployment(published.id, published.version);

    expect(candidates.items[0]?.modelCode).toBe("deterministic-chat");
    expect(requests[0]?.url).toBe("http://localhost/api/v1/model-providers/610000000000000001/current-model-candidates?pageNumber=2&pageSize=20");
    expect(requests[1]?.headers.get("Idempotency-Key")).not.toBeNull();
    expect(await requests[1]?.json()).toEqual({
      providerConnectionId: "610000000000000001", modelCode: "deterministic-chat", displayName: "客服对话模型",
      modelType: "CHAT", capabilities: ["CHAT"], contextWindow: 8192, outputLimit: 1024, embeddingDimension: null,
    });
    expect(await requests[2]?.json()).toEqual({ expectedVersion: 0, timeoutMillis: 10_000 });
    expect(await requests[3]?.json()).toEqual({ expectedVersion: 1 });
    expect(await requests[4]?.json()).toEqual({ expectedVersion: 2 });
    expect(disabled.status).toBe("DISABLED");
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
    displayRoles: ["TENANT_USER"],
    capabilityCodes: ["knowledge:read"],
    maximumSessionAgeSeconds: 3600,
  };
  return {
    login: async () => capability,
    restore: async () => capability,
    logout: async () => undefined,
    request: async (path, init) => fetcher(`http://localhost/api/v1${path}`, init),
  };
}
