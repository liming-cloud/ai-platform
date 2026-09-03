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

function primaryAction(page, fixture, state) {
  const disabled = state === "disabled";
  const navigation = fixture.nextScreen
    ? ` data-nav-screen="${escapeHtml(fixture.nextScreen)}"`
    : ' data-open-page-dialog="true"';
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

function contentFor(page, fixture) {
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

function taskBody(page, fixture, state) {
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
    contentFor(page, fixture),
    "</div>",
  ].join("");
}

function navButton(screen, label, active) {
  return `<button class="nav-item" type="button" data-nav-screen="${screen}"${active ? ' aria-current="page"' : ""}><span class="nav-abbr" aria-hidden="true">${label.slice(0, 1)}</span><span class="nav-label">${label}</span></button>`;
}

function isPlatformScope(group) {
  return ["platform-governance", "platform-operations"].includes(group.id);
}

function consoleNavigation(page, group) {
  const platform = isPlatformScope(group);
  const items = platform
    ? [["platform-overview", "治理"], ["platform-tenants", "租户"], ["platform-model-connections", "模型"], ["platform-health", "健康"], ["backup-recovery", "恢复"]]
    : [["tenant-overview", "概览"], ["tenant-members", "访问"], ["knowledge-directory", "知识"], ["model-providers", "模型"], ["assistants-directory", "助手"], ["evaluation-datasets", "评测"]];
  return `<nav class="admin-navigation" aria-label="${platform ? "平台范围" : "租户范围"}导航"><section class="nav-group"><span class="nav-group-label">${platform ? "平台范围 / 脱敏治理" : "租户范围 / 云杉服务示例组织"}</span>${items.map(([screen, label]) => navButton(screen, label, page.screen === screen)).join("")}</section></nav>`;
}

function pageDialog(page, fixture) {
  return `<dialog id="page-dialog" class="dialog-surface"><form method="dialog"><span class="section-kicker">确认操作</span><h2>${escapeHtml(page.primaryAction)}？</h2><p>${escapeHtml(fixture.entity)}。${escapeHtml(fixture.notice)}</p><dl class="dialog-impact"><div><dt>影响</dt><dd>${escapeHtml(fixture.dialog?.impact || `只更新${fixture.entity}的当前状态。`)}</dd></div><div><dt>恢复</dt><dd>${escapeHtml(fixture.dialog?.recovery || "保留最近已确认版本，可按记录撤销。")}</dd></div><div><dt>审计</dt><dd>${escapeHtml(fixture.dialog?.audit || "记录操作主体、对象、版本与结果。")}</dd></div></dl><div class="release-actions"><button class="button secondary" value="cancel">取消</button><button class="button primary" value="confirm">确认</button></div></form></dialog>`;
}

function renderConsoleLogin(page, fixture, state) {
  const interactive = ["success", "partial", "disabled"].includes(state);
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
      ? `<form id="console-login-form" class="login-form"><label class="field"><span>登录名</span><input class="input" autocomplete="username" value="platform.admin.demo" /></label><label class="field"><span>密码</span><input class="input" type="password" autocomplete="current-password" value="prototype-only" /></label><p class="inline-note ${state === "disabled" ? "danger" : ""}">${escapeHtml(statusNotice)}</p>${primaryAction(page, fixture, state)}</form>`
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

export function renderInventoryPage(page, group, fixture, state, tenant) {
  if (page.screen === "console-login") {
    return renderConsoleLogin(page, fixture, state);
  }
  if (group.shell === "device") {
    return renderDevice(page, fixture, state);
  }
  const platform = isPlatformScope(group);
  return [
    `<div class="admin-shell full-platform-shell" data-state="${state}" data-scope="${platform ? "platform" : "tenant"}">`,
    '<aside class="admin-sidebar">',
    `<div class="brand"><span class="brand-mark" aria-hidden="true">R</span><span class="brand-copy"><strong>可信 RAG</strong><small>${platform ? "平台范围" : "租户范围"}</small></span></div>`,
    consoleNavigation(page, group),
    `<div class="admin-account"><strong>${platform ? "平台管理员" : escapeHtml(tenant.administratorLabel)}</strong><small>${platform ? "仅脱敏聚合" : escapeHtml(tenant.displayName)}</small></div>`,
    '</aside><div class="admin-workspace">',
    `<header class="topbar"><span class="topbar-context scope-context">${platform ? "平台管理 / 不含租户内容" : `${escapeHtml(tenant.displayName)} / 租户管理`}</span><button class="button subtle account-button" type="button">${platform ? "平台管理员" : "租户管理员"} / 账户菜单</button></header>`,
    `<main id="main-content" class="admin-main" data-screen="${escapeHtml(page.screen)}" data-state="${escapeHtml(state)}" data-od-id="screen-${escapeHtml(page.screen)}" tabindex="-1">`,
    `<nav class="breadcrumb" aria-label="当前位置">${escapeHtml(group.label)}</nav>`,
    `<header class="page-header"><div class="page-header-copy"><h1 data-od-id="heading-${escapeHtml(page.screen)}">${escapeHtml(fixture.title)}</h1>${["success", "partial", "disabled"].includes(state) ? `<div class="page-object-line"><span>${escapeHtml(fixture.entity)}</span>${statusTag(fixture.status, fixture.status.includes("失败") || fixture.status.includes("阻断") ? "danger" : "success")}</div>` : ""}</div>${["success", "partial", "disabled"].includes(state) ? primaryAction(page, fixture, state) : ""}</header>`,
    `<div class="page-content">${taskBody(page, fixture, state)}</div>`,
    ["success", "partial", "disabled", "empty"].includes(state) ? pageDialog(page, fixture) : "",
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
