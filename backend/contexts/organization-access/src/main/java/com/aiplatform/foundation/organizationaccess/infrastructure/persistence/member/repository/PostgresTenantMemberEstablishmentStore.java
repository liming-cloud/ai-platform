package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberEstablishmentStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberEstablishmentMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL 成员建立存储，仅提供受锁事实、唯一性检查和领域结果写入。 */
public final class PostgresTenantMemberEstablishmentStore implements TenantMemberEstablishmentStore {

    private final TenantMemberEstablishmentMapper mapper;

    /** 构造成员建立 PostgreSQL 存储。 */
    public PostgresTenantMemberEstablishmentStore(TenantMemberEstablishmentMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "成员建立映射器不能为空");
    }

    @Override
    public Optional<SubjectId> lockActiveSubject(SubjectId subjectId) {
        var id = mapper.lockActiveSubject(Objects.requireNonNull(subjectId, "主体标识不能为空").value());
        return id == null ? Optional.empty() : Optional.of(new SubjectId(id));
    }

    @Override
    public boolean hasMembership(TenantId tenantId, SubjectId subjectId) {
        return mapper.countMembership(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(subjectId, "主体标识不能为空").value()) > 0;
    }

    @Override
    public void insert(TenantMembership membership) {
        membership = Objects.requireNonNull(membership, "成员资格不能为空");
        if (mapper.insert(
                membership.id().value(),
                membership.tenantId().value(),
                membership.subjectId().value(),
                membership.role().name(),
                membership.status().name(),
                membership.permissionVersion(),
                membership.version(),
                membership.createdAt()) != 1) {
            throw new IllegalStateException("成员资格写入失败");
        }
    }
}
