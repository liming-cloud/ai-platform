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
    });
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

  it("不以其他可见入口替代无权访问的平台治理深链接", () => {
    const route = parseConsoleRoute("/platform/tenants", "");

    expect(resolveAuthorizedConsoleRoute(route, ["data", "models"])).toEqual({
      kind: "denied",
      route: {
        navigationId: "platform",
        task: "tenants",
      },
    });
  });
});
