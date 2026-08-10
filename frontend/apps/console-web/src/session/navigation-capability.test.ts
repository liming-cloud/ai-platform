import { describe, expect, it } from "vitest";
import { previewNavigation, visibleNavigation } from "./navigation-capability";

describe("visibleNavigation", () => {
  it("只显示当前租户会话已具备实际能力的入口", () => {
    expect(visibleNavigation({
      canUseTenantWorkspace: true,
      canUsePlatformGovernance: false,
      capabilityCodes: ["knowledge:read", "assistant:query"],
    }).map((item) => item.id)).toEqual(["data", "query"]);
  });

  it("平台运营主体没有治理写权限时不得看到平台租户入口", () => {
    expect(visibleNavigation({
      canUseTenantWorkspace: false,
      canUsePlatformGovernance: false,
      capabilityCodes: ["platform-session:access"],
    })).toEqual([]);
  });

  it("访问管理员应看到成员与权限入口，普通成员不得看到", () => {
    expect(visibleNavigation({
      canUseTenantWorkspace: true,
      canUsePlatformGovernance: false,
      capabilityCodes: ["access:manage"],
    }).map((item) => item.id)).toEqual(["access"]);
    expect(visibleNavigation({
      canUseTenantWorkspace: true,
      canUsePlatformGovernance: false,
      capabilityCodes: ["knowledge:read"],
    }).map((item) => item.id)).not.toContain("access");
  });

  it("角色导航预览只映射其候选权限对应的租户入口", () => {
    expect(previewNavigation(["access:manage", "assistant:query"]).map((item) => item.id))
      .toEqual(["access", "query"]);
  });
});
