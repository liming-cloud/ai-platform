const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { mkdtempSync, realpathSync } = require('node:fs');
const { join } = require('node:path');
const { chromium } = require('playwright');
const { url, loginConsole } = require('./browser-session.cjs');

const artifacts = realpathSync(process.env.DIRECTORY_ARTIFACT_DIR || mkdtempSync('/private/tmp/wp0017-directory-design.'));
assert.ok(artifacts.startsWith('/private/tmp/'), '截图只允许保存到 /private/tmp');
let browser;
before(async () => { browser = await chromium.launch({ channel: 'chrome', headless: true }); });
after(async () => { await browser?.close(); });

async function visit(width, action, reducedMotion = 'no-preference') {
  const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.setDefaultTimeout(7000);
  try {
    await loginConsole(page, 'platform');
    await page.goto(url('platform-tenants'));
    await page.evaluate(() => document.fonts.ready);
    await action(page);
    assert.deepEqual(errors, [], '原型浏览器存在未处理异常');
  } finally { await context.close(); }
}

async function enlarge(page) {
  await page.evaluate(() => {
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    const parents = new Set();
    while (walker.nextNode()) {
      const element = walker.currentNode.parentElement;
      if (walker.currentNode.textContent.trim() && element instanceof HTMLElement
        && element.getBoundingClientRect().width > 0 && element.getBoundingClientRect().height > 0
        && getComputedStyle(element).visibility !== 'hidden') parents.add(element);
    }
    // 先读取全部字号，再对每个直接承载文字的元素翻倍，防止继承造成重复放大。
    const elements = [...parents];
    const sizes = elements.map(element => parseFloat(getComputedStyle(element).fontSize));
    elements.forEach((element, index) => { element.style.fontSize = `${sizes[index] * 2}px`; });
  });
}

async function hit(locator) {
  await locator.scrollIntoViewIfNeeded();
  const result = await locator.evaluate(element => {
    const box = element.getBoundingClientRect();
    const target = document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2);
    return { ok: target === element || element.contains(target),
      control: element.getAttribute('aria-label') || element.textContent.trim(), box: box.toJSON(),
      target: target?.className, scrollY };
  });
  assert.equal(result.ok, true, `可操作控件的中心被其他元素遮挡：${JSON.stringify(result)}`);
}

async function layout(page, width) {
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true,
    '原型出现文档级横向溢出');
  assert.deepEqual(await page.locator('main thead th').allTextContents(), ['租户', '状态', '租户管理员', '操作']);
  assert.match(await page.locator('.compact-summary').innerText(), /生命周期摘要[\s\S]*安全边界异常：尚未核实/);
  assert.equal(await page.locator('main #page-dialog').count(), 0);
  const main = await page.locator('main').innerText();
  assert.doesNotMatch(main, /驻留区域|安全边界异常 0|12 个活动租户|已停用/);
  const identities = await page.locator('tbody .primary-cell').evaluateAll(cells => cells.map(cell => {
    const name = cell.querySelector('strong');
    const code = cell.querySelector('.mono');
    return {
      fullName: getComputedStyle(name).textOverflow !== 'ellipsis' && name.scrollWidth <= name.clientWidth + 1,
      fullCode: getComputedStyle(code).textOverflow !== 'ellipsis' && code.scrollWidth <= code.clientWidth + 1,
      ordered: name.getBoundingClientRect().bottom <= code.getBoundingClientRect().top,
    };
  }));
  assert.ok(identities.length > 0 && identities.every(item => item.fullName && item.fullCode && item.ordered),
    '租户名称与编码必须完整显示，且不能互相重叠');
  if (width <= 680) {
    const separated = await page.locator('tbody tr[data-directory-state]').evaluateAll(rows => rows.every(row => {
      const cells = [...row.querySelectorAll('td')];
      return cells.slice(1).every((cell, index) => cells[index].getBoundingClientRect().bottom <= cell.getBoundingClientRect().top);
    }));
    assert.equal(separated, true, '窄屏租户事实行不能互相重叠');
  }
  for (const locator of [page.getByRole('button', { name: '建立新租户', exact: true }),
    page.getByRole('button', { name: '查看详情', exact: true }).first(),
    page.getByLabel('搜索租户', { exact: true }), page.getByLabel('每页条数', { exact: true }),
    page.getByLabel('跳转页码', { exact: true }), page.getByRole('button', { name: '跳转', exact: true })]) {
    const box = await locator.boundingBox();
    assert.ok(box && box.x >= 0 && box.x + box.width <= width + 1, '目录控件超出视口');
    await hit(locator);
  }
}

