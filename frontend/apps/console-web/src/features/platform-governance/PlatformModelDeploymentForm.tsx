import { Button, DialogTitle } from "@fluentui/react-components";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { ApiRequestError, type ControlPlaneClient, type PageResult, type PlatformDeployableModel, type TenantLifecycleSummary } from "../../api/control-plane-client";
import { platformDeploymentInput } from "./platform-deployment-input";

/** 本次表单拥有所有暂存声明，关闭即卸载；只有正式保存返回的精确部署才能进入详情。 */
export function PlatformModelDeploymentForm({ client, onClose, onCreated, onSavingChange }: {
  client: ControlPlaneClient; onClose: () => void; onCreated: (id: string) => void; onSavingChange: (busy: boolean) => void;
}) {
  const id = useId();
  const [models, setModels] = useState<PlatformDeployableModel[]>([]);
  const [tenants, setTenants] = useState<TenantLifecycleSummary[]>([]);
  const [selection, setSelection] = useState("");
  const [type, setType] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(true);
  const [failure, setFailure] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [saving, setSaving] = useState(false);
  const alive = useRef(true), pending = useRef(false);
  const request = useRef({ key: "", input: "" });
  const model = selection === "" ? undefined : models[Number(selection)];
  const capability = model?.modelType ?? type;
  useEffect(() => {
    alive.current = true;
    void Promise.all([allPages(page => client.listPlatformDeployableModels(page, 100)),
      allPages(page => client.listTenantLifecycles(page, 100, "", "ACTIVE"))]).then(([options, activeTenants]) => {
      if (!alive.current) return;
      if (options.some(option => !option.displayName || !["CHAT", "EMBEDDING", "RERANK", null].includes(option.modelType))
        || activeTenants.some(tenant => tenant.status !== "ACTIVE")) throw new Error("invalid-deployment-options");
      setModels(options); setTenants(activeTenants); setLoaded(true);
    }).catch(error => { if (alive.current) setFailure(message(error)); })
      .finally(() => { if (alive.current) setBusy(false); });
    return () => { alive.current = false; };
  }, [client]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current || busy || !loaded) return;
    setFailure("");
    try {
      const values = new FormData(event.currentTarget);
      const selected = values.getAll("authorizeTenant"); values.delete("authorizeTenant");
      for (const value of selected) {
        if (typeof value !== "string" || !/^(0|[1-9][0-9]*)$/.test(value) || !tenants[Number(value)]) throw new Error("请重新选择当前活动租户。");
        values.append("authorizeTenant", tenants[Number(value)]!.tenantId);
      }
      const input = platformDeploymentInput(values, model);
      const serialized = JSON.stringify(input);
      if (request.current.input !== serialized) request.current = { key: crypto.randomUUID(), input: serialized };
      pending.current = true; setBusy(true); setSaving(true); onSavingChange(true);
      const saved = await client.createPlatformModelDeployment(input, request.current.key);
      if (!alive.current) return;
      if (!saved.id || saved.status !== "DRAFT" || !saved.inputs) throw new Error("invalid-created-deployment");
      onSavingChange(false); onCreated(saved.id);
    } catch (error) { if (alive.current) setFailure(message(error)); }
    finally { pending.current = false; if (alive.current) { setBusy(false); setSaving(false); onSavingChange(false); } }
  }

  return <form className="platform-deployment-form" onSubmit={event => void save(event)} noValidate aria-busy={busy}>
    <header className="platform-deployment-dialog__header"><span className="platform-connection-kicker">部署草稿</span><DialogTitle>建立可授权模型部署</DialogTitle></header>
    <div className="platform-deployment-dialog__body">
      <label className="deployment-field" htmlFor={`${id}-name`}><span>部署名称</span><input id={`${id}-name`} name="name" autoFocus autoComplete="off" minLength={2} maxLength={80} disabled={busy} /></label>
      <label className="deployment-field" htmlFor={`${id}-model`}><span>已发现模型</span><select id={`${id}-model`} name="modelSelection" aria-label="已发现模型" value={selection} disabled={busy || !loaded}
        onChange={event => { setSelection(event.target.value); setType(""); setConfirmed(false); setFailure(""); }}>
        <option value="">请选择已发布连接的模型</option>{models.map((item, index) => <option key={`${item.connectionId}:${item.modelCode}:${item.modelType}`} value={index}>{item.displayName} / {typeLabel(item.modelType)}</option>)}
      </select><small>未知类型必须显式确认，不能从名称推断能力。</small><small className="deployment-selected-model">{model ? `${model.displayName} / ${typeLabel(model.modelType)}` : ""}</small></label>
      {model ? <div className="deployment-field-stack" key={selection}>
        <label className="deployment-field" htmlFor={`${id}-type`}><span>模型类型</span>{model.modelType
          ? <><input id={`${id}-type`} aria-label="模型类型" readOnly value={typeLabel(model.modelType)} /><small>类型来自当前候选，参数仍需明确确认。</small></>
          : <><select id={`${id}-type`} name="capability" aria-label="模型类型" value={type} onChange={event => { setType(event.target.value); setConfirmed(false); }} disabled={busy}><option value="">请选择并确认模型类型</option>
            {model.declaredCapability ? <option value={model.declaredCapability}>{typeLabel(model.declaredCapability)}</option> : null}</select><small>仅允许当前连接声明的能力；分类确认不替代真实验证。</small></>}</label>
        {capability ? <div className="deployment-parameter-grid" key={capability}>
          <Parameter id={`${id}-context`} name="contextWindow" label="上下文窗口（Token）" help="来源明确的声明上限，尚不代表已完成边界实测。" disabled={busy} />
          {capability === "CHAT" ? <Parameter id={`${id}-output`} name="outputLimit" label="最大输出（Token）" help="为本部署声明的输出上限，不得超过上下文窗口。" disabled={busy} />
            : capability === "EMBEDDING" ? <Parameter id={`${id}-dimension`} name="embeddingDimension" label="向量维度" help="保存的是声明值，必须与真实响应维度核对。" disabled={busy} />
              : <Parameter id={`${id}-rerank`} name="rerankCandidateLimit" label="重排候选上限" help="验证返回数量、索引范围与重复结果。" disabled={busy} />}
        </div> : <p className="deployment-help">先明确模型类型，再填写对应能力参数。</p>}
        <label className="deployment-field" htmlFor={`${id}-source`}><span>参数来源</span><input id={`${id}-source`} name="parameterSource" aria-label="参数来源" autoComplete="off" maxLength={160} placeholder="厂商资料版本或受控目录版本" disabled={busy} /><small>填写非秘密的可复核依据；文档上限不冒充实测上限。</small></label>
        <label className="deployment-field" htmlFor={`${id}-region`}><span>驻留范围</span><input id={`${id}-region`} aria-label="驻留范围" readOnly value={model.residency || "来源尚未提供"} /><small>随连接冻结，不能在本表单修改。</small></label>
        <fieldset className="deployment-scope"><legend>模型确认</legend><label><input type="checkbox" name="modelConfirmed" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} disabled={busy} /><span>我已确认模型类型、能力和参数来源</span></label><p className="deployment-help">保存时登记本候选的分类确认及参数声明，仍须单独执行能力验证。</p></fieldset>
      </div> : <p className="deployment-help">选择模型后确认类型、参数来源及驻留范围。</p>}
      <fieldset className="deployment-scope"><legend>授权租户</legend>{tenants.map((tenant, index) => <label key={tenant.tenantId}><input type="checkbox" name="authorizeTenant" value={index} disabled={busy} />{tenant.displayName}</label>)}
        {loaded && !tenants.length ? <p>当前没有活动租户，不能保存拟授权草稿。</p> : null}<p className="deployment-help">请明确选择。草稿仅保存拟授权范围，发布后才生效。</p></fieldset>
      <div className="platform-connection-note">能力验证使用版本化合成样本，并受本次请求、输出和超时上限约束；不代表月度费用已核验。</div>
      {busy && !loaded ? <p role="status">正在读取当前模型与活动租户。</p> : null}<p className="deployment-feedback" role="alert">{failure}</p>
    </div>
    <footer className="platform-deployment-dialog__actions"><Button appearance="secondary" type="button" disabled={saving} onClick={onClose}>取消</Button><Button appearance="primary" type="submit" disabled={busy || !loaded}>{saving ? "正在保存" : "保存部署草稿"}</Button></footer>
  </form>;
}

