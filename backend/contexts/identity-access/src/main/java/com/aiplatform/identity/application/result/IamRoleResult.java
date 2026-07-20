package com.aiplatform.identity.application.result;

/**
 * IAM 角色读取结果，供配置工作台展示租户内角色和默认首页事实。
 */
public record IamRoleResult(
        String roleId,
        String code,
        String name,
        String type,
        String defaultMenuCode,
        String status,
        String description
) {
}
