package com.aiplatform.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import org.junit.jupiter.api.Test;

class ParseKnowledgeDocumentCommandTest {
    @Test
    void normalizesRequiredFactsBeforeApplicationUse() {
        ParseKnowledgeDocumentCommand command = new ParseKnowledgeDocumentCommand(
                TenantId.of("2001"),
                " 1001 ",
                " 7001 ",
                " 8001 "
        );

        assertThat(command.operatorId()).isEqualTo("1001");
        assertThat(command.knowledgeBaseId()).isEqualTo("7001");
        assertThat(command.documentId()).isEqualTo("8001");
    }

    @Test
    void rejectsBlankDocumentIdWithChineseMessage() {
        assertThatThrownBy(() -> new ParseKnowledgeDocumentCommand(
                TenantId.of("2001"),
                "1001",
                "7001",
                " "
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档 ID不能为空");
    }
}
