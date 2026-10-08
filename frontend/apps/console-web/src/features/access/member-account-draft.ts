import type { ControlPlaneClient, MemberConversationFeatures } from "../../api/control-plane-client";

export const memberFeatureLabels = {
  assistantSelection: "选择助手", conversation: "发起和继续对话", ownHistory: "本人历史", authorizedCitations: "查看引用",
} as const;
export type MemberDraft = { loginName: string; conversationFeatures: MemberConversationFeatures; assistantIds: string[] };
export function defaultMemberDraft(): MemberDraft {
  return { loginName: "", conversationFeatures: { assistantSelection: true, conversation: true, ownHistory: false, authorizedCitations: false }, assistantIds: [] };
}
const key = "foundation.member-create.draft";
const observed = new WeakSet<ControlPlaneClient>();

/** 非身份 CSRF 值在每次登录后轮换；摘要只隔离非秘密草稿，不能参与服务器授权。 */
export async function memberDraftOwner(client: ControlPlaneClient): Promise<string | undefined> {
  if (!observed.has(client)) {
    observed.add(client);
    client.subscribeSessionChange(active => { if (!active) clearMemberDraft(); });
  }
  const epoch = client.currentSessionRevision();
  try {
    const csrf = document.cookie.split(";").map(part => part.trim()).find(part => part.startsWith("__Host-foundation-csrf="))?.slice("__Host-foundation-csrf=".length);
    if (!csrf || !client.hasSession()) { clearMemberDraft(); return undefined; }
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(csrf));
    if (epoch !== client.currentSessionRevision() || !client.hasSession()) return undefined;
    return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, "0")).join("");
  } catch { clearMemberDraft(); return undefined; }
}

/** 显式白名单：请求、密码、确认密码及会话值都不能进入浏览器存储。 */
export function serializeMemberDraft(owner: string, draft: MemberDraft): string {
  return JSON.stringify({ owner, loginName: draft.loginName, assistantIds: [...draft.assistantIds],
    conversationFeatures: Object.fromEntries(Object.keys(memberFeatureLabels).map(code => [code, draft.conversationFeatures[code as keyof MemberConversationFeatures]])) });
}
export function restoreMemberDraft(owner: string, serialized: string | null): MemberDraft | undefined {
  try {
    const value = JSON.parse(serialized ?? "null");
    if (!owner || value?.owner !== owner || typeof value.loginName !== "string" || value.loginName.length > 64
        || !Array.isArray(value.assistantIds) || value.assistantIds.length > 200
        || value.assistantIds.some((id: unknown) => typeof id !== "string" || !/^[1-9][0-9]{0,18}$/.test(id))
        || Object.keys(memberFeatureLabels).some(code => typeof value.conversationFeatures?.[code] !== "boolean")) return undefined;
    return { loginName: value.loginName, assistantIds: [...new Set<string>(value.assistantIds)],
      conversationFeatures: Object.fromEntries(Object.keys(memberFeatureLabels).map(code => [code, value.conversationFeatures[code]])) as MemberConversationFeatures };
  } catch { return undefined; }
}
export function loadMemberDraft(owner: string): MemberDraft | undefined {
  try { const draft = restoreMemberDraft(owner, sessionStorage.getItem(key)); if (!draft) clearMemberDraft(); return draft; } catch { return undefined; }
}
export function saveMemberDraft(owner: string | undefined, draft: MemberDraft): void {
  if (owner) { try { sessionStorage.setItem(key, serializeMemberDraft(owner, draft)); } catch { /* 禁用存储时仍允许当页填写。 */ } }
}
export function clearMemberDraft(): void { try { sessionStorage.removeItem(key); } catch { /* 无可写存储时没有持久草稿。 */ } }
/** 密码使用非受控输入，步骤切换、取消、提交、失败和退出均清除 DOM 值。 */
export function clearMemberPasswords(form: HTMLFormElement | null): void {
  form?.querySelectorAll<HTMLInputElement>('input[type="password"]').forEach(input => { input.value = ""; });
}
