/* WP-0019 S1 辅助读取降级原型；评审参数仅用于原项目，不构成生产故障或权限事实。 */
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { test, before, after } = require("node:test");
const { chromium } = require("playwright");
const { enlargeText } = require("../development/acceptance-overview.cjs");
const { openForReview } = require("../../docs/prototypes/wp-0016/scripts/browser-session.cjs");

const base = process.env.PROTOTYPE_BASE_URL;
assert.ok(base, "必须明确指定原 OpenDesign Cloud 项目预览地址");
const artifacts = process.env.S1_PARTIAL_ARTIFACTS_DIR;
if (artifacts) {
  assert.ok(fs.realpathSync(artifacts).startsWith("/private/tmp/"), "截图只能放在临时目录");
  assert.equal(fs.statSync(artifacts).mode & 0o777, 0o700);
}
const directory = "platform-model-connections";
const detail = "platform-model-connection-detail";
let browser;
before(async () => { browser = await chromium.launch({ headless: true }); });
after(async () => { await browser?.close(); });

async function visit(width, action) {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : width === 1024 ? 900 : 1000 }, reducedMotion: "reduce" });
  const page = await context.newPage();
  page.setDefaultTimeout(6000);
  const errors = [];
  let writes = 0;
  page.on("pageerror", error => errors.push(error.message));
  await page.route("**/*", async route => {
    if (!["GET", "HEAD", "OPTIONS"].includes(route.request().method())) {
      writes++; await route.abort();
    } else await route.continue();
  });
  try {
    await action(page);
    assert.deepEqual(errors, [], "原型不得出现运行异常");
    assert.equal(writes, 0, "只读原型验证不得发出业务写入");
  } finally { await context.close(); }
}

async function open(page, screen, state = "partial", auxiliary = "both") {
  const url = new URL(base);
  for (const [key, value] of Object.entries({ screen, state, auxiliary, review: "0" })) url.searchParams.set(key, value);
  await openForReview(page, url.href);
  await page.locator(`main[data-screen="${screen}"][data-state="${state}"]`).waitFor();
  await page.evaluate(() => document.fonts.ready);
}

async function checkReflow(page, key, width) {
  const h1 = page.locator("main h1");
  const normal = await h1.evaluate(e => parseFloat(getComputedStyle(e).fontSize));
  for (const enlarged of [false, true]) {
    if (enlarged) await enlargeText(page);
    assert.equal(await h1.evaluate(e => parseFloat(getComputedStyle(e).fontSize)), normal * (enlarged ? 2 : 1));
    const metrics = await page.evaluate(() => {
      const main = document.querySelector("main");
      const elements = [...main.querySelectorAll("h1,h2,p,dt,dd,.inline-note,button")].filter(e => e.getClientRects().length);
      return {
        overflow: document.documentElement.scrollWidth - innerWidth,
        clipped: elements.filter(e => e.scrollWidth > e.clientWidth + 1 || e.scrollHeight > e.clientHeight + 1).map(e => e.tagName),
        reduced: matchMedia("(prefers-reduced-motion: reduce)").matches,
      };
    });
    assert.ok(metrics.overflow <= 1, "原型不得产生横向溢出");
    assert.deepEqual(metrics.clipped, [], "部分可用说明及按钮不得裁切");
    assert.equal(metrics.reduced, true);
    if (artifacts) {
      const output = path.join(artifacts, `${key}-${width}-${enlarged ? 200 : 100}.png`);
      await page.screenshot({ path: output, fullPage: true }); fs.chmodSync(output, 0o600);
    }
  }
}

async function retryWithKeyboard(page, screen) {
  const before = await page.evaluate(() => localStorage.getItem("wp0016-business-demo-v1"));
  const retry = page.getByRole("button", { name: "重新载入", exact: true });
  assert.equal(await retry.isEnabled(), true);
  await retry.focus(); assert.equal(await retry.evaluate(e => e === document.activeElement), true);
  await page.keyboard.press("Enter");
  await page.locator(`main[data-screen="${screen}"][data-state="success"]`).waitFor();
  assert.equal(await page.locator("main [data-primary-action]").first().isEnabled(), true);
  assert.ok(before === await page.evaluate(() => localStorage.getItem("wp0016-business-demo-v1")), "重新载入不得修改原型连接或发布事实");
}

