import { useEffect, useRef, useState } from 'react';
import {
  Badge,
  Button,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  Text,
  Textarea,
  Title3,
} from '@fluentui/react-components';
import {
  createChatSession,
  createPermissionRequest,
  getDebugEvidence,
  listAiApplications,
  listChatMessages,
  listResource,
  sendChatMessage,
  submitChatMessageFeedback,
} from './api';
import { renderCell } from './page-format';
import { PageHeading, RequestFeedback } from './page-shared';
import type { Navigate } from './page-shared';
import type { AiApplicationItem, ApiError, ChatCitation, ChatMessageHistoryItem, CurrentUser, PermissionRequestItem, RequestStatus, SendChatMessageResult } from './types';

function useChatSessionStarter(navigate: Navigate) {
  const [startingId, setStartingId] = useState<string>();
  const [actionError, setActionError] = useState<Error>();
  const controllerRef = useRef<AbortController>();
  const generationRef = useRef(0);

  useEffect(() => () => {
    generationRef.current += 1;
    controllerRef.current?.abort();
    controllerRef.current = undefined;
  }, []);

  async function start(applicationId: string) {
    controllerRef.current?.abort();
    const controller = new AbortController();
    const generation = generationRef.current + 1;
    generationRef.current = generation;
    controllerRef.current = controller;
    setStartingId(applicationId);
    setActionError(undefined);
    try {
      const created = await createChatSession(applicationId, controller.signal);
      if (controller.signal.aborted || generation !== generationRef.current
        || controllerRef.current !== controller) return;
      navigate(`/workspace/sessions/${created.sessionId}`);
    } catch (reason) {
      if (controller.signal.aborted || isAbortError(reason) || generation !== generationRef.current
        || controllerRef.current !== controller) return;
      setActionError(reason instanceof Error ? reason : new Error('会话创建失败'));
    } finally {
      if (generation === generationRef.current && controllerRef.current === controller) {
        controllerRef.current = undefined;
        setStartingId(undefined);
      }
    }
  }

  return { actionError, start, startingId };
}

export function WorkspaceHome({ user, navigate }: { user: CurrentUser; navigate: Navigate }) {
  const [applications, setApplications] = useState<AiApplicationItem[]>([]);
  const [status, setStatus] = useState<RequestStatus>('loading');
  const [error, setError] = useState<Error>();
  const [loadedAt, setLoadedAt] = useState<Date>();
  const [attempt, setAttempt] = useState(0);
  const { actionError, start, startingId } = useChatSessionStarter(navigate);

  useEffect(() => {
    const controller = new AbortController();
    setApplications([]);
    setLoadedAt(undefined);
    setError(undefined);
    setStatus('loading');
    listAiApplications(controller.signal)
      .then((value) => {
        if (controller.signal.aborted) return;
        if (!Array.isArray(value)) throw new Error('服务返回了无法识别的应用列表');
        setApplications(value);
        setLoadedAt(new Date());
        setStatus(value.length > 0 ? 'success' : 'empty');
      })
      .catch((reason: unknown) => {
        if (controller.signal.aborted || (reason instanceof Error && reason.name === 'AbortError')) return;
        setError(reason instanceof Error ? reason : new Error('应用列表读取失败'));
        setStatus('error');
      });
    return () => controller.abort();
  }, [attempt]);

  return <main className="page-stack">
    <PageHeading title={`你好，${user.displayName}`} description="从服务端已授权的 AI 应用直接开始当前工作。" />
    <RequestFeedback status={status} error={error} onRetry={() => setAttempt((value) => value + 1)} loadingLabel="正在读取已授权应用" />
    {actionError && <MessageBar intent="error"><MessageBarBody>{actionError.message}</MessageBarBody></MessageBar>}
    {status === 'empty' && <section className="empty-state"><Title3>暂无可开始的工作</Title3><Text>当前账号没有已授权的 AI 应用，请联系租户管理员。</Text></section>}
    {applications.length > 0 && <section className="surface workspace-workbench" aria-labelledby="authorized-applications-title">
      <div className="workbench-section-header workspace-workbench-header">
        <div><Title3 as="h2" id="authorized-applications-title">可开始的工作</Title3><Text>仅显示当前服务端返回的应用和状态。</Text></div>
        {loadedAt && <Text>读取时间 {loadedAt.toLocaleString('zh-CN')}</Text>}
      </div>
      <div className="application-task-list">{applications.slice(0, 5).map((application) => <article className="application-task-row" key={application.applicationId}>
        <div className="application-task-copy"><strong>{application.name}</strong><Text>{application.description || '服务端未提供应用说明'}</Text><div className="application-task-meta"><Badge appearance="outline">{application.status}</Badge><span>{application.applicationType}</span></div></div>
        <Button appearance="primary" aria-label={`使用 ${application.name}`} disabled={startingId === application.applicationId} onClick={() => { void start(application.applicationId); }}>{startingId === application.applicationId ? '正在创建会话' : '开始使用'}</Button>
      </article>)}</div>
      <div className="workbench-footer"><Text>当前共 {applications.length} 个授权应用</Text><Button appearance="subtle" onClick={() => navigate('/workspace/apps')}>查看全部应用</Button></div>
    </section>}
  </main>;
}

