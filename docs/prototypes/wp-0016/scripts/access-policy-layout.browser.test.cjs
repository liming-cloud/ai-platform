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

for (const width of [390, 1024, 1440]) test(`策略预览 ${width}px 四位计数在两倍文字下完整可读`, async () => {
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  try {
    const page = await browser.newPage({ viewport: { width, height: 960 }, reducedMotion: "reduce" });
    await openForReview(page, `${base}?screen=access-policy-detail&state=success&review=0`);
    await page.getByRole("button", { name: "生成授权预览", exact: true }).click();
    await page.locator(".summary-item dd").first().waitFor();
    // 仅改变当前测试 DOM 的数值，检查原组件承载真实规模计数；不改原型数据或持久状态。
    await page.locator(".summary-item dd").evaluateAll(values => { values[0].textContent = "3992"; values[1].textContent = "4000"; });
    for (const enlarged of [false, true]) await withTextScale(page, enlarged, async () => {
      const clipped = await page.locator(".summary-item dd").evaluateAll(values => values.filter(value =>
        value.scrollWidth > value.clientWidth + 1 || value.scrollHeight > value.clientHeight + 1).length);
      assert.equal(clipped, 0, "原项目策略计数被省略或裁切");
    });
  } finally { await browser.close(); }
});
