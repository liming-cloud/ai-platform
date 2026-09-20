import { describe, expect, it, vi } from "vitest";
import type { TenantLifecycle, TenantAdministratorSummary, PageResult } from "../../api/control-plane-client";
import { currentTenantOverview, readTenantOverview, type TenantOverviewRead } from "./tenant-overview-loader";

describe("租户概览异步读取归属", () => {
  it("配额接口不可用时概览仍只读取自身所需的租户与管理员事实", async () => {
    const tenant = { displayName: "已开户租户" } as TenantLifecycle;
    const administrators = { items: [], totalElements: 0 } as unknown as PageResult<TenantAdministratorSummary>;
    const client = { hasSession: () => true, getTenantLifecycle: () => Promise.resolve(tenant),
      getTenantQuotas: vi.fn(async () => { throw new Error("概览不应依赖配额读取"); }),
      listTenantAdministrators: () => Promise.resolve(administrators) };
    const events: TenantOverviewRead[] = [];
    readTenantOverview(client, "tenant", "tenant:0", value => events.push(value));
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(events).toEqual([{ owner: "tenant:0", data: { tenant, administrators } }]);
    expect(client.getTenantQuotas).not.toHaveBeenCalled();
  });
  it("概览读取真实管理员摘要并限制读取量", async () => {
    const administrator = { loginName: "actual.admin", status: "ENABLED" } as TenantAdministratorSummary;
    const administrators = { items: [administrator], totalElements: 1 } as PageResult<TenantAdministratorSummary>;
    const client = { hasSession: () => true, getTenantLifecycle: () => Promise.resolve({} as TenantLifecycle),
      listTenantAdministrators: vi.fn(async () => administrators) };
    const events: TenantOverviewRead[] = [];
    readTenantOverview(client, "tenant", "tenant:0", value => events.push(value));
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(client.listTenantAdministrators).toHaveBeenCalledWith("tenant", 1, 1);
    expect(events[0]?.data).toMatchObject({ administrators });
  });
  it("新目标或重新读取期间不呈现上一轮租户事实", () => {
    const previous = { owner: "tenant-a:0", data: { tenant: { displayName: "上一租户" } } } as TenantOverviewRead;
    expect(currentTenantOverview(previous, "tenant-b:0")).toBeUndefined();
    expect(currentTenantOverview(previous, "tenant-a:1")).toBeUndefined();
    expect(currentTenantOverview(previous, "tenant-a:0")?.data?.tenant.displayName).toBe("上一租户");
  });
  it("取消后的旧成功结果不覆盖新目标的拒绝结果", async () => {
    let resolveOld!: (value: TenantLifecycle) => void;
    const old = new Promise<TenantLifecycle>(resolve => { resolveOld = resolve; });
    const events: TenantOverviewRead[] = [];
    const client = { hasSession: () => true, getTenantLifecycle: (id: string) => id === "old" ? old : Promise.reject(new Error("不可展示的原始响应")), listTenantAdministrators: () => Promise.resolve({ items: [], totalElements: 0 } as unknown as PageResult<TenantAdministratorSummary>) };
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
    const client = { hasSession: () => active, getTenantLifecycle: () => Promise.resolve({} as TenantLifecycle), listTenantAdministrators: () => Promise.resolve({ items: [], totalElements: 0 } as unknown as PageResult<TenantAdministratorSummary>) };
    readTenantOverview(client, "tenant", "tenant:0", result => events.push(result));
    active = false;
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(events).toEqual([]);
  });
});
