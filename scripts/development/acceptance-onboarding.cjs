/* WP-0017：原子开户只通过本机生产 UI/API 验证，不拦截网络、不重置数据、不输出秘密。 */
const fs = require("node:fs");
const crypto = require("node:crypto");
const { readConfiguration, request, requireCondition, SafeAssertionError, openLogin, fillLogin,
  submitLogin, assertWorkspace, assertIdentity, assertAnonymous } = require("./acceptance-login.cjs");

function readOnboardingConfiguration(environment = process.env) {
  requireCondition(!environment.SETUP, "开户验收不接受 SETUP，必须执行完整 UI 闭环");
  return readConfiguration({ ...environment, SETUP: "tenant" });
}

/** 使用非默认配额，避免后端忽略表单后仍被误判为成功。 */
function onboardingRequest(identity) {
  return { tenantCode: identity.tenantCode, displayName: "WP-0017 原子开户验收租户",
    administrator: { loginName: identity.loginName, password: identity.password },
    quotas: { knowledgeBases: 4, documentsPerKnowledgeBase: 1234, bytesPerKnowledgeBase: "12500000000",
      maxFileBytes: "75000000", maxTextBytes: "2097152" } };
}

function verifyOnboardingResult(tenant, quotas) {
  const reference = value => typeof value === "string" && /^[1-9][0-9]*$/.test(value);
  requireCondition(tenant.status === 201 && tenant.body?.status === "ACTIVE"
    && reference(tenant.body.tenantId) && reference(tenant.body.initialAdministratorSubjectId), "开户未原子产生可用租户和管理员");
  requireCondition(quotas.status === 200 && quotas.body?.version === 0, "配额无法真实读取或初始版本不正确");
  for (const [key, expected] of Object.entries(onboardingRequest({}).quotas)) {
    requireCondition(quotas.body[key] === expected, "非默认开户配额未精确保存");
  }
}

/** 子步骤名称仅使用固定任务文案；不能附加凭据、字段值或浏览器原始异常。 */
async function acceptanceStep(label, action) {
  try { return await action(); }
  catch (error) {
    throw new SafeAssertionError(`${label}：${error instanceof SafeAssertionError ? error.message : "定位、交互或等待未完成"}`);
  }
}
/** Fluent 的必填星号属于标签内容；只接受精确业务名称及可选星号，不做子串匹配。 */
function wizardLabelPattern(label) {
  return new RegExp(`^${label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*\\*?$`);
}
async function fillInformation(page, identity) {
  await acceptanceStep("填写组织名称", () => page.getByLabel(wizardLabelPattern("组织名称")).fill("WP-0017 原子开户验收租户"));
  await acceptanceStep("填写租户编码", () => page.getByLabel(wizardLabelPattern("租户编码")).fill(identity.tenantCode));
}
async function fillQuotas(page) {
  for (const [name, value] of [["知识库数量", "4"], ["每库文档数量", "1234"], ["每库内容容量", "12.5"], ["单文件大小", "75"], ["粘贴文本大小", "2"]]) {
    await acceptanceStep(`填写${name}`, () => page.getByLabel(wizardLabelPattern(name)).fill(value));
  }
}
async function fillAdministrator(page, identity, matching = true) {
  await acceptanceStep("填写管理员登录名", () => page.getByLabel(wizardLabelPattern("管理员登录名")).fill(identity.loginName));
  await acceptanceStep("填写初始密码", () => page.getByLabel(wizardLabelPattern("初始密码")).fill(identity.password));
  await acceptanceStep("再次输入密码", () => page.getByLabel(wizardLabelPattern("再次输入")).fill(matching ? identity.password : "SyntheticWrong9!"));
  await acceptanceStep("勾选开户确认", () => page.getByRole("checkbox", { name: "确认创建租户及其管理员账号", exact: true }).check());
}
async function assertNoStoredSecret(page, secret) {
  requireCondition(await page.evaluate(secret => ![location.href, JSON.stringify(history.state),
    JSON.stringify(localStorage), JSON.stringify(sessionStorage)].some(value => value.includes(secret)), secret), "密码进入 URL 或浏览器草稿存储");
}
/** 按 DOM 顺序和既定列数划分逻辑行，不能用错位后的坐标重新分组掩盖缺陷。 */
function verifyLogicalFieldRows(fields, columns, expectedCount, expectedColumns = columns) {
  requireCondition(Number.isInteger(columns) && columns > 0 && columns === expectedColumns, "开户表单列数不符合当前宽度");
  requireCondition(fields.length === expectedCount && expectedCount > 0, "开户字段缺失，不能进行空集合验收");
  for (const field of fields) {
    requireCondition(field.control && ["left", "right", "top", "bottom"].every(key => Number.isFinite(field.control[key])), "开户字段控件缺失");
    requireCondition(!field.control.clipped, "开户控件包装器发生裁切");
    requireCondition(field.label && !field.label.clipped && field.label.bottom <= field.control.top + 1, "开户标签缺失、裁切或遮挡控件");
    requireCondition(!field.hint || (!field.hint.clipped && field.hint.top >= field.control.bottom - 1), "开户帮助信息裁切或遮挡控件");
  }
  const aligned = numbers => Math.max(...numbers) - Math.min(...numbers) <= 1;
  for (let index = 0; index < fields.length; index += columns) {
    const row = fields.slice(index, index + columns);
    requireCondition(aligned(row.map(field => field.control.top)) && aligned(row.map(field => field.control.bottom))
      && aligned(row.map(field => field.label.top)), "同行表单控件或标签边界误差超过 1px");
    const hints = row.flatMap(field => field.hint ? [field.hint.top] : []);
    requireCondition(hints.length < 2 || aligned(hints), "同行帮助信息未对齐");
    requireCondition(row.every((field, column) => !column || field.control.left >= row[column - 1].control.right - 1), "同行字段发生重叠");
    const nextRow = fields.slice(index + columns, index + columns * 2);
    requireCondition(!nextRow.length || Math.min(...nextRow.map(field => field.label.top)) >= Math.max(...row.map(field => field.hint?.bottom ?? field.control.bottom)) - 1,
      "开户帮助信息与下一行发生重叠");
  }
}

