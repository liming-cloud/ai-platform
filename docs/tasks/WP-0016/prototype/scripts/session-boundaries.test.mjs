import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { createMemoryStorage, createPrototypeStore, DEFAULT_TENANT_ID } from "../prototype-state.js";

const fixtures = JSON.parse(readFileSync(new URL("../fixtures.json", import.meta.url), "utf8"));
const memberLogin = { tenantCode: "spruce-demo", username: "shen.demo", password: "demo-only" };
const platformLogin = { username: "platform.admin.demo", password: "demo-only" };
const tenantLogin = { username: "qiao.demo", password: "demo-only" };
const assistantId = "assistant-employee-service";
const projection = (store, id) => store.queryProjection(fixtures, { conversationId: id });

test("管理端拒绝错误口令与普通成员，正确身份进入唯一角色范围", () => {
  const store = createPrototypeStore();
  for (const username of ["platform.admin.demo", "qiao.demo", "lin.demo", "unknown.demo"]) {
    assert.equal(store.loginConsole({ username, password: "wrong-demo-password" }).ok, false);
  }
  assert.equal(store.loginConsole({ username: "lin.demo", password: "demo-only" }).ok, false);
  assert.equal(store.loginConsole(platformLogin).screen, "platform-overview");
  assert.equal(store.authorizeSession("platform-accounts", "platform").allowed, true);
  assert.equal(store.authorizeSession("knowledge-directory", "tenant").allowed, false);
  assert.equal(store.authorizeSession("assistants", "query").allowed, false);
  assert.equal(store.loginConsole(tenantLogin).screen, "tenant-overview");
  assert.equal(store.authorizeSession("knowledge-directory", "tenant").allowed, true);
  assert.equal(store.authorizeSession("platform-accounts", "platform").allowed, false);
});

test("匿名、普通成员和身份不匹配的管理写操作失败关闭", () => {
  const store = createPrototypeStore();
  assert.equal(store.authorizeSession("knowledge-directory", "tenant").allowed, false);
  store.loginQuery(memberLogin);
  assert.equal(store.authorizeSession("knowledge-directory", "tenant").allowed, false);
  assert.equal(store.authorizeSession("tenant-members", "tenant").allowed, false);
  assert.equal(store.createKnowledgeBase(DEFAULT_TENANT_ID, { name: "普通成员不应建立" }, store.getSession().accountId).ok, false);
});

test("当前身份刷新恢复，口令不持久化，退出后旧接收结果不能恢复", () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  store.loginQuery(memberLogin);
  const { conversation } = store.createQueryConversation(fixtures, assistantId);
  const { run } = store.startQueryRun(fixtures, conversation.id, "年假是否支持结转？");
  const epoch = store.getSession().epoch;
  store.loginConsole(platformLogin);
  assert.equal(store.advanceQueryRun(fixtures, conversation.id, run.id, { expectedEpoch: epoch }).ok, false);
  const reopened = createPrototypeStore(storage);
  assert.equal(reopened.authorizeSession("platform-accounts", "platform").allowed, true);
  assert.deepEqual(projection(reopened, conversation.id).messages, []);
  reopened.logoutSession();
  assert.equal(createPrototypeStore(storage).getSession().authenticated, false);
  assert.doesNotMatch(JSON.stringify(store.getSnapshot()), /demo-only|wrong-demo-password/);
});

test("管理身份登录保存失败不能替换现有普通成员身份", () => {
  const memory = createMemoryStorage();
  let failing = false;
  const storage = {
    getItem: key => memory.getItem(key),
    setItem: (key, value) => { if (failing) throw new Error("合成存储故障"); memory.setItem(key, value); },
    removeItem: key => memory.removeItem(key),
  };
  const store = createPrototypeStore(storage);
  store.loginQuery(memberLogin);
  failing = true;
  assert.equal(store.loginConsole(platformLogin).ok, false);
  assert.equal(store.getSession().accountId, "account-shen");
});

test("只在登录记录写入失败时也不能改变既有聊天快照", () => {
  const memory = createMemoryStorage();
  let failing = false;
  const storage = {
    getItem: key => memory.getItem(key),
    setItem: (key, value) => {
      if (failing && key === "wp0016-query-session-v1") throw new Error("合成登录记录故障");
      memory.setItem(key, value);
    },
    removeItem: key => memory.removeItem(key),
  };
  const store = createPrototypeStore(storage);
  store.loginQuery(memberLogin);
  const { conversation } = store.createQueryConversation(fixtures, assistantId);
  store.startQueryRun(fixtures, conversation.id, "年假是否支持结转？");
  const before = memory.getItem("wp0016-business-demo-v1");
  failing = true;
  assert.equal(store.loginConsole(platformLogin).ok, false);
  assert.equal(memory.getItem("wp0016-business-demo-v1"), before);
  assert.equal(createPrototypeStore(storage).getSession().accountId, "account-shen");
});

test("关闭历史后新会话可问答及刷新，旧会话和重新登录后的恢复仍拒绝", () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  store.setMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen", "ownHistory", false);
  store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen");
  store.loginQuery(memberLogin);
  const { conversation } = store.createQueryConversation(fixtures, assistantId);
  assert.equal(projection(store, conversation.id).conversationAllowed, true);
  assert.deepEqual(projection(store, conversation.id).history, []);
  const { run } = store.startQueryRun(fixtures, conversation.id, "年假是否支持结转？");
  assert.ok(run);
  assert.equal(store.advanceQueryRun(fixtures, conversation.id, run.id, { chunkSize: 1000 }).ok, true);
  assert.equal(projection(createPrototypeStore(storage), conversation.id).conversationAllowed, true);
  assert.equal(projection(store, "cv-20260902-01").conversationAllowed, false);
  const second = store.createQueryConversation(fixtures, assistantId).conversation;
  assert.equal(projection(store, conversation.id).conversationAllowed, false);
  assert.equal(projection(store, second.id).conversationAllowed, true);
  store.logoutSession();
  store.loginQuery(memberLogin);
  assert.equal(projection(store, second.id).conversationAllowed, false);
  assert.deepEqual(projection(store, second.id).messages, []);
});

test("关闭历史不扩大助手、对话和引用授权", () => {
  const store = createPrototypeStore();
  for (const feature of ["ownHistory", "authorizedCitations"]) {
    store.setMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen", feature, false);
  }
  store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen");
  store.loginQuery(memberLogin);
  const { conversation } = store.createQueryConversation(fixtures, assistantId);
  assert.equal(store.loadEarlierQueryMessages(fixtures, conversation.id).ok, false);
  const { run } = store.startQueryRun(fixtures, conversation.id, "年假是否支持结转？");
  assert.ok(run);
  store.advanceQueryRun(fixtures, conversation.id, run.id, { chunkSize: 1000 });
  assert.doesNotMatch(JSON.stringify(projection(store, conversation.id)), /cit-demo-leave/);
  store.setMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen", "conversation", false);
  store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen");
  assert.equal(projection(store, conversation.id).conversationAllowed, false);
  assert.deepEqual(projection(store, conversation.id).messages, []);
});

test("普通用户没有已授权发布助手时不能建立问答登录态", () => {
  const store = createPrototypeStore();
  store.setMemberAssistantDraft(DEFAULT_TENANT_ID, "account-shen", []);
  store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen");
  assert.equal(store.loginQuery(memberLogin).ok, false);
  assert.equal(store.getSession().authenticated, false);
});
