package com.aiplatform.identity.application.result;

import com.aiplatform.identity.domain.delegation.DelegatedTenantAction;

/**
 * 平台代管租户动作结果。
 */
public record DelegatedTenantActionResult(
        String delegationActionId,
        String status,
        String auditEventId
) {
    public static DelegatedTenantActionResult from(DelegatedTenantAction action) {
        if (action.actionId() == null) {
            throw new IllegalArgumentException("已完成代管动作必须包含动作 ID");
        }
        return new DelegatedTenantActionResult(action.actionId(), action.status(), action.auditEventId());
    }
}
