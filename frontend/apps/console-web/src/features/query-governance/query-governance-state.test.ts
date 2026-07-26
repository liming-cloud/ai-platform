import { describe, expect, it } from "vitest";
import {
  isOperationsStale,
  resolveOperationsViewState,
} from "./query-governance-state";

describe("查询治理页面状态", () => {
  it("区分正常、部分、未登录、无权限和错误状态", () => {
    expect(resolveOperationsViewState(true, true, [])).toBe("ready");
    expect(resolveOperationsViewState(true, false, ["UPSTREAM_FAILURE"]))
      .toBe("partial");
    expect(resolveOperationsViewState(
      false,
      false,
      ["AUTHENTICATION_REQUIRED", "AUTHENTICATION_REQUIRED"],
    )).toBe("unauthenticated");
    expect(resolveOperationsViewState(
      false,
      false,
      ["ACCESS_DENIED", "ACCESS_DENIED"],
    )).toBe("denied");
    expect(resolveOperationsViewState(
      false,
      false,
      ["NETWORK_FAILURE"],
    )).toBe("error");
  });

  it("只把生成超过五分钟的数据标为陈旧", () => {
    const generatedAt = "2026-07-27T00:00:00Z";
    const generatedMillis = Date.parse(generatedAt);

    expect(isOperationsStale(generatedAt, generatedMillis + 299_999))
      .toBe(false);
    expect(isOperationsStale(generatedAt, generatedMillis + 300_001))
      .toBe(true);
  });
});
