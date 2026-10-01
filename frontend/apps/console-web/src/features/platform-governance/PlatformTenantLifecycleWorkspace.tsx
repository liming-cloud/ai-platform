import { Button, MessageBar, MessageBarBody, MessageBarTitle } from "@fluentui/react-components";
import { PageHeading, StatePanel } from "@foundation/design-system";
import { useEffect, useState, type ReactNode } from "react";
import type { ControlPlaneClient, TenantLifecycleSummary } from "../../api/control-plane-client";
import { TenantProvisioningFlow } from "./TenantProvisioningFlow";
import { TenantOverview } from "./TenantOverview";
import { TenantAdministratorSettings } from "./TenantAdministratorSettings";
import { TenantQuotaSettings } from "./TenantQuotaSettings";
import { TenantFeatureSettings } from "./TenantFeatureSettings";
import { TenantLoginSecuritySettings } from "./TenantLoginSecuritySettings";
import { currentTenantDirectory, readTenantDirectory, type TenantDirectoryRead } from "./tenant-directory-loader";
import { tenantAdministratorPageNumbers } from "./tenant-administrator-pagination";
import type { ConsoleRoute } from "../../session/console-route";
import "./tenant-directory.css";

type TenantRoute = Extract<ConsoleRoute, { navigationId: "platform" }>;
type DirectoryRoute = Extract<TenantRoute, { task: "tenants" }>;

