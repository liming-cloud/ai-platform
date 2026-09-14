/* WP-0017：原子开户只通过本机生产 UI/API 验证，不拦截网络、不重置数据、不输出秘密。 */
const fs = require("node:fs");
const crypto = require("node:crypto");
const { readConfiguration, request, requireCondition, SafeAssertionError, openLogin, fillLogin,
  submitLogin, assertWorkspace, assertIdentity, assertAnonymous } = require("./acceptance-login.cjs");

function readOnboardingConfiguration(environment = process.env) {
  requireCondition(!environment.SETUP, "开户验收不接受 SETUP，必须执行完整 UI 闭环");
  return readConfiguration({ ...environment, SETUP: "tenant" });
}

/** 使用非默认配额，避免后端忽略表单后仍被误判为成功。 */
function onboardingRequest(identity) {
  return { tenantCode: identity.tenantCode, displayName: "WP-0017 原子开户验收租户",
    administrator: { loginName: identity.loginName, password: identity.password },
    quotas: { knowledgeBases: 4, documentsPerKnowledgeBase: 1234, bytesPerKnowledgeBase: "12500000000",
      maxFileBytes: "75000000", maxTextBytes: "2097152" } };
}

function verifyOnboardingResult(tenant, quotas) {
  const reference = value => typeof value === "string" && /^[1-9][0-9]*$/.test(value);
  requireCondition(tenant.status === 201 && tenant.body?.status === "ACTIVE"
    && reference(tenant.body.tenantId) && reference(tenant.body.initialAdministratorSubjectId), "开户未原子产生可用租户和管理员");
  requireCondition(quotas.status === 200 && quotas.body?.version === 0, "配额无法真实读取或初始版本不正确");
  for (const [key, expected] of Object.entries(onboardingRequest({}).quotas)) {
    requireCondition(quotas.body[key] === expected, "非默认开户配额未精确保存");
  }
}

async function fillInformation(page, identity) {
  await page.getByLabel("组织名称", { exact: true }).fill("WP-0017 原子开户验收租户");
  await page.getByLabel("租户编码", { exact: true }).fill(identity.tenantCode);
}
async function fillQuotas(page) {
  for (const [name, value] of [["知识库数量", "4"], ["每库文档数量", "1234"], ["每库内容容量", "12.5"], ["单文件大小", "75"], ["粘贴文本大小", "2"]]) {
    await page.getByLabel(name, { exact: true }).fill(value);
  }
}
async function fillAdministrator(page, identity, matching = true) {
  await page.getByLabel("管理员登录名", { exact: true }).fill(identity.loginName);
  await page.getByLabel("初始密码", { exact: true }).fill(identity.password);
  await page.getByLabel("再次输入", { exact: true }).fill(matching ? identity.password : "SyntheticWrong9!");
  await page.getByRole("checkbox", { name: "确认创建租户及其管理员账号", exact: true }).check();
}
async function assertNoStoredSecret(page, secret) {
  requireCondition(await page.evaluate(secret => ![location.href, JSON.stringify(history.state),
    JSON.stringify(localStorage), JSON.stringify(sessionStorage)].some(value => value.includes(secret)), secret), "密码进入 URL 或浏览器草稿存储");
}
async function assertWizardLayout(page, width, enlarged = false) {
  if (enlarged) await page.evaluate(() => {
    const elements = [...document.querySelectorAll("main h1,main h2,main p,main label,main a,main span,main input,main button")];
    const sizes = elements.map(element => parseFloat(getComputedStyle(element).fontSize));
    elements.forEach((element, index) => { element.style.fontSize = `${sizes[index] * 2}px`; });
  });
  const result = await page.evaluate(() => {
    const visible = element => element.getBoundingClientRect().width > 0 && element.getBoundingClientRect().height > 0;
    const controls = [...document.querySelectorAll('.tenant-onboarding input:not([type="checkbox"]),.tenant-onboarding button')].filter(visible);
    const rows = new Map();
    for (const field of document.querySelectorAll(".tenant-onboarding__fields .fui-Field")) {
      const control = field.querySelector(".fui-Input");
      if (!control || !visible(control)) continue;
      const row = Math.round(field.getBoundingClientRect().top);
      rows.set(row, [...(rows.get(row) || []), control.getBoundingClientRect()]);
    }
    return { overflow: document.documentElement.scrollWidth > innerWidth + 1,
      minimumHeight: Math.min(...controls.map(element => (element.closest(".fui-Input") || element).getBoundingClientRect().height)),
      aligned: [...rows.values()].every(items => Math.max(...items.map(rect => rect.top)) - Math.min(...items.map(rect => rect.top)) <= 1
        && Math.max(...items.map(rect => rect.bottom)) - Math.min(...items.map(rect => rect.bottom)) <= 1),
      clipped: [...document.querySelectorAll("main button,main h1,main h2")].filter(visible)
        .some(element => element.scrollHeight > element.clientHeight + 1),
      moving: matchMedia("(prefers-reduced-motion: reduce)").matches && document.getAnimations()
        .some(animation => animation.playState === "running" && animation.effect?.getTiming().duration > 1) };
  });
  requireCondition(!result.overflow && !result.clipped, "开户页面溢出或文字裁切");
  requireCondition(result.minimumHeight >= (width === 390 ? 44 : 36), "开户控件点击区域不足");
  requireCondition(result.aligned, "同行表单控件上下边界误差超过 1px");
  requireCondition(!result.moving, "开户页 reduced-motion 未生效");
}

