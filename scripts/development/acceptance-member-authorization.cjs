/* WP-0018 助手／策略切片：正式本机部署，空发布依赖如实拒绝；规模正例由隔离门禁验证。 */
const fs = require("node:fs");
const crypto = require("node:crypto");
const { readConfiguration: loginConfiguration, request, requireCondition, SafeAssertionError,
  openLogin, fillLogin, submitLogin, assertWorkspace, logoutWorkspace } = require("./acceptance-login.cjs");
const { onboardingRequest, withTextScale } = require("./acceptance-onboarding.cjs");
const { freshOutputFile, keyedRequest, memberLogin, verifyLayout } = require("./acceptance-member-account.cjs");
const id = value => typeof value === "string" && /^[1-9][0-9]{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;
const count = value => Number.isSafeInteger(value) && value >= 0;

function readConfiguration(environment = process.env) {
  requireCondition(!environment.SETUP, "助手切片只创建本轮独占对象，不接受外部 SETUP");
  return { ...loginConfiguration(environment), outputFile: freshOutputFile(environment) };
}
function verifyAssistants(body) {
  const keys = ["membershipId", "loginName", "status", "readOnly", "assistantIds", "authorizationRevision"];
  requireCondition(body && Object.keys(body).length === keys.length && keys.every(key => Object.hasOwn(body, key)), "助手响应不是最小安全投影");
  requireCondition(id(body.membershipId) && typeof body.loginName === "string" && ["ENABLED", "DISABLED"].includes(body.status)
    && typeof body.readOnly === "boolean" && count(body.authorizationRevision) && Array.isArray(body.assistantIds)
    && body.assistantIds.every(id) && new Set(body.assistantIds).size === body.assistantIds.length, "助手响应的标识集合或版本无效");
}
/** 预览必须包含四项实际检查及可加总计数；不能以固定通过文案代替事实。 */
function verifyPreview(body) {
  const codes = ["assistantSelection", "conversation", "ownHistory", "authorizedCitations"];
  requireCondition(body?.passed === true && count(body.allowCount) && count(body.denyCount) && count(body.leakCount) && body.leakCount === 0
    && Array.isArray(body.checks) && body.checks.length === 4 && new Set(body.checks.map(check => check.feature)).size === 4
    && body.checks.every(check => codes.includes(check.feature) && count(check.allowCount) && count(check.denyCount))
    && body.allowCount === body.checks.reduce((total, check) => total + check.allowCount, 0)
    && body.denyCount === body.checks.reduce((total, check) => total + check.denyCount, 0), "预览缺少真实检查、计数不一致或存在越权泄漏");
}

/** 原型目录初始态保持 24px 区块间距和真实不可创建时的禁用样式。 */
async function verifyDirectorySpacing(page) {
  const create = page.getByRole("button", { name: "新建访问策略", exact: true });
  if (await create.isDisabled()) {
    const style = await create.evaluate(element => {
      const value = getComputedStyle(element);
      return { color: value.color, background: value.backgroundColor, border: value.borderColor, opacity: value.opacity };
    });
    requireCondition(style.color === "rgb(94, 93, 89)" && style.background === "rgb(245, 244, 237)"
      && style.border === "rgb(232, 230, 220)" && style.opacity === "0.76", "策略目录不可创建状态未使用原型禁用样式");
  }
  const gap = await page.evaluate(() => document.querySelector(".policy-directory-table").getBoundingClientRect().top
    - document.querySelector(".policy-directory-toolbar").getBoundingClientRect().bottom);
  requireCondition(gap === 24, "策略目录筛选区与表格间距不是原型的24px");
}

/** 对象导航承接原型 flex 按钮，避免放大文字时原生块行框产生累积偏移。 */
async function verifyAssistantNavigation(page) {
  const styles = await page.locator(".member-object-navigation button").evaluateAll(elements => elements.map(element => {
    const value = getComputedStyle(element);
    return { display: value.display, align: value.alignItems, gap: value.gap };
  }));
  requireCondition(styles.length === 3 && styles.every(style => style.display === "flex" && style.align === "center" && style.gap === "8px"),
    "助手页对象导航没有承接原型flex按钮行框");
}

async function run(configuration) {
  requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定的 Playwright 运行时");
  const suffix = crypto.randomBytes(6).toString("hex"); const password = () => `Aa9!${crypto.randomBytes(24).toString("base64url")}`;
  const generated = { status: "STARTED", platform: configuration.credentials.platform, suffix,
    tenant: { tenantCode: `wp18a-${suffix}`, loginName: `wp18aa-${suffix}`, password: password() },
    member: { loginName: `wp18am-${suffix}`, password: password() } };
  const descriptor = fs.openSync(configuration.outputFile, "wx", 0o600);
  const persist = () => { fs.ftruncateSync(descriptor, 0); fs.writeSync(descriptor, JSON.stringify(generated), 0, "utf8"); fs.fsyncSync(descriptor); }; persist();
  let browser; let stage = "启动浏览器"; let errors = 0;
  const check = async (name, action) => { stage = name; await action(); process.stdout.write(`${JSON.stringify({ assertion: name, result: "通过" })}\n`); };
  try {
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
    const page = await context.newPage(); page.setDefaultTimeout(15_000); page.on("pageerror", () => errors++);
    const platform = await browser.newPage({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
    const configurationForMember = { ...configuration, credentials: generated };
    const route = () => `/tenant/members/${generated.member.membershipId}/assistants`;
    const direct = async () => { await page.goto(configuration.baseUrl + route(), { waitUntil: "networkidle" }); await page.getByRole("heading", { name: generated.member.loginName, exact: true }).waitFor(); };
    const read = async () => { const value = await request(page, route()); requireCondition(value.status === 200, "助手配置读取失败"); verifyAssistants(value.body); return value.body; };
    const login = async () => { await openLogin(page, configurationForMember, "tenant"); await fillLogin(page, generated.tenant, "tenant"); await submitLogin(page); await assertWorkspace(page, "tenant"); };
    await check("正式接口建立本轮租户与普通成员，不改动历史对象", async () => {
      await openLogin(platform, configuration, "platform"); await fillLogin(platform, generated.platform, "platform"); await submitLogin(platform); await assertWorkspace(platform, "platform");
      const tenant = await keyedRequest(platform, "/platform/tenants", { ...onboardingRequest(generated.tenant), displayName: "WP-0018 统一授权验收租户" }, `authorization-tenant-${suffix}`);
      requireCondition(tenant.status === 201 && tenant.body?.status === "ACTIVE", "独占租户准备失败"); generated.tenant.tenantId = tenant.body.tenantId; persist(); await login();
      const options = (await request(page, "/tenant/members/create-options")).body;
      const member = await keyedRequest(page, "/tenant/members", { loginName: generated.member.loginName, password: generated.member.password,
        conversationFeatures: { assistantSelection: true, conversation: true, ownHistory: false, authorizedCitations: false }, assistantIds: [],
        expectedTenantFeatureVersion: options.tenantFeatureVersion, expectedAuthorizationRevision: options.authorizationRevision }, `authorization-member-${suffix}`);
      requireCondition(member.status === 201 && member.body.assistantIds.length === 0, "普通成员准备失败"); generated.member.membershipId = member.body.membershipId; persist(); await direct();
    });
    await check("助手独立读取取消刷新及无变化保存不覆盖账户和功能", async () => {
      const before = (await request(page, `/tenant/members/${generated.member.membershipId}`)).body; const facts = await read();
      await page.getByRole("button", { name: "取消修改", exact: true }).click(); await direct();
      const sent = page.waitForResponse(response => response.request().method() === "PUT" && response.url().endsWith(route()));
      await page.getByRole("button", { name: "保存可用助手", exact: true }).click(); requireCondition((await sent).status() === 200, "无变化保存失败");
      requireCondition(JSON.stringify(await read()) === JSON.stringify(facts) && JSON.stringify((await request(page, `/tenant/members/${generated.member.membershipId}`)).body) === JSON.stringify(before), "空集合保存推进版本或覆盖其他设置");
    });
    await check("过时修订号重复标识未知字段与缺少CSRF均拒绝", async () => {
      const facts = await read(); const good = { assistantIds: [], expectedAuthorizationRevision: facts.authorizationRevision };
      requireCondition((await request(page, route(), "PUT", { ...good, expectedAuthorizationRevision: facts.authorizationRevision + 1 })).status === 409, "旧授权版本未拒绝");
      for (const input of [{ ...good, tenantId: generated.tenant.tenantId }, { ...good, assistantIds: ["1", "1"] }, { ...good, expectedAuthorizationRevision: 0.5 }, { ...good, assistantIds: ["9223372036854775808"] }]) requireCondition((await request(page, route(), "PUT", input)).status === 400, "不合法助手请求未拒绝");
      requireCondition((await request(page, route(), "PUT", good, false)).status === 403 && (await request(page, route() + "?tenantId=1")).status === 400, "CSRF或查询范围校验未生效");
      requireCondition(JSON.stringify(await read()) === JSON.stringify(facts), "失败请求改变授权事实");
    });
    await check("管理员只读错租户及不存在发布对象均失败关闭", async () => {
      const admin = (await request(page, "/tenant/members?pageNumber=1&pageSize=20")).body.items.find(item => item.membershipRole === "TENANT_ADMINISTRATOR");
      const value = await request(page, `/tenant/members/${admin.membershipId}/assistants`); verifyAssistants(value.body); requireCondition(value.body.readOnly, "管理员不是只读");
      requireCondition((await request(page, `/tenant/members/${admin.membershipId}/assistants`, "PUT", { assistantIds: [], expectedAuthorizationRevision: 0 })).status === 403, "管理员可修改普通助手授权");
      requireCondition((await request(page, "/tenant/members/1/assistants")).status === 403 && (await request(platform, route())).status === 401, "跨范围读取被放行");
      requireCondition((await request(page, route(), "PUT", { assistantIds: ["1"], expectedAuthorizationRevision: (await read()).authorizationRevision })).status === 403, "不存在的发布助手可以授权");
      await page.goto(`${configuration.baseUrl}/tenant/members/${admin.membershipId}/assistants`, { waitUntil: "networkidle" }); await page.getByRole("heading", { name: "管理员账号只读", exact: true }).waitFor();
      requireCondition(await page.getByRole("button", { name: "保存可用助手", exact: true }).count() === 0, "管理员只读页仍提供写控件"); await direct();
    });
    await check("策略目录真实空态与发布依赖门禁，不伪造草稿和通过数", async () => {
      await page.locator(".product-navigation button").filter({ hasText: "访问治理" }).click(); await page.getByRole("heading", { name: "访问策略目录", exact: true }).waitFor();
      requireCondition(await page.getByRole("button", { name: "新建访问策略", exact: true }).isDisabled(), "缺少已发布助手仍可以新建策略");
      await page.getByText("先发布助手并添加普通成员，再配置访问策略。", { exact: true }).waitFor();
      const policies = await request(page, "/tenant/access-policies?pageNumber=1&pageSize=20&q=合成&status=DRAFT");
      requireCondition(policies.status === 200 && policies.body.totalElements === 0 && policies.body.items.length === 0 && !policies.body.hasNext, "空态分页不是真实事实");
      const members = await request(page, `/tenant/access-policies/member-options?q=${generated.member.loginName}&pageSize=20`);
      requireCondition(members.status === 200 && members.body.totalElements === 1 && members.body.items[0].membershipId === generated.member.membershipId, "普通成员候选范围或计数错误");
      requireCondition((await request(page, "/tenant/access-policies?tenantId=1")).status === 400 && (await request(page, "/tenant/access-policies/1/versions/1")).status === 403, "策略范围伪造被放行");
    });
    await check("无助手成员本人登录保持受限，不能读取或发布策略", async () => {
      const ordinary = await memberLogin(browser, configurationForMember, generated.member);
      requireCondition((await request(ordinary.page, "/tenant/access-policies")).status === 403 && (await request(ordinary.page, route())).status === 403, "普通成员进入治理范围");
      requireCondition((await request(ordinary.page, "/query-assistants")).body?.totalElements === 0, "无助手成员看到授权目录");
      await ordinary.page.goto(configuration.baseUrl + "/tenant/access-policies?page=1", { waitUntil: "networkidle" }); await ordinary.page.getByRole("heading", { name: "当前会话无权访问此工作区", exact: true }).first().waitFor(); await ordinary.context.close();
    });
    await check("三尺寸两倍文字键盘与reduced-motion及对象页往返", async () => {
      for (const width of [390, 1024, 1440]) {
        await page.setViewportSize({ width, height: 960 }); await direct();
        for (const enlarged of [false, true]) await withTextScale(page, enlarged, async () => { await verifyLayout(page); await verifyAssistantNavigation(page); });
        await page.getByRole("button", { name: "账号信息", exact: true }).click(); await page.locator(".member-account-facts").waitFor();
        await page.getByRole("button", { name: "对话功能", exact: true }).click(); await page.locator(".member-feature-workspace").waitFor();
        await page.getByRole("button", { name: "可用助手", exact: true }).click(); await page.locator(".member-assistants-workspace").waitFor();
        await page.goto(configuration.baseUrl + "/tenant/access-policies?page=1", { waitUntil: "networkidle" }); await page.getByRole("heading", { name: "访问策略目录", exact: true }).waitFor();
        for (const enlarged of [false, true]) await withTextScale(page, enlarged, async () => { await verifyLayout(page); await verifyDirectorySpacing(page); });
      }
    });
    await check("退出清理与重新登录后真实授权仍为空且没有脚本错误", async () => {
      await logoutWorkspace(page); await login(); await direct(); const facts = await read();
      requireCondition(facts.assistantIds.length === 0 && facts.authorizationRevision === 0 && errors === 0, "空态验收改变权限或发生脚本错误");
      generated.finalFacts = facts; generated.status = "VERIFIED"; persist();
    });
    process.stdout.write("WP-0018 / assistants 生产空态与拒绝路径通过；发布正例和规模选择须另有真实隔离门禁证据，整包与M6未完成。\n");
  } catch (error) { throw new Error(`统一授权生产验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；本轮对象和凭据保留。`); }
  finally { fs.closeSync(descriptor); await browser?.close(); }
}
module.exports = { readConfiguration, verifyAssistants, verifyPreview, verifyDirectorySpacing, verifyAssistantNavigation };
if (require.main === module) { try { run(readConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); } catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; } }
