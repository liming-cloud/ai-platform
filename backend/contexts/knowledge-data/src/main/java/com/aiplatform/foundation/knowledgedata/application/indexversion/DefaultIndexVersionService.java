package com.aiplatform.foundation.knowledgedata.application.indexversion;

import com.aiplatform.foundation.knowledgedata.api.IndexVersionUseCase;
import com.aiplatform.foundation.knowledgedata.api.model.index.CreateIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.api.model.index.CreateIndexVersionResult;
import com.aiplatform.foundation.knowledgedata.api.model.index.IndexVersionPage;
import com.aiplatform.foundation.knowledgedata.api.model.index.IndexVersionView;
import com.aiplatform.foundation.knowledgedata.api.model.index.ListIndexVersionsQuery;
import com.aiplatform.foundation.knowledgedata.api.model.index.PublishIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.api.model.index.PublishIndexVersionResult;
import com.aiplatform.foundation.knowledgedata.application.knowledge.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexVersionBuildRequest;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.ParagraphWindowChunker;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.modelruntime.api.catalog.PublishedEmbeddingDeploymentLookup;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 在可信租户边界内创建固定策略的索引版本。
 */
public final class DefaultIndexVersionService implements IndexVersionUseCase {

    private static final PermissionCode READ = new PermissionCode("knowledge:read");
    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final IndexVersionRepository repository;
    private final PublishedEmbeddingDeploymentLookup embeddingDeployments;
    private final Clock clock;
    private final EntityIdGenerator identifiers;

    public DefaultIndexVersionService(
            IndexVersionRepository repository,
            PublishedEmbeddingDeploymentLookup embeddingDeployments,
            Clock clock,
            EntityIdGenerator identifiers) {
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
                .find(
                        context.tenantId(),
                        parseDeploymentId(command.embeddingDeploymentId()))
                .orElseThrow(IndexEmbeddingDeploymentUnavailableException::new);
        var embedding = new EmbeddingConfiguration(
                deployment.deploymentId(),
                deployment.deploymentRevision(),
                deployment.dimension());
        var request = new IndexVersionBuildRequest(
                context.tenantId(),
                KnowledgeSpaceId.parse(command.knowledgeSpaceId()),
                new IndexVersionId(identifiers.nextId()),
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
                KnowledgeSpaceId.parse(query.knowledgeSpaceId()),
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
                KnowledgeSpaceId.parse(command.knowledgeSpaceId()),
                IndexVersionId.parse(command.indexVersionId()),
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
            String knowledgeSpaceId,
            EmbeddingConfiguration embedding,
            String chunkPolicy) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, knowledgeSpaceId.toString());
            update(digest, chunkPolicy);
            update(digest, Long.toString(embedding.deploymentId()));
            update(digest, Long.toString(embedding.deploymentRevision()));
            update(digest, Integer.toString(embedding.dimension()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static String fingerprint(
            String knowledgeSpaceId,
            String indexVersionId,
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
                version.id().toString(),
                version.knowledgeSpaceId().toString(),
                version.sourceSnapshotDigest(),
                version.chunkPolicy(),
                Long.toString(embedding.deploymentId()),
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

    private static long parseDeploymentId(String value) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException("嵌入部署标识必须是正整数");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("嵌入部署标识格式不正确", exception);
        }
    }
}