function wizardKeyboardPlan(step) {
  const inputs = { 1: ["displayName", "tenantCode"],
    2: ["knowledgeBases", "documentsPerKnowledgeBase", "storageGb", "fileMb", "textMib"],
    3: ["loginName", "readonly", "password", "passwordConfirmation", "confirmed"] };
  requireCondition(Object.hasOwn(inputs, step), "未知开户步骤");
  return [...inputs[step].map(name => `input:${name}`), "button:取消", ...(step > 1 ? ["button:上一步"] : []), step === 3 ? "button:创建租户" : "button:下一步"];
}
function verifyKeyboardTraversal(expected, observed) {
  requireCondition(expected.length > 0 && expected.length === observed.length && expected.every((target, index) => target === observed[index]), "开户键盘遍历遗漏控件或顺序错误");
}
/** 原生 Tab 滚动后，焦点中心必须处于可见控件内，不能落在固定导航后方。 */
async function assertFocusedControlVisible(page) {
  requireCondition(await page.evaluate(() => {
    const element = document.activeElement;
    if (!element?.closest(".tenant-onboarding form")) return false;
    const bounds = element.getBoundingClientRect();
    const hit = document.elementFromPoint(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2);
    return bounds.top >= 0 && bounds.bottom <= innerHeight && element.contains(hit);
  }), "开户键盘焦点被固定导航遮挡或位于视口之外");
}

async function exerciseWizardKeyboard(page, step, advance = false) {
  const expected = wizardKeyboardPlan(step);
  const activeTarget = () => page.evaluate(() => {
    const element = document.activeElement;
    if (!element?.closest(".tenant-onboarding form")) return "outside";
    if (element.tagName === "INPUT") return `input:${element.name || (element.readOnly ? "readonly" : "unknown")}`;
    if (element.tagName === "BUTTON") return `button:${element.innerText.trim()}`;
    return "unknown";
  });
  // 只定位非交互标题作为起点，所有控件都必须通过真实 Tab 到达。
  await page.locator(".tenant-onboarding__heading h2").focus();
  const forward = [];
  for (const target of expected) {
    await page.keyboard.press("Tab");
    forward.push(await activeTarget());
    await assertFocusedControlVisible(page);
    if (target === "input:confirmed" && forward.at(-1) === target) {
      const checkbox = page.getByRole("checkbox", { name: "确认创建租户及其管理员账号", exact: true });
      const checked = await checkbox.isChecked();
      await page.keyboard.press("Space");
      requireCondition(await checkbox.isChecked() !== checked, "空格键无法切换开户确认框");
      await page.keyboard.press("Space");
      requireCondition(await checkbox.isChecked() === checked, "空格键无法恢复开户确认框");
    }
  }
  verifyKeyboardTraversal(expected, forward);
  const reverse = [];
  for (let index = expected.length - 2; index >= 0; index -= 1) {
    await page.keyboard.press("Shift+Tab");
    reverse.push(await activeTarget());
    await assertFocusedControlVisible(page);
  }
  verifyKeyboardTraversal(expected.slice(0, -1).reverse(), reverse);
  if (advance) {
    requireCondition(step < 3, "键盘布局验收不得创建租户");
    for (let index = 1; index < expected.length; index += 1) await page.keyboard.press("Tab");
    verifyKeyboardTraversal([expected.at(-1)], [await activeTarget()]);
    await page.keyboard.press("Enter");
  }
}

