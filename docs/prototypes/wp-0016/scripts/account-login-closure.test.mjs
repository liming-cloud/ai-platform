import test from "node:test";
import assert from "node:assert/strict";
import { createMemoryStorage, createPrototypeStore, DEFAULT_TENANT_ID, PROTOTYPE_STATE_KEY } from "../prototype-state.js";

const actor = "account-platform-admin";
const initial = "Initial-only-2026!";
const replacement = "Reset-only-2026!";
const input = password => ({ password, passwordConfirmation: password, revokeSessions: true });
const consoleInput = (username, password) => ({ username, password });
const queryInput = (username, password) => ({ tenantCode: "spruce-demo", username, password });
const create = store => store.createAccount({ loginName: "new.person", ...input(initial) }, actor);

test("重置后新演示口令可登录、旧口令失效，刷新与再次轮换保持一致", () => {
  const storage = createMemoryStorage();
  let store = createPrototypeStore(storage);
  assert.equal(store.resetAccountPassword("account-qiao", input(replacement), actor).ok, true);
  assert.equal(store.loginConsole(consoleInput("qiao.demo", "demo-only")).ok, false);
  assert.equal(store.loginConsole(consoleInput("qiao.demo", replacement)).ok, true);
  store = createPrototypeStore(storage);
  assert.equal(store.loginConsole(consoleInput("qiao.demo", replacement)).ok, true);
  assert.equal(store.resetAccountPassword("account-qiao", input(initial), actor).ok, true);
  assert.equal(store.getSession().authenticated, false);
  assert.equal(store.loginConsole(consoleInput("qiao.demo", replacement)).ok, false);
  assert.equal(store.loginConsole(consoleInput("qiao.demo", initial)).ok, true);
  assert.doesNotMatch(storage.dump(), /Initial-only|Reset-only|demo-only|password|credential|secret/i);
});

test("创建账户不隐含管理权限，分配平台角色后首次登录进入本人范围", () => {
  const storage = createMemoryStorage();
  let store = createPrototypeStore(storage);
  const created = create(store);
  assert.equal(created.ok, true);
  assert.equal(store.loginConsole(consoleInput("new.person", initial)).ok, false);
  assert.equal(store.assignPlatformAdministrator(created.account.id, actor).ok, true);
  store = createPrototypeStore(storage);
  assert.equal(store.loginConsole(consoleInput("new.person", initial)).screen, "platform-overview");
  assert.equal(store.getSession().accountId, created.account.id);
  assert.equal(store.loginConsole(consoleInput("new.person", "demo-only")).ok, false);
  assert.equal(store.authorizeSession("knowledge-directory", "tenant").allowed, false);
});

test("普通成员使用目录中同一登录名，账户口令与成员助手授权同时成立才可登录", () => {
  const store = createPrototypeStore();
  assert.equal(store.loginQuery(queryInput("shen.demo", "demo-only")).ok, true);
  assert.equal(store.loginQuery(queryInput("demo.user", "demo-only")).ok, false);
  const created = create(store);
  assert.equal(store.loginQuery(queryInput("new.person", initial)).ok, false);
  store.addMember(DEFAULT_TENANT_ID, { accountId: created.account.id, conversationFeatures: ["assistantSelection", "conversation"], assistantIds: [] });
  assert.equal(store.loginQuery(queryInput("new.person", initial)).reason, "NO_AUTHORIZED_ASSISTANT");
  store.setMemberAssistantDraft(DEFAULT_TENANT_ID, created.account.id, ["assistant-employee-service"]);
  store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, created.account.id);
  assert.equal(store.loginQuery(queryInput("new.person", initial)).ok, true);
  assert.equal(store.getSession().accountId, created.account.id);
  assert.equal(store.loginConsole(consoleInput("new.person", initial)).ok, false);
  assert.equal(store.loginQuery({ ...queryInput("new.person", initial), tenantCode: "unknown" }).ok, false);
  store.resetAccountPassword(created.account.id, input(replacement), actor);
  assert.equal(store.loginQuery(queryInput("new.person", initial)).ok, false);
  assert.equal(store.loginQuery(queryInput("new.person", replacement)).ok, true);
});

