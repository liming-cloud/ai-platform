import { presentApiError, type ControlPlaneClient, type PageResult, type TenantAdministratorSummary, type TenantLifecycle, type TenantQuotaSummary, type UserFacingError } from "../../api/control-plane-client";

/** 读取结果绑定目标和读取轮次，跨目标切换及重试均不能复用旧正文。 */
export interface TenantOverviewRead {
  owner: string;
  data?: { tenant: TenantLifecycle; quotas: TenantQuotaSummary; administrators: PageResult<TenantAdministratorSummary> };
  failure?: UserFacingError;
}

export function currentTenantOverview(value: TenantOverviewRead | undefined, owner: string): TenantOverviewRead | undefined {
  return value?.owner === owner ? value : undefined;
}

/** 返回取消函数；旧异步结果及失效会话不得重新写入受保护界面。 */
export function readTenantOverview(
  client: Pick<ControlPlaneClient, "getTenantLifecycle" | "getTenantQuotas" | "listTenantAdministrators" | "hasSession">,
  tenantId: string,
  owner: string,
  receive: (value: TenantOverviewRead) => void,
): () => void {
  let current = true;
  void Promise.all([client.getTenantLifecycle(tenantId), client.getTenantQuotas(tenantId), client.listTenantAdministrators(tenantId, 1, 1)]).then(([tenant, quotas, administrators]) => {
    if (current && client.hasSession()) receive({ owner, data: { tenant, quotas, administrators } });
  }, error => {
    if (current && client.hasSession()) receive({ owner, failure: presentApiError(error, "读取租户概览") });
  });
  return () => { current = false; };
}
