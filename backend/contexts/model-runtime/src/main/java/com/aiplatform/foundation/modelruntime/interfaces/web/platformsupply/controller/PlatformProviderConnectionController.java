package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.CreatePlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderConnectionView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.query.ListPlatformProviderConnectionsQuery;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionService;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request.PlatformProviderConnectionRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.request.ProviderConnectionProbeRequest;
import java.time.Duration;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 平台范围连接的创建、只读详情、分页与独立探测入口。 */
@RestController
@RequestMapping("/api/v1/platform/model-supply/connections")
@ConditionalOnBean(PlatformProviderConnectionService.class)
public class PlatformProviderConnectionController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final PlatformProviderConnectionService connections;

    public PlatformProviderConnectionController(PlatformProviderConnectionService connections) {
        this.connections = Objects.requireNonNull(connections, "平台供应商连接用例不能为空");
    }

    @PostMapping
    public ResponseEntity<PlatformProviderConnectionView> create(
            PlatformRequestContext context,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody PlatformProviderConnectionRequest request) {
        var result = withCredentials(request, (apiKey, accessKeyId, secretAccessKey) -> connections.create(
                new CreatePlatformProviderConnectionCommand(
                        context,
                        requireIdempotencyKey(idempotencyKey),
                        request.providerKind(),
                        request.discoveryProfile(),
                        new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection(
                                request.providerPresetId(), request.providerPresetVersion(), request.capability(), request.workspaceId()),
                        request.displayName(),
                        request.endpoint(),
                        request.region(),
                        request.authenticationType(),
                        apiKey,
                        accessKeyId,
                        secretAccessKey)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.providerConnection());
    }

    @GetMapping
    public PageResult<PlatformProviderConnectionView> list(
            PlatformRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(defaultValue = "") String q,
            @RequestParam org.springframework.util.MultiValueMap<String, String> parameters) {
        if (parameters.entrySet().stream().anyMatch(entry -> !java.util.Set.of("pageNumber", "pageSize", "q").contains(entry.getKey()) || entry.getValue().size() != 1)) {
            throw new IllegalArgumentException("平台连接目录不接受范围、重复或未知参数");
        }
        return connections.list(new ListPlatformProviderConnectionsQuery(
                context, new PageQuery(pageNumber, pageSize), q));
    }

    /** 全目录发布摘要只接受可信平台会话，不接受筛选、分页或范围覆盖。 */
    @GetMapping("/summary")
    public com.aiplatform.foundation.modelruntime.application.platformsupply.dto.directory.PlatformProviderConnectionDirectorySummary summary(
            PlatformRequestContext context, @RequestParam java.util.Map<String, String> parameters) {
        if (!parameters.isEmpty()) throw new IllegalArgumentException("平台连接摘要不接受查询参数");
        return connections.summary(context);
    }

    /** 对象标识只用于定位；拒绝外部范围及未知参数，身份由可信平台会话提供。 */
    @GetMapping("/{providerConnectionId}")
    public PlatformProviderConnectionView get(PlatformRequestContext context,
            @PathVariable String providerConnectionId, @RequestParam java.util.Map<String, String> query) {
        if (!query.isEmpty()) throw new IllegalArgumentException("平台连接详情不接受范围或未知参数");
        return connections.get(context, providerConnectionId);
    }

    /** 只读预设目录不接受客户端替换作用域、地址或目录版本。 */
    @GetMapping("/presets")
    public com.aiplatform.foundation.modelruntime.application.platformsupply.dto.preset.PlatformProviderPresetsView presets(
            PlatformRequestContext context, @RequestParam java.util.Map<String, String> query) {
        if (!query.isEmpty()) throw new IllegalArgumentException("平台连接预设不接受范围或未知参数");
        return connections.presets(context);
    }

    @PostMapping("/{providerConnectionId}/probe")
    public PlatformProviderConnectionView probe(
            PlatformRequestContext context,
            @org.springframework.web.bind.annotation.PathVariable String providerConnectionId,
            @RequestBody ProviderConnectionProbeRequest request) {
        return connections.probe(new ProbePlatformProviderConnectionCommand(
                context, providerConnectionId, request.expectedVersion(), request.modelCode(),
                Duration.ofMillis(request.timeoutMillis())));
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供幂等键");
        }
        return value;
    }

    private static <T> T withCredentials(
            PlatformProviderConnectionRequest request,
            CredentialAction<T> action) {
        try (var apiKey = secret(request.apiKey(), "API Key不能为空");
                var accessKeyId = optionalSecret(request.discoveryAccessKeyId());
                var secretAccessKey = optionalSecret(request.discoverySecretAccessKey())) {
            return action.apply(apiKey, accessKeyId, secretAccessKey);
        }
    }

    private static SecretValue secret(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return SecretValue.copyOf(value.toCharArray());
    }

    private static SecretValue optionalSecret(String value) {
        return value == null ? null : secret(value, "发现 AK/SK 不能为空");
    }

    @FunctionalInterface
    private interface CredentialAction<T> {
        T apply(SecretValue apiKey, SecretValue accessKeyId, SecretValue secretAccessKey);
    }
}
