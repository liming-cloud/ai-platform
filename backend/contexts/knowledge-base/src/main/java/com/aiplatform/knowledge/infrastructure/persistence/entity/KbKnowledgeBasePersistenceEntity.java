package com.aiplatform.knowledge.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * `kb_knowledge_base` 表持久化实体，字段与数据库设计保持一一映射。
 */
@TableName("kb_knowledge_base")
public class KbKnowledgeBasePersistenceEntity {
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String DRAFT_STATUS = "DRAFT";
    private static final String DISABLED_STATUS = "DISABLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("kb_code")
    private String kbCode;

    @TableField("kb_name")
    private String kbName;

    @TableField("description")
    private String description;

    @TableField("visibility")
    private String visibility;

    @TableField("embedding_model_config_id")
    private Long embeddingModelConfigId;

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

    public static KbKnowledgeBasePersistenceEntity fromDomain(KnowledgeBase knowledgeBase) {
        Objects.requireNonNull(knowledgeBase, "知识库不能为空");
        KbKnowledgeBasePersistenceEntity entity = new KbKnowledgeBasePersistenceEntity();
        Long id = parseRequiredLong(knowledgeBase.knowledgeBaseId(), "知识库 ID");
        entity.setId(id);
        entity.setTenantId(parseRequiredLong(knowledgeBase.tenantId().value(), "租户 ID"));
        entity.setKbCode("KB-" + id);
        entity.setKbName(knowledgeBase.name());
        entity.setDescription(knowledgeBase.description());
        entity.setVisibility(toDatabaseVisibility(knowledgeBase.visibility()));
        entity.setEmbeddingModelConfigId(parseRequiredLong(knowledgeBase.embeddingModelConfigId(), "知识库向量模型配置 ID"));
        entity.setStatus(knowledgeBase.published() ? ACTIVE_STATUS : DISABLED_STATUS);
        entity.setCreatedAt(knowledgeBase.createdAt());
        entity.setCreatedBy(parseRequiredLong(knowledgeBase.createdBy(), "创建人 ID"));
        entity.setUpdatedAt(knowledgeBase.updatedAt());
        entity.setUpdatedBy(parseRequiredLong(knowledgeBase.createdBy(), "创建人 ID"));
        entity.setVersion(0);
        return entity;
    }

    public KnowledgeBase toDomain() {
        String creatorId = requiredLongText(createdBy, "创建人 ID");
        return new KnowledgeBase(
                TenantId.of(requiredLongText(tenantId, "租户 ID")),
                requiredLongText(id, "知识库 ID"),
                requiredText(kbName, "知识库名称"),
                description == null ? "" : description.trim(),
                fromDatabaseVisibility(requiredText(visibility, "知识库可见性")),
                Set.of(creatorId),
                creatorId,
                requiredLongText(embeddingModelConfigId, "知识库向量模型配置 ID"),
                true,
                fromDatabaseStatus(requiredText(status, "知识库状态")),
                requiredInstant(createdAt, "知识库创建时间"),
                requiredInstant(updatedAt, "知识库更新时间")
        );
    }

    private static String toDatabaseVisibility(KnowledgeVisibility visibility) {
        return switch (visibility) {
            case PUBLIC -> "public";
            case DEPARTMENT -> "department";
            case PROJECT -> "project";
            case PRIVATE -> "private";
            case SPECIFIED_MEMBERS -> "members";
            case SPECIFIED_ROLES -> "roles";
        };
    }

    private static KnowledgeVisibility fromDatabaseVisibility(String visibility) {
        return switch (visibility) {
            case "public" -> KnowledgeVisibility.PUBLIC;
            case "department" -> KnowledgeVisibility.DEPARTMENT;
            case "project" -> KnowledgeVisibility.PROJECT;
            case "private" -> KnowledgeVisibility.PRIVATE;
            case "members" -> KnowledgeVisibility.SPECIFIED_MEMBERS;
            case "roles" -> KnowledgeVisibility.SPECIFIED_ROLES;
            default -> throw new IllegalArgumentException("不支持的知识库可见性：" + visibility);
        };
    }

    private static boolean fromDatabaseStatus(String status) {
        return switch (status) {
            case ACTIVE_STATUS -> true;
            case DRAFT_STATUS -> false;
            case DISABLED_STATUS -> false;
            default -> throw new IllegalArgumentException("不支持的知识库状态：" + status);
        };
    }

    private static String requiredLongText(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("知识库持久化记录缺少" + fieldName);
        }
        return String.valueOf(value);
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("知识库持久化记录缺少" + fieldName);
        }
        return value.trim();
    }

    private static Instant requiredInstant(Instant value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("知识库持久化记录缺少" + fieldName);
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

    public String getKbCode() {
        return kbCode;
    }

    public void setKbCode(String kbCode) {
        this.kbCode = kbCode;
    }

    public String getKbName() {
        return kbName;
    }

    public void setKbName(String kbName) {
        this.kbName = kbName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public Long getEmbeddingModelConfigId() {
        return embeddingModelConfigId;
    }

    public void setEmbeddingModelConfigId(Long embeddingModelConfigId) {
        this.embeddingModelConfigId = embeddingModelConfigId;
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
