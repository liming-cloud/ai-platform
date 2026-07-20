import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FluentProvider } from '@fluentui/react-components';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApplicationCatalogPage, WorkspaceHome } from '../workspace-pages';
import { enterpriseLightTheme } from '../theme';
import type { CurrentUser } from '../types';

const businessUser: CurrentUser = {
  tenantId: '2001', userId: '8', loginName: 'business-user', displayName: '业务用户',
  roleCodes: ['BUSINESS_USER'], permissionCodes: ['app-studio:application:read'], menus: [],
  defaultRoute: '/workspace/home', accessTokenExpiresAt: '2099-01-01T00:00:00Z',
};

const applications = [
  {
    applicationId: 'APP-1', name: '知识问答助手', description: '检索当前账号可访问的制度知识。',
    applicationType: 'RAG_CHAT', status: 'PUBLISHED',
  },
  {
    applicationId: 'APP-2', name: '工单摘要助手', description: '整理授权范围内的工单内容。',
    applicationType: 'ASSISTANT', status: 'PUBLISHED',
  },
];

function response(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    header: { code: 'SUCCESS', message: 'success', traceId: 'transport-trace' },
    body,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
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

function renderHome(navigate = vi.fn()) {
  return {
    navigate,
    ...render(<FluentProvider theme={enterpriseLightTheme}><WorkspaceHome user={businessUser} navigate={navigate} /></FluentProvider>),
  };
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('business task home', () => {
  it('loads the real authorized application list and starts work directly', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = String(input);
      if (path.includes('/chat-sessions')) return response({ sessionId: 'SESSION-9' });
      return response(applications);
    });
    const { navigate } = renderHome();

    expect(await screen.findByText('知识问答助手')).toBeInTheDocument();
    expect(screen.getByText('工单摘要助手')).toBeInTheDocument();
    expect(screen.getByText(/读取时间/)).toBeInTheDocument();
    const application = screen.getByText('知识问答助手').closest('article');
    expect(application).not.toBeNull();
    expect(within(application!).getByText('PUBLISHED')).toBeInTheDocument();

    await userEvent.click(within(application!).getByRole('button', { name: '使用 知识问答助手' }));

    expect(navigate).toHaveBeenCalledWith('/workspace/sessions/SESSION-9');
  });

  it('locks only the selected home application and drops a late create result after route exit', async () => {
    const lateCreate = deferred<Response>();
    let createSignal: AbortSignal | null | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      if (String(input).includes('/chat-sessions')) {
        createSignal = init?.signal;
        return lateCreate.promise;
      }
      return response(applications);
    });
    const view = renderHome();

    const firstApplication = (await screen.findByText('知识问答助手')).closest('article');
    const secondApplication = screen.getByText('工单摘要助手').closest('article');
    expect(firstApplication).not.toBeNull();
    expect(secondApplication).not.toBeNull();
    await userEvent.click(within(firstApplication!).getByRole('button', { name: '使用 知识问答助手' }));

    expect(within(firstApplication!).getByRole('button', { name: '使用 知识问答助手' })).toBeDisabled();
    expect(within(secondApplication!).getByRole('button', { name: '使用 工单摘要助手' })).toBeEnabled();
    expect(createSignal).toBeInstanceOf(AbortSignal);
    view.unmount();
    expect(createSignal?.aborted).toBe(true);
    await act(async () => {
      lateCreate.resolve(await response({ sessionId: 'SESSION-LATE' }));
    });

    await waitFor(() => expect(view.navigate).not.toHaveBeenCalled());
  });

  it('keeps the work surface closed on load failure and recovers through retry', async () => {
    let attempts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => {
      attempts += 1;
      return attempts === 1 ? Promise.reject(new TypeError('network unavailable')) : response(applications);
    });
    renderHome();

    expect(await screen.findByText('network unavailable')).toBeInTheDocument();
    expect(screen.queryByText('知识问答助手')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '重试' }));

    expect(await screen.findByText('知识问答助手')).toBeInTheDocument();
    expect(attempts).toBe(2);
  });

  it('does not invent an application type when the service omitted it', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response([
      { applicationId: 'APP-3', name: '合同助手', status: 'PUBLISHED' },
    ]));

    render(<FluentProvider theme={enterpriseLightTheme}><ApplicationCatalogPage navigate={vi.fn()} /></FluentProvider>);

    const typeLabel = await screen.findByText('类型未提供');
    expect(typeLabel).toHaveClass('surface-metadata');
    expect(screen.queryByText('AI APPLICATION')).not.toBeInTheDocument();
  });

  it('locks only the selected catalog application and drops its late create result after unmount', async () => {
    const lateCreate = deferred<Response>();
    let createSignal: AbortSignal | null | undefined;
    const navigate = vi.fn();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      if (String(input).includes('/chat-sessions')) {
        createSignal = init?.signal;
        return lateCreate.promise;
      }
      return response(applications);
    });
    const view = render(<FluentProvider theme={enterpriseLightTheme}><ApplicationCatalogPage navigate={navigate} /></FluentProvider>);

    const firstApplication = (await screen.findByText('知识问答助手')).closest('article');
    const secondApplication = screen.getByText('工单摘要助手').closest('article');
    expect(firstApplication).not.toBeNull();
    expect(secondApplication).not.toBeNull();
    await userEvent.click(within(firstApplication!).getByRole('button', { name: '开始使用' }));

    expect(within(firstApplication!).getByRole('button', { name: '正在创建会话' })).toBeDisabled();
    expect(within(secondApplication!).getByRole('button', { name: '开始使用' })).toBeEnabled();
    expect(createSignal).toBeInstanceOf(AbortSignal);
    view.unmount();
    expect(createSignal?.aborted).toBe(true);
    await act(async () => {
      lateCreate.resolve(await response({ sessionId: 'SESSION-CATALOG-LATE' }));
    });

    await waitFor(() => expect(navigate).not.toHaveBeenCalled());
  });

  it('keeps the application catalog closed on load failure and recovers through retry', async () => {
    let attempts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => {
      attempts += 1;
      return attempts === 1 ? Promise.reject(new TypeError('catalog unavailable')) : response(applications);
    });

    render(<FluentProvider theme={enterpriseLightTheme}><ApplicationCatalogPage navigate={vi.fn()} /></FluentProvider>);

    expect(await screen.findByText('catalog unavailable')).toBeInTheDocument();
    expect(screen.queryByText('知识问答助手')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '重试' }));

    expect(await screen.findByText('知识问答助手')).toBeInTheDocument();
    expect(attempts).toBe(2);
  });
});