for (const width of [390, 1024, 1440]) {
  test(`${width}px 目录四列、事实说明、控件及暖色主题`, () => visit(width, async page => {
    await layout(page, width);
    const styles = await page.evaluate(() => ({
      body: getComputedStyle(document.body).fontSize,
      heading: getComputedStyle(document.querySelector('h1')).fontFamily,
      background: getComputedStyle(document.body).backgroundColor,
    }));
    assert.equal(styles.body, '16px');
    assert.match(styles.heading, /Georgia/);
    assert.equal(styles.background, 'rgb(245, 244, 237)');
    assert.equal(await page.locator('.brand-mark').innerText(), 'R');
    assert.equal(await page.locator('.brand-copy strong').innerText(), '可信 RAG');
    await page.evaluate(() => { document.activeElement?.blur(); window.scrollTo(0, 0); });
    await page.screenshot({ path: join(artifacts, `directory-${width}.png`), fullPage: true });
  }));
  test(`${width}px 的 200% 文字不溢出或遮挡`, () => visit(width, async page => {
    await enlarge(page);
    await layout(page, width);
    await page.evaluate(() => { document.activeElement?.blur(); window.scrollTo(0, 0); });
    await page.screenshot({ path: join(artifacts, `directory-${width}-text-200.png`), fullPage: true });
  }));
  test(`${width}px 保留七态及保护内容边界`, () => visit(width, async page => {
    for (const state of ['loading', 'empty', 'error', 'denied', 'partial', 'disabled', 'success']) {
      await page.goto(url('platform-tenants', state));
      await page.locator(`main[data-screen="platform-tenants"][data-state="${state}"]`).waitFor();
      assert.equal(await page.getByRole('heading', { name: '租户目录', exact: true }).count(), 1);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
      if (['loading', 'empty', 'error', 'denied'].includes(state)) {
        assert.equal(await page.locator('[data-protected-content]').count(), 0, '不可用状态不得展示保护目录');
      }
    }
  }));
}

test('目录分页、筛选与历史恢复使用原合成行，零资格不伪装为已分配', () => visit(1440, async page => {
  await page.evaluate(async () => {
    const { PROTOTYPE_STATE_KEY, createPrototypeStore } = await import('./prototype-state.js');
    const snapshot = createPrototypeStore().getSnapshot();
    const seed = snapshot.tenants[0];
    snapshot.tenants = Array.from({ length: 43 }, (_, index) => ({ ...seed,
      id: `directory-design-${index}`, code: `directory-${index}`, organizationName: `目录合成租户 ${index}`,
      status: ['provisioning', 'active', 'suspended'][index % 3], members: index === 0 ? [] : seed.members,
    }));
    localStorage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
  });
  await page.reload();
  const rows = page.locator('tbody tr[data-directory-state]:visible');
  assert.equal(await rows.count(), 20);
  assert.equal(await rows.first().locator('[data-label="租户管理员"]').innerText(), '未分配');
  const size = page.getByLabel('每页条数', { exact: true });
  assert.deepEqual(await size.locator('option').evaluateAll(options => options.map(option => option.value)), ['10', '20', '50', '100']);
  await size.selectOption('10');
  assert.equal(await rows.count(), 10);
  await page.getByLabel('跳转页码', { exact: true }).fill('5');
  await page.getByRole('button', { name: '跳转', exact: true }).click();
  assert.equal(await rows.count(), 3);
  await page.reload();
  assert.equal(await rows.count(), 3);
  await page.goBack();
  assert.equal(await rows.count(), 10);
  await page.goForward();
  assert.equal(await rows.count(), 3);
  const search = page.getByLabel('搜索租户', { exact: true });
  await search.fill('directory-12');
  assert.equal(await rows.count(), 1);
  assert.equal(new URL(page.url()).searchParams.get('page'), '1');
  await page.reload();
  assert.equal(await search.inputValue(), 'directory-12');
  assert.equal(await rows.count(), 1);
  await search.fill('');
  const status = page.getByLabel('状态', { exact: true });
  for (const [value, label] of [['provisioning', '待启用'], ['active', '活动'], ['suspended', '已暂停']]) {
    await status.selectOption(value);
    const labels = await rows.locator('[data-label="状态"]').allTextContents();
    assert.ok(labels.length > 0 && labels.every(text => text === label));
  }
  await status.selectOption('');
  await search.fill('无匹配合成租户');
  assert.equal(await rows.count(), 0);
  assert.match(await page.locator('.pagination-summary').innerText(), /共 0 条/);
  await search.fill('');
  await page.getByLabel('跳转页码', { exact: true }).fill('999');
  await page.getByRole('button', { name: '跳转', exact: true }).click();
  assert.match(await page.locator('.pagination [role="alert"]').innerText(), /1–5/);
}));

test('390px reduced-motion 下键盘查看详情及建立入口取消', () => visit(390, async page => {
  await layout(page, 390);
  const details = page.getByRole('button', { name: '查看详情', exact: true }).first();
  await details.focus();
  await page.keyboard.press('Enter');
  await page.locator('main[data-screen="platform-tenant-detail"]').waitFor();
  await page.goBack();
  await page.locator('main[data-screen="platform-tenants"]').waitFor();
  const count = await page.locator('tbody tr:visible').count();
  const create = page.getByRole('button', { name: '建立新租户', exact: true });
  await create.focus();
  await page.keyboard.press('Enter');
  await page.locator('main[data-screen="platform-tenant-create"]').waitFor();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.locator('main[data-screen="platform-tenants"]').waitFor();
  assert.equal(await page.locator('tbody tr:visible').count(), count);
  await page.evaluate(() => { document.activeElement?.blur(); window.scrollTo(0, 0); });
  await page.screenshot({ path: join(artifacts, 'directory-390-reduced-motion.png'), fullPage: true });
}, 'reduce'));
