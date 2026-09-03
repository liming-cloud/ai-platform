const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
let browser;
before(async () => { browser = await chromium.launch({ headless: true }); });
after(async () => { await browser?.close(); });
const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const url = screen => base + "?screen=" + screen + "&state=success&review=0";
async function scenario(action) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  page.setDefaultTimeout(3000);
  try { await action(page); } finally { await context.close(); }
}
async function login(page) {
  await page.goto(url("login"));
  await page.locator('#login-form input[name="tenantCode"]').fill("spruce-demo");
  await page.locator('#login-form input[name="username"]').fill("demo.user");
  await page.locator('#login-form input[name="password"]').fill("demo-only");
  await page.locator('#login-form button[type="submit"]').click();
  await page.locator('main[data-screen="assistants"]').waitFor();
}
async function createConversation(page) {
  await page.locator('[data-assistant-id="assistant-employee-service"]').click();
  await page.locator('main[data-screen="conversation"]').waitFor();
  const id = new URL(page.url()).searchParams.get("conversationId");
  assert.ok(id, "新会话 URL 缺少对象标识");
  return id;
}
async function ask(page, question) {
  await page.locator("#question-input").fill(question);
  await page.locator("#question-input").press("Enter");
  await page.locator('[data-run-state="COMPLETED"], [data-run-state="REFUSED"]').last().waitFor({ timeout: 12000 });
}
test("普通用户登录具备独立表单而不是管理员页面", () => scenario(async page => {
  await page.goto(url("login"));
  await page.locator("#login-form").waitFor();
  assert.equal(await page.locator(".admin-navigation").count(), 0);
  assert.equal(await page.locator('#login-form input[type="password"]').count(), 1);
}));
test("未登录会话深链接不读取历史正文", () => scenario(async page => {
  await page.goto(url("conversation") + "&conversationId=cv-20260902-01");
  await page.locator("main[data-screen]").waitFor();
  assert.equal(await page.locator("[data-citation-id], .message-body").count(), 0);
  await page.locator("#login-form").waitFor();
}));
test("错误登录可纠正且输入口令不写入浏览器存储", () => scenario(async page => {
  await page.goto(url("login"));
  await page.locator('#login-form input[name="tenantCode"]').fill("spruce-demo");
  await page.locator('#login-form input[name="username"]').fill("demo.user");
  await page.locator('#login-form input[name="password"]').fill("not-the-demo-password");
  await page.locator('#login-form button[type="submit"]').click();
  assert.equal(await page.locator('main[data-screen="assistants"]').count(), 0);
  await login(page);
  const saved = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
  assert.doesNotMatch(saved, /not-the-demo-password|demo-only/);
}));
test("新增会话提问后刷新及重新登录恢复同一会话", () => scenario(async page => {
  await login(page);
  const id = await createConversation(page);
  const question = "未休 7 天如何处理？";
  await ask(page, question);
  await page.reload();
  await page.locator('main[data-screen="conversation"]').waitFor();
  assert.equal(new URL(page.url()).searchParams.get("conversationId"), id);
  assert.equal(await page.locator(".message.user", { hasText: question }).count(), 1);
  await page.locator("[data-query-logout]").click();
  await page.locator("#login-form").waitFor();
  await login(page);
  await page.goto(url("conversation") + "&conversationId=" + encodeURIComponent(id));
  await page.locator(".message.user", { hasText: question }).waitFor();
  assert.equal(await page.locator(".message.user", { hasText: question }).count(), 1);
}));
test("两个会话正文隔离且未知问题不套用年假答案", () => scenario(async page => {
  await login(page);
  const first = await createConversation(page);
  await ask(page, "未休 7 天如何处理？");
  await page.locator("[data-new-conversation]").click();
  await page.locator('main[data-screen="assistants"]').waitFor();
  const second = await createConversation(page);
  assert.notEqual(first, second);
  const unrelated = "请预测明年的黄金价格";
  await ask(page, unrelated);
  await page.locator('[data-run-state="REFUSED"]').waitFor();
  assert.doesNotMatch(await page.locator("#messages").innerText(), /最多结转 5 天/);
  await page.goto(url("conversation") + "&conversationId=" + encodeURIComponent(first));
  await page.locator("#messages").waitFor();
  assert.doesNotMatch(await page.locator("#messages").innerText(), /黄金价格/);
}));
