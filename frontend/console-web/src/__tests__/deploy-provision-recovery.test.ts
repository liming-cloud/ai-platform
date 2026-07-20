import { describe, expect, it } from 'vitest';
import {
  approvalTaskDiscoveryExpired,
  assertConsistentApprovalDecision,
  buildDeployCreationCommand,
  modelConfigApprovalTitles,
  modelConfigRecoveryAction,
  observableModelDefinitionMatches,
  providerRecoveryAction,
  selectExactPendingApprovalTask,
  type ApprovalTaskExpectation,
} from '../deploy-provision-contract';

const expectation = {
  sourceContext: 'model-hub',
  approvalRequestId: 'REQ-B',
  resourceType: 'model_config' as const,
  resourceId: 'M-1',
  approvalType: 'model_config_change',
  titles: ['创建模型配置 验收模型'],
} satisfies ApprovalTaskExpectation;

function task(overrides: Record<string, unknown> = {}) {
  return {
    taskId: 'T-1',
    approvalRequestId: 'REQ-B',
    sourceContext: 'model-hub',
    sourceResourceType: 'model_config',
    sourceResourceId: 'M-1',
    requesterId: '1001',
    approverId: null,
    status: 'PENDING',
    traceId: 'TRACE-1',
    approvalType: 'model_config_change',
    title: '创建模型配置 验收模型',
    evidenceSchema: 'model-approval-evidence/v1',
    evidenceDigest: 'a'.repeat(64),
    targetDigest: 'b'.repeat(64),
    ...overrides,
  };
}

