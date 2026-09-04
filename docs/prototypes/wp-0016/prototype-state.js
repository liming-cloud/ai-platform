export const DEFAULT_TENANT_ID = "tenant-spruce";
export const PROTOTYPE_STATE_KEY = "wp0016-business-demo-v1";
export const QUERY_SESSION_KEY = "wp0016-query-session-v1";

export const CONVERSATION_FEATURES = Object.freeze([
  "assistantSelection",
  "conversation",
  "ownHistory",
  "authorizedCitations",
]);

export const TENANT_FEATURES = Object.freeze([
  "knowledgeManagement",
  "modelGovernance",
  "assistantDelivery",
  "evaluationGovernance",
  ...CONVERSATION_FEATURES,
]);

const SCREEN_FEATURE = Object.freeze({
  assistants: "assistantSelection",
  conversation: "conversation",
  "assistants-directory": "assistantDelivery",
  "assistant-create": "assistantDelivery",
  "assistant-draft": "assistantDelivery",
  "assistant-test": "assistantDelivery",
  "assistant-evaluation": "assistantDelivery",
  "assistant-release": "assistantDelivery",
  "channel-binding": "assistantDelivery",
  "knowledge-directory": "knowledgeManagement",
  "knowledge-overview": "knowledgeManagement",
  "knowledge-sources": "knowledgeManagement",
  "source-add": "knowledgeManagement",
  "source-detail": "knowledgeManagement",
  "processing-quality": "knowledgeManagement",
  "sync-result": "knowledgeManagement",
  "dataset-schema": "knowledgeManagement",
  "object-storage-scope": "knowledgeManagement",
  "quick-publication": "knowledgeManagement",
  "index-versions": "knowledgeManagement",
  "index-detail": "knowledgeManagement",
  "index-rebuild": "knowledgeManagement",
  "model-providers": "modelGovernance",
  "model-provider-detail": "modelGovernance",
  "model-configurations": "modelGovernance",
  "model-configuration-detail": "modelGovernance",
  "model-deployments": "modelGovernance",
  "model-deployment-detail": "modelGovernance",
  "routing-policies": "modelGovernance",
  "evaluation-datasets": "evaluationGovernance",
  "evaluation-dataset-detail": "evaluationGovernance",
  "evaluation-run-create": "evaluationGovernance",
  "evaluation-run-detail": "evaluationGovernance",
  "evaluation-comparison": "evaluationGovernance",
  "evaluation-failure-analysis": "evaluationGovernance",
});

export const DEFAULT_QUOTAS = Object.freeze({
  knowledgeBases: 3,
  documentsPerKnowledgeBase: 1000,
  rawContentGbPerKnowledgeBase: 10,
  singleFileMb: 50,
  pastedTextMib: 1,
});

/** 仅供配置交互的合成预填值，不代表生产安全建议或已执行登录锁定。 */
export const TENANT_SECURITY_FIELDS = Object.freeze([
  { key: "sessionHours", label: "会话有效期", unit: "小时", defaultValue: 8 },
  { key: "failedAttempts", label: "登录失败次数", unit: "次", defaultValue: 5 },
  { key: "lockMinutes", label: "锁定时长", unit: "分钟", defaultValue: 15 },
].map(Object.freeze));
const DEFAULT_SECURITY = Object.freeze(Object.fromEntries(TENANT_SECURITY_FIELDS.map(field => [field.key, field.defaultValue])));

/** 原型输入和校验共享字段；这些值不代表生产解析器的容量承诺。 */
export const TENANT_QUOTA_FIELDS = Object.freeze([
  { key: "knowledgeBases", label: "知识库数量", unit: "个", integer: true },
  { key: "documentsPerKnowledgeBase", label: "每库文档数量", unit: "个", integer: true },
  { key: "rawContentGbPerKnowledgeBase", label: "每库内容容量", unit: "GB", integer: false },
  { key: "singleFileMb", label: "单文件大小", unit: "MB", integer: false },
  { key: "pastedTextMib", label: "粘贴文本大小", unit: "MiB", integer: false },
].map(Object.freeze));

function defaultTenantSetup() {
  return { step: 1, organizationName: "", tenantCode: "", administratorAccountId: "", quotas: { ...DEFAULT_QUOTAS } };
}

/** 草稿只接收非秘密白名单字段，不能借此保存密码或额外权限。 */
function tenantSetupFrom(input = {}) {
  const setup = defaultTenantSetup();
  for (const key of ["organizationName", "tenantCode", "administratorAccountId"]) {
    if (typeof input[key] === "string") setup[key] = input[key];
  }
  if (Number.isInteger(input.step) && input.step >= 1 && input.step <= 3) setup.step = input.step;
  for (const { key } of TENANT_QUOTA_FIELDS) {
    if (["number", "string"].includes(typeof input.quotas?.[key])) setup.quotas[key] = input.quotas[key];
  }
  return setup;
}

function validateQuotas(input, usage = {}) {
  const errors = [];
  const quotas = {};
  for (const { key, label, integer } of TENANT_QUOTA_FIELDS) {
    const raw = input?.[key];
    const value = ["number", "string"].includes(typeof raw) ? Number(raw) : NaN;
    if (!Number.isFinite(value) || value <= 0 || value > Number.MAX_SAFE_INTEGER || (integer && !Number.isSafeInteger(value))) {
      errors.push({ field: key, message: `${label}请输入有效的正${integer ? "整数" : "数"}。` });
    } else if (value < (usage[key] || 0)) {
      errors.push({ field: key, message: `${label}不能低于当前已用量 ${usage[key]}。` });
    }
    quotas[key] = value;
  }
  return { quotas, errors };
}

const QUERY_DEMO_ACCOUNT = Object.freeze({
  tenantCode: "spruce-demo",
  username: "demo.user",
  password: "demo-only",
  tenantId: DEFAULT_TENANT_ID,
  accountId: "account-shen",
});

function defaultQueryState() {
  return {
    session: {
      authenticated: false,
      tenantId: null,
      accountId: null,
      epoch: 0,
    },
    currentConversationId: null,
    nextConversationNumber: 1,
    conversations: [],
    loadedEarlierConversationIds: [],
  };
}

const SOURCE_STAGES = Object.freeze([
  "RECEIVED",
  "SCANNING",
  "EXTRACTING",
  "EXTRACTED",
  "CHUNKING",
  "INDEXING",
  "READY",
]);

const FILE_TYPES = Object.freeze({
  txt: { kind: "TXT", mimeTypes: ["text/plain"] },
  md: { kind: "Markdown", mimeTypes: ["text/markdown", "text/plain"] },
  doc: { kind: "DOC", mimeTypes: ["application/msword"] },
  docx: { kind: "DOCX", mimeTypes: ["application/vnd.openxmlformats-officedocument.wordprocessingml.document"] },
  xls: { kind: "XLS", mimeTypes: ["application/vnd.ms-excel"] },
  xlsx: { kind: "XLSX", mimeTypes: ["application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"] },
  csv: { kind: "CSV", mimeTypes: ["text/csv", "text/plain"] },
  pdf: { kind: "PDF", mimeTypes: ["application/pdf"] },
  png: { kind: "PNG", mimeTypes: ["image/png"] },
  jpg: { kind: "JPEG", mimeTypes: ["image/jpeg"] },
  jpeg: { kind: "JPEG", mimeTypes: ["image/jpeg"] },
  webp: { kind: "WebP", mimeTypes: ["image/webp"] },
});

const INDEX_BUILD_STAGES = Object.freeze([
  { stage: "MANIFEST_FREEZING", progress: 10 },
  { stage: "CHUNKING", progress: 35 },
  { stage: "EMBEDDING", progress: 65 },
  { stage: "HYBRID_INDEXING", progress: 85 },
  { stage: "EVALUATING", progress: 95 },
]);

const INDEX_QUALITY_GATES = Object.freeze({
  recallAt10: { label: "Recall@10", accepts: value => value >= 0.85 },
  citationPrecision: { label: "引用正确率", accepts: value => value >= 0.95 },
  refusalAccuracy: { label: "拒答准确率", accepts: value => value >= 0.98 },
  unauthorizedLeakage: { label: "越权泄漏", accepts: value => value === 0 },
  retrievalP95Ms: { label: "检索 p95", accepts: value => value < 800 },
});

function indexConfig(embeddingDimension = 1536, parserVersion = "typed-parser-v4", chunkingStrategy = "structure-parent-child-v3") {
  return {
    embeddingModel: "text-embedding-3-large",
    embeddingDimension,
    normalization: "L2",
    distanceMetric: "cosine",
    parserVersion,
    chunkingStrategy,
    retrievalStrategy: "dense 50 + sparse 50 + RRF k=60 + rerank 30 -> 8",
  };
}

function defaultIndexVersions(knowledgeBaseId, activeIndex) {
  if (knowledgeBaseId === "kb-handbook") {
    return [
      {
        id: "idx-v12",
        status: "READY_FOR_REVIEW",
        stage: "READY_FOR_REVIEW",
        trigger: "CONFIGURATION_CHANGE",
        changedFields: ["embeddingDimension"],
        reason: "Embedding 维度由 1024 变更为 1536",
        config: indexConfig(),
        progress: 100,
        documents: 428,
        chunks: 3486,
        evaluationSet: "eval-set-v6",
        evaluationQuestionCount: 100,
        quality: { recallAt10: 0.88, citationPrecision: 0.97, refusalAccuracy: 0.99, unauthorizedLeakage: 0, retrievalP95Ms: 612 },
        failedGates: [],
        activeIndexAtCreation: "idx-v11",
        updatedAt: "今天 17:44",
      },
      {
        id: "idx-v11",
        status: "ACTIVE",
        stage: "ACTIVE",
        trigger: "MANUAL_REBUILD",
        changedFields: [],
        reason: "当前活动索引",
        config: indexConfig(1024, "typed-parser-v3", "structure-parent-child-v2"),
        progress: 100,
        documents: 428,
        chunks: 3312,
        quality: { recallAt10: 0.86, citationPrecision: 0.96, refusalAccuracy: 0.99, unauthorizedLeakage: 0, retrievalP95Ms: 640 },
        failedGates: [],
        activeIndexAtCreation: "idx-v10",
        updatedAt: "8 月 21 日",
      },
      {
        id: "idx-v10",
        status: "SUPERSEDED",
        stage: "SUPERSEDED",
        trigger: "PARSER_CHANGE",
        changedFields: ["parserVersion"],
        reason: "解析器升级",
        config: indexConfig(1024, "typed-parser-v2", "structure-parent-child-v2"),
        progress: 100,
        documents: 412,
        chunks: 3198,
        quality: null,
        failedGates: [],
        activeIndexAtCreation: "idx-v9",
        updatedAt: "7 月 30 日",
      },
    ];
  }
  if (!activeIndex) return [];
  return [{
    id: activeIndex,
    status: "ACTIVE",
    stage: "ACTIVE",
    trigger: "MANUAL_REBUILD",
    changedFields: [],
    reason: "当前活动索引",
    config: indexConfig(),
    progress: 100,
    documents: 0,
    chunks: 0,
    quality: null,
    failedGates: [],
    activeIndexAtCreation: null,
    updatedAt: "今天",
  }];
}

