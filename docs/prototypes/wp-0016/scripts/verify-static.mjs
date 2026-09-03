import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { renderInventoryPage } from "../full-platform.js";
import { renderConsoleNavigation } from "../navigation.js";
import { createMemoryStorage, createPrototypeStore } from "../prototype-state.js";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const prototypeDirectory = path.resolve(scriptDirectory, "..");
const artifactDirectory = path.join(prototypeDirectory, "artifacts");
const baseUrl = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const inventory = JSON.parse(
  fs.readFileSync(path.join(prototypeDirectory, "page-inventory.json"), "utf8"),
);
const pageFixtures = JSON.parse(
  fs.readFileSync(path.join(prototypeDirectory, "page-fixtures.json"), "utf8"),
);
const fixtures = JSON.parse(
  fs.readFileSync(path.join(prototypeDirectory, "fixtures.json"), "utf8"),
);
const prototypeSource = fs.readFileSync(
  path.join(prototypeDirectory, "prototype.js"),
  "utf8",
);
const fullPlatformSource = fs.readFileSync(
  path.join(prototypeDirectory, "full-platform.js"),
  "utf8",
);
const indexSource = fs.readFileSync(
  path.join(prototypeDirectory, "index.html"),
  "utf8",
);
const tokenSource = fs.readFileSync(
  path.join(prototypeDirectory, "tokens.css"),
  "utf8",
);
const styleSource = fs.readFileSync(
  path.join(prototypeDirectory, "prototype.css"),
  "utf8",
);
const navigationSource = fs.readFileSync(
  path.join(prototypeDirectory, "navigation.js"),
  "utf8",
);
const stateSource = fs.readFileSync(
  path.join(prototypeDirectory, "prototype-state.js"),
  "utf8",
);
const iconManifest = JSON.parse(
  fs.readFileSync(
    path.join(prototypeDirectory, "assets/icons/manifest.json"),
    "utf8",
  ),
);
const actionLedger = JSON.parse(
  fs.readFileSync(path.join(prototypeDirectory, "ACTION-LEDGER.json"), "utf8"),
);
const demoStore = createPrototypeStore(createMemoryStorage());
demoStore.reset();
const demoState = demoStore.getSnapshot();
const pages = inventory.groups.flatMap((group) =>
  group.pages.map((page) => ({ page, group })),
);
const specialized = new Map([
  ["login", "renderLogin"],
  ["assistants", "renderAssistants"],
  ["conversation", "renderConversation"],
  ["knowledge-directory", "renderKnowledgeDirectory"],
  ["knowledge-sources", "renderKnowledgeSources"],
  ["index-detail", "renderIndexDetail"],
  ["assistant-release", "renderAssistantRelease"],
]);
const failures = [];
let assertionCount = 0;
let renderedScenarioCount = 0;
let httpScenarioCount = 0;

function assert(condition, message) {
  assertionCount += 1;
  if (!condition) {
    failures.push(message);
  }
}

function attributeValues(html, attribute) {
  const expression = new RegExp(`(?:^|\\s)${attribute}="([^"]+)"`, "g");
  return [...html.matchAll(expression)].map((match) => match[1]);
}

function functionSource(name, nextName) {
  const start = fullPlatformSource.indexOf(`function ${name}(`);
  const end = nextName
    ? fullPlatformSource.indexOf(`function ${nextName}(`, start + 1)
    : fullPlatformSource.length;
  return start >= 0 ? fullPlatformSource.slice(start, end > start ? end : undefined) : "";
}

assert(pages.length === 68, `页面数应为 68，实际为 ${pages.length}`);
assert(inventory.groups.length === 10, "页面分组数不是 10");
assert(inventory.states.length === 7, "状态数不是 7");
assert(inventory.viewports.length === 3, "视口数不是 3");

