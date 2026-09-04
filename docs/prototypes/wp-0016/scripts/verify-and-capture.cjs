const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { chromium } = require("playwright");

const baseUrl = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const prototypeDirectory = path.resolve(__dirname, "..");
const artifactDirectory =
  process.env.PROTOTYPE_ARTIFACT_DIR ||
  path.join(os.tmpdir(), "wp-0016", "full-platform-browser");
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
let completedScenarioCount = 0;
const stageResults = [];

function assert(condition, message) {
  assertionCount += 1;
  if (!condition) {
    failures.push(message);
  }
}

function scenarioUrl(screen, state, review = false, conversationId = "") {
  const url = new URL(baseUrl);
  url.searchParams.set("screen", screen);
  url.searchParams.set("state", state);
  url.searchParams.set("review", review ? "1" : "0");
  if (screen === "conversation" && conversationId) {
    url.searchParams.set("conversationId", conversationId);
  }
  return url.toString();
}

async function loginQueryDemo(page) {
  await page.goto(scenarioUrl("login", "success"), {
    waitUntil: "domcontentloaded",
  });
  await page.locator('#login-form input[name="tenantCode"]').fill("spruce-demo");
  await page.locator('#login-form input[name="username"]').fill("demo.user");
  await page.locator('#login-form input[name="password"]').fill("demo-only");
  await page.locator('#login-form button[type="submit"]').click();
  await page.locator('main[data-screen="assistants"]').waitFor();
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
  const protectedQueryScreen = ["assistants", "conversation"].includes(screen);
  const targetUrl = scenarioUrl(
    screen,
    state,
    review,
    screen === "conversation" ? "cv-20260902-01" : "",
  );
  let response = await page.goto(targetUrl, {
    waitUntil: "domcontentloaded",
  });
  if (
    protectedQueryScreen &&
    (await page.locator('main[data-screen="login"]').count()) > 0
  ) {
    await loginQueryDemo(page);
    response = await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
  }
  assert(response && response.ok(), `${screen}/${state} 返回非成功 HTTP 状态`);
  await page
    .locator(`main[data-screen="${screen}"][data-state="${state}"]`)
    .waitFor();
  if (review) {
    await page.locator("#prototype-reviewer summary").click();
  }
}

