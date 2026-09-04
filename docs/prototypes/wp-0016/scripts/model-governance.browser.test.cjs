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
  await page.goto(url(screen));
  await page.evaluate(() => localStorage.removeItem("wp0016-business-demo-v1"));
  await page.reload();
}

test("平台连接完成创建、探测、发布和刷新恢复", () => visit(async page => {
  await reset(page, "platform-model-connections");
  await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
  const dialog = page.locator("#platform-connection-dialog");
  await dialog.getByLabel("连接名称").fill("华北对话连接");
  await dialog.getByLabel("运行地址").fill("https://models.example.invalid/v1");
  await dialog.getByLabel("一次性访问密钥").fill("ephemeral-browser-key");
  await dialog.getByRole("button", { name: "保存连接草稿", exact: true }).click();
  await page.locator('main[data-screen="platform-model-connection-detail"]').waitFor();
  assert.doesNotMatch(await page.locator("body").innerText(), /ephemeral-browser-key/);
  await page.getByRole("button", { name: "执行连接探测", exact: true }).click();
  await page.getByRole("button", { name: "发布连接", exact: true }).click();
  assert.equal(await page.locator("[data-model-status]").getAttribute("data-model-status"), "PUBLISHED");
  await page.reload();
  assert.equal(await page.locator("[data-model-status]").getAttribute("data-model-status"), "PUBLISHED");
}));

test("平台部署完成建草稿、能力验证、租户授权和发布", () => visit(async page => {
  await reset(page, "platform-model-deployments");
  await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
  const dialog = page.locator("#platform-deployment-dialog");
  await dialog.getByLabel("部署名称").fill("企业对话模型 v7");
  await dialog.getByRole("button", { name: "保存部署草稿", exact: true }).click();
  await page.locator('main[data-screen="platform-model-deployment-detail"]').waitFor();
  await page.getByRole("button", { name: "执行能力验证", exact: true }).click();
  await page.getByRole("button", { name: "发布部署", exact: true }).click();
  assert.equal(await page.locator("[data-model-status]").getAttribute("data-model-status"), "PUBLISHED");
  const text = await page.locator("main").innerText();
  assert.match(text, /已授权租户/);
  assert.match(text, /云杉服务示例组织/);
}));

test("租户模型发现只展示已授权目录并可刷新恢复", () => visit(async page => {
  await reset(page, "model-providers");
  await page.getByRole("button", { name: "执行模型发现", exact: true }).click();
  await page.locator('main[data-screen="model-provider-detail"]').waitFor();
  assert.match(await page.locator("main").innerText(), /企业对话模型 v6/);
  assert.doesNotMatch(await page.locator("main").innerText(), /https?:\/\/|访问密钥/);
  await page.getByRole("button", { name: "重新探测", exact: true }).click();
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.locator('main[data-screen="model-provider-detail"]').waitFor();
  assert.match(await page.locator("main").innerText(), /发现完成/);
}));

test("租户模型配置完成创建、验证和启用", () => visit(async page => {
  await reset(page, "model-configurations");
  await page.getByRole("button", { name: "创建模型配置", exact: true }).click();
  const dialog = page.locator("#model-configuration-dialog");
  await dialog.getByLabel("配置名称").fill("知识咨询对话配置");
  await dialog.getByRole("button", { name: "保存配置草稿", exact: true }).click();
  await page.locator('main[data-screen="model-configuration-detail"]').waitFor();
  await page.getByRole("button", { name: "执行配置验证", exact: true }).click();
  await page.getByRole("button", { name: "启用配置", exact: true }).click();
  assert.equal(await page.locator("[data-model-status]").getAttribute("data-model-status"), "ACTIVE");
  await page.reload();
  await page.locator('main[data-screen="model-configuration-detail"]').waitFor();
  assert.match(await page.locator("main").innerText(), /知识咨询对话配置/);
}));

