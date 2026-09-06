import { decorateExistingPage, renderInventoryPage, renderDemoLoginHelp } from "./full-platform.js";
import { renderConsoleNavigation, renderAccountMenu } from "./navigation.js";
import { workspaceJourney } from "./workspace-journeys.js";
import { governanceDirectory } from "./governance-directories.js";
import { DETAIL_CONTEXT, valueAt } from "./detail-context.js";
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
let consoleLoginFeedback = "";
let consoleLoginName = "";
let consoleLoginTenants = [];
let queryFeedback = "";
let accountFeedback = "";
let knowledgeFeedback = "";
let indexFeedback = "";
let modelFeedback = "";
let assistantFeedback = "";
let evaluationFeedback = "";
let accessGovernanceFeedback = "";
let dataGovernanceFeedback = "";
let nextQueryMode = "normal";
let queryStreamingSuspended = false;
const prototypeStore = createPrototypeStore();
let renderedSessionEpoch = 0;

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
  assistantFeedback = "";
  evaluationFeedback = "";
  accessGovernanceFeedback = "";
  dataGovernanceFeedback = "";
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
// 跨标签页身份改变后，即便 storage 通知尚未派发，旧页面事件也不能沿用管理员身份。
for (const type of ["click", "submit", "change", "input"]) {
  root.addEventListener(type, event => {
    const main = root.querySelector("main[data-screen]");
    const screen = main?.dataset.screen;
    if (["login", "console-login"].includes(screen)) return;
    if (main?.dataset.state === "denied") return;
    const access = prototypeStore.authorizeSession(screen, scopeForScreen(screen));
    if (!access.allowed || prototypeStore.getSession().epoch !== renderedSessionEpoch) {
      event.preventDefault();
      event.stopImmediatePropagation();
      render();
    }
  }, true);
}
window.addEventListener("storage", (event) => {
  if (![PROTOTYPE_STATE_KEY, QUERY_SESSION_KEY, null].includes(event.key)) {
    return;
  }
  cancelQueryTimer();
  prototypeStore.reloadFromStorage();
  render();
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    const accountMenu = root.querySelector(".account-menu[open]");
    if (accountMenu) {
      accountMenu.open = false;
      accountMenu.querySelector("summary")?.focus();
      event.preventDefault();
    }
  }
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
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const tenant = snapshot.tenants.find((item) => item.id === workspaceTenantId());
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
  const snapshot = prototypeStore.getWorkspaceSnapshot();
  const inKnowledge = ["knowledge-overview", "knowledge-sources", "source-add", "source-detail", "processing-quality", "sync-result", "quick-publication", "index-versions", "index-detail", "index-rebuild"].includes(screen);
  for (const item of DETAIL_CONTEXT) {
    const value = item.screens.includes(screen) ? valueAt(snapshot, item.selection) : null;
    if (value) next.searchParams.set(item.parameter, value);
    else next.searchParams.delete(item.parameter);
  }
  for (const [key, value] of Object.entries({
    knowledgeBaseId: inKnowledge ? snapshot.selectedKnowledgeBaseId : null,
    sourceId: screen === "source-detail" ? snapshot.selectedKnowledgeSourceId : screen === "source-add" ? options.updateSourceId : null,
    indexVersionId: screen === "index-detail" ? snapshot.selectedIndexVersionId : null,
  })) {
    if (value) next.searchParams.set(key, value);
    else next.searchParams.delete(key);
  }
  next.searchParams.delete("tenantId");
  for (const [key, detail] of [["auditId", "audit-record-detail"], ["policyId", "access-policy-detail"]]) {
    if (screen === detail && options[key]) next.searchParams.set(key, options[key]);
    else if (screen !== detail) next.searchParams.delete(key);
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
  const querySession = prototypeStore.getSession();
  const params = new URLSearchParams(window.location.search);
  const context = Object.fromEntries(["tenantId", "knowledgeBaseId", "sourceId", "indexVersionId"].filter(key => params.has(key)).map(key => [key, params.get(key)]));
  const detailContext = Object.fromEntries(DETAIL_CONTEXT.filter(item => params.has(item.parameter)).map(item => [item.parameter, params.get(item.parameter)]));
  const contextAllowed = (!Object.keys(context).length && !querySession.tenantId ||
    prototypeStore.applyWorkspaceContext(context).ok) && (!Object.keys(detailContext).length || prototypeStore.applyDetailContext(detailContext).ok);
  const requestedAccess = prototypeStore.authorizeSession(scenario.screen, scopeForScreen(scenario.screen));
  const screen =
    requestedAccess.reason === "AUTH_REQUIRED"
      ? scopeForScreen(scenario.screen) === "query" ? "login" : "console-login"
      : scenario.screen;
  if (screen === "conversation") {
    activeConversationId =
      scenario.conversationId ||
      prototypeStore.getWorkspaceSnapshot().query.currentConversationId ||
      "";
  }
  const demoState = prototypeStore.getWorkspaceSnapshot();
  const access = prototypeStore.authorizeSession(screen, scopeForScreen(screen));
  const objectAllowed = screen !== "conversation" || prototypeStore.queryProjection(fixtures, {
    assistantId: selectedAssistantId,
    conversationId: activeConversationId,
  }).conversationAllowed;
  const renderedState =
    (!access.allowed || !objectAllowed || !contextAllowed)
      ? "denied"
      : screen !== scenario.screen ? "success" : scenario.state;
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
            demoState.selectedTenantId || workspaceTenantId(),
          ),
        }
      : screen === "platform-accounts"
        ? { ...demoState, accountFeedback }
        : screen === "console-login" ? { ...demoState, consoleLoginFeedback, consoleLoginName, consoleLoginTenants } : demoState;
  root.innerHTML = access.allowed && contextAllowed ? renderScreen(screen, renderedState, renderedDemoState) : renderAccessDenied(screen, querySession);
  if (renderedState !== "success" && ["tenant-overview", "knowledge-overview", "processing-quality", "sync-result", "quick-publication", "access-policies", "audit-records"].includes(screen)) {
    root.querySelectorAll("form input, form select, form textarea, form button").forEach(control => { control.disabled = true; });
  }
  renderedSessionEpoch = querySession.epoch;
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

function scopeForScreen(screen) {
  const group = groupByScreen.get(screen);
  if (group?.shell === "device") return "device";
  if (["login", "assistants", "conversation"].includes(screen)) return "query";
  return ["platform-governance", "platform-operations"].includes(group?.id) ? "platform" : "tenant";
}

function workspaceTenantId() {
  return prototypeStore.getSession().tenantId || DEFAULT_TENANT_ID;
}

function workspaceTenant() {
  const tenant = prototypeStore.getWorkspaceSnapshot().tenants.find(item => item.id === workspaceTenantId());
  return { displayName: tenant?.organizationName || "当前组织", administratorLabel: "租户管理员" };
}

function renderAccessDenied(screen, session) {
  const destination = session.accessChannel === "QUERY_APP" ? "assistants"
    : session.tenantId ? "tenant-overview" : "platform-overview";
  return `<main id="main-content" class="boot-state" data-screen="${escapeHtml(screen)}" data-state="denied" tabindex="-1"><h1>无法访问此页面</h1><p>当前账户没有此项访问权限，请返回自己的工作区。</p><div class="form-actions"><button class="button primary" type="button" data-nav-screen="${destination}">返回我的工作区</button><button class="button secondary" type="button" ${session.accessChannel === "QUERY_APP" ? "data-query-logout" : "data-console-logout"}>退出登录</button></div></main>`;
}

