/* WP-0019 / S1 正式验收入口：各执行模式必须分别绑定对象及证据。 */
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { execFileSync } = require("node:child_process");
const { readConfiguration, requireCondition, SafeAssertionError, request,
  openLogin, fillLogin, submitLogin, assertIdentity, logoutWorkspace, assertAnonymous } = require("./acceptance-login.cjs");
const { enlargeText } = require("./acceptance-overview.cjs");

const modes = ["read-only", "create-draft", "vendor-discovery", "publish-current"];
const repository = path.resolve(__dirname, "../..");
const api = "/platform/model-supply/connections";
const directoryPath = "/platform/model-supply";
const prototype = { projectId: "249f81f2-6f1a-46ff-988c-da3cdedb35e7",
  revision: "d5deddaa1955814c5f08795b0b1ca58a7e6ac784" };
const longId = value => typeof value === "string" && /^[1-9][0-9]{0,18}$/.test(value)
  && BigInt(value) <= 9223372036854775807n;

/** 输出只写本轮显式指定的新文件；不覆盖凭据、历史记录或仓库文件。 */
function validateOutputFile(file, required) {
  if (file === undefined && !required) return;
  requireCondition(typeof file === "string" && path.isAbsolute(file)
    && file.startsWith("/private/tmp/") && !fs.existsSync(file), "需要新的独占输出文件");
  try {
    const parent = path.dirname(file);
    const stat = fs.statSync(parent);
    requireCondition(fs.realpathSync(parent) === parent && stat.isDirectory()
      && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o700, "输出文件父目录必须为当前用户独占的 0700 目录");
  } catch { throw new SafeAssertionError("输出文件父目录不可用，必须为当前用户独占的 0700 目录"); }
}

