/* WP-0017 登录安全切片：只调用本机正式 UI/API，合成对象留在独占凭据文件。 */
const fs = require("node:fs");
const crypto = require("node:crypto");
const { request, requireCondition, SafeAssertionError, openLogin, fillLogin,
  submitLogin, assertWorkspace, assertIdentity, assertAnonymous } = require("./acceptance-login.cjs");
const { onboardingRequest, withTextScale } = require("./acceptance-onboarding.cjs");
const { readConfiguration, syntheticIdentity } = require("./acceptance-admins.cjs");

const defaults = { sessionHours: 8, failedAttempts: 5, lockMinutes: 15, version: 0 };
const changed = { sessionHours: 4, failedAttempts: 3, lockMinutes: 5, version: 1 };

async function tenantLogin(browser, configuration, identity) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
  const page = await context.newPage();
  page.setDefaultTimeout(15_000);
  await openLogin(page, configuration, "tenant");
  await fillLogin(page, identity, "tenant");
  await submitLogin(page);
  await assertWorkspace(page, "tenant");
  await assertIdentity(page, "tenant");
  return { context, page };
}

async function run(configuration) {
  requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定的 Playwright 运行时");
  const suffix = crypto.randomBytes(6).toString("hex");
  const created = { status: "STARTED", platform: configuration.platform,
    tenant: syntheticIdentity("wp17sl", suffix), securityTenant: syntheticIdentity("wp17sp", suffix) };
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
    const platform = await platformContext.newPage();
    platform.setDefaultTimeout(15_000);
    let pageErrors = 0;
    platform.on("pageerror", () => { pageErrors += 1; });
    await check("平台管理员真实登录", async () => {
      await openLogin(platform, configuration, "platform");
      await fillLogin(platform, configuration.platform, "platform");
      await submitLogin(platform);
      await assertWorkspace(platform, "platform");
      await assertIdentity(platform, "platform");
    });
    await check("正式接口建立目标与对照两个合成租户", async () => {
      for (const [key, label] of [["tenant", "对照"], ["securityTenant", "策略"]]) {
        const identity = created[key];
        const result = await request(platform, "/platform/tenants", "POST", {
          ...onboardingRequest(identity), displayName: `WP-0017 登录安全${label}租户`,
        });
        requireCondition(result.status === 201 && /^[1-9]\d*$/.test(result.body?.tenantId)
          && /^[1-9]\d*$/.test(result.body?.initialAdministratorSubjectId), "合成租户开户未成功");
        identity.tenantId = result.body.tenantId;
        identity.subjectId = result.body.initialAdministratorSubjectId;
        persist();
      }
    });
    const api = `/platform/tenants/${created.securityTenant.tenantId}/security`;
    const otherApi = `/platform/tenants/${created.tenant.tenantId}/security`;
    await check("独立登录安全页展示原型三项与服务端默认值", async () => {
      await platform.goto(`${configuration.baseUrl}${api}`, { waitUntil: "networkidle" });
      await platform.locator("h1", { hasText: "登录安全" }).waitFor();
      const value = await request(platform, api);
      requireCondition(value.status === 200 && Object.keys(defaults).every(key => value.body?.[key] === defaults[key]),
        "默认登录策略与合同不一致");
      const navigation = platform.getByRole("navigation", { name: "租户设置" });
      requireCondition(await navigation.getByRole("link").count() === 5
        && await navigation.getByRole("link", { name: "登录安全" }).getAttribute("aria-current") === "page",
      "独立设置导航与原型不一致");
      const summary = platform.locator(".tenant-security-page .tenant-quota-page__summary");
      requireCondition(await summary.locator("dt").allTextContents().then(values =>
        JSON.stringify(values) === JSON.stringify(["会话有效期", "登录失败次数", "锁定时长"])),
      "三项策略顺序不正确");
    });
    let writes = 0;
    platform.on("request", outgoing => {
      if (outgoing.method() === "PUT" && new URL(outgoing.url()).pathname.endsWith(api)) writes += 1;
    });
    await check("取消编辑不写入且焦点回到触发按钮", async () => {
      await platform.getByRole("button", { name: "编辑登录策略" }).click();
      const dialog = platform.getByRole("dialog");
      requireCondition(await dialog.getByText("保存后，此租户现有会话将立即失效。", { exact: true }).isVisible(),
        "保存前未提示目标租户旧会话立即失效");
      await dialog.getByRole("spinbutton", { name: "会话有效期（小时）" }).fill("4");
      await dialog.getByRole("button", { name: "取消" }).click();
      await platform.waitForFunction(() => document.activeElement?.textContent?.trim() === "编辑登录策略");
      requireCondition(writes === 0 && (await request(platform, api)).body?.version === 0,
        "取消编辑仍写入策略");
    });
    await check("超范围输入在客户端与服务端均被拒绝", async () => {
      await platform.getByRole("button", { name: "编辑登录策略" }).click();
      const dialog = platform.getByRole("dialog");
      await dialog.getByRole("spinbutton", { name: "会话有效期（小时）" }).fill("25");
      await dialog.getByRole("button", { name: "保存策略" }).click();
      requireCondition(writes === 0 && await dialog.isVisible(), "超范围输入被发送或弹窗错误关闭");
      await dialog.getByRole("button", { name: "取消" }).click();
      const rejected = await request(platform, api, "PUT", {
        sessionHours: 25, failedAttempts: 3, lockMinutes: 5, expectedVersion: 0,
      });
      requireCondition(rejected.status === 400 && writes === 1, "服务端接受超范围策略");
      // 上一请求是主动验证服务端拒绝，下一场景单独统计界面保存。
      writes = 0;
    });
    const oldTarget = await tenantLogin(browser, configuration, created.securityTenant);
    const comparator = await tenantLogin(browser, configuration, created.tenant);
    await check("平台保存目标策略并立即撤销旧目标会话", async () => {
      await platform.getByRole("button", { name: "编辑登录策略" }).click();
      const dialog = platform.getByRole("dialog");
      await dialog.getByRole("spinbutton", { name: "会话有效期（小时）" }).fill("4");
      await dialog.getByRole("spinbutton", { name: "登录失败次数（次）" }).fill("3");
      await dialog.getByRole("spinbutton", { name: "锁定时长（分钟）" }).fill("5");
      await dialog.getByRole("button", { name: "保存策略" }).click();
      await platform.getByText("登录策略已保存。").waitFor();
      const target = await request(platform, api);
      const other = await request(platform, otherApi);
      requireCondition(writes === 1 && target.status === 200 && other.status === 200
        && Object.keys(changed).every(key => target.body?.[key] === changed[key])
        && Object.keys(defaults).every(key => other.body?.[key] === defaults[key]),
      "保存跨越租户或版本边界");
      await assertAnonymous(oldTarget.page);
      await assertIdentity(comparator.page, "tenant");
      await assertIdentity(platform, "platform");
      await oldTarget.context.close();
      await comparator.context.close();
      await platform.reload({ waitUntil: "networkidle" });
      requireCondition(await platform.getByText("4 小时").isVisible(), "刷新后未读取保存的策略");
    });
    await check("新租户浏览器会话承接四小时时长", async () => {
      const target = await tenantLogin(browser, configuration, created.securityTenant);
      try {
        const capability = await request(target.page, "/auth/browser-session");
        requireCondition(capability.status === 200 && capability.body?.maximumSessionAgeSeconds === 14_400,
          "浏览器会话未承接目标租户时长");
      } finally { await target.context.close(); }
    });
    await check("旧版本、未知字段、缺少 CSRF 和租户身份不能修改", async () => {
      requireCondition((await request(platform, api, "PUT", {
        sessionHours: 5, failedAttempts: 4, lockMinutes: 10, expectedVersion: 0,
      })).status === 409, "旧版本覆盖未拒绝");
      requireCondition((await request(platform, api, "PUT", {
        sessionHours: 5, failedAttempts: 4, lockMinutes: 10, expectedVersion: 1, unknown: true,
      })).status === 400, "未知字段未拒绝");
      requireCondition((await request(platform, api, "PUT", {
        sessionHours: 5, failedAttempts: 4, lockMinutes: 10, expectedVersion: 1,
      }, false)).status === 403, "缺少 CSRF 未拒绝");
      const tenant = await tenantLogin(browser, configuration, created.tenant);
      try {
        requireCondition((await request(tenant.page, api, "PUT", {
          sessionHours: 5, failedAttempts: 4, lockMinutes: 10, expectedVersion: 1,
        })).status === 401, "租户身份越权修改平台登录策略");
      } finally { await tenant.context.close(); }
    });
    await check("目标租户三次失败锁定且正确口令不能解除", async () => {
      const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
      try {
        const page = await context.newPage();
        await openLogin(page, configuration, "tenant");
        for (let attempt = 0; attempt < 3; attempt += 1) {
          await fillLogin(page, { ...created.securityTenant, password: "invalid-for-acceptance" }, "tenant");
          const sent = page.waitForResponse(response => response.url().endsWith("/api/v1/auth/browser-sessions")
            && response.request().method() === "POST");
          await submitLogin(page);
          requireCondition((await sent).status() === 401, "错误口令没有被统一拒绝");
        }
        await fillLogin(page, created.securityTenant, "tenant");
        const blocked = page.waitForResponse(response => response.url().endsWith("/api/v1/auth/browser-sessions")
          && response.request().method() === "POST");
        await submitLogin(page);
        requireCondition((await blocked).status() === 401, "锁定期间正确口令仍能登录");
        await assertAnonymous(page);
      } finally { await context.close(); }
      const other = await tenantLogin(browser, configuration, created.tenant);
      try { await assertIdentity(other.page, "tenant"); }
      finally { await other.context.close(); }
    });
    await check("三尺寸、200% 文字、键盘与 reduced-motion 无裁切", async () => {
      for (const width of [390, 1024, 1440]) {
        await platform.setViewportSize({ width, height: 960 });
        await platform.reload({ waitUntil: "networkidle" });
        await platform.getByRole("button", { name: "编辑登录策略" }).click();
        const dialog = platform.getByRole("dialog");
        const layout = await platform.evaluate(() => {
          const summary = document.querySelector(".tenant-security-page .tenant-quota-page__summary");
          const grid = document.querySelector(".tenant-security-page__dialog .tenant-quota-page__grid");
          const modal = document.querySelector(".tenant-security-page__dialog");
          return { overflow: document.documentElement.scrollWidth > innerWidth + 1,
            summaryColumns: getComputedStyle(summary).gridTemplateColumns.split(" ").length,
            inputColumns: getComputedStyle(grid).gridTemplateColumns.split(" ").length,
            modalWidth: modal.getBoundingClientRect().width,
            headings: [...document.querySelectorAll("h1")].map(item => item.textContent.trim()) };
        });
        requireCondition(!layout.overflow && layout.summaryColumns === (width <= 680 ? 2 : 3)
          && layout.inputColumns === 2
          && layout.modalWidth <= width - 8 && layout.headings.length === 1
          && layout.headings[0] === "登录安全", `${width} 登录安全布局与原型不一致`);
        await withTextScale(platform, true, async () => {
          const fits = await platform.evaluate(() => {
            const modal = document.querySelector(".tenant-security-page__dialog");
            const save = [...modal.querySelectorAll("button")].find(item => item.textContent?.trim() === "保存策略");
            save?.scrollIntoView({ block: "nearest" });
            const bounds = save?.getBoundingClientRect();
            return document.documentElement.scrollWidth <= innerWidth + 1
              && modal.scrollWidth <= modal.clientWidth + 1
              && Boolean(bounds && bounds.left >= -1 && bounds.right <= innerWidth + 1);
          });
          requireCondition(fits, `${width} 放大文字后弹窗裁切或保存不可达`);
        });
        await dialog.getByRole("spinbutton", { name: "会话有效期（小时）" }).focus();
        await platform.keyboard.press("Tab");
        requireCondition(await dialog.getByRole("spinbutton", { name: "登录失败次数（次）" })
          .evaluate(element => element === document.activeElement), "键盘无法依序进入登录策略字段");
        await dialog.getByRole("button", { name: "取消" }).click();
      }
      requireCondition(pageErrors === 0, "登录安全页出现未处理异常");
      requireCondition(await platform.evaluate(() => !document.getAnimations().some(animation =>
        animation.playState === "running" && animation.effect?.getTiming().duration > 1)),
      "reduced-motion 下仍有运动动画");
    });
    created.status = "VERIFIED";
    persist();
    process.stdout.write("WP-0017 / security 切片真实部署验收通过；整包与 M6 仍未完成。\n");
  } catch (error) {
    throw new Error(`登录安全验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；合成对象保留于独占文件，不输出秘密。`);
  } finally {
    fs.closeSync(descriptor);
    await browser?.close();
  }
}

if (require.main === module) {
  try { run(readConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
