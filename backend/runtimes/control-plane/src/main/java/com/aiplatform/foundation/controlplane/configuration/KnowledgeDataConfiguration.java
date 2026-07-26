package com.aiplatform.foundation.controlplane.configuration;

import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.KnowledgeSpaceMapper;
import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.PostgresKnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.api.KnowledgeSpaceUseCase;
import com.aiplatform.foundation.knowledgedata.application.knowledge.DefaultKnowledgeSpaceService;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceRepository;
import java.time.Clock;
import java.util.UUID;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 控制面使用的知识与数据上下文显式装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.knowledge-data.enabled",
        havingValue = "true",
        matchIfMissing = true)
@MapperScan(basePackageClasses = KnowledgeSpaceMapper.class)
public class KnowledgeDataConfiguration {

    @Bean
    KnowledgeSpaceRepository knowledgeSpaceRepository(
            KnowledgeSpaceMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresKnowledgeSpaceRepository(mapper, transactions);
    }

    @Bean
    KnowledgeSpaceUseCase knowledgeSpaceUseCase(
            KnowledgeSpaceRepository repository,
            @Value("${foundation.demo.knowledge-space-limit:3}") int tenantLimit) {
        return new DefaultKnowledgeSpaceService(
                repository,
                Clock.systemUTC(),
                UUID::randomUUID,
                tenantLimit);
    }
}
