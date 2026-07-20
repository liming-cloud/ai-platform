import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  apiRequest,
  createModelConfig,
  createModelProvider,
  createPermissionRequest,
  disableModelConfig,
  disableModelProvider,
  getDebugEvidence,
  getCurrentUser,
  indexKnowledgeDocument,
  listApprovalTasks,
  listChatRunDeadLetters,
  testModelConnectivity,
  SESSION_EXPIRED_EVENT,
  sendChatMessage,
  session,
  submitChatMessageFeedback,
  updateModelConfig,
} from '../api';
import { ApiError } from '../types';

const AUTH_SESSION_KEY = 'aip.auth-session';
const TOKEN_KEY = 'aip.access-token';
const FUTURE_EXPIRY = '2099-01-01T00:00:00Z';

let stopSynchronization: (() => void) | undefined;

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

function apiResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function failedResponse(status: number) {
  return apiResponse({
    header: { code: 'FAILED', message: 'request failed', traceId: 'trace' },
    body: null,
  }, status);
}

function synchronizeRemoteSession(input: {
  token: string | null;
  generation: string;
  tenantId?: string;
  userId?: string;
  expiresAt?: string | null;
}) {
  const value = JSON.stringify({
    version: 1,
    token: input.token,
    generation: input.generation,
    identity: input.tenantId && input.userId
      ? { tenantId: input.tenantId, userId: input.userId }
      : null,
    expiresAt: input.expiresAt ?? null,
  });
  localStorage.setItem(AUTH_SESSION_KEY, value);
  if (input.token) localStorage.setItem(TOKEN_KEY, input.token);
  else localStorage.removeItem(TOKEN_KEY);
  window.dispatchEvent(new StorageEvent('storage', { key: AUTH_SESSION_KEY, newValue: value }));
  return value;
}

beforeEach(() => {
  localStorage.clear();
});

afterEach(() => {
  stopSynchronization?.();
  stopSynchronization = undefined;
  vi.restoreAllMocks();
});

