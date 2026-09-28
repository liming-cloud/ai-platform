/* WP-0017 配额独立切片：只访问本机生产 UI/API，合成对象留存于独占文件。 */
const fs = require("node:fs");
const crypto = require("node:crypto");
const { request, requireCondition, SafeAssertionError, openLogin, fillLogin,
  submitLogin, assertWorkspace, assertIdentity } = require("./acceptance-login.cjs");
const { onboardingRequest, withTextScale } = require("./acceptance-onboarding.cjs");
const { readConfiguration, syntheticIdentity } = require("./acceptance-admins.cjs");

async function createKnowledgeSpace(page, name, key) {
  return page.evaluate(async ({ name, key }) => {
    const csrf = document.cookie.split(";").map(value => value.trim())
      .find(value => value.startsWith("__Host-foundation-csrf="));
    const response = await fetch("/api/v1/knowledge-spaces", {
      method: "POST", credentials: "same-origin",
      headers: { "Content-Type": "application/json", "Idempotency-Key": key,
        "X-CSRF-Token": csrf?.slice(csrf.indexOf("=") + 1) ?? "" },
      body: JSON.stringify({ name, description: "WP-0017 配额切片合成用量" }),
      signal: AbortSignal.timeout(10_000),
    });
    return { status: response.status, body: response.ok ? await response.json() : undefined };
  }, { name, key });
}

