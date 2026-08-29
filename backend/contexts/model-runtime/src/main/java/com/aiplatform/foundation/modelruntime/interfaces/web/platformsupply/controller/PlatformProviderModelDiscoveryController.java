package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.DiscoverPlatformProviderModelsCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.DiscoverPlatformProviderModelsResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderModelDiscoveryService;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.request.ProviderModelDiscoveryRequest;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    public PlatformProviderModelDiscoveryResponse discover(
            PlatformRequestContext context,
            @PathVariable String providerConnectionId,
            @RequestBody ProviderModelDiscoveryRequest request) {
        var result = discoveries.discover(new DiscoverPlatformProviderModelsCommand(
                context,
                providerConnectionId,
                request.expectedVersion(),
                Duration.ofMillis(request.timeoutMillis())));
        return PlatformProviderModelDiscoveryResponse.from(result);
    }

    /** 并发冲突只保留 HTTP 语义，不向浏览器暴露平台错误码、追踪标识或内部详情。 */
    @ExceptionHandler(ProviderConnectionConcurrentModificationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public PlatformProviderModelDiscoveryResponse concurrentModification() {
        return new PlatformProviderModelDiscoveryResponse(false, Set.of());
    }

    /** 浏览器只接收业务结果和安全候选，不暴露内部发现标识、适配器结果码、耗时或供应商原始响应。 */
    public record PlatformProviderModelDiscoveryResponse(
            boolean succeeded,
            Set<PlatformDiscoveredModelCandidate> models) {

        public PlatformProviderModelDiscoveryResponse {
            models = Set.copyOf(Objects.requireNonNull(models, "安全模型候选不能为空"));
        }

        private static PlatformProviderModelDiscoveryResponse from(DiscoverPlatformProviderModelsResult result) {
            return new PlatformProviderModelDiscoveryResponse(result.succeeded(), result.models());
        }
    }
}
