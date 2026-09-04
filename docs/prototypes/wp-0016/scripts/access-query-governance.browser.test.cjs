const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");

const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const url = (screen, state = "success") => `${base}?screen=${screen}&state=${state}&review=0`;
let browser;

before(async () => { browser = await chromium.launch({ headless: true }); });
after(async () => { await browser?.close(); });

async function visit(action, width = 1440) {
  const context = await browser.newContext({
    viewport: { width, height: width === 390 ? 844 : 960 },
    reducedMotion: "reduce",
  });
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

test("访问策略保存后重新预览并发布，知识库始终由助手自动确定", () => visit(async page => {
  await reset(page, "access-policy-detail");
  assert.equal(await page.locator("[data-derived-knowledge-base]").innerText(), "员工服务手册");
  assert.equal(await page.getByRole("button", { name: "发布策略版本", exact: true }).isDisabled(), true);
  await page.getByLabel("已发布助手").selectOption("assistant-support");
  assert.equal(await page.locator("[data-derived-knowledge-base]").innerText(), "客户支持知识");
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /草稿已保存.*重新生成授权预览/s);
  await page.getByRole("button", { name: "生成授权预览", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /32 个样本已通过/);
  await page.getByRole("button", { name: "发布策略版本", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /策略版本已发布|已发布/);
  await page.reload();
  await page.locator('main[data-screen="access-policy-detail"]').waitFor();
  assert.equal(await page.getByRole("button", { name: "策略版本已发布", exact: true }).isDisabled(), true);
  assert.equal(await page.locator("[data-derived-knowledge-base]").innerText(), "客户支持知识");
}));

test("查询治理只读展示部署配额与单一分页，不出现在线修改控件", () => visit(async page => {
  await reset(page, "governance-query");
  assert.match(await page.locator("main").innerText(), /今日已接受\s*252/);
  assert.equal(await page.locator(".pagination").count(), 1);
  await page.getByRole("button", { name: "查看配额规则", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "查询配额规则" });
  assert.match(await dialog.innerText(), /UTC 每日已接受请求\s*1000 次/);
  assert.match(await dialog.innerText(), /当前版本不提供在线修改/);
  assert.equal(await dialog.locator("input, select, textarea").count(), 0);
}));

test("查询运行可进入历史诊断并创建不含正文的可恢复修复事项", () => visit(async page => {
  await reset(page, "governance-query");
  await page.locator('tr[data-query-run-row="Q-042"]').getByRole("button", { name: "查看诊断", exact: true }).click();
  await page.locator('main[data-screen="query-diagnostic"]').waitFor();
  const diagnostic = await page.locator("main").innerText();
  assert.match(diagnostic, /历史事实/);
  assert.match(diagnostic, /可披露候选\s*0/);
  assert.doesNotMatch(diagnostic, /年假如何结转|真实问题正文|真实回答正文/);
  await page.getByRole("button", { name: "创建修复事项", exact: true }).click();
  await page.getByLabel("负责人").fill("检索治理组");
  await page.getByRole("button", { name: "创建事项", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /修复事项已创建|检索治理组/);
  await page.reload();
  await page.locator('main[data-screen="query-diagnostic"]').waitFor();
  assert.match(await page.locator("main").innerText(), /检索治理组/);
  assert.equal(await page.getByRole("button", { name: "修复事项已创建", exact: true }).isDisabled(), true);
}));

test("拒绝态不披露策略、运行和模型修订", () => visit(async page => {
  for (const screen of ["access-policy-detail", "query-diagnostic"]) {
    await page.goto(url(screen, "denied"));
    const content = await page.locator("main").innerText();
    assert.doesNotMatch(content, /沈言|Q-042|chat-r13|embed-r8|员工服务手册/);
  }
}));

test("不可用态保留说明但禁用全部主要操作", () => visit(async page => {
  for (const screen of ["access-policy-detail", "governance-query", "query-diagnostic"]) {
    await page.goto(url(screen, "disabled"));
    const primary = page.locator("[data-primary-action]").first();
    await primary.waitFor();
    assert.equal(await primary.isDisabled(), true, `${screen} 主要操作未禁用`);
  }
}));

for (const width of [390, 1024, 1440]) {
  test(`${width} 宽度下访问与查询治理三页无横向溢出且主要操作可达`, () => visit(async page => {
    for (const screen of ["access-policy-detail", "governance-query", "query-diagnostic"]) {
      await page.goto(url(screen));
      await page.locator(`main[data-screen="${screen}"]`).waitFor();
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${screen} 横向溢出`);
      await page.locator("[data-primary-action]").first().waitFor();
    }
  }, width));
}
