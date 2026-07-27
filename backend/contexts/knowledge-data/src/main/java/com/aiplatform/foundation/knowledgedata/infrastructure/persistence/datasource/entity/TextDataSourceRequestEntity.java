package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 文本数据源幂等请求结果快照实体。
 */
@TableName("knowledge_data.text_data_source_request")
public class TextDataSourceRequestEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("operation")
    private String operation;
    @TableField("idempotency_key_hash")
    private String idempotencyKeyHash;
    @TableField("request_fingerprint")
    private String requestFingerprint;
    @TableField("knowledge_space_id")
    private Long knowledgeSpaceId;
    @TableField("data_source_id")
    private Long dataSourceId;
    @TableField("result_status")
    private String resultStatus;
    @TableField("result_version")
    private long resultVersion;
    @TableField("result_content_version")
    private int resultContentVersion;
    @TableField("result_published_content_version")
    private int resultPublishedContentVersion;
    @TableField("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getIdempotencyKeyHash() {
        return idempotencyKeyHash;
    }

    public void setIdempotencyKeyHash(String idempotencyKeyHash) {
        this.idempotencyKeyHash = idempotencyKeyHash;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public Long getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public void setKnowledgeSpaceId(Long knowledgeSpaceId) {
        this.knowledgeSpaceId = knowledgeSpaceId;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public long getResultVersion() {
        return resultVersion;
    }

    public void setResultVersion(long resultVersion) {
        this.resultVersion = resultVersion;
    }

    public int getResultContentVersion() {
        return resultContentVersion;
    }

    public void setResultContentVersion(int resultContentVersion) {
        this.resultContentVersion = resultContentVersion;
    }

    public int getResultPublishedContentVersion() {
        return resultPublishedContentVersion;
    }

    public void setResultPublishedContentVersion(int value) {
        this.resultPublishedContentVersion = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
