package com.aiplatform.foundation.provider.springai;

import com.aiplatform.foundation.modelruntime.api.invocation.ChatModelPort;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatChunk;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatMessage;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatResult;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelUsage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Mono;

/**
 * 将 Spring AI 对话模型限制在平台框架无关契约之后。
 */
public final class SpringAiChatModelAdapter implements ChatModelPort {

    private static final String UNKNOWN_FINISH_REASON = "UNKNOWN";

    private final ChatModel model;
    private final ChatDeploymentRef deployment;

    public SpringAiChatModelAdapter(ChatModel model, ChatDeploymentRef deployment) {
        this.model = Objects.requireNonNull(model, "Spring AI 对话模型不能为空");
        this.deployment = Objects.requireNonNull(deployment, "对话部署不能为空");
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        requireDeployment(request);
        var response = model.call(toPrompt(request));
        return new ChatResult(
                deployment,
                content(response),
                finishReason(response),
                usage(response));
    }

    @Override
    public Flow.Publisher<ChatChunk> stream(ChatRequest request) {
        requireDeployment(request);
        var sequence = new AtomicLong();
        var finalUsage = new AtomicReference<>(ModelUsage.UNKNOWN);
        var chunks = model.stream(toPrompt(request))
                .map(response -> {
                    finalUsage.set(usage(response));
                    return new ChatChunk(
                            deployment,
                            sequence.incrementAndGet(),
                            content(response),
                            false,
                            null);
                })
                .concatWith(Mono.fromSupplier(() -> new ChatChunk(
                        deployment,
                        sequence.incrementAndGet(),
                        "",
                        true,
                        finalUsage.get())));
        return JdkFlowAdapter.publisherToFlowPublisher(chunks);
    }

    private void requireDeployment(ChatRequest request) {
        Objects.requireNonNull(request, "对话请求不能为空");
        if (!deployment.equals(request.deployment())) {
            throw new IllegalArgumentException("对话请求绑定的模型部署不匹配");
        }
    }

    private static Prompt toPrompt(ChatRequest request) {
        var messages = request.messages().stream()
                .map(SpringAiChatModelAdapter::toSpringMessage)
                .toList();
        if (request.maxOutputTokens() == 0) {
            return new Prompt(messages);
        }
        return new Prompt(
                messages,
                ChatOptions.builder()
                        .maxTokens(request.maxOutputTokens())
                        .build());
    }

    private static Message toSpringMessage(ChatMessage message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    private static String content(ChatResponse response) {
        Objects.requireNonNull(response, "Spring AI 对话响应不能为空");
        var result = Objects.requireNonNull(response.getResult(), "Spring AI 对话结果不能为空");
        var output = Objects.requireNonNull(result.getOutput(), "Spring AI 对话输出不能为空");
        return Objects.requireNonNullElse(output.getText(), "");
    }

    private static String finishReason(ChatResponse response) {
        var metadata = response.getResult().getMetadata();
        if (metadata == null || metadata.getFinishReason() == null
                || metadata.getFinishReason().isBlank()) {
            return UNKNOWN_FINISH_REASON;
        }
        return metadata.getFinishReason();
    }

    private static ModelUsage usage(ChatResponse response) {
        var metadata = response.getMetadata();
        if (metadata == null || metadata.getUsage() == null) {
            return ModelUsage.UNKNOWN;
        }
        var usage = metadata.getUsage();
        return new ModelUsage(
                nonNegative(usage.getPromptTokens()),
                nonNegative(usage.getCompletionTokens()),
                nonNegative(usage.getTotalTokens()));
    }

    private static int nonNegative(Number value) {
        return value == null ? 0 : Math.max(0, value.intValue());
    }
}
