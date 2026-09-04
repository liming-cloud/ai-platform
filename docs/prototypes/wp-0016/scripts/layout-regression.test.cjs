const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { mkdir } = require("node:fs/promises");
const { tmpdir } = require("node:os");
const { join } = require("node:path");
const { chromium } = require("playwright");

const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const artifacts = process.env.LAYOUT_ARTIFACT_DIR || join(tmpdir(), "wp-0016", "layout-regression");
let browser;
before(async () => {
  browser = await chromium.launch({ headless: true });
  await mkdir(artifacts, { recursive: true });
});
after(async () => { await browser?.close(); });

async function scenario(name, viewport, action, reducedMotion = "no-preference") {
  const context = await browser.newContext({ viewport, reducedMotion });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  try {
    await action(page);
    assert.deepEqual(errors, [], "浏览器存在未处理异常");
  } catch (error) {
    await page.screenshot({ path: `${artifacts}/${name}-failure.png`, fullPage: true });
    throw error;
  } finally { await context.close(); }
}

async function contained(locator, width, height = Infinity) {
  const box = await locator.boundingBox();
  assert.ok(box, "目标必须可见");
  assert.ok(box.x >= 0 && box.x + box.width <= width + 1, `控件横向超出视口：${JSON.stringify(box)}`);
  if (Number.isFinite(height)) assert.ok(box.y >= 0 && box.y + box.height <= height + 1, `控件纵向超出视口：${JSON.stringify(box)}`);
}

async function detailFits(page, width) {
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), "角色详情导致文档级横向溢出");
  for (const selector of [".detail-facts", ".detail-facts h2", ".detail-facts .status-tag"]) {
    await contained(page.locator(selector), width);
  }
  const heading = await page.locator(".detail-facts h2").boundingBox();
  const status = await page.locator(".detail-facts .status-tag").boundingBox();
  assert.ok(heading.x + heading.width <= status.x || heading.y + heading.height <= status.y, "角色名称与状态标签重叠");
  assert.ok(await page.locator(".detail-facts").evaluate(element => element.scrollWidth <= element.clientWidth + 1), "详情内容超出自身栏位");
  if (width <= 1040) await singleColumn(page, ".detail-workspace");
}

async function singleColumn(page, selector) {
  const layout = await page.locator(selector).evaluate(element => {
    const container = element.getBoundingClientRect();
    return { width: container.width, children: [...element.children].map(child => child.getBoundingClientRect().toJSON()) };
  });
  assert.ok(layout.children.every(child => Math.abs(child.width - layout.width) <= 1), `${selector} 窄屏没有占满单列`);
  assert.ok(layout.children[0].bottom <= layout.children[1].top, `${selector} 窄屏两个区块应上下排列`);
}

async function settleMotion(page) {
  await page.evaluate(() => Promise.all(document.getAnimations()
    .filter(animation => Number.isFinite(animation.effect.getComputedTiming().endTime))
    .map(animation => animation.finished.catch(() => {}))));
}

for (const width of [390, 1024, 1440]) {
  test(`${width} 宽度下角色详情三种内容状态不溢出且使用中文名称`, () => scenario(`roles-${width}`, { width, height: 844 }, async page => {
    for (const state of ["partial", "disabled", "success"]) {
      await page.goto(`${base}?screen=platform-roles&state=${state}&review=0`);
      await page.locator(".detail-facts").waitFor();
      await settleMotion(page);
      await detailFits(page, width);
      assert.equal(await page.locator(".detail-facts h2").textContent(), "平台管理员");
    }
    await page.screenshot({ path: `${artifacts}/roles-${width}.png`, fullPage: true });
  }));
}

test("详情模板可容纳长连续标识，不以裁切掩盖溢出", () => scenario("long-title", { width: 390, height: 844 }, async page => {
  await page.goto(`${base}?screen=platform-roles&state=success&review=0`);
  await page.locator(".detail-facts h2").evaluate(element => { element.textContent = "PLATFORM_ADMINISTRATOR_LONG_IDENTIFIER"; });
  await detailFits(page, 390);
  assert.ok(await page.locator(".detail-facts h2").evaluate(element => element.scrollWidth <= element.clientWidth + 1), "长标题被截断或横向撑开");
}));

for (const [screen, layout] of [["assistant-create", "wizard"], ["platform-roles", "detail"], ["sync-result", "result"], ["audit-record-detail", "timeline"]]) {
  test(`手机 ${layout} 工作区上下排列且不压缩正文`, () => scenario(`workspace-${layout}`, { width: 390, height: 844 }, async page => {
    await page.goto(`${base}?screen=${screen}&state=success&review=0`);
    await page.locator(`.${layout}-workspace`).waitFor();
    await singleColumn(page, `.${layout}-workspace`);
    await settleMotion(page);
    await page.screenshot({ path: `${artifacts}/workspace-${layout}.png`, fullPage: true });
  }));
}

for (const viewport of [{ width: 390, height: 600 }, { width: 1024, height: 600 }, { width: 1280, height: 720 }, { width: 1440, height: 720 }, { width: 844, height: 390 }]) {
  const name = `reviewer-${viewport.width}-${viewport.height}`;
  test(`${viewport.width}×${viewport.height} 审查器重置后仍可点击与键盘收起`, () => scenario(name, viewport, async page => {
    await page.goto(`${base}?screen=backup-recovery&state=success&review=1`);
    const summary = page.locator("#prototype-reviewer summary");
    const details = page.locator("#prototype-reviewer details");
    await summary.click();
    await page.locator("#review-reset").click();
    await contained(page.locator("#prototype-reviewer"), viewport.width, viewport.height);
    await contained(summary, viewport.width, viewport.height);
    assert.ok(await page.locator("#prototype-reviewer").evaluate(element => element.scrollWidth <= element.clientWidth + 1), "审查器内控件存在横向裁切");
    for (const selector of ["#review-search", "#review-screen", "#review-state", "#review-apply", "#review-reset"]) {
      await contained(page.locator(selector), viewport.width);
    }
    await settleMotion(page);
    await page.screenshot({ path: `${artifacts}/${name}.png` });
    await summary.click();
    assert.equal(await details.evaluate(element => element.open), false);
    assert.ok(await summary.evaluate(element => element === document.activeElement), "收起后焦点不在原入口");
    await summary.press("Enter");
    await summary.press("Tab");
    assert.ok(await page.locator("#review-search").evaluate(element => element === document.activeElement), "键盘展开后不能进入首个字段");
    await page.keyboard.press("Shift+Tab");
    await page.keyboard.press("Space");
    assert.equal(await details.evaluate(element => element.open), false);
    await page.locator('input[value="backup-verified"]').check();
    assert.ok(await page.locator('input[value="backup-verified"]').isChecked(), "审查器收起后产品操作仍被遮挡");
  }, viewport.width === 390 ? "reduce" : "no-preference"));
}
