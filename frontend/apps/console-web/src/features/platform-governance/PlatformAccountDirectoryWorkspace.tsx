import { PageHeading, StatePanel } from "@foundation/design-system";
import {
  Button, Checkbox, Dialog, DialogActions, DialogBody, DialogContent, DialogSurface, DialogTitle,
  Field, Input, MessageBar, MessageBarBody, MessageBarTitle,
} from "@fluentui/react-components";
import { useEffect, useRef, useState } from "react";
import {
  presentApiError, type ControlPlaneClient, type PageResult, type PlatformAccountSummary,
  type UserFacingError,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { tenantAdministratorPageNumbers } from "./tenant-administrator-pagination";
import "./tenant-onboarding.css";
import "./tenant-administrator-settings.css";
import "./platform-account-directory.css";

type AccountRoute = Extract<ConsoleRoute, { task: "accounts" }>;
type Loaded = { owner: string; page: PageResult<PlatformAccountSummary>; allCount: number; activeCount: number };
type Pending =
  | { kind: "create" }
  | { kind: "reset" | "status"; target: PlatformAccountSummary };

/** 平台账户页只写平台管理员身份；行目标由受权目录给出，所有结果从服务端重读。 */
export function PlatformAccountDirectoryWorkspace({ client, route, onRouteChange }: {
  client: ControlPlaneClient;
  route: AccountRoute;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [loaded, setLoaded] = useState<Loaded>();
  const [failure, setFailure] = useState<UserFacingError>();
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [searchDraft, setSearchDraft] = useState({ fromQuery: route.query, value: route.query });
  const [jumpDraft, setJumpDraft] = useState({ fromPage: route.page, value: String(route.page) });
  const [jumpError, setJumpError] = useState("");
  const [pending, setPending] = useState<Pending>();
  const [busy, setBusy] = useState(false);
  const [loginName, setLoginName] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [revokeAcknowledged, setRevokeAcknowledged] = useState(false);
  const [dialogValidation, setDialogValidation] = useState("");
  const loginInput = useRef<HTMLInputElement | null>(null);
  const passwordInput = useRef<HTMLInputElement | null>(null);
  const confirmationInput = useRef<HTMLInputElement | null>(null);
  const revokeInput = useRef<HTMLInputElement | null>(null);
  const trigger = useRef<HTMLElement | null>(null);
  const owner = `${route.query}:${route.status}:${route.page}:${route.pageSize}:${revision}`;
  const data = loaded?.owner === owner ? loaded : undefined;
  const page = data?.page;
  const search = searchDraft.fromQuery === route.query ? searchDraft.value : route.query;
  const jump = jumpDraft.fromPage === route.page ? jumpDraft.value : String(route.page);

  useEffect(() => {
    if (!pending) return;
    const frame = window.requestAnimationFrame(() => {
      if (pending.kind === "create") loginInput.current?.focus();
      if (pending.kind === "reset") passwordInput.current?.focus();
    });
    return () => window.cancelAnimationFrame(frame);
  }, [pending]);

  useEffect(() => {
    if (search === route.query) return;
    const timer = window.setTimeout(() => onRouteChange({ ...route, query: search.trim(), page: 1 }), 350);
    return () => window.clearTimeout(timer);
  }, [onRouteChange, route, search]);

  useEffect(() => {
    let active = true;
    Promise.all([
      client.listPlatformAccounts(route.page, route.pageSize, route.query, route.status),
      client.listPlatformAccounts(1, 10, "", "ALL"),
      client.listPlatformAccounts(1, 10, "", "ACTIVE"),
    ]).then(([result, all, enabled]) => {
      if (!active) return;
      if (route.page > Math.max(1, result.totalPages)) {
        setJumpError(`当前筛选只有 ${Math.max(1, result.totalPages)} 页，请重新选择页码。`);
        setLoaded({ owner, page: result, allCount: all.totalElements, activeCount: enabled.totalElements });
        return;
      }
      setLoaded({ owner, page: result, allCount: all.totalElements, activeCount: enabled.totalElements });
      setFailure(undefined);
    }).catch(error => {
      if (active) { setLoaded(undefined); setFailure(presentApiError(error, "读取平台账户")); }
    });
    const unsubscribe = client.subscribeSessionChange(isActive => {
      if (!isActive) { active = false; setLoaded(undefined); }
    });
    return () => { active = false; unsubscribe(); };
  }, [client, onRouteChange, owner, route]);

  function navigate(change: Partial<AccountRoute>) {
    setNotice("");
    setJumpError("");
    onRouteChange({ ...route, ...change });
  }

  function closeDialog() {
    if (busy) return;
    setPending(undefined);
    setLoginName("");
    setPassword("");
    setConfirmation("");
    setRevokeAcknowledged(false);
    setDialogValidation("");
    window.requestAnimationFrame(() => trigger.current?.focus());
  }

  function openDialog(next: Pending, element: HTMLElement) {
    trigger.current = element;
    setFailure(undefined);
    setDialogValidation("");
    setPending(next);
  }

  async function commit() {
    if (!pending) return;
    if (pending.kind === "create" && !/^[a-z][a-z0-9._-]{2,63}$/.test(loginName.trim())) {
      setDialogValidation("登录名应为 3–64 位小写字母、数字、点、下划线或连字符。");
      loginInput.current?.focus();
      return;
    }
    if (pending.kind !== "status") {
      if (!password || !confirmation) {
        setDialogValidation("请完整填写密码并再次输入。");
        (password ? confirmationInput : passwordInput).current?.focus();
        return;
      }
      if (password !== confirmation) {
        setDialogValidation("两次输入不一致。");
        confirmationInput.current?.focus();
        return;
      }
      if (pending.kind === "reset" && !revokeAcknowledged) {
        setDialogValidation("请确认撤销全部会话。");
        revokeInput.current?.focus();
        return;
      }
    }
    setBusy(true);
    setFailure(undefined);
    try {
      if (pending.kind === "create") {
        await client.createPlatformAccount({ loginName: loginName.trim(), password });
        setNotice(`平台管理员“${loginName.trim()}”已建立；请通过安全渠道交付初始密码。`);
        if (route.page !== 1 || route.query || route.status !== "ALL") {
          onRouteChange({ ...route, page: 1, query: "", status: "ALL" });
        } else setRevision(value => value + 1);
      } else if (pending.kind === "reset") {
        await client.resetPlatformAccountPassword(pending.target.subjectId, password);
        setNotice(`“${pending.target.loginName}”的密码已重置，全部旧会话已失效。`);
        setRevision(value => value + 1);
      } else {
        const disabling = pending.target.status === "ACTIVE";
        await (disabling
          ? client.disablePlatformAccount(pending.target.subjectId)
          : client.enablePlatformAccount(pending.target.subjectId));
        setNotice(`“${pending.target.loginName}”已${disabling ? "停用" : "恢复"}，全部旧会话已失效。`);
        setRevision(value => value + 1);
      }
      setPending(undefined);
      setLoginName("");
      window.requestAnimationFrame(() => trigger.current?.focus());
    } catch (error) {
      const presented = presentApiError(error, pending.kind === "create" ? "创建平台账户"
        : pending.kind === "reset" ? "重置平台账户密码" : "变更平台账户状态");
      setFailure(presented);
      setDialogValidation(`${presented.title}：${presented.description}`);
      if (pending.kind !== "status") passwordInput.current?.focus();
    } finally {
      setPassword("");
      setConfirmation("");
      setRevokeAcknowledged(false);
      setBusy(false);
    }
  }

  const first = page && page.totalElements > 0 ? (page.pageNumber - 1) * page.pageSize + 1 : 0;
  const last = page ? Math.min(page.pageNumber * page.pageSize, page.totalElements) : 0;
  const isStatus = pending?.kind === "status";
  const disabling = isStatus && pending.target.status === "ACTIVE";
  return <>
    <PageHeading title="平台账户" context="平台治理" summary="平台管理员账户"
      status={{ label: "可治理", tone: "success" }}
      action={<Button appearance="primary" onClick={event => openDialog({ kind: "create" }, event.currentTarget)}
        disabled={busy}>创建平台账户</Button>} />
    <section className="tenant-onboarding tenant-admin-page platform-account-page" aria-live="polite">
      {notice ? <MessageBar intent="success"><MessageBarBody><MessageBarTitle>操作已生效</MessageBarTitle>{notice}</MessageBarBody></MessageBar> : null}
      {failure ? <MessageBar intent="error"><MessageBarBody><MessageBarTitle>{failure.title}</MessageBarTitle>{failure.description}</MessageBarBody></MessageBar> : null}
      {!data && !failure ? <StatePanel kind="loading" title="正在加载平台账户" description="正在读取服务端账户目录。" /> : null}
      {data ? <>
        <dl className="tenant-admin-page__summary">
          <div><dt>平台账户</dt><dd>{data.allCount}</dd></div>
          <div><dt>可用账户</dt><dd>{data.activeCount}</dd></div>
        </dl>
        <div className="tenant-admin-page__toolbar">
          <label>搜索账号
            <Input type="search" aria-label="按登录名搜索" placeholder="按登录名搜索" maxLength={64}
              value={search} onChange={(_, value) => setSearchDraft({
                fromQuery: route.query, value: value.value })} />
          </label>
          <label>状态
            <select aria-label="账户状态" value={route.status}
              onChange={event => navigate({ status: event.target.value as AccountRoute["status"], page: 1 })}>
              <option value="ALL">全部状态</option><option value="ACTIVE">可用</option>
              <option value="DISABLED">已停用</option>
            </select>
          </label>
        </div>
        <div className="tenant-admin-page__table-shell">
          <table><thead><tr><th scope="col">登录名</th><th scope="col">账号用途</th>
            <th scope="col">状态</th><th scope="col">最近安全变更</th><th scope="col">操作</th></tr></thead>
            <tbody>{page?.items.map(item => <tr key={item.subjectId}>
              <td data-label="登录名"><strong>{item.loginName}</strong></td>
              <td data-label="账号用途">平台管理员</td>
              <td data-label="状态"><span className="tenant-admin-page__status" data-status={item.status}>
                <span aria-hidden="true">●</span>{item.status === "ACTIVE" ? "可用" : "已停用"}</span></td>
              <td data-label="最近安全变更">{item.lastSecurityChangeAt
                ? new Date(item.lastSecurityChangeAt).toLocaleString("zh-CN") : "初始凭据已设置"}</td>
              <td data-label="操作"><div className="tenant-admin-page__actions">
                <Button appearance="secondary" disabled={busy || item.current}
                  aria-label={item.current ? "当前账户不能由此重置密码" : undefined}
                  onClick={event => openDialog({ kind: "reset", target: item }, event.currentTarget)}>重置密码</Button>
                <details><summary>更多</summary><div>
                  <button type="button" disabled={busy || item.current} onClick={event => {
                    const details = event.currentTarget.closest("details");
                    openDialog({ kind: "status", target: item },
                      details?.querySelector<HTMLElement>("summary") ?? event.currentTarget);
                    details?.removeAttribute("open");
                  }}>{item.status === "ACTIVE" ? "停用账户" : "恢复账户"}</button>
                </div></details>
              </div></td>
            </tr>)}</tbody>
          </table>
          {page?.items.length === 0 ? <div className="platform-account-page__empty">
            <p>{data.allCount === 0 ? "当前没有平台管理员账户。" : "当前条件下没有平台账户。"}</p>
            <Button appearance="secondary" onClick={event => openDialog({ kind: "create" }, event.currentTarget)}>
              创建平台账户</Button>
          </div> : null}
          <div className="tenant-admin-page__pagination" aria-label="账户目录分页">
            <span>共 {page?.totalElements ?? 0} 条 · {first}–{last} 条</span>
            <label className="tenant-admin-page__page-size">每页 <select aria-label="每页条数" value={route.pageSize}
              onChange={event => navigate({ pageSize: Number(event.target.value) as AccountRoute["pageSize"], page: 1 })}>
              <option value="10">10 条</option><option value="20">20 条</option>
              <option value="50">50 条</option><option value="100">100 条</option>
            </select></label>
            <div className="tenant-admin-page__page-actions">
              <Button appearance="secondary" disabled={!page?.hasPrevious} onClick={() => navigate({ page: route.page - 1 })}>上一页</Button>
              {tenantAdministratorPageNumbers(route.page, Math.max(1, page?.totalPages ?? 1)).map((number, index) =>
                number === "…" ? <span key={`ellipsis-${index}`} aria-hidden="true">…</span>
                  : <Button key={number} appearance={number === route.page ? "primary" : "secondary"}
                    aria-label={`第 ${number} 页`} aria-current={number === route.page ? "page" : undefined}
                    onClick={() => navigate({ page: number })}>{number}</Button>)}
              <Button appearance="secondary" disabled={!page?.hasNext} onClick={() => navigate({ page: route.page + 1 })}>下一页</Button>
            </div>
            <form noValidate onSubmit={event => {
              event.preventDefault();
              const target = Number(jump);
              if (!Number.isSafeInteger(target) || target < 1 || target > Math.max(1, page?.totalPages ?? 1)) {
                setJumpError(`请输入 1–${Math.max(1, page?.totalPages ?? 1)} 之间的整数页码。`);
                event.currentTarget.querySelector("input")?.focus();
                return;
              }
              navigate({ page: target });
            }}>
              <label>跳至 <input type="number" aria-label="跳转页码" min="1" max={page?.totalPages || 1}
                aria-invalid={Boolean(jumpError)} value={jump}
                onChange={event => { setJumpError(""); setJumpDraft({
                  fromPage: route.page, value: event.target.value }); }} /> 页</label>
              <Button type="submit" appearance="secondary">跳转</Button>
            </form>
            {jumpError ? <span className="tenant-admin-page__page-error" role="alert">{jumpError}</span> : null}
          </div>
        </div>
      </> : null}
    </section>
    <Dialog open={pending !== undefined} onOpenChange={(_, value) => { if (!value.open) closeDialog(); }}>
      <DialogSurface className="foundation-task-dialog tenant-admin-page__dialog platform-account-page__dialog">
        <DialogBody>
          <DialogTitle><span className="tenant-admin-page__kicker">{pending?.kind === "create" ? "平台管理员账户"
            : pending?.kind === "reset" ? "受控凭据重置" : "账户治理"}</span>
            <span>{pending?.kind === "create" ? "创建平台账户" : pending?.kind === "reset" ? "重置密码"
              : disabling ? "停用人员账户" : "恢复人员账户"}</span>
          </DialogTitle>
          <DialogContent>{pending?.kind === "create" ? <>
            <p>设置一次性初始密码；提交后不再显示。</p>
            <details className="tenant-admin-page__password-help"><summary>密码安全说明</summary>
              <p>请为本人设置独立密码，并通过组织批准的安全渠道单独告知。</p>
            </details>
            <Field label="登录名" required hint="3–64 位小写字母、数字、点、下划线或连字符。">
              <Input ref={loginInput} value={loginName} maxLength={64} placeholder="例如：zhou.demo"
                autoComplete="off" onChange={(_, value) => { setDialogValidation(""); setLoginName(value.value); }} />
            </Field>
            <div className="tenant-admin-page__password-grid">
              <Field label="初始密码" required><Input ref={passwordInput} type="password" maxLength={1024}
                autoComplete="new-password" value={password}
                onChange={(_, value) => { setDialogValidation(""); setPassword(value.value); }} /></Field>
              <Field label="再次输入" required><Input ref={confirmationInput} type="password" maxLength={1024}
                autoComplete="new-password" value={confirmation}
                onChange={(_, value) => { setDialogValidation(""); setConfirmation(value.value); }} /></Field>
            </div>
            <p className="platform-account-page__note">请通过组织批准的安全渠道单独告知本人；页面、存储和审计均不保留明文。</p>
          </> : pending?.kind === "reset" ? <>
            <p>为 {pending.target.loginName} 设置新密码。系统不会回显或保存明文。</p>
            <details className="tenant-admin-page__password-help"><summary>密码安全说明</summary>
              <p>当前密码无法查看。请设置独立的新密码，并通过安全渠道告知本人。</p>
            </details>
            <div className="tenant-admin-page__password-grid">
              <Field label="新密码" required><Input ref={passwordInput} type="password" maxLength={1024}
                autoComplete="new-password" value={password}
                onChange={(_, value) => { setDialogValidation(""); setPassword(value.value); }} /></Field>
              <Field label="再次输入" required><Input ref={confirmationInput} type="password" maxLength={1024}
                autoComplete="new-password" value={confirmation}
                onChange={(_, value) => { setDialogValidation(""); setConfirmation(value.value); }} /></Field>
            </div>
            <div className="tenant-admin-page__revoke"><Checkbox ref={revokeInput} checked={revokeAcknowledged}
              aria-label="确认撤销全部会话"
              onChange={(_, value) => { setDialogValidation(""); setRevokeAcknowledged(Boolean(value.checked)); }}
              label={<span className="tenant-admin-page__revoke-copy"><strong>撤销全部会话</strong>
                <small>平台与租户范围的既有会话同时失效。</small></span>} /></div>
          </> : isStatus ? <>
            <p>确认{disabling ? "停用" : "恢复"} {pending.target.loginName}？</p>
            <dl className="tenant-admin-page__impact">
              <div><dt>影响</dt><dd>{disabling ? "立即禁止登录并撤销全部既有会话。" : "恢复登录资格，但不会恢复曾被撤销的会话。"}</dd></div>
              <div><dt>恢复</dt><dd>{disabling ? "可由其他平台管理员恢复此账户。" : "需要时可再次停用。"}</dd></div>
              <div><dt>审计</dt><dd>记录操作者、目标账户、操作、结果与非秘密版本。</dd></div>
            </dl>
          </> : null}
            {dialogValidation ? <p className="tenant-admin-page__validation" role="alert">{dialogValidation}</p> : null}
          </DialogContent>
          <DialogActions>
            <Button appearance="secondary" disabled={busy} onClick={closeDialog}>取消</Button>
            <Button appearance="primary" disabled={busy || !pending} onClick={() => void commit()}>
              {busy ? "正在提交" : pending?.kind === "create" ? "创建账户" : pending?.kind === "reset" ? "重置密码"
                : disabling ? "确认停用" : "确认恢复"}</Button>
          </DialogActions>
        </DialogBody>
      </DialogSurface>
    </Dialog>
  </>;
}
