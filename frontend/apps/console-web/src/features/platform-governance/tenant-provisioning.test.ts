import { describe, expect, it } from "vitest";
import { bytesFromDecimal, defaultTenantDraft, provisioningQuotas, provisioningFailure, restoreTenantDraft, serializeTenantDraft, tenantInformationValid } from "./tenant-provisioning";
import { ApiRequestError } from "../../api/control-plane-client";

describe("原子开户表单契约", () => {
  it("配额错误返回可编辑步骤并定位字段，不把用户留在管理员页", () => {
    expect.assertions(2);
    try { provisioningQuotas({ ...defaultTenantDraft(), storageGb: "0.01" }); }
    catch (error) { expect(provisioningFailure(error)).toMatchObject({ step: 2, field: "fileMb" }); }
    try { provisioningQuotas({ ...defaultTenantDraft(), knowledgeBases: "0" }); }
    catch (error) { expect(provisioningFailure(error)).toMatchObject({ step: 2, field: "knowledgeBases" }); }
  });
  it("冲突区分编码与登录名，其他错误仍使用安全摘要", () => {
    expect(provisioningFailure(new ApiRequestError(409, "ORGANIZATION_ACCESS.CONFLICT.0081", "敏感响应", ""))).toMatchObject({ step: 1, field: "tenantCode" });
    expect(provisioningFailure(new ApiRequestError(409, "ORGANIZATION_ACCESS.CONFLICT.0080", "敏感响应", ""))).toMatchObject({ step: 3, field: "loginName" });
    expect(provisioningFailure(new ApiRequestError(503, "UNKNOWN", "敏感响应", "")).message).not.toContain("敏感响应");
  });
  it("按十进制精确换算容量，不经过浮点整数", () => {
    expect(bytesFromDecimal("0.01", 1_000_000_000n)).toBe("10000000");
    expect(bytesFromDecimal("9223372036.854775807", 1_000_000_000n)).toBe("9223372036854775807");
    expect(bytesFromDecimal("1.25", 1_048_576n)).toBe("1310720");
  });
  it.each(["0", "-1", "NaN", "Infinity", "1e3", "9223372037", "0.0000000001"])("拒绝不可表示容量 %s", value => {
    expect(() => bytesFromDecimal(value, 1_000_000_000n)).toThrow();
  });
  it("默认配额及单文件不得超过每库容量", () => {
    expect(provisioningQuotas(defaultTenantDraft())).toEqual({ knowledgeBases: 3, documentsPerKnowledgeBase: 1000, bytesPerKnowledgeBase: "10000000000", maxFileBytes: "50000000", maxTextBytes: "1048576" });
    expect(() => provisioningQuotas({ ...defaultTenantDraft(), storageGb: "0.01" })).toThrow();
    expect(() => provisioningQuotas({ ...defaultTenantDraft(), knowledgeBases: "2147483648" })).toThrow();
  });
  it("新建信息约束不改变历史读取", () => {
    expect(tenantInformationValid({ ...defaultTenantDraft(), displayName: "青岚科技", tenantCode: "qinglan-demo" })).toBe(true);
    expect(tenantInformationValid({ ...defaultTenantDraft(), displayName: "青", tenantCode: "x" })).toBe(false);
  });
  it("仅恢复同一会话拥有的非秘密白名单草稿", () => {
    const draft = { ...defaultTenantDraft(), displayName: "青岚科技", tenantCode: "qinglan-demo", loginName: "qinglan.admin", password: "不得持久化" };
    const stored = serializeTenantDraft("session-one", draft);
    expect(stored).not.toContain("不得持久化");
    expect(restoreTenantDraft("session-one", stored)?.displayName).toBe("青岚科技");
    expect(restoreTenantDraft("session-two", stored)).toBeUndefined();
    expect(restoreTenantDraft("session-one", "bad json")).toBeUndefined();
  });
});
