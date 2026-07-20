import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Badge,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogBody,
  DialogContent,
  DialogSurface,
  DialogTitle,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  Select,
  Text,
  Textarea,
  Title2,
  Title3,
} from '@fluentui/react-components';
import { useActivateModal } from '@fluentui/react-tabster';
import {
  CheckmarkCircle20Regular,
  ShieldCheckmark20Regular,
} from '@fluentui/react-icons';
import { AsyncState } from './async-state';
import { dialogBackdropMotion, dialogSurfaceMotion } from './dialog-motion';
import {
  decideApprovalTask,
  decidePermissionRequest,
  changePlatformTenantStatus,
  createPlatformTenant,
  updatePlatformTenant,
  listApprovalTasks,
  listIamPermissions,
  listIamRoles,
  pagePlatformTenants,
  listPlatformTenantFeatures,
  listPermissionRequests,
  listRolePermissions,
  replaceRolePermissions,
  submitDelegatedTenantAction,
  updatePlatformTenantFeature,
  listModelProviders,
  listModelConfigs,
  createModelProvider,
  createModelConfig,
  getApprovalTask,
  updateModelConfig,
  disableModelProvider,
  disableModelConfig,
  testModelConnectivity,
} from './api';
import { PAGE_SIZE, Pagination } from './pagination';
import { ApiError } from './types';
import type {
  ApprovalDecisionResult,
  ApprovalTask,
  IamPermission,
  IamRole,
  IamTenant,
  TenantFeature,
  PermissionRequestItem,
  RequestStatus,
  ModelProviderItem,
  ModelConfigItem,
  CurrentUser,
} from './types';

function PageHeading({ title, description }: { title: string; description: string; marker?: string }) {
  return <header className="page-heading"><div><Title2 as="h1">{title}</Title2><Text>{description}</Text></div></header>;
}

function Feedback({ status, error, onRetry }: { status: RequestStatus; error?: Error; onRetry?: () => void }) {
  return <AsyncState status={status} error={error} onRetry={onRetry} />;
}

const PLATFORM_ONLY_PERMISSIONS = new Set([
  'identity-access:platform-admin:use',
  'identity-access:iam-menu:manage',
  'identity-access:tenant-feature:manage',
  'identity-access:system-setting:manage',
  'identity-access:tenant-delegation:manage',
  'summary-ops:platform-overview:read',
]);

type TenantStatusChangeDraft = {
  tenantId: string;
  tenantCode: string;
  tenantName: string;
  oldStatus: 'ACTIVE' | 'DISABLED';
  newStatus: 'ACTIVE' | 'DISABLED';
};

type DelegatedTenantIntent = {
  targetTenantId: string;
  actionType: 'ASSIGN_TENANT_ADMIN' | 'REPLACE_ROLE_PERMISSIONS';
  targetRoleId: string;
  targetUserId?: string;
  permissionCodes: string[];
  reason: string;
  idempotencyKey: string;
};

type TenantFeatureChangeDraft = {
  tenantId: string;
  tenantName: string;
  featureCode: string;
  featureName: string;
  oldEnabled: boolean;
  newEnabled: boolean;
};

type PermissionDiffItem = { code: string; name: string };

type RolePermissionChangeDraft = {
  roleId: string;
  roleName: string;
  permissionCodes: string[];
  added: PermissionDiffItem[];
  removed: PermissionDiffItem[];
};

function errorTraceId(error?: Error) {
  if (!error || !('traceId' in error)) return '';
  return String((error as Error & { traceId?: string }).traceId || '');
}

function InlineMutationError({ error }: { error?: Error }) {
  if (!error) return null;
  const traceId = errorTraceId(error);
  return <MessageBar intent="error"><MessageBarBody>{error.message}{traceId && <span className="trace-hint">错误追踪：{traceId}</span>}</MessageBarBody></MessageBar>;
}

const MODEL_APPROVAL_EVIDENCE_SCHEMA = 'model-approval-evidence/v1';
const SHA256_DIGEST = /^[0-9a-f]{64}$/;
const EVIDENCE_OBJECT_FIELDS = new Set('providerId providerCode providerKind providerName endpoint authType credentialConfigured outboundMode modelConfigId modelCode modelName modelType contextWindow embeddingDimension inputPricePer1k outputPricePer1k maxTokens temperature topP dimensions'.split(' '));

type ApprovalEvidenceReady = {
  ready: true;
  facts: Record<string, unknown>;
  before: Record<string, unknown> | null;
  after: Record<string, unknown>;
  target: Record<string, unknown>;
  changedFields: string[];
};

type ApprovalEvidenceReadiness = ApprovalEvidenceReady | { ready: false; message: string };

function evidenceRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function approvalEvidenceReadiness(task: ApprovalTask): ApprovalEvidenceReadiness {
  if (!task.evidenceSchema || !task.evidenceDigest || !task.targetDigest || !task.evidence) {
    return { ready: false, message: '该任务缺少可验证的审批证据，仅允许查看；请重新发起变更。' };
  }
  if (task.evidenceSchema !== MODEL_APPROVAL_EVIDENCE_SCHEMA) {
    return { ready: false, message: `不支持的审批证据版本 ${task.evidenceSchema}，已阻止决策。` };
  }
  if (!SHA256_DIGEST.test(task.evidenceDigest) || !SHA256_DIGEST.test(task.targetDigest)) {
    return { ready: false, message: '审批证据摘要格式无效，已阻止决策。' };
  }
  const facts = evidenceRecord(task.evidence);
  const beforeValue = facts?.before;
  const before = beforeValue == null ? null : evidenceRecord(beforeValue);
  const after = evidenceRecord(facts?.after);
  const target = evidenceRecord(facts?.target);
  const changedFields = facts?.changedFields;
  if (!facts || (beforeValue != null && !before) || !after || !target
    || !Array.isArray(changedFields) || changedFields.length === 0
    || changedFields.some((field) => typeof field !== 'string' || !field.trim())) {
    return { ready: false, message: '审批证据结构不完整，已阻止决策。' };
  }
  if (facts.approvalRequestId !== task.approvalRequestId
    || facts.approvalType !== task.approvalType
    || facts.sourceContext !== task.sourceContext
    || facts.resourceType !== task.sourceResourceType
    || facts.resourceId !== task.sourceResourceId
    || facts.requesterId !== task.requesterId
    || facts.riskLevel !== 'HIGH_RISK') {
    return { ready: false, message: '审批证据与当前任务不匹配，已阻止决策。' };
  }
  const providerEvidence = task.sourceContext === 'model-hub'
    && task.sourceResourceType === 'model_provider'
    && task.approvalType === 'model_provider_change'
    && facts.action === 'MODEL_PROVIDER_CREATE'
    && facts.expectedPostDecisionState === 'ACTIVE'
    && before === null;
  const configAction = facts.action === 'MODEL_CONFIG_CREATE' || facts.action === 'MODEL_CONFIG_UPDATE';
  const configEvidence = task.sourceContext === 'model-hub'
    && task.sourceResourceType === 'model_config'
    && task.approvalType === 'model_config_change'
    && configAction
    && facts.expectedPostDecisionState === 'INACTIVE+NOT_TESTED'
    && facts.modelConfigId === task.sourceResourceId
    && (facts.action === 'MODEL_CONFIG_CREATE' ? before === null : before !== null);
  if (!providerEvidence && !configEvidence) {
    return { ready: false, message: '证据类型或动作不受支持，仅允许查看。' };
  }
  return {
    ready: true,
    facts,
    before: before ?? null,
    after,
    target,
    changedFields: changedFields.map((field) => String(field).trim()),
  };
}

function evidenceValue(value: unknown): string {
  if (value == null) return '无';
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (typeof value === 'string') return value || '空字符串';
  if (typeof value === 'number') return String(value);
  if (Array.isArray(value)) return value.length ? value.map(evidenceValue).join('、') : '空列表';
  const record = evidenceRecord(value);
  if (record) {
    const facts = Object.entries(record)
      .filter(([key]) => EVIDENCE_OBJECT_FIELDS.has(key))
      .map(([key, child]) => `${key}=${evidenceValue(child)}`);
    return facts.length ? facts.join('；') : '结构化配置';
  }
  return '结构化配置';
}

function evidencePathValue(source: Record<string, unknown> | null, path: string) {
  if (!source) return undefined;
  let value: unknown = source;
  for (const segment of path.split('.')) {
    const row = evidenceRecord(value);
    if (!row) return undefined;
    value = row[segment];
  }
  return value;
}

function ApprovalEvidencePanel({ task, readiness }: {
  task: ApprovalTask;
  readiness: ApprovalEvidenceReadiness;
}) {
  if (!readiness.ready) {
    return <MessageBar intent="error"><MessageBarBody>{readiness.message}</MessageBarBody></MessageBar>;
  }
  return <div className="approval-evidence" aria-label="可验证审批证据">
    <section className="evidence-section">
      <Text weight="semibold">证据身份</Text>
      <dl className="evidence-grid">
        <dt>请求编号</dt><dd>{task.approvalRequestId}</dd>
        <dt>证据版本</dt><dd>{task.evidenceSchema}</dd>
        <dt>证据摘要</dt><dd className="evidence-digest">{task.evidenceDigest}</dd>
        <dt>目标摘要</dt><dd className="evidence-digest">{task.targetDigest}</dd>
        <dt>申请时间</dt><dd>{evidenceValue(readiness.facts.requestedAt)}</dd>
        <dt>变更动作</dt><dd>{evidenceValue(readiness.facts.action)}</dd>
        <dt>决策后预期</dt><dd>{evidenceValue(readiness.facts.expectedPostDecisionState)}</dd>
      </dl>
    </section>
    <section className="evidence-section">
      <Text weight="semibold">逐项变更</Text>
      {readiness.before === null && <Text className="evidence-create-note">新建资源（无历史快照）</Text>}
      <div className="evidence-diff"><table aria-label="审批证据变更明细"><thead><tr><th>字段</th><th>变更前</th><th>变更后</th></tr></thead><tbody>{readiness.changedFields.map((field) => <tr key={field}><td>{field}</td><td>{readiness.before === null ? '新建' : evidenceValue(evidencePathValue(readiness.before, field))}</td><td>{evidenceValue(evidencePathValue(readiness.after, field))}</td></tr>)}</tbody></table></div>
    </section>
  </div>;
}

function approvalDecisionMatches(
  result: ApprovalDecisionResult,
  task: ApprovalTask,
  action: 'APPROVE' | 'REJECT',
) {
  return result.taskId === task.taskId
    && result.approvalRequestId === task.approvalRequestId
    && result.sourceContext === task.sourceContext
    && result.sourceResourceType === task.sourceResourceType
    && result.sourceResourceId === task.sourceResourceId
    && result.evidenceSchema === task.evidenceSchema
    && result.evidenceDigest === task.evidenceDigest
    && result.targetDigest === task.targetDigest
    && result.status === (action === 'APPROVE' ? 'APPROVED' : 'REJECTED');
}

