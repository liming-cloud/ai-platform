import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button, Checkbox, Dialog, DialogSurface, DialogTitle, Field, Input } from "@fluentui/react-components";
import { PageHeading } from "@foundation/design-system";
import { presentApiError, type ControlPlaneClient, type MemberAccount, type PageResult, type TenantMemberSummary } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { MemberCreateWorkspace } from "./MemberCreateWorkspace";
import { clearMemberPasswords } from "./member-account-draft";
import "./member-account.css";

type MemberRoute = Extract<ConsoleRoute, { navigationId: "access" }>;
type MemberDirectoryRoute = Extract<ConsoleRoute, { task: "members" }>;
type PendingOperation = { kind: "reset"; account: MemberAccount; key: string } | { kind: "disable"; account: TenantMemberSummary };

/** 不一致的历史角色也按管理员保护；服务器仍重新裁决权限。 */
export function memberIsReadOnly(member: Pick<TenantMemberSummary, "membershipRole" | "builtInRoles">): boolean {
  return member.membershipRole === "TENANT_ADMINISTRATOR" || member.builtInRoles.includes("TENANT_ADMINISTRATOR");
}

export function TenantMemberWorkspace({ client, route, onRouteChange }: {
  client: ControlPlaneClient; sessionRevision: number; route: MemberRoute; onRouteChange: (route: ConsoleRoute) => void;
}) {
  const epoch = client.currentSessionRevision();
  if (route.task === "member-create") return <MemberCreateWorkspace key={epoch} client={client} route={route} onRouteChange={onRouteChange} />;
  if (route.task === "member-account") return <MemberAccountWorkspace key={`${epoch}:${route.membershipId}`} client={client} membershipId={route.membershipId} onRouteChange={onRouteChange} />;
  if (route.task === "members") return <MemberDirectory key={epoch} client={client} route={route} onRouteChange={onRouteChange} />;
  return null;
}

