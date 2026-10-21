/* WP-0019 S2：隔离发布／撤权投影，只验当前授权显示；不向服务器提交发布或撤权。 */
const fs = require("node:fs");
const path = require("node:path");
const { createBrowserTestCache } = require("../quality/browser-test-cache.cjs");
const { requireCondition, openLogin, fillLogin, submitLogin, assertIdentity, request } = require("./acceptance-login.cjs");
const { enlargeText } = require("./acceptance-overview.cjs");
const root = path.resolve(__dirname, "../..");
const targetId = process.env.FOUNDATION_ACCEPTANCE_DEPLOYMENT_ID;
const output = process.env.FOUNDATION_ACCEPTANCE_EVIDENCE_DIRECTORY;
const baseUrl = "http://127.0.0.1:13000";
let browser, context, cache, intercepted = 0, blocked = 0, outputVerified = false, stage = "初始化";

async function run() {
  requireCondition(typeof output === "string" && output.startsWith("/private/tmp/") && path.isAbsolute(output), "证据必须在独立临时目录");
  const directory = fs.lstatSync(output);
  requireCondition(directory.isDirectory() && !directory.isSymbolicLink() && fs.realpathSync(output) === output
    && directory.uid === process.getuid() && (directory.mode & 0o777) === 0o700, "证据目录权限不正确");
  outputVerified = true;
  requireCondition(typeof targetId === "string" && /^[1-9][0-9]{0,18}$/.test(targetId)
    && BigInt(targetId) <= 9223372036854775807n, "必须指定既有部署");
  let platform;
  const descriptor = fs.openSync(path.join(root, "pwd.txt"), fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
  try {
    const stat = fs.fstatSync(descriptor);
    requireCondition(stat.isFile() && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o600, "本地凭据文件权限不正确");
    platform = JSON.parse(fs.readFileSync(descriptor, "utf8")).platform;
  } finally { fs.closeSync(descriptor); }
  const { chromium } = require("playwright");
  cache = createBrowserTestCache(output, async () => { try { await context?.close(); } finally { await browser?.close(); } });
  fs.mkdirSync(cache.cacheDir, { recursive: true, mode: 0o700 }); process.env.TMPDIR = cache.cacheDir;
  browser = await chromium.launch({ channel: "chrome", headless: true, args: ["--disk-cache-dir=" + path.join(cache.cacheDir, "chrome")] });
  context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, reducedMotion: "reduce" });
  let deployment;
  const detailPath = "/api/v1/platform/model-supply/deployments/" + targetId;
  await context.route("**/api/v1/**", async route => {
    const req = route.request(), pathname = new URL(req.url()).pathname;
    if (deployment && req.method() === "GET" && pathname === detailPath) {
      intercepted++;
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(deployment) }); return;
    }
    if (deployment && req.method() === "GET" && pathname === "/api/v1/platform/model-supply/deployments/facts") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
        writesAvailable: true, counts: { total: 1, drafts: 0, verified: 0, published: 1, disabled: 0 }
      }) }); return;
    }
    const login = req.method() === "POST" && ["/api/v1/auth/browser-sessions", "/api/v1/platform/auth/browser-sessions"].includes(pathname);
    if (!["GET", "HEAD", "OPTIONS"].includes(req.method()) && !login) { blocked++; await route.abort(); return; }
    await route.continue();
  });
  const page = await context.newPage();
  stage = "复用受保护身份";
  await openLogin(page, { baseUrl }, "platform"); await fillLogin(page, platform, "platform"); await submitLogin(page); await assertIdentity(page, "platform");
  const exact = await request(page, "/platform/model-supply/deployments/" + targetId);
  requireCondition(exact.status === 200 && exact.body.id === targetId && exact.body.status === "DRAFT", "只允许既有草稿只读观察");
  deployment = { ...exact.body, status: "PUBLISHED", version: exact.body.version + 2,
    availability: "HEALTHY", nextStep: "NONE", displayName: "合成授权恢复模型", connectionDisplayName: "合成已发布连接",
    verifiedAt: new Date().toISOString(), verificationExpiresAt: new Date(Date.now() + 3600000).toISOString(),
    supply: { publicationId: "990000000000000001", deploymentRevision: exact.body.version + 2,
      catalogEntryId: "990000000000000002", publishedAt: new Date().toISOString(),
      selectedTenantCount: exact.body.inputs.plannedTenantIds.length, activeGrantCount: 0,
      revokedGrantCount: exact.body.inputs.plannedTenantIds.length, dependentConfigurationCount: 0,
      activeTenantIds: [], currentSourceAvailable: true }
  };
  stage = "撤权后只能显示当前授权而非计划租户";
  await page.goto(baseUrl + "/platform/model-supply/deployments/" + targetId, { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: "平台部署发布", exact: true }).waitFor();
  await page.getByText("0 个租户", { exact: true }).waitFor({ timeout: 4000 });
  const observations = [];
  for (const scale of [1, 2]) {
    if (scale === 2) await enlargeText(page);
    for (const width of [390, 1024, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      await page.evaluate(() => document.fonts.ready);
      const feedback = page.getByText("0 个租户", { exact: true });
      await feedback.scrollIntoViewIfNeeded();
      const fits = await feedback.evaluate(element => element.scrollWidth <= element.clientWidth
        && element.scrollHeight <= element.clientHeight && document.documentElement.scrollWidth <= innerWidth);
      requireCondition(fits, "当前授权事实不得裁切或制造横向滚动");
      await page.screenshot({ path: path.join(output, "publication-" + width + "-" + scale + ".png"), fullPage: true });
      observations.push({ width, textScale: scale, clipped: false });
    }
  }
  stage = "停用后仍读取历史授权而非回退计划";
  deployment = { ...deployment, status: "DISABLED", version: deployment.version + 1, availability: "UNAVAILABLE" };
  await page.reload({ waitUntil: "networkidle" });
  await page.getByText("已授权租户", { exact: true }).waitFor();
  await page.getByText("0 个租户", { exact: true }).waitFor();
  requireCondition(await page.getByText("拟授权租户", { exact: true }).count() === 0, "停用不能把已发布授权恢复为计划");
  requireCondition(intercepted >= 2 && blocked === 0, "请求次数或写入隔离不正确");
  fs.writeFileSync(path.join(output, "publication-browser.json"), JSON.stringify({
    passed: true, scope: "隔离发布与撤权投影；不证明真实厂商、正式发布或赋权通过",
    interceptedPublishedReads: intercepted, businessWrites: 0, vendorCalls: 0, disabledRetainsSupplyFacts: true, observations
  }, null, 2), { mode: 0o600, flag: "wx" });
  process.stdout.write("供应授权投影隔离浏览器检查通过；业务写入 0，厂商调用 0。\n");
}
run().catch(() => {
  if (outputVerified) {
    try { fs.writeFileSync(path.join(output, "publication-browser-failure.json"), JSON.stringify({ passed: false, stage, intercepted, blocked }), { mode: 0o600, flag: "wx" }); } catch {}
  }
  process.stderr.write(JSON.stringify({ passed: false, stage, intercepted, blocked }) + "\n"); process.exitCode = 1;
}).finally(async () => { try { await cache?.close(); } catch { process.stderr.write("浏览器资源关闭失败。\n"); process.exitCode = 1; } });
