const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require("playwright");
const { openForReview } = require("./browser-session.cjs");

const base = process.env.PROTOTYPE_BASE_URL || "http://127.0.0.1:4176/";
const evidenceRoot = process.env.PROTOTYPE_EVIDENCE_DIR;
const observations = [];
let context, launchPromise, cleanupPromise;
let output;
if (evidenceRoot) {
  const stat = fs.lstatSync(evidenceRoot);
  assert.ok(stat.isDirectory() && !stat.isSymbolicLink() && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o700);
  assert.ok(fs.realpathSync(evidenceRoot).startsWith("/private/tmp/"));
  output = fs.mkdtempSync(path.join(evidenceRoot, "prototype-observe-"));
  fs.chmodSync(output, 0o700);
}

const cache = fs.mkdtempSync("/private/tmp/wp0019-s2-browser-cache-");
fs.chmodSync(cache, 0o700);

// 缓存仅含本脚本新建的浏览器资料；日志、截图和观察回执始终位于其外。
async function cleanup() {
  if (!cleanupPromise) cleanupPromise = (async () => {
    try { await (context || await launchPromise?.catch(() => undefined))?.close(); }
    finally { fs.rmSync(cache, { recursive: true, force: true }); }
  })();
  await cleanupPromise;
}
for (const signal of ["SIGINT", "SIGTERM"]) process.once(signal, async () => {
  try { await cleanup(); } finally { process.exit(signal === "SIGINT" ? 130 : 143); }
});
before(async () => {
  launchPromise = chromium.launchPersistentContext(cache, {
    headless: true, reducedMotion: "reduce",
    args: ["--disk-cache-size=10485760", "--media-cache-size=1048576"],
  });
  context = await launchPromise;
});
after(async () => {
  await cleanup();
  if (output) fs.writeFileSync(path.join(output, "observations.json"), JSON.stringify({
    scope: "S2 原型候选，不是生产验收", observations, cacheRemoved: !fs.existsSync(cache),
  }, null, 2) + "\n", { flag: "wx", mode: 0o600 });
});

const url = (screen, state = "success") => `${base}?screen=${screen}&state=${state}&review=0`;
async function visit(width, action) {
  const page = await context.newPage();
  await page.setViewportSize({ width, height: width === 390 ? 844 : width === 1024 ? 900 : 1000 });
  page.setDefaultTimeout(8000);
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  try {
    await page.goto(url("console-login"));
    await page.evaluate(async () => {
      const { PROTOTYPE_STATE_KEY, QUERY_SESSION_KEY } = await import("./prototype-state.js");
      // 只重置本脚本独占的临时浏览器内合成数据，不接触用户会话或生产存储。
      localStorage.removeItem(PROTOTYPE_STATE_KEY); localStorage.removeItem(QUERY_SESSION_KEY);
    });
    await action(page);
    assert.deepEqual(errors, [], "候选页面不得产生运行异常");
  } catch (error) {
    await capture(page, `failure-${width}-${Date.now()}`).catch(() => {});
    throw error;
  } finally { await page.close(); }
}
async function doubleText(page) {
  await page.evaluate(() => {
    const values = [...document.querySelectorAll("body, body *")].map(element => [element, parseFloat(getComputedStyle(element).fontSize)]);
    for (const [element, size] of values) if (Number.isFinite(size)) element.style.setProperty("font-size", `${size * 2}px`, "important");
  });
}
async function capture(page, name) {
  if (output) {
    const target = path.join(output, name + ".png");
    await page.screenshot({ path: target }); fs.chmodSync(target, 0o600);
  }
}
async function assertNoOverflow(page, name) {
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, name + " 页面横向溢出");
  assert.doesNotMatch(await page.locator("main").innerText(), /\b(?:undefined|null)\b/);
  assert.equal(await page.evaluate(() => matchMedia("(prefers-reduced-motion: reduce)").matches), true);
}

