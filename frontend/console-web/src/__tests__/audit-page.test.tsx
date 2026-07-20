import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FluentProvider } from '@fluentui/react-components';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuditPage } from '../overview-pages';
import { enterpriseLightTheme } from '../theme';

interface AuditRow {
  id: string;
  tenantId: string;
  actorId: string;
  eventType: string;
  riskLevel: string;
  subjectType: string;
  subjectId?: string | null;
  subjectRef?: string | null;
  resultStatus: string;
  detailJson: string;
  traceId: string;
  createdAt: string;
}

function row(id: string, eventType = 'LOGIN_FAILED'): AuditRow {
  return {
    id,
    tenantId: '2001',
    actorId: '1002',
    eventType,
    riskLevel: 'HIGH',
    subjectType: 'IAM_USER',
    subjectId: '3002',
    subjectRef: '3002',
    resultStatus: 'FAILED',
    detailJson: JSON.stringify({ reason: 'invalid credentials' }),
    traceId: `trace-${id}`,
    createdAt: '2026-07-18T08:00:00Z',
  };
}

function page(items: AuditRow[], pageNumber = 1, pageSize = 20, total = items.length) {
  return {
    items,
    page: pageNumber,
    pageSize,
    total,
    totalPages: total === 0 ? 0 : Math.ceil(total / pageSize),
  };
}

