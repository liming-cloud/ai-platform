/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const workspacePath = new URL("./PlatformModelDeploymentWorkspace.tsx", import.meta.url).pathname;

describe("平台模型候选确认", () => {
  it("待确认候选只能由管理员选择类型后进入部署", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain("确认模型类型");
    expect(source).toContain("confirmPlatformProviderModelCandidate");
    expect(source).toContain('value="CHAT"');
    expect(source).toContain('value="EMBEDDING"');
    expect(source).toContain("确认不会替代后续真实能力探测");
  });
});
