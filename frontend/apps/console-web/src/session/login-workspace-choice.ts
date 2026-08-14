import type { BrowserLoginCredentials } from "@foundation/browser-session";

export type LoginWorkspace = "platform" | "tenant";

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
