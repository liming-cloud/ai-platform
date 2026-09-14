import { describe, expect, it } from "vitest";
import {
  buildBrowserLoginCredentials,
  isLoginReady,
  loginWorkspaceFromPath,
  isConsoleLoginPath,
  canEnterLoginWorkspace,
} from "./login-workspace-choice";

describe("登录工作区选择", () => {
  it("两个已发布入口独立，平台深链失效后仍显示平台登录", () => {
    expect(loginWorkspaceFromPath("/login")).toBe("platform");
    expect(loginWorkspaceFromPath("/tenant/login")).toBe("tenant");
    expect(loginWorkspaceFromPath("/platform/accounts")).toBe("platform");
    expect(loginWorkspaceFromPath("/platform/tenants")).toBe("platform");
    expect(loginWorkspaceFromPath("/knowledge")).toBe("tenant");
    expect(isConsoleLoginPath("/login")).toBe(true);
    expect(isConsoleLoginPath("/tenant/login")).toBe(true);
    expect(isConsoleLoginPath("/login/forged")).toBe(false);
  });

  it("入口只接受匹配的服务端角色和工作区，普通成员不能进入管理员入口", () => {
    const platform = { displayRoles: ["PLATFORM_ADMINISTRATOR"], workspaceCategories: ["PLATFORM_TENANT_GOVERNANCE"] };
    const tenant = { displayRoles: ["TENANT_ADMINISTRATOR"], workspaceCategories: ["TENANT"] };
    expect(canEnterLoginWorkspace("platform", platform)).toBe(true);
    expect(canEnterLoginWorkspace("tenant", tenant)).toBe(true);
    expect(canEnterLoginWorkspace("tenant", platform)).toBe(false);
    expect(canEnterLoginWorkspace("platform", tenant)).toBe(false);
    expect(canEnterLoginWorkspace("tenant", { ...tenant, displayRoles: ["TENANT_USER"] })).toBe(false);
    expect(canEnterLoginWorkspace("tenant", { ...tenant, workspaceCategories: [] })).toBe(false);
  });
  it("平台管理登录不提交租户编码", () => {
    expect(buildBrowserLoginCredentials({
      workspace: "platform",
      tenantCode: "demo-store",
      loginName: "platform.root",
      password: "not-a-real-password",
    })).toEqual({
      tenantCode: undefined,
      loginName: "platform.root",
      password: "not-a-real-password",
    });
  });

  it("租户工作区必须填写租户编码才能提交", () => {
    expect(isLoginReady({
      workspace: "tenant",
      tenantCode: "   ",
      loginName: "admin",
      password: "not-a-real-password",
    })).toBe(false);

    expect(isLoginReady({
      workspace: "tenant",
      tenantCode: " demo-store ",
      loginName: " admin ",
      password: "not-a-real-password",
    })).toBe(true);
  });
});
