import type { CurrentUser, RoleKey, TenantFeature } from './types';

export type RouteDecision = 'allowed' | 'forbidden' | 'not_found' | 'tenant_feature_disabled';
export type KnowledgeBaseRouteCapability = 'knowledge-base:read' | 'knowledge-base:manage';
export type NavigationAuthorizationPolicy =
  | { kind: 'server-menu'; menuCodes: readonly string[] }
  | {
    kind: 'registered-permission';
    policyId: 'security-audit-menu-gap' | 'tenant-approval-menu-gap';
    allowOutsideRoleBucket: boolean;
    rationale: string;
  };

export interface NavItem {
  path: string;
  label: string;
  section: string;
  permission?: string;
  authorizationPolicy: NavigationAuthorizationPolicy;
  featureCode?: string;
  capability?: KnowledgeBaseRouteCapability;
  sourceMenuCode?: string;
  disabledReason?: 'tenant_feature_disabled';
}

const routes: Record<RoleKey, NavItem[]> = {
  'platform-admin': [
    { path: '/admin/platform-overview', label: '平台总览', section: '平台治理', permission: 'summary-ops:platform-overview:read', authorizationPolicy: { kind: 'server-menu', menuCodes: ['admin.platform-overview'] } },
    { path: '/admin/tenants', label: '租户与代管', section: '平台治理', permission: 'identity-access:tenant-delegation:manage', authorizationPolicy: { kind: 'server-menu', menuCodes: ['admin.tenants'] } },
    { path: '/admin/model-hub', label: '模型中心', section: 'AI 资源', permission: 'model-hub:provider:read', authorizationPolicy: { kind: 'server-menu', menuCodes: ['admin.model-providers'] }, featureCode: 'model-hub' },
    { path: '/admin/approvals', label: '审批中心', section: '风险控制', permission: 'approval-center:task:read', authorizationPolicy: { kind: 'server-menu', menuCodes: ['admin.approvals'] } },
    { path: '/admin/audit', label: '安全审计', section: '风险控制', permission: 'observability-audit:security-log:read', authorizationPolicy: { kind: 'registered-permission', policyId: 'security-audit-menu-gap', allowOutsideRoleBucket: true, rationale: '服务端尚未登记安全审计菜单；专职审计员以服务端权限码进入。' } },
  ],
  'tenant-admin': [
    { path: '/tenant/overview', label: '租户总览', section: '租户治理', permission: 'summary-ops:tenant-workbench:read', authorizationPolicy: { kind: 'server-menu', menuCodes: ['tenant.overview'] } },
    { path: '/tenant/users', label: '用户管理', section: '组织与权限', permission: 'identity-access:tenant-setting:manage', authorizationPolicy: { kind: 'server-menu', menuCodes: ['tenant.users'] } },
    { path: '/tenant/roles', label: '角色权限', section: '组织与权限', permission: 'identity-access:tenant-setting:manage', authorizationPolicy: { kind: 'server-menu', menuCodes: ['tenant.roles'] } },
    { path: '/tenant/approvals', label: '审批中心', section: '风险控制', permission: 'approval-center:task:read', authorizationPolicy: { kind: 'registered-permission', policyId: 'tenant-approval-menu-gap', allowOutsideRoleBucket: false, rationale: '服务端仅登记平台审批菜单；租户审批入口以租户角色桶与服务端权限码共同约束。' } },
    { path: '/tenant/knowledge-bases', label: '知识库治理', section: 'AI 资产', permission: 'knowledge-base:knowledge-base:read', authorizationPolicy: { kind: 'server-menu', menuCodes: ['business.knowledge-bases'] }, featureCode: 'knowledge-base', capability: 'knowledge-base:manage' },
    { path: '/tenant/applications', label: '应用治理', section: 'AI 资产', permission: 'app-studio:application:read', authorizationPolicy: { kind: 'server-menu', menuCodes: ['business.app-studio'] }, featureCode: 'app-studio' },
    { path: '/tenant/permission-requests', label: '权限申请', section: '风险控制', permission: 'identity-access:permission-request:manage', authorizationPolicy: { kind: 'server-menu', menuCodes: ['tenant.permission-requests'] } },
  ],
  'business-user': [
    { path: '/workspace/home', label: '工作台', section: '业务使用', authorizationPolicy: { kind: 'server-menu', menuCodes: ['business.my-workspace'] } },
    { path: '/workspace/apps', label: 'AI 应用', section: '业务使用', permission: 'app-studio:application:read', authorizationPolicy: { kind: 'server-menu', menuCodes: ['business.app-studio'] }, featureCode: 'app-studio' },
    { path: '/workspace/knowledge-bases', label: '可用知识', section: '业务使用', permission: 'knowledge-base:knowledge-base:read', authorizationPolicy: { kind: 'server-menu', menuCodes: ['business.knowledge-bases'] }, featureCode: 'knowledge-base', capability: 'knowledge-base:read' },
  ],
};

