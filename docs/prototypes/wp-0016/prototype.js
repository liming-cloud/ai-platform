import { decorateExistingPage, renderInventoryPage } from "./full-platform.js";

const root = document.querySelector("#prototype-root");
const reviewer = document.querySelector("#prototype-reviewer");
const screenSelect = document.querySelector("#review-screen");
const stateSelect = document.querySelector("#review-state");
const applyReview = document.querySelector("#review-apply");
const reviewSearch = document.querySelector("#review-search");

let screens = new Set();
let states = new Set();
let inventory;
let pageFixtures;
let pageByScreen;
let groupByScreen;

let fixtures;
let selectedAssistantId = "assistant-employee-service";
let activeConversationId = "cv-20260902-01";
let streamTimer;

try {
  const [fixtureResponse, inventoryResponse, pageFixtureResponse] =
    await Promise.all([
      fetch("./fixtures.json", { cache: "no-store" }),
      fetch("./page-inventory.json", { cache: "no-store" }),
      fetch("./page-fixtures.json", { cache: "no-store" }),
    ]);
  if (
    !fixtureResponse.ok ||
    !inventoryResponse.ok ||
    !pageFixtureResponse.ok
  ) {
    throw new Error("fixture-load-failed");
  }
  [fixtures, inventory, pageFixtures] = await Promise.all([
    fixtureResponse.json(),
    inventoryResponse.json(),
    pageFixtureResponse.json(),
  ]);
  const pages = inventory.groups.flatMap((group) => group.pages);
  screens = new Set(pages.map((page) => page.screen));
  states = new Set(inventory.states);
  pageByScreen = new Map(pages.map((page) => [page.screen, page]));
  groupByScreen = new Map(
    inventory.groups.flatMap((group) =>
      group.pages.map((page) => [page.screen, group]),
    ),
  );
  initializeReviewer();
  render();
} catch {
  root.innerHTML = [
    '<main id="main-content" class="boot-state" tabindex="-1">',
    '<p class="eyebrow">OpenDesign 高保真原型</p>',
    "<h1>无法读取本地脱敏 fixtures</h1>",
    "<p>请通过本地 HTTP 服务器打开本目录，不要直接使用 file URL。</p>",
    "</main>",
  ].join("");
}

applyReview.addEventListener("click", () => {
  navigate(screenSelect.value, stateSelect.value);
});

reviewSearch.addEventListener("input", filterReviewerOptions);
reviewSearch.addEventListener("keydown", (event) => {
  if (event.key !== "Enter") {
    return;
  }
  const first = [...screenSelect.options].find((option) => !option.hidden);
  if (first) {
    screenSelect.value = first.value;
    navigate(first.value, stateSelect.value);
  }
});

window.addEventListener("popstate", render);
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && document.body.dataset.historyOpen === "true") {
    closeHistory();
  }
});

function currentScenario() {
  const params = new URLSearchParams(window.location.search);
  const requestedScreen = params.get("screen") || "login";
  const requestedState = params.get("state") || "success";
  return {
    screen: screens.has(requestedScreen) ? requestedScreen : "login",
    state: states.has(requestedState) ? requestedState : "success",
    review: params.get("review") !== "0",
  };
}

function initializeReviewer() {
  screenSelect.innerHTML = inventory.groups
    .map(
      (group) =>
        `<optgroup label="${escapeHtml(`${group.phase} · ${group.label}`)}">${group.pages
          .map((page) => {
            const title = pageFixtures.pages[page.screen]?.title || page.task;
            return `<option value="${escapeHtml(page.screen)}">${escapeHtml(`${group.phase} · ${title} · ${page.screen}`)}</option>`;
          })
          .join("")}</optgroup>`,
    )
    .join("");
}

function filterReviewerOptions(event) {
  const query = event.currentTarget.value.trim().toLocaleLowerCase("zh-CN");
  [...screenSelect.options].forEach((option) => {
    option.hidden =
      query.length > 0 &&
      !option.textContent.toLocaleLowerCase("zh-CN").includes(query);
  });
}

function navigate(screen, state = "success") {
  const next = new URL(window.location.href);
  next.searchParams.set("screen", screens.has(screen) ? screen : "login");
  next.searchParams.set("state", states.has(state) ? state : "success");
  window.history.pushState({}, "", next);
  render();
}

function render() {
  if (!fixtures) {
    return;
  }
  window.clearInterval(streamTimer);
  const scenario = currentScenario();
  document.body.dataset.screen = scenario.screen;
  document.body.dataset.state = scenario.state;
  document.body.dataset.review = scenario.review ? "visible" : "hidden";
  document.body.dataset.historyOpen = "false";
  reviewer.hidden = !scenario.review;
  screenSelect.value = scenario.screen;
  stateSelect.value = scenario.state;
  root.innerHTML = renderScreen(scenario.screen, scenario.state);
  const page = pageByScreen.get(scenario.screen);
  decorateExistingPage(
    root.querySelector(`main[data-screen="${CSS.escape(scenario.screen)}"]`),
    page,
    groupByScreen.get(scenario.screen),
  );
  root
    .querySelectorAll("button, input, textarea, select, dialog")
    .forEach((control, index) => {
      if (!control.dataset.odId) {
        control.dataset.odId = `${scenario.screen}-control-${index + 1}`;
      }
    });
  bindInteractions(scenario.screen);
  document.title = screenTitle(scenario.screen) + " · 可信 RAG 原型";
}

function renderScreen(screen, state) {
  switch (screen) {
    case "assistants":
      return renderAssistants(state);
    case "conversation":
      return renderConversation(state);
    case "knowledge-directory":
      return renderKnowledgeDirectory(state);
    case "knowledge-sources":
      return renderKnowledgeSources(state);
    case "index-detail":
      return renderIndexDetail(state);
    case "assistant-release":
      return renderAssistantRelease(state);
    default:
      return renderInventoryPage(
        pageByScreen.get(screen),
        groupByScreen.get(screen),
        pageFixtures.pages[screen],
        state,
        fixtures.tenant,
      );
  }
}

