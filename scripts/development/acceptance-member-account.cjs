/* WP-0018 账户独立切片：正式浏览器和 API，不拦截响应、不保留 trace/profile 或秘密日志。 */
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { readConfiguration: readLoginConfiguration, request, requireCondition, SafeAssertionError,
  openLogin, fillLogin, submitLogin, assertWorkspace } = require("./acceptance-login.cjs");
const { withTextScale } = require("./acceptance-onboarding.cjs");

function readConfiguration(environment = process.env) {
  requireCondition(!environment.SETUP, "成员账户切片不创建或重置租户");
  const result = readLoginConfiguration(environment);
  const outputFile = environment.FOUNDATION_ACCEPTANCE_OUTPUT_FILE;
  requireCondition(typeof outputFile === "string" && path.isAbsolute(outputFile)
    && outputFile.startsWith("/private/tmp/") && !fs.existsSync(outputFile), "必须指定尚不存在的临时凭据输出文件");
  const parent = fs.statSync(path.dirname(outputFile));
  requireCondition(parent.isDirectory() && parent.uid === process.getuid() && (parent.mode & 0o777) === 0o700,
    "输出目录必须为当前用户独占的 0700 临时目录");
  return { ...result, outputFile };
}

/** 安全白名单拒绝额外主体、凭据或会话字段；所有失败只披露静态断言。 */
function verifyAccount(body, loginName) {
  const keys = ["membershipId", "loginName", "membershipRole", "status", "version", "identityVersion", "permissionVersion",
    "createdAt", "conversationFeatures", "featureVersion", "assistantIds", "passwordResetEligible"];
  requireCondition(body && Object.keys(body).length === keys.length && keys.every(key => Object.hasOwn(body, key)), "账户响应不是最小安全投影");
  requireCondition(typeof body.membershipId === "string" && /^[1-9]\d{0,18}$/.test(body.membershipId)
    && BigInt(body.membershipId) <= 9223372036854775807n && body.loginName === loginName
    && body.membershipRole === "TENANT_USER" && body.status === "ENABLED" && body.version === 0
    && body.identityVersion === 0 && body.featureVersion === 0 && body.passwordResetEligible === true,
    "原子开户响应缺少新普通成员与版本事实");
  requireCondition(JSON.stringify(body.conversationFeatures) !== undefined && body.conversationFeatures.assistantSelection === true
    && body.conversationFeatures.conversation === true && body.conversationFeatures.ownHistory === false
    && body.conversationFeatures.authorizedCitations === false && Object.keys(body.conversationFeatures).length === 4
    && Array.isArray(body.assistantIds) && body.assistantIds.length === 0, "初始功能或无助手范围不符合显式开户配置");
}

async function keyedRequest(page, route, data, key) {
  return page.evaluate(async ({ route, data, key }) => {
    const value = document.cookie.split(";").map(item => item.trim()).find(item => item.startsWith("__Host-foundation-csrf="));
    const headers = { "Content-Type": "application/json", "Idempotency-Key": key };
    if (value) headers["X-CSRF-Token"] = value.slice(value.indexOf("=") + 1);
    const response = await fetch(`/api/v1${route}`, { method: "POST", credentials: "same-origin", headers,
      body: JSON.stringify(data), signal: AbortSignal.timeout(10_000) });
    return { status: response.status, body: response.ok && response.headers.get("content-type")?.includes("application/json") ? await response.json() : undefined };
  }, { route, data, key });
}

async function memberLogin(browser, configuration, identity, accepted = true) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
  const page = await context.newPage(); page.setDefaultTimeout(15_000);
  await page.goto(`${configuration.baseUrl}/ask`, { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: "知识问答登录", exact: true }).waitFor();
  await fillLogin(page, { ...identity, tenantCode: configuration.credentials.tenant.tenantCode }, "tenant");
  const sent = page.waitForResponse(response => response.url().endsWith("/api/v1/auth/browser-sessions") && response.request().method() === "POST");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  requireCondition((await sent).status() === (accepted ? 204 : 401), "新成员凭据与资格状态的登录裁决不正确");
  return { context, page };
}

