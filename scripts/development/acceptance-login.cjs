/* WP-0017：只连接本机真实部署；不拦截 API、不保存截图、trace、cookie 或浏览器 profile。 */
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

class SafeAssertionError extends Error {}

function requireCondition(condition, message) {
  if (!condition) throw new SafeAssertionError(message);
}

/** 凭据必须来自当前用户独占文件；错误不拼接文件正文、输入值或原始异常。 */
function readConfiguration(environment = process.env) {
  const baseUrl = environment.FOUNDATION_ACCEPTANCE_BASE_URL || "http://127.0.0.1:13000";
  let url;
  try { url = new URL(baseUrl); } catch { throw new Error("验收目标只能是本机 13000"); }
  requireCondition(url.protocol === "http:" && ["127.0.0.1", "localhost"].includes(url.hostname)
    && url.port === "13000" && url.pathname === "/" && !url.search && !url.hash
    && !url.username && !url.password, "验收目标只能是本机 13000");
  requireCondition(!environment.SETUP || environment.SETUP === "tenant", "SETUP 只允许 tenant");
  const setup = environment.SETUP === "tenant";
  let descriptor;
  let credentials;
  try {
    descriptor = fs.openSync(environment.FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
    const stat = fs.fstatSync(descriptor);
    requireCondition(stat.isFile() && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o600
      && stat.size > 0 && stat.size <= 16_384, "凭据文件必须是当前用户拥有的普通 0600 文件");
    credentials = JSON.parse(fs.readFileSync(descriptor, "utf8"));
  } catch { throw new Error("凭据文件不可用：需要当前用户拥有的普通 0600 JSON 文件"); }
  finally { if (descriptor !== undefined) fs.closeSync(descriptor); }
  const validIdentity = value => value && typeof value.loginName === "string" && value.loginName.length > 0
    && value.loginName.length <= 128 && typeof value.password === "string" && value.password.length > 0 && value.password.length <= 256;
  requireCondition(validIdentity(credentials.platform), "平台管理员凭据不完整");
  if (!setup) requireCondition(validIdentity(credentials.tenant) && typeof credentials.tenant.tenantCode === "string"
    && /^[a-z][a-z0-9-]{2,62}$/.test(credentials.tenant.tenantCode), "租户管理员凭据不完整，不允许跳过租户验收");
  const outputFile = environment.FOUNDATION_ACCEPTANCE_OUTPUT_FILE;
  if (setup) requireCondition(typeof outputFile === "string" && path.isAbsolute(outputFile)
    && !fs.existsSync(outputFile), "显式 setup 必须指定尚不存在的新凭据输出文件");
  return { baseUrl: url.origin, credentials, setup, outputFile };
}

const loginPaths = { platform: "/login", tenant: "/tenant/login" };
const titles = { platform: "平台管理登录", tenant: "租户管理员登录" };
const roles = { platform: "PLATFORM_ADMINISTRATOR", tenant: "TENANT_ADMINISTRATOR" };

/** 只返回状态和安全业务响应；所有请求通过真实同源浏览器网络完成。 */
async function request(page, route, method = "GET", data, csrf = true) {
  return page.evaluate(async ({ route, method, data, csrf }) => {
    const headers = { Accept: "application/json" };
    if (data !== undefined) headers["Content-Type"] = "application/json";
    if (csrf && method !== "GET") {
      const value = document.cookie.split(";").map(item => item.trim()).find(item => item.startsWith("__Host-foundation-csrf="));
      if (value) headers["X-CSRF-Token"] = value.slice(value.indexOf("=") + 1);
    }
    const response = await fetch(`/api/v1${route}`, {
      method, headers, credentials: "same-origin", body: data === undefined ? undefined : JSON.stringify(data),
      signal: AbortSignal.timeout(10_000),
    });
    return { status: response.status, body: response.ok && response.headers.get("content-type")?.includes("application/json") ? await response.json() : undefined };
  }, { route, method, data, csrf });
}

async function assertIdentity(page, role) {
  const response = await request(page, "/auth/browser-session");
  requireCondition(response.status === 200 && response.body?.displayRoles?.includes(roles[role]), "真实会话未恢复目标管理员角色");
  requireCondition(!response.body.displayRoles.includes(roles[role === "platform" ? "tenant" : "platform"]), "会话意外包含另一管理范围");
}

async function assertAnonymous(page) {
  requireCondition((await request(page, "/auth/browser-session")).status === 401, "退出或拒绝后仍存在可用会话");
}

async function assertWorkspace(page, role) {
  const platform = role === "platform";
  await page.waitForURL(url => url.pathname === (platform ? "/platform/operations" : "/operations")
    && url.searchParams.get("range") === "DAY_7" && (!platform || url.searchParams.get("page") === "1"));
  // 使用稳定的页头标识，避免与内容区的资源标题混淆。
  const pageTitle = page.locator("#page-title");
  await pageTitle.waitFor();
  requireCondition(await pageTitle.innerText() === (platform ? "平台运营总览" : "运营总览"), "登录后未进入预期工作区");
  await page.getByText(platform ? "平台资源健康" : "当前租户资源", { exact: true }).waitFor();
  requireCondition(await page.locator('input[type="password"]').count() === 0, "登录后仍停留在凭据表单");
}

async function logoutWorkspace(page) {
  await page.getByRole("button", { name: /账户菜单/ }).click();
  await page.getByRole("menuitem", { name: "退出登录", exact: true }).click();
}

async function openLogin(page, configuration, role) {
  await page.goto(`${configuration.baseUrl}${loginPaths[role]}`, { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: titles[role], exact: true }).waitFor();
  requireCondition(await page.getByRole("tab").count() === 0, "独立入口仍使用旧登录页签");
  requireCondition(await page.getByLabel("租户编码", { exact: true }).count() === (role === "tenant" ? 1 : 0), "登录表单范围不正确");
}

async function fillLogin(page, credentials, targetRole) {
  if (targetRole === "tenant") await page.getByLabel("租户编码", { exact: true }).fill(credentials.tenantCode);
  await page.getByLabel("登录名", { exact: true }).fill(credentials.loginName);
  await page.getByLabel("密码", { exact: true }).fill(credentials.password);
}

async function submitLogin(page) {
  const sent = page.waitForResponse(response => response.url().endsWith("/api/v1/auth/browser-sessions") && response.request().method() === "POST");
  await page.getByRole("button", { name: "登录", exact: true }).press("Enter");
  await sent;
}

async function assertLayout(page, enlarged = false) {
  if (enlarged) await page.evaluate(() => {
    // 固定原字号后统一翻倍，避免父子节点依次修改造成指数放大；不以缩放视口替代文字放大。
    const elements = [...document.querySelectorAll("h1,h2,p,label,a,span,input,button")];
    const sizes = elements.map(element => parseFloat(getComputedStyle(element).fontSize));
    elements.forEach((element, index) => { element.style.fontSize = `${sizes[index] * 2}px`; });
  });
  const layout = await page.evaluate(() => {
    const visible = element => element.getBoundingClientRect().width > 0 && element.getBoundingClientRect().height > 0;
    const animationDisabled = element => {
      const style = getComputedStyle(element);
      return style.animationName === "none" || style.animationDuration.split(",").every(value => parseFloat(value) <= 0.001);
    };
    return {
      overflow: document.documentElement.scrollWidth > innerWidth + 1,
      clipped: [...document.querySelectorAll("button,h1,p")].filter(visible).some(element => element.clientHeight > 0 && element.scrollHeight > element.clientHeight + 1),
      controls: [...document.querySelectorAll("input,button")].filter(visible).every(element => (element.closest(".fui-Input") || element).getBoundingClientRect().height >= 44),
      reducedMotion: matchMedia("(prefers-reduced-motion: reduce)").matches,
      animationDisabled: [...document.body.querySelectorAll("*")].filter(visible).every(animationDisabled),
      moving: document.getAnimations().some(animation => animation.playState === "running"
        && animation.effect?.getTiming().duration > 1),
    };
  });
  requireCondition(!layout.overflow, "登录页新增横向溢出");
  requireCondition(!layout.clipped, "登录页文字被裁切");
  requireCondition(layout.controls, "登录控件点击区域小于 44px");
  requireCondition(!layout.reducedMotion || (!layout.moving && layout.animationDisabled), "reduced-motion 下装饰动画名称或时长未禁用");
  await page.getByRole("button", { name: "登录", exact: true }).scrollIntoViewIfNeeded();
}

async function assertKeyboard(page, role) {
  const names = role === "tenant" ? ["租户编码", "登录名", "密码"] : ["登录名", "密码"];
  await page.getByLabel(names[0], { exact: true }).focus();
  for (const name of names.slice(1)) {
    await page.keyboard.press("Tab");
    requireCondition(await page.getByLabel(name, { exact: true }).evaluate(element => element === document.activeElement), "登录表单键盘焦点顺序不正确");
  }
}

/** setup 只准备合成验收对象，不重置、删除或恢复任何既有数据；部分成功也保留 ID 供人工处理。 */
async function prepareTenant(page, configuration) {
  const suffix = crypto.randomBytes(6).toString("hex");
  const credentials = { platform: configuration.credentials.platform, tenant: {
    tenantCode: `wp17-${suffix}`, loginName: `wp17-${suffix}`, password: `Aa9!${crypto.randomBytes(24).toString("base64url")}`,
  }, setup: { status: "STARTED" } };
  const descriptor = fs.openSync(configuration.outputFile, "wx", 0o600);
  const persist = () => {
    fs.ftruncateSync(descriptor, 0);
    fs.writeSync(descriptor, JSON.stringify(credentials, null, 2), 0, "utf8");
    fs.fsyncSync(descriptor);
  };
  persist();
  let authenticated = false;
  let failed = false;
  try {
    await page.goto(configuration.baseUrl, { waitUntil: "networkidle" });
    requireCondition((await request(page, "/auth/browser-csrf")).status === 204, "无法取得 setup CSRF 防护");
    requireCondition((await request(page, "/auth/browser-sessions", "POST", credentials.platform)).status === 204, "setup 平台登录失败");
    authenticated = true;
    await assertIdentity(page, "platform");
    const tenant = await request(page, "/platform/tenants", "POST", {
      tenantCode: credentials.tenant.tenantCode, displayName: "WP-0017 登录切片验收租户",
      administrator: { loginName: credentials.tenant.loginName, password: credentials.tenant.password },
      quotas: { knowledgeBases: 3, documentsPerKnowledgeBase: 1000, bytesPerKnowledgeBase: "10000000000",
        maxFileBytes: "50000000", maxTextBytes: "1048576" },
    });
    requireCondition(tenant.status === 201 && typeof tenant.body?.tenantId === "string"
      && /^[1-9][0-9]*$/.test(tenant.body.tenantId) && tenant.body.status === "ACTIVE"
      && typeof tenant.body.initialAdministratorSubjectId === "string", "setup 原子建立合成租户失败");
    credentials.setup.tenantId = tenant.body.tenantId;
    credentials.setup.subjectId = tenant.body.initialAdministratorSubjectId;
    credentials.setup.status = "ACTIVE";
    persist();
    process.stdout.write(`${JSON.stringify({ preparation: "合成租户准备完成，尚未执行浏览器切片验收", ...credentials.setup })}\n`);
  } catch (error) {
    failed = true;
    throw error;
  } finally {
    let logoutFailed = false;
    if (authenticated) {
      try { logoutFailed = (await request(page, "/auth/browser-session", "DELETE")).status !== 204; }
      catch { logoutFailed = true; }
    }
    fs.closeSync(descriptor);
    process.stdout.write(`${JSON.stringify({ preparationStatus: credentials.setup.status, subjectId: credentials.setup.subjectId, tenantId: credentials.setup.tenantId })}\n`);
    if (!failed) requireCondition(!logoutFailed, "setup 对象已保留，但平台会话退出失败");
  }
}

async function run(configuration) {
  let browser;
  let stage = "启动现有 Chrome 与锁定 Playwright 运行时";
  async function scenario(name, action, width = 1440, reducedMotion = "reduce") {
    stage = name;
    const start = Date.now();
    const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 960 }, reducedMotion });
    const page = await context.newPage();
    page.setDefaultTimeout(12_000);
    let pageErrors = 0;
    page.on("pageerror", () => { pageErrors += 1; });
    try {
      await action(page);
      requireCondition(pageErrors === 0, "页面发生未处理异常");
      // 读取真实导航计时，不输出带查询参数的 URL，也不把步骤总耗时包装成性能 SLA。
      const navigationProfile = await page.evaluate(() => {
        const navigation = performance.getEntriesByType("navigation")[0];
        return navigation ? { domContentLoadedMs: navigation.domContentLoadedEventEnd,
          loadMs: navigation.loadEventEnd, transferBytes: navigation.transferSize } : null;
      });
      process.stdout.write(`${JSON.stringify({ assertion: name, result: "通过", elapsedMs: Date.now() - start, navigationProfile })}\n`);
    } finally { await context.close(); }
  }
  try {
    requireCondition(require("playwright/package.json").version === "1.62.1", "需要现有锁定 Playwright 1.62.1 运行时");
    const { chromium } = require("playwright");
    browser = await chromium.launch({ channel: "chrome", headless: true });
    if (configuration.setup) {
      await scenario("显式准备唯一合成租户", page => prepareTenant(page, configuration));
      process.stdout.write("仅准备验收数据；请用输出凭据文件重新运行 SLICE=login，未执行登录切片验收。\n");
      return;
    }
    await scenario("两个独立入口互相可达", async page => {
      await openLogin(page, configuration, "platform");
      await page.locator('a[href="/tenant/login"]').click();
      await page.getByRole("heading", { name: titles.tenant, exact: true }).waitFor();
      await page.locator('a[href="/login"]').click();
      await page.getByRole("heading", { name: titles.platform, exact: true }).waitFor();
    });
    for (const role of ["platform", "tenant"]) {
      for (const width of [390, 1024, 1440]) {
        await scenario(`${role} ${width} 布局、200%文字、键盘`, async page => {
          await openLogin(page, configuration, role);
          await assertLayout(page);
          await assertKeyboard(page, role);
          await assertLayout(page, true);
        }, width);
      }
      for (const reducedMotion of ["reduce", "no-preference"]) {
        await scenario(`${role} ${reducedMotion} 登录、刷新、CSRF、退出`, async page => {
          await openLogin(page, configuration, role);
          await fillLogin(page, configuration.credentials[role], role);
          await submitLogin(page);
          await assertWorkspace(page, role);
          await assertIdentity(page, role);
          await page.reload({ waitUntil: "networkidle" });
          await assertWorkspace(page, role);
          await assertIdentity(page, role);
          requireCondition((await request(page, "/auth/browser-session", "DELETE", undefined, false)).status === 403, "缺少 CSRF 的写入未被拒绝");
          await assertIdentity(page, role);
          await logoutWorkspace(page);
          await page.getByRole("heading", { name: titles[role], exact: true }).waitFor();
          await assertAnonymous(page);
        }, 1440, reducedMotion);
      }
      const other = role === "platform" ? "tenant" : "platform";
      await scenario(`${role} 已有身份访问错误入口保持会话并可退出切换`, async page => {
        await openLogin(page, configuration, role);
        await fillLogin(page, configuration.credentials[role], role);
        await submitLogin(page);
        await assertWorkspace(page, role);
        await page.goto(`${configuration.baseUrl}${loginPaths[other]}`, { waitUntil: "networkidle" });
        await page.getByText("当前登录身份与入口不匹配", { exact: true }).waitFor();
        await assertIdentity(page, role);
        await page.getByRole("link", { name: "返回当前工作区", exact: true }).click();
        await assertWorkspace(page, role);
        await assertIdentity(page, role);
        await page.goto(`${configuration.baseUrl}${loginPaths[other]}`, { waitUntil: "networkidle" });
        await page.getByRole("button", { name: "退出并切换账户", exact: true }).click();
        await page.getByRole("heading", { name: titles[other], exact: true }).waitFor();
        await assertAnonymous(page);
      });
      await scenario(`${role} 账户不能从错误入口建立新会话`, async page => {
        await openLogin(page, configuration, other);
        await fillLogin(page, { ...configuration.credentials[role], tenantCode: configuration.credentials.tenant.tenantCode }, other);
        await submitLogin(page);
        await page.getByText("登录未成功", { exact: true }).waitFor();
        await assertAnonymous(page);
      });
    }
    process.stdout.write("WP-0017 / login 切片真实部署验收通过；不代表 WP-0017 整包完成。\n");
  } catch (error) {
    // Playwright 原始异常可能包含输入值和网络参数，只披露预先定义的断言阶段。
    const detail = error instanceof SafeAssertionError ? `；${error.message}` : "";
    throw new Error(`真实部署验收失败：${stage}${detail}；未保存截图、trace 或会话材料。`);
  } finally {
    try { await browser?.close(); }
    catch { throw new Error("验收浏览器关闭失败；未输出浏览器诊断或会话材料。"); }
  }
}

module.exports = { readConfiguration, assertWorkspace, logoutWorkspace, request, requireCondition,
  SafeAssertionError, assertIdentity, assertAnonymous, openLogin, fillLogin, submitLogin };
if (require.main === module) {
  try {
    const configuration = readConfiguration();
    run(configuration).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; });
  } catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
