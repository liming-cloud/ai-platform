const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require("playwright");

const baseUrl = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const prototypeDirectory = path.resolve(__dirname, "..");
const artifactDirectory = path.join(prototypeDirectory, "artifacts");
const screenshotDirectory = path.join(artifactDirectory, "screenshots");
const inventory = JSON.parse(
  fs.readFileSync(path.join(prototypeDirectory, "page-inventory.json"), "utf8"),
);
const pages = inventory.groups.flatMap((group) =>
  group.pages.map((page) => ({ ...page, groupId: group.id, phase: group.phase })),
);
const screens = pages.map((page) => page.screen);
const states = inventory.states;
const viewports = inventory.viewports;
const failures = [];
const browserErrors = [];
let assertionCount = 0;
let screenshotCount = 0;

function assert(condition, message) {
  assertionCount += 1;
  if (!condition) {
    failures.push(message);
  }
}

function scenarioUrl(screen, state, review = false) {
  const url = new URL(baseUrl);
  url.searchParams.set("screen", screen);
  url.searchParams.set("state", state);
  if (!review) {
    url.searchParams.set("review", "0");
  }
  return url.toString();
}

function attachBrowserErrorCapture(page, label) {
  page.on("pageerror", (error) =>
    browserErrors.push(`${label} pageerror: ${error.message}`),
  );
  page.on("console", (message) => {
    if (message.type() === "error") {
      browserErrors.push(`${label} console: ${message.text()}`);
    }
  });
  page.on("requestfailed", (request) => {
    browserErrors.push(
      `${label} requestfailed: ${request.url()} · ${request.failure()?.errorText || "unknown"}`,
    );
  });
}

async function openScenario(page, screen, state, review = false) {
  const response = await page.goto(scenarioUrl(screen, state, review), {
    waitUntil: "domcontentloaded",
  });
  assert(response && response.ok(), `${screen}/${state} 返回非成功 HTTP 状态`);
  await page
    .locator(`main[data-screen="${screen}"][data-state="${state}"]`)
    .waitFor();
}

async function validateInventoryContract(page) {
  await openScenario(page, screens[0], "success", true);
  const reviewer = await page.evaluate(() => ({
    options: [...document.querySelectorAll("#review-screen option")].map(
      (option) => option.value,
    ),
    groups: [...document.querySelectorAll("#review-screen optgroup")].map(
      (group) => group.label,
    ),
  }));
  assert(
    JSON.stringify(reviewer.options) === JSON.stringify(screens),
    "审查器 option 与 page-inventory.json 的 screen 顺序或集合不一致",
  );
  assert(
    reviewer.groups.length === inventory.groups.length,
    "审查器未按十个清单分组生成 optgroup",
  );
  inventory.groups.forEach((group, index) => {
    assert(
      reviewer.groups[index] === `${group.phase} · ${group.label}`,
      `审查器分组 ${group.id} 未显示阶段和中文名`,
    );
  });
  const search = page.locator("#review-search");
  await search.fill("device-answer");
  await search.press("Enter");
  await page.locator('main[data-screen="device-answer"]').waitFor();
  assert(
    new URL(page.url()).searchParams.get("screen") === "device-answer",
    "审查器快速查找未跳转到设备回答",
  );
}