for (const width of [390, 1024, 1440]) for (const scale of [1, 2]) {
  test(`S2 两页七态：${width}px / ${scale * 100}% 文字`, () => visit(width, async page => {
    for (const screen of ["platform-model-deployments", "platform-model-deployment-detail"]) {
      for (const state of ["loading", "empty", "error", "denied", "partial", "disabled", "success"]) {
        await openForReview(page, url(screen, state));
        await page.locator(`main[data-screen="${screen}"]`).waitFor();
        if (scale === 2) await doubleText(page);
        await assertNoOverflow(page, screen + "/" + state);
        if (state === "partial") {
          assert.match(await page.locator("main").innerText(), /暂未取得/);
          assert.equal(await page.getByRole("button", { name: "重新载入", exact: true }).isEnabled(), true);
        }
        if (["partial", "disabled"].includes(state)) {
          for (const button of await page.locator("[data-open-platform-deployment], [data-verify-platform-deployment], [data-publish-platform-deployment]").all()) {
            assert.equal(await button.isDisabled(), true);
          }
        }
        observations.push({ kind: "page", screen, state, width, scale, passed: true });
        await capture(page, `${screen}-${state}-${width}-${scale}`);
      }
    }
  }));

  test(`S2 四类模型表单、取消与键盘：${width}px / ${scale * 100}% 文字`, () => visit(width, async page => {
    for (const type of ["CHAT", "EMBEDDING", "RERANK", "UNKNOWN"]) {
      await openForReview(page, url("platform-model-deployments"));
      await page.getByRole("button", { name: "建立部署草稿", exact: true }).focus();
      await page.keyboard.press("Enter");
      const dialog = page.locator("#platform-deployment-dialog"), form = page.locator("#platform-deployment-form");
      await dialog.waitFor({ state: "visible" });
      assert.equal(await form.locator('[name="authorizeTenant"]:checked').count(), 0, "不可预选任意租户");
      const value = { CHAT: "pc-chat|model-chat-v6", EMBEDDING: "pc-embedding|model-embedding-v4", RERANK: "pc-rerank|model-rerank-v2", UNKNOWN: "pc-chat|model-unconfirmed-s2" }[type];
      await form.getByLabel("已发现模型", { exact: true }).selectOption(value);
      if (type === "UNKNOWN") {
        assert.equal(await form.locator('[name="capability"]').inputValue(), "", "未知类型不能默认继承");
        await form.getByLabel("模型类型", { exact: true }).selectOption("CHAT");
      }
      await form.getByLabel("部署名称", { exact: true }).fill("可验证的平台部署候选");
      await form.locator('[name="contextWindow"]').fill("4096");
      const limit = form.locator('[name="outputLimit"], [name="embeddingDimension"], [name="rerankCandidateLimit"]');
      await limit.fill(type === "EMBEDDING" ? "1024" : type === "RERANK" ? "30" : "64");
      await form.locator('[name="parameterSource"]').fill("厂商资料示例版本 v1");
      const before = await form.locator('[name="contextWindow"]').evaluate(element => parseFloat(getComputedStyle(element).fontSize));
      if (scale === 2) await doubleText(page);
      assert.equal(await form.locator('[name="contextWindow"]').evaluate(element => parseFloat(getComputedStyle(element).fontSize)), before * scale);
      assert.equal(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth + 1), true, "表单横向溢出");
      for (const input of await form.locator('input:not([type="hidden"]):not([type="checkbox"]), select').all()) {
        await input.scrollIntoViewIfNeeded();
        const bounds = await input.boundingBox();
        const viewport = page.viewportSize();
        assert.ok(bounds && bounds.x >= 0 && bounds.x + bounds.width <= width + 1 && bounds.y >= 0 && bounds.y + bounds.height <= viewport.height + 1, "原生输入须可滚动到视口");
        assert.equal(await input.evaluate(element => element.scrollHeight <= element.clientHeight + 1), true, "原生文字不能被控件裁切");
      }
      const save = form.getByRole("button", { name: "保存部署草稿", exact: true });
      await form.getByLabel("部署名称", { exact: true }).focus();
      for (let step = 0; step < 40; step++) {
        if (await save.evaluate(element => element === document.activeElement)) break;
        const focus = await page.evaluate(() => {
          const element = document.activeElement, rect = element.getBoundingClientRect();
          const hit = document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2);
          return {
            inside: Boolean(element.closest("#platform-deployment-dialog")),
            reachable: rect.width > 0 && rect.height > 0 && rect.y >= 0 && rect.bottom <= innerHeight + 1 && (hit === element || element.contains(hit)),
            checkbox: element.matches('input[type="checkbox"]'),
          };
        });
        assert.equal(focus.inside, true, "焦点不得离开当前弹窗");
        assert.equal(focus.reachable, true, "键盘焦点不能被标题或动作区遮挡");
        if (focus.checkbox) await page.keyboard.press("Space");
        await page.keyboard.press("Tab");
      }
      assert.equal(await save.evaluate(element => element === document.activeElement), true, "Tab 必须能够到达保存动作");
      const heading = await dialog.locator(".dialog-header").boundingBox();
      assert.ok(heading && heading.y >= 0 && heading.y + heading.height <= page.viewportSize().height + 1, "滚动内容后弹窗标题仍须可见");
      await capture(page, `form-${type}-${width}-${scale}`);
      await page.keyboard.press("Escape");
      await dialog.waitFor({ state: "hidden" });
      assert.equal(await page.locator("[data-open-platform-deployment]").evaluate(element => element === document.activeElement), true);
      observations.push({ kind: "form", type, width, scale, passed: true });
    }
  }));
}

