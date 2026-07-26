package com.aiplatform.foundation.controlplane.configuration;

import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.KnowledgeSpaceMapper;
import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.PostgresKnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.PostgresTextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.TextDataSourceMapper;
import com.aiplatform.foundation.knowledgedata.adapter.out.objectstorage.UnavailableTextContentObjectStorageAdapter;
import com.aiplatform.foundation.knowledgedata.adapter.out.staging.LocalTextContentStagingAdapter;
import com.aiplatform.foundation.knowledgedata.api.KnowledgeSpaceUseCase;
import com.aiplatform.foundation.knowledgedata.api.TextDataSourceUseCase;
import com.aiplatform.foundation.knowledgedata.application.datasource.DefaultTextDataSourceService;
import com.aiplatform.foundation.knowledgedata.application.knowledge.DefaultKnowledgeSpaceService;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

    @Bean
    TextDataSourceRepository textDataSourceRepository(
            TextDataSourceMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresTextDataSourceRepository(mapper, transactions);
    }

    @Bean
    TextContentStagingPort textContentStagingPort(
            @Value("${foundation.knowledge-data.staging-directory:/tmp/foundation-text-staging}")
            String directory,
            @Value("${foundation.knowledge-data.maximum-text-bytes:20971520}")
            long maximumBytes) {
        if (maximumBytes > TextContentReference.MAX_BYTE_SIZE) {
            throw new IllegalArgumentException("文本暂存上限不能突破平台安全上限");
        }
        return new LocalTextContentStagingAdapter(Path.of(directory), maximumBytes);
    }

    @Bean
    @ConditionalOnMissingBean(TextContentObjectStoragePort.class)
    TextContentObjectStoragePort unavailableTextContentObjectStorage() {
        return new UnavailableTextContentObjectStorageAdapter();
    }

    @Bean
    TextDataSourceUseCase textDataSourceUseCase(
            TextDataSourceRepository repository,
            TextContentStagingPort staging,
            TextContentObjectStoragePort objectStorage,
            @Value("${foundation.demo.data-source-limit:20}") int tenantLimit) {
        return new DefaultTextDataSourceService(
                repository,
                staging,
                objectStorage,
                Clock.systemUTC(),
                UUID::randomUUID,
                tenantLimit);
    }
}
