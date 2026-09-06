import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  PROTOTYPE_STATE_KEY,
  createMemoryStorage,
  createPrototypeStore,
} from "../prototype-state.js";

const TENANT_ID = "tenant-spruce";
const TENANT_ACTOR = "account-qiao";
const fixtures = JSON.parse(readFileSync(new URL("../fixtures.json", import.meta.url), "utf8"));

function fresh() {
  const storage = createMemoryStorage();
  return { storage, store: createPrototypeStore(storage) };
}

function createDraft(store, name = "制度咨询助手") {
  const created = store.createAssistant(TENANT_ID, {
    name,
    summary: "回答员工制度和办事流程问题，并提供可定位引用。",
    knowledgeBaseId: "kb-handbook",
  }, TENANT_ACTOR);
  assert.equal(created.ok, true);
  return created;
}

function saveDraft(store, versionId, overrides = {}) {
  return store.saveAssistantDraft(versionId, {
    knowledgeBaseId: "kb-handbook",
    indexVersionId: "idx-v11",
    chatConfigurationId: "cfg-chat",
    embeddingConfigurationId: "cfg-embedding",
    rerankConfigurationId: "cfg-rerank",
    retrievalPolicy: "HYBRID_RRF_RERANK_V3",
    citationPolicy: "REQUIRED_V2",
    refusalPolicy: "CALIBRATED_V5",
    ...overrides,
  }, TENANT_ACTOR);
}

function prepareEvaluation(store, name = "制度咨询助手") {
  const { version } = createDraft(store, name);
  assert.equal(saveDraft(store, version.id).ok, true);
  const tested = store.runAssistantTest(version.id, "年假最多可以结转几天？", TENANT_ACTOR);
  assert.equal(tested.testResult.status, "ANSWERED");
  const added = store.addAssistantTestToEvaluationSet(version.id, TENANT_ACTOR);
  assert.equal(added.version.status, "EVALUATION_PENDING");
  return version.id;
}

test("创建助手永久绑定一个就绪知识库并拒绝重复、越权或不可用知识库", () => {
  const { store } = fresh();
  const created = createDraft(store);
  assert.equal(created.assistant.knowledgeBaseId, "kb-handbook");
  assert.equal(created.version.status, "DRAFT");
  assert.equal(store.createAssistant(TENANT_ID, {
    name: "制度咨询助手",
    summary: "重复名称不应建立第二个稳定助手。",
    knowledgeBaseId: "kb-handbook",
  }, TENANT_ACTOR).reason, "ASSISTANT_NAME_CONFLICT");
  const emptyKnowledgeBase = store.createKnowledgeBase({ name: "未建立索引知识库", description: "用于验证助手创建失败关闭。" }, TENANT_ACTOR);
  assert.equal(emptyKnowledgeBase.ok, true);
  assert.equal(store.createAssistant(TENANT_ID, {
    name: "客户知识助手",
    summary: "索引尚未完成时不能创建可测试助手。",
    knowledgeBaseId: emptyKnowledgeBase.knowledgeBase.id,
  }, TENANT_ACTOR).reason, "KNOWLEDGE_BASE_NOT_READY");
  assert.equal(store.createAssistant(TENANT_ID, {
    name: "越权助手",
    summary: "普通成员不能建立助手。",
    knowledgeBaseId: "kb-handbook",
  }, "account-shen").reason, "FORBIDDEN");
});

test("新版本继承稳定知识库并冻结索引、模型配置版本和三类策略", () => {
  const { store } = fresh();
  const created = store.createAssistantVersion("assistant-office", TENANT_ACTOR);
  assert.equal(created.version.knowledgeBaseId, "kb-handbook");
  assert.equal(saveDraft(store, created.version.id, { knowledgeBaseId: "kb-support" }).reason, "ASSISTANT_KNOWLEDGE_BASE_IMMUTABLE");
  const saved = saveDraft(store, created.version.id);
  assert.equal(saved.version.status, "TESTING");
  assert.equal(saved.version.frozen.indexVersionId, "idx-v11");
  assert.deepEqual(saved.version.frozen.configurationRevisions, {
    chat: { id: "cfg-chat", revision: 6 },
    embedding: { id: "cfg-embedding", revision: 4 },
    rerank: { id: "cfg-rerank", revision: 2 },
  });
});

