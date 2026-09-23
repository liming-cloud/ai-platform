import { PageHeading } from "@foundation/design-system";
import {
  Button, Checkbox, Dialog, DialogActions, DialogBody, DialogContent, DialogSurface, DialogTitle,
  Field, Input, MessageBar, MessageBarBody, MessageBarTitle,
} from "@fluentui/react-components";
import { useEffect, useRef, useState } from "react";
import {
  presentApiError, type ControlPlaneClient, type PageResult, type TenantAdministratorSummary,
  type TenantLifecycle, type UserFacingError,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { TenantSettingsNavigation } from "./TenantSettingsNavigation";
import "./tenant-onboarding.css";
import "./tenant-administrator-settings.css";

type AdminRoute = Extract<ConsoleRoute, { navigationId: "platform"; task: "admins" }>;
type Loaded = {
  owner: string;
  tenant: TenantLifecycle;
  page: PageResult<TenantAdministratorSummary>;
  administratorCount: number;
  enabledCount: number;
};
type Pending = { kind: "status" | "reset"; target: TenantAdministratorSummary };

/** 平台受权的租户管理员独立设置页；所有目录与变更结果都从服务端事实恢复。 */
export function TenantAdministratorSettings({ client, route, onRouteChange }: {
  client: ControlPlaneClient;
  route: AdminRoute;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [loaded, setLoaded] = useState<Loaded>();
  const [failure, setFailure] = useState<UserFacingError>();
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [search, setSearch] = useState(route.query);
  const [jump, setJump] = useState(String(route.page));
  const [pending, setPending] = useState<Pending>();
  const [busy, setBusy] = useState(false);
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [revokeAcknowledged, setRevokeAcknowledged] = useState(false);
  const trigger = useRef<HTMLElement | null>(null);
  const owner = `${route.tenantId}:${route.query}:${route.status}:${route.page}:${route.pageSize}:${revision}`;
  const data = loaded?.owner === owner ? loaded : undefined;

  useEffect(() => {
    if (search === route.query) return;
    const timer = window.setTimeout(() => onRouteChange({ ...route, query: search.trim(), page: 1 }), 350);
    return () => window.clearTimeout(timer);
  }, [onRouteChange, route, search]);

  useEffect(() => {
    let active = true;
    Promise.all([
      client.getTenantLifecycle(route.tenantId),
      client.listTenantAdministrators(route.tenantId, route.page, route.pageSize, route.query, route.status),
      client.listTenantAdministrators(route.tenantId, 1, 1),
      client.listTenantAdministrators(route.tenantId, 1, 1, "", "ENABLED"),
    ]).then(([tenant, page, all, enabled]) => {
      if (!active) return;
      if (route.page > 1 && route.page > Math.max(1, page.totalPages)) {
        onRouteChange({ ...route, page: Math.max(1, page.totalPages) });
        return;
      }
      setLoaded({ owner, tenant, page, administratorCount: all.totalElements,
        enabledCount: enabled.totalElements });
      setFailure(undefined);
    }).catch(error => {
      if (active) setFailure(presentApiError(error, "读取租户管理员"));
    });
    const unsubscribe = client.subscribeSessionChange(isActive => {
      if (!isActive) { active = false; setLoaded(undefined); }
    });
    return () => { active = false; unsubscribe(); };
  }, [client, onRouteChange, owner, route]);

  function navigate(change: Partial<AdminRoute>) {
    setNotice("");
    onRouteChange({ ...route, ...change });
  }

  function closeDialog() {
    if (busy) return;
    setPending(undefined);
    setPassword("");
    setConfirmation("");
    setRevokeAcknowledged(false);
    window.requestAnimationFrame(() => trigger.current?.focus());
  }

  function openDialog(kind: Pending["kind"], target: TenantAdministratorSummary, element: HTMLElement) {
    trigger.current = element;
    setFailure(undefined);
    setPending({ kind, target });
  }

  async function commit() {
    if (!pending) return;
    setBusy(true);
    setFailure(undefined);
    try {
      const { target } = pending;
      if (pending.kind === "reset") {
        if (!password || password !== confirmation || !revokeAcknowledged) return;
        await client.resetTenantAdministratorPassword(
          route.tenantId, target.membershipReference, target.version, password);
        setNotice(`“${target.loginName}”的密码已重置，旧会话已失效。`);
      } else {
        const disabling = target.status === "ENABLED";
        await (disabling
          ? client.disableTenantAdministrator(route.tenantId, target.membershipReference, target.version)
          : client.restoreTenantAdministrator(route.tenantId, target.membershipReference, target.version));
        setNotice(`“${target.loginName}”在当前租户的管理员资格已${disabling ? "停用" : "恢复"}。`);
      }
      setPending(undefined);
      setRevision(value => value + 1);
      window.requestAnimationFrame(() => trigger.current?.focus());
    } catch (error) {
      setFailure(presentApiError(error, pending.kind === "reset" ? "重置管理员密码" : "变更管理员资格"));
    } finally {
      setPassword("");
      setConfirmation("");
      setRevokeAcknowledged(false);
      setBusy(false);
    }
  }

  const tenantStatus = data?.tenant.status === "ACTIVE" ? "活动"
    : data?.tenant.status === "SUSPENDED" ? "已暂停" : "准备中";
  const page = data?.page;
  const first = page && page.totalElements > 0 ? (page.pageNumber - 1) * page.pageSize + 1 : 0;
  const last = page ? Math.min(page.pageNumber * page.pageSize, page.totalElements) : 0;

  return <>
    <PageHeading title="租户管理员" context="平台治理" summary={data?.tenant.displayName}
      status={data ? { label: tenantStatus, tone: data.tenant.status === "ACTIVE" ? "success" : "warning" } : undefined}
      action={<Button appearance="primary" onClick={() => onRouteChange({ navigationId: "platform", task: "tenants", page: 1 })}>返回租户目录</Button>} />
    <section className="tenant-onboarding tenant-overview tenant-admin-page">
      <TenantSettingsNavigation tenantId={route.tenantId} active="admins" onRouteChange={onRouteChange} />
      {notice ? <MessageBar intent="success"><MessageBarBody><MessageBarTitle>操作已生效</MessageBarTitle>{notice}</MessageBarBody></MessageBar> : null}
      {failure ? <MessageBar intent="error"><MessageBarBody><MessageBarTitle>{failure.title}</MessageBarTitle>{failure.description}</MessageBarBody></MessageBar> : null}
      {!data ? <p role="status">正在读取租户管理员…</p> : <>
        <dl className="tenant-admin-page__summary">
          <div><dt>租户管理员</dt><dd>{data.administratorCount}</dd></div>
          <div><dt>可用管理员资格</dt><dd>{data.enabledCount}</dd></div>
        </dl>
        <div className="tenant-admin-page__toolbar">
          <label>搜索账号
            <Input type="search" aria-label="按登录名搜索" placeholder="按登录名搜索"
              value={search} onChange={(_, value) => setSearch(value.value)} />
          </label>
          <label>状态
            <select aria-label="管理员状态" value={route.status}
              onChange={event => navigate({ status: event.target.value as AdminRoute["status"], page: 1 })}>
              <option value="ALL">全部状态</option><option value="ENABLED">可用</option><option value="DISABLED">已停用</option>
            </select>
          </label>
        </div>
        <div className="tenant-admin-page__table-shell">
          <table><thead><tr><th scope="col">登录名</th><th scope="col">账号用途</th><th scope="col">状态</th>
            <th scope="col">最近安全变更</th><th scope="col">操作</th></tr></thead>
            <tbody>{page?.items.map(item => <tr key={item.membershipReference}>
              <td data-label="登录名"><strong>{item.loginName}</strong></td>
              <td data-label="账号用途">当前租户管理员</td>
              <td data-label="状态"><span className="tenant-admin-page__status" data-status={item.status}>
                <span aria-hidden="true">●</span>{item.status === "ENABLED" ? "可用" : "已停用"}</span></td>
              <td data-label="最近安全变更">{item.lastSecurityChangeAt
                ? new Date(item.lastSecurityChangeAt).toLocaleString("zh-CN") : "初始凭据已设置"}</td>
              <td data-label="操作"><div className="tenant-admin-page__actions">
                <Button appearance="secondary" onClick={event => openDialog("reset", item, event.currentTarget)}>重置密码</Button>
                <details><summary>更多</summary><div>
                  <button type="button" onClick={event => {
                    event.currentTarget.closest("details")?.removeAttribute("open");
                    openDialog("status", item, event.currentTarget);
                  }}>{item.status === "ENABLED" ? "停用管理员资格" : "恢复管理员资格"}</button>
                </div></details>
              </div></td>
            </tr>)}</tbody>
          </table>
          {page?.items.length === 0 ? <p className="tenant-admin-page__empty">当前条件下没有租户管理员。</p> : null}
          <div className="tenant-admin-page__pagination" aria-label="管理员目录分页">
            <span>共 {page?.totalElements ?? 0} 条 · {first}–{last} 条</span>
            <div><label>每页 <select aria-label="每页条数" value={route.pageSize}
              onChange={event => navigate({ pageSize: Number(event.target.value) as 20 | 50, page: 1 })}>
              <option value="20">20 条</option><option value="50">50 条</option>
            </select></label>
              <Button appearance="secondary" disabled={!page?.hasPrevious} onClick={() => navigate({ page: route.page - 1 })}>上一页</Button>
              <span className="tenant-admin-page__current">{route.page}</span>
              <Button appearance="secondary" disabled={!page?.hasNext} onClick={() => navigate({ page: route.page + 1 })}>下一页</Button>
              <form onSubmit={event => { event.preventDefault(); const target = Number(jump);
                if (Number.isSafeInteger(target) && target >= 1 && target <= (page?.totalPages ?? 0)) navigate({ page: target }); }}>
                <label>跳至 <input type="number" aria-label="跳转页码" min="1" max={page?.totalPages || 1}
                  value={jump} onChange={event => setJump(event.target.value)} /> 页</label>
                <Button type="submit" appearance="secondary">跳转</Button>
              </form>
            </div>
          </div>
        </div>
      </>}
    </section>
    <Dialog open={pending !== undefined} onOpenChange={(_, value) => { if (!value.open) closeDialog(); }}>
      <DialogSurface className="foundation-task-dialog tenant-admin-page__dialog">
        <DialogBody>
          <DialogTitle>{pending?.kind === "reset" ? "重置密码" : pending?.target.status === "ENABLED" ? "停用管理员资格" : "恢复管理员资格"}</DialogTitle>
          <DialogContent>{pending?.kind === "reset" ? <>
            <p>为“{pending.target.loginName}”重置全局密码。该身份在所有租户的旧会话都会失效，需使用新密码重新登录。</p>
            <Field label="新密码" required><Input type="password" maxLength={1024} autoComplete="new-password"
              value={password} onChange={(_, value) => setPassword(value.value)} /></Field>
            <Field label="再次输入" required validationMessage={confirmation && password !== confirmation ? "两次输入不一致" : undefined}>
              <Input type="password" maxLength={1024} autoComplete="new-password" value={confirmation}
                onChange={(_, value) => setConfirmation(value.value)} /></Field>
            <Checkbox checked={revokeAcknowledged} onChange={(_, value) => setRevokeAcknowledged(Boolean(value.checked))}
              label="确认撤销该身份在所有租户的旧会话" />
          </> : pending ? <p>“{pending.target.loginName}”在当前租户的管理员资格将被
            {pending.target.status === "ENABLED" ? "停用" : "恢复"}。全局账户状态不变；旧会话将失效。
            {pending.target.status === "ENABLED" ? "若这是最后一位启用管理员，服务端会拒绝操作。" : "恢复时将重新校验平台与租户人员范围。"}</p> : null}</DialogContent>
          <DialogActions>
            <Button appearance="secondary" disabled={busy} onClick={closeDialog}>取消</Button>
            <Button appearance="primary" disabled={busy || !pending || (pending.kind === "reset"
              && (!password || password !== confirmation || !revokeAcknowledged))}
              onClick={() => void commit()}>{busy ? "正在提交" : pending?.kind === "reset" ? "重置密码" : "确认提交"}</Button>
          </DialogActions>
        </DialogBody>
      </DialogSurface>
    </Dialog>
  </>;
}
