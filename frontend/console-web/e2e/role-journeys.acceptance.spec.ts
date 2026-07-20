import { mkdir, open, readFile, unlink, writeFile, type FileHandle } from 'node:fs/promises';
import { join } from 'node:path';
import {
  expect,
  test,
  type APIRequestContext,
  type APIResponse,
  type Locator,
  type Page,
  type Response,
  type TestInfo,
} from '@playwright/test';
import {
  expectNoFullPageHorizontalOverflow,
  expectNoWcagAaViolations,
} from './fixtures/accessibility';
import {
  DEPLOY_APPLICATION_NAME,
  DEPLOY_BUSINESS_QUESTION,
  DEPLOY_CANDIDATE_APPROVER_USERNAME,
  DEPLOY_CANDIDATE_INVITATION_EMAIL,
  DEPLOY_CANDIDATE_ROLE_NAME,
  DEPLOY_KNOWLEDGE_BASE_NAME,
  DEPLOY_KNOWLEDGE_EVIDENCE_FILE_NAME,
  DEPLOY_KNOWLEDGE_RECOVERY_FILE_NAME,
  DEPLOY_ROLE_JOURNEY_RUN_ID,
} from './fixtures/deploy-provision';
import {
  navigateThroughProductMenu,
  roleCredentials,
  signInThroughUi,
  type AcceptanceRole,
  type DeployCredentials,
} from './fixtures/authorized';
import {
  expectDialogMotionEvidence,
  installDialogMotionEvidence,
  resetDialogMotionEvidence,
} from './fixtures/dialog-motion-evidence';

// The platform journey submits one ephemeral credential. Disable network trace
// and video for this file so failure artifacts cannot retain request material;
// screenshots still see only a password input and non-secret terminal state.
test.use({ trace: 'off', video: 'off' });

const viewportMatrix = [
  { name: '宽屏桌面', width: 1440, height: 1000 },
  { name: '标准笔记本', width: 1280, height: 800 },
  { name: '紧凑桌面', width: 1024, height: 768 },
  { name: '平板竖屏断点', width: 768, height: 1024 },
  { name: '移动竖屏', width: 390, height: 844 },
] as const;

const modelHubThemeMatrix = [
  { id: 'light', label: '浅色', colorScheme: 'light', forcedColors: 'none' },
  { id: 'dark', label: '深色', colorScheme: 'dark', forcedColors: 'none' },
  { id: 'forced-colors', label: '系统强制色', colorScheme: 'light', forcedColors: 'active' },
] as const;

const RECOVERY_BUDGET_MS = 30_000;
const MUTATION_ACTION_TIMEOUT_MS = 10_000;

type RuntimeEnvironment = Readonly<Record<string, string | undefined>>;
declare const process: { readonly env: RuntimeEnvironment };
function requiredModelProbeEndpoint(): string {
  const endpoint = process.env.E2E_MODEL_PROBE_ENDPOINT;
  if (endpoint !== 'https://203.1.113.2/v1') {
    throw new Error('E2E_MODEL_PROBE_ENDPOINT must identify the exact deploy acceptance TLS-failure probe.');
  }
  return endpoint;
}

type MutationLease = {
  handle: FileHandle;
  lockPath: string;
  resource:
    | 'platform-tenant-name'
    | 'platform-model-lifecycle'
    | 'tenant-role-permissions'
    | 'tenant-candidate-governance'
    | 'business-chat-idempotency';
};

type ApiEnvelope<T> = {
  header: { code: string; message: string; traceId?: string };
  body: T;
};

type ApiIdentity = {
  token: string;
  tenantId: string;
  userId: string;
};

type IamRoleContract = {
  roleId: string;
  name: string;
  type: string;
};

type IamPermissionContract = {
  code: string;
  name: string;
};

type UserInvitationContract = {
  invitationId: string;
  email: string;
  status: string;
};

type KnowledgeBaseContract = {
  knowledgeBaseId: string;
  name: string;
};

type ModelProviderContract = {
  providerId: string;
  providerName: string;
  status: string;
};

type ModelConfigContract = {
  modelConfigId: string;
  modelName: string;
  status: string;
  connectivityStatus: string;
};

type ChatSendContract = {
  userMessageId: string;
  sessionId: string;
  applicationId: string;
  assistantMessageId: string;
  answer?: string;
  noAnswerReason?: string;
  filteredCitationCount?: number;
  citations: Array<{
    knowledgeBaseId: string;
    documentId: string;
    title: string;
  }>;
  traceId?: string;
  modelInvocationLogId?: string;
};

type ChatHistoryMessageContract = {
  messageId: string;
  sessionId: string;
  applicationId: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  citationCount: number;
  citations: ChatSendContract['citations'];
  modelInvocationLogId?: string;
};

type PermissionState = {
  name: string;
  checked: boolean;
};

type RolePermissionBaseline = {
  roleId: string;
  permissions: PermissionState[];
};

type TenantNameBaseline = {
  originalName: string;
  temporaryName: string;
};

function errorDetails(error: unknown) {
  if (error instanceof Error) {
    return { name: error.name, message: error.message, stack: error.stack };
  }
  return { name: typeof error, message: String(error) };
}

function nodeErrorCode(error: unknown): string | undefined {
  return typeof error === 'object' && error !== null && 'code' in error
    ? String((error as { code?: unknown }).code)
    : undefined;
}

function requestPath(url: string) {
  return new URL(url).pathname;
}

