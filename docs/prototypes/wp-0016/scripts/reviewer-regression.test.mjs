import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { renderConsoleNavigation } from "../navigation.js";
import { renderInventoryPage } from "../full-platform.js";
import { createMemoryStorage, createPrototypeStore, DEFAULT_TENANT_ID } from "../prototype-state.js";

const read = (file) => JSON.parse(readFileSync(new URL(`../${file}`, import.meta.url), "utf8"));
const inventory = read("page-inventory.json");
const fixtures = read("fixtures.json");
const pageFixtures = read("page-fixtures.json").pages;
const entries = inventory.groups.flatMap((group) => group.pages.map((page) => ({ page, group })));
const fresh = () => createPrototypeStore(createMemoryStorage());
const loginQuery = (store) =>
  store.loginQuery({
    tenantCode: "spruce-demo",
    username: "shen.demo",
    password: "demo-only",
  });
const render = (screen, store) => {
  const { page, group } = entries.find((entry) => entry.page.screen === screen);
  return renderInventoryPage(page, group, pageFixtures[screen], "success", fixtures.tenant, store.getSnapshot());
};

test("租户用量页只读已保存资源配额，不提供提权修改入口", () => {
  const store = fresh();
  const quotas = store.getTenantQuotas(DEFAULT_TENANT_ID).quotas;
  store.saveTenantQuotas(DEFAULT_TENANT_ID, { ...quotas, knowledgeBases: 17 }, "platformAdministrator");
  const html = render("usage-quotas", store);
  assert.match(html, /知识库数量/);
  assert.match(html, /17 个/);
  assert.match(html, /刷新用量/);
  assert.doesNotMatch(html, /调整配额|data-edit-tenant|data-open-page-dialog|id="page-dialog"/);
});

test("租户暂不可用场景禁用整组创建和配置控件", () => {
  for (const screen of ["platform-tenant-create", "platform-tenant-detail"]) {
    const { page, group } = entries.find(entry => entry.page.screen === screen);
    const html = renderInventoryPage(page, group, pageFixtures[screen], "disabled", fixtures.tenant, fresh().getSnapshot());
    assert.match(html, /<fieldset disabled class="tenant-disabled-fields"/);
  }
});

test("全部管理页面默认导航恰有一个当前目录", () => {
  for (const { page, group } of entries.filter(({ page, group }) => group.shell === "console" && page.screen !== "console-login")) {
    const scope = ["platform-governance", "platform-operations"].includes(group.id) ? "platform" : "tenant";
    assert.equal((renderConsoleNavigation({ screen: page.screen, scope }).match(/aria-current="page"/g) || []).length, 1, page.screen);
  }
});

test("管理员助手管理与普通用户助手选择各用自己的功能开关", () => {
  const store = fresh();
  const visible = () => renderConsoleNavigation({ screen: "tenant-overview", scope: "tenant", snapshot: store.getSnapshot() }).includes('data-nav-screen="assistants-directory"');
  store.setTenantFeatureDraft(DEFAULT_TENANT_ID, "assistantDelivery", false);
  store.commitTenantFeatureDraft(DEFAULT_TENANT_ID);
  assert.equal(visible(), false);
  store.setTenantFeatureDraft(DEFAULT_TENANT_ID, "assistantDelivery", true);
  store.setTenantFeatureDraft(DEFAULT_TENANT_ID, "assistantSelection", false);
  store.commitTenantFeatureDraft(DEFAULT_TENANT_ID);
  assert.equal(visible(), true);
  assert.equal(store.authorizeScreen("assistants", DEFAULT_TENANT_ID, "account-shen").allowed, false);
});

test("成员取消按钮不提交添加表单", () => {
  const html = render("tenant-members", fresh());
  const dialog = html.slice(html.indexOf('<dialog id="add-member-dialog"'));
  assert.match(dialog, /<button[^>]*type="button"[^>]*data-cancel-add-member[^>]*>取消<\/button>/);
  const source = readFileSync(new URL("../prototype.js", import.meta.url), "utf8");
  assert.match(source, /querySelector\("\[data-cancel-add-member\]"\)/);
});

