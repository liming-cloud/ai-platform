import { describe, expect, it } from "vitest";
import { ApiRequestError, type ControlPlaneClient, type PlatformModelDeploymentSummary } from "../../api/control-plane-client";
import { readPlatformDeployments } from "./platform-deployment-read";

const deployment: PlatformModelDeploymentSummary = { id: "123", providerConnectionId: "11", providerConnectionRevision: 2, modelCode: "synthetic-id", displayName: "合成部署",
  modelType: "CHAT", capabilities: ["CHAT"], contextWindow: 128, outputLimit: 64, embeddingDimension: null, verificationExpiresAt: null, verifiedAt: null,
  status: "DRAFT", availability: "UNAVAILABLE", nextStep: "PROBE_MODEL", version: 0, createdAt: "2026-10-20T00:00:00Z", connectionDisplayName: "合成连接", inputs: null };
const facts = { writesAvailable: true, counts: { total: 1, drafts: 1, verified: 0, published: 0, disabled: 0 } };
function reader(overrides = {}) { return { getPlatformModelDeployment: async () => deployment, getPlatformDeploymentPageFacts: async () => facts,
  listPlatformModelDeployments: async () => ({ items: [deployment], pageNumber: 1, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false }), ...overrides } as Pick<ControlPlaneClient, "getPlatformModelDeployment" | "listPlatformModelDeployments" | "getPlatformDeploymentPageFacts">; }
const failure = (status: number) => new ApiRequestError(status, status === 0 ? "NETWORK_UNAVAILABLE" : "SYNTHETIC_ERROR", "合成错误", "trc_synthetic");
describe("S2 页面事实", () => {
  it.each([
    { deploymentRevision: 1 }, { activeGrantCount: 2 }, { activeTenantIds: ["202"] },
    { activeTenantIds: ["201", "201"] }, { currentSourceAvailable: "true" }, { catalogEntryId: "0" }
  ])("独立发布事实畸形时不能用计划集合或状态补齐：%o", async invalid => {
    const published = { ...deployment, status: "PUBLISHED", version: 2,
      inputs: { parameterSource: "合成资料", residency: "合成区域", candidateDisplayName: "合成候选", plannedTenantIds: ["201"] },
      supply: { publicationId: "401", deploymentRevision: 2, catalogEntryId: "501", publishedAt: "2026-10-21T00:00:00Z",
        selectedTenantCount: 1, activeGrantCount: 1, revokedGrantCount: 0, dependentConfigurationCount: 0,
        activeTenantIds: ["201"], currentSourceAvailable: true, ...invalid } };
    await expect(readPlatformDeployments(reader({ getPlatformModelDeployment: async () => published }), { id: "123" }))
      .rejects.toThrow("invalid-deployment-binding");
  });
  it("完整发布事实允许全部撤权后的真实零集合，不回退计划", async () => {
    const published = { ...deployment, status: "PUBLISHED", version: 2,
      inputs: { parameterSource: "合成资料", residency: "合成区域", candidateDisplayName: "合成候选", plannedTenantIds: ["201"] },
      supply: { publicationId: "401", deploymentRevision: 2, catalogEntryId: "501", publishedAt: "2026-10-21T00:00:00Z",
        selectedTenantCount: 1, activeGrantCount: 0, revokedGrantCount: 1, dependentConfigurationCount: 0,
        activeTenantIds: [], currentSourceAvailable: true } };
    const result = await readPlatformDeployments(reader({ getPlatformModelDeployment: async () => published }), { id: "123" });
    expect(result.kind).toBe("ready");
    expect((result.data as PlatformModelDeploymentSummary).supply?.activeTenantIds).toEqual([]);
  });
  it("恢复精确部署并拒绝错对象", async () => { expect((await readPlatformDeployments(reader(), { id: "123" })).kind).toBe("ready"); await expect(readPlatformDeployments(reader(), { id: "124" })).rejects.toThrow("invalid-deployment-binding"); });
  it.each([0, 500, 503])("仅辅助可用性错误 %s 进入只读 partial", async status => {
    expect((await readPlatformDeployments(reader({ getPlatformDeploymentPageFacts: async () => { throw failure(status); } }), { id: "123" })).kind).toBe("partial");
  });
  it.each([401, 403, 404, 409])("辅助拒绝 %s 不得降级", async status => { await expect(readPlatformDeployments(reader({ getPlatformDeploymentPageFacts: async () => { throw failure(status); } }), { id: "123" })).rejects.toBeInstanceOf(ApiRequestError); });
  it("实例暂停只有 disabled，不把单个对象停用当页面暂停", async () => {
    expect((await readPlatformDeployments(reader({ getPlatformDeploymentPageFacts: async () => ({ ...facts, writesAvailable: false }) }), { id: "123" })).kind).toBe("disabled");
    expect((await readPlatformDeployments(reader({ getPlatformModelDeployment: async () => ({ ...deployment, status: "DISABLED" }) }), { id: "123" })).kind).toBe("ready");
  });
  it("主读取畸形或辅助缺字段不能被可用性降级掩盖", async () => {
    await expect(readPlatformDeployments(reader({ getPlatformModelDeployment: async () => ({ id: "123" }), getPlatformDeploymentPageFacts: async () => { throw failure(503); } }), { id: "123" })).rejects.toThrow("invalid-deployment-binding");
    await expect(readPlatformDeployments(reader({ getPlatformDeploymentPageFacts: async () => ({ counts: facts.counts }) }), { id: "123" })).rejects.toThrow("invalid-deployment-facts");
  });
  it("权限拒绝不等待悬挂的主读取", async () => {
    await expect(readPlatformDeployments(reader({ getPlatformModelDeployment: () => new Promise(() => {}), getPlatformDeploymentPageFacts: async () => { throw failure(403); } }), { id: "123" })).rejects.toMatchObject({ status: 403 });
  });
});
