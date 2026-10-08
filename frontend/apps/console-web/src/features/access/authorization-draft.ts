import type { AccessPolicyInput, ControlPlaneClient, MemberAssistantSettingsInput } from "../../api/control-plane-client";

export type AuthorizationDraft = ({ kind: "assistants" } & MemberAssistantSettingsInput) | ({ kind: "policy" } & AccessPolicyInput);
const prefix = "foundation.authorization.draft.";
const observed = new WeakSet<ControlPlaneClient>();
const id = (value: unknown): value is string => typeof value === "string" && /^[1-9][0-9]{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;
const version = (value: unknown) => Number.isSafeInteger(value) && Number(value) >= 0;
const ids = (value: unknown): value is string[] => Array.isArray(value) && value.every(id) && new Set(value).size === value.length;

/** 只持久化授权选择与编辑时的版本，不能把整个请求、身份或秘密写进浏览器草稿。 */
export function serializeAuthorizationDraft(owner: string, target: string, input: AuthorizationDraft): string {
  return JSON.stringify({ owner, target, kind: input.kind, expectedAuthorizationRevision: input.expectedAuthorizationRevision,
    ...(input.kind === "assistants" ? { assistantIds: [...input.assistantIds].sort() }
      : { assistantId: input.assistantId, membershipIds: [...input.membershipIds].sort(), expectedVersion: input.expectedVersion, expectedTenantFeatureVersion: input.expectedTenantFeatureVersion }) });
}
export function restoreAuthorizationDraft(owner: string, target: string, text: string | null): AuthorizationDraft | undefined {
  try {
    const value = JSON.parse(text ?? "null");
    if (!owner || value?.owner !== owner || value.target !== target || !version(value.expectedAuthorizationRevision)) return undefined;
    if (value.kind === "assistants" && target.startsWith("member:") && ids(value.assistantIds)) {
      return { kind: "assistants", assistantIds: value.assistantIds, expectedAuthorizationRevision: value.expectedAuthorizationRevision };
    }
    if (value.kind === "policy" && target.startsWith("policy:") && id(value.assistantId) && ids(value.membershipIds)
        && version(value.expectedVersion) && version(value.expectedTenantFeatureVersion)) {
      return { kind: "policy", assistantId: value.assistantId, membershipIds: value.membershipIds,
        expectedVersion: value.expectedVersion, expectedAuthorizationRevision: value.expectedAuthorizationRevision, expectedTenantFeatureVersion: value.expectedTenantFeatureVersion };
    }
  } catch { /* 损坏草稿不能成为页面授权输入。 */ }
  return undefined;
}
export function loadAuthorizationDraft(owner: string, target: string): AuthorizationDraft | undefined {
  const value = restoreAuthorizationDraft(owner, target, sessionStorage.getItem(prefix + target));
  if (!value) clearAuthorizationDraft(target);
  return value;
}
export function saveAuthorizationDraft(owner: string, target: string, input: AuthorizationDraft): void {
  sessionStorage.setItem(prefix + target, serializeAuthorizationDraft(owner, target, input));
}
export function clearAuthorizationDraft(target: string): void { sessionStorage.removeItem(prefix + target); }
/** 页面卸载后订阅仍负责退出清理；存储不可用不能阻止服务器注销。 */
export function observeAuthorizationDrafts(client: ControlPlaneClient): void {
  if (observed.has(client)) return;
  observed.add(client);
  client.subscribeSessionChange(active => {
    if (active) return;
    try {
      for (let index = sessionStorage.length - 1; index >= 0; index--) {
        const key = sessionStorage.key(index);
        if (key?.startsWith(prefix)) sessionStorage.removeItem(key);
      }
    } catch { /* 下一身份仍由 owner 和登录世代拒绝残留草稿。 */ }
  });
}
