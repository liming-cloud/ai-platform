package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * IAM 角色权限矩阵替换请求 DTO，完整表达该角色保存后的权限集合。
 */
public record IamRolePermissionsReplaceRequest(
        @NotNull List<String> permissionCodes,
        @NotBlank @Size(max = 300) String reason
) {
}
