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

test('方舟必须同时提供独立发现 AK/SK，失败不保存连接', () => {
  const store = createPrototypeStore();
  const input = { name: '方舟发现凭据校验', providerId: 'VOLCENGINE', capability: 'CHAT', runtimeBaseUrl: 'https://ark.cn-beijing.volces.com/api/v3', accessKey: 'synthetic-inference-only' };
  const before = JSON.stringify(store.getSnapshot());
  for (const credentials of [{}, { discoveryAccessKeyId: 'synthetic-access-only' }, { discoverySecretAccessKey: 'synthetic-secret-only' }, { discoveryAccessKeyId: ' ', discoverySecretAccessKey: 'synthetic-secret-only' }]) {
    const result = store.createPlatformModelConnection({ ...input, ...credentials }, 'account-platform-admin');
    assert.equal(result.ok, false);
    assert.equal(result.reason, 'DISCOVERY_CREDENTIALS_REQUIRED');
    assert.equal(JSON.stringify(store.getSnapshot()), before);
  }
});

test('方舟只保存发现认证配置状态，三项秘密均不进入返回值或持久化', () => {
  const storage = new Map();
  const store = createPrototypeStore({ getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) });
  const result = store.createPlatformModelConnection({ name: '方舟独立发现认证', providerId: 'VOLCENGINE', capability: 'CHAT', runtimeBaseUrl: 'https://ark.cn-beijing.volces.com/api/v3', accessKey: 'synthetic-inference-only', discoveryAccessKeyId: 'synthetic-access-only', discoverySecretAccessKey: 'synthetic-secret-only' }, 'account-platform-admin');
  assert.equal(result.ok, true);
  assert.equal(result.connection.discoveryProfile, 'VOLCENGINE_ARK_CONTROL_PLANE');
  assert.equal(result.connection.discoveryCredentialsConfigured, true);
  assert.doesNotMatch(JSON.stringify([result, store.getSnapshot(), [...storage]]), /synthetic-(inference|access|secret)-only/);
});

test('其他厂商拒绝方舟发现凭据，不从秘密字段推导发现协议', () => {
  const store = createPrototypeStore();
  const result = store.createPlatformModelConnection({ name: '错误发现凭据用途', providerId: 'DEEPSEEK', capability: 'CHAT', runtimeBaseUrl: 'https://api.deepseek.com', accessKey: 'synthetic-inference-only', discoveryAccessKeyId: 'synthetic-access-only', discoverySecretAccessKey: 'synthetic-secret-only' }, 'account-platform-admin');
  assert.equal(result.ok, false);
  assert.equal(result.reason, 'DISCOVERY_CREDENTIALS_UNEXPECTED');
});
