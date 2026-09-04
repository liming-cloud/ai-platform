import { decorateExistingPage, renderInventoryPage } from "./full-platform.js";
import { renderConsoleNavigation } from "./navigation.js";
import {
  createPrototypeStore,
  DEFAULT_TENANT_ID,
  PROTOTYPE_STATE_KEY,
  QUERY_SESSION_KEY,
} from "./prototype-state.js";

const root = document.querySelector("#prototype-root");
const reviewer = document.querySelector("#prototype-reviewer");
const screenSelect = document.querySelector("#review-screen");
const stateSelect = document.querySelector("#review-state");
const applyReview = document.querySelector("#review-apply");
const resetReview = document.querySelector("#review-reset");
const reviewSearch = document.querySelector("#review-search");
const reviewContract = document.querySelector("#review-contract");
const reviewQueryTools = document.querySelector("#review-query-tools");
const reviewDemoAccount = document.querySelector("#review-demo-account");
const reviewQueryMode = document.querySelector("#review-query-mode");

let screens = new Set();
let states = new Set();
let inventory;
let pageFixtures;
let pageByScreen;
let groupByScreen;

let fixtures;
let selectedAssistantId = "assistant-employee-service";
let activeConversationId = "";
let streamTimer;
let streamGeneration = 0;
let loginFeedback = "";
let queryFeedback = "";
let accountFeedback = "";
let knowledgeFeedback = "";
let indexFeedback = "";
let nextQueryMode = "normal";
let queryStreamingSuspended = false;
const PLATFORM_ACCOUNT_ACTOR_ID = "account-platform-admin";
const TENANT_ADMIN_ACTOR_ID = "account-qiao";
const prototypeStore = createPrototypeStore();

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

resetReview.addEventListener("click", () => {
  cancelQueryTimer();
  prototypeStore.reset();
  activeConversationId = "";
  loginFeedback = "";
  queryFeedback = "";
  accountFeedback = "";
  nextQueryMode = "normal";
  render();
});

reviewQueryMode.addEventListener("change", () => {
  nextQueryMode = reviewQueryMode.value;
});

reviewDemoAccount.addEventListener("change", () => {
  cancelQueryTimer();
  prototypeStore.selectDemoAccount(reviewDemoAccount.value);
  activeConversationId = "";
  queryFeedback = "演示身份已切换，请重新登录。";
  navigate("login");
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
window.addEventListener("storage", (event) => {
  if (![PROTOTYPE_STATE_KEY, QUERY_SESSION_KEY, null].includes(event.key)) {
    return;
  }
  cancelQueryTimer();
  prototypeStore.reloadFromStorage();
  render();
});
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
    conversationId: params.get("conversationId") || "",
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

function updateReviewerContext(screen) {
  const page = pageByScreen.get(screen);
  const group = groupByScreen.get(screen);
  if (!page || !group) {
    return;
  }
  const authorization =
    group.phase === "M7" || group.phase === "M8"
      ? `<div class="review-authorization"><strong>${escapeHtml(group.phase)} 原型前置，不代表实现授权</strong><span>本候选只用于产品与视觉评审。</span></div>`
      : '<span class="review-boundary">当前里程碑授权范围内的原型页面</span>';
  reviewContract.innerHTML = [
    `<header><span>${escapeHtml(group.phase)} / ${escapeHtml(group.label)}</span><strong>${escapeHtml(pageFixtures.pages[screen]?.title || page.task)}</strong></header>`,
    '<dl>',
    `<div><dt>目标路径</dt><dd>${escapeHtml(page.route)}</dd></div>`,
    `<div><dt>唯一任务</dt><dd>${escapeHtml(page.task)}</dd></div>`,
    `<div><dt>主要操作</dt><dd>${escapeHtml(page.primaryAction)}</dd></div>`,
    "</dl>",
    authorization,
  ].join("");
  const queryScreen = ["login", "assistants", "conversation"].includes(screen);
  reviewQueryTools.hidden = !queryScreen;
  if (queryScreen) {
    const snapshot = prototypeStore.getSnapshot();
    const tenant = snapshot.tenants.find((item) => item.id === DEFAULT_TENANT_ID);
    reviewDemoAccount.innerHTML = (tenant?.members || [])
      .filter((member) => member.role === "ordinaryMember")
      .map((member) => {
        const account = snapshot.accounts.find(
          (item) => item.id === member.accountId,
        );
        return `<option value="${escapeHtml(member.accountId)}"${member.accountId === snapshot.selectedDemoAccountId ? " selected" : ""}>${escapeHtml(account?.name || "合成成员")}</option>`;
      })
      .join("");
    reviewQueryMode.value = nextQueryMode;
  }
}

function filterReviewerOptions(event) {
  const query = event.currentTarget.value.trim().toLocaleLowerCase("zh-CN");
  [...screenSelect.options].forEach((option) => {
    option.hidden =
      query.length > 0 &&
      !option.textContent.toLocaleLowerCase("zh-CN").includes(query);
  });
}

function navigate(screen, state = "success", options = {}) {
  const next = new URL(window.location.href);
  next.searchParams.set("screen", screens.has(screen) ? screen : "login");
  next.searchParams.set("state", states.has(state) ? state : "success");
  if (screen === "conversation" && options.conversationId) {
    next.searchParams.set("conversationId", options.conversationId);
  } else if (screen !== "conversation") {
    next.searchParams.delete("conversationId");
  }
  window.history.pushState({}, "", next);
  render();
}

function render() {
  if (!fixtures) {
    return;
  }
  cancelQueryTimer();
  const scenario = currentScenario();
  const querySession = prototypeStore.getQuerySession();
  const protectedQueryScreen = ["assistants", "conversation"].includes(
    scenario.screen,
  );
  const screen =
    protectedQueryScreen && !querySession.authenticated
      ? "login"
      : scenario.screen;
  if (screen === "conversation") {
    activeConversationId =
      scenario.conversationId ||
      prototypeStore.getSnapshot().query.currentConversationId ||
      "";
  }
  const demoState = prototypeStore.getSnapshot();
  const access = prototypeStore.authorizeScreen(
    screen,
    DEFAULT_TENANT_ID,
    querySession.accountId || demoState.selectedDemoAccountId,
  );
  const objectAllowed = screen !== "conversation" || prototypeStore.queryProjection(fixtures, {
    assistantId: selectedAssistantId,
    conversationId: activeConversationId,
  }).conversationAllowed;
  const renderedState =
    (!access.allowed || !objectAllowed) && ["success", "partial", "disabled"].includes(scenario.state)
      ? "denied"
      : scenario.state;
  document.body.dataset.screen = screen;
  document.body.dataset.state = renderedState;
  document.body.dataset.review = scenario.review ? "visible" : "hidden";
  document.body.dataset.historyOpen = "false";
  reviewer.hidden = !scenario.review;
  screenSelect.value = scenario.screen;
  stateSelect.value = scenario.state;
  updateReviewerContext(scenario.screen);
  const renderedDemoState =
    screen === "platform-tenant-detail"
      ? {
          ...demoState,
          tenantQuotaProjection: prototypeStore.getTenantQuotas(
            demoState.selectedTenantId || DEFAULT_TENANT_ID,
          ),
        }
      : screen === "platform-accounts"
        ? { ...demoState, accountFeedback }
        : demoState;
  root.innerHTML = renderScreen(screen, renderedState, renderedDemoState);
  const page = pageByScreen.get(screen);
  decorateExistingPage(
    root.querySelector(`main[data-screen="${CSS.escape(screen)}"]`),
    page,
    groupByScreen.get(screen),
  );
  root
    .querySelectorAll("button, input, textarea, select, dialog")
    .forEach((control, index) => {
      if (!control.dataset.odId) {
        control.dataset.odId = `${screen}-control-${index + 1}`;
      }
    });
  bindInteractions(screen);
  if (screen === "conversation" && renderedState === "success") {
    scheduleQueryContinuation();
  }
  document.title = screenTitle(screen) + " · 可信 RAG 原型";
}

function renderScreen(screen, state, demoState) {
  switch (screen) {
    case "login":
      return renderLogin(state);
    case "assistants":
      return renderAssistants(state);
    case "conversation":
      return renderConversation(state);
    case "knowledge-directory":
      return renderKnowledgeDirectory(state, demoState);
    case "knowledge-sources":
      return renderKnowledgeSources(state, demoState);
    case "source-add":
      return renderSourceAdd(state, demoState);
    case "source-detail":
      return renderSourceDetail(state, demoState);
    case "index-versions":
      return renderIndexVersions(state, demoState);
    case "index-rebuild":
      return renderIndexRebuild(state, demoState);
    case "index-detail":
      return renderIndexDetail(state, demoState);
    case "assistant-release":
      return renderAssistantRelease(state);
    default:
      return renderInventoryPage(
        pageByScreen.get(screen),
        groupByScreen.get(screen),
        pageFixtures.pages[screen],
        state,
        fixtures.tenant,
        demoState,
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
    PROCESSING: ["处理中", "warning"],
    EMPTY: ["待添加资料", "info"],
    FAILED: ["失败", "danger"],
    BUILDING: ["构建中", "warning"],
    EVALUATING: ["评测中", "warning"],
    ACTIVE: ["当前活动", "success"],
    BLOCKED: ["门禁未通过", "danger"],
    SUPERSEDED: ["已保留", "info"],
    CANCELLED: ["已取消", "info"],
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
      '<span class="section-kicker">身份验证</span>',
      "<h2>使用组织账户登录</h2>",
      "<p>登录后只显示已授权助手。</p>",
      "</div>",
      '<div class="demo-credentials" aria-label="公开合成演示账号"><span>合成演示账号</span><code>演示口令：demo-only</code></div>',
      state === "partial"
        ? '<div class="inline-note warning">上次会话已退出，未发送的输入没有保存。可以重新登录。</div>'
        : "",
      loginFeedback
        ? `<p id="login-feedback" class="form-feedback" data-tone="danger" role="alert">${escapeHtml(loginFeedback)}</p>`
        : '<p id="login-feedback" class="form-feedback" aria-live="polite"></p>',
      '<label class="field"><span>组织编码</span><input class="input" name="tenantCode" autocomplete="organization" value="spruce-demo" required /></label>',
      '<label class="field"><span>登录名</span><input class="input" name="username" autocomplete="username" value="demo.user" required /></label>',
      '<label class="field"><span>密码</span><input class="input" name="password" type="password" autocomplete="current-password" required /></label>',
      '<div class="login-actions">',
      '<button class="button primary" type="submit" data-primary-action' +
        (state === "disabled"
          ? ' disabled aria-describedby="login-disabled-reason"'
          : "") +
        ">登录</button>",
      state === "disabled"
        ? '<p id="login-disabled-reason" class="field-hint">当前账户暂时无法使用问答，请联系组织管理员。</p>'
        : '<p class="field-hint">无法登录时，请联系组织管理员。</p>',
      "</div>",
      '<p class="session-safety">请勿在公共设备上保存密码。</p>',
      "</form>",
    ].join("");
  return [
    '<main id="main-content" class="login-shell" data-screen="login" data-state="' +
      state +
      '" tabindex="-1">',
    '<section class="login-story" aria-label="普通用户访问范围">',
    '<div class="brand"><span class="brand-mark" aria-hidden="true">R</span><span class="brand-copy"><strong>可信 RAG</strong><small>普通用户工作区</small></span></div>',
    '<div class="login-story-main">',
    '<span class="section-kicker">普通用户问答</span>',
    "<h1>登录可信问答</h1>",
    "<p>回答附带可核验引用，证据不足时明确拒答。</p>",
    '<dl class="login-boundary-list"><div><dt>访问范围</dt><dd>当前成员已授权知识</dd></div><div><dt>会话基础</dt><dd>固定助手与索引版本</dd></div></dl>',
    "</div>",
    '<p class="login-story-footer">仅供组织成员使用。</p>',
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
    '<div class="topbar-actions"><button class="button subtle account-button" type="button" data-query-logout>退出登录</button></div>',
    "</header>",
  ].join("");
}

function demoAccountControl() {
  const snapshot = prototypeStore.getSnapshot();
  const tenant = snapshot.tenants.find((item) => item.id === DEFAULT_TENANT_ID);
  const members = (tenant?.members || []).filter(
    (member) => member.role === "ordinaryMember",
  );
  return `<label class="demo-account-control"><span>演示成员</span><select class="select" data-demo-account aria-label="切换普通成员演示">${members
    .map(
      (member) =>
        `<option value="${escapeHtml(member.accountId)}"${member.accountId === snapshot.selectedDemoAccountId ? " selected" : ""}>${escapeHtml(snapshot.accounts.find((account) => account.id === member.accountId)?.name || "合成成员")}</option>`,
    )
    .join("")}</select></label>`;
}

function renderAssistants(state) {
  const availableAssistants = prototypeStore.queryProjection(fixtures).assistants;
  if (!availableAssistants.some((assistant) => assistant.id === selectedAssistantId) && availableAssistants[0]) {
    selectedAssistantId = availableAssistants[0].id;
  }
  const generic = genericState("assistants", state);
  const rows = availableAssistants
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
      availableAssistants.length
        ? '<div id="assistant-list" class="assistant-list" role="listbox" aria-label="已授权助手">' +
          rows +
          "</div>"
        : '<section class="state-panel" data-kind="empty"><h2>当前成员没有已授权助手</h2><p>租户管理员可为普通成员选择可用助手范围。</p></section>',
      '<div class="pagination" aria-label="助手目录分页">',
      `<span>第 1 页，共 ${availableAssistants.length} 个已授权助手</span>`,
      '<div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div>',
      "</div>",
    ].join("");
  return [
    '<div class="app-shell" data-state="' + state + '">',
    appTopbar(),
    '<main id="main-content" class="assistant-page" data-screen="assistants" data-state="' +
      state +
      '" tabindex="-1">',
    '<header class="assistant-page-header"><div><span class="section-kicker">已授权助手</span><h1>选择助手</h1><p>选择后立即创建一条固定版本的空会话。</p></div>',
    "</header>",
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
      " · 索引 " +
      escapeHtml(assistant.indexVersionId || "—") +
      "</span></span>",
    statusTag(assistant.status),
    "</button>",
  ].join("");
}

function renderConversation(state) {
  const projection = prototypeStore.queryProjection(fixtures, {
    conversationId: activeConversationId,
  });
  if (projection.conversation?.assistantId) {
    selectedAssistantId = projection.conversation.assistantId;
  }
  const historyAllowed = projection.history.length > 0;
  const assistant =
    fixtures.assistants.find((item) => item.id === selectedAssistantId) || {};
  const history =
    state === "denied" || !historyAllowed
      ? '<p class="field-hint">暂无可查看的会话记录。</p>'
      : projection.history
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
    '<div class="history-search"><label class="field"><span class="field-label">搜索会话</span><input class="input" type="search" placeholder="按标题搜索" data-history-search /></label></div>',
    '<nav class="conversation-list" aria-label="历史会话">' +
      history +
      "</nav>",
    '<footer class="history-footer"><button class="button secondary" type="button" data-nav-screen="assistants">选择其他助手</button><small>仅你可查看自己的会话记录。</small></footer>',
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
          " · 助手 " +
          escapeHtml(projection.conversation?.assistantVersion || assistant.version) +
          " · 索引 " +
          escapeHtml(projection.conversation?.indexVersionId || assistant.indexVersionId || "—")) +
      "</small></div>",
    '<div class="conversation-header-actions">' +
      '<button class="button secondary compact mobile-history-button" type="button" aria-controls="history-panel" aria-expanded="false" data-open-history' +
      (historyAllowed ? "" : " disabled") +
      '>会话记录</button><button class="button subtle compact" type="button" data-new-conversation>新会话</button><button class="button subtle compact" type="button" data-query-logout>退出</button></div>',
    "</header>",
    queryFeedback
      ? `<div class="conversation-feedback inline-note warning" role="status">${escapeHtml(queryFeedback)}</div>`
      : "",
    renderMessageRegion(state, projection),
    renderComposer(state, projection.activeRun),
    "</section>",
    "</main>",
  ].join("");
}

