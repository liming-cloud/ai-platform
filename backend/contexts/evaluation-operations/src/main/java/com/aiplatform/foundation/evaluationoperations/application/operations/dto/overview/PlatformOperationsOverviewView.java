package com.aiplatform.foundation.evaluationoperations.application.operations.dto.overview;

import com.aiplatform.foundation.evaluationoperations.application.operations.dto.OperationsNoticeView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.QueryOperationsView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.ResourceStatusView;

import java.time.Instant;
import java.util.List;

/** 平台管理员可见的全平台脱敏运营总览。 */
public record PlatformOperationsOverviewView(
        OperationsRange range,
        Instant periodFrom,
        Instant periodTo,
        Instant generatedAt,
        Instant staleAfter,
        ResourceStatusView tenants,
        ResourceStatusView platformAdministrators,
        ResourceStatusView tenantAdministrators,
        ResourceStatusView tenantUsers,
        ResourceStatusView knowledgeSpaces,
        ResourceStatusView dataSources,
        ResourceStatusView indexes,
        ResourceStatusView indexJobs,
        ResourceStatusView platformModelConnections,
        ResourceStatusView platformModelDeployments,
        QueryOperationsView queries,
        long attentionTenantCount,
        List<OperationsNoticeView> notices) {

    public PlatformOperationsOverviewView {
        notices = List.copyOf(notices);
    }
}
