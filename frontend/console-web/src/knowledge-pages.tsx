import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogBody,
  DialogContent,
  DialogSurface,
  DialogTitle,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  Select,
  Text,
  Textarea,
} from '@fluentui/react-components';
import {
  createKnowledgeBase,
  indexKnowledgeDocument,
  listEmbeddingModelOptions,
  listResource,
  parseKnowledgeDocument,
  uploadKnowledgeDocument,
} from './api';
import { dialogBackdropMotion, dialogSurfaceMotion } from './dialog-motion';
import {
  cancelKnowledgeDocumentProcessing,
  listKnowledgeRecoveryDocuments,
  recoveryActionForDocument,
  retryFailedKnowledgeDocument,
  type KnowledgeRecoveryAction,
  type KnowledgeRecoveryDocument,
} from './knowledge-recovery-api';
import { renderCell } from './page-format';
import { PageHeading, RequestFeedback } from './page-shared';
import { PAGE_SIZE, Pagination } from './pagination';
import type { KnowledgeBaseRouteCapability } from './navigation';
import type { RequestStatus } from './types';

type DocumentMutation =
  | { kind: 'upload'; file: File }
  | { kind: 'parse' | 'index'; documentId: string };

interface DocumentActionFailure {
  error: Error;
  knowledgeBaseId: string;
  retry: { kind: 'mutation'; command: DocumentMutation } | { kind: 'refresh' };
}

type ManualRecoveryTarget = KnowledgeRecoveryDocument & { action: KnowledgeRecoveryAction };

interface ManualRecoveryFailure {
  error: Error;
  target: ManualRecoveryTarget;
}

const DOCUMENT_STATUS_LABELS: Record<string, string> = {
  UPLOADED: '已上传，等待解析',
  PARSING: '正在解析',
  CHUNKED: '已解析，等待建立索引',
  VECTORIZING: '正在建立索引',
  AVAILABLE: '可检索，索引已激活',
  FAILED: '失败，需要人工介入',
  CANCELLED: '已取消',
};

function documentStatusLabel(status: string) {
  return DOCUMENT_STATUS_LABELS[status] || status;
}

function recoveryLabel(action: KnowledgeRecoveryAction) {
  return action === 'parse' ? '重试解析' : '重建索引';
}

function errorTraceId(error?: Error) {
  if (!error || !('traceId' in error)) return '';
  return String((error as Error & { traceId?: unknown }).traceId || '');
}

function documentError(reason: unknown, fallback: string) {
  return reason instanceof Error ? reason : new Error(fallback);
}

function retryLabel(failure: DocumentActionFailure) {
  if (failure.retry.kind === 'refresh') return '刷新文档列表';
  if (failure.retry.command.kind === 'upload') return '重试上传';
  return failure.retry.command.kind === 'parse' ? '重试解析' : '重试建立索引';
}

