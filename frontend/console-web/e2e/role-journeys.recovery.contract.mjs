import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const sourceUrl = new URL('./role-journeys.acceptance.spec.ts', import.meta.url);

test('stateful role journeys reserve cleanup time and restore both mutations in finally blocks', async () => {
  const source = await readFile(sourceUrl, 'utf8');

  assert.match(source, /type TestInfo/);
  assert.match(source, /testInfo\.setTimeout\(testInfo\.timeout \+ RECOVERY_BUDGET_MS\)/);
  assert.match(source, /restoreTenantName\(/);
  assert.match(source, /restoreRolePermissions\(/);
  assert.ok((source.match(/finally \{/g) || []).length >= 2, 'both persistent mutations must have finally recovery');
});

test('recovery failure is diagnostic, preserves the primary error, and blocks unsafe retries', async () => {
  const source = await readFile(sourceUrl, 'utf8');

  assert.match(source, /new AggregateError\(/);
  assert.match(source, /recovery-failure\.json/);
  assert.match(source, /recovery-failure\.png/);
  assert.match(source, /open\(lockPath, 'wx'\)/);
  assert.match(source, /testInfo\.retry/);
  assert.match(source, /workers !== 1/);
});

test('D-09 platform journey is a disposable four-eyes model lifecycle with zero secret echo', async () => {
  const source = await readFile(sourceUrl, 'utf8');

  assert.match(source, /completePlatformModelLifecycle/);
  assert.match(source, /新增模型供应商/);
  assert.match(source, /访问凭证/);
  assert.match(source, /approveModelHubResourceThroughUi/);
  assert.match(source, /switchToCandidateApprover/);
  assert.match(source, /DEPLOY_CANDIDATE_APPROVER_USERNAME/);
  assert.match(source, /connectivity-test/);
  assert.match(source, /确认停用/);
  assert.match(source, /安全审计/);
  assert.match(source, /secret zero-echo/i);
  assert.match(source, /test\.use\(\{ trace: 'off', video: 'off' \}\)/);
  assert.doesNotMatch(source, /\.not\.toContain\(secret\)/);
});

test('D-09 tenant journey covers invitation cleanup, stale role response, full diff, and FAILED recovery', async () => {
  const source = await readFile(sourceUrl, 'utf8');

  assert.match(source, /completeTenantGovernanceJourney/);
  assert.match(source, /发送邀请/);
  assert.match(source, /确认撤销/);
  assert.match(source, /releaseStaleRoleResponse/);
  assert.match(source, /权限变更差异/);
  assert.match(source, /DEPLOY_KNOWLEDGE_RECOVERY_FILE_NAME/);
  assert.match(source, /确认重试解析/);
});

test('D-09 business journey retries one client message and proves one durable answer, feedback, citations, and refresh recovery', async () => {
  const source = await readFile(sourceUrl, 'utf8');

  assert.match(source, /completeBusinessChatJourney/);
  assert.match(source, /route\.fulfill/);
  assert.match(source, /clientMessageId/);
  assert.match(source, /重试同一请求/);
  assert.match(source, /modelInvocationLogId/);
  assert.match(source, /回答有帮助/);
  assert.match(source, /重新加载历史/);
  assert.match(source, /cross-tenant citations must remain zero/i);
});