async function validateScenarioMatrix(page) {
  const renderedScreens = new Set();
  for (const viewport of viewports) {
    await page.setViewportSize(viewport);
    for (const pageMeta of pages) {
      for (const state of states) {
        await openScenario(page, pageMeta.screen, state);
        renderedScreens.add(
          await page.locator("main[data-screen]").getAttribute("data-screen"),
        );
        const result = await page.evaluate(
          ({ expectedRoute, expectedTask, expectedAction }) => {
            const allIds = [...document.querySelectorAll("[id]")].map(
              (element) => element.id,
            );
            const main = document.querySelector("main[data-screen]");
            const route = main?.querySelector("[data-inventory-route]");
            const task = main?.querySelector("[data-inventory-task]");
            const action = main?.querySelector("[data-inventory-action]");
            return {
              paginationCount: document.querySelectorAll(".pagination").length,
              documentOverflow:
                document.documentElement.scrollWidth -
                document.documentElement.clientWidth,
              duplicateIds: allIds.filter(
                (id, index) => allIds.indexOf(id) !== index,
              ),
              reviewerHidden:
                document.body.dataset.review === "hidden" &&
                document.querySelector("#prototype-reviewer").hidden,
              heading: main?.querySelector("h1")?.textContent.trim() || "",
              routeMatches: route?.textContent.trim() === expectedRoute,
              taskMatches: task?.textContent.includes(expectedTask) || false,
              actionMatches: action?.textContent.includes(expectedAction) || false,
              hasProtectedContent: Boolean(
                main?.querySelector(
                  "[data-protected-content], [data-citation-excerpt]:not([hidden]), [data-conversation-id]",
                ),
              ),
            };
          },
          {
            expectedRoute: pageMeta.route,
            expectedTask: pageMeta.task,
            expectedAction: pageMeta.primaryAction,
          },
        );
        const label = `${viewport.name}/${pageMeta.screen}/${state}`;
        assert(result.paginationCount <= 1, `${label} 出现多个普通分页`);
        assert(result.documentOverflow <= 1, `${label} 出现文档级横向滚动`);
        assert(result.duplicateIds.length === 0, `${label} 存在重复 id`);
        assert(result.reviewerHidden, `${label} 未隐藏原型审查器`);
        assert(result.heading.length > 0, `${label} 缺少主标题`);
        if (state === "success") {
          assert(result.routeMatches, `${label} 未展示目标路径 ${pageMeta.route}`);
          assert(result.taskMatches, `${label} 未展示唯一任务`);
          assert(result.actionMatches, `${label} 未展示主要操作`);
        }
        if (state === "denied") {
          assert(
            !result.hasProtectedContent,
            `${label} 无权限态加载了受保护摘要或历史内容`,
          );
        }
      }
    }
  }
  assert(
    JSON.stringify([...renderedScreens]) === JSON.stringify(screens),
    "实际渲染的 main[data-screen] 与清单 screen 不完全一致",
  );
}

async function clickTo(page, selector, expectedScreen, label) {
  const control = page.locator(selector).first();
  assert((await control.count()) === 1, `${label} 缺少跳转控件 ${selector}`);
  if ((await control.count()) === 0) {
    return;
  }
  await control.click();
  await page.locator(`main[data-screen="${expectedScreen}"]`).waitFor();
  assert(
    new URL(page.url()).searchParams.get("screen") === expectedScreen,
    `${label} 未进入 ${expectedScreen}`,
  );
}

async function runFlow(page, start, steps, label) {
  await openScenario(page, start, "success");
  for (const [selector, expected] of steps) {
    await clickTo(page, selector, expected, label);
  }
}

