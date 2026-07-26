package com.aiplatform.foundation.objectstorage.minio;

import com.aiplatform.foundation.knowledgedata.application.content.ObjectStorageUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.content.TextContentIntegrityException;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentObjectMetadata;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 使用私有 MinIO 桶保存受治理原始文本。
 */
public final class MinioTextContentObjectStorageAdapter
        implements TextContentObjectStoragePort {

    private static final Pattern BUCKET_NAME =
            Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");
    private static final String SHA256_METADATA = "sha256";
    private static final String KEY_TEMPLATE =
            "foundation-v1/tenants/%s/knowledge-data/knowledge-spaces/%s/"
                    + "data-sources/%s/versions/%d/%s/content.txt";

    private final MinioClient client;
    private final String bucket;

    public MinioTextContentObjectStorageAdapter(
            MinioClient client,
            String bucket) {
        this.client = Objects.requireNonNull(client, "对象存储客户端不能为空");
        this.bucket = requireBucketName(bucket);
    }

    /**
     * 检查私有桶；只有演示环境可以在缺失时创建。
     */
    public void ensureBucket(boolean createIfMissing) {
        try {
            if (bucketExists()) {
                return;
            }
            if (!createIfMissing) {
                throw new ObjectStorageUnavailableException();
            }
            try {
                client.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucket)
                        .build());
            } catch (Exception exception) {
                if (!bucketExists()) {
                    throw exception;
                }
            }
        } catch (ObjectStorageUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public TextContentReference put(TextContentStorageRequest request) {
        Objects.requireNonNull(request, "文本对象存储请求不能为空");
        var reference = referenceFor(request);
        try (var content = request.content().openStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(reference.objectKey())
                    .stream(content, reference.byteSize(), -1)
                    .contentType(reference.mediaType())
                    .userMetadata(Map.of(
                            SHA256_METADATA,
                            reference.sha256()))
                    .build());
            return reference;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public TextContentObjectMetadata metadata(TextContentReference reference) {
        Objects.requireNonNull(reference, "文本对象引用不能为空");
        final var response = stat(reference.objectKey());
        var sha256 = response.userMetadata().get(SHA256_METADATA);
        if (sha256 == null
                || response.size() < 1
                || response.contentType() == null) {
            throw new TextContentIntegrityException();
        }
        return new TextContentObjectMetadata(
                reference.objectKey(),
                sha256,
                response.size(),
                response.contentType());
    }

    @Override
    public void delete(TextContentReference reference) {
        Objects.requireNonNull(reference, "文本对象引用不能为空");
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(reference.objectKey())
                    .build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private boolean bucketExists() throws Exception {
        return client.bucketExists(BucketExistsArgs.builder()
                .bucket(bucket)
                .build());
    }

    private io.minio.StatObjectResponse stat(String objectKey) {
        try {
            return client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private static TextContentReference referenceFor(
            TextContentStorageRequest request) {
        var objectKey = KEY_TEMPLATE.formatted(
                request.tenantId().value(),
                request.knowledgeSpaceId().value(),
                request.dataSourceId().value(),
                request.contentVersion(),
                request.content().sha256());
        return new TextContentReference(
                request.contentVersion(),
                objectKey,
                request.content().sha256(),
                request.content().byteSize(),
                TextContentReference.UTF8_TEXT,
                request.createdAt());
    }

    private static String requireBucketName(String value) {
        Objects.requireNonNull(value, "对象存储桶名不能为空");
        if (!BUCKET_NAME.matcher(value).matches()
                || value.contains("..")
                || value.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalArgumentException("对象存储桶名格式不正确");
        }
        return value;
    }

    private static ObjectStorageUnavailableException unavailable(
            Exception exception) {
        return new ObjectStorageUnavailableException(exception);
    }
}
