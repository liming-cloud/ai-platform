package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelCandidateClassification;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelCandidateSource;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderCredentialResolver;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/** ADR-0101 的硅基流动官方重排列表适配；不从运行地址推导发现地址或猜测模型类型。 */
public final class SiliconFlowRerankModelDiscoverer implements PlatformProviderModelDiscoverer {
    static final URI LIST_SOURCE = URI.create("https://api.siliconflow.cn/v1/models?sub_type=reranker");
    static final String MAPPING_VERSION = PlatformModelCandidateSource.CURRENT_MAPPING_VERSION;
    static final String MAPPING_DIGEST = PlatformModelCandidateSource.CURRENT_MAPPING_DIGEST;
    private static final URI RUNTIME_ENDPOINT = URI.create("https://api.siliconflow.cn/v1/rerank");
    private static final int MAX_BYTES = 1_048_576;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final PlatformProviderCredentialResolver credentials;
    private final OutboundEndpointPolicy egress;
    private final URI listEndpoint;

    /** 生产只使用固定官方地址；平台凭据不读取到响应、异常或候选中。 */
    public SiliconFlowRerankModelDiscoverer(PlatformProviderCredentialResolver credentials, OutboundEndpointPolicy egress) {
        this(credentials, egress, LIST_SOURCE);
    }

    // 包内测试仅把传输指向隔离 HTTP 服务，不提供运行时配置或公共地址覆盖入口。
    SiliconFlowRerankModelDiscoverer(PlatformProviderCredentialResolver credentials, OutboundEndpointPolicy egress, URI listEndpoint) {
        this.credentials = Objects.requireNonNull(credentials, "平台凭据解析器不能为空");
        this.egress = Objects.requireNonNull(egress, "出站策略不能为空");
        this.listEndpoint = Objects.requireNonNull(listEndpoint, "发现传输地址不能为空");
    }

    @Override
    public ProviderKind providerKind() { return ProviderKind.OPENAI_COMPATIBLE; }

    @Override
    public PlatformModelDiscoveryProfile discoveryProfile() { return PlatformModelDiscoveryProfile.SILICONFLOW_RERANK_MODELS_API; }

    @Override
    public PlatformProviderModelDiscoveryObservation discover(PlatformProviderModelDiscoveryRequest request) {
        Objects.requireNonNull(request, "发现请求不能为空");
        long started = System.nanoTime();
        if (request.providerKind() != providerKind() || request.discoveryProfile() != discoveryProfile()
                || !RUNTIME_ENDPOINT.equals(request.endpoint()) || !"cn".equals(request.region())) {
            return outcome(started, false, Set.of(), "PROFILE_MISMATCH");
        }
        // 出站校验先于凭据解析，固定列表地址的 SSRF 拒绝不能被运行地址校验代替。
        try {
            egress.verify(LIST_SOURCE);
            try (var secret = credentials.resolve(request.credentialReference())) {
                return secret.use(chars -> execute(request.timeout(), chars, started));
            }
        } catch (UnsafeOutboundEndpointException exception) {
            return outcome(started, false, Set.of(), "ENDPOINT_REJECTED");
        } catch (RuntimeException exception) {
            return outcome(started, false, Set.of(), "CREDENTIAL_UNAVAILABLE");
        }
    }

