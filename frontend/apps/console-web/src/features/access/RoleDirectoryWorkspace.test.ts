import { describe, expect, it } from "vitest";
import { platformRoleCatalog, tenantRoleCatalog } from "./RoleDirectoryWorkspace";

describe("三角色目录", () => {
  it("只发布一个平台角色和两个租户角色", () => {
    expect(platformRoleCatalog.map((role) => role.code)).toEqual([
      "PLATFORM_ADMINISTRATOR",
    ]);
    expect(tenantRoleCatalog.map((role) => role.code)).toEqual([
      "TENANT_ADMINISTRATOR",
      "TENANT_USER",
    ]);
  });

  it("角色详情只使用面向用户的能力名称，不暴露权限码", () => {
    const roles = [...platformRoleCatalog, ...tenantRoleCatalog];

    expect(roles.every((role) => role.capabilities.length > 0)).toBe(true);
    expect(roles.flatMap((role) => role.capabilities).join(" ")).not.toContain(":");
  });
});