async function openModelHubVisualSurface(page: Page) {
  const credentials = roleCredentials('platform-admin');
  if (!credentials) throw new Error('Missing deploy platform administrator credentials.');
  await signInThroughUi(page, credentials);
  await navigateThroughProductMenu(page, '模型中心');
  await expect(page.getByRole('heading', { level: 1, name: '模型中心' })).toBeVisible({
    timeout: MUTATION_ACTION_TIMEOUT_MS,
  });

  const providerTable = page.getByRole('table', { name: '模型供应商列表' });
  const activeLocalProvider = providerTable.getByRole('row')
    .filter({ hasText: 'local-deterministic' })
    .filter({ hasText: '已启用' })
    .first();
  await expect(
    activeLocalProvider,
    'deploy provisioning must retain an active local deterministic provider for non-mutating visual checks',
  ).toBeVisible({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  await activeLocalProvider.getByRole('button', { name: /^查看 .+ 的模型配置$/ }).click();
  await expect(page.getByRole('button', { name: '新增模型配置', exact: true })).toBeEnabled({
    timeout: MUTATION_ACTION_TIMEOUT_MS,
  });
}

async function openDialogWithKeyboard(page: Page, triggerName: string, dialogName: string) {
  const trigger = page.getByRole('button', { name: triggerName, exact: true });
  await expect(trigger).toBeEnabled({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  await trigger.focus();
  await expect(trigger).toBeFocused();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: dialogName });
  await expect(dialog).toBeVisible({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  expect(
    await dialog.evaluate((element) => element.contains(document.activeElement)),
    `${dialogName} must receive focus when opened from the keyboard`,
  ).toBe(true);
  return { trigger, dialog };
}

async function expectDialogFitsViewport(page: Page, dialog: Locator) {
  await expectNoFullPageHorizontalOverflow(page);
  await expect.poll(() => dialog.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true);
  const [bounds, viewport] = await Promise.all([dialog.boundingBox(), page.evaluate(() => ({
    width: document.documentElement.clientWidth,
    height: document.documentElement.clientHeight,
  }))]);
  expect(bounds, 'visible dialog must expose viewport bounds').not.toBeNull();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.y).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(viewport.width + 1);
  expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(viewport.height + 1);
}

async function auditOpenDialog(
  page: Page,
  testInfo: TestInfo,
  triggerName: string,
  dialogName: string,
  evidenceName: string,
) {
  const { trigger, dialog } = await openDialogWithKeyboard(page, triggerName, dialogName);
  await expectDialogFitsViewport(page, dialog);
  await page.keyboard.press('Shift+Tab');
  expect(await dialog.evaluate((element) => element.contains(document.activeElement))).toBe(true);
  await page.keyboard.press('Tab');
  expect(await dialog.evaluate((element) => element.contains(document.activeElement))).toBe(true);

  const cancel = dialog.getByRole('button', { name: '取消', exact: true });
  await cancel.scrollIntoViewIfNeeded();
  await expect(cancel).toBeVisible();
  await cancel.focus();
  await expect(cancel).toBeFocused();
  await expectNoWcagAaViolations(page);
  await testInfo.attach(evidenceName, {
    body: await page.screenshot({ fullPage: true }),
    contentType: 'image/png',
  });

  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  await expect(trigger).toBeFocused();
}

async function auditModelHubDialogPair(page: Page, testInfo: TestInfo, evidencePrefix: string) {
  await auditOpenDialog(
    page,
    testInfo,
    '新增供应商',
    '新增模型供应商',
    `${evidencePrefix}-provider-dialog`,
  );
  await auditOpenDialog(
    page,
    testInfo,
    '新增模型配置',
    '新增模型配置',
    `${evidencePrefix}-model-dialog`,
  );
}

async function expectThemePreferences(
  page: Page,
  theme: (typeof modelHubThemeMatrix)[number],
) {
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
}

async function apiSuccess<T>(response: APIResponse, description: string): Promise<T> {
  const raw = await response.text();
  let envelope: ApiEnvelope<T>;
  try {
    envelope = JSON.parse(raw) as ApiEnvelope<T>;
  } catch {
    throw new Error(`${description} returned non-JSON HTTP ${response.status()}: ${raw.slice(0, 500)}`);
  }
  if (!response.ok() || envelope.header?.code !== 'SUCCESS') {
    throw new Error(
      `${description} failed: HTTP ${response.status()}, code=${envelope.header?.code || 'missing'}, `
      + `message=${envelope.header?.message || 'missing'}, trace=${envelope.header?.traceId || 'missing'}`,
    );
  }
  return envelope.body;
}

async function browserApiSuccess<T>(
  response: Response,
  description: string,
  forbiddenSensitiveValue?: string,
): Promise<ApiEnvelope<T>> {
  const raw = await response.text();
  if (forbiddenSensitiveValue && raw.includes(forbiddenSensitiveValue)) {
    throw new Error(`${description} response exposed forbidden credential material.`);
  }
  let envelope: ApiEnvelope<T>;
  try {
    envelope = JSON.parse(raw) as ApiEnvelope<T>;
  } catch {
    throw new Error(`${description} returned non-JSON HTTP ${response.status()}: ${raw.slice(0, 500)}`);
  }
  if (!response.ok() || envelope.header?.code !== 'SUCCESS') {
    throw new Error(
      `${description} failed: HTTP ${response.status()}, code=${envelope.header?.code || 'missing'}, `
      + `message=${envelope.header?.message || 'missing'}, trace=${envelope.header?.traceId || 'missing'}`,
    );
  }
  return envelope;
}

function authorization(token: string) {
  return { Authorization: `Bearer ${token}` };
}

async function loginForApi(request: APIRequestContext, role: AcceptanceRole): Promise<ApiIdentity> {
  const credentials = roleCredentials(role);
  if (!credentials) throw new Error(`Missing deploy credentials for ${role}.`);
  return apiSuccess<ApiIdentity>(await request.post('/api/v1/auth/login', {
    data: {
      tenantCode: credentials.tenantCode,
      username: credentials.username,
      password: credentials.password,
    },
  }), `${role} API login`);
}

async function apiGet<T>(request: APIRequestContext, path: string, token: string, description: string) {
  return apiSuccess<T>(await request.get(path, { headers: authorization(token) }), description);
}

async function switchUiCredentials(page: Page, credentials: DeployCredentials) {
  const signOut = page.getByRole('button', { name: '退出登录' });
  if (await signOut.isVisible()) {
    await signOut.click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    await expect(page).toHaveURL(/\/login(?:\?|$)/, { timeout: MUTATION_ACTION_TIMEOUT_MS });
  }
  await signInThroughUi(page, credentials);
}

async function switchUiRole(page: Page, role: AcceptanceRole) {
  const credentials = roleCredentials(role);
  if (!credentials) throw new Error(`Missing deploy credentials for ${role}.`);
  await switchUiCredentials(page, credentials);
}

async function switchToCandidateApprover(page: Page) {
  const platformCredentials = roleCredentials('platform-admin');
  if (!platformCredentials) throw new Error('Missing deploy credentials for candidate platform approver.');
  await switchUiCredentials(page, {
    ...platformCredentials,
    username: DEPLOY_CANDIDATE_APPROVER_USERNAME,
  });
}

function assertRoleJourneyFixtureConfigured() {
  if (process.env.E2E_REQUIRE_ROLE_JOURNEYS !== 'true') {
    throw new Error('D-09 role journeys require the candidate-only deploy fixture.');
  }
  if (!/^[a-z0-9][a-z0-9_-]{5,31}$/.test(DEPLOY_ROLE_JOURNEY_RUN_ID)) {
    throw new Error(`Invalid D-09 role journey RUN_ID: ${DEPLOY_ROLE_JOURNEY_RUN_ID}`);
  }
}

async function acquireMutationLease(
  testInfo: TestInfo,
  resource: MutationLease['resource'],
): Promise<MutationLease> {
  if (testInfo.config.workers !== 1) {
    throw new Error(
      `Stateful deploy acceptance must run with workers=1; received workers=${testInfo.config.workers}.`,
    );
  }
  const lockDirectory = join(testInfo.project.outputDir, '.stateful-mutation-locks');
  await mkdir(lockDirectory, { recursive: true });
  const lockPath = join(lockDirectory, `${resource}.json`);
  let handle: FileHandle;
  try {
    handle = await open(lockPath, 'wx');
  } catch (error) {
    if (nodeErrorCode(error) !== 'EEXIST') throw error;
    let previousEvidence = 'existing recovery lock could not be read';
    try {
      previousEvidence = await readFile(lockPath, 'utf8');
    } catch (readError) {
      previousEvidence = JSON.stringify({ readError: errorDetails(readError) });
    }
    await testInfo.attach(`${resource}-blocked-retry.json`, {
      body: previousEvidence,
      contentType: 'application/json',
    });
    throw new Error(
      `Fail-closed: ${resource} is protected by an existing mutation/recovery lock on retry ${testInfo.retry}. `
      + 'Inspect the blocked-retry attachment and restore the recorded baseline before rerunning.',
      { cause: error },
    );
  }
  const lease: MutationLease = { handle, lockPath, resource };
  await updateMutationLease(lease, {
    status: 'acquired',
    resource,
    retry: testInfo.retry,
    parallelIndex: testInfo.parallelIndex,
    title: testInfo.title,
    acquiredAt: new Date().toISOString(),
  });
  return lease;
}

async function updateMutationLease(lease: MutationLease, evidence: unknown) {
  await writeFile(lease.lockPath, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');
}

async function releaseMutationLease(lease: MutationLease) {
  await lease.handle.close();
  await unlink(lease.lockPath);
}

async function quarantineMutationLease(lease: MutationLease, evidence: unknown) {
  const quarantineErrors: unknown[] = [];
  try {
    await lease.handle.close();
  } catch (error) {
    quarantineErrors.push(error);
  }
  try {
    await writeFile(lease.lockPath, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');
  } catch (error) {
    quarantineErrors.push(error);
  }
  if (quarantineErrors.length) {
    throw new AggregateError(quarantineErrors, `Failed to persist the ${lease.resource} recovery quarantine.`);
  }
}

async function attachRecoveryFailure(
  page: Page,
  testInfo: TestInfo,
  evidence: Record<string, unknown>,
) {
  let screenshotError: unknown;
  if (!page.isClosed()) {
    try {
      await testInfo.attach('recovery-failure.png', {
        body: await page.screenshot({ fullPage: true, timeout: 5_000 }),
        contentType: 'image/png',
      });
    } catch (error) {
      screenshotError = error;
    }
  }
  await testInfo.attach('recovery-failure.json', {
    body: JSON.stringify({ ...evidence, screenshotError: screenshotError && errorDetails(screenshotError) }, null, 2),
    contentType: 'application/json',
  });
}

async function failClosedAfterRecoveryError(
  page: Page,
  testInfo: TestInfo,
  lease: MutationLease,
  baseline: unknown,
  primaryError: unknown,
  recoveryError: unknown,
): Promise<never> {
  const evidence = {
    status: 'recovery-failed',
    resource: lease.resource,
    retry: testInfo.retry,
    title: testInfo.title,
    baseline,
    primaryError: primaryError && errorDetails(primaryError),
    recoveryError: errorDetails(recoveryError),
    failedAt: new Date().toISOString(),
  };
  const diagnosticErrors: unknown[] = [];
  try {
    await quarantineMutationLease(lease, evidence);
  } catch (error) {
    diagnosticErrors.push(error);
  }
  try {
    await attachRecoveryFailure(page, testInfo, evidence);
  } catch (error) {
    diagnosticErrors.push(error);
  }
  const completeRecoveryError = diagnosticErrors.length
    ? new AggregateError([recoveryError, ...diagnosticErrors], 'Recovery and diagnostic persistence both failed.')
    : recoveryError;
  if (primaryError) {
    throw new AggregateError(
      [primaryError, completeRecoveryError],
      `${lease.resource} primary operation failed and its original state could not be restored.`,
    );
  }
  throw new Error(`${lease.resource} original state could not be restored.`, { cause: completeRecoveryError });
}

async function runCandidateMutation<T>(
  page: Page,
  testInfo: TestInfo,
  resource: MutationLease['resource'],
  action: (lease: MutationLease) => Promise<T>,
): Promise<T> {
  const lease = await acquireMutationLease(testInfo, resource);
  await updateMutationLease(lease, {
    status: 'candidate-mutation-started',
    resource,
    runId: DEPLOY_ROLE_JOURNEY_RUN_ID,
    retry: testInfo.retry,
    startedAt: new Date().toISOString(),
  });
  try {
    const result = await action(lease);
    await releaseMutationLease(lease);
    return result;
  } catch (primaryError) {
    const evidence = {
      status: 'candidate-mutation-failed',
      resource,
      runId: DEPLOY_ROLE_JOURNEY_RUN_ID,
      retry: testInfo.retry,
      primaryError: errorDetails(primaryError),
      failedAt: new Date().toISOString(),
    };
    const diagnosticErrors: unknown[] = [];
    try {
      await quarantineMutationLease(lease, evidence);
    } catch (error) {
      diagnosticErrors.push(error);
    }
    try {
      await attachRecoveryFailure(page, testInfo, evidence);
    } catch (error) {
      diagnosticErrors.push(error);
    }
    if (diagnosticErrors.length) {
      throw new AggregateError(
        [primaryError, ...diagnosticErrors],
        `${resource} failed and its fail-closed retry evidence could not be fully persisted.`,
      );
    }
    throw primaryError;
  }
}

async function tenantMutationSurface(page: Page) {
  await expect(page.getByRole('heading', { level: 1, name: '租户管理' })).toBeVisible({
    timeout: MUTATION_ACTION_TIMEOUT_MS,
  });
  const table = page.getByRole('table', { name: '租户列表' });
  await expect(table).toBeVisible({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  return table;
}

async function restoreTenantName(page: Page, baseline: TenantNameBaseline) {
  await page.reload({ waitUntil: 'domcontentloaded', timeout: MUTATION_ACTION_TIMEOUT_MS });
  const tenantTable = await tenantMutationSurface(page);
  const original = tenantTable.getByRole('button', {
    name: `修改 ${baseline.originalName}`,
    exact: true,
  });
  const temporary = tenantTable.getByRole('button', {
    name: `修改 ${baseline.temporaryName}`,
    exact: true,
  });
  const [originalCount, temporaryCount] = await Promise.all([original.count(), temporary.count()]);
  if (originalCount === 1 && temporaryCount === 0) return;
  if (originalCount !== 0 || temporaryCount !== 1) {
    const visibleLabels = await tenantTable.getByRole('button', { name: /^修改 / }).evaluateAll((buttons) => (
      buttons.map((button) => button.getAttribute('aria-label') || button.textContent?.trim() || '')
    ));
    throw new Error(
      `Tenant recovery target is ambiguous: original=${originalCount}, temporary=${temporaryCount}, `
      + `visible=${JSON.stringify(visibleLabels)}.`,
    );
  }
  await temporary.click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  const dialog = page.getByRole('dialog', { name: '修改租户' });
  await expect(dialog).toBeVisible({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  await dialog.getByLabel('租户名称').fill(baseline.originalName, { timeout: MUTATION_ACTION_TIMEOUT_MS });
  await dialog.getByRole('button', { name: '确认' }).click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  await expect(dialog).toBeHidden({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  await expect(original).toHaveCount(1, { timeout: MUTATION_ACTION_TIMEOUT_MS });
  await expect(temporary).toHaveCount(0, { timeout: MUTATION_ACTION_TIMEOUT_MS });
}

async function permissionStates(page: Page): Promise<PermissionState[]> {
  const checkboxes = page.getByRole('checkbox');
  const count = await checkboxes.count();
  if (!count) throw new Error('No tenant-assignable permissions are available for recovery.');
  const states: PermissionState[] = [];
  for (let index = 0; index < count; index += 1) {
    const checkbox = checkboxes.nth(index);
    await expect(checkbox).toBeEnabled({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    const name = await checkbox.evaluate((element) => {
      const input = element as HTMLInputElement;
      return input.getAttribute('aria-label')?.trim()
        || Array.from(input.labels || []).map((label) => label.textContent?.trim() || '').find(Boolean)
        || '';
    });
    if (!name) throw new Error(`Permission checkbox ${index} has no stable accessible name.`);
    states.push({ name, checked: await checkbox.isChecked() });
  }
  if (new Set(states.map((state) => state.name)).size !== states.length) {
    throw new Error(`Permission accessible names are not unique: ${JSON.stringify(states.map((state) => state.name))}.`);
  }
  return states;
}

async function selectRoleAndReadPermissions(page: Page, roleId: string): Promise<PermissionState[]> {
  const roleSelector = page.getByLabel('当前角色');
  await expect(roleSelector).not.toHaveValue('', { timeout: MUTATION_ACTION_TIMEOUT_MS });
  await roleSelector.selectOption(roleId, { timeout: MUTATION_ACTION_TIMEOUT_MS });
  await expect(roleSelector).toHaveValue(roleId, { timeout: MUTATION_ACTION_TIMEOUT_MS });
  return permissionStates(page);
}

function assertPermissionShape(actual: PermissionState[], expected: PermissionState[]) {
  expect(actual.map(({ name }) => name), 'permission catalog changed while restoring the saved role').toEqual(
    expected.map(({ name }) => name),
  );
}

async function restoreRolePermissions(page: Page, baseline: RolePermissionBaseline) {
  await page.reload({ waitUntil: 'domcontentloaded', timeout: MUTATION_ACTION_TIMEOUT_MS });
  await expect(page.getByRole('heading', { level: 1, name: '角色权限' })).toBeVisible({
    timeout: MUTATION_ACTION_TIMEOUT_MS,
  });
  const current = await selectRoleAndReadPermissions(page, baseline.roleId);
  assertPermissionShape(current, baseline.permissions);
  let changed = false;
  for (let index = 0; index < current.length; index += 1) {
    if (current[index].checked !== baseline.permissions[index].checked) {
      await page.getByRole('checkbox', { name: baseline.permissions[index].name, exact: true })
        .click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
      changed = true;
    }
  }
  if (changed) {
    await page.getByRole('button', { name: '保存权限' }).click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    await confirmRolePermissionReplacement(page, 'deploy E2E finally 恢复权限矩阵原始状态');
    await expect(page.getByText('权限矩阵已保存。', { exact: true })).toBeVisible({
      timeout: MUTATION_ACTION_TIMEOUT_MS,
    });
  }
  await page.reload({ waitUntil: 'domcontentloaded', timeout: MUTATION_ACTION_TIMEOUT_MS });
  await expect(page.getByRole('heading', { level: 1, name: '角色权限' })).toBeVisible({
    timeout: MUTATION_ACTION_TIMEOUT_MS,
  });
  const verified = await selectRoleAndReadPermissions(page, baseline.roleId);
  expect(verified, 'role permission recovery must reproduce the complete original matrix').toEqual(
    baseline.permissions,
  );
}

const roles: Array<{
  id: AcceptanceRole;
  label: string;
  route: RegExp;
  heading: RegExp;
}> = [
  { id: 'platform-admin', label: '平台管理员', route: /\/admin\/platform-overview$/, heading: /^平台任务首页$/ },
  { id: 'tenant-admin', label: '租户管理员', route: /\/tenant\/overview$/, heading: /^租户任务首页$/ },
  { id: 'business-user', label: '业务用户', route: /\/workspace\/home$/, heading: /^你好，/ },
];

async function confirmRolePermissionReplacement(page: Page, reason: string) {
  const dialog = page.getByRole('dialog', { name: '确认替换角色权限' });
  await expect(dialog).toBeVisible({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  await expect(dialog.getByLabel('权限变更差异')).toBeVisible({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  await expect(dialog.getByText(
    '完整替换会移除所有未选中的权限，并立即影响该角色下用户的可访问范围。',
    { exact: true },
  )).toBeVisible({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  const confirm = dialog.getByRole('button', { name: '确认替换权限' });
  await expect(confirm, '权限完整替换必须在填写审计原因前 fail-closed').toBeDisabled({
    timeout: MUTATION_ACTION_TIMEOUT_MS,
  });
  await dialog.getByLabel('变更原因').fill(reason, { timeout: MUTATION_ACTION_TIMEOUT_MS });
  await expect(confirm).toBeEnabled({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  await confirm.click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  await expect(dialog).toBeHidden({ timeout: MUTATION_ACTION_TIMEOUT_MS });
}

async function approveModelHubResourceThroughUi(page: Page, resourceId: string, description: string) {
  await switchToCandidateApprover(page);
  await navigateThroughProductMenu(page, '审批中心');
  await expect(page.getByRole('heading', { level: 1, name: '审批中心' })).toBeVisible({
    timeout: MUTATION_ACTION_TIMEOUT_MS,
  });
  await page.getByLabel('搜索审批事项或资源编号').fill(resourceId);
  const row = page.getByRole('table', { name: '审批任务列表' })
    .getByRole('row')
    .filter({ hasText: resourceId });
  await expect(row, `${description} must appear as one pending four-eyes task`).toHaveCount(1, {
    timeout: 30_000,
  });
  const approve = row.getByRole('button', { name: /^通过 / });
  const taskLabel = await approve.getAttribute('aria-label');
  if (!taskLabel?.startsWith('通过 ')) throw new Error(`${description} approval task has no stable accessible id.`);
  const taskId = taskLabel.slice('通过 '.length);
  await approve.click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
  const approvalPanel = page.locator('.approval-panel');
  await expect(approvalPanel.getByRole('heading', { name: '确认通过' })).toBeVisible({
    timeout: MUTATION_ACTION_TIMEOUT_MS,
  });
  await approvalPanel.getByLabel('审批原因').fill(`D-09 ${description} 异人审批`, {
    timeout: MUTATION_ACTION_TIMEOUT_MS,
  });
  const decisionResponse = page.waitForResponse((response) => (
    response.request().method() === 'POST'
      && requestPath(response.url()) === `/api/v1/approvals/tasks/${taskId}/actions`
  ));
  await approvalPanel.getByRole('button', { name: '确认通过' }).click({
    timeout: MUTATION_ACTION_TIMEOUT_MS,
  });
  const decision = await browserApiSuccess<{ status: string; sourceResourceId: string }>(
    await decisionResponse,
    `${description} browser approval`,
  );
  expect(decision.body).toMatchObject({ status: 'APPROVED', sourceResourceId: resourceId });
  await switchUiRole(page, 'platform-admin');
  return { taskId, traceId: decision.header.traceId };
}

async function completePlatformModelLifecycle(
  page: Page,
  request: APIRequestContext,
  testInfo: TestInfo,
) {
  assertRoleJourneyFixtureConfigured();
  const modelProbeEndpoint = requiredModelProbeEndpoint();
  await runCandidateMutation(page, testInfo, 'platform-model-lifecycle', async (lease) => {
    const admin = await loginForApi(request, 'platform-admin');
    const providerName = `D09 供应商 ${DEPLOY_ROLE_JOURNEY_RUN_ID}`;
    const credentialProviderName = `D09 凭证供应商 ${DEPLOY_ROLE_JOURNEY_RUN_ID}`;
    const modelName = `D09 对话模型 ${DEPLOY_ROLE_JOURNEY_RUN_ID}`;
    const modelCode = `d09-chat-${DEPLOY_ROLE_JOURNEY_RUN_ID}`;
    let secret = await page.evaluate(() => `d09-${crypto.randomUUID()}-${crypto.randomUUID()}`);

    await navigateThroughProductMenu(page, '模型中心');
    await expect(page.getByRole('heading', { level: 1, name: '模型中心' })).toBeVisible({
      timeout: MUTATION_ACTION_TIMEOUT_MS,
    });
    await page.getByRole('button', { name: '新增供应商' }).click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    const providerDialog = page.getByRole('dialog', { name: '新增模型供应商' });
    await expect(providerDialog).toBeVisible({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    await providerDialog.getByLabel('供应商名称').fill(credentialProviderName);
    await providerDialog.getByLabel('供应商类型').selectOption('mistral-ai');
    await providerDialog.getByLabel('API 地址').fill(modelProbeEndpoint);
    await expect(providerDialog.getByLabel('认证方式')).toHaveValue('api_key');
    await expect(providerDialog.getByLabel('认证方式')).toBeDisabled();
    await providerDialog.getByLabel('访问凭证').fill(secret);
    const credentialProviderResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST' && requestPath(response.url()) === '/api/v1/model-providers'
    ));
    await providerDialog.getByRole('button', { name: '保存' }).click();
    const credentialProviderCreated = await browserApiSuccess<{ providerId: string; status: string }>(
      await credentialProviderResponse,
      'credential-backed provider creation',
      secret,
    );
    const credentialProviderId = credentialProviderCreated.body.providerId;
    await updateMutationLease(lease, {
      status: 'credential-provider-created',
      resource: lease.resource,
      runId: DEPLOY_ROLE_JOURNEY_RUN_ID,
      credentialProviderId,
      retry: testInfo.retry,
    });
    await expect(providerDialog).toBeHidden({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    expect(
      (await page.locator('body').innerText()).includes(secret),
      'secret zero-echo: provider UI must not retain credential material',
    ).toBe(false);
    const providerApiBeforeApproval = await apiGet<ModelProviderContract[]>(
      request,
      '/api/v1/model-providers',
      admin.token,
      'provider zero-echo API proof',
    );
    expect(
      JSON.stringify(providerApiBeforeApproval).includes(secret),
      'secret zero-echo: provider API must never return credential material',
    ).toBe(false);
    await page.getByRole('button', { name: '新增供应商' }).click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    await expect(providerDialog).toBeVisible({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    const reopenedCredentialIsEmpty = await providerDialog.locator('input[type="password"]')
      .evaluateAll((inputs) => inputs.length === 1 && inputs.every((input) => (
        input instanceof HTMLInputElement && input.value === ''
      )));
    expect(reopenedCredentialIsEmpty, 'provider credential must be cleared before the dialog is reopened').toBe(true);
    await providerDialog.getByRole('button', { name: '取消' }).click();
    await expect(providerDialog).toBeHidden({ timeout: MUTATION_ACTION_TIMEOUT_MS });

    const credentialProviderApproval = await approveModelHubResourceThroughUi(
      page,
      credentialProviderId,
      '凭证模型供应商',
    );
    await navigateThroughProductMenu(page, '模型中心');
    const credentialProviderRow = page.getByRole('table', { name: '模型供应商列表' })
      .getByRole('row')
      .filter({ hasText: credentialProviderName });
    await expect(credentialProviderRow).toHaveCount(1, { timeout: 30_000 });
    await expect(credentialProviderRow).toContainText('已启用', { timeout: 30_000 });

    await page.getByRole('button', { name: '新增供应商' }).click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    await expect(providerDialog).toBeVisible({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    await providerDialog.getByLabel('供应商名称').fill(providerName);
    await providerDialog.getByLabel('供应商类型').selectOption('local-deterministic');
    await expect(providerDialog.getByLabel('API 地址')).toHaveValue('local://deterministic');
    await expect(providerDialog.getByLabel('API 地址')).toHaveAttribute('readonly');
    await expect(providerDialog.getByLabel('认证方式')).toHaveValue('no_auth');
    await expect(providerDialog.getByLabel('认证方式')).toBeDisabled();
    await expect(providerDialog.getByLabel('访问凭证')).toHaveCount(0);
    const providerResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST' && requestPath(response.url()) === '/api/v1/model-providers'
    ));
    await providerDialog.getByRole('button', { name: '保存' }).click();
    const providerCreated = await browserApiSuccess<{ providerId: string; status: string }>(
      await providerResponse,
      'local deterministic provider creation',
    );
    const providerId = providerCreated.body.providerId;
    await updateMutationLease(lease, {
      status: 'local-provider-created',
      resource: lease.resource,
      runId: DEPLOY_ROLE_JOURNEY_RUN_ID,
      providerId,
      credentialProviderId,
      retry: testInfo.retry,
    });
    await expect(providerDialog).toBeHidden({ timeout: MUTATION_ACTION_TIMEOUT_MS });

    const providerApproval = await approveModelHubResourceThroughUi(page, providerId, '本地模型供应商');
    await navigateThroughProductMenu(page, '模型中心');
    const providerRow = page.getByRole('table', { name: '模型供应商列表' })
      .getByRole('row')
      .filter({ hasText: providerName });
    await expect(providerRow).toHaveCount(1, { timeout: 30_000 });
    await expect(providerRow).toContainText('已启用', { timeout: 30_000 });
    await providerRow.getByRole('button', { name: `查看 ${providerName} 的模型配置` }).click();
    await page.getByRole('button', { name: '新增模型配置' }).click();
    const modelDialog = page.getByRole('dialog', { name: '新增模型配置' });
    await modelDialog.getByLabel('模型名称').fill(modelName);
    await modelDialog.getByLabel('模型编码').fill(modelCode);
    await modelDialog.getByLabel('模型类型').selectOption('CHAT');
    await modelDialog.getByLabel('上下文窗口').fill('8192');
    await modelDialog.getByLabel('最大输出 Token').fill('512');
    await modelDialog.getByLabel('输入价格（每千 Token）').fill('0');
    await modelDialog.getByLabel('输出价格（每千 Token）').fill('0');
    const modelResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST'
      && requestPath(response.url()) === `/api/v1/model-providers/${providerId}/models`
    ));
    await modelDialog.getByRole('button', { name: '保存' }).click();
    const modelCreated = await browserApiSuccess<{ modelConfigId: string; status: string }>(
      await modelResponse,
      'disposable model creation',
    );
    const modelConfigId = modelCreated.body.modelConfigId;
    const modelApproval = await approveModelHubResourceThroughUi(page, modelConfigId, '模型配置');

    await navigateThroughProductMenu(page, '模型中心');
    const refreshedProviderRow = page.getByRole('table', { name: '模型供应商列表' })
      .getByRole('row')
      .filter({ hasText: providerName });
    await refreshedProviderRow.getByRole('button', { name: `查看 ${providerName} 的模型配置` }).click();
    const modelCard = page.getByRole('article', { name: `模型配置 ${modelName}` });
    await expect(modelCard).toBeVisible({ timeout: 30_000 });
    const connectivityButton = modelCard.getByRole('button', { name: `测试 ${modelName} 连通性` });
    await expect(modelCard).toContainText('待启用', { timeout: 30_000 });
    await expect(connectivityButton).toBeEnabled({ timeout: 30_000 });

    const connectivityPath = `/api/v1/model-providers/${providerId}/models/${modelConfigId}/connectivity-test`;
    let failConnectivityOnce = true;
    await page.route(`**${connectivityPath}`, async (route) => {
      if (!failConnectivityOnce) {
        await route.continue();
        return;
      }
      failConnectivityOnce = false;
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          header: {
            code: 'AIP-D09-CONNECTIVITY-TRANSIENT',
            message: 'D-09 注入的瞬时连通失败，请明确重试。',
            traceId: `d09-connectivity-${DEPLOY_ROLE_JOURNEY_RUN_ID}`,
          },
          body: null,
        }),
      });
    });
    await connectivityButton.click();
    await expect(page.getByText('D-09 注入的瞬时连通失败，请明确重试。', { exact: true })).toBeVisible({
      timeout: MUTATION_ACTION_TIMEOUT_MS,
    });
    const connectivityResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST' && requestPath(response.url()) === connectivityPath
    ));
    await connectivityButton.click();
    await browserApiSuccess<Record<string, unknown>>(await connectivityResponse, 'connectivity recovery retry');
    await expect(modelCard).toContainText('连通性：已通过', { timeout: 30_000 });
    await page.unroute(`**${connectivityPath}`);

    await modelCard.getByRole('button', { name: `停用 ${modelName}` }).click();
    const modelDisableDialog = page.getByRole('dialog', { name: '停用模型配置' });
    await modelDisableDialog.getByLabel('停用理由').fill('D-09 disposable model lifecycle completed');
    const modelDisableResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST'
      && requestPath(response.url()) === `/api/v1/model-providers/${providerId}/models/${modelConfigId}/disable`
    ));
    await modelDisableDialog.getByRole('button', { name: '确认停用' }).click();
    const modelDisabled = await browserApiSuccess<Record<string, unknown>>(
      await modelDisableResponse,
      'model disable',
    );

    const activeProviderRow = page.getByRole('table', { name: '模型供应商列表' })
      .getByRole('row')
      .filter({ hasText: providerName });
    await activeProviderRow.getByRole('button', { name: `停用 ${providerName}` }).click();
    const providerDisableDialog = page.getByRole('dialog', { name: '停用模型供应商' });
    await providerDisableDialog.getByLabel('停用理由').fill('D-09 disposable provider lifecycle completed');
    const providerDisableResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST'
      && requestPath(response.url()) === `/api/v1/model-providers/${providerId}/disable`
    ));
    await providerDisableDialog.getByRole('button', { name: '确认停用' }).click();
    const providerDisabled = await browserApiSuccess<Record<string, unknown>>(
      await providerDisableResponse,
      'provider disable',
    );

    const activeCredentialProviderRow = page.getByRole('table', { name: '模型供应商列表' })
      .getByRole('row')
      .filter({ hasText: credentialProviderName });
    await expect(activeCredentialProviderRow).toContainText('已启用', { timeout: 30_000 });
    await activeCredentialProviderRow.getByRole('button', { name: `停用 ${credentialProviderName}` }).click();
    const credentialProviderDisableDialog = page.getByRole('dialog', { name: '停用模型供应商' });
    await credentialProviderDisableDialog.getByLabel('停用理由')
      .fill('D-09 credential governance proof completed');
    const credentialProviderDisableResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST'
      && requestPath(response.url()) === `/api/v1/model-providers/${credentialProviderId}/disable`
    ));
    await credentialProviderDisableDialog.getByRole('button', { name: '确认停用' }).click();
    const credentialProviderDisabled = await browserApiSuccess<Record<string, unknown>>(
      await credentialProviderDisableResponse,
      'credential provider disable',
    );

    const providersAfterDisable = await apiGet<ModelProviderContract[]>(
      request,
      '/api/v1/model-providers',
      admin.token,
      'provider terminal API proof',
    );
    expect(providersAfterDisable.find((provider) => provider.providerId === providerId))
      .toMatchObject({ providerName, status: 'DISABLED' });
    expect(providersAfterDisable.find((provider) => provider.providerId === credentialProviderId))
      .toMatchObject({ providerName: credentialProviderName, status: 'DISABLED' });
    const modelsAfterDisable = await apiGet<ModelConfigContract[]>(
      request,
      `/api/v1/model-providers/${providerId}/models`,
      admin.token,
      'model terminal API proof',
    );
    expect(modelsAfterDisable.find((model) => model.modelConfigId === modelConfigId)).toMatchObject({
      modelName,
      connectivityStatus: 'PASSED',
    });
    expect(
      JSON.stringify({ providersAfterDisable, modelsAfterDisable }).includes(secret),
      'secret zero-echo in terminal API proof',
    ).toBe(false);
    secret = '';

    const auditTraceId = providerDisabled.header.traceId;
    if (!auditTraceId) throw new Error('Provider disable response must expose an audit trace id.');
    await navigateThroughProductMenu(page, '安全审计');
    await page.getByLabel('追踪编号').fill(auditTraceId);
    await page.getByRole('button', { name: '查询' }).click();
    const auditTable = page.getByRole('table', { name: '安全审计列表' });
    await expect(auditTable.getByRole('row').filter({ hasText: providerId })).toHaveCount(1, {
      timeout: 30_000,
    });

    await testInfo.attach('d09-platform-model-lifecycle.json', {
      body: JSON.stringify({
        runId: DEPLOY_ROLE_JOURNEY_RUN_ID,
        providerId,
        credentialProviderId,
        modelConfigId,
        providerApproval,
        credentialProviderApproval,
        modelApproval,
        modelDisableTraceId: modelDisabled.header.traceId,
        providerDisableTraceId: auditTraceId,
        credentialProviderDisableTraceId: credentialProviderDisabled.header.traceId,
        secretZeroEcho: true,
      }, null, 2),
      contentType: 'application/json',
    });
  });
}

async function completeTenantGovernanceJourney(
  page: Page,
  request: APIRequestContext,
  testInfo: TestInfo,
) {
  assertRoleJourneyFixtureConfigured();
  await runCandidateMutation(page, testInfo, 'tenant-candidate-governance', async (lease) => {
    const tenantAdmin = await loginForApi(request, 'tenant-admin');
    const rolesForTenant = (await apiGet<IamRoleContract[]>(
      request,
      '/api/v1/iam/roles',
      tenantAdmin.token,
      'tenant role catalog',
    )).filter((role) => role.type.toLowerCase() === 'business');
    const candidateRole = rolesForTenant.find((role) => role.name === DEPLOY_CANDIDATE_ROLE_NAME);
    if (!candidateRole) throw new Error(`Candidate role is missing: ${DEPLOY_CANDIDATE_ROLE_NAME}`);
    if (rolesForTenant.length < 2) throw new Error('Stale-response acceptance requires two tenant-manageable roles.');

    await navigateThroughProductMenu(page, '用户管理');
    await expect(page.getByRole('heading', { level: 1, name: '用户管理' })).toBeVisible({
      timeout: MUTATION_ACTION_TIMEOUT_MS,
    });
    await page.getByRole('button', { name: '邀请用户' }).click();
    const invitationDialog = page.getByRole('dialog', { name: '邀请用户' });
    const invitationUsername = `d09-${DEPLOY_ROLE_JOURNEY_RUN_ID}`;
    await invitationDialog.getByLabel('姓名').fill(`D09 验收用户 ${DEPLOY_ROLE_JOURNEY_RUN_ID}`);
    await invitationDialog.getByLabel('登录账号').fill(invitationUsername);
    await invitationDialog.getByLabel('邮箱').fill(DEPLOY_CANDIDATE_INVITATION_EMAIL);
    await invitationDialog.getByLabel('初始角色').selectOption({ label: DEPLOY_CANDIDATE_ROLE_NAME });
    const invitationResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST'
      && requestPath(response.url()) === '/api/v1/iam/user-invitations'
    ));
    await invitationDialog.getByRole('button', { name: '发送邀请' }).click();
    const invitationCreated = await browserApiSuccess<{
      invitationId: string;
      status: string;
      deliveryStatus: string;
    }>(await invitationResponse, 'candidate invitation creation');
    await expect(invitationDialog).toBeHidden({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    const invitationRow = page.getByRole('table', { name: '用户邀请记录' })
      .getByRole('row')
      .filter({ hasText: DEPLOY_CANDIDATE_INVITATION_EMAIL });
    await expect(invitationRow).toHaveCount(1, { timeout: 30_000 });
    await invitationRow.getByRole('button', { name: `撤销 ${DEPLOY_CANDIDATE_INVITATION_EMAIL}` }).click();
    const revokeDialog = page.getByRole('dialog', { name: '撤销用户邀请' });
    await expect(revokeDialog).toContainText(invitationCreated.body.invitationId);
    const revokeConfirm = revokeDialog.getByRole('button', { name: '确认撤销' });
    await expect(revokeConfirm, 'invitation revocation must require an audit reason').toBeDisabled();
    await revokeDialog.getByLabel('撤销原因').fill('D-09 candidate invitation cleanup');
    const revokePath = `/api/v1/iam/user-invitations/${invitationCreated.body.invitationId}/revoke`;
    const revokeResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST' && requestPath(response.url()) === revokePath
    ));
    await revokeConfirm.click();
    await browserApiSuccess<Record<string, unknown>>(await revokeResponse, 'candidate invitation revocation');
    await expect(revokeDialog).toBeHidden({ timeout: MUTATION_ACTION_TIMEOUT_MS });
    const invitationsAfterRevoke = await apiGet<UserInvitationContract[]>(
      request,
      '/api/v1/iam/user-invitations',
      tenantAdmin.token,
      'revoked invitation API proof',
    );
    expect(invitationsAfterRevoke.filter((item) => item.email === DEPLOY_CANDIDATE_INVITATION_EMAIL))
      .toEqual([expect.objectContaining({
        invitationId: invitationCreated.body.invitationId,
        status: 'REVOKED',
      })]);

    const initialRole = rolesForTenant[0];
    const switchedRole = rolesForTenant.find((role) => role.roleId !== initialRole.roleId);
    if (!switchedRole) throw new Error('No second tenant-manageable role is available.');
    const switchedRolePermissions = await apiGet<IamPermissionContract[]>(
      request,
      `/api/v1/iam/roles/${switchedRole.roleId}/permissions`,
      tenantAdmin.token,
      'role switch permission baseline',
    );
    let releaseStaleRoleResponse!: () => void;
    let markStaleRoleStarted!: () => void;
    let markStaleRoleCompleted!: () => void;
    const staleRoleRelease = new Promise<void>((resolve) => { releaseStaleRoleResponse = resolve; });
    const staleRoleStarted = new Promise<void>((resolve) => { markStaleRoleStarted = resolve; });
    const staleRoleCompleted = new Promise<void>((resolve) => { markStaleRoleCompleted = resolve; });
    const staleRolePath = `/api/v1/iam/roles/${initialRole.roleId}/permissions`;
    let staleRoleIntercepted = false;
    await page.route(`**${staleRolePath}`, async (route) => {
      if (staleRoleIntercepted || route.request().method() !== 'GET') {
        await route.continue();
        return;
      }
      staleRoleIntercepted = true;
      const upstream = await route.fetch();
      markStaleRoleStarted();
      await staleRoleRelease;
      await route.fulfill({ response: upstream });
      markStaleRoleCompleted();
    });
    await navigateThroughProductMenu(page, '角色权限');
    await staleRoleStarted;
    const roleSelector = page.getByLabel('当前角色');
    await expect(roleSelector).toHaveValue(initialRole.roleId, { timeout: MUTATION_ACTION_TIMEOUT_MS });
    await roleSelector.selectOption(switchedRole.roleId);
    await expect(roleSelector).toHaveValue(switchedRole.roleId, { timeout: MUTATION_ACTION_TIMEOUT_MS });
    const switchedPermissionState = await permissionStates(page);
    expect(switchedPermissionState.filter((item) => item.checked).map((item) => item.name).sort())
      .toEqual(switchedRolePermissions.map((item) => item.name).sort());
    releaseStaleRoleResponse();
    await staleRoleCompleted;
    await expect(roleSelector, 'late permission response must not switch the selected role back')
      .toHaveValue(switchedRole.roleId);
    expect((await permissionStates(page)).filter((item) => item.checked).map((item) => item.name).sort(),
      'late permission response must not overwrite the current role matrix')
      .toEqual(switchedRolePermissions.map((item) => item.name).sort());
    await page.unroute(`**${staleRolePath}`);

    const permissionCatalog = await apiGet<IamPermissionContract[]>(
      request,
      '/api/v1/iam/permissions',
      tenantAdmin.token,
      'tenant permission catalog',
    );
    const candidateBaseline = await selectRoleAndReadPermissions(page, candidateRole.roleId);
    expect(candidateBaseline.every((permission) => !permission.checked),
      'candidate role must start with an isolated empty permission matrix').toBe(true);
    if (candidateBaseline.length < 2) throw new Error('Candidate role replacement requires at least two permissions.');
    const replacementNames = [candidateBaseline[0].name, candidateBaseline.at(-1)!.name];
    for (const name of replacementNames) {
      await page.getByRole('checkbox', { name, exact: true }).click();
    }
    const replacementCodes = replacementNames.map((name) => {
      const permission = permissionCatalog.find((item) => item.name === name);
      if (!permission) throw new Error(`Permission code is missing for ${name}.`);
      return permission.code;
    }).sort();
    await page.getByRole('button', { name: '保存权限' }).click();
    const permissionDialog = page.getByRole('dialog', { name: '确认替换角色权限' });
    const permissionDiff = permissionDialog.getByLabel('权限变更差异');
    await expect(permissionDiff.getByText('新增权限', { exact: true })).toBeVisible();
    await expect(permissionDiff.getByText('移除权限', { exact: true })).toBeVisible();
    for (const name of replacementNames) await expect(permissionDiff.getByText(name, { exact: true })).toBeVisible();
    await confirmRolePermissionReplacement(page, 'D-09 candidate full permission diff acceptance');
    await expect(page.getByText('权限矩阵已保存。', { exact: true })).toBeVisible({
      timeout: MUTATION_ACTION_TIMEOUT_MS,
    });
    const persistedCandidatePermissions = await apiGet<IamPermissionContract[]>(
      request,
      `/api/v1/iam/roles/${candidateRole.roleId}/permissions`,
      tenantAdmin.token,
      'candidate permission replacement API proof',
    );
    expect(persistedCandidatePermissions.map((item) => item.code).sort()).toEqual(replacementCodes);

    const knowledgeBases = await apiGet<KnowledgeBaseContract[]>(
      request,
      '/api/v1/knowledge-bases',
      tenantAdmin.token,
      'tenant knowledge-base catalog',
    );
    const targetKnowledgeBase = knowledgeBases.find((item) => item.name === DEPLOY_KNOWLEDGE_BASE_NAME);
    if (!targetKnowledgeBase) throw new Error(`Knowledge base is missing: ${DEPLOY_KNOWLEDGE_BASE_NAME}`);
    await navigateThroughProductMenu(page, '知识库治理');
    await page.getByRole('button', { name: `管理文档 ${targetKnowledgeBase.knowledgeBaseId}` }).click();
    const documentDialog = page.getByRole('dialog', { name: '文档维护' });
    const failedDocumentRow = documentDialog.getByRole('table', { name: '知识库文档列表' })
      .getByRole('row')
      .filter({ hasText: DEPLOY_KNOWLEDGE_RECOVERY_FILE_NAME });
    await expect(failedDocumentRow).toHaveCount(1, { timeout: 30_000 });
    await expect(failedDocumentRow).toContainText('失败，需要人工介入');
    await failedDocumentRow.getByRole('button', {
      name: `重试解析 ${DEPLOY_KNOWLEDGE_RECOVERY_FILE_NAME}`,
    }).click();
    const retryDialog = page.getByRole('dialog', { name: '确认重试解析' });
    await expect(retryDialog).toContainText(DEPLOY_KNOWLEDGE_RECOVERY_FILE_NAME);
    await expect(retryDialog).toContainText(/DOCUMENT_PARSE.*FAILED/);
    await expect(retryDialog).toContainText('AIP-KB-D09-PARSE-FAILED');
    await expect(retryDialog).toContainText(`d09-fixture-${DEPLOY_ROLE_JOURNEY_RUN_ID}`);
    const retryPathPattern = new RegExp(
      `/api/v1/knowledge-bases/${targetKnowledgeBase.knowledgeBaseId}/documents/([^/]+)/parse$`,
    );
    const retryResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST' && retryPathPattern.test(requestPath(response.url()))
    ));
    await retryDialog.getByRole('button', { name: '确认重试解析' }).click();
    const retried = await browserApiSuccess<{
      documentId: string;
      ingestJobId: string;
      status: string;
      chunkCount: number;
    }>(await retryResponse, 'FAILED document manual recovery');
    expect(retried.body.status).toBe('PENDING');
    expect(retried.body.ingestJobId).toMatch(/^\d+$/);
    await expect(page.getByText(
      `已将“${DEPLOY_KNOWLEDGE_RECOVERY_FILE_NAME}”重新排队解析。`,
      { exact: true },
    )).toBeVisible({ timeout: 30_000 });

    await updateMutationLease(lease, {
      status: 'tenant-candidate-governance-complete',
      runId: DEPLOY_ROLE_JOURNEY_RUN_ID,
      invitationId: invitationCreated.body.invitationId,
      candidateRoleId: candidateRole.roleId,
      permissionCodes: replacementCodes,
      recoveredDocumentId: retried.body.documentId,
      recoveredJobId: retried.body.ingestJobId,
    });
    await testInfo.attach('d09-tenant-governance.json', {
      body: JSON.stringify({
        runId: DEPLOY_ROLE_JOURNEY_RUN_ID,
        invitationId: invitationCreated.body.invitationId,
        candidateRoleId: candidateRole.roleId,
        permissionCodes: replacementCodes,
        staleResponseIgnored: true,
        recoveredDocumentId: retried.body.documentId,
        recoveredJobId: retried.body.ingestJobId,
      }, null, 2),
      contentType: 'application/json',
    });
  });
}

async function completeBusinessChatJourney(
  page: Page,
  request: APIRequestContext,
  testInfo: TestInfo,
) {
  assertRoleJourneyFixtureConfigured();
  await runCandidateMutation(page, testInfo, 'business-chat-idempotency', async (lease) => {
    const business = await loginForApi(request, 'business-user');
    const knowledgeBases = await apiGet<KnowledgeBaseContract[]>(
      request,
      '/api/v1/knowledge-bases',
      business.token,
      'business authorized knowledge-base catalog',
    );
    const authorizedKnowledgeBase = knowledgeBases.find((item) => item.name === DEPLOY_KNOWLEDGE_BASE_NAME);
    if (!authorizedKnowledgeBase) throw new Error(`Authorized knowledge base is missing: ${DEPLOY_KNOWLEDGE_BASE_NAME}`);

    await navigateThroughProductMenu(page, 'AI应用');
    await expect(page.getByRole('heading', { level: 1, name: 'AI 应用' })).toBeVisible();
    const applicationCard = page.locator('article.application-item').filter({
      has: page.getByRole('heading', { level: 2, name: DEPLOY_APPLICATION_NAME }),
    });
    await applicationCard.getByRole('button', { name: '开始使用' }).click();
    await expect(page).toHaveURL(/\/workspace\/sessions\/[^/?#]+$/);
    const sessionId = new URL(page.url()).pathname.split('/').at(-1);
    if (!sessionId) throw new Error('Chat session route did not expose a session ID.');
    await expect(page.getByRole('heading', { level: 1, name: 'RAG 对话' })).toBeVisible();

    const messagePath = `/api/v1/chat-sessions/${sessionId}/messages`;
    const attempts: Array<{ clientMessageId: string; content: string }> = [];
    let failFirstMessage = true;
    await page.route(`**${messagePath}`, async (route) => {
      if (route.request().method() !== 'POST') {
        await route.continue();
        return;
      }
      const payload = route.request().postDataJSON() as Partial<{ clientMessageId: string; content: string }>;
      if (typeof payload.clientMessageId !== 'string' || typeof payload.content !== 'string') {
        throw new Error('Chat command did not expose clientMessageId and content.');
      }
      attempts.push({ clientMessageId: payload.clientMessageId, content: payload.content });
      if (!failFirstMessage) {
        await route.continue();
        return;
      }
      failFirstMessage = false;
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          header: {
            code: 'AIP-D09-CHAT-TRANSIENT',
            message: 'D-09 注入的瞬时问答失败，请保留原请求重试。',
            traceId: `d09-chat-failure-${DEPLOY_ROLE_JOURNEY_RUN_ID}`,
          },
          body: null,
        }),
      });
    });
    await page.getByLabel('输入问题').fill(DEPLOY_BUSINESS_QUESTION);
    await page.getByRole('button', { name: '发送' }).click();
    await expect(page.getByRole('alert')).toContainText('D-09 注入的瞬时问答失败');
    const recoveredResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST'
      && requestPath(response.url()) === messagePath
      && response.status() === 200
    ));
    await page.getByRole('button', { name: '重试同一请求' }).click();
    const recovered = await browserApiSuccess<ChatSendContract>(await recoveredResponse, 'idempotent chat recovery');
    await page.unroute(`**${messagePath}`);
    expect(attempts).toHaveLength(2);
    expect(attempts[0].clientMessageId).toBeTruthy();
    expect(attempts[1]).toEqual(attempts[0]);
    expect(attempts[0].content).toBe(DEPLOY_BUSINESS_QUESTION);
    expect(recovered.body.sessionId).toBe(sessionId);
    expect(recovered.body.citations.length).toBeGreaterThan(0);
    if (!recovered.body.modelInvocationLogId) throw new Error('Recovered answer has no billable invocation proof.');
    for (const citation of recovered.body.citations) {
      expect(citation.knowledgeBaseId, 'cross-tenant citations must remain zero')
        .toBe(authorizedKnowledgeBase.knowledgeBaseId);
    }
    expect(recovered.body.citations.some((citation) => citation.title === DEPLOY_KNOWLEDGE_EVIDENCE_FILE_NAME),
      'the deterministic candidate evidence document must be cited').toBe(true);

    const userMessages = page.getByRole('article', { name: '你的问题' })
      .filter({ hasText: DEPLOY_BUSINESS_QUESTION });
    const assistantMessages = page.getByRole('article', { name: 'AI 回答' });
    await expect(userMessages, 'same clientMessageId retry must render one user message').toHaveCount(1);
    await expect(assistantMessages, 'same clientMessageId retry must render one assistant message').toHaveCount(1);
    await expect(page.getByText(`可见引用 ${recovered.body.citations.length} 条`, { exact: true })).toBeVisible();
    await expect(page.locator('.citation-list')).toContainText(DEPLOY_KNOWLEDGE_EVIDENCE_FILE_NAME);
    const feedbackPath = `${messagePath}/${recovered.body.assistantMessageId}/feedback`;
    const feedbackResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST' && requestPath(response.url()) === feedbackPath
    ));
    await page.getByRole('button', { name: '回答有帮助' }).click();
    await browserApiSuccess<{ rating: string }>(await feedbackResponse, 'chat feedback persistence');
    await expect(page.getByText('反馈已记录', { exact: true })).toBeVisible();

    const history = await apiGet<ChatHistoryMessageContract[]>(
      request,
      messagePath,
      business.token,
      'chat history idempotency proof',
    );
    const matchingUserMessages = history.filter((item) => (
      item.role === 'USER' && item.content === DEPLOY_BUSINESS_QUESTION
    ));
    const matchingAssistantMessages = history.filter((item) => (
      item.role === 'ASSISTANT' && item.messageId === recovered.body.assistantMessageId
    ));
    expect(matchingUserMessages).toHaveLength(1);
    expect(matchingAssistantMessages).toHaveLength(1);
    expect(matchingAssistantMessages[0].modelInvocationLogId).toBe(recovered.body.modelInvocationLogId);
    expect(matchingAssistantMessages[0].citations).toHaveLength(recovered.body.citations.length);

    let failHistoryOnce = true;
    await page.route(`**${messagePath}`, async (route) => {
      if (route.request().method() !== 'GET' || !failHistoryOnce) {
        await route.continue();
        return;
      }
      failHistoryOnce = false;
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          header: {
            code: 'AIP-D09-HISTORY-TRANSIENT',
            message: 'D-09 注入的历史恢复失败。',
            traceId: `d09-history-${DEPLOY_ROLE_JOURNEY_RUN_ID}`,
          },
          body: null,
        }),
      });
    });
    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('alert')).toContainText('D-09 注入的历史恢复失败。');
    const historyRecoveryResponse = page.waitForResponse((response) => (
      response.request().method() === 'GET'
      && requestPath(response.url()) === messagePath
      && response.status() === 200
    ));
    await page.getByRole('button', { name: '重新加载历史' }).click();
    await browserApiSuccess<ChatHistoryMessageContract[]>(await historyRecoveryResponse, 'chat refresh recovery');
    await page.unroute(`**${messagePath}`);
    await expect(page.getByRole('article', { name: '你的问题' })
      .filter({ hasText: DEPLOY_BUSINESS_QUESTION })).toHaveCount(1);
    await expect(page.getByRole('article', { name: 'AI 回答' })).toHaveCount(1);
    await expect(page.locator('.citation-list')).toContainText(DEPLOY_KNOWLEDGE_EVIDENCE_FILE_NAME);

    await updateMutationLease(lease, {
      status: 'business-chat-idempotency-complete',
      runId: DEPLOY_ROLE_JOURNEY_RUN_ID,
      sessionId,
      clientMessageId: attempts[0].clientMessageId,
      userMessageId: recovered.body.userMessageId,
      assistantMessageId: recovered.body.assistantMessageId,
      modelInvocationLogId: recovered.body.modelInvocationLogId,
      citationCount: recovered.body.citations.length,
    });
    await testInfo.attach('d09-business-chat.json', {
      body: JSON.stringify({
        runId: DEPLOY_ROLE_JOURNEY_RUN_ID,
        sessionId,
        clientMessageId: attempts[0].clientMessageId,
        userMessageId: recovered.body.userMessageId,
        assistantMessageId: recovered.body.assistantMessageId,
        modelInvocationLogId: recovered.body.modelInvocationLogId,
        citationCount: recovered.body.citations.length,
        crossTenantCitationCount: 0,
        refreshRecovered: true,
      }, null, 2),
      contentType: 'application/json',
    });
  });
}

