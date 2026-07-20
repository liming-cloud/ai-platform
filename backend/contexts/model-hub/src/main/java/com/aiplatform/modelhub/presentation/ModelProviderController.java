package com.aiplatform.modelhub.presentation;

import com.aiplatform.modelhub.application.ModelConfigApplicationService;
import com.aiplatform.modelhub.application.ModelCreationApplicationService;
import com.aiplatform.modelhub.application.ModelProviderApplicationService;
import com.aiplatform.modelhub.application.command.CreateModelConfigCommand;
import com.aiplatform.modelhub.application.command.CreateModelProviderCommand;
import com.aiplatform.modelhub.application.command.DisableModelConfigCommand;
import com.aiplatform.modelhub.application.command.DisableModelProviderCommand;
import com.aiplatform.modelhub.application.command.UpdateModelConfigCommand;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.presentation.converter.ModelHubPresentationConverter;
import com.aiplatform.modelhub.presentation.dto.CreateModelConfigRequest;
import com.aiplatform.modelhub.presentation.dto.CreateModelProviderRequest;
import com.aiplatform.modelhub.presentation.dto.DisableModelResourceRequest;
import com.aiplatform.modelhub.presentation.dto.ModelConfigDetailResponse;
import com.aiplatform.modelhub.presentation.dto.ModelConfigResponse;
import com.aiplatform.modelhub.presentation.dto.ModelConnectivityTestResponse;
import com.aiplatform.modelhub.presentation.dto.ModelConnectivityTestRequest;
import com.aiplatform.modelhub.presentation.dto.ModelProviderDetailResponse;
import com.aiplatform.modelhub.presentation.dto.ModelProviderResponse;
import com.aiplatform.modelhub.presentation.dto.EmbeddingModelOptionResponse;
import com.aiplatform.modelhub.presentation.dto.UpdateModelConfigRequest;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureCodes;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型供应商 HTTP 适配器，负责上下文租户、操作者请求头、请求校验和统一响应包装。
 */
@RestController
@RequestMapping("/api/v1/model-providers")
public class ModelProviderController {
    private final ModelProviderApplicationService providerService;
    private final ModelConfigApplicationService configService;
    private final ModelCreationApplicationService creationService;
    private final ModelHubPresentationConverter converter;
    private final TenantFeatureGuard featureGuard;

    public ModelProviderController(
            ModelProviderApplicationService providerService,
            ModelConfigApplicationService configService,
            ModelCreationApplicationService creationService,
            ModelHubPresentationConverter converter,
            TenantFeatureGuard featureGuard
    ) {
        this.providerService = Objects.requireNonNull(providerService, "模型供应商应用服务不能为空");
        this.configService = Objects.requireNonNull(configService, "模型配置应用服务不能为空");
        this.creationService = Objects.requireNonNull(creationService, "模型创建应用服务不能为空");
        this.converter = Objects.requireNonNull(converter, "模型中心展示转换器不能为空");
        this.featureGuard = Objects.requireNonNull(featureGuard, "租户功能守卫不能为空");
    }

