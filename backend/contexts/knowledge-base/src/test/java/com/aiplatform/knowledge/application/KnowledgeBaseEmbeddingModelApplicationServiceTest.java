package com.aiplatform.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.application.command.SwitchKnowledgeBaseEmbeddingModelCommand;
import com.aiplatform.knowledge.application.indexing.model.KnowledgeIndexRebuildRequest;
import com.aiplatform.knowledge.application.indexing.model.KnowledgeIndexRebuildResult;
import com.aiplatform.knowledge.application.indexing.service.KnowledgeIndexRebuildTransactionService;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildPlan;
import com.aiplatform.knowledge.domain.port.embedding.KnowledgeQueryEmbeddingPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

class KnowledgeBaseEmbeddingModelApplicationServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-06-09T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void switchEmbeddingModelProbesNewRuntimeModelBeforeMountingAndSchedulingFullReindex() {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeIndexRebuildTransactionService transactions = mock(KnowledgeIndexRebuildTransactionService.class);
        ModelUsagePolicyPort modelUsage = mock(ModelUsagePolicyPort.class);
        KnowledgeQueryEmbeddingPort embedding = mock(KnowledgeQueryEmbeddingPort.class);
        KnowledgeModelRuntimeDescriptor descriptor = descriptor("17");
        when(knowledgeBases.findById(TENANT_ID, "16")).thenReturn(Optional.of(knowledgeBase("6")));
        when(modelUsage.decide(any(KnowledgeModelUsageRequest.class)))
                .thenReturn(KnowledgeModelUsageDecision.allow(descriptor, Map.of("source", "test")));
        when(embedding.embedQuery(eq(TENANT_ID), eq(descriptor), any())).thenReturn(new float[] {0.1f, 0.2f});
        KnowledgeIndexRebuildPlan plan = KnowledgeIndexRebuildPlan.active(
                TENANT_ID, "9101", "16", 3, "17", "BAAI/bge-m3", NOW, "1001"
        );
        when(transactions.switchModelAndCreate(any(KnowledgeBase.class), any()))
                .thenReturn(new KnowledgeIndexRebuildResult(plan, "", 2, false));
        KnowledgeBaseEmbeddingModelApplicationService service = service(
                knowledgeBases,
                modelUsage,
                embedding,
                transactions
        );

        var result = service.switchEmbeddingModel(command("17"));

        assertThat(result.embeddingModelConfigId()).isEqualTo("17");
        assertThat(result.reindexJobCount()).isEqualTo(2);
        ArgumentCaptor<KnowledgeModelUsageRequest> policyRequest = ArgumentCaptor.forClass(KnowledgeModelUsageRequest.class);
        verify(modelUsage).decide(policyRequest.capture());
        assertThat(policyRequest.getValue().requestedModelConfigId()).isEqualTo("17");
        verify(embedding).embedQuery(eq(TENANT_ID), eq(descriptor), any());
        ArgumentCaptor<KnowledgeBase> savedKnowledgeBase = ArgumentCaptor.forClass(KnowledgeBase.class);
        ArgumentCaptor<KnowledgeIndexRebuildRequest> rebuildRequest =
                ArgumentCaptor.forClass(KnowledgeIndexRebuildRequest.class);
        verify(transactions).switchModelAndCreate(savedKnowledgeBase.capture(), rebuildRequest.capture());
        assertThat(savedKnowledgeBase.getValue().embeddingModelConfigId()).isEqualTo("17");
        assertThat(rebuildRequest.getValue().targetEmbeddingModel()).isEqualTo("BAAI/bge-m3");
        assertThat(rebuildRequest.getValue().requestedDocumentId()).isEmpty();
        InOrder order = org.mockito.Mockito.inOrder(embedding, transactions);
        order.verify(embedding).embedQuery(eq(TENANT_ID), eq(descriptor), any());
        order.verify(transactions).switchModelAndCreate(any(), any());
    }

    @Test
    void switchEmbeddingModelDoesNotPersistWhenRuntimeProbeFails() {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeIndexRebuildTransactionService transactions = mock(KnowledgeIndexRebuildTransactionService.class);
        ModelUsagePolicyPort modelUsage = mock(ModelUsagePolicyPort.class);
        KnowledgeQueryEmbeddingPort embedding = mock(KnowledgeQueryEmbeddingPort.class);
        KnowledgeModelRuntimeDescriptor descriptor = descriptor("17");
        when(knowledgeBases.findById(TENANT_ID, "16")).thenReturn(Optional.of(knowledgeBase("6")));
        when(modelUsage.decide(any(KnowledgeModelUsageRequest.class)))
                .thenReturn(KnowledgeModelUsageDecision.allow(descriptor, Map.of("source", "test")));
        when(embedding.embedQuery(eq(TENANT_ID), eq(descriptor), any()))
                .thenThrow(new RuntimeException("connect failed sk-secret-value"));
        KnowledgeBaseEmbeddingModelApplicationService service = service(
                knowledgeBases,
                modelUsage,
                embedding,
                transactions
        );

        assertThatThrownBy(() -> service.switchEmbeddingModel(command("17")))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("connect failed sk-***");
        verify(transactions, never()).switchModelAndCreate(any(), any());
    }

    @Test
    void externalProbeOrchestrationIsNotATransactionBoundary() throws NoSuchMethodException {
        assertThat(KnowledgeBaseEmbeddingModelApplicationService.class
                .getMethod("switchEmbeddingModel", SwitchKnowledgeBaseEmbeddingModelCommand.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
    }

    private static KnowledgeBaseEmbeddingModelApplicationService service(
            KnowledgeBaseRepository knowledgeBases,
            ModelUsagePolicyPort modelUsage,
            KnowledgeQueryEmbeddingPort embedding,
            KnowledgeIndexRebuildTransactionService transactions
    ) {
        return new KnowledgeBaseEmbeddingModelApplicationService(
                knowledgeBases,
                modelUsage,
                embedding,
                transactions,
                CLOCK
        );
    }

    private static SwitchKnowledgeBaseEmbeddingModelCommand command(String embeddingModelConfigId) {
        return new SwitchKnowledgeBaseEmbeddingModelCommand(
                TENANT_ID,
                "16",
                "1001",
                "USER",
                Set.of("platform-admin"),
                Set.of(new PermissionCode("knowledge:base:write")),
                embeddingModelConfigId
        );
    }

    private static KnowledgeBase knowledgeBase(String embeddingModelConfigId) {
        return new KnowledgeBase(
                TENANT_ID,
                "16",
                "企业制度知识库",
                "用于制度问答",
                KnowledgeVisibility.PUBLIC,
                Set.of("1001"),
                "1001",
                embeddingModelConfigId,
                true,
                true,
                NOW,
                NOW
        );
    }

    private static KnowledgeModelRuntimeDescriptor descriptor(String modelConfigId) {
        return new KnowledgeModelRuntimeDescriptor(
                modelConfigId,
                "3001",
                "siliconflow",
                "openai-compatible",
                new KnowledgeModelRuntimeConnectionRef("https://api.siliconflow.cn/v1", "api_key", "secret-1", "sk-***"),
                "BAAI/bge-m3",
                7,
                "EMBEDDING",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Map.of("dimensions", 1024),
                "a".repeat(64),
                "policy-v1"
        );
    }
}
