/* WP-0017：三入口仅导航；不创建业务对象，不记录输入、Cookie、trace 或浏览器 profile。 */
const { chromium } = require("playwright");
const h = require("./acceptance-login.cjs");
const entries = [
  { id: "platform", path: "/login", label: "平台管理登录" },
  { id: "tenant", path: "/tenant/login", label: "租户管理员登录" },
  { id: "query", path: "/ask", label: "普通用户问答登录" },
];

async function assertEntry(page, entry) {
  await page.getByLabel("密码", { exact: true }).waitFor();
  const navigation = page.getByRole("navigation", { name: "其他登录入口" });
  await navigation.waitFor();
  const links = await navigation.getByRole("link").evaluateAll(elements => elements.map(element => ({
    label: element.textContent.trim(), path: new URL(element.href).pathname,
  })));
  const expected = entries.filter(item => item.id !== entry.id);
  h.requireCondition(links.length === 2 && expected.every((item, index) =>
    links[index].label === item.label && links[index].path === item.path), "三入口链接或顺序不符合原型");
  h.requireCondition(await page.getByLabel("密码", { exact: true }).inputValue() === "", "入口恢复了上页密码");
  h.requireCondition(await page.getByLabel("租户编码", { exact: true }).count() === (entry.id === "platform" ? 0 : 1),
    "导航错误改变了租户登录范围");
  h.requireCondition(new URL(page.url()).pathname === entry.path, "导航未到达固定入口");
  await h.assertAnonymous(page);
}