describe('authenticated API requests', () => {
  it('sends the visible aggregate version on every model-hub mutation', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      const body = url.endsWith('/P%2F1/disable')
        ? { providerId: 'P/1', status: 'DISABLED', version: 12 }
        : url.endsWith('/M%2F1/disable')
          ? { providerId: 'P/1', modelConfigId: 'M/1', status: 'DEPRECATED', version: 14 }
          : url.endsWith('/connectivity-test')
            ? {
              providerId: 'P/1', modelConfigId: 'M/1', status: 'ACTIVE', connected: true,
              connectivityStatus: 'PASSED', message: '连接成功', version: 15,
            }
            : { providerId: 'P/1', modelConfigId: 'M/1', status: 'INACTIVE', version: 13 };
      return apiResponse({
        header: { code: 'SUCCESS', message: 'success', traceId: 'trace-version' },
        body,
      });
    });
    const input = {
      modelCode: 'gpt-contract',
      modelName: '合同审核模型',
      modelType: 'CHAT',
      contextWindow: 32768,
      embeddingDimension: null,
      inputPricePer1k: '0.0015',
      outputPricePer1k: '0.003',
      options: { providerKind: 'openai-compatible', maxTokens: 4096 },
    };

    await disableModelProvider('P/1', '供应商合同已到期', 11);
    await updateModelConfig('P/1', 'M/1', input, 12);
    await disableModelConfig('P/1', 'M/1', '切换到已验证的新模型', 13);
    await testModelConnectivity('P/1', 'M/1', 14);

    expect(fetchSpy.mock.calls.map(([path, init]) => ({
      body: JSON.parse(String(init?.body)),
      method: init?.method,
      path,
    }))).toEqual([
      {
        body: { reason: '供应商合同已到期', expectedVersion: 11 },
        method: 'POST',
        path: '/api/v1/model-providers/P%2F1/disable',
      },
      {
        body: { ...input, expectedVersion: 12 },
        method: 'PUT',
        path: '/api/v1/model-providers/P%2F1/models/M%2F1',
      },
      {
        body: { reason: '切换到已验证的新模型', expectedVersion: 13 },
        method: 'POST',
        path: '/api/v1/model-providers/P%2F1/models/M%2F1/disable',
      },
      {
        body: { expectedVersion: 14 },
        method: 'POST',
        path: '/api/v1/model-providers/P%2F1/models/M%2F1/connectivity-test',
      },
    ]);
  });

  it.each([
    [
      'provider creation',
      () => createModelProvider({
        requestId: 'REQ-P-CREATE',
        providerName: '受控模型网关',
        providerType: 'openai-compatible',
        apiEndpoint: 'https://models.example.test',
        authType: 'api_key',
        credentialMaterialInput: 'test-only-credential',
      }),
    ],
    [
      'model creation',
      () => createModelConfig('P-1', {
        requestId: 'REQ-M-CREATE',
        modelCode: 'gpt-contract',
        modelName: '合同审核模型',
        modelType: 'CHAT',
        contextWindow: 32768,
        embeddingDimension: null,
        inputPricePer1k: '0.001',
        outputPricePer1k: '0.002',
        options: { providerKind: 'openai-compatible', maxTokens: 4096 },
      }),
    ],
    ['provider disable', () => disableModelProvider('P-1', '合同到期', 7)],
    [
      'model update',
      () => updateModelConfig('P-1', 'M-1', {
        modelCode: 'gpt-contract',
        modelName: '合同审核模型',
        modelType: 'CHAT',
        contextWindow: 32768,
        embeddingDimension: null,
        inputPricePer1k: '0.001',
        outputPricePer1k: '0.002',
        options: { providerKind: 'openai-compatible', maxTokens: 4096 },
      }, 7),
    ],
    ['model disable', () => disableModelConfig('P-1', 'M-1', '迁移完成', 7)],
    ['connectivity test', () => testModelConnectivity('P-1', 'M-1', 7)],
  ])('rejects a malformed SUCCESS body for %s as an uncertain INVALID_RESPONSE', async (_label, request) => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(apiResponse({
      header: { code: 'SUCCESS', message: 'success', traceId: 'trace-invalid-body' },
      body: {},
    }));

    await expect(request()).rejects.toMatchObject({
      code: 'INVALID_RESPONSE',
      status: 200,
      traceId: 'trace-invalid-body',
    });
  });

  it.each([
    [
      'provider disable with a different resource id',
      () => disableModelProvider('P-1', '合同到期', 7),
      { providerId: 'P-2', status: 'DISABLED', version: 8 },
    ],
    [
      'provider disable without the terminal state',
      () => disableModelProvider('P-1', '合同到期', 7),
      { providerId: 'P-1', status: 'ACTIVE', version: 8 },
    ],
    [
      'provider disable without a version advance',
      () => disableModelProvider('P-1', '合同到期', 7),
      { providerId: 'P-1', status: 'DISABLED', version: 7 },
    ],
    [
      'model update with stale evidence',
      () => updateModelConfig('P-1', 'M-1', {
        modelCode: 'gpt-contract',
        modelName: '合同审核模型',
        modelType: 'CHAT',
        contextWindow: 32768,
        embeddingDimension: null,
        inputPricePer1k: '0.001',
        outputPricePer1k: '0.002',
        options: { providerKind: 'openai-compatible' },
      }, 7),
      { providerId: 'P-1', modelConfigId: 'M-1', status: 'INACTIVE', version: 7 },
    ],
    [
      'model disable without the terminal state',
      () => disableModelConfig('P-1', 'M-1', '迁移完成', 7),
      { providerId: 'P-1', modelConfigId: 'M-1', status: 'ACTIVE', version: 8 },
    ],
    [
      'model disable with a different frozen resource id',
      () => disableModelConfig('P-1', 'M-1', '迁移完成', 7),
      { providerId: 'P-1', modelConfigId: 'M-2', status: 'DEPRECATED', version: 8 },
    ],
    [
      'model disable without a version advance',
      () => disableModelConfig('P-1', 'M-1', '迁移完成', 7),
      { providerId: 'P-1', modelConfigId: 'M-1', status: 'DEPRECATED', version: 7 },
    ],
    [
      'connectivity evidence without a version advance',
      () => testModelConnectivity('P-1', 'M-1', 7),
      {
        providerId: 'P-1', modelConfigId: 'M-1', status: 'ACTIVE', connected: true,
        connectivityStatus: 'PASSED', message: '连接成功', version: 7,
      },
    ],
    [
      'connectivity evidence inconsistent with the probe result',
      () => testModelConnectivity('P-1', 'M-1', 7),
      {
        providerId: 'P-1', modelConfigId: 'M-1', status: 'ACTIVE', connected: false,
        connectivityStatus: 'PASSED', message: '结果不一致', version: 8,
      },
    ],
  ])('rejects %s as an uncertain INVALID_RESPONSE', async (_label, request, body) => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(apiResponse({
      header: { code: 'SUCCESS', message: 'success', traceId: 'trace-untrusted-mutation' },
      body,
    }));

    await expect(request()).rejects.toMatchObject({
      code: 'INVALID_RESPONSE',
      traceId: 'trace-untrusted-mutation',
    });
  });

  it('fails closed when a privileged dead-letter response is not the declared runtime contract', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(apiResponse({
      header: { code: 'SUCCESS', message: 'success', traceId: 'trace' },
      body: [{ applicationId: 'APP-1', status: 'DRAFT' }],
    }));

    await expect(listChatRunDeadLetters()).rejects.toThrow('死信响应无效');
  });

  it('forwards caller abort signals through approval and chat request seams', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(apiResponse({
      header: { code: 'SUCCESS', message: 'success', traceId: 'trace' },
      body: [],
    }));
    const controllers = Array.from({ length: 5 }, () => new AbortController());
    const requests = [
      listApprovalTasks({ page: 1, pageSize: 20 }, controllers[0].signal),
      sendChatMessage('session/1', 'client/1', 'hello', controllers[1].signal),
      getDebugEvidence('application/1', 'message/1', controllers[2].signal),
      submitChatMessageFeedback('session/1', 'message/1', 'HELPFUL', controllers[3].signal),
      createPermissionRequest({
        resourceType: 'KNOWLEDGE_BASE',
        resourceId: 'kb/1',
        permissionCode: 'knowledge:read',
        reason: 'temporary access',
      }, controllers[4].signal),
    ];

    controllers.forEach((controller) => controller.abort());
    await Promise.allSettled(requests);

    expect(fetchSpy).toHaveBeenCalledTimes(5);
    expect(fetchSpy.mock.calls.map(([, init]) => init?.signal?.aborted)).toEqual([
      true,
      true,
      true,
      true,
      true,
    ]);
  });

  it('keeps success and failure bodies isolated while preserving trusted error headers', async () => {
    const successBody = { result: 'accepted', code: 'untrusted-success-code' };
    const failureBody = {
      message: 'untrusted body message',
      code: 'untrusted-body-code',
      tenantId: 'must-not-merge',
    };
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(apiResponse({
        header: { code: 'SUCCESS', message: 'success', traceId: 'success-trace' },
        body: successBody,
      }))
      .mockResolvedValueOnce(apiResponse({
        header: { code: 'AIP-IAM-DENIED', message: 'trusted header message', traceId: 'failure-trace' },
        body: failureBody,
      }, 403));

    await expect(apiRequest('/api/v1/success-body')).resolves.toMatchObject({ body: successBody });
    const failure = await apiRequest('/api/v1/failure-body').catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(ApiError);
    expect(failure).toMatchObject({
      status: 403,
      code: 'AIP-IAM-DENIED',
      message: 'trusted header message',
      traceId: 'failure-trace',
      body: failureBody,
    });
    expect(failure).not.toHaveProperty('tenantId');
  });

  it('starts document indexing without client-owned model or version fields', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(apiResponse({
      header: { code: 'SUCCESS', message: 'success', traceId: 'trace-index' },
      body: { documentId: 'DOC-1', status: 'INDEXING' },
    }));

    await indexKnowledgeDocument('KB/1', 'DOC 1');

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [path, init] = fetchSpy.mock.calls[0];
    expect(path).toBe('/api/v1/knowledge-bases/KB%2F1/documents/DOC%201/index');
    expect(init).toMatchObject({ method: 'POST' });
    expect(init?.body).toBeUndefined();
  });

  it('advances the auth generation for login and session termination', () => {
    const initialGeneration = session.generation();

    session.save('issued-token');
    const loginGeneration = session.generation();
    session.clear();

    expect(loginGeneration).not.toBe(initialGeneration);
    expect(session.generation()).not.toBe(loginGeneration);
  });

  it('aborts a stale response and expires the old shell when another tab logs into a different tenant', async () => {
    session.save(
      'tenant-a-token',
      { tenantId: 'tenant-a', userId: 'user-a' },
      FUTURE_EXPIRY,
    );
    stopSynchronization = session.synchronize();
    const pending = deferred<Response>();
    let requestSignal: AbortSignal | null = null;
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
      requestSignal = init?.signal as AbortSignal;
      return pending.promise;
    });

    const oldTenantRequest = apiRequest('/api/v1/tenant-a/slow-resource');
    const remoteValue = synchronizeRemoteSession({
      token: 'tenant-b-token',
      generation: 'remote-tenant-b-login',
      tenantId: 'tenant-b',
      userId: 'user-b',
      expiresAt: FUTURE_EXPIRY,
    });
    window.dispatchEvent(new StorageEvent('storage', { key: AUTH_SESSION_KEY, newValue: remoteValue }));
    window.dispatchEvent(new StorageEvent('storage', { key: TOKEN_KEY, newValue: 'tenant-b-token' }));

    expect((requestSignal as unknown as AbortSignal).aborted).toBe(true);
    expect(expired).toHaveBeenCalledTimes(1);
    expect(session.snapshot()).toMatchObject({
      token: 'tenant-b-token',
      generation: 'remote-tenant-b-login',
      identity: { tenantId: 'tenant-b', userId: 'user-b' },
    });

    pending.resolve(apiResponse({
      header: { code: 'SUCCESS', message: 'success' },
      body: { tenantId: 'tenant-a', secret: 'must-not-commit' },
    }));
    await expect(oldTenantRequest).rejects.toMatchObject({ name: 'AbortError' });
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('deduplicates a remote-tab logout and never writes a synchronization loop', () => {
    session.save(
      'tenant-a-token',
      { tenantId: 'tenant-a', userId: 'user-a' },
      FUTURE_EXPIRY,
    );
    stopSynchronization = session.synchronize();
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);

    const remoteValue = synchronizeRemoteSession({
      token: null,
      generation: 'remote-logout',
    });
    window.dispatchEvent(new StorageEvent('storage', { key: AUTH_SESSION_KEY, newValue: remoteValue }));
    window.dispatchEvent(new StorageEvent('storage', { key: TOKEN_KEY, newValue: null }));

    expect(session.token()).toBeNull();
    expect(session.snapshot().identity).toBeNull();
    expect(expired).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem(AUTH_SESSION_KEY)).toBe(remoteValue);
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('does not replay a local login as expiration when duplicate storage events arrive', () => {
    stopSynchronization = session.synchronize();
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);

    session.save(
      'local-login-token',
      { tenantId: 'tenant-a', userId: 'user-a' },
      FUTURE_EXPIRY,
    );
    const storedValue = localStorage.getItem(AUTH_SESSION_KEY);
    window.dispatchEvent(new StorageEvent('storage', {
      key: AUTH_SESSION_KEY,
      newValue: storedValue,
    }));
    window.dispatchEvent(new StorageEvent('storage', {
      key: TOKEN_KEY,
      newValue: 'local-login-token',
    }));

    expect(expired).not.toHaveBeenCalled();
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('expires the current session from the raw HTTP 401 status even when the envelope is malformed', async () => {
    session.save('current-token');
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(apiResponse({}, 401));

    await expect(apiRequest('/api/v1/protected')).rejects.toMatchObject({ name: 'AbortError' });

    expect(session.token()).toBeNull();
    expect(expired).toHaveBeenCalledTimes(1);
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('fails closed and emits one expiration when a no-token request receives HTTP 401', async () => {
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(apiResponse({}, 401));

    await expect(apiRequest('/api/v1/protected-without-token')).rejects.toBeInstanceOf(Error);

    expect(session.token()).toBeNull();
    expect(session.snapshot().identity).toBeNull();
    expect(expired).toHaveBeenCalledTimes(1);
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('rejects auth/me when the response identity differs from the frozen login tenant', async () => {
    session.save(
      'tenant-a-token',
      { tenantId: 'tenant-a', userId: 'user-a' },
      FUTURE_EXPIRY,
    );
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(apiResponse({
      header: { code: 'SUCCESS', message: 'success' },
      body: {
        tenantId: 'tenant-b',
        userId: 'user-b',
        loginName: 'wrong-tenant-user',
        displayName: '错误租户用户',
        roleCodes: [],
        permissionCodes: [],
        menus: [],
        defaultRoute: '/workspace/home',
        accessTokenExpiresAt: FUTURE_EXPIRY,
      },
    }));

    await expect(getCurrentUser()).rejects.toMatchObject({ name: 'AbortError' });

    expect(session.token()).toBeNull();
    expect(session.snapshot().identity).toBeNull();
    expect(expired).toHaveBeenCalledTimes(1);
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('fails closed when an in-flight response crosses the token expiry boundary', async () => {
    const startedAt = Date.parse('2030-01-01T00:00:00Z');
    let now = startedAt;
    vi.spyOn(Date, 'now').mockImplementation(() => now);
    session.save(
      'short-lived-token',
      { tenantId: 'tenant-a', userId: 'user-a' },
      new Date(startedAt + 1_000).toISOString(),
    );
    const pending = deferred<Response>();
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.spyOn(globalThis, 'fetch').mockReturnValue(pending.promise);

    const request = apiRequest('/api/v1/slow-resource');
    now = startedAt + 1_001;
    pending.resolve(apiResponse({
      header: { code: 'SUCCESS', message: 'success' },
      body: { secret: 'must-not-commit-after-expiry' },
    }));

    await expect(request).rejects.toMatchObject({ name: 'AbortError' });
    expect(session.token()).toBeNull();
    expect(expired).toHaveBeenCalledTimes(1);
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('does not let an old 401 clear a newer login generation when the token value is reused', async () => {
    session.save('shared-token');
    const pending = deferred<Response>();
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.spyOn(globalThis, 'fetch').mockReturnValue(pending.promise);

    const oldRequest = apiRequest('/api/v1/slow-request');
    session.save('shared-token');
    pending.resolve(failedResponse(401));

    await expect(oldRequest).rejects.toMatchObject({ name: 'AbortError' });
    expect(session.token()).toBe('shared-token');
    expect(expired).toHaveBeenCalledTimes(1);
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('emits one expiration for local logout and ignores the stale request completion', async () => {
    session.save('current-token');
    const pending = deferred<Response>();
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.spyOn(globalThis, 'fetch').mockReturnValue(pending.promise);

    const oldRequest = apiRequest('/api/v1/slow-request');
    session.clear();
    expect(expired).toHaveBeenCalledTimes(1);
    pending.resolve(failedResponse(401));

    await expect(oldRequest).rejects.toMatchObject({ name: 'AbortError' });
    expect(session.token()).toBeNull();
    expect(expired).toHaveBeenCalledTimes(1);
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('does not let an aborted request expire the current session when its adapter resolves late', async () => {
    session.save('current-token');
    const pending = deferred<Response>();
    const controller = new AbortController();
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.spyOn(globalThis, 'fetch').mockReturnValue(pending.promise);

    const request = apiRequest('/api/v1/slow-request', { signal: controller.signal });
    controller.abort();
    pending.resolve(failedResponse(401));

    await expect(request).rejects.toMatchObject({ name: 'AbortError' });
    expect(session.token()).toBe('current-token');
    expect(expired).not.toHaveBeenCalled();
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it.each([403, 500])('preserves the session for HTTP %s', async (status) => {
    session.save('current-token');
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(failedResponse(status));

    await expect(apiRequest('/api/v1/protected')).rejects.toBeInstanceOf(Error);

    expect(session.token()).toBe('current-token');
    expect(expired).not.toHaveBeenCalled();
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('preserves the session for a network failure', async () => {
    session.save('current-token');
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('network unavailable'));

    await expect(apiRequest('/api/v1/protected')).rejects.toThrow('network unavailable');

    expect(session.token()).toBe('current-token');
    expect(expired).not.toHaveBeenCalled();
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });
});
