package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * IAM 角色菜单矩阵替换请求 DTO，完整表达该角色保存后的菜单集合。
 */
public record IamRoleMenusReplaceRequest(
        @NotNull List<String> menuIds
) {
}
