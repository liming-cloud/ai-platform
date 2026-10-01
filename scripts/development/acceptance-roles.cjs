/* WP-0017：真实部署的固定角色与导航验收；只登录，不创建业务对象或保存浏览器材料。 */
const {
  readConfiguration, openLogin, fillLogin, submitLogin, assertIdentity,
  assertAnonymous, logoutWorkspace, request, requireCondition, SafeAssertionError,
} = require("./acceptance-login.cjs");

async function roleView(page, baseUrl) {
  await page.goto(`${baseUrl}/platform/roles`, { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: "平台固定角色", exact: true }).waitFor();
  await page.evaluate(() => document.fonts.ready);
  const facts = page.locator(".platform-role-page__fact-list");
  requireCondition(await facts.locator("div").count() === 4, "固定角色事实数量不符");
  for (const [label, value] of [["平台治理", "租户生命周期与模型供应链"], ["运营读取", "仅脱敏聚合"],
    ["租户正文", "禁止访问"], ["成员明细", "禁止访问"]]) {
    const row = facts.locator("div").filter({ has: page.getByText(label, { exact: true }) });
    requireCondition(await row.locator("dd").innerText() === value, "固定角色职责与已接受合同不符");
  }
  requireCondition(await page.getByRole("tab").count() === 0, "角色页仍存在额外任务页签");
  requireCondition(await page.getByRole("button", { name: "查看详情", exact: true }).count() === 0,
    "固定职责仍要求打开额外详情");
  const body = await page.locator(".platform-role-page").innerText();
  requireCondition(!/TENANT_|PLATFORM_|租户管理员|普通用户/.test(body), "角色页混入租户角色或内部代码");
  requireCondition(await page.getByRole("button", { name: "管理账户", exact: true }).count() === 1,
    "唯一账户导航缺失或重复");
  const parity = await page.evaluate(() => {
    const header = document.querySelector(".page-heading").getBoundingClientRect();
    const workspace = document.querySelector(".platform-role-page__workspace").getBoundingClientRect();
    const action = document.querySelector(".page-heading > button");
    const title = document.querySelector(".platform-role-page__heading h2");
    const attention = document.querySelector(".platform-role-page__attention h2");
    return { gap: workspace.top - header.bottom, width: innerWidth,
      fullWidthAction: Math.abs(action.getBoundingClientRect().width - header.width) <= 1,
      actionWeight: getComputedStyle(action).fontWeight,
      titleFont: getComputedStyle(title).fontFamily, attentionFont: getComputedStyle(attention).fontFamily,
      labelColor: getComputedStyle(document.querySelector(".platform-role-page__fact-list dt")).color };
  });
  requireCondition(Math.abs(parity.gap - 24) <= 1 && /Georgia/.test(parity.titleFont)
    && /Georgia/.test(parity.attentionFont) && parity.labelColor === "rgb(94, 93, 89)"
    && parity.actionWeight === "620" && (parity.width > 680 || parity.fullWidthAction),
  "角色职责的字体、间距或移动端主要操作与固定原型不符");
}

async function layout(page, width) {
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  const facts = await page.evaluate(() => {
    const button = [...document.querySelectorAll("button")].find(element => element.textContent.trim() === "管理账户");
    const rect = button.getBoundingClientRect();
    const clippedText = [...document.querySelectorAll(".platform-role-page h2,.platform-role-page dt,.platform-role-page dd")]
      .some(element => {
        const style = getComputedStyle(element);
        return (style.overflowX !== "visible" && element.scrollWidth > element.clientWidth + 1)
          || (style.overflowY !== "visible" && element.scrollHeight > element.clientHeight + 1);
      });
    return { overflow: document.documentElement.scrollWidth - innerWidth, buttonHeight: rect.height,
      buttonWidth: rect.width, clippedText,
      hit: button.contains(document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2)) };
  });
  requireCondition(facts.overflow <= 1, "角色页出现文档级横向裁切");
  requireCondition(!facts.clippedText, "角色页职责文字发生裁切");
  requireCondition(facts.buttonHeight >= (width <= 680 ? 44 : 36) && facts.buttonWidth >= 44,
    "角色页主要操作未满足控件基线");
  requireCondition(facts.hit, "角色页主要操作被其他内容遮挡");
}

