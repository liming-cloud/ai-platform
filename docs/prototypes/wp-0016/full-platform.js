import { renderConsoleNavigation, renderAccountMenu } from "./navigation.js";
import { memberWorkspace, memberCreateWorkspace } from "./member-workspace.js";
import {
  CONVERSATION_FEATURES,
  DEFAULT_QUOTAS,
  DEFAULT_TENANT_ID,
  TENANT_QUOTA_FIELDS,
  TENANT_SECURITY_FIELDS,
  DEMO_LOGIN_SCENARIOS,
} from "./prototype-state.js";

/** 显示全部公开夹具，不展示或推断任何账户当前选择的密码。 */
export function renderDemoLoginHelp() {
  return `<details class="demo-login-help"><summary>演示密码说明</summary><p>仅接受以下公开演示密码：<br />${Object.values(DEMO_LOGIN_SCENARIOS).map(escapeHtml).join("<br />")}</p><small>请勿输入真实密码。未修改过的内置账户使用 demo-only；创建或重置后使用刚设置的演示密码，并非上面任意一个都能登录。不会显示账户当前密码。检测到旧版数据时，登录页提供保留数据的修复入口。</small></details>`;
}

/** 两种治理入口复用重置表单，授权由各自状态方法重新检查。 */
export function renderPasswordResetDialog() {
  return '<dialog id="reset-account-password-dialog" class="dialog-surface account-dialog" aria-labelledby="reset-account-password-title"><form id="reset-account-password-form" novalidate><div class="dialog-heading"><span class="section-kicker">受控凭据重置</span><h2 id="reset-account-password-title">重置密码</h2><p id="reset-account-target"></p></div>' + renderDemoLoginHelp() + '<div class="wizard-field-grid account-password-grid"><label class="field" for="reset-account-password"><span>新密码</span><input id="reset-account-password" class="input" name="password" type="password" required maxlength="1024" autocomplete="new-password" /></label><label class="field" for="reset-account-password-confirmation"><span>再次输入</span><input id="reset-account-password-confirmation" class="input" name="passwordConfirmation" type="password" required maxlength="1024" autocomplete="new-password" /></label></div><label class="consent-control"><input name="revokeSessions" type="checkbox" required /><span><strong>撤销全部会话</strong><small>平台与租户范围的既有会话同时失效。</small></span></label><p id="reset-account-password-feedback" class="form-feedback" role="alert" aria-live="assertive"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-cancel-reset-account>取消</button><button class="button primary" type="submit">重置密码</button></div></form></dialog>';
}

/** 演示数据升级与产品登录分开，修复必须明确确认且不能清空原来的业务快照。 */
export function renderDemoLoginRepair(required, feedback = "") {
  if (!required) return feedback ? `<p class="inline-note" role="status">${escapeHtml(feedback)}</p>` : "";
  return `<section class="inline-note warning" aria-label="旧版演示登录配置"><strong>检测到旧版演示数据</strong><p>缺少登录配置，按初始密码也无法进入。可保留数据修复后重试。</p><button class="button secondary compact" type="button" data-open-demo-login-repair>修复演示登录</button></section><dialog id="demo-login-repair-dialog" class="dialog-surface" aria-labelledby="demo-login-repair-title"><h2 id="demo-login-repair-title">修复旧版演示登录</h2><p>仅补齐未修改过的内置演示账户，修复后使用公开密码 demo-only。</p><p>不清空租户、知识库、会话和草稿；不覆盖已重置的密码，不恢复停用账户，不改变角色和权限。</p><p>修复后仍需手动登录。这是本机原型修复，不是真实账户密码恢复。</p><p class="form-feedback" role="alert" data-demo-login-repair-error></p><div class="dialog-actions"><button class="button secondary" type="button" data-cancel-demo-login-repair autofocus>取消</button><button class="button primary" type="button" data-confirm-demo-login-repair>确认修复</button></div></dialog>`;
}

