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
    expect(styles).toMatch(/\.product-shell\s*\{[^}]*grid-template-columns:\s*14\.5rem\s+minmax\(0,\s*1fr\);/s);
    expect(styles).toMatch(/\.product-topbar\s*\{[^}]*min-height:\s*3\.5rem;/s);
    expect(styles).toMatch(/@media \(max-width:\s*1040px\)[\s\S]*?\.product-shell\s*\{[^}]*grid-template-columns:\s*5rem\s+minmax\(0,\s*1fr\);/s);
  });
});
