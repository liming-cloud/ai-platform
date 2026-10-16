/* WP-0019 S1 真实前端客户端与受控 HTTP 响应回归；不访问生产或厂商。 */
const fs = require("node:fs");
const path = require("node:path");
const { createRequire } = require("node:module");
const { pathToFileURL } = require("node:url");
const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const { enlargeText } = require("../development/acceptance-overview.cjs");
const repository = path.resolve(__dirname, "../..");
const frontend = path.join(repository, "frontend");
const fromFrontend = createRequire(path.join(frontend, "package.json"));
const id = "610000000000000001";
let server, browser, origin, temporary;

before(async () => {
  temporary = fs.mkdtempSync("/private/tmp/wp0019-partial-source-"); fs.chmodSync(temporary, 0o700);
  const harness = `import React from "react"; import {createRoot} from "react-dom/client";
import {FoundationProvider} from "/@fs/${frontend}/packages/design-system/src/providers/FoundationProvider.tsx";
import {ProductShell} from "/@fs/${frontend}/packages/design-system/src/shell/ProductShell.tsx";
import {ControlPlaneClient} from "/@fs/${frontend}/apps/console-web/src/api/control-plane-client.ts";
import {PlatformModelSupplyWorkspace} from "/@fs/${frontend}/apps/console-web/src/features/platform-governance/PlatformModelSupplyWorkspace.tsx";
import {PlatformProviderConnectionDetail} from "/@fs/${frontend}/apps/console-web/src/features/platform-governance/PlatformProviderConnectionDetail.tsx";
import "/@fs/${frontend}/apps/console-web/src/styles.css";
import "/@fs/${frontend}/packages/design-system/src/styles/tokens.css";
const gateway={request:(p,i)=>fetch("/api/v1"+p,i),login:async()=>({}),logout:async()=>{},restore:async()=>({})};
const makeClient=()=>new ControlPlaneClient(undefined,undefined,"/api/v1",gateway);
window.client=makeClient(); let identity=1;const root=createRoot(document.getElementById("root"));
const navigate=route=>window.draw(route);
window.draw=route=>{window.route=route;root.render(<FoundationProvider><ProductShell productName="可信 RAG" workspaceScope="platform"
activeNavigationId="models" accountLabel="隔离验收身份" navigation={[{id:"models",label:"模型治理",icon:"models"}]}>
{route.task==="connections"?<PlatformModelSupplyWorkspace key={identity} client={window.client} route={route} sessionRevision={identity} onRouteChange={navigate}/>
:<PlatformProviderConnectionDetail key={identity} client={window.client} route={route} onRouteChange={navigate}/>}</ProductShell></FoundationProvider>);};
window.start=(detail=false,query="")=>window.draw(detail?{navigationId:"platform-model-supply",task:"connection-detail",connectionId:"${id}",candidatePage:1,query}
:{navigationId:"platform-model-supply",task:"connections",page:1,query});
window.refreshRead=()=>window.draw({...window.route});
window.replaceIdentity=()=>{window.client=makeClient();identity++;window.start();};
window.start();`;
  fs.writeFileSync(path.join(temporary, "index.html"), '<div id="root"></div><script type="module" src="/harness.tsx"></script>');
  fs.writeFileSync(path.join(temporary, "harness.tsx"), harness);
  const { createServer } = await import(pathToFileURL(fromFrontend.resolve("vite")));
  const react = (await import(pathToFileURL(fromFrontend.resolve("@vitejs/plugin-react")))).default;
  const alias = ["react/jsx-runtime", "react/jsx-dev-runtime", "react", "react-dom/client", "react-dom", "@fluentui/react-components"]
    .map(name => ({ find: new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`), replacement: fromFrontend.resolve(name) }));
  server = await createServer({ configFile: false, root: temporary, plugins: [react()], resolve: { alias },
    server: { host: "127.0.0.1", port: 0, fs: { allow: [temporary, frontend] } }, logLevel: "error" });
  await server.listen(); origin = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ channel: "chrome", headless: true });
});
after(async () => { await browser?.close(); await server?.close(); });

function gate() { let release, arrive; return { wait: new Promise(r => { release = r; }), reached: new Promise(r => { arrive = r; }), release: () => release(), arrive: () => arrive() }; }
async function visit(action, width = 1440) {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 1000 }, reducedMotion: "reduce" });
  const page = await context.newPage(); page.setDefaultTimeout(5000);
  const config = { faults: {}, label: "本轮连接", count: 21, calls: [], writes: 0 };
  const errors = [];
  page.on("pageerror", e => errors.push(e.message));
  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url()), pathname = url.pathname;
    if (route.request().method() !== "GET") { config.writes++; return route.abort(); }
    const key = pathname.endsWith("/presets") ? "presets" : pathname.endsWith("summary") ? "summary"
      : pathname.endsWith("publication") ? "publication" : pathname.endsWith("model-candidates")
        ? url.searchParams.get("pageSize") === "1" ? "count" : "candidates" : pathname.endsWith(id) ? "connection" : "directory";
    config.calls.push(key);
    const connection = { id, displayName: config.label, providerKind: "OPENAI_COMPATIBLE", version: 3, status: "VERIFIED", published: true,
      credentialConfigured: true, discoveryCredentialsConfigured: false, discoveryProfile: "OPENAI_MODELS_API", lastProbeSucceeded: true,
      lastProbedAt: new Date().toISOString(), preset: { id: "CUSTOM", capability: "CHAT" } };
    const item = { modelCode: "本轮模型", modelType: "CHAT", capabilities: ["CHAT"], classification: "CONFIRMED_BY_PROVIDER" };
    const rows = key === "directory" ? [connection] : [item];
    const body = key === "presets" ? { version: "synthetic", items: [{ id: "CUSTOM", name: "自定义连接", capabilities: ["CHAT"], variants: [{
      capability: "CHAT", runtimeProtocol: "OPENAI_COMPATIBLE", endpointTemplate: "", workspaceRequired: false, discoveryProfile: "OPENAI_MODELS_API",
    }] }] } : key === "summary" ? { publishedConnections: config.count } : key === "connection" ? connection
      : key === "publication" ? { connectionId: id, connectionRevision: 3, published: true }
      : { items: rows, pageNumber: 1, pageSize: 20, totalElements: 1, hasPrevious: false, hasNext: false };
    const fault = config.faults[key];
    if (fault?.gate) { fault.gate.arrive(); await fault.gate.wait; }
    if (fault?.abort) return route.abort("failed");
    await route.fulfill({ status: fault?.status ?? 200, contentType: fault?.contentType ?? "application/json",
      body: fault && Object.hasOwn(fault, "body") ? fault.body : JSON.stringify(body) });
  });
  try { await page.goto(origin, { timeout: 15000 }); await page.getByText("21 个已发布连接", { exact: true }).waitFor(); await action(page, config);
    assert.equal(config.writes, 0); assert.deepEqual(errors, []);
  } finally { await context.close(); }
}
async function assertPartial(page, detail) {
  await page.getByText("部分完成", { exact: true }).waitFor();
  assert.equal(await page.getByRole("button", { name: detail ? "执行连接探测" : "新增供应商连接", exact: true }).isDisabled(), true);
  assert.equal(await page.getByRole("button", { name: "重新载入", exact: true }).isEnabled(), true);
}

test("辅助摘要503保留本轮目录、未知计数与只读恢复", () => visit(async (page, config) => {
  config.faults.summary = { status: 503 }; await page.evaluate(() => window.refreshRead());
  await assertPartial(page, false); assert.match(await page.locator("main").innerText(), /发布摘要暂未取得|本轮连接/);
  assert.equal(await page.getByText("21 个已发布连接", { exact: true }).count(), 0);
  assert.equal(await page.getByText("0 个已发布连接", { exact: true }).count(), 0);
  delete config.faults.summary; config.count = 8;
  await page.getByRole("button", { name: "重新载入", exact: true }).focus(); await page.keyboard.press("Enter");
  await page.getByText("8 个已发布连接", { exact: true }).waitFor();
  assert.equal(await page.getByRole("button", { name: "新增供应商连接", exact: true }).isEnabled(), true);
}));

for (const key of ["candidates", "publication"]) test(`详情${key}网络失败不沿用旧事实`, () => visit(async (page, config) => {
  config.faults[key] = { abort: true }; await page.evaluate(() => window.start(true));
  await assertPartial(page, true);
  const text = await page.locator("main").innerText();
  assert.match(text, key === "candidates" ? /发现模型暂未取得/ : /发布状态暂未取得/);
  if (key === "candidates") { assert.doesNotMatch(text, /本轮模型|0 个/); assert.match(text, /连接已发布/); }
  else { assert.match(text, /本轮模型/); assert.doesNotMatch(text, /连接已发布/); }
}));

test("搜索附加计数失败属于候选读取，不猜测全目录总数", () => visit(async (page, config) => {
  config.faults.count = { status: 502 }; await page.evaluate(() => window.start(true, "模型"));
  await assertPartial(page, true); assert.match(await page.locator("main").innerText(), /发现模型暂未取得/);
  assert.equal(config.calls.includes("count"), true);
}));

for (const key of ["directory", "presets", "connection"]) test(`主读取${key}失败不能降级`, () => visit(async (page, config) => {
  config.faults[key] = { status: 503 }; await page.evaluate(detail => window.start(detail), key === "connection");
  await page.getByText("载入失败", { exact: true }).waitFor();
  assert.equal(await page.getByText("部分完成", { exact: true }).count(), 0);
  assert.equal(await page.getByText("本轮连接", { exact: true }).count(), 0);
}));

for (const status of [401, 403, 404, 409]) test(`辅助原始${status}畸形正文保持失败关闭`, () => visit(async (page, config) => {
  config.faults.publication = { status, body: "{", contentType: "application/json" }; await page.evaluate(() => window.start(true));
  await page.getByText(status === 401 ? "浏览器会话已经失效" : status === 403 ? "访问受限" : "载入失败", { exact: true }).waitFor();
  assert.equal(await page.getByText("本轮连接", { exact: true }).count(), 0);
  assert.equal(await page.getByText("部分完成", { exact: true }).count(), 0);
}));

for (const body of ["{", "null", '{"connectionId":"wrong","connectionRevision":3,"published":true}'])
  test(`辅助成功响应非法不能降级或保留正文：${body}`, () => visit(async (page, config) => {
    config.faults.publication = { body }; await page.evaluate(() => window.start(true));
    await page.getByText("载入失败", { exact: true }).waitFor();
    assert.equal(await page.getByText("本轮连接", { exact: true }).count(), 0);
  }));

test("较早的辅助503不能掩盖迟到的403", () => visit(async (page, config) => {
  const pending = gate(); config.faults.publication = { status: 503 }; config.faults.candidates = { status: 403, gate: pending };
  await page.evaluate(() => window.start(true)); await pending.reached;
  assert.equal(await page.getByText("部分完成", { exact: true }).count(), 0);
  pending.release(); await page.getByText("访问受限", { exact: true }).waitFor();
  assert.equal(await page.getByText("本轮连接", { exact: true }).count(), 0);
}));

test("明确403立即清除页面，不等待悬挂发布读取", () => visit(async (page, config) => {
  const pending = gate(); config.faults.publication = { status: 503, gate: pending }; config.faults.candidates = { status: 403 };
  try {
    await page.evaluate(() => window.start(true)); await pending.reached;
    await page.getByText("访问受限", { exact: true }).waitFor();
    assert.equal(await page.getByText("本轮连接", { exact: true }).count(), 0);
  } finally { pending.release(); }
}));

for (const status of [401, 503]) test(`身份替换后旧辅助${status}不能覆盖当前目录`, () => visit(async (page, config) => {
  const pending = gate(); config.faults.candidates = { status, gate: pending };
  await page.evaluate(() => window.start(true)); await pending.reached;
  config.label = "新身份连接"; await page.evaluate(() => window.replaceIdentity());
  await page.getByText("新身份连接", { exact: true }).waitFor();
  const response = page.waitForResponse(r => r.url().includes("model-candidates")); pending.release(); await response;
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  assert.equal(await page.getByText("新身份连接", { exact: true }).isVisible(), true);
  assert.equal(await page.getByText("部分完成", { exact: true }).count(), 0);
}));

test("重读转partial同步关闭草稿并清除输入", () => visit(async (page, config) => {
  await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
  await page.getByLabel("一次性访问密钥", { exact: true }).fill("synthetic-private-input");
  config.faults.summary = { status: 503 }; await page.evaluate(() => window.refreshRead());
  await assertPartial(page, false);
  await page.getByRole("dialog").waitFor({ state: "detached" });
  assert.equal(await page.getByRole("dialog").count(), 0);
  assert.equal(await page.locator('input[type="password"]').count(), 0);
}));

for (const width of [390, 1024, 1440]) test(`${width}px partial 200%文字与键盘恢复`, () => visit(async (page, config) => {
  config.faults.publication = { status: 503 }; config.faults.candidates = { status: 502 };
  await page.evaluate(() => window.start(true)); await assertPartial(page, true);
  // 窄屏下说明在首屏之外；先使被测正文实际参与布局，再放大，不能把未渲染内容算作200%。
  await page.getByText("发现模型暂未取得，重新载入后再试。", { exact: true }).scrollIntoViewIfNeeded();
  await page.evaluate(() => document.fonts.ready);
  await enlargeText(page);
  // 样式提交后的首帧仍可能返回旧字号；等实际计算值达到32px，不降低文字放大断言。
  await page.waitForFunction(() => [...document.querySelectorAll(".platform-connection-section > p")]
    .some(e => getComputedStyle(e).fontSize === "32px"));
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
  assert.equal(await page.getByText("发现模型暂未取得，重新载入后再试。", { exact: true }).evaluate(e => getComputedStyle(e).fontSize), "32px");
  assert.equal(await page.locator(".fui-Badge,.platform-connection-status").evaluateAll(elements => elements.every(e => {
    const box = e.getBoundingClientRect(), range = document.createRange(); range.selectNodeContents(e);
    return [...range.getClientRects()].every(r => r.top >= box.top - 1 && r.bottom <= box.bottom + 1);
  })), true, "200%文字下连接与候选状态标签均不得裁切");
  if (width === 390) assert.equal(await page.locator(".platform-connection-descriptions > div").first().evaluate(e => {
    const label = e.querySelector("dt").getBoundingClientRect(), value = e.querySelector("dd").getBoundingClientRect();
    return Math.abs(label.left - value.left) <= 1 && label.bottom <= value.top + 1;
  }), true, "手机事实区须按已接受原型将标签与值上下排列");
  config.faults = {}; await page.getByRole("button", { name: "重新载入", exact: true }).focus(); await page.keyboard.press("Enter");
  await page.getByRole("heading", { name: "连接已发布", exact: true }).waitFor();
  assert.equal(await page.getByRole("button", { name: "执行连接探测", exact: true }).isEnabled(), true);
}, width));
