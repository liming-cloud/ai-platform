/* WP-0019 S1 原型手机重排回归；历史原型测试保留原文，当前反例只访问原项目。 */
const fs = require("node:fs");
const path = require("node:path");
const { enlargeText } = require("../development/acceptance-overview.cjs");
const { openForReview, loginConsole } = require("../../docs/prototypes/wp-0016/scripts/browser-session.cjs");
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

for (const width of [390, 1024, 1440]) {
  test(`${width}px 固定预设地址在正常和200%文字下完整可读，且不镜像自定义输入`, () => visit(async page => {
    await reset(page, "platform-model-connections");
    for (const provider of ["DEEPSEEK", "ALIYUN", "SILICONFLOW", "VOLCENGINE"]) {
      await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
      const dialog = page.getByRole("dialog");
      await dialog.locator('[name="providerId"]').selectOption(provider);
      const capabilities = await dialog.locator('[name="capability"] option').evaluateAll(options => options.map(option => option.value));
      for (const capability of capabilities) {
        // 每个状态从未放大的原始表单开始，避免连续遍历重复放大。
        await dialog.getByRole("button", { name: "取消", exact: true }).click();
        await reset(page, "platform-model-connections");
        await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
        await dialog.locator('[name="providerId"]').selectOption(provider);
        await dialog.locator('[name="capability"]').selectOption(capability);
        const workspace = dialog.locator('[name="workspaceId"]');
        if (await workspace.isVisible()) {
          assert.equal(await dialog.locator("[data-provider-address-value]").isVisible(), false, "缺少业务空间时不展示未解析地址");
          assert.equal(await dialog.locator("[data-provider-address-value]").textContent(), "");
          await workspace.fill("w".repeat(63));
        }
        const input = dialog.locator('[name="runtimeBaseUrl"]');
        const preview = dialog.locator("[data-provider-address-value]");
        const address = await input.inputValue();
        await preview.waitFor({ state: "visible" });
        assert.equal(await preview.textContent(), address, "完整地址必须与本次已解析预设一致");
        assert.equal(await input.getAttribute("aria-describedby"), "platform-connection-runtime-preview platform-connection-address-help");
        const normalSize = await preview.evaluate(element => parseFloat(getComputedStyle(element).fontSize));
        for (const enlarged of [false, true]) {
          if (enlarged) await enlargeText(page);
          await preview.scrollIntoViewIfNeeded();
          await page.waitForFunction(({ enlarged, normalSize }) => Math.abs(parseFloat(getComputedStyle(document.querySelector("[data-provider-address-value]")).fontSize) - normalSize * (enlarged ? 2 : 1)) < 0.01, { enlarged, normalSize });
          const metrics = await preview.evaluate(element => {
            const box = element.getBoundingClientRect(), range = document.createRange();
            range.selectNodeContents(element);
            return { fits: [...range.getClientRects()].every(rect => rect.left >= box.left - 1 && rect.right <= box.right + 1 && rect.top >= box.top - 1 && rect.bottom <= box.bottom + 1),
              contentFits: element.scrollHeight <= element.clientHeight + 1,
              nextTop: element.parentElement.querySelector("[data-provider-address-help]").getBoundingClientRect().top,
              bottom: box.bottom, pageOverflow: document.documentElement.scrollWidth - innerWidth };
          });
          assert.ok(metrics.fits && metrics.contentFits, "完整预设地址不得裁切或依赖原生单行滚动");
          assert.ok(metrics.nextTop >= metrics.bottom - 1, "地址不得遮挡帮助说明");
          assert.ok(metrics.pageOverflow <= 1, "地址不得撑宽页面");
          assert.equal(await input.inputValue(), address, "展示地址不得改变保存值");
          assert.equal(await input.evaluate(element => element.readOnly), true);
        }
        await dialog.locator('[name="providerId"]').selectOption("CUSTOM");
        assert.equal(await preview.isVisible(), false);
        assert.equal(await preview.textContent(), "", "切回自定义时必须立即清除旧预设地址");
        assert.equal(await input.evaluate(element => element.readOnly), false);
      }
      await dialog.getByRole("button", { name: "取消", exact: true }).click();
      await reset(page, "platform-model-connections");
    }
    await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
    const dialog = page.getByRole("dialog");
    await dialog.locator('[name="providerId"]').selectOption("CUSTOM");
    await dialog.locator('[name="runtimeBaseUrl"]').fill("https://public.example.invalid/v1");
    assert.equal(await dialog.locator("[data-provider-address-value]").isVisible(), false);
    assert.equal(await dialog.locator("[data-provider-address-value]").textContent(), "", "自定义输入不得复制到额外展示区");
  }, width));
}

for (const width of [390, 1024, 1440]) {
  test(`${width}px S1拒绝态恢复保留平台范围，租户不能借此进入平台`, () => visit(async page => {
    for (const screen of ["platform-model-connections", "platform-model-connection-detail"]) {
      await openForReview(page, `${base}?screen=${screen}&state=denied&review=0`);
      await page.locator(`main[data-screen="${screen}"][data-state="denied"]`).waitFor();
      await enlargeText(page);
      await page.getByRole("button", { name: "返回已授权入口", exact: true }).click();
      await page.waitForURL(current => current.searchParams.get("screen") !== screen);
      assert.equal(new URL(page.url()).searchParams.get("screen"), "platform-tenants", "平台连接任务应恢复到已交付的租户管理，不能转入普通会员入口");
      await page.locator('main[data-screen="platform-tenants"][data-state="success"]').waitFor();
    }
    await loginConsole(page, "tenant");
    await page.goto(`${base}?screen=platform-model-connections&state=denied&review=0`);
    await page.getByRole("heading", { name: "无法访问此页面", exact: true }).waitFor();
    assert.equal(await page.getByRole("button", { name: "返回已授权入口", exact: true }).count(), 0, "租户拒绝页不能取得平台任务恢复入口");
    assert.equal(await page.locator("main .data-table").count(), 0, "拒绝时不呈现平台连接数据");
    await page.getByRole("button", { name: "返回我的工作区", exact: true }).click();
    await page.locator('main[data-screen="tenant-overview"][data-state="success"]').waitFor();
  }, width));
}
