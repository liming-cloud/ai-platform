import { describe, expect, it } from "vitest";
import {
  defaultConsoleRoute,
  navigationEntryRoute,
  formatConsoleRoute,
  knowledgeDataTaskRoute,
  parseConsoleRoute,
  resolveKnowledgeSpaceIdOnPage,
  resolveAuthorizedConsoleRoute,
} from "./console-route";

describe("统一控制台路由", () => {
  it("访问策略目录和冻结版本都有可恢复地址", () => {
    expect(parseConsoleRoute("/tenant/access-policies", "?page=2&size=50&q=权限&status=DRAFT")).toEqual({
      navigationId: "access-policies", task: "policies", page: 2, pageSize: 50, query: "权限", status: "DRAFT",
    });
    expect(parseConsoleRoute("/tenant/access-policies/3382738893456539650/versions/3382738893456539651", "")).toEqual({
      navigationId: "access-policies", task: "policy-detail", policyId: "3382738893456539650", versionId: "3382738893456539651",
    });
    expect(parseConsoleRoute("/tenant/access-policies/0", "")).toBeUndefined();
  });
  it("成员助手独立页可直达且错误成员引用失败关闭", () => {
    expect(parseConsoleRoute("/tenant/members/3382738893456539650/assistants", "")).toEqual({ navigationId: "access", task: "member-assistants", membershipId: "3382738893456539650" });
    expect(parseConsoleRoute("/tenant/members/0/assistants", "")).toBeUndefined();
    expect(parseConsoleRoute("/tenant/members/9223372036854775808/assistants", "")).toBeUndefined();
  });
  it("成员对话功能有独立直接地址并拒绝无效成员标识", () => {
    const route = { navigationId: "access", task: "member-features", membershipId: "3382738893456539650" } as const;
    expect(parseConsoleRoute("/tenant/members/3382738893456539650/features", "")).toEqual(route);
    expect(formatConsoleRoute(route)).toBe("/tenant/members/3382738893456539650/features");
    expect(parseConsoleRoute("/tenant/members/0/features", "")).toBeUndefined();
    expect(parseConsoleRoute("/tenant/members/9223372036854775808/features", "")).toBeUndefined();
  });
  it("已交付原型导航进入对应任务，旧直达路由保持独立", () => {
    expect(navigationEntryRoute("models", true)).toEqual({ navigationId: "models", task: "providers", providerPage: 1, discoveryPage: 1 });
    expect(navigationEntryRoute("governance", true)).toEqual({ navigationId: "governance", task: "evaluation" });
    expect(navigationEntryRoute("query", true)).toEqual(defaultConsoleRoute("query"));
    expect(navigationEntryRoute("governance", false)).toEqual(defaultConsoleRoute("governance"));
  });
  it("成员目录 URL 不向服务端发送超过平台偏移上限的分页", () => {
    for (const [size, lastPage] of [[20, 5001], [100, 1001]] as const) {
      expect(parseConsoleRoute("/access/members", `?page=${lastPage}&size=${size}`)).toEqual({ navigationId: "access", task: "members", page: lastPage, pageSize: size });
      expect(parseConsoleRoute("/access/members", `?page=${lastPage + 1}&size=${size}`)).toEqual({ navigationId: "access", task: "members", page: 1, pageSize: size });
    }
    expect(parseConsoleRoute("/access/members", "?page=99999")).toEqual({ navigationId: "access", task: "members", page: 1 });
  });
  it("成员开户与账号使用独立直接地址，目录筛选和标准页大小可恢复", () => {
    expect(parseConsoleRoute("/tenant/members/new", "?step=3")).toEqual({ navigationId: "access", task: "member-create", step: 3 });
    expect(formatConsoleRoute({ navigationId: "access", task: "member-create", step: 2 })).toBe("/tenant/members/new?step=2");
    expect(parseConsoleRoute("/tenant/members/3382738893456539650", "")).toEqual({ navigationId: "access", task: "member-account", membershipId: "3382738893456539650" });
    expect(parseConsoleRoute("/tenant/members/9223372036854775808", "")).toBeUndefined();
    expect(parseConsoleRoute("/tenant/members/0", "")).toBeUndefined();
    const route = { navigationId: "access", task: "members", page: 3, pageSize: 50, query: "普通成员", status: "DISABLED" } as const;
    const url = new URL(formatConsoleRoute(route), "https://example.invalid");
    expect(parseConsoleRoute(url.pathname, url.search)).toEqual(route);
  });
  it("租户概览由稳定标识恢复，未知子路径不猜测目标", () => {
    const route = { navigationId: "platform", task: "detail", tenantId: "610000000000000001" } as const;
    expect(formatConsoleRoute(route)).toBe("/platform/tenants/610000000000000001");
    expect(parseConsoleRoute("/platform/tenants/610000000000000001", "")).toEqual(route);
    expect(parseConsoleRoute("/platform/tenants/not-a-tenant", "")).toBeUndefined();
  });
  it("资源配额设置由租户标识直接恢复", () => {
    const route = { navigationId: "platform", task: "quotas", tenantId: "610000000000000001" } as const;
    expect(formatConsoleRoute(route)).toBe("/platform/tenants/610000000000000001/quotas");
    expect(parseConsoleRoute("/platform/tenants/610000000000000001/quotas", "")).toEqual(route);
    expect(parseConsoleRoute("/platform/tenants/nope/quotas", "")).toBeUndefined();
  });
  it("租户可用功能设置使用独立且可恢复的路由", () => {
    const route = { navigationId: "platform", task: "features", tenantId: "610000000000000001" } as const;
    expect(formatConsoleRoute(route)).toBe("/platform/tenants/610000000000000001/features");
    expect(parseConsoleRoute("/platform/tenants/610000000000000001/features", "")).toEqual(route);
    expect(parseConsoleRoute("/platform/tenants/nope/features", "")).toBeUndefined();
  });
  it("租户登录安全设置使用独立且可恢复的路由", () => {
    const route = { navigationId: "platform", task: "security", tenantId: "610000000000000001" } as const;
    expect(formatConsoleRoute(route)).toBe("/platform/tenants/610000000000000001/security");
    expect(parseConsoleRoute("/platform/tenants/610000000000000001/security", "")).toEqual(route);
  });
  it("两级运营总览使用稳定入口并恢复平台租户摘要页码", () => {
    expect(defaultConsoleRoute("operations")).toEqual({
      navigationId: "operations",
      task: "overview",
      range: "DAY_7",
    });
    expect(parseConsoleRoute("/operations", "?range=DAY_30")).toEqual({
      navigationId: "operations",
      task: "overview",
      range: "DAY_30",
    });
    expect(formatConsoleRoute({ navigationId: "operations", task: "overview", range: "DAY_1" }))
      .toBe("/operations?range=DAY_1");
    expect(parseConsoleRoute("/operations", "?range=ARBITRARY")).toEqual({
      navigationId: "operations",
      task: "overview",
      range: "DAY_7",
    });

    expect(defaultConsoleRoute("platform-operations")).toEqual({
      navigationId: "platform-operations",
      task: "overview",
      page: 1,
      range: "DAY_7",
    });
    expect(parseConsoleRoute("/platform/operations", "?range=DAY_30&page=3")).toEqual({
      navigationId: "platform-operations",
      task: "overview",
      page: 3,
      range: "DAY_30",
    });
    expect(formatConsoleRoute({
      navigationId: "platform-operations",
      task: "overview",
      page: 3,
      range: "DAY_30",
    })).toBe("/platform/operations?range=DAY_30&page=3");
  });

  it("知识库一级入口使用独立可恢复目录路由", () => {
    expect(defaultConsoleRoute("data")).toEqual({
      navigationId: "data",
      task: "knowledge-bases",
      page: 1,
    });
    expect(parseConsoleRoute("/knowledge-bases", "?page=2")).toEqual({
      navigationId: "data",
      task: "knowledge-bases",
      page: 2,
    });
    expect(formatConsoleRoute({
      navigationId: "data",
      task: "knowledge-bases",
      page: 2,
    })).toBe("/knowledge-bases?page=2");
  });

  it("从知识库目录第 2 页进入时保留知识库和服务端页码", () => {
    expect(knowledgeDataTaskRoute(
      { navigationId: "data", task: "knowledge-bases", page: 2 },
      "governed-file",
      "410000000000000021",
    )).toEqual({
      navigationId: "data",
      task: "governed-file",
      knowledgeSpacePage: 2,
      knowledgeSpaceId: "410000000000000021",
      sourcePage: 1,
      embeddingModelConfigurationPage: 1,
    });
  });

  it("跨知识库子任务保留显式选择，返回目录也保留所在页", () => {
    const route = {
      navigationId: "data" as const,
      task: "governed-file" as const,
      knowledgeSpacePage: 3,
      knowledgeSpaceId: "410000000000000041",
      sourcePage: 2,
      embeddingModelConfigurationPage: 4,
      embeddingModelConfigurationId: "510000000000000001",
    };

    expect(knowledgeDataTaskRoute(route, "object-storage")).toEqual({
      navigationId: "data",
      task: "object-storage",
      knowledgeSpacePage: 3,
      knowledgeSpaceId: "410000000000000041",
      embeddingModelConfigurationPage: 4,
      embeddingModelConfigurationId: "510000000000000001",
    });
    expect(knowledgeDataTaskRoute(route, "knowledge-bases")).toEqual({
      navigationId: "data",
      task: "knowledge-bases",
      page: 3,
    });
  });

  it("显式知识库未命中当前页时失败关闭，不回退到第一项", () => {
    const candidates = [
      { id: "410000000000000001" },
      { id: "410000000000000002" },
    ];

    expect(resolveKnowledgeSpaceIdOnPage(undefined, candidates)).toBeUndefined();
    expect(resolveKnowledgeSpaceIdOnPage("410000000000000099", candidates)).toBeUndefined();
    expect(resolveKnowledgeSpaceIdOnPage("410000000000000002", candidates))
      .toBe("410000000000000002");
  });

  it("平台工作区不得用其他入口替代知识库深链接", () => {
    const route = parseConsoleRoute("/knowledge-bases", "");

    expect(resolveAuthorizedConsoleRoute(route, ["platform", "platform-accounts"]))
      .toEqual({
        kind: "denied",
        route: { navigationId: "data", task: "knowledge-bases", page: 1 },
      });
  });

  it("旧平台根地址兼容进入真实的运营总览", () => {
    expect(parseConsoleRoute("/platform", "")).toEqual({
      navigationId: "platform-operations",
      task: "overview",
      page: 1,
      range: "DAY_7",
    });
  });

  it("一级模型导航进入可恢复的配置工作区，而不是内存标签页", () => {
    expect(defaultConsoleRoute("models")).toEqual({
      navigationId: "models",
      task: "configuration",
      configurationPage: 1,
      catalogPage: 1,
    });
  });

  it("模型配置和受控目录分别从 URL 恢复服务端页码", () => {
    expect(parseConsoleRoute(
      "/models/configurations",
      "?configurationPage=3&catalogPage=2",
    )).toEqual({
      navigationId: "models",
      task: "configuration",
      configurationPage: 3,
      catalogPage: 2,
    });
    expect(formatConsoleRoute({
      navigationId: "models",
      task: "configuration",
      configurationPage: 3,
      catalogPage: 2,
    })).toBe("/models/configurations?configurationPage=3&catalogPage=2");
  });

  it("成员与权限目录把服务端页码稳定恢复到 URL", () => {
    expect(parseConsoleRoute("/access/members", "?page=3")).toEqual({
      navigationId: "access",
      task: "members",
      page: 3,
    });
    expect(formatConsoleRoute({
      navigationId: "access",
      task: "members",
      page: 3,
    })).toBe("/access/members?page=3");
  });

  it("租户与平台角色目录都有可恢复的独立详情入口", () => {
    expect(parseConsoleRoute("/access/roles", "")).toEqual({
      navigationId: "access",
      task: "roles",
    });
    expect(formatConsoleRoute({ navigationId: "access", task: "roles" }))
      .toBe("/access/roles");
    expect(parseConsoleRoute("/platform/roles", "")).toEqual({
      navigationId: "platform-accounts",
      task: "roles",
    });
    expect(formatConsoleRoute({ navigationId: "platform-accounts", task: "roles" }))
      .toBe("/platform/roles");
  });

  it("从深链接恢复模型供应链发现记录的服务端页码", () => {
    expect(parseConsoleRoute(
      "/models/providers",
      "?providerPage=3&connectionId=610000000000000001&discoveryPage=2",
    )).toEqual({
      navigationId: "models",
      task: "providers",
      providerPage: 3,
      connectionId: "610000000000000001",
      discoveryPage: 2,
    });
  });

  it("把模型供应链页码稳定写回 URL", () => {
    expect(formatConsoleRoute({
      navigationId: "models",
      task: "providers",
      providerPage: 3,
      connectionId: "610000000000000001",
      discoveryPage: 2,
    })).toBe("/models/providers?providerPage=3&connectionId=610000000000000001&discoveryPage=2");
  });

  it("模型能力探测使用独立的可恢复部署任务路由", () => {
    expect(parseConsoleRoute(
      "/models/deployments",
      "?connectionId=610000000000000001&candidatePage=2&deploymentPage=3",
    )).toEqual({
      navigationId: "models",
      task: "deployments",
      connectionId: "610000000000000001",
      candidatePage: 2,
      deploymentPage: 3,
    });
    expect(formatConsoleRoute({
      navigationId: "models",
      task: "deployments",
      connectionId: "610000000000000001",
      candidatePage: 2,
      deploymentPage: 3,
    })).toBe("/models/deployments?connectionId=610000000000000001&candidatePage=2&deploymentPage=3");
  });

  it("查询审计目录从深链接恢复页码，而不是累加到浏览器内存", () => {
    expect(parseConsoleRoute("/governance/query", "?auditPage=4")).toEqual({
      navigationId: "governance",
      task: "query",
      auditPage: 4,
    });
    expect(formatConsoleRoute({
      navigationId: "governance",
      task: "query",
      auditPage: 4,
    })).toBe("/governance/query?auditPage=4");
  });

  it("问答助手目录把服务端页码稳定恢复到统一控制台 URL", () => {
    expect(defaultConsoleRoute("query")).toEqual({
      navigationId: "query",
      task: "ask",
      assistantPage: 1,
    });
    expect(parseConsoleRoute("/ask", "?assistantPage=3")).toEqual({
      navigationId: "query",
      task: "ask",
      assistantPage: 3,
    });
    expect(formatConsoleRoute({
      navigationId: "query",
      task: "ask",
      assistantPage: 3,
    })).toBe("/ask?assistantPage=3");
  });

  it("不以其他可见入口替代无权访问的平台治理深链接", () => {
    const route = parseConsoleRoute("/platform/tenants", "");

    expect(resolveAuthorizedConsoleRoute(route, ["data", "models"])).toEqual({
      kind: "denied",
      route: {
        navigationId: "platform",
        task: "tenants",
        page: 1,
        query: "", status: "ALL", pageSize: 20,
      },
    });
  });

  it("平台租户目录把服务器页码保留在深链接中", () => {
    expect(defaultConsoleRoute("platform")).toEqual({
      navigationId: "platform",
      task: "tenants",
      page: 1,
      query: "", status: "ALL", pageSize: 20,
    });
    expect(parseConsoleRoute("/platform/tenants", "?page=2")).toEqual({
      navigationId: "platform",
      task: "tenants",
      page: 2,
      query: "", status: "ALL", pageSize: 20,
    });
    expect(formatConsoleRoute({ navigationId: "platform", task: "tenants", page: 2 }))
      .toBe("/platform/tenants?q=&status=ALL&page=2&pageSize=20");
  });

  it("租户目录在刷新与历史中恢复查询、状态和完整分页", () => {
    const route = { navigationId: "platform" as const, task: "tenants" as const,
      query: "云杉 %_", status: "SUSPENDED" as const, page: 2, pageSize: 50 as const };
    expect(parseConsoleRoute("/platform/tenants", "?q=%E4%BA%91%E6%9D%89+%25_&status=SUSPENDED&page=2&pageSize=50"))
      .toEqual(route);
    expect(formatConsoleRoute(route))
      .toBe("/platform/tenants?q=%E4%BA%91%E6%9D%89+%25_&status=SUSPENDED&page=2&pageSize=50");
  });

  it("租户目录把未知状态和页大小恢复为明确默认值", () => {
    expect(parseConsoleRoute("/platform/tenants", "?q=%20demo%20&status=DISABLED&page=-1&pageSize=7"))
      .toEqual({ navigationId: "platform", task: "tenants", query: "demo", status: "ALL", page: 1, pageSize: 20 });
  });

  it("建立租户使用独立三步路由且拒绝无效步骤", () => {
    expect(parseConsoleRoute("/platform/tenants/new", "?step=2")).toEqual({
      navigationId: "platform",
      task: "create",
      step: 2,
    });
    expect(parseConsoleRoute("/platform/tenants/new", "?step=8")).toEqual({
      navigationId: "platform",
      task: "create",
      step: 1,
    });
    expect(formatConsoleRoute({ navigationId: "platform", task: "create", step: 3 }))
      .toBe("/platform/tenants/new?step=3");
  });

  it("租户管理员独立页恢复搜索、状态与服务端页码", () => {
    expect(parseConsoleRoute("/platform/tenants/123/admins", "?q=alice&status=DISABLED&page=2"))
      .toEqual({ navigationId: "platform", task: "admins", tenantId: "123", query: "alice", status: "DISABLED", page: 2, pageSize: 20 });
    expect(formatConsoleRoute({ navigationId: "platform", task: "admins", tenantId: "123",
      query: "alice", status: "DISABLED", page: 2, pageSize: 20 }))
      .toBe("/platform/tenants/123/admins?q=alice&status=DISABLED&page=2&pageSize=20");
    expect(parseConsoleRoute("/platform/tenants/123/admins", "?status=BAD&page=0"))
      .toEqual({ navigationId: "platform", task: "admins", tenantId: "123", query: "", status: "ALL", page: 1, pageSize: 20 });
    for (const pageSize of [10, 20, 50, 100]) {
      expect(parseConsoleRoute("/platform/tenants/123/admins", `?pageSize=${pageSize}`))
        .toMatchObject({ task: "admins", pageSize });
    }
    expect(parseConsoleRoute("/platform/tenants/123/admins", "?pageSize=101"))
      .toMatchObject({ task: "admins", pageSize: 20 });
  });

  it("平台账户目录以独立能力入口和 URL 恢复服务端页码", () => {
    expect(defaultConsoleRoute("platform-accounts")).toEqual({
      navigationId: "platform-accounts",
      task: "accounts",
      page: 1,
      pageSize: 20,
      query: "",
      status: "ALL",
    });
    expect(parseConsoleRoute("/platform/accounts", "?q=admin&status=DISABLED&page=3&pageSize=50")).toEqual({
      navigationId: "platform-accounts",
      task: "accounts",
      page: 3,
      pageSize: 50,
      query: "admin",
      status: "DISABLED",
    });
    expect(formatConsoleRoute({ navigationId: "platform-accounts", task: "accounts", page: 3,
      pageSize: 50, query: "admin", status: "DISABLED" }))
      .toBe("/platform/accounts?q=admin&status=DISABLED&page=3&pageSize=50");
    expect(parseConsoleRoute("/platform/accounts", "?pageSize=101&status=unknown"))
      .toMatchObject({ pageSize: 20, status: "ALL" });
  });

  it("平台模型供应链以独立能力入口和 URL 恢复服务端页码", () => {
    expect(defaultConsoleRoute("platform-model-supply")).toEqual({
      navigationId: "platform-model-supply",
      task: "connections",
      page: 1,
    });
    expect(parseConsoleRoute("/platform/model-supply", "?page=3")).toEqual({
      navigationId: "platform-model-supply",
      task: "connections",
      page: 3,
    });
    expect(formatConsoleRoute({ navigationId: "platform-model-supply", task: "connections", page: 3 }))
      .toBe("/platform/model-supply?page=3");
  });

  it("平台部署任务从 URL 恢复连接修订与两组服务端页码", () => {
    expect(parseConsoleRoute(
      "/platform/model-supply/deployments",
      "?connectionId=610000000000000001&connectionVersion=2&candidatePage=3&deploymentPage=4",
    )).toEqual({
      navigationId: "platform-model-supply",
      task: "deployments",
      connectionId: "610000000000000001",
      connectionVersion: 2,
      candidatePage: 3,
      deploymentPage: 4,
    });
    expect(formatConsoleRoute({
      navigationId: "platform-model-supply",
      task: "deployments",
      connectionId: "610000000000000001",
      connectionVersion: 2,
      candidatePage: 3,
      deploymentPage: 4,
    })).toBe("/platform/model-supply/deployments?connectionId=610000000000000001&connectionVersion=2&candidatePage=3&deploymentPage=4");
  });

  it("平台连接两页恢复已接受的页大小并限制最大偏移", () => {
    const connectionId = "610000000000000001";
    for (const pageSize of [10, 20, 50, 100] as const) {
      expect(parseConsoleRoute("/platform/model-supply", `?page=3&size=${pageSize}`))
        .toMatchObject({ page: 3, pageSize });
      expect(parseConsoleRoute(`/platform/model-supply/connections/${connectionId}`, `?candidatePage=3&size=${pageSize}`))
        .toMatchObject({ connectionId, candidatePage: 3, pageSize });
      expect(formatConsoleRoute({ navigationId: "platform-model-supply", task: "connections", page: 3, pageSize }))
        .toBe(`/platform/model-supply?page=3&size=${pageSize}`);
      expect(formatConsoleRoute({ navigationId: "platform-model-supply", task: "connection-detail", connectionId, candidatePage: 3, pageSize }))
        .toBe(`/platform/model-supply/connections/${connectionId}?candidatePage=3&size=${pageSize}`);
    }
    for (const value of ["0", "-1", "1.5", "999999999999999999999999999999", "10002"]) {
      expect(parseConsoleRoute("/platform/model-supply", `?page=${value}&size=10`)).toMatchObject({ page: 1, pageSize: 10 });
      expect(parseConsoleRoute(`/platform/model-supply/connections/${connectionId}`, `?candidatePage=${value}&size=10`))
        .toMatchObject({ candidatePage: 1, pageSize: 10 });
    }
    expect(parseConsoleRoute("/platform/model-supply", "?page=10001&size=10")).toMatchObject({ page: 10001, pageSize: 10 });
    expect(parseConsoleRoute("/platform/model-supply", "?page=2&size=101")).toMatchObject({ page: 2, pageSize: 20 });
  });

  it("模型连接搜索与详情返回保持两个独立目录位置", () => {
    const directory = { page: 3, pageSize: 10 as const, query: "中文 %_\\&" };
    const url = formatConsoleRoute({ navigationId: "platform-model-supply", task: "connection-detail",
      connectionId: "610000000000000001", candidatePage: 2, pageSize: 50, query: "对话模型", directory });
    const parsed = new URL(url, "http://localhost");
    expect(parseConsoleRoute(parsed.pathname, parsed.search)).toMatchObject({
      candidatePage: 2, pageSize: 50, query: "对话模型", directory });
    expect(parseConsoleRoute("/platform/model-supply", "?page=3&size=10&q=%E4%B8%AD%E6%96%87%20%25_"))
      .toMatchObject({ page: 3, pageSize: 10, query: "中文 %_" });
  });

  it("平台连接详情深链接恢复精确连接和候选页，不回退其他连接", () => {
    expect(formatConsoleRoute({ navigationId: "platform-model-supply", task: "connection-detail",
      connectionId: "610000000000000001", candidatePage: 2 }))
      .toBe("/platform/model-supply/connections/610000000000000001?candidatePage=2");
    expect(parseConsoleRoute("/platform/model-supply/connections/610000000000000001", "?candidatePage=2"))
      .toEqual({ navigationId: "platform-model-supply", task: "connection-detail",
        connectionId: "610000000000000001", candidatePage: 2 });
    expect(parseConsoleRoute("/platform/model-supply/connections/0", "")).toBeUndefined();
    expect(parseConsoleRoute("/platform/model-supply/connections/9223372036854775808", "")).toBeUndefined();
  });

  it("受治理文件任务从 URL 恢复资源选择器的服务端页码", () => {
    expect(parseConsoleRoute(
      "/data/files",
      "?knowledgeSpacePage=2&knowledgeSpaceId=410000000000000001&sourcePage=3&embeddingModelConfigurationPage=4&embeddingModelConfigurationId=510000000000000001",
    )).toEqual({
      navigationId: "data",
      task: "governed-file",
      knowledgeSpacePage: 2,
      knowledgeSpaceId: "410000000000000001",
      sourcePage: 3,
      embeddingModelConfigurationPage: 4,
      embeddingModelConfigurationId: "510000000000000001",
    });
    expect(formatConsoleRoute({
      navigationId: "data",
      task: "governed-file",
      knowledgeSpacePage: 2,
      knowledgeSpaceId: "410000000000000001",
      sourcePage: 3,
      embeddingModelConfigurationPage: 4,
      embeddingModelConfigurationId: "510000000000000001",
    })).toBe("/data/files?knowledgeSpacePage=2&knowledgeSpaceId=410000000000000001&sourcePage=3&embeddingModelConfigurationPage=4&embeddingModelConfigurationId=510000000000000001");
  });

  it("结构化数据集任务从 URL 恢复知识空间选择器页码", () => {
    expect(parseConsoleRoute(
      "/data/datasets",
      "?knowledgeSpacePage=3&knowledgeSpaceId=410000000000000002&embeddingModelConfigurationPage=2&embeddingModelConfigurationId=510000000000000002",
    )).toEqual({
      navigationId: "data",
      task: "structured-dataset",
      knowledgeSpacePage: 3,
      knowledgeSpaceId: "410000000000000002",
      embeddingModelConfigurationPage: 2,
      embeddingModelConfigurationId: "510000000000000002",
    });
    expect(formatConsoleRoute({
      navigationId: "data",
      task: "structured-dataset",
      knowledgeSpacePage: 3,
      knowledgeSpaceId: "410000000000000002",
      embeddingModelConfigurationPage: 2,
      embeddingModelConfigurationId: "510000000000000002",
    })).toBe("/data/datasets?knowledgeSpacePage=3&knowledgeSpaceId=410000000000000002&embeddingModelConfigurationPage=2&embeddingModelConfigurationId=510000000000000002");
  });

  it("对象存储任务从 URL 恢复知识空间选择器页码", () => {
    expect(parseConsoleRoute(
      "/data/object-storage",
      "?knowledgeSpacePage=4&knowledgeSpaceId=410000000000000003&embeddingModelConfigurationPage=5&embeddingModelConfigurationId=510000000000000003",
    )).toEqual({
      navigationId: "data",
      task: "object-storage",
      knowledgeSpacePage: 4,
      knowledgeSpaceId: "410000000000000003",
      embeddingModelConfigurationPage: 5,
      embeddingModelConfigurationId: "510000000000000003",
    });
    expect(formatConsoleRoute({
      navigationId: "data",
      task: "object-storage",
      knowledgeSpacePage: 4,
      knowledgeSpaceId: "410000000000000003",
      embeddingModelConfigurationPage: 5,
      embeddingModelConfigurationId: "510000000000000003",
    })).toBe("/data/object-storage?knowledgeSpacePage=4&knowledgeSpaceId=410000000000000003&embeddingModelConfigurationPage=5&embeddingModelConfigurationId=510000000000000003");
  });

  it("首份发布任务从深链接恢复知识空间和嵌入模型的服务端页码", () => {
    expect(parseConsoleRoute(
      "/data/publication",
      "?knowledgeSpacePage=2&knowledgeSpaceId=410000000000000004&embeddingModelConfigurationPage=3&embeddingModelConfigurationId=510000000000000004",
    )).toEqual({
      navigationId: "data",
      task: "first-publication",
      knowledgeSpacePage: 2,
      knowledgeSpaceId: "410000000000000004",
      embeddingModelConfigurationPage: 3,
      embeddingModelConfigurationId: "510000000000000004",
    });
    expect(formatConsoleRoute({
      navigationId: "data",
      task: "first-publication",
      knowledgeSpacePage: 2,
      knowledgeSpaceId: "410000000000000004",
      embeddingModelConfigurationPage: 3,
      embeddingModelConfigurationId: "510000000000000004",
    })).toBe("/data/publication?knowledgeSpacePage=2&knowledgeSpaceId=410000000000000004&embeddingModelConfigurationPage=3&embeddingModelConfigurationId=510000000000000004");
  });
});
