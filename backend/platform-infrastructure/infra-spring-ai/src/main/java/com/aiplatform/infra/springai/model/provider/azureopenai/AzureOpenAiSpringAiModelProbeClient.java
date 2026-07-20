package com.aiplatform.infra.springai.model.provider.azureopenai;

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
 * Azure OpenAI provider 探测客户端，使用 Spring AI Azure OpenAI 抽象调用部署。
 */
@Component
public class AzureOpenAiSpringAiModelProbeClient implements SpringAiModelProbeClient {
    private static final String PROBE_INPUT = "ping";

    private final AzureOpenAiChatModelFactory chatModelFactory;
    private final AzureOpenAiEmbeddingModelFactory embeddingModelFactory;

    public AzureOpenAiSpringAiModelProbeClient(
            AzureOpenAiChatModelFactory chatModelFactory,
            AzureOpenAiEmbeddingModelFactory embeddingModelFactory
    ) {
        this.chatModelFactory = Objects.requireNonNull(chatModelFactory, "Azure OpenAI ChatModel 工厂不能为空");
        this.embeddingModelFactory = Objects.requireNonNull(embeddingModelFactory, "Azure OpenAI EmbeddingModel 工厂不能为空");
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
            return SpringAiModelConnectivityResult.passed("Azure OpenAI 模型运行时探测通过，已通过 Spring AI 模型抽象完成调用");
        } catch (IllegalArgumentException exception) {
            return SpringAiModelConnectivityResult.failed(
                    "Azure OpenAI 模型供应商地址或配置不合法："
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
        throw new IllegalArgumentException("Azure OpenAI 聊天模型探测需要 ChatOptions");
    }

    private static EmbeddingOptions requireEmbeddingOptions(ModelOptions options) {
        if (options instanceof EmbeddingOptions embeddingOptions) {
            return embeddingOptions;
        }
        throw new IllegalArgumentException("Azure OpenAI 向量模型探测需要 EmbeddingOptions");
    }

    private static boolean isEmbedding(SpringAiModelRuntimeRequest request) {
        return "EMBEDDING".equals(request.modelType().trim().toUpperCase(Locale.ROOT));
    }

    private static String runtimeFailureMessage(RuntimeException exception, SpringAiModelRuntimeRequest request) {
        String reason = safeReason(exception, request.credentialMaterial());
        String normalized = diagnosticText(exception);
        if (normalized.contains("401") || normalized.contains("403")
                || normalized.contains("unauthorized") || normalized.contains("forbidden")) {
            return "Azure OpenAI 模型供应商鉴权失败：" + reason;
        }
        if (normalized.contains("404") || normalized.contains("not found")) {
            return "Azure OpenAI deployment、api-version 或模型地址不存在：" + reason;
        }
        if (normalized.contains("i/o error") || normalized.contains("connect") || normalized.contains("connection")
                || normalized.contains("refused") || normalized.contains("timed out")
                || normalized.contains("unknownhost") || normalized.contains("unknown host")) {
            return "Azure OpenAI 网络连接失败：" + reason;
        }
        return "Azure OpenAI 模型运行时探测失败：" + reason;
    }

    private static String diagnosticText(Throwable failure) {
        StringBuilder diagnostic = new StringBuilder();
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++) {
            diagnostic.append(' ')
                    .append(current.getClass().getName())
                    .append(' ')
                    .append(current.getMessage());
            current = current.getCause();
        }
        return diagnostic.toString().toLowerCase(Locale.ROOT);
    }

}
