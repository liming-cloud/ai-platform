const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { url } = require('./browser-session.cjs');
let browser;
before(async () => { browser = await chromium.launch(); });
after(async () => browser?.close());

// 组件夹具仅挂载到隔离浏览器；不改动业务状态或用户 Chrome 数据。
test('大量授权候选搜索保留选择和禁用语义，不吞掉外层页面内容', async () => {
  const context = await browser.newContext();
  const page = await context.newPage();
  try {
    await page.goto(url('console-login'));
    const result = await page.evaluate(async () => {
      const { enhanceTaskComponents } = await import('./task-components.js');
      const root = document.createElement('section');
      root.innerHTML = `<form id="component-fixture"><fieldset class="tenant-disabled-fields"><h2>页面内容保留</h2><fieldset class="assistant-scope"><legend>授权对象</legend>${Array.from({length:60}, (_,i)=>`<label><input type="checkbox" name="grants" data-business-id="g${i}" value="g${i}"${[0,59].includes(i)?' checked':''}${i===59?' disabled':''}>候选-${i}</label>`).join('')}</fieldset></fieldset></form>`;
      document.body.append(root);
      enhanceTaskComponents(root);
      const form = root.querySelector('form'), search = root.querySelector('input[type=search]');
      const initial = new FormData(form).getAll('grants');
      const businessId = root.querySelector('input[type=checkbox]')?.dataset.businessId;
      search.value = '候选-35'; search.dispatchEvent(new Event('input', {bubbles:true}));
      const choice = root.querySelector('input[type=checkbox]');
      choice.checked = true; choice.dispatchEvent(new Event('change', {bubbles:true}));
      const searched = new FormData(form).getAll('grants').sort();
      search.value = ''; search.dispatchEvent(new Event('input', {bubbles:true}));
      return { initial, businessId, searched, restored: new FormData(form).getAll('grants').sort(), heading: root.querySelector('h2')?.textContent, mounted: root.querySelectorAll('input[type=checkbox]').length };
    });
    assert.equal(result.heading, '页面内容保留');
    assert.equal(result.businessId, 'g0');
    assert.deepEqual(result.initial, ['g0'], '禁用选项不得因离开搜索窗口变成可提交隐藏字段');
    assert.deepEqual(result.searched, ['g0','g35']);
    assert.deepEqual(result.restored, ['g0','g35']);
    assert.equal(result.mounted, 20);
  } finally { await context.close(); }
});

test('大量单选候选搜索后可键盘聚焦，选择与再次搜索保留已选值', async () => {
  const context = await browser.newContext();
  const page = await context.newPage();
  try {
    await page.goto(url('console-login'));
    await page.evaluate(async () => {
      const { enhanceTaskComponents } = await import('./task-components.js');
      const root = document.createElement('form');root.id='choice-fixture';
      root.innerHTML=`<label class="field"><span>模型版本</span><select name="model">${Array.from({length:200},(_,i)=>`<option value="m${i}"${i===150?' selected':''}>模型-${i}</option>`).join('')}</select></label><input type="password" name="secret">`;
      document.body.append(root);enhanceTaskComponents(root);
    });
    const form = page.locator('#choice-fixture');
    const search = form.getByRole('searchbox', {name:'搜索模型版本'});
    const select = form.getByRole('combobox', {name:'模型版本'});
    await search.fill('模型-199');
    assert.equal(await select.inputValue(),'m150');
    await search.press('Tab');
    assert.equal(await select.evaluate(el=>el===document.activeElement),true);
    // macOS 无头 Chromium 不驱动系统原生选项弹层；选择本身通过原生 select 事件验证。
    await select.selectOption('m199');
    assert.equal(await select.inputValue(),'m199');
    await search.fill('');
    assert.equal(await select.inputValue(),'m199');
    assert.ok(await select.locator('option').count()<=21);
    assert.equal(await form.locator('input[type=password]').count(),1);
  } finally { await context.close(); }
});
