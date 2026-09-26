/** 2026-09-07 核实的文本能力预设；不是供应商完整能力目录，不发出网络请求。来源见 PROVIDER-PRESETS.md。 */
export const PROVIDER_PRESET_VERSION = '2026-09-07';
export const PROVIDER_PRESETS = Object.freeze([
  { id: 'CUSTOM', name: '自定义连接', region: '自行配置', capabilities: ['CHAT', 'EMBEDDING', 'RERANK'] },
  { id: 'DEEPSEEK', name: 'DeepSeek', region: '厂商默认', capabilities: ['CHAT'], base: 'https://api.deepseek.com' },
  { id: 'ALIYUN', name: '阿里云百炼', region: '中国北京', capabilities: ['CHAT', 'EMBEDDING', 'RERANK'] },
  { id: 'SILICONFLOW', name: '硅基流动', region: '中国区', capabilities: ['CHAT', 'EMBEDDING', 'RERANK'], base: 'https://api.siliconflow.cn/v1' },
  { id: 'VOLCENGINE', name: '火山方舟', region: '中国北京', capabilities: ['CHAT'], base: 'https://ark.cn-beijing.volces.com/api/v3' },
].map(preset => Object.freeze({ ...preset, capabilities: Object.freeze(preset.capabilities) })));

/** 生成可观察的非秘密连接参数；未知组合与业务空间主机注入失败关闭。 */
export function resolveProviderPreset({ providerId = 'CUSTOM', capability = 'CHAT', workspaceId = '', runtimeBaseUrl = '' } = {}) {
  const preset = PROVIDER_PRESETS.find(item => item.id === providerId);
  if (!preset || !preset.capabilities.includes(capability)) return { ok: false, reason: 'PROVIDER_CAPABILITY_INVALID' };
  let endpoint = preset.base || runtimeBaseUrl;
  if (providerId === 'ALIYUN') {
    if (capability === 'EMBEDDING') {
      endpoint = 'https://dashscope.aliyuncs.com/compatible-mode/v1';
    } else {
      if (typeof workspaceId !== 'string' || !/^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(workspaceId)) return { ok: false, reason: 'WORKSPACE_REQUIRED' };
      const path = capability === 'RERANK' ? '/api/v1/services/rerank/text-rerank/text-rerank' : '/compatible-mode/v1';
      endpoint = `https://${workspaceId}.cn-beijing.maas.aliyuncs.com${path}`;
    }
  } else if (providerId === 'SILICONFLOW' && capability === 'RERANK') {
    endpoint += '/rerank';
  }
  return { ok: true, providerId, providerName: preset.name, region: preset.region, runtimeBaseUrl: endpoint, runtimeProtocol: capability === 'RERANK' ? 'VENDOR_POST_PROCESSOR' : 'OPENAI_COMPATIBLE', version: PROVIDER_PRESET_VERSION };
}
