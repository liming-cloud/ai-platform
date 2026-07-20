package com.aiplatform.infra.objectstorage.gateway.minio.config;

import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 平台对象存储基础设施配置。
 */
@Validated
@Component
@ConfigurationProperties(prefix = "aip.object-storage")
public class ObjectStorageProperties {
    @NotBlank
    private String endpoint = "http://localhost:9000";

    @NotBlank
    private String accessKey;

    @NotBlank
    private String secretKey;

    @NotBlank
    private String bucket = "aip-knowledge";

    @NotBlank
    private String region = "us-east-1";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = requireText(endpoint, "endpoint");
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = requireCredential(accessKey, "accessKey");
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = requireCredential(secretKey, "secretKey");
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = requireText(bucket, "bucket");
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = requireText(region, "region");
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

    private static String requireCredential(String value, String fieldName) {
        String normalized = requireText(value, fieldName);
        String comparable = normalized.toLowerCase(Locale.ROOT);
        if (comparable.equals("aip")
                || comparable.equals("aipminio")
                || comparable.equals("aipminio123")
                || comparable.startsWith("local-dev")
                || comparable.startsWith("replace-")
                || comparable.startsWith("change-")
                || comparable.startsWith("changeme")
                || comparable.startsWith("example")
                || comparable.startsWith("password")) {
            throw new IllegalArgumentException(fieldName + "不能使用占位或已知弱值");
        }
        return normalized;
    }
}
