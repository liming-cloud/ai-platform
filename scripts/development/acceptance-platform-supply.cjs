/* WP-0019 S2：只读与单次合成草稿明确分离，不发起推理、发布或生效赋权。 */
const fs = require("node:fs");
const path = require("node:path");
const { readConfiguration, requireCondition, SafeAssertionError, request, openLogin, fillLogin, submitLogin, assertIdentity } = require("./acceptance-login.cjs");
const { inspectRuntime, validateOutputFile, assertSafeResponse } = require("./acceptance-platform-connection.cjs");
const { createBrowserTestCache } = require("../quality/browser-test-cache.cjs");
const { enlargeText } = require("./acceptance-overview.cjs");
const api = "/platform/model-supply/deployments";
const prototype = { projectId: "249f81f2-6f1a-46ff-988c-da3cdedb35e7", revision: "f3216dffe717e194e96fa20b4afee7983a9ff420" };
const longId = value => typeof value === "string" && /^[1-9][0-9]{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;

/** 只读取显式材料；不允许把发现授权或旧发布授权扩展成真实推理和新赋权。 */
function configuration(environment = process.env) {
  requireCondition(!environment.SETUP, "S2 不接受外部 SETUP");
  const mode = environment.FOUNDATION_ACCEPTANCE_MODE ?? "read-only";
  requireCondition(["read-only", "create-draft"].includes(mode), "S2 当前只允许 read-only 或 create-draft；真实推理、发布和赋权另行确认");
  const revisions = [environment.FOUNDATION_ACCEPTANCE_FRONTEND_REVISION, environment.FOUNDATION_ACCEPTANCE_BACKEND_REVISION];
  requireCondition(revisions.every(value => typeof value === "string" && /^[a-f0-9]{40}$/.test(value)), "必须分别绑定前后端精确运行提交");
  const base = readConfiguration(environment);
  requireCondition(!base.setup, "S2 不能隐式创建租户");
  validateOutputFile(base.outputFile, true);
  let input;
  if (mode === "create-draft") input = readDraftInput(environment.FOUNDATION_ACCEPTANCE_INPUT_FILE);
  else requireCondition(!environment.FOUNDATION_ACCEPTANCE_INPUT_FILE, "只读模式不读取创建材料");
  const targetId = environment.FOUNDATION_ACCEPTANCE_DEPLOYMENT_ID;
  requireCondition(targetId === undefined || (mode === "read-only" && longId(targetId)), "只读精确目标不正确");
  return { ...base, mode, input, targetId, frontendRevision: revisions[0], backendRevision: revisions[1] };
}

/** 非秘密的合成声明仍用独占文件，不能覆盖历史验收材料。 */
function readDraftInput(file) {
  let descriptor, input;
  try {
    requireCondition(typeof file === "string" && path.isAbsolute(file), "草稿模式必须指定材料");
    descriptor = fs.openSync(file, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
    const stat = fs.fstatSync(descriptor);
    requireCondition(stat.isFile() && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o600 && stat.size <= 16384, "草稿材料必须是当前用户的普通 0600 文件");
    input = JSON.parse(fs.readFileSync(descriptor, "utf8"));
  } catch { throw new SafeAssertionError("草稿材料不可用"); }
  finally { if (descriptor !== undefined) fs.closeSync(descriptor); }
  const keys = ["connectionId", "connectionRevision", "modelCode", "displayName", "modelType", "contextWindow", "outputLimit", "embeddingDimension", "rerankCandidateLimit", "parameterSource", "plannedTenantIds"];
  requireCondition(input && !Array.isArray(input) && Object.keys(input).every(key => keys.includes(key))
    && longId(input.connectionId) && Number.isSafeInteger(input.connectionRevision) && input.connectionRevision >= 0
    && typeof input.modelCode === "string" && input.modelCode.length > 0 && ["CHAT", "EMBEDDING", "RERANK"].includes(input.modelType)
    && typeof input.displayName === "string" && Array.from(input.displayName).length >= 2 && Array.from(input.displayName).length <= 80
    && typeof input.parameterSource === "string" && input.parameterSource.length > 0 && input.parameterSource.length <= 160
    && Array.isArray(input.plannedTenantIds) && input.plannedTenantIds.length > 0 && input.plannedTenantIds.every(longId), "草稿材料字段或目标不正确");
  const positive = value => Number.isInteger(value) && value > 0 && value <= 2147483647;
  requireCondition(positive(input.contextWindow) && (input.modelType === "CHAT" ? positive(input.outputLimit) && input.outputLimit <= input.contextWindow
    : input.modelType === "EMBEDDING" ? positive(input.embeddingDimension) : positive(input.rerankCandidateLimit)), "草稿参数不正确");
  return input;
}

/** 浏览器只可写登录／注销及本轮显式草稿，禁止任何推理、发布和隐式租户操作。 */
function permittedMutation(method, pathname, mode, armed, created) {
  if (["GET", "HEAD", "OPTIONS"].includes(method)) return true;
  if (method === "POST" && pathname === "/api/v1/auth/browser-sessions") return true;
  if (method === "DELETE" && pathname === "/api/v1/auth/browser-session") return true;
  return mode === "create-draft" && armed && created === 0 && method === "POST" && pathname === `/api/v1${api}`;
}

async function run(config) {
  const { chromium } = require("playwright");
  const runtime = inspectRuntime(config);
  const output = config.outputFile;
  const directory = path.dirname(output);
  let browser, context, cache, descriptor, stage = "初始化", blocked = 0, pageErrors = 0, armed = false, writes = 0;
  const report = { scope: "WP-0019/S2 草稿与精确详情", prototype, runtime, mode: config.mode, checks: [], observations: [], blockedWrites: 0 };
  const secrets = [config.credentials.platform.password, config.credentials.tenant.password];
  const persist = () => { assertSafeResponse(report, secrets); fs.ftruncateSync(descriptor, 0); fs.writeSync(descriptor, JSON.stringify(report, null, 2) + "\n", 0, "utf8"); fs.fsyncSync(descriptor); };
  const check = async (name, action) => { stage = name; await action(); requireCondition(!blocked && !pageErrors, "发现范围外请求或页面异常"); report.checks.push(name); persist(); process.stdout.write(JSON.stringify({ assertion: name, result: "通过" }) + "\n"); };
  try {
    descriptor = fs.openSync(output, "wx+", 0o600);
    cache = createBrowserTestCache(directory, async () => { try { await context?.close(); } finally { await browser?.close(); } });
    browser = await chromium.launch({ channel: "chrome", headless: true, args: ["--disk-cache-dir=" + path.join(cache.cacheDir, "chrome-cache")] });
    context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, reducedMotion: "reduce" });
    const page = await context.newPage();
    page.on("pageerror", () => { pageErrors++; });
    await context.route("**/api/v1/**", async route => {
      const sent = route.request(), pathname = new URL(sent.url()).pathname;
      if (!permittedMutation(sent.method(), pathname, config.mode, armed, writes)) { blocked++; report.blockedWrites = blocked; await route.abort(); return; }
      if (sent.method() === "POST" && pathname === `/api/v1${api}`) writes++;
      await route.continue();
    });
    await check("当前部署平台会话", async () => { await openLogin(page, config, "platform"); await fillLogin(page, config.credentials.platform, "platform"); await submitLogin(page); await assertIdentity(page, "platform"); });
    await check("部署目录与独立安全事实", async () => {
      await page.goto(config.baseUrl + "/platform/model-supply/deployments", { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "平台模型部署", exact: true }).waitFor();
      const navigation = page.getByRole("navigation", { name: "对象工作区", exact: true });
      requireCondition(await navigation.getByRole("button", { name: "平台部署", exact: true }).getAttribute("aria-current") === "page", "部署页签未绑定当前对象工作区");
      await navigation.getByRole("button", { name: "模型连接", exact: true }).click();
      await page.getByRole("heading", { name: "平台模型连接", exact: true }).waitFor();
      await page.getByRole("navigation", { name: "对象工作区", exact: true }).getByRole("button", { name: "平台部署", exact: true }).click();
      await page.getByRole("heading", { name: "平台模型部署", exact: true }).waitFor();
      const facts = await request(page, api + "/facts"); const list = await request(page, api);
      requireCondition(facts.status === 200 && typeof facts.body?.writesAvailable === "boolean" && list.status === 200 && Array.isArray(list.body?.items), "正式部署事实读取失败");
      report.before = { total: list.body.totalElements, writesAvailable: facts.body.writesAvailable };
      assertSafeResponse(facts.body, secrets); assertSafeResponse(list.body, secrets);
      if (list.body.totalElements === 0) {
        await page.getByRole("region", { name: "空状态", exact: true }).waitFor();
        const screenshot = path.join(directory, "unfiltered-empty-directory.png");
        await page.screenshot({ path: screenshot, fullPage: true }); fs.chmodSync(screenshot, 0o600);
        report.emptyDirectory = { unfiltered: true, total: 0, screenshot };
      }
    });
    let options, tenants;
    await check("完整表单无默认参数且取消后清空", async () => {
      await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
      const modal = page.getByRole("dialog"); await modal.getByRole("button", { name: "保存部署草稿", exact: true }).waitFor({ state: "visible" });
      await modal.getByLabel("已发现模型", { exact: true }).waitFor();
      await page.waitForFunction(() => { const button = document.querySelector('.platform-deployment-form button[type="submit"]'); return button && !button.disabled; });
      requireCondition(await modal.getByLabel("部署名称", { exact: true }).inputValue() === "" && await modal.getByLabel("已发现模型", { exact: true }).inputValue() === ""
        && await modal.locator('input[type="checkbox"]:checked').count() === 0, "表单预选了模型或租户");
      await modal.getByLabel("部署名称", { exact: true }).fill("取消的合成草稿"); await page.keyboard.press("Escape"); await modal.waitFor({ state: "hidden" });
      await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
      requireCondition(await page.getByRole("dialog").getByLabel("部署名称", { exact: true }).inputValue() === "", "取消后保留了旧声明");
      await page.keyboard.press("Escape");
      options = (await request(page, api + "/candidates?pageNumber=1&pageSize=100")).body;
      tenants = (await request(page, "/platform/tenants?pageNumber=1&pageSize=100&status=ACTIVE")).body;
      requireCondition(Array.isArray(options?.items) && Array.isArray(tenants?.items), "草稿选择事实不可用");
    });
    if (config.mode === "create-draft") await check("正式表单保存精确草稿且不验证发布赋权", async () => {
      const input = config.input;
      const selected = options.items.findIndex(item => item.connectionId === input.connectionId && item.connectionRevision === input.connectionRevision && item.modelCode === input.modelCode
        && (item.modelType === input.modelType || (item.modelType === null && item.declaredCapability === input.modelType)));
      requireCondition(selected >= 0 && config.input.plannedTenantIds.every(id => tenants.items.some(tenant => tenant.tenantId === id && tenant.status === "ACTIVE")), "精确候选或活动租户已变化");
      await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
      const modal = page.getByRole("dialog");
      await page.waitForFunction(() => !document.querySelector('.platform-deployment-form button[type="submit"]')?.disabled);
      await modal.getByLabel("部署名称", { exact: true }).fill(input.displayName); await modal.getByLabel("已发现模型", { exact: true }).selectOption(String(selected));
      if (options.items[selected].modelType === null) await modal.getByLabel("模型类型", { exact: true }).selectOption(input.modelType);
      await modal.getByLabel("上下文窗口（Token）", { exact: true }).fill(String(input.contextWindow));
      const extra = input.modelType === "CHAT" ? ["最大输出（Token）", input.outputLimit] : input.modelType === "EMBEDDING" ? ["向量维度", input.embeddingDimension] : ["重排候选上限", input.rerankCandidateLimit];
      await modal.getByLabel(extra[0], { exact: true }).fill(String(extra[1])); await modal.getByLabel("参数来源", { exact: true }).fill(input.parameterSource);
      await modal.getByLabel("我已确认模型类型、能力和参数来源", { exact: true }).check();
      for (const tenantId of input.plannedTenantIds) {
        const name = tenants.items.find(tenant => tenant.tenantId === tenantId).displayName;
        await modal.getByRole("group", { name: "授权租户", exact: true }).getByLabel(name, { exact: true }).check();
      }
      armed = true;
      const response = page.waitForResponse(response => new URL(response.url()).pathname === `/api/v1${api}` && response.request().method() === "POST");
      await modal.getByRole("button", { name: "保存部署草稿", exact: true }).click();
      const saved = await response; armed = false;
      requireCondition(saved.status() === 201, "正式保存草稿失败");
      const record = await saved.json(); assertSafeResponse(record, secrets);
      requireCondition(longId(record.id) && record.status === "DRAFT" && record.version === 0 && record.verifiedAt === null && record.verificationExpiresAt === null
        && record.providerConnectionId === input.connectionId && record.providerConnectionRevision === input.connectionRevision
        && record.modelCode === input.modelCode && record.modelType === input.modelType && record.contextWindow === input.contextWindow
        && record.outputLimit === (input.outputLimit ?? null) && record.embeddingDimension === (input.embeddingDimension ?? null)
        && record.inputs?.rerankCandidateLimit === (input.rerankCandidateLimit ?? null)
        && JSON.stringify([...record.inputs.plannedTenantIds].sort()) === JSON.stringify([...input.plannedTenantIds].sort())
        && record.inputs.parameterSource === input.parameterSource, "保存结果不是明确草稿声明");
      await page.waitForURL(url => url.pathname === `/platform/model-supply/deployments/${record.id}`);
      await page.getByRole("heading", { name: input.displayName, exact: true }).waitFor();
      const exact = await request(page, api + "/" + record.id);
      requireCondition(exact.status === 200 && exact.body.id === record.id && exact.body.inputs.discoveryId === record.inputs.discoveryId, "精确详情未恢复冻结来源");
      report.created = { id: record.id, connectionId: record.providerConnectionId, connectionRevision: record.providerConnectionRevision,
        discoveryId: record.inputs.discoveryId, modelType: record.modelType, plannedTenantIds: record.inputs.plannedTenantIds, status: record.status };
      await page.reload({ waitUntil: "networkidle" }); await page.getByRole("heading", { name: input.displayName, exact: true }).waitFor();
      requireCondition((await page.locator("dl").innerText()).includes(input.parameterSource), "刷新后声明丢失");
      await page.getByRole("button", { name: "返回部署目录", exact: true }).first().click();
      await page.getByRole("button", { name: "查看" + input.displayName, exact: true }).click();
      await page.getByRole("heading", { name: input.displayName, exact: true }).waitFor();
    });
    const targetId = report.created?.id ?? config.targetId;
    const targets = [{ view: "directory", url: "/platform/model-supply/deployments" }];
    let previewSource = config.input;
    if (targetId) {
      const target = await request(page, api + "/" + targetId);
      requireCondition(target.status === 200 && target.body?.id === targetId, "只读精确目标不存在");
      previewSource = { connectionId: target.body.providerConnectionId, connectionRevision: target.body.providerConnectionRevision,
        modelCode: target.body.modelCode, modelType: target.body.modelType };
      targets.push({ view: "detail", url: "/platform/model-supply/deployments/" + targetId }); report.targetId = targetId;
    }
    await check("三尺寸原生文字与只读布局", async () => {
      for (const target of targets) for (const width of [390, 1024, 1440]) for (const textScale of [1, 2]) {
        await page.setViewportSize({ width, height: 1000 }); await page.goto(config.baseUrl + target.url, { waitUntil: "networkidle" });
        await page.locator('.platform-deployment-page[data-read-state="ready"]').waitFor();
        if (textScale === 2) { await enlargeText(page); await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))); }
        const observed = await layout(page);
        requireCondition(!observed.overflow && observed.clipped.length === 0, "S2 页面存在裁切或横向溢出");
        const screenshot = path.join(directory, `${target.view}-${width}-${textScale}.png`);
        await page.screenshot({ path: screenshot, fullPage: true }); fs.chmodSync(screenshot, 0o600);
        report.observations.push({ view: target.view, width, textScale, ...observed, screenshot,
          headingStyle: await page.locator("h1").first().evaluate(styleFacts) });
      }
    });
    if (previewSource) await check("生产完整表单原生文字、固定操作区与键盘", async () => {
      const current = await request(page, api + "/candidates?pageNumber=1&pageSize=100");
      const index = current.body?.items?.findIndex(item => item.connectionId === previewSource.connectionId
        && item.connectionRevision === previewSource.connectionRevision && item.modelCode === previewSource.modelCode);
      requireCondition(Number.isInteger(index) && index >= 0, "用于只读表单观察的精确来源已不可用");
      for (const width of [390, 1024, 1440]) for (const textScale of [1, 2]) {
        await page.setViewportSize({ width, height: 1000 }); await page.goto(config.baseUrl + "/platform/model-supply/deployments", { waitUntil: "networkidle" });
        await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
        const modal = page.getByRole("dialog");
        await page.waitForFunction(() => !document.querySelector('.platform-deployment-form button[type="submit"]')?.disabled);
        await modal.getByLabel("已发现模型", { exact: true }).selectOption(String(index));
        if (current.body.items[index].modelType === null) await modal.getByLabel("模型类型", { exact: true }).selectOption(previewSource.modelType);
        await modal.getByLabel("部署名称", { exact: true }).fill("只读表单观察，不保存");
        await modal.getByLabel("上下文窗口（Token）", { exact: true }).fill("128");
        const parameterLabel = previewSource.modelType === "CHAT" ? "最大输出（Token）" : previewSource.modelType === "EMBEDDING" ? "向量维度" : "重排候选上限";
        await modal.getByLabel(parameterLabel, { exact: true }).fill("20"); await modal.getByLabel("参数来源", { exact: true }).fill("只读观察，不作为厂商事实");
        if (textScale === 2) { await enlargeText(page); await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))); }
        const fonts = await modal.locator('.deployment-field input,.deployment-field select').evaluateAll(elements => elements.map(element => parseFloat(getComputedStyle(element).fontSize)));
        requireCondition(fonts.length >= 7 && fonts.every(size => size >= 16 * textScale), "原生表单文字未达到真实目标字号");
        await modal.getByLabel("部署名称", { exact: true }).focus();
        let reached = false;
        const focusLimit = await modal.locator("input,select,button").count() + 2;
        for (let step = 0; step < focusLimit; step++) {
          if (await modal.getByRole("button", { name: "保存部署草稿", exact: true }).evaluate(element => element === document.activeElement)) { reached = true; break; }
          await page.keyboard.press("Tab");
        }
        requireCondition(reached, "键盘不能到达保存操作");
        const header = await modal.locator("header").boundingBox(), footer = await modal.locator("footer").boundingBox();
        requireCondition(header.y >= 0 && footer.y + footer.height <= 1001 && !(await modal.evaluate(element => element.scrollWidth > element.clientWidth + 1)), "完整表单标题或操作区被裁切");
        const screenshot = path.join(directory, `form-${width}-${textScale}.png`);
        await page.screenshot({ path: screenshot }); fs.chmodSync(screenshot, 0o600);
        const styles = {};
        for (const [key, locator] of Object.entries({ surface: modal, title: modal.locator("h2"), input: modal.locator('input[name="name"]'), header: modal.locator("header"), footer: modal.locator("footer"), form: modal.locator("form"), button: modal.getByRole("button", { name: "取消", exact: true }) }))
          styles[key] = await locator.evaluate(styleFacts);
        report.observations.push({ view: "form", width, textScale, nativeFonts: fonts, header, footer, screenshot, styles });
        await page.keyboard.press("Escape"); await modal.waitFor({ state: "hidden" });
      }
    });
    requireCondition(writes === (config.mode === "create-draft" ? 1 : 0), "写入次数与显式模式不一致");
    report.completed = true; report.writes = writes; persist();
    return report;
  } catch (error) {
    if (descriptor !== undefined) { report.failedStage = stage; report.completed = false; persist(); }
    throw new SafeAssertionError(`S2 验收未完成：${stage}`);
  } finally { try { await cache?.close(); } finally { if (descriptor !== undefined) fs.closeSync(descriptor); } }
}

