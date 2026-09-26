import test from 'node:test';
import assert from 'node:assert/strict';
import { createPrototypeStore, createMemoryStorage, DEFAULT_TENANT_ID as tenant } from '../prototype-state.js';
import { memberWorkspace } from '../member-workspace.js';
import { memberDrafts } from '../member-drafts.js';
import { documentWorkspace, boundedDirectoryMarkup, recordWindow } from '../object-workspaces.js';
import { createStressSnapshot } from '../stress-fixtures.js';
import { readFileSync } from 'node:fs';

test('成员独立保存不夹带另一范围的旧草稿', () => {
  const store=createPrototypeStore(createMemoryStorage());
  const before=store.getSnapshot().tenants.find(t=>t.id===tenant).members.find(m=>m.accountId==='account-shen');
  store.setMemberAssistantDraft(tenant,'account-shen',[]);
  assert.equal(store.saveMemberScope(tenant,'account-shen','features',{...before.conversationFeatures,ownHistory:false},'account-qiao').ok,true);
  const after=store.getSnapshot().tenants.find(t=>t.id===tenant).members.find(m=>m.accountId==='account-shen');
  assert.deepEqual(after.assistantIds,before.assistantIds);
  assert.equal(after.conversationFeatures.ownHistory,false);
  assert.equal(store.saveMemberScope(tenant,'account-shen','assistants',[],'account-qiao').ok,true);
  assert.equal(store.getSnapshot().tenants.find(t=>t.id===tenant).members.find(m=>m.accountId==='account-shen').conversationFeatures.ownHistory,false);
});
test('独立授权保存拒绝越权并对存储失败回滚', () => {
  const storage=createMemoryStorage(), store=createPrototypeStore(storage), before=store.getSnapshot();
  assert.equal(store.saveMemberScope(tenant,'account-shen','assistants',[],'account-shen').ok,false);
  assert.equal(store.saveMemberScope(tenant,'account-shen','assistants',['missing'],'account-qiao').ok,false);
  storage.setItem=()=>{throw Error('磁盘不可用');};
  assert.equal(store.saveMemberScope(tenant,'account-shen','assistants',[],'account-qiao').ok,false);
  assert.deepEqual(store.getSnapshot(),before);
});
test('非法成员标识不得回退到演示成员',()=>{
  const snapshot=createPrototypeStore(createMemoryStorage()).getSnapshot();
  snapshot.selectedMemberAccountId='missing';
  assert.match(memberWorkspace('tenant-member-account',snapshot),/请选择成员/);
  delete snapshot.selectedMemberAccountId;
  assert.match(memberWorkspace('tenant-member-account',snapshot),/请选择成员/);
});

test('非秘密草稿隔离账户和登录代次；取消清除且密码不入存储',()=>{
  const map=new Map(), storage={get length(){return map.size;},key:i=>[...map.keys()][i],getItem:k=>map.get(k)||null,setItem:(k,v)=>map.set(k,v),removeItem:k=>map.delete(k)};
  const session={authenticated:true,tenantId:tenant,accountId:'account-qiao',epoch:1};
  const drafts=memberDrafts(storage,session);
  drafts.save('create',{step:2,loginName:'member.example',features:['conversation'],assistants:[],password:'demo-only',passwordConfirmation:'demo-only'});
  assert.equal(memberDrafts(storage,session).read('create').step,2);
  assert.doesNotMatch(JSON.stringify([...map]),/password|demo-only/);
  assert.equal(memberDrafts(storage,{...session,accountId:'another'}).read('create'),null);
  assert.equal(map.size,0);
  drafts.save('create',{step:1});drafts.clear('create');assert.equal(drafts.read('create'),null);
  drafts.save('create',{step:1});memberDrafts(storage,{...session,epoch:2});assert.equal(map.size,0);
});

test('实际文档工作区预览有合成正文，记录与版本 DOM 有界',()=>{
  const base=createPrototypeStore(createMemoryStorage()).getSnapshot(), before=structuredClone(base);
  const snapshot=createStressSnapshot(base), kb=snapshot.knowledgeBases.find(k=>k.id===snapshot.selectedKnowledgeBaseId), source=kb.sources[0];
  assert.deepEqual(base,before);
  assert.equal(kb.sources.length,1000);
  assert.match(documentWorkspace(source),/年假最多结转 5 天/);
  const records=documentWorkspace(source,'records');
  assert.equal((records.match(/data-event-sequence=/g)||[]).length,20);
  assert.match(records,/序号 10000/);
  assert.doesNotMatch(records,/序号 1 ·/);
  assert.equal(recordWindow(source.processingRecords,9981)[0].sequence,9961);
  assert.equal((documentWorkspace(source,'versions').match(/<tr>/g)||[]).length,21);
  const rows=kb.sources.map(item=>`<tr><td>${item.name}</td></tr>`).join('');
  const markup=`<div class="table-shell"><table><tbody>${rows}</tbody></table><div class="pagination"><span>旧分页</span></div></div>`;
  const rendered=boundedDirectoryMarkup(markup);
  assert.equal((rendered.match(/<tr>/g)||[]).length,20);
  assert.match(rendered,/共 1000 条/);
  assert.equal((boundedDirectoryMarkup(markup,new URLSearchParams({size:'50',page:'2'})).match(/<tr>/g)||[]).length,50);
});

