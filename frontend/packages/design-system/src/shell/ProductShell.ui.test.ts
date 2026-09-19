/// <reference types="node" />
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const shellPath = new URL("./ProductShell.tsx", import.meta.url).pathname;
const shellStylesPath = new URL("../styles/shell.css", import.meta.url).pathname;
const tokensPath = new URL("../styles/tokens.css", import.meta.url).pathname;

describe("共享产品壳视觉契约", () => {
  it("使用稳定的浅色语义令牌并移除应用根渐变", () => {
    const tokens = readFileSync(tokensPath, "utf8");

    [
      "--foundation-canvas",
      "--foundation-sidebar",
      "--foundation-surface",
      "--foundation-subtle-surface",
      "--foundation-border",
      "--foundation-text",
      "--foundation-muted-text",
      "--foundation-accent",
      "--foundation-control-radius",
      "--foundation-panel-radius",
      "--foundation-elevation",
    ].forEach((token) => expect(tokens).toContain(token));
    expect(tokens).not.toContain("linear-gradient(");
  });

  it("桌面、折叠侧栏与顶栏遵循已接受的壳层尺寸", () => {
    const source = readFileSync(shellPath, "utf8");
    const styles = readFileSync(shellStylesPath, "utf8");

    expect(source).toContain("Tooltip");
    expect(source).toContain('className="product-topbar"');
    expect(source).not.toContain("Enterprise");
    expect(styles).toMatch(/\.product-shell\s*\{[^}]*grid-template-columns:\s*15rem\s+minmax\(0,\s*1fr\);/s);
    expect(styles).toMatch(/\.product-topbar\s*\{[^}]*min-height:\s*3\.5rem;/s);
    expect(styles).toMatch(/@media \(max-width:\s*1040px\)[\s\S]*?\.product-shell\s*\{[^}]*grid-template-columns:\s*5rem\s+minmax\(0,\s*1fr\);/s);
  });

  it("折叠侧栏隐藏可见文字后仍保留导航按钮名称", () => {
    const source = readFileSync(shellPath, "utf8");

    expect(source).toMatch(
      /function NavigationButton[\s\S]*?<Button[\s\S]*?aria-label=\{item\.label\}/,
    );
  });

  it("颜色、字体与已验收原型固定基线一致，而非只验证令牌存在", () => {
    // 来源为原型提交 `21b45c347` 的 `tokens.css`，不能从生产代码反推期望值。
    const tokens = readFileSync(tokensPath, "utf8");
    const expected = {
      "foundation-canvas": "#f5f4ed",
      "foundation-surface": "#faf9f5",
      "foundation-border": "#e8e6dc",
      "foundation-text": "#141413",
      "foundation-muted-text": "#5e5d59",
      "foundation-accent-reference": "#c96442",
    };
    Object.entries(expected).forEach(([name, value]) => {
      expect(tokens).toContain(`--${name}: ${value};`);
    });
    expect(tokens).toContain('--foundation-font-display: Georgia, "Songti SC", "Noto Serif CJK SC", SimSun, serif;');
    const provider = readFileSync(new URL("../providers/FoundationProvider.tsx", import.meta.url), "utf8");
    expect(provider).toContain('colorBrandBackground: "var(--foundation-accent)"');
    expect(provider).toContain('fontFamilyBase: "var(--foundation-font-body)"');
  });

  it("页头使用真实一级标题，缩放时共享样式仍命中", () => {
    const source = readFileSync(shellPath, "utf8");
    const styles = readFileSync(shellStylesPath, "utf8");
    expect(readFileSync(new URL("./PageHeading.tsx", import.meta.url), "utf8")).toContain('<h1 id="page-title">{title}</h1>');
    expect(source).toContain('<span aria-hidden="true">R</span>');
    expect(styles).toMatch(/\.page-heading h1\s*\{[^}]*font-family:\s*var\(--foundation-font-display\);[^}]*line-height:\s*1\.35;/s);
  });

});