    @GetMapping
    public ApiResponse<List<ModelProviderDetailResponse>> listProviders() {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.MODEL_HUB);
        return ApiResponse.success(providerService.listProviders(TenantContext.current())
                .stream()
                .map(converter::toResponse)
                .toList());
    }

    @GetMapping("/embedding-options")
    public ApiResponse<List<EmbeddingModelOptionResponse>> listEmbeddingOptions() {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.MODEL_HUB);
        return ApiResponse.success(providerService.listProviders(TenantContext.current()).stream()
                .filter(provider -> ModelProviderStatus.ACTIVE.name().equals(provider.status()))
                .flatMap(provider -> configService.listModelConfigs(TenantContext.current(), provider.providerId()).stream()
                        .filter(model -> "EMBEDDING".equalsIgnoreCase(model.modelType())
                                && ModelConfigStatus.ACTIVE.name().equals(model.status()))
                        .map(model -> new EmbeddingModelOptionResponse(model.modelConfigId(),
                                provider.providerName() + " / " + model.modelName(), provider.providerName(),
                                model.modelCode(), model.embeddingDimension())))
                .toList());
    }

    @PostMapping
    public ApiResponse<ModelProviderResponse> createProvider(
            @Valid @RequestBody CreateModelProviderRequest request
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.MODEL_HUB);
        CreateModelProviderCommand command = new CreateModelProviderCommand(
                TenantContext.current(),
                PrincipalContext.currentActorId(),
                request.requestId(),
                request.providerType(),
                request.providerName(),
                request.apiEndpoint(),
                request.authType(),
                request.credentialMaterialInput()
        );
        return ApiResponse.success(ModelProviderResponse.from(creationService.createProvider(command)));
    }

    @PostMapping("/{providerId}/disable")
    public ApiResponse<ModelProviderDetailResponse> disableProvider(
            @PathVariable String providerId,
            @Valid @RequestBody DisableModelResourceRequest request
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.MODEL_HUB);
        DisableModelProviderCommand command = new DisableModelProviderCommand(
                TenantContext.current(),
                PrincipalContext.currentActorId(),
                providerId,
                request.reason(),
                request.expectedVersion()
        );
        return ApiResponse.success(converter.toResponse(providerService.disableProvider(command)));
    }

    @PostMapping("/{providerId}/models")
    public ApiResponse<ModelConfigResponse> createModelConfig(
            @PathVariable String providerId,
            @Valid @RequestBody CreateModelConfigRequest request
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.MODEL_HUB);
        CreateModelConfigCommand command = new CreateModelConfigCommand(
                TenantContext.current(),
                PrincipalContext.currentActorId(),
                request.requestId(),
                providerId,
                request.modelCode(),
                request.modelName(),
                request.modelType(),
                request.contextWindow(),
                request.embeddingDimension(),
                request.inputPricePer1k(),
                request.outputPricePer1k(),
                request.options()
        );
        return ApiResponse.success(ModelConfigResponse.from(creationService.createModelConfig(command)));
    }

    @GetMapping("/{providerId}/models")
    public ApiResponse<List<ModelConfigDetailResponse>> listModelConfigs(@PathVariable String providerId) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.MODEL_HUB);
        return ApiResponse.success(configService.listModelConfigs(TenantContext.current(), providerId)
                .stream()
                .map(converter::toResponse)
                .toList());
    }

    @PutMapping("/{providerId}/models/{modelConfigId}")
    public ApiResponse<ModelConfigDetailResponse> updateModelConfig(
            @PathVariable String providerId,
            @PathVariable String modelConfigId,
            @Valid @RequestBody UpdateModelConfigRequest request
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.MODEL_HUB);
        UpdateModelConfigCommand command = new UpdateModelConfigCommand(
                TenantContext.current(),
                PrincipalContext.currentActorId(),
                providerId,
                modelConfigId,
                request.modelCode(),
                request.modelName(),
                request.modelType(),
                request.contextWindow(),
                request.embeddingDimension(),
                request.inputPricePer1k(),
                request.outputPricePer1k(),
                request.options(),
                request.expectedVersion()
        );
        return ApiResponse.success(converter.toResponse(configService.updateModelConfig(command)));
    }

    @PostMapping("/{providerId}/models/{modelConfigId}/disable")
    public ApiResponse<ModelConfigDetailResponse> disableModelConfig(
            @PathVariable String providerId,
            @PathVariable String modelConfigId,
            @Valid @RequestBody DisableModelResourceRequest request
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.MODEL_HUB);
        DisableModelConfigCommand command = new DisableModelConfigCommand(
                TenantContext.current(),
                PrincipalContext.currentActorId(),
                providerId,
                modelConfigId,
                request.reason(),
                request.expectedVersion()
        );
        return ApiResponse.success(converter.toResponse(configService.disableModelConfig(command)));
    }

    @PostMapping("/{providerId}/models/{modelConfigId}/connectivity-test")
    public ApiResponse<ModelConnectivityTestResponse> testModelConnectivity(
            @PathVariable String providerId,
            @PathVariable String modelConfigId,
            @Valid @RequestBody ModelConnectivityTestRequest request
    ) {
        featureGuard.requireEnabled(TenantContext.current(), TenantFeatureCodes.MODEL_HUB);
        return ApiResponse.success(ModelConnectivityTestResponse.from(configService.testConnectivity(
                TenantContext.current(),
                providerId,
                modelConfigId,
                request.expectedVersion()
        )));
    }
}