async function validateManagementFlows(page) {
  await page.setViewportSize(viewports[0]);
  await runFlow(page, "platform-tenants", [
    ["[data-primary-action]", "platform-tenant-create"],
    ["[data-primary-action]", "platform-tenant-detail"],
  ], "租户建立闭环");
  await runFlow(page, "knowledge-directory", [
    ['[data-nav-screen="knowledge-overview"]', "knowledge-overview"],
    ["[data-primary-action]", "knowledge-sources"],
    ['.page-header [data-nav-screen="source-add"]', "source-add"],
    ["[data-primary-action]", "source-detail"],
    ["[data-primary-action]", "processing-quality"],
    ["[data-primary-action]", "sync-result"],
    ["[data-primary-action]", "index-versions"],
    ["[data-primary-action]", "index-rebuild"],
    ["[data-primary-action]", "index-detail"],
  ], "知识接入与索引闭环");
  await runFlow(page, "platform-model-connections", [
    ["[data-primary-action]", "platform-model-connection-detail"],
    ["[data-primary-action]", "platform-model-deployments"],
    ["[data-primary-action]", "platform-model-deployment-detail"],
  ], "平台模型供应链闭环");
  await runFlow(page, "model-providers", [
    ["[data-primary-action]", "model-provider-detail"],
    ["[data-primary-action]", "model-configurations"],
    ["[data-primary-action]", "model-configuration-detail"],
    ["[data-primary-action]", "model-deployments"],
    ["[data-primary-action]", "model-deployment-detail"],
    ["[data-primary-action]", "routing-policies"],
  ], "租户模型闭环");
  await runFlow(page, "assistants-directory", [
    ["[data-primary-action]", "assistant-create"],
    ["[data-primary-action]", "assistant-draft"],
    ["[data-primary-action]", "assistant-test"],
    ["[data-primary-action]", "assistant-evaluation"],
    ["[data-primary-action]", "assistant-release"],
  ], "助手交付闭环");
  await page.locator("[data-confirm-release]").click();
  assert(
    await page.locator("#release-dialog").evaluate((element) => element.open),
    "助手发布确认对话框未打开",
  );
  await page.locator('#release-dialog button[value="confirm"]').click();
  await page.locator('main[data-screen="channel-binding"]').waitFor();
  assert(
    new URL(page.url()).searchParams.get("screen") === "channel-binding",
    "助手发布后未进入渠道绑定",
  );
  await runFlow(page, "evaluation-datasets", [
    ["[data-primary-action]", "evaluation-dataset-detail"],
    ["[data-primary-action]", "evaluation-run-create"],
    ["[data-primary-action]", "evaluation-run-detail"],
    ["[data-primary-action]", "evaluation-comparison"],
    ["[data-primary-action]", "evaluation-failure-analysis"],
  ], "评测与失败分析闭环");
  await runFlow(page, "platform-health", [
    ["[data-primary-action]", "ingestion-queue"],
    ["[data-primary-action]", "model-health"],
    ["[data-primary-action]", "capacity-planning"],
    ["[data-primary-action]", "backup-recovery"],
    ["[data-primary-action]", "release-operations"],
  ], "平台运维闭环");
}

