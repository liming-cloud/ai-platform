const { test } = require('node:test');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { loginConsole, url } = require('./browser-session.cjs');

// 全程通过真实原型控件操作；只读取快照核对对象，不注入已发布状态或绕过门禁。
for (const viewport of [
  { width: 390, height: 844, label: '手机' },
  { width: 1024, height: 900, label: '平板' },
  { width: 1440, height: 900, label: '桌面' },
  { width: 1366, height: 768, label: '低高度桌面' },
  // CSS 视口减半、输出像素密度翻倍，检查 1440×900 窗口放大至 200% 的等效重排。
  { width: 720, height: 450, scale: 2, label: '200% 等效重排' },
]) test(`${viewport.label}：从新建知识库到普通成员首次问答的连续样板`, { timeout: 90000 }, async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: viewport.width, height: viewport.height }, deviceScaleFactor: viewport.scale || 1, reducedMotion: 'reduce' });
  page.setDefaultTimeout(5000);
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const main = screen => page.locator(`main[data-screen="${screen}"][data-state="success"]`);
  const snapshot = () => page.evaluate(() => JSON.parse(localStorage.getItem('wp0016-business-demo-v1')));
  try {
    await loginConsole(page);
    await page.goto(url('knowledge-directory'));
    await page.getByRole('button', { name: '创建知识库', exact: true }).click();
    const create = page.locator('#create-knowledge-base-dialog');
    await create.locator('[name="name"]').fill('样板员工知识库');
    await create.locator('[name="description"]').fill('仅用于连续流程验收的合成员工制度。');
    await create.getByRole('button', { name: '确认创建', exact: true }).click();
    const row = page.locator('[data-kb-row]').filter({ hasText: '样板员工知识库' });
    await row.getByRole('button', { name: '进入', exact: true }).click();
    await main('knowledge-sources').waitFor();
    const knowledgeBaseId = new URL(page.url()).searchParams.get('knowledgeBaseId');
    assert.ok(knowledgeBaseId);
    await page.getByRole('button', { name: '添加数据', exact: true }).click();
    const source = page.locator('#source-add-form');
    await source.locator('[name="mode"]').selectOption('paste');
    await source.locator('[name="name"]').fill('样板年假制度');
    await source.locator('[name="text"]').fill('合成制度：员工年假经审批最多结转五天，结转假期应在下一年三月底前使用。');
    await source.getByRole('button', { name: '开始安全扫描', exact: true }).click();
    await main('source-detail').waitFor();
    for (const stage of ['SCANNING', 'EXTRACTING', 'EXTRACTED', 'CHUNKING', 'INDEXING', 'READY']) {
      await page.getByRole('button', { name: '刷新处理状态', exact: true }).click();
      assert.equal(await page.locator('[data-source-stage]').getAttribute('data-source-stage'), stage);
    }
    await page.locator('.object-navigation [data-nav-screen="index-versions"]').click();
    await page.getByRole('button', { name: '重建索引', exact: true }).click();
    await page.getByRole('button', { name: '开始全库重建', exact: true }).click();
    await main('index-detail').waitFor();
    for (const stage of ['CHUNKING', 'EMBEDDING', 'HYBRID_INDEXING', 'EVALUATING']) {
      await page.getByRole('button', { name: '刷新构建状态', exact: true }).click();
      assert.equal(await page.locator('[data-index-stage]').getAttribute('data-index-stage'), stage);
    }
    await page.getByRole('button', { name: '运行冻结评测', exact: true }).click();
    assert.equal(await page.locator('[data-index-status]').getAttribute('data-index-status'), 'READY_FOR_REVIEW');
    await page.getByRole('button', { name: '激活此版本', exact: true }).click();
    await page.locator('#index-dialog').getByRole('button', { name: '确认激活', exact: true }).click();
    assert.equal(await page.locator('[data-index-status]').getAttribute('data-index-status'), 'ACTIVE');
    const activeIndex = (await snapshot()).knowledgeBases.find(item => item.id === knowledgeBaseId).activeIndex;
    assert.ok(activeIndex);
    await page.locator('.admin-sidebar [data-nav-screen="assistants-directory"]').click();
    await page.getByRole('button', { name: '新建助手', exact: true }).click();
    await page.getByLabel('助手名称').fill('连续样板助手');
    await page.getByLabel('用途说明').fill('回答已审核的合成年假制度问题。');
    await page.getByLabel('关联知识库').fill('样板员工知识库');
    await page.getByRole('button', { name: '创建助手', exact: true }).click();
    await main('assistant-draft').waitFor();
    await page.locator('select[name="indexVersionId"]').selectOption(activeIndex);
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    await main('assistant-test').waitFor();
    await page.getByLabel('测试问题').fill('年假最多可以结转几天？');
    await page.getByRole('button', { name: '运行单题测试', exact: true }).click();
    await page.getByRole('button', { name: '加入评测集', exact: true }).click();
    await page.getByRole('button', { name: '运行冻结评测', exact: true }).click();
    await page.getByRole('button', { name: '请求发布', exact: true }).click();
    await page.getByRole('button', { name: '发布助手版本', exact: true }).click();
    await page.locator('#release-dialog').getByRole('button', { name: '确认发布', exact: true }).click();
    await main('assistant-overview').waitFor();
    const assistant = (await snapshot()).assistantDelivery.assistants.find(item => item.name === '连续样板助手');
    assert.equal(assistant.knowledgeBaseId, knowledgeBaseId);
    assert.equal(assistant.versions.find(item => item.id === assistant.publishedVersionId).frozen.indexVersionId, activeIndex);
    await page.locator('.admin-sidebar [data-nav-screen="tenant-members"]').click();
    await page.locator('[data-business-action="open-add-member"]').click();
    const member = page.locator('#add-member-form');
    await member.locator('[name="loginName"]').fill('sample.member');
    await member.getByRole('button', { name: '下一步', exact: true }).click();
    await member.locator(`[data-choice-id="${assistant.id}"]`).check();
    await member.getByRole('button', { name: '下一步', exact: true }).click();
    await member.locator('[name="password"]').fill('Initial-only-2026!');
    await member.locator('[name="passwordConfirmation"]').fill('Initial-only-2026!');
    await member.getByRole('button', { name: '创建成员', exact: true }).click();
    await main('tenant-member-account').waitFor();
    await page.goto(url('login'));
    await page.locator('#login-form [name="tenantCode"]').fill('spruce-demo');
    await page.locator('#login-form [name="username"]').fill('sample.member');
    await page.locator('#login-form [name="password"]').fill('Initial-only-2026!');
    await page.locator('#login-form [type="submit"]').click();
    await main('assistants').waitFor();
    await page.locator(`[data-assistant-id="${assistant.id}"]`).click();
    await main('conversation').waitFor();
    assert.ok(new URL(page.url()).searchParams.get('conversationId'));
    await page.locator('#question-input').fill('年假最多可以结转几天？');
    await page.locator('#question-input').press('Enter');
    await page.locator('[data-run-state="COMPLETED"]').waitFor({ timeout: 12000 });
    await page.reload();
    await main('conversation').waitFor();
    assert.match(await page.locator('#messages').innerText(), /年假/);
    assert.doesNotMatch(await page.evaluate(() => Object.values(localStorage).join('\n') + Object.values(sessionStorage).join('\n')), /Initial-only-2026!/);
    assert.deepEqual(errors, []);
  } catch (error) {
    error.message += `\n失败页面：${page.url()}\n${(await page.locator('main').innerText()).slice(0,2200)}`;
    throw error;
  } finally { await browser.close(); }
});
