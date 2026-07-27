package com.aiplatform.foundation.provider.ollama;

import com.aiplatform.foundation.modelruntime.api.extension.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelCapability;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderProbeRequest;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;

/**
 * 通过最小真实调用验证 Ollama 模型及其指定能力。
 */
public final class OllamaConnectionProbe implements ProviderConnectionProbe {

    private static final String PROBE_PROMPT = "只回复 OK。";
    private static final Set<ModelCapability> CHAT_CAPABILITIES =
            Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT);
    private static final Set<ModelCapability> EMBEDDING_CAPABILITIES =
            Set.of(ModelCapability.EMBEDDING);

    private final OutboundEndpointPolicy endpointPolicy;

    public OllamaConnectionProbe(OutboundEndpointPolicy endpointPolicy) {
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "出站地址策略不能为空");
    }

    @Override
    public ProviderKind providerKind() {
        return ProviderKind.OLLAMA;
    }

    @Override
    public ProviderProbeObservation probe(ProviderProbeRequest request) {
        Objects.requireNonNull(request, "探测请求不能为空");
        var startedAt = System.nanoTime();
        if (request.providerKind() != providerKind()) {
            return failure(startedAt, "OLLAMA_PROVIDER_MISMATCH");
        }
        try {
            endpointPolicy.verify(request.endpoint());
        } catch (UnsafeOutboundEndpointException exception) {
            return failure(startedAt, "OLLAMA_ENDPOINT_REJECTED");
        }
        try {
            callModel(request);
            return new ProviderProbeObservation(
                    true,
                    detectedCapabilities(request),
                    elapsedSince(startedAt),
                    "OLLAMA_OK");
        } catch (RuntimeException exception) {
            return failure(startedAt, "OLLAMA_CALL_FAILED");
        }
    }

    private static void callModel(ProviderProbeRequest request) {
        var api = OllamaApi.builder()
                .baseUrl(request.endpoint().toString())
                .build();
        if (request.requestedCapabilities().contains(ModelCapability.EMBEDDING)) {
            var model = OllamaEmbeddingModel.builder()
                    .ollamaApi(api)
                    .options(OllamaEmbeddingOptions.builder()
                            .model(request.modelCode())
                            .build())
                    .build();
            model.embed(PROBE_PROMPT);
            return;
        }
        var model = OllamaChatModel.builder()
                .ollamaApi(api)
                .options(OllamaChatOptions.builder()
                        .model(request.modelCode())
                        .build())
                .build();
        model.call(PROBE_PROMPT);
    }

    private static Set<ModelCapability> detectedCapabilities(ProviderProbeRequest request) {
        return request.requestedCapabilities().contains(ModelCapability.EMBEDDING)
                ? EMBEDDING_CAPABILITIES
                : CHAT_CAPABILITIES;
    }

    private static ProviderProbeObservation failure(long startedAt, String detailCode) {
        return new ProviderProbeObservation(
                false,
                Set.of(),
                elapsedSince(startedAt),
                detailCode);
    }

    private static Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt));
    }
}
