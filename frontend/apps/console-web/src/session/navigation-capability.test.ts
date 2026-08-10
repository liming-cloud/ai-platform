import { describe, expect, it } from "vitest";
import { visibleNavigation } from "./navigation-capability";

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
});