async function run(configuration) {
  h.requireCondition(!configuration.setup, "登录导航切片只读，不提供业务对象准备");
  let stage = "启动真实浏览器", browser, passed = 0, businessWrites = 0;
  async function scenario(name, body) {
    stage = name;
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    try {
      const page = await context.newPage();
      page.on("request", request => {
        const path = new URL(request.url()).pathname;
        if (path.startsWith("/api/v1/") && !path.startsWith("/api/v1/auth/")
            && !["GET", "HEAD", "OPTIONS"].includes(request.method())) businessWrites += 1;
      });
      await body(page);
      h.requireCondition(businessWrites === 0, "导航触发了业务写入");
      passed += 1;
      process.stdout.write(`通过 ${passed}：${name}\n`);
    } finally { await context.close(); }
  }
  try {
    browser = await chromium.launch({ channel: "chrome", headless: true });
    for (const entry of entries) {
      await scenario(`${entry.id} 直接访问显示另外两个入口`, async page => {
        await page.goto(configuration.baseUrl + entry.path, { waitUntil: "networkidle" });
        await assertEntry(page, entry);
      });
    }
    for (const from of entries) for (const to of entries.filter(item => item.id !== from.id)) {
      await scenario(`${from.id} 到 ${to.id} 不认证且不继承密码`, async page => {
        await page.goto(configuration.baseUrl + from.path, { waitUntil: "networkidle" });
        await page.getByLabel("密码", { exact: true }).fill("navigation-fixture-only");
        let authenticationWrites = 0;
        page.on("request", request => {
          if (new URL(request.url()).pathname.startsWith("/api/v1/auth/")
              && !["GET", "HEAD", "OPTIONS"].includes(request.method())) authenticationWrites += 1;
        });
        await page.getByRole("link", { name: to.label, exact: true }).click();
        await assertEntry(page, to);
        h.requireCondition(authenticationWrites === 0, "点击入口自动认证或退出");
        const persisted = await page.evaluate(() => [location.href, JSON.stringify(localStorage),
          JSON.stringify(sessionStorage)].some(value => value.includes("navigation-fixture-only")));
        h.requireCondition(!persisted, "密码写入了地址或浏览器存储");
      });
    }
    await scenario("前后退与刷新保持入口且不恢复密码", async page => {
      await page.goto(configuration.baseUrl + entries[1].path, { waitUntil: "networkidle" });
      await page.getByLabel("密码", { exact: true }).fill("navigation-fixture-only");
      await page.getByRole("link", { name: entries[0].label, exact: true }).click();
      await assertEntry(page, entries[0]);
      await page.getByRole("link", { name: entries[2].label, exact: true }).click();
      await assertEntry(page, entries[2]);
      await page.goBack({ waitUntil: "networkidle" });
      await assertEntry(page, entries[0]);
      await page.reload({ waitUntil: "networkidle" });
      await assertEntry(page, entries[0]);
      await page.goForward({ waitUntil: "networkidle" });
      await assertEntry(page, entries[2]);
      await page.goBack({ waitUntil: "networkidle" });
      await page.goBack({ waitUntil: "networkidle" });
      await assertEntry(page, entries[1]);
    });
    for (const role of ["platform", "tenant"]) {
      await scenario(`${role} 已有身份访问另一入口不退出或增权`, async page => {
        await h.openLogin(page, configuration, role);
        await h.fillLogin(page, configuration.credentials[role], role);
        await h.submitLogin(page);
        await h.assertWorkspace(page, role);
        const before = await h.request(page, "/auth/browser-session");
        const sessionCookies = async () => JSON.stringify((await page.context().cookies())
          .filter(cookie => cookie.httpOnly).map(cookie => [cookie.name, cookie.value]).sort());
        const identityBefore = await sessionCookies();
        h.requireCondition(identityBefore !== "[]", "真实认证未建立 HttpOnly 会话");
        let authenticationWrites = 0;
        page.on("request", request => {
          if (new URL(request.url()).pathname.startsWith("/api/v1/auth/")
              && !["GET", "HEAD", "OPTIONS"].includes(request.method())) authenticationWrites += 1;
        });
        await page.goto(configuration.baseUrl + (role === "platform" ? "/tenant/login" : "/login"),
          { waitUntil: "networkidle" });
        await page.getByRole("heading", { name: "当前登录身份与入口不匹配", exact: true }).waitFor();
        await h.assertIdentity(page, role);
        h.requireCondition(await page.locator('input[type="password"]').count() === 0,
          "已有身份被静默替换为凭据表单");
        await page.goto(configuration.baseUrl + "/ask", { waitUntil: "networkidle" });
        const after = await h.request(page, "/auth/browser-session");
        h.requireCondition(after.status === 200 && identityBefore === await sessionCookies()
          && JSON.stringify(before.body.displayRoles) === JSON.stringify(after.body.displayRoles)
          && JSON.stringify(before.body.workspaceCategories) === JSON.stringify(after.body.workspaceCategories)
          && JSON.stringify(before.body.capabilityCodes) === JSON.stringify(after.body.capabilityCodes),
        "问答导航改变了服务端身份或能力");
        h.requireCondition(authenticationWrites === 0, "访问另一入口自动认证或退出");
        const leaked = await page.evaluate(passwords => [location.href, JSON.stringify(localStorage),
          JSON.stringify(sessionStorage)].some(value => passwords.some(password => value.includes(password))),
        [configuration.credentials.platform.password, configuration.credentials.tenant.password]);
        h.requireCondition(!leaked, "凭据写入了地址或浏览器存储");
      });
    }
    for (const entry of entries) {
      await scenario(`${entry.id} 可用键盘切换入口`, async page => {
        await page.goto(configuration.baseUrl + entry.path, { waitUntil: "networkidle" });
        const target = entries.find(item => item.id !== entry.id);
        let focused = false;
        for (let attempt = 0; attempt < 16 && !focused; attempt += 1) {
          await page.keyboard.press("Tab");
          focused = await page.getByRole("link", { name: target.label, exact: true })
            .evaluate(element => element === document.activeElement);
        }
        h.requireCondition(focused, "入口链接无法通过键盘到达");
        await page.keyboard.press("Enter");
        await assertEntry(page, target);
      });
    }
    for (const width of [390, 1024, 1440]) {
      await scenario(`${width}px 三入口链接无横向裁切或互相遮挡`, async page => {
        await page.setViewportSize({ width, height: 900 });
        for (const entry of entries) {
          await page.goto(configuration.baseUrl + entry.path, { waitUntil: "networkidle" });
          await assertEntry(page, entry);
          const valid = await page.getByRole("navigation", { name: "其他登录入口" }).evaluate(element => {
            const links = [...element.querySelectorAll("a")].map(link => link.getBoundingClientRect());
            const fits = links.every(rect => rect.width > 0 && rect.height > 0 && rect.left >= 0 && rect.right <= innerWidth);
            const [a, b] = links;
            return fits && (a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top)
              && document.documentElement.scrollWidth <= innerWidth;
          });
          h.requireCondition(valid, "入口链接被裁切或遮挡");
        }
      });
    }
    h.requireCondition(passed === 18, "登录导航场景没有完整执行");
    process.stdout.write("WP-0017 / login-entries：18 项通过，业务写入 0；普通成员登录、整包和 M6 未验收。\n");
  } catch (error) {
    const detail = error instanceof h.SafeAssertionError ? `；${error.message}` : "";
    throw new Error(`登录导航验收失败：${stage}${detail}；未输出输入或会话材料。`);
  } finally {
    try { await browser?.close(); }
    catch { throw new Error("登录导航验收浏览器关闭失败；未输出会话材料。"); }
  }
}

module.exports = { run };
if (require.main === module) {
  try { run(h.readConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch { process.stderr.write("登录导航验收配置无效，未输出原始配置。\n"); process.exitCode = 1; }
}
