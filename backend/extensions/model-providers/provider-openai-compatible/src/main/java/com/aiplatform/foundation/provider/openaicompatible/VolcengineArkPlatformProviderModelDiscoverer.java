package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderCredentialResolver;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/** 完整读取方舟 `default` 项目的两类接入点，仅产生待确认候选；任一来源失败不返回部分结果。 */
public final class VolcengineArkPlatformProviderModelDiscoverer implements PlatformProviderModelDiscoverer {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 100;
    private static final int MAX_CANDIDATES = 10_000;
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final Pattern REGION = Pattern.compile("[a-z]{2}-[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PlatformProviderCredentialResolver credentials;
    private final OutboundEndpointPolicy endpointPolicy;
    private final Clock clock;
    private final Function<String, URI> endpointResolver;
    private final HttpClient httpClient;

    public VolcengineArkPlatformProviderModelDiscoverer(
            PlatformProviderCredentialResolver credentials,
            OutboundEndpointPolicy endpointPolicy) {
        this(credentials, endpointPolicy, Clock.systemUTC(),
                region -> URI.create("https://ark." + region + ".volcengineapi.com"));
    }

    VolcengineArkPlatformProviderModelDiscoverer(
            PlatformProviderCredentialResolver credentials,
            OutboundEndpointPolicy endpointPolicy,
            Clock clock,
            Function<String, URI> endpointResolver) {
        this(credentials, endpointPolicy, clock, endpointResolver,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
    }

    VolcengineArkPlatformProviderModelDiscoverer(
            PlatformProviderCredentialResolver credentials,
            OutboundEndpointPolicy endpointPolicy,
            Clock clock,
            Function<String, URI> endpointResolver,
            HttpClient httpClient) {
        this.credentials = Objects.requireNonNull(credentials, "平台凭据解析器不能为空");
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "出站地址策略不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "方舟控制面地址解析器不能为空");
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP 客户端不能为空");
    }

    @Override
    public ProviderKind providerKind() {
        return ProviderKind.OPENAI_COMPATIBLE;
    }

    @Override
    public PlatformModelDiscoveryProfile discoveryProfile() {
        return PlatformModelDiscoveryProfile.VOLCENGINE_ARK_CONTROL_PLANE;
    }

    @Override
    public PlatformProviderModelDiscoveryObservation discover(PlatformProviderModelDiscoveryRequest request) {
        Objects.requireNonNull(request, "平台模型发现请求不能为空");
        var startedAt = System.nanoTime();
        if (request.providerKind() != providerKind() || request.discoveryProfile() != discoveryProfile()) {
            return failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_PROFILE_MISMATCH");
        }
        if (!REGION.matcher(request.region()).matches()) {
            return failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_ENDPOINT_REJECTED");
        }
        var endpoint = endpointResolver.apply(request.region());
        try {
            endpointPolicy.verify(endpoint);
        } catch (IllegalArgumentException exception) {
            return failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_ENDPOINT_REJECTED");
        }
        var references = request.discoveryCredentials().orElseThrow();
        final com.aiplatform.foundation.security.secret.SecretValue accessKeyId;
        final com.aiplatform.foundation.security.secret.SecretValue secretAccessKey;
        try {
            accessKeyId = credentials.resolve(references.accessKeyId());
            try {
                secretAccessKey = credentials.resolve(references.secretAccessKey());
            } catch (RuntimeException exception) {
                accessKeyId.close();
                throw exception;
            }
        } catch (RuntimeException exception) {
            return failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_SECRET_RESOLUTION_FAILED");
        }
        try (accessKeyId; secretAccessKey) {
            return accessKeyId.use(ak -> secretAccessKey.use(sk -> execute(
                    endpoint, request.region(), request.timeout(), ak, sk, startedAt)));
        } catch (RuntimeException exception) {
            return failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_CALL_FAILED");
        }
    }

    private PlatformProviderModelDiscoveryObservation execute(
            URI endpoint,
            String region,
            Duration timeout,
            char[] accessKeyId,
            char[] secretAccessKey,
            long startedAt) {
        var candidates = new LinkedHashSet<PlatformDiscoveredModelCandidate>();
        var requestedPages = 0;
        // 两个来源共同形成一次发现；共享请求上限，任一来源失败都不能提交部分候选。
        for (var action : List.of("ListEndpoints", "InnerDescribeModelEndpoints")) {
            var totalCount = -1;
            var pageSize = -1;
            for (var page = 1; ; page++) {
                if (++requestedPages > MAX_PAGES) {
                    return failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_RESPONSE_INVALID");
                }
                var pageResult = callPage(
                        endpoint, region, timeout, accessKeyId, secretAccessKey, action, page, startedAt);
                if (pageResult.failure() != null) {
                    return pageResult.failure();
                }
                if (page > 1 && (pageResult.totalCount() != totalCount || pageResult.pageSize() != pageSize)) {
                    return failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_RESPONSE_INVALID");
                }
                totalCount = pageResult.totalCount();
                pageSize = pageResult.pageSize();
                candidates.addAll(pageResult.candidates());
                if (candidates.size() > MAX_CANDIDATES) {
                    return failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_RESPONSE_INVALID");
                }
                if (pageResult.complete()) {
                    break;
                }
            }
        }
        return success(startedAt, candidates);
    }

    private PageResult callPage(
            URI endpoint,
            String region,
            Duration timeout,
            char[] accessKeyId,
            char[] secretAccessKey,
            String action,
            int page,
            long startedAt) {
        var body = ("{\"PageNumber\":" + page + ",\"PageSize\":" + PAGE_SIZE + ",\"ProjectName\":\"default\"}")
                .getBytes(StandardCharsets.UTF_8);
        var uri = URI.create(endpoint.toString()
                + (endpoint.toString().contains("?") ? "&" : "?")
                + "Action=" + action + "&Version=2024-01-01");
        try {
            endpointPolicy.verify(uri);
        } catch (IllegalArgumentException exception) {
            return PageResult.failed(failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_ENDPOINT_REJECTED"));
        }
        var signed = VolcengineSignerV4.sign(
                uri, "POST", body, accessKeyId, secretAccessKey, region, "ark", clock.instant());
        var request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-Date", signed.date())
                .header("X-Content-Sha256", signed.contentSha256())
                .header("Authorization", signed.authorization())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        final HttpResponse<java.io.InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PageResult.failed(failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_CALL_FAILED"));
        } catch (IOException exception) {
            return PageResult.failed(failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_CALL_FAILED"));
        }
        try (var stream = response.body()) {
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return PageResult.failed(failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_AUTHENTICATION_FAILED"));
            }
            if (response.statusCode() == 429) {
                return PageResult.failed(failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_RATE_LIMITED"));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return PageResult.failed(failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_CALL_FAILED"));
            }
            var bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                return PageResult.failed(failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_RESPONSE_INVALID"));
            }
            return parsePage(bytes, action.equals("InnerDescribeModelEndpoints"), page, startedAt);
        } catch (IOException | RuntimeException exception) {
            return PageResult.failed(failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_RESPONSE_INVALID"));
        }
    }

    private PageResult parsePage(byte[] bytes, boolean preset, int expectedPage, long startedAt) throws IOException {
        JsonNode root = JSON.readTree(bytes);
        if (root == null || root.path("ResponseMetadata").has("Error")) {
            return PageResult.failed(failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_CALL_FAILED"));
        }
        var result = root.get("Result");
        if (result == null || !result.isObject() || !result.path("Items").isArray()
                || !result.path("PageNumber").isIntegralNumber() || !result.path("PageNumber").canConvertToInt()
                || !result.path("PageSize").isIntegralNumber() || !result.path("PageSize").canConvertToInt()
                || !result.path("TotalCount").isIntegralNumber() || !result.path("TotalCount").canConvertToInt()) {
            throw new IOException("方舟 Endpoint 列表格式不正确");
        }
        var pageNumber = result.path("PageNumber").intValue();
        var pageSize = result.path("PageSize").intValue();
        var totalCount = result.path("TotalCount").intValue();
        if (pageNumber != expectedPage || pageSize <= 0 || pageSize > PAGE_SIZE
                || totalCount < 0 || totalCount > MAX_CANDIDATES) {
            throw new IOException("方舟 Endpoint 分页格式不正确");
        }
        var remaining = Math.max(0L, totalCount - (long) (pageNumber - 1) * pageSize);
        if (result.path("Items").size() != Math.min(pageSize, remaining)) {
            throw new IOException("方舟 Endpoint 分页记录不完整");
        }
        var candidates = new LinkedHashSet<PlatformDiscoveredModelCandidate>();
        for (var item : result.path("Items")) {
            if (item == null || !item.isObject() || !"default".equals(item.path("ProjectName").asText())) {
                throw new IOException("方舟 Endpoint 项目不匹配");
            }
            // 预置管理面 `Id` 不是推理编码；只使用官方 `ModelId`，不能按名称猜测类型和能力。
            var id = item.get(preset ? "ModelId" : "Id");
            if (id == null || !id.isTextual() || id.textValue().isBlank()) {
                throw new IOException("方舟 Endpoint 标识格式不正确");
            }
            if (preset) {
                var status = item.path("Status");
                if (!status.isTextual() || status.textValue().isBlank()) {
                    throw new IOException("方舟预置 Endpoint 状态格式不正确");
                }
                if (!status.textValue().equals("Running")) {
                    continue;
                }
            }
            candidates.add(PlatformDiscoveredModelCandidate.requiresAdministratorConfirmation(id.textValue()));
        }
        return new PageResult(Set.copyOf(candidates), totalCount, pageSize,
                (long) pageNumber * pageSize >= totalCount, null);
    }

    private PlatformProviderModelDiscoveryObservation success(
            long startedAt,
            Set<PlatformDiscoveredModelCandidate> candidates) {
        // 零候选不能建立可发布验证，须作为失败事实保存，避免触发领域成功记录的非空约束。
        if (candidates.isEmpty()) {
            return failure(startedAt, "VOLCENGINE_ARK_DISCOVERY_NO_MODELS");
        }
        return new PlatformProviderModelDiscoveryObservation(
                true, Set.copyOf(candidates), "VOLCENGINE_ARK_DISCOVERY_OK",
                elapsedSince(startedAt), clock.instant());
    }

    private PlatformProviderModelDiscoveryObservation failure(long startedAt, String resultCode) {
        return new PlatformProviderModelDiscoveryObservation(
                false, Set.of(), resultCode, elapsedSince(startedAt), clock.instant());
    }

    private static Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt));
    }

    private record PageResult(
            Set<PlatformDiscoveredModelCandidate> candidates,
            int totalCount,
            int pageSize,
            boolean complete,
            PlatformProviderModelDiscoveryObservation failure) {

        private static PageResult failed(PlatformProviderModelDiscoveryObservation failure) {
            return new PageResult(Set.of(), 0, 0, true, failure);
        }
    }
}
