/* WP-0019 S1 源码浏览器回归：隔离服务与合成输入，不访问产品 API 或真实厂商。 */
const fs = require("node:fs");
const path = require("node:path");
const { createRequire } = require("node:module");
const { pathToFileURL } = require("node:url");
const assert = require("node:assert/strict");
const { enlargeText } = require("../development/acceptance-overview.cjs");

const repository = path.resolve(__dirname, "../..");
const frontend = path.join(repository, "frontend");
const fromFrontend = createRequire(path.join(frontend, "package.json"));
const capabilities = ["CHAT", "EMBEDDING", "RERANK"];
const variant = (capability, endpointTemplate, workspaceRequired = false, discoveryProfile = "OPENAI_MODELS_API") => ({
  capability, endpointTemplate, workspaceRequired, discoveryProfile,
  runtimeProtocol: capability === "RERANK" ? "VENDOR_POST_PROCESSOR" : "OPENAI_COMPATIBLE",
});
const preset = (id, name, region, regionLabel, variants) => ({
  id, name, region, regionLabel, variants, capabilities: variants.map(item => item.capability),
  discoveryProfile: variants[0].discoveryProfile,
});
// 夹具只用于固定前端请求合同；厂商真实性和后端预设另有独立验收。
const directory = { version: "2026-10-12", items: [
  preset("CUSTOM", "自定义连接", "unspecified", "自行配置", capabilities.map(value => variant(value, ""))),
  preset("DEEPSEEK", "DeepSeek", "provider-default", "厂商默认", [variant("CHAT", "https://api.deepseek.com")]),
  preset("ALIYUN", "阿里云百炼", "cn-beijing", "中国北京", [
    variant("CHAT", "https://{workspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1", true),
    variant("EMBEDDING", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    variant("RERANK", "https://{workspaceId}.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank", true),
  ]),
  preset("SILICONFLOW", "硅基流动", "cn", "中国区", capabilities.map(value => variant(value,
    `https://api.siliconflow.cn/v1${value === "RERANK" ? "/rerank" : ""}`))),
  preset("VOLCENGINE", "火山方舟", "cn-beijing", "中国北京", [
    variant("CHAT", "https://ark.cn-beijing.volces.com/api/v3", false, "VOLCENGINE_ARK_CONTROL_PLANE"),
  ]),
] };

async function run() {
  assert.equal(require("playwright/package.json").version, "1.62.1", "需要已锁定的 Playwright 运行时");
  const temporary = fs.mkdtempSync(path.join("/private/tmp/", "wp0019-form-source-"));
  fs.chmodSync(temporary, 0o700);
  process.stdout.write(`${JSON.stringify({ sourceArtifacts: temporary, productionAcceptance: false })}\n`);
  const harness = `import React from "react";
import {createRoot} from "react-dom/client";
import {FoundationProvider} from "/@fs/${repository}/frontend/packages/design-system/src/providers/FoundationProvider.tsx";
import {PlatformModelSupplyWorkspace} from "/@fs/${repository}/frontend/apps/console-web/src/features/platform-governance/PlatformModelSupplyWorkspace.tsx";
import {PlatformProviderConnectionDetail} from "/@fs/${repository}/frontend/apps/console-web/src/features/platform-governance/PlatformProviderConnectionDetail.tsx";
import {ApiRequestError} from "/@fs/${repository}/frontend/apps/console-web/src/api/control-plane-client.ts";
import "/@fs/${repository}/frontend/apps/console-web/src/styles.css";
import "/@fs/${repository}/frontend/packages/design-system/src/styles/tokens.css";
const directory=${JSON.stringify(directory)};
const draft=()=>({id:"610000000000000001",displayName:"源码隔离连接",providerKind:"OPENAI_COMPATIBLE",version:0,status:"DRAFT",published:false,
  credentialConfigured:true,discoveryCredentialsConfigured:false,discoveryProfile:"OPENAI_MODELS_API",
  authenticationType:"API_KEY",preset:{id:"CUSTOM",capability:"CHAT"}});
window.testState={writes:[],fail:false,pending:false,route:null,connection:draft(),detailCalls:[],failProbe:false,badBinding:false,autoRoute:false,
  connectionError:null,presetError:null,candidateError:null,emptyCandidates:false,directoryContext:undefined};
const failRead=error=>{if(error)throw new ApiRequestError(error.status,error.code,"合成读取失败","source-check");};
const client={hasSession:()=>true,
  listPlatformProviderConnections:async()=>({items:[],totalElements:0,hasPrevious:false,hasNext:false}),
  getPlatformProviderConnectionDirectorySummary:async()=>({publishedConnections:0}),
  getPlatformProviderPresets:async()=>{failRead(window.testState.presetError);return directory;},
  getPlatformProviderConnection:async()=>{failRead(window.testState.connectionError);return {...window.testState.connection};},
  getPlatformProviderConnectionPublication:async(id,version)=>({connectionId:id,
    connectionRevision:window.testState.badBinding?version+1:version,published:window.testState.connection.published}),
  listPlatformProviderModelCandidates:async(id,version,page)=>{failRead(window.testState.candidateError);return {
    items:window.testState.emptyCandidates?[]:[{modelCode:"source-chat",modelType:"CHAT",classification:"KNOWN"}],
    pageNumber:page,totalElements:window.testState.emptyCandidates?0:1,hasPrevious:page>1,hasNext:false};},
  discoverPlatformProviderModels:async(id,version)=>{
    window.testState.detailCalls.push({action:"probe",id,version});
    if(window.testState.pending) await new Promise(resolve=>window.resolveProbe=resolve);
    const succeeded=!window.testState.failProbe;
    window.testState.connection={...window.testState.connection,version:version+1,published:false,
      status:succeeded?"VERIFIED":"DRAFT",lastProbeSucceeded:succeeded,lastProbedAt:new Date().toISOString()};
    return {succeeded};
  },
  publishPlatformProviderConnection:async(id,version)=>{
    window.testState.detailCalls.push({action:"publish",id,version});
    window.testState.connection={...window.testState.connection,published:true};
    return {connectionId:id,connectionRevision:version,published:true};
  },
  createPlatformProviderConnection:async input=>{
    window.testState.writes.push(input);
    if(window.testState.pending) await new Promise(resolve=>window.resolveWrite=resolve);
    if(window.testState.fail) throw new Error("synthetic-failure");
    return {id:"610000000000000001",version:0};
  }};
const root=createRoot(document.getElementById("root"));
window.renderWorkspace=revision=>root.render(
  <FoundationProvider><PlatformModelSupplyWorkspace key={revision} client={client}
    route={{navigationId:"platform-model-supply",task:"connections",page:1}}
    sessionRevision={revision} onRouteChange={route=>{
      window.testState.route=route;
      if(window.testState.autoRoute && route.task==="connection-detail") window.renderDetail(1,11);
    }}/></FoundationProvider>);
window.renderDetail=(candidatePage=1,identity=1)=>root.render(
  <FoundationProvider><PlatformProviderConnectionDetail
    key={identity+":"+window.testState.connection.id} client={client}
    route={{navigationId:"platform-model-supply",task:"connection-detail",connectionId:window.testState.connection.id,candidatePage,
      directory:window.testState.directoryContext}}
    onRouteChange={route=>window.testState.route=route}/></FoundationProvider>);
window.resetDetail=()=>{window.testState.connection=draft();window.testState.detailCalls=[];window.testState.badBinding=false;};
window.renderWorkspace(1);`;
  fs.writeFileSync(path.join(temporary, "index.html"), '<div id="root"></div><script type="module" src="/harness.tsx"></script>');
  fs.writeFileSync(path.join(temporary, "harness.tsx"), harness);
  const { createServer } = await import(pathToFileURL(fromFrontend.resolve("vite")));
  const react = (await import(pathToFileURL(fromFrontend.resolve("@vitejs/plugin-react")))).default;
  const aliases = ["react/jsx-runtime", "react/jsx-dev-runtime", "react", "react-dom/client", "react-dom", "@fluentui/react-components"]
    .map(name => ({ find: new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`), replacement: fromFrontend.resolve(name) }));
  const server = await createServer({ configFile: false, root: temporary, plugins: [react()],
    resolve: { alias: aliases }, server: { host: "127.0.0.1", port: 0, fs: { allow: [temporary, frontend] } }, logLevel: "error" });
  let browser;
  let page;
  let stage = "启动隔离浏览器";
  let phase = "";
  let passed = 0;
  const emptyMetrics = [];
  const check = async (name, action) => { stage = name; await action(); passed += 1;
    process.stdout.write(`${JSON.stringify({ assertion: name, result: "通过" })}\n`); };
  try {
    await server.listen();
    const address = server.httpServer.address();
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    page = await browser.newPage({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
    page.setDefaultTimeout(8000);
    let pageErrors = 0;
    page.on("pageerror", () => { pageErrors += 1; });
    await page.goto(`http://127.0.0.1:${address.port}`);
    for (const width of [390, 1024, 1440]) await check(`${width}px 空目录按原型保留说明、200%文字及键盘创建取消`, async () => {
      await page.setViewportSize({ width, height: 1000 });
      await page.reload();
      await page.waitForLoadState("networkidle");
      const create = page.getByRole("button", { name: "新增供应商连接", exact: true });
      await create.waitFor();
      assert.equal(await page.getByRole("heading", { name: "平台模型连接目前为空", exact: true }).count(), 1);
      assert.equal(await page.getByText("当前还没有供应商连接。请先保存连接草稿，探测通过后再单独发布。", { exact: true }).count(), 1);
      assert.equal(await page.getByText("暂无记录", { exact: true }).count(), 1);
      assert.equal(await page.locator("table").count(), 0);
      assert.equal(await page.getByRole("navigation", { name: "目录分页", exact: true }).count(), 0);
      emptyMetrics.push({ width, screen: "platform-model-connections", properties: await page.locator(".platform-connection-empty").evaluate(panel => {
        const properties = element => { const style = getComputedStyle(element); return Object.fromEntries([
          "fontFamily", "fontSize", "fontWeight", "lineHeight", "letterSpacing", "color", "backgroundColor", "borderColor",
          "borderWidth", "borderRadius", "padding", "gap", "minHeight", "maxWidth", "boxSizing",
        ].map(key => [key, style[key]])); };
        return { panel: properties(panel), title: properties(panel.querySelector("h2")), label: properties(panel.querySelector("p")), description: properties(panel.querySelector("p:last-child")) };
      }) });
      const baselineImage = path.join(temporary, `empty-directory-${width}-1.png`);
      await page.screenshot({ path: baselineImage, fullPage: true }); fs.chmodSync(baselineImage, 0o600);
      await page.evaluate(() => {
        const sizes = [...document.querySelectorAll("body, body *")].map(element => [element, parseFloat(getComputedStyle(element).fontSize)]);
        for (const [element, size] of sizes) if (Number.isFinite(size)) element.style.setProperty("font-size", size * 2 + "px", "important");
      });
      await page.waitForFunction(() => getComputedStyle(document.querySelector(".platform-connection-empty > p:last-child")).fontSize === "32px");
      assert.equal(await page.getByText("当前还没有供应商连接。请先保存连接草稿，探测通过后再单独发布。", { exact: true }).evaluate(item => getComputedStyle(item).fontSize), "32px");
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
      const enlargedImage = path.join(temporary, `empty-directory-${width}-2.png`);
      await page.screenshot({ path: enlargedImage, fullPage: true }); fs.chmodSync(enlargedImage, 0o600);
      await create.focus(); await page.keyboard.press("Enter");
      await page.getByRole("dialog").waitFor();
      await page.getByRole("dialog").evaluate(dialog => {
        const sizes = [dialog, ...dialog.querySelectorAll("*")].map(element => [element, parseFloat(getComputedStyle(element).fontSize)]);
        for (const [element, size] of sizes) if (Number.isFinite(size)) element.style.setProperty("font-size", size * 2 + "px", "important");
      });
      const input = page.getByLabel("一次性访问密钥", { exact: true });
      await page.waitForFunction(() => getComputedStyle(document.querySelector('input[type="password"]')).fontSize === "32px");
      assert.equal(await input.evaluate(element => element.scrollHeight <= element.clientHeight + 1), true);
      for (const select of await page.getByRole("dialog").locator("select").all()) {
        assert.equal(await select.evaluate(element => element.clientHeight >= Math.ceil(parseFloat(getComputedStyle(element).lineHeight))), true, "放大文字不能被下拉框固定高度裁切");
      }
      const kicker = await page.getByRole("dialog").locator(".platform-connection-kicker").boundingBox();
      const title = await page.getByRole("dialog").getByRole("heading", { name: "新增供应商连接", exact: true }).boundingBox();
      assert.ok(kicker && title && kicker.y + kicker.height <= title.y, "模型治理说明必须保持原型中位于标题之前的顺序");
      assert.equal(await page.getByRole("dialog").evaluate(element => element.scrollWidth <= element.clientWidth + 1), true);
      const dialogImage = path.join(temporary, `empty-dialog-${width}-2.png`);
      await page.screenshot({ path: dialogImage }); fs.chmodSync(dialogImage, 0o600);
      await page.keyboard.press("Escape");
      await page.getByRole("dialog").waitFor({ state: "hidden" });
      await page.waitForFunction(() => document.activeElement?.textContent === "新增供应商连接");
      assert.equal(await page.evaluate(() => window.testState.writes.length), 0);
    });
    await page.setViewportSize({ width: 1440, height: 960 });
    await check("精确连接404展示已接受空态并保留返回查询位置", async () => {
      await page.reload(); await page.getByRole("button", { name: "新增供应商连接", exact: true }).waitFor();
      await page.evaluate(() => {
        window.testState.connectionError = { status: 404, code: "MODEL_RUNTIME.RESOURCE.0001" };
        window.testState.directoryContext = { page: 2, pageSize: 10, query: "合成" }; window.renderDetail(1, 60);
      });
      await page.getByRole("heading", { name: "供应商连接探测", exact: true }).waitFor();
      assert.equal(await page.getByRole("heading", { name: "供应商连接探测目前为空", exact: true }).count(), 1);
      assert.equal(await page.getByText("当前还没有可查看的连接。请先从连接目录选择或建立连接草稿。", { exact: true }).count(), 1);
      assert.equal(await page.getByRole("button", { name: /执行连接探测|发布连接/ }).count(), 0);
      const detailImage = path.join(temporary, "empty-detail-1440-1.png");
      await page.screenshot({ path: detailImage, fullPage: true }); fs.chmodSync(detailImage, 0o600);
      await page.getByRole("button", { name: "返回连接目录", exact: true }).click();
      assert.deepEqual(await page.evaluate(() => window.testState.route), {
        navigationId: "platform-model-supply", task: "connections", page: 2, pageSize: 10, query: "合成",
      });
      assert.equal(await page.evaluate(() => window.testState.detailCalls.length), 0);
    });
    for (const source of ["presetError", "candidateError", "connectionError"]) await check(`${source} 的404或拒绝不伪装为连接空态`, async () => {
      await page.reload(); await page.getByRole("button", { name: "新增供应商连接", exact: true }).waitFor();
      await page.evaluate(source => {
        window.testState.connection = { ...window.testState.connection, status: "VERIFIED", lastProbeSucceeded: true, lastProbedAt: new Date().toISOString() };
        window.testState[source] = { status: source === "connectionError" ? 403 : 404,
          code: source === "connectionError" ? "MODEL_RUNTIME.AUTHORIZATION.0001" : "MODEL_RUNTIME.RESOURCE.0001" };
        window.renderDetail(1, 61);
      }, source);
      await page.getByRole("heading", { name: "暂时无法加载供应商连接探测", exact: true }).waitFor();
      assert.equal(await page.getByRole("heading", { name: "供应商连接探测目前为空", exact: true }).count(), 0);
      assert.equal(await page.getByRole("button", { name: /执行连接探测|发布连接/ }).count(), 0);
      assert.equal(await page.getByRole("button", { name: "返回连接目录", exact: true }).count(), 0);
      assert.equal(await page.getByRole("button", { name: "重新加载", exact: true }).isEnabled(), true);
      await page.evaluate(() => { window.testState.connectionError = null; window.testState.presetError = null; window.testState.candidateError = null; });
      await page.getByRole("button", { name: "重新加载", exact: true }).click();
      await page.getByRole("heading", { name: "源码隔离连接", exact: true }).waitFor();
      await page.waitForFunction(() => document.activeElement?.textContent === "供应商连接探测");
    });
    await check("连接404与预设403并发返回时不把权限失败伪装为空态", async () => {
      await page.reload(); await page.getByRole("button", { name: "新增供应商连接", exact: true }).waitFor();
      await page.evaluate(() => {
        window.testState.connectionError = { status: 404, code: "MODEL_RUNTIME.RESOURCE.0001" };
        window.testState.presetError = { status: 403, code: "MODEL_RUNTIME.AUTHORIZATION.0001" }; window.renderDetail(1, 63);
      });
      await page.getByRole("heading", { name: "暂时无法加载供应商连接探测", exact: true }).waitFor();
      assert.equal(await page.getByRole("heading", { name: "供应商连接探测目前为空", exact: true }).count(), 0);
      assert.equal(await page.getByRole("button", { name: /执行连接探测|发布连接/ }).count(), 0);
    });
    await check("存在的连接无发现候选仍展示真实详情和独立操作", async () => {
      await page.reload(); await page.getByRole("button", { name: "新增供应商连接", exact: true }).waitFor();
      await page.evaluate(() => {
        window.testState.connection = { ...window.testState.connection, status: "VERIFIED", lastProbeSucceeded: true, lastProbedAt: new Date().toISOString() };
        window.testState.emptyCandidates = true; window.renderDetail(1, 62);
      });
      await page.getByRole("heading", { name: "源码隔离连接", exact: true }).waitFor();
      assert.equal(await page.getByText("当前没有可用的模型发现结果。", { exact: true }).count(), 1);
      assert.equal(await page.getByRole("heading", { name: "供应商连接探测目前为空", exact: true }).count(), 0);
      assert.equal(await page.getByRole("button", { name: "发布连接", exact: true }).isVisible(), true);
      assert.equal(await page.evaluate(() => window.testState.detailCalls.length), 0);
    });
    await page.reload();
    await page.getByRole("button", { name: /新建平台连接|新增供应商连接/ }).click();
    const providers = page.locator('select[name="providerPreset"]');
    const secretInputs = () => page.getByRole("dialog").locator('input[type="password"]');
    for (const width of [390, 1024, 1440]) await check(`${width}px 完整预设地址按已接受原型换行并可真实放大`, async () => {
      await page.setViewportSize({ width, height: width === 390 ? 844 : width === 1024 ? 900 : 1000 });
      for (const provider of directory.items.filter(item => item.id !== "CUSTOM")) for (const choice of provider.variants) {
        await page.reload();
        await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
        await providers.selectOption(provider.id);
        await page.getByLabel("运行能力", { exact: true }).selectOption(choice.capability);
        const dialog = page.getByRole("dialog");
        const preview = dialog.locator("[data-provider-address-value]");
        if (choice.workspaceRequired) {
          assert.equal(await preview.isVisible(), false);
          assert.equal(await preview.textContent(), "", "业务空间未解析时不得复制不完整地址");
          await page.getByLabel("业务空间 ID", { exact: true }).fill("w".repeat(63));
        }
        const input = page.getByLabel("运行地址", { exact: true });
        const address = await input.inputValue();
        await preview.waitFor({ state: "visible" });
        assert.equal(await preview.textContent(), address, "完整展示必须与本次预设保存值一致");
        assert.equal(await input.evaluate(element => (element.getAttribute("aria-describedby") || "").split(/\s+/).some(id => document.getElementById(id)?.querySelector("[data-provider-address-value]"))), true,
          "运行地址的辅助说明必须包含完整地址");
        const normalSize = await preview.evaluate(element => parseFloat(getComputedStyle(element).fontSize));
        assert.equal(normalSize, 14, "完整地址沿用原型14px辅助字体");
        for (const enlarged of [false, true]) {
          if (enlarged) await enlargeText(page);
          await preview.scrollIntoViewIfNeeded();
          await page.waitForFunction(({ normalSize, enlarged }) => Math.abs(parseFloat(getComputedStyle(document.querySelector("[data-provider-address-value]")).fontSize) - normalSize * (enlarged ? 2 : 1)) < 0.01, { normalSize, enlarged });
          const metrics = await preview.evaluate(element => {
            const box = element.getBoundingClientRect(), range = document.createRange(); range.selectNodeContents(element);
            return { fits: [...range.getClientRects()].every(rect => rect.left >= box.left - 1 && rect.right <= box.right + 1 && rect.top >= box.top - 1 && rect.bottom <= box.bottom + 1),
              contentFits: element.scrollHeight <= element.clientHeight + 1,
              helpTop: document.querySelector("[data-provider-address-help]").getBoundingClientRect().top,
              bottom: box.bottom, overflow: document.documentElement.scrollWidth - innerWidth };
          });
          assert.ok(metrics.fits && metrics.contentFits, "完整地址不得裁切或依赖原生单行滚动");
          assert.ok(metrics.helpTop >= metrics.bottom - 1, "地址不得遮挡帮助说明");
          assert.ok(metrics.overflow <= 1, "完整地址不得撑宽页面");
          assert.equal(await input.inputValue(), address);
          assert.equal(await input.evaluate(element => element.readOnly), true);
        }
        await providers.selectOption("CUSTOM");
        assert.equal(await preview.isVisible(), false);
        assert.equal(await preview.textContent(), "", "切回自定义时清除旧预设地址");
        await input.fill("https://public.example.invalid/v1");
        assert.equal(await preview.textContent(), "", "自定义输入不复制到展示区");
        assert.equal(await page.evaluate(() => window.testState.writes.length), 0);
      }
    });
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.reload();
    await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
    await check("方舟弹窗标签和发现认证分区精确承接已接受原型", async () => {
      await providers.selectOption("VOLCENGINE");
      await page.getByRole("dialog").evaluate(async dialog=>{
        await document.fonts.ready;
        await Promise.all(dialog.getAnimations({subtree:true}).map(animation=>animation.finished.catch(()=>{})));
      });
      const properties=await page.getByRole("dialog").evaluate(dialog=>{
        const label=getComputedStyle(dialog.querySelector(".fui-Label"));
        const title=getComputedStyle(dialog.querySelector(".platform-connection-form__discovery h3"));
        return {labelWeight:label.fontWeight,labelLineHeight:label.lineHeight,
          titleSize:title.fontSize,titleWeight:title.fontWeight,titleLineHeight:title.lineHeight};
      });
      assert.deepEqual(properties,{labelWeight:"620",labelLineHeight:"25.6px",
        titleSize:"16px",titleWeight:"500",titleLineHeight:"21.6px"});
      const bounds=await page.getByRole("dialog").evaluate(dialog=>{
        const box=dialog.getBoundingClientRect(),action=dialog.querySelector("button[type='submit']").getBoundingClientRect();
        return {width:box.width,actionFits:action.top>=0&&action.bottom<=innerHeight&&action.bottom<=box.bottom,
          boxSizing:getComputedStyle(dialog).boxSizing,padding:getComputedStyle(dialog).padding,
          actionBottom:action.bottom,dialogBottom:box.bottom,
          actionsWidth:dialog.querySelector(".fui-DialogActions").getBoundingClientRect().width,
          contentWidth:dialog.querySelector(".fui-DialogContent").getBoundingClientRect().width,
          actionsRightGap:dialog.querySelector(".fui-DialogActions").getBoundingClientRect().right-action.right};
      });
      process.stdout.write(JSON.stringify({dialogLayout:bounds})+"\n");
      assert.ok(Math.abs(bounds.width-608)<=1,"桌面方舟表单沿用原型38rem宽度");
      assert.ok(Math.abs(bounds.actionsWidth-bounds.contentWidth)<=1,"原型操作区分隔线应覆盖完整表单宽度");
      assert.ok(Math.abs(bounds.actionsRightGap)<=1,"原型操作区按钮应对齐表单右边缘");
      assert.equal(bounds.actionFits,true,"正常桌面方舟表单的保存操作应完整显示");
    });
    await check("五厂商选择来自受控目录", async () => {
      const ids = await providers.locator("option").evaluateAll(options => options.map(option => option.value));
      assert.deepEqual(ids, directory.items.map(item => item.id));
    });
    for (const provider of directory.items) for (const choice of provider.variants) {
      await check(`${provider.id}/${choice.capability} 地址与能力联动`, async () => {
        await providers.selectOption(provider.id);
        await page.getByLabel("运行能力", { exact: true }).selectOption(choice.capability);
        if (choice.workspaceRequired) await page.getByLabel("业务空间 ID", { exact: true }).fill("source-check");
        const endpoint = page.getByLabel("运行地址", { exact: true });
        if (provider.id === "CUSTOM") await endpoint.fill("https://models.example.invalid/v1");
        const expected = provider.id === "CUSTOM" ? "https://models.example.invalid/v1"
          : choice.endpointTemplate.replace("{workspaceId}", "source-check");
        assert.equal(await endpoint.inputValue(), expected);
        assert.equal(await endpoint.evaluate(input => input.readOnly), provider.id !== "CUSTOM");
        assert.equal(await secretInputs().count(), provider.id === "VOLCENGINE" ? 3 : 1);
      });
    }
    await check("厂商与能力切换清空一次性输入", async () => {
      for (const input of await secretInputs().all()) await input.fill("browser-only-sentinel");
      await providers.selectOption("ALIYUN");
      assert.equal(await page.getByLabel("一次性访问密钥", { exact: true }).inputValue(), "");
      await page.getByLabel("一次性访问密钥", { exact: true }).fill("browser-only-sentinel");
      await page.getByLabel("运行能力", { exact: true }).selectOption("EMBEDDING");
      assert.equal(await page.getByLabel("一次性访问密钥", { exact: true }).inputValue(), "");
    });
    await check("业务空间变化清空密钥且拒绝主机注入", async () => {
      await page.getByLabel("运行能力", { exact: true }).selectOption("CHAT");
      await page.getByLabel("业务空间 ID", { exact: true }).fill("source-check");
      await page.getByLabel("一次性访问密钥", { exact: true }).fill("browser-only-sentinel");
      await page.getByLabel("业务空间 ID", { exact: true }).fill("bad.example");
      assert.equal(await page.getByLabel("一次性访问密钥", { exact: true }).inputValue(), "");
      assert.equal(await page.getByLabel("运行地址", { exact: true }).inputValue(), "");
    });
    await check("模态弹窗保持键盘焦点范围", async () => {
      for (let step=0;step<14;step++) {
        await page.keyboard.press("Tab");
        assert.equal(await page.getByRole("dialog").evaluate(dialog => dialog.contains(document.activeElement)), true);
      }
    });
    await check("Escape 清空所有秘密并恢复触发焦点", async () => {
      await providers.selectOption("VOLCENGINE");
      for (const input of await secretInputs().all()) await input.fill("browser-only-sentinel");
      await page.keyboard.press("Escape");
      await page.getByRole("dialog").waitFor({ state: "hidden" });
      await page.waitForFunction(() => document.activeElement?.textContent === "新增供应商连接");
      assert.equal(await page.getByRole("dialog").count(), 0);
      assert.equal(await page.getByRole("button", { name: "新增供应商连接", exact: true }).evaluate(button => button === document.activeElement), true);
      await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
      assert.equal(await secretInputs().evaluateAll(inputs => inputs.every(input => input.value === "")), true);
    });
    await check("失败保存清空秘密且不持久化到浏览器", async () => {
      await providers.selectOption("VOLCENGINE");
      await page.getByLabel("连接名称", { exact: true }).fill("源码隔离连接");
      for (const input of await secretInputs().all()) await input.fill("browser-only-sentinel");
      await page.evaluate(() => { window.testState.fail = true; });
      await page.getByRole("button", { name: "保存连接草稿", exact: true }).click();
      await page.getByRole("alert").filter({ hasText: "请求未完成" }).waitFor();
      assert.equal(await secretInputs().evaluateAll(inputs => inputs.every(input => input.value === "")), true);
      assert.equal(await page.evaluate(() => [localStorage, sessionStorage].every(storage => storage.length === 0)), true);
    });
    await check("保存合同冻结预设能力并导航到返回的连接", async () => {
      await page.evaluate(() => { window.testState.fail = false; window.testState.writes = []; });
      await providers.selectOption("ALIYUN");
      await page.getByLabel("运行能力", { exact: true }).selectOption("RERANK");
      await page.getByLabel("业务空间 ID", { exact: true }).fill("source-check");
      await page.getByLabel("连接名称", { exact: true }).fill("源码隔离连接");
      await page.getByLabel("一次性访问密钥", { exact: true }).fill("browser-only-sentinel");
      await page.getByRole("button", { name: "保存连接草稿", exact: true }).click();
      await page.waitForFunction(() => window.testState.route !== null);
      assert.equal(await page.evaluate(() => {
        const { writes, route } = window.testState;
        const input = writes[0];
        return writes.length === 1 && input.providerPresetId === "ALIYUN" && input.providerPresetVersion === "2026-10-12"
          && input.capability === "RERANK" && input.workspaceId === "source-check" && input.region === "cn-beijing"
          && !Object.hasOwn(input, "discoveryAccessKeyId") && !Object.hasOwn(input, "discoverySecretAccessKey")
          && route.task === "connection-detail" && route.connectionId === "610000000000000001";
      }), true);
    });
    for (const transition of ["取消", "身份替换"]) await check(`${transition}后的迟到保存不导航且不恢复秘密`, async () => {
      phase = "准备新表单";
      await page.evaluate(() => { window.testState.pending = true; window.testState.route = null; window.testState.writes = []; });
      await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
      await providers.selectOption("VOLCENGINE");
      for (const input of await secretInputs().all()) await input.fill("browser-only-sentinel");
      await page.getByRole("button", { name: "保存连接草稿", exact: true }).click();
      phase = "等待挂起请求";
      await page.waitForFunction(() => typeof window.resolveWrite === "function");
      assert.equal(await secretInputs().evaluateAll(inputs => inputs.every(input => input.value === "")), true);
      phase = "切换并关闭旧表单";
      if (transition === "取消") await page.getByRole("button", { name: "取消", exact: true }).click();
      else await page.evaluate(() => window.renderWorkspace(2));
      await page.getByRole("dialog").waitFor({ state: "hidden" });
      await page.evaluate(() => { window.resolveWrite(); window.resolveWrite = undefined; window.testState.pending = false; });
      phase = "重新打开新表单";
      await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
      phase = "检查秘密与旧导航";
      assert.equal(await secretInputs().evaluateAll(inputs => inputs.every(input => input.value === "")), true);
      assert.equal(await page.evaluate(() => window.testState.route), null);
      assert.equal(await page.evaluate(() => window.testState.writes.length), 1);
      phase = "关闭新表单";
      await page.keyboard.press("Escape");
      await page.getByRole("dialog").waitFor({ state: "hidden" });
      phase = "";
    });
    await check("详情深链接显示真实配置状态且草稿不能直接发布", async () => {
      await page.evaluate(() => window.renderDetail());
      await page.getByRole("heading", { name: "供应商连接探测", exact: true }).waitFor();
      assert.equal(await page.getByRole("button", { name: "执行连接探测", exact: true }).isEnabled(), true);
      assert.equal(await page.getByRole("button", { name: "发布连接", exact: true }).count(), 0);
      assert.equal(await page.getByText("已配置（不回显）", { exact: true }).count(), 1);
      assert.equal(await page.locator('input[type="password"]').count(), 0);
    });
    await check("旧 VERIFIED 不能显示可发布，必须重新探测", async () => {
      await page.evaluate(() => {
        window.testState.connection = { ...window.testState.connection, status: "VERIFIED", lastProbeSucceeded: true,
          lastProbedAt: "2026-08-29T00:00:00Z" }; window.renderDetail(1, 20);
      });
      await page.getByRole("heading", { name: "供应商连接探测", exact: true }).waitFor();
      assert.equal(await page.getByRole("button", { name: "发布连接", exact: true }).count(), 0);
      await page.getByText("连接探测已失效，请重新执行连接探测。", { exact: true }).waitFor();
      assert.equal(await page.evaluate(() => window.testState.detailCalls.length), 0);
      await page.evaluate(() => { window.resetDetail(); window.renderDetail(1, 21); });
      await page.getByRole("button", { name: "执行连接探测", exact: true }).waitFor();
    });
    await check("成功探测读取新版本候选后才允许发布", async () => {
      await page.getByRole("button", { name: "执行连接探测", exact: true }).click();
      await page.getByRole("button", { name: "发布连接", exact: true }).waitFor();
      assert.equal(await page.getByText("source-chat", { exact: true }).count(), 1);
      assert.deepEqual(await page.evaluate(() => window.testState.detailCalls), [{ action: "probe", id: "610000000000000001", version: 0 }]);
    });
    await check("发布绑定当前探测版本且不自动建立部署", async () => {
      await page.getByRole("button", { name: "发布连接", exact: true }).click();
      await page.getByRole("heading", { name: "连接已发布", exact: true }).waitFor();
      assert.deepEqual(await page.evaluate(() => window.testState.detailCalls[1]), { action: "publish", id: "610000000000000001", version: 1 });
      assert.equal(await page.getByRole("button", { name: /管理部署|建立部署/ }).count(), 0);
    });
    await check("重新探测失败撤回旧通过与发布状态", async () => {
      await page.evaluate(() => { window.testState.failProbe = true; });
      await page.getByRole("button", { name: "执行连接探测", exact: true }).click();
      await page.getByRole("alert").filter({ hasText: "不能发布连接" }).waitFor();
      assert.equal(await page.getByRole("button", { name: "发布连接", exact: true }).count(), 0);
      assert.equal(await page.getByText("source-chat", { exact: true }).count(), 0);
      assert.equal(await page.locator(".platform-connection-object-line").textContent(), "失败");
      assert.equal(await page.locator(".platform-connection-section__heading").getByText("失败", { exact: true }).count(), 1);
    });
    await check("缺少方舟独立 AK/SK 时显示待配置且禁止探测", async () => {
      await page.evaluate(() => {
        window.resetDetail(); window.testState.connection.discoveryProfile = "VOLCENGINE_ARK_CONTROL_PLANE";
        window.testState.connection.preset = { id: "VOLCENGINE", capability: "CHAT" }; window.renderDetail(1, 2);
      });
      await page.getByText("待配置独立 AK/SK", { exact: true }).waitFor();
      assert.equal(await page.getByRole("button", { name: "执行连接探测", exact: true }).isDisabled(), true);
      assert.equal(await page.evaluate(() => window.testState.detailCalls.length), 0);
    });
    await check("发布事实版本不匹配时拒绝展示可操作详情", async () => {
      await page.evaluate(() => { window.resetDetail(); window.testState.badBinding = true; window.renderDetail(1, 3); });
      await page.getByRole("heading", { name: "暂时无法加载供应商连接探测", exact: true }).waitFor();
      assert.equal(await page.getByRole("button", { name: /执行连接探测|发布连接/ }).count(), 0);
    });
    await check("分页切换丢弃迟到探测并可重新操作", async () => {
      await page.evaluate(() => { window.resetDetail(); window.testState.pending = true; window.testState.failProbe = false; window.renderDetail(1, 4); });
      await page.getByRole("button", { name: "执行连接探测", exact: true }).click();
      await page.waitForFunction(() => typeof window.resolveProbe === "function");
      await page.evaluate(() => window.renderDetail(2, 4));
      await page.getByRole("button", { name: "执行连接探测", exact: true }).waitFor();
      assert.equal(await page.getByRole("button", { name: "执行连接探测", exact: true }).isEnabled(), true);
      await page.evaluate(() => { window.resolveProbe(); window.resolveProbe = undefined; window.testState.pending = false; });
      assert.equal(await page.getByRole("button", { name: "发布连接", exact: true }).count(), 0);
      assert.equal(await page.getByRole("alert").count(), 0);
    });
    await check("无预设的旧连接不被猜测为 OpenAI 协议或已知模型能力", async () => {
      await page.evaluate(() => {
        window.resetDetail(); window.testState.connection.providerKind = "DETERMINISTIC";
        delete window.testState.connection.preset; window.renderDetail(1, 5);
      });
      await page.getByText("待确认协议", { exact: true }).waitFor();
      assert.equal(await page.getByText("待确认", { exact: true }).count(), 1);
    });
    await check("保存导航后的详情不被旧模态隐藏且焦点归属新页面", async () => {
      await page.evaluate(() => {
        window.resetDetail(); window.testState.fail = false; window.testState.pending = false;
        window.testState.autoRoute = true; window.renderWorkspace(10);
      });
      await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
      await providers.selectOption("DEEPSEEK");
      await page.getByLabel("一次性访问密钥", { exact: true }).fill("browser-only-sentinel");
      await page.getByRole("button", { name: "保存连接草稿", exact: true }).click();
      await page.getByRole("heading", { name: "供应商连接探测", exact: true }).waitFor();
      await page.waitForFunction(() => document.activeElement?.textContent === "供应商连接探测");
      assert.equal(await page.getByRole("button", { name: "执行连接探测", exact: true }).isEnabled(), true);
      assert.equal(await page.getByRole("dialog").count(), 0);
    });
    await check("页面停留跨过24小时有效期时撤下发布动作，不发起任何业务请求", async () => {
      // 只在到期场景接管时钟，避免改变既有 Fluent 模态清理的计时环境。
      await page.clock.install();
      await page.evaluate(() => {
        window.resetDetail(); window.testState.connection = { ...window.testState.connection, status: "VERIFIED",
          lastProbeSucceeded: true, lastProbedAt: new Date(Date.now() - 24 * 60 * 60 * 1000 + 10_000).toISOString() };
        window.renderDetail(1, 30);
      });
      await page.getByRole("button", { name: "发布连接", exact: true }).waitFor();
      await page.clock.fastForward(10_001);
      await page.getByRole("button", { name: "执行连接探测", exact: true }).waitFor();
      assert.equal(await page.getByRole("button", { name: "发布连接", exact: true }).count(), 0);
      assert.equal(await page.evaluate(() => window.testState.detailCalls.length), 0);
    });
    await check("两页标题在三尺寸下精确承接已接受原型字号、行高和页头文案", async () => {
      for (const [width, size, line] of [[390, "26.4px", "35.64px"], [1024, "30.72px", "41.472px"], [1440, "32px", "43.2px"]]) {
        await page.setViewportSize({ width, height: 1000 });
        for (const screen of ["directory", "detail"]) {
          await page.evaluate(screen => screen === "directory" ? window.renderWorkspace(40) : window.renderDetail(1, 40), screen);
          const heading = page.getByRole("heading", { name: screen === "directory" ? "平台模型连接" : "供应商连接探测", exact: true });
          await heading.waitFor(); await page.evaluate(() => document.fonts.ready);
          assert.deepEqual(await heading.evaluate(element => {
            const style = getComputedStyle(element); return [style.fontSize, style.lineHeight, style.fontWeight];
          }), [size, line, "500"]);
          assert.equal(await page.locator(".platform-connection-page__context").textContent(), "模型治理");
        }
      }
    });
    assert.equal(pageErrors, 0, "隔离页面不得产生脚本错误");
    fs.writeFileSync(path.join(temporary, "empty-state-metrics.json"), JSON.stringify({ emptyMetrics }, null, 2) + "\n", { mode: 0o600, flag: "wx" });
    process.stdout.write(`${JSON.stringify({ passed, status: "PASSED", productionAcceptance: false })}\n`);
  } catch (error) {
    process.stdout.write(`${JSON.stringify({ assertion: stage, phase, errorKind: error?.code === "ERR_ASSERTION" ? "ASSERTION" : "UI_WAIT", result: "失败", passed, productionAcceptance: false })}\n`);
    if (page) process.stdout.write(`${JSON.stringify({ sourceLine: error?.stack?.split("\n").find(line => line.includes(__filename)), layout: await page.evaluate(() => ({
      viewport: innerWidth, document: document.documentElement.scrollWidth,
      heading: document.querySelector(".platform-connection-page__heading")?.getBoundingClientRect().width,
      panel: document.querySelector(".platform-connection-empty")?.getBoundingClientRect().width,
      descriptionFont: document.querySelector(".platform-connection-empty > p:last-child") && getComputedStyle(document.querySelector(".platform-connection-empty > p:last-child")).fontSize,
      descriptionInlineFont: document.querySelector(".platform-connection-empty > p:last-child")?.style.fontSize,
      descriptionTransition: document.querySelector(".platform-connection-empty > p:last-child") && getComputedStyle(document.querySelector(".platform-connection-empty > p:last-child")).transition,
    })) })}\n`);
    if (page && stage.includes("空目录")) { const image = path.join(temporary, "failure-layout.png"); await page.screenshot({ path: image, fullPage: true }); fs.chmodSync(image, 0o600); }
    if (phase && page) process.stdout.write(`${JSON.stringify(await page.evaluate(() => ({
      diagnostic: "身份切换后的非秘密 DOM 状态",
      buttons: Array.from(document.querySelectorAll("button")).map(button => ({ text: button.textContent, disabled: button.disabled,
        hidden: !!button.closest('[aria-hidden="true"], [inert]'), rect: { width: button.getBoundingClientRect().width, height: button.getBoundingClientRect().height } })),
      states: Array.from(document.querySelectorAll('[role="status"], [role="alert"]')).map(element => element.textContent),
      overlays: Array.from(document.querySelectorAll('[aria-modal="true"], [inert], [aria-hidden="true"]')).map(element => ({ tag: element.tagName, className: element.className })),
    }))) }\n`);
    process.exitCode = 1;
  } finally { if (browser) await browser.close(); await server.close(); }
}

run().catch(() => { process.stdout.write('{"result":"隔离回归启动失败"}\n'); process.exitCode = 1; });
