package com.aiplatform.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UploadKnowledgeDocumentCommandTest {
    @Test
    void normalizesUploadFactsBeforeApplicationServiceUse() {
        UploadKnowledgeDocumentCommand command = new UploadKnowledgeDocumentCommand(
                TenantId.of("2001"),
                " 1001 ",
                " 7001 ",
                " 员工手册.txt ",
                " text/plain ",
                "制度内容".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(command.operatorId()).isEqualTo("1001");
        assertThat(command.knowledgeBaseId()).isEqualTo("7001");
        assertThat(command.fileName()).isEqualTo("员工手册.txt");
        assertThat(command.contentType()).isEqualTo("text/plain");
    }

    @Test
    void trimsContentTypeAndDefensivelyCopiesContent() {
        byte[] content = "制度内容".getBytes(StandardCharsets.UTF_8);
        UploadKnowledgeDocumentCommand command = new UploadKnowledgeDocumentCommand(
                TenantId.of("2001"),
                "1001",
                "7001",
                "员工手册.txt",
                " text/plain ",
                content
        );
        content[0] = 0;

        assertThat(command.contentType()).isEqualTo("text/plain");
        assertThat(command.content()).containsExactly("制度内容".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsBlankContentTypeWithChineseMessage() {
        assertThatThrownBy(() -> new UploadKnowledgeDocumentCommand(
                TenantId.of("2001"),
                "1001",
                "7001",
                "员工手册.txt",
                " ",
                "制度内容".getBytes(StandardCharsets.UTF_8)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件 MIME 类型不能为空");
    }

    @Test
    void rejectsBlankFileNameWithChineseMessage() {
        assertThatThrownBy(() -> new UploadKnowledgeDocumentCommand(
                TenantId.of("2001"),
                "1001",
                "7001",
                " ",
                "text/plain",
                "制度内容".getBytes(StandardCharsets.UTF_8)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件名不能为空");
    }

    @Test
    void rejectsEmptyContentWithChineseMessage() {
        assertThatThrownBy(() -> new UploadKnowledgeDocumentCommand(
                TenantId.of("2001"),
                "1001",
                "7001",
                "员工手册.txt",
                "text/plain",
                new byte[0]
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件内容不能为空");
    }
}
