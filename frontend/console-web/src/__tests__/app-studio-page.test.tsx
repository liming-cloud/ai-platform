import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FluentProvider } from '@fluentui/react-components';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AppStudioPage } from '../app-studio-page';
import { enterpriseLightTheme } from '../theme';
import type { CurrentUser } from '../types';
import { removeIsolatedPortalHosts, renderWithIsolatedPortal } from './test-render';

const user: CurrentUser = {
  tenantId: '2001',
  userId: '1001',
  loginName: 'studio-admin',
  displayName: '应用管理员',
  roleCodes: ['tenant-admin'],
  permissionCodes: [
    'app-studio:application:read',
    'app-studio:application:create',
    'app-studio:application:update',
    'app-studio:application:manage-acl',
    'app-studio:application:publish',
  ],
  menus: [],
  defaultRoute: '/tenant/applications',
  accessTokenExpiresAt: '2099-01-01T00:00:00Z',
};

const ownerCapabilities = {
  canEditConfiguration: true,
  canRunPublishPreflight: true,
  canPublish: true,
  canRollback: true,
  canManageAccessPolicy: true,
  canReadReleaseEvidence: true,
};

const draftApplication = {
  applicationId: 'APP-1',
  applicationCode: 'CONTRACT_REVIEW',
  name: '合同审阅助手',
  description: '帮助法务团队核对合同条款。',
  applicationType: 'KNOWLEDGE_QA',
  modelConfigId: 'MODEL-CHAT-1',
  knowledgeBaseIds: ['KB-CONTRACTS'],
  systemPrompt: '只根据已授权合同知识回答。',
  publicationStatus: 'DRAFT',
  createdAt: '2026-07-18T01:00:00Z',
  updatedAt: '2026-07-18T01:00:00Z',
  accessMode: 'OWNER',
  capabilities: ownerCapabilities,
};

const publishedApplication = {
  ...draftApplication,
  publicationStatus: 'PUBLISHED',
  activeReleaseId: 'RELEASE-2',
  activeReleaseVersion: 2,
  activeReleaseOperation: 'PUBLISH',
  sourceReleaseId: null,
  sourceReleaseVersion: null,
};

const governanceOnlyApplication = {
  applicationId: 'APP-2',
  applicationCode: 'APP-APP-2',
  name: '已发布制度助手',
  description: null,
  applicationType: 'KNOWLEDGE_QA',
  modelConfigId: null,
  knowledgeBaseIds: [],
  systemPrompt: null,
  publicationStatus: 'PUBLISHED',
  activeReleaseId: 'RELEASE-GOV-1',
  activeReleaseVersion: 1,
  activeReleaseOperation: 'PUBLISH',
  sourceReleaseId: null,
  sourceReleaseVersion: null,
  updatedAt: null,
  accessMode: 'GOVERNANCE_READ_ONLY',
  capabilities: {
    canEditConfiguration: false,
    canRunPublishPreflight: false,
    canPublish: false,
    canRollback: false,
    canManageAccessPolicy: false,
    canReadReleaseEvidence: true,
  },
};

const releaseV1 = {
  releaseId: 'RELEASE-1',
  version: 1,
  active: false,
  operation: 'PUBLISH',
  sourceReleaseId: null,
  name: '合同审阅助手',
  applicationType: 'KNOWLEDGE_QA',
  modelConfigId: 'MODEL-CHAT-0',
  knowledgeBaseIds: ['KB-CONTRACTS'],
  systemPromptSummary: '只根据已授权合同知识回答。',
  systemPromptSha256: 'hash-v1',
  ownerUserCount: 1,
  allowedUserCount: 8,
  publishedBy: 'USER-ADMIN',
  reason: '首版上线',
  traceId: 'TRACE-RELEASE-1',
  createdAt: '2026-07-18T03:20:00Z',
};

const releaseV2 = {
  ...releaseV1,
  releaseId: 'RELEASE-2',
  version: 2,
  active: true,
  modelConfigId: 'MODEL-CHAT-1',
  knowledgeBaseIds: ['KB-CONTRACTS', 'KB-POLICY'],
  systemPromptSha256: 'hash-v2',
  allowedUserCount: 12,
  reason: '扩大制度知识范围',
  traceId: 'TRACE-RELEASE-2',
  createdAt: '2026-07-18T04:20:00Z',
};

