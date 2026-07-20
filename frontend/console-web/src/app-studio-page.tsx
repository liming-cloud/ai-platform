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
  makeStyles,
  shorthands,
  tokens,
} from '@fluentui/react-components';
import {
  LockClosed20Regular,
  Warning20Regular,
} from '@fluentui/react-icons';
import {
  createStudioApplication,
  APPLICATION_CAPABILITY_KEYS,
  listApplicationReleases,
  listStudioConfigurationOptions,
  listStudioApplications,
  publishStudioApplication,
  rollbackApplicationRelease,
  parseApplicationCapabilities,
  runStudioPublishPreflight,
  updateStudioApplication,
} from './app-studio-api';
import type {
  ApplicationConfigurationInput,
  ApplicationRelease,
  CreateApplicationInput,
  StudioConfigurationOptions,
  StudioApplication,
  StudioApplicationCapabilities,
  StudioPublishPreflight,
} from './app-studio-api';
import { AsyncState } from './async-state';
import { ChatFinalizationOperations } from './chat-finalization-operations';
import { dialogBackdropMotion, dialogSurfaceMotion } from './dialog-motion';
import { PageHeading } from './page-shared';
import type { CurrentUser, RequestStatus } from './types';

const APPLICATION_TYPES = [
  ['KNOWLEDGE_QA', '知识问答'],
  ['CUSTOMER_SERVICE', '客户服务'],
  ['DOCUMENT_GENERATION', '文档生成'],
  ['DATA_ANALYSIS', '数据分析'],
  ['DEVELOPMENT_ASSISTANT', '研发助手'],
  ['PROCESS_ASSISTANT', '流程助手'],
  ['MULTI_AGENT', '多智能体'],
  ['CUSTOM_WORKFLOW', '自定义工作流'],
] as const;

const EMPTY_CONFIGURATION: CreateApplicationInput = {
  applicationType: 'KNOWLEDGE_QA',
  name: '',
  description: '',
  modelConfigId: '',
  knowledgeBaseIds: [],
  systemPrompt: '',
};

const NO_APPLICATION_CAPABILITIES = Object.fromEntries(
  APPLICATION_CAPABILITY_KEYS.map((key) => [key, false]),
) as unknown as StudioApplicationCapabilities;

const FAIL_CLOSED_APPLICATION_ACCESS = {
  accessMode: 'BUSINESS_USER' as const,
  capabilities: NO_APPLICATION_CAPABILITIES,
};

const useStyles = makeStyles({
  toolbar: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    ...shorthands.gap(tokens.spacingHorizontalM),
  },
  journey: {
    display: 'grid',
    gridTemplateColumns: 'minmax(0, 1.15fr) minmax(320px, 0.85fr)',
    alignItems: 'start',
    ...shorthands.gap(tokens.spacingHorizontalL),
    '@media (max-width: 1100px)': {
      gridTemplateColumns: 'minmax(0, 1fr)',
    },
  },
  catalog: {
    minWidth: 0,
  },
  tableWrap: {
    overflowX: 'auto',
  },
  table: {
    minWidth: '720px',
  },
  selectedRow: {
    backgroundColor: tokens.colorNeutralBackground1Selected,
  },
  detail: {
    minWidth: 0,
    display: 'grid',
    ...shorthands.gap(tokens.spacingVerticalL),
  },
  detailHeader: {
    display: 'flex',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    ...shorthands.gap(tokens.spacingHorizontalM),
  },
  detailIdentity: {
    display: 'grid',
    ...shorthands.gap(tokens.spacingVerticalXS),
  },
  statusLine: {
    display: 'flex',
    flexWrap: 'wrap',
    ...shorthands.gap(tokens.spacingHorizontalS),
    alignItems: 'center',
    color: tokens.colorNeutralForeground2,
  },
  actions: {
    display: 'flex',
    flexWrap: 'wrap',
    ...shorthands.gap(tokens.spacingHorizontalS),
  },
  section: {
    display: 'grid',
    ...shorthands.gap(tokens.spacingVerticalS),
  },
  definitionList: {
    display: 'grid',
    gridTemplateColumns: 'minmax(120px, 0.34fr) minmax(0, 1fr)',
    margin: 0,
    ...shorthands.gap(tokens.spacingVerticalS, tokens.spacingHorizontalM),
    '@media (max-width: 560px)': {
      gridTemplateColumns: 'minmax(0, 1fr)',
    },
  },
  term: {
    color: tokens.colorNeutralForeground2,
  },
  value: {
    margin: 0,
    overflowWrap: 'anywhere',
    whiteSpace: 'pre-wrap',
  },
  preflight: {
    display: 'grid',
    ...shorthands.gap(tokens.spacingVerticalS),
    ...shorthands.padding(tokens.spacingVerticalM, tokens.spacingHorizontalM),
    ...shorthands.border('1px', 'solid', tokens.colorNeutralStroke2),
    borderRadius: tokens.borderRadiusMedium,
  },
  issue: {
    display: 'grid',
    gridTemplateColumns: 'auto minmax(0, 1fr)',
    alignItems: 'start',
    ...shorthands.gap(tokens.spacingHorizontalS),
  },
  trace: {
    display: 'block',
    color: tokens.colorNeutralForeground2,
    overflowWrap: 'anywhere',
  },
  dialogForm: {
    minWidth: 'min(560px, calc(100vw - 64px))',
    display: 'grid',
    ...shorthands.gap(tokens.spacingVerticalM),
    '@media (max-width: 640px)': {
      minWidth: 0,
    },
  },
  empty: {
    display: 'grid',
    justifyItems: 'start',
    ...shorthands.gap(tokens.spacingVerticalS),
  },
  readOnlyNotice: {
    display: 'grid',
    gridTemplateColumns: 'auto minmax(0, 1fr)',
    alignItems: 'start',
    ...shorthands.gap(tokens.spacingHorizontalS),
    ...shorthands.padding(tokens.spacingVerticalM, tokens.spacingHorizontalM),
    backgroundColor: tokens.colorNeutralBackground3,
    borderRadius: tokens.borderRadiusMedium,
  },
  choices: {
    maxHeight: '180px',
    overflowY: 'auto',
    display: 'grid',
    ...shorthands.gap(tokens.spacingVerticalXS),
    ...shorthands.padding(tokens.spacingVerticalS, tokens.spacingHorizontalS),
    ...shorthands.border('1px', 'solid', tokens.colorNeutralStroke2),
    borderRadius: tokens.borderRadiusMedium,
  },
});

