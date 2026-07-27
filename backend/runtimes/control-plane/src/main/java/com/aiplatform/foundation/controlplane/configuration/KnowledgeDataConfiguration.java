package com.aiplatform.foundation.controlplane.configuration;

import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.KnowledgeSpaceMapper;
import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.IndexVersionMapper;
import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.PostgresIndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.PostgresKnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.PostgresTextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.TextDataSourceMapper;
import com.aiplatform.foundation.knowledgedata.adapter.out.objectstorage.UnavailableTextContentObjectStorageAdapter;
import com.aiplatform.foundation.knowledgedata.adapter.out.staging.LocalTextContentStagingAdapter;
import com.aiplatform.foundation.knowledgedata.api.IndexVersionUseCase;
import com.aiplatform.foundation.knowledgedata.api.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.api.KnowledgeSpaceUseCase;
import com.aiplatform.foundation.knowledgedata.api.TextDataSourceUseCase;
import com.aiplatform.foundation.knowledgedata.application.datasource.DefaultTextDataSourceService;
import com.aiplatform.foundation.knowledgedata.application.indexversion.DefaultIndexVersionService;
import com.aiplatform.foundation.knowledgedata.application.indexversion.DefaultActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.knowledge.DefaultKnowledgeSpaceService;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.objectstorage.minio.MinioTextContentObjectStorageAdapter;
import com.aiplatform.foundation.modelruntime.api.catalog.PublishedEmbeddingDeploymentLookup;
import io.minio.MinioClient;
import java.nio.file.Path;
import java.time.Clock;
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
            EntityIdGenerator identifiers,
            @Value("${foundation.demo.knowledge-space-limit:3}") int tenantLimit) {
        return new DefaultKnowledgeSpaceService(
                repository,
                Clock.systemUTC(),
                identifiers,
                tenantLimit);
    }

    @Bean
    TextDataSourceRepository textDataSourceRepository(
            TextDataSourceMapper mapper,
            PlatformTransactionManager transactions,
            EntityIdGenerator identifiers) {
        return new PostgresTextDataSourceRepository(
                mapper,
                transactions,
                identifiers);
    }

    @Bean
    IndexVersionRepository indexVersionRepository(
            IndexVersionMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresIndexVersionRepository(mapper, transactions);
    }

    @Bean
    ActiveIndexLookup activeIndexLookup(IndexVersionRepository repository) {
        return new DefaultActiveIndexLookup(repository);
    }

    @Bean
    IndexVersionUseCase indexVersionUseCase(
            IndexVersionRepository repository,
            PublishedEmbeddingDeploymentLookup embeddingDeployments,
            EntityIdGenerator identifiers) {
        return new DefaultIndexVersionService(
                repository,
                embeddingDeployments,
                Clock.systemUTC(),
                identifiers);
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
    @ConditionalOnProperty(
            name = "foundation.knowledge-data.object-storage.enabled",
            havingValue = "false",
            matchIfMissing = true)
    TextContentObjectStoragePort unavailableTextContentObjectStorage() {
        return new UnavailableTextContentObjectStorageAdapter();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            name = "foundation.knowledge-data.object-storage.enabled",
            havingValue = "true")
    MinioClient minioClient(
            @Value("${foundation.knowledge-data.object-storage.endpoint}")
            String endpoint,
            @Value("${foundation.knowledge-data.object-storage.access-key}")
            String accessKey,
            @Value("${foundation.knowledge-data.object-storage.secret-key}")
            String secretKey,
            @Value(
                    "${foundation.knowledge-data.object-storage."
                            + "connect-timeout-millis:5000}")
            long connectTimeout,
            @Value(
                    "${foundation.knowledge-data.object-storage."
                            + "write-timeout-millis:60000}")
            long writeTimeout,
            @Value(
                    "${foundation.knowledge-data.object-storage."
                            + "read-timeout-millis:10000}")
            long readTimeout) {
        var client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        client.setTimeout(connectTimeout, writeTimeout, readTimeout);
        client.setAppInfo("trusted-rag-foundation", "foundation-v1");
        return client;
    }

    @Bean
    @ConditionalOnProperty(
            name = "foundation.knowledge-data.object-storage.enabled",
            havingValue = "true")
    TextContentObjectStoragePort minioTextContentObjectStorage(
            MinioClient client,
            @Value("${foundation.knowledge-data.object-storage.bucket}")
            String bucket,
            @Value(
                    "${foundation.knowledge-data.object-storage."
                            + "create-bucket-if-missing:false}")
            boolean createBucketIfMissing) {
        var adapter = new MinioTextContentObjectStorageAdapter(client, bucket);
        adapter.ensureBucket(createBucketIfMissing);
        return adapter;
    }

    @Bean
    TextDataSourceUseCase textDataSourceUseCase(
            TextDataSourceRepository repository,
            TextContentStagingPort staging,
            TextContentObjectStoragePort objectStorage,
            EntityIdGenerator identifiers,
            @Value("${foundation.demo.data-source-limit:20}") int tenantLimit) {
        return new DefaultTextDataSourceService(
                repository,
                staging,
                objectStorage,
                Clock.systemUTC(),
                identifiers,
                tenantLimit);
    }
}
