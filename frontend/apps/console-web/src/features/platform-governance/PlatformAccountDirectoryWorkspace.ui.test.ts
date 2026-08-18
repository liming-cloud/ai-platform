/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const workspacePath = new URL("./PlatformAccountDirectoryWorkspace.tsx", import.meta.url).pathname;

describe("平台账户详情", () => {
  it("列表提供安全详情抽屉且不渲染内部标识", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain("查看详情");
    expect(source).toContain("<Drawer");
    expect(source).toContain("账户详情");
    expect(source).not.toContain("{detailAccount.subjectId}");
    expect(source).not.toContain("{detailAccount.version}");
  });

  it("把全局人员账户与平台管理员授权明确分开", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain("人员账户目录");
    expect(source).toContain("尚未授予平台治理权限");
    expect(source).toContain("授予平台治理权限");
    expect(source).not.toContain("尚未绑定平台角色");
    expect(source).not.toContain("绑定平台管理员");
  });
});
