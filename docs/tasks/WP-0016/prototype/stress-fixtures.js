/** 独立压力夹具：只在测试中生成，不载入或重置浏览器的业务状态。 */
export function createStressFixtures() {
  const rows=(count,prefix)=>Array.from({length:count},(_,index)=>({id:`stress-${prefix}-${index+1}`,name:`合成${prefix}${String(index+1).padStart(5,'0')}`,sequence:index+1}));
  return {documents:rows(1000,'文档'),members:rows(1000,'成员'),assistants:rows(200,'助手'),versions:rows(100,'版本'),records:rows(10000,'记录')};
}

/** 父测试可把返回快照交给实际渲染器；不访问浏览器存储，不修改传入状态或账户凭据。 */
export function createStressSnapshot(base) {
  const snapshot=structuredClone(base), data=createStressFixtures();
  const tenant=snapshot.tenants.find(item=>item.id===snapshot.query?.session?.tenantId)||snapshot.tenants[0];
  const kb=snapshot.knowledgeBases.find(item=>item.tenantId===tenant.id);
  const source=kb.sources[0];
  // 目录规模与单对象历史分别施压，避免把 100 份历史重复到全部文档而超出原型存储。
  kb.sources=data.documents.map(item=>({...source,id:item.id,name:item.name+'.txt',kind:'粘贴文本',stage:'READY',revision:'rev-0001',previousRevisions:[],processingRecords:[]}));
  kb.sources[0].revision='rev-0100';
  kb.sources[0].previousRevisions=data.versions.slice(0,-1).map(v=>({name:kb.sources[0].name,revision:`rev-${String(v.sequence).padStart(4,'0')}`,sizeMb:0.01}));
  kb.sources[0].processingRecords=data.records.map(item=>({sequence:item.sequence,stage:'READY',detail:`第 ${item.sequence} 次合成状态核对；没有调用后台。`}));
  kb.documents=1000;snapshot.selectedKnowledgeBaseId=kb.id;snapshot.selectedKnowledgeSourceId=kb.sources[0].id;
  const ordinary=tenant.members.find(item=>item.role==='ordinaryMember');
  const account=snapshot.accounts.find(item=>item.id===ordinary.accountId);
  const members=data.members.map(item=>({...ordinary,accountId:item.id,assistantIds:[]}));
  tenant.members=[...tenant.members.filter(item=>item.role!=='ordinaryMember'),...members];
  snapshot.accounts.push(...data.members.map(item=>({...account,id:item.id,name:item.name,loginName:`stress.member.${item.sequence}`})));
  const assistant=snapshot.assistantDelivery.assistants.find(item=>item.tenantId===tenant.id);
  const published=assistant.versions.find(item=>item.id===assistant.publishedVersionId)||assistant.versions[0];
  snapshot.assistantDelivery.assistants=data.assistants.map(item=>({...assistant,id:item.id,name:item.name,knowledgeBaseId:kb.id,publishedVersionId:`${item.id}-v1`,versions:[{...published,id:`${item.id}-v1`,number:1,status:'PUBLISHED'}]}));
  const first=snapshot.assistantDelivery.assistants[0];
  first.versions=data.versions.map(item=>({...published,id:`${first.id}-v${item.sequence}`,number:item.sequence,status:item.sequence===100?'PUBLISHED':'SUPERSEDED'}));
  first.publishedVersionId=first.versions.at(-1).id;
  snapshot.assistantDelivery.selectedVersionId=snapshot.assistantDelivery.assistants[0].publishedVersionId;
  snapshot.selectedMemberAccountId=members[0].accountId;
  return snapshot;
}
