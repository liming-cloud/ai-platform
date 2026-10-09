/* WP-0018：固定角色和成员导航只读验收，复用现有身份，不准备业务对象。 */
const fs = require("node:fs");
const {
  readConfiguration, openLogin, fillLogin, submitLogin, assertIdentity, assertAnonymous,
  logoutWorkspace, request, requireCondition, SafeAssertionError,
} = require("./acceptance-login.cjs");
const { freshOutputFile, memberLogin, verifyLayout } = require("./acceptance-member-account.cjs");
const { withTextScale } = require("./acceptance-onboarding.cjs");

function readRoleConfiguration(environment = process.env) {
  requireCondition(!environment.SETUP, "固定角色切片只读，不接受外部 SETUP");
  const configuration = readConfiguration(environment);
  const member = configuration.credentials.member;
  requireCondition(typeof member?.loginName === "string" && member.loginName.length > 0
    && typeof member?.password === "string" && member.password.length > 0,
  "固定角色验收需要现有普通成员凭据，不能创建替代对象");
  return { ...configuration, outputFile: freshOutputFile(environment) };
}

function verifyAdministratorAccount(account) {
  const keys = ["membershipId", "loginName", "membershipRole", "status", "version", "identityVersion", "permissionVersion",
    "createdAt", "conversationFeatures", "featureVersion", "assistantIds", "passwordResetEligible"];
  requireCondition(account && Object.keys(account).length === keys.length && keys.every(key => Object.hasOwn(account, key))
    && account.membershipRole === "TENANT_ADMINISTRATOR" && account.passwordResetEligible === false,
  "管理员账户投影缺少固定角色保护，或包含非公开字段");
}

async function roleView(page, baseUrl) {
  await page.goto(`${baseUrl}/access/roles`, { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: "租户固定角色", exact: true }).waitFor();
  await page.evaluate(() => document.fonts.ready);
  const facts = page.locator(".tenant-role-page__fact-list");
  requireCondition(await facts.locator("div").count() === 4, "固定角色职责数量不符");
  for (const [label, value] of [
    ["租户管理员", "管理本组织知识、模型、助手与普通成员"],
    ["普通成员", "仅使用已授权助手、本人历史与引用"],
    ["自定义角色", "不属于当前产品契约"], ["授权依据", "服务端能力投影"],
  ]) {
    const row = facts.locator("div").filter({ has: page.getByText(label, { exact: true }) });
    requireCondition(await row.locator("dd").innerText() === value, "固定角色职责与原型不符");
  }
  requireCondition(await page.locator(".page-context").innerText() === "租户访问与知识",
    "角色页范围标题与原型不符");
  requireCondition(await page.locator(".tenant-role-page__attention h2").innerText()
    === "管理员与普通成员职责固定；配置成员功能不会改变角色。", "角色职责说明与原型不符");
  requireCondition(await page.getByRole("button", { name: "管理成员", exact: true }).count() === 1
    && await page.getByRole("tab").count() === 0 && await page.getByRole("dialog").count() === 0,
  "固定角色页存在额外任务或编辑入口");
  requireCondition(!/TENANT_|PLATFORM_|角色分配|角色绑定管理|查看详情/.test(await page.locator("main").innerText()),
    "固定角色页泄漏内部代码或暗示角色可编辑");
}

