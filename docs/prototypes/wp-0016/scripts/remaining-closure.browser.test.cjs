const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const { openForReview } = require("./browser-session.cjs");
const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const url = (screen, state = "success") => `${base}?screen=${screen}&state=${state}&review=0`;
let browser;
before(async () => { browser = await chromium.launch({ headless: true }); });
after(async () => { await browser?.close(); });
async function visit(screen, action, state = "success") {
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce", acceptDownloads: true });
  const page = await context.newPage();
  page.setDefaultTimeout(4500);
  const errors = [];
  page.on("pageerror", e => errors.push(e.message));
  try {
    await openForReview(page, url(screen, state));
    await action(page, context);
    assert.deepEqual(errors, []);
  } finally { await context.close(); }
}

test("知识库搜索与状态筛选组合生效，无结果可清空恢复", () => visit("knowledge-directory", async page => {
  const search = page.getByRole("searchbox", { name: "搜索知识库" });
  await search.fill("完全不存在的知识库");
  assert.equal(await page.locator("[data-kb-row]:visible").count(), 0);
  await search.fill("客户支持");
  assert.equal(await page.locator("[data-kb-row]:visible").count(), 1);
  await page.locator("[data-directory-status]").selectOption("READY");
  assert.equal(await page.locator("[data-kb-row]:visible").count(), 0);
  await search.fill("");
  await page.locator("[data-directory-status]").selectOption("");
  assert.equal(await page.locator("[data-kb-row]:visible").count(), 2);
}));

for (const [screen, label, dialog] of [["knowledge-directory", "创建知识库", "#create-knowledge-base-dialog"], ["platform-accounts", "创建人员账户", "#create-account-dialog"]]) {
  test(`${screen} 空态入口可以打开完整创建表单并取消`, () => visit(screen, async page => {
    await page.getByRole("button", { name: label, exact: true }).first().click();
    await page.locator(`${dialog}[open]`).waitFor();
    await page.locator(dialog).getByRole("button", { name: "取消", exact: true }).click();
    assert.equal(await page.locator(`${dialog}[open]`).count(), 0);
  }, "empty"));
}

test("结构草稿未保存修改时不能预检或发布旧值，保存后可继续", () => visit("dataset-schema", async page => {
  await page.getByRole("button", { name: "运行抽样预检", exact: true }).click();
  await page.getByLabel("floor 的业务含义").fill("楼层说明更新");
  assert.equal(await page.getByRole("button", { name: "发布结构版本", exact: true }).isDisabled(), true);
  assert.equal(await page.getByRole("button", { name: "运行抽样预检", exact: true }).isDisabled(), true);
  await page.getByRole("button", { name: "保存结构草稿", exact: true }).click();
  await page.getByRole("button", { name: "运行抽样预检", exact: true }).click();
  await page.getByRole("button", { name: "发布结构版本", exact: true }).click();
  await page.reload();
  assert.equal(await page.getByLabel("floor 的业务含义").inputValue(), "楼层说明更新");
}));

test("授权预览后修改表单必须重新保存和预览才能发布", () => visit("access-policy-detail", async page => {
  await page.getByRole("button", { name: "生成授权预览", exact: true }).click();
  await page.locator('[data-access-policy-assistant]').selectOption("assistant-policy");
  assert.equal(await page.getByRole("button", { name: "发布策略版本", exact: true }).isDisabled(), true);
  assert.equal(await page.getByRole("button", { name: "生成授权预览", exact: true }).isDisabled(), true);
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await page.getByRole("button", { name: "生成授权预览", exact: true }).click();
  await page.getByRole("button", { name: "发布策略版本", exact: true }).click();
  await page.getByRole("button", { name: "创建策略新版本", exact: true }).click();
  await page.locator('input[name="memberAccountIds"]').first().uncheck();
  await page.locator('input[name="memberAccountIds"]').first().check();
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await page.getByRole("button", { name: "生成授权预览", exact: true }).click();
  await page.getByRole("button", { name: "发布策略版本", exact: true }).click();
  await page.reload();
  assert.equal(await page.locator('[data-access-policy-assistant]').inputValue(), "assistant-policy");
}));

