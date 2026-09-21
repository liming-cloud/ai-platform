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

test("结构版本冻结 CSV 标题顺序、字段语义和安全规则但不返回样本值", () => {
  const { store } = fresh();
  const projection = store.getStructuredDatasetProjection("dataset-office-directory", TENANT_ACTOR);
  assert.equal(projection.ok, true);
  assert.deepEqual(projection.dataset.headers, ["office_name", "floor", "access_note", "contact"]);
  assert.equal(projection.version.status, "DRAFT");
  assert.equal(projection.version.qualityRuleVersion, "dataset-csv-v1");
  assert.doesNotMatch(JSON.stringify(projection), /上海市|电话号码|身份证|rowValues|sampleRows/i);
});

test("结构版本草稿要求标题逐项一致且主内容与标题只能使用公开文本字段", () => {
  const { store } = fresh();
  const valid = store.getStructuredDatasetProjection("dataset-office-directory", TENANT_ACTOR).version;
  assert.equal(store.saveStructuredSchemaDraft(valid.id, {
    ...valid,
    headers: ["floor", "office_name", "access_note", "contact"],
  }, TENANT_ACTOR).reason, "DATASET_HEADERS_MISMATCH");
  assert.equal(store.saveStructuredSchemaDraft(valid.id, {
    ...valid,
    fields: valid.fields.map(field => field.name === "access_note" ? { ...field, sensitivity: "RESTRICTED" } : field),
  }, TENANT_ACTOR).reason, "DATASET_CONTENT_FIELD_UNSAFE");
  assert.equal(store.saveStructuredSchemaDraft(valid.id, {
    ...valid,
    fields: valid.fields.map(field => field.name === "office_name" ? { ...field, logicalType: "INTEGER" } : field),
  }, TENANT_ACTOR).reason, "DATASET_TITLE_FIELD_UNSAFE");
});

test("结构版本必须先通过抽样预检才能发布且发布后不可变", () => {
  const { storage, store } = fresh();
  assert.equal(store.publishStructuredSchemaVersion("dataset-schema-v4", TENANT_ACTOR).reason, "DATASET_PREVIEW_REQUIRED");
  const previewed = store.previewStructuredSchemaVersion("dataset-schema-v4", TENANT_ACTOR);
  assert.equal(previewed.ok, true);
  assert.equal(previewed.version.preview.passed, true);
  assert.equal(previewed.version.preview.checkedRows, 100);
  const published = store.publishStructuredSchemaVersion("dataset-schema-v4", TENANT_ACTOR);
  assert.equal(published.ok, true);
  assert.equal(published.version.status, "PUBLISHED");
  assert.equal(store.saveStructuredSchemaDraft("dataset-schema-v4", published.version, TENANT_ACTOR).reason, "DATASET_SCHEMA_IMMUTABLE");
  assert.equal(createPrototypeStore(storage).getStructuredDatasetProjection("dataset-office-directory", TENANT_ACTOR).version.status, "PUBLISHED");
});

test("结构版本越权和存储失败均保持原状态", () => {
  const { store } = fresh();
  assert.equal(store.previewStructuredSchemaVersion("dataset-schema-v4", "account-shen").reason, "FORBIDDEN");

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
  assert.equal(guarded.previewStructuredSchemaVersion("dataset-schema-v4", TENANT_ACTOR).reason, "STORAGE_UNAVAILABLE");
  assert.deepEqual(guarded.getSnapshot(), before);
});

test("对象范围投影只返回平台签发的业务事实，不返回对象位置和凭据", () => {
  const { store } = fresh();
  const projection = store.getObjectStorageScopeProjection(TENANT_ID, TENANT_ACTOR);
  assert.equal(projection.ok, true);
  assert.equal(projection.availableRanges[0].selectionRevision, 7);
  assert.equal(projection.availableRanges[0].mediaType, "text/plain");
  assert.doesNotMatch(JSON.stringify(projection), /bucket|objectKey|endpoint|accessKey|secret|s3:\/\//i);
});

test("登记对象范围只接受同租户已签发范围并形成可刷新来源", () => {
  const { storage, store } = fresh();
  const created = store.registerObjectStorageScope({
    tenantId: TENANT_ID,
    knowledgeBaseId: "kb-handbook",
    rangeId: "range-east-office-text",
    selectionRevision: 7,
    name: "华东办公制度对象来源",
  }, TENANT_ACTOR);
  assert.equal(created.ok, true);
  assert.equal(created.registration.status, "DRAFT");
  assert.equal(created.registration.objectCount, 18);
  assert.doesNotMatch(JSON.stringify(created.registration), /bucket|objectKey|endpoint|accessKey|secret/i);
  const restored = createPrototypeStore(storage).getObjectStorageScopeProjection(TENANT_ID, TENANT_ACTOR);
  assert.equal(restored.registrations.length, 1);
  assert.equal(restored.registrations[0].name, "华东办公制度对象来源");
});

test("对象范围重复请求幂等，伪造版本、越权或不属于租户的范围失败关闭", () => {
  const { store } = fresh();
  const input = {
    tenantId: TENANT_ID,
    knowledgeBaseId: "kb-handbook",
    rangeId: "range-east-office-text",
    selectionRevision: 7,
    name: "华东办公制度对象来源",
  };
  assert.equal(store.registerObjectStorageScope(input, TENANT_ACTOR).ok, true);
  assert.equal(store.registerObjectStorageScope(input, TENANT_ACTOR).replayed, true);
  assert.equal(store.getObjectStorageScopeProjection(TENANT_ID, TENANT_ACTOR).registrations.length, 1);
  assert.equal(store.registerObjectStorageScope({ ...input, selectionRevision: 8 }, TENANT_ACTOR).reason, "OBJECT_RANGE_REVISION_MISMATCH");
  assert.equal(store.registerObjectStorageScope({ ...input, rangeId: "range-other-tenant" }, TENANT_ACTOR).reason, "OBJECT_RANGE_NOT_FOUND");
  assert.equal(store.registerObjectStorageScope(input, "account-shen").reason, "FORBIDDEN");
});

test("知识管理功能关闭后结构版本和对象范围操作均失败关闭", () => {
  const { storage, store } = fresh();
  const snapshot = store.getSnapshot();
  snapshot.tenants.find(item => item.id === TENANT_ID).availableFeatures.knowledgeManagement = false;
  storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  const disabled = createPrototypeStore(storage);
  assert.equal(disabled.getStructuredDatasetProjection("dataset-office-directory", TENANT_ACTOR).reason, "FORBIDDEN");
  assert.equal(disabled.getObjectStorageScopeProjection(TENANT_ID, TENANT_ACTOR).reason, "FORBIDDEN");
});
