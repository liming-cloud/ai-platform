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

function failure(message: string) {
  return Promise.resolve(new Response(JSON.stringify({
    header: { code: 'AIP-KB-RECOVERY-5031', message, traceId: 'trace-recovery' },
    body: null,
  }), { status: 503, headers: { 'Content-Type': 'application/json' } }));
}

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

describe('knowledge document manual recovery journey', () => {
  it('opens from the keyboard and returns focus to the failed document after cancellation', async () => {
    const actor = userEvent.setup();
    let retryPosts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/model-providers/embedding-options')) return envelope([]);
      if (url.endsWith('/api/v1/knowledge-bases')) return envelope([
        { knowledgeBaseId: 'KB-1', name: '产品知识库', visibility: 'PRIVATE', status: 'ACTIVE' },
      ]);
      if ((!init?.method || init.method === 'GET') && url.endsWith('/knowledge-bases/KB-1/documents')) {
        return envelope([{
          knowledgeBaseId: 'KB-1',
          documentId: 'D-FAILED',
          fileName: '合同规范.pdf',
          status: 'FAILED',
          jobId: 'JOB-8',
          jobType: 'DOCUMENT_PARSE',
          jobStatus: 'FAILED',
          failureCode: 'AIP-KB-PARSE-4001',
          failureSummary: '文件结构损坏',
          traceId: 'trace-job-8',
          manualRetryEligible: true,
          cancellationReason: null,
          cancelledBy: null,
          cancellationTraceId: null,
          cancelledAt: null,
          cancelEligible: false,
        }]);
      }
      if (init?.method === 'POST') retryPosts += 1;
      return envelope([]);
    });

    renderPage();
    await actor.click(await screen.findByRole('button', { name: '管理文档 KB-1' }));
    const trigger = await screen.findByRole('button', { name: '重试解析 合同规范.pdf' });
    trigger.focus();
    await actor.keyboard('{Enter}');

    expect(screen.getByRole('dialog', { name: '确认重试解析' })).toBeInTheDocument();
    screen.getByRole('button', { name: '取消' }).focus();
    await actor.keyboard('{Enter}');

    const restoredTrigger = await screen.findByRole('button', { name: '重试解析 合同规范.pdf' });
    await waitFor(() => expect(restoredTrigger).toHaveFocus());
    expect(retryPosts).toBe(0);
  });

  it('requires one explicit confirmation, requeues the exact failed document once, and refreshes its state', async () => {
    const actor = userEvent.setup();
    let documentReads = 0;
    let retryPosts = 0;
    let releaseRetry!: (response: Response) => void;
    const pendingRetry = new Promise<Response>((resolve) => { releaseRetry = resolve; });

    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/model-providers/embedding-options')) return envelope([]);
      if (url.endsWith('/api/v1/knowledge-bases')) return envelope([
        { knowledgeBaseId: 'KB-1', name: '产品知识库', visibility: 'PRIVATE', status: 'ACTIVE' },
      ]);
      if ((!init?.method || init.method === 'GET') && url.endsWith('/knowledge-bases/KB-1/documents')) {
        documentReads += 1;
        return envelope([{
          knowledgeBaseId: 'KB-1',
          documentId: 'D-FAILED',
          fileName: '合同规范.pdf',
          status: documentReads === 1 ? 'FAILED' : 'UPLOADED',
          jobId: documentReads === 1 ? 'JOB-8' : null,
          jobType: documentReads === 1 ? 'DOCUMENT_PARSE' : null,
          jobStatus: documentReads === 1 ? 'FAILED' : null,
          failureCode: documentReads === 1 ? 'AIP-KB-PARSE-4001' : null,
          failureSummary: documentReads === 1 ? '文件结构损坏' : null,
          traceId: documentReads === 1 ? 'trace-job-8' : null,
          manualRetryEligible: documentReads === 1,
          cancellationReason: null,
          cancelledBy: null,
          cancellationTraceId: null,
          cancelledAt: null,
          cancelEligible: false,
        }]);
      }
      if (init?.method === 'POST' && url.endsWith('/knowledge-bases/KB-1/documents/D-FAILED/parse')) {
        retryPosts += 1;
        return pendingRetry;
      }
      return envelope([]);
    });

    renderPage();
    await actor.click(await screen.findByRole('button', { name: '管理文档 KB-1' }));

    expect(await screen.findByText('合同规范.pdf')).toBeInTheDocument();
    expect(screen.getByText('AIP-KB-PARSE-4001：文件结构损坏')).toBeInTheDocument();
    expect(screen.getByText('Trace trace-job-8')).toBeInTheDocument();

    await actor.click(screen.getByRole('button', { name: '重试解析 合同规范.pdf' }));

    expect(screen.getByRole('dialog', { name: '确认重试解析' })).toBeInTheDocument();
    expect(screen.getByText('将为此文档创建新的解析作业；失败作业不会被复用。')).toBeInTheDocument();
    expect(retryPosts).toBe(0);

    const confirm = screen.getByRole('button', { name: '确认重试解析' });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(retryPosts).toBe(1);

    releaseRetry(await envelope({
      documentId: 'D-FAILED',
      ingestJobId: 'JOB-9',
      status: 'PENDING',
      chunkCount: 0,
    }));

    expect(await screen.findByText('已将“合同规范.pdf”重新排队解析。')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('已上传，等待解析')).toBeInTheDocument());
    expect(documentReads).toBe(2);
    expect(retryPosts).toBe(1);
    expect(screen.queryByRole('dialog', { name: '确认重试解析' })).not.toBeInTheDocument();
  });

  it('keeps the trusted list and requires a fresh confirmation after a traced recovery error', async () => {
    const actor = userEvent.setup();
    let retryPosts = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/model-providers/embedding-options')) return envelope([]);
      if (url.endsWith('/api/v1/knowledge-bases')) return envelope([
        { knowledgeBaseId: 'KB-1', name: '产品知识库', visibility: 'PRIVATE', status: 'ACTIVE' },
      ]);
      if ((!init?.method || init.method === 'GET') && url.endsWith('/knowledge-bases/KB-1/documents')) {
        return envelope([{
          knowledgeBaseId: 'KB-1',
          documentId: 'D-FAILED',
          fileName: '合同规范.pdf',
          status: retryPosts > 1 ? 'UPLOADED' : 'FAILED',
          jobId: retryPosts > 1 ? null : 'JOB-8',
          jobType: retryPosts > 1 ? null : 'DOCUMENT_PARSE',
          jobStatus: retryPosts > 1 ? null : 'FAILED',
          failureCode: retryPosts > 1 ? null : 'AIP-KB-PARSE-4001',
          failureSummary: retryPosts > 1 ? null : '解析器拒绝了损坏的文件结构',
          traceId: retryPosts > 1 ? null : 'trace-job-8',
          manualRetryEligible: retryPosts <= 1,
          cancellationReason: null,
          cancelledBy: null,
          cancellationTraceId: null,
          cancelledAt: null,
          cancelEligible: false,
        }]);
      }
      if (init?.method === 'POST' && url.endsWith('/knowledge-bases/KB-1/documents/D-FAILED/parse')) {
        retryPosts += 1;
        return retryPosts === 1
          ? failure('人工重试暂时无法提交')
          : envelope({ documentId: 'D-FAILED', ingestJobId: 'JOB-10', status: 'PENDING', chunkCount: 0 });
      }
      return envelope([]);
    });

    renderPage();
    await actor.click(await screen.findByRole('button', { name: '管理文档 KB-1' }));
    await actor.click(await screen.findByRole('button', { name: '重试解析 合同规范.pdf' }));
    await actor.click(screen.getByRole('button', { name: '确认重试解析' }));

    expect(await screen.findByText('人工重试暂时无法提交', { exact: false })).toBeInTheDocument();
    expect(screen.getByText(/Trace trace-recovery/)).toBeInTheDocument();
    expect(screen.getByRole('table', { name: '知识库文档列表' })).toBeInTheDocument();
    expect(screen.getByText('合同规范.pdf')).toBeInTheDocument();
    expect(retryPosts).toBe(1);

    await actor.click(screen.getByRole('button', { name: '重新确认重试解析' }));
    expect(screen.getByRole('dialog', { name: '确认重试解析' })).toBeInTheDocument();
    expect(retryPosts).toBe(1);

    await actor.click(screen.getByRole('button', { name: '确认重试解析' }));
    await waitFor(() => expect(retryPosts).toBe(2));
    expect(await screen.findByText('已将“合同规范.pdf”重新排队解析。')).toBeInTheDocument();
  });

  it('rebuilds a failed index through the index seam and never calls parse', async () => {
    const actor = userEvent.setup();
    let documentReads = 0;
    const posts: string[] = [];

    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/model-providers/embedding-options')) return envelope([]);
      if (url.endsWith('/api/v1/knowledge-bases')) return envelope([
        { knowledgeBaseId: 'KB-1', name: '产品知识库', visibility: 'PRIVATE', status: 'ACTIVE' },
      ]);
      if ((!init?.method || init.method === 'GET') && url.endsWith('/knowledge-bases/KB-1/documents')) {
        documentReads += 1;
        return envelope([{
          knowledgeBaseId: 'KB-1',
          documentId: 'D-INDEX',
          fileName: '索引手册.pdf',
          status: documentReads === 1 ? 'FAILED' : 'VECTORIZING',
          jobId: documentReads === 1 ? 'JOB-INDEX-7' : 'JOB-INDEX-8',
          jobType: 'DOCUMENT_INDEX',
          jobStatus: documentReads === 1 ? 'FAILED' : 'PENDING',
          failureCode: documentReads === 1 ? 'AIP-KB-INDEX-5031' : null,
          failureSummary: documentReads === 1 ? '向量服务暂不可用' : null,
          traceId: documentReads === 1 ? 'trace-index-7' : null,
          manualRetryEligible: false,
          cancellationReason: null,
          cancelledBy: null,
          cancellationTraceId: null,
          cancelledAt: null,
          cancelEligible: false,
        }]);
      }
      if (init?.method === 'POST') {
        posts.push(url);
        return envelope({
          documentId: 'D-INDEX', ingestJobId: 'JOB-INDEX-8', status: 'PENDING', indexedChunkCount: 4,
        });
      }
      return envelope([]);
    });

    renderPage();
    await actor.click(await screen.findByRole('button', { name: '管理文档 KB-1' }));
    await actor.click(await screen.findByRole('button', { name: '重建索引 索引手册.pdf' }));

    expect(screen.getByRole('dialog', { name: '确认重建索引' })).toBeInTheDocument();
    expect(screen.getByText('将以现有可信切片创建新的索引代次；失败代次不会被激活。')).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '确认重建索引' }));

    expect(await screen.findByText('已为“索引手册.pdf”创建新的索引代次。')).toBeInTheDocument();
    expect(posts).toEqual(['/api/v1/knowledge-bases/KB-1/documents/D-INDEX/index']);
    expect(posts.some((url) => url.endsWith('/parse'))).toBe(false);
    await waitFor(() => expect(screen.getByText('正在建立索引')).toBeInTheDocument());
  });
});
