package com.aiplatform.modelhub.application.config;

import com.aiplatform.modelhub.domain.ModelConfigCreationService;
import com.aiplatform.modelhub.domain.ModelProviderCreationService;
import com.aiplatform.modelhub.domain.service.ModelConfigActivationPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * model-hub 领域服务装配，避免领域对象直接依赖 Spring 注解。
 */
@Configuration
public class ModelHubDomainConfiguration {
    @Bean
    ModelProviderCreationService modelProviderCreationService() {
        return new ModelProviderCreationService();
    }

    @Bean
    ModelConfigCreationService modelConfigCreationService() {
        return new ModelConfigCreationService();
    }

    @Bean
    ModelConfigActivationPolicy modelConfigActivationPolicy() {
        return new ModelConfigActivationPolicy();
    }
}