async function withTextScale(page, enlarged, action, scope = "main") {
  let originalStyles;
  const selector = `${scope} :is(h1,h2,h3,h4,p,li,label,a,span,input,button,strong,small,dt,dd,legend,textarea,select,summary,th,td,.fui-Badge,.fui-Field__hint,.fui-Field__validationMessage)`;
  // CSS 过渡结束才是实际字号；还原也必须等待，避免下一轮读取到放大中的值。
  const settle = sizes => page.waitForFunction(({ selector, sizes }) => {
    const elements = [...document.querySelectorAll(selector)].filter(element =>
      element.getClientRects().length > 0 && (!element.closest("details:not([open])")
        || element.tagName === "SUMMARY"));
    return elements.length === sizes.length && elements.every((element, index) =>
      Math.abs(parseFloat(getComputedStyle(element).fontSize) - sizes[index]) < 0.1);
  }, { selector, sizes }, { polling: "raf", timeout: 2000 });
  try {
    // 两种字号都先等待媒体查询布局，避免拿旧控件尺寸或旧标题字号校验新视口。
    await page.waitForFunction(async () => {
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
      return true;
    }, { phase: "viewport" }, { timeout: 2000 });
    if (enlarged) originalStyles = await page.evaluate(selector => {
      // Chromium 可能给关闭的 details 子节点保留布局盒；可见的 summary 仍须参与字号检查。
      const elements = [...document.querySelectorAll(selector)].filter(element =>
        element.getClientRects().length > 0 && (!element.closest("details:not([open])")
          || element.tagName === "SUMMARY"));
      // 先读取全部原始字号，再统一放大，避免子元素继承父元素放大后的字号而变成 400%。
      const originals = elements.map((element, index) => ({ index, style: element.getAttribute("style"),
        marker: element.getAttribute("data-wp0017-text-scale"), size: parseFloat(getComputedStyle(element).fontSize) }));
      elements.forEach((element, index) => {
        element.setAttribute("data-wp0017-text-scale", String(index));
        element.style.fontSize = `${originals[index].size * 2}px`;
      });
      return originals;
    }, selector);
    requireCondition(!enlarged || originalStyles.length > 0, "未找到待放大的页面文字，不能跳过 200% 检查");
    if (enlarged) await settle(originalStyles.map(original => original.size * 2));
    return await action();
  } finally {
    if (originalStyles) {
      const restored = await page.evaluate(originals => {
        let count = 0;
        for (const original of originals) {
          const element = document.querySelector(`[data-wp0017-text-scale="${original.index}"]`);
          if (!element) continue;
          if (original.style === null) element.removeAttribute("style"); else element.setAttribute("style", original.style);
          if (original.marker === null) element.removeAttribute("data-wp0017-text-scale"); else element.setAttribute("data-wp0017-text-scale", original.marker);
          count += 1;
        }
        return count;
      }, originalStyles);
      requireCondition(restored === originalStyles.length, "200% 文字检查未完整恢复原始样式");
      if (originalStyles.length) await settle(originalStyles.map(original => original.size));
    }
  }
}

/** 基线来自已人工验收原型，不从生产令牌反推期望值；正常字号下检查真实计算样式。 */
function verifyPrototypeStyle(style, width) {
  requireCondition(style.surface === "rgb(250, 249, 245)" && style.text === "rgb(20, 20, 19)", "原型面板或文字颜色不一致");
  requireCondition(style.bodySize === 16 && style.bodyFont.includes("PingFang SC") && style.headingCount === 1 && style.headingFont.includes("Georgia"), "原型正文或标题排版不一致");
  requireCondition(style.inputSize === 16 && style.inputRadius === 8, "原型输入控件字号或圆角不一致");
  requireCondition(style.buttonMinWidths.length > 0 && style.buttonMinWidths.every(value => value === 0), "原型按钮不应沿用框架固定最小宽度");
  const height = width <= 680 ? 44 : 36;
  requireCondition(style.heights.length > 0 && style.heights.every(value => Number.isFinite(value) && Math.abs(value - height) <= 1), "原型普通控件尺寸不一致");
}

/** 固定原型的响应式边界，移动导航与操作栏位置不能作为视觉例外。 */
function verifyPrototypeFrame(frame, width) {
  requireCondition(frame.mainPadding === (width <= 680 ? "20px 16px 40px" : "32px 32px 48px"), "页面内边距未承接原型");
  requireCondition(frame.cardPadding === (width <= 680 ? "16px" : "16px 20px"), "开户卡片内边距未承接原型");
  requireCondition(frame.sidebarPosition === "sticky" && frame.sidebarTop === "0px", "原型导航应固定在顶部滚动边界");
  requireCondition(frame.actionsPosition === (width <= 680 ? "fixed" : "static"), "移动操作栏未按原型固定");
  requireCondition(frame.headingClip === (width > 680 && width <= 1040 ? "inset(50%)" : "none"), "平板重复页头的可访问收起方式不一致");
  requireCondition(frame.navigationFontSize === (width <= 680 ? "10.4px" : "16px")
    && frame.navigationLineHeight === (width <= 680 ? "16.64px" : "25.6px"), "导航字号或行高被普通按钮样式覆盖");
  requireCondition(frame.iconWidth === (width <= 680 ? "20px" : "24px") && frame.iconHeight === "24px"
    && frame.iconMarginRight === "0px", "导航图标槽尺寸或边距未承接原型");
}

async function assertPrototypeStyle(page, width) {
  const style = await page.evaluate(() => {
    const panel = document.querySelector(".tenant-onboarding");
    const heading = document.querySelector("h1#page-title");
    const input = panel.querySelector(".fui-Input");
    return { surface: getComputedStyle(document.querySelector(".product-shell")).backgroundColor, text: getComputedStyle(panel).color,
      bodySize: parseFloat(getComputedStyle(panel).fontSize), bodyFont: getComputedStyle(panel).fontFamily,
      headingCount: document.querySelectorAll("main h1#page-title").length, headingFont: heading ? getComputedStyle(heading).fontFamily : "",
      inputSize: parseFloat(getComputedStyle(input.querySelector("input")).fontSize), inputRadius: parseFloat(getComputedStyle(input).borderRadius),
      heights: [...panel.querySelectorAll(".fui-Input,.tenant-onboarding__actions button")].map(element => element.getBoundingClientRect().height),
      buttonMinWidths: [...panel.querySelectorAll(".tenant-onboarding__actions button")].map(element => parseFloat(getComputedStyle(element).minWidth)) };
  });
  verifyPrototypeStyle(style, width);
  verifyPrototypeFrame(await page.evaluate(() => {
    const style = selector => getComputedStyle(document.querySelector(selector));
    return { mainPadding: style(".product-main").padding, cardPadding: style(".tenant-onboarding__card").padding,
      sidebarPosition: style(".product-sidebar").position, sidebarTop: style(".product-sidebar").top,
      actionsPosition: style(".tenant-onboarding__actions").position, headingClip: style(".page-heading").clipPath,
      navigationFontSize: style(".product-navigation button").fontSize, navigationLineHeight: style(".product-navigation button").lineHeight,
      iconWidth: style(".product-navigation .fui-Button__icon").width, iconHeight: style(".product-navigation .fui-Button__icon").height,
      iconMarginRight: style(".product-navigation .fui-Button__icon").marginRight };
  }), width);
}

