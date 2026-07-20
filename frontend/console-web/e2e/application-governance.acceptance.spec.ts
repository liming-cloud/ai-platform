import {
  expect,
  test,
  type APIRequestContext,
  type APIResponse,
  type Locator,
  type Page,
  type Response,
} from '@playwright/test';
import {
  expectNoFullPageHorizontalOverflow,
  expectNoWcagAaViolations,
} from './fixtures/accessibility';
import {
  navigateThroughProductMenu,
  roleCredentials,
  signInThroughUi,
} from './fixtures/authorized';
import { DEPLOY_APPLICATION_NAME } from './fixtures/deploy-provision';

type RuntimeEnvironment = Readonly<Record<string, string | undefined>>;
declare const process: { readonly env: RuntimeEnvironment };

type ApiEnvelope<T> = {
  header: { code: string; message: string; traceId?: string };
  body: T;
};

type CurrentUserContract = {
  tenantId: string;
  userId: string;
  permissionCodes: string[];
};

type ChatRunDeadLetterItem = {
  runId: string;
  sessionId: string;
  applicationId: string;
  originalActorId: string;
  finalizationAttemptCount: number;
  finalizationCycleAttemptCount: number;
  manualReplayCount: number;
  failureCode: string;
  failureSummary: string;
  failureTraceId: string;
  updatedAt: string;
};

type ChatRunManualReplayResult = {
  runId: string;
  replayRequestId: string;
  status: string;
};

type ApplicationView = {
  applicationId: string;
  name: string;
  description: string;
  publicationStatus: string;
  activeReleaseId: string;
  activeReleaseVersion: number;
};

type ChatSessionView = {
  sessionId: string;
  releaseId: string;
  releaseVersion: number;
};

type PermissionRequestView = {
  requestId: string;
  requesterId: string;
  resourceType: string;
  resourceId: string;
  permissionCode: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
};

type PublishPreflightView = {
  applicationId: string;
  publishRequestId: string;
  passed: boolean;
  blockers: unknown[];
  warnings: unknown[];
  traceId: string;
};

type ApplicationReleaseView = {
  releaseId: string;
  version: number;
  active: boolean;
  operation: 'PUBLISH' | 'ROLLBACK';
  sourceReleaseId: string | null;
  allowedUserCount: number;
  traceId: string;
};

type RequestablePermissionTarget = {
  resourceType: string;
  resourceId: string;
  permissionCode: string;
};

type ApiFailureEvidence<T> = {
  status: number;
  code: string;
  message: string;
  traceId?: string;
  body: T;
};

const REQUIRED_RECOVERY_PERMISSIONS = [
  'app-studio:application:publish',
  'app-studio:application:manage-acl',
] as const;

const SAFE_DEAD_LETTER_KEYS = [
  'runId',
  'sessionId',
  'applicationId',
  'originalActorId',
  'finalizationAttemptCount',
  'finalizationCycleAttemptCount',
  'manualReplayCount',
  'failureCode',
  'failureSummary',
  'failureTraceId',
  'updatedAt',
] as const;

const SAFE_DEAD_LETTER_STRING_KEYS = [
  'runId',
  'sessionId',
  'applicationId',
  'originalActorId',
  'failureCode',
  'failureSummary',
  'failureTraceId',
  'updatedAt',
] as const;

const SAFE_DEAD_LETTER_COUNT_KEYS = [
  'finalizationAttemptCount',
  'finalizationCycleAttemptCount',
  'manualReplayCount',
] as const;

const themeMatrix = [
  { id: 'light', label: '浅色', colorScheme: 'light', forcedColors: 'none' },
  { id: 'dark', label: '深色', colorScheme: 'dark', forcedColors: 'none' },
  { id: 'forced-colors', label: '系统强制色', colorScheme: 'light', forcedColors: 'active' },
] as const;

function requestPath(value: string) {
  return new URL(value).pathname;
}