function screenTitle(screen) {
  return pageFixtures.pages[screen]?.title || pageByScreen.get(screen)?.task;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function statusTag(status) {
  const mapping = {
    READY: ["就绪", "success"],
    PUBLISHED: ["已发布", "success"],
    READY_FOR_REVIEW: ["待激活", "success"],
    READY_FOR_RELEASE: ["可发布", "success"],
    INDEXING: ["索引中", "warning"],
    FAILED: ["失败", "danger"],
    BUILDING: ["构建中", "warning"],
  };
  const item = mapping[status] || [status, "info"];
  return (
    '<span class="status-tag" data-tone="' +
    item[1] +
    '">' +
    item[0] +
    "</span>"
  );
}

function statePanel(kind, title, description, actionLabel, actionScreen) {
  const action = actionLabel
    ? '<button class="button secondary" type="button" data-nav-screen="' +
      (actionScreen || currentScenario().screen) +
      '" data-nav-state="success">' +
      escapeHtml(actionLabel) +
      "</button>"
    : "";
  const content =
    kind === "loading"
      ? [
          '<div class="skeleton-stack" aria-label="正在加载">',
          '<span class="skeleton-line"></span>',
          '<span class="skeleton-line"></span>',
          '<span class="skeleton-line"></span>',
          "</div>",
        ].join("")
      : [
          '<p class="eyebrow">' + stateLabel(kind) + "</p>",
          "<h2>" + escapeHtml(title) + "</h2>",
          "<p>" + escapeHtml(description) + "</p>",
          action,
        ].join("");
  return (
    '<section class="state-panel" data-kind="' +
    kind +
    '">' +
    content +
    "</section>"
  );
}

function stateLabel(kind) {
  return (
    {
      loading: "正在加载",
      empty: "暂无内容",
      error: "任务失败",
      denied: "无权访问",
      partial: "部分完成",
      disabled: "暂不可用",
    }[kind] || "状态"
  );
}

function genericState(screen, state) {
  if (state === "success" || state === "partial" || state === "disabled") {
    return "";
  }
  const labels = {
    login: "普通用户登录",
    assistants: "授权助手",
    conversation: "会话消息",
    "knowledge-directory": "知识库目录",
    "knowledge-sources": "数据来源",
    "index-detail": "索引版本",
    "assistant-release": "助手版本",
  };
  const subject = labels[screen];
  if (state === "loading") {
    return statePanel("loading", "", "", "", "");
  }
  if (state === "empty") {
    return statePanel(
      "empty",
      subject + "目前为空",
      emptyDescription(screen),
      emptyAction(screen),
      emptyActionScreen(screen),
    );
  }
  if (state === "error") {
    return statePanel(
      "error",
      "暂时无法加载" + subject,
      "当前任务没有完成，也没有使用过期结果。请检查连接后安全重试。",
      "重新加载",
      screen,
    );
  }
  return statePanel(
    "denied",
    "当前会话不能访问此任务",
    "页面不会确认目标资源是否存在，也不会加载正文、引用或历史消息。",
    screen === "login" ? "返回登录" : "返回已授权入口",
    screen === "login"
      ? "login"
      : screen.startsWith("knowledge") ||
          screen.includes("index") ||
          screen.includes("release")
        ? "knowledge-directory"
        : "assistants",
  );
}

function emptyDescription(screen) {
  return {
    login: "当前浏览器没有可恢复的会话，请使用已获授权的普通用户账户登录。",
    assistants: "管理员发布并授权助手后，普通用户才能建立新会话。",
    conversation: "这个会话还没有消息。发送第一个问题即可开始。",
    "knowledge-directory":
      "先建立一个明确归属当前租户的知识库，再接入数据来源。",
    "knowledge-sources":
      "当前知识库还没有数据来源。可以粘贴文本或上传本地文件。",
    "index-detail": "还没有待评审的索引版本。创建重建任务后在此跟踪。",
    "assistant-release": "还没有待发布的助手版本。先完成草稿、测试和冻结评测。",
  }[screen];
}

function emptyAction(screen) {
  return {
    login: "使用普通用户账户登录",
    assistants: "",
    conversation: "发送第一个问题",
    "knowledge-directory": "创建知识库",
    "knowledge-sources": "添加数据",
    "index-detail": "返回索引目录",
    "assistant-release": "返回助手草稿",
  }[screen];
}

function emptyActionScreen(screen) {
  return {
    login: "login",
    assistants: "assistants",
    conversation: "conversation",
    "knowledge-directory": "knowledge-directory",
    "knowledge-sources": "knowledge-sources",
    "index-detail": "knowledge-sources",
    "assistant-release": "assistant-release",
  }[screen];
}

function renderLogin(state) {
  const generic = genericState("login", state);
  const task =
    generic ||
    [
      '<form class="login-form" id="login-form">',
      '<div class="login-form-header">',
      '<p class="eyebrow">普通用户问答</p>',
      "<h2>登录你的问答工作区</h2>",
      "<p>登录后只会显示当前账户获授权且已发布的助手。</p>",
      "</div>",
      state === "partial"
        ? '<div class="inline-note warning">上次会话已退出，未发送的输入没有保存。可以重新登录。</div>'
        : "",
      '<label class="field"><span>组织编码</span><input class="input" name="tenantCode" autocomplete="organization" value="spruce-demo" required /></label>',
      '<label class="field"><span>登录名</span><input class="input" name="username" autocomplete="username" value="demo.user" required /></label>',
      '<label class="field"><span>密码</span><input class="input" name="password" type="password" autocomplete="current-password" value="prototype-only" required /></label>',
      '<div class="login-actions">',
      '<button class="button primary" type="submit"' +
        (state === "disabled"
          ? ' disabled aria-describedby="login-disabled-reason"'
          : "") +
        ">进入问答工作区</button>",
      state === "disabled"
        ? '<p id="login-disabled-reason" class="field-hint">当前账户没有 QUERY_USE 能力或尚无已授权助手。</p>'
        : '<button class="text-link" type="button">无法登录？联系组织管理员</button>',
      "</div>",
      '<p class="session-safety">会话凭据只由同源 HttpOnly cookie 承载。本页面不会保存 token，也不会进入租户管理控制台。</p>',
      "</form>",
    ].join("");
  return [
    '<main id="main-content" class="login-shell" data-screen="login" data-state="' +
      state +
      '" tabindex="-1">',
    '<section class="login-story" aria-label="可信问答说明">',
    '<div class="brand"><span class="brand-mark" aria-hidden="true">R</span><span class="brand-copy"><strong>可信 RAG</strong><small>普通用户工作区</small></span></div>',
    '<div class="login-story-main">',
    '<p class="eyebrow">有依据地回答，不确定就拒答</p>',
    "<h1>从获授权的企业知识中获得可核验答案</h1>",
    "<p>每次回答都固定助手与索引版本，引用可定位到真实来源。权限撤销后，历史正文和引用立即停止披露。</p>",
    '<ul class="trust-list"><li>只检索当前成员有权访问的知识</li><li>答案附带来源、版本与位置</li><li>证据不足时明确拒答，不编造结论</li></ul>',
    "</div>",
    '<p class="login-story-footer">此入口只面向 TENANT_USER。管理任务使用独立租户会话。</p>',
    "</section>",
    '<section class="login-task" aria-label="普通用户登录表单">',
    task,
    "</section>",
    "</main>",
  ].join("");
}

function appTopbar() {
  return [
    '<header class="app-topbar">',
    '<div class="app-brand"><span class="brand-mark" aria-hidden="true">R</span><span>可信问答</span></div>',
    '<button class="button subtle account-button" type="button">普通用户 · 账户菜单</button>',
    "</header>",
  ].join("");
}

function renderAssistants(state) {
  const generic = genericState("assistants", state);
  const rows = fixtures.assistants
    .map((assistant) => assistantRow(assistant, state === "disabled"))
    .join("");
  const body =
    generic ||
    [
      state === "partial"
        ? '<div class="inline-note warning">1 个助手正在重新评测，已从当前可选目录中暂时移除。其余助手仍可安全使用。</div>'
        : "",
      state === "disabled"
        ? '<div class="inline-note warning">当前账户的助手授权正在重新计算。目录可查看，但暂时不能创建新会话。</div>'
        : "",
      '<div class="assistant-search">',
      '<label class="field"><span>搜索助手</span><input id="assistant-search" class="input" type="search" placeholder="按助手名称或用途搜索" autocomplete="off" /></label>',
      '<button class="button secondary" type="button" data-clear-assistant-search>清除</button>',
      "</div>",
      '<div id="assistant-list" class="assistant-list" role="listbox" aria-label="已授权助手">' +
        rows +
        "</div>",
      '<div class="pagination" aria-label="助手目录分页">',
      "<span>第 1 页，共 3 个已授权助手</span>",
      '<div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div>',
      "</div>",
    ].join("");
  return [
    '<div class="app-shell" data-state="' + state + '">',
    appTopbar(),
    '<main id="main-content" class="assistant-page" data-screen="assistants" data-state="' +
      state +
      '" tabindex="-1">',
    '<header class="assistant-page-header"><div><p class="eyebrow">已授权助手</p><h1>你想向谁提问？</h1><p>选择助手会创建固定当前已发布版本的新会话。完整目录只有这一处普通分页。</p></div>',
    '<button class="button secondary" type="button" data-nav-screen="conversation">继续最近会话</button></header>',
    body,
    "</main>",
    "</div>",
  ].join("");
}

function assistantRow(assistant, disabled) {
  return [
    '<button class="assistant-row" type="button" role="option" data-assistant-id="' +
      escapeHtml(assistant.id) +
      '" aria-selected="' +
      String(selectedAssistantId === assistant.id) +
      '"' +
      (disabled ? " disabled" : "") +
      ">",
    '<span class="assistant-row-copy"><strong>' +
      escapeHtml(assistant.name) +
      "</strong><p>" +
      escapeHtml(assistant.summary) +
      "</p></span>",
    '<span class="assistant-row-meta"><span>知识库：' +
      escapeHtml(assistant.knowledgeBase) +
      "</span><span>固定版本：" +
      escapeHtml(assistant.version) +
      "</span></span>",
    statusTag(assistant.status),
    "</button>",
  ].join("");
}

function renderConversation(state) {
  const assistant =
    fixtures.assistants.find((item) => item.id === selectedAssistantId) ||
    fixtures.assistants[0];
  const history =
    state === "denied"
      ? '<p class="field-hint">当前会话无权读取历史，未加载任何会话摘要。</p>'
      : fixtures.conversations
          .map((conversation) =>
            [
              '<button class="conversation-item" type="button" data-conversation-id="' +
                escapeHtml(conversation.id) +
                '" aria-current="' +
                (activeConversationId === conversation.id ? "page" : "false") +
                '">',
              "<span>" + escapeHtml(conversation.title) + "</span>",
              "<small>" +
                escapeHtml(conversation.assistant) +
                " · " +
                escapeHtml(conversation.updatedAt) +
                "</small>",
              "</button>",
            ].join(""),
          )
          .join("");
  return [
    '<main id="main-content" class="conversation-shell" data-screen="conversation" data-state="' +
      state +
      '" tabindex="-1">',
    '<aside id="history-panel" class="history-panel" aria-label="会话历史">',
    '<header class="history-header"><div class="app-brand"><span class="brand-mark" aria-hidden="true">R</span><span>会话记录</span></div><button class="button subtle compact drawer-close" type="button" data-close-history>关闭</button></header>',
    '<div class="history-search"><label class="field"><span class="field-label">搜索会话</span><input class="input" type="search" placeholder="按标题搜索" /></label></div>',
    '<nav class="conversation-list" aria-label="历史会话">' +
      history +
      "</nav>",
    '<footer class="history-footer"><button class="button primary" type="button" data-nav-screen="assistants">新会话</button><small>完整历史只用于查看和恢复，不会全部注入模型上下文。</small></footer>',
    "</aside>",
    '<button class="history-backdrop" type="button" aria-label="关闭会话历史" data-close-history></button>',
    '<section class="conversation-main">',
    '<header class="conversation-header">',
    '<div class="conversation-header-copy"><h1>' +
      (state === "denied" ? "可信问答会话" : escapeHtml(assistant.name)) +
      "</h1><small>" +
      (state === "denied"
        ? "已隐藏受保护会话信息"
        : escapeHtml(assistant.knowledgeBase) +
          " · 固定 " +
          escapeHtml(assistant.version) +
          " · 引用必需") +
      "</small></div>",
    '<div class="conversation-header-actions"><button class="button secondary compact mobile-history-button" type="button" aria-controls="history-panel" aria-expanded="false" data-open-history>会话记录</button><button class="button subtle compact" type="button" data-nav-screen="assistants">助手目录</button></div>',
    "</header>",
    renderMessageRegion(state),
    renderComposer(state),
    "</section>",
    "</main>",
  ].join("");
}

function renderMessageRegion(state) {
  const generic = genericState("conversation", state);
  if (generic) {
    return (
      '<section class="message-region" aria-live="polite">' +
      generic +
      "</section>"
    );
  }
  const messages = fixtures.messages.map(renderMessage).join("");
  const partial =
    state === "partial"
      ? [
          '<article class="message assistant" data-streaming-message>',
          '<div class="message-meta"><strong>员工服务助手</strong><span>正在回答</span></div>',
          '<div class="message-body"><p class="streaming-cursor">你有 7 天未休年假时，最多可结转 5 天。剩余 2 天是否可以补偿，需要继续核验适用制度和审批条件</p></div>',
          '<p class="query-stage" aria-live="polite">已检索 8 个授权证据块，正在生成并等待引用最终化</p>',
          "</article>",
        ].join("")
      : "";
  const disabled =
    state === "disabled"
      ? '<div class="inline-note warning">助手已发布 v13。当前会话仍固定 v12；如需使用新版本，请明确开启新会话。</div>'
      : "";
  return [
    '<section class="message-region">',
    '<div class="older-messages"><button class="button subtle compact" type="button" data-load-older>加载更早消息 · beforeSequence=41</button></div>',
    state === "partial"
      ? '<div class="inline-note warning">连接曾短暂中断，已从最后确认的事件序号 44 恢复；已完成消息不会重复追加。</div>'
      : "",
    disabled,
    '<div id="messages" class="messages" aria-live="polite">' +
      messages +
      partial +
      "</div>",
    "</section>",
  ].join("");
}

function renderMessage(message) {
  const citations = message.citations
    ? '<div class="citations" aria-label="回答引用">' +
      message.citations
        .map((citation, index) =>
          [
            '<article class="citation">',
            '<span class="citation-index" aria-hidden="true">' +
              String(index + 1) +
              "</span>",
            '<span class="citation-copy"><strong>' +
              escapeHtml(citation.source) +
              "</strong><small>" +
              escapeHtml(citation.location) +
              '</small><span hidden data-citation-excerpt="' +
              escapeHtml(citation.id) +
              '">' +
              escapeHtml(citation.excerpt) +
              "</span></span>",
            '<button class="button secondary compact" type="button" data-citation-id="' +
              escapeHtml(citation.id) +
              '" aria-expanded="false">查看授权引用</button>',
            "</article>",
          ].join(""),
        )
        .join("") +
      "</div>"
    : "";
  return [
    '<article class="message ' +
      escapeHtml(message.role) +
      '" data-sequence="' +
      escapeHtml(message.sequence) +
      '">',
    '<div class="message-meta"><strong>' +
      (message.role === "user" ? "你" : "员工服务助手") +
      "</strong><span>事件 " +
      escapeHtml(message.sequence) +
      " · " +
      escapeHtml(message.createdAt) +
      "</span></div>",
    '<div class="message-body"><p>' +
      escapeHtml(message.content) +
      "</p></div>",
    citations,
    "</article>",
  ].join("");
}

function renderComposer(state) {
  const blocked =
    state === "disabled" || state === "denied" || state === "loading";
  return [
    '<footer class="composer-shell">',
    '<form id="composer-form" class="composer">',
    '<label class="field"><span class="field-label">继续提问</span><textarea id="question-input" class="textarea" rows="2" maxlength="2000" placeholder="基于当前已授权知识继续提问"' +
      (blocked ? " disabled" : "") +
      "></textarea></label>",
    '<button class="button primary" type="submit"' +
      (blocked ? " disabled" : "") +
      ">发送</button>",
    "</form>",
    '<p class="composer-hint">Enter 发送，Shift+Enter 换行。最多使用最近 6 个已完成轮次且不超过 3,000 tokens 做问题改写。</p>',
    "</footer>",
  ].join("");
}

function adminShell(active, title, summary, action, body, state, breadcrumb) {
  return [
    '<div class="admin-shell" data-state="' + state + '">',
    '<aside class="admin-sidebar">',
    '<div class="brand"><span class="brand-mark" aria-hidden="true">R</span><span class="brand-copy"><strong>可信 RAG</strong><small>租户工作区</small></span></div>',
    '<nav class="admin-navigation" aria-label="租户管理导航">',
    navGroup("知识管理", [
      ["knowledge-directory", "库", "知识库", active === "knowledge-directory"],
      ["knowledge-sources", "源", "数据来源", active === "knowledge-sources"],
      ["index-detail", "索", "索引发布", active === "index-detail"],
    ]),
    navGroup("AI 应用", [
      ["assistant-release", "助", "助手发布", active === "assistant-release"],
      ["assistant-release", "评", "评测结果", false],
    ]),
    "</nav>",
    '<div class="admin-account"><strong>' +
      escapeHtml(fixtures.tenant.administratorLabel) +
      "</strong><small>" +
      escapeHtml(fixtures.tenant.displayName) +
      "</small></div>",
    "</aside>",
    '<div class="admin-workspace">',
    '<header class="topbar"><span class="topbar-context">' +
      escapeHtml(fixtures.tenant.displayName) +
      ' · 租户管理</span><button class="button subtle account-button" type="button">租户管理员 · 账户菜单</button></header>',
    '<main id="main-content" class="admin-main" data-screen="' +
      active +
      '" data-state="' +
      state +
      '" tabindex="-1">',
    '<nav class="breadcrumb" aria-label="面包屑">' +
      escapeHtml(breadcrumb) +
      "</nav>",
    '<header class="page-header"><div class="page-header-copy"><p class="eyebrow">' +
      pageContext(active) +
      "</p><h1>" +
      escapeHtml(title) +
      "</h1><p>" +
      escapeHtml(summary) +
      "</p></div>" +
      action +
      "</header>",
    '<div class="page-content">' + body + "</div>",
    "</main>",
    "</div>",
    "</div>",
  ].join("");
}

function navGroup(label, items) {
  return [
    '<section class="nav-group" aria-label="' + escapeHtml(label) + '">',
    '<span class="nav-group-label">' + escapeHtml(label) + "</span>",
    items
      .map((item) =>
        [
          '<button class="nav-item" type="button" data-nav-screen="' +
            item[0] +
            '"' +
            (item[3] ? ' aria-current="page"' : "") +
            ">",
          '<span class="nav-abbr" aria-hidden="true">' +
            item[1] +
            '</span><span class="nav-label">' +
            item[2] +
            "</span>",
          "</button>",
        ].join(""),
      )
      .join(""),
    "</section>",
  ].join("");
}

function pageContext(active) {
  return active === "assistant-release" ? "AI 应用 / 助手发布" : "知识管理";
}

function renderKnowledgeDirectory(state) {
  const generic = genericState("knowledge-directory", state);
  const rows = fixtures.knowledgeBases
    .map((item) =>
      [
        "<tr>",
        '<td data-label="知识库"><span class="primary-cell"><strong>' +
          escapeHtml(item.name) +
          "</strong><span>" +
          escapeHtml(item.description) +
          "</span></span></td>",
        '<td data-label="状态">' + statusTag(item.status) + "</td>",
        '<td data-label="有效文档">' + escapeHtml(item.documents) + "</td>",
        '<td data-label="原始内容">' + escapeHtml(item.storage) + "</td>",
        '<td data-label="活动索引"><span class="mono">' +
          escapeHtml(item.activeIndex) +
          "</span></td>",
        '<td data-label="最近变化">' + escapeHtml(item.updatedAt) + "</td>",
        '<td data-label="操作"><div class="table-actions"><button class="button secondary compact" type="button" data-nav-screen="knowledge-overview">进入</button></div></td>',
        "</tr>",
      ].join(""),
    )
    .join("");
  const content =
    generic ||
    [
      state === "partial"
        ? '<div class="inline-note warning">“客户支持知识”正在构建新索引，活动 idx-v7 仍持续服务。目录可以继续管理。</div>'
        : "",
      state === "disabled"
        ? '<div class="inline-note warning">当前租户已使用 3 / 3 个知识库配额。创建入口已禁用，现有知识库仍可维护。</div>'
        : "",
      '<dl class="summary-strip"><div class="summary-item"><dt>知识库</dt><dd>2 / 3</dd></div><div class="summary-item"><dt>有效文档</dt><dd>684</dd></div><div class="summary-item"><dt>原始内容</dt><dd>3.5 / 10 GB</dd></div><div class="summary-item"><dt>需处理</dt><dd>1</dd></div></dl>',
      '<div class="toolbar"><label class="field"><span>搜索知识库</span><input class="input" type="search" placeholder="按名称搜索" /></label><span class="muted">选择保持在可恢复 URL 中</span></div>',
      '<div class="table-shell"><table class="data-table"><thead><tr><th style="width:30%">知识库</th><th>状态</th><th>有效文档</th><th>原始内容</th><th>活动索引</th><th>最近变化</th><th style="text-align:right">操作</th></tr></thead><tbody>' +
        rows +
        "</tbody></table>",
      '<div class="pagination" aria-label="知识库目录分页"><span>第 1 页，共 2 个知识库</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>',
    ].join("");
  const action =
    '<button class="button primary" type="button"' +
    (state === "disabled"
      ? ' disabled aria-describedby="kb-quota-reason"'
      : ' data-nav-screen="knowledge-overview"') +
    ">创建知识库</button>";
  return adminShell(
    "knowledge-directory",
    "知识库",
    "建立、找到并进入当前租户的知识库。每个知识库拥有独立来源、索引和助手边界。",
    action,
    content +
      (state === "disabled"
        ? '<p id="kb-quota-reason" class="field-hint">达到每租户 3 个知识库的小型试点配额。</p>'
        : ""),
    state,
    "知识管理 / 知识库",
  );
}

function renderKnowledgeSources(state) {
  const generic = genericState("knowledge-sources", state);
  const sourceItems =
    state === "success"
      ? fixtures.sources.map((item) => ({
          ...item,
          status: "READY",
          stage: "READY",
          detail:
            item.status === "FAILED"
              ? "安全重试完成，引用定位已保留。"
              : item.detail,
        }))
      : fixtures.sources;
  const rows = sourceItems
    .map((item) =>
      [
        "<tr>",
        '<td data-label="数据来源"><span class="primary-cell"><strong>' +
          escapeHtml(item.name) +
          "</strong><span>" +
          escapeHtml(item.kind) +
          " · " +
          escapeHtml(item.revision) +
          "</span></span></td>",
        '<td data-label="处理阶段"><span class="mono">' +
          escapeHtml(item.stage) +
          "</span></td>",
        '<td data-label="状态">' + statusTag(item.status) + "</td>",
        '<td data-label="文档数">' + escapeHtml(item.documents) + "</td>",
        '<td data-label="最近变化">' + escapeHtml(item.updatedAt) + "</td>",
        '<td data-label="操作"><div class="table-actions"><button class="button secondary compact" type="button" data-nav-screen="source-detail">' +
          (item.status === "FAILED" ? "安全重试" : "查看") +
          '</button><button class="button subtle compact" type="button">更多</button></div></td>',
        "</tr>",
        item.status === "FAILED"
          ? '<tr><td data-label="失败说明" colspan="6"><div class="inline-note danger">' +
            escapeHtml(item.detail) +
            " 仅可从 EXTRACTING 安全阶段重试。</div></td></tr>"
          : "",
      ].join(""),
    )
    .join("");
  const content =
    generic ||
    [
      '<div class="inline-note' +
        (state === "partial" ? " warning" : "") +
        '">' +
        (state === "partial"
          ? "3 个来源可继续用于当前活动索引，1 个扫描 PDF 失败且未写入索引。"
          : "活动索引 idx-v11 继续服务。添加或替换来源不会原地修改活动索引。") +
        "</div>",
      '<dl class="summary-strip"><div class="summary-item"><dt>来源</dt><dd>4</dd></div><div class="summary-item"><dt>就绪</dt><dd>' +
        (state === "partial" ? "2" : "4") +
        '</dd></div><div class="summary-item"><dt>处理中</dt><dd>' +
        (state === "partial" ? "1" : "0") +
        '</dd></div><div class="summary-item"><dt>失败</dt><dd>' +
        (state === "partial" ? "1" : "0") +
        "</dd></div></dl>",
      '<div class="toolbar"><label class="field"><span>搜索数据来源</span><input class="input" type="search" placeholder="按名称或类型搜索" /></label><button class="button secondary" type="button" data-nav-screen="processing-quality">查看处理与质量</button></div>',
      '<div class="table-shell"><table class="data-table"><thead><tr><th style="width:31%">数据来源</th><th>处理阶段</th><th>状态</th><th>文档数</th><th>最近变化</th><th style="text-align:right">操作</th></tr></thead><tbody>' +
        rows +
        "</tbody></table>",
      '<div class="pagination" aria-label="数据来源分页"><span>第 1 页，共 4 个来源</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>',
      '<p class="field-hint">支持：' +
        fixtures.supportedSources.map(escapeHtml).join("、") +
        "。单文件上限 50 MB；粘贴文本上限 1 MiB；不接受远程 URL。</p>",
    ].join("");
  const action =
    '<button class="button primary" type="button"' +
    (state === "disabled"
      ? ' disabled aria-describedby="source-disabled-reason"'
      : ' data-nav-screen="source-add"') +
    ">添加数据</button>";
  return adminShell(
    "knowledge-sources",
    "数据来源",
    "员工服务手册 · 管理本库的粘贴文本和本地文件，观察每个来源的安全处理阶段。",
    action,
    content +
      (state === "disabled"
        ? '<p id="source-disabled-reason" class="field-hint">全库重建正在生成清单，暂不接受并发替换。</p>'
        : ""),
    state,
    "知识管理 / 知识库 / 员工服务手册 / 数据来源",
  );
}

function qualityGrid(quality, includeFirstToken) {
  const items = [
    ["Recall@10", (quality.recallAt10 * 100).toFixed(0) + "%"],
    ["引用正确率", (quality.citationPrecision * 100).toFixed(0) + "%"],
    ["拒答准确率", (quality.refusalAccuracy * 100).toFixed(0) + "%"],
    ["越权泄漏", String(quality.unauthorizedLeakage)],
    includeFirstToken
      ? ["首 Token p95", (quality.firstTokenP95Ms / 1000).toFixed(2) + " s"]
      : ["检索 p95", quality.retrievalP95Ms + " ms"],
  ];
  return (
    '<div class="quality-grid">' +
    items
      .map(
        (item) =>
          '<div class="quality-item"><span>' +
          item[0] +
          "</span><strong>" +
          item[1] +
          "</strong></div>",
      )
      .join("") +
    "</div>"
  );
}

function renderIndexDetail(state) {
  const generic = genericState("index-detail", state);
  const index = fixtures.indexVersion;
  const ready = state !== "partial" && state !== "disabled";
  const content =
    generic ||
    [
      '<div class="inline-note' +
        (ready ? "" : " warning") +
        '">' +
        (state === "partial"
          ? "已处理 301 / 428 个文档，活动 idx-v11 继续服务。构建完成前不能评测或激活。"
          : state === "disabled"
            ? "冻结评测发现成员授权过滤后的 Recall@10 为 0.81，未达到 0.85 门槛。"
            : escapeHtml(index.reason)) +
        "</div>",
      '<div class="split-detail"><div>',
      '<section class="section-block"><div class="section-heading"><div><p class="eyebrow">构建状态</p><h2>' +
        (ready ? "构建与评测完成" : "正在构建新版本") +
        "</h2></div>" +
        statusTag(ready ? "READY_FOR_REVIEW" : "BUILDING") +
        "</div>",
      '<div class="progress-track" aria-label="索引构建进度"><div class="progress-value" style="width:' +
        (ready ? "100" : "71") +
        '%"></div></div>',
      '<p class="muted">' +
        (ready
          ? "428 个文档 · 3,486 个分块 · 未自动激活"
          : "301 / 428 个文档 · 可安全断点恢复") +
        "</p></section>",
      '<section class="section-block"><div class="section-heading"><h2>冻结评测</h2><span class="mono">eval-set-v6 · 100 题</span></div>' +
        qualityGrid(
          state === "disabled"
            ? { ...index.quality, recallAt10: 0.81 }
            : index.quality,
          false,
        ) +
        "</section>",
      '<section class="section-block"><h2>构建时间线</h2><ol class="timeline"><li><span><strong>清单冻结</strong><br><small>428 个活动来源修订</small></span><small>17:05</small></li><li><span><strong>结构化分块</strong><br><small>structure-parent-child-v3</small></span><small>17:18</small></li><li><span><strong>向量与全文构建</strong><br><small>HNSW + PostgreSQL FTS</small></span><small>17:31</small></li><li><span><strong>冻结评测</strong><br><small>权限、召回、引用、拒答与延迟</small></span><small>17:44</small></li></ol></section>',
      "</div><aside>",
      '<section class="section-block"><h2>不可变配置</h2><dl class="description-list"><dt>Embedding</dt><dd>' +
        escapeHtml(index.embedding) +
        "</dd><dt>解析器</dt><dd>" +
        escapeHtml(index.parser) +
        "</dd><dt>分块</dt><dd>" +
        escapeHtml(index.chunking) +
        "</dd><dt>检索</dt><dd>" +
        escapeHtml(index.retrieval) +
        '</dd><dt>当前活动</dt><dd><span class="mono">idx-v11</span></dd></dl></section>',
      '<section class="release-decision' +
        (state === "disabled" ? " blocked" : "") +
        '"><p class="eyebrow">激活决定</p><h2>' +
        (state === "disabled"
          ? "暂不能激活"
          : ready
            ? "可以激活 idx-v12"
            : "等待构建完成") +
        "</h2><p>" +
        (state === "disabled"
          ? "先修复授权过滤后的召回损失并重新运行冻结评测。"
          : "激活是原子切换。旧索引保留，不会在构建期间停止服务。") +
        "</p>",
      '<button class="button primary" type="button" data-confirm-index' +
        (!ready || state === "disabled" ? " disabled" : "") +
        ">激活此版本</button></section>",
      "</aside></div>",
      '<dialog id="index-dialog" class="dialog-surface"><form method="dialog"><p class="eyebrow">高风险确认</p><h2>激活 idx-v12？</h2><p>新查询将使用 idx-v12；已固定旧版本的会话继续使用原版本。此操作会记录审计事件。</p><div class="release-actions"><button class="button secondary" value="cancel">取消</button><button class="button primary" value="confirm">确认激活</button></div></form></dialog>',
    ].join("");
  return adminShell(
    "index-detail",
    "索引 idx-v12",
    "评审全量重建结果，并在质量与权限门禁通过后原子激活。构建完成不等于自动上线。",
    '<button class="button secondary" type="button" data-nav-screen="index-rebuild">查看重建配置</button>',
    content,
    state,
    "知识管理 / 知识库 / 员工服务手册 / 索引 idx-v12",
  );
}

function renderAssistantRelease(state) {
  const generic = genericState("assistant-release", state);
  const assistant = fixtures.assistantVersion;
  const blocked = state === "disabled" || state === "partial";
  const quality =
    state === "disabled"
      ? {
          ...assistant.quality,
          citationPrecision: 0.91,
          unauthorizedLeakage: 1,
        }
      : assistant.quality;
  const content =
    generic ||
    [
      '<div class="inline-note' +
        (blocked ? " warning" : "") +
        '">' +
        (state === "partial"
          ? "冻结评测已完成 84 / 100 题。结果未冻结前不能形成发布决定。"
          : state === "disabled"
            ? "发现 1 个无授权样本返回了引用，安全门禁失败。发布按钮保持禁用。"
            : "所有 M6 质量门禁通过。发布只影响新会话，已有会话继续固定原版本。") +
        "</div>",
      '<section class="section-block"><div class="section-heading"><div><p class="eyebrow">发布结论</p><h2>' +
        (blocked ? "暂不能发布 v13" : "v13 可以发布") +
        "</h2></div>" +
        statusTag(blocked ? "BUILDING" : "READY_FOR_RELEASE") +
        "</div>",
      qualityGrid(quality, true),
      "</section>",
      '<div class="split-detail"><div>',
      '<section class="section-block"><h2>冻结配置</h2><dl class="description-list"><dt>知识库</dt><dd>' +
        escapeHtml(assistant.knowledgeBase) +
        ' · 永久绑定</dd><dt>索引</dt><dd><span class="mono">' +
        escapeHtml(assistant.indexVersion) +
        "</span></dd><dt>Chat</dt><dd>" +
        escapeHtml(assistant.chatModel) +
        "</dd><dt>Embedding</dt><dd>" +
        escapeHtml(assistant.embeddingModel) +
        "</dd><dt>Rerank</dt><dd>" +
        escapeHtml(assistant.rerankModel) +
        '</dd><dt>检索策略</dt><dd><span class="mono">' +
        escapeHtml(assistant.retrievalPolicy) +
        '</span></dd><dt>引用策略</dt><dd><span class="mono">' +
        escapeHtml(assistant.citationPolicy) +
        '</span></dd><dt>拒答策略</dt><dd><span class="mono">' +
        escapeHtml(assistant.refusalPolicy) +
        "</span></dd></dl></section>",
      '</div><aside><section class="release-decision' +
        (blocked ? " blocked" : "") +
        '"><p class="eyebrow">唯一主决策</p><h2>' +
        (blocked ? "先关闭阻断项" : "发布员工服务助手 v13") +
        "</h2><p>" +
        (blocked
          ? "安全门禁不能被产品进度或人工例外绕过。修复后重新运行冻结评测。"
          : "发布后，新会话固定 v13。旧会话显示升级提示，但不会静默改变答案基础。") +
        '</p><button class="button primary" type="button" data-confirm-release' +
        (blocked ? " disabled" : "") +
        ">确认发布</button></section></aside></div>",
      '<dialog id="release-dialog" class="dialog-surface"><form method="dialog"><p class="eyebrow">发布确认</p><h2>发布员工服务助手 v13？</h2><p>新会话将固定 asv-0013 和 idx-v12。旧会话继续使用旧版本，授权撤销仍立即失败关闭。</p><div class="release-actions"><button class="button secondary" value="cancel">取消</button><button class="button primary" value="confirm">发布版本</button></div></form></dialog>',
    ].join("");
  return adminShell(
    "assistant-release",
    "发布员工服务助手 v13",
    "核对冻结版本、质量门禁和会话影响，只形成一个可审计的发布决策。",
    '<button class="button secondary" type="button" data-nav-screen="assistant-evaluation">查看完整评测</button>',
    content,
    state,
    "AI 应用 / 助手 / 员工服务助手 / v13 / 发布",
  );
}

function bindInteractions(screen) {
  root.querySelectorAll("[data-nav-screen]").forEach((element) => {
    element.addEventListener("click", () => {
      navigate(
        element.dataset.navScreen,
        element.dataset.navState || "success",
      );
    });
  });
  root.querySelector("#login-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    navigate("assistants");
  });
  root
    .querySelector("#assistant-search")
    ?.addEventListener("input", (event) => {
      filterAssistants(event.currentTarget.value);
    });
  root
    .querySelector("[data-clear-assistant-search]")
    ?.addEventListener("click", () => {
      const input = root.querySelector("#assistant-search");
      input.value = "";
      input.focus();
      filterAssistants("");
    });
  root.querySelectorAll("[data-assistant-id]").forEach((element) => {
    element.addEventListener("click", () => {
      selectedAssistantId = element.dataset.assistantId;
      navigate("conversation");
    });
    element.addEventListener("keydown", moveAssistantFocus);
  });
  root.querySelectorAll("[data-conversation-id]").forEach((element) => {
    element.addEventListener("click", () => {
      activeConversationId = element.dataset.conversationId;
      render();
      closeHistory();
    });
  });
  root
    .querySelector("[data-open-history]")
    ?.addEventListener("click", openHistory);
  root.querySelectorAll("[data-close-history]").forEach((element) => {
    element.addEventListener("click", closeHistory);
  });
  root.querySelectorAll("[data-citation-id]").forEach((element) => {
    element.addEventListener("click", () => toggleCitation(element));
  });
  root
    .querySelector("[data-load-older]")
    ?.addEventListener("click", (event) => {
      event.currentTarget.textContent = "已加载到会话起点 · sequence=1";
      event.currentTarget.disabled = true;
    });
  root
    .querySelector("#composer-form")
    ?.addEventListener("submit", submitQuestion);
  root
    .querySelector("#question-input")
    ?.addEventListener("keydown", (event) => {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        event.currentTarget.form?.requestSubmit();
      }
    });
  root.querySelector("[data-confirm-index]")?.addEventListener("click", () => {
    root.querySelector("#index-dialog")?.showModal();
  });
  root
    .querySelector("[data-confirm-release]")
    ?.addEventListener("click", () => {
      root.querySelector("#release-dialog")?.showModal();
    });
  root
    .querySelector('#release-dialog button[value="confirm"]')
    ?.addEventListener("click", () => {
      window.setTimeout(() => navigate("channel-binding"), 0);
    });
  root.querySelector("[data-open-page-dialog]")?.addEventListener("click", () => {
    root.querySelector("#page-dialog")?.showModal();
  });
  root.querySelector("#console-login-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    navigate("platform-overview");
  });
}