export function DelegatedTenantPage() {
  const [tenants, setTenants] = useState<IamTenant[]>([]);
  const [features, setFeatures] = useState<TenantFeature[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState('');
  const [dialog, setDialog] = useState<'create' | 'edit' | 'status' | 'feature' | 'delegate'>();
  const [statusChange, setStatusChange] = useState<TenantStatusChangeDraft>();
  const [featureChange, setFeatureChange] = useState<TenantFeatureChangeDraft>();
  const [tenantId, setTenantId] = useState('');
  const [actionType, setActionType] = useState<'ASSIGN_TENANT_ADMIN' | 'REPLACE_ROLE_PERMISSIONS'>('ASSIGN_TENANT_ADMIN');
  const [targetUserId, setTargetUserId] = useState('');
  const [targetRoleId, setTargetRoleId] = useState('');
  const [permissionCodes, setPermissionCodes] = useState('');
  const [reason, setReason] = useState('');
  const [delegationIntent, setDelegationIntent] = useState<DelegatedTenantIntent>();
  const [delegationResult, setDelegationResult] = useState<{ status: string; auditEventId: string }>();
  const [delegationError, setDelegationError] = useState<Error>();
  const [reconcilingDelegation, setReconcilingDelegation] = useState(false);
  const [error, setError] = useState<Error>();
  const [loadError, setLoadError] = useState<Error>();
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [tenantCode, setTenantCode] = useState('');
  const [tenantName, setTenantName] = useState('');
  const [planCode, setPlanCode] = useState('STANDARD');
  const tenantCatalogGeneration = useRef(0);
  const tenantCatalogController = useRef<AbortController>();
  const tenantFeatureGeneration = useRef(0);
  const tenantFeatureController = useRef<AbortController>();
  const selectedTenantRef = useRef('');
  const delegationLocked = Boolean(delegationIntent);
  const valid = Boolean(tenantId.trim() && targetRoleId.trim() && reason.trim()
    && (actionType === 'ASSIGN_TENANT_ADMIN' ? targetUserId.trim() : permissionCodes.trim()));

  async function loadTenants(nextPage = page, nextPageSize = pageSize) {
    tenantCatalogController.current?.abort();
    const controller = new AbortController();
    const generation = tenantCatalogGeneration.current + 1;
    tenantCatalogController.current = controller;
    tenantCatalogGeneration.current = generation;
    setLoading(true);
    setLoadError(undefined);
    try {
      const response = await pagePlatformTenants(nextPage, nextPageSize, keyword, statusFilter, controller.signal);
      if (controller.signal.aborted || generation !== tenantCatalogGeneration.current) return;
      setTenants(response.items);
      setTotal(response.total);
      setTotalPages(response.totalPages);
      const currentSelection = selectedTenantRef.current;
      const first = response.items.some((tenant) => tenant.tenantId === currentSelection)
        ? currentSelection
        : response.items[0]?.tenantId || '';
      if (first) {
        await selectTenant(first, false);
      } else {
        tenantFeatureController.current?.abort();
        tenantFeatureGeneration.current += 1;
        selectedTenantRef.current = '';
        setSelectedTenantId('');
        setTenantId('');
        setFeatures([]);
      }
    } catch (cause) {
      if (!controller.signal.aborted && generation === tenantCatalogGeneration.current) {
        setLoadError(cause as Error);
      }
    } finally {
      if (generation === tenantCatalogGeneration.current) setLoading(false);
    }
  }
  // Query state is the dependency surface; loadTenants is intentionally recreated with those values.
  useEffect(() => {
    loadTenants().catch(() => undefined);
    return () => tenantCatalogController.current?.abort();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize, keyword, statusFilter]);
  useEffect(() => () => {
    tenantCatalogController.current?.abort();
    tenantFeatureController.current?.abort();
  }, []);
  async function selectTenant(id: string, manageLoading = true) {
    tenantFeatureController.current?.abort();
    const controller = new AbortController();
    const generation = tenantFeatureGeneration.current + 1;
    tenantFeatureController.current = controller;
    tenantFeatureGeneration.current = generation;
    selectedTenantRef.current = id;
    setSelectedTenantId(id);
    setTenantId(id);
    setFeatures([]);
    setLoadError(undefined);
    if (manageLoading) setLoading(true);
    try {
      const rows = await listPlatformTenantFeatures(id, controller.signal);
      if (!controller.signal.aborted
        && generation === tenantFeatureGeneration.current
        && selectedTenantRef.current === id) {
        setFeatures(rows);
      }
    } catch (cause) {
      if (!controller.signal.aborted
        && generation === tenantFeatureGeneration.current
        && selectedTenantRef.current === id) {
        setLoadError(cause as Error);
      }
    } finally {
      if (manageLoading && generation === tenantFeatureGeneration.current) setLoading(false);
    }
  }
  function closeDialog() {
    const preserveDelegation = dialog === 'delegate' && delegationIntent;
    setDialog(undefined);
    setStatusChange(undefined);
    setFeatureChange(undefined);
    setError(undefined);
    if (!preserveDelegation) {
      setDelegationResult(undefined);
      setDelegationError(undefined);
    }
  }
  function openTenantDialog(mode: 'create' | 'edit' | 'status', tenant?: IamTenant) {
    const selected = tenant || tenants.find((row) => row.tenantId === selectedTenantId);
    if (tenant && mode !== 'status') {
      tenantFeatureController.current?.abort(); tenantFeatureGeneration.current += 1;
      selectedTenantRef.current = tenant.tenantId; setSelectedTenantId(tenant.tenantId); setTenantId(tenant.tenantId); setFeatures([]);
    }
    if (mode === 'status' && selected) {
      const oldStatus = selected.status === 'ACTIVE' ? 'ACTIVE' : 'DISABLED';
      setStatusChange({
        tenantId: selected.tenantId,
        tenantCode: selected.code,
        tenantName: selected.name,
        oldStatus,
        newStatus: oldStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
      });
    }
    setTenantCode(mode === 'create' ? '' : selected?.code || ''); setTenantName(mode === 'create' ? '' : selected?.name || ''); setPlanCode(mode === 'create' ? 'STANDARD' : selected?.planCode || 'STANDARD'); setReason(''); setError(undefined); setDialog(mode);
  }
  function openDelegateDialog() {
    setError(undefined);
    if (delegationIntent) {
      setTenantId(delegationIntent.targetTenantId);
      setActionType(delegationIntent.actionType);
      setTargetUserId(delegationIntent.targetUserId || '');
      setTargetRoleId(delegationIntent.targetRoleId);
      setPermissionCodes(delegationIntent.permissionCodes.join(', '));
      setReason(delegationIntent.reason);
    } else {
      setTargetUserId('');
      setTargetRoleId('');
      setPermissionCodes('');
      setReason('');
      setDelegationResult(undefined);
      setDelegationError(undefined);
    }
    setDialog('delegate');
  }
  function openFeatureDialog(feature: TenantFeature, enabled: boolean) {
    const targetTenantId = selectedTenantRef.current;
    const targetTenant = tenants.find((tenant) => tenant.tenantId === targetTenantId);
    if (!targetTenantId || !targetTenant || enabled === feature.enabled) return;
    setFeatureChange({
      tenantId: targetTenantId,
      tenantName: targetTenant.name,
      featureCode: feature.featureCode,
      featureName: feature.featureName,
      oldEnabled: feature.enabled,
      newEnabled: enabled,
    });
    setReason(''); setError(undefined); setDialog('feature');
  }
  async function saveTenant() {
    setSubmitting(true); setError(undefined);
    try {
      if (dialog === 'create') await createPlatformTenant({ code: tenantCode, name: tenantName, planCode });
      else if (dialog === 'edit') await updatePlatformTenant(selectedTenantId, { name: tenantName, planCode });
      else if (dialog === 'status' && statusChange) await changePlatformTenantStatus(statusChange.tenantId, statusChange.newStatus, reason.trim());
      setDialog(undefined); setStatusChange(undefined); await loadTenants();
    } catch (cause) { setError(cause as Error); }
    finally { setSubmitting(false); }
  }
  async function confirmFeatureChange() {
    if (!featureChange || !reason.trim()) return;
    setSubmitting(true); setError(undefined);
    try {
      await updatePlatformTenantFeature(featureChange.tenantId, featureChange.featureCode, featureChange.newEnabled, reason.trim());
      if (selectedTenantRef.current === featureChange.tenantId) {
        setFeatures((rows) => rows.map((row) => row.featureCode === featureChange.featureCode ? { ...row, enabled: featureChange.newEnabled } : row));
      }
      setDialog(undefined); setFeatureChange(undefined);
    } catch (cause) { setError(cause as Error); }
    finally { setSubmitting(false); }
  }
  async function submit() {
    if (!valid) return;
    let frozenIntent = delegationIntent;
    if (!frozenIntent) {
      try {
        frozenIntent = {
          targetTenantId: tenantId.trim(),
          actionType,
          targetRoleId: targetRoleId.trim(),
          targetUserId: actionType === 'ASSIGN_TENANT_ADMIN' ? targetUserId.trim() : undefined,
          permissionCodes: actionType === 'REPLACE_ROLE_PERMISSIONS'
            ? permissionCodes.split(/[,，]/).map((item) => item.trim()).filter(Boolean)
            : [],
          reason: reason.trim(),
          idempotencyKey: crypto.randomUUID(),
        };
        setDelegationIntent(frozenIntent);
      } catch (cause) {
        setDelegationError(cause as Error);
        return;
      }
    }
    setSubmitting(true); setDelegationError(undefined); setDelegationResult(undefined);
    try {
      const response = await submitDelegatedTenantAction(frozenIntent.targetTenantId, {
        actionType: frozenIntent.actionType,
        targetRoleId: frozenIntent.targetRoleId,
        targetUserId: frozenIntent.targetUserId,
        permissionCodes: frozenIntent.permissionCodes,
        reason: frozenIntent.reason,
        idempotencyKey: frozenIntent.idempotencyKey,
      });
      if (!response.delegationActionId?.trim()
        || response.status !== 'SUCCEEDED'
        || !response.auditEventId?.trim()) {
        throw new Error('代管响应缺少可验证的成功证据，已保留原请求编号以便安全重试。');
      }
      setDelegationResult(response);
    } catch (cause) { setDelegationError(cause as Error); }
    finally { setSubmitting(false); }
  }

  async function reconcileDelegationAndStartNext() {
    if (!delegationIntent || !delegationResult || reconcilingDelegation) return;
    setReconcilingDelegation(true);
    setDelegationError(undefined);
    try {
      const rows = await listPlatformTenantFeatures(delegationIntent.targetTenantId);
      if (selectedTenantRef.current === delegationIntent.targetTenantId) setFeatures(rows);
      setDelegationIntent(undefined);
      setDelegationResult(undefined);
      setTargetUserId('');
      setTargetRoleId('');
      setPermissionCodes('');
      setReason('');
      setActionType('ASSIGN_TENANT_ADMIN');
      setTenantId(selectedTenantRef.current);
    } catch (cause) {
      setDelegationError(cause as Error);
    } finally {
      setReconcilingDelegation(false);
    }
  }

  const dialogTitle = dialog === 'create' ? '新建租户'
    : dialog === 'edit' ? '修改租户'
      : dialog === 'status' && statusChange ? `确认${statusChange.newStatus === 'ACTIVE' ? '启用' : '停用'}租户`
        : dialog === 'feature' && featureChange ? `确认${featureChange.newEnabled ? '启用' : '停用'}产品能力`
          : '应急代管';

  return <main className="page-stack">
    <PageHeading title="租户管理" description="查看租户状态、开通产品能力；应急代管作为选中租户的独立高风险操作。" />
    {!dialog && <InlineMutationError error={error} />}
    <div className="toolbar"><div className="toolbar-search"><Input aria-label="搜索租户名称或编码" placeholder="搜索租户名称或编码" value={keyword} onChange={(_, data) => { setPage(1); setKeyword(data.value); }} /><Select aria-label="租户状态" value={statusFilter} onChange={(event) => { setPage(1); setStatusFilter(event.target.value); }}><option value="">全部状态</option><option value="ACTIVE">正常</option><option value="DISABLED">已停用</option></Select></div><Button appearance="primary" onClick={() => openTenantDialog('create')}>新建租户</Button></div>
    <AsyncState status={loading ? 'loading' : loadError ? 'error' : 'success'} error={loadError} loadingLabel="正在读取租户配置" onRetry={() => { loadTenants().catch(() => undefined); }} />
    {!loading && !loadError && tenants.length === 0 && <section className="empty-state"><CheckmarkCircle20Regular /><Title3>没有匹配的租户</Title3><Text>调整搜索条件，或新建一个租户。</Text></section>}
    {!loading && !loadError && tenants.length > 0 && <section className="master-detail"><div className="surface data-region"><div className="table-wrap"><table className="tenant-table" aria-label="租户列表"><thead><tr><th>租户名称</th><th>租户编码</th><th>套餐</th><th>状态</th><th>操作</th></tr></thead><tbody>{tenants.map((tenant) => <tr key={tenant.tenantId}><td>{tenant.name}</td><td>{tenant.code}</td><td>{tenant.planCode || '-'}</td><td>{tenant.status === 'ACTIVE' ? '正常' : '已停用'}</td><td><div className="row-actions" role="group" aria-label={`${tenant.name}操作`}><Button size="small" aria-label={`配置 ${tenant.name}`} appearance={selectedTenantId === tenant.tenantId ? 'primary' : 'subtle'} onClick={() => selectTenant(tenant.tenantId)}>配置</Button><Button size="small" aria-label={`修改 ${tenant.name}`} onClick={() => openTenantDialog('edit', tenant)}>修改</Button><Button size="small" aria-label={`${tenant.status === 'ACTIVE' ? '停用' : '启用'} ${tenant.name}`} onClick={() => openTenantDialog('status', tenant)}>{tenant.status === 'ACTIVE' ? '停用' : '启用'}</Button></div></td></tr>)}</tbody></table></div><Pagination page={page} pageSize={pageSize} total={total} totalPages={totalPages} onChange={(nextPage, nextSize) => { setPage(nextPage); setPageSize(nextSize); }} /></div>
      <aside className="surface detail-panel"><div><div><Title3>产品能力</Title3><Text>{tenants.find((tenant) => tenant.tenantId === selectedTenantId)?.name}</Text></div><Button appearance="subtle" onClick={openDelegateDialog}>应急代管</Button></div><div className="feature-list">{features.map((feature) => <div key={feature.featureCode}><div><strong>{feature.featureName}</strong><span>{feature.featureCode} · {feature.source}</span></div><Checkbox aria-label={`${feature.featureName}产品能力`} label={feature.enabled ? '已启用' : '未启用'} checked={feature.enabled} disabled={submitting} onChange={(_, data) => openFeatureDialog(feature, data.checked === true)} /></div>)}</div></aside>
    </section>}
    <Dialog open={Boolean(dialog)} surfaceMotion={dialogSurfaceMotion} onOpenChange={(_, data) => { if (!data.open && !submitting) closeDialog(); }}><DialogSurface backdropMotion={dialogBackdropMotion}><DialogBody><DialogTitle>{dialogTitle}</DialogTitle><DialogContent>
    {(dialog === 'create' || dialog === 'edit') && <div className="dialog-form">{dialog === 'create' && <Field label="租户编码" required><Input value={tenantCode} onChange={(_, data) => setTenantCode(data.value)} /></Field>}<Field label="租户名称" required><Input value={tenantName} onChange={(_, data) => setTenantName(data.value)} /></Field><Field label="套餐" required><Select value={planCode} onChange={(event) => setPlanCode(event.target.value)}><option value="STANDARD">标准版</option><option value="PROFESSIONAL">专业版</option><option value="ENTERPRISE">企业版</option></Select></Field></div>}
    {dialog === 'status' && statusChange && <div className="dialog-form risk-confirmation"><dl className="change-summary"><dt>目标租户</dt><dd>{statusChange.tenantName}</dd><dt>租户编码</dt><dd>{statusChange.tenantCode}</dd><dt>原状态</dt><dd>{statusChange.oldStatus === 'ACTIVE' ? '正常' : '已停用'}</dd><dt>新状态</dt><dd>{statusChange.newStatus === 'ACTIVE' ? '正常' : '已停用'}</dd></dl><MessageBar intent="warning"><MessageBarBody>{statusChange.newStatus === 'DISABLED' ? '停用后将阻止用户登录和租户业务访问，请确认业务方已收到通知。' : '启用后将恢复用户登录和租户业务访问，请确认安全与合同条件均已满足。'}</MessageBarBody></MessageBar><Field label="变更原因" required><Textarea maxLength={300} value={reason} onChange={(_, data) => setReason(data.value)} /></Field></div>}
    {dialog === 'feature' && featureChange && <div className="dialog-form risk-confirmation"><dl className="change-summary"><dt>目标租户</dt><dd>{featureChange.tenantName}</dd><dt>产品能力</dt><dd>{featureChange.featureName}</dd><dt>能力编码</dt><dd>{featureChange.featureCode}</dd><dt>原状态</dt><dd>{featureChange.oldEnabled ? '已启用' : '未启用'}</dd><dt>新状态</dt><dd>{featureChange.newEnabled ? '已启用' : '未启用'}</dd></dl><MessageBar intent="warning"><MessageBarBody>{featureChange.newEnabled ? '启用后，该租户用户可按权限使用此能力，并可能产生新的数据与资源消耗。' : '停用后，依赖该能力的租户用户将无法继续使用相关功能，请先完成业务下线。'}</MessageBarBody></MessageBar><Field label="变更原因" required><Textarea maxLength={300} value={reason} onChange={(_, data) => setReason(data.value)} /></Field></div>}
    {dialog === 'delegate' && <div className="dialog-form"><Text>仅用于管理员恢复和授权纠偏，所有操作强制记录原因与审计事件。</Text>
      <div className="form-grid">
        <Field label="目标租户"><Input value={tenants.find((tenant) => tenant.tenantId === tenantId)?.name || tenantId} disabled /></Field>
        <Field label="代管动作" required><Select aria-label="代管动作" value={actionType} disabled={delegationLocked} onChange={(event) => setActionType(event.target.value as typeof actionType)}><option value="ASSIGN_TENANT_ADMIN">指定租户管理员</option><option value="REPLACE_ROLE_PERMISSIONS">替换角色权限</option></Select></Field>
        {actionType === 'ASSIGN_TENANT_ADMIN' && <Field label="目标用户 ID" required><Input value={targetUserId} disabled={delegationLocked} onChange={(_, data) => setTargetUserId(data.value)} /></Field>}
        <Field label="目标角色 ID" required><Input value={targetRoleId} disabled={delegationLocked} onChange={(_, data) => setTargetRoleId(data.value)} /></Field>
        {actionType === 'REPLACE_ROLE_PERMISSIONS' && <Field label="权限编码" hint="多个编码使用英文逗号分隔" required><Input value={permissionCodes} disabled={delegationLocked} onChange={(_, data) => setPermissionCodes(data.value)} /></Field>}
      </div>
      <Field label="原因" required><Textarea value={reason} disabled={delegationLocked} onChange={(_, data) => setReason(data.value)} /></Field>
      {delegationIntent && !delegationResult && <MessageBar intent="warning"><MessageBarBody>本次操作的目标、理由和请求编号已冻结。结果确认前只能使用原请求编号重试，避免超时后重复执行或改写业务意图。</MessageBarBody></MessageBar>}
      {delegationResult && <MessageBar intent="success"><MessageBarBody>代管状态 {delegationResult.status}，审计事件 <strong>{delegationResult.auditEventId}</strong>。开始下一项操作前必须重新读取目标租户。</MessageBarBody></MessageBar>}
    </div>}
    <InlineMutationError error={dialog === 'delegate' ? delegationError : error} />
    </DialogContent><DialogActions><Button disabled={submitting || reconcilingDelegation} onClick={closeDialog}>取消</Button>{dialog === 'delegate' ? delegationResult ? <Button appearance="primary" disabled={reconcilingDelegation} onClick={() => { void reconcileDelegationAndStartNext(); }}>{reconcilingDelegation ? '正在重新读取' : '重新读取后开始下一项'}</Button> : <Button appearance="primary" disabled={!valid || submitting || reconcilingDelegation} onClick={submit}>{submitting ? '正在提交' : delegationIntent ? '使用原请求编号重试' : '提交代管动作'}</Button> : dialog === 'feature' ? <Button appearance="primary" disabled={!featureChange || !reason.trim() || submitting} onClick={confirmFeatureChange}>{featureChange?.newEnabled ? '确认启用' : '确认停用'}</Button> : dialog === 'status' ? <Button appearance="primary" disabled={!statusChange || !reason.trim() || submitting} onClick={saveTenant}>{statusChange?.newStatus === 'ACTIVE' ? '确认启用租户' : '确认停用租户'}</Button> : <Button appearance="primary" disabled={submitting || !tenantName || !planCode || (dialog === 'create' && !tenantCode)} onClick={saveTenant}>确认</Button>}</DialogActions></DialogBody></DialogSurface></Dialog>
  </main>;
}

export function ApprovalCenterPage({ user }: { user: CurrentUser }) {
  const [tasks, setTasks] = useState<ApprovalTask[]>([]);
  const [comment, setComment] = useState('');
  const [decisions, setDecisions] = useState<Record<string, ApprovalDecisionResult>>({});
  const [status, setStatus] = useState<RequestStatus>('loading');
  const [error, setError] = useState<Error>();
  const [selected, setSelected] = useState<{ task: ApprovalTask; action: 'APPROVE' | 'REJECT' | 'VIEW' }>();
  const [submittingId, setSubmittingId] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [riskFilter, setRiskFilter] = useState('');
  const [sourceFilter, setSourceFilter] = useState('');
  const [keyword, setKeyword] = useState('');
  const queryGeneration = useRef(0);
  const queryController = useRef<AbortController>();
  const latestLoad = useRef<(preserveSelection?: boolean) => Promise<void>>();
  const detailPanel = useRef<HTMLElement>(null);
  const detailTrigger = useRef<HTMLElement>();
  const load = useCallback(async (preserveSelection = false) => {
    queryController.current?.abort();
    const controller = new AbortController();
    const generation = queryGeneration.current + 1;
    queryController.current = controller;
    queryGeneration.current = generation;
    setTasks([]);
    if (!preserveSelection) setSelected(undefined);
    setTotal(0);
    setTotalPages(0);
    setStatus('loading');
    setError(undefined);
    try {
      const value = await listApprovalTasks(
        { page, pageSize, status: statusFilter, riskLevel: riskFilter, sourceContext: sourceFilter, keyword },
        controller.signal,
      );
      if (controller.signal.aborted || generation !== queryGeneration.current) return;
      setTasks(value.items);
      setTotal(value.total);
      setTotalPages(value.totalPages);
      setStatus(value.total ? 'success' : 'empty');
    } catch (cause) {
      if (!controller.signal.aborted && generation === queryGeneration.current) {
        setSelected(undefined);
        setError(cause as Error);
        setStatus('error');
      }
    }
  }, [keyword, page, pageSize, riskFilter, sourceFilter, statusFilter]);
  latestLoad.current = load;
  useEffect(() => {
    void load();
    return () => queryController.current?.abort();
  }, [load]);
  useEffect(() => {
    const target = selected ? detailPanel.current : detailTrigger.current;
    if (target?.isConnected) target.focus();
  }, [selected]);
  function openTask(task: ApprovalTask, action: 'APPROVE' | 'REJECT' | 'VIEW', trigger: HTMLElement) {
    detailTrigger.current = trigger;
    setSelected({ task, action }); setComment(''); setError(undefined);
  }
  async function decide(task: ApprovalTask, action: 'APPROVE' | 'REJECT') {
    const reason = comment.trim(); if (!reason) return;
    const readiness = approvalEvidenceReadiness(task);
    if (!readiness.ready || !task.evidenceDigest) {
      setError(new Error(readiness.ready ? '审批证据摘要缺失，已阻止决策。' : readiness.message));
      return;
    }
    setSubmittingId(task.taskId); setError(undefined);
    try {
      const result = await decideApprovalTask(task.taskId, action, reason, task.evidenceDigest);
      if (!approvalDecisionMatches(result, task, action)) throw new Error('审批结果与当前证据不一致，未记录成功状态；任务列表已强制刷新。');
      setDecisions((value) => ({ ...value, [task.taskId]: result }));
      setSelected(undefined); setComment(''); await latestLoad.current?.();
    }
    catch (cause) {
      try {
        const current = await getApprovalTask(task.taskId);
        if (approvalDecisionMatches(current as ApprovalDecisionResult, task, action)) {
          setDecisions((value) => ({ ...value, [task.taskId]: current as ApprovalDecisionResult }));
          setSelected(undefined); setComment(''); return;
        }
      } catch { /* The list refresh below becomes the fail-closed authority. */ }
      await latestLoad.current?.(true);
      setError(cause as Error);
    }
    finally { setSubmittingId(undefined); }
  }
  const traceId = error && 'traceId' in error ? String((error as Error & { traceId?: string }).traceId || '') : '';
  const canDecide = user.permissionCodes.includes('approval-center:task:decide');
  return <main className="page-stack"><PageHeading title="审批中心" description="集中处理高风险配置变更；默认按创建时间倒序，筛选、排序和分页由服务端执行。" marker="理由必填" />
    <div className="toolbar"><div className="toolbar-search"><Input aria-label="搜索审批事项或资源编号" placeholder="搜索审批事项或资源编号" value={keyword} onChange={(_, data) => { setPage(1); setKeyword(data.value); }} /><Select aria-label="审批状态" value={statusFilter} onChange={(event) => { setPage(1); setStatusFilter(event.target.value); }}><option value="">全部状态</option><option value="PENDING">待审批</option><option value="APPROVED">已通过</option><option value="REJECTED">已拒绝</option><option value="CANCELLED">已撤销</option></Select><Select aria-label="风险等级" value={riskFilter} onChange={(event) => { setPage(1); setRiskFilter(event.target.value); }}><option value="">全部风险</option><option value="HIGH_RISK">高风险</option><option value="NORMAL">常规</option></Select><Select aria-label="业务来源" value={sourceFilter} onChange={(event) => { setPage(1); setSourceFilter(event.target.value); }}><option value="">全部来源</option><option value="model-hub">模型中心</option><option value="identity-access">身份与权限</option><option value="app-studio">应用治理</option></Select></div></div><Feedback status={status} error={selected ? undefined : error} onRetry={() => { void load(); }} />
    {status === 'empty' && <section className="empty-state"><CheckmarkCircle20Regular /><Title3>暂无待审批任务</Title3><Text>当前授权范围内没有需要处理的风险变更。</Text></section>}
    {tasks.length > 0 && <section className="master-detail"><div className="surface data-region"><div className="table-wrap"><table aria-label="审批任务列表"><thead><tr><th>审批事项</th><th>申请人</th><th>变更目标</th><th>影响</th><th>状态</th><th>操作</th></tr></thead><tbody>{tasks.map((task) => {
      const effectiveStatus = decisions[task.taskId]?.status || task.status;
      const isRequester = task.requesterId === user.userId;
      const evidenceReadiness = approvalEvidenceReadiness(task);
      const actionHint = isRequester ? '本人申请，需由其他管理员处理' : !canDecide ? '当前账号仅可查看审批任务' : !evidenceReadiness.ready ? evidenceReadiness.message : undefined;
      const actionHintId = actionHint ? `approval-action-hint-${task.taskId}` : undefined;
      const actionDisabled = effectiveStatus !== 'PENDING' || Boolean(actionHint);
      return <tr key={task.taskId}><td><strong>{task.title}</strong><span className="cell-secondary">任务编号 {task.taskId}</span><span className="cell-secondary">追踪编号 {task.traceId || '-'}</span></td><td>{task.requesterId}</td><td>{task.sourceResourceType} / {task.sourceResourceId}<span className="cell-secondary">{task.sourceContextLabel || task.sourceContext}</span></td><td><Badge aria-label={`风险等级：${task.riskLevelLabel || task.riskLevel}`} color={task.riskLevel === 'HIGH_RISK' ? 'danger' : 'subtle'}>{task.riskLevelLabel || task.riskLevel}</Badge></td><td>{decisions[task.taskId]?.status || task.statusLabel || task.status}</td><td><div className="approval-action-cell"><div className="row-actions"><Button appearance="subtle" aria-label={`查看 ${task.taskId}`} onClick={(event) => openTask(task, 'VIEW', event.currentTarget)}>查看</Button><Button disabled={actionDisabled} aria-describedby={actionHintId} aria-label={`拒绝 ${task.taskId}`} onClick={(event) => openTask(task, 'REJECT', event.currentTarget)}>拒绝</Button><Button appearance="primary" disabled={actionDisabled} aria-describedby={actionHintId} aria-label={`通过 ${task.taskId}`} onClick={(event) => openTask(task, 'APPROVE', event.currentTarget)}>通过</Button></div>{actionHint && <Text id={actionHintId} className="approval-action-hint">{actionHint}</Text>}</div></td></tr>;
    })}</tbody></table></div><Pagination page={page} pageSize={pageSize} total={total} totalPages={totalPages} onChange={(nextPage, nextSize) => { setPage(nextPage); setPageSize(nextSize); }} /></div>
      <aside ref={detailPanel} tabIndex={-1} aria-label="审批任务详情" className="surface detail-panel approval-panel">{selected ? (() => { const evidenceReadiness = approvalEvidenceReadiness(selected.task); return <><div><div><Title3>{selected.action === 'VIEW' ? '审批详情' : selected.action === 'APPROVE' ? '确认通过' : '确认拒绝'}</Title3><Text>{selected.task.title}</Text></div><Button appearance="subtle" onClick={() => { setSelected(undefined); setError(undefined); }}>{selected.action === 'VIEW' ? '关闭' : '取消'}</Button></div><dl><dt>任务编号</dt><dd>{selected.task.taskId}</dd><dt>申请人</dt><dd>{selected.task.requesterId}</dd><dt>变更目标</dt><dd>{selected.task.sourceResourceType} / {selected.task.sourceResourceId}</dd><dt>影响等级</dt><dd>{selected.task.riskLevelLabel || selected.task.riskLevel}</dd><dt>业务来源</dt><dd>{selected.task.sourceContextLabel || selected.task.sourceContext}</dd><dt>追踪编号</dt><dd>{selected.task.traceId || '-'}</dd></dl><ApprovalEvidencePanel task={selected.task} readiness={evidenceReadiness} />{selected.action === 'VIEW' ? <MessageBar intent={selected.task.requesterId === user.userId ? 'warning' : 'info'}><MessageBarBody>{selected.task.requesterId === user.userId ? '此任务由当前账号发起，需由其他管理员完成决策。' : !canDecide ? '当前账号仅可查看审批任务，决策操作由具备审批权限的管理员完成。' : evidenceReadiness.ready ? '请逐项核对证据、差异和目标摘要后再执行决策。' : '该历史任务仅可查看，不能执行决策。'}</MessageBarBody></MessageBar> : <><Field label="审批原因" required><Textarea value={comment} onChange={(_, data) => setComment(data.value)} /></Field>{error && <MessageBar intent="error"><MessageBarBody>{error.message}{traceId && <span className="trace-hint">错误追踪：{traceId}</span>}</MessageBarBody></MessageBar>}<Button appearance={selected.action === 'APPROVE' ? 'primary' : 'secondary'} disabled={!evidenceReadiness.ready || !comment.trim() || submittingId === selected.task.taskId} onClick={() => { if (selected.action !== 'VIEW') void decide(selected.task, selected.action); }}>{selected.action === 'APPROVE' ? '确认通过' : '确认拒绝'}</Button></>}</>; })() : <div className="detail-empty"><ShieldCheckmark20Regular /><Text>从列表选择任务查看详情或执行决策</Text></div>}</aside>
    </section>}
  </main>;
}

export function RolePermissionPage() {
  const [roles, setRoles] = useState<IamRole[]>([]);
  const [permissions, setPermissions] = useState<IamPermission[]>([]);
  const [selectedRoleId, setSelectedRoleId] = useState('');
  const [selectedCodes, setSelectedCodes] = useState<Set<string>>(new Set());
  const [originalCodes, setOriginalCodes] = useState<Set<string>>(new Set());
  const [permissionRoleId, setPermissionRoleId] = useState('');
  const [permissionsLoading, setPermissionsLoading] = useState(false);
  const [permissionLoadError, setPermissionLoadError] = useState<Error>();
  const [permissionLoadAttempt, setPermissionLoadAttempt] = useState(0);
  const [status, setStatus] = useState<RequestStatus>('loading');
  const [error, setError] = useState<Error>();
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [change, setChange] = useState<RolePermissionChangeDraft>();
  const [reason, setReason] = useState('');
  useEffect(() => { Promise.all([listIamRoles(), listIamPermissions()]).then(([roleRows, permissionRows]) => { const manageableRoles = roleRows.filter((role) => role.type.toLowerCase() === 'business'); const tenantAssignable = permissionRows.filter((permission) => !PLATFORM_ONLY_PERMISSIONS.has(permission.code)); setRoles(manageableRoles); setPermissions(tenantAssignable); setSelectedRoleId(manageableRoles[0]?.roleId || ''); setStatus(manageableRoles.length ? 'success' : 'empty'); }).catch((cause: Error) => { setError(cause); setStatus('error'); }); }, []);
  useEffect(() => {
    let active = true;
    setSaved(false);
    setSelectedCodes(new Set());
    setOriginalCodes(new Set());
    setPermissionRoleId('');
    setChange(undefined);
    setError(undefined);
    setPermissionLoadError(undefined);
    if (!selectedRoleId) {
      setPermissionsLoading(false);
      return () => { active = false; };
    }
    setPermissionsLoading(true);
    listRolePermissions(selectedRoleId)
      .then((rows) => {
        if (!active) return;
        const codes = new Set(rows.map((item) => item.code));
        setSelectedCodes(codes);
        setOriginalCodes(new Set(codes));
        setPermissionRoleId(selectedRoleId);
      })
      .catch((cause: Error) => { if (active) setPermissionLoadError(cause); })
      .finally(() => { if (active) setPermissionsLoading(false); });
    return () => { active = false; };
  }, [permissionLoadAttempt, selectedRoleId]);
  const permissionsReady = Boolean(selectedRoleId) && !permissionsLoading && !permissionLoadError
    && permissionRoleId === selectedRoleId;
  const selectedRoleName = roles.find((role) => role.roleId === selectedRoleId)?.name || selectedRoleId;
  const grouped = useMemo(() => permissions.reduce<Map<string, IamPermission[]>>((result, permission) => {
    const rows = result.get(permission.resourceType) || [];
    result.set(permission.resourceType, [...rows, permission]);
    return result;
  }, new Map()), [permissions]);
  function toggle(code: string, checked: boolean) { if (!permissionsReady || saving) return; setSaved(false); setSelectedCodes((value) => { const next = new Set(value); if (checked) next.add(code); else next.delete(code); return next; }); }
  function permissionDiffItem(code: string): PermissionDiffItem {
    const permission = permissions.find((item) => item.code === code);
    return { code, name: permission?.name || code };
  }
  function openSaveConfirmation() {
    if (!permissionsReady || saving) return;
    const role = roles.find((item) => item.roleId === selectedRoleId);
    if (!role) return;
    const permissionCodes = Array.from(selectedCodes).sort();
    setChange({
      roleId: role.roleId,
      roleName: role.name,
      permissionCodes,
      added: permissionCodes.filter((code) => !originalCodes.has(code)).map(permissionDiffItem),
      removed: Array.from(originalCodes).filter((code) => !selectedCodes.has(code)).sort().map(permissionDiffItem),
    });
    setReason(''); setError(undefined);
  }
  function closeSaveConfirmation() { if (saving) return; setChange(undefined); setReason(''); setError(undefined); }
  async function confirmSave() {
    if (!change || !reason.trim() || saving) return;
    setSaving(true); setError(undefined);
    try {
      await replaceRolePermissions(change.roleId, change.permissionCodes, reason.trim());
      if (selectedRoleId === change.roleId) {
        const savedCodes = new Set(change.permissionCodes);
        setSelectedCodes(savedCodes);
        setOriginalCodes(new Set(savedCodes));
      }
      setSaved(true); setChange(undefined); setReason('');
    } catch (cause) { setError(cause as Error); }
    finally { setSaving(false); }
  }
  return <main className="page-stack"><PageHeading title="角色权限" description="租户管理员在平台权限上限内维护角色权限；保存采用完整集合替换，避免遗留授权。" marker="租户内授权" /><Feedback status={status} error={status === 'error' && !change ? error : undefined} />{status !== 'error' && !change && <InlineMutationError error={error} />}
    {roles.length > 0 && <section className="surface role-matrix"><div className="role-selector"><Field label="当前角色"><Select aria-label="当前角色" value={selectedRoleId} disabled={saving} onChange={(event) => setSelectedRoleId(event.target.value)}>{roles.map((role) => <option key={role.roleId} value={role.roleId}>{role.name}</option>)}</Select></Field><Text>{roles.find((role) => role.roleId === selectedRoleId)?.description || '按业务职责配置最小权限。'}</Text></div>
      {permissionLoadError && <MessageBar intent="error" role="alert"><MessageBarBody><div className="chat-error"><span>{permissionLoadError.message}{errorTraceId(permissionLoadError) && <span className="trace-hint">错误追踪：{errorTraceId(permissionLoadError)}</span>}</span><Button size="small" disabled={permissionsLoading} onClick={() => setPermissionLoadAttempt((value) => value + 1)}>重试读取{selectedRoleName}权限</Button></div></MessageBarBody></MessageBar>}
      <div className="permission-groups">{Array.from(grouped.entries()).map(([resourceType, rows]) => <fieldset key={resourceType}><legend>{resourceType}</legend>{rows.map((permission) => <Checkbox key={permission.code} label={permission.name} checked={selectedCodes.has(permission.code)} disabled={!permissionsReady || saving} onChange={(_, data) => toggle(permission.code, data.checked === true)} />)}</fieldset>)}</div>
      {saved && <MessageBar intent="success"><MessageBarBody>权限矩阵已保存。</MessageBarBody></MessageBar>}<div className="form-actions"><Button appearance="primary" disabled={!permissionsReady || saving} onClick={openSaveConfirmation}>{saving ? '保存中' : '保存权限'}</Button></div>
    </section>}
    <Dialog open={Boolean(change)} surfaceMotion={dialogSurfaceMotion} onOpenChange={(_, data) => { if (!data.open) closeSaveConfirmation(); }}><DialogSurface backdropMotion={dialogBackdropMotion}><DialogBody><DialogTitle>确认替换角色权限</DialogTitle><DialogContent>{change && <div className="dialog-form risk-confirmation"><dl className="change-summary"><dt>目标角色</dt><dd>{change.roleName}</dd><dt>角色编号</dt><dd>{change.roleId}</dd><dt>替换后权限数</dt><dd>{change.permissionCodes.length}</dd></dl><div className="permission-diff" aria-label="权限变更差异"><section><Text weight="semibold">新增权限</Text>{change.added.length ? <ul>{change.added.map((item) => <li key={item.code}><strong>{item.name}</strong><span>{item.code}</span></li>)}</ul> : <Text>无</Text>}</section><section><Text weight="semibold">移除权限</Text>{change.removed.length ? <ul>{change.removed.map((item) => <li key={item.code}><strong>{item.name}</strong><span>{item.code}</span></li>)}</ul> : <Text>无</Text>}</section></div><MessageBar intent="warning"><MessageBarBody>完整替换会移除所有未选中的权限，并立即影响该角色下用户的可访问范围。</MessageBarBody></MessageBar><Field label="变更原因" required><Textarea maxLength={300} value={reason} onChange={(_, data) => setReason(data.value)} /></Field><InlineMutationError error={error} /></div>}</DialogContent><DialogActions><Button disabled={saving} onClick={closeSaveConfirmation}>取消</Button><Button appearance="primary" disabled={saving || !reason.trim()} onClick={confirmSave}>确认替换权限</Button></DialogActions></DialogBody></DialogSurface></Dialog>
  </main>;
}

type ModelHubDisableResourceTarget =
  | { kind: 'provider'; provider: ModelProviderItem }
  | { kind: 'model'; model: ModelConfigItem };

type ModelHubDisableTarget =
  | { kind: 'provider'; tenantId: string; provider: ModelProviderItem }
  | { kind: 'model'; tenantId: string; model: ModelConfigItem };

function freezeModelHubDisableTarget(
  target: ModelHubDisableResourceTarget,
  tenantId: string,
): ModelHubDisableTarget {
  return target.kind === 'provider'
    ? Object.freeze({ kind: 'provider', tenantId, provider: Object.freeze({ ...target.provider }) })
    : Object.freeze({ kind: 'model', tenantId, model: Object.freeze({ ...target.model }) });
}

const UNCERTAIN_CREATION_HTTP_STATUSES = new Set([408, 425, 429]);
const CREATION_IDEMPOTENCY_CONFLICT_CODE = 'AIP-MDL-CREATIONIDEMPOTENCYCONFLICT';
const MODEL_HUB_PENDING_CREATION_STORAGE_VERSION = 1;
const MODEL_HUB_PENDING_CREATION_STORAGE_PREFIX = 'aip:model-hub:pending-creation:v1';
const MODEL_HUB_PENDING_CREATION_MAX_SERIALIZED_LENGTH = 8_192;
const MODEL_HUB_SCOPE_ID_MAX_LENGTH = 128;
const MODEL_HUB_RESOURCE_ID_MAX_LENGTH = 128;
const MODEL_HUB_NAME_MAX_LENGTH = 200;
const MODEL_HUB_CODE_MAX_LENGTH = 200;
const MODEL_HUB_ENDPOINT_MAX_LENGTH = 2_048;
const MODEL_HUB_PROVIDER_KIND_MAX_LENGTH = 64;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

type PendingProviderCreationIntent = {
  version: typeof MODEL_HUB_PENDING_CREATION_STORAGE_VERSION;
  tenantId: string;
  userId: string;
  kind: 'provider';
  requestId: string;
  command: {
    providerName: string;
    providerType: string;
    apiEndpoint: string;
    authType: string;
  };
};

type PendingModelCreationIntent = {
  version: typeof MODEL_HUB_PENDING_CREATION_STORAGE_VERSION;
  tenantId: string;
  userId: string;
  kind: 'model';
  requestId: string;
  command: {
    providerId: string;
    modelCode: string;
    modelName: string;
    modelType: string;
    contextWindow: number;
    embeddingDimension: number | null;
    inputPricePer1k: string;
    outputPricePer1k: string;
    options: { providerKind: string; maxTokens?: number };
  };
};

type PendingModelHubCreationIntent = PendingProviderCreationIntent | PendingModelCreationIntent;

function isUncertainCreationFailure(cause: unknown) {
  if (!(cause instanceof ApiError)) return true;
  return cause.code === CREATION_IDEMPOTENCY_CONFLICT_CODE
    || cause.code === 'INVALID_RESPONSE'
    || cause.status >= 500
    || UNCERTAIN_CREATION_HTTP_STATUSES.has(cause.status);
}

function shouldRotateCreationRequestId(cause: unknown) {
  return cause instanceof ApiError
    && cause.status >= 400
    && cause.status < 500
    && !isUncertainCreationFailure(cause);
}

type EditableModelConfigItem = ModelConfigItem & {
  embeddingDimension?: number | null;
  inputPricePer1k?: string;
  outputPricePer1k?: string;
  providerKind?: string;
  temperature?: string;
  topP?: string;
  maxTokens?: number;
};

function isPositiveInteger(value: string) {
  const normalized = value.trim();
  return /^\d+$/.test(normalized) && Number.isSafeInteger(Number(normalized)) && Number(normalized) > 0;
}

function isNonNegativeNumber(value: string) {
  const normalized = value.trim();
  return /^(?:0|[1-9]\d{0,9})(?:\.\d{1,8})?$/.test(normalized);
}

function modelHubCreationStorageKey(tenantId: string, userId: string) {
  return `${MODEL_HUB_PENDING_CREATION_STORAGE_PREFIX}:${encodeURIComponent(tenantId.trim())}:${encodeURIComponent(userId.trim())}`;
}

function sessionStorageIfAvailable() {
  try {
    return typeof window === 'undefined' ? undefined : window.sessionStorage;
  } catch {
    return undefined;
  }
}

function storageRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function boundedStorageString(value: unknown, maxLength: number): value is string {
  return typeof value === 'string' && Boolean(value.trim()) && value.length <= maxLength;
}

function positiveStorageInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
}