async function responseBody<T>(response: Response): Promise<T> {
  expect(response.ok(), `${response.request().method()} ${response.url()} must succeed`).toBe(true);
  const envelope = await response.json() as ApiEnvelope<T>;
  expect(envelope.header.code).toBe('SUCCESS');
  expect(Object.prototype.hasOwnProperty.call(envelope, 'body')).toBe(true);
  return envelope.body;
}

async function apiSuccess<T>(response: APIResponse, label: string): Promise<T> {
  const envelope = await response.json() as ApiEnvelope<T>;
  expect(response.ok(), `${label} must succeed; code=${envelope.header.code}`).toBe(true);
  expect(envelope.header.code, `${label} must return SUCCESS`).toBe('SUCCESS');
  expect(Object.prototype.hasOwnProperty.call(envelope, 'body'), `${label} must return a body`).toBe(true);
  return envelope.body;
}

async function apiFailure<T>(
  response: APIResponse,
  expectedStatus: number,
  expectedCode: string,
  label: string,
): Promise<ApiFailureEvidence<T>> {
  const envelope = await response.json() as ApiEnvelope<T>;
  expect(response.status(), `${label} HTTP status`).toBe(expectedStatus);
  expect(response.ok(), `${label} must fail closed`).toBe(false);
  expect(envelope.header.code, `${label} application error code`).toBe(expectedCode);
  return {
    status: response.status(),
    code: envelope.header.code,
    message: envelope.header.message,
    traceId: envelope.header.traceId,
    body: envelope.body,
  };
}

function requiredEnvironment(key: string) {
  const value = process.env[key]?.trim();
  if (!value) throw new Error(`Missing deploy acceptance environment: ${key}`);
  return value;
}

function authorization(token: string) {
  return { Authorization: `Bearer ${token}` };
}

async function accessTokenFor(
  request: APIRequestContext,
  credentials: { tenantCode: string; username: string; password: string },
  label: string,
): Promise<string> {
  const loginResponse = await request.post('/api/v1/auth/login', { data: credentials });
  const login = await apiSuccess<{ token: string }>(loginResponse, `${label} login`);
  expect(login.token, `${label} login token`).not.toBe('');
  return login.token;
}

function tenantAdminCredentials() {
  const credentials = roleCredentials('tenant-admin');
  if (!credentials) {
    throw new Error('Missing deploy tenant administrator credentials.');
  }
  return credentials;
}

async function signInAsTenantAdmin(page: Page): Promise<CurrentUserContract> {
  const currentUserResponse = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && requestPath(response.url()) === '/api/v1/auth/me'
  ));
  await signInThroughUi(page, tenantAdminCredentials());
  return responseBody<CurrentUserContract>(await currentUserResponse);
}

async function openApplicationGovernance(page: Page) {
  await navigateThroughProductMenu(page, 'AI应用');
  await expect(page).toHaveURL(/\/tenant\/applications$/);
  await expect(page.getByRole('heading', { level: 1, name: '应用工作室' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: '聊天最终化恢复' })).toBeVisible();
}

async function businessAccessToken(request: APIRequestContext): Promise<string> {
  const credentials = roleCredentials('business-user');
  if (!credentials) throw new Error('Missing deploy business user credentials.');
  return accessTokenFor(request, credentials, 'business user');
}

async function applicationCatalog(request: APIRequestContext, token?: string): Promise<ApplicationView[]> {
  const accessToken = token || await businessAccessToken(request);
  const response = await request.get('/api/v1/ai-applications', {
    headers: authorization(accessToken),
  });
  return apiSuccess<ApplicationView[]>(response, 'application catalog');
}

async function waitForRecoveryTerminalState(page: Page): Promise<'empty' | 'list'> {
  const emptyState = page.getByText('当前没有等待人工处理的最终化死信', { exact: true });
  const table = page.getByRole('table', { name: '聊天最终化死信列表' });
  await expect(emptyState.or(table)).toBeVisible();
  return await table.isVisible() ? 'list' : 'empty';
}

async function definitionValue(dialog: Locator, label: string) {
  const term = dialog.getByText(label, { exact: true });
  const value = term.locator('xpath=following-sibling::dd[1]');
  await expect(value).toBeVisible();
  return (await value.innerText()).trim();
}

