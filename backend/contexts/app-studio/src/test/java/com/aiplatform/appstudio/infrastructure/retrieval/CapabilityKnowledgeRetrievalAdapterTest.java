package com.aiplatform.appstudio.infrastructure.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalCapability;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalCitation;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalEvidence;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalRequest;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalResponse;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CapabilityKnowledgeRetrievalAdapterTest {
    @Test
    void deniesRetrievalWhenCapabilityProviderIsNotWired() {
        CapabilityKnowledgeRetrievalAdapter adapter = new CapabilityKnowledgeRetrievalAdapter(emptyProvider());

        KnowledgeRetrievalResponse result = adapter.retrieve(request());

        assertThat(result.answerable()).isFalse();
        assertThat(result.citations()).isEmpty();
        assertThat(result.evidences()).isEmpty();
        assertThat(result.noAnswerReason()).isEqualTo(
                CapabilityKnowledgeRetrievalAdapter.CAPABILITY_NOT_AVAILABLE_REASON
        );
    }

    @Test
    void delegatesToRegisteredKnowledgeRetrievalCapability() {
        KnowledgeRetrievalCitation citation = new KnowledgeRetrievalCitation("7001", "8001", "1", "制度手册", "差旅报销上限 2000 元", "p1", 0.91);
        KnowledgeRetrievalEvidence evidence = new KnowledgeRetrievalEvidence("7001", "8001", "1", 0.91, "VECTOR", "kb-7001-active");
        KnowledgeRetrievalResponse expected = new KnowledgeRetrievalResponse(true, List.of(citation), List.of(evidence), null);
        KnowledgeRetrievalCapability capability = mock(KnowledgeRetrievalCapability.class);
        when(capability.retrieve(request())).thenReturn(expected);
        CapabilityKnowledgeRetrievalAdapter adapter = new CapabilityKnowledgeRetrievalAdapter(fixedProvider(capability));

        KnowledgeRetrievalResponse result = adapter.retrieve(request());

        assertThat(result).isEqualTo(expected);
    }

    private static KnowledgeRetrievalRequest request() {
        return new KnowledgeRetrievalRequest(
                TenantId.of("2001"),
                "1001",
                "user",
                List.of("1001"),
                List.of("7001"),
                "差旅报销上限是多少？",
                "balanced",
                5,
                0.0,
                true
        );
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<KnowledgeRetrievalCapability> emptyProvider() {
        return mock(ObjectProvider.class);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<KnowledgeRetrievalCapability> fixedProvider(KnowledgeRetrievalCapability capability) {
        ObjectProvider<KnowledgeRetrievalCapability> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(capability);
        return provider;
    }
}
