import { Button, DialogActions, DialogContent, Field, Input, Select } from "@fluentui/react-components";
import { type FormEvent, useLayoutEffect, useRef, useState } from "react";
import { ApiRequestError, type ControlPlaneClient, type PlatformProviderPresets } from "../../api/control-plane-client";

/** 只持有本次输入；厂商目录负责地址和协议，秘密不进入页面恢复状态。 */
export function PlatformProviderConnectionForm({ client, directory, onCancel, onCreated }: {
  client: ControlPlaneClient;
  directory: PlatformProviderPresets;
  onCancel: () => void;
  onCreated: (id: string) => void;
}) {
  const [providerId, setProviderId] = useState(directory.items[0]!.id);
  const [capability, setCapability] = useState(directory.items[0]!.capabilities[0]!);
  const [name, setName] = useState("");
  const [workspaceId, setWorkspaceId] = useState("");
  const [customEndpoint, setCustomEndpoint] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [discoveryAccessKeyId, setDiscoveryAccessKeyId] = useState("");
  const [discoverySecretAccessKey, setDiscoverySecretAccessKey] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [failure, setFailure] = useState("");
  const owner = useRef(true);
  const suggestedName = useRef("");
  const preset = directory.items.find(item => item.id === providerId)!;
  const variant = preset.variants.find(item => item.capability === capability)!;
  const validWorkspace = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(workspaceId);
  const endpoint = providerId === "CUSTOM" ? customEndpoint
    : variant.workspaceRequired && !validWorkspace ? "" : variant.endpointTemplate.replace("{workspaceId}", workspaceId);
  const arkDiscovery = variant.discoveryProfile === "VOLCENGINE_ARK_CONTROL_PLANE";

  // 卸载提交时同步失效，迟到请求不能抢在被动清理前导航到旧主体的对象。
  useLayoutEffect(() => { owner.current = true; return () => { owner.current = false; }; }, []);

  function clearSecrets() {
    setApiKey(""); setDiscoveryAccessKeyId(""); setDiscoverySecretAccessKey("");
  }

  function select(nextProvider: string, nextCapability: string) {
    const next = directory.items.find(item => item.id === nextProvider)!;
    const selected = next.capabilities.includes(nextCapability) ? nextCapability : next.capabilities[0]!;
    const suggestion = next.id === "CUSTOM" ? "" : `${next.name} · ${capabilityLabel(selected)}`;
    if (!name || name === suggestedName.current) setName(suggestion);
    suggestedName.current = suggestion;
    if (providerId !== nextProvider) { setWorkspaceId(""); setCustomEndpoint(""); }
    setProviderId(nextProvider); setCapability(selected); clearSecrets(); setFailure("");
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    // 旧表单即使仍被引用也不能提交；正式后端继续独立复核启动配置。
    if (!directory.writesAvailable) { clearSecrets(); return; }
    let message = "";
    if (!name.trim() || name.trim().length > 80) message = "请输入不超过 80 个字符的连接名称。";
    else if (variant.workspaceRequired && !validWorkspace) message = "业务空间 ID 仅允许小写字母、数字和连字符。";
    else if (!endpoint || !/^https:\/\//.test(endpoint)) message = "运行地址必须是公网 HTTPS。";
    else if (apiKey.length < 8) message = "请输入至少 8 个字符的一次性访问密钥。";
    else if (arkDiscovery && (!discoveryAccessKeyId.trim() || !discoverySecretAccessKey.trim())) {
      message = "请填写独立模型发现 AK 和 SK，不能使用推理访问密钥代替。";
    }
    if (message) { clearSecrets(); setFailure(message); return; }
    const input = {
      providerPresetId: preset.id, providerPresetVersion: directory.version,
      capability: capability as "CHAT" | "EMBEDDING" | "RERANK", displayName: name.trim(), endpoint,
      region: preset.region, providerKind: "OPENAI_COMPATIBLE" as const, discoveryProfile: variant.discoveryProfile,
      authenticationType: "API_KEY" as const, apiKey,
      ...(variant.workspaceRequired ? { workspaceId } : {}),
      ...(arkDiscovery ? { discoveryAccessKeyId, discoverySecretAccessKey } : {}),
    };
    // 提交即清空可见输入；关闭、身份替换后的迟到响应不得导航或恢复秘密。
    clearSecrets(); setFailure(""); setSubmitting(true);
    try {
      const created = await client.createPlatformProviderConnection(input);
      if (owner.current) onCreated(created.id);
    } catch (error) {
      if (owner.current) setFailure(error instanceof ApiRequestError ? error.message : "请求未完成，请稍后重试。");
    } finally { if (owner.current) setSubmitting(false); }
  }

  return <form className="foundation-dialog-form platform-connection-form" noValidate onSubmit={event => void submit(event)}>
    <DialogContent>
      <div className="platform-connection-form__grid">
        <Field label="厂商类型"><Select name="providerPreset" value={providerId} disabled={submitting || !directory.writesAvailable}
          onChange={(_, data) => select(data.value, capability)}>
          {directory.items.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
        </Select></Field>
        <Field label="运行能力"><Select name="capability" value={capability} disabled={submitting || !directory.writesAvailable}
          onChange={(_, data) => select(providerId, data.value)}>
          {preset.capabilities.map(value => <option key={value} value={value}>{capabilityLabel(value)}</option>)}
        </Select></Field>
      </div>
      <Field label="连接名称"><Input name="displayName" value={name} maxLength={80} required autoComplete="off"
        disabled={submitting || !directory.writesAvailable} onChange={(_, data) => setName(data.value)} /></Field>
      {variant.workspaceRequired ? <Field label="业务空间 ID"><Input value={workspaceId} maxLength={63} required
        autoComplete="off" placeholder="填写百炼控制台的业务空间 ID" disabled={submitting || !directory.writesAvailable}
        onChange={(_, data) => { setWorkspaceId(data.value); clearSecrets(); setFailure(""); }} /></Field> : null}
      <div className="platform-connection-form__summary"><span>{preset.regionLabel}</span>
        <span>{variant.runtimeProtocol === "VENDOR_POST_PROCESSOR" ? "厂商重排适配 · 非统一 OpenAI 标准" : "OpenAI 兼容协议"}</span></div>
      <Field label="运行地址" hint={{ className: "platform-connection-address-hint", children: <>
        {/* 固定预设地址完整换行；自定义输入不复制，保存值仍只由原字段持有。 */}
        <span data-provider-address-value hidden={providerId === "CUSTOM" || !endpoint}>{providerId !== "CUSTOM" ? endpoint : ""}</span>
        <span data-provider-address-help>{variant.workspaceRequired && !validWorkspace
          ? "先填写业务空间 ID，自动生成北京地域地址。" : providerId === "CUSTOM"
            ? "填写服务商提供的公网 HTTPS 地址；重排填写完整端点。"
            : "地址由已核实的厂商预设生成；如需其他区域，请使用自定义连接。"}</span>
      </> }}>
        <Input type="url" name="endpoint" value={endpoint} required readOnly={providerId !== "CUSTOM"}
          placeholder="https://models.example.invalid/v1" disabled={submitting || !directory.writesAvailable}
          onChange={(_, data) => setCustomEndpoint(data.value)} />
      </Field>
      <Field label="一次性访问密钥" hint={arkDiscovery
        ? "仅用于推理调用；模型发现需要下方独立 AK/SK。选择厂商或能力后重新填写，保存后不会回显。"
        : "选择厂商或能力后重新填写，保存后不会回显。"}>
        <Input type="password" value={apiKey} required minLength={8} maxLength={4096} autoComplete="new-password"
          disabled={submitting || !directory.writesAvailable} onChange={(_, data) => setApiKey(data.value)} />
      </Field>
      {arkDiscovery ? <section className="platform-connection-form__discovery" role="group" aria-labelledby="platform-discovery-credentials-title">
        <h3 id="platform-discovery-credentials-title">独立模型发现认证</h3>
        <p>仅用于方舟控制面模型发现，与推理访问密钥分别管理。</p>
        <Field label="模型发现 Access Key ID"><Input type="password" value={discoveryAccessKeyId} required maxLength={4096}
          autoComplete="new-password" disabled={submitting || !directory.writesAvailable} onChange={(_, data) => setDiscoveryAccessKeyId(data.value)} /></Field>
        <Field label="模型发现 Secret Access Key"><Input type="password" value={discoverySecretAccessKey} required maxLength={4096}
          autoComplete="new-password" disabled={submitting || !directory.writesAvailable} onChange={(_, data) => setDiscoverySecretAccessKey(data.value)} /></Field>
      </section> : null}
      <p className="form-feedback platform-connection-feedback--danger" role="alert">{failure}</p>
    </DialogContent>
    <DialogActions><Button appearance="secondary" onClick={() => { clearSecrets(); onCancel(); }}>取消</Button>
      <Button appearance="primary" type="submit" disabled={submitting || !directory.writesAvailable}>{submitting ? "正在保存" : "保存连接草稿"}</Button></DialogActions>
  </form>;
}

function capabilityLabel(value: string): string {
  return ({ CHAT: "对话模型", EMBEDDING: "向量模型", RERANK: "重排序模型" } as Record<string, string>)[value] ?? value;
}
