import test from "node:test";
import assert from "node:assert/strict";
import { createPrototypeStore, createMemoryStorage, DEFAULT_TENANT_ID } from "../prototype-state.js";

const admin = "account-qiao";
test("模型、助手、评测详情从本页 URL 恢复，跨组织参数整组拒绝", () => {
  const store = createPrototypeStore();
  login(store);
  const context = { configurationId: "cfg-embedding", assistantVersionId: "asv-0013", evaluationRunId: "eval-run-v13" };
  assert.equal(store.applyDetailContext(context).ok, true);
  assert.equal(store.getWorkspaceSnapshot().models.selectedConfigurationId, "cfg-embedding");
  const before = store.getSnapshot();
  assert.equal(store.applyDetailContext({ configurationId: "cfg-chat", assistantVersionId: "missing" }).ok, false);
  assert.deepEqual(store.getSnapshot(), before);
  const tenant = secondTenant(store);
  login(store, tenant.code);
  assert.equal(store.applyDetailContext(context).ok, false);
});
function secondTenant(store) {
  return store.createTenant({ organizationName: "北辰服务组织", tenantCode: "beichen-demo", administrator: { loginName: 'beichen.admin', password: 'demo-only', passwordConfirmation: 'demo-only' }, confirmed: true }, 'account-platform-admin').tenant;
}
const login = (store, tenantCode = "spruce-demo") => store.loginConsole({ username: tenantCode === 'spruce-demo' ? 'qiao.demo' : 'beichen.admin', password: "demo-only", tenantCode });

test("新组织登录后只展示自己的空工作区，创建知识库不写入默认组织", () => {
  const storage = createMemoryStorage();
  let store = createPrototypeStore(storage);
  const tenant = secondTenant(store);
  assert.equal(login(store, tenant.code).ok, true);
  assert.deepEqual(store.getWorkspaceSnapshot().knowledgeBases, []);
  assert.deepEqual(store.getWorkspaceSnapshot().assistantDelivery.assistants, []);
  assert.deepEqual(store.getWorkspaceSnapshot().models.configurations, []);
  const created = store.createKnowledgeBase({ name: "北辰制度" }, tenant.administratorAccountId, tenant.id);
  assert.equal(created.ok, true);
  assert.equal(created.knowledgeBase.tenantId, tenant.id);
  store = createPrototypeStore(storage);
  assert.equal(store.getWorkspaceSnapshot().knowledgeBases[0].name, "北辰制度");
  assert.equal(store.getWorkspaceSnapshot().tenants.length, 1);
});

test("不同组织创建知识库产生全局唯一标识", () => {
  const store = createPrototypeStore();
  const tenant = secondTenant(store);
  const first = store.createKnowledgeBase({ name: "新增知识库甲" }, admin, DEFAULT_TENANT_ID);
  const second = store.createKnowledgeBase({ name: "新增知识库乙" }, tenant.administratorAccountId, tenant.id);
  assert.equal(first.ok && second.ok, true);
  assert.notEqual(first.knowledgeBase.id, second.knowledgeBase.id);
});

test("URL 对象上下文先校验归属，无效或跨租户标识不回落到首项", () => {
  const store = createPrototypeStore();
  const tenant = secondTenant(store);
  login(store, tenant.code);
  assert.equal(store.applyWorkspaceContext({ knowledgeBaseId: "kb-handbook" }).ok, false);
  assert.equal(store.applyWorkspaceContext({ knowledgeBaseId: "does-not-exist" }).ok, false);
  assert.equal(store.applyWorkspaceContext({ tenantId: DEFAULT_TENANT_ID }).ok, false);
  assert.deepEqual(store.getWorkspaceSnapshot().knowledgeBases, []);
});

test("两个标签页各按 URL 恢复知识库、来源与索引，读取不改写存储", () => {
  const storage = createMemoryStorage();
  const first = createPrototypeStore(storage);
  login(first);
  first.selectKnowledgeBase("kb-handbook");
  const other = createPrototypeStore(storage);
  other.selectKnowledgeBase("kb-support");
  first.reloadFromStorage();
  const before = storage.dump();
  assert.equal(first.applyWorkspaceContext({ knowledgeBaseId: "kb-handbook", sourceId: "src-policy-pdf", indexVersionId: "idx-v11" }).ok, true);
  assert.equal(first.getWorkspaceSnapshot().selectedKnowledgeBaseId, "kb-handbook");
  assert.equal(other.applyWorkspaceContext({ knowledgeBaseId: "kb-support" }).ok, true);
  assert.equal(other.getWorkspaceSnapshot().selectedKnowledgeBaseId, "kb-support");
  assert.equal(storage.dump(), before);
  assert.equal(first.applyWorkspaceContext({ knowledgeBaseId: "kb-support", sourceId: "src-policy-pdf" }).ok, false);
});

test("未认证访问不投影租户知识内容，平台会话不继承租户上下文", () => {
  const store = createPrototypeStore();
  assert.equal(store.applyWorkspaceContext({ knowledgeBaseId: "kb-handbook" }).ok, false);
  assert.equal(store.getWorkspaceSnapshot().knowledgeBases.length, 0);
  store.loginConsole({ username: "platform.admin.demo", password: "demo-only" });
  assert.equal(store.applyWorkspaceContext({ knowledgeBaseId: "kb-handbook" }).ok, false);
});
