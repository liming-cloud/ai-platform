package com.aiplatform.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import org.junit.jupiter.api.Test;

class ParseDocumentIngestJobCommandTest {
    @Test
    void normalizesRequiredIdsBeforeApplicationUse() {
        ParseDocumentIngestJobCommand command = new ParseDocumentIngestJobCommand(
                TenantId.of("2001"),
                " 8101 ",
                " 7001 ",
                " 8001 "
        );

        assertThat(command.ingestJobId()).isEqualTo("8101");
        assertThat(command.knowledgeBaseId()).isEqualTo("7001");
        assertThat(command.documentId()).isEqualTo("8001");
    }

    @Test
    void rejectsBlankRequiredIds() {
        assertThatThrownBy(() -> new ParseDocumentIngestJobCommand(
                TenantId.of("2001"),
                " ",
                "7001",
                "8001"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("入库任务 ID不能为空");
    }
}
