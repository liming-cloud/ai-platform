package com.aiplatform.foundation.modelruntime.interfaces.web.provider.controller;

import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderConnectionService;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderModelDiscoveryService;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderModelDiscoveryReadService;
import com.aiplatform.foundation.modelruntime.application.provider.command.CreateProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.provider.command.DiscoverProviderModelsCommand;
import com.aiplatform.foundation.modelruntime.application.provider.query.ListProviderConnectionsQuery;
import com.aiplatform.foundation.modelruntime.application.provider.command.ProbeProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.provider.command.RotateProviderConnectionCredentialCommand;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProbeProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderConnectionView;
import com.aiplatform.foundation.modelruntime.application.provider.dto.DiscoverProviderModelsResult;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryView;
import com.aiplatform.foundation.modelruntime.application.provider.query.ListProviderModelDiscoveriesQuery;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.request.ProviderConnectionProbeRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.request.ProviderConnectionRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.request.ProviderConnectionCredentialRotateRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.request.ProviderModelDiscoveryRequest;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Duration;
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

/**
 * 供应商连接创建、列表、探测和受控模型发现入口。
 */
@RestController
@RequestMapping("/api/v1/model-providers")
@ConditionalOnBean({
    ProviderConnectionService.class,
    ProviderModelDiscoveryService.class,
    ProviderModelDiscoveryReadService.class
})
public class ProviderConnectionController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final ProviderConnectionService providerConnections;
    private final ProviderModelDiscoveryService modelDiscoveries;
    private final ProviderModelDiscoveryReadService discoveryHistory;

    public ProviderConnectionController(
            ProviderConnectionService providerConnections,
            ProviderModelDiscoveryService modelDiscoveries,
            ProviderModelDiscoveryReadService discoveryHistory) {
        this.providerConnections = Objects.requireNonNull(
                providerConnections,
                "供应商连接用例不能为空");
        this.modelDiscoveries = Objects.requireNonNull(
                modelDiscoveries,
                "模型发现用例不能为空");
        this.discoveryHistory = Objects.requireNonNull(
                discoveryHistory,
                "模型发现历史读取用例不能为空");
    }

    @PostMapping
    public ResponseEntity<ProviderConnectionView> create(
            TrustedRequestContext context,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody ProviderConnectionRequest request) {
        var result = withApiKey(request.apiKey(), apiKey -> providerConnections.create(
                new CreateProviderConnectionCommand(
                        context,
                        requireIdempotencyKey(idempotencyKey),
                        request.providerKind(),
                        request.displayName(),
                        request.endpoint(),
                        request.region(),
                        request.authenticationType(),
                        apiKey)));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.providerConnection());
    }

    @PostMapping("/{providerConnectionId}/credential-rotation")
    public ProviderConnectionView rotateCredential(
            TrustedRequestContext context,
            @PathVariable String providerConnectionId,
            @RequestBody ProviderConnectionCredentialRotateRequest request) {
        return withApiKey(request.apiKey(), apiKey -> providerConnections.rotateCredential(
                new RotateProviderConnectionCredentialCommand(
                        context, providerConnectionId, request.expectedVersion(), apiKey))
                .providerConnection());
    }

    @GetMapping
    public PageResult<ProviderConnectionView> list(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return providerConnections.list(new ListProviderConnectionsQuery(
                context,
                new PageQuery(pageNumber, pageSize)));
    }

    @PostMapping("/{providerConnectionId}/probe")
    public ProbeProviderConnectionResult probe(
            TrustedRequestContext context,
            @PathVariable String providerConnectionId,
            @RequestBody ProviderConnectionProbeRequest request) {
        return providerConnections.probe(new ProbeProviderConnectionCommand(
                context,
                providerConnectionId,
                request.expectedVersion(),
                request.modelCode(),
                Duration.ofMillis(request.timeoutMillis())));
    }

    @PostMapping("/{providerConnectionId}/model-discovery")
    public DiscoverProviderModelsResult discoverModels(
            TrustedRequestContext context,
            @PathVariable String providerConnectionId,
            @RequestBody ProviderModelDiscoveryRequest request) {
        return modelDiscoveries.discover(new DiscoverProviderModelsCommand(
                context,
                providerConnectionId,
                request.expectedVersion(),
                Duration.ofMillis(request.timeoutMillis())));
    }

    @GetMapping("/{providerConnectionId}/model-discoveries")
    public PageResult<ProviderModelDiscoveryView> listDiscoveries(
            TrustedRequestContext context,
            @PathVariable String providerConnectionId,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return discoveryHistory.list(new ListProviderModelDiscoveriesQuery(
                context,
                providerConnectionId,
                new PageQuery(pageNumber, pageSize)));
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供幂等键");
        }
        return value;
    }

    private static <T> T withApiKey(
            String value,
            java.util.function.Function<SecretValue, T> action) {
        Objects.requireNonNull(value, "API Key不能为空");
        try (var apiKey = SecretValue.copyOf(value.toCharArray())) {
            return action.apply(apiKey);
        }
    }
}
