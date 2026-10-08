package com.aiplatform.foundation.assistantdelivery.domain.assistant.repository;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/**
 * 助手身份、版本和幂等事实持久化端口。
 */
public interface AssistantRepository {

    AssistantCreationOutcome create(AssistantCreation creation, int tenantLimit);

    Optional<Assistant> findAssistant(TenantId tenantId, AssistantId assistantId);
    /** 在调用方事务内锁定助手，和版本发布共用同一行锁。 */
    Optional<Assistant> lockAssistant(TenantId tenantId, AssistantId assistantId);

    AssistantPage findPage(
            TenantId tenantId,
            long pageNumber,
            long pageSize);

    PublishedAssistantPage findPublishedPage(
            TenantId tenantId,
            long pageNumber,
            long pageSize, PublishedAssistantFilter filter);

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
