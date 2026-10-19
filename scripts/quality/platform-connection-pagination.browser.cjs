/* WP-0019 S1 分页浏览器反例：只用隔离服务和合成页响应，不连接业务系统。 */
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { createRequire } = require("node:module");
const { pathToFileURL } = require("node:url");
const { createBrowserTestCache } = require("./browser-test-cache.cjs");
const repository = path.resolve(__dirname, "../..");
const frontend = path.join(repository, "frontend");
const fromFrontend = createRequire(path.join(frontend, "package.json"));

async function run() {
  assert.equal(require("playwright/package.json").version, "1.62.1");
  const temporary = fs.mkdtempSync("/private/tmp/wp0019-pagination-source-");
  fs.chmodSync(temporary, 0o700);
  let server, browser;
  const cache = createBrowserTestCache(temporary, async () => {
    try { await browser?.close(); } finally { await server?.close(); }
  });
  process.stdout.write(JSON.stringify({sourceArtifacts:temporary,productionAcceptance:false})+"\n");
  const harness = `import React from "react";
import {createRoot} from "react-dom/client";
import {FoundationProvider} from "/@fs/${frontend}/packages/design-system/src/providers/FoundationProvider.tsx";
import {ProductShell} from "/@fs/${frontend}/packages/design-system/src/shell/ProductShell.tsx";
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
 if(window.testState.readFail)throw new Error("受控只读请求失败");
 const response={items:matches.slice((page-1)*size,page*size),
 pageNumber:page,pageSize:size,totalElements:total,totalPages:Math.ceil(total/size),hasPrevious:page>1,hasNext:page*size<total};
 if(page===window.testState.delayPage)await new Promise(resolve=>window.testState.resolves.push(resolve));return response;};
const client={hasSession:()=>window.testState.session,listPlatformProviderConnections:(page,size,q)=>result("connections",page,size,q),
 getPlatformProviderConnectionDirectorySummary:async()=>({publishedConnections:21}),
 getPlatformProviderPresets:async()=>({writesAvailable:true,items:[{id:"CUSTOM",name:"自定义连接",capabilities:["CHAT"],variants:[{capability:"CHAT"}]}]}),
 getPlatformProviderConnection:async id=>({...connection,id,displayName:"分页连接"+id.slice(-3)}),getPlatformProviderConnectionPublication:async id=>({connectionId:id,connectionRevision:3,published:true}),
 listPlatformProviderModelCandidates:async(id,version,page,size,q)=>result("candidates",page,size,q)};
const root=createRoot(document.getElementById("root"));
window.showShell=scope=>root.render(<FoundationProvider><ProductShell productName="可信 RAG" workspaceScope={scope}
 navigation={[]} activeNavigationId="" accountLabel="样式验收账户"><h1>样式验收</h1></ProductShell></FoundationProvider>);
window.draw=(route,mode="replaceState")=>{window.testState.route=route;history[mode]({},"",formatConsoleRoute(route));
 const content=route.task==="connections"
 ?<PlatformModelSupplyWorkspace key={window.testState.identity} client={client} route={route} sessionRevision={window.testState.identity} onRouteChange={(next,replace=false)=>window.draw(next,replace?"replaceState":"pushState")}/>
 :<PlatformProviderConnectionDetail key={window.testState.identity} client={client} route={route} onRouteChange={(next,replace=false)=>window.draw(next,replace?"replaceState":"pushState")}/>;
 root.render(<FoundationProvider>{window.testState.withShell
 ?<ProductShell productName="可信 RAG" workspaceScope="platform" activeNavigationId="models" accountLabel="样式验收账户"
 navigation={[{id:"overview",label:"平台治理概览",icon:"overview"},{id:"tenants",label:"租户管理",icon:"data"},{id:"accounts",label:"平台账户",icon:"accounts"},{id:"models",label:"模型治理",icon:"models"}]}>{content}</ProductShell>
 :content}</FoundationProvider>);};
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
  server = await createServer({ configFile:false,root:temporary,cacheDir:cache.cacheDir,plugins:[react()],resolve:{alias},
    server:{host:"127.0.0.1",port:0,fs:{allow:[temporary,frontend]}},logLevel:"error" });
  let passed=0; let stage="启动隔离浏览器";
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
        if(detail) {
          // 候选为空仍是已有连接的详情；分页与连接不存在的空态不能混用。
          await pager.getByRole("status").filter({hasText:"共 0 条 · 0–0 条"}).waitFor();
          assert.equal(await pager.getByRole("button",{name:"上一页",exact:true}).isDisabled(),true);
          assert.equal(await pager.getByRole("button",{name:"下一页",exact:true}).isDisabled(),true);
        } else {
          // 新接受原型的真实空目录仅有说明和创建入口，不保留无数据分页表格。
          await page.getByRole("heading",{name:"平台模型连接目前为空",exact:true}).waitFor();
          assert.equal(await page.locator("table").count(),0);
          assert.equal(await pager.count(),0);
          assert.equal(await page.getByRole("button",{name:"新增供应商连接",exact:true}).isEnabled(),true);
        }
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
          await page.waitForFunction(enlarged=>getComputedStyle(document.documentElement).fontSize===(enlarged?"32px":"16px"),enlarged);
          await page.waitForFunction(enlarged=>[...document.querySelectorAll('.platform-connection-search input,.platform-connection-pagination select')]
            .every(item=>getComputedStyle(item).fontSize===(enlarged?"32px":"16px")),enlarged);
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
    await check("连接目录桌面操作与已接受原型的标题首行对齐",async()=>{
      await page.setViewportSize({width:1440,height:1000});
      await page.evaluate(()=>window.start(false));
      await page.waitForFunction(()=>getComputedStyle(document.documentElement).fontSize==="16px");
      const heading=page.getByRole("heading",{name:"平台模型连接",exact:true});
      const create=page.getByRole("button",{name:"新增供应商连接",exact:true});
      await page.getByText("分页连接001",{exact:true}).waitFor();
      await heading.scrollIntoViewIfNeeded();
      const alignment=await page.locator(".platform-connection-page__heading").evaluate(header=>{
        const title=header.querySelector("h1").getBoundingClientRect(),button=header.querySelector("button").getBoundingClientRect();
        return Math.abs(title.y-button.y);
      });
      assert.ok(alignment<=1,"桌面创建操作应与标题首行对齐");
    });
    await check("连接目录手机视图沿用原型满宽创建操作、卡片表格及左对齐分页",async()=>{
      await page.setViewportSize({width:390,height:1000});
      const header=page.locator(".platform-connection-page__heading");
      const create=page.getByRole("button",{name:"新增供应商连接",exact:true});
      const headerBox=await header.boundingBox(),buttonBox=await create.boundingBox();
      assert.ok(Math.abs(headerBox.width-buttonBox.width)<=1,"手机创建操作应占满页头宽度");
      assert.equal(await page.locator("tbody tr").first().evaluate(item=>getComputedStyle(item).display),"block");
      assert.equal(await page.locator("tbody td").first().evaluate(item=>getComputedStyle(item).display),"grid");
      const tableHead=await page.locator("thead").evaluate(item=>{
        const box=item.getBoundingClientRect(),style=getComputedStyle(item);
        return {width:box.width,height:box.height,position:style.position,clipPath:style.clipPath};
      });
      assert.deepEqual(tableHead,{width:1,height:1,position:"absolute",clipPath:"inset(50%)"});
      assert.equal(await page.locator("thead th").count(),4,"手机视图保留可访问表头");
      assert.equal(await page.getByRole("navigation",{name:"目录分页",exact:true})
        .evaluate(item=>getComputedStyle(item).justifyContent),"flex-start");
    });
    await check("目录搜索标签与已接受原型的字重和边框一致",async()=>{
      const search=page.getByRole("searchbox",{name:"搜索当前目录",exact:true});
      const styles=await search.evaluate(item=>{
        const label=item.closest("label").querySelector("span"),style=getComputedStyle(item);
        const reference=document.createElement("div");reference.style.border="1px solid var(--foundation-border-strong)";document.body.append(reference);
        const border=getComputedStyle(reference).border;reference.remove();
        return {weight:getComputedStyle(label).fontWeight,lineHeight:style.lineHeight,border:style.border,expectedBorder:border};
      });
      assert.equal(styles.weight,"620");
      assert.equal(styles.lineHeight,"24px");
      assert.equal(styles.border,styles.expectedBorder);
    });
    await check("手机目录真实200%文字放大时查看操作不被卡片行高裁切",async()=>{
      const {enlargeText}=require("../development/acceptance-overview.cjs");
      const {verifyLayout}=require("../development/acceptance-platform-connection.cjs");
      await enlargeText(page);
      await verifyLayout(page);
    });
    await check("S1两页手机卡片在正常与真实200%文字下不进入相邻单元格或分页",async()=>{
      const {enlargeText}=require("../development/acceptance-overview.cjs");
      const observations=[];
      for(const detail of [false,true]) {
        await page.reload();await page.evaluate(detail=>window.start(detail,21),detail);
        await page.getByText(detail?"分页模型001":"分页连接001",{exact:true}).waitFor();
        await page.getByRole("status").filter({hasText:"共 21 条 · 1–20 条"}).waitFor();
        const normalTitleSize=await page.locator("h1").evaluate(item=>parseFloat(getComputedStyle(item).fontSize));
        for(const enlarged of [false,true]) {
          if(enlarged) {
            await enlargeText(page);
            await page.waitForFunction(expected=>Math.abs(parseFloat(getComputedStyle(document.querySelector("h1")).fontSize)-expected)<0.01,normalTitleSize*2);
          }
          const metrics=await page.evaluate(()=>{
            const pager=document.querySelector(".platform-connection-pagination").getBoundingClientRect();
            const cells=[...document.querySelectorAll("tbody td")];
            const overlap=cells.filter(cell=>{
              const box=cell.getBoundingClientRect(),walker=document.createTreeWalker(cell,NodeFilter.SHOW_TEXT);
              while(walker.nextNode()) {
                if(!walker.currentNode.textContent.trim())continue;
                const range=document.createRange();range.selectNodeContents(walker.currentNode);
                if([...range.getClientRects()].some(rect=>rect.top<box.top-1||rect.bottom>box.bottom+1||rect.bottom>pager.top+1))return true;
              }
              return cell.scrollHeight>cell.clientHeight+1;
            }).length;
            const title=document.querySelector("h1");
            return {overlap,overflow:document.documentElement.scrollWidth-innerWidth,
              titleSize:parseFloat(getComputedStyle(title).fontSize)};
          });
          observations.push({detail,enlarged,normalTitleSize,...metrics});
        }
      }
      process.stdout.write(JSON.stringify({cardReflow:observations})+"\n");
      assert.equal(observations.some(item=>Math.abs(item.titleSize-item.normalTitleSize*(item.enlarged?2:1))>=0.01),false,"标题文字必须实际放大");
      assert.equal(observations.some(item=>item.overlap>0),false,"手机卡片文字不能进入相邻单元格或分页");
      assert.equal(observations.some(item=>item.overflow>1),false,"手机页面不能横向溢出");
    });
    await check("平台与租户共享顶栏范围说明沿用原型中性色和文字基线",async()=>{
      await page.reload();
      for(const scope of ["platform","tenant"]) {
        await page.evaluate(scope=>window.showShell(scope),scope);
        const context=page.locator(`.product-shell[data-scope="${scope}"] .product-topbar__context`);
        await context.waitFor();
        const style=await context.evaluate(item=>{
          const value=getComputedStyle(item);return {color:value.color,fontSize:value.fontSize,fontWeight:value.fontWeight,lineHeight:value.lineHeight};
        });
        process.stdout.write(JSON.stringify({scopeContext:{scope,...style}})+"\n");
        assert.deepEqual(style,{color:"rgb(94, 93, 89)",fontSize:"14px",fontWeight:"650",lineHeight:"22.4px"});
      }
    });
    await check("手机200%文字下真实外壳吸顶导航不遮挡目录操作",async()=>{
      const {enlargeText}=require("../development/acceptance-overview.cjs");
      const {verifyLayout}=require("../development/acceptance-platform-connection.cjs");
      await page.setViewportSize({width:390,height:844});await page.reload();
      await page.evaluate(()=>{window.testState.withShell=true;window.start(false,1);});
      await page.getByRole("status").filter({hasText:"共 1 条 · 1–1 条"}).waitFor();
      await verifyLayout(page);
      await enlargeText(page);
      await page.waitForFunction(()=>Math.abs(parseFloat(getComputedStyle(document.querySelector("h1")).fontSize)-52.8)<0.01);
      await verifyLayout(page);
      const wrappedAccount=await page.evaluate(()=>{
        const bar=document.querySelector(".product-topbar"),context=bar.querySelector(".product-topbar__context").getBoundingClientRect();
        const account=bar.querySelector(".product-account").getBoundingClientRect();
        return {wrapped:account.top>=context.bottom-1,offset:account.left-bar.getBoundingClientRect().left-parseFloat(getComputedStyle(bar).paddingLeft)};
      });
      assert.equal(wrappedAccount.wrapped,true,"必须真实触发账户菜单换行");
      assert.ok(Math.abs(wrappedAccount.offset)<=1,"换行账户菜单应与原型一致，从顶栏内容起始位置排列");
    });
    await check("S1两页加载和错误保留已接受原型页头及说明面板",async()=>{
      await page.setViewportSize({width:390,height:844});
      for(const detail of [false,true]) {
        await page.reload();
        await page.evaluate(detail=>{window.testState.delayPage=1;window.start(detail,21);},detail);
        await page.locator('.state-panel-loading,section[data-kind="loading"]').waitFor();
        assert.equal(await page.getByRole("heading",{name:detail?"供应商连接探测":"平台模型连接",exact:true}).count(),1);
        assert.equal(await page.locator("h1").evaluate(item=>getComputedStyle(item).minHeight),"auto","页头正文沿用原型 grid 排布");
        await page.evaluate(()=>{window.testState.delayPage=0;window.testState.readFail=true;window.testState.resolves.splice(0).forEach(resolve=>resolve());window.start(window.testState.route.task==="connection-detail",21);});
        const panel=page.locator('section[data-kind="error"]');await panel.waitFor();
        await panel.getByRole("heading",{name:detail?"暂时无法加载供应商连接探测":"暂时无法加载平台模型连接",exact:true}).waitFor();
        await panel.getByRole("button",{name:"重新加载",exact:true}).click();
        await panel.waitFor();
        assert.equal(await panel.isVisible(),true,"再次失败不能使用旧成功结果");
        await page.evaluate(()=>{window.testState.readFail=false;});
        await panel.getByRole("button",{name:"重新加载",exact:true}).click();
        await page.getByRole("status").filter({hasText:"共 21 条 · 1–20 条"}).waitFor();
      }
    });
    for(const width of [390,1024,1440])for(const detail of [false,true])await check(`${detail?"发现模型":"连接目录"}${width}分页外观与原型一致且真实200%文字不裁切`,async()=>{
      const {enlargeText}=require("../development/acceptance-overview.cjs");
      await page.setViewportSize({width,height:1000});await page.reload();
      await page.evaluate(detail=>window.start(detail,1),detail);
      const pager=page.getByRole("navigation",{name:detail?"发现模型分页":"目录分页",exact:true});
      await pager.getByRole("status").filter({hasText:"共 1 条 · 1–1 条"}).waitFor();
      for(const enlarged of [false,true]) {
        if(enlarged)await enlargeText(page);
        await page.waitForFunction(size=>getComputedStyle(document.querySelector('.platform-connection-pagination select')).fontSize===size,enlarged?"32px":"16px");
        for(const hover of [false,true]) {
          const previous=pager.getByRole("button",{name:"上一页",exact:true});
          if(hover)await previous.hover({force:true});else await page.mouse.move(0,0);
          await pager.evaluate(async nav=>{
            const controls=[...nav.querySelectorAll("button,input,select")];controls.forEach(e=>getComputedStyle(e).color);
            await Promise.all(controls.flatMap(e=>e.getAnimations()).map(a=>a.finished.catch(()=>{})));
          });
          for(const name of ["上一页","下一页"]) {
            const button=pager.getByRole("button",{name,exact:true});assert.equal(await button.isDisabled(),true);
            const style=await button.evaluate(e=>{const s=getComputedStyle(e),b=e.getBoundingClientRect();return {weight:s.fontWeight,color:s.color,background:s.backgroundColor,border:s.borderTopColor,padding:s.padding,gap:s.gap,opacity:s.opacity,width:b.width,height:b.height};});
            assert.deepEqual(style,{weight:"620",color:"rgb(94, 93, 89)",background:"rgb(245, 244, 237)",border:"rgb(232, 230, 220)",padding:"4px 8px",gap:"8px",opacity:"0.76",width:enlarged?102:60,height:enlarged?50:width<=680?44:32});
          }
          const current=pager.locator('[aria-current="page"]');
          const currentStyle=await current.evaluate(e=>{const s=getComputedStyle(e);return {weight:s.fontWeight,padding:s.padding,border:s.borderTopColor,background:s.backgroundColor,cursor:s.cursor};});
          assert.deepEqual(currentStyle,{weight:"620",padding:"4px",border:"oklab(0.514837 0.0810745 0.066312)",background:"oklab(0.514837 0.0810745 0.066312)",cursor:"pointer"});
          for(const control of await pager.locator("input,select").all()) {
            const style=await control.evaluate(e=>{const s=getComputedStyle(e),b=e.getBoundingClientRect();return {color:s.color,border:s.borderTopColor,padding:s.padding,lineHeight:s.lineHeight,height:b.height};});
            const tag=await control.evaluate(e=>e.tagName);
            // Chrome 对原生 `select` 的计算行高保留 `normal`；`input` 必须匹配原型的实际像素行高。
            assert.deepEqual(style,{color:"rgb(94, 93, 89)",border:"oklab(0.665396 -0.000483552 0.00399556)",padding:"0px 12px",lineHeight:tag==="SELECT"?"normal":enlarged?"48px":"24px",height:enlarged?56:width<=680?44:32});
          }
        }
        await pager.locator('[aria-current="page"]').focus();await page.keyboard.press("Tab");
        assert.equal(await pager.getByLabel("跳转页码",{exact:true}).evaluate(e=>e===document.activeElement),true,"Tab应跳过不可用的下一页");
        await page.keyboard.press("Tab");assert.equal(await pager.getByRole("button",{name:"跳转",exact:true}).evaluate(e=>e===document.activeElement),true);
        assert.equal(await pager.evaluate(e=>e.scrollWidth<=e.clientWidth+1),true);
      }
    });
    assert.equal(pageErrors,0);
    process.stdout.write(JSON.stringify({status:"PASSED",checks:passed,productionAcceptance:false})+"\n");
  }catch(error){
    process.stderr.write(JSON.stringify({status:"FAILED",stage,checks:passed,sourceLine:error?.stack?.split("\n").find(line=>line.includes(__filename)),productionAcceptance:false})+"\n");
    if(browser) {
      const pages=browser.contexts().flatMap(context=>context.pages());const current=pages[0];
      if(current) {
        process.stderr.write(JSON.stringify(await current.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth,rootFont:getComputedStyle(document.documentElement).fontSize,rootInlineFont:document.documentElement.style.fontSize,
          controls:[...document.querySelectorAll('.platform-connection-pagination button,.platform-connection-pagination input,.platform-connection-pagination select')].map(item=>{
            const box=item.getBoundingClientRect(),style=getComputedStyle(item);return {tag:item.tagName,width:box.width,height:box.height,x:box.x,y:box.y,disabled:item.disabled,font:style.fontSize,cssWidth:style.width,transition:style.transition};
          })})))+"\n");
        const image=path.join(temporary,"failure-layout.png");await current.screenshot({path:image,fullPage:true});fs.chmodSync(image,0o600);
      }
    }
    process.exitCode=1;
  }
  finally{await cache.close();}
}
run().catch(()=>{process.stderr.write("隔离分页回归未能启动。\n");process.exitCode=1;});