test("密钥轮换不回显且启用新版本前旧版本继续可用", () => visit(async page => {
  await reset(page, "model-configurations");
  await page.getByRole("button", { name: "查看员工服务对话配置", exact: true }).click();
  await page.getByRole("button", { name: "轮换访问密钥", exact: true }).click();
  const dialog = page.locator("#model-key-dialog");
  await dialog.getByLabel("新访问密钥").fill("rotation-browser-key");
  await dialog.getByRole("button", { name: "保存待验证版本", exact: true }).click();
  assert.doesNotMatch(await page.locator("body").innerText(), /rotation-browser-key/);
  let text = await page.locator("main").innerText();
  assert.match(text, /当前启用版本/);
  assert.match(text, /v6/);
  await page.getByRole("button", { name: "执行配置验证", exact: true }).click();
  await page.getByRole("button", { name: "启用配置", exact: true }).click();
  text = await page.locator("main").innerText();
  assert.match(text, /当前启用版本/);
  assert.match(text, /v7/);
  assert.match(text, /助手重新评测/);
}));

test("租户部署完成创建、验证、发布和影响确认停用", () => visit(async page => {
  await reset(page, "model-deployments");
  await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
  const dialog = page.locator("#tenant-deployment-dialog");
  await dialog.getByLabel("部署名称").fill("员工服务 Chat 候选");
  await dialog.getByRole("button", { name: "保存部署草稿", exact: true }).click();
  await page.locator('main[data-screen="model-deployment-detail"]').waitFor();
  await page.getByRole("button", { name: "执行部署验证", exact: true }).click();
  await page.getByRole("button", { name: "发布部署", exact: true }).click();
  await page.locator('[data-nav-screen="model-deployments"]').first().click();
  await page.getByRole("button", { name: "查看员工服务 Chat", exact: true }).click();
  await page.getByRole("button", { name: "停用部署", exact: true }).click();
  const stopDialog = page.locator("#deployment-stop-dialog");
  assert.match(await stopDialog.innerText(), /员工服务助手/);
  await stopDialog.getByLabel("我已确认受影响助手与路由").check();
  await stopDialog.getByRole("button", { name: "确认停用", exact: true }).click();
  assert.equal(await page.locator("[data-model-status]").getAttribute("data-model-status"), "OFFLINE");
}));

test("同能力路由先模拟再发布，刷新后保留发布版本", () => visit(async page => {
  await reset(page, "routing-policies");
  await page.getByRole("button", { name: "运行路由模拟", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /30 \/ 30/);
  await page.getByRole("button", { name: "发布路由策略", exact: true }).click();
  assert.equal(await page.locator("[data-model-status]").getAttribute("data-model-status"), "PUBLISHED");
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.locator('main[data-screen="routing-policies"]').waitFor();
  assert.match(await page.locator("main").innerText(), /已发布/);
}));

test("模型治理拒绝态不泄露地址、密钥、内部标识或租户授权明细", () => visit(async page => {
  await reset(page, "model-provider-detail");
  for (const screen of ["platform-model-connection-detail", "platform-model-deployment-detail", "model-provider-detail", "model-configuration-detail", "model-deployment-detail", "routing-policies"]) {
    await page.goto(`${base}?screen=${screen}&state=denied&review=0`);
    const text = await page.locator("body").innerText();
    assert.doesNotMatch(text, /models\.example|pc-chat|pd-chat|cfg-chat|访问密钥/);
  }
}));

for (const width of [390, 1024, 1440]) {
  test(`${width} 宽度下模型治理 11 页无横向溢出且主操作可达`, () => visit(async page => {
    for (const screen of ["platform-model-connections", "platform-model-connection-detail", "platform-model-deployments", "platform-model-deployment-detail", "model-providers", "model-provider-detail", "model-configurations", "model-configuration-detail", "model-deployments", "model-deployment-detail", "routing-policies"]) {
      await page.goto(url(screen));
      await page.locator(`main[data-screen="${screen}"]`).waitFor();
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${screen} 横向溢出`);
      await page.locator("[data-primary-action]").first().waitFor();
    }
  }, width));
}
