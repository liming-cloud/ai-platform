package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller;

import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationCredentialService;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request.ModelConfigurationCreateRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request.ModelConfigurationCredentialRotateRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.view.ModelConfigurationView;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创建模型配置的受控入口。API Key 只在方法作用域中保存为可清零秘密。
 */
@RequiresTenantFeature("modelGovernance")
@RestController
@ConditionalOnBean(ModelConfigurationCredentialService.class)
@RequestMapping("/api/v1/model-configurations")
public class ModelConfigurationController {

    private final ModelConfigurationCredentialService configurations;

    public ModelConfigurationController(ModelConfigurationCredentialService configurations) {
        this.configurations = Objects.requireNonNull(configurations, "模型配置凭据用例不能为空");
    }

    @PostMapping
    public ResponseEntity<ModelConfigurationView> create(
            TrustedRequestContext context,
            @RequestBody ModelConfigurationCreateRequest request) {
        var input = Objects.requireNonNull(request.apiKey(), "API Key不能为空").toCharArray();
        try (var apiKey = SecretValue.copyOf(input)) {
            var configuration = configurations.create(
                    context,
                    Objects.requireNonNull(request.scope(), "模型配置范围不能为空"),
                    request.displayName(),
                    new ControlledModelCatalogEntryId(request.catalogEntryId()),
                    apiKey);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ModelConfigurationView.created(configuration));
        } finally {
            Arrays.fill(input, '\0');
        }
    }

    @PostMapping("/{modelConfigurationId}/credential")
    public ModelConfigurationView rotateCredential(
            TrustedRequestContext context,
            @PathVariable long modelConfigurationId,
            @RequestBody ModelConfigurationCredentialRotateRequest request) {
        return withApiKey(request.apiKey(), apiKey -> ModelConfigurationView.created(
                configurations.rotate(context, new ModelConfigurationId(modelConfigurationId), apiKey)));
    }

    private static <T> T withApiKey(String value, java.util.function.Function<SecretValue, T> action) {
        var input = Objects.requireNonNull(value, "API Key不能为空").toCharArray();
        try (var apiKey = SecretValue.copyOf(input)) {
            return action.apply(apiKey);
        } finally {
            Arrays.fill(input, '\0');
        }
    }
}
