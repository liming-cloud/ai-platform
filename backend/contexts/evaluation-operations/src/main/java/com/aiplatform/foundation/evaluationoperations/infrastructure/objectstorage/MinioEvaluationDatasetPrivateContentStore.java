package com.aiplatform.foundation.evaluationoperations.infrastructure.objectstorage;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetPrivateContentStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationContent;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 将合成评测正文写入独占私有桶，并在对象写入完成后校验摘要。
 */
public final class MinioEvaluationDatasetPrivateContentStore
        implements EvaluationDatasetPrivateContentStore {

    private static final Pattern BUCKET_NAME =
            Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");
    private static final String REFERENCE_PREFIX = "private://evaluation/";
    private static final String SHA256_METADATA = "sha256";
    private static final long PART_SIZE = 10L * 1024L * 1024L;
    private static final String KEY_TEMPLATE =
            "foundation-v1/tenants/%s/evaluation-operations/datasets/%s/%s.content";

    private final MinioClient client;
    private final String bucket;

    public MinioEvaluationDatasetPrivateContentStore(MinioClient client, String bucket) {
        this.client = Objects.requireNonNull(client, "对象存储客户端不能为空");
        this.bucket = requireBucketName(bucket);
    }

    /**
     * 检查评测私有桶；只有本地演示环境可以显式允许自动创建。
     */
    public void ensureBucket(boolean createIfMissing) {
        try {
            if (bucketExists()) {
                return;
            }
            if (!createIfMissing) {
                throw new IllegalStateException("评测私有对象存储桶不可用");
            }
            try {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            } catch (Exception exception) {
                if (!bucketExists()) {
                    throw exception;
                }
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public String put(
            TenantId tenantId,
            long datasetId,
            EvaluationDatasetRegistrationContent content) {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        content = Objects.requireNonNull(content, "评测私有内容不能为空");
        if (datasetId <= 0L) {
            throw new IllegalArgumentException("评测数据集标识必须为正数");
        }
        var objectKey = KEY_TEMPLATE.formatted(tenantId.value(), datasetId, content.contentDigest());
        try (var source = content.content();
                var verified = new DigestInputStream(source, MessageDigest.getInstance("SHA-256"))) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(verified, -1L, PART_SIZE)
                    .contentType("application/octet-stream")
                    .userMetadata(Map.of(SHA256_METADATA, content.contentDigest()))
                    .build());
            var actualDigest = HexFormat.of().formatHex(verified.getMessageDigest().digest());
            if (!actualDigest.equals(content.contentDigest())) {
                removeQuietly(objectKey);
                throw new IllegalArgumentException("评测私有内容摘要不匹配");
            }
            return REFERENCE_PREFIX + objectKey;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            removeQuietly(objectKey);
            throw unavailable(exception);
        }
    }

    @Override
    public java.io.InputStream open(String privateContentReference) {
        var objectKey = objectKey(privateContentReference);
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void delete(String privateContentReference) {
        var objectKey = objectKey(privateContentReference);
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private boolean bucketExists() throws Exception {
        return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    }

    private void removeQuietly(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception ignored) {
            // 摘要不匹配后的删除仅作补偿；外层仍稳定拒绝该请求。
        }
    }

    private static String objectKey(String reference) {
        if (reference == null || !reference.startsWith(REFERENCE_PREFIX)
                || !reference.equals(reference.trim())) {
            throw new IllegalArgumentException("评测私有内容引用格式不正确");
        }
        return reference.substring(REFERENCE_PREFIX.length());
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

    private static IllegalStateException unavailable(Exception exception) {
        return new IllegalStateException("评测私有对象存储不可用", exception);
    }
}
