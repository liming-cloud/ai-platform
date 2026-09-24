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
      const pageSize = page.getByRole("combobox", { name: "每页条数" });
      await pageSize.selectOption("10");
      await page.waitForURL(url => url.searchParams.get("pageSize") === "10");
      await page.waitForFunction(() => document.activeElement?.getAttribute("aria-label") === "每页条数");
      await pageSize.selectOption("20");
      await page.waitForURL(url => url.searchParams.get("pageSize") === "20");
      const jump = page.getByRole("spinbutton", { name: "跳转页码" });
      await jump.fill("0");
      await page.getByRole("button", { name: "跳转" }).click();
      requireCondition(await page.getByRole("alert").getByText(/请输入 1–/).isVisible(),
        "越界页码未提供原型错误提示");
      await jump.fill("1");
      const target = `/platform/tenants/${created.tenantA.tenantId}/administrators`;
      const first = await request(page, `${target}?pageNumber=1&pageSize=1`);
      const second = await request(page, `${target}?pageNumber=2&pageSize=1`);
      requireCondition(first.status === 200 && second.status === 200
        && first.body?.totalElements === 2 && second.body?.totalElements === 2
        && first.body.items[0].membershipReference !== second.body.items[0].membershipReference,
      "真实分页未稳定返回两条不同管理员资格");
      await page.goBack({ waitUntil: "networkidle" });
      requireCondition(new URL(page.url()).searchParams.get("pageSize") === "10",
        "浏览器后退未恢复目录每页条数");
      await page.goBack({ waitUntil: "networkidle" });
      requireCondition(new URL(page.url()).searchParams.get("pageSize") === "20",
        "浏览器后退未恢复默认每页条数");
      await page.goBack({ waitUntil: "networkidle" });
      requireCondition(new URL(page.url()).searchParams.get("status") === "ALL", "浏览器后退未恢复筛选");
      requireCondition(await page.getByRole("combobox", { name: "管理员状态" }).inputValue() === "ALL",
        "浏览器后退后筛选控件未恢复");
      await page.goBack({ waitUntil: "networkidle" });
      requireCondition(new URL(page.url()).searchParams.get("q") === null
        && await page.getByRole("searchbox", { name: "按登录名搜索" }).inputValue() === "",
      "浏览器后退后搜索控件未恢复");
      await page.getByRole("button", { name: "第 1 页" }).click();
      await page.goForward({ waitUntil: "networkidle" });
      requireCondition(new URL(page.url()).searchParams.get("q") === created.shared.loginName,
        "点击当前页重复写入历史，前进无法恢复搜索");
      await page.goBack({ waitUntil: "networkidle" });
      await page.getByRole("searchbox", { name: "按登录名搜索" }).fill(created.shared.loginName);
      await page.waitForURL(url => url.searchParams.get("q") === created.shared.loginName);
      await page.waitForFunction(() => document.activeElement?.getAttribute("aria-label") === "按登录名搜索"
        && Boolean(document.querySelector(".tenant-admin-page__pagination")));
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
      let cancelledDisableRequests = 0;
      const observeCancellation = outgoing => {
        if (outgoing.method() === "POST"
          && new URL(outgoing.url()).pathname.endsWith(
            `/administrators/${created.tenantA.sharedMembershipReference}/disable`)) {
          cancelledDisableRequests += 1;
        }
      };
      page.on("request", observeCancellation);
      await row.locator("summary").click();
      await row.getByRole("button", { name: "停用管理员资格" }).click();
      const governance = page.getByRole("dialog");
      for (const label of ["影响", "恢复", "审计"]) {
        requireCondition(await governance.getByText(label, { exact: true }).isVisible(),
          `资格确认框缺少${label}范围`);
      }
      requireCondition(await governance.getByText(/全局账户状态不变/).isVisible(),
        "资格确认框未说明全局账户不受影响");
      await page.getByRole("button", { name: "取消", exact: true }).click();
      await page.waitForFunction(() => document.activeElement?.tagName === "SUMMARY"
        && document.activeElement.textContent?.trim() === "更多");
      const before = await request(page,
        `/platform/tenants/${created.tenantA.tenantId}/administrators?pageNumber=1&pageSize=20&q=${created.shared.loginName}`);
      page.off("request", observeCancellation);
      requireCondition(cancelledDisableRequests === 0, "取消时仍发送了停用管理员资格请求");
      requireCondition(before.status === 200, `取消后目录查询失败：HTTP ${before.status}`);
      requireCondition(before.body?.totalElements === 1
        && before.body?.items?.[0]?.membershipReference === created.tenantA.sharedMembershipReference,
      "取消后目录未返回精确管理员资格");
      requireCondition(before.body.items[0].status === "ENABLED", "取消仍改变管理员资格");
      await row.locator("summary").click();
      await row.getByRole("button", { name: "停用管理员资格" }).click();
      await page.getByRole("button", { name: "确认停用" }).click();
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
      await page.getByRole("button", { name: "确认恢复" }).click();
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
      requireCondition(await dialog.getByText("受控凭据重置", { exact: true }).isVisible()
        && await dialog.getByText("平台与租户范围的既有会话同时失效。", { exact: true }).isVisible(),
      "重置确认框未承接原型信息层级与全范围会话说明");
      const safetyHelp = dialog.locator("details.tenant-admin-page__password-help");
      const safetySummary = safetyHelp.locator("summary");
      requireCondition((await safetySummary.textContent())?.trim() === "密码安全说明"
        && await safetySummary.isVisible()
        && await safetyHelp.getAttribute("open") === null
        && await dialog.getByText("演示密码说明", { exact: true }).count() === 0,
      "重置说明区未按原型结构默认折叠或仍含演示凭据说明");
      await safetySummary.focus();
      await safetySummary.press("Enter");
      requireCondition(await safetyHelp.getAttribute("open") !== null
        && await safetyHelp.getByText(/当前密码无法查看/).isVisible(),
      "安全说明无法通过键盘展开");
      await safetySummary.press("Space");
      requireCondition(await safetyHelp.getAttribute("open") === null,
        "安全说明无法通过键盘折叠");
      let invalidResetRequests = 0;
      const observeInvalidReset = outgoing => {
        if (outgoing.method() === "POST" && new URL(outgoing.url()).pathname.endsWith("/password-reset")) {
          invalidResetRequests += 1;
        }
      };
      page.on("request", observeInvalidReset);
      const confirmReset = dialog.getByRole("button", { name: "重置密码" });
      requireCondition(await confirmReset.isEnabled(), "空表单应允许触发与原型一致的校验反馈");
      await confirmReset.click();
      requireCondition(await dialog.getByRole("alert").getByText("请完整填写新密码并确认撤销全部会话。").isVisible(),
        "空表单未给出校验反馈");
      const nextPassword = `Aa9!${crypto.randomBytes(24).toString("base64url")}`;
      await dialog.getByLabel("新密码").fill(nextPassword);
      await dialog.getByLabel("再次输入").fill(created.shared.password);
      await confirmReset.click();
      requireCondition(await dialog.getByRole("alert").getByText("两次输入不一致。").isVisible(),
        "两次密码不一致未被拦截");
      await dialog.getByLabel("再次输入").fill(nextPassword);
      await confirmReset.click();
      requireCondition(await dialog.getByRole("alert").getByText("请确认撤销全部会话。").isVisible(),
        "未确认全范围会话撤销仍可提交");
      await page.waitForLoadState("networkidle");
      page.off("request", observeInvalidReset);
      requireCondition(invalidResetRequests === 0, "无效重置点击仍发送了密码重置请求");
      const revokeAll = dialog.getByRole("checkbox", { name: "确认撤销该身份在所有租户的旧会话" });
      await revokeAll.check();
      let refusedRequests = 0;
      await page.route("**/password-reset", async route => {
        refusedRequests += 1;
        await route.fulfill({ status: 409, contentType: "application/json",
          body: JSON.stringify({ code: "CONFLICT", message: "资料已更新" }) });
      });
      await confirmReset.click();
      await dialog.getByRole("alert").getByText(/重置管理员密码未完成/).waitFor();
      requireCondition(refusedRequests === 1 && await dialog.getByLabel("新密码").inputValue() === ""
        && !await revokeAll.isChecked(), "服务端拒绝后弹窗未反馈或秘密字段未清除");
      await page.unroute("**/password-reset");
      await dialog.getByLabel("新密码").fill(nextPassword);
      await dialog.getByLabel("再次输入").fill(nextPassword);
      await revokeAll.check();
      await confirmReset.click();
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
        const frame = await page.evaluate(() => {
          const summary = document.querySelector(".tenant-admin-page__summary");
          const status = document.querySelector('.tenant-admin-page__toolbar select[aria-label="管理员状态"]');
          return { columns: summary ? getComputedStyle(summary).gridTemplateColumns.split(" ").length : 0,
            summaryHeight: summary?.getBoundingClientRect().height ?? 0,
            statusHeight: status?.getBoundingClientRect().height ?? 0,
            statusWidth: status?.getBoundingClientRect().width ?? 0 };
        });
        requireCondition(frame.columns === (width <= 1040 ? 2 : 4)
          && (width !== 390 || (Math.floor(frame.summaryHeight) === 93 && Math.abs(frame.statusHeight - 44) <= 1))
          && (width === 390 || frame.statusWidth === 110),
        `${width} 摘要列数或筛选控件尺寸未承接原型`);
        await withTextScale(page, true, async () => {
          const directoryTextScaled = await page.evaluate(() => {
            const rows = [...document.querySelectorAll(".tenant-admin-page__table-shell tbody tr")];
            const headings = [...document.querySelectorAll(".tenant-admin-page__table-shell th")]
              .filter(element => element.getClientRects().length > 0);
            return rows.length > 0 && rows.every(row => {
              const summary = row.querySelector("summary");
              return summary?.hasAttribute("data-wp0017-text-scale")
                && [...row.querySelectorAll("td")].every(cell => cell.hasAttribute("data-wp0017-text-scale"));
            }) && headings.every(heading => heading.hasAttribute("data-wp0017-text-scale"));
          });
          requireCondition(directoryTextScaled, `${width} 的目录或菜单文字未参与 200% 验收`);
          requireCondition(await visible(), `${width} 的 200% 文字横向溢出或标题裁切`);
        });
        const menus = page.locator("tbody tr details");
        const menuCount = await menus.count();
        requireCondition(menuCount > 0, `${width} 缺少待检查的管理员操作菜单`);
        for (let index = 0; index < menuCount; index += 1) {
          const menu = menus.nth(index);
          await menu.locator("summary").click();
          try {
            const menuFlow = await menu.evaluate(details => {
              const next = details.closest("tr")?.nextElementSibling;
              return !next || next.getBoundingClientRect().top >= details.getBoundingClientRect().bottom - 1;
            });
            requireCondition(menuFlow, `${width} 操作菜单覆盖下一行`);
            await withTextScale(page, true, async () => {
              const expanded = await menu.evaluate(details => {
                const buttons = [...details.querySelectorAll("button")]
                  .filter(button => button.getClientRects().length > 0);
                return details.open && buttons.length > 0 && buttons.every(button => {
                  const bounds = button.getBoundingClientRect();
                  return button.hasAttribute("data-wp0017-text-scale")
                    && bounds.width > 0 && bounds.left >= -1 && bounds.right <= innerWidth + 1;
                });
              });
              requireCondition(expanded && await visible(), `${width} 的 200% 操作菜单文字缺失或横向裁切`);
            });
          } finally {
            if (await menu.evaluate(details => details.open)) await menu.locator("summary").click();
          }
        }
        const firstRow = page.locator("tbody tr").first();
        for (const kind of width === 390 ? ["reset", "status"] : ["reset"]) {
          if (kind === "reset") await firstRow.getByRole("button", { name: "重置密码" }).click();
          else {
            await firstRow.locator("summary").click();
            await firstRow.getByRole("button", { name: "停用管理员资格" }).click();
          }
          const dialog = page.getByRole("dialog");
          // 先等 Fluent 入场缩放结束，再按固定原型比较真实盒宽。
          await dialog.evaluate(async surface => {
            await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
            await Promise.all(surface.getAnimations({ subtree: true }).map(animation => animation.finished.catch(() => {})));
          });
          const dialogWidth = await dialog.evaluate(surface => surface.getBoundingClientRect().width);
          requireCondition(Math.abs(dialogWidth - (width === 390 ? 352 : 608)) <= 1,
            `${width} 的 ${kind} 弹窗宽度未承接固定原型`);
          await withTextScale(page, true, async () => {
            const confirm = dialog.getByRole("button", { name: kind === "reset" ? "重置密码" : "确认停用" });
            await confirm.scrollIntoViewIfNeeded();
            const reachable = await dialog.evaluate(surface => {
              const surfaceBounds = surface.getBoundingClientRect();
              const content = surface.querySelector(".fui-DialogContent");
              const contentBounds = content?.getBoundingClientRect();
              const passwordInputs = [...surface.querySelectorAll(".tenant-admin-page__password-grid .fui-Input")];
              const inputsFit = passwordInputs.every(input => {
                const bounds = input.getBoundingClientRect();
                return contentBounds && bounds.left >= contentBounds.left - 1
                  && bounds.right <= contentBounds.right + 1
                  && input.scrollWidth <= input.clientWidth + 1;
              });
              const button = [...surface.querySelectorAll("button")].find(item =>
                item.textContent?.trim() === (surface.textContent?.includes("受控凭据重置") ? "重置密码" : "确认停用"));
              const bounds = button?.getBoundingClientRect();
              return surface.scrollWidth <= surface.clientWidth + 1
                && content && content.scrollWidth <= content.clientWidth + 1 && inputsFit
                && surfaceBounds.left >= -1 && surfaceBounds.right <= innerWidth + 1
                && bounds && bounds.top >= -1 && bounds.bottom <= innerHeight + 1;
            });
            requireCondition(reachable, `${width} 的 ${kind} 弹窗在 200% 文字下裁切或确认按钮不可达`);
          }, ".tenant-admin-page__dialog");
          await dialog.getByRole("button", { name: "取消" }).click();
        }
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