function renderMessageRegion(state, projection) {
  const projectedMessages = projection.messages || [];
  const generic = genericState("conversation", state);
  if (generic) {
    return (
      '<section class="message-region" aria-live="polite">' +
      generic +
      "</section>"
    );
  }
  if (projectedMessages.length === 0 && state === "success") {
    return '<section class="message-region"><div id="messages" class="messages" aria-live="polite"><section class="conversation-empty"><span class="section-kicker">新会话</span><h2>从一个明确问题开始</h2><p>回答只使用此会话固定的助手与索引版本；证据不足时会明确拒答。</p></section></div></section>';
  }
  const messages = projectedMessages
    .map((message) => renderMessage(message))
    .join("");
  const receiving =
    projection.activeRun?.status === "RECEIVING" &&
    !projectedMessages.some(
      (message) => message.runId === projection.activeRun.id,
    )
      ? '<article class="message assistant run-status" data-run-state="RECEIVING"><div class="message-meta"><strong>员工服务助手</strong><span>正在回答</span></div><p class="query-stage" aria-live="polite">正在检索授权证据并保存接收进度</p><button class="button secondary compact" type="button" data-stop-run>停止生成</button></article>'
      : "";
  const recovery = renderRunRecovery(projection.activeRun);
  const disabled =
    state === "disabled"
      ? '<div class="inline-note warning">助手已发布 v13。当前会话仍固定 v12；如需使用新版本，请明确开启新会话。</div>'
      : "";
  return [
    '<section class="message-region">',
    projection.canLoadEarlier
      ? '<div class="older-messages"><button class="button subtle compact" type="button" data-load-older>加载事件 41 之前的消息</button></div>'
      : "",
    state === "partial"
      ? '<div class="inline-note warning">连接曾短暂中断，已从最后确认的事件序号 44 恢复；已完成消息不会重复追加。</div>'
      : "",
    disabled,
    '<div id="messages" class="messages" aria-live="polite">' +
      messages +
      receiving +
      recovery +
      "</div>",
    "</section>",
  ].join("");
}

function renderRunRecovery(run) {
  if (!run || run.status === "RECEIVING") {
    return "";
  }
  const copy = {
    PAUSED: ["接收已暂停", "已保存当前偏移，可从这里继续。", "继续接收", "resume"],
    FAILED: ["本轮接收失败", "问题只记录一次；重试会沿用同一轮次。", "重试本轮", "retry"],
    STOPPED: ["已停止生成", "已保存当前内容，可选择继续。", "继续生成", "resume"],
  }[run.status];
  if (!copy) {
    return "";
  }
  return `<section class="run-recovery" data-run-state="${escapeHtml(run.status)}"><div><strong>${copy[0]}</strong><p>${copy[1]}</p></div><button class="button secondary" type="button" data-run-action="${copy[3]}" data-query-${copy[3]}>${copy[2]}</button></section>`;
}

function renderMessage(message, citationsAllowed = true) {
  const citations = message.citations?.length && citationsAllowed
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
      '"' +
      (message.runState
        ? ' data-run-state="' + escapeHtml(message.runState) + '"'
        : "") +
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
      (message.runState === "RECEIVING"
        ? '<span class="streaming-cursor" aria-hidden="true"></span>'
        : "") +
      "</p></div>",
    citations,
    message.runState === "RECEIVING"
      ? '<div class="message-run-footer"><p class="query-stage" aria-live="polite">接收中 · 已保存当前偏移</p><button class="button secondary compact" type="button" data-stop-run>停止生成</button></div>'
      : "",
    "</article>",
  ].join("");
}

function renderComposer(state, activeRun) {
  const blocked =
    state === "disabled" ||
    state === "denied" ||
    state === "loading" ||
    Boolean(activeRun);
  return [
    '<footer class="composer-shell">',
    '<form id="composer-form" class="composer">',
    '<label class="field"><span class="field-label">继续提问</span><textarea id="question-input" class="textarea" rows="2" maxlength="2000" placeholder="基于当前已授权知识继续提问"' +
      (blocked ? " disabled" : "") +
      "></textarea></label>",
    '<button class="button primary" type="submit" data-primary-action' +
      (blocked ? " disabled" : "") +
      ">发送问题</button>",
    "</form>",
    '<p class="composer-hint">Enter 发送，Shift+Enter 换行。请勿输入敏感信息。</p>',
    "</footer>",
  ].join("");
}

function adminShell(active, title, summary, action, body, state, breadcrumb) {
  const demoState = prototypeStore.getSnapshot();
  return [
    '<div class="admin-shell" data-state="' + state + '">',
    '<aside class="admin-sidebar">',
    '<div class="brand"><span class="brand-mark" aria-hidden="true">R</span><span class="brand-copy"><strong>可信 RAG</strong><small>租户工作区</small></span></div>',
    renderConsoleNavigation({
      screen: active,
      scope: "tenant",
      snapshot: demoState,
      tenantLabel: fixtures.tenant.displayName,
    }),
    '<div class="admin-account"><strong>' +
      escapeHtml(fixtures.tenant.administratorLabel) +
      "</strong><small>" +
      escapeHtml(fixtures.tenant.displayName) +
      "</small></div>",
    "</aside>",
    '<div class="admin-workspace">',
    '<header class="topbar"><span class="topbar-context">' +
      escapeHtml(fixtures.tenant.displayName) +
      ' / 租户管理</span><div class="topbar-actions">' +
      demoAccountControl() +
      '<button class="button subtle account-button" type="button">租户管理员 / 账户菜单</button></div></header>',
    '<main id="main-content" class="admin-main" data-screen="' +
      active +
      '" data-state="' +
      state +
      '" tabindex="-1">',
    '<nav class="breadcrumb" aria-label="当前位置">' +
      escapeHtml(breadcrumb.split(" / ")[0]) +
      "</nav>",
    '<header class="page-header"><div class="page-header-copy"><h1>' +
      escapeHtml(title) +
      '</h1><div class="page-object-line"><span>' +
      escapeHtml(pageStatus(active, state)) +
      '</span></div></div>' +
      action +
      "</header>",
    '<div class="page-content">' + body + "</div>",
    "</main>",
    "</div>",
    "</div>",
  ].join("");
}

function pageStatus(active, state) {
  if (state !== "success") {
    return { loading: "正在载入", empty: "暂无记录", error: "载入失败", denied: "访问受限", partial: "部分完成", disabled: "暂不可用" }[state] || "";
  }
  const snapshot = prototypeStore.getSnapshot();
  const knowledgeBases = snapshot.knowledgeBases.filter(item => item.tenantId === DEFAULT_TENANT_ID);
  const knowledgeBase = selectedKnowledgeBase(snapshot);
  const indexVersion = selectedIndexVersion(snapshot);
  return {
    "knowledge-directory": `${knowledgeBases.length} 个知识库 / ${knowledgeBases.filter(item => !["READY", "EMPTY"].includes(item.status)).length} 项需处理`,
    "knowledge-sources": `${knowledgeBase?.name || "当前知识库"} / ${knowledgeBase?.sources.length || 0} 个来源`,
    "index-versions": `${knowledgeBase?.activeIndex || "尚无活动索引"} / ${knowledgeBase?.indexVersions.length || 0} 个版本`,
    "index-rebuild": `${knowledgeBase?.name || "当前知识库"} / 新建独立版本`,
    "index-detail": `${indexVersion?.id || "未选择版本"} / ${indexStatusLabel(indexVersion?.status)}`,
    "assistant-release": "员工服务助手 v13 / 发布门禁通过",
  }[active] || "当前工作区";
}

function selectedKnowledgeBase(demoState) {
  return demoState.knowledgeBases.find(
    item => item.id === demoState.selectedKnowledgeBaseId,
  ) || demoState.knowledgeBases.find(item => item.id === "kb-handbook") || demoState.knowledgeBases[0];
}

function formatStorage(storageGb) {
  return storageGb >= 1
    ? `${storageGb.toFixed(1)} GB`
    : `${Math.round(storageGb * 1000)} MB`;
}

function formatRevision(revision) {
  const number = Number(String(revision).replace(/^rev-/, ""));
  return Number.isSafeInteger(number) ? `v${number}` : String(revision);
}