for (const width of [390, 1024, 1440]) {
  test(`${width}px 目录部分可用明确摘要未取得，查询可用、新增禁用并可键盘恢复`, () => visit(width, async page => {
    await open(page, directory);
    assert.match(await page.locator("main .inline-note").innerText(), /发布摘要暂未取得/);
    assert.match(await page.locator("main .page-object-line").innerText(), /部分完成/);
    assert.doesNotMatch(await page.locator("main .page-object-line").innerText(), /\d+ 个已发布连接/);
    assert.equal(await page.getByRole("button", { name: "新增供应商连接", exact: true }).isDisabled(), true);
    assert.equal(await page.getByLabel("搜索当前目录", { exact: true }).isEnabled(), true);
    assert.ok(await page.locator("main tbody tr").count() > 0);
    assert.equal(await page.getByRole("button", { name: /^查看/ }).first().isEnabled(), true);
    await page.getByLabel("搜索当前目录", { exact: true }).fill("不存在的连接");
    assert.match(await page.locator("main tbody").innerText(), /没有匹配记录/);
    assert.equal(await page.getByRole("button", { name: "新增供应商连接", exact: true }).isDisabled(), true);
    assert.match(await page.locator("main .inline-note").innerText(), /发布摘要暂未取得/);
    await page.getByLabel("搜索当前目录", { exact: true }).fill("");
    await page.getByRole("button", { name: /^查看/ }).first().waitFor();
    await checkReflow(page, "directory-summary", width);
    await retryWithKeyboard(page, directory);
  }));

  for (const auxiliary of ["candidates", "publication", "both"]) {
    test(`${width}px 详情${auxiliary}未取得仅保留本轮已知事实，写入禁用并可恢复`, () => visit(width, async page => {
      await open(page, detail, "partial", auxiliary);
      const text = await page.locator("main").innerText();
      assert.match(text, /部分辅助信息暂未取得/);
      const missingCandidates = auxiliary !== "publication";
      const missingPublication = auxiliary !== "candidates";
      assert.equal(text.includes("发现模型暂未取得"), missingCandidates);
      assert.equal(text.includes("发布状态暂未取得"), missingPublication);
      assert.equal(await page.locator("main .data-table").count() > 0, !missingCandidates);
      assert.equal(await page.locator("main [data-model-status]").getAttribute("data-model-status"), missingPublication ? "VERIFIED" : "PUBLISHED");
      assert.equal(text.includes("连接已发布"), !missingPublication);
      assert.doesNotMatch(text, /0 个|暂无记录|没有匹配记录/);
      assert.equal(await page.locator("main [data-primary-action]").first().isDisabled(), true);
      assert.equal(await page.getByRole("button", { name: "返回连接目录", exact: true }).isEnabled(), true);
      await checkReflow(page, `detail-${auxiliary}`, width);
      await retryWithKeyboard(page, detail);
    }));
  }

  test(`${width}px 两页七态保持隔离且在实际200%文字下可读`, () => visit(width, async page => {
    for (const screen of [directory, detail]) {
      for (const state of ["success", "loading", "empty", "error", "denied", "partial", "disabled"]) {
        await open(page, screen, state);
        const text = await page.locator("main").innerText();
        if (state === "partial") {
          assert.match(text, screen === directory ? /发布摘要暂未取得/ : /部分辅助信息暂未取得/);
        } else {
          assert.doesNotMatch(text, /部分辅助信息暂未取得|发布状态暂未取得|发现模型暂未取得|发布摘要暂未取得/);
        }
        if (state === "denied") {
          assert.equal(await page.getByRole("heading", { name: "当前会话不能访问此任务", exact: true }).isVisible(), true);
          assert.equal(await page.locator("main [data-model-status], main .data-table").count(), 0);
        }
        if (["success", "partial", "disabled"].includes(state)) {
          assert.equal(await page.locator("main [data-primary-action]").first().isDisabled(), state !== "success");
          if (screen === detail && state !== "partial") assert.match(text, /连接已发布/);
        }
        await checkReflow(page, `${screen}-${state}`, width);
      }
    }
  }));
}
