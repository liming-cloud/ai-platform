import { Fragment, useEffect, useRef, useState, type FormEvent } from 'react';
import { Badge, Button, Field, Input, MessageBar, MessageBarBody, Select, Text, Title3 } from '@fluentui/react-components';
import { DocumentSearch20Regular, Eye20Regular } from '@fluentui/react-icons';
import { getPlatformSummary, getTenantSummary, listPage } from './api';
import { renderCell } from './page-format';
import { PageHeading, RequestFeedback } from './page-shared';
import type { Navigate } from './page-shared';
import { Pagination } from './pagination';
import type { CurrentUser, PageResponse, RequestStatus, SummaryResult } from './types';

interface HomeProps {
  user: CurrentUser;
  navigate: Navigate;
}

interface WorkItem {
  label: string;
  description: string;
  value?: number;
  route?: string;
  actionLabel?: string;
  visible?: boolean;
}

interface Fact {
  label: string;
  value?: string | number;
}

function metricNumber(metric: Record<string, unknown> | undefined, key: string): number | undefined {
  const value = metric?.[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function authorizedRoute(user: CurrentUser, permission: string, route: string): string | undefined {
  return user.permissionCodes.includes(permission) && user.menus.some((menu) => menu.path === route)
    ? route
    : undefined;
}

function formatDataTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? value : date.toLocaleString('zh-CN');
}

function freshnessLabel(value: string): string {
  if (value === 'REALTIME') return '实时';
  if (value === 'SNAPSHOT') return '快照';
  if (value === 'STALE') return '可能延迟';
  return value;
}

function WorkQueue({ items, navigate, partial }: { items: WorkItem[]; navigate: Navigate; partial: boolean }) {
  const availableItems = items.filter((item) => item.visible !== false && item.value !== undefined);
  const activeItems = availableItems.filter((item) => Number(item.value) > 0);
  return <section className="surface task-workbench" aria-labelledby="work-queue-title">
    <div className="workbench-section-header">
      <div><Title3 as="h2" id="work-queue-title">待办与异常</Title3><Text>按当前账号的真实授权范围进入处理链路。</Text></div>
      <Badge appearance="outline">{availableItems.length > 0 ? `${activeItems.length} 类需处理` : '数据不可用'}</Badge>
    </div>
    {activeItems.length > 0
      ? <div className="task-list">{activeItems.map((item) => <article className="task-row" key={item.label}>
          <strong className="task-count">{item.value}</strong>
          <div className="task-copy"><strong>{item.label}</strong><Text>{item.description}</Text></div>
          {item.route && item.actionLabel && <Button appearance="subtle" onClick={() => navigate(item.route!)}>{item.actionLabel}</Button>}
        </article>)}</div>
      : <div className="workbench-empty"><strong>{availableItems.length > 0 ? '当前没有待处理事项' : '任务数据不可用'}</strong><Text>{availableItems.length === 0 ? '服务端未返回可识别的待办与异常计数。' : partial ? '已返回的指标中没有待办，仍有部分数据不可用。' : '所有已返回的待办和异常计数均为 0。'}</Text></div>}
  </section>;
}

function OperationalContext({ summary, facts }: { summary: SummaryResult; facts: Fact[] }) {
  const availableFacts = facts.filter((fact) => fact.value !== undefined);
  return <aside className="surface operational-context" aria-labelledby="operational-context-title">
    <div className="workbench-section-header"><div><Title3 as="h2" id="operational-context-title">运行上下文</Title3><Text>用于判断待办的当前规模和数据新鲜度。</Text></div></div>
    {availableFacts.length > 0 && <dl className="workbench-facts">{availableFacts.map((fact) => <div key={fact.label}><dt>{fact.label}</dt><dd>{fact.value}</dd></div>)}</dl>}
    <dl className="workbench-metadata">
      <dt>数据时间</dt><dd>{formatDataTime(summary.asOf)}</dd>
      <dt>数据状态</dt><dd>{freshnessLabel(summary.freshness)}{summary.partial ? '，部分成功' : '，完整'}</dd>
      <dt>Trace ID</dt><dd>{summary.traceId}</dd>
    </dl>
  </aside>;
}

function SummaryPage({ mode, user, navigate }: { mode: 'platform' | 'tenant' } & HomeProps) {
  const [status, setStatus] = useState<RequestStatus>('loading');
  const [summary, setSummary] = useState<SummaryResult>();
  const [error, setError] = useState<Error>();
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setSummary(undefined);
    setError(undefined);
    setStatus('loading');
    (mode === 'platform' ? getPlatformSummary(controller.signal) : getTenantSummary(controller.signal))
      .then((value) => {
        if (controller.signal.aborted) return;
        setSummary(value);
        setStatus(value.partial ? 'partial' : 'success');
      })
      .catch((reason: unknown) => {
        if (controller.signal.aborted || (reason instanceof Error && reason.name === 'AbortError')) return;
        setError(reason instanceof Error ? reason : new Error('数据读取失败'));
        setStatus('error');
      });
    return () => controller.abort();
  }, [attempt, mode]);

  const candidateWorkItems: WorkItem[] = mode === 'platform'
    ? [
        { label: '待处理审批', description: '审核需要平台级裁决的高风险任务。', value: metricNumber(summary?.approvalTodos, 'pending'), route: authorizedRoute(user, 'approval-center:task:read', '/admin/approvals'), actionLabel: '处理审批' },
        { label: '异常租户', description: '核查健康状态异常的租户及其治理配置。', value: metricNumber(summary?.tenantHealth, 'abnormal'), route: authorizedRoute(user, 'identity-access:tenant-delegation:manage', '/admin/tenants'), actionLabel: '查看租户' },
        { label: '缺少租户管理员', description: '为未建立管理责任人的租户补齐管理链路。', value: metricNumber(summary?.tenantHealth, 'missingTenantAdministrator'), route: authorizedRoute(user, 'identity-access:tenant-delegation:manage', '/admin/tenants'), actionLabel: '配置管理员' },
        { label: '故障模型', description: '检查连通性或运行状态为失败的模型。', value: metricNumber(summary?.modelHealth, 'failed'), route: authorizedRoute(user, 'model-hub:provider:read', '/admin/model-hub'), actionLabel: '查看模型' },
        { label: '待批准模型', description: '处理尚未完成审批的模型配置。', value: metricNumber(summary?.modelHealth, 'pendingApproval'), route: authorizedRoute(user, 'approval-center:task:read', '/admin/approvals'), actionLabel: '处理审批' },
        { label: 'RAG 授权拒绝', description: '查看过去 24 小时内被服务端策略拒绝的访问。', value: metricNumber(summary?.ragSecurity, 'authorizationDenialsLast24Hours'), route: authorizedRoute(user, 'observability-audit:security-log:read', '/admin/audit'), actionLabel: '查看审计' },
        { label: '应用发布阻断', description: '已检出租户应用发布链路中的阻断项。', value: metricNumber(summary?.appPublishBlocks, 'blocked') },
      ]
    : [
        { label: '待处理权限申请', description: '核验业务人员提交的权限请求与业务理由。', value: metricNumber(summary?.permissionTodos, 'pending'), route: authorizedRoute(user, 'identity-access:permission-request:manage', '/tenant/permission-requests'), actionLabel: '处理申请' },
        { label: '未分配角色的用户', description: '为活跃用户补齐角色，避免账号无法执行必要任务。', value: metricNumber(summary?.userRoleGaps, 'usersWithoutRole'), route: authorizedRoute(user, 'identity-access:tenant-setting:manage', '/tenant/users'), actionLabel: '管理用户' },
        { label: '无权限的角色', description: '检查已创建但没有授权项的角色配置。', value: metricNumber(summary?.userRoleGaps, 'rolesWithoutPermission'), route: authorizedRoute(user, 'identity-access:tenant-setting:manage', '/tenant/roles'), actionLabel: '管理角色' },
        { label: '未索引知识库', description: '完成知识库索引，使已授权内容可被应用检索。', value: metricNumber(summary?.knowledgeHealth, 'unindexed'), route: authorizedRoute(user, 'knowledge-base:knowledge-base:read', '/tenant/knowledge-bases'), actionLabel: '查看知识库', visible: summary?.enabledFeatures?.includes('knowledge-base') },
        { label: '失败知识库', description: '处理知识准备或索引链路的失败状态。', value: metricNumber(summary?.knowledgeHealth, 'failed'), route: authorizedRoute(user, 'knowledge-base:knowledge-base:read', '/tenant/knowledge-bases'), actionLabel: '查看知识库', visible: summary?.enabledFeatures?.includes('knowledge-base') },
        { label: '发布受阻应用', description: '处理发布预检未通过的应用配置。', value: metricNumber(summary?.appHealth, 'publishBlocked'), route: authorizedRoute(user, 'app-studio:application:read', '/tenant/applications'), actionLabel: '查看应用', visible: summary?.enabledFeatures?.includes('app-studio') },
      ];
  const workItems = candidateWorkItems.map((item) => ({
    ...item,
    visible: item.visible !== false && (!item.actionLabel || Boolean(item.route)),
  }));

  const facts: Fact[] = mode === 'platform'
    ? [
        { label: '租户总数', value: metricNumber(summary?.tenantHealth, 'total') },
        { label: '活跃租户', value: metricNumber(summary?.tenantHealth, 'active') },
        { label: '模型总数', value: metricNumber(summary?.modelHealth, 'total') },
        { label: '健康模型', value: metricNumber(summary?.modelHealth, 'healthy') },
      ]
    : [
        { label: '活跃用户', value: metricNumber(summary?.userRoleGaps, 'activeUsers') },
        { label: '就绪知识库', value: metricNumber(summary?.knowledgeHealth, 'ready') },
        { label: '知识库总数', value: metricNumber(summary?.knowledgeHealth, 'total') },
        { label: '已发布应用', value: metricNumber(summary?.appHealth, 'published') },
        { label: '应用总数', value: metricNumber(summary?.appHealth, 'total') },
        { label: '已启用功能', value: summary?.enabledFeatures ? summary.enabledFeatures.join('、') || '未启用' : undefined },
      ];

  return <main className="page-stack">
    <PageHeading
      title={mode === 'platform' ? '平台任务首页' : '租户任务首页'}
      description={mode === 'platform' ? '先处理平台审批、租户异常与模型风险，再核对运行上下文。' : '先处理成员授权、知识资产和应用发布异常。'}
    />
    <RequestFeedback status={status} error={error} onRetry={() => setAttempt((value) => value + 1)} loadingLabel="正在读取任务与异常" />
    {summary?.partial && <MessageBar intent="warning"><MessageBarBody><div className="summary-warning"><span>部分指标暂不可用：{summary.failedMetrics.join('、') || '未提供指标名称'}。已保留成功返回的数据。</span><Button size="small" appearance="transparent" onClick={() => setAttempt((value) => value + 1)}>重试</Button></div></MessageBarBody></MessageBar>}
    {summary && <div className="workbench-layout"><WorkQueue items={workItems} navigate={navigate} partial={summary.partial} /><OperationalContext summary={summary} facts={facts} /></div>}
  </main>;
}

