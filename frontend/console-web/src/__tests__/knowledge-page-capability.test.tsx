import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FluentProvider } from '@fluentui/react-components';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PageRouter } from '../pages';
import { enterpriseLightTheme } from '../theme';
import type { CurrentUser } from '../types';

function envelope(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    header: { code: 'SUCCESS', message: 'success', traceId: 'capability-test' },
    body,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

const sharedKnowledgeMenu = {
  code: 'business.knowledge-bases',
  name: '企业知识',
  platform: 'BUSINESS',
  path: '/workspace/knowledge-bases',
  routeName: 'BusinessKnowledgeBases',
  componentKey: 'business/knowledge-bases',
  permission: 'knowledge-base:knowledge-base:read',
  sort: 20,
};

const businessUser: CurrentUser = {
  tenantId: '2001',
  userId: 'BUSINESS-1',
  loginName: 'business-user',
  displayName: '业务用户',
  roleCodes: ['business-user'],
  permissionCodes: ['knowledge-base:knowledge-base:read'],
  menus: [sharedKnowledgeMenu],
  defaultRoute: '/workspace/knowledge-bases',
  accessTokenExpiresAt: '2099-01-01T00:00:00Z',
};

const tenantAdministrator: CurrentUser = {
  ...businessUser,
  userId: 'TENANT-ADMIN-1',
  loginName: 'tenant-admin',
  displayName: '租户管理员',
  roleCodes: ['tenant-admin'],
  permissionCodes: [
    'knowledge-base:knowledge-base:read',
    'knowledge-base:knowledge-base:create',
    'knowledge-base:document:read',
    'knowledge-base:document:upload',
    'knowledge-base:document:parse',
    'knowledge-base:document:index',
    'knowledge-base:document:cancel',
  ],
  defaultRoute: '/tenant/knowledge-bases',
};

function renderPage(path: string, user: CurrentUser) {
  return render(
    <FluentProvider theme={enterpriseLightTheme}>
      <PageRouter path={path} user={user} navigate={vi.fn()} />
    </FluentProvider>,
  );
}

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.restoreAllMocks();
});

describe('knowledge page role capability boundary', () => {
  it('keeps a business workspace read-only and never requests management options', async () => {
    const requests: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      requests.push(url);
      if (url.endsWith('/api/v1/knowledge-bases')) return envelope([
        { knowledgeBaseId: 'KB-1', name: '员工手册', visibility: 'TENANT', embeddingModelConfigId: 'EMB-1', status: 'ACTIVE' },
      ]);
      throw new Error(`unexpected request: ${url}`);
    });

    renderPage('/workspace/knowledge-bases', businessUser);

    expect(await screen.findByRole('heading', { level: 1, name: '可用知识' })).toBeInTheDocument();
    expect(await screen.findByText('员工手册')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新建知识库' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /管理文档/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '选择并上传' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /重试解析|重建索引|建立索引|取消处理/ })).not.toBeInTheDocument();
    expect(requests).toEqual(['/api/v1/knowledge-bases']);
  });

  it('fails closed before any API request when a business user opens the tenant governance URL directly', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch');

    renderPage('/tenant/knowledge-bases', businessUser);

    expect(await screen.findByText('无权访问')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新建知识库' })).not.toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('keeps every permitted tenant governance action keyboard-addressable', async () => {
    const actor = userEvent.setup();
    const documents = [
      {
        knowledgeBaseId: 'KB-1', documentId: 'DOC-UPLOADED', fileName: '待解析.pdf', status: 'UPLOADED',
        jobId: null, jobType: null, jobStatus: null, failureCode: null, failureSummary: null, traceId: null,
        manualRetryEligible: false, cancellationReason: null, cancelledBy: null, cancellationTraceId: null,
        cancelledAt: null, cancelEligible: false,
      },
      {
        knowledgeBaseId: 'KB-1', documentId: 'DOC-CHUNKED', fileName: '待索引.pdf', status: 'CHUNKED',
        jobId: null, jobType: null, jobStatus: null, failureCode: null, failureSummary: null, traceId: null,
        manualRetryEligible: false, cancellationReason: null, cancelledBy: null, cancellationTraceId: null,
        cancelledAt: null, cancelEligible: false,
      },
      {
        knowledgeBaseId: 'KB-1', documentId: 'DOC-FAILED', fileName: '解析失败.pdf', status: 'FAILED',
        jobId: 'JOB-FAILED', jobType: 'DOCUMENT_PARSE', jobStatus: 'FAILED', failureCode: 'AIP-KB-PARSE',
        failureSummary: '解析失败', traceId: 'trace-failed', manualRetryEligible: true,
        cancellationReason: null, cancelledBy: null, cancellationTraceId: null, cancelledAt: null, cancelEligible: false,
      },
      {
        knowledgeBaseId: 'KB-1', documentId: 'DOC-PARSING', fileName: '处理中.pdf', status: 'PARSING',
        jobId: 'JOB-PARSING', jobType: 'DOCUMENT_PARSE', jobStatus: 'PARSING', failureCode: null,
        failureSummary: null, traceId: 'trace-parsing', manualRetryEligible: false,
        cancellationReason: null, cancelledBy: null, cancellationTraceId: null, cancelledAt: null, cancelEligible: true,
      },
    ];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/model-providers/embedding-options')) return envelope([
        { modelConfigId: 'EMB-1', label: '企业向量模型' },
      ]);
      if (url.endsWith('/api/v1/knowledge-bases')) return envelope([
        { knowledgeBaseId: 'KB-1', name: '员工手册', visibility: 'TENANT', embeddingModelConfigId: 'EMB-1', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/knowledge-bases/KB-1/documents')) return envelope(documents);
      throw new Error(`unexpected request: ${url}`);
    });

    renderPage('/tenant/knowledge-bases', tenantAdministrator);

    expect(await screen.findByRole('button', { name: '新建知识库' })).toBeEnabled();
    const manage = screen.getByRole('button', { name: '管理文档 KB-1' });
    manage.focus();
    await actor.keyboard('{Enter}');

    const dialog = await screen.findByRole('dialog', { name: '文档维护' });
    expect(within(dialog).getByRole('button', { name: '选择并上传' })).toBeEnabled();
    expect(within(dialog).getByRole('button', { name: '解析' })).toBeEnabled();
    expect(within(dialog).getByRole('button', { name: '建立索引' })).toBeEnabled();
    expect(within(dialog).getByRole('button', { name: '重试解析 解析失败.pdf' })).toBeEnabled();
    expect(within(dialog).getByRole('button', { name: '取消处理 处理中.pdf' })).toBeEnabled();
  });
});
