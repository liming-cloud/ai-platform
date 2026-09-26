const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { loginConsole, url } = require('./browser-session.cjs');
let browser;
before(async () => { browser = await chromium.launch(); });
after(async () => browser?.close());
async function visit(action, motion = 'reduce') {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: motion });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  try { await loginConsole(page); await action(page); } finally { await context.close(); }
}

test('成员修改刷新可恢复，离开有确认，两个保存范围互不夹带', () => visit(async page => {
  await page.goto(url('tenant-members'));
  await page.locator('tbody tr').filter({ hasText: '沈言（合成）' }).getByRole('button', { name: '查看成员', exact: true }).click();
  const nav = page.getByRole('navigation', { name: '成员工作区', exact: true });
  await nav.getByRole('button', { name: '对话功能', exact: true }).click();
  const history = page.locator('[data-member-feature="ownHistory"]');
  await history.uncheck();
  await page.reload();
  await history.waitFor();
  assert.equal(await history.isChecked(), false);
  await nav.getByRole('button', { name: '可用助手', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '修改尚未保存', exact: true });
  await dialog.getByRole('button', { name: '保留当前', exact: true }).click();
  assert.equal(await history.isChecked(), false);
  await nav.getByRole('button', { name: '可用助手', exact: true }).click();
  await dialog.getByRole('button', { name: '放弃修改并离开', exact: true }).click();
  await page.locator('[data-choice-id="assistant-employee-service"]').uncheck();
  await page.getByRole('button', { name: '保存可用助手', exact: true }).click();
  const member = await page.evaluate(() => JSON.parse(localStorage.getItem('wp0016-business-demo-v1')).tenants.find(item => item.id === 'tenant-spruce').members.find(item => item.accountId === 'account-shen'));
  assert.equal(member.conversationFeatures.ownHistory, true, '保存助手不得夹带已放弃的功能修改');
  assert.deepEqual(member.assistantIds, []);
  await nav.getByRole('button', { name: '对话功能', exact: true }).click();
  await history.uncheck();
  await page.getByRole('button', { name: '保存对话功能', exact: true }).click();
  await page.reload();
  await history.waitFor();
  assert.equal(await history.isChecked(), false);
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('wp0016-business-demo-v1')).tenants.find(item => item.id === 'tenant-spruce').members.find(item => item.accountId === 'account-shen'));
  assert.deepEqual(saved.assistantIds, [], '保存功能不得恢复已删除的助手范围');
}));

test('手机向导第一步没有返回上一步，步骤指示与可访问标签准确', () => visit(async page => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(url('tenant-member-create'));
  await page.locator('#add-member-form').waitFor();
  assert.equal(await page.getByRole('button', { name: '上一步', exact: true }).count(), 0);
  await page.getByLabel('登录名', { exact: true }).fill('mobile.member');
  await page.getByRole('button', { name: '下一步', exact: true }).click();
  assert.equal(await page.locator('[aria-label="添加成员步骤"] [aria-current="step"]').innerText(), '2 · 使用权限');
  await page.getByRole('button', { name: '上一步', exact: true }).click();
  assert.equal(await page.getByLabel('登录名', { exact: true }).inputValue(), 'mobile.member');
  assert.ok(await page.locator('#add-member-form [type="submit"]').evaluate(el => el.getBoundingClientRect().bottom <= innerHeight));
}));

test('成员编辑与开户的禁用状态不可修改或提交，但对象导航仍可用', () => visit(async page => {
  for (const screen of ['tenant-member-features', 'tenant-member-assistants', 'tenant-member-create']) {
    await page.goto(url(screen, 'disabled') + '&memberAccountId=account-shen');
    await page.locator(`main[data-screen="${screen}"][data-state="disabled"]`).waitFor();
    assert.equal(await page.locator('[data-primary-action]').isDisabled(), true, screen);
    const input = page.locator('[data-member-feature], [data-choice-id], #add-member-form input').first();
    assert.equal(await input.isDisabled(), true, screen);
  }
}));

test('浏览器返回也使用已设计的离开弹窗，不丢失未保存内容', () => visit(async page => {
  const nativeDialogs=[];
  page.on('dialog', async dialog => { nativeDialogs.push(dialog.type()); await dialog.dismiss(); });
  await page.goto(url('tenant-members'));
  await page.locator('tbody tr').filter({ hasText:'沈言（合成）' }).getByRole('button',{name:'查看成员',exact:true}).click();
  await page.getByRole('navigation',{name:'成员工作区',exact:true}).getByRole('button',{name:'对话功能',exact:true}).click();
  await page.locator('[data-member-feature="ownHistory"]').uncheck();
  await page.goBack();
  const dialog=page.getByRole('dialog',{name:'修改尚未保存',exact:true});
  await dialog.waitFor();
  assert.deepEqual(nativeDialogs,[]);
  await dialog.getByRole('button',{name:'保留当前',exact:true}).click();
  assert.equal(await page.locator('[data-member-feature="ownHistory"]').isChecked(),false);
  assert.equal(new URL(page.url()).searchParams.get('screen'),'tenant-member-features');
}));

for (const reducedMotion of ['reduce', 'no-preference']) test(`动效样板可快速收敛、弹窗可取消并恢复焦点（${reducedMotion}）`, () => visit(async page => {
  await page.goto(url('knowledge-directory').replace('review=0', 'review=1'));
  await page.getByText('原型场景', { exact: true }).click();
  const motion = page.locator('.motion-samples');
  await motion.locator('[data-motion-fast]').click();
  assert.match(await motion.locator('[data-motion-content]').innerText(), /结果可继续/);
  assert.ok(await motion.locator('[data-motion-content]').evaluate(el => el.getAnimations().length <= 1), '快速操作取消旧动画，不排队');
  const trigger = motion.locator('[data-motion-dialog]');
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: '确认样板操作？', exact: true });
  await dialog.waitFor();
  assert.ok(await dialog.evaluate(el => parseFloat(getComputedStyle(el).paddingLeft) >= 20), '弹窗内容必须有设计后的内边距');
  await page.keyboard.press('Escape');
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(await trigger.evaluate(el => el === document.activeElement), true);
  if (reducedMotion === 'reduce') assert.equal(await motion.evaluate(el => el.getAnimations({ subtree: true }).length), 0);
  await motion.locator('[data-motion-reduce]').check();
  await motion.locator('[data-motion-tab="overview"]').click();
  assert.equal(await motion.evaluate(el => el.getAnimations({ subtree: true }).length), 0, '手动减少动效立即停止位移');
}, reducedMotion));
