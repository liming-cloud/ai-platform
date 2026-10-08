/* 连接 Testcontainers 中的真实应用，静态服务只转发网络，不拦截或伪造 API 响应。 */
const fs = require("node:fs");
const path = require("node:path");
const http = require("node:http");
const crypto = require("node:crypto");
const { request, requireCondition, SafeAssertionError, openLogin, fillLogin, submitLogin, assertWorkspace, logoutWorkspace } = require("./acceptance-login.cjs");
const { withTextScale } = require("./acceptance-onboarding.cjs");
const { memberLogin, verifyLayout } = require("./acceptance-member-account.cjs");
const { verifyAssistants, verifyPreview, verifyDirectorySpacing, verifyAssistantNavigation } = require("./acceptance-member-authorization.cjs");

function configuration(args) {
  requireCondition(args.length === 5, "隔离验收必须提供控制面、私有输入、目录、构建产物和独立查询应用");
  const api = new URL(args[0]);
  requireCondition(api.protocol === "http:" && api.hostname === "127.0.0.1" && Number(api.port) >= 1024 && api.pathname === "/" && !api.search && !api.hash && !api.username && !api.password, "隔离后端必须是本机随机端口");
  const directory = fs.realpathSync(args[2]); const stat = fs.statSync(directory);
  requireCondition(directory.startsWith("/private/tmp/") && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o700, "隔离结果目录必须是私有临时目录");
  const descriptor = fs.openSync(args[1], fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
  let credentials;
  try { const input = fs.fstatSync(descriptor); requireCondition(input.isFile() && input.uid === process.getuid() && (input.mode & 0o777) === 0o600, "隔离输入必须是0600普通文件"); credentials = JSON.parse(fs.readFileSync(descriptor, "utf8")); }
  finally { fs.closeSync(descriptor); }
  requireCondition(credentials.tenant?.tenantCode?.startsWith("scale-") && credentials.member?.membershipId && credentials.assistantId && credentials.memberPrefix, "隔离种子不完整");
  const dist = fs.realpathSync(args[3]); requireCondition(fs.statSync(path.join(dist, "index.html")).isFile(), "必须使用真实前端构建产物");
  const queryApi = new URL(args[4]);
  requireCondition(queryApi.protocol === "http:" && queryApi.hostname === "127.0.0.1" && Number(queryApi.port) >= 1024 && queryApi.pathname === "/" && !queryApi.search && !queryApi.hash && !queryApi.username && !queryApi.password && queryApi.port !== api.port, "查询应用必须是独立本机随机端口");
  return { api, queryApi, directory, dist, credentials };
}

/** 固定切片的只读样式事实，不采集页面正文、身份或浏览器认证材料。 */
async function measureParity(page, kind, original = false) {
  return page.evaluate(({ kind, original }) => {
    const pairs = {
      assistants: { section: ['[data-od-id="member-workspace"]', '.member-assistants-workspace'], nav: ['.object-navigation', '.member-object-navigation'], navButton: ['.object-navigation button', '.member-object-navigation button'], field: ['[data-assistant-picker] .field', '.published-assistant-picker .member-native-field'], input: ['[data-assistant-picker] input[type="search"]', '.published-assistant-picker input[type="search"]'], list: ['.assistant-choice-list', '.assistant-choice-list'], row: ['.assistant-choice-list label', '.assistant-choice-list label'], actions: ['[data-od-id="member-workspace"] .form-actions', '.member-assistants-workspace .member-form-actions'], cancel: ['[data-od-id="member-workspace"] .form-actions button', '.member-assistants-workspace .member-form-actions button'], primary: ['[data-od-id="member-workspace"] .form-actions button.primary', '.member-assistants-workspace .member-form-actions button:last-child'] },
      directory: { primary: ['.page-header > .button.primary', '.page-heading > .fui-Button'], pagination: ['.pagination', '.access-pagination'], pageSelect: ['.pagination-size select', '.access-pagination select'], pageJump: ['.pagination-jump input', '.access-pagination input'], pageButton: ['.pagination-actions button', '.access-pagination .member-pagination-pages button'], toolbar: ['.toolbar', '.policy-directory-toolbar'], input: ['.toolbar input', '.policy-directory-toolbar input'], select: ['.toolbar select', '.policy-directory-toolbar select'], table: ['.data-table', '.policy-directory-table table'], heading: ['.data-table th', '.policy-directory-table th'] },
      detail: { back: ['.page-header > .button', '.page-heading > .fui-Button'], status: ['#access-policy-form .status-tag', '.policy-section-heading .member-status'], note: ['.page-content > .inline-note', '.access-policy-workspace > .policy-inline-note'], split: ['.split-detail', '.policy-split-detail'], editor: ['#access-policy-form', '.policy-editor'], heading: ['.section-heading', '.policy-section-heading'], field: ['#access-policy-form .field', '.policy-form-grid .member-native-field'], select: ['#access-policy-form select', '.policy-form-grid select'], readonly: ['.readonly-value', '.policy-readonly-value'], members: ['.access-policy-members .feature-grid', '.policy-member-grid'], member: ['.access-policy-members .feature-control', '.policy-member-grid > label'], aside: ['.release-decision', '.policy-release-decision'], summary: ['.summary-strip', '.policy-summary-strip'], summaryItem: ['.summary-item', '.policy-summary-strip dl'], timeline: ['.timeline li', '.policy-timeline li'], actions: ['#access-policy-form .form-actions', '.policy-editor .member-form-actions'], cancel: ['#access-policy-form .form-actions button', '.policy-editor .member-form-actions button'], primary: ['.release-decision button.primary', '.policy-release-decision > button'], facts: ['.release-decision .description-list', '.policy-release-decision .member-account-facts'] },
      dialog: { surface: ['dialog[open]', '[role="dialog"]'], heading: ['dialog[open] h2', '[role="dialog"] h2'], actions: ['dialog[open] .dialog-actions', '[role="dialog"] .member-dialog-actions'] },
    };
    const group = kind === 'assistants' ? 'assistants' : kind === 'directory' ? 'directory' : kind === 'unsaved' || kind === 'create-dialog' ? 'dialog' : 'detail';
    if (kind === 'create-dialog') pairs.dialog.actions = ['dialog[open] .dialog-actions', '[role="dialog"] .member-form-actions'];
    const selectors = { body: ['main', 'main'], ...(group === 'dialog' ? {} : { title: ['h1', 'h1'] }), ...pairs[group] };
    const keys = ['display', 'fontFamily', 'fontSize', 'fontWeight', 'lineHeight', 'color', 'backgroundColor', 'padding', 'margin', 'gap', 'borderColor', 'borderWidth', 'borderRadius', 'minHeight', 'minWidth', 'maxWidth', 'alignItems', 'flexWrap', 'boxShadow'];
    return Object.fromEntries(Object.entries(selectors).map(([name, choices]) => {
      const element = document.querySelector(choices[original ? 0 : 1]);
      if (!element) return [name, null];
      const rect = element.getBoundingClientRect();
      return [name, { ...Object.fromEntries(keys.map(key => [key, getComputedStyle(element)[key]])), bounds: { x: rect.x, y: rect.y, width: rect.width, height: rect.height } }];
    }));
  }, { kind, original });
}

async function run(config) {
  const { api, queryApi, directory, dist, credentials } = config;
  const server = http.createServer((incoming, outgoing) => {
    if (incoming.url.startsWith("/api/")) {
      const target = incoming.url.startsWith("/api/v1/query-assistants") ? queryApi : api;
      const proxy = http.request({ hostname: target.hostname, port: target.port, path: incoming.url, method: incoming.method, headers: incoming.headers }, response => {
        outgoing.writeHead(response.statusCode, response.headers); response.pipe(outgoing);
      });
      proxy.on("error", () => { outgoing.writeHead(502); outgoing.end(); }); incoming.pipe(proxy); return;
    }
    let file;
    try { const name = decodeURIComponent(new URL(incoming.url, "http://localhost").pathname); file = path.resolve(dist, "." + name); }
    catch { outgoing.writeHead(400); outgoing.end(); return; }
    if (!file.startsWith(dist + path.sep)) file = path.join(dist, "index.html");
    if (!fs.existsSync(file) || !fs.statSync(file).isFile()) file = path.join(dist, "index.html");
    const mime = { ".js": "text/javascript", ".css": "text/css", ".html": "text/html", ".svg": "image/svg+xml", ".png": "image/png" }[path.extname(file)] || "application/octet-stream";
    outgoing.writeHead(200, { "Content-Type": mime }); fs.createReadStream(file).pipe(outgoing);
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  const baseUrl = `http://127.0.0.1:${server.address().port}`; const loginConfig = { baseUrl, credentials };
  let browser; let stage = "启动隔离浏览器"; let errors = 0; const results = [];
  const check = async (name, action) => { stage = name; await action(); results.push({ assertion: name, result: "通过" }); process.stdout.write(JSON.stringify(results.at(-1)) + "\n"); };
  try {
    requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定的 Playwright 运行时");
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
    const page = await context.newPage(); page.setDefaultTimeout(20_000); page.on("pageerror", () => errors++);
    const login = async () => { await openLogin(page, loginConfig, "tenant"); await fillLogin(page, credentials.tenant, "tenant"); await submitLogin(page); await assertWorkspace(page, "tenant"); };
    const memberPath = `/tenant/members/${credentials.member.membershipId}/assistants`;
    const readMember = async (id = credentials.member.membershipId) => { const value = await request(page, `/tenant/members/${id}/assistants`); requireCondition(value.status === 200, "隔离助手读取失败"); verifyAssistants(value.body); return value.body; };
    const directMember = async target => { await target.goto(baseUrl + memberPath, { waitUntil: "networkidle" }); await target.getByRole("heading", { name: credentials.member.loginName, exact: true }).waitFor(); await target.locator(".assistant-choice-list label").first().waitFor(); };
    const waitCommand = (target, method, suffix) => target.waitForResponse(response => response.request().method() === method && new URL(response.url()).pathname.endsWith(suffix));
    const saveMember = async target => { const sent = waitCommand(target, "PUT", memberPath); await target.getByRole("button", { name: "保存可用助手", exact: true }).click(); return sent; };
    const capture = async name => {
      if (name === "assistants") {
        const style = await page.locator(".member-assistants-workspace").evaluate(element => ({ width: getComputedStyle(element).maxWidth, layout: getComputedStyle(element.querySelector(".assistant-choice-list")).display }));
        requireCondition(style.width === "896px" && style.layout === "grid", "助手独立入口未加载原型样式");
      }
      for (const width of [390, 1024, 1440]) {
        await page.setViewportSize({ width, height: 960 });
        for (const enlarged of [false, true]) await withTextScale(page, enlarged, async () => {
          await page.evaluate(async () => { await document.fonts.ready; await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))); });
          await verifyLayout(page); await page.evaluate(() => { document.activeElement?.blur(); scrollTo(0, 0); document.querySelectorAll('.assistant-choice-list').forEach(list => { list.scrollTop = 0; }); document.querySelector('[role="dialog"]')?.scrollTo(0, 0); });
          if (name === "draft" || name === "preview") {
            const badge = await page.locator(".policy-section-heading .member-status").evaluate(element => ({ color: getComputedStyle(element).color, background: getComputedStyle(element).backgroundColor, radius: getComputedStyle(element).borderRadius }));
            requireCondition(badge.color === "rgb(61, 61, 58)" && badge.background === "rgb(245, 244, 237)" && badge.radius === "9999px", "草稿状态标签未使用原型信息色");
          }
          const clippedCounts = await page.locator('.policy-summary-strip dd').evaluateAll(values => values.filter(value =>
            value.scrollWidth > value.clientWidth + 1 || value.scrollHeight > value.clientHeight + 1).length);
          requireCondition(clippedCounts === 0, "真实策略预览计数被省略或裁切");
          if (name === "directory") await verifyDirectorySpacing(page);
          if (name === "assistants") await verifyAssistantNavigation(page);
          await page.mouse.move(0, 0);
          const filename = path.join(directory, `isolated-${name}-${width}-${enlarged ? 200 : 100}-${crypto.randomBytes(3).toString("hex")}.png`);
          fs.writeFileSync(filename.replace(/\.png$/, ".json"), JSON.stringify({ kind: name, width, enlarged, metrics: await measureParity(page, name) }), { mode: 0o600, flag: "wx" });
          await page.screenshot({ path: filename, fullPage: true, mask: [page.locator('input[type="password"]')] });
        }, name === "unsaved" || name === "create-dialog" ? '[role="dialog"]' : 'main');
      }
      await page.setViewportSize({ width: 1440, height: 960 });
    };
    await check("真实千成员二百助手目录及范围计数", async () => {
      await login(); await page.goto(baseUrl + "/tenant/access-policies?page=1", { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "访问策略目录", exact: true }).waitFor();
      requireCondition((await page.locator(".access-policy-directory > .policy-feedback").first().textContent()) === "", "目录初始态新增了原型不存在的辅助文案");
      await page.setViewportSize({ width: 390, height: 960 });
      // 复用布局稳定检查，不能在媒体查询尚未绘制时比较新旧视口的边界。
      await withTextScale(page, false, async () => {
        const action = await page.locator(".page-heading > button").boundingBox(); const heading = await page.locator(".page-heading").boundingBox();
        requireCondition(action && heading && Math.abs(action.width - heading.width) <= .01, "策略目录小屏主要操作未按原型占满行宽");
        requireCondition((await page.locator(".member-pagination select").evaluate(element => getComputedStyle(element).fontSize)) === "16px", "策略目录页大小字号未承接原型");
      });
      await page.setViewportSize({ width: 1440, height: 960 });
      const members = await request(page, "/tenant/access-policies/member-options?pageNumber=50&pageSize=20"); const assistants = await request(page, "/tenant/member-assistants?pageNumber=10&pageSize=20");
      requireCondition(members.body?.totalElements === 1000 && members.body.items.length === 20 && !members.body.hasNext, "千成员分页不是真实完整事实");
      requireCondition(assistants.body?.totalElements === 200 && assistants.body.items.length === 20 && !assistants.body.hasNext, "二百助手分页不是真实完整事实");
    });
    const priorMember = await memberLogin(browser, loginConfig, credentials.member);
    await check("助手跨分页跨搜索保留选择刷新恢复并独立保存", async () => {
      await directMember(page); const picker = page.locator(".published-assistant-picker");
      await picker.getByRole("checkbox").first().check(); await picker.getByRole("button", { name: "下一页", exact: true }).click(); await picker.getByText("第 2 / 10 页", { exact: true }).waitFor();
      await picker.getByRole("checkbox").first().check(); await picker.getByRole("searchbox").fill(`合成权限助手-${credentials.assistantId}`);
      await picker.getByText("匹配 1 个，已选 2 个", { exact: true }).waitFor(); await picker.getByRole("checkbox").check();
      await page.reload({ waitUntil: "networkidle" }); await page.getByText("已恢复本页未保存修改。", { exact: true }).waitFor();
      await page.getByText("匹配 200 个，已选 3 个；请输入更完整名称缩小范围", { exact: true }).waitFor();
      requireCondition((await saveMember(page)).status() === 200, "助手界面保存失败");
      const facts = await readMember(); requireCondition(facts.assistantIds.length === 3 && facts.assistantIds.includes(credentials.assistantId) && facts.authorizationRevision === 1, "跨页搜索选择丢失或版本错误");
      requireCondition((await request(priorMember.page, "/auth/browser-session")).status === 401, "助手保存未撤销旧资格会话"); await priorMember.context.close(); await capture("assistants");
    });
    const oldTab = await context.newPage(); await directMember(oldTab); let policy;
    await check("助手未保存离开保留焦点放弃取消及退出清理", async () => {
      const box = page.locator(".assistant-choice-list input").first(); await box.uncheck();
      await page.getByRole("button", { name: "账号信息", exact: true }).click(); const dialog = page.getByRole("dialog");
      await dialog.getByRole("heading", { name: "修改尚未保存", exact: true }).waitFor(); await capture("unsaved");
      await dialog.getByRole("button", { name: "保留当前", exact: true }).click(); await dialog.waitFor({ state: "hidden" });
      requireCondition(new URL(page.url()).pathname === memberPath, "保留仍离开助手页");
      await page.getByRole("button", { name: "账号信息", exact: true }).click(); await dialog.getByRole("button", { name: "放弃修改并离开", exact: true }).click(); await page.locator(".member-account-facts").waitFor();
      await page.getByRole("button", { name: "可用助手", exact: true }).click(); await page.getByText("匹配 200 个，已选 3 个；请输入更完整名称缩小范围", { exact: true }).waitFor();
      await page.locator(".assistant-choice-list input").first().uncheck(); await logoutWorkspace(page); await login(); await directMember(page);
      await page.getByText("匹配 200 个，已选 3 个；请输入更完整名称缩小范围", { exact: true }).waitFor();
    });
    const secondId = String(BigInt(credentials.member.membershipId) + 999n); const secondName = credentials.memberPrefix + "1000";
    const secondBefore = await memberLogin(browser, loginConfig, { loginName: secondName, password: credentials.member.password });
    await check("创建真实策略草稿跨搜索选择成员且不隐式赋权", async () => {
      await page.goto(baseUrl + "/tenant/access-policies?page=1", { waitUntil: "networkidle" }); await page.getByRole("heading", { name: "访问策略目录", exact: true }).waitFor(); await capture("directory");
      await page.getByRole("button", { name: "新建访问策略", exact: true }).click(); const dialog = page.getByRole("dialog");
      await dialog.getByLabel("策略名称", { exact: true }).fill("浏览器统一授权策略");
      const picker = dialog.locator(".published-assistant-picker");
      await picker.locator('select:not(:disabled)').waitFor();
      requireCondition(Boolean(await picker.locator("select").inputValue()), "新建策略未按原型预选首个真实已发布助手");
      await picker.getByRole("searchbox").fill(`合成权限助手-${credentials.assistantId}`);
      await picker.locator(`option[value="${credentials.assistantId}"]`).waitFor({ state: "attached" }); await picker.locator("select").selectOption(credentials.assistantId);
      const members = dialog.locator(".policy-member-options"); await members.getByRole("searchbox").fill(credentials.member.loginName);
      await members.getByRole("checkbox").first().waitFor();
      for (let number = 1; number < 50 && await members.getByRole("checkbox", { name: credentials.member.loginName, exact: true }).count() === 0; number++) {
        const loaded = page.waitForResponse(response => response.request().method() === "GET" && new URL(response.url()).pathname.endsWith("/member-options"));
        requireCondition(await members.getByRole("button", { name: "下一页", exact: true }).isEnabled(), "筛选结果缺少目标普通成员");
        await members.getByRole("button", { name: "下一页", exact: true }).click(); await loaded;
        await members.getByText(new RegExp(`^第 ${number + 1} / [0-9]+ 页$`)).waitFor();
      }
      await members.getByRole("checkbox", { name: credentials.member.loginName, exact: true }).check();
      await members.getByRole("searchbox").fill(secondName); await members.getByRole("checkbox", { name: secondName, exact: true }).check();
      await members.getByText("匹配 1 个，已选 2 个", { exact: true }).waitFor(); await capture("create-dialog");
      const sent = waitCommand(page, "POST", "/tenant/access-policies"); await dialog.getByRole("button", { name: "创建策略草稿", exact: true }).click(); const response = await sent;
      requireCondition(response.status() === 201, "真实策略草稿创建失败"); policy = await response.json();
      await page.getByRole("heading", { name: "浏览器统一授权策略 v1", exact: true }).waitFor(); requireCondition(policy.status === "DRAFT" && !policy.preview, "新草稿伪造预览或直接发布");
      requireCondition((await readMember(secondId)).assistantIds.length === 0, "草稿隐式赋权"); await capture("draft");
    });
    await check("真实预览发布与受影响会话撤销旧编辑冲突", async () => {
      const previewed = waitCommand(page, "POST", "/preview"); await page.getByRole("button", { name: "生成授权预览", exact: true }).click(); const preview = await previewed;
      requireCondition(preview.status() === 200, "授权预览失败"); policy = await preview.json(); verifyPreview(policy.preview);
      requireCondition(policy.preview.allowCount === 8 && policy.preview.denyCount === 3992, "预览不是千成员四项真实判定"); await capture("preview");
      const published = waitCommand(page, "POST", "/publish"); await page.getByRole("button", { name: "发布策略版本", exact: true }).click(); const response = await published;
      requireCondition(response.status() === 200, "真实策略发布失败"); policy = await response.json();
      requireCondition((await readMember(secondId)).assistantIds.join() === credentials.assistantId && (await request(secondBefore.page, "/auth/browser-session")).status === 401, "策略与成员助手事实分叉或旧会话仍可用"); await secondBefore.context.close();
      await oldTab.locator(".assistant-choice-list input").first().uncheck(); requireCondition((await saveMember(oldTab)).status() === 409, "策略发布后旧助手草稿覆盖新授权"); await oldTab.close(); await capture("published");
    });
    await check("成员页撤权后旧发布不能重放新草稿不生效且必须重新预览", async () => {
      const before = await readMember(); requireCondition((await request(page, memberPath, "PUT", { assistantIds: [], expectedAuthorizationRevision: before.authorizationRevision })).status === 200, "成员页撤权失败");
      const versionPath = `/tenant/access-policies/${policy.policyId}/versions/${policy.versionId}`;
      requireCondition((await request(page, versionPath + "/publish", "POST", { expectedVersion: policy.version, expectedAuthorizationRevision: policy.authorizationRevision, expectedTenantFeatureVersion: policy.tenantFeatureVersion })).status === 409, "旧发布复活授权");
      const created = waitCommand(page, "POST", `/tenant/access-policies/${policy.policyId}/versions`); await page.getByRole("button", { name: "创建策略新版本", exact: true }).click(); const newVersionResponse = await created; requireCondition(newVersionResponse.status() === 201, "策略新版本创建失败");
      const newVersion = await newVersionResponse.json(); await page.getByRole("heading", { name: "浏览器统一授权策略 v2", exact: true }).waitFor();
      requireCondition(new URL(page.url()).pathname.endsWith(`/versions/${newVersion.versionId}`), "新版本正文与 URL 不一致"); requireCondition((await readMember()).assistantIds.length === 0, "新草稿自动恢复已撤权成员");
      requireCondition(await page.getByRole("button", { name: "发布策略版本", exact: true }).isDisabled(), "新版本跳过预览");
      const saved = waitCommand(page, "PUT", "/versions/" + new URL(page.url()).pathname.split("/").at(-1)); await page.getByRole("button", { name: "保存草稿", exact: true }).click(); const savedResponse = await saved;
      if (savedResponse.status() !== 200) {
        const submitted = savedResponse.request().postDataJSON(); const currentVersion = (await request(page, `/tenant/access-policies/${policy.policyId}/versions/${new URL(page.url()).pathname.split("/").at(-1)}`)).body;
        process.stdout.write(JSON.stringify({ stage: "新草稿保存失败的非秘密版本诊断", status: savedResponse.status(), fields: Object.keys(submitted),
          submittedVersion: submitted.expectedVersion, submittedRevision: submitted.expectedAuthorizationRevision, submittedCeiling: submitted.expectedTenantFeatureVersion,
          currentVersion: currentVersion.version, currentRevision: currentVersion.authorizationRevision, currentCeiling: currentVersion.tenantFeatureVersion,
          memberCount: submitted.membershipIds?.length }) + "\n");
      }
      requireCondition(savedResponse.status() === 200, "草稿保存失败");
      const previewed = waitCommand(page, "POST", "/preview"); await page.getByRole("button", { name: "生成授权预览", exact: true }).click(); requireCondition((await previewed).status() === 200, "新版本真实预览失败");
      const published = waitCommand(page, "POST", "/publish"); await page.getByRole("button", { name: "发布策略版本", exact: true }).click(); const response = await published; requireCondition(response.status() === 200, "新预览发布失败");
      const next = await response.json(); await page.getByRole("button", { name: "创建策略新版本", exact: true }).waitFor();
      await page.getByRole("combobox", { name: "策略版本", exact: true }).locator(`option[value="${next.versionId}"]`).filter({ hasText: "已发布" }).waitFor({ state: "attached" });
      await page.getByRole("combobox", { name: "策略版本", exact: true }).selectOption(policy.versionId);
      await page.getByRole("heading", { name: "浏览器统一授权策略 v1", exact: true }).waitFor(); requireCondition(await page.getByRole("button", { name: "保存草稿", exact: true }).isDisabled(), "历史版本可以修改"); requireCondition((await page.locator(".policy-section-heading .member-status").textContent()) === "已保留", "历史版本状态文案偏离原型");
      requireCondition((await readMember()).assistantIds.join() === credentials.assistantId, "新授权没有写回唯一事实");
      await page.goto(`${baseUrl}/tenant/access-policies/${next.policyId}/versions/${next.versionId}`, { waitUntil: "networkidle" }); await page.getByRole("heading", { name: "浏览器统一授权策略 v2", exact: true }).waitFor();
    });
    await check("当前普通成员目录只有明确授权的发布版本且没有脚本错误", async () => {
      const ordinary = await memberLogin(browser, loginConfig, credentials.member); const choices = await request(ordinary.page, "/query-assistants?pageNumber=1&pageSize=20");
      requireCondition(choices.status === 200 && choices.body.totalElements === 1 && choices.body.items[0].assistantId === credentials.assistantId, "普通成员目录与统一授权不一致");
      requireCondition((await request(ordinary.page, "/tenant/access-policies")).status === 403 && errors === 0, "普通成员越权或页面发生脚本错误"); await ordinary.context.close();
    });
    fs.writeFileSync(path.join(directory, `isolated-browser-facts-${crypto.randomBytes(6).toString("hex")}.json`), JSON.stringify({ scope: "WP-0018 助手／策略隔离验收，不证明生产助手发布或问答旅程", result: "VERIFIED", members: 1000, assistants: 200, results }), { mode: 0o600, flag: "wx" });
    process.stdout.write("隔离真实浏览器与规模门禁通过；未运行真实问答／历史／引用正向旅程。\n");
  } catch (error) { throw new Error(`隔离统一授权验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；证据保留在独占临时目录。`); }
  finally { await browser?.close(); await new Promise(resolve => server.close(resolve)); }
}
module.exports = { configuration, measureParity };
if (require.main === module) { try { run(configuration(process.argv.slice(2))).catch(error => { process.stderr.write(error.message + "\n"); process.exitCode = 1; }); } catch (error) { process.stderr.write("隔离验收固定输入校验失败。\n"); process.exitCode = 1; } }
