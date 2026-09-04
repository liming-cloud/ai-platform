import assert from "node:assert/strict";
import test from "node:test";
import {
  PROTOTYPE_STATE_KEY,
  createMemoryStorage,
  createPrototypeStore,
} from "../prototype-state.js";

const TENANT_ID = "tenant-spruce";
const TENANT_ACTOR = "account-qiao";

function fresh() {
  const storage = createMemoryStorage();
  return { storage, store: createPrototypeStore(storage) };
}

test("访问策略只授权普通成员使用已发布助手，知识库从助手永久关系派生", () => {
  const { store } = fresh();
  const switched = store.saveAccessPolicyDraft("access-policy-v6", {
    assistantId: "assistant-support",
    memberAccountIds: ["account-shen"],
  }, TENANT_ACTOR);
  assert.equal(switched.ok, true);
  assert.equal(switched.version.knowledgeBaseId, "kb-support");
  assert.equal(switched.version.preview, null);
  assert.equal(store.saveAccessPolicyDraft("access-policy-v6", {
    assistantId: "assistant-support",
    memberAccountIds: ["account-qiao"],
  }, TENANT_ACTOR).reason, "ACCESS_POLICY_MEMBER_INVALID");
  store.previewAccessPolicyVersion("access-policy-v6", TENANT_ACTOR);
  store.publishAccessPolicyVersion("access-policy-v6", TENANT_ACTOR);
  const member = store.findMember(TENANT_ID, "account-shen");
  assert.equal(member.assistantIds.includes("assistant-support"), true);
  assert.equal(member.assistantIds.includes("assistant-employee-service"), false);
});

test("访问策略必须先通过允许与拒绝样本预览，发布后不可变并可刷新恢复", () => {
  const { storage, store } = fresh();
  assert.equal(store.publishAccessPolicyVersion("access-policy-v6", TENANT_ACTOR).reason, "ACCESS_POLICY_PREVIEW_REQUIRED");
  const previewed = store.previewAccessPolicyVersion("access-policy-v6", TENANT_ACTOR);
  assert.equal(previewed.ok, true);
  assert.equal(previewed.version.preview.passed, true);
  assert.equal(previewed.version.preview.allowCount, 24);
  assert.equal(previewed.version.preview.denyCount, 8);
  const published = store.publishAccessPolicyVersion("access-policy-v6", TENANT_ACTOR);
  assert.equal(published.ok, true);
  assert.equal(published.version.status, "PUBLISHED");
  assert.equal(store.saveAccessPolicyDraft("access-policy-v6", {
    assistantId: "assistant-office",
    memberAccountIds: ["account-shen"],
  }, TENANT_ACTOR).reason, "ACCESS_POLICY_VERSION_IMMUTABLE");
  const restored = createPrototypeStore(storage).getAccessPolicyProjection("access-policy-all-employees", TENANT_ACTOR);
  assert.equal(restored.version.status, "PUBLISHED");
  assert.equal(restored.assistant.name, "员工服务助手");
  assert.equal(restored.knowledgeBase.name, "员工服务手册");
});

test("访问策略越权与浏览器存储失败均不留下幽灵发布", () => {
  const { store } = fresh();
  assert.equal(store.previewAccessPolicyVersion("access-policy-v6", "account-shen").reason, "FORBIDDEN");

  const memory = createMemoryStorage();
  let rejectWrites = false;
  const guardedStorage = {
    ...memory,
    setItem(key, value) {
      if (rejectWrites) throw new Error("合成存储故障");
      memory.setItem(key, value);
    },
  };
  const guarded = createPrototypeStore(guardedStorage);
  const before = guarded.getSnapshot();
  rejectWrites = true;
  assert.equal(guarded.previewAccessPolicyVersion("access-policy-v6", TENANT_ACTOR).reason, "STORAGE_UNAVAILABLE");
  assert.deepEqual(guarded.getSnapshot(), before);
});

test("查询配额来自部署配置且只能读取脱敏用量，原型不提供在线修改接口", () => {
  const { store } = fresh();
  const projection = store.getQueryGovernanceProjection(TENANT_ID, TENANT_ACTOR);
  assert.equal(projection.ok, true);
  assert.deepEqual(projection.quota, {
    dailyAcceptedLimit: 1000,
    maxConcurrent: 20,
    leaseSeconds: 120,
    timezone: "UTC",
    source: "部署配置",
  });
  assert.equal(projection.usage.acceptedToday, 252);
  assert.equal(projection.usage.remainingToday, 748);
  assert.doesNotMatch(
    JSON.stringify(projection),
    /questionText|answerText|citations|prompt|tokenUsage|tenantId|chatModelRevision|embeddingModelRevision|tenant-spruce|chat-r|embed-r/i,
  );
  assert.equal(typeof store.saveQueryQuota, "undefined");
});

test("查询诊断只返回终态历史事实，不重新检索也不暴露正文", () => {
  const { store } = fresh();
  const result = store.getQueryDiagnostic("query-run-refused-042", TENANT_ACTOR);
  assert.equal(result.ok, true);
  assert.equal(result.diagnostic.candidateCount, 0);
  assert.equal(result.diagnostic.retrievalAuthorization, "AUTHORIZED");
  assert.equal(result.diagnostic.citationValidation, "NOT_APPLICABLE");
  assert.equal(result.diagnostic.outcome, "REFUSED");
  assert.equal(result.diagnostic.historical, true);
  assert.doesNotMatch(JSON.stringify(result), /年假|问题正文|回答正文|引用正文|prompt|position/i);
  assert.equal(store.getQueryDiagnostic("query-run-refused-042", "account-shen").reason, "FORBIDDEN");
});

test("诊断修复事项按运行去重且只保存阶段、分类和数量", () => {
  const { storage, store } = fresh();
  const created = store.createQueryRemediation("query-run-refused-042", {
    category: "AUTHORIZED_EVIDENCE_EMPTY",
    title: "复核访问策略与索引覆盖",
    owner: "知识治理组",
  }, TENANT_ACTOR);
  assert.equal(created.ok, true);
  assert.equal(created.remediation.stage, "INSUFFICIENT_EVIDENCE");
  assert.equal(created.remediation.candidateCount, 0);
  assert.doesNotMatch(JSON.stringify(created.remediation), /问题正文|回答正文|引用正文/);
  const replayed = store.createQueryRemediation("query-run-refused-042", {
    category: "AUTHORIZED_EVIDENCE_EMPTY",
    title: "重复事项",
    owner: "知识治理组",
  }, TENANT_ACTOR);
  assert.equal(replayed.replayed, true);
  assert.equal(createPrototypeStore(storage).getSnapshot().accessGovernance.queryRemediations.length, 1);
});

test("访问治理功能关闭后写操作和查询治理读取都失败关闭", () => {
  const { storage, store } = fresh();
  const snapshot = store.getSnapshot();
  snapshot.tenants.find(item => item.id === TENANT_ID).availableFeatures.evaluationGovernance = false;
  storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  const disabled = createPrototypeStore(storage);
  assert.equal(disabled.getQueryGovernanceProjection(TENANT_ID, TENANT_ACTOR).reason, "FORBIDDEN");
  assert.equal(disabled.createQueryRemediation("query-run-refused-042", {
    category: "AUTHORIZED_EVIDENCE_EMPTY",
    title: "不可创建事项",
    owner: "知识治理组",
  }, TENANT_ACTOR).reason, "FORBIDDEN");
});
