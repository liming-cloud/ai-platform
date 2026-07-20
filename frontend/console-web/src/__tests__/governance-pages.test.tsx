import {
  act,
  cleanup,
  fireEvent,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FluentProvider } from '@fluentui/react-components';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ModelHubPage } from '../governance-pages';
import { PageRouter } from '../pages';
import { enterpriseLightTheme } from '../theme';
import type { ApprovalDecisionResult, ApprovalTask, CurrentUser } from '../types';
import { removeIsolatedPortalHosts, renderWithIsolatedPortal } from './test-render';

const user: CurrentUser = {
  tenantId: '2001', userId: '1001', loginName: 'admin', displayName: '管理员',
  roleCodes: ['platform-admin'], permissionCodes: [
    'approval-center:task:read', 'approval-center:task:decide',
    'app-studio:application:read', 'app-studio:application:create', 'app-studio:application:update',
    'app-studio:application:manage-acl', 'app-studio:application:publish',
    'model-hub:provider:read', 'model-hub:provider:create', 'model-hub:provider:disable',
    'model-hub:model-config:read', 'model-hub:model-config:create', 'model-hub:model-config:update',
    'model-hub:model-config:disable', 'model-hub:model-config:test-connectivity',
  ], menus: [], defaultRoute: '/',
  accessTokenExpiresAt: '2099-01-01T00:00:00Z',
};

const ownerApplicationAccess = {
  accessMode: 'OWNER',
  capabilities: {
    canEditConfiguration: true,
    canRunPublishPreflight: true,
    canPublish: true,
    canRollback: true,
    canManageAccessPolicy: true,
    canReadReleaseEvidence: true,
  },
} as const;

const knowledgeAdministrator: CurrentUser = {
  ...user,
  roleCodes: ['tenant-admin'],
  permissionCodes: [
    'knowledge-base:knowledge-base:read',
    'knowledge-base:knowledge-base:create',
    'knowledge-base:document:read',
    'knowledge-base:document:upload',
    'knowledge-base:document:parse',
    'knowledge-base:document:index',
    'knowledge-base:document:cancel',
  ],
  menus: [{
    code: 'business.knowledge-bases',
    name: '企业知识',
    platform: 'BUSINESS',
    path: '/workspace/knowledge-bases',
    routeName: 'BusinessKnowledgeBases',
    componentKey: 'business/knowledge-bases',
    permission: 'knowledge-base:knowledge-base:read',
    sort: 20,
  }],
  defaultRoute: '/tenant/knowledge-bases',
};

function envelope(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify({ header: { code: 'SUCCESS', message: 'success', traceId: 'test-trace' }, body }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

function failure(message: string, code = 'AIP-TEST-CONFLICT', status = 409) {
  return Promise.resolve(new Response(JSON.stringify({ header: { code, message, traceId: 'error-trace' }, body: null }), { status, headers: { 'Content-Type': 'application/json' } }));
}

const EVIDENCE_DIGEST = 'a'.repeat(64);
const TARGET_DIGEST = 'b'.repeat(64);

function modelApprovalTask(overrides: Partial<ApprovalTask> = {}): ApprovalTask {
  const task = {
    taskId: 'T-EVIDENCE',
    approvalRequestId: 'REQ-EVIDENCE',
    approvalType: 'model_config_change',
    title: '发布模型配置',
    riskLevel: 'HIGH_RISK',
    riskLevelLabel: '高风险',
    status: 'PENDING',
    statusLabel: '待审批',
    sourceContext: 'model-hub',
    sourceContextLabel: '模型中心',
    sourceResourceType: 'model_config',
    sourceResourceId: 'M-EVIDENCE',
    requesterId: '1002',
    approverId: undefined,
    traceId: 'TRACE-EVIDENCE',
    evidenceSchema: 'model-approval-evidence/v1',
    evidenceDigest: EVIDENCE_DIGEST,
    targetDigest: TARGET_DIGEST,
    ...overrides,
  } satisfies Omit<ApprovalTask, 'evidence'>;
  const target = {
    modelConfigId: task.sourceResourceId,
    modelCode: 'gpt-enterprise',
    modelName: 'Enterprise GPT',
    modelType: 'CHAT',
    contextWindow: 128000,
    embeddingDimension: null,
    inputPricePer1k: '9999999999.99999999',
    outputPricePer1k: '0.00000001',
    runtimeOptions: { providerKind: 'openai-compatible', maxTokens: 4096, temperature: 0.2 },
    expectedStatus: 'INACTIVE',
    connectivityStatus: 'NOT_TESTED',
    connectivityCheckedAt: null,
    connectivityMessage: '',
  };
  const after = {
    ...target,
    provider: {
      providerId: 'P-1', providerCode: 'openai-compatible', providerKind: 'openai-compatible',
      providerName: 'OpenAI 生产', endpoint: 'https://models.example.test',
    },
  };
  return {
    ...task,
    evidence: {
      tenantId: '2001', approvalRequestId: task.approvalRequestId, approvalType: task.approvalType,
      action: 'MODEL_CONFIG_CREATE', sourceContext: task.sourceContext,
      resourceType: task.sourceResourceType, resourceId: task.sourceResourceId,
      providerId: 'P-1', modelConfigId: task.sourceResourceId, requesterId: task.requesterId,
      requestedAt: '2026-07-20T08:00:00Z', riskLevel: 'HIGH_RISK',
      expectedPostDecisionState: 'INACTIVE+NOT_TESTED', before: null, after, target,
      changedFields: Object.keys(after).sort(),
    },
  };
}

function approvalDecision(task: ApprovalTask, status: 'APPROVED' | 'REJECTED'): ApprovalDecisionResult {
  return {
    taskId: task.taskId,
    approvalRequestId: task.approvalRequestId,
    status,
    sourceContext: task.sourceContext,
    sourceResourceType: task.sourceResourceType,
    sourceResourceId: task.sourceResourceId,
    evidenceSchema: task.evidenceSchema!,
    evidenceDigest: task.evidenceDigest!,
    targetDigest: task.targetDigest!,
  };
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

function renderPage(path: string, currentUser: CurrentUser = user) {
  return renderWithIsolatedPortal(<FluentProvider theme={enterpriseLightTheme}><PageRouter path={path} user={currentUser} navigate={vi.fn()} /></FluentProvider>);
}

const uncertainCreationFailures: ReadonlyArray<{
  label: string;
  message: string;
  respond: () => Promise<Response>;
}> = [
  { label: 'HTTP 408', message: '创建结果暂时无法确认（HTTP 408）', respond: () => failure('创建结果暂时无法确认（HTTP 408）', 'AIP-UPSTREAM-TIMEOUT', 408) },
  { label: 'HTTP 425', message: '创建结果暂时无法确认（HTTP 425）', respond: () => failure('创建结果暂时无法确认（HTTP 425）', 'AIP-UPSTREAM-EARLY', 425) },
  { label: 'HTTP 429', message: '创建结果暂时无法确认（HTTP 429）', respond: () => failure('创建结果暂时无法确认（HTTP 429）', 'AIP-UPSTREAM-RATE-LIMITED', 429) },
  { label: 'HTTP 503', message: '创建结果暂时无法确认（HTTP 503）', respond: () => failure('创建结果暂时无法确认（HTTP 503）', 'AIP-UPSTREAM-UNAVAILABLE', 503) },
  { label: 'INVALID_RESPONSE', message: '服务返回了无法识别的操作结果', respond: () => envelope({}) },
  { label: 'network failure', message: '创建请求的网络结果暂时无法确认', respond: () => Promise.reject(new TypeError('创建请求的网络结果暂时无法确认')) },
];

type ModelHubCreationKind = 'provider' | 'model';
type TestActor = ReturnType<typeof userEvent.setup>;

const modelHubActiveProvider = {
  providerId: 'P-1',
  providerCode: 'openai-compatible',
  providerKind: 'openai-compatible',
  providerName: 'OpenAI 生产',
  endpoint: 'https://models.example.test',
  status: 'ACTIVE',
  version: 7,
};

function successfulModelHubCreation(kind: ModelHubCreationKind) {
  return kind === 'provider'
    ? envelope({ providerId: 'P-RECOVERED', status: 'PENDING_APPROVAL', approvalRequestId: 'REQ-P-RECOVERED' })
    : envelope({
      providerId: 'P-1', modelConfigId: 'M-RECOVERED', modelCode: 'gpt-pending', modelType: 'CHAT',
      status: 'PENDING_APPROVAL', approvalRequestId: 'REQ-M-RECOVERED', connectivityStatus: 'NOT_TESTED',
    });
}

function mockModelHubCreation(
  kind: ModelHubCreationKind,
  createBodies: Array<Record<string, unknown>>,
  responses: ReadonlyArray<() => Promise<Response>>,
) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
    const url = String(input);
    if (init?.method === 'POST') {
      createBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
      const respond = responses[Math.min(createBodies.length - 1, responses.length - 1)];
      return respond();
    }
    if (url.endsWith('/model-providers')) return envelope([modelHubActiveProvider]);
    if (url.endsWith('/models')) return envelope([]);
    throw new Error(`unexpected request: ${url}`);
  });
}

async function openAndFillModelHubCreation(actor: TestActor, kind: ModelHubCreationKind) {
  const triggerName = kind === 'provider' ? '新增供应商' : '新增模型配置';
  const dialogName = kind === 'provider' ? '新增模型供应商' : '新增模型配置';
  await actor.click(await screen.findByRole('button', { name: triggerName }));
  const dialog = await screen.findByRole('dialog', { name: dialogName });
  if (kind === 'provider') {
    await actor.type(within(dialog).getByLabelText(/^供应商名称/), '未决模型网关');
    await actor.type(within(dialog).getByLabelText(/^API 地址/), 'https://pending.example.test');
    await actor.type(within(dialog).getByLabelText(/^访问凭证/), 'provider-secret-never-persist');
  } else {
    await actor.type(within(dialog).getByLabelText(/^模型名称/), '未决对话模型');
    await actor.type(within(dialog).getByLabelText(/^模型编码/), 'gpt-pending');
    await actor.type(within(dialog).getByLabelText(/^上下文窗口/), '32768');
    await actor.type(within(dialog).getByLabelText(/^输入价格（每千 Token）/), '0.001');
    await actor.type(within(dialog).getByLabelText(/^输出价格（每千 Token）/), '0.002');
  }
  return dialog;
}

afterEach(() => {
  cleanup();
  removeIsolatedPortalHosts();
  localStorage.clear();
  sessionStorage.clear();
  vi.restoreAllMocks();
});

