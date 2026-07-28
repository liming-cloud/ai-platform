package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller;

import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationReadService;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.view.ModelConfigurationView;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 读取模型配置的受控入口，响应中绝不包含 API Key 或加密材料。
 */
@RestController
@ConditionalOnBean(ModelConfigurationReadService.class)
@RequestMapping("/api/v1/model-configurations")
public class ModelConfigurationReadController {

    private final ModelConfigurationReadService configurations;

    public ModelConfigurationReadController(ModelConfigurationReadService configurations) {
        this.configurations = Objects.requireNonNull(configurations, "模型配置读取用例不能为空");
    }

    @GetMapping("/{modelConfigurationId}")
    public ModelConfigurationView get(
            TrustedRequestContext context, @PathVariable long modelConfigurationId) {
        return ModelConfigurationView.from(configurations.get(
                context, new ModelConfigurationId(modelConfigurationId)));
    }
}