function MemberDirectory({ client, route, onRouteChange }: { client: ControlPlaneClient; route: MemberDirectoryRoute; onRouteChange: (route: ConsoleRoute) => void }) {
  const [loaded, setLoaded] = useState<{ owner: string; page: PageResult<TenantMemberSummary> }>();
  const [failure, setFailure] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [pending, setPending] = useState<PendingOperation>();
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState("");
  const [acknowledged, setAcknowledged] = useState(false);
  const [passwordRevision, setPasswordRevision] = useState(0);
  const query = route.query ?? "";
  const [searchDraft, setSearchDraft] = useState({ routeQuery: query, value: query });
  const search = searchDraft.routeQuery === query ? searchDraft.value : query;
  const alive = useRef(false);
  const writing = useRef(false);
  const form = useRef<HTMLFormElement>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const epoch = client.currentSessionRevision();
  const size = route.pageSize ?? 20;
  const status = route.status ?? "ALL";
  const owner = `${epoch}:${route.page}:${size}:${query}:${status}:${revision}`;
  const page = loaded?.owner === owner ? loaded.page : undefined;
  function current() { return alive.current && client.hasSession() && epoch === client.currentSessionRevision(); }
  function restoreFocus() { window.requestAnimationFrame(() => { if (current()) trigger.current?.focus(); }); }

  useEffect(() => {
    alive.current = true;
    const currentForm = form.current;
    const unsubscribe = client.subscribeSessionChange(active => {
      if (!active) { alive.current = false; clearMemberPasswords(form.current); setPending(undefined); setLoaded(undefined); }
    });
    return () => { alive.current = false; unsubscribe(); clearMemberPasswords(currentForm); };
  }, [client, epoch]);
  useEffect(() => {
    let active = true;
    client.listTenantMembers(route.page, size, query, status).then(value => {
      if (active && alive.current && epoch === client.currentSessionRevision() && client.hasSession()) { setLoaded({ owner, page: value }); setFailure(""); }
    }).catch(error => { if (active && alive.current && epoch === client.currentSessionRevision()) setFailure(presentApiError(error, "读取成员目录").description); });
    return () => { active = false; };
  }, [client, epoch, owner, query, route.page, size, status]);
  useEffect(() => {
    if (search.trim() === query) return;
    const timer = window.setTimeout(() => onRouteChange({ ...route, page: 1, query: search.trim() }), 350);
    return () => window.clearTimeout(timer);
  }, [onRouteChange, query, route, search]);
  useEffect(() => {
    if (pending?.kind !== "reset" || busy) return;
    const timer = window.requestAnimationFrame(() => form.current?.querySelector<HTMLInputElement>('[name="password"]')?.focus());
    return () => window.cancelAnimationFrame(timer);
  }, [pending, passwordRevision, busy]);

  function closeDialog() {
    if (writing.current) return;
    clearMemberPasswords(form.current); setPending(undefined); setDialogError(""); setAcknowledged(false); restoreFocus();
  }
  async function openReset(member: TenantMemberSummary, button: HTMLButtonElement) {
    if (writing.current || !current()) return;
    trigger.current = button; writing.current = true; setBusy(true); setNotice("");
    try {
      const account = await client.getTenantMember(member.membershipId);
      if (!current()) return;
      if (!account.passwordResetEligible) { setNotice("此身份另有资格或属于管理员，不能在当前租户重置密码。"); restoreFocus(); return; }
      setDialogError(""); setAcknowledged(false); setPending({ kind: "reset", account, key: crypto.randomUUID() });
    } catch (error) { if (current()) setNotice(presentApiError(error, "读取成员账号").description); }
    finally { writing.current = false; if (current()) setBusy(false); }
  }
  async function restore(member: TenantMemberSummary, button: HTMLButtonElement) {
    if (writing.current || !current()) return;
    trigger.current = button; writing.current = true; setBusy(true); setNotice("");
    try { await client.enableTenantMember(member.membershipId, member.version); if (current()) setNotice("成员资格已恢复；请使用有效凭据重新登录。"); }
    catch (error) { if (current()) setNotice(presentApiError(error, "恢复成员").description); }
    finally { writing.current = false; if (current()) { setBusy(false); setRevision(value => value + 1); restoreFocus(); } }
  }
  async function commit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!pending || writing.current || !current()) return;
    const submitted = event.currentTarget;
    const fields = new FormData(submitted);
    const password = String(fields.get("password") ?? "");
    clearMemberPasswords(submitted);
    // 原生值和 Fluent 内部状态一起清除，校验或请求失败不能恢复密码。
    setPasswordRevision(value => value + 1);
    if (pending.kind === "reset" && (!password || password !== fields.get("confirmation") || !acknowledged)) {
      setDialogError("请完整填写两次相同密码，并确认撤销全部会话。");
      return;
    }
    writing.current = true; setBusy(true); setDialogError(""); setNotice("");
    try {
      if (pending.kind === "reset") await client.resetTenantMemberPassword(pending.account, password, pending.key);
      else await client.disableTenantMember(pending.account.membershipId, pending.account.version);
      if (!current()) return;
      setNotice(pending.kind === "reset" ? "密码已重置，既有会话已撤销。" : "普通成员已停用，助手授权保留。");
      setPending(undefined); setAcknowledged(false); restoreFocus();
    } catch (error) {
      if (current()) setDialogError(`${presentApiError(error, pending.kind === "reset" ? "重置密码" : "停用成员").description} 请取消后重新选择目标。`);
    } finally {
      clearMemberPasswords(submitted); writing.current = false;
      if (current()) { setBusy(false); setRevision(value => value + 1); }
    }
  }
  function changePage(next: number) { onRouteChange({ ...route, page: next }); }

  return <>
    <PageHeading title="成员与对话功能" context="租户访问与知识" summary="普通成员" status={{ label: "可配置", tone: "success" }}
      action={<Button appearance="primary" disabled={busy} onClick={() => onRouteChange({ navigationId: "access", task: "member-create", step: 1 })}>添加成员</Button>} />
    <section className="member-workspace" aria-busy={!page || busy}>
      <div className="member-directory-toolbar"><Field label="搜索成员"><Input type="search" placeholder="按姓名或角色搜索" maxLength={64} value={search}
        onChange={(_, data) => setSearchDraft({ routeQuery: query, value: data.value })} /></Field><span>管理员条目只读</span></div>
      {notice ? <p role="status">{notice}</p> : null}
      {failure ? <p role="alert">{failure} <Button onClick={() => setRevision(value => value + 1)}>重试</Button></p> : !page ? <p role="status">正在加载成员目录…</p> :
        <div className="tenant-member-directory-table"><table><thead><tr>{["成员", "角色", "状态", "助手范围", "操作"].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
          <tbody>{page.items.length ? page.items.map(member => {
            const readOnly = memberIsReadOnly(member);
            return <tr key={member.membershipId}><td data-label="成员"><strong>{member.loginName}</strong></td><td data-label="角色">{readOnly ? "租户管理员" : "普通成员"}</td>
              <td data-label="状态"><span className="member-status" data-tone={member.status === "ENABLED" ? "success" : "warning"}>{member.status === "ENABLED" ? "活动" : "已停用"}</span></td>
              <td data-label="助手范围">{readOnly ? "平台治理" : `${member.assistantCount} 个`}</td>
              <td data-label="操作">{readOnly ? <span>只读</span> : <div className="member-row-actions">
                <Button disabled={busy} onClick={() => onRouteChange({ navigationId: "access", task: "member-account", membershipId: member.membershipId })}>查看成员</Button>
                <Button disabled={busy} onClick={event => void openReset(member, event.currentTarget)}>重置密码</Button>
                {member.status === "ENABLED" ? <Button disabled={busy} onClick={event => { trigger.current = event.currentTarget; setDialogError(""); setPending({ kind: "disable", account: member }); }}>停用</Button>
                  : <Button disabled={busy} onClick={event => void restore(member, event.currentTarget)}>恢复</Button>}
              </div>}</td></tr>;
          }) : <tr><td colSpan={5}>没有匹配的成员。</td></tr>}</tbody></table>
          <div className="member-pagination"><span>共 {page.totalElements} 名成员</span><label>每页 <select aria-label="每页成员数" value={size} onChange={event => onRouteChange({ ...route, page: 1, pageSize: Number(event.target.value) as 10 | 20 | 50 | 100 })}>
            {[10, 20, 50, 100].map(value => <option key={value} value={value}>{value}</option>)}</select> 条</label>
            <Button disabled={route.page <= 1 || busy} onClick={() => changePage(route.page - 1)}>上一页</Button><span>第 {route.page} 页</span><Button disabled={!page.hasNext || busy} onClick={() => changePage(route.page + 1)}>下一页</Button></div>
        </div>}
    </section>
    <Dialog open={Boolean(pending)} onOpenChange={(_, data) => { if (!data.open) closeDialog(); }}>
      <DialogSurface className="member-account-dialog"><form className="member-dialog-form" ref={form} onSubmit={event => void commit(event)} noValidate>
        <header className="member-dialog-header">
          {pending?.kind === "reset" ? <span className="member-dialog-kicker">受控凭据重置</span> : null}
          <DialogTitle className="member-dialog-title">{pending?.kind === "reset" ? "重置密码" : "停用普通成员"}</DialogTitle>
          {pending?.kind === "reset" ? <p>为 {pending.account.loginName} 设置新密码。系统不会回显或保存明文。</p> : null}
        </header>
        <div className="member-dialog-content">{pending?.kind === "reset" ? <>
          <div className="member-password-fields"><Field label="新密码"><Input key={passwordRevision} name="password" type="password" required disabled={busy} maxLength={1024} autoComplete="new-password" /></Field>
            <Field label="再次输入"><Input key={passwordRevision} name="confirmation" type="password" required disabled={busy} maxLength={1024} autoComplete="new-password" /></Field></div>
          <Checkbox className="member-reset-consent" checked={acknowledged} disabled={busy} label={<span><strong>撤销全部会话</strong><small>平台与租户范围的既有会话同时失效。</small></span>} onChange={(_, data) => setAcknowledged(Boolean(data.checked))} />
        </> : <p>此成员将无法登录本租户，当前对话与历史访问立即失效。助手授权保留，可在成员目录恢复。</p>}
          {dialogError ? <p role="alert">{dialogError}</p> : null}</div>
        <footer className="member-dialog-actions"><Button type="button" disabled={busy} onClick={closeDialog}>取消</Button><Button appearance="primary" type="submit" disabled={busy}>{busy ? "正在提交…" : pending?.kind === "reset" ? "重置密码" : "确认停用"}</Button></footer>
      </form></DialogSurface>
    </Dialog>
  </>;
}