export function ApplicationCatalogPage({ navigate }: { navigate: Navigate }) {
  const [applications, setApplications] = useState<Record<string, unknown>[]>([]);
  const [status, setStatus] = useState<RequestStatus>('loading');
  const [error, setError] = useState<Error>();
  const [attempt, setAttempt] = useState(0);
  const { actionError, start, startingId } = useChatSessionStarter(navigate);

  useEffect(() => {
    const controller = new AbortController();
    setApplications([]);
    setError(undefined);
    setStatus('loading');
    listResource('/api/v1/ai-applications', controller.signal)
      .then((value) => {
        if (controller.signal.aborted) return;
        setApplications(value);
        setStatus(value.length ? 'success' : 'empty');
      })
      .catch((reason: unknown) => {
        if (controller.signal.aborted || (reason instanceof Error && reason.name === 'AbortError')) return;
        setError(reason instanceof Error ? reason : new Error('应用目录读取失败'));
        setStatus('error');
      });
    return () => controller.abort();
  }, [attempt]);

  return <main className="page-stack">
    <PageHeading title="AI 应用" description="选择服务端已授权且已发布的应用，创建仅属于当前用户的新会话。" />
    <RequestFeedback status={status} error={error} onRetry={() => setAttempt((value) => value + 1)} loadingLabel="正在读取已授权应用" />
    {actionError && <MessageBar intent="error"><MessageBarBody>{actionError.message}</MessageBarBody></MessageBar>}
    {status === 'empty' && <section className="empty-state"><Title3>暂无可用应用</Title3><Text>请联系租户管理员完成应用发布和使用授权。</Text></section>}
    {status === 'success' && applications.length > 0 && <section className="application-grid">{applications.map((application) => {
      const id = String(application.applicationId || application.id);
      return <article className="application-item" key={id}><div><span className="surface-metadata">{renderCell(application.applicationType || '类型未提供')}</span><Title3 as="h2">{renderCell(application.name || application.applicationName)}</Title3><Text>{renderCell(application.description)}</Text></div><div className="application-meta"><span>{renderCell(application.applicationCode || application.code)}</span><Button appearance="primary" disabled={startingId === id} onClick={() => { void start(id); }}>{startingId === id ? '正在创建会话' : '开始使用'}</Button></div></article>;
    })}</section>}
  </main>;
}

interface ChatEvidence {
  assistantMessageId: string;
  sessionId: string;
  applicationId: string;
  citations: ChatCitation[];
  citationCount: number;
  filteredCitationCount?: number;
  traceId?: string;
  modelInvocationLogId?: string;
}

interface ConversationMessage {
  messageId?: string;
  type: 'user' | 'assistant';
  text: string;
  clientMessageId?: string;
  question?: string;
  response?: ChatEvidence;
}