test("路由模拟后修改预算必须重新模拟才能发布", () => visit("routing-policies", async page => {
  await page.getByRole("button", { name: "运行路由模拟", exact: true }).click();
  await page.getByLabel("月度预算上限").fill("9000");
  assert.equal(await page.getByRole("button", { name: "发布路由策略", exact: true }).isDisabled(), true);
  await page.getByRole("button", { name: "运行路由模拟", exact: true }).click();
  await page.getByRole("button", { name: "发布路由策略", exact: true }).click();
  await page.reload();
  assert.equal(await page.getByLabel("月度预算上限").inputValue(), "9000");
}));

test("知识库总览与来源 URL 不随另一标签页选择变化", () => visit("knowledge-directory", async (page, context) => {
  await page.locator('[data-select-kb="kb-handbook"]').click();
  assert.match(page.url(), /knowledgeBaseId=kb-handbook/);
  assert.match(await page.locator(".page-object-line").innerText(), /员工服务手册/);
  await page.getByRole("button", { name: "继续当前阻断任务", exact: true }).click();
  const firstUrl = page.url();
  const other = await context.newPage();
  await other.goto(url("knowledge-directory"));
  await other.locator('[data-select-kb="kb-support"]').click();
  assert.match(await other.locator(".page-object-line").innerText(), /客户支持知识/);
  await page.reload();
  assert.equal(page.url(), firstUrl);
  assert.match(await page.locator(".page-object-line").innerText(), /员工服务手册/);
  await page.goto(`${url("knowledge-overview")}&knowledgeBaseId=missing`);
  assert.equal(await page.locator("main").getAttribute("data-state"), "denied");
  assert.doesNotMatch(await page.locator("main").innerText(), /员工服务手册|客户支持知识/);
}));

test("新租户从创建到登录、知识接入全程不展示示例组织内容", () => visit("platform-tenant-create", async page => {
  await page.locator("#tenant-organization-name").fill("北辰服务组织");
  await page.locator("#tenant-code").fill("beichen-demo");
  await page.getByRole("button", { name: "下一步", exact: true }).click();
  await page.locator("#tenant-administrator").selectOption("account-qiao");
  await page.getByRole("button", { name: "下一步", exact: true }).click();
  await page.locator('[name="confirmed"]').check();
  await page.getByRole("button", { name: "创建租户", exact: true }).click();
  await page.goto(url("console-login"));
  await page.getByLabel("登录名", { exact: true }).fill("qiao.demo");
  await page.getByLabel("密码", { exact: true }).fill("demo-only");
  await page.locator("#console-login-form [data-primary-action]").click();
  await page.locator("#console-tenant-select").selectOption("beichen-demo");
  await page.getByLabel("密码", { exact: true }).fill("demo-only");
  await page.locator("#console-login-form [data-primary-action]").click();
  await page.locator('main[data-screen="tenant-overview"]').waitFor();
  assert.doesNotMatch(await page.locator("#prototype-root").innerText(), /云杉服务|扫描 PDF|员工服务手册/);
  for (const screen of ["knowledge-directory", "assistants-directory", "model-configurations", "evaluation-datasets", "access-policies", "audit-records", "usage-quotas", "model-provider-detail", "model-configuration-detail", "model-deployment-detail", "routing-policies", "evaluation-comparison", "dataset-schema", "query-diagnostic", "tenant-members"]) {
    await page.goto(url(screen));
    await page.locator(`main[data-screen="${screen}"]`).waitFor();
    assert.doesNotMatch(await page.locator("main").innerText(), /员工服务|客户支持|全员制度|办公地点访问/, screen);
  }
  await page.goto(url("knowledge-directory"));
  await page.getByRole("button", { name: "创建知识库", exact: true }).click();
  await page.getByLabel("知识库名称", { exact: true }).fill("北辰制度");
  await page.getByRole("button", { name: "确认创建", exact: true }).click();
  await page.locator("[data-kb-row]").getByRole("button", { name: "进入", exact: true }).click();
  await page.getByRole("button", { name: "添加数据", exact: true }).click();
  await page.locator('[name="mode"]').selectOption("paste");
  await page.getByLabel("资料名称").fill("北辰办公规范");
  await page.getByLabel("粘贴内容").fill("这是北辰组织的合成资料。");
  await page.getByRole("button", { name: "开始安全扫描", exact: true }).click();
  await page.locator('main[data-screen="source-detail"]').waitFor();
  await page.reload();
  await page.locator('main[data-screen="source-detail"]').waitFor();
  assert.match(await page.locator("main").innerText(), /北辰办公规范/);
  assert.doesNotMatch(await page.locator("main").innerText(), /员工服务|客户支持/);
}));

