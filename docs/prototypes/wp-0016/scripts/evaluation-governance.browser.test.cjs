const { openForReview } = require("./browser-session.cjs");
const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");

const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const url = (screen, state = "success") => `${base}?screen=${screen}&state=${state}&review=0`;
let browser;

before(async () => { browser = await chromium.launch({ headless: true }); });
after(async () => { await browser?.close(); });

async function visit(action, width = 1440) {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 960 }, reducedMotion: "reduce" });
  const page = await context.newPage();
  page.setDefaultTimeout(7000);
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
  await page.locator(`main[data-screen="${screen}"]`).waitFor();
}

test("新建数据集可复制基线、维护样本并发布为不可变版本", () => visit(async page => {
  await reset(page, "evaluation-datasets");
  await page.getByRole("button", { name: "新建评测数据集", exact: true }).click();
  await page.getByLabel("数据集名称").fill("浏览器制度专项回归");
  await page.getByRole("button", { name: "创建数据集", exact: true }).click();
  await page.locator('main[data-screen="evaluation-dataset-detail"]').waitFor();
  assert.match(await page.locator("main").innerText(), /100 个冻结样本/);
  await page.getByRole("button", { name: "新增样本", exact: true }).click();
  await page.getByLabel("问题摘要").fill("浏览器新增证据定位样本");
  await page.getByRole("button", { name: "保存样本", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /101 个冻结样本/);
  await page.getByRole("button", { name: "发布数据集版本", exact: true }).click();
  assert.equal(await page.getByRole("button", { name: "创建新草稿版本", exact: true }).isVisible(), true);
  await page.reload();
  await page.locator('main[data-screen="evaluation-dataset-detail"]').waitFor();
  assert.match(await page.locator("main").innerText(), /此版本已冻结/);
}));

test("冻结运行完成四阶段后可与同数据集基线比较并形成放行决定", () => visit(async page => {
  await reset(page, "evaluation-run-create");
  await page.getByRole("button", { name: "运行评测", exact: true }).click();
  await page.locator('main[data-screen="evaluation-run-detail"]').waitFor();
  for (let index = 0; index < 4; index += 1) {
    await page.getByRole("button", { name: "继续运行评测", exact: true }).click();
  }
  const detail = await page.locator("main").innerText();
  assert.match(detail, /越权泄漏\s*0/);
  assert.match(detail, /五次一致性\s*稳定/);
  await page.getByRole("button", { name: "比较版本", exact: true }).click();
  await page.getByLabel("基线运行").selectOption("eval-run-v12");
  await page.getByRole("button", { name: "生成对比", exact: true }).click();
  await page.locator('main[data-screen="evaluation-comparison"]').waitFor();
  assert.match(await page.locator("main").innerText(), /同一数据集版本|未发现安全或质量回退/);
  await page.getByRole("button", { name: "形成门禁决定", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /候选已放行/);
  await page.reload();
  await page.locator('main[data-screen="evaluation-comparison"]').waitFor();
  assert.match(await page.locator("main").innerText(), /候选已放行/);
}));

test("失败分类只展示合成摘要并可创建可刷新恢复的去重修复事项", () => visit(async page => {
  await reset(page, "evaluation-failure-analysis");
  const content = await page.locator("main").innerText();
  assert.match(content, /不展示真实问题、回答或引用正文/);
  await page.locator('tr[data-failure-category="CITATION_MISMATCH"]').getByRole("button", { name: "查看", exact: true }).click();
  await page.getByRole("button", { name: "创建修复事项", exact: true }).click();
  await page.getByLabel("负责人").fill("解析治理组");
  await page.getByRole("button", { name: "创建事项", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /修复事项已创建|解析治理组/);
  await page.reload();
  await page.locator('main[data-screen="evaluation-failure-analysis"]').waitFor();
  assert.match(await page.locator("main").innerText(), /解析治理组/);
  assert.equal(await page.getByRole("button", { name: "修复事项已创建", exact: true }).isDisabled(), true);
}));

test("拒绝态不投影冻结标识且不可用态禁用评测主操作", () => visit(async page => {
  await reset(page, "evaluation-run-detail");
  await openForReview(page, url("evaluation-run-detail", "denied"));
  assert.doesNotMatch(await page.locator("main").innerText(), /eval-set-v6|idx-v12|cfg-chat|员工服务助手 v13/);
  await openForReview(page, url("evaluation-run-create", "disabled"));
  assert.equal(await page.getByRole("button", { name: "运行评测", exact: true }).isDisabled(), true);
}));

for (const width of [390, 1024, 1440]) {
  test(`${width} 宽度下评测六页无横向溢出且主操作可达`, () => visit(async page => {
    for (const screen of ["evaluation-datasets", "evaluation-dataset-detail", "evaluation-run-create", "evaluation-run-detail", "evaluation-comparison", "evaluation-failure-analysis"]) {
      await openForReview(page, url(screen));
      await page.locator(`main[data-screen="${screen}"]`).waitFor();
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${screen} 横向溢出`);
      await page.locator("[data-primary-action]").first().waitFor();
    }
  }, width));
}
