/* WP-0017 租户概览：只读既有隔离对象，不准备业务数据或保存浏览器材料。 */
const {
  readConfiguration, openLogin, fillLogin, submitLogin, assertIdentity,
  assertAnonymous, logoutWorkspace, request, requireCondition, SafeAssertionError,
} = require("./acceptance-login.cjs");

const settings = [["概览", "", "租户概览"], ["管理员", "/admins", "租户管理员"],
  ["资源配额", "/quotas", "资源配额"], ["可用功能", "/features", "可用功能"],
  ["登录安全", "/security", "登录安全"]];

/** 真实目标由显式资源标识选择，读取前不创建或更改对象。 */
function overviewConfiguration(environment = process.env) {
  requireCondition(!environment.SETUP, "概览切片只读，不提供业务对象准备");
  const configuration = readConfiguration(environment);
  const tenantId = environment.FOUNDATION_ACCEPTANCE_TENANT_ID;
  requireCondition(typeof tenantId === "string" && /^[1-9][0-9]{0,18}$/.test(tenantId)
    && BigInt(tenantId) <= 9223372036854775807n, "需要显式提供有效的既有验收租户标识");
  return { ...configuration, tenantId };
}

/** 只扩大可见文本一次，避免父子元素重复翻倍或遗漏不同语义标签。 */
async function enlargeText(page) {
  await page.evaluate(() => {
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
}

async function overview(page, configuration) {
  await page.goto(`${configuration.baseUrl}/platform/tenants/${configuration.tenantId}`, { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: "租户概览", exact: true }).waitFor();
  await page.locator(".tenant-overview__identity-facts").waitFor();
  await page.evaluate(() => document.fonts.ready);
}

async function layout(page, width) {
  const metrics = await page.evaluate(() => {
    const grid = document.querySelector(".tenant-overview__summary-grid");
    const facts = document.querySelector(".tenant-overview__identity-facts");
    const clipped = [...grid.querySelectorAll("h2,dt,dd,li")].some(element => {
      const style = getComputedStyle(element);
      return (style.overflowX !== "visible" && element.scrollWidth > element.clientWidth + 1)
        || (style.overflowY !== "visible" && element.scrollHeight > element.clientHeight + 1);
    });
    return { overflow: document.documentElement.scrollWidth - innerWidth, clipped,
      columns: getComputedStyle(grid).gridTemplateColumns.split(/\s+/).length,
      factsColumns: getComputedStyle(facts).gridTemplateColumns.split(/\s+/).length,
      moving: document.getAnimations().some(animation => animation.playState === "running"
        && animation.effect?.getTiming().duration > 1) };
  });
  requireCondition(metrics.overflow <= 1 && !metrics.clipped, "概览文字裁切或文档横向溢出");
  requireCondition(metrics.columns === (width <= 1040 ? 1 : 2)
    && metrics.factsColumns === (width <= 680 ? 1 : 2), "概览分栏与原型不一致");
  if (await page.evaluate(() => matchMedia("(prefers-reduced-motion: reduce)").matches)) {
    requireCondition(!metrics.moving, "reduced-motion 仍存在装饰动画");
  }
  for (const selector of [".tenant-overview__return", ".tenant-overview__settings a", ".tenant-overview__entry > a"]) {
    const elements = page.locator(selector);
    for (let i = 0; i < await elements.count(); i += 1) {
      const element = elements.nth(i);
      await element.scrollIntoViewIfNeeded();
      requireCondition(await element.evaluate(e => {
        const rect = e.getBoundingClientRect();
        return rect.height >= (innerWidth <= 680 ? 44 : 36)
          && e.contains(document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2));
      }), "概览导航或登录入口被遮挡或未满足控件基线");
    }
  }
}

async function run(configuration) {
  requireCondition(!configuration.setup, "概览切片只读，不提供业务对象准备");
  let browser;
  let stage = "启动";
  let passed = 0;
  async function scenario(name, check, identity = "platform", width = 1440) {
    stage = name;
    const context = await browser.newContext({ viewport: { width, height: 900 } });
    let writes = 0;
    try {
      const page = await context.newPage();
      page.setDefaultTimeout(15_000);
      if (identity) {
        await openLogin(page, configuration, identity);
        await fillLogin(page, configuration.credentials[identity], identity);
        await submitLogin(page);
        await assertIdentity(page, identity);
      }
      // 认证与业务分开计数；原型、概览和导航不得产生业务写请求。
      page.on("request", sent => {
        const path = new URL(sent.url()).pathname;
        if (path.startsWith("/api/v1/") && !path.startsWith("/api/v1/auth/")
          && !["GET", "HEAD", "OPTIONS"].includes(sent.method())) writes += 1;
      });
      await check(page, context);
      requireCondition(writes === 0, "只读概览或设置导航产生业务写入");
      passed += 1;
      process.stdout.write(JSON.stringify({ assertion: name, result: "通过", businessWrites: writes }) + "\n");
    } finally { await context.close(); }
  }
  try {
    requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定 Playwright 1.62.1 运行时");
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    await scenario("真实基本信息、安全保障与标题基线", async page => {
      await overview(page, configuration);
      const title = page.getByRole("heading", { name: "基础保护已开启", exact: true });
      requireCondition(await title.count() === 1, "安全保障主要标题仍被写成普通正文");
      requireCondition(await title.evaluate(e => {
        const style = getComputedStyle(e);
        return e.tagName === "H2" && style.fontSize === "20px" && style.fontWeight === "500"
          && style.lineHeight === "27px" && /Georgia/.test(style.fontFamily);
      }), "安全保障标题字体、字重或行高未承接原型");
      const tenant = await request(page, `/platform/tenants/${configuration.tenantId}`);
      const administrators = await request(page, `/platform/tenants/${configuration.tenantId}/administrators?pageNumber=1&pageSize=1`);
      requireCondition(tenant.status === 200 && administrators.status === 200
        && tenant.body.tenantCode === configuration.credentials.tenant.tenantCode, "真实验收对象与凭据所属租户不一致");
      requireCondition(await page.locator(".tenant-overview__identity h2").innerText() === tenant.body.displayName
        && await page.locator(".tenant-overview__code").innerText() === tenant.body.tenantCode, "概览未展示目标真实基本信息");
      const expected = administrators.body.totalElements === 1 ? administrators.body.items[0].loginName
        : administrators.body.totalElements > 1 ? `${administrators.body.totalElements} 名` : "暂未分配";
      requireCondition(await page.locator(".tenant-overview__identity-facts dd").last().innerText() === expected,
        "概览管理员摘要与真实分页事实不一致");
      requireCondition(await page.locator(".tenant-overview__identity-facts dt").allTextContents()
        .then(values => JSON.stringify(values) === JSON.stringify(["租户编码", "租户管理员"])), "概览增加了虚构区域或额外配额区");
      requireCondition(await page.locator(".tenant-overview__safety li").allTextContents()
        .then(values => JSON.stringify(values) === JSON.stringify(["租户数据隔离", "服务端权限校验", "密码与密钥不回显"])), "安全保障与原型不一致");
      const text = await page.locator(".tenant-overview").innerText();
      requireCondition(!text.includes(configuration.tenantId) && !text.includes(tenant.body.initialAdministratorSubjectId)
        && !/PLATFORM_|TENANT_/.test(text), "概览泄露内部标识或角色代码");
    });
    await scenario("五页设置导航保持同一目标与当前项", async page => {
      await overview(page, configuration);
      const nav = page.getByRole("navigation", { name: "租户设置", exact: true });
      requireCondition(await nav.getByRole("link").count() === 5, "概览五页导航缺失或重复");
      for (const [label, suffix, title] of settings) {
        await nav.getByRole("link", { name: label, exact: true }).click();
        await page.getByRole("heading", { name: title, exact: true }).waitFor();
        requireCondition(new URL(page.url()).pathname === `/platform/tenants/${configuration.tenantId}${suffix}`,
          "设置导航切换了目标租户");
        requireCondition(await nav.locator('[aria-current="page"]').innerText() === label, "设置导航当前项错误");
      }
    });
    await scenario("刷新、历史与返回目录保持真实路由", async page => {
      await overview(page, configuration);
      await page.getByRole("link", { name: "管理员", exact: true }).click();
      await page.getByRole("heading", { name: "租户管理员", exact: true }).waitFor();
      await page.goBack(); await page.locator(".tenant-overview__identity-facts").waitFor();
      await page.reload({ waitUntil: "networkidle" }); await page.locator(".tenant-overview__identity-facts").waitFor();
      await page.goForward(); await page.getByRole("heading", { name: "租户管理员", exact: true }).waitFor();
      await page.goBack(); await page.locator(".tenant-overview__identity-facts").waitFor();
      await page.getByRole("button", { name: "返回租户目录", exact: true }).click();
      requireCondition(new URL(page.url()).pathname === "/platform/tenants", "返回目录目标错误");
    });
    await scenario("不存在的目标与重试不展示上一租户", async page => {
      await overview(page, configuration);
      const missing = "9223372036854775807";
      requireCondition((await request(page, `/platform/tenants/${missing}`)).status === 404, "缺失目标反例已被占用");
      await page.goto(`${configuration.baseUrl}/platform/tenants/${missing}`, { waitUntil: "networkidle" });
      await page.getByRole("button", { name: "重新加载", exact: true }).waitFor();
      requireCondition(await page.locator(".tenant-overview__identity-facts").count() === 0, "缺失目标仍展示旧事实");
      await page.getByRole("button", { name: "重新加载", exact: true }).click();
      await page.getByRole("button", { name: "重新加载", exact: true }).waitFor();
      requireCondition(await page.locator(".tenant-overview__identity-facts").count() === 0, "缺失目标重试编造成功");
    });
    await scenario("真实网络失败恢复后重新读取", async (page, context) => {
      await overview(page, configuration);
      await page.getByRole("link", { name: "管理员", exact: true }).click();
      await page.getByRole("heading", { name: "租户管理员", exact: true }).waitFor();
      await context.setOffline(true);
      await page.getByRole("link", { name: "概览", exact: true }).click();
      await page.getByRole("button", { name: "重新加载", exact: true }).waitFor();
      requireCondition(await page.locator(".tenant-overview__identity-facts").count() === 0, "失败后继续显示旧事实");
      await context.setOffline(false);
      await page.getByRole("button", { name: "重新加载", exact: true }).click();
      await page.locator(".tenant-overview__identity-facts").waitFor();
    });
    await scenario("真实延迟下加载后显示服务器结果", async (page, context) => {
      await overview(page, configuration);
      await page.getByRole("link", { name: "管理员", exact: true }).click();
      await page.getByRole("heading", { name: "租户管理员", exact: true }).waitFor();
      const network = await context.newCDPSession(page);
      await network.send("Network.enable");
      await network.send("Network.emulateNetworkConditions", { offline: false, latency: 800, downloadThroughput: -1, uploadThroughput: -1 });
      await page.getByRole("link", { name: "概览", exact: true }).click();
      await page.getByText("正在读取租户信息…", { exact: true }).waitFor();
      requireCondition(await page.locator(".tenant-overview__identity-facts").count() === 0, "加载中仍显示旧事实");
      await page.locator(".tenant-overview__identity-facts").waitFor();
      await network.detach();
    });
    for (const width of [390, 1024, 1440]) {
      await scenario(`${width}px 布局和全部入口命中`, async page => { await overview(page, configuration); await layout(page, width); }, "platform", width);
    }
    await scenario("三尺寸 200% 文字无裁切与遮挡", async page => {
      for (const width of [390, 1024, 1440]) {
        await page.setViewportSize({ width, height: 900 }); await overview(page, configuration);
        await enlargeText(page); await layout(page, width);
      }
    });
    await scenario("键盘导航管理员与概览", async page => {
      await overview(page, configuration);
      const action = page.getByRole("link", { name: "管理员", exact: true });
      for (let i = 0; i < 30 && !await action.evaluate(e => e === document.activeElement); i += 1) await page.keyboard.press("Tab");
      requireCondition(await action.evaluate(e => e === document.activeElement), "管理员入口键盘不可达");
      await action.press("Enter"); await page.getByRole("heading", { name: "租户管理员", exact: true }).waitFor();
      await page.goBack(); await page.locator(".tenant-overview__identity-facts").waitFor();
    }, "platform", 390);
    await scenario("reduced-motion 保持概览与导航", async page => {
      await page.emulateMedia({ reducedMotion: "reduce" }); await overview(page, configuration); await layout(page, 390);
    }, "platform", 390);
    await scenario("租户直接访问概览和平台事实被拒绝", async page => {
      await page.goto(`${configuration.baseUrl}/platform/tenants/${configuration.tenantId}`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "当前会话无权访问此工作区", exact: true }).waitFor();
      requireCondition(await page.locator(".tenant-overview").count() === 0
        && (await request(page, `/platform/tenants/${configuration.tenantId}`)).status === 401, "租户会话读取了平台概览");
    }, "tenant");
    await scenario("匿名概览入口要求平台登录", async page => {
      await page.goto(`${configuration.baseUrl}/platform/tenants/${configuration.tenantId}`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "平台管理登录", exact: true }).waitFor(); await assertAnonymous(page);
      requireCondition(await page.locator(".tenant-overview").count() === 0, "匿名会话展示了概览");
    }, null);
    await scenario("退出后概览及读取接口均不可访问", async page => {
      await overview(page, configuration); await logoutWorkspace(page);
      await page.getByRole("heading", { name: "平台管理登录", exact: true }).waitFor(); await assertAnonymous(page);
      await page.goto(`${configuration.baseUrl}/platform/tenants/${configuration.tenantId}`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "平台管理登录", exact: true }).waitFor();
      requireCondition(await page.locator(".tenant-overview").count() === 0
        && (await request(page, `/platform/tenants/${configuration.tenantId}`)).status === 401, "退出后仍可读取概览");
    });
    process.stdout.write(`租户概览切片：${passed}/15 场景通过；业务写入为 0。\n`);
  } catch (error) {
    throw new Error(`真实部署概览验收失败：${stage}${error instanceof SafeAssertionError ? `：${error.message}` : ""}；未保存截图、trace 或会话材料。`);
  } finally { await browser?.close(); }
}

module.exports = { overviewConfiguration, enlargeText, run };
if (require.main === module) {
  try {
    run(overviewConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; });
  } catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
