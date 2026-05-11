package org.fujay.ai.platform.tenant.application.dto;

import java.util.List;
import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile.MenuProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile.PermissionProfile;

public record RoleManagementProfile(
        List<RoleAccessProfile> roles, List<PermissionProfile> permissions, List<MenuProfile> menus) {
}
