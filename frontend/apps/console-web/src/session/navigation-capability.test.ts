import { describe, expect, it } from "vitest";
import {
  defaultNavigationTarget,
  previewNavigation,
  visibleNavigation,
  authorizedNavigationTargets,
} from "./navigation-capability";

describe("visibleNavigation", () => {
  it("租户管理员只承接已交付入口的原型分组名称，隐藏入口不撤销直达路由能力", () => {
    const capability = { canUseTenantWorkspace: true, canUsePlatformGovernance: false,
      capabilityCodes: ["access:manage", "knowledge:write", "model:read", "usage:read", "assistant:query"],
      tenantAdministrator: true };
    const navigation = visibleNavigation(capability);
    expect(navigation.map(group => ({ label: group.label, items: group.children?.map(item => item.label) }))).toEqual([
      { label: "租户工作区", items: ["成员管理"] },
      { label: "内容", items: ["知识库"] },
      { label: "治理", items: ["访问治理", "模型治理"] },
      { label: "交付", items: ["评测治理"] },
    ]);
    expect(defaultNavigationTarget(navigation)).toBe("access");
    expect(authorizedNavigationTargets(capability)).toEqual(["operations", "access", "data", "access-policies", "models", "governance", "query"]);
  });

  it("已交付的平台入口承接原型分组与名称，不生成未交付的运行占位入口", () => {
    const navigation = visibleNavigation({ canUseTenantWorkspace: false, canUsePlatformGovernance: true,
      capabilityCodes: ["platform-operations:read", "tenant-governance:write", "platform:accounts:manage", "model-platform:write"] });
    expect(navigation.map(group => ({ label: group.label, items: group.children?.map(item => item.label) }))).toEqual([
      { label: "平台治理", items: ["平台治理概览", "租户管理", "平台账户"] },
      { label: "治理", items: ["模型治理"] },
    ]);
  });
  it("租户治理入口使用已验收原型名称，路由与授权条件不变", () => {
    const navigation = visibleNavigation({ canUseTenantWorkspace: false, canUsePlatformGovernance: true, capabilityCodes: ["tenant-governance:write"] });
    expect(navigation[0]?.children).toEqual([{ id: "platform", label: "租户管理", icon: "platform", requiredCapability: "tenant-governance:write", workspace: "PLATFORM_TENANT_GOVERNANCE" }]);
  });
  it("只显示当前租户会话已具备实际能力的入口", () => {
    expect(visibleNavigation({
      canUseTenantWorkspace: true,
      canUsePlatformGovernance: false,
      capabilityCodes: ["knowledge:read", "assistant:query"],
    }).map((item) => item.id)).toEqual(["query"]);
  });

  it("只有租户知识管理能力才能看到知识库入口", () => {
    const tenantNavigation = visibleNavigation({
      canUseTenantWorkspace: true,
      canUsePlatformGovernance: false,
      capabilityCodes: ["knowledge:write"],
    });
    const platformNavigation = visibleNavigation({
      canUseTenantWorkspace: false,
      canUsePlatformGovernance: true,
      capabilityCodes: ["knowledge:write"],
    });

    expect(tenantNavigation.map((item) => ({
      id: item.id,
      label: item.label,
    }))).toEqual([{ id: "data", label: "知识库" }]);
    expect(platformNavigation).toEqual([]);
  });

  it("平台运营主体没有治理写权限时不得看到平台租户入口", () => {
    expect(visibleNavigation({
      canUseTenantWorkspace: false,
      canUsePlatformGovernance: false,
      capabilityCodes: ["platform-session:access"],
    })).toEqual([]);
  });

  it("只有平台会话而没有实际治理能力时不显示空占位任务", () => {
    const navigation = visibleNavigation({
      canUseTenantWorkspace: false,
      canUsePlatformGovernance: true,
      capabilityCodes: ["platform-session:access"],
    });

    expect(navigation).toEqual([]);
    expect(defaultNavigationTarget(navigation)).toBeUndefined();
  });

  it("平台管理员首次进入应从可操作的租户治理任务开始", () => {
    const navigation = visibleNavigation({
      canUseTenantWorkspace: false,
      canUsePlatformGovernance: true,
      capabilityCodes: [
        "platform-session:access",
        "platform-operations:read",
        "tenant-governance:write",
        "platform:accounts:manage",
        "model-platform:write",
      ],
    });

    expect(defaultNavigationTarget(navigation)).toBe("platform-operations");
  });

  it("两级管理员都把运营总览作为各自工作区首入口", () => {
    const tenantNavigation = visibleNavigation({
      canUseTenantWorkspace: true,
      canUsePlatformGovernance: false,
      capabilityCodes: ["access:manage", "knowledge:write"],
    });
    const platformNavigation = visibleNavigation({
      canUseTenantWorkspace: false,
      canUsePlatformGovernance: true,
      capabilityCodes: ["platform-session:access", "platform-operations:read"],
    });

    expect(tenantNavigation.map((item) => item.id)).toEqual(["operations", "access", "data", "access-policies"]);
    expect(defaultNavigationTarget(tenantNavigation)).toBe("operations");
    expect(platformNavigation[0]?.children?.map((item) => item.id)).toEqual([
      "platform-operations",
    ]);
    expect(defaultNavigationTarget(platformNavigation)).toBe("platform-operations");
  });

  it("S2 部署入口复用平台模型能力，不生成额外授权标识", () => {
    const capability = { canUseTenantWorkspace: false, canUsePlatformGovernance: true, capabilityCodes: ["model-platform:write"] };
    const group = visibleNavigation(capability).find(item => item.id === "platform-model-governance");
    expect(group?.children?.map(item => item.label)).toEqual(["模型治理"]);
    expect(authorizedNavigationTargets(capability)).toEqual(["platform-model-supply"]);
  });

  it("平台模型写能力只生成原型治理组中的受权入口", () => {
    expect(visibleNavigation({
      canUseTenantWorkspace: false,
      canUsePlatformGovernance: true,
      capabilityCodes: ["platform-session:access", "model-platform:write"],
    }).map((item) => item.id)).toEqual(["platform-model-governance"]);
  });

  it("平台和租户复合职责不会突破六项顶级导航预算", () => {
    expect(visibleNavigation({
      canUseTenantWorkspace: true,
      canUsePlatformGovernance: true,
      capabilityCodes: [
        "access:manage",
        "knowledge:read",
        "knowledge:write",
        "model:read",
        "usage:read",
        "assistant:query",
        "platform-session:access",
        "platform-operations:read",
        "tenant-governance:write",
        "platform:accounts:manage",
        "model-platform:write",
      ],
    }).map((item) => item.id)).toEqual([
      "operations", "access", "data", "access-policies", "models", "governance", "query", "platform-governance", "platform-model-governance",
    ]);
  });

  it("访问管理员应看到成员与权限入口，普通成员不得看到", () => {
    expect(visibleNavigation({
      canUseTenantWorkspace: true,
      canUsePlatformGovernance: false,
      capabilityCodes: ["access:manage"],
    }).map((item) => item.id)).toEqual(["operations", "access", "access-policies"]);
    expect(visibleNavigation({
      canUseTenantWorkspace: true,
      canUsePlatformGovernance: false,
      capabilityCodes: ["knowledge:read"],
    }).map((item) => item.id)).not.toContain("access");
  });

  it("角色导航预览只映射其候选权限对应的租户入口", () => {
    expect(previewNavigation(["access:manage", "assistant:query"]).map((item) => item.id))
      .toEqual(["operations", "access", "access-policies", "query"]);
  });
});
