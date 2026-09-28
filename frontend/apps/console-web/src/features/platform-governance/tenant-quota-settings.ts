import type { TenantQuotaSummary, TenantQuotaUsage, TenantQuotas } from "../../api/control-plane-client";
import { defaultTenantDraft, provisioningQuotas } from "./tenant-provisioning";

export type TenantQuotaForm = Pick<ReturnType<typeof defaultTenantDraft>,
  "knowledgeBases" | "documentsPerKnowledgeBase" | "storageGb" | "fileMb" | "textMib">;

/** 配额仅在输入处使用人类单位；服务端契约始终传精确整数字节。 */
export function decimalFromBytes(bytes: string, unit: bigint): string {
  const scaled = BigInt(bytes) * 10n ** 20n / unit;
  const whole = scaled / 10n ** 20n;
  const fraction = (scaled % 10n ** 20n).toString().padStart(20, "0").replace(/0+$/, "");
  return fraction ? `${whole}.${fraction}` : whole.toString();
}

export function quotaFormFrom(quota: TenantQuotaSummary): TenantQuotaForm {
  return {
    knowledgeBases: String(quota.knowledgeBases),
    documentsPerKnowledgeBase: String(quota.documentsPerKnowledgeBase),
    storageGb: decimalFromBytes(quota.bytesPerKnowledgeBase, 1_000_000_000n),
    fileMb: decimalFromBytes(quota.maxFileBytes, 1_000_000n),
    textMib: decimalFromBytes(quota.maxTextBytes, 1_048_576n),
  };
}

export function quotasFromForm(form: TenantQuotaForm, usage: TenantQuotaUsage, current: TenantQuotaSummary): TenantQuotas {
  const next = provisioningQuotas({ ...defaultTenantDraft(), ...form });
  if (next.knowledgeBases < usage.knowledgeBases
    || next.documentsPerKnowledgeBase < usage.documentsPerKnowledgeBase
    || BigInt(next.bytesPerKnowledgeBase) < BigInt(usage.bytesPerKnowledgeBase)) {
    throw new Error("保存值不能低于当前已用量。");
  }
  if (!usage.complete && (next.knowledgeBases < current.knowledgeBases
    || next.documentsPerKnowledgeBase < current.documentsPerKnowledgeBase
    || BigInt(next.bytesPerKnowledgeBase) < BigInt(current.bytesPerKnowledgeBase))) {
    throw new Error("当前用量尚未完整核实，不能下调资源上限。");
  }
  return next;
}
