package com.aiplatform.foundation.knowledgedata.application.indexversion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexVectorBatch;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildCompletionPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildLease;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildQueuePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexSourceSnapshot;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexSourceKind;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectMetadata;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.ParagraphWindowChunker;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationUsageContext;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingVector;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IndexBuildWorkerTest {

    private static final TenantId TENANT_ID =
            new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE_ID =
            new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID =
            new DataSourceId(220000000000000001L);
    private static final EmbeddingConfiguration EMBEDDING =
            new EmbeddingConfiguration(
                    320000000000000001L,
                    1,
                    4);
    private static final EmbeddingConfiguration GOVERNED_EMBEDDING =
            EmbeddingConfiguration.governed(
                    320000000000000002L,
                    2,
                    330000000000000001L,
                    3,
                    4);
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void 认领后核验对象分块嵌入并提交完整批次() throws Exception {
        var content = "记录一\n位置：一层\n\n记录二\n位置：二层";
        var lease = lease(content);
        var queue = new RecordingQueue(lease);
        var completion = new RecordingCompletion();
        var worker = new IndexBuildWorker(
                queue,
                completion,
                new ContentStore(content),
                new FixedModelRuntimeResolver(new FourDimensionEmbedding()),
                new ParagraphWindowChunker(),
                CLOCK,
                Duration.ofMinutes(1));

        assertTrue(worker.processNext());

        assertNotNull(completion.batch);
        assertEquals(2, completion.batch.vectors().size());
        assertEquals(SOURCE_ID.value(), completion.batch.vectors().get(0).dataSourceId());
        assertEquals(0, completion.batch.vectors().get(0).ordinal());
        assertEquals(1, completion.batch.vectors().get(1).ordinal());
        assertTrue(completion.buildDigest.matches("[0-9a-f]{64}"));
        assertTrue(queue.failureCode == null);
    }

    @Test
    void 嵌入维度不匹配时登记稳定失败码且不提交产物() throws Exception {
        var content = "只有一条记录";
        var lease = lease(content);
        var queue = new RecordingQueue(lease);
        var completion = new RecordingCompletion();
        var worker = new IndexBuildWorker(
                queue,
                completion,
                new ContentStore(content),
                new FixedModelRuntimeResolver(request -> new EmbeddingBatch(
                                new EmbeddingDeploymentRef(
                                        request.deployment().deploymentId(),
                                        request.deployment().revision(),
                                        3),
                                List.of(new EmbeddingVector(
                                        0,
                                        new float[] {1, 0, 0})))),
                new ParagraphWindowChunker(),
                CLOCK,
                Duration.ofMinutes(1));

        assertTrue(worker.processNext());

        assertEquals("EMBEDDING_RESULT_INVALID", queue.failureCode);
        assertTrue(completion.batch == null);
    }

    @Test
    void 没有可认领任务时不执行外部依赖() {
        var worker = new IndexBuildWorker(
                new RecordingQueue(null),
                new RecordingCompletion(),
                new ContentStore("不会读取"),
                new FixedModelRuntimeResolver(new FourDimensionEmbedding()),
                new ParagraphWindowChunker(),
                CLOCK,
                Duration.ofMinutes(1));

        assertFalse(worker.processNext());
    }

    @Test
    void 受控配置索引按创建成员重新校验后才调用短生命周期运行时() throws Exception {
        var content = "只有一条记录";
        var lease = lease(content, GOVERNED_EMBEDDING);
        var completion = new RecordingCompletion();
        var runtime = new RecordingGovernedRuntime();
        var worker = new IndexBuildWorker(
                new RecordingQueue(lease),
                completion,
                new ContentStore(content),
                new FixedModelRuntimeResolver(new FourDimensionEmbedding()),
                governedLookup(),
                runtime,
                new ParagraphWindowChunker(),
                CLOCK,
                Duration.ofMinutes(1));

        assertTrue(worker.processNext());

        assertNotNull(completion.batch);
        assertEquals(GOVERNED_EMBEDDING, completion.batch.embedding());
        assertEquals(6765477786913079298L, runtime.context.membershipId());
        assertEquals(GOVERNED_EMBEDDING.configurationId(), runtime.snapshot.configurationId().value());
    }

    @Test
    void 受治理文件快照只读取冻结对象并保留产物质量事实() throws Exception {
        var content = "文件来源内容";
        var legacy = lease(content);
        var reference = legacy.sources().getFirst().content();
        var governed = new IndexSourceSnapshot(
                IndexSourceKind.GOVERNED_SYNC_ARTIFACT,
                SOURCE_ID,
                1,
                240000000000000001L,
                "WARN",
                "file-quality-v1",
                reference);
        var governedLease = new IndexBuildLease(
                legacy.leaseId(), legacy.indexVersion(), List.of(governed), legacy.attempt());
        var completion = new RecordingCompletion();
        var worker = new IndexBuildWorker(
                new RecordingQueue(governedLease),
                completion,
                new ContentStore(content),
                new FixedModelRuntimeResolver(new FourDimensionEmbedding()),
                new ParagraphWindowChunker(),
                CLOCK,
                Duration.ofMinutes(1));

        assertTrue(worker.processNext());

        assertNotNull(completion.batch);
        assertEquals(1, completion.batch.vectors().size());
        assertEquals(1, completion.batch.vectors().getFirst().contentVersion());
    }

    @Test
    void 相同内容的不同来源生成不同分块标识() throws Exception {
        var content = "不同来源保留各自引用";
        var legacy = lease(content);
        var reference = legacy.sources().getFirst().content();
        var secondSource = new IndexSourceSnapshot(
                IndexSourceKind.GOVERNED_SYNC_ARTIFACT,
                new DataSourceId(220000000000000002L),
                1,
                240000000000000002L,
                "PASS",
                "file-quality-v1",
                reference);
        var leaseWithDuplicateContent = new IndexBuildLease(
                legacy.leaseId(), legacy.indexVersion(),
                List.of(legacy.sources().getFirst(), secondSource), legacy.attempt());
        var queue = new RecordingQueue(leaseWithDuplicateContent);
        var completion = new RecordingCompletion();
        var worker = new IndexBuildWorker(
                queue,
                completion,
                new ContentStore(content),
                new FixedModelRuntimeResolver(new FourDimensionEmbedding()),
                new ParagraphWindowChunker(),
                CLOCK,
                Duration.ofMinutes(1));

        assertTrue(worker.processNext());

        assertNotNull(completion.batch);
        assertEquals(2, completion.batch.vectors().size());
        assertNotEquals(
                completion.batch.vectors().get(0).chunkId(),
                completion.batch.vectors().get(1).chunkId());
        assertTrue(completion.batch.vectors().stream()
                .map(value -> value.dataSourceId())
                .distinct()
                .count() == 2);
        assertTrue(queue.failureCode == null);
    }

    private static IndexBuildLease lease(String content) throws Exception {
        return lease(content, EMBEDDING);
    }

    private static IndexBuildLease lease(
            String content,
            EmbeddingConfiguration embedding) throws Exception {
        var bytes = content.getBytes(StandardCharsets.UTF_8);
        var digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
        var reference = new TextContentReference(
                1,
                "foundation-v1/test/content.txt",
                digest,
                bytes.length,
                TextContentReference.UTF8_TEXT,
                NOW);
        var index = IndexVersion.start(
                TENANT_ID,
                SPACE_ID,
                new IndexVersionId(230000000000000001L),
                digest,
                ParagraphWindowChunker.POLICY,
                embedding,
                new CreatorId(6765477786913079298L),
                NOW);
        return new IndexBuildLease(
                540000000000000001L,
                index,
                List.of(new IndexSourceSnapshot(SOURCE_ID, reference)),
                1);
    }

    private static UsableModelConfigurationLookup governedLookup() {
        return new UsableModelConfigurationLookup() {
            @Override
            public Optional<UsableModelConfigurationSnapshot> find(
                    com.aiplatform.foundation.security.identity.TrustedRequestContext context,
                    com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId configurationId,
                    ModelType requiredType) {
                return snapshot(configurationId);
            }

            @Override
            public Optional<UsableModelConfigurationSnapshot> find(
                    ModelConfigurationUsageContext context,
                    com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId configurationId,
                    ModelType requiredType) {
                return snapshot(configurationId);
            }

            private Optional<UsableModelConfigurationSnapshot> snapshot(
                    com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId configurationId) {
                return Optional.of(new UsableModelConfigurationSnapshot(
                        configurationId,
                        GOVERNED_EMBEDDING.configurationRevision(),
                        new ControlledModelCatalogEntryId(GOVERNED_EMBEDDING.catalogEntryId()),
                        GOVERNED_EMBEDDING.catalogEntryRevision(),
                        ModelType.EMBEDDING,
                        new ModelRuntimeCapabilities(8192, null, GOVERNED_EMBEDDING.dimension())));
            }
        };
    }

    private static final class RecordingQueue implements IndexBuildQueuePort {

        private IndexBuildLease lease;
        private String failureCode;

        private RecordingQueue(IndexBuildLease lease) {
            this.lease = lease;
        }

        @Override
        public Optional<IndexBuildLease> claim(Instant now, Duration leaseDuration) {
            var result = Optional.ofNullable(lease);
            lease = null;
            return result;
        }

        @Override
        public void recordFailure(
                IndexBuildLease value,
                String code,
                Instant failedAt) {
            failureCode = code;
        }
    }

    private static final class RecordingCompletion implements IndexBuildCompletionPort {

        private IndexVectorBatch batch;
        private String buildDigest;

        @Override
        public void complete(
                IndexBuildLease lease,
                IndexVectorBatch value,
                String digest,
                Instant completedAt) {
            batch = value;
            buildDigest = digest;
        }
    }

    private static final class ContentStore implements TextContentObjectStoragePort {

        private final byte[] content;

        private ContentStore(String value) {
            content = value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public TextContentReference put(TextContentStorageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TextContentObjectMetadata metadata(TextContentReference reference) {
            return new TextContentObjectMetadata(
                    reference.objectKey(),
                    reference.sha256(),
                    reference.byteSize(),
                    reference.mediaType());
        }

        @Override
        public InputStream read(TextContentReference reference) {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void delete(TextContentReference reference) {
        }
    }

    private static final class FourDimensionEmbedding implements EmbeddingModelPort {

        @Override
        public EmbeddingBatch embed(EmbeddingRequest request) {
            var vectors = new ArrayList<EmbeddingVector>();
            for (var index = 0; index < request.texts().size(); index++) {
                vectors.add(new EmbeddingVector(
                        index,
                        new float[] {index + 1, 1, 0, 0}));
            }
            return new EmbeddingBatch(request.deployment(), vectors);
        }
    }

    private static final class FixedModelRuntimeResolver
            implements ModelRuntimeResolver {

        private final EmbeddingModelPort embedding;

        private FixedModelRuntimeResolver(EmbeddingModelPort embedding) {
            this.embedding = embedding;
        }

        @Override
        public ChatModelPort resolveChat(TenantId tenantId, long deploymentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EmbeddingModelPort resolveEmbedding(
                TenantId tenantId,
                long deploymentId) {
            assertEquals(TENANT_ID, tenantId);
            assertEquals(EMBEDDING.deploymentId(), deploymentId);
            return embedding;
        }

        @Override
        public void evictProvider(TenantId tenantId, long providerConnectionId) {
        }

        @Override
        public void evictDeployment(TenantId tenantId, long deploymentId) {
        }
    }

    private static final class RecordingGovernedRuntime
            implements GovernedEmbeddingRuntime {

        private ModelConfigurationUsageContext context;
        private UsableModelConfigurationSnapshot snapshot;

        @Override
        public EmbeddingBatch embed(
                com.aiplatform.foundation.security.identity.TrustedRequestContext context,
                UsableModelConfigurationSnapshot expected,
                EmbeddingRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EmbeddingBatch embed(
                ModelConfigurationUsageContext context,
                UsableModelConfigurationSnapshot expected,
                EmbeddingRequest request) {
            this.context = context;
            snapshot = expected;
            return new FourDimensionEmbedding().embed(request);
        }
    }
}
