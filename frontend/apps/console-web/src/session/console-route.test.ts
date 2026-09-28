import { describe, expect, it } from "vitest";
import {
  defaultConsoleRoute,
  formatConsoleRoute,
  knowledgeDataTaskRoute,
  parseConsoleRoute,
  resolveKnowledgeSpaceIdOnPage,
  resolveAuthorizedConsoleRoute,
} from "./console-route";

describe("统一控制台路由", () => {
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
      },
    });
  });

  it("平台租户目录把服务器页码保留在深链接中", () => {
    expect(defaultConsoleRoute("platform")).toEqual({
      navigationId: "platform",
      task: "tenants",
      page: 1,
    });
    expect(parseConsoleRoute("/platform/tenants", "?page=2")).toEqual({
      navigationId: "platform",
      task: "tenants",
      page: 2,
    });
    expect(formatConsoleRoute({ navigationId: "platform", task: "tenants", page: 2 }))
      .toBe("/platform/tenants?page=2");
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
    });
    expect(parseConsoleRoute("/platform/accounts", "?page=3")).toEqual({
      navigationId: "platform-accounts",
      task: "accounts",
      page: 3,
    });
    expect(formatConsoleRoute({ navigationId: "platform-accounts", task: "accounts", page: 3 }))
      .toBe("/platform/accounts?page=3");
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
