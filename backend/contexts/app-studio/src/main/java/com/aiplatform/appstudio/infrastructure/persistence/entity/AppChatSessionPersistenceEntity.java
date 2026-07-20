package com.aiplatform.appstudio.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("app_chat_session")
public class AppChatSessionPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("application_id")
    private Long applicationId;
    @TableField("release_id")
    private Long releaseId;
    @TableField("release_version")
    private Long releaseVersion;
    @TableField("user_id")
    private Long userId;
    @TableField("title")
    private String title;
    @TableField("status")
    private String status;
    @TableField("last_message_at")
    private Instant lastMessageAt;
    @TableField("trace_id")
    private String traceId;
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

    public static AppChatSessionPersistenceEntity fromDomain(ChatSession session) {
        AppChatSessionPersistenceEntity entity = new AppChatSessionPersistenceEntity();
        entity.setId(parseRequiredLong(session.sessionId(), "会话 ID"));
        entity.setTenantId(parseRequiredLong(session.tenantId().value(), "租户 ID"));
        entity.setApplicationId(parseRequiredLong(session.applicationId(), "应用 ID"));
        entity.setReleaseId(parseRequiredLong(session.releaseId(), "发布版本 ID"));
        entity.setReleaseVersion(session.releaseVersion());
        entity.setUserId(parseRequiredLong(session.userId(), "用户 ID"));
        entity.setTitle(session.title());
        entity.setStatus("ACTIVE");
        entity.setLastMessageAt(session.lastMessageAt());
        entity.setCreatedAt(session.createdAt());
        entity.setCreatedBy(parseRequiredLong(session.userId(), "创建人 ID"));
        entity.setUpdatedAt(session.lastMessageAt());
        entity.setUpdatedBy(parseRequiredLong(session.userId(), "更新人 ID"));
        entity.setVersion(0);
        return entity;
    }

    public ChatSession toDomain() {
        if (releaseId == null || releaseVersion == null) {
            throw new IllegalStateException("ACTIVE 聊天会话缺少发布版本固定信息");
        }
        return new ChatSession(
                TenantId.of(String.valueOf(tenantId)),
                String.valueOf(id),
                String.valueOf(applicationId),
                String.valueOf(releaseId),
                releaseVersion,
                String.valueOf(userId),
                title == null || title.isBlank() ? "未命名会话" : title,
                createdAt,
                lastMessageAt
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getReleaseId() { return releaseId; }
    public void setReleaseId(Long releaseId) { this.releaseId = releaseId; }
    public Long getReleaseVersion() { return releaseVersion; }
    public void setReleaseVersion(Long releaseVersion) { this.releaseVersion = releaseVersion; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
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
