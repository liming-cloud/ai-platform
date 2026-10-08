package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto;

/** 当前租户管理员外壳的最小只读投影，不包含租户或人员标识。 */
public record TenantWorkspaceContextView(String displayName) { }