/** 结构基线来自固定原型：桌面管理员表单左置，摘要与确认右置；移动端顺序堆叠。 */
async function assertAdministratorSummaryLayout(page, width, enlarged = false) {
  const boxes = await page.evaluate(() => {
    const box = selector => {
      const element = document.querySelector(selector);
      if (!element) return null;
      const { left, right, top, bottom } = element.getBoundingClientRect();
      return { left, right, top, bottom };
    };
    return { fields: box(".tenant-onboarding__fields"), summary: box(".tenant-onboarding__summary"),
      summaryColumns: getComputedStyle(document.querySelector(".tenant-onboarding__summary")).gridTemplateColumns.split(/\s+/).length,
      consent: box(".tenant-onboarding .fui-Checkbox"),
      readonlySize: parseFloat(getComputedStyle(document.querySelector(".tenant-onboarding input[readonly]")).fontSize),
      readonlyLineHeight: parseFloat(getComputedStyle(document.querySelector(".tenant-onboarding input[readonly]")).lineHeight),
      summarySizes: [...document.querySelectorAll(".tenant-onboarding__summary small")].map(element => parseFloat(getComputedStyle(element).fontSize)),
      stepColor: getComputedStyle(document.querySelector(".tenant-onboarding__steps li[aria-current] > span")).color,
      submitWidth: document.querySelector(".tenant-onboarding__actions button[type=submit]").getBoundingClientRect().width };
  });
  const scale = enlarged ? 2 : 1;
  requireCondition(boxes.readonlySize === 16 * scale && boxes.readonlyLineHeight === 24 * scale
    && boxes.summarySizes.length === 2 && boxes.summarySizes.every(size => size === 14 * scale),
    "管理员只读值或摘要辅助字号未承接原型");
  requireCondition(boxes.stepColor === "rgb(255, 255, 255)", "当前步骤数字颜色未承接原型");
  if (!enlarged && width > 680) requireCondition(Math.abs(boxes.submitWidth - 82) <= 1, "创建租户按钮宽度未承接原型");
  const { fields, summary, consent } = boxes;
  requireCondition(boxes.summaryColumns === (width <= 680 ? 2 : 1), "管理员确认摘要的响应式列数未承接原型");
  requireCondition(fields && summary && consent, "管理员表单、摘要或确认区缺失");
  const consentColumn = width > 680 ? summary : fields;
  requireCondition(Math.abs((consent.right - consent.left) - (consentColumn.right - consentColumn.left)) <= 1,
    "确认控件未使用原型分区的完整宽度");
  if (width > 680) {
    requireCondition(summary.left >= fields.right - 1 && Math.abs(summary.top - fields.top) <= 1
      && consent.left >= fields.right - 1 && consent.top >= summary.bottom - 1,
    "管理员步骤未承接原型左表单、右摘要与确认布局");
  } else {
    requireCondition(summary.bottom <= fields.top + 1 && consent.top >= fields.bottom - 1,
      "移动端管理员摘要、表单与确认区顺序不正确");
  }
}

/** 概览只核对本片已有事实与真实入口，不要求未交付的独立设置导航。 */
async function assertOverviewStructure(page) {
  requireCondition(await page.locator("#tenant-quota-title,.tenant-overview__facts").count() === 0,
    "概览仍包含固定原型没有的额外资源配额区");
  requireCondition(await page.locator("h1#page-title").innerText() === "租户概览", "概览页头仍未承接原型任务名称");
  requireCondition(await page.locator(".tenant-overview__identity a").count() === 1,
    "管理员登录入口未归属概览基本信息区");
  requireCondition(await page.locator(".tenant-overview__safety h3").innerText() === "安全保障",
    "概览安全保障区缺少原型分区标题");
  const frame = await page.evaluate(() => {
    const element = selector => document.querySelector(selector);
    const columns = selector => getComputedStyle(element(selector)).gridTemplateColumns.split(/\s+/).length;
    return { width: innerWidth, columns: columns(".tenant-overview__summary-grid"), factsColumns: columns(".tenant-overview__identity-facts"),
      headingWidth: element(".page-heading").getBoundingClientRect().width,
      returnWidth: element(".page-heading > button").getBoundingClientRect().width,
      entryHeight: element(".tenant-overview__entry > a").getBoundingClientRect().height };
  });
  requireCondition(frame.columns === (frame.width <= 1040 ? 1 : 2), "概览与安全保障的响应式分栏未承接原型");
  requireCondition(frame.factsColumns === (frame.width <= 680 ? 1 : 2), "概览基本信息的移动单列未承接原型");
  requireCondition(frame.width > 680 || (Math.abs(frame.headingWidth - frame.returnWidth) <= 1 && frame.entryHeight >= 44),
    "概览移动返回按钮宽度或登录入口触控高度未承接原型");
}

