package com.aiplatform.boot.health;

import com.aiplatform.infra.objectstorage.gateway.minio.config.ObjectStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * MinIO readiness 探针，仅检查配置桶可访问性，不创建桶、不读写对象，也不暴露连接配置。
 */
@Component("minioHealthIndicator")
final class MinioReadinessHealthIndicator implements HealthIndicator {
    private static final long PROBE_TIMEOUT_MILLIS = 2_000L;

    private final String bucket;
    private final MinioClient minioClient;

    @Autowired
    MinioReadinessHealthIndicator(ObjectStorageProperties properties) {
        this(properties, readinessClient(properties));
    }

    MinioReadinessHealthIndicator(ObjectStorageProperties properties, MinioClient minioClient) {
        ObjectStorageProperties storageProperties = Objects.requireNonNull(
                properties,
                "对象存储配置不能为空"
        );
        this.bucket = storageProperties.getBucket();
        this.minioClient = Objects.requireNonNull(minioClient, "MinIO readiness 客户端不能为空");
    }

    @Override
    public Health health() {
        try {
            boolean bucketAvailable = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            return bucketAvailable ? Health.up().build() : Health.down().build();
        } catch (Exception ignored) {
            return Health.down().build();
        }
    }

    private static MinioClient readinessClient(ObjectStorageProperties properties) {
        ObjectStorageProperties storageProperties = Objects.requireNonNull(
                properties,
                "对象存储配置不能为空"
        );
        MinioClient client = MinioClient.builder()
                .endpoint(storageProperties.getEndpoint())
                .credentials(storageProperties.getAccessKey(), storageProperties.getSecretKey())
                .region(storageProperties.getRegion())
                .build();
        client.setTimeout(PROBE_TIMEOUT_MILLIS, PROBE_TIMEOUT_MILLIS, PROBE_TIMEOUT_MILLIS);
        return client;
    }
}
