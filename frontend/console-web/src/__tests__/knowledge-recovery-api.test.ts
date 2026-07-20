import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  cancelKnowledgeDocumentProcessing,
  listKnowledgeRecoveryDocuments,
  recoveryActionForDocument,
  retryFailedKnowledgeDocument,
} from '../knowledge-recovery-api';

function response(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    header: { code: 'SUCCESS', message: 'success', traceId: 'trace-api' },
    body,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

afterEach(() => {
  localStorage.clear();
  vi.restoreAllMocks();
});

describe('knowledge recovery API', () => {
  it('loads and normalizes the document recovery facts exposed by the backend', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() => response([{
      knowledgeBaseId: 'KB /1',
      documentId: 'DOC-1',
      fileName: '运行手册.pdf',
      status: 'FAILED',
      jobId: 'JOB-1',
      jobType: 'DOCUMENT_PARSE',
      jobStatus: 'FAILED',
      failureCode: 'AIP-KB-PARSE-4001',
      failureSummary: 'PDF 结构损坏',
      traceId: 'trace-job-1',
      manualRetryEligible: true,
      cancellationReason: null,
      cancelledBy: null,
      cancellationTraceId: null,
      cancelledAt: null,
      cancelEligible: false,
    }]));

    await expect(listKnowledgeRecoveryDocuments('KB /1')).resolves.toEqual([{
      knowledgeBaseId: 'KB /1',
      documentId: 'DOC-1',
      fileName: '运行手册.pdf',
      status: 'FAILED',
      jobId: 'JOB-1',
      jobType: 'DOCUMENT_PARSE',
      jobStatus: 'FAILED',
      failureCode: 'AIP-KB-PARSE-4001',
      failureSummary: 'PDF 结构损坏',
      traceId: 'trace-job-1',
      manualRetryEligible: true,
      cancellationReason: '',
      cancelledBy: '',
      cancellationTraceId: '',
      cancelledAt: '',
      cancelEligible: false,
    }]);
    expect(fetchSpy.mock.calls[0]?.[0]).toBe('/api/v1/knowledge-bases/KB%20%2F1/documents');
    expect(fetchSpy.mock.calls[0]?.[1]?.method).toBeUndefined();
  });

  it('posts only to the backend manual parse-requeue seam for the exact failed document', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() => response({
      documentId: 'DOC /1',
      ingestJobId: 'JOB-9',
      status: 'PENDING',
      chunkCount: 0,
    }));

    await expect(retryFailedKnowledgeDocument({
      knowledgeBaseId: 'KB /1',
      documentId: 'DOC /1',
      fileName: '运行手册.pdf',
      status: 'FAILED',
      jobId: 'JOB-9',
      jobType: 'DOCUMENT_PARSE',
      jobStatus: 'FAILED',
      failureCode: 'AIP-KB-PARSE-4001',
      failureSummary: '解析失败',
      traceId: 'trace-job-9',
      manualRetryEligible: true,
      cancellationReason: '',
      cancelledBy: '',
      cancellationTraceId: '',
      cancelledAt: '',
      cancelEligible: false,
    })).resolves.toEqual({
      action: 'parse',
      documentId: 'DOC /1',
      ingestJobId: 'JOB-9',
      status: 'PENDING',
    });
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/v1/knowledge-bases/KB%20%2F1/documents/DOC%20%2F1/parse',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('creates a replacement generation for the exact failed index document', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() => response({
      documentId: 'DOC-2',
      ingestJobId: 'JOB-11',
      status: 'PENDING',
      indexedChunkCount: 7,
      failureReason: null,
    }));

    await expect(retryFailedKnowledgeDocument({
      knowledgeBaseId: 'KB-1',
      documentId: 'DOC-2',
      fileName: '索引手册.pdf',
      status: 'FAILED',
      jobId: 'JOB-10',
      jobType: 'DOCUMENT_INDEX',
      jobStatus: 'FAILED',
      failureCode: 'AIP-KB-INDEX-5001',
      failureSummary: '向量服务暂不可用',
      traceId: 'trace-job-10',
      manualRetryEligible: false,
      cancellationReason: '',
      cancelledBy: '',
      cancellationTraceId: '',
      cancelledAt: '',
      cancelEligible: false,
    })).resolves.toEqual({
      action: 'index',
      documentId: 'DOC-2',
      ingestJobId: 'JOB-11',
      status: 'PENDING',
    });
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/v1/knowledge-bases/KB-1/documents/DOC-2/index',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(fetchSpy.mock.calls.some(([url]) => String(url).endsWith('/parse'))).toBe(false);
  });

  it('fails closed before any request when the trusted job facts do not identify a supported recovery', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const document = {
      knowledgeBaseId: 'KB-1',
      documentId: 'DOC-3',
      fileName: '未知作业.pdf',
      status: 'FAILED',
      jobId: 'JOB-12',
      jobType: 'DOCUMENT_UNKNOWN',
      jobStatus: 'FAILED',
      failureCode: 'AIP-KB-UNKNOWN',
      failureSummary: '未知作业失败',
      traceId: 'trace-job-12',
      manualRetryEligible: true,
      cancellationReason: '',
      cancelledBy: '',
      cancellationTraceId: '',
      cancelledAt: '',
      cancelEligible: false,
    };

    expect(recoveryActionForDocument(document)).toBeUndefined();
    await expect(retryFailedKnowledgeDocument(document)).rejects.toThrow('当前失败作业不支持人工恢复');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('fails closed when the list response is not a document array', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response({ documentId: 'DOC-1' }));

    await expect(listKnowledgeRecoveryDocuments('KB-1'))
      .rejects.toThrow('文档列表响应格式无效');
  });

  it('cancels the exact eligible job with a normalized reason and requires complete cancellation evidence', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() => response({
      knowledgeBaseId: 'KB /1',
      documentId: 'DOC /1',
      fileName: '运行手册.pdf',
      status: 'CANCELLED',
      jobId: 'JOB-21',
      jobType: 'DOCUMENT_PARSE',
      jobStatus: 'CANCELLED',
      failureCode: null,
      failureSummary: null,
      traceId: 'trace-job-21',
      manualRetryEligible: false,
      cancellationReason: '内容已由新版替代',
      cancelledBy: 'USER-9',
      cancellationTraceId: 'trace-cancel-21',
      cancelledAt: '2026-07-19T02:03:04Z',
      cancelEligible: false,
    }));
    const target = {
      knowledgeBaseId: 'KB /1',
      documentId: 'DOC /1',
      fileName: '运行手册.pdf',
      status: 'PARSING',
      jobId: 'JOB-21',
      jobType: 'DOCUMENT_PARSE',
      jobStatus: 'PARSING',
      failureCode: '',
      failureSummary: '',
      traceId: 'trace-job-21',
      manualRetryEligible: false,
      cancellationReason: '',
      cancelledBy: '',
      cancellationTraceId: '',
      cancelledAt: '',
      cancelEligible: true,
    };

    await expect(cancelKnowledgeDocumentProcessing(target, '  内容已由新版替代  ')).resolves.toEqual({
      ...target,
      status: 'CANCELLED',
      jobStatus: 'CANCELLED',
      cancellationReason: '内容已由新版替代',
      cancelledBy: 'USER-9',
      cancellationTraceId: 'trace-cancel-21',
      cancelledAt: '2026-07-19T02:03:04Z',
      cancelEligible: false,
    });
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/v1/knowledge-bases/KB%20%2F1/documents/DOC%20%2F1/cancel',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ reason: '内容已由新版替代' }) }),
    );
  });

  it('fails closed before cancellation for an ineligible target or invalid reason', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const target = {
      knowledgeBaseId: 'KB-1', documentId: 'DOC-1', fileName: '运行手册.pdf', status: 'PARSING',
      jobId: 'JOB-1', jobType: 'DOCUMENT_PARSE', jobStatus: 'PARSING', failureCode: '', failureSummary: '',
      traceId: 'trace-job-1', manualRetryEligible: false, cancellationReason: '', cancelledBy: '',
      cancellationTraceId: '', cancelledAt: '', cancelEligible: false,
    };

    await expect(cancelKnowledgeDocumentProcessing(target, '足够长的理由')).rejects.toThrow('当前文档作业不支持取消');
    await expect(cancelKnowledgeDocumentProcessing({ ...target, cancelEligible: true }, '短')).rejects.toThrow('取消理由至少需要 3 个字符');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('rejects a nominally cancelled response when durable evidence is incomplete', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response({
      knowledgeBaseId: 'KB-1', documentId: 'DOC-1', fileName: '运行手册.pdf', status: 'CANCELLED',
      jobId: 'JOB-1', jobType: 'DOCUMENT_PARSE', jobStatus: 'CANCELLED', failureCode: null, failureSummary: null,
      traceId: 'trace-job-1', manualRetryEligible: false, cancellationReason: '停止处理', cancelledBy: null,
      cancellationTraceId: 'trace-cancel-1', cancelledAt: 'not-an-instant', cancelEligible: false,
    }));
    const target = {
      knowledgeBaseId: 'KB-1', documentId: 'DOC-1', fileName: '运行手册.pdf', status: 'PARSING',
      jobId: 'JOB-1', jobType: 'DOCUMENT_PARSE', jobStatus: 'PARSING', failureCode: '', failureSummary: '',
      traceId: 'trace-job-1', manualRetryEligible: false, cancellationReason: '', cancelledBy: '',
      cancellationTraceId: '', cancelledAt: '', cancelEligible: true,
    };

    await expect(cancelKnowledgeDocumentProcessing(target, '停止处理')).rejects.toThrow('文档取消响应格式无效');
  });
});