function validPendingModelHubCreationIntent(
  value: unknown,
  tenantId: string,
  userId: string,
): value is PendingModelHubCreationIntent {
  const candidate = storageRecord(value);
  const command = storageRecord(candidate?.command);
  if (!candidate || !command
    || candidate.version !== MODEL_HUB_PENDING_CREATION_STORAGE_VERSION
    || candidate.tenantId !== tenantId
    || candidate.userId !== userId
    || !boundedStorageString(candidate.tenantId, MODEL_HUB_SCOPE_ID_MAX_LENGTH)
    || !boundedStorageString(candidate.userId, MODEL_HUB_SCOPE_ID_MAX_LENGTH)
    || !boundedStorageString(candidate.requestId, 64)
    || !UUID_PATTERN.test(candidate.requestId)
    || Object.keys(candidate).length !== 6) return false;
  if (candidate.kind === 'provider') {
    return Object.keys(command).length === 4
      && boundedStorageString(command.providerName, MODEL_HUB_NAME_MAX_LENGTH)
      && boundedStorageString(command.providerType, MODEL_HUB_PROVIDER_KIND_MAX_LENGTH)
      && boundedStorageString(command.apiEndpoint, MODEL_HUB_ENDPOINT_MAX_LENGTH)
      && boundedStorageString(command.authType, MODEL_HUB_PROVIDER_KIND_MAX_LENGTH)
      && PROVIDER_AUTH_TYPE_DEFAULTS[command.providerType] === command.authType;
  }
  if (candidate.kind !== 'model') return false;
  const options = storageRecord(command.options);
  return Object.keys(command).length === 9
    && boundedStorageString(command.providerId, MODEL_HUB_RESOURCE_ID_MAX_LENGTH)
    && boundedStorageString(command.modelCode, MODEL_HUB_CODE_MAX_LENGTH)
    && boundedStorageString(command.modelName, MODEL_HUB_NAME_MAX_LENGTH)
    && (command.modelType === 'CHAT' || command.modelType === 'EMBEDDING')
    && positiveStorageInteger(command.contextWindow)
    && (command.embeddingDimension === null || positiveStorageInteger(command.embeddingDimension))
    && boundedStorageString(command.inputPricePer1k, 19)
    && isNonNegativeNumber(command.inputPricePer1k)
    && boundedStorageString(command.outputPricePer1k, 19)
    && isNonNegativeNumber(command.outputPricePer1k)
    && Boolean(options)
    && Object.keys(options!).length === (options!.maxTokens === undefined ? 1 : 2)
    && boundedStorageString(options?.providerKind, MODEL_HUB_PROVIDER_KIND_MAX_LENGTH)
    && CONSOLE_MANAGED_PROVIDER_KINDS.has(options.providerKind.toLowerCase())
    && (options.maxTokens === undefined || positiveStorageInteger(options.maxTokens));
}

