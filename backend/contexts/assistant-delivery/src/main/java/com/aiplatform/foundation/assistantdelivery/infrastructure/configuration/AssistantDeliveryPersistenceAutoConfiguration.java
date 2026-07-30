package com.aiplatform.foundation.assistantdelivery.infrastructure.configuration;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.repository.AssistantTestRunRepository;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantTestCitationMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantTestRunMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantRequestMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantVersionMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.repository.PostgresAssistantRepository;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.repository.PostgresAssistantTestRunRepository;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 注册助手交付持久化能力。
 */
@AutoConfiguration(after = MybatisPlusAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.assistant-delivery.enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnBean(SqlSessionFactory.class)
@MapperScan(basePackageClasses = AssistantMapper.class)
public class AssistantDeliveryPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AssistantRepository assistantRepository(
            AssistantMapper mapper,
            AssistantRequestMapper requestMapper,
            AssistantVersionMapper versionMapper,
            EntityIdGenerator identifiers,
            PlatformTransactionManager transactions) {
        return new PostgresAssistantRepository(
                mapper,
                requestMapper,
                versionMapper,
                identifiers,
                transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    AssistantTestRunRepository assistantTestRunRepository(
            AssistantTestRunMapper mapper,
            AssistantTestCitationMapper citationMapper,
            EntityIdGenerator identifiers,
            PlatformTransactionManager transactions) {
        return new PostgresAssistantTestRunRepository(
                mapper,
                citationMapper,
                identifiers,
                transactions);
    }
}
