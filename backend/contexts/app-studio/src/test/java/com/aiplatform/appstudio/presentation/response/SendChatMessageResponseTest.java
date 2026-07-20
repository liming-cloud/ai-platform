package com.aiplatform.appstudio.presentation.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.appstudio.application.result.ChatMessageResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SendChatMessageResponseTest {
    @Test
    void fromExposesFailedInvocationDiagnosticsWithoutDroppingErrorFields() {
        ChatMessageResult result = new ChatMessageResult(
                "5001",
                "5002",
                "4001",
                "3001",
                "模型调用失败，已记录调用日志，请在调试运行详情中查看错误诊断。",
                false,
                List.of(),
                List.of(),
                "模型调用失败，已记录调用日志，请在调试运行详情中查看错误诊断。",
                3,
                Instant.parse("2026-06-21T12:00:00Z"),
                Map.of(
                        "traceId", "model-failed-trace",
                        "modelInvocationLogId", "99",
                        "decisionSource", "model-invocation-failed",
                        "invocationStatus", "FAILED",
                        "errorCode", "IllegalStateException",
                        "errorMessage", "simulated model outage"
                )
        );

        SendChatMessageResponse response = SendChatMessageResponse.from(result);

        assertThat(response.messageId()).isEqualTo("5002");
        assertThat(response.answerable()).isFalse();
        assertThat(response.modelInvocationLogId()).isEqualTo("99");
        assertThat(response.decisionSource()).isEqualTo("model-invocation-failed");
        assertThat(response.invocationStatus()).isEqualTo("FAILED");
        assertThat(response.errorCode()).isEqualTo("IllegalStateException");
        assertThat(response.errorMessage()).isEqualTo("simulated model outage");
        assertThat(response.filteredCitationCount()).isZero();
    }

    @Test
    void chatHttpResponsesAreIndistinguishableAcrossInternalFilteringCounts() {
        Instant createdAt = Instant.parse("2026-06-21T12:00:00Z");
        ChatMessageResult withoutFiltering = resultWithFilteredCitationCount(0, createdAt);
        ChatMessageResult withFiltering = resultWithFilteredCitationCount(8, createdAt);

        assertThat(SendChatMessageResponse.from(withFiltering))
                .isEqualTo(SendChatMessageResponse.from(withoutFiltering));
    }

    private static ChatMessageResult resultWithFilteredCitationCount(int count, Instant createdAt) {
        return new ChatMessageResult(
                "5001",
                "5002",
                "4001",
                "3001",
                "未召回可用知识",
                false,
                List.of(),
                List.of(),
                "未召回可用知识",
                count,
                createdAt,
                Map.of()
        );
    }
}
