package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.domain.ChatMessageFeedback;
import com.aiplatform.appstudio.domain.ChatMessageFeedbackRating;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppChatMessageFeedbackMapper;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppChatMessageFeedbackRepositoryAdapterTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    @Test
    void returnsCanonicalPersistedIdInsteadOfTheRequestPlaceholder() {
        AppChatMessageFeedbackMapper mapper = mock(AppChatMessageFeedbackMapper.class);
        AppChatMessageFeedbackRepositoryAdapter repository = repository(mapper);
        ChatMessageFeedback feedback = feedback("temporary-feedback-id");
        when(mapper.upsertFeedback(
                2001L, "temporary-feedback-id", 4001L, 5002L, 1001L,
                "HELPFUL", "[\"准确\"]", "已解决问题", NOW
        )).thenReturn("persisted-feedback-id");

        ChatMessageFeedback saved = repository.save(feedback);

        assertThat(saved.feedbackId()).isEqualTo("persisted-feedback-id");
        assertThat(saved).usingRecursiveComparison()
                .ignoringFields("feedbackId")
                .isEqualTo(feedback);
        verify(mapper).upsertFeedback(
                2001L, "temporary-feedback-id", 4001L, 5002L, 1001L,
                "HELPFUL", "[\"准确\"]", "已解决问题", NOW
        );
    }

    @Test
    void repeatedUpsertReturnsTheExistingCanonicalPersistedId() {
        AppChatMessageFeedbackMapper mapper = mock(AppChatMessageFeedbackMapper.class);
        AppChatMessageFeedbackRepositoryAdapter repository = repository(mapper);
        when(mapper.upsertFeedback(
                2001L, "temporary-feedback-id-1", 4001L, 5002L, 1001L,
                "HELPFUL", "[\"准确\"]", "已解决问题", NOW
        )).thenReturn("persisted-feedback-id");
        when(mapper.upsertFeedback(
                2001L, "temporary-feedback-id-2", 4001L, 5002L, 1001L,
                "HELPFUL", "[\"准确\"]", "已解决问题", NOW
        )).thenReturn("persisted-feedback-id");

        ChatMessageFeedback first = repository.save(feedback("temporary-feedback-id-1"));
        ChatMessageFeedback repeated = repository.save(feedback("temporary-feedback-id-2"));

        assertThat(first.feedbackId()).isEqualTo("persisted-feedback-id");
        assertThat(repeated.feedbackId()).isEqualTo("persisted-feedback-id");
    }

    @Test
    void failsClosedWhenTheScopedUpsertReturnsNoPersistedId() {
        AppChatMessageFeedbackMapper mapper = mock(AppChatMessageFeedbackMapper.class);
        AppChatMessageFeedbackRepositoryAdapter repository = repository(mapper);
        when(mapper.upsertFeedback(
                2001L, "temporary-feedback-id", 4001L, 5002L, 1001L,
                "HELPFUL", "[\"准确\"]", "已解决问题", NOW
        )).thenReturn(null);

        assertThatThrownBy(() -> repository.save(feedback("temporary-feedback-id")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("消息反馈保存后未返回持久化 ID");
    }

    private static AppChatMessageFeedbackRepositoryAdapter repository(AppChatMessageFeedbackMapper mapper) {
        return new AppChatMessageFeedbackRepositoryAdapter(mapper, new JsonMapper());
    }

    private static ChatMessageFeedback feedback(String feedbackId) {
        return new ChatMessageFeedback(
                TENANT_ID,
                feedbackId,
                "4001",
                "5002",
                "1001",
                ChatMessageFeedbackRating.HELPFUL,
                List.of("准确"),
                "已解决问题",
                NOW
        );
    }
}