test("单题测试使用冻结依赖并把有引用结果加入评测集", () => {
  const { store } = fresh();
  const { version } = createDraft(store);
  saveDraft(store, version.id);
  const tested = store.runAssistantTest(version.id, "年假最多可以结转几天？", TENANT_ACTOR);
  assert.equal(tested.version.status, "TESTED");
  assert.equal(tested.testResult.status, "ANSWERED");
  assert.equal(tested.testResult.citations.length, 2);
  assert.equal(tested.testResult.frozenIndexVersionId, "idx-v11");
  const added = store.addAssistantTestToEvaluationSet(version.id, TENANT_ACTOR);
  assert.equal(added.version.evaluationDataset.questionCount, 101);
  assert.equal(added.version.testResult.addedToEvaluation, true);
});

test("越权泄漏阻断发布请求，重新评测通过后才能进入发布确认", () => {
  const { store } = fresh();
  const versionId = prepareEvaluation(store);
  const failed = store.runAssistantEvaluation(versionId, TENANT_ACTOR, { failSecurity: true });
  assert.equal(failed.evaluation.status, "FAILED");
  assert.equal(failed.evaluation.metrics.unauthorizedLeakage, 1);
  assert.equal(store.requestAssistantRelease(versionId, TENANT_ACTOR).reason, "EVALUATION_GATE_FAILED");
  const passed = store.runAssistantEvaluation(versionId, TENANT_ACTOR);
  assert.equal(passed.evaluation.status, "PASSED");
  assert.equal(store.requestAssistantRelease(versionId, TENANT_ACTOR).version.status, "READY_FOR_RELEASE");
});

test("发布新版本只影响新会话，已存在会话继续固定旧版本", () => {
  const { store } = fresh();
  assert.equal(store.loginQuery({ tenantCode: "spruce-demo", username: "shen.demo", password: "demo-only" }).ok, true);
  const before = store.queryProjection(fixtures, { conversationId: "cv-20260902-01" }).conversation;
  assert.equal(before.assistantVersionId, "asv-0012");
  assert.equal(store.publishAssistantVersion("asv-0013", TENANT_ACTOR).version.status, "PUBLISHED");
  const created = store.createQueryConversation(fixtures, "assistant-employee-service");
  assert.equal(created.conversation.assistantVersionId, "asv-0013");
  assert.equal(created.conversation.indexVersionId, "idx-v12");
  const oldConversation = store.queryProjection(fixtures, { conversationId: "cv-20260902-01" }).conversation;
  assert.equal(oldConversation.assistantVersionId, "asv-0012");
});

test("助手状态可刷新恢复，功能关闭或存储失败时不留下幽灵助手", () => {
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
  assert.equal(guarded.createAssistant(TENANT_ID, {
    name: "幽灵助手",
    summary: "存储失败时不应出现在目录。",
    knowledgeBaseId: "kb-handbook",
  }, TENANT_ACTOR).reason, "STORAGE_UNAVAILABLE");
  assert.deepEqual(guarded.getSnapshot(), before);

  const { storage, store } = fresh();
  const snapshot = store.getSnapshot();
  snapshot.tenants.find(item => item.id === TENANT_ID).availableFeatures.assistantDelivery = false;
  storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  assert.equal(createPrototypeStore(storage).createAssistant(TENANT_ID, {
    name: "功能关闭助手",
    summary: "上级关闭后不能通过状态接口绕过。",
    knowledgeBaseId: "kb-handbook",
  }, TENANT_ACTOR).reason, "FORBIDDEN");
});
