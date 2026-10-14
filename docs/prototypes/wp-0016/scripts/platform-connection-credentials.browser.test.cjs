const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require('playwright');
const { openForReview } = require('./browser-session.cjs');

const base = process.env.PROTOTYPE_BASE_URL || 'http://127.0.0.1:4176/';
const temporaryScreenshots = process.env.PROTOTYPE_SCREENSHOT_DIR;
if (temporaryScreenshots && !path.resolve(temporaryScreenshots).startsWith('/private/tmp/')) throw new Error('截图只能存放于 /private/tmp');
let browser;
before(async () => { browser = await chromium.launch({ headless: true }); });
after(async () => { await browser?.close(); });

async function visit(action, width = 1440) {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : width === 1024 ? 900 : 1000 }, reducedMotion: 'reduce' });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  const errors = [];
  page.on('pageerror', () => errors.push('PAGE_ERROR'));
  try {
    await openForReview(page, `${base}?screen=platform-model-connections&state=success&review=0`);
    await page.evaluate(() => localStorage.removeItem('wp0016-business-demo-v1'));
    await page.reload();
    await action(page);
    assert.deepEqual(errors, []);
  } finally { await context.close(); }
}

async function openArk(page) {
  await page.getByRole('button', { name: '新增供应商连接', exact: true }).click();
  const form = page.locator('#platform-connection-form');
  await form.getByLabel('厂商类型', { exact: true }).selectOption('VOLCENGINE');
  return form;
}

async function fillSecrets(form) {
  await form.locator('[name="accessKey"]').fill('synthetic-inference-only');
  await form.locator('[name="discoveryAccessKeyId"]').fill('synthetic-access-only');
  await form.locator('[name="discoverySecretAccessKey"]').fill('synthetic-secret-only');
}

async function assertSecretsCleared(form) {
  for (const name of ['accessKey', 'discoveryAccessKeyId', 'discoverySecretAccessKey']) assert.equal(await form.locator(`[name="${name}"]`).inputValue(), '');
}

async function prepareEmptyConnections(page) {
  // 只准备独立浏览器中的合成列表，不清除登录态或调用任何生产接口。
  await page.evaluate(async () => {
    const { createPrototypeStore, PROTOTYPE_STATE_KEY } = await import('./prototype-state.js');
    const snapshot = createPrototypeStore().getWorkspaceSnapshot();
    snapshot.models.platformConnections = [];
    snapshot.models.selectedPlatformConnectionId = null;
    localStorage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  });
}

async function connectionFacts(page) {
  return page.evaluate(async () => {
    const { createPrototypeStore } = await import('./prototype-state.js');
    const models = createPrototypeStore().getWorkspaceSnapshot().models;
    return { selectedId: models.selectedPlatformConnectionId, connections: models.platformConnections.map(item => ({ id: item.id, name: item.name, status: item.status })) };
  });
}

async function doubleText(page) {
  await page.evaluate(() => {
    const sizes = [...document.querySelectorAll('body, body *')].map(element => [element, parseFloat(getComputedStyle(element).fontSize)]);
    for (const [element, size] of sizes) if (Number.isFinite(size)) element.style.setProperty('font-size', `${size * 2}px`, 'important');
  });
}

