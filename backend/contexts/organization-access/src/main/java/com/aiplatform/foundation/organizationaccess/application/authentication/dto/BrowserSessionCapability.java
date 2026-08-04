package com.aiplatform.foundation.organizationaccess.application.authentication.dto;

import java.util.Objects;
import java.util.Set;

/**
 * 浏览器可用工作区的最小只读投影，不包含任何身份、权限、成员或 token 事实。
 * 已验证但未获分配工作区的平台主体以两个空集合表示，不代表会话失效。
 */
public record BrowserSessionCapability(
        Set<WorkspaceCategory> workspaceCategories,
        Set<DisplayRole> displayRoles,
        long maximumSessionAgeSeconds) {

    public BrowserSessionCapability {
        workspaceCategories = Set.copyOf(Objects.requireNonNull(workspaceCategories, "工作区类别不能为空"));
        displayRoles = Set.copyOf(Objects.requireNonNull(displayRoles, "显示角色不能为空"));
        if (workspaceCategories.isEmpty() != displayRoles.isEmpty()) {
            throw new IllegalArgumentException("浏览器工作区与显示角色必须同时为空或同时存在");
        }
        if (maximumSessionAgeSeconds <= 0) {
            throw new IllegalArgumentException("最大会话年龄必须大于零");
        }
    }

    /**
     * 导航可显示的工作区类别，不表示接口授权。
     */
    public enum WorkspaceCategory {
        TENANT,
        PLATFORM_TENANT_GOVERNANCE
    }

    /**
     * 仅供界面说明范围的显示角色，不对应成员详情或权限集合。
     */
    public enum DisplayRole {
        TENANT_MEMBER,
        PLATFORM_TENANT_GOVERNOR
    }
}
