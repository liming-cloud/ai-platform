import { PageHeading } from "@foundation/design-system";
import {
  Button, Dialog, DialogActions, DialogBody, DialogContent, DialogSurface, DialogTitle,
  Input, MessageBar, MessageBarBody, MessageBarTitle,
} from "@fluentui/react-components";
import { useEffect, useRef, useState } from "react";
import {
  presentApiError, type ControlPlaneClient, type TenantLifecycle, type TenantQuotaSummary,
  type TenantQuotaUsage, type UserFacingError,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { TenantSettingsNavigation } from "./TenantSettingsNavigation";
import { decimalFromBytes, quotaFormFrom, quotasFromForm, type TenantQuotaForm } from "./tenant-quota-settings";
import "./tenant-onboarding.css";
import "./tenant-quota-settings.css";

type Loaded = { tenant: TenantLifecycle; quotas: TenantQuotaSummary; usage: TenantQuotaUsage };
type FieldKey = keyof TenantQuotaForm;
const fields: readonly { key: FieldKey; label: string; unit: string; integer: boolean }[] = [
  { key: "knowledgeBases", label: "知识库数量", unit: "个", integer: true },
  { key: "documentsPerKnowledgeBase", label: "每库文档数量", unit: "个", integer: true },
  { key: "storageGb", label: "每库内容容量", unit: "GB", integer: false },
  { key: "fileMb", label: "单文件大小", unit: "MB", integer: false },
  { key: "textMib", label: "粘贴文本大小", unit: "MiB", integer: false },
];

/** 平台管理员按原型查看和编辑五项资源配额；服务端最终核实版本与用量。 */
export function TenantQuotaSettings({ client, tenantId, onRouteChange }: {
  client: ControlPlaneClient;
  tenantId: string;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [loaded, setLoaded] = useState<Loaded>();
  const [failure, setFailure] = useState<UserFacingError>();
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [form, setForm] = useState<TenantQuotaForm>();
  const [validation, setValidation] = useState("");
  const [saving, setSaving] = useState(false);
  const trigger = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([
      client.getTenantLifecycle(tenantId), client.getTenantQuotas(tenantId), client.getTenantQuotaUsage(tenantId),
    ]).then(([tenant, quotas, usage]) => {
      if (!active) return;
      setLoaded({ tenant, quotas, usage });
      setFailure(undefined);
    }).catch(error => {
      if (active) setFailure(presentApiError(error, "读取资源配额"));
    });
    const unsubscribe = client.subscribeSessionChange(hasSession => {
      if (!hasSession) { active = false; setLoaded(undefined); setForm(undefined); }
    });
    return () => { active = false; unsubscribe(); };
  }, [client, tenantId, revision]);

  function close() {
    if (saving) return;
    setForm(undefined);
    setValidation("");
    window.requestAnimationFrame(() => trigger.current?.focus());
  }

  async function save() {
    if (!loaded || !form || saving) return;
    let update;
    try { update = quotasFromForm(form, loaded.usage, loaded.quotas); }
    catch (error) { setValidation(error instanceof Error ? error.message : "请检查配额数值。"); return; }
    setSaving(true);
    setValidation("");
    try {
      const quotas = await client.updateTenantQuotas(tenantId, { ...update, expectedVersion: loaded.quotas.version });
      setLoaded({ ...loaded, quotas });
      setNotice("资源配额已保存。");
      setForm(undefined);
      window.requestAnimationFrame(() => trigger.current?.focus());
      try {
        const usage = await client.getTenantQuotaUsage(tenantId);
        setLoaded(previous => previous ? { ...previous, usage } : previous);
      } catch (error) {
        setFailure(presentApiError(error, "刷新资源用量"));
      }
    } catch (error) {
      const presented = presentApiError(error, "保存资源配额");
      setValidation(`${presented.title}：${presented.description}`);
    } finally { setSaving(false); }
  }

  const status = loaded?.tenant.status === "ACTIVE" ? "活动" : loaded?.tenant.status === "SUSPENDED" ? "已暂停" : "准备中";
  const display = loaded ? quotaFormFrom(loaded.quotas) : undefined;
  const currentUsage = loaded?.usage;
  const used: Partial<Record<FieldKey, string>> = currentUsage ? {
    knowledgeBases: String(currentUsage.knowledgeBases),
    documentsPerKnowledgeBase: String(currentUsage.documentsPerKnowledgeBase),
    storageGb: decimalFromBytes(currentUsage.bytesPerKnowledgeBase, 1_000_000_000n),
  } : {};

  return <>
    <PageHeading title="资源配额" context="平台治理" summary={loaded?.tenant.displayName}
      status={loaded ? { label: status, tone: loaded.tenant.status === "ACTIVE" ? "success" : "warning" } : undefined}
      action={<Button className="tenant-overview__return" appearance="primary"
        onClick={() => onRouteChange({ navigationId: "platform", task: "tenants", page: 1 })}>返回租户目录</Button>} />
    <section className="tenant-onboarding tenant-overview tenant-quota-page">
      <TenantSettingsNavigation tenantId={tenantId} active="quotas" onRouteChange={onRouteChange} />
      {notice ? <MessageBar intent="success"><MessageBarBody><MessageBarTitle>操作已生效</MessageBarTitle>{notice}</MessageBarBody></MessageBar> : null}
      {failure ? <><MessageBar intent="error"><MessageBarBody><MessageBarTitle>{failure.title}</MessageBarTitle>{failure.description}</MessageBarBody></MessageBar>
        <Button onClick={() => setRevision(value => value + 1)}>重新加载</Button></> : null}
      {!loaded && !failure ? <p role="status">正在读取资源配额…</p> : null}
      {loaded && display ? <section className="tenant-quota-page__section" aria-labelledby="tenant-quota-heading">
        <div className="tenant-quota-page__heading"><div><span>平台配置</span><h2 id="tenant-quota-heading">资源配额</h2></div>
          <Button appearance="secondary" onClick={event => { trigger.current = event.currentTarget; setForm(quotaFormFrom(loaded.quotas)); setNotice(""); }}>编辑配额</Button></div>
        <dl className="tenant-quota-page__summary">{fields.map(({ key, label, unit }) => <div key={key}>
          <dt>{label}</dt><dd><strong>{display[key]}</strong><span>{unit}</span>
            {used[key] !== undefined ? <small>已用 {used[key]} {unit}</small> : null}</dd>
        </div>)}</dl>
      </section> : null}
    </section>
    <Dialog open={form !== undefined} onOpenChange={(_, value) => { if (!value.open) close(); }}>
      <DialogSurface className="foundation-task-dialog tenant-quota-page__dialog">
        <form onSubmit={event => { event.preventDefault(); void save(); }}>
          <DialogBody>
            <DialogTitle><span className="tenant-quota-page__kicker">资源配额</span>
              <span>编辑 {loaded?.tenant.displayName} 的配额</span></DialogTitle>
            <DialogContent>
              <p>保存值必须大于 0，且不能低于当前已用量。</p>
              {!currentUsage?.complete ? <p role="status">当前用量尚未完整核实，不能下调知识库数量、每库文档数量或每库内容容量。</p> : null}
              <div className="tenant-quota-page__grid">{fields.map(({ key, label, unit, integer }) => <label key={key}
                className="tenant-quota-page__field" htmlFor={`quota-${key}`}>
                <span className="tenant-quota-page__field-label"><span>{label}</span><small>{unit}</small></span>
                <Input type="number" inputMode="decimal" min={integer ? "1" : "0.01"} step={integer ? "1" : "0.01"}
                  id={`quota-${key}`} aria-label={`${label} ${unit}`} aria-describedby={`quota-${key}-hint`}
                  value={form?.[key] ?? ""}
                  onChange={(_, value) => setForm(previous => previous ? { ...previous, [key]: value.value } : previous)} />
                <small className="tenant-quota-page__field-hint" id={`quota-${key}-hint`}>{key === "fileMb" || key === "textMib"
                  ? "降低后只影响后续新增内容。"
                  : `当前已用 ${used[key] ?? "0"} ${unit}，保存值不能低于已用量。`}</small>
              </label>)}</div>
              {validation ? <p className="tenant-quota-page__validation" role="alert">{validation}</p> : null}
            </DialogContent>
            <DialogActions><Button type="button" appearance="secondary" disabled={saving} onClick={close}>取消</Button>
              <Button type="submit" appearance="primary" disabled={saving}>{saving ? "正在保存" : "保存配额"}</Button></DialogActions>
          </DialogBody>
        </form>
      </DialogSurface>
    </Dialog>
  </>;
}
