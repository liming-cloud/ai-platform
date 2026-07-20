package com.aiplatform.approval.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.approval.domain.model.ApprovalDecisionRecord;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.Objects;

/**
 * `appr_record` 表持久化实体，保存审批任务的每一次人工决策。
 */
@TableName("appr_record")
public class ApprRecordPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("task_id")
    private Long taskId;

    @TableField("actor_id")
    private Long actorId;

    @TableField("action")
    private String action;

    @TableField("comment")
    private String comment;

    @TableField("action_at")
    private Instant actionAt;

    @TableField("created_at")
    private Instant createdAt;

    public static ApprRecordPersistenceEntity fromDomain(ApprovalDecisionRecord record) {
        Objects.requireNonNull(record, "审批流水不能为空");
        ApprRecordPersistenceEntity entity = new ApprRecordPersistenceEntity();
        entity.setTenantId(parseRequiredLong(record.tenantId().value(), "租户 ID"));
        entity.setTaskId(parseRequiredLong(record.taskId(), "审批任务 ID"));
        entity.setActorId(parseRequiredLong(record.actorId(), "审批人 ID"));
        entity.setAction(record.action().name());
        entity.setComment(record.comment());
        entity.setActionAt(record.actionAt());
        entity.setCreatedAt(record.actionAt());
        return entity;
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

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getActionAt() {
        return actionAt;
    }

    public void setActionAt(Instant actionAt) {
        this.actionAt = actionAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
