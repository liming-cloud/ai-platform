package com.aiplatform.foundation.controlplane.configuration;

import com.aiplatform.foundation.assistantdelivery.adapter.out.persistence.AssistantMapper;
import com.aiplatform.foundation.assistantdelivery.adapter.out.persistence.PostgresAssistantRepository;
import com.aiplatform.foundation.assistantdelivery.api.AssistantUseCase;
import com.aiplatform.foundation.assistantdelivery.api.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.DefaultAssistantService;
import com.aiplatform.foundation.assistantdelivery.application.DefaultPublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantRepository;
import com.aiplatform.foundation.knowledgedata.api.ActiveIndexLookup;
import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.modelruntime.api.catalog.PublishedChatDeploymentLookup;
import java.time.Clock;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 控制面使用的助手身份、不可变版本和发布能力显式装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.assistant-delivery.enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(
        name = {
            "foundation.model-catalog.enabled",
            "foundation.knowledge-data.enabled"
        },
        havingValue = "true",
        matchIfMissing = true)
@MapperScan(basePackageClasses = AssistantMapper.class)
public class AssistantDeliveryConfiguration {

    @Bean
    AssistantRepository assistantRepository(
            AssistantMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresAssistantRepository(mapper, transactions);
    }

    @Bean
    AssistantUseCase assistantUseCase(
            AssistantRepository repository,
            PublishedChatDeploymentLookup chatDeployments,
            ActiveIndexLookup activeIndexes,
            EntityIdGenerator identifiers,
            @Value("${foundation.demo.assistant-limit:5}") int tenantLimit,
            @Value("${foundation.demo.assistant-version-limit:20}") int versionLimit) {
        return new DefaultAssistantService(
                repository,
                chatDeployments,
                activeIndexes,
                Clock.systemUTC(),
                identifiers,
                tenantLimit,
                versionLimit);
    }

    @Bean
    PublishedAssistantVersionLookup publishedAssistantVersionLookup(
            AssistantRepository repository) {
        return new DefaultPublishedAssistantVersionLookup(repository);
    }
}
