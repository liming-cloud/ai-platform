package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.domain.publish.PublishPreflightIssue;

public record PublishPreflightIssueResponse(
        String code,
        String message,
        String resourceType,
        String resourceId
) {
    public static PublishPreflightIssueResponse from(PublishPreflightIssue issue) {
        return new PublishPreflightIssueResponse(
                issue.code(),
                issue.message(),
                issue.resourceType(),
                issue.resourceId()
        );
    }
}
