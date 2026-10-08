import { describe, expect, it } from "vitest";
import { memberIsReadOnly } from "./TenantMemberWorkspace";

describe("租户普通成员治理边界", () => {
  it("资格或绑定角色任一为管理员时均只读，不能把不一致的管理员降为普通操作目标", () => {
    expect(memberIsReadOnly({ membershipRole: "TENANT_ADMINISTRATOR", builtInRoles: [] })).toBe(true);
    expect(memberIsReadOnly({ membershipRole: "TENANT_USER", builtInRoles: ["TENANT_ADMINISTRATOR"] })).toBe(true);
    expect(memberIsReadOnly({ membershipRole: "TENANT_USER", builtInRoles: ["TENANT_USER"] })).toBe(false);
  });
});