function roleKeysOf(user: CurrentUser): RoleKey[] {
  const roles = user.roleCodes.map((value) => value.toLowerCase().replace('_', '-'));
  return (Object.keys(routes) as RoleKey[]).filter((role) => roles.includes(role));
}

function menuMatchesRole(role: RoleKey, platform: string): boolean {
  const normalized = platform.toUpperCase();
  if (role === 'platform-admin') return normalized === 'ADMIN';
  if (role === 'tenant-admin') return normalized === 'TENANT' || normalized === 'BUSINESS';
  return normalized === 'BUSINESS';
}

export function roleContextLabel(user: CurrentUser): string {
  const labels = roleKeysOf(user).map((role) => (
    role === 'platform-admin' ? '平台管理员' : role === 'tenant-admin' ? '租户管理员' : '业务用户'
  ));
  if (labels.length) return labels.join(' / ');
  if (user.permissionCodes.includes('observability-audit:security-log:read')) return '安全审计员';
  return user.roleCodes.join('').trim() ? '自定义角色' : '未分配角色';
}

export function navFor(user: CurrentUser, tenantFeatures: TenantFeature[] = []): NavItem[] {
  const permissions = new Set(user.permissionCodes);
  const features = new Map(tenantFeatures.map((feature) => [feature.featureCode, feature.enabled]));
  const activeRoles = roleKeysOf(user);
  const customBusinessFallback = activeRoles.length === 0;
  const serverMenus = new Map(user.menus.map((menu) => [menu.code, menu]));
  return (Object.entries(routes) as [RoleKey, NavItem[]][])
    .flatMap(([role, items]) => {
      const bucketActive = activeRoles.includes(role) || (customBusinessFallback && role === 'business-user');
      return items
        .flatMap((item) => {
          const policy = item.authorizationPolicy;
          if (!bucketActive
            && !(policy.kind === 'registered-permission'
              && policy.allowOutsideRoleBucket
              && item.permission
              && permissions.has(item.permission))) return [];
          if (policy.kind === 'registered-permission' && !item.permission) return [];
          if (item.permission && !permissions.has(item.permission)) return [];
          const sourceMenu = policy.kind === 'server-menu'
            ? policy.menuCodes
              .map((code) => serverMenus.get(code))
              .find((menu) => menu && menuMatchesRole(role, menu.platform))
            : undefined;
          if (policy.kind === 'server-menu' && !sourceMenu) return [];
          const disabledReason = item.featureCode && features.get(item.featureCode) === false
            ? 'tenant_feature_disabled' as const
            : undefined;
          return [{
            ...item,
            label: sourceMenu?.name || item.label,
            sourceMenuCode: sourceMenu?.code,
            disabledReason,
          }];
        });
    });
}

export function routeCapabilityFor(
  user: CurrentUser,
  path: string,
  tenantFeatures: TenantFeature[] = [],
): KnowledgeBaseRouteCapability | undefined {
  const route = navFor(user, tenantFeatures).find((item) => item.path === path);
  return route?.disabledReason ? undefined : route?.capability;
}

export function defaultRoute(user: CurrentUser, tenantFeatures: TenantFeature[] = []): string {
  const available = navFor(user, tenantFeatures).filter((item) => !item.disabledReason);
  return available.some((item) => item.path === user.defaultRoute) ? user.defaultRoute : available[0]?.path || '/403';
}

export function routeDecision(user: CurrentUser, path: string, tenantFeatures: TenantFeature[] = []): RouteDecision {
  if (['/403', '/not-found', '/feature-disabled'].includes(path)) return 'allowed';
  if (path.startsWith('/target-state/')) return 'not_found';
  const allowed = navFor(user, tenantFeatures);
  const direct = allowed.find((item) => item.path === path);
  if (direct) return direct.disabledReason || 'allowed';
  const workspaceApp = allowed.find((item) => item.path === '/workspace/apps');
  if (path.startsWith('/workspace/apps/') || path.startsWith('/workspace/sessions/')) {
    return workspaceApp?.disabledReason || (workspaceApp ? 'allowed' : 'forbidden');
  }
  const knownStaticRoute = Object.values(routes)
    .some((items) => items.some((item) => item.path === path));
  return knownStaticRoute ? 'forbidden' : 'not_found';
}
