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

test("新建评测数据集可复制已发布基线，但发布前仍是独立可编辑草稿", () => {
  const { store } = fresh();
  const result = store.createEvaluationDataset(TENANT_ID, {
    name: "制度专项回归",
    knowledgeBaseId: "kb-handbook",
    templateVersionId: "eval-set-v6",
  }, TENANT_ACTOR);
  assert.equal(result.ok, true);
  assert.equal(result.dataset.status, "DRAFT");
  assert.equal(result.version.status, "DRAFT");
  assert.equal(result.version.samples.length, 100);
  assert.notEqual(result.version.id, "eval-set-v6");
  assert.equal(result.version.sourceTemplateVersionId, "eval-set-v6");
});

test("数据集版本满足四类最小样本和期望证据后才能发布且发布后不可修改", () => {
  const { store } = fresh();
  const draftId = "eval-set-v7";
  const published = store.publishEvaluationDatasetVersion(draftId, TENANT_ACTOR);
  assert.equal(published.ok, true);
  assert.equal(published.version.status, "PUBLISHED");
  assert.equal(published.version.composition.answerable >= 60, true);
  assert.equal(published.version.composition.refusal >= 20, true);
  assert.equal(published.version.composition.authorization >= 10, true);
  assert.equal(published.version.composition.multiTurn >= 10, true);
  assert.equal(
    store.addEvaluationSample(draftId, {
      type: "ANSWERABLE",
      question: "发布后不得修改",
      expectedEvidence: ["src-policy-pdf#p6"],
    }, TENANT_ACTOR).reason,
    "EVALUATION_VERSION_IMMUTABLE",
  );

  const created = store.createEvaluationDataset(TENANT_ID, {
    name: "空白数据集",
    knowledgeBaseId: "kb-handbook",
  }, TENANT_ACTOR);
  assert.equal(store.publishEvaluationDatasetVersion(created.version.id, TENANT_ACTOR).reason, "EVALUATION_DATASET_INCOMPLETE");
});

test("运行只接受同租户冻结助手版本与已发布数据集并固定全部输入", () => {
  const { store } = fresh();
  const unpublished = store.createEvaluationRun(TENANT_ID, {
    assistantVersionId: "asv-0013",
    datasetVersionId: "eval-set-v7",
  }, TENANT_ACTOR);
  assert.equal(unpublished.reason, "EVALUATION_DATASET_NOT_PUBLISHED");

  const created = store.createEvaluationRun(TENANT_ID, {
    assistantVersionId: "asv-0013",
    datasetVersionId: "eval-set-v6",
  }, TENANT_ACTOR);
  assert.equal(created.ok, true);
  assert.equal(created.run.status, "RUNNING");
  assert.equal(created.run.frozen.assistantVersionId, "asv-0013");
  assert.equal(created.run.frozen.datasetVersionId, "eval-set-v6");
  assert.equal(created.run.frozen.indexVersionId, "idx-v12");
  assert.equal(store.createEvaluationRun(TENANT_ID, {
    assistantVersionId: "asv-0005",
    datasetVersionId: "eval-set-v6",
  }, TENANT_ACTOR).reason, "ASSISTANT_VERSION_NOT_FROZEN");
  assert.equal(store.createEvaluationRun(TENANT_ID, {
    assistantVersionId: "asv-0008",
    datasetVersionId: "eval-set-v6",
  }, TENANT_ACTOR).reason, "EVALUATION_KNOWLEDGE_BASE_MISMATCH");
});

test("评测运行逐阶段完成并同时产出质量、安全、性能和失败分类", () => {
  const { store } = fresh();
  const created = store.createEvaluationRun(TENANT_ID, {
    assistantVersionId: "asv-0013",
    datasetVersionId: "eval-set-v6",
  }, TENANT_ACTOR);
  let result;
  for (let index = 0; index < 4; index += 1) {
    result = store.advanceEvaluationRun(created.run.id, TENANT_ACTOR);
  }
  assert.equal(result.run.status, "COMPLETED");
  assert.equal(result.run.gate, "PASSED");
  assert.equal(result.run.metrics.unauthorizedLeakage, 0);
  assert.equal(result.run.metrics.recallAt10 >= 0.85, true);
  assert.equal(result.run.failures.reduce((total, item) => total + item.sampleCount, 0), 7);
  assert.deepEqual(result.run.failures.map(item => item.category), ["RETRIEVAL_MISS", "CITATION_MISMATCH", "OVER_REFUSAL"]);
});

test("版本比较只允许同一数据集，安全或质量回退时不能形成放行决定", () => {
  const { store } = fresh();
  const comparison = store.createEvaluationComparison("eval-run-v12", "eval-run-v13", TENANT_ACTOR);
  assert.equal(comparison.ok, true);
  assert.equal(comparison.comparison.sameDataset, true);
  assert.equal(store.recordEvaluationDecision(comparison.comparison.id, "APPROVE", TENANT_ACTOR).comparison.decision, "APPROVED");
  assert.equal(
    store.createEvaluationComparison("eval-run-v12", "eval-run-other-dataset", TENANT_ACTOR).reason,
    "EVALUATION_DATASET_MISMATCH",
  );
  const unsafe = store.createEvaluationComparison("eval-run-v12", "eval-run-security-regression", TENANT_ACTOR);
  assert.equal(unsafe.comparison.safeToApprove, false);
  assert.equal(store.recordEvaluationDecision(unsafe.comparison.id, "APPROVE", TENANT_ACTOR).reason, "EVALUATION_REGRESSION_BLOCKED");
});

test("失败类别可建立去重修复事项，越权和存储失败不留下幽灵状态", () => {
  const { store } = fresh();
  const created = store.createEvaluationRemediation("eval-run-v13", "RETRIEVAL_MISS", {
    title: "调整混合召回参数",
    owner: "知识治理组",
  }, TENANT_ACTOR);
  assert.equal(created.ok, true);
  assert.equal(created.remediation.sampleCount, 3);
  assert.doesNotMatch(JSON.stringify(created.remediation), /年假|真实问题|回答正文/);
  const replayed = store.createEvaluationRemediation("eval-run-v13", "RETRIEVAL_MISS", {
    title: "重复创建",
    owner: "知识治理组",
  }, TENANT_ACTOR);
  assert.equal(replayed.replayed, true);
  assert.equal(store.createEvaluationRemediation("eval-run-v13", "OVER_REFUSAL", {
    title: "越权操作",
    owner: "普通成员",
  }, "account-shen").reason, "FORBIDDEN");

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
  assert.equal(guarded.createEvaluationDataset(TENANT_ID, {
    name: "幽灵评测集",
    knowledgeBaseId: "kb-handbook",
  }, TENANT_ACTOR).reason, "STORAGE_UNAVAILABLE");
  assert.deepEqual(guarded.getSnapshot(), before);

  const disabledStorage = createMemoryStorage();
  const disabled = createPrototypeStore(disabledStorage);
  const snapshot = disabled.getSnapshot();
  snapshot.tenants.find(item => item.id === TENANT_ID).availableFeatures.evaluationGovernance = false;
  disabledStorage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  assert.equal(createPrototypeStore(disabledStorage).createEvaluationDataset(TENANT_ID, {
    name: "被上级关闭的评测集",
    knowledgeBaseId: "kb-handbook",
  }, TENANT_ACTOR).reason, "FORBIDDEN");
});
