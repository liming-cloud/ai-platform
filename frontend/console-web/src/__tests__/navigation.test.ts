import { describe, expect, it } from 'vitest';
import { defaultRoute, navFor, roleContextLabel, routeDecision } from '../navigation';
import type { CurrentUser, TenantFeature } from '../types';

const platformUser: CurrentUser = {
  tenantId: '2001',
  userId: '1001',
  loginName: 'admin',
  displayName: '平台管理员',
  roleCodes: ['platform-admin'],
  permissionCodes: [
    'summary-ops:platform-overview:read',
    'model-hub:provider:read',
  ],
  menus: [
    {
      code: 'admin.platform-overview',
      name: '平台总览',
      platform: 'ADMIN',
      path: '/admin/platform-overview',
      routeName: 'AdminPlatformOverview',
      componentKey: 'admin/platform-overview',
      permission: 'summary-ops:platform-overview:read',
      sort: 1,
    },
    {
      code: 'admin.model-providers',
      name: '服务端模型目录',
      platform: 'ADMIN',
      path: '/admin/model-providers',
      routeName: 'AdminModelProviders',
      componentKey: 'admin/model-providers',
      permission: 'model-hub:provider:read',
      sort: 2,
    },
  ],
  defaultRoute: '/admin/platform-overview',
  accessTokenExpiresAt: '2099-01-01T00:00:00Z',
};

const tenantAdminUser: CurrentUser = {
  ...platformUser,
  userId: '1002',
  loginName: 'tenant-admin',
  displayName: '租户管理员',
  roleCodes: ['tenant-admin'],
  permissionCodes: [
    'identity-access:tenant-setting:manage',
    'identity-access:permission-request:manage',
    'approval-center:task:read',
    'approval-center:task:decide',
  ],
  menus: [
    { code: 'tenant.users', name: '用户管理', platform: 'TENANT', path: '/tenant/users', routeName: 'TenantUsers', componentKey: 'tenant/users', permission: 'identity-access:tenant-setting:manage', sort: 10 },
    { code: 'tenant.roles', name: '角色权限', platform: 'TENANT', path: '/tenant/roles', routeName: 'TenantRoles', componentKey: 'tenant/roles', permission: 'identity-access:tenant-setting:manage', sort: 15 },
    { code: 'tenant.permission-requests', name: '权限申请', platform: 'TENANT', path: '/tenant/permission-requests', routeName: 'TenantPermissionRequests', componentKey: 'tenant/permission-requests', permission: 'identity-access:permission-request:manage', sort: 40 },
  ],
  defaultRoute: '/tenant/users',
};