for (const realEmpty of [false, true]) for (const width of [390, 1024, 1440]) test(`${realEmpty ? '业务空目录' : '评审空态'}在 ${width}px、200% 文字下可取消、保存首个草稿并刷新返回`, () => visit(async page => {
  if (realEmpty) await prepareEmptyConnections(page);
  await openForReview(page, `${base}?screen=platform-model-connections&state=${realEmpty ? 'success' : 'empty'}&review=0`);
  const before = await connectionFacts(page);
  assert.equal(await page.locator('main .state-panel[data-kind="empty"]').count(), 1, '空目录必须有说明面板');
  assert.match(await page.locator('main').innerText(), /当前还没有供应商连接。请先保存连接草稿/);
  assert.equal(await page.locator('main [data-open-platform-connection]').count(), 1, '空态必须挂载既有创建操作');
  assert.equal(await page.locator('main tbody tr').count(), 0);
  const initialFont = await page.locator('#platform-connection-form [name="accessKey"]').evaluate(element => parseFloat(getComputedStyle(element).fontSize));
  await doubleText(page);
  const create = page.getByRole('button', { name: '新增供应商连接', exact: true });
  assert.equal(await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches), true);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
  await create.focus();
  await page.keyboard.press('Enter');
  const dialog = page.locator('#platform-connection-dialog');
  await dialog.waitFor({ state: 'visible' });
  assert.equal(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth + 1), true);
  const form = page.locator('#platform-connection-form');
  assert.equal(await form.locator('[name="accessKey"]').evaluate(element => parseFloat(getComputedStyle(element).fontSize)), initialFont * 2);
  await form.getByLabel('厂商类型', { exact: true }).selectOption('VOLCENGINE');
  await fillSecrets(form);
  // 原生 close 事件异步执行重置；等待既有关闭处理，而非仅检查 open 属性或浏览器自动归还的焦点。
  await dialog.evaluate(element => {
    element.dataset.closeHandled = 'false';
    element.addEventListener('close', () => { element.dataset.closeHandled = 'true'; }, { once: true });
  });
  if (width === 390) await page.keyboard.press('Escape');
  else await form.getByRole('button', { name: '取消', exact: true }).click();
  await page.waitForFunction(() => document.querySelector('#platform-connection-dialog')?.dataset.closeHandled === 'true');
  await dialog.waitFor({ state: 'hidden' });
  await assertSecretsCleared(form);
  assert.equal(await create.evaluate(element => element === document.activeElement), true);
  assert.deepEqual(await connectionFacts(page), before, '取消不能创建连接或替换原有列表');
  await page.reload();
  assert.deepEqual(await connectionFacts(page), before, '刷新不能恢复默认演示连接');
  await doubleText(page);
  const reopened = await openArk(page);
  assert.equal(await reopened.locator('[name="accessKey"]').evaluate(element => parseFloat(getComputedStyle(element).fontSize)), initialFont * 2);
  await assertSecretsCleared(reopened);
  const name = `空目录连接-${width}`;
  await reopened.getByLabel('连接名称', { exact: true }).fill(name);
  await fillSecrets(reopened);
  await reopened.getByRole('button', { name: '保存连接草稿', exact: true }).click();
  await page.locator('main[data-screen="platform-model-connection-detail"] [data-model-status="DRAFT"]').waitFor();
  const saved = await connectionFacts(page);
  assert.equal(new URL(page.url()).searchParams.get('connectionId'), saved.selectedId, '详情必须定位本次精确草稿');
  assert.equal(saved.connections.length, before.connections.length + 1);
  assert.equal(saved.connections.find(item => item.id === saved.selectedId)?.name, name);
  assert.doesNotMatch(await page.locator('main').innerText(), /当前还没有可查看的连接/);
  assert.match(await page.locator('main').innerText(), /发现模型/);
  await page.reload();
  assert.deepEqual(await connectionFacts(page), saved);
  assert.match(await page.locator('main').innerText(), new RegExp(name));
  await page.getByRole('button', { name: '返回连接目录', exact: true }).click();
  await page.locator('main[data-screen="platform-model-connections"]').waitFor();
  assert.deepEqual(await connectionFacts(page), saved);
  assert.equal(await page.locator('main tbody tr').count(), saved.connections.length);
  const storage = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
  assert.doesNotMatch(storage + page.url() + await page.locator('body').innerText(), /synthetic-(inference|access|secret)-only/);
}, width));

