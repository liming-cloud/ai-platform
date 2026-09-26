const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { loginConsole, url } = require('./browser-session.cjs');
let browser;
before(async () => { browser = await chromium.launch(); });
after(async () => browser?.close());

// 仅在独立浏览器上下文注入合成规模数据；权限与业务闭环测试不使用此捷径。
async function stress(action) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  try {
    await loginConsole(page);
    const ids = await page.evaluate(async () => {
      const { PROTOTYPE_STATE_KEY, createPrototypeStore } = await import('./prototype-state.js');
      const { createStressSnapshot } = await import('./stress-fixtures.js');
      const snapshot = createStressSnapshot(createPrototypeStore().getSnapshot());
      localStorage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
      return { knowledgeBaseId: snapshot.selectedKnowledgeBaseId, sourceId: snapshot.selectedKnowledgeSourceId, memberAccountId: snapshot.selectedMemberAccountId };
    });
    await action(page, ids);
  } finally { await context.close(); }
}
const route = (screen, ids, extra = {}) => {
  const target = new URL(url(screen));
  for (const [key, value] of Object.entries({ ...ids, ...extra })) target.searchParams.set(key, value);
  return target.href;
};

test('1000 文档只挂载当前页，支持每页大小、跳页、搜索和刷新恢复', () => stress(async (page, ids) => {
  await page.goto(route('knowledge-sources', ids));
  await page.locator('tbody tr').first().waitFor();
  assert.equal(await page.locator('tbody tr').count(), 20, '隐藏 980 行不等于有界 DOM');
  assert.match(await page.locator('.pagination').innerText(), /共 1000 条/);
  await page.getByLabel('每页条数', { exact: true }).selectOption('10');
  assert.equal(await page.locator('tbody tr').count(), 10);
  await page.getByLabel('跳转页码', { exact: true }).fill('100');
  await page.getByLabel('跳转页码', { exact: true }).press('Enter');
  assert.equal(await page.getByLabel('跳转页码', { exact: true }).evaluate(el => el === document.activeElement), true);
  assert.match(await page.locator('tbody').innerText(), /合成文档01000/);
  await page.reload();
  await page.locator('tbody tr').first().waitFor();
  assert.equal(await page.locator('tbody tr').count(), 10);
  assert.match(await page.locator('tbody').innerText(), /合成文档01000/);
  await page.getByLabel('搜索数据来源', { exact: true }).fill('合成文档00555');
  assert.equal(await page.locator('tbody tr').count(), 1);
  assert.equal(new URL(page.url()).searchParams.get('page'), '1');
  await page.getByLabel('搜索数据来源', { exact: true }).fill('无匹配内容');
  assert.equal(await page.locator('tbody [data-source-row]').count(), 0);
  assert.match(await page.locator('tbody').innerText(), /没有匹配记录/);
  assert.match(await page.locator('.pagination').innerText(), /0–0/);
}));

test('1000 普通成员目录保持有界，200 助手候选搜索与键盘选择不丢焦点', () => stress(async (page, ids) => {
  await page.goto(route('tenant-members', ids));
  await page.locator('tbody tr').first().waitFor();
  assert.equal(await page.locator('tbody tr').count(), 20);
  assert.match(await page.locator('.pagination').innerText(), /共 1001 条/);
  await page.goto(route('tenant-member-assistants', ids));
  const search = page.locator('[data-picker-search]');
  await search.waitFor();
  assert.equal(await page.locator('[data-choice-id]').count(), 20);
  await search.fill('合成助手00200');
  const choice = page.locator('[data-choice-id="stress-助手-200"]');
  await choice.focus();
  await choice.press('Space');
  assert.equal(await choice.isChecked(), true);
  assert.equal(await choice.evaluate(el => el === document.activeElement), true, '选择后必须保留键盘焦点');
  await search.fill('合成助手00001');
  await page.locator('[data-choice-id="stress-助手-1"]').check();
  await search.fill('合成助手00200');
  assert.equal(await choice.isChecked(), true, '筛选不应删除之前已选助手');
  await page.getByRole('button', { name: '保存可用助手', exact: true }).click();
  await page.reload();
  await page.locator('[data-picker-summary]').waitFor();
  assert.match(await page.locator('[data-picker-summary]').innerText(), /已选 2 个/);
}));

test('10000 处理记录使用序号窗口，100 文档版本只挂载当前页', () => stress(async (page, ids) => {
  await page.goto(route('source-detail', ids));
  const nav = page.getByRole('navigation', { name: '文档工作区', exact: true });
  await nav.getByRole('button', { name: '处理记录', exact: true }).click();
  const records = page.locator('[data-event-sequence]');
  assert.equal(await records.count(), 20);
  assert.equal(await records.last().getAttribute('data-event-sequence'), '10000');
  await page.getByRole('button', { name: '加载更早记录', exact: true }).click();
  assert.equal(await records.count(), 20);
  assert.equal(await records.last().getAttribute('data-event-sequence'), '9980');
  await page.reload();
  await records.first().waitFor();
  assert.equal(await records.last().getAttribute('data-event-sequence'), '9980');
  await page.getByRole('navigation', { name: '文档工作区', exact: true }).getByRole('button', { name: '版本', exact: true }).click();
  assert.equal(await page.locator('tbody tr').count(), 20);
  assert.match(await page.locator('.pagination').innerText(), /共 100 条/);
  await page.getByLabel('跳转页码', { exact: true }).fill('5');
  await page.getByRole('button', { name: '跳转', exact: true }).click();
  await page.getByRole('button', { name: '查看内容', exact: true }).last().click();
  assert.ok(new URL(page.url()).searchParams.get('documentVersion'));
  assert.match(await page.locator('.document-preview').innerText(), /合成/);
}));
