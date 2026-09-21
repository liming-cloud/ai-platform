const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { loginConsole, url } = require('./browser-session.cjs');

let browser;
before(async () => { browser = await chromium.launch(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}); });
after(async () => browser?.close());

async function visit(width, action, options = {}) {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 900 }, reducedMotion: 'reduce', ...options });
  const page = await context.newPage();
  try { await loginConsole(page, 'platform'); await action(page); } finally { await context.close(); }
}

async function metrics(locator) {
  return locator.evaluate(element => {
    const box = element.getBoundingClientRect(), style = getComputedStyle(element);
    return { x: box.x, y: box.y, width: box.width, height: box.height, fontSize: parseFloat(style.fontSize), lineHeight: parseFloat(style.lineHeight) };
  });
}

function near(actual, expected, message) { assert.ok(Math.abs(actual - expected) <= 1, `${message}：期望 ${expected}px，实际 ${actual}px`); }

async function openConnectionDialog(page) {
  await page.goto(url('platform-model-connections'));
  await page.getByRole('button', { name: '新增供应商连接', exact: true }).click();
  return page.locator('#platform-connection-dialog');
}

// 每个测试使用独立浏览器上下文；尺寸夹具只改变当前页面，不修改持久化数据或用户会话。
for (const width of [1024, 1440]) {
  test(`${width} 桌面普通操作与输入为36px，表格操作为32px`, () => visit(width, async page => {
    await page.goto(url('platform-accounts'));
    const primary = await metrics(page.getByRole('button', { name: '创建平台账户', exact: true }));
    const input = await metrics(page.locator('main .toolbar .input').first());
    const rowAction = await metrics(page.locator('[data-account-row] .button:visible').first());
    near(primary.height, 36, '普通按钮高度');
    near(input.height, 36, '普通输入高度');
    near(rowAction.height, 32, '表格紧凑操作高度');
    near(primary.fontSize, 14, '按钮字号');
    near(rowAction.fontSize, 14, '行内按钮字号');
    near(input.fontSize, 16, '输入字号');
    const tableTextSize = await page.locator('.data-table').evaluate(element => parseFloat(getComputedStyle(element).fontSize));
    assert.ok(tableTextSize >= 15, '压缩操作控件不得同步压缩目录正文');
  }));

  test(`${width} 分页按钮、每页大小与跳转输入同高且数字页码为方形`, () => visit(width, async page => {
    await page.goto(url('platform-accounts'));
    const controls = await page.locator('.pagination button, .pagination input, .pagination select').all();
    assert.ok(controls.length >= 5, '必须检查完整分页控件而非单一页码');
    for (const control of controls) near((await metrics(control)).height, 32, '分页控件高度');
    const current = await metrics(page.locator('.pagination [aria-current="page"]'));
    near(current.width, current.height, '数字页码宽高');
  }));

  test(`${width} 配对字段长标签与不等长帮助文本仍保持输入基线`, () => visit(width, async page => {
    const dialog = await openConnectionDialog(page);
    await dialog.locator('.wizard-field-grid').first().evaluate(async grid => {
      const fields = [...grid.querySelectorAll('.field')];
      fields[0].querySelector('span').textContent = '选择当前供应商所属厂商类型以及受支持的运行服务范围';
      const help = document.createElement('small'); help.className = 'field-hint'; help.textContent = '该说明用于验证帮助内容换行之后，另一列控件仍与当前控件对齐。'.repeat(3); fields[0].append(help);
      const { enhanceTaskComponents } = await import('./task-components.js'); enhanceTaskComponents(document.querySelector('main'));
    });
    const provider = await metrics(dialog.getByRole('combobox', { name: '厂商类型', exact: true }));
    const capability = await metrics(dialog.getByRole('combobox', { name: '运行能力', exact: true }));
    near(provider.y, capability.y, '换行标签后的控件顶部');
    near(provider.height, capability.height, '配对输入盒高');
  }));

  test(`${width} 运行状态处理按钮不被输入框帮助文本拉到底部`, () => visit(width, async page => {
    await page.goto(url('platform-health'));
    await page.locator('#health-action-form .field').evaluate(async field => {
      const help = document.createElement('small'); help.className = 'field-hint'; help.textContent = '请仅记录脱敏后的处理措施，不填写租户问答正文、凭据或个人信息。'.repeat(2); field.append(help);
      const { enhanceTaskComponents } = await import('./task-components.js'); enhanceTaskComponents(document.querySelector('main'));
    });
    const input = await metrics(page.locator('#health-action'));
    const action = await metrics(page.locator('#health-action-form button[type="submit"]'));
    near(input.y, action.y, '带帮助文本的输入与操作顶部');
    near(input.height, action.height, '同组输入与操作高度');
  }));
}

