import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveProviderPreset } from '../provider-presets.js';
import { createPrototypeStore } from '../prototype-state.js';

test('厂商能力组合生成核实地址，不把重排当成统一 OpenAI 协议', () => {
  assert.equal(resolveProviderPreset({ providerId: 'DEEPSEEK', capability: 'CHAT' }).runtimeBaseUrl, 'https://api.deepseek.com');
  assert.equal(resolveProviderPreset({ providerId: 'DEEPSEEK', capability: 'EMBEDDING' }).ok, false);
  assert.equal(resolveProviderPreset({ providerId: 'SILICONFLOW', capability: 'RERANK' }).runtimeBaseUrl, 'https://api.siliconflow.cn/v1/rerank');
  assert.equal(resolveProviderPreset({ providerId: 'SILICONFLOW', capability: 'RERANK' }).runtimeProtocol, 'VENDOR_POST_PROCESSOR');
  assert.equal(resolveProviderPreset({ providerId: 'VOLCENGINE', capability: 'CHAT' }).runtimeBaseUrl, 'https://ark.cn-beijing.volces.com/api/v3');
});

test('百炼要求业务空间，按能力生成不同路径，拒绝主机注入', () => {
  assert.equal(resolveProviderPreset({ providerId: 'ALIYUN', capability: 'CHAT' }).reason, 'WORKSPACE_REQUIRED');
  const base = { providerId: 'ALIYUN', workspaceId: 'demo-space' };
  assert.equal(resolveProviderPreset({ ...base, capability: 'CHAT' }).runtimeBaseUrl, 'https://demo-space.cn-beijing.maas.aliyuncs.com/compatible-mode/v1');
  assert.equal(resolveProviderPreset({ ...base, capability: 'RERANK' }).runtimeBaseUrl, 'https://demo-space.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank');
  assert.equal(resolveProviderPreset({ ...base, workspaceId: 'a.evil.invalid/', capability: 'CHAT' }).ok, false);
});

test('连接保存重新校验厂商组合，不能篡改固定地址或借厂商名伪造能力', () => {
  const store = createPrototypeStore();
  const input = { name: 'DeepSeek 试验连接', providerId: 'DEEPSEEK', capability: 'CHAT', runtimeBaseUrl: 'https://api.deepseek.com', accessKey: 'synthetic-only' };
  assert.equal(store.createPlatformModelConnection({ ...input, capability: 'RERANK' }, 'account-platform-admin').ok, false);
  assert.equal(store.createPlatformModelConnection({ ...input, runtimeBaseUrl: 'https://other.example.invalid' }, 'account-platform-admin').ok, false);
  const created = store.createPlatformModelConnection(input, 'account-platform-admin');
  assert.equal(created.ok, true);
  assert.equal(created.connection.providerId, 'DEEPSEEK');
  assert.equal(created.connection.status, 'DRAFT');
  assert.doesNotMatch(JSON.stringify(store.getSnapshot()), /synthetic-only/);
});
