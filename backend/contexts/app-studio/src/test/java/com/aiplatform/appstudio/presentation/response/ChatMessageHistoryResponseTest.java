package com.aiplatform.appstudio.presentation.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.appstudio.application.result.ChatMessageHistoryResult;
import com.aiplatform.appstudio.application.result.CitationResult;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatMessageHistoryResponseTest {

    @Test
    void mapsOnlyTheTrustedHistoryContractWithoutInventingFilteredCitationCounts() {
        ChatMessageHistoryResult result = new ChatMessageHistoryResult(
                "5002",
                "4001",
                "3001",
                "ASSISTANT",
                "制度内容如下。",
                List.of(new CitationResult("21", "18", "19", "员工手册", "制度内容", "p1", 0.91)),
                1,
                "model-log-5002",
                "trace-5002",
                Instant.parse("2026-07-10T00:00:00Z")
        );

        ChatMessageHistoryResponse response = ChatMessageHistoryResponse.from(result);

        assertThat(response.applicationId()).isEqualTo("3001");
        assertThat(response.messageId()).isEqualTo("5002");
        assertThat(response.citationCount()).isEqualTo(1);
        assertThat(response.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.knowledgeBaseId()).isEqualTo("21");
            assertThat(citation.documentId()).isEqualTo("18");
            assertThat(citation.chunkId()).isEqualTo("19");
            assertThat(citation.title()).isEqualTo("员工手册");
            assertThat(citation.snippet()).isEqualTo("制度内容");
            assertThat(citation.locator()).isEqualTo("p1");
            assertThat(citation.score()).isEqualTo(0.91);
        });
        assertThat(Arrays.stream(ChatMessageHistoryResponse.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly(
                        "messageId", "sessionId", "applicationId", "role", "content", "citations",
                        "citationCount", "modelInvocationLogId", "traceId", "createdAt"
                );
    }
}