function renderKnowledgeDirectory(state, demoState) {
  const generic = genericState("knowledge-directory", state);
  const tenant = demoState.tenants.find(item => item.id === DEFAULT_TENANT_ID);
  const knowledgeBases = demoState.knowledgeBases.filter(
    item => item.tenantId === DEFAULT_TENANT_ID,
  );
  const quota = tenant?.quotas.knowledgeBases || 0;
  const quotaReached = knowledgeBases.length >= quota;
  const rows = knowledgeBases
    .map((item) =>
      [
        `<tr data-kb-row="${escapeHtml(item.id)}" data-directory-row>`,
        '<td data-label="知识库"><span class="primary-cell"><strong>' +
          escapeHtml(item.name) +
          "</strong><span>" +
          escapeHtml(item.description) +
          "</span></span></td>",
        '<td data-label="状态">' + statusTag(item.status) + "</td>",
        '<td data-label="有效文档">' + escapeHtml(item.documents) + "</td>",
        '<td data-label="原始内容">' + escapeHtml(formatStorage(item.storageGb)) + "</td>",
        '<td data-label="活动索引"><span class="mono">' +
          escapeHtml(item.activeIndex || "尚未建立") +
          "</span></td>",
        '<td data-label="最近变化">' + escapeHtml(item.updatedAt) + "</td>",
        `<td data-label="操作"><div class="table-actions"><button class="button secondary compact" type="button" data-select-kb="${escapeHtml(item.id)}" data-nav-screen="${item.sources.length ? "knowledge-overview" : "knowledge-sources"}">进入</button></div></td>`,
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
      state === "disabled" || quotaReached
        ? `<div class="inline-note warning">当前租户已使用 ${knowledgeBases.length} / ${quota} 个知识库配额。现有知识库仍可维护。</div>`
        : "",
      `<dl class="summary-strip"><div class="summary-item"><dt>知识库</dt><dd>${knowledgeBases.length} / ${quota}</dd></div><div class="summary-item"><dt>有效文档</dt><dd>${knowledgeBases.reduce((sum, item) => sum + item.documents, 0)}</dd></div><div class="summary-item"><dt>原始内容</dt><dd>${formatStorage(knowledgeBases.reduce((sum, item) => sum + item.storageGb, 0))}</dd></div><div class="summary-item"><dt>需处理</dt><dd>${knowledgeBases.filter(item => !["READY", "EMPTY"].includes(item.status)).length}</dd></div></dl>`,
      '<div class="toolbar"><label class="field"><span>搜索知识库</span><input class="input" type="search" data-directory-search placeholder="按名称搜索" /></label><label class="field compact-field"><span>状态</span><select class="select"><option>全部状态</option><option>需处理</option><option>已就绪</option></select></label></div>',
      '<div class="table-shell"><table class="data-table"><thead><tr><th style="width:30%">知识库</th><th>状态</th><th>有效文档</th><th>原始内容</th><th>活动索引</th><th>最近变化</th><th style="text-align:right">操作</th></tr></thead><tbody>' +
        rows +
        "</tbody></table>",
      `<div class="pagination" aria-label="知识库目录分页"><span>第 1 页，共 ${knowledgeBases.length} 个知识库</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>`,
      knowledgeFeedback ? `<p class="form-feedback" role="status">${escapeHtml(knowledgeFeedback)}</p>` : "",
    ].join("");
  const action =
    '<button class="button primary" type="button" data-open-create-knowledge' +
    (state === "disabled" || quotaReached
      ? ' disabled aria-describedby="kb-quota-reason"'
      : "") +
    ">创建知识库</button>";
  const dialog = [
    '<dialog class="dialog-surface account-dialog" id="create-knowledge-base-dialog" aria-labelledby="create-knowledge-base-title">',
    '<form id="create-knowledge-base-form" method="dialog">',
    '<header class="dialog-heading"><div><span class="section-kicker">新建知识容器</span><h2 id="create-knowledge-base-title">创建知识库</h2></div><button class="button subtle compact" type="button" data-close-knowledge-dialog aria-label="关闭">关闭</button></header>',
    '<div class="dialog-body field-stack"><label class="field"><span>知识库名称</span><input class="input" name="name" maxlength="80" autocomplete="off" required /></label><label class="field"><span>用途说明</span><textarea class="textarea" name="description" maxlength="200" rows="3" placeholder="说明主要内容和使用人群"></textarea></label><p class="inline-note">创建后先添加资料，再建立索引。当前租户还可创建 ' +
      Math.max(0, quota - knowledgeBases.length) +
      ' 个知识库。</p><p id="create-knowledge-base-feedback" class="form-feedback" role="alert"></p></div>',
    '<footer class="dialog-actions"><button class="button secondary" type="button" data-close-knowledge-dialog>取消</button><button class="button primary" type="submit">确认创建</button></footer>',
    "</form></dialog>",
  ].join("");
  return adminShell(
    "knowledge-directory",
    "知识库",
    "当前租户知识库目录。",
    action,
    content +
      (state === "disabled" || quotaReached
        ? `<p id="kb-quota-reason" class="field-hint">已达到平台配置的 ${quota} 个知识库配额。</p>`
        : "") +
      (generic ? "" : dialog),
    state,
    "知识管理 / 知识库",
  );
}

function renderKnowledgeSources(state, demoState) {
  const generic = genericState("knowledge-sources", state);
  const knowledgeBase = selectedKnowledgeBase(demoState);
  const tenant = demoState.tenants.find(item => item.id === knowledgeBase?.tenantId);
  const sourceItems = knowledgeBase?.sources || [];
  const rows = sourceItems
    .map((item) =>
      [
        `<tr data-source-row="${escapeHtml(item.id)}" data-directory-row>`,
        '<td data-label="数据来源"><span class="primary-cell"><strong>' +
          escapeHtml(item.name) +
          "</strong><span>" +
          escapeHtml(item.kind) +
          " · " +
          "版本 " +
          escapeHtml(formatRevision(item.revision)) +
          "</span></span></td>",
        '<td data-label="处理阶段"><span class="mono">' +
          escapeHtml(item.stage) +
          "</span></td>",
        '<td data-label="状态">' + statusTag(item.stage === "FAILED" ? "FAILED" : item.stage === "READY" ? "READY" : "PROCESSING") + "</td>",
        '<td data-label="内容范围">' + escapeHtml(item.coverage || "待处理") + "</td>",
        '<td data-label="最近变化">' + escapeHtml(item.updatedAt || "刚刚") + "</td>",
        `<td data-label="操作"><div class="table-actions"><button class="button secondary compact" type="button" data-open-source data-select-source="${escapeHtml(item.id)}" data-nav-screen="source-detail">` +
          (item.status === "FAILED" ? "安全重试" : "查看") +
          "</button></div></td>",
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
      `<div class="inline-note${sourceItems.some(item => item.stage === "FAILED") ? " warning" : ""}">活动索引 ${escapeHtml(knowledgeBase?.activeIndex || "尚未建立")} 继续服务；失败或新修订不会原地修改活动索引。</div>`,
      `<dl class="summary-strip"><div class="summary-item"><dt>来源</dt><dd>${sourceItems.length}</dd></div><div class="summary-item"><dt>就绪</dt><dd>${sourceItems.filter(item => item.stage === "READY").length}</dd></div><div class="summary-item"><dt>处理中</dt><dd>${sourceItems.filter(item => !["READY", "FAILED"].includes(item.stage)).length}</dd></div><div class="summary-item"><dt>失败</dt><dd>${sourceItems.filter(item => item.stage === "FAILED").length}</dd></div></dl>`,
      '<div class="toolbar"><label class="field"><span>搜索数据来源</span><input class="input" type="search" data-directory-search placeholder="按名称或类型搜索" /></label><button class="button secondary" type="button" data-nav-screen="processing-quality">查看处理与质量</button></div>',
      '<div class="table-shell"><table class="data-table"><thead><tr><th style="width:31%">数据来源</th><th>处理阶段</th><th>状态</th><th>内容范围</th><th>最近变化</th><th style="text-align:right">操作</th></tr></thead><tbody>' +
        (rows || '<tr><td colspan="6"><div class="empty-table">尚无资料。添加粘贴文本或本地文件开始处理。</div></td></tr>') +
        "</tbody></table>",
      `<div class="pagination" aria-label="数据来源分页"><span>第 1 页，共 ${sourceItems.length} 个来源</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>`,
      '<p class="field-hint">支持：' +
        fixtures.supportedSources.map(escapeHtml).join("、") +
        `。单文件上限 ${escapeHtml(tenant?.quotas.singleFileMb || 0)} MB；粘贴文本上限 ${escapeHtml(tenant?.quotas.pastedTextMib || 0)} MiB；不接受远程 URL。</p>`,
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
    `${knowledgeBase?.name || "知识库"} · 管理粘贴文本和本地文件，观察每个来源的安全处理阶段。`,
    action,
    content +
      (state === "disabled"
        ? '<p id="source-disabled-reason" class="field-hint">全库重建正在生成清单，暂不接受并发替换。</p>'
        : ""),
    state,
    `知识管理 / 知识库 / ${knowledgeBase?.name || "当前知识库"} / 数据来源`,
  );
}

function renderSourceAdd(state, demoState) {
  const generic = genericState("source-add", state);
  const knowledgeBase = selectedKnowledgeBase(demoState);
  const tenant = demoState.tenants.find(item => item.id === knowledgeBase?.tenantId);
  const content = generic || [
    '<form id="source-add-form" class="wizard-workspace source-intake-form">',
    '<section class="section-block wizard-inputs"><div class="section-heading"><div><span class="section-kicker">资料接入</span><h2>选择一种来源方式</h2></div><span class="status-tag" data-tone="info">尚未提交</span></div>',
    '<div class="field-stack"><label class="field"><span>来源方式</span><select class="select" name="mode"><option value="file">本地文件</option><option value="paste">粘贴文本</option></select></label>',
    '<div data-file-fields><label class="field"><span>选择本地文件</span><span class="file-picker"><span class="button secondary" aria-hidden="true">选择文件</span><span data-selected-file>尚未选择文件</span><input name="file" type="file" accept=".txt,.md,.doc,.docx,.xls,.xlsx,.csv,.pdf,.png,.jpg,.jpeg,.webp" /></span></label><p class="field-hint">选择后先校验扩展名、MIME、大小和安全属性，不直接写入索引。</p></div>',
    '<div data-paste-fields hidden><label class="field"><span>资料名称</span><input class="input" name="name" maxlength="120" autocomplete="off" /></label><label class="field"><span>粘贴内容</span><textarea class="textarea" name="text" rows="8" placeholder="粘贴需要进入知识库的已授权内容"></textarea></label></div>',
    '<p id="source-add-feedback" class="form-feedback" role="alert"></p>',
    '<div class="form-actions"><button class="button secondary" type="button" data-nav-screen="knowledge-sources">取消</button><button class="button primary" type="submit" data-primary-action>开始安全扫描</button></div></div></section>',
    '<aside class="section-block wizard-review"><span class="section-kicker">提交前核对</span><dl class="description-list"><div class="description-row"><dt>知识库</dt><dd>' +
      escapeHtml(knowledgeBase?.name || "未选择") +
      '</dd></div><div class="description-row"><dt>单文件上限</dt><dd>' +
      escapeHtml(tenant?.quotas.singleFileMb || 0) +
      ' MB</dd></div><div class="description-row"><dt>粘贴上限</dt><dd>' +
      escapeHtml(tenant?.quotas.pastedTextMib || 0) +
      ' MiB</dd></div><div class="description-row"><dt>远程 URL</dt><dd>不接受</dd></div></dl><p class="inline-note">加密文件、含宏 Office、损坏压缩包和 MIME 不一致会直接拒绝。扫描通过后才开始提取。</p></aside></form>',
  ].join("");
  return adminShell(
    "source-add",
    "添加数据来源",
    `${knowledgeBase?.name || "当前知识库"} · 添加一份粘贴文本或本地文件。`,
    '<button class="button secondary" type="button" data-nav-screen="knowledge-sources">返回数据来源</button>',
    content,
    state,
    `知识管理 / 知识库 / ${knowledgeBase?.name || "当前知识库"} / 添加数据`,
  );
}

function renderSourceDetail(state, demoState) {
  const generic = genericState("source-detail", state);
  const knowledgeBase = selectedKnowledgeBase(demoState);
  const source = knowledgeBase?.sources.find(
    item => item.id === demoState.selectedKnowledgeSourceId,
  ) || knowledgeBase?.sources[0];
  if (generic || !source) {
    return adminShell(
      "source-detail",
      "文档详情",
      knowledgeBase?.name || "当前知识库",
      '<button class="button secondary" type="button" data-nav-screen="knowledge-sources">返回数据来源</button>',
      generic || statePanel("empty", "尚未选择资料", "请从数据来源目录选择一项。", "返回数据来源", "knowledge-sources"),
      state,
      `知识管理 / 知识库 / ${knowledgeBase?.name || "当前知识库"} / 文档详情`,
    );
  }
  const revision = formatRevision(source.revision);
  const processing = !["READY", "FAILED"].includes(source.stage);
  const operation = source.stage === "FAILED"
    ? '<button class="button primary" type="button" data-retry-source>安全重试</button>'
    : processing
      ? '<button class="button primary" type="button" data-advance-source>刷新处理状态</button>'
      : '<button class="button secondary" type="button" data-reprocess-source>重新处理</button>';
  const stages = ["RECEIVED", "SCANNING", "EXTRACTING", "EXTRACTED", "CHUNKING", "INDEXING", "READY"];
  const currentIndex = Math.max(0, stages.indexOf(source.stage === "FAILED" ? source.retryStage : source.stage));
  const content = [
    `<section class="section-block source-detail-card" data-source-stage="${escapeHtml(source.stage)}"><div class="section-heading"><div><span class="section-kicker">当前文档</span><h2>${escapeHtml(source.name)}</h2><p class="entity-meta">版本 ${escapeHtml(revision)} · ${escapeHtml(source.kind)}</p></div>${statusTag(source.stage === "FAILED" ? "FAILED" : source.stage === "READY" ? "READY" : "PROCESSING")}</div>`,
    `<ol class="process-steps source-process" aria-label="处理阶段">${stages.map((stage, index) => `<li data-step-state="${index < currentIndex ? "complete" : index === currentIndex ? "current" : "upcoming"}"><span>${index + 1}</span><strong>${stage}</strong></li>`).join("")}</ol>`,
    source.error ? `<div class="inline-note danger"><strong>${escapeHtml(source.error.code)}</strong> · ${escapeHtml(source.error.message)}</div>` : "",
    `<dl class="description-list"><div class="description-row"><dt>当前修订</dt><dd>${escapeHtml(revision)}</dd></div><div class="description-row"><dt>活动修订</dt><dd>${escapeHtml(source.activeRevision ? formatRevision(source.activeRevision) : "尚无")}</dd></div><div class="description-row"><dt>活动索引</dt><dd>${escapeHtml(knowledgeBase.activeIndex || "尚未建立")} 继续服务</dd></div><div class="description-row"><dt>索引写入</dt><dd>${source.indexed ? "已进入当前清单" : "尚未写入"}</dd></div></dl>`,
    `<div class="form-actions">${operation}<button class="button ${source.stage === "READY" ? "primary" : "secondary"}" type="button" data-primary-action data-nav-screen="source-add">更新文档</button><button class="button subtle" type="button" data-nav-screen="index-rebuild">全库重建</button></div>`,
    knowledgeFeedback ? `<p class="form-feedback" role="status">${escapeHtml(knowledgeFeedback)}</p>` : "",
    "</section>",
  ].join("");
  return adminShell(
    "source-detail",
    "文档详情",
    `${knowledgeBase.name} · 查看修订、处理阶段与失败恢复。`,
    '<button class="button secondary" type="button" data-nav-screen="knowledge-sources">返回数据来源</button>',
    content,
    state,
    `知识管理 / 知识库 / ${knowledgeBase.name} / ${source.name}`,
  );
}

function selectedIndexVersion(demoState) {
  const knowledgeBase = selectedKnowledgeBase(demoState);
  return knowledgeBase?.indexVersions.find(
    item => item.id === demoState.selectedIndexVersionId,
  ) || knowledgeBase?.indexVersions.find(item => item.id === knowledgeBase.activeIndex) || knowledgeBase?.indexVersions[0];
}

function indexStatusLabel(status) {
  return {
    BUILDING: "构建中",
    EVALUATING: "评测中",
    READY_FOR_REVIEW: "待激活",
    ACTIVE: "当前活动",
    BLOCKED: "门禁未通过",
    SUPERSEDED: "已保留",
    CANCELLED: "已取消",
  }[status] || "未知状态";
}

function renderIndexVersions(state, demoState) {
  const generic = genericState("index-versions", state);
  if (generic) {
    return adminShell("index-versions", "索引版本", "", "", generic, state, "知识管理 / 索引版本");
  }
  const knowledgeBase = selectedKnowledgeBase(demoState);
  const versions = knowledgeBase?.indexVersions || [];
  const rows = versions.map(indexVersion => [
    `<tr data-index-row="${escapeHtml(indexVersion.id)}" data-directory-row>`,
    `<td data-label="版本"><span class="primary-cell"><strong>${escapeHtml(indexVersion.id)}</strong><span>${escapeHtml(indexVersion.reason)}</span></span></td>`,
    `<td data-label="触发原因">${escapeHtml(indexVersion.trigger === "CONFIGURATION_CHANGE" ? "配置变化" : indexVersion.trigger === "PARSER_CHANGE" ? "解析器变化" : "手动重建")}</td>`,
    `<td data-label="文档">${escapeHtml(indexVersion.documents)}</td>`,
    `<td data-label="进度">${escapeHtml(indexVersion.progress)}%</td>`,
    `<td data-label="状态">${statusTag(indexVersion.status)}</td>`,
    `<td data-label="最近变化">${escapeHtml(indexVersion.updatedAt)}</td>`,
    `<td data-label="操作"><div class="table-actions"><button class="button secondary compact" type="button" data-select-index="${escapeHtml(indexVersion.id)}" data-nav-screen="index-detail">查看</button></div></td>`,
    "</tr>",
  ].join("")).join("");
  const content = [
    state === "partial" ? `<div class="inline-note warning">候选版本仍在构建，活动 ${escapeHtml(knowledgeBase?.activeIndex || "索引")} 继续服务。</div>` : "",
    `<dl class="summary-strip"><div class="summary-item"><dt>活动版本</dt><dd>${escapeHtml(knowledgeBase?.activeIndex || "尚无")}</dd></div><div class="summary-item"><dt>构建中</dt><dd>${versions.filter(item => ["BUILDING", "EVALUATING"].includes(item.status)).length}</dd></div><div class="summary-item"><dt>待激活</dt><dd>${versions.filter(item => item.status === "READY_FOR_REVIEW").length}</dd></div><div class="summary-item"><dt>被阻止</dt><dd>${versions.filter(item => item.status === "BLOCKED").length}</dd></div></dl>`,
    '<div class="toolbar"><label class="field"><span>搜索索引版本</span><input class="input" type="search" data-directory-search placeholder="按版本或原因搜索" /></label><button class="button secondary" type="button" data-nav-screen="processing-quality">查看处理与质量</button></div>',
    `<div class="table-shell"><table class="data-table"><thead><tr><th style="width:28%">版本</th><th>触发原因</th><th>文档</th><th>进度</th><th>状态</th><th>最近变化</th><th style="text-align:right">操作</th></tr></thead><tbody>${rows || '<tr><td colspan="7"><div class="empty-table">尚无索引版本。</div></td></tr>'}</tbody></table><div class="pagination" aria-label="索引版本分页"><span>第 1 页，共 ${versions.length} 个版本</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>`,
    indexFeedback ? `<p class="form-feedback" role="status">${escapeHtml(indexFeedback)}</p>` : "",
  ].join("");
  return adminShell(
    "index-versions",
    "索引版本",
    "",
    '<button class="button primary" type="button" data-nav-screen="index-rebuild">重建索引</button>',
    content,
    state,
    "知识管理 / 索引版本",
  );
}

function renderIndexRebuild(state, demoState) {
  const generic = genericState("index-rebuild", state);
  if (generic) {
    return adminShell("index-rebuild", "全库索引重建", "", "", generic, state, "知识管理 / 索引重建");
  }
  const knowledgeBase = selectedKnowledgeBase(demoState);
  const activeVersion = knowledgeBase?.indexVersions.find(item => item.id === knowledgeBase.activeIndex);
  const candidateConfig = knowledgeBase?.indexVersions[0]?.config || activeVersion?.config;
  const activeBuild = knowledgeBase?.indexVersions.find(item => ["BUILDING", "EVALUATING"].includes(item.status));
  const disabled = state === "disabled" || Boolean(activeBuild);
  const content = [
    disabled ? `<div class="inline-note warning">${activeBuild ? `${escapeHtml(activeBuild.id)} 正在执行，同一知识库不能并发创建第二个全库重建。` : "当前资源不足，暂不能开始新的全库重建。"}</div>` : "",
    '<form id="index-rebuild-form" class="wizard-workspace">',
    '<section class="section-block wizard-inputs"><div class="section-heading"><div><span class="section-kicker">新建候选版本</span><h2>确认重建原因</h2></div><span class="status-tag" data-tone="info">不会自动激活</span></div>',
    '<div class="field-stack"><label class="field"><span>重建原因</span><select class="select" name="trigger"><option value="CONFIGURATION_CHANGE">Embedding、维度或距离度量变化</option><option value="PARSER_CHANGE">解析器或分块策略变化</option><option value="MANUAL_REBUILD">内容治理后手动全量重建</option></select></label>',
    `<dl class="description-list"><div class="description-row"><dt>活动文档</dt><dd>${escapeHtml(knowledgeBase?.documents || 0)} 个</dd></div><div class="description-row"><dt>当前活动索引</dt><dd>${escapeHtml(knowledgeBase?.activeIndex || "尚无")}</dd></div><div class="description-row"><dt>候选 Embedding</dt><dd>${escapeHtml(candidateConfig?.embeddingModel || "未配置")} / ${escapeHtml(candidateConfig?.embeddingDimension || 0)} 维</dd></div><div class="description-row"><dt>距离与归一化</dt><dd>${escapeHtml(candidateConfig?.distanceMetric || "未配置")} / ${escapeHtml(candidateConfig?.normalization || "未配置")}</dd></div><div class="description-row"><dt>解析与分块</dt><dd>${escapeHtml(candidateConfig?.parserVersion || "未配置")} / ${escapeHtml(candidateConfig?.chunkingStrategy || "未配置")}</dd></div></dl>`,
    '<p id="index-rebuild-feedback" class="form-feedback" role="alert"></p><div class="form-actions"><button class="button secondary" type="button" data-nav-screen="index-versions">取消</button><button class="button primary" type="submit" data-primary-action' + (disabled ? " disabled" : "") + '>开始全库重建</button></div></div></section>',
    `<aside class="section-block wizard-review"><span class="section-kicker">影响范围</span><h2>${escapeHtml(knowledgeBase?.name || "当前知识库")}</h2><dl class="description-list"><div class="description-row"><dt>新版本</dt><dd>独立构建，不修改活动索引</dd></div><div class="description-row"><dt>查询服务</dt><dd>${escapeHtml(knowledgeBase?.activeIndex || "当前版本")} 持续服务</dd></div><div class="description-row"><dt>上线条件</dt><dd>构建和冻结评测通过后人工激活</dd></div><div class="description-row"><dt>恢复方式</dt><dd>取消候选构建，保留活动指针</dd></div></dl></aside>`,
    "</form>",
  ].join("");
  return adminShell(
    "index-rebuild",
    "全库索引重建",
    "",
    '<button class="button secondary" type="button" data-nav-screen="index-versions">返回索引版本</button>',
    content,
    state,
    "知识管理 / 索引重建",
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

function renderIndexDetail(state, demoState) {
  const generic = genericState("index-detail", state);
  if (generic) {
    return adminShell("index-detail", "索引版本", "", "", generic, state, "知识管理 / 索引版本");
  }
  const knowledgeBase = selectedKnowledgeBase(demoState);
  const stored = selectedIndexVersion(demoState);
  if (!stored) {
    return adminShell(
      "index-detail",
      "索引版本",
      "",
      '<button class="button primary" type="button" data-nav-screen="index-rebuild">建立索引</button>',
      statePanel("empty", "尚无索引版本", "请先建立一个全库索引版本。", "建立索引", "index-rebuild"),
      state,
      "知识管理 / 索引版本",
    );
  }
  const indexVersion = state === "partial"
    ? { ...stored, status: "BUILDING", stage: "EMBEDDING", progress: 65, quality: null }
    : state === "disabled"
      ? { ...stored, status: "BLOCKED", stage: "BLOCKED", progress: 100, quality: { ...fixtures.indexVersion.quality, recallAt10: 0.81 }, failedGates: ["Recall@10"] }
      : stored;
  const building = indexVersion.status === "BUILDING";
  const evaluating = indexVersion.status === "EVALUATING";
  const ready = indexVersion.status === "READY_FOR_REVIEW";
  const complete = ["READY_FOR_REVIEW", "ACTIVE", "BLOCKED", "SUPERSEDED", "CANCELLED"].includes(indexVersion.status);
  const stageOrder = ["MANIFEST_FREEZING", "CHUNKING", "EMBEDDING", "HYBRID_INDEXING", "EVALUATING"];
  const stageNames = { MANIFEST_FREEZING: "冻结清单", CHUNKING: "结构化分块", EMBEDDING: "生成向量", HYBRID_INDEXING: "建立混合索引", EVALUATING: "冻结评测" };
  const position = complete ? stageOrder.length : Math.max(0, stageOrder.indexOf(indexVersion.stage));
  const operation = building
    ? '<button class="button primary" type="button" data-advance-index>刷新构建状态</button><button class="button secondary" type="button" data-cancel-index>取消构建</button>'
    : evaluating
      ? '<button class="button primary" type="button" data-evaluate-index>运行冻结评测</button><button class="button secondary" type="button" data-cancel-index>取消构建</button>'
      : ready
        ? '<button class="button primary" type="button" data-confirm-index>激活此版本</button>'
        : indexVersion.status === "ACTIVE"
          ? '<button class="button secondary" type="button" data-nav-screen="index-versions">返回版本目录</button>'
          : '<button class="button primary" type="button" data-nav-screen="index-rebuild">重新建立版本</button>';
  const quality = indexVersion.quality
    ? `<section class="section-block"><div class="section-heading"><h2>冻结评测</h2><span class="mono">${escapeHtml(indexVersion.evaluationSet || "eval-set-v6")} / ${escapeHtml(indexVersion.evaluationQuestionCount || 100)} 题</span></div>${qualityGrid(indexVersion.quality, false)}${indexVersion.failedGates?.length ? `<p class="inline-note danger">未通过：${indexVersion.failedGates.map(escapeHtml).join("、")}</p>` : ""}</section>`
    : '<section class="section-block"><div class="section-heading"><h2>冻结评测</h2><span class="status-tag" data-tone="info">尚未运行</span></div><p class="muted">构建完成后运行权限、召回、引用、拒答和延迟评测。</p></section>';
  const notice = indexVersion.status === "BLOCKED"
    ? `质量门禁未通过，活动 ${knowledgeBase.activeIndex} 继续服务。`
    : indexVersion.status === "ACTIVE"
      ? `${indexVersion.id} 已成为当前活动索引。旧版本继续保留。`
      : indexVersion.status === "CANCELLED"
        ? `候选构建已取消，活动 ${knowledgeBase.activeIndex} 未变化。`
        : `活动 ${knowledgeBase.activeIndex} 继续服务；${indexVersion.id} 不会自动激活。`;
  const content = [
    `<div class="inline-note${indexVersion.status === "BLOCKED" ? " danger" : building || evaluating ? " warning" : ""}">${escapeHtml(notice)}</div>`,
    '<div class="split-detail"><div>',
    `<section class="section-block" data-index-stage="${escapeHtml(indexVersion.stage)}" data-index-status="${escapeHtml(indexVersion.status)}"><div class="section-heading"><div><span class="section-kicker">构建状态</span><h2>${escapeHtml(indexStatusLabel(indexVersion.status))}</h2></div>${statusTag(indexVersion.status)}</div><ol class="process-steps source-process index-process" aria-label="索引构建阶段">${stageOrder.map((stage, index) => `<li data-step-state="${index < position ? "complete" : index === position ? "current" : "upcoming"}"><span>${index + 1}</span><strong>${stageNames[stage]}</strong></li>`).join("")}</ol><div class="progress-track" aria-label="索引构建进度"><div class="progress-value" style="width:${escapeHtml(indexVersion.progress)}%"></div></div><p class="muted">${escapeHtml(indexVersion.documents)} 个文档${indexVersion.chunks ? ` / ${escapeHtml(indexVersion.chunks)} 个分块` : ""}</p></section>`,
    quality,
    "</div><aside>",
    `<section class="section-block"><h2>不可变配置</h2><dl class="description-list"><div class="description-row"><dt>Embedding</dt><dd>${escapeHtml(indexVersion.config.embeddingModel)} / ${escapeHtml(indexVersion.config.embeddingDimension)} 维</dd></div><div class="description-row"><dt>规范化与距离</dt><dd>${escapeHtml(indexVersion.config.normalization)} / ${escapeHtml(indexVersion.config.distanceMetric)}</dd></div><div class="description-row"><dt>解析器</dt><dd>${escapeHtml(indexVersion.config.parserVersion)}</dd></div><div class="description-row"><dt>分块</dt><dd>${escapeHtml(indexVersion.config.chunkingStrategy)}</dd></div><div class="description-row"><dt>检索</dt><dd>${escapeHtml(indexVersion.config.retrievalStrategy)}</dd></div><div class="description-row"><dt>当前活动索引</dt><dd>${escapeHtml(knowledgeBase.activeIndex || "尚无")}</dd></div></dl></section>`,
    `<section class="release-decision${indexVersion.status === "BLOCKED" ? " blocked" : ""}"><span class="section-kicker">版本操作</span><h2>${escapeHtml(indexVersion.id)}</h2><p>${escapeHtml(indexVersion.reason)}</p><div class="form-actions">${operation}</div>${indexFeedback ? `<p class="form-feedback" role="status">${escapeHtml(indexFeedback)}</p>` : ""}</section>`,
    "</aside></div>",
    `<dialog id="index-dialog" class="dialog-surface"><form method="dialog"><span class="section-kicker">激活确认</span><h2>激活 ${escapeHtml(indexVersion.id)}？</h2><p>新查询使用此版本；已固定旧版本的会话继续使用原版本。</p><div class="dialog-impact"><div><dt>当前</dt><dd>${escapeHtml(knowledgeBase.activeIndex || "尚无")}</dd></div><div><dt>切换后</dt><dd>${escapeHtml(indexVersion.id)}</dd></div></div><div class="release-actions"><button class="button secondary" value="cancel">取消</button><button class="button primary" type="button" data-activate-index>确认激活</button></div></form></dialog>`,
    `<dialog id="cancel-index-dialog" class="dialog-surface"><form method="dialog"><span class="section-kicker">取消确认</span><h2>取消 ${escapeHtml(indexVersion.id)} 的构建？</h2><p>候选版本停止推进，活动 ${escapeHtml(knowledgeBase.activeIndex || "索引")} 不受影响。</p><div class="release-actions"><button class="button secondary" value="cancel">返回</button><button class="button primary" type="button" data-confirm-cancel-index>确认取消</button></div></form></dialog>`,
  ].join("");
  return adminShell(
    "index-detail",
    `索引 ${indexVersion.id}`,
    "",
    '<button class="button secondary" type="button" data-nav-screen="index-rebuild">重建索引</button>',
    content,
    state,
    "知识管理 / 索引版本",
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
        ' · 创建后不能更换</dd><dt>索引</dt><dd><span class="mono">' +
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
        ">发布助手版本</button></section></aside></div>",
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

function bindKnowledgeManagement() {
  const createDialog = root.querySelector("#create-knowledge-base-dialog");
  root.querySelector("[data-open-create-knowledge]")?.addEventListener("click", () => {
    createDialog?.showModal();
    createDialog?.querySelector('[name="name"]')?.focus();
  });
  root.querySelectorAll("[data-close-knowledge-dialog]").forEach(button => {
    button.addEventListener("click", () => createDialog?.close());
  });
  root.querySelector("#create-knowledge-base-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const result = prototypeStore.createKnowledgeBase(
      { name: values.get("name"), description: values.get("description") },
      TENANT_ADMIN_ACTOR_ID,
      DEFAULT_TENANT_ID,
    );
    if (!result.ok) {
      const message = {
        KNOWLEDGE_BASE_NAME_CONFLICT: "此知识库名称已存在，请使用其他名称。",
        KNOWLEDGE_BASE_QUOTA_EXCEEDED: "已达到平台配置的知识库配额。",
        STORAGE_UNAVAILABLE: "浏览器无法保存，知识库没有创建。",
      }[result.reason] || result.errors?.[0]?.message || "当前无法创建知识库。";
      root.querySelector("#create-knowledge-base-feedback").textContent = message;
      event.currentTarget.querySelector(`[name="${result.errors?.[0]?.field || "name"}"]`)?.focus();
      return;
    }
    createDialog?.close();
    knowledgeFeedback = `已创建“${result.knowledgeBase.name}”，下一步可进入并添加资料。`;
    render();
    root.querySelector(`[data-kb-row="${CSS.escape(result.knowledgeBase.id)}"]`)?.focus();
  });

  const sourceForm = root.querySelector("#source-add-form");
  const mode = sourceForm?.elements.mode;
  const updateSourceMode = () => {
    const paste = mode?.value === "paste";
    const fileFields = sourceForm?.querySelector("[data-file-fields]");
    const pasteFields = sourceForm?.querySelector("[data-paste-fields]");
    if (fileFields) fileFields.hidden = paste;
    if (pasteFields) pasteFields.hidden = !paste;
  };
  mode?.addEventListener("change", updateSourceMode);
  updateSourceMode();
  sourceForm?.elements.file?.addEventListener("change", event => {
    const selected = sourceForm.querySelector("[data-selected-file]");
    if (selected) selected.textContent = event.currentTarget.files?.[0]?.name || "尚未选择文件";
  });
  sourceForm?.addEventListener("submit", event => {
    event.preventDefault();
    const knowledgeBase = selectedKnowledgeBase(prototypeStore.getSnapshot());
    const values = new FormData(event.currentTarget);
    const sourceMode = values.get("mode");
    const file = event.currentTarget.elements.file?.files?.[0];
    const input = sourceMode === "paste"
      ? { mode: "paste", name: values.get("name"), text: values.get("text") }
      : {
          mode: "file",
          name: file?.name || "",
          sizeMb: file ? file.size / 1024 / 1024 : 0,
          mimeType: file?.type || "",
          simulatedFailure: file?.name.includes("超时") ? "OCR_TIMEOUT" : null,
        };
    const result = prototypeStore.addKnowledgeSource(
      knowledgeBase?.id,
      input,
      TENANT_ADMIN_ACTOR_ID,
    );
    if (!result.ok) {
      const message = {
        SOURCE_CONTENT_REQUIRED: "请填写资料名称和粘贴内容。",
        SOURCE_MODE_REQUIRED: "请选择一种来源方式。",
        UNSUPPORTED_FILE_TYPE: "不支持此文件类型，请选择允许的本地文件。",
        FILE_SIZE_INVALID: "请选择非空文件。",
        FILE_TOO_LARGE: "文件超过平台配置的单文件上限。",
        PASTED_TEXT_TOO_LARGE: "粘贴内容超过平台配置的大小上限。",
        MIME_MISMATCH: "文件类型与内容类型不一致，已拒绝接入。",
        DOCUMENT_QUOTA_EXCEEDED: "当前知识库已达到文档配额。",
        STORAGE_UNAVAILABLE: "浏览器无法保存，资料没有进入处理队列。",
      }[result.reason] || "安全校验未通过，资料没有进入处理队列。";
      root.querySelector("#source-add-feedback").textContent = message;
      return;
    }
    knowledgeFeedback = `已接收“${result.source.name}”，等待安全扫描。`;
    navigate("source-detail");
  });

  root.querySelector("[data-advance-source]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getSnapshot();
    const result = prototypeStore.advanceKnowledgeSource(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedKnowledgeSourceId,
      TENANT_ADMIN_ACTOR_ID,
    );
    knowledgeFeedback = result.ok ? `处理阶段已更新为 ${result.source.stage}。` : "当前阶段不能继续推进。";
    render();
  });
  root.querySelector("[data-retry-source]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getSnapshot();
    const result = prototypeStore.retryKnowledgeSource(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedKnowledgeSourceId,
      TENANT_ADMIN_ACTOR_ID,
    );
    knowledgeFeedback = result.ok ? "已从安全重试点继续，失败内容仍未进入活动索引。" : "当前来源不能重试。";
    render();
  });
  root.querySelector("[data-reprocess-source]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getSnapshot();
    const result = prototypeStore.reprocessKnowledgeSource(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedKnowledgeSourceId,
      TENANT_ADMIN_ACTOR_ID,
    );
    knowledgeFeedback = result.ok ? "已建立新修订并开始重新提取；活动索引继续服务。" : "当前来源不能重新处理。";
    render();
  });
}

function bindIndexManagement() {
  root.querySelector("#index-rebuild-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const snapshot = prototypeStore.getSnapshot();
    const knowledgeBase = selectedKnowledgeBase(snapshot);
    const trigger = new FormData(event.currentTarget).get("trigger");
    const definitions = {
      CONFIGURATION_CHANGE: { changedFields: ["embeddingModel", "embeddingDimension", "distanceMetric"], reason: "Embedding、维度或距离度量变化" },
      PARSER_CHANGE: { changedFields: ["parserVersion", "chunkingStrategy"], reason: "解析器或分块策略变化" },
      MANUAL_REBUILD: { changedFields: [], reason: "内容治理后手动全量重建" },
    };
    const definition = definitions[trigger] || definitions.MANUAL_REBUILD;
    const result = prototypeStore.createIndexRebuild(
      knowledgeBase?.id,
      { trigger, ...definition },
      TENANT_ADMIN_ACTOR_ID,
    );
    if (!result.ok) {
      const message = {
        INDEX_BUILD_ALREADY_ACTIVE: "已有索引版本正在执行，请先查看或取消现有任务。",
        NO_ACTIVE_DOCUMENTS: "当前没有可冻结的有效文档。",
        STORAGE_UNAVAILABLE: "浏览器无法保存，未创建索引版本。",
      }[result.reason] || "当前不能创建全库重建任务。";
      root.querySelector("#index-rebuild-feedback").textContent = message;
      return;
    }
    indexFeedback = `已创建 ${result.indexVersion.id}，活动索引未变化。`;
    navigate("index-detail");
  });

  root.querySelector("[data-advance-index]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getSnapshot();
    const result = prototypeStore.advanceIndexBuild(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedIndexVersionId,
      TENANT_ADMIN_ACTOR_ID,
    );
    indexFeedback = result.ok ? `构建已推进到 ${result.indexVersion.stage}。` : "当前构建不能继续推进。";
    render();
  });
  root.querySelector("[data-evaluate-index]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getSnapshot();
    const result = prototypeStore.recordIndexEvaluation(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedIndexVersionId,
      fixtures.indexVersion.quality,
      TENANT_ADMIN_ACTOR_ID,
    );
    indexFeedback = result.ok && result.indexVersion.status === "READY_FOR_REVIEW"
      ? "冻结评测通过，可以由授权管理员激活。"
      : "冻结评测未通过，活动索引继续服务。";
    render();
  });
  const indexDialog = root.querySelector("#index-dialog");
  root.querySelector("[data-confirm-index]")?.addEventListener("click", () => indexDialog?.showModal());
  root.querySelector("[data-activate-index]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getSnapshot();
    const result = prototypeStore.activateIndexVersion(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedIndexVersionId,
      TENANT_ADMIN_ACTOR_ID,
    );
    indexFeedback = result.ok ? `${result.indexVersion.id} 已原子切换为当前活动索引。` : "激活失败，活动索引未变化。";
    indexDialog?.close();
    render();
  });
  const cancelDialog = root.querySelector("#cancel-index-dialog");
  root.querySelector("[data-cancel-index]")?.addEventListener("click", () => cancelDialog?.showModal());
  root.querySelector("[data-confirm-cancel-index]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getSnapshot();
    const result = prototypeStore.cancelIndexBuild(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedIndexVersionId,
      TENANT_ADMIN_ACTOR_ID,
    );
    indexFeedback = result.ok ? "候选构建已取消，活动索引未变化。" : "当前版本不能取消。";
    cancelDialog?.close();
    render();
  });
}

