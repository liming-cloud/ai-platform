import test from "node:test";
import assert from "node:assert/strict";
import { createMemoryStorage, createPrototypeStore } from "../prototype-state.js";

const ACTOR = "account-qiao";
const KNOWLEDGE_BASE_ID = "kb-handbook";
const rebuildInput = {
  trigger: "CONFIGURATION_CHANGE",
  changedFields: ["embeddingDimension"],
  reason: "Embedding 维度由 1024 变更为 1536",
};
const passingQuality = {
  recallAt10: 0.88,
  citationPrecision: 0.97,
  refusalAccuracy: 0.99,
  unauthorizedLeakage: 0,
  retrievalP95Ms: 612,
};

function fresh() {
  const storage = createMemoryStorage();
  return { storage, store: createPrototypeStore(storage) };
}

function advanceToEvaluation(store, indexVersionId) {
  for (const stage of ["CHUNKING", "EMBEDDING", "HYBRID_INDEXING", "EVALUATING"]) {
    const result = store.advanceIndexBuild(KNOWLEDGE_BASE_ID, indexVersionId, ACTOR);
    assert.equal(result.ok, true);
    assert.equal(result.indexVersion.stage, stage);
  }
}

test("配置变化创建不可变索引版本但不自动激活，并可刷新恢复", () => {
  const { storage, store } = fresh();
  const before = store.getSnapshot().knowledgeBases.find(item => item.id === KNOWLEDGE_BASE_ID);
  const created = store.createIndexRebuild(KNOWLEDGE_BASE_ID, rebuildInput, ACTOR);
  assert.equal(created.ok, true);
  assert.equal(created.indexVersion.id, "idx-v13");
  assert.equal(created.indexVersion.status, "BUILDING");
  assert.equal(created.indexVersion.stage, "MANIFEST_FREEZING");
  assert.equal(created.indexVersion.trigger, "CONFIGURATION_CHANGE");
  assert.deepEqual(created.indexVersion.changedFields, ["embeddingDimension"]);
  assert.equal(created.indexVersion.config.embeddingDimension, 1536);

  const after = store.getSnapshot().knowledgeBases.find(item => item.id === KNOWLEDGE_BASE_ID);
  assert.equal(after.activeIndex, before.activeIndex);
  assert.equal(store.createIndexRebuild(KNOWLEDGE_BASE_ID, rebuildInput, ACTOR).reason, "INDEX_BUILD_ALREADY_ACTIVE");
  const restored = createPrototypeStore(storage).getSnapshot();
  assert.equal(restored.selectedIndexVersionId, "idx-v13");
  assert.equal(restored.knowledgeBases.find(item => item.id === KNOWLEDGE_BASE_ID).activeIndex, "idx-v11");
});

test("构建按阶段推进，冻结评测通过前禁止激活", () => {
  const { store } = fresh();
  const created = store.createIndexRebuild(KNOWLEDGE_BASE_ID, rebuildInput, ACTOR);
  assert.equal(store.activateIndexVersion(KNOWLEDGE_BASE_ID, created.indexVersion.id, ACTOR).reason, "INDEX_NOT_READY");
  advanceToEvaluation(store, created.indexVersion.id);
  assert.equal(store.advanceIndexBuild(KNOWLEDGE_BASE_ID, created.indexVersion.id, ACTOR).reason, "INDEX_EVALUATION_REQUIRED");
  const evaluated = store.recordIndexEvaluation(KNOWLEDGE_BASE_ID, created.indexVersion.id, passingQuality, ACTOR);
  assert.equal(evaluated.indexVersion.status, "READY_FOR_REVIEW");
  assert.equal(evaluated.indexVersion.progress, 100);
  assert.equal(store.getSnapshot().knowledgeBases.find(item => item.id === KNOWLEDGE_BASE_ID).status, "READY");
});