function filterAssistants(query) {
  const normalized = query.trim().toLocaleLowerCase("zh-CN");
  root.querySelectorAll("[data-assistant-id]").forEach((element) => {
    element.hidden =
      normalized.length > 0 &&
      !element.textContent.toLocaleLowerCase("zh-CN").includes(normalized);
  });
}

function moveAssistantFocus(event) {
  if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
    return;
  }
  event.preventDefault();
  const options = [
    ...root.querySelectorAll(
      "[data-assistant-id]:not([hidden]):not(:disabled)",
    ),
  ];
  const currentIndex = options.indexOf(event.currentTarget);
  const nextIndex =
    event.key === "Home"
      ? 0
      : event.key === "End"
        ? options.length - 1
        : event.key === "ArrowDown"
          ? Math.min(currentIndex + 1, options.length - 1)
          : Math.max(currentIndex - 1, 0);
  options[nextIndex]?.focus();
}

function openHistory() {
  document.body.dataset.historyOpen = "true";
  const trigger = root.querySelector("[data-open-history]");
  trigger?.setAttribute("aria-expanded", "true");
  window.setTimeout(
    () => root.querySelector("[data-close-history]")?.focus(),
    0,
  );
}

function closeHistory() {
  document.body.dataset.historyOpen = "false";
  const trigger = root.querySelector("[data-open-history]");
  trigger?.setAttribute("aria-expanded", "false");
  trigger?.focus();
}