export async function completeRoleTask(
  page: Page,
  request: APIRequestContext,
  role: AcceptanceRole,
  testInfo: TestInfo,
) {
  if (role === 'platform-admin') {
    const lease = await acquireMutationLease(testInfo, 'platform-tenant-name');
    let primaryError: unknown;
    let baseline: TenantNameBaseline | undefined;
    testInfo.setTimeout(testInfo.timeout + RECOVERY_BUDGET_MS);
    try {
      await navigateThroughProductMenu(page, '租户与代管');
      const tenantTable = await tenantMutationSurface(page);
      const firstModify = tenantTable.getByRole('button', { name: /^修改 / }).first();
      await expect(firstModify, 'deploy acceptance must provision at least one tenant').toBeVisible({
        timeout: MUTATION_ACTION_TIMEOUT_MS,
      });
      await firstModify.click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
      const dialog = page.getByRole('dialog', { name: '修改租户' });
      await expect(dialog).toBeVisible({ timeout: MUTATION_ACTION_TIMEOUT_MS });
      const tenantName = dialog.getByLabel('租户名称');
      await expect(tenantName).not.toHaveValue('', { timeout: MUTATION_ACTION_TIMEOUT_MS });
      const originalName = await tenantName.inputValue();
      const [firstCharacter, ...remainingCharacters] = Array.from(originalName);
      const temporaryName = `${firstCharacter === '验' ? '测' : '验'}${remainingCharacters.join('')}`;
      baseline = { originalName, temporaryName };
      await updateMutationLease(lease, {
        status: 'mutation-started',
        resource: lease.resource,
        retry: testInfo.retry,
        baseline,
      });
      await expect(
        tenantTable.getByRole('button', { name: `修改 ${temporaryName}`, exact: true }),
        'temporary tenant name must not collide with another tenant',
      ).toHaveCount(0);
      await tenantName.fill(temporaryName, { timeout: MUTATION_ACTION_TIMEOUT_MS });
      await dialog.getByRole('button', { name: '确认' }).click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
      await expect(dialog).toBeHidden({ timeout: MUTATION_ACTION_TIMEOUT_MS });
      await expect(
        tenantTable.getByRole('button', { name: `修改 ${temporaryName}`, exact: true }),
      ).toHaveCount(1, { timeout: MUTATION_ACTION_TIMEOUT_MS });
    } catch (error) {
      primaryError = error;
      throw error;
    } finally {
      testInfo.setTimeout(testInfo.timeout + RECOVERY_BUDGET_MS);
      try {
        if (baseline) await restoreTenantName(page, baseline);
        await releaseMutationLease(lease);
      } catch (recoveryError) {
        await failClosedAfterRecoveryError(
          page,
          testInfo,
          lease,
          baseline,
          primaryError,
          recoveryError,
        );
      }
    }
    await completePlatformModelLifecycle(page, request, testInfo);
    return;
  }
  if (role === 'tenant-admin') {
    const lease = await acquireMutationLease(testInfo, 'tenant-role-permissions');
    let primaryError: unknown;
    let baseline: RolePermissionBaseline | undefined;
    testInfo.setTimeout(testInfo.timeout + RECOVERY_BUDGET_MS);
    try {
      await navigateThroughProductMenu(page, '角色权限');
      await expect(page.getByRole('heading', { level: 1, name: '角色权限' })).toBeVisible({
        timeout: MUTATION_ACTION_TIMEOUT_MS,
      });
      const roleSelector = page.getByLabel('当前角色');
      await expect(roleSelector).not.toHaveValue('', { timeout: MUTATION_ACTION_TIMEOUT_MS });
      const roleId = await roleSelector.inputValue();
      baseline = { roleId, permissions: await permissionStates(page) };
      await updateMutationLease(lease, {
        status: 'mutation-started',
        resource: lease.resource,
        retry: testInfo.retry,
        baseline,
      });
      const firstPermission = page.getByRole('checkbox', {
        name: baseline.permissions[0].name,
        exact: true,
      });
      await firstPermission.click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
      await page.getByRole('button', { name: '保存权限' }).click({ timeout: MUTATION_ACTION_TIMEOUT_MS });
      await confirmRolePermissionReplacement(page, 'deploy E2E 验证权限矩阵变更可持久化');
      await expect(page.getByText('权限矩阵已保存。', { exact: true })).toBeVisible({
        timeout: MUTATION_ACTION_TIMEOUT_MS,
      });

      await page.reload({ waitUntil: 'domcontentloaded', timeout: MUTATION_ACTION_TIMEOUT_MS });
      await expect(page.getByRole('heading', { level: 1, name: '角色权限' })).toBeVisible({
        timeout: MUTATION_ACTION_TIMEOUT_MS,
      });
      const persisted = await selectRoleAndReadPermissions(page, roleId);
      assertPermissionShape(persisted, baseline.permissions);
      const expected = baseline.permissions.map((permission, index) => (
        index === 0 ? { ...permission, checked: !permission.checked } : permission
      ));
      expect(persisted, 'role permission replacement must persist the intended complete matrix').toEqual(expected);
    } catch (error) {
      primaryError = error;
      throw error;
    } finally {
      testInfo.setTimeout(testInfo.timeout + RECOVERY_BUDGET_MS);
      try {
        if (baseline) await restoreRolePermissions(page, baseline);
        await releaseMutationLease(lease);
      } catch (recoveryError) {
        await failClosedAfterRecoveryError(
          page,
          testInfo,
          lease,
          baseline,
          primaryError,
          recoveryError,
        );
      }
    }
    await completeTenantGovernanceJourney(page, request, testInfo);
    return;
  }
  await completeBusinessChatJourney(page, request, testInfo);
}

