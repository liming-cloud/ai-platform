/* WP-0019 S1 原型校验错误语义回归；不填写真实凭据，不调用厂商。 */
const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const { openForReview } = require("../../docs/prototypes/wp-0016/scripts/browser-session.cjs");
const { enlargeText } = require("../development/acceptance-overview.cjs");

const base = process.env.PROTOTYPE_BASE_URL;
assert.ok(base, "必须指定原 OpenDesign Cloud 项目的预览地址");
let browser;
before(async () => { browser = await chromium.launch({ channel: "chrome", headless: true }); });
after(async () => { await browser?.close(); });

for (const width of [390, 1024, 1440]) test(`${width}px 原型连接校验错误使用已有danger语义并正确放大`, async () => {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 1000 }, reducedMotion: "reduce" });
  const page = await context.newPage();
  let writes = 0;
  await page.route("**/*", async route => {
    if (!["GET", "HEAD", "OPTIONS"].includes(route.request().method())) { writes++; await route.abort(); }
    else await route.continue();
  });
  try {
    await openForReview(page, base + "?screen=platform-model-connections&state=success&review=0");
    await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("连接名称", { exact: true }).fill("合成校验颜色");
    await dialog.locator('input[type="url"]').fill("https://models.example.invalid/v1");
    await dialog.getByRole("button", { name: "保存连接草稿", exact: true }).click();
    const feedback = dialog.getByRole("alert");
    assert.equal(await feedback.innerText(), "请输入至少 8 个字符的一次性访问密钥。");
    assert.equal(await feedback.getAttribute("data-tone"), "danger", "错误不得继承反馈段的默认成功绿色");
    for (const doubled of [false, true]) {
      if (doubled) await enlargeText(page);
      await page.waitForFunction(size => getComputedStyle(document.querySelector("#platform-connection-feedback")).fontSize === size,
        doubled ? "28px" : "14px");
      const style = await feedback.evaluate(element => {
        const s = getComputedStyle(element);
        return { color: s.color, size: s.fontSize, margin: s.margin };
      });
      assert.deepEqual(style, { color: "rgb(181, 51, 51)", size: doubled ? "28px" : "14px", margin: "0px" });
    }
    assert.equal(writes, 0, "原型验证不得发出业务写入");
    await dialog.getByRole("button", { name: "取消", exact: true }).click();
  } finally { await context.close(); }
});