/** 创建材料只在显式创建模式读取，秘密不进入输出记录或错误。 */
function readConnectionInput(file) {
  let descriptor;
  let input;
  try {
    requireCondition(typeof file === "string" && path.isAbsolute(file), "需要创建材料文件");
    descriptor = fs.openSync(file, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
    const stat = fs.fstatSync(descriptor);
    requireCondition(stat.isFile() && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o600
      && stat.size > 0 && stat.size <= 16_384, "创建材料必须为普通 0600 文件");
    input = JSON.parse(fs.readFileSync(descriptor, "utf8"));
  } catch { throw new SafeAssertionError("创建材料文件不可用，需要当前用户拥有的普通 0600 JSON 文件"); }
  finally { if (descriptor !== undefined) fs.closeSync(descriptor); }
  const keys = ["providerPresetId", "capability", "workspaceId", "endpoint", "apiKey", "discoveryAccessKeyId", "discoverySecretAccessKey"];
  requireCondition(input && !Array.isArray(input) && Object.keys(input).every(key => keys.includes(key)), "创建材料包含未知字段");
  requireCondition(["CUSTOM", "DEEPSEEK", "ALIYUN", "SILICONFLOW", "VOLCENGINE"].includes(input.providerPresetId)
    && ["CHAT", "EMBEDDING", "RERANK"].includes(input.capability), "创建材料的厂商或能力无效");
  requireCondition(typeof input.apiKey === "string" && input.apiKey.trim().length >= 8 && input.apiKey.length <= 4096,
    "创建材料的推理认证不完整");
  if (input.workspaceId !== undefined) requireCondition(typeof input.workspaceId === "string"
    && /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(input.workspaceId), "创建材料的业务空间无效");
  if (input.providerPresetId === "CUSTOM") {
    let endpoint;
    try { endpoint = new URL(input.endpoint); } catch { throw new SafeAssertionError("创建材料的自定义地址无效"); }
    requireCondition(endpoint.protocol === "https:" && !endpoint.username && !endpoint.password
      && !endpoint.search && !endpoint.hash, "创建材料的自定义地址无效");
  } else requireCondition(input.endpoint === undefined, "创建材料不能覆盖固定厂商地址");
  if (input.providerPresetId === "VOLCENGINE") {
    requireCondition([input.discoveryAccessKeyId, input.discoverySecretAccessKey].every(value =>
      typeof value === "string" && value.trim().length > 0 && value.length <= 4096), "创建材料缺少独立发现 AK/SK");
  } else requireCondition(input.discoveryAccessKeyId === undefined && input.discoverySecretAccessKey === undefined,
    "创建材料包含不匹配的独立发现凭据");
  return input;
}

/** 默认只读；写入、真实厂商和发布分别显式选择，不能从历史状态推导授权。 */
function platformConnectionConfiguration(environment = process.env) {
  requireCondition(!environment.SETUP, "平台连接验收不接受外部 SETUP");
  const mode = environment.FOUNDATION_ACCEPTANCE_MODE ?? "read-only";
  requireCondition(modes.includes(mode), "执行模式只允许 read-only、create-draft、vendor-discovery 或 publish-current");
  const frontendRevision = environment.FOUNDATION_ACCEPTANCE_FRONTEND_REVISION;
  const backendRevision = environment.FOUNDATION_ACCEPTANCE_BACKEND_REVISION;
  requireCondition([frontendRevision, backendRevision].every(value => typeof value === "string" && /^[a-f0-9]{40}$/.test(value)),
    "必须分别提供精确的前端及后端运行提交");
  const connectionId = environment.FOUNDATION_ACCEPTANCE_CONNECTION_ID;
  const rawVersion = environment.FOUNDATION_ACCEPTANCE_CONNECTION_VERSION;
  if (mode === "create-draft") {
    requireCondition(connectionId === undefined && rawVersion === undefined, "创建材料不能同时指定既有连接标识或版本");
  } else {
    requireCondition(longId(connectionId), "必须指定精确的既有连接标识");
    requireCondition(typeof rawVersion === "string" && /^(0|[1-9][0-9]*)$/.test(rawVersion)
      && Number.isSafeInteger(Number(rawVersion)), "必须指定精确的非负连接版本");
    requireCondition(environment.FOUNDATION_ACCEPTANCE_CONNECTION_FILE === undefined, "当前模式不能读取创建材料");
  }
  const outputFile = environment.FOUNDATION_ACCEPTANCE_OUTPUT_FILE;
  validateOutputFile(outputFile, mode !== "read-only");
  const input = mode === "create-draft" ? readConnectionInput(environment.FOUNDATION_ACCEPTANCE_CONNECTION_FILE) : undefined;
  return { ...readConfiguration(environment), mode, frontendRevision, backendRevision, connectionId,
    expectedVersion: rawVersion === undefined ? undefined : Number(rawVersion), outputFile, input };
}

/** 只选择健康、服务名、镜像摘要及 OCI revision，不读取容器环境或认证材料。 */
function inspectRuntime(configuration, execute = execFileSync) {
  const invoke = (command, arguments_) => execute(command, arguments_, { cwd: repository,
    encoding: "utf8", timeout: 15_000, stdio: ["ignore", "pipe", "pipe"] }).trim();
  let containers;
  let facts;
  let sourceCommit;
  try {
    sourceCommit = invoke("git", ["rev-parse", "HEAD"]);
    requireCondition(!invoke("git", ["status", "--porcelain=v1", "--untracked-files=all"]), "验收源码工作区必须干净并已提交");
    containers = invoke("docker", ["ps", "--all", "--filter",
      "label=com.docker.compose.project=trusted-rag-foundation-v1", "--format", "{{.ID}}"]).split(/\s+/);
    requireCondition(containers.length === 7 && containers.every(id => /^[a-f0-9]{12,64}$/.test(id)),
      "需要本项目七个精确 Compose 服务");
    const format = '{"service":{{json (index .Config.Labels "com.docker.compose.service")}},'
      + '"health":{{json .State.Health.Status}},"image":{{json .Image}},'
      + '"revision":{{json (index .Config.Labels "org.opencontainers.image.revision")}}}';
    facts = invoke("docker", ["inspect", "--format", format, ...containers]).split("\n").map(line => JSON.parse(line));
  } catch (error) {
    if (error instanceof SafeAssertionError) throw error;
    throw new SafeAssertionError("无法只读确认源码与 Compose 运行事实");
  }
  requireCondition(/^[a-f0-9]{40}$/.test(sourceCommit), "无法绑定精确验收源码提交");
  const services = ["console-web", "control-plane", "query-runtime", "ingestion-worker", "postgres", "redis", "minio"];
  requireCondition(facts.length === services.length && new Set(facts.map(item => item.service)).size === services.length
    && facts.every(item => services.includes(item.service) && item.health === "healthy"
      && /^sha256:[a-f0-9]{64}$/.test(item.image)), "Compose 服务缺失、重复或不健康");
  for (const fact of facts.filter(item => services.slice(0, 4).includes(item.service))) {
    requireCondition(fact.revision === (fact.service === "console-web" ? configuration.frontendRevision : configuration.backendRevision),
      "运行版本不匹配：" + fact.service);
  }
  return { sourceCommit, services: facts.map(({ service, health, image, revision }) =>
    ({ service, health, image, ...(services.slice(0, 4).includes(service) ? { revision } : {}) })) };
}

/** 公共读取不能携带秘密或存储细节；失败消息不拼接响应正文。 */
function assertSafeResponse(value, secrets = []) {
  const forbidden = /^(apiKey|password|tokenValue|credentialReference|discoveryAccessKeyId|discoverySecretAccessKey|ciphertext|nonce|dataKeyCiphertext|wrappedDataKey|fingerprint)$/i;
  const visit = current => {
    if (!current || typeof current !== "object") return;
    for (const [key, child] of Object.entries(current)) {
      requireCondition(!forbidden.test(key), "公共读取包含禁止披露的认证或存储字段");
      visit(child);
    }
  };
  visit(value);
  const text = JSON.stringify(value) ?? "";
  requireCondition(!text.includes("secret://") && !secrets.some(secret => typeof secret === "string"
    && secret.length > 0 && text.includes(secret)), "公共读取或浏览器存储包含秘密");
}

function verifyConnection(connection, id, version) {
  requireCondition(connection && connection.id === id && connection.version === version
    && ["DRAFT", "VERIFIED", "DISABLED"].includes(connection.status)
    && typeof connection.credentialConfigured === "boolean"
    && typeof connection.discoveryCredentialsConfigured === "boolean" && typeof connection.published === "boolean"
    && connection.endpoint === undefined && connection.workspaceId === undefined,
  "连接对象、修订或当前非秘密事实不匹配");
  assertSafeResponse(connection);
}

function verifyPublication(publication, connection) {
  requireCondition(publication && publication.connectionId === connection.id
    && publication.connectionRevision === connection.version
    && typeof publication.published === "boolean" && publication.published === connection.published,
  "发布读取未绑定当前连接修订");
  assertSafeResponse(publication);
}

/** 分页断言保留真实总数，不能用当前页长度替代或接受重复对象。 */
function verifyPage(result, number, size, itemId) {
  requireCondition(result && Array.isArray(result.items) && Number.isSafeInteger(result.totalElements)
    && result.totalElements >= 0 && result.items.length <= size
    && result.items.length === Math.max(0, Math.min(size, result.totalElements - (number - 1) * size))
    && result.hasPrevious === (number > 1) && result.hasNext === (number * size < result.totalElements)
    && result.items.every(item => typeof itemId(item) === "string" && itemId(item).length > 0)
    && new Set(result.items.map(itemId)).size === result.items.length, "分页总数、边界或对象唯一性不一致");
  assertSafeResponse(result);
}

/** 身份建立/退出不算模型业务写入；其他请求只放行当前模式的一条精确命令。 */
function permittedMutation(method, pathname, configuration) {
  if (method === "GET" || method === "HEAD") return "read";
  if ((pathname === "/api/v1/auth/browser-sessions" && method === "POST")
    || (pathname === "/api/v1/auth/browser-session" && method === "DELETE")) return "authentication";
  if (method !== "POST") return undefined;
  const target = "/api/v1" + api;
  if (configuration.mode === "create-draft" && pathname === target) return "business";
  if (configuration.mode === "vendor-discovery" && pathname === target + "/" + configuration.connectionId + "/model-discovery") return "business";
  if (configuration.mode === "publish-current" && pathname === target + "/" + configuration.connectionId + "/publish") return "business";
  return undefined;
}

/** 真实发现不接受确定性模拟、缺失用途凭据或停用对象；发布另行验证时效。 */
function requireActionReady(configuration, connection, now = Date.now()) {
  verifyConnection(connection, configuration.connectionId, configuration.expectedVersion);
  if (configuration.mode === "vendor-discovery") {
    requireCondition(connection.status !== "DISABLED" && connection.providerKind === "OPENAI_COMPATIBLE"
      && ["OPENAI_MODELS_API", "VOLCENGINE_ARK_CONTROL_PLANE"].includes(connection.discoveryProfile),
    "真实厂商发现不能使用停用、确定性或无发现协议的连接");
    requireCondition(connection.credentialConfigured
      && (connection.discoveryProfile !== "VOLCENGINE_ARK_CONTROL_PLANE" || connection.discoveryCredentialsConfigured),
    "真实厂商发现缺少匹配的推理认证或独立发现 AK/SK");
  }
  if (configuration.mode === "publish-current") {
    const probedAt = Date.parse(connection.lastProbedAt);
    requireCondition(connection.status === "VERIFIED" && connection.lastProbeSucceeded === true
      && !connection.published && Number.isFinite(probedAt) && probedAt <= now
      && now - probedAt < 24 * 60 * 60 * 1000, "发布要求当前修订未发布且验证仍在有效期内");
  }
}

function connectionFact(connection) {
  return { id: connection.id, version: connection.version, status: connection.status,
    credentialConfigured: connection.credentialConfigured,
    discoveryCredentialsConfigured: connection.discoveryCredentialsConfigured, published: connection.published };
}

async function readCurrent(page, configuration, secrets) {
  const connection = await request(page, api + "/" + configuration.connectionId);
  requireCondition(connection.status === 200, "正式连接详情接口未成功");
  assertSafeResponse(connection.body, secrets);
  verifyConnection(connection.body, configuration.connectionId, configuration.expectedVersion);
  const publication = await request(page, api + "/" + configuration.connectionId + "/publication?expectedVersion=" + configuration.expectedVersion);
  requireCondition(publication.status === 200, "正式连接发布读取未成功");
  verifyPublication(publication.body, connection.body);
  return connection.body;
}

async function directory(page, configuration) {
  await page.goto(configuration.baseUrl + directoryPath + "?page=1", { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: "平台模型连接", exact: true }).waitFor();
  await page.getByRole("region", { name: "平台模型连接目录", exact: true }).waitFor();
}

async function detail(page, configuration) {
  await page.goto(configuration.baseUrl + directoryPath + "/connections/" + configuration.connectionId + "?candidatePage=1", { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: "供应商连接探测", exact: true }).waitFor();
  requireCondition(new URL(page.url()).pathname === directoryPath + "/connections/" + configuration.connectionId, "详情未恢复精确连接");
}

/** 布局检查只证明当前生产页面可操作，不代替固定版本原型的人工视觉对照。 */
async function verifyLayout(page) {
  await page.evaluate(() => document.fonts.ready);
  const result = await page.evaluate(() => {
    const root = document.querySelector('[role="dialog"]') ?? document.querySelector(".platform-connection-page");
    const elements = root ? [...root.querySelectorAll("h1,h2,h3,p,dt,dd,label,button")] : [];
    const clipped = elements.some(element => {
      if (!element.getClientRects().length) return false;
      const style = getComputedStyle(element);
      return (style.overflowX !== "visible" && element.scrollWidth > element.clientWidth + 1)
        || (style.overflowY !== "visible" && element.scrollHeight > element.clientHeight + 1);
    });
    return { exists: !!root, overflow: document.documentElement.scrollWidth - innerWidth, clipped };
  });
  requireCondition(result.exists && result.overflow <= 1 && !result.clipped, "当前页面存在横向溢出或文字裁切");
  const controls = page.locator('[role="dialog"] button:visible, .platform-connection-page button:visible');
  for (let index = 0; index < await controls.count(); index += 1) {
    const control = controls.nth(index);
    if (!await control.isEnabled()) continue;
    await control.scrollIntoViewIfNeeded();
    requireCondition(await control.evaluate(element => {
      const box = element.getBoundingClientRect();
      const target = document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2);
      return element.contains(target) && box.height >= (innerWidth <= 680 ? 44
        : element.closest(".platform-connection-pagination") ? 32 : 36);
    }), "当前页面操作被遮挡或未达到控件基线");
  }
}