async function run(configuration) {
  requireCondition(!configuration.setup, "角色切片不提供业务对象准备");
  let browser;
  let stage = "启动";
  let passed = 0;
  async function scenario(name, check, identity = "platform", width = 1440) {
    stage = name;
    const context = await browser.newContext({ viewport: { width, height: 960 } });
    let writes = 0;
    try {
      const page = await context.newPage();
      if (identity) {
        await openLogin(page, configuration, identity);
        await fillLogin(page, configuration.credentials[identity], identity);
        await submitLogin(page);
        await assertIdentity(page, identity);
      }
      // 登录和退出属于认证；本片其余网络行为必须保持业务零写入。
      page.on("request", sent => {
        const path = new URL(sent.url()).pathname;
        if (path.startsWith("/api/v1/") && !path.startsWith("/api/v1/auth/")
          && !["GET", "HEAD", "OPTIONS"].includes(sent.method())) writes += 1;
      });
      await check(page);
      requireCondition(writes === 0, "固定角色或账户导航产生业务写入");
      passed += 1;
      process.stdout.write(JSON.stringify({ assertion: name, result: "通过", businessWrites: writes }) + "\n");
    } finally { await context.close(); }
  }
  try {
    requireCondition(require("playwright/package.json").version === "1.62.1", "需要现有锁定 Playwright 1.62.1 运行时");
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    await scenario("唯一固定角色与完整职责", page => roleView(page, configuration.baseUrl));
    await scenario("管理账户及刷新前后退", async page => {
      await roleView(page, configuration.baseUrl);
      await page.getByRole("button", { name: "管理账户", exact: true }).click();
      await page.getByRole("heading", { name: "平台账户", exact: true }).waitFor();
      requireCondition(new URL(page.url()).pathname === "/platform/accounts", "账户导航目标错误");
      await page.goBack();
      await page.getByRole("heading", { name: "平台固定角色", exact: true }).waitFor();
      await page.reload({ waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "平台固定角色", exact: true }).waitFor();
      await page.goForward();
      await page.getByRole("heading", { name: "平台账户", exact: true }).waitFor();
    });
    for (const width of [390, 1024, 1440]) {
      await scenario(`${width}px 布局和控件`, async page => {
        await roleView(page, configuration.baseUrl);
        await layout(page, width);
      }, "platform", width);
    }
    await scenario("三尺寸 200% 文字", async page => {
      for (const width of [390, 1024, 1440]) {
        await page.setViewportSize({ width, height: 960 });
        await roleView(page, configuration.baseUrl);
        await page.evaluate(() => {
          // 按可见文本的直属元素统一翻倍，避免原型与生产使用不同标签时遗漏文字。
          const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
          const parents = new Set();
          while (walker.nextNode()) {
            const element = walker.currentNode.parentElement;
            if (walker.currentNode.textContent.trim() && element instanceof HTMLElement
              && element.getBoundingClientRect().width > 0 && element.getBoundingClientRect().height > 0
              && getComputedStyle(element).visibility !== "hidden") parents.add(element);
          }
          const elements = [...parents];
          const sizes = elements.map(element => parseFloat(getComputedStyle(element).fontSize));
          elements.forEach((element, index) => { element.style.fontSize = `${sizes[index] * 2}px`; });
        });
        await layout(page, width);
      }
    });
    await scenario("键盘进入账户与返回", async page => {
      await roleView(page, configuration.baseUrl);
      const action = page.getByRole("button", { name: "管理账户", exact: true });
      for (let index = 0; index < 30 && !await action.evaluate(element => element === document.activeElement); index += 1) {
        await page.keyboard.press("Tab");
      }
      requireCondition(await action.evaluate(element => element === document.activeElement), "账户导航未取得键盘焦点");
      await action.press("Enter");
      await page.getByRole("heading", { name: "平台账户", exact: true }).waitFor();
      await page.goBack();
      await page.getByRole("heading", { name: "平台固定角色", exact: true }).waitFor();
    });
    await scenario("reduced-motion 保持角色任务", async page => {
      await page.emulateMedia({ reducedMotion: "reduce" });
      await roleView(page, configuration.baseUrl);
      await layout(page, 390);
      requireCondition(await page.evaluate(() => matchMedia("(prefers-reduced-motion: reduce)").matches),
        "reduced-motion 未生效");
    }, "platform", 390);
    await scenario("租户直接访问平台角色拒绝", async page => {
      await page.goto(`${configuration.baseUrl}/platform/roles`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "当前会话无权访问此工作区", exact: true }).waitFor();
      requireCondition(await page.locator(".platform-role-page").count() === 0, "租户会话加载了平台角色任务");
      requireCondition((await request(page, "/platform/accounts")).status === 401, "租户会话获得了平台账户访问");
    }, "tenant");
    await scenario("匿名角色入口要求登录", async page => {
      await page.goto(`${configuration.baseUrl}/platform/roles`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "平台管理登录", exact: true }).waitFor();
      await assertAnonymous(page);
      requireCondition(await page.locator(".platform-role-page").count() === 0, "匿名会话加载了平台角色任务");
    }, null);
    await scenario("退出后角色与账户均不可访问", async page => {
      await roleView(page, configuration.baseUrl);
      await logoutWorkspace(page);
      await page.getByRole("heading", { name: "平台管理登录", exact: true }).waitFor();
      await assertAnonymous(page);
      await page.goto(`${configuration.baseUrl}/platform/roles`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "平台管理登录", exact: true }).waitFor();
      requireCondition(await page.locator(".platform-role-page").count() === 0, "退出后角色任务仍可见");
      requireCondition((await request(page, "/platform/accounts")).status === 401, "退出后仍可读取平台账户");
    });
    process.stdout.write(`平台固定角色切片：${passed}/11 场景通过；业务写入为 0。\n`);
  } catch (error) {
    const detail = error instanceof SafeAssertionError ? `：${error.message}` : "";
    throw new Error(`真实部署角色验收失败：${stage}${detail}；未保存截图、trace 或会话材料。`);
  } finally { await browser?.close(); }
}

if (require.main === module) {
  try {
    const configuration = readConfiguration();
    run(configuration).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; });
  } catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