function readPendingModelHubCreationIntent(tenantId: string, userId: string): PendingModelHubCreationIntent | undefined {
  const storage = sessionStorageIfAvailable();
  if (!storage) return undefined;
  const key = modelHubCreationStorageKey(tenantId, userId);
  try {
    const serialized = storage.getItem(key);
    if (!serialized) return undefined;
    const candidate = serialized.length <= MODEL_HUB_PENDING_CREATION_MAX_SERIALIZED_LENGTH
      ? JSON.parse(serialized) as unknown
      : undefined;
    if (!validPendingModelHubCreationIntent(candidate, tenantId, userId)) throw new Error('invalid pending intent');
    return candidate;
  } catch {
    try { storage.removeItem(key); } catch { /* storage can become unavailable */ }
    return undefined;
  }
}

function persistPendingModelHubCreationIntent(intent: PendingModelHubCreationIntent) {
  try {
    const serialized = JSON.stringify(intent);
    if (serialized.length > MODEL_HUB_PENDING_CREATION_MAX_SERIALIZED_LENGTH
      || !validPendingModelHubCreationIntent(intent, intent.tenantId, intent.userId)) return;
    sessionStorageIfAvailable()?.setItem(
      modelHubCreationStorageKey(intent.tenantId, intent.userId),
      serialized,
    );
  } catch {
    // Keep the in-memory fail-closed state even when browser storage is unavailable.
  }
}

