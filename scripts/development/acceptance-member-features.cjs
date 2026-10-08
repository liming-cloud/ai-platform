/* WP-0018 功能独立切片：仅正式本机 UI/API；新建隔离验收租户，不调整历史对象。 */
const fs = require("node:fs");
const crypto = require("node:crypto");
const { readConfiguration: loginConfiguration, request, requireCondition, SafeAssertionError,
  openLogin, fillLogin, submitLogin, assertWorkspace, logoutWorkspace } = require("./acceptance-login.cjs");
const { onboardingRequest, withTextScale } = require("./acceptance-onboarding.cjs");
const { freshOutputFile, keyedRequest, memberLogin, verifyLayout } = require("./acceptance-member-account.cjs");

const codes = ["assistantSelection", "conversation", "ownHistory", "authorizedCitations"];
const initial = { assistantSelection: true, conversation: true, ownHistory: false, authorizedCitations: false };

function readConfiguration(environment = process.env) {
  requireCondition(!environment.SETUP, "功能切片只准备本轮隔离对象，不接受外部 SETUP");
  return { ...loginConfiguration(environment), outputFile: freshOutputFile(environment) };
}

/** 只允许安全投影和精确四布尔值；版本、保存值和有效值不能混为一个事实。 */
function verifyFeatures(body) {
  const fields = ["membershipId", "loginName", "status", "readOnly", "conversationFeatures", "featureVersion",
    "tenantAvailableFeatures", "tenantFeatureVersion", "effectiveFeatures"];
  requireCondition(body && Object.keys(body).length === fields.length && fields.every(key => Object.hasOwn(body, key)), "功能响应不是最小安全投影");
  requireCondition(typeof body.membershipId === "string" && /^[1-9]\d{0,18}$/.test(body.membershipId)
    && BigInt(body.membershipId) <= 9223372036854775807n && typeof body.loginName === "string"
    && ["ENABLED", "DISABLED"].includes(body.status) && typeof body.readOnly === "boolean"
    && Number.isSafeInteger(body.featureVersion) && body.featureVersion >= 0
    && Number.isSafeInteger(body.tenantFeatureVersion) && body.tenantFeatureVersion >= 0, "功能响应缺少明确资格或版本事实");
  for (const key of ["conversationFeatures", "tenantAvailableFeatures", "effectiveFeatures"]) {
    const values = body[key];
    requireCondition(values && (key === "conversationFeatures" && body.readOnly ? Object.keys(values).length === 0
      : Object.keys(values).length === 4 && codes.every(code => typeof values[code] === "boolean")), "功能响应包含未知、缺失或非布尔值");
  }
}

const input = (facts, values) => ({ conversationFeatures: values, expectedFeatureVersion: facts.featureVersion, expectedTenantFeatureVersion: facts.tenantFeatureVersion });
const same = (a, b) => codes.every(code => a[code] === b[code]);

