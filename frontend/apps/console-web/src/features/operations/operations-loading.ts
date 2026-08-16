import type {
  ControlPlaneClient,
  OperationsRange,
  PageResult,
  PlatformOperationsOverviewView,
  PlatformTenantOperationsSummaryView,
} from "../../api/control-plane-client";

type PlatformOperationsClient = Pick<
  ControlPlaneClient,
  "getPlatformOperationsOverview" | "listPlatformTenantOperations"
>;

export interface PlatformOperationsSnapshot {
  overview?: PlatformOperationsOverviewView;
  overviewFailure?: unknown;
  tenants?: PageResult<PlatformTenantOperationsSummaryView>;
  tenantFailure?: unknown;
}

/** 平台总览与租户摘要独立结算，使单一读取失败不会遮蔽另一份可用数据。 */
export async function loadPlatformOperationsSnapshot(
  client: PlatformOperationsClient,
  range: OperationsRange,
  page: number,
): Promise<PlatformOperationsSnapshot> {
  const [overview, tenants] = await Promise.allSettled([
    client.getPlatformOperationsOverview(range),
    client.listPlatformTenantOperations(range, page, 20),
  ]);
  return {
    overview: overview.status === "fulfilled" ? overview.value : undefined,
    overviewFailure: overview.status === "rejected" ? overview.reason : undefined,
    tenants: tenants.status === "fulfilled" ? tenants.value : undefined,
    tenantFailure: tenants.status === "rejected" ? tenants.reason : undefined,
  };
}
