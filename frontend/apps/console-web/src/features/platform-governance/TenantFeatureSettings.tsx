import { PageHeading } from "@foundation/design-system";
import { Button, MessageBar, MessageBarBody, MessageBarTitle } from "@fluentui/react-components";
import { useEffect, useRef, useState } from "react";
import { presentApiError, type ControlPlaneClient, type TenantFeatureSettings as Settings,
  type TenantLifecycle, type UserFacingError } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { TENANT_FEATURES, type TenantFeatureFlags } from "./tenant-feature-settings";
import { TenantSettingsNavigation } from "./TenantSettingsNavigation";
import "./tenant-onboarding.css";
import "./tenant-feature-settings.css";

/** 平台租户功能页只保存独立草稿；每次进入从服务端重读版本事实。 */
export function TenantFeatureSettings({ client, tenantId, onRouteChange }: {
  client: ControlPlaneClient;
  tenantId: string;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [loaded, setLoaded] = useState<{ tenant: TenantLifecycle; settings: Settings }>();
  const [draft, setDraft] = useState<TenantFeatureFlags>();
  const [failure, setFailure] = useState<UserFacingError>();
  const [feedback, setFeedback] = useState("");
  const [revision, setRevision] = useState(0);
  const [saving, setSaving] = useState(false);
  const active = useRef(true);

  useEffect(() => {
    active.current = true;
    let requestActive = true;
    Promise.all([client.getTenantLifecycle(tenantId), client.getTenantFeatures(tenantId)])
      .then(([tenant, settings]) => {
        if (!requestActive || !active.current) return;
        setLoaded({ tenant, settings });
        setDraft({ ...settings.availableFeatures });
        setFailure(undefined);
      }).catch(error => { if (requestActive && active.current) setFailure(presentApiError(error, "读取租户可用功能")); });
    const unsubscribe = client.subscribeSessionChange(hasSession => {
      if (!hasSession) { active.current = false; requestActive = false; setLoaded(undefined); setDraft(undefined); }
    });
    return () => { requestActive = false; active.current = false; unsubscribe(); };
  }, [client, tenantId, revision]);

  async function save() {
    if (!loaded || !draft || saving) return;
    if (TENANT_FEATURES.every(item => draft[item.code] === loaded.settings.availableFeatures[item.code])) {
      setFeedback("当前没有待保存的功能变更。");
      return;
    }
    setSaving(true);
    setFailure(undefined);
    setFeedback("");
    try {
      const settings = await client.updateTenantFeatures(tenantId, draft, loaded.settings.version);
      if (!active.current) return;
      setLoaded({ ...loaded, settings });
      setDraft({ ...settings.availableFeatures });
      setFeedback("功能配置已保存。");
    } catch (error) {
      if (active.current) setFailure(presentApiError(error, "保存功能配置"));
    } finally { if (active.current) setSaving(false); }
  }

  return <>
    <PageHeading title="可用功能" context="平台治理" summary={loaded?.tenant.displayName}
      status={loaded ? { label: loaded.tenant.status === "ACTIVE" ? "活动"
        : loaded.tenant.status === "SUSPENDED" ? "已暂停" : "准备中",
        tone: loaded.tenant.status === "ACTIVE" ? "success" : "warning" } : undefined}
      action={<Button appearance="primary" disabled={!loaded || saving} onClick={() => void save()}>
        {saving ? "正在保存" : "保存功能配置"}</Button>} />
    <section className="tenant-onboarding tenant-feature-page">
      <TenantSettingsNavigation tenantId={tenantId} active="features" onRouteChange={onRouteChange} />
      {failure ? <MessageBar intent="error"><MessageBarBody><MessageBarTitle>{failure.title}</MessageBarTitle>{failure.description}</MessageBarBody></MessageBar> : null}
      {failure && !loaded ? <Button onClick={() => setRevision(value => value + 1)}>重新加载</Button> : null}
      {!loaded && !failure ? <p role="status">正在读取租户可用功能…</p> : null}
      {loaded && draft ? <section className="tenant-feature-page__editor" aria-labelledby="tenant-feature-heading">
        <div className="tenant-feature-page__heading"><div><span>平台配置</span><h2 id="tenant-feature-heading">租户可用功能</h2></div>
          <span className="tenant-feature-page__status">保存后生效</span></div>
        <div className="tenant-feature-page__grid">{TENANT_FEATURES.map(item => <label key={item.code} className="tenant-feature-page__control">
          <input type="checkbox" checked={draft[item.code]} disabled={saving}
            onChange={event => { setDraft({ ...draft, [item.code]: event.target.checked }); setFeedback(""); }} />
          <span><strong>{item.label}</strong><small>{item.help}</small></span>
        </label>)}</div>
        <div className="tenant-feature-page__actions"><Button appearance="secondary" disabled={saving}
          onClick={() => { setDraft({ ...loaded.settings.availableFeatures }); setFeedback(""); }}>取消</Button>
          <span role="status" aria-live="polite">{feedback}</span></div>
      </section> : null}
    </section>
  </>;
}