function renderScreen(screen, state, demoState) {
  switch (screen) {
    case "access-policies":
    case "audit-records":
    case "audit-record-detail":
      return renderGovernanceDirectory(screen, state, demoState);
    case "tenant-overview":
    case "knowledge-overview":
    case "processing-quality":
    case "sync-result":
    case "quick-publication": {
      const journey = workspaceJourney(screen, demoState, { escapeHtml, statusTag, feedback: knowledgeFeedback });
      return adminShell(screen, journey.title, journey.summary, state === "success" ? journey.action : "", genericState(screen, state) || journey.body, state, "知识工作区");
    }
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
    case "platform-model-connections":
    case "platform-model-connection-detail":
    case "platform-model-deployments":
    case "platform-model-deployment-detail":
    case "model-providers":
    case "model-provider-detail":
    case "model-configurations":
    case "model-configuration-detail":
    case "model-deployments":
    case "model-deployment-detail":
    case "routing-policies":
      return renderModelGovernance(screen, state, demoState);
    case "assistants-directory":
    case "assistant-create":
    case "assistant-draft":
    case "assistant-test":
    case "assistant-evaluation":
    case "assistant-release":
      return renderAssistantDelivery(screen, state, demoState);
    case "evaluation-datasets":
    case "evaluation-dataset-detail":
    case "evaluation-run-create":
    case "evaluation-run-detail":
    case "evaluation-comparison":
    case "evaluation-failure-analysis":
      return renderEvaluationGovernance(screen, state, demoState);
    case "access-policy-detail":
    case "governance-query":
    case "query-diagnostic":
      return renderAccessQueryGovernance(screen, state, demoState);
    case "dataset-schema":
    case "object-storage-scope":
      return renderStructuredObjectGovernance(screen, state, demoState);
    default:
      return renderInventoryPage(
        pageByScreen.get(screen),
        groupByScreen.get(screen),
        pageFixtures.pages[screen],
        state,
        workspaceTenant(),
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
    RUNNING: ["运行中", "warning"],
    COMPLETED: ["已完成", "success"],
    PASSED: ["门禁通过", "success"],
    PENDING: ["待决定", "warning"],
    APPROVED: ["已放行", "success"],
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
    DRAFT: ["草稿", "info"],
    VERIFIED: ["验证通过", "success"],
    TESTING: ["待单题测试", "warning"],
    TESTED: ["单题已验证", "success"],
    EVALUATION_PENDING: ["待冻结评测", "warning"],
    EVALUATED: ["评测完成", "success"],
    COMPLETE: ["发现完成", "success"],
    ANSWERED: ["已回答", "success"],
    REFUSED: ["安全拒答", "warning"],
    OFFLINE: ["已停用", "danger"],
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
    "assistants-directory": "助手目录",
    "assistant-create": "助手",
    "assistant-draft": "助手草稿",
    "assistant-test": "单题测试",
    "assistant-evaluation": "发布门禁",
    "assistant-release": "助手版本",
    "evaluation-datasets": "评测数据集",
    "evaluation-dataset-detail": "评测数据集版本",
    "evaluation-run-create": "评测运行",
    "evaluation-run-detail": "评测运行结果",
    "evaluation-comparison": "版本对比",
    "evaluation-failure-analysis": "失败分类",
    "access-policy-detail": "访问策略版本",
    "governance-query": "查询治理摘要",
    "query-diagnostic": "受权查询诊断",
    "dataset-schema": "结构化数据版本",
    "object-storage-scope": "对象存储范围",
  };
  const subject = labels[screen] || pageFixtures.pages[screen]?.title || "页面";
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
    "access-policy-detail": "还没有可维护的策略草稿。请从策略目录建立新版本。",
    "governance-query": "当前周期还没有已接受的查询运行。配额规则仍可查看。",
    "query-diagnostic": "当前没有可读取的终态诊断事实。",
    "dataset-schema": "当前没有可维护的结构草稿。请先上传受控 UTF-8 CSV。",
    "object-storage-scope": "平台尚未向当前租户签发可用对象范围。",
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
    "access-policy-detail": "返回策略目录",
    "governance-query": "返回工作区",
    "query-diagnostic": "返回查询治理",
    "dataset-schema": "返回数据来源",
    "object-storage-scope": "返回数据来源",
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
    "access-policy-detail": "access-policies",
    "governance-query": "tenant-overview",
    "query-diagnostic": "governance-query",
    "dataset-schema": "knowledge-sources",
    "object-storage-scope": "knowledge-sources",
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
      state === "partial"
        ? '<div class="inline-note warning">上次会话已退出，未发送的输入没有保存。可以重新登录。</div>'
        : "",
      loginFeedback
        ? `<p id="login-feedback" class="form-feedback" data-tone="danger" role="alert">${escapeHtml(loginFeedback)}</p>`
        : '<p id="login-feedback" class="form-feedback" aria-live="polite"></p>',
      '<label class="field"><span>组织编码</span><input class="input" name="tenantCode" autocomplete="organization" value="spruce-demo" required /></label>',
      '<label class="field"><span>登录名</span><input class="input" name="username" autocomplete="username" value="shen.demo" required /></label>',
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
      renderDemoLoginHelp(),
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
  const snapshot = prototypeStore.getWorkspaceSnapshot();
  const tenant = snapshot.tenants.find((item) => item.id === workspaceTenantId());
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

function adminShell(active, title, summary, action, body, state, breadcrumb, scope = "tenant") {
  const demoState = prototypeStore.getWorkspaceSnapshot();
  const platform = scope === "platform";
  return [
    '<div class="admin-shell" data-state="' + state + '" data-scope="' + scope + '">',
    '<aside class="admin-sidebar">',
    '<div class="brand"><span class="brand-mark" aria-hidden="true">R</span><span class="brand-copy"><strong>可信 RAG</strong><small>' +
      (platform ? "平台范围" : "租户工作区") +
      "</small></span></div>",
    renderConsoleNavigation({
      screen: active,
      scope,
      snapshot: demoState,
      tenantLabel: workspaceTenant().displayName,
    }),
    '<div class="admin-account"><strong>' +
      (platform ? "平台管理员" : escapeHtml(workspaceTenant().administratorLabel)) +
      "</strong><small>" +
      (platform ? "仅脱敏聚合" : escapeHtml(workspaceTenant().displayName)) +
      "</small></div>",
    "</aside>",
    '<div class="admin-workspace">',
    '<header class="topbar"><span class="topbar-context">' +
      (platform ? "平台管理 / 不含租户内容" : escapeHtml(workspaceTenant().displayName) + " / 租户管理") +
      '</span><div class="topbar-actions">' +
      (platform ? "" : demoAccountControl()) +
      renderAccountMenu(demoState) + "</div></header>",
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
  const snapshot = prototypeStore.getWorkspaceSnapshot();
  const knowledgeBases = snapshot.knowledgeBases.filter(item => item.tenantId === workspaceTenantId());
  const knowledgeBase = selectedKnowledgeBase(snapshot);
  const indexVersion = selectedIndexVersion(snapshot);
  const models = snapshot.models;
  return {
    "tenant-overview": workspaceTenant().displayName,
    "knowledge-overview": knowledgeBase?.name || "请选择知识库",
    "processing-quality": knowledgeBase?.name || "请选择知识库",
    "sync-result": knowledgeBase?.name || "请选择知识库",
    "quick-publication": knowledgeBase?.name || "请选择知识库",
    "knowledge-directory": `${knowledgeBases.length} 个知识库 / ${knowledgeBases.filter(item => !["READY", "EMPTY"].includes(item.status)).length} 项需处理`,
    "knowledge-sources": `${knowledgeBase?.name || "当前知识库"} / ${knowledgeBase?.sources.length || 0} 个来源`,
    "index-versions": `${knowledgeBase?.activeIndex || "尚无活动索引"} / ${knowledgeBase?.indexVersions.length || 0} 个版本`,
    "index-rebuild": `${knowledgeBase?.name || "当前知识库"} / 新建独立版本`,
    "index-detail": `${indexVersion?.id || "未选择版本"} / ${indexStatusLabel(indexVersion?.status)}`,
    "platform-model-connections": `${models.platformConnections.filter(item => item.status === "PUBLISHED").length} 个已发布连接`,
    "platform-model-connection-detail": modelStatusLabel(models.platformConnections.find(item => item.id === models.selectedPlatformConnectionId)?.status),
    "platform-model-deployments": `${models.platformDeployments.filter(item => item.status === "PUBLISHED").length} 个可授权部署`,
    "platform-model-deployment-detail": modelStatusLabel(models.platformDeployments.find(item => item.id === models.selectedPlatformDeploymentId)?.status),
    "model-providers": `${models.platformConnections.filter(item => item.status === "PUBLISHED").length} 个可用连接`,
    "model-provider-detail": "只显示当前租户获授权目录",
    "model-configurations": `${models.configurations.filter(item => item.status === "ACTIVE").length} 个启用配置`,
    "model-configuration-detail": modelStatusLabel(models.configurations.find(item => item.id === models.selectedConfigurationId)?.status),
    "model-deployments": `${models.deployments.filter(item => item.status === "PUBLISHED").length} 个已发布部署`,
    "model-deployment-detail": modelStatusLabel(models.deployments.find(item => item.id === models.selectedDeploymentId)?.status),
    "routing-policies": modelStatusLabel(models.routingPolicies.find(item => item.id === models.selectedRoutingPolicyId)?.status),
    "assistants-directory": `${snapshot.assistantDelivery.assistants.length} 个稳定助手`,
    "assistant-create": "永久关联一个知识库",
    "assistant-draft": "冻结索引、模型版本和策略",
    "assistant-test": "使用冻结依赖",
    "assistant-evaluation": "质量与安全门禁",
    "assistant-release": "只影响新会话",
    "access-policy-detail": "成员助手授权版本",
    "governance-query": "UTC 日配额 / 脱敏终态摘要",
    "query-diagnostic": "只读历史事实",
    "dataset-schema": "CSV 字段规则与不可变版本",
    "object-storage-scope": "平台签发范围 / 部署定位不可见",
  }[active] || "当前工作区";
}

function selectedKnowledgeBase(demoState) {
  return demoState.knowledgeBases.find(
    item => item.id === demoState.selectedKnowledgeBaseId,
  );
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
  const generic = state === "empty" ? '<section class="state-panel" data-kind="empty"><h2>尚无知识库</h2><p>创建知识库后即可添加资料。</p></section>' : genericState("knowledge-directory", state);
  const tenant = demoState.tenants.find(item => item.id === workspaceTenantId());
  const knowledgeBases = demoState.knowledgeBases.filter(
    item => item.tenantId === workspaceTenantId(),
  );
  const quota = tenant?.quotas.knowledgeBases || 0;
  const quotaReached = knowledgeBases.length >= quota;
  const rows = knowledgeBases
    .map((item) =>
      [
        `<tr data-kb-row="${escapeHtml(item.id)}" data-directory-row data-search-text="${escapeHtml(item.name)}" data-directory-state="${escapeHtml(item.status === "READY" ? "READY" : "ATTENTION")}">`,
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
      '<div class="toolbar"><label class="field"><span>搜索知识库</span><input class="input" type="search" data-directory-search placeholder="按名称搜索" /></label><label class="field compact-field"><span>状态</span><select class="select" data-directory-status><option value="">全部状态</option><option value="ATTENTION">需处理</option><option value="READY">已就绪</option></select></label></div><p data-filter-summary class="form-feedback" role="status"></p>',
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
      (!generic || state === "empty" ? dialog : ""),
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
      `<div class="inline-note${sourceItems.some(item => item.stage === "FAILED") ? " warning" : ""}">活动索引 ${escapeHtml(knowledgeBase?.activeIndex || "尚未建立")} 继续服务；失败或新版本不会原地修改活动索引。</div>`,
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
  const replacementId = new URLSearchParams(location.search).get("sourceId");
  const replacement = knowledgeBase?.sources.find(item => item.id === replacementId);
  const tenant = demoState.tenants.find(item => item.id === knowledgeBase?.tenantId);
  const content = generic || [
    `<form id="source-add-form" class="wizard-workspace source-intake-form"${replacement ? ` data-replace-source="${escapeHtml(replacement.id)}"` : ""}>`,
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
    replacement ? `更新文档 · ${replacement.name}` : "添加数据来源",
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
    `<dl class="description-list"><div class="description-row"><dt>当前版本</dt><dd>${escapeHtml(revision)}</dd></div><div class="description-row"><dt>活动版本</dt><dd>${escapeHtml(source.activeRevision ? formatRevision(source.activeRevision) : "尚无")}</dd></div><div class="description-row"><dt>活动索引</dt><dd>${escapeHtml(knowledgeBase.activeIndex || "尚未建立")} 继续服务</dd></div><div class="description-row"><dt>索引写入</dt><dd>${source.indexed ? "已进入当前清单" : "尚未写入"}</dd></div></dl>`,
    `<div class="form-actions">${operation}<button class="button ${source.stage === "READY" ? "primary" : "secondary"}" type="button" data-primary-action data-nav-screen="source-add" data-update-source="${escapeHtml(source.id)}"${processing || source.kind === "对象存储" ? " disabled" : ""}>更新文档</button><button class="button secondary" type="button" data-rebuild-source${source.stage === "READY" ? "" : " disabled"}>单文件重建索引</button><button class="button subtle" type="button" data-nav-screen="index-rebuild">全库重建</button></div>`,
    knowledgeFeedback ? `<p class="form-feedback" role="status">${escapeHtml(knowledgeFeedback)}</p>` : "",
    "</section>",
  ].join("");
  return adminShell(
    "source-detail",
    "文档详情",
    `${knowledgeBase.name} · 查看版本、处理阶段与失败恢复。`,
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

function modelStatusLabel(status) {
  return {
    DRAFT: "草稿",
    VERIFIED: "验证通过",
    PUBLISHED: "已发布",
    ACTIVE: "已启用",
    COMPLETE: "发现完成",
    READY_FOR_RELEASE: "待发布",
    FAILED: "失败",
    OFFLINE: "已停用",
  }[status] || "待处理";
}

function modelCapabilityLabel(capability) {
  return { CHAT: "Chat", EMBEDDING: "Embedding", RERANK: "Rerank" }[capability] || capability;
}

function selectedModelItem(models, collection, selectedKey) {
  return models[collection].find(item => item.id === models[selectedKey]) || models[collection][0];
}

function modelDirectoryTable(headers, rows) {
  return [
    '<div class="table-shell"><table class="data-table"><thead><tr>',
    headers.map(header => `<th scope="col">${escapeHtml(header)}</th>`).join(""),
    "</tr></thead><tbody>",
    rows.join(""),
    "</tbody></table></div>",
    '<nav class="pagination" aria-label="目录分页"><button class="button secondary" type="button" disabled>上一页</button><span>第 1 / 1 页</span><button class="button secondary" type="button" disabled>下一页</button></nav>',
  ].join("");
}

function renderPlatformModelConnections(state, demoState) {
  const models = demoState.models;
  const rows = models.platformConnections.map(connection =>
    `<tr><td><strong>${escapeHtml(connection.name)}</strong><small>${escapeHtml(connection.providerKind === "OPENAI_COMPATIBLE" ? "OpenAI 兼容运行接口" : connection.providerKind)}</small></td><td>${escapeHtml(modelCapabilityLabel(connection.capability))}</td><td>${statusTag(connection.status)}</td><td><button class="button subtle" type="button" data-select-platform-connection="${escapeHtml(connection.id)}" data-nav-screen="platform-model-connection-detail" aria-label="查看${escapeHtml(connection.name)}">查看</button></td></tr>`
  );
  const content = [
    '<div class="inline-note">标准 Chat 与 Embedding 运行调用使用 OpenAI 兼容协议；Rerank 通过独立检索后处理适配。供应商发现可以使用受限控制面接口，但不会把地址、访问密钥或原始响应展示给租户。</div>',
    modelDirectoryTable(["连接", "运行能力", "状态", "操作"], rows),
    '<dialog id="platform-connection-dialog" class="dialog-surface"><form id="platform-connection-form"><span class="section-kicker">新增连接</span><h2>建立供应商连接草稿</h2><div class="form-grid"><label class="field"><span>连接名称</span><input class="input" name="name" autocomplete="off" required></label><label class="field"><span>运行能力</span><select class="select" name="capability"><option value="CHAT">Chat</option><option value="EMBEDDING">Embedding</option><option value="RERANK">Rerank</option></select></label><label class="field form-grid-wide"><span>运行地址</span><input class="input" name="runtimeBaseUrl" type="url" placeholder="https://models.example.invalid/v1" required><small>仅接受公网 HTTPS；保存后只显示脱敏连接事实。</small></label><label class="field form-grid-wide"><span>一次性访问密钥</span><input class="input" name="accessKey" type="password" autocomplete="new-password" required><small>只用于当前提交，不回显、不写入原型状态。</small></label></div><p id="platform-connection-feedback" class="form-feedback" role="alert"></p><div class="release-actions"><button class="button secondary" type="button" data-close-dialog="platform-connection-dialog">取消</button><button class="button primary" type="submit">保存连接草稿</button></div></form></dialog>',
  ].join("");
  return adminShell(
    "platform-model-connections",
    "平台模型连接",
    "",
    '<button class="button primary" type="button" data-open-platform-connection data-primary-action>新增供应商连接</button>',
    content,
    state,
    "模型治理 / 平台模型连接",
    "platform",
  );
}

function renderPlatformModelConnectionDetail(state, demoState) {
  const connection = selectedModelItem(demoState.models, "platformConnections", "selectedPlatformConnectionId");
  const operation = connection.status === "VERIFIED"
    ? '<button class="button primary" type="button" data-publish-platform-connection data-primary-action>发布连接</button>'
    : '<button class="button primary" type="button" data-probe-platform-connection data-primary-action>执行连接探测</button>';
  const models = connection.discoveredModels.length
    ? connection.discoveredModels.map(item => `<li><span><strong>${escapeHtml(item.name)}</strong><br><small>${escapeHtml(modelCapabilityLabel(item.capability))}${item.embeddingDimension ? ` / ${escapeHtml(item.embeddingDimension)} 维` : ""}</small></span>${statusTag("VERIFIED")}</li>`).join("")
    : '<li><span><strong>尚无发现结果</strong><br><small>先执行连接探测</small></span></li>';
  const content = [
    `<div class="inline-note${connection.status === "FAILED" ? " danger" : ""}">${escapeHtml(connection.probeResult || "尚未执行本版本的连接探测。")}</div>`,
    '<div class="split-detail"><div>',
    `<section class="section-block" data-model-status="${escapeHtml(connection.status)}"><div class="section-heading"><div><span class="section-kicker">连接版本 v${escapeHtml(connection.version)}</span><h2>${escapeHtml(connection.name)}</h2></div>${statusTag(connection.status)}</div><dl class="description-list"><div class="description-row"><dt>供应商类型</dt><dd>OpenAI 兼容</dd></div><div class="description-row"><dt>运行协议</dt><dd>${escapeHtml(connection.runtimeProtocol === "OPENAI_COMPATIBLE" ? "OpenAI 兼容接口" : "独立 Rerank 后处理适配")}</dd></div><div class="description-row"><dt>能力范围</dt><dd>${escapeHtml(modelCapabilityLabel(connection.capability))}</dd></div><div class="description-row"><dt>访问认证</dt><dd>${escapeHtml(connection.authenticationState === "CONFIGURED" ? "已配置（不回显）" : "待配置")}</dd></div><div class="description-row"><dt>最近探测</dt><dd>${escapeHtml(connection.lastProbe || "尚未探测")}</dd></div></dl></section>`,
    `<section class="section-block"><div class="section-heading"><h2>发现模型</h2><span class="status-tag" data-tone="info">${connection.discoveredModels.length} 个</span></div><ul class="selection-list">${models}</ul></section>`,
    '</div><aside><section class="release-decision"><span class="section-kicker">当前动作</span><h2>探测后再发布</h2><p>验证公网 HTTPS、认证、能力和响应边界；发布后仍需建立平台部署并显式授权租户。</p><div class="form-actions">' + operation + '</div>' + (modelFeedback ? `<p class="form-feedback" role="status">${escapeHtml(modelFeedback)}</p>` : "") + '</section></aside></div>',
  ].join("");
  return adminShell("platform-model-connection-detail", "供应商连接探测", "", '<button class="button secondary" type="button" data-nav-screen="platform-model-connections">返回连接目录</button>', content, state, "模型治理 / 连接探测", "platform");
}

function renderPlatformModelDeployments(state, demoState) {
  const models = demoState.models;
  const publishedConnections = models.platformConnections.filter(item => item.status === "PUBLISHED" && item.discoveredModels.length);
  const modelOptions = publishedConnections.flatMap(connection =>
    connection.discoveredModels.map(model => `<option value="${escapeHtml(connection.id)}|${escapeHtml(model.id)}">${escapeHtml(model.name)} / ${escapeHtml(modelCapabilityLabel(model.capability))}</option>`)
  ).join("");
  const rows = models.platformDeployments.map(deployment =>
    `<tr><td><strong>${escapeHtml(deployment.name)}</strong><small>${escapeHtml(deployment.residency)}</small></td><td>${escapeHtml(modelCapabilityLabel(deployment.capability))}</td><td>${escapeHtml(deployment.tenantIds.length)} 个租户</td><td>${statusTag(deployment.status)}</td><td><button class="button subtle" type="button" data-select-platform-deployment="${escapeHtml(deployment.id)}" data-nav-screen="platform-model-deployment-detail" aria-label="查看${escapeHtml(deployment.name)}">查看</button></td></tr>`
  );
  const content = [
    '<div class="inline-note">平台部署发布后只进入可授权目录，不会自动授予租户，也不会修改租户配置、索引或助手。</div>',
    modelDirectoryTable(["部署", "能力", "授权范围", "状态", "操作"], rows),
    `<dialog id="platform-deployment-dialog" class="dialog-surface"><form id="platform-deployment-form"><span class="section-kicker">部署草稿</span><h2>建立可授权模型部署</h2><div class="field-stack"><label class="field"><span>部署名称</span><input class="input" name="name" required></label><label class="field"><span>已发现模型</span><select class="select" name="modelSelection">${modelOptions}</select></label><fieldset class="assistant-scope"><legend>授权租户</legend>${demoState.tenants.filter(item => item.status === "active").map(item => `<label><input type="checkbox" name="authorizeTenant" value="${escapeHtml(item.id)}"${item.id === DEFAULT_TENANT_ID ? " checked" : ""}>${escapeHtml(item.organizationName)}</label>`).join("")}</fieldset></div><p id="platform-deployment-feedback" class="form-feedback" role="alert"></p><div class="release-actions"><button class="button secondary" type="button" data-close-dialog="platform-deployment-dialog">取消</button><button class="button primary" type="submit">保存部署草稿</button></div></form></dialog>`,
  ].join("");
  return adminShell("platform-model-deployments", "平台模型部署", "", '<button class="button primary" type="button" data-open-platform-deployment data-primary-action>建立部署草稿</button>', content, state, "模型治理 / 平台模型部署", "platform");
}

function renderPlatformModelDeploymentDetail(state, demoState) {
  const models = demoState.models;
  const deployment = selectedModelItem(models, "platformDeployments", "selectedPlatformDeploymentId");
  const connection = models.platformConnections.find(item => item.id === deployment.connectionId);
  const operation = deployment.status === "VERIFIED"
    ? '<button class="button primary" type="button" data-publish-platform-deployment data-primary-action>发布部署</button>'
    : deployment.status === "PUBLISHED"
      ? '<button class="button primary" type="button" data-nav-screen="platform-model-deployments" data-primary-action>返回部署目录</button>'
      : '<button class="button primary" type="button" data-verify-platform-deployment data-primary-action>执行能力验证</button>';
  const tenantNames = deployment.tenantIds.map(tenantId => demoState.tenants.find(item => item.id === tenantId)?.organizationName || "已授权租户").join("、");
  const content = [
    `<div class="inline-note${deployment.status === "FAILED" ? " danger" : ""}">${escapeHtml(deployment.verificationResult || "冻结连接版本、模型能力、租户授权和驻留范围后执行验证。")}</div>`,
    '<div class="split-detail"><div>',
    `<section class="section-block" data-model-status="${escapeHtml(deployment.status)}"><div class="section-heading"><div><span class="section-kicker">平台部署</span><h2>${escapeHtml(deployment.name)}</h2></div>${statusTag(deployment.status)}</div><dl class="description-list"><div class="description-row"><dt>连接</dt><dd>${escapeHtml(connection?.name || "不可用连接")}</dd></div><div class="description-row"><dt>能力</dt><dd>${escapeHtml(modelCapabilityLabel(deployment.capability))}</dd></div><div class="description-row"><dt>驻留范围</dt><dd>${escapeHtml(deployment.residency)}</dd></div><div class="description-row"><dt>已授权租户</dt><dd>${escapeHtml(tenantNames || "尚未授权")}</dd></div><div class="description-row"><dt>最近验证</dt><dd>${escapeHtml(deployment.verifiedAt || "尚未验证")}</dd></div></dl></section>`,
    '</div><aside><section class="release-decision"><span class="section-kicker">发布决定</span><h2>验证通过后进入授权目录</h2><p>发布不会自动生成租户配置，也不会触发索引或助手变更。</p><div class="form-actions">' + operation + '</div>' + (modelFeedback ? `<p class="form-feedback" role="status">${escapeHtml(modelFeedback)}</p>` : "") + '</section></aside></div>',
  ].join("");
  return adminShell("platform-model-deployment-detail", "平台部署发布", "", '<button class="button secondary" type="button" data-nav-screen="platform-model-deployments">返回部署目录</button>', content, state, "模型治理 / 平台部署发布", "platform");
}

function renderTenantModelProviders(state, demoState) {
  const models = demoState.models;
  const connections = models.platformConnections.filter(item => item.status === "PUBLISHED");
  const selectedId = models.selectedTenantConnectionId || connections[0]?.id;
  const rows = connections.map(connection => {
    const discovery = models.tenantDiscoveries.find(item => item.tenantId === workspaceTenantId() && item.connectionId === connection.id);
    return `<tr><td><strong>${escapeHtml(connection.name)}</strong><small>平台已授权连接</small></td><td>${escapeHtml(modelCapabilityLabel(connection.capability))}</td><td>${statusTag(discovery?.status || "DRAFT")}</td><td><button class="button subtle" type="button" data-select-tenant-connection="${escapeHtml(connection.id)}" data-nav-screen="model-provider-detail" aria-label="查看${escapeHtml(connection.name)}">查看</button></td></tr>`;
  });
  const content = [
    '<section class="section-block compact-toolbar"><label class="field"><span>本次发现连接</span><select class="select" id="tenant-discovery-connection">' + connections.map(item => `<option value="${escapeHtml(item.id)}"${item.id === selectedId ? " selected" : ""}>${escapeHtml(item.name)} / ${escapeHtml(modelCapabilityLabel(item.capability))}</option>`).join("") + '</select></label><p class="muted">只返回平台已发布且授权给当前租户的模型；不展示地址、访问认证或厂商原始响应。</p></section>',
    modelDirectoryTable(["供应商连接", "能力", "发现状态", "操作"], rows),
  ].join("");
  return adminShell("model-providers", "租户模型供应商", "", '<button class="button primary" type="button" data-discover-tenant-models data-primary-action>执行模型发现</button>', content, state, "模型治理 / 供应商发现");
}

function renderTenantModelProviderDetail(state, demoState) {
  const models = demoState.models;
  const connection = models.platformConnections.find(item => item.id === models.selectedTenantConnectionId) || models.platformConnections.find(item => item.status === "PUBLISHED");
  const discovery = models.tenantDiscoveries.find(item => item.tenantId === workspaceTenantId() && item.connectionId === connection?.id);
  const deployments = (discovery?.deploymentIds || []).map(id => models.platformDeployments.find(item => item.id === id)).filter(Boolean);
  const items = deployments.length
    ? deployments.map(deployment => `<li><span><strong>${escapeHtml(deployment.name)}</strong><br><small>${escapeHtml(modelCapabilityLabel(deployment.capability))} / ${escapeHtml(deployment.residency)}</small></span><button class="button secondary" type="button" data-use-catalog-model="${escapeHtml(deployment.id)}">使用此模型</button></li>`).join("")
    : '<li><span><strong>尚无已授权模型</strong><br><small>请联系平台管理员核对部署发布和租户授权。</small></span></li>';
  const content = [
    `<div class="inline-note">发现结果只包含当前租户可使用的目录名称、能力和驻留范围；${escapeHtml(connection?.name || "当前连接")} 的底层地址与认证信息保持隐藏。</div>`,
    `<div class="split-detail"><section class="section-block" data-model-status="${escapeHtml(discovery?.status || "DRAFT")}"><div class="section-heading"><div><span class="section-kicker">发现结果</span><h2>${escapeHtml(connection?.name || "供应商连接")}</h2></div>${statusTag(discovery?.status || "DRAFT")}</div><ul class="selection-list">${items}</ul></section><aside><section class="release-decision"><span class="section-kicker">下一步</span><h2>创建租户模型配置</h2><p>选择目录模型后仍需建立、验证并启用租户配置，发现不会自动改变索引或助手。</p>${modelFeedback ? `<p class="form-feedback" role="status">${escapeHtml(modelFeedback)}</p>` : ""}</section></aside></div>`,
  ].join("");
  return adminShell("model-provider-detail", "供应商发现结果", "", '<button class="button primary" type="button" data-rediscover-tenant-models data-primary-action>重新探测</button>', content, state, "模型治理 / 供应商发现结果");
}

function renderModelConfigurations(state, demoState) {
  const models = demoState.models;
  const available = models.platformDeployments.filter(item => item.status === "PUBLISHED" && item.tenantIds.includes(workspaceTenantId()));
  const selected = models.selectedCatalogDeploymentId || available[0]?.id;
  const rows = models.configurations.filter(item => item.tenantId === workspaceTenantId()).map(configuration =>
    `<tr><td><strong>${escapeHtml(configuration.name)}</strong><small>当前 v${escapeHtml(configuration.activeRevision || "—")}</small></td><td>${escapeHtml(modelCapabilityLabel(configuration.capability))}</td><td>${statusTag(configuration.status)}</td><td><button class="button subtle" type="button" data-select-model-configuration="${escapeHtml(configuration.id)}" data-nav-screen="model-configuration-detail" aria-label="查看${escapeHtml(configuration.name)}">查看</button></td></tr>`
  );
  const content = [
    '<div class="inline-note">配置只引用当前租户已获授权的目录模型。访问密钥提交后不回显；新版本验证并启用前，旧启用版本继续服务。</div>',
    modelDirectoryTable(["配置", "能力", "状态", "操作"], rows),
    `<dialog id="model-configuration-dialog" class="dialog-surface"><form id="model-configuration-form"><span class="section-kicker">创建配置</span><h2>建立受控模型配置</h2><div class="field-stack"><label class="field"><span>配置名称</span><input class="input" name="name" required></label><label class="field"><span>已授权目录模型</span><select class="select" name="platformDeploymentId">${available.map(item => `<option value="${escapeHtml(item.id)}"${item.id === selected ? " selected" : ""}>${escapeHtml(item.name)} / ${escapeHtml(modelCapabilityLabel(item.capability))}</option>`).join("")}</select></label></div><p id="model-configuration-feedback" class="form-feedback" role="alert"></p><div class="release-actions"><button class="button secondary" type="button" data-close-dialog="model-configuration-dialog">取消</button><button class="button primary" type="submit">保存配置草稿</button></div></form></dialog>`,
  ].join("");
  return adminShell("model-configurations", "模型配置目录", "", '<button class="button primary" type="button" data-open-model-configuration data-primary-action>创建模型配置</button>', content, state, "模型治理 / 模型配置");
}

function renderModelConfigurationDetail(state, demoState) {
  const models = demoState.models;
  const configuration = selectedModelItem(models, "configurations", "selectedConfigurationId");
  const platformDeployment = models.platformDeployments.find(item => item.id === configuration.platformDeploymentId);
  const pending = configuration.pendingRevision;
  const operation = pending?.status === "VERIFIED"
    ? '<button class="button primary" type="button" data-enable-model-configuration data-primary-action>启用配置</button>'
    : pending
      ? '<button class="button primary" type="button" data-verify-model-configuration data-primary-action>执行配置验证</button>'
      : '<button class="button primary" type="button" data-open-model-revision data-primary-action>新建配置版本</button><button class="button secondary" type="button" data-open-model-key>轮换访问密钥</button>';
  const parameters = pending?.parameters || configuration.parameters || {};
  const parameterFacts = configuration.capability === "EMBEDDING"
    ? `<div class="description-row"><dt>向量维度</dt><dd>${escapeHtml(parameters.embeddingDimension)} 维</dd></div><div class="description-row"><dt>距离度量</dt><dd>${escapeHtml(parameters.distanceMetric)}</dd></div>`
    : configuration.capability === "CHAT"
      ? `<div class="description-row"><dt>Temperature</dt><dd>${escapeHtml(parameters.temperature)}</dd></div><div class="description-row"><dt>最大输出</dt><dd>${escapeHtml(parameters.maxOutputTokens)} tokens</dd></div>`
      : `<div class="description-row"><dt>重排候选</dt><dd>${escapeHtml(parameters.candidateCount)} 个</dd></div><div class="description-row"><dt>最终证据</dt><dd>${escapeHtml(parameters.evidenceCount)} 个</dd></div>`;
  const revisionFields = configuration.capability === "EMBEDDING"
    ? `<label class="field"><span>向量维度</span><input class="input" name="embeddingDimension" type="number" value="${escapeHtml(parameters.embeddingDimension)}"></label><label class="field"><span>距离度量</span><select class="select" name="distanceMetric"><option>COSINE</option><option>IP</option><option>L2</option></select></label>`
    : configuration.capability === "CHAT"
      ? `<label class="field"><span>Temperature</span><select class="select" name="temperature"><option value="0">0</option><option value="0.2">0.2</option></select></label><label class="field"><span>最大输出 tokens</span><input class="input" name="maxOutputTokens" type="number" value="${escapeHtml(parameters.maxOutputTokens)}"></label>`
      : `<label class="field"><span>重排候选数量</span><input class="input" name="candidateCount" type="number" value="${escapeHtml(parameters.candidateCount)}"></label><label class="field"><span>最终证据数量</span><input class="input" name="evidenceCount" type="number" value="${escapeHtml(parameters.evidenceCount)}"></label>`;
  const impact = configuration.capability === "EMBEDDING"
    ? "启用维度、规范化或距离变化会自动创建全库重建候选，但不会自动激活索引。"
    : "Chat 或 Rerank 版本启用后不重建向量，只要求助手新版本重新评测。";
  const content = [
    `<div class="inline-note${pending?.status === "FAILED" ? " danger" : ""}">${escapeHtml(pending?.result || impact)}</div>`,
    '<div class="split-detail"><div>',
    `<section class="section-block" data-model-status="${escapeHtml(configuration.status)}"><div class="section-heading"><div><span class="section-kicker">${escapeHtml(modelCapabilityLabel(configuration.capability))} 配置</span><h2>${escapeHtml(configuration.name)}</h2></div>${statusTag(configuration.status)}</div><dl class="description-list"><div class="description-row"><dt>目录模型</dt><dd>${escapeHtml(platformDeployment?.name || "不可用模型")}</dd></div><div class="description-row"><dt>当前启用版本</dt><dd>v${escapeHtml(configuration.activeRevision || "尚未启用")}</dd></div><div class="description-row"><dt>待验证版本</dt><dd>${pending ? `v${escapeHtml(pending.number)} / ${escapeHtml(modelStatusLabel(pending.status))}` : "无"}</dd></div><div class="description-row"><dt>访问认证</dt><dd>${escapeHtml(configuration.authenticationState === "PLATFORM_MANAGED" ? "平台托管" : configuration.authenticationState === "ROTATION_PENDING" ? "新密钥待验证" : "已配置（不回显）")}</dd></div>${parameterFacts}</dl></section>`,
    '</div><aside><section class="release-decision"><span class="section-kicker">配置版本</span><h2>验证通过后再启用</h2><p>' + escapeHtml(impact) + '</p><div class="form-actions">' + operation + '</div>' + (modelFeedback ? `<p class="form-feedback" role="status">${escapeHtml(modelFeedback)}</p>` : "") + '</section></aside></div>',
    `<dialog id="model-key-dialog" class="dialog-surface"><form id="model-key-form"><span class="section-kicker">密钥轮换</span><h2>提交新的访问密钥</h2><p>旧版本继续服务；新密钥只用于当前提交，验证成功并启用后才替换。</p><label class="field"><span>新访问密钥</span><input class="input" name="accessKey" type="password" autocomplete="new-password" required></label><p id="model-key-feedback" class="form-feedback" role="alert"></p><div class="release-actions"><button class="button secondary" type="button" data-close-dialog="model-key-dialog">取消</button><button class="button primary" type="submit">保存待验证版本</button></div></form></dialog>`,
    `<dialog id="model-revision-dialog" class="dialog-surface"><form id="model-revision-form"><span class="section-kicker">新配置版本</span><h2>修改 ${escapeHtml(modelCapabilityLabel(configuration.capability))} 参数</h2><div class="form-grid wizard-field-grid">${revisionFields}</div><p id="model-revision-feedback" class="form-feedback" role="alert"></p><div class="release-actions"><button class="button secondary" type="button" data-close-dialog="model-revision-dialog">取消</button><button class="button primary" type="submit">保存待验证版本</button></div></form></dialog>`,
  ].join("");
  return adminShell("model-configuration-detail", "模型配置向导", "", '<button class="button secondary" type="button" data-nav-screen="model-configurations">返回配置目录</button>', content, state, "模型治理 / 模型配置向导");
}

function renderTenantModelDeployments(state, demoState) {
  const models = demoState.models;
  const configurations = models.configurations.filter(item => item.tenantId === workspaceTenantId() && item.status === "ACTIVE");
  const rows = models.deployments.filter(item => item.tenantId === workspaceTenantId()).map(deployment =>
    `<tr><td><strong>${escapeHtml(deployment.name)}</strong><small>${escapeHtml(deployment.residency)}</small></td><td>${escapeHtml(modelCapabilityLabel(deployment.capability))}</td><td>${statusTag(deployment.status)}</td><td><button class="button subtle" type="button" data-select-tenant-deployment="${escapeHtml(deployment.id)}" data-nav-screen="model-deployment-detail" aria-label="查看${escapeHtml(deployment.name)}">查看</button></td></tr>`
  );
  const content = [
    '<div class="inline-note">部署冻结一个已启用配置版本。发布不会自动修改助手；停用前必须核对被固定的助手和路由影响。</div>',
    modelDirectoryTable(["部署", "能力", "状态", "操作"], rows),
    `<dialog id="tenant-deployment-dialog" class="dialog-surface"><form id="tenant-deployment-form"><span class="section-kicker">部署草稿</span><h2>建立租户模型部署</h2><div class="field-stack"><label class="field"><span>部署名称</span><input class="input" name="name" required></label><label class="field"><span>启用配置</span><select class="select" name="configurationId">${configurations.map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)} / ${escapeHtml(modelCapabilityLabel(item.capability))}</option>`).join("")}</select></label></div><p id="tenant-deployment-feedback" class="form-feedback" role="alert"></p><div class="release-actions"><button class="button secondary" type="button" data-close-dialog="tenant-deployment-dialog">取消</button><button class="button primary" type="submit">保存部署草稿</button></div></form></dialog>`,
  ].join("");
  return adminShell("model-deployments", "租户模型部署", "", '<button class="button primary" type="button" data-open-tenant-deployment data-primary-action>建立部署草稿</button>', content, state, "模型治理 / 租户模型部署");
}

function renderTenantModelDeploymentDetail(state, demoState) {
  const models = demoState.models;
  const deployment = selectedModelItem(models, "deployments", "selectedDeploymentId");
  const configuration = models.configurations.find(item => item.id === deployment.configurationId);
  const assistantNames = deployment.assistantIds.map(id => id === "assistant-employee-service" ? "员工服务助手" : "已绑定助手");
  const routingNames = models.routingPolicies
    .filter(policy => policy.primaryDeploymentId === deployment.id || policy.fallbackDeploymentId === deployment.id)
    .map(policy => policy.name);
  const operation = deployment.status === "VERIFIED"
    ? '<button class="button primary" type="button" data-publish-tenant-deployment data-primary-action>发布部署</button>'
    : deployment.status === "PUBLISHED"
      ? '<button class="button primary" type="button" data-open-deployment-stop data-primary-action>停用部署</button>'
      : deployment.status === "OFFLINE"
        ? '<button class="button primary" type="button" data-nav-screen="model-deployments" data-primary-action>返回部署目录</button>'
        : '<button class="button primary" type="button" data-verify-tenant-deployment data-primary-action>执行部署验证</button>';
  const content = [
    `<div class="inline-note${deployment.status === "FAILED" ? " danger" : ""}">${escapeHtml(deployment.verificationResult || "验证能力、预算、驻留和安全样本后才能发布。")}</div>`,
    '<div class="split-detail"><div>',
    `<section class="section-block" data-model-status="${escapeHtml(deployment.status)}"><div class="section-heading"><div><span class="section-kicker">${escapeHtml(modelCapabilityLabel(deployment.capability))} 部署</span><h2>${escapeHtml(deployment.name)}</h2></div>${statusTag(deployment.status)}</div><dl class="description-list"><div class="description-row"><dt>冻结配置</dt><dd>${escapeHtml(configuration?.name || "配置不可用")} / v${escapeHtml(configuration?.activeRevision || "—")}</dd></div><div class="description-row"><dt>驻留范围</dt><dd>${escapeHtml(deployment.residency)}</dd></div><div class="description-row"><dt>助手影响</dt><dd>${escapeHtml(assistantNames.length ? assistantNames.join("、") : "当前无助手固定")}</dd></div><div class="description-row"><dt>最近验证</dt><dd>${escapeHtml(deployment.verifiedAt || "尚未验证")}</dd></div></dl></section>`,
    '</div><aside><section class="release-decision"><span class="section-kicker">部署操作</span><h2>发布不等于助手切换</h2><p>助手版本必须显式冻结部署；停用会使固定此部署且无可用路由的问答失败关闭。</p><div class="form-actions">' + operation + '</div>' + (modelFeedback ? `<p class="form-feedback" role="status">${escapeHtml(modelFeedback)}</p>` : "") + '</section></aside></div>',
    `<dialog id="deployment-stop-dialog" class="dialog-surface"><form id="deployment-stop-form"><span class="section-kicker">影响确认</span><h2>停用 ${escapeHtml(deployment.name)}？</h2><p>受影响助手：${escapeHtml(assistantNames.length ? assistantNames.join("、") : "当前无助手")}；受影响路由：${escapeHtml(routingNames.length ? routingNames.join("、") : "当前无路由")}。停用后新调用失败关闭，不会静默换成不同能力模型。</p><label class="check-line"><input type="checkbox" name="acknowledged"><span>我已确认受影响助手与路由</span></label><p id="deployment-stop-feedback" class="form-feedback" role="alert"></p><div class="release-actions"><button class="button secondary" type="button" data-close-dialog="deployment-stop-dialog">取消</button><button class="button primary" type="submit">确认停用</button></div></form></dialog>`,
  ].join("");
  return adminShell("model-deployment-detail", "租户部署验证", "", '<button class="button secondary" type="button" data-nav-screen="model-deployments">返回部署目录</button>', content, state, "模型治理 / 租户部署验证");
}

function renderRoutingPolicies(state, demoState) {
  const models = demoState.models;
  const policy = selectedModelItem(models, "routingPolicies", "selectedRoutingPolicyId");
  if (!policy) return adminShell("routing-policies", "模型路由策略", "", "", `<form class="section-block" id="create-routing-form"><h2>建立第一条路由策略</h2><label class="field"><span>路由名称</span><input class="input" name="name" value="对话主备路由" minlength="2" maxlength="80" required></label><p>建立草稿后选择同能力的主备部署，模拟通过才能发布。</p><p class="form-feedback" role="alert">${escapeHtml(modelFeedback)}</p><div class="form-actions"><button class="button secondary" type="button" data-nav-screen="model-deployments">查看部署</button><button class="button primary" type="submit"${state === "success" ? "" : " disabled"}>创建路由草稿</button></div></form>`, state, "模型治理 / 路由策略");
  const deployments = models.deployments.filter(item => item.tenantId === workspaceTenantId() && item.status === "PUBLISHED");
  const simulation = policy.simulation
    ? `<div class="metric-grid"><article class="metric-card"><span>模拟样本</span><strong>${escapeHtml(policy.simulation.passed)} / ${escapeHtml(policy.simulation.total)}</strong></article><article class="metric-card"><span>预计月度消耗</span><strong>${escapeHtml(policy.simulation.estimatedBudget)}</strong></article><article class="metric-card"><span>路由 p95</span><strong>${escapeHtml(policy.simulation.p95Ms)} ms</strong></article></div>`
    : '<p class="muted">尚未运行本版本模拟。</p>';
  const content = [
    `<div class="inline-note">有限回退只能选择同能力、同驻留、已发布部署；不会跨 Chat、Embedding、Rerank 或租户边界静默降级。</div>`,
    `<div class="split-detail"><form id="routing-policy-form" class="section-block" data-model-status="${escapeHtml(policy.status)}"><div class="section-heading"><div><span class="section-kicker">策略 v${escapeHtml(policy.version)}</span><h2>${escapeHtml(policy.name)}</h2></div>${statusTag(policy.status)}</div><div class="form-grid"><label class="field"><span>主部署</span><select class="select" name="primaryDeploymentId">${deployments.map(item => `<option value="${escapeHtml(item.id)}"${item.id === policy.primaryDeploymentId ? " selected" : ""}>${escapeHtml(item.name)} / ${escapeHtml(modelCapabilityLabel(item.capability))}</option>`).join("")}</select></label><label class="field"><span>有限回退</span><select class="select" name="fallbackDeploymentId">${deployments.map(item => `<option value="${escapeHtml(item.id)}"${item.id === policy.fallbackDeploymentId ? " selected" : ""}>${escapeHtml(item.name)} / ${escapeHtml(modelCapabilityLabel(item.capability))}</option>`).join("")}</select></label><label class="field"><span>月度预算上限</span><input class="input" name="monthlyBudget" type="number" value="${escapeHtml(policy.monthlyBudget)}"></label></div><p id="routing-policy-feedback" class="form-feedback" role="alert"></p><div class="form-actions"><button class="button secondary" type="submit">运行路由模拟</button><button class="button primary" type="button" data-publish-routing-policy data-primary-action${policy.status !== "READY_FOR_RELEASE" ? " disabled" : ""}>发布路由策略</button></div></form><aside><section class="section-block"><span class="section-kicker">模拟结果</span><h2>成本、延迟与失败边界</h2>${simulation}${modelFeedback ? `<p class="form-feedback" role="status">${escapeHtml(modelFeedback)}</p>` : ""}</section></aside></div>`,
  ].join("");
  return adminShell("routing-policies", "模型路由策略", "", '<button class="button secondary" type="button" data-nav-screen="model-deployments">返回部署目录</button>', content, state, "模型治理 / 模型路由策略");
}

function renderModelGovernance(screen, state, demoState) {
  const platform = screen.startsWith("platform-model-");
  const generic = genericState(screen, state);
  if (generic) {
    return adminShell(screen, pageFixtures.pages[screen].title, "", "", generic, state, "模型治理", platform ? "platform" : "tenant");
  }
  const detailCollection = {
    "platform-model-connection-detail": ["platformConnections", "platform-model-connections"],
    "platform-model-deployment-detail": ["platformDeployments", "platform-model-deployments"],
    "model-provider-detail": ["platformConnections", "model-providers"],
    "model-configuration-detail": ["configurations", "model-configurations"],
    "model-deployment-detail": ["deployments", "model-deployments"],
  }[screen];
  if (detailCollection && !demoState.models[detailCollection[0]].length) return adminShell(screen, pageFixtures.pages[screen].title, "", "", statePanel("empty", "当前还没有可查看的模型记录", "请先从目录选择或建立模型记录。", "返回目录", detailCollection[1]), state, "模型治理", platform ? "platform" : "tenant");
  return {
    "platform-model-connections": renderPlatformModelConnections,
    "platform-model-connection-detail": renderPlatformModelConnectionDetail,
    "platform-model-deployments": renderPlatformModelDeployments,
    "platform-model-deployment-detail": renderPlatformModelDeploymentDetail,
    "model-providers": renderTenantModelProviders,
    "model-provider-detail": renderTenantModelProviderDetail,
    "model-configurations": renderModelConfigurations,
    "model-configuration-detail": renderModelConfigurationDetail,
    "model-deployments": renderTenantModelDeployments,
    "model-deployment-detail": renderTenantModelDeploymentDetail,
    "routing-policies": renderRoutingPolicies,
  }[screen](state, demoState);
}

function assistantStageContext(snapshot, statuses) {
  const assistants = snapshot.assistantDelivery.assistants;
  const selected = assistants
    .flatMap(assistant => assistant.versions.map(version => ({ assistant, version })))
    .find(item => item.version.id === snapshot.assistantDelivery.selectedVersionId);
  if (selected && statuses.includes(selected.version.status)) return selected;
  if (new URLSearchParams(location.search).has("assistantVersionId")) return null;
  return assistants
    .flatMap(assistant => assistant.versions.map(version => ({ assistant, version })))
    .find(item => statuses.includes(item.version.status)) || null;
}

function assistantNextScreen(status) {
  return {
    DRAFT: "assistant-draft",
    TESTING: "assistant-test",
    TESTED: "assistant-test",
    EVALUATION_PENDING: "assistant-evaluation",
    EVALUATED: "assistant-evaluation",
    READY_FOR_RELEASE: "assistant-release",
  }[status] || "assistant-draft";
}

function renderAssistantDirectory(state, snapshot) {
  const blocked = state !== "success";
  const rows = snapshot.assistantDelivery.assistants.map(assistant => {
    const published = assistant.versions.find(item => item.id === assistant.publishedVersionId);
    const candidate = assistant.versions.find(item => !["PUBLISHED", "SUPERSEDED"].includes(item.status));
    const knowledgeBase = snapshot.knowledgeBases.find(item => item.id === assistant.knowledgeBaseId);
    const actionLabel = candidate ? "继续处理" : "创建新版本";
    return `<tr><td data-label="助手"><strong>${escapeHtml(assistant.name)}</strong><small>${escapeHtml(assistant.summary)}</small></td><td data-label="关联知识库">${escapeHtml(knowledgeBase?.name || "知识库不可用")}</td><td data-label="已发布版本">${published ? `v${escapeHtml(published.number)}` : "尚未发布"}</td><td data-label="当前候选">${candidate ? `${statusTag(candidate.status)} <span class="mono">v${escapeHtml(candidate.number)}</span>` : "—"}</td><td data-label="操作" class="table-action"><button class="button secondary compact" type="button" data-manage-assistant-id="${escapeHtml(assistant.id)}"${candidate ? ` data-assistant-version-id="${escapeHtml(candidate.id)}" data-assistant-target="${assistantNextScreen(candidate.status)}"` : " data-create-assistant-version"}${blocked ? " disabled" : ""}>${actionLabel}</button></td></tr>`;
  }).join("");
  const body = [
    state === "partial" ? '<div class="inline-note warning">候选状态正在刷新，目录保持只读。</div>' : "",
    state === "disabled" ? '<div class="inline-note warning">平台已关闭助手交付，现有发布版本继续服务。</div>' : "",
    assistantFeedback ? `<p class="form-feedback" role="status">${escapeHtml(assistantFeedback)}</p>` : "",
    `<div class="table-shell"><table class="data-table"><thead><tr><th style="width:28%">助手</th><th>关联知识库</th><th>已发布版本</th><th>当前候选</th><th style="text-align:right">操作</th></tr></thead><tbody>${rows}</tbody></table><div class="pagination" aria-label="助手目录分页"><span>第 1 页，共 ${snapshot.assistantDelivery.assistants.length} 个助手</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>`,
  ].join("");
  return adminShell(
    "assistants-directory",
    "助手目录",
    "",
    `<button class="button primary" type="button" data-nav-screen="assistant-create" data-primary-action${blocked ? " disabled" : ""}>新建助手</button>`,
    body,
    state,
    "助手交付 / 助手目录",
  );
}

function renderAssistantCreate(state, snapshot) {
  const blocked = state !== "success";
  const knowledgeBases = snapshot.knowledgeBases.filter(item => item.tenantId === workspaceTenantId() && item.indexVersions.some(version => version.id === item.activeIndex && version.status === "ACTIVE"));
  const body = [
    state === "partial" ? '<div class="inline-note warning">知识库目录正在刷新，暂不能创建助手。</div>' : "",
    state === "disabled" ? '<div class="inline-note warning">当前租户没有助手交付权限。</div>' : "",
    '<div class="split-detail">',
    '<form id="assistant-create-form" class="section-block">',
    '<div class="section-heading"><div><span class="section-kicker">基本信息</span><h2>创建助手</h2></div><span class="status-tag" data-tone="info">创建后进入草稿</span></div>',
    '<div class="form-grid"><label class="field"><span>助手名称</span><input class="input" name="name" minlength="2" maxlength="80" value="员工政策助手" autocomplete="off" required></label><label class="field"><span>关联知识库</span><input class="input" name="knowledgeBaseName" type="search" list="assistant-knowledge-base-options" value="员工服务手册" autocomplete="off" required></label></div>',
    `<datalist id="assistant-knowledge-base-options">${knowledgeBases.map(item => `<option value="${escapeHtml(item.name)}"></option>`).join("")}</datalist>`,
    '<label class="field"><span>用途说明</span><textarea class="textarea" name="summary" rows="3" minlength="8" maxlength="200" required>回答员工制度和办事流程问题，并提供可定位引用。</textarea></label>',
    '<p id="assistant-create-feedback" class="form-feedback" role="alert"></p>',
    `<div class="form-actions"><button class="button secondary" type="button" data-nav-screen="assistants-directory">取消</button><button class="button primary" type="submit" data-primary-action${blocked ? " disabled" : ""}>创建助手</button></div></form>`,
    '<aside><section class="release-decision"><span class="section-kicker">永久边界</span><h2>关联后不能更换知识库</h2><dl class="description-list"><dt>可选范围</dt><dd>当前租户、索引就绪</dd><dt>版本变化</dt><dd>只替换索引或模型版本</dd><dt>需要换库</dt><dd>创建新的助手</dd></dl></section></aside></div>',
  ].join("");
  return adminShell("assistant-create", "创建助手", "", '<button class="button secondary" type="button" data-nav-screen="assistants-directory">返回目录</button>', body, state, "助手交付 / 创建助手");
}

function renderAssistantDraft(state, snapshot) {
  const context = assistantStageContext(snapshot, ["DRAFT"]);
  if (!context) return adminShell("assistant-draft", "助手草稿配置", "", "", statePanel("empty", "没有待配置草稿", "从助手目录创建新助手或新版本。", "返回助手目录", "assistants-directory"), state, "助手交付 / 草稿配置");
  const { assistant, version } = context;
  const knowledgeBase = snapshot.knowledgeBases.find(item => item.id === assistant.knowledgeBaseId);
  const indexVersions = knowledgeBase.indexVersions.filter(item => !["CANCELLED", "BLOCKED", "BUILDING"].includes(item.status));
  const configurations = capability => snapshot.models.configurations.filter(item => item.tenantId === assistant.tenantId && item.capability === capability && item.status === "ACTIVE");
  const options = capability => configurations(capability).map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)} · r${escapeHtml(item.activeRevision)}</option>`).join("");
  const blocked = state !== "success";
  const body = [
    state === "partial" ? '<div class="inline-note warning">模型目录正在刷新，草稿保持未保存。</div>' : "",
    state === "disabled" ? '<div class="inline-note warning">当前候选存在不可用依赖，请先恢复模型或索引。</div>' : "",
    `<div class="inline-note">${escapeHtml(assistant.name)} v${escapeHtml(version.number)} 永久关联 ${escapeHtml(knowledgeBase.name)}。</div>`,
    '<form id="assistant-draft-form" class="section-block" data-assistant-version-id="' + escapeHtml(version.id) + '">',
    '<div class="section-heading"><div><span class="section-kicker">冻结配置</span><h2>选择本版本依赖</h2></div>' + statusTag(version.status) + '</div>',
    `<input type="hidden" name="knowledgeBaseId" value="${escapeHtml(assistant.knowledgeBaseId)}"><div class="form-grid"><label class="field"><span>索引版本</span><select class="select" name="indexVersionId" required>${indexVersions.map(item => `<option value="${escapeHtml(item.id)}"${item.status === "READY_FOR_REVIEW" ? " selected" : ""}>${escapeHtml(item.id)} · ${escapeHtml(item.status === "ACTIVE" ? "当前活动" : "评测通过")}</option>`).join("")}</select></label><label class="field"><span>Chat 配置</span><select class="select" name="chatConfigurationId" required>${options("CHAT")}</select></label><label class="field"><span>Embedding 配置</span><select class="select" name="embeddingConfigurationId" required>${options("EMBEDDING")}</select></label><label class="field"><span>Rerank 配置</span><select class="select" name="rerankConfigurationId" required>${options("RERANK")}</select></label><label class="field"><span>检索策略</span><select class="select" name="retrievalPolicy"><option value="HYBRID_RRF_RERANK_V3">混合召回 + RRF + Rerank v3</option></select></label><label class="field"><span>引用策略</span><select class="select" name="citationPolicy"><option value="REQUIRED_V2">必须提供可定位引用 v2</option></select></label><label class="field"><span>拒答策略</span><select class="select" name="refusalPolicy"><option value="CALIBRATED_V5">冻结评测校准阈值 v5</option></select></label></div>`,
    '<p id="assistant-draft-feedback" class="form-feedback" role="alert"></p>',
    `<div class="form-actions"><button class="button secondary" type="button" data-nav-screen="assistants-directory">稍后处理</button><button class="button primary" type="submit" data-primary-action${blocked ? " disabled" : ""}>保存草稿</button></div></form>`,
  ].join("");
  return adminShell("assistant-draft", `${assistant.name} v${version.number}`, "", '<button class="button secondary" type="button" data-nav-screen="assistants-directory">返回目录</button>', body, state, "助手交付 / 草稿配置");
}

function renderAssistantTest(state, snapshot) {
  const context = assistantStageContext(snapshot, ["TESTING", "TESTED"]);
  if (!context) return adminShell("assistant-test", "助手单题测试", "", "", statePanel("empty", "没有待测试版本", "先保存助手草稿并冻结依赖。", "返回助手目录", "assistants-directory"), state, "助手交付 / 单题测试");
  const { assistant, version } = context;
  const result = version.testResult;
  const blocked = state !== "success";
  const resultPanel = result ? [
    `<section class="section-block"><div class="section-heading"><div><span class="section-kicker">测试结果</span><h2>${escapeHtml(result.question)}</h2></div>${statusTag(result.status === "ANSWERED" ? "READY" : "BLOCKED")}</div><p class="result-copy">${escapeHtml(result.answer)}</p>`,
    result.citations.length ? `<ol class="selection-list">${result.citations.map(item => `<li><strong>${escapeHtml(item.source)}</strong><span>${escapeHtml(item.location)}</span></li>`).join("")}</ol>` : '<div class="inline-note">没有足够证据，未生成引用。</div>',
    `<div class="form-actions"><button class="button secondary" type="button" data-rerun-assistant-test>重新测试</button><button class="button primary" type="button" data-add-assistant-evaluation data-assistant-version-id="${escapeHtml(version.id)}" data-primary-action${blocked || result.addedToEvaluation ? " disabled" : ""}>${result.addedToEvaluation ? "已加入评测集" : "加入评测集"}</button></div></section>`,
  ].join("") : [
    `<form id="assistant-test-form" class="section-block" data-assistant-version-id="${escapeHtml(version.id)}"><div class="section-heading"><div><span class="section-kicker">冻结依赖</span><h2>${escapeHtml(assistant.name)} v${escapeHtml(version.number)}</h2></div>${statusTag(version.status)}</div><label class="field"><span>测试问题</span><textarea class="textarea" name="question" rows="3" required>年假最多可以结转几天？</textarea></label><p id="assistant-test-feedback" class="form-feedback" role="alert"></p><div class="form-actions"><button class="button primary" type="submit" data-primary-action${blocked ? " disabled" : ""}>运行单题测试</button></div></form>`,
  ].join("");
  const body = `<div class="inline-note">本次只读取 <span class="mono">${escapeHtml(version.frozen?.indexVersionId || "未冻结")}</span>，测试数据不写入用户会话。</div>${assistantFeedback ? `<p class="form-feedback" role="status">${escapeHtml(assistantFeedback)}</p>` : ""}${resultPanel}`;
  return adminShell("assistant-test", "助手单题测试", "", '<button class="button secondary" type="button" data-nav-screen="assistant-draft">查看冻结配置</button>', body, state, "助手交付 / 单题测试");
}

function renderAssistantEvaluation(state, snapshot) {
  const context = assistantStageContext(snapshot, ["EVALUATION_PENDING", "EVALUATED"]);
  if (!context) return adminShell("assistant-evaluation", "助手发布门禁", "", "", statePanel("empty", "没有待评测版本", "先把单题测试加入冻结评测集。", "返回助手目录", "assistants-directory"), state, "助手交付 / 发布门禁");
  const { assistant, version } = context;
  const evaluation = version.evaluation;
  const blocked = state !== "success";
  const action = evaluation
    ? `<button class="button primary" type="button" data-request-assistant-release data-assistant-version-id="${escapeHtml(version.id)}" data-primary-action${blocked || evaluation.status !== "PASSED" ? " disabled" : ""}>请求发布</button>`
    : `<button class="button primary" type="button" data-run-assistant-evaluation data-assistant-version-id="${escapeHtml(version.id)}" data-primary-action${blocked ? " disabled" : ""}>运行冻结评测</button>`;
  const body = [
    evaluation?.status === "FAILED" ? '<div class="inline-note danger">发现越权泄漏或质量未达标，本版本不能请求发布。</div>' : '<div class="inline-note">越权泄漏必须为 0；其余指标达到 M6 门禁后才可请求发布。</div>',
    `<section class="section-block"><div class="section-heading"><div><span class="section-kicker">评测集 v${escapeHtml(version.evaluationDataset?.version || 6)}</span><h2>${escapeHtml(assistant.name)} v${escapeHtml(version.number)}</h2></div>${statusTag(version.status)}</div>`,
    evaluation ? qualityGrid(evaluation.metrics, true) : '<div class="metric-grid"><article class="metric-card"><span>冻结问题</span><strong>101</strong></article><article class="metric-card"><span>可回答</span><strong>60</strong></article><article class="metric-card"><span>应拒答</span><strong>20</strong></article><article class="metric-card"><span>安全样本</span><strong>10</strong></article></div>',
    assistantFeedback ? `<p class="form-feedback" role="status">${escapeHtml(assistantFeedback)}</p>` : "",
    `<div class="form-actions">${evaluation?.status === "FAILED" ? `<button class="button secondary" type="button" data-run-assistant-evaluation data-assistant-version-id="${escapeHtml(version.id)}">重新运行评测</button>` : ""}${action}</div></section>`,
  ].join("");
  return adminShell("assistant-evaluation", "助手发布门禁", "", '<button class="button secondary" type="button" data-nav-screen="assistant-test">返回单题测试</button>', body, state, "助手交付 / 发布门禁");
}

function renderAssistantRelease(state, snapshot) {
  const context = assistantStageContext(snapshot, ["READY_FOR_RELEASE"]);
  if (!context) return adminShell("assistant-release", "发布助手版本", "", "", statePanel("empty", "没有可发布版本", "只有通过冻结评测并完成发布请求的版本会出现在这里。", "返回助手目录", "assistants-directory"), state, "助手交付 / 发布确认");
  const { assistant, version } = context;
  const knowledgeBase = snapshot.knowledgeBases.find(item => item.id === assistant.knowledgeBaseId);
  const frozen = version.frozen;
  const blocked = state !== "success";
  const body = [
    `<div class="inline-note${blocked ? " warning" : ""}">${blocked ? "当前状态不可发布，发布指针保持不变。" : "发布只更新新会话使用的版本；已有会话继续固定原版本。"}</div>`,
    `<section class="section-block"><div class="section-heading"><div><span class="section-kicker">发布门禁通过</span><h2>${escapeHtml(assistant.name)} v${escapeHtml(version.number)}</h2></div>${statusTag(version.status)}</div>${qualityGrid(version.evaluation.metrics, true)}</section>`,
    '<div class="split-detail"><section class="section-block"><h2>冻结配置</h2><dl class="description-list">',
    `<dt>知识库</dt><dd>${escapeHtml(knowledgeBase?.name || assistant.knowledgeBaseId)}（永久关联）</dd><dt>索引</dt><dd><span class="mono">${escapeHtml(frozen.indexVersionId)}</span></dd><dt>Chat</dt><dd><span class="mono">${escapeHtml(frozen.configurationRevisions.chat.id)} / r${escapeHtml(frozen.configurationRevisions.chat.revision)}</span></dd><dt>Embedding</dt><dd><span class="mono">${escapeHtml(frozen.configurationRevisions.embedding.id)} / r${escapeHtml(frozen.configurationRevisions.embedding.revision)}</span></dd><dt>Rerank</dt><dd><span class="mono">${escapeHtml(frozen.configurationRevisions.rerank.id)} / r${escapeHtml(frozen.configurationRevisions.rerank.revision)}</span></dd><dt>检索策略</dt><dd>${escapeHtml(frozen.retrievalPolicy)}</dd><dt>引用 / 拒答</dt><dd>${escapeHtml(frozen.citationPolicy)} / ${escapeHtml(frozen.refusalPolicy)}</dd></dl></section>`,
    `<aside><section class="release-decision${blocked ? " blocked" : ""}"><span class="section-kicker">发布影响</span><h2>新会话切换到 v${escapeHtml(version.number)}</h2><p>旧会话不迁移；授权撤销仍立即阻断正文与引用。</p><button class="button primary" type="button" data-confirm-release data-assistant-version-id="${escapeHtml(version.id)}" data-primary-action${blocked ? " disabled" : ""}>发布助手版本</button>${assistantFeedback ? `<p class="form-feedback" role="status">${escapeHtml(assistantFeedback)}</p>` : ""}</section></aside></div>`,
    `<dialog id="release-dialog" class="dialog-surface"><div class="dialog-body"><span class="section-kicker">发布确认</span><h2>发布 ${escapeHtml(assistant.name)} v${escapeHtml(version.number)}？</h2><dl class="description-list"><dt>新会话</dt><dd>${escapeHtml(version.id)} / ${escapeHtml(frozen.indexVersionId)}</dd><dt>已有会话</dt><dd>继续固定原版本</dd></dl><p id="assistant-release-feedback" class="form-feedback" role="alert"></p><div class="release-actions"><button class="button secondary" type="button" data-cancel-assistant-release>取消</button><button class="button primary" type="button" data-publish-assistant-version="${escapeHtml(version.id)}">确认发布</button></div></div></dialog>`,
  ].join("");
  return adminShell("assistant-release", "发布助手版本", "", '<button class="button secondary" type="button" data-nav-screen="assistant-evaluation">查看评测结果</button>', body, state, "助手交付 / 发布确认");
}

function renderAssistantDelivery(screen, state, snapshot) {
  const generic = genericState(screen, state);
  if (generic) return adminShell(screen, pageFixtures.pages[screen].title, "", "", generic, state, "助手交付");
  return {
    "assistants-directory": renderAssistantDirectory,
    "assistant-create": renderAssistantCreate,
    "assistant-draft": renderAssistantDraft,
    "assistant-test": renderAssistantTest,
    "assistant-evaluation": renderAssistantEvaluation,
    "assistant-release": renderAssistantRelease,
  }[screen](state, snapshot);
}

function evaluationDatasetContext(snapshot) {
  const dataset = snapshot.evaluation.datasets.find(item => item.id === snapshot.evaluation.selectedDatasetId)
    || snapshot.evaluation.datasets[0];
  const version = dataset?.versions.find(item => item.id === snapshot.evaluation.selectedDatasetVersionId)
    || dataset?.versions.at(-1);
  return dataset && version ? { dataset, version } : null;
}

function evaluationAssistantVersionLabel(snapshot, versionId) {
  for (const assistant of snapshot.assistantDelivery.assistants) {
    const version = assistant.versions.find(item => item.id === versionId);
    if (version) return `${assistant.name} v${version.number}`;
  }
  return versionId;
}

function evaluationMetricValue(key, value) {
  if (["recallAt10", "citationPrecision", "refusalAccuracy"].includes(key)) return `${Math.round(value * 100)}%`;
  if (["retrievalP95Ms", "firstTokenP95Ms"].includes(key)) return `${value} ms`;
  if (key === "consistency") return value === "PASSED" ? "稳定" : "存在矛盾";
  return String(value);
}

function evaluationMetricGrid(metrics) {
  const entries = [
    ["Recall@10", "recallAt10"],
    ["引用正确率", "citationPrecision"],
    ["拒答准确率", "refusalAccuracy"],
    ["越权泄漏", "unauthorizedLeakage"],
    ["检索 p95", "retrievalP95Ms"],
    ["首 Token p95", "firstTokenP95Ms"],
    ["五次一致性", "consistency"],
  ];
  return `<div class="quality-grid evaluation-quality-grid">${entries.map(([label, key]) => `<div class="quality-item"><span>${label}</span><strong>${escapeHtml(evaluationMetricValue(key, metrics[key]))}</strong></div>`).join("")}</div>`;
}

function renderEvaluationDatasets(state, snapshot) {
  const blocked = state !== "success";
  const rows = snapshot.evaluation.datasets.map(dataset => {
    const published = dataset.versions.find(item => item.id === dataset.publishedVersionId);
    const draft = dataset.versions.find(item => item.status === "DRAFT");
    const version = draft || published || dataset.versions.at(-1);
    const knowledgeBase = snapshot.knowledgeBases.find(item => item.id === dataset.knowledgeBaseId);
    return `<tr data-evaluation-dataset-row="${escapeHtml(dataset.id)}"><td data-label="数据集"><span class="primary-cell"><strong>${escapeHtml(dataset.name)}</strong><small>${escapeHtml(knowledgeBase?.name || "知识库不可用")}</small></span></td><td data-label="已发布版本">${published ? `v${escapeHtml(published.number)} · ${published.samples.length} 题` : "尚未发布"}</td><td data-label="当前草稿">${draft ? `${statusTag(draft.status)} v${escapeHtml(draft.number)} · ${draft.samples.length} 题` : "—"}</td><td data-label="操作"><button class="button secondary compact" type="button" data-select-evaluation-dataset="${escapeHtml(dataset.id)}" data-evaluation-version-id="${escapeHtml(version.id)}" data-nav-screen="evaluation-dataset-detail"${blocked ? " disabled" : ""}>${draft ? "继续维护" : "查看版本"}</button></td></tr>`;
  }).join("");
  const publishedVersions = snapshot.evaluation.datasets.flatMap(dataset => dataset.versions
    .filter(version => version.status === "PUBLISHED")
    .map(version => ({ dataset, version })));
  const body = [
    state === "partial" ? '<div class="inline-note warning">数据集目录正在刷新，当前版本保持只读。</div>' : "",
    state === "disabled" ? '<div class="inline-note warning">平台已关闭评测治理，既有评测结论仍可查看。</div>' : "",
    evaluationFeedback ? `<p class="form-feedback" role="status">${escapeHtml(evaluationFeedback)}</p>` : "",
    `<div class="table-shell"><table class="data-table"><thead><tr><th style="width:32%">数据集</th><th>已发布版本</th><th>当前草稿</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table><div class="pagination" aria-label="评测数据集分页"><span>第 1 页，共 ${snapshot.evaluation.datasets.length} 个数据集</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>`,
    `<dialog id="evaluation-dataset-dialog" class="dialog-surface" aria-labelledby="evaluation-dataset-title"><form id="evaluation-dataset-form"><div class="dialog-heading"><span class="section-kicker">独立质量资产</span><h2 id="evaluation-dataset-title">新建评测数据集</h2></div><div class="form-grid"><label class="field"><span>数据集名称</span><input class="input" name="name" minlength="2" maxlength="80" value="制度专项回归" required></label><label class="field"><span>关联知识库</span><select class="select" name="knowledgeBaseId">${snapshot.knowledgeBases.filter(item => item.tenantId === workspaceTenantId()).map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}</option>`).join("")}</select></label><label class="field"><span>起始方式</span><select class="select" name="templateVersionId"><option value="">建立空白数据集</option>${publishedVersions.map(({ dataset, version }) => `<option value="${escapeHtml(version.id)}"${version.id === "eval-set-v6" ? " selected" : ""}>复制 ${escapeHtml(dataset.name)} v${escapeHtml(version.number)}</option>`).join("")}</select></label></div><p class="inline-note compact-note">复制会创建独立草稿；发布版本不会被修改。</p><p id="evaluation-dataset-feedback" class="form-feedback" role="alert"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-close-evaluation-dataset>取消</button><button class="button primary" type="submit">创建数据集</button></div></form></dialog>`,
  ].join("");
  return adminShell("evaluation-datasets", "评测数据集", "", `<button class="button primary" type="button" data-open-evaluation-dataset data-primary-action${blocked ? " disabled" : ""}>新建评测数据集</button>`, body, state, "评测治理 / 数据集");
}

function renderEvaluationDatasetDetail(state, snapshot) {
  const context = evaluationDatasetContext(snapshot);
  if (!context) return adminShell("evaluation-dataset-detail", "评测数据集版本", "", "", statePanel("empty", "没有评测数据集", "先创建一个数据集。", "返回数据集", "evaluation-datasets"), state, "评测治理 / 数据集版本");
  const { dataset, version } = context;
  const blocked = state !== "success";
  const composition = version.composition;
  const sampleRows = version.samples.slice(0, 6).map(sample => `<tr><td data-label="类型">${escapeHtml(sample.label)}</td><td data-label="问题摘要"><strong>${escapeHtml(sample.questionSummary)}</strong></td><td data-label="期望结果">${sample.expectedOutcome === "ANSWER" ? "回答并引用" : "拒答且不泄漏"}</td><td data-label="期望证据">${sample.expectedEvidence.length ? escapeHtml(sample.expectedEvidence.join("、")) : "不应返回正文"}</td></tr>`).join("");
  const primary = version.status === "DRAFT"
    ? `<button class="button primary" type="button" data-publish-evaluation-version="${escapeHtml(version.id)}" data-primary-action${blocked ? " disabled" : ""}>发布数据集版本</button>`
    : `<button class="button primary" type="button" data-create-evaluation-version="${escapeHtml(dataset.id)}" data-primary-action${blocked ? " disabled" : ""}>创建新草稿版本</button>`;
  const body = [
    `<div class="inline-note${version.status === "DRAFT" ? " warning" : ""}">${version.status === "DRAFT" ? "发布前可维护样本；发布后本版本不可变。" : "此版本已冻结，新的修改必须创建草稿版本。"}</div>`,
    `<div class="metric-grid"><article class="metric-card"><span>可回答</span><strong>${composition.answerable}</strong><small>门槛 60</small></article><article class="metric-card"><span>证据不足拒答</span><strong>${composition.refusal}</strong><small>门槛 20</small></article><article class="metric-card"><span>权限拒绝</span><strong>${composition.authorization}</strong><small>门槛 10</small></article><article class="metric-card"><span>多轮指代</span><strong>${composition.multiTurn}</strong><small>门槛 10</small></article></div>`,
    `<section class="section-block"><div class="section-heading"><div><span class="section-kicker">${escapeHtml(dataset.name)}</span><h2>v${escapeHtml(version.number)} · ${version.samples.length} 个冻结样本</h2></div>${statusTag(version.status)}</div><div class="table-shell"><table class="data-table"><thead><tr><th>类型</th><th>问题摘要</th><th>期望结果</th><th>期望证据</th></tr></thead><tbody>${sampleRows}</tbody></table><div class="pagination" aria-label="评测样本分页"><span>第 1 页，共 ${version.samples.length} 个样本</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div>${evaluationFeedback ? `<p class="form-feedback" role="status">${escapeHtml(evaluationFeedback)}</p>` : ""}<div class="form-actions">${version.status === "DRAFT" ? '<button class="button secondary" type="button" data-open-evaluation-sample>新增样本</button>' : ""}${primary}</div></section>`,
    `<dialog id="evaluation-sample-dialog" class="dialog-surface" aria-labelledby="evaluation-sample-title"><form id="evaluation-sample-form" data-evaluation-version-id="${escapeHtml(version.id)}"><div class="dialog-heading"><span class="section-kicker">仅保存合成摘要</span><h2 id="evaluation-sample-title">新增评测样本</h2></div><label class="field"><span>样本类型</span><select class="select" name="type"><option value="ANSWERABLE">可回答</option><option value="REFUSAL">证据不足拒答</option><option value="AUTHORIZATION">权限拒绝</option><option value="MULTI_TURN">多轮指代</option></select></label><label class="field"><span>问题摘要</span><textarea class="textarea" name="question" rows="3" required>年假结转规则专项验证</textarea></label><label class="field"><span>期望证据标识</span><input class="input" name="expectedEvidence" value="src-policy-pdf#p6"></label><p id="evaluation-sample-feedback" class="form-feedback" role="alert"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-close-evaluation-sample>取消</button><button class="button primary" type="submit">保存样本</button></div></form></dialog>`,
  ].join("");
  return adminShell("evaluation-dataset-detail", `${dataset.name} v${version.number}`, "", '<button class="button secondary" type="button" data-nav-screen="evaluation-datasets">返回数据集</button>', body, state, "评测治理 / 数据集版本");
}

function renderEvaluationRunCreate(state, snapshot) {
  const blocked = state !== "success";
  const assistantVersions = snapshot.assistantDelivery.assistants.flatMap(assistant => assistant.versions
    .filter(version => Boolean(version.frozen))
    .map(version => ({ assistant, version })));
  const datasetVersions = snapshot.evaluation.datasets.flatMap(dataset => dataset.versions
    .filter(version => version.status === "PUBLISHED")
    .map(version => ({ dataset, version })));
  const selectedDataset = datasetVersions.find(item => item.version.id === snapshot.evaluation.selectedDatasetVersionId) || datasetVersions[0];
  const body = [
    state === "partial" ? '<div class="inline-note warning">版本目录正在刷新，当前选择尚未提交。</div>' : "",
    '<div class="split-detail"><form id="evaluation-run-form" class="section-block"><div class="section-heading"><div><span class="section-kicker">冻结一次运行</span><h2>选择评测输入</h2></div><span class="status-tag" data-tone="info">运行中不漂移</span></div>',
    `<div class="form-grid"><label class="field"><span>助手版本</span><select class="select" name="assistantVersionId">${assistantVersions.map(({ assistant, version }) => `<option value="${escapeHtml(version.id)}"${version.id === "asv-0013" ? " selected" : ""}>${escapeHtml(assistant.name)} v${escapeHtml(version.number)} · ${escapeHtml(version.frozen.indexVersionId)}</option>`).join("")}</select></label><label class="field"><span>已发布数据集版本</span><select class="select" name="datasetVersionId">${datasetVersions.map(({ dataset, version }) => `<option value="${escapeHtml(version.id)}"${version.id === selectedDataset?.version.id ? " selected" : ""}>${escapeHtml(dataset.name)} v${escapeHtml(version.number)} · ${version.samples.length} 题</option>`).join("")}</select></label></div>`,
    `<p id="evaluation-run-feedback" class="form-feedback" role="alert">${escapeHtml(evaluationFeedback)}</p><div class="form-actions"><button class="button secondary" type="button" data-nav-screen="evaluation-datasets">取消</button><button class="button primary" type="submit" data-primary-action${blocked ? " disabled" : ""}>运行评测</button></div></form>`,
    '<aside><section class="release-decision"><span class="section-kicker">运行边界</span><h2>助手与数据集必须冻结</h2><dl class="description-list"><dt>配置变化</dt><dd>不影响本次运行</dd><dt>安全门禁</dt><dd>越权泄漏必须为 0</dd><dt>对比条件</dt><dd>必须使用同一数据集版本</dd><dt>原始内容</dt><dd>失败页只显示合成摘要</dd></dl></section></aside></div>',
  ].join("");
  return adminShell("evaluation-run-create", "发起评测", "", '<button class="button secondary" type="button" data-nav-screen="evaluation-datasets">返回数据集</button>', body, state, "评测治理 / 发起运行");
}

function renderEvaluationRunDetail(state, snapshot) {
  const run = snapshot.evaluation.runs.find(item => item.id === snapshot.evaluation.selectedRunId) || snapshot.evaluation.runs[0];
  if (!run) return adminShell("evaluation-run-detail", "评测运行", "", "", statePanel("empty", "尚无评测运行", "选择冻结版本发起第一次评测。", "发起评测", "evaluation-run-create"), state, "评测治理 / 运行详情");
  const blocked = state !== "success";
  const stages = ["冻结输入", "检索与生成", "质量与安全评分", "形成门禁结论"];
  const stageList = `<ol class="process-steps evaluation-run-steps" aria-label="评测运行阶段">${stages.map((label, index) => `<li data-step-state="${run.status === "COMPLETED" || index < run.stageIndex ? "complete" : index === run.stageIndex ? "current" : "upcoming"}"><span>${index + 1}</span><strong>${label}</strong></li>`).join("")}</ol>`;
  const baselineOptions = snapshot.evaluation.runs.filter(item => item.id !== run.id && item.status === "COMPLETED" && item.frozen.datasetVersionId === run.frozen.datasetVersionId);
  const operation = run.status === "RUNNING"
    ? `<button class="button primary" type="button" data-advance-evaluation-run="${escapeHtml(run.id)}" data-primary-action${blocked ? " disabled" : ""}>继续运行评测</button>`
    : `<button class="button secondary" type="button" data-nav-screen="evaluation-failure-analysis" data-select-evaluation-run="${escapeHtml(run.id)}">查看失败分类</button><button class="button primary" type="button" data-open-evaluation-comparison data-primary-action${blocked || baselineOptions.length === 0 ? " disabled" : ""}>比较版本</button>`;
  const body = [
    `<div class="inline-note${run.gate === "FAILED" ? " danger" : run.status === "RUNNING" ? " warning" : ""}">${run.status === "RUNNING" ? `正在执行 ${escapeHtml(run.stage)}；已冻结输入不会随活动版本变化。` : run.gate === "PASSED" ? "全部 M6 质量、安全、性能和一致性门禁通过。" : "至少一项门禁未通过，本运行不能支持发布。"}</div>`,
    `<div class="split-detail"><div><section class="section-block"><div class="section-heading"><div><span class="section-kicker">${escapeHtml(run.id)}</span><h2>${escapeHtml(run.name)}</h2></div>${statusTag(run.status)}</div>${stageList}<div class="progress-track" aria-label="评测运行进度"><div class="progress-value" style="width:${escapeHtml(run.progress)}%"></div></div></section>${run.metrics ? `<section class="section-block"><div class="section-heading"><h2>门禁指标</h2>${statusTag(run.gate)}</div>${evaluationMetricGrid(run.metrics)}</section>` : ""}</div>`,
    `<aside><section class="section-block"><h2>冻结输入</h2><dl class="description-list"><dt>助手版本</dt><dd>${escapeHtml(evaluationAssistantVersionLabel(snapshot, run.frozen.assistantVersionId))}</dd><dt>数据集版本</dt><dd><span class="mono">${escapeHtml(run.frozen.datasetVersionId)}</span></dd><dt>索引版本</dt><dd><span class="mono">${escapeHtml(run.frozen.indexVersionId)}</span></dd><dt>模型版本</dt><dd>Chat r${escapeHtml(run.frozen.configurationRevisions.chat.revision)} / Embedding r${escapeHtml(run.frozen.configurationRevisions.embedding.revision)} / Rerank r${escapeHtml(run.frozen.configurationRevisions.rerank.revision)}</dd></dl></section><section class="release-decision"><span class="section-kicker">下一步</span><h2>${run.status === "RUNNING" ? "完成全部四个阶段" : "同数据集比较基线"}</h2><div class="form-actions">${operation}</div>${evaluationFeedback ? `<p class="form-feedback" role="status">${escapeHtml(evaluationFeedback)}</p>` : ""}</section></aside></div>`,
    `<dialog id="evaluation-comparison-dialog" class="dialog-surface" aria-labelledby="evaluation-comparison-title"><form id="evaluation-comparison-form" data-candidate-run-id="${escapeHtml(run.id)}"><div class="dialog-heading"><span class="section-kicker">同一数据集版本</span><h2 id="evaluation-comparison-title">选择对比基线</h2></div><label class="field"><span>基线运行</span><select class="select" name="baselineRunId">${baselineOptions.map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}</option>`).join("")}</select></label><p id="evaluation-comparison-feedback" class="form-feedback" role="alert"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-close-evaluation-comparison>取消</button><button class="button primary" type="submit">生成对比</button></div></form></dialog>`,
  ].join("");
  return adminShell("evaluation-run-detail", "评测运行详情", "", '<button class="button secondary" type="button" data-nav-screen="evaluation-run-create">发起新评测</button>', body, state, "评测治理 / 运行详情");
}

function renderEvaluationComparison(state, snapshot) {
  const comparison = snapshot.evaluation.comparisons.find(item => item.id === snapshot.evaluation.selectedComparisonId) || snapshot.evaluation.comparisons[0];
  if (!comparison) return adminShell("evaluation-comparison", "版本对比", "", "", statePanel("empty", "尚无版本对比", "从已完成运行中选择同一数据集基线。", "查看运行", "evaluation-run-detail"), state, "评测治理 / 版本对比");
  const baseline = snapshot.evaluation.runs.find(item => item.id === comparison.baselineRunId);
  const candidate = snapshot.evaluation.runs.find(item => item.id === comparison.candidateRunId);
  const blocked = state !== "success" || comparison.decision !== "PENDING";
  const metrics = [
    ["Recall@10", "recallAt10"], ["引用正确率", "citationPrecision"], ["拒答准确率", "refusalAccuracy"],
    ["越权泄漏", "unauthorizedLeakage"], ["检索 p95", "retrievalP95Ms"], ["首 Token p95", "firstTokenP95Ms"], ["五次一致性", "consistency"],
  ];
  const rows = metrics.map(([label, key]) => `<tr><td data-label="指标"><strong>${label}</strong></td><td data-label="基线">${escapeHtml(evaluationMetricValue(key, baseline.metrics[key]))}</td><td data-label="候选">${escapeHtml(evaluationMetricValue(key, candidate.metrics[key]))}</td><td data-label="判断">${key === "unauthorizedLeakage" ? (candidate.metrics[key] === 0 ? "无泄漏" : "安全回退") : key === "consistency" ? (candidate.metrics[key] === "PASSED" ? "稳定" : "回退") : comparison.noQualityRegression ? "未回退" : "需复核"}</td></tr>`).join("");
  const body = [
    `<div class="inline-note${comparison.safeToApprove ? "" : " danger"}">${comparison.safeToApprove ? "候选无安全或质量回退，可以形成放行决定。" : "发现安全或质量回退，只能阻断并继续修复。"}</div>`,
    `<div class="split-detail"><section class="section-block"><div class="section-heading"><div><span class="section-kicker">数据集 ${escapeHtml(comparison.datasetVersionId)}</span><h2>${escapeHtml(candidate.name)}</h2></div>${statusTag(comparison.decision)}</div><div class="table-shell"><table class="data-table"><thead><tr><th>指标</th><th>基线</th><th>候选</th><th>判断</th></tr></thead><tbody>${rows}</tbody></table></div></section>`,
    `<aside><section class="release-decision${comparison.safeToApprove ? "" : " blocked"}"><span class="section-kicker">门禁决定</span><h2>${comparison.decision === "PENDING" ? "形成评测结论" : comparison.decision === "APPROVED" ? "候选已放行" : "候选已阻断"}</h2><dl class="description-list"><dt>基线</dt><dd>${escapeHtml(baseline.name)}</dd><dt>候选</dt><dd>${escapeHtml(candidate.name)}</dd><dt>数据集</dt><dd>${escapeHtml(comparison.datasetVersionId)}</dd></dl><div class="form-actions"><button class="button secondary" type="button" data-evaluation-decision="BLOCK" data-evaluation-comparison-id="${escapeHtml(comparison.id)}"${blocked ? " disabled" : ""}>阻断候选</button><button class="button primary" type="button" data-evaluation-decision="APPROVE" data-evaluation-comparison-id="${escapeHtml(comparison.id)}" data-primary-action${blocked || !comparison.safeToApprove ? " disabled" : ""}>形成门禁决定</button></div>${evaluationFeedback ? `<p class="form-feedback" role="status">${escapeHtml(evaluationFeedback)}</p>` : ""}</section></aside></div>`,
  ].join("");
  return adminShell("evaluation-comparison", "评测版本对比", "", '<button class="button secondary" type="button" data-nav-screen="evaluation-run-detail">返回运行</button>', body, state, "评测治理 / 版本对比");
}

function renderEvaluationFailureAnalysis(state, snapshot) {
  const run = snapshot.evaluation.runs.find(item => item.id === snapshot.evaluation.selectedRunId && item.status === "COMPLETED") || snapshot.evaluation.runs.find(item => item.id === "eval-run-v13");
  const selected = run?.failures.find(item => item.category === snapshot.evaluation.selectedFailureCategory) || run?.failures[0];
  if (!run || !selected) return adminShell("evaluation-failure-analysis", "失败分析", "", "", statePanel("empty", "没有失败分类", "当前运行没有可归类失败。", "返回运行", "evaluation-run-detail"), state, "评测治理 / 失败分析");
  const blocked = state !== "success";
  const rows = run.failures.map(item => `<tr data-failure-category="${escapeHtml(item.category)}"><td data-label="类别"><strong>${escapeHtml(item.label)}</strong></td><td data-label="样本数">${item.sampleCount}</td><td data-label="建议处理">${escapeHtml(item.suggestedAction)}</td><td data-label="操作"><button class="button secondary compact" type="button" data-select-evaluation-failure="${escapeHtml(item.category)}" data-evaluation-run-id="${escapeHtml(run.id)}"${blocked ? " disabled" : ""}>查看</button></td></tr>`).join("");
  const remediation = snapshot.evaluation.remediations.find(item => item.runId === run.id && item.category === selected.category && item.status === "OPEN");
  const body = [
    '<div class="inline-note">这里只显示合成问题摘要、失败类别和数量，不展示真实问题、回答或引用正文。</div>',
    `<div class="split-detail"><section class="section-block"><div class="section-heading"><div><span class="section-kicker">${escapeHtml(run.name)}</span><h2>7 个失败样本 / 3 类原因</h2></div>${statusTag(run.gate)}</div><div class="table-shell"><table class="data-table"><thead><tr><th>类别</th><th>样本数</th><th>建议处理</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table><div class="pagination" aria-label="失败分类分页"><span>第 1 页，共 ${run.failures.length} 个分类</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div></section>`,
    `<aside><section class="release-decision"><span class="section-kicker">当前类别</span><h2>${escapeHtml(selected.label)}</h2><dl class="description-list"><dt>失败样本</dt><dd>${selected.sampleCount}</dd><dt>建议修复</dt><dd>${escapeHtml(selected.suggestedAction)}</dd><dt>修复事项</dt><dd>${remediation ? `${escapeHtml(remediation.title)} · ${escapeHtml(remediation.owner)}` : "尚未创建"}</dd></dl><button class="button primary" type="button" data-open-evaluation-remediation data-primary-action${blocked || Boolean(remediation) ? " disabled" : ""}>${remediation ? "修复事项已创建" : "创建修复事项"}</button>${evaluationFeedback ? `<p class="form-feedback" role="status">${escapeHtml(evaluationFeedback)}</p>` : ""}</section></aside></div>`,
    `<dialog id="evaluation-remediation-dialog" class="dialog-surface" aria-labelledby="evaluation-remediation-title"><form id="evaluation-remediation-form" data-evaluation-run-id="${escapeHtml(run.id)}" data-failure-category="${escapeHtml(selected.category)}"><div class="dialog-heading"><span class="section-kicker">${escapeHtml(selected.label)}</span><h2 id="evaluation-remediation-title">创建修复事项</h2></div><label class="field"><span>事项名称</span><input class="input" name="title" minlength="4" maxlength="100" value="${escapeHtml(selected.suggestedAction)}" required></label><label class="field"><span>负责人</span><input class="input" name="owner" minlength="2" maxlength="40" value="知识治理组" required></label><p id="evaluation-remediation-feedback" class="form-feedback" role="alert"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-close-evaluation-remediation>取消</button><button class="button primary" type="submit">创建事项</button></div></form></dialog>`,
  ].join("");
  return adminShell("evaluation-failure-analysis", "评测失败分析", "", '<button class="button secondary" type="button" data-nav-screen="evaluation-run-detail">返回运行</button>', body, state, "评测治理 / 失败分析");
}

function renderEvaluationGovernance(screen, state, snapshot) {
  const generic = genericState(screen, state);
  if (generic) return adminShell(screen, pageFixtures.pages[screen].title, "", "", generic, state, "评测治理");
  return {
    "evaluation-datasets": renderEvaluationDatasets,
    "evaluation-dataset-detail": renderEvaluationDatasetDetail,
    "evaluation-run-create": renderEvaluationRunCreate,
    "evaluation-run-detail": renderEvaluationRunDetail,
    "evaluation-comparison": renderEvaluationComparison,
    "evaluation-failure-analysis": renderEvaluationFailureAnalysis,
  }[screen](state, snapshot);
}

function accessPolicyFailureMessage(result) {
  return {
    ACCESS_POLICY_VERSION_IMMUTABLE: "已发布版本不可修改，请建立新的策略草稿。",
    ACCESS_POLICY_ASSISTANT_INVALID: "请选择当前租户已发布的助手。",
    ACCESS_POLICY_MEMBER_INVALID: "至少选择一名可用的普通成员。",
    ACCESS_POLICY_PREVIEW_REQUIRED: "发布前必须重新生成并通过授权预览。",
    QUERY_REMEDIATION_CATEGORY_INVALID: "请选择与当前失败阶段匹配的修复分类。",
    QUERY_REMEDIATION_INVALID: "请填写 4 至 100 字事项名称和 2 至 40 字负责人。",
    STORAGE_UNAVAILABLE: "浏览器无法保存，本次操作没有生效。",
    FORBIDDEN: "当前演示身份无权执行此操作。",
  }[result.reason] || "操作未完成，原状态保持不变。";
}

function renderGovernanceDirectory(screen, state, snapshot) {
  const projection = prototypeStore.getAuditProjection(workspaceTenantId(), prototypeStore.getSession().accountId);
  const generic = genericState(screen, state);
  if (generic) return adminShell(screen, pageFixtures.pages[screen].title, "", "", generic, state, "访问与审计");
  if (screen === "audit-record-detail") {
    const auditId = new URLSearchParams(location.search).get("auditId");
    const record = projection.ok ? auditId ? projection.records.find(item => item.id === auditId) : projection.records[0] : null;
    const back = '<button class="button secondary" type="button" data-nav-screen="audit-records">返回审计目录</button>';
    if (!record) return adminShell(screen, "审计事件", "", back, statePanel("empty", "没有可查看的审计事件", "请从当前组织的审计目录选择记录。", "返回审计目录", "audit-records"), state, "审计记录");
    const body = `<section class="section-block" data-audit-detail="${escapeHtml(record.id)}"><div class="section-heading"><h2>${escapeHtml(record.event)}</h2>${statusTag(record.outcome === "SUCCESS" ? "READY" : "FAILED")}</div><dl class="description-list"><dt>操作范围</dt><dd>${escapeHtml(record.scope)}</dd><dt>主体类型</dt><dd>${escapeHtml(record.actorRole)}</dd><dt>记录时间</dt><dd>${escapeHtml(record.occurredAt)}</dd><dt>结果说明</dt><dd>${escapeHtml(record.summary)}</dd></dl></section>`;
    return adminShell(screen, "审计事件解释", "", back, `<div class="timeline-workspace">${body}<aside class="next-step-panel"><h2>记录保持只读</h2><p>后续操作形成新的事实，不改写此条审计事件。</p>${back}</aside></div>`, state, "审计记录");
  }
  const result = governanceDirectory(screen, snapshot, projection.ok ? projection.records : [], { escapeHtml, statusTag });
  return adminShell(screen, result.title, "", state === "success" ? result.action : "", result.body, state, "访问与审计");
}

function renderAccessPolicyDetail(state, snapshot) {
  const policyId = new URLSearchParams(location.search).get("policyId") || snapshot.accessGovernance.selectedPolicyId;
  const visiblePolicy = snapshot.accessGovernance.accessPolicies.find(item => item.id === policyId);
  const projection = visiblePolicy ? prototypeStore.getAccessPolicyProjection(policyId, prototypeStore.getSession().accountId) : { ok: false };
  if (!projection.ok) return adminShell("access-policy-detail", "访问策略版本", "", "", statePanel("denied", "当前会话不能访问此策略", "页面不会确认策略或成员是否存在。", "返回访问策略", "access-policies"), state, "访问治理 / 策略版本");
  const { policy, version, assistant, knowledgeBase, members } = projection;
  const blocked = state !== "success" || version.status !== "DRAFT";
  const tenant = snapshot.tenants.find(item => item.id === workspaceTenantId());
  const ordinaryMembers = (tenant?.members || []).filter(member => member.role === "ordinaryMember" && member.status === "active");
  const publishedAssistants = snapshot.assistantDelivery.assistants.filter(item => item.status === "PUBLISHED" && item.publishedVersionId);
  const memberControls = ordinaryMembers.map(member => {
    const account = snapshot.accounts.find(item => item.id === member.accountId);
    return `<label class="feature-control"><input type="checkbox" name="memberAccountIds" value="${escapeHtml(member.accountId)}"${version.memberAccountIds.includes(member.accountId) ? " checked" : ""}${blocked ? " disabled" : ""}><span><strong>${escapeHtml(account?.name || "不可用成员")}</strong><small>普通成员 · 只获得所选助手的对话与授权引用</small></span></label>`;
  }).join("");
  const preview = version.preview;
  const previewBody = preview
    ? `<div class="summary-strip"><dl class="summary-item"><dt>允许样本</dt><dd>${preview.allowCount}</dd></dl><dl class="summary-item"><dt>拒绝样本</dt><dd>${preview.denyCount}</dd></dl><dl class="summary-item"><dt>越权泄漏</dt><dd>0</dd></dl><dl class="summary-item"><dt>预览结果</dt><dd>通过</dd></dl></div><ul class="timeline">${preview.checks.map(item => `<li><strong>${escapeHtml(item)}</strong><small>通过</small></li>`).join("")}</ul>`
    : '<div class="inline-note warning">成员或助手变化后预览会失效。请在发布前核对允许、拒绝、历史与引用四类样本。</div>';
  const primary = version.status === "DRAFT"
    ? `<button class="button primary" type="button" data-publish-access-policy data-access-policy-version-id="${escapeHtml(version.id)}" data-primary-action${blocked || !preview?.passed ? " disabled" : ""}>发布策略版本</button>`
    : `<button class="button primary" type="button" data-primary-action data-create-access-version="${escapeHtml(policy.id)}"${state === "success" ? "" : " disabled"}>创建策略新版本</button>`;
  const body = [
    '<div class="inline-note">策略只配置普通成员可用的助手；知识库由助手的永久关联关系确定，不能在这里另选。</div>',
    `<div class="split-detail"><form id="access-policy-form" class="section-block" data-access-policy-version-id="${escapeHtml(version.id)}"><div class="section-heading"><div><span class="section-kicker">${escapeHtml(policy.name)}</span><h2>策略 v${escapeHtml(version.number)}</h2></div>${statusTag(version.status)}</div><div class="form-grid"><label class="field"><span>已发布助手</span><select class="select" name="assistantId" data-access-policy-assistant${blocked ? " disabled" : ""}>${publishedAssistants.map(item => `<option value="${escapeHtml(item.id)}"${item.id === version.assistantId ? " selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}</select></label><div class="field"><span>关联知识库（自动确定）</span><div class="input readonly-value" data-derived-knowledge-base>${escapeHtml(knowledgeBase.name)}</div><small class="field-hint" data-derived-knowledge-help>来自“${escapeHtml(assistant.name)}”的永久知识库关系。</small></div></div><fieldset class="feature-editor access-policy-members"><legend class="section-kicker">授权普通成员</legend><div class="feature-grid">${memberControls}</div></fieldset><p id="access-policy-feedback" class="form-feedback" role="alert">${escapeHtml(accessGovernanceFeedback)}</p><div class="form-actions"><button class="button secondary" type="submit"${blocked ? " disabled" : ""}>保存草稿</button><button class="button ${preview?.passed ? "secondary" : "primary"}" type="button" data-preview-access-policy data-access-policy-version-id="${escapeHtml(version.id)}"${preview?.passed ? "" : " data-primary-action"}${blocked ? " disabled" : ""}>生成授权预览</button></div></form>`,
    `<aside><section class="release-decision${preview?.passed ? "" : " pending"}"><span class="section-kicker">发布前授权预览</span><h2>${preview?.passed ? "32 个样本已通过" : "尚未生成有效预览"}</h2>${previewBody}<div class="form-actions">${primary}</div><dl class="description-list"><dt>当前成员</dt><dd>${escapeHtml(members.map(item => item.name).join("、"))}</dd><dt>生效范围</dt><dd>助手目录、对话、本人历史和授权引用</dd><dt>撤销结果</dt><dd>直接路由、历史正文与引用立即失败关闭</dd></dl></section></aside></div>`,
  ].join("");
  return adminShell("access-policy-detail", `${policy.name} v${version.number}`, "", '<button class="button secondary" type="button" data-nav-screen="access-policies">返回策略目录</button>', body, state, "访问治理 / 策略版本");
}

function queryOutcomeLabel(outcome) {
  return { ANSWERED: "已回答", REFUSED: "安全拒答", FAILED: "失败关闭" }[outcome] || outcome;
}

function renderGovernanceQuery(state) {
  const projection = prototypeStore.getQueryGovernanceProjection(workspaceTenantId(), prototypeStore.getSession().accountId);
  if (!projection.ok) return adminShell("governance-query", "查询治理", "", "", statePanel("denied", "当前会话不能读取查询治理", "不显示租户、运行或配额是否存在。", "返回工作区", "tenant-overview"), state, "评测治理 / 查询治理");
  const blocked = state !== "success";
  const { quota, usage, runs } = projection;
  const rows = runs.map(item => `<tr data-query-run-row="${escapeHtml(item.reference)}"><td data-label="运行"><span class="primary-cell"><strong class="mono">${escapeHtml(item.reference)}</strong><span>${escapeHtml(item.occurredAt)}</span></span></td><td data-label="助手">${escapeHtml(item.assistantName)}</td><td data-label="终态">${statusTag(item.outcome)}</td><td data-label="耗时">${escapeHtml(item.durationMs)} ms</td><td data-label="操作"><button class="button secondary compact" type="button" data-select-query-run="${escapeHtml(item.id)}" data-nav-screen="query-diagnostic"${blocked ? " disabled" : ""}>查看诊断</button></td></tr>`).join("");
  const body = [
    '<div class="inline-note">只显示聚合用量和脱敏终态，不展示问题、回答、引用正文、模型原始响应或租户内部标识。</div>',
    `<div class="summary-strip"><dl class="summary-item"><dt>今日已接受</dt><dd>${usage.acceptedToday}</dd></dl><dl class="summary-item"><dt>今日剩余</dt><dd>${usage.remainingToday}</dd></dl><dl class="summary-item"><dt>当前执行中</dt><dd>${usage.activeNow}</dd></dl><dl class="summary-item"><dt>今日安全拒答</dt><dd>${usage.refusedToday}</dd></dl></div>`,
    `<section class="section-block"><div class="section-heading"><div><span class="section-kicker">脱敏终态投影</span><h2>最近查询运行</h2></div><span class="status-tag" data-tone="info">只读</span></div><div class="table-shell"><table class="data-table"><thead><tr><th>运行</th><th>助手</th><th>终态</th><th>耗时</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table><div class="pagination" aria-label="查询运行分页"><span>第 1 页，共 ${runs.length} 条运行</span><div class="pagination-actions"><button class="button secondary compact" type="button" disabled>上一页</button><button class="button secondary compact" type="button" disabled>下一页</button></div></div></div></section>`,
    `<dialog id="query-quota-dialog" class="dialog-surface" aria-labelledby="query-quota-title"><form method="dialog"><div class="dialog-heading"><span class="section-kicker">${escapeHtml(quota.source)}</span><h2 id="query-quota-title">查询配额规则</h2><p>当前版本不提供在线修改；变更须通过受控部署配置与发布验证。</p></div><dl class="description-list"><dt>UTC 每日已接受请求</dt><dd>${quota.dailyAcceptedLimit} 次</dd><dt>最大并发运行</dt><dd>${quota.maxConcurrent} 个</dd><dt>运行租约</dt><dd>${quota.leaseSeconds} 秒</dd><dt>消耗规则</dt><dd>请求一经接受即计入当日用量，后续失败或拒答不会返还</dd><dt>超限结果</dt><dd>失败关闭，仅返回限制类型、限制值和重试时间</dd></dl><div class="form-actions dialog-actions"><button class="button primary" value="close">关闭</button></div></form></dialog>`,
  ].join("");
  return adminShell("governance-query", "查询治理", "", `<button class="button primary" type="button" data-open-query-quota data-primary-action${blocked ? " disabled" : ""}>查看配额规则</button>`, body, state, "评测治理 / 查询治理");
}

function renderQueryDiagnostic(state, snapshot) {
  const selectedId = snapshot.accessGovernance.selectedQueryRunId;
  const projection = snapshot.accessGovernance.queryRuns.some(item => item.id === selectedId) ? prototypeStore.getQueryDiagnostic(selectedId, prototypeStore.getSession().accountId) : { ok: false };
  if (!projection.ok) return adminShell("query-diagnostic", "受权查询诊断", "", "", statePanel("empty", "诊断事实不可用", "终态事实不存在或当前会话无权读取。", "返回查询治理", "governance-query"), state, "评测治理 / 查询诊断");
  const { run, diagnostic, remediation } = projection;
  const actionable = run.outcome !== "ANSWERED";
  const blocked = state !== "success" || Boolean(remediation) || !actionable;
  const stageLabels = {
    RUN_ACCEPTED: "请求已接受",
    AUTHORIZATION_FILTERED: "授权过滤完成",
    INSUFFICIENT_EVIDENCE: "证据不足",
    COMPLETED_REFUSAL: "安全拒答完成",
    CITATIONS_VALIDATED: "引用校验完成",
    FAILED_CLOSED: "失败关闭完成",
  };
  const stages = diagnostic.outcome === "REFUSED"
    ? ["RUN_ACCEPTED", "AUTHORIZATION_FILTERED", "INSUFFICIENT_EVIDENCE", "COMPLETED_REFUSAL"]
    : diagnostic.outcome === "ANSWERED"
      ? ["RUN_ACCEPTED", "AUTHORIZATION_FILTERED", "CITATIONS_VALIDATED", "COMPLETED"]
      : ["RUN_ACCEPTED", "AUTHORIZATION_FILTERED", diagnostic.stage, "FAILED_CLOSED"];
  const candidateCollection = { FILTERED: "已完成授权过滤", NOT_COMPLETED: "未完成" }[diagnostic.candidateCollection] || diagnostic.candidateCollection;
  const authorization = { AUTHORIZED: "已在授权范围内" }[diagnostic.retrievalAuthorization] || diagnostic.retrievalAuthorization;
  const citationValidation = { VALIDATED: "已通过", NOT_APPLICABLE: "不适用" }[diagnostic.citationValidation] || diagnostic.citationValidation;
  const body = [
    '<div class="inline-note">这是运行结束时原子写入的历史事实。页面不会重新检索，也不会根据当前策略推断过去结果。</div>',
    `<div class="split-detail"><div><section class="section-block"><div class="section-heading"><div><span class="section-kicker mono">${escapeHtml(run.reference)}</span><h2>${escapeHtml(run.assistantName)} · ${escapeHtml(queryOutcomeLabel(run.outcome))}</h2></div>${statusTag(run.outcome)}</div><ol class="process-steps" aria-label="查询终态阶段">${stages.map((stage, index) => `<li data-step-state="complete"><span>${index + 1}</span><strong>${escapeHtml(stageLabels[stage] || stage)}</strong></li>`).join("")}</ol></section><section class="section-block"><div class="section-heading"><h2>最小诊断事实</h2><span class="status-tag" data-tone="info">只读</span></div><dl class="description-list"><dt>候选收集</dt><dd>${escapeHtml(candidateCollection)}</dd><dt>可披露候选</dt><dd>${diagnostic.candidateCount ?? "未完成"}</dd><dt>授权判定</dt><dd>${escapeHtml(authorization)}</dd><dt>引用校验</dt><dd>${escapeHtml(citationValidation)}</dd><dt>Chat 版本</dt><dd>${escapeHtml(diagnostic.chatModelRevision)}</dd><dt>Embedding 版本</dt><dd>${escapeHtml(diagnostic.embeddingModelRevision)}</dd><dt>事实时间</dt><dd>${escapeHtml(diagnostic.createdAt)}</dd></dl></section></div>`,
    `<aside><section class="release-decision"><span class="section-kicker">${actionable ? "可执行下一步" : "运行结论"}</span><h2>${remediation ? "修复事项已创建" : actionable ? "将原因转成交付事项" : "本次运行无需修复"}</h2><p>${actionable ? "只引用运行编号、失败阶段、分类和候选数量，不复制问题、回答或引用正文。" : "回答和引用校验均已完成，可以返回查询治理继续查看。"}</p>${remediation ? `<dl class="description-list"><dt>事项</dt><dd>${escapeHtml(remediation.title)}</dd><dt>负责人</dt><dd>${escapeHtml(remediation.owner)}</dd><dt>阶段</dt><dd>${escapeHtml(remediation.stage)}</dd><dt>状态</dt><dd>待处理</dd></dl>` : ""}<button class="button primary" type="button" data-open-query-remediation data-primary-action${blocked ? " disabled" : ""}>${remediation ? "修复事项已创建" : actionable ? "创建修复事项" : "无需创建修复事项"}</button>${accessGovernanceFeedback ? `<p class="form-feedback" role="status">${escapeHtml(accessGovernanceFeedback)}</p>` : ""}</section></aside></div>`,
    `<dialog id="query-remediation-dialog" class="dialog-surface" aria-labelledby="query-remediation-title"><form id="query-remediation-form" data-query-run-id="${escapeHtml(run.id)}"><div class="dialog-heading"><span class="section-kicker mono">${escapeHtml(run.reference)}</span><h2 id="query-remediation-title">创建查询修复事项</h2></div><label class="field"><span>失败分类</span><select class="select" name="category"><option value="AUTHORIZED_EVIDENCE_EMPTY"${diagnostic.stage === "INSUFFICIENT_EVIDENCE" ? " selected" : ""}>授权后无可披露证据</option><option value="RETRIEVAL_RUNTIME_FAILURE"${diagnostic.stage === "RETRIEVAL_FAILED" ? " selected" : ""}>检索运行失败</option><option value="CITATION_VALIDATION_FAILURE">引用校验失败</option></select></label><label class="field"><span>事项名称</span><input class="input" name="title" minlength="4" maxlength="100" value="${diagnostic.stage === "RETRIEVAL_FAILED" ? "排查检索运行失败" : "复核访问策略与索引覆盖"}" required></label><label class="field"><span>负责人</span><input class="input" name="owner" minlength="2" maxlength="40" value="知识治理组" required></label><p id="query-remediation-feedback" class="form-feedback" role="alert"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-close-query-remediation>取消</button><button class="button primary" type="submit">创建事项</button></div></form></dialog>`,
  ].join("");
  return adminShell("query-diagnostic", "受权查询诊断", "", '<button class="button secondary" type="button" data-nav-screen="governance-query">返回查询治理</button>', body, state, "评测治理 / 查询诊断");
}

function structuredDataFailureMessage(result) {
  return {
    DATASET_HEADERS_MISMATCH: "CSV 标题或顺序已变化，请重新建立来源版本。",
    DATASET_FIELDS_MISMATCH: "字段必须与冻结的 CSV 标题逐项一致。",
    DATASET_FIELD_INVALID: "请核对字段类型、用途和敏感级别。",
    DATASET_QUERY_FIELD_UNSAFE: "敏感字段不能设为可查询。",
    DATASET_CONTENT_FIELD_UNSAFE: "主内容必须是公开、可查询的文本字段。",
    DATASET_TITLE_FIELD_UNSAFE: "标题必须是公开、可查询的文本字段。",
    DATASET_ROW_FILTER_INVALID: "请选择受支持的空内容处理方式。",
    DATASET_PREVIEW_REQUIRED: "请先运行抽样预检。",
    DATASET_SCHEMA_IMMUTABLE: "已发布版本不可修改。",
    OBJECT_RANGE_NOT_FOUND: "所选范围不可用或不属于当前租户。",
    OBJECT_RANGE_REVISION_MISMATCH: "范围版本已变化，请刷新后重新选择。",
    OBJECT_RANGE_ALREADY_REGISTERED: "该范围版本已登记，不能改名后重复登记。",
    OBJECT_RANGE_NAME_INVALID: "来源名称请输入 2 至 80 个字符。",
    STORAGE_UNAVAILABLE: "浏览器无法保存，本次操作未生效。",
    FORBIDDEN: "当前身份不能执行此操作。",
  }[result?.reason] || "当前操作未完成，请核对后重试。";
}

function fieldSelect(name, label, value, options, disabled) {
  return `<select class="select compact-select" name="${escapeHtml(name)}" aria-label="${escapeHtml(label)}"${disabled ? " disabled" : ""}>${options.map(option => `<option value="${escapeHtml(option.value)}"${option.value === value ? " selected" : ""}>${escapeHtml(option.label)}</option>`).join("")}</select>`;
}

function renderDatasetSchema(state, snapshot) {
  const datasetId = snapshot.dataGovernance.structuredDatasets[0]?.id;
  const projection = datasetId ? prototypeStore.getStructuredDatasetProjection(datasetId, prototypeStore.getSession().accountId) : { ok: false };
  if (!projection.ok) return adminShell("dataset-schema", "结构化数据版本", "", "", statePanel("denied", "当前会话不能访问此任务", "页面不会确认数据集是否存在。", "返回数据来源", "knowledge-sources"), state, "知识处理 / 结构化数据");
  const { dataset, version } = projection;
  const editable = state === "success" && version.status === "DRAFT";
  const typeOptions = [
    { value: "TEXT", label: "文本" },
    { value: "INTEGER", label: "整数" },
    { value: "DECIMAL", label: "小数" },
    { value: "DATE", label: "日期" },
  ];
  const sensitivityOptions = [
    { value: "PUBLIC", label: "可用于回答" },
    { value: "INTERNAL", label: "仅内部处理" },
    { value: "RESTRICTED", label: "禁止进入检索" },
  ];
  const fieldRows = version.fields.map(field => [
    `<tr data-dataset-field-row data-field-name="${escapeHtml(field.name)}">`,
    `<td data-label="CSV 字段"><span class="primary-cell"><strong class="mono">${escapeHtml(field.name)}</strong></span></td>`,
    `<td data-label="逻辑类型">${fieldSelect(`type-${field.name}`, `${field.name} 的逻辑类型`, field.logicalType, typeOptions, !editable)}</td>`,
    `<td data-label="业务含义"><input class="input compact-input" name="semantic-${escapeHtml(field.name)}" aria-label="${escapeHtml(field.name)} 的业务含义" value="${escapeHtml(field.semantic)}"${editable ? "" : " disabled"}></td>`,
    `<td data-label="查询资格"><label class="table-checkbox"><input type="checkbox" name="query-${escapeHtml(field.name)}" aria-label="${escapeHtml(field.name)} 的查询资格"${field.queryEligible ? " checked" : ""}${editable ? "" : " disabled"}><span>${field.queryEligible ? "允许" : "禁止"}</span></label></td>`,
    `<td data-label="敏感级别">${fieldSelect(`sensitivity-${field.name}`, `${field.name} 的敏感级别`, field.sensitivity, sensitivityOptions, !editable)}</td>`,
    "</tr>",
  ].join("")).join("");
  const preview = version.preview;
  const primary = version.status === "PUBLISHED"
    ? '<button class="button primary" type="button" data-primary-action disabled>结构版本已发布</button>'
    : `<button class="button primary" type="button" data-publish-structured-schema="${escapeHtml(version.id)}" data-primary-action${!editable || !preview?.passed ? " disabled" : ""}>发布结构版本</button>`;
  const body = [
    state === "partial" ? '<div class="inline-note warning">来源版本正在刷新；当前结构草稿保持只读。</div>' : "",
    state === "disabled" ? '<div class="inline-note warning">平台已关闭知识管理；结构规则可查看但不可修改。</div>' : "",
    `<section class="section-block"><div class="section-heading"><div><span class="section-kicker">${escapeHtml(dataset.sourceName)} · 来源版本 ${escapeHtml(formatRevision(dataset.sourceRevision))}</span><h2>${escapeHtml(dataset.name)} v${escapeHtml(version.number)}</h2></div>${statusTag(version.status)}</div><div class="schema-header-order"><span>CSV 标题顺序</span><div>${dataset.headers.map(header => `<code>${escapeHtml(header)}</code>`).join('<span aria-hidden="true">→</span>')}</div></div></section>`,
    `<form id="structured-schema-form" data-version-id="${escapeHtml(version.id)}"><div class="table-shell schema-table-shell"><table class="data-table schema-field-table"><thead><tr><th>CSV 字段</th><th>逻辑类型</th><th>业务含义</th><th>查询资格</th><th>敏感级别</th></tr></thead><tbody>${fieldRows}</tbody></table></div><section class="section-block"><div class="form-grid"><label class="field"><span>主内容字段</span><select class="select" name="contentField"${editable ? "" : " disabled"}>${version.fields.map(field => `<option value="${escapeHtml(field.name)}"${field.name === version.contentField ? " selected" : ""}>${escapeHtml(field.name)}</option>`).join("")}</select><small>仅公开、可查询的文本字段可作为正文。</small></label><label class="field"><span>标题字段</span><select class="select" name="titleField"${editable ? "" : " disabled"}><option value="">不使用标题</option>${version.fields.map(field => `<option value="${escapeHtml(field.name)}"${field.name === version.titleField ? " selected" : ""}>${escapeHtml(field.name)}</option>`).join("")}</select><small>标题只用于证据定位，不拼接其他字段。</small></label><label class="field"><span>空内容行</span><select class="select" name="rowFilter"${editable ? "" : " disabled"}><option value="NON_BLANK_CONTENT"${version.rowFilter === "NON_BLANK_CONTENT" ? " selected" : ""}>跳过并记录警告</option><option value="ALL_ROWS"${version.rowFilter === "ALL_ROWS" ? " selected" : ""}>发现即阻断</option></select></label><div class="readonly-fact"><span>质量规则</span><strong class="mono">${escapeHtml(version.qualityRuleVersion)}</strong></div></div><p class="form-feedback" role="status">${escapeHtml(dataGovernanceFeedback)}</p><div class="form-actions"><button class="button secondary" type="submit"${editable ? "" : " disabled"}>保存结构草稿</button><button class="button secondary" type="button" data-preview-structured-schema="${escapeHtml(version.id)}"${editable ? "" : " disabled"}>运行抽样预检</button></div></section></form>`,
    `<section class="release-decision${preview?.passed ? "" : " pending"}"><span class="section-kicker">发布前检查</span><h2>${preview?.passed ? `${preview.checkedRows} 行已检查，${preview.blockedRows} 个阻断` : "等待抽样预检"}</h2><dl class="description-list"><dt>标题匹配</dt><dd>${preview?.headerMatched ? "逐项一致" : "待检查"}</dd><dt>跳过空内容</dt><dd>${preview ? `${preview.skippedRows} 行` : "待检查"}</dd><dt>敏感正文</dt><dd>${preview?.passed ? "未进入投影" : "待检查"}</dd><dt>发布影响</dt><dd>形成不可变版本，不自动重建活动索引</dd></dl></section>`,
  ].join("");
  return adminShell("dataset-schema", "结构化数据版本", "", primary, body, state, "知识处理 / 结构化数据");
}

function renderObjectStorageScope(state, snapshot) {
  const projection = prototypeStore.getObjectStorageScopeProjection(workspaceTenantId(), prototypeStore.getSession().accountId);
  if (!projection.ok) return adminShell("object-storage-scope", "对象存储范围", "", "", statePanel("denied", "当前会话不能访问此任务", "页面不会确认对象范围是否存在。", "返回数据来源", "knowledge-sources"), state, "知识处理 / 对象范围");
  const blocked = state !== "success" || projection.availableRanges.length === 0;
  const rangeRows = projection.availableRanges.map(range => `<tr><td data-label="签发范围"><span class="primary-cell"><strong>${escapeHtml(range.label)}</strong><span>版本 ${escapeHtml(range.selectionRevision)}</span></span></td><td data-label="驻留区域">${escapeHtml(range.region)}</td><td data-label="冻结对象">${escapeHtml(range.objectCount)} 个</td><td data-label="支持内容">严格 UTF-8 文本</td><td data-label="状态">${statusTag("READY")}</td></tr>`).join("");
  const registrationRows = projection.registrations.map(item => {
    const source = snapshot.knowledgeBases.find(kb => kb.id === item.knowledgeBaseId)?.sources.find(source => source.id === item.id);
    return `<tr><td data-label="对象来源"><span class="primary-cell"><strong>${escapeHtml(item.name)}</strong><span>${escapeHtml(item.rangeLabel)} · 版本 ${escapeHtml(item.selectionRevision)}</span></span></td><td data-label="知识库">${escapeHtml(item.knowledgeBaseName)}</td><td data-label="冻结对象">${escapeHtml(item.objectCount)} 个</td><td data-label="状态"><span class="status-tag" data-tone="warning">${source?.stage === "RECEIVED" ? "待探测" : escapeHtml(source?.stage || "未接通来源")}</span></td><td data-label="建立时间">${escapeHtml(item.createdAt)}<button class="button secondary compact" type="button" data-select-kb="${escapeHtml(item.knowledgeBaseId)}" data-select-source="${escapeHtml(item.id)}" data-nav-screen="source-detail"${source ? "" : " disabled"}>查看并继续处理</button></td></tr>`;
  }).join("");
  const body = [
    state === "partial" ? '<div class="inline-note warning">签发目录正在刷新；当前列表可查看但不能登记。</div>' : "",
    state === "disabled" ? '<div class="inline-note warning">平台已关闭知识管理；既有登记保持只读。</div>' : "",
    '<div class="inline-note">只选择平台签发范围。桶名、对象键、端点与凭据不会进入页面或浏览器状态。</div>',
    `<section class="section-block"><div class="section-heading"><div><span class="section-kicker">当前可用</span><h2>${projection.availableRanges.length} 个签发范围</h2></div><span class="status-tag" data-tone="info">只读目录</span></div><div class="table-shell"><table class="data-table"><thead><tr><th>签发范围</th><th>驻留区域</th><th>冻结对象</th><th>支持内容</th><th>状态</th></tr></thead><tbody>${rangeRows}</tbody></table></div></section>`,
    `<section class="section-block"><div class="section-heading"><div><span class="section-kicker">当前知识工作区</span><h2>已登记对象来源</h2></div><span>${projection.registrations.length} 个</span></div>${projection.registrations.length ? `<div class="table-shell"><table class="data-table"><thead><tr><th>对象来源</th><th>知识库</th><th>冻结对象</th><th>状态</th><th>建立时间</th></tr></thead><tbody>${registrationRows}</tbody></table></div>` : '<div class="empty-inline"><strong>尚未登记对象来源</strong><span>从上方签发范围建立来源后，将进入探测与同步流程。</span></div>'}${dataGovernanceFeedback ? `<p class="form-feedback" role="status">${escapeHtml(dataGovernanceFeedback)}</p>` : ""}</section>`,
    `<dialog id="object-scope-dialog" class="dialog-surface" aria-labelledby="object-scope-title"><form id="object-scope-form"><div class="dialog-heading"><span class="section-kicker">只登记签发事实</span><h2 id="object-scope-title">登记对象来源</h2></div><label class="field"><span>来源名称</span><input class="input" name="name" value="华东办公制度对象来源" minlength="2" maxlength="80" required></label><label class="field"><span>目标知识库</span><select class="select" name="knowledgeBaseId">${snapshot.knowledgeBases.filter(item => item.tenantId === workspaceTenantId()).map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}</option>`).join("")}</select></label><label class="field"><span>平台签发范围</span><select class="select" name="rangeId">${projection.availableRanges.map(range => `<option value="${escapeHtml(range.id)}">${escapeHtml(range.label)} · ${escapeHtml(range.objectCount)} 个文本对象</option>`).join("")}</select><small>范围版本由平台冻结，不能手工输入。</small></label><div class="dialog-impact"><div><dt>登记结果</dt><dd>建立待探测来源，不自动同步或发布</dd></div><div><dt>失败语义</dt><dd>范围变化或越权时不留下半成品</dd></div></div><p id="object-scope-feedback" class="form-feedback" role="alert"></p><div class="form-actions dialog-actions"><button class="button secondary" type="button" data-close-object-scope>取消</button><button class="button primary" type="submit">确认登记</button></div></form></dialog>`,
  ].join("");
  return adminShell("object-storage-scope", "对象范围登记", "", `<button class="button primary" type="button" data-open-object-scope data-primary-action${blocked ? " disabled" : ""}>登记对象范围</button>`, body, state, "知识处理 / 对象范围");
}

function renderStructuredObjectGovernance(screen, state, snapshot) {
  const generic = genericState(screen, state);
  if (generic) return adminShell(screen, pageFixtures.pages[screen].title, "", "", generic, state, "知识处理");
  return screen === "dataset-schema"
    ? renderDatasetSchema(state, snapshot)
    : renderObjectStorageScope(state, snapshot);
}

function renderAccessQueryGovernance(screen, state, snapshot) {
  const generic = genericState(screen, state);
  if (generic) return adminShell(screen, pageFixtures.pages[screen].title, "", "", generic, state, screen === "access-policy-detail" ? "访问治理" : "评测治理");
  return {
    "access-policy-detail": renderAccessPolicyDetail,
    "governance-query": renderGovernanceQuery,
    "query-diagnostic": renderQueryDiagnostic,
  }[screen](state, snapshot);
}

function bindKnowledgeManagement() {
  root.querySelector("[data-rebuild-source]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const kb = selectedKnowledgeBase(snapshot);
    const source = kb?.sources.find(item => item.id === snapshot.selectedKnowledgeSourceId);
    const result = prototypeStore.createIndexRebuild(kb?.id, { sourceId: source?.id, trigger: "SOURCE_REINDEX", reason: `单文件重建：${source?.name} ${formatRevision(source?.revision)}；其余内容复用 ${kb?.activeIndex || "现有清单"}` }, prototypeStore.getSession().accountId);
    if (result.ok) navigate("index-detail");
    else { knowledgeFeedback = "未能建立单文件索引，请先完成资料处理或正在进行的构建；原活动索引不变。"; render(); }
  });
  root.querySelector("[data-retry-failed-sources]")?.addEventListener("click", () => {
    const result = prototypeStore.retryFailedKnowledgeSources(prototypeStore.getWorkspaceSnapshot().selectedKnowledgeBaseId, prototypeStore.getSession().accountId);
    knowledgeFeedback = result.ok ? `已安全重试 ${result.count} 份资料，可逐项查看处理进度。` : result.reason === "STORAGE_UNAVAILABLE" ? "无法保存重试结果，资料状态未变，请重试。" : "当前没有可重试的失败资料。";
    render();
  });
  root.querySelector("#quick-publication-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    if (values.get("decision") === "repair") { navigate("processing-quality"); return; }
    const kb = selectedKnowledgeBase(prototypeStore.getWorkspaceSnapshot());
    if (!kb?.sources.some(item => item.stage === "READY")) {
      root.querySelector("#quick-publication-feedback").textContent = "还没有已就绪资料，请先完成来源处理。";
      return;
    }
    const result = prototypeStore.createIndexRebuild(kb.id, { reason: String(values.get("batchName")).trim() }, prototypeStore.getSession().accountId);
    if (!result.ok) {
      root.querySelector("#quick-publication-feedback").textContent = result.reason === "INDEX_BUILD_ALREADY_ACTIVE" ? "已有索引正在构建，请先在索引版本中继续处理。" : "创建失败，原活动索引不变，请重试。";
      return;
    }
    navigate("index-detail");
  });
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
      prototypeStore.getSession().accountId,
      workspaceTenantId(),
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
    const knowledgeBase = selectedKnowledgeBase(prototypeStore.getWorkspaceSnapshot());
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
    const result = event.currentTarget.dataset.replaceSource
      ? prototypeStore.replaceKnowledgeSource(knowledgeBase?.id, event.currentTarget.dataset.replaceSource, input, prototypeStore.getSession().accountId)
      : prototypeStore.addKnowledgeSource(knowledgeBase?.id, input, prototypeStore.getSession().accountId);
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
        STORAGE_QUOTA_EXCEEDED: "原始内容容量不足，新版本未保存；请联系平台管理员调整配额。",
        STORAGE_UNAVAILABLE: "浏览器无法保存，资料没有进入处理队列。",
      }[result.reason] || "安全校验未通过，资料没有进入处理队列。";
      root.querySelector("#source-add-feedback").textContent = message;
      return;
    }
    knowledgeFeedback = `已接收“${result.source.name}”，等待安全扫描。`;
    navigate("source-detail");
  });

  root.querySelector("[data-advance-source]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.advanceKnowledgeSource(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedKnowledgeSourceId,
      prototypeStore.getSession().accountId,
    );
    knowledgeFeedback = result.ok ? `处理阶段已更新为 ${result.source.stage}。` : "当前阶段不能继续推进。";
    render();
  });
  root.querySelector("[data-retry-source]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.retryKnowledgeSource(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedKnowledgeSourceId,
      prototypeStore.getSession().accountId,
    );
    knowledgeFeedback = result.ok ? "已从安全重试点继续，失败内容仍未进入活动索引。" : "当前来源不能重试。";
    render();
  });
  root.querySelector("[data-reprocess-source]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.reprocessKnowledgeSource(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedKnowledgeSourceId,
      prototypeStore.getSession().accountId,
    );
    knowledgeFeedback = result.ok ? "已建立新版本并开始重新提取；活动索引继续服务。" : "当前来源不能重新处理。";
    render();
  });
}

function bindIndexManagement() {
  root.querySelector("#index-rebuild-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const snapshot = prototypeStore.getWorkspaceSnapshot();
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
      prototypeStore.getSession().accountId,
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
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.advanceIndexBuild(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedIndexVersionId,
      prototypeStore.getSession().accountId,
    );
    indexFeedback = result.ok ? `构建已推进到 ${result.indexVersion.stage}。` : "当前构建不能继续推进。";
    render();
  });
  root.querySelector("[data-evaluate-index]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.recordIndexEvaluation(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedIndexVersionId,
      fixtures.indexVersion.quality,
      prototypeStore.getSession().accountId,
    );
    indexFeedback = result.ok && result.indexVersion.status === "READY_FOR_REVIEW"
      ? "冻结评测通过，可以由授权管理员激活。"
      : "冻结评测未通过，活动索引继续服务。";
    render();
  });
  const indexDialog = root.querySelector("#index-dialog");
  root.querySelector("[data-confirm-index]")?.addEventListener("click", () => indexDialog?.showModal());
  root.querySelector("[data-activate-index]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.activateIndexVersion(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedIndexVersionId,
      prototypeStore.getSession().accountId,
    );
    indexFeedback = result.ok ? `${result.indexVersion.id} 已原子切换为当前活动索引。` : "激活失败，活动索引未变化。";
    indexDialog?.close();
    render();
  });
  const cancelDialog = root.querySelector("#cancel-index-dialog");
  root.querySelector("[data-cancel-index]")?.addEventListener("click", () => cancelDialog?.showModal());
  root.querySelector("[data-confirm-cancel-index]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.cancelIndexBuild(
      snapshot.selectedKnowledgeBaseId,
      snapshot.selectedIndexVersionId,
      prototypeStore.getSession().accountId,
    );
    indexFeedback = result.ok ? "候选构建已取消，活动索引未变化。" : "当前版本不能取消。";
    cancelDialog?.close();
    render();
  });
}

function bindModelGovernance() {
  root.querySelector("#create-routing-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const result = prototypeStore.createRoutingPolicy(workspaceTenantId(), { name: new FormData(event.currentTarget).get("name") }, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? "路由草稿已创建，请配置主备部署并模拟。" : "无法创建路由草稿，请核对名称或存储后重试。";
    render();
  });
  root.querySelector("#tenant-discovery-connection")?.addEventListener("change", event => {
    prototypeStore.selectModelGovernanceItem("tenantConnection", event.currentTarget.value);
  });
  root.querySelectorAll("[data-close-dialog]").forEach(button => {
    button.addEventListener("click", () => root.querySelector(`#${CSS.escape(button.dataset.closeDialog)}`)?.close());
  });

  const platformConnectionDialog = root.querySelector("#platform-connection-dialog");
  root.querySelector("[data-open-platform-connection]")?.addEventListener("click", () => platformConnectionDialog?.showModal());
  root.querySelector("#platform-connection-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const result = prototypeStore.createPlatformModelConnection({
      name: form.get("name"),
      runtimeBaseUrl: form.get("runtimeBaseUrl"),
      capability: form.get("capability"),
      accessKey: form.get("accessKey"),
    }, prototypeStore.getSession().accountId);
    if (!result.ok) {
      root.querySelector("#platform-connection-feedback").textContent = {
        UNSAFE_RUNTIME_ENDPOINT: "运行地址必须是公网 HTTPS，不能使用本机或私网目标。",
        ACCESS_KEY_REQUIRED: "请输入至少 8 个字符的一次性访问密钥。",
        CONNECTION_NAME_CONFLICT: "连接名称已存在。",
      }[result.reason] || "连接草稿未保存，请核对输入后重试。";
      return;
    }
    modelFeedback = "连接草稿已保存；访问密钥未回显、未进入原型状态。";
    platformConnectionDialog?.close();
    navigate("platform-model-connection-detail");
  });
  root.querySelector("[data-probe-platform-connection]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.probePlatformModelConnection(snapshot.models.selectedPlatformConnectionId, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? "连接探测通过，可以发布本版本。" : "连接探测失败，未发布任何能力。";
    render();
  });
  root.querySelector("[data-publish-platform-connection]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.publishPlatformModelConnection(snapshot.models.selectedPlatformConnectionId, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? "连接版本已发布；尚未自动建立部署或授权租户。" : "连接未发布，状态未变化。";
    render();
  });

  const platformDeploymentDialog = root.querySelector("#platform-deployment-dialog");
  root.querySelector("[data-open-platform-deployment]")?.addEventListener("click", () => platformDeploymentDialog?.showModal());
  root.querySelector("#platform-deployment-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const [connectionId, modelId] = String(form.get("modelSelection") || "").split("|");
    const result = prototypeStore.createPlatformModelDeployment({
      name: form.get("name"),
      connectionId,
      modelId,
      tenantIds: form.getAll("authorizeTenant"),
    }, prototypeStore.getSession().accountId);
    if (!result.ok) {
      root.querySelector("#platform-deployment-feedback").textContent = result.reason === "TENANT_AUTHORIZATION_REQUIRED"
        ? "请至少选择一个可发现此部署的租户。"
        : "部署草稿未保存，请核对连接、模型和名称。";
      return;
    }
    modelFeedback = "部署草稿已建立；验证通过前不会进入租户目录。";
    platformDeploymentDialog?.close();
    navigate("platform-model-deployment-detail");
  });
  root.querySelector("[data-verify-platform-deployment]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.verifyPlatformModelDeployment(snapshot.models.selectedPlatformDeploymentId, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? "能力、预算与安全样本验证通过。" : "验证失败，部署未进入授权目录。";
    render();
  });
  root.querySelector("[data-publish-platform-deployment]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.publishPlatformModelDeployment(snapshot.models.selectedPlatformDeploymentId, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? "部署已发布到租户授权目录；未自动创建租户配置。" : "部署未发布，状态未变化。";
    render();
  });

  root.querySelector("[data-discover-tenant-models]")?.addEventListener("click", () => {
    const connectionId = root.querySelector("#tenant-discovery-connection")?.value;
    const result = prototypeStore.discoverTenantModels(workspaceTenantId(), connectionId, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? `发现完成：${result.models.length} 个已授权模型。` : "发现失败，没有改变租户目录。";
    if (result.ok) navigate("model-provider-detail");
    else render();
  });
  root.querySelector("[data-rediscover-tenant-models]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.discoverTenantModels(workspaceTenantId(), snapshot.models.selectedTenantConnectionId, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? `发现完成：${result.models.length} 个已授权模型。` : "重新探测失败，保留上次成功结果。";
    render();
  });
  root.querySelectorAll("[data-use-catalog-model]").forEach(button => {
    button.addEventListener("click", () => {
      prototypeStore.selectModelGovernanceItem("catalogDeployment", button.dataset.useCatalogModel);
      modelFeedback = "已选择目录模型，请创建租户配置。";
      navigate("model-configurations");
    });
  });

  const modelConfigurationDialog = root.querySelector("#model-configuration-dialog");
  root.querySelector("[data-open-model-configuration]")?.addEventListener("click", () => modelConfigurationDialog?.showModal());
  root.querySelector("#model-configuration-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const result = prototypeStore.createTenantModelConfiguration(workspaceTenantId(), {
      name: form.get("name"),
      platformDeploymentId: form.get("platformDeploymentId"),
    }, prototypeStore.getSession().accountId);
    if (!result.ok) {
      root.querySelector("#model-configuration-feedback").textContent = result.reason === "CONFIGURATION_NAME_CONFLICT"
        ? "配置名称已存在。"
        : "配置草稿未保存，请核对名称与目录模型授权。";
      return;
    }
    modelFeedback = "配置草稿已建立，验证通过前不能启用。";
    modelConfigurationDialog?.close();
    navigate("model-configuration-detail");
  });
  const modelKeyDialog = root.querySelector("#model-key-dialog");
  root.querySelector("[data-open-model-key]")?.addEventListener("click", () => modelKeyDialog?.showModal());
  root.querySelector("#model-key-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.rotateTenantModelAccessKey(
      snapshot.models.selectedConfigurationId,
      new FormData(event.currentTarget).get("accessKey"),
      prototypeStore.getSession().accountId,
    );
    if (!result.ok) {
      root.querySelector("#model-key-feedback").textContent = "新访问密钥未保存，请确认至少 8 个字符且没有其他待验证版本。";
      return;
    }
    modelFeedback = "新访问密钥已提交为待验证版本；旧启用版本继续服务，页面不回显密钥。";
    modelKeyDialog?.close();
    render();
  });
  const modelRevisionDialog = root.querySelector("#model-revision-dialog");
  root.querySelector("[data-open-model-revision]")?.addEventListener("click", () => modelRevisionDialog?.showModal());
  root.querySelector("#model-revision-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const configuration = snapshot.models.configurations.find(item => item.id === snapshot.models.selectedConfigurationId);
    const values = Object.fromEntries(new FormData(event.currentTarget));
    const input = configuration.capability === "EMBEDDING"
      ? { embeddingDimension: Number(values.embeddingDimension), distanceMetric: values.distanceMetric }
      : configuration.capability === "CHAT"
        ? { temperature: Number(values.temperature), maxOutputTokens: Number(values.maxOutputTokens) }
        : { candidateCount: Number(values.candidateCount), evidenceCount: Number(values.evidenceCount) };
    const result = prototypeStore.createTenantModelConfigurationRevision(configuration.id, input, prototypeStore.getSession().accountId);
    if (!result.ok) {
      root.querySelector("#model-revision-feedback").textContent = result.reason === "CONFIGURATION_CHANGE_REQUIRED"
        ? "请至少修改一项参数。"
        : "新配置版本未保存，请核对参数范围。";
      return;
    }
    modelFeedback = "新配置版本已保存；验证并启用前旧版本继续服务。";
    modelRevisionDialog?.close();
    render();
  });
  root.querySelector("[data-verify-model-configuration]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.verifyTenantModelConfiguration(snapshot.models.selectedConfigurationId, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? "配置验证通过，可以启用待验证版本。" : "配置验证失败，旧版本继续服务。";
    render();
  });
  root.querySelector("[data-enable-model-configuration]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.enableTenantModelConfiguration(snapshot.models.selectedConfigurationId, prototypeStore.getSession().accountId);
    if (!result.ok) {
      modelFeedback = "配置未启用，原状态保持不变。";
    } else if (result.effects.some(item => item.type === "FULL_INDEX_REBUILD_CREATED")) {
      modelFeedback = `配置已启用；自动建立 ${result.effects.length} 个全库重建候选，不会自动激活。`;
    } else {
      modelFeedback = "配置已启用；已标记助手重新评测，不重建向量索引。";
    }
    render();
  });

  const tenantDeploymentDialog = root.querySelector("#tenant-deployment-dialog");
  root.querySelector("[data-open-tenant-deployment]")?.addEventListener("click", () => tenantDeploymentDialog?.showModal());
  root.querySelector("#tenant-deployment-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const result = prototypeStore.createTenantModelDeployment(workspaceTenantId(), {
      name: form.get("name"),
      configurationId: form.get("configurationId"),
    }, prototypeStore.getSession().accountId);
    if (!result.ok) {
      root.querySelector("#tenant-deployment-feedback").textContent = "部署草稿未保存，请选择已启用配置并核对名称。";
      return;
    }
    modelFeedback = "部署草稿已建立；发布前必须完成能力与安全验证。";
    tenantDeploymentDialog?.close();
    navigate("model-deployment-detail");
  });
  root.querySelector("[data-verify-tenant-deployment]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.verifyTenantModelDeployment(snapshot.models.selectedDeploymentId, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? "部署验证通过，可以发布。" : "部署验证失败，未发布。";
    render();
  });
  root.querySelector("[data-publish-tenant-deployment]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.publishTenantModelDeployment(snapshot.models.selectedDeploymentId, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? "部署已发布；助手版本不会自动切换。" : "部署未发布，状态未变化。";
    render();
  });
  const stopDialog = root.querySelector("#deployment-stop-dialog");
  root.querySelector("[data-open-deployment-stop]")?.addEventListener("click", () => stopDialog?.showModal());
  root.querySelector("#deployment-stop-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const acknowledged = new FormData(event.currentTarget).get("acknowledged") === "on";
    const result = prototypeStore.deactivateTenantModelDeployment(snapshot.models.selectedDeploymentId, acknowledged, prototypeStore.getSession().accountId);
    if (!result.ok) {
      root.querySelector("#deployment-stop-feedback").textContent = result.reason === "IMPACT_ACK_REQUIRED"
        ? "请先确认受影响助手与路由。"
        : "部署未停用，原状态保持不变。";
      return;
    }
    modelFeedback = "部署已停用；受影响调用将失败关闭，不会跨能力静默回退。";
    stopDialog?.close();
    render();
  });

  const routingForm = root.querySelector("#routing-policy-form");
  for (const eventName of ["input", "change"]) routingForm?.addEventListener(eventName, () => {
    root.querySelector("[data-publish-routing-policy]").disabled = true;
    root.querySelector("#routing-policy-feedback").textContent = "配置已修改，请重新运行路由模拟后发布。";
  });
  routingForm?.addEventListener("submit", event => {
    event.preventDefault();
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const form = new FormData(event.currentTarget);
    const result = prototypeStore.simulateRoutingPolicy(snapshot.models.selectedRoutingPolicyId, {
      primaryDeploymentId: form.get("primaryDeploymentId"),
      fallbackDeploymentId: form.get("fallbackDeploymentId"),
      monthlyBudget: form.get("monthlyBudget"),
    }, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? "30 / 30 个成本、延迟与失败样本通过，可以发布。" : {
      ROUTING_FALLBACK_DUPLICATE: "主部署与有限回退不能选择同一个部署。",
      ROUTING_CAPABILITY_MISMATCH: "主部署与回退部署能力不同，不能发布。",
      ROUTING_RESIDENCY_MISMATCH: "主部署与回退部署驻留范围不同，不能发布。",
    }[result.reason] || "路由模拟失败，策略未改变。";
    render();
  });
  root.querySelector("[data-publish-routing-policy]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const result = prototypeStore.publishRoutingPolicy(snapshot.models.selectedRoutingPolicyId, prototypeStore.getSession().accountId);
    modelFeedback = result.ok ? "路由策略已发布；只允许同能力、同驻留的有限回退。" : "路由策略未发布，请先通过模拟。";
    render();
  });
}