function bindInteractions(screen) {
  root.querySelectorAll("[data-nav-screen]").forEach((element) => {
    element.addEventListener("click", () => {
      if (element.dataset.selectTenant) {
        prototypeStore.selectTenant(element.dataset.selectTenant);
      }
      if (element.dataset.selectKb) {
        prototypeStore.selectKnowledgeBase(element.dataset.selectKb);
      }
      if (element.dataset.selectSource) {
        prototypeStore.selectKnowledgeSource(element.dataset.selectSource);
      }
      if (element.dataset.selectIndex) {
        prototypeStore.selectIndexVersion(element.dataset.selectIndex);
      }
      navigate(
        element.dataset.navScreen,
        element.dataset.navState || "success",
      );
    });
  });
  root.querySelector("#login-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const result = prototypeStore.loginQuery({
      tenantCode: values.get("tenantCode"),
      username: values.get("username"),
      password: values.get("password"),
    });
    event.currentTarget.elements.password.value = "";
    if (!result.ok) {
      loginFeedback =
        result.reason === "STORAGE_UNAVAILABLE"
          ? "浏览器无法保存演示会话，本次登录未生效。"
          : "组织编码、登录名或密码不正确。";
      render();
      root.querySelector('#login-form input[name="password"]')?.focus();
      return;
    }
    loginFeedback = "";
    queryFeedback = "";
    const requested = currentScenario();
    if (requested.screen === "conversation" && requested.conversationId) {
      navigate("conversation", "success", {
        conversationId: requested.conversationId,
      });
    } else {
      navigate("assistants");
    }
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
      const result = prototypeStore.createQueryConversation(
        fixtures,
        selectedAssistantId,
      );
      if (!result.ok) {
        queryFeedback =
          result.reason === "STORAGE_UNAVAILABLE"
            ? "无法保存新会话，请检查浏览器存储后重试。"
            : "当前授权不能创建此会话。";
        render();
        return;
      }
      activeConversationId = result.conversation.id;
      queryFeedback = "";
      navigate("conversation", "success", {
        conversationId: activeConversationId,
      });
    });
    element.addEventListener("keydown", moveAssistantFocus);
  });
  root.querySelectorAll("[data-conversation-id]").forEach((element) => {
    element.addEventListener("click", () => {
      const conversation = prototypeStore.queryProjection(fixtures).history.find((item) => item.id === element.dataset.conversationId);
      if (!conversation) return;
      activeConversationId = conversation.id;
      selectedAssistantId = conversation.assistantId;
      closeHistory();
      navigate("conversation", "success", {
        conversationId: activeConversationId,
      });
    });
  });
  root.querySelector("[data-history-search]")?.addEventListener("input", (event) => {
    const query = event.currentTarget.value.trim().toLocaleLowerCase("zh-CN");
    root.querySelectorAll("[data-conversation-id]").forEach((item) => {
      item.hidden =
        query.length > 0 &&
        !item.textContent.toLocaleLowerCase("zh-CN").includes(query);
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
      const result = prototypeStore.loadEarlierQueryMessages(
        fixtures,
        activeConversationId,
      );
      queryFeedback = result.ok
        ? "已按事件序号载入更早消息。"
        : "无法读取更早消息，当前内容保持不变。";
      render();
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
  root.querySelectorAll("[data-query-logout]").forEach((button) => {
    button.addEventListener("click", () => {
      cancelQueryTimer();
      const result = prototypeStore.logoutQuery();
      if (!result.ok) {
        loginFeedback = "当前页面已退出，但浏览器未能清除登录记录。请关闭原型并清理本地演示登录态后重试。";
        navigate("login");
        return;
      }
      activeConversationId = "";
      queryFeedback = "";
      navigate("login");
    });
  });
  root.querySelector("[data-new-conversation]")?.addEventListener("click", () => {
    queryFeedback = "";
    navigate("assistants");
  });
  root.querySelectorAll("[data-stop-run]").forEach((button) => {
    button.addEventListener("click", () => {
      const projection = prototypeStore.queryProjection(fixtures, {
        conversationId: activeConversationId,
      });
      const result = prototypeStore.stopQueryRun(
        fixtures,
        activeConversationId,
        projection.activeRun?.id,
      );
      queryFeedback = result.ok ? "已停止并保存当前接收位置。" : "本轮已不在接收中。";
      render();
    });
  });
  root.querySelectorAll("[data-run-action]").forEach((button) => {
    button.addEventListener("click", () => {
      const projection = prototypeStore.queryProjection(fixtures, {
        conversationId: activeConversationId,
      });
      const method =
        button.dataset.runAction === "retry"
          ? prototypeStore.retryQueryRun
          : prototypeStore.resumeQueryRun;
      const result = method(
        fixtures,
        activeConversationId,
        projection.activeRun?.id,
      );
      queryStreamingSuspended = false;
      queryFeedback = result.ok ? "已继续同一轮次。" : "当前状态不能继续。";
      render();
    });
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
  root.querySelectorAll("[data-demo-account]").forEach((select) => {
    select.addEventListener("change", () => {
      prototypeStore.selectDemoAccount(select.value);
      activeConversationId = "";
      selectedAssistantId = prototypeStore.queryProjection(fixtures).assistants[0]?.id || "";
      render();
    });
  });
  bindTenantCreation();
  bindTenantSettingEditing("quota", "quotas", "资源配额", prototypeStore.saveTenantQuotas);
  bindTenantSettingEditing("security", "security", "登录策略", prototypeStore.saveTenantSecurity);
  root.querySelector("[data-refresh-tenant-usage]")?.addEventListener("click", () => {
    render();
    root.querySelector("[data-refresh-tenant-usage]")?.focus();
    root.querySelector("#tenant-usage-feedback").textContent = "已刷新合成用量与平台配额。";
  });
  bindDirectorySearch();
  bindAccountManagement();
  bindTenantFeatures();
  bindMemberManagement();
  bindHealthManagement();
  bindRecoveryDrill();
  bindKnowledgeManagement();
  bindIndexManagement();
}

function bindAccountManagement() {
  const createDialog = root.querySelector("#create-account-dialog");
  const createForm = root.querySelector("#create-account-form");
  const createTrigger = root.querySelector(
    '[data-business-action="open-create-account"]',
  );

  const resetValidation = (form, feedback) => {
    form?.reset();
    form?.querySelectorAll("[aria-invalid]").forEach((control) =>
      control.removeAttribute("aria-invalid"),
    );
    if (feedback) {
      feedback.textContent = "";
      feedback.removeAttribute("data-tone");
    }
  };

  const showErrors = (form, feedback, result, fallback) => {
    const errors = result.errors || [];
    errors.forEach((error) => {
      form.elements.namedItem(error.field)?.setAttribute("aria-invalid", "true");
    });
    feedback.textContent =
      result.reason === "STORAGE_UNAVAILABLE"
        ? "浏览器无法保存账户变更，本次操作未生效。"
        : result.reason === "FORBIDDEN"
          ? "当前演示身份无权管理人员账户。"
          : errors.map((error) => error.message).join(" ") || fallback;
    feedback.dataset.tone = "danger";
    form.querySelector("[aria-invalid='true']")?.focus();
  };

  createTrigger?.addEventListener("click", () => {
    resetValidation(createForm, createForm?.querySelector("[role='alert']"));
    createDialog?.showModal();
    createForm?.elements.namedItem("loginName")?.focus();
  });
  createForm
    ?.querySelector("[data-cancel-create-account]")
    ?.addEventListener("click", () => {
      resetValidation(createForm, createForm.querySelector("[role='alert']"));
      createDialog.close();
      createTrigger?.focus();
    });
  createForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    const feedback = createForm.querySelector("[role='alert']");
    createForm.querySelectorAll("[aria-invalid]").forEach((control) =>
      control.removeAttribute("aria-invalid"),
    );
    feedback.textContent = "";
    if (!createForm.reportValidity()) {
      const invalid = createForm.querySelector(":invalid");
      invalid?.setAttribute("aria-invalid", "true");
      feedback.textContent = "请修正账户信息后提交。";
      feedback.dataset.tone = "danger";
      invalid?.focus();
      return;
    }
    const values = new FormData(createForm);
    const result = prototypeStore.createAccount(
      {
        loginName: values.get("loginName"),
        password: values.get("password"),
        passwordConfirmation: values.get("passwordConfirmation"),
      },
      PLATFORM_ACCOUNT_ACTOR_ID,
    );
    if (!result.ok) {
      showErrors(createForm, feedback, result, "当前信息无法创建账户。");
      return;
    }
    const createdAccountId = result.account.id;
    accountFeedback = `已创建 ${result.account.loginName}；初始密码不再显示。`;
    createDialog.close();
    render();
    root.querySelector(`[data-account-row="${CSS.escape(createdAccountId)}"]`)?.focus();
  });

  const resetDialog = root.querySelector("#reset-account-password-dialog");
  const resetForm = root.querySelector("#reset-account-password-form");
  root.querySelectorAll("[data-reset-account]").forEach((trigger) => {
    trigger.addEventListener("click", () => {
      resetValidation(resetForm, resetForm?.querySelector("[role='alert']"));
      const accountId = trigger.dataset.resetAccount;
      const account = prototypeStore
        .getSnapshot()
        .accounts.find((item) => item.id === accountId);
      resetForm.dataset.accountId = accountId;
      root.querySelector("#reset-account-target").textContent =
        `为 ${account?.loginName || "此账户"} 设置新密码。系统不会回显或保存明文。`;
      resetDialog?.showModal();
      resetForm.elements.namedItem("password")?.focus();
    });
  });
  resetForm
    ?.querySelector("[data-cancel-reset-account]")
    ?.addEventListener("click", () => {
      const accountId = resetForm.dataset.accountId;
      resetValidation(resetForm, resetForm.querySelector("[role='alert']"));
      resetDialog.close();
      root.querySelector(`[data-reset-account="${CSS.escape(accountId)}"]`)?.focus();
    });
  resetForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    const feedback = resetForm.querySelector("[role='alert']");
    resetForm.querySelectorAll("[aria-invalid]").forEach((control) =>
      control.removeAttribute("aria-invalid"),
    );
    feedback.textContent = "";
    if (!resetForm.reportValidity()) {
      const invalid = resetForm.querySelector(":invalid");
      invalid?.setAttribute("aria-invalid", "true");
      feedback.textContent = "请完整填写新密码并确认撤销全部会话。";
      feedback.dataset.tone = "danger";
      invalid?.focus();
      return;
    }
    const values = new FormData(resetForm);
    const accountId = resetForm.dataset.accountId;
    const result = prototypeStore.resetAccountPassword(
      accountId,
      {
        password: values.get("password"),
        passwordConfirmation: values.get("passwordConfirmation"),
        revokeSessions: values.get("revokeSessions") === "on",
      },
      PLATFORM_ACCOUNT_ACTOR_ID,
    );
    if (!result.ok) {
      showErrors(resetForm, feedback, result, "当前账户无法重置密码。");
      return;
    }
    accountFeedback = `${result.account.loginName} 的密码已重置，全部会话已撤销；系统不显示新密码。`;
    resetDialog.close();
    render();
    root.querySelector(`[data-account-row="${CSS.escape(accountId)}"]`)?.focus();
  });

  const governanceDialog = root.querySelector("#account-governance-dialog");
  const governanceForm = root.querySelector("#account-governance-form");
  const openGovernance = (trigger, action, nextStatus = "") => {
    const accountId =
      trigger.dataset.assignPlatformAdministrator || trigger.dataset.setAccountStatus;
    const account = prototypeStore
      .getSnapshot()
      .accounts.find((item) => item.id === accountId);
    governanceForm.dataset.accountId = accountId;
    governanceForm.dataset.action = action;
    governanceForm.dataset.nextStatus = nextStatus;
    governanceForm.querySelector("[role='alert']").textContent = "";
    const title = root.querySelector("#account-governance-title");
    const description = root.querySelector("#account-governance-description");
    const impact = root.querySelector("#account-governance-impact");
    const recovery = root.querySelector("#account-governance-recovery");
    const confirm = root.querySelector("#account-governance-confirm");
    if (action === "assign") {
      title.textContent = "设置平台管理员身份";
      description.textContent = `确认将 ${account?.loginName || "此账户"} 设为平台管理员？`;
      impact.textContent = "获得平台范围治理能力，并撤销该账户既有会话。";
      recovery.textContent = "当前原型不提供自助降级；需走受控身份变更流程。";
      confirm.textContent = "确认设置";
    } else {
      const suspended = nextStatus === "suspended";
      title.textContent = suspended ? "停用人员账户" : "恢复人员账户";
      description.textContent = `${suspended ? "确认停用" : "确认恢复"} ${account?.loginName || "此账户"}？`;
      impact.textContent = suspended
        ? "立即禁止登录并撤销全部既有会话。"
        : "恢复登录资格，但不会恢复曾被撤销的会话。";
      recovery.textContent = suspended ? "可由其他平台管理员恢复此账户。" : "需要时可再次停用。";
      confirm.textContent = suspended ? "确认停用" : "确认恢复";
    }
    governanceDialog?.showModal();
    confirm.focus();
  };
  root.querySelectorAll("[data-assign-platform-administrator]").forEach((trigger) => {
    trigger.addEventListener("click", () => openGovernance(trigger, "assign"));
  });
  root.querySelectorAll("[data-set-account-status]").forEach((trigger) => {
    trigger.addEventListener("click", () =>
      openGovernance(trigger, "status", trigger.dataset.nextStatus),
    );
  });
  governanceForm
    ?.querySelector("[data-cancel-account-governance]")
    ?.addEventListener("click", () => {
      const accountId = governanceForm.dataset.accountId;
      const action = governanceForm.dataset.action;
      governanceDialog.close();
      const selector =
        action === "assign"
          ? `[data-assign-platform-administrator="${CSS.escape(accountId)}"]`
          : `[data-set-account-status="${CSS.escape(accountId)}"]`;
      root.querySelector(selector)?.focus();
    });
  governanceForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    const accountId = governanceForm.dataset.accountId;
    const action = governanceForm.dataset.action;
    const nextStatus = governanceForm.dataset.nextStatus;
    const result =
      action === "assign"
        ? prototypeStore.assignPlatformAdministrator(
            accountId,
            PLATFORM_ACCOUNT_ACTOR_ID,
          )
        : prototypeStore.setAccountStatus(
            accountId,
            nextStatus,
            PLATFORM_ACCOUNT_ACTOR_ID,
          );
    if (!result.ok) {
      const messages = {
        ACCOUNT_ASSIGNED_TO_TENANT: "该账户已属于租户人员范围，不能设为平台管理员。",
        ACCOUNT_NOT_ACTIVE: "请先恢复该账户。",
        LAST_PLATFORM_ADMINISTRATOR: "不能停用最后一个可用的平台管理员。",
        SELF_MUTATION_FORBIDDEN: "不能在当前会话中停用自己。",
        STORAGE_UNAVAILABLE: "浏览器无法保存账户变更，本次操作未生效。",
      };
      const feedback = governanceForm.querySelector("[role='alert']");
      feedback.textContent = messages[result.reason] || "当前账户操作未生效。";
      feedback.dataset.tone = "danger";
      return;
    }
    accountFeedback =
      action === "assign"
        ? `${result.account.loginName} 已设为平台管理员，既有会话已撤销。`
        : `${result.account.loginName} 已${nextStatus === "suspended" ? "停用，全部会话已撤销" : "恢复"}。`;
    governanceDialog.close();
    render();
    root.querySelector(`[data-account-row="${CSS.escape(accountId)}"]`)?.focus();
  });

  const applyAccountFilters = () => {
    const query =
      root
        .querySelector('[data-directory-search="platform-accounts"]')
        ?.value.trim()
        .toLocaleLowerCase("zh-CN") || "";
    const status = root.querySelector("[data-account-status-filter]")?.value || "";
    root.querySelectorAll("[data-account-row]").forEach((row) => {
      const matchesQuery =
        query.length === 0 || row.dataset.searchText.toLocaleLowerCase("zh-CN").includes(query);
      const matchesStatus = status.length === 0 || row.dataset.accountStatus === status;
      row.hidden = !matchesQuery || !matchesStatus;
    });
  };
  root
    .querySelector('[data-directory-search="platform-accounts"]')
    ?.addEventListener("input", applyAccountFilters);
  root
    .querySelector("[data-account-status-filter]")
    ?.addEventListener("change", applyAccountFilters);
}

