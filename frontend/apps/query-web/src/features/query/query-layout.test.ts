import { describe, expect, it } from "vitest";
import { queryMainLayoutClassName } from "./query-workflow";

describe("可信问答任务布局", () => {
  it("只在查询结果区域出现后启用桌面双栏", () => {
    expect(queryMainLayoutClassName("ready")).toBe("query-main-layout");
    expect(queryMainLayoutClassName("querying")).toBe(
      "query-main-layout has-result",
    );
    expect(queryMainLayoutClassName("answered")).toBe(
      "query-main-layout has-result",
    );
    expect(queryMainLayoutClassName("refused")).toBe(
      "query-main-layout has-result",
    );
    expect(queryMainLayoutClassName("failed")).toBe(
      "query-main-layout has-result",
    );
  });
});
