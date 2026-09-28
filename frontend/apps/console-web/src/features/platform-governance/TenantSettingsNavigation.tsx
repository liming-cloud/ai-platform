import type { ConsoleRoute } from "../../session/console-route";

/** 仅开放已交付的租户设置入口；其余标题不伪装成可操作链接。 */
export function TenantSettingsNavigation({ tenantId, active, onRouteChange }: {
  tenantId: string;
  active: "detail" | "admins" | "quotas" | "features";
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  return <nav className="tenant-overview__settings" aria-label="租户设置">
    <a href={`/platform/tenants/${encodeURIComponent(tenantId)}`}
      aria-current={active === "detail" ? "page" : undefined}
      onClick={event => { event.preventDefault(); onRouteChange({ navigationId: "platform", task: "detail", tenantId }); }}>概览</a>
    <a href={`/platform/tenants/${encodeURIComponent(tenantId)}/admins`}
      aria-current={active === "admins" ? "page" : undefined}
      onClick={event => { event.preventDefault(); onRouteChange({ navigationId: "platform", task: "admins", tenantId,
        query: "", status: "ALL", page: 1, pageSize: 20 }); }}>管理员</a>
    <a href={`/platform/tenants/${encodeURIComponent(tenantId)}/quotas`}
      aria-current={active === "quotas" ? "page" : undefined}
      onClick={event => { event.preventDefault(); onRouteChange({ navigationId: "platform", task: "quotas", tenantId }); }}>资源配额</a>
    <a href={`/platform/tenants/${encodeURIComponent(tenantId)}/features`}
      aria-current={active === "features" ? "page" : undefined}
      onClick={event => { event.preventDefault(); onRouteChange({ navigationId: "platform", task: "features", tenantId }); }}>可用功能</a>
    <span aria-disabled="true">登录安全</span>
  </nav>;
}