function envelope(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    header: { code: 'SUCCESS', message: 'success', traceId: 'trace-success' },
    body,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

function failure(message: string, code = 'AIP-APP-STUDIO-409', status = 409) {
  return Promise.resolve(new Response(JSON.stringify({
    header: { code, message, traceId: 'trace-failure' },
    body: null,
  }), { status, headers: { 'Content-Type': 'application/json' } }));
}

function renderPage(currentUser = user) {
  return renderWithIsolatedPortal(<FluentProvider theme={enterpriseLightTheme}><AppStudioPage user={currentUser} /></FluentProvider>);
}

afterEach(() => {
  cleanup();
  removeIsolatedPortalHosts();
  localStorage.clear();
  vi.restoreAllMocks();
});

describe('App Studio operator journey', () => {
  it('keeps application and recovery data closed when their permissions are absent', async () => {
    const requestedUrls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      requestedUrls.push(String(input));
      throw new Error(`unexpected request: ${String(input)}`);
    });

    renderPage({ ...user, permissionCodes: [] });

    expect(await screen.findByText('当前账号没有应用读取权限，应用目录和配置保持隐藏。')).toBeInTheDocument();
    expect(screen.getByText('缺少 app-studio:application:publish')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '刷新死信' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: '创建应用' })).not.toBeInTheDocument();
    expect(requestedUrls).toEqual([]);
  });

  it('creates a usable draft from a validated complete configuration snapshot', async () => {
    const actor = userEvent.setup();
    let createBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (!init?.method || init.method === 'GET') {
        if (url.startsWith('/api/v1/chat-runs/dead-letters')) return envelope([]);
        if (url === '/api/v1/model-providers') return envelope([{ providerId: 'PROVIDER-1', providerName: '企业模型', status: 'ACTIVE' }]);
        if (url.endsWith('/models')) return envelope([{ modelConfigId: 'MODEL-CHAT-2', modelName: '法务对话模型', modelType: 'CHAT', status: 'ACTIVE' }]);
        if (url === '/api/v1/knowledge-bases') return envelope([
          { knowledgeBaseId: 'KB-LAW', name: '法规知识', status: 'ACTIVE' },
          { knowledgeBaseId: 'KB-POLICY', name: '内部制度', status: 'ACTIVE' },
        ]);
        return envelope([]);
      }
      createBody = JSON.parse(String(init.body));
      return envelope({
        applicationId: 'APP-NEW', applicationCode: 'LEGAL_ASSISTANT', name: '法务问答助手',
        modelConfigId: 'MODEL-CHAT-2', knowledgeBaseIds: ['KB-LAW', 'KB-POLICY'],
        publicationStatus: 'DRAFT', createdAt: '2026-07-18T02:00:00Z',
        accessMode: 'OWNER', capabilities: ownerCapabilities,
      });
    });
    renderPage();

    expect(await screen.findByText('当前租户还没有 AI 应用')).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '创建应用' }));
    const dialog = await screen.findByRole('dialog', { name: '创建 AI 应用' });
    const submit = within(dialog).getByRole('button', { name: '保存草稿' });
    expect(submit).toBeDisabled();

    fireEvent.change(within(dialog).getByLabelText(/^应用名称/), { target: { value: '法务问答助手' } });
    await actor.selectOptions(within(dialog).getByLabelText(/^应用类型/), 'KNOWLEDGE_QA');
    fireEvent.change(within(dialog).getByLabelText(/^应用说明/), { target: { value: '为法务人员提供受控知识问答。' } });
    fireEvent.change(within(dialog).getByLabelText(/^模型配置 ID/), { target: { value: 'MODEL-CHAT-2' } });
    const knowledgeChoices = await within(dialog).findByRole('group', { name: '可用知识库快捷选择' });
    await actor.click(within(knowledgeChoices).getByRole('checkbox', { name: /法规知识/ }));
    const refreshedCreateDialog = await screen.findByRole('dialog', { name: '创建 AI 应用' });
    const refreshedKnowledgeChoices = await within(refreshedCreateDialog).findByRole('group', { name: '可用知识库快捷选择' });
    await actor.click(within(refreshedKnowledgeChoices).getByRole('checkbox', { name: /内部制度/ }));
    fireEvent.change(within(dialog).getByLabelText(/^系统提示词/), { target: { value: '只引用当前用户有权查看的法务知识。' } });
    await actor.click(submit);

    await waitFor(() => expect(createBody).toEqual({
      applicationType: 'KNOWLEDGE_QA',
      name: '法务问答助手',
      description: '为法务人员提供受控知识问答。',
      modelConfigId: 'MODEL-CHAT-2',
      knowledgeBaseIds: ['KB-LAW', 'KB-POLICY'],
      systemPrompt: '只引用当前用户有权查看的法务知识。',
    }));
    expect(await screen.findByRole('heading', { name: '法务问答助手' })).toBeInTheDocument();
    expect(screen.getByText('草稿')).toBeInTheDocument();
  });

  it('edits the complete draft, keeps unsafe ACL replacement read-only, preflights, and publishes an immutable active release', async () => {
    const actor = userEvent.setup();
    const writes: Array<{ url: string; method: string; body: Record<string, unknown> }> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      const method = init?.method || 'GET';
      if (method === 'GET') {
        if (url.startsWith('/api/v1/chat-runs/dead-letters')) return envelope([]);
        if (url === '/api/v1/ai-applications/APP-1/releases') return envelope([{ ...releaseV1, active: true }]);
        if (url === '/api/v1/model-providers') return envelope([{ providerId: 'PROVIDER-1', providerName: '企业模型', status: 'ACTIVE' }]);
        if (url.endsWith('/models')) return envelope([{ modelConfigId: 'MODEL-CHAT-1', modelName: '合同模型', modelType: 'CHAT', status: 'ACTIVE' }]);
        if (url === '/api/v1/knowledge-bases') return envelope([{ knowledgeBaseId: 'KB-CONTRACTS', name: '合同知识', status: 'ACTIVE' }]);
        return envelope([draftApplication]);
      }
      const body = JSON.parse(String(init?.body || '{}')) as Record<string, unknown>;
      writes.push({ url, method, body });
      if (url.endsWith('/access-policy')) throw new Error('unsafe blind ACL replacement must not be reachable');
      if (url.endsWith('/publish/preflight')) return envelope({
        applicationId: 'APP-1', publishRequestId: String(body.publishRequestId), passed: true,
        blockers: [], warnings: [], traceId: 'TRACE-PREFLIGHT-1', checkedAt: '2026-07-18T03:10:00Z',
      });
      if (url.endsWith('/publish')) return envelope({
        applicationId: 'APP-1', status: 'PUBLISHED',
        activeReleaseId: 'RELEASE-1', activeReleaseVersion: 1,
        activeReleaseOperation: 'PUBLISH', sourceReleaseId: null, sourceReleaseVersion: null,
        updatedAt: '2026-07-18T03:20:00Z',
      });
      return envelope({ ...draftApplication, name: '合同审阅工作台', updatedAt: '2026-07-18T02:30:00Z' });
    });
    renderPage();

    await screen.findByRole('heading', { name: '合同审阅助手' });
    await actor.click(screen.getByRole('button', { name: '编辑配置' }));
    const editDialog = await screen.findByRole('dialog', { name: '编辑应用配置' });
    await within(editDialog).findByRole('group', { name: '可用知识库快捷选择' });
    const nameInput = within(editDialog).getByLabelText(/^应用名称/);
    fireEvent.change(nameInput, { target: { value: '合同审阅工作台' } });
    await actor.click(within(editDialog).getByRole('button', { name: '保存配置' }));

    expect(writes[0]).toEqual({
      url: '/api/v1/ai-applications/APP-1',
      method: 'PUT',
      body: {
        name: '合同审阅工作台',
        description: '帮助法务团队核对合同条款。',
        modelConfigId: 'MODEL-CHAT-1',
        knowledgeBaseIds: ['KB-CONTRACTS'],
        systemPrompt: '只根据已授权合同知识回答。',
      },
    });

    const accessControl = screen.getByRole('button', { name: '访问策略只读' });
    expect(accessControl).toBeDisabled();
    expect(screen.getByRole('heading', { name: '访问策略' })).toBeInTheDocument();
    expect(screen.getByText(/未提供当前授权明细、策略版本和条件更新令牌/)).toBeInTheDocument();
    expect(screen.getByText(/现有访问策略继续生效/)).toBeInTheDocument();
    expect(writes.some((write) => write.url.endsWith('/access-policy'))).toBe(false);

    await actor.click(screen.getByRole('button', { name: '发布预检' }));
    expect(await screen.findByText('发布预检通过')).toBeInTheDocument();
    expect(screen.getByText(/TRACE-PREFLIGHT-1/)).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '确认发布' }));
    const publishDialog = await screen.findByRole('dialog', { name: '确认发布应用' });
    fireEvent.change(within(publishDialog).getByLabelText(/^发布理由/), { target: { value: '法务负责人已核对配置、访问范围与预检证据' } });
    const refreshedPublishDialog = await screen.findByRole('dialog', { name: '确认发布应用' });
    await actor.click(within(refreshedPublishDialog).getByRole('button', { name: '确认并发布' }));

    expect(await screen.findByText(/应用已发布为活动版本 v1/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '编辑配置' })).toBeEnabled();
    expect(screen.getByText(/修改草稿不会影响活动版本 v1/)).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '发布版本与回滚' })).toBeInTheDocument();
    const releaseTable = screen.getByRole('table', { name: '应用发布版本' });
    expect(releaseTable.parentElement).toHaveAttribute('tabindex', '0');
    expect(releaseTable.parentElement).toHaveAttribute('aria-label', '应用发布版本横向滚动区域');
    expect(screen.getByText('TRACE-RELEASE-1')).toBeInTheDocument();
    expect(writes.at(-1)).toMatchObject({
      url: '/api/v1/ai-applications/APP-1/publish',
      method: 'POST',
      body: { reason: '法务负责人已核对配置、访问范围与预检证据' },
    });
    expect(writes.some((write) => write.url.endsWith('/access-policy'))).toBe(false);
  });

  it('previews rollback impact, requires explicit reason, reuses the idempotency key after failure, and activates a new version', async () => {
    const actor = userEvent.setup();
    const rollbackWrites: Array<{ url: string; body: Record<string, unknown> }> = [];
    let releaseReads = 0;
    let rollbackAttempts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      const method = init?.method || 'GET';
      if (method === 'GET') {
        if (url.startsWith('/api/v1/chat-runs/dead-letters')) return envelope([]);
        if (url === '/api/v1/ai-applications/APP-1/releases') {
          releaseReads += 1;
          if (releaseReads === 1) return envelope([releaseV2, releaseV1]);
          return envelope([
            {
              ...releaseV1,
              releaseId: 'RELEASE-3',
              version: 3,
              active: true,
              operation: 'ROLLBACK',
              sourceReleaseId: 'RELEASE-1',
              reason: '线上回答偏离合同范围，恢复已验证版本',
              traceId: 'TRACE-ROLLBACK-3',
            },
            { ...releaseV2, active: false },
            releaseV1,
          ]);
        }
        return envelope([publishedApplication]);
      }
      const body = JSON.parse(String(init?.body || '{}')) as Record<string, unknown>;
      rollbackWrites.push({ url, body });
      rollbackAttempts += 1;
      if (rollbackAttempts === 1) return failure('版本激活暂时冲突');
      return envelope({
        ...publishedApplication,
        modelConfigId: 'MODEL-CHAT-0',
        knowledgeBaseIds: ['KB-CONTRACTS'],
        activeReleaseId: 'RELEASE-3',
        activeReleaseVersion: 3,
        activeReleaseOperation: 'ROLLBACK',
        sourceReleaseId: 'RELEASE-1',
        sourceReleaseVersion: 1,
      });
    });
    renderPage();

    expect(await screen.findByText('TRACE-RELEASE-2')).toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '回滚到 v1' }));
    const dialog = await screen.findByRole('dialog', { name: '确认回滚到 v1' });
    expect(within(dialog).getByText(/活动版本 v2/)).toBeInTheDocument();
    expect(within(dialog).getByText(/模型配置：MODEL-CHAT-1 → MODEL-CHAT-0/)).toBeInTheDocument();
    expect(within(dialog).getByText(/知识库将移除：KB-POLICY/)).toBeInTheDocument();
    expect(within(dialog).getByText(/授权受众：12 → 8/)).toBeInTheDocument();
    const confirm = within(dialog).getByRole('button', { name: '确认并回滚' });
    expect(confirm).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText(/^回滚理由/), { target: { value: '线上回答偏离合同范围，恢复已验证版本' } });
    expect(confirm).toBeDisabled();
    await actor.click(within(dialog).getByRole('checkbox', { name: /会创建新的活动版本/ }));
    await actor.click(confirm);

    expect(await within(dialog).findByText('版本激活暂时冲突')).toBeInTheDocument();
    expect(within(dialog).getByText('错误追踪：trace-failure')).toBeInTheDocument();
    await actor.click(within(dialog).getByRole('button', { name: '重试并回滚' }));

    expect(await screen.findByText(/已基于 v1 创建并激活 v3/)).toBeInTheDocument();
    expect(await screen.findByText('TRACE-ROLLBACK-3')).toBeInTheDocument();
    expect(releaseReads).toBe(2);
    expect(rollbackWrites).toHaveLength(2);
    expect(rollbackWrites[0].url).toBe('/api/v1/ai-applications/APP-1/releases/RELEASE-1/rollback');
    expect(rollbackWrites[0].body).toMatchObject({ reason: '线上回答偏离合同范围，恢复已验证版本' });
    expect(rollbackWrites[0].body.rollbackRequestId).toEqual(rollbackWrites[1].body.rollbackRequestId);
  });

  it('keeps cross-owner ACL governance read-only and exposes only frozen release evidence', async () => {
    const requestedWrites: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      const method = init?.method || 'GET';
      if (method !== 'GET') {
        requestedWrites.push(`${method} ${url}`);
        throw new Error(`governance read-only mode must not write: ${method} ${url}`);
      }
      if (url.startsWith('/api/v1/chat-runs/dead-letters')) return envelope([]);
      if (url === '/api/v1/ai-applications/APP-2/releases') return envelope([{
        ...releaseV1,
        releaseId: 'RELEASE-GOV-1',
        active: true,
        modelConfigId: 'MODEL-FROZEN-1',
        knowledgeBaseIds: ['KB-FROZEN-1'],
        systemPromptSummary: null,
        systemPromptSha256: 'f'.repeat(64),
        traceId: 'TRACE-GOVERNANCE-1',
      }]);
      return envelope([governanceOnlyApplication]);
    });

    renderPage();

    expect(await screen.findByText('TRACE-GOVERNANCE-1')).toBeInTheDocument();
    expect(screen.getByRole('note', { name: '治理只读范围说明' })).toBeInTheDocument();
    expect(screen.getByText(/MODEL-FROZEN-1/)).toBeInTheDocument();
    expect(screen.getByText(/KB-FROZEN-1/)).toBeInTheDocument();
    expect(screen.getByText(/f{64}/)).toBeInTheDocument();
    expect(screen.queryByText('不得向治理只读主体泄露的提示词')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '编辑配置' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '发布预检' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '重新预检' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '确认发布' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /回滚到/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '访问策略只读' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '当前可发布草稿配置' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '访问策略' })).not.toBeInTheDocument();
    expect(requestedWrites).toEqual([]);
  });

  it('fails closed when access mode and capabilities disagree and never renders business runtime configuration', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (String(input).startsWith('/api/v1/chat-runs/dead-letters')) return envelope([]);
      return envelope([{
        ...publishedApplication,
        accessMode: 'BUSINESS_USER',
        capabilities: ownerCapabilities,
        modelConfigId: 'MODEL-MUST-STAY-HIDDEN',
        knowledgeBaseIds: ['KB-MUST-STAY-HIDDEN'],
        systemPrompt: 'PROMPT-MUST-STAY-HIDDEN',
      }]);
    });

    renderPage();

    expect(await screen.findByRole('heading', { name: '合同审阅助手' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '编辑配置' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '发布预检' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '确认发布' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '当前可发布草稿配置' })).not.toBeInTheDocument();
    expect(screen.queryByText('MODEL-MUST-STAY-HIDDEN')).not.toBeInTheDocument();
    expect(screen.queryByText('KB-MUST-STAY-HIDDEN')).not.toBeInTheDocument();
    expect(screen.queryByText('PROMPT-MUST-STAY-HIDDEN')).not.toBeInTheDocument();
    expect(screen.getByRole('note', { name: '业务使用范围说明' })).toBeInTheDocument();
  });

  it('keeps the page closed on load failure and exposes traceable retry evidence', async () => {
    const actor = userEvent.setup();
    let attempts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (String(input).startsWith('/api/v1/chat-runs/dead-letters')) return envelope([]);
      attempts += 1;
      return attempts === 1 ? failure('应用目录暂时不可用', 'AIP-APP-STUDIO-503', 503) : envelope([draftApplication]);
    });
    renderPage();

    expect(await screen.findByText('应用目录暂时不可用')).toBeInTheDocument();
    expect(screen.getByText('错误追踪：trace-failure')).toBeInTheDocument();
    expect(screen.queryByText('合同审阅助手')).not.toBeInTheDocument();
    await actor.click(screen.getByRole('button', { name: '重试读取应用' }));

    expect(await screen.findByRole('heading', { name: '合同审阅助手' })).toBeInTheDocument();
    expect(attempts).toBe(2);
  });
});
