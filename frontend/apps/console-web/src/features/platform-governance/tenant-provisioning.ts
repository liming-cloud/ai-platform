import { ApiRequestError, presentApiError, type TenantQuotas } from "../../api/control-plane-client";

/** 明确列出可恢复的非秘密字段；凭据与确认勾选永不进入草稿。 */
export interface TenantDraft {
  displayName: string;
  tenantCode: string;
  loginName: string;
  knowledgeBases: string;
  documentsPerKnowledgeBase: string;
  storageGb: string;
  fileMb: string;
  textMib: string;
}

export function defaultTenantDraft(): TenantDraft {
  return { displayName: "", tenantCode: "", loginName: "", knowledgeBases: "3", documentsPerKnowledgeBase: "1000", storageGb: "10", fileMb: "50", textMib: "1" };
}

/** 数值输入保留为字符串，以有界整数运算校验整数字节。 */
export function bytesFromDecimal(value: string, unit: bigint): string {
  if (value.length > 40 || !/^\d+(?:\.\d+)?$/.test(value)) throw new Error("请输入大于 0 的容量数值。");
  const [whole = "0", fraction = ""] = value.split(".");
  const scale = 10n ** BigInt(fraction.length);
  const numerator = BigInt(whole + fraction) * unit;
  const bytes = numerator / scale;
  if (numerator % scale !== 0n || bytes < 1n || bytes > 9_223_372_036_854_775_807n) throw new Error("容量必须能精确表示为正整数字节，且不超过系统数值上限。");
  return bytes.toString();
}

/** 新建约束沿用原型；不用于裁剪历史租户名称或编码。 */
export function tenantInformationValid(draft: TenantDraft): boolean {
  const length = Array.from(draft.displayName.trim()).length;
  return length >= 2 && length <= 40 && /^[a-z][a-z0-9-]{2,19}$/.test(draft.tenantCode.trim());
}

export function provisioningQuotas(draft: TenantDraft): TenantQuotas {
  function count(field: "knowledgeBases" | "documentsPerKnowledgeBase"): number {
    const value = draft[field];
    if (!/^\d{1,10}$/.test(value) || Number(value) < 1 || Number(value) > 2_147_483_647) throw new TenantQuotaInputError(field, "数量须为 1～2,147,483,647 的整数。");
    return Number(value);
  }
  function bytes(field: "storageGb" | "fileMb" | "textMib", unit: bigint): string {
    try { return bytesFromDecimal(draft[field], unit); }
    catch (error) { throw new TenantQuotaInputError(field, error instanceof Error ? error.message : "请检查容量。"); }
  }
  const quotas = {
    knowledgeBases: count("knowledgeBases"), documentsPerKnowledgeBase: count("documentsPerKnowledgeBase"),
    bytesPerKnowledgeBase: bytes("storageGb", 1_000_000_000n),
    maxFileBytes: bytes("fileMb", 1_000_000n), maxTextBytes: bytes("textMib", 1_048_576n),
  };
  if (BigInt(quotas.maxFileBytes) > BigInt(quotas.bytesPerKnowledgeBase)) throw new TenantQuotaInputError("fileMb", "单文件大小不能超过每库内容容量。");
  return quotas;
}

class TenantQuotaInputError extends Error {
  constructor(readonly field: keyof TenantDraft, message: string) { super(message); }
}

/** 错误只解释当前开户操作，附可编辑步骤与字段，不暴露服务器原始响应。 */
export function provisioningFailure(error: unknown): { step?: 1 | 2 | 3; field?: keyof TenantDraft; message: string } {
  if (error instanceof TenantQuotaInputError) return { step: 2, field: error.field, message: error.message };
  if (error instanceof ApiRequestError && error.status === 409) {
    if (error.code === "ORGANIZATION_ACCESS.CONFLICT.0081") return { step: 1, field: "tenantCode", message: "租户编码已被使用，请更换后继续。" };
    if (error.code === "ORGANIZATION_ACCESS.CONFLICT.0080") return { step: 3, field: "loginName", message: "管理员登录名已被使用，请更换并重新输入密码。" };
  }
  const summary = presentApiError(error, "创建租户");
  return { message: `${summary.title}。${summary.description} 非秘密信息已保留，请重新输入密码后重试。` };
}

/** 持久化白名单防止调用方误把 password 等临时字段一起写入。 */
export function serializeTenantDraft(owner: string, draft: TenantDraft): string {
  const fields = Object.fromEntries(Object.keys(defaultTenantDraft()).map(key => [key, draft[key as keyof TenantDraft]]));
  return JSON.stringify({ owner, fields });
}

export function restoreTenantDraft(owner: string, serialized: string | null): TenantDraft | undefined {
  try {
    if (!serialized || serialized.length > 4096) return undefined;
    const saved = JSON.parse(serialized) as { owner?: unknown; fields?: Record<string, unknown> };
    if (!owner || saved.owner !== owner || !saved.fields) return undefined;
    const draft = defaultTenantDraft();
    for (const key of Object.keys(draft) as (keyof TenantDraft)[]) {
      const value = saved.fields[key];
      if (typeof value !== "string" || value.length > 128) return undefined;
      draft[key] = value;
    }
    return draft;
  } catch { return undefined; }
}