async function verifyReadOnlyPages(page, configuration, presets, check) {
  await check("目录分页与精确详情刷新", async () => {
    const first = await request(page, api + "?pageNumber=1&pageSize=20");
    requireCondition(first.status === 200, "平台连接目录接口未成功");
    verifyPage(first.body, 1, 20, item => item.id);
    const number = Math.floor(first.body.totalElements / 20) + 2;
    const overflow = await request(page, api + "?pageNumber=" + number + "&pageSize=20");
    requireCondition(overflow.status === 200 && overflow.body.totalElements === first.body.totalElements, "越界页丢失真实总数");
    verifyPage(overflow.body, number, 20, item => item.id);
    await directory(page, configuration);
    requireCondition(JSON.stringify(await page.locator(".platform-connection-table thead th").allTextContents())
      === JSON.stringify(["连接", "运行能力", "状态", "操作"]), "连接目录列未承接当前原型");
    await detail(page, configuration); await page.reload({ waitUntil: "networkidle" });
    await page.getByRole("heading", { name: "供应商连接探测", exact: true }).waitFor();
  });
  for (const width of [390, 1024, 1440]) {
    await check(width + "px 目录、详情、弹窗及 200% 文字", async () => {
      await page.setViewportSize({ width, height: ({ 390: 844, 1024: 900, 1440: 1000 })[width] });
      for (const navigate of [directory, detail]) {
        await navigate(page, configuration); await verifyLayout(page);
        await enlargeText(page); await verifyLayout(page);
      }
      await directory(page, configuration);
      await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
      await page.getByRole("dialog").waitFor();
      requireCondition(await page.getByRole("button", { name: "取消", exact: true })
        .evaluate(element => getComputedStyle(element).minWidth) === "0px", "短按钮仍带固定最小宽度");
      await verifyLayout(page); await enlargeText(page); await verifyLayout(page);
      await page.getByRole("button", { name: "取消", exact: true }).click();
      await page.getByRole("dialog").waitFor({ state: "hidden" });
    });
  }
  await check("五厂商联动、秘密取消清理、键盘及 reduced-motion", async () => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await directory(page, configuration);
    const trigger = page.getByRole("button", { name: "新增供应商连接", exact: true });
    await trigger.click(); const dialog = page.getByRole("dialog");
    requireCondition(await dialog.getByLabel("厂商类型", { exact: true }).locator("option").count() === 5, "厂商目录未承接五厂商");
    for (const preset of presets.items) {
      await dialog.getByLabel("厂商类型", { exact: true }).selectOption(preset.id);
      for (const variant of preset.variants) {
        await dialog.getByLabel("运行能力", { exact: true }).selectOption(variant.capability);
        if (variant.workspaceRequired) await dialog.getByLabel("业务空间 ID", { exact: true }).fill("s1-acceptance");
        const endpoint = dialog.getByLabel("运行地址", { exact: true });
        requireCondition(await endpoint.evaluate(element => element.readOnly) === (preset.id !== "CUSTOM"), "固定厂商地址可被编辑");
        if (preset.id !== "CUSTOM") requireCondition(await endpoint.inputValue()
          === variant.endpointTemplate.replace("{workspaceId}", "s1-acceptance"), "预设地址联动不匹配");
      }
    }
    await dialog.getByLabel("厂商类型", { exact: true }).selectOption("VOLCENGINE");
    const synthetic = "wp0019-cancel-" + crypto.randomBytes(8).toString("hex");
    await dialog.getByLabel("一次性访问密钥", { exact: true }).fill(synthetic);
    await dialog.getByLabel("模型发现 Access Key ID", { exact: true }).fill(synthetic);
    await dialog.getByLabel("模型发现 Secret Access Key", { exact: true }).fill(synthetic);
    for (let index = 0; index < 12; index += 1) {
      await page.keyboard.press("Tab");
      requireCondition(await dialog.evaluate(element => element.contains(document.activeElement)), "键盘焦点离开模态");
    }
    await page.keyboard.press("Escape"); await dialog.waitFor({ state: "hidden" });
    await page.waitForFunction(() => document.activeElement?.textContent === "新增供应商连接");
    await trigger.click();
    requireCondition(await page.getByRole("dialog").locator('input[type="password"]').evaluateAll(elements =>
      elements.every(element => element.value === "")), "取消后恢复了秘密");
    assertSafeResponse(await page.evaluate(() => ({ ...localStorage, ...sessionStorage })), [synthetic]);
    await page.keyboard.press("Escape");
    requireCondition(await page.evaluate(() => !document.getAnimations().some(animation =>
      animation.playState === "running" && animation.effect?.getTiming().duration > 1)), "reduced-motion 仍存在装饰动画");
  });
}

