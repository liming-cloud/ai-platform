/* WP-0017 平台账户切片：正式 UI/API 验收；合成凭据仅留在独占临时文件。 */
const fs = require("node:fs");
const crypto = require("node:crypto");
const {
  request, requireCondition, SafeAssertionError, openLogin, fillLogin,
  submitLogin, assertWorkspace, assertIdentity, assertAnonymous,
} = require("./acceptance-login.cjs");
const { readConfiguration } = require("./acceptance-admins.cjs");
const { withTextScale } = require("./acceptance-onboarding.cjs");

function newPassword() { return `Aa9!${crypto.randomBytes(24).toString("base64url")}`; }

async function login(browser, configuration, identity, accepted = true) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
  const page = await context.newPage();
  page.setDefaultTimeout(15_000);
  await openLogin(page, configuration, "platform");
  await fillLogin(page, identity, "platform");
  const response = page.waitForResponse(value => value.url().endsWith("/api/v1/auth/browser-sessions")
    && value.request().method() === "POST");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  const status = (await response).status();
  requireCondition(status === (accepted ? 204 : 401), "平台账户登录状态不符合当前凭据与账户状态");
  if (accepted) { await assertWorkspace(page, "platform"); await assertIdentity(page, "platform"); }
  else await assertAnonymous(page);
  return { context, page };
}