test.describe('deploy 三类真实身份旅程', () => {
  for (const role of roles) {
    test(`${role.label}进入任务首页、保持安全上下文`, async ({ page }) => {
      const credentials = roleCredentials(role.id);
      if (!credentials) throw new Error(`Missing deploy credentials for ${role.id}.`);

      await signInThroughUi(page, credentials);
      await expect(page).toHaveURL(role.route);
      await expect(page.getByRole('heading', { name: role.heading, level: 1 })).toBeVisible();

      const securityContext = page.getByLabel('当前安全上下文');
      await expect(securityContext).toContainText(`角色 ${role.label}`);
      await expect(securityContext).toContainText('安全上下文已建立');

      const navigation = page.getByRole('navigation');
      await expect(navigation.locator('[aria-current="page"]')).toHaveCount(1);
      for (const placeholder of ['Prompt', 'Tool', 'Agent', 'Workflow', 'Evaluation']) {
        await expect(navigation.getByText(placeholder, { exact: true })).toHaveCount(0);
      }
    });
  }
});

test.describe('deploy 响应式任务首页', () => {
  for (const viewport of viewportMatrix) {
    test(`${viewport.name} ${viewport.width}x${viewport.height} 无整页横向溢出`, async ({ page }, testInfo) => {
      const credentials = roleCredentials('platform-admin');
      if (!credentials) throw new Error('Missing deploy platform administrator credentials.');

      await page.setViewportSize(viewport);
      await signInThroughUi(page, credentials);
      await expect(page.getByRole('heading', { name: '平台任务首页', level: 1 })).toBeVisible();
      await expectNoFullPageHorizontalOverflow(page);

      if (viewport.width <= 768) {
        await page.getByRole('button', { name: '打开导航' }).click();
        const drawer = page.getByRole('dialog', { name: '主导航' });
        await expect(drawer).toBeVisible();
        await expect(drawer.getByRole('navigation')).toBeVisible();
        await expectNoFullPageHorizontalOverflow(page);
        await drawer.getByRole('button', { name: '关闭导航' }).click();
        await expect(drawer).toBeHidden();
      }

      await testInfo.attach(`platform-home-${viewport.name}-${viewport.width}x${viewport.height}`, {
        body: await page.screenshot({ fullPage: true }),
        contentType: 'image/png',
      });
    });
  }
});