function assistantFailureMessage(result) {
  return {
    ASSISTANT_NAME_CONFLICT: "助手名称已存在，请使用其他名称。",
    KNOWLEDGE_BASE_NOT_READY: "请选择当前租户内索引就绪的知识库。",
    ASSISTANT_KNOWLEDGE_BASE_IMMUTABLE: "稳定助手的知识库不能更换。",
    INDEX_VERSION_NOT_ELIGIBLE: "该索引版本不可用于新助手版本。",
    MODEL_CONFIGURATION_NOT_ACTIVE: "请选择已启用且能力匹配的模型配置。",
    EVALUATION_GATE_FAILED: "质量或安全门禁未通过，不能请求发布。",
    STORAGE_UNAVAILABLE: "浏览器无法保存，本次操作未生效。",
    FORBIDDEN: "当前演示身份无权管理助手。",
  }[result.reason] || result.errors?.map(item => item.message).join(" ") || "操作未完成，状态保持不变。";
}

function bindAssistantDelivery() {
  root.querySelectorAll("[data-manage-assistant-id]").forEach(button => {
    button.addEventListener("click", () => {
      let result;
      if (button.hasAttribute("data-create-assistant-version")) {
        result = prototypeStore.createAssistantVersion(button.dataset.manageAssistantId, prototypeStore.getSession().accountId);
      } else {
        result = prototypeStore.selectAssistantDeliveryItem(button.dataset.manageAssistantId, button.dataset.assistantVersionId);
      }
      if (!result.ok) {
        assistantFeedback = assistantFailureMessage(result);
        render();
        return;
      }
      assistantFeedback = "";
      navigate(button.dataset.assistantTarget || "assistant-draft");
    });
  });

  root.querySelector("#assistant-create-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const form = event.currentTarget;
    const feedback = form.querySelector("#assistant-create-feedback");
    if (!form.reportValidity()) {
      feedback.textContent = "请补全助手名称、知识库和用途说明。";
      return;
    }
    const values = new FormData(form);
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const requestedKnowledgeBase = String(values.get("knowledgeBaseName") || "").trim();
    const knowledgeBase = snapshot.knowledgeBases.find(item => item.tenantId === workspaceTenantId() && item.name === requestedKnowledgeBase);
    const result = prototypeStore.createAssistant(workspaceTenantId(), {
      name: values.get("name"),
      summary: values.get("summary"),
      knowledgeBaseId: knowledgeBase?.id || "",
    }, prototypeStore.getSession().accountId);
    if (!result.ok) {
      feedback.textContent = assistantFailureMessage(result);
      feedback.dataset.tone = "danger";
      return;
    }
    assistantFeedback = "助手已创建，知识库关联已固定。";
    navigate("assistant-draft");
  });

  root.querySelector("#assistant-draft-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form));
    const result = prototypeStore.saveAssistantDraft(form.dataset.assistantVersionId, values, prototypeStore.getSession().accountId);
    if (!result.ok) {
      const feedback = form.querySelector("#assistant-draft-feedback");
      feedback.textContent = assistantFailureMessage(result);
      feedback.dataset.tone = "danger";
      return;
    }
    assistantFeedback = "索引、模型配置版本和策略已冻结。";
    navigate("assistant-test");
  });

  root.querySelector("#assistant-test-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const form = event.currentTarget;
    const result = prototypeStore.runAssistantTest(
      form.dataset.assistantVersionId,
      new FormData(form).get("question"),
      prototypeStore.getSession().accountId,
    );
    if (!result.ok) {
      const feedback = form.querySelector("#assistant-test-feedback");
      feedback.textContent = assistantFailureMessage(result);
      feedback.dataset.tone = "danger";
      return;
    }
    assistantFeedback = result.testResult.status === "ANSWERED"
      ? "单题测试已完成，请核对回答与引用。"
      : "证据不足，已按冻结策略拒答。";
    render();
  });
  root.querySelector("[data-rerun-assistant-test]")?.addEventListener("click", () => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const context = assistantStageContext(snapshot, ["TESTED"]);
    if (!context) return;
    const result = prototypeStore.runAssistantTest(context.version.id, context.version.testResult.question, prototypeStore.getSession().accountId);
    assistantFeedback = result.ok ? "已使用同一冻结版本重新测试。" : assistantFailureMessage(result);
    render();
  });
  root.querySelector("[data-add-assistant-evaluation]")?.addEventListener("click", event => {
    const result = prototypeStore.addAssistantTestToEvaluationSet(event.currentTarget.dataset.assistantVersionId, prototypeStore.getSession().accountId);
    if (!result.ok) {
      assistantFeedback = assistantFailureMessage(result);
      render();
      return;
    }
    assistantFeedback = "单题样本已加入冻结评测集。";
    navigate("assistant-evaluation");
  });

  root.querySelectorAll("[data-run-assistant-evaluation]").forEach(button => {
    button.addEventListener("click", () => {
      const result = prototypeStore.runAssistantEvaluation(button.dataset.assistantVersionId, prototypeStore.getSession().accountId);
      assistantFeedback = result.ok ? "冻结评测完成，质量与安全门禁通过。" : assistantFailureMessage(result);
      render();
    });
  });
  root.querySelector("[data-request-assistant-release]")?.addEventListener("click", event => {
    const result = prototypeStore.requestAssistantRelease(event.currentTarget.dataset.assistantVersionId, prototypeStore.getSession().accountId);
    if (!result.ok) {
      assistantFeedback = assistantFailureMessage(result);
      render();
      return;
    }
    assistantFeedback = "发布请求已形成，请核对版本影响。";
    navigate("assistant-release");
  });

  root.querySelector("[data-confirm-release]")?.addEventListener("click", () => {
    root.querySelector("#release-dialog")?.showModal();
  });
  root.querySelector("[data-cancel-assistant-release]")?.addEventListener("click", () => {
    root.querySelector("#release-dialog")?.close();
  });
  root.querySelector("[data-publish-assistant-version]")?.addEventListener("click", event => {
    const result = prototypeStore.publishAssistantVersion(event.currentTarget.dataset.publishAssistantVersion, prototypeStore.getSession().accountId);
    if (!result.ok) {
      const feedback = root.querySelector("#assistant-release-feedback");
      feedback.textContent = assistantFailureMessage(result);
      feedback.dataset.tone = "danger";
      return;
    }
    root.querySelector("#release-dialog")?.close();
    assistantFeedback = `${result.assistant.name} v${result.version.number} 已发布；已有会话未迁移。`;
    navigate("assistants-directory");
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
      if (element.dataset.selectPlatformConnection) {
        prototypeStore.selectModelGovernanceItem("platformConnection", element.dataset.selectPlatformConnection);
      }
      if (element.dataset.selectPlatformDeployment) {
        prototypeStore.selectModelGovernanceItem("platformDeployment", element.dataset.selectPlatformDeployment);
      }
      if (element.dataset.selectTenantConnection) {
        prototypeStore.selectModelGovernanceItem("tenantConnection", element.dataset.selectTenantConnection);
      }
      if (element.dataset.selectModelConfiguration) {
        prototypeStore.selectModelGovernanceItem("configuration", element.dataset.selectModelConfiguration);
      }
      if (element.dataset.selectTenantDeployment) {
        prototypeStore.selectModelGovernanceItem("tenantDeployment", element.dataset.selectTenantDeployment);
      }
      if (element.dataset.selectEvaluationDataset) {
        prototypeStore.selectEvaluationDataset(element.dataset.selectEvaluationDataset, element.dataset.evaluationVersionId || null);
      }
      if (element.dataset.selectEvaluationRun) {
        prototypeStore.selectEvaluationRun(element.dataset.selectEvaluationRun);
      }
      if (element.dataset.selectEvaluationComparison) {
        prototypeStore.selectEvaluationComparison(element.dataset.selectEvaluationComparison);
      }
      if (element.dataset.selectQueryRun) {
        prototypeStore.selectQueryDiagnostic(element.dataset.selectQueryRun, prototypeStore.getSession().accountId);
      }
      navigate(
        element.dataset.navScreen,
        element.dataset.navState || "success",
        { updateSourceId: element.dataset.updateSource },
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
          : result.reason === "INVALID_CREDENTIALS"
            ? "组织编码、登录名或密码不正确。"
            : "当前账户暂时无法使用问答，请联系组织管理员确认功能和助手授权。";
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
      const result = prototypeStore.logoutSession();
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
  root.querySelector("[data-open-page-dialog]")?.addEventListener("click", () => {
    root.querySelector("#page-dialog")?.showModal();
  });
  root.querySelector("#console-login-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    consoleLoginName = String(values.get("username") || "");
    const result = prototypeStore.loginConsole({ username: consoleLoginName, password: values.get("password"), tenantCode: values.get("tenantCode") });
    event.currentTarget.reset();
    consoleLoginTenants = result.tenants || [];
    if (!result.ok) {
      consoleLoginFeedback = result.reason === "STORAGE_UNAVAILABLE" ? "浏览器无法保存登录状态，请检查存储后重试。"
        : result.reason === "TENANT_SELECTION_REQUIRED" ? "请选择本次管理的组织，并再次输入演示口令。"
        : result.reason === "WORKSPACE_NOT_READY" ? "此组织工作区的原型尚未接通，暂不能进入；不会打开其他组织的数据。"
        : "登录名或密码不正确，或当前账户不能进入控制台。";
      render();
      root.querySelector(result.reason === "TENANT_SELECTION_REQUIRED" ? "#console-tenant-select" : '#console-login-form [name="password"]')?.focus();
      return;
    }
    consoleLoginFeedback = "";
    cancelQueryTimer();
    activeConversationId = "";
    navigate(result.screen);
  });
  root.querySelectorAll("[data-console-logout]").forEach(button => button.addEventListener("click", () => {
    cancelQueryTimer();
    const result = prototypeStore.logoutSession();
    activeConversationId = "";
    consoleLoginFeedback = result.ok ? "" : "当前页面已退出，但未能清理演示登录记录。请关闭原型并清理本地登录态后重试。";
    navigate("console-login");
  }));
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
  bindModelGovernance();
  bindAssistantDelivery();
  bindEvaluationGovernance();
  bindAccessQueryGovernance();
  bindStructuredObjectGovernance();
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
    form.querySelectorAll('input[type="password"]').forEach(control => { control.value = ""; });
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
      prototypeStore.getSession().accountId,
    );
    if (!result.ok) {
      showErrors(createForm, feedback, result, "当前信息无法创建账户。");
      return;
    }
    const createdAccountId = result.account.id;
    accountFeedback = `已创建 ${result.account.loginName}；分配管理资格或成员与助手权限后，本人可使用刚设置的演示口令登录。`;
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
        .getWorkspaceSnapshot()
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
      prototypeStore.getSession().accountId,
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
      .getWorkspaceSnapshot()
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
            prototypeStore.getSession().accountId,
          )
        : prototypeStore.setAccountStatus(
            accountId,
            nextStatus,
            prototypeStore.getSession().accountId,
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
  const apply = input => {
    const query = input.value.trim().toLocaleLowerCase("zh-CN");
    const status = root.querySelector("[data-directory-status]")?.value || "";
    let count = 0;
    root.querySelectorAll("[data-search-text], [data-directory-row]").forEach(row => {
      const text = (row.dataset.searchText || row.textContent).toLocaleLowerCase("zh-CN");
      row.hidden = !text.includes(query) || Boolean(status && row.dataset.directoryState !== status);
      if (!row.hidden) count += 1;
    });
    const feedback = root.querySelector("[data-filter-summary]");
    if (feedback) feedback.textContent = count ? `找到 ${count} 项` : "没有匹配结果，请修改搜索词或筛选条件。";
  };
  root.querySelectorAll("[data-directory-search]").forEach((input) => {
    if (input.dataset.directorySearch === "platform-accounts") return;
    input.addEventListener("input", () => apply(input));
    root.querySelector("[data-directory-status]")?.addEventListener("change", () => apply(input));
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
      if (step === 1 && prototypeStore.getWorkspaceSnapshot().tenants.some(tenant => tenant.code === values.get("tenantCode"))) {
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
    const setup = prototypeStore.getWorkspaceSnapshot().tenantSetup;
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
  const snapshot = prototypeStore.getWorkspaceSnapshot();
  const tenantId = snapshot.selectedTenantId || workspaceTenantId();
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
    const result = prototypeStore.addMember(workspaceTenantId(), {
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
          workspaceTenantId(),
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
        workspaceTenantId(),
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
        workspaceTenantId(),
        accountId,
        assistantIds,
      );
    });
  });
  root
    .querySelector('[data-business-action="save-member-features"]')
    ?.addEventListener("click", (event) => {
      const result = prototypeStore.commitMemberFeatureDraft(
        workspaceTenantId(),
        event.currentTarget.dataset.memberAccount,
      );
      if (result.ok) render();
    });
  root
    .querySelector('[data-business-action="cancel-member-features"]')
    ?.addEventListener("click", (event) => {
      prototypeStore.discardMemberFeatureDraft(
        workspaceTenantId(),
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
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const action = new FormData(event.currentTarget).get("action");
    prototypeStore.recordHealthAction(snapshot.health.selectedIncidentId, action);
    render();
  });
  root
    .querySelector('[data-business-action="recheck-health"]')
    ?.addEventListener("click", () => {
      const snapshot = prototypeStore.getWorkspaceSnapshot();
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
      const stage = prototypeStore.getWorkspaceSnapshot().recovery.stage;
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

function evaluationFailureMessage(result) {
  return {
    EVALUATION_DATASET_NAME_CONFLICT: "数据集名称已存在，请使用其他名称。",
    EVALUATION_TEMPLATE_NOT_AVAILABLE: "所选基线不属于当前知识库或尚未发布。",
    EVALUATION_DATASET_INCOMPLETE: "尚未达到 60 个可回答、20 个拒答、10 个权限和 10 个多轮样本门槛。",
    EVALUATION_VERSION_IMMUTABLE: "已发布版本不可修改，请创建新草稿版本。",
    EXPECTED_EVIDENCE_REQUIRED: "可回答和多轮样本必须填写期望证据标识。",
    ASSISTANT_VERSION_NOT_FROZEN: "请选择已冻结索引和模型版本的助手版本。",
    EVALUATION_DATASET_NOT_PUBLISHED: "请选择已发布的数据集版本。",
    EVALUATION_KNOWLEDGE_BASE_MISMATCH: "评测数据集必须属于助手永久关联的知识库。",
    EVALUATION_DATASET_MISMATCH: "只能比较使用同一数据集版本的运行。",
    EVALUATION_ASSISTANT_MISMATCH: "只能比较同一个稳定助手的冻结版本。",
    EVALUATION_REGRESSION_BLOCKED: "发现安全或质量回退，不能形成放行决定。",
    EVALUATION_REMEDIATION_INVALID: "请填写 4 至 100 字事项名称和 2 至 40 字负责人。",
    STORAGE_UNAVAILABLE: "浏览器无法保存，本次操作未生效。",
    FORBIDDEN: "当前演示身份无权管理评测。",
  }[result.reason] || "操作未完成，原状态保持不变。";
}

function bindEvaluationGovernance() {
  const datasetDialog = root.querySelector("#evaluation-dataset-dialog");
  root.querySelector("[data-open-evaluation-dataset]")?.addEventListener("click", () => {
    datasetDialog?.showModal();
    datasetDialog?.querySelector('[name="name"]')?.focus();
  });
  root.querySelector("[data-close-evaluation-dataset]")?.addEventListener("click", () => datasetDialog?.close());
  root.querySelector("#evaluation-dataset-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    const result = prototypeStore.createEvaluationDataset(workspaceTenantId(), values, prototypeStore.getSession().accountId);
    if (!result.ok) {
      event.currentTarget.querySelector("#evaluation-dataset-feedback").textContent = evaluationFailureMessage(result);
      return;
    }
    datasetDialog?.close();
    evaluationFeedback = `已建立“${result.dataset.name}”草稿，发布前可继续维护样本。`;
    navigate("evaluation-dataset-detail");
  });

  root.querySelectorAll("[data-select-evaluation-dataset]").forEach(button => {
    button.addEventListener("click", () => {
      prototypeStore.selectEvaluationDataset(button.dataset.selectEvaluationDataset, button.dataset.evaluationVersionId);
    });
  });
  root.querySelector("[data-create-evaluation-version]")?.addEventListener("click", event => {
    const result = prototypeStore.createEvaluationDatasetVersion(event.currentTarget.dataset.createEvaluationVersion, prototypeStore.getSession().accountId);
    evaluationFeedback = result.ok
      ? result.replayed ? "已继续现有草稿，没有建立并行版本。" : "已从当前发布版本创建独立草稿。"
      : evaluationFailureMessage(result);
    render();
  });
  root.querySelector("[data-publish-evaluation-version]")?.addEventListener("click", event => {
    const result = prototypeStore.publishEvaluationDatasetVersion(event.currentTarget.dataset.publishEvaluationVersion, prototypeStore.getSession().accountId);
    evaluationFeedback = result.ok ? `数据集 v${result.version.number} 已发布并冻结。` : evaluationFailureMessage(result);
    render();
  });

  const sampleDialog = root.querySelector("#evaluation-sample-dialog");
  root.querySelector("[data-open-evaluation-sample]")?.addEventListener("click", () => sampleDialog?.showModal());
  root.querySelector("[data-close-evaluation-sample]")?.addEventListener("click", () => sampleDialog?.close());
  root.querySelector("#evaluation-sample-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    const result = prototypeStore.addEvaluationSample(event.currentTarget.dataset.evaluationVersionId, values, prototypeStore.getSession().accountId);
    if (!result.ok) {
      event.currentTarget.querySelector("#evaluation-sample-feedback").textContent = evaluationFailureMessage(result);
      return;
    }
    sampleDialog?.close();
    evaluationFeedback = `已新增 1 个${result.sample.label}样本。`;
    render();
  });

  root.querySelector("#evaluation-run-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    const result = prototypeStore.createEvaluationRun(workspaceTenantId(), values, prototypeStore.getSession().accountId);
    if (!result.ok) {
      event.currentTarget.querySelector("#evaluation-run-feedback").textContent = evaluationFailureMessage(result);
      return;
    }
    evaluationFeedback = "评测已启动，助手、索引、模型版本和数据集版本均已冻结。";
    navigate("evaluation-run-detail");
  });
  root.querySelector("[data-advance-evaluation-run]")?.addEventListener("click", event => {
    const result = prototypeStore.advanceEvaluationRun(event.currentTarget.dataset.advanceEvaluationRun, prototypeStore.getSession().accountId);
    evaluationFeedback = result.ok
      ? result.run.status === "COMPLETED" ? "评测完成，全部 M6 门禁通过。" : `已完成当前阶段，进入 ${result.run.stage}。`
      : evaluationFailureMessage(result);
    render();
  });

  const comparisonDialog = root.querySelector("#evaluation-comparison-dialog");
  root.querySelector("[data-open-evaluation-comparison]")?.addEventListener("click", () => comparisonDialog?.showModal());
  root.querySelector("[data-close-evaluation-comparison]")?.addEventListener("click", () => comparisonDialog?.close());
  root.querySelector("#evaluation-comparison-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const result = prototypeStore.createEvaluationComparison(
      new FormData(event.currentTarget).get("baselineRunId"),
      event.currentTarget.dataset.candidateRunId,
      prototypeStore.getSession().accountId,
    );
    if (!result.ok) {
      event.currentTarget.querySelector("#evaluation-comparison-feedback").textContent = evaluationFailureMessage(result);
      return;
    }
    comparisonDialog?.close();
    evaluationFeedback = result.comparison.safeToApprove ? "对比完成，未发现安全或质量回退。" : "对比完成，候选存在回退。";
    navigate("evaluation-comparison");
  });
  root.querySelectorAll("[data-evaluation-decision]").forEach(button => {
    button.addEventListener("click", () => {
      const result = prototypeStore.recordEvaluationDecision(button.dataset.evaluationComparisonId, button.dataset.evaluationDecision, prototypeStore.getSession().accountId);
      evaluationFeedback = result.ok
        ? result.comparison.decision === "APPROVED" ? "已形成放行决定，可回到助手发布门禁继续处理。" : "已阻断候选，保留基线版本。"
        : evaluationFailureMessage(result);
      render();
    });
  });

  root.querySelectorAll("[data-select-evaluation-failure]").forEach(button => {
    button.addEventListener("click", () => {
      prototypeStore.selectEvaluationFailure(button.dataset.evaluationRunId, button.dataset.selectEvaluationFailure);
      evaluationFeedback = "";
      render();
    });
  });
  const remediationDialog = root.querySelector("#evaluation-remediation-dialog");
  root.querySelector("[data-open-evaluation-remediation]")?.addEventListener("click", () => remediationDialog?.showModal());
  root.querySelector("[data-close-evaluation-remediation]")?.addEventListener("click", () => remediationDialog?.close());
  root.querySelector("#evaluation-remediation-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    const result = prototypeStore.createEvaluationRemediation(
      event.currentTarget.dataset.evaluationRunId,
      event.currentTarget.dataset.failureCategory,
      values,
      prototypeStore.getSession().accountId,
    );
    if (!result.ok) {
      event.currentTarget.querySelector("#evaluation-remediation-feedback").textContent = evaluationFailureMessage(result);
      return;
    }
    remediationDialog?.close();
    evaluationFeedback = result.replayed ? "该失败类别已有未关闭事项。" : `已创建修复事项“${result.remediation.title}”。`;
    render();
  });
}

