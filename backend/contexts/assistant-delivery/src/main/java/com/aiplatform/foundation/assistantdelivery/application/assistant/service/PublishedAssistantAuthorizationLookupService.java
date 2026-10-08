package com.aiplatform.foundation.assistantdelivery.application.assistant.service;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantAuthorizationLookup;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionStatus;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantFilter;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;
import java.util.Set;

/** 发布拥有者在自己的仓储内核对当前版本，锁不会越过上下文公开门面。 */
public final class PublishedAssistantAuthorizationLookupService implements PublishedAssistantAuthorizationLookup {
    private final AssistantRepository repository;
    public PublishedAssistantAuthorizationLookupService(AssistantRepository repository) { this.repository = repository; }
    @Override public Optional<Fact> find(TenantId tenant, long id) { return current(tenant, id); }
    @Override public Optional<Fact> lock(TenantId tenant, long id) {
        if (repository.lockAssistant(tenant, new AssistantId(id)).isEmpty()) return Optional.empty();
        return current(tenant, id);
    }
    private Optional<Fact> current(TenantId tenant, long id) {
        if (id < 1) throw new IllegalArgumentException("助手引用必须为正整数");
        var rows = repository.findPublishedPage(tenant, 1, 1, new PublishedAssistantFilter("", Set.of(id), false));
        if (rows.items().isEmpty()) return Optional.empty();
        var row = rows.items().getFirst();
        return repository.findVersion(tenant, new AssistantVersionId(row.assistantVersionId()))
                .filter(version -> version.status() == AssistantVersionStatus.PUBLISHED && version.assistantId().value() == id)
                .map(version -> new Fact(id, row.displayName(), version.id().value(), version.number(), version.knowledgeSpaceId()));
    }
}
