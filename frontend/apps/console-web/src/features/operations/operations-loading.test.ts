import { describe, expect, it, vi } from "vitest";
import type {
  PageResult,
  PlatformOperationsOverviewView,
  PlatformTenantOperationsSummaryView,
} from "../../api/control-plane-client";
import { loadPlatformOperationsSnapshot } from "./operations-loading";

const overview = { range: "DAY_7" } as PlatformOperationsOverviewView;
const tenants: PageResult<PlatformTenantOperationsSummaryView> = {
  items: [],
  pageNumber: 2,
  pageSize: 20,
  totalElements: 0,
  totalPages: 0,
  hasPrevious: true,
  hasNext: false,
};

describe("平台运营总览部分失败", () => {
  it("租户摘要失败时仍返回已经成功取得的平台总览", async () => {
    const summaryFailure = new Error("summary unavailable");
    const client = {
      getPlatformOperationsOverview: vi.fn().mockResolvedValue(overview),
      listPlatformTenantOperations: vi.fn().mockRejectedValue(summaryFailure),
    };

    const result = await loadPlatformOperationsSnapshot(client, "DAY_7", 2);

    expect(result.overview).toBe(overview);
    expect(result.overviewFailure).toBeUndefined();
    expect(result.tenants).toBeUndefined();
    expect(result.tenantFailure).toBe(summaryFailure);
  });

  it("平台总览失败时仍返回已经成功取得的租户摘要", async () => {
    const overviewFailure = new Error("overview unavailable");
    const client = {
      getPlatformOperationsOverview: vi.fn().mockRejectedValue(overviewFailure),
      listPlatformTenantOperations: vi.fn().mockResolvedValue(tenants),
    };

    const result = await loadPlatformOperationsSnapshot(client, "DAY_30", 2);

    expect(result.overview).toBeUndefined();
    expect(result.overviewFailure).toBe(overviewFailure);
    expect(result.tenants).toBe(tenants);
    expect(result.tenantFailure).toBeUndefined();
    expect(client.listPlatformTenantOperations).toHaveBeenCalledWith("DAY_30", 2, 20);
  });
});