function removePendingModelHubCreationIntent(tenantId: string, userId: string) {
  try {
    sessionStorageIfAvailable()?.removeItem(modelHubCreationStorageKey(tenantId, userId));
  } catch {
    // Storage cleanup must not break the authoritative mutation result path.
  }
}

const RESOURCE_LIFECYCLES = {
  PENDING_APPROVAL: { label: '待审批', color: 'warning' },
  ACTIVE: { label: '已启用', color: 'success' },
  INACTIVE: { label: '待启用', color: 'informative' },
  REJECTED: { label: '审批已拒绝', color: 'danger' },
  DEPRECATED: { label: '已停用', color: 'danger' },
  DISABLED: { label: '已停用', color: 'danger' },
} as const;

function resourceLifecycle(status: string) {
  return RESOURCE_LIFECYCLES[status as keyof typeof RESOURCE_LIFECYCLES]
    || { label: status || '状态未知', color: 'subtle' as const };
}

const CONNECTIVITY_LABELS = {
  PASSED: '已通过', FAILED: '未通过', TESTING: '测试中', NOT_TESTED: '尚未测试', UNTESTED: '尚未测试', UNKNOWN: '尚未测试',
} as const;

function connectivityLabel(status: string) {
  return CONNECTIVITY_LABELS[status as keyof typeof CONNECTIVITY_LABELS] || status || '未知';
}

const LOCAL_DETERMINISTIC_PROVIDER_TYPE = 'local-deterministic';
const LOCAL_DETERMINISTIC_MAX_EMBEDDING_DIMENSION = 4096;
const LOCAL_DETERMINISTIC_PROVIDER_ENABLED = import.meta.env.MODE === 'test'
  || import.meta.env.VITE_LOCAL_DETERMINISTIC_PROVIDER_ENABLED === 'true';
const PROVIDER_AUTH_TYPE_DEFAULTS: Readonly<Record<string, 'api_key' | 'no_auth'>> = {
  'openai-compatible': 'api_key',
  ollama: 'no_auth',
  'mistral-ai': 'api_key',
  [LOCAL_DETERMINISTIC_PROVIDER_TYPE]: 'no_auth',
};
const CONSOLE_MANAGED_PROVIDER_KINDS = new Set([
  'openai-compatible',
  'openai',
  'ollama',
  'mistral-ai',
  'mistral_ai',
  'mistral',
  LOCAL_DETERMINISTIC_PROVIDER_TYPE,
]);

function isConsoleManagedProviderKind(providerKind: string, localDeterministicProviderEnabled: boolean) {
  const normalized = providerKind.trim().toLowerCase();
  if (normalized === LOCAL_DETERMINISTIC_PROVIDER_TYPE && !localDeterministicProviderEnabled) return false;
  return CONSOLE_MANAGED_PROVIDER_KINDS.has(normalized);
}

function allowsConnectivityTest(status: string) {
  return status === 'INACTIVE' || status === 'ACTIVE';
}

