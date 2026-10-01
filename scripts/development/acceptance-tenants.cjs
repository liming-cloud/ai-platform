/* WP-0017：目录只读验收复用正式身份与既有对象，禁止准备数据或保存会话材料。 */
const {
  readConfiguration, openLogin, fillLogin, submitLogin, assertIdentity, assertAnonymous,
  logoutWorkspace, request, requireCondition, SafeAssertionError,
} = require("./acceptance-login.cjs");
const { enlargeText } = require("./acceptance-overview.cjs");

/** 验收对象显式绑定；只读优先于任何准备参数，凭据仍由独占文件读取。 */
function tenantsConfiguration(environment = process.env) {
  requireCondition(!environment.SETUP, "目录切片只读，不提供业务对象准备");
  const configuration = readConfiguration(environment);
  const tenantId = environment.FOUNDATION_ACCEPTANCE_TENANT_ID;
  requireCondition(typeof tenantId === "string" && /^[1-9][0-9]{0,18}$/.test(tenantId)
    && BigInt(tenantId) <= 9223372036854775807n, "需要显式提供有效的既有验收租户标识");
  return { ...configuration, tenantId };
}

async function directory(page, configuration, parameters = {}) {
  await page.goto(`${configuration.baseUrl}/platform/tenants?${new URLSearchParams(parameters)}`, { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: "租户目录", exact: true }).waitFor();
  await page.locator(".tenant-directory-table").waitFor();
  await page.evaluate(() => document.fonts.ready);
}

/** 不放宽文字裁切和命中检查；移动事实块必须随真实名称及文字放大撑开。 */
async function layout(page, width) {
  const metrics = await page.evaluate(() => {
    const rows = [...document.querySelectorAll(".tenant-directory-table tbody tr[data-tenant-id]")];
    const clipped = rows.some(row => {
      const name = row.querySelector("strong");
      const code = row.querySelector(".tenant-directory-identity > span");
      return [name, code].some(element => {
        const style = getComputedStyle(element);
        return style.textOverflow === "ellipsis" || style.whiteSpace === "nowrap"
          || element.scrollWidth > element.clientWidth + 1 || element.scrollHeight > element.clientHeight + 1;
      }) || name.getBoundingClientRect().bottom > code.getBoundingClientRect().top + 1;
    });
    const overlap = innerWidth <= 680 && rows.some(row => {
      const cells = [...row.querySelectorAll("td")];
      return cells.some((cell, index) => index > 0
        && cells[index - 1].getBoundingClientRect().bottom > cell.getBoundingClientRect().top + 1);
    });
    return { overflow: document.documentElement.scrollWidth - innerWidth, clipped, overlap,
      moving: document.getAnimations().some(animation => animation.playState === "running"
        && animation.effect?.getTiming().duration > 1) };
  });
  requireCondition(metrics.overflow <= 1 && !metrics.clipped && !metrics.overlap, "目录文字裁切、事实重叠或页面溢出");
  if (await page.evaluate(() => matchMedia("(prefers-reduced-motion: reduce)").matches))
    requireCondition(!metrics.moving, "reduced-motion 仍有运动");
  for (const control of await page.locator(".tenant-directory-page button:enabled, .tenant-directory-page input, .tenant-directory-page select").all()) {
    await control.scrollIntoViewIfNeeded();
    requireCondition(await control.evaluate((element, mobile) => {
      const box = element.getBoundingClientRect();
      const target = document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2);
      return box.height >= (mobile ? 44 : element.closest(".tenant-directory-table, .tenant-directory-pagination") ? 32 : 36)
        && (target === element || element.contains(target));
    }, width <= 680), "目录控件被遮挡或低于原型尺寸");
  }
}

