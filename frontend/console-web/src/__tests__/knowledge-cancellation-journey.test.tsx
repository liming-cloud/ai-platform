import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
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
    header: { code: 'SUCCESS', message: 'success', traceId: 'trace-success' },
    body,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

function cancellationFailure() {
  return Promise.resolve(new Response(JSON.stringify({
    header: {
      code: 'AIP-KB-DOCUMENT-CANCEL-CONFLICT',
      message: '文档作业状态已变化，取消未生效',
      traceId: 'trace-cancel-conflict',
    },
    body: null,
  }), { status: 409, headers: { 'Content-Type': 'application/json' } }));
}

const activeDocument = {
  knowledgeBaseId: 'KB-1',
  documentId: 'DOC-21',
  fileName: '运行手册.pdf',
  status: 'PARSING',
  jobId: 'JOB-21',
  jobType: 'DOCUMENT_PARSE',
  jobStatus: 'PARSING',
  failureCode: null,
  failureSummary: null,
  traceId: 'trace-job-21',
  manualRetryEligible: false,
  cancellationReason: null,
  cancelledBy: null,
  cancellationTraceId: null,
  cancelledAt: null,
  cancelEligible: true,
};

const cancelledDocument = {
  ...activeDocument,
  status: 'CANCELLED',
  jobStatus: 'CANCELLED',
  cancellationReason: '内容已由新版替代',
  cancelledBy: 'USER-9',
  cancellationTraceId: 'trace-cancel-21',
  cancelledAt: '2026-07-19T02:03:04Z',
  cancelEligible: false,
};

function renderPage() {
  return render(
    <FluentProvider theme={enterpriseLightTheme}>
      <KnowledgeBasePage {...managementAccess} />
    </FluentProvider>,
  );
}

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.restoreAllMocks();
});

describe('knowledge document cancellation journey', () => {
  it('freezes the selected job, requires a reason, submits once, refreshes, and exposes durable evidence', async () => {
    const actor = userEvent.setup();
    let reads = 0;
    let cancelPosts = 0;
    let postedBody = '';
    let releaseCancel!: (response: Response) => void;
    const pendingCancel = new Promise<Response>((resolve) => { releaseCancel = resolve; });
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/model-providers/embedding-options')) return envelope([]);
      if (url.endsWith('/api/v1/knowledge-bases')) return envelope([
        { knowledgeBaseId: 'KB-1', name: '产品知识库', visibility: 'PRIVATE', status: 'ACTIVE' },
      ]);
      if ((!init?.method || init.method === 'GET') && url.endsWith('/knowledge-bases/KB-1/documents')) {
        reads += 1;
        return envelope([reads === 1 ? activeDocument : cancelledDocument]);
      }
      if (init?.method === 'POST' && url.endsWith('/knowledge-bases/KB-1/documents/DOC-21/cancel')) {
        cancelPosts += 1;
        postedBody = String(init.body);
        return pendingCancel;
      }
      return envelope([]);
    });

    renderPage();
    await actor.click(await screen.findByRole('button', { name: '管理文档 KB-1' }));
    const trigger = await screen.findByRole('button', { name: '取消处理 运行手册.pdf' });
    trigger.focus();
    await actor.keyboard('{Enter}');

    expect(screen.getByRole('dialog', { name: '确认取消文档处理' })).toBeInTheDocument();
    expect(screen.getByText('作业 JOB-21 · DOCUMENT_PARSE · PARSING')).toBeInTheDocument();
    expect(screen.getByText('Trace trace-job-21')).toBeInTheDocument();
    const confirm = screen.getByRole('button', { name: '确认取消处理' });
    expect(confirm).toBeDisabled();

    fireEvent.change(screen.getByLabelText('取消理由'), { target: { value: '  内容已由新版替代  ' } });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(cancelPosts).toBe(1);
    expect(postedBody).toBe(JSON.stringify({ reason: '内容已由新版替代' }));

    releaseCancel(await envelope(cancelledDocument));

    expect(await screen.findByText('已取消“运行手册.pdf”的当前处理作业。')).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: '确认取消文档处理' })).not.toBeInTheDocument();
    await waitFor(() => expect(reads).toBe(2));
    expect(screen.getByText('已取消')).toBeInTheDocument();
    expect(screen.getByText('原因：内容已由新版替代')).toBeInTheDocument();
    expect(screen.getByText('操作人：USER-9')).toBeInTheDocument();
    expect(screen.getByText('Trace trace-cancel-21')).toBeInTheDocument();
    const documentCell = screen.getByText('运行手册.pdf').closest('td');
    await waitFor(() => expect(documentCell).toHaveFocus());
  });

  it('keeps the frozen target and entered reason in the dialog after a traced conflict', async () => {
    const actor = userEvent.setup();
    let cancelPosts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/model-providers/embedding-options')) return envelope([]);
      if (url.endsWith('/api/v1/knowledge-bases')) return envelope([
        { knowledgeBaseId: 'KB-1', name: '产品知识库', visibility: 'PRIVATE', status: 'ACTIVE' },
      ]);
      if ((!init?.method || init.method === 'GET') && url.endsWith('/knowledge-bases/KB-1/documents')) {
        return envelope([activeDocument]);
      }
      if (init?.method === 'POST' && url.endsWith('/knowledge-bases/KB-1/documents/DOC-21/cancel')) {
        cancelPosts += 1;
        return cancellationFailure();
      }
      return envelope([]);
    });

    renderPage();
    await actor.click(await screen.findByRole('button', { name: '管理文档 KB-1' }));
    await actor.click(await screen.findByRole('button', { name: '取消处理 运行手册.pdf' }));
    fireEvent.change(screen.getByLabelText('取消理由'), { target: { value: '内容已由新版替代' } });
    await actor.click(screen.getByRole('button', { name: '确认取消处理' }));

    expect(await screen.findByText('文档作业状态已变化，取消未生效', { exact: false })).toBeInTheDocument();
    expect(screen.getByText(/Trace trace-cancel-conflict/)).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: '确认取消文档处理' })).toBeInTheDocument();
    expect(screen.getByLabelText('取消理由')).toHaveValue('内容已由新版替代');
    expect(screen.getByText('作业 JOB-21 · DOCUMENT_PARSE · PARSING')).toBeInTheDocument();
    expect(cancelPosts).toBe(1);
  });

  it('returns keyboard focus without a request when the operator backs out', async () => {
    const actor = userEvent.setup();
    let cancelPosts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/model-providers/embedding-options')) return envelope([]);
      if (url.endsWith('/api/v1/knowledge-bases')) return envelope([
        { knowledgeBaseId: 'KB-1', name: '产品知识库', visibility: 'PRIVATE', status: 'ACTIVE' },
      ]);
      if ((!init?.method || init.method === 'GET') && url.endsWith('/knowledge-bases/KB-1/documents')) {
        return envelope([activeDocument]);
      }
      if (init?.method === 'POST') cancelPosts += 1;
      return envelope([]);
    });

    renderPage();
    await actor.click(await screen.findByRole('button', { name: '管理文档 KB-1' }));
    const trigger = await screen.findByRole('button', { name: '取消处理 运行手册.pdf' });
    trigger.focus();
    await actor.keyboard('{Enter}');
    await actor.keyboard('{Escape}');

    await waitFor(() => expect(trigger).toHaveFocus());
    expect(cancelPosts).toBe(0);
  });
});