for (const theme of themeMatrix) {
  test.describe(`deploy ${theme.label}主题`, () => {
    test('应用治理可见渲染、无横向溢出、通过 axe 并保留截图', async ({ page }, testInfo) => {
      await page.emulateMedia({ colorScheme: theme.colorScheme, forcedColors: theme.forcedColors });
      await page.setViewportSize({ width: 1440, height: 1000 });
      await signInAsTenantAdmin(page);
      await openApplicationGovernance(page);
      await waitForRecoveryTerminalState(page);

      const media = await page.evaluate(() => ({
        dark: window.matchMedia('(prefers-color-scheme: dark)').matches,
        light: window.matchMedia('(prefers-color-scheme: light)').matches,
        forcedColors: window.matchMedia('(forced-colors: active)').matches,
      }));
      expect(media.forcedColors).toBe(theme.forcedColors === 'active');
      if (theme.forcedColors === 'none') {
        expect(media.dark).toBe(theme.colorScheme === 'dark');
        expect(media.light).toBe(theme.colorScheme === 'light');
      }

      const main = page.locator('main.page-stack');
      await expect(main).toBeVisible();
      const bounds = await main.boundingBox();
      expect(bounds?.width).toBeGreaterThan(0);
      expect(bounds?.height).toBeGreaterThan(0);
      await expect(page.getByLabel('当前安全上下文')).toBeVisible();
      await expectNoFullPageHorizontalOverflow(page);
      await expectNoWcagAaViolations(page);

      await testInfo.attach(`application-governance-${theme.id}`, {
        body: await page.screenshot({ fullPage: true }),
        contentType: 'image/png',
      });
    });
  });
}

test('租户管理员进入应用治理并观察聊天最终化 loading 到 empty/list 的真实状态迁移', async ({ page }) => {
  const user = await signInAsTenantAdmin(page);
  expect(user.permissionCodes).toEqual(expect.arrayContaining([...REQUIRED_RECOVERY_PERMISSIONS]));

  await page.route((url) => url.pathname === '/api/v1/chat-runs/dead-letters', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 500));
    await route.continue();
  });
  const deadLetterResponse = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && requestPath(response.url()) === '/api/v1/chat-runs/dead-letters'
  ));

  await openApplicationGovernance(page);
  await expect(page.getByText('正在读取当前租户最终化死信', { exact: true })).toBeVisible();
  const deadLetters = await responseBody<ChatRunDeadLetterItem[]>(await deadLetterResponse);
  const terminalState = await waitForRecoveryTerminalState(page);

  expect(terminalState).toBe(deadLetters.length > 0 ? 'list' : 'empty');
  await expect(page.getByText(`当前租户 ${user.tenantId}`, { exact: true })).toBeVisible();
  const securityContext = page.getByLabel('当前安全上下文');
  await expect(securityContext).toContainText(`租户 ${user.tenantId}`);
  await expect(securityContext).toContainText('角色 租户管理员');
  await expect(page.getByLabel('缺失权限')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '刷新死信' })).toBeEnabled();
  await expectNoFullPageHorizontalOverflow(page);
  await expectNoWcagAaViolations(page);
});

