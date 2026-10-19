/* WP-0019 S1 真实前端客户端与受控 HTTP 响应回归；不访问生产或厂商。 */
const fs = require("node:fs");
const path = require("node:path");
const { createRequire } = require("node:module");
const { pathToFileURL } = require("node:url");
const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const { enlargeText } = require("../development/acceptance-overview.cjs");
const { createBrowserTestCache } = require("./browser-test-cache.cjs");
const repository = path.resolve(__dirname, "../..");
const frontend = path.join(repository, "frontend");
const fromFrontend = createRequire(path.join(frontend, "package.json"));
const id = "610000000000000001";
let server, browser, origin, temporary, cache;

before(async () => {
  temporary = fs.mkdtempSync("/private/tmp/wp0019-partial-source-"); fs.chmodSync(temporary, 0o700);
  cache = createBrowserTestCache(temporary, async () => {
    try { await browser?.close(); } finally { await server?.close(); }
  });
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
  server = await createServer({ configFile: false, root: temporary, cacheDir: cache.cacheDir, plugins: [react()], resolve: { alias },
    server: { host: "127.0.0.1", port: 0, fs: { allow: [temporary, frontend] } }, logLevel: "error" });
  await server.listen(); origin = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ channel: "chrome", headless: true });
});
after(async () => { await cache?.close(); });