/** 目录只读导航；开户与各设置由自己的独立页面承担写入边界。 */
export function PlatformTenantLifecycleWorkspace({ client, route, onRouteChange }: {
  client: ControlPlaneClient;
  route: TenantRoute;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  if (route.task === "create") return <TenantProvisioningFlow client={client} route={route} onRouteChange={onRouteChange} />;
  if (route.task === "detail") return <TenantOverview key={route.tenantId} client={client} tenantId={route.tenantId} onRouteChange={onRouteChange} />;
  if (route.task === "quotas") return <TenantQuotaSettings key={route.tenantId} client={client} tenantId={route.tenantId} onRouteChange={onRouteChange} />;
  if (route.task === "features") return <TenantFeatureSettings key={route.tenantId} client={client} tenantId={route.tenantId} onRouteChange={onRouteChange} />;
  if (route.task === "security") return <TenantLoginSecuritySettings key={route.tenantId} client={client} tenantId={route.tenantId} onRouteChange={onRouteChange} />;
  if (route.task === "admins") return <TenantAdministratorSettings
    key={`${route.tenantId}:${route.query}:${route.status}:${route.page}:${route.pageSize}`}
    client={client} route={route} onRouteChange={onRouteChange} />;
  return <TenantDirectory client={client} route={route} onRouteChange={onRouteChange} />;
}

function TenantDirectory({ client, route, onRouteChange }: {
  client: ControlPlaneClient;
  route: DirectoryRoute;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const query = route.query ?? "";
  const status = route.status ?? "ALL";
  const pageSize = route.pageSize ?? 20;
  const [searchDraft, setSearchDraft] = useState<{ query: string; value: string }>();
  const [jumpDraft, setJumpDraft] = useState<{ owner: string; value: string; error?: string }>();
  const [revision, setRevision] = useState(0);
  const [reading, setReading] = useState<TenantDirectoryRead>();
  const owner = JSON.stringify([route.page, pageSize, query, status, revision]);
  const search = searchDraft?.query === query ? searchDraft.value : query;
  const jump = jumpDraft?.owner === owner ? jumpDraft.value : String(route.page);
  const jumpError = jumpDraft?.owner === owner ? jumpDraft.error : undefined;
  const { page, error } = currentTenantDirectory(reading, owner) ?? {};
  useEffect(() => readTenantDirectory(client, owner, [route.page, pageSize, query, status], setReading),
    [client, owner, route.page, pageSize, query, status]);
  useEffect(() => {
    const normalized = search.trim();
    if (normalized === query) return;
    const timer = window.setTimeout(() => onRouteChange({ ...route, page: 1, query: normalized }), 350);
    return () => window.clearTimeout(timer);
  }, [search, query, route, onRouteChange]);
  const total = page?.totalElements ?? 0;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const first = page?.items.length ? (route.page - 1) * pageSize + 1 : 0;
  const last = page?.items.length ? first + page.items.length - 1 : 0;
  const outOfRange = !!page && route.page > totalPages;
  const move = (number: number) => onRouteChange({ ...route, page: number });
  const submitJump = () => {
    const number = Number(jump);
    if (!/^[1-9]\d*$/.test(jump) || !Number.isSafeInteger(number) || number > totalPages) {
      setJumpDraft({ owner, value: jump, error: `请输入 1–${totalPages} 之间的整数页码。` }); return;
    }
    setJumpDraft({ owner, value: jump }); move(number);
  };
  return <>
    <PageHeading title="租户目录" context="平台治理" summary="租户生命周期" status={{ label: "只读目录", tone: "success" }}
      action={<Button appearance="primary" onClick={() => onRouteChange({ navigationId: "platform", task: "create", step: 1 })}>建立新租户</Button>} />
    <section className="tenant-directory-page" aria-labelledby="page-title">
      {error ? <><MessageBar intent="error"><MessageBarBody><MessageBarTitle>{error.title}</MessageBarTitle>{error.description}</MessageBarBody></MessageBar>
        <Button onClick={() => setRevision(value => value + 1)}>重新加载</Button></>
        : !page ? <StatePanel kind="loading" title="正在加载租户目录" description="正在读取租户生命周期与管理员资格摘要。" /> : <>
        <div className="tenant-directory-summary"><span><strong>租户目录</strong> · 生命周期摘要</span><span>安全边界异常：尚未核实</span></div>
        <div className="tenant-directory-toolbar">
          <label className="tenant-directory-field"><span>搜索租户</span><input type="search" value={search} maxLength={64}
            placeholder="按组织名称或租户编码搜索" onChange={event => setSearchDraft({ query, value: event.target.value })} /></label>
          <label className="tenant-directory-field tenant-directory-field--status"><span>状态</span>
            <select value={status} onChange={event => onRouteChange({ ...route, status: event.target.value as DirectoryRoute["status"], page: 1 })}>
              <option value="ALL">全部状态</option><option value="PROVISIONING">待启用</option><option value="ACTIVE">活动</option><option value="SUSPENDED">已暂停</option>
            </select></label>
        </div>
        <p className="tenant-directory-filter-feedback" role="status" />
        <div className="tenant-directory-table-shell"><TenantDirectoryTable tenants={page.items} onRouteChange={onRouteChange}
          empty={outOfRange ? <>当前页超出目录范围。<Button onClick={() => move(1)}>返回第一页</Button></> : "没有符合条件的租户。"} />
          <div className="tenant-directory-pagination" aria-label="租户目录分页">
            <span className="tenant-directory-pagination-summary" role="status">共 {total} 条 · {first}–{last} 条</span>
            <label className="tenant-directory-page-size">每页<select aria-label="每页条数" value={pageSize}
              onChange={event => onRouteChange({ ...route, pageSize: Number(event.target.value) as DirectoryRoute["pageSize"], page: 1 })}>
              {[10, 20, 50, 100].map(size => <option key={size} value={size}>{size} 条</option>)}</select></label>
            <div className="tenant-directory-page-actions">
              <Button size="small" disabled={route.page <= 1} onClick={() => move(route.page - 1)}>上一页</Button>
              {tenantAdministratorPageNumbers(route.page, totalPages).map((number, index) => number === "…" ? <span key={`gap-${index}`}>…</span>
                : <Button size="small" key={number} aria-label={`第 ${number} 页`} aria-current={number === route.page ? "page" : undefined}
                  appearance={number === route.page ? "primary" : "secondary"} onClick={() => move(number)}>{number}</Button>)}
              <Button size="small" disabled={route.page >= totalPages} onClick={() => move(route.page + 1)}>下一页</Button>
            </div>
            <form className="tenant-directory-page-jump" noValidate onSubmit={event => { event.preventDefault(); submitJump(); }}>
              <label>跳至<input type="number" min="1" max={totalPages} aria-label="跳转页码" value={jump} onChange={event => setJumpDraft({ owner, value: event.target.value })} /></label><span>页</span>
              <Button size="small" type="submit">跳转</Button>
            </form>
            {jumpError ? <span className="tenant-directory-page-error" role="alert">{jumpError}</span> : null}
          </div>
        </div>
      </>}
    </section>
  </>;
}

/** 实际只读投影的表格；零资格、停用租户及未核实事实不能被模拟为成功。 */
export function TenantDirectoryTable({ tenants, empty, onRouteChange }: {
  tenants: readonly TenantLifecycleSummary[];
  empty: ReactNode;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  return <table className="tenant-directory-table"><thead><tr>
    <th scope="col">租户</th><th scope="col">状态</th><th scope="col">租户管理员</th><th scope="col">操作</th>
  </tr></thead><tbody>{tenants.map(tenant => <tr key={tenant.tenantId} data-tenant-id={tenant.tenantId}>
    <td data-label="租户"><span className="tenant-directory-identity"><strong>{tenant.displayName}</strong><span>{tenant.tenantCode}</span></span></td>
    <td data-label="状态"><span className="tenant-directory-status" data-tone={tenant.status === "ACTIVE" ? "success" : "warning"}>
      {tenant.status === "ACTIVE" ? "活动" : tenant.status === "SUSPENDED" ? "已暂停" : tenant.status === "PROVISIONING" ? "待启用" : "状态未知"}</span></td>
    <td data-label="租户管理员">{tenant.administratorCount > 0 ? "已分配" : "未分配"}</td>
    <td data-label="操作"><Button size="small" onClick={() => onRouteChange({ navigationId: "platform", task: "detail", tenantId: tenant.tenantId })}>查看详情</Button></td>
  </tr>)}{tenants.length === 0 ? <tr className="tenant-directory-empty"><td colSpan={4}>{empty}</td></tr> : null}</tbody></table>;
}
