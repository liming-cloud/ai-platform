package com.aiplatform.knowledge.presentation.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckRequest;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeBaseAccessCheckCapabilityProviderTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-10T03:00:00Z");

    @Test
    void returnsOnlyStableAccessDecisionsWithoutLeakingKnowledgeBaseDetails() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        when(repository.findById(TENANT_ID, "20")).thenReturn(Optional.of(knowledgeBase("20", true, Set.of("1001"))));
        when(repository.findById(TENANT_ID, "21")).thenReturn(Optional.of(knowledgeBase("21", true, Set.of("2002"))));
        when(repository.findById(TENANT_ID, "22")).thenReturn(Optional.of(knowledgeBase("22", false, Set.of("1001"))));
        when(repository.findById(TENANT_ID, "23")).thenReturn(Optional.empty());

        var result = new KnowledgeBaseAccessCheckCapabilityProvider(repository).check(
                new KnowledgeBaseAccessCheckRequest(TENANT_ID, "user", "1001", List.of("20", "21", "22", "23"))
        );

        assertThat(result.items()).extracting(item -> item.knowledgeBaseId()).containsExactly("20", "21", "22", "23");
        assertThat(result.items()).extracting(item -> item.accessible()).containsExactly(true, false, false, false);
        assertThat(result.items()).extracting(item -> item.reasonCode()).containsExactly(
                null,
                "resource_not_visible",
                "knowledge_base_not_published",
                "resource_not_visible"
        );
    }

    private static KnowledgeBase knowledgeBase(String id, boolean published, Set<String> allowedPrincipals) {
        return new KnowledgeBase(
                TENANT_ID,
                id,
                "内部名称不应进入能力响应",
                "",
                KnowledgeVisibility.SPECIFIED_MEMBERS,
                allowedPrincipals,
                "1001",
                "6",
                true,
                published,
                NOW,
                NOW
        );
    }
}
