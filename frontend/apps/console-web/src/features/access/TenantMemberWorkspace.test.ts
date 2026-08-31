/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const workspacePath = new URL("./TenantMemberWorkspace.tsx", import.meta.url).pathname;

describe("租户普通成员治理边界", () => {
  it("把租户管理员标记为平台治理目标且不提供租户内变更操作", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain('member.builtInRoles.includes("TENANT_ADMINISTRATOR")');
    expect(source).toContain("由平台治理");
    expect(source).not.toContain('{ code: "TENANT_ADMINISTRATOR", label: "租户管理员" }');
  });
});