function MemberAccountWorkspace({ client, membershipId, onRouteChange }: { client: ControlPlaneClient; membershipId: string; onRouteChange: (route: ConsoleRoute) => void }) {
  const [account, setAccount] = useState<MemberAccount>();
  const [failure, setFailure] = useState("");
  const epoch = client.currentSessionRevision();
  useEffect(() => {
    let active = true;
    const unsubscribe = client.subscribeSessionChange(isActive => { if (!isActive) { active = false; setAccount(undefined); } });
    client.getTenantMember(membershipId).then(value => {
      if (active && epoch === client.currentSessionRevision() && client.hasSession()) setAccount(value);
    }).catch(error => { if (active && epoch === client.currentSessionRevision()) setFailure(presentApiError(error, "读取成员账号").description); });
    return () => { active = false; unsubscribe(); };
  }, [client, epoch, membershipId]);
  return <>
    <PageHeading title="成员账号" context="租户访问与知识" summary="当前成员" status={{ label: "可查看", tone: "success" }} />
    <nav className="member-object-navigation" aria-label="成员工作区"><button aria-current="page">账号信息</button><button disabled>对话功能</button><button disabled>可用助手</button></nav>
    <section className="member-workspace member-section member-account-workspace">{failure ? <p role="alert">{failure}</p> : !account ? <p role="status">正在读取成员账号…</p> : account.membershipRole === "TENANT_ADMINISTRATOR" ? <>
      <h2>管理员账号只读</h2><p>管理员由平台维护。</p></> : <>
      <h2>{account.loginName}</h2><dl className="member-account-facts"><dt>成员</dt><dd>{account.loginName}</dd><dt>登录名</dt><dd>{account.loginName}</dd><dt>状态</dt><dd>{account.status === "ENABLED" ? "可用" : "已停用"}</dd></dl>
      <p>密码不显示。账号停用和密码重置可返回成员目录操作。</p></>}
      <div className="member-form-actions"><Button appearance="primary" onClick={() => onRouteChange({ navigationId: "access", task: "members", page: 1 })}>返回成员目录</Button></div>
    </section>
  </>;
}