describe('governance workflows', () => {
  it('submits a delegated tenant action with a required reason and idempotency key', async () => {
    const actor = userEvent.setup();
    let requestBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (!init?.method || init.method === 'GET') {
        if (url.endsWith('/features')) return envelope([{ featureCode: 'knowledge-base', featureName: '知识库', enabled: true, source: 'PLAN' }]);
        return envelope([{ tenantId: '2001', code: '2001', name: '默认企业', status: 'ACTIVE', planCode: 'PRO' }]);
      }
      requestBody = JSON.parse(String(init?.body));
      return envelope({ delegationActionId: 'D-1', status: 'SUCCEEDED', auditEventId: 'AUDIT-1' });
    });
    renderPage('/admin/tenants');

    await actor.click(await screen.findByRole('button', { name: '应急代管' }));
    expect(await screen.findByRole('button', { name: '提交代管动作' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('代管动作'), { target: { value: 'ASSIGN_TENANT_ADMIN' } });
    fireEvent.change(screen.getByLabelText(/目标用户 ID/), { target: { value: '1002' } });
    fireEvent.change(screen.getByLabelText(/目标角色 ID/), { target: { value: '3002' } });
    fireEvent.change(screen.getByLabelText(/原因/), { target: { value: '协助租户恢复管理员授权' } });
    await actor.click(await screen.findByRole('button', { name: '提交代管动作' }));

    expect(await screen.findByText('AUDIT-1')).toBeInTheDocument();
    expect(requestBody).toMatchObject({ actionType: 'ASSIGN_TENANT_ADMIN', reason: '协助租户恢复管理员授权' });
    expect(String(requestBody?.idempotencyKey)).not.toBe('');
  });

  it('freezes one delegated intent across an uncertain retry and rotates its key only after reconciliation', async () => {
    const actor = userEvent.setup();
    const requestBodies: Array<Record<string, unknown>> = [];
    let authoritativeReads = 0;
    let failNextReconciliation = false;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (!init?.method || init.method === 'GET') {
        authoritativeReads += 1;
        if (url.endsWith('/features')) {
          if (failNextReconciliation) {
            failNextReconciliation = false;
            return Promise.reject(new Error('目标租户重新读取失败'));
          }
          return envelope([{ featureCode: 'knowledge-base', featureName: '知识库', enabled: true, source: 'PLAN' }]);
        }
        return envelope([{ tenantId: '2001', code: '2001', name: '默认企业', status: 'ACTIVE', planCode: 'PRO' }]);
      }
      requestBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
      if (requestBodies.length === 1) return Promise.reject(new Error('代管请求超时，服务端结果尚未确认'));
      return envelope({
        delegationActionId: `D-${requestBodies.length}`,
        status: 'SUCCEEDED',
        auditEventId: `AUDIT-${requestBodies.length}`,
      });
    });
    renderPage('/admin/tenants');

    await actor.click(await screen.findByRole('button', { name: '应急代管' }));
    fireEvent.change(screen.getByLabelText(/目标用户 ID/), { target: { value: '1002' } });
    fireEvent.change(screen.getByLabelText(/目标角色 ID/), { target: { value: '3002' } });
    fireEvent.change(screen.getByLabelText(/原因/), { target: { value: '协助租户恢复管理员授权' } });
    fireEvent.click(within(
      await screen.findByRole('dialog', { name: '应急代管' }),
    ).getByRole('button', { name: '提交代管动作' }));

    expect(await screen.findByText('代管请求超时，服务端结果尚未确认')).toBeInTheDocument();
    expect(screen.getByLabelText(/目标用户 ID/)).toBeDisabled();
    expect(screen.getByLabelText(/目标角色 ID/)).toBeDisabled();
    expect(screen.getByLabelText(/原因/)).toBeDisabled();
    const uncertainDialog = await screen.findByRole('dialog', { name: '应急代管' });
    const uncertainDialogRemoved = waitForElementToBeRemoved(uncertainDialog);
    fireEvent.click(within(uncertainDialog).getByRole('button', { name: '取消' }));
    await uncertainDialogRemoved;

    await actor.click(screen.getByRole('button', { name: '应急代管' }));
    const retryDialog = await screen.findByRole('dialog', { name: '应急代管' });
    expect(within(retryDialog).getByLabelText(/目标用户 ID/)).toHaveValue('1002');
    expect(within(retryDialog).getByLabelText(/目标用户 ID/)).toBeDisabled();
    fireEvent.click(within(retryDialog).getByRole('button', { name: '使用原请求编号重试' }));

    const completedDialog = await screen.findByRole('dialog', { name: '应急代管' });
    expect(await within(completedDialog).findByText('AUDIT-2')).toBeInTheDocument();
    expect(requestBodies).toHaveLength(2);
    expect(requestBodies[1]).toEqual(requestBodies[0]);
    const completedIntentKey = String(requestBodies[0].idempotencyKey);
    expect(completedIntentKey).not.toBe('');
    const readsBeforeReconciliation = authoritativeReads;

    failNextReconciliation = true;
    fireEvent.click(within(completedDialog).getByRole('button', { name: '重新读取后开始下一项' }));
    const failedReconciliationDialog = await screen.findByRole('dialog', { name: '应急代管' });
    expect(await within(failedReconciliationDialog).findByText('目标租户重新读取失败')).toBeInTheDocument();
    expect(within(failedReconciliationDialog).getByLabelText(/目标用户 ID/)).toBeDisabled();
    expect(requestBodies).toHaveLength(2);
    const readsAfterFailedReconciliation = authoritativeReads;

    fireEvent.click(within(
      await screen.findByRole('dialog', { name: '应急代管' }),
    ).getByRole('button', { name: '重新读取后开始下一项' }));
    await waitFor(() => expect(authoritativeReads).toBeGreaterThan(readsAfterFailedReconciliation));
    expect(authoritativeReads).toBeGreaterThan(readsBeforeReconciliation);
    expect(screen.getByLabelText(/目标用户 ID/)).toBeEnabled();
    expect(screen.getByLabelText(/目标用户 ID/)).toHaveValue('');
    fireEvent.change(screen.getByLabelText(/目标用户 ID/), { target: { value: '1003' } });
    fireEvent.change(screen.getByLabelText(/目标角色 ID/), { target: { value: '3003' } });
    fireEvent.change(screen.getByLabelText(/原因/), { target: { value: '处理新的租户管理员授权请求' } });
    fireEvent.click(within(
      screen.getByRole('dialog', { name: '应急代管' }),
    ).getByRole('button', { name: '提交代管动作' }));

    await waitFor(() => expect(requestBodies).toHaveLength(3));
    expect(requestBodies[2].idempotencyKey).not.toBe(completedIntentKey);
  });

  it('exposes an accessible tenant search and a stable action region for every tenant row', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/features')) return envelope([{ featureCode: 'knowledge-base', featureName: '知识库', enabled: true, source: 'PLAN' }]);
      return envelope([{ tenantId: '2001', code: '2001', name: '默认企业', status: 'ACTIVE', planCode: 'PRO' }]);
    });
    renderPage('/admin/tenants');

    expect(await screen.findByRole('textbox', { name: '搜索租户名称或编码' })).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: '租户列表' });
    expect(table).toHaveClass('tenant-table');
    const tenantRow = within(table).getByRole('row', { name: /默认企业/ });
    expect(within(tenantRow).getByRole('group', { name: '默认企业操作' })).toBeInTheDocument();
  });

  it('confirms a frozen tenant capability change with impact and a required audited reason', async () => {
    const actor = userEvent.setup();
    const writes: Array<{ url: string; body: Record<string, unknown> }> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'PUT') {
        writes.push({ url, body: JSON.parse(String(init.body)) });
        return envelope({ resourceId: 'knowledge-base', status: 'SAVED' });
      }
      if (url.endsWith('/features')) return envelope([
        { featureCode: 'knowledge-base', featureName: '知识库', enabled: true, source: 'PLAN' },
      ]);
      return envelope([{ tenantId: '2001', code: 'default', name: '默认企业', status: 'ACTIVE', planCode: 'PRO' }]);
    });
    renderPage('/admin/tenants');

    const capabilityCheckbox = await screen.findByRole('checkbox', { name: '知识库产品能力' });
    await actor.click(capabilityCheckbox);

    expect(writes).toHaveLength(0);
    expect(capabilityCheckbox).toBeChecked();
    const dialog = await screen.findByRole('dialog', { name: '确认停用产品能力' });
    expect(within(dialog).getByText('默认企业')).toBeInTheDocument();
    expect(within(dialog).getByText('知识库')).toBeInTheDocument();
    expect(within(dialog).getByText('已启用')).toBeInTheDocument();
    expect(within(dialog).getByText('未启用')).toBeInTheDocument();
    expect(within(dialog).getByText(/依赖该能力的租户用户/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '确认停用' })).toBeDisabled();

    fireEvent.change(within(dialog).getByLabelText(/变更原因/), { target: { value: '租户已完成知识库下线评审' } });
    await actor.click(within(
      await screen.findByRole('dialog', { name: '确认停用产品能力' }),
    ).getByRole('button', { name: '确认停用' }));

    await waitFor(() => expect(writes).toEqual([{
      url: '/api/v1/iam/tenants/2001/features/knowledge-base',
      body: { enabled: false, reason: '租户已完成知识库下线评审' },
    }]));
    await waitFor(() => expect(capabilityCheckbox).not.toBeChecked());
  });

  it('confirms a frozen tenant status transition with impact and a required audited reason', async () => {
    const actor = userEvent.setup();
    const writes: Array<{ url: string; body: Record<string, unknown> }> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST' && url.endsWith('/status')) {
        writes.push({ url, body: JSON.parse(String(init.body)) });
        return envelope({ resourceId: '2001', status: 'DISABLED' });
      }
      if (url.endsWith('/features')) return envelope([]);
      return envelope([{ tenantId: '2001', code: 'default', name: '默认企业', status: 'ACTIVE', planCode: 'PRO' }]);
    });
    renderPage('/admin/tenants');

    await actor.click(await screen.findByRole('button', { name: '停用 默认企业' }));

    expect(writes).toHaveLength(0);
    const dialog = await screen.findByRole('dialog', { name: '确认停用租户' });
    expect(within(dialog).getByText('默认企业')).toBeInTheDocument();
    expect(within(dialog).getByText('default')).toBeInTheDocument();
    expect(within(dialog).getByText('正常')).toBeInTheDocument();
    expect(within(dialog).getByText('已停用')).toBeInTheDocument();
    expect(within(dialog).getByText(/用户登录和租户业务访问/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '确认停用租户' })).toBeDisabled();

    fireEvent.change(within(dialog).getByLabelText(/变更原因/), { target: { value: '合同到期并已完成停服通知' } });
    await actor.click(within(
      await screen.findByRole('dialog', { name: '确认停用租户' }),
    ).getByRole('button', { name: '确认停用租户' }));

    await waitFor(() => expect(writes).toEqual([{
      url: '/api/v1/iam/tenants/2001/status',
      body: { status: 'DISABLED', reason: '合同到期并已完成停服通知' },
    }]));
  });

  it('keeps the newest tenant search result when an older request finishes last', async () => {
    const actor = userEvent.setup();
    const firstSearch = deferred<Response>();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/iam/tenants?') && !url.includes('keyword=')) return firstSearch.promise;
      if (url.includes('/iam/tenants?') && url.includes('keyword=%E6%96%B0')) {
        return envelope([{ tenantId: '2002', code: 'new', name: '新企业', status: 'ACTIVE', planCode: 'PRO' }]);
      }
      if (url.endsWith('/iam/tenants/2002/features')) {
        return envelope([{ featureCode: 'app-studio', featureName: '应用', enabled: true, source: 'PLAN' }]);
      }
      if (url.endsWith('/iam/tenants/2001/features')) {
        return envelope([{ featureCode: 'knowledge-base', featureName: '知识库', enabled: true, source: 'PLAN' }]);
      }
      return envelope([]);
    });
    renderPage('/admin/tenants');

    await actor.type(screen.getByRole('textbox', { name: '搜索租户名称或编码' }), '新');
    expect((await screen.findAllByText('新企业')).length).toBeGreaterThan(0);
    await act(async () => {
      firstSearch.resolve(await envelope([{ tenantId: '2001', code: 'old', name: '旧企业', status: 'ACTIVE', planCode: 'STANDARD' }]));
    });

    await waitFor(() => expect(screen.queryAllByText('新企业').length).toBeGreaterThan(0));
    expect(screen.queryAllByText('旧企业')).toHaveLength(0);
    expect(screen.getByText('应用')).toBeInTheDocument();
  });

  it('dismisses the delegated tenant dialog with Escape', async () => {
    const actor = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/features')) return envelope([{ featureCode: 'knowledge-base', featureName: '知识库', enabled: true, source: 'PLAN' }]);
      return envelope([{ tenantId: '2001', code: '2001', name: '默认企业', status: 'ACTIVE', planCode: 'PRO' }]);
    });
    renderPage('/admin/tenants');

    await actor.click(await screen.findByRole('button', { name: '应急代管' }));
    expect(await screen.findByRole('dialog', { name: '应急代管' })).toBeInTheDocument();
    await actor.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('dialog', { name: '应急代管' })).not.toBeInTheDocument());
  });

  it('records an approval decision only after a nonblank reason is entered', async () => {
    const actor = userEvent.setup();
    const requests: { url: string; body?: Record<string, unknown> }[] = [];
    const task = modelApprovalTask({ taskId: 'T-1', approvalRequestId: 'REQ-1', sourceResourceId: 'M-1', traceId: 'TRACE-1' });
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input); requests.push({ url, body: init?.body ? JSON.parse(String(init.body)) : undefined });
      if (!init?.method || init.method === 'GET') return envelope([task]);
      return envelope(approvalDecision(task, 'APPROVED'));
    });
    renderPage('/admin/approvals');

    expect(await screen.findByRole('textbox', { name: '搜索审批事项或资源编号' })).toBeInTheDocument();
    expect(await screen.findByRole('table', { name: '审批任务列表' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '通过 T-1' })).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '通过 T-1' }));
    await actor.type(screen.getByLabelText(/审批原因/), '已核验模型策略和密钥托管方式');
    await actor.click(screen.getByRole('button', { name: '确认通过' }));

    expect(await screen.findByText('APPROVED')).toBeInTheDocument();
    expect(requests.find((request) => request.body?.action === 'APPROVE')?.body).toEqual({
      action: 'APPROVE', comment: '已核验模型策略和密钥托管方式', expectedEvidenceDigest: EVIDENCE_DIGEST,
    });
  });

  it('queries approvals with the persisted risk and source contract values', async () => {
    const actor = userEvent.setup();
    const queryRequests: URL[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      queryRequests.push(new URL(String(input), 'http://console.test'));
      return envelope([{
        taskId: 'T-FILTER', title: '发布模型配置', riskLevel: 'HIGH_RISK', riskLevelLabel: '高风险',
        status: 'PENDING', statusLabel: '待审批', sourceContext: 'model-hub', sourceContextLabel: '模型中心',
        sourceResourceType: 'model_config', sourceResourceId: 'M-FILTER', requesterId: '1002',
        approverId: null, traceId: 'TRACE-FILTER',
      }]);
    });
    renderPage('/admin/approvals');

    await screen.findByRole('table', { name: '审批任务列表' });
    const risk = screen.getByLabelText('风险等级');
    const source = screen.getByLabelText('业务来源');
    expect(within(risk).getByRole('option', { name: '高风险' })).toHaveValue('HIGH_RISK');
    expect(within(risk).getByRole('option', { name: '常规' })).toHaveValue('NORMAL');
    expect(within(source).getByRole('option', { name: '模型中心' })).toHaveValue('model-hub');

    await actor.selectOptions(risk, 'HIGH_RISK');
    await actor.selectOptions(source, 'model-hub');

    await waitFor(() => expect(queryRequests.some((request) => (
      request.searchParams.get('riskLevel') === 'HIGH_RISK'
      && request.searchParams.get('sourceContext') === 'model-hub'
    ))).toBe(true));
    expect(screen.getByLabelText('风险等级：高风险', { selector: '.fui-Badge' })).toBeInTheDocument();
  });

  it('gives a tenant administrator a formal approval route with requester, target, impact, and trace context', async () => {
    const actor = userEvent.setup();
    const requests: { url: string; body?: Record<string, unknown> }[] = [];
    const tenantApprover: CurrentUser = {
      ...user,
      userId: '1003',
      loginName: 'tenant-admin',
      displayName: '租户审批员',
      roleCodes: ['tenant-admin'],
      permissionCodes: ['approval-center:task:read', 'approval-center:task:decide'],
    };
    const task = modelApprovalTask({
      taskId: 'T-TENANT', approvalRequestId: 'REQ-TENANT', title: '启用租户模型',
      sourceResourceId: 'M-TENANT', traceId: 'TRACE-TENANT',
    });
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      requests.push({ url, body: init?.body ? JSON.parse(String(init.body)) : undefined });
      if (!init?.method || init.method === 'GET') return envelope([task]);
      return envelope(approvalDecision(task, 'APPROVED'));
    });

    renderPage('/tenant/approvals', tenantApprover);

    const table = await screen.findByRole('table', { name: '审批任务列表' });
    expect(within(table).getByText('1002')).toBeInTheDocument();
    expect(within(table).getByText('model_config / M-TENANT')).toBeInTheDocument();
    expect(within(table).getByText('高风险')).toBeInTheDocument();
    expect(within(table).getByText(/TRACE-TENANT/)).toBeInTheDocument();
    await actor.click(within(table).getByRole('button', { name: '通过 T-TENANT' }));
    const detail = document.querySelector<HTMLElement>('.approval-panel');
    expect(detail).not.toBeNull();
    expect(within(detail!).getByText('申请人')).toBeInTheDocument();
    expect(within(detail!).getByText('变更目标')).toBeInTheDocument();
    expect(within(detail!).getByText('影响等级')).toBeInTheDocument();
    expect(within(detail!).getByText('追踪编号')).toBeInTheDocument();
    expect(within(detail!).getByText('新建资源（无历史快照）')).toBeInTheDocument();
    expect(within(detail!).getAllByText('9999999999.99999999')).not.toHaveLength(0);
    expect(within(detail!).getByText(EVIDENCE_DIGEST)).toBeInTheDocument();
    await actor.type(screen.getByLabelText(/审批原因/), '已由独立租户管理员完成影响复核');
    await actor.click(screen.getByRole('button', { name: '确认通过' }));

    expect(requests.find((request) => request.body?.action === 'APPROVE')?.body).toEqual({
      action: 'APPROVE', comment: '已由独立租户管理员完成影响复核', expectedEvidenceDigest: EVIDENCE_DIGEST,
    });
  });

  it('disables self-approval actions and explains the four-eyes requirement', async () => {
    const actor = userEvent.setup();
    const requester: CurrentUser = { ...user, userId: '1002' };
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => envelope([{
      taskId: 'T-SELF', title: '发布本人配置', riskLevel: 'HIGH', riskLevelLabel: '高风险', status: 'PENDING', statusLabel: '待审批',
      sourceContext: 'model-hub', sourceContextLabel: '模型中心', sourceResourceType: 'model_config', sourceResourceId: 'M-SELF',
      requesterId: '1002', approverId: null, traceId: 'TRACE-SELF',
    }]));

    renderPage('/admin/approvals', requester);

    const table = await screen.findByRole('table', { name: '审批任务列表' });
    expect(within(table).getByRole('button', { name: '拒绝 T-SELF' })).toBeDisabled();
    expect(within(table).getByRole('button', { name: '通过 T-SELF' })).toBeDisabled();
    expect(within(table).getByText('本人申请，需由其他管理员处理')).toBeInTheDocument();
    await actor.click(within(table).getByRole('button', { name: '查看 T-SELF' }));
    const detail = document.querySelector<HTMLElement>('.approval-panel');
    expect(detail).not.toBeNull();
    expect(within(detail!).getByText('TRACE-SELF')).toBeInTheDocument();
    expect(within(detail!).getByText('此任务由当前账号发起，需由其他管理员完成决策。')).toBeInTheDocument();
  });

  it('keeps a read-only approval viewer from invoking a decision action', async () => {
    const viewer: CurrentUser = { ...user, userId: '1004', permissionCodes: ['approval-center:task:read'] };
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => envelope([{
      taskId: 'T-READ', title: '查看风险变更', riskLevel: 'MEDIUM', riskLevelLabel: '中风险', status: 'PENDING', statusLabel: '待审批',
      sourceContext: 'identity-access', sourceContextLabel: '身份与权限', sourceResourceType: 'role', sourceResourceId: 'R-1',
      requesterId: '1002', approverId: null, traceId: 'TRACE-READ',
    }]));

    renderPage('/admin/approvals', viewer);

    const table = await screen.findByRole('table', { name: '审批任务列表' });
    expect(within(table).getByRole('button', { name: '拒绝 T-READ' })).toBeDisabled();
    expect(within(table).getByRole('button', { name: '通过 T-READ' })).toBeDisabled();
    expect(within(table).getByText('当前账号仅可查看审批任务')).toBeInTheDocument();
  });

  it('shows an approval failure beside the selected task instead of appearing unresponsive', async () => {
    const actor = userEvent.setup();
    const task = modelApprovalTask({ taskId: 'T-9', approvalRequestId: 'REQ-9', sourceResourceId: 'M-9', traceId: 'TRACE-9' });
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
      if (!init?.method || init.method === 'GET') return envelope([task]);
      return failure('审批任务已经被其他管理员处理');
    });
    renderPage('/admin/approvals');

    await actor.click(await screen.findByRole('button', { name: '拒绝 T-9' }));
    await actor.type(screen.getByLabelText(/审批原因/), '配置不符合平台密钥托管规范');
    await actor.click(screen.getByRole('button', { name: '确认拒绝' }));

    expect(await screen.findByText('审批任务已经被其他管理员处理')).toBeInTheDocument();
    expect(screen.getByText('错误追踪：error-trace')).toBeInTheDocument();
  });

  it('keeps legacy or unknown evidence tasks read-only with an explicit reason', async () => {
    const actor = userEvent.setup();
    const legacy = { ...modelApprovalTask({ taskId: 'T-LEGACY' }), evidenceSchema: null, evidenceDigest: null, targetDigest: null, evidence: null };
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => envelope([legacy]));

    renderPage('/admin/approvals');

    const table = await screen.findByRole('table', { name: '审批任务列表' });
    expect(within(table).getByRole('button', { name: '拒绝 T-LEGACY' })).toBeDisabled();
    expect(within(table).getByRole('button', { name: '通过 T-LEGACY' })).toBeDisabled();
    expect(within(table).getByText(/缺少可验证的审批证据/)).toBeInTheDocument();
    await actor.click(within(table).getByRole('button', { name: '查看 T-LEGACY' }));
    expect(screen.getByText(/该历史任务仅可查看/)).toBeInTheDocument();
  });

  it('fails closed when model evidence uses an action outside the supported contract', async () => {
    const task = modelApprovalTask({ taskId: 'T-UNKNOWN-ACTION' });
    task.evidence = { ...task.evidence!, action: 'MODEL_CONFIG_DELETE' };
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => envelope([task]));

    renderPage('/admin/approvals');

    const table = await screen.findByRole('table', { name: '审批任务列表' });
    expect(within(table).getByRole('button', { name: '拒绝 T-UNKNOWN-ACTION' })).toBeDisabled();
    expect(within(table).getByRole('button', { name: '通过 T-UNKNOWN-ACTION' })).toBeDisabled();
    expect(within(table).getByText(/证据类型或动作不受支持/)).toBeInTheDocument();
  });

  it('clears stale approval actions when an authoritative refresh fails', async () => {
    const actor = userEvent.setup();
    const task = modelApprovalTask({ taskId: 'T-STALE-REFRESH' });
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => (
      String(input).includes('status=APPROVED')
        ? failure('审批任务刷新失败', 'AIP-APPROVAL-REFRESH-FAILED', 503)
        : envelope([task])
    ));

    renderPage('/admin/approvals');
    expect(await screen.findByRole('button', { name: '通过 T-STALE-REFRESH' })).toBeEnabled();
    await actor.selectOptions(screen.getByLabelText('审批状态'), 'APPROVED');

    expect(await screen.findByText('审批任务刷新失败')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: '审批任务列表' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '通过 T-STALE-REFRESH' })).not.toBeInTheDocument();
  });

  it('moves focus into the approval detail rail and restores it to the invoking action', async () => {
    const actor = userEvent.setup();
    const task = modelApprovalTask({ taskId: 'T-FOCUS' });
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => envelope([task]));

    renderPage('/admin/approvals');
    const trigger = await screen.findByRole('button', { name: '查看 T-FOCUS' });
    await actor.click(trigger);

    const detail = await screen.findByRole('complementary', { name: '审批任务详情' });
    await waitFor(() => expect(detail).toHaveFocus());
    expect(detail).toHaveAttribute('tabindex', '-1');
    await actor.click(within(detail).getByRole('button', { name: '关闭' }));
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it('reconciles an uncertain decision POST with the authoritative task resource', async () => {
    const actor = userEvent.setup();
    const task = modelApprovalTask({ taskId: 'T-UNCERTAIN' });
    const requested: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      requested.push(url);
      if (init?.method === 'POST') return Promise.reject(new TypeError('network outcome unknown'));
      if (url.endsWith('/api/v1/approvals/tasks/T-UNCERTAIN')) {
        return envelope({ ...task, status: 'APPROVED', statusLabel: '已通过', approverId: '1001' });
      }
      return envelope([task]);
    });

    renderPage('/admin/approvals');
    await actor.click(await screen.findByRole('button', { name: '通过 T-UNCERTAIN' }));
    await actor.type(screen.getByLabelText(/审批原因/), '已完成独立复核');
    await actor.click(screen.getByRole('button', { name: '确认通过' }));

    expect(await screen.findByText('APPROVED')).toBeInTheDocument();
    expect(requested).toContain('/api/v1/approvals/tasks/T-UNCERTAIN');
    expect(screen.queryByLabelText(/审批原因/)).not.toBeInTheDocument();
  });

  it('does not show success when the decision response is bound to different evidence', async () => {
    const actor = userEvent.setup();
    const task = modelApprovalTask({ taskId: 'T-MISMATCH' });
    let reads = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
      if (!init?.method || init.method === 'GET') {
        reads += 1;
        return envelope([task]);
      }
      return envelope({ ...approvalDecision(task, 'APPROVED'), evidenceDigest: 'c'.repeat(64) });
    });

    renderPage('/admin/approvals');
    await actor.click(await screen.findByRole('button', { name: '通过 T-MISMATCH' }));
    await actor.type(screen.getByLabelText(/审批原因/), '独立复核通过');
    await actor.click(screen.getByRole('button', { name: '确认通过' }));

    expect(await screen.findByText(/审批结果与当前证据不一致/)).toBeInTheDocument();
    expect(reads).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText('APPROVED')).not.toBeInTheDocument();
  });

  it('discards an older approval query after filters change', async () => {
    const actor = userEvent.setup();
    const stalePendingQuery = deferred<Response>();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('status=APPROVED')) return envelope([{
        taskId: 'T-APPROVED', title: '已完成审批', riskLevel: 'LOW', status: 'APPROVED',
        sourceContext: 'model-hub', sourceResourceType: 'model_config', sourceResourceId: 'M-2',
        requesterId: '1002', approverId: '1001', traceId: 'TRACE-APPROVED',
      }]);
      return stalePendingQuery.promise;
    });
    renderPage('/admin/approvals');

    await actor.selectOptions(screen.getByLabelText('审批状态'), 'APPROVED');
    expect(await screen.findByText('已完成审批')).toBeInTheDocument();
    await act(async () => {
      stalePendingQuery.resolve(await envelope([{
        taskId: 'T-STALE', title: '过期的待审批任务', riskLevel: 'HIGH', status: 'PENDING',
        sourceContext: 'model-hub', sourceResourceType: 'model_config', sourceResourceId: 'M-STALE',
        requesterId: '1003', approverId: null, traceId: 'TRACE-STALE',
      }]));
    });

    await waitFor(() => expect(screen.queryByText('过期的待审批任务')).not.toBeInTheDocument());
    expect(screen.getByText('已完成审批')).toBeInTheDocument();
  });

  it('shows publish blockers returned by the real preflight contract', async () => {
    const actor = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.startsWith('/api/v1/chat-runs/dead-letters') || url.endsWith('/releases')) return envelope([]);
      if (!init?.method || init.method === 'GET') return envelope([{
        applicationId: 'A-1', name: '合同助手', applicationCode: 'CONTRACT', status: 'DRAFT',
        ...ownerApplicationAccess,
      }]);
      return envelope({ applicationId: 'A-1', publishRequestId: 'P-1', passed: false, blockers: [{ code: 'BOUND_KNOWLEDGE_BASE_INVISIBLE', message: '绑定知识库不可见', resourceType: 'knowledge_base', resourceId: 'KB-1' }], warnings: [], traceId: 'TRACE-P', checkedAt: '2026-07-10T00:00:00Z' });
    });
    renderPage('/tenant/applications');

    await actor.click(await screen.findByRole('button', { name: '发布预检' }));

    expect(await screen.findByText('绑定知识库不可见')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认发布' })).toBeDisabled();
  });

  it('publishes only after an operator reviews the frozen preflight and enters a business reason', async () => {
    const actor = userEvent.setup();
    let publishBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (!init?.method || init.method === 'GET') {
        if (url.startsWith('/api/v1/chat-runs/dead-letters') || url.endsWith('/releases')) return envelope([]);
        return envelope([{
          applicationId: 'A-1', name: '合同助手', applicationCode: 'CONTRACT', status: 'DRAFT',
          ...ownerApplicationAccess,
        }]);
      }
      if (url.endsWith('/publish/preflight')) return envelope({
        applicationId: 'A-1', publishRequestId: 'P-FROZEN', passed: true, blockers: [], warnings: [],
        traceId: 'TRACE-PREFLIGHT', checkedAt: '2026-07-10T00:00:00Z',
      });
      if (url.endsWith('/publish')) {
        publishBody = JSON.parse(String(init?.body));
        return envelope({
          applicationId: 'A-1', status: 'PUBLISHED', activeReleaseId: 'RELEASE-1', activeReleaseVersion: 1,
        });
      }
      return envelope({});
    });
    renderPage('/tenant/applications');

    await actor.click(await screen.findByRole('button', { name: '发布预检' }));
    expect(await screen.findByText('发布预检通过')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: '确认发布' })).toBeEnabled());
    await actor.click(screen.getByRole('button', { name: '确认发布' }));

    const dialog = await screen.findByRole('dialog', { name: '确认发布应用' });
    expect(within(dialog).getByText('合同助手')).toBeInTheDocument();
    expect(within(dialog).getByText(/TRACE-PREFLIGHT/)).toBeInTheDocument();
    const confirm = within(dialog).getByRole('button', { name: '确认并发布' });
    expect(confirm).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText(/发布理由/), { target: { value: '法务团队已完成知识范围与模型输出复核' } });
    await actor.click(within(
      await screen.findByRole('dialog', { name: '确认发布应用' }),
    ).getByRole('button', { name: '确认并发布' }));

    expect(await screen.findByText(/应用已发布为活动版本 v1/)).toBeInTheDocument();
    expect(publishBody).toEqual({ publishRequestId: 'P-FROZEN', reason: '法务团队已完成知识范围与模型输出复核' });
  });

  it('does not attach an older application preflight to a newly selected application', async () => {
    const actor = userEvent.setup();
    const applicationAPreflight = deferred<Response>();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (!init?.method || init.method === 'GET') return envelope([
        {
          applicationId: 'A-1', name: '应用 A', applicationType: 'CHAT', modelConfigId: 'M-1', status: 'DRAFT',
          ...ownerApplicationAccess,
        },
        {
          applicationId: 'A-2', name: '应用 B', applicationType: 'CHAT', modelConfigId: 'M-2', status: 'DRAFT',
          ...ownerApplicationAccess,
        },
      ]);
      if (url.includes('/ai-applications/A-1/publish/preflight')) return applicationAPreflight.promise;
      return envelope({ applicationId: 'A-2', publishRequestId: 'P-2', passed: true, blockers: [], warnings: [], traceId: 'TRACE-B', checkedAt: '2026-07-10T00:00:00Z' });
    });
    renderPage('/tenant/applications');

    const table = await screen.findByRole('table', { name: '应用治理列表' });
    const applicationARow = within(table).getByRole('row', { name: /应用 A/ });
    const applicationBRow = within(table).getByRole('row', { name: /应用 B/ });
    await actor.click(within(applicationARow).getByRole('button', { name: '发布预检' }));
    await actor.click(within(applicationBRow).getByRole('button', { name: '治理' }));
    await act(async () => {
      applicationAPreflight.resolve(await envelope({ applicationId: 'A-1', publishRequestId: 'P-1', passed: true, blockers: [], warnings: [], traceId: 'TRACE-A', checkedAt: '2026-07-10T00:00:00Z' }));
    });

    const controlPanel = document.querySelector('aside');
    expect(controlPanel).not.toBeNull();
    expect(within(controlPanel!).getByText('应用 B')).toBeInTheDocument();
    expect(screen.queryByText('TRACE-A')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认发布' })).toBeDisabled();
  });

  it('shows only the safe tenant-owned dead-letter fields needed by an operator', async () => {
    const deadLetterOperator: CurrentUser = {
      ...user,
      permissionCodes: ['app-studio:application:publish', 'app-studio:application:manage-acl'],
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.startsWith('/api/v1/chat-runs/dead-letters')) return envelope([{
        runId: 'RUN-1', sessionId: 'SESSION-1', applicationId: 'APP-1', originalActorId: 'USER-7',
        finalizationAttemptCount: 3, finalizationCycleAttemptCount: 3, manualReplayCount: 0,
        failureCode: 'FINALIZATION_TRANSACTION_FAILED', failureSummary: '最终结果事务提交失败',
        failureTraceId: 'TRACE-DEAD-1', updatedAt: '2026-07-18T03:04:05Z',
        storedCompletedResultJson: '不得进入运营界面的敏感结果正文',
      }]);
      if (url.endsWith('/api/v1/ai-applications')) return envelope([
        { applicationId: 'APP-1', name: '合同助手', applicationType: 'CHAT', modelConfigId: 'MODEL-1', status: 'PUBLISHED' },
      ]);
      throw new Error(`unexpected request: ${url}`);
    });

    renderPage('/tenant/applications', deadLetterOperator);

    const table = await screen.findByRole('table', { name: '聊天最终化死信列表' });
    const row = within(table).getByRole('row', { name: /RUN-1/ });
    expect(within(row).getByText('APP-1')).toBeInTheDocument();
    expect(within(row).getByText('SESSION-1')).toBeInTheDocument();
    expect(within(row).getByText('第 3 / 3 次')).toBeInTheDocument();
    expect(within(row).getByText('累计 3 次')).toBeInTheDocument();
    expect(within(row).getByText('TRACE-DEAD-1')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: '重放 RUN-1' })).toBeEnabled();
    expect(screen.queryByText('USER-7')).not.toBeInTheDocument();
    expect(screen.queryByText('不得进入运营界面的敏感结果正文')).not.toBeInTheDocument();
  });

  it('retries a failed manual replay with the same frozen idempotency key and explains the outcome', async () => {
    const actor = userEvent.setup();
    const deadLetterOperator: CurrentUser = {
      ...user,
      permissionCodes: ['app-studio:application:publish', 'app-studio:application:manage-acl'],
    };
    const replayBodies: Array<Record<string, unknown>> = [];
    let replayCalls = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST' && url.endsWith('/chat-runs/RUN-1/dead-letter/replay')) {
        const body = JSON.parse(String(init.body)) as Record<string, unknown>;
        replayBodies.push(body);
        replayCalls += 1;
        if (replayCalls === 1) return failure('重放响应暂时不可确认', 'AIP-APP-REPLAY-CONFLICT');
        return envelope({ runId: 'RUN-1', replayRequestId: body.replayRequestId, status: 'RETRY_SCHEDULED' });
      }
      if (url.startsWith('/api/v1/chat-runs/dead-letters')) return envelope(replayCalls > 1 ? [] : [{
        runId: 'RUN-1', sessionId: 'SESSION-1', applicationId: 'APP-1', originalActorId: 'USER-7',
        finalizationAttemptCount: 3, finalizationCycleAttemptCount: 3, manualReplayCount: 0,
        failureCode: 'FINALIZATION_TRANSACTION_FAILED', failureSummary: '最终结果事务提交失败',
        failureTraceId: 'TRACE-DEAD-1', updatedAt: '2026-07-18T03:04:05Z',
      }]);
      if (url.endsWith('/api/v1/ai-applications')) return envelope([
        { applicationId: 'APP-1', name: '合同助手', applicationType: 'CHAT', modelConfigId: 'MODEL-1', status: 'PUBLISHED' },
      ]);
      throw new Error(`unexpected request: ${url}`);
    });

    renderPage('/tenant/applications', deadLetterOperator);
    await actor.click(await screen.findByRole('button', { name: '重放 RUN-1' }));

    const dialog = await screen.findByRole('dialog', { name: '确认人工重放' });
    expect(within(dialog).getByText('2001')).toBeInTheDocument();
    expect(within(dialog).getByText('RUN-1')).toBeInTheDocument();
    expect(within(dialog).getByText('只重放已保存的最终化事务，不会再次调用模型、检索或策略链。')).toBeInTheDocument();
    const confirm = within(dialog).getByRole('button', { name: '确认重放' });
    expect(confirm).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText(/重放原因/), { target: { value: '数据库恢复后重新提交最终结果' } });
    expect(within(await screen.findByRole('dialog', { name: '确认人工重放' }))
      .getByRole('button', { name: '确认重放' })).toBeEnabled();

    await actor.click(within(
      await screen.findByRole('dialog', { name: '确认人工重放' }),
    ).getByRole('button', { name: '确认重放' }));
    const failedReplayDialog = await screen.findByRole('dialog', { name: '确认人工重放' });
    expect(await within(failedReplayDialog).findByText('重放响应暂时不可确认')).toBeInTheDocument();
    expect(within(failedReplayDialog).getByText('错误追踪：error-trace')).toBeInTheDocument();
    expect(within(failedReplayDialog).getByText('请保留当前对话框并使用同一重放请求 ID 重试，避免重复执行。')).toBeInTheDocument();
    await actor.click(within(
      await screen.findByRole('dialog', { name: '确认人工重放' }),
    ).getByRole('button', { name: '确认重放' }));

    expect(await screen.findByText('重放已进入受控重试')).toBeInTheDocument();
    expect(screen.getByText(/已按幂等键受理/)).toBeInTheDocument();
    expect(replayBodies).toHaveLength(2);
    expect(replayBodies[0].replayRequestId).toBeTruthy();
    expect(replayBodies[1].replayRequestId).toBe(replayBodies[0].replayRequestId);
    expect(replayBodies[0]).toEqual({
      replayRequestId: replayBodies[0].replayRequestId,
      reason: '数据库恢复后重新提交最终结果',
    });
    expect(replayBodies[0]).not.toHaveProperty('tenantId');
  });

  it.each([
    [['app-studio:application:manage-acl'], 'app-studio:application:publish'],
    [['app-studio:application:publish'], 'app-studio:application:manage-acl'],
  ])('does not query dead letters when an operator lacks %s', async (permissionCodes, missingPermission) => {
    const requestedUrls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      requestedUrls.push(url);
      if (url.endsWith('/api/v1/ai-applications')) return envelope([]);
      throw new Error(`unexpected request: ${url}`);
    });

    renderPage('/tenant/applications', { ...user, permissionCodes });

    expect(await screen.findByText(`缺少 ${missingPermission}`)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '刷新死信' })).toBeDisabled();
    expect(requestedUrls.some((url) => url.includes('/chat-runs/dead-letters'))).toBe(false);
  });

  it('never restores a previous tenant dead-letter response after the authenticated tenant changes', async () => {
    const tenantAResponse = deferred<Response>();
    let deadLetterCalls = 0;
    const tenantA: CurrentUser = {
      ...user,
      tenantId: '2001',
      permissionCodes: ['app-studio:application:publish', 'app-studio:application:manage-acl'],
    };
    const tenantB: CurrentUser = { ...tenantA, tenantId: '2002', userId: '1002' };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.startsWith('/api/v1/chat-runs/dead-letters')) {
        deadLetterCalls += 1;
        if (deadLetterCalls === 1) return tenantAResponse.promise;
        return envelope([{
          runId: 'RUN-B', sessionId: 'SESSION-B', applicationId: 'APP-B', originalActorId: 'USER-B',
          finalizationAttemptCount: 3, finalizationCycleAttemptCount: 3, manualReplayCount: 0,
          failureCode: 'FINALIZATION_TRANSACTION_FAILED', failureSummary: '租户 B 的最终结果事务提交失败',
          failureTraceId: 'TRACE-B', updatedAt: '2026-07-18T03:04:05Z',
        }]);
      }
      if (url.endsWith('/api/v1/ai-applications')) return envelope([]);
      throw new Error(`unexpected request: ${url}`);
    });

    const view = renderPage('/tenant/applications', tenantA);
    await waitFor(() => expect(deadLetterCalls).toBe(1));
    view.rerender(<FluentProvider theme={enterpriseLightTheme}><PageRouter path="/tenant/applications" user={tenantB} navigate={vi.fn()} /></FluentProvider>);

    expect(await screen.findByRole('row', { name: /RUN-B/ })).toBeInTheDocument();
    await act(async () => {
      tenantAResponse.resolve(await envelope([{
        runId: 'RUN-A', sessionId: 'SESSION-A', applicationId: 'APP-A', originalActorId: 'USER-A',
        finalizationAttemptCount: 3, finalizationCycleAttemptCount: 3, manualReplayCount: 0,
        failureCode: 'FINALIZATION_TRANSACTION_FAILED', failureSummary: '租户 A 的最终结果事务提交失败',
        failureTraceId: 'TRACE-A', updatedAt: '2026-07-18T03:04:05Z',
      }]));
    });

    expect(screen.queryByText('RUN-A')).not.toBeInTheDocument();
    expect(screen.getByText('当前租户 2002')).toBeInTheDocument();
  });

  it('keeps dead-letter loading failures contextual and supports an explicit empty-state retry', async () => {
    const actor = userEvent.setup();
    let deadLetterCalls = 0;
    const deadLetterOperator: CurrentUser = {
      ...user,
      permissionCodes: ['app-studio:application:publish', 'app-studio:application:manage-acl'],
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.startsWith('/api/v1/chat-runs/dead-letters')) {
        deadLetterCalls += 1;
        return deadLetterCalls === 1
          ? failure('最终化恢复服务暂时不可用', 'AIP-APP-RECOVERY-UNAVAILABLE', 503)
          : envelope([]);
      }
      if (url.endsWith('/api/v1/ai-applications')) return envelope([]);
      throw new Error(`unexpected request: ${url}`);
    });

    renderPage('/tenant/applications', deadLetterOperator);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('死信列表加载失败：最终化恢复服务暂时不可用');
    expect(alert).toHaveTextContent('错误追踪：error-trace');
    await actor.click(within(alert).getByRole('button', { name: '重试加载' }));
    expect(await screen.findByText('当前没有等待人工处理的最终化死信')).toBeInTheDocument();
  });

  it('fails closed on a malformed dead-letter row instead of rendering a replay action', async () => {
    const deadLetterOperator: CurrentUser = {
      ...user,
      permissionCodes: ['app-studio:application:publish', 'app-studio:application:manage-acl'],
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.startsWith('/api/v1/chat-runs/dead-letters')) return envelope([{ applicationId: 'APP-1' }]);
      if (url.endsWith('/api/v1/ai-applications')) return envelope([]);
      throw new Error(`unexpected request: ${url}`);
    });

    renderPage('/tenant/applications', deadLetterOperator);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('死信响应无效');
    expect(screen.queryByRole('button', { name: /重放 undefined/ })).not.toBeInTheDocument();
  });

  it('shows a recoverable model-provider loading state instead of an unexplained blank table', async () => {
    const actor = userEvent.setup();
    let providerLoads = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/model-providers')) {
        providerLoads += 1;
        return providerLoads === 1
          ? failure('模型目录暂时不可用', 'AIP-MODEL-CATALOG-UNAVAILABLE', 503)
          : envelope([]);
      }
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('模型目录暂时不可用');
    await actor.click(within(alert).getByRole('button', { name: '重试' }));

    expect(await screen.findByText('暂无模型供应商')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增供应商' })).toBeEnabled();
  });

  it('keeps a read-only model operator on GET requests and hides every management action', async () => {
    const requests: { method: string; url: string }[] = [];
    const readOnlyUser: CurrentUser = {
      ...user,
      permissionCodes: ['model-hub:provider:read', 'model-hub:model-config:read'],
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      requests.push({ method: init?.method || 'GET', url });
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/models')) return envelope([
        { providerId: 'P-1', modelConfigId: 'M-1', modelCode: 'gpt-contract', modelName: '合同审核模型', modelType: 'CHAT', status: 'ACTIVE', connectivityStatus: 'PASSED' },
      ]);
      throw new Error(`unexpected request: ${url}`);
    });

    renderPage('/admin/model-hub', readOnlyUser);

    expect(await screen.findByRole('article', { name: '模型配置 合同审核模型' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '查看 OpenAI 生产 的模型配置' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: '新增供应商' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增模型配置' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '停用 OpenAI 生产' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '编辑 合同审核模型' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '测试 合同审核模型 连通性' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '停用 合同审核模型' })).not.toBeInTheDocument();
    expect(requests).toEqual([
      { method: 'GET', url: '/api/v1/model-providers' },
      { method: 'GET', url: '/api/v1/model-providers/P-1/models' },
    ]);
  });

  it('does not request model configuration data without the matching read permission', async () => {
    const requests: string[] = [];
    const providerReader: CurrentUser = {
      ...user,
      permissionCodes: ['model-hub:provider:read'],
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      requests.push(url);
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE' },
      ]);
      throw new Error(`unexpected request: ${url}`);
    });

    renderPage('/admin/model-hub', providerReader);

    expect(await screen.findByRole('table', { name: '模型供应商列表' })).toBeInTheDocument();
    expect(screen.getByText('无模型配置读取权限。')).toBeInTheDocument();
    expect(requests).toEqual(['/api/v1/model-providers']);
  });

  it.each([
    ['model-hub:provider:create', '新增供应商'],
    ['model-hub:model-config:create', '新增模型配置'],
    ['model-hub:provider:disable', '停用 OpenAI 生产'],
    ['model-hub:model-config:update', '编辑 合同审核模型'],
    ['model-hub:model-config:test-connectivity', '测试 合同审核模型 连通性'],
    ['model-hub:model-config:disable', '停用 合同审核模型'],
  ])('hides the exact %s action when that permission is absent', async (missingPermission, actionName) => {
    const restrictedUser: CurrentUser = {
      ...user,
      permissionCodes: user.permissionCodes.filter((permission) => permission !== missingPermission),
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/models')) return envelope([
        { providerId: 'P-1', modelConfigId: 'M-1', modelCode: 'gpt-contract', modelName: '合同审核模型', modelType: 'CHAT', status: 'ACTIVE', connectivityStatus: 'PASSED' },
      ]);
      throw new Error(`unexpected request: ${url}`);
    });

    renderPage('/admin/model-hub', restrictedUser);

    expect(await screen.findByRole('article', { name: '模型配置 合同审核模型' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: actionName })).not.toBeInTheDocument();
  });

  it('keeps provider creation failures inside the dialog and clears secret material when dismissed', async () => {
    const actor = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') return failure('供应商凭证校验失败', 'AIP-MODEL-CREDENTIAL-INVALID', 422);
      if (url.endsWith('/model-providers')) return envelope([]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    const createProviderButton = await screen.findByRole('button', { name: '新增供应商' });
    await actor.click(createProviderButton);
    const dialog = await screen.findByRole('dialog', { name: '新增模型供应商' });
    await actor.type(within(dialog).getByLabelText(/^供应商名称/), '生产模型网关');
    await actor.type(within(dialog).getByLabelText(/^API 地址/), 'https://models.example.test');
    await actor.type(within(dialog).getByLabelText(/^访问凭证/), 'top-secret-value');
    const readyDialog = await screen.findByRole('dialog', { name: '新增模型供应商' });
    await actor.click(within(readyDialog).getByRole('button', { name: '保存' }));

    expect(await screen.findByText('供应商凭证校验失败')).toBeInTheDocument();
    const failedDialog = screen.getByRole('dialog', { name: '新增模型供应商' });
    expect(within(failedDialog).getByText('错误追踪：error-trace')).toBeInTheDocument();
    await actor.click(within(failedDialog).getByRole('button', { name: '取消' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '新增模型供应商' })).not.toBeInTheDocument());
    await waitFor(() => expect(createProviderButton).toHaveFocus());
    await actor.click(createProviderButton);

    const reopened = await screen.findByRole('dialog', { name: '新增模型供应商' });
    expect(within(reopened).getByLabelText(/^访问凭证/)).toHaveValue('');
    await actor.click(within(reopened).getByRole('button', { name: '取消' }));
    await waitFor(() => expect(reopened).not.toBeInTheDocument());
  });

  it('rotates the provider creation request ID after a definite client rejection', async () => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        createBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
        return createBodies.length === 1
          ? failure('供应商输入不符合契约', 'AIP-MODEL-VALIDATION', 422)
          : envelope({ providerId: 'P-ROTATED', status: 'PENDING_APPROVAL', approvalRequestId: 'REQ-P-ROTATED' });
      }
      if (url.endsWith('/model-providers')) return envelope([]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '新增供应商' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型供应商' });
    await actor.type(within(dialog).getByLabelText(/^供应商名称/), '确定失败后重试网关');
    await actor.type(within(dialog).getByLabelText(/^API 地址/), 'https://definite-retry.example.test');
    await actor.type(within(dialog).getByLabelText(/^访问凭证/), 'provider-secret-never-log');
    expect(dialog).not.toHaveAttribute('aria-hidden', 'true');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    expect(await screen.findByText('供应商输入不符合契约')).toBeInTheDocument();
    const retryDialog = screen.getByRole('dialog', { name: '新增模型供应商' });
    expect(retryDialog).not.toHaveAttribute('aria-hidden', 'true');
    await actor.click(within(retryDialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[0].requestId).toEqual(expect.any(String));
    expect(createBodies[1].requestId).toEqual(expect.any(String));
    expect(createBodies[1].requestId).not.toBe(createBodies[0].requestId);
    await waitFor(() => expect(retryDialog).not.toBeInTheDocument());
  });

  it('keeps the provider creation request ID after an uncertain server failure', async () => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        createBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
        return createBodies.length === 1
          ? failure('上游结果暂时无法确认', 'AIP-UPSTREAM-UNAVAILABLE', 503)
          : envelope({ providerId: 'P-REPLAYED', status: 'PENDING_APPROVAL', approvalRequestId: 'REQ-P-REPLAYED' });
      }
      if (url.endsWith('/model-providers')) return envelope([]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '新增供应商' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型供应商' });
    await actor.type(within(dialog).getByLabelText(/^供应商名称/), '服务端失败重试网关');
    await actor.type(within(dialog).getByLabelText(/^API 地址/), 'https://uncertain-retry.example.test');
    await actor.type(within(dialog).getByLabelText(/^访问凭证/), 'provider-secret-never-log');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    expect(await screen.findByText('上游结果暂时无法确认')).toBeInTheDocument();
    const retryDialog = screen.getByRole('dialog', { name: '新增模型供应商' });
    await actor.click(within(retryDialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[0].requestId).toEqual(expect.any(String));
    expect(createBodies[1].requestId).toBe(createBodies[0].requestId);
    await waitFor(() => expect(retryDialog).not.toBeInTheDocument());
  });

  it('retries an uncertain provider creation with the same request ID', async () => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        createBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
        return createBodies.length === 1
          ? Promise.reject(new TypeError('供应商创建结果暂时无法确认'))
          : envelope({ providerId: 'P-IDEMPOTENT', status: 'PENDING_APPROVAL', approvalRequestId: 'REQ-P-IDEMPOTENT' });
      }
      if (url.endsWith('/model-providers')) return envelope([]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '新增供应商' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型供应商' });
    await actor.type(within(dialog).getByLabelText(/^供应商名称/), '幂等模型网关');
    await actor.type(within(dialog).getByLabelText(/^API 地址/), 'https://idempotent.example.test');
    await actor.type(within(dialog).getByLabelText(/^访问凭证/), 'provider-secret-never-log');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    expect(await within(dialog).findByText('供应商创建结果暂时无法确认')).toBeInTheDocument();
    await waitFor(() => expect(within(dialog).getByRole('button', { name: '保存' })).toBeEnabled());
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[0].requestId).toEqual(expect.any(String));
    expect(String(createBodies[0].requestId)).not.toHaveLength(0);
    expect(createBodies[1].requestId).toBe(createBodies[0].requestId);
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
  });

  it('retries an uncertain model creation with the same request ID', async () => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/models') && init?.method === 'POST') {
        createBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
        return createBodies.length === 1
          ? Promise.reject(new TypeError('模型创建结果暂时无法确认'))
          : envelope({
            providerId: 'P-1',
            modelConfigId: 'M-IDEMPOTENT',
            modelCode: 'gpt-idempotent',
            modelType: 'CHAT',
            status: 'PENDING_APPROVAL',
            approvalRequestId: 'REQ-M-IDEMPOTENT',
            connectivityStatus: 'NOT_TESTED',
          });
      }
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE', version: 7 },
      ]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '新增模型配置' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型配置' });
    await actor.type(within(dialog).getByLabelText(/^模型名称/), '幂等对话模型');
    await actor.type(within(dialog).getByLabelText(/^模型编码/), 'gpt-idempotent');
    await actor.type(within(dialog).getByLabelText(/^上下文窗口/), '32768');
    await actor.type(within(dialog).getByLabelText(/^输入价格（每千 Token）/), '0.001');
    await actor.type(within(dialog).getByLabelText(/^输出价格（每千 Token）/), '0.002');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    expect(await within(dialog).findByText('模型创建结果暂时无法确认')).toBeInTheDocument();
    await waitFor(() => expect(within(dialog).getByRole('button', { name: '保存' })).toBeEnabled());
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[0].requestId).toEqual(expect.any(String));
    expect(createBodies[1].requestId).toBe(createBodies[0].requestId);
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
  });

  it.each(uncertainCreationFailures)('keeps provider creation state, focus, secret, and request ID after $label', async ({ message, respond }) => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        createBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
        return createBodies.length === 1
          ? respond()
          : envelope({
            providerId: 'P-UNCERTAIN',
            status: 'PENDING_APPROVAL',
            approvalRequestId: 'REQ-P-UNCERTAIN',
          });
      }
      if (url.endsWith('/model-providers')) return envelope([]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '新增供应商' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型供应商' });
    await actor.type(within(dialog).getByLabelText(/^供应商名称/), '不确定结果模型网关');
    await actor.type(within(dialog).getByLabelText(/^API 地址/), 'https://uncertain-matrix.example.test');
    const credentialInput = within(dialog).getByLabelText(/^访问凭证/);
    await actor.type(credentialInput, 'retained-secret-for-retry');
    expect(dialog).not.toHaveAttribute('aria-hidden', 'true');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    expect(await within(dialog).findByText(message)).toBeInTheDocument();
    const retryDialog = screen.getByRole('dialog', { name: '新增模型供应商' });
    expect(retryDialog).not.toHaveAttribute('aria-hidden', 'true');
    const retrySaveButton = within(retryDialog).getByRole('button', { name: '保存' });
    await waitFor(() => expect(retrySaveButton).toHaveFocus());
    expect(credentialInput).toHaveValue('retained-secret-for-retry');
    await actor.click(within(retryDialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[0].requestId).toEqual(expect.any(String));
    expect(createBodies[1].requestId).toBe(createBodies[0].requestId);
    await waitFor(() => expect(retryDialog).not.toBeInTheDocument());
  });

  it.each(uncertainCreationFailures)('keeps model creation state, focus, and request ID after $label', async ({ message, respond }) => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/models') && init?.method === 'POST') {
        createBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
        return createBodies.length === 1
          ? respond()
          : envelope({
            providerId: 'P-1',
            modelConfigId: 'M-UNCERTAIN',
            modelCode: 'gpt-uncertain',
            modelType: 'CHAT',
            status: 'PENDING_APPROVAL',
            approvalRequestId: 'REQ-M-UNCERTAIN',
            connectivityStatus: 'NOT_TESTED',
          });
      }
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE', version: 7 },
      ]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '新增模型配置' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型配置' });
    const modelNameInput = within(dialog).getByLabelText(/^模型名称/);
    fireEvent.change(modelNameInput, { target: { value: '不确定结果对话模型' } });
    await actor.type(within(dialog).getByLabelText(/^模型编码/), 'gpt-uncertain');
    await actor.type(within(dialog).getByLabelText(/^上下文窗口/), '32768');
    await actor.type(within(dialog).getByLabelText(/^输入价格（每千 Token）/), '0.001');
    await actor.type(within(dialog).getByLabelText(/^输出价格（每千 Token）/), '0.002');
    expect(dialog).not.toHaveAttribute('aria-hidden', 'true');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    expect(await within(dialog).findByText(message)).toBeInTheDocument();
    const retryDialog = screen.getByRole('dialog', { name: '新增模型配置' });
    expect(retryDialog).not.toHaveAttribute('aria-hidden', 'true');
    const retrySaveButton = within(retryDialog).getByRole('button', { name: '保存' });
    await waitFor(() => expect(retrySaveButton).toHaveFocus());
    expect(modelNameInput).toHaveValue('不确定结果对话模型');
    await actor.click(within(retryDialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[0].requestId).toEqual(expect.any(String));
    expect(createBodies[1].requestId).toBe(createBodies[0].requestId);
    await waitFor(() => expect(retryDialog).not.toBeInTheDocument());
  });

  it.each([
    ['provider', '新增供应商', '新增模型供应商'],
    ['model', '新增模型配置', '新增模型配置'],
  ] as const)('freezes the %s creation command and request ID after an uncertain failure', async (kind, triggerName, dialogName) => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        createBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
        if (createBodies.length === 1) return failure('结果待确认', 'AIP-UPSTREAM-UNAVAILABLE', 503);
        return kind === 'provider'
          ? envelope({ providerId: 'P-CHANGED', status: 'PENDING_APPROVAL', approvalRequestId: 'REQ-P-CHANGED' })
          : envelope({
            providerId: 'P-1', modelConfigId: 'M-CHANGED', modelCode: 'gpt-changed', modelType: 'CHAT',
            status: 'PENDING_APPROVAL', approvalRequestId: 'REQ-M-CHANGED', connectivityStatus: 'NOT_TESTED',
          });
      }
      if (url.endsWith('/model-providers')) return envelope(kind === 'provider' ? [] : [
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE', version: 7 },
      ]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: triggerName }));
    const dialog = await screen.findByRole('dialog', { name: dialogName });
    if (kind === 'provider') {
      await actor.type(within(dialog).getByLabelText(/^供应商名称/), '字段变化模型网关');
      await actor.type(within(dialog).getByLabelText(/^API 地址/), 'https://field-change.example.test');
      await actor.type(within(dialog).getByLabelText(/^访问凭证/), 'field-change-secret');
    } else {
      await actor.type(within(dialog).getByLabelText(/^模型名称/), '字段变化对话模型');
      await actor.type(within(dialog).getByLabelText(/^模型编码/), 'gpt-changed');
      await actor.type(within(dialog).getByLabelText(/^上下文窗口/), '32768');
      await actor.type(within(dialog).getByLabelText(/^输入价格（每千 Token）/), '0.001');
      await actor.type(within(dialog).getByLabelText(/^输出价格（每千 Token）/), '0.002');
    }
    expect(dialog).not.toHaveAttribute('aria-hidden', 'true');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));
    expect(await within(dialog).findByText('结果待确认')).toBeInTheDocument();
    const retryDialog = await screen.findByRole('dialog', { name: dialogName });
    await within(retryDialog).findByRole('button', { name: '保存' });

    const frozenInput = kind === 'provider'
      ? within(retryDialog).getByLabelText(/^供应商名称/)
      : within(retryDialog).getByLabelText(/^模型编码/);
    const originalValue = kind === 'provider' ? '字段变化模型网关' : 'gpt-changed';
    expect(frozenInput).toHaveAttribute('readonly');
    await actor.type(frozenInput, kind === 'provider' ? ' v2' : '-v2');
    expect(frozenInput).toHaveValue(originalValue);
    expect(retryDialog).not.toHaveAttribute('aria-hidden', 'true');
    await actor.click(within(retryDialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[1]).toEqual(createBodies[0]);
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
  });

  it.each([
    ['provider', 'cancel'],
    ['provider', 'escape'],
    ['model', 'cancel'],
    ['model', 'escape'],
  ] as const)('recovers an uncertain %s intent with the same request ID after %s dismissal', async (kind, dismissal) => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    mockModelHubCreation(kind, createBodies, [
      () => failure('创建结果待核对', 'AIP-UPSTREAM-UNAVAILABLE', 503),
      () => successfulModelHubCreation(kind),
    ]);
    renderPage('/admin/model-hub');

    const dialog = await openAndFillModelHubCreation(actor, kind);
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));
    expect(await within(dialog).findByText('创建结果待核对')).toBeInTheDocument();
    expect(sessionStorage).toHaveLength(1);
    const persisted = sessionStorage.getItem(sessionStorage.key(0)!);
    const storedIntent = JSON.parse(persisted!) as Record<string, unknown>;
    expect(Object.keys(storedIntent).sort()).toEqual(['command', 'kind', 'requestId', 'tenantId', 'userId', 'version']);
    expect(storedIntent).toMatchObject({ version: 1, tenantId: '2001', userId: '1001', kind });
    expect(persisted).toContain(String(createBodies[0].requestId));
    expect(persisted).not.toContain('provider-secret-never-persist');
    expect(persisted).not.toContain('credential');

    if (dismissal === 'cancel') {
      await actor.click(within(dialog).getByRole('button', { name: '取消' }));
    } else if (dismissal === 'escape') {
      await actor.keyboard('{Escape}');
    }

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    const pendingNotice = await screen.findByRole('status', { name: '未决创建意图' });
    expect(within(pendingNotice).getByText(/禁止开始新的供应商或模型创建/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增供应商' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '新增模型配置' })).toBeDisabled();

    await actor.click(within(pendingNotice).getByRole('button', { name: '继续核对/重试' }));
    const reopened = await screen.findByRole('dialog', {
      name: kind === 'provider' ? '新增模型供应商' : '新增模型配置',
    });
    const frozenInput = within(reopened).getByLabelText(kind === 'provider' ? /^供应商名称/ : /^模型编码/);
    expect(frozenInput).toHaveValue(kind === 'provider' ? '未决模型网关' : 'gpt-pending');
    expect(frozenInput).toHaveAttribute('readonly');
    if (kind === 'provider') {
      const credential = within(reopened).getByLabelText(/^访问凭证/);
      expect(credential).toHaveValue('');
      await actor.type(credential, 'provider-secret-never-persist');
    }
    const retry = within(reopened).getByRole('button', { name: '保存' });
    await waitFor(() => expect(retry).toBeEnabled());
    await actor.click(retry);

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[1]).toEqual(createBodies[0]);
    await waitFor(() => expect(reopened).not.toBeInTheDocument());
    expect(sessionStorage).toHaveLength(0);
    expect(screen.queryByRole('status', { name: '未决创建意图' })).not.toBeInTheDocument();
  });

  it.each(['provider', 'model'] as const)('restores an uncertain %s intent after reload without persisting a credential', async (kind) => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    mockModelHubCreation(kind, createBodies, [
      () => failure('刷新前结果待核对', 'AIP-UPSTREAM-UNAVAILABLE', 503),
      () => successfulModelHubCreation(kind),
    ]);
    const firstView = renderPage('/admin/model-hub');

    const dialog = await openAndFillModelHubCreation(actor, kind);
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));
    expect(await within(dialog).findByText('刷新前结果待核对')).toBeInTheDocument();
    const persisted = sessionStorage.getItem(sessionStorage.key(0)!);
    expect(persisted).not.toContain('provider-secret-never-persist');
    expect(persisted).not.toContain('credential');
    firstView.unmount();

    renderPage('/admin/model-hub');
    const pendingNotice = await screen.findByRole('status', { name: '未决创建意图' });
    await actor.click(within(pendingNotice).getByRole('button', { name: '继续核对/重试' }));
    const restored = await screen.findByRole('dialog', {
      name: kind === 'provider' ? '新增模型供应商' : '新增模型配置',
    });
    expect(within(restored).getByLabelText(kind === 'provider' ? /^供应商名称/ : /^模型编码/))
      .toHaveValue(kind === 'provider' ? '未决模型网关' : 'gpt-pending');
    if (kind === 'provider') {
      const credential = within(restored).getByLabelText(/^访问凭证/);
      expect(credential).toHaveValue('');
      await actor.type(credential, 'provider-secret-never-persist');
    }
    const retry = within(restored).getByRole('button', { name: '保存' });
    await waitFor(() => expect(retry).toBeEnabled());
    await actor.click(retry);

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[1]).toEqual(createBodies[0]);
    await waitFor(() => expect(restored).not.toBeInTheDocument());
    expect(sessionStorage).toHaveLength(0);
  });

  it('replays a validated pending model command when the provider catalog fails after reload', async () => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    let catalogUnavailable = false;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/models') && init?.method === 'POST') {
        createBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
        return createBodies.length === 1
          ? failure('模型结果待核对', 'AIP-UPSTREAM-UNAVAILABLE', 503)
          : successfulModelHubCreation('model');
      }
      if (url.endsWith('/model-providers')) {
        return catalogUnavailable
          ? Promise.reject(new TypeError('供应商目录暂不可用'))
          : envelope([modelHubActiveProvider]);
      }
      if (url.endsWith('/models')) return envelope([]);
      throw new Error(`unexpected request: ${url}`);
    });
    const firstView = renderPage('/admin/model-hub');
    const dialog = await openAndFillModelHubCreation(actor, 'model');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));
    expect(await within(dialog).findByText('模型结果待核对')).toBeInTheDocument();
    firstView.unmount();

    catalogUnavailable = true;
    renderPage('/admin/model-hub');
    expect(await screen.findByText('供应商目录暂不可用')).toBeInTheDocument();
    const pendingNotice = screen.getByRole('status', { name: '未决创建意图' });
    await actor.click(within(pendingNotice).getByRole('button', { name: '继续核对/重试' }));
    const restored = await screen.findByRole('dialog', { name: '新增模型配置' });
    const retry = within(restored).getByRole('button', { name: '保存' });
    expect(within(restored).getByLabelText(/^模型编码/)).toHaveValue('gpt-pending');
    expect(retry).toBeEnabled();
    await actor.click(retry);

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[1]).toEqual(createBodies[0]);
    await waitFor(() => expect(restored).not.toBeInTheDocument());
    expect(sessionStorage).toHaveLength(0);
  });

  it('isolates an uncertain creation intent by tenant and user', async () => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    mockModelHubCreation('provider', createBodies, [
      () => failure('身份隔离结果待核对', 'AIP-UPSTREAM-UNAVAILABLE', 503),
    ]);
    const ownerView = renderPage('/admin/model-hub', user);
    const dialog = await openAndFillModelHubCreation(actor, 'provider');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));
    expect(await within(dialog).findByText('身份隔离结果待核对')).toBeInTheDocument();
    await actor.click(within(dialog).getByRole('button', { name: '取消' }));
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
    expect(sessionStorage).toHaveLength(1);
    expect(sessionStorage.key(0)).toContain('2001');
    expect(sessionStorage.key(0)).toContain('1001');
    ownerView.unmount();

    const otherUserView = renderPage('/admin/model-hub', { ...user, userId: '1002' });
    await waitFor(() => expect(screen.getByRole('button', { name: '新增供应商' })).toBeEnabled());
    expect(screen.queryByRole('status', { name: '未决创建意图' })).not.toBeInTheDocument();
    otherUserView.unmount();

    const otherTenantView = renderPage('/admin/model-hub', { ...user, tenantId: '2002' });
    await waitFor(() => expect(screen.getByRole('button', { name: '新增供应商' })).toBeEnabled());
    expect(screen.queryByRole('status', { name: '未决创建意图' })).not.toBeInTheDocument();
    otherTenantView.unmount();

    renderPage('/admin/model-hub', user);
    expect(await screen.findByRole('status', { name: '未决创建意图' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增供应商' })).toBeDisabled();
  });

  it.each([
    ['malformed JSON', '{'],
    ['oversized payload', 'x'.repeat(8_193)],
    ['invalid request UUID', JSON.stringify({
      version: 1,
      tenantId: '2001',
      userId: '1001',
      kind: 'provider',
      requestId: 'not-a-uuid',
      command: {
        providerName: '损坏草稿', providerType: 'openai-compatible',
        apiEndpoint: 'https://invalid.example.test', authType: 'api_key',
      },
    })],
    ['overlong endpoint field', JSON.stringify({
      version: 1,
      tenantId: '2001',
      userId: '1001',
      kind: 'provider',
      requestId: '11111111-1111-4111-8111-111111111111',
      command: {
        providerName: '超长地址草稿', providerType: 'openai-compatible',
        apiEndpoint: 'x'.repeat(2_049), authType: 'api_key',
      },
    })],
    ['unknown enum and injected secret field', JSON.stringify({
      version: 1,
      tenantId: '2001',
      userId: '1001',
      kind: 'provider',
      requestId: '11111111-1111-4111-8111-111111111111',
      command: {
        providerName: '注入草稿', providerType: 'unknown-provider',
        apiEndpoint: 'https://invalid.example.test', authType: 'unknown_auth',
        credentialMaterialInput: 'must-never-be-restored',
      },
    })],
  ])('fails closed and removes %s from pending creation storage', async (_label, serialized) => {
    const storageKey = 'aip:model-hub:pending-creation:v1:2001:1001';
    sessionStorage.setItem(storageKey, serialized);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/model-providers')) return envelope([modelHubActiveProvider]);
      if (url.endsWith('/models')) return envelope([]);
      throw new Error(`unexpected request: ${url}`);
    });

    renderPage('/admin/model-hub');

    await waitFor(() => expect(screen.getByRole('button', { name: '新增供应商' })).toBeEnabled());
    expect(screen.queryByRole('status', { name: '未决创建意图' })).not.toBeInTheDocument();
    expect(sessionStorage.getItem(storageKey)).toBeNull();
  });

  it('keeps an idempotency-conflict provider command frozen under the original request ID', async () => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    mockModelHubCreation('provider', createBodies, [
      () => failure('创建请求编号已被不同命令占用', 'AIP-MDL-CREATIONIDEMPOTENCYCONFLICT', 409),
      () => successfulModelHubCreation('provider'),
    ]);
    renderPage('/admin/model-hub');

    const dialog = await openAndFillModelHubCreation(actor, 'provider');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));
    expect(await within(dialog).findByText('创建请求编号已被不同命令占用')).toBeInTheDocument();
    const reconciliation = within(dialog).getByRole('status', { name: '未决创建命令' });
    expect(within(reconciliation).getByText(/不得生成新请求编号/)).toBeInTheDocument();
    expect(within(reconciliation).getByText(/原命令和原凭证/)).toBeInTheDocument();
    const frozenName = within(dialog).getByLabelText(/^供应商名称/);
    expect(frozenName).toHaveAttribute('readonly');
    await actor.type(frozenName, '不得修改');
    expect(frozenName).toHaveValue('未决模型网关');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[1]).toEqual(createBodies[0]);
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
    expect(sessionStorage).toHaveLength(0);
  });

  it('clears an uncertain intent only after a safe definite rejection and then rotates the request ID', async () => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    mockModelHubCreation('provider', createBodies, [
      () => failure('首次结果待核对', 'AIP-UPSTREAM-UNAVAILABLE', 503),
      () => failure('原命令已确定拒绝', 'AIP-MODEL-VALIDATION', 422),
      () => successfulModelHubCreation('provider'),
    ]);
    renderPage('/admin/model-hub');

    const dialog = await openAndFillModelHubCreation(actor, 'provider');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));
    expect(await within(dialog).findByText('首次结果待核对')).toBeInTheDocument();
    expect(sessionStorage).toHaveLength(1);
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    expect(await within(dialog).findByText('原命令已确定拒绝')).toBeInTheDocument();
    expect(sessionStorage).toHaveLength(0);
    const nextSave = await within(dialog).findByRole('button', { name: '保存' }, { timeout: 5_000 });
    const editableName = within(dialog).getByLabelText(/^供应商名称/);
    expect(editableName).not.toHaveAttribute('readonly');
    await actor.type(editableName, ' v2');
    await actor.click(nextSave);

    await waitFor(() => expect(createBodies).toHaveLength(3));
    expect(createBodies[1].requestId).toBe(createBodies[0].requestId);
    expect(createBodies[2].requestId).not.toBe(createBodies[0].requestId);
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
  });

  it('rotates the model creation request ID after a definite client rejection', async () => {
    const actor = userEvent.setup();
    const createBodies: Array<Record<string, unknown>> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/models') && init?.method === 'POST') {
        createBodies.push(JSON.parse(String(init.body)) as Record<string, unknown>);
        return createBodies.length === 1
          ? failure('模型输入不符合契约', 'AIP-MODEL-VALIDATION', 422)
          : envelope({
            providerId: 'P-1', modelConfigId: 'M-ROTATED', modelCode: 'gpt-rotated', modelType: 'CHAT',
            status: 'PENDING_APPROVAL', approvalRequestId: 'REQ-M-ROTATED', connectivityStatus: 'NOT_TESTED',
          });
      }
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE', version: 7 },
      ]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '新增模型配置' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型配置' });
    await actor.type(within(dialog).getByLabelText(/^模型名称/), '确定失败对话模型');
    await actor.type(within(dialog).getByLabelText(/^模型编码/), 'gpt-rotated');
    await actor.type(within(dialog).getByLabelText(/^上下文窗口/), '32768');
    await actor.type(within(dialog).getByLabelText(/^输入价格（每千 Token）/), '0.001');
    await actor.type(within(dialog).getByLabelText(/^输出价格（每千 Token）/), '0.002');
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));
    expect(await within(dialog).findByText('模型输入不符合契约')).toBeInTheDocument();
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBodies).toHaveLength(2));
    expect(createBodies[1].requestId).not.toBe(createBodies[0].requestId);
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
  });

  it('submits only operator-provided model limits and prices through the real contract', async () => {
    const actor = userEvent.setup();
    let createBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        createBody = JSON.parse(String(init.body));
        return envelope({ providerId: 'P-1', modelConfigId: 'M-NEW', modelCode: 'gpt-contract', modelType: 'CHAT', status: 'PENDING_APPROVAL', approvalRequestId: 'REQ-M-NEW', connectivityStatus: 'NOT_TESTED' });
      }
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE', version: 7 },
      ]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    expect(await screen.findByRole('heading', { level: 1, name: '模型中心' })).toBeInTheDocument();
    await actor.click(await screen.findByRole('button', { name: '新增模型配置' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型配置' });
    const modelTypeSelect = within(dialog).getByLabelText(/^模型类型/);
    expect(Array.from(modelTypeSelect.querySelectorAll('option')).map((option) => option.value)).toEqual([
      'CHAT',
      'EMBEDDING',
    ]);
    expect(within(modelTypeSelect).queryByRole('option', { name: '重排' })).not.toBeInTheDocument();
    expect(within(dialog).getByLabelText(/^上下文窗口/)).toHaveValue(null);
    expect(within(dialog).getByLabelText('最大输出 Token')).toHaveValue(null);
    expect(within(dialog).getByLabelText(/^输入价格（每千 Token）/)).toHaveValue('');
    expect(within(dialog).getByLabelText(/^输出价格（每千 Token）/)).toHaveValue('');
    expect(within(dialog).getByRole('button', { name: '保存' })).toBeDisabled();

    await actor.type(within(dialog).getByLabelText(/^模型名称/), '合同审核模型');
    await actor.type(within(dialog).getByLabelText(/^模型编码/), 'gpt-contract');
    const contextInput = within(dialog).getByLabelText(/^上下文窗口/);
    const maxOutputInput = within(dialog).getByLabelText('最大输出 Token');
    const inputPriceInput = within(dialog).getByLabelText(/^输入价格（每千 Token）/);
    const outputPriceInput = within(dialog).getByLabelText(/^输出价格（每千 Token）/);
    await actor.type(contextInput, '32768');
    fireEvent.change(maxOutputInput, { target: { value: '0' } });
    fireEvent.change(inputPriceInput, { target: { value: '0.123456789' } });
    await actor.type(outputPriceInput, '0.00000001');
    expect(within(dialog).getByRole('button', { name: '保存' })).toBeDisabled();
    fireEvent.change(maxOutputInput, { target: { value: '4096' } });
    fireEvent.change(inputPriceInput, { target: { value: '9999999999.99999999' } });
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBody).toEqual({
      requestId: expect.any(String),
      modelCode: 'gpt-contract',
      modelName: '合同审核模型',
      modelType: 'CHAT',
      contextWindow: 32768,
      embeddingDimension: null,
      inputPricePer1k: '9999999999.99999999',
      outputPricePer1k: '0.00000001',
      options: { providerKind: 'openai-compatible', maxTokens: 4096 },
    }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '新增模型配置' })).not.toBeInTheDocument());
    expect(await screen.findByText('模型 M-NEW 待审批，连通后启用。')).toBeInTheDocument();
  });

  it('never renders an older provider model response under the newly selected provider', async () => {
    const actor = userEvent.setup();
    const staleModels = deferred<Response>();
    let providerAModelLoads = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-A', providerCode: 'a', providerName: '供应商 A', endpoint: 'https://a.invalid', status: 'ACTIVE' },
        { providerId: 'P-B', providerCode: 'b', providerName: '供应商 B', endpoint: 'https://b.invalid', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/model-providers/P-A/models')) {
        providerAModelLoads += 1;
        return envelope([{ providerId: 'P-A', modelConfigId: `M-A-${providerAModelLoads}`, modelCode: 'a', modelName: '模型 A', modelType: 'CHAT', status: 'ACTIVE', connectivityStatus: 'PASSED' }]);
      }
      if (url.endsWith('/model-providers/P-B/models')) return staleModels.promise;
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    expect(await screen.findByText('模型 A')).toBeInTheDocument();
    const providerRows = screen.getAllByRole('row');
    await actor.click(within(providerRows.find((row) => row.textContent?.includes('供应商 B'))!).getByRole('button', { name: '查看 供应商 B 的模型配置' }));
    await actor.click(within(providerRows.find((row) => row.textContent?.includes('供应商 A'))!).getByRole('button', { name: '查看 供应商 A 的模型配置' }));
    expect(await screen.findByText('模型 A')).toBeInTheDocument();
    await act(async () => {
      staleModels.resolve(await envelope([{ providerId: 'P-B', modelConfigId: 'M-B', modelCode: 'b', modelName: '模型 B', modelType: 'CHAT', status: 'ACTIVE', connectivityStatus: 'PASSED' }]));
    });

    await waitFor(() => expect(screen.queryByText('模型 B')).not.toBeInTheDocument());
    const modelPanel = document.querySelector('aside');
    expect(modelPanel).not.toBeNull();
    expect(within(modelPanel!).getByText('供应商 A')).toBeInTheDocument();
    expect(screen.getByText('模型 A')).toBeInTheDocument();
  });

  it('makes a pending model approval lifecycle visible and actionable', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE', version: 4 },
      ]);
      if (url.endsWith('/models')) return envelope([
        { providerId: 'P-1', modelConfigId: 'M-PENDING', modelCode: 'gpt-contract', modelName: '合同审核模型', modelType: 'CHAT', status: 'PENDING_APPROVAL', connectivityStatus: 'NOT_TESTED', version: 6 },
      ]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    const model = await screen.findByRole('article', { name: '模型配置 合同审核模型' });
    expect(within(model).getByText('待审批')).toBeInTheDocument();
    expect(within(model).getAllByText(/M-PENDING/)).toHaveLength(2);
    expect(within(model).getByText(/审批通过后仍需连通性测试/)).toBeInTheDocument();
    expect(within(model).getByRole('link', { name: '前往审批中心' })).toHaveAttribute('href', '/admin/approvals');
    expect(within(model).getByRole('button', { name: '测试 合同审核模型 连通性' })).toBeDisabled();
    expect(within(model).getByRole('button', { name: '停用 合同审核模型' })).toBeDisabled();
    expect(within(model).getByRole('button', { name: '停用 合同审核模型' }))
      .toHaveAttribute('title', '待审批模型配置不可停用，请先完成或撤销审批');
  });

  it('lets an authorized operator cancel a pending provider approval with an explicit invalidation warning', async () => {
    const actor = userEvent.setup();
    let cancellationBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/P-PENDING/disable') && init?.method === 'POST') {
        cancellationBody = JSON.parse(String(init.body));
        return envelope({
          providerId: 'P-PENDING', providerName: '待审批供应商', status: 'DISABLED', version: 3,
        });
      }
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-PENDING', providerCode: 'openai-compatible', providerName: '待审批供应商', endpoint: 'https://models.example.test', status: 'PENDING_APPROVAL', approvalRequestId: 'REQ-P-PENDING', version: 2 },
      ]);
      return envelope([]);
    });

    renderPage('/admin/model-hub');

    const cancel = await screen.findByRole('button', { name: '撤销审批 待审批供应商' });
    expect(cancel).toBeEnabled();
    await actor.click(cancel);

    const dialog = await screen.findByRole('dialog', { name: '撤销供应商审批' });
    const snapshot = within(dialog).getByRole('group', { name: '危险操作对象快照' });
    expect(within(snapshot).getByText('P-PENDING')).toBeInTheDocument();
    expect(within(snapshot).getByText('2001')).toBeInTheDocument();
    expect(within(snapshot).getByText('PENDING_APPROVAL（待审批）')).toBeInTheDocument();
    expect(within(snapshot).getByText('2')).toBeInTheDocument();
    expect(within(snapshot).getByText('PENDING_APPROVAL → DISABLED')).toBeInTheDocument();
    expect(within(snapshot).getByText('不可原地恢复')).toBeInTheDocument();
    expect(within(snapshot).getByText('REQ-P-PENDING')).toBeInTheDocument();
    expect(within(dialog).getByText(/审批中心状态可能短暂延迟/)).toBeInTheDocument();
    expect(within(dialog).getByText(/若人工决定已先完成，将保留该审批终态/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '确认撤销' })).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText(/^撤销理由/), {
      target: { value: '地址录入错误，重新提交审批' },
    });
    await actor.click(await screen.findByRole('button', { name: '确认撤销' }));

    await waitFor(() => expect(cancellationBody).toEqual({
      reason: '地址录入错误，重新提交审批',
      expectedVersion: 2,
    }));
    expect(await screen.findByText('模型供应商 待审批供应商已停用；审批撤销请求已提交，审批中心正在同步。')).toBeInTheDocument();
    expect(screen.getByText('操作追踪：test-trace')).toBeInTheDocument();
  });

  it('keeps API-only provider models read-only in the generic console without dropping specialist options', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-AZURE', providerCode: 'azure-openai', providerName: 'Azure 企业模型', endpoint: 'https://aip.openai.azure.com', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/models')) return envelope([
        { providerId: 'P-AZURE', modelConfigId: 'M-AZURE', modelCode: 'gpt4o-prod', modelName: 'Azure GPT-4o', modelType: 'CHAT', status: 'ACTIVE', connectivityStatus: 'PASSED' },
      ]);
      return envelope([]);
    });

    renderPage('/admin/model-hub');

    const providerSelector = await screen.findByRole('button', { name: '查看 Azure 企业模型 的模型配置' });
    expect(providerSelector).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText(/由受认证 API 管理模型配置/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增模型配置' })).toBeDisabled();
    const model = await screen.findByRole('article', { name: '模型配置 Azure GPT-4o' });
    expect(within(model).queryByRole('button', { name: '编辑 Azure GPT-4o' })).not.toBeInTheDocument();
    expect(within(model).getByRole('button', { name: '测试 Azure GPT-4o 连通性' })).toBeEnabled();
  });

  it('keeps a historical local provider read-only when the local runtime capability is disabled', async () => {
    const actor = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-LOCAL', providerCode: 'local-deterministic', providerName: '历史本地运行时', endpoint: 'local://deterministic', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/models')) return envelope([
        { providerId: 'P-LOCAL', modelConfigId: 'M-LOCAL', modelCode: 'local-chat', modelName: '历史本地模型', modelType: 'CHAT', status: 'ACTIVE', connectivityStatus: 'PASSED' },
      ]);
      return envelope([]);
    });

    renderWithIsolatedPortal(<FluentProvider theme={enterpriseLightTheme}>
      <ModelHubPage user={user} localDeterministicProviderEnabled={false} />
    </FluentProvider>);

    expect(await screen.findByText(/本地确定性运行时能力已关闭/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增模型配置' })).toBeDisabled();
    const model = await screen.findByRole('article', { name: '模型配置 历史本地模型' });
    expect(within(model).queryByRole('button', { name: '编辑 历史本地模型' })).not.toBeInTheDocument();
    expect(within(model).getByRole('button', { name: '测试 历史本地模型 连通性' })).toBeDisabled();

    await actor.click(screen.getByRole('button', { name: '新增供应商' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型供应商' });
    expect(within(dialog).queryByRole('option', { name: /本地确定性运行时/ })).not.toBeInTheDocument();
  });

  it('constrains local deterministic embedding dimensions to the backend enterprise limit', async () => {
    const actor = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-LOCAL', providerCode: 'local-deterministic', providerName: '本地验收运行时', endpoint: 'local://deterministic', status: 'ACTIVE' },
      ]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '新增模型配置' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型配置' });
    await actor.type(within(dialog).getByLabelText(/^模型名称/), '本地向量模型');
    await actor.type(within(dialog).getByLabelText(/^模型编码/), 'local-embedding');
    fireEvent.change(within(dialog).getByLabelText(/^模型类型/), { target: { value: 'EMBEDDING' } });
    await actor.type(within(dialog).getByLabelText(/^上下文窗口/), '8192');
    await actor.type(within(dialog).getByLabelText(/^输入价格（每千 Token）/), '0');
    await actor.type(within(dialog).getByLabelText(/^输出价格（每千 Token）/), '0');
    const dimension = within(dialog).getByLabelText(/^向量维度/);
    expect(dimension).toHaveAttribute('min', '1');
    expect(dimension).toHaveAttribute('max', '4096');
    expect(within(dialog).getByText(/1 到 4096/)).toBeInTheDocument();
    expect(within(dialog).getByText(/运行时 dimensions/)).toBeInTheDocument();

    fireEvent.change(dimension, { target: { value: '4097' } });
    expect(within(dialog).getByText('向量维度不得大于 4096。')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '保存' })).toBeDisabled();
    fireEvent.change(dimension, { target: { value: '4096' } });
    expect(within(dialog).queryByText(/不得大于 4096/)).not.toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '保存' })).toBeEnabled();
  });

  it('renders rejected and disabled lifecycle states in user language and blocks unsafe retesting', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: '已停服供应商', endpoint: 'https://models.example.test', status: 'DISABLED' },
      ]);
      if (url.endsWith('/models')) return envelope([
        { providerId: 'P-1', modelConfigId: 'M-REJECTED', modelCode: 'rejected-model', modelName: '被拒绝模型', modelType: 'CHAT', status: 'REJECTED', connectivityStatus: 'NOT_TESTED' },
      ]);
      return envelope([]);
    });

    renderPage('/admin/model-hub');

    expect(await screen.findByText('已停用')).toBeInTheDocument();
    expect(screen.queryByText('DISABLED')).not.toBeInTheDocument();
    expect(screen.getByText(/供应商审批通过并启用后/)).toBeInTheDocument();
    const model = await screen.findByRole('article', { name: '模型配置 被拒绝模型' });
    expect(within(model).getByText('审批已拒绝')).toBeInTheDocument();
    expect(within(model).getByText(/拒绝状态下不会执行连通测试/)).toBeInTheDocument();
    expect(within(model).getByRole('button', { name: '测试 被拒绝模型 连通性' })).toBeDisabled();
  });

  it('edits a model through the real update contract without dropping runtime options', async () => {
    const actor = userEvent.setup();
    let updateBody: Record<string, unknown> | undefined;
    let updateUrl = '';
    let updated = false;
    const original = {
      providerId: 'P-1', modelConfigId: 'M-EDIT', modelCode: 'gpt-contract', modelName: '合同审核模型', modelType: 'CHAT',
      contextWindow: 32768, embeddingDimension: null, inputPricePer1k: '0.0015', outputPricePer1k: '0.003',
      providerKind: 'openai-compatible', temperature: '0.2', topP: '0.9', maxTokens: 4096,
      status: 'ACTIVE', connectivityStatus: 'PASSED', connectivityMessage: '最近探测通过', version: 9,
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'PUT') {
        updateUrl = url;
        updateBody = JSON.parse(String(init.body));
        updated = true;
        return envelope({ ...original, modelName: '合同审核模型 v2', status: 'PENDING_APPROVAL', version: 10 });
      }
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', providerKind: 'openai-compatible', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/models')) return envelope([updated ? { ...original, modelName: '合同审核模型 v2', status: 'PENDING_APPROVAL' } : original]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    const model = await screen.findByRole('article', { name: '模型配置 合同审核模型' });
    await actor.click(within(model).getByRole('button', { name: '编辑 合同审核模型' }));
    const dialog = await screen.findByRole('dialog', { name: '编辑模型配置' });
    expect(within(dialog).getByLabelText(/^上下文窗口/)).toHaveValue(32768);
    expect(within(dialog).getByLabelText('最大输出 Token')).toHaveValue(4096);
    await actor.clear(within(dialog).getByLabelText(/^模型名称/));
    await actor.type(within(dialog).getByLabelText(/^模型名称/), '合同审核模型 v2');
    await actor.click(within(dialog).getByRole('button', { name: '保存并提交审批' }));

    await waitFor(() => expect(updateBody).toEqual({
      modelCode: 'gpt-contract', modelName: '合同审核模型 v2', modelType: 'CHAT', contextWindow: 32768,
      embeddingDimension: null, inputPricePer1k: '0.0015', outputPricePer1k: '0.003',
      options: { providerKind: 'openai-compatible', maxTokens: 4096, temperature: 0.2, topP: 0.9 },
      expectedVersion: 9,
    }));
    expect(updateUrl).toContain('/api/v1/model-providers/P-1/models/M-EDIT');
    expect(await screen.findByText('模型 M-EDIT 已更新，待审批。')).toBeInTheDocument();
  });

  it('creates a no-auth local runtime without inventing credential material', async () => {
    const actor = userEvent.setup();
    let createBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      if (init?.method === 'POST') {
        createBody = JSON.parse(String(init.body));
        return envelope({ providerId: 'P-LOCAL', status: 'PENDING_APPROVAL', approvalRequestId: 'REQ-P-LOCAL' });
      }
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '新增供应商' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型供应商' });
    await actor.type(within(dialog).getByLabelText(/^API 地址/), 'https://api.example.com/v1');
    await actor.type(within(dialog).getByLabelText(/^访问凭证/), 'must-be-cleared');
    fireEvent.change(within(dialog).getByLabelText(/^供应商类型/), { target: { value: 'local-deterministic' } });
    await actor.type(within(dialog).getByLabelText(/^供应商名称/), '本地验收运行时');

    expect(within(dialog).getByLabelText(/^API 地址/)).toHaveValue('local://deterministic');
    expect(within(dialog).getByLabelText(/^API 地址/)).toHaveAttribute('readonly');
    expect(within(dialog).getByLabelText(/^认证方式/)).toHaveValue('no_auth');
    expect(within(dialog).getByLabelText(/^认证方式/)).toBeDisabled();
    expect(within(dialog).queryByLabelText(/^访问凭证/)).not.toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText(/^供应商类型/), { target: { value: 'openai-compatible' } });
    expect(within(dialog).getByLabelText(/^API 地址/)).toHaveValue('');
    expect(within(dialog).getByLabelText(/^认证方式/)).toHaveValue('api_key');
    expect(within(dialog).getByLabelText(/^认证方式/)).toBeDisabled();
    expect(within(dialog).getByLabelText(/^访问凭证/)).toHaveValue('');
    fireEvent.change(within(dialog).getByLabelText(/^供应商类型/), { target: { value: 'local-deterministic' } });
    expect(within(dialog).getByRole('button', { name: '保存' })).toBeEnabled();
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBody).toEqual({
      requestId: expect.any(String),
      providerName: '本地验收运行时',
      providerType: 'local-deterministic',
      apiEndpoint: 'local://deterministic',
      authType: 'no_auth',
      credentialMaterialInput: null,
    }));
  });

  it('offers only console-supported providers and clears sensitive state on every type change', async () => {
    const actor = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => envelope([]));
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '新增供应商' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型供应商' });
    const providerTypeSelect = within(dialog).getByLabelText(/^供应商类型/);
    const apiEndpointInput = within(dialog).getByLabelText(/^API 地址/);
    const authTypeSelect = within(dialog).getByLabelText(/^认证方式/);

    expect(Array.from(providerTypeSelect.querySelectorAll('option')).map((option) => option.value)).toEqual([
      'openai-compatible',
      'ollama',
      'mistral-ai',
      'local-deterministic',
    ]);
    expect(within(providerTypeSelect).queryByRole('option', { name: 'Azure OpenAI' })).not.toBeInTheDocument();
    expect(within(providerTypeSelect).queryByRole('option', { name: 'Amazon Bedrock' })).not.toBeInTheDocument();
    expect(within(providerTypeSelect).queryByRole('option', { name: 'Google Vertex AI' })).not.toBeInTheDocument();
    expect(within(providerTypeSelect).queryByRole('option', { name: '智谱 AI' })).not.toBeInTheDocument();
    expect(authTypeSelect).toHaveValue('api_key');
    expect(authTypeSelect).toBeDisabled();
    expect(within(dialog).getByText(/公网 HTTPS\/443 地址/)).toBeInTheDocument();

    await actor.type(apiEndpointInput, 'https://openai.example.test/v1');
    await actor.type(within(dialog).getByLabelText(/^访问凭证/), 'openai-secret');
    fireEvent.change(providerTypeSelect, { target: { value: 'mistral-ai' } });
    expect(apiEndpointInput).toHaveValue('');
    expect(authTypeSelect).toHaveValue('api_key');
    expect(authTypeSelect).toBeDisabled();
    expect(within(dialog).getByLabelText(/^访问凭证/)).toHaveValue('');

    await actor.type(apiEndpointInput, 'https://mistral.example.test/v1');
    await actor.type(within(dialog).getByLabelText(/^访问凭证/), 'mistral-secret');
    fireEvent.change(providerTypeSelect, { target: { value: 'ollama' } });
    expect(apiEndpointInput).toHaveValue('');
    expect(authTypeSelect).toHaveValue('no_auth');
    expect(authTypeSelect).toBeDisabled();
    expect(within(dialog).queryByLabelText(/^访问凭证/)).not.toBeInTheDocument();

    await actor.type(apiEndpointInput, 'https://ollama.example.test');
    fireEvent.change(providerTypeSelect, { target: { value: 'openai-compatible' } });
    expect(apiEndpointInput).toHaveValue('');
    expect(authTypeSelect).toHaveValue('api_key');
    expect(authTypeSelect).toBeDisabled();
    expect(within(dialog).getByLabelText(/^访问凭证/)).toHaveValue('');

    await actor.type(apiEndpointInput, 'https://openai-again.example.test/v1');
    await actor.type(within(dialog).getByLabelText(/^访问凭证/), 'second-openai-secret');
    fireEvent.change(providerTypeSelect, { target: { value: 'local-deterministic' } });
    expect(apiEndpointInput).toHaveValue('local://deterministic');
    expect(authTypeSelect).toHaveValue('no_auth');
    expect(authTypeSelect).toBeDisabled();
    expect(within(dialog).queryByLabelText(/^访问凭证/)).not.toBeInTheDocument();

    fireEvent.change(providerTypeSelect, { target: { value: 'openai-compatible' } });
    expect(apiEndpointInput).toHaveValue('');
    expect(authTypeSelect).toHaveValue('api_key');
    expect(authTypeSelect).toBeDisabled();
    expect(within(dialog).getByLabelText(/^访问凭证/)).toHaveValue('');

    expect(dialog).not.toHaveAttribute('aria-hidden', 'true');
    await actor.click(within(dialog).getByRole('button', { name: '取消' }));
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
  });

  it('preserves an unknown maximum output limit by omitting maxTokens', async () => {
    const actor = userEvent.setup();
    let createBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        createBody = JSON.parse(String(init.body));
        return envelope({
          providerId: 'P-1',
          modelConfigId: 'M-NEW',
          modelCode: 'provider-default-limit',
          modelType: 'CHAT',
          status: 'INACTIVE',
          connectivityStatus: 'NOT_TESTED',
        });
      }
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE' },
      ]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '新增模型配置' }));
    const dialog = await screen.findByRole('dialog', { name: '新增模型配置' });
    await actor.type(within(dialog).getByLabelText(/^模型名称/), '供应商默认上限模型');
    await actor.type(within(dialog).getByLabelText(/^模型编码/), 'provider-default-limit');
    await actor.type(within(dialog).getByLabelText(/^上下文窗口/), '16384');
    await actor.type(within(dialog).getByLabelText(/^输入价格（每千 Token）/), '0');
    await actor.type(within(dialog).getByLabelText(/^输出价格（每千 Token）/), '0');
    expect(within(dialog).getByLabelText('最大输出 Token')).toHaveValue(null);
    expect(within(dialog).getByRole('button', { name: '保存' })).toBeEnabled();
    await actor.click(within(dialog).getByRole('button', { name: '保存' }));

    await waitFor(() => expect(createBody).toMatchObject({
      contextWindow: 16384,
      inputPricePer1k: '0',
      outputPricePer1k: '0',
      options: { providerKind: 'openai-compatible' },
    }));
    expect((createBody?.options as Record<string, unknown>).maxTokens).toBeUndefined();
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '新增模型配置' })).not.toBeInTheDocument());
  });

  it('requires a reason and tracks provider disable submission and success', async () => {
    const actor = userEvent.setup();
    const disableResponse = deferred<Response>();
    let disableBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/P-1/disable') && init?.method === 'POST') {
        disableBody = JSON.parse(String(init.body));
        return disableResponse.promise;
      }
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE', version: 7 },
      ]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '停用 OpenAI 生产' }));
    const dialog = await screen.findByRole('dialog', { name: '停用模型供应商' });
    expect(within(dialog).getByText('OpenAI 生产')).toBeInTheDocument();
    const providerSnapshot = within(dialog).getByRole('group', { name: '危险操作对象快照' });
    expect(within(providerSnapshot).getByText('P-1')).toBeInTheDocument();
    expect(within(providerSnapshot).getByText('2001')).toBeInTheDocument();
    expect(within(providerSnapshot).getByText('ACTIVE（已启用）')).toBeInTheDocument();
    expect(within(providerSnapshot).getByText('7')).toBeInTheDocument();
    expect(within(providerSnapshot).getByText('ACTIVE → DISABLED')).toBeInTheDocument();
    expect(within(providerSnapshot).getByText('不可原地恢复')).toBeInTheDocument();
    expect(within(dialog).getByText(/关联模型配置/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '确认停用' })).toBeDisabled();
    await actor.type(within(dialog).getByLabelText(/^停用理由/), '供应商合同终止，已安排迁移');
    await actor.click(within(dialog).getByRole('button', { name: '确认停用' }));
    expect(within(dialog).getByRole('button', { name: '正在停用' })).toBeDisabled();

    await act(async () => {
      disableResponse.resolve(await envelope({ providerId: 'P-1', status: 'DISABLED', version: 8 }));
    });

    const operationResult = await screen.findByLabelText('模型中心操作结果');
    expect(operationResult).toHaveTextContent('模型供应商 OpenAI 生产已停用。');
    expect(screen.getByText('操作追踪：test-trace')).toBeInTheDocument();
    expect(disableBody).toEqual({ reason: '供应商合同终止，已安排迁移', expectedVersion: 7 });
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '停用模型供应商' })).not.toBeInTheDocument());
    await waitFor(() => expect(operationResult).toHaveFocus());
  });

  it('focuses stable disable feedback when the post-success provider refresh fails', async () => {
    const actor = userEvent.setup();
    let providerLoads = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/P-1/disable') && init?.method === 'POST') {
        return envelope({ providerId: 'P-1', status: 'DISABLED', version: 8 });
      }
      if (url.endsWith('/model-providers')) {
        providerLoads += 1;
        return providerLoads === 1
          ? envelope([
            { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE', version: 7 },
          ])
          : failure('停用成功，但模型目录刷新失败', 'AIP-MODEL-CATALOG-UNAVAILABLE', 503);
      }
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '停用 OpenAI 生产' }));
    const dialog = await screen.findByRole('dialog', { name: '停用模型供应商' });
    await actor.type(within(dialog).getByLabelText(/^停用理由/), '供应商合同终止，已安排迁移');
    await actor.click(within(dialog).getByRole('button', { name: '确认停用' }));

    const operationResult = await screen.findByLabelText('模型中心操作结果');
    expect(operationResult).toHaveTextContent('模型供应商 OpenAI 生产已停用。');
    expect(await screen.findByText('停用成功，但模型目录刷新失败')).toBeInTheDocument();
    await waitFor(() => expect(operationResult).toHaveFocus());
  });

  it('freezes the tenant in a disable snapshot and blocks submission after tenant drift', async () => {
    const actor = userEvent.setup();
    let disableCalls = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/P-1/disable') && init?.method === 'POST') {
        disableCalls += 1;
        return envelope({ providerId: 'P-1', status: 'DISABLED' });
      }
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE', version: 7 },
      ]);
      return envelope([]);
    });
    const view = renderWithIsolatedPortal(
      <FluentProvider theme={enterpriseLightTheme}><ModelHubPage user={user} /></FluentProvider>,
    );

    await actor.click(await screen.findByRole('button', { name: '停用 OpenAI 生产' }));
    const dialog = await screen.findByRole('dialog', { name: '停用模型供应商' });
    const snapshot = within(dialog).getByRole('group', { name: '危险操作对象快照' });
    expect(within(snapshot).getByText('2001')).toBeInTheDocument();

    view.rerender(
      <FluentProvider theme={enterpriseLightTheme}>
        <ModelHubPage user={{ ...user, tenantId: '2002' }} />
      </FluentProvider>,
    );
    const driftedDialog = await screen.findByRole('dialog', { name: '停用模型供应商' });
    const driftedSnapshot = within(driftedDialog).getByRole('group', { name: '危险操作对象快照' });
    expect(within(driftedSnapshot).getByText('2001')).toBeInTheDocument();
    expect(within(driftedSnapshot).queryByText('2002')).not.toBeInTheDocument();
    fireEvent.change(within(driftedDialog).getByLabelText(/^停用理由/), { target: { value: '租户切换后不得提交旧上下文操作' } });
    const submittedDialog = await screen.findByRole('dialog', { name: '停用模型供应商' });
    await actor.click(within(submittedDialog).getByRole('button', { name: '确认停用' }));

    expect(await within(
      await screen.findByRole('dialog', { name: '停用模型供应商' }),
    ).findByText('当前租户上下文已变化，请关闭确认框并重新选择操作对象。')).toBeInTheDocument();
    expect(disableCalls).toBe(0);
  });

  it('keeps model disable failures in context with a trace id', async () => {
    const actor = userEvent.setup();
    let disableBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/models/M-1/disable') && init?.method === 'POST') {
        disableBody = JSON.parse(String(init.body));
        return failure('模型仍被生产应用引用', 'AIP-MODEL-IN-USE', 409);
      }
      if (url.endsWith('/model-providers')) return envelope([
        { providerId: 'P-1', providerCode: 'openai-compatible', providerName: 'OpenAI 生产', endpoint: 'https://models.example.test', status: 'ACTIVE', version: 7 },
      ]);
      if (url.endsWith('/models')) return envelope([
        { providerId: 'P-1', modelConfigId: 'M-1', modelCode: 'gpt-contract', modelName: '合同审核模型', modelType: 'CHAT', status: 'ACTIVE', connectivityStatus: 'PASSED', version: 9 },
      ]);
      return envelope([]);
    });
    renderPage('/admin/model-hub');

    await actor.click(await screen.findByRole('button', { name: '停用 合同审核模型' }));
    const dialog = await screen.findByRole('dialog', { name: '停用模型配置' });
    const modelSnapshot = within(dialog).getByRole('group', { name: '危险操作对象快照' });
    expect(within(modelSnapshot).getByText('M-1')).toBeInTheDocument();
    expect(within(modelSnapshot).getByText('P-1')).toBeInTheDocument();
    expect(within(modelSnapshot).getByText('2001')).toBeInTheDocument();
    expect(within(modelSnapshot).getByText('ACTIVE（已启用）')).toBeInTheDocument();
    expect(within(modelSnapshot).getByText('9')).toBeInTheDocument();
    expect(within(modelSnapshot).getByText('ACTIVE → DEPRECATED')).toBeInTheDocument();
    expect(within(dialog).getByText(/依赖该配置的应用/)).toBeInTheDocument();
    await actor.type(within(dialog).getByLabelText(/^停用理由/), '模型输出质量不再满足生产要求');
    await actor.click(within(dialog).getByRole('button', { name: '确认停用' }));

    expect(await within(dialog).findByText('模型仍被生产应用引用')).toBeInTheDocument();
    expect(within(dialog).getByText('错误追踪：error-trace')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '确认停用' })).toBeEnabled();
    expect(disableBody).toEqual({ reason: '模型输出质量不再满足生产要求', expectedVersion: 9 });
    await actor.click(within(dialog).getByRole('button', { name: '取消' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '停用模型配置' })).not.toBeInTheDocument());
  });

  it('saves the selected role permission matrix through the IAM command endpoint', async () => {
    const actor = userEvent.setup();
    const requests: { url: string; body?: Record<string, unknown> }[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input); requests.push({ url, body: init?.body ? JSON.parse(String(init.body)) : undefined });
      if (init?.method === 'PUT') return envelope({ resourceId: 'R-1', status: 'SAVED' });
      if (url.endsWith('/iam/roles')) return envelope([
        { roleId: 'R-0', code: 'platform-admin', name: '平台管理员', type: 'system', defaultMenuCode: 'admin.platform-overview', status: 'ACTIVE', description: '系统最高权限角色' },
        { roleId: 'R-1', code: 'business-user', name: '业务用户', type: 'business', defaultMenuCode: 'business.my-workspace', status: 'ACTIVE', description: '业务角色' },
      ]);
      if (url.endsWith('/iam/permissions')) return envelope([
        { permissionId: 'P-0', code: 'identity-access:platform-admin:use', name: '使用平台管理员控制台', resourceType: 'console', actionCode: 'platform-admin-use', description: '', status: 'ACTIVE' },
        { permissionId: 'P-1', code: 'knowledge-base:retrieval:query', name: '知识检索', resourceType: 'knowledge-base', actionCode: 'query', description: '', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/iam/menus')) return envelope([]);
      if (url.endsWith('/menus')) return envelope([]);
      return envelope([]);
    });
    renderPage('/tenant/roles');

    const permission = await screen.findByRole('checkbox', { name: '知识检索' });
    expect(screen.queryByRole('option', { name: '平台管理员' })).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: '使用平台管理员控制台' })).not.toBeInTheDocument();
    await actor.click(permission);
    await actor.click(screen.getByRole('button', { name: '保存权限' }));

    expect(requests.filter((request) => request.url.endsWith('/iam/roles/R-1/permissions') && request.body)).toHaveLength(0);
    const dialog = await screen.findByRole('dialog', { name: '确认替换角色权限' });
    expect(within(dialog).getByText('业务用户')).toBeInTheDocument();
    expect(within(dialog).getByText('知识检索')).toBeInTheDocument();
    expect(within(dialog).getByText(/完整替换会移除所有未选中的权限/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '确认替换权限' })).toBeDisabled();
    await actor.type(within(dialog).getByLabelText(/变更原因/), '为业务用户开放知识检索职责');
    await actor.click(within(dialog).getByRole('button', { name: '确认替换权限' }));

    await waitFor(() => expect(requests.some((request) => {
      const codes = request.body?.permissionCodes;
      return request.url.endsWith('/iam/roles/R-1/permissions')
        && Array.isArray(codes)
        && codes.includes('knowledge-base:retrieval:query')
        && request.body?.reason === '为业务用户开放知识检索职责';
    })).toBe(true));
  });

  it('keeps role permission failures inside the confirmation with a trace id', async () => {
    const actor = userEvent.setup();
    let writeBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'PUT') {
        writeBody = JSON.parse(String(init.body));
        return failure('权限集合已被其他管理员更新', 'AIP-IAM-PERMISSION-CONFLICT', 409);
      }
      if (url.endsWith('/iam/roles')) return envelope([
        { roleId: 'R-1', code: 'business-user', name: '业务用户', type: 'business', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/iam/permissions')) return envelope([
        { permissionId: 'P-1', code: 'knowledge-base:retrieval:query', name: '知识检索', resourceType: 'knowledge-base', status: 'ACTIVE' },
      ]);
      return envelope([]);
    });
    renderPage('/tenant/roles');

    await actor.click(await screen.findByRole('checkbox', { name: '知识检索' }));
    await actor.click(screen.getByRole('button', { name: '保存权限' }));
    const dialog = await screen.findByRole('dialog', { name: '确认替换角色权限' });
    fireEvent.change(within(dialog).getByLabelText(/变更原因/), { target: { value: '为业务用户开放知识检索职责' } });
    await actor.click(within(
      await screen.findByRole('dialog', { name: '确认替换角色权限' }),
    ).getByRole('button', { name: '确认替换权限' }));

    const failedPermissionDialog = await screen.findByRole('dialog', { name: '确认替换角色权限' });
    expect(await within(failedPermissionDialog).findByText('权限集合已被其他管理员更新')).toBeInTheDocument();
    expect(within(failedPermissionDialog).getByText('错误追踪：error-trace')).toBeInTheDocument();
    expect(within(failedPermissionDialog).getByRole('button', { name: '确认替换权限' })).toBeEnabled();
    expect(writeBody).toEqual({
      permissionCodes: ['knowledge-base:retrieval:query'],
      reason: '为业务用户开放知识检索职责',
    });
  });

  it('retries only the selected role permissions while preserving the loaded role and permission catalogs', async () => {
    const actor = userEvent.setup();
    let roleCatalogReads = 0;
    let permissionCatalogReads = 0;
    let selectedRoleReads = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/iam/roles/R-1/permissions')) {
        selectedRoleReads += 1;
        return selectedRoleReads === 1
          ? failure('业务用户权限读取暂时失败', 'AIP-IAM-ROLE-PERMISSION-UNAVAILABLE', 503)
          : envelope([
            { permissionId: 'P-1', code: 'knowledge-base:retrieval:query', name: '知识检索', resourceType: 'knowledge-base', status: 'ACTIVE' },
          ]);
      }
      if (url.endsWith('/iam/roles')) {
        roleCatalogReads += 1;
        return envelope([
          { roleId: 'R-1', code: 'business-user', name: '业务用户', type: 'business', status: 'ACTIVE', description: '业务角色' },
        ]);
      }
      if (url.endsWith('/iam/permissions')) {
        permissionCatalogReads += 1;
        return envelope([
          { permissionId: 'P-1', code: 'knowledge-base:retrieval:query', name: '知识检索', resourceType: 'knowledge-base', status: 'ACTIVE' },
        ]);
      }
      return envelope([]);
    });
    renderPage('/tenant/roles');

    const roleSelect = await screen.findByRole('combobox', { name: '当前角色' });
    const permission = await screen.findByRole('checkbox', { name: '知识检索' });
    expect(roleSelect).toHaveValue('R-1');
    expect(screen.getByRole('option', { name: '业务用户' })).toBeInTheDocument();
    expect(permission).toBeDisabled();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('业务用户权限读取暂时失败');
    expect(alert).toHaveTextContent('错误追踪：error-trace');
    await actor.click(within(alert).getByRole('button', { name: '重试读取业务用户权限' }));

    await waitFor(() => expect(permission).toBeChecked());
    expect(permission).toBeEnabled();
    expect(screen.getByRole('button', { name: '保存权限' })).toBeEnabled();
    expect(selectedRoleReads).toBe(2);
    expect(roleCatalogReads).toBe(1);
    expect(permissionCatalogReads).toBe(1);
  });

  it('locks permission saving while the newly selected role is still loading', async () => {
    const actor = userEvent.setup();
    const roleTwoPermissions = deferred<Response>();
    let writeCount = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'PUT') {
        writeCount += 1;
        return envelope({ resourceId: 'R-2', status: 'SAVED' });
      }
      if (url.endsWith('/iam/roles')) return envelope([
        { roleId: 'R-1', code: 'analyst', name: '分析员', type: 'business', status: 'ACTIVE' },
        { roleId: 'R-2', code: 'operator', name: '运营员', type: 'business', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/iam/permissions')) return envelope([
        { permissionId: 'P-1', code: 'knowledge-base:retrieval:query', name: '知识检索', resourceType: 'knowledge-base', status: 'ACTIVE' },
        { permissionId: 'P-2', code: 'app-studio:application:use', name: '应用使用', resourceType: 'application', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/iam/roles/R-1/permissions')) return envelope([
        { permissionId: 'P-1', code: 'knowledge-base:retrieval:query', name: '知识检索', resourceType: 'knowledge-base', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/iam/roles/R-2/permissions')) return roleTwoPermissions.promise;
      return envelope([]);
    });
    renderPage('/tenant/roles');

    const roleSelect = await screen.findByRole('combobox', { name: '当前角色' });
    await waitFor(() => expect(screen.getByRole('checkbox', { name: '知识检索' })).toBeChecked());
    await actor.selectOptions(roleSelect, 'R-2');

    expect(screen.getByRole('button', { name: '保存权限' })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: '知识检索' })).toBeDisabled();
    expect(writeCount).toBe(0);

    await act(async () => {
      roleTwoPermissions.resolve(await envelope([
        { permissionId: 'P-2', code: 'app-studio:application:use', name: '应用使用', resourceType: 'application', status: 'ACTIVE' },
      ]));
    });
    await waitFor(() => expect(screen.getByRole('checkbox', { name: '应用使用' })).toBeChecked());
    expect(screen.getByRole('checkbox', { name: '知识检索' })).not.toBeChecked();
    expect(screen.getByRole('button', { name: '保存权限' })).toBeEnabled();
  });

  it('ignores an older role permission response that arrives after the current role', async () => {
    const actor = userEvent.setup();
    const roleOnePermissions = deferred<Response>();
    const roleTwoPermissions = deferred<Response>();
    const writes: Array<{ url: string; body: Record<string, unknown> }> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'PUT') {
        writes.push({ url, body: JSON.parse(String(init.body)) });
        return envelope({ resourceId: 'R-2', status: 'SAVED' });
      }
      if (url.endsWith('/iam/roles')) return envelope([
        { roleId: 'R-1', code: 'analyst', name: '分析员', type: 'business', status: 'ACTIVE' },
        { roleId: 'R-2', code: 'operator', name: '运营员', type: 'business', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/iam/permissions')) return envelope([
        { permissionId: 'P-1', code: 'knowledge-base:retrieval:query', name: '知识检索', resourceType: 'knowledge-base', status: 'ACTIVE' },
        { permissionId: 'P-2', code: 'app-studio:application:use', name: '应用使用', resourceType: 'application', status: 'ACTIVE' },
      ]);
      if (url.endsWith('/iam/roles/R-1/permissions')) return roleOnePermissions.promise;
      if (url.endsWith('/iam/roles/R-2/permissions')) return roleTwoPermissions.promise;
      return envelope([]);
    });
    renderPage('/tenant/roles');

    const roleSelect = await screen.findByRole('combobox', { name: '当前角色' });
    await actor.selectOptions(roleSelect, 'R-2');
    await act(async () => {
      roleTwoPermissions.resolve(await envelope([
        { permissionId: 'P-2', code: 'app-studio:application:use', name: '应用使用', resourceType: 'application', status: 'ACTIVE' },
      ]));
    });
    await waitFor(() => expect(screen.getByRole('checkbox', { name: '应用使用' })).toBeChecked());

    await act(async () => {
      roleOnePermissions.resolve(await envelope([
        { permissionId: 'P-1', code: 'knowledge-base:retrieval:query', name: '知识检索', resourceType: 'knowledge-base', status: 'ACTIVE' },
      ]));
    });
    await Promise.resolve();

    expect(screen.getByRole('checkbox', { name: '应用使用' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: '知识检索' })).not.toBeChecked();
    await actor.click(screen.getByRole('button', { name: '保存权限' }));
    const dialog = await screen.findByRole('dialog', { name: '确认替换角色权限' });
    await actor.type(within(dialog).getByLabelText(/变更原因/), '按运营员职责刷新完整权限集合');
    await actor.click(within(dialog).getByRole('button', { name: '确认替换权限' }));
    await waitFor(() => expect(writes).toEqual([{
      url: '/api/v1/iam/roles/R-2/permissions',
      body: { permissionCodes: ['app-studio:application:use'], reason: '按运营员职责刷新完整权限集合' },
    }]));
  });

  it('renders localized audit columns with pagination and a selected detail view', async () => {
    const actor = userEvent.setup();
    const auditRows = Array.from({ length: 21 }, (_, index) => ({
      id: `A-${index + 1}`, actorId: `U-${index + 1}`, eventType: 'ROLE_PERMISSION_REPLACED', riskLevel: index === 0 ? 'HIGH' : 'LOW',
      subjectType: 'IAM_ROLE', subjectId: `R-${index + 1}`, resultStatus: 'SUCCESS', detailJson: JSON.stringify({ reason: '权限治理' }),
      traceId: `TRACE-${index + 1}`, createdAt: `2026-07-10T00:${String(index).padStart(2, '0')}:00Z`,
    }));
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const requestedPage = String(input).includes('page=2') ? 2 : 1;
      return envelope({
        items: requestedPage === 1 ? auditRows.slice(0, 20) : auditRows.slice(20),
        page: requestedPage,
        pageSize: 20,
        total: auditRows.length,
        totalPages: 2,
      });
    });
    renderPage('/admin/audit');

    expect(await screen.findByRole('columnheader', { name: '事件类型' })).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'eventType' })).not.toBeInTheDocument();
    expect(screen.getByText(/第 1 \/ 2 页/)).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '查看审计 A-1' }));
    expect(screen.getByText('权限治理')).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '下一页' }));
    expect(await screen.findByText(/第 2 \/ 2 页/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '查看审计 A-21' })).toBeInTheDocument();
  });

  it('exposes knowledge-base creation and document maintenance to tenant administrators', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => envelope([{ knowledgeBaseId: 'KB-1', name: '产品知识库', visibility: 'PRIVATE', status: 'ACTIVE', documentCount: 3, updatedAt: '2026-07-10T00:00:00Z' }]));
    renderPage('/tenant/knowledge-bases', knowledgeAdministrator);

    expect(await screen.findByRole('button', { name: '新建知识库' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: '管理文档 KB-1' })).toBeInTheDocument();
  });

  it('clears documents and disables stale actions when switching knowledge bases fails', async () => {
    const actor = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/model-providers/embedding-options')) return envelope([]);
      if (url.endsWith('/knowledge-bases/KB-1/documents')) return envelope([
        {
          documentId: 'D-OLD', fileName: '旧知识库合同.pdf', status: 'CHUNKED',
          jobId: null, jobType: null, jobStatus: null, failureCode: null,
          failureSummary: null, traceId: null, manualRetryEligible: false,
          cancellationReason: null, cancelledBy: null, cancellationTraceId: null,
          cancelledAt: null, cancelEligible: false,
        },
      ]);
      if (url.endsWith('/knowledge-bases/KB-2/documents')) return failure('新知识库文档加载失败', 'AIP-KB-LOAD', 503);
      return envelope([
        { knowledgeBaseId: 'KB-1', name: '旧知识库', visibility: 'PRIVATE', status: 'ACTIVE' },
        { knowledgeBaseId: 'KB-2', name: '新知识库', visibility: 'PRIVATE', status: 'ACTIVE' },
      ]);
    });
    renderPage('/tenant/knowledge-bases', knowledgeAdministrator);

    await actor.click(await screen.findByRole('button', { name: '管理文档 KB-1' }));
    expect(await screen.findByText('旧知识库合同.pdf')).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '关闭' }));
    await actor.click(screen.getByRole('button', { name: '管理文档 KB-2' }));

    expect(await screen.findByText('新知识库文档加载失败')).toBeInTheDocument();
    expect(screen.queryByText('旧知识库合同.pdf')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '建立索引' })).not.toBeInTheDocument();
  });

  it('keeps the knowledge catalog closed on load failure and retries to an explicit empty state', async () => {
    const actor = userEvent.setup();
    let failed = false;
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => {
      if (!failed) {
        failed = true;
        return Promise.reject(new TypeError('知识库目录暂时不可用'));
      }
      return envelope([]);
    });
    renderPage('/tenant/knowledge-bases', knowledgeAdministrator);

    expect(await screen.findByText('知识库目录暂时不可用')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: '知识库列表' })).not.toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '重试' }));

    expect(await screen.findByText('暂无知识库')).toBeInTheDocument();
  });

  it('keeps knowledge-base creation failures inside the creation dialog', async () => {
    const actor = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') return failure('知识库名称已被占用', 'AIP-KB-NAME-CONFLICT', 409);
      if (url.endsWith('/model-providers/embedding-options')) return envelope([
        { modelConfigId: 'EMB-1', label: '企业向量模型' },
      ]);
      return envelope([]);
    });
    renderPage('/tenant/knowledge-bases', knowledgeAdministrator);

    await actor.click(await screen.findByRole('button', { name: '新建知识库' }));
    const dialog = await screen.findByRole('dialog', { name: '新建知识库' });
    await actor.type(within(dialog).getByLabelText(/^知识库名称/), '合同制度库');
    await actor.selectOptions(within(dialog).getByLabelText('向量模型'), 'EMB-1');
    await actor.click(within(dialog).getByRole('button', { name: '创建' }));

    expect(await within(dialog).findByText('知识库名称已被占用')).toBeInTheDocument();
    expect(within(dialog).getByText('错误追踪：error-trace')).toBeInTheDocument();
  });

  it('adds a tenant user through the invitation activation contract', async () => {
    const actor = userEvent.setup();
    let invitationBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        invitationBody = JSON.parse(String(init.body));
        return envelope({ invitationId: 'I-1', userId: 'U-9', status: 'PENDING_ACTIVATION', deliveryStatus: 'QUEUED', expiresAt: '2026-07-12T00:00:00Z' });
      }
      if (url.endsWith('/iam/roles')) return envelope([{ roleId: 'R-1', code: 'business-user', name: '业务用户', type: 'business', status: 'ACTIVE' }]);
      return envelope([]);
    });
    renderPage('/tenant/users');

    await actor.click(await screen.findByRole('button', { name: '邀请用户' }));
    fireEvent.change(screen.getByRole('textbox', { name: /姓名/ }), { target: { value: '新用户' } });
    fireEvent.change(screen.getByRole('textbox', { name: /登录账号/ }), { target: { value: 'new-user' } });
    fireEvent.change(screen.getByRole('textbox', { name: /邮箱/ }), { target: { value: 'new-user@example.com' } });
    await actor.click(await screen.findByRole('button', { name: '发送邀请' }));

    await waitFor(() => expect(invitationBody).toEqual({ username: 'new-user', displayName: '新用户', email: 'new-user@example.com', roleId: 'R-1' }));
    expect(await screen.findByText(/通知状态 QUEUED/)).toBeInTheDocument();
  });

  it('keeps invitation failures in the dialog where the operator can correct the input', async () => {
    const actor = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') return failure('该邮箱已有待激活邀请', 'AIP-IAM-INVITATION-CONFLICT', 409);
      if (url.endsWith('/iam/roles')) return envelope([
        { roleId: 'R-1', code: 'business-user', name: '业务用户', type: 'business', status: 'ACTIVE' },
      ]);
      return envelope([]);
    });
    renderPage('/tenant/users');

    await actor.click(await screen.findByRole('button', { name: '邀请用户' }));
    const dialog = await screen.findByRole('dialog', { name: '邀请用户' });
    fireEvent.change(within(dialog).getByLabelText(/^姓名/), { target: { value: '新用户' } });
    fireEvent.change(within(dialog).getByLabelText(/^登录账号/), { target: { value: 'new-user' } });
    fireEvent.change(within(dialog).getByLabelText(/^邮箱/), { target: { value: 'new-user@example.com' } });
    await actor.click(within(
      await screen.findByRole('dialog', { name: '邀请用户' }),
    ).getByRole('button', { name: '发送邀请' }));

    const failedDialog = await screen.findByRole('dialog', { name: '邀请用户' });
    expect(await within(failedDialog).findByText('该邮箱已有待激活邀请')).toBeInTheDocument();
    expect(within(failedDialog).getByText('错误追踪：error-trace')).toBeInTheDocument();
    expect(within(failedDialog).getByLabelText(/^邮箱/)).toHaveValue('new-user@example.com');
  });

  it('requires an audited reason before revoking a pending invitation', async () => {
    const actor = userEvent.setup();
    let revokeBody: Record<string, unknown> | undefined;
    const invitation = {
      invitationId: 'I-REVOKE', userId: 'U-9', email: 'departed@example.com', status: 'PENDING', statusLabel: '待激活',
      deliveryStatus: 'DELIVERED', deliveryStatusLabel: '已送达', expiresAt: '2026-07-19T00:00:00Z',
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        revokeBody = JSON.parse(String(init.body));
        return envelope(null);
      }
      if (url.endsWith('/iam/roles')) return envelope([{ roleId: 'R-1', code: 'business-user', name: '业务用户', type: 'business', status: 'ACTIVE' }]);
      if (url.endsWith('/iam/user-invitations')) return envelope(revokeBody ? [] : [invitation]);
      return envelope([]);
    });
    renderPage('/tenant/users');

    await actor.click(await screen.findByRole('button', { name: '撤销 departed@example.com' }));
    const dialog = await screen.findByRole('dialog', { name: '撤销用户邀请' });
    expect(within(dialog).getByText('departed@example.com')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '确认撤销' })).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText(/撤销原因/), { target: { value: '候选人已取消入职，阻止旧链接继续激活' } });
    await actor.click(within(
      await screen.findByRole('dialog', { name: '撤销用户邀请' }),
    ).getByRole('button', { name: '确认撤销' }));

    await waitFor(() => expect(revokeBody).toEqual({ reason: '候选人已取消入职，阻止旧链接继续激活' }));
    expect(screen.queryByRole('dialog', { name: '撤销用户邀请' })).not.toBeInTheDocument();
  });

  it('keeps a failed invitation revocation inside the confirmation dialog with its trace and reason', async () => {
    const actor = userEvent.setup();
    const invitation = {
      invitationId: 'I-FAIL', userId: 'U-9', email: 'security@example.com', status: 'PENDING', statusLabel: '待激活',
      deliveryStatus: 'DELIVERED', deliveryStatusLabel: '已送达', expiresAt: '2026-07-19T00:00:00Z',
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') return failure('邀请状态已变化，请复核', 'AIP-IAM-INVITATION-STATE-CONFLICT', 409);
      if (url.endsWith('/iam/roles')) return envelope([{ roleId: 'R-1', code: 'business-user', name: '业务用户', type: 'business', status: 'ACTIVE' }]);
      if (url.endsWith('/iam/user-invitations')) return envelope([invitation]);
      return envelope([]);
    });
    renderPage('/tenant/users');

    await actor.click(await screen.findByRole('button', { name: '撤销 security@example.com' }));
    const dialog = await screen.findByRole('dialog', { name: '撤销用户邀请' });
    fireEvent.change(within(dialog).getByLabelText(/撤销原因/), { target: { value: '安全团队要求终止未使用的邀请' } });
    await actor.click(within(
      await screen.findByRole('dialog', { name: '撤销用户邀请' }),
    ).getByRole('button', { name: '确认撤销' }));

    const failedDialog = await screen.findByRole('dialog', { name: '撤销用户邀请' });
    expect(within(failedDialog).getByRole('alert')).toHaveTextContent('邀请状态已变化，请复核');
    expect(within(failedDialog).getByText('错误追踪：error-trace')).toBeInTheDocument();
    expect(within(failedDialog).getByLabelText(/撤销原因/)).toHaveValue('安全团队要求终止未使用的邀请');
  });

  it('requests a changed tenant-user page size from the server', async () => {
    const actor = userEvent.setup();
    const userRequests: URL[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), 'http://console.local');
      if (url.pathname.endsWith('/iam/roles')) return envelope([
        { roleId: 'R-1', code: 'business-user', name: '业务用户', type: 'business', status: 'ACTIVE' },
      ]);
      if (url.pathname.endsWith('/iam/user-invitations')) return envelope([]);
      if (url.pathname.endsWith('/iam/users')) {
        userRequests.push(url);
        const pageSize = Number(url.searchParams.get('pageSize'));
        const displayIndex = pageSize === 20 ? 12 : 10;
        return envelope({
          items: [{
            userId: `U-${displayIndex}`, displayName: `用户 ${displayIndex}`, username: `user-${displayIndex}`,
            email: `user-${displayIndex}@example.com`, roleCodes: ['business-user'], status: 'ACTIVE',
          }],
          page: 1,
          pageSize,
          total: 12,
          totalPages: Math.ceil(12 / pageSize),
        });
      }
      return envelope([]);
    });
    renderPage('/tenant/users');

    expect(await screen.findByText('用户 10')).toBeInTheDocument();
    expect(userRequests[0].searchParams.get('page')).toBe('1');
    expect(userRequests[0].searchParams.get('pageSize')).toBe('10');
    expect(screen.getByText(/共 12 条/)).toBeInTheDocument();
    await actor.selectOptions(screen.getByLabelText('每页条数'), '20');

    await waitFor(() => expect(userRequests).toHaveLength(2));
    expect(userRequests[1].searchParams.get('page')).toBe('1');
    expect(userRequests[1].searchParams.get('pageSize')).toBe('20');
    expect(await screen.findByText('用户 12')).toBeInTheDocument();
    expect(screen.getByText(/第 1 \/ 1 页/)).toBeInTheDocument();
  });

  it('keeps tenant users fail-closed on load failure and recovers to an explicit empty state', async () => {
    const actor = userEvent.setup();
    let failed = false;
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => {
      if (!failed) {
        failed = true;
        return Promise.reject(new TypeError('用户目录暂时不可用'));
      }
      return envelope([]);
    });
    renderPage('/tenant/users');

    expect(await screen.findByText('用户目录暂时不可用')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: '租户用户列表' })).not.toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '重试' }));

    expect(await screen.findByText('暂无用户或待处理邀请')).toBeInTheDocument();
  });

  it('requires a decision comment before accepting a permission request', async () => {
    const actor = userEvent.setup();
    let decisionBody: Record<string, unknown> | undefined;
    let approved = false;
    const pending = { requestId: 'PR-1', requesterId: '1003', resourceType: 'AI_APPLICATION', resourceId: '3001', permissionCode: 'app-studio:application:use', reason: '需要完成合同审核', status: 'PENDING', createdAt: '2026-07-10T00:00:00Z', updatedAt: '2026-07-10T00:00:00Z' };
    const decided = { ...pending, status: 'APPROVED', decisionComment: '请在应用授权页完成授权' };
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
      if (!init?.method || init.method === 'GET') return envelope({ items: [approved ? decided : pending], page: 1, pageSize: 10, total: 1, totalPages: 1 });
      decisionBody = JSON.parse(String(init.body));
      approved = true;
      return envelope(decided);
    });
    renderPage('/tenant/permission-requests');
    await actor.click(await screen.findByRole('button', { name: '受理' }));
    expect(screen.getByText(/活动发布版本受众不变.*重新发布后才对新会话生效/)).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: '批准并授权' })).toBeDisabled();
    await actor.type(screen.getByLabelText(/处理意见/), '请在应用授权页完成授权');
    await actor.click(screen.getByRole('button', { name: '批准并授权' }));
    await waitFor(() => expect(screen.getAllByText('APPROVED')).toHaveLength(2));
    expect(decisionBody).toEqual({ action: 'APPROVE', comment: '请在应用授权页完成授权' });
  });

  it('keeps a failed permission decision local to the detail and retries the exact mutation', async () => {
    const actor = userEvent.setup();
    let decisionAttempts = 0;
    const decisionBodies: Array<Record<string, unknown>> = [];
    const pending = { requestId: 'PR-RETRY', requesterId: '1003', resourceType: 'AI_APPLICATION', resourceId: '3001', permissionCode: 'app-studio:application:use', reason: '需要完成合同审核', status: 'PENDING', createdAt: '2026-07-10T00:00:00Z', updatedAt: '2026-07-10T00:00:00Z' };
    const decided = { ...pending, status: 'APPROVED', decisionComment: '完成最小授权复核' };
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
      if (!init?.method || init.method === 'GET') {
        return envelope({ items: [decisionAttempts > 1 ? decided : pending], page: 1, pageSize: 10, total: 1, totalPages: 1 });
      }
      decisionAttempts += 1;
      decisionBodies.push(JSON.parse(String(init.body)));
      return decisionAttempts === 1
        ? failure('申请已被其他管理员更新，请复核后重试', 'AIP-IAM-PERMISSION-REQUEST-CONFLICT', 409)
        : envelope(decided);
    });
    renderPage('/tenant/permission-requests');

    await actor.click(await screen.findByRole('button', { name: '受理' }));
    await actor.type(screen.getByLabelText(/处理意见/), '完成最小授权复核');
    await actor.click(screen.getByRole('button', { name: '批准并授权' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('申请已被其他管理员更新，请复核后重试');
    expect(alert).toHaveTextContent('错误追踪：error-trace');
    expect(screen.getByRole('table', { name: '权限申请列表' })).toBeInTheDocument();
    expect(screen.getByLabelText(/处理意见/)).toHaveValue('完成最小授权复核');
    await actor.click(within(alert).getByRole('button', { name: '重试批准并授权' }));

    await waitFor(() => expect(screen.getAllByText('APPROVED')).toHaveLength(2));
    expect(decisionAttempts).toBe(2);
    expect(decisionBodies).toEqual([
      { action: 'APPROVE', comment: '完成最小授权复核' },
      { action: 'APPROVE', comment: '完成最小授权复核' },
    ]);
  });

  it('drives permission request pagination and status filtering from the server contract', async () => {
    const actor = userEvent.setup();
    const requestedUrls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      requestedUrls.push(url);
      const secondPage = url.includes('page=2');
      return envelope({
        items: [{ requestId: secondPage ? 'PR-11' : 'PR-1', requesterId: '1003', resourceType: 'AI_APPLICATION', resourceId: secondPage ? '3011' : '3001', permissionCode: 'app-studio:application:use', reason: '需要完成合同审核', status: 'PENDING', createdAt: '2026-07-10T00:00:00Z', updatedAt: '2026-07-10T00:00:00Z' }],
        page: secondPage ? 2 : 1,
        pageSize: 10,
        total: 21,
        totalPages: 3,
      });
    });
    renderPage('/tenant/permission-requests');

    expect(await screen.findByRole('cell', { name: /3001/ })).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '下一页' }));
    expect(await screen.findByRole('cell', { name: /3011/ })).toBeInTheDocument();
    expect(requestedUrls.some((url) => url.includes('page=2') && url.includes('pageSize=10'))).toBe(true);

    await actor.selectOptions(screen.getByLabelText('按处理状态筛选'), 'PENDING');
    await waitFor(() => expect(requestedUrls.some((url) => url.includes('status=PENDING') && url.includes('page=1'))).toBe(true));
  });

  it('shows the requester and keeps self decisions fail-closed', async () => {
    const actor = userEvent.setup();
    let postCount = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
      if (init?.method === 'POST') postCount += 1;
      return envelope([{ requestId: 'PR-SELF', requesterId: '1003', resourceType: 'AI_APPLICATION', resourceId: '3001', permissionCode: 'app-studio:application:use', reason: '需要完成合同审核', status: 'PENDING', createdAt: '2026-07-10T00:00:00Z', updatedAt: '2026-07-10T00:00:00Z' }]);
    });
    renderPage('/tenant/permission-requests', { ...user, userId: '1003' });

    await actor.click(await screen.findByRole('button', { name: '查看' }));

    expect(screen.getAllByText('1003（当前账号）')).toHaveLength(2);
    expect(screen.getByText('这是当前账号提交的申请。职责分离要求由另一名具备管理权限的管理员处理。')).toBeInTheDocument();
    expect(screen.getByLabelText(/处理意见/)).toBeDisabled();
    expect(screen.getByRole('button', { name: '拒绝申请' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '批准并授权' })).toBeDisabled();
    expect(postCount).toBe(0);
  });

  it('keeps permission requests fail-closed on load failure and offers an explicit retry', async () => {
    const actor = userEvent.setup();
    let calls = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => {
      calls += 1;
      return calls === 1
        ? failure('权限申请目录暂时不可用', 'AIP-IAM-PERMISSION-REQUEST-UNAVAILABLE', 503)
        : envelope([]);
    });
    renderPage('/tenant/permission-requests');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('权限申请目录暂时不可用');
    expect(screen.queryByRole('table', { name: '权限申请列表' })).not.toBeInTheDocument();
    await actor.click(within(alert).getByRole('button', { name: '重试' }));

    expect(await screen.findByText('暂无权限申请')).toBeInTheDocument();
    expect(calls).toBe(2);
  });
});