export function PlatformOverviewPage(props: HomeProps) {
  return <SummaryPage mode="platform" {...props} />;
}

export function TenantOverviewPage(props: HomeProps) {
  return <SummaryPage mode="tenant" {...props} />;
}

const COLUMN_LABELS: Record<string, string> = {
  id: '编号', tenantId: '租户', actorId: '操作人', eventType: '事件类型', riskLevel: '风险等级', subjectType: '对象类型', subjectId: '对象编号', subjectRef: '对象引用', resultStatus: '结果', traceId: '追踪编号', createdAt: '发生时间',
  action: '操作', eventId: '事件标识', resourceId: '资源编号', resourceType: '资源类型', approvalReferenceId: '审批关联编号', reason: '原因', invitationId: '邀请编号', deliveryStatus: '通知状态', status: '状态', featureCode: '功能编码', enabled: '是否启用', permissionCodes: '权限范围', targetTenantId: '目标租户', targetUserId: '目标用户', requestId: '申请编号', roleId: '角色编号', menuId: '菜单编号', providerId: '供应商编号', modelConfigId: '模型配置编号', applicationId: '应用编号', sessionId: '会话编号', runId: '运行编号', knowledgeBaseId: '知识库编号', documentId: '文档编号', decision: '裁决', failureCode: '失败编码', retryCount: '重试次数', attemptCount: '尝试次数', sourceContext: '业务来源', sourceResourceType: '来源对象类型', sourceResourceId: '来源对象编号',
};

