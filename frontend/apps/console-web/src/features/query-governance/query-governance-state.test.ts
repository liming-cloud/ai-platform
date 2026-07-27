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
      ["PLATFORM_SECURITY.AUTHENTICATION.0001", "PLATFORM_SECURITY.AUTHENTICATION.0001"],
    )).toBe("unauthenticated");
    expect(resolveOperationsViewState(
      false,
      false,
      ["EVALUATION_OPERATIONS.AUTHORIZATION.0001", "EVALUATION_OPERATIONS.AUTHORIZATION.0001"],
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
