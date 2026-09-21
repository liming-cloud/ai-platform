import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {boundedDirectoryMarkup} from '../object-workspaces.js';
import {objectNavigation} from '../workspace-journeys.js';

test('阶段三目录在分页前按状态过滤，不把其他状态算入总数',()=>{
  const rows=Array.from({length:1000},(_,i)=>`<tr data-status="${i%2?'ACTIVE':'DISABLED'}"><td>合成账户 ${i}</td></tr>`).join('');
  const html=boundedDirectoryMarkup(`<div class="table-shell"><table><tbody>${rows}</tbody></table><div class="pagination"></div></div>`,new URLSearchParams('status=ACTIVE&page=2'));
  assert.equal((html.match(/<tr /g)||[]).length,20);
  assert.doesNotMatch(html,/data-status="DISABLED"/);
  assert.match(html,/共 500 条/);
});
test('失败来源与说明附行作为一项保留，不能被当作空结果丢弃',()=>{
  const html=boundedDirectoryMarkup('<div class="table-shell"><table><tbody><tr data-source-row="failed"><td>失败文档</td></tr><tr data-directory-row-detail><td colspan="6">请重新提取</td></tr></tbody></table><div class="pagination"></div></div>');
  assert.ok(html.includes('data-source-row="failed"'));
  assert.match(html,/共 1 条/);
});
test('阶段三普通目录统一在 DOM 写入之前分页',()=>{
  const source=readFileSync(new URL('../prototype.js',import.meta.url),'utf8');
  assert.match(source,/root.innerHTML = boundedDirectoryMarkup\(markup,params\)/);
});
test('模型与评测对象有返回目录及同对象任务导航',()=>{
  assert.match(objectNavigation('model-configuration-detail'),/模型配置/);
  assert.match(objectNavigation('evaluation-run-detail'),/失败分析/);
});
test('平台运行记录不得把全量记录挂载后隐藏',()=>{
  const source=readFileSync(new URL('../full-platform.js',import.meta.url),'utf8');
  const body=source.slice(source.indexOf('function renderPlatformHealth'),source.indexOf('function recoveryActionLabel'));
  assert.doesNotMatch(body,/index >= 5/);
  assert.match(body,/recordWindow/);
});