test("处理总览重试后保留结果，快速发布尊重修复选择并建立新的候选索引", () => visit("processing-quality", async page => {
  await page.getByRole("button", { name: "重试失败项", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /安全重试 1 份/);
  await page.reload();
  assert.equal(await page.getByRole("button", { name: "重试失败项", exact: true }).isDisabled(), true);
  await page.goto(`${url("quick-publication")}&knowledgeBaseId=kb-handbook`);
  await page.getByLabel("处理批次").fill("验收批次");
  await page.getByLabel("质量结论").selectOption("repair");
  await page.getByRole("button", { name: "开始发布", exact: true }).click();
  await page.locator('main[data-screen="processing-quality"]').waitFor();
  await page.goto(`${url("quick-publication")}&knowledgeBaseId=kb-handbook`);
  await page.getByLabel("处理批次").fill("验收批次");
  await page.getByRole("button", { name: "开始发布", exact: true }).click();
  await page.locator('main[data-screen="index-detail"]').waitFor();
  assert.match(await page.locator("main").innerText(), /验收批次/);
  assert.match(page.url(), /indexVersionId=/);
  await page.reload();
  assert.match(await page.locator("main").innerText(), /验收批次/);
}));

test("对象来源登记后可进入同一来源推进处理，刷新保持阶段", () => visit("object-storage-scope", async page => {
  await page.getByRole("button", { name: "登记对象范围", exact: true }).click();
  await page.getByLabel("来源名称").fill("验收对象来源");
  await page.getByRole("button", { name: "确认登记", exact: true }).click();
  await page.getByRole("button", { name: "查看并继续处理", exact: true }).click();
  assert.match(page.url(), /sourceId=object-source-/);
  assert.match(await page.locator("main").innerText(), /验收对象来源/);
  await page.getByRole("button", { name: "刷新处理状态", exact: true }).click();
  await page.reload();
  assert.equal(await page.locator("[data-source-stage]").getAttribute("data-source-stage"), "SCANNING");
}));

test("审计每行详情独立、筛选与下载结果一致，导出失败可重试", () => visit("audit-records", async page => {
  await page.locator('[data-open-audit="audit-2"]').click();
  assert.match(page.url(), /auditId=audit-2/);
  await page.reload();
  assert.match(await page.locator("[data-audit-detail]").innerText(), /访问策略发布/);
  assert.doesNotMatch(await page.locator("[data-audit-detail]").innerText(), /助手版本发布/);
  await page.getByRole("button", { name: "返回审计目录", exact: true }).first().click();
  await page.getByRole("searchbox", { name: "搜索审计记录" }).fill("完全不存在");
  assert.equal(await page.locator("[data-audit-row]:visible").count(), 0);
  await page.getByRole("button", { name: "导出受控记录", exact: true }).click();
  assert.match(await page.locator("#governance-directory-feedback").innerText(), /没有可导出/);
  await page.getByRole("searchbox", { name: "搜索审计记录" }).fill("");
  await page.locator("[data-directory-status]").selectOption("FAILED");
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "导出受控记录", exact: true }).click();
  const file = await download;
  const content = require("node:fs").readFileSync(await file.path(), "utf8");
  assert.equal(JSON.parse(content).records.length, 1);
  assert.equal(JSON.parse(content).records[0].outcome, "FAILED");
  assert.doesNotMatch(content, /password|tenantId|question|answer/);
  await page.evaluate(() => { window.savedCreateObjectUrl = URL.createObjectURL; URL.createObjectURL = () => { throw Error("合成导出失败"); }; });
  await page.getByRole("button", { name: "导出受控记录", exact: true }).click();
  assert.match(await page.locator("#governance-directory-feedback").innerText(), /导出失败/);
  await page.evaluate(() => { URL.createObjectURL = window.savedCreateObjectUrl; });
}));