test("授权管理员原子激活新版本并保留旧版本事实", () => {
  const { storage, store } = fresh();
  const created = store.createIndexRebuild(KNOWLEDGE_BASE_ID, rebuildInput, ACTOR);
  advanceToEvaluation(store, created.indexVersion.id);
  store.recordIndexEvaluation(KNOWLEDGE_BASE_ID, created.indexVersion.id, passingQuality, ACTOR);
  const activated = store.activateIndexVersion(KNOWLEDGE_BASE_ID, created.indexVersion.id, ACTOR);
  assert.equal(activated.ok, true);
  assert.equal(activated.indexVersion.status, "ACTIVE");

  const restored = createPrototypeStore(storage).getSnapshot().knowledgeBases.find(item => item.id === KNOWLEDGE_BASE_ID);
  assert.equal(restored.activeIndex, "idx-v13");
  assert.equal(restored.indexVersions.find(item => item.id === "idx-v11").status, "SUPERSEDED");
  assert.equal(restored.indexVersions.find(item => item.id === "idx-v12").status, "SUPERSEDED");
  assert.equal(restored.indexVersions.find(item => item.id === "idx-v13").status, "ACTIVE");
});

test("质量门禁不通过时阻止激活且旧索引继续服务", () => {
  const { store } = fresh();
  const created = store.createIndexRebuild(KNOWLEDGE_BASE_ID, rebuildInput, ACTOR);
  advanceToEvaluation(store, created.indexVersion.id);
  const evaluated = store.recordIndexEvaluation(
    KNOWLEDGE_BASE_ID,
    created.indexVersion.id,
    { ...passingQuality, recallAt10: 0.81 },
    ACTOR,
  );
  assert.equal(evaluated.indexVersion.status, "BLOCKED");
  assert.deepEqual(evaluated.indexVersion.failedGates, ["Recall@10"]);
  assert.equal(store.activateIndexVersion(KNOWLEDGE_BASE_ID, created.indexVersion.id, ACTOR).reason, "QUALITY_GATE_FAILED");
  const knowledgeBase = store.getSnapshot().knowledgeBases.find(item => item.id === KNOWLEDGE_BASE_ID);
  assert.equal(knowledgeBase.activeIndex, "idx-v11");
  assert.equal(knowledgeBase.status, "READY");
});

test("取消、越权和存储失败均保留活动索引与原状态", () => {
  const { store } = fresh();
  assert.equal(store.createIndexRebuild(KNOWLEDGE_BASE_ID, rebuildInput, "account-shen").reason, "FORBIDDEN");
  const created = store.createIndexRebuild(KNOWLEDGE_BASE_ID, rebuildInput, ACTOR);
  const cancelled = store.cancelIndexBuild(KNOWLEDGE_BASE_ID, created.indexVersion.id, ACTOR);
  assert.equal(cancelled.indexVersion.status, "CANCELLED");
  const knowledgeBase = store.getSnapshot().knowledgeBases.find(item => item.id === KNOWLEDGE_BASE_ID);
  assert.equal(knowledgeBase.activeIndex, "idx-v11");
  assert.equal(knowledgeBase.status, "READY");

  const memory = createMemoryStorage();
  let rejectWrites = false;
  const guardedStorage = {
    ...memory,
    setItem(key, value) {
      if (rejectWrites) throw new Error("合成存储故障");
      memory.setItem(key, value);
    },
  };
  const guardedStore = createPrototypeStore(guardedStorage);
  const guarded = guardedStore.createIndexRebuild(KNOWLEDGE_BASE_ID, rebuildInput, ACTOR);
  advanceToEvaluation(guardedStore, guarded.indexVersion.id);
  guardedStore.recordIndexEvaluation(KNOWLEDGE_BASE_ID, guarded.indexVersion.id, passingQuality, ACTOR);
  const before = guardedStore.getSnapshot();
  rejectWrites = true;
  assert.equal(guardedStore.activateIndexVersion(KNOWLEDGE_BASE_ID, guarded.indexVersion.id, ACTOR).reason, "STORAGE_UNAVAILABLE");
  assert.deepEqual(guardedStore.getSnapshot(), before);
});
