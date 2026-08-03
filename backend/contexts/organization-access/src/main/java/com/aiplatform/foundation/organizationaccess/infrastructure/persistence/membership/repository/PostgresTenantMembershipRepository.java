package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.repository;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionGrantSet;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberAuthenticationProfile;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMembershipRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 使用显式租户与主体条件读取 PostgreSQL 成员资格事实。
 */
public final class PostgresTenantMembershipRepository implements TenantMembershipRepository {

    private final TenantMembershipMapper mapper;

    public PostgresTenantMembershipRepository(TenantMembershipMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "成员资格映射器不能为空");
    }

    @Override
    public Optional<MemberAuthenticationProfile> findEnabled(
            String tenantCode,
            SubjectId subjectId) {
        var row = mapper.findEnabled(
                Objects.requireNonNull(tenantCode, "租户编码不能为空"),
                Objects.requireNonNull(subjectId, "主体标识不能为空").value());
        if (row == null) {
            return Optional.empty();
        }
        var tenantId = row.getTenantId();
        var membershipId = row.getMembershipId();
        var permissions = mapper
                .findPermissionCodes(tenantId, membershipId)
                .stream()
                .map(PermissionCode::new)
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(new MemberAuthenticationProfile(
                new TenantId(tenantId),
                new SubjectId(row.getSubjectId()),
                new MemberId(membershipId),
                new PermissionGrantSet(row.getPermissionVersion(), permissions)));
    }
}
