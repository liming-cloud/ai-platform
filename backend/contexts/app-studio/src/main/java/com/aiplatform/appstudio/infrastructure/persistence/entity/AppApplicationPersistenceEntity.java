package com.aiplatform.appstudio.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("app_application")
public class AppApplicationPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("app_code")
    private String appCode;
    @TableField("app_name")
    private String appName;
    @TableField("app_type")
    private String appType;
    @TableField("description")
    private String description;
    @TableField("default_model_config_id")
    private Long defaultModelConfigId;
    @TableField("system_prompt")
    private String systemPrompt;
    @TableField("status")
    private String status;
    @TableField("published_at")
    private Instant publishedAt;
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

    public static AppApplicationPersistenceEntity fromDomain(AiApplication domain) {
        AppApplicationPersistenceEntity entity = new AppApplicationPersistenceEntity();
        Long id = parseRequiredLong(domain.applicationId(), "应用 ID");
        entity.setId(id);
        entity.setTenantId(parseRequiredLong(domain.tenantId().value(), "租户 ID"));
        entity.setAppCode("APP-" + id);
        entity.setAppName(domain.name());
        entity.setAppType(domain.type().name());
        entity.setDescription(domain.description());
        entity.setDefaultModelConfigId(parseRequiredLong(domain.modelConfigId(), "模型配置 ID"));
        entity.setSystemPrompt(domain.systemPrompt());
        entity.setStatus(toStatus(domain.publicationStatus()));
        entity.setPublishedAt(domain.publicationStatus() == ApplicationPublicationStatus.PUBLISHED ? domain.updatedAt() : null);
        entity.setCreatedAt(domain.createdAt());
        entity.setCreatedBy(parseRequiredLong(domain.visiblePrincipalIds().iterator().next(), "创建人 ID"));
        entity.setUpdatedAt(domain.updatedAt());
        entity.setUpdatedBy(parseRequiredLong(domain.visiblePrincipalIds().iterator().next(), "更新人 ID"));
        entity.setVersion(0);
        return entity;
    }

    public AiApplication toDomain(java.util.List<String> knowledgeBaseIds) {
        String actorId = String.valueOf(createdBy);
        return new AiApplication(
                TenantId.of(String.valueOf(tenantId)),
                String.valueOf(id),
                AiApplicationType.valueOf(appType),
                appName,
                description == null ? "" : description,
                String.valueOf(defaultModelConfigId),
                knowledgeBaseIds,
                java.util.List.of(),
                java.util.Set.of(actorId),
                systemPrompt == null ? "" : systemPrompt,
                fromStatus(status),
                createdAt,
                updatedAt
        );
    }

    private static String toStatus(ApplicationPublicationStatus status) {
        return switch (status) {
            case DRAFT -> "DRAFT";
            case PENDING_APPROVAL -> "PENDING_APPROVAL";
            case PUBLISHED -> "ACTIVE";
            case OFFLINE -> "OFFLINE";
        };
    }

    private static ApplicationPublicationStatus fromStatus(String value) {
        return switch (value) {
            case "DRAFT" -> ApplicationPublicationStatus.DRAFT;
            case "PENDING_APPROVAL" -> ApplicationPublicationStatus.PENDING_APPROVAL;
            case "ACTIVE" -> ApplicationPublicationStatus.PUBLISHED;
            case "OFFLINE" -> ApplicationPublicationStatus.OFFLINE;
            default -> throw new IllegalArgumentException("不支持的应用状态：" + value);
        };
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getAppType() { return appType; }
    public void setAppType(String appType) { this.appType = appType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getDefaultModelConfigId() { return defaultModelConfigId; }
    public void setDefaultModelConfigId(Long defaultModelConfigId) { this.defaultModelConfigId = defaultModelConfigId; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
