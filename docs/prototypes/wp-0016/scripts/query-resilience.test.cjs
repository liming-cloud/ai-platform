const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { mkdir } = require("node:fs/promises");
const { tmpdir } = require("node:os");
const { join } = require("node:path");
const { chromium } = require("playwright");
const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const artifacts = process.env.QUERY_ARTIFACT_DIR || join(tmpdir(), "wp-0016", "query-resilience");
let browser;
before(async () => { browser = await chromium.launch({ headless: true }); await mkdir(artifacts, { recursive: true }); });
after(async () => { await browser?.close(); });
const url = screen => base + "?screen=" + screen + "&state=success&review=0";
async function scenario(name, action, width = 1440, reducedMotion = "no-preference") {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 1000 }, reducedMotion });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  try {
    await action(page);
    assert.deepEqual(errors, [], "浏览器存在未处理异常");
  } catch (error) {
    await page.screenshot({ path: artifacts + "/" + name + "-failure.png", fullPage: true });
    throw error;
  } finally { await context.close(); }
}
async function login(page) {
  await page.goto(url("login"));
  for (const [name, value] of Object.entries({ tenantCode: "spruce-demo", username: "demo.user", password: "demo-only" })) {
    await page.locator('#login-form input[name="' + name + '"]').fill(value);
  }
  await page.locator('#login-form button[type="submit"]').click();
  await page.locator('main[data-screen="assistants"]').waitFor();
}
async function start(page, question = "未休 7 天如何处理？", observeStream = true) {
  await page.locator('[data-assistant-id="assistant-employee-service"]').click();
  await page.locator("#question-input").fill(question);
  await page.locator("#question-input").press("Enter");
  if (observeStream) await page.waitForFunction(() => [...document.querySelectorAll("[data-run-state]")].some(element => !["COMPLETED", "REFUSED", "FAILED"].includes(element.dataset.runState)));
  return new URL(page.url()).searchParams.get("conversationId");
}
async function terminal(page) {
  await page.locator('[data-run-state="COMPLETED"], [data-run-state="REFUSED"]').last().waitFor({ timeout: 15000 });
}
async function noOverflow(page) {
  const dimensions = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  assert.ok(dimensions.document <= dimensions.viewport + 1, "页面存在横向溢出：" + JSON.stringify(dimensions));
}
test("助手深链接在匿名状态下回到独立登录", () => scenario("anonymous-assistants", async page => {
  await page.goto(url("assistants"));
  await page.locator("#login-form").waitFor();
  assert.equal(await page.locator("[data-assistant-id]").count(), 0);
}));
test("接收中刷新后同一轮可恢复且问题不重复", () => scenario("refresh-stream", async page => {
  await login(page);
  const id = await start(page);
  await page.reload();
  await page.locator('main[data-screen="conversation"]').waitFor();
  const resume = page.locator("[data-query-resume]");
  if (await resume.isVisible()) await resume.click();
  await terminal(page);
  assert.equal(new URL(page.url()).searchParams.get("conversationId"), id);
  assert.equal(await page.locator(".message.user", { hasText: "未休 7 天如何处理？" }).count(), 1);
  await page.screenshot({ path: artifacts + "/refresh-stream.png", fullPage: true });
}));
test("流式接收时退出不会被旧回调恢复正文", () => scenario("logout-stream", async page => {
  await login(page);
  const id = await start(page);
  await page.locator("[data-query-logout]").click();
  await page.locator("#login-form").waitFor();
  await page.waitForTimeout(750);
  assert.equal(await page.locator(".message-body, [data-citation-id]").count(), 0);
  await page.goto(url("conversation") + "&conversationId=" + encodeURIComponent(id));
  await page.locator("#login-form").waitFor();
  assert.equal(await page.locator(".message-body, [data-citation-id]").count(), 0);
  await login(page);
  await page.goto(url("conversation") + "&conversationId=" + encodeURIComponent(id));
  await page.locator("[data-query-resume]").click();
  await terminal(page);
  assert.equal(await page.locator(".message.user", { hasText: "未休 7 天如何处理？" }).count(), 1);
}));
test("另一标签页关闭对话权限后当前正文与引用立即失效", () => scenario("cross-tab-revocation", async page => {
  await login(page);
  await start(page);
  const administration = await page.context().newPage();
  await administration.goto(url("platform-tenant-detail"));
  await administration.locator('[data-tenant-feature="conversation"]').uncheck();
  await administration.locator('[data-business-action="save-tenant-features"]').click();
  await page.waitForFunction(() => document.querySelectorAll(".message-body, [data-citation-id]").length === 0);
  await page.waitForTimeout(750);
  assert.equal(await page.locator(".message-body, [data-citation-id]").count(), 0);
  await page.reload();
  await page.locator("main[data-screen]").waitFor();
  assert.equal(await page.locator(".message-body, [data-citation-id]").count(), 0);
}));
test("浏览器存储写入失败不能显示已完成", () => scenario("storage-failure", async page => {
  await page.addInitScript(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (...args) {
      if (window.__failSyntheticWrites) throw new DOMException("合成配额故障", "QuotaExceededError");
      return original.apply(this, args);
    };
  });
  await login(page);
  await page.locator('[data-assistant-id="assistant-employee-service"]').click();
  await page.evaluate(() => { window.__failSyntheticWrites = true; });
  await page.locator("#question-input").fill("未休 7 天如何处理？");
  await page.locator("#question-input").press("Enter");
  await page.getByText(/无法保存|未保存|存储|保存失败/).first().waitFor();
  assert.equal(await page.locator('[data-run-state="COMPLETED"]').count(), 0);
  await page.screenshot({ path: artifacts + "/storage-failure.png", fullPage: true });
}));
test("减少动效不改变小屏历史抽屉的开闭布局", () => scenario("reduced-motion-history", async page => {
  await login(page);
  await page.locator('[data-assistant-id="assistant-employee-service"]').click();
  await page.locator("#question-input").waitFor();
  const right = await page.locator(".history-panel").evaluate(element => element.getBoundingClientRect().right);
  assert.ok(right <= 0, "未打开的历史抽屉遮挡聊天区域：right=" + right);
  await page.locator("[data-open-history]").click();
  await page.waitForFunction(() => document.querySelector(".history-panel").getBoundingClientRect().right > 0);
  await page.keyboard.press("Escape");
  await page.waitForFunction(() => document.querySelector(".history-panel").getBoundingClientRect().right <= 0);
}, 390, "reduce"));
test("历史筛选及故障模式的重试和继续有实际结果", () => scenario("failure-recovery-controls", async page => {
  await page.setViewportSize({ width: 1440, height: 1300 });
  await login(page);
  await page.locator('[data-assistant-id="assistant-employee-service"]').click();
  const history = page.locator("[data-history-search]");
  await history.fill("报销");
  assert.equal(await page.locator("[data-conversation-id]:visible").count(), 1);
  await history.fill("");
  assert.ok(await page.locator("[data-conversation-id]:visible").count() >= 3);
  const reviewUrl = new URL(page.url());
  reviewUrl.searchParams.set("review", "1");
  await page.goto(reviewUrl.href);
  let questions = 0;
  for (const [mode, state, action] of [["failure", "FAILED", "retry"], ["disconnect", "PAUSED", "resume"]]) {
    await page.locator("#prototype-reviewer summary").click();
    await page.locator("#review-query-mode").selectOption(mode);
    await page.locator("#prototype-reviewer summary").click();
    await page.locator("#question-input").fill("未休 7 天如何处理？");
    await page.locator("#question-input").press("Enter");
    await page.locator('[data-run-state="' + state + '"]').last().waitFor();
    await page.locator("[data-query-" + action + "]").click();
    questions += 1;
    await page.waitForFunction(count => document.querySelectorAll('.message.assistant[data-run-state="COMPLETED"]').length === count, questions);
    assert.equal(await page.locator(".message.user").count(), questions);
  }
  await page.locator("#question-input").fill("年假是否支持结转？");
  await page.locator("#question-input").press("Enter");
  await page.locator("[data-stop-run]").click();
  await page.locator('[data-run-state="STOPPED"]').last().waitFor();
  await page.locator("[data-query-resume]").click();
  await page.waitForFunction(() => document.querySelectorAll('.message.assistant[data-run-state="COMPLETED"]').length === 3);
  assert.equal(await page.locator(".message.user").count(), 3);
}));
for (const width of [390, 1024, 1440]) {
  test(width + " 宽度登录、助手、聊天可操作且没有横向溢出", () => scenario("viewport-" + width, async page => {
    await page.goto(url("login"));
    await page.locator("#login-form").waitFor();
    await noOverflow(page);
    if (width === 390) {
      const bottom = await page.locator('#login-form button[type="submit"]').evaluate(element => element.getBoundingClientRect().bottom);
      assert.ok(bottom <= 844, "小屏登录主操作被重复说明挤出首屏：bottom=" + bottom);
    }
    await page.screenshot({ path: artifacts + "/login-" + width + ".png", fullPage: true, animations: "disabled" });
    await login(page);
    await noOverflow(page);
    await page.screenshot({ path: artifacts + "/assistants-" + width + ".png", fullPage: true, animations: "disabled" });
    await start(page, "未休 7 天如何处理？", width !== 390);
    await terminal(page);
    await noOverflow(page);
    const citation = page.locator("[data-citation-id]").last();
    await citation.click();
    assert.equal(await citation.getAttribute("aria-expanded"), "true");
    await citation.click();
    await page.locator("#question-input").focus();
    assert.equal(await page.locator("#question-input").evaluate(element => element === document.activeElement), true);
    await page.screenshot({ path: artifacts + "/conversation-" + width + ".png", fullPage: true, animations: "disabled" });
  }, width, width === 390 ? "reduce" : "no-preference"));
}