async function layout(page) {
  return page.evaluate(() => ({ overflow: document.documentElement.scrollWidth > innerWidth + 1,
    clipped: [...document.querySelectorAll('h1,h2,button,label,p,td,dt,dd')].filter(element => element.getBoundingClientRect().width > 0 && element.clientHeight > 0 && element.scrollHeight > element.clientHeight + 1)
      .map(element => element.tagName), reducedMotion: matchMedia("(prefers-reduced-motion: reduce)").matches }));
}
/** 仅记录排版事实，不读取输入值、秘密或浏览器会话状态。 */
function styleFacts(element) {
  const style = getComputedStyle(element), rect = element.getBoundingClientRect();
  return { font: style.fontFamily, size: style.fontSize, lineHeight: style.lineHeight, color: style.color,
    background: style.backgroundColor, border: style.borderColor, radius: style.borderRadius,
    width: rect.width, height: rect.height, padding: style.padding, maxWidth: style.maxWidth, maxHeight: style.maxHeight, boxSizing: style.boxSizing };
}
module.exports = { configuration, readDraftInput, permittedMutation, run, layout };
if (require.main === module) {
  try { run(configuration()).catch(error => { process.stderr.write((error instanceof SafeAssertionError ? error.message : "S2 验收未完成") + "\n"); process.exitCode = 1; }); }
  catch (error) { process.stderr.write((error instanceof SafeAssertionError ? error.message : "S2 验收配置不可用") + "\n"); process.exitCode = 1; }
}
