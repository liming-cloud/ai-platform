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
  await page.goto(url(screen));
  await page.evaluate(() => localStorage.removeItem("wp0016-business-demo-v1"));
  await page.reload();
  await page.locator(`main[data-screen="${screen}"]`).waitFor();
}

test("结构化数据页保存、抽样预检并发布不可变版本", () => visit(async page => {
  await reset(page, "dataset-schema");
  assert.match(await page.locator("main").innerText(), /CSV 标题顺序.*office_name.*floor.*access_note.*contact/s);
  assert.equal(await page.locator("[data-dataset-field-row]").count(), 4);
  assert.doesNotMatch(await page.locator("main").innerText(), /上海市|电话号码/);
  await page.getByLabel("floor 的查询资格").check();
  await page.getByRole("button", { name: "保存结构草稿", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /草稿已保存.*重新抽样预检/s);
  await page.getByRole("button", { name: "运行抽样预检", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /100 行.*0 个阻断/s);
  await page.getByRole("button", { name: "发布结构版本", exact: true }).click();
  await page.reload();
  await page.locator('main[data-screen="dataset-schema"]').waitFor();
  assert.equal(await page.getByRole("button", { name: "结构版本已发布", exact: true }).isDisabled(), true);
}));

test("对象范围登记只接受已签发范围且不出现基础设施定位或密钥输入", () => visit(async page => {
  await reset(page, "object-storage-scope");
  await page.getByRole("button", { name: "登记对象范围", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "登记对象来源" });
  assert.equal(await dialog.locator('input[name="bucket"], input[name="objectKey"], input[name="endpoint"], input[name="accessKey"], input[name="secretKey"]').count(), 0);
  await dialog.getByLabel("来源名称").fill("华东办公制度对象来源");
  await dialog.getByRole("button", { name: "确认登记", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /华东办公制度对象来源.*待探测/s);
  await page.reload();
  await page.locator('main[data-screen="object-storage-scope"]').waitFor();
  assert.match(await page.locator("main").innerText(), /华东办公制度对象来源.*待探测/s);
}));

test("拒绝态不披露数据集、对象范围或字段事实", () => visit(async page => {
  for (const screen of ["dataset-schema", "object-storage-scope"]) {
    await page.goto(url(screen, "denied"));
    const content = await page.locator("main").innerText();
    assert.doesNotMatch(content, /office_name|华东办公制度|range-east|员工服务手册/);
  }
}));

test("不可用态保留可理解说明并禁用主要操作", () => visit(async page => {
  for (const screen of ["dataset-schema", "object-storage-scope"]) {
    await page.goto(url(screen, "disabled"));
    const primary = page.locator("[data-primary-action]").first();
    await primary.waitFor();
    assert.equal(await primary.isDisabled(), true);
  }
}));

for (const width of [390, 1024, 1440]) {
  test(`${width} 宽度下结构化与对象范围页面无横向溢出且主任务可达`, () => visit(async page => {
    for (const screen of ["dataset-schema", "object-storage-scope"]) {
      await page.goto(url(screen));
      await page.locator(`main[data-screen="${screen}"]`).waitFor();
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${screen} 横向溢出`);
      if (width === 390) {
        assert.notEqual(await page.locator(".admin-sidebar").evaluate(element => getComputedStyle(element).position), "fixed", `${screen} 的移动导航遮挡内容`);
      }
      await page.locator("[data-primary-action]").first().waitFor();
    }
  }, width));
}
