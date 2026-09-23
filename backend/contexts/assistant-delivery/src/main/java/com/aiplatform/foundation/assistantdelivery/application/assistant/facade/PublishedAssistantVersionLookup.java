package com.aiplatform.foundation.assistantdelivery.application.assistant.facade;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionSnapshot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/**
 * 向可信查询上下文提供已发布助手版本的不可变快照。
 */
public interface PublishedAssistantVersionLookup {

    Optional<PublishedAssistantVersionSnapshot> find(
            TenantId tenantId,
            long assistantVersionId);
}
