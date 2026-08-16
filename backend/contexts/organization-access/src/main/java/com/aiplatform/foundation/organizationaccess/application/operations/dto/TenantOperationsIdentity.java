package com.aiplatform.foundation.organizationaccess.application.operations.dto;

/** 平台运营分页使用的租户名称与生命周期事实，不包含成员或业务内容。 */
public record TenantOperationsIdentity(
        long tenantId,
        String displayName,
        String status) {
}