for (const realEmpty of [false, true]) test(`${realEmpty ? '业务无连接' : '评审空态'}详情可返回目录且不创建默认连接`, () => visit(async page => {
  if (realEmpty) await prepareEmptyConnections(page);
  await openForReview(page, `${base}?screen=platform-model-connection-detail&state=${realEmpty ? 'success' : 'empty'}&review=0`);
  const before = await connectionFacts(page);
  assert.match(await page.locator('main').innerText(), /当前还没有可查看的连接。请先从连接目录选择或建立连接草稿。/);
  assert.equal(await page.getByRole('button', { name: '返回连接目录', exact: true }).count(), 1);
  await page.getByRole('button', { name: '返回连接目录', exact: true }).click();
  await page.locator('main[data-screen="platform-model-connections"]').waitFor();
  assert.deepEqual(await connectionFacts(page), before);
  assert.equal(await page.getByRole('button', { name: '新增供应商连接', exact: true }).isVisible(), true);
  if (realEmpty) assert.equal(await page.locator('main .state-panel[data-kind="empty"]').count(), 1);
}));

test('方舟显示独立发现认证，缺少 AK/SK 不保存并清空推理密钥', () => visit(async page => {
  const form = await openArk(page);
  for (const label of ['模型发现 Access Key ID', '模型发现 Secret Access Key']) {
    const input = form.getByLabel(label, { exact: true });
    assert.equal(await input.isVisible(), true);
    assert.equal(await input.getAttribute('type'), 'password');
    assert.equal(await input.getAttribute('required'), '');
  }
  await form.locator('[name="accessKey"]').fill('synthetic-inference-only');
  await form.getByRole('button', { name: '保存连接草稿', exact: true }).click();
  assert.match(await form.locator('[role="alert"]').innerText(), /独立.*AK.*SK/);
  await assertSecretsCleared(form);
  assert.equal(await page.locator('#platform-connection-dialog').isVisible(), true);
}));

test('方舟保存只记录配置状态，URL、页面及浏览器存储没有三项秘密', () => visit(async page => {
  const form = await openArk(page);
  await fillSecrets(form);
  await form.getByRole('button', { name: '保存连接草稿', exact: true }).click();
  await page.locator('main[data-screen="platform-model-connection-detail"]').waitFor();
  assert.match(await page.locator('main').innerText(), /模型发现认证/);
  assert.match(await page.locator('main').innerText(), /独立 AK\/SK 已配置/);
  const storage = await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]));
  assert.doesNotMatch(storage + page.url() + await page.locator('body').innerText(), /synthetic-(inference|access|secret)-only/);
  await page.reload();
  assert.match(await page.locator('main').innerText(), /独立 AK\/SK 已配置/);
}));

test('切换厂商后隐藏并禁用方舟凭据，再选回时三项输入仍为空', () => visit(async page => {
  const form = await openArk(page);
  await fillSecrets(form);
  await form.getByLabel('厂商类型', { exact: true }).selectOption('DEEPSEEK');
  await assertSecretsCleared(form);
  assert.equal(await form.locator('[data-provider-discovery-credentials]').isVisible(), false);
  for (const name of ['discoveryAccessKeyId', 'discoverySecretAccessKey']) assert.equal(await form.locator(`[name="${name}"]`).isDisabled(), true);
  await form.getByLabel('厂商类型', { exact: true }).selectOption('VOLCENGINE');
  await assertSecretsCleared(form);
}));

for (const close of ['取消', 'Escape']) test(`${close} 关闭后清空秘密并恢复新增按钮焦点`, () => visit(async page => {
  const form = await openArk(page);
  await fillSecrets(form);
  if (close === 'Escape') await page.keyboard.press('Escape');
  else await form.getByRole('button', { name: close, exact: true }).click();
  await page.locator('#platform-connection-dialog').waitFor({ state: 'hidden' });
  assert.equal(await page.getByRole('button', { name: '新增供应商连接', exact: true }).evaluate(element => element === document.activeElement), true);
  const reopened = await openArk(page);
  await assertSecretsCleared(reopened);
}));