const inventoryScreens = pages.map(({ page }) => page.screen);
const fixtureScreens = Object.keys(pageFixtures.pages);
assert(
  JSON.stringify(inventoryScreens) === JSON.stringify(fixtureScreens),
  "page-fixtures.json 与 page-inventory.json 的 screen 顺序或集合不一致",
);
assert(!prototypeSource.includes("scrollIntoView"), "prototype.js 禁止使用 scrollIntoView");
assert(
  prototypeSource.includes('fetch("./page-inventory.json"'),
  "prototype.js 未显式加载 page-inventory.json",
);
assert(
  prototypeSource.includes("inventory.groups") &&
    prototypeSource.includes("<optgroup"),
  "审查器未由清单分组生成 optgroup",
);
assert(
  indexSource.includes('id="review-contract"') &&
    prototypeSource.includes("updateReviewerContext"),
  "目标路径、唯一任务和主要操作尚未迁入审查工具",
);
assert(
  prototypeSource.includes("原型前置，不代表实现授权"),
  "审查工具未显式保留 M7/M8 原型授权边界",
);
assert(
  tokenSource.includes("--design-variance: 7") &&
    tokenSource.includes("--motion-intensity: 5") &&
    tokenSource.includes("--visual-density: 7"),
  "视觉候选参数不是 7 / 5 / 7",
);
assert(
  tokenSource.includes("--motion-fast: 160ms") &&
    tokenSource.includes("--motion-normal: 200ms") &&
    tokenSource.includes("--motion-slow: 240ms"),
  "交互反馈未绑定 160-240ms 动效令牌",
);
assert(
  styleSource.includes("@media (prefers-reduced-motion: reduce)") &&
    styleSource.includes("animation-iteration-count: 1 !important"),
  "reduced-motion 未关闭非必要动画",
);
assert(
  prototypeSource.includes('from "./navigation.js"') &&
    fullPlatformSource.includes('from "./navigation.js"') &&
    !prototypeSource.includes("function navGroup(") &&
    !fullPlatformSource.includes("function consoleNavigation("),
  "专属页与通用页尚未共用唯一导航渲染入口",
);
const platformNavigation = renderConsoleNavigation({
  screen: "platform-model-deployment-detail",
  scope: "platform",
  snapshot: demoState,
});
const tenantNavigation = renderConsoleNavigation({
  screen: "source-detail",
  scope: "tenant",
  snapshot: demoState,
});
assert(
  [platformNavigation, tenantNavigation].every(
    (html) =>
      html.includes('<img class="nav-icon"') &&
      !html.includes("nav-abbr") &&
      !/emoji/i.test(html),
  ),
  "导航未全部使用本地 Fluent SVG 图片",
);
assert(
  platformNavigation.includes(">平台运行状态<") &&
    platformNavigation.includes(">备份与恢复<") &&
    platformNavigation.includes('aria-label="治理"'),
  "平台导航缺少完整自然中文或模型治理分组",
);
assert(
  tenantNavigation.includes('data-nav-screen="knowledge-sources"') &&
    tenantNavigation.includes('aria-current="page"'),
  "文档详情未高亮文档与来源父目录",
);
assert(
  iconManifest.package === "Microsoft Fluent UI System Icons" &&
    /^[a-f0-9]{40}$/.test(iconManifest.commit) &&
    iconManifest.license === "MIT" &&
    iconManifest.files.every((entry) =>
      fs.existsSync(path.join(prototypeDirectory, "assets/icons", entry.local)),
    ) &&
    fs.existsSync(path.join(prototypeDirectory, "assets/icons/LICENSE.txt")),
  "Fluent 图标资产缺少固定 commit、清单、本地文件或 MIT 许可",
);
assert(
  !prototypeSource.includes('value="prototype-only"') &&
    !fullPlatformSource.includes('value="prototype-only"') &&
    !/password|credential|secret/i.test(JSON.stringify(demoState)),
  "密码被预填或进入了合成持久状态",
);
assert(
  actionLedger.pages.length === pages.length &&
    actionLedger.summary.unclosedControls > 0 &&
    actionLedger.browserVerification === "pendingAuthorization",
  "68 页操作账本缺失或错误宣称全部闭环",
);
assert(
  navigationSource.includes("PARENT_SCREEN") &&
    stateSource.includes("getEffectiveAccess"),
  "导航父目录或有效权限交集缺少公共实现",
);
assert(
  !fullPlatformSource.includes("function contractStrip("),
  "共享渲染器仍包含产品正文工程合同条",
);
assert(
  !fullPlatformSource.includes('value="员工服务手册"'),
  "向导仍硬编码错误的通用关联对象",
);
const detailSource = functionSource("renderDetail", "renderResult");
assert(
  !(detailSource.includes("summaryFacts(fixture)") && detailSource.includes("factList(fixture)")),
  "详情模式仍重复渲染同一组事实",
);