async function run(configuration) {
  requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定的 Playwright 运行时");
  const suffix = crypto.randomBytes(6).toString("hex");
  const password = () => `Aa9!${crypto.randomBytes(24).toString("base64url")}`;
  const generated = { status: "STARTED", platform: configuration.credentials.platform, suffix,
    tenant: { tenantCode: `wp18f-${suffix}`, loginName: `wp18fa-${suffix}`, password: password() },
    member: { loginName: `wp18fm-${suffix}`, password: password() } };
  const descriptor = fs.openSync(configuration.outputFile, "wx", 0o600);
  const persist = () => { fs.ftruncateSync(descriptor, 0); fs.writeSync(descriptor, JSON.stringify(generated), 0, "utf8"); fs.fsyncSync(descriptor); };
  persist();
  let stage = "启动浏览器"; let browser; let errors = 0; let writes = 0;
  const check = async (name, action) => { stage = name; await action(); process.stdout.write(`${JSON.stringify({ assertion: name, result: "通过" })}\n`); };
  try {
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
    const page = await context.newPage(); page.setDefaultTimeout(15_000);
    const platformContext = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
    const platform = await platformContext.newPage(); platform.setDefaultTimeout(15_000);
    const memberConfiguration = { ...configuration, credentials: generated };
    for (const target of [page, platform]) target.on("pageerror", () => { errors += 1; });
    page.on("request", sent => { if (sent.method() === "PUT" && /\/tenant\/members\/\d+\/features$/.test(new URL(sent.url()).pathname)) writes += 1; });
    const route = () => `/tenant/members/${generated.member.membershipId}/features`;
    const direct = async target => { await target.goto(configuration.baseUrl + route(), { waitUntil: "networkidle" }); await target.getByRole("heading", { name: generated.member.loginName, exact: true }).waitFor(); };
    const read = async () => { const result = await request(page, route()); requireCondition(result.status === 200, "功能读取未成功"); verifyFeatures(result.body); return result.body; };
    const loginAdmin = async () => { await openLogin(page, memberConfiguration, "tenant"); await fillLogin(page, generated.tenant, "tenant"); await submitLogin(page); await assertWorkspace(page, "tenant"); };
    const saveUI = async () => {
      const sent = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1" + route() && response.request().method() === "PUT");
      await page.getByRole("button", { name: "保存对话功能", exact: true }).click(); return sent;
    };
    await check("正式接口准备本轮独占租户和普通成员，历史对象不改动", async () => {
      await openLogin(platform, configuration, "platform"); await fillLogin(platform, generated.platform, "platform"); await submitLogin(platform); await assertWorkspace(platform, "platform");
      const created = await keyedRequest(platform, "/platform/tenants", { ...onboardingRequest(generated.tenant), displayName: "WP-0018 功能隔离验收租户" }, `features-tenant-${suffix}`);
      requireCondition(created.status === 201 && created.body?.status === "ACTIVE", "隔离租户准备未成功");
      generated.tenant.tenantId = created.body.tenantId; persist();
      await loginAdmin();
      const options = (await request(page, "/tenant/members/create-options")).body;
      const member = await keyedRequest(page, "/tenant/members", { loginName: generated.member.loginName, password: generated.member.password,
        conversationFeatures: initial, assistantIds: [], expectedTenantFeatureVersion: options.tenantFeatureVersion, expectedAuthorizationRevision: options.authorizationRevision }, `features-member-${suffix}`);
      requireCondition(member.status === 201 && member.body.membershipRole === "TENANT_USER" && member.body.assistantIds.length === 0, "普通成员准备未成功");
      generated.member.membershipId = member.body.membershipId; generated.initialAccount = { version: member.body.version, permissionVersion: member.body.permissionVersion, identityVersion: member.body.identityVersion };
      persist(); await direct(page);
      requireCondition(same((await read()).conversationFeatures, initial), "功能页未读取真实保存值");
    });
    await check("取消和刷新只操作本页非秘密草稿，不产生功能写入", async () => {
      await page.getByRole("checkbox", { name: "本人历史", exact: true }).check(); await page.reload({ waitUntil: "networkidle" });
      await page.getByText("已恢复本页未保存修改。", { exact: true }).waitFor();
      requireCondition(await page.getByRole("checkbox", { name: "本人历史", exact: true }).isChecked(), "刷新没有恢复本页草稿");
      requireCondition(writes === 0 && same((await read()).conversationFeatures, initial), "刷新或草稿编辑产生业务写入");
      await page.getByRole("button", { name: "取消修改", exact: true }).click(); await page.reload({ waitUntil: "networkidle" }); await direct(page);
      requireCondition(!await page.getByRole("checkbox", { name: "本人历史", exact: true }).isChecked() && writes === 0, "取消没有清除草稿或产生写入");
      const safe = await page.evaluate(secrets => !secrets.some(secret => JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }).includes(secret)), [generated.member.password, generated.tenant.password]);
      requireCondition(safe, "浏览器草稿包含秘密");
    });
    const oldMember = await memberLogin(browser, memberConfiguration, generated.member);
    let originalRevision;
    await check("正式界面独立保存推进功能版本且不改写助手或身份", async () => {
      originalRevision = (await request(page, "/tenant/members/create-options")).body.authorizationRevision;
      await page.getByRole("checkbox", { name: "选择助手", exact: true }).uncheck();
      await page.getByRole("checkbox", { name: "本人历史", exact: true }).check();
      await page.getByRole("checkbox", { name: "查看引用", exact: true }).check();
      requireCondition((await saveUI()).status() === 200, "正式功能保存未成功"); await page.getByText("对话功能已保存。", { exact: true }).waitFor();
      const facts = await read(); requireCondition(facts.featureVersion === 1 && same(facts.conversationFeatures, { assistantSelection: false, conversation: true, ownHistory: true, authorizedCitations: true }), "保存值或独立版本不正确");
      const account = (await request(page, `/tenant/members/${generated.member.membershipId}`)).body;
      requireCondition(account.permissionVersion === 1 && account.version === 1 && account.identityVersion === 0 && account.assistantIds.length === 0
        && (await request(page, "/tenant/members/create-options")).body.authorizationRevision === originalRevision, "功能保存覆盖助手、身份或授权 revision");
    });
    await check("旧资格会话立即失效，普通成员和平台不能直接管理功能", async () => {
      requireCondition((await request(oldMember.page, "/auth/browser-session")).status === 401, "功能保存未撤销旧会话");
      requireCondition((await request(page, "/auth/browser-session")).status === 200, "功能保存撤销操作者会话");
      const current = await memberLogin(browser, memberConfiguration, generated.member);
      requireCondition((await request(current.page, route())).status === 403 && (await request(current.page, "/query-assistants?pageNumber=1&pageSize=20")).status === 403, "成员获得管理权限或关闭的助手选择仍可直达");
      requireCondition((await keyedRequest(current.page, "/query-runs", { assistantVersionId: "1", question: "合成范围拒绝" }, `features-query-${suffix}`)).status === 403, "无助手成员可直接问答");
      requireCondition((await request(platform, route())).status === 401, "平台会话进入租户功能管理范围");
      await current.context.close(); await oldMember.context.close();
    });
    await check("交错编辑保留原始版本，旧草稿拒绝且显式刷新可恢复", async () => {
      const tab = await context.newPage(); await direct(tab);
      await page.getByRole("checkbox", { name: "本人历史", exact: true }).uncheck(); requireCondition((await saveUI()).status() === 200, "新编辑未保存");
      await tab.getByRole("checkbox", { name: "选择助手", exact: true }).check();
      const sent = tab.waitForResponse(response => response.request().method() === "PUT" && response.url().endsWith(route()));
      await tab.getByRole("button", { name: "保存对话功能", exact: true }).click(); requireCondition((await sent).status() === 409, "旧页覆盖新保存值");
      await tab.getByRole("alert").waitFor();
      requireCondition((await read()).featureVersion === 2, "冲突请求仍推进版本");
      await tab.getByRole("button", { name: "重新读取配置", exact: true }).click(); await tab.getByRole("heading", { name: generated.member.loginName, exact: true }).waitFor();
      requireCondition(!await tab.getByRole("checkbox", { name: "本人历史", exact: true }).isChecked(), "显式刷新未读取新保存值"); await tab.close();
    });
    await check("无变化保存不推进版本，严格字段及缺少CSRF均拒绝", async () => {
      await direct(page); const facts = await read(); requireCondition((await saveUI()).status() === 200 && (await read()).featureVersion === facts.featureVersion, "无变化保存推进版本");
      const good = input(facts, facts.conversationFeatures);
      for (const invalid of [{ ...good, assistantIds: [] }, { ...good, tenantId: generated.tenant.tenantId },
        { ...good, conversationFeatures: { conversation: true } }, { ...good, conversationFeatures: { ...facts.conversationFeatures, ownHistory: "true" } }, { ...good, expectedFeatureVersion: 0.5 }]) {
        requireCondition((await request(page, route(), "PUT", invalid)).status === 400, "未知缺失或非布尔功能字段被接受");
      }
      requireCondition((await request(page, route() + "?tenantId=1")).status === 400 && (await request(page, route(), "PUT", good, false)).status === 403, "范围伪造或缺少CSRF仍被接受");
      requireCondition((await read()).featureVersion === facts.featureVersion, "拒绝请求仍改变版本");
    });
    await check("管理员目标只读，错租户目标读取与保存均拒绝", async () => {
      const admin = (await request(page, "/tenant/members?pageNumber=1&pageSize=20")).body.items.find(item => item.membershipRole === "TENANT_ADMINISTRATOR");
      requireCondition(admin, "隔离租户没有管理员事实");
      const readonly = await request(page, `/tenant/members/${admin.membershipId}/features`); verifyFeatures(readonly.body);
      requireCondition(readonly.body.readOnly && (await request(page, `/tenant/members/${admin.membershipId}/features`, "PUT", input(await read(), initial))).status === 403, "管理员目标可以写入普通功能");
      const denied = await request(page, "/tenant/members/3382738893456539650/features");
      requireCondition(denied.status === 403, "错租户成员可读取配置");
      await page.goto(`${configuration.baseUrl}/tenant/members/${admin.membershipId}/features`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "管理员账号只读", exact: true }).waitFor();
      requireCondition(await page.getByRole("checkbox").count() === 0 && await page.getByRole("button", { name: "保存对话功能", exact: true }).count() === 0, "管理员只读页仍暴露编辑控件"); await direct(page);
    });
    await check("并发同版本保存只有一个获胜，独立授权 revision 保持不变", async () => {
      const facts = await read();
      const first = { ...facts.conversationFeatures, assistantSelection: true };
      const second = { ...facts.conversationFeatures, authorizedCitations: false };
      const outcomes = await Promise.all([request(page, route(), "PUT", input(facts, first)), request(page, route(), "PUT", input(facts, second))]);
      requireCondition(outcomes.map(value => value.status).sort().join(",") === "200,409" && (await read()).featureVersion === facts.featureVersion + 1, "并发保存发生覆盖或重复提交");
      requireCondition((await request(page, "/tenant/members/create-options")).body.authorizationRevision === originalRevision, "并发功能保存改写助手授权 revision");
      const current = await read(); const baseline = { assistantSelection: false, conversation: true, ownHistory: false, authorizedCitations: true };
      requireCondition((await request(page, route(), "PUT", input(current, baseline))).status === 200, "未能建立上限验证的明确保存基线");
    });
    await check("租户关闭上限保留保存值，过时上限冲突且禁止新增越界授权", async () => {
      const before = await read(); const upperRoute = `/platform/tenants/${generated.tenant.tenantId}/features`;
      const upper = (await request(platform, upperRoute)).body;
      const closed = { ...upper.availableFeatures, conversation: false, ownHistory: false };
      requireCondition((await request(platform, upperRoute, "PUT", { availableFeatures: closed, expectedVersion: upper.version })).status === 200, "隔离租户上限关闭未成功");
      await loginAdmin(); await direct(page);
      const current = await read();
      requireCondition(current.conversationFeatures.conversation && !current.effectiveFeatures.conversation
        && await page.getByRole("checkbox", { name: "发起和继续对话 · 租户未开放", exact: true }).isChecked()
        && await page.getByRole("checkbox", { name: "发起和继续对话 · 租户未开放", exact: true }).isDisabled(), "上限关闭丢失保存值或仍可编辑越界控件");
      requireCondition((await request(page, route(), "PUT", input(before, before.conversationFeatures))).status === 409, "过时上限版本被接受");
      requireCondition((await request(page, route(), "PUT", input(current, { ...current.conversationFeatures, ownHistory: true }))).status === 400, "租户未开放功能仍能新增授权");
      await page.getByRole("checkbox", { name: "选择助手", exact: true }).check(); requireCondition((await saveUI()).status() === 200, "保留上限已关闭保存值时无法编辑其他合法功能");
      const closedNow = (await request(platform, upperRoute)).body;
      requireCondition((await request(platform, upperRoute, "PUT", { availableFeatures: upper.availableFeatures, expectedVersion: closedNow.version })).status === 200, "本轮隔离租户上限恢复失败");
      await loginAdmin(); await direct(page);
      requireCondition(!(await read()).conversationFeatures.ownHistory && !(await read()).effectiveFeatures.ownHistory, "恢复租户上限隐式新增了成员历史授权");
    });
    await check("停用资格仍可编辑功能，恢复保持新保存值且不激活旧会话", async () => {
      const before = (await request(page, `/tenant/members/${generated.member.membershipId}`)).body;
      const old = await memberLogin(browser, memberConfiguration, generated.member);
      requireCondition((await request(page, `/tenant/members/${generated.member.membershipId}/disable`, "POST", { expectedVersion: before.version })).status === 200, "隔离成员停用未成功");
      await direct(page); await page.getByRole("checkbox", { name: "查看引用", exact: true }).uncheck(); requireCondition((await saveUI()).status() === 200, "停用成员无法保存功能");
      const saved = await read();
      requireCondition(saved.status === "DISABLED", "停用编辑未保持资格状态");
      requireCondition(Object.values(saved.effectiveFeatures).every(value => value === false), "停用资格仍有有效功能");
      const rejected = await memberLogin(browser, memberConfiguration, generated.member, false); await rejected.context.close();
      const disabled = (await request(page, `/tenant/members/${generated.member.membershipId}`)).body;
      requireCondition((await request(page, `/tenant/members/${generated.member.membershipId}/enable`, "POST", { expectedVersion: disabled.version })).status === 200, "隔离成员恢复未成功");
      requireCondition((await request(old.page, "/auth/browser-session")).status === 401 && !(await read()).conversationFeatures.authorizedCitations, "恢复激活旧会话或覆盖保存配置");
      const current = await memberLogin(browser, memberConfiguration, generated.member);
      requireCondition((await request(current.page, "/query-runs/1")).status === 403, "关闭本人历史时运行读取入口未先拒绝");
      const eventStatus = await current.page.evaluate(async () => (await fetch("/api/v1/query-runs/1/events", {
        headers: { Accept: "text/event-stream" }, credentials: "same-origin", signal: AbortSignal.timeout(10_000),
      })).status);
      requireCondition(eventStatus === 403, "关闭本人历史时事件续传入口未先拒绝");
      await current.context.close(); await old.context.close(); await direct(page);
    });
    await check("退出清除功能草稿，新登录不继承上一代编辑", async () => {
      await page.getByRole("checkbox", { name: "本人历史", exact: true }).check(); await logoutWorkspace(page);
      await page.getByRole("heading", { name: "租户管理员登录", exact: true }).waitFor();
      await loginAdmin(); await direct(page);
      requireCondition(!await page.getByRole("checkbox", { name: "本人历史", exact: true }).isChecked(), "新登录恢复上一代草稿");
    });
    await check("未保存修改离开须确认，保留及浏览器后退均不丢失草稿", async () => {
      // 先建立同文档对象导航；硬刷新与跨文档后退通过隔离草稿恢复，不伪装为 SPA 后退。
      await page.getByRole("button", { name: "账号信息", exact: true }).click(); await page.locator(".member-account-facts").waitFor();
      await page.getByRole("button", { name: "对话功能", exact: true }).click(); await page.getByRole("heading", { name: generated.member.loginName, exact: true }).waitFor();
      await page.getByRole("checkbox", { name: "本人历史", exact: true }).check();
      await page.getByRole("button", { name: "账号信息", exact: true }).click();
      const dialog = page.getByRole("dialog"); await dialog.getByRole("heading", { name: "修改尚未保存", exact: true }).waitFor();
      await dialog.getByRole("button", { name: "保留当前", exact: true }).click(); await dialog.waitFor({ state: "hidden" });
      requireCondition(new URL(page.url()).pathname === route() && await page.getByRole("checkbox", { name: "本人历史", exact: true }).isChecked(), "保留当前仍离开或清除草稿");
      await page.locator(".product-navigation button").filter({ hasText: "知识库" }).click();
      await dialog.getByRole("heading", { name: "修改尚未保存", exact: true }).waitFor();
      await dialog.getByRole("button", { name: "保留当前", exact: true }).click(); await dialog.waitFor({ state: "hidden" });
      requireCondition(new URL(page.url()).pathname === route(), "侧栏跳转绕过未保存门禁");
      await page.goBack(); await dialog.getByRole("heading", { name: "修改尚未保存", exact: true }).waitFor();
      await page.keyboard.press("Escape"); await dialog.waitFor({ state: "hidden" });
      requireCondition(new URL(page.url()).pathname === route(), "取消浏览器后退仍离开编辑页");
      await page.getByRole("button", { name: "账号信息", exact: true }).click();
      await dialog.getByRole("button", { name: "放弃修改并离开", exact: true }).click(); await page.locator(".member-account-facts").waitFor();
      await page.getByRole("button", { name: "对话功能", exact: true }).click(); await page.getByRole("heading", { name: generated.member.loginName, exact: true }).waitFor();
      requireCondition(!await page.getByRole("checkbox", { name: "本人历史", exact: true }).isChecked() && !(await read()).conversationFeatures.ownHistory, "放弃修改仍恢复旧草稿或写入业务");
    });
    await check("三尺寸两倍文字键盘与reduced-motion无裁切且账号可往返", async () => {
      for (const width of [390, 1024, 1440]) {
        await page.setViewportSize({ width, height: 960 }); await direct(page);
        for (const enlarged of [false, true]) await withTextScale(page, enlarged, async () => {
          await verifyLayout(page);
          const matches = await page.evaluate(() => {
            const row = document.querySelector(".member-feature-control"); const input = row.querySelector("input");
            const label = row.querySelector("span"); const box = row.getBoundingClientRect();
            return input.getBoundingClientRect().x - box.x === 24 && label.getBoundingClientRect().x - box.x === 57
              && getComputedStyle(input).margin === "2px 3px 3px 4px"
              && getComputedStyle(row).transitionDuration === "0s"
              && [...document.querySelectorAll(".member-form-actions button")].every(button => {
                const style = getComputedStyle(button);
                return style.paddingBlock === "6px" && button.getBoundingClientRect().height === Math.max(parseFloat(style.minHeight), parseFloat(style.fontSize) * 20 / 14 + 14);
              });
          });
          requireCondition(matches, "功能复选框、短按钮或 reduced-motion 与已接受原型不一致");
          for (const button of await page.locator(".member-form-actions button").all()) {
            const borderColor = await button.evaluate(element => getComputedStyle(element).borderColor);
            await button.focus(); await page.keyboard.press("Shift");
            await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
            requireCondition(await button.evaluate((element, expectedBorderColor) => element.matches(":focus-visible")
              && getComputedStyle(element).outlineOffset === "2px" && getComputedStyle(element).borderRadius === "8px"
              && getComputedStyle(element).borderColor === expectedBorderColor
              && getComputedStyle(element).boxShadow === "none", borderColor), "功能按钮键盘焦点样式与原型不一致");
          }
        });
        await page.getByRole("checkbox", { name: "本人历史", exact: true }).check();
        await page.getByRole("button", { name: "账号信息", exact: true }).click();
        const dialog = page.getByRole("dialog"); await dialog.getByRole("heading", { name: "修改尚未保存", exact: true }).waitFor();
        for (const enlarged of [false, true]) await withTextScale(page, enlarged, async () => {
          await verifyLayout(page);
          const matches = await dialog.evaluate(element => element.getBoundingClientRect().width === Math.min(512, innerWidth - 38)
            && [...element.querySelectorAll("p")].every(paragraph => getComputedStyle(paragraph).margin === "0px")
            && getComputedStyle(element.querySelector(".member-dialog-content")).marginTop === "16px"
            && getComputedStyle(element.querySelector(".member-dialog-actions")).marginTop === "16px");
          requireCondition(matches, "未保存提示尺寸或段落留白与已接受原型不一致");
          for (const button of await dialog.getByRole("button").all()) {
            const borderColor = await button.evaluate(element => getComputedStyle(element).borderColor);
            await button.focus(); await page.keyboard.press("Shift");
            await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
            requireCondition(await button.evaluate((element, expectedBorderColor) => element.matches(":focus-visible")
              && getComputedStyle(element).outlineStyle === "solid" && getComputedStyle(element).outlineWidth === "2px"
              && getComputedStyle(element).outlineOffset === "2px"
              && getComputedStyle(element).borderColor === expectedBorderColor
              && getComputedStyle(element).boxShadow === "none", borderColor), "未保存提示键盘焦点样式与原型不一致");
          }
        }, '[role="dialog"]');
        await dialog.getByRole("button", { name: "保留当前", exact: true }).click(); await dialog.waitFor({ state: "hidden" });
        await page.getByRole("button", { name: "取消修改", exact: true }).click(); await direct(page);
        await page.getByRole("checkbox", { name: "选择助手", exact: true }).focus(); await page.keyboard.press("Tab");
        requireCondition(await page.getByRole("checkbox", { name: "发起和继续对话", exact: true }).evaluate(element => element === document.activeElement), "功能键盘顺序不正确");
        await page.getByRole("button", { name: "账号信息", exact: true }).click(); await page.locator(".member-account-facts").waitFor();
        await page.getByRole("button", { name: "对话功能", exact: true }).click(); await page.getByRole("heading", { name: generated.member.loginName, exact: true }).waitFor();
      }
    });
    requireCondition(errors === 0, "功能页面出现浏览器脚本错误");
    generated.finalFacts = await read(); generated.status = "VERIFIED"; persist();
    process.stdout.write("WP-0018 / features 独立切片浏览器场景通过；助手／策略、完整工作区、整包与 M6 未完成。\n");
  } catch (error) {
    throw new Error(`成员功能验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；本轮合成对象与凭据保留于独占临时文件。`);
  } finally { fs.closeSync(descriptor); await browser?.close(); }
}

module.exports = { readConfiguration, verifyFeatures };
if (require.main === module) {
  try { run(readConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
