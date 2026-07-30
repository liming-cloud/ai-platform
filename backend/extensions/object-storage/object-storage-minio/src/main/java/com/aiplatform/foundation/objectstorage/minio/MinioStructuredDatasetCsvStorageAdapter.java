package com.aiplatform.foundation.objectstorage.minio;

import com.aiplatform.foundation.knowledgedata.application.datasource.exception.ObjectStorageUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStoragePort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStorageRequest;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** 使用私有 MinIO 桶保存结构化数据集的 UTF-8 CSV。 */
public final class MinioStructuredDatasetCsvStorageAdapter implements StructuredDatasetCsvStoragePort {

    private static final Pattern BUCKET_NAME = Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");
    private static final String SHA256_METADATA = "sha256";
    private static final String KEY_TEMPLATE = "foundation-v1/tenants/%s/knowledge-data/knowledge-spaces/%s/"
            + "structured-datasets/%s/revisions/%d/%s/data.csv";

    private final MinioClient client;
    private final String bucket;

    public MinioStructuredDatasetCsvStorageAdapter(MinioClient client, String bucket) {
        this.client = Objects.requireNonNull(client, "对象存储客户端不能为空");
        this.bucket = requireBucketName(bucket);
    }

    /** 检查私有桶；只有演示环境可以在缺失时创建。 */
    public void ensureBucket(boolean createIfMissing) {
        try {
            if (bucketExists()) {
                return;
            }
            if (!createIfMissing) {
                throw new ObjectStorageUnavailableException();
            }
            try {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
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
    public StructuredDatasetCsvReference put(StructuredDatasetCsvStorageRequest request) {
        Objects.requireNonNull(request, "CSV 对象存储请求不能为空");
        var reference = referenceFor(request);
        try (var content = request.content().openStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(reference.objectKey())
                    .stream(content, reference.byteSize(), -1)
                    .contentType(StructuredDatasetCsvReference.UTF8_CSV)
                    .userMetadata(Map.of(SHA256_METADATA, reference.contentDigest()))
                    .build());
            return reference;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public InputStream read(StructuredDatasetCsvReference reference) {
        Objects.requireNonNull(reference, "CSV 对象引用不能为空");
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(reference.objectKey()).build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void delete(StructuredDatasetCsvReference reference) {
        Objects.requireNonNull(reference, "CSV 对象引用不能为空");
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(reference.objectKey()).build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private static StructuredDatasetCsvReference referenceFor(StructuredDatasetCsvStorageRequest request) {
        return new StructuredDatasetCsvReference(
                KEY_TEMPLATE.formatted(
                        request.tenantId().value(),
                        request.knowledgeSpaceId().value(),
                        request.dataSourceId().value(),
                        request.revision(),
                        request.content().sha256()),
                request.content().sha256(),
                request.content().byteSize());
    }

    private boolean bucketExists() throws Exception {
        return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    }

    private static String requireBucketName(String value) {
        Objects.requireNonNull(value, "对象存储桶名不能为空");
        if (!BUCKET_NAME.matcher(value).matches() || value.contains("..")
                || value.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalArgumentException("对象存储桶名格式不正确");
        }
        return value;
    }

    private static ObjectStorageUnavailableException unavailable(Exception exception) {
        return new ObjectStorageUnavailableException(exception);
    }
}
