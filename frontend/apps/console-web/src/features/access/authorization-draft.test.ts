import { describe, expect, it } from "vitest";
import { restoreAuthorizationDraft, serializeAuthorizationDraft } from "./authorization-draft";

describe("助手与策略草稿隔离", () => {
  it("未保存的策略空选择仍可恢复，保存时再检查至少一名成员", () => {
    const input = { kind: "policy" as const, assistantId: "3", membershipIds: [], expectedVersion: 5, expectedAuthorizationRevision: 6, expectedTenantFeatureVersion: 7 };
    expect(restoreAuthorizationDraft("owner", "policy:2", serializeAuthorizationDraft("owner", "policy:2", input))).toEqual(input);
  });
  it("白名单不保留秘密、主体、角色或其他页配置，且保留编辑时的原始修订号", () => {
    const input = { kind: "assistants" as const, assistantIds: ["2", "1"], expectedAuthorizationRevision: 3, password: "不得保存", role: "admin" };
    const text = serializeAuthorizationDraft("owner", "member:1", input);
    expect(JSON.parse(text)).toEqual({ owner: "owner", target: "member:1", kind: "assistants", assistantIds: ["1", "2"], expectedAuthorizationRevision: 3 });
    expect(restoreAuthorizationDraft("owner", "member:1", text)).toEqual({ kind: "assistants", assistantIds: ["1", "2"], expectedAuthorizationRevision: 3 });
    expect(text).not.toContain("password"); expect(text).not.toContain("role");
  });
  it("换身份、登录世代、成员和策略版本均不能恢复旧草稿", () => {
    const text = serializeAuthorizationDraft("owner:1", "policy:2", { kind: "policy", assistantId: "3", membershipIds: ["4"], expectedVersion: 5, expectedAuthorizationRevision: 6, expectedTenantFeatureVersion: 7 });
    expect(restoreAuthorizationDraft("owner:2", "policy:2", text)).toBeUndefined();
    expect(restoreAuthorizationDraft("owner:1", "policy:3", text)).toBeUndefined();
    expect(restoreAuthorizationDraft("owner:1", "member:2", text)).toBeUndefined();
    expect(restoreAuthorizationDraft("owner:1", "policy:2", text)).toMatchObject({ expectedVersion: 5, expectedAuthorizationRevision: 6 });
  });
  it("拒绝重复、越界标识、未知种类与非法版本；空助手集合可以撤权", () => {
    const valid = { kind: "assistants", assistantIds: [], expectedAuthorizationRevision: 0 };
    const read = (input: object) => restoreAuthorizationDraft("owner", "member:1", JSON.stringify({ owner: "owner", target: "member:1", ...input }));
    expect(read(valid)).toEqual(valid);
    for (const invalid of [{ ...valid, assistantIds: ["1", "1"] }, { ...valid, assistantIds: ["9223372036854775808"] },
      { ...valid, assistantIds: ["0"] }, { ...valid, expectedAuthorizationRevision: -1 }, { ...valid, kind: "unknown" },
      { kind: "policy", assistantId: "1", membershipIds: [], expectedVersion: 0, expectedAuthorizationRevision: 0, expectedTenantFeatureVersion: 0 },
    ]) expect(read(invalid)).toBeUndefined();
  });
});
