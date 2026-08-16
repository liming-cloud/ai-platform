package com.aiplatform.foundation.evaluationoperations.application.operations.dto.overview;

import com.aiplatform.foundation.evaluationoperations.application.operations.dto.QueryOperationsView;

/** 平台按租户分页展示的安全摘要，不含成员或业务内容。 */
public record PlatformTenantOperationsSummaryView(
        String tenantReference,
        String tenantName,
        String lifecycleStatus,
        QueryOperationsView queries,
        boolean attentionRequired) {
}
