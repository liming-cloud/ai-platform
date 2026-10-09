import { describe, expect, it } from "vitest";
import { policyReadFailure, policyStatusLabel } from "./access-policy-state";
import { ApiRequestError } from "../../api/control-plane-client";

describe("策略版本的原型状态语义", () => {
  it("保留不可变旧版本，不把它表示成另一个已发布版本", () => {
    expect(policyStatusLabel("SUPERSEDED")).toBe("已保留");
    expect(policyStatusLabel("PUBLISHED")).toBe("已发布");
    expect(policyStatusLabel("DRAFT")).toBe("草稿");
  });
  it("真实 403 承接受限状态和已授权入口，不能伪装成重试任务", () => {
    expect(policyReadFailure(new ApiRequestError(403, "DENIED", "synthetic-sensitive-message", "synthetic-trace"))).toEqual({
      kind: "denied", summary: "访问受限", title: "当前会话不能访问此任务", description: "页面不会确认目标资源是否存在，也不会加载正文、引用或历史消息。", action: "返回已授权入口",
    });
  });
  it("依赖失败及未知异常只给安全重试文案，不回显响应或异常内容", () => {
    for (const error of [new ApiRequestError(503, "UNAVAILABLE", "synthetic-sensitive-message", "synthetic-trace"), new Error("synthetic-sensitive-message")]) {
      const state = policyReadFailure(error);
      expect(state.kind).toBe("error"); expect(state.action).toBe("重新加载");
      expect(JSON.stringify(state)).not.toContain("synthetic-sensitive-message");
      expect(JSON.stringify(state)).not.toContain("synthetic-trace");
    }
  });
});
