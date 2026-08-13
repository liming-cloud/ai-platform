package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import com.aiplatform.foundation.modelruntime.interfaces.web.deployment.controller.ModelDeploymentController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ControlledModelCatalogController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationAccessController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationLifecycleController;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller.ModelConfigurationReadController;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.controller.ProviderConnectionController;
import com.aiplatform.foundation.modelruntime.interfaces.web.provider.controller.ProviderModelCandidateController;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller.PlatformProviderConnectionController;
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
    PlatformProviderConnectionController.class,
    ProviderModelCandidateController.class,
    ModelDeploymentController.class,
    ModelConfigurationController.class,
    ControlledModelCatalogController.class,
    ModelConfigurationAccessController.class,
    ModelConfigurationLifecycleController.class,
    ModelConfigurationReadController.class
})
public class ModelRuntimeWebAutoConfiguration {
}
