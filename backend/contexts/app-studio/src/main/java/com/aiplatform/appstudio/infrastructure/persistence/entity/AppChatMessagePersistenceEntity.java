package com.aiplatform.appstudio.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("app_chat_message")
public class AppChatMessagePersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("session_id")
    private Long sessionId;
    @TableField("application_id")
    private Long applicationId;
    @TableField("role")
    private String role;
    @TableField("content")
    private String content;
    @TableField("content_format")
    private String contentFormat;
    @TableField("model_invocation_log_id")
    private Long modelInvocationLogId;
    @TableField("token_count")
    private Integer tokenCount;
    @TableField("citation_count")
    private Integer citationCount;
    @TableField("trace_id")
    private String traceId;
    @TableField("client_message_id")
    private String clientMessageId;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("created_by")
    private Long createdBy;

    public static AppChatMessagePersistenceEntity fromDomain(ChatMessage message, String applicationId, String actorId) {
        AppChatMessagePersistenceEntity entity = new AppChatMessagePersistenceEntity();
        entity.setId(parseRequiredLong(message.messageId(), "消息 ID"));
        entity.setTenantId(parseRequiredLong(message.tenantId().value(), "租户 ID"));
        entity.setSessionId(parseRequiredLong(message.sessionId(), "会话 ID"));
        entity.setApplicationId(parseRequiredLong(applicationId, "应用 ID"));
        entity.setRole(message.role().name().toLowerCase());
        entity.setContent(message.content());
        entity.setContentFormat("markdown");
        entity.setModelInvocationLogId(message.modelInvocationLogId() == null ? null : parseRequiredLong(message.modelInvocationLogId(), "模型调用日志 ID"));
        entity.setTokenCount(0);
        entity.setCitationCount(message.citations().size());
        entity.setTraceId(message.traceId());
        entity.setClientMessageId(message.clientMessageId());
        entity.setCreatedAt(message.createdAt());
        entity.setCreatedBy(parseRequiredLong(actorId, "创建人 ID"));
        return entity;
    }

    public ChatMessage toDomain() {
        return toDomain(java.util.List.of());
    }

    public ChatMessage toDomain(java.util.List<com.aiplatform.appstudio.domain.Citation> citations) {
        return new ChatMessage(
                TenantId.of(String.valueOf(tenantId)),
                String.valueOf(id),
                String.valueOf(sessionId),
                ChatRole.valueOf(role.toUpperCase()),
                content,
                citations,
                modelInvocationLogId == null ? null : String.valueOf(modelInvocationLogId),
                clientMessageId,
                traceId,
                createdAt
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentFormat() { return contentFormat; }
    public void setContentFormat(String contentFormat) { this.contentFormat = contentFormat; }
    public Long getModelInvocationLogId() { return modelInvocationLogId; }
    public void setModelInvocationLogId(Long modelInvocationLogId) { this.modelInvocationLogId = modelInvocationLogId; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public Integer getCitationCount() { return citationCount; }
    public void setCitationCount(Integer citationCount) { this.citationCount = citationCount; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getClientMessageId() { return clientMessageId; }
    public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
