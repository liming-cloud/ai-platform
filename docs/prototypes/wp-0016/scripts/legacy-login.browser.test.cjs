const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const { url } = require("./browser-session.cjs");
let browser;
before(async () => { browser = await chromium.launch(); });
after(async () => browser?.close());

async function visit(action, width = 1440, screen = "console-login") {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 900 }, reducedMotion: "reduce" });
  const page = await context.newPage();
  page.setDefaultTimeout(4000);
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  try {
    await page.goto(url(screen));
    // 只在隔离测试浏览器构造历史夹具；不读取或修改用户 Chrome 的数据。
    const before = await page.evaluate(async () => {
      const { createPrototypeStore, PROTOTYPE_STATE_KEY } = await import("./prototype-state.js");
      const snapshot = createPrototypeStore().getSnapshot();
      snapshot.tenantSetup.organizationName = "旧版验收草稿必须保留";
      for (const account of snapshot.accounts) delete account.loginScenario;
      localStorage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
      return snapshot;
    });
    await page.reload();
    await action(page, before);
    assert.deepEqual(errors, []);
  } finally { await context.close(); }
}

for (const [width, username, screen, target] of [
  [1440, "platform.admin.demo", "console-login", "platform-overview"],
  [1024, "qiao.demo", "console-login", "tenant-overview"],
  [390, "shen.demo", "login", "assistants"],
]) {
  test(`${width} 旧版登录修复有确认、取消与保留数据结果，${username} 可登录并刷新`, () => visit(async (page, before) => {
    const repair = page.getByRole("button", { name: "修复演示登录", exact: true });
    await repair.waitFor();
    assert.equal(await page.evaluate(() => localStorage.getItem("wp0016-query-session-v1")), null);
    await repair.click();
    const dialog = page.getByRole("dialog", { name: "修复旧版演示登录" });
    await dialog.waitFor();
    assert.match(await dialog.innerText(), /不清空租户、知识库、会话和草稿/);
    await page.keyboard.press("Escape");
    assert.equal(await repair.evaluate(button => button === document.activeElement), true);
    assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem("wp0016-business-demo-v1"))), before);
    await repair.click();
    await dialog.getByRole("button", { name: "确认修复", exact: true }).click();
    await page.getByRole("status").filter({ hasText: "演示登录已修复" }).waitFor();
    assert.equal(await repair.count(), 0);
    const expected = structuredClone(before);
    for (const account of expected.accounts.filter(item => item.status === "available")) {
      Object.assign(account, { loginScenario: "INITIAL", authenticationRevision: 2 });
    }
    assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem("wp0016-business-demo-v1"))), expected);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    assert.equal(await page.evaluate(() => localStorage.getItem("wp0016-query-session-v1")), null);
    const form = page.locator(screen === "login" ? "#login-form" : "#console-login-form");
    await form.locator('[name="username"]').fill(username);
    await form.locator('[name="password"]').fill("demo-only");
    await form.getByRole("button", { name: "登录", exact: true }).click();
    await page.locator(`main[data-screen="${target}"][data-state="success"]`).waitFor();
    await page.reload();
    await page.locator(`main[data-screen="${target}"][data-state="success"]`).waitFor();
  }, width, screen));
}

test("登录修复写入失败提供重试，不清空旧数据也不提前建立会话", () => visit(async (page, before) => {
  await page.getByRole("button", { name: "修复演示登录", exact: true }).click();
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key === "wp0016-business-demo-v1") throw Error("合成存储故障");
      return original.call(this, key, value);
    };
  });
  const dialog = page.getByRole("dialog", { name: "修复旧版演示登录" });
  await dialog.getByRole("button", { name: "确认修复", exact: true }).click();
  assert.match(await dialog.getByRole("alert").innerText(), /未修改原数据/);
  assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem("wp0016-business-demo-v1"))), before);
  await page.reload();
  await page.getByRole("button", { name: "修复演示登录", exact: true }).click();
  await dialog.getByRole("button", { name: "确认修复", exact: true }).click();
  await page.getByRole("status").filter({ hasText: "演示登录已修复" }).waitFor();
}));
