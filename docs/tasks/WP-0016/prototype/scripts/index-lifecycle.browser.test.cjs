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

async function createRebuild(page) {
  await openForReview(page, url("index-rebuild"));
  await page.getByRole("button", { name: "开始全库重建", exact: true }).click();
  await page.locator('main[data-screen="index-detail"]').waitFor();
}

test("索引目录创建全库重建后进入独立版本且旧索引继续服务", () => visit(async page => {
  await reset(page, "index-versions");
  await page.getByRole("button", { name: "重建索引", exact: true }).click();
  await page.locator('main[data-screen="index-rebuild"]').waitFor();
  await page.getByRole("button", { name: "开始全库重建", exact: true }).click();
  await page.locator('main[data-screen="index-detail"]').waitFor();
  assert.match(await page.locator("main").innerText(), /idx-v13/);
  assert.match(await page.locator("main").innerText(), /idx-v11 继续服务/);
  assert.equal(await page.locator("[data-index-stage]").getAttribute("data-index-stage"), "MANIFEST_FREEZING");
  await page.reload();
  assert.equal(await page.locator("[data-index-stage]").getAttribute("data-index-stage"), "MANIFEST_FREEZING");
}));

test("构建完成并通过评测后，确认激活原子切换且刷新一致", () => visit(async page => {
  await reset(page, "index-rebuild");
  await createRebuild(page);
  for (const stage of ["CHUNKING", "EMBEDDING", "HYBRID_INDEXING", "EVALUATING"]) {
    await page.getByRole("button", { name: "刷新构建状态", exact: true }).click();
    assert.equal(await page.locator("[data-index-stage]").getAttribute("data-index-stage"), stage);
  }
  await page.getByRole("button", { name: "运行冻结评测", exact: true }).click();
  assert.equal(await page.locator("[data-index-status]").getAttribute("data-index-status"), "READY_FOR_REVIEW");
  await page.getByRole("button", { name: "激活此版本", exact: true }).click();
  const dialog = page.locator("#index-dialog");
  assert.equal(await dialog.evaluate(element => element.open), true);
  await dialog.getByRole("button", { name: "取消", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /idx-v11 继续服务/);
  await page.getByRole("button", { name: "激活此版本", exact: true }).click();
  await dialog.getByRole("button", { name: "确认激活", exact: true }).click();
  assert.equal(await page.locator("[data-index-status]").getAttribute("data-index-status"), "ACTIVE");
  assert.match(await page.locator("main").innerText(), /当前活动索引[\s\S]*idx-v13/);
  await page.reload();
  assert.equal(await page.locator("[data-index-status]").getAttribute("data-index-status"), "ACTIVE");
}));

test("构建中的版本可取消且不会改变活动索引", () => visit(async page => {
  await reset(page, "index-rebuild");
  await createRebuild(page);
  await page.getByRole("button", { name: "取消构建", exact: true }).click();
  const dialog = page.locator("#cancel-index-dialog");
  assert.equal(await dialog.evaluate(element => element.open), true);
  await dialog.getByRole("button", { name: "确认取消", exact: true }).click();
  assert.equal(await page.locator("[data-index-status]").getAttribute("data-index-status"), "CANCELLED");
  assert.match(await page.locator("main").innerText(), /活动索引[\s\S]*idx-v11/);
}));

test("无权限态不泄露索引标识、配置或质量详情", () => visit(async page => {
  await reset(page, "index-detail");
  await openForReview(page, `${base}?screen=index-detail&state=denied&review=0`);
  const text = await page.locator("body").innerText();
  assert.doesNotMatch(text, /idx-v11|idx-v12|1536|Recall@10|员工服务手册/);
}));

for (const width of [390, 1024, 1440]) {
  test(`${width} 宽度下索引目录、重建和详情无横向溢出`, () => visit(async page => {
    await reset(page, "index-versions");
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
    await page.getByRole("button", { name: "重建索引", exact: true }).click();
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
    await page.getByRole("button", { name: "开始全库重建", exact: true }).click();
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
  }, width));
}
