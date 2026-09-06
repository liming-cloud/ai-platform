import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  createMemoryStorage,
  createPrototypeStore,
  DEFAULT_TENANT_ID,
  PROTOTYPE_STATE_KEY,
} from "../prototype-state.js";

const fixtures = JSON.parse(
  readFileSync(new URL("../fixtures.json", import.meta.url), "utf8"),
);

function login(store) {
  return store.loginQuery({
    tenantCode: "spruce-demo",
    username: "shen.demo",
    password: "demo-only",
  });
}

test("错误口令不建立会话且口令从不进入持久化", () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  assert.deepEqual(
    store.loginQuery({
      tenantCode: "spruce-demo",
      username: "shen.demo",
      password: "wrong-password",
    }),
    { ok: false, reason: "INVALID_CREDENTIALS" },
  );
  assert.equal(store.getSession().authenticated, false);
  assert.doesNotMatch(storage.dump(), /wrong-password|demo-only/);
  assert.equal(login(store).ok, true);
  assert.doesNotMatch(storage.dump(), /demo-only/);
});

test("新会话为空且固定助手版本与索引版本，会话之间相互隔离", () => {
  const store = createPrototypeStore(createMemoryStorage());
  assert.equal(login(store).ok, true);
  const first = store.createQueryConversation(fixtures, "assistant-employee-service");
  const second = store.createQueryConversation(fixtures, "assistant-employee-service");
  assert.equal(first.ok, true);
  assert.equal(second.ok, true);
  assert.notEqual(first.conversation.id, second.conversation.id);
  assert.equal(first.conversation.assistantVersionId, "asv-0012");
  assert.equal(first.conversation.indexVersionId, "idx-v11");
  assert.deepEqual(
    store.queryProjection(fixtures, {
      assistantId: "assistant-employee-service",
      conversationId: first.conversation.id,
    }).messages,
    [],
  );
});

test("接收中的同一问题可恢复且不会重复追加", () => {
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  assert.equal(login(store).ok, true);
  const { conversation } = store.createQueryConversation(
    fixtures,
    "assistant-employee-service",
  );
  const started = store.startQueryRun(
    fixtures,
    conversation.id,
    "未休 7 天如何处理？",
  );
  assert.equal(started.ok, true);
  assert.equal(started.run.status, "RECEIVING");
  const duplicate = store.startQueryRun(
    fixtures,
    conversation.id,
    "未休 7 天如何处理？",
  );
  assert.equal(duplicate.replayed, true);
  let advanced = duplicate;
  while (advanced.run.status === "RECEIVING") {
    advanced = store.advanceQueryRun(fixtures, conversation.id, started.run.id, {
      chunkSize: 32,
      expectedEpoch: store.getSession().epoch,
    });
  }
  assert.equal(advanced.run.status, "COMPLETED");

  const reloaded = createPrototypeStore(storage);
  const projection = reloaded.queryProjection(fixtures, {
    assistantId: "assistant-employee-service",
    conversationId: conversation.id,
  });
  assert.equal(
    projection.messages.filter((message) => message.content === "未休 7 天如何处理？").length,
    1,
  );
  assert.equal(projection.messages.at(-1).runState, "COMPLETED");
  assert.ok(projection.messages.at(-1).citations.length > 0);
  assert.equal(reloaded.logoutSession().ok, true);
  assert.equal(login(reloaded).ok, true);
  assert.equal(
    reloaded.queryProjection(fixtures, {
      conversationId: conversation.id,
    }).messages.filter((message) => message.content === "未休 7 天如何处理？").length,
    1,
  );
});

test("未知问题明确拒答且不复用年假答案", () => {
  const store = createPrototypeStore(createMemoryStorage());
  assert.equal(login(store).ok, true);
  const { conversation } = store.createQueryConversation(
    fixtures,
    "assistant-employee-service",
  );
  const started = store.startQueryRun(
    fixtures,
    conversation.id,
    "请预测明年的黄金价格",
  );
  let result = started;
  while (result.run.status === "RECEIVING") {
    result = store.advanceQueryRun(fixtures, conversation.id, started.run.id, {
      chunkSize: 80,
      expectedEpoch: store.getSession().epoch,
    });
  }
  assert.equal(result.run.status, "REFUSED");
  const messages = store.queryProjection(fixtures, {
    assistantId: "assistant-employee-service",
    conversationId: conversation.id,
  }).messages;
  assert.match(messages.at(-1).content, /无法|不能/);
  assert.doesNotMatch(messages.at(-1).content, /年假|7 天|人力资源/);
  assert.deepEqual(messages.at(-1).citations || [], []);
});

