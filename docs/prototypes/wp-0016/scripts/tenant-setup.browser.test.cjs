const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { mkdir } = require("node:fs/promises");
const { chromium } = require("playwright");
const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const artifacts = process.env.TENANT_ARTIFACT_DIR || "docs/prototypes/wp-0016/artifacts/tenant-setup-20260903";
let browser;
before(async () => { browser = await chromium.launch(); await mkdir(artifacts, { recursive: true }); });
after(async () => { await browser?.close(); });

async function visit(name, action, width = 1440, height = 900) {
  const context = await browser.newContext({ viewport: { width, height }, reducedMotion: "reduce" });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  try { await action(page); assert.deepEqual(errors, [], "页面出现运行异常"); }
  catch (error) { await page.screenshot({ path: `${artifacts}/${name}-failure.png`, fullPage: true }); throw error; }
  finally { await context.close(); }
}

const url = screen => `${base}?screen=${screen}&state=success&review=0`;

async function fillTenant(page, code = "qinglan-full") {
  await page.goto(url("platform-tenant-create"));
  await page.locator("#tenant-organization-name").fill("青岚完整流程租户");
  await page.locator("#tenant-code").fill(code);
  await page.getByRole("button", { name: "下一步", exact: true }).click();
  await page.locator("#tenant-administrator").selectOption("account-qiao");
  await page.getByRole("button", { name: "下一步", exact: true }).click();
}

test("修改创建配额并刷新仍保留当前步骤和输入，成功后详情回显", () => visit("create-quotas", async page => {
  await fillTenant(page);
  const form = page.locator("#tenant-create-form");
  await form.locator('[name="knowledgeBases"]').fill("6");
  await form.locator('[name="documentsPerKnowledgeBase"]').fill("2500");
  await form.locator('[name="rawContentGbPerKnowledgeBase"]').fill("30");
  await form.locator('[name="rawContentGbPerKnowledgeBase"]').press("Tab");
  await page.reload();
  assert.equal(await form.locator('[name="knowledgeBases"]').inputValue(), "6");
  assert.equal(await page.locator("[data-tenant-step]:visible").count(), 1);
  await form.locator('[name="confirmed"]').check();
  await page.getByRole("button", { name: "创建租户", exact: true }).click();
  await page.locator('main[data-screen="platform-tenant-detail"]').waitFor();
  assert.match(await page.locator("main").innerText(), /青岚完整流程租户/);
  await page.locator("[data-edit-tenant-quotas]").click();
  assert.equal(await page.locator('#tenant-quota-form [name="knowledgeBases"]').inputValue(), "6");
  assert.equal(await page.locator('#tenant-quota-form [name="documentsPerKnowledgeBase"]').inputValue(), "2500");
  await page.screenshot({ path: `${artifacts}/created-quota-dialog.png`, fullPage: true });
}));

test("详情配额取消不生效，保存与刷新一致", () => visit("edit-quotas", async page => {
  await page.goto(url("platform-tenant-detail"));
  const trigger = page.locator("[data-edit-tenant-quotas]");
  await trigger.click();
  const form = page.locator("#tenant-quota-form");
  await form.locator('[name="knowledgeBases"]').fill("8");
  await page.locator("[data-cancel-tenant-quotas]").click();
  assert.ok(await trigger.evaluate(element => element === document.activeElement), "取消后焦点未返回");
  await trigger.click();
  assert.equal(await form.locator('[name="knowledgeBases"]').inputValue(), "3");
  await form.locator('[name="knowledgeBases"]').fill("8");
  await form.locator('button[type="submit"]').click();
  await page.reload();
  await trigger.click();
  assert.equal(await form.locator('[name="knowledgeBases"]').inputValue(), "8");
  await page.keyboard.press("Escape");
  assert.equal(await page.locator("#tenant-quota-dialog").evaluate(dialog => dialog.open), false);
}));

test("低于已用量拒绝并定位错误，保存失败不能假称成功", () => visit("quota-failure", async page => {
  await page.addInitScript(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (...args) {
      if (window.__failTenantWrite) throw new DOMException("合成存储故障", "QuotaExceededError");
      return original.apply(this, args);
    };
  });
  await page.goto(url("platform-tenant-detail"));
  await page.locator("[data-edit-tenant-quotas]").click();
  const form = page.locator("#tenant-quota-form");
  await form.locator('[name="knowledgeBases"]').fill("1");
  await form.locator('button[type="submit"]').click();
  assert.ok(await form.locator('[name="knowledgeBases"]').evaluate(input => input.getAttribute("aria-invalid") === "true" && input === document.activeElement));
  assert.match(await form.innerText(), /已用量/);
  await form.locator('[name="knowledgeBases"]').fill("8");
  await page.evaluate(() => { window.__failTenantWrite = true; });
  await form.locator('button[type="submit"]').click();
  assert.match(await form.innerText(), /无法保存|保存失败|存储/);
  assert.equal(await page.locator("#tenant-quota-dialog").evaluate(dialog => dialog.open), true);
}));

