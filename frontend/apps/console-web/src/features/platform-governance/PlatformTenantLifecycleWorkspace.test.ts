/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const workspacePath = new URL("./PlatformTenantLifecycleWorkspace.tsx", import.meta.url).pathname;
const stylesPath = new URL("../../styles.css", import.meta.url).pathname;

describe("租户建立流程", () => {
  it("在管理员选择步骤原地建立专用租户管理员账户", () => {
    const source = readFileSync(workspacePath, "utf8");

    expect(source).toContain("client.createPlatformAccount");
    expect(source).toContain("建立专用租户管理员账户");
    expect(source).not.toContain("请先在平台账户页面创建并启用账户");
  });

  it("平台租户目录提供管理员治理工作台且不展示内部标识", () => {
    const source = readFileSync(workspacePath, "utf8");
    const styles = readFileSync(stylesPath, "utf8");

    expect(source).toContain("管理租户管理员");
    expect(source).toContain("client.listTenantAdministrators");
    expect(source).toContain("client.listTenantAdministratorCandidates");
    expect(source).toContain("client.replaceTenantAdministrator");
    expect(source).toContain("administratorTrigger.current = event.currentTarget");
    expect(source).toContain("administratorTrigger.current?.focus()");
    expect(source).toContain('className="tenant-administrator-dialog"');
    expect(source).not.toContain("成员标识");
    expect(source).not.toContain("主体标识");
    expect(styles).toContain(".fui-DialogSurface.tenant-administrator-dialog");
    expect(styles).toContain("max-width: none;");
    expect(styles).toContain("grid-auto-rows: max-content;");
    expect(styles).toMatch(
      /\.tenant-administrator-dialog__content\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\);/s,
    );
    expect(styles).toContain("@media (prefers-reduced-motion: reduce)");
  });
});
