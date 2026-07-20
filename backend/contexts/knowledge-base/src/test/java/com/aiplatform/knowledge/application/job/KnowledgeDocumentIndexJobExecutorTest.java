package com.aiplatform.knowledge.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.application.indexing.model.PreparedKnowledgeDocumentIndex;
import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIndexJobTarget;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobOwnership;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentChunkRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.embedding.KnowledgeDocumentEmbeddingPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeDocumentIndexJobExecutorTest {
    private static final Instant NOW = Instant.parse("2026-07-18T01:00:00Z");

    @Test
    void reauthorizesAndEmbedsOutsideFinalizationTransactionUsingFrozenTarget() {
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        ModelUsagePolicyPort policy = mock(ModelUsagePolicyPort.class);
        RbacAuthorizationQueryPort authorization = mock(RbacAuthorizationQueryPort.class);
        KnowledgeDocumentEmbeddingPort embeddings = mock(KnowledgeDocumentEmbeddingPort.class);
        KnowledgeDocumentJobTransactionService transactions = mock(KnowledgeDocumentJobTransactionService.class);
        KnowledgeDocument document = document();
        KnowledgeDocumentChunk chunk = chunk();
        ClaimedDocumentIngestJob claim = claim();
        KnowledgeModelRuntimeDescriptor runtime = runtime("17", "BAAI/bge-m3");
        when(documents.findById(document.tenantId(), document.knowledgeBaseId(), document.documentId()))
                .thenReturn(Optional.of(document));
        when(chunks.findByDocumentId(document.tenantId(), document.documentId())).thenReturn(List.of(chunk));
        when(authorization.findRoleCodes(document.tenantId(), "user", "1001")).thenReturn(Set.of("kb-admin"));
        when(authorization.findPermissionCodes(document.tenantId(), "user", "1001")).thenReturn(Set.of());
        when(policy.decide(any())).thenReturn(KnowledgeModelUsageDecision.allow(runtime, Map.of()));
        when(embeddings.embedChunk(document.tenantId(), runtime, chunk.content()))
                .thenReturn(new float[]{0.1F, 0.2F});
        KnowledgeDocumentIndexJobExecutor executor = new KnowledgeDocumentIndexJobExecutor(
                documents, chunks, policy, authorization, embeddings, transactions
        );

        executor.execute(claim);

        var order = inOrder(policy, embeddings, transactions);
        order.verify(policy).decide(any());
        order.verify(embeddings).embedChunk(document.tenantId(), runtime, chunk.content());
        ArgumentCaptor<PreparedKnowledgeDocumentIndex> prepared =
                ArgumentCaptor.forClass(PreparedKnowledgeDocumentIndex.class);
        order.verify(transactions).completeIndex(org.mockito.ArgumentMatchers.eq(claim), prepared.capture());
        assertThat(prepared.getValue().target()).isEqualTo(claim.indexTarget());
        assertThat(prepared.getValue().embeddings()).hasSize(1);
    }

    @Test
    void terminallyRejectsRuntimeThatDiffersFromPersistedTarget() {
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        ModelUsagePolicyPort policy = mock(ModelUsagePolicyPort.class);
        RbacAuthorizationQueryPort authorization = mock(RbacAuthorizationQueryPort.class);
        KnowledgeDocumentEmbeddingPort embeddings = mock(KnowledgeDocumentEmbeddingPort.class);
        KnowledgeDocumentJobTransactionService transactions = mock(KnowledgeDocumentJobTransactionService.class);
        KnowledgeDocument document = document();
        when(documents.findById(document.tenantId(), document.knowledgeBaseId(), document.documentId()))
                .thenReturn(Optional.of(document));
        when(chunks.findByDocumentId(document.tenantId(), document.documentId())).thenReturn(List.of(chunk()));
        when(authorization.findRoleCodes(document.tenantId(), "user", "1001")).thenReturn(Set.of("kb-admin"));
        when(authorization.findPermissionCodes(document.tenantId(), "user", "1001")).thenReturn(Set.of());
        when(policy.decide(any())).thenReturn(KnowledgeModelUsageDecision.allow(
                runtime("17", "different-model"), Map.of()
        ));
        KnowledgeDocumentIndexJobExecutor executor = new KnowledgeDocumentIndexJobExecutor(
                documents, chunks, policy, authorization, embeddings, transactions
        );

        assertThatThrownBy(() -> executor.execute(claim()))
                .isInstanceOfSatisfying(KnowledgeDocumentJobFailure.class, failure -> {
                    assertThat(failure.failureCode()).isEqualTo("AIP-KB-EMBEDDING-TARGET-MISMATCH");
                    assertThat(failure.retryable()).isFalse();
                });
        verify(embeddings, never()).embedChunk(any(), any(), any());
        verify(transactions, never()).completeIndex(any(), any());
    }

    private static ClaimedDocumentIngestJob claim() {
        DocumentIngestJob job = DocumentIngestJob.documentIndex(
                TenantId.of("2001"), "8201", "8001", "7001", "trace-index", NOW, "1001"
        ).markIndexing(NOW.plusSeconds(1));
        DocumentIndexJobTarget target = new DocumentIndexJobTarget(
                "9101", "17", "BAAI/bge-m3", "kb-7001-g-2", "user"
        );
        return new ClaimedDocumentIngestJob(
                job,
                1,
                new DocumentIngestJobOwnership(
                        job.tenantId(), job.jobId(), "worker-a", "lease-1", NOW.plusSeconds(300), 1
                ),
                target
        );
    }

    private static KnowledgeDocument document() {
        return new KnowledgeDocument(
                TenantId.of("2001"), "8001", "7001", "policy.md", "upload", "s3://bucket/policy.md",
                "hash", "text/markdown", 128, 1, KnowledgeDocumentStatus.CHUNKED,
                NOW, NOW, "1001"
        );
    }

    private static KnowledgeDocumentChunk chunk() {
        return new KnowledgeDocumentChunk(
                TenantId.of("2001"), "9001", "7001", "8001", 1, "policy", "chunk-hash", 2,
                null, 0, 6,
                new KnowledgeDocumentChunkMetadata(
                        "s3://bucket/policy.md", 1, "policy", null, null, "internal", "tenant",
                        List.of("tenant:2001"), false, NOW, null, "draft", "token:v1", "draft"
                ),
                "ACTIVE", NOW, NOW, "1001"
        );
    }

    private static KnowledgeModelRuntimeDescriptor runtime(String configId, String modelCode) {
        return new KnowledgeModelRuntimeDescriptor(
                configId,
                "3001",
                "provider",
                "openai-compatible",
                new KnowledgeModelRuntimeConnectionRef(
                        "https://example.invalid", "api_key", "secret-1", "sk-***"
                ),
                modelCode,
                7,
                "EMBEDDING",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Map.of(),
                "a".repeat(64),
                "policy-v1"
        );
    }
}
