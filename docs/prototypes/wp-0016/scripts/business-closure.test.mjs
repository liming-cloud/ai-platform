import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { renderInventoryPage } from "../full-platform.js";

const readJson = (name) =>
  JSON.parse(readFileSync(new URL(`../${name}`, import.meta.url), "utf8"));
const inventory = readJson("page-inventory.json");
const fixtures = readJson("page-fixtures.json").pages;
const tenantFixture = readJson("fixtures.json").tenant;
const pageEntries = inventory.groups.flatMap((group) =>
  group.pages.map((page) => ({ group, page })),
);

function entry(screen) {
  const found = pageEntries.find((item) => item.page.screen === screen);
  assert.ok(found, `清单缺少页面：${screen}`);
  return found;
}

function render(screen, snapshot) {
  const { page, group } = entry(screen);
  return renderInventoryPage(
    page,
    group,
    fixtures[screen],
    "success",
    tenantFixture,
    snapshot,
  );
}

test("共享导航使用本地 Fluent SVG，并让详情页高亮父目录", async () => {
  const { renderConsoleNavigation } = await import("../navigation.js");
  const platform = renderConsoleNavigation({
    screen: "platform-model-deployment-detail",
    scope: "platform",
  });
  const tenant = renderConsoleNavigation({
    screen: "source-detail",
    scope: "tenant",
  });

  for (const html of [platform, tenant]) {
    assert.match(html, /<img[^>]+src="\.\/assets\/icons\/[^"]+\.svg"/);
    assert.doesNotMatch(html, /nav-abbr|emoji/);
  }
  assert.match(platform, />平台运行状态</);
  assert.match(platform, />备份与恢复</);
  assert.match(platform, /data-nav-screen="platform-model-connections"[^>]+aria-current="page"/);
  assert.match(tenant, /data-nav-screen="knowledge-sources"[^>]+aria-current="page"/);
  assert.match(tenant, /aria-label="治理"/);
});

test("文档、版本、重新处理和重建索引使用不同业务语义", () => {
  const source = render("source-detail");
  const sourcePage = entry("source-detail").page;
  assert.equal(sourcePage.primaryAction, "更新文档");
  assert.match(source, />文档详情</);
  assert.match(source, />更新内容</);
  assert.match(source, />重新处理</);
  assert.doesNotMatch(source, /创建新版本|来源版本详情|84\s*个文档/);

  const sourceList = render("knowledge-sources");
  assert.doesNotMatch(sourceList, /修订/);
  assert.match(sourceList, /版本/);

  const index = render("index-detail");
  assert.match(index, /重建索引/);
  assert.doesNotMatch(index, /更新内容|重新处理/);
});

test("租户建立要求有效组织、编码、合资格初始管理员和确认", async () => {
  const { createMemoryStorage, createPrototypeStore } = await import(
    "../prototype-state.js"
  );
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  store.reset();

  const rejected = store.createTenant({
    organizationName: "A",
    tenantCode: "Bad Code",
    administratorAccountId: "account-platform-admin",
    confirmed: false,
    password: "不得持久化",
  });
  assert.equal(rejected.ok, false);
  assert.deepEqual(
    new Set(rejected.errors.map((error) => error.field)),
    new Set(["organizationName", "tenantCode", "administratorAccountId", "confirmed"]),
  );
  assert.equal(store.getSnapshot().tenants.length, 1);

  const created = store.createTenant({
    organizationName: "青岚制造示例组织",
    tenantCode: "qinglan-demo",
    administratorAccountId: "account-qiao",
    confirmed: true,
    password: "不得持久化",
  });
  assert.equal(created.ok, true);
  assert.equal(created.tenant.organizationName, "青岚制造示例组织");
  assert.deepEqual(created.tenant.quotas, {
    knowledgeBases: 3,
    documentsPerKnowledgeBase: 1000,
    rawContentGbPerKnowledgeBase: 10,
    singleFileMb: 50,
    pastedTextMib: 1,
  });
  assert.equal(created.tenant.safetyBoundaryMutable, false);

  const reloaded = createPrototypeStore(storage).getSnapshot();
  assert.ok(reloaded.tenants.some((tenant) => tenant.code === "qinglan-demo"));
  assert.doesNotMatch(storage.dump(), /不得持久化|password|credential|secret/i);
});

