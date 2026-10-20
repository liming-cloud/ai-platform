/* WP-0019 S2：真实客户端和组件的隔离 HTTP 夹具；不连接生产或厂商，不作为真实合同验收。 */
const fs = require("node:fs");
const path = require("node:path");
const { createRequire } = require("node:module");
const { pathToFileURL } = require("node:url");
const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const { enlargeText } = require("../development/acceptance-overview.cjs");
const { layout, selectPlannedTenants } = require("../development/acceptance-platform-supply.cjs");
const { createBrowserTestCache } = require("./browser-test-cache.cjs");
const frontend = path.resolve(__dirname, "../../frontend");
const fromFrontend = createRequire(path.join(frontend, "package.json"));
let server, browser, origin, temporary, cache;
const observations = [], releases = [];
const options = ["CHAT", "EMBEDDING", "RERANK", null].map((type, index) => ({ connectionId: String(11 + index), connectionRevision: 2,
  connectionDisplayName: "合成连接", residency: "合成北京区域", declaredCapability: type ?? "RERANK", modelCode: "synthetic-code-" + index,
  displayName: "合成" + (type ?? "待确认") + "模型", modelType: type, capabilities: type ? [type] : [], classification: type ? "CONFIRMED_BY_PROVIDER" : "REQUIRES_ADMIN_CONFIRMATION" }));
const deployment = { id: "123", providerConnectionId: "11", providerConnectionRevision: 2, modelCode: "synthetic-code-0", displayName: "合成对话部署",
  modelType: "CHAT", capabilities: ["CHAT"], contextWindow: 128, outputLimit: 64, embeddingDimension: null, verificationExpiresAt: null, verifiedAt: null,
  status: "DRAFT", availability: "UNAVAILABLE", nextStep: "PROBE_MODEL", version: 0, createdAt: "2026-10-20T00:00:00Z", connectionDisplayName: "合成连接",
  inputs: { parameterSource: "合成文档 v1", residency: "合成北京区域", rerankCandidateLimit: null, plannedTenantIds: ["21"], discoveryId: "31", candidateDisplayName: options[0].displayName,
    classification: "CONFIRMED_BY_PROVIDER", declaredBySubjectId: "101", declaredAt: "2026-10-20T00:00:00Z" } };
const pageOf = (items, size = 20) => ({ items, pageNumber: 1, pageSize: size, totalElements: items.length, totalPages: items.length ? 1 : 0, hasPrevious: false, hasNext: false });