async function createDraft(page, configuration, presets, report, persist) {
  const input = configuration.input;
  const preset = presets.items.find(item => item.id === input.providerPresetId);
  const variant = preset?.variants.find(item => item.capability === input.capability);
  requireCondition(variant && variant.workspaceRequired === (input.workspaceId !== undefined), "创建材料与当前服务端预设不匹配");
  await directory(page, configuration);
  await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("厂商类型", { exact: true }).selectOption(preset.id);
  await dialog.getByLabel("运行能力", { exact: true }).selectOption(input.capability);
  report.creation = { displayName: "WP-0019 S1 验收 " + crypto.randomBytes(6).toString("hex") };
  persist();
  await dialog.getByLabel("连接名称", { exact: true }).fill(report.creation.displayName);
  if (input.workspaceId) await dialog.getByLabel("业务空间 ID", { exact: true }).fill(input.workspaceId);
  if (preset.id === "CUSTOM") await dialog.getByLabel("运行地址", { exact: true }).fill(input.endpoint);
  await dialog.getByLabel("一次性访问密钥", { exact: true }).fill(input.apiKey);
  if (preset.id === "VOLCENGINE") {
    await dialog.getByLabel("模型发现 Access Key ID", { exact: true }).fill(input.discoveryAccessKeyId);
    await dialog.getByLabel("模型发现 Secret Access Key", { exact: true }).fill(input.discoverySecretAccessKey);
  }
  const received = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1" + api
    && response.request().method() === "POST");
  await dialog.getByRole("button", { name: "保存连接草稿", exact: true }).click();
  const response = await received;
  requireCondition(response.status() === 201, "草稿创建未成功，保留现场且不自动重试");
  const connection = await response.json();
  // 一旦正式接口返回有效标识即登记，后续验证失败也不能丢失本轮对象定位。
  requireCondition(longId(connection.id) && Number.isSafeInteger(connection.version), "草稿接口未返回精确对象");
  report.connection = { id: connection.id, version: connection.version }; persist();
  assertSafeResponse(connection, [input.apiKey, input.discoveryAccessKeyId, input.discoverySecretAccessKey]);
  verifyConnection(connection, connection.id, connection.version);
  requireCondition(connection.status === "DRAFT" && !connection.published, "创建草稿意外完成了探测或发布");
  configuration.connectionId = connection.id; configuration.expectedVersion = connection.version;
  await page.getByRole("heading", { name: "供应商连接探测", exact: true }).waitFor();
  return connection;
}

