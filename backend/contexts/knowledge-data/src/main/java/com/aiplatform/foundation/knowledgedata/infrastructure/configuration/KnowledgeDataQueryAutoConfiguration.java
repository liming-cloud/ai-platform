package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.service.ActiveIndexLookupService;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 为在线查询进程只公开活动索引查询门面。
 */
@AutoConfiguration(after = KnowledgeDataPersistenceAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.query.enabled",
        havingValue = "true")
public class KnowledgeDataQueryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ActiveIndexLookup activeIndexLookup(IndexVersionRepository repository) {
        return new ActiveIndexLookupService(repository);
    }
}
