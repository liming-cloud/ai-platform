const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { readOnboardingConfiguration, onboardingRequest, verifyOnboardingResult,
  verifyLogicalFieldRows, wizardKeyboardPlan, verifyKeyboardTraversal, acceptanceStep, wizardLabelPattern,
  verifyOverviewBadge, withTextScale, verifyPrototypeStyle } = require("../development/acceptance-onboarding.cjs");
const { SafeAssertionError } = require("../development/acceptance-login.cjs");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "wp0017-onboarding-test-"));
after(() => fs.rmSync(directory, { recursive: true, force: true }));
const credentialsFile = path.join(directory, "credentials.json");
fs.writeFileSync(credentialsFile, JSON.stringify({ platform: { loginName: "synthetic-platform", password: "synthetic-only-password" } }), { mode: 0o600 });
const environment = () => ({ FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: credentialsFile,
  FOUNDATION_ACCEPTANCE_OUTPUT_FILE: path.join(directory, "new-created.json") });

test("原型一致性拒绝旧白底、旧字体及仅字号名正确的控件", () => {
  const current = { surface: "rgb(250, 249, 245)", text: "rgb(20, 20, 19)", bodySize: 16, bodyFont: '"PingFang SC", sans-serif',
    headingCount: 1, headingFont: 'Georgia, "Songti SC", serif', inputSize: 16, inputRadius: 8, heights: [36, 36, 36], buttonMinWidths: [0, 0] };
  assert.doesNotThrow(() => verifyPrototypeStyle(current, 1440));
  assert.doesNotThrow(() => verifyPrototypeStyle({ ...current, heights: [44, 44, 44] }, 390));
  for (const defect of [{ surface: "rgb(255, 255, 255)" }, { bodySize: 14 }, { headingCount: 0 }, { inputSize: 14 }, { inputRadius: 7 }, { heights: [36, 56] }, { buttonMinWidths: [96, 96] }]) {
    assert.throws(() => verifyPrototypeStyle({ ...current, ...defect }, 1440), /原型/);
  }
  assert.throws(() => verifyPrototypeStyle(current, 390), /原型/);
});

test("开户只需平台凭据，但必须显式指定新的独占凭据输出文件", () => {
  assert.equal(readOnboardingConfiguration(environment()).setup, true);
  assert.throws(() => readOnboardingConfiguration({ FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE: credentialsFile }), /新凭据输出文件/);
  assert.throws(() => readOnboardingConfiguration({ ...environment(), FOUNDATION_ACCEPTANCE_OUTPUT_FILE: credentialsFile }), /新凭据输出文件/);
});
test("开户验收不得通过 SETUP 绕过真实 UI，也不接受远端目标", () => {
  assert.throws(() => readOnboardingConfiguration({ ...environment(), SETUP: "tenant" }), /不接受 SETUP/);
  assert.throws(() => readOnboardingConfiguration({ ...environment(), FOUNDATION_ACCEPTANCE_BASE_URL: "https://example.com" }), /本机 13000/);
});
test("开户请求使用单次新凭据和精确字节配额，不再引用已建主体", () => {
  const request = onboardingRequest({ tenantCode: "new-tenant", loginName: "new-admin", password: "synthetic-only-password" });
  assert.deepEqual(Object.keys(request).sort(), ["administrator", "displayName", "quotas", "tenantCode"]);
  assert.equal(request.administrator.loginName, "new-admin");
  assert.deepEqual(request.quotas, { knowledgeBases: 4, documentsPerKnowledgeBase: 1234,
    bytesPerKnowledgeBase: "12500000000", maxFileBytes: "75000000", maxTextBytes: "2097152" });
});
test("不能把准备中、精度漂移或缺失字段当成原子开户完成", () => {
  const tenant = { tenantId: "123456789012345678", initialAdministratorSubjectId: "123456789012345679", status: "ACTIVE" };
  const quotas = { ...onboardingRequest({}).quotas, version: 0 };
  assert.doesNotThrow(() => verifyOnboardingResult({ status: 201, body: tenant }, { status: 200, body: quotas }));
  for (const changed of [{ ...tenant, status: "PROVISIONING" }, { ...tenant, tenantId: 123456789012345678 }, { ...tenant, initialAdministratorSubjectId: undefined }]) {
    assert.throws(() => verifyOnboardingResult({ status: 201, body: changed }, { status: 200, body: quotas }));
  }
  assert.throws(() => verifyOnboardingResult({ status: 201, body: tenant }, { status: 200, body: { ...quotas, bytesPerKnowledgeBase: 12500000000 } }));
  assert.throws(() => verifyOnboardingResult({ status: 201, body: tenant }, { status: 200, body: { ...quotas, knowledgeBases: 3 } }));
});

