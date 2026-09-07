import test from "node:test";
import assert from "node:assert/strict";
import { createMemoryStorage, createPrototypeStore, DEFAULT_TENANT_ID } from "../prototype-state.js";

const quotas = { knowledgeBases: 6, documentsPerKnowledgeBase: 2500, rawContentGbPerKnowledgeBase: 30, singleFileMb: 80, pastedTextMib: 2 };
const input = { organizationName: "青岚配置租户", tenantCode: "qinglan-config", administrator: { loginName: "qinglan.admin", password: "demo-only", passwordConfirmation: "demo-only" }, confirmed: true, quotas };
const fresh = () => { const storage = createMemoryStorage(); return { storage, store: createPrototypeStore(storage) }; };

test("创建租户保存非默认配额且刷新恢复", () => {
  const { storage, store } = fresh();
  const result = store.createTenant(input, "account-platform-admin");
  assert.equal(result.ok, true);
  assert.deepEqual(result.tenant.quotas, quotas);
  assert.deepEqual(createPrototypeStore(storage).getSnapshot().tenants.find(t => t.id === result.tenant.id).quotas, quotas);
  assert.equal(result.tenant.safetyBoundaryMutable, false);
});

test("非法配额在创建前拒绝且不留下租户", () => {
  for (const value of [0, -1, 1.5, "", true, Infinity, NaN]) {
    const { store } = fresh();
    const result = store.createTenant({ ...input, quotas: { ...quotas, knowledgeBases: value } }, "account-platform-admin");
    assert.equal(result.ok, false, `知识库数量 ${value} 应拒绝`);
    assert.ok(result.errors.some(error => error.field === "knowledgeBases"));
    assert.equal(store.getSnapshot().tenants.length, 1);
  }
});

test("创建草稿白名单持久化、回退与取消", () => {
  const { store, storage } = fresh();
  assert.equal(store.updateTenantSetup({ step: 2, organizationName: "未提交草稿", tenantCode: "draft-demo", password: "禁止持久化示例" }).ok, true);
  const resumed = createPrototypeStore(storage);
  assert.equal(resumed.getSnapshot().tenantSetup.organizationName, "未提交草稿");
  assert.equal(resumed.getSnapshot().tenantSetup.step, 2);
  resumed.updateTenantSetup({ step: 1 });
  assert.equal(resumed.getSnapshot().tenantSetup.tenantCode, "draft-demo");
  assert.doesNotMatch(storage.dump(), /禁止持久化示例|password/);
  assert.equal(resumed.discardTenantSetup().ok, true);
  assert.equal(createPrototypeStore(storage).getSnapshot().tenantSetup.organizationName, "");
});

test("成功创建清除草稿，重复编码失败则保留草稿", () => {
  const { store } = fresh();
  store.updateTenantSetup({ ...input, step: 3 });
  assert.equal(store.createTenant(input, "account-platform-admin").ok, true);
  assert.equal(store.getSnapshot().tenantSetup.step, 1);
  store.updateTenantSetup({ ...input, step: 3 });
  assert.equal(store.createTenant(input, "account-platform-admin").ok, false);
  assert.equal(store.getSnapshot().tenantSetup.organizationName, input.organizationName);
});

test("平台保存配额、只读投影隔离与刷新", () => {
  const { storage, store } = fresh();
  assert.equal(store.saveTenantQuotas(DEFAULT_TENANT_ID, quotas, "platformAdministrator").ok, true);
  const reloaded = createPrototypeStore(storage).getTenantQuotas(DEFAULT_TENANT_ID);
  assert.deepEqual(reloaded.quotas, quotas);
  assert.deepEqual(Object.keys(reloaded).sort(), ["quotas", "usage"]);
  assert.doesNotMatch(JSON.stringify(reloaded), /成员|员工服务手册|account|members|正文/);
  reloaded.quotas.knowledgeBases = 999;
  assert.equal(store.getTenantQuotas(DEFAULT_TENANT_ID).quotas.knowledgeBases, 6);
});

test("模拟租户角色和未知租户不能修改配额", () => {
  const { store } = fresh();
  const before = store.getSnapshot();
  for (const role of ["tenantAdministrator", "ordinaryMember", undefined]) {
    assert.equal(store.saveTenantQuotas(DEFAULT_TENANT_ID, quotas, role).ok, false);
  }
  assert.equal(store.saveTenantQuotas("tenant-missing", quotas, "platformAdministrator").ok, false);
  assert.deepEqual(store.getSnapshot(), before);
});

test("资源配额不能低于已用量，单次接入限制可降低而不删数据", () => {
  const { store } = fresh();
  const projection = store.getTenantQuotas(DEFAULT_TENANT_ID);
  assert.deepEqual(projection.usage, { knowledgeBases: 2, documentsPerKnowledgeBase: 428, rawContentGbPerKnowledgeBase: 2.6 });
  for (const [key, value] of Object.entries({ knowledgeBases: 1, documentsPerKnowledgeBase: 100, rawContentGbPerKnowledgeBase: 1 })) {
    assert.equal(store.saveTenantQuotas(DEFAULT_TENANT_ID, { ...quotas, [key]: value }, "platformAdministrator").ok, false);
  }
  const before = store.getSnapshot().tenants[0].members;
  assert.equal(store.saveTenantQuotas(DEFAULT_TENANT_ID, { ...quotas, singleFileMb: 1, pastedTextMib: 0.5 }, "platformAdministrator").ok, true);
  assert.deepEqual(store.getSnapshot().tenants[0].members, before);
});

test("存储失败不能报告创建、草稿或配置保存成功", () => {
  const storage = createMemoryStorage();
  const failing = { ...storage, setItem() { throw new Error("合成存储故障"); } };
  for (const act of [store => store.createTenant(input, "account-platform-admin"), store => store.updateTenantSetup({ organizationName: "失败草稿" }), store => store.saveTenantQuotas(DEFAULT_TENANT_ID, quotas, "platformAdministrator")]) {
    const store = createPrototypeStore(failing);
    const before = store.getSnapshot();
    const result = act(store);
    assert.equal(result.ok, false);
    assert.equal(result.reason, "STORAGE_UNAVAILABLE");
    assert.deepEqual(store.getSnapshot(), before);
  }
});
