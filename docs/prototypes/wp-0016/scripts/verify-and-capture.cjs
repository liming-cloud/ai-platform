const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require("playwright");

const baseUrl = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const artifactDirectory = path.resolve(
  process.cwd(),
  "project/evidence/artifacts/WP-0016-open-design",
);
const screens = [
  "login",
  "assistants",
  "conversation",
  "knowledge-directory",
  "knowledge-sources",
  "index-detail",
  "assistant-release",
];
const states = [
  "loading",
  "empty",
  "error",
  "denied",
  "partial",
  "disabled",
  "success",
];
const viewports = [
  { name: "desktop", width: 1440, height: 1000 },
  { name: "tablet", width: 1024, height: 900 },
  { name: "mobile", width: 390, height: 844 },
];
const failures = [];
const browserErrors = [];
let assertionCount = 0;

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

async function openScenario(page, screen, state, review = false) {
  const response = await page.goto(scenarioUrl(screen, state, review), {
    waitUntil: "domcontentloaded",
  });
  assert(
    response && response.ok(),
    screen + "/" + state + " 返回非成功 HTTP 状态",
  );
  await page
    .locator('main[data-screen="' + screen + '"][data-state="' + state + '"]')
    .waitFor();
}

async function validateScenarioMatrix(page) {
  for (const viewport of viewports) {
    await page.setViewportSize(viewport);
    for (const screen of screens) {
      for (const state of states) {
        await openScenario(page, screen, state);
        const result = await page.evaluate(() => {
          const allIds = [...document.querySelectorAll("[id]")].map(
            (element) => element.id,
          );
          return {
            paginationCount: document.querySelectorAll(".pagination").length,
            documentOverflow:
              document.documentElement.scrollWidth -
              document.documentElement.clientWidth,
            duplicateIds: allIds.filter(
              (id, index) => allIds.indexOf(id) !== index,
            ),
            reviewerHidden: document.body.dataset.review === "hidden",
            hasMainHeading: Boolean(document.querySelector("main h1")),
          };
        });
        const label = viewport.name + "/" + screen + "/" + state;
        assert(result.paginationCount <= 1, label + " 出现多个普通分页");
        assert(result.documentOverflow <= 1, label + " 出现文档级横向滚动");
        assert(result.duplicateIds.length === 0, label + " 存在重复 id");
        assert(result.reviewerHidden, label + " 未隐藏原型审查器");
        assert(result.hasMainHeading, label + " 缺少主标题");
      }
    }
  }
}

