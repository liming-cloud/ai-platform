/* WP-0017 管理员设置切片：仅使用本机正式 UI/API，保留新建合成对象，不输出秘密。 */
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { request, requireCondition, SafeAssertionError, openLogin, fillLogin,
  submitLogin, assertWorkspace, assertIdentity, assertAnonymous } = require("./acceptance-login.cjs");
const { onboardingRequest, withTextScale } = require("./acceptance-onboarding.cjs");

function readConfiguration(environment = process.env) {
  requireCondition(!environment.SETUP, "管理员切片不接受其他工作包的 SETUP 参数");
  const credentialFile = environment.FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE;
  const outputFile = environment.FOUNDATION_ACCEPTANCE_OUTPUT_FILE;
  requireCondition(typeof credentialFile === "string" && path.isAbsolute(credentialFile)
    && typeof outputFile === "string" && path.isAbsolute(outputFile)
    && !fs.existsSync(outputFile), "必须指定已有 0600 输入凭据及新的输出文件");
  const parent = fs.statSync(path.dirname(outputFile));
  requireCondition(outputFile.startsWith("/private/tmp/") && parent.isDirectory()
    && parent.uid === process.getuid() && (parent.mode & 0o777) === 0o700,
  "输出文件必须位于当前用户独占的 /private/tmp 目录");
  let descriptor;
  let credentials;
  try {
    descriptor = fs.openSync(credentialFile, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
    const stat = fs.fstatSync(descriptor);
    requireCondition(stat.isFile() && stat.uid === process.getuid()
      && (stat.mode & 0o777) === 0o600 && stat.size > 0 && stat.size <= 16_384,
    "凭据文件必须是当前用户独占的普通 0600 文件");
    credentials = JSON.parse(fs.readFileSync(descriptor, "utf8"));
  } catch { throw new SafeAssertionError("输入凭据文件不可用"); }
  finally { if (descriptor !== undefined) fs.closeSync(descriptor); }
  requireCondition(typeof credentials?.platform?.loginName === "string"
    && typeof credentials.platform.password === "string"
    && credentials.platform.loginName.length > 0 && credentials.platform.password.length > 0,
  "平台凭据不完整");
  return { baseUrl: "http://127.0.0.1:13000", platform: credentials.platform, outputFile };
}

function syntheticIdentity(prefix, suffix) {
  return { tenantCode: `${prefix}-${suffix}`, loginName: `${prefix}-${suffix}`,
    password: `Aa9!${crypto.randomBytes(24).toString("base64url")}` };
}

async function run(configuration) {
  requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定的 Playwright 运行时");
  const suffix = crypto.randomBytes(6).toString("hex");
  const created = { status: "STARTED", tenantA: syntheticIdentity("wp17a", suffix),
    tenantB: syntheticIdentity("wp17b", suffix),
    shared: { loginName: `wp17s-${suffix}`, password: `Aa9!${crypto.randomBytes(24).toString("base64url")}` } };
  const descriptor = fs.openSync(configuration.outputFile, "wx", 0o600);
  const persist = () => {
    fs.ftruncateSync(descriptor, 0);
    fs.writeSync(descriptor, JSON.stringify(created), 0, "utf8");
    fs.fsyncSync(descriptor);
  };
  persist();
  let browser;
  let stage = "启动浏览器";
  const check = async (name, action) => {
    stage = name;
    await action();
    process.stdout.write(`${JSON.stringify({ assertion: name, result: "通过" })}\n`);
  };
  try {
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    const platformContext = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
    const page = await platformContext.newPage();
    page.setDefaultTimeout(15_000);
    let pageErrors = 0;
    page.on("pageerror", () => { pageErrors += 1; });
    await check("平台管理员真实登录", async () => {
      await openLogin(page, configuration, "platform");
      await fillLogin(page, configuration.platform, "platform");
      await submitLogin(page);
      await assertWorkspace(page, "platform");
      await assertIdentity(page, "platform");
    });
    await check("仅用正式接口建立隔离的合成验收租户与管理员", async () => {
      for (const [key, label] of [["tenantA", "甲"], ["tenantB", "乙"]]) {
        const identity = created[key];
        const result = await request(page, "/platform/tenants", "POST", {
          ...onboardingRequest(identity), displayName: `WP-0017 管理员验收租户${label}`,
        });
        requireCondition(result.status === 201 && /^[1-9]\d*$/.test(result.body?.tenantId)
          && /^[1-9]\d*$/.test(result.body?.initialAdministratorSubjectId), "合成租户开户未原子成功");
        identity.tenantId = result.body.tenantId;
        identity.subjectId = result.body.initialAdministratorSubjectId;
        persist();
      }
      const account = await request(page, "/platform/accounts", "POST", created.shared);
      requireCondition(account.status === 201 && /^[1-9]\d*$/.test(account.body?.subjectId), "合成账户建立失败");
      created.shared.subjectId = account.body.subjectId;
      persist();
      const result = await request(page, `/platform/tenants/${created.tenantA.tenantId}/administrators`,
        "POST", { accountReference: created.shared.subjectId });
      requireCondition(result.status === 201 && /^[1-9]\d*$/.test(result.body?.membershipReference),
        "管理员分配失败");
      created.tenantA.sharedMembershipReference = result.body.membershipReference;
      created.tenantA.sharedVersion = result.body.version;
      persist();
      created.status = "CREATED";
      persist();
    });
    const adminUrl = `${configuration.baseUrl}/platform/tenants/${created.tenantA.tenantId}/admins`;
    await check("独立管理员页由真实服务端目录恢复", async () => {
      await page.goto(adminUrl, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "租户管理员", exact: true }).waitFor();
      await page.getByRole("cell", { name: created.shared.loginName }).waitFor();
      requireCondition(await page.getByRole("navigation", { name: "租户设置" }).getByRole("link").count() === 2,
        "未交付设置页被伪装为可操作入口");
      requireCondition(await page.locator(".tenant-admin-page__summary dd").first().textContent() === "2",
        "真实管理员总数未显示");
    });
    await check("搜索、状态与页码可刷新和历史恢复", async () => {
      await page.getByRole("searchbox", { name: "按登录名搜索" }).fill(created.shared.loginName);
      await page.waitForURL(url => url.searchParams.get("q") === created.shared.loginName);
      await page.getByRole("combobox", { name: "管理员状态" }).selectOption("ENABLED");
      await page.waitForURL(url => url.searchParams.get("status") === "ENABLED");
      await page.reload({ waitUntil: "networkidle" });
      requireCondition(await page.getByRole("searchbox", { name: "按登录名搜索" }).inputValue()
        === created.shared.loginName, "搜索词刷新后丢失");
      requireCondition(await page.getByRole("combobox", { name: "管理员状态" }).inputValue()
        === "ENABLED", "状态刷新后丢失");
      const target = `/platform/tenants/${created.tenantA.tenantId}/administrators`;
      const first = await request(page, `${target}?pageNumber=1&pageSize=1`);
      const second = await request(page, `${target}?pageNumber=2&pageSize=1`);
      requireCondition(first.status === 200 && second.status === 200
        && first.body?.totalElements === 2 && second.body?.totalElements === 2
        && first.body.items[0].membershipReference !== second.body.items[0].membershipReference,
      "真实分页未稳定返回两条不同管理员资格");
      await page.goBack({ waitUntil: "networkidle" });
      requireCondition(new URL(page.url()).searchParams.get("status") === "ALL", "浏览器后退未恢复筛选");
      requireCondition(await page.getByRole("combobox", { name: "管理员状态" }).inputValue() === "ALL",
        "浏览器后退后筛选控件未恢复");
      await page.goBack({ waitUntil: "networkidle" });
      requireCondition(new URL(page.url()).searchParams.get("q") === null
        && await page.getByRole("searchbox", { name: "按登录名搜索" }).inputValue() === "",
      "浏览器后退后搜索控件未恢复");
    });
    await check("跨租户分配、错租户、旧版本和无 CSRF 写入失败关闭", async () => {
      const target = `/platform/tenants/${created.tenantA.tenantId}/administrators/${created.tenantA.sharedMembershipReference}`;
      const duplicate = await request(page,
        `/platform/tenants/${created.tenantB.tenantId}/administrators`,
        "POST", { accountReference: created.shared.subjectId });
      requireCondition(duplicate.status === 409, "同一身份仍可管理第二个租户");
      const probeContext = await browser.newContext();
      try {
        const probePage = await probeContext.newPage();
        await openLogin(probePage, configuration, "tenant");
        await fillLogin(probePage,
          { tenantCode: created.tenantB.tenantCode, ...created.shared }, "tenant");
        await submitLogin(probePage);
        await assertAnonymous(probePage);
      } finally {
        await probeContext.close();
      }
      const wrong = await request(page,
        `/platform/tenants/${created.tenantB.tenantId}/administrators/${created.tenantA.sharedMembershipReference}/password-reset`,
        "POST", { expectedVersion: created.tenantA.sharedVersion, password: created.shared.password });
      requireCondition(wrong.status === 400, "错租户可重置全局身份");
      const stale = await request(page, `${target}/disable`, "POST", { expectedVersion: 999999 });
      requireCondition(stale.status === 400 || stale.status === 409, "旧版本可以停用管理员");
      const csrf = await request(page, `${target}/password-reset`, "POST",
        { expectedVersion: created.tenantA.sharedVersion, password: created.shared.password }, false);
      requireCondition(csrf.status === 403, "缺少 CSRF 仍可重置密码");
      const oldRoute = await request(page, `/platform/accounts/${created.shared.subjectId}/password-reset`,
        "POST", { password: created.shared.password });
      requireCondition(oldRoute.status === 400 || oldRoute.status === 403,
        "平台账户路径仍可绕过租户管理员资格校验");
    });
    await check("停用仅影响当前租户管理员资格并可恢复", async () => {
      await page.goto(adminUrl, { waitUntil: "networkidle" });
      const row = page.locator("tbody tr").filter({ hasText: created.shared.loginName });
      await row.locator("summary").click();
      await row.getByRole("button", { name: "停用管理员资格" }).click();
      await page.getByRole("button", { name: "取消", exact: true }).click();
      const before = await request(page,
        `/platform/tenants/${created.tenantA.tenantId}/administrators?pageNumber=1&pageSize=20&q=${created.shared.loginName}`);
      requireCondition(before.body?.items[0]?.status === "ENABLED", "取消仍改变管理员资格");
      await row.locator("summary").click();
      await row.getByRole("button", { name: "停用管理员资格" }).click();
      await page.getByRole("button", { name: "确认提交" }).click();
      await page.getByText(/在当前租户的管理员资格已停用/).waitFor();
      const local = await request(page,
        `/platform/tenants/${created.tenantA.tenantId}/administrators?pageNumber=1&pageSize=20&q=${created.shared.loginName}`);
      const other = await request(page,
        `/platform/tenants/${created.tenantB.tenantId}/administrators?pageNumber=1&pageSize=20&q=${created.shared.loginName}`);
      requireCondition(local.body?.items[0]?.status === "DISABLED"
        && other.body?.totalElements === 0, "停用未生效或管理员泄露到其他租户");
      const disabledCrossTenant = await request(page,
        `/platform/tenants/${created.tenantB.tenantId}/administrators`,
        "POST", { accountReference: created.shared.subjectId });
      requireCondition(disabledCrossTenant.status === 409,
        "停用管理员资格后错误地释放了原租户归属");
      const refreshed = page.locator("tbody tr").filter({ hasText: created.shared.loginName });
      await refreshed.locator("summary").click();
      await refreshed.getByRole("button", { name: "恢复管理员资格" }).click();
      await page.getByRole("button", { name: "确认提交" }).click();
      await page.getByText(/在当前租户的管理员资格已恢复/).waitFor();
      const restored = await request(page,
        `/platform/tenants/${created.tenantA.tenantId}/administrators?pageNumber=1&pageSize=20&q=${created.shared.loginName}`);
      requireCondition(restored.body?.items[0]?.status === "ENABLED", "管理员资格未恢复");
      created.tenantA.sharedVersion = restored.body.items[0].version;
      persist();
    });
    let tenantContext;
    let tenantPage;
    await check("管理员只能从所属租户登录", async () => {
      tenantContext = await browser.newContext({ viewport: { width: 1440, height: 960 } });
      tenantPage = await tenantContext.newPage();
      await openLogin(tenantPage, configuration, "tenant");
      await fillLogin(tenantPage,
        { tenantCode: created.tenantA.tenantCode, ...created.shared }, "tenant");
      await submitLogin(tenantPage);
      await assertWorkspace(tenantPage, "tenant");
      await assertIdentity(tenantPage, "tenant");
    });
    await check("双重输入与全范围会话确认后才允许密码重置", async () => {
      await page.goto(adminUrl, { waitUntil: "networkidle" });
      const row = page.locator("tbody tr").filter({ hasText: created.shared.loginName });
      await row.getByRole("button", { name: "重置密码" }).click();
      const dialog = page.getByRole("dialog");
      const nextPassword = `Aa9!${crypto.randomBytes(24).toString("base64url")}`;
      await dialog.getByLabel("新密码").fill(nextPassword);
      await dialog.getByLabel("再次输入").fill(created.shared.password);
      requireCondition(!await dialog.getByRole("button", { name: "重置密码" }).isEnabled(),
        "两次密码不一致仍可提交");
      await dialog.getByLabel("再次输入").fill(nextPassword);
      requireCondition(!await dialog.getByRole("button", { name: "重置密码" }).isEnabled(),
        "未确认全范围会话撤销仍可提交");
      await dialog.getByRole("checkbox", { name: "确认撤销该身份在所有租户的旧会话" }).check();
      await dialog.getByRole("button", { name: "重置密码" }).click();
      await page.getByText(/密码已重置，旧会话已失效/).waitFor();
      created.shared.password = nextPassword;
      persist();
      await assertAnonymous(tenantPage);
      await openLogin(tenantPage, configuration, "tenant");
      await fillLogin(tenantPage,
        { tenantCode: created.tenantA.tenantCode, ...created.shared }, "tenant");
      await submitLogin(tenantPage);
      await assertWorkspace(tenantPage, "tenant");
      await assertIdentity(tenantPage, "tenant");
    });
    await check("390、1024、1440 与 200% 文字无裁切", async () => {
      for (const width of [390, 1024, 1440]) {
        await page.setViewportSize({ width, height: width === 390 ? 844 : 960 });
        await page.goto(adminUrl, { waitUntil: "networkidle" });
        await page.getByRole("heading", { name: "租户管理员", exact: true }).waitFor();
        const visible = () => page.evaluate(() => {
          const main = document.querySelector("main");
          const title = main?.querySelector("h1");
          const bounds = title?.getBoundingClientRect();
          return Boolean(main && title && bounds && bounds.width > 0 && bounds.height > 0
            && document.documentElement.scrollWidth <= innerWidth + 1);
        });
        requireCondition(await visible(), `${width} 页面横向溢出或标题裁切`);
        await withTextScale(page, true, async () => {
          requireCondition(await visible(), `${width} 的 200% 文字横向溢出或标题裁切`);
        });
      }
      requireCondition(pageErrors === 0, "浏览器页面发生未处理异常");
      requireCondition(await page.evaluate(() => !document.getAnimations().some(animation =>
        animation.playState === "running" && animation.effect?.getTiming().duration > 1)),
      "reduced-motion 下仍有运动动画");
    });
    created.status = "VERIFIED";
    persist();
    process.stdout.write("WP-0017 / admins 切片真实部署验收通过；整包与 M6 仍未完成。\n");
  } catch (error) {
    throw new Error(`管理员设置验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；合成对象已在 0600 文件中保留，不输出秘密。`);
  } finally {
    fs.closeSync(descriptor);
    await browser?.close();
  }
}

module.exports = { readConfiguration, syntheticIdentity };
if (require.main === module) {
  try { run(readConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
