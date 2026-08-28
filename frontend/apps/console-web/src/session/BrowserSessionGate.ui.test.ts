/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const gatePath = new URL("./BrowserSessionGate.tsx", import.meta.url).pathname;
const stylesPath = new URL("../styles.css", import.meta.url).pathname;

describe("登录入口视觉结构", () => {
  it("恢复会话和匿名登录复用同一页面框架", () => {
    const source = readFileSync(gatePath, "utf8");

    expect(source).toContain("function LoginFrame");
    expect(source.match(/<LoginFrame/g)?.length).toBeGreaterThanOrEqual(2);
    expect(source).toMatch(
      /state === "restoring"[\s\S]*?<LoginFrame[\s\S]*?<StatePanel[\s\S]*?<\/LoginFrame>/,
    );
  });

  it("桌面提供产品上下文并在移动端收敛为单列任务", () => {
    const source = readFileSync(gatePath, "utf8");
    const styles = readFileSync(stylesPath, "utf8");

    expect(source).toContain('className="governance-login__brief"');
    expect(source).toContain('className="governance-login__principles"');
    expect(styles).toMatch(
      /\.governance-login__layout\s*\{[^}]*grid-template-columns:\s*minmax\([^;]+\)\s+minmax\([^;]+\);/s,
    );
    expect(styles).toMatch(
      /@media \(max-width:\s*52rem\)[\s\S]*?\.governance-login__layout\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\);/s,
    );
    expect(styles).toMatch(
      /@media \(max-width:\s*44rem\)[\s\S]*?\.governance-login__principles\s*\{[^}]*display:\s*none;/s,
    );
  });

  it("登录失败提示出现后输入框仍可在卡片内收缩", () => {
    const source = readFileSync(gatePath, "utf8");
    const styles = readFileSync(stylesPath, "utf8");

    expect(source.match(/className="governance-login__input"/g)?.length)
      .toBeGreaterThanOrEqual(2);
    expect(styles).toMatch(
      /\.governance-login__card\s*>\s*\*\s*\{[^}]*min-width:\s*0;[^}]*max-width:\s*100%;/s,
    );
    expect(styles).toMatch(
      /\.governance-login__input\s*\{[^}]*width:\s*100%;[^}]*min-width:\s*0;[^}]*max-width:\s*100%;/s,
    );
  });

  it("登录输入拥有明确无障碍名称且平台示例不引用旧账号", () => {
    const source = readFileSync(gatePath, "utf8");

    expect(source).toContain('aria-label="租户编码"');
    expect(source).toContain('aria-label="登录名"');
    expect(source).toContain('aria-label="密码"');
    expect(source).toContain('workspace === "platform" ? "请输入平台登录名" : "请输入登录名"');
    expect(source).not.toContain("platform.root");
  });
});
