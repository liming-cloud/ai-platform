package com.aiplatform.foundation.knowledgedata.application.indexversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.api.model.CreateIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.application.knowledge.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionBuildRequest;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionCreationOutcome;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionSlice;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultIndexVersionServiceTest {

    private static final TenantId TENANT_ID =
            new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final UUID SPACE_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID INDEX_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final MembershipId MEMBER_ID =
            new MembershipId(UUID.fromString("40000000-0000-0000-0000-000000000001"));
    private static final EmbeddingConfiguration EMBEDDING =
            new EmbeddingConfiguration(
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    1,
                    128);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 有写权限时从可信租户创建固定构建请求() {
        var repository = new RecordingRepository();
        var service = new DefaultIndexVersionService(
                repository,
                EMBEDDING,
                CLOCK,
                () -> INDEX_ID);

        var result = service.create(new CreateIndexVersionCommand(
                context("knowledge:write"),
                SPACE_ID,
                "index-request-0001"));

        assertEquals(INDEX_ID, result.indexVersion().id().value());
        assertEquals(TENANT_ID, repository.request.tenantId());
        assertEquals(new KnowledgeSpaceId(SPACE_ID), repository.request.knowledgeSpaceId());
        assertEquals(EMBEDDING, repository.request.embedding());
        assertEquals("paragraph-window-v1", repository.request.chunkPolicy());
        assertEquals(MEMBER_ID, repository.request.createdBy());
        assertTrue(repository.request.requestFingerprint().matches("[0-9a-f]{64}"));
    }

    @Test
    void 缺少写权限时不访问仓储() {
        var repository = new RecordingRepository();
        var service = new DefaultIndexVersionService(
                repository,
                EMBEDDING,
                CLOCK,
                () -> INDEX_ID);

        assertThrows(
                KnowledgePermissionDeniedException.class,
                () -> service.create(new CreateIndexVersionCommand(
                        context("knowledge:read"),
                        SPACE_ID,
                        "index-request-0001")));
        assertTrue(repository.request == null);
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT_ID,
                new SubjectId(UUID.fromString("60000000-0000-0000-0000-000000000001")),
                MEMBER_ID,
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode(permission)),
                "trace-index-0001");
    }

    private static final class RecordingRepository implements IndexVersionRepository {

        private IndexVersionBuildRequest request;

        @Override
        public IndexVersionCreationOutcome create(IndexVersionBuildRequest value) {
            request = value;
            var version = IndexVersion.start(
                    value.tenantId(),
                    value.knowledgeSpaceId(),
                    value.indexVersionId(),
                    "a".repeat(64),
                    value.chunkPolicy(),
                    value.embedding(),
                    value.createdBy(),
                    value.createdAt());
            return new IndexVersionCreationOutcome(version, false);
        }

        @Override
        public IndexVersionSlice findPage(
                TenantId tenantId,
                KnowledgeSpaceId knowledgeSpaceId,
                String cursor,
                int limit) {
            return new IndexVersionSlice(List.of(), null);
        }

        @Override
        public IndexVersionCreationOutcome activate(
                TenantId tenantId,
                KnowledgeSpaceId knowledgeSpaceId,
                com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionId id,
                long expectedVersion,
                String idempotencyKey,
                String requestFingerprint,
                Instant activatedAt) {
            throw new UnsupportedOperationException();
        }
    }
}
