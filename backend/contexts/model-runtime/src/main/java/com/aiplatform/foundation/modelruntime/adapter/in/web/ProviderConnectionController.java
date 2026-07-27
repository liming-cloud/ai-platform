package com.aiplatform.foundation.modelruntime.adapter.in.web;

import com.aiplatform.foundation.modelruntime.api.catalog.ProviderConnectionUseCase;
import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.CreateProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.ListProviderConnectionsQuery;
import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.ProbeProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.ProbeProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.ProviderConnectionPage;
import com.aiplatform.foundation.modelruntime.api.catalog.model.provider.ProviderConnectionView;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
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
 * 供应商连接创建、列表和探测入口。
 */
@RestController
@RequestMapping("/api/v1/model-providers")
@ConditionalOnBean(ProviderConnectionUseCase.class)
public class ProviderConnectionController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final ProviderConnectionUseCase providerConnections;

    public ProviderConnectionController(ProviderConnectionUseCase providerConnections) {
        this.providerConnections = Objects.requireNonNull(
                providerConnections,
                "供应商连接用例不能为空");
    }

    @PostMapping
    public ResponseEntity<ProviderConnectionView> create(
            TrustedRequestContext context,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody ProviderConnectionRequest request) {
        var result = providerConnections.create(new CreateProviderConnectionCommand(
                context,
                requireIdempotencyKey(idempotencyKey),
                request.providerKind(),
                request.displayName(),
                request.endpoint(),
                request.region(),
                request.authenticationType(),
                request.credentialReference()));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.providerConnection());
    }

    @GetMapping
    public ProviderConnectionPage list(
            TrustedRequestContext context,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return providerConnections.list(new ListProviderConnectionsQuery(
                context,
                cursor,
                limit));
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

    private static String requireIdempotencyKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供幂等键");
        }
        return value;
    }
}