test("添加普通成员支持资格拒绝、去重、停用恢复和刷新", async () => {
  const { createMemoryStorage, createPrototypeStore, DEFAULT_TENANT_ID } =
    await import("../prototype-state.js");
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  store.reset();

  assert.equal(
    store.addMember(DEFAULT_TENANT_ID, { accountId: "account-platform-admin" }).reason,
    "ACCOUNT_NOT_ELIGIBLE",
  );
  const added = store.addMember(DEFAULT_TENANT_ID, {
    accountId: "account-lin",
    conversationFeatures: ["assistantSelection", "conversation"],
    assistantIds: ["assistant-employee-service"],
  });
  assert.equal(added.ok, true);
  assert.equal(
    store.addMember(DEFAULT_TENANT_ID, { accountId: "account-lin" }).reason,
    "MEMBER_ALREADY_EXISTS",
  );
  assert.equal(
    store.setMemberStatus(DEFAULT_TENANT_ID, "account-qiao", "disabled").reason,
    "ADMINISTRATOR_READ_ONLY",
  );
  assert.equal(
    store.setMemberStatus(DEFAULT_TENANT_ID, "account-lin", "disabled").ok,
    true,
  );
  assert.equal(
    createPrototypeStore(storage).findMember(DEFAULT_TENANT_ID, "account-lin").status,
    "disabled",
  );
  assert.equal(
    store.setMemberStatus(DEFAULT_TENANT_ID, "account-lin", "active").ok,
    true,
  );
});

test("两级功能草稿取消不生效，保存与刷新生效，直接访问受交集约束", async () => {
  const { createMemoryStorage, createPrototypeStore, DEFAULT_TENANT_ID } =
    await import("../prototype-state.js");
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  store.reset();
  store.addMember(DEFAULT_TENANT_ID, {
    accountId: "account-lin",
    conversationFeatures: ["assistantSelection", "conversation"],
    assistantIds: ["assistant-employee-service"],
  });

  store.setTenantFeatureDraft(DEFAULT_TENANT_ID, "conversation", false);
  store.discardTenantFeatureDraft(DEFAULT_TENANT_ID);
  assert.equal(
    store.getSnapshot().tenants[0].availableFeatures.conversation,
    true,
  );

  store.setTenantFeatureDraft(DEFAULT_TENANT_ID, "conversation", false);
  assert.equal(store.commitTenantFeatureDraft(DEFAULT_TENANT_ID).ok, true);
  assert.equal(
    createPrototypeStore(storage).getEffectiveAccess(
      DEFAULT_TENANT_ID,
      "account-lin",
      { feature: "conversation" },
    ).allowed,
    false,
  );

  store.setMemberFeatureDraft(
    DEFAULT_TENANT_ID,
    "account-lin",
    "ownHistory",
    false,
  );
  store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, "account-lin");
  store.setTenantFeatureDraft(DEFAULT_TENANT_ID, "ownHistory", false);
  store.commitTenantFeatureDraft(DEFAULT_TENANT_ID);
  store.setTenantFeatureDraft(DEFAULT_TENANT_ID, "ownHistory", true);
  store.commitTenantFeatureDraft(DEFAULT_TENANT_ID);
  assert.equal(
    createPrototypeStore(storage).getEffectiveAccess(
      DEFAULT_TENANT_ID,
      "account-lin",
      { feature: "ownHistory" },
    ).allowed,
    false,
  );

  const assistantDecision = store.getEffectiveAccess(
    DEFAULT_TENANT_ID,
    "account-lin",
    {
      feature: "assistantSelection",
      assistantId: "assistant-support",
    },
  );
  assert.equal(assistantDecision.allowed, false);
  assert.equal(assistantDecision.reason, "OBJECT_NOT_AUTHORIZED");
});