async function run(configuration) {
  let browser;
  let stage = "启动受控浏览器";
  let authenticated = false;
  let page;
  let descriptor;
  const suffix = crypto.randomBytes(6).toString("hex");
  const credentials = { platform: configuration.credentials.platform,
    tenant: { tenantCode: `wp17-${suffix}`, loginName: `wp17-${suffix}`, password: `Aa9!${crypto.randomBytes(24).toString("base64url")}` },
    setup: { status: "STARTED" } };
  const persist = () => { fs.ftruncateSync(descriptor, 0); fs.writeSync(descriptor, JSON.stringify(credentials, null, 2), 0, "utf8"); fs.fsyncSync(descriptor); };
  async function scenario(name, action) {
    stage = name;
    const started = Date.now();
    await action();
    const navigationProfile = await page.evaluate(() => {
      const navigation = performance.getEntriesByType("navigation")[0];
      return navigation ? { domContentLoadedMs: navigation.domContentLoadedEventEnd,
        loadMs: navigation.loadEventEnd, transferBytes: navigation.transferSize } : null;
    });
    process.stdout.write(`${JSON.stringify({ assertion: name, result: "通过", elapsedMs: Date.now() - started, navigationProfile })}\n`);
  }
  try {
    requireCondition(require("playwright/package.json").version === "1.62.1", "需要现有锁定 Playwright 1.62.1 运行时");
    descriptor = fs.openSync(configuration.outputFile, "wx", 0o600);
    persist();
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
    page = await context.newPage();
    page.setDefaultTimeout(15_000);
    let pageErrors = 0;
    page.on("pageerror", () => { pageErrors += 1; });
    await scenario("真实平台身份进入开户", async () => {
      await openLogin(page, configuration, "platform");
      await fillLogin(page, credentials.platform, "platform");
      await submitLogin(page);
      await assertWorkspace(page, "platform");
      await assertIdentity(page, "platform");
      authenticated = true;
    });
    for (const width of [390, 1024, 1440]) {
      await scenario(`${width} 三步、回退、刷新、密码清除与取消`, async () => {
        await page.setViewportSize({ width, height: width === 390 ? 844 : 960 });
        await page.goto(`${configuration.baseUrl}/platform/tenants/new?step=1`, { waitUntil: "networkidle" });
        let creates = 0;
        const countCreate = outgoing => { if (outgoing.method() === "POST" && /\/api\/v1\/platform\/(tenants|accounts)$/.test(outgoing.url())) creates += 1; };
        page.on("request", countCreate);
        try {
          await fillInformation(page, credentials.tenant);
          await assertWizardLayout(page, width);
          await page.getByRole("button", { name: "下一步", exact: true }).press("Enter");
          await fillQuotas(page);
          await assertWizardLayout(page, width);
          await page.getByRole("button", { name: "下一步", exact: true }).click();
          await fillAdministrator(page, credentials.tenant);
          await assertNoStoredSecret(page, credentials.tenant.password);
          await page.reload({ waitUntil: "networkidle" });
          requireCondition(await page.getByLabel("初始密码", { exact: true }).inputValue() === "", "刷新后仍保留密码");
          await page.getByRole("button", { name: "上一步", exact: true }).click();
          requireCondition(await page.getByLabel("每库内容容量", { exact: true }).inputValue() === "12.5", "刷新后非秘密配额草稿丢失");
          await page.getByRole("button", { name: "上一步", exact: true }).click();
          requireCondition(await page.getByLabel("租户编码", { exact: true }).inputValue() === credentials.tenant.tenantCode, "回退后租户信息丢失");
          await assertWizardLayout(page, width, true);
          await page.getByRole("button", { name: "取消", exact: true }).click();
          requireCondition(creates === 0, "进入步骤或取消时提前创建了账户或租户");
        } finally { page.off("request", countCreate); }
      });
    }
    await page.setViewportSize({ width: 1440, height: 960 });
    await scenario("一次提交原子开户并刷新读取非默认配额", async () => {
      await page.goto(`${configuration.baseUrl}/platform/tenants/new?step=1`, { waitUntil: "networkidle" });
      requireCondition(await page.getByLabel("组织名称", { exact: true }).inputValue() === "", "取消未清除开户草稿");
      await fillInformation(page, credentials.tenant);
      await page.getByRole("button", { name: "下一步", exact: true }).click();
      await fillQuotas(page);
      await page.getByRole("button", { name: "下一步", exact: true }).click();
      await fillAdministrator(page, credentials.tenant, false);
      let creates = 0;
      const countCreate = outgoing => { if (outgoing.method() === "POST" && /\/api\/v1\/platform\/(tenants|accounts)$/.test(outgoing.url())) creates += 1; };
      page.on("request", countCreate);
      try {
        const create = page.getByRole("button", { name: "创建租户", exact: true });
        if (await create.isEnabled()) await create.click();
        requireCondition(creates === 0, "密码不一致仍发出开户请求");
        await fillAdministrator(page, credentials.tenant);
        const responsePromise = page.waitForResponse(response => /\/api\/v1\/platform\/tenants$/.test(response.url()) && response.request().method() === "POST");
        await create.click();
        const response = await responsePromise;
        requireCondition(response.status() === 201, "原子开户接口未返回 201");
        const body = await response.json();
        credentials.setup = { status: "CREATED", tenantId: body.tenantId, subjectId: body.initialAdministratorSubjectId };
        persist();
        requireCondition(!JSON.stringify(body).includes(credentials.tenant.password) && !/password|hash|token/i.test(JSON.stringify(body)), "开户响应包含秘密字段");
        await page.waitForURL(url => url.pathname === `/platform/tenants/${body.tenantId}`);
        await page.getByRole("heading", { name: "租户概览", exact: true }).waitFor();
        await page.reload({ waitUntil: "networkidle" });
        await page.getByRole("heading", { name: "租户概览", exact: true }).waitFor();
        await page.getByRole("heading", { name: onboardingRequest(credentials.tenant).displayName, exact: true }).waitFor();
        requireCondition(await page.locator(".tenant-overview__identity p").textContent() === credentials.tenant.tenantCode, "刷新详情未显示当前租户编码");
        for (const [label, expected] of [["知识库数量", "4 个"], ["每库文档数量", "1234 个"], ["每库内容容量", "12.5 GB"], ["单文件大小", "75 MB"], ["粘贴文本大小", "2 MiB"]]) {
          const row = page.locator(".tenant-overview__facts > div").filter({ has: page.locator("dt", { hasText: label }) });
          requireCondition((await row.locator("dd").textContent())?.trim() === expected, `刷新详情未显示实际${label}`);
        }
        const quotas = await request(page, `/platform/tenants/${body.tenantId}/quotas`);
        verifyOnboardingResult({ status: response.status(), body }, quotas);
        requireCondition(creates === 1, "开户没有保持单次提交边界");
        await assertNoStoredSecret(page, credentials.tenant.password);
        credentials.setup.status = "ACTIVE";
        persist();
      } finally { page.off("request", countCreate); }
    });
    await scenario("重复编码、重复登录名、旧请求和缺少 CSRF 失败关闭", async () => {
      const input = onboardingRequest(credentials.tenant);
      requireCondition((await request(page, "/platform/tenants", "POST", { ...input, administrator: { ...input.administrator, loginName: `unused-${suffix}` } })).status === 409, "重复租户编码未拒绝");
      requireCondition((await request(page, "/platform/tenants", "POST", { ...input, tenantCode: `unused-${suffix}` })).status === 409, "重复全局登录名未拒绝");
      requireCondition((await request(page, "/platform/tenants", "POST", { tenantCode: `old-${suffix}`, displayName: "旧合同反例", initialAdministratorSubjectId: credentials.setup.subjectId })).status === 400, "旧候选主体请求仍可使用");
      requireCondition((await request(page, "/platform/tenants", "POST", input, false)).status === 403, "缺少 CSRF 仍可开户");
    });
    await scenario("重复编码错误返回信息步骤并定位字段，取消清除失败草稿", async () => {
      await page.goto(`${configuration.baseUrl}/platform/tenants/new?step=1`, { waitUntil: "networkidle" });
      await fillInformation(page, credentials.tenant);
      await page.getByRole("button", { name: "下一步", exact: true }).click();
      await fillQuotas(page);
      await page.getByRole("button", { name: "下一步", exact: true }).click();
      await fillAdministrator(page, { ...credentials.tenant, loginName: `unused-${suffix}` });
      const pending = page.waitForResponse(response => /\/api\/v1\/platform\/tenants$/.test(response.url()) && response.request().method() === "POST");
      await page.getByRole("button", { name: "创建租户", exact: true }).click();
      requireCondition((await pending).status() === 409, "重复编码 UI 提交未返回冲突");
      const field = page.getByLabel("租户编码", { exact: true });
      await field.waitFor();
      requireCondition(await field.inputValue() === credentials.tenant.tenantCode, "失败丢失非秘密输入");
      requireCondition(await field.evaluate(element => element === document.activeElement), "重复编码错误未定位可修正输入");
      requireCondition(await page.locator('input[type="password"]').count() === 0, "失败回跳仍保留密码字段");
      await assertNoStoredSecret(page, credentials.tenant.password);
      await page.getByRole("button", { name: "取消", exact: true }).click();
      await page.goto(`${configuration.baseUrl}/platform/tenants/${credentials.setup.tenantId}`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "租户概览", exact: true }).waitFor();
    });
    await scenario("成功入口不切换身份，新管理员从租户入口登录且无平台权限", async () => {
      const link = page.getByRole("link", { name: "租户管理员登录入口", exact: true });
      requireCondition((await link.getAttribute("href")) === `/tenant/login?tenantCode=${credentials.tenant.tenantCode}`, "租户登录入口未携带当前编码");
      await assertIdentity(page, "platform");
      requireCondition((await request(page, "/auth/browser-session", "DELETE")).status === 204, "平台退出失败");
      authenticated = false;
      await assertAnonymous(page);
      await openLogin(page, configuration, "tenant");
      await fillLogin(page, credentials.tenant, "tenant");
      await submitLogin(page);
      await assertWorkspace(page, "tenant");
      await assertIdentity(page, "tenant");
      authenticated = true;
      requireCondition([401,403].includes((await request(page, "/platform/tenants", "POST", onboardingRequest(credentials.tenant))).status), "新租户管理员能够调用平台开户");
      requireCondition([401,403,404].includes((await request(page, `/platform/tenants/${credentials.setup.tenantId}/quotas`)).status), "新管理员能够读取平台租户配额接口");
      await assertIdentity(page, "tenant");
      requireCondition(pageErrors === 0, "真实页面出现未处理异常");
    });
    process.stdout.write(`${JSON.stringify({ slice: "WP-0017/onboarding", result: "通过", ...credentials.setup, retained: "仅保留本次合成租户和管理员，不创建普通会员" })}\n`);
  } catch (error) {
    throw new Error(`开户验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；已保留测试对象记录，未输出秘密。`);
  } finally {
    if (descriptor !== undefined) fs.closeSync(descriptor);
    let logoutFailed = false;
    if (authenticated && page) {
      try { logoutFailed = (await request(page, "/auth/browser-session", "DELETE")).status !== 204; } catch { logoutFailed = true; }
    }
    try { await browser?.close(); } catch { logoutFailed = true; }
    requireCondition(!logoutFailed, "验收结束后的会话或浏览器清理失败");
  }
}

module.exports = { readOnboardingConfiguration, onboardingRequest, verifyOnboardingResult };
if (require.main === module) {
  try { run(readOnboardingConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
