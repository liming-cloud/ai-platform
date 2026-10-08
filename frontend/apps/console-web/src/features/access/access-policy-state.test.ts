import { describe, expect, it } from "vitest";
import { policyStatusLabel } from "./access-policy-state";

describe("策略版本的原型状态语义", () => {
  it("保留不可变旧版本，不把它表示成另一个已发布版本", () => {
    expect(policyStatusLabel("SUPERSEDED")).toBe("已保留");
    expect(policyStatusLabel("PUBLISHED")).toBe("已发布");
    expect(policyStatusLabel("DRAFT")).toBe("草稿");
  });
});
