import { describe, expect, it } from "vitest";
import {
  defaultConsoleRoute,
  formatConsoleRoute,
  parseConsoleRoute,
  resolveAuthorizedConsoleRoute,
} from "./console-route";

describe("统一控制台路由", () => {
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

  it("自定义角色目录使用独立可恢复路由和服务端页码", () => {
    expect(parseConsoleRoute("/access/roles", "?page=2")).toEqual({
      navigationId: "access",
      task: "roles",
      page: 2,
    });
    expect(formatConsoleRoute({ navigationId: "access", task: "roles", page: 2 }))
      .toBe("/access/roles?page=2");
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

  it("受治理文件任务从 URL 恢复资源选择器的服务端页码", () => {
    expect(parseConsoleRoute(
      "/data/files",
      "?knowledgeSpacePage=2&knowledgeSpaceId=410000000000000001&sourcePage=3",
    )).toEqual({
      navigationId: "data",
      task: "governed-file",
      knowledgeSpacePage: 2,
      knowledgeSpaceId: "410000000000000001",
      sourcePage: 3,
    });
    expect(formatConsoleRoute({
      navigationId: "data",
      task: "governed-file",
      knowledgeSpacePage: 2,
      knowledgeSpaceId: "410000000000000001",
      sourcePage: 3,
    })).toBe("/data/files?knowledgeSpacePage=2&knowledgeSpaceId=410000000000000001&sourcePage=3");
  });
});
