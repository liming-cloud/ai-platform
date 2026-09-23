package com.aiplatform.foundation.assistantdelivery.application.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.ListQueryAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantPermissionDeniedException;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantCreation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantPage;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionCreation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionPublication;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionPublicationOutcome;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantPage;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantSummary;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryAssistantCatalogServiceTest {

    private static final TenantId TENANT =
            new TenantId(100000000000000001L);
    private static final long ASSISTANT = 410000000000000001L;
    private static final long VERSION = 420000000000000001L;
    private static final Instant PUBLISHED_AT =
            Instant.parse("2026-07-27T06:00:00Z");

    @Test
    void 只向具备查询和知识读取权限的当前租户返回目录() {
        var repository = new RecordingRepository();
        var service = new DefaultQueryAssistantCatalogService(repository);

        var page = service.list(new ListQueryAssistantsQuery(
                context(Set.of(
                        new PermissionCode("assistant:query"),
                        new PermissionCode("knowledge:read"))),
                new PageQuery(1, 20)));

        assertEquals(TENANT, repository.tenantId);
        assertEquals(Long.toString(ASSISTANT), page.items().getFirst().assistantId());
        assertEquals(Long.toString(VERSION), page.items().getFirst().assistantVersionId());
        assertEquals("门店导购助手", page.items().getFirst().displayName());
        assertEquals(1L, page.totalElements());
        assertEquals(1L, page.totalPages());
        assertEquals(false, page.hasNext());
    }

    @Test
    void 缺少任一权限时不得读取目录() {
        var service = new DefaultQueryAssistantCatalogService(
                new RecordingRepository());

        assertThrows(
                AssistantPermissionDeniedException.class,
                () -> service.list(new ListQueryAssistantsQuery(
                        context(Set.of(new PermissionCode("assistant:query"))),
                        new PageQuery(1, 20))));
    }

    private static TrustedRequestContext context(Set<PermissionCode> permissions) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(7200000000000000001L),
                new MembershipId(7300000000000000001L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                permissions,
                "trace-query-assistant-catalog");
    }

    private static final class RecordingRepository implements AssistantRepository {

        private TenantId tenantId;

        @Override
        public PublishedAssistantPage findPublishedPage(
                TenantId value,
                long pageNumber,
                long pageSize) {
            tenantId = value;
            return new PublishedAssistantPage(
                    List.of(new PublishedAssistantSummary(
                            ASSISTANT,
                            "门店导购助手",
                            VERSION,
                            2,
                            PUBLISHED_AT)),
                    1L);
        }

        @Override
        public AssistantCreationOutcome create(
                AssistantCreation creation,
                int tenantLimit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Assistant> findAssistant(
                TenantId tenantId,
                AssistantId assistantId) {
            return Optional.empty();
        }

        @Override
        public AssistantPage findPage(
                TenantId tenantId,
                long pageNumber,
                long pageSize) {
            return new AssistantPage(List.of(), 0L);
        }

        @Override
        public AssistantVersionCreationOutcome createVersion(
                AssistantVersionCreation creation,
                int perAssistantLimit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AssistantVersion> findVersion(
                TenantId tenantId,
                AssistantVersionId assistantVersionId) {
            return Optional.empty();
        }

        @Override
        public Optional<AssistantVersionPublicationOutcome> replayPublication(
                TenantId tenantId,
                AssistantVersionId assistantVersionId,
                String idempotencyKey,
                String requestFingerprint) {
            return Optional.empty();
        }

        @Override
        public AssistantVersionPublicationOutcome publishVersion(
                AssistantVersionPublication publication) {
            throw new UnsupportedOperationException();
        }
    }
}
