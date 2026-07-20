import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { SESSION_EXPIRED_EVENT, session } from '../api';

const AUTH_SESSION_KEY = 'aip.auth-session';
const TOKEN_KEY = 'aip.access-token';

const platformUser = {
  tenantId: '1000', userId: '1', loginName: 'platform-admin', displayName: '平台管理员',
  roleCodes: ['PLATFORM_ADMIN'],
  permissionCodes: ['summary-ops:platform-overview:read', 'approval-center:task:read', 'observability-audit:security-log:read'],
  menus: [
    { code: 'admin.platform-overview', name: '平台总览', platform: 'ADMIN', path: '/admin/platform-overview', routeName: 'AdminPlatformOverview', componentKey: 'admin/platform-overview', permission: 'summary-ops:platform-overview:read', sort: 1 },
    { code: 'admin.approvals', name: '审批中心', platform: 'ADMIN', path: '/admin/approvals', routeName: 'AdminApprovals', componentKey: 'admin/approvals', permission: 'approval-center:task:read', sort: 40 },
  ], defaultRoute: '/admin/platform-overview', accessTokenExpiresAt: '2099-01-01T00:00:00Z',
};

const businessUser = {
  tenantId: '2001', userId: '8', loginName: 'business-user', displayName: '业务用户',
  roleCodes: ['BUSINESS_USER'], permissionCodes: ['identity-access:workspace:use', 'app-studio:application:read'], menus: [
    { code: 'business.my-workspace', name: '我的工作台', platform: 'BUSINESS', path: '/workspace/home', routeName: 'BusinessMyWorkspace', componentKey: 'workspace/home', permission: 'identity-access:workspace:use', sort: 8 },
    { code: 'business.app-studio', name: 'AI 应用', platform: 'BUSINESS', path: '/workspace/app-studio', routeName: 'BusinessAppStudio', componentKey: 'business/app-studio/list', permission: 'app-studio:application:read', sort: 30 },
  ],
  defaultRoute: '/workspace/home', accessTokenExpiresAt: '2099-01-01T00:00:00Z',
};

function loginResult(user: typeof platformUser, token: string) {
  return {
    token,
    userId: user.userId,
    tenantId: user.tenantId,
    displayName: user.displayName,
    roleCodes: user.roleCodes,
    tokenExpiresAt: user.accessTokenExpiresAt,
  };
}

function synchronizeRemoteSession(input: {
  token: string | null;
  generation: string;
  tenantId?: string;
  userId?: string;
  expiresAt?: string | null;
}) {
  const value = JSON.stringify({
    version: 1,
    token: input.token,
    generation: input.generation,
    identity: input.tenantId && input.userId
      ? { tenantId: input.tenantId, userId: input.userId }
      : null,
    expiresAt: input.expiresAt ?? null,
  });
  localStorage.setItem(AUTH_SESSION_KEY, value);
  if (input.token) localStorage.setItem(TOKEN_KEY, input.token);
  else localStorage.removeItem(TOKEN_KEY);
  window.dispatchEvent(new StorageEvent('storage', { key: AUTH_SESSION_KEY, newValue: value }));
}

async function flushMicrotasks() {
  for (let pass = 0; pass < 12; pass += 1) await Promise.resolve();
}

function response(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify({ header: { code: status === 200 ? 'SUCCESS' : 'FAILED', message: status === 200 ? 'success' : 'failed', traceId: 'test-trace' }, body }), { status, headers: { 'Content-Type': 'application/json' } }));
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

const platformSummary = {
  asOf: '2026-07-10T00:00:00Z', freshness: 'REALTIME', partial: false, failedMetrics: [],
  traceId: 'summary-trace',
  tenantHealth: { total: 3, active: 2, abnormal: 1, missingTenantAdministrator: 1 },
  modelHealth: { total: 2, healthy: 1, failed: 1, pendingApproval: 1 },
  approvalTodos: { pending: 1 }, ragSecurity: { authorizationDenialsLast24Hours: 4 },
  appPublishBlocks: { blocked: 0 },
};

