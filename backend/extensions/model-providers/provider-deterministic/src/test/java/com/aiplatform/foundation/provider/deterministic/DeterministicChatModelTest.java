package com.aiplatform.foundation.provider.deterministic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.modelruntime.api.model.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.ChatMessage;
import com.aiplatform.foundation.modelruntime.api.model.ChatRequest;
import com.aiplatform.foundation.modelruntime.api.model.ChatRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicChatModelTest {

    @Test
    void 应稳定回显最后一条用户消息() {
        var deployment = new ChatDeploymentRef(
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                2);
        var model = new DeterministicChatModel(deployment);

        var result = model.chat(new ChatRequest(
                deployment,
                List.of(
                        new ChatMessage(ChatRole.USER, "第一问"),
                        new ChatMessage(ChatRole.ASSISTANT, "第一答"),
                        new ChatMessage(ChatRole.USER, "第二问"))));

        assertEquals("演示模型已收到：第二问", result.content());
        assertEquals("STOP", result.finishReason());
    }
}