test("新租户详情页头与正文均显示刚创建的对象", () => {
  const store = fresh();
  const created = store.createTenant({ organizationName: "青岚制造复核租户", tenantCode: "qinglan-review", administratorAccountId: "account-qiao", confirmed: true });
  assert.equal(created.ok, true);
  const html = render("platform-tenant-detail", store);
  const objectLine = html.match(/<div class="page-object-line">([\s\S]*?)<\/div>/)?.[1];
  assert.match(objectLine || "", /青岚制造复核租户/);
});

test("新增租户和成员目录的搜索控件与可筛选行有绑定", () => {
  for (const screen of ["platform-tenants", "tenant-members"]) {
    const html = render(screen, fresh());
    assert.match(html, /<input[^>]*data-directory-search/);
    assert.match(html, /<tr[^>]*data-search-text=/);
  }
  const source = readFileSync(new URL("../prototype.js", import.meta.url), "utf8");
  assert.match(source, /querySelectorAll\("\[data-directory-search\]"\)/);
});

test("成员可授权的助手必须存在于真实合成目录", () => {
  const html = render("tenant-members", fresh());
  const known = new Set(fixtures.assistants.map((assistant) => assistant.id));
  for (const match of html.matchAll(/(?:data-member-assistant|value)="(assistant-[^"]+)"/g)) {
    assert.ok(known.has(match[1]), `未知助手：${match[1]}`);
  }
});

test("产品操作区不夹带研发评审说明", () => {
  for (const screen of ["platform-tenant-create", "platform-tenant-detail", "tenant-members"]) {
    assert.doesNotMatch(render(screen, fresh()), /本原型|待产品决策|伪保存|规则尚未确认/);
  }
});

test("权限投影隐藏未授权助手、其他成员历史与引用", () => {
  const store = fresh();
  assert.equal(loginQuery(store).ok, true);
  const request = { assistantId: "assistant-employee-service", conversationId: "cv-20260902-01" };
  const first = store.queryProjection(fixtures, request);
  assert.deepEqual(first.assistants.map((assistant) => assistant.id), ["assistant-employee-service"]);
  assert.ok(first.messages.length > 0);
  store.setMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen", "authorizedCitations", false);
  store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen");
  assert.ok(store.queryProjection(fixtures, request).messages.every((message) => !message.citations?.length));
  store.setMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen", "ownHistory", false);
  store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen");
  assert.deepEqual(store.queryProjection(fixtures, request).history, []);
  assert.deepEqual(store.queryProjection(fixtures, request).messages, []);
  store.addMember(DEFAULT_TENANT_ID, { accountId: "account-lin", conversationFeatures: ["assistantSelection", "conversation", "ownHistory"], assistantIds: ["assistant-employee-service"] });
  store.selectDemoAccount("account-lin");
  assert.deepEqual(store.queryProjection(fixtures, request).history, []);
  assert.deepEqual(store.queryProjection(fixtures, request).messages, []);
});

test("历史目录逐项检查助手授权且正文只属于选中的会话", () => {
  const store = fresh();
  assert.equal(loginQuery(store).ok, true);
  const first = store.queryProjection(fixtures, { assistantId: "assistant-employee-service", conversationId: "cv-20260902-01" });
  assert.deepEqual(first.history.map((conversation) => conversation.id), ["cv-20260902-01", "cv-20260902-02"]);
  const second = store.queryProjection(fixtures, { assistantId: "assistant-employee-service", conversationId: "cv-20260902-02" });
  assert.ok(second.messages.every((message) => message.conversationId === "cv-20260902-02"));
  store.setMemberAssistantDraft(DEFAULT_TENANT_ID, "account-shen", []);
  store.commitMemberFeatureDraft(DEFAULT_TENANT_ID, "account-shen");
  assert.deepEqual(store.queryProjection(fixtures).history, []);
});
