package com.aiplatform.foundation.ingestionworker;

import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.IndexVersionMapper;
import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.PostgresIndexBuildCompletion;
import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.PostgresIndexBuildQueue;
import com.aiplatform.foundation.knowledgedata.application.indexversion.DefaultIndexBuildWorker;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexBuildCompletionPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexBuildQueuePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.ParagraphWindowChunker;
import com.aiplatform.foundation.modelruntime.api.invocation.ModelRuntimeResolver;
import com.aiplatform.foundation.objectstorage.minio.MinioTextContentObjectStorageAdapter;
import com.aiplatform.foundation.vectorstore.pgvector.PgVectorIndexStore;
import io.minio.MinioClient;
import java.time.Clock;
import java.time.Duration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 索引摄取工作进程的显式端口与适配器装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.ingestion.enabled",
        havingValue = "true")
@MapperScan(basePackageClasses = IndexVersionMapper.class)
public class IndexIngestionConfiguration {

    @Bean
    IndexBuildQueuePort indexBuildQueue(
            IndexVersionMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresIndexBuildQueue(mapper, transactions);
    }

    @Bean
    PgVectorIndexStore pgVectorIndexStore(JdbcTemplate jdbc) {
        return new PgVectorIndexStore(jdbc);
    }

    @Bean
    IndexBuildCompletionPort indexBuildCompletion(
            IndexVersionMapper mapper,
            PgVectorIndexStore vectors,
            PlatformTransactionManager transactions) {
        return new PostgresIndexBuildCompletion(mapper, vectors, transactions);
    }

    @Bean(destroyMethod = "close")
    MinioClient minioClient(
            @Value("${foundation.ingestion.object-storage.endpoint}")
            String endpoint,
            @Value("${foundation.ingestion.object-storage.access-key}")
            String accessKey,
            @Value("${foundation.ingestion.object-storage.secret-key}")
            String secretKey,
            @Value("${foundation.ingestion.object-storage.connect-timeout-millis:5000}")
            long connectTimeout,
            @Value("${foundation.ingestion.object-storage.write-timeout-millis:60000}")
            long writeTimeout,
            @Value("${foundation.ingestion.object-storage.read-timeout-millis:10000}")
            long readTimeout) {
        var client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        client.setTimeout(connectTimeout, writeTimeout, readTimeout);
        client.setAppInfo("trusted-rag-foundation-ingestion", "foundation-v1");
        return client;
    }

    @Bean
    TextContentObjectStoragePort textContentObjectStorage(
            MinioClient client,
            @Value("${foundation.ingestion.object-storage.bucket}")
            String bucket) {
        var adapter = new MinioTextContentObjectStorageAdapter(client, bucket);
        adapter.ensureBucket(false);
        return adapter;
    }

    @Bean
    DefaultIndexBuildWorker indexBuildWorker(
            IndexBuildQueuePort queue,
            IndexBuildCompletionPort completion,
            TextContentObjectStoragePort objectStorage,
            ModelRuntimeResolver models,
            @Value("${foundation.ingestion.lease-duration:PT1M}")
            Duration leaseDuration) {
        return new DefaultIndexBuildWorker(
                queue,
                completion,
                objectStorage,
                models,
                new ParagraphWindowChunker(),
                Clock.systemUTC(),
                leaseDuration);
    }
}
