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

function authorizationBanner(group) {
  if (group.phase !== "M7" && group.phase !== "M8") {
    return "";
  }
  return `<aside class="authorization-banner" data-od-id="authorization-${group.phase.toLowerCase()}"><strong>${group.phase} 原型前置，不代表实现授权</strong><span>仅用于确认信息架构、任务边界和视觉基线。</span></aside>`;
}

function contractStrip(page, group) {
  return [
    '<div class="inventory-contract" data-od-id="page-contract">',
    `<span class="route-label" data-inventory-route>${escapeHtml(page.route)}</span>`,
    `<span data-inventory-task><strong>唯一任务</strong> ${escapeHtml(page.task)}</span>`,
    `<span data-inventory-action><strong>主要操作</strong> ${escapeHtml(page.primaryAction)}</span>`,
    `<span class="synthetic-label">${escapeHtml(group.phase)} · 合成数据</span>`,
    "</div>",
  ].join("");
}

function primaryAction(page, fixture, state) {
  const disabled = state === "disabled";
  const navigation = fixture.nextScreen
    ? ` data-nav-screen="${escapeHtml(fixture.nextScreen)}"`
    : ' data-open-page-dialog="true"';
  const reason = disabled
    ? ` disabled aria-describedby="primary-disabled-${escapeHtml(page.screen)}"`
    : navigation;
  return [
    `<button class="button primary" type="button" data-primary-action data-od-id="primary-${escapeHtml(page.screen)}"${reason}>${escapeHtml(page.primaryAction)}</button>`,
    disabled
      ? `<p class="field-hint primary-disabled-reason" id="primary-disabled-${escapeHtml(page.screen)}">${escapeHtml(fixture.disabled || `完成“${page.task}”所需的前置检查尚未通过。`)}</p>`
      : "",
  ].join("");
}