async function validateInteractions(page) {
  await page.setViewportSize(viewports[0]);
  await openScenario(page, "login", "success");
  await page.locator('#login-form button[type="submit"]').click();
  await page.locator('main[data-screen="assistants"]').waitFor();
  assert(
    new URL(page.url()).searchParams.get("screen") === "assistants",
    "登录没有进入助手目录",
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
    await assistantOptions
      .last()
      .evaluate((element) => element === document.activeElement),
    "助手目录 End 键未移动到末项",
  );
  await page.keyboard.press("Home");
  assert(
    await assistantOptions
      .first()
      .evaluate((element) => element === document.activeElement),
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
  await page
    .locator(".query-stage", { hasText: "COMPLETED" })
    .waitFor({ timeout: 7000 });
  assert(await input.isEnabled(), "流式完成后输入框未恢复");
  assert(
    (await page.locator("[data-live-text]").textContent()) ===
      "未休 7 天时，最多结转 5 天。其余 2 天是否可以补偿，需要根据适用制度和审批结果判断；当前证据不足以给出统一结论。",
    "流式完成答案与确定性 fixture 不一致",
  );

  await page.setViewportSize(viewports[2]);
  await openScenario(page, "conversation", "success");
  const historyTrigger = page.locator("[data-open-history]");
  await historyTrigger.click();
  assert(
    (await page.locator("body").getAttribute("data-history-open")) === "true",
    "移动端会话历史未打开",
  );
  await page.keyboard.press("Escape");
  assert(
    (await page.locator("body").getAttribute("data-history-open")) === "false",
    "Escape 未关闭会话历史",
  );
  assert(
    await historyTrigger.evaluate(
      (element) => element === document.activeElement,
    ),
    "关闭会话历史后焦点未返回触发器",
  );

  await page.setViewportSize(viewports[0]);
  await openScenario(page, "login", "success");
  await page.keyboard.press("Tab");
  assert(
    await page
      .locator(".skip-link")
      .evaluate((element) => element === document.activeElement),
    "首个 Tab 未聚焦跳转链接",
  );

  await openScenario(page, "index-detail", "success");
  await page.locator("[data-confirm-index]").click();
  assert(
    await page.locator("#index-dialog").evaluate((element) => element.open),
    "索引激活确认对话框未打开",
  );
  await page.locator('#index-dialog button[value="cancel"]').click();
  await openScenario(page, "assistant-release", "success");
  await page.locator("[data-confirm-release]").click();
  assert(
    await page.locator("#release-dialog").evaluate((element) => element.open),
    "助手发布确认对话框未打开",
  );

  await openScenario(page, "assistant-release", "disabled");
  assert(
    await page.locator("[data-confirm-release]").isDisabled(),
    "安全门禁失败时仍可发布",
  );
}

async function validateReducedMotion(browserInstance) {
  const context = await browserInstance.newContext({
    viewport: viewports[1],
    reducedMotion: "reduce",
  });
  const page = await context.newPage();
  await openScenario(page, "conversation", "success");
  const motion = await page.evaluate(() => {
    const style = getComputedStyle(document.querySelector(".history-panel"));
    return {
      animationDuration: style.animationDuration,
      transitionDuration: style.transitionDuration,
    };
  });
  assert(
    parseFloat(motion.animationDuration) <= 0.001,
    "reduced-motion 仍保留长动画",
  );
  assert(
    parseFloat(motion.transitionDuration) <= 0.001,
    "reduced-motion 仍保留长过渡",
  );
  const input = page.locator("#question-input");
  await input.fill("请验证减少动效下的流式完成");
  await input.press("Enter");
  await page
    .locator(".query-stage", { hasText: "COMPLETED" })
    .waitFor({ timeout: 2000 });
  await page.screenshot({
    path: path.join(
      artifactDirectory,
      "conversation-tablet-reduced-motion.png",
    ),
    fullPage: true,
    animations: "disabled",
  });
  await context.close();
}

async function captureReviewSet(page) {
  const captures = [
    ["login", "success", viewports[0]],
    ["assistants", "success", viewports[0]],
    ["conversation", "partial", viewports[0]],
    ["knowledge-directory", "success", viewports[0]],
    ["knowledge-sources", "partial", viewports[0]],
    ["index-detail", "success", viewports[0]],
    ["assistant-release", "disabled", viewports[0]],
    ["conversation", "success", viewports[1]],
    ["knowledge-sources", "error", viewports[1]],
    ["login", "success", viewports[2]],
    ["assistants", "empty", viewports[2]],
    ["conversation", "success", viewports[2]],
    ["knowledge-sources", "denied", viewports[2]],
  ];
  for (const [screen, state, viewport] of captures) {
    await page.setViewportSize(viewport);
    await openScenario(page, screen, state);
    await page.screenshot({
      path: path.join(
        artifactDirectory,
        screen + "-" + state + "-" + viewport.name + ".png",
      ),
      fullPage: true,
      animations: "disabled",
    });
  }

  await page.setViewportSize(viewports[2]);
  await openScenario(page, "conversation", "success");
  await page.locator("body").click({ position: { x: 370, y: 820 } });
  await page.keyboard.press("Tab");
  await page.screenshot({
    path: path.join(
      artifactDirectory,
      "conversation-mobile-keyboard-focus.png",
    ),
    fullPage: true,
    animations: "disabled",
  });
}

async function main() {
  fs.mkdirSync(artifactDirectory, { recursive: true });
  const browserInstance = await chromium.launch({ headless: true });
  const context = await browserInstance.newContext();
  const page = await context.newPage();
  page.on("pageerror", (error) =>
    browserErrors.push("pageerror: " + error.message),
  );
  page.on("console", (message) => {
    if (message.type() === "error") {
      browserErrors.push("console: " + message.text());
    }
  });
  page.on("requestfailed", (request) => {
    browserErrors.push(
      "requestfailed: " +
        request.url() +
        " · " +
        (request.failure()?.errorText || "unknown"),
    );
  });

  await validateScenarioMatrix(page);
  await validateInteractions(page);
  await captureReviewSet(page);
  await context.close();
  await validateReducedMotion(browserInstance);
  await browserInstance.close();

  assert(browserErrors.length === 0, "浏览器错误：" + browserErrors.join("；"));
  const result = {
    status: failures.length === 0 ? "PASS" : "FAIL",
    assertionCount,
    scenarioCount: screens.length * states.length * viewports.length,
    screenshotCount: 15,
    browserErrors,
    failures,
  };
  process.stdout.write(JSON.stringify(result, null, 2) + "\n");
  if (failures.length > 0) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  process.stderr.write(error.stack + "\n");
  process.exitCode = 1;
});
