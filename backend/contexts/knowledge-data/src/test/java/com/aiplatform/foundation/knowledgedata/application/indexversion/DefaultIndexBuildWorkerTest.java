package com.aiplatform.foundation.knowledgedata.application.indexversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexVectorBatch;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexBuildCompletionPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexBuildLease;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexBuildQueuePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexSourceSnapshot;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentObjectMetadata;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.ParagraphWindowChunker;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import com.aiplatform.foundation.modelruntime.api.invocation.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.api.invocation.ChatModelPort;
import com.aiplatform.foundation.modelruntime.api.invocation.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingRequest;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingVector;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
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

class DefaultIndexBuildWorkerTest {

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
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void 认领后核验对象分块嵌入并提交完整批次() throws Exception {
        var content = "记录一\n位置：一层\n\n记录二\n位置：二层";
        var lease = lease(content);
        var queue = new RecordingQueue(lease);
        var completion = new RecordingCompletion();
        var worker = new DefaultIndexBuildWorker(
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
        var worker = new DefaultIndexBuildWorker(
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
        var worker = new DefaultIndexBuildWorker(
                new RecordingQueue(null),
                new RecordingCompletion(),
                new ContentStore("不会读取"),
                new FixedModelRuntimeResolver(new FourDimensionEmbedding()),
                new ParagraphWindowChunker(),
                CLOCK,
                Duration.ofMinutes(1));

        assertFalse(worker.processNext());
    }

    private static IndexBuildLease lease(String content) throws Exception {
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
                EMBEDDING,
                new MembershipId(6765477786913079298L),
                NOW);
        return new IndexBuildLease(
                540000000000000001L,
                index,
                List.of(new IndexSourceSnapshot(SOURCE_ID, reference)),
                1);
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
}
