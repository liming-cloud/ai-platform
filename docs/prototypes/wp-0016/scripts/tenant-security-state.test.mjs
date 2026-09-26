import test from "node:test";
import assert from "node:assert/strict";
import { createMemoryStorage, createPrototypeStore, DEFAULT_TENANT_ID } from "../prototype-state.js";

const policy = { sessionHours: 4, failedAttempts: 3, lockMinutes: 30 };
test("平台保存登录策略并刷新，不保存安全关闭开关", () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  assert.equal(store.saveTenantSecurity(DEFAULT_TENANT_ID, { ...policy, isolation: false }, "platformAdministrator").ok, true);
  assert.deepEqual(createPrototypeStore(storage).getTenantSecurity(DEFAULT_TENANT_ID), policy);
  assert.equal(store.getSnapshot().tenants[0].safetyBoundaryMutable, false);
});

test("登录策略拒绝零、分数、无穷值和越权写入", () => {
  const store = createPrototypeStore(createMemoryStorage());
  const before = store.getSnapshot();
  for (const field of Object.keys(policy)) {
    for (const value of [0, -1, 0.5, "", true, Infinity, NaN]) {
      const result = store.saveTenantSecurity(DEFAULT_TENANT_ID, { ...policy, [field]: value }, "platformAdministrator");
      assert.equal(result.ok, false);
      assert.ok(result.errors.some(error => error.field === field));
    }
  }
  for (const role of ["tenantAdministrator", "ordinaryMember", undefined]) {
    assert.equal(store.saveTenantSecurity(DEFAULT_TENANT_ID, policy, role).reason, "FORBIDDEN");
  }
  assert.equal(store.saveTenantSecurity("missing", policy, "platformAdministrator").reason, "TENANT_NOT_FOUND");
  assert.deepEqual(store.getSnapshot(), before);
});

test("登录策略存储失败回滚，读取投影不能修改状态", () => {
  const store = createPrototypeStore({ ...createMemoryStorage(), setItem() { throw new Error("合成存储故障"); } });
  const before = store.getTenantSecurity(DEFAULT_TENANT_ID);
  assert.equal(store.saveTenantSecurity(DEFAULT_TENANT_ID, policy, "platformAdministrator").reason, "STORAGE_UNAVAILABLE");
  assert.deepEqual(store.getTenantSecurity(DEFAULT_TENANT_ID), before);
  before.sessionHours = 999;
  assert.notEqual(store.getTenantSecurity(DEFAULT_TENANT_ID).sessionHours, 999);
});