test("访问策略新建、取消、选择、保存与发布形成独立记录", () => visit("access-policies", async page => {
  await page.getByRole("button", { name: "新建访问策略", exact: true }).click();
  await page.locator("#access-create-dialog").getByRole("button", { name: "取消", exact: true }).click();
  await page.getByRole("button", { name: "新建访问策略", exact: true }).click();
  await page.getByLabel("策略名称").fill("服务组访问");
  await page.locator('#access-create-form [name="memberAccountIds"]').first().check();
  await page.getByRole("button", { name: "创建策略草稿", exact: true }).click();
  assert.match(page.url(), /policyId=access-policy-/);
  await page.reload();
  assert.match(await page.locator("h1").innerText(), /服务组访问/);
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await page.getByRole("button", { name: "生成授权预览", exact: true }).click();
  await page.getByRole("button", { name: "发布策略版本", exact: true }).click();
  await page.getByRole("button", { name: "返回策略目录", exact: true }).click();
  assert.match(await page.locator("tbody").innerText(), /服务组访问/);
}));

test("更新文档不会另建来源，可从新版本发起单文件重建", () => visit("knowledge-sources", async page => {
  const count = await page.locator("[data-source-row]").count();
  await page.locator('[data-select-source="src-policy-pdf"]').click();
  await page.getByRole("button", { name: "更新文档", exact: true }).click();
  assert.match(page.url(), /sourceId=src-policy-pdf/);
  await page.locator('[name="mode"]').selectOption("paste");
  await page.getByLabel("资料名称").fill("年假制度更新");
  await page.getByLabel("粘贴内容").fill("合成制度版本内容。");
  await page.getByRole("button", { name: "开始安全扫描", exact: true }).click();
  for (let i = 0; i < 6; i += 1) await page.getByRole("button", { name: "刷新处理状态", exact: true }).click();
  await page.getByRole("button", { name: "单文件重建索引", exact: true }).click();
  assert.match(await page.locator("main").innerText(), /单文件重建.*年假制度更新.*v8/s);
  await page.goto(`${url("knowledge-sources")}&knowledgeBaseId=kb-handbook`);
  assert.equal(await page.locator("[data-source-row]").count(), count);
  assert.match(await page.locator('[data-source-row="src-policy-pdf"]').innerText(), /年假制度更新/);
}));

test("模型配置详情的 URL 在另一个标签页选择后仍恢复原对象", () => visit("model-configurations", async (page, context) => {
  await page.locator('[data-select-model-configuration="cfg-embedding"]').click();
  assert.match(page.url(), /configurationId=cfg-embedding/);
  const other = await context.newPage();
  await other.goto(url("model-configurations"));
  await other.locator('[data-select-model-configuration="cfg-chat"]').click();
  await page.reload();
  assert.match(page.url(), /configurationId=cfg-embedding/);
  assert.match(await page.locator("main").innerText(), /Embedding/);
  await page.goto(`${url("model-configuration-detail")}&configurationId=missing`);
  assert.equal(await page.locator("main").getAttribute("data-state"), "denied");
}));

test("指定助手版本不符合当前阶段时不偷偷显示另一助手", () => visit("assistant-test", async page => {
  await page.goto(`${url("assistant-test")}&assistantVersionId=asv-0013`);
  await page.locator('main[data-screen="assistant-test"]').waitFor();
  assert.equal(await page.locator("#assistant-test-form").count(), 0);
  assert.match(await page.locator("main").innerText(), /没有待测试版本/);
}));
