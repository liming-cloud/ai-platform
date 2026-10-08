import { describe, expect, it } from "vitest";
import { restoreMemberFeatureDraft, serializeMemberFeatureDraft } from "./member-feature-draft";

const facts = { conversationFeatures: { assistantSelection: false, conversation: true, ownHistory: true, authorizedCitations: false }, expectedFeatureVersion: 2, expectedTenantFeatureVersion: 3 };

describe("独立成员功能草稿", () => {
  it("只保存当前成员四项功能及原始版本，刷新不把旧草稿伪装成新版本", () => {
    const text = serializeMemberFeatureDraft("owner-one", "123", { ...facts, assistantIds: ["456"], password: "应被丢弃" } as typeof facts);
    expect(JSON.parse(text)).toEqual({ owner: "owner-one", membershipId: "123", ...facts });
    expect(restoreMemberFeatureDraft("owner-one", "123", text)).toEqual(facts);
    expect(text).not.toContain("password");
    expect(text).not.toContain("assistantIds");
  });
  it("不恢复其他登录世代或其他成员的草稿", () => {
    const text = serializeMemberFeatureDraft("owner-one", "123", facts);
    expect(restoreMemberFeatureDraft("owner-two", "123", text)).toBeUndefined();
    expect(restoreMemberFeatureDraft("owner-one", "456", text)).toBeUndefined();
  });
  it("损坏、非布尔、缺失功能和无效版本均不恢复", () => {
    for (const draft of [
      { ...facts, expectedFeatureVersion: 1.5 }, { ...facts, expectedTenantFeatureVersion: -1 },
      { ...facts, conversationFeatures: { conversation: true } },
      { ...facts, conversationFeatures: { ...facts.conversationFeatures, ownHistory: "true" } },
    ]) expect(restoreMemberFeatureDraft("owner-one", "123", JSON.stringify({ owner: "owner-one", membershipId: "123", ...draft }))).toBeUndefined();
    expect(restoreMemberFeatureDraft("owner-one", "123", "{")).toBeUndefined();
  });
});
