package com.aiplatform.foundation.assistantdelivery.application.port.out;

import com.aiplatform.foundation.assistantdelivery.domain.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersionId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/**
 * 助手身份、版本和幂等事实持久化端口。
 */
public interface AssistantRepository {

    AssistantCreationOutcome create(AssistantCreation creation, int tenantLimit);

    Optional<Assistant> findAssistant(TenantId tenantId, AssistantId assistantId);

    AssistantSlice findPage(TenantId tenantId, String cursor, int limit);

    PublishedAssistantSlice findPublishedPage(
            TenantId tenantId,
            String cursor,
            int limit);

    AssistantVersionCreationOutcome createVersion(
            AssistantVersionCreation creation,
            int perAssistantLimit);

    Optional<AssistantVersion> findVersion(
            TenantId tenantId,
            AssistantVersionId assistantVersionId);

    Optional<AssistantVersionPublicationOutcome> replayPublication(
            TenantId tenantId,
            AssistantVersionId assistantVersionId,
            String idempotencyKey,
            String requestFingerprint);

    AssistantVersionPublicationOutcome publishVersion(
            AssistantVersionPublication publication);
}
