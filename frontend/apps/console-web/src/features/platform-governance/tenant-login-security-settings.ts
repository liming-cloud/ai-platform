import type { TenantLoginSecurityPolicy } from "../../api/control-plane-client";

export type SecurityForm = Record<"sessionHours" | "failedAttempts" | "lockMinutes", string>;

export const SECURITY_FIELDS = [
  { key: "sessionHours", label: "会话有效期", unit: "小时", min: 1, max: 24 },
  { key: "failedAttempts", label: "登录失败次数", unit: "次", min: 3, max: 10 },
  { key: "lockMinutes", label: "锁定时长", unit: "分钟", min: 5, max: 60 },
] as const;

export function securityFormFrom(policy: TenantLoginSecurityPolicy): SecurityForm {
  return {
    sessionHours: String(policy.sessionHours),
    failedAttempts: String(policy.failedAttempts),
    lockMinutes: String(policy.lockMinutes),
  };
}

/** 先按 ADR-0079 的整数范围拒绝无效输入；服务端仍独立校验完整请求。 */
export function securityPolicyFromForm(form: SecurityForm): Omit<TenantLoginSecurityPolicy, "version"> {
  const values = {} as Omit<TenantLoginSecurityPolicy, "version">;
  for (const field of SECURITY_FIELDS) {
    const raw = form[field.key];
    const value = Number(raw);
    if (!raw.trim() || !Number.isSafeInteger(value) || value < field.min || value > field.max) {
      throw new Error(`${field.label}应为 ${field.min}–${field.max} ${field.unit}的整数。`);
    }
    values[field.key] = value;
  }
  return values;
}
