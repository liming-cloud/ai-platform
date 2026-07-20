import {
  expect,
  test,
  type APIRequestContext,
  type APIResponse,
  type Page,
  type TestInfo,
} from '@playwright/test';
import {
  expectNoFullPageHorizontalOverflow,
  expectNoWcagAaViolations,
} from './fixtures/accessibility';
import {
  DEPLOY_KNOWLEDGE_BASE_NAME,
  DEPLOY_KNOWLEDGE_CANCELLATION_FILE_NAME,
  DEPLOY_ROLE_JOURNEY_RUN_ID,
} from './fixtures/deploy-provision';
import {
  navigateThroughProductMenu,
  roleCredentials,
  signInThroughUi,
  type AcceptanceRole,
} from './fixtures/authorized';

type RuntimeEnvironment = Readonly<Record<string, string | undefined>>;
declare const process: { readonly env: RuntimeEnvironment };

type ApiEnvelope<T> = {
  header: { code: string; message: string; traceId?: string };
  body: T;
};

type ApiIdentity = {
  token: string;
  tenantId: string;
  userId: string;
};

type KnowledgeBaseContract = {
  knowledgeBaseId: string;
  name: string;
};

type KnowledgeDocumentContract = {
  knowledgeBaseId: string;
  documentId: string;
  fileName: string;
  status: string;
  jobId: string | null;
  jobType: string | null;
  jobStatus: string | null;
  traceId: string | null;
  cancellationReason: string | null;
  cancelledBy: string | null;
  cancellationTraceId: string | null;
  cancelledAt: string | null;
  cancelEligible: boolean;
};

const CANCELLATION_REASON = `D09 固定验收取消 ${DEPLOY_ROLE_JOURNEY_RUN_ID}`;

function authorization(token: string) {
  return { Authorization: `Bearer ${token}` };
}

async function envelope<T>(response: APIResponse, description: string): Promise<ApiEnvelope<T>> {
  const raw = await response.text();
  let parsed: ApiEnvelope<T>;
  try {
    parsed = JSON.parse(raw) as ApiEnvelope<T>;
  } catch {
    throw new Error(`${description} returned non-JSON HTTP ${response.status()}: ${raw.slice(0, 500)}`);
  }
  if (!response.ok() || parsed.header?.code !== 'SUCCESS') {
    throw new Error(
      `${description} failed: HTTP ${response.status()}, code=${parsed.header?.code || 'missing'}, `
      + `message=${parsed.header?.message || 'missing'}, trace=${parsed.header?.traceId || 'missing'}`,
    );
  }
  return parsed;
}

async function loginForApi(request: APIRequestContext, role: AcceptanceRole): Promise<ApiIdentity> {
  const credentials = roleCredentials(role);
  if (!credentials) throw new Error(`Missing deploy credentials for ${role}.`);
  return (await envelope<ApiIdentity>(await request.post('/api/v1/auth/login', {
    data: {
      tenantCode: credentials.tenantCode,
      username: credentials.username,
      password: credentials.password,
    },
  }), `${role} API login`)).body;
}

async function cancellationTarget(request: APIRequestContext, identity: ApiIdentity) {
  const headers = authorization(identity.token);
  const knowledgeBases = (await envelope<KnowledgeBaseContract[]>(
    await request.get('/api/v1/knowledge-bases', { headers }),
    'list knowledge bases',
  )).body;
  const knowledgeBase = knowledgeBases.find((candidate) => candidate.name === DEPLOY_KNOWLEDGE_BASE_NAME);
  expect(knowledgeBase, 'candidate knowledge base must be visible to the tenant administrator').toBeDefined();
  const documents = (await envelope<KnowledgeDocumentContract[]>(
    await request.get(
      `/api/v1/knowledge-bases/${encodeURIComponent(knowledgeBase!.knowledgeBaseId)}/documents`,
      { headers },
    ),
    'list cancellation candidate documents',
  )).body;
  const document = documents.find(
    (candidate) => candidate.fileName === DEPLOY_KNOWLEDGE_CANCELLATION_FILE_NAME,
  );
  expect(document, 'candidate fixture must expose the deterministic cancellation document').toBeDefined();
  return { knowledgeBase: knowledgeBase!, document: document! };
}

