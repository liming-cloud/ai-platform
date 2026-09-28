package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller;

import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationService;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.view.ModelConfigurationView;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 显式启用或停用模型配置，禁止草稿或已停用配置进入运行时。
 */
@RequiresTenantFeature("modelGovernance")
@RestController
@RequestMapping("/api/v1/model-configurations")
public class ModelConfigurationLifecycleController {

    private final ModelConfigurationService configurations;

    public ModelConfigurationLifecycleController(ModelConfigurationService configurations) {
        this.configurations = Objects.requireNonNull(configurations, "模型配置用例不能为空");
    }

    @PostMapping("/{modelConfigurationId}/activate")
    public ModelConfigurationView activate(
            TrustedRequestContext context, @PathVariable long modelConfigurationId) {
        return ModelConfigurationView.created(configurations.activate(
                context, new ModelConfigurationId(modelConfigurationId)));
    }

    @PostMapping("/{modelConfigurationId}/disable")
    public ModelConfigurationView disable(
            TrustedRequestContext context, @PathVariable long modelConfigurationId) {
        return ModelConfigurationView.created(configurations.disable(
                context, new ModelConfigurationId(modelConfigurationId)));
    }
}
