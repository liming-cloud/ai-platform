import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FluentProvider } from '@fluentui/react-components';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PlatformOverviewPage, TenantOverviewPage } from '../overview-pages';
import { enterpriseLightTheme } from '../theme';
import type { CurrentUser } from '../types';

function menu(path: string, sort: number) {
  return { code: `menu-${sort}`, name: path, platform: 'WEB', path, routeName: path, componentKey: path, sort };
}

const platformUser: CurrentUser = {
  tenantId: '1000', userId: '1', loginName: 'platform-admin', displayName: '平台管理员',
  roleCodes: ['PLATFORM_ADMIN'],
  permissionCodes: [
    'summary-ops:platform-overview:read',
    'identity-access:tenant-delegation:manage',
    'model-hub:provider:read',
    'approval-center:task:read',
    'observability-audit:security-log:read',
  ],
  menus: [
    menu('/admin/platform-overview', 1), menu('/admin/approvals', 2), menu('/admin/tenants', 3),
    menu('/admin/model-hub', 4), menu('/admin/audit', 5),
  ],
  defaultRoute: '/admin/platform-overview', accessTokenExpiresAt: '2099-01-01T00:00:00Z',
};

const tenantUser: CurrentUser = {
  tenantId: '2001', userId: '2', loginName: 'tenant-admin', displayName: '租户管理员',
  roleCodes: ['TENANT_ADMIN'],
  permissionCodes: [
    'summary-ops:tenant-workbench:read',
    'identity-access:tenant-setting:manage',
    'identity-access:permission-request:manage',
    'knowledge-base:knowledge-base:read',
    'app-studio:application:read',
  ],
  menus: [
    menu('/tenant/overview', 1), menu('/tenant/permission-requests', 2), menu('/tenant/users', 3),
    menu('/tenant/roles', 4), menu('/tenant/knowledge-bases', 5), menu('/tenant/applications', 6),
  ],
  defaultRoute: '/tenant/overview', accessTokenExpiresAt: '2099-01-01T00:00:00Z',
};

const platformSummary = {
  asOf: '2026-07-17T08:30:00Z', freshness: 'REALTIME', partial: false, failedMetrics: [],
  traceId: 'platform-trace',
  tenantHealth: { total: 12, active: 10, abnormal: 2, missingTenantAdministrator: 1 },
  modelHealth: { total: 8, healthy: 6, failed: 2, pendingApproval: 3 },
  approvalTodos: { pending: 4 },
  ragSecurity: { authorizationDenialsLast24Hours: 5 },
  appPublishBlocks: { blocked: 2 },
};

const tenantSummary = {
  tenantId: '2001', asOf: '2026-07-17T08:35:00Z', freshness: 'REALTIME', partial: false,
  failedMetrics: [], traceId: 'tenant-trace', enabledFeatures: ['app-studio', 'knowledge-base'],
  userRoleGaps: { activeUsers: 18, usersWithoutRole: 2, rolesWithoutPermission: 1 },
  knowledgeHealth: { total: 7, ready: 4, unindexed: 2, failed: 1 },
  appHealth: { total: 6, published: 3, draft: 2, publishBlocked: 1 },
  permissionTodos: { pending: 3 },
};

function response(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    header: { code: 'SUCCESS', message: 'success', traceId: 'transport-trace' },
    body,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

function renderPage(page: React.ReactNode) {
  return render(<FluentProvider theme={enterpriseLightTheme}>{page}</FluentProvider>);
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('task-first role home pages', () => {
  it('puts actionable platform work before operational context using the exact summary fields', async () => {
    const navigate = vi.fn();
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response(platformSummary));

    renderPage(<PlatformOverviewPage user={platformUser} navigate={navigate} />);

    expect(await screen.findByRole('heading', { name: '平台任务首页' })).toBeInTheDocument();
    const approvalTask = screen.getByText('待处理审批').closest('article');
    expect(approvalTask).not.toBeNull();
    expect(within(approvalTask!).getByText('4')).toBeInTheDocument();
    expect(screen.getByText('异常租户')).toBeInTheDocument();
    expect(screen.getByText('应用发布阻断')).toBeInTheDocument();
    expect(screen.getByText(/数据时间/)).toBeInTheDocument();
    expect(screen.getByText('platform-trace')).toBeInTheDocument();
    expect(screen.queryByLabelText('运营指标')).not.toBeInTheDocument();

    await userEvent.click(within(approvalTask!).getByRole('button', { name: '处理审批' }));
    expect(navigate).toHaveBeenCalledWith('/admin/approvals');
  });

  it('puts tenant authorization and asset exceptions into a real work queue', async () => {
    const navigate = vi.fn();
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response(tenantSummary));

    renderPage(<TenantOverviewPage user={tenantUser} navigate={navigate} />);

    expect(await screen.findByRole('heading', { name: '租户任务首页' })).toBeInTheDocument();
    const permissionTask = screen.getByText('待处理权限申请').closest('article');
    expect(permissionTask).not.toBeNull();
    expect(within(permissionTask!).getByText('3')).toBeInTheDocument();
    expect(screen.getByText('未分配角色的用户')).toBeInTheDocument();
    expect(screen.getByText('未索引知识库')).toBeInTheDocument();
    expect(screen.getByText('发布受阻应用')).toBeInTheDocument();
    expect(screen.getByText('app-studio、knowledge-base')).toBeInTheDocument();

    await userEvent.click(within(permissionTask!).getByRole('button', { name: '处理申请' }));
    expect(navigate).toHaveBeenCalledWith('/tenant/permission-requests');
  });

  it('omits task actions that are absent from the session menu or disabled by tenant features', async () => {
    const navigate = vi.fn();
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response({
      ...tenantSummary,
      enabledFeatures: ['app-studio'],
    }));
    const restrictedUser: CurrentUser = {
      ...tenantUser,
      menus: [menu('/tenant/overview', 1), menu('/tenant/applications', 2)],
    };

    renderPage(<TenantOverviewPage user={restrictedUser} navigate={navigate} />);

    expect(await screen.findByText('发布受阻应用')).toBeInTheDocument();
    expect(screen.queryByText('待处理权限申请')).not.toBeInTheDocument();
    expect(screen.queryByText('未索引知识库')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '查看应用' }));
    expect(navigate).toHaveBeenCalledWith('/tenant/applications');
  });

  it('fails closed and retries the same platform summary seam after a load failure', async () => {
    let attempts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => {
      attempts += 1;
      return attempts === 1 ? Promise.reject(new TypeError('network unavailable')) : response(platformSummary);
    });

    renderPage(<PlatformOverviewPage user={platformUser} navigate={vi.fn()} />);

    expect(await screen.findByText('network unavailable')).toBeInTheDocument();
    expect(screen.queryByText('待处理审批')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '重试' }));

    expect(await screen.findByText('待处理审批')).toBeInTheDocument();
    expect(attempts).toBe(2);
  });
});
