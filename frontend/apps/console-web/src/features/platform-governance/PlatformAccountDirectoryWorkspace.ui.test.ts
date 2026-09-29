/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const workspacePath = new URL("./PlatformAccountDirectoryWorkspace.tsx", import.meta.url).pathname;

describe("平台管理员账户页安全边界", () => {
  it("账户页只提供平台管理员操作且不渲染内部标识", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain("创建平台账户");
    expect(source).toContain("重置密码");
    expect(source).toContain("停用账户");
    expect(source).toContain("恢复账户");
    expect(source).toContain("item.current");
    expect(source).not.toContain("bindPlatformRole");
    expect(source).not.toContain("revokePlatformRole");
    expect(source).not.toContain("<td>{item.subjectId}</td>");
    expect(source).not.toContain("<td>{item.version}</td>");
  });
});
