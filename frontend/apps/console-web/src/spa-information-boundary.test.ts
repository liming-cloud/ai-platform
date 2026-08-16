/// <reference types="node" />
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const roots = [
  new URL("./features", import.meta.url).pathname,
  new URL("../../../packages/query-workspace/src", import.meta.url).pathname,
];

function sourceFiles(directory: string): string[] {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    return statSync(path).isDirectory() ? sourceFiles(path) : path.endsWith(".tsx") ? [path] : [];
  });
}

describe("全 SPA 用户信息边界", () => {
  it("可编辑控件不得要求内部标识、修订、版本或结果编码", () => {
    const violations = roots.flatMap(sourceFiles).flatMap((path) => {
      const source = readFileSync(path, "utf8");
      const labels = source.match(/(?:<Field[^>]*label=|aria-label=)["'`][^"'`]+["'`]/g) ?? [];
      return labels.filter((label) => /(SubjectId|tenantId|membershipId|modelId|内部标识|数据集标识|版本标识|范围标识|选择修订|结果码)/i.test(label))
        .map((label) => `${path}: ${label}`);
    });
    expect(violations).toEqual([]);
  });

  it("渲染文案不得包含追踪标识或后端异常字段", () => {
    const violations = roots.flatMap(sourceFiles).flatMap((path) => {
      const source = readFileSync(path, "utf8");
      return ["追踪标识", "异常类型：", "SQL：", "结果码：", "厂商原始响应："]
        .filter((marker) => source.includes(marker))
        .map((marker) => `${path}: ${marker}`);
    });
    expect(violations).toEqual([]);
  });
});
