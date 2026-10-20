package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.ProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformModelDeploymentProbeRequest;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import com.aiplatform.foundation.security.secret.SecretResolver;
import com.aiplatform.foundation.security.secret.SecretReference;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.openai.errors.OpenAIException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import java.net.URI;
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

    // 当前探测只执行非流式最小调用；未执行流式协议就不得声明 `STREAMING_CHAT`。
    private static final Set<ModelCapability> CHAT_CAPABILITIES =
            Set.of(ModelCapability.CHAT);
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

    /** 执行一次单能力最小探测，拒绝未验证的流式、重排及混合意图。 */
    @Override
    public ProviderProbeObservation probe(ProviderProbeRequest request) {
        Objects.requireNonNull(request, "探测请求不能为空");
        return probe(request.providerKind(), request.endpoint(), request.credentialReference(), request.modelCode(),
                request.requestedCapabilities(), request.timeout());
    }

    // 平台适配器只注入平台解析器，不借助虚构租户或租户范围请求复用协议实现。
    ProviderProbeObservation probePlatform(PlatformModelDeploymentProbeRequest request) {
        Objects.requireNonNull(request, "平台探测请求不能为空");
        return probe(request.providerKind(), request.endpoint(), request.credentialReference(), request.modelCode(),
                request.requestedCapabilities(), request.timeout());
    }

    private ProviderProbeObservation probe(ProviderKind kind, URI endpoint, String credentialReference,
            String modelCode, Set<ModelCapability> capabilities, Duration timeout) {
        var startedAt = System.nanoTime();
        if (kind != providerKind()) {
            return failure(startedAt, "OPENAI_COMPATIBLE_PROVIDER_MISMATCH");
        }
        // OpenAI-compatible 不是重排标准；不允许混合意图落入默认 Chat 分支。
        if (!CHAT_CAPABILITIES.equals(capabilities) && !EMBEDDING_CAPABILITIES.equals(capabilities))
            return failure(startedAt, "OPENAI_COMPATIBLE_CAPABILITY_UNSUPPORTED");

        try {
            endpointPolicy.verify(endpoint);
        } catch (UnsafeOutboundEndpointException exception) {
            return failure(startedAt, "OPENAI_COMPATIBLE_ENDPOINT_REJECTED");
        }

        final SecretValue secret;
        try {
            secret = Objects.requireNonNull(
                    secretResolver.resolve(new SecretReference(
                            credentialReference)),
                    "密钥解析结果不能为空");
        } catch (RuntimeException exception) {
            return failure(startedAt, "OPENAI_COMPATIBLE_SECRET_RESOLUTION_FAILED");
        }

        try (secret) {
            if (!secret.use(chars -> callModel(endpoint, modelCode, capabilities, timeout, new String(chars))))
                return failure(startedAt, "OPENAI_COMPATIBLE_RESPONSE_INVALID");
            return ProviderProbeObservation.of(
                    true,
                    capabilities,
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

    private static boolean callModel(URI endpoint, String modelCode, Set<ModelCapability> capabilities,
            Duration timeout, String apiKey) {
        if (EMBEDDING_CAPABILITIES.equals(capabilities)) {
            return callEmbeddingModel(endpoint, modelCode, timeout, apiKey);
        }
        return callChatModel(endpoint, modelCode, timeout, apiKey);
    }

    private static boolean callChatModel(URI endpoint, String modelCode, Duration timeout, String apiKey) {
        var options = OpenAiChatOptions.builder()
                .baseUrl(endpoint.toString())
                .apiKey(apiKey)
                .model(modelCode)
                .timeout(timeout)
                .maxRetries(0)
                // 同时限制回答与推理 Token；厂商不支持此字段时失败关闭，不重试无界请求。
                .maxCompletionTokens(64)
                .build();
        var model = OpenAiChatModel.builder()
                .options(options)
                .build();

        var output = model.call(PROBE_PROMPT);
        return output != null && !output.isBlank();
    }

    private static boolean callEmbeddingModel(URI endpoint, String modelCode, Duration timeout, String apiKey) {
        var options = OpenAiEmbeddingOptions.builder()
                .baseUrl(endpoint.toString())
                .apiKey(apiKey)
                .model(modelCode)
                .timeout(timeout)
                .maxRetries(0)
                .build();
        var model = OpenAiEmbeddingModel.builder()
                .options(options)
                .build();
        var vector = model.embed(PROBE_PROMPT);
        if (vector == null || vector.length == 0) return false;
        var nonZero = false;
        for (var value : vector) {
            if (!Float.isFinite(value)) return false;
            nonZero |= value != 0;
        }
        return nonZero;
    }

    private static ProviderProbeObservation failure(long startedAt, String detailCode) {
        return ProviderProbeObservation.of(
                false,
                Set.of(),
                elapsedSince(startedAt),
                detailCode);
    }

    private static Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt));
    }
}
