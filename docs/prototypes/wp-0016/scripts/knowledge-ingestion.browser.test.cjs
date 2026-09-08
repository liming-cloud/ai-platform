const { openForReview } = require("./browser-session.cjs");
const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");

const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const url = screen => `${base}?screen=${screen}&state=success&review=0`;
let browser;

before(async () => { browser = await chromium.launch({ headless: true }); });
after(async () => { await browser?.close(); });

async function visit(action, width = 1440) {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 960 }, reducedMotion: "reduce" });
  const page = await context.newPage();
  page.setDefaultTimeout(6000);
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  try {
    await action(page);
    assert.deepEqual(errors, [], "页面出现运行异常");
  } finally {
    await context.close();
  }
}

async function reset(page, screen) {
  await openForReview(page, url(screen));
  await page.evaluate(() => localStorage.removeItem("wp0016-business-demo-v1"));
  await page.reload();
}

test("创建知识库支持取消、提交、刷新恢复和配额反馈", () => visit(async page => {
  await reset(page, "knowledge-directory");
  await page.getByRole("button", { name: "创建知识库", exact: true }).click();
  let dialog = page.locator("#create-knowledge-base-dialog");
  await dialog.locator('[name="name"]').fill("不会保存的知识库");
  await dialog.getByRole("button", { name: "取消", exact: true }).click();
  assert.equal(await page.getByText("不会保存的知识库", { exact: true }).count(), 0);

  await page.getByRole("button", { name: "创建知识库", exact: true }).click();
  dialog = page.locator("#create-knowledge-base-dialog");
  await dialog.locator('[name="name"]').fill("服务运营手册");
  await dialog.locator('[name="description"]').fill("面向一线服务团队的已审核流程。");
  await dialog.getByRole("button", { name: "确认创建", exact: true }).click();
  await page.locator('[data-kb-row="kb-custom-3"]').waitFor();
  assert.ok((await page.locator("main").innerText()).includes("3 / 3"));
  assert.match(await page.locator(".page-object-line").innerText(), /3 个知识库/);
  assert.equal(await page.getByRole("button", { name: "创建知识库", exact: true }).isDisabled(), true);
  await page.reload();
  await page.locator('[data-kb-row="kb-custom-3"]').waitFor();
}));

test("粘贴文本从接收推进到就绪并可刷新恢复", () => visit(async page => {
  await reset(page, "knowledge-sources");
  await page.getByRole("button", { name: "添加数据", exact: true }).click();
  const form = page.locator("#source-add-form");
  await form.locator('[name="mode"]').selectOption("paste");
  await form.locator('[name="name"]').fill("差旅补充说明");
  await form.locator('[name="text"]').fill("差旅标准以已审批制度为准。");
  await form.getByRole("button", { name: "开始安全扫描", exact: true }).click();
  await page.locator('main[data-screen="source-detail"]').waitFor();
  assert.equal(await page.locator("[data-source-stage]").getAttribute("data-source-stage"), "RECEIVED");
  for (const stage of ["SCANNING", "EXTRACTING", "EXTRACTED", "CHUNKING", "INDEXING", "READY"]) {
    await page.getByRole("button", { name: "刷新处理状态", exact: true }).click();
    assert.equal(await page.locator("[data-source-stage]").getAttribute("data-source-stage"), stage);
  }
  await page.reload();
  assert.equal(await page.locator("[data-source-stage]").getAttribute("data-source-stage"), "READY");
}));

test("本地文件拒绝非白名单类型且取消不新增来源", () => visit(async page => {
  await reset(page, "source-add");
  const form = page.locator("#source-add-form");
  await form.locator('[name="file"]').setInputFiles({ name: "脚本.exe", mimeType: "application/octet-stream", buffer: Buffer.from("demo") });
  await form.getByRole("button", { name: "开始安全扫描", exact: true }).click();
  assert.match(await page.locator("#source-add-feedback").innerText(), /不支持此文件类型/);
  await form.getByRole("button", { name: "取消", exact: true }).click();
  await page.locator('main[data-screen="knowledge-sources"]').waitFor();
  assert.equal(await page.getByText("脚本.exe", { exact: true }).count(), 0);
}));

test("失败来源可从安全阶段重试，就绪来源重新处理建立新版本", () => visit(async page => {
  await reset(page, "knowledge-sources");
  await page.locator('[data-source-row="src-scan-pdf"] [data-open-source]').click();
  assert.equal(await page.locator("[data-source-stage]").getAttribute("data-source-stage"), "FAILED");
  await page.getByRole("button", { name: "安全重试", exact: true }).click();
  assert.equal(await page.locator("[data-source-stage]").getAttribute("data-source-stage"), "EXTRACTING");

  await openForReview(page, url("knowledge-sources"));
  await page.locator('[data-source-row="src-policy-pdf"] [data-open-source]').click();
  await page.getByRole("button", { name: "重新处理", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /版本 v8/);
  assert.equal(await page.locator("[data-source-stage]").getAttribute("data-source-stage"), "EXTRACTING");
  await page.locator('.source-detail-card details').getByText('技术详情', { exact: true }).click();
  assert.match(await page.locator("main").innerText(), /idx-v11 继续服务/);
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.locator('main[data-screen="source-detail"]').waitFor();
  assert.match(await page.locator("main").innerText(), /版本 v8/);
}));

test("无权限态不泄露知识库名称、文档名称或处理详情", () => visit(async page => {
  await reset(page, "source-detail");
  await openForReview(page, base + "?screen=source-detail&state=denied&review=0");
  const text = await page.locator("main").innerText();
  assert.doesNotMatch(text, /员工服务手册|2026 员工假期制度|idx-v11|rev-0007/);
}));

for (const width of [390, 1024, 1440]) {
  test(`${width} 宽度下知识目录和资料表单无横向溢出`, () => visit(async page => {
    await reset(page, "knowledge-directory");
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
    await page.getByRole("button", { name: "创建知识库", exact: true }).click();
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
    await page.locator("#create-knowledge-base-dialog").getByRole("button", { name: "取消", exact: true }).click();
    await openForReview(page, url("source-add"));
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
  }, width));
}
