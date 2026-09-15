/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("开户原型承接边界", () => {
  it("只使用共享颜色，不重复维护局部品牌主题", () => {
    const styles = readFileSync(new URL("./tenant-onboarding.css", import.meta.url), "utf8");
    expect(styles).not.toMatch(/--color(?:Brand|Neutral)|#[0-9a-f]{3,8}\b/i);
    expect(styles).toContain("var(--foundation-accent)");
    expect(styles).not.toContain(".product-canvas:has(.tenant-onboarding)");
  });

  it("提交按钮为正常与加载文案共同预留内容宽度，不使用任意固定宽度", () => {
    const source = readFileSync(new URL("./TenantProvisioningFlow.tsx", import.meta.url), "utf8");
    const styles = readFileSync(new URL("./tenant-onboarding.css", import.meta.url), "utf8");
    expect(source).toContain('className="tenant-onboarding__submit-copy"');
    expect(source).toContain('aria-hidden={!busy}');
    expect(styles).toMatch(/\.tenant-onboarding__submit-copy > span\s*\{\s*grid-area:\s*1\s*\/\s*1;/);
    expect(styles).toContain("visibility: hidden");
    expect(styles).not.toMatch(/button\[type="submit"\][^}]*min-width:\s*112px/);
  });

  it("概览承接原型安全保障，但不复制演示驻留区域或未交付设置操作", () => {
    const source = readFileSync(new URL("./TenantOverview.tsx", import.meta.url), "utf8");
    expect(source).toContain('aria-labelledby="tenant-safety-title"');
    for (const label of ["基础保护已开启", "租户数据隔离", "服务端权限校验", "密码与密钥不回显"]) expect(source).toContain(label);
    expect(source).not.toMatch(/华东受控区|编辑配额|保存功能配置/);
  });
});