const AUDIT_DETAIL_DISCLOSURE_FIELDS = new Set([
  'eventId', 'action', 'actorId', 'resourceType', 'resourceId', 'approvalReferenceId',
  'reason', 'invitationId', 'deliveryStatus', 'status', 'featureCode', 'enabled',
  'permissionCodes', 'targetTenantId', 'targetUserId', 'requestId', 'roleId', 'menuId',
  'providerId', 'modelConfigId', 'applicationId', 'sessionId', 'runId', 'knowledgeBaseId',
  'documentId', 'decision', 'resultStatus', 'failureCode', 'retryCount', 'attemptCount',
  'sourceContext', 'sourceResourceType', 'sourceResourceId',
]);

function isAuditDetailValue(value: unknown): boolean {
  return value === null || ['string', 'number', 'boolean'].includes(typeof value)
    || (Array.isArray(value) && value.length <= 50
      && value.every((item) => item === null || ['string', 'number', 'boolean'].includes(typeof item)));
}

function parseDetail(value: unknown): Record<string, unknown> {
  if (typeof value !== 'string' || !value.trim()) return {};
  try {
    const parsed = JSON.parse(value);
    if (typeof parsed !== 'object' || !parsed || Array.isArray(parsed)) return {};
    return Object.fromEntries(Object.entries(parsed as Record<string, unknown>)
      .filter(([key, fieldValue]) => AUDIT_DETAIL_DISCLOSURE_FIELDS.has(key) && isAuditDetailValue(fieldValue)));
  } catch {
    return {};
  }
}

