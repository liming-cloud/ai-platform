package com.aiplatform.knowledge.presentation.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalRequest;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalResponse;
import com.aiplatform.knowledge.application.command.RetrieveKnowledgeCommand;
import com.aiplatform.knowledge.application.retrieval.result.RetrieveKnowledgeResult;
import com.aiplatform.knowledge.application.retrieval.result.RetrievalAuthorizationSnapshot;
import com.aiplatform.knowledge.application.retrieval.result.RetrievedKnowledgeChunk;
import com.aiplatform.knowledge.application.retrieval.service.KnowledgeRetrievalApplicationService;
import com.aiplatform.knowledge.domain.retrieval.RetrievalSignal;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeRetrievalCapabilityProviderTest {
    @Test
    void mapsCapabilityRequestToKnowledgeRetrievalCommandAndResponse() {
        KnowledgeRetrievalApplicationService service = mock(KnowledgeRetrievalApplicationService.class);
        when(service.retrieve(any(RetrieveKnowledgeCommand.class))).thenReturn(
                RetrieveKnowledgeResult.answerable(List.of(chunk("2001", "7001", "8001", "1", "kb-7001-active-v2")))
                        .withSecurityEvidence(authorizedSnapshot(), 2, 2)
        );
        KnowledgeRetrievalCapabilityProvider provider = new KnowledgeRetrievalCapabilityProvider(service);

        KnowledgeRetrievalResponse response = provider.retrieve(new KnowledgeRetrievalRequest(
                TenantId.of("2001"),
                "1001",
                "user",
                List.of("role:finance"),
                List.of("7001"),
                "差旅报销上限是多少？",
                "balanced",
                5,
                0.0,
                true
        ));

        ArgumentCaptor<RetrieveKnowledgeCommand> commandCaptor = ArgumentCaptor.forClass(RetrieveKnowledgeCommand.class);
        verify(service).retrieve(commandCaptor.capture());
        RetrieveKnowledgeCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(command.principalId()).isEqualTo("1001");
        assertThat(command.principalType()).isEqualTo("user");
        assertThat(command.knowledgeBaseIds()).containsExactly("7001");
        assertThat(command.answerModel()).isNull();
        assertThat(response.answerable()).isTrue();
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().fragment()).contains("差旅报销上限");
        assertThat(response.citations().getFirst().location()).isEqualTo("p1");
        assertThat(response.evidences().getFirst().retrievalSignal()).isEqualTo("VECTOR");
        assertThat(response.filteredCitationCount()).isZero();
    }

    @Test
    void filtersUnauthorizedCapabilityEvidenceAndDeduplicatesByCompleteIdentity() {
        KnowledgeRetrievalApplicationService service = mock(KnowledgeRetrievalApplicationService.class);
        RetrievedKnowledgeChunk allowed = chunk();
        RetrievedKnowledgeChunk unauthorized = chunk(
                "2001", "7002", "8002", "1", "kb-7002-active-v1"
        );
        RetrievedKnowledgeChunk crossTenant = chunk(
                "2002", "7001", "8001", "1", "kb-7001-active-v1"
        );
        when(service.retrieve(any(RetrieveKnowledgeCommand.class))).thenReturn(
                RetrieveKnowledgeResult.answerable(List.of(
                        allowed,
                        allowed,
                        unauthorized,
                        crossTenant
                ))
                        .withSecurityEvidence(authorizedSnapshot(), 0, 0)
        );
        KnowledgeRetrievalCapabilityProvider provider = new KnowledgeRetrievalCapabilityProvider(service);

        KnowledgeRetrievalResponse response = provider.retrieve(request());

        assertThat(response.answerable()).isTrue();
        assertThat(response.citations()).singleElement().satisfies(citation ->
                assertThat(citation.knowledgeBaseId()).isEqualTo("7001")
        );
        assertThat(response.evidences()).hasSize(1);
        assertThat(response.filteredCitationCount()).isZero();
    }

    @Test
    void failsClosedWhenApplicationResultMixesIndexVersions() {
        KnowledgeRetrievalApplicationService service = mock(KnowledgeRetrievalApplicationService.class);
        when(service.retrieve(any(RetrieveKnowledgeCommand.class))).thenReturn(
                RetrieveKnowledgeResult.answerable(List.of(
                        chunk("2001", "7001", "8001", "1", "kb-7001-active-v1"),
                        chunk("2001", "7001", "8002", "2", "kb-7001-stale-v1")
                )).withSecurityEvidence(authorizedSnapshot(), 0, 0)
        );
        KnowledgeRetrievalCapabilityProvider provider = new KnowledgeRetrievalCapabilityProvider(service);

        KnowledgeRetrievalResponse response = provider.retrieve(request());

        assertThat(response.answerable()).isFalse();
        assertThat(response.citations()).isEmpty();
        assertThat(response.evidences()).isEmpty();
        assertThat(response.filteredCitationCount()).isZero();
    }

    private static KnowledgeRetrievalRequest request() {
        return new KnowledgeRetrievalRequest(
                TenantId.of("2001"),
                "1001",
                "user",
                List.of("role:finance"),
                List.of("7001"),
                "差旅报销上限是多少？",
                "balanced",
                5,
                0.0,
                true
        );
    }

    private static RetrievalAuthorizationSnapshot authorizedSnapshot() {
        return RetrievalAuthorizationSnapshot.of(
                List.of("admin"),
                List.of(),
                List.of("7001"),
                List.of("7001")
        );
    }

    private static RetrievedKnowledgeChunk chunk() {
        return chunk("2001", "7001", "8001", "1", "kb-7001-active-v1");
    }

    private static RetrievedKnowledgeChunk chunk(
            String tenantId,
            String knowledgeBaseId,
            String documentId,
            String chunkId,
            String indexVersion
    ) {
        return new RetrievedKnowledgeChunk(
                tenantId,
                knowledgeBaseId,
                documentId,
                chunkId,
                "差旅报销上限 2000 元。",
                0.91,
                RetrievalSignal.VECTOR,
                "s3://knowledge/doc.txt",
                "员工制度",
                1,
                null,
                false,
                indexVersion
        );
    }
}
