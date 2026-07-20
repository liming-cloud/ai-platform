package com.aiplatform.approval.presentation.converter;

import com.aiplatform.approval.application.result.ApprovalDecisionResult;
import com.aiplatform.approval.application.result.ApprovalTaskResult;
import com.aiplatform.approval.presentation.dto.ApprovalDecisionResponse;
import com.aiplatform.approval.presentation.dto.ApprovalTaskResponse;
import org.springframework.stereotype.Component;

/**
 * 审批中心展示层转换器，隔离 Result 与 HTTP DTO。
 */
@Component
public class ApprovalPresentationConverter {
    public ApprovalTaskResponse toResponse(ApprovalTaskResult result) {
        return new ApprovalTaskResponse(
                result.taskId(),
                result.approvalRequestId(),
                result.approvalType(),
                result.sourceContext(),
                result.sourceResourceType(),
                result.sourceResourceId(),
                result.title(),
                result.riskLevel(),
                ApprovalTaskResponse.labelRisk(result.riskLevel()),
                result.requesterId(),
                result.approverId(),
                result.status(),
                ApprovalTaskResponse.labelStatus(result.status()),
                ApprovalTaskResponse.labelSource(result.sourceContext()),
                result.traceId(),
                result.evidenceSchema(),
                result.evidenceDigest(),
                result.targetDigest(),
                result.evidence()
        );
    }

    public ApprovalDecisionResponse toResponse(ApprovalDecisionResult result) {
        return new ApprovalDecisionResponse(
                result.taskId(),
                result.approvalRequestId(),
                result.status(),
                result.sourceContext(),
                result.sourceResourceType(),
                result.sourceResourceId(),
                result.evidenceSchema(),
                result.evidenceDigest(),
                result.targetDigest()
        );
    }
}
