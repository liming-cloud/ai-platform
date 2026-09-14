import type { ControlPlaneClient } from "../../api/control-plane-client";
import { restoreTenantDraft, serializeTenantDraft, type TenantDraft } from "./tenant-provisioning";

const draftKey = "foundation.tenant-onboarding.draft";
type DraftSession = Pick<ControlPlaneClient, "subscribeSessionChange" | "hasSession">;
const observedClients = new WeakSet<DraftSession>();

/** 摘要只绑定草稿所属浏览器会话，不作为身份或权限；原 CSRF 值不落存储。 */
export async function tenantDraftOwner(client: DraftSession): Promise<string | undefined> {
  if (!observedClients.has(client)) {
    observedClients.add(client);
    // 监听保持到客户端生命周期结束，离开向导后退出同样会删除草稿。
    client.subscribeSessionChange(active => { if (!active) clearTenantDraft(); });
  }
  try {
    const csrf = document.cookie.split(";").map(item => item.trim()).find(item => item.startsWith("__Host-foundation-csrf="))?.slice("__Host-foundation-csrf=".length);
    if (!csrf || !client.hasSession()) { clearTenantDraft(); return undefined; }
    const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(csrf));
    if (!client.hasSession()) { clearTenantDraft(); return undefined; }
    return Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, "0")).join("");
  } catch { clearTenantDraft(); return undefined; }
}

export function loadTenantDraft(owner: string): TenantDraft | undefined {
  try {
    const restored = restoreTenantDraft(owner, sessionStorage.getItem(draftKey));
    if (!restored) clearTenantDraft();
    return restored;
  } catch { return undefined; }
}

export function saveTenantDraft(owner: string | undefined, draft: TenantDraft): void {
  if (!owner) return;
  try { sessionStorage.setItem(draftKey, serializeTenantDraft(owner, draft)); } catch { /* 禁用存储时允许当页填写，但不宣称已保存草稿。 */ }
}

export function clearTenantDraft(): void {
  try { sessionStorage.removeItem(draftKey); } catch { /* 存储被禁用时没有可清除的持久草稿。 */ }
}