const field = (left, top = 100, hint = true) => ({
  control: { left, right: left + 180, top: top + 28, bottom: top + 64 },
  label: { top, bottom: top + 20, clipped: false },
  hint: hint ? { top: top + 70, bottom: top + 90, clipped: false } : null,
});
test("逻辑同行整体错位不能通过拆成两个单元素组隐藏", () => {
  assert.doesNotThrow(() => verifyLogicalFieldRows([field(0), field(200)], 2, 2));
  assert.throws(() => verifyLogicalFieldRows([field(0), field(200, 106)], 2, 2), /同行/);
});
test("缺失字段、错误列数、帮助信息遮挡或裁切不得假绿", () => {
  assert.throws(() => verifyLogicalFieldRows([], 2, 2), /字段/);
  assert.throws(() => verifyLogicalFieldRows([field(0)], 2, 2), /字段/);
  assert.throws(() => verifyLogicalFieldRows([field(0), field(200)], 1, 2, 2), /列数/);
  assert.throws(() => verifyLogicalFieldRows([{ ...field(0), hint: { top: 155, bottom: 180, clipped: false } }], 1, 1), /帮助/);
  assert.throws(() => verifyLogicalFieldRows([{ ...field(0), label: { top: 100, bottom: 120, clipped: true } }], 1, 1), /标签/);
  assert.throws(() => verifyLogicalFieldRows([{ ...field(0), control: { ...field(0).control, clipped: true } }], 1, 1), /控件.*裁切/);
});
test("移动端单列与桌面多行按显式逻辑结构校验", () => {
  assert.doesNotThrow(() => verifyLogicalFieldRows([field(0), field(0, 220)], 1, 2));
  assert.doesNotThrow(() => verifyLogicalFieldRows([field(0), field(200), field(400), field(0, 220), field(200, 220)], 3, 5));
});
test("三步键盘计划覆盖所有输入、确认、取消和提交", () => {
  assert.deepEqual(wizardKeyboardPlan(1), ["input:displayName", "input:tenantCode", "button:取消", "button:下一步"]);
  assert.deepEqual(wizardKeyboardPlan(2), ["input:knowledgeBases", "input:documentsPerKnowledgeBase", "input:storageGb", "input:fileMb", "input:textMib", "button:取消", "button:上一步", "button:下一步"]);
  assert.deepEqual(wizardKeyboardPlan(3), ["input:loginName", "input:readonly", "input:password", "input:passwordConfirmation", "input:confirmed", "button:取消", "button:上一步", "button:创建租户"]);
  assert.throws(() => wizardKeyboardPlan(4), /步骤/);
});
test("Tab 跳过输入或错误顺序、Shift+Tab 无法返回都会失败", () => {
  const expected = wizardKeyboardPlan(3);
  assert.doesNotThrow(() => verifyKeyboardTraversal(expected, [...expected]));
  assert.doesNotThrow(() => verifyKeyboardTraversal(expected.slice(0, -1).reverse(), expected.slice(0, -1).reverse()));
  assert.throws(() => verifyKeyboardTraversal(expected, expected.filter(value => value !== "input:password")), /键盘/);
  assert.throws(() => verifyKeyboardTraversal(expected, [...expected].reverse()), /键盘/);
  assert.throws(() => verifyKeyboardTraversal(expected.slice(0, -1).reverse(), []), /键盘/);
});
test("键盘定位不把为加载尺寸保留的不可见文案算成当前按钮名称", () => {
  const source = fs.readFileSync(path.join(__dirname, "../development/acceptance-onboarding.cjs"), "utf8");
  assert.match(source, /return `button:\$\{element\.innerText\.trim\(\)\}`/);
});
test("子步骤定位只披露静态阶段，不输出浏览器原异常中的密码或页面数据", async () => {
  await assert.rejects(() => acceptanceStep("填写组织名称", async () => { throw new Error("synthetic-secret-password 页面正文"); }), error => {
    assert.match(error.message, /填写组织名称/);
    assert.doesNotMatch(error.message, /synthetic-secret|页面正文/);
    return error instanceof SafeAssertionError;
  });
  await assert.rejects(() => acceptanceStep("第二步布局", async () => { throw new SafeAssertionError("同行未对齐"); }), /第二步布局.*同行未对齐/);
  assert.equal(await acceptanceStep("读取结果", async () => 42), 42);
});
test("Fluent 必填星号可匹配，但相似名称和正则元字符不扩大字段范围", () => {
  const label = wizardLabelPattern("组织名称");
  for (const text of ["组织名称", "组织名称*", "组织名称 *"]) assert.equal(label.test(text), true);
  for (const text of ["原组织名称", "组织名称说明", "组织名称**"]) assert.equal(label.test(text), false);
  assert.equal(wizardLabelPattern("每库容量（GB）(必填)").test("每库容量（GB）(必填)*"), true);
  assert.equal(wizardLabelPattern("容量.*").test("容量任意字段"), false);
});
test("租户状态徽标只能按内容宽度展示，不能被 flex 拉宽或占据半卡片", () => {
  const height = { badgeHeight: 30, contentHeight: 28, verticalInsets: 2 };
  for (const contentWidth of [24, 48]) assert.doesNotThrow(() => verifyOverviewBadge({ ...height, cardWidth: 320, badgeWidth: contentWidth + 10, contentWidth, insets: 10 }));
  assert.throws(() => verifyOverviewBadge({ ...height, cardWidth: 944, badgeWidth: 425, contentWidth: 24, insets: 10 }), /徽标.*拉宽/);
  assert.throws(() => verifyOverviewBadge({ ...height, cardWidth: 200, badgeWidth: 100, contentWidth: 90, insets: 10 }), /半张卡片/);
  assert.throws(() => verifyOverviewBadge({ ...height, cardWidth: 944, badgeWidth: 0, contentWidth: 0, insets: 0 }), /缺失/);
  assert.throws(() => verifyOverviewBadge({ ...height, cardWidth: 944, badgeWidth: 36, contentWidth: 24, insets: 12, badgeHeight: 20 }), /徽标.*裁切/);
  assert.throws(() => verifyOverviewBadge({ ...height, cardWidth: 320, badgeWidth: 30, contentWidth: 24, insets: 12 }), /徽标.*裁切/);
});
test("字号检查失败时仍恢复原 inline 样式，不能污染下一尺寸或步骤", async () => {
  const originals = [{ index: 0, style: "color: red", marker: null, size: 12 }];
  const restored = [];
  const settled = [];
  const page = { waitForFunction: async (_callback, argument) => { settled.push(argument); }, evaluate: async (_callback, argument) => {
    if (typeof argument === "string") return originals;
    restored.push(argument);
    return argument.length;
  } };
  await assert.rejects(() => withTextScale(page, true, async () => { throw new SafeAssertionError("徽标被拉宽"); }), /徽标被拉宽/);
  assert.deepEqual(restored, [originals]);
  assert.equal(settled[0].phase, "viewport");
  assert.deepEqual(settled.filter(value => value.sizes).map(value => value.sizes), [[24], [12]]);
  await assert.rejects(() => withTextScale({ waitForFunction: async () => {}, evaluate: async (_callback, argument) => typeof argument === "string" ? [] : 0 }, true, async () => 42), /未找到.*文字/);
});
test("只有字号过渡结束后才能执行布局断言，恢复后才能进入下一尺寸", async () => {
  const order = [];
  const originals = [{ index: 0, style: null, marker: null, size: 12 }];
  const page = {
    evaluate: async (_callback, argument) => { const restoring = Array.isArray(argument); order.push(restoring ? "restore" : "scale"); return restoring ? 1 : originals; },
    waitForFunction: async (_callback, argument) => { order.push(argument.phase === "viewport" ? "viewport-settled" : "settled"); },
  };
  await withTextScale(page, true, async () => { order.push("assert"); });
  assert.deepEqual(order, ["viewport-settled", "scale", "settled", "assert", "restore", "settled"]);
});


