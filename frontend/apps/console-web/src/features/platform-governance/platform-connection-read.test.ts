import { describe, expect, it, vi } from "vitest";
import { ApiRequestError, type ControlPlaneClient, type PageResult, type PlatformProviderConnectionSummary,
  type PlatformProviderModelCandidate, type PlatformProviderPresets } from "../../api/control-plane-client";
import { readPlatformConnectionDetail, readPlatformConnectionDirectory } from "./platform-connection-read";

const id = "610000000000000001";
const connection: PlatformProviderConnectionSummary = { id, displayName: "合成连接", providerKind: "OPENAI_COMPATIBLE", version: 3,
  status: "VERIFIED", discoveryProfile: "OPENAI_MODELS_API", credentialConfigured: true, discoveryCredentialsConfigured: false,
  published: true, region: "unspecified", authenticationType: "API_KEY", createdAt: "2026-10-16T00:00:00Z",
  lastProbeCode: null, lastProbeSucceeded: true, lastProbedAt: "2026-10-16T00:00:00Z" };
const presets: PlatformProviderPresets = { writesAvailable: true, version: "synthetic", items: [{ id: "CUSTOM", name: "自定义连接", region: "unspecified",
  regionLabel: "自行配置", capabilities: ["CHAT"], discoveryProfile: "OPENAI_MODELS_API", variants: [{ capability: "CHAT",
    endpointTemplate: "", workspaceRequired: false, runtimeProtocol: "OPENAI_COMPATIBLE", discoveryProfile: "OPENAI_MODELS_API" }] }] };
const candidate: PlatformProviderModelCandidate = { modelCode: "合成模型", displayName: "合成模型", modelType: "CHAT", capabilities: ["CHAT"], classification: "CONFIRMED_BY_PROVIDER" };
const page = <T,>(items: T[]): PageResult<T> => ({ items, pageNumber: 1, pageSize: 20, totalElements: items.length, totalPages: 1, hasPrevious: false, hasNext: false });
function client() {
  return {
    listPlatformProviderConnections: vi.fn<ControlPlaneClient["listPlatformProviderConnections"]>().mockResolvedValue(page([connection])),
    getPlatformProviderPresets: vi.fn<ControlPlaneClient["getPlatformProviderPresets"]>().mockResolvedValue(presets),
    getPlatformProviderConnectionDirectorySummary: vi.fn<ControlPlaneClient["getPlatformProviderConnectionDirectorySummary"]>().mockResolvedValue({ publishedConnections: 21 }),
    getPlatformProviderConnection: vi.fn<ControlPlaneClient["getPlatformProviderConnection"]>().mockResolvedValue(connection),
    getPlatformProviderConnectionPublication: vi.fn<ControlPlaneClient["getPlatformProviderConnectionPublication"]>().mockResolvedValue({ connectionId: id,
      connectionRevision: 3, published: true, publicationRevision: 1, probedAt: connection.lastProbedAt, publishedAt: "2026-10-16T01:00:00Z" }),
    listPlatformProviderModelCandidates: vi.fn<ControlPlaneClient["listPlatformProviderModelCandidates"]>().mockResolvedValue(page([candidate])),
  };
}
const error = (status: number) => new ApiRequestError(status, status === 0 ? "NETWORK_UNAVAILABLE" : "SYNTHETIC.FAILURE", "安全失败", "");
const directory = (c: ReturnType<typeof client>) => readPlatformConnectionDirectory(c, 1, 20, "");
const detail = (c: ReturnType<typeof client>) => readPlatformConnectionDetail(c, id, 1, 20, "");