async function roleLayout(page, width) {
  await verifyLayout(page);
  await page.evaluate(() => scrollTo(0, 0));
  const facts = await page.evaluate(() => {
    const header = document.querySelector(".page-heading").getBoundingClientRect();
    const action = document.querySelector(".page-heading > button");
    const workspace = document.querySelector(".tenant-role-page__workspace").getBoundingClientRect();
    const style = element => getComputedStyle(document.querySelector(element));
    const overlaps = (left, right) => Math.min(left.right, right.right) - Math.max(left.left, right.left) > 1
      && Math.min(left.bottom, right.bottom) - Math.max(left.top, right.top) > 1;
    const scope = document.querySelector(".product-topbar__context").getBoundingClientRect();
    const account = document.querySelector(".product-account").getBoundingClientRect();
    const sidebar = document.querySelector(".product-sidebar").getBoundingClientRect();
    const nav = [...document.querySelectorAll(".product-navigation button")].map(element => element.getBoundingClientRect());
    return { gap: workspace.top - header.bottom, titleFont: style(".tenant-role-page__heading h2").fontFamily,
      attentionFont: style(".tenant-role-page__attention h2").fontFamily,
      labelColor: style(".tenant-role-page__fact-list dt").color,
      actionWeight: getComputedStyle(action).fontWeight,
      fullWidthAction: Math.abs(action.getBoundingClientRect().width - header.width) <= 1,
      overflow: document.documentElement.scrollWidth - innerWidth,
      shellCollision: overlaps(scope, account) || nav.some(rect => overlaps(rect, scope)),
      navContained: innerWidth > 680 || nav.every(rect => rect.bottom <= sidebar.bottom + 1),
      reducedMotion: matchMedia("(prefers-reduced-motion: reduce)").matches };
  });
  requireCondition(Math.abs(facts.gap - 24) <= 1 && /Georgia/.test(facts.titleFont)
    && /Georgia/.test(facts.attentionFont) && facts.labelColor === "rgb(94, 93, 89)"
    && facts.actionWeight === "620" && (width > 680 || facts.fullWidthAction),
  "固定角色页字体、间距或移动端操作与原型不符");
  requireCondition(facts.overflow <= 1 && !facts.shellCollision && facts.navContained && facts.reducedMotion,
    "固定角色页或共享导航裁切、遮挡，或 reduced-motion 未生效");
}

