package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.api.extension.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.api.model.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.api.model.ProviderProbeRequest;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import com.aiplatform.foundation.security.secret.SecretResolver;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.openai.errors.OpenAIException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

/**
 * 使用 Spring AI 执行最小真实调用的 OpenAI 兼容协议探测器。
 */
public final class OpenAiCompatibleConnectionProbe implements ProviderConnectionProbe {

    private static final Set<ModelCapability> CHAT_CAPABILITIES =
            Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT);
    private static final Set<ModelCapability> EMBEDDING_CAPABILITIES =
            Set.of(ModelCapability.EMBEDDING);
    private static final String PROBE_PROMPT = "只回复 OK。";

    private final SecretResolver secretResolver;
    private final OutboundEndpointPolicy endpointPolicy;

    public OpenAiCompatibleConnectionProbe(
            SecretResolver secretResolver,
            OutboundEndpointPolicy endpointPolicy) {
        this.secretResolver = Objects.requireNonNull(secretResolver, "密钥解析器不能为空");
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "出站地址策略不能为空");
    }

    @Override
    public ProviderKind providerKind() {
        return ProviderKind.OPENAI_COMPATIBLE;
    }

    @Override
    public ProviderProbeObservation probe(ProviderProbeRequest request) {
        Objects.requireNonNull(request, "探测请求不能为空");
        var startedAt = System.nanoTime();
        if (request.providerKind() != providerKind()) {
            return failure(startedAt, "OPENAI_COMPATIBLE_PROVIDER_MISMATCH");
        }

        try {
            endpointPolicy.verify(request.endpoint());
        } catch (UnsafeOutboundEndpointException exception) {
            return failure(startedAt, "OPENAI_COMPATIBLE_ENDPOINT_REJECTED");
        }

        final SecretValue secret;
        try {
            secret = Objects.requireNonNull(
                    secretResolver.resolve(request.credentialReference()),
                    "密钥解析结果不能为空");
        } catch (RuntimeException exception) {
            return failure(startedAt, "OPENAI_COMPATIBLE_SECRET_RESOLUTION_FAILED");
        }

        try (secret) {
            secret.use(chars -> {
                callModel(request, new String(chars));
                return null;
            });
            return new ProviderProbeObservation(
                    true,
                    detectedCapabilities(request),
                    elapsedSince(startedAt),
                    "OPENAI_COMPATIBLE_OK");
        } catch (UnauthorizedException | PermissionDeniedException exception) {
            return failure(startedAt, "OPENAI_COMPATIBLE_AUTHENTICATION_FAILED");
        } catch (RateLimitException exception) {
            return failure(startedAt, "OPENAI_COMPATIBLE_RATE_LIMITED");
        } catch (OpenAIException exception) {
            return failure(startedAt, "OPENAI_COMPATIBLE_CALL_FAILED");
        } catch (RuntimeException exception) {
            return failure(startedAt, "OPENAI_COMPATIBLE_CALL_FAILED");
        }
    }

    private static void callModel(ProviderProbeRequest request, String apiKey) {
        if (request.requestedCapabilities().contains(ModelCapability.EMBEDDING)) {
            callEmbeddingModel(request, apiKey);
            return;
        }
        callChatModel(request, apiKey);
    }

    private static void callChatModel(ProviderProbeRequest request, String apiKey) {
        var options = OpenAiChatOptions.builder()
                .baseUrl(request.endpoint().toString())
                .apiKey(apiKey)
                .model(request.modelCode())
                .timeout(request.timeout())
                .maxRetries(0)
                .build();
        var model = OpenAiChatModel.builder()
                .options(options)
                .build();

        model.call(PROBE_PROMPT);
    }

    private static void callEmbeddingModel(ProviderProbeRequest request, String apiKey) {
        var options = OpenAiEmbeddingOptions.builder()
                .baseUrl(request.endpoint().toString())
                .apiKey(apiKey)
                .model(request.modelCode())
                .timeout(request.timeout())
                .maxRetries(0)
                .build();
        var model = OpenAiEmbeddingModel.builder()
                .options(options)
                .build();
        model.embed(PROBE_PROMPT);
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
