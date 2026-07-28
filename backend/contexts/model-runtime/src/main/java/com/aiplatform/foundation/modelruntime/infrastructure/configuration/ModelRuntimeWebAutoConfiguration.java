package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import com.aiplatform.foundation.modelruntime.interfaces.web.deployment.controller.ModelDeploymentController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ControlledModelCatalogController;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.controller.ProviderConnectionController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * 显式导入模型目录控制面入口。
 */
@AutoConfiguration(after = ModelRuntimeCoreAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.model-catalog.enabled",
        havingValue = "true")
@Import({
    ProviderConnectionController.class,
    ModelDeploymentController.class,
    ModelConfigurationController.class,
    ControlledModelCatalogController.class
})
public class ModelRuntimeWebAutoConfiguration {
}