type RawApplication = Partial<StudioApplication> & { status?: string };
type ConfigField = 'name' | 'description' | 'modelConfigId' | 'knowledgeBaseIds' | 'systemPrompt';

function uniqueIds(value: string): string[] {
  return Array.from(new Set(value.split(/[\n,，]/).map((item) => item.trim()).filter(Boolean)));
}

function normalizedApplicationAccess(
  accessModeValue: unknown,
  capabilitiesValue: unknown,
  fallback?: Pick<StudioApplication, 'accessMode' | 'capabilities'>,
): Pick<StudioApplication, 'accessMode' | 'capabilities'> {
  if (accessModeValue === undefined && capabilitiesValue === undefined && fallback) {
    return fallback;
  }
  if (accessModeValue === 'BUSINESS_USER') {
    return FAIL_CLOSED_APPLICATION_ACCESS;
  }
  if (accessModeValue !== 'OWNER' && accessModeValue !== 'GOVERNANCE_READ_ONLY') {
    return FAIL_CLOSED_APPLICATION_ACCESS;
  }
  const capabilities = parseApplicationCapabilities(capabilitiesValue);
  if (!capabilities) return FAIL_CLOSED_APPLICATION_ACCESS;
  if (accessModeValue === 'GOVERNANCE_READ_ONLY') {
    return APPLICATION_CAPABILITY_KEYS.slice(0, -1).some((key) => capabilities[key])
      || !capabilities.canReadReleaseEvidence
      ? FAIL_CLOSED_APPLICATION_ACCESS
      : { accessMode: accessModeValue, capabilities: { ...NO_APPLICATION_CAPABILITIES, canReadReleaseEvidence: true } };
  }
  return { accessMode: accessModeValue, capabilities };
}

function normalizedApplication(row: RawApplication, fallback?: StudioApplication): StudioApplication {
  const activeReleaseVersion = Number(row.activeReleaseVersion);
  const access = normalizedApplicationAccess(row.accessMode, row.capabilities, fallback);
  const exposesRuntimeConfiguration = access.accessMode === 'OWNER';
  return {
    applicationId: String(row.applicationId || ''),
    applicationCode: String(row.applicationCode || ''),
    name: String(row.name || '未命名应用'),
    description: String(row.description || ''),
    applicationType: typeof row.applicationType === 'string' && row.applicationType ? row.applicationType : null,
    modelConfigId: exposesRuntimeConfiguration && typeof row.modelConfigId === 'string' && row.modelConfigId ? row.modelConfigId : null,
    knowledgeBaseIds: exposesRuntimeConfiguration && Array.isArray(row.knowledgeBaseIds) ? row.knowledgeBaseIds.map(String) : [],
    systemPrompt: exposesRuntimeConfiguration && typeof row.systemPrompt === 'string' && row.systemPrompt ? row.systemPrompt : null,
    publicationStatus: String(row.publicationStatus || row.status || 'DRAFT').toUpperCase(),
    activeReleaseId: row.activeReleaseId ? String(row.activeReleaseId) : null,
    activeReleaseVersion: Number.isInteger(activeReleaseVersion) && activeReleaseVersion > 0 ? activeReleaseVersion : null,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    accessMode: access.accessMode,
    capabilities: access.capabilities,
  };
}

function mergedApplication(current: StudioApplication, row: RawApplication): StudioApplication {
  const merged: RawApplication = { ...current, ...row };
  if (!row.publicationStatus && row.status) merged.publicationStatus = row.status;
  return normalizedApplication(merged, current);
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING_APPROVAL: '等待审批',
    PUBLISHED: '已发布',
    OFFLINE: '已下线',
  };
  return labels[status] || status;
}

function applicationTypeLabel(type: string | null): string {
  if (!type) return '仅发布证据可见';
  return APPLICATION_TYPES.find(([value]) => value === type)?.[1] || type;
}

function traceIdOf(error?: Error): string {
  if (!error || !('traceId' in error)) return '';
  return String((error as Error & { traceId?: string }).traceId || '');
}

function isAbortError(reason: unknown): boolean {
  return reason instanceof Error && reason.name === 'AbortError';
}

function errorOf(reason: unknown, fallback: string): Error {
  return reason instanceof Error ? reason : new Error(fallback);
}

function freshRequestId(action: string): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  throw new Error(`当前浏览器无法生成安全的${action}请求编号，请升级浏览器后重试。`);
}

function rollbackImpact(active: ApplicationRelease, target: ApplicationRelease): string[] {
  const removed = active.knowledgeBaseIds.filter((id) => !target.knowledgeBaseIds.includes(id));
  const added = target.knowledgeBaseIds.filter((id) => !active.knowledgeBaseIds.includes(id));
  return [
    `模型配置：${active.modelConfigId} → ${target.modelConfigId}`,
    removed.length ? `知识库将移除：${removed.join('、')}` : '知识库不会移除',
    added.length ? `知识库将增加：${added.join('、')}` : '知识库不会增加',
    `授权受众：${active.allowedUserCount} → ${target.allowedUserCount}`,
    `系统提示词：${active.systemPromptSha256 === target.systemPromptSha256 ? '内容哈希不变' : '内容哈希将变化'}`,
  ];
}

function ErrorNotice({ error, retryLabel, onRetry }: { error: Error; retryLabel?: string; onRetry?: () => void }) {
  const traceId = traceIdOf(error);
  return <MessageBar intent="error" role="alert"><MessageBarBody>
    <span>{error.message}</span>
    {traceId && <span className="trace-hint">错误追踪：{traceId}</span>}
    {onRetry && <Button appearance="transparent" size="small" onClick={onRetry}>{retryLabel || '重试'}</Button>}
  </MessageBarBody></MessageBar>;
}

function configurationIsValid(configuration: CreateApplicationInput): boolean {
  return Boolean(
    configuration.name.trim()
    && configuration.description.trim()
    && configuration.modelConfigId.trim()
    && configuration.knowledgeBaseIds.length
    && configuration.systemPrompt.trim(),
  );
}

