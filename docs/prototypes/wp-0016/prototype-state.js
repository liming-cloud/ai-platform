import { restoreDetailContext } from "./detail-context.js";
import { resolveProviderPreset } from "./provider-presets.js";

export const DEFAULT_TENANT_ID = "tenant-spruce";
export const PROTOTYPE_STATE_KEY = "wp0016-business-demo-v1";
export const QUERY_SESSION_KEY = "wp0016-query-session-v1";

/** 公开、非秘密的验收夹具；只持久化场景标识，不接受真实密码或保存认证摘要。 */
export const DEMO_LOGIN_SCENARIOS = Object.freeze({
  INITIAL: "demo-only",
  CREATED: "Initial-only-2026!",
  ROTATED: "Reset-only-2026!",
});

function demoLoginScenario(value) {
  return Object.entries(DEMO_LOGIN_SCENARIOS).find(([, phrase]) => phrase === value)?.[0] ?? null;
}

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
  "assistant-overview": "assistantDelivery",
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
  "access-policies": "assistantDelivery",
  "access-policy-detail": "assistantDelivery",
  "governance-query": "evaluationGovernance",
  "query-diagnostic": "evaluationGovernance",
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

function defaultQueryState() {
  return {
    session: {
      authenticated: false,
      tenantId: null,
      accountId: null,
      accessChannel: null,
      authenticationRevision: null,
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

const MODEL_CAPABILITIES = Object.freeze(["CHAT", "EMBEDDING", "RERANK"]);

function modelParameters(capability, overrides = {}) {
  const defaults = {
    CHAT: { temperature: 0, maxOutputTokens: 4096 },
    EMBEDDING: { embeddingDimension: 1024, normalization: "L2", distanceMetric: "COSINE" },
    RERANK: { candidateCount: 30, evidenceCount: 8 },
  }[capability] || {};
  return { ...defaults, ...overrides };
}

function defaultModelGovernance() {
  return {
    platformCandidateConfirmations: [],
    platformConnections: [
      {
        id: "pc-chat",
        name: "企业对话模型连接",
        providerKind: "OPENAI_COMPATIBLE",
        runtimeProtocol: "OPENAI_COMPATIBLE",
        runtimeOrigin: "https://chat.example.invalid",
        capability: "CHAT",
        status: "PUBLISHED",
        version: 4,
        authenticationState: "CONFIGURED",
        lastProbe: "今天 16:20",
        residency: "华东受控区",
        discoveredModels: [
          { id: "model-chat-v6", name: "企业对话模型 v6", capability: "CHAT" },
          { id: "model-unconfirmed-s2", name: "待确认合成模型", capability: "待确认" },
        ],
      },
      {
        id: "pc-embedding",
        name: "向量服务连接",
        providerKind: "OPENAI_COMPATIBLE",
        runtimeProtocol: "OPENAI_COMPATIBLE",
        runtimeOrigin: "https://embedding.example.invalid",
        capability: "EMBEDDING",
        status: "PUBLISHED",
        version: 3,
        authenticationState: "CONFIGURED",
        lastProbe: "今天 16:05",
        residency: "华东受控区",
        discoveredModels: [{ id: "model-embedding-v4", name: "企业向量模型 v4", capability: "EMBEDDING", embeddingDimension: 1024 }],
      },
      {
        id: "pc-rerank",
        name: "重排服务连接",
        providerKind: "OPENAI_COMPATIBLE",
        runtimeProtocol: "VENDOR_POST_PROCESSOR",
        runtimeOrigin: "https://rerank.example.invalid",
        capability: "RERANK",
        status: "PUBLISHED",
        version: 2,
        authenticationState: "CONFIGURED",
        lastProbe: "今天 15:48",
        residency: "华东受控区",
        discoveredModels: [{ id: "model-rerank-v2", name: "企业重排模型 v2", capability: "RERANK" }],
      },
    ],
    selectedPlatformConnectionId: "pc-chat",
    platformDeployments: [
      {
        id: "pd-chat-v6",
        name: "企业对话模型 v6",
        connectionId: "pc-chat",
        modelId: "model-chat-v6",
        capability: "CHAT",
        status: "PUBLISHED",
        tenantIds: [DEFAULT_TENANT_ID],
        residency: "华东受控区",
        verifiedAt: "今天 16:24",
      },
      {
        id: "pd-chat-backup",
        name: "企业对话模型 v6 备用",
        connectionId: "pc-chat",
        modelId: "model-chat-v6",
        capability: "CHAT",
        status: "PUBLISHED",
        tenantIds: [DEFAULT_TENANT_ID],
        residency: "华东受控区",
        verifiedAt: "今天 16:22",
      },
      {
        id: "pd-embedding-v4",
        name: "企业向量模型 v4",
        connectionId: "pc-embedding",
        modelId: "model-embedding-v4",
        capability: "EMBEDDING",
        status: "PUBLISHED",
        tenantIds: [DEFAULT_TENANT_ID],
        residency: "华东受控区",
        verifiedAt: "今天 16:08",
      },
      {
        id: "pd-rerank-v2",
        name: "企业重排模型 v2",
        connectionId: "pc-rerank",
        modelId: "model-rerank-v2",
        capability: "RERANK",
        status: "PUBLISHED",
        tenantIds: [DEFAULT_TENANT_ID],
        residency: "华东受控区",
        verifiedAt: "今天 15:52",
      },
      {
        id: "pd-chat-candidate",
        name: "企业对话模型 v7 候选",
        connectionId: "pc-chat",
        modelId: "model-chat-v6",
        capability: "CHAT",
        status: "VERIFIED",
        tenantIds: [DEFAULT_TENANT_ID],
        residency: "华东受控区",
        verifiedAt: "今天 16:45",
      },
    ],
    selectedPlatformDeploymentId: "pd-chat-candidate",
    tenantDiscoveries: [{
      tenantId: DEFAULT_TENANT_ID,
      connectionId: "pc-chat",
      status: "COMPLETE",
      deploymentIds: ["pd-chat-v6", "pd-chat-backup"],
      updatedAt: "今天 16:30",
    }],
    selectedTenantConnectionId: "pc-chat",
    selectedCatalogDeploymentId: "pd-chat-v6",
    configurations: [
      {
        id: "cfg-chat",
        tenantId: DEFAULT_TENANT_ID,
        name: "员工服务对话配置",
        platformDeploymentId: "pd-chat-v6",
        capability: "CHAT",
        status: "ACTIVE",
        activeRevision: 6,
        pendingRevision: null,
        parameters: modelParameters("CHAT"),
        authenticationState: "CONFIGURED",
        updatedAt: "今天 16:35",
      },
      {
        id: "cfg-embedding",
        tenantId: DEFAULT_TENANT_ID,
        name: "知识库向量配置",
        platformDeploymentId: "pd-embedding-v4",
        capability: "EMBEDDING",
        status: "ACTIVE",
        activeRevision: 4,
        pendingRevision: null,
        parameters: modelParameters("EMBEDDING"),
        authenticationState: "PLATFORM_MANAGED",
        updatedAt: "今天 16:12",
      },
      {
        id: "cfg-rerank",
        tenantId: DEFAULT_TENANT_ID,
        name: "召回重排配置",
        platformDeploymentId: "pd-rerank-v2",
        capability: "RERANK",
        status: "ACTIVE",
        activeRevision: 2,
        pendingRevision: null,
        parameters: modelParameters("RERANK"),
        authenticationState: "CONFIGURED",
        updatedAt: "今天 15:58",
      },
      {
        id: "cfg-rerank-candidate",
        tenantId: DEFAULT_TENANT_ID,
        name: "召回重排配置候选",
        platformDeploymentId: "pd-rerank-v2",
        capability: "RERANK",
        status: "VERIFIED",
        activeRevision: null,
        pendingRevision: {
          number: 1,
          status: "VERIFIED",
          parameters: modelParameters("RERANK"),
          changedFields: ["candidateCount", "evidenceCount"],
          result: "能力、限时调用和敏感输出检查通过。",
        },
        parameters: modelParameters("RERANK"),
        authenticationState: "CONFIGURED",
        updatedAt: "今天 16:46",
      },
    ],
    selectedConfigurationId: "cfg-rerank-candidate",
    deployments: [
      {
        id: "td-chat-v6",
        tenantId: DEFAULT_TENANT_ID,
        name: "员工服务 Chat",
        configurationId: "cfg-chat",
        capability: "CHAT",
        status: "PUBLISHED",
        residency: "华东受控区",
        assistantIds: ["assistant-employee-service"],
        verifiedAt: "今天 16:40",
      },
      {
        id: "td-chat-backup",
        tenantId: DEFAULT_TENANT_ID,
        name: "员工服务 Chat 备用",
        configurationId: "cfg-chat",
        capability: "CHAT",
        status: "PUBLISHED",
        residency: "华东受控区",
        assistantIds: [],
        verifiedAt: "今天 16:38",
      },
      {
        id: "td-embedding-v4",
        tenantId: DEFAULT_TENANT_ID,
        name: "知识检索 Embedding",
        configurationId: "cfg-embedding",
        capability: "EMBEDDING",
        status: "PUBLISHED",
        residency: "华东受控区",
        assistantIds: [],
        verifiedAt: "今天 16:18",
      },
      {
        id: "td-rerank-candidate",
        tenantId: DEFAULT_TENANT_ID,
        name: "结果重排候选",
        configurationId: "cfg-rerank",
        capability: "RERANK",
        status: "VERIFIED",
        residency: "华东受控区",
        assistantIds: [],
        verifiedAt: "今天 16:47",
      },
    ],
    selectedDeploymentId: "td-rerank-candidate",
    routingPolicies: [{
      id: "route-employee",
      tenantId: DEFAULT_TENANT_ID,
      name: "员工服务路由",
      version: 5,
      status: "DRAFT",
      primaryDeploymentId: "td-chat-v6",
      fallbackDeploymentId: "td-chat-backup",
      monthlyBudget: 10000,
      simulation: null,
      updatedAt: "今天 16:42",
    }],
    selectedRoutingPolicyId: "route-employee",
    effects: [],
  };
}

function frozenAssistantDependencies(knowledgeBaseId, indexVersionId) {
  return {
    knowledgeBaseId,
    indexVersionId,
    configurationRevisions: {
      chat: { id: "cfg-chat", revision: 6 },
      embedding: { id: "cfg-embedding", revision: 4 },
      rerank: { id: "cfg-rerank", revision: 2 },
    },
    retrievalPolicy: "HYBRID_RRF_RERANK_V3",
    citationPolicy: "REQUIRED_V2",
    refusalPolicy: "CALIBRATED_V5",
  };
}

function passedAssistantEvaluation() {
  return {
    status: "PASSED",
    datasetVersion: 6,
    evaluatedAt: "今天 16:52",
    metrics: {
      recallAt10: 0.88,
      citationPrecision: 0.97,
      refusalAccuracy: 0.99,
      unauthorizedLeakage: 0,
      firstTokenP95Ms: 2840,
    },
  };
}

function answeredAssistantTest(addedToEvaluation = false) {
  return {
    id: "assistant-test-leave-transfer",
    question: "年假最多可以结转几天？",
    status: "ANSWERED",
    answer: "未休年假最多可结转 5 个工作日，并需在次年 3 月 31 日前使用。",
    citations: [
      { source: "2026 员工假期制度.pdf", location: "第 6 页 · 年假结转" },
      { source: "福利办事指南.docx", location: "假期申请 · 第 3 节" },
    ],
    frozenIndexVersionId: "idx-v11",
    addedToEvaluation,
  };
}

/** 稳定助手永久绑定知识库；候选版本只冻结可替换的索引、模型配置修订和问答策略。 */
function defaultAssistantDelivery() {
  return {
    assistants: [
      {
        id: "assistant-employee-service",
        tenantId: DEFAULT_TENANT_ID,
        name: "员工服务助手",
        summary: "查询制度、假期、福利和办事流程，并提供可定位引用。",
        knowledgeBaseId: "kb-handbook",
        status: "PUBLISHED",
        publishedVersionId: "asv-0012",
        versions: [
          {
            id: "asv-0012",
            number: 12,
            status: "PUBLISHED",
            knowledgeBaseId: "kb-handbook",
            frozen: frozenAssistantDependencies("kb-handbook", "idx-v11"),
            publishedAt: "今天 16:58",
          },
          {
            id: "asv-0013",
            number: 13,
            status: "READY_FOR_RELEASE",
            knowledgeBaseId: "kb-handbook",
            frozen: frozenAssistantDependencies("kb-handbook", "idx-v12"),
            testResult: { ...answeredAssistantTest(true), frozenIndexVersionId: "idx-v12" },
            evaluationDataset: { id: "eval-set-v6", version: 6, questionCount: 100, frozenAt: "今天 16:48" },
            evaluation: passedAssistantEvaluation(),
          },
        ],
      },
      {
        id: "assistant-support",
        tenantId: DEFAULT_TENANT_ID,
        name: "客户支持助手",
        summary: "查询合成产品说明和故障排查步骤。",
        knowledgeBaseId: "kb-support",
        status: "PUBLISHED",
        publishedVersionId: "asv-0008",
        versions: [
          {
            id: "asv-0008",
            number: 8,
            status: "PUBLISHED",
            knowledgeBaseId: "kb-support",
            frozen: frozenAssistantDependencies("kb-support", "idx-v7"),
            publishedAt: "昨天 18:20",
          },
          {
            id: "asv-0009",
            number: 9,
            status: "TESTED",
            knowledgeBaseId: "kb-support",
            frozen: frozenAssistantDependencies("kb-support", "idx-v7"),
            testResult: { ...answeredAssistantTest(false), frozenIndexVersionId: "idx-v7" },
          },
        ],
      },
      {
        id: "assistant-office",
        tenantId: DEFAULT_TENANT_ID,
        name: "办公地点助手",
        summary: "查询办公室地址、开放时间和公共联系方式。",
        knowledgeBaseId: "kb-handbook",
        status: "PUBLISHED",
        publishedVersionId: "asv-0004",
        versions: [
          {
            id: "asv-0004",
            number: 4,
            status: "PUBLISHED",
            knowledgeBaseId: "kb-handbook",
            frozen: frozenAssistantDependencies("kb-handbook", "idx-v11"),
            publishedAt: "8 月 29 日",
          },
          {
            id: "asv-0005",
            number: 5,
            status: "DRAFT",
            knowledgeBaseId: "kb-handbook",
            frozen: null,
          },
        ],
      },
      {
        id: "assistant-policy",
        tenantId: DEFAULT_TENANT_ID,
        name: "制度问答助手",
        summary: "面向制度维护人员验证检索质量和拒答边界。",
        knowledgeBaseId: "kb-handbook",
        status: "PUBLISHED",
        publishedVersionId: "asv-policy-0002",
        versions: [
          {
            id: "asv-policy-0002",
            number: 2,
            status: "PUBLISHED",
            knowledgeBaseId: "kb-handbook",
            frozen: frozenAssistantDependencies("kb-handbook", "idx-v11"),
            publishedAt: "8 月 28 日",
          },
          {
            id: "asv-policy-0003",
            number: 3,
            status: "EVALUATED",
            knowledgeBaseId: "kb-handbook",
            frozen: frozenAssistantDependencies("kb-handbook", "idx-v12"),
            testResult: { ...answeredAssistantTest(true), frozenIndexVersionId: "idx-v12" },
            evaluationDataset: { id: "eval-set-v6", version: 6, questionCount: 100, frozenAt: "今天 16:32" },
            evaluation: passedAssistantEvaluation(),
          },
        ],
      },
    ],
    selectedAssistantId: "assistant-employee-service",
    selectedVersionId: "asv-0013",
    effects: [],
  };
}

const EVALUATION_SAMPLE_TYPES = Object.freeze([
  ["ANSWERABLE", "可回答"],
  ["REFUSAL", "证据不足拒答"],
  ["AUTHORIZATION", "权限拒绝"],
  ["MULTI_TURN", "多轮指代"],
]);

function evaluationSamples(prefix, composition) {
  const samples = [];
  for (const [type, label] of EVALUATION_SAMPLE_TYPES) {
    const key = {
      ANSWERABLE: "answerable",
      REFUSAL: "refusal",
      AUTHORIZATION: "authorization",
      MULTI_TURN: "multiTurn",
    }[type];
    for (let index = 1; index <= composition[key]; index += 1) {
      samples.push({
        id: `${prefix}-${type.toLowerCase()}-${index}`,
        type,
        label,
        questionSummary: `${label}合成样本 ${index}`,
        expectedEvidence: ["ANSWERABLE", "MULTI_TURN"].includes(type)
          ? [`src-policy-pdf#evidence-${index}`]
          : [],
        expectedOutcome: type === "ANSWERABLE" || type === "MULTI_TURN" ? "ANSWER" : "REFUSE",
      });
    }
  }
  return samples;
}

function evaluationComposition(samples) {
  const result = { answerable: 0, refusal: 0, authorization: 0, multiTurn: 0 };
  const mapping = {
    ANSWERABLE: "answerable",
    REFUSAL: "refusal",
    AUTHORIZATION: "authorization",
    MULTI_TURN: "multiTurn",
  };
  for (const sample of samples) {
    const key = mapping[sample.type];
    if (key) result[key] += 1;
  }
  return result;
}

function evaluationMetrics(overrides = {}) {
  return {
    recallAt10: 0.88,
    citationPrecision: 0.97,
    refusalAccuracy: 0.99,
    unauthorizedLeakage: 0,
    retrievalP95Ms: 612,
    firstTokenP95Ms: 2840,
    consistency: "PASSED",
    ...overrides,
  };
}

function evaluationFailures() {
  return [
    { category: "RETRIEVAL_MISS", label: "召回不足", sampleCount: 3, suggestedAction: "调整检索或分块" },
    { category: "CITATION_MISMATCH", label: "引用定位偏差", sampleCount: 2, suggestedAction: "修复解析定位" },
    { category: "OVER_REFUSAL", label: "过度拒答", sampleCount: 2, suggestedAction: "校准拒答阈值" },
  ];
}

function completedEvaluationRun(id, assistantVersionId, datasetVersionId, metrics, options = {}) {
  const assistantId = options.assistantId || {
    "asv-0012": "assistant-employee-service",
    "asv-0013": "assistant-employee-service",
    "asv-0008": "assistant-support",
    "asv-policy-0003": "assistant-policy",
  }[assistantVersionId];
  return {
    id,
    tenantId: DEFAULT_TENANT_ID,
    name: options.name || `${assistantVersionId} / ${datasetVersionId}`,
    status: "COMPLETED",
    gate: metrics.unauthorizedLeakage === 0 &&
      metrics.recallAt10 >= 0.85 &&
      metrics.citationPrecision >= 0.95 &&
      metrics.refusalAccuracy >= 0.98 &&
      metrics.retrievalP95Ms < 800 &&
      metrics.firstTokenP95Ms < 4000 &&
      metrics.consistency === "PASSED" ? "PASSED" : "FAILED",
    stageIndex: 4,
    stage: "COMPLETED",
    progress: 100,
    frozen: {
      assistantId,
      assistantVersionId,
      datasetVersionId,
      indexVersionId: options.indexVersionId || "idx-v11",
      configurationRevisions: options.configurationRevisions || frozenAssistantDependencies("kb-handbook", options.indexVersionId || "idx-v11").configurationRevisions,
    },
    metrics,
    failures: evaluationFailures(),
    completedAt: options.completedAt || "今天 16:54",
  };
}

/** 评测数据只保存合成问题摘要和期望证据标识，不包含真实用户问题或回答正文。 */
function defaultEvaluationGovernance() {
  const v6Samples = evaluationSamples("eval-v6", { answerable: 60, refusal: 20, authorization: 10, multiTurn: 10 });
  const v7Samples = evaluationSamples("eval-v7", { answerable: 62, refusal: 20, authorization: 12, multiTurn: 10 });
  const otherSamples = evaluationSamples("eval-support-v4", { answerable: 60, refusal: 20, authorization: 10, multiTurn: 10 });
  const baselineRun = completedEvaluationRun("eval-run-v12", "asv-0012", "eval-set-v6", evaluationMetrics({ recallAt10: 0.85, citationPrecision: 0.96, retrievalP95Ms: 720, firstTokenP95Ms: 3200 }), { name: "员工服务助手 v12 / 基线 v6", indexVersionId: "idx-v11", completedAt: "昨天 18:10" });
  const candidateRun = completedEvaluationRun("eval-run-v13", "asv-0013", "eval-set-v6", evaluationMetrics(), { name: "员工服务助手 v13 / 基线 v6", indexVersionId: "idx-v12" });
  const otherRun = completedEvaluationRun("eval-run-other-dataset", "asv-0008", "eval-support-v4", evaluationMetrics({ recallAt10: 0.87 }), { name: "客户支持助手 v8 / 回归 v4", indexVersionId: "idx-v7" });
  const unsafeRun = completedEvaluationRun("eval-run-security-regression", "asv-0013", "eval-set-v6", evaluationMetrics({ citationPrecision: 0.94, unauthorizedLeakage: 1 }), { name: "员工服务助手 v13 / 安全回退复测", indexVersionId: "idx-v12", assistantId: "assistant-employee-service" });
  const initialComparison = {
    id: "evaluation-comparison-initial",
    tenantId: DEFAULT_TENANT_ID,
    baselineRunId: baselineRun.id,
    candidateRunId: candidateRun.id,
    datasetVersionId: "eval-set-v6",
    sameDataset: true,
    safeToApprove: true,
    noQualityRegression: true,
    decision: "PENDING",
    createdAt: "今天 16:56",
  };
  return {
    datasets: [
      {
        id: "eval-set",
        tenantId: DEFAULT_TENANT_ID,
        name: "员工制度基线",
        knowledgeBaseId: "kb-handbook",
        status: "PUBLISHED",
        publishedVersionId: "eval-set-v6",
        versions: [
          { id: "eval-set-v6", number: 6, status: "PUBLISHED", samples: v6Samples, composition: evaluationComposition(v6Samples), publishedAt: "昨天 17:20" },
          { id: "eval-set-v7", number: 7, status: "DRAFT", samples: v7Samples, composition: evaluationComposition(v7Samples), sourceTemplateVersionId: "eval-set-v6", updatedAt: "今天 16:40" },
        ],
      },
      {
        id: "eval-support",
        tenantId: DEFAULT_TENANT_ID,
        name: "客户支持回归",
        knowledgeBaseId: "kb-support",
        status: "PUBLISHED",
        publishedVersionId: "eval-support-v4",
        versions: [
          { id: "eval-support-v4", number: 4, status: "PUBLISHED", samples: otherSamples, composition: evaluationComposition(otherSamples), publishedAt: "8 月 30 日" },
        ],
      },
    ],
    selectedDatasetId: "eval-set",
    selectedDatasetVersionId: "eval-set-v7",
    runs: [baselineRun, candidateRun, otherRun, unsafeRun],
    selectedRunId: "eval-run-v13",
    comparisons: [initialComparison],
    selectedComparisonId: initialComparison.id,
    remediations: [],
    selectedFailureCategory: "RETRIEVAL_MISS",
  };
}

/**
 * 访问策略只表达普通成员对已发布助手的使用范围；知识库由助手的永久关联关系派生。
 * 查询治理仅保存合成的脱敏终态事实，不保存问题、回答、引用正文或供应商错误。
 */
function defaultAccessGovernance() {
  return {
    accessPolicies: [
      {
        id: "access-policy-all-employees",
        tenantId: DEFAULT_TENANT_ID,
        name: "全员制度访问",
        publishedVersionId: "access-policy-v5",
        versions: [
          {
            id: "access-policy-v5",
            number: 5,
            status: "PUBLISHED",
            assistantId: "assistant-employee-service",
            knowledgeBaseId: "kb-handbook",
            memberAccountIds: ["account-shen"],
            preview: { passed: true, allowCount: 24, denyCount: 8 },
            publishedAt: "昨天 17:12",
          },
          {
            id: "access-policy-v6",
            number: 6,
            status: "DRAFT",
            assistantId: "assistant-employee-service",
            knowledgeBaseId: "kb-handbook",
            memberAccountIds: ["account-shen"],
            preview: null,
            updatedAt: "今天 16:24",
          },
        ],
      },
    ],
    selectedPolicyId: "access-policy-all-employees",
    selectedPolicyVersionId: "access-policy-v6",
    queryQuota: {
      dailyAcceptedLimit: 1000,
      maxConcurrent: 20,
      leaseSeconds: 120,
      timezone: "UTC",
      source: "部署配置",
    },
    queryUsage: {
      acceptedToday: 252,
      remainingToday: 748,
      activeNow: 3,
      refusedToday: 20,
    },
    queryRuns: [
      {
        id: "query-run-refused-042",
        reference: "Q-042",
        tenantId: DEFAULT_TENANT_ID,
        assistantName: "员工服务助手",
        outcome: "REFUSED",
        durationMs: 641,
        occurredAt: "今天 16:42",
        diagnostic: {
          candidateCollection: "FILTERED",
          candidateCount: 0,
          retrievalAuthorization: "AUTHORIZED",
          chatModelRevision: "chat-r13",
          embeddingModelRevision: "embed-r8",
          citationValidation: "NOT_APPLICABLE",
          stage: "INSUFFICIENT_EVIDENCE",
          outcome: "REFUSED",
          historical: true,
          createdAt: "今天 16:42",
        },
      },
      {
        id: "query-run-answered-041",
        reference: "Q-041",
        tenantId: DEFAULT_TENANT_ID,
        assistantName: "客户支持助手",
        outcome: "ANSWERED",
        durationMs: 524,
        occurredAt: "今天 16:38",
        diagnostic: {
          candidateCollection: "FILTERED",
          candidateCount: 30,
          retrievalAuthorization: "AUTHORIZED",
          chatModelRevision: "chat-r11",
          embeddingModelRevision: "embed-r7",
          citationValidation: "VALIDATED",
          stage: "COMPLETED",
          outcome: "ANSWERED",
          historical: true,
          createdAt: "今天 16:38",
        },
      },
      {
        id: "query-run-failed-040",
        reference: "Q-040",
        tenantId: DEFAULT_TENANT_ID,
        assistantName: "办公地点助手",
        outcome: "FAILED",
        durationMs: 812,
        occurredAt: "今天 16:31",
        diagnostic: {
          candidateCollection: "NOT_COMPLETED",
          candidateCount: null,
          retrievalAuthorization: "AUTHORIZED",
          chatModelRevision: "chat-r13",
          embeddingModelRevision: "embed-r8",
          citationValidation: "NOT_APPLICABLE",
          stage: "RETRIEVAL_FAILED",
          outcome: "FAILED",
          historical: true,
          createdAt: "今天 16:31",
        },
      },
    ],
    selectedQueryRunId: "query-run-refused-042",
    queryRemediations: [],
  };
}

/**
 * 结构化数据只冻结字段规则，不保存样本行；对象范围只保存平台签发的业务标识。
 * 桶名、对象键、端点和凭据属于部署私有事实，禁止进入原型状态与读取投影。
 */
function defaultDataGovernance() {
  const fields = [
    { name: "office_name", logicalType: "TEXT", semantic: "地点名称", queryEligible: true, sensitivity: "PUBLIC" },
    { name: "floor", logicalType: "INTEGER", semantic: "楼层", queryEligible: false, sensitivity: "PUBLIC" },
    { name: "access_note", logicalType: "TEXT", semantic: "到访说明", queryEligible: true, sensitivity: "PUBLIC" },
    { name: "contact", logicalType: "TEXT", semantic: "内部联系人", queryEligible: false, sensitivity: "RESTRICTED" },
  ];
  return {
    structuredDatasets: [
      {
        id: "dataset-office-directory",
        tenantId: DEFAULT_TENANT_ID,
        knowledgeBaseId: "kb-handbook",
        name: "办公地点数据",
        sourceRevision: "rev-0004",
        sourceName: "办公地点清单.csv",
        headers: fields.map(field => field.name),
        publishedVersionId: "dataset-schema-v3",
        selectedVersionId: "dataset-schema-v4",
        versions: [
          {
            id: "dataset-schema-v3",
            number: 3,
            status: "PUBLISHED",
            headers: fields.map(field => field.name),
            fields: clone(fields),
            contentField: "access_note",
            titleField: "office_name",
            rowFilter: "NON_BLANK_CONTENT",
            qualityRuleVersion: "dataset-csv-v1",
            preview: { passed: true, checkedRows: 100, blockedRows: 0, skippedRows: 2 },
            publishedAt: "昨天 16:20",
          },
          {
            id: "dataset-schema-v4",
            number: 4,
            status: "DRAFT",
            headers: fields.map(field => field.name),
            fields: clone(fields),
            contentField: "access_note",
            titleField: "office_name",
            rowFilter: "NON_BLANK_CONTENT",
            qualityRuleVersion: "dataset-csv-v1",
            preview: null,
            updatedAt: "今天 14:36",
          },
        ],
      },
    ],
    selectedDatasetId: "dataset-office-directory",
    platformObjectRanges: [
      {
        id: "range-east-office-text",
        tenantId: DEFAULT_TENANT_ID,
        label: "华东办公制度文本",
        region: "华东受控区",
        selectionRevision: 7,
        objectCount: 18,
        mediaType: "text/plain",
        status: "AVAILABLE",
      },
      {
        id: "range-east-support-text",
        tenantId: DEFAULT_TENANT_ID,
        label: "华东客户支持文本",
        region: "华东受控区",
        selectionRevision: 3,
        objectCount: 42,
        mediaType: "text/plain",
        status: "AVAILABLE",
      },
      {
        id: "range-other-tenant",
        tenantId: "tenant-other",
        label: "其他租户范围",
        region: "华北受控区",
        selectionRevision: 2,
        objectCount: 9,
        mediaType: "text/plain",
        status: "AVAILABLE",
      },
    ],
    objectScopeRegistrations: [],
  };
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
      ["CONSOLE", "QUERY_APP"].includes(session.accessChannel) &&
      typeof session.accountId === "string" &&
      Number.isSafeInteger(session.authenticationRevision) &&
      Number.isSafeInteger(session.epoch) && session.epoch > 0
      ? { authenticated: true, tenantId: session.tenantId, accountId: session.accountId,
          accessChannel: session.accessChannel, authenticationRevision: session.authenticationRevision, epoch: session.epoch }
      : anonymous;
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
    ].map(account => ({ ...account, loginScenario: "INITIAL" })),
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
    models: defaultModelGovernance(),
    assistantDelivery: defaultAssistantDelivery(),
    evaluation: defaultEvaluationGovernance(),
    accessGovernance: defaultAccessGovernance(),
    dataGovernance: defaultDataGovernance(),
    auditRecords: [
      { id: "audit-1", tenantId: DEFAULT_TENANT_ID, event: "助手版本发布", scope: "员工服务助手", outcome: "SUCCESS", occurredAt: "昨天 17:40", actorRole: "租户管理员", summary: "发布版本已经冻结，仅影响新会话。" },
      { id: "audit-2", tenantId: DEFAULT_TENANT_ID, event: "访问策略发布", scope: "全员制度访问", outcome: "SUCCESS", occurredAt: "昨天 17:12", actorRole: "租户管理员", summary: "已核对普通成员与助手范围，写入授权版本。" },
      { id: "audit-3", tenantId: DEFAULT_TENANT_ID, event: "索引版本激活", scope: "员工服务手册", outcome: "SUCCESS", occurredAt: "昨天 16:30", actorRole: "租户管理员", summary: "评测通过后切换活动索引，旧版本保留。" },
      { id: "audit-4", tenantId: DEFAULT_TENANT_ID, event: "来源处理超时", scope: "扫描 PDF", outcome: "FAILED", occurredAt: "今天 16:10", actorRole: "受控处理任务", summary: "提取阶段超时，未改变活动索引，可从安全检查点重试。" },
    ],
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
      const initial = defaultState();
      initial.query.session = readQuerySession(storage);
      return initial;
    }
    parsed.tenantSetup = tenantSetupFrom(parsed.tenantSetup);
    parsed.models = parsed.models && Array.isArray(parsed.models.platformConnections)
      ? parsed.models
      : defaultModelGovernance();
    parsed.assistantDelivery = parsed.assistantDelivery && Array.isArray(parsed.assistantDelivery.assistants)
      ? parsed.assistantDelivery
      : defaultAssistantDelivery();
    parsed.evaluation = parsed.evaluation && Array.isArray(parsed.evaluation.datasets)
      ? parsed.evaluation
      : defaultEvaluationGovernance();
    parsed.accessGovernance = parsed.accessGovernance && Array.isArray(parsed.accessGovernance.accessPolicies)
      ? parsed.accessGovernance
      : defaultAccessGovernance();
    parsed.dataGovernance = parsed.dataGovernance && Array.isArray(parsed.dataGovernance.structuredDatasets)
      ? parsed.dataGovernance
      : defaultDataGovernance();
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

  function canManagePlatformModels(actorAccountId) {
    return canManageAccounts(actorAccountId);
  }

  /** 模型治理写操作必须同时满足账户、租户、管理员成员和租户功能上限。 */
  function canManageModels(tenantId, actorAccountId) {
    const tenant = tenantById(tenantId);
    const actor = accountById(actorAccountId);
    const member = tenant?.members.find(item => item.accountId === actorAccountId);
    return Boolean(
      tenant?.status === "active" &&
      actor?.status === "available" &&
      tenant.availableFeatures?.modelGovernance !== false &&
      member?.role === "tenantAdministrator" &&
      member.status === "active",
    );
  }

  function modelItem(collection, id) {
    return state.models[collection].find(item => item.id === id);
  }

  function selectModelGovernanceItem(target, id) {
    const mapping = {
      platformConnection: ["platformConnections", "selectedPlatformConnectionId"],
      platformDeployment: ["platformDeployments", "selectedPlatformDeploymentId"],
      tenantConnection: ["platformConnections", "selectedTenantConnectionId"],
      catalogDeployment: ["platformDeployments", "selectedCatalogDeploymentId"],
      configuration: ["configurations", "selectedConfigurationId"],
      tenantDeployment: ["deployments", "selectedDeploymentId"],
      routingPolicy: ["routingPolicies", "selectedRoutingPolicyId"],
    };
    const [collection, selectedKey] = mapping[target] || [];
    if (!collection || !modelItem(collection, id)) return { ok: false, reason: "MODEL_ITEM_NOT_FOUND" };
    return commitState({ ...state, models: { ...state.models, [selectedKey]: id } });
  }

  function replaceModelItem(collection, item, selection = {}) {
    return {
      ...state,
      models: {
        ...state.models,
        ...selection,
        [collection]: state.models[collection].map(current => current.id === item.id ? item : current),
      },
    };
  }

  function safeRuntimeOrigin(value) {
    try {
      const endpoint = new URL(String(value || ""));
      const hostname = endpoint.hostname.toLowerCase();
      const privateIpv4 = /^(127\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.)/;
      if (
        endpoint.protocol !== "https:" ||
        !hostname ||
        hostname === "localhost" ||
        hostname.endsWith(".local") ||
        hostname === "::1" ||
        privateIpv4.test(hostname)
      ) {
        return null;
      }
      return endpoint.origin;
    } catch {
      return null;
    }
  }

  /** 推理与方舟发现凭据分别校验；只保存配置状态，三项秘密均不进入返回对象或持久化快照。 */
  function createPlatformModelConnection(input = {}, actorAccountId) {
    if (!canManagePlatformModels(actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const name = typeof input.name === "string" ? input.name.trim() : "";
    const runtimeOrigin = safeRuntimeOrigin(input.runtimeBaseUrl);
    const capability = String(input.capability || "CHAT");
    const preset = resolveProviderPreset({ ...input, capability });
    if (!preset.ok) return preset;
    if (input.providerId && input.providerId !== "CUSTOM" && input.runtimeBaseUrl !== preset.runtimeBaseUrl) return { ok: false, reason: "PROVIDER_ENDPOINT_MISMATCH" };
    if (name.length < 2 || name.length > 80) return { ok: false, reason: "CONNECTION_NAME_INVALID" };
    if (!runtimeOrigin) return { ok: false, reason: "UNSAFE_RUNTIME_ENDPOINT" };
    if (!MODEL_CAPABILITIES.includes(capability)) return { ok: false, reason: "MODEL_CAPABILITY_INVALID" };
    if (typeof input.accessKey !== "string" || input.accessKey.length < 8) return { ok: false, reason: "ACCESS_KEY_REQUIRED" };
    const requiresDiscoveryCredentials = preset.providerId === "VOLCENGINE";
    if (requiresDiscoveryCredentials && [input.discoveryAccessKeyId, input.discoverySecretAccessKey].some(value => typeof value !== "string" || !value.trim() || value.length > 4096)) return { ok: false, reason: "DISCOVERY_CREDENTIALS_REQUIRED" };
    if (!requiresDiscoveryCredentials && [input.discoveryAccessKeyId, input.discoverySecretAccessKey].some(value => value != null && value !== "")) return { ok: false, reason: "DISCOVERY_CREDENTIALS_UNEXPECTED" };
    if (state.models.platformConnections.some(item => item.name === name)) return { ok: false, reason: "CONNECTION_NAME_CONFLICT" };
    const connection = {
      id: `pc-custom-${state.models.platformConnections.length + 1}`,
      name,
      providerKind: "OPENAI_COMPATIBLE",
      providerId: preset.providerId,
      providerName: preset.providerName,
      presetVersion: preset.version,
      runtimeProtocol: preset.runtimeProtocol,
      runtimeOrigin,
      capability,
      status: "DRAFT",
      version: 1,
      authenticationState: "CONFIGURED",
      ...(requiresDiscoveryCredentials ? { discoveryProfile: "VOLCENGINE_ARK_CONTROL_PLANE", discoveryCredentialsConfigured: true } : {}),
      lastProbe: null,
      discoveredModels: [],
    };
    const saved = commitState({
      ...state,
      models: {
        ...state.models,
        platformConnections: [...state.models.platformConnections, connection],
        selectedPlatformConnectionId: connection.id,
      },
    });
    return saved.ok ? { ...saved, connection: clone(connection) } : saved;
  }

  function probePlatformModelConnection(connectionId, actorAccountId, options = {}) {
    if (!canManagePlatformModels(actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = modelItem("platformConnections", connectionId);
    if (!current) return { ok: false, reason: "CONNECTION_NOT_FOUND" };
    const failed = options.fail === true;
    const modelName = {
      CHAT: "企业对话模型候选",
      EMBEDDING: "企业向量模型候选",
      RERANK: "企业重排模型候选",
    }[current.capability];
    const published = current.status === "PUBLISHED";
    const candidateModels = [{
      id: `model-${current.capability.toLowerCase()}-${current.id.replace("pc-", "")}`,
      name: modelName,
      capability: current.capability,
      ...(current.capability === "EMBEDDING" ? { embeddingDimension: 1536 } : {}),
    }];
    const connection = {
      ...current,
      status: failed ? published ? "PUBLISHED" : "FAILED" : published ? "PUBLISHED" : "VERIFIED",
      lastProbe: "刚刚",
      probeResult: failed
        ? published ? "探测失败，已发布版本继续服务。" : "连接失败，未发布任何能力。"
        : "HTTPS、鉴权和能力探测通过。",
      discoveredModels: published ? current.discoveredModels : failed ? [] : candidateModels,
    };
    const saved = commitState(replaceModelItem("platformConnections", connection));
    return saved.ok ? { ...saved, connection: clone(connection) } : saved;
  }

  function publishPlatformModelConnection(connectionId, actorAccountId) {
    if (!canManagePlatformModels(actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = modelItem("platformConnections", connectionId);
    if (!current) return { ok: false, reason: "CONNECTION_NOT_FOUND" };
    if (current.status !== "VERIFIED") return { ok: false, reason: "CONNECTION_NOT_VERIFIED" };
    const connection = { ...current, status: "PUBLISHED", publishedAt: "刚刚" };
    const saved = commitState(replaceModelItem("platformConnections", connection));
    return saved.ok ? { ...saved, connection: clone(connection) } : saved;
  }

  function createPlatformModelDeployment(input = {}, actorAccountId) {
    if (!canManagePlatformModels(actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const connection = modelItem("platformConnections", input.connectionId);
    if (!connection || connection.status !== "PUBLISHED") return { ok: false, reason: "CONNECTION_NOT_PUBLISHED" };
    const model = connection.discoveredModels.find(item => item.id === input.modelId);
    if (!model) return { ok: false, reason: "DISCOVERED_MODEL_NOT_FOUND" };
    const allowedTypes = ["CHAT", "EMBEDDING", "RERANK"];
    const sourceConfirmed = allowedTypes.includes(model.capability);
    const capability = sourceConfirmed ? model.capability : input.capability;
    if (!allowedTypes.includes(capability)) return { ok: false, reason: "MODEL_CONFIRMATION_REQUIRED" };
    if ((input.capability && input.capability !== capability) || capability !== connection.capability) {
      return { ok: false, reason: "MODEL_CAPABILITY_MISMATCH" };
    }
    const contextWindow = Number(input.contextWindow);
    const outputLimit = capability === "CHAT" ? Number(input.outputLimit) : null;
    const embeddingDimension = capability === "EMBEDDING" ? Number(input.embeddingDimension) : null;
    const rerankCandidateLimit = capability === "RERANK" ? Number(input.rerankCandidateLimit) : null;
    const positiveInteger = value => Number.isSafeInteger(value) && value > 0;
    if (!positiveInteger(contextWindow)
      || (outputLimit !== null && (!positiveInteger(outputLimit) || outputLimit > contextWindow))
      || (embeddingDimension !== null && !positiveInteger(embeddingDimension))
      || (rerankCandidateLimit !== null && !positiveInteger(rerankCandidateLimit))) {
      return { ok: false, reason: "MODEL_PARAMETERS_REQUIRED" };
    }
    const parameterSource = typeof input.parameterSource === "string" ? input.parameterSource.trim() : "";
    if (input.modelConfirmed !== true || !parameterSource || parameterSource.length > 160 || /[\r\n]/.test(parameterSource)) {
      return { ok: false, reason: "MODEL_CONFIRMATION_REQUIRED" };
    }
    if (!connection.residency) return { ok: false, reason: "RESIDENCY_UNCONFIRMED" };
    const name = typeof input.name === "string" ? input.name.trim() : "";
    if (name.length < 2 || name.length > 80) return { ok: false, reason: "DEPLOYMENT_NAME_INVALID" };
    const tenantIds = [...new Set(Array.isArray(input.tenantIds) ? input.tenantIds : [])];
    if (!tenantIds.length) return { ok: false, reason: "TENANT_AUTHORIZATION_REQUIRED" };
    if (tenantIds.some(id => tenantById(id)?.status !== "active")) return { ok: false, reason: "TENANT_AUTHORIZATION_INVALID" };
    const confirmations = state.models.platformCandidateConfirmations || [];
    const confirmed = confirmations.find(item => item.connectionId === connection.id
      && item.connectionVersion === connection.version && item.modelId === model.id);
    if (confirmed && confirmed.capability !== capability) return { ok: false, reason: "MODEL_CONFIRMATION_CONFLICT" };
    const confirmation = confirmed || {
      connectionId: connection.id, connectionVersion: connection.version, modelId: model.id,
      capability, origin: sourceConfirmed ? "SOURCE_CONFIRMED" : "CONFIRMED_BY_ADMIN",
      confirmedBy: actorAccountId, confirmedAt: "刚刚", parameterSource,
    };
    const deployment = {
      id: `pd-custom-${state.models.platformDeployments.length + 1}`,
      name, connectionId: connection.id, connectionVersion: connection.version,
      modelId: model.id, capability, status: "DRAFT", tenantIds, authorizationStatus: "PLANNED",
      residency: connection.residency, verifiedAt: null, modelConfirmation: clone(confirmation),
      parameters: { contextWindow, outputLimit, embeddingDimension, rerankCandidateLimit, parameterSource },
    };
    const saved = commitState({
      ...state,
      models: {
        ...state.models,
        platformCandidateConfirmations: confirmed ? confirmations : [...confirmations, confirmation],
        platformDeployments: [...state.models.platformDeployments, deployment],
        selectedPlatformDeploymentId: deployment.id,
      },
    });
    return saved.ok ? { ...saved, deployment: clone(deployment) } : saved;
  }

  function verifyPlatformModelDeployment(deploymentId, actorAccountId, options = {}) {
    if (!canManagePlatformModels(actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = modelItem("platformDeployments", deploymentId);
    if (!current) return { ok: false, reason: "DEPLOYMENT_NOT_FOUND" };
    if (!["DRAFT", "FAILED"].includes(current.status)) return { ok: false, reason: "DEPLOYMENT_NOT_VERIFIABLE" };
    const connection = modelItem("platformConnections", current.connectionId);
    if (!connection || connection.status !== "PUBLISHED" || connection.version !== current.connectionVersion) {
      return { ok: false, reason: "CONNECTION_CHANGED" };
    }
    if (!current.parameters || !current.modelConfirmation) return { ok: false, reason: "MODEL_CONFIRMATION_REQUIRED" };
    // 这里只演示受控样本的结果和上限，不调用厂商，也不把演示值视为生产能力证据。
    const failed = options.fail === true || options.budgetExceeded === true || options.safetyFailure === true;
    const deployment = {
      ...current, status: failed ? "FAILED" : "VERIFIED", verifiedAt: "刚刚",
      verification: {
        sampleVersion: "s2-capability-samples-v1", requestLimit: 3, inputCharacterLimit: 128,
        outputTokenLimit: 64, totalTimeoutMillis: 30000, mode: "PROTOTYPE_SIMULATION",
        budgetResult: options.budgetExceeded === true ? "FAILED" : "PASSED",
        sampleResult: failed ? "FAILED" : "PASSED", monthlyCostVerified: false,
      },
      verificationResult: options.budgetExceeded === true
        ? "验证超出本次请求或输出上限，部署未发布。"
        : options.safetyFailure === true ? "合成安全样本未通过，部署未发布。"
          : failed ? "能力验证失败，未进入授权目录。" : "能力与本次受控样本通过；月度费用未核验。",
    };
    const saved = commitState(replaceModelItem("platformDeployments", deployment));
    return saved.ok ? { ...saved, deployment: clone(deployment) } : saved;
  }

  function publishPlatformModelDeployment(deploymentId, actorAccountId) {
    if (!canManagePlatformModels(actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = modelItem("platformDeployments", deploymentId);
    if (!current) return { ok: false, reason: "DEPLOYMENT_NOT_FOUND" };
    if (current.status !== "VERIFIED") return { ok: false, reason: "DEPLOYMENT_NOT_VERIFIED" };
    const connection = modelItem("platformConnections", current.connectionId);
    if (!connection || connection.status !== "PUBLISHED" || connection.version !== current.connectionVersion
      || connection.residency !== current.residency) return { ok: false, reason: "CONNECTION_CHANGED" };
    if (current.tenantIds.some(id => tenantById(id)?.status !== "active")) return { ok: false, reason: "TENANT_AUTHORIZATION_INVALID" };
    const deployment = { ...current, status: "PUBLISHED", authorizationStatus: "EFFECTIVE", publishedAt: "刚刚" };
    const saved = commitState(replaceModelItem("platformDeployments", deployment));
    return saved.ok ? { ...saved, deployment: clone(deployment) } : saved;
  }

  /** 租户发现只投影平台已发布且显式授权给当前租户的部署。 */
  function discoverTenantModels(tenantId, connectionId, actorAccountId) {
    if (!canManageModels(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const connection = modelItem("platformConnections", connectionId);
    if (!connection || connection.status !== "PUBLISHED") return { ok: false, reason: "CONNECTION_NOT_AVAILABLE" };
    const models = state.models.platformDeployments.filter(item =>
      item.connectionId === connectionId &&
      item.status === "PUBLISHED" &&
      item.tenantIds.includes(tenantId)
    );
    const discovery = {
      tenantId,
      connectionId,
      status: "COMPLETE",
      deploymentIds: models.map(item => item.id),
      updatedAt: "刚刚",
    };
    const discoveries = [
      ...state.models.tenantDiscoveries.filter(item => !(item.tenantId === tenantId && item.connectionId === connectionId)),
      discovery,
    ];
    const saved = commitState({
      ...state,
      models: {
        ...state.models,
        tenantDiscoveries: discoveries,
        selectedTenantConnectionId: connectionId,
        selectedCatalogDeploymentId: models[0]?.id ?? null,
      },
    });
    return saved.ok ? { ...saved, discovery: clone(discovery), models: clone(models) } : saved;
  }

  function createTenantModelConfiguration(tenantId, input = {}, actorAccountId) {
    if (!canManageModels(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const platformDeployment = modelItem("platformDeployments", input.platformDeploymentId);
    if (
      !platformDeployment ||
      platformDeployment.status !== "PUBLISHED" ||
      !platformDeployment.tenantIds.includes(tenantId)
    ) {
      return { ok: false, reason: "MODEL_NOT_AUTHORIZED" };
    }
    const name = typeof input.name === "string" ? input.name.trim() : "";
    if (name.length < 2 || name.length > 80) return { ok: false, reason: "CONFIGURATION_NAME_INVALID" };
    if (state.models.configurations.some(item => item.tenantId === tenantId && item.name === name)) {
      return { ok: false, reason: "CONFIGURATION_NAME_CONFLICT" };
    }
    const configuration = {
      id: `cfg-custom-${state.models.configurations.length + 1}`,
      tenantId,
      name,
      platformDeploymentId: platformDeployment.id,
      capability: platformDeployment.capability,
      status: "DRAFT",
      activeRevision: null,
      pendingRevision: {
        number: 1,
        status: "DRAFT",
        parameters: modelParameters(platformDeployment.capability),
        changedFields: ["INITIAL_CONFIGURATION"],
      },
      parameters: null,
      authenticationState: "PLATFORM_MANAGED",
      updatedAt: "刚刚",
    };
    const saved = commitState({
      ...state,
      models: {
        ...state.models,
        configurations: [...state.models.configurations, configuration],
        selectedConfigurationId: configuration.id,
      },
    });
    return saved.ok ? { ...saved, configuration: clone(configuration) } : saved;
  }

  function createTenantModelConfigurationRevision(configurationId, input = {}, actorAccountId) {
    const current = modelItem("configurations", configurationId);
    if (!current) return { ok: false, reason: "CONFIGURATION_NOT_FOUND" };
    if (!canManageModels(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (current.status !== "ACTIVE" || current.pendingRevision) return { ok: false, reason: "CONFIGURATION_REVISION_CONFLICT" };
    const parameters = modelParameters(current.capability, { ...current.parameters, ...input });
    const changedFields = Object.keys(parameters).filter(key => parameters[key] !== current.parameters?.[key]);
    if (!changedFields.length) return { ok: false, reason: "CONFIGURATION_CHANGE_REQUIRED" };
    if (
      current.capability === "EMBEDDING" &&
      (!Number.isSafeInteger(parameters.embeddingDimension) ||
        parameters.embeddingDimension < 384 ||
        parameters.embeddingDimension > 4096 ||
        !["COSINE", "IP", "L2"].includes(parameters.distanceMetric))
    ) {
      return { ok: false, reason: "EMBEDDING_CONFIGURATION_INVALID" };
    }
    const configuration = {
      ...current,
      pendingRevision: {
        number: current.activeRevision + 1,
        status: "DRAFT",
        parameters,
        changedFields,
      },
      updatedAt: "刚刚",
    };
    const saved = commitState(replaceModelItem("configurations", configuration, { selectedConfigurationId: configuration.id }));
    return saved.ok ? { ...saved, configuration: clone(configuration) } : saved;
  }

  /** 新访问密钥不落状态；待验证版本失败时旧活动修订继续服务。 */
  function rotateTenantModelAccessKey(configurationId, accessKey, actorAccountId) {
    const current = modelItem("configurations", configurationId);
    if (!current) return { ok: false, reason: "CONFIGURATION_NOT_FOUND" };
    if (!canManageModels(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (typeof accessKey !== "string" || accessKey.length < 8) return { ok: false, reason: "ACCESS_KEY_REQUIRED" };
    if (current.status !== "ACTIVE" || current.pendingRevision) return { ok: false, reason: "CONFIGURATION_REVISION_CONFLICT" };
    const configuration = {
      ...current,
      pendingRevision: {
        number: current.activeRevision + 1,
        status: "DRAFT",
        parameters: clone(current.parameters),
        changedFields: ["ACCESS_KEY"],
      },
      authenticationState: "ROTATION_PENDING",
      updatedAt: "刚刚",
    };
    const saved = commitState(replaceModelItem("configurations", configuration, { selectedConfigurationId: configuration.id }));
    return saved.ok ? { ...saved, configuration: clone(configuration) } : saved;
  }

  function verifyTenantModelConfiguration(configurationId, actorAccountId, options = {}) {
    const current = modelItem("configurations", configurationId);
    if (!current) return { ok: false, reason: "CONFIGURATION_NOT_FOUND" };
    if (!canManageModels(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (!current.pendingRevision || !["DRAFT", "FAILED"].includes(current.pendingRevision.status)) {
      return { ok: false, reason: "CONFIGURATION_NOT_VERIFIABLE" };
    }
    const configuration = {
      ...current,
      status: current.activeRevision ? "ACTIVE" : options.fail === true ? "DRAFT" : "VERIFIED",
      pendingRevision: {
        ...current.pendingRevision,
        status: options.fail === true ? "FAILED" : "VERIFIED",
        result: options.fail === true ? "验证失败，旧版本继续服务。" : "能力、限时调用和敏感输出检查通过。",
      },
      updatedAt: "刚刚",
    };
    const saved = commitState(replaceModelItem("configurations", configuration));
    return saved.ok ? { ...saved, configuration: clone(configuration) } : saved;
  }

  function buildEmbeddingIndexCandidate(knowledgeBase, configuration, revision) {
    if (!knowledgeBase.activeIndex || knowledgeBase.indexVersions.some(item => ["BUILDING", "EVALUATING"].includes(item.status))) {
      return { knowledgeBase, effect: null };
    }
    const number = Math.max(0, ...knowledgeBase.indexVersions.map(item => Number(item.id.replace(/^idx-v/, "")) || 0)) + 1;
    const active = knowledgeBase.indexVersions.find(item => item.id === knowledgeBase.activeIndex) || knowledgeBase.indexVersions[0];
    const indexVersion = {
      id: `idx-v${number}`,
      status: "BUILDING",
      stage: "MANIFEST_FREEZING",
      trigger: "CONFIGURATION_CHANGE",
      changedFields: clone(revision.changedFields),
      reason: `Embedding 配置 ${configuration.name} v${revision.number} 已启用`,
      config: {
        ...(active?.config || indexConfig()),
        embeddingModel: configuration.name,
        embeddingDimension: revision.parameters.embeddingDimension,
        normalization: revision.parameters.normalization,
        distanceMetric: revision.parameters.distanceMetric.toLowerCase(),
      },
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
    return {
      knowledgeBase: {
        ...knowledgeBase,
        status: "INDEXING",
        updatedAt: "刚刚",
        indexVersions: [indexVersion, ...knowledgeBase.indexVersions],
      },
      effect: { type: "FULL_INDEX_REBUILD_CREATED", knowledgeBaseId: knowledgeBase.id, indexVersionId: indexVersion.id },
    };
  }

  /** 启用修订与其后续影响同次提交；Embedding 只建候选索引，不自动切换活动指针。 */
  function enableTenantModelConfiguration(configurationId, actorAccountId) {
    const current = modelItem("configurations", configurationId);
    if (!current) return { ok: false, reason: "CONFIGURATION_NOT_FOUND" };
    if (!canManageModels(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (current.pendingRevision?.status !== "VERIFIED") return { ok: false, reason: "CONFIGURATION_NOT_VERIFIED" };
    const platformDeployment = modelItem("platformDeployments", current.platformDeploymentId);
    if (
      !platformDeployment ||
      platformDeployment.status !== "PUBLISHED" ||
      !platformDeployment.tenantIds.includes(current.tenantId)
    ) {
      return { ok: false, reason: "MODEL_NOT_AUTHORIZED" };
    }
    const revision = current.pendingRevision;
    const configuration = {
      ...current,
      status: "ACTIVE",
      activeRevision: revision.number,
      pendingRevision: null,
      parameters: clone(revision.parameters),
      authenticationState: "CONFIGURED",
      updatedAt: "刚刚",
    };
    const effects = [];
    let knowledgeBases = state.knowledgeBases;
    if (configuration.capability === "EMBEDDING" && revision.changedFields.some(field => ["embeddingDimension", "distanceMetric", "normalization"].includes(field))) {
      knowledgeBases = state.knowledgeBases.map(knowledgeBase => {
        if (knowledgeBase.tenantId !== configuration.tenantId) return knowledgeBase;
        const result = buildEmbeddingIndexCandidate(knowledgeBase, configuration, revision);
        if (result.effect) effects.push(result.effect);
        return result.knowledgeBase;
      });
    } else if (["CHAT", "RERANK"].includes(configuration.capability)) {
      effects.push({ type: "ASSISTANT_REEVALUATION_REQUIRED", capability: configuration.capability, configurationId });
    }
    const nextState = replaceModelItem("configurations", configuration);
    const saved = commitState({
      ...nextState,
      knowledgeBases,
      models: {
        ...nextState.models,
        effects: [...state.models.effects, ...effects],
      },
    });
    return saved.ok ? { ...saved, configuration: clone(configuration), effects: clone(effects) } : saved;
  }

  function createTenantModelDeployment(tenantId, input = {}, actorAccountId) {
    if (!canManageModels(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const configuration = modelItem("configurations", input.configurationId);
    if (!configuration || configuration.tenantId !== tenantId || configuration.status !== "ACTIVE") {
      return { ok: false, reason: "CONFIGURATION_NOT_ACTIVE" };
    }
    const name = typeof input.name === "string" ? input.name.trim() : "";
    if (name.length < 2 || name.length > 80) return { ok: false, reason: "DEPLOYMENT_NAME_INVALID" };
    const deployment = {
      id: `td-custom-${state.models.deployments.length + 1}`,
      tenantId,
      name,
      configurationId: configuration.id,
      capability: configuration.capability,
      status: "DRAFT",
      residency: "华东受控区",
      assistantIds: [],
      verifiedAt: null,
    };
    const saved = commitState({
      ...state,
      models: {
        ...state.models,
        deployments: [...state.models.deployments, deployment],
        selectedDeploymentId: deployment.id,
      },
    });
    return saved.ok ? { ...saved, deployment: clone(deployment) } : saved;
  }

  function verifyTenantModelDeployment(deploymentId, actorAccountId, options = {}) {
    const current = modelItem("deployments", deploymentId);
    if (!current) return { ok: false, reason: "DEPLOYMENT_NOT_FOUND" };
    if (!canManageModels(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (!["DRAFT", "FAILED"].includes(current.status)) return { ok: false, reason: "DEPLOYMENT_NOT_VERIFIABLE" };
    const deployment = {
      ...current,
      status: options.fail === true ? "FAILED" : "VERIFIED",
      verifiedAt: "刚刚",
      verificationResult: options.fail === true ? "安全样本失败，部署未发布。" : "能力、预算、驻留和安全样本通过。",
    };
    const saved = commitState(replaceModelItem("deployments", deployment));
    return saved.ok ? { ...saved, deployment: clone(deployment) } : saved;
  }

  function publishTenantModelDeployment(deploymentId, actorAccountId) {
    const current = modelItem("deployments", deploymentId);
    if (!current) return { ok: false, reason: "DEPLOYMENT_NOT_FOUND" };
    if (!canManageModels(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (current.status !== "VERIFIED") return { ok: false, reason: "DEPLOYMENT_NOT_VERIFIED" };
    const deployment = { ...current, status: "PUBLISHED", publishedAt: "刚刚" };
    const saved = commitState(replaceModelItem("deployments", deployment));
    return saved.ok ? { ...saved, deployment: clone(deployment) } : saved;
  }

  function deactivateTenantModelDeployment(deploymentId, acknowledged, actorAccountId) {
    const current = modelItem("deployments", deploymentId);
    if (!current) return { ok: false, reason: "DEPLOYMENT_NOT_FOUND" };
    if (!canManageModels(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (current.status !== "PUBLISHED") return { ok: false, reason: "DEPLOYMENT_NOT_PUBLISHED" };
    const routingPolicyIds = state.models.routingPolicies
      .filter(policy => policy.primaryDeploymentId === deploymentId || policy.fallbackDeploymentId === deploymentId)
      .map(policy => policy.id);
    if ((current.assistantIds.length || routingPolicyIds.length) && acknowledged !== true) {
      return {
        ok: false,
        reason: "IMPACT_ACK_REQUIRED",
        assistantIds: clone(current.assistantIds),
        routingPolicyIds: clone(routingPolicyIds),
      };
    }
    const deployment = { ...current, status: "OFFLINE", offlineAt: "刚刚" };
    const saved = commitState(replaceModelItem("deployments", deployment));
    return saved.ok ? { ...saved, deployment: clone(deployment) } : saved;
  }

  /** 建立路由草稿不等于发布；主备部署和预算仍须通过独立模拟校验。 */
  function createRoutingPolicy(tenantId, input = {}, actorAccountId) {
    if (!canManageModels(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const name = String(input.name || "").trim();
    if (name.length < 2 || name.length > 80) return { ok: false, reason: "ROUTING_POLICY_NAME_INVALID" };
    if (state.models.routingPolicies.some(item => item.tenantId === tenantId && item.name === name)) return { ok: false, reason: "ROUTING_POLICY_NAME_CONFLICT" };
    const policy = { id: `route-${state.models.routingPolicies.length + 1}`, tenantId, name, version: 1, status: "DRAFT", primaryDeploymentId: null, fallbackDeploymentId: null, monthlyBudget: 10000, simulation: null, updatedAt: "刚刚" };
    const saved = commitState({ ...state, models: { ...state.models, routingPolicies: [...state.models.routingPolicies, policy], selectedRoutingPolicyId: policy.id } });
    return saved.ok ? { ...saved, policy: clone(policy) } : saved;
  }

  function simulateRoutingPolicy(policyId, input = {}, actorAccountId) {
    const current = modelItem("routingPolicies", policyId);
    if (!current) return { ok: false, reason: "ROUTING_POLICY_NOT_FOUND" };
    if (!canManageModels(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const primary = modelItem("deployments", input.primaryDeploymentId);
    const fallback = modelItem("deployments", input.fallbackDeploymentId);
    if (!primary || !fallback || primary.status !== "PUBLISHED" || fallback.status !== "PUBLISHED") {
      return { ok: false, reason: "ROUTING_DEPLOYMENT_NOT_AVAILABLE" };
    }
    if (primary.tenantId !== current.tenantId || fallback.tenantId !== current.tenantId) {
      return { ok: false, reason: "ROUTING_DEPLOYMENT_NOT_AVAILABLE" };
    }
    if (primary.id === fallback.id) return { ok: false, reason: "ROUTING_FALLBACK_DUPLICATE" };
    if (primary.capability !== fallback.capability) return { ok: false, reason: "ROUTING_CAPABILITY_MISMATCH" };
    if (primary.residency !== fallback.residency) return { ok: false, reason: "ROUTING_RESIDENCY_MISMATCH" };
    const monthlyBudget = Number(input.monthlyBudget);
    if (!Number.isFinite(monthlyBudget) || monthlyBudget <= 0) return { ok: false, reason: "ROUTING_BUDGET_INVALID" };
    const policy = {
      ...current,
      primaryDeploymentId: primary.id,
      fallbackDeploymentId: fallback.id,
      monthlyBudget,
      status: "READY_FOR_RELEASE",
      simulation: { passed: 30, total: 30, estimatedBudget: Math.round(monthlyBudget * 0.72), p95Ms: 2180 },
      updatedAt: "刚刚",
    };
    const saved = commitState(replaceModelItem("routingPolicies", policy));
    return saved.ok ? { ...saved, policy: clone(policy) } : saved;
  }

  function publishRoutingPolicy(policyId, actorAccountId) {
    const current = modelItem("routingPolicies", policyId);
    if (!current) return { ok: false, reason: "ROUTING_POLICY_NOT_FOUND" };
    if (!canManageModels(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (current.status !== "READY_FOR_RELEASE" || current.simulation?.passed !== current.simulation?.total) {
      return { ok: false, reason: "ROUTING_SIMULATION_REQUIRED" };
    }
    const policy = { ...current, status: "PUBLISHED", publishedAt: "刚刚", updatedAt: "刚刚" };
    const saved = commitState(replaceModelItem("routingPolicies", policy));
    return saved.ok ? { ...saved, policy: clone(policy) } : saved;
  }

  /** 助手写操作必须同时满足租户、账户、管理员成员和助手交付功能上限。 */
  function canManageAssistants(tenantId, actorAccountId) {
    const tenant = tenantById(tenantId);
    const actor = accountById(actorAccountId);
    const member = tenant?.members.find(item => item.accountId === actorAccountId);
    return Boolean(
      tenant?.status === "active" &&
      actor?.status === "available" &&
      tenant.availableFeatures?.assistantDelivery !== false &&
      member?.role === "tenantAdministrator" &&
      member.status === "active",
    );
  }

  function assistantById(assistantId) {
    return state.assistantDelivery.assistants.find(item => item.id === assistantId);
  }

  function assistantVersionContext(versionId) {
    for (const assistant of state.assistantDelivery.assistants) {
      const version = assistant.versions.find(item => item.id === versionId);
      if (version) return { assistant, version };
    }
    return null;
  }

  function replaceAssistant(assistant, selection = {}) {
    return {
      ...state,
      assistantDelivery: {
        ...state.assistantDelivery,
        ...selection,
        assistants: state.assistantDelivery.assistants.map(item => item.id === assistant.id ? assistant : item),
      },
    };
  }

  function selectAssistantDeliveryItem(assistantId, versionId = null) {
    const assistant = assistantById(assistantId);
    if (!assistant) return { ok: false, reason: "ASSISTANT_NOT_FOUND" };
    const selectedVersionId = versionId || assistant.versions.at(-1)?.id || null;
    if (selectedVersionId && !assistant.versions.some(item => item.id === selectedVersionId)) {
      return { ok: false, reason: "ASSISTANT_VERSION_NOT_FOUND" };
    }
    return commitState({
      ...state,
      assistantDelivery: {
        ...state.assistantDelivery,
        selectedAssistantId: assistantId,
        selectedVersionId,
      },
    });
  }

  /** 稳定助手在创建时永久绑定一个已就绪知识库，后续版本不能替换该边界。 */
  function createAssistant(tenantId, input = {}, actorAccountId) {
    if (!canManageAssistants(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const name = String(input.name || "").trim();
    const summary = String(input.summary || "").trim();
    const knowledgeBase = knowledgeBaseById(input.knowledgeBaseId);
    const errors = [];
    if (name.length < 2 || name.length > 80) errors.push({ field: "name", message: "助手名称请输入 2 至 80 个字符。" });
    if (summary.length < 8 || summary.length > 200) errors.push({ field: "summary", message: "用途说明请输入 8 至 200 个字符。" });
    if (errors.length) return { ok: false, reason: "VALIDATION_FAILED", errors };
    if (state.assistantDelivery.assistants.some(item => item.tenantId === tenantId && item.name === name)) {
      return { ok: false, reason: "ASSISTANT_NAME_CONFLICT" };
    }
    const activeIndex = knowledgeBase?.indexVersions.find(item => item.id === knowledgeBase.activeIndex && item.status === "ACTIVE");
    if (knowledgeBase?.tenantId !== tenantId || !activeIndex) {
      return { ok: false, reason: "KNOWLEDGE_BASE_NOT_READY" };
    }
    const ordinal = state.assistantDelivery.assistants.length + 1;
    const assistant = {
      id: `assistant-custom-${ordinal}`,
      tenantId,
      name,
      summary,
      knowledgeBaseId: knowledgeBase.id,
      status: "DRAFT",
      publishedVersionId: null,
      versions: [{
        id: `asv-custom-${ordinal}-0001`,
        number: 1,
        status: "DRAFT",
        knowledgeBaseId: knowledgeBase.id,
        frozen: null,
      }],
    };
    const version = assistant.versions[0];
    const saved = commitState({
      ...state,
      assistantDelivery: {
        ...state.assistantDelivery,
        assistants: [...state.assistantDelivery.assistants, assistant],
        selectedAssistantId: assistant.id,
        selectedVersionId: version.id,
      },
    });
    return saved.ok ? { ...saved, assistant: clone(assistant), version: clone(version) } : saved;
  }

  /** 同一稳定助手只有一个未发布候选；重复点击继续现有候选，不制造并行分支。 */
  function createAssistantVersion(assistantId, actorAccountId) {
    const current = assistantById(assistantId);
    if (!current) return { ok: false, reason: "ASSISTANT_NOT_FOUND" };
    if (!canManageAssistants(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const existing = current.versions.find(item => !["PUBLISHED", "SUPERSEDED"].includes(item.status));
    if (existing) {
      const saved = selectAssistantDeliveryItem(current.id, existing.id);
      return saved.ok ? { ...saved, replayed: true, assistant: clone(current), version: clone(existing) } : saved;
    }
    const number = Math.max(0, ...current.versions.map(item => Number(item.number) || 0)) + 1;
    const version = {
      id: `${current.id}-v${String(number).padStart(4, "0")}`,
      number,
      status: "DRAFT",
      knowledgeBaseId: current.knowledgeBaseId,
      frozen: null,
    };
    const assistant = { ...current, versions: [...current.versions, version] };
    const saved = commitState(replaceAssistant(assistant, {
      selectedAssistantId: assistant.id,
      selectedVersionId: version.id,
    }));
    return saved.ok ? { ...saved, replayed: false, assistant: clone(assistant), version: clone(version) } : saved;
  }

  function saveAssistantDraft(versionId, input = {}, actorAccountId) {
    const context = assistantVersionContext(versionId);
    if (!context) return { ok: false, reason: "ASSISTANT_VERSION_NOT_FOUND" };
    const { assistant, version } = context;
    if (!canManageAssistants(assistant.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (version.status !== "DRAFT") return { ok: false, reason: "ASSISTANT_VERSION_NOT_DRAFT" };
    if (input.knowledgeBaseId !== assistant.knowledgeBaseId) {
      return { ok: false, reason: "ASSISTANT_KNOWLEDGE_BASE_IMMUTABLE" };
    }
    const knowledgeBase = knowledgeBaseById(assistant.knowledgeBaseId);
    const indexVersion = knowledgeBase?.indexVersions.find(item => item.id === input.indexVersionId);
    if (!indexVersion || ["CANCELLED", "BLOCKED", "BUILDING"].includes(indexVersion.status)) {
      return { ok: false, reason: "INDEX_VERSION_NOT_ELIGIBLE" };
    }
    const configurationKinds = [
      ["chat", input.chatConfigurationId, "CHAT"],
      ["embedding", input.embeddingConfigurationId, "EMBEDDING"],
      ["rerank", input.rerankConfigurationId, "RERANK"],
    ];
    const configurationRevisions = {};
    for (const [key, id, capability] of configurationKinds) {
      const configuration = modelItem("configurations", id);
      if (configuration?.tenantId !== assistant.tenantId || configuration.status !== "ACTIVE" || configuration.capability !== capability) {
        return { ok: false, reason: "MODEL_CONFIGURATION_NOT_ACTIVE" };
      }
      configurationRevisions[key] = { id: configuration.id, revision: configuration.activeRevision };
    }
    if (![input.retrievalPolicy, input.citationPolicy, input.refusalPolicy].every(value => String(value || "").trim())) {
      return { ok: false, reason: "ASSISTANT_POLICY_REQUIRED" };
    }
    const nextVersion = {
      ...version,
      status: "TESTING",
      frozen: {
        knowledgeBaseId: assistant.knowledgeBaseId,
        indexVersionId: indexVersion.id,
        configurationRevisions,
        retrievalPolicy: input.retrievalPolicy,
        citationPolicy: input.citationPolicy,
        refusalPolicy: input.refusalPolicy,
      },
      updatedAt: "刚刚",
    };
    const nextAssistant = {
      ...assistant,
      versions: assistant.versions.map(item => item.id === version.id ? nextVersion : item),
    };
    const saved = commitState(replaceAssistant(nextAssistant, {
      selectedAssistantId: assistant.id,
      selectedVersionId: nextVersion.id,
    }));
    return saved.ok ? { ...saved, assistant: clone(nextAssistant), version: clone(nextVersion) } : saved;
  }

  function runAssistantTest(versionId, rawQuestion, actorAccountId) {
    const context = assistantVersionContext(versionId);
    if (!context) return { ok: false, reason: "ASSISTANT_VERSION_NOT_FOUND" };
    const { assistant, version } = context;
    if (!canManageAssistants(assistant.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (!["TESTING", "TESTED"].includes(version.status) || !version.frozen) {
      return { ok: false, reason: "ASSISTANT_VERSION_NOT_TESTABLE" };
    }
    const question = String(rawQuestion || "").trim();
    if (!question) return { ok: false, reason: "QUESTION_REQUIRED" };
    const answered = /年假|假期|结转/.test(question);
    const testSequence = (version.testSequence || 0) + 1;
    const testResult = answered
      ? { ...answeredAssistantTest(false), id: `${version.id}-test-${testSequence}`, question, frozenIndexVersionId: version.frozen.indexVersionId }
      : {
          id: `${version.id}-test-${testSequence}`,
          question,
          status: "REFUSED",
          answer: "当前已授权知识中没有足够证据，已拒绝回答。",
          citations: [],
          frozenIndexVersionId: version.frozen.indexVersionId,
          addedToEvaluation: false,
        };
    const nextVersion = { ...version, status: "TESTED", testSequence, testResult, updatedAt: "刚刚" };
    const nextAssistant = { ...assistant, versions: assistant.versions.map(item => item.id === version.id ? nextVersion : item) };
    const saved = commitState(replaceAssistant(nextAssistant));
    return saved.ok ? { ...saved, version: clone(nextVersion), testResult: clone(testResult) } : saved;
  }

  function addAssistantTestToEvaluationSet(versionId, actorAccountId) {
    const context = assistantVersionContext(versionId);
    if (!context) return { ok: false, reason: "ASSISTANT_VERSION_NOT_FOUND" };
    const { assistant, version } = context;
    if (!canManageAssistants(assistant.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (version.status !== "TESTED" || !version.testResult) return { ok: false, reason: "ASSISTANT_TEST_REQUIRED" };
    const nextVersion = {
      ...version,
      status: "EVALUATION_PENDING",
      testResult: { ...version.testResult, addedToEvaluation: true },
      evaluationDataset: { id: "eval-set-v6", version: 6, questionCount: 101, frozenAt: "刚刚" },
      updatedAt: "刚刚",
    };
    const nextAssistant = { ...assistant, versions: assistant.versions.map(item => item.id === version.id ? nextVersion : item) };
    const saved = commitState(replaceAssistant(nextAssistant));
    return saved.ok ? { ...saved, version: clone(nextVersion) } : saved;
  }

  /** 评测同时校验质量、越权泄漏和首 token 时延；任一门禁失败都不能进入发布确认。 */
  function runAssistantEvaluation(versionId, actorAccountId, options = {}) {
    const context = assistantVersionContext(versionId);
    if (!context) return { ok: false, reason: "ASSISTANT_VERSION_NOT_FOUND" };
    const { assistant, version } = context;
    if (!canManageAssistants(assistant.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (!["EVALUATION_PENDING", "EVALUATED"].includes(version.status) || !version.evaluationDataset) {
      return { ok: false, reason: "EVALUATION_DATASET_REQUIRED" };
    }
    const evaluation = options.failSecurity
      ? {
          status: "FAILED",
          datasetVersion: version.evaluationDataset.version,
          evaluatedAt: "刚刚",
          metrics: { recallAt10: 0.86, citationPrecision: 0.91, refusalAccuracy: 0.96, unauthorizedLeakage: 1, firstTokenP95Ms: 3010 },
        }
      : { ...passedAssistantEvaluation(), datasetVersion: version.evaluationDataset.version, evaluatedAt: "刚刚" };
    const nextVersion = { ...version, status: "EVALUATED", evaluation, updatedAt: "刚刚" };
    const nextAssistant = { ...assistant, versions: assistant.versions.map(item => item.id === version.id ? nextVersion : item) };
    const saved = commitState(replaceAssistant(nextAssistant));
    return saved.ok ? { ...saved, version: clone(nextVersion), evaluation: clone(evaluation) } : saved;
  }

  function requestAssistantRelease(versionId, actorAccountId) {
    const context = assistantVersionContext(versionId);
    if (!context) return { ok: false, reason: "ASSISTANT_VERSION_NOT_FOUND" };
    const { assistant, version } = context;
    if (!canManageAssistants(assistant.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const metrics = version.evaluation?.metrics;
    const gatesPassed = version.status === "EVALUATED" && version.evaluation?.status === "PASSED" &&
      metrics?.recallAt10 >= 0.85 && metrics?.citationPrecision >= 0.95 &&
      metrics?.refusalAccuracy >= 0.98 && metrics?.unauthorizedLeakage === 0 &&
      metrics?.firstTokenP95Ms < 4000;
    if (!gatesPassed) return { ok: false, reason: "EVALUATION_GATE_FAILED" };
    const nextVersion = { ...version, status: "READY_FOR_RELEASE", releaseRequestedAt: "刚刚" };
    const nextAssistant = { ...assistant, versions: assistant.versions.map(item => item.id === version.id ? nextVersion : item) };
    const saved = commitState(replaceAssistant(nextAssistant));
    return saved.ok ? { ...saved, version: clone(nextVersion) } : saved;
  }

  /** 发布只推进稳定助手的当前发布指针；旧会话已冻结的版本和索引不随指针变化。 */
  function publishAssistantVersion(versionId, actorAccountId) {
    const context = assistantVersionContext(versionId);
    if (!context) return { ok: false, reason: "ASSISTANT_VERSION_NOT_FOUND" };
    const { assistant, version } = context;
    if (!canManageAssistants(assistant.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (version.status !== "READY_FOR_RELEASE") return { ok: false, reason: "ASSISTANT_VERSION_NOT_READY" };
    const nextVersions = assistant.versions.map(item => {
      if (item.id === version.id) return { ...item, status: "PUBLISHED", publishedAt: "刚刚" };
      if (item.id === assistant.publishedVersionId && item.status === "PUBLISHED") return { ...item, status: "SUPERSEDED" };
      return item;
    });
    const nextVersion = nextVersions.find(item => item.id === version.id);
    const nextAssistant = {
      ...assistant,
      status: "PUBLISHED",
      publishedVersionId: version.id,
      versions: nextVersions,
    };
    const effect = {
      id: `assistant-release-${state.assistantDelivery.effects.length + 1}`,
      assistantId: assistant.id,
      versionId: version.id,
      text: `v${version.number} 仅供新会话使用；已有会话继续固定原版本。`,
      createdAt: "刚刚",
    };
    const next = replaceAssistant(nextAssistant);
    next.assistantDelivery.effects = [...next.assistantDelivery.effects, effect];
    const saved = commitState(next);
    return saved.ok ? { ...saved, assistant: clone(nextAssistant), version: clone(nextVersion), effect: clone(effect) } : saved;
  }

  /** 评测治理写操作与助手交付分离授权，但同样受租户功能上限约束。 */
  function canManageEvaluations(tenantId, actorAccountId) {
    const tenant = tenantById(tenantId);
    const actor = accountById(actorAccountId);
    const member = tenant?.members.find(item => item.accountId === actorAccountId);
    return Boolean(
      tenant?.status === "active" &&
      actor?.status === "available" &&
      tenant.availableFeatures?.evaluationGovernance !== false &&
      member?.role === "tenantAdministrator" &&
      member.status === "active",
    );
  }

  function evaluationDatasetById(datasetId) {
    return state.evaluation.datasets.find(item => item.id === datasetId);
  }

  function evaluationVersionContext(versionId) {
    for (const dataset of state.evaluation.datasets) {
      const version = dataset.versions.find(item => item.id === versionId);
      if (version) return { dataset, version };
    }
    return null;
  }

  function evaluationRunById(runId) {
    return state.evaluation.runs.find(item => item.id === runId);
  }

  function replaceEvaluationDataset(dataset, selection = {}) {
    return {
      ...state,
      evaluation: {
        ...state.evaluation,
        ...selection,
        datasets: state.evaluation.datasets.map(item => item.id === dataset.id ? dataset : item),
      },
    };
  }

  function selectEvaluationDataset(datasetId, versionId = null) {
    const dataset = evaluationDatasetById(datasetId);
    if (!dataset) return { ok: false, reason: "EVALUATION_DATASET_NOT_FOUND" };
    const selectedVersionId = versionId || dataset.versions.at(-1)?.id || null;
    if (selectedVersionId && !dataset.versions.some(item => item.id === selectedVersionId)) {
      return { ok: false, reason: "EVALUATION_VERSION_NOT_FOUND" };
    }
    return commitState({
      ...state,
      evaluation: {
        ...state.evaluation,
        selectedDatasetId: dataset.id,
        selectedDatasetVersionId,
      },
    });
  }

  function selectEvaluationRun(runId) {
    if (!evaluationRunById(runId)) return { ok: false, reason: "EVALUATION_RUN_NOT_FOUND" };
    return commitState({ ...state, evaluation: { ...state.evaluation, selectedRunId: runId } });
  }

  function selectEvaluationComparison(comparisonId) {
    if (!state.evaluation.comparisons.some(item => item.id === comparisonId)) {
      return { ok: false, reason: "EVALUATION_COMPARISON_NOT_FOUND" };
    }
    return commitState({ ...state, evaluation: { ...state.evaluation, selectedComparisonId: comparisonId } });
  }

  function selectEvaluationFailure(runId, category) {
    const run = evaluationRunById(runId);
    if (!run?.failures.some(item => item.category === category)) {
      return { ok: false, reason: "EVALUATION_FAILURE_NOT_FOUND" };
    }
    return commitState({
      ...state,
      evaluation: {
        ...state.evaluation,
        selectedRunId: runId,
        selectedFailureCategory: category,
      },
    });
  }

  /** 新数据集可显式复制已发布基线，复制品始终成为独立草稿，不能冒充原版本。 */
  function createEvaluationDataset(tenantId, input = {}, actorAccountId) {
    if (!canManageEvaluations(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const name = String(input.name || "").trim();
    const knowledgeBase = knowledgeBaseById(input.knowledgeBaseId);
    if (name.length < 2 || name.length > 80) return { ok: false, reason: "EVALUATION_DATASET_NAME_INVALID" };
    if (state.evaluation.datasets.some(item => item.tenantId === tenantId && item.name === name)) {
      return { ok: false, reason: "EVALUATION_DATASET_NAME_CONFLICT" };
    }
    if (knowledgeBase?.tenantId !== tenantId) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    const template = input.templateVersionId ? evaluationVersionContext(input.templateVersionId) : null;
    if (input.templateVersionId && (template?.dataset.tenantId !== tenantId || template.version.status !== "PUBLISHED" || template.dataset.knowledgeBaseId !== knowledgeBase.id)) {
      return { ok: false, reason: "EVALUATION_TEMPLATE_NOT_AVAILABLE" };
    }
    const ordinal = state.evaluation.datasets.length + 1;
    const samples = clone(template?.version.samples || []);
    const version = {
      id: `eval-custom-${ordinal}-v1`,
      number: 1,
      status: "DRAFT",
      samples,
      composition: evaluationComposition(samples),
      sourceTemplateVersionId: template?.version.id || null,
      updatedAt: "刚刚",
    };
    const dataset = {
      id: `eval-custom-${ordinal}`,
      tenantId,
      name,
      knowledgeBaseId: knowledgeBase.id,
      status: "DRAFT",
      publishedVersionId: null,
      versions: [version],
    };
    const saved = commitState({
      ...state,
      evaluation: {
        ...state.evaluation,
        datasets: [...state.evaluation.datasets, dataset],
        selectedDatasetId: dataset.id,
        selectedDatasetVersionId: version.id,
      },
    });
    return saved.ok ? { ...saved, dataset: clone(dataset), version: clone(version) } : saved;
  }

  function createEvaluationDatasetVersion(datasetId, actorAccountId) {
    const current = evaluationDatasetById(datasetId);
    if (!current) return { ok: false, reason: "EVALUATION_DATASET_NOT_FOUND" };
    if (!canManageEvaluations(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const existing = current.versions.find(item => item.status === "DRAFT");
    if (existing) {
      const saved = selectEvaluationDataset(current.id, existing.id);
      return saved.ok ? { ...saved, replayed: true, dataset: clone(current), version: clone(existing) } : saved;
    }
    const source = current.versions.find(item => item.id === current.publishedVersionId);
    if (!source) return { ok: false, reason: "EVALUATION_PUBLISHED_BASELINE_REQUIRED" };
    const number = Math.max(...current.versions.map(item => item.number)) + 1;
    const samples = clone(source.samples);
    const version = {
      id: `${current.id}-v${number}`,
      number,
      status: "DRAFT",
      samples,
      composition: evaluationComposition(samples),
      sourceTemplateVersionId: source.id,
      updatedAt: "刚刚",
    };
    const dataset = { ...current, versions: [...current.versions, version] };
    const saved = commitState(replaceEvaluationDataset(dataset, {
      selectedDatasetId: dataset.id,
      selectedDatasetVersionId: version.id,
    }));
    return saved.ok ? { ...saved, replayed: false, dataset: clone(dataset), version: clone(version) } : saved;
  }

  function addEvaluationSample(versionId, input = {}, actorAccountId) {
    const context = evaluationVersionContext(versionId);
    if (!context) return { ok: false, reason: "EVALUATION_VERSION_NOT_FOUND" };
    const { dataset, version } = context;
    if (!canManageEvaluations(dataset.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (version.status !== "DRAFT") return { ok: false, reason: "EVALUATION_VERSION_IMMUTABLE" };
    const type = String(input.type || "");
    const question = String(input.question || "").trim();
    const expectedEvidence = Array.isArray(input.expectedEvidence)
      ? input.expectedEvidence.map(item => String(item).trim()).filter(Boolean)
      : String(input.expectedEvidence || "").split(/[，,\n]/).map(item => item.trim()).filter(Boolean);
    if (!EVALUATION_SAMPLE_TYPES.some(([value]) => value === type) || question.length < 4) {
      return { ok: false, reason: "EVALUATION_SAMPLE_INVALID" };
    }
    if (["ANSWERABLE", "MULTI_TURN"].includes(type) && expectedEvidence.length === 0) {
      return { ok: false, reason: "EXPECTED_EVIDENCE_REQUIRED" };
    }
    const sample = {
      id: `${version.id}-sample-${version.samples.length + 1}`,
      type,
      label: EVALUATION_SAMPLE_TYPES.find(([value]) => value === type)[1],
      questionSummary: question,
      expectedEvidence,
      expectedOutcome: ["ANSWERABLE", "MULTI_TURN"].includes(type) ? "ANSWER" : "REFUSE",
    };
    const samples = [...version.samples, sample];
    const nextVersion = { ...version, samples, composition: evaluationComposition(samples), updatedAt: "刚刚" };
    const nextDataset = { ...dataset, versions: dataset.versions.map(item => item.id === version.id ? nextVersion : item) };
    const saved = commitState(replaceEvaluationDataset(nextDataset));
    return saved.ok ? { ...saved, version: clone(nextVersion), sample: clone(sample) } : saved;
  }

  /** 发布校验四类冻结样本下限与期望证据，发布后版本不可变。 */
  function publishEvaluationDatasetVersion(versionId, actorAccountId) {
    const context = evaluationVersionContext(versionId);
    if (!context) return { ok: false, reason: "EVALUATION_VERSION_NOT_FOUND" };
    const { dataset, version } = context;
    if (!canManageEvaluations(dataset.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (version.status !== "DRAFT") return { ok: false, reason: "EVALUATION_VERSION_IMMUTABLE" };
    const composition = evaluationComposition(version.samples);
    const complete = version.samples.length >= 100 &&
      composition.answerable >= 60 && composition.refusal >= 20 &&
      composition.authorization >= 10 && composition.multiTurn >= 10 &&
      version.samples.filter(item => ["ANSWERABLE", "MULTI_TURN"].includes(item.type))
        .every(item => item.expectedEvidence.length > 0);
    if (!complete) return { ok: false, reason: "EVALUATION_DATASET_INCOMPLETE", composition };
    const nextVersion = { ...version, status: "PUBLISHED", composition, publishedAt: "刚刚", updatedAt: "刚刚" };
    const nextDataset = {
      ...dataset,
      status: "PUBLISHED",
      publishedVersionId: version.id,
      versions: dataset.versions.map(item => item.id === version.id ? nextVersion : item),
    };
    const saved = commitState(replaceEvaluationDataset(nextDataset));
    return saved.ok ? { ...saved, dataset: clone(nextDataset), version: clone(nextVersion) } : saved;
  }

  /** 运行固定助手、索引、模型配置修订与已发布数据集，运行中不读取活动指针。 */
  function createEvaluationRun(tenantId, input = {}, actorAccountId) {
    if (!canManageEvaluations(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const assistantContext = assistantVersionContext(input.assistantVersionId);
    if (assistantContext?.assistant.tenantId !== tenantId || !assistantContext.version.frozen) {
      return { ok: false, reason: "ASSISTANT_VERSION_NOT_FROZEN" };
    }
    const datasetContext = evaluationVersionContext(input.datasetVersionId);
    if (datasetContext?.dataset.tenantId !== tenantId || datasetContext.version.status !== "PUBLISHED") {
      return { ok: false, reason: "EVALUATION_DATASET_NOT_PUBLISHED" };
    }
    if (assistantContext.assistant.knowledgeBaseId !== datasetContext.dataset.knowledgeBaseId) {
      return { ok: false, reason: "EVALUATION_KNOWLEDGE_BASE_MISMATCH" };
    }
    const ordinal = state.evaluation.runs.length + 1;
    const run = {
      id: `eval-run-${ordinal}`,
      tenantId,
      name: `${assistantContext.assistant.name} v${assistantContext.version.number} / ${datasetContext.dataset.name} v${datasetContext.version.number}`,
      status: "RUNNING",
      gate: "PENDING",
      stageIndex: 0,
      stage: "RETRIEVING",
      progress: 10,
      frozen: {
        assistantId: assistantContext.assistant.id,
        assistantVersionId: assistantContext.version.id,
        datasetVersionId: datasetContext.version.id,
        indexVersionId: assistantContext.version.frozen.indexVersionId,
        configurationRevisions: clone(assistantContext.version.frozen.configurationRevisions),
      },
      metrics: null,
      failures: [],
      createdAt: "刚刚",
    };
    const saved = commitState({
      ...state,
      evaluation: {
        ...state.evaluation,
        runs: [...state.evaluation.runs, run],
        selectedRunId: run.id,
      },
    });
    return saved.ok ? { ...saved, run: clone(run) } : saved;
  }

  function advanceEvaluationRun(runId, actorAccountId) {
    const current = evaluationRunById(runId);
    if (!current) return { ok: false, reason: "EVALUATION_RUN_NOT_FOUND" };
    if (!canManageEvaluations(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (current.status !== "RUNNING") return { ok: false, reason: "EVALUATION_RUN_NOT_RUNNING" };
    const stages = ["RETRIEVING", "GENERATING", "SCORING", "FINALIZING"];
    const nextIndex = current.stageIndex + 1;
    const complete = nextIndex >= stages.length;
    const metrics = complete ? evaluationMetrics() : null;
    const run = complete
      ? { ...current, status: "COMPLETED", gate: "PASSED", stageIndex: nextIndex, stage: "COMPLETED", progress: 100, metrics, failures: evaluationFailures(), completedAt: "刚刚" }
      : { ...current, stageIndex: nextIndex, stage: stages[nextIndex], progress: [10, 35, 68, 90][nextIndex] };
    const saved = commitState({
      ...state,
      evaluation: {
        ...state.evaluation,
        runs: state.evaluation.runs.map(item => item.id === run.id ? run : item),
      },
    });
    return saved.ok ? { ...saved, run: clone(run) } : saved;
  }

  function createEvaluationComparison(baselineRunId, candidateRunId, actorAccountId) {
    const baseline = evaluationRunById(baselineRunId);
    const candidate = evaluationRunById(candidateRunId);
    if (!baseline || !candidate || baseline.status !== "COMPLETED" || candidate.status !== "COMPLETED") {
      return { ok: false, reason: "EVALUATION_RUN_NOT_COMPLETED" };
    }
    if (baseline.tenantId !== candidate.tenantId || !canManageEvaluations(baseline.tenantId, actorAccountId)) {
      return { ok: false, reason: "FORBIDDEN" };
    }
    if (baseline.frozen.datasetVersionId !== candidate.frozen.datasetVersionId) {
      return { ok: false, reason: "EVALUATION_DATASET_MISMATCH" };
    }
    if (baseline.frozen.assistantId !== candidate.frozen.assistantId) {
      return { ok: false, reason: "EVALUATION_ASSISTANT_MISMATCH" };
    }
    const noQualityRegression = candidate.metrics.recallAt10 >= baseline.metrics.recallAt10 &&
      candidate.metrics.citationPrecision >= baseline.metrics.citationPrecision &&
      candidate.metrics.refusalAccuracy >= baseline.metrics.refusalAccuracy &&
      candidate.metrics.retrievalP95Ms <= baseline.metrics.retrievalP95Ms &&
      candidate.metrics.firstTokenP95Ms <= baseline.metrics.firstTokenP95Ms &&
      candidate.metrics.consistency === "PASSED";
    const safeToApprove = candidate.gate === "PASSED" &&
      candidate.metrics.unauthorizedLeakage === 0 &&
      candidate.metrics.unauthorizedLeakage <= baseline.metrics.unauthorizedLeakage &&
      noQualityRegression;
    const comparison = {
      id: `evaluation-comparison-${state.evaluation.comparisons.length + 1}`,
      tenantId: baseline.tenantId,
      baselineRunId,
      candidateRunId,
      datasetVersionId: baseline.frozen.datasetVersionId,
      sameDataset: true,
      safeToApprove,
      noQualityRegression,
      decision: "PENDING",
      createdAt: "刚刚",
    };
    const saved = commitState({
      ...state,
      evaluation: {
        ...state.evaluation,
        comparisons: [...state.evaluation.comparisons, comparison],
        selectedComparisonId: comparison.id,
        selectedRunId: candidate.id,
      },
    });
    return saved.ok ? { ...saved, comparison: clone(comparison) } : saved;
  }

  function recordEvaluationDecision(comparisonId, action, actorAccountId) {
    const current = state.evaluation.comparisons.find(item => item.id === comparisonId);
    if (!current) return { ok: false, reason: "EVALUATION_COMPARISON_NOT_FOUND" };
    if (!canManageEvaluations(current.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (action === "APPROVE" && !current.safeToApprove) {
      return { ok: false, reason: "EVALUATION_REGRESSION_BLOCKED" };
    }
    const comparison = {
      ...current,
      decision: action === "APPROVE" ? "APPROVED" : "BLOCKED",
      decidedAt: "刚刚",
    };
    const saved = commitState({
      ...state,
      evaluation: {
        ...state.evaluation,
        comparisons: state.evaluation.comparisons.map(item => item.id === comparison.id ? comparison : item),
      },
    });
    return saved.ok ? { ...saved, comparison: clone(comparison) } : saved;
  }

  /** 修复事项只引用失败分类和数量，不复制评测问题、模型回答或引用正文。 */
  function createEvaluationRemediation(runId, category, input = {}, actorAccountId) {
    const run = evaluationRunById(runId);
    if (!run) return { ok: false, reason: "EVALUATION_RUN_NOT_FOUND" };
    if (!canManageEvaluations(run.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const failure = run.failures.find(item => item.category === category);
    if (!failure) return { ok: false, reason: "EVALUATION_FAILURE_NOT_FOUND" };
    const existing = state.evaluation.remediations.find(item => item.runId === runId && item.category === category && item.status === "OPEN");
    if (existing) return { ok: true, replayed: true, remediation: clone(existing) };
    const title = String(input.title || "").trim();
    const owner = String(input.owner || "").trim();
    if (title.length < 4 || title.length > 100 || owner.length < 2 || owner.length > 40) {
      return { ok: false, reason: "EVALUATION_REMEDIATION_INVALID" };
    }
    const remediation = {
      id: `evaluation-remediation-${state.evaluation.remediations.length + 1}`,
      tenantId: run.tenantId,
      runId,
      category,
      categoryLabel: failure.label,
      sampleCount: failure.sampleCount,
      title,
      owner,
      status: "OPEN",
      createdAt: "刚刚",
    };
    const saved = commitState({
      ...state,
      evaluation: {
        ...state.evaluation,
        remediations: [...state.evaluation.remediations, remediation],
        selectedRunId: runId,
        selectedFailureCategory: category,
      },
    });
    return saved.ok ? { ...saved, replayed: false, remediation: clone(remediation) } : saved;
  }

  function canManageAccessPolicies(tenantId, actorAccountId) {
    return canManageAssistants(tenantId, actorAccountId);
  }

  /** 新策略形成独立草稿，创建本身不授予成员访问权限。 */
  function createAccessPolicy(tenantId, input = {}, actorAccountId) {
    if (!canManageAccessPolicies(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const name = String(input.name || "").trim();
    if (name.length < 2 || name.length > 80) return { ok: false, reason: "ACCESS_POLICY_NAME_INVALID" };
    if (state.accessGovernance.accessPolicies.some(item => item.tenantId === tenantId && item.name === name)) return { ok: false, reason: "ACCESS_POLICY_NAME_CONFLICT" };
    const assistant = assistantById(input.assistantId);
    if (!assistant || assistant.tenantId !== tenantId || !assistant.publishedVersionId) return { ok: false, reason: "ACCESS_POLICY_ASSISTANT_INVALID" };
    const memberAccountIds = [...new Set(input.memberAccountIds || [])];
    if (!memberAccountIds.length || memberAccountIds.some(id => memberById(tenantId, id)?.role !== "ordinaryMember" || memberById(tenantId, id)?.status !== "active" || accountById(id)?.status !== "available")) return { ok: false, reason: "ACCESS_POLICY_MEMBER_INVALID" };
    const id = `access-policy-${state.accessGovernance.accessPolicies.length + 1}`;
    const version = { id: `${id}-v1`, number: 1, status: "DRAFT", assistantId: assistant.id, knowledgeBaseId: assistant.knowledgeBaseId, memberAccountIds, preview: null, updatedAt: "刚刚" };
    const policy = { id, tenantId, name, publishedVersionId: null, versions: [version] };
    const saved = commitState({ ...state, accessGovernance: { ...state.accessGovernance,
      accessPolicies: [...state.accessGovernance.accessPolicies, policy], selectedPolicyId: id, selectedPolicyVersionId: version.id,
    } });
    return saved.ok ? { ...saved, policy: clone(policy) } : saved;
  }

  function selectAccessPolicy(policyId, actorAccountId) {
    const policy = state.accessGovernance.accessPolicies.find(item => item.id === policyId);
    if (!policy || !canManageAccessPolicies(policy.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    state.accessGovernance.selectedPolicyId = policy.id;
    state.accessGovernance.selectedPolicyVersionId = policy.versions.at(-1).id;
    return { ok: true };
  }

  /** 调整已发布策略时复用唯一草稿；只有再次预览并发布才改变成员权限。 */
  function createAccessPolicyVersion(policyId, actorAccountId) {
    const policy = state.accessGovernance.accessPolicies.find(item => item.id === policyId);
    if (!policy || !canManageAccessPolicies(policy.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const existing = policy.versions.find(item => item.status === "DRAFT");
    const published = policy.versions.find(item => item.id === policy.publishedVersionId);
    if (!existing && !published) return { ok: false, reason: "ACCESS_POLICY_VERSION_NOT_FOUND" };
    const number = Math.max(...policy.versions.map(item => item.number)) + 1;
    const version = existing || { ...published, id: `${policy.id}-v${number}`, number, status: "DRAFT", preview: null, updatedAt: "刚刚" };
    const saved = commitState(replaceAccessPolicy({ ...policy, versions: existing ? policy.versions : [...policy.versions, version] }, { selectedPolicyId: policyId, selectedPolicyVersionId: version.id }));
    return saved.ok ? { ...saved, version: clone(version), replayed: Boolean(existing) } : saved;
  }

  function accessPolicyVersionContext(versionId) {
    for (const policy of state.accessGovernance.accessPolicies) {
      const version = policy.versions.find(item => item.id === versionId);
      if (version) return { policy, version };
    }
    return null;
  }

  function replaceAccessPolicy(policy, selection = {}) {
    return {
      ...state,
      accessGovernance: {
        ...state.accessGovernance,
        ...selection,
        accessPolicies: state.accessGovernance.accessPolicies.map(item => item.id === policy.id ? policy : item),
      },
    };
  }

  /** 访问策略不单独选择知识库，防止策略范围与助手的永久知识库关系发生错配。 */
  function saveAccessPolicyDraft(versionId, input = {}, actorAccountId) {
    const context = accessPolicyVersionContext(versionId);
    if (!context) return { ok: false, reason: "ACCESS_POLICY_VERSION_NOT_FOUND" };
    const { policy, version } = context;
    if (!canManageAccessPolicies(policy.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (version.status !== "DRAFT") return { ok: false, reason: "ACCESS_POLICY_VERSION_IMMUTABLE" };
    const assistant = assistantById(String(input.assistantId || ""));
    const publishedVersion = assistant?.versions.find(item => item.id === assistant.publishedVersionId && item.status === "PUBLISHED");
    if (!assistant || assistant.tenantId !== policy.tenantId || !publishedVersion) {
      return { ok: false, reason: "ACCESS_POLICY_ASSISTANT_INVALID" };
    }
    const memberAccountIds = [...new Set(Array.isArray(input.memberAccountIds) ? input.memberAccountIds.map(String) : [])];
    const membersValid = memberAccountIds.length > 0 && memberAccountIds.every(accountId => {
      const member = memberById(policy.tenantId, accountId);
      const account = accountById(accountId);
      return member?.role === "ordinaryMember" && member.status === "active" && account?.status === "available";
    });
    if (!membersValid) return { ok: false, reason: "ACCESS_POLICY_MEMBER_INVALID" };
    const nextVersion = {
      ...version,
      assistantId: assistant.id,
      knowledgeBaseId: assistant.knowledgeBaseId,
      memberAccountIds,
      preview: null,
      updatedAt: "刚刚",
    };
    const nextPolicy = { ...policy, versions: policy.versions.map(item => item.id === version.id ? nextVersion : item) };
    const saved = commitState(replaceAccessPolicy(nextPolicy, {
      selectedPolicyId: policy.id,
      selectedPolicyVersionId: version.id,
    }));
    return saved.ok ? { ...saved, version: clone(nextVersion) } : saved;
  }

  function previewAccessPolicyVersion(versionId, actorAccountId) {
    const context = accessPolicyVersionContext(versionId);
    if (!context) return { ok: false, reason: "ACCESS_POLICY_VERSION_NOT_FOUND" };
    const { policy, version } = context;
    if (!canManageAccessPolicies(policy.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (version.status !== "DRAFT") return { ok: false, reason: "ACCESS_POLICY_VERSION_IMMUTABLE" };
    const preview = {
      passed: true,
      allowCount: 24,
      denyCount: 8,
      checks: [
        "已选活动普通成员可以看到并使用助手",
        "未选成员不能通过目录或直接路由使用助手",
        "引用访问沿用同一助手授权范围",
        "权限撤销后历史和引用立即失败关闭",
      ],
      generatedAt: "刚刚",
    };
    const nextVersion = { ...version, preview, updatedAt: "刚刚" };
    const nextPolicy = { ...policy, versions: policy.versions.map(item => item.id === version.id ? nextVersion : item) };
    const saved = commitState(replaceAccessPolicy(nextPolicy));
    return saved.ok ? { ...saved, version: clone(nextVersion) } : saved;
  }

  /** 发布后以新版本替换同一助手的成员范围；移除授权同样即时作用于历史和引用入口。 */
  function publishAccessPolicyVersion(versionId, actorAccountId) {
    const context = accessPolicyVersionContext(versionId);
    if (!context) return { ok: false, reason: "ACCESS_POLICY_VERSION_NOT_FOUND" };
    const { policy, version } = context;
    if (!canManageAccessPolicies(policy.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (version.status !== "DRAFT") return { ok: false, reason: "ACCESS_POLICY_VERSION_IMMUTABLE" };
    if (!version.preview?.passed) return { ok: false, reason: "ACCESS_POLICY_PREVIEW_REQUIRED" };
    const previousPublished = policy.versions.find(item => item.id === policy.publishedVersionId);
    const versions = policy.versions.map(item => {
      if (item.id === version.id) return { ...item, status: "PUBLISHED", publishedAt: "刚刚" };
      if (item.id === policy.publishedVersionId && item.status === "PUBLISHED") return { ...item, status: "SUPERSEDED" };
      return item;
    });
    const nextVersion = versions.find(item => item.id === version.id);
    const nextPolicy = { ...policy, publishedVersionId: version.id, versions };
    const next = replaceAccessPolicy(nextPolicy);
    next.tenants = next.tenants.map(tenant => tenant.id !== policy.tenantId ? tenant : {
      ...tenant,
      members: tenant.members.map(member => member.role !== "ordinaryMember" ? member : {
        ...member,
        assistantIds: version.memberAccountIds.includes(member.accountId)
          ? [...new Set([
              ...member.assistantIds.filter(assistantId => assistantId !== previousPublished?.assistantId),
              version.assistantId,
            ])]
          : member.assistantIds.filter(assistantId =>
              assistantId !== version.assistantId && assistantId !== previousPublished?.assistantId),
      }),
    });
    const saved = commitState(next);
    return saved.ok ? { ...saved, policy: clone(nextPolicy), version: clone(nextVersion) } : saved;
  }

  function getAccessPolicyProjection(policyId, actorAccountId) {
    const policy = state.accessGovernance.accessPolicies.find(item => item.id === policyId);
    if (!policy) return { ok: false, reason: "ACCESS_POLICY_NOT_FOUND" };
    if (!canManageAccessPolicies(policy.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const version = policy.versions.find(item => item.id === state.accessGovernance.selectedPolicyVersionId) || policy.versions.at(-1);
    const assistant = assistantById(version.assistantId);
    const knowledgeBase = knowledgeBaseById(version.knowledgeBaseId);
    const members = version.memberAccountIds.map(accountId => {
      const account = accountById(accountId);
      return { accountId, name: account?.name || "不可用成员" };
    });
    return {
      ok: true,
      policy: { id: policy.id, name: policy.name, publishedVersionId: policy.publishedVersionId },
      version: clone(version),
      assistant: { id: assistant.id, name: assistant.name, publishedVersionId: assistant.publishedVersionId },
      knowledgeBase: { id: knowledgeBase.id, name: knowledgeBase.name },
      members,
    };
  }

  /** 查询治理目录只投影配额与脱敏终态摘要，模型修订只在单次受权诊断内披露。 */
  function getQueryGovernanceProjection(tenantId, actorAccountId) {
    if (!canManageEvaluations(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    return {
      ok: true,
      quota: clone(state.accessGovernance.queryQuota),
      usage: clone(tenantId === DEFAULT_TENANT_ID ? state.accessGovernance.queryUsage : { acceptedToday: 0, remainingToday: state.accessGovernance.queryQuota.dailyAcceptedLimit, activeNow: 0, refusedToday: 0 }),
      runs: state.accessGovernance.queryRuns
        .filter(item => item.tenantId === tenantId)
        .map(({ id, reference, assistantName, outcome, durationMs, occurredAt }) => ({ id, reference, assistantName, outcome, durationMs, occurredAt })),
    };
  }

  /** 审计读取和导出复用同一白名单投影，不包含凭据、问题、回答或原始异常。 */
  function getAuditProjection(tenantId, actorAccountId, filters = {}) {
    if (!canManageEvaluations(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const query = String(filters.query || "").trim().toLocaleLowerCase("zh-CN");
    const records = (state.auditRecords || []).filter(item => item.tenantId === tenantId &&
      (!filters.outcome || item.outcome === filters.outcome) && `${item.event} ${item.scope}`.toLocaleLowerCase("zh-CN").includes(query))
      .map(({ id, event, scope, outcome, occurredAt, actorRole, summary }) => ({ id, event, scope, outcome, occurredAt, actorRole, summary }));
    return { ok: true, records };
  }

  function selectQueryDiagnostic(queryRunId, actorAccountId) {
    const run = state.accessGovernance.queryRuns.find(item => item.id === queryRunId);
    if (!run || !canManageEvaluations(run.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    return commitState({
      ...state,
      accessGovernance: { ...state.accessGovernance, selectedQueryRunId: queryRunId },
    });
  }

  /** 诊断读取固定终态事实，不重新运行检索，也不从当前配置推断历史结果。 */
  function getQueryDiagnostic(queryRunId, actorAccountId) {
    const run = state.accessGovernance.queryRuns.find(item => item.id === queryRunId);
    if (!run) return { ok: false, reason: "QUERY_DIAGNOSTIC_NOT_FOUND" };
    if (!canManageEvaluations(run.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    return {
      ok: true,
      run: {
        id: run.id,
        reference: run.reference,
        assistantName: run.assistantName,
        outcome: run.outcome,
        durationMs: run.durationMs,
        occurredAt: run.occurredAt,
      },
      diagnostic: clone(run.diagnostic),
      remediation: clone(state.accessGovernance.queryRemediations.find(item => item.queryRunId === run.id && item.status === "OPEN") || null),
    };
  }

  /** 修复事项只复制允许披露的阶段、分类和数量，不复制问题、回答、引用或供应商错误。 */
  function createQueryRemediation(queryRunId, input = {}, actorAccountId) {
    const run = state.accessGovernance.queryRuns.find(item => item.id === queryRunId);
    if (!run) return { ok: false, reason: "QUERY_DIAGNOSTIC_NOT_FOUND" };
    if (!canManageEvaluations(run.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const category = String(input.category || "");
    if (![
      "AUTHORIZED_EVIDENCE_EMPTY",
      "RETRIEVAL_RUNTIME_FAILURE",
      "CITATION_VALIDATION_FAILURE",
    ].includes(category)) return { ok: false, reason: "QUERY_REMEDIATION_CATEGORY_INVALID" };
    const existing = state.accessGovernance.queryRemediations.find(item => item.queryRunId === queryRunId && item.category === category && item.status === "OPEN");
    if (existing) return { ok: true, replayed: true, remediation: clone(existing) };
    const title = String(input.title || "").trim();
    const owner = String(input.owner || "").trim();
    if (title.length < 4 || title.length > 100 || owner.length < 2 || owner.length > 40) {
      return { ok: false, reason: "QUERY_REMEDIATION_INVALID" };
    }
    const remediation = {
      id: `query-remediation-${state.accessGovernance.queryRemediations.length + 1}`,
      queryRunId,
      category,
      stage: run.diagnostic.stage,
      candidateCount: run.diagnostic.candidateCount,
      title,
      owner,
      status: "OPEN",
      createdAt: "刚刚",
    };
    const saved = commitState({
      ...state,
      accessGovernance: {
        ...state.accessGovernance,
        queryRemediations: [...state.accessGovernance.queryRemediations, remediation],
        selectedQueryRunId: queryRunId,
      },
    });
    return saved.ok ? { ...saved, replayed: false, remediation: clone(remediation) } : saved;
  }

  function structuredSchemaContext(versionId) {
    for (const dataset of state.dataGovernance.structuredDatasets) {
      const version = dataset.versions.find(item => item.id === versionId);
      if (version) return { dataset, version };
    }
    return null;
  }

  function structuredSchemaFailure(dataset, input = {}) {
    const headers = Array.isArray(input.headers) ? input.headers.map(String) : [];
    if (JSON.stringify(headers) !== JSON.stringify(dataset.headers)) return "DATASET_HEADERS_MISMATCH";
    const fields = Array.isArray(input.fields) ? input.fields : [];
    if (fields.length !== headers.length || fields.some((field, index) => String(field?.name || "") !== headers[index])) {
      return "DATASET_FIELDS_MISMATCH";
    }
    if (new Set(fields.map(field => field.name)).size !== fields.length) return "DATASET_FIELDS_MISMATCH";
    const logicalTypes = new Set(["TEXT", "INTEGER", "DECIMAL", "DATE"]);
    const sensitivities = new Set(["PUBLIC", "INTERNAL", "RESTRICTED"]);
    if (fields.some(field =>
      !logicalTypes.has(field.logicalType) ||
      !sensitivities.has(field.sensitivity) ||
      typeof field.queryEligible !== "boolean" ||
      !String(field.semantic || "").trim()
    )) return "DATASET_FIELD_INVALID";
    const contentField = fields.find(field => field.name === input.contentField);
    if (!contentField || contentField.logicalType !== "TEXT" || contentField.sensitivity !== "PUBLIC" || !contentField.queryEligible) {
      return "DATASET_CONTENT_FIELD_UNSAFE";
    }
    if (input.titleField) {
      const titleField = fields.find(field => field.name === input.titleField);
      if (!titleField || titleField.logicalType !== "TEXT" || titleField.sensitivity !== "PUBLIC" || !titleField.queryEligible) {
        return "DATASET_TITLE_FIELD_UNSAFE";
      }
    }
    if (fields.some(field => field.queryEligible && field.sensitivity !== "PUBLIC")) {
      return "DATASET_QUERY_FIELD_UNSAFE";
    }
    if (!["ALL_ROWS", "NON_BLANK_CONTENT"].includes(input.rowFilter)) return "DATASET_ROW_FILTER_INVALID";
    return null;
  }

  function replaceStructuredDataset(dataset) {
    return {
      ...state,
      dataGovernance: {
        ...state.dataGovernance,
        selectedDatasetId: dataset.id,
        structuredDatasets: state.dataGovernance.structuredDatasets.map(item => item.id === dataset.id ? dataset : item),
      },
    };
  }

  /** 结构读取投影只包含字段规则和 CSV 标题，不投影样本行或字段值。 */
  function getStructuredDatasetProjection(datasetId, actorAccountId) {
    const dataset = state.dataGovernance.structuredDatasets.find(item => item.id === datasetId);
    if (!dataset) return { ok: false, reason: "STRUCTURED_DATASET_NOT_FOUND" };
    if (!canManageKnowledge(dataset.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const version = dataset.versions.find(item => item.id === dataset.selectedVersionId) || dataset.versions.at(-1);
    return {
      ok: true,
      dataset: {
        id: dataset.id,
        name: dataset.name,
        knowledgeBaseId: dataset.knowledgeBaseId,
        sourceRevision: dataset.sourceRevision,
        sourceName: dataset.sourceName,
        headers: clone(dataset.headers),
        publishedVersionId: dataset.publishedVersionId,
      },
      version: clone(version),
    };
  }

  function saveStructuredSchemaDraft(versionId, input = {}, actorAccountId) {
    const context = structuredSchemaContext(versionId);
    if (!context) return { ok: false, reason: "DATASET_SCHEMA_NOT_FOUND" };
    const { dataset, version } = context;
    if (!canManageKnowledge(dataset.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (version.status !== "DRAFT") return { ok: false, reason: "DATASET_SCHEMA_IMMUTABLE" };
    const failure = structuredSchemaFailure(dataset, input);
    if (failure) return { ok: false, reason: failure };
    const nextVersion = {
      ...version,
      headers: dataset.headers.slice(),
      fields: input.fields.map(field => ({
        name: String(field.name),
        logicalType: field.logicalType,
        semantic: String(field.semantic).trim(),
        queryEligible: field.queryEligible,
        sensitivity: field.sensitivity,
      })),
      contentField: input.contentField,
      titleField: input.titleField || null,
      rowFilter: input.rowFilter,
      qualityRuleVersion: "dataset-csv-v1",
      preview: null,
      updatedAt: "刚刚",
    };
    const nextDataset = {
      ...dataset,
      versions: dataset.versions.map(item => item.id === version.id ? nextVersion : item),
    };
    const saved = commitState(replaceStructuredDataset(nextDataset));
    return saved.ok ? { ...saved, version: clone(nextVersion) } : saved;
  }

  function previewStructuredSchemaVersion(versionId, actorAccountId) {
    const context = structuredSchemaContext(versionId);
    if (!context) return { ok: false, reason: "DATASET_SCHEMA_NOT_FOUND" };
    const { dataset, version } = context;
    if (!canManageKnowledge(dataset.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (version.status !== "DRAFT") return { ok: false, reason: "DATASET_SCHEMA_IMMUTABLE" };
    const failure = structuredSchemaFailure(dataset, version);
    if (failure) return { ok: false, reason: failure };
    const nextVersion = {
      ...version,
      preview: {
        passed: true,
        checkedRows: 100,
        blockedRows: 0,
        skippedRows: version.rowFilter === "NON_BLANK_CONTENT" ? 2 : 0,
        headerMatched: true,
        generatedAt: "刚刚",
      },
      updatedAt: "刚刚",
    };
    const nextDataset = {
      ...dataset,
      versions: dataset.versions.map(item => item.id === version.id ? nextVersion : item),
    };
    const saved = commitState(replaceStructuredDataset(nextDataset));
    return saved.ok ? { ...saved, version: clone(nextVersion) } : saved;
  }

  function publishStructuredSchemaVersion(versionId, actorAccountId) {
    const context = structuredSchemaContext(versionId);
    if (!context) return { ok: false, reason: "DATASET_SCHEMA_NOT_FOUND" };
    const { dataset, version } = context;
    if (!canManageKnowledge(dataset.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    if (version.status !== "DRAFT") return { ok: false, reason: "DATASET_SCHEMA_IMMUTABLE" };
    if (!version.preview?.passed) return { ok: false, reason: "DATASET_PREVIEW_REQUIRED" };
    const versions = dataset.versions.map(item => {
      if (item.id === version.id) return { ...item, status: "PUBLISHED", publishedAt: "刚刚" };
      if (item.id === dataset.publishedVersionId && item.status === "PUBLISHED") return { ...item, status: "SUPERSEDED" };
      return item;
    });
    const nextVersion = versions.find(item => item.id === version.id);
    const nextDataset = { ...dataset, versions, publishedVersionId: version.id, selectedVersionId: version.id };
    const saved = commitState(replaceStructuredDataset(nextDataset));
    return saved.ok ? { ...saved, version: clone(nextVersion) } : saved;
  }

  /** 对象范围只投影签发标签与冻结元数据；基础设施定位和凭据始终不可见。 */
  function getObjectStorageScopeProjection(tenantId, actorAccountId) {
    if (!canManageKnowledge(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const availableRanges = state.dataGovernance.platformObjectRanges
      .filter(item => item.tenantId === tenantId && item.status === "AVAILABLE")
      .map(({ id, label, region, selectionRevision, objectCount, mediaType }) => ({
        id,
        label,
        region,
        selectionRevision,
        objectCount,
        mediaType,
      }));
    const registrations = state.dataGovernance.objectScopeRegistrations
      .filter(item => item.tenantId === tenantId)
      .map(item => ({
        ...clone(item),
        knowledgeBaseName: knowledgeBaseById(item.knowledgeBaseId)?.name || "知识库不可用",
      }));
    return { ok: true, availableRanges, registrations };
  }

  /** 登记只接受签发范围标识和修订，客户端不能提交桶、对象键、端点或凭据。 */
  function registerObjectStorageScope(input = {}, actorAccountId) {
    const tenantId = String(input.tenantId || "");
    if (!canManageKnowledge(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const knowledgeBase = knowledgeBaseById(String(input.knowledgeBaseId || ""));
    if (!knowledgeBase || knowledgeBase.tenantId !== tenantId) return { ok: false, reason: "KNOWLEDGE_BASE_NOT_FOUND" };
    const range = state.dataGovernance.platformObjectRanges.find(item =>
      item.id === String(input.rangeId || "") && item.tenantId === tenantId && item.status === "AVAILABLE",
    );
    if (!range) return { ok: false, reason: "OBJECT_RANGE_NOT_FOUND" };
    if (Number(input.selectionRevision) !== range.selectionRevision) return { ok: false, reason: "OBJECT_RANGE_REVISION_MISMATCH" };
    if (range.mediaType !== "text/plain") return { ok: false, reason: "OBJECT_RANGE_MEDIA_TYPE_UNSUPPORTED" };
    const name = String(input.name || "").trim();
    if (name.length < 2 || name.length > 80) return { ok: false, reason: "OBJECT_RANGE_NAME_INVALID" };
    const existing = state.dataGovernance.objectScopeRegistrations.find(item =>
      item.tenantId === tenantId &&
      item.knowledgeBaseId === knowledgeBase.id &&
      item.rangeId === range.id &&
      item.selectionRevision === range.selectionRevision,
    );
    if (existing) {
      if (existing.name !== name) return { ok: false, reason: "OBJECT_RANGE_ALREADY_REGISTERED" };
      return { ok: true, replayed: true, registration: clone(existing) };
    }
    if (knowledgeBase.documents + knowledgeBase.sources.filter(item => item.reservedDocument).length >= tenantById(tenantId).quotas.documentsPerKnowledgeBase) {
      return { ok: false, reason: "DOCUMENT_QUOTA_EXCEEDED" };
    }
    const registration = {
      id: `object-source-${state.dataGovernance.objectScopeRegistrations.length + 1}`,
      tenantId,
      knowledgeBaseId: knowledgeBase.id,
      rangeId: range.id,
      rangeLabel: range.label,
      selectionRevision: range.selectionRevision,
      region: range.region,
      objectCount: range.objectCount,
      mediaType: range.mediaType,
      name,
      status: "DRAFT",
      createdAt: "刚刚",
    };
    const saved = commitState({
      ...state,
      knowledgeBases: state.knowledgeBases.map(item => item.id !== knowledgeBase.id ? item : {
        ...item, status: "PROCESSING", sources: [...item.sources, {
          id: registration.id, name, kind: "对象存储", revision: "rev-0001", activeRevision: null,
          stage: "RECEIVED", status: "PROCESSING", indexed: false, reservedDocument: true,
          rawContentStored: false, sizeMb: 0, error: null, rangeRevision: range.selectionRevision,
          coverage: `${range.objectCount} 个冻结文本对象`, detail: "待探测，尚未读取对象内容。",
        }],
      }),
      dataGovernance: {
        ...state.dataGovernance,
        objectScopeRegistrations: [...state.dataGovernance.objectScopeRegistrations, registration],
      },
    });
    return saved.ok ? { ...saved, replayed: false, registration: clone(registration) } : saved;
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
      id: `kb-custom-${state.knowledgeBases.length + 1}`,
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

  /** 替换内容保持来源身份；旧修订和活动索引不原地修改，不保存输入正文。 */
  function replaceKnowledgeSource(knowledgeBaseId, sourceId, input = {}, actorAccountId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase || !canManageKnowledge(knowledgeBase.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const current = knowledgeBase.sources.find(item => item.id === sourceId);
    if (!current || !["READY", "FAILED"].includes(current.stage)) return { ok: false, reason: "SOURCE_NOT_REPLACEABLE" };
    const failure = sourceInputFailure(knowledgeBase, input);
    if (failure) return { ok: false, reason: failure };
    const sizeMb = input.mode === "paste" ? new TextEncoder().encode(input.text.trim()).byteLength / 1024 / 1024 : input.sizeMb;
    const storageGb = knowledgeBase.storageGb + sizeMb / 1024;
    if (storageGb > tenantById(knowledgeBase.tenantId).quotas.rawContentGbPerKnowledgeBase) return { ok: false, reason: "STORAGE_QUOTA_EXCEEDED" };
    const source = { ...current, name: String(input.name).trim(), kind: input.mode === "paste" ? "粘贴文本" : FILE_TYPES[String(input.name).split(".").at(-1).toLowerCase()].kind,
      revision: `rev-${String(Number(current.revision.replace("rev-", "")) + 1).padStart(4, "0")}`,
      previousRevisions: [...(current.previousRevisions || []), { revision: current.revision, name: current.name, sizeMb: current.sizeMb, extractedText: current.extractedText }],
      sizeMb, stage: "RECEIVED", status: "PROCESSING", indexed: false, rawContentStored: false,
      simulatedFailure: input.simulatedFailure || null, error: null, detail: "新版本已接收，旧活动索引继续服务。",
    };
    const next = { ...knowledgeBase, storageGb, status: "PROCESSING", sources: knowledgeBase.sources.map(item => item.id === sourceId ? source : item) };
    const saved = commitState({ ...replaceKnowledgeBase(next), selectedKnowledgeSourceId: sourceId });
    return saved.ok ? { ...saved, source: clone(source) } : saved;
  }

  function updateKnowledgeSource(knowledgeBase, source) {
    const previous = knowledgeBase.sources.find(item => item.id === source.id);
    // 合成操作记录只在状态实际推进时追加，不虚构后台时间或真实日志。
    source = { ...source, processingRecords: [...(previous?.processingRecords || []), { sequence: (previous?.processingRecords?.at(-1)?.sequence || 0) + 1, stage: source.stage, detail: source.stage === 'FAILED' ? '合成处理失败，当前活动索引未改变。' : '已完成本次合成状态更新。' }] };
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
      detail: ready ? "处理完成，已进入待激活索引清单。" : `正在${({SCANNING:'安全检查',EXTRACTING:'提取内容',EXTRACTED:'核对提取结果',CHUNKING:'内容分段',INDEXING:'建立索引'})[nextStage] || '处理资料'}。`,
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

  /** 批量安全重试在一个快照中提交；任一持久化失败不留下半批结果。 */
  function retryFailedKnowledgeSources(knowledgeBaseId, actorAccountId) {
    const knowledgeBase = knowledgeBaseById(knowledgeBaseId);
    if (!knowledgeBase || !canManageKnowledge(knowledgeBase.tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const count = knowledgeBase.sources.filter(item => item.stage === "FAILED").length;
    if (!count) return { ok: false, reason: "NO_FAILED_SOURCES" };
    const next = { ...knowledgeBase, status: "PROCESSING", sources: knowledgeBase.sources.map(item => item.stage !== "FAILED" ? item : {
      ...item, stage: item.retryStage || "SCANNING", status: "PROCESSING", error: null, simulatedFailure: null,
      detail: "已从安全检查点重试，活动索引不变。",
    }) };
    const saved = commitState(replaceKnowledgeBase(next));
    return saved.ok ? { ...saved, count } : saved;
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
    const source = input.sourceId ? knowledgeBase.sources.find(item => item.id === input.sourceId) : null;
    if (input.sourceId && source?.stage !== "READY") return { ok: false, reason: "SOURCE_NOT_READY" };
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
      ...(source ? { sourceId: source.id, sourceRevision: source.revision, reusedFromIndexId: knowledgeBase.activeIndex } : {}),
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
    if (!demoLoginScenario(password)) {
      errors.push({
        field: "password",
        message: "仅接受公开演示密码，请展开密码说明；不要输入真实密码。",
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

  /** 构造身份但不提交；租户开通必须与管理员资格共同原子写入。 */
  function prepareAccount(input = {}, platformAdministrator = false) {
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
      platformAdministrator,
      administratorEligible: !platformAdministrator,
      memberEligible: !platformAdministrator,
      authenticationRevision: 1,
      loginScenario: demoLoginScenario(input.password),
      authenticationChangedAt: "刚刚创建",
    };
    return { ok: true, account };
  }

  /** 平台目录只创建平台管理身份；合成口令场景不是实际凭据。 */
  function createAccount(input = {}, actorAccountId) {
    if (!canManageAccounts(actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const prepared = prepareAccount(input, true);
    if (!prepared.ok) return prepared;
    const saved = commitState({ ...state, accounts: [...state.accounts, prepared.account] });
    return saved.ok ? { ok: true, account: clone(prepared.account) } : saved;
  }

  /** 重置同时切换公开场景和认证修订，保存失败时原场景与既有身份均不受影响。 */
  function resetAccountPassword(accountId, input = {}, actorAccountId) {
    if (!canManageAccounts(actorAccountId)) {
      return { ok: false, reason: "FORBIDDEN" };
    }
    if (accountId === actorAccountId) {
      return { ok: false, reason: "SELF_RESET_FORBIDDEN" };
    }
    return applyPasswordReset(accountId, input);
  }

  /** 全局口令只能由所属租户治理；共享身份和管理员不能借成员入口重置。 */
  function resetMemberPassword(tenantId, accountId, input, actorAccountId) {
    if (!canManageMembers(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const memberships = state.tenants.flatMap(tenant => tenant.members.filter(member => member.accountId === accountId).map(member => ({ tenantId: tenant.id, ...member })));
    if (accountById(accountId)?.platformAdministrator || memberships.length !== 1 || memberships[0].tenantId !== tenantId || memberships[0].role !== "ordinaryMember") return { ok: false, reason: "MEMBER_RESET_FORBIDDEN" };
    return applyPasswordReset(accountId, input);
  }

  /** 两个受控入口共享口令轮换事务，失败不得撤销既有身份。 */
  function applyPasswordReset(accountId, input = {}) {
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
      loginScenario: demoLoginScenario(input.password),
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

  /** 平台一次性开通组织、身份和管理员资格；失败不留下账号或无管理员租户。 */
  function createTenant(input = {}, actorAccountId) {
    if (!canManageAccounts(actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const organizationName = String(input.organizationName || "").trim();
    const tenantCode = String(input.tenantCode || "").trim().toLowerCase();
    const administrator = prepareAccount(input.administrator);
    const errors = [];

    if (organizationName.length < 2 || organizationName.length > 40) {
      errors.push({ field: "organizationName", message: "请输入 2–40 个字符的组织名称。" });
    }
    if (!/^[a-z][a-z0-9-]{2,19}$/.test(tenantCode) || state.tenants.some((tenant) => tenant.code === tenantCode)) {
      errors.push({ field: "tenantCode", message: "租户编码需为 3–20 位小写字母、数字或连字符，且不能重复。" });
    }
    if (!administrator.ok) errors.push(...administrator.errors);
    if (input.confirmed !== true) {
      errors.push({ field: "confirmed", message: "请确认租户信息、管理员和资源配额。" });
    }
    const validated = validateQuotas(input.quotas ?? DEFAULT_QUOTAS);
    errors.push(...validated.errors);
    if (errors.length > 0) {
      return { ok: false, errors };
    }

    const administratorAccountId = administrator.account.id;
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
    const saved = commitState({ ...state, accounts: [...state.accounts, administrator.account], tenants: [...state.tenants, tenant], selectedTenantId: tenant.id, tenantSetup: defaultTenantSetup() });
    if (!saved.ok) return saved;
    return { ok: true, tenant: clone(tenant) };
  }

  /** 仅活动租户的活动管理员可治理普通成员；入口隐藏不是授权边界。 */
  function canManageMembers(tenantId, actorAccountId) {
    const account = accountById(actorAccountId);
    const tenant = tenantById(tenantId);
    const membership = memberById(tenantId, actorAccountId);
    return account?.status === "available" && !account.platformAdministrator && tenant?.status === "active" && membership?.role === "tenantAdministrator" && membership.status === "active";
  }

  /** 开户与普通成员授权一次提交，不再依赖平台的人员候选目录。 */
  function addMember(tenantId, input = {}, actorAccountId) {
    if (!canManageMembers(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const prepared = prepareAccount(input, false);
    if (!prepared.ok) return prepared;
    const account = prepared.account;
    if (!Array.isArray(input.conversationFeatures) || !Array.isArray(input.assistantIds) || input.conversationFeatures.some(feature => !CONVERSATION_FEATURES.includes(feature))) return { ok: false, reason: "MEMBER_FEATURES_INVALID" };
    if (input.assistantIds.some(id => !state.assistantDelivery.assistants.some(assistant => assistant.id === id && assistant.tenantId === tenantId && assistant.versions.some(version => version.id === assistant.publishedVersionId && version.status === "PUBLISHED")))) return { ok: false, reason: "MEMBER_ASSISTANTS_INVALID" };
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
    const saved = commitState({ ...state, selectedDemoAccountId: account.id, accounts: [...state.accounts, account], tenants: state.tenants.map(item => item.id === tenantId ? { ...item, members: [...item.members, member] } : item) });
    return saved.ok ? { ok: true, member: clone(member), account: clone(account) } : saved;
  }

  function findMember(tenantId, accountId) {
    const member = memberById(tenantId, accountId);
    return member ? clone(member) : null;
  }

  function setMemberStatus(tenantId, accountId, status, actorAccountId) {
    if (!canManageMembers(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
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
    const nextMember = { ...member, status };
    const saved = commitState({ ...state, tenants: state.tenants.map(tenant => tenant.id === tenantId ? { ...tenant, members: tenant.members.map(item => item.accountId === accountId ? nextMember : item) } : tenant) });
    return saved.ok ? { ok: true, member: clone(nextMember) } : saved;
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

  /** 两个编辑页只提交各自白名单字段；复用管理员授权与原子持久化，旧草稿不能夹带提交。 */
  function saveMemberScope(tenantId, accountId, scope, value, actorAccountId) {
    if (!canManageMembers(tenantId, actorAccountId)) return { ok: false, reason: "FORBIDDEN" };
    const member = memberById(tenantId, accountId), tenant = tenantById(tenantId);
    if (!member || member.role !== "ordinaryMember") return { ok: false, reason: "MEMBER_FEATURE_NOT_EDITABLE" };
    let patch;
    if (scope === 'features') {
      if (!value || Object.keys(value).some(key => !CONVERSATION_FEATURES.includes(key)) || CONVERSATION_FEATURES.some(key => typeof value[key] !== 'boolean' || (value[key] && !member.conversationFeatures[key] && !tenant.availableFeatures[key]))) return { ok: false, reason: 'MEMBER_FEATURES_INVALID' };
      patch = { conversationFeatures: clone(value) };
    } else if (scope === 'assistants') {
      if (!Array.isArray(value) || value.some(id => !state.assistantDelivery.assistants.some(assistant => assistant.id === id && assistant.tenantId === tenantId && assistant.publishedVersionId && assistant.versions.some(version => version.id === assistant.publishedVersionId && version.status === 'PUBLISHED')))) return { ok: false, reason: 'MEMBER_ASSISTANTS_INVALID' };
      patch = { assistantIds: [...new Set(value)] };
    } else return { ok: false, reason: 'SCOPE_INVALID' };
    const saved = commitState({ ...state, tenants: state.tenants.map(item => item.id === tenantId ? { ...item, members: item.members.map(current => current.accountId === accountId ? { ...current, ...patch } : current) } : item) });
    return saved.ok ? { ok: true, member: clone(memberById(tenantId, accountId)) } : saved;
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

  function authorizeScreen(screen, tenantId, accountId, scope = "tenant") {
    const feature = SCREEN_FEATURE[screen];
    if (CONVERSATION_FEATURES.includes(feature)) {
      return getEffectiveAccess(tenantId, accountId, { feature });
    }
    const account = accountById(accountId);
    if (!account || account.status !== "available") return { allowed: false, reason: "ACCOUNT_NOT_ACTIVE" };
    if (scope === "platform") return { allowed: canManageAccounts(accountId), reason: "ROLE_BOUNDARY" };
    const member = memberById(tenantId, accountId);
    const tenant = tenantById(tenantId);
    if (scope !== "tenant" || account.platformAdministrator || member?.role !== "tenantAdministrator" ||
        member.status !== "active" || tenant?.status !== "active") {
      return { allowed: false, reason: "ROLE_BOUNDARY" };
    }
    return !feature || tenant.availableFeatures[feature]
      ? { allowed: true, reason: "ALLOWED" }
      : { allowed: false, reason: "TENANT_FEATURE_CLOSED" };
  }

  /** 浏览器只消费当前合成登录身份；场景参数不能赋予角色或绕过功能上限。 */
  function authorizeSession(screen, scope) {
    if (["login", "console-login", "tenant-login"].includes(screen) || scope === "device") return { allowed: true };
    const session = getSession();
    if (!session.authenticated) return { allowed: false, reason: "AUTH_REQUIRED" };
    if ((scope === "query") !== (session.accessChannel === "QUERY_APP")) return { allowed: false, reason: "ROLE_BOUNDARY" };
    return authorizeScreen(screen, session.tenantId, session.accountId, scope);
  }

  function persistQueryMutation(mutator) {
    const before = clone(state.query);
    const selectedDemoAccountId = state.selectedDemoAccountId;
    const result = mutator();
    try {
      persist();
      return result;
    } catch {
      state.query = before;
      state.selectedDemoAccountId = selectedDemoAccountId;
      return { ok: false, reason: "STORAGE_UNAVAILABLE" };
    }
  }

  /** storage 通知可能晚于点击；直接核对已提交账户事实，读取失败不回退旧口令或身份。 */
  function authenticationAccounts() {
    try {
      const raw = storage.getItem(PROTOTYPE_STATE_KEY);
      if (raw === null) return state.accounts;
      const accounts = JSON.parse(raw)?.accounts;
      return Array.isArray(accounts) ? accounts : [];
    } catch { return []; }
  }

  function authenticateAccount(username, password) {
    const scenario = demoLoginScenario(password);
    if (!scenario) return null;
    return authenticationAccounts().find(account => account.loginName === String(username || "").trim() &&
      account.status === "available" && account.loginScenario === scenario) ?? null;
  }

  /** 只识别未配置过登录的内置合成账户；不覆盖重置、停用、自建或畸形账户。 */
  function legacyDemoAccounts(snapshot) {
    if (snapshot?.schemaVersion !== 1 || !Array.isArray(snapshot.accounts)) return [];
    const fixtures = defaultState().accounts;
    return snapshot.accounts.filter(account => account && account.status === "available" &&
      !Object.hasOwn(account, "loginScenario") && (account.authenticationRevision ?? 1) === 1 &&
      !account.authenticationChangedAt && fixtures.some(fixture => fixture.id === account.id &&
        fixture.loginName === account.loginName) &&
      snapshot.accounts.filter(item => item?.id === account.id || item?.loginName === account.loginName).length === 1);
  }

  /** 仅投影旧演示配置是否可修复，不显示账户当前口令，也不以读取动作迁移数据。 */
  function needsDemoLoginRepair() {
    try { return legacyDemoAccounts(JSON.parse(storage.getItem(PROTOTYPE_STATE_KEY))).length > 0; }
    catch { return false; }
  }

  /**
   * 必须人工确认后补齐旧内置夹具。此操作不是生产密码恢复：不赋予角色、不启用账户、不自动登录。
   * 只原子写回最新快照的登录字段，保留全部业务数据；提高认证版本使旧会话继续失效。
   */
  function repairDemoLogin(confirmed) {
    if (confirmed !== true) return { ok: false, reason: "CONFIRMATION_REQUIRED" };
    try {
      const snapshot = JSON.parse(storage.getItem(PROTOTYPE_STATE_KEY));
      if (snapshot?.schemaVersion !== 1 || !Array.isArray(snapshot.accounts)) return { ok: false, reason: "INVALID_DEMO_DATA" };
      const accounts = legacyDemoAccounts(snapshot);
      if (!accounts.length) return { ok: true, repairedCount: 0 };
      for (const account of accounts) Object.assign(account, { loginScenario: "INITIAL", authenticationRevision: 2 });
      storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
      state.accounts = snapshot.accounts;
      return { ok: true, repairedCount: accounts.length };
    } catch { return { ok: false, reason: "STORAGE_UNAVAILABLE" }; }
  }

  /** 合成会话独立于业务快照；重新校验账户修订、成员状态和渠道，失效时不返回旧主体。 */
  function getSession() {
    if (!querySessionClosed) state.query.session = readQuerySession(storage, state.query.session.epoch);
    const session = state.query.session;
    const account = authenticationAccounts().find(item => item.id === session.accountId);
    const member = memberById(session.tenantId, session.accountId);
    const validAccount = account?.status === "available" && Object.hasOwn(DEMO_LOGIN_SCENARIOS, account.loginScenario) &&
      (account.authenticationRevision || 1) === session.authenticationRevision;
    const validScope = session.accessChannel === "CONSOLE" && account?.platformAdministrator
      ? session.tenantId === null
      : tenantById(session.tenantId)?.status === "active" && member?.status === "active" &&
        member.role === (session.accessChannel === "CONSOLE" ? "tenantAdministrator" : "ordinaryMember");
    return session.authenticated && validAccount && validScope
      ? clone(session) : { ...defaultQueryState().session, epoch: session.epoch };
  }

  function establishSession(accountId, tenantId, accessChannel, screen) {
    const account = accountById(accountId);
    const epoch = Math.max(Date.now(), state.query.session.epoch + 1, readQuerySession(storage).epoch + 1);
    const session = { authenticated: true, tenantId, accountId, accessChannel,
      authenticationRevision: account.authenticationRevision || 1, epoch };
    // 登录只原子替换一个键，不在认证期间改写聊天记录；保存失败仍保持原身份和正文。
    try { storage.setItem(QUERY_SESSION_KEY, JSON.stringify(session)); }
    catch { return { ok: false, reason: "STORAGE_UNAVAILABLE" }; }
    state.query.session = session;
    state.query.currentConversationId = null;
    if (accessChannel === "QUERY_APP") state.selectedDemoAccountId = accountId;
    querySessionClosed = false;
    return { ok: true, screen };
  }

  /** 从同一人员目录认证；没有管理职责不能进入控制台，不能回落到默认租户。 */
  function loginConsole(input = {}) {
    const account = authenticateAccount(input.username, input.password);
    if (!account) return { ok: false, reason: "INVALID_CREDENTIALS" };
    reloadFromStorage();
    if ((input.entry === 'platform' && !account.platformAdministrator) || (input.entry === 'tenant' && account.platformAdministrator)) return { ok: false, reason: 'ROLE_BOUNDARY' };
    if (account.platformAdministrator) return establishSession(account.id, null, "CONSOLE", "platform-overview");
    const tenants = state.tenants.filter(tenant => authorizeScreen("tenant-overview", tenant.id, account.id).allowed);
    if (!tenants.length) return { ok: false, reason: "ROLE_BOUNDARY" };
    const tenantCode = String(input.tenantCode || "").trim();
    if (tenants.length > 1 && !tenantCode) return {
      ok: false, reason: "TENANT_SELECTION_REQUIRED",
      tenants: tenants.map(({ code, organizationName }) => ({ code, organizationName })),
    };
    const tenant = tenantCode ? tenants.find(item => item.code === tenantCode) : tenants[0];
    if (!tenant) return { ok: false, reason: "ROLE_BOUNDARY" };
    return establishSession(account.id, tenant.id, "CONSOLE", "tenant-overview");
  }

  function loginQuery(input = {}) {
    const account = authenticateAccount(input.username, input.password);
    if (!account) return { ok: false, reason: "INVALID_CREDENTIALS" };
    reloadFromStorage();
    const tenant = state.tenants.find(item => item.code === String(input.tenantCode || "").trim());
    const member = memberById(tenant?.id, account.id);
    if (!tenant || member?.role !== "ordinaryMember" || account.platformAdministrator) return { ok: false, reason: "MEMBERSHIP_NOT_ACTIVE" };
    const access = getEffectiveAccess(tenant.id, account.id, { feature: "assistantSelection" });
    if (!access.allowed) return { ok: false, reason: access.reason };
    if (!member.assistantIds.some(id => assistantById(id)?.tenantId === tenant.id && publishedAssistant(null, id))) return { ok: false, reason: "NO_AUTHORIZED_ASSISTANT" };
    return establishSession(account.id, tenant.id, "QUERY_APP", "assistants");
  }

  function logoutSession() {
    querySessionClosed = true;
    state.query.session = { ...defaultQueryState().session, epoch: state.query.session.epoch + 1 };
    try {
      storage.removeItem(QUERY_SESSION_KEY);
      return { ok: true, session: getSession() };
    } catch {
      return { ok: false, reason: "STORAGE_UNAVAILABLE" };
    }
  }

  function queryAccess(feature, assistantId) {
    const session = getSession();
    if (!session.authenticated || session.accessChannel !== "QUERY_APP") {
      return { allowed: false, reason: "AUTH_REQUIRED" };
    }
    return getEffectiveAccess(session.tenantId, session.accountId, {
      feature,
      assistantId,
    });
  }

  function publishedAssistant(fixtures, assistantId) {
    const fixture = (fixtures?.assistants || []).find(
      (assistant) =>
        assistant.id === assistantId && assistant.status === "PUBLISHED",
    );
    const managed = assistantById(assistantId);
    const version = managed?.versions.find(item => item.id === managed.publishedVersionId && item.status === "PUBLISHED");
    if (!managed || !version) return fixture;
    const knowledgeBase = knowledgeBaseById(managed.knowledgeBaseId);
    return {
      ...(fixture || {}),
      id: managed.id,
      versionId: version.id,
      name: managed.name,
      summary: managed.summary,
      knowledgeBase: knowledgeBase?.name || "不可用知识库",
      version: `v${version.number}`,
      indexVersionId: version.frozen?.indexVersionId || "",
      status: "PUBLISHED",
      updatedAt: version.publishedAt || "刚刚",
    };
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
      assistantVersionId: source.assistantVersionId || assistant?.versionId || "",
      assistantVersion: source.assistantVersion || assistant?.version || "",
      indexVersionId: source.indexVersionId || assistant?.indexVersionId || "",
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
        createdSessionEpoch: state.query.session.epoch,
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
    const { id, userSequence, assistantSequence, offset, sessionEpoch } = run;
    // 旧身份的接收器只能显示为暂停；用户明确继续后再保存新 epoch 和运行状态。
    const status = run.status === "RECEIVING" && sessionEpoch !== state.query.session.epoch ? "PAUSED" : run.status;
    return { id, status, userSequence, assistantSequence, offset, sessionEpoch, totalCharacters: run.answer.length };
  }

  function canUseConversation(fixtures, conversationId) {
    const session = getSession();
    if (!session.authenticated || session.accessChannel !== "QUERY_APP") {
      return { allowed: false, reason: "AUTH_REQUIRED" };
    }
    const conversation = queryConversation(fixtures, conversationId);
    if (!conversation || conversation.memberId !== state.query.session.accountId) {
      return { allowed: false, reason: "CONVERSATION_NOT_FOUND" };
    }
    const history = queryAccess("ownHistory", conversation.assistantId);
    const conversationAccess = queryAccess("conversation", conversation.assistantId);
    // 仅当前登录中新建且仍选中的会话不依赖历史权限；URL 本身不能把旧历史变成当前会话。
    const current = conversation.id === state.query.currentConversationId && conversation.createdSessionEpoch === session.epoch;
    if ((!history.allowed && !current) || !conversationAccess.allowed) {
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
    if (!run || !from.includes(queryRunSummary(run).status)) {
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
    const history = queryAccess("ownHistory", access.conversation.assistantId);
    if (!history.allowed) return { ok: false, reason: history.reason };
    return persistQueryMutation(() => {
      if (!state.query.loadedEarlierConversationIds.includes(conversationId)) {
        state.query.loadedEarlierConversationIds.push(conversationId);
      }
      return { ok: true };
    });
  }

  /** 合成问答数据也按当前登录成员、已发布助手和会话归属过滤，不以菜单隐藏替代数据过滤。 */
  function queryProjection(fixtures, request = {}) {
    const session = getSession();
    if (!session.authenticated || session.accessChannel !== "QUERY_APP") {
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
    const publishedIds = [...new Set([
      ...(fixtures?.assistants || []).filter(item => item.status === "PUBLISHED").map(item => item.id),
      ...state.assistantDelivery.assistants.filter(item => item.publishedVersionId).map(item => item.id),
    ])];
    const published = publishedIds.map(assistantId => publishedAssistant(fixtures, assistantId)).filter(Boolean);
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
    const access = canUseConversation(fixtures, request.conversationId);
    const conversation = access.allowed ? access.conversation : null;
    const assistantId = conversation?.assistantId || request.assistantId || assistants[0]?.id;
    const conversationAllowed = Boolean(
      conversation &&
      allowed("conversation", assistantId),
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
    for (const message of messages) {
      const run = stored?.runs.find(item => item.id === message.runId);
      if (run && message.runState) message.runState = queryRunSummary(run).status;
    }
    const activeRun = stored && conversationAllowed
      ? [...stored.runs].reverse().find((run) =>
          ["RECEIVING", "PAUSED", "FAILED", "STOPPED"].includes(run.status),
        ) || null
      : null;
    const canLoadEarlier = Boolean(
      conversation &&
        allowed("ownHistory", conversation.assistantId) &&
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
    if (state.query.session.authenticated && state.query.session.accountId !== accountId) logoutSession();
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
    const closedEpoch = state.query.session.epoch;
    state = initialState(storage);
    if (querySessionClosed && state.query.session.epoch <= closedEpoch) state.query.session = { ...defaultQueryState().session, epoch: closedEpoch };
    else querySessionClosed = false;
    tenantDrafts.clear();
    memberDrafts.clear();
    return clone(state);
  }

  /** 页面只读取当前组织的合成资产；原始快照仅供状态验证和平台目录治理使用。 */
  function getWorkspaceSnapshot() {
    const session = getSession();
    const snapshot = clone(state);
    if (session.authenticated && !session.tenantId) return snapshot;
    const tenantId = session.authenticated ? session.tenantId : null;
    const project = value => {
      if (Array.isArray(value)) return value.filter(item => !item || typeof item !== "object" ||
        ((!Object.hasOwn(item, "tenantId") || item.tenantId === tenantId) &&
         (!Array.isArray(item.tenantIds) || item.tenantIds.includes(tenantId)))).map(project);
      if (!value || typeof value !== "object") return value;
      return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, project(item)]));
    };
    const scoped = project(snapshot);
    scoped.tenants = snapshot.tenants.filter(tenant => tenant.id === tenantId);
    scoped.selectedTenantId = tenantId;
    const runIds = new Set(scoped.evaluation.runs.map(item => item.id));
    scoped.evaluation.comparisons = scoped.evaluation.comparisons.filter(item => runIds.has(item.baselineRunId) && runIds.has(item.candidateRunId));
    if (tenantId !== DEFAULT_TENANT_ID) scoped.accessGovernance.queryUsage = { acceptedToday: 0, remainingToday: scoped.accessGovernance.queryQuota.dailyAcceptedLimit, activeNow: 0, refusedToday: 0 };
    const authorizedConnections = new Set(scoped.models.platformDeployments.map(item => item.connectionId));
    scoped.models.platformConnections = scoped.models.platformConnections.filter(item => authorizedConnections.has(item.id));
    if (!scoped.knowledgeBases.some(item => item.id === scoped.selectedKnowledgeBaseId)) {
      scoped.selectedKnowledgeBaseId = scoped.knowledgeBases[0]?.id ?? null;
      scoped.selectedKnowledgeSourceId = null;
      scoped.selectedIndexVersionId = null;
    }
    return scoped;
  }

  /** URL 是当前标签页的对象定位依据；读取只改本页选择，不持久化、不接受跨组织定位。 */
  function applyWorkspaceContext(context = {}) {
    const session = getSession();
    if (!session.authenticated || !session.tenantId ||
        (context.tenantId && context.tenantId !== session.tenantId)) return { ok: false, reason: "FORBIDDEN" };
    const candidates = state.knowledgeBases.filter(item => item.tenantId === session.tenantId);
    const knowledgeBase = context.knowledgeBaseId
      ? candidates.find(item => item.id === context.knowledgeBaseId)
      : candidates.find(item => item.id === state.selectedKnowledgeBaseId) || candidates[0];
    if ((context.knowledgeBaseId && !knowledgeBase) ||
        (context.sourceId && !knowledgeBase?.sources.some(item => item.id === context.sourceId)) ||
        (context.indexVersionId && !knowledgeBase?.indexVersions.some(item => item.id === context.indexVersionId))) {
      return { ok: false, reason: "OBJECT_NOT_AVAILABLE" };
    }
    const changed = state.selectedKnowledgeBaseId !== knowledgeBase?.id;
    state.selectedKnowledgeBaseId = knowledgeBase?.id ?? null;
    state.selectedKnowledgeSourceId = context.sourceId || (!changed && state.selectedKnowledgeSourceId) || knowledgeBase?.sources[0]?.id || null;
    state.selectedIndexVersionId = context.indexVersionId || (!changed && state.selectedIndexVersionId) || knowledgeBase?.indexVersions.find(item => item.status === "READY_FOR_REVIEW")?.id || knowledgeBase?.activeIndex || null;
    return { ok: true };
  }

  return {
    reset,
    getSnapshot: () => clone(state),
    getWorkspaceSnapshot,
    applyWorkspaceContext,
    applyDetailContext: context => getSession().authenticated ? restoreDetailContext(state, getWorkspaceSnapshot(), context) : { ok: false, reason: "AUTH_REQUIRED" },
    updateTenantSetup,
    discardTenantSetup,
    getTenantQuotas,
    saveTenantQuotas,
    getTenantSecurity,
    saveTenantSecurity,
    createAccount,
    resetAccountPassword,
    resetMemberPassword,
    assignPlatformAdministrator,
    setAccountStatus,
    createKnowledgeBase,
    selectKnowledgeBase,
    selectKnowledgeSource,
    addKnowledgeSource,
    replaceKnowledgeSource,
    advanceKnowledgeSource,
    retryKnowledgeSource,
    retryFailedKnowledgeSources,
    reprocessKnowledgeSource,
    selectIndexVersion,
    createIndexRebuild,
    advanceIndexBuild,
    recordIndexEvaluation,
    activateIndexVersion,
    cancelIndexBuild,
    selectModelGovernanceItem,
    createPlatformModelConnection,
    probePlatformModelConnection,
    publishPlatformModelConnection,
    createPlatformModelDeployment,
    verifyPlatformModelDeployment,
    publishPlatformModelDeployment,
    discoverTenantModels,
    createTenantModelConfiguration,
    createTenantModelConfigurationRevision,
    rotateTenantModelAccessKey,
    verifyTenantModelConfiguration,
    enableTenantModelConfiguration,
    createTenantModelDeployment,
    verifyTenantModelDeployment,
    publishTenantModelDeployment,
    deactivateTenantModelDeployment,
    simulateRoutingPolicy,
    createRoutingPolicy,
    publishRoutingPolicy,
    selectAssistantDeliveryItem,
    createAssistant,
    createAssistantVersion,
    saveAssistantDraft,
    runAssistantTest,
    addAssistantTestToEvaluationSet,
    runAssistantEvaluation,
    requestAssistantRelease,
    publishAssistantVersion,
    selectEvaluationDataset,
    selectEvaluationRun,
    selectEvaluationComparison,
    selectEvaluationFailure,
    createEvaluationDataset,
    createEvaluationDatasetVersion,
    addEvaluationSample,
    publishEvaluationDatasetVersion,
    createEvaluationRun,
    advanceEvaluationRun,
    createEvaluationComparison,
    recordEvaluationDecision,
    createEvaluationRemediation,
    saveAccessPolicyDraft,
    createAccessPolicy,
    createAccessPolicyVersion,
    selectAccessPolicy,
    previewAccessPolicyVersion,
    publishAccessPolicyVersion,
    getAccessPolicyProjection,
    getQueryGovernanceProjection,
    getAuditProjection,
    selectQueryDiagnostic,
    getQueryDiagnostic,
    createQueryRemediation,
    getStructuredDatasetProjection,
    saveStructuredSchemaDraft,
    previewStructuredSchemaVersion,
    publishStructuredSchemaVersion,
    getObjectStorageScopeProjection,
    registerObjectStorageScope,
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
    saveMemberScope,
    getEffectiveAccess,
    authorizeScreen,
    authorizeSession,
    queryProjection,
    getSession,
    loginConsole,
    loginQuery,
    needsDemoLoginRepair,
    repairDemoLogin,
    logoutSession,
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
