package com.aiplatform.foundation.knowledgedata.application.indexversion.service;

import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexVector;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexVectorBatch;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildCompletionPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildLease;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildQueuePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.ParagraphWindowChunker;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.TextChunk;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationUsageContext;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 认领一个构建作业，在事务外读取内容和调用嵌入模型，再原子提交产物。
 */
public final class IndexBuildWorker {

    private static final Logger log = LoggerFactory.getLogger(IndexBuildWorker.class);
    private static final String CONTENT_INTEGRITY_FAILED =
            "CONTENT_INTEGRITY_FAILED";
    private static final String EMBEDDING_RESULT_INVALID =
            "EMBEDDING_RESULT_INVALID";
    private static final String INDEX_BUILD_FAILED = "INDEX_BUILD_FAILED";

    private final IndexBuildQueuePort queue;
    private final IndexBuildCompletionPort completion;
    private final TextContentObjectStoragePort objectStorage;
    private final ModelRuntimeResolver models;
    private final UsableModelConfigurationLookup governedConfigurations;
    private final GovernedEmbeddingRuntime governedModels;
    private final ParagraphWindowChunker chunker;
    private final Clock clock;
    private final Duration leaseDuration;

    public IndexBuildWorker(
            IndexBuildQueuePort queue,
            IndexBuildCompletionPort completion,
            TextContentObjectStoragePort objectStorage,
            ModelRuntimeResolver models,
            ParagraphWindowChunker chunker,
            Clock clock,
            Duration leaseDuration) {
        this.queue = Objects.requireNonNull(queue, "索引构建队列不能为空");
        this.completion = Objects.requireNonNull(completion, "构建完成端口不能为空");
        this.objectStorage = Objects.requireNonNull(objectStorage, "对象存储不能为空");
        this.models = Objects.requireNonNull(models, "模型运行时解析器不能为空");
        governedConfigurations = null;
        governedModels = null;
        this.chunker = Objects.requireNonNull(chunker, "分块器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.leaseDuration = requirePositive(leaseDuration);
    }

    /**
     * 新受控索引构建在每批调用前按原创建成员重新确认配置和目录快照。
     */
    public IndexBuildWorker(
            IndexBuildQueuePort queue,
            IndexBuildCompletionPort completion,
            TextContentObjectStoragePort objectStorage,
            ModelRuntimeResolver models,
            UsableModelConfigurationLookup governedConfigurations,
            GovernedEmbeddingRuntime governedModels,
            ParagraphWindowChunker chunker,
            Clock clock,
            Duration leaseDuration) {
        this.queue = Objects.requireNonNull(queue, "索引构建队列不能为空");
        this.completion = Objects.requireNonNull(completion, "构建完成端口不能为空");
        this.objectStorage = Objects.requireNonNull(objectStorage, "对象存储不能为空");
        this.models = Objects.requireNonNull(models, "模型运行时解析器不能为空");
        this.governedConfigurations = Objects.requireNonNull(governedConfigurations, "受控模型配置查询不能为空");
        this.governedModels = Objects.requireNonNull(governedModels, "受控嵌入运行时不能为空");
        this.chunker = Objects.requireNonNull(chunker, "分块器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.leaseDuration = requirePositive(leaseDuration);
    }

    /**
     * 最多处理一个作业；没有可认领任务时返回 {@code false}。
     */
    public boolean processNext() {
        var claimed = queue.claim(clock.instant(), leaseDuration);
        if (claimed.isEmpty()) {
            return false;
        }
        var lease = claimed.get();
        try {
            var batch = build(lease);
            completion.complete(
                    lease,
                    batch,
                    buildDigest(batch),
                    clock.instant());
        } catch (ContentIntegrityFailure exception) {
            queue.recordFailure(lease, CONTENT_INTEGRITY_FAILED, clock.instant());
        } catch (EmbeddingResultFailure | IllegalArgumentException exception) {
            queue.recordFailure(lease, EMBEDDING_RESULT_INVALID, clock.instant());
        } catch (RuntimeException exception) {
            log.warn("索引构建失败，索引版本={}，失败类型={}",
                    lease.indexVersion().id().value(), exception.getClass().getSimpleName());
            queue.recordFailure(lease, INDEX_BUILD_FAILED, clock.instant());
        }
        return true;
    }

    private IndexVectorBatch build(IndexBuildLease lease) {
        var indexVersion = lease.indexVersion();
        var chunks = new ArrayList<ChunkSource>();
        for (var source : lease.sources()) {
            var text = readVerified(source.content());
            for (var chunk : chunker.chunk(source.sourceIdentityDigest(), text)) {
                chunks.add(new ChunkSource(
                        source.dataSourceId().value(),
                        source.content().version(),
                        chunk));
            }
        }
        if (chunks.isEmpty()) {
            throw new ContentIntegrityFailure();
        }

        var embedding = indexVersion.embedding();
        var deployment = embeddingReference(embedding);
        var usage = new ModelConfigurationUsageContext(
                indexVersion.tenantId(),
                indexVersion.createdBy().value());
        var governedSnapshot = embedding.isGoverned()
                ? governedSnapshot(usage, embedding)
                : null;
        var embeddingModel = embedding.isLegacyDeployment()
                ? models.resolveEmbedding(indexVersion.tenantId(), embedding.deploymentId())
                : null;
        var vectors = new ArrayList<IndexVector>(chunks.size());
        for (var start = 0; start < chunks.size(); start += EmbeddingRequest.MAX_BATCH_SIZE) {
            var end = Math.min(start + EmbeddingRequest.MAX_BATCH_SIZE, chunks.size());
            var window = chunks.subList(start, end);
            var request = new EmbeddingRequest(
                    deployment,
                    window.stream().map(value -> value.chunk().text()).toList());
            var result = embedding.isLegacyDeployment()
                    ? embeddingModel.embed(request)
                    : governedModels.embed(usage, governedSnapshot, request);
            validateResult(deployment, window.size(), result.deployment(), result.vectors().size());
            for (var offset = 0; offset < window.size(); offset++) {
                var source = window.get(offset);
                var vector = result.vectors().get(offset);
                if (vector.index() != offset || vector.dimension() != embedding.dimension()) {
                    throw new EmbeddingResultFailure();
                }
                vectors.add(new IndexVector(
                        source.dataSourceId(),
                        source.contentVersion(),
                        source.chunk().id(),
                        source.chunk().ordinal(),
                        source.chunk().text(),
                        source.chunk().sha256(),
                        vector.values()));
            }
        }
        return new IndexVectorBatch(
                indexVersion.tenantId(),
                indexVersion.knowledgeSpaceId().value(),
                indexVersion.id().value(),
                embedding,
                vectors);
    }

    private EmbeddingDeploymentRef embeddingReference(EmbeddingConfiguration embedding) {
        if (embedding.isLegacyDeployment()) {
            return new EmbeddingDeploymentRef(
                    embedding.deploymentId(),
                    embedding.deploymentRevision(),
                    embedding.dimension());
        }
        return new EmbeddingDeploymentRef(
                embedding.configurationId(),
                embedding.configurationRevision(),
                embedding.dimension());
    }

    private com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationSnapshot governedSnapshot(
            ModelConfigurationUsageContext usage,
            EmbeddingConfiguration embedding) {
        if (governedConfigurations == null || governedModels == null) {
            throw new EmbeddingResultFailure();
        }
        return governedConfigurations.find(
                        usage,
                        new ModelConfigurationId(embedding.configurationId()),
                        ModelType.EMBEDDING)
                .filter(value -> value.configurationRevision() == embedding.configurationRevision()
                        && value.catalogEntryId().value() == embedding.catalogEntryId()
                        && value.catalogEntryRevision() == embedding.catalogEntryRevision()
                        && value.capabilities().embeddingDimension() == embedding.dimension())
                .orElseThrow(EmbeddingResultFailure::new);
    }

    private String readVerified(TextContentReference reference) {
        var metadata = objectStorage.metadata(reference);
        if (!metadata.objectKey().equals(reference.objectKey())
                || !metadata.sha256().equals(reference.sha256())
                || metadata.byteSize() != reference.byteSize()
                || !metadata.mediaType().equals(reference.mediaType())) {
            throw new ContentIntegrityFailure();
        }
        try (var input = objectStorage.read(reference)) {
            var bytes = input.readNBytes(Math.toIntExact(reference.byteSize()) + 1);
            if (bytes.length != reference.byteSize()
                    || !sha256(bytes).equals(reference.sha256())) {
                throw new ContentIntegrityFailure();
            }
            return decodeUtf8(bytes);
        } catch (IOException exception) {
            throw new ContentIntegrityFailure(exception);
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new ContentIntegrityFailure(exception);
        }
    }

    private static void validateResult(
            EmbeddingDeploymentRef expected,
            int expectedCount,
            EmbeddingDeploymentRef actual,
            int actualCount) {
        if (!expected.equals(actual) || expectedCount != actualCount) {
            throw new EmbeddingResultFailure();
        }
    }

    private static String buildDigest(IndexVectorBatch batch) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, Long.toString(batch.indexVersionId()));
            var embedding = batch.embedding();
            update(digest, embedding.isLegacyDeployment() ? "legacy-deployment" : "governed-configuration");
            update(digest, Long.toString(embedding.isLegacyDeployment()
                    ? embedding.deploymentId()
                    : embedding.configurationId()));
            update(digest, Long.toString(embedding.isLegacyDeployment()
                    ? embedding.deploymentRevision()
                    : embedding.configurationRevision()));
            for (var vector : batch.vectors()) {
                update(digest, vector.chunkId());
                for (var value : vector.embedding()) {
                    digest.update(ByteBuffer.allocate(Float.BYTES)
                            .putInt(Float.floatToIntBits(value))
                            .array());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static Duration requirePositive(Duration value) {
        Objects.requireNonNull(value, "构建租约时长不能为空");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("构建租约时长必须大于零");
        }
        return value;
    }

    private record ChunkSource(
            long dataSourceId,
            int contentVersion,
            TextChunk chunk) {
    }

    private static final class ContentIntegrityFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private ContentIntegrityFailure() {
            super("内容完整性校验失败");
        }

        private ContentIntegrityFailure(Throwable cause) {
            super("内容完整性校验失败", cause);
        }
    }

    private static final class EmbeddingResultFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private EmbeddingResultFailure() {
            super("嵌入结果不符合冻结配置");
        }
    }
}
