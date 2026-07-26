package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.api.invocation.ChatModelPort;
import com.aiplatform.foundation.modelruntime.api.model.ChatChunk;
import com.aiplatform.foundation.modelruntime.api.model.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.ChatRequest;
import com.aiplatform.foundation.modelruntime.api.model.ChatResult;
import com.aiplatform.foundation.modelruntime.api.model.ModelUsage;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用于合同测试和演示自检的确定性对话模型。
 *
 * <p>该模型只回显最后一条用户消息，不代表真实生成质量。</p>
 */
public final class DeterministicChatModel implements ChatModelPort {

    private final ChatDeploymentRef deployment;

    public DeterministicChatModel(ChatDeploymentRef deployment) {
        this.deployment = Objects.requireNonNull(deployment, "确定性对话部署不能为空");
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        requireDeployment(request);
        return new ChatResult(
                deployment,
                answer(request),
                "STOP",
                ModelUsage.UNKNOWN);
    }

    @Override
    public Flow.Publisher<ChatChunk> stream(ChatRequest request) {
        requireDeployment(request);
        var answer = answer(request);
        return subscriber -> {
            Objects.requireNonNull(subscriber, "对话流订阅者不能为空");
            subscriber.onSubscribe(new Flow.Subscription() {
                private final AtomicBoolean completed = new AtomicBoolean();

                @Override
                public void request(long count) {
                    if (count < 1) {
                        subscriber.onError(new IllegalArgumentException("订阅请求数量必须大于零"));
                        return;
                    }
                    if (completed.compareAndSet(false, true)) {
                        subscriber.onNext(new ChatChunk(
                                deployment,
                                1,
                                answer,
                                false,
                                null));
                        subscriber.onNext(new ChatChunk(
                                deployment,
                                2,
                                "",
                                true,
                                ModelUsage.UNKNOWN));
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    completed.set(true);
                }
            });
        };
    }

    private void requireDeployment(ChatRequest request) {
        Objects.requireNonNull(request, "对话请求不能为空");
        if (!deployment.equals(request.deployment())) {
            throw new IllegalArgumentException("确定性对话部署不匹配");
        }
    }

    private static String answer(ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role()
                        == com.aiplatform.foundation.modelruntime.api.model.ChatRole.USER)
                .reduce((left, right) -> right)
                .map(message -> "演示模型已收到：" + message.content())
                .orElse("演示模型未收到用户消息。");
    }
}
