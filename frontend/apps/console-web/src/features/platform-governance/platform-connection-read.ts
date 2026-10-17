import { ApiRequestError, type ControlPlaneClient, type PageResult, type PlatformProviderConnectionSummary,
  type PlatformProviderModelCandidate, type PlatformProviderPresets } from "../../api/control-plane-client";

export type ConnectionCandidates = Pick<PageResult<PlatformProviderModelCandidate>, "items" | "totalElements" | "hasPrevious" | "hasNext">;
/** 发布未知与未发布分别表达，不能由主读取或历史发布补齐辅助事实。 */
export type ConnectionProjection = Omit<PlatformProviderConnectionSummary, "published"> & { published: boolean | undefined };
const EMPTY_CANDIDATES: ConnectionCandidates = { items: [], totalElements: 0, hasPrevious: false, hasNext: false };
type ReadClient = Pick<ControlPlaneClient, "listPlatformProviderConnections" | "getPlatformProviderPresets"
  | "getPlatformProviderConnectionDirectorySummary" | "getPlatformProviderConnection" | "getPlatformProviderConnectionPublication"
  | "listPlatformProviderModelCandidates">;

/** ADR-0097：目录只允许发布摘要降级；所有并发安全结果明确后才返回本轮投影。 */
export async function readPlatformConnectionDirectory(client: ReadClient, page: number, size: number, query: string) {
  const results = await settleReads([client.listPlatformProviderConnections(page, size, query),
    client.getPlatformProviderPresets(), client.getPlatformProviderConnectionDirectorySummary()]);
  failReads(results.slice(0, 2), [results[2]!]);
  const connections = value(results[0]);
  const presets = value(results[1]);
  requirePage(connections, validConnection); requirePresets(presets);
  let publishedConnections: number | undefined;
  if (results[2].status === "fulfilled") {
    const summary = results[2].value;
    if (!summary || !count(summary.publishedConnections)) throw new Error("invalid-connection-summary");
    publishedConnections = summary.publishedConnections;
  }
  return { connections, presets, publishedConnections, partial: publishedConnections === undefined };
}

/** 主连接及预设失败关闭；候选和发布各自只保留本轮同一修订的成功事实。 */
export async function readPlatformConnectionDetail(client: ReadClient, id: string, page: number, size: number, query: string) {
  const primary = await settleReads([client.getPlatformProviderConnection(id), client.getPlatformProviderPresets()]);
  try { failReads(primary); } catch (error) {
    // 只有精确主资源的既有 404 合同可成为空详情；辅助 404 或并发拒绝不能借此暴露存在性。
    if (primary[0].status === "rejected" && primary[0].reason === error && error instanceof ApiRequestError
      && error.status === 404 && error.code === "MODEL_RUNTIME.RESOURCE.0001" && primary[1].status === "fulfilled") {
      requirePresets(primary[1].value); return { kind: "empty" as const };
    }
    throw error;
  }
  const current = value(primary[0]), presets = value(primary[1]);
  if (!validConnection(current) || current.id !== id) throw new Error("invalid-connection-binding");
  requirePresets(presets);
  const auxiliary = await settleReads([client.getPlatformProviderConnectionPublication(id, current.version),
    current.status === "VERIFIED" ? readCandidates(client, id, current.version, page, size, query)
      : Promise.resolve({ page: EMPTY_CANDIDATES, total: 0 })]);
  failReads([], auxiliary);
  let published: boolean | undefined;
  if (auxiliary[0].status === "fulfilled") {
    const publication = auxiliary[0].value;
    if (!publication || publication.connectionId !== id || publication.connectionRevision !== current.version
      || typeof publication.published !== "boolean") throw new Error("invalid-publication-binding");
    published = publication.published;
  }
  const candidates = auxiliary[1].status === "fulfilled" ? auxiliary[1].value : undefined;
  const connection: ConnectionProjection = { ...current, published };
  return { kind: published === undefined || candidates === undefined ? "partial" as const
      : presets.writesAvailable ? "ready" as const : "disabled" as const,
    connection, presets, candidates: candidates?.page, discoveredTotal: candidates?.total };
}