describe('authorized navigation model', () => {
  it('maps the authorized server menu onto the frozen product route and label', () => {
    const items = navFor(platformUser, []);
    const modelHub = items.find((item) => item.path === '/admin/model-hub');

    expect(modelHub?.label).toBe('服务端模型目录');
    expect(modelHub?.sourceMenuCode).toBe('admin.model-providers');
  });

  it('keeps a disabled tenant feature visible but blocks direct route access', () => {
    const features: TenantFeature[] = [
      { featureCode: 'model-hub', featureName: '模型中心', enabled: false, source: 'tenant' },
    ];

    const modelHub = navFor(platformUser, features).find((item) => item.path === '/admin/model-hub');

    expect(modelHub?.disabledReason).toBe('tenant_feature_disabled');
    expect(routeDecision(platformUser, '/admin/model-hub', features)).toBe('tenant_feature_disabled');
  });

  it('keeps tenant governance entries distinct when menus come from the server', () => {
    const governanceItems = navFor(tenantAdminUser).filter((item) => item.path.startsWith('/tenant/'));

    expect(governanceItems.map((item) => [item.path, item.label, item.sourceMenuCode])).toEqual([
      ['/tenant/users', '用户管理', 'tenant.users'],
      ['/tenant/roles', '角色权限', 'tenant.roles'],
      ['/tenant/approvals', '审批中心', undefined],
      ['/tenant/permission-requests', '权限申请', 'tenant.permission-requests'],
    ]);
    expect(routeDecision(tenantAdminUser, '/tenant/approvals')).toBe('allowed');
  });

  it('retains the platform approval entry for an authorized platform administrator', () => {
    const approvalUser: CurrentUser = {
      ...platformUser,
      permissionCodes: [...platformUser.permissionCodes, 'approval-center:task:read'],
      menus: [...platformUser.menus, {
        code: 'admin.approvals',
        name: '审批中心',
        platform: 'ADMIN',
        path: '/admin/approvals',
        routeName: 'AdminApprovals',
        componentKey: 'admin/approvals',
        permission: 'approval-center:task:read',
        sort: 40,
      }],
    };

    expect(navFor(approvalUser).filter((item) => item.label === '审批中心').map((item) => item.path)).toEqual([
      '/admin/approvals',
    ]);
    expect(routeDecision(approvalUser, '/admin/approvals')).toBe('allowed');
    expect(routeDecision(approvalUser, '/tenant/approvals')).toBe('forbidden');
  });

  it('builds the authorized union for a user with multiple role assignments', () => {
    const multiRoleUser: CurrentUser = {
      ...tenantAdminUser,
      roleCodes: ['platform-admin', 'tenant-admin'],
      permissionCodes: [
        ...tenantAdminUser.permissionCodes,
        'summary-ops:platform-overview:read',
      ],
      menus: [
        ...tenantAdminUser.menus,
        platformUser.menus[0],
      ],
    };

    expect(navFor(multiRoleUser).map((item) => item.path)).toEqual([
      '/admin/platform-overview',
      '/tenant/users',
      '/tenant/roles',
      '/tenant/approvals',
      '/tenant/permission-requests',
    ]);
    expect(routeDecision(multiRoleUser, '/tenant/users')).toBe('allowed');
    expect(roleContextLabel(multiRoleUser)).toBe('平台管理员 / 租户管理员');
  });

  it('allows a dedicated auditor by permission even without an administrator role bucket', () => {
    const auditor: CurrentUser = {
      ...platformUser,
      roleCodes: ['security-auditor'],
      permissionCodes: ['observability-audit:security-log:read'],
      menus: [],
      defaultRoute: '/admin/audit',
    };

    expect(navFor(auditor).map((item) => item.path)).toEqual(['/admin/audit']);
    expect(defaultRoute(auditor)).toBe('/admin/audit');
    expect(routeDecision(auditor, '/admin/audit')).toBe('allowed');
    expect(roleContextLabel(auditor)).toBe('安全审计员');
  });

  it('registers every route without a server menu as an explicit permission policy', () => {
    const tenantApproval = navFor(tenantAdminUser).find((item) => item.path === '/tenant/approvals');
    expect(tenantApproval?.authorizationPolicy).toMatchObject({
      kind: 'registered-permission',
      policyId: 'tenant-approval-menu-gap',
      allowOutsideRoleBucket: false,
    });

    const auditor: CurrentUser = {
      ...platformUser,
      roleCodes: ['security-auditor'],
      permissionCodes: ['observability-audit:security-log:read'],
      menus: [],
      defaultRoute: '/admin/audit',
    };
    const audit = navFor(auditor).find((item) => item.path === '/admin/audit');
    expect(audit?.authorizationPolicy).toMatchObject({
      kind: 'registered-permission',
      policyId: 'security-audit-menu-gap',
      allowOutsideRoleBucket: true,
    });
  });

  it('does not mislabel an unmapped or unassigned identity as a business user', () => {
    expect(roleContextLabel({ ...platformUser, roleCodes: ['custom-risk-reviewer'] }))
      .toBe('自定义角色');
    expect(roleContextLabel({ ...platformUser, roleCodes: [] })).toBe('未分配角色');
  });

  it('fails closed when a feature-gated permission exists without its authoritative server menu', () => {
    const businessUser: CurrentUser = {
      tenantId: '2001',
      userId: '1003',
      loginName: 'business-user',
      displayName: '业务用户',
      roleCodes: ['business-user'],
      permissionCodes: ['identity-access:workspace:use', 'app-studio:application:read'],
      menus: [{
        code: 'business.my-workspace',
        name: '我的工作台',
        platform: 'BUSINESS',
        path: '/workspace/home',
        routeName: 'BusinessMyWorkspace',
        componentKey: 'workspace/home',
        permission: 'identity-access:workspace:use',
        sort: 8,
      }],
      defaultRoute: '/workspace/home',
      accessTokenExpiresAt: '2099-01-01T00:00:00Z',
    };

    expect(navFor(businessUser).map((item) => item.path)).toEqual(['/workspace/home']);
    expect(routeDecision(businessUser, '/workspace/apps')).toBe('forbidden');
    expect(routeDecision(businessUser, '/workspace/sessions/S-1')).toBe('forbidden');
  });

  it('omits capabilities without a production backend seam and treats their old URLs as not found', () => {
    const platformItems = navFor(platformUser);
    const tenantItems = navFor(tenantAdminUser);

    expect(platformItems.some((item) => item.path.startsWith('/target-state/'))).toBe(false);
    expect(tenantItems.some((item) => item.path.startsWith('/target-state/'))).toBe(false);
    expect(routeDecision(platformUser, '/target-state/evaluation-center')).toBe('not_found');
    expect(routeDecision(tenantAdminUser, '/target-state/workflow-builder')).toBe('not_found');
  });

  it('distinguishes an unknown authenticated URL from a known route without permission', () => {
    expect(routeDecision(platformUser, '/totally-unknown')).toBe('not_found');
    expect(routeDecision(platformUser, '/tenant/users')).toBe('forbidden');
  });

  it('never resolves an obsolete target-state URL as the default entry', () => {
    expect(defaultRoute({ ...platformUser, defaultRoute: '/target-state/evaluation-center' })).toBe('/admin/platform-overview');
  });

  it('keeps shared business menu codes inside the authenticated role bucket', () => {
    const sharedMenus = [
      {
        code: 'business.knowledge-bases',
        name: '企业知识',
        platform: 'BUSINESS',
        path: '/workspace/knowledge-bases',
        routeName: 'BusinessKnowledgeBases',
        componentKey: 'business/knowledge-bases',
        permission: 'knowledge-base:knowledge-base:read',
        sort: 20,
      },
      {
        code: 'business.app-studio',
        name: 'AI 应用',
        platform: 'BUSINESS',
        path: '/workspace/apps',
        routeName: 'BusinessAppStudio',
        componentKey: 'business/app-studio',
        permission: 'app-studio:application:read',
        sort: 30,
      },
    ];
    const permissionCodes = [
      'knowledge-base:knowledge-base:read',
      'app-studio:application:read',
    ];
    const businessUser: CurrentUser = {
      ...tenantAdminUser,
      roleCodes: ['business-user'],
      permissionCodes,
      menus: sharedMenus,
      defaultRoute: '/workspace/knowledge-bases',
    };
    const tenantAdministrator: CurrentUser = {
      ...tenantAdminUser,
      permissionCodes,
      menus: sharedMenus,
      defaultRoute: '/tenant/knowledge-bases',
    };

    expect(navFor(businessUser).map((item) => item.path)).toEqual([
      '/workspace/apps',
      '/workspace/knowledge-bases',
    ]);
    expect(routeDecision(businessUser, '/tenant/knowledge-bases')).toBe('forbidden');
    expect(routeDecision(businessUser, '/tenant/applications')).toBe('forbidden');

    expect(navFor(tenantAdministrator).map((item) => item.path)).toEqual([
      '/tenant/knowledge-bases',
      '/tenant/applications',
    ]);
    expect(routeDecision(tenantAdministrator, '/workspace/knowledge-bases')).toBe('forbidden');
    expect(routeDecision(tenantAdministrator, '/workspace/apps')).toBe('forbidden');
  });

  it('never treats a foreign server menu as authority for a platform route bucket', () => {
    const businessWithPlatformMenu: CurrentUser = {
      ...tenantAdminUser,
      roleCodes: ['business-user'],
      permissionCodes: ['summary-ops:platform-overview:read'],
      menus: [platformUser.menus[0]],
      defaultRoute: '/workspace/home',
    };
    const platformWithBusinessMenus: CurrentUser = {
      ...platformUser,
      permissionCodes: [
        ...platformUser.permissionCodes,
        'knowledge-base:knowledge-base:read',
        'app-studio:application:read',
      ],
      menus: [
        ...platformUser.menus,
        {
          code: 'business.knowledge-bases',
          name: '企业知识',
          platform: 'BUSINESS',
          path: '/workspace/knowledge-bases',
          routeName: 'BusinessKnowledgeBases',
          componentKey: 'business/knowledge-bases',
          permission: 'knowledge-base:knowledge-base:read',
          sort: 20,
        },
        {
          code: 'business.app-studio',
          name: 'AI 应用',
          platform: 'BUSINESS',
          path: '/workspace/apps',
          routeName: 'BusinessAppStudio',
          componentKey: 'business/app-studio',
          permission: 'app-studio:application:read',
          sort: 30,
        },
      ],
    };

    expect(navFor(businessWithPlatformMenu)).toEqual([]);
    expect(routeDecision(businessWithPlatformMenu, '/admin/platform-overview')).toBe('forbidden');
    expect(navFor(platformWithBusinessMenus).map((item) => item.path)).toEqual([
      '/admin/platform-overview',
      '/admin/model-hub',
    ]);
    expect(routeDecision(platformWithBusinessMenus, '/tenant/knowledge-bases')).toBe('forbidden');
    expect(routeDecision(platformWithBusinessMenus, '/workspace/knowledge-bases')).toBe('forbidden');
  });

  it('uses normalized BUSINESS menus as a scoped workspace fallback for a custom role', () => {
    const customBusinessUser: CurrentUser = {
      ...tenantAdminUser,
      roleCodes: ['custom-knowledge-editor'],
      permissionCodes: [
        'knowledge-base:knowledge-base:read',
        'app-studio:application:read',
        'summary-ops:platform-overview:read',
        'identity-access:tenant-setting:manage',
      ],
      menus: [
        {
          code: 'business.knowledge-bases',
          name: '企业知识',
          platform: 'business',
          path: '/workspace/knowledge-bases',
          routeName: 'BusinessKnowledgeBases',
          componentKey: 'business/knowledge-bases',
          permission: 'knowledge-base:knowledge-base:read',
          sort: 20,
        },
        {
          code: 'business.app-studio',
          name: 'AI 应用',
          platform: 'BuSiNeSs',
          path: '/workspace/apps',
          routeName: 'BusinessAppStudio',
          componentKey: 'business/app-studio',
          permission: 'app-studio:application:read',
          sort: 30,
        },
        platformUser.menus[0],
        tenantAdminUser.menus[0],
      ],
      defaultRoute: '/workspace/knowledge-bases',
    };

    expect(navFor(customBusinessUser).map((item) => item.path)).toEqual([
      '/workspace/apps',
      '/workspace/knowledge-bases',
    ]);
    expect(defaultRoute(customBusinessUser)).toBe('/workspace/knowledge-bases');
    expect(routeDecision(customBusinessUser, '/workspace/apps')).toBe('allowed');
    expect(routeDecision(customBusinessUser, '/workspace/knowledge-bases')).toBe('allowed');
    expect(routeDecision(customBusinessUser, '/admin/platform-overview')).toBe('forbidden');
    expect(routeDecision(customBusinessUser, '/tenant/users')).toBe('forbidden');
  });

  it('rejects an ADMIN menu that reuses a business code for a custom role', () => {
    const customBusinessUser: CurrentUser = {
      ...tenantAdminUser,
      roleCodes: ['custom-business-editor'],
      permissionCodes: [
        'knowledge-base:knowledge-base:read',
        'app-studio:application:read',
      ],
      menus: [
        {
          code: 'business.knowledge-bases',
          name: '伪造知识入口',
          platform: 'ADMIN',
          path: '/workspace/knowledge-bases',
          routeName: 'BusinessKnowledgeBases',
          componentKey: 'business/knowledge-bases',
          permission: 'knowledge-base:knowledge-base:read',
          sort: 20,
        },
        {
          code: 'business.app-studio',
          name: '伪造应用入口',
          platform: 'admin',
          path: '/workspace/apps',
          routeName: 'BusinessAppStudio',
          componentKey: 'business/app-studio',
          permission: 'app-studio:application:read',
          sort: 30,
        },
      ],
      defaultRoute: '/workspace/knowledge-bases',
    };

    expect(navFor(customBusinessUser)).toEqual([]);
    expect(defaultRoute(customBusinessUser)).toBe('/403');
    expect(routeDecision(customBusinessUser, '/workspace/apps')).toBe('forbidden');
    expect(routeDecision(customBusinessUser, '/workspace/knowledge-bases')).toBe('forbidden');
  });

  it('rejects ADMIN menus that reuse business codes for a built-in business user', () => {
    const businessUser: CurrentUser = {
      ...tenantAdminUser,
      roleCodes: ['business-user'],
      permissionCodes: [
        'knowledge-base:knowledge-base:read',
        'app-studio:application:read',
      ],
      menus: [
        {
          code: 'business.knowledge-bases',
          name: '伪造知识入口',
          platform: 'ADMIN',
          path: '/workspace/knowledge-bases',
          routeName: 'BusinessKnowledgeBases',
          componentKey: 'business/knowledge-bases',
          permission: 'knowledge-base:knowledge-base:read',
          sort: 20,
        },
        {
          code: 'business.app-studio',
          name: '伪造应用入口',
          platform: 'ADMIN',
          path: '/workspace/apps',
          routeName: 'BusinessAppStudio',
          componentKey: 'business/app-studio',
          permission: 'app-studio:application:read',
          sort: 30,
        },
      ],
      defaultRoute: '/workspace/apps',
    };

    expect(navFor(businessUser)).toEqual([]);
    expect(defaultRoute(businessUser)).toBe('/403');
    expect(routeDecision(businessUser, '/workspace/apps')).toBe('forbidden');
    expect(routeDecision(businessUser, '/workspace/knowledge-bases')).toBe('forbidden');
  });
});
