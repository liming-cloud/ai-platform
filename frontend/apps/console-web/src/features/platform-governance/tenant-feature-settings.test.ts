import { describe, expect, it } from "vitest";
import { TENANT_FEATURES } from "./tenant-feature-settings";

describe("租户功能目录", () => {
  it("与已验收原型保持八项代码、顺序及帮助文案一致", () => {
    expect(TENANT_FEATURES.map(item => [item.code, item.label])).toEqual([
      ["knowledgeManagement", "知识管理"],
      ["modelGovernance", "模型治理"],
      ["assistantDelivery", "助手交付"],
      ["evaluationGovernance", "评测治理"],
      ["assistantSelection", "选择已授权助手"],
      ["conversation", "发起与继续对话"],
      ["ownHistory", "查看本人历史"],
      ["authorizedCitations", "查看有权引用"],
    ]);
    expect(TENANT_FEATURES[1]?.help).toBe("模型配置、部署与路由归入治理。");
    expect(TENANT_FEATURES.filter(item => item.code !== "modelGovernance")
      .every(item => item.help === "关闭后租户内对应入口和直接访问同时受限。")).toBe(true);
  });
});
