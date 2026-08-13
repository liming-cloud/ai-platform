package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.DiscoverPlatformProviderModelsCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.DiscoverPlatformProviderModelsResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderModelDiscoveryService;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.request.ProviderModelDiscoveryRequest;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台范围连接模型发现入口；身份只从平台会话解析。 */
@RestController
@RequestMapping("/api/v1/platform/model-supply/connections")
@ConditionalOnBean(PlatformProviderModelDiscoveryService.class)
public class PlatformProviderModelDiscoveryController {

    private final PlatformProviderModelDiscoveryService discoveries;

    public PlatformProviderModelDiscoveryController(PlatformProviderModelDiscoveryService discoveries) {
        this.discoveries = Objects.requireNonNull(discoveries, "平台模型发现用例不能为空");
    }

    @PostMapping("/{providerConnectionId}/model-discovery")
    public DiscoverPlatformProviderModelsResult discover(
            PlatformRequestContext context,
            @PathVariable String providerConnectionId,
            @RequestBody ProviderModelDiscoveryRequest request) {
        return discoveries.discover(new DiscoverPlatformProviderModelsCommand(
                context,
                providerConnectionId,
                request.expectedVersion(),
                Duration.ofMillis(request.timeoutMillis())));
    }
}
