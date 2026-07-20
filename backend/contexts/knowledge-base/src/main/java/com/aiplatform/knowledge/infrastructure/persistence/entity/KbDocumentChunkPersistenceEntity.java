package com.aiplatform.knowledge.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@TableName("kb_document_chunk")
public class KbDocumentChunkPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("document_id")
    private Long documentId;

    @TableField("chunk_no")
    private Integer chunkNo;

    @TableField("content")
    private String content;

    @TableField("content_hash")
    private String contentHash;

    @TableField("source_uri")
    private String sourceUri;

    @TableField("document_version")
    private Integer documentVersion;

    @TableField("title_path")
    private String titlePath;

    @TableField("paragraph_no")
    private Integer paragraphNo;

    @TableField("table_cell_range")
    private String tableCellRange;

    @TableField("classification")
    private String classification;

    @TableField("visibility")
    private String visibility;

    @TableField("acl_tokens")
    private String aclTokens;

    @TableField("authoritative")
    private Boolean authoritative;

    @TableField("published_at")
    private Instant publishedAt;

    @TableField("expires_at")
    private Instant expiresAt;

    @TableField("embedding_model")
    private String embeddingModel;

    @TableField("chunk_strategy_version")
    private String chunkStrategyVersion;

    @TableField("index_version")
    private String indexVersion;

    @TableField("embedding")
    private String embedding;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("page_no")
    private Integer pageNo;

    @TableField("position_start")
    private Integer positionStart;

    @TableField("position_end")
    private Integer positionEnd;

    @TableField("status")
    private String status;

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

    public static KbDocumentChunkPersistenceEntity fromDomain(KnowledgeDocumentChunk chunk) {
        Objects.requireNonNull(chunk, "知识切片不能为空");
        KbDocumentChunkPersistenceEntity entity = new KbDocumentChunkPersistenceEntity();
        entity.setId(parseRequiredLong(chunk.chunkId(), "切片 ID"));
        entity.setTenantId(parseRequiredLong(chunk.tenantId().value(), "租户 ID"));
        entity.setKnowledgeBaseId(parseRequiredLong(chunk.knowledgeBaseId(), "知识库 ID"));
        entity.setDocumentId(parseRequiredLong(chunk.documentId(), "文档 ID"));
        entity.setChunkNo(chunk.chunkNo());
        entity.setContent(chunk.content());
        entity.setContentHash(chunk.contentHash());
        entity.setSourceUri(chunk.metadata().sourceUri());
        entity.setDocumentVersion(chunk.metadata().documentVersion());
        entity.setTitlePath(chunk.metadata().titlePath());
        entity.setParagraphNo(chunk.metadata().paragraphNo());
        entity.setTableCellRange(chunk.metadata().tableCellRange());
        entity.setClassification(chunk.metadata().classification());
        entity.setVisibility(chunk.metadata().visibility());
        entity.setAclTokens(encodeAclTokens(chunk.metadata().aclTokens()));
        entity.setAuthoritative(chunk.metadata().authoritative());
        entity.setPublishedAt(chunk.metadata().publishedAt());
        entity.setExpiresAt(chunk.metadata().expiresAt());
        entity.setEmbeddingModel(chunk.metadata().embeddingModel());
        entity.setChunkStrategyVersion(chunk.metadata().chunkStrategyVersion());
        entity.setIndexVersion(chunk.metadata().indexVersion());
        entity.setTokenCount(chunk.tokenCount());
        entity.setPageNo(chunk.pageNo());
        entity.setPositionStart(chunk.positionStart());
        entity.setPositionEnd(chunk.positionEnd());
        entity.setStatus(chunk.status());
        entity.setCreatedAt(chunk.createdAt());
        entity.setCreatedBy(parseRequiredLong(chunk.operatorId(), "操作人 ID"));
        entity.setUpdatedAt(chunk.updatedAt());
        entity.setUpdatedBy(parseRequiredLong(chunk.operatorId(), "操作人 ID"));
        entity.setVersion(0);
        return entity;
    }

    public KnowledgeDocumentChunk toDomain() {
        return new KnowledgeDocumentChunk(
                TenantId.of(requiredLongText(tenantId, "租户 ID")),
                requiredLongText(id, "切片 ID"),
                requiredLongText(knowledgeBaseId, "知识库 ID"),
                requiredLongText(documentId, "文档 ID"),
                requiredInteger(chunkNo, "切片序号"),
                content,
                contentHash,
                requiredInteger(tokenCount, "token 数"),
                pageNo,
                requiredInteger(positionStart, "引用片段起始位置"),
                requiredInteger(positionEnd, "引用片段结束位置"),
                new KnowledgeDocumentChunkMetadata(
                        sourceUri,
                        requiredInteger(documentVersion, "文档版本号"),
                        titlePath,
                        paragraphNo,
                        tableCellRange,
                        classification,
                        visibility,
                        decodeAclTokens(aclTokens),
                        requiredBoolean(authoritative, "权威来源标记"),
                        publishedAt,
                        expiresAt,
                        embeddingModel,
                        chunkStrategyVersion,
                        indexVersion
                ),
                status,
                createdAt,
                updatedAt,
                requiredLongText(createdBy, "创建人 ID")
        );
    }

    private static String encodeAclTokens(List<String> tokens) {
        return String.join("\n", tokens);
    }

    private static String requiredLongText(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return String.valueOf(value);
    }

    private static Integer requiredInteger(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static boolean requiredBoolean(Boolean value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static List<String> decodeAclTokens(String tokens) {
        if (tokens == null) {
            throw new IllegalArgumentException("权限快照不能为空");
        }
        return Arrays.stream(tokens.split("\\R", -1))
                .map(KbDocumentChunkPersistenceEntity::requireAclToken)
                .toList();
    }

    private static String requireAclToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("权限快照不能为空白");
        }
        return token.trim();
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

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getChunkNo() {
        return chunkNo;
    }

    public void setChunkNo(Integer chunkNo) {
        this.chunkNo = chunkNo;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public Integer getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(Integer documentVersion) {
        this.documentVersion = documentVersion;
    }

    public String getTitlePath() {
        return titlePath;
    }

    public void setTitlePath(String titlePath) {
        this.titlePath = titlePath;
    }

    public Integer getParagraphNo() {
        return paragraphNo;
    }

    public void setParagraphNo(Integer paragraphNo) {
        this.paragraphNo = paragraphNo;
    }

    public String getTableCellRange() {
        return tableCellRange;
    }

    public void setTableCellRange(String tableCellRange) {
        this.tableCellRange = tableCellRange;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getAclTokens() {
        return aclTokens;
    }

    public void setAclTokens(String aclTokens) {
        this.aclTokens = aclTokens;
    }

    public Boolean getAuthoritative() {
        return authoritative;
    }

    public void setAuthoritative(Boolean authoritative) {
        this.authoritative = authoritative;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getChunkStrategyVersion() {
        return chunkStrategyVersion;
    }

    public void setChunkStrategyVersion(String chunkStrategyVersion) {
        this.chunkStrategyVersion = chunkStrategyVersion;
    }

    public String getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(String indexVersion) {
        this.indexVersion = indexVersion;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPositionStart() {
        return positionStart;
    }

    public void setPositionStart(Integer positionStart) {
        this.positionStart = positionStart;
    }

    public Integer getPositionEnd() {
        return positionEnd;
    }

    public void setPositionEnd(Integer positionEnd) {
        this.positionEnd = positionEnd;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
