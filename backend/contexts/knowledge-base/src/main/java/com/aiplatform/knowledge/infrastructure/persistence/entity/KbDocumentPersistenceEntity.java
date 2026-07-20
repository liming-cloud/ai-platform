package com.aiplatform.knowledge.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.Objects;

/**
 * `kb_document` 表持久化实体，保存上传文档的来源、哈希和解析状态。
 */
@TableName("kb_document")
public class KbDocumentPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("document_name")
    private String documentName;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_uri")
    private String sourceUri;

    @TableField("content_hash")
    private String contentHash;

    @TableField("mime_type")
    private String mimeType;

    @TableField("file_size_bytes")
    private Long fileSizeBytes;

    @TableField("parse_status")
    private String parseStatus;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("version_no")
    private Integer versionNo;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField("deleted_at")
    private Instant deletedAt;

    @TableField("version")
    private Integer version;

    public static KbDocumentPersistenceEntity fromDomain(KnowledgeDocument document) {
        Objects.requireNonNull(document, "知识文档不能为空");
        KbDocumentPersistenceEntity entity = new KbDocumentPersistenceEntity();
        entity.setId(parseRequiredLong(document.documentId(), "文档 ID"));
        entity.setTenantId(parseRequiredLong(document.tenantId().value(), "租户 ID"));
        entity.setKnowledgeBaseId(parseRequiredLong(document.knowledgeBaseId(), "知识库 ID"));
        entity.setDocumentName(document.fileName());
        entity.setSourceType(document.sourceType());
        entity.setSourceUri(document.sourceUri());
        entity.setContentHash(document.contentHash());
        entity.setMimeType(document.contentType());
        entity.setFileSizeBytes(document.fileSizeBytes());
        entity.setParseStatus(document.status().name());
        entity.setVersionNo(document.version());
        entity.setCreatedAt(document.uploadedAt());
        entity.setCreatedBy(parseRequiredLong(document.uploadedBy(), "上传人 ID"));
        entity.setUpdatedAt(document.updatedAt());
        entity.setUpdatedBy(parseRequiredLong(document.uploadedBy(), "上传人 ID"));
        entity.setVersion(0);
        return entity;
    }

    public KnowledgeDocument toDomain() {
        return new KnowledgeDocument(
                TenantId.of(requiredLongText(tenantId, "租户 ID")),
                requiredLongText(id, "文档 ID"),
                requiredLongText(knowledgeBaseId, "知识库 ID"),
                requiredText(documentName, "文档名称"),
                requiredText(sourceType, "文档来源类型"),
                requiredText(sourceUri, "文档来源地址"),
                requiredText(contentHash, "文档内容哈希"),
                requiredText(mimeType, "文档 MIME 类型"),
                requiredLong(fileSizeBytes, "文档大小"),
                requiredInteger(versionNo, "文档业务版本号"),
                KnowledgeDocumentStatus.valueOf(requiredText(parseStatus, "文档解析状态")),
                requiredInstant(createdAt, "文档创建时间"),
                requiredInstant(updatedAt, "文档更新时间"),
                requiredLongText(createdBy, "上传人 ID")
        );
    }

    private static String requiredLongText(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("知识文档持久化记录缺少" + fieldName);
        }
        return String.valueOf(value);
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("知识文档持久化记录缺少" + fieldName);
        }
        return value.trim();
    }

    private static long requiredLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("知识文档持久化记录缺少" + fieldName);
        }
        return value;
    }

    private static int requiredInteger(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("知识文档持久化记录缺少" + fieldName);
        }
        return value;
    }

    private static Instant requiredInstant(Instant value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("知识文档持久化记录缺少" + fieldName);
        }
        return value;
    }

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

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
