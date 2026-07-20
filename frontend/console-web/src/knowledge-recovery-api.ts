import {
  cancelKnowledgeDocument,
  indexKnowledgeDocument,
  listKnowledgeDocuments,
  parseKnowledgeDocument,
} from './api';

export type KnowledgeRecoveryAction = 'parse' | 'index';

export interface KnowledgeRecoveryDocument {
  knowledgeBaseId: string;
  documentId: string;
  fileName: string;
  status: string;
  jobId: string;
  jobType: string;
  jobStatus: string;
  failureCode: string;
  failureSummary: string;
  traceId: string;
  manualRetryEligible: boolean;
  cancellationReason: string;
  cancelledBy: string;
  cancellationTraceId: string;
  cancelledAt: string;
  cancelEligible: boolean;
}

export interface KnowledgeDocumentRetryResult {
  action: KnowledgeRecoveryAction;
  documentId: string;
  ingestJobId: string;
  status: string;
}

function requiredText(value: unknown, message: string) {
  if (typeof value !== 'string' || !value.trim()) throw new Error(message);
  return value.trim();
}

function optionalText(value: unknown, message: string) {
  if (value === null || value === undefined) return '';
  if (typeof value !== 'string') throw new Error(message);
  return value.trim();
}

function recoveryDocument(
  value: unknown,
  requestedKnowledgeBaseId: string,
  message = '文档列表响应格式无效',
): KnowledgeRecoveryDocument {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(message);
  const record = value as Record<string, unknown>;
  const document = {
    knowledgeBaseId: typeof record.knowledgeBaseId === 'string' && record.knowledgeBaseId.trim()
      ? record.knowledgeBaseId.trim()
      : requestedKnowledgeBaseId,
    documentId: requiredText(record.documentId, message),
    fileName: requiredText(record.fileName, message),
    status: requiredText(record.status, message),
    jobId: optionalText(record.jobId, message),
    jobType: optionalText(record.jobType, message),
    jobStatus: optionalText(record.jobStatus, message),
    failureCode: optionalText(record.failureCode, message),
    failureSummary: optionalText(record.failureSummary, message),
    traceId: optionalText(record.traceId, message),
    manualRetryEligible: (() => {
      if (typeof record.manualRetryEligible !== 'boolean') throw new Error(message);
      return record.manualRetryEligible;
    })(),
    cancellationReason: optionalText(record.cancellationReason, message),
    cancelledBy: optionalText(record.cancelledBy, message),
    cancellationTraceId: optionalText(record.cancellationTraceId, message),
    cancelledAt: optionalText(record.cancelledAt, message),
    cancelEligible: (() => {
      if (typeof record.cancelEligible !== 'boolean') throw new Error(message);
      return record.cancelEligible;
    })(),
  };
  const cancellationEvidence = [
    document.cancellationReason,
    document.cancelledBy,
    document.cancellationTraceId,
    document.cancelledAt,
  ];
  if (document.status === 'CANCELLED') {
    if (document.jobStatus !== 'CANCELLED'
      || document.cancelEligible
      || cancellationEvidence.some((evidence) => !evidence)
      || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(document.cancelledAt)
      || Number.isNaN(Date.parse(document.cancelledAt))) throw new Error(message);
  } else if (cancellationEvidence.some(Boolean)) {
    throw new Error(message);
  }
  if (document.cancelEligible && (
    !document.jobId
    || !document.jobType
    || !['PENDING', 'RETRY_WAIT', 'PARSING', 'INDEXING'].includes(document.jobStatus)
  )) throw new Error(message);
  return document;
}

function retryResult(value: unknown, action: KnowledgeRecoveryAction): KnowledgeDocumentRetryResult {
  const message = '人工重试响应格式无效';
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(message);
  const record = value as Record<string, unknown>;
  const count = action === 'parse' ? record.chunkCount : record.indexedChunkCount;
  if (!Number.isInteger(count) || Number(count) < 0) {
    throw new Error(message);
  }
  return {
    action,
    documentId: requiredText(record.documentId, message),
    ingestJobId: requiredText(record.ingestJobId, message),
    status: requiredText(record.status, message),
  };
}

export function recoveryActionForDocument(
  document: Pick<KnowledgeRecoveryDocument, 'status' | 'jobId' | 'jobType' | 'jobStatus' | 'manualRetryEligible'>,
): KnowledgeRecoveryAction | undefined {
  if (document.status !== 'FAILED' || !document.jobId || document.jobStatus !== 'FAILED') return undefined;
  if (document.jobType === 'DOCUMENT_PARSE' && document.manualRetryEligible) return 'parse';
  if (document.jobType === 'DOCUMENT_INDEX') return 'index';
  return undefined;
}

export async function listKnowledgeRecoveryDocuments(
  knowledgeBaseId: string,
): Promise<KnowledgeRecoveryDocument[]> {
  const documents = await listKnowledgeDocuments(knowledgeBaseId);
  if (!Array.isArray(documents)) throw new Error('文档列表响应格式无效');
  return documents.map((document) => recoveryDocument(document, knowledgeBaseId));
}

export async function retryFailedKnowledgeDocument(
  document: KnowledgeRecoveryDocument,
): Promise<KnowledgeDocumentRetryResult> {
  const action = recoveryActionForDocument(document);
  if (!action) throw new Error('当前失败作业不支持人工恢复，请刷新状态或联系平台管理员。');
  const result = action === 'parse'
    ? await parseKnowledgeDocument(document.knowledgeBaseId, document.documentId)
    : await indexKnowledgeDocument(document.knowledgeBaseId, document.documentId);
  return retryResult(result, action);
}

export async function cancelKnowledgeDocumentProcessing(
  document: KnowledgeRecoveryDocument,
  reason: string,
): Promise<KnowledgeRecoveryDocument> {
  if (!document.cancelEligible || !document.jobId || !document.jobStatus) {
    throw new Error('当前文档作业不支持取消，请刷新状态后重试。');
  }
  const normalizedReason = reason.trim();
  if ([...normalizedReason].length < 3) throw new Error('取消理由至少需要 3 个字符。');
  if ([...normalizedReason].length > 300) throw new Error('取消理由不能超过 300 个字符。');
  const result = await cancelKnowledgeDocument(
    document.knowledgeBaseId,
    document.documentId,
    normalizedReason,
  );
  return recoveryDocument(result, document.knowledgeBaseId, '文档取消响应格式无效');
}
