import { ApiRequestError, type ControlPlaneClient, type PlatformDeploymentPageFacts, type PlatformModelDeploymentSummary, type PageResult } from "../../api/control-plane-client";

type Reader = Pick<ControlPlaneClient, "listPlatformModelDeployments" | "getPlatformModelDeployment" | "getPlatformDeploymentPageFacts">;

/** 主读取和全部安全拒绝失败关闭；仅辅助网络／5xx 可降级，不能用旧摘要补齐。 */
export async function readPlatformDeployments(client: Reader, target: { id: string } | { page: number; size: number }) {
  const main = "id" in target ? client.getPlatformModelDeployment(target.id) : client.listPlatformModelDeployments(target.page, target.size);
  const reads = [main, client.getPlatformDeploymentPageFacts()] as const;
  const results = await settleDeploymentReads(reads);
  for (const result of results) if (result.status === "rejected" && result.reason instanceof ApiRequestError
    && (result.reason.status === 401 || result.reason.status === 403)) throw result.reason;
  if (results[0].status === "rejected") throw results[0].reason;
  const data = results[0].value;
  if ("id" in target) {
    if (!validDeployment(data) || data.id !== target.id) throw new Error("invalid-deployment-binding");
  } else {
    const page = data as PageResult<PlatformModelDeploymentSummary>;
    if (!page || !Array.isArray(page.items) || !page.items.every(validDeployment) || !Number.isSafeInteger(page.totalElements)
      || page.totalElements < 0 || page.pageNumber !== target.page || page.pageSize !== target.size
      || typeof page.hasNext !== "boolean" || typeof page.hasPrevious !== "boolean") throw new Error("invalid-deployment-page");
  }
  let facts: PlatformDeploymentPageFacts | undefined;
  if (results[1].status === "fulfilled") {
    facts = results[1].value;
    if (!facts || typeof facts.writesAvailable !== "boolean" || !facts.counts
      || ["total", "drafts", "verified", "published", "disabled"].some(key => {
        const value = facts!.counts[key as keyof PlatformDeploymentPageFacts["counts"]];
        return !Number.isSafeInteger(value) || value < 0;
      })) throw new Error("invalid-deployment-facts");
  } else if (!(results[1].reason instanceof ApiRequestError && (results[1].reason.status >= 500
    || (results[1].reason.status === 0 && results[1].reason.code === "NETWORK_UNAVAILABLE")))) throw results[1].reason;
  return { data, facts, kind: !facts ? "partial" as const : facts.writesAvailable ? "ready" as const : "disabled" as const };
}

function validDeployment(value: unknown): value is PlatformModelDeploymentSummary {
  if (!value || typeof value !== "object") return false;
  const d = value as PlatformModelDeploymentSummary;
  if (!/^[1-9][0-9]*$/.test(d.id) || typeof d.displayName !== "string" || typeof d.connectionDisplayName !== "string"
    || !["CHAT", "EMBEDDING", "RERANK"].includes(d.modelType) || !["DRAFT", "VERIFIED", "PUBLISHED", "DISABLED"].includes(d.status)
    || !Number.isSafeInteger(d.version) || d.version < 0 || !Number.isSafeInteger(d.contextWindow) || d.contextWindow <= 0) return false;
  if (!validSupply(d)) return false;
  if (d.inputs === null) return true;
  const input = d.inputs;
  return !!input && typeof input.parameterSource === "string" && typeof input.residency === "string"
    && typeof input.candidateDisplayName === "string" && Array.isArray(input.plannedTenantIds)
    && input.plannedTenantIds.length > 0 && input.plannedTenantIds.every(id => typeof id === "string" && /^[1-9][0-9]*$/.test(id));
}

function validSupply(d: PlatformModelDeploymentSummary): boolean {
  const s = d.supply;
  if (s == null) return true;
  if (!s || typeof s !== "object" || !d.inputs || !Array.isArray(d.inputs.plannedTenantIds)
    || !publicId(s.publicationId) || !publicId(s.catalogEntryId) || typeof s.currentSourceAvailable !== "boolean"
    || !Number.isSafeInteger(s.deploymentRevision) || s.deploymentRevision < 1
    || !(d.status === "PUBLISHED" ? s.deploymentRevision === d.version : d.status === "DISABLED" && s.deploymentRevision + 1 === d.version)
    || typeof s.publishedAt !== "string" || !Number.isFinite(Date.parse(s.publishedAt))) return false;
  const counts = [s.selectedTenantCount, s.activeGrantCount, s.revokedGrantCount, s.dependentConfigurationCount];
  return counts.every(count => Number.isSafeInteger(count) && count >= 0)
    && s.selectedTenantCount <= 1000 && s.selectedTenantCount === d.inputs.plannedTenantIds.length
    && s.activeGrantCount + s.revokedGrantCount === s.selectedTenantCount
    && Array.isArray(s.activeTenantIds) && s.activeTenantIds.length === s.activeGrantCount
    && new Set(s.activeTenantIds).size === s.activeTenantIds.length
    && s.activeTenantIds.every(id => publicId(id) && d.inputs!.plannedTenantIds.includes(id));
}

function publicId(value: unknown): value is string {
  return typeof value === "string" && /^[1-9][0-9]{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;
}

/** 发布前显示计划，发布后只恢复独立活动授权；缺正式事实时保持未知，不能回退计划。 */
export function displayedSupplyTenantIds(deployment: PlatformModelDeploymentSummary): string[] | undefined {
  return deployment.status === "DRAFT" || deployment.status === "VERIFIED"
    ? deployment.inputs?.plannedTenantIds : deployment.supply?.activeTenantIds;
}

/** 身份／权限拒绝立即结束本轮读取，剩余结果仅由页面所有者令牌控制，不能恢复旧事实。 */
export function settleDeploymentReads<T extends readonly unknown[] | []>(reads: T) {
  const denied = new Promise<never>((_, reject) => {
    for (const read of reads) void Promise.resolve(read).catch(error => {
      if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) reject(error);
    });
  });
  return Promise.race([Promise.allSettled(reads), denied]);
}
