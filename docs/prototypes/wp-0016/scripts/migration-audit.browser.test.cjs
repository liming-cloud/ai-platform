const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { loginConsole, openForReview, url } = require('./browser-session.cjs');
const inventory = require('../page-inventory.json');
let browser;
before(async () => { browser = await chromium.launch(); });
after(async () => browser?.close());

test('全量管理页默认可见文案不暴露状态枚举或旧产品术语', async () => {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' });
  const page = await context.newPage();
  const findings = [];
  try {
    const pages = inventory.groups.filter(group => group.phase === 'M6' && group.shell === 'console')
      .flatMap(group => group.pages).filter(item => !['device-credentials', 'channel-binding'].includes(item.screen));
    for (const item of pages) {
      await openForReview(page, url(item.screen));
      await page.locator(`main[data-screen="${item.screen}"]`).waitFor();
      const visible = await page.locator('main').evaluate(main => {
        const walker = document.createTreeWalker(main, NodeFilter.SHOW_TEXT);
        const texts = [];
        while (walker.nextNode()) {
          const node = walker.currentNode, parent = node.parentElement;
          if (!parent || parent.closest('script,style,details,pre,code,option')) continue;
          const range = document.createRange(); range.selectNode(node);
          if (range.getClientRects().length) texts.push(node.textContent);
        }
        return texts.join(' ');
      });
      const tokens = visible.match(/\b(?:DRAFT|PUBLISHED|READY|OPENAI_COMPATIBLE|RECEIVED|SCANNING|EXTRACTING|CHUNKING|INDEXING)\b|口令|创建稳定助手/g);
      if (tokens) findings.push({ screen: item.screen, tokens: [...new Set(tokens)] });
    }
    assert.deepEqual(findings, [], '默认视图应使用中文业务语言；技术代码仅保留在渐进披露区域');
  } finally { await context.close(); }
});

// 此注入只验证独立浏览器内的规模布局，不用于证明真实服务授权或运维能力。
test('平台运行记录一万条仍是有界序号窗口，加载更早后刷新不回到最新', async () => {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  try {
    await loginConsole(page, 'platform');
    await page.evaluate(async () => {
      const { PROTOTYPE_STATE_KEY, createPrototypeStore } = await import('./prototype-state.js');
      const snapshot = createPrototypeStore().getSnapshot();
      const selected = snapshot.health.incidents.find(item => item.id === snapshot.health.selectedIncidentId);
      selected.records = Array.from({ length: 10000 }, (_, index) => ({ sequence: index + 1, time: '09:30', text: `合成处置记录-${index + 1}` }));
      localStorage.setItem(PROTOTYPE_STATE_KEY, JSON.stringify(snapshot));
    });
    await page.goto(url('platform-health'));
    const records = page.locator('[aria-label="处理记录"] > li');
    await records.first().waitFor();
    assert.ok(await records.count() <= 20, '隐藏多余行不能替代进入 DOM 前的有界读取');
    assert.match(await records.first().innerText(), /合成处置记录-10000/);
    await page.getByRole('button', { name: '加载更早记录', exact: true }).click();
    assert.ok(await records.count() <= 20);
    const older = await records.first().innerText();
    assert.doesNotMatch(older, /合成处置记录-10000/);
    await page.reload();
    await records.first().waitFor();
    assert.equal(await records.first().innerText(), older);
    assert.deepEqual(errors, []);
  } finally { await context.close(); }
});
