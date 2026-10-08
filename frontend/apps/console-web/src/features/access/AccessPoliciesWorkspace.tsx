import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button, Dialog, DialogSurface, DialogTitle } from "@fluentui/react-components";
import { PageHeading } from "@foundation/design-system";
import { presentApiError, type AccessPolicySummary, type ControlPlaneClient, type MemberCreateOptions, type PageResult } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { AccessPolicyWorkspace } from "./AccessPolicyWorkspace";
import { AccessPagination } from "./AccessPagination";
import type { RegisterAuthorizationDeparture } from "./AuthorizationDeparture";
import { PublishedAssistantPicker } from "./PublishedAssistantPicker";
import { PolicyMemberPicker } from "./PolicyMemberPicker";
import "./member-account.css";
import "./access-policy.css";
import { policyRoute, policyStatusLabel } from "./access-policy-state";

type PolicyRoute = Extract<ConsoleRoute, { navigationId: "access-policies" }>;

/** 目录与版本工作区共享受控入口；新建只产生草稿，不授予任何权限。 */
export function AccessPoliciesWorkspace({ client, route, onRouteChange, registerDeparture }: {
  client: ControlPlaneClient; route: PolicyRoute; onRouteChange: (route: ConsoleRoute) => void; registerDeparture: RegisterAuthorizationDeparture;
}) {
  const epoch = client.currentSessionRevision();
  return route.task === "policy-detail" ? <AccessPolicyWorkspace key={`${epoch}:${route.policyId}:${route.versionId}`} client={client} route={route} onRouteChange={onRouteChange} registerDeparture={registerDeparture} />
    : <PolicyDirectory key={epoch} client={client} route={route} onRouteChange={onRouteChange} />;
}

