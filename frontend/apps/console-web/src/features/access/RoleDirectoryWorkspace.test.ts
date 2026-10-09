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

  it("租户页只解释原型的两个固定模板，不提供角色分配或修改", () => {
    const markup = renderToStaticMarkup(createElement(RoleDirectoryWorkspace, {
      scope: "TENANT",
      onManageMembers: () => undefined,
    }));

    expect(markup).toMatch(/<h1\b[^>]*>租户固定角色<\/h1>/);
    expect(markup).toContain("租户访问与知识");
    expect(markup).toContain("2 个固定模板");
    expect(markup).toContain("管理本组织知识、模型、助手与普通成员");
    expect(markup).toContain("仅使用已授权助手、本人历史与引用");
    expect(markup).toContain("不属于当前产品契约");
    expect(markup).toContain("服务端能力投影");
    expect(markup).toContain("管理员与普通成员职责固定；配置成员功能不会改变角色。");
    expect(markup).toContain(">管理成员</button>");
    expect(markup).not.toContain("角色分配");
    expect(markup).not.toContain("角色绑定管理");
    expect(markup).not.toContain("查看详情");
    expect(markup).not.toContain("TENANT_ADMINISTRATOR");
    expect(markup).not.toContain("TENANT_USER");
  });
});
