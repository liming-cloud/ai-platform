import { CONVERSATION_FEATURES, DEFAULT_TENANT_ID } from './prototype-state.js';

const labels = { assistantSelection:'选择助手', conversation:'发起和继续对话', ownHistory:'本人历史', authorizedCitations:'查看引用' };
const e = value => String(value ?? '').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');

function assistantPicker(assistants, selected, memberId = '') {
  return `<div data-assistant-picker data-member-id="${e(memberId)}" data-options="${e(JSON.stringify(assistants.map(({id,name})=>({id,name}))))}" data-selected="${e(JSON.stringify(selected))}"><label class="field"><span>搜索已发布助手</span><input class="input" type="search" data-picker-search placeholder="输入助手名称"></label><div class="assistant-choice-list" data-picker-results></div><p data-picker-summary role="status"></p><div data-picker-values></div></div>`;
}

/** 候选最多渲染 20 项；已选集合独立于检索结果，避免搜索后覆盖原授权。 */
export function bindAssistantPickers(root, onMemberChange) {
  root.querySelectorAll('[data-assistant-picker]').forEach(picker=>{
    const options=JSON.parse(picker.dataset.options), selected=new Set(JSON.parse(picker.dataset.selected));
    const paint=()=>{
      const query=picker.querySelector('[data-picker-search]').value.trim();
      const matches=options.filter(item=>item.name.includes(query));
      picker.querySelector('[data-picker-results]').innerHTML=matches.slice(0,20).map(item=>`<label><input type="checkbox" data-choice-id="${e(item.id)}" data-od-id="assistant-choice-${e(picker.dataset.memberId||'create')}-${e(item.id)}"${selected.has(item.id)?' checked':''}>${e(item.name)}</label>`).join('');
      picker.querySelector('[data-picker-summary]').textContent=`匹配 ${matches.length} 个，已选 ${selected.size} 个${matches.length>20?'；请输入更完整名称缩小范围':''}`;
      picker.querySelector('[data-picker-values]').innerHTML=picker.dataset.memberId?'':[...selected].map(id=>`<input type="hidden" name="assistantIds" value="${e(id)}">`).join('');
    };
    picker.querySelector('[data-picker-search]').addEventListener('input',paint);
    picker.addEventListener('change',event=>{
      const id=event.target.dataset.choiceId;if(!id)return;
      event.target.checked?selected.add(id):selected.delete(id);
      if(picker.dataset.memberId)onMemberChange(picker.dataset.memberId,[...selected]);
      paint();
      picker.querySelector(`[data-choice-id="${CSS.escape(id)}"]`)?.focus();
    });
    paint();
  });
}

