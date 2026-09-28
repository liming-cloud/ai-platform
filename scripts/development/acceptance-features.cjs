/* WP-0017 租户功能切片：仅通过本机正式接口建立可追溯合成对象，不输出秘密。 */
const fs = require("node:fs");
const crypto = require("node:crypto");
const { request, requireCondition, SafeAssertionError, openLogin, fillLogin,
  submitLogin, assertWorkspace, assertIdentity, assertAnonymous } = require("./acceptance-login.cjs");
const { onboardingRequest, withTextScale } = require("./acceptance-onboarding.cjs");
const { readConfiguration, syntheticIdentity } = require("./acceptance-admins.cjs");

const codes = ["knowledgeManagement", "modelGovernance", "assistantDelivery", "evaluationGovernance",
  "assistantSelection", "conversation", "ownHistory", "authorizedCitations"];
const labels = ["知识管理", "模型治理", "助手交付", "评测治理", "选择已授权助手",
  "发起与继续对话", "查看本人历史", "查看有权引用"];

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
    tenant: syntheticIdentity("wp17f", suffix), otherTenant: syntheticIdentity("wp17g", suffix) };
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
    await check("正式接口原子建立两个隔离合成租户", async () => {
      for (const [key, name] of [["tenant", "功能验收甲"], ["otherTenant", "功能验收乙"]]) {
        const identity = created[key];
        const result = await request(platform, "/platform/tenants", "POST", {
          ...onboardingRequest(identity), displayName: `WP-0017 ${name}`,
        });
        requireCondition(result.status === 201 && /^[1-9]\d*$/.test(result.body?.tenantId)
          && /^[1-9]\d*$/.test(result.body?.initialAdministratorSubjectId), "合成租户开户未成功");
        identity.tenantId = result.body.tenantId;
        identity.subjectId = result.body.initialAdministratorSubjectId;
        persist();
      }
    });
    const api = `/platform/tenants/${created.tenant.tenantId}/features`;
    const otherApi = `/platform/tenants/${created.otherTenant.tenantId}/features`;
    await check("独立功能页展示固定八项且初值全开", async () => {
      await platform.goto(`${configuration.baseUrl}${api}`, { waitUntil: "networkidle" });
      await platform.getByRole("heading", { name: "租户可用功能", exact: true }).waitFor();
      const values = await request(platform, api);
      requireCondition(values.status === 200 && values.body?.version === 0
        && codes.every(code => values.body.availableFeatures?.[code] === true)
        && Object.keys(values.body.availableFeatures).length === 8, "服务端初值不是原型八项全开");
      requireCondition(await platform.getByRole("navigation", { name: "租户设置" }).getByRole("link").count() === 4,
        "功能入口未开放或未交付设置被伪装为链接");
      const checkboxes = platform.locator(".tenant-feature-page__control input[type=checkbox]");
      requireCondition(await checkboxes.count() === 8, "功能勾选数量不正确");
      for (let index = 0; index < codes.length; index += 1) {
        const control = platform.locator(".tenant-feature-page__control").nth(index);
        requireCondition(await checkboxes.nth(index).isChecked()
          && await control.isVisible()
          && await control.locator("strong").innerText() === labels[index], "功能顺序或初值不一致");
      }
    });
    let writes = 0;
    platform.on("request", outgoing => {
      if (outgoing.method() === "PUT" && new URL(outgoing.url()).pathname.endsWith(api)) writes += 1;
    });
    await check("取消草稿不写入且刷新恢复服务端值", async () => {
      await platform.getByRole("checkbox", { name: /知识管理/ }).uncheck();
      await platform.getByRole("button", { name: "取消", exact: true }).click();
      requireCondition(writes === 0 && await platform.getByRole("checkbox", { name: /知识管理/ }).isChecked(),
        "取消草稿仍保存或未恢复");
      await platform.reload({ waitUntil: "networkidle" });
      requireCondition((await request(platform, api)).body?.version === 0, "取消改变了数据库版本");
    });
    const oldTenant = await tenantLogin(browser, configuration, created.tenant);
    await check("保存关闭知识管理只改变目标租户并递增版本", async () => {
      await platform.getByRole("checkbox", { name: /知识管理/ }).uncheck();
      await platform.getByRole("button", { name: "保存功能配置" }).click();
      await platform.getByText("功能配置已保存。").waitFor();
      const target = await request(platform, api);
      const other = await request(platform, otherApi);
      requireCondition(writes === 1 && target.status === 200 && target.body?.version === 1
        && target.body.availableFeatures.knowledgeManagement === false
        && codes.slice(1).every(code => target.body.availableFeatures[code] === true)
        && other.status === 200 && other.body?.version === 0
        && codes.every(code => other.body.availableFeatures[code] === true), "保存越过独立配置边界");
      await platform.reload({ waitUntil: "networkidle" });
      requireCondition(!await platform.getByRole("checkbox", { name: /知识管理/ }).isChecked(), "刷新后未恢复保存值");
    });
    await check("目标租户旧会话撤销而平台会话保持有效", async () => {
      await assertAnonymous(oldTenant.page);
      await assertIdentity(platform, "platform");
      await oldTenant.context.close();
    });
    await check("新租户会话隐藏知识入口且直接接口返回拒绝", async () => {
      const tenant = await tenantLogin(browser, configuration, created.tenant);
      try {
        const capability = await request(tenant.page, "/auth/browser-session");
        const direct = await request(tenant.page, "/knowledge-spaces");
        requireCondition(capability.status === 200 && !capability.body.capabilityCodes.includes("knowledge:write")
          && direct.status === 403, "新会话仍保留关闭的知识功能");
      } finally { await tenant.context.close(); }
    });
    await check("其他租户会话仍可使用知识入口", async () => {
      const tenant = await tenantLogin(browser, configuration, created.otherTenant);
      try {
        const direct = await request(tenant.page, "/knowledge-spaces");
        requireCondition(direct.status === 200, "其他租户被错误阻断");
      } finally { await tenant.context.close(); }
    });
    await check("旧版本、未知字段、缺少 CSRF 与租户身份不能写入", async () => {
      const current = (await request(platform, api)).body;
      requireCondition((await request(platform, api, "PUT", {
        availableFeatures: current.availableFeatures, expectedVersion: 0,
      })).status === 409, "旧版本未拒绝");
      requireCondition((await request(platform, api, "PUT", {
        availableFeatures: { ...current.availableFeatures, unknownFeature: true }, expectedVersion: 1,
      })).status === 400, "未知字段未拒绝");
      requireCondition((await request(platform, api, "PUT", {
        availableFeatures: current.availableFeatures, expectedVersion: 1,
      }, false)).status === 403, "缺少 CSRF 未拒绝");
      const tenant = await tenantLogin(browser, configuration, created.tenant);
      try {
        requireCondition((await request(tenant.page, api, "PUT", {
          availableFeatures: current.availableFeatures, expectedVersion: 1,
        })).status === 401, "租户会话越权修改平台配置");
      } finally { await tenant.context.close(); }
    });
    await check("恢复功能后新会话重新获得直接访问", async () => {
      const current = (await request(platform, api)).body;
      await platform.getByRole("checkbox", { name: /知识管理/ }).check();
      await platform.getByRole("button", { name: "保存功能配置" }).click();
      await platform.getByText("功能配置已保存。").waitFor();
      requireCondition((await request(platform, api)).body?.version === 2, "恢复未递增版本");
      const tenant = await tenantLogin(browser, configuration, created.tenant);
      try {
        const capability = await request(tenant.page, "/auth/browser-session");
        const direct = await request(tenant.page, "/knowledge-spaces");
        requireCondition(capability.body.capabilityCodes.includes("knowledge:write") && direct.status === 200,
          "功能恢复后新会话仍被阻断");
      } finally { await tenant.context.close(); }
    });
    await check("八项开关落库并约束四类已发布直接入口", async () => {
      const routes = ["/knowledge-spaces", "/model-configurations", "/assistants",
        "/evaluation-datasets/candidates"];
      const baseline = await tenantLogin(browser, configuration, created.tenant);
      try {
        for (const route of routes) {
          requireCondition((await request(baseline.page, route)).status === 200,
            `已发布入口 ${route} 在全开基线下不可用`);
        }
        const disabled = Object.fromEntries(codes.map(code => [code, false]));
        const closed = await request(platform, api, "PUT", { availableFeatures: disabled, expectedVersion: 2 });
        requireCondition(closed.status === 200 && closed.body?.version === 3
          && codes.every(code => closed.body.availableFeatures[code] === false), "八项关闭未原子保存");
        await assertAnonymous(baseline.page);
      } finally { await baseline.context.close(); }
      const blocked = await tenantLogin(browser, configuration, created.tenant);
      try {
        for (const route of routes) {
          requireCondition((await request(blocked.page, route)).status === 403,
            `关闭后直接入口 ${route} 未拒绝`);
        }
      } finally { await blocked.context.close(); }
      const other = await tenantLogin(browser, configuration, created.otherTenant);
      try {
        for (const route of routes) {
          requireCondition((await request(other.page, route)).status === 200,
            `关闭目标租户时影响了对照租户入口 ${route}`);
        }
      } finally { await other.context.close(); }
      const restored = await request(platform, api, "PUT", {
        availableFeatures: Object.fromEntries(codes.map(code => [code, true])), expectedVersion: 3,
      });
      requireCondition(restored.status === 200 && restored.body?.version === 4
        && codes.every(code => restored.body.availableFeatures[code] === true), "八项恢复未原子保存");
      const reopened = await tenantLogin(browser, configuration, created.tenant);
      try {
        for (const route of routes) {
          requireCondition((await request(reopened.page, route)).status === 200,
            `恢复后直接入口 ${route} 仍被阻断`);
        }
      } finally { await reopened.context.close(); }
    });
    await check("三尺寸、200% 文字、键盘与 reduced-motion 无裁切", async () => {
      for (const width of [390, 1024, 1440]) {
        await platform.setViewportSize({ width, height: 960 });
        await platform.reload({ waitUntil: "networkidle" });
        await platform.getByRole("heading", { name: "租户可用功能", exact: true }).waitFor();
        const layout = await platform.evaluate(() => ({
          columns: getComputedStyle(document.querySelector(".tenant-feature-page__grid")).gridTemplateColumns.split(" ").length,
          overflow: document.documentElement.scrollWidth > innerWidth + 1,
          order: [...document.querySelectorAll(".tenant-feature-page__control strong")].map(item => item.textContent),
          headings: [...document.querySelectorAll("h1")].map(item => item.textContent.trim()),
          editorRight: document.querySelector(".tenant-feature-page__editor").getBoundingClientRect().right,
        }));
        requireCondition(layout.columns === (width <= 680 ? 1 : 2) && !layout.overflow
          && labels.every((label, index) => layout.order[index] === label)
          && layout.headings.length === 1 && layout.headings[0] === "可用功能"
          && Math.abs(width - layout.editorRight - (width <= 680 ? 16 : 32)) <= 1,
        `${width} 功能页布局不符合原型`);
        await withTextScale(platform, true, async () => {
          const fits = await platform.evaluate(() => {
            const grid = document.querySelector(".tenant-feature-page__grid");
            const save = [...document.querySelectorAll("button")].find(item => item.textContent?.trim() === "保存功能配置");
            save?.scrollIntoView({ block: "nearest" });
            const bounds = save?.getBoundingClientRect();
            return document.documentElement.scrollWidth <= innerWidth + 1
              && grid.scrollWidth <= grid.clientWidth + 1
              && Boolean(bounds && bounds.left >= -1 && bounds.right <= innerWidth + 1);
          });
          requireCondition(fits, `${width} 放大文字后发生横向裁切或保存不可达`);
        });
        await platform.getByRole("checkbox", { name: /知识管理/ }).focus();
        await platform.keyboard.press("Space");
        requireCondition(!await platform.getByRole("checkbox", { name: /知识管理/ }).isChecked(), "键盘无法修改草稿");
        await platform.getByRole("button", { name: "取消", exact: true }).click();
      }
      requireCondition(pageErrors === 0, "功能页出现未处理异常");
      requireCondition(await platform.evaluate(() => !document.getAnimations().some(animation =>
        animation.playState === "running" && animation.effect?.getTiming().duration > 1)),
      "reduced-motion 下仍有运动动画");
    });
    created.status = "VERIFIED";
    persist();
    process.stdout.write("WP-0017 / features 切片真实部署验收通过；整包与 M6 仍未完成。\n");
  } catch (error) {
    throw new Error(`功能设置验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；合成对象保留于独占文件，不输出秘密。`);
  } finally {
    fs.closeSync(descriptor);
    await browser?.close();
  }
}

if (require.main === module) {
  try { run(readConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