function defaultKnowledgeBases() {
  return [
    {
      id: "kb-handbook",
      tenantId: DEFAULT_TENANT_ID,
      name: "员工服务手册",
      description: "面向全体成员的制度、假期、福利和办事指南。",
      status: "READY",
      documents: 428,
      storageGb: 2.6,
      activeIndex: "idx-v11",
      indexVersions: defaultIndexVersions("kb-handbook", "idx-v11"),
      updatedAt: "今天 17:42",
      sources: [
        { id: "src-policy-pdf", name: "2026 员工假期制度.pdf", kind: "PDF", revision: "rev-0007", activeRevision: "rev-0007", stage: "READY", status: "READY", indexed: true, coverage: "42 页", detail: "文本提取完成，引用保留页码。" },
        { id: "src-benefit-docx", name: "福利办事指南.docx", kind: "DOCX", revision: "rev-0004", activeRevision: "rev-0004", stage: "READY", status: "READY", indexed: true, coverage: "36 节", detail: "标题、段落和列表结构已保留。" },
        { id: "src-office-xlsx", name: "各地办公室联系表.xlsx", kind: "XLSX", revision: "rev-0003", activeRevision: "rev-0002", stage: "INDEXING", status: "PROCESSING", indexed: false, coverage: "216 行", detail: "正在嵌入 184 / 216 个表格分块。" },
        { id: "src-scan-pdf", name: "报销流程扫描件.pdf", kind: "扫描 PDF", revision: "rev-0002", activeRevision: "rev-0001", stage: "FAILED", status: "FAILED", indexed: false, retryStage: "EXTRACTING", error: { code: "PIXEL_LIMIT", message: "第 18 页像素超出安全限制，未写入索引。" }, coverage: "24 页，已处理 17 页", detail: "第 18 页像素超出安全限制，未写入索引。" },
      ],
    },
    {
      id: "kb-support",
      tenantId: DEFAULT_TENANT_ID,
      name: "客户支持知识",
      description: "合成产品说明、故障排查和服务政策。",
      status: "INDEXING",
      documents: 256,
      storageGb: 0.986,
      activeIndex: "idx-v7",
      indexVersions: defaultIndexVersions("kb-support", "idx-v7"),
      updatedAt: "今天 17:18",
      sources: [],
    },
  ];
}

/** 登录态与聊天记录分开保存，存储满时退出仍可移除登录态而不清除历史。 */
function readQuerySession(storage, epoch = 0) {
  const anonymous = { ...defaultQueryState().session, epoch };
  try {
    const session = JSON.parse(storage.getItem(QUERY_SESSION_KEY));
    return session?.authenticated === true &&
      session.tenantId === QUERY_DEMO_ACCOUNT.tenantId &&
      session.accountId === QUERY_DEMO_ACCOUNT.accountId &&
      Number.isSafeInteger(session.epoch) && session.epoch > 0
      ? session : anonymous;
  } catch {
    return anonymous;
  }
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function defaultFeatures() {
  return Object.fromEntries(TENANT_FEATURES.map((feature) => [feature, true]));
}

function defaultState() {
  return {
    schemaVersion: 1,
    tenantSetup: defaultTenantSetup(),
    accounts: [
      {
        id: "account-qiao",
        name: "乔宁（合成）",
        loginName: "qiao.demo",
        status: "available",
        platformAdministrator: false,
        administratorEligible: true,
        memberEligible: true,
      },
      {
        id: "account-lin",
        name: "林澈（合成）",
        loginName: "lin.demo",
        status: "available",
        platformAdministrator: false,
        administratorEligible: false,
        memberEligible: true,
      },
      {
        id: "account-shen",
        name: "沈言（合成）",
        loginName: "shen.demo",
        status: "available",
        platformAdministrator: false,
        administratorEligible: false,
        memberEligible: true,
      },
      {
        id: "account-platform-admin",
        name: "平台管理员演示账户",
        loginName: "platform.admin.demo",
        status: "available",
        platformAdministrator: true,
        administratorEligible: false,
        memberEligible: false,
      },
      {
        id: "account-suspended",
        name: "顾遥（合成）",
        loginName: "gu.demo",
        status: "suspended",
        platformAdministrator: false,
        administratorEligible: false,
        memberEligible: false,
      },
    ],
    tenants: [
      {
        id: DEFAULT_TENANT_ID,
        organizationName: "云杉服务示例组织",
        code: "spruce-demo",
        region: "华东受控区",
        status: "active",
        administratorAccountId: "account-qiao",
        quotas: clone(DEFAULT_QUOTAS),
        safetyBoundaryMutable: false,
        availableFeatures: defaultFeatures(),
        members: [
          {
            accountId: "account-qiao",
            role: "tenantAdministrator",
            status: "active",
            conversationFeatures: {},
            assistantIds: [],
          },
          {
            accountId: "account-shen",
            role: "ordinaryMember",
            status: "active",
            conversationFeatures: {
              assistantSelection: true,
              conversation: true,
              ownHistory: true,
              authorizedCitations: true,
            },
            assistantIds: ["assistant-employee-service"],
          },
        ],
      },
    ],
    knowledgeBases: defaultKnowledgeBases(),
    selectedKnowledgeBaseId: "kb-handbook",
    selectedKnowledgeSourceId: "src-policy-pdf",
    selectedIndexVersionId: "idx-v12",
    selectedTenantId: DEFAULT_TENANT_ID,
    selectedDemoAccountId: "account-shen",
    health: {
      selectedIncidentId: "incident-retrieval-latency",
      incidents: [
        {
          id: "incident-retrieval-latency",
          title: "检索链路延迟",
          chain: "提问 → 权限过滤 → 检索 → 回答",
          status: "degraded",
          impact: "2 个租户的部分问答变慢；仅显示聚合数量。",
          startedAt: "今天 16:42",
          recheckCount: 0,
          recoverAfter: 2,
          records: [
            {
              sequence: 1,
              time: "16:42",
              text: "延迟超过受控阈值，自动保留已有会话与引用结果。",
            },
          ],
        },
        {
          id: "incident-file-processing",
          title: "文件处理队列积压",
          chain: "上传 → 安全扫描 → 提取 → 分块",
          status: "degraded",
          impact: "3 个新文件等待处理；活动索引不受影响。",
          startedAt: "今天 17:06",
          recheckCount: 0,
          recoverAfter: 1,
          records: [
            {
              sequence: 1,
              time: "17:06",
              text: "暂停新增处理批次，已完成文件继续可用。",
            },
          ],
        },
      ],
    },
    recovery: {
      stage: "idle",
      selectedBackupId: null,
      target: "固定隔离演练环境",
      progress: 0,
      report: null,
      failure: null,
      backups: [
        {
          id: "backup-verified",
          label: "今天 02:00 · 华东受控区",
          recoveryPoint: "今天 01:58",
          size: "18.4 GB",
          willPass: true,
        },
        {
          id: "backup-check-fails",
          label: "昨天 02:00 · 华东受控区",
          recoveryPoint: "昨天 01:57",
          size: "18.1 GB",
          willPass: false,
        },
      ],
    },
    query: defaultQueryState(),
  };
}

export function createMemoryStorage(seed = {}) {
  const values = new Map(Object.entries(seed));
  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null;
    },
    setItem(key, value) {
      values.set(key, String(value));
    },
    removeItem(key) {
      values.delete(key);
    },
    dump() {
      return [...values.values()].join("\n");
    },
  };
}

function browserStorage() {
  try {
    return globalThis.localStorage || createMemoryStorage();
  } catch {
    return createMemoryStorage();
  }
}

function initialState(storage) {
  try {
    const parsed = JSON.parse(storage.getItem(PROTOTYPE_STATE_KEY));
    if (parsed?.schemaVersion !== 1) {
      return defaultState();
    }
    parsed.tenantSetup = tenantSetupFrom(parsed.tenantSetup);
    const fallbackKnowledgeBases = defaultKnowledgeBases();
    parsed.knowledgeBases = (Array.isArray(parsed.knowledgeBases)
      ? parsed.knowledgeBases
      : fallbackKnowledgeBases).map(knowledgeBase => ({
        ...knowledgeBase,
        indexVersions: Array.isArray(knowledgeBase.indexVersions)
          ? knowledgeBase.indexVersions
          : clone(fallbackKnowledgeBases.find(item => item.id === knowledgeBase.id)?.indexVersions || defaultIndexVersions(knowledgeBase.id, knowledgeBase.activeIndex)),
      }));
    parsed.selectedKnowledgeBaseId = parsed.knowledgeBases.some(
      knowledgeBase => knowledgeBase.id === parsed.selectedKnowledgeBaseId,
    ) ? parsed.selectedKnowledgeBaseId : parsed.knowledgeBases[0]?.id ?? null;
    const selectedKnowledgeBase = parsed.knowledgeBases.find(
      knowledgeBase => knowledgeBase.id === parsed.selectedKnowledgeBaseId,
    );
    parsed.selectedKnowledgeSourceId = selectedKnowledgeBase?.sources.some(
      source => source.id === parsed.selectedKnowledgeSourceId,
    ) ? parsed.selectedKnowledgeSourceId : selectedKnowledgeBase?.sources[0]?.id ?? null;
    parsed.selectedIndexVersionId = selectedKnowledgeBase?.indexVersions.some(
      indexVersion => indexVersion.id === parsed.selectedIndexVersionId,
    ) ? parsed.selectedIndexVersionId : selectedKnowledgeBase?.indexVersions.find(
      indexVersion => indexVersion.status === "READY_FOR_REVIEW",
    )?.id ?? selectedKnowledgeBase?.activeIndex ?? selectedKnowledgeBase?.indexVersions[0]?.id ?? null;
    parsed.query = {
      ...defaultQueryState(),
      ...(parsed.query || {}),
      session: readQuerySession(storage, Number(parsed.query?.session?.epoch) || 0),
      conversations: Array.isArray(parsed.query?.conversations)
        ? parsed.query.conversations
        : [],
      loadedEarlierConversationIds: Array.isArray(
        parsed.query?.loadedEarlierConversationIds,
      )
        ? parsed.query.loadedEarlierConversationIds
        : [],
    };
    return parsed;
  } catch {
    return defaultState();
  }
}

