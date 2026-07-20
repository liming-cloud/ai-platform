package com.aiplatform.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import org.junit.jupiter.api.Test;

class CreateKnowledgeBaseCommandTest {
    @Test
    void normalizesCreationFactsBeforeApplicationServiceUse() {
        CreateKnowledgeBaseCommand command = new CreateKnowledgeBaseCommand(
                TenantId.of("2001"),
                " 1001 ",
                " user ",
                " 企业制度知识库 ",
                " public ",
                " 用于制度问答 ",
                " 17 "
        );

        assertThat(command.operatorId()).isEqualTo("1001");
        assertThat(command.operatorType()).isEqualTo("user");
        assertThat(command.name()).isEqualTo("企业制度知识库");
        assertThat(command.visibility()).isEqualTo("public");
        assertThat(command.description()).isEqualTo("用于制度问答");
    }

    @Test
    void rejectsBlankOperatorAndVisibility() {
        assertThatThrownBy(() -> new CreateKnowledgeBaseCommand(
                        TenantId.of("2001"),
                        " ",
                        "user",
                        "企业制度知识库",
                        "public",
                        "用于制度问答",
                        "17"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("操作人 ID不能为空");
        assertThatThrownBy(() -> new CreateKnowledgeBaseCommand(
                        TenantId.of("2001"),
                        "1001",
                        "user",
                        "企业制度知识库",
                        " ",
                        "用于制度问答",
                        "17"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("知识库可见性不能为空");
    }
}