function bindDirectorySearch() {
  root.querySelectorAll("[data-directory-search]").forEach((input) => {
    input.addEventListener("input", () => {
      const query = input.value.trim().toLocaleLowerCase("zh-CN");
      root.querySelectorAll("[data-search-text]").forEach((row) => {
        row.hidden =
          query.length > 0 &&
          !row.dataset.searchText.toLocaleLowerCase("zh-CN").includes(query);
      });
    });
  });
}

function bindTenantCreation() {
  const form = root.querySelector("#tenant-create-form");
  if (!form) return;
  const feedback = form.querySelector("#tenant-create-feedback");
  const showStorageFailure = () => {
    feedback.textContent = "浏览器无法保存租户草稿，请检查本地存储后重试。";
    feedback.dataset.tone = "danger";
  };
  form.querySelectorAll("[data-tenant-draft], [data-tenant-quota]").forEach((control) => {
    const persist = () => {
      const patch = control.hasAttribute("data-tenant-quota")
        ? { quotas: { [control.name]: control.value } }
        : { [control.name]: control.value };
      if (!prototypeStore.updateTenantSetup(patch).ok) showStorageFailure();
    };
    control.addEventListener(control.tagName === "SELECT" ? "change" : "input", persist);
  });
  form.querySelector("[data-tenant-setup-back]")?.addEventListener("click", () => {
    const step = Number(form.dataset.currentTenantStep) || 1;
    const result = prototypeStore.updateTenantSetup({ step: Math.max(1, step - 1) });
    if (!result.ok) {
      showStorageFailure();
      return;
    }
    render();
  });
  form.querySelector("[data-discard-tenant-setup]")?.addEventListener("click", () => {
    const result = prototypeStore.discardTenantSetup();
    if (!result.ok) {
      showStorageFailure();
      return;
    }
    navigate("platform-tenants");
  });
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    form.querySelectorAll("[aria-invalid]").forEach((control) =>
      control.removeAttribute("aria-invalid"),
    );
    feedback.textContent = "";
    feedback.removeAttribute("data-tone");
    if (!form.reportValidity()) {
      const invalid = form.querySelector(":invalid");
      invalid?.setAttribute("aria-invalid", "true");
      feedback.textContent = "请完成当前步骤后继续。";
      feedback.dataset.tone = "danger";
      invalid?.focus();
      return;
    }
    const step = Number(form.dataset.currentTenantStep) || 1;
    const values = new FormData(form);
    if (step < 3) {
      if (step === 1 && prototypeStore.getSnapshot().tenants.some(tenant => tenant.code === values.get("tenantCode"))) {
        const control = form.elements.namedItem("tenantCode");
        control.setAttribute("aria-invalid", "true");
        feedback.textContent = "此租户编码已存在，请使用其他编码。";
        feedback.dataset.tone = "danger";
        control.focus();
        return;
      }
      const patch =
        step === 1
          ? {
              organizationName: values.get("organizationName"),
              tenantCode: values.get("tenantCode"),
              step: 2,
            }
          : {
              administratorAccountId: values.get("administratorAccountId"),
              step: 3,
            };
      const updated = prototypeStore.updateTenantSetup(patch);
      if (!updated.ok) {
        showStorageFailure();
        return;
      }
      render();
      return;
    }
    const setup = prototypeStore.getSnapshot().tenantSetup;
    const quotas = Object.fromEntries(
      [...form.querySelectorAll("[data-tenant-quota]")].map((control) => [
        control.name,
        control.value,
      ]),
    );
    const result = prototypeStore.createTenant({
      organizationName: setup.organizationName,
      tenantCode: setup.tenantCode,
      administratorAccountId: setup.administratorAccountId,
      quotas,
      confirmed: values.get("confirmed") === "on",
    });
    if (!result.ok) {
      const errors = result.errors || [];
      const firstError = errors[0];
      const errorStep =
        firstError && ["organizationName", "tenantCode"].includes(firstError.field)
          ? 1
          : firstError?.field === "administratorAccountId"
            ? 2
            : 3;
      if (errorStep !== step) {
        const moved = prototypeStore.updateTenantSetup({ step: errorStep });
        if (!moved.ok) {
          showStorageFailure();
          return;
        }
        render();
        const nextForm = root.querySelector("#tenant-create-form");
        const target = nextForm?.elements.namedItem(firstError.field);
        target?.setAttribute("aria-invalid", "true");
        const nextFeedback = nextForm?.querySelector("#tenant-create-feedback");
        if (nextFeedback) {
          nextFeedback.textContent = errors.map((error) => error.message).join(" ");
          nextFeedback.dataset.tone = "danger";
        }
        target?.focus();
        return;
      }
      errors.forEach((error) => {
        form.elements.namedItem(error.field)?.setAttribute("aria-invalid", "true");
      });
      feedback.textContent =
        result.reason === "STORAGE_UNAVAILABLE"
          ? "浏览器无法保存新租户，本次创建未生效。"
          : errors.map((error) => error.message).join(" ") || "当前信息无法创建租户。";
      feedback.dataset.tone = "danger";
      const firstInvalid = form.querySelector("[aria-invalid='true']");
      firstInvalid?.focus();
      return;
    }
    navigate("platform-tenant-detail");
  });
}