for (const width of [390, 1024, 1440]) {
  test(`${width} 弹窗操作区留白一致且内容不横向溢出`, () => visit(width, async page => {
    const dialog = await openConnectionDialog(page);
    const layout = await dialog.evaluate(element => {
      const actions = element.querySelector('.dialog-actions'), actionStyle = getComputedStyle(actions), box = element.getBoundingClientRect();
      const button = actions.querySelector('button:last-child'), actionBox = button.getBoundingClientRect();
      return { gap: parseFloat(actionStyle.columnGap), padding: parseFloat(actionStyle.paddingTop), trailingInset: box.right - actionBox.right, overflow: element.scrollWidth - element.clientWidth, viewportOverflow: document.documentElement.scrollWidth - innerWidth };
    });
    near(layout.gap, 8, '操作间距'); near(layout.padding, 16, '操作区上边距');
    near(layout.trailingInset, width === 390 ? 17 : 25, '弹窗内容右侧内边距（含边框）');
    assert.ok(layout.overflow <= 1 && layout.viewportOverflow <= 1, '弹窗与页面不得横向溢出');
  }));

  test(`${width} 登录保持44px点击高度`, async () => {
    const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion: 'reduce' });
    const page = await context.newPage();
    try {
      for (const screen of ['console-login', 'tenant-login', 'login']) {
        await page.goto(url(screen));
        const controls = await page.locator('#console-login-form .input, #console-login-form .select, #console-login-form button[type="submit"], #login-form .input, #login-form button[type="submit"]').all();
        for (const control of controls) if (await control.isVisible()) assert.ok((await metrics(control)).height >= 44, `${screen} 登录控件不可低于44px`);
      }
    } finally { await context.close(); }
  });
}

test('390 手机保留44px触控目标，次操作不无差别占满整行', () => visit(390, async page => {
  const dialog = await openConnectionDialog(page);
  const secondary = dialog.getByRole('button', { name: '取消', exact: true });
  const primary = dialog.getByRole('button', { name: '保存连接草稿', exact: true });
  const cancel = await metrics(secondary), save = await metrics(primary), actions = await metrics(dialog.locator('.dialog-actions'));
  assert.ok(cancel.height >= 44 && save.height >= 44, '手机操作点击目标至少44px');
  assert.ok(cancel.width < actions.width * 0.5, '取消应使用内容宽度，不与主操作平均撑满整个操作栏');
  for (const control of await dialog.locator('.input:visible, .select:visible').all()) assert.ok((await metrics(control)).height >= 44, '手机输入不可低于44px');
}));

test('1024粗指针设备保留44px触控目标', () => visit(1024, async page => {
  await page.goto(url('platform-accounts'));
  assert.equal(await page.evaluate(() => matchMedia('(pointer: coarse)').matches), true, '夹具必须实际使用粗指针媒体条件');
  const controls = await page.locator('main .button:visible, main .input:visible, main .select:visible').all();
  for (const control of controls) assert.ok((await metrics(control)).height >= 44, '粗指针环境控件至少44px');
}, { hasTouch: true }));

test('200%文字放大后主要按钮文字不裁切、输入行高可容纳且页面无横向溢出', () => visit(1440, async page => {
  const dialog = await openConnectionDialog(page);
  await dialog.evaluate(element => {
    // 明确模拟文字放大，不以deviceScaleFactor冒充字体可读性验证。
    const sizes = [...element.querySelectorAll('*'), element].map(node => ({ node, size: parseFloat(getComputedStyle(node).fontSize), line: parseFloat(getComputedStyle(node).lineHeight) }));
    for (const { node, size, line } of sizes) { node.style.fontSize = `${size * 2}px`; if (Number.isFinite(line)) node.style.lineHeight = `${line * 2}px`; }
  });
  const overflow = await dialog.evaluate(element => ({ own: element.scrollWidth - element.clientWidth, page: document.documentElement.scrollWidth - innerWidth }));
  assert.ok(overflow.own <= 1 && overflow.page <= 1, '文字放大不得产生横向滚动');
  for (const control of await dialog.locator('button:visible, .input:visible, .select:visible').all()) {
    const size = await metrics(control);
    // 原生 select 的计算行高可能为 normal；此时只检查可验证的字形高度，不将 NaN 当成布局失败。
    const contentHeight = Math.max(size.fontSize, Number.isFinite(size.lineHeight) ? size.lineHeight : 0);
    assert.ok(size.height >= contentHeight + 2, `放大后的控件高度${size.height}px必须容纳${contentHeight}px文字及边框`);
  }
}));
