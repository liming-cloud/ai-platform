package com.aiplatform.foundation.knowledgedata.application.indexversion;

import com.aiplatform.foundation.knowledgedata.api.IndexVersionUseCase;
import com.aiplatform.foundation.knowledgedata.api.model.CreateIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.api.model.CreateIndexVersionResult;
import com.aiplatform.foundation.knowledgedata.api.model.IndexVersionPage;
import com.aiplatform.foundation.knowledgedata.api.model.IndexVersionView;
import com.aiplatform.foundation.knowledgedata.api.model.ListIndexVersionsQuery;
import com.aiplatform.foundation.knowledgedata.api.model.PublishIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.api.model.PublishIndexVersionResult;
import com.aiplatform.foundation.knowledgedata.application.knowledge.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionBuildRequest;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.ParagraphWindowChunker;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.modelruntime.api.catalog.PublishedEmbeddingDeploymentLookup;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 在可信租户边界内创建固定策略的索引版本。
 */
public final class DefaultIndexVersionService implements IndexVersionUseCase {

    private static final PermissionCode READ = new PermissionCode("knowledge:read");
    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final IndexVersionRepository repository;
    private final PublishedEmbeddingDeploymentLookup embeddingDeployments;
    private final Clock clock;
    private final Supplier<UUID> identifiers;

    public DefaultIndexVersionService(
            IndexVersionRepository repository,
            PublishedEmbeddingDeploymentLookup embeddingDeployments,
            Clock clock,
            Supplier<UUID> identifiers) {
        this.repository = Objects.requireNonNull(repository, "索引版本仓储不能为空");
        this.embeddingDeployments = Objects.requireNonNull(
                embeddingDeployments,
                "已发布嵌入部署查询不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
    }

    @Override
    public CreateIndexVersionResult create(CreateIndexVersionCommand command) {
        Objects.requireNonNull(command, "创建索引版本命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new KnowledgePermissionDeniedException();
        }
        var context = command.context();
        var deployment = embeddingDeployments
                .find(context.tenantId(), command.embeddingDeploymentId())
                .orElseThrow(IndexEmbeddingDeploymentUnavailableException::new);
        var embedding = new EmbeddingConfiguration(
                deployment.deploymentId(),
                deployment.deploymentRevision(),
                deployment.dimension());
        var request = new IndexVersionBuildRequest(
                context.tenantId(),
                new KnowledgeSpaceId(command.knowledgeSpaceId()),
                new IndexVersionId(identifiers.get()),
                embedding,
                ParagraphWindowChunker.POLICY,
                context.membershipId(),
                clock.instant(),
                command.idempotencyKey(),
                fingerprint(
                        command.knowledgeSpaceId(),
                        embedding,
                        ParagraphWindowChunker.POLICY));
        var outcome = repository.create(request);
        return new CreateIndexVersionResult(
                toView(outcome.indexVersion()),
                outcome.replayed());
    }

    @Override
    public IndexVersionPage list(ListIndexVersionsQuery query) {
        Objects.requireNonNull(query, "索引版本列表查询不能为空");
        requirePermission(query.context(), READ);
        var slice = repository.findPage(
                query.context().tenantId(),
                new KnowledgeSpaceId(query.knowledgeSpaceId()),
                query.cursor(),
                query.limit());
        return new IndexVersionPage(
                slice.items().stream()
                        .map(DefaultIndexVersionService::toView)
                        .toList(),
                slice.nextCursor());
    }

    @Override
    public PublishIndexVersionResult publish(PublishIndexVersionCommand command) {
        Objects.requireNonNull(command, "发布索引版本命令不能为空");
        requirePermission(command.context(), WRITE);
        var outcome = repository.activate(
                command.context().tenantId(),
                new KnowledgeSpaceId(command.knowledgeSpaceId()),
                new IndexVersionId(command.indexVersionId()),
                command.expectedVersion(),
                command.idempotencyKey(),
                fingerprint(
                        command.knowledgeSpaceId(),
                        command.indexVersionId(),
                        command.expectedVersion()),
                clock.instant());
        return new PublishIndexVersionResult(
                toView(outcome.indexVersion()),
                outcome.replayed());
    }

    private static String fingerprint(
            UUID knowledgeSpaceId,
            EmbeddingConfiguration embedding,
            String chunkPolicy) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, knowledgeSpaceId.toString());
            update(digest, chunkPolicy);
            update(digest, embedding.deploymentId().toString());
            update(digest, Long.toString(embedding.deploymentRevision()));
            update(digest, Integer.toString(embedding.dimension()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static String fingerprint(
            UUID knowledgeSpaceId,
            UUID indexVersionId,
            long expectedVersion) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, knowledgeSpaceId.toString());
            update(digest, indexVersionId.toString());
            update(digest, Long.toString(expectedVersion));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void requirePermission(
            TrustedRequestContext context,
            PermissionCode permission) {
        if (!context.hasPermission(permission)) {
            throw new KnowledgePermissionDeniedException();
        }
    }

    private static IndexVersionView toView(IndexVersion version) {
        var embedding = version.embedding();
        return new IndexVersionView(
                version.id().value(),
                version.knowledgeSpaceId().value(),
                version.sourceSnapshotDigest(),
                version.chunkPolicy(),
                embedding.deploymentId(),
                embedding.deploymentRevision(),
                embedding.dimension(),
                version.status().name(),
                version.version(),
                version.buildDigest(),
                version.chunkCount(),
                version.failureCode(),
                version.createdAt(),
                version.readyAt(),
                version.activatedAt(),
                version.supersededAt());
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
