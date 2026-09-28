import { describe, expect, it } from "vitest";
import { securityFormFrom, securityPolicyFromForm } from "./tenant-login-security-settings";

describe("租户登录安全输入", () => {
  it("按已验收原型的三项字段往返", () => {
    const form = securityFormFrom({ sessionHours: 8, failedAttempts: 5, lockMinutes: 15, version: 0 });
    expect(form).toEqual({ sessionHours: "8", failedAttempts: "5", lockMinutes: "15" });
    expect(securityPolicyFromForm(form)).toEqual({ sessionHours: 8, failedAttempts: 5, lockMinutes: 15 });
  });

  it("在提交前拒绝小数、空值和 ADR-0079 范围外的设置", () => {
    expect(() => securityPolicyFromForm({ sessionHours: "1.5", failedAttempts: "5", lockMinutes: "15" })).toThrow(/会话有效期/);
    expect(() => securityPolicyFromForm({ sessionHours: "", failedAttempts: "5", lockMinutes: "15" })).toThrow(/会话有效期/);
    expect(() => securityPolicyFromForm({ sessionHours: "25", failedAttempts: "5", lockMinutes: "15" })).toThrow(/会话有效期/);
    expect(() => securityPolicyFromForm({ sessionHours: "8", failedAttempts: "2", lockMinutes: "15" })).toThrow(/登录失败次数/);
    expect(() => securityPolicyFromForm({ sessionHours: "8", failedAttempts: "5", lockMinutes: "61" })).toThrow(/锁定时长/);
  });
});
