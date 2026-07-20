package com.aiplatform.infra.objectstorage.gateway.minio;

import com.aiplatform.infra.objectstorage.gateway.ObjectStorageGateway;
import com.aiplatform.infra.objectstorage.gateway.exception.ObjectStorageException;
import com.aiplatform.infra.objectstorage.gateway.minio.config.ObjectStorageProperties;
import com.aiplatform.infra.objectstorage.gateway.model.ObjectStorageContent;
import com.aiplatform.infra.objectstorage.gateway.model.ObjectStorageObject;
import com.aiplatform.infra.objectstorage.gateway.model.StoredObject;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 基于 MinIO S3 兼容协议的对象存储网关。
 */
@Component
public class MinioObjectStorageGateway implements ObjectStorageGateway {
    private final ObjectStorageProperties properties;
    private final MinioClient minioClient;

    @Autowired
    public MinioObjectStorageGateway(ObjectStorageProperties properties) {
        this(properties, MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region(properties.getRegion())
                .build());
    }

    MinioObjectStorageGateway(ObjectStorageProperties properties, MinioClient minioClient) {
        if (properties == null) {
            throw new IllegalArgumentException("对象存储配置不能为空");
        }
        if (minioClient == null) {
            throw new IllegalArgumentException("MinIO 客户端不能为空");
        }
        this.properties = properties;
        this.minioClient = minioClient;
    }

    @Override
    public StoredObject put(ObjectStorageObject object) {
        if (object == null) {
            throw new IllegalArgumentException("待写入对象不能为空");
        }
        String bucket = requireText(properties.getBucket(), "bucket");
        try {
            ensureBucket(bucket);
            byte[] content = object.content();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(object.objectKey())
                    .contentType(object.contentType())
                    .userMetadata(object.metadata())
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .build());
            return new StoredObject(bucket, object.objectKey(), s3Uri(bucket, object.objectKey()));
        } catch (Exception exception) {
            String detail = "bucket=%s, objectKey=%s, cause=%s".formatted(
                    bucket,
                    object.objectKey(),
                    exception.getMessage()
            );
            throw new ObjectStorageException(detail, exception);
        }
    }

    @Override
    public ObjectStorageContent get(String bucket, String objectKey) {
        GetObjectArgs args = getObjectArgs(bucket, objectKey);
        try (InputStream input = minioClient.getObject(args)) {
            return new ObjectStorageContent(args.bucket(), args.object(), input.readAllBytes());
        } catch (Exception exception) {
            String detail = "bucket=%s, objectKey=%s, cause=%s".formatted(
                    args.bucket(),
                    args.object(),
                    exception.getMessage()
            );
            throw new ObjectStorageException(detail, exception);
        }
    }

    static String s3Uri(String bucket, String objectKey) {
        String normalizedBucket = requireText(bucket, "bucket");
        String normalizedObjectKey = requireText(objectKey, "objectKey");
        try {
            return new URI("s3", normalizedBucket, "/" + normalizedObjectKey, null).toString();
        } catch (URISyntaxException exception) {
            throw new ObjectStorageException(
                    "bucket=%s, objectKey=%s, cause=S3 URI 非法".formatted(normalizedBucket, normalizedObjectKey),
                    exception
            );
        }
    }

    static GetObjectArgs getObjectArgs(String bucket, String objectKey) {
        String normalizedBucket = requireText(bucket, "bucket");
        String normalizedObjectKey = requireText(objectKey, "objectKey");
        return GetObjectArgs.builder()
                .bucket(normalizedBucket)
                .object(normalizedObjectKey)
                .build();
    }

    private void ensureBucket(String bucket) throws Exception {
        String normalizedBucket = requireText(bucket, "bucket");
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(normalizedBucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(normalizedBucket).build());
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}