function expectDurableCancellation(document: KnowledgeDocumentContract, expectedReason: string) {
  expect(document.status).toBe('CANCELLED');
  expect(document.jobStatus).toBe('CANCELLED');
  expect(document.cancelEligible).toBe(false);
  expect(document.cancellationReason).toBe(expectedReason);
  expect(document.cancelledBy).toMatch(/^\d+$/);
  expect(document.cancellationTraceId).toMatch(/\S/);
  expect(document.cancelledAt).toMatch(/^\d{4}-\d{2}-\d{2}T/);
}

async function openCandidateDocumentPanel(page: Page) {
  const credentials = roleCredentials('tenant-admin');
  if (!credentials) throw new Error('Missing deploy credentials for tenant-admin.');
  await page.setViewportSize({ width: 1280, height: 800 });
  await signInThroughUi(page, credentials);
  await navigateThroughProductMenu(page, '知识库治理');
  await expect(page.getByRole('heading', { name: '知识库治理' })).toBeVisible();
  const knowledgeBaseRow = page.getByRole('row').filter({ hasText: DEPLOY_KNOWLEDGE_BASE_NAME });
  await knowledgeBaseRow.getByRole('button', { name: /^管理文档 / }).click();
  const documentDialog = page.getByRole('dialog', { name: '文档维护' });
  await expect(documentDialog).toBeVisible();
  await documentDialog.getByLabel('每页条数').selectOption('50');
  const documentRow = documentDialog.getByRole('row').filter({
    hasText: DEPLOY_KNOWLEDGE_CANCELLATION_FILE_NAME,
  });
  await expect(documentRow).toBeVisible();
  return { documentDialog, documentRow };
}

async function denyBusinessUserCancellation(
  request: APIRequestContext,
  identity: ApiIdentity,
  knowledgeBaseId: string,
  documentId: string,
) {
  const denied = await request.post(
    `/api/v1/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}`
      + `/documents/${encodeURIComponent(documentId)}/cancel`,
    {
      headers: authorization(identity.token),
      data: { reason: '业务用户不得执行治理取消' },
      failOnStatusCode: false,
    },
  );
  const body = await denied.json() as ApiEnvelope<unknown>;
  expect(denied.status()).toBe(403);
  expect(body.header.code).not.toBe('SUCCESS');
  expect(body.header.traceId).toMatch(/\S/);
}

