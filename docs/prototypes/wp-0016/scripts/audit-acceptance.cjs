// WP-0016 验收调查脚本：仅在隔离浏览器中操作合成原型，收集行为事实，不访问生产服务。
// 本脚本不是通过门禁：无监听器只是调查线索；应结合下方实际交互结果与源码判断缺陷。
// 页面改正后部分基线探针可能记录 executionError，不能把探针完成或进程退出码当成人工验收通过。
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { execFileSync } = require('node:child_process');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '..');
const base = process.env.PROTOTYPE_BASE_URL || 'http://127.0.0.1:4176/';
const output = process.env.PROTOTYPE_AUDIT_REPORT || path.join(os.tmpdir(), 'wp0016-acceptance-audit.json');
const inventory = JSON.parse(fs.readFileSync(root + '/page-inventory.json', 'utf8'));
const pages = inventory.groups.flatMap(g => g.pages.map(p => ({ ...p, phase: g.phase })));
const excluded = ['device-credentials', 'channel-binding'];
const report = { auditedHead: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(), generatedAt: new Date().toISOString(), excluded, controls: [], probes: [], errors: [] };
const url = (screen, state='success') => `${base}?screen=${screen}&state=${state}&review=0`;
let browser;
async function fresh(screen) {
  const context = await browser.newContext({viewport:{width:1440,height:960}, reducedMotion:'reduce'});
  await context.addInitScript(() => {
    const original = EventTarget.prototype.addEventListener;
    window.auditListeners = new WeakMap();
    EventTarget.prototype.addEventListener = function(type, ...args) {
      const events = window.auditListeners.get(this) || [];
      events.push(type); window.auditListeners.set(this, events);
      return original.call(this, type, ...args);
    };
  });
  const page = await context.newPage(); page.setDefaultTimeout(4000);
  page.on('pageerror',e=>report.errors.push({screen,message:e.message}));
  await page.goto(url(screen)); await page.locator('main').waitFor();
  return { context, page };
}
async function probe(name, fn) {
  try { report.probes.push({name, ...(await fn())}); }
  catch(e) { report.probes.push({name, executionError:e.message}); }
}
async function snapshot(page) {
  return page.evaluate(()=>({url:location.href, text:document.querySelector('#app')?.innerText || document.body.innerText, dialogs:document.querySelectorAll('dialog[open]').length, storage:localStorage.getItem('wp0016-business-demo-v1')}));
}
async function main() {
  browser = await chromium.launch({headless:true});
  const sample = await fresh('platform-overview');
  for(const p of pages.filter(p=>!excluded.includes(p.screen))) {
    await sample.page.goto(url(p.screen)); await sample.page.locator('main').waitFor();
    const actual = await sample.page.locator('main').getAttribute('data-screen');
    if(actual !== p.screen && p.phase==='M6') {report.controls.push({screen:p.screen,actual,requiresLogin:true});continue;}
    const controls=await sample.page.evaluate(()=>Array.from(document.querySelectorAll('main button, main input, main select, .topbar .account-button')).filter(e=>e.getClientRects().length).map(e=>({tag:e.tagName,type:e.type,label:(e.getAttribute('aria-label') || e.closest('label')?.innerText || e.innerText || e.placeholder || '').trim(),id:e.id,disabled:e.disabled,events:window.auditListeners.get(e)||[],form:e.form?.id||'',data:Object.fromEntries(Object.entries(e.dataset).filter(([k])=>k!=='odId'))})));
    report.controls.push({screen:p.screen,phase:p.phase,controls});
  }
  await sample.context.close();
  await probe('管理端登录角色和口令',async()=>{
    const results=[];
    for(const login of ['platform.admin.demo','lin.demo','does-not-exist']) {
      const {context,page}=await fresh('console-login');
      await page.locator('#console-login-form input').first().fill(login);
      await page.locator('#console-login-form input[type=password]').fill('invalid-demo-password');
      await page.locator('#console-login-form button').click();
      await page.locator('main[data-screen="platform-overview"]').waitFor();
      results.push({login,actualScreen:await page.locator('main').getAttribute('data-screen')});
      await context.close();
    }
    return {results};
  });
  await probe('审计目录搜索筛选行操作和导出',async()=>{
    const {context,page}=await fresh('audit-records');
    const rows=()=>page.locator('tbody tr:visible').allTextContents();
    const before=await rows();
    await page.locator('main input[type=search]').fill('完全不存在的审计记录');
    const afterSearch=await rows();
    await page.locator('.toolbar select').selectOption({label:'已完成'});
    const afterFilter=await rows();
    const beforeClick=await snapshot(page); await page.locator('tbody tr').nth(1).getByRole('button',{name:'查看',exact:true}).click();
    const secondRowChanged=JSON.stringify(beforeClick)!==JSON.stringify(await snapshot(page));
    const downloads=[];page.on('download',d=>downloads.push(d.suggestedFilename()));
    await page.getByRole('button',{name:'导出受控记录',exact:true}).click();
    const exportDestination=await page.locator('main').getAttribute('data-screen');
    const result={before,afterSearch,afterFilter,secondRowChanged,exportDestination,downloads};
    await context.close();return result;
  });
  await probe('账户菜单',async()=>{
    const {context,page}=await fresh('tenant-overview');const before=await snapshot(page);
    await page.locator('.account-button').click();const changed=JSON.stringify(before)!==JSON.stringify(await snapshot(page));
    await context.close();return {changed};
  });
  await probe('平台账户空态创建入口',async()=>{
    const {context,page}=await fresh('platform-accounts');await page.goto(url('platform-accounts','empty'));await page.locator('main[data-state="empty"]').waitFor();
    const before=await snapshot(page);await page.getByRole('button',{name:'创建人员账户',exact:true}).click();
    const changed=JSON.stringify(before)!==JSON.stringify(await snapshot(page));await context.close();return {changed};
  });
  await probe('来源失败处理总览主操作',async()=>{
    const {context,page}=await fresh('processing-quality');
    const before=await snapshot(page);await page.getByRole('button',{name:'重试失败项',exact:true}).click();
    const after=await snapshot(page);const result={destination:await page.locator('main').getAttribute('data-screen'),stateChanged:before.storage!==after.storage};
    await context.close();return result;
  });
  await probe('结构草稿未保存直接预检发布',async()=>{
    const {context,page}=await fresh('dataset-schema');
    await page.getByLabel('floor 的业务含义').fill('楼层说明更新');
    await page.getByRole('button',{name:'运行抽样预检',exact:true}).click();
    const afterPreview=await page.getByLabel('floor 的业务含义').inputValue();
    await page.getByRole('button',{name:'发布结构版本',exact:true}).click();
    const saved=await page.evaluate(()=>{const s=JSON.parse(localStorage.getItem('wp0016-business-demo-v1'));const d=s.dataGovernance.structuredDatasets[0];return d.versions.find(v=>v.id===d.publishedVersionId).fields.find(f=>f.name==='floor').semantic;});
    await context.close();return {typed:'楼层说明更新',afterPreview,published:saved};
  });
  await probe('登记对象范围后进入来源目录',async()=>{
    const {context,page}=await fresh('object-storage-scope');
    await page.getByRole('button',{name:'登记对象范围',exact:true}).click();
    await page.getByLabel('来源名称').fill('验收对象来源');
    await page.getByRole('button',{name:'确认登记',exact:true}).click();
    const registered=(await page.locator('main').innerText()).includes('验收对象来源');
    const operations=await page.locator('tbody tr').last().getByRole('button').allTextContents();
    await page.goto(url('knowledge-sources'));await page.locator('main[data-screen="knowledge-sources"]').waitFor();
    const visible=(await page.locator('main').innerText()).includes('验收对象来源');
    const state=await page.evaluate(()=>{const s=JSON.parse(localStorage.getItem('wp0016-business-demo-v1'));return {registrations:s.dataGovernance.objectScopeRegistrations.length,sources:s.knowledgeBases.flatMap(k=>k.sources||[]).filter(v=>v.name==='验收对象来源').length};});
    await context.close();return {registered,operations,visibleInSourceDirectory:visible,state};
  });
  await probe('知识目录筛选与对象上下文',async()=>{
    const {context,page}=await fresh('knowledge-directory');
    const before=await page.locator('tbody tr:visible').allTextContents();
    await page.getByRole('searchbox',{name:'搜索知识库'}).fill('完全不存在的知识库');
    const afterSearch=await page.locator('tbody tr:visible').allTextContents();
    await page.locator('.toolbar select').selectOption({label:'已就绪'});
    const afterFilter=await page.locator('tbody tr:visible').allTextContents();
    await page.locator('tr',{hasText:'客户支持知识'}).getByRole('button',{name:'进入',exact:true}).click();
    const actualOverview=await page.locator('main').innerText();
    const selected=await page.evaluate(()=>JSON.parse(localStorage.getItem('wp0016-business-demo-v1')).selectedKnowledgeBaseId);
    const actualUrl=page.url();
    await context.close();return {before,afterSearch,afterFilter,selected,actualUrl,actualOverview};
  });
  await probe('快速发布输入与提交结果',async()=>{
    const {context,page}=await fresh('quick-publication');
    await page.getByLabel('处理批次').fill('验收批次');
    await page.getByLabel('质量结论').selectOption({label:'返回修复来源'});
    const before=await snapshot(page);await page.getByRole('button',{name:'开始发布',exact:true}).click();
    const after=await snapshot(page); const result={destination:await page.locator('main').getAttribute('data-screen'),stateChanged:before.storage!==after.storage,mainText:await page.locator('main').innerText()};
    await context.close();return result;
  });
  await probe('知识库空态创建入口',async()=>{
    const {context,page}=await fresh('knowledge-directory');await page.goto(url('knowledge-directory','empty'));await page.locator('main[data-state="empty"]').waitFor();
    const before=await snapshot(page);const button=page.getByRole('button',{name:'创建知识库',exact:true}).first();await button.click();
    const changed=JSON.stringify(before)!==JSON.stringify(await snapshot(page));const dialogs=await page.locator('dialog[open]').count();
    await context.close();return {changed,dialogs};
  });
  await probe('普通用户登录后直接访问管理页',async()=>{
    const {context,page}=await fresh('login');
    await page.locator('#login-form [name=tenantCode]').fill('spruce-demo');
    await page.locator('#login-form [name=username]').fill('demo.user');
    await page.locator('#login-form [name=password]').fill('demo-only');
    await page.locator('#login-form button[type=submit]').click();
    await page.locator('main[data-screen="assistants"]').waitFor();
    const assistantsControls=await page.locator('main button, main input').count();
    await page.locator('[data-assistant-id="assistant-employee-service"]').click();
    await page.locator('main[data-screen="conversation"]').waitFor();
    const conversationControls=await page.locator('main button, main input, main textarea').count();
    await page.goto(url('knowledge-directory'));await page.locator('main[data-screen="knowledge-directory"]').waitFor();
    const renderedState=await page.locator('main').getAttribute('data-state');
    const protectedVisible=(await page.locator('main').innerText()).includes('员工服务手册');
    const button=page.getByRole('button',{name:'创建知识库',exact:true});
    let canCreate=false;
    if(await button.isVisible() && !(await button.isDisabled())) {
      await button.click();await page.getByLabel('知识库名称',{exact:true}).fill('普通成员创建的验收库');
      await page.getByRole('button',{name:'确认创建',exact:true}).click();
      canCreate=(await page.locator('main').innerText()).includes('普通成员创建的验收库');
    }
    await context.close();return {renderedState,protectedVisible,canCreate,queryPages:{assistantsControls,conversationControls}};
  });
  await probe('知识库深链接在不同标签页的对象恢复',async()=>{
    const {context,page}=await fresh('knowledge-directory');
    await page.locator('[data-kb-row="kb-handbook"]').getByRole('button',{name:'进入',exact:true}).click();
    await page.getByRole('button',{name:'继续当前阻断任务',exact:true}).click();
    const firstUrl=page.url();const initialTitle=await page.locator('.page-object-line').innerText();
    const second=await context.newPage();await second.goto(url('knowledge-directory'));await second.locator('main[data-screen="knowledge-directory"]').waitFor();
    await second.locator('[data-kb-row="kb-support"]').getByRole('button',{name:'进入',exact:true}).click();
    await page.reload();await page.locator('main[data-screen="knowledge-sources"]').waitFor();
    const afterReload=await page.locator('.page-object-line').innerText();
    await page.goto(url('knowledge-overview'));await page.locator('main[data-screen="knowledge-overview"]').waitFor();
    const overview=await page.locator('main').innerText();
    await context.close();return {firstUrl,initialTitle,afterReload,sameUrl:firstUrl===url('knowledge-sources'),overview};
  });
  await probe('关闭本人历史后创建新会话',async()=>{
    const {context,page}=await fresh('tenant-members');
    await page.locator('[data-member-feature="ownHistory"]').uncheck();
    await page.getByRole('button',{name:'保存对话功能',exact:true}).click();
    await page.goto(url('login'));await page.locator('#login-form').waitFor();
    await page.locator('#login-form [name=tenantCode]').fill('spruce-demo');
    await page.locator('#login-form [name=username]').fill('demo.user');
    await page.locator('#login-form [name=password]').fill('demo-only');
    await page.locator('#login-form button[type=submit]').click();
    await page.locator('main[data-screen="assistants"]').waitFor();
    await page.locator('[data-assistant-id="assistant-employee-service"]').click();
    const actualScreen=await page.locator('main').getAttribute('data-screen');
    const renderedState=await page.locator('main').getAttribute('data-state');
    const feedback=await page.locator('main').innerText();
    const member=await page.evaluate(()=>JSON.parse(localStorage.getItem('wp0016-business-demo-v1')).tenants[0].members.find(m=>m.accountId==='account-shen'));
    await context.close();return {actualScreen,renderedState,feedback,features:member.conversationFeatures};
  });
  fs.writeFileSync(output, JSON.stringify(report,null,2));
  console.log(JSON.stringify({pageCount:report.controls.length,probes:report.probes,errors:report.errors},null,2));
  await browser.close();
}
main().catch(async e=>{console.error(e);await browser?.close();process.exitCode=1;});
