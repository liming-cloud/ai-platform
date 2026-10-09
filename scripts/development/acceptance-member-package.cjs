/* WP-0018：调整范围的整包只读集成；真实写入和规模结论仍绑定原切片证据。 */
const fs = require("node:fs");
const { openLogin, fillLogin, submitLogin, assertIdentity, assertAnonymous, logoutWorkspace,
  request, requireCondition, SafeAssertionError } = require("./acceptance-login.cjs");
const { verifyLayout } = require("./acceptance-member-account.cjs");
const { readRoleConfiguration, verifyAdministratorAccount } = require("./acceptance-member-roles.cjs");
const { verifyFeatures } = require("./acceptance-member-features.cjs");
const { verifyAssistants } = require("./acceptance-member-authorization.cjs");
const { withTextScale } = require("./acceptance-onboarding.cjs");

function readPackageConfiguration(environment = process.env) {
  const configuration = readRoleConfiguration(environment);
  const id = configuration.credentials.member.membershipId;
  requireCondition(typeof id === "string" && /^[1-9]\d{0,18}$/.test(id)
    && BigInt(id) <= 9223372036854775807n, "整包只读验收需要精确的既有成员标识");
  return configuration;
}

/** 只允许同源精确认证端点；其他非读取方法在发送前阻断，不用宽泛 auth 前缀豁免。 */
function classifyRequest(method, target, baseUrl) {
  if (["GET", "HEAD", "OPTIONS"].includes(method)) return "read";
  const url = new URL(target);
  if (url.origin === baseUrl && !url.search && !url.hash && !url.username && !url.password
    && ((method === "POST" && url.pathname === "/api/v1/auth/browser-sessions")
      || (method === "DELETE" && url.pathname === "/api/v1/auth/browser-session"))) return "authentication";
  return "business";
}

