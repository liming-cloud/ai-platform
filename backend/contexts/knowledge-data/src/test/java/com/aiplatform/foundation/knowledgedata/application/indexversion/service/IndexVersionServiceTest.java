package com.aiplatform.foundation.knowledgedata.application.indexversion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.indexversion.command.CreateIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.application.indexversion.exception.IndexEmbeddingDeploymentUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionBuildRequest;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionCreationOutcome;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionRepository;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionPage;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedEmbeddingDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.PublishedEmbeddingDeploymentSnapshot;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IndexVersionServiceTest {

    private static final TenantId TENANT_ID =
            new TenantId(100000000000000001L);
    private static final long SPACE_ID = 210000000000000001L;
    private static final long INDEX_ID = 230000000000000001L;
    private static final MembershipId MEMBER_ID =
            new MembershipId(1843651857942052866L);
    private static final EmbeddingConfiguration EMBEDDING =
            new EmbeddingConfiguration(
                    320000000000000001L,
                    1,
                    128);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 有写权限时从可信租户创建固定构建请求() {
        var repository = new RecordingRepository();
        var service = new IndexVersionService(
                repository,
                publishedEmbedding(),
                CLOCK,
                () -> INDEX_ID);

        var result = service.create(new CreateIndexVersionCommand(
                context("knowledge:write"),
                Long.toString(SPACE_ID),
                Long.toString(EMBEDDING.deploymentId()),
                "index-request-0001"));

        assertEquals(Long.toString(INDEX_ID), result.indexVersion().id());
        assertEquals(TENANT_ID, repository.request.tenantId());
        assertEquals(new KnowledgeSpaceId(SPACE_ID), repository.request.knowledgeSpaceId());
        assertEquals(EMBEDDING, repository.request.embedding());
        assertEquals("paragraph-window-v1", repository.request.chunkPolicy());
        assertEquals(MEMBER_ID.value(), repository.request.createdBy().value());
        assertTrue(repository.request.requestFingerprint().matches("[0-9a-f]{64}"));
    }

    @Test
    void 缺少写权限时不访问仓储() {
        var repository = new RecordingRepository();
        var service = new IndexVersionService(
                repository,
                publishedEmbedding(),
                CLOCK,
                () -> INDEX_ID);

        assertThrows(
                KnowledgePermissionDeniedException.class,
                () -> service.create(new CreateIndexVersionCommand(
                        context("knowledge:read"),
                        Long.toString(SPACE_ID),
                        Long.toString(EMBEDDING.deploymentId()),
                        "index-request-0001")));
        assertTrue(repository.request == null);
    }

    @Test
    void 未发布或非嵌入部署不能创建索引() {
        var repository = new RecordingRepository();
        var service = new IndexVersionService(
                repository,
                (tenantId, deploymentId) -> Optional.empty(),
                CLOCK,
                () -> INDEX_ID);

        assertThrows(
                IndexEmbeddingDeploymentUnavailableException.class,
                () -> service.create(new CreateIndexVersionCommand(
                        context("knowledge:write"),
                        Long.toString(SPACE_ID),
                        Long.toString(EMBEDDING.deploymentId()),
                        "index-request-0001")));
        assertTrue(repository.request == null);
    }

    private static PublishedEmbeddingDeploymentLookup publishedEmbedding() {
        return (tenantId, deploymentId) -> Optional.of(
                new PublishedEmbeddingDeploymentSnapshot(
                        deploymentId,
                        EMBEDDING.deploymentRevision(),
                        EMBEDDING.dimension()));
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT_ID,
                new SubjectId(6765477786913079298L),
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
        public IndexVersionPage findPage(
                TenantId tenantId,
                KnowledgeSpaceId knowledgeSpaceId,
                long pageNumber,
                long pageSize) {
            return new IndexVersionPage(List.of(), pageNumber, pageSize, 0);
        }

        @Override
        public Optional<IndexVersion> findActive(
                TenantId tenantId,
                KnowledgeSpaceId knowledgeSpaceId) {
            return Optional.empty();
        }

        @Override
        public IndexVersionCreationOutcome activate(
                TenantId tenantId,
                KnowledgeSpaceId knowledgeSpaceId,
                com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersionId id,
                long expectedVersion,
                String idempotencyKey,
                String requestFingerprint,
                Instant activatedAt) {
            throw new UnsupportedOperationException();
        }
    }
}