function Parameter({ id, name, label, help, disabled }: { id: string; name: string; label: string; help: string; disabled: boolean }) {
  return <label className="deployment-field" htmlFor={id}><span>{label}</span><input id={id} name={name} aria-label={label} type="number" min={1} step={1} disabled={disabled} /><small>{help}</small></label>;
}
export function typeLabel(type: string | null): string { return ({ CHAT: "对话模型", EMBEDDING: "向量模型", RERANK: "重排序模型" } as Record<string, string>)[type ?? ""] ?? "待确认"; }
function message(error: unknown): string { return error instanceof ApiRequestError ? error.message : error instanceof Error && !error.message.startsWith("invalid-") ? error.message : "读取或保存未完成，请关闭后重试。"; }
async function allPages<T>(read: (page: number) => Promise<PageResult<T>>): Promise<T[]> {
  const items: T[] = [];
  let total: number | undefined;
  for (let page = 1; ; page += 1) {
    const result = await read(page);
    if (!result || result.pageNumber !== page || !Array.isArray(result.items) || !Number.isSafeInteger(result.totalElements)
      || result.totalElements < 0 || (total !== undefined && result.totalElements !== total) || typeof result.hasNext !== "boolean") throw new Error("invalid-option-page");
    total = result.totalElements; items.push(...result.items);
    if (!result.hasNext) {
      if (items.length !== total) throw new Error("invalid-option-count");
      return items;
    }
    if (!result.items.length || items.length >= total) throw new Error("invalid-option-pagination");
  }
}
