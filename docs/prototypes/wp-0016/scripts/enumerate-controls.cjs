// 从隔离浏览器枚举实际渲染控件；监听器只证明接线，业务闭环仍由专项测试验证。
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { chromium } = require("playwright");
const { openForReview, createConversation } = require("./browser-session.cjs");
const inventory = require("../page-inventory.json");
const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const output = process.env.PROTOTYPE_CONTROL_REPORT || path.join(os.tmpdir(), "wp0016-control-inventory.json");
const excluded = ["device-credentials", "channel-binding"];
async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, reducedMotion: "reduce" });
  await context.addInitScript(() => {
    const add = EventTarget.prototype.addEventListener;
    window.controlEvents = new WeakMap();
    EventTarget.prototype.addEventListener = function (event, ...args) {
      window.controlEvents.set(this, [...(window.controlEvents.get(this) || []), event]);
      return add.call(this, event, ...args);
    };
  });
  const page = await context.newPage();
  const report = { generatedAt: new Date().toISOString(), scope: "M6，排除渠道绑定与设备凭据；邮件邀请无实现控件", scenes: [], errors: [], unbound: [] };
  page.on("pageerror", error => report.errors.push(error.message));
  try {
    for (const group of inventory.groups.filter(item => item.phase === "M6")) for (const item of group.pages.filter(item => !excluded.includes(item.screen))) {
      for (const state of inventory.states) {
        if (item.screen === "conversation") {
          await openForReview(page, `${base}?screen=assistants&state=success&review=0`);
          await createConversation(page);
          const conversationId = new URL(page.url()).searchParams.get("conversationId");
          await page.goto(`${base}?screen=conversation&state=${state}&conversationId=${encodeURIComponent(conversationId)}&review=0`);
        } else {
          await openForReview(page, `${base}?screen=${item.screen}&state=${state}&review=0`);
        }
        // 正常聊天须先创建真实合成会话；其余场景也不能把尚未渲染的外壳计入覆盖。
        await page.locator(`main[data-screen="${item.screen}"][data-state="${state}"]`).waitFor();
        const controls = await page.evaluate(() => [...document.querySelectorAll("button, input, textarea, select, a, summary")].filter(element => !element.closest("#prototype-reviewer")).map(element => {
          const events = window.controlEvents.get(element) || [];
          const formEvents = window.controlEvents.get(element.form) || [];
          const native = ["A", "SUMMARY"].includes(element.tagName) || Boolean(element.form && (element.form.method === "dialog" || formEvents.includes("submit")));
          const nativeControl = ["INPUT", "SELECT", "TEXTAREA"].includes(element.tagName);
          // 公共分页以组件级事件委托接线；只有已验证的分页组件适用，不把根节点权限拦截误算成业务处理。
          const pagerEvents = window.controlEvents.get(element.closest('.pagination')) || [];
          const paginated = Boolean(element.closest('.pagination') && pagerEvents.includes('click') && pagerEvents.includes('change') && pagerEvents.includes('keydown'));
          const directHandler = ['onclick', 'oninput', 'onchange', 'onkeydown'].some(key => typeof element[key] === 'function');
          const pickerEvents = window.controlEvents.get(element.closest('[data-assistant-picker]')) || [];
          const pickerChoice = element.matches('[data-choice-id]') && pickerEvents.includes('change');
          const bound = element.disabled || native || paginated || pickerChoice || directHandler || events.includes("click") || nativeControl && events.some(event => ["input", "change", "keydown"].includes(event));
          return { id: element.dataset.odId || element.id, tag: element.tagName, type: element.type, label: (element.getAttribute("aria-label") || element.closest("label")?.textContent || element.textContent || element.placeholder || "").trim(), disabled: element.disabled || false, visible: Boolean(element.getClientRects().length), dialog: Boolean(element.closest("dialog")), events, form: element.form?.id || "", bound, status: element.dataset.controlStatus || "" };
        }));
        const actualScreen = await page.locator("main").getAttribute("data-screen");
        const actualState = await page.locator("main").getAttribute("data-state");
        if (actualScreen !== item.screen || actualState !== state) report.errors.push(`场景不匹配：${item.screen}/${state} → ${actualScreen}/${actualState}`);
        report.scenes.push({ screen: item.screen, state, actualScreen, actualState, controls });
        report.unbound.push(...controls.filter(control => !control.bound || control.status === "unimplemented").map(control => ({ screen: item.screen, state, ...control })));
      }
      console.log(`已枚举 ${item.screen}`);
    }
    fs.writeFileSync(output, JSON.stringify(report, null, 2));
    console.log(JSON.stringify({ scenes: report.scenes.length, controls: report.scenes.reduce((n, scene) => n + scene.controls.length, 0), unbound: report.unbound, errors: report.errors }, null, 2));
    process.exitCode = report.unbound.length || report.errors.length ? 1 : 0;
  } finally { await browser.close(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