/** 成员对象来自当前租户投影；页面拆分复用原授权草稿，不引入新的权限规则。 */
export function memberWorkspace(screen, snapshot = {tenants:[],accounts:[]}) {
  const tenant = snapshot.tenants.find(item => item.id === (snapshot.query?.session?.tenantId || DEFAULT_TENANT_ID));
  const member = tenant?.members.find(item => item.accountId === snapshot.selectedMemberAccountId);
  const account = snapshot.accounts.find(item => item.id === member?.accountId);
  if (!member || !account) return '<section class="state-panel"><h2>请选择成员</h2><button class="button secondary" data-nav-screen="tenant-members">返回成员目录</button></section>';
  const nav = `<nav class="object-navigation" aria-label="成员工作区">${[['tenant-member-account','账号信息'],['tenant-member-features','对话功能'],['tenant-member-assistants','可用助手']].map(([target,label]) => `<button class="button secondary" data-nav-screen="${target}"${screen===target?' aria-current="page"':''}>${label}</button>`).join('')}</nav>`;
  if (member.role !== 'ordinaryMember') return nav + '<section class="section-block"><h2>管理员账号只读</h2><p>管理员由平台维护。</p></section>';
  const save = `<div class="form-actions"><button class="button secondary" data-business-action="cancel-member-features" data-member-account="${e(member.accountId)}">取消修改</button><button class="button primary" data-primary-action data-business-action="${screen==='tenant-member-assistants'?'save-member-assistants':'save-member-features'}" data-member-account="${e(member.accountId)}">${screen==='tenant-member-assistants'?'保存可用助手':'保存对话功能'}</button></div><p id="member-feature-feedback" role="status"></p>`;
  let body = `<dl class="description-list"><dt>成员</dt><dd>${e(account.name)}</dd><dt>登录名</dt><dd>${e(account.loginName)}</dd><dt>状态</dt><dd>${member.status==='active'?'可用':'已停用'}</dd></dl><p>密码不显示。账号停用和密码重置可返回成员目录操作。</p>`;
  if(screen==='tenant-member-features') body = `<div class="feature-stack">${CONVERSATION_FEATURES.map(feature=>`<label class="feature-control"><input type="checkbox" data-member-feature="${feature}" data-member-account="${e(member.accountId)}"${member.conversationFeatures[feature]?' checked':''}${tenant.availableFeatures[feature]?'':' disabled'}><span>${labels[feature]}${tenant.availableFeatures[feature]?'':' · 租户未开放'}</span></label>`).join('')}</div>${save}`;
  if(screen==='tenant-member-assistants') {
    const assistants=(snapshot.assistantDelivery?.assistants||[]).filter(item=>item.tenantId===tenant.id&&item.publishedVersionId);
    body=assistantPicker(assistants,member.assistantIds,member.accountId)+save;
  }
  return nav + `<section class="section-block" data-od-id="member-workspace"><h2>${e(account.name)}</h2>${body}${screen==='tenant-member-account'?'<div class="form-actions"><button class="button primary" data-primary-action data-nav-screen="tenant-members">返回成员目录</button></div>':''}</section>`;
}

/** 新成员表单复用原子开户提交；密码字段仅存在当前表单，不写入草稿。 */
export function memberCreateWorkspace(snapshot = {}) {
  const tenantId=snapshot.query?.session?.tenantId;
  const assistants=(snapshot.assistantDelivery?.assistants||[]).filter(item=>item.tenantId===tenantId&&item.publishedVersionId);
  return `<ol class="process-steps member-create-steps" aria-label="添加成员步骤"><li>1 · 成员信息</li><li>2 · 使用权限</li><li>3 · 密码并创建</li></ol><form id="add-member-form" class="section-block" data-member-wizard novalidate>
    <section data-member-step="1"><h2>成员信息</h2><label class="field"><span id="member-login-label">登录名</span><input class="input" name="loginName" required pattern="[a-z](?:[a-z0-9._]|-){2,63}" maxlength="64" aria-labelledby="member-login-label" aria-describedby="member-login-help"><small id="member-login-help">3–64 位小写字母、数字、点、下划线或连字符。</small></label></section>
    <section data-member-step="2" hidden><h2>使用权限</h2><fieldset class="assistant-scope"><legend>对话功能</legend>${CONVERSATION_FEATURES.map(feature=>`<label><input type="checkbox" name="conversationFeatures" value="${feature}"${['assistantSelection','conversation'].includes(feature)?' checked':''}>${labels[feature]}</label>`).join('')}</fieldset>${assistantPicker(assistants,[])}</section>
    <section data-member-step="3" hidden><h2>密码并创建</h2><label class="field"><span>初始密码</span><input class="input" type="password" name="password" required autocomplete="new-password" aria-describedby="member-password-help"></label><label class="field"><span>再次输入密码</span><input class="input" type="password" name="passwordConfirmation" required autocomplete="new-password" aria-describedby="member-password-help"></label><p id="member-password-help">公开合成演示可填 demo-only 或 Initial-only-2026!。刷新后请重新输入，不使用真实密码。</p></section>
    <p id="add-member-feedback" role="alert"></p><div class="form-actions"><button class="button secondary" type="button" data-cancel-member-wizard>取消</button><button class="button secondary" type="button" data-member-back hidden>上一步</button><button class="button primary" data-primary-action type="submit">下一步</button></div></form>`;
}
