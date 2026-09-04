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
}

async function createAssistant(page, name) {
  await page.getByLabel("助手名称").fill(name);
  await page.getByLabel("用途说明").fill("验证助手创建、版本冻结和发布的完整闭环。");
  await page.getByLabel("关联知识库").fill("员工服务手册");
  await page.getByRole("button", { name: "创建助手", exact: true }).click();
  await page.locator('main[data-screen="assistant-draft"]').waitFor();
}

test("创建助手只接受当前租户内索引就绪知识库并永久关联", () => visit(async page => {
  await reset(page, "assistant-create");
  await page.getByLabel("关联知识库").fill("尚未建立索引知识库");
  await page.getByRole("button", { name: "创建助手", exact: true }).click();
  assert.match(await page.locator("#assistant-create-feedback").innerText(), /索引就绪/);
  await createAssistant(page, "制度边界助手");
  const text = await page.locator("main").innerText();
  assert.match(text, /永久关联 员工服务手册/);
  assert.equal(await page.locator('select[name="knowledgeBaseId"]').count(), 0, "草稿页不应允许更换稳定知识库");
}));

test("助手从创建、冻结、单题测试、评测到发布形成可刷新闭环", () => visit(async page => {
  await reset(page, "assistant-create");
  await createAssistant(page, "浏览器制度助手");
  await page.locator('select[name="indexVersionId"]').selectOption("idx-v12");
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await page.locator('main[data-screen="assistant-test"]').waitFor();
  await page.getByLabel("测试问题").fill("年假最多可以结转几天？");
  await page.getByRole("button", { name: "运行单题测试", exact: true }).click();
  assert.equal(await page.locator(".selection-list li").count(), 2);
  await page.getByRole("button", { name: "加入评测集", exact: true }).click();
  await page.getByRole("button", { name: "运行冻结评测", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /越权泄漏\s*0/);
  await page.getByRole("button", { name: "请求发布", exact: true }).click();
  await page.getByRole("button", { name: "发布助手版本", exact: true }).click();
  const dialog = page.locator("#release-dialog");
  assert.equal(await dialog.evaluate(element => element.open), true);
  await dialog.getByRole("button", { name: "确认发布", exact: true }).click();
  await page.locator('main[data-screen="assistants-directory"]').waitFor();
  assert.equal(await page.getByText("浏览器制度助手", { exact: true }).isVisible(), true);
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.locator('main[data-screen="assistants-directory"]').waitFor();
  assert.equal(await page.getByText("浏览器制度助手", { exact: true }).isVisible(), true);
  const frozen = await page.evaluate(() => {
    const state = JSON.parse(localStorage.getItem("wp0016-business-demo-v1"));
    const assistant = state.assistantDelivery.assistants.find(item => item.name === "浏览器制度助手");
    return assistant.versions.find(item => item.id === assistant.publishedVersionId).frozen;
  });
  assert.equal(frozen.knowledgeBaseId, "kb-handbook");
  assert.equal(frozen.indexVersionId, "idx-v12");
  assert.deepEqual(frozen.configurationRevisions, {
    chat: { id: "cfg-chat", revision: 6 },
    embedding: { id: "cfg-embedding", revision: 4 },
    rerank: { id: "cfg-rerank", revision: 2 },
  });
}));

test("同一稳定助手重复进入候选时继续原版本而不制造并行分支", () => visit(async page => {
  await reset(page, "assistants-directory");
  const officeRow = page.locator("tr", { hasText: "办公地点助手" });
  await officeRow.getByRole("button", { name: "继续处理", exact: true }).click();
  await page.locator('main[data-screen="assistant-draft"]').waitFor();
  assert.match(await page.locator("h1").innerText(), /v5/);
  await page.goto(url("assistants-directory"));
  await page.locator("tr", { hasText: "办公地点助手" }).getByRole("button", { name: "继续处理", exact: true }).click();
  const candidates = await page.evaluate(() => {
    const state = JSON.parse(localStorage.getItem("wp0016-business-demo-v1"));
    return state.assistantDelivery.assistants.find(item => item.id === "assistant-office").versions.filter(item => !["PUBLISHED", "SUPERSEDED"].includes(item.status)).length;
  });
  assert.equal(candidates, 1);
}));

test("安全门禁不可用状态禁用发布且拒绝态不投影冻结配置", () => visit(async page => {
  await reset(page, "assistant-release");
  await page.goto(url("assistant-release", "disabled"));
  assert.equal(await page.getByRole("button", { name: "发布助手版本", exact: true }).isDisabled(), true);
  await page.goto(url("assistant-release", "denied"));
  const text = await page.locator("main").innerText();
  assert.doesNotMatch(text, /idx-v12|cfg-chat|员工服务手册|asv-0013/);
}));

test("创建页同排输入框对齐且没有依赖下方说明撑开控件", () => visit(async page => {
  await reset(page, "assistant-create");
  const name = await page.getByLabel("助手名称").boundingBox();
  const knowledgeBase = await page.getByLabel("关联知识库").boundingBox();
  assert.ok(Math.abs(name.y - knowledgeBase.y) <= 1, "同排输入框顶部未对齐");
  assert.ok(Math.abs(name.height - knowledgeBase.height) <= 1, "同排输入框高度不一致");
}));

for (const width of [390, 1024, 1440]) {
  test(`${width} 宽度下助手交付六页无横向溢出且主操作可达`, () => visit(async page => {
    for (const screen of ["assistants-directory", "assistant-create", "assistant-draft", "assistant-test", "assistant-evaluation", "assistant-release"]) {
      await page.goto(url(screen));
      await page.locator(`main[data-screen="${screen}"]`).waitFor();
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${screen} 横向溢出`);
      await page.locator("[data-primary-action]").first().waitFor();
    }
  }, width));
}