function toggleCitation(button) {
  const excerpt = root.querySelector(
    '[data-citation-excerpt="' + CSS.escape(button.dataset.citationId) + '"]',
  );
  const willOpen = excerpt?.hidden ?? false;
  if (excerpt) {
    excerpt.hidden = !willOpen;
  }
  button.setAttribute("aria-expanded", String(willOpen));
  button.textContent = willOpen ? "收起引用" : "查看授权引用";
}

function submitQuestion(event) {
  event.preventDefault();
  const input = root.querySelector("#question-input");
  const question = input?.value.trim();
  if (!question) {
    input?.focus();
    return;
  }
  const messages = root.querySelector("#messages");
  const sendButton = event.currentTarget.querySelector('button[type="submit"]');
  input.disabled = true;
  sendButton.disabled = true;
  messages.insertAdjacentHTML(
    "beforeend",
    [
      '<article class="message user" data-sequence="44">',
      '<div class="message-meta"><strong>你</strong><span>事件 44 · 刚刚</span></div>',
      '<div class="message-body"><p>' + escapeHtml(question) + "</p></div>",
      "</article>",
      '<article class="message assistant" data-live-answer>',
      '<div class="message-meta"><strong>员工服务助手</strong><span>正在回答</span></div>',
      '<div class="message-body"><p class="streaming-cursor" data-live-text></p></div>',
      '<p class="query-stage" aria-live="polite">RUN_ACCEPTED · 正在检索授权证据</p>',
      "</article>",
    ].join(""),
  );
  input.value = "";
  const answer =
    "未休 7 天时，最多结转 5 天。其余 2 天是否可以补偿，需要根据适用制度和审批结果判断；当前证据不足以给出统一结论。";
  const target = root.querySelector("[data-live-text]");
  const stage = root.querySelector("[data-live-answer] .query-stage");
  let offset = 0;
  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const step = reduced ? answer.length : 4;
  streamTimer = window.setInterval(
    () => {
      offset = Math.min(answer.length, offset + step);
      target.textContent = answer.slice(0, offset);
      stage.textContent =
        offset < answer.length
          ? "TOKEN_DELTA · 已持久化事件 45"
          : "COMPLETED · 最终答案与引用已原子提交";
      if (offset >= answer.length) {
        window.clearInterval(streamTimer);
        target.classList.remove("streaming-cursor");
        input.disabled = false;
        sendButton.disabled = false;
        input.focus();
      }
    },
    reduced ? 1 : 100,
  );
}