/** 仅保存可重置的合成状态；不能作为生产鉴权或持久化实现，输入密码不进入状态。 */
export function createPrototypeStore(storage = browserStorage()) {
  let state = initialState(storage);
  const tenantDrafts = new Map();
  const memberDrafts = new Map();
  let querySessionClosed = false;

  function persist() {
    const snapshot = clone(state);
    snapshot.query.session = { ...defaultQueryState().session, epoch: state.query.session.epoch };
    storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  }

  /** 原型写入成功才替换可观察状态；存储失败不能留下幽灵对象或虚假成功。 */
  function commitState(next) {
    const previous = state;
    state = next;
    try {
      persist();
      return { ok: true };
    } catch {
      state = previous;
      return { ok: false, reason: "STORAGE_UNAVAILABLE", errors: [{ field: "form", message: "浏览器无法保存，请检查存储空间后重试。" }] };
    }
  }

  function updateTenantSetup(patch = {}) {
    const current = state.tenantSetup;
    const tenantSetup = tenantSetupFrom({ ...current, ...patch, quotas: { ...current.quotas, ...patch.quotas } });
    return commitState({ ...state, tenantSetup });
  }

  function discardTenantSetup() {
    return commitState({ ...state, tenantSetup: defaultTenantSetup() });
  }

  /** 平台只看合成用量聚合，不投影知识库名称、来源、成员或正文。 */
  function getTenantQuotas(tenantId) {
    const tenant = tenantById(tenantId);
    if (!tenant) return null;
    const knowledgeBases = state.knowledgeBases.filter(item => item.tenantId === tenantId);
    const usage = {
      knowledgeBases: knowledgeBases.length,
      documentsPerKnowledgeBase: Math.max(0, ...knowledgeBases.map(item => item.documents || 0)),
      rawContentGbPerKnowledgeBase: Math.max(0, ...knowledgeBases.map(item => item.storageGb || 0)),
    };
    return { quotas: clone(tenant.quotas), usage };
  }

  /** actorRole 只模拟原型职责边界，不是可供生产使用的客户端授权方式。 */
  function saveTenantQuotas(tenantId, input, actorRole) {
    if (actorRole !== "platformAdministrator") return { ok: false, reason: "FORBIDDEN" };
    const current = getTenantQuotas(tenantId);
    if (!current) return { ok: false, reason: "TENANT_NOT_FOUND" };
    const { quotas, errors } = validateQuotas(input, current.usage);
    if (errors.length) return { ok: false, errors };
    return commitState({ ...state, tenants: state.tenants.map(tenant => tenant.id === tenantId ? { ...tenant, quotas } : tenant) });
  }

  /** 返回配置副本，不允许把安全底线写入可编辑字段。 */
  function getTenantSecurity(tenantId) {
    const tenant = tenantById(tenantId);
    return tenant ? clone(tenant.securityPolicy ?? DEFAULT_SECURITY) : null;
  }

  /** 仅平台角色模拟保存白名单配置；真实认证策略由后续工作包实施。 */
  function saveTenantSecurity(tenantId, input, actorRole) {
    if (actorRole !== "platformAdministrator") return { ok: false, reason: "FORBIDDEN" };
    if (!tenantById(tenantId)) return { ok: false, reason: "TENANT_NOT_FOUND" };
    const errors = [];
    const securityPolicy = {};
    for (const { key, label } of TENANT_SECURITY_FIELDS) {
      const raw = input?.[key];
      const value = ["string", "number"].includes(typeof raw) ? Number(raw) : NaN;
      if (!Number.isSafeInteger(value) || value <= 0) errors.push({ field: key, message: `${label}请输入有效的正整数。` });
      securityPolicy[key] = value;
    }
    if (errors.length) return { ok: false, errors };
    return commitState({ ...state, tenants: state.tenants.map(tenant => tenant.id === tenantId ? { ...tenant, securityPolicy } : tenant) });
  }

  function tenantById(tenantId) {
    return state.tenants.find((tenant) => tenant.id === tenantId);
  }

  function accountById(accountId) {
    return state.accounts.find((account) => account.id === accountId);
  }

  function canManageAccounts(actorAccountId) {
    const actor = accountById(actorAccountId);
    return Boolean(actor?.platformAdministrator && actor.status === "available");
  }

  function knowledgeBaseById(knowledgeBaseId) {
    return state.knowledgeBases.find(knowledgeBase => knowledgeBase.id === knowledgeBaseId);
  }

  /** 原型授权在状态变更前检查租户、成员状态、角色和租户功能上限。 */
  function canManageKnowledge(tenantId, actorAccountId) {
    const tenant = tenantById(tenantId);
    const actor = accountById(actorAccountId);
    const member = tenant?.members.find(item => item.accountId === actorAccountId);
    return Boolean(
      tenant?.status === "active" &&
      actor?.status === "available" &&
      tenant.availableFeatures?.knowledgeManagement !== false &&
      member?.role === "tenantAdministrator" &&
      member.status === "active",
    );
  }

  function replaceKnowledgeBase(knowledgeBase) {
    return {
      ...state,
      knowledgeBases: state.knowledgeBases.map(item => item.id === knowledgeBase.id ? knowledgeBase : item),
    };
  }

  function createKnowledgeBase(input = {}, actorAccountId, tenantId = DEFAULT_TENANT_ID) {
    if (!canManageKnowledge(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const tenant = tenantById(tenantId);
    const name = typeof input.name === "string" ? input.name.trim() : "";
    const description = typeof input.description === "string" ? input.description.trim() : "";
    const errors = [];
    if (name.length < 2 || name.length > 80) errors.push({ field: "name", message: "知识库名称请输入 2 至 80 个字符。" });
    if (description.length > 200) errors.push({ field: "description", message: "用途说明最多 200 个字符。" });
    if (errors.length) return { ok: false, errors };
    if (state.knowledgeBases.some(item => item.tenantId === tenantId && item.name === name)) {
      return { ok: false, reason: "KNOWLEDGE_BASE_NAME_CONFLICT" };
    }
    const tenantKnowledgeBases = state.knowledgeBases.filter(item => item.tenantId === tenantId);
    if (tenantKnowledgeBases.length >= tenant.quotas.knowledgeBases) {
      return { ok: false, reason: "KNOWLEDGE_BASE_QUOTA_EXCEEDED" };
    }
    const knowledgeBase = {
      id: `kb-custom-${tenantKnowledgeBases.length + 1}`,
      tenantId,
      name,
      description,
      status: "EMPTY",
      documents: 0,
      storageGb: 0,
      activeIndex: null,
      indexVersions: [],
      updatedAt: "刚刚",
      sources: [],
    };
    const saved = commitState({ ...state, knowledgeBases: [...state.knowledgeBases, knowledgeBase], selectedKnowledgeBaseId: knowledgeBase.id, selectedKnowledgeSourceId: null });
    return saved.ok ? { ...saved, knowledgeBase: clone(knowledgeBase) } : saved;
  }

  function selectKnowledgeBase(knowledgeBaseId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    return commitState({
      ...state,
      selectedKnowledgeBaseId: knowledgeBaseId,
      selectedKnowledgeSourceId: knowledgeBase.sources[0]?.id ?? null,
      selectedIndexVersionId: knowledgeBase.indexVersions.find(item => item.status === "READY_FOR_REVIEW")?.id ?? knowledgeBase.activeIndex ?? knowledgeBase.indexVersions[0]?.id ?? null,
    });
  }

  function selectKnowledgeSource(sourceId) {
    const knowledgeBase = knowledgeBaseById(state.selectedKnowledgeBaseId);
    if (!knowledgeBase?.sources.some(source => source.id === sourceId)) {
      return { ok: false, reason: "SOURCE_NOT_FOUND" };
    }
    return commitState({ ...state, selectedKnowledgeSourceId: sourceId });
  }

  function sourceInputFailure(knowledgeBase, input = {}) {
    const tenant = tenantById(knowledgeBase.tenantId);
    if (input.remoteUrl || input.mode === "url") return "REMOTE_URL_FORBIDDEN";
    if (input.mode === "paste") {
      const text = typeof input.text === "string" ? input.text.trim() : "";
      if (!text || !String(input.name || "").trim()) return "SOURCE_CONTENT_REQUIRED";
      if (new TextEncoder().encode(text).byteLength > tenant.quotas.pastedTextMib * 1024 * 1024) return "PASTED_TEXT_TOO_LARGE";
      return null;
    }
    if (input.mode !== "file") return "SOURCE_MODE_REQUIRED";
    const name = typeof input.name === "string" ? input.name.trim() : "";
    const extension = name.includes(".") ? name.split(".").at(-1).toLowerCase() : "";
    const type = FILE_TYPES[extension];
    if (!type) return "UNSUPPORTED_FILE_TYPE";
    if (!Number.isFinite(input.sizeMb) || input.sizeMb <= 0) return "FILE_SIZE_INVALID";
    if (input.sizeMb > tenant.quotas.singleFileMb) return "FILE_TOO_LARGE";
    if (input.encrypted) return "ENCRYPTED_FILE";
    if (input.containsMacros) return "OFFICE_MACRO_DETECTED";
    if (input.damagedArchive) return "DAMAGED_ARCHIVE";
    if (!type.mimeTypes.includes(input.mimeType)) return "MIME_MISMATCH";
    return null;
  }

  function addKnowledgeSource(knowledgeBaseId, input = {}, actorAccountId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    if (!canManageKnowledge(knowledgeBase.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const tenant = tenantById(knowledgeBase.tenantId);
    if (knowledgeBase.documents + knowledgeBase.sources.filter(source => source.reservedDocument).length >= tenant.quotas.documentsPerKnowledgeBase) {
      return { ok: false, reason: "DOCUMENT_QUOTA_EXCEEDED" };
    }
    const failure = sourceInputFailure(knowledgeBase, input);
    if (failure) return { ok: false, reason: failure };
    const name = String(input.name).trim();
    const extension = input.mode === "file" ? name.split(".").at(-1).toLowerCase() : "";
    const source = {
      id: `src-${knowledgeBaseId}-${knowledgeBase.sources.length + 1}`,
      name,
      kind: input.mode === "paste" ? "粘贴文本" : FILE_TYPES[extension].kind,
      revision: "rev-0001",
      activeRevision: null,
      stage: "RECEIVED",
      status: "PROCESSING",
      indexed: false,
      reservedDocument: true,
      rawContentStored: false,
      sizeMb: input.mode === "file" ? input.sizeMb : Number((new TextEncoder().encode(input.text.trim()).byteLength / 1024 / 1024).toFixed(3)),
      simulatedFailure: input.simulatedFailure || null,
      error: null,
      detail: "已接收，等待安全扫描。",
    };
    const nextKnowledgeBase = {
      ...knowledgeBase,
      status: "PROCESSING",
      updatedAt: "刚刚",
      storageGb: Number((knowledgeBase.storageGb + source.sizeMb / 1024).toFixed(3)),
      sources: [...knowledgeBase.sources, source],
    };
    const saved = commitState({ ...replaceKnowledgeBase(nextKnowledgeBase), selectedKnowledgeSourceId: source.id });
    return saved.ok ? { ...saved, source: clone(source) } : saved;
  }

  function updateKnowledgeSource(knowledgeBase, source) {
    const previous = knowledgeBase.sources.find(item => item.id === source.id);
    const reservationCompleted = source.stage === "READY" && previous?.reservedDocument;
    const updatedSource = reservationCompleted
      ? { ...source, reservedDocument: false }
      : source;
    const nextKnowledgeBase = {
      ...knowledgeBase,
      updatedAt: "刚刚",
      documents: knowledgeBase.documents + (reservationCompleted ? 1 : 0),
      status: updatedSource.stage === "READY" && knowledgeBase.sources.every(item => item.id === updatedSource.id || ["READY", "FAILED"].includes(item.stage)) ? "READY" : "PROCESSING",
      sources: knowledgeBase.sources.map(item => item.id === updatedSource.id ? updatedSource : item),
    };
    const saved = commitState(replaceKnowledgeBase(nextKnowledgeBase));
    return saved.ok ? { ...saved, source: clone(updatedSource) } : saved;
  }

  function advanceKnowledgeSource(knowledgeBaseId, sourceId, actorAccountId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    if (!canManageKnowledge(knowledgeBase.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = knowledgeBase.sources.find(source => source.id === sourceId);
    if (!current) return { ok: false, reason: "SOURCE_NOT_FOUND" };
    if (["FAILED", "READY"].includes(current.stage)) return { ok: false, reason: "SOURCE_NOT_ADVANCEABLE" };
    if (current.stage === "EXTRACTING" && current.simulatedFailure) {
      return updateKnowledgeSource(knowledgeBase, {
        ...current,
        stage: "FAILED",
        status: "FAILED",
        indexed: false,
        retryStage: "EXTRACTING",
        error: { code: current.simulatedFailure, message: "处理超时，未写入活动索引。" },
        detail: "处理超时，未写入活动索引。",
      });
    }
    const nextStage = SOURCE_STAGES[SOURCE_STAGES.indexOf(current.stage) + 1];
    const ready = nextStage === "READY";
    return updateKnowledgeSource(knowledgeBase, {
      ...current,
      stage: nextStage,
      status: ready ? "READY" : "PROCESSING",
      indexed: ready,
      activeRevision: ready ? current.revision : current.activeRevision,
      rawContentStored: ["EXTRACTING", "EXTRACTED", "CHUNKING", "INDEXING", "READY"].includes(nextStage),
      detail: ready ? "处理完成，已进入待激活索引清单。" : `正在执行 ${nextStage}。`,
    });
  }

  function retryKnowledgeSource(knowledgeBaseId, sourceId, actorAccountId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    if (!canManageKnowledge(knowledgeBase.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = knowledgeBase.sources.find(source => source.id === sourceId);
    if (current?.stage !== "FAILED") return { ok: false, reason: "SOURCE_NOT_FAILED" };
    return updateKnowledgeSource(knowledgeBase, { ...current, stage: current.retryStage || "SCANNING", status: "PROCESSING", error: null, simulatedFailure: null, detail: "已从安全重试点继续处理。" });
  }

  function reprocessKnowledgeSource(knowledgeBaseId, sourceId, actorAccountId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    if (!canManageKnowledge(knowledgeBase.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = knowledgeBase.sources.find(source => source.id === sourceId);
    if (!current) return { ok: false, reason: "SOURCE_NOT_FOUND" };
    if (current.stage !== "READY") return { ok: false, reason: "SOURCE_NOT_READY" };
    const revisionNumber = Number(current.revision.replace("rev-", "")) + 1;
    const source = { ...current, revision: `rev-${String(revisionNumber).padStart(4, "0")}`, stage: "EXTRACTING", status: "PROCESSING", indexed: false, error: null, detail: "新修订正在重新提取；活动索引继续使用上一修订。" };
    return updateKnowledgeSource(knowledgeBase, source);
  }

  function selectIndexVersion(indexVersionId) {
    const knowledgeBase = knowledgeBaseById(state.selectedKnowledgeBaseId);
    if (!knowledgeBase?.indexVersions.some(indexVersion => indexVersion.id === indexVersionId)) {
      return { ok: false, reason: "INDEX_VERSION_NOT_FOUND" };
    }
    return commitState({ ...state, selectedIndexVersionId: indexVersionId });
  }

  function updateIndexVersion(knowledgeBase, indexVersion, knowledgeBaseStatus = knowledgeBase.status) {
    const nextKnowledgeBase = {
      ...knowledgeBase,
      status: knowledgeBaseStatus,
      updatedAt: "刚刚",
      indexVersions: knowledgeBase.indexVersions.map(item => item.id === indexVersion.id ? indexVersion : item),
    };
    const saved = commitState(replaceKnowledgeBase(nextKnowledgeBase));
    return saved.ok ? { ...saved, indexVersion: clone(indexVersion) } : saved;
  }

  /** 重建只创建不可变候选版本，活动索引指针必须由独立激活动作修改。 */
  function createIndexRebuild(knowledgeBaseId, input = {}, actorAccountId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    if (!canManageKnowledge(knowledgeBase.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (knowledgeBase.documents <= 0) return { ok: false, reason: "NO_ACTIVE_DOCUMENTS" };
    if (knowledgeBase.indexVersions.some(item => ["BUILDING", "EVALUATING"].includes(item.status))) {
      return { ok: false, reason: "INDEX_BUILD_ALREADY_ACTIVE" };
    }
    const trigger = String(input.trigger || "MANUAL_REBUILD");
    const changedFields = Array.isArray(input.changedFields) ? [...new Set(input.changedFields.map(String))] : [];
    if (trigger === "CONFIGURATION_CHANGE" && changedFields.length === 0) {
      return { ok: false, reason: "CONFIGURATION_CHANGE_REQUIRED" };
    }
    const number = Math.max(0, ...knowledgeBase.indexVersions.map(item => Number(item.id.replace(/^idx-v/, "")) || 0)) + 1;
    const config = clone(input.config || knowledgeBase.indexVersions[0]?.config || indexConfig());
    const indexVersion = {
      id: `idx-v${number}`,
      status: "BUILDING",
      stage: "MANIFEST_FREEZING",
      trigger,
      changedFields,
      reason: String(input.reason || "管理员发起全库重建").trim(),
      config,
      progress: 10,
      documents: knowledgeBase.documents,
      chunks: null,
      evaluationSet: "eval-set-v6",
      evaluationQuestionCount: 100,
      quality: null,
      failedGates: [],
      activeIndexAtCreation: knowledgeBase.activeIndex,
      updatedAt: "刚刚",
    };
    const nextKnowledgeBase = {
      ...knowledgeBase,
      status: "INDEXING",
      updatedAt: "刚刚",
      indexVersions: [indexVersion, ...knowledgeBase.indexVersions],
    };
    const saved = commitState({ ...replaceKnowledgeBase(nextKnowledgeBase), selectedIndexVersionId: indexVersion.id });
    return saved.ok ? { ...saved, indexVersion: clone(indexVersion) } : saved;
  }

  function advanceIndexBuild(knowledgeBaseId, indexVersionId, actorAccountId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    if (!canManageKnowledge(knowledgeBase.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = knowledgeBase.indexVersions.find(item => item.id === indexVersionId);
    if (!current) return { ok: false, reason: "INDEX_VERSION_NOT_FOUND" };
    if (current.status === "EVALUATING") return { ok: false, reason: "INDEX_EVALUATION_REQUIRED" };
    if (current.status !== "BUILDING") return { ok: false, reason: "INDEX_NOT_BUILDING" };
    const currentPosition = INDEX_BUILD_STAGES.findIndex(item => item.stage === current.stage);
    const next = INDEX_BUILD_STAGES[currentPosition + 1];
    if (!next) return { ok: false, reason: "INDEX_EVALUATION_REQUIRED" };
    return updateIndexVersion(knowledgeBase, {
      ...current,
      ...next,
      status: next.stage === "EVALUATING" ? "EVALUATING" : "BUILDING",
      chunks: next.stage === "EVALUATING" ? Math.max(current.documents * 8, 1) : current.chunks,
      updatedAt: "刚刚",
    });
  }

  function recordIndexEvaluation(knowledgeBaseId, indexVersionId, quality = {}, actorAccountId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    if (!canManageKnowledge(knowledgeBase.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = knowledgeBase.indexVersions.find(item => item.id === indexVersionId);
    if (!current) return { ok: false, reason: "INDEX_VERSION_NOT_FOUND" };
    if (current.status !== "EVALUATING") return { ok: false, reason: "INDEX_NOT_EVALUATING" };
    if (Object.keys(INDEX_QUALITY_GATES).some(key => !Number.isFinite(quality[key]))) {
      return { ok: false, reason: "INVALID_QUALITY_RESULT" };
    }
    const failedGates = Object.entries(INDEX_QUALITY_GATES)
      .filter(([key, gate]) => !gate.accepts(quality[key]))
      .map(([, gate]) => gate.label);
    const status = failedGates.length ? "BLOCKED" : "READY_FOR_REVIEW";
    return updateIndexVersion(knowledgeBase, {
      ...current,
      status,
      stage: status,
      progress: 100,
      quality: clone(quality),
      failedGates,
      updatedAt: "刚刚",
    }, knowledgeBase.activeIndex ? "READY" : "EMPTY");
  }

  /** 激活在一次持久化提交中更新新旧版本和知识库活动指针，失败时整体回滚。 */
  function activateIndexVersion(knowledgeBaseId, indexVersionId, actorAccountId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    if (!canManageKnowledge(knowledgeBase.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = knowledgeBase.indexVersions.find(item => item.id === indexVersionId);
    if (!current) return { ok: false, reason: "INDEX_VERSION_NOT_FOUND" };
    if (current.status === "BLOCKED") return { ok: false, reason: "QUALITY_GATE_FAILED" };
    if (current.status !== "READY_FOR_REVIEW") return { ok: false, reason: "INDEX_NOT_READY" };
    const nextKnowledgeBase = {
      ...knowledgeBase,
      activeIndex: current.id,
      status: "READY",
      updatedAt: "刚刚",
      indexVersions: knowledgeBase.indexVersions.map(item => {
        if (item.id === current.id) return { ...item, status: "ACTIVE", stage: "ACTIVE", updatedAt: "刚刚" };
        if (["ACTIVE", "READY_FOR_REVIEW"].includes(item.status)) return { ...item, status: "SUPERSEDED", stage: "SUPERSEDED" };
        return item;
      }),
    };
    const saved = commitState(replaceKnowledgeBase(nextKnowledgeBase));
    const activated = nextKnowledgeBase.indexVersions.find(item => item.id === current.id);
    return saved.ok ? { ...saved, indexVersion: clone(activated) } : saved;
  }

  function cancelIndexBuild(knowledgeBaseId, indexVersionId, actorAccountId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    if (!canManageKnowledge(knowledgeBase.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = knowledgeBase.indexVersions.find(item => item.id === indexVersionId);
    if (!current) return { ok: false, reason: "INDEX_VERSION_NOT_FOUND" };
    if (!["BUILDING", "EVALUATING"].includes(current.status)) return { ok: false, reason: "INDEX_NOT_CANCELLABLE" };
    return updateIndexVersion(
      knowledgeBase,
      { ...current, status: "CANCELLED", stage: "CANCELLED", updatedAt: "刚刚" },
      knowledgeBase.activeIndex ? "READY" : "EMPTY",
    );
  }

  function accountPasswordErrors(input = {}) {
    const password = typeof input.password === "string" ? input.password : "";
    const confirmation =
      typeof input.passwordConfirmation === "string"
        ? input.passwordConfirmation
        : "";
    const errors = [];
    if (password.length < 1 || password.length > 1024) {
      errors.push({
        field: "password",
        message: "请输入有效的新密码。",
      });
    }
    if (confirmation !== password) {
      errors.push({
        field: "passwordConfirmation",
        message: "两次输入的密码不一致。",
      });
    }
    return errors;
  }

  /** 只保存非秘密账户事实；明文初始密码只参与本次合成校验。 */
  function createAccount(input = {}, actorAccountId) {
    if (!canManageAccounts(actorAccountId)) {
      return { ok: false, reason: "FORBIDDEN" };
    }
    const loginName = String(input.loginName || "").trim();
    const errors = accountPasswordErrors(input);
    if (!/^[a-z][a-z0-9._-]{2,63}$/.test(loginName)) {
      errors.unshift({
        field: "loginName",
        message: "登录名需为 3–64 位小写字母、数字、点、下划线或连字符。",
      });
    }
    if (state.accounts.some((account) => account.loginName === loginName)) {
      return {
        ok: false,
        reason: "LOGIN_NAME_CONFLICT",
        errors: [{ field: "loginName", message: "此登录名已存在，请使用其他登录名。" }, ...errors.filter(error => error.field !== "loginName")],
      };
    }
    if (errors.length) {
      return { ok: false, reason: "VALIDATION_FAILED", errors };
    }
    const account = {
      id: `account-${loginName.replaceAll(/[^a-z0-9]+/g, "-")}-${state.accounts.length + 1}`,
      name: loginName,
      loginName,
      status: "available",
      platformAdministrator: false,
      administratorEligible: true,
      memberEligible: true,
      authenticationRevision: 1,
      authenticationChangedAt: "刚刚创建",
    };
    const saved = commitState({ ...state, accounts: [...state.accounts, account] });
    return saved.ok ? { ok: true, account: clone(account) } : saved;
  }

  /** 重置只推进非秘密认证修订；原型状态、日志和返回值均不携带明文。 */
  function resetAccountPassword(accountId, input = {}, actorAccountId) {
    if (!canManageAccounts(actorAccountId)) {
      return { ok: false, reason: "FORBIDDEN" };
    }
    if (accountId === actorAccountId) {
      return { ok: false, reason: "SELF_RESET_FORBIDDEN" };
    }
    const account = accountById(accountId);
    if (!account) {
      return { ok: false, reason: "ACCOUNT_NOT_FOUND" };
    }
    const errors = accountPasswordErrors(input);
    if (input.revokeSessions !== true) {
      errors.push({
        field: "revokeSessions",
        message: "必须确认撤销该账户的全部会话。",
      });
    }
    if (errors.length) {
      return { ok: false, reason: "VALIDATION_FAILED", errors };
    }
    const nextAccount = {
      ...account,
      authenticationRevision: (account.authenticationRevision || 1) + 1,
      authenticationChangedAt: "刚刚重置",
    };
    const saved = commitState({
      ...state,
      accounts: state.accounts.map((item) =>
        item.id === accountId ? nextAccount : item,
      ),
    });
    return saved.ok ? { ok: true, account: clone(nextAccount), sessionsRevoked: true } : saved;
  }

  /** 平台身份与租户成员范围互斥，不能通过账户页给已有租户成员提权。 */
  function assignPlatformAdministrator(accountId, actorAccountId) {
    if (!canManageAccounts(actorAccountId)) {
      return { ok: false, reason: "FORBIDDEN" };
    }
    const account = accountById(accountId);
    if (!account) {
      return { ok: false, reason: "ACCOUNT_NOT_FOUND" };
    }
    if (state.tenants.some((tenant) => tenant.members.some((member) => member.accountId === accountId))) {
      return { ok: false, reason: "ACCOUNT_ASSIGNED_TO_TENANT" };
    }
    if (account.status !== "available") {
      return { ok: false, reason: "ACCOUNT_NOT_ACTIVE" };
    }
    if (account.platformAdministrator) {
      return { ok: false, reason: "ALREADY_PLATFORM_ADMINISTRATOR" };
    }
    const nextAccount = {
      ...account,
      platformAdministrator: true,
      administratorEligible: false,
      memberEligible: false,
    };
    const saved = commitState({
      ...state,
      accounts: state.accounts.map((item) =>
        item.id === accountId ? nextAccount : item,
      ),
    });
    return saved.ok ? { ok: true, account: clone(nextAccount), sessionsRevoked: true } : saved;
  }

  function setAccountStatus(accountId, status, actorAccountId) {
    if (!canManageAccounts(actorAccountId)) {
      return { ok: false, reason: "FORBIDDEN" };
    }
    if (accountId === actorAccountId) {
      return { ok: false, reason: "SELF_MUTATION_FORBIDDEN" };
    }
    const account = accountById(accountId);
    if (!account) {
      return { ok: false, reason: "ACCOUNT_NOT_FOUND" };
    }
    if (!["available", "suspended"].includes(status)) {
      return { ok: false, reason: "INVALID_STATUS" };
    }
    const activeAdministrators = state.accounts.filter(
      (item) => item.platformAdministrator && item.status === "available",
    ).length;
    if (
      status === "suspended" &&
      account.platformAdministrator &&
      activeAdministrators <= 1
    ) {
      return { ok: false, reason: "LAST_PLATFORM_ADMINISTRATOR" };
    }
    const nextAccount = { ...account, status };
    const saved = commitState({
      ...state,
      accounts: state.accounts.map((item) =>
        item.id === accountId ? nextAccount : item,
      ),
    });
    return saved.ok ? { ok: true, account: clone(nextAccount), sessionsRevoked: status === "suspended" } : saved;
  }

  function memberKey(tenantId, accountId) {
    return `${tenantId}:${accountId}`;
  }

  function memberById(tenantId, accountId) {
    return tenantById(tenantId)?.members.find(
      (member) => member.accountId === accountId,
    );
  }

  function reset() {
    storage.removeItem(QUERY_SESSION_KEY);
    querySessionClosed = true;
    state = defaultState();
    tenantDrafts.clear();
    memberDrafts.clear();
    persist();
    return clone(state);
  }

  function createTenant(input = {}) {
    const organizationName = String(input.organizationName || "").trim();
    const tenantCode = String(input.tenantCode || "").trim().toLowerCase();
    const administratorAccountId = String(
      input.administratorAccountId || "",
    );
    const administrator = accountById(administratorAccountId);
    const errors = [];

    if (organizationName.length < 2 || organizationName.length > 40) {
      errors.push({ field: "organizationName", message: "请输入 2–40 个字符的组织名称。" });
    }
    if (!/^[a-z][a-z0-9-]{2,19}$/.test(tenantCode) || state.tenants.some((tenant) => tenant.code === tenantCode)) {
      errors.push({ field: "tenantCode", message: "租户编码需为 3–20 位小写字母、数字或连字符，且不能重复。" });
    }
    if (
      !administrator ||
      administrator.platformAdministrator ||
      !administrator.administratorEligible ||
      administrator.status !== "available"
    ) {
      errors.push({ field: "administratorAccountId", message: "请选择一位可用的租户管理员。" });
    }
    if (input.confirmed !== true) {
      errors.push({ field: "confirmed", message: "请确认租户信息、管理员和资源配额。" });
    }
    const validated = validateQuotas(input.quotas ?? DEFAULT_QUOTAS);
    errors.push(...validated.errors);
    if (errors.length > 0) {
      return { ok: false, errors };
    }

    const tenant = {
      id: `tenant-${tenantCode}`,
      organizationName,
      code: tenantCode,
      region: "华东受控区",
      status: "active",
      administratorAccountId,
      quotas: validated.quotas,
      safetyBoundaryMutable: false,
      availableFeatures: defaultFeatures(),
      members: [
        {
          accountId: administratorAccountId,
          role: "tenantAdministrator",
          status: "active",
          conversationFeatures: {},
          assistantIds: [],
        },
      ],
    };
    const saved = commitState({ ...state, tenants: [...state.tenants, tenant], selectedTenantId: tenant.id, tenantSetup: defaultTenantSetup() });
    if (!saved.ok) return saved;
    return { ok: true, tenant: clone(tenant) };
  }

  function addMember(tenantId, input = {}) {
    const tenant = tenantById(tenantId);
    const account = accountById(input.accountId);
    if (!tenant) {
      return { ok: false, reason: "TENANT_NOT_FOUND" };
    }
    if (
      !account ||
      account.platformAdministrator ||
      !account.memberEligible ||
      account.status !== "available"
    ) {
      return { ok: false, reason: "ACCOUNT_NOT_ELIGIBLE" };
    }
    if (tenant.members.some((member) => member.accountId === account.id)) {
      return { ok: false, reason: "MEMBER_ALREADY_EXISTS" };
    }
    const requested = new Set(input.conversationFeatures || []);
    const member = {
      accountId: account.id,
      role: "ordinaryMember",
      status: "active",
      conversationFeatures: Object.fromEntries(
        CONVERSATION_FEATURES.map((feature) => [feature, requested.has(feature)]),
      ),
      assistantIds: [...new Set(input.assistantIds || [])],
    };
    tenant.members.push(member);
    persist();
    return { ok: true, member: clone(member) };
  }

  function findMember(tenantId, accountId) {
    const member = memberById(tenantId, accountId);
    return member ? clone(member) : null;
  }

  function setMemberStatus(tenantId, accountId, status) {
    const member = memberById(tenantId, accountId);
    if (!member) {
      return { ok: false, reason: "MEMBER_NOT_FOUND" };
    }
    if (member.role === "tenantAdministrator") {
      return { ok: false, reason: "ADMINISTRATOR_READ_ONLY" };
    }
    if (!["active", "disabled"].includes(status)) {
      return { ok: false, reason: "INVALID_STATUS" };
    }
    member.status = status;
    persist();
    return { ok: true, member: clone(member) };
  }

  function setTenantFeatureDraft(tenantId, feature, enabled) {
    const tenant = tenantById(tenantId);
    if (!tenant || !TENANT_FEATURES.includes(feature)) {
      return { ok: false, reason: "INVALID_FEATURE" };
    }
    const draft = tenantDrafts.get(tenantId) || clone(tenant.availableFeatures);
    draft[feature] = Boolean(enabled);
    tenantDrafts.set(tenantId, draft);
    return { ok: true, draft: clone(draft) };
  }

  function discardTenantFeatureDraft(tenantId) {
    tenantDrafts.delete(tenantId);
    return { ok: true };
  }

  function commitTenantFeatureDraft(tenantId) {
    const tenant = tenantById(tenantId);
    const draft = tenantDrafts.get(tenantId);
    if (!tenant || !draft) {
      return { ok: false, reason: "NO_DRAFT" };
    }
    tenant.availableFeatures = clone(draft);
    tenantDrafts.delete(tenantId);
    persist();
    return { ok: true, availableFeatures: clone(tenant.availableFeatures) };
  }

  function setMemberFeatureDraft(tenantId, accountId, feature, enabled) {
    const member = memberById(tenantId, accountId);
    if (!member || member.role !== "ordinaryMember" || !CONVERSATION_FEATURES.includes(feature)) {
      return { ok: false, reason: "MEMBER_FEATURE_NOT_EDITABLE" };
    }
    const key = memberKey(tenantId, accountId);
    const draft = memberDrafts.get(key) || {
      conversationFeatures: clone(member.conversationFeatures),
      assistantIds: clone(member.assistantIds),
    };
    draft.conversationFeatures[feature] = Boolean(enabled);
    memberDrafts.set(key, draft);
    return { ok: true, draft: clone(draft) };
  }

  function setMemberAssistantDraft(tenantId, accountId, assistantIds) {
    const member = memberById(tenantId, accountId);
    if (!member || member.role !== "ordinaryMember") {
      return { ok: false, reason: "MEMBER_FEATURE_NOT_EDITABLE" };
    }
    const key = memberKey(tenantId, accountId);
    const draft = memberDrafts.get(key) || {
      conversationFeatures: clone(member.conversationFeatures),
      assistantIds: clone(member.assistantIds),
    };
    draft.assistantIds = [...new Set(assistantIds)];
    memberDrafts.set(key, draft);
    return { ok: true, draft: clone(draft) };
  }

  function discardMemberFeatureDraft(tenantId, accountId) {
    memberDrafts.delete(memberKey(tenantId, accountId));
    return { ok: true };
  }

  function commitMemberFeatureDraft(tenantId, accountId) {
    const member = memberById(tenantId, accountId);
    const key = memberKey(tenantId, accountId);
    const draft = memberDrafts.get(key);
    if (!member || member.role !== "ordinaryMember" || !draft) {
      return { ok: false, reason: "NO_DRAFT" };
    }
    member.conversationFeatures = clone(draft.conversationFeatures);
    member.assistantIds = clone(draft.assistantIds);
    memberDrafts.delete(key);
    persist();
    return { ok: true, member: clone(member) };
  }

  function getEffectiveAccess(tenantId, accountId, request = {}) {
    const tenant = tenantById(tenantId);
    const member = memberById(tenantId, accountId);
    const account = accountById(accountId);
    if (!tenant || tenant.status !== "active" || !member || member.status !== "active" || account?.status !== "available") {
      return { allowed: false, reason: "MEMBERSHIP_NOT_ACTIVE" };
    }
    if (member.role !== "ordinaryMember" || !CONVERSATION_FEATURES.includes(request.feature)) {
      return { allowed: false, reason: "ROLE_BOUNDARY" };
    }
    if (!tenant.availableFeatures[request.feature]) {
      return { allowed: false, reason: "TENANT_FEATURE_CLOSED" };
    }
    if (!member.conversationFeatures[request.feature]) {
      return { allowed: false, reason: "MEMBER_FEATURE_NOT_GRANTED" };
    }
    if (request.assistantId && !member.assistantIds.includes(request.assistantId)) {
      return { allowed: false, reason: "OBJECT_NOT_AUTHORIZED" };
    }
    return { allowed: true, reason: "ALLOWED" };
  }

  function authorizeScreen(screen, tenantId, accountId) {
    const feature = SCREEN_FEATURE[screen];
    if (!feature) {
      return { allowed: true, reason: "NO_FEATURE_GATE" };
    }
    if (CONVERSATION_FEATURES.includes(feature)) {
      return getEffectiveAccess(tenantId, accountId, { feature });
    }
    const tenant = tenantById(tenantId);
    return tenant?.availableFeatures[feature]
      ? { allowed: true, reason: "ALLOWED" }
      : { allowed: false, reason: "TENANT_FEATURE_CLOSED" };
  }

  function persistQueryMutation(mutator) {
    const before = clone(state.query);
    const selectedDemoAccountId = state.selectedDemoAccountId;
    const result = mutator();
    try {
      persist();
      if (state.query.session.epoch !== before.session.epoch && state.query.session.authenticated) {
        storage.setItem(QUERY_SESSION_KEY, JSON.stringify(state.query.session));
      }
      return result;
    } catch {
      state.query = before;
      state.selectedDemoAccountId = selectedDemoAccountId;
      return { ok: false, reason: "STORAGE_UNAVAILABLE" };
    }
  }

  function getQuerySession() {
    return clone(state.query.session);
  }

  function loginQuery(input = {}) {
    const valid =
      String(input.tenantCode || "").trim() === QUERY_DEMO_ACCOUNT.tenantCode &&
      String(input.username || "").trim() === QUERY_DEMO_ACCOUNT.username &&
      String(input.password || "") === QUERY_DEMO_ACCOUNT.password;
    if (!valid) {
      return { ok: false, reason: "INVALID_CREDENTIALS" };
    }
    const result = persistQueryMutation(() => {
      state.selectedDemoAccountId = QUERY_DEMO_ACCOUNT.accountId;
      state.query.session = {
        authenticated: true,
        tenantId: QUERY_DEMO_ACCOUNT.tenantId,
        accountId: QUERY_DEMO_ACCOUNT.accountId,
        epoch: state.query.session.epoch + 1,
      };
      // 旧登录的接收器已失效，保留偏移并等待当前用户显式继续。
      for (const conversation of state.query.conversations) {
        if (conversation.memberId !== QUERY_DEMO_ACCOUNT.accountId) continue;
        for (const run of conversation.runs) {
          if (run.status !== "RECEIVING") continue;
          run.status = "PAUSED";
          const message = conversation.messages.find(item => item.runId === run.id);
          if (message) message.runState = "PAUSED";
        }
      }
      return { ok: true, session: getQuerySession() };
    });
    if (result.ok) querySessionClosed = false;
    return result;
  }

  function logoutQuery() {
    querySessionClosed = true;
    state.query.session = { ...defaultQueryState().session, epoch: state.query.session.epoch + 1 };
    try {
      storage.removeItem(QUERY_SESSION_KEY);
      return { ok: true, session: getQuerySession() };
    } catch {
      return { ok: false, reason: "STORAGE_UNAVAILABLE" };
    }
  }

  function queryAccess(feature, assistantId) {
    const session = state.query.session;
    if (!session.authenticated || !session.tenantId || !session.accountId) {
      return { allowed: false, reason: "AUTH_REQUIRED" };
    }
    return getEffectiveAccess(session.tenantId, session.accountId, {
      feature,
      assistantId,
    });
  }

  function publishedAssistant(fixtures, assistantId) {
    return (fixtures?.assistants || []).find(
      (assistant) =>
        assistant.id === assistantId && assistant.status === "PUBLISHED",
    );
  }

  function storedConversation(conversationId) {
    return state.query.conversations.find(
      (conversation) => conversation.id === conversationId,
    );
  }

  function fixtureConversation(fixtures, conversationId) {
    const source = (fixtures?.conversations || []).find(
      (conversation) => conversation.id === conversationId,
    );
    if (!source) {
      return null;
    }
    const assistant = publishedAssistant(fixtures, source.assistantId);
    return {
      ...clone(source),
      assistantVersionId: assistant?.versionId || "",
      assistantVersion: assistant?.version || "",
      indexVersionId: assistant?.indexVersionId || "",
      messages: clone(
        (fixtures?.messages || []).filter(
          (message) => message.conversationId === source.id,
        ),
      ),
      runs: [],
    };
  }

  function queryConversation(fixtures, conversationId) {
    return storedConversation(conversationId) ||
      fixtureConversation(fixtures, conversationId);
  }

  function materializeConversation(fixtures, conversationId) {
    const stored = storedConversation(conversationId);
    if (stored) {
      return stored;
    }
    const seeded = fixtureConversation(fixtures, conversationId);
    if (!seeded) {
      return null;
    }
    state.query.conversations.push(seeded);
    return seeded;
  }

  function createQueryConversation(fixtures, assistantId) {
    const assistant = publishedAssistant(fixtures, assistantId);
    if (!assistant) {
      return { ok: false, reason: "ASSISTANT_NOT_FOUND" };
    }
    const canSelect = queryAccess("assistantSelection", assistantId);
    const canConverse = queryAccess("conversation", assistantId);
    if (!canSelect.allowed || !canConverse.allowed) {
      return { ok: false, reason: canSelect.allowed ? canConverse.reason : canSelect.reason };
    }
    return persistQueryMutation(() => {
      const number = state.query.nextConversationNumber;
      const conversation = {
        id: `cv-demo-${String(number).padStart(4, "0")}`,
        memberId: state.query.session.accountId,
        assistantId,
        assistant: assistant.name,
        title: "新会话",
        updatedAt: "刚刚",
        assistantVersionId: assistant.versionId,
        assistantVersion: assistant.version,
        indexVersionId: assistant.indexVersionId,
        messages: [],
        runs: [],
      };
      state.query.nextConversationNumber += 1;
      state.query.currentConversationId = conversation.id;
      state.query.conversations.unshift(conversation);
      return { ok: true, conversation: clone(conversation) };
    });
  }

  function queryAnswer(question) {
    if (/年假|未休\s*7\s*天/.test(question)) {
      return {
        terminalStatus: "COMPLETED",
        content:
          "按当前制度，未休年假最多可结转 5 个工作日，并需在次年 3 月 31 日前使用。" +
          (/未休\s*7\s*天/.test(question)
            ? "你有 7 天未休，其中 5 天可结转；其余 2 天请联系直属负责人和人力资源确认安排。"
            : "超过结转上限的部分，请联系直属负责人和人力资源确认安排。"),
        citations: [
          {
            id: "cit-demo-leave-1",
            source: "2026 员工假期制度.pdf",
            location: "第 6 页 · 年假结转",
            excerpt: "未休年假可结转至次年第一季度，结转上限为五个工作日。",
          },
          {
            id: "cit-demo-leave-2",
            source: "福利办事指南.docx",
            location: "假期申请 · 第 3 节",
            excerpt: "结转年假需在次年三月三十一日前完成申请和使用。",
          },
        ],
      };
    }
    return {
      terminalStatus: "REFUSED",
      content:
        "我无法依据当前已授权知识回答这个问题。请改问员工制度、假期或办事流程，或联系相应业务负责人。",
      citations: [],
    };
  }

  function nextMessageSequence(conversation) {
    return Math.max(0, ...conversation.messages.map((message) => message.sequence || 0), ...conversation.runs.map(run => run.assistantSequence)) + 1;
  }

  /** 页面投影不返回内部完整答案、证据或消息容器，正文只能经过对应权限投影。 */
  function conversationSummary(conversation) {
    const { id, memberId, assistantId, assistant, title, updatedAt, assistantVersionId, assistantVersion, indexVersionId } = conversation;
    return { id, memberId, assistantId, assistant, title, updatedAt, assistantVersionId, assistantVersion, indexVersionId };
  }

  function queryRunSummary(run) {
    const { id, status, userSequence, assistantSequence, offset, sessionEpoch } = run;
    return { id, status, userSequence, assistantSequence, offset, sessionEpoch, totalCharacters: run.answer.length };
  }

  function canUseConversation(fixtures, conversationId) {
    if (!state.query.session.authenticated) {
      return { allowed: false, reason: "AUTH_REQUIRED" };
    }
    const conversation = queryConversation(fixtures, conversationId);
    if (!conversation || conversation.memberId !== state.query.session.accountId) {
      return { allowed: false, reason: "CONVERSATION_NOT_FOUND" };
    }
    const history = queryAccess("ownHistory", conversation.assistantId);
    const conversationAccess = queryAccess("conversation", conversation.assistantId);
    if (!history.allowed || !conversationAccess.allowed) {
      return {
        allowed: false,
        reason: history.allowed ? conversationAccess.reason : history.reason,
      };
    }
    return { allowed: true, conversation };
  }

  function startQueryRun(fixtures, conversationId, rawQuestion, options = {}) {
    const question = String(rawQuestion || "").trim();
    if (!question) {
      return { ok: false, reason: "QUESTION_REQUIRED" };
    }
    const access = canUseConversation(fixtures, conversationId);
    if (!access.allowed) {
      return { ok: false, reason: access.reason };
    }
    const active = (access.conversation.runs || []).find(
      (run) =>
        ["RECEIVING", "PAUSED", "FAILED", "STOPPED"].includes(run.status),
    );
    if (active) {
      return active.question === question
        ? { ok: true, replayed: true, run: queryRunSummary(active) }
        : { ok: false, reason: "RUN_IN_PROGRESS" };
    }
    return persistQueryMutation(() => {
      const conversation = materializeConversation(fixtures, conversationId);
      const answer = queryAnswer(question);
      const userSequence = nextMessageSequence(conversation);
      const run = {
        id: `${conversation.id}-run-${conversation.runs.length + 1}`,
        question,
        status:
          options.mode === "failure"
            ? "FAILED"
            : options.mode === "disconnect"
              ? "PAUSED"
              : "RECEIVING",
        userSequence,
        assistantSequence: userSequence + 1,
        answer: answer.content,
        citations: answer.citations,
        terminalStatus: answer.terminalStatus,
        offset: 0,
        sessionEpoch: state.query.session.epoch,
      };
      conversation.messages.push({
        sequence: userSequence,
        conversationId: conversation.id,
        role: "user",
        content: question,
        createdAt: "刚刚",
      });
      conversation.runs.push(run);
      conversation.title = question.length > 18 ? `${question.slice(0, 18)}…` : question;
      conversation.updatedAt = "刚刚";
      state.query.currentConversationId = conversation.id;
      return { ok: true, replayed: false, run: queryRunSummary(run) };
    });
  }

  function advanceQueryRun(fixtures, conversationId, runId, options = {}) {
    const access = canUseConversation(fixtures, conversationId);
    if (!access.allowed) {
      return { ok: false, reason: access.reason };
    }
    if (
      Number.isInteger(options.expectedEpoch) &&
      options.expectedEpoch !== state.query.session.epoch
    ) {
      return { ok: false, reason: "STALE_SESSION" };
    }
    const conversation = storedConversation(conversationId);
    const run = conversation?.runs.find((item) => item.id === runId);
    if (!run || run.status !== "RECEIVING") {
      return { ok: false, reason: "RUN_NOT_RECEIVING" };
    }
    if (run.sessionEpoch !== state.query.session.epoch) {
      return { ok: false, reason: "STALE_SESSION" };
    }
    return persistQueryMutation(() => {
      const chunkSize = Math.max(1, Number(options.chunkSize) || 18);
      run.offset = Math.min(run.answer.length, run.offset + chunkSize);
      if (run.offset >= run.answer.length) {
        run.status = run.terminalStatus;
      }
      let message = conversation.messages.find(
        (item) => item.sequence === run.assistantSequence,
      );
      if (!message) {
        message = {
          sequence: run.assistantSequence,
          conversationId: conversation.id,
          role: "assistant",
          content: "",
          createdAt: "刚刚",
          citations: [],
          runId: run.id,
          runState: "RECEIVING",
        };
        conversation.messages.push(message);
      }
      message.content = run.answer.slice(0, run.offset);
      message.runState = run.status;
      message.citations = ["COMPLETED", "REFUSED"].includes(run.status)
        ? clone(run.citations)
        : [];
      return { ok: true, run: queryRunSummary(run) };
    });
  }

  function changeQueryRunStatus(fixtures, conversationId, runId, from, to) {
    const access = canUseConversation(fixtures, conversationId);
    if (!access.allowed) {
      return { ok: false, reason: access.reason };
    }
    const conversation = storedConversation(conversationId);
    const run = conversation?.runs.find((item) => item.id === runId);
    if (!run || !from.includes(run.status)) {
      return { ok: false, reason: "RUN_STATE_MISMATCH" };
    }
    return persistQueryMutation(() => {
      run.status = to;
      run.sessionEpoch = state.query.session.epoch;
      const message = conversation.messages.find(
        (item) => item.sequence === run.assistantSequence,
      );
      if (message) {
        message.runState = to;
      }
      return { ok: true, run: queryRunSummary(run) };
    });
  }

  function pauseQueryRun(fixtures, conversationId, runId) {
    return changeQueryRunStatus(
      fixtures,
      conversationId,
      runId,
      ["RECEIVING"],
      "PAUSED",
    );
  }

  function resumeQueryRun(fixtures, conversationId, runId) {
    return changeQueryRunStatus(
      fixtures,
      conversationId,
      runId,
      ["PAUSED", "STOPPED"],
      "RECEIVING",
    );
  }

  function retryQueryRun(fixtures, conversationId, runId) {
    return changeQueryRunStatus(
      fixtures,
      conversationId,
      runId,
      ["FAILED"],
      "RECEIVING",
    );
  }

  function stopQueryRun(fixtures, conversationId, runId) {
    return changeQueryRunStatus(
      fixtures,
      conversationId,
      runId,
      ["RECEIVING", "PAUSED"],
      "STOPPED",
    );
  }

  function loadEarlierQueryMessages(fixtures, conversationId) {
    const access = canUseConversation(fixtures, conversationId);
    if (!access.allowed) {
      return { ok: false, reason: access.reason };
    }
    return persistQueryMutation(() => {
      if (!state.query.loadedEarlierConversationIds.includes(conversationId)) {
        state.query.loadedEarlierConversationIds.push(conversationId);
      }
      return { ok: true };
    });
  }

  /** 合成问答数据也按当前登录成员、已发布助手和会话归属过滤，不以菜单隐藏替代数据过滤。 */
  function queryProjection(fixtures, request = {}) {
    const session = state.query.session;
    if (!session.authenticated) {
      return {
        member: null,
        assistants: [],
        history: [],
        messages: [],
        conversation: null,
        activeRun: null,
        canLoadEarlier: false,
        conversationAllowed: false,
        reason: "AUTH_REQUIRED",
      };
    }
    const tenantId = session.tenantId;
    const accountId = session.accountId;
    const member = memberById(tenantId, accountId);
    const published = (fixtures?.assistants || []).filter((assistant) => assistant.status === "PUBLISHED");
    const allowed = (feature, assistantId) => published.some((assistant) => assistant.id === assistantId) &&
      getEffectiveAccess(tenantId, accountId, { feature, assistantId }).allowed;
    const assistants = published.filter((assistant) => allowed("assistantSelection", assistant.id));
    const combined = [
      ...state.query.conversations,
      ...(fixtures?.conversations || []).filter(
        (fixtureItem) =>
          !state.query.conversations.some((stored) => stored.id === fixtureItem.id),
      ),
    ];
    const history = combined.filter((conversation) =>
      conversation.memberId === accountId && allowed("ownHistory", conversation.assistantId));
    const conversation = history.find((item) => item.id === request.conversationId) || null;
    const assistantId = conversation?.assistantId || request.assistantId || assistants[0]?.id;
    const conversationAllowed = Boolean(
      conversation &&
      allowed("conversation", assistantId) &&
      allowed("ownHistory", assistantId),
    );
    let messages = conversation && conversationAllowed
      ? clone(
          storedConversation(conversation.id)?.messages ||
            (fixtures?.messages || []).filter(
              (message) => message.conversationId === conversation.id,
            ),
        )
      : [];
    if (
      conversation &&
      !state.query.loadedEarlierConversationIds.includes(conversation.id)
    ) {
      messages = messages.filter((message) => !message.earlier);
    }
    if (!allowed("authorizedCitations", assistantId)) {
      messages.forEach((message) => delete message.citations);
    }
    const stored = conversation ? storedConversation(conversation.id) : null;
    const activeRun = stored && conversationAllowed
      ? [...stored.runs].reverse().find((run) =>
          ["RECEIVING", "PAUSED", "FAILED", "STOPPED"].includes(run.status),
        ) || null
      : null;
    const canLoadEarlier = Boolean(
      conversation &&
        !state.query.loadedEarlierConversationIds.includes(conversation.id) &&
        (fixtures?.messages || []).some(
          (message) =>
            message.conversationId === conversation.id && message.earlier,
        ),
    );
    return {
      member: member ? clone(member) : null,
      assistants: clone(assistants),
      history: history.map(conversationSummary),
      messages,
      conversation: conversation ? conversationSummary(conversation) : null,
      activeRun: activeRun ? queryRunSummary(activeRun) : null,
      canLoadEarlier,
      conversationAllowed,
    };
  }

  function selectDemoAccount(accountId) {
    const member = memberById(DEFAULT_TENANT_ID, accountId);
    if (!member || member.role !== "ordinaryMember") {
      return { ok: false, reason: "DEMO_MEMBER_REQUIRED" };
    }
    if (state.query.session.authenticated && state.query.session.accountId !== accountId) logoutQuery();
    return persistQueryMutation(() => {
      state.selectedDemoAccountId = accountId;
      return { ok: true };
    });
  }

  function selectTenant(tenantId) {
    if (!tenantById(tenantId)) {
      return { ok: false, reason: "TENANT_NOT_FOUND" };
    }
    state.selectedTenantId = tenantId;
    persist();
    return { ok: true };
  }

  function selectHealthIncident(incidentId) {
    const incident = state.health.incidents.find((item) => item.id === incidentId);
    if (!incident) {
      return { ok: false, reason: "INCIDENT_NOT_FOUND" };
    }
    state.health.selectedIncidentId = incidentId;
    persist();
    return { ok: true, incident: clone(incident) };
  }

  function recordHealthAction(incidentId, text) {
    const incident = state.health.incidents.find((item) => item.id === incidentId);
    const action = String(text || "").trim();
    if (!incident || !action) {
      return { ok: false, reason: "ACTION_REQUIRED" };
    }
    incident.records.push({
      sequence: incident.records.length + 1,
      time: `检查 ${incident.records.length + 1}`,
      text: action,
    });
    persist();
    return { ok: true, incident: clone(incident) };
  }

  function recheckHealth(incidentId) {
    const incident = state.health.incidents.find((item) => item.id === incidentId);
    if (!incident) {
      return { ok: false, reason: "INCIDENT_NOT_FOUND" };
    }
    incident.recheckCount += 1;
    incident.status =
      incident.recheckCount >= incident.recoverAfter ? "recovered" : "degraded";
    incident.records.push({
      sequence: incident.records.length + 1,
      time: `复查 ${incident.recheckCount}`,
      text:
        incident.status === "recovered"
          ? "链路已恢复，继续观察下一次受控检查。"
          : "链路仍异常，保留当前限制并继续观察。",
    });
    persist();
    return { ok: true, status: incident.status, incident: clone(incident) };
  }

  function selectRecoveryBackup(backupId) {
    const backup = state.recovery.backups.find((item) => item.id === backupId);
    if (!backup) {
      return { ok: false, reason: "BACKUP_NOT_FOUND" };
    }
    Object.assign(state.recovery, {
      stage: "selected",
      selectedBackupId: backupId,
      progress: 0,
      report: null,
      failure: null,
    });
    persist();
    return clone(state.recovery);
  }

  function runIntegrityCheck() {
    const backup = state.recovery.backups.find(
      (item) => item.id === state.recovery.selectedBackupId,
    );
    if (!backup || state.recovery.stage !== "selected") {
      return { ok: false, reason: "BACKUP_SELECTION_REQUIRED" };
    }
    if (!backup.willPass) {
      state.recovery.stage = "failed";
      state.recovery.failure = "完整性检查未通过；没有创建演练环境。";
    } else {
      state.recovery.stage = "readyToConfirm";
    }
    persist();
    return clone(state.recovery);
  }

  function confirmRecovery() {
    if (state.recovery.stage !== "readyToConfirm") {
      return { ok: false, reason: "INTEGRITY_CHECK_REQUIRED" };
    }
    state.recovery.stage = "running";
    state.recovery.progress = 10;
    persist();
    return clone(state.recovery);
  }

  function advanceRecovery() {
    if (state.recovery.stage !== "running") {
      return { ok: false, reason: "RECOVERY_NOT_RUNNING" };
    }
    if (state.recovery.progress < 50) {
      state.recovery.progress = 55;
    } else {
      state.recovery.progress = 100;
      state.recovery.stage = "reported";
      state.recovery.report = {
        result: "通过",
        restoredTo: state.recovery.target,
        productionChanged: false,
        verifiedItems: ["租户目录摘要", "知识对象清单", "审计连续性"],
      };
    }
    persist();
    return clone(state.recovery);
  }

  function retryRecovery() {
    if (state.recovery.stage !== "failed") {
      return { ok: false, reason: "NO_FAILED_CHECK" };
    }
    state.recovery.stage = "selected";
    state.recovery.failure = null;
    persist();
    return clone(state.recovery);
  }

  function resetRecovery() {
    state.recovery = defaultState().recovery;
    persist();
    return clone(state.recovery);
  }

  function reloadFromStorage() {
    state = initialState(storage);
    if (querySessionClosed) state.query.session = { ...defaultQueryState().session, epoch: state.query.session.epoch };
    tenantDrafts.clear();
    memberDrafts.clear();
    return clone(state);
  }

  return {
    reset,
    getSnapshot: () => clone(state),
    updateTenantSetup,
    discardTenantSetup,
    getTenantQuotas,
    saveTenantQuotas,
    getTenantSecurity,
    saveTenantSecurity,
    createAccount,
    resetAccountPassword,
    assignPlatformAdministrator,
    setAccountStatus,
    createKnowledgeBase,
    selectKnowledgeBase,
    selectKnowledgeSource,
    addKnowledgeSource,
    advanceKnowledgeSource,
    retryKnowledgeSource,
    reprocessKnowledgeSource,
    selectIndexVersion,
    createIndexRebuild,
    advanceIndexBuild,
    recordIndexEvaluation,
    activateIndexVersion,
    cancelIndexBuild,
    createTenant,
    addMember,
    findMember,
    setMemberStatus,
    setTenantFeatureDraft,
    discardTenantFeatureDraft,
    commitTenantFeatureDraft,
    setMemberFeatureDraft,
    setMemberAssistantDraft,
    discardMemberFeatureDraft,
    commitMemberFeatureDraft,
    getEffectiveAccess,
    authorizeScreen,
    queryProjection,
    getQuerySession,
    loginQuery,
    logoutQuery,
    createQueryConversation,
    startQueryRun,
    advanceQueryRun,
    pauseQueryRun,
    resumeQueryRun,
    retryQueryRun,
    stopQueryRun,
    loadEarlierQueryMessages,
    reloadFromStorage,
    selectTenant,
    selectDemoAccount,
    selectHealthIncident,
    recordHealthAction,
    recheckHealth,
    selectRecoveryBackup,
    runIntegrityCheck,
    confirmRecovery,
    advanceRecovery,
    retryRecovery,
    resetRecovery,
  };
}