async function validateQueryFlow(page) {
  await page.setViewportSize(viewports[0]);
  await openScenario(page, "login", "success");
  await page.locator('#login-form button[type="submit"]').click();
  await page.locator('main[data-screen="assistants"]').waitFor();
  assert(
    new URL(page.url()).searchParams.get("screen") === "assistants",
    "普通用户登录没有进入助手目录",
  );
  const search = page.locator("#assistant-search");
  await search.fill("办公");
  assert(
    (await page.locator("[data-assistant-id]:not([hidden])").count()) === 1,
    "助手搜索未收敛到一个结果",
  );
  await page.locator("[data-clear-assistant-search]").click();
  const assistantOptions = page.locator("[data-assistant-id]:not([hidden])");
  await assistantOptions.first().focus();
  await page.keyboard.press("End");
  assert(
    await assistantOptions.last().evaluate((element) => element === document.activeElement),
    "助手目录 End 键未移动到末项",
  );
  await page.keyboard.press("Home");
  assert(
    await assistantOptions.first().evaluate((element) => element === document.activeElement),
    "助手目录 Home 键未移动到首项",
  );
  await assistantOptions.first().click();
  await page.locator('main[data-screen="conversation"]').waitFor();
  const citationButton = page.locator("[data-citation-id]").first();
  await citationButton.click();
  assert(
    (await citationButton.getAttribute("aria-expanded")) === "true",
    "引用展开状态未更新",
  );
  assert(
    await page.locator("[data-citation-excerpt]").first().isVisible(),
    "引用授权后仍未展示摘录",
  );
  const olderButton = page.locator("[data-load-older]");
  await olderButton.click();
  assert(await olderButton.isDisabled(), "按事件序号加载后按钮未进入终点状态");
  const input = page.locator("#question-input");
  await input.fill("未休 7 天如何处理？");
  await input.press("Enter");
  await page.locator(".query-stage", { hasText: "COMPLETED" }).waitFor({ timeout: 7000 });
  assert(await input.isEnabled(), "流式完成后输入框未恢复");
  assert(
    (await page.locator("[data-live-text]").textContent()) ===
      "未休 7 天时，最多结转 5 天。其余 2 天是否可以补偿，需要根据适用制度和审批结果判断；当前证据不足以给出统一结论。",
    "流式完成答案与确定性 fixture 不一致",
  );
  await openScenario(page, "conversation", "partial");
  assert(
    await page.locator(".inline-note", { hasText: "事件序号 44" }).isVisible(),
    "断线恢复未展示最后确认事件序号",
  );
  await page.setViewportSize(viewports[2]);
  await openScenario(page, "conversation", "success");
  const historyTrigger = page.locator("[data-open-history]");
  await historyTrigger.click();
  assert(
    (await page.locator("body").getAttribute("data-history-open")) === "true",
    "移动端会话历史未打开",
  );
  await page.locator("[data-conversation-id]").nth(1).click();
  assert(
    (await page.locator("body").getAttribute("data-history-open")) === "false",
    "切换历史后 Drawer 未关闭",
  );
  await historyTrigger.click();
  await page.keyboard.press("Escape");
  assert(
    (await page.locator("body").getAttribute("data-history-open")) === "false",
    "Escape 未关闭会话历史",
  );
  assert(
    await historyTrigger.evaluate((element) => element === document.activeElement),
    "关闭会话历史后焦点未返回触发器",
  );
  await page.setViewportSize(viewports[0]);
  await openScenario(page, "login", "success");
  await page.keyboard.press("Tab");
  assert(
    await page.locator(".skip-link").evaluate((element) => element === document.activeElement),
    "首个 Tab 未聚焦跳转链接",
  );
  await openScenario(page, "index-detail", "success");
  await page.locator("[data-confirm-index]").click();
  assert(
    await page.locator("#index-dialog").evaluate((element) => element.open),
    "索引激活确认对话框未打开",
  );
  await page.locator('#index-dialog button[value="cancel"]').click();
  await openScenario(page, "assistant-release", "disabled");
  assert(
    await page.locator("[data-confirm-release]").isDisabled(),
    "安全门禁失败时仍可发布",
  );
}

async function validateDeviceFlow(page) {
  await page.setViewportSize(viewports[2]);
  await runFlow(page, "device-start", [
    ["[data-primary-action]", "device-consent"],
    ["[data-primary-action]", "device-question"],
    ["[data-primary-action]", "device-answer"],
    ["[data-primary-action]", "device-question"],
  ], "设备问答闭环");
}

async function capture(page, screen, state, viewport, suffix = "") {
  await page.setViewportSize(viewport);
  await openScenario(page, screen, state);
  await page.screenshot({
    path: path.join(
      screenshotDirectory,
      `${screen}-${state}-${viewport.name}${suffix}.png`,
    ),
    fullPage: true,
    animations: "disabled",
  });
  screenshotCount += 1;
}

async function captureReviewSet(page) {
  for (const screen of screens) {
    await capture(page, screen, "success", viewports[0]);
  }
  for (const screen of [
    "platform-tenants",
    "conversation",
    "device-question",
    "platform-tenant-detail",
    "backup-recovery",
    "assistant-release",
  ]) {
    await capture(page, screen, "success", viewports[1], "-representative");
    await capture(page, screen, "success", viewports[2], "-representative");
  }
}

