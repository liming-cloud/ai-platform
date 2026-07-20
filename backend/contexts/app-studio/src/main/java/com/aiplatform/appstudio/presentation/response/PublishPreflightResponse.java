package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.PublishPreflightResult;
import java.time.Instant;
import java.util.List;

public record PublishPreflightResponse(
        String applicationId,
        String publishRequestId,
        boolean passed,
        List<PublishPreflightIssueResponse> blockers,
        List<PublishPreflightIssueResponse> warnings,
        String traceId,
        Instant checkedAt
) {
    public static PublishPreflightResponse from(PublishPreflightResult result) {
        return new PublishPreflightResponse(
                result.applicationId(),
                result.publishRequestId(),
                result.passed(),
                result.blockers().stream().map(PublishPreflightIssueResponse::from).toList(),
                result.warnings().stream().map(PublishPreflightIssueResponse::from).toList(),
                result.traceId(),
                result.checkedAt()
        );
    }
}
