package com.aiplatform.infra.springai.model.provider.bedrock;

import static com.aiplatform.infra.springai.model.provider.ProviderErrorSanitizer.safeReason;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.model.ModelOptions;
import org.springframework.stereotype.Component;

/**
 * Amazon Bedrock provider 探测客户端，使用 Spring AI Bedrock 模型抽象完成调用。
 */
@Component
public class BedrockSpringAiModelProbeClient implements SpringAiModelProbeClient {
    private static final String PROBE_INPUT = "ping";

    private final BedrockChatModelFactory chatModelFactory;
    private final BedrockEmbeddingModelFactory embeddingModelFactory;

    public BedrockSpringAiModelProbeClient(
            BedrockChatModelFactory chatModelFactory,
            BedrockEmbeddingModelFactory embeddingModelFactory
    ) {
        this.chatModelFactory = Objects.requireNonNull(chatModelFactory, "Amazon Bedrock ChatModel 工厂不能为空");
        this.embeddingModelFactory = Objects.requireNonNull(embeddingModelFactory, "Amazon Bedrock EmbeddingModel 工厂不能为空");
    }

    @Override
    public boolean supports(SpringAiModelRuntimeProbe probe) {
        Objects.requireNonNull(probe, "模型运行时探测请求不能为空");
        if (isEmbedding(probe.request())) {
            return embeddingModelFactory.supports(probe.request());
        }
        return chatModelFactory.supports(probe.request());
    }

    @Override
    public SpringAiModelConnectivityResult probe(SpringAiModelRuntimeProbe probe) {
        Objects.requireNonNull(probe, "模型运行时探测请求不能为空");
        try {
            if (isEmbedding(probe.request())) {
                probeEmbeddingModel(probe);
            } else {
                probeChatModel(probe);
            }
            return SpringAiModelConnectivityResult.passed("Amazon Bedrock 模型运行时探测通过，已通过 Spring AI 模型抽象完成调用");
        } catch (IllegalArgumentException exception) {
            return SpringAiModelConnectivityResult.failed(
                    "Amazon Bedrock 模型供应商地址或配置不合法："
                            + safeReason(exception, probe.request().credentialMaterial())
            );
        } catch (RuntimeException exception) {
            return SpringAiModelConnectivityResult.failed(runtimeFailureMessage(exception, probe.request()));
        }
    }

    private void probeChatModel(SpringAiModelRuntimeProbe probe) {
        ChatOptions chatOptions = requireChatOptions(probe.options());
        ChatModel chatModel = chatModelFactory.create(probe.request(), chatOptions);
        chatModel.call(new Prompt(PROBE_INPUT, chatOptions));
    }

    private void probeEmbeddingModel(SpringAiModelRuntimeProbe probe) {
        EmbeddingOptions embeddingOptions = requireEmbeddingOptions(probe.options());
        EmbeddingModel embeddingModel = embeddingModelFactory.create(probe.request(), embeddingOptions);
        embeddingModel.call(new EmbeddingRequest(List.of(PROBE_INPUT), embeddingOptions));
    }

    private static ChatOptions requireChatOptions(ModelOptions options) {
        if (options instanceof ChatOptions chatOptions) {
            return chatOptions;
        }
        throw new IllegalArgumentException("Amazon Bedrock 聊天模型探测需要 ChatOptions");
    }

    private static EmbeddingOptions requireEmbeddingOptions(ModelOptions options) {
        if (options instanceof EmbeddingOptions embeddingOptions) {
            return embeddingOptions;
        }
        throw new IllegalArgumentException("Amazon Bedrock 向量模型探测需要 EmbeddingOptions");
    }

    private static boolean isEmbedding(SpringAiModelRuntimeRequest request) {
        return "EMBEDDING".equals(request.modelType().trim().toUpperCase(Locale.ROOT));
    }

    private static String runtimeFailureMessage(RuntimeException exception, SpringAiModelRuntimeRequest request) {
        String reason = safeReason(exception, request.credentialMaterial());
        String normalized = reason.toLowerCase(Locale.ROOT);
        if (normalized.contains("401") || normalized.contains("403")
                || normalized.contains("unauthorized") || normalized.contains("forbidden")
                || normalized.contains("accessdenied") || normalized.contains("security token")) {
            return "Amazon Bedrock 模型供应商鉴权失败：" + reason;
        }
        if (normalized.contains("404") || normalized.contains("not found") || normalized.contains("validationexception")) {
            return "Amazon Bedrock 模型地址或模型编码不存在：" + reason;
        }
        if (normalized.contains("i/o error") || normalized.contains("connect") || normalized.contains("connection")
                || normalized.contains("refused") || normalized.contains("timed out")
                || normalized.contains("unknownhost") || normalized.contains("unknown host")) {
            return "Amazon Bedrock 网络连接失败：" + reason;
        }
        return "Amazon Bedrock 模型运行时探测失败：" + reason;
    }

}
