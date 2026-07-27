package com.aiplatform.foundation.assistantdelivery.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.assistantdelivery.api.model.ListQueryAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantCreation;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantSlice;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionCreation;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionPublication;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionPublicationOutcome;
import com.aiplatform.foundation.assistantdelivery.application.port.out.PublishedAssistantSlice;
import com.aiplatform.foundation.assistantdelivery.application.port.out.PublishedAssistantSummary;
import com.aiplatform.foundation.assistantdelivery.domain.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersionId;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultQueryAssistantCatalogServiceTest {

    private static final TenantId TENANT =
            TenantId.parse("10000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID VERSION =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
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
                null,
                20));

        assertEquals(TENANT, repository.tenantId);
        assertEquals(ASSISTANT, page.items().getFirst().assistantId());
        assertEquals(VERSION, page.items().getFirst().assistantVersionId());
        assertEquals("门店导购助手", page.items().getFirst().displayName());
        assertEquals("next", page.nextCursor());
    }

    @Test
    void 缺少任一权限时不得读取目录() {
        var service = new DefaultQueryAssistantCatalogService(
                new RecordingRepository());

        assertThrows(
                AssistantPermissionDeniedException.class,
                () -> service.list(new ListQueryAssistantsQuery(
                        context(Set.of(new PermissionCode("assistant:query"))),
                        null,
                        20)));
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
        public PublishedAssistantSlice findPublishedPage(
                TenantId value,
                String cursor,
                int limit) {
            tenantId = value;
            return new PublishedAssistantSlice(
                    List.of(new PublishedAssistantSummary(
                            ASSISTANT,
                            "门店导购助手",
                            VERSION,
                            2,
                            PUBLISHED_AT)),
                    "next");
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
        public AssistantSlice findPage(TenantId tenantId, String cursor, int limit) {
            return new AssistantSlice(List.of(), null);
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