function PolicyDirectory({ client, route, onRouteChange }: { client: ControlPlaneClient; route: Extract<PolicyRoute, { task: "policies" }>; onRouteChange: (route: ConsoleRoute) => void }) {
  const size = route.pageSize ?? 20; const query = route.query ?? ""; const status = route.status ?? "ALL";
  const [searchDraft, setSearch] = useState({ routeQuery: query, value: query });
  const search = searchDraft.routeQuery === query ? searchDraft.value : query; const [revision, setRevision] = useState(0);
  const [loaded, setLoaded] = useState<{ key: string; page: PageResult<AccessPolicySummary>; canCreate: boolean }>();
  const [error, setError] = useState<{ key: string; message: string }>(); const [creating, setCreating] = useState(false);
  const [options, setOptions] = useState<MemberCreateOptions>(); const [assistantId, setAssistant] = useState(""); const [membershipIds, setMembers] = useState<string[]>([]);
  const [dialogError, setDialogError] = useState(""); const [busy, setBusy] = useState(false);
  const epoch = client.currentSessionRevision(); const active = useRef(false); const writing = useRef(false); const trigger = useRef<HTMLElement | null>(null);
  const current = () => active.current && client.hasSession() && client.currentSessionRevision() === epoch;
  const key = `${epoch}:${route.page}:${size}:${query}:${status}:${revision}`;
  const page = loaded?.key === key ? loaded.page : undefined;
  const canCreate = loaded?.key === key && loaded.canCreate;
  const failure = error?.key === key ? error.message : "";
  useEffect(() => {
    const timer = window.setTimeout(() => { if (search !== query) onRouteChange({ ...route, page: 1, query: search }); }, 300);
    return () => window.clearTimeout(timer);
  }, [search, query, onRouteChange, route]);
  useEffect(() => {
    let mounted = true; active.current = true;
    Promise.all([client.listAccessPolicies(route.page, size, query, status), client.listMemberAssistants("", 1, 1), client.listAccessPolicyMembers("", 1, 1)]).then(([value, assistants, members]) => {
      if (!mounted || !active.current || !client.hasSession() || client.currentSessionRevision() !== epoch) return;
      setLoaded({ key, page: value, canCreate: assistants.totalElements > 0 && members.totalElements > 0 });
      if (route.page > Math.max(1, value.totalPages)) onRouteChange({ ...route, page: Math.max(1, value.totalPages) });
    }).catch(error => { if (mounted && active.current && client.currentSessionRevision() === epoch) setError({ key, message: presentApiError(error, "读取访问策略").description }); });
    return () => { mounted = false; active.current = false; };
  }, [client, epoch, route, size, query, status, revision, key, onRouteChange]);
  async function open() {
    if (!current() || writing.current) return; trigger.current = document.activeElement as HTMLElement;
    setDialogError(""); setAssistant(""); setMembers([]); setOptions(undefined); setCreating(true);
    try { const value = await client.memberCreateOptions(); if (current()) setOptions(value); }
    catch (error) { if (current()) setDialogError(presentApiError(error, "读取策略创建条件").description); }
  }
  function close() {
    if (writing.current) return; setCreating(false); setOptions(undefined); setAssistant(""); setMembers([]); setDialogError("");
    window.requestAnimationFrame(() => { if (current() && trigger.current?.isConnected) trigger.current.focus(); });
  }
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!current() || writing.current || !options) return;
    const name = String(new FormData(event.currentTarget).get("name") ?? "").trim();
    if (name.length < 2 || name.length > 80 || !assistantId || !membershipIds.length) { setDialogError("请填写 2 至 80 字策略名称，并选择已发布助手及普通成员。"); return; }
    writing.current = true; setBusy(true); setDialogError("");
    try {
      const value = await client.createAccessPolicy({ name, assistantId, membershipIds, expectedAuthorizationRevision: options.authorizationRevision, expectedTenantFeatureVersion: options.tenantFeatureVersion });
      if (current()) { setCreating(false); onRouteChange(policyRoute(value)); }
    } catch (error) { if (current()) setDialogError(presentApiError(error, "创建策略草稿").description); }
    finally { writing.current = false; if (current()) setBusy(false); }
  }
  return <>
    <PageHeading title="访问策略目录" context="访问与审计" summary="当前工作区" action={<Button className="policy-page-action policy-directory-create" appearance="primary" disabled={!canCreate || busy} onClick={() => void open()}>新建访问策略</Button>} />
    <section className="member-workspace access-policy-directory" aria-busy={!page || busy}>
      <div className="policy-directory-toolbar"><label className="member-native-field"><span>搜索访问策略</span><input type="search" placeholder="按名称或范围搜索" maxLength={80} value={search} onChange={event => setSearch({ routeQuery: query, value: event.target.value })} /></label>
        <label className="member-native-field policy-status-field"><span>状态</span><select value={status} onChange={event => onRouteChange({ ...route, page: 1, status: event.target.value as "ALL" | "DRAFT" | "PUBLISHED" })}>
          <option value="ALL">全部状态</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option></select></label></div>
      <p className="policy-feedback" role="status">{page && (query || status !== "ALL") ? page.totalElements ? `找到 ${page.totalElements} 项` : "没有匹配结果，请修改搜索词或筛选条件。" : ""}</p>
      {failure ? <p role="alert">{failure} <Button onClick={() => setRevision(value => value + 1)}>重试</Button></p> : !page ? <p role="status">正在读取访问策略…</p>
        : <div className="tenant-member-directory-table policy-directory-table"><table><thead><tr>{["策略", "范围", "状态", "操作"].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
          <tbody>{page.items.length ? page.items.map(policy => <tr key={policy.policyId}><td data-label="策略"><strong>{policy.name}</strong></td><td data-label="范围">{policy.assistantName}</td>
            <td data-label="状态"><span className="member-status policy-status" data-tone={policy.status === "PUBLISHED" ? "success" : "info"}>{policyStatusLabel(policy.status)}</span></td>
            <td data-label="操作"><Button onClick={() => onRouteChange({ navigationId: "access-policies", task: "policy-detail", policyId: policy.policyId, versionId: policy.versionId })}>查看</Button></td></tr>) : <tr><td colSpan={4}>暂无记录。</td></tr>}</tbody></table>
          <AccessPagination total={page.totalElements} page={route.page} size={size} label="目录分页" onChange={(page, pageSize) => onRouteChange({ ...route, page, pageSize })} /></div>}
      <p className="policy-feedback" role="status" />{page && !canCreate ? <p className="policy-inline-note">先发布助手并添加普通成员，再配置访问策略。</p> : null}
    </section>
    <Dialog open={creating} onOpenChange={(_, data) => { if (!data.open) close(); }}>
      <DialogSurface className="member-account-dialog policy-create-dialog" aria-labelledby="access-create-title" backdrop={{ className: "member-account-backdrop" }}>
        <form className="member-dialog-form" onSubmit={event => void create(event)} noValidate>
          <DialogTitle id="access-create-title" className="member-dialog-title" action={null}>新建访问策略</DialogTitle>
          <label className="member-native-field"><span>策略名称</span><input name="name" minLength={2} maxLength={80} required disabled={busy} /></label>
          <PublishedAssistantPicker client={client} single selectFirst selected={assistantId ? [assistantId] : []} disabled={busy} onChange={ids => setAssistant(ids[0] ?? "")} />
          <PolicyMemberPicker client={client} compact selected={membershipIds} disabled={busy} onChange={setMembers} />
          <p className="policy-feedback" role="alert">{dialogError}</p><div className="member-form-actions"><Button disabled={busy} onClick={close}>取消</Button><Button appearance="primary" type="submit" disabled={busy || !options}>{busy ? "正在创建…" : "创建策略草稿"}</Button></div>
        </form>
      </DialogSurface>
    </Dialog>
  </>;
}
