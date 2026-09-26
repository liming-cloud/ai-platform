import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { workspaceJourney } from '../workspace-journeys.js';

test('处理任务公共输出使用中文阶段', () => {
  const snapshot = { tenants: [], selectedKnowledgeBaseId: 'kb', knowledgeBases: [{ id: 'kb', name: '合成知识库', sources: [{ id: 'source', name: '合成资料', stage: 'READY' }] }] };
  const output = workspaceJourney('processing-quality', snapshot, { escapeHtml: String, statusTag: String });
  assert.doesNotMatch(output.body, />READY</);
  assert.match(output.body, /已就绪/);
});

test('弹窗容器自身承担统一内边距', () => {
  const css = readFileSync(new URL('../prototype.css', import.meta.url), 'utf8');
  assert.match(css, /\.dialog-surface\s*\{[^}]*padding:\s*var\(--space-6\)/);
});

test('成员目录不再拼接长权限编辑区', () => {
  const source = readFileSync(new URL('../full-platform.js', import.meta.url), 'utf8');
  const body = source.slice(source.indexOf('function renderTenantMembers('), source.indexOf('function renderDocumentDetail('));
  assert.doesNotMatch(body, /\$\{renderMemberFeatureEditor\(/);
});
