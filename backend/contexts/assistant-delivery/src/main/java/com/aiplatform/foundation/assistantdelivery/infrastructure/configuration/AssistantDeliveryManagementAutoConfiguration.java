package com.aiplatform.foundation.assistantdelivery.infrastructure.configuration;

import com.aiplatform.foundation.assistantdelivery.application.assistant.service.AssistantService;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.service.AssistantVersioning;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 仅为控制面注册助手管理应用服务。
 *
 * <p>查询进程只需要读取已发布助手，不应被迫装配模型部署和索引管理依赖。
 */
@AutoConfiguration(after = AssistantDeliveryCoreAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.control-plane.enabled",
        havingValue = "true")
@ConditionalOnProperty(
        name = "foundation.assistant-delivery.enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(AssistantDeliveryProperties.class)
public class AssistantDeliveryManagementAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AssistantService assistantService(
            AssistantRepository repository,
            ObjectProvider<UsableModelConfigurationLookup> modelConfigurations,
            ActiveIndexLookup activeIndexes,
            EntityIdGenerator identifiers,
            AssistantDeliveryProperties properties) {
        return new AssistantService(
                repository,
                modelConfigurations.getIfAvailable(),
                activeIndexes,
                Clock.systemUTC(),
                identifiers,
                new AssistantVersioning(),
                properties.getAssistantLimit(),
                properties.getVersionLimit());
    }
}