test('严格开关下按冻结租户、运行与幂等键人工重放注入的最终化死信', async ({ page }, testInfo) => {
  const strictRecoveryRequired = process.env.E2E_REQUIRE_CHAT_RECOVERY?.trim().toLowerCase() === 'true';
  if (!strictRecoveryRequired) {
    testInfo.annotations.push({
      type: 'manual-replay-gate',
      description: 'Set E2E_REQUIRE_CHAT_RECOVERY=true and inject E2E_CHAT_DEAD_LETTER_RUN_ID to exercise manual replay.',
    });
    return;
  }

  const targetRunId = process.env.E2E_CHAT_DEAD_LETTER_RUN_ID?.trim();
  if (!targetRunId) {
    throw new Error('E2E_REQUIRE_CHAT_RECOVERY=true requires E2E_CHAT_DEAD_LETTER_RUN_ID.');
  }

  const user = await signInAsTenantAdmin(page);
  expect(user.permissionCodes).toEqual(expect.arrayContaining([...REQUIRED_RECOVERY_PERMISSIONS]));
  const deadLetterResponse = page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && requestPath(response.url()) === '/api/v1/chat-runs/dead-letters'
  ));
  await openApplicationGovernance(page);
  const deadLetters = await responseBody<ChatRunDeadLetterItem[]>(await deadLetterResponse);
  const target = deadLetters.find((candidate) => candidate.runId === targetRunId);
  expect(
    target,
    `Strict chat recovery requires injected run ${targetRunId} to be visible to tenant ${user.tenantId}.`,
  ).toBeDefined();
  if (!target) throw new Error(`Injected dead-letter run is missing: ${targetRunId}`);

  expect(Object.keys(target).sort()).toEqual([...SAFE_DEAD_LETTER_KEYS].sort());
  for (const key of SAFE_DEAD_LETTER_STRING_KEYS) {
    expect(typeof target[key], `${key} must be a string`).toBe('string');
    expect(target[key].trim(), `${key} must not be blank`).not.toBe('');
  }
  for (const key of SAFE_DEAD_LETTER_COUNT_KEYS) {
    expect(Number.isInteger(target[key]), `${key} must be an integer`).toBe(true);
    expect(target[key], `${key} must not be negative`).toBeGreaterThanOrEqual(0);
  }
  const table = page.getByRole('table', { name: '聊天最终化死信列表' });
  await expect(table).toBeVisible();
  const row = table.getByRole('row').filter({ hasText: targetRunId });
  await expect(row, `Injected dead-letter row ${targetRunId} must exist; strict mode never skips it.`).toHaveCount(1);
  await expect(row).toContainText(target.applicationId);
  await expect(row).toContainText(target.sessionId);
  await expect(row).toContainText(target.failureCode);
  await expect(row).toContainText(target.failureSummary);
  await expect(row).toContainText(target.failureTraceId);
  await expect(row).toContainText(`人工重放 ${target.manualReplayCount} 次`);
  await expect(row).toContainText(`第 ${target.finalizationCycleAttemptCount} / 3 次`);
  await expect(row).toContainText(`累计 ${target.finalizationAttemptCount} 次`);
  await expect(row.locator('time')).toHaveAttribute('dateTime', target.updatedAt);
  await expect(row.getByText(target.originalActorId, { exact: true })).toHaveCount(0);

  await row.getByRole('button', { name: `重放 ${targetRunId}`, exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '确认人工重放' });
  await expect(dialog).toBeVisible();
  expect(await definitionValue(dialog, '当前租户')).toBe(user.tenantId);
  expect(await definitionValue(dialog, '运行 ID')).toBe(targetRunId);
  expect(await definitionValue(dialog, '应用 ID')).toBe(target.applicationId);
  expect(await definitionValue(dialog, '会话 ID')).toBe(target.sessionId);
  expect(await definitionValue(dialog, '失败周期')).toBe(`第 ${target.finalizationCycleAttemptCount} / 3 次`);
  const replayRequestId = await definitionValue(dialog, '重放请求 ID');
  expect(replayRequestId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
  await expect(dialog.getByText(
    '只重放已保存的最终化事务，不会再次调用模型、检索或策略链。',
    { exact: true },
  )).toBeVisible();

  const replayReason = 'deploy E2E 已确认最终化依赖恢复，执行受控人工重放';
  const confirm = dialog.getByRole('button', { name: '确认重放' });
  await expect(confirm).toBeDisabled();
  await dialog.getByLabel('重放原因').fill(replayReason);
  await expect(confirm).toBeEnabled();

  const replayRequest = page.waitForRequest((request) => (
    request.method() === 'POST'
      && requestPath(request.url()) === `/api/v1/chat-runs/${encodeURIComponent(targetRunId)}/dead-letter/replay`
  ));
  const replayResponse = page.waitForResponse((response) => (
    response.request().method() === 'POST'
      && requestPath(response.url()) === `/api/v1/chat-runs/${encodeURIComponent(targetRunId)}/dead-letter/replay`
  ));
  await confirm.click();

  const request = await replayRequest;
  const requestBody = request.postDataJSON() as Record<string, unknown>;
  expect(requestBody).toEqual({ replayRequestId, reason: replayReason });
  expect(requestBody).not.toHaveProperty('tenantId');

  const outcome = await responseBody<ChatRunManualReplayResult>(await replayResponse);
  expect(Object.keys(outcome).sort()).toEqual(['replayRequestId', 'runId', 'status']);
  expect(outcome.runId).toBe(targetRunId);
  expect(outcome.replayRequestId).toBe(replayRequestId);
  expect(['COMPLETED', 'RETRY_SCHEDULED', 'DEAD_LETTER']).toContain(outcome.status);
  await expect(page.getByText(`运行 ${targetRunId}，状态 ${outcome.status}。`, { exact: false })).toBeVisible();
  const securityContext = page.getByLabel('当前安全上下文');
  await expect(securityContext).toContainText(`租户 ${user.tenantId}`);
  await expect(securityContext).toContainText('角色 租户管理员');
});

