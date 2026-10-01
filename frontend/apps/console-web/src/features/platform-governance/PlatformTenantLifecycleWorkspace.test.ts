/// <reference types="node" />
import { readFileSync } from "node:fs";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { PlatformTenantLifecycleWorkspace, TenantDirectoryTable } from "./PlatformTenantLifecycleWorkspace";
import type { ControlPlaneClient, TenantLifecycleSummary } from "../../api/control-plane-client";

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

  it("真实目录表格区分零资格和已有资格，并完整转义名称与编码", () => {
    const tenant: TenantLifecycleSummary = { tenantId: "2104457357567197185", tenantCode: "synthetic-long-tenant-code",
      displayName: "合成组织 <script>不可执行</script>", administratorCount: 0, status: "PROVISIONING", version: 1,
      createdAt: "2026-10-01T00:00:00Z", lastChangedAt: "2026-10-01T00:00:00Z" };
    const html = renderToStaticMarkup(createElement(TenantDirectoryTable, {
      tenants: [tenant, { ...tenant, tenantId: "2104457357567197186", status: "SUSPENDED", administratorCount: 1 }],
      empty: "没有符合条件的租户。", onRouteChange: () => undefined,
    }));
    expect(html).toContain('data-label="租户管理员">未分配');
    expect(html).toContain('data-label="租户管理员">已分配');
    expect(html).toContain("待启用"); expect(html).toContain("已暂停");
    expect(html).toContain("&lt;script&gt;不可执行&lt;/script&gt;");
    expect(html).not.toContain("<script>");
    expect(html.match(/查看详情/g)).toHaveLength(2);
  });
});