function bindAccessQueryGovernance() {
  root.querySelector("[data-create-access-version]")?.addEventListener("click", event => {
    const result = prototypeStore.createAccessPolicyVersion(event.currentTarget.dataset.createAccessVersion, prototypeStore.getSession().accountId);
    accessGovernanceFeedback = result.ok ? "已创建草稿，当前已发布权限保持不变。" : accessPolicyFailureMessage(result);
    render();
  });
  root.querySelector("[data-open-access-create]")?.addEventListener("click", () => root.querySelector("#access-create-dialog").showModal());
  root.querySelector("#access-create-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const result = prototypeStore.createAccessPolicy(workspaceTenantId(), { name: values.get("name"), assistantId: values.get("assistantId"), memberAccountIds: values.getAll("memberAccountIds") }, prototypeStore.getSession().accountId);
    if (!result.ok) {
      root.querySelector("#access-create-feedback").textContent = result.reason === "ACCESS_POLICY_NAME_CONFLICT" ? "策略名称已存在，请修改名称。" : result.reason === "STORAGE_UNAVAILABLE" ? "无法保存，策略未创建，请重试。" : "请选择已发布助手及至少一位普通成员。";
      return;
    }
    navigate("access-policy-detail", "success", { policyId: result.policy.id });
  });
  root.querySelectorAll("[data-open-access-policy]").forEach(button => button.addEventListener("click", () => {
    const policyId = button.dataset.openAccessPolicy;
    if (prototypeStore.selectAccessPolicy(policyId, prototypeStore.getSession().accountId).ok) navigate("access-policy-detail", "success", { policyId });
  }));
  root.querySelectorAll("[data-open-audit]").forEach(button => button.addEventListener("click", () => navigate("audit-record-detail", "success", { auditId: button.dataset.openAudit })));
  root.querySelector("[data-export-audit]")?.addEventListener("click", () => {
    const result = prototypeStore.getAuditProjection(workspaceTenantId(), prototypeStore.getSession().accountId, { query: root.querySelector("[data-directory-search]").value, outcome: root.querySelector("[data-directory-status]").value });
    const feedback = root.querySelector("#governance-directory-feedback");
    if (!result.ok || !result.records.length) { feedback.textContent = "没有可导出的授权记录，请调整筛选条件。"; return; }
    let href;
    try {
      href = URL.createObjectURL(new Blob([JSON.stringify({ prototype: true, records: result.records }, null, 2)], { type: "application/json" }));
      const link = document.createElement("a");
      link.href = href; link.download = "受控审计记录-原型.json";
      link.click();
      feedback.textContent = `已生成 ${result.records.length} 条受控记录的下载文件。`;
    } catch { feedback.textContent = "导出失败，审计记录未改变，请重试。"; }
    finally { if (href) setTimeout(() => URL.revokeObjectURL(href), 1000); }
  });
  root.querySelector("[data-access-policy-assistant]")?.addEventListener("change", event => {
    const snapshot = prototypeStore.getWorkspaceSnapshot();
    const selected = snapshot.assistantDelivery.assistants.find(item => item.id === event.currentTarget.value);
    const knowledgeBase = snapshot.knowledgeBases.find(item => item.id === selected?.knowledgeBaseId);
    const field = root.querySelector("[data-derived-knowledge-base]");
    const help = root.querySelector("[data-derived-knowledge-help]");
    if (field) field.textContent = knowledgeBase?.name || "知识库不可用";
    if (help) help.textContent = `来自“${selected?.name || "所选助手"}”的永久知识库关系。`;
  });
  const accessForm = root.querySelector("#access-policy-form");
  for (const eventName of ["input", "change"]) accessForm?.addEventListener(eventName, () => {
    root.querySelectorAll("[data-preview-access-policy], [data-publish-access-policy]").forEach(button => { button.disabled = true; });
    root.querySelector("#access-policy-feedback").textContent = "授权范围已修改，请先保存草稿，再重新生成授权预览。";
  });
  accessForm?.addEventListener("submit", event => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const result = prototypeStore.saveAccessPolicyDraft(event.currentTarget.dataset.accessPolicyVersionId, {
      assistantId: values.get("assistantId"),
      memberAccountIds: values.getAll("memberAccountIds"),
    }, prototypeStore.getSession().accountId);
    accessGovernanceFeedback = result.ok ? "策略草稿已保存；请重新生成授权预览。" : accessPolicyFailureMessage(result);
    render();
  });
  root.querySelector("[data-preview-access-policy]")?.addEventListener("click", event => {
    const result = prototypeStore.previewAccessPolicyVersion(event.currentTarget.dataset.accessPolicyVersionId, prototypeStore.getSession().accountId);
    accessGovernanceFeedback = result.ok ? "允许、拒绝、历史和引用样本预览全部通过。" : accessPolicyFailureMessage(result);
    render();
  });
  root.querySelector("[data-publish-access-policy]")?.addEventListener("click", event => {
    const result = prototypeStore.publishAccessPolicyVersion(event.currentTarget.dataset.accessPolicyVersionId, prototypeStore.getSession().accountId);
    accessGovernanceFeedback = result.ok ? `策略 v${result.version.number} 已发布，成员授权范围已更新。` : accessPolicyFailureMessage(result);
    render();
  });

  const quotaDialog = root.querySelector("#query-quota-dialog");
  root.querySelector("[data-open-query-quota]")?.addEventListener("click", () => quotaDialog?.showModal());

  const remediationDialog = root.querySelector("#query-remediation-dialog");
  root.querySelector("[data-open-query-remediation]")?.addEventListener("click", () => {
    remediationDialog?.showModal();
    remediationDialog?.querySelector('[name="title"]')?.focus();
  });
  root.querySelector("[data-close-query-remediation]")?.addEventListener("click", () => remediationDialog?.close());
  root.querySelector("#query-remediation-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    const result = prototypeStore.createQueryRemediation(
      event.currentTarget.dataset.queryRunId,
      values,
      prototypeStore.getSession().accountId,
    );
    if (!result.ok) {
      event.currentTarget.querySelector("#query-remediation-feedback").textContent = accessPolicyFailureMessage(result);
      return;
    }
    remediationDialog?.close();
    accessGovernanceFeedback = result.replayed ? "此运行已有相同分类的待处理事项。" : `已创建修复事项“${result.remediation.title}”。`;
    render();
  });
}

