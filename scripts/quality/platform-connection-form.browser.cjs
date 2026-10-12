/* WP-0019 S1 源码浏览器回归：隔离服务与合成输入，不访问产品 API 或真实厂商。 */
const fs = require("node:fs");
const path = require("node:path");
const { createRequire } = require("node:module");
const { pathToFileURL } = require("node:url");
const assert = require("node:assert/strict");

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
  const harness = `import React from "react";
import {createRoot} from "react-dom/client";
import {FluentProvider, webLightTheme} from "@fluentui/react-components";
import {PlatformModelSupplyWorkspace} from "/@fs/${repository}/frontend/apps/console-web/src/features/platform-governance/PlatformModelSupplyWorkspace.tsx";
import {PlatformProviderConnectionDetail} from "/@fs/${repository}/frontend/apps/console-web/src/features/platform-governance/PlatformProviderConnectionDetail.tsx";
import "/@fs/${repository}/frontend/apps/console-web/src/styles.css";
import "/@fs/${repository}/frontend/packages/design-system/src/styles/tokens.css";
const directory=${JSON.stringify(directory)};
const draft=()=>({id:"610000000000000001",displayName:"源码隔离连接",providerKind:"OPENAI_COMPATIBLE",version:0,status:"DRAFT",published:false,
  credentialConfigured:true,discoveryCredentialsConfigured:false,discoveryProfile:"OPENAI_MODELS_API",
  authenticationType:"API_KEY",preset:{id:"CUSTOM",capability:"CHAT"}});
window.testState={writes:[],fail:false,pending:false,route:null,connection:draft(),detailCalls:[],failProbe:false,badBinding:false,autoRoute:false};
const client={hasSession:()=>true,
  listPlatformProviderConnections:async()=>({items:[],totalElements:0,hasPrevious:false,hasNext:false}),
  getPlatformProviderPresets:async()=>directory,
  getPlatformProviderConnection:async()=>({...window.testState.connection}),
  getPlatformProviderConnectionPublication:async(id,version)=>({connectionId:id,
    connectionRevision:window.testState.badBinding?version+1:version,published:window.testState.connection.published}),
  listPlatformProviderModelCandidates:async(id,version,page)=>({items:[{modelCode:"source-chat",modelType:"CHAT",classification:"KNOWN"}],
    pageNumber:page,totalElements:1,hasPrevious:page>1,hasNext:false}),
  discoverPlatformProviderModels:async(id,version)=>{
    window.testState.detailCalls.push({action:"probe",id,version});
    if(window.testState.pending) await new Promise(resolve=>window.resolveProbe=resolve);
    const succeeded=!window.testState.failProbe;
    window.testState.connection={...window.testState.connection,version:version+1,published:false,
      status:succeeded?"VERIFIED":"DRAFT",lastProbeSucceeded:succeeded,lastProbedAt:"2026-10-12T00:00:00Z"};
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
  <FluentProvider className="foundation-app-root" theme={webLightTheme}><PlatformModelSupplyWorkspace key={revision} client={client}
    route={{navigationId:"platform-model-supply",task:"connections",page:1}}
    sessionRevision={revision} onRouteChange={route=>{
      window.testState.route=route;
      if(window.testState.autoRoute && route.task==="connection-detail") window.renderDetail(1,11);
    }}/></FluentProvider>);
window.renderDetail=(candidatePage=1,identity=1)=>root.render(
  <FluentProvider className="foundation-app-root" theme={webLightTheme}><PlatformProviderConnectionDetail
    key={identity+":"+window.testState.connection.id} client={client}
    route={{navigationId:"platform-model-supply",task:"connection-detail",connectionId:window.testState.connection.id,candidatePage}}
    onRouteChange={route=>window.testState.route=route}/></FluentProvider>);
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
    await page.getByRole("button", { name: /新建平台连接|新增供应商连接/ }).click();
    const providers = page.locator('select[name="providerPreset"]');
    const secretInputs = () => page.getByRole("dialog").locator('input[type="password"]');
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
      assert.equal(await page.getByText("失败", { exact: true }).count(), 1);
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
      await page.getByText("供应商连接暂时不可用", { exact: true }).waitFor();
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
    assert.equal(pageErrors, 0, "隔离页面不得产生脚本错误");
    process.stdout.write(`${JSON.stringify({ passed, status: "PASSED", productionAcceptance: false })}\n`);
  } catch (error) {
    process.stdout.write(`${JSON.stringify({ assertion: stage, phase, errorKind: error?.code === "ERR_ASSERTION" ? "ASSERTION" : "UI_WAIT", result: "失败", passed, productionAcceptance: false })}\n`);
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