function response(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    header: { code: 'SUCCESS', message: 'success', traceId: 'transport-trace' },
    body,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

function renderPage() {
  return render(<FluentProvider theme={enterpriseLightTheme}><AuditPage /></FluentProvider>);
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('security audit database pagination and filters', () => {
  it('sends explicit filters, keeps them across server pages, changes page size, and resets', async () => {
    const requests: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const requestUrl = String(input);
      requests.push(requestUrl);
      const query = new URL(requestUrl, 'http://console.local').searchParams;
      return response(page([row(`page-${query.get('page') || '1'}`)], Number(query.get('page')), Number(query.get('pageSize')), 45));
    });
    renderPage();

    expect(await screen.findByRole('button', { name: '查看审计 page-1' })).toBeInTheDocument();
    expect(new URL(requests[0], 'http://console.local').searchParams.get('pageSize')).toBe('20');

    await userEvent.type(screen.getByLabelText('操作人编号'), '1002');
    await userEvent.type(screen.getByLabelText('事件类型'), 'login_failed');
    await userEvent.type(screen.getByLabelText('对象类型'), 'iam_user');
    await userEvent.type(screen.getByLabelText('对象编号或引用'), '3002');
    await userEvent.type(screen.getByLabelText('追踪编号'), 'trace-filter');
    await userEvent.selectOptions(screen.getByLabelText('风险等级'), 'HIGH');
    await userEvent.selectOptions(screen.getByLabelText('结果状态'), 'FAILED');
    fireEvent.change(screen.getByLabelText('开始时间'), { target: { value: '2026-07-18T08:00' } });
    fireEvent.change(screen.getByLabelText('结束时间'), { target: { value: '2026-07-18T10:00' } });
    await userEvent.click(screen.getByRole('button', { name: '查询' }));

    await waitFor(() => expect(requests).toHaveLength(2));
    const filtered = new URL(requests[1], 'http://console.local').searchParams;
    expect(Object.fromEntries(filtered)).toMatchObject({
      page: '1', pageSize: '20', actorId: '1002', eventType: 'login_failed', subjectType: 'iam_user',
      subjectId: '3002', traceId: 'trace-filter', riskLevel: 'HIGH', resultStatus: 'FAILED',
    });
    expect(filtered.get('createdFrom')).toBe(new Date('2026-07-18T08:00').toISOString());
    expect(filtered.get('createdTo')).toBe(new Date('2026-07-18T10:00').toISOString());

    await userEvent.click(screen.getByRole('button', { name: '下一页' }));
    await waitFor(() => expect(requests).toHaveLength(3));
    const nextPage = new URL(requests[2], 'http://console.local').searchParams;
    expect(nextPage.get('page')).toBe('2');
    expect(nextPage.get('actorId')).toBe('1002');

    await userEvent.selectOptions(screen.getByLabelText('每页条数'), '50');
    await waitFor(() => expect(requests).toHaveLength(4));
    const resized = new URL(requests[3], 'http://console.local').searchParams;
    expect(resized.get('page')).toBe('1');
    expect(resized.get('pageSize')).toBe('50');
    expect(resized.get('actorId')).toBe('1002');

    await userEvent.click(screen.getByRole('button', { name: '重置筛选' }));
    await waitFor(() => expect(requests).toHaveLength(5));
    const reset = new URL(requests[4], 'http://console.local').searchParams;
    expect(reset.get('page')).toBe('1');
    expect(reset.get('pageSize')).toBe('50');
    expect(reset.has('actorId')).toBe(false);
    expect(screen.getByLabelText('操作人编号')).toHaveValue('');
  });

  it('ignores an older response after a newer filtered request has completed', async () => {
    let resolveFirst!: (value: Response) => void;
    let resolveSecond!: (value: Response) => void;
    const first = new Promise<Response>((resolve) => { resolveFirst = resolve; });
    const second = new Promise<Response>((resolve) => { resolveSecond = resolve; });
    vi.spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() => first)
      .mockImplementationOnce(() => second);
    renderPage();

    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledTimes(1));
    expect(screen.getByText('正在读取安全审计')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('操作人编号'), '1002');
    await userEvent.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledTimes(2));

    await act(async () => resolveSecond(await response(page([row('latest', 'LATEST_EVENT')], 1, 20, 1))));
    expect(await screen.findByText('LATEST_EVENT')).toBeInTheDocument();
    await act(async () => resolveFirst(await response(page([row('stale', 'STALE_EVENT')], 1, 20, 1))));
    await act(async () => Promise.resolve());

    expect(screen.queryByText('STALE_EVENT')).not.toBeInTheDocument();
    expect(screen.getByText('LATEST_EVENT')).toBeInTheDocument();
  });

  it('fails closed, retries, and validates the local time range before requesting', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new TypeError('audit service unavailable'))
      .mockImplementationOnce(() => response(page([row('recovered')], 1, 20, 1)));
    renderPage();

    expect(await screen.findByText('audit service unavailable')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: '安全审计列表' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByRole('button', { name: '查看审计 recovered' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('开始时间'), { target: { value: '2026-07-18T10:00' } });
    fireEvent.change(screen.getByLabelText('结束时间'), { target: { value: '2026-07-18T08:00' } });
    await userEvent.click(screen.getByRole('button', { name: '查询' }));

    expect(await screen.findByText('开始时间不能晚于结束时间。')).toBeInTheDocument();
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });

  it('distinguishes an empty tenant from an empty filtered result', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response(page([], 1, 20, 0)));
    renderPage();

    expect(await screen.findByRole('heading', { name: '暂无审计记录' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('事件类型'), 'permission_denied');
    await userEvent.click(screen.getByRole('button', { name: '查询' }));

    expect(await screen.findByRole('heading', { name: '没有符合条件的记录' })).toBeInTheDocument();
    expect(screen.getByText('请调整筛选条件后重新查询。')).toBeInTheDocument();
  });

  it('renders only the audit detail disclosure allowlist', async () => {
    const unsafe = {
      ...row('sensitive'),
      detailJson: JSON.stringify({
        reason: 'invalid credentials',
        action: 'LOGIN_FAILED',
        token: 'bearer-secret',
        password: 'plain-secret',
        prompt: 'private question',
        unknown: 'internal-only',
      }),
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response(page([unsafe])));
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '查看审计 sensitive' }));

    expect(screen.getByText('invalid credentials')).toBeInTheDocument();
    expect(screen.getAllByText('LOGIN_FAILED').length).toBeGreaterThan(0);
    expect(screen.queryByText(/bearer-secret|plain-secret|private question|internal-only/)).not.toBeInTheDocument();
    expect(screen.queryByText(/token|password|prompt|unknown/i)).not.toBeInTheDocument();
  });

  it('displays and filters by an opaque subject reference without a numeric input hint', async () => {
    const subjectRef = '90323c3e-569a-4f13-ab3a-a5a115edc2b5';
    const requests: string[] = [];
    const opaque = {
      ...row('opaque-subject', 'PERMISSION_REQUEST_DECIDED'),
      subjectType: 'IAM_PERMISSION_REQUEST',
      subjectId: null,
      subjectRef,
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      requests.push(String(input));
      return response(page([opaque]));
    });
    renderPage();

    const viewButton = await screen.findByRole('button', { name: '查看审计 opaque-subject' });
    expect(viewButton.closest('tr')).toHaveTextContent(subjectRef);
    const subjectFilter = screen.getByLabelText('对象编号或引用');
    expect(subjectFilter).not.toHaveAttribute('inputmode', 'numeric');
    await userEvent.type(subjectFilter, subjectRef);
    await userEvent.click(screen.getByRole('button', { name: '查询' }));

    await waitFor(() => expect(requests).toHaveLength(2));
    expect(new URL(requests[1], 'http://console.local').searchParams.get('subjectId')).toBe(subjectRef);
    await userEvent.click(await screen.findByRole('button', { name: '查看审计 opaque-subject' }));
    expect(screen.getByLabelText('审计详情')).toHaveTextContent(subjectRef);
  });
});
