import type { BrowserLoginCredentials, BrowserSessionCapability } from "@foundation/browser-session";

export type LoginWorkspace = "platform" | "tenant";

/** 已发布的两个管理登录地址；其他路径仍由原工作区路由校验。 */
export function isConsoleLoginPath(pathname: string): boolean {
  return pathname === "/login" || pathname === "/tenant/login";
}

/** 地址仅决定登录表单类型，不提供身份或权限；保留平台深链失效后的入口语义。 */
export function loginWorkspaceFromPath(pathname: string): LoginWorkspace {
  return pathname === "/login" || pathname === "/platform" || pathname.startsWith("/platform/")
    ? "platform" : "tenant";
}

/** 服务端恢复的角色和工作区必须同时匹配；此 UI 约束不替代接口授权。 */
export function canEnterLoginWorkspace(
  workspace: LoginWorkspace,
  capability: Pick<BrowserSessionCapability, "displayRoles" | "workspaceCategories">,
): boolean {
  return capability.displayRoles.includes(workspace === "platform" ? "PLATFORM_ADMINISTRATOR" : "TENANT_ADMINISTRATOR")
    && capability.workspaceCategories.includes(workspace === "platform" ? "PLATFORM_TENANT_GOVERNANCE" : "TENANT");
}

export interface LoginWorkspaceChoice {
  workspace: LoginWorkspace;
  tenantCode: string;
  loginName: string;
  password: string;
}

export function isLoginReady(choice: LoginWorkspaceChoice): boolean {
  return choice.loginName.trim().length > 0
    && choice.password.length > 0
    && (choice.workspace === "platform" || choice.tenantCode.trim().length > 0);
}

export function buildBrowserLoginCredentials(
  choice: LoginWorkspaceChoice,
): BrowserLoginCredentials {
  return {
    tenantCode: choice.workspace === "tenant" ? choice.tenantCode.trim() : undefined,
    loginName: choice.loginName.trim(),
    password: choice.password,
  };
}
