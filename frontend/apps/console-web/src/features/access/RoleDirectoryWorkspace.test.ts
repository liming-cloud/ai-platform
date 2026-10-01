import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { RoleDirectoryWorkspace, platformRoleCatalog, tenantRoleCatalog } from "./RoleDirectoryWorkspace";

describe("固定角色目录", () => {
  it("平台目录只解释唯一平台模板，租户目录保持两个租户内角色", () => {
    expect(platformRoleCatalog.map((role) => role.code)).toEqual([
      "PLATFORM_ADMINISTRATOR",
    ]);
    expect(tenantRoleCatalog.map((role) => role.code)).toEqual([
      "TENANT_ADMINISTRATOR",
      "TENANT_USER",
    ]);
  });

  it("平台页直接说明固定职责并只提供管理账户导航", () => {
    const markup = renderToStaticMarkup(createElement(RoleDirectoryWorkspace, {
      scope: "PLATFORM",
      onManageMembers: () => undefined,
    }));

    expect(markup).toMatch(/<h1\b[^>]*>平台固定角色<\/h1>/);
    expect(markup).toContain("租户生命周期与模型供应链");
    expect(markup).toContain("仅脱敏聚合");
    expect(markup.match(/禁止访问/g)).toHaveLength(2);
    expect(markup).toContain(">管理账户</button>");
    expect(markup).not.toContain("查看详情");
    expect(markup).not.toContain("租户管理员");
    expect(markup).not.toContain("普通用户");
    expect(markup).not.toContain("PLATFORM_ADMINISTRATOR");
  });

  it("角色详情只使用面向用户的能力名称，不暴露权限码", () => {
    const roles = [...platformRoleCatalog, ...tenantRoleCatalog];

    expect(roles.every((role) => role.capabilities.length > 0)).toBe(true);
    expect(roles.flatMap((role) => role.capabilities).join(" ")).not.toContain(":");
  });
});