async function assertWizardLayout(page, width, step, enlarged = false) {
  return withTextScale(page, enlarged, async () => {
    if (!enlarged) await assertPrototypeStyle(page, width);
    const result = await page.evaluate(() => {
      // 已单独断言平板页头的视觉收起合同；其辅助技术文本不按可见控件测量裁切。
      const visible = element => element && getComputedStyle(element.closest(".page-heading") || element).clipPath !== "inset(50%)"
        && element.getBoundingClientRect().width > 0 && element.getBoundingClientRect().height > 0;
      const clipped = element => element.scrollHeight > element.clientHeight + 1 || element.scrollWidth > element.clientWidth + 1;
      const geometry = element => {
        if (!visible(element)) return null;
        const { left, right, top, bottom } = element.getBoundingClientRect();
        return { left, right, top, bottom, clipped: clipped(element) };
      };
      const grid = document.querySelector(".tenant-onboarding__fields");
      const controls = [...document.querySelectorAll('.tenant-onboarding input:not([type="checkbox"]),.tenant-onboarding button')].filter(visible);
      return { overflow: document.documentElement.scrollWidth > innerWidth + 1,
        controlCount: controls.length,
        minimumHeight: Math.min(...controls.map(element => (element.closest(".fui-Input") || element).getBoundingClientRect().height)),
        columns: grid ? getComputedStyle(grid).gridTemplateColumns.split(/\s+/).length : 0,
        fields: [...(grid?.querySelectorAll(".fui-Field") || [])].map(field => ({ control: geometry(field.querySelector(".fui-Input")),
          label: geometry(field.querySelector("label")), hint: geometry(field.querySelector(".fui-Field__hint")) })),
        clipped: [...document.querySelectorAll("main button,main h1,main h2,main .page-heading > *, .tenant-onboarding__steps li > span")].filter(visible).some(clipped),
        moving: matchMedia("(prefers-reduced-motion: reduce)").matches && document.getAnimations()
          .some(animation => animation.playState === "running" && animation.effect?.getTiming().duration > 1) };
    });
    requireCondition(!result.overflow && !result.clipped, "开户页面溢出或文字裁切");
    requireCondition(result.controlCount > 0 && result.minimumHeight >= (width === 390 ? 44 : 36), "开户控件点击区域不足");
    verifyLogicalFieldRows(result.fields, result.columns, { 1: 2, 2: 5, 3: 4 }[step], width <= 680 ? step === 2 ? 2 : 1 : step === 2 ? 3 : 2);
    requireCondition(result.fields.filter(field => field.hint).length === { 1: 2, 2: 5, 3: 1 }[step], "开户帮助信息缺失，不能跳过帮助文字验收");
    requireCondition(!result.moving, "开户页 reduced-motion 未生效");
    if (step === 3) await assertAdministratorSummaryLayout(page, width, enlarged);
  });
}

function verifyOverviewBadge({ cardWidth, badgeWidth, contentWidth, insets, badgeHeight, contentHeight, verticalInsets }) {
  requireCondition([cardWidth, badgeWidth, contentWidth, badgeHeight, contentHeight].every(value => Number.isFinite(value) && value > 0)
    && [insets, verticalInsets].every(value => Number.isFinite(value) && value >= 0), "租户状态徽标或内容缺失");
  requireCondition(badgeWidth <= contentWidth + insets + 2,
    `租户状态徽标被拉宽：实际 ${Math.round(badgeWidth)}px，内容含内边距 ${Math.round(contentWidth + insets)}px`);
  requireCondition(badgeWidth + 1 >= contentWidth + insets, "租户状态徽标内容被横向裁切");
  requireCondition(badgeWidth < cardWidth / 2, "租户状态徽标占据半张卡片");
  requireCondition(badgeHeight + 1 >= contentHeight + verticalInsets, "租户状态徽标文字被固定高度裁切");
}

