import test from "node:test";
import assert from "node:assert/strict";
import { createMemoryStorage, createPrototypeStore, PROTOTYPE_STATE_KEY, QUERY_SESSION_KEY } from "../prototype-state.js";

function legacyStorage() {
  const storage = createMemoryStorage();
  const snapshot = createPrototypeStore().getSnapshot();
  snapshot.tenantSetup.organizationName = "保留未提交的组织草稿";
  for (const account of snapshot.accounts) delete account.loginScenario;
  storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  return storage;
}
const read = storage => JSON.parse(storage.getItem(PROTOTYPE_STATE_KEY));

test("旧版内置演示登录可检测，但没有明确确认不改变数据、不建立身份", () => {
  const storage = legacyStorage();
  const before = storage.dump();
  const store = createPrototypeStore(storage);
  assert.equal(store.needsDemoLoginRepair(), true);
  assert.equal(store.loginConsole({ username: "platform.admin.demo", password: "demo-only" }).ok, false);
  for (const confirmed of [undefined, false, "true", 1]) {
    assert.equal(store.repairDemoLogin(confirmed).reason, "CONFIRMATION_REQUIRED");
  }
  assert.equal(storage.dump(), before);
  assert.equal(store.getSession().authenticated, false);
});

test("显式修复只补齐内置登录场景，全部业务数据保持原样，三角色可重新登录", () => {
  const storage = legacyStorage();
  const before = read(storage);
  const store = createPrototypeStore(storage);
  assert.deepEqual(store.repairDemoLogin(true), { ok: true, repairedCount: 4 });
  const expected = structuredClone(before);
  for (const account of expected.accounts.filter(item => item.status === "available")) {
    account.loginScenario = "INITIAL";
    account.authenticationRevision = 2;
  }
  assert.deepEqual(read(storage), expected);
  assert.equal(storage.getItem(QUERY_SESSION_KEY), null);
  assert.equal(store.needsDemoLoginRepair(), false);
  assert.equal(store.repairDemoLogin(true).repairedCount, 0);
  assert.equal(store.loginConsole({ username: "platform.admin.demo", password: "demo-only" }).screen, "platform-overview");
  assert.equal(store.loginConsole({ username: "qiao.demo", password: "demo-only" }).screen, "tenant-overview");
  assert.equal(createPrototypeStore(storage).loginQuery({ tenantCode: "spruce-demo", username: "shen.demo", password: "demo-only" }).screen, "assistants");
  assert.doesNotMatch(storage.dump(), /demo-only|Initial-only|Reset-only/);
});

test("修复不得覆盖重置、停用、改名、非法配置或自建账户，也不重新授予角色", () => {
  const storage = legacyStorage();
  const snapshot = read(storage);
  snapshot.accounts.find(item => item.id === "account-qiao").authenticationRevision = 2;
  snapshot.accounts.find(item => item.id === "account-lin").loginName = "renamed.demo";
  snapshot.accounts.find(item => item.id === "account-shen").loginScenario = "CORRUPT";
  snapshot.accounts.find(item => item.id === "account-platform-admin").platformAdministrator = false;
  snapshot.accounts.push({ id: "custom", loginName: "custom.demo", status: "available" });
  storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  const store = createPrototypeStore(storage);
  assert.equal(store.repairDemoLogin(true).repairedCount, 1);
  const expected = structuredClone(snapshot);
  Object.assign(expected.accounts.find(item => item.id === "account-platform-admin"), { loginScenario: "INITIAL", authenticationRevision: 2 });
  assert.deepEqual(read(storage), expected);
  assert.equal(store.loginConsole({ username: "platform.admin.demo", password: "demo-only" }).ok, false);
});

test("读取损坏或写入失败时不回落到默认数据，保留原快照并允许重试", () => {
  const memory = legacyStorage();
  let fail = true;
  const store = createPrototypeStore({ ...memory, setItem(key, value) {
    if (fail) throw Error("合成写入失败");
    memory.setItem(key, value);
  } });
  const before = memory.dump();
  assert.equal(store.repairDemoLogin(true).reason, "STORAGE_UNAVAILABLE");
  assert.equal(memory.dump(), before);
  assert.equal(store.needsDemoLoginRepair(), true);
  fail = false;
  assert.equal(store.repairDemoLogin(true).ok, true);
  for (const raw of ["{broken", "null", '{"schemaVersion":99,"accounts":[]}']) {
    memory.setItem(PROTOTYPE_STATE_KEY, raw);
    assert.equal(store.needsDemoLoginRepair(), false);
    assert.equal(store.repairDemoLogin(true).ok, false);
    assert.equal(memory.getItem(PROTOTYPE_STATE_KEY), raw);
  }
});

test("确认前其他页签已经重置的账户不被覆盖，历史会话不会随修复复活", () => {
  const storage = legacyStorage();
  const store = createPrototypeStore(storage);
  const current = read(storage);
  Object.assign(current.accounts.find(item => item.id === "account-qiao"), { loginScenario: "ROTATED", authenticationRevision: 2 });
  current.tenantSetup.organizationName = "另一标签页刚保存的草稿";
  storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(current));
  storage.setItem(QUERY_SESSION_KEY, JSON.stringify({ authenticated: true, tenantId: null, accountId: "account-platform-admin", accessChannel: "CONSOLE", authenticationRevision: 1, epoch: 1 }));
  assert.equal(store.repairDemoLogin(true).repairedCount, 3);
  assert.equal(store.getSession().authenticated, false);
  assert.equal(read(storage).tenantSetup.organizationName, current.tenantSetup.organizationName);
  assert.equal(store.loginConsole({ username: "qiao.demo", password: "demo-only" }).ok, false);
  assert.equal(store.loginConsole({ username: "qiao.demo", password: "Reset-only-2026!" }).ok, true);
});

test("当前新数据与已有有效口令不显示修复入口，错误口令仍被拒绝", () => {
  const store = createPrototypeStore();
  assert.equal(store.needsDemoLoginRepair(), false);
  assert.equal(store.loginConsole({ username: "platform.admin.demo", password: "wrong" }).ok, false);
});
