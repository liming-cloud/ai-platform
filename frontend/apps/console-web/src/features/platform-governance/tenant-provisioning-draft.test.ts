import { afterEach, describe, expect, it, vi } from "vitest";
import { clearTenantDraft, loadTenantDraft, saveTenantDraft, tenantDraftOwner } from "./tenant-provisioning-draft";
import { defaultTenantDraft } from "./tenant-provisioning";

afterEach(() => vi.unstubAllGlobals());

function environment(cookie = "__Host-foundation-csrf=local-session-value") {
  const values = new Map<string, string>();
  const listeners: ((active: boolean) => void)[] = [];
  let active = true;
  vi.stubGlobal("document", { cookie });
  vi.stubGlobal("sessionStorage", { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key) });
  return { values, client: { hasSession: () => active, subscribeSessionChange(listener: (active: boolean) => void) { listeners.push(listener); return () => {}; } }, logout() { active = false; listeners.forEach(listener => listener(false)); } };
}

describe("开户草稿的浏览器会话归属", () => {
  it("仅写入摘要，刷新同一会话可恢复，另一会话不能恢复", async () => {
    const env = environment();
    const owner = await tenantDraftOwner(env.client);
    expect(owner).toMatch(/^[a-f0-9]{64}$/);
    saveTenantDraft(owner, { ...defaultTenantDraft(), displayName: "青岚科技" });
    expect(loadTenantDraft(owner!)?.displayName).toBe("青岚科技");
    expect([...env.values.values()].join()).not.toContain("local-session-value");
    vi.stubGlobal("document", { cookie: "__Host-foundation-csrf=second-session" });
    const second = await tenantDraftOwner(env.client);
    expect(loadTenantDraft(second!)).toBeUndefined();
    expect(env.values.size).toBe(0);
  });
  it("退出清除草稿，取消及成功也使用同一清除入口", async () => {
    const env = environment();
    const owner = await tenantDraftOwner(env.client);
    saveTenantDraft(owner, defaultTenantDraft());
    env.logout();
    expect(env.values.size).toBe(0);
    saveTenantDraft("owner", defaultTenantDraft());
    clearTenantDraft();
    expect(env.values.size).toBe(0);
  });
  it("防护 cookie 缺失时失败关闭，不保留旧草稿", async () => {
    const env = environment("");
    saveTenantDraft("old-owner", defaultTenantDraft());
    expect(await tenantDraftOwner(env.client)).toBeUndefined();
    expect(env.values.size).toBe(0);
  });
  it("旧摘要异步返回不能在退出后恢复草稿", async () => {
    const env = environment();
    let resolve!: (value: ArrayBuffer) => void;
    vi.stubGlobal("crypto", { subtle: { digest: () => new Promise<ArrayBuffer>(complete => { resolve = complete; }) } });
    const pending = tenantDraftOwner(env.client);
    env.logout();
    resolve(new ArrayBuffer(32));
    expect(await pending).toBeUndefined();
    expect(env.values.size).toBe(0);
  });
});
