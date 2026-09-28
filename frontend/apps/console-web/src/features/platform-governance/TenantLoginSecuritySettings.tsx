import { PageHeading } from "@foundation/design-system";
import {
  Button, Dialog, DialogBody, DialogContent, DialogSurface, DialogTitle,
  Input, MessageBar, MessageBarBody, MessageBarTitle,
} from "@fluentui/react-components";
import { useEffect, useRef, useState } from "react";
import {
  presentApiError, type ControlPlaneClient, type TenantLifecycle,
  type TenantLoginSecurityPolicy, type UserFacingError,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { TenantSettingsNavigation } from "./TenantSettingsNavigation";
import { SECURITY_FIELDS, securityFormFrom, securityPolicyFromForm, type SecurityForm } from "./tenant-login-security-settings";
import "./tenant-onboarding.css";
import "./tenant-quota-settings.css";
import "./tenant-login-security-settings.css";

/** 平台管理员查看和独立保存租户登录策略；每次进入均从服务端重读策略版本。 */
export function TenantLoginSecuritySettings({ client, tenantId, onRouteChange }: {
  client: ControlPlaneClient;
  tenantId: string;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [loaded, setLoaded] = useState<{ tenant: TenantLifecycle; policy: TenantLoginSecurityPolicy }>();
  const [failure, setFailure] = useState<UserFacingError>();
  const [feedback, setFeedback] = useState("");
  const [revision, setRevision] = useState(0);
  const [form, setForm] = useState<SecurityForm>();
  const [validation, setValidation] = useState("");
  const [saving, setSaving] = useState(false);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const active = useRef(true);

  useEffect(() => {
    active.current = true;
    let requestActive = true;
    Promise.all([client.getTenantLifecycle(tenantId), client.getTenantLoginSecurity(tenantId)])
      .then(([tenant, policy]) => {
        if (!requestActive || !active.current) return;
        setLoaded({ tenant, policy });
        setFailure(undefined);
      }).catch(error => {
        if (requestActive && active.current) setFailure(presentApiError(error, "读取登录安全策略"));
      });
    const unsubscribe = client.subscribeSessionChange(hasSession => {
      if (!hasSession) { active.current = false; requestActive = false; setLoaded(undefined); setForm(undefined); }
    });
    return () => { requestActive = false; active.current = false; unsubscribe(); };
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
    try { update = securityPolicyFromForm(form); }
    catch (error) { setValidation(error instanceof Error ? error.message : "请检查登录策略数值。"); return; }
    setSaving(true);
    setValidation("");
    try {
      const policy = await client.updateTenantLoginSecurity(tenantId, {
        ...update, expectedVersion: loaded.policy.version,
      });
      if (!active.current) return;
      setLoaded({ ...loaded, policy });
      setFeedback("登录策略已保存。");
      setForm(undefined);
      window.requestAnimationFrame(() => trigger.current?.focus());
    } catch (error) {
      if (!active.current) return;
      const presented = presentApiError(error, "保存登录安全策略");
      setValidation(`${presented.title}：${presented.description}`);
    } finally { if (active.current) setSaving(false); }
  }

  const status = loaded?.tenant.status === "ACTIVE" ? "活动" : loaded?.tenant.status === "SUSPENDED" ? "已暂停" : "准备中";
  return <>
    <PageHeading title="登录安全" context="平台治理" summary={loaded?.tenant.displayName}
      status={loaded ? { label: status, tone: loaded.tenant.status === "ACTIVE" ? "success" : "warning" } : undefined}
      action={<Button className="tenant-overview__return" appearance="primary"
        onClick={() => onRouteChange({ navigationId: "platform", task: "tenants", page: 1 })}>返回租户目录</Button>} />
    <section className="tenant-onboarding tenant-overview tenant-quota-page tenant-security-page">
      <TenantSettingsNavigation tenantId={tenantId} active="security" onRouteChange={onRouteChange} />
      {feedback ? <MessageBar intent="success"><MessageBarBody><MessageBarTitle>操作已生效</MessageBarTitle>{feedback}</MessageBarBody></MessageBar> : null}
      {failure ? <><MessageBar intent="error"><MessageBarBody><MessageBarTitle>{failure.title}</MessageBarTitle>{failure.description}</MessageBarBody></MessageBar>
        <Button onClick={() => setRevision(value => value + 1)}>重新加载</Button></> : null}
      {!loaded && !failure ? <p role="status">正在读取登录安全策略…</p> : null}
      {loaded ? <section className="tenant-quota-page__section" aria-labelledby="tenant-security-heading">
        <div className="tenant-quota-page__heading"><h2 id="tenant-security-heading">登录安全</h2>
          <Button appearance="secondary" onClick={event => {
            trigger.current = event.currentTarget;
            setForm(securityFormFrom(loaded.policy));
            setFeedback("");
          }}>编辑登录策略</Button></div>
        <dl className="tenant-quota-page__summary">{SECURITY_FIELDS.map(({ key, label, unit }) => <div key={key}>
          <dt>{label}</dt><dd>{loaded.policy[key]} {unit}</dd>
        </div>)}</dl>
      </section> : null}
    </section>
    <Dialog open={form !== undefined} onOpenChange={(_, value) => { if (!value.open) close(); }}>
      <DialogSurface className="foundation-task-dialog tenant-quota-page__dialog tenant-security-page__dialog">
        <form onSubmit={event => { event.preventDefault(); void save(); }}>
          <DialogBody>
            <DialogTitle>编辑登录策略</DialogTitle>
            <DialogContent>
              <p>租户隔离、权限校验和密码不回显始终开启。</p>
              <div className="tenant-quota-page__grid">{SECURITY_FIELDS.map(({ key, label, unit, min, max }) => <label
                key={key} className="tenant-quota-page__field" htmlFor={`security-${key}`}>
                <span className="tenant-quota-page__field-label">{label}（{unit}）</span>
                <Input id={`security-${key}`} type="number" inputMode="numeric" min={min} max={max} step="1" required
                  value={form?.[key] ?? ""} aria-label={`${label}（${unit}）`}
                  onChange={(_, value) => setForm(previous => previous ? { ...previous, [key]: value.value } : previous)} />
              </label>)}</div>
              <p className="tenant-security-page__impact" role="note">保存后，此租户现有会话将立即失效。</p>
              {validation ? <p className="tenant-quota-page__validation" role="alert">{validation}</p> : null}
            </DialogContent>
          </DialogBody>
          <div className="tenant-quota-page__actions"><Button type="button" appearance="secondary" disabled={saving} onClick={close}>取消</Button>
            <Button type="submit" appearance="primary" disabled={saving}>{saving ? "正在保存" : "保存策略"}</Button></div>
        </form>
      </DialogSurface>
    </Dialog>
  </>;
}
