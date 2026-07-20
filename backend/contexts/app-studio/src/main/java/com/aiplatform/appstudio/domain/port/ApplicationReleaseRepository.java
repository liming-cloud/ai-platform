package com.aiplatform.appstudio.domain.port;

import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.release.AiApplicationRelease;
import com.aiplatform.appstudio.domain.release.ApplicationReleaseOperation;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 应用发布版本的事务边界：版本递增、active pointer 和 outbox 必须原子提交。
 * 应用级串行化统一由 {@link AiApplicationRepository#acquireMutationLock(TenantId, String)} 提供。
 */
public interface ApplicationReleaseRepository {
    Optional<AiApplicationRelease> findActive(TenantId tenantId, String applicationId);

    Map<String, AiApplicationRelease> findActiveByApplicationIds(TenantId tenantId, List<String> applicationIds);

    Optional<AiApplicationRelease> findByRequestId(TenantId tenantId, String applicationId, String requestId);

    Optional<AiApplicationRelease> findById(TenantId tenantId, String applicationId, String releaseId);

    List<AiApplicationRelease> findAll(TenantId tenantId, String applicationId);

    MutationResult createAndActivate(MutationCommand command);

    record MutationCommand(
            TenantId tenantId,
            String applicationId,
            String requestId,
            ApplicationReleaseOperation operation,
            String sourceReleaseId,
            AiApplication snapshot,
            Set<String> allowedUserIds,
            String actorId,
            String reason,
            String traceId,
            String eventId,
            Instant occurredAt,
            Instant expectedApplicationUpdatedAt
    ) {
    }

    record MutationResult(AiApplicationRelease release, boolean created) {
    }
}