async function validateInventoryContract(page) {
  await openScenario(page, screens[0], "success", true);
  await page.locator("#review-reset").click();
  const reviewer = await page.evaluate(() => ({
    options: [...document.querySelectorAll("#review-screen option")].map(
      (option) => option.value,
    ),
    groups: [...document.querySelectorAll("#review-screen optgroup")].map(
      (group) => group.label,
    ),
    contract: document.querySelector("#review-contract")?.textContent || "",
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
  assert(
    reviewer.contract.includes(pages[0].route) &&
      reviewer.contract.includes(pages[0].task) &&
      reviewer.contract.includes(pages[0].primaryAction),
    "审查工具未展示当前页面的路径、任务与主要操作",
  );
  const search = page.locator("#review-search");
  await search.fill("device-answer");
  await search.press("Enter");
  await page.locator('main[data-screen="device-answer"]').waitFor();
  assert(
    new URL(page.url()).searchParams.get("screen") === "device-answer",
    "审查器快速查找未跳转到设备回答",
  );
  assert(
    (await page.locator("#review-contract").textContent()).includes(
      "原型前置，不代表实现授权",
    ),
    "M8 页面未在审查工具显示授权边界",
  );
  await openScenario(page, "platform-overview", "success");
  const navigation = await page.evaluate(() => ({
    iconCount: document.querySelectorAll(".admin-navigation .nav-icon").length,
    invalidIcons: [...document.querySelectorAll(".admin-navigation .nav-icon")].filter(
      (icon) =>
        !icon.getAttribute("src")?.startsWith("./assets/icons/") ||
        !icon.getAttribute("src")?.endsWith(".svg"),
    ).length,
    hasAbbreviation: Boolean(document.querySelector(".nav-abbr")),
    text: document.querySelector(".admin-navigation")?.textContent || "",
  }));
  assert(navigation.iconCount >= 6, "平台导航缺少 Fluent SVG 图片图标");
  assert(navigation.invalidIcons === 0, "平台导航包含非本地 SVG 图标");
  assert(!navigation.hasAbbreviation, "平台导航仍使用首字或小字代替图标");
  assert(
    navigation.text.includes("平台运行状态") &&
      navigation.text.includes("备份与恢复") &&
      navigation.text.includes("模型治理"),
    "平台导航缺少完整自然中文或模型治理入口",
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
            const mainText = main?.textContent || "";
            const primaryAction = main?.querySelector("[data-primary-action]");
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
              hasContractMarkers: Boolean(
                main?.querySelector(
                  "[data-inventory-route], [data-inventory-task], [data-inventory-action]",
                ),
              ),
              hasContractCopy:
                mainText.includes(expectedRoute) ||
                mainText.includes(expectedTask) ||
                mainText.includes("唯一任务") ||
                mainText.includes("主要操作"),
              actionMatches:
                primaryAction?.textContent.trim() === expectedAction ||
                (main?.dataset.screen === "assistants" &&
                  Boolean(main.querySelector("[data-assistant-id]"))),
              hasAuthorizationBanner: Boolean(
                main?.querySelector(".authorization-banner"),
              ),
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
        completedScenarioCount += 1;
        assert(result.paginationCount <= 1, `${label} 出现多个普通分页`);
        assert(result.documentOverflow <= 1, `${label} 出现文档级横向滚动`);
        assert(result.duplicateIds.length === 0, `${label} 存在重复 id`);
        assert(result.reviewerHidden, `${label} 未隐藏原型审查器`);
        assert(result.heading.length > 0, `${label} 缺少主标题`);
        if (state === "success") {
          assert(!result.hasContractMarkers, `${label} 产品正文仍含工程合同标记`);
          assert(!result.hasContractCopy, `${label} 产品正文仍复述工程合同`);
          assert(result.actionMatches, `${label} 缺少可操作的主要动作`);
          if (pageMeta.phase === "M7" || pageMeta.phase === "M8") {
            assert(
              !result.hasAuthorizationBanner,
              `${label} 将原型授权边界伪装成产品正文`,
            );
          }
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

async function validateModelGovernanceFlow(page) {
  await openScenario(page, "platform-model-connections", "success");
  await page.getByRole("button", { name: "新增供应商连接", exact: true }).click();
  let dialog = page.locator("#platform-connection-dialog");
  await dialog.getByLabel("连接名称").fill("全矩阵对话连接");
  await dialog.getByLabel("运行地址").fill("https://matrix-models.example.invalid/v1");
  await dialog.getByLabel("一次性访问密钥").fill("matrix-ephemeral-key");
  await dialog.getByRole("button", { name: "保存连接草稿", exact: true }).click();
  await page.locator('main[data-screen="platform-model-connection-detail"]').waitFor();
  assert(
    !(await page.locator("body").innerText()).includes("matrix-ephemeral-key"),
    "平台模型连接回显了一次性访问密钥",
  );
  await page.getByRole("button", { name: "执行连接探测", exact: true }).click();
  await page.getByRole("button", { name: "发布连接", exact: true }).click();

  await openScenario(page, "platform-model-deployments", "success");
  await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
  dialog = page.locator("#platform-deployment-dialog");
  await dialog.getByLabel("部署名称").fill("全矩阵对话模型");
  await dialog.getByLabel("已发现模型").selectOption({ label: "企业对话模型候选 / Chat" });
  await dialog.getByRole("button", { name: "保存部署草稿", exact: true }).click();
  await page.locator('main[data-screen="platform-model-deployment-detail"]').waitFor();
  await page.getByRole("button", { name: "执行能力验证", exact: true }).click();
  await page.getByRole("button", { name: "发布部署", exact: true }).click();

  await openScenario(page, "model-providers", "success");
  await page.locator("#tenant-discovery-connection").selectOption({ label: "全矩阵对话连接 / Chat" });
  await page.getByRole("button", { name: "执行模型发现", exact: true }).click();
  await page.locator('main[data-screen="model-provider-detail"]').waitFor();
  assert(
    await page.getByText("全矩阵对话模型", { exact: true }).isVisible(),
    "租户发现结果缺少平台已授权模型",
  );
  await page.getByRole("button", { name: "使用此模型", exact: true }).click();
  await page.locator('main[data-screen="model-configurations"]').waitFor();

  await page.getByRole("button", { name: "创建模型配置", exact: true }).click();
  dialog = page.locator("#model-configuration-dialog");
  await dialog.getByLabel("配置名称").fill("全矩阵租户对话配置");
  await dialog.getByRole("button", { name: "保存配置草稿", exact: true }).click();
  await page.locator('main[data-screen="model-configuration-detail"]').waitFor();
  await page.getByRole("button", { name: "执行配置验证", exact: true }).click();
  await page.getByRole("button", { name: "启用配置", exact: true }).click();

  await openScenario(page, "model-deployments", "success");
  await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
  dialog = page.locator("#tenant-deployment-dialog");
  await dialog.getByLabel("部署名称").fill("全矩阵租户对话部署");
  await dialog.getByLabel("启用配置").selectOption({ label: "全矩阵租户对话配置 / Chat" });
  await dialog.getByRole("button", { name: "保存部署草稿", exact: true }).click();
  await page.locator('main[data-screen="model-deployment-detail"]').waitFor();
  await page.getByRole("button", { name: "执行部署验证", exact: true }).click();
  await page.getByRole("button", { name: "发布部署", exact: true }).click();

  await openScenario(page, "routing-policies", "success");
  await page.getByRole("button", { name: "运行路由模拟", exact: true }).click();
  await page.getByRole("button", { name: "发布路由策略", exact: true }).click();
  assert(
    (await page.locator("[data-model-status]").getAttribute("data-model-status")) === "PUBLISHED",
    "路由策略未形成已发布版本",
  );
}

async function validateManagementFlows(page) {
  await page.setViewportSize(viewports[0]);
  await openScenario(page, "platform-tenant-create", "success", true);
  await page.locator("#review-reset").click();
  const reviewerSummary = page.locator("#prototype-reviewer summary");
  await reviewerSummary.focus();
  await reviewerSummary.press("Enter");
  assert(
    !(await page.locator("#prototype-reviewer details").evaluate((details) => details.open)),
    "创建租户前原型审查器未通过键盘收起",
  );
  await page.locator("#tenant-organization-name").fill("青岚制造示例组织");
  await page.locator("#tenant-code").fill("qinglan-demo");
  await page.getByRole("button", { name: "下一步", exact: true }).click();
  await page.locator("#tenant-administrator").selectOption("account-qiao");
  await page.getByRole("button", { name: "下一步", exact: true }).click();
  await page.locator('#tenant-create-form input[name="confirmed"]').check();
  await page.locator("[data-primary-action]").click();
  await page.locator('main[data-screen="platform-tenant-detail"]').waitFor();
  assert(
    await page.getByText("青岚制造示例组织", { exact: true }).first().isVisible(),
    "建立租户后详情未回显组织名称",
  );
  await openScenario(page, "platform-tenants", "success");
  assert(
    await page.getByText("qinglan-demo", { exact: true }).isVisible(),
    "建立租户后目录未回显租户编码",
  );
  await page.reload({ waitUntil: "domcontentloaded" });
  assert(
    await page.getByText("qinglan-demo", { exact: true }).isVisible(),
    "刷新后新租户没有保留",
  );
  await runFlow(page, "knowledge-directory", [
    ['[data-nav-screen="knowledge-overview"]', "knowledge-overview"],
    ["[data-primary-action]", "knowledge-sources"],
    ['.page-header [data-nav-screen="source-add"]', "source-add"],
  ], "文档更新入口");
  await page.locator('#source-add-form [name="mode"]').selectOption("paste");
  await page.locator('#source-add-form [name="name"]').fill("全矩阵合成资料");
  await page.locator('#source-add-form [name="text"]').fill("用于验证安全扫描入口的合成内容。");
  await clickTo(page, "[data-primary-action]", "source-detail", "资料安全扫描");
  await clickTo(page, '[data-primary-action][data-nav-screen="source-add"]', "source-add", "文档更新入口");
  await runFlow(page, "processing-quality", [
    ["[data-primary-action]", "sync-result"],
    ["[data-primary-action]", "index-versions"],
    ["[data-primary-action]", "index-rebuild"],
    ["[data-primary-action]", "index-detail"],
  ], "处理结果与索引导航");
  await validateModelGovernanceFlow(page);
  await runFlow(page, "assistants-directory", [
    ["[data-primary-action]", "assistant-create"],
    ["[data-primary-action]", "assistant-draft"],
    ["[data-primary-action]", "assistant-test"],
    ["[data-primary-action]", "assistant-evaluation"],
    ["[data-primary-action]", "assistant-release"],
  ], "助手交付导航");
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
  ], "评测与失败分析导航");
}

async function validateTenantAndMemberControls(page) {
  await loginQueryDemo(page);
  await openScenario(page, "tenant-members", "success");
  await page.locator('[data-business-action="open-add-member"]').click();
  await page.locator("#member-account-search").fill("林澈");
  await page.locator("#member-account-select").selectOption("account-lin");
  await page.locator('#add-member-form button[type="submit"]').click();
  assert(
    await page.getByText("林澈（合成）", { exact: true }).first().isVisible(),
    "添加普通成员后列表未更新",
  );
  await page.reload({ waitUntil: "domcontentloaded" });
  assert(
    await page.getByText("林澈（合成）", { exact: true }).first().isVisible(),
    "刷新后新增普通成员未保留",
  );

  await openScenario(page, "platform-tenants", "success");
  await page.locator('[data-select-tenant="tenant-spruce"]').click();
  await page.locator('main[data-screen="platform-tenant-detail"]').waitFor();
  const assistantFeature = page.locator('[data-tenant-feature="assistantSelection"]');
  await assistantFeature.uncheck();
  await page.locator('[data-business-action="cancel-tenant-features"]').click();
  assert(
    await page.locator('[data-tenant-feature="assistantSelection"]').isChecked(),
    "取消租户功能编辑后仍然生效",
  );
  await page.locator('[data-tenant-feature="assistantSelection"]').uncheck();
  await page.locator('[data-business-action="save-tenant-features"]').click();
  await page.goto(scenarioUrl("assistants", "success"), {
    waitUntil: "domcontentloaded",
  });
  await page.locator('main[data-screen="assistants"][data-state="denied"]').waitFor();
  assert(
    (await page.locator("main[data-screen=assistants]").textContent()).includes(
      "当前会话不能访问此任务",
    ),
    "平台关闭租户功能后直接访问未拒绝",
  );

  await openScenario(page, "platform-tenant-detail", "success");
  await page.locator('[data-tenant-feature="assistantSelection"]').check();
  await page.locator('[data-business-action="save-tenant-features"]').click();
  await openScenario(page, "tenant-members", "success");
  await page
    .locator('[data-business-action="select-demo-member"][data-account-id="account-lin"]')
    .click();
  await page.goto(
    scenarioUrl("conversation", "success", false, "cv-20260902-01"),
    { waitUntil: "domcontentloaded" },
  );
  assert(
    (await page.locator('main[data-screen="login"]').count()) === 1 &&
      (await page.locator(".message-body").count()) === 0,
    "切换演示成员后旧登录仍读取会话正文",
  );
}

async function validateHealthAndRecovery(page) {
  await openScenario(page, "platform-health", "success");
  await page
    .locator('[data-incident-id="incident-file-processing"]')
    .click();
  await page.locator("#health-action").fill("保留活动索引并暂停新增处理批次");
  await page.locator('#health-action-form button[type="submit"]').click();
  assert(
    await page.getByText("保留活动索引并暂停新增处理批次", { exact: true }).isVisible(),
    "平台运行状态未记录处理结果",
  );
  await page.locator('[data-business-action="recheck-health"]').click();
  assert(
    await page.getByText("已恢复", { exact: true }).first().isVisible(),
    "平台运行状态重新检查未显示恢复结果",
  );

  await openScenario(page, "backup-recovery", "success");
  await page.locator('input[value="backup-verified"]').check();
  await page.locator('[data-business-action="advance-recovery"]').click();
  assert(await page.getByText("确认隔离演练", { exact: true }).isVisible(), "完整性检查后未进入确认阶段");
  await page.locator('[data-business-action="advance-recovery"]').click();
  await page.locator('[data-business-action="advance-recovery"]').click();
  await page.locator('[data-business-action="advance-recovery"]').click();
  assert(
    await page.getByText("校验报告：通过", { exact: true }).isVisible(),
    "隔离恢复演练未生成校验报告",
  );

}

async function validateRecoveryFailure(page) {
  await openScenario(page, "backup-recovery", "success");
  await page.locator('input[value="backup-check-fails"]').check();
  await page.locator('[data-business-action="advance-recovery"]').click();
  assert(
    await page.getByText("完整性检查未通过", { exact: true }).isVisible(),
    "恢复演练失败路径未阻止创建隔离环境",
  );
  await page.locator('[data-business-action="retry-recovery"]').click();
  assert(
    await page.getByText("开始完整性检查", { exact: true }).first().isVisible(),
    "恢复演练失败后不能重试",
  );
}

async function validateReviewerReachability(page) {
  await page.setViewportSize({ width: 1280, height: 720 });
  await openScenario(page, "backup-recovery", "success", true);
  await page.locator("#review-reset").click();
  await page.locator("#prototype-reviewer summary").click();
  assert(!(await page.locator("#prototype-reviewer details").evaluate((details) => details.open)), "审查器不能收起");
}

async function validateMemberDialog(page) {
  await openScenario(page, "tenant-members", "success");
  const trigger = page.locator('[data-business-action="open-add-member"]');
  await trigger.click();
  assert(await page.locator("#add-member-dialog").evaluate((dialog) => dialog.contains(document.activeElement)), "成员弹窗焦点未进入");
  await page.keyboard.press("Escape");
  assert(!(await page.locator("#add-member-dialog").evaluate((dialog) => dialog.open)), "Escape 未关闭成员弹窗");
  assert(await trigger.evaluate((button) => button === document.activeElement), "成员弹窗关闭后焦点未返回");
  await trigger.click();
  await page.locator("#member-account-select").selectOption("account-lin");
  await page.locator("[data-cancel-add-member]").click();
  assert(!(await page.locator("main").innerText()).includes("林澈（合成）"), "取消添加仍创建成员");
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.locator('main[data-screen="tenant-members"]').waitFor();
  assert(!(await page.locator("main").innerText()).includes("林澈（合成）"), "取消添加后刷新出现新成员");
}

async function validateQueryLogin(page) {
  await page.setViewportSize(viewports[0]);
  await openScenario(page, "login", "success", true);
  await page.locator("#review-reset").click();
  await openScenario(page, "login", "success");
  await page.locator('#login-form input[name="password"]').fill("demo-only");
  await page.locator('#login-form button[type="submit"]').click();
  await page.locator('main[data-screen="assistants"]').waitFor();
  assert(
    new URL(page.url()).searchParams.get("screen") === "assistants",
    "普通用户登录没有进入助手目录",
  );
}

async function validateQueryFlow(page) {
  await page.setViewportSize(viewports[0]);
  await loginQueryDemo(page);
  const search = page.locator("#assistant-search");
  await search.fill("员工");
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
  assert(
    new URL(page.url()).searchParams.has("conversationId") &&
      (await page.locator(".message").count()) === 0,
    "选择助手没有建立固定版本的空会话",
  );
  await page.locator('[data-conversation-id="cv-20260902-01"]').click();
  await page.locator('[data-sequence="41"]').waitFor();
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
  assert(
    (await page.locator('[data-sequence="39"]').count()) === 1 &&
      (await page.locator("[data-load-older]").count()) === 0,
    "按事件序号加载后没有显示更早消息或仍保留重复入口",
  );
  const input = page.locator("#question-input");
  await input.fill("未休 7 天如何处理？");
  await input.press("Enter");
  await page.locator('[data-run-state="COMPLETED"]').last().waitFor({ timeout: 7000 });
  assert(await input.isEnabled(), "流式完成后输入框未恢复");
  assert(
    (await page.locator('[data-run-state="COMPLETED"] .message-body').last().textContent()).includes(
      "其中 5 天可结转",
    ),
    "完成态没有显示由状态源保存的确定性回答",
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
  await page.waitForFunction(() => document.activeElement.matches("[data-close-history]"));
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

async function validateConversationPersistence(page) {
  await loginQueryDemo(page);
  await page.locator('[data-assistant-id="assistant-employee-service"]').click();
  await page.locator('main[data-screen="conversation"]').waitFor();
  const question = "浏览器验收：未休 7 天如何处理？";
  await page.locator("#question-input").fill(question);
  await page.locator("#question-input").press("Enter");
  await page.locator('[data-run-state="COMPLETED"]').waitFor({ timeout: 7000 });
  assert((await page.locator("main").innerText()).includes(question), "提交后没有显示本轮问题");
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.locator('main[data-screen="conversation"]').waitFor();
  assert((await page.locator("main").innerText()).includes(question), "刷新后刚提交的问题丢失，会话未形成可恢复闭环");
}

async function validateDeviceFlow(page) {
  await page.setViewportSize(viewports[2]);
  await runFlow(page, "device-start", [
    ["[data-primary-action]", "device-consent"],
    ["[data-primary-action]", "device-question"],
    ["[data-primary-action]", "device-answer"],
    ["[data-primary-action]", "device-question"],
  ], "设备问答导航");
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
    "platform-roles",
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

async function runStage(browserInstance, name, task) {
  // 每项使用独立合成存储，避免前一流程修改租户或成员污染后一验收。
  const context = await browserInstance.newContext();
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  attachBrowserErrorCapture(page, name);
  const failureStart = failures.length;
  const errorStart = browserErrors.length;
  process.stdout.write(`开始：${name}\n`);
  try {
    await task(page);
  } catch (error) {
    failures.push(`${name}: ${error.stack}`);
    try {
      await page.screenshot({
        path: path.join(screenshotDirectory, `failure-${stageResults.length + 1}.png`),
        fullPage: true,
        animations: "disabled",
      });
      screenshotCount += 1;
    } catch (captureError) {
      failures.push(`${name} 失败现场截图不可用：${captureError.message}`);
    }
  } finally {
    stageResults.push({
      name,
      status: failures.length === failureStart && browserErrors.length === errorStart ? "PASS" : "FAIL",
      failures: failures.slice(failureStart),
      browserErrors: browserErrors.slice(errorStart),
    });
    await context.close();
  }
  process.stdout.write(`结束：${name} · ${stageResults.at(-1).status}\n`);
}

async function main() {
  fs.mkdirSync(screenshotDirectory, { recursive: true });
  assert(pages.length === 68, `页面清单数量应为 68，实际为 ${pages.length}`);
  assert(new Set(screens).size === screens.length, "页面清单包含重复 screen");
  let browserInstance;
  try {
    browserInstance = await chromium.launch({ headless: true });
    for (const [name, task] of [
      ["审查器与导航图标", validateInventoryContract],
      ["全部页面状态与视口", validateScenarioMatrix],
      ["创建租户与管理导航", validateManagementFlows],
      ["成员与两级功能控制", validateTenantAndMemberControls],
      ["成员弹窗键盘与取消", validateMemberDialog],
      ["运行状态与隔离恢复", validateHealthAndRecovery],
      ["恢复检查失败与重试", validateRecoveryFailure],
      ["审查器收起可达性", validateReviewerReachability],
      ["普通用户登录", validateQueryLogin],
      ["问答示例与键盘", validateQueryFlow],
      ["会话刷新恢复", validateConversationPersistence],
      ["设备前置导航", validateDeviceFlow],
      ["页面截图", captureReviewSet],
      ["减少动态效果", () => validateReducedMotion(browserInstance)],
    ]) {
      await runStage(browserInstance, name, task);
    }
  } catch (error) {
    // 保留真实原因，不把产品超时、断言失败或脚本缺陷误报为授权问题。
    failures.push(error.stack);
  } finally {
    await browserInstance?.close();
    assert(browserErrors.length === 0, `浏览器错误：${browserErrors.join("；")}`);
    const result = {
      status: failures.length === 0 ? "PASS" : "FAIL",
      generatedAt: new Date().toISOString(),
      evidenceScope: "真实 DOM、布局、特定交互与导航；不是全部控件闭环或人工认可",
      pageCount: pages.length,
      stateCount: states.length,
      viewportCount: viewports.length,
      declaredScenarioCount: pages.length * states.length * viewports.length,
      completedScenarioCount,
      assertionCount,
      screenshotCount,
      stages: stageResults,
      browserErrors,
      failures,
    };
    fs.writeFileSync(path.join(artifactDirectory, "verification.json"), JSON.stringify(result, null, 2) + "\n");
    process.stdout.write(JSON.stringify(result, null, 2) + "\n");
    if (failures.length > 0) process.exitCode = 1;
  }
}

main().catch((error) => {
  process.stderr.write(`${error.stack}\n`);
  process.exitCode = 1;
});