describe('deploy model recovery contract', () => {
  it('binds provider and model creation retries to stable versioned request ids', () => {
    const providerCommand = {
      providerName: 'E2E 本地确定性运行时 v1',
      providerType: 'local-deterministic',
    };
    const firstProviderAttempt = buildDeployCreationCommand(
      'provider',
      'local-deterministic-v1',
      providerCommand,
    );
    const retriedProviderAttempt = buildDeployCreationCommand(
      'provider',
      'local-deterministic-v1',
      providerCommand,
    );
    const chatModelAttempt = buildDeployCreationCommand(
      'model',
      'local-deterministic-v1.e2e-local-chat-v1',
      { modelCode: 'e2e-local-chat-v1' },
    );
    const retriedChatModelAttempt = buildDeployCreationCommand(
      'model',
      'local-deterministic-v1.e2e-local-chat-v1',
      { modelCode: 'e2e-local-chat-v1' },
    );
    const embeddingModelAttempt = buildDeployCreationCommand(
      'model',
      'local-deterministic-v1.e2e-local-embedding-v1',
      { modelCode: 'e2e-local-embedding-v1' },
    );

    expect(firstProviderAttempt).toEqual({
      requestId: 'deploy.provider.create.local-deterministic-v1',
      ...providerCommand,
    });
    expect(retriedProviderAttempt.requestId).toBe(firstProviderAttempt.requestId);
    expect(chatModelAttempt.requestId).toBe(
      'deploy.model.create.local-deterministic-v1.e2e-local-chat-v1',
    );
    expect(retriedChatModelAttempt).toEqual(chatModelAttempt);
    expect(embeddingModelAttempt.requestId).toBe(
      'deploy.model.create.local-deterministic-v1.e2e-local-embedding-v1',
    );
    expect(new Set([
      firstProviderAttempt.requestId,
      chatModelAttempt.requestId,
      embeddingModelAttempt.requestId,
    ])).toHaveLength(3);
  });

  it('rejects unsafe deploy creation keys before making a request', () => {
    const modelPrefix = 'deploy.model.create.';
    const maximumSafeKey = 'x'.repeat(128 - modelPrefix.length);

    expect(buildDeployCreationCommand('model', maximumSafeKey, {}).requestId).toHaveLength(128);
    expect(() => buildDeployCreationCommand('provider', '包含 空格', {})).toThrow(/safe ASCII/);
    expect(() => buildDeployCreationCommand('model', `${maximumSafeKey}x`, {})).toThrow(/128/);
  });

  it.each([
    ['REJECTED', true, 'RESUBMIT'],
    ['REJECTED', false, 'RESUBMIT'],
    ['DEPRECATED', true, 'FAIL_TERMINAL'],
    ['DISABLED', true, 'FAIL_TERMINAL'],
    ['PENDING_APPROVAL', true, 'WAIT_FOR_APPROVAL'],
    ['PENDING_APPROVAL', false, 'FAIL_PENDING_DRIFT'],
    ['ACTIVE', true, 'REUSE'],
    ['ACTIVE', false, 'UPDATE'],
    ['INACTIVE', true, 'REUSE'],
    ['INACTIVE', false, 'UPDATE'],
  ] as const)('maps %s (matches=%s) to %s', (status, definitionMatches, expected) => {
    expect(modelConfigRecoveryAction(status, definitionMatches)).toBe(expected);
  });

  it('selects exactly one task across context, type, resource, approval type and title', () => {
    const exact = task();
    const selected = selectExactPendingApprovalTask([
      task({ taskId: 'T-WRONG-CONTEXT', approvalRequestId: 'REQ-CONTEXT', sourceContext: 'identity-access' }),
      task({ taskId: 'T-STALE-A', approvalRequestId: 'REQ-A' }),
      task({ taskId: 'T-WRONG-TYPE', approvalRequestId: 'REQ-TYPE', approvalType: 'model_provider_change' }),
      task({ taskId: 'T-WRONG-TITLE', approvalRequestId: 'REQ-TITLE', title: '更新模型配置 验收模型' }),
      exact,
    ], expectation);

    expect(selected).toEqual(exact);
  });

  it('accepts either exact create or update title only when recovering an interrupted pending model', () => {
    const pendingTitles = modelConfigApprovalTitles('WAIT_FOR_APPROVAL', '验收模型');
    expect(pendingTitles).toEqual(['创建模型配置 验收模型', '更新模型配置 验收模型']);
    expect(selectExactPendingApprovalTask([
      task({ taskId: 'T-UPDATE', title: '更新模型配置 验收模型' }),
    ], { ...expectation, titles: pendingTitles })).toMatchObject({ taskId: 'T-UPDATE' });
    expect(modelConfigApprovalTitles('RESUBMIT', '验收模型')).toEqual(['更新模型配置 验收模型']);
  });

  it('fails closed for duplicate or metadata-conflicting pending tasks', () => {
    expect(() => selectExactPendingApprovalTask([
      task({ taskId: 'T-1' }),
      task({ taskId: 'T-2' }),
    ], expectation)).toThrow(/multiple exact pending approval tasks/);

    expect(() => selectExactPendingApprovalTask([
      task({ taskId: 'T-WRONG', approvalType: 'model_provider_change' }),
    ], expectation)).toThrow(/inconsistent pending approval task/);
  });

  it('ignores a stale task for the same resource and selects only the current request', () => {
    const current = task({ taskId: 'T-B', approvalRequestId: 'REQ-B' });
    expect(selectExactPendingApprovalTask([
      task({ taskId: 'T-A', approvalRequestId: 'REQ-A' }),
      current,
    ], expectation)).toEqual(current);
  });

  it('bounds pending-without-task discovery instead of polling for the full provision timeout', () => {
    expect(approvalTaskDiscoveryExpired(1_000, 30_999)).toBe(false);
    expect(approvalTaskDiscoveryExpired(1_000, 31_000)).toBe(true);
  });

  it('binds approval decisions to the selected task and model-hub context', () => {
    const selected = task();
    expect(() => assertConsistentApprovalDecision({
      taskId: 'T-1',
      approvalRequestId: 'REQ-B',
      status: 'APPROVED',
      sourceContext: 'model-hub',
      sourceResourceType: 'model_config',
      sourceResourceId: 'M-1',
      evidenceSchema: 'model-approval-evidence/v1',
      evidenceDigest: 'a'.repeat(64),
      targetDigest: 'b'.repeat(64),
    }, selected, expectation)).not.toThrow();

    expect(() => assertConsistentApprovalDecision({
      taskId: 'T-OTHER',
      approvalRequestId: 'REQ-B',
      status: 'APPROVED',
      sourceContext: 'model-hub',
      sourceResourceType: 'model_config',
      sourceResourceId: 'M-1',
    }, selected, expectation)).toThrow(/inconsistent approval decision/);
    expect(() => assertConsistentApprovalDecision({
      taskId: 'T-1',
      approvalRequestId: 'REQ-B',
      status: 'APPROVED',
      sourceContext: 'identity-access',
      sourceResourceType: 'model_config',
      sourceResourceId: 'M-1',
    }, selected, expectation)).toThrow(/inconsistent approval decision/);
    expect(() => assertConsistentApprovalDecision({
      taskId: 'T-1',
      approvalRequestId: 'REQ-A',
      status: 'APPROVED',
      sourceContext: 'model-hub',
      sourceResourceType: 'model_config',
      sourceResourceId: 'M-1',
    }, selected, expectation)).toThrow(/inconsistent approval decision/);
  });

  it('compares every model definition field exposed by the API with numeric normalization', () => {
    const definition = {
      modelName: '验收模型',
      modelType: 'CHAT',
      contextWindow: 8192,
      embeddingDimension: null,
      inputPricePer1k: 0,
      outputPricePer1k: 0.001,
      options: { providerKind: 'local-deterministic', temperature: 0, maxTokens: 512 },
    };
    const model = {
      modelName: '验收模型',
      modelType: 'CHAT',
      contextWindow: 8192,
      embeddingDimension: null,
      inputPricePer1k: '0.0000',
      outputPricePer1k: '0.0010',
      providerKind: 'LOCAL-DETERMINISTIC',
      temperature: '0.0',
      topP: null,
      maxTokens: 512,
    };

    expect(observableModelDefinitionMatches(model, definition)).toBe(true);
    expect(observableModelDefinitionMatches({ ...model, inputPricePer1k: '0.01' }, definition)).toBe(false);
    expect(observableModelDefinitionMatches({ ...model, temperature: '0.2' }, definition)).toBe(false);
    expect(observableModelDefinitionMatches({ ...model, topP: '0.9' }, definition)).toBe(false);
    expect(observableModelDefinitionMatches({ ...model, maxTokens: 1024 }, definition)).toBe(false);
  });

  it.each([
    ['ACTIVE', true, 'REUSE'],
    ['PENDING_APPROVAL', true, 'REUSE'],
    ['DISABLED', true, 'FAIL_TERMINAL'],
    ['ACTIVE', false, 'FAIL_DRIFT'],
    ['DRAFT', true, 'FAIL_UNSUPPORTED'],
  ] as const)('maps provider %s (matches=%s) to %s', (status, definitionMatches, expected) => {
    expect(providerRecoveryAction(status, definitionMatches)).toBe(expected);
  });
});