test("按事件序号加载更早消息会扩展同一会话而不生成第二套分页", () => {
  const store = createPrototypeStore(createMemoryStorage());
  assert.equal(login(store).ok, true);
  const before = store.queryProjection(fixtures, {
    conversationId: "cv-20260902-01",
  });
  assert.deepEqual(before.messages.map((message) => message.sequence), [41, 42, 43]);
  assert.equal(before.canLoadEarlier, true);
  assert.equal(
    store.loadEarlierQueryMessages(fixtures, "cv-20260902-01").ok,
    true,
  );
  const after = store.queryProjection(fixtures, {
    conversationId: "cv-20260902-01",
  });
  assert.deepEqual(after.messages.map((message) => message.sequence), [39, 40, 41, 42, 43]);
  assert.equal(after.canLoadEarlier, false);
});

test("暂停、失败和重试沿用原轮次，退出或撤权后旧接收器失效", () => {
  const store = createPrototypeStore(createMemoryStorage());
  assert.equal(login(store).ok, true);
  const { conversation } = store.createQueryConversation(
    fixtures,
    "assistant-employee-service",
  );
  const failed = store.startQueryRun(
    fixtures,
    conversation.id,
    "未休 7 天如何处理？",
    { mode: "failure" },
  );
  assert.equal(failed.run.status, "FAILED");
  const retried = store.retryQueryRun(fixtures, conversation.id, failed.run.id);
  assert.equal(retried.run.status, "RECEIVING");
  assert.equal(retried.run.userSequence, failed.run.userSequence);
  const stopped = store.stopQueryRun(fixtures, conversation.id, failed.run.id);
  assert.equal(stopped.run.status, "STOPPED");
  const resumed = store.resumeQueryRun(fixtures, conversation.id, failed.run.id);
  assert.equal(resumed.run.status, "RECEIVING");
  const disconnectedConversation = store.createQueryConversation(
    fixtures,
    "assistant-employee-service",
  ).conversation;
  const disconnected = store.startQueryRun(
    fixtures,
    disconnectedConversation.id,
    "未休 7 天如何处理？",
    { mode: "disconnect" },
  );
  assert.equal(disconnected.run.status, "PAUSED");
  assert.equal(
    store.resumeQueryRun(
      fixtures,
      disconnectedConversation.id,
      disconnected.run.id,
    ).run.status,
    "RECEIVING",
  );
  const epoch = store.getSession().epoch;
  store.logoutSession();
  assert.deepEqual(
    store.advanceQueryRun(fixtures, conversation.id, failed.run.id, {
      chunkSize: 20,
      expectedEpoch: epoch,
    }),
    { ok: false, reason: "AUTH_REQUIRED" },
  );

  assert.equal(login(store).ok, true);
  store.setMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen", "conversation", false);
  store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen");
  const denied = store.queryProjection(fixtures, {
    assistantId: "assistant-employee-service",
    conversationId: conversation.id,
  });
  assert.equal(denied.conversationAllowed, false);
  assert.deepEqual(denied.messages, []);
  assert.equal(
    store.advanceQueryRun(fixtures, conversation.id, failed.run.id, {
      chunkSize: 20,
      expectedEpoch: store.getSession().epoch,
    }).ok,
    false,
  );
});

test("跨标签退出后重新载入状态会阻止旧轮次继续", () => {
  const storage = createMemoryStorage();
  const firstTab = createPrototypeStore(storage);
  assert.equal(login(firstTab).ok, true);
  const { conversation } = firstTab.createQueryConversation(
    fixtures,
    "assistant-employee-service",
  );
  const started = firstTab.startQueryRun(
    fixtures,
    conversation.id,
    "未休 7 天如何处理？",
  );
  const oldEpoch = firstTab.getSession().epoch;
  const secondTab = createPrototypeStore(storage);
  assert.equal(secondTab.logoutSession().ok, true);
  firstTab.reloadFromStorage();
  assert.deepEqual(
    firstTab.advanceQueryRun(fixtures, conversation.id, started.run.id, {
      chunkSize: 20,
      expectedEpoch: oldEpoch,
    }),
    { ok: false, reason: "AUTH_REQUIRED" },
  );
});

test("持久化失败会回滚登录并返回可见失败语义", () => {
  const storage = {
    getItem() {
      return null;
    },
    setItem() {
      throw new Error("synthetic storage failure");
    },
    removeItem() {},
  };
  const store = createPrototypeStore(storage);
  assert.deepEqual(login(store), { ok: false, reason: "STORAGE_UNAVAILABLE" });
  assert.equal(store.getSession().authenticated, false);
  assert.equal(storage.getItem(PROTOTYPE_STATE_KEY), null);
});