async function run(configuration) {
  requireCondition(!configuration.setup, "目录切片只读，不提供业务对象准备");
  let browser;
  let context;
  let stage = "启动";
  let passed = 0;
  let writes = 0;
  let pageErrors = 0;
  const observe = page => {
    page.on("pageerror", () => { pageErrors += 1; });
    page.on("request", outgoing => {
      const path = new URL(outgoing.url()).pathname;
      if (path.startsWith("/api/v1/") && !["GET", "HEAD", "OPTIONS"].includes(outgoing.method())
        && !["/api/v1/auth/browser-sessions", "/api/v1/auth/browser-session"].includes(path)) writes += 1;
    });
  };
  const check = async (name, action) => {
    stage = name; await action();
    requireCondition(writes === 0 && pageErrors === 0, "只读目录发生业务写入或页面异常");
    passed += 1;
    process.stdout.write(JSON.stringify({ assertion: name, result: "通过", businessWrites: writes, pageErrors }) + "\n");
  };
  const query = (page, parameters = {}) => request(page,
    `/platform/tenants?${new URLSearchParams({ pageNumber: "1", pageSize: "100", ...parameters })}`);
  const code = configuration.credentials.tenant.tenantCode;
  try {
    requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定 Playwright 1.62.1 运行时");
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage(); observe(page); page.setDefaultTimeout(15_000);
    await check("平台正式身份与精确目标目录事实", async () => {
      await openLogin(page, configuration, "platform"); await fillLogin(page, configuration.credentials.platform, "platform");
      await submitLogin(page); await assertIdentity(page, "platform");
      const result = await query(page, { query: code });
      requireCondition(result.status === 200 && result.body.totalElements === 1
        && result.body.items.length === 1 && result.body.items[0].tenantId === configuration.tenantId
        && result.body.items[0].administratorCount === 1, "目录未绑定真实既有租户与管理员资格数量");
      requireCondition(!/password|credential|token|secret|initialAdministratorSubjectId/i.test(JSON.stringify(result.body)), "目录返回秘密或身份明细");
    });
    let all;
    await check("真实分页、总数、排序和字面搜索合同", async () => {
      const first = await query(page); all = [...first.body.items];
      requireCondition(first.status === 200 && first.body.totalElements >= 20, "既有分页样本不足");
      for (let number = 2; number <= first.body.totalPages; number += 1) {
        const next = await query(page, { pageNumber: String(number) });
        requireCondition(next.status === 200 && next.body.totalElements === first.body.totalElements, "读取期间总数变化");
        all.push(...next.body.items);
      }
      requireCondition(all.length === first.body.totalElements && new Set(all.map(item => item.tenantId)).size === all.length, "分页重复或遗漏");
      for (const literal of ["%", "_", "\\", "%_\\"]) {
        const expected = all.filter(item => item.displayName.includes(literal) || item.tenantCode.includes(literal));
        const result = await query(page, { query: literal });
        requireCondition(result.status === 200 && result.body.totalElements === expected.length
          && JSON.stringify(result.body.items.map(item => item.tenantId)) === JSON.stringify(expected.slice(0, 100).map(item => item.tenantId)), "字面搜索扩大读取范围");
      }
      for (const parameters of [{ query: "x".repeat(65) }, { query: "\0" }, { status: "DISABLED" }])
        requireCondition((await query(page, parameters)).status === 400, "非法条件未拒绝");
    });
    await check("目录四列、摘要与真实管理员资格", async () => {
      await directory(page, configuration, { q: code });
      requireCondition(await page.getByRole("heading", { level: 1 }).count() === 1, "目录出现重复页面标题");
      requireCondition(JSON.stringify(await page.locator("thead th").allTextContents())
        === JSON.stringify(["租户", "状态", "租户管理员", "操作"]), "目录列未承接原型");
      const target = all.find(item => item.tenantId === configuration.tenantId);
      const row = page.locator("tr[data-tenant-id]");
      requireCondition(await row.count() === 1 && await row.locator("strong").innerText() === target.displayName
        && await row.locator(".tenant-directory-identity > span").innerText() === code
        && await row.locator("td[data-label='租户管理员']").innerText() === "已分配", "目录未展示当前目标真实事实");
      requireCondition((await page.locator(".tenant-directory-summary").innerText()).includes("安全边界异常：尚未核实"), "目录伪造异常数字");
      await directory(page, configuration, { pageSize: "100" });
      for (const tenant of all.slice(0, 100)) {
        requireCondition(await page.locator(`tr[data-tenant-id='${tenant.tenantId}'] td[data-label='租户管理员']`).innerText()
          === (tenant.administratorCount > 0 ? "已分配" : "未分配"), "管理员摘要与实际资格数量不一致");
      }
      process.stdout.write(JSON.stringify({ coverage: "既有资格数量", samples: all.length,
        zeroQualificationSamples: all.filter(item => item.administratorCount === 0).length }) + "\n");
      requireCondition(await page.getByRole("button", { name: /激活租户|暂停租户|管理租户管理员|分配管理员/ }).count() === 0, "目录仍保留额外写操作");
    });
    await check("搜索筛选写入 URL 并恢复刷新和历史", async () => {
      await directory(page, configuration);
      await page.getByRole("searchbox", { name: "搜索租户" }).fill(` ${code.toUpperCase()} `);
      await page.waitForURL(url => url.searchParams.get("q") === code.toUpperCase());
      await page.locator("tr[data-tenant-id]").waitFor();
      requireCondition(await page.locator("tr[data-tenant-id]").count() === 1, "真实搜索未过滤唯一对象");
      await page.getByRole("combobox", { name: "状态", exact: true }).selectOption("ACTIVE");
      await page.waitForURL(url => url.searchParams.get("status") === "ACTIVE"); await page.locator("tr[data-tenant-id]").waitFor();
      await page.reload({ waitUntil: "networkidle" });
      requireCondition(await page.getByRole("searchbox").inputValue() === code.toUpperCase()
        && await page.getByRole("combobox", { name: "状态", exact: true }).inputValue() === "ACTIVE", "刷新丢失搜索条件");
      await page.goBack(); await page.locator("tr[data-tenant-id]").waitFor();
      requireCondition(await page.getByRole("combobox", { name: "状态", exact: true }).inputValue() === "ALL", "后退丢失上一筛选状态");
      await page.goForward(); await page.locator("tr[data-tenant-id]").waitFor();
      requireCondition(await page.getByRole("combobox", { name: "状态", exact: true }).inputValue() === "ACTIVE", "前进丢失筛选状态");
    });
    await check("10/20/50/100、页码、跳转与错误反馈", async () => {
      await directory(page, configuration);
      for (const size of [10, 20, 50, 100]) {
        await page.getByRole("combobox", { name: "每页条数" }).selectOption(String(size));
        await page.waitForURL(url => url.searchParams.get("pageSize") === String(size));
        await page.locator("tr[data-tenant-id]").waitFor();
        requireCondition(JSON.stringify(await page.locator("tr[data-tenant-id]").evaluateAll(rows => rows.map(row => row.dataset.tenantId)))
          === JSON.stringify(all.slice(0, size).map(item => item.tenantId)), "页大小改变后结果或排序错误");
      }
      await page.getByRole("combobox", { name: "每页条数" }).selectOption("10"); await page.locator("tr[data-tenant-id]").waitFor();
      await page.getByRole("button", { name: "下一页", exact: true }).click();
      await page.waitForURL(url => url.searchParams.get("page") === "2"); await page.locator("tr[data-tenant-id]").waitFor();
      requireCondition(JSON.stringify(await page.locator("tr[data-tenant-id]").evaluateAll(rows => rows.map(row => row.dataset.tenantId)))
        === JSON.stringify(all.slice(10, 20).map(item => item.tenantId)), "第二页结果错误");
      await page.getByRole("spinbutton", { name: "跳转页码" }).fill("0"); await page.getByRole("button", { name: "跳转", exact: true }).click();
      await page.getByRole("alert").filter({ hasText: "请输入 1–" }).waitFor();
      requireCondition(new URL(page.url()).searchParams.get("page") === "2", "非法跳转改变了页面");
      await page.getByRole("spinbutton", { name: "跳转页码" }).fill("1"); await page.getByRole("button", { name: "跳转", exact: true }).click();
      await page.waitForURL(url => url.searchParams.get("page") === "1"); await page.locator("tr[data-tenant-id]").waitFor();
    });
    await check("三种生命周期、空结果与越界保留事实", async () => {
      for (const status of ["PROVISIONING", "ACTIVE", "SUSPENDED"]) {
        await directory(page, configuration, { status, pageSize: "100" });
        const expected = all.filter(item => item.status === status);
        requireCondition(await page.locator("tr[data-tenant-id]").count() === expected.length
          && (await page.locator(".tenant-directory-pagination-summary").innerText()).includes(`共 ${expected.length} 条`), "状态过滤与真实数量不一致");
      }
      await directory(page, configuration, { q: "wp0017-unmatched-directory-20261001" });
      requireCondition(await page.locator("tr[data-tenant-id]").count() === 0
        && await page.locator(".tenant-directory-pagination-summary").innerText() === "共 0 条 · 0–0 条", "空结果伪造旧数据或计数");
      await directory(page, configuration, { q: code, page: "99" });
      requireCondition(await page.locator("tr[data-tenant-id]").count() === 0
        && await page.locator(".tenant-directory-pagination-summary").innerText() === "共 1 条 · 0–0 条", "越界页丢失总数或复用旧数据");
      await page.getByRole("button", { name: "返回第一页" }).click(); await page.locator("tr[data-tenant-id]").waitFor();
    });
    await check("建立入口取消及详情导航均只读", async () => {
      await directory(page, configuration, { q: code });
      await page.getByRole("button", { name: "查看详情", exact: true }).click();
      await page.getByRole("heading", { name: "租户概览", exact: true }).waitFor();
      requireCondition(new URL(page.url()).pathname === `/platform/tenants/${configuration.tenantId}`, "详情导航目标错误");
      await page.goBack(); await page.locator("tr[data-tenant-id]").waitFor();
      await page.getByRole("button", { name: "建立新租户", exact: true }).click();
      await page.getByRole("heading", { name: "建立新租户", exact: true }).waitFor();
      await page.getByRole("button", { name: "取消", exact: true }).click();
      await page.getByRole("heading", { name: "租户目录", exact: true }).waitFor();
    });
    await check("迟到请求不能覆盖后退恢复的页码", async () => {
      await directory(page, configuration, { pageSize: "10", page: "1" });
      const cdp = await context.newCDPSession(page); await cdp.send("Network.enable");
      await cdp.send("Network.emulateNetworkConditions", { offline: false, latency: 900, downloadThroughput: -1, uploadThroughput: -1 });
      try {
        const sent = page.waitForRequest(request => new URL(request.url()).pathname === "/api/v1/platform/tenants"
          && new URL(request.url()).searchParams.get("pageNumber") === "2");
        await page.getByRole("button", { name: "下一页", exact: true }).click(); await sent;
        requireCondition(await page.locator("tr[data-tenant-id]").count() === 0, "切换加载复用了旧数据");
        await page.goBack(); await page.locator("tr[data-tenant-id]").waitFor(); await page.waitForLoadState("networkidle");
        requireCondition(JSON.stringify(await page.locator("tr[data-tenant-id]").evaluateAll(rows => rows.map(row => row.dataset.tenantId)))
          === JSON.stringify(all.slice(0, 10).map(item => item.tenantId)), "迟到响应覆盖了恢复的第一页");
      } finally { await cdp.send("Network.emulateNetworkConditions", { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 }); await cdp.detach(); }
    });
    await check("真实断网读取失败与恢复", async () => {
      await context.setOffline(true);
      try {
        await page.getByRole("button", { name: "下一页", exact: true }).click();
        await page.getByRole("button", { name: "重新加载", exact: true }).waitFor();
        requireCondition(await page.locator("tr[data-tenant-id]").count() === 0, "失败后仍显示旧数据");
      } finally { await context.setOffline(false); }
      await page.getByRole("button", { name: "重新加载", exact: true }).click(); await page.locator("tr[data-tenant-id]").waitFor();
    });
    for (const width of [390, 1024, 1440]) {
      await check(`${width}px 正常与 200% 文字`, async () => {
        await page.setViewportSize({ width, height: 900 }); await directory(page, configuration, { q: code });
        await layout(page, width); await enlargeText(page); await layout(page, width);
      });
    }
    await check("390px 键盘与 reduced-motion 导航", async () => {
      await page.emulateMedia({ reducedMotion: "reduce" }); await directory(page, configuration, { q: code }); await layout(page, 390);
      await page.locator("body").click({ position: { x: 2, y: 2 } });
      const detail = page.getByRole("button", { name: "查看详情", exact: true });
      for (let i = 0; i < 40 && !await detail.evaluate(element => element === document.activeElement); i += 1) await page.keyboard.press("Tab");
      requireCondition(await detail.evaluate(element => element === document.activeElement), "详情入口键盘不可达");
      await detail.press("Enter"); await page.getByRole("heading", { name: "租户概览", exact: true }).waitFor();
    });
    await check("租户身份无法读取或进入平台目录", async () => {
      const tenant = await browser.newContext();
      try {
        const probe = await tenant.newPage(); observe(probe);
        await openLogin(probe, configuration, "tenant"); await fillLogin(probe, configuration.credentials.tenant, "tenant");
        await submitLogin(probe); await assertIdentity(probe, "tenant");
        const denied = await query(probe, { query: code });
        requireCondition(denied.status === 401 && !Object.hasOwn(denied.body ?? {}, "items")
          && !Object.hasOwn(denied.body ?? {}, "totalElements"), "租户身份读到平台事实");
        await probe.goto(`${configuration.baseUrl}/platform/tenants`, { waitUntil: "networkidle" });
        await probe.getByRole("heading", { name: "当前会话无权访问此工作区", exact: true }).waitFor();
        requireCondition(await probe.locator(".tenant-directory-page").count() === 0, "租户显示平台目录");
      } finally { await tenant.close(); }
    });
    await check("退出、匿名与非法条件均先拒绝身份", async () => {
      await directory(page, configuration, { q: code }); await logoutWorkspace(page); await assertAnonymous(page);
      await page.goto(`${configuration.baseUrl}/platform/tenants`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "平台管理登录", exact: true }).waitFor();
      const denied = await query(page, { status: "DISABLED" });
      requireCondition(denied.status === 401 && !Object.hasOwn(denied.body ?? {}, "totalElements")
        && await page.locator(".tenant-directory-page").count() === 0, "退出后目录未失败关闭");
    });
    process.stdout.write(`租户目录切片：${passed}/15 场景通过；业务写入 ${writes}，页面异常 ${pageErrors}。\n`);
  } catch (error) {
    throw new Error(`真实部署目录验收失败：${stage}${error instanceof SafeAssertionError ? `：${error.message}` : ""}；未保存截图、trace 或会话材料。`);
  } finally { await context?.close(); await browser?.close(); }
}

module.exports = { tenantsConfiguration, directory, layout, run };
if (require.main === module) {
  try { run(tenantsConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
