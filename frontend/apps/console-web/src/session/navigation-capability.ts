export const NAVIGATION = [
  { id: "access", label: "成员与权限", requiredCapability: "access:manage", workspace: "TENANT" },
  { id: "data", label: "数据", requiredCapability: "knowledge:read", workspace: "TENANT" },
  { id: "models", label: "模型", requiredCapability: "model:read", workspace: "TENANT" },
  { id: "governance", label: "治理", requiredCapability: "usage:read", workspace: "TENANT" },
  { id: "query", label: "问答", requiredCapability: "assistant:query", workspace: "TENANT" },
  {
    id: "platform",
    label: "平台租户",
    requiredCapability: "tenant-governance:write",
    workspace: "PLATFORM_TENANT_GOVERNANCE",
  },
] as const;

/** 以服务端会话投影过滤导航；结果只影响入口可见性，不参与接口授权。 */
export function visibleNavigation({
  canUseTenantWorkspace,
  canUsePlatformGovernance,
  capabilityCodes,
}: {
  canUseTenantWorkspace: boolean;
  canUsePlatformGovernance: boolean;
  capabilityCodes: string[];
}) {
  return NAVIGATION.filter((item) =>
    item.workspace === "TENANT"
      ? canUseTenantWorkspace && capabilityCodes.includes(item.requiredCapability)
      : canUsePlatformGovernance && capabilityCodes.includes(item.requiredCapability),
  );
}

/**
 * 将角色候选权限映射为其将可见的固定控制台入口。
 *
 * <p>预览只帮助管理员理解授予效果；真实接口授权仍由服务端会话权限执行。</p>
 */
export function previewNavigation(permissionCodes: readonly string[]) {
  return NAVIGATION.filter((item) =>
    item.workspace === "TENANT" && permissionCodes.includes(item.requiredCapability),
  );
}
