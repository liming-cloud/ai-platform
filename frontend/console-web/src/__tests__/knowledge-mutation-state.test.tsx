import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FluentProvider } from '@fluentui/react-components';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { KnowledgeBasePage } from '../knowledge-pages';
import { enterpriseLightTheme } from '../theme';

const managementAccess = {
  capability: 'knowledge-base:manage' as const,
  permissionCodes: [
    'knowledge-base:knowledge-base:create',
    'knowledge-base:document:read',
    'knowledge-base:document:upload',
    'knowledge-base:document:parse',
    'knowledge-base:document:index',
    'knowledge-base:document:cancel',
  ],
};

function envelope(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    header: { code: 'SUCCESS', message: 'success', traceId: 'test-trace' },
    body,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

function failure(message: string) {
  return Promise.resolve(new Response(JSON.stringify({
    header: { code: 'AIP-KB-ACTION-5031', message, traceId: 'trace-action' },
    body: null,
  }), { status: 503, headers: { 'Content-Type': 'application/json' } }));
}

interface MutationCase {
  action: 'upload' | 'parse' | 'index';
  documentStatus: 'UPLOADED' | 'CHUNKED';
  failureMessage: string;
  retryLabel: string;
}

async function expectMutationRecovery(testCase: MutationCase) {
  const actor = userEvent.setup();
  let actionAttempts = 0;
  const actionUrls: string[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
    const url = String(input);
    if (url.endsWith('/model-providers/embedding-options')) return envelope([]);
    if (url.endsWith('/api/v1/knowledge-bases')) return envelope([
      { knowledgeBaseId: 'KB-1', name: '产品知识库', visibility: 'PRIVATE', status: 'ACTIVE' },
    ]);
    if ((!init?.method || init.method === 'GET') && url.endsWith('/knowledge-bases/KB-1/documents')) {
      return envelope([
        {
          documentId: 'D-1',
          fileName: '合同规范.pdf',
          status: testCase.documentStatus,
          jobId: null,
          jobType: null,
          jobStatus: null,
          failureCode: null,
          failureSummary: null,
          traceId: null,
          manualRetryEligible: false,
          cancellationReason: null,
          cancelledBy: null,
          cancellationTraceId: null,
          cancelledAt: null,
          cancelEligible: false,
        },
      ]);
    }
    if (init?.method === 'POST') {
      actionAttempts += 1;
      actionUrls.push(url);
      return actionAttempts === 1 ? failure(testCase.failureMessage) : envelope({ status: 'ACCEPTED' });
    }
    return envelope([]);
  });

  render(<FluentProvider theme={enterpriseLightTheme}><KnowledgeBasePage {...managementAccess} /></FluentProvider>);
  await actor.click(await screen.findByRole('button', { name: '管理文档 KB-1' }));
  expect(await screen.findByText('合同规范.pdf')).toBeInTheDocument();

  if (testCase.action === 'upload') {
    const uploadInput = document.querySelector<HTMLInputElement>('input[type="file"]');
    expect(uploadInput).not.toBeNull();
    await actor.upload(uploadInput!, new File(['contract'], '新版合同.pdf', { type: 'application/pdf' }));
  } else {
    await actor.click(screen.getByRole('button', { name: testCase.action === 'parse' ? '解析' : '建立索引' }));
  }

  expect(await screen.findByText(testCase.failureMessage, { exact: false })).toBeInTheDocument();
  expect(screen.getByRole('table', { name: '知识库文档列表' })).toBeInTheDocument();
  expect(screen.getByText('合同规范.pdf')).toBeInTheDocument();
  expect(screen.getByText(/trace-action/)).toBeInTheDocument();
  expect(actionAttempts).toBe(1);

  await actor.click(screen.getByRole('button', { name: testCase.retryLabel }));

  await waitFor(() => expect(actionAttempts).toBe(2));
  await waitFor(() => expect(screen.queryByText(testCase.failureMessage, { exact: false })).not.toBeInTheDocument());
  expect(screen.getByText('合同规范.pdf')).toBeInTheDocument();
  const expectedActionUrl = testCase.action === 'upload'
    ? '/api/v1/knowledge-bases/KB-1/documents'
    : `/api/v1/knowledge-bases/KB-1/documents/D-1/${testCase.action}`;
  expect(actionUrls).toEqual([
    expectedActionUrl,
    expectedActionUrl,
  ]);
}

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.restoreAllMocks();
});

describe('knowledge document mutation recovery', () => {
  it('preserves the trusted document list and retries a failed upload', async () => {
    await expectMutationRecovery({
      action: 'upload',
      documentStatus: 'UPLOADED',
      failureMessage: '文档上传暂时失败',
      retryLabel: '重试上传',
    });
  });

  it('preserves the trusted document list and retries a failed parse', async () => {
    await expectMutationRecovery({
      action: 'parse',
      documentStatus: 'UPLOADED',
      failureMessage: '文档解析暂时失败',
      retryLabel: '重试解析',
    });
  });

  it('preserves the trusted document list and retries a failed index build', async () => {
    await expectMutationRecovery({
      action: 'index',
      documentStatus: 'CHUNKED',
      failureMessage: '文档索引暂时失败',
      retryLabel: '重试建立索引',
    });
  });
});
