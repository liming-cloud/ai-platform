/**
 * 登录后的回跳只接受固定的同源查询工作区，避免将 `next` 变成开放重定向或权限旁路。
 */
export function resolvePostAuthenticationDestination(
  search: string,
  canUseTenantWorkspace: boolean,
): "/query/" | undefined {
  if (!canUseTenantWorkspace) {
    return undefined;
  }
  return new URLSearchParams(search).get("next") === "/query/" ? "/query/" : undefined;
}