async function run(configuration) {
  requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定的 Playwright 运行时");
  const suffix = crypto.randomBytes(6).toString("hex");
  const created = { status: "STARTED", tenant: syntheticIdentity("wp17q", suffix), knowledgeSpaceIds: [] };
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
    await check("正式接口建立可追溯的合成验收租户", async () => {
      const provisioned = await request(platform, "/platform/tenants", "POST", {
        ...onboardingRequest(created.tenant), displayName: "WP-0017 配额验收租户",
      });
      requireCondition(provisioned.status === 201 && /^[1-9]\d*$/.test(provisioned.body?.tenantId)
        && /^[1-9]\d*$/.test(provisioned.body?.initialAdministratorSubjectId), "合成租户开户未成功");
      created.tenant.tenantId = provisioned.body.tenantId;
      created.tenant.subjectId = provisioned.body.initialAdministratorSubjectId;
      persist();
    });
    await check("租户管理员建立两座真实知识库形成下调下限", async () => {
      const tenantContext = await browser.newContext({ viewport: { width: 1440, height: 960 } });
      try {
        const tenant = await tenantContext.newPage();
        await openLogin(tenant, configuration, "tenant");
        await fillLogin(tenant, created.tenant, "tenant");
        await submitLogin(tenant);
        await assertWorkspace(tenant, "tenant");
        await assertIdentity(tenant, "tenant");
        for (const ordinal of [1, 2]) {
          const result = await createKnowledgeSpace(tenant, `WP-0017 配额验收知识库 ${ordinal}`, `wp17q-${suffix}-${ordinal}`);
          requireCondition(result.status === 201 && /^[1-9]\d*$/.test(result.body?.id), "知识库建立失败");
          created.knowledgeSpaceIds.push(result.body.id);
          persist();
        }
      } finally { await tenantContext.close(); }
    });
    const route = `${configuration.baseUrl}/platform/tenants/${created.tenant.tenantId}/quotas`;
    const api = `/platform/tenants/${created.tenant.tenantId}/quotas`;
    await check("独立配额页展示五项配置与已核实用量", async () => {
      await platform.goto(route, { waitUntil: "networkidle" });
      await platform.getByRole("heading", { name: "资源配额", exact: true }).first().waitFor();
      await platform.getByRole("button", { name: "编辑配额" }).waitFor();
      requireCondition(await platform.getByRole("navigation", { name: "租户设置" }).getByRole("link").count() === 3,
        "未交付设置页被伪装为可操作入口");
      const usage = await request(platform, `${api}/usage`);
      requireCondition(usage.status === 200 && usage.body?.complete === true
        && usage.body.knowledgeBases === 2 && usage.body.documentsPerKnowledgeBase === 0,
      "真实已用量没有反映合成知识库");
      requireCondition(await platform.getByText("已用 2 个", { exact: true }).isVisible(), "页面未呈现知识库已用量");
    });
    let writes = 0;
    const observeWrites = outgoing => {
      if (outgoing.method() === "PUT" && new URL(outgoing.url()).pathname.endsWith(api)) writes += 1;
    };
    platform.on("request", observeWrites);
    await check("取消编辑不写入且恢复触发焦点", async () => {
      await platform.getByRole("button", { name: "编辑配额" }).click();
      const dialog = platform.getByRole("dialog");
      await dialog.getByRole("spinbutton", { name: "知识库数量 个" }).fill("2");
      await dialog.getByRole("button", { name: "取消" }).click();
      await platform.waitForFunction(() => document.activeElement?.textContent?.trim() === "编辑配额");
      requireCondition(writes === 0, "取消编辑仍发送了配额写入");
      const unchanged = await request(platform, api);
      requireCondition(unchanged.status === 200 && unchanged.body?.knowledgeBases === 4 && unchanged.body?.version === 0,
        "取消编辑改变了真实配额");
    });
    await check("前端与服务端均拒绝低于已用量", async () => {
      await platform.getByRole("button", { name: "编辑配额" }).click();
      const dialog = platform.getByRole("dialog");
      await dialog.getByRole("spinbutton", { name: "知识库数量 个" }).fill("1");
      await dialog.getByRole("button", { name: "保存配额" }).click();
      requireCondition(await dialog.getByRole("alert").getByText(/不能低于当前已用量/).isVisible()
        && writes === 0, "低于用量的输入仍被客户端写入");
      await dialog.getByRole("button", { name: "取消" }).click();
      const invalid = await request(platform, api, "PUT", {
        knowledgeBases: 1, documentsPerKnowledgeBase: 1234,
        bytesPerKnowledgeBase: "12500000000", maxFileBytes: "75000000", maxTextBytes: "2097152", expectedVersion: 0,
      });
      requireCondition(invalid.status === 409, "服务端未拒绝低于真实用量");
      writes = 0;
    });
    await check("保存到用量下限并在刷新后恢复新版本", async () => {
      await platform.getByRole("button", { name: "编辑配额" }).click();
      const dialog = platform.getByRole("dialog");
      await dialog.getByRole("spinbutton", { name: "知识库数量 个" }).fill("2");
      await dialog.getByRole("spinbutton", { name: "单文件大小 MB" }).fill("50");
      await dialog.getByRole("spinbutton", { name: "粘贴文本大小 MiB" }).fill("1");
      await dialog.getByRole("button", { name: "保存配额" }).click();
      await platform.getByText("资源配额已保存。").waitFor();
      requireCondition(writes === 1, "配额保存未恰好发送一次请求");
      await platform.reload({ waitUntil: "networkidle" });
      await platform.getByRole("button", { name: "编辑配额" }).waitFor();
      const stored = await request(platform, api);
      requireCondition(stored.status === 200 && stored.body?.version === 1
        && stored.body.knowledgeBases === 2 && stored.body.maxFileBytes === "50000000"
        && stored.body.maxTextBytes === "1048576", "刷新后没有恢复真实保存的五项配置");
    });
    await check("旧版本、未知字段、缺少 CSRF 与租户身份均无法写入", async () => {
      const current = (await request(platform, api)).body;
      const changes = { knowledgeBases: 3, documentsPerKnowledgeBase: current.documentsPerKnowledgeBase,
        bytesPerKnowledgeBase: current.bytesPerKnowledgeBase, maxFileBytes: current.maxFileBytes,
        maxTextBytes: current.maxTextBytes, expectedVersion: 0 };
      requireCondition((await request(platform, api, "PUT", changes)).status === 409, "旧版本覆盖了配额");
      requireCondition((await request(platform, api, "PUT", { ...changes, expectedVersion: 1, tenantId: 999 })).status === 400,
        "额外字段进入了配额命令");
      requireCondition((await request(platform, api, "PUT", { ...changes, expectedVersion: 1 }, false)).status === 403,
        "缺少 CSRF 仍可保存配额");
      const tenantContext = await browser.newContext();
      try {
        const tenant = await tenantContext.newPage();
        await openLogin(tenant, configuration, "tenant");
        await fillLogin(tenant, created.tenant, "tenant");
        await submitLogin(tenant);
        await assertWorkspace(tenant, "tenant");
        requireCondition([401, 403].includes((await request(tenant, `${api}/usage`)).status)
          && [401, 403].includes((await request(tenant, api, "PUT", { ...changes, expectedVersion: 1 })).status),
        "租户管理员获得平台配额读取或写入能力");
      } finally { await tenantContext.close(); }
    });
    platform.off("request", observeWrites);
    await check("三尺寸、200% 文字、键盘和 reduced-motion 保持可操作", async () => {
      for (const width of [390, 1024, 1440]) {
        await platform.setViewportSize({ width, height: width === 390 ? 844 : 960 });
        await platform.goto(route, { waitUntil: "networkidle" });
        await platform.getByRole("button", { name: "编辑配额" }).waitFor();
        const layout = await platform.evaluate(() => ({
          overflow: document.documentElement.scrollWidth > innerWidth + 1,
          columns: getComputedStyle(document.querySelector(".tenant-quota-page__summary")).gridTemplateColumns.split(" ").length,
        }));
        requireCondition(!layout.overflow && layout.columns === (width <= 680 ? 2 : width <= 1040 ? 3 : 5),
          `${width} 配额摘要列数或横向布局错误`);
        await platform.getByRole("button", { name: "编辑配额" }).focus();
        await platform.keyboard.press("Enter");
        const dialog = platform.getByRole("dialog");
        await dialog.getByRole("spinbutton", { name: "知识库数量 个" }).waitFor();
        const parity = await dialog.evaluate(surface => {
          const grid = surface.querySelector(".tenant-quota-page__grid");
          const form = surface.querySelector("form");
          const actions = surface.querySelector(".fui-DialogActions");
          const section = document.querySelector(".tenant-quota-page__section");
          const number = document.querySelector(".tenant-quota-page__summary dd strong");
          const unit = number?.nextElementSibling;
          return {
            columns: getComputedStyle(grid).gridTemplateColumns.split(" ").length,
            formWide: form.getBoundingClientRect().width >= surface.getBoundingClientRect().width - 60,
            unitBelowNumber: Boolean(number && unit && unit.getBoundingClientRect().top > number.getBoundingClientRect().top + 8),
            sectionUnboxed: getComputedStyle(section).borderLeftWidth === "0px"
              && getComputedStyle(section).borderRadius === "0px",
            mobileActionsSticky: innerWidth > 680 || getComputedStyle(actions).position === "sticky",
          };
        });
        requireCondition(parity.columns === (width <= 680 ? 1 : 2) && parity.formWide
          && parity.unitBelowNumber && parity.sectionUnboxed && parity.mobileActionsSticky,
        `${width} 配额原型结构或弹窗列宽不一致`);
        await withTextScale(platform, true, async () => {
          const bounds = await dialog.evaluate(surface => {
            const frame = surface.getBoundingClientRect();
            const fields = [...surface.querySelectorAll(".tenant-quota-page__grid .fui-Input")];
            const button = [...surface.querySelectorAll("button")].find(item => item.textContent?.trim() === "保存配额");
            button?.scrollIntoView({ block: "nearest" });
            const confirm = button?.getBoundingClientRect();
            return { fits: frame.left >= -1 && frame.right <= innerWidth + 1
              && surface.scrollWidth <= surface.clientWidth + 1
              && fields.every(field => field.scrollWidth <= field.clientWidth + 1),
              reachable: Boolean(confirm && confirm.top >= -1 && confirm.bottom <= innerHeight + 1) };
          });
          requireCondition(bounds.fits && bounds.reachable, `${width} 放大文字后配额弹窗裁切或保存不可达`);
        }, ".tenant-quota-page__dialog");
        await dialog.getByRole("button", { name: "取消" }).click();
      }
      requireCondition(pageErrors === 0, "配额页出现未处理异常");
      requireCondition(await platform.evaluate(() => !document.getAnimations().some(animation =>
        animation.playState === "running" && animation.effect?.getTiming().duration > 1)),
      "reduced-motion 下仍有运动动画");
    });
    created.status = "VERIFIED";
    persist();
    process.stdout.write("WP-0017 / quotas 切片真实部署验收通过；整包与 M6 仍未完成。\n");
  } catch (error) {
    throw new Error(`配额设置验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；合成对象保留于独占文件，不输出秘密。`);
  } finally {
    fs.closeSync(descriptor);
    await browser?.close();
  }
}

if (require.main === module) {
  try { run(readConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
