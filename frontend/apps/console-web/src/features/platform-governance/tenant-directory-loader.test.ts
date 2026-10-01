import { describe, expect, it, vi } from "vitest";
import type { PageResult, TenantLifecycleSummary } from "../../api/control-plane-client";
import { currentTenantDirectory, readTenantDirectory, type TenantDirectoryRead } from "./tenant-directory-loader";

const page: PageResult<TenantLifecycleSummary> = {
  items: [{ tenantId: "100000000000000001", tenantCode: "fixture", displayName: "当前目录事实",
    status: "ACTIVE", version: 1, createdAt: "2026-10-01T00:00:00Z", lastChangedAt: "2026-10-01T00:00:00Z",
    administratorCount: 1 }], pageNumber: 1, pageSize: 20, totalElements: 1, totalPages: 1,
  hasPrevious: false, hasNext: false,
};

function gateway(result: Promise<PageResult<TenantLifecycleSummary>>) {
  let active = true;
  let listener: (value: boolean) => void = () => undefined;
  const unsubscribe = vi.fn();
  const client = { listTenantLifecycles: vi.fn(() => result), hasSession: () => active,
    subscribeSessionChange: (next: (value: boolean) => void) => { listener = next; return unsubscribe; } };
  return { client, unsubscribe, change: (value: boolean) => { active = value; listener(value); } };
}

async function settled() { await new Promise(resolve => setTimeout(resolve, 0)); }

describe("租户目录的异步查询归属", () => {
  it("只读取服务端当前页与条件，不逐行读取管理员或在前端过滤", async () => {
    const { client } = gateway(Promise.resolve(page));
    const events: TenantDirectoryRead[] = [];
    readTenantDirectory(client, "owner", [1, 20, "演示 %_", "ACTIVE"], value => events.push(value));
    await settled();
    expect(client.listTenantLifecycles).toHaveBeenCalledExactlyOnceWith(1, 20, "演示 %_", "ACTIVE");
    expect(events).toEqual([{ owner: "owner", page }]);
  });

  it("筛选、页码或刷新修订改变后立即隐藏旧事实", () => {
    const read = { owner: "old", page };
    expect(currentTenantDirectory(read, "new")).toBeUndefined();
    expect(currentTenantDirectory(read, "old")?.page).toBe(page);
  });

  it("取消后的旧成功不能覆盖新请求的失败", async () => {
    let resolve!: (value: PageResult<TenantLifecycleSummary>) => void;
    const previous = gateway(new Promise<PageResult<TenantLifecycleSummary>>(done => { resolve = done; }));
    const current = gateway(Promise.reject(new Error("不可外显的原始响应")));
    const events: TenantDirectoryRead[] = [];
    const cancel = readTenantDirectory(previous.client, "old", [1], value => events.push(value));
    cancel();
    readTenantDirectory(current.client, "new", [2], value => events.push(value));
    resolve(page); await settled();
    expect(events).toHaveLength(1);
    expect(events[0]?.owner).toBe("new");
    expect(events[0]?.page).toBeUndefined();
    expect(events[0]?.error).toBeDefined();
    expect(JSON.stringify(events)).not.toContain("不可外显的原始响应");
    expect(previous.unsubscribe).toHaveBeenCalledOnce();
  });

  it("取消后的旧失败不清除新查询事实", async () => {
    let reject!: (value: Error) => void;
    const previous = gateway(new Promise<PageResult<TenantLifecycleSummary>>((_, failure) => { reject = failure; }));
    const current = gateway(Promise.resolve(page));
    const events: TenantDirectoryRead[] = [];
    const cancel = readTenantDirectory(previous.client, "old", [1], value => events.push(value));
    cancel();
    readTenantDirectory(current.client, "new", [2], value => events.push(value));
    reject(new Error("迟到的失败")); await settled();
    expect(events).toEqual([{ owner: "new", page }]);
  });

  it("会话失效清除已加载事实，恢复会话也不重新激活旧读取", async () => {
    const transport = gateway(Promise.resolve(page));
    const events: TenantDirectoryRead[] = [];
    readTenantDirectory(transport.client, "old", [1], value => events.push(value));
    await settled();
    transport.change(false); transport.change(true); await settled();
    expect(events).toEqual([{ owner: "old", page }, { owner: "old" }]);
    expect(transport.client.listTenantLifecycles).toHaveBeenCalledOnce();
  });

  it("失效后迟到成功与清理后的旧通知均不能恢复或清除数据", async () => {
    let resolve!: (value: PageResult<TenantLifecycleSummary>) => void;
    const transport = gateway(new Promise<PageResult<TenantLifecycleSummary>>(done => { resolve = done; }));
    const events: TenantDirectoryRead[] = [];
    const cancel = readTenantDirectory(transport.client, "old", [1], value => events.push(value));
    transport.change(false); transport.change(true); resolve(page); await settled();
    cancel(); transport.change(false);
    expect(events).toEqual([{ owner: "old" }]);
  });

  it("没有有效会话时不发起受保护读取", async () => {
    const transport = gateway(Promise.resolve(page));
    transport.change(false);
    const events: TenantDirectoryRead[] = [];
    readTenantDirectory(transport.client, "owner", [1], value => events.push(value));
    await settled();
    expect(transport.client.listTenantLifecycles).not.toHaveBeenCalled();
    expect(events).toEqual([]);
  });
});
