package com.aiplatform.foundation.evaluationoperations.application.operations.dto.overview;

import com.aiplatform.foundation.evaluationoperations.application.operations.dto.MemberOperationsView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.OperationsNoticeView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.OperationsQuotaView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.QueryOperationsView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.ResourceStatusView;

import java.time.Instant;
import java.util.List;

/** 租户管理员可见且严格限定当前租户的运营总览。 */
public record TenantOperationsOverviewView(
        OperationsRange range,
        Instant periodFrom,
        Instant periodTo,
        Instant generatedAt,
        Instant staleAfter,
        MemberOperationsView members,
        ResourceStatusView knowledgeSpaces,
        ResourceStatusView dataSources,
        ResourceStatusView indexes,
        ResourceStatusView indexJobs,
        ResourceStatusView modelConnections,
        ResourceStatusView modelDeployments,
        ResourceStatusView modelConfigurations,
        long assistants,
        ResourceStatusView assistantVersions,
        ResourceStatusView assistantTestRuns,
        ResourceStatusView evaluationRuns,
        ResourceStatusView releaseDecisions,
        QueryOperationsView queries,
        OperationsQuotaView quota,
        List<OperationsNoticeView> notices) {

    public TenantOperationsOverviewView {
        notices = List.copyOf(notices);
    }
}
