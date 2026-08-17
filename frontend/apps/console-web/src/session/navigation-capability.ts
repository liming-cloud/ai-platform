import type { NavigationItem } from "@foundation/design-system";

export const NAVIGATION = [
  { id: "operations", label: "运营总览", icon: "overview", requiredCapability: "access:manage", workspace: "TENANT" },
  { id: "access", label: "成员与权限", icon: "people", requiredCapability: "access:manage", workspace: "TENANT" },
  { id: "data", label: "数据", icon: "data", requiredCapability: "knowledge:read", workspace: "TENANT" },
  { id: "models", label: "模型", icon: "models", requiredCapability: "model:read", workspace: "TENANT" },
  { id: "governance", label: "治理", icon: "governance", requiredCapability: "usage:read", workspace: "TENANT" },
  { id: "query", label: "问答", icon: "query", requiredCapability: "assistant:query", workspace: "TENANT" },
  {
    id: "platform-operations",
    label: "运营总览",
    icon: "overview",
    requiredCapability: "platform-operations:read",
    workspace: "PLATFORM_TENANT_GOVERNANCE",
  },
  {
    id: "platform-workspace",
    label: "平台工作区",
    icon: "workspace",
    requiredCapability: "platform-session:access",
    workspace: "PLATFORM_TENANT_GOVERNANCE",
  },
  {
    id: "platform",
    label: "平台租户",
    icon: "platform",
    requiredCapability: "tenant-governance:write",
    workspace: "PLATFORM_TENANT_GOVERNANCE",
  },
  {
    id: "platform-accounts",
    label: "平台账户",
    icon: "accounts",
    requiredCapability: "platform:accounts:manage",
    workspace: "PLATFORM_TENANT_GOVERNANCE",
  },
  {
    id: "platform-model-supply",
    label: "平台模型供应链",
    icon: "supply",
    requiredCapability: "model-platform:write",
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
  const tenantNavigation = NAVIGATION.filter((item) =>
    item.workspace === "TENANT"
      && canUseTenantWorkspace
      && capabilityCodes.includes(item.requiredCapability));
  const platformNavigation = NAVIGATION.filter((item) =>
    item.workspace === "PLATFORM_TENANT_GOVERNANCE"
      && canUsePlatformGovernance
      && capabilityCodes.includes(item.requiredCapability));
  const result: NavigationItem[] = [...tenantNavigation];
  if (platformNavigation.length > 0) {
    result.push({
      id: "platform-governance",
      label: "平台治理",
      icon: "governance",
      children: platformNavigation,
    });
  }
  return result;
}

/**
 * 首次进入优先选择实际可执行的平台任务；只有平台会话进入能力时才停留在无数据工作区。
 */
export function defaultNavigationTarget(
  navigation: readonly NavigationItem[],
): typeof NAVIGATION[number]["id"] | undefined {
  const targets = navigation.flatMap((item) =>
    (item.children?.map((child) => child.id) ?? [item.id]) as typeof NAVIGATION[number]["id"][],
  );
  return targets.find((target) => target !== "platform-workspace") ?? targets[0];
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
