import type { ControlPlaneClient, MemberFeatureSettingsInput } from "../../api/control-plane-client";
import { memberFeatureLabels } from "./member-account-draft";

const prefix = "foundation.member-features.draft.";
const observed = new WeakSet<ControlPlaneClient>();

/** 退出及失效清理所有成员页草稿；页面卸载后仍保留清理订阅。 */
export function observeMemberFeatureDrafts(client: ControlPlaneClient): void {
  if (observed.has(client)) return;
  observed.add(client);
  client.subscribeSessionChange(active => { if (!active) clearMemberFeatureDrafts(); });
}

/** 显式白名单只含非秘密功能草稿，原始版本在刷新后保留以便服务器拒绝旧编辑。 */
export function serializeMemberFeatureDraft(owner: string, membershipId: string, input: MemberFeatureSettingsInput): string {
  return JSON.stringify({ owner, membershipId,
    conversationFeatures: Object.fromEntries(Object.keys(memberFeatureLabels).map(code => [code, input.conversationFeatures[code as keyof typeof memberFeatureLabels]])),
    expectedFeatureVersion: input.expectedFeatureVersion, expectedTenantFeatureVersion: input.expectedTenantFeatureVersion });
}

export function restoreMemberFeatureDraft(owner: string, membershipId: string, text: string | null): MemberFeatureSettingsInput | undefined {
  try {
    const value = JSON.parse(text ?? "null");
    if (!owner || value?.owner !== owner || value.membershipId !== membershipId
        || !Number.isSafeInteger(value.expectedFeatureVersion) || value.expectedFeatureVersion < 0
        || !Number.isSafeInteger(value.expectedTenantFeatureVersion) || value.expectedTenantFeatureVersion < 0
        || Object.keys(value.conversationFeatures ?? {}).length !== 4
        || Object.keys(memberFeatureLabels).some(code => typeof value.conversationFeatures?.[code] !== "boolean")) return undefined;
    return { conversationFeatures: value.conversationFeatures, expectedFeatureVersion: value.expectedFeatureVersion,
      expectedTenantFeatureVersion: value.expectedTenantFeatureVersion };
  } catch { return undefined; }
}

export function loadMemberFeatureDraft(owner: string, membershipId: string): MemberFeatureSettingsInput | undefined {
  const draft = restoreMemberFeatureDraft(owner, membershipId, sessionStorage.getItem(prefix + membershipId));
  if (!draft) clearMemberFeatureDraft(membershipId);
  return draft;
}
export function saveMemberFeatureDraft(owner: string | undefined, membershipId: string, input: MemberFeatureSettingsInput): void {
  if (owner) sessionStorage.setItem(prefix + membershipId, serializeMemberFeatureDraft(owner, membershipId, input));
}
export function clearMemberFeatureDraft(membershipId: string): void { sessionStorage.removeItem(prefix + membershipId); }
function clearMemberFeatureDrafts(): void {
  try {
    for (let index = sessionStorage.length - 1; index >= 0; index--) {
      const key = sessionStorage.key(index);
      if (key?.startsWith(prefix)) sessionStorage.removeItem(key);
    }
  } catch { /* 禁用存储时不能阻止注销；下一登录仍由 owner 拒绝旧草稿。 */ }
}