beforeEach(() => {
  localStorage.clear();
  window.history.replaceState({}, '', '/');
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1024 });
});

describe('AI Platform product shell', () => {
  it('requires a tenant-scoped server session before rendering product entries', async () => {
    render(<App />);
    expect(await screen.findByText('登录工作空间')).toBeInTheDocument();
    expect(screen.getByRole('form', { name: '登录工作空间' })).toHaveAttribute('aria-busy', 'false');
    expect(screen.getByText('企业 AI 可信运行平台')).toHaveClass('on-brand-metadata');
    expect(screen.getByLabelText(/租户编码/)).toBeInTheDocument();
    expect(screen.queryByText('角色切换')).not.toBeInTheDocument();
  });

  it('announces login validation as an in-progress form operation', async () => {
    const loginRequest = deferred<Response>();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/auth/login')) return loginRequest.promise;
      if (path.includes('/auth/me')) return response(businessUser);
      return response([]);
    });
    const actor = userEvent.setup();

    render(<App />);

    const form = await screen.findByRole('form', { name: '登录工作空间' });
    await actor.type(screen.getByLabelText(/租户编码/), 'tenant-a');
    await actor.type(screen.getByLabelText(/^账号/), 'operator');
    await actor.type(screen.getByLabelText(/^密码/), 'a-private-password');
    await actor.click(screen.getByRole('button', { name: '登录' }));

    expect(form).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByRole('button', { name: '正在验证' })).toBeDisabled();

    loginRequest.resolve(response(loginResult(businessUser, 'issued-token')));
    expect(await screen.findByRole('heading', { name: '你好，业务用户', level: 1 })).toBeInTheDocument();
  });

  it('renders the platform entry from auth/me and live SummaryOps data', async () => {
    localStorage.setItem('aip.access-token', 'token');
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/auth/me')) return response(platformUser);
      return response(platformSummary);
    });
    render(<App />);
    expect(await screen.findByText('平台任务首页')).toBeInTheDocument();
    expect(screen.getByText('安全审计')).toBeInTheDocument();
    expect(await screen.findByText('summary-trace')).toBeInTheDocument();
    expect(screen.queryByText('评测中心')).not.toBeInTheDocument();
    const securityContext = screen.getByLabelText('当前安全上下文');
    expect(within(securityContext).getByText('租户 1000')).toBeInTheDocument();
    expect(within(securityContext).getByText('角色 平台管理员')).toBeInTheDocument();
    expect(within(securityContext).getByText('安全上下文已建立')).toBeInTheDocument();
  });

  it('shows the real auditor security context instead of a business-user fallback', async () => {
    localStorage.setItem('aip.access-token', 'token');
    const auditor = {
      ...platformUser,
      roleCodes: ['security-auditor'],
      permissionCodes: ['observability-audit:security-log:read'],
      menus: [],
      defaultRoute: '/admin/audit',
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => (
      String(input).includes('/auth/me') ? response(auditor) : response([])
    ));

    render(<App />);

    const securityContext = await screen.findByLabelText('当前安全上下文');
    expect(within(securityContext).getByText('角色 安全审计员')).toBeInTheDocument();
    expect(within(securityContext).queryByText('角色 业务用户')).not.toBeInTheDocument();
  });

  it('returns an explicit not-found page for an obsolete target-state URL', async () => {
    localStorage.setItem('aip.access-token', 'token');
    window.history.replaceState({}, '', '/target-state/evaluation-center');
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response(platformUser));

    render(<App />);

    expect(await screen.findByText('页面不存在')).toBeInTheDocument();
    expect(screen.queryByText('产品边界已保留')).not.toBeInTheDocument();
  });

  it('does not render tenant governance navigation for a business user', async () => {
    localStorage.setItem('aip.access-token', 'token');
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => String(input).includes('/auth/me') ? response(businessUser) : response([]));
    render(<App />);
    expect(await screen.findByText('你好，业务用户')).toBeInTheDocument();
    expect(screen.getAllByText('AI 应用').length).toBeGreaterThan(0);
    expect(screen.queryByText('角色权限')).not.toBeInTheDocument();
    expect(screen.queryByText('租户与代管')).not.toBeInTheDocument();
  });

  it('makes the mobile navigation inert when closed and restores focus after Escape', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    vi.spyOn(window, 'matchMedia').mockImplementation((query) => ({
      matches: query === '(max-width: 768px)',
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }));
    localStorage.setItem('aip.access-token', 'token');
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => String(input).includes('/auth/me') ? response(platformUser) : response(platformSummary));
    const actor = userEvent.setup();

    render(<App />);

    const openButton = await screen.findByRole('button', { name: '打开导航' });
    const navigation = screen.getByLabelText('主导航');
    expect(screen.getByRole('link', { name: '跳到主要内容' })).toHaveAttribute('href', '#main-content');
    expect(openButton).toHaveAttribute('aria-expanded', 'false');
    expect(navigation).toHaveAttribute('aria-hidden', 'true');

    await actor.click(openButton);

    expect(openButton).toHaveAttribute('aria-expanded', 'true');
    expect(navigation).not.toHaveAttribute('aria-hidden');
    const closeButton = screen.getByRole('button', { name: '关闭导航' });
    expect(closeButton).toHaveFocus();

    await actor.keyboard('{Shift>}{Tab}{/Shift}');
    expect(screen.getByRole('button', { name: '退出登录' })).toHaveFocus();
    await actor.keyboard('{Tab}');
    expect(closeButton).toHaveFocus();

    await actor.keyboard('{Escape}');

    expect(openButton).toHaveAttribute('aria-expanded', 'false');
    expect(navigation).toHaveAttribute('aria-hidden', 'true');
    expect(openButton).toHaveFocus();
  });

  it('moves focus to the new page heading after choosing a mobile navigation entry', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    vi.spyOn(window, 'matchMedia').mockImplementation((query) => ({
      matches: query === '(max-width: 768px)',
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }));
    localStorage.setItem('aip.access-token', 'token');
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => String(input).includes('/auth/me') ? response(platformUser) : response([]));
    const actor = userEvent.setup();

    render(<App />);

    const openButton = await screen.findByRole('button', { name: '打开导航' });
    await actor.click(openButton);
    const navigation = screen.getByLabelText('主导航');
    await actor.click(within(navigation).getAllByRole('button', { name: '审批中心' })[0]);

    const heading = await screen.findByRole('heading', { name: '审批中心', level: 1 });
    expect(openButton).toHaveAttribute('aria-expanded', 'false');
    expect(navigation).toHaveAttribute('aria-hidden', 'true');
    expect(heading).toHaveAttribute('tabindex', '-1');
    expect(heading).toHaveFocus();
  });

  it('enforces the documented activation password bound and removes the one-time token after success', async () => {
    window.history.replaceState({}, '', '/activate?token=one-time-secret');
    let requestBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
      requestBody = JSON.parse(String(init?.body));
      return response({ invitationId: 'I-1', userId: 'U-1', tenantId: 'T-1', status: 'ACTIVATED' });
    });
    const actor = userEvent.setup();

    render(<App />);

    const password = await screen.findByLabelText(/^新密码/);
    const confirmation = screen.getByLabelText(/^确认密码/);
    expect(password).toHaveAttribute('maxlength', '128');
    expect(confirmation).toHaveAttribute('maxlength', '128');
    fireEvent.change(password, { target: { value: 'x'.repeat(129) } });
    fireEvent.change(confirmation, { target: { value: 'x'.repeat(129) } });
    expect(screen.getByRole('button', { name: '激活账号' })).toBeDisabled();

    fireEvent.change(password, { target: { value: 'x'.repeat(128) } });
    fireEvent.change(confirmation, { target: { value: 'x'.repeat(128) } });
    await actor.click(screen.getByRole('button', { name: '激活账号' }));

    expect(await screen.findByText('账号已激活')).toBeInTheDocument();
    expect(requestBody).toEqual({ token: 'one-time-secret', password: 'x'.repeat(128) });
    expect(window.location.pathname).toBe('/activate');
    expect(window.location.search).toBe('');
  });

  it('fails closed when a business user opens a platform route directly', async () => {
    localStorage.setItem('aip.access-token', 'token');
    window.history.replaceState({}, '', '/admin/tenants');
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response(businessUser));
    render(<App />);
    expect(await screen.findByText('无权访问')).toBeInTheDocument();
    expect(screen.queryByText('发起租户代管')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '返回默认入口' }));

    expect(window.location.pathname).toBe('/workspace/home');
    expect(await screen.findByText('你好，业务用户')).toBeInTheDocument();
  });

  it('does not expose a feature route when the user cannot load features and the server omitted its menu', async () => {
    localStorage.setItem('aip.access-token', 'token');
    const userWithoutApplicationMenu = {
      ...businessUser,
      menus: businessUser.menus.filter((menu) => menu.code !== 'business.app-studio'),
    };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/auth/me')) return response(userWithoutApplicationMenu);
      return response([]);
    });

    render(<App />);

    expect(await screen.findByText('你好，业务用户')).toBeInTheDocument();
    expect(within(screen.getByRole('navigation')).queryByText('AI 应用', { exact: true })).not.toBeInTheDocument();
    expect(fetchSpy.mock.calls.some(([input]) => String(input).includes('/iam/tenant-features'))).toBe(false);
  });

  it('logs in and resolves the default route from auth/me', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/login')) return response(loginResult(businessUser, 'issued-token'));
      if (path.includes('/auth/me')) return response(businessUser);
      return response([]);
    });
    render(<App />);
    await user.type(await screen.findByLabelText(/租户编码/), '2001');
    await user.type(screen.getByLabelText(/账号/), 'business-user');
    await user.type(screen.getByLabelText(/密码/), 'secret');
    await user.click(screen.getByRole('button', { name: '登录' }));
    expect(await screen.findByText('你好，业务用户')).toBeInTheDocument();
    await waitFor(() => expect(localStorage.getItem('aip.access-token')).toBe('issued-token'));
  });

  it('blocks a direct route when the authenticated tenant feature is disabled', async () => {
    localStorage.setItem('aip.access-token', 'token');
    window.history.replaceState({}, '', '/admin/model-hub');
    const user = {
      ...platformUser,
      permissionCodes: [...platformUser.permissionCodes, 'model-hub:provider:read', 'identity-access:iam-config:read'],
      menus: [{
        code: 'admin.model-providers', name: '模型中心', platform: 'ADMIN', path: '/admin/model-providers',
        routeName: 'AdminModelProviders', componentKey: 'admin/model-providers',
        permission: 'model-hub:provider:read', sort: 2,
      }],
    };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/auth/me')) return response(user);
      if (path.includes('/iam/tenant-features')) return response([
        { featureCode: 'model-hub', featureName: '模型中心', enabled: false, source: 'tenant' },
      ]);
      return response([]);
    });

    render(<App />);

    expect(await screen.findByText('功能未启用')).toBeInTheDocument();
    expect(fetchSpy.mock.calls.some(([input]) => String(input).includes('/model-providers'))).toBe(false);
  });

  it('keeps a feature-disabled navigation item keyboard-readable without activating it', async () => {
    localStorage.setItem('aip.access-token', 'token');
    const user = {
      ...platformUser,
      permissionCodes: [...platformUser.permissionCodes, 'model-hub:provider:read', 'identity-access:iam-config:read'],
      menus: [...platformUser.menus, {
        code: 'admin.model-providers', name: '模型中心', platform: 'ADMIN', path: '/admin/model-providers',
        routeName: 'AdminModelProviders', componentKey: 'admin/model-providers',
        permission: 'model-hub:provider:read', sort: 2,
      }],
    };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/auth/me')) return response(user);
      if (path.includes('/iam/tenant-features')) return response([
        { featureCode: 'model-hub', featureName: '模型中心', enabled: false, source: 'tenant' },
      ]);
      return response(platformSummary);
    });
    const actor = userEvent.setup();

    render(<App />);

    expect(await screen.findByText('平台任务首页')).toBeInTheDocument();
    const disabledItem = screen.getByRole('button', { name: '模型中心' });
    expect(disabledItem).not.toBeDisabled();
    expect(disabledItem).toHaveAttribute('aria-disabled', 'true');
    expect(disabledItem).toHaveAccessibleDescription('当前租户未启用此功能');
    disabledItem.focus();
    await actor.keyboard('{Enter}');

    expect(window.location.pathname).toBe('/admin/platform-overview');
    expect(fetchSpy.mock.calls.some(([input]) => String(input).includes('/model-providers'))).toBe(false);
  });

  it('returns to login when a page request reports an expired session', async () => {
    localStorage.setItem('aip.access-token', 'token');
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/auth/me')) return response(platformUser);
      return response(null, 401);
    });

    render(<App />);

    expect(await screen.findByText('登录工作空间')).toBeInTheDocument();
    expect(localStorage.getItem('aip.access-token')).toBeNull();
  });

  it('clears the old tenant shell when another tab logs into a different tenant', async () => {
    session.save(
      'tenant-a-token',
      { tenantId: platformUser.tenantId, userId: platformUser.userId },
      platformUser.accessTokenExpiresAt,
    );
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => (
      String(input).includes('/auth/me') ? response(platformUser) : response(platformSummary)
    ));

    render(<App />);
    expect(await screen.findByText('平台任务首页')).toBeInTheDocument();

    act(() => {
      synchronizeRemoteSession({
        token: 'tenant-b-token',
        generation: 'remote-tenant-b-login',
        tenantId: businessUser.tenantId,
        userId: businessUser.userId,
        expiresAt: businessUser.accessTokenExpiresAt,
      });
    });

    expect(await screen.findByText('登录工作空间')).toBeInTheDocument();
    expect(screen.queryByText('平台任务首页')).not.toBeInTheDocument();
    expect(session.snapshot()).toMatchObject({
      token: 'tenant-b-token',
      identity: { tenantId: businessUser.tenantId, userId: businessUser.userId },
    });
  });

  it('clears the authenticated shell when another tab logs out', async () => {
    session.save(
      'tenant-a-token',
      { tenantId: platformUser.tenantId, userId: platformUser.userId },
      platformUser.accessTokenExpiresAt,
    );
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => (
      String(input).includes('/auth/me') ? response(platformUser) : response(platformSummary)
    ));

    render(<App />);
    expect(await screen.findByText('平台任务首页')).toBeInTheDocument();

    act(() => {
      synchronizeRemoteSession({ token: null, generation: 'remote-logout' });
    });

    expect(await screen.findByText('登录工作空间')).toBeInTheDocument();
    expect(screen.queryByText('平台任务首页')).not.toBeInTheDocument();
    expect(session.token()).toBeNull();
  });

  it('expires the shell at accessTokenExpiresAt using a fail-closed timer', async () => {
    vi.useFakeTimers();
    const now = new Date('2030-01-01T00:00:00Z');
    const expiresAt = new Date(now.getTime() + 60_000).toISOString();
    vi.setSystemTime(now);
    const expiringUser = { ...platformUser, accessTokenExpiresAt: expiresAt };
    session.save(
      'expiring-token',
      { tenantId: expiringUser.tenantId, userId: expiringUser.userId },
      expiresAt,
    );
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => (
      String(input).includes('/auth/me') ? response(expiringUser) : response(platformSummary)
    ));

    render(<App />);
    await act(flushMicrotasks);
    expect(screen.getByLabelText('当前安全上下文')).toBeInTheDocument();

    act(() => vi.advanceTimersByTime(60_001));
    await act(flushMicrotasks);

    expect(screen.getByText('登录工作空间')).toBeInTheDocument();
    expect(screen.queryByLabelText('当前安全上下文')).not.toBeInTheDocument();
    expect(session.token()).toBeNull();
  });

  it.each(['pageshow', 'visibilitychange'] as const)(
    'revalidates an expired token on %s before restoring the shell',
    async (eventName) => {
      vi.useFakeTimers();
      const now = new Date('2030-01-01T00:00:00Z');
      const expiresAt = new Date(now.getTime() + 60_000).toISOString();
      vi.setSystemTime(now);
      const expiringUser = { ...platformUser, accessTokenExpiresAt: expiresAt };
      session.save(
        'expiring-token',
        { tenantId: expiringUser.tenantId, userId: expiringUser.userId },
        expiresAt,
      );
      vi.spyOn(globalThis, 'fetch').mockImplementation((input) => (
        String(input).includes('/auth/me') ? response(expiringUser) : response(platformSummary)
      ));
      if (eventName === 'visibilitychange') {
        vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
      }

      render(<App />);
      await act(flushMicrotasks);
      expect(screen.getByLabelText('当前安全上下文')).toBeInTheDocument();

      vi.setSystemTime(new Date(expiresAt).getTime() + 1);
      act(() => {
        if (eventName === 'pageshow') window.dispatchEvent(new Event('pageshow'));
        else document.dispatchEvent(new Event('visibilitychange'));
      });
      await act(flushMicrotasks);

      expect(screen.getByText('登录工作空间')).toBeInTheDocument();
      expect(screen.queryByLabelText('当前安全上下文')).not.toBeInTheDocument();
      expect(session.token()).toBeNull();
    },
  );

  it('fails closed on a bootstrap network error, preserves the session, and recovers through retry', async () => {
    localStorage.setItem('aip.access-token', 'token');
    let identityAttempts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/auth/me')) {
        identityAttempts += 1;
        if (identityAttempts === 1) return Promise.reject(new TypeError('network unavailable'));
        return response(platformUser);
      }
      return response(platformSummary);
    });

    render(<App />);

    expect(await screen.findByText('无法验证登录状态')).toBeInTheDocument();
    expect(screen.queryByText('平台任务首页')).not.toBeInTheDocument();
    expect(session.token()).toBe('token');

    await userEvent.click(screen.getByRole('button', { name: '重试' }));

    expect(await screen.findByText('平台任务首页')).toBeInTheDocument();
    expect(identityAttempts).toBe(2);
  });

  it('aborts the bootstrap identity request when the application unmounts', async () => {
    localStorage.setItem('aip.access-token', 'token');
    const identity = deferred<Response>();
    let requestSignal: AbortSignal | null = null;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
      requestSignal = init?.signal as AbortSignal;
      return identity.promise;
    });

    const view = render(<App />);
    await waitFor(() => expect(requestSignal).not.toBeNull());

    view.unmount();

    expect((requestSignal as unknown as AbortSignal).aborted).toBe(true);
    identity.resolve(await response(platformUser));
  });

  it('ignores a post-login feature response after the session expires', async () => {
    const user = userEvent.setup();
    const features = deferred<Response>();
    const featureUser = {
      ...platformUser,
      permissionCodes: [...platformUser.permissionCodes, 'identity-access:iam-config:read'],
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/auth/login')) return response(loginResult(featureUser, 'issued-token'));
      if (path.includes('/auth/me')) return response(featureUser);
      if (path.includes('/iam/tenant-features')) return features.promise;
      return response(platformSummary);
    });

    render(<App />);
    await user.type(await screen.findByLabelText(/租户编码/), '1000');
    await user.type(screen.getByLabelText(/账号/), 'platform-admin');
    await user.type(screen.getByLabelText(/密码/), 'secret');
    await user.click(screen.getByRole('button', { name: '登录' }));
    await waitFor(() => expect(session.token()).toBe('issued-token'));

    act(() => {
      session.clear();
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT));
    });
    expect(await screen.findByText('登录工作空间')).toBeInTheDocument();

    await act(async () => {
      features.resolve(await response([]));
      await Promise.resolve();
    });

    expect(window.location.pathname).toBe('/login');
    expect(screen.getByText('登录工作空间')).toBeInTheDocument();
    expect(screen.queryByText('平台任务首页')).not.toBeInTheDocument();
  });

  it('aborts post-login feature loading when the login view unmounts', async () => {
    const user = userEvent.setup();
    const features = deferred<Response>();
    let featureSignal: AbortSignal | null = null;
    const featureUser = {
      ...platformUser,
      permissionCodes: [...platformUser.permissionCodes, 'identity-access:iam-config:read'],
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const path = String(input);
      if (path.includes('/auth/login')) return response(loginResult(featureUser, 'issued-token'));
      if (path.includes('/auth/me')) return response(featureUser);
      if (path.includes('/iam/tenant-features')) {
        featureSignal = init?.signal as AbortSignal;
        return features.promise;
      }
      return response(platformSummary);
    });

    const view = render(<App />);
    await user.type(await screen.findByLabelText(/租户编码/), '1000');
    await user.type(screen.getByLabelText(/账号/), 'platform-admin');
    await user.type(screen.getByLabelText(/密码/), 'secret');
    await user.click(screen.getByRole('button', { name: '登录' }));
    await waitFor(() => expect(featureSignal).not.toBeNull());

    view.unmount();

    expect((featureSignal as unknown as AbortSignal).aborted).toBe(true);
    await act(async () => {
      features.resolve(await response([]));
      await Promise.resolve();
    });
  });

  it('keeps a newly issued session when an earlier logout response arrives late', async () => {
    const user = userEvent.setup();
    const pendingLogout = deferred<Response>();
    let identityAttempts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/auth/logout')) return pendingLogout.promise;
      if (path.includes('/auth/login')) return response(loginResult(businessUser, 'new-session-token'));
      if (path.includes('/auth/me')) {
        identityAttempts += 1;
        return response(identityAttempts === 1 ? platformUser : businessUser);
      }
      if (path.includes('/ai-applications')) return response([]);
      return response(platformSummary);
    });
    localStorage.setItem('aip.access-token', 'old-session-token');

    render(<App />);
    expect(await screen.findByText('平台任务首页')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '退出登录' }));

    await user.type(await screen.findByLabelText(/租户编码/), '2001');
    await user.type(screen.getByLabelText(/账号/), 'business-user');
    await user.type(screen.getByLabelText(/密码/), 'secret');
    await user.click(screen.getByRole('button', { name: '登录' }));
    expect(await screen.findByText('你好，业务用户')).toBeInTheDocument();

    await act(async () => {
      pendingLogout.resolve(await response(null));
      await Promise.resolve();
    });

    expect(session.token()).toBe('new-session-token');
    expect(screen.getByText('你好，业务用户')).toBeInTheDocument();
  });

  it('preserves a newly issued token when post-login identity loading has a network failure', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (String(input).includes('/auth/login')) return response(loginResult(businessUser, 'issued-token'));
      return Promise.reject(new TypeError('network unavailable'));
    });

    render(<App />);
    await user.type(await screen.findByLabelText(/租户编码/), '2001');
    await user.type(screen.getByLabelText(/账号/), 'business-user');
    await user.type(screen.getByLabelText(/密码/), 'secret');
    await user.click(screen.getByRole('button', { name: '登录' }));

    expect(await screen.findByText('network unavailable')).toBeInTheDocument();
    expect(session.token()).toBe('issued-token');
    expect(screen.getByText('登录工作空间')).toBeInTheDocument();
  });
});
