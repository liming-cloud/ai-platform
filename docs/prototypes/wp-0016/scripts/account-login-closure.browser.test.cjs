const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const { mkdir } = require("node:fs/promises");
const { join } = require("node:path");
const { tmpdir } = require("node:os");
const { url, loginConsole } = require("./browser-session.cjs");
let browser;
const artifacts = process.env.ACCOUNT_LOGIN_ARTIFACT_DIR || join(tmpdir(), "wp0016-account-login");
before(async () => { browser = await chromium.launch(); await mkdir(artifacts, { recursive: true }); });
after(async () => browser?.close());

async function visit(action, width = 1440) {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 900 }, reducedMotion: "reduce" });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  try { await loginConsole(page, "platform"); await action(page); assert.deepEqual(errors, []); }
  finally { await context.close(); }
}

async function attempt(page, username, password, query = false) {
  await page.goto(url(query ? "login" : username === "qiao.demo" ? "tenant-login" : "console-login"));
  const form = page.locator(query ? "#login-form" : "#console-login-form");
  await form.locator('[name="username"]').fill(username);
  await form.locator('[name="password"]').fill(password);
  await form.locator('[type="submit"]').click();
}

async function resetAccount(page, id) {
  if (id === 'account-shen') {
    // 此用例聚焦登录边界；租户成员可见重置入口另由 member-provider.browser.test.cjs 覆盖。
    await page.evaluate(async () => {
      const { createPrototypeStore } = await import('./prototype-state.js');
      createPrototypeStore().resetAccountPassword('account-shen', { password: 'Reset-only-2026!', passwordConfirmation: 'Reset-only-2026!', revokeSessions: true }, 'account-platform-admin');
    });
    return;
  }
  await page.goto(url("platform-tenant-admins"));
  await page.locator(`[data-reset-account="${id}"]`).click();
  const form = page.locator("#reset-account-password-form");
  await form.locator('[name="password"]').fill("Reset-only-2026!");
  await form.locator('[name="passwordConfirmation"]').fill("Reset-only-2026!");
  await form.locator('[name="revokeSessions"]').check();
  await form.getByRole("button", { name: "重置密码", exact: true }).click();
  await page.locator("#reset-account-password-dialog").waitFor({ state: "hidden" });
}

test("管理账户密码重置后旧口令拒绝、新口令进入正确身份并可刷新", () => visit(async page => {
  await resetAccount(page, "account-qiao");
  await attempt(page, "qiao.demo", "demo-only");
  await page.locator("#console-login-form [role=alert]").waitFor();
  await attempt(page, "qiao.demo", "Reset-only-2026!");
  await page.locator('main[data-screen="tenant-overview"]').waitFor();
  await page.reload();
  await page.locator('main[data-screen="tenant-overview"]').waitFor();
  assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem("wp0016-query-session-v1")).accountId), "account-qiao");
  assert.doesNotMatch(await page.evaluate(() => Object.values(localStorage).join("\n")), /Reset-only|demo-only/);
}));

async function createPerson(page) {
  await page.goto(url("platform-accounts"));
  await page.getByRole("button", { name: "创建平台账户", exact: true }).click();
  const form = page.locator("#create-account-form");
  await form.locator('[name="loginName"]').fill("new.person");
  await form.locator('[name="password"]').fill("Initial-only-2026!");
  await form.locator('[name="passwordConfirmation"]').fill("Initial-only-2026!");
  await form.getByRole("button", { name: "创建账户", exact: true }).click();
  return "account-new-person-6";
}

test("新建平台账户使用刚设置的口令首次登录", () => visit(async page => {
  const id = await createPerson(page);
  await attempt(page, "new.person", "Initial-only-2026!");
  await page.locator('main[data-screen="platform-overview"]').waitFor();
  assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem("wp0016-query-session-v1")).accountId), id);
}));

test("租户创建普通人员并授权，再由本人首次登录提问", () => visit(async page => {
  const id = 'account-lin-member-6';
  await loginConsole(page, "tenant");
  await page.goto(url("tenant-members"));
  await page.locator('[data-business-action="open-add-member"]').click();
  await page.locator('#add-member-form [name="loginName"]').fill('lin.member');
  await page.locator('#add-member-form').getByRole('button', { name: '下一步', exact: true }).click();
  await page.locator('#add-member-form [data-choice-id="assistant-employee-service"]').check();
  await page.locator('#add-member-form').getByRole('button', { name: '下一步', exact: true }).click();
  await page.locator('#add-member-form [name="password"]').fill('demo-only');
  await page.locator('#add-member-form [name="passwordConfirmation"]').fill('demo-only');
  await page.locator("#add-member-form").getByRole("button", { name: "创建成员", exact: true }).click();
  await page.locator('main[data-screen="tenant-member-account"]').waitFor();
  await attempt(page, "lin.member", "demo-only", true);
  await page.locator('main[data-screen="assistants"]').waitFor();
  assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem("wp0016-query-session-v1")).accountId), id);
  await page.locator('[data-assistant-id="assistant-employee-service"]').click();
  await page.locator('main[data-screen="conversation"]').waitFor();
}));

test("普通用户按账户目录登录名使用重置口令进入助手与新会话", () => visit(async page => {
  await resetAccount(page, "account-shen");
  await attempt(page, "shen.demo", "demo-only", true);
  await page.locator("#login-feedback[data-tone=danger]").waitFor();
  await attempt(page, "shen.demo", "Reset-only-2026!", true);
  await page.locator('main[data-screen="assistants"]').waitFor();
  await page.locator('[data-assistant-id="assistant-employee-service"]').click();
  await page.locator('main[data-screen="conversation"]').waitFor();
  assert.equal(await page.locator(".admin-sidebar").count(), 0);
}));

for (const width of [390, 1024, 1440]) {
  test(`${width} 口令帮助可访问，非演示口令拒绝且取消不留下密码`, () => visit(async page => {
    await page.goto(url("platform-accounts"));
    await page.getByRole("button", { name: "创建平台账户", exact: true }).click();
    const form = page.locator("#create-account-form");
    await form.getByText("演示密码说明", { exact: true }).click();
    assert.match(await form.innerText(), /Initial-only-2026!/);
    await page.screenshot({ path: join(artifacts, `account-login-help-${width}.png`), fullPage: true });
    await form.locator('[name="loginName"]').fill("rejected.person");
    await form.locator('[name="password"]').fill("not-a-public-fixture");
    await form.locator('[name="passwordConfirmation"]').fill("not-a-public-fixture");
    await form.getByRole("button", { name: "创建账户", exact: true }).click();
    assert.match(await form.locator("[role=alert]").innerText(), /公开演示口令/);
    assert.equal(await form.locator('[name="password"]').inputValue(), "");
    await form.getByRole("button", { name: "取消", exact: true }).click();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    assert.doesNotMatch(await page.evaluate(() => Object.values(localStorage).join("\n")), /not-a-public-fixture/);
  }, width));
}