/** 执行只走正式会话与同源接口，不连接数据库、不导出秘密、不删除验收对象。 */
async function run(configuration) {
  const report = { schemaVersion: 1, workPackage: "WP-0019", slice: "platform-connection", mode: configuration.mode,
    prototype, startedAt: new Date().toISOString(), status: "RUNNING", checks: [], businessWrites: 0, blockedWrites: 0,
    prototypeParity: "NOT_RUN", sliceComplete: false };
  let descriptor;
  let browser;
  let stage = "只读确认运行版本";
  let pageErrors = 0;
  const persist = () => {
    if (descriptor === undefined) return;
    fs.ftruncateSync(descriptor, 0); fs.writeSync(descriptor, JSON.stringify(report), 0, "utf8"); fs.fsyncSync(descriptor);
  };
  const secrets = [configuration.credentials.platform.password, configuration.credentials.tenant.password,
    configuration.input?.apiKey, configuration.input?.discoveryAccessKeyId, configuration.input?.discoverySecretAccessKey].filter(Boolean);
  const check = async (name, action) => {
    stage = name; await action();
    requireCondition(report.blockedWrites === 0 && pageErrors === 0, "发现未授权业务请求或页面异常");
    report.checks.push(name); persist();
    process.stdout.write(JSON.stringify({ assertion: name, result: "通过" }) + "\n");
  };
  try {
    if (configuration.outputFile) descriptor = fs.openSync(configuration.outputFile, "wx", 0o600);
    persist();
    report.runtime = inspectRuntime(configuration); persist();
    requireCondition(require("playwright/package.json").version === "1.62.1", "需要锁定的 Playwright 运行时");
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const observe = async current => {
      await current.route("**/api/v1/**", async route => {
        const outgoing = route.request();
        const kind = permittedMutation(outgoing.method(), new URL(outgoing.url()).pathname, configuration);
        if (!kind || (kind === "business" && report.businessWrites >= 1)) {
          report.blockedWrites += 1; await route.abort(); return;
        }
        if (kind === "business") { report.businessWrites += 1; persist(); }
        await route.continue();
      });
      current.on("page", page => page.on("pageerror", () => { pageErrors += 1; }));
    };
    await observe(context);
    const page = await context.newPage(); page.setDefaultTimeout(15_000);
    await check("平台管理员正式登录与范围恢复", async () => {
      await openLogin(page, configuration, "platform");
      await fillLogin(page, configuration.credentials.platform, "platform"); await submitLogin(page);
      await assertIdentity(page, "platform");
    });
    let presets;
    await check("正式五厂商预设及匿名、租户范围拒绝", async () => {
      const response = await request(page, api + "/presets");
      requireCondition(response.status === 200 && response.body?.items?.length === 5
        && response.body.items.reduce((sum, item) => sum + item.variants.length, 0) === 11, "正式五厂商目录或十一组能力缺失");
      assertSafeResponse(response.body, secrets); presets = response.body;
      for (const role of ["anonymous", "tenant"]) {
        const other = await browser.newContext(); await observe(other);
        try {
          const probe = await other.newPage(); await probe.goto(configuration.baseUrl);
          if (role === "tenant") {
            await openLogin(probe, configuration, "tenant");
            await fillLogin(probe, configuration.credentials.tenant, "tenant"); await submitLogin(probe);
            await assertIdentity(probe, "tenant");
          }
          for (const target of [api, api + "/presets", ...(configuration.connectionId ? [api + "/" + configuration.connectionId] : [])]) {
            const denied = await request(probe, target);
            requireCondition([401, 403].includes(denied.status), "非平台会话读取到平台模型供应");
          }
        } finally { await other.close(); }
      }
    });
    let before;
    if (configuration.mode !== "create-draft") await check("精确连接、当前发布与非秘密配置事实", async () => {
      before = await readCurrent(page, configuration, secrets); report.connection = connectionFact(before);
      report.connectionBefore = connectionFact(before);
    });
    if (configuration.mode === "read-only") {
      await verifyReadOnlyPages(page, configuration, presets, check);
      await check("复验前后连接事实不变且业务写入为零", async () => {
        const after = await readCurrent(page, configuration, secrets);
        requireCondition(JSON.stringify(after) === JSON.stringify(before) && report.businessWrites === 0, "只读复验改变了连接事实");
      });
    } else if (configuration.mode === "create-draft") {
      await check("正式表单仅创建一个可追溯草稿", async () => {
        const created = await createDraft(page, configuration, presets, report, persist);
        const current = await readCurrent(page, configuration, secrets);
        requireCondition(current.status === created.status && !current.published, "创建后事实不一致");
        report.connection = connectionFact(current);
      });
    } else {
      await check("当前修订具备明确动作前提", async () => requireActionReady(configuration, before));
      if (configuration.mode === "vendor-discovery") await check("仅一次正式最小厂商发现及新修订读取", async () => {
        const response = await request(page, api + "/" + configuration.connectionId + "/model-discovery", "POST",
          { expectedVersion: configuration.expectedVersion, timeoutMillis: 10000 });
        requireCondition(response.status === 200, "正式发现未成功，保留结果且不自动重试");
        assertSafeResponse(response.body, secrets);
        delete report.connection;
        report.discoverySucceeded = response.body?.succeeded === true; persist();
        configuration.expectedVersion += 1;
        const current = await readCurrent(page, configuration, secrets);
        report.connection = connectionFact(current); persist();
        requireCondition(response.body?.succeeded === true && Array.isArray(response.body.models)
          && response.body.models.length > 0, "真实厂商发现未通过或没有候选，不视为合同成功");
        requireCondition(current.status === "VERIFIED" && !current.published && current.lastProbeSucceeded === true,
          "真实发现没有产生当前验证事实或意外发布");
        report.connection = connectionFact(current); report.discoveredCount = response.body.models.length;
      });
      else await check("真实详情独立发布当前修订", async () => {
        await detail(page, configuration);
        const received = page.waitForResponse(response => new URL(response.url()).pathname
          === "/api/v1" + api + "/" + configuration.connectionId + "/publish" && response.request().method() === "POST");
        await page.getByRole("button", { name: "发布连接", exact: true }).click();
        const response = await received; requireCondition(response.status() === 200, "发布未成功，保留结果且不自动重试");
        const publication = await response.json(); assertSafeResponse(publication, secrets);
        const current = await readCurrent(page, configuration, secrets);
        verifyPublication(publication, current); requireCondition(current.published, "发布没有形成当前事实");
        report.connection = connectionFact(current);
      });
      await detail(page, configuration);
    }
    await check("退出后受保护读取拒绝且未持久化秘密", async () => {
      assertSafeResponse(await page.evaluate(() => ({ ...localStorage, ...sessionStorage })), secrets);
      await logoutWorkspace(page); await assertAnonymous(page);
      requireCondition((await request(page, api)).status === 401, "退出后仍可读取平台模型供应");
    });
    requireCondition(report.businessWrites === (configuration.mode === "read-only" ? 0 : 1), "业务写入数量不符合明确模式");
    report.status = "PASSED"; report.completedAt = new Date().toISOString(); persist();
    process.stdout.write(JSON.stringify(report) + "\n");
    return report;
  } catch (error) {
    report.status = "FAILED"; report.failedStage = stage; report.completedAt = new Date().toISOString(); persist();
    throw new SafeAssertionError("S1 " + configuration.mode + " 验收未通过：" + stage
      + (error instanceof SafeAssertionError ? "；" + error.message : "；浏览器、网络或运行依赖未完成")
      + "。本次不判定 S1 完成，不自动重试、删除或清理对象。");
  } finally {
    try { await browser?.close(); }
    finally { if (descriptor !== undefined) fs.closeSync(descriptor); }
  }
}

module.exports = { platformConnectionConfiguration, validateOutputFile, readConnectionInput, inspectRuntime,
  assertSafeResponse, verifyConnection, verifyPublication, verifyPage, permittedMutation, requireActionReady, directory, detail, run };
if (require.main === module) {
  try { run(platformConnectionConfiguration()).catch(error => {
    process.stderr.write((error instanceof SafeAssertionError ? error.message : "平台连接验收未完成") + "\n"); process.exitCode = 1;
  }); } catch (error) {
    process.stderr.write((error instanceof SafeAssertionError ? error.message : "平台连接验收配置或凭据不可用") + "\n"); process.exitCode = 1;
  }
}
