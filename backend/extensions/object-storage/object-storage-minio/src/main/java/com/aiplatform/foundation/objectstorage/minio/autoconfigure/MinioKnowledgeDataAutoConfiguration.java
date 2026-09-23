package com.aiplatform.foundation.objectstorage.minio.autoconfigure;

import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextObjectStorageExtensionPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.CsvObjectStorageExtensionPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStoragePort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectContentReaderExtensionPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectContentReaderPort;
import com.aiplatform.foundation.objectstorage.minio.MinioPlatformObjectContentReaderAdapter;
import com.aiplatform.foundation.objectstorage.minio.MinioStructuredDatasetCsvStorageAdapter;
import com.aiplatform.foundation.objectstorage.minio.MinioTextContentObjectStorageAdapter;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 为控制面注册 MinIO 文本对象存储实现。
 */
@AutoConfiguration(beforeName =
        "com.aiplatform.foundation.knowledgedata.infrastructure.configuration.KnowledgeDataCoreAutoConfiguration")
@ConditionalOnProperty(
        name = "foundation.knowledge-data.object-storage.enabled",
        havingValue = "true")
@EnableConfigurationProperties(KnowledgeDataMinioProperties.class)
public class MinioKnowledgeDataAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    MinioClient knowledgeDataMinioClient(KnowledgeDataMinioProperties properties) {
        return client(
                properties,
                "trusted-rag-foundation-control-plane");
    }

    @Bean
    @ConditionalOnMissingBean({TextObjectStorageExtensionPort.class, TextContentObjectStoragePort.class})
    TextObjectStorageExtensionPort knowledgeDataObjectStorage(
            MinioClient client,
            KnowledgeDataMinioProperties properties) {
        var adapter = new MinioTextContentObjectStorageAdapter(
                client,
                properties.getBucket());
        adapter.ensureBucket(properties.isCreateBucketIfMissing());
        return adapter;
    }

    @Bean
    @ConditionalOnMissingBean({CsvObjectStorageExtensionPort.class, StructuredDatasetCsvStoragePort.class})
    CsvObjectStorageExtensionPort knowledgeDataStructuredDatasetCsvStorage(
            MinioClient client,
            KnowledgeDataMinioProperties properties) {
        var adapter = new MinioStructuredDatasetCsvStorageAdapter(client, properties.getBucket());
        adapter.ensureBucket(properties.isCreateBucketIfMissing());
        return adapter;
    }

    @Bean
    @ConditionalOnMissingBean({PlatformObjectContentReaderExtensionPort.class, PlatformObjectContentReaderPort.class})
    PlatformObjectContentReaderExtensionPort knowledgeDataPlatformObjectContentReader(
            MinioClient client,
            KnowledgeDataMinioProperties properties) {
        return new MinioPlatformObjectContentReaderAdapter(
                client,
                properties.getBucket(),
                properties.getPlatformObjectLocations());
    }

    static MinioClient client(
            MinioObjectStorageSettings properties,
            String applicationName) {
        var client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        client.setTimeout(
                properties.getConnectTimeoutMillis(),
                properties.getWriteTimeoutMillis(),
                properties.getReadTimeoutMillis());
        client.setAppInfo(applicationName, "foundation-v1");
        return client;
    }
}
