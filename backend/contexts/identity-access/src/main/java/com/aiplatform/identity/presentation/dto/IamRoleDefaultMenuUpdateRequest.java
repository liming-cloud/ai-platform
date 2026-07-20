package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * IAM 角色默认首页更新请求 DTO，默认菜单必须属于该角色有效菜单集合。
 */
public record IamRoleDefaultMenuUpdateRequest(
        @NotBlank String defaultMenuCode
) {
}
