import test from "node:test";
import assert from "node:assert/strict";
import { createMemoryStorage, createPrototypeStore } from "../prototype-state.js";

const ACTOR = "account-platform-admin";
const INITIAL_PASSWORD = "Initial-only-2026!";
const RESET_PASSWORD = "Reset-only-2026!";

test("平台建立人员账户并刷新恢复，明文口令不进入状态或存储", () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  const result = store.createAccount(
    {
      loginName: "zhou.demo",
      password: INITIAL_PASSWORD,
      passwordConfirmation: INITIAL_PASSWORD,
    },
    ACTOR,
  );

  assert.equal(result.ok, true);
  assert.equal(result.account.loginName, "zhou.demo");
  assert.equal(result.account.platformAdministrator, false);
  assert.equal(result.account.authenticationRevision, 1);
  assert.equal(createPrototypeStore(storage).getSnapshot().accounts.at(-1).loginName, "zhou.demo");
  assert.doesNotMatch(JSON.stringify(result), /Initial-only-2026/);
  assert.doesNotMatch(storage.dump(), /Initial-only-2026|password|credential|secret/i);
});

test("账户建立拒绝越权、非法或重复登录名及不一致口令", () => {
  const store = createPrototypeStore(createMemoryStorage());
  const before = store.getSnapshot();

  assert.equal(store.createAccount({ loginName: "zhou.demo", password: "x", passwordConfirmation: "x" }, "account-lin").reason, "FORBIDDEN");
  const invalid = store.createAccount(
    { loginName: "Zhou Demo", password: INITIAL_PASSWORD, passwordConfirmation: "different" },
    ACTOR,
  );
  assert.equal(invalid.ok, false);
  assert.deepEqual(new Set(invalid.errors.map(error => error.field)), new Set(["loginName", "passwordConfirmation"]));
  assert.equal(
    store.createAccount(
      { loginName: "qiao.demo", password: INITIAL_PASSWORD, passwordConfirmation: INITIAL_PASSWORD },
      ACTOR,
    ).reason,
    "LOGIN_NAME_CONFLICT",
  );
  assert.deepEqual(store.getSnapshot(), before);
});

test("密码重置只允许其他账户，推进认证修订且不持久化口令", () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  assert.equal(
    store.resetAccountPassword(
      "account-platform-admin",
      { password: RESET_PASSWORD, passwordConfirmation: RESET_PASSWORD, revokeSessions: true },
      ACTOR,
    ).reason,
    "SELF_RESET_FORBIDDEN",
  );
  assert.equal(
    store.resetAccountPassword(
      "account-lin",
      { password: RESET_PASSWORD, passwordConfirmation: "different", revokeSessions: true },
      ACTOR,
    ).ok,
    false,
  );

  const reset = store.resetAccountPassword(
    "account-lin",
    { password: RESET_PASSWORD, passwordConfirmation: RESET_PASSWORD, revokeSessions: true },
    ACTOR,
  );
  assert.equal(reset.ok, true);
  assert.equal(reset.account.authenticationRevision, 2);
  assert.equal(createPrototypeStore(storage).getSnapshot().accounts.find(account => account.id === "account-lin").authenticationRevision, 2);
  assert.doesNotMatch(storage.dump(), /Reset-only-2026|password|credential|secret/i);
});

test("平台管理员分配和账户停用遵守人员范围、操作者与最后管理员边界", () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);

  assert.equal(store.assignPlatformAdministrator("account-qiao", ACTOR).reason, "ACCOUNT_ASSIGNED_TO_TENANT");
  assert.equal(store.assignPlatformAdministrator("account-lin", "account-qiao").reason, "FORBIDDEN");
  assert.equal(store.assignPlatformAdministrator("account-lin", ACTOR).ok, true);
  assert.equal(store.getSnapshot().accounts.find(account => account.id === "account-lin").platformAdministrator, true);

  assert.equal(store.setAccountStatus(ACTOR, "suspended", ACTOR).reason, "SELF_MUTATION_FORBIDDEN");
  assert.equal(store.setAccountStatus("account-lin", "suspended", ACTOR).ok, true);
  assert.equal(store.setAccountStatus("account-lin", "available", ACTOR).ok, true);
  assert.equal(createPrototypeStore(storage).getSnapshot().accounts.find(account => account.id === "account-lin").status, "available");
});

test("账户建立和密码重置在存储失败时回滚", () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore({
    ...storage,
    setItem() {
      throw new Error("合成存储故障");
    },
  });
  const before = store.getSnapshot();

  assert.equal(
    store.createAccount(
      { loginName: "zhou.demo", password: INITIAL_PASSWORD, passwordConfirmation: INITIAL_PASSWORD },
      ACTOR,
    ).reason,
    "STORAGE_UNAVAILABLE",
  );
  assert.equal(
    store.resetAccountPassword(
      "account-lin",
      { password: RESET_PASSWORD, passwordConfirmation: RESET_PASSWORD, revokeSessions: true },
      ACTOR,
    ).reason,
    "STORAGE_UNAVAILABLE",
  );
  assert.deepEqual(store.getSnapshot(), before);
});
