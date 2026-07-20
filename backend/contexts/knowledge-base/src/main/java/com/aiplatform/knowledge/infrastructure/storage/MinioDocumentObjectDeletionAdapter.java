package com.aiplatform.knowledge.infrastructure.storage;

import com.aiplatform.infra.objectstorage.gateway.minio.config.ObjectStorageProperties;
import com.aiplatform.knowledge.domain.maintenance.ClaimedDocumentObjectCleanupTask;
import com.aiplatform.knowledge.domain.port.maintenance.DocumentObjectDeletionPort;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** MinIO 幂等删除适配器；任何 bucket、URI 或 tenant 路径不一致都失败关闭。 */
@Component
public class MinioDocumentObjectDeletionAdapter implements DocumentObjectDeletionPort {
    private final String configuredBucket;
    private final MinioClient minioClient;

    @Autowired
    public MinioDocumentObjectDeletionAdapter(ObjectStorageProperties properties) {
        this(properties, client(Objects.requireNonNull(properties, "对象存储配置不能为空")));
    }

    MinioDocumentObjectDeletionAdapter(ObjectStorageProperties properties, MinioClient minioClient) {
        this.configuredBucket = requireText(
                Objects.requireNonNull(properties, "对象存储配置不能为空").getBucket(), "对象存储 bucket"
        );
        this.minioClient = Objects.requireNonNull(minioClient, "MinIO 客户端不能为空");
    }

    @Override
    public void delete(ClaimedDocumentObjectCleanupTask claimed) {
        Objects.requireNonNull(claimed, "已认领对象清理任务不能为空");
        if (!configuredBucket.equals(claimed.bucket())) {
            throw new IllegalArgumentException("清理对象 bucket 与平台配置不一致");
        }
        String requiredPrefix = "tenants/%s/knowledge-bases/%s/documents/%s/".formatted(
                claimed.tenantId().value(), claimed.knowledgeBaseId(), claimed.documentId()
        );
        if (!claimed.objectKey().startsWith(requiredPrefix)) {
            throw new IllegalArgumentException("清理对象不是当前租户对象路径");
        }
        if (!matchesCanonicalSource(claimed)) {
            throw new IllegalArgumentException("清理对象 URI 与 bucket/key 不一致");
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(claimed.bucket())
                    .object(claimed.objectKey())
                    .build());
        } catch (Exception failure) {
            throw new IllegalStateException("对象存储删除失败", failure);
        }
    }

    private static boolean matchesCanonicalSource(ClaimedDocumentObjectCleanupTask claimed) {
        try {
            String canonical = new URI("s3", claimed.bucket(), "/" + claimed.objectKey(), null).toString();
            return canonical.equals(claimed.sourceUri());
        } catch (URISyntaxException failure) {
            return false;
        }
    }

    private static MinioClient client(ObjectStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region(properties.getRegion())
                .build();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
