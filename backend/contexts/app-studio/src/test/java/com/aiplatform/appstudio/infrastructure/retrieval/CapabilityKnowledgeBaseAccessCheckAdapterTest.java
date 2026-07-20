package com.aiplatform.appstudio.infrastructure.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckCapability;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckItem;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckRequest;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckResponse;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CapabilityKnowledgeBaseAccessCheckAdapterTest {
    @Test
    void failsClosedWhenKnowledgeCapabilityIsNotWired() {
        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeBaseAccessCheckCapability> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        var adapter = new CapabilityKnowledgeBaseAccessCheckAdapter(provider);

        var result = adapter.check(request());

        assertThat(result.passed()).isFalse();
        assertThat(result.items()).allMatch(item -> !item.accessible());
        assertThat(result.items()).extracting(KnowledgeBaseAccessCheckItem::reasonCode)
                .containsOnly("knowledge_access_capability_not_available");
    }

    @Test
    void delegatesToRegisteredCapability() {
        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeBaseAccessCheckCapability> provider = mock(ObjectProvider.class);
        KnowledgeBaseAccessCheckCapability capability = mock(KnowledgeBaseAccessCheckCapability.class);
        KnowledgeBaseAccessCheckResponse expected = new KnowledgeBaseAccessCheckResponse(List.of(
                KnowledgeBaseAccessCheckItem.allowed("20")
        ));
        when(provider.getIfAvailable()).thenReturn(capability);
        when(capability.check(request())).thenReturn(expected);

        assertThat(new CapabilityKnowledgeBaseAccessCheckAdapter(provider).check(request())).isEqualTo(expected);
    }

    private static KnowledgeBaseAccessCheckRequest request() {
        return new KnowledgeBaseAccessCheckRequest(TenantId.of("2001"), "user", "1001", List.of("20"));
    }
}