export function ModelHubPage({
  user,
  localDeterministicProviderEnabled = LOCAL_DETERMINISTIC_PROVIDER_ENABLED,
}: {
  user: CurrentUser;
  localDeterministicProviderEnabled?: boolean;
}) {
  const activateCreateDialog = useActivateModal();
  const hasPermission = (permission: string) => user.permissionCodes.includes(permission);
  const canReadModels = hasPermission('model-hub:model-config:read');
  const canCreateProvider = hasPermission('model-hub:provider:create');
  const canDisableProvider = hasPermission('model-hub:provider:disable');
  const canCreateModel = hasPermission('model-hub:model-config:create');
  const canUpdateModel = hasPermission('model-hub:model-config:update');
  const canDisableModel = hasPermission('model-hub:model-config:disable');
  const canTestModel = hasPermission('model-hub:model-config:test-connectivity');
  const creationIntentOwnerKey = modelHubCreationStorageKey(user.tenantId, user.userId);
  const [pendingCreationIntent, setPendingCreationIntent] = useState<PendingModelHubCreationIntent | undefined>(
    () => readPendingModelHubCreationIntent(user.tenantId, user.userId),
  );
  const creationIntentOwnerKeyRef = useRef(creationIntentOwnerKey);
  creationIntentOwnerKeyRef.current = creationIntentOwnerKey;
  const [providers, setProviders] = useState<ModelProviderItem[]>([]);
  const [models, setModels] = useState<ModelConfigItem[]>([]);
  const [selectedProviderId, setSelectedProviderId] = useState('');
  const [dialog, setDialog] = useState<'provider' | 'model' | 'model-edit'>();
  const creationRequestId = useRef(pendingCreationIntent?.requestId || '');
  const [editingModel, setEditingModel] = useState<EditableModelConfigItem>();
  const [disableTarget, setDisableTarget] = useState<ModelHubDisableTarget>();
  const [disableReason, setDisableReason] = useState('');
  const [disableError, setDisableError] = useState<Error>();
  const [operationResult, setOperationResult] = useState<{ message: string; traceId?: string }>();
  const [error, setError] = useState<Error>();
  const [dialogError, setDialogError] = useState<Error>();
  const [providerStatus, setProviderStatus] = useState<RequestStatus>('loading');
  const [modelStatus, setModelStatus] = useState<RequestStatus>('idle');
  const [working, setWorking] = useState(false);
  const [disableSubmitting, setDisableSubmitting] = useState(false);
  const [providerName, setProviderName] = useState('');
  const [providerType, setProviderType] = useState('openai-compatible');
  const [apiEndpoint, setApiEndpoint] = useState('');
  const [authType, setAuthType] = useState('api_key');
  const [credential, setCredential] = useState('');
  const [modelCode, setModelCode] = useState('');
  const [modelName, setModelName] = useState('');
  const [modelType, setModelType] = useState('CHAT');
  const [contextWindow, setContextWindow] = useState('');
  const [maxOutputTokens, setMaxOutputTokens] = useState('');
  const [inputPricePer1k, setInputPricePer1k] = useState('');
  const [outputPricePer1k, setOutputPricePer1k] = useState('');
  const [embeddingDimension, setEmbeddingDimension] = useState('');
  const providerCatalogGeneration = useRef(0);
  const providerCatalogController = useRef<AbortController>();
  const modelCatalogGeneration = useRef(0);
  const modelCatalogController = useRef<AbortController>();
  const selectedProviderRef = useRef(
    pendingCreationIntent?.kind === 'model' ? pendingCreationIntent.command.providerId : '',
  );
  const dialogRestoreFocusTarget = useRef<HTMLElement>();
  const createDialogSurfaceTarget = useRef<HTMLDivElement | null>(null);
  const createDialogSubmitTarget = useRef<HTMLButtonElement | null>(null);
  const restoreDisableResultFocus = useRef(false);
  const operationResultFocusTarget = useRef<HTMLDivElement | null>(null);
  const selectedProvider = providers.find((provider) => provider.providerId === selectedProviderId);
  const providerKind = selectedProvider?.providerKind?.trim() || selectedProvider?.providerCode.trim() || '';
  const selectedProviderLocalDeterministic = providerKind.toLowerCase() === LOCAL_DETERMINISTIC_PROVIDER_TYPE;
  const selectedProviderCapabilityDisabled = selectedProviderLocalDeterministic
    && !localDeterministicProviderEnabled;
  const selectedProviderConsoleManaged = Boolean(
    providerKind && isConsoleManagedProviderKind(providerKind, localDeterministicProviderEnabled),
  );
  const selectedProviderActive = selectedProvider?.status === 'ACTIVE';
  const modelManagementHint = !selectedProviderActive
      ? '供应商审批通过并启用后可管理。'
      : selectedProviderCapabilityDisabled
        ? '本地确定性运行时能力已关闭；仅可停用。'
      : !selectedProviderConsoleManaged
        ? '由受认证 API 管理模型配置。'
        : '';
  const localDeterministicProvider = providerType === LOCAL_DETERMINISTIC_PROVIDER_TYPE;
  const providerEndpointHint = localDeterministicProvider
    ? '不出站；地址和鉴权已锁定。'
    : '仅支持公网 HTTPS/443 地址；禁止用户信息及查询参数。';
  const providerRequiresCredential = authType !== 'no_auth';
  const embeddingDimensionValidationMessage = embeddingDimension.trim()
    ? !isPositiveInteger(embeddingDimension)
      ? '请输入大于 0 的整数。'
      : selectedProviderLocalDeterministic
        && Number(embeddingDimension) > LOCAL_DETERMINISTIC_MAX_EMBEDDING_DIMENSION
        ? `向量维度不得大于 ${LOCAL_DETERMINISTIC_MAX_EMBEDDING_DIMENSION}。`
        : undefined
    : undefined;
  const providerFormValid = Boolean(
    providerName.trim()
    && providerType.trim()
    && apiEndpoint.trim()
    && authType
    && (!localDeterministicProvider || localDeterministicProviderEnabled)
    && (!providerRequiresCredential || credential),
  );
  const modelFormValid = Boolean(
    selectedProviderId
    && selectedProviderActive
    && selectedProviderConsoleManaged
    && providerKind
    && modelCode.trim()
    && modelName.trim()
    && modelType
    && isPositiveInteger(contextWindow)
    && (!maxOutputTokens.trim() || isPositiveInteger(maxOutputTokens))
    && isNonNegativeNumber(inputPricePer1k)
    && isNonNegativeNumber(outputPricePer1k)
    && (modelType !== 'EMBEDDING'
      || (isPositiveInteger(embeddingDimension) && !embeddingDimensionValidationMessage)),
  );
  const creationIntentLocked = Boolean(
    pendingCreationIntent
      && dialog === pendingCreationIntent.kind
      && creationRequestId.current === pendingCreationIntent.requestId,
  );
  const canSave = creationIntentLocked && pendingCreationIntent
    ? pendingCreationIntent.kind === 'model'
      ? canCreateModel
      : canCreateProvider && (pendingCreationIntent.command.authType === 'no_auth' || Boolean(credential))
    : dialog === 'provider'
      ? providerFormValid
      : dialog === 'model' || dialog === 'model-edit' ? modelFormValid : false;
  const disableTraceId = disableError && 'traceId' in disableError
    ? String((disableError as Error & { traceId?: string }).traceId || '')
    : '';
  const dialogTraceId = dialogError && 'traceId' in dialogError
    ? String((dialogError as Error & { traceId?: string }).traceId || '')
    : '';
  const creationIntentConflict = creationIntentLocked
    && dialogError instanceof ApiError
    && dialogError.code === CREATION_IDEMPOTENCY_CONFLICT_CODE;

  function rememberPendingCreationIntent(intent: PendingModelHubCreationIntent) {
    persistPendingModelHubCreationIntent(intent);
    if (creationIntentOwnerKeyRef.current !== modelHubCreationStorageKey(intent.tenantId, intent.userId)) return;
    setPendingCreationIntent(intent);
  }

  function clearPendingCreationIntent(tenantId = user.tenantId, userId = user.userId) {
    removePendingModelHubCreationIntent(tenantId, userId);
    if (creationIntentOwnerKeyRef.current !== modelHubCreationStorageKey(tenantId, userId)) return;
    setPendingCreationIntent(undefined);
  }

  function restorePendingCreationIntent(intent: PendingModelHubCreationIntent) {
    creationRequestId.current = intent.requestId;
    setEditingModel(undefined);
    setCredential('');
    if (intent.kind === 'provider') {
      setProviderName(intent.command.providerName);
      setProviderType(intent.command.providerType);
      setApiEndpoint(intent.command.apiEndpoint);
      setAuthType(intent.command.authType);
      return;
    }
    selectedProviderRef.current = intent.command.providerId;
    setSelectedProviderId(intent.command.providerId);
    setModelCode(intent.command.modelCode);
    setModelName(intent.command.modelName);
    setModelType(intent.command.modelType);
    setContextWindow(String(intent.command.contextWindow));
    setMaxOutputTokens(intent.command.options.maxTokens == null ? '' : String(intent.command.options.maxTokens));
    setInputPricePer1k(intent.command.inputPricePer1k);
    setOutputPricePer1k(intent.command.outputPricePer1k);
    setEmbeddingDimension(intent.command.embeddingDimension == null ? '' : String(intent.command.embeddingDimension));
  }

  function resumePendingCreationIntent(restoreFocusTarget?: HTMLElement) {
    const intent = pendingCreationIntent;
    if (!intent
      || (intent.kind === 'provider' && !canCreateProvider)
      || (intent.kind === 'model' && !canCreateModel)) return;
    dialogRestoreFocusTarget.current = restoreFocusTarget;
    setError(undefined);
    setDialogError(undefined);
    setOperationResult(undefined);
    restorePendingCreationIntent(intent);
    setDialog(intent.kind);
  }

  async function loadModels(id: string, manageWorking = false) {
    modelCatalogController.current?.abort();
    const controller = new AbortController();
    const generation = modelCatalogGeneration.current + 1;
    modelCatalogController.current = controller;
    modelCatalogGeneration.current = generation;
    if (manageWorking) setWorking(true);
    setModels([]);
    setError(undefined);
    if (!id || !canReadModels) {
      setModelStatus('idle');
      if (manageWorking) setWorking(false);
      return;
    }
    setModelStatus('loading');
    try {
      const rows = await listModelConfigs(id, controller.signal);
      if (!controller.signal.aborted
        && generation === modelCatalogGeneration.current
        && selectedProviderRef.current === id) {
        setModels(rows);
        setModelStatus(rows.length ? 'success' : 'empty');
      }
    } catch (cause) {
      if (!controller.signal.aborted
        && generation === modelCatalogGeneration.current
        && selectedProviderRef.current === id) {
        setError(cause as Error);
        setModelStatus('error');
      }
    } finally {
      if (manageWorking && generation === modelCatalogGeneration.current) setWorking(false);
    }
  }

  async function loadProviders() {
    providerCatalogController.current?.abort();
    const controller = new AbortController();
    const generation = providerCatalogGeneration.current + 1;
    providerCatalogController.current = controller;
    providerCatalogGeneration.current = generation;
    setProviderStatus('loading');
    setError(undefined);
    try {
      const rows = await listModelProviders(controller.signal);
      if (controller.signal.aborted || generation !== providerCatalogGeneration.current) return;
      setProviders(rows);
      setProviderStatus(rows.length ? 'success' : 'empty');
      const currentSelection = selectedProviderRef.current;
      const id = rows.some((provider) => provider.providerId === currentSelection)
        ? currentSelection
        : rows[0]?.providerId || '';
      selectedProviderRef.current = id;
      setSelectedProviderId(id);
      if (id) await loadModels(id);
      else {
        setModels([]);
        setModelStatus('idle');
      }
    } catch (cause) {
      if (!controller.signal.aborted && generation === providerCatalogGeneration.current) {
        setError(cause as Error);
        setProviderStatus('error');
      }
    }
  }

  useEffect(() => {
    const restored = readPendingModelHubCreationIntent(user.tenantId, user.userId);
    setPendingCreationIntent(restored);
    creationRequestId.current = restored?.requestId || '';
    setCredential('');
    setDialogError(undefined);
    setEditingModel(undefined);
    setDialog(undefined);
    dialogRestoreFocusTarget.current = undefined;
    selectedProviderRef.current = restored?.kind === 'model' ? restored.command.providerId : '';
    setSelectedProviderId(selectedProviderRef.current);
    loadProviders().catch((cause: Error) => setError(cause));
  }, [creationIntentOwnerKey]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => () => {
    providerCatalogController.current?.abort();
    modelCatalogController.current?.abort();
  }, []);
  useEffect(() => {
    if (dialog || working || !dialogRestoreFocusTarget.current) return;
    const target = dialogRestoreFocusTarget.current;
    dialogRestoreFocusTarget.current = undefined;
    if (target.isConnected && !target.hasAttribute('disabled') && target.getAttribute('aria-disabled') !== 'true') {
      target.focus({ preventScroll: true });
    }
  }, [dialog, working]);
  useEffect(() => {
    if (!dialog || !createDialogSurfaceTarget.current) return;
    activateCreateDialog(createDialogSurfaceTarget.current);
  }, [activateCreateDialog, dialog]);
  useEffect(() => {
    if (!dialog || working || !dialogError || !createDialogSurfaceTarget.current || !createDialogSubmitTarget.current) return;
    activateCreateDialog(createDialogSurfaceTarget.current);
    const focusFrame = requestAnimationFrame(() => {
      createDialogSubmitTarget.current?.focus({ preventScroll: true });
    });
    return () => cancelAnimationFrame(focusFrame);
  }, [activateCreateDialog, dialog, dialogError, working]);
  useEffect(() => {
    if (!restoreDisableResultFocus.current
      || disableTarget
      || disableSubmitting
      || providerStatus === 'loading'
      || !operationResult
      || !operationResultFocusTarget.current) return;
    restoreDisableResultFocus.current = false;
    let focusFrame = 0;
    const settleFrame = requestAnimationFrame(() => {
      focusFrame = requestAnimationFrame(() => {
        operationResultFocusTarget.current?.focus({ preventScroll: true });
      });
    });
    return () => {
      cancelAnimationFrame(settleFrame);
      cancelAnimationFrame(focusFrame);
    };
  }, [disableSubmitting, disableTarget, operationResult, providerStatus]);

  async function selectProvider(id: string) {
    selectedProviderRef.current = id;
    setSelectedProviderId(id);
    setError(undefined);
    await loadModels(id, true);
  }

  function selectProviderType(nextProviderType: string) {
    if (nextProviderType === providerType) return;
    if (nextProviderType === LOCAL_DETERMINISTIC_PROVIDER_TYPE && !localDeterministicProviderEnabled) return;
    const nextAuthType = PROVIDER_AUTH_TYPE_DEFAULTS[nextProviderType];
    if (!nextAuthType) return;
    setProviderType(nextProviderType);
    setApiEndpoint(nextProviderType === LOCAL_DETERMINISTIC_PROVIDER_TYPE ? 'local://deterministic' : '');
    setAuthType(nextAuthType);
    setCredential('');
  }

  function openCreateDialog(kind: 'provider' | 'model', restoreFocusTarget?: HTMLElement) {
    if ((kind === 'provider' && !canCreateProvider) || (kind === 'model' && !canCreateModel)) return;
    const pendingIntent = pendingCreationIntent;
    if (pendingIntent) {
      if (pendingIntent.kind === kind) resumePendingCreationIntent(restoreFocusTarget);
      return;
    }
    if (kind === 'model' && (!selectedProviderActive || !selectedProviderConsoleManaged)) return;
    dialogRestoreFocusTarget.current = restoreFocusTarget;
    setError(undefined);
    setDialogError(undefined);
    setOperationResult(undefined);
    creationRequestId.current = crypto.randomUUID();
    if (kind === 'provider') {
      setEditingModel(undefined);
      setProviderName('');
      setProviderType('openai-compatible');
      setApiEndpoint('');
      setAuthType('api_key');
      setCredential('');
    } else {
      setEditingModel(undefined);
      setModelCode('');
      setModelName('');
      setModelType('CHAT');
      setContextWindow('');
      setMaxOutputTokens('');
      setInputPricePer1k('');
      setOutputPricePer1k('');
      setEmbeddingDimension('');
    }
    setDialog(kind);
  }

  function openEditModel(model: ModelConfigItem, restoreFocusTarget?: HTMLElement) {
    if (!canUpdateModel || !selectedProviderActive || !selectedProviderConsoleManaged) return;
    dialogRestoreFocusTarget.current = restoreFocusTarget;
    const editable = model as EditableModelConfigItem;
    setError(undefined);
    setDialogError(undefined);
    setOperationResult(undefined);
    setEditingModel(editable);
    if (!pendingCreationIntent) creationRequestId.current = '';
    setModelCode(editable.modelCode);
    setModelName(editable.modelName);
    setModelType(editable.modelType);
    setContextWindow(editable.contextWindow == null ? '' : String(editable.contextWindow));
    setMaxOutputTokens(editable.maxTokens == null ? '' : String(editable.maxTokens));
    setInputPricePer1k(editable.inputPricePer1k == null ? '' : String(editable.inputPricePer1k));
    setOutputPricePer1k(editable.outputPricePer1k == null ? '' : String(editable.outputPricePer1k));
    setEmbeddingDimension(editable.embeddingDimension == null ? '' : String(editable.embeddingDimension));
    setDialog('model-edit');
  }

  function closeCreateDialog() {
    setCredential('');
    if (!pendingCreationIntent) creationRequestId.current = '';
    setEditingModel(undefined);
    setDialogError(undefined);
    setDialog(undefined);
  }

  async function save() {
    const authorized = dialog === 'provider'
      ? canCreateProvider
      : dialog === 'model'
        ? canCreateModel
        : dialog === 'model-edit' && canUpdateModel;
    if (!authorized || !dialog || !canSave || working) return;
    setWorking(true);
    setDialogError(undefined);
    let attemptedCreationIntent: PendingModelHubCreationIntent | undefined;
    try {
      if (dialog === 'provider') {
        const pendingIntent = pendingCreationIntent?.kind === 'provider'
          && pendingCreationIntent.requestId === creationRequestId.current
          ? pendingCreationIntent
          : undefined;
        const command = pendingIntent?.command || {
          providerName: providerName.trim(),
          providerType: providerType.trim(),
          apiEndpoint: apiEndpoint.trim(),
          authType,
        };
        attemptedCreationIntent = {
          version: MODEL_HUB_PENDING_CREATION_STORAGE_VERSION,
          tenantId: user.tenantId,
          userId: user.userId,
          kind: 'provider',
          requestId: creationRequestId.current,
          command,
        };
        const created = await createModelProvider({
          requestId: attemptedCreationIntent.requestId,
          ...command,
          credentialMaterialInput: command.authType === 'no_auth' ? null : credential,
        });
        setOperationResult({
          message: `供应商 ${created.providerId} 待审批。`,
        });
      } else {
        const options: { providerKind: string; maxTokens?: number; temperature?: number; topP?: number } = {
          providerKind: editingModel?.providerKind || providerKind,
        };
        if (maxOutputTokens.trim()) options.maxTokens = Number(maxOutputTokens);
        if (editingModel?.temperature != null) options.temperature = Number(editingModel.temperature);
        if (editingModel?.topP != null) options.topP = Number(editingModel.topP);
        const input = {
          modelCode: modelCode.trim(),
          modelName: modelName.trim(),
          modelType,
          contextWindow: Number(contextWindow),
          embeddingDimension: modelType === 'EMBEDDING' ? Number(embeddingDimension) : null,
          inputPricePer1k: inputPricePer1k.trim(),
          outputPricePer1k: outputPricePer1k.trim(),
          options,
        };
        if (dialog === 'model-edit' && editingModel) {
          const updated = await updateModelConfig(selectedProviderId, editingModel.modelConfigId, input, editingModel.version);
          setOperationResult({
            message: updated.status === 'PENDING_APPROVAL'
              ? `模型 ${updated.modelConfigId} 已更新，待审批。`
              : `模型 ${updated.modelConfigId} 状态 ${resourceLifecycle(updated.status).label}。`,
          });
        } else {
          const pendingIntent = pendingCreationIntent?.kind === 'model'
            && pendingCreationIntent.requestId === creationRequestId.current
            ? pendingCreationIntent
            : undefined;
          const command = pendingIntent?.command || {
            providerId: selectedProviderId,
            ...input,
            options: {
              providerKind: input.options.providerKind,
              ...(input.options.maxTokens == null ? {} : { maxTokens: input.options.maxTokens }),
            },
          };
          attemptedCreationIntent = {
            version: MODEL_HUB_PENDING_CREATION_STORAGE_VERSION,
            tenantId: user.tenantId,
            userId: user.userId,
            kind: 'model',
            requestId: creationRequestId.current,
            command,
          };
          const { providerId: commandProviderId, ...modelCommand } = command;
          const created = await createModelConfig(commandProviderId, {
            ...modelCommand,
            requestId: attemptedCreationIntent.requestId,
          });
          setOperationResult({
            message: created.status === 'PENDING_APPROVAL'
              ? `模型 ${created.modelConfigId} 待审批，连通后启用。`
              : `模型 ${created.modelConfigId} 状态 ${resourceLifecycle(created.status).label}。`,
          });
        }
      }
      if (attemptedCreationIntent) {
        clearPendingCreationIntent(attemptedCreationIntent.tenantId, attemptedCreationIntent.userId);
      }
      closeCreateDialog();
      await loadProviders();
    } catch (cause) {
      if (attemptedCreationIntent) {
        if (isUncertainCreationFailure(cause)) {
          rememberPendingCreationIntent(attemptedCreationIntent);
        } else if (shouldRotateCreationRequestId(cause)) {
          clearPendingCreationIntent(attemptedCreationIntent.tenantId, attemptedCreationIntent.userId);
          creationRequestId.current = crypto.randomUUID();
        }
      }
      setDialogError(cause as Error);
    }
    finally { setWorking(false); }
  }

  async function testModel(model: ModelConfigItem) {
    if (!canTestModel
      || working
      || !selectedProviderActive
      || selectedProviderCapabilityDisabled
      || !allowsConnectivityTest(model.status)) return;
    const targetProviderId = model.providerId;
    setWorking(true);
    setError(undefined);
    try {
      await testModelConnectivity(model.providerId, model.modelConfigId, model.version);
      if (selectedProviderRef.current === targetProviderId) await loadModels(targetProviderId);
    } catch (cause) {
      if (selectedProviderRef.current === targetProviderId) setError(cause as Error);
    } finally {
      if (selectedProviderRef.current === targetProviderId) setWorking(false);
    }
  }

  function openDisable(target: ModelHubDisableResourceTarget) {
    if ((target.kind === 'provider' && !canDisableProvider)
      || (target.kind === 'model' && (!canDisableModel || target.model.status === 'PENDING_APPROVAL'))) return;
    restoreDisableResultFocus.current = false;
    setDisableTarget(freezeModelHubDisableTarget(target, user.tenantId));
    setDisableReason('');
    setDisableError(undefined);
    setOperationResult(undefined);
    setError(undefined);
  }

  async function confirmDisable() {
    const authorized = disableTarget?.kind === 'provider' ? canDisableProvider : canDisableModel;
    if (!authorized || !disableTarget || !disableReason.trim() || disableSubmitting) return;
    const currentTarget = disableTarget;
    const reason = disableReason.trim();
    if (currentTarget.tenantId !== user.tenantId) {
      setDisableError(new Error('当前租户上下文已变化，请关闭确认框并重新选择操作对象。'));
      return;
    }
    setDisableSubmitting(true);
    setDisableError(undefined);
    try {
      if (currentTarget.kind === 'provider') {
        const result = await disableModelProvider(currentTarget.provider.providerId, reason, currentTarget.provider.version);
        setOperationResult({
          message: currentTarget.provider.status === 'PENDING_APPROVAL'
            ? `模型供应商 ${currentTarget.provider.providerName}已停用；审批撤销请求已提交，审批中心正在同步。`
            : `模型供应商 ${currentTarget.provider.providerName}已停用。`,
          traceId: result.traceId,
        });
      } else {
        const result = await disableModelConfig(currentTarget.model.providerId, currentTarget.model.modelConfigId, reason, currentTarget.model.version);
        setOperationResult({ message: `模型配置 ${currentTarget.model.modelName}已停用。`, traceId: result.traceId });
      }
      restoreDisableResultFocus.current = true;
      setDisableTarget(undefined);
      setDisableReason('');
      try { await loadProviders(); }
      catch (cause) { setError(cause as Error); }
    } catch (cause) { setDisableError(cause as Error); }
    finally { setDisableSubmitting(false); }
  }

  const disableName = disableTarget?.kind === 'provider'
    ? disableTarget.provider.providerName
    : disableTarget?.model.modelName;
  const cancellingProviderApproval = disableTarget?.kind === 'provider'
    && disableTarget.provider.status === 'PENDING_APPROVAL';
  const disableResourceId = disableTarget?.kind === 'provider'
    ? disableTarget.provider.providerId
    : disableTarget?.model.modelConfigId;
  const disableProviderId = disableTarget?.kind === 'model' ? disableTarget.model.providerId : undefined;
  const disableCurrentStatus = disableTarget?.kind === 'provider'
    ? disableTarget.provider.status
    : disableTarget?.model.status;
  const disableExpectedVersion = disableTarget?.kind === 'provider'
    ? disableTarget.provider.version
    : disableTarget?.model.version;
  const disableNextStatus = disableTarget?.kind === 'model' ? 'DEPRECATED' : 'DISABLED';
  const disableRecovery = cancellingProviderApproval
    ? '修正配置后重新创建供应商并提交新审批，当前供应商不恢复。'
    : disableTarget?.kind === 'provider'
      ? '先迁移依赖，再新建供应商并重新绑定模型配置。'
      : '创建或编辑替代模型配置，完成审批与连通性测试后迁移依赖。';

  return <main className="page-stack">
    <PageHeading
      title="模型中心"
      description="按权限管理供应商与模型。"
    />
    {pendingCreationIntent && <MessageBar intent="warning" role="status" aria-label="未决创建意图">
      <MessageBarBody><div className="chat-error"><span>
        {pendingCreationIntent.kind === 'provider'
          ? `供应商“${pendingCreationIntent.command.providerName}”`
          : `模型“${pendingCreationIntent.command.modelName}”`}
        的创建结果尚未确认（请求编号 {pendingCreationIntent.requestId}）。在核对原命令前，禁止开始新的供应商或模型创建。
      </span><Button
        size="small"
        disabled={(pendingCreationIntent.kind === 'provider' && !canCreateProvider)
          || (pendingCreationIntent.kind === 'model' && !canCreateModel)}
        onClick={(event) => resumePendingCreationIntent(event.currentTarget)}
      >继续核对/重试</Button></div></MessageBarBody>
    </MessageBar>}
    {(canCreateProvider || canCreateModel) && <div className="toolbar"><div className="row-actions">
      {canCreateProvider && <Button appearance="primary" title={pendingCreationIntent ? '请先继续核对未决创建意图' : undefined} disabled={Boolean(pendingCreationIntent) || providerStatus === 'loading' || providerStatus === 'error'} onClick={(event) => openCreateDialog('provider', event.currentTarget)}>新增供应商</Button>}
      {canCreateModel && <Button title={pendingCreationIntent ? '请先继续核对未决创建意图' : undefined} disabled={Boolean(pendingCreationIntent) || !selectedProviderId || !selectedProviderActive || !selectedProviderConsoleManaged || modelStatus === 'loading'} onClick={(event) => openCreateDialog('model', event.currentTarget)}>新增模型配置</Button>}
    </div></div>}
    <Feedback status={providerStatus} error={providerStatus === 'error' ? error : undefined} onRetry={() => { void loadProviders(); }} />
    {providerStatus === 'empty' && <section className="empty-state"><CheckmarkCircle20Regular /><Title3>暂无模型供应商</Title3></section>}
    {error && providerStatus !== 'error' && modelStatus !== 'error' && <MessageBar intent="error"><MessageBarBody>{error.message}</MessageBarBody></MessageBar>}
    {operationResult && <MessageBar ref={operationResultFocusTarget} aria-label="模型中心操作结果" tabIndex={-1} intent="success"><MessageBarBody>{operationResult.message}{operationResult.traceId && <span className="trace-hint">操作追踪：{operationResult.traceId}</span>}</MessageBarBody></MessageBar>}
    {providerStatus === 'success' && providers.length > 0 && <section className="master-detail"><div className="surface data-region"><div className="table-wrap"><table aria-label="模型供应商列表"><thead><tr><th>供应商</th><th>编码</th><th>服务地址</th><th>状态</th><th>操作</th></tr></thead><tbody>{providers.map((provider) => { const lifecycle = resourceLifecycle(provider.status); const pendingApproval = provider.status === 'PENDING_APPROVAL'; return <tr key={provider.providerId}><td>{provider.providerName}</td><td>{provider.providerCode}</td><td>{provider.endpoint}</td><td><Badge color={lifecycle.color}>{lifecycle.label}</Badge></td><td><div className="row-actions"><Button aria-label={`查看 ${provider.providerName} 的模型配置`} aria-pressed={selectedProviderId === provider.providerId} appearance={selectedProviderId === provider.providerId ? 'primary' : 'subtle'} onClick={() => { void selectProvider(provider.providerId); }}>模型配置</Button>{canDisableProvider && <Button aria-label={`${pendingApproval ? '撤销审批' : '停用'} ${provider.providerName}`} disabled={provider.status === 'DISABLED' || working || disableSubmitting} onClick={() => openDisable({ kind: 'provider', provider })}>{pendingApproval ? '撤销审批' : '停用'}</Button>}</div></td></tr>; })}</tbody></table></div></div>
      <aside className="surface detail-panel model-detail"><div><Title3>模型配置</Title3><Text>{selectedProvider?.providerName || '请选择供应商'}</Text></div>
        {selectedProvider && modelManagementHint && <MessageBar intent="info"><MessageBarBody>{modelManagementHint}</MessageBarBody></MessageBar>}
        {canReadModels
          ? <Feedback status={modelStatus} error={modelStatus === 'error' ? error : undefined} onRetry={() => { if (selectedProviderId) void loadModels(selectedProviderId, true); }} />
          : <Text>无模型配置读取权限。</Text>}
        {canReadModels && modelStatus === 'empty' && <Text>暂无模型配置。</Text>}
        {models.length > 0 && <div className="model-list">{models.map((model) => {
          const lifecycle = resourceLifecycle(model.status);
          return <article key={model.modelConfigId} aria-label={`模型配置 ${model.modelName}`}>
            <div><div><strong>{model.modelName}</strong><span>{model.modelType} · {model.modelConfigId}</span></div><Badge color={lifecycle.color}>{lifecycle.label}</Badge></div>
            <Text>连通性：{connectivityLabel(model.connectivityStatus)}{model.connectivityMessage ? ` · ${model.connectivityMessage}` : ''}</Text>
            {model.status === 'PENDING_APPROVAL' && <MessageBar intent="info"><MessageBarBody>配置 {model.modelConfigId} 待审批。审批通过后仍需连通性测试。 <a className="inline-link" href="/admin/approvals">前往审批中心</a></MessageBarBody></MessageBar>}
            {model.status === 'REJECTED' && <MessageBar intent="error"><MessageBarBody>拒绝状态下不会执行连通测试。</MessageBarBody></MessageBar>}
            {(canUpdateModel || canTestModel || canDisableModel) && <div className="row-actions">
              {canUpdateModel && selectedProviderConsoleManaged && <Button aria-label={`编辑 ${model.modelName}`} disabled={working || !selectedProviderActive || model.status === 'PENDING_APPROVAL' || model.status === 'DEPRECATED'} onClick={(event) => openEditModel(model, event.currentTarget)}>编辑</Button>}
              {canTestModel && <Button aria-label={`测试 ${model.modelName} 连通性`} disabled={working || !selectedProviderActive || selectedProviderCapabilityDisabled || !allowsConnectivityTest(model.status)} onClick={() => { void testModel(model); }}>连通测试</Button>}
              {canDisableModel && <Button aria-label={`停用 ${model.modelName}`} title={model.status === 'PENDING_APPROVAL' ? '待审批模型配置不可停用，请先完成或撤销审批' : undefined} disabled={working || disableSubmitting || model.status === 'PENDING_APPROVAL' || model.status === 'DISABLED' || model.status === 'DEPRECATED'} onClick={() => openDisable({ kind: 'model', model })}>停用</Button>}
            </div>}
          </article>;
        })}</div>}
      </aside>
    </section>}
    <Dialog open={Boolean(dialog)} surfaceMotion={dialogSurfaceMotion} onOpenChange={(_, data) => { if (!data.open && !working) closeCreateDialog(); }}>
      <DialogSurface ref={createDialogSurfaceTarget} backdropMotion={dialogBackdropMotion}>
        <DialogBody>
          <DialogTitle>{dialog === 'provider' ? '新增模型供应商' : dialog === 'model-edit' ? '编辑模型配置' : '新增模型配置'}</DialogTitle>
          <DialogContent>
            <div className="dialog-form">
              {creationIntentLocked && <MessageBar intent="warning" role="status" aria-label="未决创建命令"><MessageBarBody>
                {creationIntentConflict
                  ? '服务端报告请求编号已存在且命令不一致。不得生成新请求编号；请核对并使用原命令和原凭证重试，或联系管理员确认服务端结果。'
                  : '创建结果尚未确认。已锁定原命令和请求编号；请核对后重试。关闭窗口会清除访问凭证，但可从模型中心继续恢复。'}
              </MessageBarBody></MessageBar>}
              {dialog === 'provider' ? <>
                <Field label="供应商名称" required>
                  <Input maxLength={MODEL_HUB_NAME_MAX_LENGTH} value={providerName} readOnly={creationIntentLocked} onChange={(_, data) => setProviderName(data.value)} />
                </Field>
                <Field label="供应商类型" required>
                  <Select value={providerType} disabled={creationIntentLocked} onChange={(event) => selectProviderType(event.target.value)}>
                    <option value="openai-compatible">OpenAI Compatible</option>
                    <option value="ollama">Ollama</option>
                    <option value="mistral-ai">Mistral AI</option>
                    {localDeterministicProviderEnabled && <option value="local-deterministic">本地确定性运行时（受控验收能力）</option>}
                  </Select>
                </Field>
                <Field label="API 地址" required hint={providerEndpointHint}>
                  <Input maxLength={MODEL_HUB_ENDPOINT_MAX_LENGTH} value={apiEndpoint} readOnly={localDeterministicProvider || creationIntentLocked} onChange={(_, data) => setApiEndpoint(data.value)} />
                </Field>
                <Field label="认证方式" required hint="类型锁定；切换即清空。">
                  <Select value={authType} disabled>
                    <option value="api_key">API Key</option>
                    <option value="no_auth">无鉴权</option>
                  </Select>
                </Field>
                {providerRequiresCredential && <Field label="访问凭证" required hint="加密保存，不回显。">
                  <Input type="password" value={credential} onChange={(_, data) => setCredential(data.value)} />
                </Field>}
              </> : <>
                {dialog === 'model-edit' && <MessageBar intent="warning"><MessageBarBody>保存后重新审批；通过前不可用。</MessageBarBody></MessageBar>}
                <Field label="模型名称" required><Input maxLength={MODEL_HUB_NAME_MAX_LENGTH} value={modelName} readOnly={creationIntentLocked} onChange={(_, data) => setModelName(data.value)} /></Field>
                <Field label="模型编码" required><Input maxLength={MODEL_HUB_CODE_MAX_LENGTH} value={modelCode} readOnly={creationIntentLocked} onChange={(_, data) => setModelCode(data.value)} /></Field>
                <Field label="模型类型" required><Select value={modelType} disabled={creationIntentLocked} onChange={(event) => setModelType(event.target.value)}><option value="CHAT">对话</option><option value="EMBEDDING">向量</option></Select></Field>
                <Field label="上下文窗口" required validationMessage={contextWindow && !isPositiveInteger(contextWindow) ? '请输入大于 0 的整数。' : undefined}><Input type="number" min={1} step={1} value={contextWindow} readOnly={creationIntentLocked} onChange={(_, data) => setContextWindow(data.value)} /></Field>
                <Field label="最大输出 Token" hint="留空用默认值；否则填正整数。" validationMessage={maxOutputTokens && !isPositiveInteger(maxOutputTokens) ? '请输入大于 0 的整数。' : undefined}><Input type="number" min={1} step={1} value={maxOutputTokens} readOnly={creationIntentLocked} onChange={(_, data) => setMaxOutputTokens(data.value)} /></Field>
                <Field label="输入价格（每千 Token）" hint="最多 10 位整数、8 位小数" required validationMessage={inputPricePer1k && !isNonNegativeNumber(inputPricePer1k) ? '请输入最多 10 位整数、8 位小数的非负金额。' : undefined}><Input type="text" inputMode="decimal" value={inputPricePer1k} readOnly={creationIntentLocked} onChange={(_, data) => setInputPricePer1k(data.value)} /></Field>
                <Field label="输出价格（每千 Token）" hint="最多 10 位整数、8 位小数" required validationMessage={outputPricePer1k && !isNonNegativeNumber(outputPricePer1k) ? '请输入最多 10 位整数、8 位小数的非负金额。' : undefined}><Input type="text" inputMode="decimal" value={outputPricePer1k} readOnly={creationIntentLocked} onChange={(_, data) => setOutputPricePer1k(data.value)} /></Field>
                {modelType === 'EMBEDDING' && <Field label="向量维度" required hint={selectedProviderLocalDeterministic ? `范围 1 到 ${LOCAL_DETERMINISTIC_MAX_EMBEDDING_DIMENSION}；运行时 dimensions 必须一致。` : undefined} validationMessage={embeddingDimensionValidationMessage}><Input type="number" min={1} max={selectedProviderLocalDeterministic ? LOCAL_DETERMINISTIC_MAX_EMBEDDING_DIMENSION : undefined} step={1} value={embeddingDimension} readOnly={creationIntentLocked} onChange={(_, data) => setEmbeddingDimension(data.value)} /></Field>}
              </>}
              {dialogError && <MessageBar intent="error"><MessageBarBody>{dialogError.message}{dialogTraceId && <span className="trace-hint">错误追踪：{dialogTraceId}</span>}</MessageBarBody></MessageBar>}
            </div>
          </DialogContent>
          <DialogActions>
            <Button disabled={working} onClick={closeCreateDialog}>取消</Button>
            <Button
              ref={createDialogSubmitTarget}
              appearance="primary"
              disabled={!working && !canSave}
              disabledFocusable={working}
              onClick={() => { void save(); }}
            >{working ? '保存中' : dialog === 'model-edit' ? '保存并提交审批' : '保存'}</Button>
          </DialogActions>
        </DialogBody>
      </DialogSurface>
    </Dialog>
    <Dialog open={Boolean(disableTarget)} surfaceMotion={dialogSurfaceMotion} onOpenChange={(_, data) => { if (!data.open && !disableSubmitting) { setDisableTarget(undefined); setDisableReason(''); setDisableError(undefined); } }}><DialogSurface backdropMotion={dialogBackdropMotion}><DialogBody><DialogTitle>{cancellingProviderApproval ? '撤销供应商审批' : disableTarget?.kind === 'provider' ? '停用模型供应商' : '停用模型配置'}</DialogTitle><DialogContent><div className="dialog-form risk-confirmation"><Text>{cancellingProviderApproval ? '即将撤销审批：' : '即将停用：'}<strong>{disableName}</strong></Text>{disableTarget && <dl className="change-summary" role="group" aria-label="危险操作对象快照"><dt>资源 ID</dt><dd>{disableResourceId}</dd>{disableProviderId && <><dt>所属供应商 ID</dt><dd>{disableProviderId}</dd></>}<dt>租户 ID</dt><dd>{disableTarget.tenantId}</dd><dt>当前状态</dt><dd>{disableCurrentStatus}（{resourceLifecycle(disableCurrentStatus || '').label}）</dd><dt>预期版本</dt><dd>{disableExpectedVersion}</dd><dt>状态变更</dt><dd>{disableCurrentStatus} → {disableNextStatus}</dd><dt>可逆性</dt><dd>不可原地恢复</dd><dt>恢复路径</dt><dd>{disableRecovery}</dd>{cancellingProviderApproval && disableTarget.kind === 'provider' && disableTarget.provider.approvalRequestId && <><dt>审批请求 ID</dt><dd>{disableTarget.provider.approvalRequestId}</dd></>}</dl>}<MessageBar intent="warning"><MessageBarBody>{cancellingProviderApproval ? '确认后供应商立即进入已停用，并提交审批撤销请求。审批中心状态可能短暂延迟；若人工决定已先完成，将保留该审批终态，供应商仍保持停用。' : disableTarget?.kind === 'provider' ? '关联模型配置将停止新调用；请确认迁移。' : '依赖该配置的应用无法选用；请先迁移。'}</MessageBarBody></MessageBar><Field label={cancellingProviderApproval ? '撤销理由' : '停用理由'} required hint={cancellingProviderApproval ? '说明撤销原因，后续需修正后重新提交审批。' : '填写业务原因与迁移处置。'}><Textarea value={disableReason} onChange={(_, data) => setDisableReason(data.value)} /></Field>{disableError && <MessageBar intent="error"><MessageBarBody>{disableError.message}{disableTraceId && <span className="trace-hint">错误追踪：{disableTraceId}</span>}</MessageBarBody></MessageBar>}</div></DialogContent><DialogActions><Button disabled={disableSubmitting} onClick={() => { setDisableTarget(undefined); setDisableReason(''); setDisableError(undefined); }}>取消</Button><Button appearance="primary" disabled={disableSubmitting || !disableReason.trim()} onClick={() => { void confirmDisable(); }}>{disableSubmitting ? cancellingProviderApproval ? '正在撤销' : '正在停用' : cancellingProviderApproval ? '确认撤销' : '确认停用'}</Button></DialogActions></DialogBody></DialogSurface></Dialog>
  </main>;
}

