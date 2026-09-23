package com.aiplatform.foundation.objectstorage.minio;

import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.ObjectStorageUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.TextContentIntegrityException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectContentReaderExtensionPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectReadRequest;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextObjectReference;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 读取部署配置中已映射的 MinIO 平台对象，并将正文流与冻结元数据逐字节核验。
 *
 * <p>内部对象键仅在此适配器内解析，返回流和异常均不暴露该位置。</p>
 */
public final class MinioPlatformObjectContentReaderAdapter
        implements PlatformObjectContentReaderExtensionPort {

    private final MinioClient client;
    private final String bucket;
    private final Map<LocationKey, String> locations;

    public MinioPlatformObjectContentReaderAdapter(
            MinioClient client,
            String bucket,
            List<PlatformObjectLocation> locations) {
        this.client = Objects.requireNonNull(client, "对象存储客户端不能为空");
        this.bucket = requireText(bucket, "对象存储桶名");
        this.locations = index(locations);
    }

    /**
     * 冻结集合成员资格由知识上下文桥接层先验证；此处在流耗尽时校验长度、媒体类型和 SHA-256。
     */
    @Override
    public InputStream open(PlatformObjectReadRequest object) {
        Objects.requireNonNull(object, "平台对象不能为空");
        var key = new LocationKey(
                object.tenantId(),
                object.knowledgeSpaceId(),
                object.rangeId(),
                object.selectionRevision(),
                object.objectId());
        var objectKey = locations.get(key);
        if (objectKey == null) {
            throw new ObjectStorageUnavailableException();
        }
        verifyMetadata(objectKey, object);
        try {
            return new VerifyingInputStream(client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()), object);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private void verifyMetadata(String objectKey, PlatformObjectReadRequest object) {
        try {
            var metadata = client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            if (metadata.size() != object.byteSize()
                    || !TextObjectReference.UTF8_TEXT.equals(object.mediaType())
                    || !object.mediaType().equals(metadata.contentType())) {
                throw new TextContentIntegrityException();
            }
        } catch (TextContentIntegrityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private static Map<LocationKey, String> index(List<PlatformObjectLocation> values) {
        Objects.requireNonNull(values, "平台对象位置不能为空");
        var indexed = new HashMap<LocationKey, String>();
        for (var value : values) {
            var location = Objects.requireNonNull(value, "平台对象位置不能为空");
            var key = new LocationKey(
                    location.tenantId(),
                    location.knowledgeSpaceId(),
                    location.rangeId(),
                    location.selectionRevision(),
                    location.objectId());
            if (indexed.putIfAbsent(key, location.objectKey()) != null) {
                throw new IllegalArgumentException("平台对象位置不能重复");
            }
        }
        return Map.copyOf(indexed);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + "不能为空");
        if (value.isBlank() || !value.equals(value.trim())
                || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException(name + "格式不正确");
        }
        return value;
    }

    private static ObjectStorageUnavailableException unavailable(Exception exception) {
        return new ObjectStorageUnavailableException(exception);
    }

    private record LocationKey(
            long tenantId,
            long knowledgeSpaceId,
            String rangeId,
            long selectionRevision,
            String objectId) {
    }

    /** 在输入流耗尽时核验真实字节，避免只信任远端元数据。 */
    private static final class VerifyingInputStream extends FilterInputStream {
        private final PlatformObjectReadRequest object;
        private final MessageDigest digest;
        private long byteSize;
        private boolean verified;

        private VerifyingInputStream(InputStream input, PlatformObjectReadRequest object) {
            super(input);
            this.object = object;
            this.digest = sha256();
        }

        @Override
        public int read() throws IOException {
            var value = super.read();
            if (value < 0) {
                verify();
            } else {
                digest.update((byte) value);
                byteSize++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            var count = super.read(bytes, offset, length);
            if (count < 0) {
                verify();
            } else if (count > 0) {
                digest.update(bytes, offset, count);
                byteSize += count;
            }
            return count;
        }

        private void verify() {
            if (verified) {
                return;
            }
            verified = true;
            if (byteSize != object.byteSize()
                    || !HexFormat.of().formatHex(digest.digest()).equals(object.contentDigest())) {
                throw new TextContentIntegrityException();
            }
        }

        private static MessageDigest sha256() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
            }
        }
    }
}