function bindStructuredObjectGovernance() {
  const schemaForm = root.querySelector("#structured-schema-form");
  const invalidateUnsaved = () => {
    schemaForm.dataset.dirty = "true";
    root.querySelectorAll("[data-preview-structured-schema], [data-publish-structured-schema]").forEach(button => { button.disabled = true; });
    schemaForm.querySelector(".form-feedback").textContent = "有未保存修改。请保存结构草稿，再重新预检与发布。";
  };
  schemaForm?.addEventListener("input", invalidateUnsaved);
  schemaForm?.addEventListener("change", invalidateUnsaved);
  root.querySelector("#structured-schema-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const projection = prototypeStore.getStructuredDatasetProjection("dataset-office-directory", prototypeStore.getSession().accountId);
    if (!projection.ok) return;
    const fields = [...event.currentTarget.querySelectorAll("[data-dataset-field-row]")].map(row => {
      const name = row.dataset.fieldName;
      return {
        name,
        logicalType: values.get(`type-${name}`),
        semantic: values.get(`semantic-${name}`),
        queryEligible: values.has(`query-${name}`),
        sensitivity: values.get(`sensitivity-${name}`),
      };
    });
    const result = prototypeStore.saveStructuredSchemaDraft(event.currentTarget.dataset.versionId, {
      headers: projection.dataset.headers,
      fields,
      contentField: values.get("contentField"),
      titleField: values.get("titleField"),
      rowFilter: values.get("rowFilter"),
    }, prototypeStore.getSession().accountId);
    dataGovernanceFeedback = result.ok ? "结构草稿已保存；字段变化后需要重新抽样预检。" : structuredDataFailureMessage(result);
    render();
  });

  root.querySelector("[data-preview-structured-schema]")?.addEventListener("click", event => {
    const result = prototypeStore.previewStructuredSchemaVersion(event.currentTarget.dataset.previewStructuredSchema, prototypeStore.getSession().accountId);
    dataGovernanceFeedback = result.ok ? "抽样预检通过：100 行已检查，0 个阻断。" : structuredDataFailureMessage(result);
    render();
  });

  root.querySelector("[data-publish-structured-schema]")?.addEventListener("click", event => {
    const result = prototypeStore.publishStructuredSchemaVersion(event.currentTarget.dataset.publishStructuredSchema, prototypeStore.getSession().accountId);
    dataGovernanceFeedback = result.ok ? `结构 v${result.version.number} 已发布；活动索引没有自动变化。` : structuredDataFailureMessage(result);
    render();
  });

  const objectDialog = root.querySelector("#object-scope-dialog");
  root.querySelector("[data-open-object-scope]")?.addEventListener("click", () => {
    objectDialog?.showModal();
    objectDialog?.querySelector('[name="name"]')?.focus();
  });
  root.querySelector("[data-close-object-scope]")?.addEventListener("click", () => objectDialog?.close());
  root.querySelector("#object-scope-form")?.addEventListener("submit", event => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const projection = prototypeStore.getObjectStorageScopeProjection(workspaceTenantId(), prototypeStore.getSession().accountId);
    const range = projection.ok ? projection.availableRanges.find(item => item.id === values.get("rangeId")) : null;
    const result = prototypeStore.registerObjectStorageScope({
      tenantId: workspaceTenantId(),
      knowledgeBaseId: values.get("knowledgeBaseId"),
      rangeId: values.get("rangeId"),
      selectionRevision: range?.selectionRevision,
      name: values.get("name"),
    }, prototypeStore.getSession().accountId);
    if (!result.ok) {
      event.currentTarget.querySelector("#object-scope-feedback").textContent = structuredDataFailureMessage(result);
      return;
    }
    objectDialog?.close();
    dataGovernanceFeedback = result.replayed ? "该范围版本已经登记，没有重复创建来源。" : `已登记“${result.registration.name}”，下一步执行范围探测。`;
    render();
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
  const session = prototypeStore.getSession();
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
