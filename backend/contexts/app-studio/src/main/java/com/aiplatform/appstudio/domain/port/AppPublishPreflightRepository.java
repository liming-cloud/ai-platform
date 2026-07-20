package com.aiplatform.appstudio.domain.port;

import com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Optional;

public interface AppPublishPreflightRepository {
    Optional<AppPublishPreflightResult> findByRequestId(
            TenantId tenantId,
            String applicationId,
            String publishRequestId
    );

    AppPublishPreflightResult save(AppPublishPreflightResult result);
}
