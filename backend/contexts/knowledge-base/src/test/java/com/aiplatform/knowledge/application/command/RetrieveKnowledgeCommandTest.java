package com.aiplatform.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.retrieval.RetrievalProfile;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrieveKnowledgeCommandTest {
    @Test
    void normalizesRetrievalCommandFactsBeforeApplicationUse() {
        RetrieveKnowledgeCommand command = new RetrieveKnowledgeCommand(
                TenantId.of("2001"),
                " 1001 ",
                " user ",
                List.of(" 7001 ", "7001", " 7002 "),
                " 员工制度 ",
                RetrievalProfile.BALANCED,
                " chat-model-1 "
        );

        assertThat(command.principalId()).isEqualTo("1001");
        assertThat(command.principalType()).isEqualTo("user");
        assertThat(command.knowledgeBaseIds()).containsExactly("7001", "7002");
        assertThat(command.query()).isEqualTo("员工制度");
        assertThat(command.answerModel()).isEqualTo("chat-model-1");
    }

    @Test
    void acceptsBlankAnswerModelAsNotRequested() {
        RetrieveKnowledgeCommand command = new RetrieveKnowledgeCommand(
                TenantId.of("2001"),
                "1001",
                "user",
                List.of("7001"),
                "员工制度",
                RetrievalProfile.BALANCED,
                "   "
        );

        assertThat(command.answerModel()).isNull();
    }

    @Test
    void rejectsBlankKnowledgeBaseIdInsteadOfFilteringBadInput() {
        assertThatThrownBy(() -> command(List.of("7001", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("知识库范围不能为空白");
    }

    @Test
    void rejectsNullKnowledgeBaseIdInsteadOfFilteringBadInput() {
        assertThatThrownBy(() -> command(Arrays.asList("7001", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("知识库范围不能为空白");
    }

    private static RetrieveKnowledgeCommand command(List<String> knowledgeBaseIds) {
        return new RetrieveKnowledgeCommand(
                TenantId.of("2001"),
                "1001",
                "user",
                knowledgeBaseIds,
                "员工制度",
                RetrievalProfile.BALANCED,
                null
        );
    }
}
