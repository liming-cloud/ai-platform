package com.aiplatform.foundation.knowledgedata.application.indexversion.service;

import com.aiplatform.foundation.knowledgedata.application.indexversion.command.CreateIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.application.indexversion.dto.CreateIndexVersionResult;
import com.aiplatform.foundation.knowledgedata.application.indexversion.dto.IndexVersionView;
import com.aiplatform.foundation.knowledgedata.application.indexversion.query.ListIndexVersionsQuery;
import com.aiplatform.foundation.knowledgedata.application.indexversion.command.PublishIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.application.indexversion.exception.IndexEmbeddingDeploymentUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.dto.PublishIndexVersionResult;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionBuildRequest;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.ParagraphWindowChunker;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
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
public final class IndexVersionService {

    private static final PermissionCode READ = new PermissionCode("knowledge:read");
    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final IndexVersionRepository repository;
    private final UsableModelConfigurationLookup embeddingConfigurations;
    private final Clock clock;
    private final EntityIdGenerator identifiers;

    public IndexVersionService(
            IndexVersionRepository repository,
            UsableModelConfigurationLookup embeddingConfigurations,
            Clock clock,
            EntityIdGenerator identifiers) {
        this.repository = Objects.requireNonNull(repository, "索引版本仓储不能为空");
        this.embeddingConfigurations = Objects.requireNonNull(
                embeddingConfigurations,
                "可用嵌入模型配置查询不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
    }

    public CreateIndexVersionResult create(CreateIndexVersionCommand command) {
        Objects.requireNonNull(command, "创建索引版本命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new KnowledgePermissionDeniedException();
        }
        var context = command.context();
        var configuration = embeddingConfigurations
                .find(
                        context,
                        parseConfigurationId(command.embeddingModelConfigurationId()),
                        ModelType.EMBEDDING)
                .orElseThrow(IndexEmbeddingDeploymentUnavailableException::new);
        var embedding = EmbeddingConfiguration.governed(
                configuration.configurationId().value(),
                configuration.configurationRevision(),
                configuration.catalogEntryId().value(),
                configuration.catalogEntryRevision(),
                configuration.capabilities().embeddingDimension());
        var request = new IndexVersionBuildRequest(
                context.tenantId(),
                KnowledgeSpaceId.parse(command.knowledgeSpaceId()),
                new IndexVersionId(identifiers.nextId()),
                embedding,
                ParagraphWindowChunker.POLICY,
                new CreatorId(context.membershipId().value()),
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

    public PageResult<IndexVersionView> list(ListIndexVersionsQuery query) {
        Objects.requireNonNull(query, "索引版本列表查询不能为空");
        requirePermission(query.context(), READ);
        var page = repository.findPage(
                query.context().tenantId(),
                KnowledgeSpaceId.parse(query.knowledgeSpaceId()),
                query.page().pageNumber(),
                query.page().pageSize());
        return PageResult.of(
                page.items().stream()
                        .map(IndexVersionService::toView)
                        .toList(),
                query.page(),
                page.totalElements());
    }

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
            if (embedding.isLegacyDeployment()) {
                update(digest, "legacy-deployment");
                update(digest, Long.toString(embedding.deploymentId()));
                update(digest, Long.toString(embedding.deploymentRevision()));
            } else {
                update(digest, "governed-configuration");
                update(digest, Long.toString(embedding.configurationId()));
                update(digest, Long.toString(embedding.configurationRevision()));
                update(digest, Long.toString(embedding.catalogEntryId()));
                update(digest, Long.toString(embedding.catalogEntryRevision()));
            }
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
                embedding.isGoverned() ? "GOVERNED_CONFIGURATION" : "LEGACY_DEPLOYMENT",
                embedding.isGoverned() ? Long.toString(embedding.configurationId()) : null,
                embedding.isGoverned() ? embedding.configurationRevision() : null,
                embedding.isGoverned() ? Long.toString(embedding.catalogEntryId()) : null,
                embedding.isGoverned() ? embedding.catalogEntryRevision() : null,
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

    private static ModelConfigurationId parseConfigurationId(String value) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException("嵌入模型配置标识必须是正整数");
            }
            return new ModelConfigurationId(parsed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("嵌入模型配置标识格式不正确", exception);
        }
    }
}
