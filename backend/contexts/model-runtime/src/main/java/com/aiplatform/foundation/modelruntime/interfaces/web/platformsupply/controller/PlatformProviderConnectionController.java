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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 平台范围连接的创建和分页读取入口。 */
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
            @RequestParam(defaultValue = "20") long pageSize) {
        return connections.list(new ListPlatformProviderConnectionsQuery(
                context, new PageQuery(pageNumber, pageSize)));
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