/** 配额与登录策略共用编辑交互，状态层分别执行白名单与角色校验。 */
function bindTenantSettingEditing(key, action, label, save) {
  const triggerSelector = `[data-edit-tenant-${action}]`;
  const trigger = root.querySelector(triggerSelector);
  const dialog = root.querySelector(`#tenant-${key}-dialog`);
  const form = root.querySelector(`#tenant-${key}-form`);
  if (!trigger || !dialog || !form) return;
  const feedback = form.querySelector(`#tenant-${key}-feedback`);
  const resetForm = () => {
    form.reset();
    form.querySelectorAll("[aria-invalid]").forEach((control) =>
      control.removeAttribute("aria-invalid"),
    );
    feedback.textContent = "";
    feedback.removeAttribute("data-tone");
  };
  trigger.addEventListener("click", () => {
    resetForm();
    dialog.showModal();
    form.querySelector("input")?.focus();
  });
  form.querySelector(`[data-cancel-tenant-${action}]`)?.addEventListener("click", () => {
    resetForm();
    dialog.close();
    trigger.focus();
  });
  dialog.addEventListener("close", resetForm);
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    form.querySelectorAll("[aria-invalid]").forEach((control) =>
      control.removeAttribute("aria-invalid"),
    );
    if (!form.reportValidity()) {
      const invalid = form.querySelector(":invalid");
      invalid?.setAttribute("aria-invalid", "true");
      feedback.textContent = `请修正${label}后保存。`;
      feedback.dataset.tone = "danger";
      invalid?.focus();
      return;
    }
    const values = new FormData(form);
    const settings = Object.fromEntries(
      [...form.querySelectorAll("input[name]")].map((control) => [
        control.name,
        values.get(control.name),
      ]),
    );
    const result = save(
      form.dataset.tenantId,
      settings,
      "platformAdministrator",
    );
    if (!result.ok) {
      const errors = result.errors || [];
      errors.forEach((error) => {
        form.elements.namedItem(error.field)?.setAttribute("aria-invalid", "true");
      });
      feedback.textContent =
        result.reason === "STORAGE_UNAVAILABLE"
          ? `浏览器无法保存${label}，本次修改未生效。`
          : result.reason === "FORBIDDEN"
            ? `当前演示身份无权修改${label}。`
            : errors.map((error) => error.message).join(" ") || `${label}未保存。`;
      feedback.dataset.tone = "danger";
      form.querySelector("[aria-invalid='true']")?.focus();
      return;
    }
    dialog.close();
    render();
    root.querySelector(triggerSelector)?.focus();
    root.querySelector("#tenant-settings-feedback").textContent = `${label}已保存。`;
  });
}

