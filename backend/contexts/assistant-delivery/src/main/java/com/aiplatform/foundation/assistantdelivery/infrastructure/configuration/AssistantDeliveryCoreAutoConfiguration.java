package com.aiplatform.foundation.assistantdelivery.infrastructure.configuration;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.assistant.service.PublishedAssistantVersionLookupService;
import com.aiplatform.foundation.assistantdelivery.application.catalog.service.QueryAssistantCatalogService;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 注册助手应用服务与跨上下文发布门面。
 */
@AutoConfiguration(after = AssistantDeliveryPersistenceAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.assistant-delivery.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AssistantDeliveryCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    PublishedAssistantVersionLookup publishedAssistantVersionLookup(
            AssistantRepository repository) {
        return new PublishedAssistantVersionLookupService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    QueryAssistantCatalogService queryAssistantCatalogService(
            AssistantRepository repository) {
        return new QueryAssistantCatalogService(repository);
    }

}
