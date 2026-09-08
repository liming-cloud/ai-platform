import { boundedDirectoryMarkup } from "./object-workspaces.js";
/** 对象导航沿用既有 URL 和选择上下文，不创建另一份页面注册表。 */
export function objectNavigation(screen) {
  const knowledge = ['knowledge-overview','knowledge-sources','source-add','source-detail','processing-quality','sync-result','index-versions','index-detail','index-rebuild'];
  const assistant = ['assistant-overview','assistant-draft','assistant-test','assistant-evaluation','assistant-release'];
  const items = knowledge.includes(screen) ? [['knowledge-overview','概览'],['knowledge-sources','文档'],['processing-quality','处理任务'],['index-versions','索引管理']]
    : assistant.includes(screen) ? [['assistant-overview','概览'],['assistant-draft','版本配置'],['assistant-test','测试'],['assistant-evaluation','质量检查'],['assistant-release','发布记录']] : [];
  const active=({'source-detail':'knowledge-sources','source-add':'knowledge-sources','sync-result':'processing-quality','index-detail':'index-versions','index-rebuild':'index-versions'})[screen]||screen;
  return items.length ? `<nav class="object-navigation" aria-label="对象工作区" data-od-id="object-navigation">${items.map(([target,label])=>`<button class="button secondary" type="button" data-nav-screen="${target}"${target===active?' aria-current="page"':''}>${label}</button>`).join('')}</nav>` : '';
}