async function verifyLayout(page) {
  const layout = await page.evaluate(() => {
    const main = document.querySelector('[role="dialog"]') || document.querySelector("main");
    const visible = [...main.querySelectorAll("h1,h2,p,label,input,button,th,td")].filter(element => element.getClientRects().length);
    const viewport = document.documentElement.clientWidth;
    return document.documentElement.scrollWidth <= viewport + 1 && visible.every(element => {
      const box = element.getBoundingClientRect();
      return box.width > 0 && box.left >= -1 && box.right <= viewport + 1
        && (element.matches("input") || element.clientWidth === 0 || element.scrollWidth <= element.clientWidth + 1)
        && (element.clientHeight === 0 || element.scrollHeight <= element.clientHeight + 1);
    }) && matchMedia("(prefers-reduced-motion: reduce)").matches
      && !document.getAnimations().some(animation => animation.playState === "running" && animation.effect?.getTiming().duration > 1);
  });
  requireCondition(layout, "成员页面出现横向裁切、溢出或控件遮挡");
  const root = await page.getByRole("dialog").count() ? page.getByRole("dialog") : page.locator("main");
  const controls = root.locator('button:not([disabled]), input:not([disabled]), select:not([disabled])');
  for (const control of await controls.all()) {
    if (!await control.isVisible()) continue;
    await control.focus(); await control.scrollIntoViewIfNeeded();
    const reachable = await control.evaluate(element => {
      const box = element.getBoundingClientRect(); const x = box.left + box.width / 2; const y = box.top + box.height / 2;
      const hit = document.elementFromPoint(x, y);
      return element === document.activeElement && y >= 0 && y <= innerHeight
        && (hit === element || element.contains(hit));
    });
    requireCondition(reachable, "成员页面焦点控件被固定导航或操作栏遮挡");
  }
}

