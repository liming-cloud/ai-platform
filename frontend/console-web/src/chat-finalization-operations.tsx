import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Badge,
  Button,
  Dialog,
  DialogActions,
  DialogBody,
  DialogContent,
  DialogSurface,
  DialogTitle,
  Field,
  MessageBar,
  MessageBarBody,
  Text,
  Textarea,
  Title3,
} from '@fluentui/react-components';
import { ArrowClockwise20Regular, Warning20Regular } from '@fluentui/react-icons';
import { AsyncState } from './async-state';
import {
  listChatRunDeadLetters,
  replayChatRunDeadLetter,
  type ChatRunDeadLetterItem,
  type ChatRunManualReplayResult,
} from './api';
import { dialogBackdropMotion, dialogSurfaceMotion } from './dialog-motion';
import type { CurrentUser, RequestStatus } from './types';

const MAX_CYCLE_ATTEMPTS = 3;
const REQUIRED_PERMISSIONS = [
  'app-studio:application:publish',
  'app-studio:application:manage-acl',
] as const;

type ReplayDraft = {
  identityKey: string;
  tenantId: string;
  replayRequestId: string;
  run: ChatRunDeadLetterItem;
};

type ReplayOutcome = {
  identityKey: string;
  response: ChatRunManualReplayResult;
};

function errorTraceId(error?: Error) {
  if (!error || !('traceId' in error)) return '';
  return String((error as Error & { traceId?: string }).traceId || '');
}

function formatFailureTime(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
    hour12: false,
  }).format(parsed);
}

function replayOutcomeTitle(status: string) {
  if (status === 'COMPLETED') return '重放已完成';
  if (status === 'RETRY_SCHEDULED') return '重放已进入受控重试';
  if (status === 'DEAD_LETTER') return '重放后仍需人工处理';
  return '重放请求已受理';
}

