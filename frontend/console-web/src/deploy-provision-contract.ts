export const APPROVAL_TASK_DISCOVERY_GRACE_MS = 30_000;

const DEPLOY_CREATION_KEY_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]*$/;
const MAX_CREATION_REQUEST_ID_LENGTH = 128;

export function buildDeployCreationCommand<T extends object>(
  resourceType: 'provider' | 'model',
  stableKey: string,
  command: T,
): T & { readonly requestId: string } {
  if (!DEPLOY_CREATION_KEY_PATTERN.test(stableKey)) {
    throw new Error('Deploy creation stable key must use safe ASCII characters only.');
  }
  const requestId = `deploy.${resourceType}.create.${stableKey}`;
  if (requestId.length > MAX_CREATION_REQUEST_ID_LENGTH) {
    throw new Error('Deploy creation requestId must not exceed 128 characters.');
  }
  return { ...command, requestId };
}

export interface PendingApprovalTaskContract {
  taskId: string;
  approvalRequestId: string;
  title: string;
  approvalType: string;
  sourceContext: string;
  sourceResourceType: string;
  sourceResourceId: string;
  evidenceSchema?: string | null;
  evidenceDigest?: string | null;
  targetDigest?: string | null;
}

export interface ApprovalDecisionContract {
  taskId: string;
  approvalRequestId: string;
  status: string;
  sourceContext: string;
  sourceResourceType: string;
  sourceResourceId: string;
  evidenceSchema?: string | null;
  evidenceDigest?: string | null;
  targetDigest?: string | null;
}

export interface ApprovalTaskExpectation {
  sourceContext: 'model-hub';
  approvalRequestId: string;
  resourceType: 'model_provider' | 'model_config';
  resourceId: string;
  approvalType: 'model_provider_change' | 'model_config_change';
  titles: readonly string[];
}

export function modelConfigRecoveryAction(status: string, definitionMatches: boolean) {
  if (status === 'REJECTED') return 'RESUBMIT' as const;
  if (status === 'DEPRECATED' || status === 'DISABLED') return 'FAIL_TERMINAL' as const;
  if (status === 'PENDING_APPROVAL') {
    return definitionMatches ? 'WAIT_FOR_APPROVAL' as const : 'FAIL_PENDING_DRIFT' as const;
  }
  if (status === 'ACTIVE' || status === 'INACTIVE') {
    return definitionMatches ? 'REUSE' as const : 'UPDATE' as const;
  }
  return 'FAIL_UNSUPPORTED' as const;
}

export function providerRecoveryAction(status: string, definitionMatches: boolean) {
  if (!definitionMatches) return 'FAIL_DRIFT' as const;
  if (status === 'ACTIVE' || status === 'PENDING_APPROVAL') return 'REUSE' as const;
  if (status === 'DISABLED') return 'FAIL_TERMINAL' as const;
  return 'FAIL_UNSUPPORTED' as const;
}

interface ObservableModelConfig {
  modelName: string;
  modelType: string;
  contextWindow: number;
  embeddingDimension?: number | null;
  inputPricePer1k: string | number;
  outputPricePer1k: string | number;
  providerKind: string;
  temperature?: string | number | null;
  topP?: string | number | null;
  maxTokens?: number | null;
}

interface ObservableModelDefinition {
  modelName: string;
  modelType: string;
  contextWindow: number;
  embeddingDimension: number | null;
  inputPricePer1k: number;
  outputPricePer1k: number;
  options: Readonly<Record<string, unknown>>;
}

function sameOptionalNumber(actual: unknown, expected: unknown) {
  if (expected == null) return actual == null || actual === '';
  if (actual == null || actual === '') return false;
  const actualNumber = Number(actual);
  const expectedNumber = Number(expected);
  return Number.isFinite(actualNumber)
    && Number.isFinite(expectedNumber)
    && actualNumber === expectedNumber;
}

export function observableModelDefinitionMatches(
  model: ObservableModelConfig,
  definition: ObservableModelDefinition,
) {
  return model.modelName === definition.modelName
    && model.modelType === definition.modelType
    && model.contextWindow === definition.contextWindow
    && (model.embeddingDimension ?? null) === definition.embeddingDimension
    && model.providerKind.trim().toLowerCase()
      === String(definition.options.providerKind || '').trim().toLowerCase()
    && sameOptionalNumber(model.inputPricePer1k, definition.inputPricePer1k)
    && sameOptionalNumber(model.outputPricePer1k, definition.outputPricePer1k)
    && sameOptionalNumber(model.temperature, definition.options.temperature)
    && sameOptionalNumber(model.topP, definition.options.topP)
    && sameOptionalNumber(model.maxTokens, definition.options.maxTokens);
}

export function modelConfigApprovalTitles(
  recoveryAction: ReturnType<typeof modelConfigRecoveryAction>,
  modelName: string,
) {
  const createTitle = `创建模型配置 ${modelName}`;
  const updateTitle = `更新模型配置 ${modelName}`;
  if (recoveryAction === 'UPDATE' || recoveryAction === 'RESUBMIT') return [updateTitle] as const;
  if (recoveryAction === 'WAIT_FOR_APPROVAL') return [createTitle, updateTitle] as const;
  return [createTitle] as const;
}

export function selectExactPendingApprovalTask<T extends PendingApprovalTaskContract>(
  tasks: readonly T[],
  expectation: ApprovalTaskExpectation,
) {
  const requestMatches = tasks.filter((candidate) => (
    candidate.approvalRequestId === expectation.approvalRequestId
  ));
  const exactMatches = requestMatches.filter((candidate) => (
    candidate.sourceContext === expectation.sourceContext
    && candidate.sourceResourceType.toLowerCase() === expectation.resourceType
    && candidate.sourceResourceId === expectation.resourceId
    && candidate.approvalType === expectation.approvalType
    && expectation.titles.includes(candidate.title)
  ));
  if (requestMatches.length > 1) {
    throw new Error(
      `multiple exact pending approval tasks for ${expectation.resourceType} ${expectation.resourceId}`,
    );
  }
  if (exactMatches.length === 0 && requestMatches.length > 0) {
    throw new Error(
      `inconsistent pending approval task for request ${expectation.approvalRequestId}`,
    );
  }
  return exactMatches[0];
}

export function approvalTaskDiscoveryExpired(pendingWithoutTaskSince: number, now: number) {
  return now - pendingWithoutTaskSince >= APPROVAL_TASK_DISCOVERY_GRACE_MS;
}

export function assertConsistentApprovalDecision(
  decision: ApprovalDecisionContract,
  task: PendingApprovalTaskContract,
  expectation: ApprovalTaskExpectation,
) {
  if (
    decision.taskId !== task.taskId
    || decision.approvalRequestId !== expectation.approvalRequestId
    || task.approvalRequestId !== expectation.approvalRequestId
    || decision.status !== 'APPROVED'
    || decision.sourceContext !== expectation.sourceContext
    || decision.sourceResourceType.toLowerCase() !== expectation.resourceType
    || decision.sourceResourceId !== expectation.resourceId
    || task.evidenceSchema !== 'model-approval-evidence/v1'
    || !task.evidenceDigest?.match(/^[0-9a-f]{64}$/)
    || !task.targetDigest?.match(/^[0-9a-f]{64}$/)
    || decision.evidenceSchema !== task.evidenceSchema
    || decision.evidenceDigest !== task.evidenceDigest
    || decision.targetDigest !== task.targetDigest
  ) {
    throw new Error(
      `${expectation.resourceType} ${expectation.resourceId} returned an inconsistent approval decision`,
    );
  }
}
