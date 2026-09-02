import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { renderInventoryPage } from "../full-platform.js";

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
  const expression = new RegExp(`${attribute}="([^"]+)"`, "g");
  return [...html.matchAll(expression)].map((match) => match[1]);
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

const fixtureNext = (screen) => pageFixtures.pages[screen]?.nextScreen;
for (const [from, to] of [
  ["platform-tenants", "platform-tenant-create"],
  ["platform-tenant-create", "platform-tenant-detail"],
  ["knowledge-overview", "knowledge-sources"],
  ["source-add", "source-detail"],
  ["source-detail", "processing-quality"],
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
  ["platform-health", "ingestion-queue"],
  ["ingestion-queue", "model-health"],
  ["model-health", "capacity-planning"],
  ["capacity-planning", "backup-recovery"],
  ["backup-recovery", "release-operations"],
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
      assert(html.includes(`>${page.route}</span>`), `${page.screen} 未展示目标路径`);
      assert(html.includes(page.task), `${page.screen} 未展示唯一任务`);
      assert(html.includes(page.primaryAction), `${page.screen} 未展示主要操作`);
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
    "此结果不启动浏览器；文档级横向滚动、真实焦点、交互与截图仍由 verify-and-capture.cjs 验证。",
};

fs.mkdirSync(artifactDirectory, { recursive: true });
fs.writeFileSync(
  path.join(artifactDirectory, "static-verification.json"),
  JSON.stringify(result, null, 2) + "\n",
);
process.stdout.write(JSON.stringify(result, null, 2) + "\n");
if (failures.length > 0) {
  process.exitCode = 1;
}
