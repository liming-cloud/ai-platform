/* WP-0019 S1 原型手机重排回归；历史原型测试保留原文，当前反例只访问原项目。 */
const fs = require("node:fs");
const path = require("node:path");
const { enlargeText } = require("../development/acceptance-overview.cjs");
const { openForReview } = require("../../docs/prototypes/wp-0016/scripts/browser-session.cjs");
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


for (const width of [390, 1024, 1440]) {
  test(`${width}px S1 两页在真实200%文字下单元格和分页不重叠`, () => visit(async page => {
    const artifacts = process.env.S1_PROTOTYPE_REFLOW_ARTIFACTS_DIR
      || fs.mkdtempSync("/private/tmp/wp0019-prototype-reflow-");
    assert.ok(fs.realpathSync(artifacts).startsWith("/private/tmp/"), "截图只能放在临时目录");
    fs.chmodSync(artifacts, 0o700);
    for (const screen of ["platform-model-connections", "platform-model-connection-detail"]) {
      await reset(page, screen);
      await page.locator(`main[data-screen="${screen}"] .data-table tbody tr`).first().waitFor();
      const titleSize = await page.locator("main h1").evaluate(element => parseFloat(getComputedStyle(element).fontSize));
      const observations = [];
      for (const enlarged of [false, true]) {
        if (enlarged) await enlargeText(page);
        const metrics = await page.evaluate(screen => {
          const main = document.querySelector(`main[data-screen="${screen}"]`);
          const cells = [...main.querySelectorAll(".data-table tbody td")];
          const textBounds = cells.map(cell => {
            const box = cell.getBoundingClientRect();
            const walker = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT);
            const rectangles = [];
            while (walker.nextNode()) {
              if (!walker.currentNode.textContent.trim()) continue;
              const range = document.createRange(); range.selectNodeContents(walker.currentNode);
              rectangles.push(...range.getClientRects());
            }
            return { fits: rectangles.every(rect => rect.top >= box.top - 1 && rect.bottom <= box.bottom + 1),
              scrollFits: cell.scrollHeight <= cell.clientHeight + 1,
              bottom: rectangles.length ? Math.max(...rectangles.map(rect => rect.bottom)) : box.bottom };
          });
          return { cells: cells.length, textBounds,
            pagerTop: main.querySelector(".pagination").getBoundingClientRect().top,
            overflow: document.documentElement.scrollWidth - innerWidth,
            titleSize: parseFloat(getComputedStyle(main.querySelector("h1")).fontSize) };
        }, screen);
        const image = path.join(artifacts, `${screen}-${width}-${enlarged ? 200 : 100}.png`);
        await page.screenshot({ path: image, fullPage: true }); fs.chmodSync(image, 0o600);
        observations.push({ metrics, enlarged });
      }
      process.stdout.write(JSON.stringify({ screen, width, reflow: observations.map(({ metrics, enlarged }) => ({
        enlarged, cellsNotFit: metrics.textBounds.filter(cell => !cell.fits || !cell.scrollFits).length,
        entersPager: metrics.textBounds.some(cell => cell.bottom > metrics.pagerTop + 1),
      })) }) + "\n");
      for (const { metrics, enlarged } of observations) {
        assert.ok(metrics.cells > 0, "必须检查真实原型目录记录");
        assert.ok(Math.abs(metrics.titleSize - titleSize * (enlarged ? 2 : 1)) < 0.01, "文字必须真实放大，不缩小字号");
        assert.ok(metrics.overflow <= 1, "目录不得产生横向溢出");
        assert.ok(metrics.textBounds.every(cell => cell.fits && cell.scrollFits), "单元格文字不得进入下一行");
        assert.ok(metrics.textBounds.every(cell => cell.bottom <= metrics.pagerTop + 1), "目录内容不得进入分页区");
      }
    }
  }, width));
}