async function run(configuration) {
  const descriptor = fs.openSync(configuration.outputFile, "wx", 0o600);
  const report = { workPackage: "WP-0018", slice: "roles", status: "IN_PROGRESS", scenarios: [] };
  const persist = () => {
    fs.ftruncateSync(descriptor, 0);
    fs.writeSync(descriptor, JSON.stringify(report, null, 2), 0, "utf8");
    fs.fsyncSync(descriptor);
  };
  let browser;
  let stage = "启动";
  persist();
  async function scenario(name, check, identity = "tenant") {
    stage = name;
    let context;
    let page;
    let writes = 0;
    let errors = 0;
    try {
      if (identity === "member") {
        ({ context, page } = await memberLogin(browser, configuration, configuration.credentials.member));
      } else {
        context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
        page = await context.newPage();
      }
      // 认证会话允许登录和退出；业务 API 必须保持零写入，不能用失败写请求替代只读验收。
      page.on("request", sent => {
        const pathname = new URL(sent.url()).pathname;
        if (pathname.startsWith("/api/v1/") && !pathname.startsWith("/api/v1/auth/")
          && !["GET", "HEAD", "OPTIONS"].includes(sent.method())) writes += 1;
      });
      page.on("pageerror", () => { errors += 1; });
      if (identity && identity !== "member") {
        await openLogin(page, configuration, identity);
        await fillLogin(page, configuration.credentials[identity], identity);
        await submitLogin(page);
        await assertIdentity(page, identity);
      }
      await check(page);
      requireCondition(writes === 0 && errors === 0, "只读角色验收出现业务写入或浏览器脚本错误");
      const result = { assertion: name, result: "通过", businessWrites: writes, pageErrors: errors };
      report.scenarios.push(result); persist();
      process.stdout.write(`${JSON.stringify(result)}\n`);
    } finally { await context?.close(); }
  }
  try {
    requireCondition(require("playwright/package.json").version === "1.62.1", "需要现有锁定 Playwright 1.62.1 运行时");
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    await scenario("两个固定模板与完整职责", page => roleView(page, configuration.baseUrl));
    await scenario("管理成员及刷新前后退，管理员保持只读", async page => {
      await roleView(page, configuration.baseUrl);
      await page.getByRole("button", { name: "管理成员", exact: true }).click();
      await page.getByRole("heading", { name: "成员与对话功能", exact: true }).waitFor();
      requireCondition(new URL(page.url()).pathname === "/access/members", "管理成员导航目标错误");
      const directory = await request(page, "/tenant/members?pageNumber=1&pageSize=20");
      const administrator = directory.body?.items?.find(item => item.membershipRole === "TENANT_ADMINISTRATOR");
      requireCondition(directory.status === 200 && administrator, "管理员目录事实缺失");
      const row = page.locator(".tenant-member-directory-table tbody tr")
        .filter({ has: page.getByText(administrator.loginName, { exact: true }) });
      requireCondition(await row.getByText("只读", { exact: true }).count() === 1
        && await row.getByRole("button").count() === 0, "管理员目录出现编辑入口");
      const account = await request(page, `/tenant/members/${administrator.membershipId}`);
      requireCondition(account.status === 200, "管理员账户事实不可读取");
      verifyAdministratorAccount(account.body);
      await page.goBack(); await page.getByRole("heading", { name: "租户固定角色", exact: true }).waitFor();
      await page.reload({ waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "租户固定角色", exact: true }).waitFor();
      await page.goForward(); await page.getByRole("heading", { name: "成员与对话功能", exact: true }).waitFor();
    });
    await scenario("键盘进入成员目录与返回", async page => {
      await roleView(page, configuration.baseUrl);
      const action = page.getByRole("button", { name: "管理成员", exact: true });
      for (let index = 0; index < 30 && !await action.evaluate(element => element === document.activeElement); index += 1) {
        await page.keyboard.press("Tab");
      }
      requireCondition(await action.evaluate(element => element === document.activeElement), "成员导航键盘不可达");
      await action.press("Enter"); await page.getByRole("heading", { name: "成员与对话功能", exact: true }).waitFor();
      await page.goBack(); await page.getByRole("heading", { name: "租户固定角色", exact: true }).waitFor();
    });
    for (const width of [390, 1024, 1440]) for (const enlarged of [false, true]) {
      await scenario(`${width}px ${enlarged ? "200%" : "100%"}文字、键盘与reduced-motion`, async page => {
        await page.setViewportSize({ width, height: 960 });
        await roleView(page, configuration.baseUrl);
        await withTextScale(page, enlarged, () => roleLayout(page, width), "body");
      });
    }
    for (const [identity, expected] of [["member", 403], ["platform", 401]]) {
      await scenario(`${identity === "member" ? "普通成员" : "平台会话"}直接URL与API拒绝`, async page => {
        await page.goto(`${configuration.baseUrl}/access/roles`, { waitUntil: "networkidle" });
        await page.getByRole("heading", { name: "当前会话无权访问此工作区", exact: true }).first().waitFor();
        requireCondition(await page.locator(".tenant-role-page").count() === 0, "越权会话加载了租户固定角色");
        requireCondition((await request(page, "/tenant/members?pageNumber=1&pageSize=20")).status === expected,
          "越权会话获得成员管理事实");
      }, identity);
    }
    await scenario("匿名角色入口要求登录", async page => {
      await page.goto(`${configuration.baseUrl}/access/roles`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "租户管理员登录", exact: true }).waitFor();
      await assertAnonymous(page);
      requireCondition(await page.locator(".tenant-role-page").count() === 0, "匿名会话加载了角色职责");
    }, null);
    await scenario("退出后角色与成员事实不可访问", async page => {
      await roleView(page, configuration.baseUrl); await logoutWorkspace(page);
      await page.getByRole("heading", { name: "租户管理员登录", exact: true }).waitFor();
      await assertAnonymous(page);
      await page.goto(`${configuration.baseUrl}/access/roles`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "租户管理员登录", exact: true }).waitFor();
      requireCondition(await page.locator(".tenant-role-page").count() === 0
        && (await request(page, "/tenant/members?pageNumber=1&pageSize=20")).status === 401,
      "退出后仍能访问角色或成员事实");
    });
    requireCondition(report.scenarios.length === 13, "角色切片场景数量不完整");
    report.status = "VERIFIED"; persist();
    process.stdout.write("WP-0018 / roles：13/13 场景通过，业务写入为 0；不代表租户概览、整包或 M6 完成。\n");
  } catch (error) {
    report.status = "FAILED"; report.failedStage = stage; persist();
    throw new Error(`固定角色验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；日志不输出秘密或浏览器会话材料。`);
  } finally {
    fs.closeSync(descriptor);
    try { await browser?.close(); }
    catch { throw new Error("固定角色验收浏览器关闭失败；未输出会话诊断。 "); }
  }
}

module.exports = { readRoleConfiguration, verifyAdministratorAccount };
if (require.main === module) {
  try { run(readRoleConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