async function assertOverviewBadgeLayout(page, enlarged = false) {
  const originalFontSize = await page.locator(".tenant-overview__identity-heading > .fui-Badge").evaluate(badge => parseFloat(getComputedStyle(badge).fontSize));
  const originalSafetySize = await page.locator(".tenant-overview__safety li").first().evaluate(item => parseFloat(getComputedStyle(item).fontSize));
  return withTextScale(page, enlarged, async () => {
    await assertOverviewStructure(page);
    const metrics = await page.evaluate(() => {
      const card = document.querySelector(".tenant-overview__identity");
      const badges = card?.querySelectorAll(".tenant-overview__identity-heading > .fui-Badge");
      if (badges?.length !== 1) return { cardWidth: 0, badgeWidth: 0, contentWidth: 0, insets: 0 };
      const badge = badges[0];
      const style = getComputedStyle(badge);
      const range = document.createRange();
      range.selectNodeContents(badge);
      const badgeRect = badge.getBoundingClientRect();
      const contentRect = range.getBoundingClientRect();
      return { cardWidth: card.getBoundingClientRect().width, badgeWidth: badgeRect.width, badgeHeight: badgeRect.height,
        safetySizes: [...document.querySelectorAll(".tenant-overview__safety li")].map(item => parseFloat(getComputedStyle(item).fontSize)),
        overflow: document.documentElement.scrollWidth > innerWidth + 1,
        clipped: [...document.querySelectorAll("main h1,main h2,main dt,main dd,main li,main button,main a")]
          .some(item => item.scrollWidth > item.clientWidth + 1 || item.scrollHeight > item.clientHeight + 1),
        contentWidth: contentRect.width, contentHeight: contentRect.height, fontSize: parseFloat(style.fontSize),
        insets: [style.paddingLeft, style.paddingRight, style.borderLeftWidth, style.borderRightWidth].reduce((sum, value) => sum + parseFloat(value), 0),
        verticalInsets: [style.paddingTop, style.paddingBottom, style.borderTopWidth, style.borderBottomWidth].reduce((sum, value) => sum + parseFloat(value), 0) };
    });
    requireCondition(Math.abs(metrics.fontSize - originalFontSize * (enlarged ? 2 : 1)) < 0.1, "租户状态徽标未实际应用目标文字倍率");
    requireCondition(metrics.safetySizes.length === 3 && metrics.safetySizes.every(size => Math.abs(size - originalSafetySize * (enlarged ? 2 : 1)) < 0.1),
      "概览安全保障文字未实际应用目标文字倍率");
    requireCondition(!metrics.overflow && !metrics.clipped, "概览页面溢出或文字裁切");
    verifyOverviewBadge(metrics);
    return metrics;
  });
}