test('候选申请人经批准后仅进入新发布 v2，回滚 v1 生成 v3 后再次失去访问', async ({ page, request }, testInfo) => {
  test.slow();
  const applicantUsername = requiredEnvironment('E2E_ACCEPTANCE_APPLICANT_USERNAME');
  const publishRequestId = requiredEnvironment('E2E_APPLICATION_PUBLISH_REQUEST_ID');
  const rollbackRequestId = requiredEnvironment('E2E_APPLICATION_ROLLBACK_REQUEST_ID');
  const staleClientMessageId = requiredEnvironment('E2E_STALE_SESSION_CLIENT_MESSAGE_ID');
  const staleTraceId = requiredEnvironment('E2E_STALE_SESSION_TRACE_ID');
  const tenantAdmin = tenantAdminCredentials();
  const businessCredentials = roleCredentials('business-user');
  if (!businessCredentials) throw new Error('Missing deploy business user credentials.');

  const [adminToken, businessToken, applicantToken] = await Promise.all([
    accessTokenFor(request, tenantAdmin, 'tenant administrator'),
    accessTokenFor(request, businessCredentials, 'business user'),
    accessTokenFor(request, { ...businessCredentials, username: applicantUsername }, 'candidate applicant'),
  ]);

  const adminApplications = await applicationCatalog(request, adminToken);
  const applicationV1 = adminApplications.find((application) => application.name === DEPLOY_APPLICATION_NAME);
  if (!applicationV1) throw new Error('Global deploy provisioning did not create the governed acceptance application.');
  expect(applicationV1).toMatchObject({ activeReleaseVersion: 1, publicationStatus: 'PUBLISHED' });

  const releasesPath = `/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}/releases`;
  const releasesAtV1 = await apiSuccess<ApplicationReleaseView[]>(
    await request.get(releasesPath, { headers: authorization(adminToken) }),
    'v1 release history',
  );
  expect(releasesAtV1).toHaveLength(1);
  expect(releasesAtV1[0]).toMatchObject({ version: 1, active: true, operation: 'PUBLISH' });
  const v1Release = releasesAtV1[0];

  const businessV1Session = await apiSuccess<ChatSessionView>(
    await request.post(
      `/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}/chat-sessions`,
      {
        headers: authorization(businessToken),
        data: { title: '固定 v1 的 deploy 验收会话', traceId: 'deploy-release-session-v1' },
      },
    ),
    'business v1 session',
  );
  expect(businessV1Session).toMatchObject({ releaseId: v1Release.releaseId, releaseVersion: 1 });

  expect((await applicationCatalog(request, applicantToken))
    .some((application) => application.applicationId === applicationV1.applicationId)).toBe(false);
  const applicantDenialBeforeApproval = await apiFailure<RequestablePermissionTarget>(
    await request.post(
      `/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}/chat-sessions`,
      {
        headers: authorization(applicantToken),
        data: { title: 'v1 未授权申请人会话', traceId: 'deploy-applicant-v1-denied-before' },
      },
    ),
    403,
    'AIP-IAM-AUTHZ-5003-REQUESTABLE',
    'candidate applicant before approval',
  );
  expect(applicantDenialBeforeApproval.body).toEqual({
    resourceType: 'AI_APPLICATION',
    resourceId: applicationV1.applicationId,
    permissionCode: 'app-studio:application:use',
  });

  const permissionRequest = await apiSuccess<PermissionRequestView>(
    await request.post('/api/v1/permission-requests', {
      headers: authorization(applicantToken),
      data: {
        ...applicantDenialBeforeApproval.body,
        reason: '需要完成候选版本的企业知识问答验收',
      },
    }),
    'candidate applicant permission request',
  );
  expect(permissionRequest).toMatchObject({
    resourceType: 'AI_APPLICATION',
    resourceId: applicationV1.applicationId,
    permissionCode: 'app-studio:application:use',
    status: 'PENDING',
  });

  const approvedRequest = await apiSuccess<PermissionRequestView>(
    await request.post(`/api/v1/permission-requests/${encodeURIComponent(permissionRequest.requestId)}/actions`, {
      headers: authorization(adminToken),
      data: { action: 'APPROVE', comment: '批准进入下一次不可变发布；活动 v1 不得原地扩权' },
    }),
    'tenant administrator approval',
  );
  expect(approvedRequest).toMatchObject({ requestId: permissionRequest.requestId, status: 'APPROVED' });

  expect((await applicationCatalog(request, applicantToken))
    .some((application) => application.applicationId === applicationV1.applicationId)).toBe(false);
  const applicantDenialAfterApproval = await apiFailure<RequestablePermissionTarget>(
    await request.post(
      `/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}/chat-sessions`,
      {
        headers: authorization(applicantToken),
        data: { title: '批准后仍固定在旧 v1', traceId: 'deploy-applicant-v1-denied-after' },
      },
    ),
    403,
    'AIP-IAM-AUTHZ-5003-REQUESTABLE',
    'approved draft ACL against immutable v1',
  );
  expect(applicantDenialAfterApproval.body).toEqual(applicantDenialBeforeApproval.body);

  const preflight = await apiSuccess<PublishPreflightView>(
    await request.post(
      `/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}/publish/preflight`,
      { headers: authorization(adminToken), data: { publishRequestId } },
    ),
    'v2 publish preflight',
  );
  expect(preflight).toMatchObject({
    applicationId: applicationV1.applicationId,
    publishRequestId,
    passed: true,
    blockers: [],
  });

  const publishData = { publishRequestId, reason: '发布包含已批准申请人的不可变受众 v2' };
  const concurrentPublishResponses = await Promise.all([
    request.post(`/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}/publish`, {
      headers: authorization(adminToken), data: publishData,
    }),
    request.post(`/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}/publish`, {
      headers: authorization(adminToken), data: publishData,
    }),
  ]);
  const concurrentPublishes = await Promise.all(concurrentPublishResponses.map((response, index) => (
    apiSuccess<ApplicationView>(response, `concurrent v2 publish ${index + 1}`)
  )));
  expect(new Set(concurrentPublishes.map((published) => published.activeReleaseId)).size).toBe(1);
  expect(concurrentPublishes).toEqual(expect.arrayContaining([
    expect.objectContaining({ activeReleaseVersion: 2, publicationStatus: 'PUBLISHED' }),
  ]));
  const repeatedPublish = await apiSuccess<ApplicationView>(
    await request.post(`/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}/publish`, {
      headers: authorization(adminToken), data: publishData,
    }),
    'repeated v2 publish',
  );
  expect(repeatedPublish).toMatchObject({
    activeReleaseId: concurrentPublishes[0].activeReleaseId,
    activeReleaseVersion: 2,
  });

  const applicantAtV2 = (await applicationCatalog(request, applicantToken))
    .find((application) => application.applicationId === applicationV1.applicationId);
  expect(applicantAtV2).toMatchObject({ activeReleaseVersion: 2, publicationStatus: 'PUBLISHED' });
  const applicantV2Session = await apiSuccess<ChatSessionView>(
    await request.post(
      `/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}/chat-sessions`,
      {
        headers: authorization(applicantToken),
        data: { title: '申请人固定 v2 会话', traceId: 'deploy-applicant-v2-session' },
      },
    ),
    'candidate applicant v2 session',
  );
  expect(applicantV2Session).toMatchObject({
    releaseId: concurrentPublishes[0].activeReleaseId,
    releaseVersion: 2,
  });

  const staleV1Failure = await apiFailure<null>(
    await request.post(`/api/v1/chat-sessions/${encodeURIComponent(businessV1Session.sessionId)}/messages`, {
      headers: authorization(businessToken),
      data: {
        clientMessageId: staleClientMessageId,
        content: '旧会话不得调用检索或模型，也不得漂移到 v2',
        traceId: staleTraceId,
      },
    }),
    409,
    'AIP-APP-CHAT-SESSION-CONFLICT',
    'stale v1 session after v2 publish',
  );
  expect(staleV1Failure.body).toBeNull();

  const rollbackConflict = await apiFailure<null>(
    await request.post(
      `/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}`
        + `/releases/${encodeURIComponent(v1Release.releaseId)}/rollback`,
      {
        headers: authorization(adminToken),
        data: { rollbackRequestId: publishRequestId, reason: '不得跨操作复用发布幂等键' },
      },
    ),
    400,
    'AIP-APP-PUBLISH-6001',
    'publish idempotency key reused for rollback',
  );
  expect(rollbackConflict.body).toBeNull();

  const rolledBackV3 = await apiSuccess<ApplicationView>(
    await request.post(
      `/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}`
        + `/releases/${encodeURIComponent(v1Release.releaseId)}/rollback`,
      {
        headers: authorization(adminToken),
        data: { rollbackRequestId, reason: '从 v1 快照创建新的活动 v3，并恢复 v1 冻结受众' },
      },
    ),
    'rollback v1 into new v3 release',
  );
  expect(rolledBackV3).toMatchObject({ activeReleaseVersion: 3, publicationStatus: 'PUBLISHED' });

  expect((await applicationCatalog(request, applicantToken))
    .some((application) => application.applicationId === applicationV1.applicationId)).toBe(false);
  const applicantDenialAfterRollback = await apiFailure<RequestablePermissionTarget>(
    await request.post(
      `/api/v1/ai-applications/${encodeURIComponent(applicationV1.applicationId)}/chat-sessions`,
      {
        headers: authorization(applicantToken),
        data: { title: '回滚后 v3 不得继承 v2 受众', traceId: 'deploy-applicant-v3-denied' },
      },
    ),
    403,
    'AIP-IAM-AUTHZ-5003-REQUESTABLE',
    'candidate applicant after v1 rollback',
  );
  expect(applicantDenialAfterRollback.body).toEqual(applicantDenialBeforeApproval.body);

  const releasesAtV3 = await apiSuccess<ApplicationReleaseView[]>(
    await request.get(releasesPath, { headers: authorization(adminToken) }),
    'v3 release history',
  );
  expect(releasesAtV3.map((release) => release.version)).toEqual([3, 2, 1]);
  expect(releasesAtV3.filter((release) => release.active)).toEqual([
    expect.objectContaining({
      version: 3,
      operation: 'ROLLBACK',
      sourceReleaseId: v1Release.releaseId,
    }),
  ]);

  await testInfo.attach('application-release-api-evidence', {
    body: JSON.stringify({
      applicationId: applicationV1.applicationId,
      candidateApplicantUsername: applicantUsername,
      permissionRequestId: permissionRequest.requestId,
      permissionDecision: approvedRequest.status,
      v1: { releaseId: v1Release.releaseId, applicantDenied: applicantDenialBeforeApproval.code },
      v2: {
        releaseId: concurrentPublishes[0].activeReleaseId,
        publishRequestId,
        concurrentResponses: concurrentPublishes.length,
        repeatConverged: repeatedPublish.activeReleaseId === concurrentPublishes[0].activeReleaseId,
        applicantSessionId: applicantV2Session.sessionId,
      },
      staleV1: {
        sessionId: businessV1Session.sessionId,
        clientMessageId: staleClientMessageId,
        traceId: staleTraceId,
        status: staleV1Failure.status,
        code: staleV1Failure.code,
      },
      idempotencyConflict: { status: rollbackConflict.status, code: rollbackConflict.code },
      v3: {
        releaseId: rolledBackV3.activeReleaseId,
        rollbackRequestId,
        sourceReleaseId: v1Release.releaseId,
        applicantDenied: applicantDenialAfterRollback.code,
      },
    }, null, 2),
    contentType: 'application/json',
  });

  await signInAsTenantAdmin(page);
  await openApplicationGovernance(page);
  const applicationRow = page.getByRole('table', { name: '应用治理列表' })
    .getByRole('row').filter({ hasText: DEPLOY_APPLICATION_NAME });
  await expect(applicationRow).toHaveCount(1);
  await applicationRow.getByRole('button', { name: '治理' }).click();
  const releaseTable = page.getByRole('table', { name: '应用发布版本' });
  await expect(releaseTable).toBeVisible();
  await expect(releaseTable.getByText('当前活动')).toHaveCount(1);
  await expect(releaseTable.getByRole('row').filter({ has: page.getByText('v3', { exact: true }) }))
    .toContainText('回滚');
  await testInfo.attach('application-release-v3-governance', {
    body: await page.screenshot({ fullPage: true }),
    contentType: 'image/png',
  });

  await page.getByRole('button', { name: '退出登录' }).click();
  await signInThroughUi(page, businessCredentials);
  await page.goto(`/workspace/sessions/${businessV1Session.sessionId}`);
  await expect(page.getByRole('heading', { level: 1, name: 'RAG 对话' })).toBeVisible();
  let staleBrowserRequests = 0;
  page.on('request', (browserRequest) => {
    if (browserRequest.method() === 'POST'
      && requestPath(browserRequest.url()) === `/api/v1/chat-sessions/${businessV1Session.sessionId}/messages`) {
      staleBrowserRequests += 1;
    }
  });
  const staleBrowserResponse = page.waitForResponse((response) => (
    response.request().method() === 'POST'
      && requestPath(response.url()) === `/api/v1/chat-sessions/${businessV1Session.sessionId}/messages`
  ));
  await page.getByLabel('输入问题').fill('旧会话不得漂移到新发布版本');
  await page.getByRole('button', { name: '发送' }).click();
  const browserFailureResponse = await staleBrowserResponse;
  expect(browserFailureResponse.status()).toBe(409);
  const browserFailure = await browserFailureResponse.json() as ApiEnvelope<null>;
  expect(browserFailure.header.code).toBe('AIP-APP-CHAT-SESSION-CONFLICT');
  await expect(page.getByRole('alert')).toContainText('应用已发布新版本，请新建会话后继续');
  await expect(page.getByRole('alert')).toContainText('固定在旧发布版本');
  expect(staleBrowserRequests).toBe(1);
  await expect(page.getByRole('button', { name: '重试同一请求' })).toHaveCount(0);
  await expect(page.getByLabel('输入问题')).toBeDisabled();
  await expectNoFullPageHorizontalOverflow(page);
  await expectNoWcagAaViolations(page);
  await testInfo.attach('stale-v1-session-fails-closed-after-v3', {
    body: await page.screenshot({ fullPage: true }),
    contentType: 'image/png',
  });
  await page.getByRole('button', { name: '返回 AI 应用并新建会话' }).click();
  await expect(page).toHaveURL(/\/workspace\/apps$/);
  await expectNoFullPageHorizontalOverflow(page);
  await expectNoWcagAaViolations(page);
});