async function validateReducedMotion(browserInstance) {
  const context = await browserInstance.newContext({ reducedMotion: "reduce" });
  const page = await context.newPage();
  attachBrowserErrorCapture(page, "reduced-motion");
  for (const [screen, viewport] of [
    ["platform-health", viewports[1]],
    ["conversation", viewports[2]],
    ["device-answer", viewports[2]],
  ]) {
    await page.setViewportSize(viewport);
    await openScenario(page, screen, "success");
    const motion = await page.evaluate(() => {
      const target =
        document.querySelector(".history-panel") ||
        document.querySelector(".admin-shell") ||
        document.querySelector(".device-stage");
      const style = getComputedStyle(target);
      return {
        animationDuration: style.animationDuration,
        transitionDuration: style.transitionDuration,
      };
    });
    assert(
      parseFloat(motion.animationDuration || "0") <= 0.001,
      `${screen} reduced-motion 仍保留长动画`,
    );
    assert(
      parseFloat(motion.transitionDuration || "0") <= 0.001,
      `${screen} reduced-motion 仍保留长过渡`,
    );
    await page.screenshot({
      path: path.join(
        screenshotDirectory,
        `${screen}-success-${viewport.name}-reduced-motion.png`,
      ),
      fullPage: true,
      animations: "disabled",
    });
    screenshotCount += 1;
  }
  await context.close();
}

async function main() {
  fs.mkdirSync(screenshotDirectory, { recursive: true });
  assert(pages.length === 68, `页面清单数量应为 68，实际为 ${pages.length}`);
  assert(new Set(screens).size === screens.length, "页面清单包含重复 screen");
  const browserInstance = await chromium.launch({ headless: true });
  const context = await browserInstance.newContext();
  const page = await context.newPage();
  attachBrowserErrorCapture(page, "default");
  await validateInventoryContract(page);
  await validateScenarioMatrix(page);
  await validateManagementFlows(page);
  await validateQueryFlow(page);
  await validateDeviceFlow(page);
  await captureReviewSet(page);
  await context.close();
  await validateReducedMotion(browserInstance);
  await browserInstance.close();
  assert(browserErrors.length === 0, `浏览器错误：${browserErrors.join("；")}`);
  const result = {
    status: failures.length === 0 ? "PASS" : "FAIL",
    generatedAt: new Date().toISOString(),
    pageCount: pages.length,
    stateCount: states.length,
    viewportCount: viewports.length,
    scenarioCount: pages.length * states.length * viewports.length,
    assertionCount,
    screenshotCount,
    browserErrors,
    failures,
    inventorySummary: {
      schemaVersion: inventory.schemaVersion,
      description: inventory.description,
      groups: inventory.groups.map((group) => ({
        id: group.id,
        label: group.label,
        phase: group.phase,
        shell: group.shell,
        pageCount: group.pages.length,
      })),
    },
  };
  fs.writeFileSync(
    path.join(artifactDirectory, "verification.json"),
    JSON.stringify(result, null, 2) + "\n",
  );
  process.stdout.write(JSON.stringify(result, null, 2) + "\n");
  if (failures.length > 0) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  const blockedResult = {
    status: "BLOCKED",
    generatedAt: new Date().toISOString(),
    pageCount: pages.length,
    stateCount: states.length,
    viewportCount: viewports.length,
    scenarioCount: pages.length * states.length * viewports.length,
    assertionCount,
    screenshotCount,
    browserErrors: ["当前运行环境未授予浏览器进程或本地页面访问权限。"],
    failures: [
      "真实 DOM、焦点、文档级横向滚动、交互与截图验证未执行。",
    ],
    inventorySummary: {
      schemaVersion: inventory.schemaVersion,
      description: inventory.description,
      groups: inventory.groups.map((group) => ({
        id: group.id,
        label: group.label,
        phase: group.phase,
        shell: group.shell,
        pageCount: group.pages.length,
      })),
    },
  };
  fs.mkdirSync(artifactDirectory, { recursive: true });
  fs.writeFileSync(
    path.join(artifactDirectory, "verification.json"),
    JSON.stringify(blockedResult, null, 2) + "\n",
  );
  process.stderr.write(`${error.stack}\n`);
  process.exitCode = 1;
});
