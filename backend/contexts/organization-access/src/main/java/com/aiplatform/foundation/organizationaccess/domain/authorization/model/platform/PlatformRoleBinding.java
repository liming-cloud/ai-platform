package com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 全局主体与一个固定平台角色之间的可撤销绑定聚合根。
 *
 * <p>它不保存可编辑的权限集合；认证只能读取所有活动绑定按模板派生的权限。</p>
 */
public final class PlatformRoleBinding implements AggregateRoot<PlatformRoleBindingId> {

    private final PlatformRoleBindingId id;
    private final SubjectId subjectId;
    private final PlatformRole role;
    private final PlatformRoleBindingStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant revokedAt;

    private PlatformRoleBinding(
            PlatformRoleBindingId id,
            SubjectId subjectId,
            PlatformRole role,
            PlatformRoleBindingStatus status,
            long version,
            Instant createdAt,
            Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "平台角色绑定标识不能为空");
        this.subjectId = Objects.requireNonNull(subjectId, "主体标识不能为空");
        this.role = Objects.requireNonNull(role, "平台角色不能为空");
        this.status = Objects.requireNonNull(status, "平台角色绑定状态不能为空");
        if (version < 0L) {
            throw new IllegalArgumentException("平台角色绑定修订不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "平台角色绑定创建时间不能为空");
        if ((status == PlatformRoleBindingStatus.REVOKED) != (revokedAt != null)) {
            throw new IllegalArgumentException("平台角色绑定撤销时间与状态不一致");
        }
        this.revokedAt = revokedAt;
    }

    /** 建立一个活动的固定平台角色绑定。 */
    public static PlatformRoleBinding bind(
            PlatformRoleBindingId id, SubjectId subjectId, PlatformRole role, Instant createdAt) {
        return new PlatformRoleBinding(
                id, subjectId, role, PlatformRoleBindingStatus.ACTIVE, 0L, createdAt, null);
    }

    /** 从持久化事实恢复角色绑定。 */
    public static PlatformRoleBinding reconstitute(
            PlatformRoleBindingId id,
            SubjectId subjectId,
            PlatformRole role,
            PlatformRoleBindingStatus status,
            long version,
            Instant createdAt,
            Instant revokedAt) {
        return new PlatformRoleBinding(id, subjectId, role, status, version, createdAt, revokedAt);
    }

    /** 撤销绑定后，该角色不得再参与认证权限派生。 */
    public PlatformRoleBinding revoke(Instant revokedAt) {
        if (status == PlatformRoleBindingStatus.REVOKED) {
            return this;
        }
        return new PlatformRoleBinding(
                id,
                subjectId,
                role,
                PlatformRoleBindingStatus.REVOKED,
                version + 1L,
                createdAt,
                Objects.requireNonNull(revokedAt, "平台角色撤销时间不能为空"));
    }

    @Override
    public PlatformRoleBindingId id() {
        return id;
    }

    public SubjectId subjectId() {
        return subjectId;
    }

    public PlatformRole role() {
        return role;
    }

    public PlatformRoleBindingStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == PlatformRoleBindingStatus.ACTIVE;
    }

    public Set<PermissionCode> permissions() {
        return role.permissions();
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