test("S2 未知候选确认、草稿、验证、独立发布与恢复", () => visit(1440, async page => {
  await openForReview(page, url("platform-model-deployments"));
  await page.getByRole("button", { name: "建立部署草稿", exact: true }).click();
  const form = page.locator("#platform-deployment-form");
  await form.getByLabel("部署名称", { exact: true }).fill("待确认模型的授权部署");
  await form.getByLabel("已发现模型", { exact: true }).selectOption("pc-chat|model-unconfirmed-s2");
  await form.getByLabel("模型类型", { exact: true }).selectOption("CHAT");
  await form.getByLabel("上下文窗口（Token）", { exact: true }).fill("4096");
  await form.getByLabel("最大输出（Token）", { exact: true }).fill("64");
  await form.getByLabel("参数来源", { exact: true }).fill("合成厂商资料 v1");
  await form.getByLabel("我已确认模型类型、能力和参数来源", { exact: true }).check();
  await form.getByRole("button", { name: "保存部署草稿", exact: true }).click();
  assert.match(await form.locator('[role="alert"]').innerText(), /拟授权租户/);
  await form.locator('[name="authorizeTenant"][value="tenant-spruce"]').check();
  await form.getByRole("button", { name: "保存部署草稿", exact: true }).click();
  await page.locator('main[data-screen="platform-model-deployment-detail"] [data-model-status="DRAFT"]').waitFor();
  assert.match(await page.locator("main").innerText(), /拟授权租户/);
  assert.match(await page.locator("main").innerText(), /月度费用未核验/);
  await page.getByRole("button", { name: "执行能力验证", exact: true }).click();
  await page.locator('[data-model-status="VERIFIED"]').waitFor();
  await page.getByRole("button", { name: "发布部署", exact: true }).click();
  await page.locator('[data-model-status="PUBLISHED"]').waitFor();
  assert.match(await page.locator("main").innerText(), /已授权租户/);
  await page.reload();
  await page.locator('[data-model-status="PUBLISHED"]').waitFor();
  assert.match(await page.locator("main").innerText(), /待确认模型的授权部署/);
  observations.push({ kind: "journey", name: "未知候选到独立发布", passed: true });
}));

test("S2 partial 重新载入恢复只读事实且不写业务对象", () => visit(1440, async page => {
  for (const screen of ["platform-model-deployments", "platform-model-deployment-detail"]) {
    await openForReview(page, url(screen, "partial"));
    const readFacts = () => page.evaluate(async () => {
      const { createPrototypeStore } = await import("./prototype-state.js");
      const models = createPrototypeStore().getSnapshot().models;
      return models.platformDeployments.map(item => ({
        id: item.id, status: item.status, tenantIds: item.tenantIds, authorizationStatus: item.authorizationStatus,
      }));
    });
    const before = await readFacts();
    await page.getByRole("button", { name: "重新载入", exact: true }).click();
    await page.locator(`main[data-screen="${screen}"][data-state="success"]`).waitFor();
    assert.deepEqual(await readFacts(), before);
    observations.push({ kind: "read-recovery", screen, passed: true });
  }
}));

test("S2 取消后重新打开不残留模型、参数或租户选择", () => visit(390, async page => {
  await openForReview(page, url("platform-model-deployments"));
  const opener = page.getByRole("button", { name: "建立部署草稿", exact: true });
  await opener.click();
  const form = page.locator("#platform-deployment-form");
  await form.getByLabel("已发现模型", { exact: true }).selectOption("pc-chat|model-unconfirmed-s2");
  await form.getByLabel("模型类型", { exact: true }).selectOption("CHAT");
  await form.getByLabel("参数来源", { exact: true }).fill("合成来源");
  await form.locator('[name="authorizeTenant"]').first().check();
  await page.keyboard.press("Escape");
  await opener.click();
  assert.equal(await form.locator("[data-platform-deployment-selected-model]").innerText(), "", "取消后不得残留旧模型预览");
  assert.equal(await form.getByLabel("已发现模型", { exact: true }).inputValue(), "");
  assert.equal(await form.locator('[name="authorizeTenant"]:checked').count(), 0);
  assert.equal(await form.locator('[name="parameterSource"]').count(), 0);
  await page.keyboard.press("Escape");
  observations.push({ kind: "cancel-recovery", passed: true });
}));
