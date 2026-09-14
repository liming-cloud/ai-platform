import { describe, expect, it } from "vitest";
import type { TenantLifecycle, TenantQuotaSummary } from "../../api/control-plane-client";
import { currentTenantOverview, readTenantOverview, type TenantOverviewRead } from "./tenant-overview-loader";

describe("租户概览异步读取归属", () => {
  it("新目标或重新读取期间不呈现上一轮租户事实", () => {
    const previous = { owner: "tenant-a:0", data: { tenant: { displayName: "上一租户" }, quotas: {} } } as TenantOverviewRead;
    expect(currentTenantOverview(previous, "tenant-b:0")).toBeUndefined();
    expect(currentTenantOverview(previous, "tenant-a:1")).toBeUndefined();
    expect(currentTenantOverview(previous, "tenant-a:0")?.data?.tenant.displayName).toBe("上一租户");
  });
  it("取消后的旧成功结果不覆盖新目标的拒绝结果", async () => {
    let resolveOld!: (value: TenantLifecycle) => void;
    const old = new Promise<TenantLifecycle>(resolve => { resolveOld = resolve; });
    const events: TenantOverviewRead[] = [];
    const client = { hasSession: () => true, getTenantLifecycle: (id: string) => id === "old" ? old : Promise.reject(new Error("不可展示的原始响应")), getTenantQuotas: () => Promise.resolve({} as TenantQuotaSummary) };
    const cancel = readTenantOverview(client, "old", "old:0", result => events.push(result));
    cancel();
    readTenantOverview(client, "new", "new:0", result => events.push(result));
    resolveOld({ displayName: "上一租户" } as TenantLifecycle);
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(events).toHaveLength(1);
    expect(events[0]?.owner).toBe("new:0");
    expect(events[0]?.data).toBeUndefined();
    expect(JSON.stringify(events)).not.toContain("不可展示的原始响应");
  });
  it("会话失效后的异步成功不能恢复受保护数据", async () => {
    let active = true;
    const events: TenantOverviewRead[] = [];
    const client = { hasSession: () => active, getTenantLifecycle: () => Promise.resolve({} as TenantLifecycle), getTenantQuotas: () => Promise.resolve({} as TenantQuotaSummary) };
    readTenantOverview(client, "tenant", "tenant:0", result => events.push(result));
    active = false;
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(events).toEqual([]);
  });
});