function bindTenantFeatures() {
  const snapshot = prototypeStore.getSnapshot();
  const tenantId = snapshot.selectedTenantId || DEFAULT_TENANT_ID;
  root.querySelectorAll("[data-tenant-feature]").forEach((control) => {
    control.addEventListener("change", () => {
      prototypeStore.setTenantFeatureDraft(
        tenantId,
        control.dataset.tenantFeature,
        control.checked,
      );
    });
  });
  root
    .querySelector('[data-business-action="save-tenant-features"]')
    ?.addEventListener("click", () => {
      const result = prototypeStore.commitTenantFeatureDraft(tenantId);
      if (result.ok) {
        render();
      } else {
        const feedback = root.querySelector("#tenant-feature-feedback");
        if (feedback) feedback.textContent = "当前没有待保存的功能变更。";
      }
    });
  root
    .querySelector('[data-business-action="cancel-tenant-features"]')
    ?.addEventListener("click", () => {
      prototypeStore.discardTenantFeatureDraft(tenantId);
      render();
    });
}

function bindMemberManagement() {
  root
    .querySelector('[data-business-action="open-add-member"]')
    ?.addEventListener("click", () => {
      root.querySelector("#add-member-dialog")?.showModal();
    });
  root.querySelector("[data-cancel-add-member]")?.addEventListener("click", () => {
    root.querySelector("#add-member-dialog")?.close();
  });
  root.querySelector("#member-account-search")?.addEventListener("input", (event) => {
    const query = event.currentTarget.value.trim().toLocaleLowerCase("zh-CN");
    const select = root.querySelector("#member-account-select");
    [...select.options].forEach((option, index) => {
      if (index > 0) {
        option.hidden = query.length > 0 && !option.textContent.toLocaleLowerCase("zh-CN").includes(query);
      }
    });
  });
  root.querySelector("#add-member-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const result = prototypeStore.addMember(DEFAULT_TENANT_ID, {
      accountId: values.get("accountId"),
      conversationFeatures: values.getAll("conversationFeatures"),
      assistantIds: values.getAll("assistantIds"),
    });
    if (!result.ok) {
      const messages = {
        ACCOUNT_NOT_ELIGIBLE: "该账户不符合普通成员资格。",
        MEMBER_ALREADY_EXISTS: "该账户已是当前租户成员。",
      };
      const feedback = form.querySelector("#add-member-feedback");
      feedback.textContent = messages[result.reason] || "无法添加该成员。";
      feedback.dataset.tone = "danger";
      return;
    }
    form.closest("dialog")?.close();
    render();
  });
  root
    .querySelectorAll('[data-business-action="toggle-member-status"]')
    .forEach((button) => {
      button.addEventListener("click", () => {
        prototypeStore.setMemberStatus(
          DEFAULT_TENANT_ID,
          button.dataset.accountId,
          button.dataset.nextStatus,
        );
        render();
      });
    });
  root
    .querySelectorAll('[data-business-action="select-demo-member"]')
    .forEach((button) => {
      button.addEventListener("click", () => {
        prototypeStore.selectDemoAccount(button.dataset.accountId);
        render();
      });
    });
  root.querySelectorAll("[data-member-feature]").forEach((control) => {
    control.addEventListener("change", () => {
      prototypeStore.setMemberFeatureDraft(
        DEFAULT_TENANT_ID,
        control.dataset.memberAccount,
        control.dataset.memberFeature,
        control.checked,
      );
    });
  });
  root.querySelectorAll("[data-member-assistant]").forEach((control) => {
    control.addEventListener("change", () => {
      const accountId = control.dataset.memberAccount;
      const assistantIds = [...root.querySelectorAll(`[data-member-assistant][data-member-account="${CSS.escape(accountId)}"]:checked`)].map(
        (item) => item.dataset.memberAssistant,
      );
      prototypeStore.setMemberAssistantDraft(
        DEFAULT_TENANT_ID,
        accountId,
        assistantIds,
      );
    });
  });
  root
    .querySelector('[data-business-action="save-member-features"]')
    ?.addEventListener("click", (event) => {
      const result = prototypeStore.commitMemberFeatureDraft(
        DEFAULT_TENANT_ID,
        event.currentTarget.dataset.memberAccount,
      );
      if (result.ok) render();
    });
  root
    .querySelector('[data-business-action="cancel-member-features"]')
    ?.addEventListener("click", (event) => {
      prototypeStore.discardMemberFeatureDraft(
        DEFAULT_TENANT_ID,
        event.currentTarget.dataset.memberAccount,
      );
      render();
    });
}

