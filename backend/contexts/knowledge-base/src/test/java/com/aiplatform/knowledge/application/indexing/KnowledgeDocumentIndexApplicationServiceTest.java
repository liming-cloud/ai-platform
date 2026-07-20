package com.aiplatform.knowledge.application.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.application.command.IndexKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.indexing.model.KnowledgeIndexRebuildRequest;
import com.aiplatform.knowledge.application.indexing.model.KnowledgeIndexRebuildResult;
import com.aiplatform.knowledge.application.indexing.service.KnowledgeDocumentIndexApplicationService;
import com.aiplatform.knowledge.application.indexing.service.KnowledgeIndexRebuildTransactionService;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildPlan;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentChunkRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeDocumentIndexApplicationServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");

    @Test
    void schedulesServerOwnedGenerationAndReturnsDurablePendingJob() {
        KnowledgeBaseRepository knowledgeBases = knowledgeBases();
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        ModelUsagePolicyPort policy = mock(ModelUsagePolicyPort.class);
        KnowledgeIndexRebuildTransactionService transactions = mock(KnowledgeIndexRebuildTransactionService.class);
        when(documents.findById(TENANT_ID, "7001", "8001"))
                .thenReturn(Optional.of(document(KnowledgeDocumentStatus.CHUNKED)));
        when(chunks.findByDocumentId(TENANT_ID, "8001")).thenReturn(List.of(chunk()));
        when(policy.decide(any(KnowledgeModelUsageRequest.class)))
                .thenReturn(KnowledgeModelUsageDecision.allow(runtime(), Map.of()));
        KnowledgeIndexRebuildPlan plan = KnowledgeIndexRebuildPlan.active(
                TENANT_ID, "9101", "7001", 4, "17", "BAAI/bge-m3", NOW, "1001"
        );
        when(transactions.createOrReuse(any())).thenReturn(
                new KnowledgeIndexRebuildResult(plan, "9201", 1, false)
        );
        KnowledgeDocumentIndexApplicationService service = service(
                knowledgeBases, documents, chunks, policy, transactions
        );

        var result = service.indexDocument(command());

        assertThat(result.ingestJobId()).isEqualTo("9201");
        assertThat(result.status()).isEqualTo("PENDING");
        ArgumentCaptor<KnowledgeIndexRebuildRequest> request =
                ArgumentCaptor.forClass(KnowledgeIndexRebuildRequest.class);
        verify(transactions).createOrReuse(request.capture());
        assertThat(request.getValue().targetModelConfigId()).isEqualTo("17");
        assertThat(request.getValue().targetEmbeddingModel()).isEqualTo("BAAI/bge-m3");
        assertThat(request.getValue().requestedDocumentId()).isEqualTo("8001");
    }

    @Test
    void rejectsUnchunkedDocumentBeforePolicyOrScheduling() {
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        ModelUsagePolicyPort policy = mock(ModelUsagePolicyPort.class);
        KnowledgeIndexRebuildTransactionService transactions = mock(KnowledgeIndexRebuildTransactionService.class);
        when(documents.findById(TENANT_ID, "7001", "8001"))
                .thenReturn(Optional.of(document(KnowledgeDocumentStatus.UPLOADED)));
        when(chunks.findByDocumentId(TENANT_ID, "8001")).thenReturn(List.of(chunk()));
        KnowledgeDocumentIndexApplicationService service = service(
                knowledgeBases(), documents, chunks, policy, transactions
        );

        assertThatThrownBy(() -> service.indexDocument(command()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("文档尚未完成切块");
        verify(policy, never()).decide(any());
        verify(transactions, never()).createOrReuse(any());
    }

    @Test
    void policyDenialDoesNotCreateAJobWithoutExecutableTargetFacts() {
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        ModelUsagePolicyPort policy = mock(ModelUsagePolicyPort.class);
        KnowledgeIndexRebuildTransactionService transactions = mock(KnowledgeIndexRebuildTransactionService.class);
        when(documents.findById(TENANT_ID, "7001", "8001"))
                .thenReturn(Optional.of(document(KnowledgeDocumentStatus.CHUNKED)));
        when(chunks.findByDocumentId(TENANT_ID, "8001")).thenReturn(List.of(chunk()));
        when(policy.decide(any())).thenReturn(KnowledgeModelUsageDecision.deny("POLICY_DENIED", "模型配置不可用"));
        KnowledgeDocumentIndexApplicationService service = service(
                knowledgeBases(), documents, chunks, policy, transactions
        );

        assertThatThrownBy(() -> service.indexDocument(command()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("模型配置不可用");
        verify(transactions, never()).createOrReuse(any());
    }

    private static KnowledgeDocumentIndexApplicationService service(
            KnowledgeBaseRepository knowledgeBases,
            KnowledgeDocumentRepository documents,
            KnowledgeDocumentChunkRepository chunks,
            ModelUsagePolicyPort policy,
            KnowledgeIndexRebuildTransactionService transactions
    ) {
        RbacAuthorizationQueryPort authorization = mock(RbacAuthorizationQueryPort.class);
        when(authorization.findRoleCodes(any(), any(), any())).thenReturn(Set.of("admin"));
        when(authorization.findPermissionCodes(any(), any(), any())).thenReturn(Set.of());
        return new KnowledgeDocumentIndexApplicationService(
                knowledgeBases, documents, chunks, policy, authorization, transactions
        );
    }

    private static KnowledgeBaseRepository knowledgeBases() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        when(repository.findById(TENANT_ID, "7001")).thenReturn(Optional.of(new KnowledgeBase(
                TENANT_ID, "7001", "员工制度库", "", KnowledgeVisibility.PUBLIC,
                Set.of("1001"), "1001", "17", true, true, NOW, NOW
        )));
        return repository;
    }

    private static IndexKnowledgeDocumentCommand command() {
        return new IndexKnowledgeDocumentCommand(TENANT_ID, "1001", "user", "7001", "8001");
    }

    private static KnowledgeDocument document(KnowledgeDocumentStatus status) {
        return new KnowledgeDocument(
                TENANT_ID, "8001", "7001", "员工手册.txt", "upload", "s3://kb/8001",
                "d".repeat(64), "text/plain", 13, 1, status, NOW, NOW, "1001"
        );
    }

    private static KnowledgeDocumentChunk chunk() {
        return new KnowledgeDocumentChunk(
                TENANT_ID, "9001", "7001", "8001", 1, "制度内容", "a".repeat(64), 4,
                null, 0, 4,
                new KnowledgeDocumentChunkMetadata(
                        "s3://kb/8001", 1, "员工手册.txt", null, null, "internal", "tenant",
                        List.of("tenant:2001"), false, NOW, null, "draft", "v1", "v1"
                ),
                "ACTIVE", NOW, NOW, "1001"
        );
    }

    private static KnowledgeModelRuntimeDescriptor runtime() {
        return new KnowledgeModelRuntimeDescriptor(
                "17", "3001", "provider", "openai-compatible",
                new KnowledgeModelRuntimeConnectionRef("https://example.invalid", "api_key", "secret-1", "***"),
                "BAAI/bge-m3", 7, "EMBEDDING", Set.of(KnowledgeModelCapability.EMBEDDING), Map.of(),
                "a".repeat(64), "policy-v1"
        );
    }
}
