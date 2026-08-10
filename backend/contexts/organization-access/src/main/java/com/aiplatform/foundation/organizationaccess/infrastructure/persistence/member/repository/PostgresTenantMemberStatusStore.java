package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberStatusMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL 成员状态存储，只映射受锁目录事实并原子提交聚合根状态。 */
public final class PostgresTenantMemberStatusStore implements TenantMemberStatusStore {

    private final TenantMemberStatusMapper mapper;

    /** 构造 PostgreSQL 成员状态存储。 */
    public PostgresTenantMemberStatusStore(TenantMemberStatusMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "成员状态映射器不能为空");
    }

    @Override
    public Optional<TenantMembership> lock(TenantId tenantId, MemberId membershipId) {
        var row = mapper.lock(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(membershipId, "成员标识不能为空").value());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(TenantMembership.reconstitute(
                new MemberId(row.getMembershipId()),
                new TenantId(row.getTenantId()),
                new SubjectId(row.getSubjectId()),
                TenantMembershipRole.valueOf(row.getMembershipRole()),
                TenantMembershipStatus.valueOf(row.getStatus()),
                row.getPermissionVersion(),
                row.getVersion(),
                row.getCreatedAt()));
    }

    @Override
    public boolean hasAccessManage(TenantId tenantId, MemberId membershipId) {
        return mapper.countAccessManage(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(membershipId, "成员标识不能为空").value()) > 0;
    }

    @Override
    public long countOtherEnabledAccessManagers(TenantId tenantId, MemberId membershipId) {
        return mapper.countOtherEnabledAccessManagers(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(membershipId, "成员标识不能为空").value());
    }

    @Override
    public void update(TenantMembership membership) {
        membership = Objects.requireNonNull(membership, "成员资格不能为空");
        if (mapper.update(
                membership.tenantId().value(),
                membership.id().value(),
                membership.status().name(),
                membership.permissionVersion(),
                membership.version(),
                membership.version() - 1L) != 1) {
            throw new IllegalStateException("成员状态更新冲突");
        }
    }
}
