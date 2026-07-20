package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.ApplicationCapabilitiesResult;

public record ApplicationCapabilitiesResponse(
        boolean canEditConfiguration,
        boolean canRunPublishPreflight,
        boolean canPublish,
        boolean canRollback,
        boolean canManageAccessPolicy,
        boolean canReadReleaseEvidence
) {
    public static ApplicationCapabilitiesResponse from(ApplicationCapabilitiesResult result) {
        return new ApplicationCapabilitiesResponse(
                result.canEditConfiguration(),
                result.canRunPublishPreflight(),
                result.canPublish(),
                result.canRollback(),
                result.canManageAccessPolicy(),
                result.canReadReleaseEvidence()
        );
    }
}
