package com.aiplatform.appstudio.domain.port.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatCompletionRequestTest {
    @Test
    void normalizesChatCompletionRequestFacts() {
        ChatCompletionRequest request = new ChatCompletionRequest(
                TenantId.of("2001"),
                " app-1 ",
                " user-1 ",
                " trace-1 ",
                " 仅依据企业制度回答 ",
                " 企业政策是什么 ",
                List.of(citation()),
                runtimeDescriptor()
        );

        assertThat(request.applicationId()).isEqualTo("app-1");
        assertThat(request.actorId()).isEqualTo("user-1");
        assertThat(request.traceId()).isEqualTo("trace-1");
        assertThat(request.applicationInstruction()).isEqualTo("仅依据企业制度回答");
        assertThat(request.userQuestion()).isEqualTo("企业政策是什么");
        assertThat(request.citations()).containsExactly(citation());
    }

    @Test
    void rejectsBlankChatCompletionRequestFacts() {
        assertThatThrownBy(() -> new ChatCompletionRequest(
                TenantId.of("2001"),
                "app-1",
                "user-1",
                "仅依据企业制度回答",
                " ",
                List.of(citation()),
                runtimeDescriptor()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户问题不能为空");
    }

    @Test
    void rejectsModelDispatchWithoutAuthorizedCitations() {
        assertThatThrownBy(() -> new ChatCompletionRequest(
                TenantId.of("2001"),
                "app-1",
                "user-1",
                "仅依据企业制度回答",
                "企业政策是什么",
                List.of(),
                runtimeDescriptor()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("检索引用不能为空，证据不足时禁止调用模型");
    }

    private static Citation citation() {
        return new Citation("kb-1", "doc-1", "chunk-1", "企业制度", "可报销差旅费用", "p1", 0.9);
    }

    private static AppModelRuntimeDescriptor runtimeDescriptor() {
        return new AppModelRuntimeDescriptor(
                "9001",
                "3001",
                "openai",
                "openai-compatible",
                new AppModelRuntimeConnectionRef("https://api.openai.example/v1", "api_key", "secret-1"),
                "gpt-4.1-mini",
                7,
                "CHAT",
                Set.of("chat"),
                Map.of("temperature", 0.2),
                "a".repeat(64),
                "policy-v1"
        );
    }
}
