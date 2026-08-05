package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 私有内容只通过不透明引用关联的数据集清单表实体。
 */
@TableName("evaluation_operations.evaluation_dataset")
public class EvaluationDatasetEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("private_content_reference")
    private String privateContentReference;
    @TableField("content_digest")
    private String contentDigest;
    @TableField("entry_count")
    private int entryCount;
    @TableField("classification")
    private String classification;
    @TableField("retention_policy_id")
    private String retentionPolicyId;
    @TableField("status")
    private String status;
    @TableField("version")
    private long version;
    @TableField("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        id = value;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long value) {
        tenantId = value;
    }

    public String getPrivateContentReference() {
        return privateContentReference;
    }

    public void setPrivateContentReference(String value) {
        privateContentReference = value;
    }

    public String getContentDigest() {
        return contentDigest;
    }

    public void setContentDigest(String value) {
        contentDigest = value;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(int value) {
        entryCount = value;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String value) {
        classification = value;
    }

    public String getRetentionPolicyId() {
        return retentionPolicyId;
    }

    public void setRetentionPolicyId(String value) {
        retentionPolicyId = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long value) {
        version = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant value) {
        createdAt = value;
    }
}
