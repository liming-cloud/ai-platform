/**
 * 登录后的回跳只接受控制台内固定问答工作区，避免将 `next` 变成开放重定向或权限旁路。
 */
export function resolvePostAuthenticationDestination(
  search: string,
  canUseTenantWorkspace: boolean,
): "/ask" | undefined {
  if (!canUseTenantWorkspace) {
    return undefined;
  }
  return new URLSearchParams(search).get("next") === "/ask" ? "/ask" : undefined;
}
