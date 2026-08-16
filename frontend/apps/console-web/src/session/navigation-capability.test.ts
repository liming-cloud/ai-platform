import { describe, expect, it } from "vitest";
import {
  defaultNavigationTarget,
  previewNavigation,
  visibleNavigation,
} from "./navigation-capability";

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

  it("平台运营员通过单一平台治理入口访问获授子任务", () => {
    const navigation = visibleNavigation({
      canUseTenantWorkspace: false,
      canUsePlatformGovernance: true,
      capabilityCodes: ["platform-session:access"],
    });

    expect(navigation.map((item) => item.id)).toEqual(["platform-governance"]);
    expect(defaultNavigationTarget(navigation)).toBe("platform-workspace");
  });

  it("平台管理员首次进入应从可操作的租户治理任务开始", () => {
    const navigation = visibleNavigation({
      canUseTenantWorkspace: false,
      canUsePlatformGovernance: true,
      capabilityCodes: [
        "platform-session:access",
        "tenant-governance:write",
        "platform:accounts:manage",
        "model-platform:write",
      ],
    });

    expect(defaultNavigationTarget(navigation)).toBe("platform");
  });

  it("平台模型写能力作为平台治理入口的受权子任务显示", () => {
    expect(visibleNavigation({
      canUseTenantWorkspace: false,
      canUsePlatformGovernance: true,
      capabilityCodes: ["platform-session:access", "model-platform:write"],
    }).map((item) => item.id)).toEqual(["platform-governance"]);
  });

  it("平台和租户复合职责不会突破六项顶级导航预算", () => {
    expect(visibleNavigation({
      canUseTenantWorkspace: true,
      canUsePlatformGovernance: true,
      capabilityCodes: [
        "access:manage",
        "knowledge:read",
        "model:read",
        "usage:read",
        "assistant:query",
        "platform-session:access",
        "tenant-governance:write",
        "platform:accounts:manage",
        "model-platform:write",
      ],
    }).map((item) => item.id)).toEqual([
      "access", "data", "models", "governance", "query", "platform-governance",
    ]);
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