async function run(configuration) {
  requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定的 Playwright 运行时");
  const report = { status: "IN_PROGRESS", workPackage: "WP-0018", scope: "read-only-package-integration",
    primaryScreens: ["tenant-members", "tenant-member-create", "tenant-member-account", "tenant-member-features",
      "tenant-member-assistants", "tenant-roles", "access-policies", "access-policy-detail"],
    transferredOperations: ["tenant-overview:0", "tenant-overview:1", "tenant-overview:2", "tenant-members:7"],
    scenarios: [], network: { read: 0, authentication: 0, business: 0 }, browserErrors: 0 };
  const descriptor = fs.openSync(configuration.outputFile, "wx", 0o600);
  const persist = () => { fs.ftruncateSync(descriptor, 0); fs.writeSync(descriptor, JSON.stringify(report), 0, "utf8"); fs.fsyncSync(descriptor); };
  persist();
  let browser; let stage = "启动浏览器";
  const check = async (name, action) => {
    stage = name; await action();
    requireCondition(report.network.business === 0 && report.browserErrors === 0, "只读集成出现业务写请求或浏览器异常");
    report.scenarios.push({ name, passed: true }); persist();
    process.stdout.write(`${JSON.stringify({ assertion: name, result: "通过" })}\n`);
  };
  const identity = async (role, action) => {
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce", serviceWorkers: "block" });
    await context.route("**/*", async route => {
      const sent = route.request(); const kind = classifyRequest(sent.method(), sent.url(), configuration.baseUrl);
      report.network[kind] += 1;
      if (kind === "business") await route.abort("blockedbyclient"); else await route.continue();
    });
    const page = await context.newPage(); page.setDefaultTimeout(15_000);
    page.on("pageerror", () => { report.browserErrors += 1; });
    try {
      if (role === "member") {
        await page.goto(configuration.baseUrl + "/ask", { waitUntil: "networkidle" });
        await page.getByRole("heading", { name: "知识问答登录", exact: true }).waitFor();
        await fillLogin(page, { ...configuration.credentials.member, tenantCode: configuration.credentials.tenant.tenantCode }, "tenant");
        await submitLogin(page);
        requireCondition((await request(page, "/auth/browser-session")).status === 200, "既有普通成员未建立真实会话");
      } else if (role !== "anonymous") {
        await openLogin(page, configuration, role); await fillLogin(page, configuration.credentials[role], role);
        await submitLogin(page); await assertIdentity(page, role);
      } else await page.goto(configuration.baseUrl, { waitUntil: "networkidle" });
      await action(page);
    } finally { await context.close(); }
  };
  try {
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    await identity("tenant", async page => {
      const member = configuration.credentials.member; const memberRoute = `/tenant/members/${member.membershipId}`;
      const facts = async () => {
        const values = [];
        for (const route of [memberRoute, memberRoute + "/features", memberRoute + "/assistants", "/tenant/members/create-options",
          "/tenant/access-policies?pageNumber=1&pageSize=20"]) {
          const response = await request(page, route); requireCondition(response.status === 200, "只读集成缺少真实成员或配置事实");
          values.push(response.body);
        }
        requireCondition(values[0].membershipId === member.membershipId && values[0].loginName === member.loginName
          && values[0].membershipRole === "TENANT_USER" && values[0].status === "ENABLED", "既有成员身份与精确目标不一致");
        verifyFeatures(values[1]); verifyAssistants(values[2]);
        return values;
      };
      const before = await facts();
      const direct = async (route, title, ready) => {
        await page.goto(configuration.baseUrl + route, { waitUntil: "networkidle" });
        await page.getByRole("heading", { name: title, exact: true }).first().waitFor();
        if (ready) await ready();
        await page.evaluate(() => document.fonts.ready);
      };
      await check("租户成员搜索与服务端分页", async () => {
        await direct("/access/members?page=1", "成员与对话功能");
        await page.getByLabel("搜索成员", { exact: true }).fill(member.loginName);
        await page.getByRole("row").filter({ hasText: member.loginName }).getByRole("button", { name: "查看成员", exact: true }).waitFor();
        const result = await request(page, `/tenant/members?pageNumber=1&pageSize=10&q=${encodeURIComponent(member.loginName)}`);
        requireCondition(result.status === 200 && result.body.totalElements === 1 && result.body.items.length === 1
          && result.body.items[0].membershipId === member.membershipId, "成员搜索或分页范围不准确");
        await page.getByRole("row").filter({ hasText: member.loginName }).getByRole("button", { name: "查看成员", exact: true }).click();
        await page.getByRole("heading", { name: "成员账号", exact: true }).waitFor();
      });
      await check("账户功能助手三页导航与取消保持独立事实", async () => {
        for (const [label, title] of [["对话功能", "对话功能"], ["可用助手", "可用助手"], ["账号信息", "成员账号"]]) {
          await page.getByRole("button", { name: label, exact: true }).click();
          await page.getByRole("heading", { name: title, exact: true }).first().waitFor();
          await page.getByRole("heading", { name: member.loginName, exact: true }).waitFor();
          if (label !== "账号信息") await page.getByRole("button", { name: "取消修改", exact: true }).click();
        }
        requireCondition(JSON.stringify(await facts()) === JSON.stringify(before), "导航或取消改变了已保存配置");
      });
      await check("开户三步仅验证草稿与取消而不创建", async () => {
        await direct("/tenant/members/new?step=1", "添加成员", () => page.getByLabel("登录名", { exact: true }).waitFor());
        await page.getByLabel("登录名", { exact: true }).fill("wp18-readonly-draft");
        await page.getByRole("button", { name: "下一步", exact: true }).click();
        await page.getByRole("heading", { name: "使用权限", exact: true }).waitFor();
        await page.getByRole("button", { name: "下一步", exact: true }).click();
        await page.getByLabel("初始密码", { exact: true }).waitFor();
        requireCondition(await page.getByLabel("初始密码", { exact: true }).inputValue() === "", "只读复验意外恢复密码");
        await page.getByRole("button", { name: "取消", exact: true }).click();
        await page.getByRole("heading", { name: "成员与对话功能", exact: true }).waitFor();
      });
      await check("管理员账户与独立设置继续只读", async () => {
        const result = await request(page, `/tenant/members?pageNumber=1&pageSize=100&q=${encodeURIComponent(configuration.credentials.tenant.loginName)}`);
        const admin = result.body?.items.find(value => value.membershipRole === "TENANT_ADMINISTRATOR"
          && value.loginName === configuration.credentials.tenant.loginName);
        requireCondition(result.status === 200 && admin, "当前租户缺少真实管理员资格");
        const response = await request(page, `/tenant/members/${admin.membershipId}`);
        requireCondition(response.status === 200, "管理员账户读取失败"); verifyAdministratorAccount(response.body);
        for (const suffix of ["", "/features", "/assistants"]) {
          await page.goto(configuration.baseUrl + `/tenant/members/${admin.membershipId}${suffix}`, { waitUntil: "networkidle" });
          await page.getByRole("heading", { name: "管理员账号只读", exact: true }).waitFor();
          requireCondition(await page.locator('main input[type="password"]').count() === 0, "管理员页面意外提供密码修改");
        }
      });
      const policy = before[4].items[0];
      const policyRoute = policy ? `/tenant/access-policies/${policy.policyId}/versions/${policy.versionId}`
        : "/tenant/access-policies/1/versions/1";
      report.policyDetailState = policy ? "existing-policy" : "denied-no-existing-policy";
      const screens = [
        ["tenant-members", "/access/members?page=1", "成员与对话功能", () => page.locator(".member-pagination").waitFor()],
        ["tenant-member-create", "/tenant/members/new?step=1", "添加成员", () => page.getByLabel("登录名", { exact: true }).waitFor()],
        ["tenant-member-account", memberRoute, "成员账号", () => page.getByRole("heading", { name: member.loginName, exact: true }).waitFor()],
        ["tenant-member-features", memberRoute + "/features", "对话功能", () => page.getByRole("heading", { name: member.loginName, exact: true }).waitFor()],
        ["tenant-member-assistants", memberRoute + "/assistants", "可用助手", () => page.getByRole("heading", { name: member.loginName, exact: true }).waitFor()],
        ["tenant-roles", "/access/roles", "租户固定角色", () => page.locator(".tenant-role-page__fact-list").waitFor()],
        ["access-policies", "/tenant/access-policies?page=1", "访问策略目录", () => page.locator(".policy-directory-table").waitFor()],
        ["access-policy-detail", policyRoute, policy ? `${policy.name} v${policy.number}` : "访问策略版本",
          () => policy ? page.locator(".policy-split-detail").waitFor() : page.getByRole("alert").waitFor()],
      ];
      for (const [screen, route, title, ready] of screens) for (const width of [390, 1024, 1440]) for (const enlarged of [false, true]) {
        await check(`${screen}-${width}-${enlarged ? 200 : 100}文字与键盘`, async () => {
          await page.setViewportSize({ width, height: 960 }); await direct(route, title, ready);
          await withTextScale(page, enlarged, async () => {
            await verifyLayout(page);
            const paint = await page.evaluate(() => {
              const brand = getComputedStyle(document.querySelector(".product-brand div > span"));
              const mark = getComputedStyle(document.querySelector(".product-brand > span"));
              return { weight: brand.fontWeight, lineHeight: brand.lineHeight, markColor: mark.color,
                buttons: [...document.querySelectorAll(".member-create-form > .member-form-actions button, .member-account-workspace .member-form-actions button")].map(button => {
                  const style = getComputedStyle(button);
                  return { height: button.getBoundingClientRect().height, accountReturn: button.closest(".member-account-workspace") !== null,
                    color: style.color, background: style.backgroundColor };
                }) };
            });
            requireCondition(paint.weight === "700" && paint.lineHeight === "19.2px" && paint.markColor === "rgb(255, 255, 255)",
              "共享品牌绘制与原项目不一致");
            for (const button of paint.buttons) {
              requireCondition(Math.abs(button.height - (enlarged ? 54 : width <= 680 ? 44 : 36)) < 0.01,
                "成员创建或账号按钮实际高度与原项目不一致");
              // 原项目先选择普通成员后，此操作为主按钮；未选对象的返回按钮属于另一状态。
              if (button.accountReturn) requireCondition(button.color === "rgb(255, 255, 255)"
                && button.background === "oklab(0.514837 0.0810745 0.066312)", "有效成员页返回操作与原项目主按钮绘制不一致");
            }
          });
        });
      }
      await check("伪造租户参数与无权对象失败关闭", async () => {
        requireCondition((await request(page, "/tenant/access-policies?tenantId=1")).status === 400
          && (await request(page, "/tenant/members/1/features")).status === 403
          && (await request(page, "/tenant/members/1/assistants")).status === 403, "越界对象或租户参数被放行");
        if (!policy) requireCondition((await request(page, policyRoute)).status === 403, "不存在策略意外可读");
      });
      await check("固定角色与成员入口可由键盘往返", async () => {
        await direct("/access/roles", "租户固定角色");
        await page.getByRole("button", { name: "管理成员", exact: true }).focus();
        await page.keyboard.press("Enter");
        await page.getByRole("heading", { name: "成员与对话功能", exact: true }).waitFor();
        await page.getByLabel("搜索成员", { exact: true }).focus(); await page.keyboard.press("Tab");
        requireCondition(await page.evaluate(() => document.activeElement !== document.body), "Tab 未保持可达焦点");
      });
      await check("租户复验前后账户功能授权及策略事实一致", async () => {
        requireCondition(JSON.stringify(await facts()) === JSON.stringify(before), "只读集成改变了业务事实");
      });
      await check("租户退出后旧会话与直接工作区失效", async () => {
        await logoutWorkspace(page); await page.getByRole("heading", { name: "租户管理员登录", exact: true }).waitFor();
        await assertAnonymous(page); requireCondition((await request(page, memberRoute)).status === 401, "退出后成员事实仍可读取");
      });
    });
    for (const role of ["member", "platform", "anonymous"]) await identity(role, async page => {
      await check(`${role}直接治理URL与API拒绝`, async () => {
        for (const route of ["/tenant/members", "/tenant/members/create-options", `/tenant/members/${configuration.credentials.member.membershipId}/features`,
          `/tenant/members/${configuration.credentials.member.membershipId}/assistants`, "/tenant/access-policies", "/tenant/access-policies/1/versions/1"]) {
          requireCondition((await request(page, route)).status === (role === "member" ? 403 : 401), "无权身份读取了租户治理事实");
        }
        await page.goto(configuration.baseUrl + "/tenant/access-policies?page=1", { waitUntil: "networkidle" });
        await page.getByRole("heading", { name: role === "anonymous" ? "租户管理员登录" : "当前会话无权访问此工作区", exact: true }).first().waitFor();
      });
    });
    report.status = "PASSED"; persist();
    process.stdout.write(`${JSON.stringify({ result: "通过", scope: report.scope, scenarios: report.scenarios.length, businessWrites: report.network.business })}\n`);
  } catch (error) {
    report.status = "FAILED"; report.failedStage = stage;
    report.failure = error instanceof SafeAssertionError ? error.message : "步骤执行失败；原始异常不输出以保护凭据";
    persist(); throw new SafeAssertionError(`WP-0018 整包只读集成失败：${stage}；${report.failure}`);
  } finally { if (browser) await browser.close(); fs.closeSync(descriptor); }
}

if (require.main === module) {
  Promise.resolve().then(() => run(readPackageConfiguration())).catch(error => {
    process.stderr.write(`${JSON.stringify({ result: "失败", message: error instanceof SafeAssertionError ? error.message : "验收配置或执行失败；未输出原始异常" })}\n`);
    process.exitCode = 1;
  });
}
module.exports = { readPackageConfiguration, classifyRequest };
