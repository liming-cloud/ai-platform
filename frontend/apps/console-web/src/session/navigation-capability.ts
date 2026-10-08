import type { NavigationItem } from "@foundation/design-system";

export const NAVIGATION = [
  { id: "operations", label: "运营总览", icon: "overview", requiredCapability: "access:manage", workspace: "TENANT" },
  { id: "access", label: "成员与权限", icon: "people", requiredCapability: "access:manage", workspace: "TENANT" },
  { id: "data", label: "知识库", icon: "data", requiredCapability: "knowledge:write", workspace: "TENANT" },
  { id: "access-policies", label: "访问治理", icon: "governance", requiredCapability: "access:manage", workspace: "TENANT" },
  { id: "models", label: "模型", icon: "models", requiredCapability: "model:read", workspace: "TENANT" },
  { id: "governance", label: "治理", icon: "governance", requiredCapability: "usage:read", workspace: "TENANT" },
  { id: "query", label: "问答", icon: "query", requiredCapability: "assistant:query", workspace: "TENANT" },
  {
    id: "platform-operations",
    label: "平台治理概览",
    icon: "governance",
    requiredCapability: "platform-operations:read",
    workspace: "PLATFORM_TENANT_GOVERNANCE",
  },
  {
    id: "platform",
    label: "租户管理",
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
    label: "模型治理",
    icon: "models",
    requiredCapability: "model-platform:write",
    workspace: "PLATFORM_TENANT_GOVERNANCE",
  },
] as const;

interface NavigationCapability {
  canUseTenantWorkspace: boolean;
  canUsePlatformGovernance: boolean;
  capabilityCodes: string[];
  tenantAdministrator?: boolean;
}

function availableNavigation(capability: NavigationCapability) {
  return NAVIGATION.filter(item => capability.capabilityCodes.includes(item.requiredCapability)
    && (item.workspace === "TENANT" ? capability.canUseTenantWorkspace : capability.canUsePlatformGovernance));
}

/** 可见入口与直达路由分别投影，隐藏未交付导航不能撤销已有路由能力。 */
export function authorizedNavigationTargets(capability: NavigationCapability) {
  return availableNavigation(capability).map(item => item.id);
}

/** 原型导航只承接真实已交付任务，接口授权仍由服务端执行。 */
export function visibleNavigation(capability: NavigationCapability) {
  const available = availableNavigation(capability);
  const tenantNavigation = available.filter(item => item.workspace === "TENANT");
  const platformNavigation = available.filter(item => item.workspace === "PLATFORM_TENANT_GOVERNANCE");
  const result: NavigationItem[] = capability.tenantAdministrator ? [
    { id: "tenant-workspace", label: "租户工作区", children: tenantNavigation.filter(item => item.id === "access").map(item => ({ ...item, label: "成员管理", icon: "accounts" as const })) },
    { id: "tenant-content", label: "内容", children: tenantNavigation.filter(item => item.id === "data").map(item => ({ ...item, icon: "knowledge" as const })) },
    { id: "tenant-governance", label: "治理", children: tenantNavigation.filter(item => item.id === "access-policies" || item.id === "models").map(item => ({ ...item, label: item.id === "models" ? "模型治理" : item.label })) },
    { id: "tenant-delivery", label: "交付", children: tenantNavigation.filter(item => item.id === "governance").map(item => ({ ...item, label: "评测治理", icon: "evaluation" as const })) },
  ].filter(group => group.children.length > 0) : [...tenantNavigation];
  const governance = platformNavigation.filter(item => item.id !== "platform-model-supply");
  const models = platformNavigation.filter(item => item.id === "platform-model-supply");
  if (governance.length > 0) result.push({ id: "platform-governance", label: "平台治理", icon: "governance", children: governance });
  if (models.length > 0) result.push({ id: "platform-model-governance", label: "治理", icon: "models", children: models });
  return result;
}

/**
 * 首次进入选择第一个实际可执行任务；纯会话能力不生成空占位入口。
 */
export function defaultNavigationTarget(
  navigation: readonly NavigationItem[],
): typeof NAVIGATION[number]["id"] | undefined {
  const targets = navigation.flatMap((item) =>
    (item.children?.map((child) => child.id) ?? [item.id]) as typeof NAVIGATION[number]["id"][],
  );
  return targets[0];
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
