/* WP-0019 S1 分页浏览器反例：只用隔离服务和合成页响应，不连接业务系统。 */
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { createRequire } = require("node:module");
const { pathToFileURL } = require("node:url");
const repository = path.resolve(__dirname, "../..");
const frontend = path.join(repository, "frontend");
const fromFrontend = createRequire(path.join(frontend, "package.json"));

async function run() {
  assert.equal(require("playwright/package.json").version, "1.62.1");
  const temporary = fs.mkdtempSync("/private/tmp/wp0019-pagination-source-");
  fs.chmodSync(temporary, 0o700);
  const harness = `import React from "react";
import {createRoot} from "react-dom/client";
import {FluentProvider,webLightTheme} from "@fluentui/react-components";
import {PlatformModelSupplyWorkspace} from "/@fs/${frontend}/apps/console-web/src/features/platform-governance/PlatformModelSupplyWorkspace.tsx";
import {PlatformProviderConnectionDetail} from "/@fs/${frontend}/apps/console-web/src/features/platform-governance/PlatformProviderConnectionDetail.tsx";
import {formatConsoleRoute,parseConsoleRoute} from "/@fs/${frontend}/apps/console-web/src/session/console-route.ts";
import "/@fs/${frontend}/apps/console-web/src/styles.css";
import "/@fs/${frontend}/packages/design-system/src/styles/tokens.css";
const connection={id:"610000000000000001",displayName:"分页连接001",providerKind:"OPENAI_COMPATIBLE",version:3,status:"VERIFIED",published:true,
 credentialConfigured:true,discoveryCredentialsConfigured:false,discoveryProfile:"OPENAI_MODELS_API",lastProbeSucceeded:true,lastProbedAt:new Date().toISOString()};
window.testState={total:101,calls:[],delayPage:0,resolves:[],route:null,identity:1,session:true};
const result=async(kind,page,size=20,q="")=>{const rows=Array.from({length:window.testState.total},(_,index)=>{
  const number=index+1;return kind==="connections"?{...connection,id:String(610000000000000000n+BigInt(number)),displayName:"分页连接"+String(number).padStart(3,"0")}
   :{modelCode:"分页模型"+String(number).padStart(3,"0"),modelType:"CHAT",classification:"CONFIRMED_BY_PROVIDER"};});
 const matches=rows.filter(row=>(row.displayName||row.modelCode).includes(q.trim()));const total=matches.length;
 window.testState.calls.push({kind,page,size,...(q?{q}:{})});
 const response={items:matches.slice((page-1)*size,page*size),
 pageNumber:page,pageSize:size,totalElements:total,totalPages:Math.ceil(total/size),hasPrevious:page>1,hasNext:page*size<total};
 if(page===window.testState.delayPage)await new Promise(resolve=>window.testState.resolves.push(resolve));return response;};
const client={hasSession:()=>window.testState.session,listPlatformProviderConnections:(page,size,q)=>result("connections",page,size,q),
 getPlatformProviderConnectionDirectorySummary:async()=>({publishedConnections:21}),
 getPlatformProviderPresets:async()=>({items:[{id:"CUSTOM",name:"自定义连接",capabilities:["CHAT"],variants:[{capability:"CHAT"}]}]}),
 getPlatformProviderConnection:async id=>({...connection,id,displayName:"分页连接"+id.slice(-3)}),getPlatformProviderConnectionPublication:async id=>({connectionId:id,connectionRevision:3,published:true}),
 listPlatformProviderModelCandidates:async(id,version,page,size,q)=>result("candidates",page,size,q)};
const root=createRoot(document.getElementById("root"));
window.draw=(route,mode="replaceState")=>{window.testState.route=route;history[mode]({},"",formatConsoleRoute(route));
 root.render(<FluentProvider className="foundation-app-root" theme={webLightTheme}>{route.task==="connections"
 ?<PlatformModelSupplyWorkspace key={window.testState.identity} client={client} route={route} sessionRevision={window.testState.identity} onRouteChange={(next,replace=false)=>window.draw(next,replace?"replaceState":"pushState")}/>
 :<PlatformProviderConnectionDetail key={window.testState.identity} client={client} route={route} onRouteChange={(next,replace=false)=>window.draw(next,replace?"replaceState":"pushState")}/>}</FluentProvider>);};
window.start=(detail=false,total=101)=>{window.testState.total=total;window.testState.calls=[];window.testState.identity++;window.testState.session=true;
 window.draw(detail?{navigationId:"platform-model-supply",task:"connection-detail",connectionId:connection.id,candidatePage:1}
 :{navigationId:"platform-model-supply",task:"connections",page:1});};
window.addEventListener("popstate",()=>window.draw(parseConsoleRoute(location.pathname,location.search)));
const initial=parseConsoleRoute(location.pathname,location.search);
if(initial?.navigationId==="platform-model-supply")window.draw(initial);else window.start();`;
  fs.writeFileSync(path.join(temporary, "index.html"), '<div id="root"></div><script type="module" src="/harness.tsx"></script>');
  fs.writeFileSync(path.join(temporary, "harness.tsx"), harness);
  const { createServer } = await import(pathToFileURL(fromFrontend.resolve("vite")));
  const react = (await import(pathToFileURL(fromFrontend.resolve("@vitejs/plugin-react")))).default;
  const alias = ["react/jsx-runtime", "react/jsx-dev-runtime", "react", "react-dom/client", "react-dom", "@fluentui/react-components"]
    .map(name => ({ find: new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`), replacement: fromFrontend.resolve(name) }));
  const server = await createServer({ configFile:false,root:temporary,plugins:[react()],resolve:{alias},
    server:{host:"127.0.0.1",port:0,fs:{allow:[temporary,frontend]}},logLevel:"error" });
  let browser; let passed=0; let stage="启动隔离浏览器";
  const check=async(name,action)=>{stage=name;await action();passed++;process.stdout.write(JSON.stringify({assertion:name,result:"通过"})+"\n");};
  try {
    await server.listen();
    browser=await require("playwright").chromium.launch({channel:"chrome",headless:true});
    const page=await browser.newPage({viewport:{width:1440,height:1000},reducedMotion:"reduce"});
    page.setDefaultTimeout(8000);
    let pageErrors=0;page.on("pageerror",()=>pageErrors++);
    await page.goto(`http://127.0.0.1:${server.httpServer.address().port}`);
    for(const detail of [false,true]) {
      const prefix=detail?"发现模型":"连接目录";
      const pageKey=detail?"candidatePage":"page";
      const rowName=number=>(detail?"分页模型":"分页连接")+String(number).padStart(3,"0");
      await page.evaluate(detail=>window.start(detail),detail);
      const pager=page.getByRole("navigation",{name:detail?"发现模型分页":"目录分页",exact:true});
      const size=pager.getByLabel("每页条数",{exact:true});
      await check(`${prefix}101条默认20条且保留真实总数`,async()=>{
        await size.waitFor();await pager.getByRole("status").filter({hasText:"共 101 条 · 1–20 条"}).waitFor();
        assert.deepEqual(await size.locator("option").evaluateAll(options=>options.map(item=>item.value)),["10","20","50","100"]);
        assert.equal(await page.locator("tbody tr").count(),20);
      });
      await check(`${prefix}下一页向服务端请求精确页而非本地切片`,async()=>{
        await pager.getByRole("button",{name:"下一页",exact:true}).click();
        await page.getByText(rowName(21),{exact:true}).waitFor();
        await pager.getByRole("status").filter({hasText:"共 101 条 · 21–40 条"}).waitFor();
        assert.equal(new URL(page.url()).searchParams.get(pageKey),"2");
        assert.equal(await page.locator("tbody tr").count(),20);
        assert.deepEqual(await page.evaluate(()=>window.testState.calls.at(-1)),{kind:detail?"candidates":"connections",page:2,size:20});
      });
      await check(`${prefix}切换页大小回到第一页并可刷新恢复`,async()=>{
        await size.selectOption("10");await pager.getByRole("status").filter({hasText:"共 101 条 · 1–10 条"}).waitFor();
        assert.equal(await page.locator("tbody tr").count(),10);
        assert.equal(new URL(page.url()).searchParams.get("size"),"10");
        await page.evaluate(()=>window.draw(window.testState.route));
        assert.equal(await size.inputValue(),"10");
      });
      await check(`${prefix}跳转到最后一页且范围为101到101`,async()=>{
        await pager.getByLabel("跳转页码",{exact:true}).fill("11");
        await pager.getByRole("button",{name:"跳转",exact:true}).click();
        await page.getByText(rowName(101),{exact:true}).waitFor();
        await pager.getByRole("status").filter({hasText:"共 101 条 · 101–101 条"}).waitFor();
        assert.equal(await page.locator("tbody tr").count(),1);
        assert.equal(await pager.getByRole("button",{name:"下一页",exact:true}).isDisabled(),true);
      });
      await check(`${prefix}非法跳转不发请求且聚焦错误字段`,async()=>{
        for(const value of ["0","12","1.5"]) {
          const calls=await page.evaluate(()=>window.testState.calls.length);
          const input=pager.getByLabel("跳转页码",{exact:true});await input.fill(value);
          await pager.getByRole("button",{name:"跳转",exact:true}).click();
          await pager.getByRole("alert").filter({hasText:"请输入 1–11 之间的整数页码。"}).waitFor();
          assert.equal(await input.getAttribute("aria-invalid"),"true");
          assert.equal(await input.evaluate(item=>item===document.activeElement),true);
          assert.equal(await page.evaluate(()=>window.testState.calls.length),calls);
        }
      });
      await check(`${prefix}Enter跳转恢复焦点且后退恢复旧页`,async()=>{
        const input=pager.getByLabel("跳转页码",{exact:true});await input.fill("2");await input.press("Enter");
        await page.getByText(rowName(11),{exact:true}).waitFor();
        await page.waitForFunction(()=>document.activeElement?.getAttribute("aria-label")==="跳转页码");
        await page.goBack();await page.getByText(rowName(101),{exact:true}).waitFor();
      });
      await check(`${prefix}越界URL恢复有效页且不追加无效历史`,async()=>{
        await page.evaluate(key=>window.draw({...window.testState.route,[key]:10},"pushState"),pageKey);
        await page.getByText(rowName(91),{exact:true}).waitFor();
        const historyLength=await page.evaluate(()=>history.length);
        await page.evaluate(key=>window.draw({...window.testState.route,[key]:99}),pageKey);
        await pager.getByRole("status").filter({hasText:"共 101 条 · 101–101 条"}).waitFor();
        await page.waitForFunction(key=>new URL(location.href).searchParams.get(key)==="11",pageKey);
        assert.equal(await page.evaluate(()=>history.length),historyLength);
      });
      await check(`${prefix}21条和空目录保持页码与范围一致`,async()=>{
        await page.evaluate(detail=>window.start(detail,21),detail);
        await pager.getByRole("status").filter({hasText:"共 21 条 · 1–20 条"}).waitFor();
        await pager.getByRole("button",{name:"下一页",exact:true}).click();
        await pager.getByRole("status").filter({hasText:"共 21 条 · 21–21 条"}).waitFor();
        await page.evaluate(detail=>window.start(detail,0),detail);
        await pager.getByRole("status").filter({hasText:"共 0 条 · 0–0 条"}).waitFor();
        assert.equal(await pager.getByRole("button",{name:"上一页",exact:true}).isDisabled(),true);
        assert.equal(await pager.getByRole("button",{name:"下一页",exact:true}).isDisabled(),true);
      });
      await check(`${prefix}搜索跨页命中由服务端计算且重置页码`,async()=>{
        await page.evaluate(detail=>window.start(detail),detail);
        await pager.getByRole("status").filter({hasText:"共 101 条 · 1–20 条"}).waitFor();
        const search=page.getByRole("searchbox",{name:"搜索当前目录",exact:true});
        await search.fill("101");await page.getByText(rowName(101),{exact:true}).waitFor();
        await pager.getByRole("status").filter({hasText:"共 1 条 · 1–1 条"}).waitFor();
        assert.equal(new URL(page.url()).searchParams.get("q"),"101");
        assert.equal(await page.evaluate(()=>window.testState.calls.some(call=>call.q==="101"&&call.page===1)),true);
        if(!detail)assert.equal(await page.getByText("21 个已发布连接",{exact:true}).count(),1);
        await page.reload();await search.waitFor();
        assert.equal(await search.inputValue(),"101");
        await pager.getByRole("status").filter({hasText:"共 1 条 · 1–1 条"}).waitFor();
        await search.fill("无匹配");await pager.getByRole("status").filter({hasText:"共 0 条 · 0–0 条"}).waitFor();
        assert.equal(await page.getByText(/没有匹配记录/).count(),1);
      });
      await check(`${prefix}超长Unicode词组不发请求且可清空恢复`,async()=>{
        const search=page.getByRole("searchbox",{name:"搜索当前目录",exact:true});
        const calls=await page.evaluate(()=>window.testState.calls.length);
        await search.fill("😀".repeat(65));
        await page.getByRole("alert").filter({hasText:"目录搜索词不能超过六十四个字符。"}).waitFor();
        assert.equal(await search.getAttribute("aria-invalid"),"true");
        assert.equal(await page.evaluate(()=>window.testState.calls.length),calls);
        await search.fill("");await pager.getByRole("status").filter({hasText:"共 101 条 · 1–20 条"}).waitFor();
      });
      if(!detail)await check("详情返回保留原目录搜索、页码和页大小",async()=>{
        await page.evaluate(()=>window.draw({navigationId:"platform-model-supply",task:"connections",page:3,pageSize:10,query:"分页"}));
        await pager.getByRole("status").filter({hasText:"共 101 条 · 21–30 条"}).waitFor();
        await page.getByRole("button",{name:"查看分页连接021",exact:true}).click();
        await page.getByRole("heading",{name:"供应商连接探测",exact:true}).waitFor();
        assert.equal(new URL(page.url()).searchParams.get("directoryQuery"),"分页");
        await page.getByRole("button",{name:"返回连接目录",exact:true}).click();
        await pager.getByRole("status").filter({hasText:"共 101 条 · 21–30 条"}).waitFor();
        assert.equal(await size.inputValue(),"10");
        assert.equal(await page.getByRole("searchbox",{name:"搜索当前目录",exact:true}).inputValue(),"分页");
      });
      await check(`${prefix}旧分页响应不能覆盖新查询或新身份`,async()=>{
        for(const identity of [false,true]) {
          await page.evaluate(detail=>window.start(detail),detail);
          await pager.getByRole("status").filter({hasText:"共 101 条 · 1–20 条"}).waitFor();
          await page.evaluate(key=>{window.testState.delayPage=2;window.draw({...window.testState.route,[key]:2});},pageKey);
          await page.waitForFunction(()=>window.testState.resolves.length>0);
          await page.evaluate(({identity,key})=>{
            window.testState.delayPage=0;if(identity)window.testState.identity++;
            window.draw({...window.testState.route,[key]:1,query:"101"});
          },{identity,key:pageKey});
          await pager.getByRole("status").filter({hasText:"共 1 条 · 1–1 条"}).waitFor();
          await page.evaluate(()=>{window.testState.resolves.splice(0).forEach(resolve=>resolve());});
          await page.waitForTimeout(100);
          assert.equal(await page.getByText(rowName(101),{exact:true}).count(),1);
          assert.equal(await page.getByText(rowName(21),{exact:true}).count(),0);
        }
      });
      for(const width of [390,1024,1440])await check(`${prefix}${width}及200%文字下分页控件可见`,async()=>{
        await page.setViewportSize({width,height:1000});await page.evaluate(detail=>window.start(detail),detail);
        await size.waitFor();await size.selectOption("10");
        await pager.getByRole("status").filter({hasText:"共 101 条 · 1–10 条"}).waitFor();
        for(const enlarged of [false,true]) {
          await page.evaluate(enlarged=>document.documentElement.style.fontSize=enlarged?"200%":"",enlarged);
          if(!enlarged) {
            const expectedHeight=width<=680?44:32;
            assert.equal(await page.getByRole("searchbox",{name:"搜索当前目录",exact:true}).evaluate(item=>item.getBoundingClientRect().height),width<=680?44:36);
            for(const control of await pager.locator("button,input,select").all()) {
              assert.equal(await control.evaluate(item=>item.getBoundingClientRect().height),expectedHeight);
            }
          }
          assert.equal(await pager.evaluate(item=>item.scrollWidth<=item.clientWidth+1),true);
          for(const control of await pager.locator("button,input,select").all()) {
            if(await control.isDisabled())continue;
            await control.scrollIntoViewIfNeeded();
            assert.equal(await control.evaluate(item=>{const box=item.getBoundingClientRect();const hit=document.elementFromPoint(box.x+box.width/2,box.y+box.height/2);return box.width>0&&box.height>0&&box.x>=0&&box.right<=innerWidth+1&&(item===hit||item.contains(hit));}),true);
          }
        }
        await page.evaluate(()=>document.documentElement.style.fontSize="");
      });
    }
    assert.equal(pageErrors,0);
    process.stdout.write(JSON.stringify({status:"PASSED",checks:passed,productionAcceptance:false})+"\n");
  }catch{process.stderr.write(JSON.stringify({status:"FAILED",stage,checks:passed,productionAcceptance:false})+"\n");process.exitCode=1;}
  finally{if(browser)await browser.close();await server.close();}
}
run().catch(()=>{process.stderr.write("隔离分页回归未能启动。\n");process.exitCode=1;});
