package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * IAM 菜单创建请求 DTO，承载新增菜单事实和可选租户功能绑定。
 */
public record IamMenuCreateRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String scope,
        @NotBlank String path,
        @NotBlank String routeName,
        @NotBlank String componentKey,
        String icon,
        @NotBlank String requiredPermissionCode,
        @NotNull Integer sort,
        @NotBlank String status,
        List<String> featureCodes
) {
}