function statePanel(page, fixture, state) {
  const entity = fixture.entity || fixture.title;
  const copy = {
    loading: [`正在加载${fixture.title}`, `正在读取完成“${page.task}”所需的${entity}事实。`],
    empty: [`暂无可处理的${entity}`, `当前没有可用于“${page.task}”的记录。可从“${page.primaryAction}”开始。`],
    error: [`未能完成${fixture.title}`, `当前任务没有完成，最近一次已确认状态保持不变。请安全重试，不会显示内部错误或厂商响应。`],
    denied: ["当前会话无权访问此任务", `为避免泄露，页面不会确认${entity}是否存在，也不会加载受保护摘要。`],
  }[state];
  const tone = state === "error" || state === "denied" ? state : "info";
  return [
    `<section class="state-panel inventory-state" data-kind="${tone}" data-od-id="state-${state}">`,
    state === "loading"
      ? '<div class="skeleton-stack" aria-label="正在加载"><span class="skeleton-line"></span><span class="skeleton-line"></span><span class="skeleton-line"></span></div>'
      : `<p class="eyebrow">${state === "empty" ? "暂无内容" : state === "error" ? "任务失败" : "无权访问"}</p><h2>${escapeHtml(copy[0])}</h2><p>${escapeHtml(copy[1])}</p>`,
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
  return `<dl class="summary-strip domain-summary">${(fixture.facts || [])
    .slice(0, 4)
    .map(
      ([label, value]) =>
        `<div class="summary-item"><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`,
    )
    .join("")}</dl>`;
}

function factList(fixture) {
  return `<dl class="description-list">${(fixture.facts || [])
    .map(
      ([label, value]) =>
        `<dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd>`,
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
    `<div class="toolbar"><label class="field"><span>搜索${escapeHtml(fixture.entity)}</span><input class="input" type="search" placeholder="按名称、状态或范围搜索" /></label><label class="field compact-field"><span>状态</span><select class="select"><option>全部状态</option><option>需处理</option><option>已完成</option></select></label></div>`,
    renderRows(page, fixture),
  ].join("");
}

function renderSteps(fixture) {
  return `<ol class="process-steps" aria-label="任务步骤">${(fixture.steps || [])
    .map(
      (step, index) =>
        `<li data-step-state="${index === 0 ? "complete" : index === 1 ? "current" : "upcoming"}"><span>${index + 1}</span><strong>${escapeHtml(step)}</strong></li>`,
    )
    .join("")}</ol>`;
}

function renderWizard(page, fixture) {
  return [
    renderSteps(fixture),
    '<div class="split-detail wizard-layout"><section class="section-block">',
    `<div class="section-heading"><div><p class="eyebrow">当前输入</p><h2>${escapeHtml(fixture.entity)}</h2></div>${statusTag(fixture.status, "warning")}</div>`,
    `<label class="field"><span>名称或范围</span><input class="input" value="${escapeHtml(fixture.entity)}" /></label>`,
    '<label class="field"><span>关联对象</span><input class="input" type="search" value="员工服务手册" aria-describedby="selector-help" /></label>',
    '<p class="field-hint" id="selector-help">输入名称搜索，不需要内部标识。</p>',
    '</section><aside class="section-block"><p class="eyebrow">提交前影响核对</p>',
    factList(fixture),
    `<p class="inline-note">${escapeHtml(fixture.notice)}</p>`,
    "</aside></div>",
  ].join("");
}

function renderDetail(fixture) {
  return [
    summaryFacts(fixture),
    '<div class="split-detail"><section class="section-block">',
    `<div class="section-heading"><div><p class="eyebrow">任务事实</p><h2>${escapeHtml(fixture.entity)}</h2></div>${statusTag(fixture.status)}</div>`,
    factList(fixture),
    '</section><aside class="section-block"><p class="eyebrow">唯一下一动作</p>',
    `<h2>${escapeHtml(fixture.notice)}</h2>`,
    '<p class="muted">页面只提供当前任务需要的事实；完整历史与低频配置按需进入独立任务。</p>',
    "</aside></div>",
  ].join("");
}

function renderResult(fixture) {
  return [
    `<section class="result-decision"><p class="eyebrow">能否继续</p><div class="section-heading"><h2>${escapeHtml(fixture.decision || fixture.status)}</h2>${statusTag(fixture.status)}</div><p>${escapeHtml(fixture.notice)}</p></section>`,
    summaryFacts(fixture),
    '<div class="split-detail"><section class="section-block"><h2>结果指标与分类</h2>',
    factList(fixture),
    '</section><aside class="section-block"><h2>运行阶段</h2>',
    renderSteps(fixture),
    "</aside></div>",
  ].join("");
}

function renderTimeline(fixture) {
  return [
    '<div class="split-detail"><section class="section-block"><div class="section-heading"><h2>事件顺序</h2>',
    statusTag(fixture.status),
    "</div>",
    `<ol class="timeline event-timeline">${(fixture.steps || [])
      .map(
        (step, index) =>
          `<li><span><strong>事件 ${index + 1}</strong><br><small>${escapeHtml(step)}</small></span><small>${18 + index}:0${index}</small></li>`,
      )
      .join("")}</ol>`,
    '</section><aside class="section-block"><h2>范围与恢复</h2>',
    factList(fixture),
    `<p class="inline-note">${escapeHtml(fixture.notice)}</p>`,
    "</aside></div>",
  ].join("");
}

function renderRisk(fixture) {
  return [
    `<section class="risk-review"><p class="eyebrow">高风险操作核对</p><h2>${escapeHtml(fixture.entity)}</h2><p>${escapeHtml(fixture.notice)}</p></section>`,
    '<div class="risk-grid">',
    '<section><h2>操作对象</h2><p>仅影响上方明确列出的对象和范围。</p></section>',
    '<section><h2>业务影响</h2><p>提交前再次确认服务连续性、授权变化与受影响范围。</p></section>',
    '<section><h2>恢复方式</h2><p>保留最近已确认版本；失败时停止推进并从安全检查点恢复。</p></section>',
    '<section><h2>审计责任</h2><p>记录操作主体类型、范围、版本、结果与恢复事实。</p></section>',
    "</div>",
    factList(fixture),
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
      ? fixture.partial || `“${page.task}”已完成可安全保留的部分，其余项目可从最近检查点重试。`
      : state === "disabled"
        ? fixture.disabled || `完成“${page.task}”所需的安全或业务前置条件尚未通过。`
        : fixture.notice;
  return [
    `<div data-protected-content="${escapeHtml(page.screen)}">`,
    state !== "success"
      ? `<div class="inline-note ${state === "disabled" ? "danger" : "warning"}">${escapeHtml(notice)}</div>`
      : "",
    contentFor(page, fixture),
    "</div>",
  ].join("");
}

function navButton(screen, label, active) {
  return `<button class="nav-item" type="button" data-nav-screen="${screen}"${active ? ' aria-current="page"' : ""}><span class="nav-abbr" aria-hidden="true">${label.slice(0, 1)}</span><span class="nav-label">${label}</span></button>`;
}

function consoleNavigation(page, group) {
  const platform = ["platform-governance", "platform-operations", "shared-access"].includes(group.id);
  const items = platform
    ? [["platform-overview", "治理"], ["platform-tenants", "租户"], ["platform-model-connections", "模型"], ["platform-health", "健康"], ["backup-recovery", "恢复"]]
    : [["tenant-overview", "概览"], ["tenant-members", "访问"], ["knowledge-directory", "知识"], ["model-providers", "模型"], ["assistants-directory", "助手"], ["evaluation-datasets", "评测"]];
  return `<nav class="admin-navigation" aria-label="${platform ? "平台范围" : "租户范围"}导航"><section class="nav-group"><span class="nav-group-label">${platform ? "平台范围 · 脱敏治理" : "租户范围 · 云杉服务示例组织"}</span>${items.map(([screen, label]) => navButton(screen, label, page.screen === screen)).join("")}</section></nav>`;
}

function pageDialog(page, fixture) {
  return `<dialog id="page-dialog" class="dialog-surface"><form method="dialog"><p class="eyebrow">单一确认层</p><h2>${escapeHtml(page.primaryAction)}？</h2><p>对象：${escapeHtml(fixture.entity)}。${escapeHtml(fixture.notice)}</p><dl class="dialog-impact"><div><dt>影响</dt><dd>只作用于当前页面明确对象。</dd></div><div><dt>恢复</dt><dd>保留最近已确认版本或可执行撤销。</dd></div><div><dt>审计</dt><dd>记录主体、范围、版本和结果。</dd></div></dl><div class="release-actions"><button class="button secondary" value="cancel">取消</button><button class="button primary" value="confirm">确认${escapeHtml(page.primaryAction)}</button></div></form></dialog>`;
}

function renderConsoleLogin(page, group, fixture, state) {
  const body = taskBody(page, fixture, state);
  return [
    `<main id="main-content" class="console-login-shell" data-screen="${page.screen}" data-state="${state}" data-od-id="screen-${page.screen}" tabindex="-1">`,
    '<section class="console-login-brand"><div class="brand"><span class="brand-mark" aria-hidden="true">R</span><span class="brand-copy"><strong>可信 RAG</strong><small>企业控制台</small></span></div><div><p class="eyebrow">平台与租户范围明确分离</p><h1 data-od-id="heading-console-login">企业级可信知识与助手治理</h1><p>登录后只进入服务端确认的唯一工作区；平台范围不读取租户正文，租户范围不获得平台权限。</p></div></section>',
    '<section class="console-login-task">',
    contractStrip(page, group),
    `<div class="login-form-header"><h2>${escapeHtml(fixture.title)}</h2><p>${escapeHtml(page.task)}</p></div>`,
    state === "success" || state === "partial" || state === "disabled"
      ? `<form id="console-login-form" class="login-form"><label class="field"><span>登录名</span><input class="input" autocomplete="username" value="platform.admin.demo" /></label><label class="field"><span>密码</span><input class="input" type="password" autocomplete="current-password" value="prototype-only" /></label>${body}${primaryAction(page, fixture, state)}</form>`
      : body,
    "</section></main>",
  ].join("");
}

function renderDevice(page, group, fixture, state) {
  const interactive = ["success", "partial", "disabled"].includes(state);
  const taskContent =
    page.screen === "device-answer" && state === "partial"
      ? `<section class="device-refusal" data-protected-content="device-answer"><p class="eyebrow">安全拒答</p><h2>当前证据不足</h2><p>${escapeHtml(fixture.partial)}</p><dl class="description-list"><dt>下一步</dt><dd>联系现场工作人员</dd><dt>本次数据</dt><dd>继续前仅保留当前上下文</dd></dl></section>`
      : taskBody(page, fixture, state);
  const deviceControls =
    page.screen === "device-consent" && interactive
      ? `<label class="consent-control"><input type="checkbox"${state === "disabled" ? " disabled" : " checked"} /><span><strong>我理解并同意本次使用范围</strong><small>可随时结束，本次问答后清除会话。</small></span></label>`
      : page.screen === "device-question" && interactive
        ? `<div class="device-question-controls"><label class="field"><span>输入现场问题</span><textarea class="textarea" rows="3"${state === "disabled" ? " disabled" : ""}>人事服务台怎么走？</textarea></label><button class="button secondary voice-button" type="button"${state === "disabled" ? " disabled" : ""}>按住说话</button></div>`
        : "";
  return [
    `<main id="main-content" class="device-shell" data-screen="${page.screen}" data-state="${state}" data-od-id="screen-${page.screen}" tabindex="-1">`,
    '<header class="device-topbar"><div class="app-brand"><span class="brand-mark" aria-hidden="true">R</span><span>现场问答</span></div><span class="device-status">触控与语音优先</span></header>',
    authorizationBanner(group),
    `<section class="device-stage"><div class="device-header-copy">${contractStrip(page, group)}<p class="eyebrow">现场问答 · ${escapeHtml(fixture.status)}</p><h1 data-od-id="heading-${page.screen}">${escapeHtml(fixture.title)}</h1><p>${escapeHtml(page.task)}</p></div>`,
    `<div class="device-task">${taskContent}${deviceControls}${interactive ? primaryAction(page, fixture, state) : ""}</div></section>`,
    "</main>",
  ].join("");
}

export function renderInventoryPage(page, group, fixture, state, tenant) {
  if (page.screen === "console-login") {
    return renderConsoleLogin(page, group, fixture, state);
  }
  if (group.shell === "device") {
    return renderDevice(page, group, fixture, state);
  }
  const platform = ["platform-governance", "platform-operations"].includes(group.id);
  return [
    `<div class="admin-shell full-platform-shell" data-state="${state}" data-scope="${platform ? "platform" : "tenant"}">`,
    '<aside class="admin-sidebar">',
    `<div class="brand"><span class="brand-mark" aria-hidden="true">R</span><span class="brand-copy"><strong>可信 RAG</strong><small>${platform ? "平台范围" : "租户范围"}</small></span></div>`,
    consoleNavigation(page, group),
    `<div class="admin-account"><strong>${platform ? "平台管理员" : escapeHtml(tenant.administratorLabel)}</strong><small>${platform ? "仅脱敏聚合" : escapeHtml(tenant.displayName)}</small></div>`,
    '</aside><div class="admin-workspace">',
    `<header class="topbar"><span class="topbar-context scope-context">${platform ? "平台管理 · 不含租户内容" : `${escapeHtml(tenant.displayName)} · 租户管理`}</span><button class="button subtle account-button" type="button">${platform ? "平台管理员" : "租户管理员"} · 账户菜单</button></header>`,
    `<main id="main-content" class="admin-main" data-screen="${escapeHtml(page.screen)}" data-state="${escapeHtml(state)}" data-od-id="screen-${escapeHtml(page.screen)}" tabindex="-1">`,
    authorizationBanner(group),
    `<nav class="breadcrumb" aria-label="面包屑">${escapeHtml(group.label)} / ${escapeHtml(fixture.title)}</nav>`,
    `<header class="page-header"><div class="page-header-copy"><p class="eyebrow">${platform ? "平台范围" : "租户范围"} / ${escapeHtml(group.label)}</p><h1 data-od-id="heading-${escapeHtml(page.screen)}">${escapeHtml(fixture.title)}</h1><p>${escapeHtml(page.task)}</p></div>${state === "success" || state === "partial" || state === "disabled" ? primaryAction(page, fixture, state) : ""}</header>`,
    contractStrip(page, group),
    `<div class="page-content">${taskBody(page, fixture, state)}</div>`,
    pageDialog(page, fixture),
    "</main></div></div>",
  ].join("");
}

export function decorateExistingPage(main, page, group) {
  if (!main || main.querySelector("[data-inventory-route]")) {
    return;
  }
  main.dataset.odId = `screen-${page.screen}`;
  const heading = main.querySelector("h1");
  if (heading) {
    heading.dataset.odId = `heading-${page.screen}`;
  }
  const target =
    main.querySelector(".page-header-copy") ||
    main.querySelector(".assistant-page-header > div") ||
    main.querySelector(".conversation-header-copy") ||
    main.querySelector(".login-form-header") ||
    main;
  target.insertAdjacentHTML("beforeend", contractStrip(page, group));
  [...main.querySelectorAll(".assistant-row, .conversation-item, tbody tr")].forEach(
    (item, index) => {
      item.dataset.odId = `${page.screen}-item-${index + 1}`;
    },
  );
}
