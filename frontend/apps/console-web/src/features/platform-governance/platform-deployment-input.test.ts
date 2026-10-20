import { describe, expect, it } from "vitest";
import type { PlatformDeployableModel } from "../../api/control-plane-client";
import { platformDeploymentInput } from "./platform-deployment-input";

const model: PlatformDeployableModel = { connectionId: "11", connectionRevision: 2, connectionDisplayName: "合成连接", residency: "合成区域",
  declaredCapability: "RERANK", modelCode: "synthetic-resource-id", displayName: "合成重排模型", modelType: "RERANK", capabilities: ["RERANK"], classification: "CONFIRMED_BY_PROVIDER" };
function values() { const v = new FormData(); for (const [key, value] of Object.entries({ name: "合成部署", contextWindow: "128", rerankCandidateLimit: "20", parameterSource: "合成文档 v1", modelConfirmed: "on", authorizeTenant: "21" })) v.set(key, value); return v; }
describe("S2 完整草稿声明", () => {
  it("重排拥有独立参数，名称不替代供应商编码", () => {
    expect(platformDeploymentInput(values(), model)).toMatchObject({ displayName: "合成部署", modelCode: "synthetic-resource-id", modelType: "RERANK", capabilities: ["RERANK"],
      contextWindow: 128, outputLimit: null, embeddingDimension: null, rerankCandidateLimit: 20, plannedTenantIds: ["21"], modelConfirmed: true });
  });
  it.each(["0", "-1", "1.5", "", "2147483648", "Infinity"])("拒绝错误参数 %s", parameter => { const v = values(); v.set("rerankCandidateLimit", parameter); expect(() => platformDeploymentInput(v, model)).toThrow(); });
  it("没有模型、显式确认或租户时不生成写入", () => {
    expect(() => platformDeploymentInput(values(), undefined)).toThrow();
    const v = values(); v.delete("modelConfirmed"); expect(() => platformDeploymentInput(v, model)).toThrow();
    v.set("modelConfirmed", "true"); expect(() => platformDeploymentInput(v, model)).toThrow();
    v.set("modelConfirmed", "on"); v.delete("authorizeTenant"); expect(() => platformDeploymentInput(v, model)).toThrow();
  });
  it("未知类型只能显式选择连接声明能力", () => {
    const unknown = { ...model, modelType: null, capabilities: [], classification: "REQUIRES_ADMIN_CONFIRMATION" as const };
    const v = values(); expect(() => platformDeploymentInput(v, unknown)).toThrow();
    v.set("capability", "CHAT"); expect(() => platformDeploymentInput(v, unknown)).toThrow();
    v.set("capability", "RERANK"); expect(platformDeploymentInput(v, unknown).capabilities).toEqual(["RERANK"]);
  });
  it("对话输出不能超过上下文，来源不能换行", () => {
    const v = values(); v.set("outputLimit", "129");
    const chat = { ...model, modelType: "CHAT" as const, declaredCapability: "CHAT" as const, capabilities: ["CHAT"] };
    expect(() => platformDeploymentInput(v, chat)).toThrow("最大输出不能超过上下文窗口。");
    v.set("outputLimit", "64"); expect(platformDeploymentInput(v, chat).outputLimit).toBe(64);
    v.set("parameterSource", "文档\n备注"); expect(() => platformDeploymentInput(v, chat)).toThrow();
  });
});
