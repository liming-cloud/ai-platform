import { renderConsoleNavigation, renderAccountMenu } from "./navigation.js";
import {
  CONVERSATION_FEATURES,
  DEFAULT_QUOTAS,
  DEFAULT_TENANT_ID,
  TENANT_QUOTA_FIELDS,
  TENANT_SECURITY_FIELDS,
  DEMO_LOGIN_SCENARIOS,
} from "./prototype-state.js";

/** 显示全部公开夹具，不展示或推断任何账户当前选择的口令。 */
export function renderDemoLoginHelp() {
  return `<details class="demo-login-help"><summary>演示口令说明</summary><p>仅接受以下公开演示口令：<br />${Object.values(DEMO_LOGIN_SCENARIOS).map(escapeHtml).join("<br />")}</p><small>请勿输入真实密码。创建或重置后使用刚设置的演示口令；不会显示账户当前口令。旧演示数据无法登录时，可在原型场景审查器中重置。</small></details>`;
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
    "platform-tenant-detail": ' type="button" data-business-action="save-tenant-features"',
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
      ? `<button class="button secondary" type="button"${fixture.nextScreen ? ` data-nav-screen="${escapeHtml(fixture.nextScreen)}"` : ' data-open-page-dialog="true"'}>${escapeHtml(page.primaryAction)}</button>`
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
          `<tr data-od-id="${escapeHtml(page.screen)}-row-${rowIndex + 1}">${row
            .map(
              (cell, index) =>
                `<td data-label="${escapeHtml(columns[index])}">${index === 0 ? `<strong>${escapeHtml(cell)}</strong>` : escapeHtml(cell)}</td>`,
            )
            .join("")}<td data-label="操作"><button class="button secondary compact" type="button"${rowIndex === 0 && fixture.nextScreen ? ` data-nav-screen="${escapeHtml(fixture.nextScreen)}"` : ""}>查看</button></td></tr>`,
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
    `<div class="toolbar"><label class="field"><span>搜索${escapeHtml(fixture.entity)}</span><input class="input" type="search" placeholder="输入名称或状态" /></label><label class="field compact-field"><span>状态</span><select class="select"><option>全部状态</option><option>需处理</option><option>已完成</option></select></label></div>`,
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

function renderPlatformAccounts(demoState) {
  const actorAccountId = demoState?.query?.session?.accountId;
  const accounts = demoState?.accounts || [];
  const activeCount = accounts.filter((account) => account.status === "available").length;
  const administratorCount = accounts.filter(
    (account) => account.platformAdministrator && account.status === "available",
  ).length;
  const tenantMemberCount = accounts.filter((account) =>
    (demoState?.tenants || []).some((tenant) =>
      tenant.members?.some((member) => member.accountId === account.id),
    ),
  ).length;
  const rows = accounts
    .map((account) => {
      const scope = accountScope(demoState, account);
      const tenantAssigned = scope !== "未分配" && scope !== "平台管理员";
      const suspended = account.status === "suspended";
      const self = account.id === actorAccountId;
      const assignmentDisabled =
        account.platformAdministrator || tenantAssigned || suspended;
      const assignmentReason = account.platformAdministrator
        ? "已经是平台管理员"
        : tenantAssigned
          ? "已属于租户人员范围，不能提升为平台管理员"
          : suspended
            ? "请先恢复账户"
            : "设置平台管理员身份";
      return `<tr data-account-row="${escapeHtml(account.id)}" data-search-text="${escapeHtml(`${account.loginName} ${scope} ${suspended ? "已停用" : "可用"}`)}" data-account-status="${suspended ? "suspended" : "available"}" tabindex="-1"><td data-label="登录名"><span class="primary-cell"><strong>${escapeHtml(account.loginName)}</strong><small>${escapeHtml(account.name || account.loginName)}</small></span></td><td data-label="账号用途">${escapeHtml(scope)}</td><td data-label="状态">${statusTag(suspended ? "已停用" : "可用", suspended ? "warning" : "success")}</td><td data-label="认证修订"><span class="primary-cell"><strong>认证修订 ${escapeHtml(account.authenticationRevision || 1)}</strong><small>${escapeHtml(account.authenticationChangedAt || "初始凭据已设置")}</small></span></td><td data-label="操作"><div class="table-actions account-row-actions"><button class="button secondary compact" type="button" data-reset-account="${escapeHtml(account.id)}"${self ? ' disabled aria-label="当前账户不能由此重置密码"' : ""}>重置密码</button><details class="account-actions-menu" data-account-menu="${escapeHtml(account.id)}"><summary class="button subtle compact">更多</summary><div class="account-actions-popover"><button class="button subtle compact" type="button" data-assign-platform-administrator="${escapeHtml(account.id)}"${assignmentDisabled ? " disabled" : ""} title="${escapeHtml(assignmentReason)}">设为平台管理员</button><button class="button subtle compact" type="button" data-set-account-status="${escapeHtml(account.id)}" data-next-status="${suspended ? "available" : "suspended"}"${self ? " disabled" : ""}>${suspended ? "恢复账户" : "停用账户"}</button></div></details></div></td></tr>`;
    })
    .join("");
  return [
    `<dl class="summary-strip domain-summary account-summary"><div class="summary-item"><dt>人员账户</dt><dd>${accounts.length}</dd></div><div class="summary-item"><dt>可用账户</dt><dd>${activeCount}</dd></div><div class="summary-item"><dt>平台管理员</dt><dd>${administratorCount}</dd></div><div class="summary-item"><dt>租户成员</dt><dd>${tenantMemberCount}</dd></div></dl>`,
    '<div class="toolbar"><label class="field"><span>搜索人员账户</span><input class="input" type="search" data-directory-search="platform-accounts" placeholder="按登录名或账号用途搜索" /></label><label class="field compact-field"><span>状态</span><select class="select" data-account-status-filter><option value="">全部状态</option><option value="available">可用</option><option value="suspended">已停用</option></select></label></div>',
    `<div class="table-shell account-table-shell"><table class="data-table"><thead><tr><th>登录名</th><th>账号用途</th><th>状态</th><th>认证修订</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table><div class="pagination" aria-label="人员账户分页"><span>第 1 页，共 ${accounts.length} 个账户</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>`,
    `<p id="account-operation-feedback" class="form-feedback" role="status" aria-live="polite">${escapeHtml(demoState?.accountFeedback || "")}</p>`,
    '<dialog id="create-account-dialog" class="dialog-surface account-dialog" aria-labelledby="create-account-title"><form id="create-account-form" novalidate><div class="dialog-heading"><span class="section-kicker">全局人员账户</span><h2 id="create-account-title">创建人员账户</h2><p>设置一次性初始密码；提交后不再显示。</p></div>' + renderDemoLoginHelp() + '<label class="field" for="create-account-login"><span>登录名</span><input id="create-account-login" class="input mono" name="loginName" required pattern="[a-z](?:[a-z0-9._]|-){2,63}" maxlength="64" autocomplete="off" placeholder="例如：zhou.demo" aria-describedby="create-account-login-help" /><small id="create-account-login-help" class="field-hint">3–64 位小写字母、数字、点、下划线或连字符。</small></label><div class="wizard-field-grid account-password-grid"><label class="field" for="create-account-password"><span>初始密码</span><input id="create-account-password" class="input" name="password" type="password" required maxlength="1024" autocomplete="new-password" /></label><label class="field" for="create-account-password-confirmation"><span>再次输入</span><input id="create-account-password-confirmation" class="input" name="passwordConfirmation" type="password" required maxlength="1024" autocomplete="new-password" /></label></div><p class="inline-note compact-note">请通过组织批准的安全渠道单独告知本人；页面、存储和审计均不保留明文。</p><p id="create-account-feedback" class="form-feedback" role="alert" aria-live="assertive"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-cancel-create-account>取消</button><button class="button primary" type="submit">创建账户</button></div></form></dialog>',
    '<dialog id="reset-account-password-dialog" class="dialog-surface account-dialog" aria-labelledby="reset-account-password-title"><form id="reset-account-password-form" novalidate><div class="dialog-heading"><span class="section-kicker">受控凭据重置</span><h2 id="reset-account-password-title">重置密码</h2><p id="reset-account-target"></p></div>' + renderDemoLoginHelp() + '<div class="wizard-field-grid account-password-grid"><label class="field" for="reset-account-password"><span>新密码</span><input id="reset-account-password" class="input" name="password" type="password" required maxlength="1024" autocomplete="new-password" /></label><label class="field" for="reset-account-password-confirmation"><span>再次输入</span><input id="reset-account-password-confirmation" class="input" name="passwordConfirmation" type="password" required maxlength="1024" autocomplete="new-password" /></label></div><label class="consent-control"><input name="revokeSessions" type="checkbox" required /><span><strong>撤销全部会话</strong><small>平台与租户范围的既有会话同时失效。</small></span></label><p id="reset-account-password-feedback" class="form-feedback" role="alert" aria-live="assertive"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-cancel-reset-account>取消</button><button class="button primary" type="submit">重置密码</button></div></form></dialog>',
    '<dialog id="account-governance-dialog" class="dialog-surface account-dialog" aria-labelledby="account-governance-title"><form id="account-governance-form"><div class="dialog-heading"><span class="section-kicker">账户治理</span><h2 id="account-governance-title">确认账户操作</h2><p id="account-governance-description"></p></div><dl class="dialog-impact"><div><dt>影响</dt><dd id="account-governance-impact"></dd></div><div><dt>恢复</dt><dd id="account-governance-recovery"></dd></div><div><dt>审计</dt><dd>记录操作者、目标账户、操作、结果与非秘密修订。</dd></div></dl><p id="account-governance-feedback" class="form-feedback" role="alert" aria-live="assertive"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-cancel-account-governance>取消</button><button class="button primary" type="submit" id="account-governance-confirm">确认</button></div></form></dialog>',
  ].join("");
}

function renderPlatformTenants(demoState) {
  const rows = (demoState?.tenants || [])
    .map(
      (tenant) =>
        `<tr data-search-text="${escapeHtml(`${tenant.organizationName} ${tenant.code} ${tenant.region}`)}"><td data-label="租户"><span class="primary-cell"><strong>${escapeHtml(tenant.organizationName)}</strong><span class="mono">${escapeHtml(tenant.code)}</span></span></td><td data-label="驻留区域">${escapeHtml(tenant.region)}</td><td data-label="状态">${statusTag(tenant.status === "active" ? "活动" : "已停用")}</td><td data-label="租户管理员">已分配</td><td data-label="操作"><button class="button secondary compact" type="button" data-select-tenant="${escapeHtml(tenant.id)}" data-nav-screen="platform-tenant-detail" data-control-status="navigation">查看详情</button></td></tr>`,
    )
    .join("");
  return [
    '<div class="compact-summary"><span><strong>租户目录</strong> · 合成生命周期数据</span><span>安全边界异常 0</span></div>',
    '<div class="toolbar"><label class="field"><span>搜索租户</span><input class="input" type="search" data-directory-search="platform-tenants" placeholder="按组织名称或租户编码搜索" /></label><label class="field compact-field"><span>状态</span><select class="select"><option>全部状态</option><option>活动</option><option>已停用</option></select></label></div>',
    `<div class="table-shell"><table class="data-table"><thead><tr><th>租户</th><th>驻留区域</th><th>状态</th><th>租户管理员</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table><div class="pagination" aria-label="租户目录分页"><span>第 1 页，共 ${demoState?.tenants?.length || 0} 个租户</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>`,
  ].join("");
}

function renderTenantCreate(demoState) {
  const setup = demoState?.tenantSetup || {
    step: 1,
    organizationName: "",
    tenantCode: "",
    administratorAccountId: "",
    quotas: DEFAULT_QUOTAS,
  };
  const step = Math.max(1, Math.min(3, Number(setup.step) || 1));
  const steps = ["租户信息", "管理员", "配额设置"];
  const candidates = (demoState?.accounts || []).filter(
    (account) =>
      account.administratorEligible &&
      !account.platformAdministrator &&
      account.status === "available",
  );
  const administrator = candidates.find(
    (account) => account.id === setup.administratorAccountId,
  );
  const stepper = `<ol class="process-steps tenant-setup-progress" aria-label="建立租户步骤">${steps
    .map(
      (label, index) =>
        `<li data-step-state="${index + 1 < step ? "complete" : index + 1 === step ? "current" : "upcoming"}"><span>${index + 1}</span><strong>${label}</strong></li>`,
    )
    .join("")}</ol>`;
  const cancel = '<button class="button subtle" type="button" data-discard-tenant-setup data-control-status="implemented">取消</button>';
  const back =
    step > 1
      ? '<button class="button secondary" type="button" data-tenant-setup-back data-control-status="implemented">上一步</button>'
      : "";
  const submit = `<button class="button primary" type="submit" data-primary-action data-control-status="implemented">${step === 3 ? "创建租户" : "下一步"}</button>`;
  let currentStep;
  if (step === 1) {
    currentStep = [
      '<section class="section-block tenant-step-card" data-tenant-step="1"><div class="tenant-step-heading"><div><span class="section-kicker">租户信息</span><h2>填写组织名称和租户编码</h2></div><span class="status-tag" data-tone="info">1 / 3</span></div>',
      '<div class="wizard-field-grid"><label class="field" for="tenant-organization-name"><span>组织名称</span><input id="tenant-organization-name" class="input" name="organizationName" value="' +
        escapeHtml(setup.organizationName) +
        '" required minlength="2" maxlength="40" autocomplete="organization" placeholder="例如：青岚体验租户" aria-describedby="tenant-organization-help" data-tenant-draft /><small class="field-hint" id="tenant-organization-help">使用 2 至 40 个字符的组织名称。</small></label><label class="field" for="tenant-code"><span>租户编码</span><input id="tenant-code" class="input mono" name="tenantCode" value="' +
        escapeHtml(setup.tenantCode) +
        '" required pattern="[a-z](?:[a-z0-9]|-){2,19}" autocomplete="off" placeholder="qinglan-demo" aria-describedby="tenant-code-help" data-tenant-draft /><small class="field-hint" id="tenant-code-help">使用 3 至 20 位小写字母、数字或连字符，创建后不能修改。</small></label></div></section>',
    ].join("");
  } else if (step === 2) {
    currentStep = [
      '<section class="section-block tenant-step-card" data-tenant-step="2"><div class="tenant-step-heading"><div><span class="section-kicker">管理员</span><h2>选择租户管理员</h2></div><span class="status-tag" data-tone="info">2 / 3</span></div>',
      '<label class="field tenant-administrator-field" for="tenant-administrator"><span>租户管理员</span><select id="tenant-administrator" class="select" name="administratorAccountId" required aria-describedby="tenant-administrator-help" data-tenant-draft><option value="">请选择账户</option>' +
        candidates
          .map(
            (account) =>
              `<option value="${escapeHtml(account.id)}"${account.id === setup.administratorAccountId ? " selected" : ""}>${escapeHtml(account.name)} · ${escapeHtml(account.loginName)}</option>`,
          )
          .join("") +
        '</select><small class="field-hint" id="tenant-administrator-help">只列出可担任租户管理员的现有合成账户。</small></label></section>',
    ].join("");
  } else {
    const quotaFields = TENANT_QUOTA_FIELDS.map(({ key, label, unit, integer }) => {
      const helpId = `tenant-setup-${key}-help`;
      return `<label class="field quota-field" for="tenant-setup-${key}"><span>${escapeHtml(label)} <small>${escapeHtml(unit)}</small></span><input id="tenant-setup-${key}" class="input mono" type="number" name="${escapeHtml(key)}" value="${escapeHtml(setup.quotas?.[key] ?? DEFAULT_QUOTAS[key])}" min="${integer ? "1" : "0.01"}" step="${integer ? "1" : "0.01"}" required inputmode="decimal" aria-describedby="${helpId}" data-tenant-quota /><small class="field-hint" id="${helpId}">请输入大于 0 的${integer ? "整数" : "数值"}。</small></label>`;
    }).join("");
    currentStep = [
      '<section class="section-block tenant-step-card" data-tenant-step="3"><div class="tenant-step-heading"><div><span class="section-kicker">配额设置</span><h2>设置资源配额</h2></div><span class="status-tag" data-tone="info">3 / 3</span></div>',
      `<div class="tenant-confirmation-summary"><span><strong>${escapeHtml(setup.organizationName)}</strong><small>${escapeHtml(setup.tenantCode)}</small></span><span><strong>${escapeHtml(administrator?.name || "未选择管理员")}</strong><small>租户管理员</small></span></div>`,
      `<div class="tenant-quota-grid">${quotaFields}</div>`,
      '<div class="tenant-safety-note"><strong>安全边界保持开启</strong><span>租户隔离、权限校验和凭据不回显不能关闭。</span></div>',
      '<label class="consent-control"><input name="confirmed" type="checkbox" required aria-describedby="tenant-confirm-help" /><span><strong>确认创建此租户</strong><small id="tenant-confirm-help">创建后可在租户详情继续调整资源配额。</small></span></label></section>',
    ].join("");
  }
  return [
    '<div class="tenant-setup-surface">',
    stepper,
    `<form id="tenant-create-form" class="business-form tenant-setup-form" data-current-tenant-step="${step}" novalidate>`,
    currentStep,
    '<p id="tenant-create-feedback" class="form-feedback" role="alert" aria-live="assertive"></p>',
    `<div class="form-actions tenant-setup-actions">${cancel}${back}${submit}</div>`,
    "</form>",
    "</div>",
  ].join("");
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

function renderTenantDetail(demoState) {
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
  return [
    '<div class="detail-workspace tenant-detail-overview"><section class="section-block detail-facts"><div class="section-heading"><div><span class="section-kicker">租户事实</span><h2>' +
      escapeHtml(tenant.organizationName) +
      "</h2></div>" +
      statusTag(tenant.status === "active" ? "活动" : "已停用") +
      '</div><dl class="fact-list"><div><dt>租户编码</dt><dd class="mono">' +
      escapeHtml(tenant.code) +
      '</dd></div><div><dt>驻留区域</dt><dd>' +
      escapeHtml(tenant.region) +
      '</dd></div><div><dt>租户管理员</dt><dd>' +
      escapeHtml(accountName(demoState, tenant.administratorAccountId)) +
      '</dd></div></dl></section><aside class="next-step-panel"><span class="section-kicker">安全边界</span><h2>隔离、权限校验与凭据不回显保持开启</h2><p>资源配额可调整，安全边界不可更改。</p></aside></div>',
    '<section class="section-block tenant-quota-section"><div class="section-heading"><div><span class="section-kicker">平台配置</span><h2>资源配额</h2></div><button class="button secondary" type="button" data-edit-tenant-quotas data-control-status="implemented">编辑配额</button></div><dl class="tenant-quota-summary">' +
      quotaRows +
      "</dl></section>",
    renderTenantSecurity(tenant),
    '<p id="tenant-settings-feedback" class="form-feedback" role="status" aria-live="polite"></p>',
    '<section class="section-block feature-editor"><div class="section-heading"><div><span class="section-kicker">平台配置</span><h2>租户可用功能</h2></div><span class="status-tag" data-tone="info">保存后生效</span></div><div class="feature-grid">' +
      featureControls(tenant) +
      '</div><div class="form-actions"><button class="button secondary" type="button" data-business-action="cancel-tenant-features" data-control-status="implemented">取消</button><span id="tenant-feature-feedback" class="form-feedback" role="status" aria-live="polite"></span></div></section>',
    `<dialog id="tenant-quota-dialog" class="dialog-surface tenant-quota-dialog" aria-labelledby="tenant-quota-title"><form id="tenant-quota-form" data-tenant-id="${escapeHtml(tenant.id)}" novalidate><div class="dialog-heading"><span class="section-kicker">资源配额</span><h2 id="tenant-quota-title">编辑 ${escapeHtml(tenant.organizationName)} 的配额</h2><p>保存值必须大于 0，且不能低于当前已用量。</p></div><div class="tenant-quota-dialog-grid">${quotaInputs}</div><p id="tenant-quota-feedback" class="form-feedback" role="alert" aria-live="assertive"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-cancel-tenant-quotas>取消</button><button class="button primary" type="submit" data-control-status="implemented">保存配额</button></div></form></dialog>`,
  ].join("");
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
      '</div><fieldset class="assistant-scope"><legend>已授权助手范围</legend><label><input type="checkbox" data-member-assistant="assistant-employee-service" data-member-account="' +
      escapeHtml(member.accountId) +
      '"' +
      (member.assistantIds.includes("assistant-employee-service") ? " checked" : "") +
      " /> 员工服务助手</label><label><input type=\"checkbox\" data-member-assistant=\"assistant-support\" data-member-account=\"" +
      escapeHtml(member.accountId) +
      '"' +
      (member.assistantIds.includes("assistant-support") ? " checked" : "") +
      " /> 客户支持助手</label></fieldset>",
    '<div class="form-actions"><button class="button secondary" type="button" data-business-action="cancel-member-features" data-member-account="' +
      escapeHtml(member.accountId) +
      '" data-control-status="implemented">取消</button><button class="button primary" type="button" data-business-action="save-member-features" data-member-account="' +
      escapeHtml(member.accountId) +
      '" data-control-status="implemented">保存对话功能</button></div><p id="member-feature-feedback" class="form-feedback" role="status" aria-live="polite"></p></aside>',
  ].join("");
}

function renderTenantMembers(demoState) {
  const tenant = demoState?.tenants?.find((item) => item.id === DEFAULT_TENANT_ID);
  const selectedAccountId = demoState?.selectedDemoAccountId;
  const selectedMember = tenant?.members?.find(
    (member) => member.accountId === selectedAccountId,
  );
  const rows = (tenant?.members || [])
    .map((member) => {
      const administrator = member.role === "tenantAdministrator";
      const name = accountName(demoState, member.accountId);
      return `<tr data-search-text="${escapeHtml(`${name} ${administrator ? "租户管理员" : "普通成员"} ${member.status === "active" ? "活动" : "已停用"}`)}"><td data-label="成员"><strong>${escapeHtml(name)}</strong></td><td data-label="角色">${administrator ? "租户管理员" : "普通成员"}</td><td data-label="状态">${statusTag(member.status === "active" ? "活动" : "已停用")}</td><td data-label="助手范围">${administrator ? "平台治理" : `${member.assistantIds.length} 个`}</td><td data-label="操作"><div class="table-actions">${administrator ? '<span class="field-hint">只读</span>' : `<button class="button secondary compact" type="button" data-business-action="select-demo-member" data-account-id="${escapeHtml(member.accountId)}" data-control-status="implemented">配置并演示</button><button class="button subtle compact" type="button" data-business-action="toggle-member-status" data-account-id="${escapeHtml(member.accountId)}" data-next-status="${member.status === "active" ? "disabled" : "active"}" data-control-status="implemented">${member.status === "active" ? "停用" : "恢复"}</button>`}</div></td></tr>`;
    })
    .join("");
  const candidates = (demoState?.accounts || []).filter(
    (account) => account.memberEligible && !account.platformAdministrator,
  );
  return [
    '<div class="member-layout"><section><div class="toolbar"><label class="field"><span>搜索成员</span><input class="input" type="search" data-directory-search="tenant-members" placeholder="按姓名或角色搜索" /></label><span class="toolbar-note">管理员条目只读</span></div><div class="table-shell"><table class="data-table"><thead><tr><th>成员</th><th>角色</th><th>状态</th><th>助手范围</th><th>操作</th></tr></thead><tbody>' +
      rows +
      `</tbody></table><div class="pagination" aria-label="成员目录分页"><span>第 1 页，共 ${tenant?.members?.length || 0} 位成员</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div></section>${renderMemberFeatureEditor(demoState, tenant, selectedMember)}</div>`,
    '<dialog id="add-member-dialog" class="dialog-surface"><form id="add-member-form" method="dialog"><span class="section-kicker">添加已有账户</span><h2>添加普通成员</h2><label class="field" for="member-account-search"><span>搜索账户</span><input id="member-account-search" class="input" type="search" placeholder="按姓名或登录名搜索" autocomplete="off" /></label><label class="field" for="member-account-select"><span>可用账户</span><select id="member-account-select" class="select" name="accountId" required><option value="">请选择账户</option>' +
      candidates
        .map(
          (account) =>
            `<option value="${escapeHtml(account.id)}">${escapeHtml(account.name)} · ${escapeHtml(account.loginName)}</option>`,
        )
        .join("") +
      '</select></label><fieldset class="assistant-scope"><legend>初始对话功能</legend><label><input type="checkbox" name="conversationFeatures" value="assistantSelection" checked /> 选择已授权助手</label><label><input type="checkbox" name="conversationFeatures" value="conversation" checked /> 发起与继续对话</label><label><input type="checkbox" name="conversationFeatures" value="ownHistory" /> 查看本人历史</label><label><input type="checkbox" name="conversationFeatures" value="authorizedCitations" /> 查看有权引用</label></fieldset><fieldset class="assistant-scope"><legend>初始助手范围</legend><label><input type="checkbox" name="assistantIds" value="assistant-employee-service" checked /> 员工服务助手</label><label><input type="checkbox" name="assistantIds" value="assistant-support" /> 客户支持助手</label></fieldset><p id="add-member-feedback" class="form-feedback" role="status" aria-live="polite"></p><div class="release-actions"><button class="button secondary" type="button" value="cancel" data-cancel-add-member data-control-status="implemented">取消</button><button class="button primary" type="submit" value="default" data-control-status="implemented">确认添加</button></div></form></dialog>',
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
  const events = selected.records
    .map(
      (record) =>
        `<li><span class="timeline-index">${record.sequence}</span><div><strong>${escapeHtml(record.time)}</strong><p>${escapeHtml(record.text)}</p></div></li>`,
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
      escapeHtml(selected.chain) +
      '</dd></div><div><dt>影响</dt><dd>' +
      escapeHtml(selected.impact) +
      '</dd></div><div><dt>开始时间</dt><dd>' +
      escapeHtml(selected.startedAt) +
      '</dd></div><div><dt>复查次数</dt><dd>' +
      selected.recheckCount +
      '</dd></div></dl><form id="health-action-form" class="compact-action-form"><label class="field" for="health-action"><span>新增处理记录</span><input id="health-action" class="input" name="action" value="暂停受影响批次并保留当前活动索引" /></label><button class="button secondary" type="submit" data-control-status="implemented">记录处理</button></form><ol class="timeline-list" aria-label="处理记录">' +
      events +
      '</ol><p id="health-feedback" class="form-feedback" role="status" aria-live="polite"></p></section></div>',
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
            : '<h2>只在完整性通过后创建隔离环境</h2><p>确认前可更换备份；不会提供生产覆盖恢复。</p>') +
      '<p id="recovery-feedback" class="form-feedback" role="status" aria-live="polite"></p></aside></div>',
  ].join("");
}

function contentFor(page, fixture, demoState) {
  switch (page.screen) {
    case "usage-quotas": {
      const tenant = demoState?.tenants.find(item => item.id === DEFAULT_TENANT_ID);
      const rows = TENANT_QUOTA_FIELDS.map(({ key, label, unit }) =>
        `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(tenant?.quotas[key] ?? DEFAULT_QUOTAS[key])} ${unit}</dd></div>`,
      ).join("");
      return `<section class="section-block"><div class="section-heading"><h2>平台分配的资源配额</h2><span class="status-tag">只读</span></div><dl class="fact-list">${rows}</dl><p class="field-hint">需要调整资源上限时，请联系平台管理员。</p></section>${renderList(page, fixture)}<p id="tenant-usage-feedback" class="form-feedback" role="status"></p>`;
    }
    case "platform-tenants":
      return renderPlatformTenants(demoState);
    case "platform-tenant-create":
      return renderTenantCreate(demoState);
    case "platform-tenant-detail":
      return renderTenantDetail(demoState);
    case "platform-accounts":
      return renderPlatformAccounts(demoState);
    case "tenant-members":
      return renderTenantMembers(demoState);
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
    return statePanel(page, fixture, state);
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
    state === "disabled" && ["platform-tenant-create", "platform-tenant-detail"].includes(page.screen)
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
  const tenantSelection = demoState?.consoleLoginTenants?.length
    ? `<label class="field"><span>选择管理组织</span><select class="select" id="console-tenant-select" name="tenantCode" required><option value="">请选择组织</option>${demoState.consoleLoginTenants.map(tenant => `<option value="${escapeHtml(tenant.code)}">${escapeHtml(tenant.organizationName)}</option>`).join("")}</select></label>` : "";
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
      ? `<form id="console-login-form" class="login-form"><label class="field"><span>登录名</span><input class="input" name="username" autocomplete="username" value="${escapeHtml(demoState?.consoleLoginName || "platform.admin.demo")}" required /></label>${tenantSelection}<label class="field"><span>密码</span><input class="input" name="password" type="password" autocomplete="current-password" required /></label>${demoState?.consoleLoginFeedback ? `<p class="form-feedback" role="alert">${escapeHtml(demoState.consoleLoginFeedback)}</p>` : ""}<p class="inline-note ${state === "disabled" ? "danger" : ""}">${escapeHtml(statusNotice)}</p><button class="button primary" type="submit" data-primary-action${state === "disabled" ? " disabled" : ""}>登录</button><details class="demo-login-help"><summary>演示账户</summary><p>平台：platform.admin.demo<br />租户：qiao.demo<br />初始演示口令：demo-only</p></details>${renderDemoLoginHelp()}</form>`
      : statePanel(page, fixture, state),
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
  if (page.screen === "console-login") {
    return renderConsoleLogin(page, fixture, state, demoState);
  }
  if (group.shell === "device") {
    return renderDevice(page, fixture, state);
  }
  const platform = isPlatformScope(group);
  const currentEntity =
    page.screen === "platform-tenant-detail"
      ? snapshotTenant(demoState)?.organizationName || fixture.entity
      : fixture.entity || fixture.title;
  const interactive = ["success", "partial", "disabled"].includes(state);
  const headerAction =
    interactive && page.screen !== "platform-tenant-create"
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
    `<header class="page-header"><div class="page-header-copy"><h1 data-od-id="heading-${escapeHtml(page.screen)}">${escapeHtml(fixture.title)}</h1>${interactive ? `<div class="page-object-line"><span>${escapeHtml(currentEntity)}</span>${statusTag(fixture.status || "可用", String(fixture.status || "").includes("失败") || String(fixture.status || "").includes("阻断") ? "danger" : "success")}</div>` : ""}</div>${headerAction}</header>`,
    `<div class="page-content">${taskBody(page, fixture, state, demoState)}</div>`,
    ["success", "partial", "disabled", "empty"].includes(state) &&
    ![
      "platform-tenant-create",
      "platform-tenant-detail",
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