test("有无帮助说明的同排输入框顶部和高度一致", () => visit("alignment", async page => {
  for (const screen of ["assistant-create", "platform-tenant-create", "model-configuration-detail"]) {
    await page.goto(url(screen));
    await page.locator(".wizard-field-grid").first().waitFor();
    const controls = page.locator(".wizard-field-grid").first().locator("input,select,textarea");
    const first = await controls.nth(0).boundingBox();
    const second = await controls.nth(1).boundingBox();
    assert.ok(Math.abs(first.y - second.y) <= 1, `${screen} 同排输入框顶部不齐`);
    assert.ok(Math.abs(first.height - second.height) <= 1, `${screen} 同排输入框高度不齐`);
  }
}));

test("登录安全配置可编辑、取消、刷新，安全底线没有关闭控件", () => visit("security-policy", async page => {
  await page.goto(url("platform-tenant-detail"));
  const trigger = page.locator("[data-edit-tenant-security]");
  await trigger.click();
  const form = page.locator("#tenant-security-form");
  assert.equal(await form.locator('input[type="checkbox"]').count(), 0);
  await form.locator('[name="sessionHours"]').fill("4");
  await page.locator("[data-cancel-tenant-security]").click();
  assert.ok(await trigger.evaluate(element => element === document.activeElement));
  await trigger.click();
  assert.equal(await form.locator('[name="sessionHours"]').inputValue(), "8");
  await form.locator('[name="failedAttempts"]').fill("0");
  await form.locator('button[type="submit"]').click();
  assert.ok(await form.locator('[name="failedAttempts"]').evaluate(input => input === document.activeElement && input.getAttribute("aria-invalid") === "true"));
  await form.locator('[name="failedAttempts"]').fill("3");
  await form.locator('[name="sessionHours"]').fill("4");
  await form.locator('button[type="submit"]').click();
  await page.reload();
  await trigger.click();
  assert.equal(await form.locator('[name="sessionHours"]').inputValue(), "4");
  assert.equal(await form.locator('[name="failedAttempts"]').inputValue(), "3");
  await page.screenshot({ path: `${artifacts}/security-policy-dialog.png`, fullPage: true });
}));

test("租户创建使用清晰业务用语", () => visit("wording", async page => {
  await page.goto(url("platform-tenant-create"));
  await page.locator("#tenant-create-form").waitFor();
  assert.doesNotMatch(await page.locator("main").innerText(), /合资格|独立租户边界|配额与不可关闭安全边界/);
  await page.locator("#tenant-organization-name").fill("自然用语租户");
  await page.locator("#tenant-code").fill("natural-copy");
  await page.getByRole("button", { name: "下一步", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /选择租户管理员/);
}));

test("取消清除创建草稿，重复租户编码在当前步骤就定位错误", () => visit("cancel-and-validation", async page => {
  await page.goto(url("platform-tenant-create"));
  await page.locator("#tenant-organization-name").fill("重复编码租户");
  await page.locator("#tenant-code").fill("spruce-demo");
  await page.getByRole("button", { name: "下一步", exact: true }).click();
  assert.ok(await page.locator("#tenant-code").evaluate(input => input === document.activeElement && input.getAttribute("aria-invalid") === "true"));
  assert.match(await page.locator("#tenant-create-feedback").innerText(), /已存在|重复/);
  await page.locator("#tenant-code").fill("new-code");
  await page.getByRole("button", { name: "下一步", exact: true }).click();
  await page.getByRole("button", { name: "取消", exact: true }).click();
  await page.goto(url("platform-tenant-create"));
  assert.equal(await page.locator("#tenant-organization-name").inputValue(), "");
}));

for (const viewport of [{ width: 1440, height: 900 }, { width: 1024, height: 768 }, { width: 390, height: 844 }]) {
  test(`${viewport.width} 租户创建只呈现当前步骤且继续按钮可见`, () => visit(`step-${viewport.width}`, async page => {
    await page.goto(url("platform-tenant-create"));
    await page.locator("#tenant-create-form").waitFor();
    assert.equal(await page.locator("[data-tenant-step]:visible").count(), 1, "必须只呈现当前步骤");
    const next = page.getByRole("button", { name: "下一步", exact: true });
    const box = await next.boundingBox();
    assert.ok(box && box.y >= 0 && box.y + box.height <= viewport.height, "继续按钮不可在视口中直接操作");
    await page.locator("#tenant-organization-name").fill("青岚体验租户");
    await page.locator("#tenant-code").fill("qinglan-ux");
    await next.click();
    await page.locator("#tenant-administrator").waitFor({ state: "visible" });
    await page.getByRole("button", { name: "上一步", exact: true }).click();
    assert.equal(await page.locator("#tenant-organization-name").inputValue(), "青岚体验租户");
    await page.screenshot({ path: `${artifacts}/tenant-first-step-${viewport.width}.png`, fullPage: true });
    await fillTenant(page, `qinglan-${viewport.width}`);
    const createBox = await page.getByRole("button", { name: "创建租户", exact: true }).boundingBox();
    assert.ok(createBox && createBox.y >= 0 && createBox.y + createBox.height <= viewport.height, "配额步骤的创建按钮不在视口内");
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    await page.screenshot({ path: `${artifacts}/tenant-quota-step-${viewport.width}.png`, fullPage: true });
  }, viewport.width, viewport.height));
}
