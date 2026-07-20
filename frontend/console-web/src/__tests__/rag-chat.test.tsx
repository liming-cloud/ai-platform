import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FluentProvider } from '@fluentui/react-components';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PageRouter } from '../pages';
import { enterpriseLightTheme } from '../theme';
import type { CurrentUser } from '../types';
import { ChatPage } from '../workspace-pages';

const user: CurrentUser = {
  tenantId: '2001', userId: '1001', loginName: 'user', displayName: '业务用户',
  roleCodes: ['business-user'], permissionCodes: ['app-studio:application:use'], menus: [],
  defaultRoute: '/workspace/home', accessTokenExpiresAt: '2099-01-01T00:00:00Z',
};

function envelope(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify({ header: { code: 'SUCCESS', message: 'success', traceId: 'test-trace' }, body }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

function failure(code: string, message: string, status = 403, body: unknown = null) {
  return Promise.resolve(new Response(JSON.stringify({ header: { code, message, traceId: 'trace-denied' }, body }), { status, headers: { 'Content-Type': 'application/json' } }));
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

function chatResult() {
  return {
    messageId: 'M-2', userMessageId: 'M-1', assistantMessageId: 'M-2', sessionId: 'S-1', applicationId: 'A-1',
    answer: '合同应由法务复核。', answerable: true,
    citations: [{ knowledgeBaseId: 'KB-1', documentId: 'D-1', chunkId: 'C-1', title: '合同审查规范', snippet: '重大合同需要法务复核。', locator: '第 3 章', score: 0.92 }],
    filteredCitationCount: 2,
    evidences: [{ knowledgeBaseId: 'KB-1', documentId: 'D-1', chunkId: 'C-1', score: 0.92, retrievalSignal: 'hybrid', indexVersion: 'v3' }],
    traceId: 'TRACE-CHAT', invocationStatus: 'SUCCEEDED', latencyMs: 320, createdAt: '2026-07-10T00:00:00Z',
  };
}

function chatHistory(sessionId = 'S-1', applicationId = 'A-1') {
  return [
    {
      messageId: 'M-1', sessionId, applicationId, role: 'USER', content: '合同需要谁复核？',
      citations: [], citationCount: 0, modelInvocationLogId: null, traceId: 'TRACE-USER', createdAt: '2026-07-10T00:00:00Z',
    },
    {
      messageId: 'M-2', sessionId, applicationId, role: 'ASSISTANT', content: '合同应由法务复核。',
      citations: [{ knowledgeBaseId: 'KB-1', documentId: 'D-1', chunkId: 'C-1', title: '合同审查规范', snippet: '重大合同需要法务复核。', locator: '第 3 章', score: 0.92 }],
      citationCount: 1, modelInvocationLogId: 'MODEL-LOG-2', traceId: 'TRACE-HISTORY', createdAt: '2026-07-10T00:00:01Z',
    },
  ];
}

function renderChat() {
  return render(<FluentProvider theme={enterpriseLightTheme}><PageRouter path="/workspace/sessions/S-1" user={user} navigate={vi.fn()} /></FluentProvider>);
}

afterEach(() => { cleanup(); localStorage.clear(); vi.restoreAllMocks(); });

describe('RAG conversation evidence', () => {
  it('announces message progress and new assistant answers through one stable conversation log', async () => {
    const actor = userEvent.setup();
    const pendingAnswer = deferred<Response>();
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => (
      init?.method === 'POST' ? pendingAnswer.promise : envelope([])
    ));
    renderChat();

    const log = await screen.findByRole('log', { name: '会话消息' });
    expect(log).toHaveAttribute('aria-live', 'polite');
    await waitFor(() => expect(log).toHaveAttribute('aria-busy', 'false'));
    await actor.type(screen.getByLabelText('输入问题'), '合同需要谁复核？');
    await actor.click(screen.getByRole('button', { name: '发送' }));

    expect(log).toHaveAttribute('aria-busy', 'true');
    expect(within(log).getByRole('status')).toHaveTextContent('正在生成回答');
    pendingAnswer.resolve(await envelope(chatResult()));

    expect(await within(log).findByText('合同应由法务复核。')).toBeInTheDocument();
    await waitFor(() => expect(log).toHaveAttribute('aria-busy', 'false'));
  });

  it('switches the send target and history atomically when the session route changes', async () => {
    const actor = userEvent.setup();
    const staleHistory = deferred<Response>();
    const postUrls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        postUrls.push(url);
        return envelope({ ...chatResult(), sessionId: 'S-B' });
      }
      if (url.includes('/chat-sessions/S-A/messages')) return staleHistory.promise;
      if (url.includes('/chat-sessions/S-B/messages')) return envelope([{
        messageId: 'B-M-1', sessionId: 'S-B', applicationId: 'A-B', role: 'USER', content: 'B 会话历史',
        citations: [], citationCount: 0, modelInvocationLogId: null, traceId: 'TRACE-B', createdAt: '2026-07-10T00:00:00Z',
      }]);
      return envelope([]);
    });
    const view = render(<FluentProvider theme={enterpriseLightTheme}><ChatPage sessionId="S-A" /></FluentProvider>);

    view.rerender(<FluentProvider theme={enterpriseLightTheme}><ChatPage sessionId="S-B" /></FluentProvider>);
    expect(await screen.findByText('B 会话历史')).toBeInTheDocument();
    staleHistory.resolve(await envelope([{
      messageId: 'A-M-1', sessionId: 'S-A', applicationId: 'A-A', role: 'USER', content: 'A 会话迟到历史',
      citations: [], citationCount: 0, modelInvocationLogId: null, traceId: 'TRACE-A', createdAt: '2026-07-10T00:00:00Z',
    }]));
    await actor.type(screen.getByLabelText('输入问题'), '只发送到 B');
    await actor.click(screen.getByRole('button', { name: '发送' }));

    await waitFor(() => expect(postUrls).toHaveLength(1));
    expect(postUrls[0]).toContain('/chat-sessions/S-B/messages');
    expect(screen.queryByText('A 会话迟到历史')).not.toBeInTheDocument();
  });

  it('fails closed when history cannot be trusted and unlocks sending only after a successful retry', async () => {
    const actor = userEvent.setup();
    let historyAttempts = 0;
    let postCount = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
      if (init?.method === 'POST') {
        postCount += 1;
        return envelope(chatResult());
      }
      historyAttempts += 1;
      return historyAttempts === 1
        ? failure('AIP-CHAT-HISTORY-5031', '会话历史暂时不可用', 503)
        : envelope([]);
    });

    renderChat();

    expect(await screen.findByText('会话历史暂时不可用', { exact: false })).toBeInTheDocument();
    expect(screen.getByText(/trace-denied/)).toBeInTheDocument();
    expect(screen.queryByText('开始一次可信问答')).not.toBeInTheDocument();
    expect(screen.queryByText('当前会话尚无可展示的回答依据')).not.toBeInTheDocument();
    expect(screen.getByText('会话历史恢复前，引用依据保持锁定')).toBeInTheDocument();
    expect(screen.getByLabelText('输入问题')).toBeDisabled();
    expect(screen.getByRole('button', { name: '发送' })).toBeDisabled();
    expect(postCount).toBe(0);

    await actor.click(screen.getByRole('button', { name: '重新加载历史' }));

    await waitFor(() => expect(screen.getByLabelText('输入问题')).not.toBeDisabled());
    expect(screen.getByText('开始一次可信问答')).toBeInTheDocument();
    await actor.type(screen.getByLabelText('输入问题'), '恢复后再发送');
    await actor.click(screen.getByRole('button', { name: '发送' }));
    await waitFor(() => expect(postCount).toBe(1));
  });

  it('shows only authorized citations and reports the filtered count without leaking names', async () => {
    const actor = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => init?.method === 'POST' ? envelope(chatResult()) : envelope([]));
    renderChat();
    await actor.type(await screen.findByLabelText('输入问题'), '合同需要谁复核？');
    await actor.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText('合同审查规范')).toBeInTheDocument();
    expect(screen.getByText('重大合同需要法务复核。')).toBeInTheDocument();
    expect(screen.getByText('可见引用 1 条')).toBeInTheDocument();
    expect(screen.getByText('另有 2 条引用因权限策略未展示')).toBeInTheDocument();
    expect(screen.queryByText('机密董事会文件')).not.toBeInTheDocument();
  });

  it('restores trusted citations, trace metadata, feedback and debug actions from history', async () => {
    const actor = userEvent.setup();
    const requests: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      requests.push(url);
      if (url.includes('/debug-runs/')) {
        return envelope({ masked: true, assistantMessageId: 'M-2', traceId: 'TRACE-HISTORY', invocationStatus: 'SUCCEEDED', latencyMs: 210 });
      }
      if (url.endsWith('/feedback')) {
        return envelope({ feedbackId: 'F-HISTORY', status: 'RECORDED', rating: 'HELPFUL', updatedAt: '2026-07-10T00:00:02Z' });
      }
      return envelope(chatHistory());
    });

    renderChat();

    expect(await screen.findByText('合同审查规范')).toBeInTheDocument();
    expect(screen.getByText('重大合同需要法务复核。')).toBeInTheDocument();
    expect(screen.getByText('TRACE-HISTORY')).toBeInTheDocument();
    expect(screen.getByText('MODEL-LOG-2')).toBeInTheDocument();
    expect(screen.queryByText('等待回答')).not.toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '回答有帮助' }));
    expect(await screen.findByText('反馈已记录')).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '查看运行证据' }));
    expect(await screen.findByText('证据已脱敏')).toBeInTheDocument();
    expect(requests.some((url) => url.includes('/ai-applications/A-1/debug-runs/M-2?view=summary'))).toBe(true);
  });

  it('retries the original user question with a new message request', async () => {
    const actor = userEvent.setup();
    let postCount = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => { if (init?.method === 'POST') { postCount += 1; return envelope(chatResult()); } return envelope([]); });
    renderChat();
    await actor.type(await screen.findByLabelText('输入问题'), '合同需要谁复核？');
    await actor.click(screen.getByRole('button', { name: '发送' }));
    await actor.click(await screen.findByRole('button', { name: '重试此问题' }));
    await waitFor(() => expect(postCount).toBe(2));
  });

  it('reuses one client message id after a transient failure without duplicating the user message', async () => {
    const actor = userEvent.setup();
    const requests: Array<Record<string, string>> = [];
    let postCount = 0;
    const stableClientMessageId = '123e4567-e89b-42d3-a456-426614174000';
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue(stableClientMessageId);
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
      if (init?.method !== 'POST') return envelope([]);
      postCount += 1;
      requests.push(JSON.parse(String(init.body)) as Record<string, string>);
      return postCount === 1
        ? failure('AIP-CHAT-RUN-RETRYABLE', '结果暂未持久确认', 503)
        : envelope(chatResult());
    });

    renderChat();
    await actor.type(await screen.findByLabelText('输入问题'), '合同需要谁复核？');
    await actor.click(screen.getByRole('button', { name: '发送' }));
    await actor.click(await screen.findByRole('button', { name: '重试同一请求' }));

    expect(await screen.findByText('合同审查规范')).toBeInTheDocument();
    expect(requests).toHaveLength(2);
    expect(requests[0]?.clientMessageId).toBe(stableClientMessageId);
    expect(requests[1]?.clientMessageId).toBe(stableClientMessageId);
    expect(screen.getAllByText('合同需要谁复核？')).toHaveLength(1);
  });

  it('locks a stale release session and routes the user to create a new session instead of retrying', async () => {
    const actor = userEvent.setup();
    const navigate = vi.fn();
    let postCount = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
      if (init?.method !== 'POST') return envelope([]);
      postCount += 1;
      return failure('AIP-APP-CHAT-SESSION-CONFLICT', '应用已发布新版本，请新建会话后继续', 409);
    });
    render(<FluentProvider theme={enterpriseLightTheme}><ChatPage sessionId="S-1" navigate={navigate} /></FluentProvider>);

    await actor.type(await screen.findByLabelText('输入问题'), '继续旧会话');
    await actor.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText('应用已发布新版本，请新建会话后继续', { exact: false })).toBeInTheDocument();
    expect(screen.getByText(/固定在旧发布版本/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '重试同一请求' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('输入问题')).toBeDisabled();
    expect(screen.getByLabelText('输入问题')).toHaveValue('继续旧会话');
    expect(screen.getByRole('button', { name: '发送' })).toBeDisabled();
    await actor.click(screen.getByRole('button', { name: '返回 AI 应用并新建会话' }));

    expect(navigate).toHaveBeenCalledWith('/workspace/apps');
    expect(postCount).toBe(1);
  });

  it('loads the server-authorized debug summary for an assistant message', async () => {
    const actor = userEvent.setup();
    const requests: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input); requests.push(url);
      if (url.includes('/debug-runs/')) return envelope({ masked: true, assistantMessageId: 'M-2', traceId: 'TRACE-CHAT', invocationStatus: 'SUCCEEDED', latencyMs: 320 });
      return init?.method === 'POST' ? envelope(chatResult()) : envelope([]);
    });
    renderChat();
    await actor.type(await screen.findByLabelText('输入问题'), '合同需要谁复核？');
    await actor.click(screen.getByRole('button', { name: '发送' }));
    await actor.click(await screen.findByRole('button', { name: '查看运行证据' }));

    expect(await screen.findAllByText('TRACE-CHAT')).not.toHaveLength(0);
    expect(screen.getByText('证据已脱敏')).toBeInTheDocument();
    expect(requests.some((url) => url.includes('/ai-applications/A-1/debug-runs/M-2?view=summary'))).toBe(true);
  });

  it('persists a helpful rating against the assistant message', async () => {
    const actor = userEvent.setup();
    let feedbackBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/feedback')) {
        feedbackBody = JSON.parse(String(init?.body));
        return envelope({ feedbackId: 'F-1', status: 'RECORDED', rating: 'HELPFUL', updatedAt: '2026-07-10T00:00:00Z' });
      }
      return init?.method === 'POST' ? envelope(chatResult()) : envelope([]);
    });
    renderChat();
    await actor.type(await screen.findByLabelText('输入问题'), '合同需要谁复核？');
    await actor.click(screen.getByRole('button', { name: '发送' }));
    await actor.click(await screen.findByRole('button', { name: '回答有帮助' }));

    expect(await screen.findByText('反馈已记录')).toBeInTheDocument();
    expect(feedbackBody).toEqual({ rating: 'HELPFUL', labels: [], comment: null });
  });

  it('submits the exact server-approved AI application ACL target with a required reason', async () => {
    const actor = userEvent.setup();
    let requestBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/permission-requests')) {
        requestBody = JSON.parse(String(init?.body));
        return envelope({ requestId: 'PR-1', status: 'PENDING' });
      }
      if (init?.method === 'POST') return failure(
        'AIP-IAM-AUTHZ-5003-REQUESTABLE',
        '当前应用尚未授权',
        403,
        { resourceType: 'AI_APPLICATION', resourceId: 'A-1', permissionCode: 'app-studio:application:use' },
      );
      return envelope([]);
    });
    renderChat();
    await actor.type(await screen.findByLabelText('输入问题'), '查询受限制度');
    await actor.click(screen.getByRole('button', { name: '发送' }));
    const submit = await screen.findByRole('button', { name: '提交权限申请' });
    expect(submit).toBeDisabled();
    expect(screen.getByText(/审批通过后会写入草稿访问策略.*重新发布后.*创建新会话/)).toBeInTheDocument();
    await actor.type(screen.getByLabelText('申请原因'), '需要依据合同规范完成本周审查');
    await actor.click(submit);

    expect(await screen.findByText('权限申请已提交')).toBeInTheDocument();
    expect(requestBody).toEqual({
      resourceType: 'AI_APPLICATION', resourceId: 'A-1', permissionCode: 'app-studio:application:use',
      reason: '需要依据合同规范完成本周审查',
    });
  });

  it('J-08 keeps the submitted permission fact and the complete handoff visible for the current session', async () => {
    const actor = userEvent.setup();
    let permissionPosts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/permission-requests')) {
        permissionPosts += 1;
        return envelope({ requestId: 'PR-J08', status: 'PENDING' });
      }
      if (init?.method === 'POST') return failure(
        'AIP-IAM-AUTHZ-5003-REQUESTABLE',
        '当前应用尚未授权',
        403,
        { resourceType: 'AI_APPLICATION', resourceId: 'A-1', permissionCode: 'app-studio:application:use' },
      );
      return envelope([]);
    });
    renderChat();

    const question = await screen.findByLabelText('输入问题');
    await actor.type(question, '查询受限制度');
    await actor.click(screen.getByRole('button', { name: '发送' }));
    await actor.type(await screen.findByLabelText('申请原因'), '完成当前合同审查');
    await actor.click(screen.getByRole('button', { name: '提交权限申请' }));

    expect(await screen.findByText(/申请 PR-J08 · PENDING/)).toBeInTheDocument();
    expect(screen.getByText(/下一责任人：租户管理员审批/)).toBeInTheDocument();
    expect(screen.getByText(/应用管理员重新发布后/)).toBeInTheDocument();
    expect(screen.getByText(/从 AI 应用创建新会话/)).toBeInTheDocument();

    await actor.clear(question);
    await actor.type(question, '再次查询受限制度');
    await actor.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText(/申请 PR-J08 · PENDING/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '提交权限申请' })).not.toBeInTheDocument();
    expect(permissionPosts).toBe(1);
  });

  it.each([
    ['AIP-RAG-POLICY-5006', '模型策略拒绝'],
    ['AIP-IAM-AUTHZ-5001', '普通权限拒绝'],
    ['AIP-IAM-AUTHZ-5003-REQUESTABLE', '权限申请目标缺失'],
  ])('does not offer an ACL request for %s', async (code, message) => {
    const actor = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => (
      init?.method === 'POST' ? failure(code, message) : envelope([])
    ));
    renderChat();
    await actor.type(await screen.findByLabelText('输入问题'), '查询受限制度');
    await actor.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText(message, { exact: false })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '提交权限申请' })).not.toBeInTheDocument();
  });

  it('drops late debug, feedback and permission-request results after the session changes', async () => {
    const actor = userEvent.setup();
    const lateDebug = deferred<Response>();
    const lateFeedback = deferred<Response>();
    const latePermission = deferred<Response>();
    let permissionDenied = false;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.includes('/chat-sessions/S-A/messages') && init?.method !== 'POST') return envelope(chatHistory('S-A', 'A-A'));
      if (url.includes('/chat-sessions/S-B/messages')) return envelope([]);
      if (url.includes('/debug-runs/')) return lateDebug.promise;
      if (url.endsWith('/feedback')) return lateFeedback.promise;
      if (url.endsWith('/permission-requests')) return latePermission.promise;
      if (init?.method === 'POST') {
        permissionDenied = true;
        return failure('AIP-IAM-AUTHZ-5003-REQUESTABLE', '当前应用尚未授权', 403, {
          resourceType: 'AI_APPLICATION', resourceId: 'A-A', permissionCode: 'app-studio:application:use',
        });
      }
      return envelope([]);
    });
    const view = render(<FluentProvider theme={enterpriseLightTheme}><ChatPage sessionId="S-A" /></FluentProvider>);
    await screen.findByText('合同审查规范');

    await actor.click(screen.getByRole('button', { name: '查看运行证据' }));
    view.rerender(<FluentProvider theme={enterpriseLightTheme}><ChatPage sessionId="S-B" /></FluentProvider>);
    lateDebug.resolve(await envelope({ masked: true, traceId: 'TRACE-STALE-DEBUG' }));
    await waitFor(() => expect(screen.queryByText('TRACE-STALE-DEBUG')).not.toBeInTheDocument());

    view.rerender(<FluentProvider theme={enterpriseLightTheme}><ChatPage sessionId="S-A" /></FluentProvider>);
    await screen.findByText('合同审查规范');
    await actor.click(screen.getByRole('button', { name: '回答有帮助' }));
    view.rerender(<FluentProvider theme={enterpriseLightTheme}><ChatPage sessionId="S-B" /></FluentProvider>);
    lateFeedback.resolve(await envelope({ feedbackId: 'F-STALE', status: 'RECORDED', rating: 'HELPFUL' }));
    await waitFor(() => expect(screen.queryByText('反馈已记录')).not.toBeInTheDocument());

    view.rerender(<FluentProvider theme={enterpriseLightTheme}><ChatPage sessionId="S-A" /></FluentProvider>);
    await screen.findByText('合同审查规范');
    await actor.type(screen.getByLabelText('输入问题'), '查询受限制度');
    await actor.click(screen.getByRole('button', { name: '发送' }));
    expect(permissionDenied).toBe(true);
    await actor.type(await screen.findByLabelText('申请原因'), '完成当前合同审查');
    await actor.click(screen.getByRole('button', { name: '提交权限申请' }));
    view.rerender(<FluentProvider theme={enterpriseLightTheme}><ChatPage sessionId="S-B" /></FluentProvider>);
    latePermission.resolve(await envelope({ requestId: 'PR-STALE', status: 'PENDING' }));
    await waitFor(() => expect(screen.queryByText('权限申请已提交')).not.toBeInTheDocument());
  });

  it('locks a manually entered session id while a message is being sent', async () => {
    const actor = userEvent.setup();
    const lateSend = deferred<Response>();
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => (
      init?.method === 'POST' ? lateSend.promise : envelope([])
    ));
    render(<FluentProvider theme={enterpriseLightTheme}><ChatPage /></FluentProvider>);
    const sessionInput = screen.getByLabelText('会话 ID');
    await actor.type(sessionInput, 'S-MANUAL');
    await actor.type(screen.getByLabelText('输入问题'), '合同需要谁复核？');
    await actor.click(screen.getByRole('button', { name: '发送' }));

    expect(sessionInput).toBeDisabled();
    lateSend.resolve(await envelope({ ...chatResult(), sessionId: 'S-MANUAL' }));
    await waitFor(() => expect(sessionInput).not.toBeDisabled());
  });
});
