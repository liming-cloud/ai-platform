package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetPrivateContentStore;
import com.aiplatform.foundation.evaluationoperations.infrastructure.objectstorage.MinioEvaluationDatasetPrivateContentStore;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 仅在明确启用时注册评测数据集的独占私有 MinIO 存储。
 */
@AutoConfiguration(before = EvaluationOperationsCoreAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.evaluation-operations.object-storage.enabled",
        havingValue = "true")
@EnableConfigurationProperties(EvaluationOperationsObjectStorageProperties.class)
public class EvaluationOperationsObjectStorageAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(EvaluationDatasetPrivateContentStore.class)
    MinioClient evaluationOperationsMinioClient(EvaluationOperationsObjectStorageProperties properties) {
        var client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        client.setTimeout(
                properties.getConnectTimeoutMillis(),
                properties.getWriteTimeoutMillis(),
                properties.getReadTimeoutMillis());
        client.setAppInfo("trusted-rag-foundation-evaluation-operations", "foundation-v1");
        return client;
    }

    @Bean
    @ConditionalOnMissingBean(EvaluationDatasetPrivateContentStore.class)
    EvaluationDatasetPrivateContentStore evaluationDatasetPrivateContentStore(
            @Qualifier("evaluationOperationsMinioClient") MinioClient evaluationOperationsMinioClient,
            EvaluationOperationsObjectStorageProperties properties) {
        var store = new MinioEvaluationDatasetPrivateContentStore(
                evaluationOperationsMinioClient,
                properties.getBucket());
        store.ensureBucket(properties.isCreateBucketIfMissing());
        return store;
    }
}
