const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const { url, loginConsole, loginMember, createConversation } = require("./browser-session.cjs");

let browser;
before(async () => { browser = await chromium.launch({ headless: true }); });
after(async () => { await browser?.close(); });

async function scenario(action, width = 1440) {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 960 }, reducedMotion: "reduce" });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  try { await action(page, context); assert.deepEqual(errors, []); }
  finally { await context.close(); }
}

test("错误控制台登录不跳转且可纠正，密码不进入反馈或存储", () => scenario(async page => {
  await page.goto(url("console-login"));
  await page.getByLabel("密码", { exact: true }).fill("wrong-demo-password");
  await page.locator("#console-login-form [data-primary-action]").click();
  assert.equal(await page.locator('main[data-screen="platform-overview"]').count(), 0);
  await page.locator('#console-login-form [role="alert"]').waitFor();
  await loginConsole(page, "platform");
  const saved = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
  assert.doesNotMatch(saved, /demo-only|wrong-demo-password/);
}));

test("三角色登录与管理直接访问全状态拒绝，不依赖审查器开关", () => scenario(async page => {
  await loginMember(page);
  for (const state of ["success", "partial", "disabled", "empty", "error", "loading", "denied"]) {
    await page.goto(url("knowledge-directory", state) + "&review=1");
    await page.locator('main[data-state="denied"]').waitFor();
    assert.equal(await page.getByRole("button", { name: "创建知识库", exact: true }).count(), 0);
    assert.doesNotMatch(await page.locator("#prototype-root").innerText(), /员工服务手册|客户支持知识|知识管理|成员管理/);
  }
  await loginConsole(page, "tenant");
  await page.goto(url("knowledge-directory"));
  await page.getByRole("button", { name: "创建知识库", exact: true }).waitFor();
  await page.goto(url("platform-accounts"));
  await page.locator('main[data-state="denied"]').waitFor();
  assert.equal(await page.locator("[data-account-row]").count(), 0);
  await loginConsole(page, "platform");
  await page.goto(url("knowledge-directory"));
  await page.locator('main[data-state="denied"]').waitFor();
  assert.doesNotMatch(await page.locator("#prototype-root").innerText(), /员工服务手册|客户支持知识/);
}));

test("管理端身份刷新、账户菜单和退出形成闭环", () => scenario(async page => {
  await loginConsole(page, "tenant");
  await page.reload();
  await page.locator('main[data-screen="tenant-overview"][data-state="success"]').waitFor();
  await page.locator(".account-menu summary").click();
  assert.match(await page.locator(".account-menu[open]").innerText(), /qiao.demo|乔宁/);
  await page.locator("[data-console-logout]").click();
  await page.locator("#console-login-form").waitFor();
  await page.goBack();
  assert.equal(await page.locator(".admin-sidebar").count(), 0);
  await page.goto(url("knowledge-directory"));
  await page.locator("#console-login-form").waitFor();
}));

test("1024 宽度账户菜单支持键盘打开与 Escape 关闭且焦点返回", () => scenario(async page => {
  await loginConsole(page, "platform");
  const summary = page.locator(".account-menu summary");
  await summary.focus();
  await page.keyboard.press("Enter");
  assert.equal(await page.locator(".account-menu[open]").count(), 1);
  await page.keyboard.press("Escape");
  assert.equal(await page.locator(".account-menu[open]").count(), 0);
  assert.equal(await summary.evaluate(element => element === document.activeElement), true);
}, 1024));

for (const width of [390, 1440]) {
  test(`${width} 宽度关闭本人历史后仍可新建、流式回答、刷新与续问`, () => scenario(async page => {
    await loginConsole(page, "tenant");
    await page.goto(url("tenant-members"));
    await page.locator('tbody tr').filter({ hasText: '沈言（合成）' }).getByRole('button', { name: '查看成员', exact: true }).click();
    await page.getByRole('navigation', { name: '成员工作区', exact: true }).getByRole('button', { name: '对话功能', exact: true }).click();
    await page.locator('[data-member-feature="ownHistory"]').uncheck();
    await page.getByRole("button", { name: "保存对话功能", exact: true }).click();
    await loginMember(page);
    const id = await createConversation(page);
    assert.equal(await page.locator("[data-conversation-id]").count(), 0);
    await page.locator("#question-input").fill("年假是否支持结转？");
    await page.locator("#question-input").press("Enter");
    await page.locator('[data-run-state="COMPLETED"]').last().waitFor({ timeout: 12000 });
    await page.reload();
    await page.locator('main[data-screen="conversation"][data-state="success"]').waitFor();
    assert.match(await page.locator("#messages").innerText(), /年假/);
    await page.locator("#question-input").fill("未休 7 天如何处理？");
    await page.locator("#question-input").press("Enter");
    await page.locator('[data-run-state="COMPLETED"]').nth(1).waitFor({ timeout: 12000 });
    await page.locator("[data-query-logout]").click();
    await loginMember(page);
    await page.goto(url("conversation") + "&conversationId=" + id);
    await page.locator('main[data-state="denied"]').waitFor();
    assert.equal(await page.locator(".message-body").count(), 0);
  }, width));
}

test("其他标签页替换身份时旧问答接收器停止，管理页面也即时失效", () => scenario(async (page, context) => {
  await loginMember(page);
  await createConversation(page);
  await page.locator("#question-input").fill("年假是否支持结转？");
  await page.locator("#question-input").press("Enter");
  const second = await context.newPage();
  await loginConsole(second, "platform");
  await page.locator('main[data-state="denied"]').waitFor();
  assert.equal(await page.locator(".message-body").count(), 0);
  await loginMember(page);
  await second.locator('main[data-state="denied"]').waitFor();
  assert.equal(await second.locator(".admin-sidebar").count(), 0);
}));
