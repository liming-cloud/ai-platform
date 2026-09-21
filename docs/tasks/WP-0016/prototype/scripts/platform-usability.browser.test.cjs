const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { url, loginConsole } = require('./browser-session.cjs');
let browser;
before(async () => { browser = await chromium.launch(); });
after(async () => browser?.close());
async function visit(action, width = 1440) {
  const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion: 'reduce' });
  const page = await context.newPage();
  page.setDefaultTimeout(4000);
  try { await loginConsole(page, 'platform'); await action(page); } finally { await context.close(); }
}

test('租户目录按真实行分页、切换大小、跳页、筛选重置和刷新恢复', () => visit(async page => {
  await page.evaluate(async () => {
    const { PROTOTYPE_STATE_KEY, createPrototypeStore } = await import('./prototype-state.js');
    const state = createPrototypeStore().getSnapshot();
    const seed = state.tenants[0];
    state.tenants = Array.from({ length: 43 }, (_, i) => ({ ...seed, id: `tenant-page-${i}`, code: `page-${i}`, organizationName: `分页租户 ${i}` }));
    localStorage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(state));
  });
  await page.goto(url('platform-tenants'));
  const visibleRows = page.locator('tbody tr:visible');
  assert.equal(await visibleRows.count(), 20);
  await page.getByLabel('每页条数', { exact: true }).selectOption('10');
  assert.equal(await visibleRows.count(), 10);
  await page.getByLabel('跳转页码', { exact: true }).fill('5');
  await page.getByRole('button', { name: '跳转', exact: true }).click();
  assert.equal(await visibleRows.count(), 3);
  await page.reload();
  assert.equal(await visibleRows.count(), 3);
  await page.getByLabel('搜索租户', { exact: true }).fill('分页租户 12');
  assert.equal(await visibleRows.count(), 1);
  assert.equal(new URL(page.url()).searchParams.get('page'), '1');
  await page.getByLabel('搜索租户', { exact: true }).fill('');
  assert.equal(await visibleRows.count(), 10);
  await page.getByLabel('跳转页码', { exact: true }).fill('999');
  await page.getByRole('button', { name: '跳转', exact: true }).click();
  assert.match(await page.locator('.pagination [role="alert"]').innerText(), /1–5/);
}));

test('共享事实块具有布局；处理记录多时分批加载且不挤出页面', () => visit(async page => {
  await page.goto(url('platform-health'));
  assert.equal(await page.locator('.page-object-line .status-tag').getAttribute('data-tone'), 'warning', '仍异常不能标记为绿色成功');
  assert.equal(await page.locator('.fact-list').first().evaluate(el => getComputedStyle(el).display), 'grid');
  await page.evaluate(async () => {
    const { PROTOTYPE_STATE_KEY, createPrototypeStore } = await import('./prototype-state.js');
    const state = createPrototypeStore().getSnapshot();
    for (const incident of state.health.incidents) incident.records = Array.from({ length: 35 }, (_, i) => ({ sequence: i + 1, time: '16:42', text: `合成处理记录 ${i + 1}` }));
    localStorage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(state));
  });
  await page.reload();
  await page.locator('.timeline-list li:visible').first().waitFor();
  assert.equal(await page.locator('.timeline-list li:visible').count(), 5);
  await page.getByRole('button', { name: '加载更早记录', exact: true }).click();
  assert.equal(await page.locator('.timeline-list li').count(), 5, '加载更早替换序号窗口，不累积全量 DOM');
  assert.match(await page.locator('.timeline-list').innerText(), /合成处理记录 30/);
  assert.doesNotMatch(await page.locator('.timeline-list').innerText(), /合成处理记录 35/);
  assert.ok(await page.locator('.timeline-list').evaluate(el => el.clientHeight <= 360));
  assert.equal(await page.locator('.pagination').count(), 0);
}));