test('连续样板遵守处理、索引激活、评测发布和成员授权门禁',()=>{
  const storage=createMemoryStorage(),store=createPrototypeStore(storage),actor='account-qiao';
  const created=store.createKnowledgeBase({name:'样板闭环知识库',description:'仅含明确标注的合成年假说明。'},actor,tenant);
  assert.equal(created.ok,true);const kb=created.knowledgeBase.id;
  const added=store.addKnowledgeSource(kb,{mode:'paste',name:'合成年假说明',text:'合成资料：年假最多结转五天。'},actor);
  assert.equal(added.ok,true);
  for(let i=0;i<6;i++)assert.equal(store.advanceKnowledgeSource(kb,added.source.id,actor).ok,true);
  const build=store.createIndexRebuild(kb,{trigger:'MANUAL_REBUILD',reason:'建立第一份合成知识索引'},actor);
  assert.equal(build.ok,true);const index=build.indexVersion.id;
  assert.equal(store.activateIndexVersion(kb,index,actor).ok,false);
  for(let i=0;i<4;i++)assert.equal(store.advanceIndexBuild(kb,index,actor).ok,true);
  assert.equal(store.recordIndexEvaluation(kb,index,{recallAt10:.88,citationPrecision:.97,refusalAccuracy:.99,unauthorizedLeakage:0,retrievalP95Ms:612},actor).ok,true);
  assert.equal(store.activateIndexVersion(kb,index,actor).ok,true);
  const assistant=store.createAssistant(tenant,{name:'样板年假助手',summary:'基于合成年假资料回答问题并提供引用。',knowledgeBaseId:kb},actor);
  assert.equal(assistant.ok,true);const version=assistant.version.id;
  assert.equal(store.publishAssistantVersion(version,actor).ok,false);
  assert.equal(store.saveAssistantDraft(version,{knowledgeBaseId:kb,indexVersionId:index,chatConfigurationId:'cfg-chat',embeddingConfigurationId:'cfg-embedding',rerankConfigurationId:'cfg-rerank',retrievalPolicy:'HYBRID_RRF_RERANK_V3',citationPolicy:'REQUIRED_V2',refusalPolicy:'CALIBRATED_V5'},actor).ok,true);
  assert.equal(store.runAssistantTest(version,'年假最多可以结转几天？',actor).ok,true);
  assert.equal(store.addAssistantTestToEvaluationSet(version,actor).ok,true);
  assert.equal(store.runAssistantEvaluation(version,actor).ok,true);
  assert.equal(store.requestAssistantRelease(version,actor).ok,true);
  assert.equal(store.publishAssistantVersion(version,actor).ok,true);
  const member=store.addMember(tenant,{loginName:'sample.member',password:'demo-only',passwordConfirmation:'demo-only',conversationFeatures:['assistantSelection','conversation','ownHistory','authorizedCitations'],assistantIds:[assistant.assistant.id]},actor);
  assert.equal(member.ok,true);
  assert.equal(store.loginQuery({tenantCode:'spruce-demo',username:'sample.member',password:'demo-only'}).ok,true);
  const fixtures=JSON.parse(readFileSync(new URL('../fixtures.json',import.meta.url),'utf8'));
  const conversation=store.createQueryConversation(fixtures,assistant.assistant.id);
  assert.equal(conversation.ok,true);assert.equal(conversation.conversation.assistantVersionId,version);assert.equal(conversation.conversation.indexVersionId,index);
  let run=store.startQueryRun(fixtures,conversation.conversation.id,'年假最多可以结转几天？');
  assert.equal(run.ok,true);
  for(let attempt=0;attempt<100&&run.run.status==='RECEIVING';attempt++)run=store.advanceQueryRun(fixtures,conversation.conversation.id,run.run.id,{chunkSize:64,expectedEpoch:store.getSession().epoch});
  assert.equal(run.run.status,'COMPLETED');
  assert.equal(createPrototypeStore(storage).queryProjection(fixtures,{conversationId:conversation.conversation.id}).conversationAllowed,true);
  assert.doesNotMatch(storage.dump(),/demo-only/);
});
