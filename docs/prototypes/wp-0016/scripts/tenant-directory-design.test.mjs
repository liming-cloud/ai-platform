import { boundedDirectoryMarkup } from '../object-workspaces.js';
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { renderInventoryPage } from '../full-platform.js';

const read = name => JSON.parse(readFileSync(new URL('../' + name, import.meta.url), 'utf8'));
const inventory = read('page-inventory.json');
const group = inventory.groups.find(item => item.pages.some(page => page.screen === 'platform-tenants'));
const page = group.pages.find(item => item.screen === 'platform-tenants');
const fixture = read('page-fixtures.json').pages[page.screen];

function directory(tenants) {
  return renderInventoryPage(page, group, fixture, 'success', { displayName: '合成验证范围' }, { tenants, accounts: [] });
}

const tenant = (status = 'active', members = []) => ({
  id: 'directory-design-test', organizationName: '合成验证租户', code: 'directory-test',
  region: '无事实源的演示区域', status, members,
});

test('目录不把演示驻留区域或未核实异常数量展示为事实', () => {
  const html = directory([tenant()]);
  assert.doesNotMatch(html, /驻留区域|无事实源的演示区域|安全边界异常 0|合成生命周期数据/);
  assert.match(html, /租户目录<\/strong> · 生命周期摘要/);
  assert.match(html, /安全边界异常：尚未核实/);
});

test('目录筛选和行状态采用三个生命周期名称', () => {
  const html = directory(['provisioning', 'active', 'suspended'].map(status => tenant(status)));
  for (const [value, label] of [['provisioning', '待启用'], ['active', '活动'], ['suspended', '已暂停']]) {
    assert.match(html, new RegExp(`<option value="${value}">${label}</option>`));
    assert.match(html, new RegExp(`data-label="状态">[\\s\\S]*?${label}`));
  }
  assert.doesNotMatch(html, /已停用/);
  assert.match(html, /data-directory-status aria-label="状态"/);
});

test('没有管理员资格的租户不能显示已分配，停用资格仍归属原租户', () => {
  const missing = directory([tenant('active', [{ role: 'ordinaryMember', status: 'active' }])]);
  assert.match(missing, /data-label="租户管理员">未分配<\/td>/);
  const assigned = directory([tenant('active', [{ role: 'tenantAdministrator', status: 'suspended' }])]);
  assert.match(assigned, /data-label="租户管理员">已分配<\/td>/);
});

test('目录只保留建立入口、查看详情与原分页入口', () => {
  const html = directory([tenant()]);
  assert.match(html, /建立新租户/);
  assert.match(html, /data-nav-screen="platform-tenant-detail"/);
  assert.match(html, /aria-label="租户目录分页"/);
  assert.match(html, /按组织名称或租户编码搜索/);
  assert.match(html, /maxlength="64"/);
  assert.doesNotMatch(html, /id="page-dialog"/);
  assert.doesNotMatch(html, /data-transition-tenant|data-replace-tenant-administrator/);
});

test('未知状态不伪装为活动，租户内容继续转义', () => {
  const html = directory([{ ...tenant('unrecognized'), organizationName: '<script>目录测试</script>' }]);
  assert.match(html, /data-label="状态">[\s\S]*?状态未知/);
  assert.match(html, /&lt;script&gt;目录测试&lt;\/script&gt;/);
  assert.doesNotMatch(html, /<script>目录测试<\/script>/);
});


test('四列租户目录空结果只跨实际四列，不凭空扩展表格', () => {
  const html = boundedDirectoryMarkup(directory([]));
  assert.match(html, /colspan="4"/);
  assert.doesNotMatch(html, /colspan="20"/);
});
