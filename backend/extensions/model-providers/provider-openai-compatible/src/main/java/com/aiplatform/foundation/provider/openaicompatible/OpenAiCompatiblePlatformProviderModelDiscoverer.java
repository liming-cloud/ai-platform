package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderCredentialResolver;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** 使用 OpenAI 官方 `GET /models` 契约发现平台模型候选。 */
public final class OpenAiCompatiblePlatformProviderModelDiscoverer implements PlatformProviderModelDiscoverer {

    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PlatformProviderCredentialResolver secretResolver;
    private final OutboundEndpointPolicy endpointPolicy;
    private final HttpClient httpClient;

    public OpenAiCompatiblePlatformProviderModelDiscoverer(
            PlatformProviderCredentialResolver secretResolver,
            OutboundEndpointPolicy endpointPolicy) {
        this(secretResolver, endpointPolicy, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    OpenAiCompatiblePlatformProviderModelDiscoverer(
            PlatformProviderCredentialResolver secretResolver,
            OutboundEndpointPolicy endpointPolicy,
            HttpClient httpClient) {
        this.secretResolver = Objects.requireNonNull(secretResolver, "密钥解析器不能为空");
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "出站地址策略不能为空");
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP 客户端不能为空");
    }

    @Override
    public ProviderKind providerKind() {
        return ProviderKind.OPENAI_COMPATIBLE;
    }

    @Override
    public PlatformModelDiscoveryProfile discoveryProfile() {
        return PlatformModelDiscoveryProfile.OPENAI_MODELS_API;
    }

    @Override
    public PlatformProviderModelDiscoveryObservation discover(PlatformProviderModelDiscoveryRequest request) {
        Objects.requireNonNull(request, "平台模型发现请求不能为空");
        var startedAt = System.nanoTime();
        if (request.providerKind() != providerKind() || request.discoveryProfile() != discoveryProfile()) {
            return failure(startedAt, "OPENAI_COMPATIBLE_DISCOVERY_PROFILE_MISMATCH");
        }
        try {
            endpointPolicy.verify(request.endpoint());
        } catch (UnsafeOutboundEndpointException exception) {
            return failure(startedAt, "OPENAI_COMPATIBLE_DISCOVERY_ENDPOINT_REJECTED");
        }

        final SecretValue secret;
        try {
            secret = Objects.requireNonNull(
                    secretResolver.resolve(request.credentialReference()),
                    "密钥解析结果不能为空");
        } catch (RuntimeException exception) {
            return failure(startedAt, "OPENAI_COMPATIBLE_DISCOVERY_SECRET_RESOLUTION_FAILED");
        }

        try (secret) {
            return secret.use(chars -> {
                try {
                    return execute(request, chars, startedAt);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new DiscoveryCallException(exception);
                } catch (IOException exception) {
                    throw new DiscoveryCallException(exception);
                }
            });
        } catch (RuntimeException exception) {
            return failure(startedAt, "OPENAI_COMPATIBLE_DISCOVERY_CALL_FAILED");
        }
    }

    private PlatformProviderModelDiscoveryObservation execute(
            PlatformProviderModelDiscoveryRequest request,
            char[] secret,
            long startedAt) throws IOException, InterruptedException {
        var httpRequest = HttpRequest.newBuilder(modelsUri(request.endpoint()))
                .timeout(request.timeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + new String(secret))
                .GET()
                .build();
        var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return failure(startedAt, "OPENAI_COMPATIBLE_DISCOVERY_AUTHENTICATION_FAILED");
            }
            if (response.statusCode() == 429) {
                return failure(startedAt, "OPENAI_COMPATIBLE_DISCOVERY_RATE_LIMITED");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return failure(startedAt, "OPENAI_COMPATIBLE_DISCOVERY_CALL_FAILED");
            }
            var bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                return failure(startedAt, "OPENAI_COMPATIBLE_DISCOVERY_RESPONSE_INVALID");
            }
            var candidates = parseCandidates(bytes);
            if (candidates.isEmpty()) {
                return failure(startedAt, "OPENAI_COMPATIBLE_DISCOVERY_RESPONSE_INVALID");
            }
            return new PlatformProviderModelDiscoveryObservation(
                    true,
                    candidates,
                    "OPENAI_COMPATIBLE_DISCOVERY_OK",
                    elapsedSince(startedAt),
                    Instant.now());
        }
    }

    private static Set<PlatformDiscoveredModelCandidate> parseCandidates(byte[] bytes) throws IOException {
        JsonNode root = JSON.readTree(bytes);
        JsonNode data = root == null ? null : root.get("data");
        if (data == null || !data.isArray()) {
            throw new IOException("供应商模型列表格式不正确");
        }
        var candidates = new LinkedHashSet<PlatformDiscoveredModelCandidate>();
        for (var item : data) {
            var id = item == null ? null : item.get("id");
            if (id == null || !id.isTextual()) {
                throw new IOException("供应商模型标识格式不正确");
            }
            candidates.add(PlatformDiscoveredModelCandidate.requiresAdministratorConfirmation(id.textValue()));
        }
        return Set.copyOf(candidates);
    }

    private static URI modelsUri(URI endpoint) {
        var value = endpoint.toString();
        return URI.create(value.endsWith("/") ? value + "models" : value + "/models");
    }

    private static PlatformProviderModelDiscoveryObservation failure(long startedAt, String resultCode) {
        return new PlatformProviderModelDiscoveryObservation(
                false, Set.of(), resultCode, elapsedSince(startedAt), Instant.now());
    }

    private static Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt));
    }

    private static final class DiscoveryCallException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private DiscoveryCallException(Exception cause) {
            super(cause);
        }
    }
}