/** 可丢弃工作区原型：概览与处理结果使用同一组织、知识库和来源快照，不另造成功事实。 */
export function workspaceJourney(screen, snapshot, { escapeHtml: e, statusTag, feedback = "" }) {
  const tenant = snapshot.tenants[0];
  const kb = snapshot.knowledgeBases.find(item => item.id === snapshot.selectedKnowledgeBaseId);
  const sources = kb?.sources || [];
  const ready = sources.filter(item => item.stage === "READY");
  const failed = sources.filter(item => item.stage === "FAILED");
  const button = (label, destination, extra = "") => `<button class="button secondary" type="button" data-nav-screen="${destination}" ${extra}>${label}</button>`;
  const facts = values => `<dl class="summary-strip">${values.map(([label, value]) => `<div class="summary-item"><dt>${label}</dt><dd>${e(value)}</dd></div>`).join("")}</dl>`;
  const sourceTable = boundedDirectoryMarkup(`<div class="table-shell"><table class="data-table"><thead><tr><th>资料</th><th>处理状态</th><th>处理说明</th><th>操作</th></tr></thead><tbody>${sources.map(source => `<tr data-processing-source="${e(source.id)}"><td data-label="资料"><strong>${e(source.name)}</strong></td><td data-label="处理状态">${statusTag(source.stage === "FAILED" ? "处理失败" : source.stage === "READY" ? "已就绪" : "处理中")}</td><td data-label="处理说明">${e(source.detail || source.coverage || "已完成处理")}</td><td data-label="操作">${button(source.stage === "FAILED" ? "查看失败" : "查看处理", "source-detail", `data-select-source="${e(source.id)}"`)}</td></tr>`).join("") || '<tr><td colspan="4">还没有资料，请先添加数据。</td></tr>'}</tbody></table><div class="pagination"></div></div>`, typeof location === "undefined" ? new URLSearchParams() : new URLSearchParams(location.search));
  const response = (title, action, body, summary = kb?.name || tenant?.organizationName || "当前组织") => ({ title, action, body: body + (screen==='knowledge-overview'&&kb?.activeIndex?`<div class="form-actions">${button('用此知识库创建助手','assistant-create')}</div>`:'') + `<p class="form-feedback" role="status">${e(feedback)}</p>`, summary });
  if (screen === "tenant-overview") {
    const pending = snapshot.knowledgeBases.flatMap(item => item.sources.map(source => ({ kb: item, source }))).find(item => item.source.stage !== "READY");
    return response("工作区概览", button(pending ? "进入阻断项" : "开始管理知识", pending ? "processing-quality" : "knowledge-directory", pending ? `data-select-kb="${e(pending.kb.id)}"` : ""),
      facts([["知识库", `${snapshot.knowledgeBases.length} / ${tenant?.quotas.knowledgeBases || 0}`], ["有效文档", snapshot.knowledgeBases.reduce((sum, item) => sum + item.documents, 0)], ["待处理来源", snapshot.knowledgeBases.flatMap(item => item.sources).filter(item => item.stage !== "READY").length], ["已发布助手", snapshot.assistantDelivery.assistants.filter(item => item.publishedVersionId).length]]) +
      `<section class="section-block"><div class="section-heading"><h2>${pending ? e(pending.source.name) : "从第一份资料开始"}</h2>${statusTag(pending ? "处理中" : "待添加")}</div><p>${pending ? e(pending.source.detail || "资料处理尚未完成") : "创建知识库、添加资料，再配置和发布助手。"}</p><div class="form-actions">${button("知识库", "knowledge-directory")}${button("助手目录", "assistants-directory")}${button("成员管理", "tenant-members")}</div></section>`, tenant?.organizationName);
  }
  if (!kb) return response("知识工作区", button("选择知识库", "knowledge-directory"), '<section class="state-panel" data-kind="empty"><h2>请先选择或创建知识库</h2><p>后续资料、处理结果和索引都将属于该知识库。</p></section>');
  if (screen === "knowledge-overview") return response("知识库概览", button("继续当前阻断任务", "knowledge-sources"),
    facts([["活动索引", kb.activeIndex || "尚未建立"], ["有效文档", kb.documents], ["来源", sources.length], ["待处理", sources.length - ready.length]]) +
    `<section class="section-block"><div class="section-heading"><h2>${failed.length ? `${failed.length} 份资料需要处理` : "知识库交付进度"}</h2>${statusTag(kb.status)}</div><div class="form-actions">${button("添加数据", "source-add")}${button("处理与质量", "processing-quality")}${button("索引版本", "index-versions")}${button("首份知识快速发布", "quick-publication")}</div></section>`);
  if (screen === "quick-publication") return response("首份知识快速发布", button("返回知识库", "knowledge-overview"),
    facts([["知识库", kb.name], ["已就绪来源", ready.length], ["待处理来源", sources.length - ready.length], ["活动索引", kb.activeIndex || "尚未建立"]]) +
    `<form id="quick-publication-form" class="section-block"><div class="section-heading"><h2>核对处理结果并创建候选索引</h2></div><div class="form-grid"><label class="field"><span>处理批次</span><input class="input" name="batchName" value="${e(kb.name)}资料批次" minlength="2" maxlength="80" required></label><label class="field"><span>质量结论</span><select class="select" name="decision"><option value="build">可以创建索引</option><option value="repair">返回修复来源</option></select></label></div><p>仅已就绪资料进入候选索引，评测通过后仍需单独确认激活。</p><p id="quick-publication-feedback" class="form-feedback" role="alert"></p><div class="form-actions">${button("取消", "knowledge-overview")}<button class="button primary" type="submit" data-primary-action>开始发布</button></div></form>` + sourceTable);
  const isQuality = screen === "processing-quality";
  return response(isQuality ? "处理与质量" : "同步运行结果", isQuality
    ? `<button class="button primary" type="button" data-retry-failed-sources${failed.length ? "" : " disabled"}>重试失败项</button>`
    : button("创建索引版本", "index-rebuild"),
    facts([["已就绪", ready.length], ["处理中", sources.length - ready.length - failed.length], ["失败", failed.length], ["活动索引", kb.activeIndex || "尚未建立"]]) + `<div class="result-workspace"><section class="result-metrics">${sourceTable}</section><aside class="next-step-panel"><h2>${failed.length ? "先处理失败资料" : "核对后建立候选索引"}</h2><p>新处理结果不会改变当前活动索引。</p>${button("查看索引版本", "index-versions")}</aside></div>` +
    `<div class="form-actions">${button("添加数据", "source-add")}${button(isQuality ? "查看同步结果" : "查看处理与质量", isQuality ? "sync-result" : "processing-quality")}</div>`);
}