async function run(configuration) {
  requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定的 Playwright 运行时");
  const suffix = crypto.randomBytes(6).toString("hex");
  const created = {
    status: "STARTED",
    platform: configuration.platform,
    account: { loginName: `wp17pa-${suffix}`, password: newPassword(), replacementPassword: newPassword() },
    extras: [],
  };
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
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
    const platform = await context.newPage();
    platform.setDefaultTimeout(15_000);
    let pageErrors = 0;
    platform.on("pageerror", () => { pageErrors += 1; });
    await check("原平台管理员真实登录", async () => {
      await openLogin(platform, configuration, "platform");
      await fillLogin(platform, configuration.platform, "platform");
      await submitLogin(platform);
      await assertWorkspace(platform, "platform");
      await assertIdentity(platform, "platform");
    });
    await check("平台账户页只展示管理员任务和原型目录结构", async () => {
      await platform.goto(`${configuration.baseUrl}/platform/accounts`, { waitUntil: "networkidle" });
      await platform.getByRole("heading", { name: "平台账户", exact: true }).waitFor();
      requireCondition(await platform.locator(".platform-account-page__dialog").count() === 0
        && await platform.locator(".tenant-admin-page__summary dt").allTextContents().then(values =>
          JSON.stringify(values) === JSON.stringify(["平台账户", "可用账户"]))
        && await platform.locator(".platform-account-page th").allTextContents().then(values =>
          JSON.stringify(values) === JSON.stringify(["登录名", "账号用途", "状态", "最近安全变更", "操作"]))
        && await platform.getByRole("navigation", { name: "平台访问治理任务切换" }).count() === 0
        && await platform.locator(".page-summary").innerText().then(value =>
          value.replace(/\s+/g, "") === "平台管理员账户可治理")
        && await platform.getByRole("button", { name: "授予平台治理权限" }).count() === 0,
      "目录结构或账户职责未承接原型");
      await platform.getByRole("searchbox", { name: "按登录名搜索" }).fill(configuration.platform.loginName);
      await platform.waitForURL(url => url.searchParams.get("q") === configuration.platform.loginName);
      const current = platform.locator(".platform-account-page tbody tr").filter({
        has: platform.getByRole("cell", { name: configuration.platform.loginName, exact: true }),
      });
      await current.waitFor();
      requireCondition(await current.count() === 1
        && await current.getByRole("button", { name: "当前账户不能由此重置密码" }).isDisabled(),
      "当前操作者自我操作未关闭");
    });
    let accountWrites = 0;
    platform.on("request", outgoing => {
      if (outgoing.method() === "POST" && new URL(outgoing.url()).pathname === "/api/v1/platform/accounts") accountWrites += 1;
    });
    await check("取消创建不写入且不保留密码", async () => {
      await platform.getByRole("button", { name: "创建平台账户" }).first().click();
      const dialog = platform.getByRole("dialog");
      requireCondition(await platform.locator(".platform-account-page__dialog").count() === 1,
        "创建弹窗未承接原型样式");
      await platform.waitForFunction(() => document.activeElement?.getAttribute("placeholder") === "例如：zhou.demo");
      requireCondition(await dialog.locator(".platform-account-page__note").count() === 1,
        "创建弹窗缺少原型安全提示卡");
      const dialogLayout = await dialog.evaluate(surface => {
        const actions = surface.querySelector(".fui-DialogActions").getBoundingClientRect();
        const body = surface.querySelector(".fui-DialogBody").getBoundingClientRect();
        const buttons = [...surface.querySelectorAll(".fui-DialogActions button")];
        return Math.abs(body.width - actions.width) <= 1
          && buttons.length === 2 && buttons[0].getBoundingClientRect().width < 72
          && buttons[1].getBoundingClientRect().width < 96;
      });
      requireCondition(dialogLayout, "创建弹窗的分隔线或短按钮与原型不一致");
      await dialog.getByRole("textbox", { name: "登录名" }).fill(created.account.loginName);
      await dialog.getByLabel("初始密码").fill(created.account.password);
      await dialog.getByRole("button", { name: "取消" }).click();
      await dialog.waitFor({ state: "hidden" });
      requireCondition(accountWrites === 0, "取消创建仍写入");
    });
    await check("密码确认不一致不得创建账户", async () => {
      await platform.getByRole("button", { name: "创建平台账户" }).first().click();
      const dialog = platform.getByRole("dialog");
      requireCondition(await dialog.getByLabel("初始密码").inputValue() === "", "取消后仍保留初始密码");
      await dialog.getByRole("textbox", { name: "登录名" }).fill(created.account.loginName);
      await dialog.getByLabel("初始密码").fill(created.account.password);
      await dialog.getByLabel("再次输入").fill("mismatch");
      await dialog.getByRole("button", { name: "创建账户" }).click();
      requireCondition(accountWrites === 0 && await dialog.getByRole("alert").innerText() === "两次输入不一致。",
        "错误确认仍提交或缺少提示");
    });
    await check("页面通过正式接口原子创建平台管理员", async () => {
      const dialog = platform.getByRole("dialog");
      await dialog.getByLabel("再次输入").fill(created.account.password);
      const sent = platform.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/platform/accounts"
        && response.request().method() === "POST");
      await dialog.getByRole("button", { name: "创建账户" }).click();
      const response = await sent;
      const body = await response.json();
      requireCondition(response.status() === 201 && /^[1-9]\d*$/.test(body.subjectId)
        && body.status === "ACTIVE" && body.activeRoleCodes?.includes("PLATFORM_ADMINISTRATOR")
        && !JSON.stringify(body).includes(created.account.password), "创建响应缺少管理员事实或泄露凭据");
      created.account.subjectId = body.subjectId;
      persist();
      await platform.locator(".platform-account-page tbody tr").filter({ hasText: created.account.loginName }).waitFor();
      await platform.getByRole("dialog").waitFor({ state: "hidden" });
      requireCondition(accountWrites === 1, "创建写入次数不正确");
    });
    await check("全局重复登录名失败关闭", async () => {
      const duplicate = await request(platform, "/platform/accounts", "POST",
        { loginName: created.account.loginName, password: newPassword() });
      requireCondition(duplicate.status === 409, "重复登录名未由数据库唯一约束拒绝");
    });
    let firstSession;
    await check("新平台管理员首次登录且只有平台范围", async () => {
      firstSession = await login(browser, configuration, created.account);
    });
    await check("重置密码必须确认撤销全部会话且取消零写入", async () => {
      stage = "重置确认：筛选目标账户";
      await platform.getByRole("searchbox", { name: "按登录名搜索" }).fill(created.account.loginName);
      await platform.waitForURL(url => url.searchParams.get("q") === created.account.loginName);
      const row = platform.locator(".platform-account-page tbody tr").filter({ hasText: created.account.loginName });
      stage = "重置确认：打开弹窗";
      await row.waitFor();
      await row.getByRole("button", { name: "重置密码" }).click();
      const dialog = platform.getByRole("dialog");
      requireCondition(await dialog.getByText(`为 ${created.account.loginName} 设置新密码。系统不会回显或保存明文。`).isVisible(),
        "重置说明与原型不一致");
      await dialog.evaluate(async surface => {
        await Promise.all(surface.getAnimations().map(animation => animation.finished.catch(() => undefined)));
      });
      const resetLayout = await dialog.evaluate(surface => {
        const revoke = surface.querySelector(".tenant-admin-page__revoke").getBoundingClientRect();
        const indicator = surface.querySelector(".fui-Checkbox__indicator").getBoundingClientRect();
        const buttons = [...surface.querySelectorAll(".fui-DialogActions button")];
        return Math.abs(indicator.left - revoke.left - 1) <= 1
          && Math.abs(indicator.top - revoke.top - 15) <= 1
          && Math.abs(indicator.width - 18) <= 1
          && buttons.length === 2
          && buttons.every(button => getComputedStyle(button).fontSize === "14px"
            && getComputedStyle(button).whiteSpace === "nowrap")
          && Math.abs(buttons[0].getBoundingClientRect().width - 54) <= 1
          && Math.abs(buttons[1].getBoundingClientRect().width - 82) <= 1;
      });
      requireCondition(resetLayout, "重置弹窗勾选区或操作按钮未对齐原型");
      stage = "重置确认：填写新密码";
      await dialog.getByLabel("新密码").fill(created.account.replacementPassword);
      await dialog.getByLabel("再次输入").fill(created.account.replacementPassword);
      stage = "重置确认：验证撤销提示";
      await dialog.getByRole("button", { name: "重置密码" }).click();
      requireCondition(await dialog.getByRole("alert").innerText() === "请确认撤销全部会话。",
        "未确认会话撤销仍提交");
      stage = "重置确认：取消零写入";
      await dialog.getByRole("button", { name: "取消" }).click();
      await dialog.waitFor({ state: "hidden" });
    });
    await check("重置密码并撤销新管理员全部旧会话", async () => {
      const row = platform.locator(".platform-account-page tbody tr").filter({ hasText: created.account.loginName });
      await row.getByRole("button", { name: "重置密码" }).click();
      const dialog = platform.getByRole("dialog");
      await dialog.getByLabel("新密码").fill(created.account.replacementPassword);
      await dialog.getByLabel("再次输入").fill(created.account.replacementPassword);
      await dialog.getByRole("checkbox", { name: "确认撤销全部会话" }).check();
      const sent = platform.waitForResponse(response => new URL(response.url()).pathname.endsWith(
        `/platform/accounts/${created.account.subjectId}/password-reset`));
      await dialog.getByRole("button", { name: "重置密码" }).click();
      requireCondition((await sent).status() === 204, "重置接口未成功");
      await assertAnonymous(firstSession.page);
      await firstSession.context.close();
      firstSession = undefined;
      const rejected = await login(browser, configuration, created.account, false);
      await rejected.context.close();
      const admitted = await login(browser, configuration,
        { ...created.account, password: created.account.replacementPassword });
      firstSession = admitted;
    });
    await check("停用账户立即阻断登录并使旧会话失效", async () => {
      const row = platform.locator(".platform-account-page tbody tr").filter({ hasText: created.account.loginName });
      await row.getByText("更多", { exact: true }).click();
      await row.getByRole("button", { name: "停用账户" }).click();
      const dialog = platform.getByRole("dialog");
      requireCondition(await dialog.getByRole("heading", { name: "停用人员账户" }).isVisible()
        && await dialog.getByText("可由其他平台管理员恢复此账户。").isVisible(),
        "停用影响与恢复说明不完整");
      const sent = platform.waitForResponse(response => new URL(response.url()).pathname.endsWith(
        `/platform/accounts/${created.account.subjectId}/disable`));
      await dialog.getByRole("button", { name: "确认停用" }).click();
      requireCondition((await sent).status() === 204, "停用接口未成功");
      await assertAnonymous(firstSession.page);
      await firstSession.context.close();
      firstSession = undefined;
      const rejected = await login(browser, configuration,
        { ...created.account, password: created.account.replacementPassword }, false);
      await rejected.context.close();
    });
    await check("恢复账户不复活旧会话且允许重新登录", async () => {
      const row = platform.locator(".platform-account-page tbody tr").filter({ hasText: created.account.loginName });
      await row.getByText("已停用").waitFor();
      await row.getByText("更多", { exact: true }).click();
      await row.getByRole("button", { name: "恢复账户" }).click();
      requireCondition(await platform.getByRole("dialog").getByRole("heading", { name: "恢复人员账户" }).isVisible(),
        "恢复弹窗标题与原型不一致");
      const sent = platform.waitForResponse(response => new URL(response.url()).pathname.endsWith(
        `/platform/accounts/${created.account.subjectId}/enable`));
      await platform.getByRole("dialog").getByRole("button", { name: "确认恢复" }).click();
      requireCondition((await sent).status() === 204, "恢复接口未成功");
      const admitted = await login(browser, configuration,
        { ...created.account, password: created.account.replacementPassword });
      await admitted.context.close();
    });
    await check("正式接口建立分页对照账户且总数只含平台管理员", async () => {
      for (let index = 0; index < 11; index += 1) {
        const loginName = `wp17pp-${suffix}-${index}`;
        const response = await request(platform, "/platform/accounts", "POST",
          { loginName, password: newPassword() });
        requireCondition(response.status === 201 && response.body?.activeRoleCodes?.includes("PLATFORM_ADMINISTRATOR"),
          "分页对照账户未原子建立");
        created.extras.push({ loginName, subjectId: response.body.subjectId });
        persist();
      }
    });
    await check("服务端搜索、状态、页大小和跳页随 URL 恢复", async () => {
      await platform.getByRole("searchbox", { name: "按登录名搜索" }).fill(`wp17pp-${suffix}`);
      await platform.waitForURL(url => url.pathname === "/platform/accounts"
        && url.searchParams.get("q") === `wp17pp-${suffix}`);
      await platform.getByRole("combobox", { name: "每页条数" }).selectOption("10");
      await platform.waitForURL(url => url.searchParams.get("pageSize") === "10");
      await platform.locator(".platform-account-page tbody tr").first().waitFor();
      requireCondition(await platform.locator(".platform-account-page tbody tr").count() === 10
        && await platform.getByLabel("账户目录分页").innerText().then(value => value.includes("共 11 条")),
      "搜索未在服务端先筛选后分页");
      await platform.getByRole("button", { name: "下一页" }).click();
      await platform.waitForURL(url => url.searchParams.get("page") === "2");
      await platform.getByLabel("跳转页码").fill("1");
      await platform.getByRole("button", { name: "跳转" }).click();
      await platform.waitForURL(url => url.searchParams.get("page") === "1");
      await platform.reload({ waitUntil: "networkidle" });
      requireCondition(await platform.getByRole("searchbox", { name: "按登录名搜索" }).inputValue() === `wp17pp-${suffix}`
        && await platform.locator(".platform-account-page tbody tr").count() === 10,
      "刷新未恢复 URL 目录状态");
      await platform.getByRole("combobox", { name: "账户状态" }).selectOption("DISABLED");
      await platform.locator(".platform-account-page__empty").waitFor();
      requireCondition(await platform.getByRole("button", { name: "创建平台账户" }).count() === 2,
        "空筛选未保留创建入口");
    });
    await check("390、1024、1440 与 200% 文字无水平裁切", async () => {
      await platform.getByRole("combobox", { name: "账户状态" }).selectOption("ALL");
      for (const width of [390, 1024, 1440]) {
        await platform.setViewportSize({ width, height: 960 });
        await withTextScale(platform, width === 390, async () => {
          const boxes = await platform.evaluate(() => ({
            overflow: document.documentElement.scrollWidth > innerWidth + 1,
            clipped: [...document.querySelectorAll(".platform-account-page :is(button,td,th,summary)")]
              .filter(element => element.getClientRects().length > 0)
              .some(element => element.clientWidth > 0 && element.scrollWidth > element.clientWidth + 1),
            motion: matchMedia("(prefers-reduced-motion: reduce)").matches,
            aligned: Math.abs(document.querySelector(".page-heading").getBoundingClientRect().right
              - document.querySelector(".platform-account-page").getBoundingClientRect().right) <= 1,
            mobileAction: innerWidth > 680 || document.querySelector(".page-heading > button").getBoundingClientRect().width
              >= document.querySelector(".page-heading").getBoundingClientRect().width - 2,
          }));
          requireCondition(!boxes.overflow && !boxes.clipped && boxes.motion && boxes.aligned && boxes.mobileAction,
            "账户页在目标尺寸或放大文字下裁切");
        });
      }
    });
    await check("390px 行操作对齐原型分页且保持可点击", async () => {
      await platform.setViewportSize({ width: 390, height: 960 });
      const summary = platform.locator(".platform-account-page tbody tr:last-child summary");
      await summary.evaluate(element => element.scrollIntoView({ block: "center" }));
      const layout = await summary.evaluate(element => {
        const trigger = element.getBoundingClientRect();
        const shell = element.closest(".tenant-admin-page__table-shell");
        const pagination = shell.querySelector(".tenant-admin-page__pagination").getBoundingClientRect();
        const hit = document.elementFromPoint(trigger.x + trigger.width / 2, trigger.y + trigger.height / 2);
        return { aligned: Math.abs(trigger.y - pagination.y) <= 1,
          tappable: trigger.height >= 44 && (hit === element || element.contains(hit)) };
      });
      requireCondition(layout.aligned && layout.tappable, "移动端行操作未对齐原型或被分页遮挡");
      await summary.click();
      requireCondition(await summary.evaluate(element => element.parentElement.open), "移动端行操作不能展开");
      await summary.click();
    });
    requireCondition(pageErrors === 0, "账户页出现浏览器脚本错误");
    created.status = "VERIFIED";
    persist();
    process.stdout.write("WP-0017 / accounts 独立切片真实浏览器场景通过；整包与 M6 仍未完成。\n");
  } catch (error) {
    throw new Error(`平台账户验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；合成对象保留于 0600 临时文件，未输出秘密。`);
  } finally {
    fs.closeSync(descriptor);
    await browser?.close();
  }
}

module.exports = { readConfiguration };
if (require.main === module) {
  try { run(readConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
