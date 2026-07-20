package com.aiplatform.appstudio.presentation.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.appstudio.application.result.DebugEvidenceResult;
import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunDetail;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunEvidenceSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppStudioDebugRunDetailResponseTest {
    @Test
    void summaryViewRemovesSensitivePromptAnswerCitationEvidenceAndModelSource() {
        AppStudioDebugRunDetailResponse response = AppStudioDebugRunDetailResponse.from(
                new DebugEvidenceResult(detail(), true, null)
        );

        assertThat(response.masked()).isTrue();
        assertThat(response.userMessageContent()).isNull();
        assertThat(response.assistantMessageContent()).isNull();
        assertThat(response.citations()).isEmpty();
        assertThat(response.evidences()).isEmpty();
        assertThat(response.providerCode()).isNull();
        assertThat(response.sourceResourceId()).isNull();
    }

    @Test
    void fullViewKeepsEvidenceAndReturnsAuditEventId() {
        AppStudioDebugRunDetailResponse response = AppStudioDebugRunDetailResponse.from(
                new DebugEvidenceResult(detail(), false, "audit-1")
        );

        assertThat(response.masked()).isFalse();
        assertThat(response.auditEventId()).isEqualTo("audit-1");
        assertThat(response.userMessageContent()).isEqualTo("敏感问题");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.evidences()).hasSize(1);
    }

    private static AppStudioDebugRunDetail detail() {
        return new AppStudioDebugRunDetail(
                "5002", "5001", "3001", "4001", "trace-1", "90", "SUCCEEDED", true,
                "敏感问题", "敏感回答", null,
                List.of(new Citation("20", "30", "1", "敏感文档", "片段", "p1", 0.9)),
                List.of(new AppStudioDebugRunEvidenceSnapshot("20", "30", "1", 0.9, "VECTOR", "v1")),
                "provider", "model", "spring-ai-chat-completion", 20, 10, 100,
                "APP_STUDIO_CHAT", "AI_APPLICATION", "3001", null, null,
                Instant.parse("2026-07-10T03:00:00Z")
        );
    }
}