export function ChatFinalizationOperations({ user }: { user: CurrentUser }) {
  const identityKey = `${user.tenantId}:${user.userId}`;
  const missingPermissions = useMemo(
    () => REQUIRED_PERMISSIONS.filter((permission) => !user.permissionCodes.includes(permission)),
    [user.permissionCodes],
  );
  const canOperate = missingPermissions.length === 0;
  const [rows, setRows] = useState<ChatRunDeadLetterItem[]>([]);
  const [rowsIdentityKey, setRowsIdentityKey] = useState('');
  const [status, setStatus] = useState<RequestStatus>(canOperate ? 'loading' : 'idle');
  const [loadError, setLoadError] = useState<Error>();
  const [draft, setDraft] = useState<ReplayDraft>();
  const [reason, setReason] = useState('');
  const [replayError, setReplayError] = useState<Error>();
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<ReplayOutcome>();
  const loadController = useRef<AbortController>();
  const loadGeneration = useRef(0);
  const replayController = useRef<AbortController>();
  const currentIdentityKey = useRef(identityKey);
  currentIdentityKey.current = identityKey;

  const load = useCallback(async () => {
    if (!canOperate) return;
    loadController.current?.abort();
    const controller = new AbortController();
    const generation = loadGeneration.current + 1;
    loadController.current = controller;
    loadGeneration.current = generation;
    setRowsIdentityKey(identityKey);
    setRows([]);
    setStatus('loading');
    setLoadError(undefined);
    try {
      const nextRows = await listChatRunDeadLetters(20, controller.signal);
      if (controller.signal.aborted || generation !== loadGeneration.current) return;
      setRows(nextRows);
      setStatus(nextRows.length ? 'success' : 'empty');
    } catch (cause) {
      if (controller.signal.aborted || generation !== loadGeneration.current) return;
      setLoadError(cause as Error);
      setStatus('error');
    }
  }, [canOperate, identityKey]);

  useEffect(() => {
    replayController.current?.abort();
    setDraft(undefined);
    setReason('');
    setReplayError(undefined);
    setSubmitting(false);
    setResult((current) => current?.identityKey === identityKey ? current : undefined);
    if (!canOperate) {
      loadController.current?.abort();
      setRows([]);
      setRowsIdentityKey(identityKey);
      setStatus('idle');
      return undefined;
    }
    void load();
    return () => {
      loadController.current?.abort();
      replayController.current?.abort();
    };
  }, [canOperate, identityKey, load]);

  function openReplay(run: ChatRunDeadLetterItem) {
    if (!canOperate) return;
    setDraft({
      identityKey,
      tenantId: user.tenantId,
      replayRequestId: crypto.randomUUID(),
      run: { ...run },
    });
    setReason('');
    setReplayError(undefined);
    setResult(undefined);
  }

  function closeReplay() {
    if (submitting) return;
    setDraft(undefined);
    setReason('');
    setReplayError(undefined);
  }

  async function confirmReplay() {
    const frozen = draft;
    const auditedReason = reason.trim();
    if (!frozen || frozen.identityKey !== identityKey || !auditedReason || submitting) return;
    replayController.current?.abort();
    const controller = new AbortController();
    replayController.current = controller;
    setSubmitting(true);
    setReplayError(undefined);
    try {
      const replayResult = await replayChatRunDeadLetter(
        frozen.run.runId,
        frozen.replayRequestId,
        auditedReason,
        controller.signal,
      );
      if (controller.signal.aborted
        || currentIdentityKey.current !== frozen.identityKey
        || draft?.replayRequestId !== frozen.replayRequestId) return;
      setResult({ identityKey: frozen.identityKey, response: replayResult });
      setDraft(undefined);
      setReason('');
      await load();
    } catch (cause) {
      if (!controller.signal.aborted
        && currentIdentityKey.current === frozen.identityKey
        && draft?.replayRequestId === frozen.replayRequestId) {
        setReplayError(cause as Error);
      }
    } finally {
      if (currentIdentityKey.current === frozen.identityKey
        && draft?.replayRequestId === frozen.replayRequestId) setSubmitting(false);
    }
  }

  const activeDraft = draft?.identityKey === identityKey ? draft : undefined;
  const visibleRows = rowsIdentityKey === identityKey ? rows : [];
  const visibleStatus = rowsIdentityKey === identityKey ? status : (canOperate ? 'loading' : 'idle');
  const activeResult = result?.identityKey === identityKey ? result.response : undefined;
  const replayTraceId = errorTraceId(replayError);
  const loadTraceId = errorTraceId(loadError);
  const outcomeIntent = activeResult?.status === 'DEAD_LETTER' ? 'warning' : 'success';

  return <section className="surface finalization-operations" aria-labelledby="chat-finalization-heading">
    <header className="section-header finalization-header">
      <div>
        <Title3 as="h2" id="chat-finalization-heading">聊天最终化恢复</Title3>
        <Text>仅处理当前租户已完成模型调用、但最终结果事务提交失败的运行。</Text>
      </div>
      <div className="finalization-header-actions">
        <Badge appearance="tint">当前租户 {user.tenantId}</Badge>
        <Button
          icon={<ArrowClockwise20Regular />}
          disabled={!canOperate || visibleStatus === 'loading'}
          onClick={() => { void load(); }}
        >刷新死信</Button>
      </div>
    </header>

    {!canOperate && <div className="finalization-boundary">
      <MessageBar intent="warning">
        <MessageBarBody>
          死信列表与人工重放已禁用。当前账号必须同时具备发布和访问控制管理权限。
        </MessageBarBody>
      </MessageBar>
      <ul className="permission-missing-list" aria-label="缺失权限">
        {missingPermissions.map((permission) => <li key={permission}>{`缺少 ${permission}`}</li>)}
      </ul>
    </div>}

    {activeResult && <MessageBar intent={outcomeIntent}>
      <MessageBarBody>
        <strong>{replayOutcomeTitle(activeResult.status)}</strong>
        <span className="finalization-result-detail">
          运行 {activeResult.runId}，状态 {activeResult.status}。已按幂等键受理，重放请求 ID：{activeResult.replayRequestId}
        </span>
      </MessageBarBody>
    </MessageBar>}

    {canOperate && visibleStatus === 'loading' && <div className="finalization-state">
      <AsyncState status="loading" loadingLabel="正在读取当前租户最终化死信" />
    </div>}

    {canOperate && visibleStatus === 'error' && <div className="finalization-state">
      <MessageBar intent="error" role="alert">
        <MessageBarBody>
          <div className="async-state-error">
            <span>死信列表加载失败：{loadError?.message || '请稍后重试。'}{loadTraceId && <span className="trace-hint">错误追踪：{loadTraceId}</span>}</span>
            <Button size="small" appearance="transparent" onClick={() => { void load(); }}>重试加载</Button>
          </div>
        </MessageBarBody>
      </MessageBar>
    </div>}

    {canOperate && visibleStatus === 'empty' && <div className="finalization-empty" role="status">
      <Title3>当前没有等待人工处理的最终化死信</Title3>
      <Text>系统会继续执行有界自动恢复；只有耗尽失败周期的运行才会出现在这里。</Text>
    </div>}

    {canOperate && visibleRows.length > 0 && <div className="table-wrap finalization-table-wrap">
      <table className="finalization-table" aria-label="聊天最终化死信列表">
        <thead><tr><th>运行</th><th>应用与会话</th><th>失败周期</th><th>失败原因</th><th>最近失败与追踪</th><th>操作</th></tr></thead>
        <tbody>{visibleRows.map((run) => <tr key={run.runId}>
          <td><strong className="cell-primary">{run.runId}</strong><span className="cell-secondary">人工重放 {run.manualReplayCount} 次</span></td>
          <td><strong className="cell-primary">{run.applicationId}</strong><span className="cell-secondary">{run.sessionId}</span></td>
          <td><strong className="cell-primary">第 {run.finalizationCycleAttemptCount} / {MAX_CYCLE_ATTEMPTS} 次</strong><span className="cell-secondary">累计 {run.finalizationAttemptCount} 次</span></td>
          <td><strong className="cell-primary">{run.failureCode}</strong><span className="cell-secondary">{run.failureSummary}</span></td>
          <td><time dateTime={run.updatedAt}>{formatFailureTime(run.updatedAt)}</time><code className="cell-secondary trace-code">{run.failureTraceId}</code></td>
          <td><Button size="small" aria-label={`重放 ${run.runId}`} onClick={() => openReplay(run)}>人工重放</Button></td>
        </tr>)}</tbody>
      </table>
    </div>}

    <Dialog open={Boolean(activeDraft)} surfaceMotion={dialogSurfaceMotion} onOpenChange={(_, data) => { if (!data.open) closeReplay(); }}>
      <DialogSurface backdropMotion={dialogBackdropMotion}>
        <DialogBody>
          <DialogTitle>确认人工重放</DialogTitle>
          <DialogContent>{activeDraft && <div className="dialog-form risk-confirmation">
            <dl className="change-summary">
              <dt>当前租户</dt><dd>{activeDraft.tenantId}</dd>
              <dt>运行 ID</dt><dd>{activeDraft.run.runId}</dd>
              <dt>应用 ID</dt><dd>{activeDraft.run.applicationId}</dd>
              <dt>会话 ID</dt><dd>{activeDraft.run.sessionId}</dd>
              <dt>失败周期</dt><dd>第 {activeDraft.run.finalizationCycleAttemptCount} / {MAX_CYCLE_ATTEMPTS} 次</dd>
              <dt>重放请求 ID</dt><dd>{activeDraft.replayRequestId}</dd>
            </dl>
            <MessageBar intent="warning" icon={<Warning20Regular />}>
              <MessageBarBody>只重放已保存的最终化事务，不会再次调用模型、检索或策略链。</MessageBarBody>
            </MessageBar>
            <Field
              label="重放原因"
              hint="说明故障已完成的处置和本次重放依据。该理由将进入审计记录。"
              required
            >
              <Textarea maxLength={512} value={reason} onChange={(_, data) => setReason(data.value)} />
            </Field>
            {replayError && <MessageBar intent="error" role="alert">
              <MessageBarBody>
                {replayError.message}
                {replayTraceId && <span className="trace-hint">错误追踪：{replayTraceId}</span>}
                <span className="trace-hint">请保留当前对话框并使用同一重放请求 ID 重试，避免重复执行。</span>
              </MessageBarBody>
            </MessageBar>}
          </div>}</DialogContent>
          <DialogActions>
            <Button disabled={submitting} onClick={closeReplay}>取消</Button>
            <Button appearance="primary" disabled={submitting || !reason.trim()} onClick={() => { void confirmReplay(); }}>
              {submitting ? '正在重放' : '确认重放'}
            </Button>
          </DialogActions>
        </DialogBody>
      </DialogSurface>
    </Dialog>
  </section>;
}
