package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * evaluation-operations 私有 MinIO 桶的部署配置；值只在基础设施层消费。
 */
@ConfigurationProperties("foundation.evaluation-operations.object-storage")
public class EvaluationOperationsObjectStorageProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private long connectTimeoutMillis = 5_000L;
    private long writeTimeoutMillis = 60_000L;
    private long readTimeoutMillis = 10_000L;
    private boolean createBucketIfMissing;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String value) {
        endpoint = requireText(value, "MinIO 地址");
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String value) {
        accessKey = requireText(value, "MinIO 访问标识");
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String value) {
        secretKey = requireText(value, "MinIO 访问密钥");
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String value) {
        bucket = requireText(value, "MinIO 私有桶");
    }

    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(long value) {
        connectTimeoutMillis = requirePositive(value, "MinIO 连接超时");
    }

    public long getWriteTimeoutMillis() {
        return writeTimeoutMillis;
    }

    public void setWriteTimeoutMillis(long value) {
        writeTimeoutMillis = requirePositive(value, "MinIO 写超时");
    }

    public long getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(long value) {
        readTimeoutMillis = requirePositive(value, "MinIO 读超时");
    }

    public boolean isCreateBucketIfMissing() {
        return createBucketIfMissing;
    }

    public void setCreateBucketIfMissing(boolean value) {
        createBucketIfMissing = value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value < 1L) {
            throw new IllegalArgumentException(name + "必须大于零");
        }
        return value;
    }
}
