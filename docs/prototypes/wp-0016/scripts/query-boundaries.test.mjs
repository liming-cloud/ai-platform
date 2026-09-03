import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { createMemoryStorage, createPrototypeStore, DEFAULT_TENANT_ID } from "../prototype-state.js";
const fixtures = JSON.parse(readFileSync(new URL("../fixtures.json", import.meta.url), "utf8"));
function started(storage = createMemoryStorage()) {
  const store = createPrototypeStore(storage);
  assert.equal(store.loginQuery({ tenantCode: "spruce-demo", username: "demo.user", password: "demo-only" }).ok, true);
  const { conversation } = store.createQueryConversation(fixtures, "assistant-employee-service");
  const { run } = store.startQueryRun(fixtures, conversation.id, "未休 7 天如何处理？");
  return { store, conversation, run };
}
function complete(context) {
  return context.store.advanceQueryRun(fixtures, context.conversation.id, context.run.id, {
    chunkSize: 1000, expectedEpoch: context.store.getQuerySession().epoch,
  });
}
function project(context) {
  return context.store.queryProjection(fixtures, { conversationId: context.conversation.id });
}
test("引用权限撤销后投影的所有分支都不夹带证据正文", () => {
  const context = started();
  complete(context);
  context.store.setMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen", "authorizedCitations", false);
  context.store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen");
  const projection = project(context);
  assert.ok(projection.messages.length > 0);
  assert.doesNotMatch(JSON.stringify(projection), /cit-demo-leave|结转上限为五个工作日/);
});
test("对话权限撤销后投影的嵌套对象不返回完整答案", () => {
  const context = started();
  complete(context);
  context.store.setMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen", "conversation", false);
  context.store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen");
  const projection = project(context);
  assert.equal(projection.conversationAllowed, false);
  assert.deepEqual(projection.messages, []);
  assert.doesNotMatch(JSON.stringify(projection), /直属负责人和人力资源确认安排|cit-demo-leave/);
});
test("退出时即使持久化失败也不能让旧接收器继续", () => {
  const memory = createMemoryStorage();
  let failing = false;
  const storage = {
    getItem: key => memory.getItem(key),
    setItem: (key, value) => { if (failing) throw new Error("合成存储故障"); memory.setItem(key, value); },
    removeItem: key => memory.removeItem(key),
  };
  const context = started(storage);
  const epoch = context.store.getQuerySession().epoch;
  failing = true;
  context.store.logoutQuery();
  assert.equal(context.store.getQuerySession().authenticated, false);
  const reopened = createPrototypeStore(storage);
  assert.equal(reopened.getQuerySession().authenticated, false);
  assert.equal(reopened.getSnapshot().query.conversations.length, 1);
  failing = false;
  assert.equal(context.store.advanceQueryRun(fixtures, context.conversation.id, context.run.id, { expectedEpoch: epoch }).ok, false);
});
test("接收中的会话拒绝开启不同问题的另一轮", () => {
  const context = started();
  const second = context.store.startQueryRun(fixtures, context.conversation.id, "请预测明年的黄金价格");
  assert.equal(second.ok, false);
  assert.equal(project(context).messages.filter(message => message.role === "user").length, 1);
});
test("年假的一般问题不虚构用户有七天未休", () => {
  const context = started();
  complete(context);
  const second = context.store.startQueryRun(fixtures, context.conversation.id, "年假是否支持结转？");
  context.store.advanceQueryRun(fixtures, context.conversation.id, second.run.id, { chunkSize: 1000, expectedEpoch: context.store.getQuerySession().epoch });
  assert.doesNotMatch(project(context).messages.at(-1).content, /你有 7 天|其余 2 天/);
});
test("接收中退出后重新登录会暂停旧轮次并允许原位继续", () => {
  const context = started();
  context.store.advanceQueryRun(fixtures, context.conversation.id, context.run.id, { chunkSize: 12, expectedEpoch: context.store.getQuerySession().epoch });
  context.store.logoutQuery();
  context.store.loginQuery({ tenantCode: "spruce-demo", username: "demo.user", password: "demo-only" });
  assert.equal(project(context).activeRun.status, "PAUSED");
  assert.equal(context.store.resumeQueryRun(fixtures, context.conversation.id, context.run.id).ok, true);
  assert.equal(complete(context).run.status, "COMPLETED");
  assert.equal(project(context).messages.filter(message => message.role === "user").length, 1);
});
