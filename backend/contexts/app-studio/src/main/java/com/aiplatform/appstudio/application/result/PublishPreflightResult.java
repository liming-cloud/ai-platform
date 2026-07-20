package com.aiplatform.appstudio.application.result;

import com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult;
import com.aiplatform.appstudio.domain.publish.PublishPreflightIssue;
import java.time.Instant;
import java.util.List;

public record PublishPreflightResult(
        String applicationId,
        String publishRequestId,
        String candidateFingerprint,
        boolean passed,
        List<PublishPreflightIssue> blockers,
        List<PublishPreflightIssue> warnings,
        String traceId,
        Instant checkedAt
) {
    public static PublishPreflightResult from(AppPublishPreflightResult result) {
        return new PublishPreflightResult(
                result.applicationId(),
                result.publishRequestId(),
                result.candidateFingerprint(),
                result.passed(),
                result.blockers(),
                result.warnings(),
                result.traceId(),
                result.checkedAt()
        );
    }
}
