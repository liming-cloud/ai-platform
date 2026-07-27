package com.aiplatform.foundation.provider.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatMessage;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRole;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import reactor.core.publisher.Flux;

class SpringAiModelAdapterTest {

    private static final long DEPLOYMENT_ID = 320000000000000001L;

    @Test
    void 对话适配器应映射回答结束原因和令牌用量() {
        var capturedPrompt = new AtomicReference<Prompt>();
        ChatModel model = prompt -> {
            capturedPrompt.set(prompt);
            return response("可信回答", "STOP", 7, 3);
        };
        var deployment = new ChatDeploymentRef(DEPLOYMENT_ID, 2);
        var adapter = new SpringAiChatModelAdapter(model, deployment);

        var result = adapter.chat(new ChatRequest(
                deployment,
                List.of(
                        new ChatMessage(ChatRole.SYSTEM, "只依据证据回答"),
                        new ChatMessage(ChatRole.USER, "商品在哪里？")),
                256));

        assertEquals("可信回答", result.content());
        assertEquals("STOP", result.finishReason());
        assertEquals(7, result.usage().inputTokens());
        assertEquals(3, result.usage().outputTokens());
        assertEquals(10, result.usage().totalTokens());
        assertEquals(256, capturedPrompt.get().getOptions().getMaxTokens());
    }

    @Test
    void 对话流应产生单调序号和唯一结束片段() throws InterruptedException {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return response("完整回答", "STOP", 4, 2);
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(
                        response("可信", "", 0, 0),
                        response("回答", "STOP", 4, 2));
            }
        };
        var deployment = new ChatDeploymentRef(DEPLOYMENT_ID, 2);
        var adapter = new SpringAiChatModelAdapter(model, deployment);
        var subscriber = new CollectingSubscriber();

        adapter.stream(new ChatRequest(
                        deployment,
                        List.of(new ChatMessage(ChatRole.USER, "问题"))))
                .subscribe(subscriber);

        assertTrue(subscriber.completed.await(2, TimeUnit.SECONDS));
        assertEquals(List.of(1L, 2L, 3L), subscriber.items.stream()
                .map(item -> item.sequence())
                .toList());
        assertEquals(1, subscriber.items.stream()
                .filter(item -> item.completed())
                .count());
        assertEquals(6, subscriber.items.getLast().usage().totalTokens());
    }

    @Test
    void 嵌入适配器应校验结果数量和维度() {
        EmbeddingModel model = new StubEmbeddingModel(List.of(
                new float[] {1.0f, 0.0f},
                new float[] {0.0f, 1.0f}));
        var deployment = new EmbeddingDeploymentRef(DEPLOYMENT_ID, 3, 2);
        var adapter = new SpringAiEmbeddingModelAdapter(model, deployment);

        var result = adapter.embed(new EmbeddingRequest(
                deployment,
                List.of("第一段", "第二段")));

        assertEquals(2, result.vectors().size());
        assertEquals(2, result.vectors().getFirst().dimension());
        assertThrows(
                IllegalStateException.class,
                () -> new SpringAiEmbeddingModelAdapter(
                                new StubEmbeddingModel(List.of(new float[] {1.0f, 0.0f})),
                                deployment)
                        .embed(new EmbeddingRequest(
                                deployment,
                                List.of("第一段", "第二段"))));
    }

    private static ChatResponse response(
            String content,
            String finishReason,
            int inputTokens,
            int outputTokens) {
        var generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
        var usage = new Usage() {
            @Override
            public Integer getPromptTokens() {
                return inputTokens;
            }

            @Override
            public Integer getCompletionTokens() {
                return outputTokens;
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }
        };
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(content), generationMetadata)),
                ChatResponseMetadata.builder()
                        .usage(usage)
                        .build());
    }

    private static final class StubEmbeddingModel implements EmbeddingModel {

        private final List<float[]> values;

        private StubEmbeddingModel(List<float[]> values) {
            this.values = values;
        }

        @Override
        public float[] embed(Document document) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
            var embeddings = new ArrayList<Embedding>();
            for (var index = 0; index < values.size(); index++) {
                embeddings.add(new Embedding(values.get(index), index));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return values;
        }
    }

    private static final class CollectingSubscriber
            implements Flow.Subscriber<com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatChunk> {

        private final List<com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatChunk> items =
                new ArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(
                com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatChunk item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            completed.countDown();
            throw new AssertionError(throwable);
        }

        @Override
        public void onComplete() {
            completed.countDown();
        }
    }
}