export function AppStudioPage({ user }: { user: CurrentUser }) {
  const styles = useStyles();
  const canRead = user.permissionCodes.includes('app-studio:application:read');
  const canCreate = user.permissionCodes.includes('app-studio:application:create');
  const [applications, setApplications] = useState<StudioApplication[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const selectedIdRef = useRef('');
  const [catalogStatus, setCatalogStatus] = useState<RequestStatus>('loading');
  const [catalogError, setCatalogError] = useState<Error>();
  const [preflight, setPreflight] = useState<StudioPublishPreflight>();
  const [preflightError, setPreflightError] = useState<Error>();
  const [checkingId, setCheckingId] = useState('');
  const [operationNotice, setOperationNotice] = useState('');

  const [configMode, setConfigMode] = useState<'create' | 'edit'>();
  const [configuration, setConfiguration] = useState<CreateApplicationInput>(EMPTY_CONFIGURATION);
  const [configurationText, setConfigurationText] = useState('');
  const [configurationOptions, setConfigurationOptions] = useState<StudioConfigurationOptions>({ models: [], knowledgeBases: [] });
  const [optionStatus, setOptionStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [optionError, setOptionError] = useState<Error>();
  const [touchedFields, setTouchedFields] = useState<Set<ConfigField>>(new Set());
  const [configBusy, setConfigBusy] = useState(false);
  const [configError, setConfigError] = useState<Error>();

  const [publishDialogOpen, setPublishDialogOpen] = useState(false);
  const [publishReason, setPublishReason] = useState('');
  const [publishBusy, setPublishBusy] = useState(false);
  const [publishError, setPublishError] = useState<Error>();

  const [releases, setReleases] = useState<ApplicationRelease[]>([]);
  const [releaseStatus, setReleaseStatus] = useState<RequestStatus>('idle');
  const [releaseError, setReleaseError] = useState<Error>();
  const [rollbackTarget, setRollbackTarget] = useState<ApplicationRelease>();
  const [rollbackRequestId, setRollbackRequestId] = useState('');
  const [rollbackReason, setRollbackReason] = useState('');
  const [rollbackConfirmed, setRollbackConfirmed] = useState(false);
  const [rollbackBusy, setRollbackBusy] = useState(false);
  const [rollbackError, setRollbackError] = useState<Error>();

  const catalogController = useRef<AbortController>();
  const preflightController = useRef<AbortController>();
  const actionController = useRef<AbortController>();
  const optionController = useRef<AbortController>();
  const releaseController = useRef<AbortController>();
  const preflightGeneration = useRef(0);
  const releaseGeneration = useRef(0);
  const rollbackInFlight = useRef(false);

  const loadApplications = useCallback(async () => {
    if (!canRead) {
      setApplications([]);
      setSelectedId('');
      selectedIdRef.current = '';
      setCatalogStatus('idle');
      return;
    }
    catalogController.current?.abort();
    const controller = new AbortController();
    catalogController.current = controller;
    setApplications([]);
    setSelectedId('');
    selectedIdRef.current = '';
    setCatalogError(undefined);
    setCatalogStatus('loading');
    setPreflight(undefined);
    setPreflightError(undefined);
    try {
      const rows = (await listStudioApplications(controller.signal))
        .map((row) => normalizedApplication(row))
        .filter((application) => application.applicationId);
      if (controller.signal.aborted) return;
      setApplications(rows);
      const firstId = rows[0]?.applicationId || '';
      setSelectedId(firstId);
      selectedIdRef.current = firstId;
      setCatalogStatus(rows.length ? 'success' : 'empty');
    } catch (reason) {
      if (controller.signal.aborted || isAbortError(reason)) return;
      setCatalogError(errorOf(reason, '应用目录读取失败'));
      setCatalogStatus('error');
    }
  }, [canRead]);

  useEffect(() => {
    void loadApplications();
    return () => {
      catalogController.current?.abort();
      preflightController.current?.abort();
      actionController.current?.abort();
      optionController.current?.abort();
      releaseController.current?.abort();
    };
  }, [loadApplications]);

  const selectedApplication = useMemo(
    () => applications.find((application) => application.applicationId === selectedId),
    [applications, selectedId],
  );
  const activeRelease = useMemo(() => releases.find((release) => release.active), [releases]);

  const loadReleases = useCallback(async (applicationId: string) => {
    releaseController.current?.abort();
    const controller = new AbortController();
    const generation = releaseGeneration.current + 1;
    releaseController.current = controller;
    releaseGeneration.current = generation;
    setReleases([]);
    setReleaseError(undefined);
    setReleaseStatus('loading');
    try {
      const rows = await listApplicationReleases(applicationId, controller.signal);
      if (controller.signal.aborted || generation !== releaseGeneration.current || selectedIdRef.current !== applicationId) return;
      setReleases(rows);
      setReleaseStatus(rows.length ? 'success' : 'empty');
    } catch (reason) {
      if (controller.signal.aborted || generation !== releaseGeneration.current || isAbortError(reason)) return;
      setReleaseError(errorOf(reason, '发布版本读取失败'));
      setReleaseStatus('error');
    }
  }, []);

  useEffect(() => {
    if (!selectedApplication?.capabilities.canReadReleaseEvidence || !selectedApplication.activeReleaseId) {
      releaseController.current?.abort();
      setReleases([]);
      setReleaseError(undefined);
      setReleaseStatus('idle');
      return;
    }
    void loadReleases(selectedApplication.applicationId);
    return () => releaseController.current?.abort();
  }, [
    loadReleases,
    selectedApplication?.activeReleaseId,
    selectedApplication?.applicationId,
    selectedApplication?.capabilities.canReadReleaseEvidence,
  ]);

  function selectApplication(applicationId: string) {
    preflightController.current?.abort();
    preflightGeneration.current += 1;
    selectedIdRef.current = applicationId;
    setSelectedId(applicationId);
    setPreflight(undefined);
    setPreflightError(undefined);
    setCheckingId('');
    setOperationNotice('');
    setPublishDialogOpen(false);
    setRollbackTarget(undefined);
    setReleases([]);
    setReleaseError(undefined);
    setReleaseStatus('idle');
  }

  function startAction(): AbortController {
    actionController.current?.abort();
    const controller = new AbortController();
    actionController.current = controller;
    return controller;
  }

  function closeConfigurationDialog() {
    if (configBusy) return;
    optionController.current?.abort();
    setConfigMode(undefined);
    setConfigError(undefined);
    setTouchedFields(new Set());
  }

  async function loadConfigurationOptions() {
    optionController.current?.abort();
    const controller = new AbortController();
    optionController.current = controller;
    setConfigurationOptions({ models: [], knowledgeBases: [] });
    setOptionStatus('loading');
    setOptionError(undefined);
    try {
      const options = await listStudioConfigurationOptions(controller.signal);
      if (controller.signal.aborted) return;
      setConfigurationOptions(options);
      setOptionStatus('success');
    } catch (reason) {
      if (controller.signal.aborted || isAbortError(reason)) return;
      setConfigurationOptions({ models: [], knowledgeBases: [] });
      setOptionError(errorOf(reason, '可选模型和知识库目录读取失败'));
      setOptionStatus('error');
    }
  }

  function openCreateDialog() {
    setConfiguration({ ...EMPTY_CONFIGURATION, knowledgeBaseIds: [] });
    setConfigurationText('');
    setTouchedFields(new Set());
    setConfigError(undefined);
    setConfigMode('create');
    void loadConfigurationOptions();
  }

  function openEditDialog() {
    if (!selectedApplication?.capabilities.canEditConfiguration) return;
    setConfiguration({
      applicationType: selectedApplication.applicationType || 'CUSTOM_WORKFLOW',
      name: selectedApplication.name,
      description: selectedApplication.description || '',
      modelConfigId: selectedApplication.modelConfigId || '',
      knowledgeBaseIds: [...selectedApplication.knowledgeBaseIds],
      systemPrompt: selectedApplication.systemPrompt || '',
    });
    setConfigurationText(selectedApplication.knowledgeBaseIds.join(', '));
    setTouchedFields(new Set());
    setConfigError(undefined);
    setConfigMode('edit');
    void loadConfigurationOptions();
  }

  function updateConfiguration<K extends keyof CreateApplicationInput>(key: K, value: CreateApplicationInput[K]) {
    setConfiguration((current) => ({ ...current, [key]: value }));
  }

  function touch(field: ConfigField) {
    setTouchedFields((current) => new Set(current).add(field));
  }

  function toggleKnowledgeBase(knowledgeBaseId: string, checked: boolean) {
    const next = checked
      ? Array.from(new Set([...configuration.knowledgeBaseIds, knowledgeBaseId]))
      : configuration.knowledgeBaseIds.filter((item) => item !== knowledgeBaseId);
    setConfiguration((current) => ({ ...current, knowledgeBaseIds: next }));
    setConfigurationText(next.join(', '));
  }

  function configurationIssue(field: ConfigField): string | undefined {
    if (!touchedFields.has(field)) return undefined;
    if (field === 'knowledgeBaseIds') return configuration.knowledgeBaseIds.length ? undefined : '至少填写一个知识库 ID。';
    const value = configuration[field];
    return typeof value === 'string' && value.trim() ? undefined : '此字段不能为空。';
  }

  async function saveConfiguration() {
    if (!configMode || !configurationIsValid(configuration)) return;
    const controller = startAction();
    setConfigBusy(true);
    setConfigError(undefined);
    setOperationNotice('');
    try {
      if (configMode === 'create') {
        const result = await createStudioApplication({
          ...configuration,
          name: configuration.name.trim(),
          description: configuration.description.trim(),
          modelConfigId: configuration.modelConfigId.trim(),
          knowledgeBaseIds: configuration.knowledgeBaseIds,
          systemPrompt: configuration.systemPrompt.trim(),
        }, controller.signal);
        if (controller.signal.aborted) return;
        const created = normalizedApplication({
          ...configuration,
          ...result,
          description: configuration.description.trim(),
          systemPrompt: configuration.systemPrompt.trim(),
          updatedAt: result.createdAt,
        });
        setApplications((current) => [created, ...current]);
        selectedIdRef.current = created.applicationId;
        setSelectedId(created.applicationId);
        setCatalogStatus('success');
        setOperationNotice('应用草稿已创建。请继续核对访问策略并完成发布预检。');
      } else if (selectedApplication?.capabilities.canEditConfiguration) {
        const input: ApplicationConfigurationInput = {
          name: configuration.name.trim(),
          description: configuration.description.trim(),
          modelConfigId: configuration.modelConfigId.trim(),
          knowledgeBaseIds: configuration.knowledgeBaseIds,
          systemPrompt: configuration.systemPrompt.trim(),
        };
        const result = await updateStudioApplication(selectedApplication.applicationId, input, controller.signal);
        if (controller.signal.aborted) return;
        const updated = mergedApplication(selectedApplication, result);
        setApplications((current) => current.map((application) => (
          application.applicationId === updated.applicationId ? updated : application
        )));
        setPreflight(undefined);
        setPreflightError(undefined);
        setOperationNotice(selectedApplication.activeReleaseVersion
          ? `草稿配置已保存，活动版本 v${selectedApplication.activeReleaseVersion} 未改变；之前的发布预检已失效。`
          : '应用配置已保存，之前的发布预检已失效。');
      }
      optionController.current?.abort();
      setConfigMode(undefined);
      setTouchedFields(new Set());
    } catch (reason) {
      if (!controller.signal.aborted && !isAbortError(reason)) setConfigError(errorOf(reason, '应用配置保存失败'));
    } finally {
      if (!controller.signal.aborted) setConfigBusy(false);
    }
  }

  async function checkApplication(applicationId = selectedIdRef.current) {
    const application = applications.find((item) => item.applicationId === applicationId);
    if (!applicationId || !application?.capabilities.canRunPublishPreflight) return;
    preflightController.current?.abort();
    const controller = new AbortController();
    const generation = preflightGeneration.current + 1;
    preflightController.current = controller;
    preflightGeneration.current = generation;
    selectedIdRef.current = applicationId;
    setSelectedId(applicationId);
    setPreflight(undefined);
    setPreflightError(undefined);
    setOperationNotice('');
    setCheckingId(applicationId);
    try {
      const result = await runStudioPublishPreflight(applicationId, freshRequestId('发布'), controller.signal);
      if (!controller.signal.aborted
        && generation === preflightGeneration.current
        && selectedIdRef.current === applicationId
        && result.applicationId === applicationId) {
        setPreflight(result);
      }
    } catch (reason) {
      if (!controller.signal.aborted
        && generation === preflightGeneration.current
        && selectedIdRef.current === applicationId
        && !isAbortError(reason)) {
        setPreflightError(errorOf(reason, '发布预检失败'));
      }
    } finally {
      if (generation === preflightGeneration.current) setCheckingId('');
    }
  }

  function openPublishDialog() {
    if (!selectedApplication?.capabilities.canPublish
      || !preflight?.passed
      || preflight.applicationId !== selectedIdRef.current) return;
    setPublishReason('');
    setPublishError(undefined);
    setPublishDialogOpen(true);
  }

  function closePublishDialog() {
    if (publishBusy) return;
    setPublishDialogOpen(false);
    setPublishError(undefined);
  }

  async function publish() {
    const application = selectedApplication;
    const frozenPreflight = preflight;
    if (!application?.capabilities.canPublish
      || !frozenPreflight?.passed
      || frozenPreflight.applicationId !== application.applicationId
      || !publishReason.trim()) return;
    const controller = startAction();
    setPublishBusy(true);
    setPublishError(undefined);
    setOperationNotice('');
    try {
      const result = await publishStudioApplication(
        application.applicationId,
        frozenPreflight.publishRequestId,
        publishReason.trim(),
        controller.signal,
      );
      if (controller.signal.aborted || selectedIdRef.current !== application.applicationId) return;
      const published = mergedApplication(application, result);
      if (!published.activeReleaseId || !published.activeReleaseVersion) {
        throw new Error('发布响应缺少活动版本证据，已停止更新控制台状态。');
      }
      setApplications((current) => current.map((item) => (
        item.applicationId === published.applicationId ? published : item
      )));
      setPreflight(undefined);
      setPublishDialogOpen(false);
      setOperationNotice(`应用已发布为活动版本 v${published.activeReleaseVersion}，业务用户的新请求将使用该不可变快照。`);
    } catch (reason) {
      if (!controller.signal.aborted && !isAbortError(reason)) setPublishError(errorOf(reason, '应用发布失败'));
    } finally {
      if (!controller.signal.aborted) setPublishBusy(false);
    }
  }

  function openRollbackDialog(target: ApplicationRelease) {
    if (!selectedApplication?.capabilities.canRollback
      || !selectedApplication.activeReleaseId
      || !activeRelease
      || target.active) return;
    setRollbackTarget(target);
    setRollbackReason('');
    setRollbackConfirmed(false);
    setRollbackError(undefined);
    try {
      setRollbackRequestId(freshRequestId('回滚'));
    } catch (reason) {
      setRollbackRequestId('');
      setRollbackError(errorOf(reason, '回滚请求编号生成失败'));
    }
  }

  function closeRollbackDialog() {
    if (rollbackBusy) return;
    setRollbackTarget(undefined);
    setRollbackRequestId('');
    setRollbackError(undefined);
  }

  async function rollback() {
    const application = selectedApplication;
    const target = rollbackTarget;
    if (
      rollbackInFlight.current || !application?.capabilities.canRollback || !target || target.active || !activeRelease
      || !rollbackRequestId || !rollbackReason.trim() || !rollbackConfirmed
    ) return;
    rollbackInFlight.current = true;
    const controller = startAction();
    setRollbackBusy(true);
    setRollbackError(undefined);
    setOperationNotice('');
    try {
      const result = await rollbackApplicationRelease(application.applicationId, target.releaseId, {
        rollbackRequestId,
        reason: rollbackReason.trim(),
      }, controller.signal);
      if (controller.signal.aborted || selectedIdRef.current !== application.applicationId) return;
      const activated = mergedApplication(application, result);
      if (!activated.activeReleaseId || !activated.activeReleaseVersion) {
        throw new Error('回滚响应缺少新活动版本证据，已停止更新控制台状态。');
      }
      setApplications((current) => current.map((item) => (
        item.applicationId === activated.applicationId ? activated : item
      )));
      setRollbackTarget(undefined);
      setRollbackRequestId('');
      setOperationNotice(`已基于 v${target.version} 创建并激活 v${activated.activeReleaseVersion}；历史版本保持不可变。`);
    } catch (reason) {
      if (!controller.signal.aborted && !isAbortError(reason)) setRollbackError(errorOf(reason, '应用版本回滚失败'));
    } finally {
      rollbackInFlight.current = false;
      if (!controller.signal.aborted) setRollbackBusy(false);
    }
  }

  const configDialogTitle = configMode === 'create' ? '创建 AI 应用' : '编辑应用配置';
  const selectedModelLabel = configurationOptions.models.find((model) => model.id === configuration.modelConfigId)?.label;
  const governanceReadOnly = selectedApplication?.accessMode === 'GOVERNANCE_READ_ONLY';
  const ownerAccess = selectedApplication?.accessMode === 'OWNER';

  return <main className="page-stack">
    <PageHeading
      title="应用工作室"
      description="从完整配置和访问边界开始，经过服务端预检后发布可审计的 AI 应用。"
    />
    <div className={styles.toolbar}>
      <Text>工作路径：维护草稿、核对范围、预检、发布不可变版本、按证据回滚。</Text>
      {canCreate && <Button appearance="primary" onClick={openCreateDialog}>创建应用</Button>}
    </div>

    {!canRead && <MessageBar intent="warning"><MessageBarBody>当前账号没有应用读取权限，应用目录和配置保持隐藏。</MessageBarBody></MessageBar>}
    {catalogStatus === 'loading' && <AsyncState status="loading" loadingLabel="正在读取应用目录" />}
    {catalogStatus === 'error' && catalogError && <ErrorNotice error={catalogError} retryLabel="重试读取应用" onRetry={() => { void loadApplications(); }} />}
    {catalogStatus === 'empty' && <section className={`empty-state ${styles.empty}`}>
      <Title3>当前租户还没有 AI 应用</Title3>
      <Text>{canCreate ? '使用右上角创建入口建立第一个受控草稿。' : '当前账号只有读取权限，请联系租户管理员创建应用。'}</Text>
    </section>}

    {catalogStatus === 'success' && applications.length > 0 && <section className={styles.journey}>
      <div className={`surface data-region ${styles.catalog}`}>
        <div className={styles.tableWrap}>
          <table aria-label="应用治理列表" className={styles.table}>
            <thead><tr><th>应用名称</th><th>类型</th><th>模型配置</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>{applications.map((application) => <tr className={selectedId === application.applicationId ? styles.selectedRow : undefined} key={application.applicationId}>
              <td><strong>{application.name}</strong><span className={styles.trace}>{application.applicationCode || application.applicationId}</span></td>
              <td>{applicationTypeLabel(application.applicationType)}</td>
              <td>{application.accessMode === 'OWNER' ? application.modelConfigId || '未配置' : '运行配置受保护'}</td>
              <td><Badge appearance="tint">{application.activeReleaseVersion ? `活动 v${application.activeReleaseVersion}` : statusLabel(application.publicationStatus)}</Badge></td>
              <td><div className="row-actions">
                <Button appearance={selectedId === application.applicationId ? 'primary' : 'subtle'} aria-pressed={selectedId === application.applicationId} onClick={() => selectApplication(application.applicationId)}>
                  {application.accessMode === 'OWNER' ? '治理' : application.accessMode === 'GOVERNANCE_READ_ONLY' ? '查看证据' : '查看状态'}
                </Button>
                {application.capabilities.canRunPublishPreflight && <Button disabled={Boolean(checkingId)} onClick={() => { void checkApplication(application.applicationId); }}>
                  {checkingId === application.applicationId ? '正在预检' : '发布预检'}
                </Button>}
              </div></td>
            </tr>)}</tbody>
          </table>
        </div>
      </div>

      <aside className={`surface detail-panel ${styles.detail}`} aria-label="应用工作室详情">
        {selectedApplication ? <>
          <div className={styles.detailHeader}>
            <div className={styles.detailIdentity}>
              <Title2 as="h2">{selectedApplication.name}</Title2>
              <span className={styles.statusLine}>当前状态：{selectedApplication.activeReleaseVersion ? `活动版本 v${selectedApplication.activeReleaseVersion}` : statusLabel(selectedApplication.publicationStatus)}<span>应用编号 {selectedApplication.applicationId}</span></span>
            </div>
          </div>
          <div className={styles.actions}>
            {selectedApplication.capabilities.canEditConfiguration && <Button
              aria-describedby={selectedApplication.activeReleaseVersion ? 'published-edit-boundary' : undefined}
              onClick={openEditDialog}
            >编辑配置</Button>}
            {selectedApplication.capabilities.canManageAccessPolicy && <Button
              aria-describedby="application-access-read-only-reason"
              disabled
              icon={<LockClosed20Regular />}
            >访问策略只读</Button>}
            {selectedApplication.capabilities.canRunPublishPreflight && <Button disabled={Boolean(checkingId)} onClick={() => { void checkApplication(); }}>重新预检</Button>}
            {selectedApplication.capabilities.canPublish && <Button appearance="primary" disabled={!preflight?.passed || preflight.applicationId !== selectedId || publishBusy} onClick={openPublishDialog}>确认发布</Button>}
          </div>

          {operationNotice && <MessageBar intent="success" role="status"><MessageBarBody>{operationNotice}</MessageBarBody></MessageBar>}
          {preflightError && <ErrorNotice error={preflightError} retryLabel="重试预检" onRetry={() => { void checkApplication(); }} />}
          {!ownerAccess && <div
            className={styles.readOnlyNotice}
            role="note"
            aria-label={governanceReadOnly ? '治理只读范围说明' : '业务使用范围说明'}
          >
            <LockClosed20Regular />
            <strong>{governanceReadOnly
              ? '冻结发布证据只读；草稿与写操作不可用'
              : '业务视图仅展示身份与状态；运行配置受保护'}</strong>
          </div>}
          {ownerAccess && selectedApplication.activeReleaseVersion && <div id="published-edit-boundary" className={styles.readOnlyNotice}>
            <LockClosed20Regular />
            <div><strong>活动版本保持不可变</strong><Text as="p">修改草稿不会影响活动版本 v{selectedApplication.activeReleaseVersion}。只有重新预检并发布后，新版本才会承接业务流量；访问策略由受控服务端授权路径调整，当前控制台不提供盲整体替换。</Text></div>
          </div>}

          {selectedApplication.capabilities.canRunPublishPreflight && preflight?.applicationId === selectedId && <section className={styles.preflight} aria-label="发布预检结果">
            <div><strong>{preflight.passed ? '发布预检通过' : '发布预检未通过，应用保持草稿且禁止发布'}</strong><span className={styles.trace}>预检追踪：{preflight.traceId}</span></div>
            {preflight.blockers.map((issue) => <div className={styles.issue} key={`blocker-${issue.code}-${issue.resourceId}`}><Warning20Regular /><span><strong>{issue.message}</strong><span className={styles.trace}>{issue.code}，资源 {issue.resourceType} / {issue.resourceId}</span></span></div>)}
            {preflight.warnings.map((issue) => <div className={styles.issue} key={`warning-${issue.code}-${issue.resourceId}`}><Warning20Regular /><span><strong>{issue.message}</strong><span className={styles.trace}>{issue.code}，资源 {issue.resourceType} / {issue.resourceId}</span></span></div>)}
          </section>}

          {ownerAccess && <section className={styles.section} aria-labelledby="application-configuration-title">
            <Title3 as="h3" id="application-configuration-title">{selectedApplication.activeReleaseVersion ? '当前可发布草稿配置' : '当前草稿配置'}</Title3>
            <dl className={styles.definitionList}>
              <dt className={styles.term}>应用说明</dt><dd className={styles.value}>{selectedApplication.description || '未提供'}</dd>
              <dt className={styles.term}>应用类型</dt><dd className={styles.value}>{applicationTypeLabel(selectedApplication.applicationType)}</dd>
              <dt className={styles.term}>模型配置</dt><dd className={styles.value}>{selectedApplication.modelConfigId || '未配置'}</dd>
              <dt className={styles.term}>知识库范围</dt><dd className={styles.value}>{selectedApplication.knowledgeBaseIds.join('、') || '未配置'}</dd>
              <dt className={styles.term}>系统提示词</dt><dd className={styles.value}>{selectedApplication.systemPrompt || '未提供'}</dd>
              <dt className={styles.term}>最近更新时间</dt><dd className={styles.value}>{selectedApplication.updatedAt ? new Date(selectedApplication.updatedAt).toLocaleString('zh-CN') : '服务端未提供'}</dd>
            </dl>
          </section>}

          {selectedApplication.capabilities.canManageAccessPolicy && <section className={styles.section} aria-labelledby="application-access-title">
            <Title3 as="h3" id="application-access-title">访问策略</Title3>
            <div className={styles.readOnlyNotice} id="application-access-read-only-reason">
              <LockClosed20Regular />
              <div>
                <strong>整体替换已安全停用</strong>
                <Text as="p">当前服务端契约未提供当前授权明细、策略版本和条件更新令牌，控制台无法构造可信的变更前快照与差异。现有访问策略继续生效；待公共契约提供可读取快照、版本校验和冲突处理后，才能恢复写入。</Text>
              </div>
            </div>
          </section>}

          {selectedApplication.capabilities.canReadReleaseEvidence && selectedApplication.activeReleaseId && <section className={styles.section} aria-labelledby="application-release-title">
            <Title3 as="h3" id="application-release-title">{selectedApplication.capabilities.canRollback ? '发布版本与回滚' : '冻结发布证据'}</Title3>
            {releaseStatus === 'loading' && <AsyncState status="loading" loadingLabel="正在读取发布版本" />}
            {releaseStatus === 'error' && releaseError && <ErrorNotice error={releaseError} retryLabel="重试读取版本" onRetry={() => { void loadReleases(selectedApplication.applicationId); }} />}
            {releaseStatus === 'empty' && <MessageBar intent="warning"><MessageBarBody>版本证据为空，已禁止回滚。</MessageBarBody></MessageBar>}
            {releaseStatus === 'success' && <div
              className={styles.tableWrap}
              tabIndex={0}
              aria-label="应用发布版本横向滚动区域"
            >
              <table aria-label="应用发布版本" className={styles.table}>
                <thead><tr><th>版本</th><th>不可变配置摘要</th><th>受众</th><th>发布证据</th><th>操作</th></tr></thead>
                <tbody>{releases.map((release) => <tr key={release.releaseId}>
                  <td><strong>v{release.version}</strong>{release.active && <Badge appearance="filled" color="brand">当前活动</Badge>}<span className={styles.trace}>{release.operation === 'ROLLBACK' ? '回滚' : '发布'}{release.sourceReleaseId ? `，来源 ${release.sourceReleaseId}` : ''}</span></td>
                  <td>模型 {release.modelConfigId}<span className={styles.trace}>知识库：{release.knowledgeBaseIds.join('、') || '无'}</span>{release.systemPromptSummary && <span className={styles.trace}>提示词摘要：{release.systemPromptSummary}</span>}<span className={styles.trace}>提示词 SHA-256：{release.systemPromptSha256}</span></td>
                  <td>{release.allowedUserCount} 位授权用户</td>
                  <td>{release.reason}<span className={styles.trace}>发布人 {release.publishedBy}</span><span className={styles.trace}>{new Date(release.createdAt).toLocaleString('zh-CN')}</span><span className={styles.trace}>{release.traceId}</span></td>
                  <td>{release.active || !selectedApplication.capabilities.canRollback
                    ? <Text>{release.active ? '当前活动' : '只读证据'}</Text>
                    : <Button disabled={!activeRelease || rollbackBusy} onClick={() => openRollbackDialog(release)}>回滚到 v{release.version}</Button>}</td>
                </tr>)}</tbody>
              </table>
            </div>}
          </section>}
        </> : <div className={styles.empty}><LockClosed20Regular /><Text>选择一项应用查看配置和发布控制。</Text></div>}
      </aside>
    </section>}

    {configMode && <Dialog open surfaceMotion={dialogSurfaceMotion} onOpenChange={(_, data) => { if (!data.open) closeConfigurationDialog(); }}>
      <DialogSurface backdropMotion={dialogBackdropMotion}><DialogBody>
        <DialogTitle>{configDialogTitle}</DialogTitle>
        <DialogContent><div className={styles.dialogForm}>
          <Text>{configMode === 'create' ? '创建请求会保存一份完整草稿配置。' : '更新请求会整体替换配置快照，并使旧预检失效。'}</Text>
          {optionStatus === 'loading' && <Text role="status">正在读取可选模型和知识库。</Text>}
          {optionStatus === 'error' && optionError && <MessageBar intent="warning"><MessageBarBody>
            <span>可选资源目录暂不可用，仍可按编号继续填写。{optionError.message}</span>
            {traceIdOf(optionError) && <span className="trace-hint">错误追踪：{traceIdOf(optionError)}</span>}
            <Button appearance="transparent" size="small" onClick={() => { void loadConfigurationOptions(); }}>重新读取可选资源</Button>
          </MessageBarBody></MessageBar>}
          <Field label="应用名称" required validationState={configurationIssue('name') ? 'error' : undefined} validationMessage={configurationIssue('name')}>
            <Input value={configuration.name} onBlur={() => touch('name')} onChange={(_, data) => updateConfiguration('name', data.value)} />
          </Field>
          {configMode === 'create' && <Field label="应用类型" required>
            <Select value={configuration.applicationType} onChange={(_, data) => updateConfiguration('applicationType', data.value)}>
              {APPLICATION_TYPES.map(([value, label]) => <option value={value} key={value}>{label}</option>)}
            </Select>
          </Field>}
          <Field label="应用说明" required validationState={configurationIssue('description') ? 'error' : undefined} validationMessage={configurationIssue('description')}>
            <Textarea value={configuration.description} onBlur={() => touch('description')} onChange={(_, data) => updateConfiguration('description', data.value)} />
          </Field>
          <Field label="模型配置 ID" required hint="填写已启用且允许当前应用使用的模型配置编号。" validationState={configurationIssue('modelConfigId') ? 'error' : undefined} validationMessage={configurationIssue('modelConfigId')}>
            <Input list="app-studio-model-options" value={configuration.modelConfigId} onBlur={() => touch('modelConfigId')} onChange={(_, data) => updateConfiguration('modelConfigId', data.value)} />
          </Field>
          <datalist id="app-studio-model-options">{configurationOptions.models.map((model) => <option key={model.id} value={model.id}>{model.label}</option>)}</datalist>
          {selectedModelLabel && <Text size={200}>已选择：{selectedModelLabel}</Text>}
          <Field label="知识库 ID" required hint="使用逗号或换行分隔。预检会再次核对可见范围。" validationState={configurationIssue('knowledgeBaseIds') ? 'error' : undefined} validationMessage={configurationIssue('knowledgeBaseIds')}>
            <Textarea value={configurationText} onBlur={() => touch('knowledgeBaseIds')} onChange={(_, data) => { setConfigurationText(data.value); updateConfiguration('knowledgeBaseIds', uniqueIds(data.value)); }} />
          </Field>
          {configurationOptions.knowledgeBases.length > 0 && <div className={styles.choices} role="group" aria-label="可用知识库快捷选择">
            {configurationOptions.knowledgeBases.map((knowledgeBase) => <Checkbox
              key={knowledgeBase.id}
              checked={configuration.knowledgeBaseIds.includes(knowledgeBase.id)}
              label={knowledgeBase.label}
              onChange={(_, data) => toggleKnowledgeBase(knowledgeBase.id, data.checked === true)}
            />)}
          </div>}
          <Field label="系统提示词" required hint="明确回答边界、拒答规则和输出要求。" validationState={configurationIssue('systemPrompt') ? 'error' : undefined} validationMessage={configurationIssue('systemPrompt')}>
            <Textarea resize="vertical" value={configuration.systemPrompt} onBlur={() => touch('systemPrompt')} onChange={(_, data) => updateConfiguration('systemPrompt', data.value)} />
          </Field>
          {configError && <ErrorNotice error={configError} />}
        </div></DialogContent>
        <DialogActions>
          <Button disabled={configBusy} onClick={closeConfigurationDialog}>取消</Button>
          <Button appearance="primary" disabled={configBusy || !configurationIsValid(configuration)} onClick={() => { void saveConfiguration(); }}>
            {configBusy ? '正在保存' : configError ? '重试保存' : configMode === 'create' ? '保存草稿' : '保存配置'}
          </Button>
        </DialogActions>
      </DialogBody></DialogSurface>
    </Dialog>}

    {publishDialogOpen && <Dialog open surfaceMotion={dialogSurfaceMotion} onOpenChange={(_, data) => { if (!data.open) closePublishDialog(); }}>
      <DialogSurface backdropMotion={dialogBackdropMotion}><DialogBody>
        <DialogTitle>确认发布应用</DialogTitle>
        <DialogContent><div className={styles.dialogForm}>
          <Text>即将发布：<strong>{selectedApplication?.name}</strong></Text>
          <MessageBar intent="warning"><MessageBarBody>发布会让现有授权范围内的业务用户发起新的模型调用。请核对应用、配置和本次预检追踪，再记录业务理由。</MessageBarBody></MessageBar>
          <dl className={styles.definitionList}>
            <dt className={styles.term}>应用编号</dt><dd className={styles.value}>{selectedApplication?.applicationId}</dd>
            <dt className={styles.term}>预检追踪</dt><dd className={styles.value}>{preflight?.traceId || '未提供'}</dd>
            <dt className={styles.term}>预检时间</dt><dd className={styles.value}>{preflight?.checkedAt ? new Date(preflight.checkedAt).toLocaleString('zh-CN') : '未提供'}</dd>
          </dl>
          <Field label="发布理由" required hint="说明上线目的、复核结论或关联变更单。"><Textarea maxLength={512} value={publishReason} onChange={(_, data) => setPublishReason(data.value)} /></Field>
          {publishError && <ErrorNotice error={publishError} />}
        </div></DialogContent>
        <DialogActions>
          <Button disabled={publishBusy} onClick={closePublishDialog}>取消</Button>
          <Button appearance="primary" disabled={publishBusy || !publishReason.trim()} onClick={() => { void publish(); }}>{publishBusy ? '正在发布' : publishError ? '重试发布' : '确认并发布'}</Button>
        </DialogActions>
      </DialogBody></DialogSurface>
    </Dialog>}

    {rollbackTarget && <Dialog open surfaceMotion={dialogSurfaceMotion} onOpenChange={(_, data) => { if (!data.open) closeRollbackDialog(); }}>
      <DialogSurface backdropMotion={dialogBackdropMotion}><DialogBody>
        <DialogTitle>确认回滚到 v{rollbackTarget.version}</DialogTitle>
        <DialogContent><div className={styles.dialogForm}>
          <Text>应用：<strong>{selectedApplication?.name}</strong></Text>
          <MessageBar intent="warning"><MessageBarBody>活动版本 v{activeRelease?.version ?? '未知'} 的新流量将切换到目标快照；历史版本不会被覆盖或删除。</MessageBarBody></MessageBar>
          {activeRelease && <div className={styles.preflight} aria-label="回滚影响摘要">
            <strong>从 v{activeRelease.version} 回滚到 v{rollbackTarget.version} 的影响</strong>
            {rollbackImpact(activeRelease, rollbackTarget).map((item) => <Text key={item}>{item}</Text>)}
          </div>}
          <dl className={styles.definitionList}>
            <dt className={styles.term}>目标发布原因</dt><dd className={styles.value}>{rollbackTarget.reason}</dd>
            <dt className={styles.term}>目标追踪</dt><dd className={styles.value}>{rollbackTarget.traceId}</dd>
          </dl>
          <Field label="回滚理由" required hint="说明故障、审批或关联事件；失败重试复用请求编号。"><Textarea maxLength={1000} value={rollbackReason} onChange={(_, data) => setRollbackReason(data.value)} /></Field>
          <Checkbox checked={rollbackConfirmed} onChange={(_, data) => setRollbackConfirmed(data.checked === true)} label="我确认回滚会创建新的活动版本，并使后续业务请求切换到目标快照" />
          {rollbackError && <ErrorNotice error={rollbackError} />}
        </div></DialogContent>
        <DialogActions>
          <Button disabled={rollbackBusy} onClick={closeRollbackDialog}>取消</Button>
          <Button appearance="primary" disabled={rollbackBusy || !rollbackRequestId || !rollbackReason.trim() || !rollbackConfirmed} onClick={() => { void rollback(); }}>
            {rollbackBusy ? '正在回滚' : rollbackError ? '重试并回滚' : '确认并回滚'}
          </Button>
        </DialogActions>
      </DialogBody></DialogSurface>
    </Dialog>}
    <ChatFinalizationOperations user={user} />
  </main>;
}
