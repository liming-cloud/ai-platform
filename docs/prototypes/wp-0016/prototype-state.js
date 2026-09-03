export const DEFAULT_TENANT_ID = "tenant-spruce";
export const PROTOTYPE_STATE_KEY = "wp0016-business-demo-v1";

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

const DEFAULT_QUOTAS = Object.freeze({
  knowledgeBases: 3,
  documentsPerKnowledgeBase: 1000,
  rawContentGbPerKnowledgeBase: 10,
  singleFileMb: 50,
  pastedTextMib: 1,
});

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function defaultFeatures() {
  return Object.fromEntries(TENANT_FEATURES.map((feature) => [feature, true]));
}

function defaultState() {
  return {
    schemaVersion: 1,
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
    return parsed?.schemaVersion === 1 ? parsed : defaultState();
  } catch {
    return defaultState();
  }
}

/** 仅保存可重置的合成状态；不能作为生产鉴权或持久化实现，输入密码不进入状态。 */
export function createPrototypeStore(storage = browserStorage()) {
  let state = initialState(storage);
  const tenantDrafts = new Map();
  const memberDrafts = new Map();

  function persist() {
    storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(state));
  }

  function tenantById(tenantId) {
    return state.tenants.find((tenant) => tenant.id === tenantId);
  }

  function accountById(accountId) {
    return state.accounts.find((account) => account.id === accountId);
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
      errors.push({ field: "administratorAccountId", message: "请选择合资格且非平台管理员的已有账户。" });
    }
    if (input.confirmed !== true) {
      errors.push({ field: "confirmed", message: "请确认组织、编码、初始管理员与默认配额。" });
    }
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
      quotas: clone(DEFAULT_QUOTAS),
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
    state.tenants.push(tenant);
    state.selectedTenantId = tenant.id;
    persist();
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

  /** 合成问答数据也按成员、已发布助手和会话归属过滤，不以菜单隐藏替代数据过滤。 */
  function queryProjection(fixtures, request = {}) {
    const tenantId = DEFAULT_TENANT_ID;
    const accountId = state.selectedDemoAccountId;
    const member = memberById(tenantId, accountId);
    const published = (fixtures?.assistants || []).filter((assistant) => assistant.status === "PUBLISHED");
    const allowed = (feature, assistantId) => published.some((assistant) => assistant.id === assistantId) &&
      getEffectiveAccess(tenantId, accountId, { feature, assistantId }).allowed;
    const assistants = published.filter((assistant) => allowed("assistantSelection", assistant.id));
    const assistantId = request.assistantId || assistants[0]?.id;
    const history = (fixtures?.conversations || []).filter((conversation) =>
      conversation.memberId === accountId && allowed("ownHistory", conversation.assistantId));
    const conversation = history.find((item) => item.id === request.conversationId && item.assistantId === assistantId);
    const conversationAllowed = allowed("conversation", assistantId);
    const messages = conversation && conversationAllowed
      ? clone((fixtures?.messages || []).filter((message) => message.conversationId === conversation.id))
      : [];
    if (!allowed("authorizedCitations", assistantId)) {
      messages.forEach((message) => delete message.citations);
    }
    return {
      member: member ? clone(member) : null,
      assistants: clone(assistants),
      history: clone(history),
      messages,
      conversationAllowed,
    };
  }

  function selectDemoAccount(accountId) {
    const member = memberById(DEFAULT_TENANT_ID, accountId);
    if (!member || member.role !== "ordinaryMember") {
      return { ok: false, reason: "DEMO_MEMBER_REQUIRED" };
    }
    state.selectedDemoAccountId = accountId;
    persist();
    return { ok: true };
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

  return {
    reset,
    getSnapshot: () => clone(state),
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