describe("S1 辅助读取有限降级", () => {
  it.each([0, 500, 503, 599])("摘要%s仅使全目录发布数未知，列表事实保持", async status => {
    const c = client(); c.getPlatformProviderConnectionDirectorySummary.mockRejectedValue(error(status));
    const result = await directory(c);
    expect(result.partial).toBe(true); expect(result.publishedConnections).toBeUndefined();
    expect(result.connections.items).toEqual([connection]);
  });
  it.each([401, 403, 404, 409, 429])("摘要%s不能作为可用性降级", async status => {
    const c = client(), failure = error(status); c.getPlatformProviderConnectionDirectorySummary.mockRejectedValue(failure);
    await expect(directory(c)).rejects.toBe(failure);
  });
  it.each(["listPlatformProviderConnections", "getPlatformProviderPresets"] as const)("主读取%s的5xx失败关闭", async method => {
    const c = client(), failure = error(503); c[method].mockRejectedValue(failure);
    await expect(directory(c)).rejects.toBe(failure);
  });
  it("只有显式发送阶段网络分类可降级，解析TypeError和SyntaxError不能降级", async () => {
    for (const failure of [new TypeError("body-consumed"), new SyntaxError("invalid-json"), new ApiRequestError(0, "UNKNOWN", "", "")]) {
      const c = client(); c.getPlatformProviderConnectionDirectorySummary.mockRejectedValue(failure);
      await expect(directory(c)).rejects.toBe(failure);
    }
  });
  it("恢复必须取得新的完整摘要，不复用旧值或将0误判为缺失", async () => {
    const c = client(); expect((await directory(c)).publishedConnections).toBe(21);
    c.getPlatformProviderConnectionDirectorySummary.mockRejectedValueOnce(error(503));
    expect((await directory(c)).publishedConnections).toBeUndefined();
    c.getPlatformProviderConnectionDirectorySummary.mockResolvedValue({ publishedConnections: 0 });
    expect(await directory(c)).toMatchObject({ partial: false, publishedConnections: 0 });
  });
  it.each(["publication", "candidates", "both"])("详情%s缺失只保留独立成功事实", async missing => {
    const c = client();
    if (missing !== "candidates") c.getPlatformProviderConnectionPublication.mockRejectedValue(error(503));
    if (missing !== "publication") c.listPlatformProviderModelCandidates.mockRejectedValue(error(0));
    const result = await detail(c);
    expect(result.kind).toBe("partial");
    if (result.kind === "empty") throw new Error("不能误判主连接为空");
    expect(result.connection.published).toBe(missing === "candidates" ? true : undefined);
    expect(result.candidates).toEqual(missing === "publication" ? page([candidate]) : undefined);
    expect(result.discoveredTotal).toBe(missing === "publication" ? 1 : undefined);
  });
  it("附加计数读取失败时候选投影未知，不能以筛选总数补齐全目录数", async () => {
    const c = client(); c.listPlatformProviderModelCandidates.mockResolvedValueOnce(page([candidate])).mockRejectedValueOnce(error(503));
    const result = await readPlatformConnectionDetail(c, id, 2, 20, "合成");
    expect(result).toMatchObject({ kind: "partial", connection: { published: true } });
    if (result.kind === "empty") throw new Error("不能误判主连接为空");
    expect(result.candidates).toBeUndefined(); expect(result.discoveredTotal).toBeUndefined();
    expect(c.listPlatformProviderModelCandidates.mock.calls.map(call => call.slice(2))).toEqual([[2, 20, "合成"], [1, 1]]);
  });
  it("候选页5xx不能掩盖附加计数404", async () => {
    const c = client(), failure = error(404);
    c.listPlatformProviderModelCandidates.mockRejectedValueOnce(error(503)).mockRejectedValueOnce(failure);
    await expect(readPlatformConnectionDetail(c, id, 1, 20, "合成")).rejects.toBe(failure);
  });
  it("并发辅助5xx不能掩盖迟到403，安全结果明确前不能返回partial", async () => {
    const c = client(); let reject!: (reason: unknown) => void;
    c.getPlatformProviderConnectionPublication.mockRejectedValue(error(503));
    c.listPlatformProviderModelCandidates.mockReturnValue(new Promise((_, rejected) => { reject = rejected; }));
    const result = detail(c); let settled = false;
    void result.then(() => { settled = true; }, () => { settled = true; });
    await Promise.resolve(); await Promise.resolve(); expect(settled).toBe(false);
    const failure = error(403); reject(failure); await expect(result).rejects.toBe(failure);
  });
  it.each([401, 403])("辅助%s先返回时立即失败关闭，不等待另一个悬挂读取", async status => {
    const c = client(), failure = error(status);
    c.getPlatformProviderConnectionPublication.mockReturnValue(new Promise(() => undefined));
    c.listPlatformProviderModelCandidates.mockRejectedValue(failure);
    await expect(detail(c)).rejects.toBe(failure);
  });
  it("仅精确主连接404可作为空详情，预设403优先拒绝", async () => {
    const c = client(), missing = new ApiRequestError(404, "MODEL_RUNTIME.RESOURCE.0001", "安全不存在", "");
    c.getPlatformProviderConnection.mockRejectedValue(missing);
    expect(await detail(c)).toEqual({ kind: "empty" });
    const forbidden = error(403); c.getPlatformProviderPresets.mockRejectedValue(forbidden);
    await expect(detail(c)).rejects.toBe(forbidden);
  });
  it("独立发布404不能误判主连接不存在", async () => {
    const c = client(), missing = new ApiRequestError(404, "MODEL_RUNTIME.RESOURCE.0001", "安全不存在", "");
    c.getPlatformProviderConnectionPublication.mockRejectedValue(missing);
    await expect(detail(c)).rejects.toBe(missing);
  });
  it("非法摘要、主对象和修订绑定均失败关闭", async () => {
    const c = client(); c.getPlatformProviderConnectionDirectorySummary.mockResolvedValue({ publishedConnections: -1 });
    await expect(directory(c)).rejects.toThrow("invalid-connection-summary");
    c.getPlatformProviderConnection.mockResolvedValue({ ...connection, id: "wrong" });
    await expect(detail(c)).rejects.toThrow("invalid-connection-binding");
    c.getPlatformProviderConnection.mockResolvedValue(connection);
    c.getPlatformProviderConnectionPublication.mockResolvedValue({ connectionId: id, connectionRevision: 4, published: true,
      publicationRevision: 1, probedAt: null, publishedAt: null });
    await expect(detail(c)).rejects.toThrow("invalid-publication-binding");
  });
  it("一侧网络失败不能掩盖另一侧畸形成功投影", async () => {
    const c = client(); c.getPlatformProviderConnectionPublication.mockRejectedValue(error(503));
    c.listPlatformProviderModelCandidates.mockResolvedValue({ ...page([candidate]), totalElements: -1 });
    await expect(detail(c)).rejects.toThrow("invalid-connection-page");
  });
  it("对象停用保持对象事实，不读取候选也不新增页面暂停事实", async () => {
    const c = client(); c.getPlatformProviderConnection.mockResolvedValue({ ...connection, status: "DISABLED" });
    const result = await detail(c);
    expect(result).toMatchObject({ kind: "ready", connection: { status: "DISABLED" }, discoveredTotal: 0 });
    expect(c.listPlatformProviderModelCandidates).not.toHaveBeenCalled();
  });
});

// 暂停事实来自必读预设，不从单对象状态、故障或客户端参数推导。
describe("S1 写入可用性读取合同", () => {
  it.each([undefined, null, "false", 0])("可用性字段%s非法时两页失败关闭", async writesAvailable => {
    const c = client(); c.getPlatformProviderPresets.mockResolvedValue(Object.assign({}, presets, { writesAvailable }) as PlatformProviderPresets);
    await expect(directory(c)).rejects.toThrow("invalid-connection-presets");
    await expect(detail(c)).rejects.toThrow("invalid-connection-presets");
  });
  it("完整读取且明确暂停时详情进入disabled，部分辅助失败优先保留partial", async () => {
    const c = client(); c.getPlatformProviderPresets.mockResolvedValue(Object.assign({}, presets, { writesAvailable: false }));
    expect((await detail(c)).kind).toBe("disabled");
    c.listPlatformProviderModelCandidates.mockRejectedValue(error(503));
    expect((await detail(c)).kind).toBe("partial");
    c.listPlatformProviderModelCandidates.mockRejectedValue(error(403));
    await expect(detail(c)).rejects.toMatchObject({ status: 403 });
  });
});
