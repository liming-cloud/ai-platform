package com.aiplatform.foundation.modelruntime.domain.configuration.model;

import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 向租户或单个成员授予模型配置使用权；撤销后不得再被运行时解析使用。
 */
public final class ModelConfigurationAccessGrant
        implements AggregateRoot<ModelConfigurationAccessGrantId> {

    private final ModelConfigurationAccessGrantId id;
    private final ModelConfigurationId modelConfigurationId;
    private final TenantId granteeTenantId;
    private final ModelConfigurationOwnerId granteeMembershipId;
    private final ModelConfigurationAccessGrantStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant revokedAt;

    private ModelConfigurationAccessGrant(
            ModelConfigurationAccessGrantId id,
            ModelConfigurationId modelConfigurationId,
            TenantId granteeTenantId,
            ModelConfigurationOwnerId granteeMembershipId,
            ModelConfigurationAccessGrantStatus status,
            long version,
            Instant createdAt,
            Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "模型配置授权标识不能为空");
        this.modelConfigurationId = Objects.requireNonNull(modelConfigurationId, "模型配置标识不能为空");
        this.granteeTenantId = Objects.requireNonNull(granteeTenantId, "受授权租户不能为空");
        this.granteeMembershipId = granteeMembershipId;
        this.status = Objects.requireNonNull(status, "模型配置授权状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("模型配置授权版本不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "授权创建时间不能为空");
        if (status == ModelConfigurationAccessGrantStatus.REVOKED && revokedAt == null) {
            throw new IllegalArgumentException("已撤销授权必须记录撤销时间");
        }
        if (status == ModelConfigurationAccessGrantStatus.ACTIVE && revokedAt != null) {
            throw new IllegalArgumentException("活动授权不能记录撤销时间");
        }
        this.revokedAt = revokedAt;
    }

    public static ModelConfigurationAccessGrant grant(
            ModelConfigurationAccessGrantId id,
            ModelConfigurationId modelConfigurationId,
            TenantId granteeTenantId,
            ModelConfigurationOwnerId granteeMembershipId,
            Instant createdAt) {
        return new ModelConfigurationAccessGrant(
                id, modelConfigurationId, granteeTenantId, granteeMembershipId,
                ModelConfigurationAccessGrantStatus.ACTIVE, 0, createdAt, null);
    }

    public static ModelConfigurationAccessGrant reconstitute(
            ModelConfigurationAccessGrantId id,
            ModelConfigurationId modelConfigurationId,
            TenantId granteeTenantId,
            ModelConfigurationOwnerId granteeMembershipId,
            ModelConfigurationAccessGrantStatus status,
            long version,
            Instant createdAt,
            Instant revokedAt) {
        return new ModelConfigurationAccessGrant(
                id, modelConfigurationId, granteeTenantId, granteeMembershipId,
                status, version, createdAt, revokedAt);
    }

    public ModelConfigurationAccessGrant revoke(Instant revokedAt) {
        if (status == ModelConfigurationAccessGrantStatus.REVOKED) {
            return this;
        }
        return new ModelConfigurationAccessGrant(
                id, modelConfigurationId, granteeTenantId, granteeMembershipId,
                ModelConfigurationAccessGrantStatus.REVOKED, version + 1, createdAt,
                Objects.requireNonNull(revokedAt, "撤销时间不能为空"));
    }

    @Override public ModelConfigurationAccessGrantId id() { return id; }
    public ModelConfigurationId modelConfigurationId() { return modelConfigurationId; }
    public TenantId granteeTenantId() { return granteeTenantId; }
    public ModelConfigurationOwnerId granteeMembershipId() { return granteeMembershipId; }
    public ModelConfigurationAccessGrantStatus status() { return status; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant revokedAt() { return revokedAt; }
}
