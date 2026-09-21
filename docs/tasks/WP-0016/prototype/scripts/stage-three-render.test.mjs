import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import * as state from '../prototype-state.js';
import * as full from '../full-platform.js';
import * as objects from '../object-workspaces.js';
import * as navigation from '../navigation.js';
import * as journeys from '../workspace-journeys.js';
import * as directories from '../governance-directories.js';
import * as pagination from '../directory-pagination.js';
import * as providers from '../provider-presets.js';
const read=name=>JSON.parse(readFileSync(new URL('../'+name,import.meta.url),'utf8'));
const inventory=read('page-inventory.json'), fixtures=read('fixtures.json'), pageFixtures=read('page-fixtures.json');
const pages=inventory.groups.flatMap(group=>group.pages.map(page=>({page,group})));
const source=readFileSync(new URL('../prototype.js',import.meta.url),'utf8');
// 调用产品原有渲染函数，不复制模板；不模拟浏览器布局或真实认证。
function renderer(store,url){
  const deps={...state,...full,...objects,...navigation,...journeys,...directories,...pagination,...providers,prototypeStore:store,inventory,fixtures,pageFixtures,location:url,window:{location:url},pageByScreen:new Map(pages.map(({page})=>[page.screen,page])),groupByScreen:new Map(pages.map(({page,group})=>[page.screen,group]))};
  const variables=source.slice(source.indexOf('let selectedAssistantId'),source.indexOf('const prototypeStore ='));
  return Function(...Object.keys(deps),variables+source.slice(source.indexOf('function currentScenario()'))+';return renderScreen;')(...Object.values(deps));
}
test('全部页面使用产品渲染函数生成标题，目录在进入 DOM 前有界',()=>{
  const failures=[];
  for(const {page,group} of pages){
    const store=state.createPrototypeStore(state.createMemoryStorage());
    if(group.shell==='query')store.loginQuery({tenantCode:'spruce-demo',username:'shen.demo',password:'demo-only'});
    else store.loginConsole({username:group.id.startsWith('platform-')?'platform.admin.demo':'qiao.demo',password:'demo-only'});
    const url=new URL('http://prototype.invalid/?screen='+page.screen);
    try{const render=renderer(store,url), snapshot=store.getWorkspaceSnapshot();
      const html=objects.boundedDirectoryMarkup(render(page.screen,'success',snapshot),url.searchParams);
      assert.ok(html.includes('<h1'),page.screen);
      if(html.includes('data-object-pagination')) assert.ok((html.match(/<tbody>[\s\S]*?<\/tbody>/)?.[0].match(/<tr\b/g)||[]).length<=20,page.screen);
    }catch(error){failures.push({screen:page.screen,error:error.message});}
  }
  assert.deepEqual(failures,[]);
});
test('平台一万条记录公共输出只有五条，加载更早使用序号窗口',()=>{
  const store=state.createPrototypeStore(state.createMemoryStorage()), snapshot=store.getSnapshot();
  const incident=snapshot.health.incidents.find(item=>item.id===snapshot.health.selectedIncidentId);
  incident.records=Array.from({length:10000},(_,i)=>({sequence:i+1,time:'合成时刻',text:`合成记录 ${i+1}`}));
  const {page,group}=pages.find(({page})=>page.screen==='platform-health');
  const html=full.renderInventoryPage(page,group,pageFixtures.pages[page.screen],'success',fixtures.tenant,snapshot);
  assert.equal((html.match(/data-event-sequence=/g)||[]).length,5);
  assert.ok(html.includes('合成记录 10000'));
  assert.ok(!html.includes('合成记录 1</'));
  assert.equal(objects.recordWindow(incident.records,9996,5).at(-1).sequence,9995);
});
test('管理页默认正文不泄漏英文阶段与旧产品术语',()=>{
  const failures=[];
  for(const {page,group} of pages.filter(item=>item.group.phase==='M6'&&item.group.shell==='console')){
    const store=state.createPrototypeStore(state.createMemoryStorage());
    store.loginConsole({username:group.id.startsWith('platform-')?'platform.admin.demo':'qiao.demo',password:'demo-only'});
    const html=renderer(store,new URL('http://prototype.invalid/?screen='+page.screen))(page.screen,'success',store.getWorkspaceSnapshot());
    const text=html.replace(/<(details|dialog|option|pre|code)\b[^>]*>[\s\S]*?<\/\1>/g,'').replace(/<[^>]*>/g,' ');
    const terms=text.match(/\b(?:DRAFT|PUBLISHED|READY|OPENAI_COMPATIBLE|RECEIVED|SCANNING|EXTRACTING|CHUNKING|INDEXING|Chat|Embedding|Rerank)\b|口令|创建稳定助手/g);
    if(terms)failures.push({screen:page.screen,terms:[...new Set(terms)]});
  }
  assert.deepEqual(failures,[]);
});