export function PermissionRequestPage({ user }: { user: CurrentUser }) {
  const [requests, setRequests] = useState<PermissionRequestItem[]>([]);
  const [selected, setSelected] = useState<PermissionRequestItem>();
  const [comment, setComment] = useState('');
  const [status, setStatus] = useState<RequestStatus>('loading');
  const [loadError, setLoadError] = useState<Error>();
  const [decisionFailure, setDecisionFailure] = useState<{
    request: PermissionRequestItem;
    action: 'APPROVE' | 'REJECT';
    comment: string;
    error: Error;
  }>();
  const [workingId, setWorkingId] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState<'' | PermissionRequestItem['status']>('');
  const requestController = useRef<AbortController>();
  const loadRequests = useCallback(async () => {
    requestController.current?.abort();
    const controller = new AbortController();
    requestController.current = controller;
    setRequests([]);
    setSelected(undefined);
    setStatus('loading');
    setLoadError(undefined);
    setDecisionFailure(undefined);
    try {
      const result = await listPermissionRequests({
        page,
        pageSize,
        status: statusFilter || undefined,
      }, controller.signal);
      if (requestController.current !== controller || controller.signal.aborted) return;
      if (result.total > 0 && page > result.totalPages) {
        setPage(result.totalPages);
        return;
      }
      setRequests(result.items);
      setTotal(result.total);
      setTotalPages(result.totalPages);
      setStatus(result.items.length ? 'success' : 'empty');
    } catch (cause) {
      if (requestController.current === controller && !controller.signal.aborted) {
        setLoadError(cause as Error);
        setStatus('error');
      }
    } finally {
      if (requestController.current === controller) requestController.current = undefined;
    }
  }, [page, pageSize, statusFilter]);
  useEffect(() => {
    void loadRequests();
    return () => requestController.current?.abort();
  }, [loadRequests]);
  async function decide(request: PermissionRequestItem, action: 'APPROVE' | 'REJECT', retryComment?: string) {
    if (request.requesterId === user.userId) return;
    const reason = retryComment ?? comment.trim(); if (!reason) return;
    setWorkingId(request.requestId); setDecisionFailure(undefined);
    try { const result = await decidePermissionRequest(request.requestId, action, reason); await loadRequests(); setSelected(result); setComment(''); }
    catch (cause) { setDecisionFailure({ request, action, comment: reason, error: cause as Error }); }
    finally { setWorkingId(undefined); }
  }
  const selectedIsOwnRequest = selected?.requesterId === user.userId;
  const selectedApplicationRequest = selected?.resourceType === 'AI_APPLICATION';
  const selectedDecisionFailure = decisionFailure?.request.requestId === selected?.requestId ? decisionFailure : undefined;
  return <main className="page-stack"><PageHeading title="权限申请" description="受理业务用户访问诉求；批准时在同一事务写入该用户对目标资源的最小 ACL，拒绝不改变任何授权。" marker="授权闭环" /><Feedback status={status} error={loadError} onRetry={() => { void loadRequests(); }} />
    <section className="surface filter-bar" aria-label="权限申请筛选"><Field label="处理状态"><Select aria-label="按处理状态筛选" value={statusFilter} onChange={(_, data) => { setStatusFilter(data.value as '' | PermissionRequestItem['status']); setPage(1); }}><option value="">全部状态</option><option value="PENDING">待处理</option><option value="APPROVED">已批准</option><option value="REJECTED">已拒绝</option></Select></Field></section>
    {status === 'empty' && <section className="empty-state"><CheckmarkCircle20Regular /><Title3>{statusFilter ? '当前筛选下暂无申请' : '暂无权限申请'}</Title3><Text>{statusFilter ? '可切换处理状态查看其他申请。' : '当前租户没有待受理的访问诉求。'}</Text></section>}
    {requests.length > 0 && <section className="master-detail"><div className="surface data-region"><div className="table-wrap"><table aria-label="权限申请列表"><thead><tr><th>申请对象</th><th>申请人</th><th>申请权限</th><th>状态</th><th>操作</th></tr></thead><tbody>{requests.map((request) => <tr key={request.requestId}><td>{request.resourceType} / {request.resourceId}</td><td>{request.requesterId}{request.requesterId === user.userId ? '（当前账号）' : ''}</td><td>{request.permissionCode}</td><td><Badge color={request.status === 'PENDING' ? 'warning' : request.status === 'APPROVED' ? 'success' : 'danger'}>{request.status}</Badge></td><td><Button appearance="subtle" onClick={() => { setSelected(request); setComment(''); setDecisionFailure(undefined); }}>{request.status === 'PENDING' && request.requesterId !== user.userId ? '受理' : '查看'}</Button></td></tr>)}</tbody></table></div><Pagination page={page} pageSize={pageSize} total={total} totalPages={totalPages} onChange={(nextPage, nextSize) => { setPage(nextPage); setPageSize(nextSize); }} /></div>
      <aside className="surface detail-panel">{selected ? <><div><div><Title3>申请详情</Title3><Text>{selected.requestId}</Text></div><Badge color={selected.status === 'PENDING' ? 'warning' : selected.status === 'APPROVED' ? 'success' : 'danger'}>{selected.status}</Badge></div><dl><dt>申请人</dt><dd>{selected.requesterId}{selectedIsOwnRequest ? '（当前账号）' : ''}</dd><dt>申请对象</dt><dd>{selected.resourceType} / {selected.resourceId}</dd><dt>申请权限</dt><dd>{selected.permissionCode}</dd><dt>申请原因</dt><dd>{selected.reason}</dd></dl>{selected.status === 'PENDING' ? <><MessageBar intent="warning"><MessageBarBody>{selectedIsOwnRequest ? '这是当前账号提交的申请。职责分离要求由另一名具备管理权限的管理员处理。' : selectedApplicationRequest ? '批准会写入应用草稿访问策略；活动发布版本受众不变，应用管理员重新发布后才对新会话生效。' : '批准将立即授予申请人此资源的指定权限；不会授予同类其他资源，也不会修改其角色。'}</MessageBarBody></MessageBar>{selectedDecisionFailure && <MessageBar intent="error" role="alert"><MessageBarBody>{selectedDecisionFailure.error.message}{errorTraceId(selectedDecisionFailure.error) && <span className="trace-hint">错误追踪：{errorTraceId(selectedDecisionFailure.error)}</span>}<Button appearance="transparent" disabled={workingId === selected.requestId} onClick={() => { void decide(selectedDecisionFailure.request, selectedDecisionFailure.action, selectedDecisionFailure.comment); }}>{selectedDecisionFailure.action === 'APPROVE' ? '重试批准并授权' : '重试拒绝申请'}</Button></MessageBarBody></MessageBar>}<Field label="处理意见" required><Textarea disabled={selectedIsOwnRequest} value={comment} onChange={(_, data) => setComment(data.value)} /></Field><div className="form-actions"><Button disabled={selectedIsOwnRequest || !comment.trim() || workingId === selected.requestId} onClick={() => decide(selected, 'REJECT')}>拒绝申请</Button><Button appearance="primary" disabled={selectedIsOwnRequest || !comment.trim() || workingId === selected.requestId} onClick={() => decide(selected, 'APPROVE')}>批准并授权</Button></div></> : <MessageBar intent="info"><MessageBarBody>{selected.decisionComment || (selected.status === 'APPROVED' ? selectedApplicationRequest ? '审批完成，应用草稿 ACL 已写入；重新发布后对新会话生效。' : '审批完成，资源 ACL 已生效。' : '申请已拒绝，授权未发生变化。')}</MessageBarBody></MessageBar>}</> : <div className="detail-empty"><ShieldCheckmark20Regular /><Text>从列表选择一条申请查看或受理</Text></div>}</aside>
    </section>}
  </main>;
}
