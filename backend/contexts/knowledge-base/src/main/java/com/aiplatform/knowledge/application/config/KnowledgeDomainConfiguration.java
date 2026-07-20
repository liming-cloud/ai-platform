package com.aiplatform.knowledge.application.config;

import com.aiplatform.knowledge.domain.ingest.service.KnowledgeDocumentChunkFactory;
import com.aiplatform.knowledge.application.job.KnowledgeDocumentJobProperties;
import com.aiplatform.knowledge.application.maintenance.KnowledgeMaintenanceProperties;
import com.aiplatform.knowledge.domain.retrieval.policy.KnowledgeRetrievalAccessPolicy;
import com.aiplatform.knowledge.domain.retrieval.service.HybridRetrievalRanker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * knowledge-base 领域服务装配，保持领域对象不依赖 Spring 注解。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({KnowledgeDocumentJobProperties.class, KnowledgeMaintenanceProperties.class})
public class KnowledgeDomainConfiguration {
    @Bean
    KnowledgeDocumentChunkFactory knowledgeDocumentChunkFactory() {
        return new KnowledgeDocumentChunkFactory();
    }

    @Bean
    KnowledgeRetrievalAccessPolicy knowledgeRetrievalAccessPolicy() {
        return new KnowledgeRetrievalAccessPolicy();
    }

    @Bean
    HybridRetrievalRanker hybridRetrievalRanker() {
        return new HybridRetrievalRanker();
    }
}
