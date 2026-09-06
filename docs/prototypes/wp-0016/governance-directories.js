/** 目录只呈现已经投影的合成数据，详情标识进入 URL，导出不使用页面 HTML。 */
export function governanceDirectory(screen, snapshot, records, { escapeHtml: e, statusTag }) {
  const audit = screen === "audit-records";
  const policies = snapshot.accessGovernance.accessPolicies;
  const source = audit ? records : policies;
  const rows = source.map(item => {
    const version = audit ? null : item.versions.at(-1);
    const name = audit ? item.event : item.name;
    const scope = audit ? item.scope : snapshot.assistantDelivery.assistants.find(a => a.id === version.assistantId)?.name || "助手不可用";
    const status = audit ? item.outcome : version.status;
    return `<tr data-directory-row data-search-text="${e(`${name} ${scope}`)}" data-directory-state="${e(status)}" ${audit ? `data-audit-row="${e(item.id)}"` : ""}><td data-label="${audit ? "事件" : "策略"}"><strong>${e(name)}</strong></td><td data-label="范围">${e(scope)}</td><td data-label="状态">${audit ? e(status === "SUCCESS" ? "已完成" : "需处理") : statusTag(status)}</td><td data-label="操作"><button class="button secondary compact" type="button" ${audit ? `data-open-audit="${e(item.id)}"` : `data-open-access-policy="${e(item.id)}"`}>查看</button></td></tr>`;
  }).join("");
  const assistants = snapshot.assistantDelivery.assistants.filter(item => item.publishedVersionId);
  const members = snapshot.tenants[0]?.members.filter(item => item.role === "ordinaryMember" && item.status === "active") || [];
  return {
    title: audit ? "审计记录" : "访问策略目录",
    action: audit ? `<button class="button primary" type="button" data-export-audit${source.length ? "" : " disabled"}>导出受控记录</button>` : `<button class="button primary" type="button" data-open-access-create${assistants.length && members.length ? "" : " disabled"}>新建访问策略</button>`,
    body: `<div class="toolbar"><label class="field"><span>${audit ? "搜索审计记录" : "搜索访问策略"}</span><input class="input" type="search" data-directory-search placeholder="按名称或范围搜索"></label><label class="field compact-field"><span>状态</span><select class="select" data-directory-status><option value="">全部状态</option>${audit ? '<option value="FAILED">需处理</option><option value="SUCCESS">已完成</option>' : '<option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option>'}</select></label></div><p data-filter-summary class="form-feedback" role="status"></p>` +
      `<div class="table-shell"><table class="data-table"><thead><tr><th>${audit ? "事件" : "策略"}</th><th>范围</th><th>状态</th><th>操作</th></tr></thead><tbody>${rows || '<tr><td colspan="4">暂无记录。</td></tr>'}</tbody></table><div class="pagination" aria-label="目录分页"><span>共 ${source.length} 条记录</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div><p class="form-feedback" id="governance-directory-feedback" role="status"></p>` +
      (audit ? '<p class="field-hint">导出当前筛选结果，不含问题、回答、引用正文或凭据。</p>' :
      `${assistants.length && members.length ? "" : '<p class="inline-note">先发布助手并添加普通成员，再配置访问策略。</p>'}<dialog id="access-create-dialog" class="dialog-surface" aria-labelledby="access-create-title"><form id="access-create-form"><h2 id="access-create-title">新建访问策略</h2><label class="field"><span>策略名称</span><input class="input" name="name" minlength="2" maxlength="80" required></label><label class="field"><span>已发布助手</span><select class="select" name="assistantId">${assistants.map(item => `<option value="${e(item.id)}">${e(item.name)}</option>`).join("")}</select></label><fieldset class="assistant-scope"><legend>普通成员</legend>${members.map(member => `<label><input type="checkbox" name="memberAccountIds" value="${e(member.accountId)}">${e(snapshot.accounts.find(item => item.id === member.accountId)?.name || "不可用账户")}</label>`).join("")}</fieldset><p class="form-feedback" role="alert" id="access-create-feedback"></p><div class="form-actions"><button class="button secondary" type="button" data-close-dialog="access-create-dialog">取消</button><button class="button primary" type="submit">创建策略草稿</button></div></form></dialog>`),
  };
}
