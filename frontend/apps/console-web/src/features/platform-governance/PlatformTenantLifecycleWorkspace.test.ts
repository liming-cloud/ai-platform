/// <reference types="node" />
import { readFileSync } from "node:fs";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { PlatformTenantLifecycleWorkspace } from "./PlatformTenantLifecycleWorkspace";
import type { ControlPlaneClient } from "../../api/control-plane-client";

const workspacePath = new URL("./PlatformTenantLifecycleWorkspace.tsx", import.meta.url).pathname;

describe("租户建立流程", () => {
  it("开户只通过原子向导提交，不提前创建人员账户", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).not.toContain("client.createPlatformAccount");
    expect(source).toContain('import { TenantProvisioningFlow }');
    expect(source).not.toContain("请先在平台账户页面创建并启用账户");
  });

  it("目录初始输出使用原型标题，未读取事实时不显示伪造计数或旧写操作", () => {
    const html = renderToStaticMarkup(createElement(PlatformTenantLifecycleWorkspace, {
      client: {} as ControlPlaneClient,
      route: { navigationId: "platform", task: "tenants", page: 1 }, onRouteChange: () => undefined,
    }));
    expect(html).toMatch(/<h1[^>]*>租户目录<\/h1>/);
    expect(html).toContain("建立新租户");
    expect(html).toContain("只读目录");
    expect(html).toContain("正在加载租户目录");
    expect(html).not.toContain("共 0 个租户");
    for (const label of ["管理租户管理员", "激活租户", "暂停租户", "分配管理员", "驻留区域"])
      expect(html).not.toContain(label);
  });
});
