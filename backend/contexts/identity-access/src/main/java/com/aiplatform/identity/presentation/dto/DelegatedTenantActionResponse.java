package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.DelegatedTenantActionResult;

/**
 * 平台管理员代管租户动作响应。
 */
public record DelegatedTenantActionResponse(
        String delegationActionId,
        String status,
        String auditEventId
) {
    public static DelegatedTenantActionResponse from(DelegatedTenantActionResult result) {
        return new DelegatedTenantActionResponse(
                result.delegationActionId(),
                result.status(),
                result.auditEventId()
        );
    }
}
