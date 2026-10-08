const { test } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const { openForReview } = require("./browser-session.cjs");
const { withTextScale } = require("../../../../scripts/development/acceptance-onboarding.cjs");

const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";

for (const width of [390, 1024, 1440]) test(`访问策略目录 ${width}px 两倍文字时单元格由内容撑高`, async () => {
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  try {
    const page = await browser.newPage({ viewport: { width, height: 960 }, reducedMotion: "reduce" });
    await openForReview(page, `${base}?screen=access-policies&state=success&review=0`);
    await page.locator('main[data-screen="access-policies"] .data-table tbody td').first().waitFor();
    for (const enlarged of [false, true]) await withTextScale(page, enlarged, async () => {
      const clipped = await page.locator('main[data-screen="access-policies"] .data-table tbody td').evaluateAll(cells => cells.filter(cell =>
        cell.scrollHeight > cell.clientHeight + 1 || cell.scrollWidth > cell.clientWidth + 1).length);
      assert.equal(clipped, 0, "原项目访问策略目录存在固定高度裁切");
    });
  } finally { await browser.close(); }
});