for (const [screen, fixture] of Object.entries(pageFixtures.pages)) {
  if (fixture.mode === "wizard") {
    assert(
      Array.isArray(fixture.fields) && fixture.fields.length >= 2,
      `${screen} 向导缺少本任务专属输入字段`,
    );
  }
  if (fixture.mode === "risk") {
    assert(
      ["object", "impact", "recovery", "audit"].every(
        (key) => typeof fixture.risk?.[key] === "string" && fixture.risk[key].length > 0,
      ),
      `${screen} 高风险页面缺少对象、影响、恢复或审计事实`,
    );
  }
}

const fixtureNext = (screen) => pageFixtures.pages[screen]?.nextScreen;
for (const [from, to] of [
  ["platform-tenants", "platform-tenant-create"],
  ["platform-tenant-create", "platform-tenant-detail"],
  ["knowledge-overview", "knowledge-sources"],
  ["source-add", "source-detail"],
  ["source-detail", "source-add"],
  ["processing-quality", "sync-result"],
  ["sync-result", "index-versions"],
  ["index-versions", "index-rebuild"],
  ["index-rebuild", "index-detail"],
  ["platform-model-connections", "platform-model-connection-detail"],
  ["platform-model-connection-detail", "platform-model-deployments"],
  ["platform-model-deployments", "platform-model-deployment-detail"],
  ["model-providers", "model-provider-detail"],
  ["model-provider-detail", "model-configurations"],
  ["model-configurations", "model-configuration-detail"],
  ["model-configuration-detail", "model-deployments"],
  ["model-deployments", "model-deployment-detail"],
  ["model-deployment-detail", "routing-policies"],
  ["assistants-directory", "assistant-create"],
  ["assistant-create", "assistant-draft"],
  ["assistant-draft", "assistant-test"],
  ["assistant-test", "assistant-evaluation"],
  ["assistant-evaluation", "assistant-release"],
  ["evaluation-datasets", "evaluation-dataset-detail"],
  ["evaluation-dataset-detail", "evaluation-run-create"],
  ["evaluation-run-create", "evaluation-run-detail"],
  ["evaluation-run-detail", "evaluation-comparison"],
  ["evaluation-comparison", "evaluation-failure-analysis"],
  ["device-start", "device-consent"],
  ["device-consent", "device-question"],
  ["device-question", "device-answer"],
  ["device-answer", "device-question"],
  ["ingestion-queue", "model-health"],
  ["model-health", "capacity-planning"],
  ["capacity-planning", "backup-recovery"],
]) {
  assert(fixtureNext(from) === to, `${from} 未声明通向 ${to} 的闭环跳转`);
}
assert(
  prototypeSource.includes('data-nav-screen="knowledge-overview"'),
  "知识库目录未进入知识库概览",
);
assert(
  prototypeSource.includes('navigate("channel-binding")'),
  "助手发布确认后未进入渠道绑定",
);

for (const [screen, functionName] of specialized) {
  assert(
    prototypeSource.includes(`function ${functionName}(`),
    `${screen} 的已验证专属渲染函数缺失`,
  );
}

