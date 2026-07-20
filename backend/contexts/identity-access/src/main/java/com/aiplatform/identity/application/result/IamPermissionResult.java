package com.aiplatform.identity.application.result;

/**
 * IAM 权限读取结果，供配置工作台展示角色权限矩阵中的权限事实。
 */
public record IamPermissionResult(
        String permissionId,
        String code,
        String name,
        String resourceType,
        String actionCode,
        String description,
        String status
) {
}