function gate() { let release, arrive; return { wait: new Promise(r => { release = r; }), reached: new Promise(r => { arrive = r; }), release: () => release(), arrive: () => arrive() }; }
async function visit(action, width = 1440) {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 1000 }, reducedMotion: "reduce" });
  const page = await context.newPage(); page.setDefaultTimeout(5000);
  const config = { writesAvailable: true, faults: {}, label: "本轮连接", count: 21, calls: [], writes: 0 };
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
    const item = { modelCode: "本轮模型", displayName: "本轮模型", modelType: "CHAT", capabilities: ["CHAT"], classification: "CONFIRMED_BY_PROVIDER" };
    const rows = key === "directory" ? [connection] : [item];
    const body = key === "presets" ? { writesAvailable: config.writesAvailable, version: "synthetic", items: [{ id: "CUSTOM", name: "自定义连接", capabilities: ["CHAT"], variants: [{
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

test("候选只显示受控名称，不将供应商编码作为可见文本", () => visit(async (page, config) => {
  config.faults.candidates = { body: JSON.stringify({ items: [{ modelCode: "ep-synthetic-opaque-code",
    displayName: "独立的受控模型名称", modelType: null, capabilities: [], classification: "REQUIRES_ADMIN_CONFIRMATION" }],
    pageNumber: 1, pageSize: 20, totalElements: 1, hasPrevious: false, hasNext: false }) };
  await page.evaluate(() => window.start(true));
  await page.locator("table tbody tr").waitFor();
  const text = await page.locator("main").innerText();
  assert.match(text, /独立的受控模型名称/);
  assert.doesNotMatch(text, /ep-synthetic-opaque-code/);
}));

test("390px非空候选卡片保留原型模型与能力字段标签", () => visit(async page => {
  await page.evaluate(() => window.start(true));
  await page.locator("table tbody tr").waitFor();
  const labels = await page.locator("table tbody tr").first().locator("td").evaluateAll(cells =>
    cells.map(cell => getComputedStyle(cell, "::before").content));
  assert.deepEqual(labels, ['"发现模型"', '"能力"']);
}, 390));

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

for (const width of [390, 1024, 1440]) for (const detail of [false, true]) test(`${width}px ${detail ? "详情" : "目录"} 真实读取字段暂停及200%文字`, () => visit(async (page, config) => {
  config.writesAvailable = false;
  await page.evaluate(detail => window.start(detail), detail);
  await page.locator('[data-read-state="disabled"]').waitFor();
  await page.getByText("暂不可用", { exact: true }).waitFor();
  const command = page.getByRole("button", { name: detail ? "执行连接探测" : "新增供应商连接", exact: true });
  assert.equal(await command.isDisabled(), true);
  const assertDisabledAppearance = async doubled => {
    // 等待已有颜色过渡结束，再比较最终可见状态，不取样旧状态的动画起点。
    await command.evaluate(async e => {
      getComputedStyle(e).color;
      await Promise.all(e.getAnimations().map(animation => animation.finished));
    });
    assert.deepEqual(await command.evaluate(e => {
      const style = getComputedStyle(e);
      return Object.fromEntries(["fontWeight", "color", "backgroundColor", "borderColor", "padding", "minWidth", "opacity"].map(key => [key, style[key]]));
    }), {
      fontWeight: "620", color: "rgb(94, 93, 89)", backgroundColor: "rgb(245, 244, 237)",
      borderColor: "rgb(232, 230, 220)", padding: "6px 12px", minWidth: "auto", opacity: "0.76",
    }, "暂停操作须使用已接受原型的暖色禁用样式");
    if (doubled) assert.ok(Math.abs((await command.boundingBox()).height - 54) <= 1,
      "200%文字下暂停操作须保留原型的54px内容高度");
  };
  await assertDisabledAppearance(false);
  await command.hover({ force: true });
  await assertDisabledAppearance(false);
  assert.equal(await page.getByRole("searchbox").first().isEnabled(), true);
  const heading = page.getByRole("heading", { level: 1 });
  const size = await heading.evaluate(e => parseFloat(getComputedStyle(e).fontSize));
  await enlargeText(page);
  await page.waitForFunction(size => parseFloat(getComputedStyle(document.querySelector("h1")).fontSize) >= size * 2, size);
  await assertDisabledAppearance(true);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
  await page.getByRole("searchbox").first().focus();
  assert.equal(await page.getByRole("searchbox").first().evaluate(e => e === document.activeElement), true);
  config.writesAvailable = true;
  await page.evaluate(() => window.refreshRead());
  await page.locator('[data-read-state="ready"]').waitFor();
  assert.equal(await command.isEnabled(), true);
}, width));

test("暂停优先级不掩盖辅助未知与安全拒绝", () => visit(async (page, config) => {
  config.writesAvailable = false; config.faults.summary = { status: 503 };
  await page.evaluate(() => window.refreshRead()); await assertPartial(page, false);
  config.faults.summary = { status: 403 };
  await page.evaluate(() => window.refreshRead());
  await page.getByText("当前会话不能访问此任务", { exact: true }).waitFor();
  assert.equal(await page.getByText("暂不可用", { exact: true }).count(), 0);
}));

test("旧表单重读暂停时卸载秘密且不能恢复提交", () => visit(async (page, config) => {
  await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
  await page.locator('input[type="password"]').first().fill("synthetic-local-only-value");
  config.writesAvailable = false;
  await page.evaluate(() => window.refreshRead());
  await page.locator('[data-read-state="disabled"]').waitFor();
  await page.getByRole("dialog").waitFor({ state: "detached" });
  assert.equal(await page.locator('input[type="password"]').count(), 0);
  assert.equal(await page.getByRole("button", { name: "新增供应商连接", exact: true }).isDisabled(), true);
}));

for (const width of [390, 1024, 1440]) for (const kind of ["loading", "empty", "error", "denied"]) test(`${width}px ${kind} 两页页头与恢复操作匹配原型`, () => visit(async (page, config) => {
  for (const detail of [false, true]) {
    config.faults = {}; config.count = 0; const key = detail ? "connection" : "directory"; let pending;
    if (kind === "loading") { pending = gate(); config.faults[key] = { gate: pending }; }
    else if (kind === "empty") config.faults[key] = detail
      ? { status: 404, body: JSON.stringify({ code: "MODEL_RUNTIME.RESOURCE.0001", message: "资源不存在" }) }
      : { body: JSON.stringify({ items: [], pageNumber: 1, pageSize: 20, totalElements: 0, hasPrevious: false, hasNext: false }) };
    else config.faults[key] = { status: kind === "denied" ? 403 : 503 };
    try {
      await page.reload({ waitUntil: "domcontentloaded" });
      if (detail) await page.evaluate(() => window.start(true));
      const panel = page.locator(`main section[data-kind="${kind}"]`); await panel.waitFor();
      const heading = page.getByRole("heading", { level: 1 });
      const normal = await heading.evaluate(e => parseFloat(getComputedStyle(e).fontSize));
      for (const doubled of [false, true]) {
        if (doubled) await enlargeText(page);
        await page.waitForFunction(expected => Math.abs(parseFloat(getComputedStyle(document.querySelector("h1")).fontSize) - expected) < 0.01, normal * (doubled ? 2 : 1));
        if (kind === "empty") {
          assert.equal(await heading.evaluate(e => getComputedStyle(e.parentElement).display), "grid", "空态页头须沿用原型网格与状态间距");
          const line = page.locator(".platform-connection-object-line");
          assert.equal(await line.evaluate(e => getComputedStyle(e).color), "rgb(94, 93, 89)");
          assert.equal(await line.evaluate(e => getComputedStyle(e).fontSize), doubled ? "32px" : "16px");
        }
        const action = kind === "loading" ? undefined : kind === "empty"
          ? page.getByRole("button", { name: detail ? "返回连接目录" : "新增供应商连接", exact: true }) : panel.getByRole("button");
        if (action) {
          const appearance = await action.evaluate(async e => {
            getComputedStyle(e).color; await Promise.all(e.getAnimations().map(a => a.finished));
            const s = getComputedStyle(e); return { weight: s.fontWeight, padding: s.padding, gap: s.gap, minimum: s.minWidth, font: s.fontSize, height: e.getBoundingClientRect().height };
          });
          assert.deepEqual(appearance, { weight: "620", padding: "6px 12px", gap: "8px", minimum: "auto", font: doubled ? "28px" : "14px", height: doubled ? 54 : width <= 680 ? 44 : 36 });
          await action.focus(); assert.equal(await action.evaluate(e => e === document.activeElement), true);
        }
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
        assert.equal(await panel.evaluate(e => [...e.querySelectorAll("h2,p,button")].every(x => x.scrollHeight <= x.clientHeight + 1)), true);
      }
    } finally { pending?.release(); }
  }
}, width));

for (const width of [390, 1024, 1440]) for (const detail of [false, true]) test(`${width}px ${detail ? "详情" : "目录"} 正常页原型动作`, () => visit(async page => {
  await page.evaluate(detail => window.start(detail), detail);
  await page.locator('[data-read-state="ready"]').waitFor();
  const commands = detail ? [page.getByRole("button", { name: "返回连接目录", exact: true }), page.getByRole("button", { name: "执行连接探测", exact: true })]
    : [page.getByRole("button", { name: "新增供应商连接", exact: true }), page.getByRole("button", { name: "查看本轮连接", exact: true })];
  for (const doubled of [false, true]) {
    if (doubled) await enlargeText(page);
    for (const command of commands) {
      await command.scrollIntoViewIfNeeded();
      await page.waitForFunction(({label,doubled}) => [...document.querySelectorAll("button")].some(e => (e.getAttribute("aria-label") || e.textContent.trim()) === label && getComputedStyle(e).fontSize === (doubled ? "28px" : "14px")), {label: await command.getAttribute("aria-label") || (await command.innerText()).trim(), doubled});
      const actual = await command.evaluate(e => { const s = getComputedStyle(e);return {weight:s.fontWeight,padding:s.padding,gap:s.gap,minWidth:s.minWidth,height:e.getBoundingClientRect().height};});
      const tableAction = !detail && command === commands[1];
      assert.deepEqual(actual, {weight:"620",padding:tableAction?"4px 12px":"6px 12px",gap:"8px",minWidth:tableAction&&width>680?"0px":"auto",height:doubled?(tableAction?50:54):width<=680?44:tableAction?32:36});
    }
  }
}, width));

for (const width of [390, 1024, 1440]) for (const detail of [false, true]) test(`${width}px ${detail ? "详情" : "目录"} 正常页正文遵循原型排版`, () => visit(async page => {
  await page.evaluate(detail => window.start(detail), detail);
  await page.locator('[data-read-state="ready"]').waitFor();
  assert.equal(await page.locator('input[type="search"]').evaluate(e => getComputedStyle(e).minWidth), "auto");
  for (const doubled of [false, true]) {
    if (doubled) await enlargeText(page);
    await page.waitForFunction(({detail, doubled}) => getComputedStyle(document.querySelector(detail ? ".platform-connection-page h2" : ".platform-connection-table strong")).fontSize === (detail ? doubled ? "50px" : "25px" : doubled ? "30px" : "15px"), {detail, doubled});
    const appearance = await page.locator(".platform-connection-page").evaluate((root, detail) => {
      const read = (selector, properties) => { const s = getComputedStyle(root.querySelector(selector)); return Object.fromEntries(properties.map(key => [key, s[key]])); };
      if (!detail) return { table:read("table", ["color", "lineHeight"]), name:read("tbody strong", ["color", "fontSize", "lineHeight"]), summary:read("tbody small", ["color", "lineHeight"]) };
      return { titles:[...root.querySelectorAll("h2")].map(e => { const s = getComputedStyle(e); return [s.fontWeight, s.lineHeight]; }),
        kicker:read(".platform-connection-kicker", ["fontWeight", "letterSpacing"]), description:read(".platform-connection-release > p", ["lineHeight"]) };
    }, detail);
    assert.deepEqual(appearance, detail
      ? {titles:Array(3).fill(["500", doubled?"67.5px":"33.75px"]), kicker:{fontWeight:"650",letterSpacing:doubled?"0.56px":"0.28px"}, description:{lineHeight:doubled?"51.2px":"25.6px"}}
      : {table:{color:"rgb(20, 20, 19)",lineHeight:"24px"},name:{color:"rgb(20, 20, 19)",fontSize:doubled?"30px":"15px",lineHeight:doubled?"48px":"24px"},summary:{color:"rgb(20, 20, 19)",lineHeight:doubled?"48px":"24px"}});
  }
}, width));

for (const detail of [false, true]) test(`${detail ? "详情" : "目录"} 路由标题焦点使用原型强调环`, () => visit(async page => {
  await page.evaluate(detail => window.start(detail), detail);
  await page.locator('[data-read-state="ready"]').waitFor();
  const heading = page.getByRole("heading", { level: 1 }); await heading.focus();
  assert.equal(await heading.evaluate(e => e === document.activeElement && e.matches(":focus-visible")), true);
  assert.deepEqual(await heading.evaluate(e => { const s = getComputedStyle(e);return {style:s.outlineStyle,width:s.outlineWidth,color:s.outlineColor,offset:s.outlineOffset}; }),
    {style:"solid",width:"2px",color:"oklab(0.514837 0.0810745 0.066312)",offset:"2px"});
}));

test("平板侧栏保留原型账户分隔区但隐藏身份正文", () => visit(async page => {
  assert.deepEqual(await page.locator(".product-sidebar-account").evaluate(e => ({height:e.getBoundingClientRect().height,
    border:getComputedStyle(e).borderTopWidth,hidden:[...e.children].every(child => getComputedStyle(child).display === "none")})),
    {height:25,border:"1px",hidden:true});
}, 1024));