/** 登录入口只是导航，不根据入口名称授予平台、租户或问答权限。 */
export function renderLoginEntries(screen) {
  return `<nav class="login-entry-links" aria-label="其他登录入口">${[['console-login', '平台管理登录'], ['tenant-login', '租户管理员登录'], ['login', '普通用户问答登录']].filter(([target]) => target !== screen).map(([target, title]) => `<a href="?screen=${target}&state=success">${title}</a>`).join('')}</nav>`;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function statusTag(value, tone = "success") {
  return `<span class="status-tag" data-tone="${tone}">${escapeHtml(value)}</span>`;
}

function primaryAction(page, fixture, state, demoState) {
  const disabled = state === "disabled";
  const businessBehavior = {
    "platform-tenant-create": ' type="submit" form="tenant-create-form" data-business-action="create-tenant"',
    "platform-tenant-features": ' type="button" data-business-action="save-tenant-features"',
    "platform-accounts": ' type="button" data-business-action="open-create-account"',
    "tenant-members": ' type="button" data-business-action="open-add-member"',
    "platform-health": ' type="button" data-business-action="recheck-health"',
    "backup-recovery": ' type="button" data-business-action="advance-recovery"',
    "usage-quotas": ' type="button" data-refresh-tenant-usage',
  }[page.screen];
  if (businessBehavior) {
    const recoveryBlocked =
      page.screen === "backup-recovery" &&
      ["idle", "reported", "failed"].includes(demoState?.recovery?.stage || "idle");
    return `<button class="button primary" data-primary-action data-od-id="primary-${escapeHtml(page.screen)}" data-control-status="implemented"${businessBehavior}${disabled || recoveryBlocked ? " disabled" : ""}>${escapeHtml(page.primaryAction)}</button>`;
  }
  const navigation = fixture.nextScreen
    ? ` data-nav-screen="${escapeHtml(fixture.nextScreen)}" data-control-status="navigation"`
    : ' data-open-page-dialog="true" data-control-status="unimplemented"';
  const behavior = disabled
    ? ` disabled aria-describedby="primary-disabled-${escapeHtml(page.screen)}"`
    : navigation;
  return [
    `<button class="button primary" type="button" data-primary-action data-od-id="primary-${escapeHtml(page.screen)}"${behavior}>${escapeHtml(page.primaryAction)}</button>`,
    disabled
      ? `<p class="field-hint primary-disabled-reason" id="primary-disabled-${escapeHtml(page.screen)}">${escapeHtml(fixture.disabled || `${fixture.entity}尚未满足执行“${page.primaryAction}”的前置条件。`)}</p>`
      : "",
  ].join("");
}

function statePanel(page, fixture, state) {
  const entity = fixture.entity || fixture.title;
  const copy = {
    loading: [`${fixture.title}正在载入`, `${entity}的最新确认状态和可执行操作正在准备。`],
    empty: [`${fixture.title}暂无记录`, `当前没有可处理的${entity}。可以从“${page.primaryAction}”开始。`],
    error: [`暂时无法载入${fixture.title}`, "已保存的内容不受影响。请稍后重试。"],
    denied: ["当前会话无权访问", "请返回可用工作区，或联系管理员。"],
  }[state];
  const tone = state === "error" || state === "denied" ? state : "info";
  return [
    `<section class="state-panel inventory-state" data-kind="${tone}" data-od-id="state-${state}">`,
    state === "loading"
      ? '<div class="skeleton-stack" aria-label="正在加载"><span class="skeleton-line"></span><span class="skeleton-line"></span><span class="skeleton-line"></span></div>'
      : `<span class="section-kicker">${state === "empty" ? "暂无内容" : state === "error" ? "载入失败" : "访问受限"}</span><h2>${escapeHtml(copy[0])}</h2><p>${escapeHtml(copy[1])}</p>`,
    state === "empty"
      ? page.screen === "platform-accounts" ? primaryAction(page, fixture, "success") : `<button class="button secondary" type="button"${fixture.nextScreen ? ` data-nav-screen="${escapeHtml(fixture.nextScreen)}"` : ' data-open-page-dialog="true"'}>${escapeHtml(page.primaryAction)}</button>`
      : state === "error"
        ? `<button class="button secondary" type="button" data-nav-screen="${escapeHtml(page.screen)}">重新加载</button>`
        : state === "denied"
          ? `<button class="button secondary" type="button" data-nav-screen="${page.screen.startsWith("device-") ? "device-start" : "console-login"}">返回已授权入口</button>`
          : "",
    "</section>",
  ].join("");
}

function summaryFacts(fixture) {
  return `<dl class="summary-strip domain-summary" data-fact-set="summary">${(fixture.facts || [])
    .slice(0, 4)
    .map(
      ([label, value], index) =>
        `<div class="summary-item" data-fact-index="${index + 1}"><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`,
    )
    .join("")}</dl>`;
}

function factList(fixture, kind = "detail") {
  return `<dl class="description-list" data-fact-set="${kind}">${(fixture.facts || [])
    .map(
      ([label, value], index) =>
        `<div class="description-row" data-fact-index="${index + 1}"><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`,
    )
    .join("")}</dl>`;
}

function renderRows(page, fixture) {
  const columns = fixture.columns || ["对象", "状态", "最近变化"];
  const rows = fixture.rows || [];
  return [
    '<div class="table-shell" data-od-id="result-table">',
    '<table class="data-table"><thead><tr>',
    columns.map((column) => `<th>${escapeHtml(column)}</th>`).join(""),
    '<th class="action-column">操作</th></tr></thead><tbody>',
    rows
      .map(
        (row, rowIndex) =>
          `<tr data-od-id="${escapeHtml(page.screen)}-row-${rowIndex + 1}" data-search-text="${escapeHtml(row.join(" "))}" data-directory-state="${row.some(value => /成功|完成|正常/.test(value)) ? "READY" : "ATTENTION"}">${row
            .map(
              (cell, index) =>
                `<td data-label="${escapeHtml(columns[index])}">${index === 0 ? `<strong>${escapeHtml(cell)}</strong>` : escapeHtml(cell)}</td>`,
            )
            .join("")}<td data-label="操作">${fixture.nextScreen ? `<button class="button secondary compact" type="button" data-nav-screen="${escapeHtml(page.screen === "platform-overview" ? ["ingestion-queue", "model-health", "backup-recovery"][rowIndex] || fixture.nextScreen : fixture.nextScreen)}">查看</button>` : '<span class="muted">汇总记录</span>'}</td></tr>`,
      )
      .join(""),
    "</tbody></table>",
    page.pagination
      ? `<div class="pagination" aria-label="${escapeHtml(fixture.title)}分页"><span>第 1 页，共 ${rows.length} 条合成记录</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div>`
      : "",
    "</div>",
  ].join("");
}

function renderList(page, fixture) {
  return [
    summaryFacts(fixture),
    `<div class="toolbar"><label class="field"><span>搜索${escapeHtml(fixture.entity)}</span><input class="input" type="search" data-directory-search placeholder="输入名称或状态" /></label><label class="field compact-field"><span>状态</span><select class="select" data-directory-status><option value="">全部状态</option><option value="ATTENTION">需处理</option><option value="READY">已完成</option></select></label></div><p data-filter-summary class="form-feedback" role="status"></p>`,
    renderRows(page, fixture),
  ].join("");
}

function renderSteps(fixture) {
  const currentStep = Math.max(
    0,
    Math.min(Number(fixture.currentStep ?? 1), (fixture.steps || []).length - 1),
  );
  return `<ol class="process-steps" aria-label="任务步骤">${(fixture.steps || [])
    .map(
      (step, index) =>
        `<li data-step-state="${index < currentStep ? "complete" : index === currentStep ? "current" : "upcoming"}"><span>${index + 1}</span><strong>${escapeHtml(step)}</strong></li>`,
    )
    .join("")}</ol>`;
}

function renderField(page, field, index) {
  const id = `${page.screen}-field-${index + 1}`;
  const helpId = field.help ? `${id}-help` : "";
  const describedBy = helpId ? ` aria-describedby="${helpId}"` : "";
  let control;
  if (field.type === "select") {
    control = `<select class="select" id="${id}"${describedBy}>${(field.options || [])
      .map(
        (option) =>
          `<option${option === field.value ? " selected" : ""}>${escapeHtml(option)}</option>`,
      )
      .join("")}</select>`;
  } else if (field.type === "textarea") {
    control = `<textarea class="textarea" id="${id}" rows="3"${describedBy}>${escapeHtml(field.value)}</textarea>`;
  } else {
    control = `<input class="input" id="${id}" type="${field.type === "search" ? "search" : "text"}" value="${escapeHtml(field.value)}"${describedBy} />`;
  }
  return `<label class="field wizard-field" for="${id}"><span>${escapeHtml(field.label)}</span>${control}${field.help ? `<small class="field-hint" id="${helpId}">${escapeHtml(field.help)}</small>` : ""}</label>`;
}

function renderWizard(page, fixture) {
  return [
    renderSteps(fixture),
    '<div class="wizard-workspace">',
    '<section class="section-block wizard-inputs">',
    `<div class="section-heading"><div><span class="section-kicker">当前步骤</span><h2>${escapeHtml((fixture.steps || [fixture.entity])[Number(fixture.currentStep ?? 1)] || fixture.entity)}</h2></div>${statusTag(fixture.status, "warning")}</div>`,
    `<div class="wizard-field-grid">${fixture.fields.map((field, index) => renderField(page, field, index)).join("")}</div>`,
    "</section>",
    '<aside class="section-block wizard-review"><span class="section-kicker">提交前核对</span>',
    factList(fixture, "wizard-review"),
    `<p class="inline-note">${escapeHtml(fixture.notice)}</p>`,
    "</aside></div>",
  ].join("");
}

function renderDetail(fixture) {
  return [
    '<div class="detail-workspace"><section class="section-block detail-facts">',
    `<div class="section-heading"><div><span class="section-kicker">当前对象</span><h2>${escapeHtml(fixture.entity)}</h2></div>${statusTag(fixture.status)}</div>`,
    factList(fixture),
    '</section><aside class="next-step-panel"><span class="section-kicker">需要关注</span>',
    `<h2>${escapeHtml(fixture.notice)}</h2>`,
    "</aside></div>",
  ].join("");
}

function renderResult(fixture) {
  return [
    `<section class="result-decision"><span class="section-kicker">运行结论</span><div class="section-heading"><h2>${escapeHtml(fixture.decision || fixture.status)}</h2>${statusTag(fixture.status)}</div><p>${escapeHtml(fixture.notice)}</p></section>`,
    '<div class="result-workspace"><section class="section-block result-metrics"><h2>关键结果</h2>',
    summaryFacts(fixture),
    '</section><aside class="section-block result-stages"><h2>运行阶段</h2>',
    renderSteps(fixture),
    "</aside></div>",
  ].join("");
}

function renderTimeline(fixture) {
  return [
    '<div class="timeline-workspace"><section class="section-block"><div class="section-heading"><h2>事件顺序</h2>',
    statusTag(fixture.status),
    "</div>",
    `<ol class="timeline event-timeline">${(fixture.steps || [])
      .map(
        (step, index) =>
          `<li><span><strong>事件 ${index + 1}</strong><small>${escapeHtml(step)}</small></span><time>${18 + index}:0${index}</time></li>`,
      )
      .join("")}</ol>`,
    '</section><aside class="section-block"><h2>范围与恢复</h2>',
    factList(fixture, "timeline"),
    `<p class="inline-note">${escapeHtml(fixture.notice)}</p>`,
    "</aside></div>",
  ].join("");
}

function renderRisk(fixture) {
  const risk = fixture.risk;
  const items = [
    ["操作对象", risk.object],
    ["业务影响", risk.impact],
    ["恢复方式", risk.recovery],
    ["审计责任", risk.audit],
  ];
  return [
    `<section class="risk-review"><span class="section-kicker">高风险操作</span><div class="section-heading"><h2>${escapeHtml(fixture.entity)}</h2>${statusTag(fixture.status, "warning")}</div><p>${escapeHtml(fixture.notice)}</p></section>`,
    `<div class="risk-grid">${items
      .map(
        ([label, value], index) =>
          `<section data-od-id="risk-fact-${index + 1}"><h2>${label}</h2><p>${escapeHtml(value)}</p></section>`,
      )
      .join("")}</div>`,
    '<section class="section-block risk-status"><h2>执行前状态</h2>',
    factList(fixture, "risk"),
    "</section>",
  ].join("");
}

const FEATURE_LABELS = Object.freeze({
  knowledgeManagement: "知识管理",
  modelGovernance: "模型治理",
  assistantDelivery: "助手交付",
  evaluationGovernance: "评测治理",
  assistantSelection: "选择已授权助手",
  conversation: "发起与继续对话",
  ownHistory: "查看本人历史",
  authorizedCitations: "查看有权引用",
});

function snapshotTenant(demoState) {
  return (
    demoState?.tenants?.find(
      (tenant) => tenant.id === (demoState.selectedTenantId || DEFAULT_TENANT_ID),
    ) ||
    demoState?.tenants?.find((tenant) => tenant.id === DEFAULT_TENANT_ID) ||
    demoState?.tenants?.[0]
  );
}

function accountName(demoState, accountId) {
  return (
    demoState?.accounts?.find((account) => account.id === accountId)?.name ||
    "合成账户"
  );
}

function accountScope(demoState, account) {
  if (account.platformAdministrator) {
    return "平台管理员";
  }
  for (const tenant of demoState?.tenants || []) {
    const member = tenant.members?.find((item) => item.accountId === account.id);
    if (member) {
      return member.role === "tenantAdministrator"
        ? `租户管理员 · ${tenant.organizationName}`
        : `普通成员 · ${tenant.organizationName}`;
    }
  }
  return "未分配";
}

function renderPlatformAccounts(demoState, tenantAdministrators = false) {
  const tenant = snapshotTenant(demoState);
  const actorAccountId = demoState?.query?.session?.accountId;
  const accounts = (demoState?.accounts || []).filter(account => tenantAdministrators
    ? tenant?.members.some(member => member.accountId === account.id && member.role === 'tenantAdministrator')
    : account.platformAdministrator);
  const rows = accounts.map(account => {
    const suspended = account.status === 'suspended';
    const self = account.id === actorAccountId;
    return `<tr data-account-row="${escapeHtml(account.id)}" data-search-text="${escapeHtml(account.loginName)}" data-account-status="${suspended ? 'suspended' : 'available'}" tabindex="-1"><td data-label="登录名"><span class="primary-cell"><strong>${escapeHtml(account.loginName)}</strong><small>${escapeHtml(account.name)}</small></span></td><td data-label="账号用途">${tenantAdministrators ? '当前租户管理员' : '平台管理员'}</td><td data-label="状态">${statusTag(suspended ? '已停用' : '可用', suspended ? 'warning' : 'success')}</td><td data-label="最近安全变更">${escapeHtml(account.authenticationChangedAt || '初始凭据已设置')}</td><td data-label="操作"><div class="table-actions account-row-actions"><button class="button secondary compact" type="button" data-reset-account="${escapeHtml(account.id)}"${self ? ' disabled aria-label="当前账户不能由此重置密码"' : ''}>重置密码</button><details class="account-actions-menu" data-account-menu="${escapeHtml(account.id)}"><summary class="button subtle compact">更多</summary><div class="account-actions-popover"><button class="button subtle compact" type="button" data-set-account-status="${escapeHtml(account.id)}" data-next-status="${suspended ? 'available' : 'suspended'}"${self ? ' disabled' : ''}>${suspended ? '恢复账户' : '停用账户'}</button></div></details></div></td></tr>`;
  }).join('');
  return [
    `<dl class="summary-strip domain-summary account-summary"><div class="summary-item"><dt>${tenantAdministrators ? '租户管理员' : '平台账户'}</dt><dd>${accounts.length}</dd></div><div class="summary-item"><dt>可用账户</dt><dd>${accounts.filter(account => account.status === 'available').length}</dd></div></dl>`,
    '<div class="toolbar"><label class="field"><span>搜索账号</span><input class="input" type="search" data-directory-search="platform-accounts" placeholder="按登录名搜索" /></label><label class="field compact-field"><span>状态</span><select class="select" data-account-status-filter><option value="">全部状态</option><option value="available">可用</option><option value="suspended">已停用</option></select></label></div>',
    `<div class="table-shell account-table-shell"><table class="data-table"><thead><tr><th>登录名</th><th>账号用途</th><th>状态</th><th>最近安全变更</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table><div class="pagination" aria-label="账户目录分页"><span>第 1 页，共 ${accounts.length} 个账户</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>`,
    `<p id="account-operation-feedback" class="form-feedback" role="status" aria-live="polite">${escapeHtml(demoState?.accountFeedback || "")}</p>`,
    '<dialog id="create-account-dialog" class="dialog-surface account-dialog" aria-labelledby="create-account-title"><form id="create-account-form" novalidate><div class="dialog-heading"><span class="section-kicker">平台管理员账户</span><h2 id="create-account-title">创建平台账户</h2><p>设置一次性初始密码；提交后不再显示。</p></div>' + renderDemoLoginHelp() + '<label class="field" for="create-account-login"><span>登录名</span><input id="create-account-login" class="input mono" name="loginName" required pattern="[a-z](?:[a-z0-9._]|-){2,63}" maxlength="64" autocomplete="off" placeholder="例如：zhou.demo" aria-describedby="create-account-login-help" /><small id="create-account-login-help" class="field-hint">3–64 位小写字母、数字、点、下划线或连字符。</small></label><div class="wizard-field-grid account-password-grid"><label class="field" for="create-account-password"><span>初始密码</span><input id="create-account-password" class="input" name="password" type="password" required maxlength="1024" autocomplete="new-password" /></label><label class="field" for="create-account-password-confirmation"><span>再次输入</span><input id="create-account-password-confirmation" class="input" name="passwordConfirmation" type="password" required maxlength="1024" autocomplete="new-password" /></label></div><p class="inline-note compact-note">请通过组织批准的安全渠道单独告知本人；页面、存储和审计均不保留明文。</p><p id="create-account-feedback" class="form-feedback" role="alert" aria-live="assertive"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-cancel-create-account>取消</button><button class="button primary" type="submit">创建账户</button></div></form></dialog>',
    renderPasswordResetDialog(),
    '<dialog id="account-governance-dialog" class="dialog-surface account-dialog" aria-labelledby="account-governance-title"><form id="account-governance-form"><div class="dialog-heading"><span class="section-kicker">账户治理</span><h2 id="account-governance-title">确认账户操作</h2><p id="account-governance-description"></p></div><dl class="dialog-impact"><div><dt>影响</dt><dd id="account-governance-impact"></dd></div><div><dt>恢复</dt><dd id="account-governance-recovery"></dd></div><div><dt>审计</dt><dd>记录操作者、目标账户、操作、结果与非秘密版本。</dd></div></dl><p id="account-governance-feedback" class="form-feedback" role="alert" aria-live="assertive"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-cancel-account-governance>取消</button><button class="button primary" type="submit" id="account-governance-confirm">确认</button></div></form></dialog>',
  ].join("");
}

function renderPlatformTenants(demoState) {
  const rows = (demoState?.tenants || [])
    .map(
      (tenant) =>
        `<tr data-directory-state="${escapeHtml(tenant.status)}" data-search-text="${escapeHtml(`${tenant.organizationName} ${tenant.code} ${tenant.region}`)}"><td data-label="租户"><span class="primary-cell"><strong>${escapeHtml(tenant.organizationName)}</strong><span class="mono">${escapeHtml(tenant.code)}</span></span></td><td data-label="驻留区域">${escapeHtml(tenant.region)}</td><td data-label="状态">${statusTag(tenant.status === "active" ? "活动" : "已停用")}</td><td data-label="租户管理员">已分配</td><td data-label="操作"><button class="button secondary compact" type="button" data-select-tenant="${escapeHtml(tenant.id)}" data-nav-screen="platform-tenant-detail" data-control-status="navigation">查看详情</button></td></tr>`,
    )
    .join("");
  return [
    '<div class="compact-summary"><span><strong>租户目录</strong> · 合成生命周期数据</span><span>安全边界异常 0</span></div>',
    '<div class="toolbar"><label class="field"><span>搜索租户</span><input class="input" type="search" data-directory-search="platform-tenants" placeholder="按组织名称或租户编码搜索" /></label><label class="field compact-field"><span>状态</span><select class="select" data-directory-status><option value="">全部状态</option><option value="active">活动</option><option value="suspended">已停用</option></select></label></div><p data-filter-summary class="form-feedback" role="status"></p>',
    `<div class="table-shell"><table class="data-table"><thead><tr><th>租户</th><th>驻留区域</th><th>状态</th><th>租户管理员</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table><div class="pagination" aria-label="租户目录分页"><span>第 1 页，共 ${demoState?.tenants?.length || 0} 个租户</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>`,
  ].join("");
}

function renderTenantCreate(demoState) {
  const setup = demoState?.tenantSetup || { step: 1, organizationName: "", tenantCode: "", quotas: DEFAULT_QUOTAS };
  const step = Math.max(1, Math.min(3, Number(setup.step) || 1));
  const steps = ["租户信息", "配额设置", "管理员账号"];
  const stepper = `<ol class="process-steps tenant-setup-progress" aria-label="建立租户步骤">${steps.map((label, i) => `<li data-step-state="${i + 1 < step ? 'complete' : i + 1 === step ? 'current' : 'upcoming'}"><span>${i + 1}</span><strong>${label}</strong></li>`).join('')}</ol>`;
  let fields;
  if (step === 1) {
    fields = `<div class="wizard-field-grid"><label class="field"><span>组织名称</span><input id="tenant-organization-name" aria-label="组织名称" class="input" name="organizationName" value="${escapeHtml(setup.organizationName)}" required minlength="2" maxlength="40" autocomplete="organization" placeholder="例如：青岚科技" data-tenant-draft /><small class="field-hint">2–40 个字符。</small></label><label class="field"><span>租户编码</span><input id="tenant-code" aria-label="租户编码" class="input mono" name="tenantCode" value="${escapeHtml(setup.tenantCode)}" required pattern="[a-z](?:[a-z0-9]|-){2,19}" autocomplete="off" placeholder="qinglan-demo" data-tenant-draft /><small class="field-hint">3–20 位小写字母、数字或连字符；创建后不能修改。</small></label></div>`;
  } else if (step === 2) {
    fields = `<div class="tenant-quota-grid">${TENANT_QUOTA_FIELDS.map(({key, label, unit, integer}) => `<label class="field quota-field"><span>${escapeHtml(label)} <small>${unit}</small></span><input id="tenant-setup-${key}" class="input mono" type="number" name="${key}" value="${escapeHtml(setup.quotas?.[key] ?? DEFAULT_QUOTAS[key])}" min="${integer ? 1 : 0.01}" step="${integer ? 1 : 0.01}" required data-tenant-quota /><small class="field-hint">大于 0 的${integer ? '整数' : '数值'}。</small></label>`).join('')}</div><p class="tenant-safety-note">租户隔离、权限校验和凭据不回显始终开启。</p>`;
  } else {
    fields = `<div class="tenant-confirmation-summary"><span><strong>${escapeHtml(setup.organizationName)}</strong><small>${escapeHtml(setup.tenantCode)}</small></span><span><strong>${escapeHtml(setup.quotas?.knowledgeBases)} 个知识库</strong><small>每库 ${escapeHtml(setup.quotas?.rawContentGbPerKnowledgeBase)} GB</small></span></div><div class="wizard-field-grid"><label class="field"><span>管理员登录名</span><input class="input mono" name="loginName" aria-label="管理员登录名" required pattern="[a-z](?:[a-z0-9._]|-){2,63}" maxlength="64" autocomplete="off" placeholder="例如：qinglan.admin" /><small class="field-hint">平台范围唯一；不需要提前创建账户。</small></label><div class="field"><span>账号权限</span><div class="input readonly-value">当前租户管理员</div></div><label class="field"><span>初始密码</span><input class="input" name="password" type="password" required autocomplete="new-password" /></label><label class="field"><span>再次输入</span><input class="input" name="passwordConfirmation" type="password" required autocomplete="new-password" /></label></div>${renderDemoLoginHelp()}<label class="consent-control"><input name="confirmed" type="checkbox" required /><span>确认创建租户及其管理员账号</span></label>`;
  }
  return `<div class="tenant-setup-surface">${stepper}<form id="tenant-create-form" class="business-form tenant-setup-form" data-current-tenant-step="${step}" novalidate><section class="section-block tenant-step-card" data-tenant-step="${step}"><div class="tenant-step-heading"><h2>${['填写租户信息', '设置资源配额', '创建租户管理员'][step - 1]}</h2><span class="status-tag" data-tone="info">${step} / 3</span></div>${fields}</section><p id="tenant-create-feedback" class="form-feedback" role="alert" aria-live="assertive"></p><div class="form-actions tenant-setup-actions"><button class="button subtle" type="button" data-discard-tenant-setup>取消</button>${step > 1 ? '<button class="button secondary" type="button" data-tenant-setup-back>上一步</button>' : ''}<button class="button primary" type="submit" data-primary-action>${step === 3 ? '创建租户' : '下一步'}</button></div></form></div>`;
}

function featureControls(tenant) {
  return Object.entries(FEATURE_LABELS)
    .map(
      ([feature, label]) =>
        `<label class="feature-control"><input type="checkbox" data-tenant-feature="${feature}"${tenant?.availableFeatures?.[feature] ? " checked" : ""} /><span><strong>${escapeHtml(label)}</strong><small>${feature === "modelGovernance" ? "模型配置、部署与路由归入治理。" : "关闭后租户内对应入口和直接访问同时受限。"}</small></span></label>`,
    )
    .join("");
}

function renderTenantSecurity(tenant) {
  const values = tenant.securityPolicy || {};
  const rows = TENANT_SECURITY_FIELDS.map(({ key, label, unit, defaultValue }) =>
    `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(values[key] ?? defaultValue)} ${unit}</dd></div>`,
  ).join("");
  const inputs = TENANT_SECURITY_FIELDS.map(({ key, label, unit, defaultValue }) =>
    `<label class="field" for="security-${key}"><span>${label}（${unit}）</span><input id="security-${key}" class="input mono" type="number" name="${key}" value="${escapeHtml(values[key] ?? defaultValue)}" min="1" step="1" required inputmode="numeric" /></label>`,
  ).join("");
  return `<section class="section-block tenant-quota-section"><div class="section-heading"><h2>登录安全</h2><button class="button secondary" type="button" data-edit-tenant-security data-control-status="implemented">编辑登录策略</button></div><dl class="tenant-quota-summary tenant-security-summary">${rows}</dl></section>
    <dialog id="tenant-security-dialog" class="dialog-surface tenant-quota-dialog" aria-labelledby="tenant-security-title"><form id="tenant-security-form" data-tenant-id="${escapeHtml(tenant.id)}" novalidate><div class="dialog-heading"><h2 id="tenant-security-title">编辑登录策略</h2><p>租户隔离、权限校验和密码不回显始终开启。</p></div><div class="tenant-quota-dialog-grid">${inputs}</div><p id="tenant-security-feedback" class="form-feedback" role="alert" aria-live="assertive"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-cancel-tenant-security>取消</button><button class="button primary" type="submit" data-control-status="implemented">保存策略</button></div></form></dialog>`;
}

function renderTenantDetail(demoState, screen = 'platform-tenant-detail') {
  const tenant = snapshotTenant(demoState);
  if (!tenant) {
    return '<section class="state-panel" data-kind="empty"><h2>暂无可查看租户</h2></section>';
  }
  const quotaProjection = demoState?.tenantQuotaProjection || {
    quotas: tenant.quotas || DEFAULT_QUOTAS,
    usage: {},
  };
  const quotaRows = TENANT_QUOTA_FIELDS.map(({ key, label, unit }) => {
    const used = quotaProjection.usage?.[key];
    return `<div><dt>${escapeHtml(label)}</dt><dd><strong class="mono">${escapeHtml(quotaProjection.quotas?.[key] ?? DEFAULT_QUOTAS[key])}</strong> ${escapeHtml(unit)}${Number.isFinite(used) ? `<small>已用 ${escapeHtml(used)} ${escapeHtml(unit)}</small>` : ""}</dd></div>`;
  }).join("");
  const quotaInputs = TENANT_QUOTA_FIELDS.map(({ key, label, unit, integer }) => {
    const used = quotaProjection.usage?.[key] || 0;
    const help =
      key === "singleFileMb" || key === "pastedTextMib"
        ? "降低后只影响后续新增内容。"
        : `当前已用 ${used} ${unit}，保存值不能低于已用量。`;
    return `<label class="field" for="tenant-quota-${escapeHtml(key)}"><span>${escapeHtml(label)} <small>${escapeHtml(unit)}</small></span><input id="tenant-quota-${escapeHtml(key)}" class="input mono" type="number" name="${escapeHtml(key)}" value="${escapeHtml(quotaProjection.quotas?.[key] ?? DEFAULT_QUOTAS[key])}" min="${integer ? "1" : "0.01"}" step="${integer ? "1" : "0.01"}" required inputmode="decimal" aria-describedby="tenant-quota-${escapeHtml(key)}-help" data-tenant-quota /><small class="field-hint" id="tenant-quota-${escapeHtml(key)}-help">${escapeHtml(help)}</small></label>`;
  }).join("");
  const tabs = [['platform-tenant-detail', '概览'], ['platform-tenant-admins', '管理员'], ['platform-tenant-quotas', '资源配额'], ['platform-tenant-features', '可用功能'], ['platform-tenant-security', '登录安全']];
  const nav = `<nav class="tenant-settings-nav" aria-label="租户设置">${tabs.map(([target, title]) => `<button type="button" class="button subtle" data-nav-screen="${target}"${screen === target ? ' aria-current="page"' : ''}>${title}</button>`).join('')}</nav>`;
  const feedback = '<p id="tenant-settings-feedback" class="form-feedback" role="status" aria-live="polite"></p>';
  const overview = [
    '<div class="detail-workspace tenant-detail-overview"><section class="section-block detail-facts"><div class="section-heading"><div><span class="section-kicker">基本信息</span><h2>' +
      escapeHtml(tenant.organizationName) +
      "</h2></div>" +
      statusTag(tenant.status === "active" ? "活动" : "已停用") +
      '</div><dl class="fact-list"><div><dt>租户编码</dt><dd class="mono">' +
      escapeHtml(tenant.code) +
      '</dd></div><div><dt>驻留区域</dt><dd>' +
      escapeHtml(tenant.region) +
      '</dd></div><div><dt>租户管理员</dt><dd>' +
      escapeHtml(accountName(demoState, tenant.administratorAccountId)) +
      '</dd></div></dl><a class="button secondary" href="?screen=tenant-login&state=success&tenantCode=' + encodeURIComponent(tenant.code) + '">租户管理员登录入口</a></section><aside class="next-step-panel"><span class="section-kicker">安全保障</span><h2>基础保护已开启</h2><ul class="safety-checklist"><li>租户数据隔离</li><li>服务端权限校验</li><li>密码与密钥不回显</li></ul></aside></div>',
  ].join('');
  const quota = [
    '<section class="section-block tenant-quota-section"><div class="section-heading"><div><span class="section-kicker">平台配置</span><h2>资源配额</h2></div><button class="button secondary" type="button" data-edit-tenant-quotas data-control-status="implemented">编辑配额</button></div><dl class="tenant-quota-summary">' +
      quotaRows +
      "</dl></section>",
    `<dialog id="tenant-quota-dialog" class="dialog-surface tenant-quota-dialog" aria-labelledby="tenant-quota-title"><form id="tenant-quota-form" data-tenant-id="${escapeHtml(tenant.id)}" novalidate><div class="dialog-heading"><span class="section-kicker">资源配额</span><h2 id="tenant-quota-title">编辑 ${escapeHtml(tenant.organizationName)} 的配额</h2><p>保存值必须大于 0，且不能低于当前已用量。</p></div><div class="tenant-quota-dialog-grid">${quotaInputs}</div><p id="tenant-quota-feedback" class="form-feedback" role="alert" aria-live="assertive"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-cancel-tenant-quotas>取消</button><button class="button primary" type="submit" data-control-status="implemented">保存配额</button></div></form></dialog>`,
  ].join('');
  const features = [
    '<section class="section-block feature-editor"><div class="section-heading"><div><span class="section-kicker">平台配置</span><h2>租户可用功能</h2></div><span class="status-tag" data-tone="info">保存后生效</span></div><div class="feature-grid">' +
      featureControls(tenant) +
      '</div><div class="form-actions"><button class="button secondary" type="button" data-business-action="cancel-tenant-features" data-control-status="implemented">取消</button><span id="tenant-feature-feedback" class="form-feedback" role="status" aria-live="polite"></span></div></section>',
  ].join("");
  return nav + ({
    'platform-tenant-detail': overview,
    'platform-tenant-admins': renderPlatformAccounts(demoState, true),
    'platform-tenant-quotas': quota,
    'platform-tenant-features': features,
    'platform-tenant-security': renderTenantSecurity(tenant),
  }[screen] || overview) + feedback;
}

function memberAssistantChoices(demoState, tenant, member) {
  const assistants = (demoState?.assistantDelivery?.assistants || []).filter(item => item.tenantId === tenant?.id && item.publishedVersionId);
  return assistants.map((assistant, index) => `<label><input type="checkbox" ${member ? `data-member-assistant="${escapeHtml(assistant.id)}" data-member-account="${escapeHtml(member.accountId)}"` : `name="assistantIds" value="${escapeHtml(assistant.id)}"`}${member ? member.assistantIds.includes(assistant.id) ? " checked" : "" : index === 0 ? " checked" : ""}>${escapeHtml(assistant.name)}</label>`).join("") || '<span class="muted">暂无已发布助手，可先添加成员，待助手发布后授权。</span>';
}

function renderMemberFeatureEditor(demoState, tenant, member) {
  if (!member || member.role !== "ordinaryMember") {
    return '<aside class="section-block"><span class="section-kicker">管理员边界</span><h2>管理员由平台治理</h2><p>租户管理员在此只读，不能新增、提权或停用其他管理员。</p></aside>';
  }
  return [
    '<aside class="section-block member-permissions"><div class="section-heading"><div><span class="section-kicker">普通成员</span><h2>' +
      escapeHtml(accountName(demoState, member.accountId)) +
      '</h2></div>' +
      statusTag(member.status === "active" ? "活动" : "已停用") +
      "</div>",
    '<div class="feature-stack">' +
      CONVERSATION_FEATURES.map(
        (feature) =>
          `<label class="feature-control"><input type="checkbox" data-member-feature="${feature}" data-member-account="${escapeHtml(member.accountId)}"${member.conversationFeatures[feature] ? " checked" : ""} /><span><strong>${escapeHtml(FEATURE_LABELS[feature])}</strong><small>${tenant.availableFeatures[feature] ? "受成员授权与对象授权共同约束。" : "平台已关闭此租户功能，当前不会生效。"}</small></span></label>`,
      ).join("") +
      '</div><fieldset class="assistant-scope"><legend>已授权助手范围</legend>' + memberAssistantChoices(demoState, tenant, member) + '</fieldset>',
    '<div class="form-actions"><button class="button secondary" type="button" data-business-action="cancel-member-features" data-member-account="' +
      escapeHtml(member.accountId) +
      '" data-control-status="implemented">取消</button><button class="button primary" type="button" data-business-action="save-member-features" data-member-account="' +
      escapeHtml(member.accountId) +
      '" data-control-status="implemented">保存对话功能</button></div><p id="member-feature-feedback" class="form-feedback" role="status" aria-live="polite"></p></aside>',
  ].join("");
}

function renderTenantMembers(demoState) {
  const tenant = demoState?.tenants?.find((item) => item.id === (demoState.query?.session?.tenantId || DEFAULT_TENANT_ID));
  const selectedAccountId = demoState?.selectedDemoAccountId;
  const selectedMember = tenant?.members?.find(
    (member) => member.accountId === selectedAccountId,
  );
  const rows = (tenant?.members || [])
    .map((member) => {
      const administrator = member.role === "tenantAdministrator";
      const name = accountName(demoState, member.accountId);
      return `<tr data-account-row="${escapeHtml(member.accountId)}" tabindex="-1" data-search-text="${escapeHtml(`${name} ${administrator ? "租户管理员" : "普通成员"} ${member.status === "active" ? "活动" : "已停用"}`)}"><td data-label="成员"><strong>${escapeHtml(name)}</strong></td><td data-label="角色">${administrator ? "租户管理员" : "普通成员"}</td><td data-label="状态">${statusTag(member.status === "active" ? "活动" : "已停用")}</td><td data-label="助手范围">${administrator ? "平台治理" : `${member.assistantIds.length} 个`}</td><td data-label="操作"><div class="table-actions">${administrator ? '<span class="field-hint">只读</span>' : `<button class="button secondary compact" type="button" data-business-action="select-demo-member" data-account-id="${escapeHtml(member.accountId)}" data-control-status="implemented">查看成员</button><button class="button subtle compact" type="button" data-reset-account="${escapeHtml(member.accountId)}" data-control-status="implemented">重置密码</button><button class="button subtle compact" type="button" data-business-action="toggle-member-status" data-account-id="${escapeHtml(member.accountId)}" data-next-status="${member.status === "active" ? "disabled" : "active"}" data-control-status="implemented">${member.status === "active" ? "停用" : "恢复"}</button>`}</div></td></tr>`;
    })
    .join("");
  return [
    '<div class="member-directory"><section><div class="toolbar"><label class="field"><span>搜索成员</span><input class="input" type="search" data-directory-search="tenant-members" placeholder="按姓名或角色搜索" /></label><span class="toolbar-note">管理员条目只读</span></div><div class="table-shell"><table class="data-table"><thead><tr><th>成员</th><th>角色</th><th>状态</th><th>助手范围</th><th>操作</th></tr></thead><tbody>' +
      rows +
      `</tbody></table><div class="pagination" aria-label="成员目录分页"><span>第 1 页，共 ${tenant?.members?.length || 0} 位成员</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div></section></div>`,
    renderPasswordResetDialog(),
    '<p id="account-operation-feedback" class="form-feedback" role="status" aria-live="polite">' + escapeHtml(demoState?.accountFeedback || '') + '</p>',
    '<dialog id="member-status-dialog" class="dialog-surface account-dialog" aria-labelledby="member-status-title"><h2 id="member-status-title">停用普通成员</h2><p>此成员将无法登录本租户，当前对话与历史访问立即失效。助手授权保留，可在成员目录恢复。</p><p id="member-status-error" role="alert"></p><div class="dialog-actions"><button type="button" class="button secondary" data-close-dialog="member-status-dialog">取消</button><button type="button" class="button primary" data-confirm-member-disable>确认停用</button></div></dialog>',
  ].join("");
}

function renderDocumentDetail() {
  return [
    '<div class="detail-workspace"><section class="section-block detail-facts"><div class="section-heading"><div><span class="section-kicker">文档详情</span><h2>2026 员工假期制度.pdf</h2></div>' +
      statusTag("版本 v7 · 就绪") +
      '</div><dl class="fact-list"><div><dt>内容对象</dt><dd>1 个 PDF 文档 · 42 页 · 8.6 MB</dd></div><div><dt>安全扫描</dt><dd>通过</dd></div><div><dt>文本提取</dt><dd>42 / 42 页完成</dd></div><div><dt>引用定位</dt><dd>保留页码</dd></div><div><dt>当前版本</dt><dd>v7</dd></div><div><dt>活动索引</dt><dd class="mono">idx-v11</dd></div></dl></section><aside class="next-step-panel"><span class="section-kicker">变更边界</span><h2>内容、处理和索引分开管理</h2><p>更新内容会建立文档新版本；重新处理只重跑扫描、提取与分块；重建索引从已就绪文档生成新索引版本。</p></aside></div>',
    '<section class="section-block action-rail"><div><span class="section-kicker">文档内容</span><h2>更新当前 PDF</h2><p>保留 v7 供审计，新文件通过检查后形成下一版本。</p><button class="button secondary" type="button" data-nav-screen="source-add" data-control-status="navigation">更新内容</button></div><div><span class="section-kicker">处理结果</span><h2>重新执行处理</h2><p>不替换文件，只从安全检查点重新扫描、提取和分块。</p><button class="button secondary" type="button" data-control-status="unimplemented">重新处理</button></div><div><span class="section-kicker">索引版本</span><h2>纳入新索引</h2><p>活动 idx-v11 不原地修改，新构建通过评测后再激活。</p><button class="button secondary" type="button" data-nav-screen="index-rebuild" data-control-status="navigation">重建索引</button></div></section>',
  ].join("");
}

function renderKnowledgeSourcesSummary() {
  return '<div class="inline-note">活动索引 idx-v11 继续服务；更新文档不会原地修改活动索引。</div><div class="table-shell"><table class="data-table"><thead><tr><th>文档</th><th>版本</th><th>处理状态</th><th>页数</th><th>操作</th></tr></thead><tbody><tr><td><strong>2026 员工假期制度.pdf</strong></td><td>v7</td><td>就绪</td><td>42 页</td><td><button class="button secondary compact" type="button" data-nav-screen="source-detail" data-control-status="navigation">文档详情</button></td></tr><tr><td><strong>差旅费用说明.txt</strong></td><td>v3</td><td>就绪</td><td>1 个文档</td><td><button class="button secondary compact" type="button" data-nav-screen="source-detail" data-control-status="navigation">文档详情</button></td></tr></tbody></table><div class="pagination" aria-label="文档目录分页"><span>第 1 页，共 2 个文档</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>';
}

function renderIndexSummary() {
  return '<div class="detail-workspace"><section class="section-block detail-facts"><div class="section-heading"><div><span class="section-kicker">索引版本</span><h2>idx-v12</h2></div><span class="status-tag" data-tone="success">待激活</span></div><dl class="fact-list"><div><dt>文档清单</dt><dd>428 个有效文档</dd></div><div><dt>构建结果</dt><dd>完成</dd></div><div><dt>评测门禁</dt><dd>通过</dd></div><div><dt>当前活动索引</dt><dd class="mono">idx-v11</dd></div></dl></section><aside class="next-step-panel"><span class="section-kicker">索引操作</span><h2>重建不会修改活动版本</h2><p>如文档内容或处理结果变化，请建立新的索引版本。</p><button class="button secondary" type="button" data-nav-screen="index-rebuild" data-control-status="navigation">重建索引</button></aside></div>';
}

function renderPlatformHealth(demoState) {
  const health = demoState?.health;
  const selected = health?.incidents?.find(
    (incident) => incident.id === health.selectedIncidentId,
  );
  if (!selected) {
    return '<section class="state-panel" data-kind="empty"><h2>当前没有异常记录</h2></section>';
  }
  const incidentRows = health.incidents
    .map(
      (incident) =>
        `<button class="incident-row" type="button" data-business-action="select-health-incident" data-incident-id="${escapeHtml(incident.id)}" aria-pressed="${String(incident.id === selected.id)}" data-control-status="implemented"><span><strong>${escapeHtml(incident.title)}</strong><small>${escapeHtml(incident.chain)}</small></span>${statusTag(incident.status === "recovered" ? "已恢复" : "仍异常", incident.status === "recovered" ? "success" : "warning")}</button>`,
    )
    .join("");
  const events = [...selected.records].sort((a, b) => b.sequence - a.sequence)
    .map(
      (record, index) =>
        `<li${index >= 5 ? ' hidden' : ''}><span class="timeline-index">${record.sequence}</span><div><strong>${escapeHtml(record.time)}</strong><p>${escapeHtml(record.text)}</p></div></li>`,
    )
    .join("");
  return [
    '<div class="health-layout"><section class="incident-list" aria-label="业务链路异常">' +
      incidentRows +
      '</section><section class="section-block incident-detail"><div class="section-heading"><div><span class="section-kicker">脱敏影响</span><h2>' +
      escapeHtml(selected.title) +
      "</h2></div>" +
      statusTag(selected.status === "recovered" ? "已恢复" : "仍异常", selected.status === "recovered" ? "success" : "warning") +
      '</div><dl class="fact-list"><div><dt>业务链路</dt><dd>' +
      '<ol class="health-chain" aria-label="业务处理步骤">' + selected.chain.split('→').map(part => `<li>${escapeHtml(part.trim())}</li>`).join('') + '</ol>' +
      '</dd></div><div><dt>影响</dt><dd>' +
      escapeHtml(selected.impact) +
      '</dd></div><div><dt>开始时间</dt><dd>' +
      escapeHtml(selected.startedAt) +
      '</dd></div><div><dt>复查次数</dt><dd>' +
      selected.recheckCount +
      '</dd></div></dl><form id="health-action-form" class="compact-action-form"><label class="field" for="health-action"><span>新增处理记录</span><input id="health-action" class="input" name="action" placeholder="填写已采取的处理措施" required maxlength="500" /></label><button class="button secondary" type="submit" data-control-status="implemented">记录处理</button></form><div class="section-heading record-heading"><h3>处理记录</h3><span class="field-hint">最新在前 · 共 ' + selected.records.length + ' 条</span></div><ol class="timeline-list" aria-label="处理记录" tabindex="0">' +
      events +
      `</ol>${selected.records.length > 5 ? '<button class="button secondary compact" type="button" data-load-health-records>加载更早记录</button>' : ''}<p id="health-feedback" class="form-feedback" role="status" aria-live="polite"></p></section></div>`,
  ].join("");
}

function recoveryActionLabel(recovery) {
  return {
    idle: "先选择备份",
    selected: "开始完整性检查",
    readyToConfirm: "确认隔离演练",
    running: "继续演练",
    reported: "演练已完成",
    failed: "请先重试检查",
  }[recovery?.stage || "idle"];
}

function renderRecovery(demoState) {
  const recovery = demoState?.recovery;
  const selected = recovery?.backups?.find(
    (backup) => backup.id === recovery.selectedBackupId,
  );
  const steps = [
    ["selected", "选择备份"],
    ["readyToConfirm", "完整性检查"],
    ["running", "隔离恢复"],
    ["reported", "校验报告"],
  ];
  const order = { idle: -1, selected: 0, readyToConfirm: 1, running: 2, reported: 3, failed: 1 };
  const current = order[recovery?.stage || "idle"];
  return [
    '<ol class="process-steps" aria-label="恢复演练步骤">' +
      steps
        .map(
          ([stage, label], index) =>
            `<li data-step-state="${index < current ? "complete" : index === current ? "current" : "upcoming"}"><span>${index + 1}</span><strong>${label}</strong></li>`,
        )
        .join("") +
      "</ol>",
    '<div class="recovery-layout"><section class="section-block"><div class="section-heading"><div><span class="section-kicker">备份来源</span><h2>选择恢复点</h2></div><span class="status-tag" data-tone="info">M8 合成演示</span></div><div class="backup-options">' +
      (recovery?.backups || [])
        .map(
          (backup) =>
            `<label class="backup-option"><input type="radio" name="recoveryBackup" value="${escapeHtml(backup.id)}"${backup.id === recovery.selectedBackupId ? " checked" : ""} /><span><strong>${escapeHtml(backup.label)}</strong><small>可恢复时间点 ${escapeHtml(backup.recoveryPoint)} · ${escapeHtml(backup.size)}</small></span></label>`,
        )
        .join("") +
      '</div><dl class="fact-list"><div><dt>固定目标</dt><dd>' +
      escapeHtml(recovery?.target || "固定隔离演练环境") +
      '</dd></div><div><dt>生产环境</dt><dd>不读取、不写入、不接收流量</dd></div><div><dt>下一操作</dt><dd>' +
      escapeHtml(recoveryActionLabel(recovery)) +
      "</dd></div>" +
      (selected
        ? `<div><dt>已选备份</dt><dd>${escapeHtml(selected.label)}</dd></div>`
        : "") +
      '</dl></section><aside class="section-block recovery-status"><span class="section-kicker">演练状态</span>' +
      (recovery?.stage === "running"
        ? `<h2>正在恢复到固定隔离演练环境</h2><div class="progress-track" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${recovery.progress}"><span style="width:${recovery.progress}%"></span></div><p>${recovery.progress}% · 生产写入不受影响</p>`
        : recovery?.stage === "reported"
          ? '<h2>校验报告：通过</h2><dl class="fact-list"><div><dt>恢复位置</dt><dd>固定隔离演练环境</dd></div><div><dt>生产变更</dt><dd>无</dd></div><div><dt>已核对</dt><dd>租户目录摘要、知识对象清单、审计连续性</dd></div></dl>'
          : recovery?.stage === "failed"
            ? '<h2>完整性检查未通过</h2><p>没有创建演练环境，可修正备份选择后重试。</p><button class="button secondary" type="button" data-business-action="retry-recovery" data-control-status="implemented">重试检查</button>'
            : `<h2>${recovery?.stage === 'readyToConfirm' ? '完整性检查通过' : selected ? '恢复点已选择' : '请选择恢复点'}</h2><p>${selected ? '下一步检查备份，检查通过后再确认隔离演练。' : '从左侧选择一份备份，开始恢复演练。'}</p><ul class="recovery-checklist"><li><strong>完整性检查</strong><span>校验备份文件和恢复时间点</span></li><li><strong>隔离环境恢复</strong><span>不连接生产流量，生产数据保持不变</span></li><li><strong>生成校验报告</strong><span>核对对象清单与审计连续性</span></li></ul>`) +
      '<p id="recovery-feedback" class="form-feedback" role="status" aria-live="polite"></p></aside></div>',
  ].join("");
}

function contentFor(page, fixture, demoState) {
  switch (page.screen) {
    case "usage-quotas": {
      const tenant = demoState?.tenants.find(item => item.id === (demoState.query?.session?.tenantId || DEFAULT_TENANT_ID));
      const rows = TENANT_QUOTA_FIELDS.map(({ key, label, unit }) =>
        `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(tenant?.quotas[key] ?? DEFAULT_QUOTAS[key])} ${unit}</dd></div>`,
      ).join("");
      const usage = demoState?.accessGovernance?.queryUsage || { acceptedToday: "未加载", remainingToday: "未加载", activeNow: "未加载", refusedToday: "未加载" };
      return `<section class="section-block"><div class="section-heading"><h2>平台分配的资源配额</h2><span class="status-tag">只读</span></div><dl class="fact-list">${rows}</dl><p class="field-hint">需要调整资源上限时，请联系平台管理员。</p></section><section class="section-block"><div class="section-heading"><h2>当前查询用量</h2><button class="button secondary" type="button" data-nav-screen="governance-query">查看查询运行</button></div><dl class="summary-strip"><div class="summary-item"><dt>今日已接受</dt><dd>${usage.acceptedToday}</dd></div><div class="summary-item"><dt>今日剩余</dt><dd>${usage.remainingToday}</dd></div><div class="summary-item"><dt>当前执行中</dt><dd>${usage.activeNow}</dd></div><div class="summary-item"><dt>安全拒答</dt><dd>${usage.refusedToday}</dd></div></dl></section><p id="tenant-usage-feedback" class="form-feedback" role="status"></p>`;
    }
    case "platform-tenants":
      return renderPlatformTenants(demoState);
    case "platform-tenant-create":
      return renderTenantCreate(demoState);
    case "platform-tenant-detail":
    case "platform-tenant-admins":
    case "platform-tenant-quotas":
    case "platform-tenant-features":
    case "platform-tenant-security":
      return renderTenantDetail(demoState, page.screen);
    case "platform-accounts":
      return renderPlatformAccounts(demoState);
    case "tenant-members":
      return renderTenantMembers(demoState);
    case "tenant-member-account":
    case "tenant-member-features":
    case "tenant-member-assistants":
      return memberWorkspace(page.screen, demoState);
    case "tenant-member-create":
      return memberCreateWorkspace(demoState);
    case "source-detail":
      return renderDocumentDetail();
    case "knowledge-sources":
      return renderKnowledgeSourcesSummary();
    case "index-detail":
      return renderIndexSummary();
    case "platform-health":
      return renderPlatformHealth(demoState);
    case "backup-recovery":
      return renderRecovery(demoState);
    default:
      break;
  }
  switch (fixture.mode) {
    case "list":
      return renderList(page, fixture);
    case "wizard":
      return renderWizard(page, fixture);
    case "result":
      return renderResult(fixture);
    case "timeline":
      return renderTimeline(fixture);
    case "risk":
      return renderRisk(fixture);
    default:
      return renderDetail(fixture);
  }
}

function taskBody(page, fixture, state, demoState) {
  if (["loading", "empty", "error", "denied"].includes(state)) {
    const dialogs = state === "empty" && page.screen === "platform-accounts"
      ? (renderPlatformAccounts(demoState).match(/<dialog\b[\s\S]*?<\/dialog>/g) || []).join("") : "";
    return statePanel(page, fixture, state) + dialogs;
  }
  const notice =
    state === "partial"
      ? fixture.partial || `${fixture.entity}已完成可安全保留的部分，其余项目可从最近检查点重试。`
      : state === "disabled"
        ? fixture.disabled || `${fixture.entity}尚未满足执行“${page.primaryAction}”的前置条件。`
        : "";
  return [
    '<div class="task-surface" data-protected-content>',
    notice
      ? `<div class="inline-note ${state === "disabled" ? "danger" : "warning"}">${escapeHtml(notice)}</div>`
      : "",
    state === "disabled" && page.screen.startsWith("platform-tenant-")
      ? `<fieldset disabled class="tenant-disabled-fields" aria-label="租户配置暂不可用">${contentFor(page, fixture, demoState)}</fieldset>`
      : contentFor(page, fixture, demoState),
    "</div>",
  ].join("");
}

function isPlatformScope(group) {
  return ["platform-governance", "platform-operations"].includes(group.id);
}

function pageDialog(page, fixture) {
  return `<dialog id="page-dialog" class="dialog-surface"><form method="dialog"><span class="section-kicker">确认操作</span><h2>${escapeHtml(page.primaryAction)}？</h2><p>${escapeHtml(fixture.entity)}。${escapeHtml(fixture.notice)}</p><dl class="dialog-impact"><div><dt>影响</dt><dd>${escapeHtml(fixture.dialog?.impact || `只更新${fixture.entity}的当前状态。`)}</dd></div><div><dt>恢复</dt><dd>${escapeHtml(fixture.dialog?.recovery || "保留最近已确认版本，可按记录撤销。")}</dd></div><div><dt>审计</dt><dd>${escapeHtml(fixture.dialog?.audit || "记录操作主体、对象、版本与结果。")}</dd></div></dl><div class="release-actions"><button class="button secondary" value="cancel">取消</button><button class="button primary" value="confirm">确认</button></div></form></dialog>`;
}

function renderConsoleLogin(page, fixture, state, demoState) {
  const interactive = ["success", "partial", "disabled"].includes(state);
  const tenantEntry = page.screen === 'tenant-login';
  const tenantSelection = tenantEntry ? `<label class="field"><span>租户编码</span><input class="input mono" name="tenantCode" value="${escapeHtml(demoState?.loginTenantCode || 'spruce-demo')}" required autocomplete="organization" /></label>` : '';
  const statusNotice =
    state === "partial"
      ? fixture.partial || "上次验证未完成，请重新登录。"
      : state === "disabled"
        ? fixture.disabled || "当前会话入口暂不可用。"
        : fixture.notice;
  return [
    `<main id="main-content" class="console-login-shell" data-screen="${page.screen}" data-state="${state}" data-od-id="screen-${page.screen}" tabindex="-1">`,
    '<section class="console-login-brand"><div class="brand"><span class="brand-mark" aria-hidden="true">R</span><span class="brand-copy"><strong>可信 RAG</strong><small>企业控制台</small></span></div><div class="login-context"><span class="section-kicker">受控访问</span><h1 data-od-id="heading-console-login">登录可信 RAG</h1><p>平台治理与租户工作区按服务端权限分开进入。</p><dl class="login-boundary-list"><div><dt>平台范围</dt><dd>只看脱敏治理摘要</dd></div><div><dt>租户范围</dt><dd>只处理当前组织对象</dd></div></dl></div></section>',
    '<section class="console-login-task">',
    `<div class="login-form-header"><span class="section-kicker">身份验证</span><h2>${escapeHtml(fixture.title)}</h2><p>使用已授权账户继续。</p></div>`,
    interactive
      ? `<form id="console-login-form" class="login-form" data-login-entry="${tenantEntry ? 'tenant' : 'platform'}">${tenantSelection}<label class="field"><span>登录名</span><input class="input" name="username" autocomplete="username" value="${escapeHtml(demoState?.consoleLoginName || (tenantEntry ? 'qiao.demo' : 'platform.admin.demo'))}" required /></label><label class="field"><span>密码</span><input class="input" name="password" type="password" autocomplete="current-password" required /></label>${demoState?.consoleLoginFeedback ? `<p class="form-feedback" role="alert">${escapeHtml(demoState.consoleLoginFeedback)}</p>` : ""}<p class="inline-note ${state === "disabled" ? "danger" : ""}">${escapeHtml(statusNotice)}</p><button class="button primary" type="submit" data-primary-action${state === "disabled" ? " disabled" : ""}>登录</button><details class="demo-login-help"><summary>演示账户</summary><p>${tenantEntry ? '租户：spruce-demo / qiao.demo' : '平台：platform.admin.demo'}<br />初始演示密码：demo-only</p></details>${renderDemoLoginHelp()}</form>`
      : statePanel(page, fixture, state),
    interactive && state !== "disabled" ? renderDemoLoginRepair(demoState?.needsDemoLoginRepair, demoState?.demoLoginRepairFeedback) : "",
    renderLoginEntries(page.screen),
    "</section></main>",
  ].join("");
}

function deviceFactList(fixture, limit = 4) {
  return `<dl class="device-facts">${(fixture.facts || [])
    .slice(0, limit)
    .map(
      ([label, value]) =>
        `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`,
    )
    .join("")}</dl>`;
}

function renderDeviceTask(page, fixture, state) {
  if (!["success", "partial", "disabled"].includes(state)) {
    return statePanel(page, fixture, state);
  }
  if (page.screen === "device-answer") {
    if (state === "partial") {
      return `<section class="device-refusal" data-protected-content><span class="section-kicker">安全拒答</span><h2>当前证据不足</h2><p>${escapeHtml(fixture.partial)}</p></section>`;
    }
    const answer = fixture.facts.find(([label]) => label === "回答")?.[1] || fixture.notice;
    return `<section class="device-answer" data-protected-content><span class="section-kicker">现场回答</span><h2>${escapeHtml(answer)}</h2>${deviceFactList({ facts: fixture.facts.filter(([label]) => label !== "回答") }, 3)}</section>`;
  }
  if (page.screen === "device-consent") {
    return `${deviceFactList(fixture, 3)}<label class="consent-control"><input type="checkbox"${state === "disabled" ? " disabled" : " checked"} /><span><strong>我理解并同意本次使用范围</strong><small>可随时结束，本次问答后清除会话。</small></span></label>`;
  }
  if (page.screen === "device-question") {
    return `<div class="device-question-controls"><label class="field"><span>输入现场问题</span><textarea class="textarea" rows="3"${state === "disabled" ? " disabled" : ""}>人事服务台怎么走？</textarea></label><button class="button secondary voice-button" type="button"${state === "disabled" ? " disabled" : ""}>按住说话</button><p class="device-scope-note">${escapeHtml(fixture.notice)}</p></div>`;
  }
  return deviceFactList(fixture, 4);
}

function renderDevice(page, fixture, state) {
  const interactive = ["success", "partial", "disabled"].includes(state);
  return [
    `<main id="main-content" class="device-shell" data-screen="${page.screen}" data-state="${state}" data-od-id="screen-${page.screen}" tabindex="-1">`,
    '<header class="device-topbar"><div class="app-brand"><span class="brand-mark" aria-hidden="true">R</span><span>现场问答</span></div><span class="device-status">触控与语音</span></header>',
    `<section class="device-stage"><header class="device-header-copy"><span class="section-kicker">${escapeHtml(fixture.status)}</span><h1 data-od-id="heading-${page.screen}">${escapeHtml(fixture.title)}</h1></header>`,
    `<div class="device-task">${renderDeviceTask(page, fixture, state)}${interactive ? primaryAction(page, fixture, state) : ""}</div></section>`,
    "</main>",
  ].join("");
}

export function renderInventoryPage(
  page,
  group,
  fixture,
  state,
  tenant,
  demoState,
) {
  if (["console-login", "tenant-login"].includes(page.screen)) {
    return renderConsoleLogin(page, fixture, state, demoState);
  }
  if (group.shell === "device") {
    return renderDevice(page, fixture, state);
  }
  const platform = isPlatformScope(group);
  const currentEntity =
    page.screen.startsWith("platform-tenant-") && page.screen !== "platform-tenant-create"
      ? snapshotTenant(demoState)?.organizationName || fixture.entity
      : fixture.entity || fixture.title;
  const interactive = ["success", "partial", "disabled"].includes(state);
  const headerAction =
    interactive && page.screen !== "platform-tenant-create" && !page.screen.startsWith("tenant-member-")
      ? primaryAction(page, fixture, state, demoState)
      : "";
  return [
    `<div class="admin-shell full-platform-shell" data-state="${state}" data-scope="${platform ? "platform" : "tenant"}">`,
    '<aside class="admin-sidebar">',
    `<div class="brand"><span class="brand-mark" aria-hidden="true">R</span><span class="brand-copy"><strong>可信 RAG</strong><small>${platform ? "平台范围" : "租户范围"}</small></span></div>`,
    renderConsoleNavigation({
      screen: page.screen,
      scope: platform ? "platform" : "tenant",
      snapshot: demoState,
      tenantLabel: tenant.displayName,
    }),
    `<div class="admin-account"><strong>${platform ? "平台管理员" : escapeHtml(tenant.administratorLabel)}</strong><small>${platform ? "仅脱敏聚合" : escapeHtml(tenant.displayName)}</small></div>`,
    '</aside><div class="admin-workspace">',
    `<header class="topbar"><span class="topbar-context scope-context">${platform ? "平台管理 / 不含租户内容" : `${escapeHtml(tenant.displayName)} / 租户管理`}</span>${renderAccountMenu(demoState)}</header>`,
    `<main id="main-content" class="admin-main" data-screen="${escapeHtml(page.screen)}" data-state="${escapeHtml(state)}" data-od-id="screen-${escapeHtml(page.screen)}" tabindex="-1">`,
    `<nav class="breadcrumb" aria-label="当前位置">${escapeHtml(group.label)}</nav>`,
    `<header class="page-header"><div class="page-header-copy"><h1 data-od-id="heading-${escapeHtml(page.screen)}">${escapeHtml(fixture.title)}</h1>${interactive ? `<div class="page-object-line"><span>${escapeHtml(currentEntity)}</span>${statusTag(fixture.status || "可用", /失败|阻断/.test(fixture.status || "") ? "danger" : /异常|等待/.test(fixture.status || "") ? "warning" : "success")}</div>` : ""}</div>${headerAction}</header>`,
    `<div class="page-content">${taskBody(page, fixture, state, demoState)}</div>`,
    ["success", "partial", "disabled", "empty"].includes(state) &&
    ![
      "platform-tenant-create",
      "platform-tenant-detail",
      "platform-tenant-admins",
      "platform-tenant-quotas",
      "platform-tenant-features",
      "platform-tenant-security",
      "platform-accounts",
      "usage-quotas",
      "tenant-members",
      "platform-health",
      "backup-recovery",
    ].includes(page.screen)
      ? pageDialog(page, fixture)
      : "",
    "</main></div></div>",
  ].join("");
}

export function decorateExistingPage(main, page, group) {
  if (!main || !page || !group) {
    return;
  }
  main.dataset.odId = `screen-${page.screen}`;
  main.dataset.inventoryScreen = page.screen;
  main.dataset.phase = group.phase;
  const heading = main.querySelector("h1");
  if (heading) {
    heading.dataset.odId = `heading-${page.screen}`;
  }
  const exactPrimary = [...main.querySelectorAll("button")].find(
    (button) => button.textContent.trim() === page.primaryAction,
  );
  if (exactPrimary) {
    exactPrimary.dataset.primaryAction = "";
  }
  [...main.querySelectorAll(".assistant-row, .conversation-item, tbody tr")].forEach(
    (item, index) => {
      item.dataset.odId = `${page.screen}-item-${index + 1}`;
    },
  );
}