before(async () => {
  temporary = fs.mkdtempSync("/private/tmp/wp0019-s2-source-"); fs.chmodSync(temporary, 0o700);
  cache = createBrowserTestCache(temporary, async () => { for (const release of releases) release(); try { await browser?.close(); } finally { await server?.close(); } });
  const harness = `import React from "react";import {createRoot} from "react-dom/client";
import {FoundationProvider} from "/@fs/${frontend}/packages/design-system/src/providers/FoundationProvider.tsx";
import {ProductShell} from "/@fs/${frontend}/packages/design-system/src/shell/ProductShell.tsx";
import {ControlPlaneClient} from "/@fs/${frontend}/apps/console-web/src/api/control-plane-client.ts";
import {PlatformModelDeploymentWorkspace} from "/@fs/${frontend}/apps/console-web/src/features/platform-governance/PlatformModelDeploymentWorkspace.tsx";
import "/@fs/${frontend}/apps/console-web/src/styles.css";import "/@fs/${frontend}/packages/design-system/src/styles/tokens.css";
const gateway={request:(p,i)=>fetch("/api/v1"+p,i),login:async()=>({}),logout:async()=>{},restore:async()=>({})};
const client=new ControlPlaneClient(undefined,undefined,"/api/v1",gateway);const root=createRoot(document.getElementById("root"));
window.draw=route=>{window.route=route;root.render(<FoundationProvider><ProductShell productName="可信 RAG" workspaceScope="platform" activeNavigationId="models" accountLabel="隔离验收身份"
navigation={[{id:"models",label:"模型治理",icon:"models"}]}><PlatformModelDeploymentWorkspace client={client} route={route} sessionRevision={1} onRouteChange={window.draw}/></ProductShell></FoundationProvider>);};
window.start=detail=>window.draw(detail?{navigationId:"platform-model-supply",task:"deployment-detail",deploymentId:"123"}:{navigationId:"platform-model-supply",task:"deployments",candidatePage:1,deploymentPage:1});window.start(false);`;
  fs.writeFileSync(path.join(temporary, "index.html"), '<div id="root"></div><script type="module" src="/harness.tsx"></script>', { mode: 0o600 });
  fs.writeFileSync(path.join(temporary, "harness.tsx"), harness, { mode: 0o600 });
  const { createServer } = await import(pathToFileURL(fromFrontend.resolve("vite")));
  const react = (await import(pathToFileURL(fromFrontend.resolve("@vitejs/plugin-react")))).default;
  const alias = ["react/jsx-runtime", "react/jsx-dev-runtime", "react", "react-dom/client", "react-dom", "@fluentui/react-components"]
    .map(name => ({ find: new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`), replacement: fromFrontend.resolve(name) }));
  server = await createServer({ configFile: false, root: temporary, cacheDir: cache.cacheDir, plugins: [react()], resolve: { alias },
    server: { host: "127.0.0.1", port: 0, hmr: false, fs: { allow: [temporary, frontend] } }, logLevel: "error" });
  await server.listen(); origin = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ channel: "chrome", headless: true });
});
after(async () => { fs.writeFileSync(path.join(temporary, "observations.json"), JSON.stringify({ kind: "ISOLATED_HTTP_UI", observations }, null, 2), { mode: 0o600 }); await cache?.close(); process.stdout.write(JSON.stringify({ evidence: temporary }) + "\n"); });

async function open(state = "success", detail = false, width = 1440, duplicateTenants = false) {
  const page = await browser.newPage({ viewport: { width, height: 1000 }, reducedMotion: "reduce" });
  const errors = []; page.on("pageerror", error => errors.push(error.name));
  let writes = 0, created, writtenInput;
  await page.route("**/api/v1/**", async route => {
    const request = route.request(), pathname = new URL(request.url()).pathname;
    const reply = (status, body) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (state === "denied" && pathname.endsWith("/facts")) return reply(403, { code: "SYNTHETIC_DENIED", message: "合成拒绝", traceId: "trc_synthetic" });
    if (pathname.endsWith("/facts")) return state === "partial" ? reply(503, { code: "SYNTHETIC_UNAVAILABLE", message: "合成不可用", traceId: "trc_synthetic" })
      : reply(200, { writesAvailable: state !== "disabled", counts: { total: 1, drafts: 1, verified: 0, published: 0, disabled: 0 } });
    if (pathname.endsWith("/candidates")) return reply(200, pageOf(options, 100));
    if (pathname === "/api/v1/platform/tenants") return reply(200, pageOf(duplicateTenants
      ? ["21", "22"].map(tenantId => ({ tenantId, tenantCode: "synthetic-" + tenantId, displayName: "合成同名租户", status: "ACTIVE" }))
      : [{ tenantId: "21", tenantCode: "synthetic-tenant", displayName: "合成租户", status: "ACTIVE" }], 100));
    if (/^\/api\/v1\/platform\/tenants\/(21|22)$/.test(pathname)) return reply(200, { tenantId: pathname.split("/").at(-1), displayName: duplicateTenants ? "合成同名租户" : "合成租户", status: "ACTIVE" });
    if (request.method() === "POST" && pathname === "/api/v1/platform/model-supply/deployments") {
      writes++; const input = request.postDataJSON(); writtenInput = input; assert.equal(input.modelConfirmed, true);
      created = { ...deployment, ...input, id: "124", inputs: { ...deployment.inputs, parameterSource: input.parameterSource, plannedTenantIds: input.plannedTenantIds, rerankCandidateLimit: input.rerankCandidateLimit } };
      await new Promise(resolve => setTimeout(resolve, 350)); return reply(201, created);
    }
    if (pathname === "/api/v1/platform/model-supply/deployments" || /\/deployments\/\d+$/.test(pathname)) {
      if (state === "loading") return new Promise(resolve => { releases.push(() => route.abort().catch(() => {}).finally(resolve)); });
      if (state === "error") return reply(500, { code: "SYNTHETIC_FAILURE", message: "合成失败", traceId: "trc_synthetic" });
      if (state === "empty" && /\/\d+$/.test(pathname)) return reply(404, { code: "MODEL_RUNTIME.RESOURCE.0002", message: "合成不存在", traceId: "trc_synthetic" });
      return reply(200, /\/\d+$/.test(pathname) ? created ?? deployment : pageOf(state === "empty" ? [] : [created ?? deployment]));
    }
    throw new Error("未声明的隔离请求：" + pathname);
  });
  await page.goto(origin, { waitUntil: "domcontentloaded" }); await page.waitForFunction(() => typeof window.start === "function");
  if (detail) await page.evaluate(() => window.start(true));
  await page.locator(`.platform-deployment-page[data-read-state="${state === "success" || (state === "empty" && !detail) ? "ready" : state}"]`).waitFor();
  return { page, errors, writes: () => writes, writtenInput: () => writtenInput };
}

test("S2 两页七态、三尺寸与原生两档文字", async () => {
  for (const state of ["loading", "empty", "success", "partial", "disabled", "error", "denied"]) for (const detail of [false, true]) for (const width of [390, 1024, 1440]) for (const scale of [1, 2]) {
    if (process.env.S2_PAGE_SCALE && Number(process.env.S2_PAGE_SCALE) !== scale) continue;
    const { page, errors } = await open(state, detail, width);
    try {
      if (scale === 2) await enlargeText(page);
      const observed = await layout(page); assert.equal(observed.overflow, false, JSON.stringify({ state, detail, width, scale, observed })); assert.deepEqual(observed.clipped, []);
      assert.deepEqual(errors, []); observations.push({ case: "page", state, detail, width, scale, ...observed });
      if (state === "success" && scale === 1) {
        assert.equal(await page.getByRole("navigation", { name: "对象工作区", exact: true }).evaluate(element => getComputedStyle(element).marginBottom), "24px");
        assert.equal(await page.locator('.platform-connection-status').first().evaluate(element => getComputedStyle(element).backgroundColor), "rgb(245, 244, 237)", "草稿沿用原型中性状态色");
      }
      if (state === "success" && width === 390) await page.screenshot({ path: path.join(temporary, `page-${detail ? "detail" : "list"}-${scale}.png`), fullPage: true });
      if (["partial", "disabled"].includes(state)) assert.equal(await page.getByRole("button", { name: detail ? "执行能力验证" : "建立部署草稿", exact: true }).isDisabled(), true);
    } finally { await page.close(); }
  }
});

test("S2 验收在同名租户中只选择精确身份", async () => {
  const { page, errors, writes, writtenInput } = await open("success", false, 1440, true);
  try {
    await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
    const modal = page.getByRole("dialog");
    await page.waitForFunction(() => !document.querySelector('.platform-deployment-form button[type="submit"]')?.disabled);
    await modal.getByLabel("部署名称", { exact: true }).fill("同名租户精确草稿");
    await modal.getByLabel("已发现模型", { exact: true }).selectOption("0");
    await modal.getByLabel("上下文窗口（Token）", { exact: true }).fill("128");
    await modal.getByLabel("最大输出（Token）", { exact: true }).fill("64");
    await modal.getByLabel("参数来源", { exact: true }).fill("合成文档 v1");
    await modal.getByLabel("我已确认模型类型、能力和参数来源", { exact: true }).check();
    await selectPlannedTenants(modal, ["21", "22"].map(tenantId => ({ tenantId, displayName: "合成同名租户", status: "ACTIVE" })), ["22"]);
    await modal.getByRole("button", { name: "保存部署草稿", exact: true }).click();
    await page.getByRole("heading", { name: "同名租户精确草稿", exact: true }).waitFor();
    assert.deepEqual(writtenInput().plannedTenantIds, ["22"]);
    assert.equal(writes(), 1); assert.deepEqual(errors, []);
  } finally { await page.close(); }
});

test("S2 目录与精确详情往返不能复用另一种读取投影", async () => {
  const { page, errors, writes } = await open("success", false, 1440);
  try {
    for (let turn = 0; turn < 2; turn++) {
      await page.getByRole("button", { name: "查看合成对话部署", exact: true }).click();
      await page.getByRole("heading", { name: "合成对话部署", exact: true }).waitFor();
      await page.getByRole("button", { name: "返回部署目录", exact: true }).first().click();
      await page.getByRole("button", { name: "查看合成对话部署", exact: true }).waitFor({ timeout: 3000 });
    }
    assert.deepEqual(errors, []); assert.equal(writes(), 0);
  } finally { await page.close(); }
});

test("S2 三类型和未知候选完整表单、键盘、取消及单次保存", async () => {
  for (const selection of [0, 1, 2, 3]) for (const width of [390, 1024, 1440]) for (const scale of [1, 2]) {
    if (process.env.S2_FORM_CASE && process.env.S2_FORM_CASE !== `${selection}-${width}-${scale}`) continue;
    const { page, errors, writes } = await open("success", false, width);
    try {
      await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
      const modal = page.getByRole("dialog"); await page.waitForFunction(() => !document.querySelector('.platform-deployment-form button[type="submit"]')?.disabled);
      await modal.getByLabel("已发现模型", { exact: true }).selectOption(String(selection));
      assert.equal(await modal.locator(".deployment-selected-model").innerText(),
        await modal.getByLabel("已发现模型", { exact: true }).locator("option:checked").innerText(), "已选择模型的完整说明必须与已接受原型一致");
      if (selection === 3) await modal.getByLabel("模型类型", { exact: true }).selectOption("RERANK");
      const type = selection === 3 ? "RERANK" : options[selection].modelType;
      await modal.getByLabel("部署名称", { exact: true }).fill("隔离完整表单");
      await modal.getByLabel("上下文窗口（Token）", { exact: true }).fill("128");
      await modal.getByLabel(type === "CHAT" ? "最大输出（Token）" : type === "EMBEDDING" ? "向量维度" : "重排候选上限", { exact: true }).fill("20");
      await modal.getByLabel("参数来源", { exact: true }).fill("合成文档 v1");
      const styles = await modal.evaluate(element => {
        const fact = target => { const style = getComputedStyle(target), rect = target.getBoundingClientRect(); return {
          width: rect.width, height: rect.height, size: style.fontSize, lineHeight: style.lineHeight, color: style.color,
          padding: style.padding, margin: style.margin, inset: style.inset, maxHeight: style.maxHeight, maxWidth: style.maxWidth,
          display: style.display, position: style.position, boxSizing: style.boxSizing, overflow: style.overflow }; };
        return { surface: fact(element), form: fact(element.querySelector("form")), button: fact(element.querySelector("footer button")),
          small: fact(element.querySelector(".deployment-field > small")), help: fact(element.querySelector(".deployment-help")), note: fact(element.querySelector(".platform-connection-note")) };
      });
      observations.push({ case: "styles", selection, width, scale, styles });
      assert.equal(styles.surface.width, Math.min(512, width - 38), "手机弹窗必须匹配已接受原生 dialog 的边界");
      assert.equal(styles.form.margin, "0px", "表单不能继承全局底部外距");
      assert.equal(styles.surface.overflow, "hidden", "只能滚动正文，不能滚动整个弹窗");
      assert.equal(styles.small.size, "13.3333px");
      assert.equal(styles.help.size, "16px");
      assert.equal(styles.note.size, "16px");
      const fontSnapshot = () => modal.locator('.deployment-field input,.deployment-field select').evaluateAll(elements => elements.map(element => ({ font: getComputedStyle(element).fontSize, inline: element.style.fontSize, visible: getComputedStyle(element).visibility, transition: getComputedStyle(element).transition, animation: getComputedStyle(element).animation, width: element.getBoundingClientRect().width, height: element.getBoundingClientRect().height })));
      const beforeFonts = await fontSnapshot();
      if (scale === 2) await enlargeText(page);
      const immediateFonts = await fontSnapshot();
      await modal.getByLabel("我已确认模型类型、能力和参数来源", { exact: true }).focus(); await page.keyboard.press("Space");
      await modal.getByRole("group", { name: "授权租户", exact: true }).getByLabel("合成租户", { exact: true }).focus(); await page.keyboard.press("Space");
      const fonts = await modal.locator('.deployment-field input,.deployment-field select').evaluateAll(elements => elements.map(element => parseFloat(getComputedStyle(element).fontSize)));
      assert.equal(fonts.every(size => size >= 16 * scale), true, JSON.stringify({ reason: "原生输入未按实际文字放大", selection, width, scale, fonts, beforeFonts, immediateFonts, afterFonts: await fontSnapshot() }));
      const header = await modal.locator("header").boundingBox(), footer = await modal.locator("footer").boundingBox();
      assert.ok(header.y >= 0 && footer.y + footer.height <= 1001, JSON.stringify({ width, scale, header, footer }));
      const overflow = await modal.evaluate(element => element.scrollWidth > element.clientWidth + 1); assert.equal(overflow, false);
      if (width === 390 && scale === 2) await page.screenshot({ path: path.join(temporary, `form-${selection}-${scale}.png`) });
      observations.push({ case: "form", selection, type, width, scale, fonts, header, footer, overflow, fontComputation: { beforeFonts, immediateFonts } });
      if (selection === 3 && width === 1440 && scale === 1) {
        await modal.getByRole("button", { name: "保存部署草稿", exact: true }).evaluate(button => { button.click(); button.click(); });
        await page.keyboard.press("Escape"); assert.equal(await modal.isVisible(), true, "保存途中不能丢弃回执");
        await page.getByRole("heading", { name: "隔离完整表单", exact: true }).waitFor(); assert.equal(writes(), 1);
        await page.waitForFunction(() => document.activeElement?.tagName === "H1" && document.activeElement?.textContent === "平台部署发布", undefined, { timeout: 3000 });
      } else {
        await page.keyboard.press("Escape"); await modal.waitFor({ state: "hidden" });
        await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
        assert.equal(await page.getByRole("dialog").getByLabel("已发现模型", { exact: true }).inputValue(), "");
        assert.equal(await page.getByRole("dialog").locator('input:checked').count(), 0);
        assert.equal(writes(), 0);
      }
      assert.deepEqual(errors, []);
    } finally { await page.close(); }
  }
});