test('租户设置分五个独立页面并固定租户，概览不混入保存表单', () => visit(async page => {
  await page.goto(url('platform-tenant-detail'));
  const nav = page.getByRole('navigation', { name: '租户设置', exact: true });
  await nav.waitFor();
  assert.equal(await nav.getByRole('button').count(), 5);
  assert.equal(await page.locator('[data-business-action="save-tenant-features"]').count(), 0);
  await nav.getByRole('button', { name: '资源配额', exact: true }).click();
  assert.equal(new URL(page.url()).searchParams.get('platformTenantId'), 'tenant-spruce');
  assert.equal(await page.locator('[data-edit-tenant-security]').count(), 0);
  await page.getByRole('button', { name: '编辑配额', exact: true }).click();
  await page.locator('#tenant-quota-knowledgeBases').fill('8');
  await page.getByRole('button', { name: '保存配额', exact: true }).click();
  await nav.getByRole('button', { name: '登录安全', exact: true }).click();
  await page.reload();
  assert.equal(new URL(page.url()).searchParams.get('platformTenantId'), 'tenant-spruce');
  assert.equal(await page.locator('[data-edit-tenant-quotas]').count(), 0);
  assert.equal(await page.locator('[data-edit-tenant-security]').count(), 1);
}));

test('平台只列平台账号；新租户直接创建管理员并从明确入口登录自己的空工作区', () => visit(async page => {
  await page.goto(url('platform-accounts'));
  await page.locator('[data-account-row]').first().waitFor();
  assert.equal(await page.locator('[data-account-row]').count(), 1);
  assert.doesNotMatch(await page.locator('.data-table').innerText(), /qiao.demo|认证版本/);
  await page.goto(url('platform-tenant-create'));
  await page.getByLabel('组织名称', { exact: true }).fill('北辰验收组织');
  await page.getByLabel('租户编码', { exact: true }).fill('north-review');
  await page.getByRole('button', { name: '下一步', exact: true }).click();
  await page.locator('#tenant-setup-knowledgeBases').fill('5');
  await page.getByRole('button', { name: '下一步', exact: true }).click();
  await page.getByLabel('管理员登录名', { exact: true }).fill('north.review');
  await page.getByLabel('初始密码', { exact: true }).fill('Initial-only-2026!');
  await page.getByLabel('再次输入', { exact: true }).fill('Initial-only-2026!');
  await page.getByRole('checkbox', { name: '确认创建租户及其管理员账号' }).check();
  await page.getByRole('button', { name: '创建租户', exact: true }).click();
  await page.locator('main[data-screen="platform-tenant-detail"]').waitFor();
  assert.match(await page.locator('.detail-facts').innerText(), /north-review/);
  assert.doesNotMatch(await page.evaluate(() => JSON.stringify(localStorage)), /Initial-only|passwordConfirmation/);
  await page.getByRole('link', { name: '租户管理员登录入口', exact: true }).click();
  assert.equal(await page.getByLabel('租户编码', { exact: true }).inputValue(), 'north-review');
  await page.getByLabel('登录名', { exact: true }).fill('north.review');
  await page.getByLabel('密码', { exact: true }).fill('Initial-only-2026!');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.locator('main[data-screen="tenant-overview"][data-state="success"]').waitFor();
  assert.match(await page.locator('.topbar').innerText(), /北辰验收组织/);
}));

test('模型目录分页读取真实表格行；来源失败附行跟随筛选隐藏', () => visit(async page => {
  await page.goto(url('platform-model-connections'));
  await page.locator('.pagination-summary').waitFor();
  assert.match(await page.locator('.pagination-summary').innerText(), /共 3 条/);
  await loginConsole(page, 'tenant');
  await page.goto(url('knowledge-sources'));
  await page.getByLabel('搜索数据来源', { exact: true }).fill('不存在的来源');
  assert.equal(await page.locator('tbody [data-source-row], tbody [data-directory-row-detail]').count(), 0);
  assert.match(await page.locator('tbody').innerText(), /没有匹配记录/);
  await page.goto(url('evaluation-dataset-detail'));
  await page.locator('.pagination-summary').waitFor();
  const total = Number((await page.locator('h2').allTextContents()).join(' ').match(/(\d+) 个冻结样本/)[1]);
  assert.ok(total >= 100);
  assert.ok((await page.locator('.pagination-summary').innerText()).includes(`共 ${total} 条`));
  assert.equal(await page.locator('tbody tr').count(), 20, '总数来自数据集，DOM 只挂载当前页');
  await page.getByLabel('跳转页码', { exact: true }).fill('5');
  await page.getByRole('button', { name: '跳转', exact: true }).click();
  assert.match(await page.locator('.pagination-summary').innerText(), /81–100/);
  assert.equal(await page.locator('tbody tr').count(), 20);
}));