test("平台功能保存投影到租户导航和普通用户直接访问", async () => {
  const {
    createMemoryStorage,
    createPrototypeStore,
    DEFAULT_TENANT_ID,
  } = await import("../prototype-state.js");
  const { renderConsoleNavigation } = await import("../navigation.js");
  const storage = createMemoryStorage();
  const store = createPrototypeStore(storage);
  store.reset();
  store.addMember(DEFAULT_TENANT_ID, {
    accountId: "account-lin",
    conversationFeatures: ["assistantSelection", "conversation"],
    assistantIds: ["assistant-employee-service"],
  });
  store.selectDemoAccount("account-lin");
  store.setTenantFeatureDraft(DEFAULT_TENANT_ID, "assistantDelivery", false);
  store.commitTenantFeatureDraft(DEFAULT_TENANT_ID);

  const snapshot = createPrototypeStore(storage).getSnapshot();
  const nav = renderConsoleNavigation({
    screen: "assistants-directory",
    scope: "tenant",
    snapshot,
  });
  assert.doesNotMatch(nav, /data-nav-screen="assistants-directory"/);
  store.setTenantFeatureDraft(DEFAULT_TENANT_ID, "assistantDelivery", true);
  store.setTenantFeatureDraft(DEFAULT_TENANT_ID, "assistantSelection", false);
  store.commitTenantFeatureDraft(DEFAULT_TENANT_ID);
  assert.equal(
    store.authorizeScreen("assistants", DEFAULT_TENANT_ID, "account-lin").allowed,
    false,
  );
});

test("平台运行状态支持处置记录以及连续重新检查结果", async () => {
  const { createMemoryStorage, createPrototypeStore } = await import(
    "../prototype-state.js"
  );
  const store = createPrototypeStore(createMemoryStorage());
  store.reset();
  assert.equal(store.selectHealthIncident("incident-retrieval-latency").ok, true);
  assert.equal(
    store.recordHealthAction(
      "incident-retrieval-latency",
      "暂停受影响批次并保留当前活动索引",
    ).ok,
    true,
  );
  assert.equal(store.recheckHealth("incident-retrieval-latency").status, "degraded");
  assert.equal(store.recheckHealth("incident-retrieval-latency").status, "recovered");
  const snapshot = store.getSnapshot();
  const html = render("platform-health", snapshot);
  assert.match(html, /检索链路延迟/);
  assert.match(html, /暂停受影响批次并保留当前活动索引/);
  assert.match(html, /已恢复/);
  assert.doesNotMatch(html, /Endpoint|厂商原始响应|租户知识正文/);
});

test("恢复演练限定隔离目标并覆盖完整性、进度、报告和失败重试", async () => {
  const { createMemoryStorage, createPrototypeStore } = await import(
    "../prototype-state.js"
  );
  const store = createPrototypeStore(createMemoryStorage());
  store.reset();
  assert.equal(store.selectRecoveryBackup("backup-verified").stage, "selected");
  assert.equal(store.runIntegrityCheck().stage, "readyToConfirm");
  assert.equal(store.confirmRecovery().stage, "running");
  assert.equal(store.advanceRecovery().stage, "running");
  assert.equal(store.advanceRecovery().stage, "reported");
  assert.equal(store.getSnapshot().recovery.target, "固定隔离演练环境");

  store.resetRecovery();
  store.selectRecoveryBackup("backup-check-fails");
  assert.equal(store.runIntegrityCheck().stage, "failed");
  assert.equal(store.retryRecovery().stage, "selected");

  const html = render("backup-recovery", store.getSnapshot());
  assert.match(html, /固定隔离演练环境/);
  assert.doesNotMatch(html, /覆盖生产|发布回滚/);
});

test("全 68 页操作账本按范围计算缺口，技术预验不替代人工确认", () => {
  const ledger = readJson("ACTION-LEDGER.json");
  assert.equal(ledger.pages.length, 68);
  assert.deepEqual(
    new Set(ledger.pages.map((page) => page.screen)),
    new Set(pageEntries.map(({ page }) => page.screen)),
  );
  const incomplete = ledger.pages.filter(page => page.phase === "M6").flatMap(page => page.controls)
    .filter(control => !control.acceptanceExcluded && ["unimplemented", "partialSimulation", "pendingDecision"].includes(control.status));
  assert.equal(ledger.summary.unclosedControls, incomplete.length);
  if (ledger.summary.readyForFullAcceptance) {
    assert.equal(incomplete.length, 0);
    assert.equal(ledger.summary.allControlsEnumerated, true);
    assert.equal(ledger.acceptanceAudit.technicalStatus, "passed");
    assert.equal(ledger.acceptanceAudit.blockingFindingGroups, 0);
  }
  assert.ok(
    ledger.pages.some((page) =>
      page.controls.some((control) => control.status === "unimplemented"),
    ),
  );
  assert.ok(
    ledger.pages.some((page) =>
      page.controls.some((control) => control.status === "pendingDecision"),
    ),
  );
  assert.ok(
    ledger.pages.every((page) => page.controls.some((control) => control.label)),
  );
});