test("原型布局拒绝旧底部导航、无内边距表单和未固定的移动操作栏", () => {
  const { verifyPrototypeFrame } = require("../development/acceptance-onboarding.cjs");
  const mobile = { mainPadding: "20px 16px 40px", cardPadding: "16px", sidebarPosition: "sticky", sidebarTop: "0px", actionsPosition: "fixed", headingClip: "none", navigationFontSize: "10.4px", navigationLineHeight: "16.64px", iconWidth: "20px", iconHeight: "24px", iconMarginRight: "0px" };
  assert.doesNotThrow(() => verifyPrototypeFrame(mobile, 390));
  for (const wrong of [{ sidebarPosition: "fixed" }, { cardPadding: "0px" }, { actionsPosition: "static" }, { mainPadding: "0px" }, { navigationFontSize: "14px" }, { navigationLineHeight: "20px" }, { iconHeight: "20px" }, { iconMarginRight: "6px" }]) {
    assert.throws(() => verifyPrototypeFrame({ ...mobile, ...wrong }, 390));
  }
  const tablet = { ...mobile, mainPadding: "32px 32px 48px", cardPadding: "16px 20px", actionsPosition: "static", headingClip: "inset(50%)", navigationFontSize: "16px", navigationLineHeight: "25.6px", iconWidth: "24px" };
  assert.doesNotThrow(() => verifyPrototypeFrame(tablet, 1024));
  assert.throws(() => verifyPrototypeFrame({ ...tablet, headingClip: "none" }, 1024));
  assert.doesNotThrow(() => verifyPrototypeFrame({ ...tablet, headingClip: "none" }, 1440));
});

test("正常字号也必须等待视口布局后再检查概览触控尺寸", async () => {
  const { withTextScale } = require("../development/acceptance-onboarding.cjs");
  const order = [];
  const page = { waitForFunction: async () => { order.push("viewport-settled"); } };
  await withTextScale(page, false, async () => { order.push("assert"); });
  assert.deepEqual(order, ["viewport-settled", "assert"]);
});
