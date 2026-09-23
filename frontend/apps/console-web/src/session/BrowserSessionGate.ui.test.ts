/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const gatePath = new URL("./BrowserSessionGate.tsx", import.meta.url).pathname;
const stylesPath = new URL("../styles.css", import.meta.url).pathname;

describe("登录入口视觉结构", () => {
  it("独立入口使用可发现链接，不继续在同页切换身份标签", () => {
    const source = readFileSync(gatePath, "utf8");
    expect(source).not.toContain("<TabList");
    expect(source).toContain('href="/login"');
    expect(source).toContain('href="/tenant/login"');
    expect(source).toContain("当前登录身份与入口不匹配");
    expect(source).toContain("canEnterLoginWorkspace");
  });
  it("恢复会话和匿名登录复用同一页面框架", () => {
    const source = readFileSync(gatePath, "utf8");

    expect(source).toContain("function LoginFrame");
    expect(source.match(/<LoginFrame/g)?.length).toBeGreaterThanOrEqual(2);
    expect(source).toMatch(
      /state === "restoring"[\s\S]*?<LoginFrame[\s\S]*?<StatePanel[\s\S]*?<\/LoginFrame>/,
    );
  });

  it("登录布局按已验收原型保留桌面说明、平板标识和移动端任务", () => {
    const source = readFileSync(gatePath, "utf8");
    const styles = readFileSync(stylesPath, "utf8");

    expect(source).toContain('className="governance-login__brief"');
    expect(source).toContain('className="governance-login__introduction"');
    expect(styles).toMatch(
      /\.governance-login__layout\s*\{[^}]*grid-template-columns:\s*minmax\([^;]+\)\s+minmax\([^;]+\);/s,
    );
    expect(styles).toMatch(
      /\.governance-login__introduction\s*\{[^}]*align-self:\s*end;/s,
    );
    expect(styles).toMatch(
      /@media \(max-width:\s*65rem\) and \(min-width:\s*42\.5625rem\)[\s\S]*?\.governance-login__brand > span:last-child\s*\{[^}]*display:\s*none;/s,
    );
    expect(styles).toMatch(
      /@media \(max-width:\s*42\.5rem\)[\s\S]*?\.governance-login__layout\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\);/s,
    );
    expect(styles).toMatch(
      /@media \(max-width:\s*42\.5rem\)[\s\S]*?\.governance-login__brand\s*\{[^}]*display:\s*none;/s,
    );
    expect(styles).toMatch(
      /@media \(max-width:\s*42\.5rem\)[\s\S]*?\.governance-login__introduction\s*\{[^}]*align-self:\s*start;/s,
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
