package com.aiplatform.appstudio.presentation.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.appstudio.application.result.ChatSessionSummaryResult;
import com.aiplatform.appstudio.application.result.CreateChatSessionResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChatSessionResponseTest {
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");

    @Test
    void createResponseExposesTheFrozenReleasePin() {
        CreateChatSessionResponse response = CreateChatSessionResponse.from(new CreateChatSessionResult(
                "4001", "3001", "7001", 1, "1002", "制度问答", NOW, NOW
        ));

        assertThat(response.releaseId()).isEqualTo("7001");
        assertThat(response.releaseVersion()).isEqualTo(1L);
    }

    @Test
    void sessionSummaryExposesRecoverableReplacementState() {
        ChatSessionSummaryResponse response = ChatSessionSummaryResponse.from(new ChatSessionSummaryResult(
                "4001", "3001", "7001", 1, true, "1002", "制度问答", NOW, NOW
        ));

        assertThat(response.releaseId()).isEqualTo("7001");
        assertThat(response.releaseVersion()).isEqualTo(1L);
        assertThat(response.requiresNewSession()).isTrue();
    }
}