interface PendingChatCommand {
  sessionId: string;
  clientMessageId: string;
  content: string;
}

interface PermissionTarget {
  resourceType: string;
  resourceId: string;
  permissionCode: string;
}

interface SubmittedPermissionReceipt extends PermissionTarget {
  requestId: string;
  status: PermissionRequestItem['status'];
}

const REQUESTABLE_AUTHORIZATION_CODE = 'AIP-IAM-AUTHZ-5003-REQUESTABLE';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function requiredText(value: unknown, fieldName: string) {
  if (typeof value !== 'string' || !value.trim()) throw new Error(`历史消息缺少${fieldName}`);
  return value.trim();
}

function optionalText(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function parseCitation(value: unknown): ChatCitation {
  if (!isRecord(value)) throw new Error('历史消息包含无法识别的引用');
  const score = value.score;
  if (typeof score !== 'number' || !Number.isFinite(score)) throw new Error('历史引用缺少可信度');
  return {
    knowledgeBaseId: requiredText(value.knowledgeBaseId, '知识库 ID'),
    documentId: requiredText(value.documentId, '文档 ID'),
    chunkId: requiredText(value.chunkId, '片段 ID'),
    title: requiredText(value.title, '引用标题'),
    snippet: requiredText(value.snippet, '引用片段'),
    locator: requiredText(value.locator, '引用位置'),
    score,
  };
}

function restoreHistory(history: Record<string, unknown>[], targetSession: string): ConversationMessage[] {
  const restored: ConversationMessage[] = [];
  let previousQuestion: string | undefined;
  history.forEach((value) => {
    const item = value as Partial<ChatMessageHistoryItem>;
    const messageId = requiredText(item.messageId, '消息 ID');
    const itemSessionId = requiredText(item.sessionId, '会话 ID');
    if (itemSessionId !== targetSession) throw new Error('历史消息与当前会话不一致');
    const role = requiredText(item.role, '消息角色');
    const content = requiredText(item.content, '消息正文');
    if (role === 'USER') {
      previousQuestion = content;
      restored.push({ messageId, type: 'user', text: content });
      return;
    }
    if (role !== 'ASSISTANT') throw new Error('历史消息包含无法识别的角色');
    if (!Array.isArray(item.citations)) throw new Error('助手历史消息缺少可见引用详情');
    const citations = item.citations.map(parseCitation);
    if (!Number.isInteger(item.citationCount) || Number(item.citationCount) < 0
      || item.citationCount !== citations.length) throw new Error('助手历史消息引用数量不一致');
    restored.push({
      messageId,
      type: 'assistant',
      text: content,
      question: previousQuestion,
      response: {
        assistantMessageId: messageId,
        sessionId: itemSessionId,
        applicationId: requiredText(item.applicationId, '应用 ID'),
        citations,
        citationCount: item.citationCount,
        traceId: optionalText(item.traceId),
        modelInvocationLogId: optionalText(item.modelInvocationLogId),
      },
    });
  });
  return restored;
}

function evidenceFromSend(response: SendChatMessageResult): ChatEvidence {
  return {
    assistantMessageId: response.assistantMessageId,
    sessionId: response.sessionId,
    applicationId: response.applicationId,
    citations: response.citations,
    citationCount: response.citations.length,
    filteredCitationCount: response.filteredCitationCount,
    traceId: response.traceId,
    modelInvocationLogId: response.modelInvocationLogId,
  };
}

function requestableTarget(error: ApiError): PermissionTarget | undefined {
  if (error.code !== REQUESTABLE_AUTHORIZATION_CODE || !isRecord(error.body)) return undefined;
  const resourceType = optionalText(error.body.resourceType);
  const resourceId = optionalText(error.body.resourceId);
  const permissionCode = optionalText(error.body.permissionCode);
  if (resourceType !== 'AI_APPLICATION' || !resourceId || permissionCode !== 'app-studio:application:use') return undefined;
  return { resourceType, resourceId, permissionCode };
}

function isRetryableChatFailure(reason: unknown) {
  const error = reason as Partial<ApiError> | undefined;
  if (error?.code === 'AIP-APP-CHAT-SESSION-CONFLICT') return false;
  const status = error?.status;
  return status === undefined || status === 0 || status === 409 || status === 429 || status >= 500;
}

function isAbortError(reason: unknown) {
  return reason instanceof Error && reason.name === 'AbortError';
}

export function ChatPage({ sessionId, navigate }: { sessionId?: string; navigate?: Navigate }) {
  const [currentSession, setCurrentSession] = useState(sessionId || '');
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<ConversationMessage[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<ApiError>();
  const [failedCommand, setFailedCommand] = useState<PendingChatCommand>();
  const [debugEvidence, setDebugEvidence] = useState<Record<string, unknown>>();
  const [debugLoadingId, setDebugLoadingId] = useState<string>();
  const [feedbackByMessage, setFeedbackByMessage] = useState<Record<string, 'HELPFUL' | 'UNHELPFUL'>>({});
  const [feedbackLoadingId, setFeedbackLoadingId] = useState<string>();
  const [permissionTarget, setPermissionTarget] = useState<PermissionTarget>();
  const [permissionReason, setPermissionReason] = useState('');
  const [permissionRequestStatus, setPermissionRequestStatus] = useState<'idle' | 'submitting'>('idle');
  const [submittedPermissionRequest, setSubmittedPermissionRequest] = useState<SubmittedPermissionReceipt>();
  const [historyStatus, setHistoryStatus] = useState<RequestStatus>(sessionId ? 'loading' : 'idle');
  const [historyError, setHistoryError] = useState<ApiError>();
  const [historyAttempt, setHistoryAttempt] = useState(0);
  const sessionGeneration = useRef(0);
  const activeSessionRef = useRef(sessionId || '');
  const sendControllerRef = useRef<AbortController>();
  const debugControllerRef = useRef<AbortController>();
  const feedbackControllerRef = useRef<AbortController>();
  const permissionControllerRef = useRef<AbortController>();

  useEffect(() => {
    const targetSession = sessionId || '';
    const historyController = new AbortController();
    const generation = sessionGeneration.current + 1;
    sessionGeneration.current = generation;
    sendControllerRef.current?.abort();
    debugControllerRef.current?.abort();
    feedbackControllerRef.current?.abort();
    permissionControllerRef.current?.abort();
    sendControllerRef.current = undefined;
    debugControllerRef.current = undefined;
    feedbackControllerRef.current = undefined;
    permissionControllerRef.current = undefined;
    activeSessionRef.current = targetSession;
    setCurrentSession(targetSession);
    setQuestion('');
    setMessages([]);
    setSending(false);
    setError(undefined);
    setFailedCommand(undefined);
    setDebugEvidence(undefined);
    setDebugLoadingId(undefined);
    setFeedbackByMessage({});
    setFeedbackLoadingId(undefined);
    setPermissionTarget(undefined);
    setPermissionReason('');
    setPermissionRequestStatus('idle');
    setSubmittedPermissionRequest(undefined);
    setHistoryStatus(targetSession ? 'loading' : 'idle');
    setHistoryError(undefined);
    const cleanup = () => {
      historyController.abort();
      if (sessionGeneration.current !== generation) return;
      sendControllerRef.current?.abort();
      debugControllerRef.current?.abort();
      feedbackControllerRef.current?.abort();
      permissionControllerRef.current?.abort();
    };
    if (!targetSession) return cleanup;
    listChatMessages(targetSession, historyController.signal)
      .then((history) => {
        if (historyController.signal.aborted
          || generation !== sessionGeneration.current
          || activeSessionRef.current !== targetSession) return;
        setMessages(restoreHistory(history, targetSession));
        setHistoryStatus('success');
      })
      .catch((reason) => {
        if (!historyController.signal.aborted
          && generation === sessionGeneration.current
          && activeSessionRef.current === targetSession) {
          setHistoryError(reason as ApiError);
          setHistoryStatus('error');
        }
      });
    return cleanup;
  }, [historyAttempt, sessionId]);

  function resetForManualSession(nextSession: string) {
    sessionGeneration.current += 1;
    sendControllerRef.current?.abort();
    debugControllerRef.current?.abort();
    feedbackControllerRef.current?.abort();
    permissionControllerRef.current?.abort();
    sendControllerRef.current = undefined;
    debugControllerRef.current = undefined;
    feedbackControllerRef.current = undefined;
    permissionControllerRef.current = undefined;
    activeSessionRef.current = nextSession;
    setCurrentSession(nextSession);
    setQuestion('');
    setMessages([]);
    setSending(false);
    setError(undefined);
    setFailedCommand(undefined);
    setDebugEvidence(undefined);
    setDebugLoadingId(undefined);
    setFeedbackByMessage({});
    setFeedbackLoadingId(undefined);
    setPermissionTarget(undefined);
    setPermissionReason('');
    setPermissionRequestStatus('idle');
    setSubmittedPermissionRequest(undefined);
    setHistoryStatus('idle');
    setHistoryError(undefined);
  }

  function showRequestableTarget(requestError: ApiError) {
    const target = requestableTarget(requestError);
    const alreadySubmitted = target && submittedPermissionRequest
      && target.resourceType === submittedPermissionRequest.resourceType
      && target.resourceId === submittedPermissionRequest.resourceId
      && target.permissionCode === submittedPermissionRequest.permissionCode;
    setPermissionTarget(alreadySubmitted ? undefined : target);
    setPermissionReason('');
    setPermissionRequestStatus('idle');
  }

  async function executeCommand(command: PendingChatCommand, appendUserMessage: boolean) {
    if (sendControllerRef.current) return;
    const controller = new AbortController();
    sendControllerRef.current = controller;
    const generation = sessionGeneration.current;
    if (appendUserMessage) {
      setMessages((value) => [...value, {
        type: 'user',
        text: command.content,
        clientMessageId: command.clientMessageId,
      }]);
    }
    setFailedCommand(undefined);
    setSending(true);
    setError(undefined);
    setDebugEvidence(undefined);
    setPermissionTarget(undefined);
    setPermissionReason('');
    setPermissionRequestStatus('idle');
    try {
      const response = await sendChatMessage(command.sessionId, command.clientMessageId, command.content, controller.signal);
      if (controller.signal.aborted || generation !== sessionGeneration.current
        || activeSessionRef.current !== command.sessionId) return;
      const answer = response.answer || response.noAnswerReason || '当前知识范围不足以生成回答。';
      setMessages((value) => [...value, {
        messageId: response.assistantMessageId,
        type: 'assistant',
        text: answer,
        question: command.content,
        response: evidenceFromSend(response),
      }]);
    } catch (reason) {
      if (controller.signal.aborted || isAbortError(reason) || generation !== sessionGeneration.current
        || activeSessionRef.current !== command.sessionId) return;
      const requestError = reason as ApiError;
      setError(requestError);
      if (isRetryableChatFailure(reason)) {
        setFailedCommand(command);
      } else {
        setMessages((value) => value.filter((message) => message.clientMessageId !== command.clientMessageId));
        setQuestion(command.content);
      }
      showRequestableTarget(requestError);
    } finally {
      if (sendControllerRef.current === controller) sendControllerRef.current = undefined;
      if (generation === sessionGeneration.current && activeSessionRef.current === command.sessionId) setSending(false);
    }
  }

  function send(retryQuestion?: string) {
    const content = retryQuestion?.trim() || question.trim();
    const requestedSession = (sessionId || activeSessionRef.current || currentSession).trim();
    const historyReady = !sessionId
      || (historyStatus === 'success' && activeSessionRef.current === sessionId);
    if (!requestedSession || !content || failedCommand || !historyReady || sendControllerRef.current) return;
    if (!retryQuestion) setQuestion('');
    void executeCommand({
      sessionId: requestedSession,
      clientMessageId: crypto.randomUUID(),
      content,
    }, true);
  }

  function retryFailedCommand() {
    if (failedCommand) void executeCommand(failedCommand, false);
  }

  function discardFailedCommand() {
    if (!failedCommand) return;
    setMessages((value) => value.filter((message) => message.clientMessageId !== failedCommand.clientMessageId));
    setQuestion(failedCommand.content);
    setFailedCommand(undefined);
    setError(undefined);
  }

  async function loadDebug(response: ChatEvidence) {
    debugControllerRef.current?.abort();
    const controller = new AbortController();
    debugControllerRef.current = controller;
    const generation = sessionGeneration.current;
    const targetSession = response.sessionId;
    setDebugLoadingId(response.assistantMessageId);
    setDebugEvidence(undefined);
    setError(undefined);
    setPermissionTarget(undefined);
    setPermissionReason('');
    setPermissionRequestStatus('idle');
    try {
      const evidence = await getDebugEvidence(response.applicationId, response.assistantMessageId, controller.signal);
      if (controller.signal.aborted || generation !== sessionGeneration.current
        || activeSessionRef.current !== targetSession) return;
      setDebugEvidence(evidence);
    } catch (reason) {
      if (controller.signal.aborted || isAbortError(reason) || generation !== sessionGeneration.current
        || activeSessionRef.current !== targetSession) return;
      const requestError = reason as ApiError;
      setError(requestError);
      showRequestableTarget(requestError);
    } finally {
      if (debugControllerRef.current === controller) {
        debugControllerRef.current = undefined;
        if (generation === sessionGeneration.current && activeSessionRef.current === targetSession) setDebugLoadingId(undefined);
      }
    }
  }

  async function submitFeedback(response: ChatEvidence, rating: 'HELPFUL' | 'UNHELPFUL') {
    feedbackControllerRef.current?.abort();
    const controller = new AbortController();
    feedbackControllerRef.current = controller;
    const generation = sessionGeneration.current;
    const targetSession = response.sessionId;
    setFeedbackLoadingId(response.assistantMessageId);
    setError(undefined);
    try {
      const result = await submitChatMessageFeedback(
        response.sessionId,
        response.assistantMessageId,
        rating,
        controller.signal,
      );
      if (controller.signal.aborted || generation !== sessionGeneration.current
        || activeSessionRef.current !== targetSession) return;
      setFeedbackByMessage((value) => ({ ...value, [response.assistantMessageId]: result.rating }));
    } catch (reason) {
      if (!controller.signal.aborted && !isAbortError(reason) && generation === sessionGeneration.current
        && activeSessionRef.current === targetSession) setError(reason as ApiError);
    } finally {
      if (feedbackControllerRef.current === controller) {
        feedbackControllerRef.current = undefined;
        if (generation === sessionGeneration.current && activeSessionRef.current === targetSession) setFeedbackLoadingId(undefined);
      }
    }
  }

  async function requestPermission() {
    const reason = permissionReason.trim();
    if (!permissionTarget || !reason || permissionControllerRef.current) return;
    const target = permissionTarget;
    const controller = new AbortController();
    permissionControllerRef.current = controller;
    const generation = sessionGeneration.current;
    const targetSession = activeSessionRef.current;
    setPermissionRequestStatus('submitting');
    setError(undefined);
    try {
      const created = await createPermissionRequest({ ...target, reason }, controller.signal);
      if (controller.signal.aborted || generation !== sessionGeneration.current
        || activeSessionRef.current !== targetSession) return;
      setSubmittedPermissionRequest({
        ...target,
        requestId: created.requestId,
        status: created.status,
      });
      setPermissionTarget(undefined);
      setPermissionReason('');
      setPermissionRequestStatus('idle');
    } catch (reasonValue) {
      if (controller.signal.aborted || isAbortError(reasonValue) || generation !== sessionGeneration.current
        || activeSessionRef.current !== targetSession) return;
      setError(reasonValue as ApiError);
      setPermissionRequestStatus('idle');
    } finally {
      if (permissionControllerRef.current === controller) permissionControllerRef.current = undefined;
    }
  }

  const latestResponse = [...messages].reverse().find((message) => message.response)?.response;
  const releaseChanged = error?.code === 'AIP-APP-CHAT-SESSION-CONFLICT';
  const historyReady = !sessionId
    || (historyStatus === 'success' && activeSessionRef.current === sessionId);
  const sessionInputLocked = historyStatus === 'loading' || sending || Boolean(debugLoadingId)
    || Boolean(feedbackLoadingId) || permissionRequestStatus === 'submitting';
  return <main className="chat-layout"><section className="chat-main"><PageHeading title="RAG 对话" description="回答仅使用当前身份可访问的应用与知识范围。" />
    {!sessionId && <Field label="会话 ID" hint="从应用目录进入时会自动携带；也可输入已有会话继续。"><Input value={currentSession} disabled={sessionInputLocked} onChange={(_, data) => resetForManualSession(data.value)} /></Field>}
    <div className="message-stream" role="log" aria-label="会话消息" aria-live="polite" aria-relevant="additions text" aria-busy={sending || historyStatus === 'loading'}>{sessionId && historyStatus === 'loading' && <div className="chat-empty" role="status"><Title3>正在校验会话历史</Title3><Text>历史校验完成前不会允许发送新消息。</Text></div>}{sessionId && historyStatus === 'error' && historyError && <MessageBar intent="error" role="alert"><MessageBarBody><div className="chat-error"><span>{historyError.message}{historyError.traceId ? `（Trace ${historyError.traceId}）` : ''}</span><Button size="small" onClick={() => setHistoryAttempt((value) => value + 1)}>重新加载历史</Button></div></MessageBarBody></MessageBar>}{historyReady && messages.length === 0 && <div className="chat-empty"><Title3>开始一次可信问答</Title3><Text>敏感知识、不可见文档和其他用户会话不会进入你的回答。</Text></div>}{historyReady && messages.map((message, index) => <article className={`chat-message ${message.type}`} aria-label={message.type === 'user' ? '你的问题' : 'AI 回答'} key={`${message.messageId || message.clientMessageId || message.type}-${index}`}><div>{message.text}</div>{message.response && <div className="message-actions"><Button appearance="subtle" size="small" aria-label="回答有帮助" disabled={feedbackLoadingId === message.response.assistantMessageId} onClick={() => submitFeedback(message.response!, 'HELPFUL')}>有帮助</Button><Button appearance="subtle" size="small" aria-label="回答需改进" disabled={feedbackLoadingId === message.response.assistantMessageId} onClick={() => submitFeedback(message.response!, 'UNHELPFUL')}>需改进</Button>{feedbackByMessage[message.response.assistantMessageId] && <span className="feedback-recorded" role="status">反馈已记录</span>}<Button appearance="subtle" size="small" disabled={sending || !message.question} onClick={() => send(message.question)}>重试此问题</Button><Button appearance="subtle" size="small" disabled={debugLoadingId === message.response.assistantMessageId} onClick={() => loadDebug(message.response!)}>查看运行证据</Button></div>}</article>)}{sending && <div className="chat-message assistant" role="status">正在生成回答；完成后会追加到当前会话。</div>}</div>
    {error && <MessageBar intent="error" role="alert"><MessageBarBody><div className="chat-error"><span>{error.message}{error.traceId ? `（Trace ${error.traceId}）` : ''}</span>{releaseChanged && <><Text>该会话固定在旧发布版本，不能自动漂移到新版本；原会话与审计记录仍会保留。</Text><Button size="small" onClick={() => navigate?.('/workspace/apps')}>返回 AI 应用并新建会话</Button></>}{failedCommand && <div className="row-actions"><Button size="small" disabled={sending} onClick={retryFailedCommand}>重试同一请求</Button><Button size="small" appearance="subtle" disabled={sending} onClick={discardFailedCommand}>放弃并编辑</Button></div>}</div></MessageBarBody></MessageBar>}
    {permissionTarget && <div className="permission-action"><Text>审批通过后会写入草稿访问策略；应用重新发布后，创建新会话生效。</Text><Text>申请对象：{permissionTarget.resourceType} / {permissionTarget.resourceId} / {permissionTarget.permissionCode}</Text><Field required label="申请原因" hint="请说明业务目的，审批人将据此核验最小权限。"><Textarea aria-label="申请原因" value={permissionReason} disabled={permissionRequestStatus === 'submitting'} onChange={(_, data) => setPermissionReason(data.value)} /></Field><Button disabled={permissionRequestStatus === 'submitting' || !permissionReason.trim()} onClick={requestPermission}>{permissionRequestStatus === 'submitting' ? '提交中' : '提交权限申请'}</Button></div>}
    {submittedPermissionRequest && <MessageBar intent="success" role="status"><MessageBarBody><div className="permission-receipt"><strong>权限申请已提交</strong><Text>申请 {submittedPermissionRequest.requestId} · {submittedPermissionRequest.status}</Text><Text>目标：{submittedPermissionRequest.resourceType} / {submittedPermissionRequest.resourceId} / {submittedPermissionRequest.permissionCode}</Text><Text>下一责任人：租户管理员审批。</Text><Text>应用管理员重新发布后，从 AI 应用创建新会话；当前会话不变。</Text></div></MessageBarBody></MessageBar>}
    <div className="composer"><Textarea aria-label="输入问题" resize="vertical" value={question} onChange={(_, data) => setQuestion(data.value)} placeholder="输入你的业务问题" disabled={releaseChanged || Boolean(failedCommand) || !historyReady} /><Button appearance="primary" onClick={() => send()} disabled={releaseChanged || !currentSession.trim() || !question.trim() || !historyReady || sending || Boolean(failedCommand)}>{historyStatus === 'loading' ? '加载历史' : sending ? '发送中' : '发送'}</Button></div>
  </section><aside className="evidence-rail"><Title3>引用依据</Title3><Text>仅展示服务端返回的可见引用。过滤数量只有本次回答明确返回时才显示。</Text>
    {!historyReady && <div className="evidence-placeholder"><span>会话历史恢复前，引用依据保持锁定</span></div>}
    {historyReady && !latestResponse && <div className="evidence-placeholder"><span>当前会话尚无可展示的回答依据</span></div>}
    {latestResponse && <><div className="evidence-summary"><strong>可见引用 {latestResponse.citationCount} 条</strong>{typeof latestResponse.filteredCitationCount === 'number' && latestResponse.filteredCitationCount > 0 && <span>另有 {latestResponse.filteredCitationCount} 条引用因权限策略未展示</span>}</div><div className="citation-list">{latestResponse.citations.map((citation) => <article key={`${citation.knowledgeBaseId}-${citation.documentId}-${citation.chunkId}`}><div><strong>{citation.title}</strong><Badge appearance="outline">{Math.round(citation.score * 100)}%</Badge></div><p>{citation.snippet}</p><span>{citation.locator}</span></article>)}</div>{(latestResponse.traceId || latestResponse.modelInvocationLogId) && <dl className="run-metadata">{latestResponse.traceId && <><dt>Trace</dt><dd>{latestResponse.traceId}</dd></>}{latestResponse.modelInvocationLogId && <><dt>模型调用记录</dt><dd>{latestResponse.modelInvocationLogId}</dd></>}</dl>}</>}
    {debugEvidence && <section className="debug-evidence"><div><strong>运行证据</strong>{debugEvidence.masked === true && <Badge appearance="tint">证据已脱敏</Badge>}</div><dl><dt>Trace</dt><dd>{renderCell(debugEvidence.traceId)}</dd><dt>调用状态</dt><dd>{renderCell(debugEvidence.invocationStatus)}</dd><dt>耗时</dt><dd>{debugEvidence.latencyMs ? `${debugEvidence.latencyMs} ms` : '-'}</dd></dl></section>}
  </aside></main>;
}