    private PlatformProviderModelDiscoveryObservation execute(Duration timeout, char[] secret, long started) {
        // OkHttp 的总调用期限覆盖响应体读取；关闭连接恢复重试和两类重定向，最多发出一次正式 GET。
        var sent = new java.util.concurrent.atomic.AtomicBoolean();
        var client = new OkHttpClient.Builder().retryOnConnectionFailure(false)
                // OkHttp 即使关闭连接重试，仍可能遵循 503 Retry-After: 0；网络拦截器拒绝第二次实际发送。
                .addNetworkInterceptor(chain -> {
                    if (!sent.compareAndSet(false, true)) throw new java.net.ProtocolException("发现请求不允许自动重发");
                    return chain.proceed(chain.request());
                })
                .followRedirects(false).followSslRedirects(false)
                .connectTimeout(timeout).readTimeout(timeout).callTimeout(timeout).build();
        var request = new Request.Builder().url(listEndpoint.toString()).get()
                .header("Accept", "application/json").header("Authorization", "Bearer " + new String(secret)).build();
        try (var response = client.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) return outcome(started, false, Set.of(), "AUTHENTICATION_FAILED");
            if (response.code() == 429) return outcome(started, false, Set.of(), "RATE_LIMITED");
            if (response.code() != 200) return outcome(started, false, Set.of(), "HTTP_REJECTED");
            var body = response.body();
            if (body == null) return outcome(started, false, Set.of(), "INVALID_RESPONSE");
            byte[] bytes = body.byteStream().readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) return outcome(started, false, Set.of(), "RESPONSE_TOO_LARGE");
            var root = JSON.readTree(bytes);
            var data = root == null ? null : root.get("data");
            if (data == null || !data.isArray() || data.size() > 2000) return outcome(started, false, Set.of(), "INVALID_RESPONSE");
            var credentialForms = credentialForms(secret);
            var models = new LinkedHashSet<PlatformDiscoveredModelCandidate>();
            var codes = new LinkedHashSet<String>();
            var source = new PlatformModelCandidateSource(discoveryProfile().name(), LIST_SOURCE.toString(), MAPPING_VERSION, MAPPING_DIGEST);
            for (var item : data) {
                var id = item.get("id");
                if (id == null || !id.isTextual() || !codes.add(id.textValue())) return outcome(started, false, Set.of(), "INVALID_RESPONSE");
                var code = id.textValue();
                if (code.codePoints().anyMatch(Character::isISOControl) || credentialForms.stream().anyMatch(
                        value -> code.toLowerCase(java.util.Locale.ROOT).contains(value))) return outcome(started, false, Set.of(), "INVALID_RESPONSE");
                boolean mapped = "BAAI/bge-reranker-v2-m3".equals(code);
                models.add(new PlatformDiscoveredModelCandidate(code, mapped ? ModelType.RERANK : null,
                        mapped ? Set.of(ModelCapability.RERANK) : Set.of(), mapped
                        ? PlatformModelCandidateClassification.CONFIRMED_BY_CONTROLLED_MAPPING
                        : PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION, null, source));
            }
            return outcome(started, true, models, "OK");
        } catch (java.io.InterruptedIOException exception) {
            return outcome(started, false, Set.of(), "TIMEOUT");
        } catch (IOException | IllegalArgumentException exception) {
            return outcome(started, false, Set.of(), "INVALID_RESPONSE");
        } finally {
            // 每次发现独占短生命期客户端，关闭可重建网络资源，不持有 API Key 或连接缓存。
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdown();
        }
    }

    private static PlatformProviderModelDiscoveryObservation outcome(long started, boolean succeeded,
            Set<PlatformDiscoveredModelCandidate> candidates, String suffix) {
        return new PlatformProviderModelDiscoveryObservation(succeeded, candidates, "SILICONFLOW_RERANK_DISCOVERY_" + suffix,
                Duration.ofNanos(Math.max(0, System.nanoTime() - started)), Instant.now());
    }

    // 先解码 JSON 再检查认证回显；普通、表单与两种 Base64 表达均只能导致失败，不能成为模型元数据。
    private static Set<String> credentialForms(char[] secret) {
        var raw = new String(secret);
        if (raw.isEmpty()) throw new IllegalArgumentException("平台凭据不能为空");
        var percent = new StringBuilder();
        for (byte value : raw.getBytes(StandardCharsets.UTF_8)) percent.append(String.format(java.util.Locale.ROOT, "%%%02X", value & 255));
        var result = new LinkedHashSet<String>();
        for (var value : java.util.List.of(raw, percent.toString(), java.net.URLEncoder.encode(raw, StandardCharsets.UTF_8), "Bearer " + raw)) {
            result.add(value);
            for (var encoder : java.util.List.of(java.util.Base64.getEncoder(), java.util.Base64.getUrlEncoder())) {
                var encoded = encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
                result.add(encoded); result.add(encoded.replace("=", ""));
                result.add(java.net.URLEncoder.encode(encoded, StandardCharsets.UTF_8));
            }
        }
        return result.stream().map(value -> value.toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

}
