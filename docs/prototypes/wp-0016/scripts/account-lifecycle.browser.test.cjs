const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { mkdir } = require("node:fs/promises");
const { tmpdir } = require("node:os");
const { join } = require("node:path");
const { chromium } = require("playwright");

const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const artifacts = process.env.ACCOUNT_ARTIFACT_DIR || join(tmpdir(), "wp-0016", "account-lifecycle-browser");
let browser;

before(async () => {
  browser = await chromium.launch();
  await mkdir(artifacts, { recursive: true });
});
after(async () => browser?.close());

async function visit(name, action, viewport = { width: 1440, height: 900 }) {
  const context = await browser.newContext({ viewport, reducedMotion: "reduce" });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => {
    if (message.type() === "error") errors.push(message.text());
  });
  try {
    await page.goto(`${base}?screen=platform-accounts&state=success&review=0`);
    await action(page);
    assert.deepEqual(errors, [], "页面出现运行异常");
  } catch (error) {
    await page.screenshot({ path: `${artifacts}/${name}-failure.png`, fullPage: true });
    throw error;
  } finally {
    await context.close();
  }
}

test("创建人员账户后刷新仍可见且口令不回显不落存储", () => visit("create-account", async page => {
  await page.getByRole("button", { name: "创建人员账户", exact: true }).click();
  const form = page.locator("#create-account-form");
  await form.locator('[name="loginName"]').fill("zhou.demo");
  await form.locator('[name="password"]').fill("Initial-only-2026!");
  await form.locator('[name="passwordConfirmation"]').fill("Initial-only-2026!");
  await form.getByRole("button", { name: "创建账户", exact: true }).click();
  await page.locator('[data-account-row="account-zhou-demo-6"]').waitFor();
  assert.doesNotMatch(await page.locator("main").innerText(), /Initial-only-2026/);
  assert.doesNotMatch(await page.evaluate(() => localStorage.getItem("wp0016-business-demo-v1") || ""), /Initial-only-2026|password|credential|secret/i);
  await page.reload();
  await page.locator('[data-account-row="account-zhou-demo-6"]').waitFor();
  await page.screenshot({ path: `${artifacts}/account-created.png`, fullPage: true });
}));

test("重置他人密码支持取消、确认撤销会话和刷新恢复", () => visit("reset-password", async page => {
  const trigger = page.locator('[data-reset-account="account-lin"]');
  await trigger.click();
  let form = page.locator("#reset-account-password-form");
  await form.locator('[name="password"]').fill("Discarded-only-2026!");
  await form.getByRole("button", { name: "取消", exact: true }).click();
  assert.ok(await trigger.evaluate(element => element === document.activeElement));

  await trigger.click();
  form = page.locator("#reset-account-password-form");
  await form.locator('[name="password"]').fill("Reset-only-2026!");
  await form.locator('[name="passwordConfirmation"]').fill("Reset-only-2026!");
  await form.locator('[name="revokeSessions"]').check();
  await form.getByRole("button", { name: "重置密码", exact: true }).click();
  assert.match(await page.locator('[data-account-row="account-lin"]').innerText(), /认证修订 2/);
  assert.doesNotMatch(await page.evaluate(() => localStorage.getItem("wp0016-business-demo-v1") || ""), /Reset-only-2026|Discarded-only-2026|password|credential|secret/i);
  await page.reload();
  assert.match(await page.locator('[data-account-row="account-lin"]').innerText(), /认证修订 2/);
}));

test("人员范围冲突、平台管理员分配与停用恢复均有确定结果", () => visit("govern-account", async page => {
  await page.locator('[data-account-menu="account-qiao"] summary').click();
  assert.equal(await page.locator('[data-assign-platform-administrator="account-qiao"]').isDisabled(), true);

  await page.locator('[data-account-menu="account-lin"] summary').click();
  await page.locator('[data-assign-platform-administrator="account-lin"]').click();
  await page.locator("#account-governance-form").getByRole("button", { name: "确认设置", exact: true }).click();
  assert.match(await page.locator('[data-account-row="account-lin"]').innerText(), /平台管理员/);

  await page.locator('[data-account-menu="account-lin"] summary').click();
  await page.locator('[data-set-account-status="account-lin"]').click();
  await page.locator("#account-governance-form").getByRole("button", { name: "确认停用", exact: true }).click();
  assert.match(await page.locator('[data-account-row="account-lin"]').innerText(), /已停用/);

  await page.locator('[data-account-menu="account-lin"] summary').click();
  await page.locator('[data-set-account-status="account-lin"]').click();
  await page.locator("#account-governance-form").getByRole("button", { name: "确认恢复", exact: true }).click();
  assert.match(await page.locator('[data-account-row="account-lin"]').innerText(), /可用/);
}));

test("重复登录名和两次口令不一致时定位首个错误", () => visit("validation", async page => {
  await page.getByRole("button", { name: "创建人员账户", exact: true }).click();
  const form = page.locator("#create-account-form");
  await form.locator('[name="loginName"]').fill("qiao.demo");
  await form.locator('[name="password"]').fill("Initial-only-2026!");
  await form.locator('[name="passwordConfirmation"]').fill("different");
  await form.getByRole("button", { name: "创建账户", exact: true }).click();
  assert.ok(await form.locator('[name="loginName"]').evaluate(input => input === document.activeElement && input.getAttribute("aria-invalid") === "true"));
  assert.match(await form.locator("[role='alert']").innerText(), /已存在|一致/);
}));

for (const viewport of [
  { width: 1440, height: 900 },
  { width: 1024, height: 768 },
  { width: 390, height: 844 },
]) {
  test(`${viewport.width} 账户目录和创建弹窗无横向溢出且主操作可见`, () => visit(`viewport-${viewport.width}`, async page => {
    const primary = page.getByRole("button", { name: "创建人员账户", exact: true });
    const box = await primary.boundingBox();
    assert.ok(box && box.y >= 0 && box.y + box.height <= viewport.height);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    await primary.click();
    assert.equal(await page.locator("#create-account-dialog").evaluate(dialog => dialog.open), true);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    await page.screenshot({ path: `${artifacts}/account-${viewport.width}.png`, fullPage: true });
  }, viewport));
}