interface AuditLogRow extends Record<string, unknown> {
  id: string;
  tenantId: string;
  actorId?: string | null;
  eventType: string;
  riskLevel: string;
  subjectType?: string | null;
  subjectId?: string | null;
  subjectRef?: string | null;
  resultStatus: string;
  detailJson?: string | null;
  traceId: string;
  createdAt: string;
}

interface AuditFilters {
  actorId: string;
  eventType: string;
  subjectType: string;
  subjectId: string;
  traceId: string;
  riskLevel: string;
  resultStatus: string;
  createdFrom: string;
  createdTo: string;
}

const EMPTY_AUDIT_FILTERS: AuditFilters = {
  actorId: '',
  eventType: '',
  subjectType: '',
  subjectId: '',
  traceId: '',
  riskLevel: '',
  resultStatus: '',
  createdFrom: '',
  createdTo: '',
};

function auditTimeRangeError(filters: AuditFilters): string | undefined {
  const from = filters.createdFrom ? new Date(filters.createdFrom) : undefined;
  const to = filters.createdTo ? new Date(filters.createdTo) : undefined;
  if (from && Number.isNaN(from.valueOf())) return '开始时间格式无效。';
  if (to && Number.isNaN(to.valueOf())) return '结束时间格式无效。';
  if (from && to && from > to) return '开始时间不能晚于结束时间。';
  return undefined;
}