test.describe('deploy 键盘、移动导航与缩放', () => {
  test('移动导航支持键盘打开、Escape 关闭并恢复焦点', async ({ page }) => {
    const credentials = roleCredentials('platform-admin');
    if (!credentials) throw new Error('Missing deploy platform administrator credentials.');
    await page.setViewportSize({ width: 390, height: 844 });
    await signInThroughUi(page, credentials);

    const trigger = page.getByRole('button', { name: '打开导航' });
    await trigger.focus();
    await page.keyboard.press('Enter');
    const drawer = page.getByRole('dialog', { name: '主导航' });
    await expect(drawer).toBeVisible();
    await expect(drawer.getByRole('button', { name: '关闭导航' })).toBeFocused();
    await page.keyboard.press('Shift+Tab');
    expect(await drawer.evaluate((element) => element.contains(document.activeElement))).toBe(true);
    await page.keyboard.press('Tab');
    expect(await drawer.evaluate((element) => element.contains(document.activeElement))).toBe(true);
    await expectNoWcagAaViolations(page);
    await page.keyboard.press('Escape');
    await expect(drawer).toBeHidden();
    await expect(trigger).toBeFocused();
  });

  test('移动端租户修改 Dialog 支持键盘打开、Escape 关闭并恢复焦点', async ({ page }) => {
    const credentials = roleCredentials('platform-admin');
    if (!credentials) throw new Error('Missing deploy platform administrator credentials.');
    await page.setViewportSize({ width: 390, height: 844 });
    await signInThroughUi(page, credentials);
    await navigateThroughProductMenu(page, '租户与代管');
    await expect(page.getByRole('heading', { level: 1, name: '租户管理' })).toBeVisible();

    const firstModify = page.getByRole('table', { name: '租户列表' })
      .getByRole('button', { name: /^修改 / })
      .first();
    await expect(firstModify, 'deploy acceptance must provision at least one tenant').toBeVisible();
    await firstModify.focus();
    await page.keyboard.press('Enter');
    const dialog = page.getByRole('dialog', { name: '修改租户' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByLabel('租户名称')).not.toHaveValue('');
    await expectNoWcagAaViolations(page);
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(firstModify).toBeFocused();
  });

  test('应急代管 Dialog 支持真实焦点圈、Escape 与背景关闭并恢复焦点', async ({ page }) => {
    const credentials = roleCredentials('platform-admin');
    if (!credentials) throw new Error('Missing deploy platform administrator credentials.');
    await installDialogMotionEvidence(page);
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await page.setViewportSize({ width: 1440, height: 1000 });
    await signInThroughUi(page, credentials);
    await navigateThroughProductMenu(page, '租户与代管');
    await expect(page.getByRole('heading', { level: 1, name: '租户管理' })).toBeVisible();

    const trigger = page.getByRole('button', { name: '应急代管' });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await expect.poll(() => page.evaluate(
      () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    )).toBe(true);
    await resetDialogMotionEvidence(page);
    await trigger.focus();
    await page.keyboard.press('Enter');
    let dialog = page.getByRole('dialog', { name: '应急代管' });
    await expect(dialog).toBeVisible();
    await expectDialogMotionEvidence(page, 'reduced');
    await expect.poll(() => dialog.evaluate((element) => element.contains(document.activeElement))).toBe(true);
    const firstFocusable = dialog.getByLabel('代管动作');
    const lastFocusable = dialog.getByRole('button', { name: '取消' });
    await lastFocusable.focus();
    await page.keyboard.press('Tab');
    await expect(firstFocusable).toBeFocused();
    await firstFocusable.focus();
    await page.keyboard.press('Shift+Tab');
    await expect(lastFocusable).toBeFocused();
    await trigger.evaluate((element) => element.focus());
    await expect(trigger).not.toBeFocused();
    await expect.poll(() => dialog.evaluate((element) => element.contains(document.activeElement))).toBe(true);
    await expectNoWcagAaViolations(page);
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();

    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await expect.poll(() => page.evaluate(
      () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    )).toBe(false);
    await resetDialogMotionEvidence(page);
    await trigger.click();
    dialog = page.getByRole('dialog', { name: '应急代管' });
    await expect(dialog).toBeVisible();
    await expectDialogMotionEvidence(page, 'animated');
    const backdrop = page.locator('.fui-DialogSurface__backdrop').last();
    await expect(backdrop).toBeVisible();
    await backdrop.click({ position: { x: 4, y: 4 } });
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();
  });

  test('1440x1000 物理视窗的 200% 等效 CSS 视窗仍可完成主要操作且无整页横向溢出', async ({ page }) => {
    const credentials = roleCredentials('platform-admin');
    if (!credentials) throw new Error('Missing deploy platform administrator credentials.');
    await page.setViewportSize({ width: 720, height: 500 });
    await signInThroughUi(page, credentials);

    await expect(page.getByRole('heading', { name: '平台任务首页', level: 1 })).toBeVisible();
    await expectNoFullPageHorizontalOverflow(page);
    await navigateThroughProductMenu(page, '租户与代管');
    await expect(page.getByRole('heading', { level: 1, name: '租户管理' })).toBeVisible();
    const firstModify = page.getByRole('table', { name: '租户列表' })
      .getByRole('button', { name: /^修改 / })
      .first();
    await expect(firstModify, 'deploy acceptance must provision at least one tenant').toBeVisible();
    await firstModify.click();
    const dialog = page.getByRole('dialog', { name: '修改租户' });
    await expect(dialog.getByLabel('租户名称')).not.toHaveValue('');
    await dialog.getByRole('button', { name: '取消' }).click();
    await expect(dialog).toBeHidden();
    await expectNoFullPageHorizontalOverflow(page);
    await expectNoWcagAaViolations(page);
  });
});

test.describe('deploy ModelHub 核心 Dialog 视觉与无障碍证据', () => {
  for (const theme of modelHubThemeMatrix) {
    test(`${theme.label}主题下 provider/model Dialog 打开态通过 axe 并保留截图`, async ({ page }, testInfo) => {
      await page.emulateMedia({ colorScheme: theme.colorScheme, forcedColors: theme.forcedColors });
      await page.setViewportSize({ width: 1440, height: 1000 });
      await openModelHubVisualSurface(page);
      await expectThemePreferences(page, theme);
      await auditModelHubDialogPair(page, testInfo, `model-hub-${theme.id}`);
    });
  }

  test('390x844 移动视口可用键盘完成 provider/model Dialog 开启、扫描与关闭', async ({ page }, testInfo) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await openModelHubVisualSurface(page);
    await auditModelHubDialogPair(page, testInfo, 'model-hub-mobile-390x844');
  });

  test('200% 等效 CSS 视口不丢失 provider/model Dialog 主要信息与操作', async ({ page }, testInfo) => {
    await page.setViewportSize({ width: 720, height: 500 });
    await openModelHubVisualSurface(page);
    await auditModelHubDialogPair(page, testInfo, 'model-hub-200-percent-equivalent');
  });
});