function bindHealthManagement() {
  root
    .querySelectorAll('[data-business-action="select-health-incident"]')
    .forEach((button) => {
      button.addEventListener("click", () => {
        prototypeStore.selectHealthIncident(button.dataset.incidentId);
        render();
      });
    });
  root.querySelector("#health-action-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const snapshot = prototypeStore.getSnapshot();
    const action = new FormData(event.currentTarget).get("action");
    prototypeStore.recordHealthAction(snapshot.health.selectedIncidentId, action);
    render();
  });
  root
    .querySelector('[data-business-action="recheck-health"]')
    ?.addEventListener("click", () => {
      const snapshot = prototypeStore.getSnapshot();
      prototypeStore.recheckHealth(snapshot.health.selectedIncidentId);
      render();
    });
}

function bindRecoveryDrill() {
  root.querySelectorAll('input[name="recoveryBackup"]').forEach((control) => {
    control.addEventListener("change", () => {
      prototypeStore.selectRecoveryBackup(control.value);
      render();
    });
  });
  root
    .querySelector('[data-business-action="advance-recovery"]')
    ?.addEventListener("click", () => {
      const stage = prototypeStore.getSnapshot().recovery.stage;
      if (stage === "selected") prototypeStore.runIntegrityCheck();
      if (stage === "readyToConfirm") prototypeStore.confirmRecovery();
      if (stage === "running") prototypeStore.advanceRecovery();
      render();
    });
  root
    .querySelector('[data-business-action="retry-recovery"]')
    ?.addEventListener("click", () => {
      prototypeStore.retryRecovery();
      render();
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
  const projection = prototypeStore.queryProjection(fixtures, { assistantId: selectedAssistantId, conversationId: activeConversationId });
  if (!projection.messages.some((message) => message.citations?.some((citation) => citation.id === button.dataset.citationId))) return;
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
  const projection = prototypeStore.queryProjection(fixtures, {
    conversationId: activeConversationId,
  });
  if (!projection.conversationAllowed) {
    render();
    return;
  }
  const input = root.querySelector("#question-input");
  const question = input?.value.trim();
  if (!question) {
    input?.focus();
    return;
  }
  const result = prototypeStore.startQueryRun(
    fixtures,
    activeConversationId,
    question,
    { mode: nextQueryMode },
  );
  queryStreamingSuspended = false;
  nextQueryMode = "normal";
  const reviewerMode = document.querySelector("#review-query-mode");
  if (reviewerMode) {
    reviewerMode.value = "normal";
  }
  if (!result.ok) {
    queryFeedback =
      result.reason === "STORAGE_UNAVAILABLE"
        ? "浏览器无法保存本轮问题，内容未发送。"
        : "当前授权不能继续此会话。";
    render();
    const draft = root.querySelector("#question-input");
    if (draft) { draft.value = question; draft.focus(); }
    return;
  }
  queryFeedback = result.replayed
    ? "已恢复同一问题的接收进度，没有重复追加。"
    : result.run.status === "FAILED"
      ? "已模拟本轮接收失败，可从原轮次重试。"
      : result.run.status === "PAUSED"
        ? "已模拟断线，接收位置已保存。"
        : "";
  render();
}

function cancelQueryTimer() {
  streamGeneration += 1;
  window.clearTimeout(streamTimer);
  streamTimer = undefined;
}

function scheduleQueryContinuation() {
  const projection = prototypeStore.queryProjection(fixtures, {
    conversationId: activeConversationId,
  });
  if (
    queryStreamingSuspended ||
    projection.activeRun?.status !== "RECEIVING"
  ) {
    return;
  }
  const generation = streamGeneration;
  const session = prototypeStore.getQuerySession();
  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  streamTimer = window.setTimeout(
    () => {
      if (generation !== streamGeneration) {
        return;
      }
      const result = prototypeStore.advanceQueryRun(
        fixtures,
        activeConversationId,
        projection.activeRun.id,
        {
          chunkSize: reduced ? projection.activeRun.totalCharacters : 12,
          expectedEpoch: session.epoch,
        },
      );
      if (!result.ok) {
        queryStreamingSuspended = true;
        queryFeedback =
          result.reason === "STORAGE_UNAVAILABLE"
            ? "接收进度无法保存，已停止继续生成。"
            : "会话授权已变化，接收已停止。";
      }
      if (result.ok && result.run.status === "RECEIVING") {
        const message = prototypeStore.queryProjection(fixtures, {
          conversationId: activeConversationId,
        }).messages.find(item => item.runId === result.run.id);
        const paragraph = message && root.querySelector(`[data-sequence="${message.sequence}"] .message-body p`);
        if (paragraph) {
          // 保持操作节点稳定，避免增量接收不断打断点击、焦点和历史筛选。
          paragraph.innerHTML = escapeHtml(message.content) + '<span class="streaming-cursor" aria-hidden="true"></span>';
          scheduleQueryContinuation();
          return;
        }
      }
      render();
    },
    reduced ? 0 : 180,
  );
}
