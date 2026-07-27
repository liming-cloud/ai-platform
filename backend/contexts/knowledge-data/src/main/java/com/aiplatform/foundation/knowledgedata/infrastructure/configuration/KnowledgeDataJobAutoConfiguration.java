package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexVectorWriter;
import com.aiplatform.foundation.knowledgedata.application.indexversion.service.IndexBuildWorker;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildCompletionPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildQueuePort;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.ParagraphWindowChunker;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexVersionMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.repository.PostgresIndexBuildCompletion;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.repository.PostgresIndexBuildQueue;
import com.aiplatform.foundation.knowledgedata.interfaces.job.indexversion.IndexBuildScheduler;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 为摄取进程注册索引构建队列、完成端口、工作进程和调度入口。
 */
@AutoConfiguration(after = KnowledgeDataPersistenceAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.ingestion.enabled",
        havingValue = "true")
@Import(IndexBuildScheduler.class)
@EnableConfigurationProperties(KnowledgeDataProperties.class)
public class KnowledgeDataJobAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    IndexBuildQueuePort indexBuildQueue(
            IndexVersionMapper mapper,
            IndexSourceMapper sourceMapper,
            PlatformTransactionManager transactions,
            EntityIdGenerator identifiers) {
        return new PostgresIndexBuildQueue(
                mapper,
                sourceMapper,
                transactions,
                identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    IndexBuildCompletionPort indexBuildCompletion(
            IndexVersionMapper mapper,
            IndexVectorWriter vectors,
            PlatformTransactionManager transactions) {
        return new PostgresIndexBuildCompletion(mapper, vectors, transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    IndexBuildWorker indexBuildWorker(
            IndexBuildQueuePort queue,
            IndexBuildCompletionPort completion,
            TextContentObjectStoragePort objectStorage,
            ModelRuntimeResolver models,
            KnowledgeDataProperties properties) {
        return new IndexBuildWorker(
                queue,
                completion,
                objectStorage,
                models,
                new ParagraphWindowChunker(),
                Clock.systemUTC(),
                properties.getIndexBuildLeaseDuration());
    }
}
