package com.aiplatform.infra.springai.model.provider.vertexai;

import static com.aiplatform.infra.springai.model.provider.ProviderErrorSanitizer.safeReason;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.model.ModelOptions;
import org.springframework.stereotype.Component;

/**
 * Vertex AI provider 探测客户端，使用 Spring AI Vertex 模型抽象完成调用。
 */
@Component
public class VertexAiSpringAiModelProbeClient implements SpringAiModelProbeClient {
    private static final String PROBE_INPUT = "ping";

    private final VertexAiEmbeddingModelFactory embeddingModelFactory;

    public VertexAiSpringAiModelProbeClient(VertexAiEmbeddingModelFactory embeddingModelFactory) {
        this.embeddingModelFactory = Objects.requireNonNull(embeddingModelFactory, "Vertex AI EmbeddingModel 工厂不能为空");
    }

    @Override
    public boolean supports(SpringAiModelRuntimeProbe probe) {
        Objects.requireNonNull(probe, "模型运行时探测请求不能为空");
        return isEmbedding(probe.request()) && embeddingModelFactory.supports(probe.request());
    }

    @Override
    public SpringAiModelConnectivityResult probe(SpringAiModelRuntimeProbe probe) {
        Objects.requireNonNull(probe, "模型运行时探测请求不能为空");
        try {
            if (!isEmbedding(probe.request())) {
                return SpringAiModelConnectivityResult.failed(
                        "Spring AI 2.0 已移除 Vertex AI CHAT；请迁移到 google-genai provider"
                );
            }
            probeEmbeddingModel(probe);
            return SpringAiModelConnectivityResult.passed("Vertex AI 模型运行时探测通过，已通过 Spring AI 模型抽象完成调用");
        } catch (IllegalArgumentException exception) {
            return SpringAiModelConnectivityResult.failed(
                    "Vertex AI 模型供应商地址或配置不合法："
                            + safeReason(exception, probe.request().credentialMaterial())
            );
        } catch (RuntimeException exception) {
            return SpringAiModelConnectivityResult.failed(runtimeFailureMessage(exception, probe.request()));
        }
    }

    private void probeEmbeddingModel(SpringAiModelRuntimeProbe probe) {
        EmbeddingOptions embeddingOptions = requireEmbeddingOptions(probe.options());
        EmbeddingModel embeddingModel = embeddingModelFactory.create(probe.request(), embeddingOptions);
        embeddingModel.call(new EmbeddingRequest(List.of(PROBE_INPUT), embeddingOptions));
    }

    private static EmbeddingOptions requireEmbeddingOptions(ModelOptions options) {
        if (options instanceof EmbeddingOptions embeddingOptions) {
            return embeddingOptions;
        }
        throw new IllegalArgumentException("Vertex AI 向量模型探测需要 EmbeddingOptions");
    }

    private static boolean isEmbedding(SpringAiModelRuntimeRequest request) {
        return "EMBEDDING".equals(request.modelType().trim().toUpperCase(Locale.ROOT));
    }

    private static String runtimeFailureMessage(RuntimeException exception, SpringAiModelRuntimeRequest request) {
        String reason = safeReason(exception, request.credentialMaterial());
        String normalized = reason.toLowerCase(Locale.ROOT);
        if (normalized.contains("401") || normalized.contains("403")
                || normalized.contains("unauthorized") || normalized.contains("forbidden")
                || normalized.contains("permission_denied")) {
            return "Vertex AI 模型供应商鉴权失败：" + reason;
        }
        if (normalized.contains("404") || normalized.contains("not found") || normalized.contains("invalid_argument")) {
            return "Vertex AI 模型地址或模型编码不存在：" + reason;
        }
        if (normalized.contains("i/o error") || normalized.contains("connect") || normalized.contains("connection")
                || normalized.contains("refused") || normalized.contains("timed out")
                || normalized.contains("unknownhost") || normalized.contains("unknown host")) {
            return "Vertex AI 网络连接失败：" + reason;
        }
        return "Vertex AI 模型运行时探测失败：" + reason;
    }

}