function auditRequestParameters(filters: AuditFilters, page: number, pageSize: number) {
  return {
    page,
    pageSize,
    actorId: filters.actorId.trim() || undefined,
    eventType: filters.eventType.trim() || undefined,
    subjectType: filters.subjectType.trim() || undefined,
    subjectId: filters.subjectId.trim() || undefined,
    traceId: filters.traceId.trim() || undefined,
    riskLevel: filters.riskLevel || undefined,
    resultStatus: filters.resultStatus || undefined,
    createdFrom: filters.createdFrom ? new Date(filters.createdFrom).toISOString() : undefined,
    createdTo: filters.createdTo ? new Date(filters.createdTo).toISOString() : undefined,
  };
}

function auditSubjectReference(row: AuditLogRow): string | null | undefined {
  return row.subjectRef ?? row.subjectId;
}

export function AuditPage() {
  const [draftFilters, setDraftFilters] = useState<AuditFilters>({ ...EMPTY_AUDIT_FILTERS });
  const [filters, setFilters] = useState<AuditFilters>({ ...EMPTY_AUDIT_FILTERS });
  const [result, setResult] = useState<PageResponse<AuditLogRow>>();
  const [selected, setSelected] = useState<AuditLogRow>();
  const [status, setStatus] = useState<RequestStatus>('loading');
  const [error, setError] = useState<Error>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [retryGeneration, setRetryGeneration] = useState(0);
  const [validationError, setValidationError] = useState<string>();
  const requestGeneration = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    const generation = ++requestGeneration.current;
    setResult(undefined);
    setSelected(undefined);
    setError(undefined);
    setStatus('loading');
    listPage<AuditLogRow>(
      '/api/v1/audit/security-logs',
      auditRequestParameters(filters, page, pageSize),
      controller.signal,
    )
      .then((value) => {
        if (controller.signal.aborted || generation !== requestGeneration.current) return;
        if (value.totalPages > 0 && page > value.totalPages) {
          setPage(value.totalPages);
          return;
        }
        setResult(value);
        setStatus(value.total > 0 ? 'success' : 'empty');
      })
      .catch((reason: unknown) => {
        if (controller.signal.aborted || generation !== requestGeneration.current
          || (reason instanceof Error && reason.name === 'AbortError')) return;
        setError(reason instanceof Error ? reason : new Error('安全审计读取失败'));
        setStatus('error');
      });
    return () => controller.abort();
  }, [filters, page, pageSize, retryGeneration]);

  const updateDraftFilter = (key: keyof AuditFilters, value: string) => {
    setDraftFilters((current) => ({ ...current, [key]: value }));
  };
  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const message = auditTimeRangeError(draftFilters);
    setValidationError(message);
    if (message) return;
    setPage(1);
    setFilters({ ...draftFilters });
  };
  const resetFilters = () => {
    setDraftFilters({ ...EMPTY_AUDIT_FILTERS });
    setFilters({ ...EMPTY_AUDIT_FILTERS });
    setValidationError(undefined);
    setPage(1);
  };
  const changePage = (nextPage: number, nextPageSize: number) => {
    setPage(nextPage);
    setPageSize(nextPageSize);
  };

  const rows = result?.items ?? [];
  const hasActiveFilters = Object.values(filters).some(Boolean);
  const details = parseDetail(selected?.detailJson);
  return <main className="page-stack">
    <PageHeading title="安全审计" description="查看谁在什么时间对哪个对象执行了什么操作，以及服务端最终裁决结果。" />
    <form className="surface governance-form" aria-label="审计筛选" onSubmit={applyFilters}>
      <div className="form-grid">
        <Field label="操作人编号"><Input inputMode="numeric" value={draftFilters.actorId} onChange={(_, data) => updateDraftFilter('actorId', data.value)} /></Field>
        <Field label="事件类型"><Input value={draftFilters.eventType} onChange={(_, data) => updateDraftFilter('eventType', data.value)} /></Field>
        <Field label="对象类型"><Input value={draftFilters.subjectType} onChange={(_, data) => updateDraftFilter('subjectType', data.value)} /></Field>
        <Field label="对象编号或引用"><Input value={draftFilters.subjectId} onChange={(_, data) => updateDraftFilter('subjectId', data.value)} /></Field>
        <Field label="追踪编号"><Input value={draftFilters.traceId} onChange={(_, data) => updateDraftFilter('traceId', data.value)} /></Field>
        <Field label="风险等级"><Select value={draftFilters.riskLevel} onChange={(_, data) => updateDraftFilter('riskLevel', data.value)}><option value="">全部风险</option><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option><option value="CRITICAL">严重</option></Select></Field>
        <Field label="结果状态"><Select value={draftFilters.resultStatus} onChange={(_, data) => updateDraftFilter('resultStatus', data.value)}><option value="">全部结果</option><option value="SUCCESS">成功</option><option value="FAILED">失败</option><option value="PENDING_APPROVAL">待审批</option></Select></Field>
        <Field label="开始时间" hint="按当前设备时区"><Input type="datetime-local" value={draftFilters.createdFrom} onChange={(_, data) => updateDraftFilter('createdFrom', data.value)} /></Field>
        <Field label="结束时间" hint="按当前设备时区"><Input type="datetime-local" value={draftFilters.createdTo} onChange={(_, data) => updateDraftFilter('createdTo', data.value)} /></Field>
      </div>
      <div className="toolbar"><Text>筛选仅在当前租户范围内执行。</Text><div className="row-actions"><Button type="submit" appearance="primary">查询</Button><Button type="button" onClick={resetFilters}>重置筛选</Button></div></div>
      {validationError && <MessageBar intent="error"><MessageBarBody>{validationError}</MessageBarBody></MessageBar>}
    </form>
    <RequestFeedback status={status} error={error} onRetry={() => setRetryGeneration((value) => value + 1)} loadingLabel="正在读取安全审计" />
    {status === 'empty' && <section className="empty-state"><DocumentSearch20Regular /><Title3 as="h2">{hasActiveFilters ? '没有符合条件的记录' : '暂无审计记录'}</Title3><Text>{hasActiveFilters ? '请调整筛选条件后重新查询。' : '当前租户还没有可显示的安全事件。'}</Text></section>}
    {rows.length > 0 && result && <section className="master-detail"><div className="surface data-region"><div className="table-wrap"><table aria-label="安全审计列表"><thead><tr><th>发生时间</th><th>事件类型</th><th>操作人</th><th>对象</th><th>风险等级</th><th>结果</th><th>操作</th></tr></thead><tbody>{rows.map((row) => <tr key={row.id}><td>{formatDataTime(row.createdAt)}</td><td>{renderCell(row.eventType)}</td><td>{renderCell(row.actorId)}</td><td>{renderCell(row.subjectType)} / {renderCell(auditSubjectReference(row))}</td><td>{renderCell(row.riskLevel)}</td><td>{renderCell(row.resultStatus)}</td><td><Button appearance="subtle" icon={<Eye20Regular />} aria-label={`查看审计 ${row.id}`} onClick={() => setSelected(row)}>查看</Button></td></tr>)}</tbody></table></div><Pagination page={result.page} pageSize={result.pageSize} total={result.total} totalPages={result.totalPages} onChange={changePage} /></div>
      <aside className="surface detail-panel" aria-label="审计详情">{selected ? <><div><Title3>审计详情</Title3><Badge appearance="tint">{renderCell(selected.riskLevel)}</Badge></div><dl><dt>事件编号</dt><dd>{renderCell(selected.id)}</dd><dt>事件类型</dt><dd>{renderCell(selected.eventType)}</dd><dt>操作对象</dt><dd>{renderCell(selected.subjectType)} / {renderCell(auditSubjectReference(selected))}</dd><dt>追踪编号</dt><dd>{renderCell(selected.traceId)}</dd>{Object.entries(details).map(([key, value]) => <Fragment key={key}><dt>{COLUMN_LABELS[key] || key}</dt><dd>{renderCell(value)}</dd></Fragment>)}</dl></> : <div className="detail-empty"><Eye20Regular /><Text>选择一条记录查看审计上下文</Text></div>}</aside>
    </section>}
  </main>;
}