async function run(configuration) {
  requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定的 Playwright 运行时");
  const suffix = crypto.randomBytes(6).toString("hex");
  const generated = { status: "STARTED", tenant: configuration.credentials.tenant,
    member: { loginName: `wp18m-${suffix}`, password: `Aa9!${crypto.randomBytes(24).toString("base64url")}`,
      replacementPassword: `Aa9!${crypto.randomBytes(24).toString("base64url")}` }, suffix };
  const descriptor = fs.openSync(configuration.outputFile, "wx", 0o600);
  const persist = () => { fs.ftruncateSync(descriptor, 0); fs.writeSync(descriptor, JSON.stringify(generated), 0, "utf8"); fs.fsyncSync(descriptor); };
  persist();
  let stage = "启动浏览器";
  let browser;
  const check = async (name, action) => { stage = name; await action(); process.stdout.write(`${JSON.stringify({ assertion: name, result: "通过" })}\n`); };
  try {
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
    const page = await context.newPage(); page.setDefaultTimeout(15_000);
    let errors = 0; let writes = 0;
    page.on("pageerror", () => { errors += 1; });
    page.on("request", outgoing => { if (outgoing.method() === "POST" && new URL(outgoing.url()).pathname === "/api/v1/tenant/members") writes += 1; });
    const directory = async () => {
      await page.goto(`${configuration.baseUrl}/access/members?page=1`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "成员与对话功能", exact: true }).waitFor();
      await page.locator(".member-directory-table tbody").waitFor();
    };
    const create = async () => { await page.getByRole("button", { name: "添加成员", exact: true }).click(); await page.getByRole("heading", { name: "成员信息", exact: true }).waitFor(); };
    const next = async () => { await page.getByRole("button", { name: "下一步", exact: true }).click(); };
    const findTarget = async () => {
      await directory(); await page.getByRole("searchbox", { name: "搜索成员", exact: true }).fill(generated.member.loginName);
      await page.waitForURL(url => url.searchParams.get("q") === generated.member.loginName);
      const row = page.locator(".member-directory-table tbody tr").filter({ hasText: generated.member.loginName }); await row.waitFor(); return row;
    };
    await check("当前租户管理员真实登录与目录管理员只读", async () => {
      await openLogin(page, configuration, "tenant"); await fillLogin(page, configuration.credentials.tenant, "tenant");
      await submitLogin(page); await assertWorkspace(page, "tenant"); await directory();
      requireCondition(JSON.stringify(await page.locator(".member-directory-table th").allTextContents()) === JSON.stringify(["成员", "角色", "状态", "助手范围", "操作"]), "成员目录未承接原型五列表格");
      const administrator = page.locator("tbody tr").filter({ hasText: "租户管理员" }).first(); await administrator.waitFor();
      requireCondition(await administrator.getByRole("button").count() === 0 && await administrator.getByText("只读", { exact: true }).isVisible(), "管理员目标未只读保护");
    });
    await check("三步开户取消不写入且密码不保留", async () => {
      await create(); await page.getByRole("textbox", { name: "登录名", exact: true }).fill(generated.member.loginName); await next();
      await page.getByText(/^匹配 [0-9]+ 个，已选 0 个$/).waitFor();
      await page.getByRole("button", { name: "下一步", exact: true }).waitFor({ state: "visible" }); await next();
      // Fluent 必填标签包含隐藏的星号；锚定完整标签，避免误选确认字段。
      await page.getByLabel(/^初始密码\*?$/).fill(generated.member.password);
      await page.getByRole("button", { name: "取消", exact: true }).click();
      await page.getByRole("heading", { name: "成员与对话功能", exact: true }).waitFor();
      requireCondition(writes === 0, "取消仍创建成员"); await create();
      requireCondition(await page.getByRole("textbox", { name: "登录名", exact: true }).inputValue() === "", "取消后仍恢复旧草稿");
      await page.goto(`${configuration.baseUrl}/tenant/members/new?step=3`, { waitUntil: "networkidle" });
      await page.waitForURL(url => url.searchParams.get("step") === "1");
      await page.getByRole("heading", { name: "成员信息", exact: true }).waitFor();
      requireCondition(await page.locator('input[type="password"]').count() === 0 && writes === 0, "无有效草稿的直接后续步骤未返回成员信息");
    });
    await check("刷新只恢复本会话非秘密草稿且确认错误清除密码", async () => {
      await page.getByRole("textbox", { name: "登录名", exact: true }).fill(generated.member.loginName); await next();
      requireCondition(await page.getByRole("checkbox", { name: "选择助手", exact: true }).isChecked()
        && await page.getByRole("checkbox", { name: "发起和继续对话", exact: true }).isChecked()
        && !await page.getByRole("checkbox", { name: "本人历史", exact: true }).isChecked(), "开户默认功能不符原型");
      await next(); await page.getByLabel(/^初始密码\*?$/).fill(generated.member.password); await page.reload({ waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "密码并创建", exact: true }).waitFor();
      requireCondition(await page.getByLabel(/^初始密码\*?$/).inputValue() === "", "刷新保留密码");
      const noSecret = await page.evaluate(secret => !JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }).includes(secret), generated.member.password);
      requireCondition(noSecret && writes === 0, "草稿持久化密码或提前写入");
      await page.getByLabel(/^初始密码\*?$/).fill(generated.member.password); await page.getByLabel(/^再次输入密码\*?$/).fill("mismatch");
      await page.getByRole("button", { name: "创建成员", exact: true }).click(); await page.getByRole("alert").waitFor();
      requireCondition(writes === 0 && await page.getByLabel(/^初始密码\*?$/).inputValue() === ""
        && await page.getByLabel(/^再次输入密码\*?$/).inputValue() === "", "错误确认仍写入或保留密码");
    });
    await check("正式界面原子开户后进入真实成员账号页", async () => {
      await page.getByLabel(/^初始密码\*?$/).fill(generated.member.password); await page.getByLabel(/^再次输入密码\*?$/).fill(generated.member.password);
      const sent = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/tenant/members" && response.request().method() === "POST");
      await page.getByRole("button", { name: "创建成员", exact: true }).click(); const response = await sent;
      requireCondition(response.status() === 201, "正式开户未成功"); const body = await response.json(); verifyAccount(body, generated.member.loginName);
      generated.member.membershipId = body.membershipId; persist();
      await page.getByRole("heading", { name: "成员账号", exact: true }).waitFor();
      requireCondition(writes === 1 && await page.locator('input[type="password"]').count() === 0
        && (await page.locator(".member-account-facts").innerText()).includes(generated.member.loginName), "成功未进入本人真实账号详情");
    });
    await check("重复登录名旧合同伪造角色及旧版本均拒绝", async () => {
      const options = (await request(page, "/tenant/members/create-options")).body;
      const command = { loginName: generated.member.loginName, password: generated.member.password,
        conversationFeatures: { assistantSelection: true, conversation: true, ownHistory: false, authorizedCitations: false },
        assistantIds: [], expectedTenantFeatureVersion: options.tenantFeatureVersion, expectedAuthorizationRevision: options.authorizationRevision };
      requireCondition((await keyedRequest(page, "/tenant/members", command, `duplicate-${suffix}`)).status === 409, "全局重复未拒绝");
      requireCondition((await keyedRequest(page, "/tenant/members", { ...command, membershipRole: "TENANT_ADMINISTRATOR" }, `forged-${suffix}`)).status === 400, "伪造角色字段未拒绝");
      requireCondition((await keyedRequest(page, "/tenant/members", { subjectId: "1" }, `legacy-${suffix}`)).status === 400, "旧主体关联接口仍可写入");
      requireCondition((await request(page, `/tenant/members/${generated.member.membershipId}/disable`, "POST", { expectedVersion: 999 })).status === 409, "陈旧资格版本未冲突");
    });
    let oldMember;
    await check("新成员本人登录无助手且直接API无管理或问答授权", async () => {
      oldMember = await memberLogin(browser, configuration, generated.member);
      const capability = await request(oldMember.page, "/auth/browser-session");
      requireCondition(capability.status === 200 && capability.body.displayRoles.includes("TENANT_USER")
        && !capability.body.capabilityCodes.includes("access:manage"), "普通成员获得管理员能力");
      const choices = await request(oldMember.page, "/query-assistants?pageNumber=1&pageSize=20");
      requireCondition(choices.status === 200 && choices.body.items.length === 0 && choices.body.totalElements === 0, "空助手范围变成全量目录");
      requireCondition((await request(oldMember.page, "/tenant/members")).status === 403, "普通成员可读取管理目录");
      requireCondition((await keyedRequest(oldMember.page, "/query-runs", { assistantVersionId: "1", question: "合成无授权访问" }, `denied-${suffix}`)).status === 403, "无助手成员可直接问答");
      requireCondition((await request(oldMember.page, "/platform/accounts")).status === 401, "普通成员取得平台范围");
    });
    await check("重置取消及错误确认零写入且正式重置撤销旧密码会话", async () => {
      let resetWrites = 0;
      page.on("request", sent => { if (sent.method() === "POST" && new URL(sent.url()).pathname.endsWith("/password-reset")) resetWrites += 1; });
      const row = await findTarget(); await row.getByRole("button", { name: "重置密码", exact: true }).click();
      let dialog = page.getByRole("dialog"); await dialog.getByLabel(/^新密码\*?$/).fill(generated.member.replacementPassword);
      await dialog.getByRole("button", { name: "取消", exact: true }).click();
      requireCondition((await request(oldMember.page, "/auth/browser-session")).status === 200, "取消重置撤销了旧会话");
      await row.getByRole("button", { name: "重置密码", exact: true }).click(); dialog = page.getByRole("dialog");
      requireCondition(await dialog.getByLabel(/^新密码\*?$/).inputValue() === "", "重新打开保留新密码");
      await dialog.getByLabel(/^新密码\*?$/).fill(generated.member.replacementPassword);
      await dialog.getByLabel(/^再次输入\*?$/).fill("mismatch");
      await dialog.getByRole("checkbox", { name: "撤销全部会话", exact: false }).check();
      await dialog.getByRole("button", { name: "重置密码", exact: true }).click(); await dialog.getByRole("alert").waitFor();
      requireCondition(resetWrites === 0 && await dialog.getByLabel(/^新密码\*?$/).inputValue() === ""
        && await dialog.getByLabel(/^再次输入\*?$/).inputValue() === "", "重置错误确认仍写入或恢复密码");
      requireCondition((await request(oldMember.page, "/auth/browser-session")).status === 200, "错误确认撤销了旧会话");
      await dialog.getByLabel(/^新密码\*?$/).fill(generated.member.replacementPassword);
      await dialog.getByLabel(/^再次输入\*?$/).fill(generated.member.replacementPassword);
      await dialog.getByRole("checkbox", { name: "撤销全部会话", exact: false }).check();
      const sent = page.waitForResponse(response => response.url().includes("/password-reset") && response.request().method() === "POST");
      await dialog.getByRole("button", { name: "重置密码", exact: true }).click(); requireCondition((await sent).status() === 204, "唯一归属重置未成功");
      await dialog.waitFor({ state: "hidden" }); requireCondition((await request(oldMember.page, "/auth/browser-session")).status === 401, "重置未撤销旧会话");
      const rejected = await memberLogin(browser, configuration, generated.member, false); await rejected.context.close();
    });
    let refreshed;
    await check("停用确认取消和恢复保持范围且不恢复旧会话", async () => {
      refreshed = await memberLogin(browser, configuration, { ...generated.member, password: generated.member.replacementPassword });
      let row = await findTarget(); await row.getByRole("button", { name: "停用", exact: true }).click();
      const dialog = page.getByRole("dialog"); await dialog.getByRole("button", { name: "取消", exact: true }).click();
      requireCondition((await request(refreshed.page, "/auth/browser-session")).status === 200, "取消停用仍撤销会话");
      await row.getByRole("button", { name: "停用", exact: true }).click();
      const sent = page.waitForResponse(response => response.url().includes("/disable") && response.request().method() === "POST");
      await dialog.getByRole("button", { name: "确认停用", exact: true }).click(); requireCondition((await sent).status() === 200, "资格停用未成功");
      await dialog.waitFor({ state: "hidden" }); requireCondition((await request(refreshed.page, "/auth/browser-session")).status === 401, "停用未撤销旧会话");
      row = await findTarget(); const enabling = page.waitForResponse(response => response.url().includes("/enable") && response.request().method() === "POST");
      await row.getByRole("button", { name: "恢复", exact: true }).click(); requireCondition((await enabling).status() === 200, "资格恢复未成功");
      requireCondition((await request(refreshed.page, "/auth/browser-session")).status === 401, "恢复重新激活了旧会话");
      const final = await request(page, `/tenant/members/${generated.member.membershipId}`);
      requireCondition(final.body.version === 2 && final.body.identityVersion === 1 && final.body.assistantIds.length === 0
        && final.body.conversationFeatures.conversation === true, "恢复覆盖功能授权或版本不正确");
      const accepted = await memberLogin(browser, configuration, { ...generated.member, password: generated.member.replacementPassword }); await accepted.context.close();
    });
    await check("三尺寸两倍文字键盘与reduced-motion不裁切", async () => {
      for (const width of [390, 1024, 1440]) {
        await page.setViewportSize({ width, height: 960 }); await directory();
        for (const enlarged of [false, true]) await withTextScale(page, enlarged, () => verifyLayout(page));
        let row = await findTarget(); await row.getByRole("button", { name: "重置密码", exact: true }).click();
        for (const enlarged of [false, true]) await withTextScale(page, enlarged, () => verifyLayout(page), '[role="dialog"]');
        const dialog = page.getByRole("dialog"); await dialog.getByLabel(/^新密码\*?$/).focus();
        await page.keyboard.press("Tab"); requireCondition(await dialog.getByLabel(/^再次输入\*?$/).evaluate(element => element === document.activeElement), "重置密码键盘顺序不正确");
        await page.keyboard.press("Escape"); await dialog.waitFor({ state: "hidden" });
        requireCondition(await row.getByRole("button", { name: "重置密码", exact: true }).evaluate(element => element === document.activeElement), "关闭重置未返回操作焦点");
        row = await findTarget(); await row.getByRole("button", { name: "停用", exact: true }).click();
        for (const enlarged of [false, true]) await withTextScale(page, enlarged, () => verifyLayout(page), '[role="dialog"]');
        await dialog.getByRole("button", { name: "取消", exact: true }).click();
        await row.getByRole("button", { name: "查看成员", exact: true }).click();
        await page.locator(".member-account-facts").waitFor();
        for (const enlarged of [false, true]) await withTextScale(page, enlarged, () => verifyLayout(page));
        await page.getByRole("button", { name: "返回成员目录", exact: true }).click();
        await page.locator(".member-directory-table tbody").waitFor();
        await create(); for (const enlarged of [false, true]) await withTextScale(page, enlarged, () => verifyLayout(page));
        const login = page.getByRole("textbox", { name: "登录名", exact: true }); await login.focus();
        await page.keyboard.press("Tab"); requireCondition(await page.getByRole("button", { name: "取消", exact: true }).evaluate(element => element === document.activeElement), "键盘跳过取消或进入隐藏字段");
        await page.keyboard.press("Shift+Tab"); requireCondition(await login.evaluate(element => element === document.activeElement), "键盘不能返回输入字段");
        await login.fill(`layout-${suffix}`); await next(); await page.getByText(/^匹配 [0-9]+ 个，已选 0 个$/).waitFor();
        for (const enlarged of [false, true]) await withTextScale(page, enlarged, () => verifyLayout(page));
        await next(); await page.getByRole("heading", { name: "密码并创建", exact: true }).waitFor();
        for (const enlarged of [false, true]) await withTextScale(page, enlarged, () => verifyLayout(page));
        await page.getByLabel(/^初始密码\*?$/).focus(); await page.keyboard.press("Tab");
        requireCondition(await page.getByLabel(/^再次输入密码\*?$/).evaluate(element => element === document.activeElement), "开户密码字段键盘顺序不正确");
        await page.getByRole("button", { name: "取消", exact: true }).click();
      }
    });
    requireCondition(errors === 0, "成员界面出现浏览器脚本错误");
    generated.status = "VERIFIED"; persist();
    process.stdout.write("WP-0018 / account 独立切片浏览器场景通过；独立功能、策略、其余页面及 M6 尚未完成。\n");
  } catch (error) {
    throw new Error(`成员账户验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；合成对象保留于独占临时文件，未输出秘密。`);
  } finally { fs.closeSync(descriptor); await browser?.close(); }
}
module.exports = { readConfiguration, verifyAccount };
if (require.main === module) {
  try { run(readConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
