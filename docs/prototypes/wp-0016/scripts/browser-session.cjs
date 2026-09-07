const assert = require("node:assert/strict");
const inventory = require("../page-inventory.json");

const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const url = (screen, state = "success") => `${base}?screen=${screen}&state=${state}&review=0`;

/** 专项通过可见登录表单准备身份，不注入存储或以路由推断产品身份。 */
async function loginConsole(page, role = "tenant") {
  await page.goto(url(role === 'platform' ? 'console-login' : 'tenant-login'));
  await page.getByLabel("登录名", { exact: true }).fill(role === "platform" ? "platform.admin.demo" : "qiao.demo");
  await page.getByLabel("密码", { exact: true }).fill("demo-only");
  await page.locator("#console-login-form [data-primary-action]").click();
  await page.locator('main[data-screen="platform-overview"], main[data-screen="tenant-overview"], #console-tenant-select').first().waitFor();
  if (await page.locator("#console-tenant-select").count()) {
    // 此正向夹具明确选择已有示例组织，不要求产品静默选择第一个组织。
    await page.locator("#console-tenant-select").selectOption("spruce-demo");
    await page.getByLabel("密码", { exact: true }).fill("demo-only");
    await page.locator("#console-login-form [data-primary-action]").click();
  }
  await page.locator(`main[data-screen="${role === "platform" ? "platform-overview" : "tenant-overview"}"][data-state="success"]`).waitFor();
}

async function loginMember(page) {
  await page.goto(url("login"));
  await page.locator('#login-form [name="tenantCode"]').fill("spruce-demo");
  await page.locator('#login-form [name="username"]').fill("shen.demo");
  await page.locator('#login-form [name="password"]').fill("demo-only");
  await page.locator('#login-form button[type="submit"]').click();
  await page.locator('main[data-screen="assistants"][data-state="success"]').waitFor();
}

async function createConversation(page) {
  await page.locator('[data-assistant-id="assistant-employee-service"]').click();
  await page.locator('main[data-screen="conversation"][data-state="success"]').waitFor();
  const id = new URL(page.url()).searchParams.get("conversationId");
  assert.ok(id, "新会话必须具有固定对象标识");
  return id;
}

/** 仅为正向页面矩阵选择夹具角色并经表单登录；权限反例不得调用此准备函数。 */
async function openForReview(page, targetUrl, options) {
  const screen = new URL(targetUrl).searchParams.get("screen");
  const group = inventory.groups.find(item => item.pages.some(item => item.screen === screen));
  if (["assistants", "conversation"].includes(screen)) {
    const channel = await page.evaluate(() => {
      try { return JSON.parse(localStorage.getItem("wp0016-query-session-v1"))?.accessChannel; } catch { return null; }
    });
    if (channel !== "QUERY_APP") await loginMember(page);
  }
  if (group?.shell === "console" && !["console-login", "tenant-login"].includes(screen)) {
    const role = ["platform-governance", "platform-operations"].includes(group.id) ? "platform" : "tenant";
    const session = await page.evaluate(() => {
      try { return JSON.parse(localStorage.getItem("wp0016-query-session-v1")); } catch { return null; }
    });
    const expected = role === "platform" ? "account-platform-admin" : "account-qiao";
    if (session?.accessChannel !== "CONSOLE" || session.accountId !== expected) await loginConsole(page, role);
  }
  return page.goto(targetUrl, options);
}

/** 通过平台目录创建新的平台夹具账户，用于生命周期浏览器验证。 */
async function createPlatformAccount(page, loginName = 'platform.review') {
  await page.goto(url('platform-accounts'));
  await page.getByRole('button', { name: '创建平台账户', exact: true }).click();
  const form = page.locator('#create-account-form');
  await form.locator('[name="loginName"]').fill(loginName);
  await form.locator('[name="password"]').fill('Initial-only-2026!');
  await form.locator('[name="passwordConfirmation"]').fill('Initial-only-2026!');
  await form.getByRole('button', { name: '创建账户', exact: true }).click();
  const row = page.locator('[data-account-row]').filter({ has: page.getByText(loginName, { exact: true }).first() });
  await row.waitFor();
  return row.getAttribute('data-account-row');
}

module.exports = { url, loginConsole, loginMember, createConversation, openForReview, createPlatformAccount };
