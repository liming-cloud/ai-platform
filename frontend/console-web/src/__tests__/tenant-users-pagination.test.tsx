import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FluentProvider } from '@fluentui/react-components';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TenantUsersPage } from '../identity-pages';
import { enterpriseLightTheme } from '../theme';

function envelope(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify({
    header: {
      code: status < 400 ? 'SUCCESS' : 'AIP-IAM-USER-LIST-FAILED',
      message: status < 400 ? 'success' : '用户目录暂时不可用',
      traceId: 'trace-user-page',
    },
    body,
  }), { status, headers: { 'Content-Type': 'application/json' } }));
}

function user(userId: string, displayName: string) {
  return {
    userId,
    username: `user-${userId}`,
    displayName,
    email: `user-${userId}@example.com`,
    roleCodes: ['business-user'],
    status: 'ACTIVE',
  };
}

function page(items: ReturnType<typeof user>[], pageNumber: number, pageSize: number, total: number) {
  return {
    items,
    page: pageNumber,
    pageSize,
    total,
    totalPages: total === 0 ? 0 : Math.ceil(total / pageSize),
  };
}

function renderPage() {
  return render(<FluentProvider theme={enterpriseLightTheme}><TenantUsersPage /></FluentProvider>);
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('tenant user database pagination', () => {
  it('requests explicit server pages and page sizes and renders server totals', async () => {
    const actor = userEvent.setup();
    const userRequests: URL[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), 'http://console.local');
      if (url.pathname.endsWith('/iam/roles')) return envelope([
        { roleId: 'R-1', code: 'business-user', name: '业务用户', type: 'business', status: 'ACTIVE' },
      ]);
      if (url.pathname.endsWith('/iam/user-invitations')) return envelope([]);
      if (url.pathname.endsWith('/iam/users')) {
        userRequests.push(url);
        const requestedPage = Number(url.searchParams.get('page'));
        const pageSize = Number(url.searchParams.get('pageSize'));
        return envelope(page(
          [user(String(requestedPage), `服务端第 ${requestedPage} 页用户`)],
          requestedPage,
          pageSize,
          25,
        ));
      }
      throw new Error(`unexpected request: ${url.pathname}`);
    });
    renderPage();

    expect(await screen.findByText('服务端第 1 页用户')).toBeInTheDocument();
    expect(userRequests[0].searchParams.get('page')).toBe('1');
    expect(userRequests[0].searchParams.get('pageSize')).toBe('10');
    expect(screen.getByText(/共 25 条/)).toBeInTheDocument();

    await actor.click(screen.getByRole('button', { name: '下一页' }));
    expect(await screen.findByText('服务端第 2 页用户')).toBeInTheDocument();
    expect(userRequests[1].searchParams.get('page')).toBe('2');

    await actor.selectOptions(screen.getByLabelText('每页条数'), '20');
    await waitFor(() => expect(userRequests).toHaveLength(3));
    expect(userRequests[2].searchParams.get('page')).toBe('1');
    expect(userRequests[2].searchParams.get('pageSize')).toBe('20');
    expect(await screen.findByText(/第 1 \/ 2 页/)).toBeInTheDocument();
  });

  it('aborts and ignores an older page response after a newer page-size request wins', async () => {
    const actor = userEvent.setup();
    let resolveOldPage!: (response: Response) => void;
    const oldPage = new Promise<Response>((resolve) => { resolveOldPage = resolve; });
    let oldSignal: AbortSignal | null = null;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), 'http://console.local');
      if (url.pathname.endsWith('/iam/roles') || url.pathname.endsWith('/iam/user-invitations')) return envelope([]);
      if (!url.pathname.endsWith('/iam/users')) throw new Error(`unexpected request: ${url.pathname}`);
      const requestedPage = Number(url.searchParams.get('page'));
      const pageSize = Number(url.searchParams.get('pageSize'));
      if (requestedPage === 2 && pageSize === 10) {
        oldSignal = init?.signal ?? null;
        return oldPage;
      }
      const displayName = pageSize === 20 ? '当前每页 20 条结果' : '初始结果';
      return envelope(page([user(`${requestedPage}-${pageSize}`, displayName)], requestedPage, pageSize, 30));
    });
    renderPage();

    expect(await screen.findByText('初始结果')).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '下一页' }));
    expect(screen.getByRole('table', { name: '租户用户列表' })).toHaveAttribute('aria-busy', 'true');
    await actor.selectOptions(screen.getByLabelText('每页条数'), '20');

    expect(await screen.findByText('当前每页 20 条结果')).toBeInTheDocument();
    expect((oldSignal as AbortSignal | null)?.aborted).toBe(true);
    resolveOldPage(await envelope(page([user('stale', '过期第 2 页结果')], 2, 10, 30)));
    await waitFor(() => expect(screen.queryByText('过期第 2 页结果')).not.toBeInTheDocument());
  });

  it('accepts a server-corrected last page after the directory shrinks and retries failures', async () => {
    const actor = userEvent.setup();
    let userCalls = 0;
    const requestedPages: number[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), 'http://console.local');
      if (url.pathname.endsWith('/iam/roles') || url.pathname.endsWith('/iam/user-invitations')) return envelope([]);
      if (!url.pathname.endsWith('/iam/users')) throw new Error(`unexpected request: ${url.pathname}`);
      userCalls += 1;
      requestedPages.push(Number(url.searchParams.get('page')));
      if (userCalls === 1) return envelope(null, 503);
      if (userCalls === 2) return envelope(page([user('recovered', '恢复后的用户')], 1, 10, 11));
      return envelope(page([user('last', '纠正后的末页用户')], 1, 10, 10));
    });
    renderPage();

    expect(await screen.findByText('用户目录暂时不可用')).toBeInTheDocument();
    expect(screen.getByText('错误追踪：trace-user-page')).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '重试' }));

    expect(await screen.findByText('恢复后的用户')).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '下一页' }));

    expect(await screen.findByText('纠正后的末页用户')).toBeInTheDocument();
    expect(screen.getByText(/第 1 \/ 1 页/)).toBeInTheDocument();
    expect(requestedPages).toContain(2);
    expect(userCalls).toBeGreaterThanOrEqual(3);
  });
});
