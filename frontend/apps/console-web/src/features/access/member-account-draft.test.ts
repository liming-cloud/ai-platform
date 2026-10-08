import { describe, expect, it } from "vitest";
import { defaultMemberDraft, serializeMemberDraft, restoreMemberDraft } from "./member-account-draft";

describe("成员开户非秘密草稿", () => {
  it("保存白名单并在登录所有者变化时拒绝恢复", () => {
    const draft = { ...defaultMemberDraft(), loginName: "member.test", assistantIds: ["410000000000000001"], password: "synthetic-only", passwordConfirmation: "synthetic-only", subjectId: "forbidden" };
    const saved = serializeMemberDraft("owner-a", draft);
    expect(saved).not.toMatch(/password|synthetic-only|subjectId/);
    expect(restoreMemberDraft("owner-a", saved)).toEqual({ loginName: "member.test", assistantIds: draft.assistantIds, conversationFeatures: draft.conversationFeatures });
    expect(restoreMemberDraft("owner-b", saved)).toBeUndefined();
    expect(restoreMemberDraft("", saved)).toBeUndefined();
  });
  it("损坏功能、非法助手及超长输入不获得隐式默认授权", () => {
    for (const altered of [{ conversationFeatures: {} }, { assistantIds: ["-1"] }, { loginName: "x".repeat(65) }]) {
      expect(restoreMemberDraft("owner", JSON.stringify({ owner: "owner", ...defaultMemberDraft(), ...altered }))).toBeUndefined();
    }
  });
});
