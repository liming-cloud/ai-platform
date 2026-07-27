package com.aiplatform.foundation.objectstorage.minio.autoconfigure;

import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.objectstorage.minio.MinioTextContentObjectStorageAdapter;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 为摄取工作进程注册只使用既有私有桶的 MinIO 实现。
 */
@AutoConfiguration
@ConditionalOnProperty(
        name = "foundation.ingestion.enabled",
        havingValue = "true")
@EnableConfigurationProperties(IngestionMinioProperties.class)
public class MinioIngestionAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    MinioClient ingestionMinioClient(IngestionMinioProperties properties) {
        return MinioKnowledgeDataAutoConfiguration.client(
                properties,
                "trusted-rag-foundation-ingestion");
    }

    @Bean
    @ConditionalOnMissingBean(TextContentObjectStoragePort.class)
    TextContentObjectStoragePort ingestionObjectStorage(
            MinioClient client,
            IngestionMinioProperties properties) {
        var adapter = new MinioTextContentObjectStorageAdapter(
                client,
                properties.getBucket());
        adapter.ensureBucket(false);
        return adapter;
    }
}