async function readCandidates(client: ReadClient, id: string, version: number, page: number, size: number, query: string) {
  const filtered = client.listPlatformProviderModelCandidates(id, version, page, size, query);
  const results = await settleReads([filtered, query ? client.listPlatformProviderModelCandidates(id, version, 1, 1) : filtered]);
  // 搜索页与全目录总数共同构成候选投影；验证成功部分再传播故障，避免畸形响应被网络故障掩盖。
  for (const result of results) if (result.status === "fulfilled") requirePage(result.value, validCandidate);
  failReads(results);
  return { page: value(results[0]), total: value(results[1]).totalElements };
}

function unavailable(error: unknown): boolean {
  return error instanceof ApiRequestError && ((error.status >= 500 && error.status <= 599)
    || (error.status === 0 && error.code === "NETWORK_UNAVAILABLE"));
}

/** 可用性失败等待其余事实；明确的身份／权限拒绝立即终止，不能被悬挂读取延迟。 */
function settleReads<T extends readonly unknown[] | []>(reads: T) {
  const denied = new Promise<never>((_, reject) => {
    for (const read of reads) void Promise.resolve(read).catch(error => {
      if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) reject(error);
    });
  });
  return Promise.race([Promise.allSettled(reads), denied]);
}

function failReads(required: readonly PromiseSettledResult<unknown>[], auxiliary: readonly PromiseSettledResult<unknown>[] = []): void {
  const failures = [...required.filter(result => result.status === "rejected"),
    ...auxiliary.filter(result => result.status === "rejected" && !unavailable(result.reason))]
    .filter((result): result is PromiseRejectedResult => result.status === "rejected");
  if (!failures.length) return;
  const failure = failures.find(result => result.reason instanceof ApiRequestError && result.reason.status === 401)
    ?? failures.find(result => result.reason instanceof ApiRequestError && result.reason.status === 403)
    ?? failures.find(result => !unavailable(result.reason)) ?? failures[0]!;
  throw failure.reason ?? new Error("invalid-read-failure");
}

function value<T>(result: PromiseSettledResult<T>): T {
  if (result.status === "rejected") throw result.reason;
  return result.value;
}

function count(value: unknown): value is number { return Number.isSafeInteger(value) && Number(value) >= 0; }
function record(value: unknown): value is Record<string, unknown> { return value !== null && typeof value === "object"; }
function validConnection(value: unknown): value is PlatformProviderConnectionSummary {
  return record(value) && typeof value.id === "string" && typeof value.displayName === "string" && count(value.version)
    && ["DRAFT", "VERIFIED", "DISABLED"].includes(String(value.status)) && typeof value.credentialConfigured === "boolean"
    && typeof value.discoveryCredentialsConfigured === "boolean" && typeof value.published === "boolean";
}
function validCandidate(value: unknown): value is PlatformProviderModelCandidate {
  return record(value) && typeof value.modelCode === "string"
    && ["CONFIRMED_BY_PROVIDER", "CONFIRMED_BY_CONTROLLED_MAPPING", "CONFIRMED_BY_ADMIN", "REQUIRES_ADMIN_CONFIRMATION"].includes(String(value.classification))
    && (value.modelType === null || value.modelType === "CHAT" || value.modelType === "EMBEDDING");
}
function requirePage<T>(page: Pick<PageResult<T>, "items" | "totalElements" | "hasPrevious" | "hasNext">, item: (value: unknown) => boolean): void {
  if (!page || !Array.isArray(page.items) || !page.items.every(item) || !count(page.totalElements)
    || typeof page.hasPrevious !== "boolean" || typeof page.hasNext !== "boolean") throw new Error("invalid-connection-page");
}
function requirePresets(presets: PlatformProviderPresets): void {
  if (!presets || typeof presets.writesAvailable !== "boolean" || !Array.isArray(presets.items) || !presets.items.length || presets.items.some(item => !item
    || !Array.isArray(item.capabilities) || !item.capabilities.length || !Array.isArray(item.variants) || !item.variants.length)) {
    throw new Error("invalid-connection-presets");
  }
}