async function run(configuration) {
  let browser;
  let stage = "启动受控浏览器";
  let authenticated = false;
  let page;
  let descriptor;
  const suffix = crypto.randomBytes(6).toString("hex");
  const credentials = { platform: configuration.credentials.platform,
    tenant: { tenantCode: `wp17-${suffix}`, loginName: `wp17-${suffix}`, password: `Aa9!${crypto.randomBytes(24).toString("base64url")}` },
    setup: { status: "STARTED" } };
  const persist = () => { fs.ftruncateSync(descriptor, 0); fs.writeSync(descriptor, JSON.stringify(credentials, null, 2), 0, "utf8"); fs.fsyncSync(descriptor); };
  async function scenario(name, action) {
    stage = name;
    const started = Date.now();
    await action();
    const navigationProfile = await page.evaluate(() => {
      const navigation = performance.getEntriesByType("navigation")[0];
      return navigation ? { domContentLoadedMs: navigation.domContentLoadedEventEnd,
        loadMs: navigation.loadEventEnd, transferBytes: navigation.transferSize } : null;
    });
    process.stdout.write(`${JSON.stringify({ assertion: name, result: "通过", elapsedMs: Date.now() - started, navigationProfile })}\n`);
  }
  try {
    requireCondition(require("playwright/package.json").version === "1.62.1", "需要现有锁定 Playwright 1.62.1 运行时");
    descriptor = fs.openSync(configuration.outputFile, "wx", 0o600);
    persist();
    browser = await require("playwright").chromium.launch({ channel: "chrome", headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
    page = await context.newPage();
    page.setDefaultTimeout(15_000);
    let pageErrors = 0;
    page.on("pageerror", () => { pageErrors += 1; });
    await scenario("真实平台身份进入开户", async () => {
      await openLogin(page, configuration, "platform");
      await fillLogin(page, credentials.platform, "platform");
      await submitLogin(page);
      await assertWorkspace(page, "platform");
      await assertIdentity(page, "platform");
      authenticated = true;
      await acceptanceStep("从导航进入租户管理", () => page.getByRole("button", { name: "租户管理", exact: true }).click());
      await acceptanceStep("从租户目录进入建立租户", () => page.getByRole("button", { name: "建立租户", exact: true }).click());
      await acceptanceStep("确认开户入口落点", () => page.waitForURL(url => url.pathname === "/platform/tenants/new" && url.searchParams.get("step") === "1"));
    });
    for (const width of [390, 1024, 1440]) {
      await scenario(`${width} 三步、回退、刷新、密码清除与取消`, async () => {
        await page.setViewportSize({ width, height: width === 390 ? 844 : 960 });
        await acceptanceStep("进入开户第一页", () => page.goto(`${configuration.baseUrl}/platform/tenants/new?step=1`, { waitUntil: "networkidle" }));
        let creates = 0;
        const countCreate = outgoing => { if (outgoing.method() === "POST" && /\/api\/v1\/platform\/(tenants|accounts)$/.test(outgoing.url())) creates += 1; };
        page.on("request", countCreate);
        try {
          await fillInformation(page, credentials.tenant);
          await acceptanceStep("第一步正常字号布局", () => assertWizardLayout(page, width, 1));
          await acceptanceStep("第一步 200% 文字布局", () => assertWizardLayout(page, width, 1, true));
          await acceptanceStep("第一步 200% 文字键盘可见性", () => withTextScale(page, true, () => exerciseWizardKeyboard(page, 1)));
          await acceptanceStep("第一步键盘遍历并进入配额", () => exerciseWizardKeyboard(page, 1, true));
          await fillQuotas(page);
          await acceptanceStep("第二步正常字号布局", () => assertWizardLayout(page, width, 2));
          await acceptanceStep("第二步 200% 文字布局", () => assertWizardLayout(page, width, 2, true));
          await acceptanceStep("第二步 200% 文字键盘可见性", () => withTextScale(page, true, () => exerciseWizardKeyboard(page, 2)));
          await acceptanceStep("第二步键盘遍历并进入管理员", () => exerciseWizardKeyboard(page, 2, true));
          await fillAdministrator(page, credentials.tenant);
          await acceptanceStep("第三步正常字号布局", () => assertWizardLayout(page, width, 3));
          await acceptanceStep("第三步 200% 文字布局", () => assertWizardLayout(page, width, 3, true));
          await acceptanceStep("第三步 200% 文字键盘可见性", () => withTextScale(page, true, () => exerciseWizardKeyboard(page, 3)));
          await acceptanceStep("第三步键盘与空格确认", () => exerciseWizardKeyboard(page, 3));
          await assertNoStoredSecret(page, credentials.tenant.password);
          await acceptanceStep("刷新管理员步骤", () => page.reload({ waitUntil: "networkidle" }));
          requireCondition(await page.getByLabel(wizardLabelPattern("初始密码")).inputValue() === "", "刷新后仍保留密码");
          await acceptanceStep("退回配额步骤", () => page.getByRole("button", { name: "上一步", exact: true }).click());
          requireCondition(await page.getByLabel(wizardLabelPattern("每库内容容量")).inputValue() === "12.5", "刷新后非秘密配额草稿丢失");
          await acceptanceStep("退回信息步骤", () => page.getByRole("button", { name: "上一步", exact: true }).click());
          requireCondition(await page.getByLabel(wizardLabelPattern("租户编码")).inputValue() === credentials.tenant.tenantCode, "回退后租户信息丢失");
          await acceptanceStep("取消开户", () => page.getByRole("button", { name: "取消", exact: true }).click());
          requireCondition(creates === 0, "进入步骤或取消时提前创建了账户或租户");
        } finally { page.off("request", countCreate); }
      });
    }
    await page.setViewportSize({ width: 1440, height: 960 });
    await scenario("一次提交原子开户并刷新读取非默认配额", async () => {
      await page.goto(`${configuration.baseUrl}/platform/tenants/new?step=1`, { waitUntil: "networkidle" });
      requireCondition(await page.getByLabel(wizardLabelPattern("组织名称")).inputValue() === "", "取消未清除开户草稿");
      await fillInformation(page, credentials.tenant);
      await page.getByRole("button", { name: "下一步", exact: true }).click();
      await fillQuotas(page);
      await page.getByRole("button", { name: "下一步", exact: true }).click();
      await fillAdministrator(page, credentials.tenant, false);
      let creates = 0;
      const countCreate = outgoing => { if (outgoing.method() === "POST" && /\/api\/v1\/platform\/(tenants|accounts)$/.test(outgoing.url())) creates += 1; };
      page.on("request", countCreate);
      try {
        const create = page.getByRole("button", { name: "创建租户", exact: true });
        if (await create.isEnabled()) await create.click();
        requireCondition(creates === 0, "密码不一致仍发出开户请求");
        await fillAdministrator(page, credentials.tenant);
        const responsePromise = page.waitForResponse(response => /\/api\/v1\/platform\/tenants$/.test(response.url()) && response.request().method() === "POST");
        await create.click();
        const response = await responsePromise;
        requireCondition(response.status() === 201, "原子开户接口未返回 201");
        const body = await response.json();
        credentials.setup = { status: "CREATED", tenantId: body.tenantId, subjectId: body.initialAdministratorSubjectId };
        persist();
        requireCondition(!JSON.stringify(body).includes(credentials.tenant.password) && !/password|hash|token/i.test(JSON.stringify(body)), "开户响应包含秘密字段");
        await page.waitForURL(url => url.pathname === `/platform/tenants/${body.tenantId}`);
        await page.getByRole("heading", { name: "租户概览", exact: true }).waitFor();
        await page.reload({ waitUntil: "networkidle" });
        await page.getByRole("heading", { name: "租户概览", exact: true }).waitFor();
        await page.getByRole("heading", { name: onboardingRequest(credentials.tenant).displayName, exact: true }).waitFor();
        requireCondition(await page.locator(".tenant-overview__identity-facts > div").filter({ has: page.locator("dt", { hasText: "租户编码" }) }).locator("dd").textContent() === credentials.tenant.tenantCode, "刷新详情未显示当前租户编码");
        await assertOverviewStructure(page);
        const quotas = await request(page, `/platform/tenants/${body.tenantId}/quotas`);
        verifyOnboardingResult({ status: response.status(), body }, quotas);
        requireCondition(creates === 1, "开户没有保持单次提交边界");
        await assertNoStoredSecret(page, credentials.tenant.password);
        credentials.setup.status = "ACTIVE";
        persist();
      } finally { page.off("request", countCreate); }
    });
    for (const width of [390, 1024, 1440]) {
      await scenario(`${width} 租户详情徽标正常与 200% 文字按内容展示`, async () => {
        await page.setViewportSize({ width, height: width === 390 ? 844 : 960 });
        await acceptanceStep("详情徽标正常字号", () => assertOverviewBadgeLayout(page));
        await acceptanceStep("详情徽标 200% 文字", () => assertOverviewBadgeLayout(page, true));
      });
    }
    await scenario("重复编码、重复登录名、旧请求和缺少 CSRF 失败关闭", async () => {
      const input = onboardingRequest(credentials.tenant);
      requireCondition((await request(page, "/platform/tenants", "POST", { ...input, administrator: { ...input.administrator, loginName: `unused-${suffix}` } })).status === 409, "重复租户编码未拒绝");
      requireCondition((await request(page, "/platform/tenants", "POST", { ...input, tenantCode: `unused-${suffix}` })).status === 409, "重复全局登录名未拒绝");
      requireCondition((await request(page, "/platform/tenants", "POST", { tenantCode: `old-${suffix}`, displayName: "旧合同反例", initialAdministratorSubjectId: credentials.setup.subjectId })).status === 400, "旧候选主体请求仍可使用");
      requireCondition((await request(page, "/platform/tenants", "POST", input, false)).status === 403, "缺少 CSRF 仍可开户");
    });
    await scenario("重复编码错误返回信息步骤并定位字段，取消清除失败草稿", async () => {
      await page.goto(`${configuration.baseUrl}/platform/tenants/new?step=1`, { waitUntil: "networkidle" });
      await fillInformation(page, credentials.tenant);
      await page.getByRole("button", { name: "下一步", exact: true }).click();
      await fillQuotas(page);
      await page.getByRole("button", { name: "下一步", exact: true }).click();
      await fillAdministrator(page, { ...credentials.tenant, loginName: `unused-${suffix}` });
      const pending = page.waitForResponse(response => /\/api\/v1\/platform\/tenants$/.test(response.url()) && response.request().method() === "POST");
      await page.getByRole("button", { name: "创建租户", exact: true }).click();
      requireCondition((await pending).status() === 409, "重复编码 UI 提交未返回冲突");
      const field = page.getByLabel(wizardLabelPattern("租户编码"));
      await field.waitFor();
      requireCondition(await field.inputValue() === credentials.tenant.tenantCode, "失败丢失非秘密输入");
      requireCondition(await field.evaluate(element => element === document.activeElement), "重复编码错误未定位可修正输入");
      requireCondition(await page.locator('input[type="password"]').count() === 0, "失败回跳仍保留密码字段");
      await assertNoStoredSecret(page, credentials.tenant.password);
      await page.getByRole("button", { name: "取消", exact: true }).click();
      await page.goto(`${configuration.baseUrl}/platform/tenants/${credentials.setup.tenantId}`, { waitUntil: "networkidle" });
      await page.getByRole("heading", { name: "租户概览", exact: true }).waitFor();
    });
    await scenario("成功入口不切换身份，新管理员从租户入口登录且无平台权限", async () => {
      const link = page.getByRole("link", { name: "租户管理员登录入口", exact: true });
      requireCondition((await link.getAttribute("href")) === `/tenant/login?tenantCode=${credentials.tenant.tenantCode}`, "租户登录入口未携带当前编码");
      await assertIdentity(page, "platform");
      requireCondition((await request(page, "/auth/browser-session", "DELETE")).status === 204, "平台退出失败");
      authenticated = false;
      await assertAnonymous(page);
      await openLogin(page, configuration, "tenant");
      await fillLogin(page, credentials.tenant, "tenant");
      await submitLogin(page);
      await assertWorkspace(page, "tenant");
      await assertIdentity(page, "tenant");
      authenticated = true;
      requireCondition([401,403].includes((await request(page, "/platform/tenants", "POST", onboardingRequest(credentials.tenant))).status), "新租户管理员能够调用平台开户");
      requireCondition([401,403,404].includes((await request(page, `/platform/tenants/${credentials.setup.tenantId}/quotas`)).status), "新管理员能够读取平台租户配额接口");
      await assertIdentity(page, "tenant");
      requireCondition(pageErrors === 0, "真实页面出现未处理异常");
    });
    process.stdout.write(`${JSON.stringify({ slice: "WP-0017/onboarding", result: "通过", ...credentials.setup, retained: "仅保留本次合成租户和管理员，不创建普通会员" })}\n`);
  } catch (error) {
    throw new Error(`开户验收失败：${stage}${error instanceof SafeAssertionError ? `；${error.message}` : ""}；已保留测试对象记录，未输出秘密。`);
  } finally {
    if (descriptor !== undefined) fs.closeSync(descriptor);
    let logoutFailed = false;
    if (authenticated && page) {
      try { logoutFailed = (await request(page, "/auth/browser-session", "DELETE")).status !== 204; } catch { logoutFailed = true; }
    }
    try { await browser?.close(); } catch { logoutFailed = true; }
    requireCondition(!logoutFailed, "验收结束后的会话或浏览器清理失败");
  }
}

module.exports = { assertFocusedControlVisible, exerciseWizardKeyboard, readOnboardingConfiguration, onboardingRequest, verifyOnboardingResult,
  verifyLogicalFieldRows, wizardKeyboardPlan, verifyKeyboardTraversal, acceptanceStep, wizardLabelPattern,
  verifyPrototypeFrame, verifyOverviewBadge, withTextScale, assertOverviewBadgeLayout, verifyPrototypeStyle, assertPrototypeStyle, assertAdministratorSummaryLayout, assertOverviewStructure };
if (require.main === module) {
  try { run(readOnboardingConfiguration()).catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }); }
  catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
