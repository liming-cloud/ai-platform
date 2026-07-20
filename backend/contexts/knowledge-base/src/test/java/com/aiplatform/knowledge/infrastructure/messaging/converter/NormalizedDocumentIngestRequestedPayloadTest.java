package com.aiplatform.knowledge.infrastructure.messaging.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NormalizedDocumentIngestRequestedPayloadTest {
    @Test
    void normalizesPayloadIdsBeforeCommandMapping() {
        NormalizedDocumentIngestRequestedPayload payload = new NormalizedDocumentIngestRequestedPayload(
                " 8101 ",
                " 7001 ",
                " 8001 "
        );

        assertThat(payload.ingestJobId()).isEqualTo("8101");
        assertThat(payload.knowledgeBaseId()).isEqualTo("7001");
        assertThat(payload.documentId()).isEqualTo("8001");
    }

    @Test
    void rejectsBlankPayloadIds() {
        assertThatThrownBy(() -> new NormalizedDocumentIngestRequestedPayload(" ", "7001", "8001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("入库任务 ID不能为空");
    }
}