test('保存失败后清空全部秘密且保持可重试的表单', () => visit(async page => {
  const form = await openArk(page);
  await form.getByLabel('连接名称', { exact: true }).fill('x');
  await fillSecrets(form);
  await form.getByRole('button', { name: '保存连接草稿', exact: true }).click();
  await assertSecretsCleared(form);
  assert.equal(await page.locator('#platform-connection-dialog').isVisible(), true);
  assert.notEqual(await form.locator('[role="alert"]').innerText(), '');
}));

for (const width of [390, 1024, 1440]) for (const textScale of [1, 2]) test(`${width} 宽度、${textScale * 100}% 文字下发现凭据无裁切、按钮和键盘可达`, () => visit(async page => {
  const form = await openArk(page);
  const beforeFont = await form.locator('[name="discoveryAccessKeyId"]').evaluate(element => parseFloat(getComputedStyle(element).fontSize));
  if (textScale === 2) await page.evaluate(() => {
    // 同时放大固定像素和继承字号，避免只改根字号却将固定 16px 误记为 200% 文字。
    const sizes = [...document.querySelectorAll('body, body *')].map(element => [element, parseFloat(getComputedStyle(element).fontSize)]);
    for (const [element, size] of sizes) if (Number.isFinite(size)) element.style.setProperty('font-size', `${size * 2}px`, 'important');
  });
  assert.equal(await form.locator('[name="discoveryAccessKeyId"]').evaluate(element => parseFloat(getComputedStyle(element).fontSize)), beforeFont * textScale);
  assert.equal(await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches), true);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
  const dialog = page.locator('#platform-connection-dialog');
  assert.equal(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth + 1), true);
  const inputs = form.locator('[data-provider-discovery-credentials] input');
  assert.equal(await inputs.count(), 2);
  for (const input of await inputs.all()) {
    await input.scrollIntoViewIfNeeded();
    const bounds = await input.boundingBox();
    assert.ok(bounds && bounds.x >= 0 && bounds.x + bounds.width <= width + 1 && bounds.y >= 0 && bounds.y + bounds.height <= (width === 390 ? 844 : width === 1024 ? 900 : 1000));
    assert.equal(await input.evaluate(element => element.scrollHeight <= element.clientHeight + 1), true, '输入文字不能被控件高度裁切');
  }
  await inputs.last().focus();
  await page.keyboard.press('Tab');
  assert.equal(await form.getByRole('button', { name: '取消', exact: true }).evaluate(element => element === document.activeElement), true);
  await page.keyboard.press('Tab');
  const save = form.getByRole('button', { name: '保存连接草稿', exact: true });
  assert.equal(await save.evaluate(element => element === document.activeElement), true);
  const bounds = await save.boundingBox();
  assert.ok(bounds && bounds.y >= 0 && bounds.y + bounds.height <= (width === 390 ? 844 : width === 1024 ? 900 : 1000));
  if (temporaryScreenshots) await page.screenshot({ path: path.join(temporaryScreenshots, `ark-dialog-${width}-${textScale}.png`) });
}, width));

for (const width of [390, 1024, 1440]) test(`${width} 宽度下平台连接两页七态无异常、缺失文案或横向溢出`, () => visit(async page => {
  for (const screen of ['platform-model-connections', 'platform-model-connection-detail']) {
    for (const state of ['loading', 'empty', 'error', 'denied', 'partial', 'disabled', 'success']) {
      await openForReview(page, `${base}?screen=${screen}&state=${state}&review=0`);
      await page.locator(`main[data-screen="${screen}"]`).waitFor();
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, `${screen}/${state}`);
      assert.doesNotMatch(await page.locator('main').innerText(), /synthetic-(inference|access|secret)-only/);
      if (state === 'empty') assert.doesNotMatch(await page.locator('main').innerText(), /\b(?:undefined|null)\b/, `${screen} 空态说明必须为可读文案`);
    }
  }
}, width));
