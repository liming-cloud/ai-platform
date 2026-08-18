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
});