export function KnowledgeBasePage({
  capability,
  permissionCodes,
}: {
  capability: KnowledgeBaseRouteCapability;
  permissionCodes: readonly string[];
}) {
  const isGovernance = capability === 'knowledge-base:manage';
  const allows = (permission: string) => isGovernance && permissionCodes.includes(permission);
  const canCreate = allows('knowledge-base:knowledge-base:create');
  const canReadDocuments = allows('knowledge-base:document:read');
  const canUpload = allows('knowledge-base:document:upload');
  const canParse = allows('knowledge-base:document:parse');
  const canIndex = allows('knowledge-base:document:index');
  const canCancel = allows('knowledge-base:document:cancel');
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [documents, setDocuments] = useState<KnowledgeRecoveryDocument[]>([]);
  const [documentsStatus, setDocumentsStatus] = useState<RequestStatus>('empty');
  const [documentsError, setDocumentsError] = useState<Error>();
  const [documentActionFailure, setDocumentActionFailure] = useState<DocumentActionFailure>();
  const [manualRecoveryTarget, setManualRecoveryTarget] = useState<ManualRecoveryTarget>();
  const [manualRecoveryFailure, setManualRecoveryFailure] = useState<ManualRecoveryFailure>();
  const [manualRecoveryNotice, setManualRecoveryNotice] = useState('');
  const [cancellationTarget, setCancellationTarget] = useState<KnowledgeRecoveryDocument>();
  const [cancellationReason, setCancellationReason] = useState('');
  const [cancellationError, setCancellationError] = useState<Error>();
  const [selectedId, setSelectedId] = useState('');
  const [panel, setPanel] = useState<'create' | 'documents'>();
  const [status, setStatus] = useState<RequestStatus>('loading');
  const [error, setError] = useState<Error>();
  const [createError, setCreateError] = useState<Error>();
  const [working, setWorking] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [documentPage, setDocumentPage] = useState(1);
  const [documentPageSize, setDocumentPageSize] = useState(PAGE_SIZE);
  const [modelOptions, setModelOptions] = useState<{ id: string; label: string }[]>([]);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [embeddingModel, setEmbeddingModel] = useState('');
  const documentRequest = useRef(0);
  const loadController = useRef<AbortController>();
  const manualRecoveryInFlight = useRef('');
  const cancellationInFlight = useRef('');
  const manualRecoveryReturnFocus = useRef('');
  const restoreManualRecoveryFocus = useRef(false);
  const manualRecoveryFeedback = useRef<HTMLDivElement | null>(null);
  const uploadInput = useRef<HTMLInputElement | null>(null);

  const load = useCallback(async () => {
    loadController.current?.abort();
    const controller = new AbortController();
    loadController.current = controller;
    setStatus('loading');
    setError(undefined);
    setRows([]);
    setModelOptions([]);
    try {
      const [value, models] = await Promise.all([
        listResource('/api/v1/knowledge-bases', controller.signal),
        canCreate ? listEmbeddingModelOptions(controller.signal) : Promise.resolve([]),
      ]);
      if (loadController.current !== controller || controller.signal.aborted) return;
      setRows(value);
      setModelOptions(models.map((model) => ({ id: model.modelConfigId, label: model.label })));
      setStatus(value.length ? 'success' : 'empty');
    } catch (reason) {
      if (loadController.current === controller && !controller.signal.aborted) {
        setError(reason as Error);
        setStatus('error');
      }
    } finally {
      if (loadController.current === controller) loadController.current = undefined;
    }
  }, [canCreate]);

  useEffect(() => {
    void load();
    return () => {
      loadController.current?.abort();
    };
  }, [load]);

  useEffect(() => {
    if (manualRecoveryTarget || cancellationTarget || !restoreManualRecoveryFocus.current) return;
    restoreManualRecoveryFocus.current = false;
    const documentId = manualRecoveryReturnFocus.current;
    const action = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-recovery-document-id]'))
      .find((candidate) => candidate.dataset.recoveryDocumentId === documentId);
    const row = Array.from(document.querySelectorAll<HTMLElement>('[data-document-focus-id]'))
      .find((candidate) => candidate.dataset.documentFocusId === documentId);
    let focusFrame = 0;
    const settleFrame = requestAnimationFrame(() => {
      focusFrame = requestAnimationFrame(() => (action || row)?.focus());
    });
    return () => {
      cancelAnimationFrame(settleFrame);
      cancelAnimationFrame(focusFrame);
    };
  }, [cancellationTarget, manualRecoveryTarget]);

  useEffect(() => {
    if (!manualRecoveryTarget && (manualRecoveryNotice || manualRecoveryFailure)) {
      manualRecoveryFeedback.current?.focus();
    }
  }, [manualRecoveryFailure, manualRecoveryNotice, manualRecoveryTarget]);

  async function openDocuments(id: string) {
    if (!canReadDocuments) return;
    const requestId = ++documentRequest.current;
    setSelectedId(id);
    setPanel('documents');
    setDocumentPage(1);
    setDocuments([]);
    setDocumentsStatus('loading');
    setDocumentsError(undefined);
    setDocumentActionFailure(undefined);
    setManualRecoveryTarget(undefined);
    setManualRecoveryFailure(undefined);
    setManualRecoveryNotice('');
    setCancellationTarget(undefined);
    setCancellationReason('');
    setCancellationError(undefined);
    setWorking(true);
    try {
      const nextDocuments = await listKnowledgeRecoveryDocuments(id);
      if (documentRequest.current !== requestId) return;
      setDocuments(nextDocuments);
      setDocumentsStatus(nextDocuments.length ? 'success' : 'empty');
    } catch (reason) {
      if (documentRequest.current !== requestId) return;
      setDocumentsError(reason as Error);
      setDocumentsStatus('error');
    } finally {
      if (documentRequest.current === requestId) setWorking(false);
    }
  }

  function closePanel() {
    if (panel === 'documents') {
      documentRequest.current += 1;
      setDocuments([]);
      setDocumentsStatus('empty');
      setDocumentsError(undefined);
      setDocumentActionFailure(undefined);
      setManualRecoveryTarget(undefined);
      setManualRecoveryFailure(undefined);
      setManualRecoveryNotice('');
      setCancellationTarget(undefined);
      setCancellationReason('');
      setCancellationError(undefined);
      setSelectedId('');
      setWorking(false);
      manualRecoveryInFlight.current = '';
      cancellationInFlight.current = '';
      manualRecoveryReturnFocus.current = '';
    }
    if (panel === 'create') {
      setCreateError(undefined);
      setName('');
      setDescription('');
      setEmbeddingModel('');
    }
    setPanel(undefined);
  }

  function openCreatePanel() {
    if (!canCreate) return;
    setCreateError(undefined);
    setName('');
    setDescription('');
    setEmbeddingModel('');
    setPanel('create');
  }

  async function create() {
    if (!canCreate || !name.trim() || !embeddingModel.trim()) return;
    setWorking(true);
    setCreateError(undefined);
    try {
      await createKnowledgeBase({
        name: name.trim(),
        description: description.trim(),
        visibility: 'private',
        embeddingModelConfigId: embeddingModel.trim(),
      });
      closePanel();
      await load();
    } catch (reason) {
      setCreateError(reason as Error);
    } finally {
      setWorking(false);
    }
  }

  const createTraceId = errorTraceId(createError);

  async function refreshTrustedDocuments(knowledgeBaseId: string, requestId: number) {
    const nextDocuments = await listKnowledgeRecoveryDocuments(knowledgeBaseId);
    if (documentRequest.current !== requestId) return;
    setDocuments(nextDocuments);
    setDocumentsStatus(nextDocuments.length ? 'success' : 'empty');
  }

  async function executeDocumentMutation(command: DocumentMutation) {
    const authorized = command.kind === 'upload' ? canUpload : command.kind === 'parse' ? canParse : canIndex;
    const documentsTrusted = documentsStatus === 'success' || documentsStatus === 'empty';
    if (!authorized || !selectedId || working || !documentsTrusted || (command.kind !== 'upload' && documentsStatus !== 'success')) return;
    const requestId = documentRequest.current;
    const knowledgeBaseId = selectedId;
    setWorking(true);
    setDocumentActionFailure(undefined);
    setManualRecoveryFailure(undefined);
    setManualRecoveryNotice('');
    try {
      try {
        if (command.kind === 'upload') await uploadKnowledgeDocument(knowledgeBaseId, command.file);
        else if (command.kind === 'parse') await parseKnowledgeDocument(knowledgeBaseId, command.documentId);
        else await indexKnowledgeDocument(knowledgeBaseId, command.documentId);
      } catch (reason) {
        if (documentRequest.current === requestId) {
          setDocumentActionFailure({
            error: documentError(reason, '文档操作失败'),
            knowledgeBaseId,
            retry: { kind: 'mutation', command },
          });
        }
        return;
      }
      if (documentRequest.current !== requestId) return;
      try {
        await refreshTrustedDocuments(knowledgeBaseId, requestId);
      } catch (reason) {
        if (documentRequest.current === requestId) {
          setDocumentActionFailure({
            error: documentError(reason, '文档操作已提交，但列表刷新失败'),
            knowledgeBaseId,
            retry: { kind: 'refresh' },
          });
        }
      }
    } finally {
      if (documentRequest.current === requestId) setWorking(false);
    }
  }

  async function retryDocumentAction() {
    const failure = documentActionFailure;
    if (!canReadDocuments || !failure || failure.knowledgeBaseId !== selectedId || working) return;
    if (failure.retry.kind === 'mutation') {
      await executeDocumentMutation(failure.retry.command);
      return;
    }
    const requestId = documentRequest.current;
    setWorking(true);
    setDocumentActionFailure(undefined);
    try {
      await refreshTrustedDocuments(failure.knowledgeBaseId, requestId);
    } catch (reason) {
      if (documentRequest.current === requestId) {
        setDocumentActionFailure({ ...failure, error: documentError(reason, '文档列表刷新失败') });
      }
    } finally {
      if (documentRequest.current === requestId) setWorking(false);
    }
  }

  function upload(file?: File) {
    if (file) void executeDocumentMutation({ kind: 'upload', file });
  }

  function documentAction(documentId: string, action: 'parse' | 'index') {
    void executeDocumentMutation({ kind: action, documentId });
  }

  const documentActionTraceId = errorTraceId(documentActionFailure?.error);
  const manualRecoveryFailureTraceId = errorTraceId(manualRecoveryFailure?.error);
  const cancellationErrorTraceId = errorTraceId(cancellationError);
  const cancellationReasonLength = [...cancellationReason.trim()].length;

  function authorizedRecoveryAction(document: KnowledgeRecoveryDocument) {
    const action = recoveryActionForDocument(document);
    if (action === 'parse' && canParse) return action;
    if (action === 'index' && canIndex) return action;
    return undefined;
  }

  function openManualRecovery(document: KnowledgeRecoveryDocument) {
    if (working || document.status !== 'FAILED' || document.knowledgeBaseId !== selectedId) return;
    const action = authorizedRecoveryAction(document);
    if (!action) return;
    manualRecoveryReturnFocus.current = document.documentId;
    setManualRecoveryFailure(undefined);
    setManualRecoveryNotice('');
    setManualRecoveryTarget({ ...document, action });
  }

  function cancelManualRecovery() {
    restoreManualRecoveryFocus.current = true;
    setManualRecoveryTarget(undefined);
  }

  function openCancellation(document: KnowledgeRecoveryDocument) {
    if (!canCancel || working || !document.cancelEligible || document.knowledgeBaseId !== selectedId) return;
    manualRecoveryReturnFocus.current = document.documentId;
    setManualRecoveryNotice('');
    setCancellationReason('');
    setCancellationError(undefined);
    setCancellationTarget({ ...document });
  }

  function closeCancellation() {
    restoreManualRecoveryFocus.current = true;
    setCancellationTarget(undefined);
    setCancellationReason('');
    setCancellationError(undefined);
  }

  async function confirmCancellation() {
    const target = cancellationTarget;
    const reason = cancellationReason.trim();
    if (!canCancel || !target || working || cancellationReasonLength < 3 || cancellationReasonLength > 300) return;
    const current = documents.find((document) => document.documentId === target.documentId);
    if (!current
      || !current.cancelEligible
      || current.knowledgeBaseId !== target.knowledgeBaseId
      || current.status !== target.status
      || current.jobId !== target.jobId
      || current.jobStatus !== target.jobStatus) {
      setCancellationError(new Error('文档作业状态已变化，未提交取消。请关闭确认框并刷新状态。'));
      return;
    }
    const inFlightKey = `${target.knowledgeBaseId}:${target.documentId}:${target.jobId}`;
    if (cancellationInFlight.current) return;
    cancellationInFlight.current = inFlightKey;
    const requestId = documentRequest.current;
    setWorking(true);
    setCancellationError(undefined);
    try {
      const result = await cancelKnowledgeDocumentProcessing(current, reason);
      if (documentRequest.current !== requestId) return;
      if (result.knowledgeBaseId !== target.knowledgeBaseId
        || result.documentId !== target.documentId
        || result.jobId !== target.jobId
        || result.status !== 'CANCELLED'
        || result.jobStatus !== 'CANCELLED'
        || result.cancellationReason !== reason) {
        throw new Error('后端未返回所选作业的完整取消证据，未将本次操作视为成功。');
      }
      setDocuments((currentDocuments) => currentDocuments.map((document) => (
        document.documentId === result.documentId ? result : document
      )));
      setManualRecoveryNotice(`已取消“${target.fileName}”的当前处理作业。`);
      restoreManualRecoveryFocus.current = true;
      setCancellationTarget(undefined);
      setCancellationReason('');
      try {
        await refreshTrustedDocuments(target.knowledgeBaseId, requestId);
      } catch (refreshError) {
        if (documentRequest.current === requestId) {
          setDocumentActionFailure({
            error: documentError(refreshError, '取消已生效，但文档状态刷新失败'),
            knowledgeBaseId: target.knowledgeBaseId,
            retry: { kind: 'refresh' },
          });
        }
      }
    } catch (reason) {
      if (documentRequest.current === requestId) {
        setCancellationError(documentError(reason, '文档作业取消失败'));
      }
    } finally {
      if (cancellationInFlight.current === inFlightKey) {
        cancellationInFlight.current = '';
        if (documentRequest.current === requestId) setWorking(false);
      }
    }
  }

  function reopenManualRecovery() {
    const failure = manualRecoveryFailure;
    if (!failure || working || failure.target.knowledgeBaseId !== selectedId) return;
    const current = documents.find((document) => document.documentId === failure.target.documentId);
    if (!current || current.status !== 'FAILED') {
      setManualRecoveryFailure({
        ...failure,
        error: new Error('文档状态已变化，请先刷新列表后再决定下一步。'),
      });
      return;
    }
    openManualRecovery(current);
  }

  async function confirmManualRecovery() {
    const target = manualRecoveryTarget;
    if (!target || working || target.knowledgeBaseId !== selectedId) return;
    const current = documents.find((document) => document.documentId === target.documentId);
    if (!current || current.status !== 'FAILED') {
      setManualRecoveryTarget(undefined);
      setManualRecoveryFailure({
        target,
        error: new Error('文档状态已变化，未提交人工重试。请刷新列表后重新确认。'),
      });
      return;
    }

    const inFlightKey = `${target.knowledgeBaseId}:${target.documentId}`;
    if (manualRecoveryInFlight.current) return;
    manualRecoveryInFlight.current = inFlightKey;
    const requestId = documentRequest.current;
    setWorking(true);
    setManualRecoveryFailure(undefined);
    setManualRecoveryNotice('');
    try {
      const currentAction = authorizedRecoveryAction(current);
      if (currentAction !== target.action || current.jobId !== target.jobId) {
        throw new Error('失败作业证据已变化，未提交恢复操作。请刷新列表后重新确认。');
      }
      const result = await retryFailedKnowledgeDocument(current);
      if (documentRequest.current !== requestId) return;
      if (result.action !== target.action || result.documentId !== target.documentId || result.status !== 'PENDING') {
        throw new Error('后端未确认所选文档已重新排队，未将本次操作视为成功。');
      }

      setManualRecoveryTarget(undefined);
      setManualRecoveryNotice(target.action === 'parse'
        ? `已将“${target.fileName}”重新排队解析。`
        : `已为“${target.fileName}”创建新的索引代次。`);
      try {
        await refreshTrustedDocuments(target.knowledgeBaseId, requestId);
      } catch (reason) {
        if (documentRequest.current === requestId) {
          setDocumentActionFailure({
            error: documentError(reason, '人工重试已提交，但文档状态刷新失败'),
            knowledgeBaseId: target.knowledgeBaseId,
            retry: { kind: 'refresh' },
          });
        }
      }
    } catch (reason) {
      if (documentRequest.current === requestId) {
        setManualRecoveryTarget(undefined);
        setManualRecoveryFailure({
          target,
          error: documentError(reason, '人工重试提交失败'),
        });
      }
    } finally {
      if (manualRecoveryInFlight.current === inFlightKey) {
        manualRecoveryInFlight.current = '';
        if (documentRequest.current === requestId) setWorking(false);
      }
    }
  }

  async function refreshManualRecoveryStatus() {
    if (!canReadDocuments || !selectedId || working) return;
    const requestId = documentRequest.current;
    setWorking(true);
    setDocumentActionFailure(undefined);
    try {
      await refreshTrustedDocuments(selectedId, requestId);
    } catch (reason) {
      if (documentRequest.current === requestId) {
        setDocumentActionFailure({
          error: documentError(reason, '文档状态刷新失败'),
          knowledgeBaseId: selectedId,
          retry: { kind: 'refresh' },
        });
      }
    } finally {
      if (documentRequest.current === requestId) setWorking(false);
    }
  }

  return (
    <main className="page-stack">
      <PageHeading
        title={isGovernance ? '知识库治理' : '可用知识'}
        description={isGovernance
          ? '创建知识库并维护文档解析、索引和可见范围；所有动作按当前租户授权。'
          : '查看当前账号可检索的知识范围。'}
      />
      {canCreate && (
        <div className="toolbar">
          <Button
            appearance="primary"
            disabled={status === 'loading' || status === 'error'}
            onClick={openCreatePanel}
          >
            新建知识库
          </Button>
        </div>
      )}
      <RequestFeedback status={status} error={error} onRetry={() => void load()} />
      {error && status !== 'error' && (
        <MessageBar intent="error"><MessageBarBody>{error.message}</MessageBarBody></MessageBar>
      )}
      {status === 'empty' && (
        <section className="empty-state">
          <Text weight="semibold">暂无知识库</Text>
          <Text>{canCreate ? '先确认可用向量模型，再创建当前租户的第一个知识库。' : '当前账号尚未获授权访问知识库。'}</Text>
        </section>
      )}
      {status === 'success' && rows.length > 0 && (
        <section className="surface data-region">
          <div className="table-wrap">
            <table aria-label="知识库列表">
              <thead><tr><th>名称</th><th>可见范围</th><th>向量模型</th><th>状态</th>{canReadDocuments && <th>操作</th>}</tr></thead>
              <tbody>{rows.slice((page - 1) * pageSize, page * pageSize).map((row) => (
                <tr key={String(row.knowledgeBaseId)}>
                  <td>{renderCell(row.name)}</td>
                  <td>{renderCell(row.visibility)}</td>
                  <td>{renderCell(row.embeddingModelConfigId)}</td>
                  <td>{renderCell(row.status)}</td>
                  {canReadDocuments && <td><Button appearance="subtle" aria-label={`管理文档 ${row.knowledgeBaseId}`} onClick={() => openDocuments(String(row.knowledgeBaseId))}>管理文档</Button></td>}
                </tr>
              ))}</tbody>
            </table>
          </div>
          <Pagination
            page={page}
            pageSize={pageSize}
            total={rows.length}
            onChange={(nextPage, nextSize) => { setPage(nextPage); setPageSize(nextSize); }}
          />
        </section>
      )}

      <Dialog
        open={Boolean(panel)}
        surfaceMotion={dialogSurfaceMotion}
        onOpenChange={(_, data) => {
          if (data.open || working) return;
          if (manualRecoveryTarget) cancelManualRecovery();
          else closePanel();
        }}
      >
        <DialogSurface
          backdropMotion={dialogBackdropMotion}
          className={panel === 'documents' && !manualRecoveryTarget ? 'wide-dialog' : undefined}
        >
          <DialogBody>
            <DialogTitle>
              {panel === 'create'
                ? '新建知识库'
                : manualRecoveryTarget
                  ? `确认${recoveryLabel(manualRecoveryTarget.action)}`
                  : '文档维护'}
            </DialogTitle>
            <DialogContent>
              {panel === 'create' ? (
                <div className="dialog-form">
                  <Field label="知识库名称" required><Input value={name} onChange={(_, data) => setName(data.value)} /></Field>
                  <Field label="说明"><Textarea value={description} onChange={(_, data) => setDescription(data.value)} /></Field>
                  <Field label="向量模型" required>
                    <Select aria-label="向量模型" value={embeddingModel} onChange={(event) => setEmbeddingModel(event.target.value)}>
                      <option value="">请选择已启用的向量模型</option>
                      {modelOptions.map((model) => <option key={model.id} value={model.id}>{model.label}</option>)}
                    </Select>
                  </Field>
                  {modelOptions.length === 0 && <MessageBar intent="warning"><MessageBarBody>当前没有可用向量模型，请联系平台管理员完成模型配置和审批。</MessageBarBody></MessageBar>}
                  {createError && <MessageBar intent="error"><MessageBarBody>{createError.message}{createTraceId && <span className="trace-hint">错误追踪：{createTraceId}</span>}</MessageBarBody></MessageBar>}
                </div>
              ) : manualRecoveryTarget ? (
                <div className="dialog-form">
                  <Text weight="semibold">{manualRecoveryTarget.fileName}</Text>
                  <Text>{manualRecoveryTarget.action === 'parse'
                    ? '将为此文档创建新的解析作业；失败作业不会被复用。'
                    : '将以现有可信切片创建新的索引代次；失败代次不会被激活。'}</Text>
                  <MessageBar intent="warning">
                    <MessageBarBody>
                      {manualRecoveryTarget.action === 'parse'
                        ? '解析完成后仍需建立索引；只有状态为“可检索，索引已激活”时内容才会进入检索。'
                        : '只有新的索引代次完成并激活后内容才会进入检索；失败代次不会被激活。'}
                    </MessageBarBody>
                  </MessageBar>
                  <Text>作业 {manualRecoveryTarget.jobId} · {manualRecoveryTarget.jobType} · {manualRecoveryTarget.jobStatus}</Text>
                  <Text>{manualRecoveryTarget.failureCode ? `${manualRecoveryTarget.failureCode}：` : ''}{manualRecoveryTarget.failureSummary || '后端未提供可披露摘要。'}</Text>
                  <Text>Trace {manualRecoveryTarget.traceId || '未提供'}</Text>
                </div>
              ) : (
                <div className="dialog-form">
                  {canUpload && (
                    <Field label="上传文档">
                      <div className="row-actions">
                        <input
                          ref={uploadInput}
                          hidden
                          type="file"
                          disabled={working || documentsStatus === 'loading' || documentsStatus === 'error'}
                          onChange={(event) => {
                            const file = event.currentTarget.files?.[0];
                            event.currentTarget.value = '';
                            upload(file);
                          }}
                        />
                        <Button
                          type="button"
                          disabled={working || documentsStatus === 'loading' || documentsStatus === 'error'}
                          onClick={() => uploadInput.current?.click()}
                        >选择并上传</Button>
                      </div>
                    </Field>
                  )}
                  <RequestFeedback
                    status={documentsStatus}
                    error={documentsError}
                    onRetry={() => openDocuments(selectedId)}
                    loadingLabel="正在读取知识库文档"
                  />
                  {manualRecoveryFailure && (
                    <div ref={manualRecoveryFeedback} tabIndex={-1} aria-live="assertive">
                      <MessageBar intent="error">
                        <MessageBarBody>
                          <div className="chat-error">
                            <span>
                              {manualRecoveryFailure.error.message}
                              {manualRecoveryFailureTraceId ? `（Trace ${manualRecoveryFailureTraceId}）` : ''}
                            </span>
                            <Button
                              size="small"
                              disabled={working}
                              data-recovery-document-id={manualRecoveryFailure.target.documentId}
                              onClick={reopenManualRecovery}
                            >
                              重新确认{recoveryLabel(manualRecoveryFailure.target.action)}
                            </Button>
                          </div>
                        </MessageBarBody>
                      </MessageBar>
                    </div>
                  )}
                  {manualRecoveryNotice && !manualRecoveryFailure && (
                    <div ref={manualRecoveryFeedback} tabIndex={-1} aria-live="polite">
                      <MessageBar intent="success">
                        <MessageBarBody>
                          <div className="chat-error">
                            <span>{manualRecoveryNotice}</span>
                            <Button size="small" disabled={working} onClick={() => void refreshManualRecoveryStatus()}>
                              刷新状态
                            </Button>
                          </div>
                        </MessageBarBody>
                      </MessageBar>
                    </div>
                  )}
                  {documentActionFailure && (
                    <MessageBar intent="error">
                      <MessageBarBody>
                        <div className="chat-error">
                          <span>{documentActionFailure.error.message}{documentActionTraceId ? `（Trace ${documentActionTraceId}）` : ''}</span>
                          <Button size="small" disabled={working} onClick={() => void retryDocumentAction()}>{retryLabel(documentActionFailure)}</Button>
                        </div>
                      </MessageBarBody>
                    </MessageBar>
                  )}
                  {documentsStatus === 'empty' ? (
                    <Text>该知识库暂无文档。</Text>
                  ) : documentsStatus === 'success' ? (
                    <div className="data-region">
                      <div className="table-wrap">
                        <table aria-label="知识库文档列表">
                          <thead><tr><th>文档名称</th><th>处理状态</th><th>处理证据</th><th>下一步操作</th></tr></thead>
                          <tbody>{documents.slice((documentPage - 1) * documentPageSize, documentPage * documentPageSize).map((document) => (
                            <tr key={document.documentId}>
                              <td tabIndex={-1} data-document-focus-id={document.documentId}>{renderCell(document.fileName)}</td>
                              <td>{documentStatusLabel(document.status)}</td>
                              <td>
                                {document.status === 'FAILED' ? (
                                  <div>
                                    <Text>作业 {document.jobId || '未提供'} · {document.jobType || '未提供'} · {document.jobStatus || '未提供'}</Text>
                                    <Text>{document.failureCode ? `${document.failureCode}：` : ''}{document.failureSummary || '后端未提供可披露摘要。'}</Text>
                                    <Text>Trace {document.traceId || '未提供'}</Text>
                                  </div>
                                ) : document.status === 'CANCELLED' ? (
                                  <div>
                                    <Text>原因：{document.cancellationReason}</Text>
                                    <Text>操作人：{document.cancelledBy}</Text>
                                    <Text>取消时间：{document.cancelledAt}</Text>
                                    <Text>Trace {document.cancellationTraceId}</Text>
                                  </div>
                                ) : <Text>无</Text>}
                              </td>
                              <td>
                                <div className="row-actions">
                                  {document.status === 'UPLOADED' && canParse && <Button disabled={working} onClick={() => documentAction(document.documentId, 'parse')}>解析</Button>}
                                  {document.status === 'CHUNKED' && canIndex && <Button disabled={working} onClick={() => documentAction(document.documentId, 'index')}>建立索引</Button>}
                                  {document.status === 'FAILED' && authorizedRecoveryAction(document) && (
                                    <Button
                                      disabled={working}
                                      aria-label={`${recoveryLabel(authorizedRecoveryAction(document)!)} ${document.fileName}`}
                                      data-recovery-document-id={document.documentId}
                                      onClick={() => openManualRecovery(document)}
                                    >
                                      {recoveryLabel(authorizedRecoveryAction(document)!)}
                                    </Button>
                                  )}
                                  {document.status === 'FAILED' && !authorizedRecoveryAction(document) && <Text>需平台管理员处理</Text>}
                                  {document.cancelEligible && canCancel && (
                                    <Button
                                      disabled={working}
                                      aria-label={`取消处理 ${document.fileName}`}
                                      data-recovery-document-id={document.documentId}
                                      onClick={() => openCancellation(document)}
                                    >
                                      取消处理
                                    </Button>
                                  )}
                                  {document.status === 'AVAILABLE' && <Text>无需操作</Text>}
                                  {document.status === 'CANCELLED' && <Text>可按需要重新上传</Text>}
                                  {['PARSING', 'VECTORIZING'].includes(document.status) && <Text>等待后台处理</Text>}
                                </div>
                              </td>
                            </tr>
                          ))}</tbody>
                        </table>
                      </div>
                      <Pagination
                        page={documentPage}
                        pageSize={documentPageSize}
                        total={documents.length}
                        onChange={(nextPage, nextSize) => { setDocumentPage(nextPage); setDocumentPageSize(nextSize); }}
                      />
                    </div>
                  ) : null}
                </div>
              )}
            </DialogContent>
            <DialogActions>
              {panel === 'create' ? (
                <>
                  <Button disabled={working} onClick={closePanel}>取消</Button>
                  <Button appearance="primary" disabled={working || !name.trim() || !embeddingModel} onClick={create}>创建</Button>
                </>
              ) : manualRecoveryTarget ? (
                <>
                  <Button disabled={working} onClick={cancelManualRecovery}>取消</Button>
                  <Button appearance="primary" disabled={working} onClick={() => void confirmManualRecovery()}>
                    确认{recoveryLabel(manualRecoveryTarget.action)}
                  </Button>
                </>
              ) : (
                <Button disabled={working} onClick={closePanel}>关闭</Button>
              )}
            </DialogActions>
          </DialogBody>
        </DialogSurface>
      </Dialog>

      <Dialog
        open={Boolean(cancellationTarget)}
        surfaceMotion={dialogSurfaceMotion}
        onOpenChange={(_, data) => {
          if (!data.open && !working) closeCancellation();
        }}
      >
        <DialogSurface backdropMotion={dialogBackdropMotion}>
          <DialogBody>
            <DialogTitle>确认取消文档处理</DialogTitle>
            <DialogContent>
              {cancellationTarget && (
                <div className="dialog-form">
                  <Text weight="semibold">{cancellationTarget.fileName}</Text>
                  <Text>取消仅作用于当前处理作业；已完成的迟到结果会被租约与 fence 拒绝。</Text>
                  <MessageBar intent="warning">
                    <MessageBarBody>取消后该文档不会进入可检索状态；已经激活的其他文档和旧索引不受影响。</MessageBarBody>
                  </MessageBar>
                  <Text>作业 {cancellationTarget.jobId} · {cancellationTarget.jobType} · {cancellationTarget.jobStatus}</Text>
                  <Text>当前文档状态：{documentStatusLabel(cancellationTarget.status)}</Text>
                  <Text>Trace {cancellationTarget.traceId || '未提供'}</Text>
                  <Field label="取消理由" required hint="说明取消原因，3 到 300 个字符；该内容会进入审计记录。">
                    <Textarea
                      aria-label="取消理由"
                      autoFocus
                      value={cancellationReason}
                      maxLength={300}
                      onChange={(_, data) => {
                        setCancellationReason(data.value);
                        setCancellationError(undefined);
                      }}
                    />
                  </Field>
                  {cancellationError && (
                    <MessageBar intent="error">
                      <MessageBarBody>
                        {cancellationError.message}
                        {cancellationErrorTraceId ? `（Trace ${cancellationErrorTraceId}）` : ''}
                      </MessageBarBody>
                    </MessageBar>
                  )}
                </div>
              )}
            </DialogContent>
            <DialogActions>
              <Button disabled={working} onClick={closeCancellation}>返回</Button>
              <Button
                appearance="primary"
                disabled={working || cancellationReasonLength < 3 || cancellationReasonLength > 300}
                onClick={() => void confirmCancellation()}
              >
                确认取消处理
              </Button>
            </DialogActions>
          </DialogBody>
        </DialogSurface>
      </Dialog>
    </main>
  );
}