for (const { page, group } of pages) {
  const fixture = pageFixtures.pages[page.screen];
  assert(Boolean(fixture?.title), `${page.screen} 缺少中文页面标题`);
  if (specialized.has(page.screen)) {
    continue;
  }
  for (const state of inventory.states) {
    const html = renderInventoryPage(
      page,
      group,
      fixture,
      state,
      fixtures.tenant,
      demoState,
    );
    renderedScenarioCount += 1;
    const ids = attributeValues(html, "id");
    const duplicateIds = ids.filter((id, index) => ids.indexOf(id) !== index);
    const paginationCount = (html.match(/class="pagination"/g) || []).length;
    assert(
      html.includes(`data-screen="${page.screen}"`),
      `${page.screen}/${state} 缺少 main[data-screen]`,
    );
    assert(
      html.includes(`data-state="${state}"`),
      `${page.screen}/${state} 缺少 data-state`,
    );
    assert(/<h1[^>]*>[^<]+<\/h1>/.test(html), `${page.screen}/${state} 缺少主标题`);
    assert(paginationCount <= 1, `${page.screen}/${state} 出现多个普通分页`);
    assert(duplicateIds.length === 0, `${page.screen}/${state} 存在重复 id`);
    if (state === "success") {
      assert(
        !html.includes("data-inventory-route") &&
          !html.includes("data-inventory-task") &&
          !html.includes("data-inventory-action"),
        `${page.screen} 产品正文仍包含工程合同标记`,
      );
      assert(!html.includes(page.route), `${page.screen} 产品正文仍展示目标路径`);
      assert(!html.includes(page.task), `${page.screen} 产品正文仍复述唯一任务`);
      assert(
        html.includes(`data-primary-action`) && html.includes(`>${page.primaryAction}</button>`),
        `${page.screen} 缺少可操作的主要动作`,
      );
      if (group.phase === "M7" || group.phase === "M8") {
        assert(
          !html.includes("原型前置") && !html.includes("不代表实现授权"),
          `${page.screen} 将原型授权边界写入产品正文`,
        );
      }
      if (fixture.mode === "detail") {
        assert(
          (html.match(/data-fact-set=/g) || []).length <= 1,
          `${page.screen} 详情事实被重复渲染`,
        );
      }
    }
    if (state === "denied") {
      assert(
        !html.includes("data-protected-content"),
        `${page.screen} denied 状态包含受保护内容标记`,
      );
    }
  }
}

for (const viewport of inventory.viewports) {
  for (const { page } of pages) {
    for (const state of inventory.states) {
      const url = new URL(baseUrl);
      url.searchParams.set("screen", page.screen);
      url.searchParams.set("state", state);
      url.searchParams.set("review", "0");
      const response = await fetch(url);
      httpScenarioCount += 1;
      assert(
        response.ok,
        `${viewport.name}/${page.screen}/${state} HTTP 状态为 ${response.status}`,
      );
      await response.body?.cancel();
    }
  }
}

const result = {
  status: failures.length === 0 ? "PASS" : "FAIL",
  generatedAt: new Date().toISOString(),
  pageCount: pages.length,
  groupCount: inventory.groups.length,
  stateCount: inventory.states.length,
  viewportCount: inventory.viewports.length,
  staticRenderedScenarioCount: renderedScenarioCount,
  httpScenarioCount,
  assertionCount,
  failures,
  limitation:
    "此结果只验证 Node 状态机、静态渲染合同和 URL 可达性；不启动浏览器，HTTP 成功不代表真实场景成立。文档级横向滚动、真实焦点、交互与本轮截图仍待用户重新授权。",
};

fs.mkdirSync(artifactDirectory, { recursive: true });
const outputName = process.env.STATIC_VERIFICATION_OUTPUT || "static-verification.json";
fs.writeFileSync(
  path.join(artifactDirectory, outputName),
  JSON.stringify(result, null, 2) + "\n",
);
process.stdout.write(JSON.stringify(result, null, 2) + "\n");
if (failures.length > 0) {
  process.exitCode = 1;
}
