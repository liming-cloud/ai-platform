package com.aiplatform.infra.springai.model.provider.local;

import static com.aiplatform.infra.springai.model.provider.ProviderErrorSanitizer.safeReason;

import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.model.ModelOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地确定性运行时探测客户端，真实调用 Spring AI ChatModel/EmbeddingModel 抽象完成连通性验证。
 */
@Component
@ConditionalOnProperty(name = "aip.model-hub.local-deterministic-enabled", havingValue = "true")
public class LocalDeterministicSpringAiModelProbeClient implements SpringAiModelProbeClient {
    private static final String PROBE_INPUT = "ping";
    private final LocalDeterministicEmbeddingModelFactory embeddingModelFactory;
    private final LocalDeterministicChatModelFactory chatModelFactory;

    public LocalDeterministicSpringAiModelProbeClient(
            LocalDeterministicEmbeddingModelFactory embeddingModelFactory,
            LocalDeterministicChatModelFactory chatModelFactory
    ) {
        this.embeddingModelFactory = Objects.requireNonNull(embeddingModelFactory, "本地 EmbeddingModel 工厂不能为空");
        this.chatModelFactory = Objects.requireNonNull(chatModelFactory, "本地 ChatModel 工厂不能为空");
    }

    @Override
    public boolean supports(SpringAiModelRuntimeProbe probe) {
        Objects.requireNonNull(probe, "模型运行时探测请求不能为空");
        return embeddingModelFactory.supports(probe.request()) || chatModelFactory.supports(probe.request());
    }

    @Override
    public SpringAiModelConnectivityResult probe(SpringAiModelRuntimeProbe probe) {
        Objects.requireNonNull(probe, "模型运行时探测请求不能为空");
        try {
            if (chatModelFactory.supports(probe.request())) {
                return probeChatModel(probe);
            }
            return probeEmbeddingModel(probe);
        } catch (RuntimeException exception) {
            return SpringAiModelConnectivityResult.failed(
                    "本地模型运行时探测失败："
                            + safeReason(exception, probe.request().credentialMaterial())
            );
        }
    }

    private SpringAiModelConnectivityResult probeChatModel(SpringAiModelRuntimeProbe probe) {
        ChatOptions options = requireChatOptions(probe.options());
        ChatModel chatModel = chatModelFactory.create(probe.request(), options);
        String answer = chatModel.call(new Prompt(PROBE_INPUT, options)).getResult().getOutput().getText();
        if (answer == null || answer.isBlank()) {
            return SpringAiModelConnectivityResult.failed("本地聊天运行时未返回回答结果");
        }
        return SpringAiModelConnectivityResult.passed("本地聊天运行时探测通过，已通过 Spring AI ChatModel 抽象完成调用");
    }

    private SpringAiModelConnectivityResult probeEmbeddingModel(SpringAiModelRuntimeProbe probe) {
            EmbeddingOptions options = requireEmbeddingOptions(probe.options());
            EmbeddingModel embeddingModel = embeddingModelFactory.create(probe.request(), options);
            var response = embeddingModel.call(new EmbeddingRequest(List.of(PROBE_INPUT), options));
            if (response.getResults().isEmpty() || response.getResult().getOutput().length == 0) {
                return SpringAiModelConnectivityResult.failed("本地向量运行时未返回向量结果");
            }
            return SpringAiModelConnectivityResult.passed("本地向量运行时探测通过，已通过 Spring AI EmbeddingModel 抽象完成调用");
    }

    private static ChatOptions requireChatOptions(ModelOptions options) {
        if (options instanceof ChatOptions chatOptions) {
            return chatOptions;
        }
        throw new IllegalArgumentException("本地聊天运行时探测需要 ChatOptions");
    }

    private static EmbeddingOptions requireEmbeddingOptions(ModelOptions options) {
        if (options instanceof EmbeddingOptions embeddingOptions) {
            return embeddingOptions;
        }
        throw new IllegalArgumentException("本地向量运行时探测需要 EmbeddingOptions");
    }

}
