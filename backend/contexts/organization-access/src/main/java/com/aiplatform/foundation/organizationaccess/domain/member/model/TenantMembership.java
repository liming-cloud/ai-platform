package com.aiplatform.foundation.organizationaccess.domain.member.model;

import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 一个全局主体在单一租户内的可定位成员资格聚合根。
 *
 * <p>成员资格只引用主体标识，绝不持有登录名或凭据摘要。初始管理员资格只能由
 * {@code TenantProvisioning} 建立，避免应用层分别拼接生命周期和成员事实。</p>
 */
public final class TenantMembership implements AggregateRoot<MemberId> {

    private final MemberId id;
    private final TenantId tenantId;
    private final SubjectId subjectId;
    private final TenantMembershipRole role;
    private final TenantMembershipStatus status;
    private final long permissionVersion;
    private final long version;
    private final Instant createdAt;

    private TenantMembership(
            MemberId id,
            TenantId tenantId,
            SubjectId subjectId,
            TenantMembershipRole role,
            TenantMembershipStatus status,
            long permissionVersion,
            long version,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "成员标识不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.subjectId = Objects.requireNonNull(subjectId, "主体标识不能为空");
        this.role = Objects.requireNonNull(role, "成员角色不能为空");
        this.status = Objects.requireNonNull(status, "成员状态不能为空");
        if (permissionVersion < 0L || version < 0L) {
            throw new IllegalArgumentException("成员资格修订不能为负数");
        }
        this.permissionVersion = permissionVersion;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "成员创建时间不能为空");
    }

    /**
     * 建立租户的初始管理员成员资格。
     */
    public static TenantMembership establishInitialAdministrator(
            MemberId id,
            TenantId tenantId,
            SubjectId subjectId,
            Instant createdAt) {
        return new TenantMembership(
                id,
                tenantId,
                subjectId,
                TenantMembershipRole.TENANT_ADMINISTRATOR,
                TenantMembershipStatus.ENABLED,
                0L,
                0L,
                createdAt);
    }

    /**
     * 从持久化成员资格事实恢复聚合根。
     *
     * @throws IllegalArgumentException 当持久化成员资格违反修订或必填约束时拒绝。
     */
    public static TenantMembership reconstitute(
            MemberId id,
            TenantId tenantId,
            SubjectId subjectId,
            TenantMembershipRole role,
            TenantMembershipStatus status,
            long permissionVersion,
            long version,
            Instant createdAt) {
        return new TenantMembership(
                id, tenantId, subjectId, role, status, permissionVersion, version, createdAt);
    }

    @Override
    public MemberId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public SubjectId subjectId() {
        return subjectId;
    }

    public TenantMembershipRole role() {
        return role;
    }

    public TenantMembershipStatus status() {
        return status;
    }

    public long permissionVersion() {
        return permissionVersion;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