test('tenant administrator cancels one frozen job with durable evidence while business user is denied', async ({
  page,
  request,
}, testInfo: TestInfo) => {
  if (process.env.E2E_REQUIRE_ROLE_JOURNEYS !== 'true') {
    throw new Error('J-06 cancellation acceptance requires the candidate-only deploy fixture.');
  }

  const tenantAdmin = await loginForApi(request, 'tenant-admin');
  const businessUser = await loginForApi(request, 'business-user');
  const initial = await cancellationTarget(request, tenantAdmin);
  await denyBusinessUserCancellation(
    request,
    businessUser,
    initial.knowledgeBase.knowledgeBaseId,
    initial.document.documentId,
  );

  const { documentDialog, documentRow } = await openCandidateDocumentPanel(page);
  let cancelledThroughUi = false;

  if (initial.document.status !== 'CANCELLED') {
    expect(initial.document.jobType).toBe('DOCUMENT_PARSE');
    expect(initial.document.jobStatus).toBe('PENDING');
    expect(initial.document.cancelEligible).toBe(true);

    const trigger = documentRow.getByRole('button', {
      name: `取消处理 ${DEPLOY_KNOWLEDGE_CANCELLATION_FILE_NAME}`,
    });
    await trigger.focus();
    await page.keyboard.press('Enter');

    const cancellationDialog = page.getByRole('dialog', { name: '确认取消文档处理' });
    await expect(cancellationDialog).toBeVisible();
    await expect(cancellationDialog.getByText(DEPLOY_KNOWLEDGE_CANCELLATION_FILE_NAME)).toBeVisible();
    await expect(cancellationDialog.getByText(
      `作业 ${initial.document.jobId} · DOCUMENT_PARSE · PENDING`,
    )).toBeVisible();
    await expect(cancellationDialog.getByText(`Trace ${initial.document.traceId}`)).toBeVisible();
    const confirm = cancellationDialog.getByRole('button', { name: '确认取消处理' });
    await expect(confirm).toBeDisabled();
    await cancellationDialog.getByLabel('取消理由').fill(CANCELLATION_REASON);
    await expect(confirm).toBeEnabled();

    const cancellationResponse = page.waitForResponse((response) => (
      response.request().method() === 'POST'
      && response.url().endsWith(
        `/knowledge-bases/${initial.knowledgeBase.knowledgeBaseId}`
          + `/documents/${initial.document.documentId}/cancel`,
      )
    ));
    await confirm.focus();
    await page.keyboard.press('Enter');
    const browserEnvelope = await (await cancellationResponse).json() as ApiEnvelope<KnowledgeDocumentContract>;
    expect(browserEnvelope.header.code).toBe('SUCCESS');
    expectDurableCancellation(browserEnvelope.body, CANCELLATION_REASON);
    await expect(cancellationDialog).toBeHidden();
    cancelledThroughUi = true;
  } else {
    expectDurableCancellation(initial.document, CANCELLATION_REASON);
  }

  await expect(documentRow.getByText('已取消')).toBeVisible();
  await expect(documentRow.getByText(`原因：${CANCELLATION_REASON}`)).toBeVisible();
  await expect(documentRow.getByText(/^操作人：\d+$/)).toBeVisible();
  await expect(documentRow.getByText(/^取消时间：\d{4}-\d{2}-\d{2}T/)).toBeVisible();
  await expect(documentRow.getByText(/^Trace \S+/)).toBeVisible();
  await expect(documentRow.getByText('可按需要重新上传')).toBeVisible();
  if (cancelledThroughUi) {
    await expect(documentRow.getByText(DEPLOY_KNOWLEDGE_CANCELLATION_FILE_NAME)).toBeFocused();
  }

  const duplicate = (await envelope<KnowledgeDocumentContract>(await request.post(
    `/api/v1/knowledge-bases/${encodeURIComponent(initial.knowledgeBase.knowledgeBaseId)}`
      + `/documents/${encodeURIComponent(initial.document.documentId)}/cancel`,
    {
      headers: authorization(tenantAdmin.token),
      data: { reason: `${CANCELLATION_REASON} duplicate` },
    },
  ), 'duplicate cancellation')).body;
  expectDurableCancellation(duplicate, CANCELLATION_REASON);

  const persisted = (await cancellationTarget(request, tenantAdmin)).document;
  expectDurableCancellation(persisted, CANCELLATION_REASON);
  expect(persisted.cancellationTraceId).toBe(duplicate.cancellationTraceId);
  expect(persisted.cancelledAt).toBe(duplicate.cancelledAt);

  await expectNoFullPageHorizontalOverflow(page);
  await expectNoWcagAaViolations(page);
  await testInfo.attach('j06-knowledge-cancellation-evidence.json', {
    body: JSON.stringify({
      runId: DEPLOY_ROLE_JOURNEY_RUN_ID,
      tenantId: tenantAdmin.tenantId,
      knowledgeBaseId: initial.knowledgeBase.knowledgeBaseId,
      documentId: initial.document.documentId,
      jobId: persisted.jobId,
      cancellationReason: persisted.cancellationReason,
      cancelledBy: persisted.cancelledBy,
      cancellationTraceId: persisted.cancellationTraceId,
      cancelledAt: persisted.cancelledAt,
    }, null, 2),
    contentType: 'application/json',
  });
  await testInfo.attach('j06-knowledge-cancellation.png', {
    body: await documentDialog.screenshot(),
    contentType: 'image/png',
  });
});
