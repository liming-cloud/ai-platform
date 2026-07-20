package com.aiplatform.contracts.capability.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeRetrievalRequestTest {
    @Test
    void normalizesRetrievalRequestFacts() {
        KnowledgeRetrievalRequest request = new KnowledgeRetrievalRequest(
                TenantId.of("2001"),
                " user-1 ",
                " user ",
                List.of(" tenant:2001 ", "tenant:2001"),
                List.of(" kb-1 ", "kb-1"),
                " 如何报销 ",
                " default ",
                5,
                0.75,
                true
        );

        assertThat(request.principalId()).isEqualTo("user-1");
        assertThat(request.principalType()).isEqualTo("user");
        assertThat(request.principalAclTokens()).containsExactly("tenant:2001");
        assertThat(request.knowledgeBaseIds()).containsExactly("kb-1");
        assertThat(request.query()).isEqualTo("如何报销");
        assertThat(request.retrievalProfile()).isEqualTo("default");
    }

    @Test
    void rejectsBlankAclTokenInsteadOfFilteringIt() {
        assertThatThrownBy(() -> new KnowledgeRetrievalRequest(
                TenantId.of("2001"),
                "user-1",
                "user",
                List.of("tenant:2001", " "),
                List.of("kb-1"),
                "如何报销",
                "default",
                5,
                0.75,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("访问主体权限快照不能为空");
    }

    @Test
    void rejectsNonFiniteScoreThreshold() {
        assertThatThrownBy(() -> new KnowledgeRetrievalRequest(
                TenantId.of("2001"),
                "user-1",
                "user",
                List.of("tenant:2001"),
                List.of("kb-1"),
                "如何报销",
                "default",
                5,
                Double.NaN,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("相似度阈值必须在 0 到 1 之间");
    }
}