test("原型拒绝非公开口令与畸形输入，不能保存或回显输入材料", () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  const before = store.getSnapshot();
  for (const value of ["not-a-public-fixture", ` ${initial}`, initial.toLowerCase(), { value: initial }, null]) {
    const result = store.createAccount({ loginName: "new.person", ...input(value) }, actor);
    assert.equal(result.ok, false);
    assert.equal(store.resetAccountPassword("account-qiao", input(value), actor).ok, false);
    assert.doesNotMatch(JSON.stringify(result), /not-a-public-fixture|Initial-only/);
  }
  assert.deepEqual(store.getSnapshot(), before);
  assert.equal(storage.dump(), "");
});

test("保存失败不切换口令、不撤销原身份，修复存储后可以重试", () => {
  const memory = createMemoryStorage();
  let fail = false;
  const store = createPrototypeStore({ ...memory, setItem(key, value) {
    if (fail && key === PROTOTYPE_STATE_KEY) throw Error("合成存储失败");
    memory.setItem(key, value);
  } });
  assert.equal(store.loginConsole(consoleInput("qiao.demo", "demo-only")).ok, true);
  fail = true;
  assert.equal(store.resetAccountPassword("account-qiao", input(replacement), actor).reason, "STORAGE_UNAVAILABLE");
  assert.equal(store.getSession().authenticated, true);
  assert.equal(store.loginConsole(consoleInput("qiao.demo", replacement)).ok, false);
  assert.equal(create(store).ok, false);
  fail = false;
  assert.equal(store.resetAccountPassword("account-qiao", input(replacement), actor).ok, true);
  assert.equal(store.loginConsole(consoleInput("qiao.demo", replacement)).ok, true);
});

test("另一标签页重置后，通知到达前旧身份和旧口令都失败关闭", () => {
  const storage = createMemoryStorage();
  const user = createPrototypeStore(storage);
  user.loginConsole(consoleInput("qiao.demo", "demo-only"));
  const admin = createPrototypeStore(storage);
  assert.equal(admin.resetAccountPassword("account-qiao", input(replacement), actor).ok, true);
  assert.equal(user.getSession().authenticated, false);
  assert.equal(user.loginConsole(consoleInput("qiao.demo", "demo-only")).ok, false);
  assert.equal(user.loginConsole(consoleInput("qiao.demo", replacement)).ok, true);
});

test("未接通的其他租户工作区不能回落到云杉示例组织", () => {
  const store = createPrototypeStore();
  const created = create(store);
  store.createTenant({ organizationName: "新增组织", tenantCode: "new-org", administratorAccountId: created.account.id, confirmed: true });
  const result = store.loginConsole(consoleInput("new.person", initial));
  assert.equal(result.reason, "WORKSPACE_NOT_READY");
  assert.equal(store.getSession().authenticated, false);
});

test("管理员拥有多个组织时必须明确选择，只返回本人已授权组织", () => {
  const store = createPrototypeStore();
  store.createTenant({ organizationName: "第二个组织", tenantCode: "second-org", administratorAccountId: "account-qiao", confirmed: true });
  const login = consoleInput("qiao.demo", "demo-only");
  assert.equal(store.loginConsole({ ...login, password: "wrong" }).tenants, undefined);
  const pending = store.loginConsole(login);
  assert.equal(pending.reason, "TENANT_SELECTION_REQUIRED");
  assert.deepEqual(pending.tenants.map(item => item.code), ["spruce-demo", "second-org"]);
  assert.equal(store.getSession().authenticated, false);
  assert.equal(store.loginConsole({ ...login, tenantCode: "second-org" }).reason, "WORKSPACE_NOT_READY");
  assert.equal(store.loginConsole({ ...login, tenantCode: "unknown" }).ok, false);
  assert.equal(store.loginConsole({ ...login, tenantCode: "spruce-demo" }).ok, true);
  assert.equal(store.getSession().tenantId, DEFAULT_TENANT_ID);
});

test("旧快照缺少场景或读取损坏时拒绝认证，不悄悄恢复默认口令", () => {
  const storage = createMemoryStorage();
  const snapshot = createPrototypeStore().getSnapshot();
  for (const account of snapshot.accounts) delete account.loginScenario;
  storage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  let store = createPrototypeStore(storage);
  assert.equal(store.loginConsole(consoleInput("platform.admin.demo", "demo-only")).ok, false);
  assert.equal(store.loginQuery(queryInput("shen.demo", "demo-only")).ok, false);
  storage.setItem(PROTOTYPE_STATE_KEY, "{broken");
  store = createPrototypeStore(storage);
  assert.equal(store.loginConsole(consoleInput("platform.admin.demo", "demo-only")).ok, false);
  assert.equal(store.getSession().authenticated, false);
});
