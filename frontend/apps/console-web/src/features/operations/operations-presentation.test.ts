import { describe, expect, it } from "vitest";
import {
  isOverviewStale,
  percentLabel,
  statusLabel,
} from "./operations-presentation";

describe("运营总览呈现", () => {
  it("使用后端给出的五分钟失效边界", () => {
    expect(isOverviewStale("2026-08-16T08:05:00Z", Date.parse("2026-08-16T08:04:59Z")))
      .toBe(false);
    expect(isOverviewStale("2026-08-16T08:05:00Z", Date.parse("2026-08-16T08:05:00Z")))
      .toBe(true);
  });

  it("统计比率和业务状态使用可理解中文", () => {
    expect(percentLabel(0.9376)).toBe("93.8%");
    expect(statusLabel("PENDING_ACTIVATION")).toBe("准备中");
    expect(statusLabel("PUBLISHED")).toBe("已发布");
    expect(statusLabel("UNKNOWN_BACKEND_STATUS")).toBe("其他");
  });
});
