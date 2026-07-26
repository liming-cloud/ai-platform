package com.aiplatform.foundation.knowledgedata.application.indexversion;

import com.aiplatform.foundation.knowledgedata.api.model.CreateIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.application.knowledge.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionBuildRequest;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionCreationOutcome;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.ParagraphWindowChunker;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
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
public final class DefaultIndexVersionService {

    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final IndexVersionRepository repository;
    private final EmbeddingConfiguration embedding;
    private final Clock clock;
    private final Supplier<UUID> identifiers;

    public DefaultIndexVersionService(
            IndexVersionRepository repository,
            EmbeddingConfiguration embedding,
            Clock clock,
            Supplier<UUID> identifiers) {
        this.repository = Objects.requireNonNull(repository, "索引版本仓储不能为空");
        this.embedding = Objects.requireNonNull(embedding, "嵌入配置不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
    }

    public IndexVersionCreationOutcome create(CreateIndexVersionCommand command) {
        Objects.requireNonNull(command, "创建索引版本命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new KnowledgePermissionDeniedException();
        }
        var context = command.context();
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
        return repository.create(request);
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

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
