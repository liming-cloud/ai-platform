/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const workspacePath = new URL("./PlatformTenantLifecycleWorkspace.tsx", import.meta.url).pathname;

describe("租户建立流程", () => {
  it("在管理员选择步骤原地建立专用租户管理员账户", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain("client.createPlatformAccount");
    expect(source).toContain("建立专用租户管理员账户");
    expect(source).not.toContain("请先在平台账户页面创建并启用账户");
  });
});
