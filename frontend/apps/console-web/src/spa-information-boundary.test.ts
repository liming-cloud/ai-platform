/// <reference types="node" />
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const roots = [
  new URL("./features", import.meta.url).pathname,
  new URL("../../../packages/query-workspace/src", import.meta.url).pathname,
];
const entryFiles = [
  new URL("./ConsoleApp.tsx", import.meta.url).pathname,
  new URL("./session/BrowserSessionGate.tsx", import.meta.url).pathname,
];

function sourceFiles(directory: string): string[] {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    return statSync(path).isDirectory() ? sourceFiles(path) : path.endsWith(".tsx") ? [path] : [];
  });
}

function spaSourceFiles(): string[] {
  return [...roots.flatMap(sourceFiles), ...entryFiles];
}

describe("全 SPA 用户信息边界", () => {
  it("可编辑控件不得要求内部标识、修订、版本或结果编码", () => {
    const violations = spaSourceFiles().flatMap((path) => {
      const source = readFileSync(path, "utf8");
      const labels = source.match(/(?:<Field[^>]*label=|aria-label=)["'`][^"'`]+["'`]/g) ?? [];
      return labels.filter((label) => /(SubjectId|tenantId|membershipId|modelId|内部标识|数据集标识|版本标识|范围标识|选择修订|结果码)/i.test(label))
        .map((label) => `${path}: ${label}`);
    });
    expect(violations).toEqual([]);
  });

  it("渲染文案不得包含追踪标识或后端异常字段", () => {
    const violations = spaSourceFiles().flatMap((path) => {
      const source = readFileSync(path, "utf8");
      return ["追踪标识", "异常类型：", "SQL：", "结果码：", "厂商原始响应："]
        .filter((marker) => source.includes(marker))
        .map((marker) => `${path}: ${marker}`);
    });
    expect(violations).toEqual([]);
  });

  it("可见文案不得要求用户理解内部实现概念", () => {
    const forbiddenMarkers = [
      "内部标识",
      "内部引用",
      "选择修订",
      "连接修订",
      "当前修订",
      "发布修订",
      "最新修订",
      "乐观版本",
      "版本事实",
      "厂商原始响应",
      "服务端分页",
      "后台正在",
    ];
    const violations = spaSourceFiles().flatMap((path) => {
      const visibleLines = readFileSync(path, "utf8")
        .split("\n")
        .filter((line) => {
          const trimmed = line.trim();
          return !trimmed.startsWith("//")
            && !trimmed.startsWith("/*")
            && !trimmed.startsWith("*")
            && !trimmed.startsWith("throw new Error");
        });
      return visibleLines.flatMap((line, index) => forbiddenMarkers
        .filter((marker) => line.includes(marker))
        .map((marker) => `${path}:${index + 1}: ${marker}`));
    });
    expect(violations).toEqual([]);
  });

  it("可信问答不得向用户展示助手内部版本号", () => {
    const workspacePath = new URL("../../../packages/query-workspace/src/QueryWorkspace.tsx", import.meta.url).pathname;
    const source = readFileSync(workspacePath, "utf8");
    expect(source).not.toMatch(/(?:assistant|selectedAssistant)\.versionNumber/);
  });

  it("M6 只声明浅色主题并完整尊重减少动态效果", () => {
    const tokensPath = new URL("../../../packages/design-system/src/styles/tokens.css", import.meta.url).pathname;
    const tokens = readFileSync(tokensPath, "utf8");
    expect(tokens).toMatch(/color-scheme:\s*light;/);
    expect(tokens).toContain("@media (prefers-reduced-motion: reduce)");
    expect(tokens).toMatch(/animation-duration:\s*0\.01ms\s*!important;/);
    expect(tokens).toMatch(/animation-iteration-count:\s*1\s*!important;/);
    expect(tokens).toMatch(/transition-duration:\s*0\.01ms\s*!important;/);
  });
});
