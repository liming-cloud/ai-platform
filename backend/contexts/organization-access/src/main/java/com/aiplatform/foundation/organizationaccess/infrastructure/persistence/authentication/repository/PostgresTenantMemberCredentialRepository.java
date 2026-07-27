package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authentication.repository;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionGrantSet;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberAuthenticationProfile;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMemberCredentialRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authentication.mapper.TenantMemberCredentialMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 使用显式租户条件读取 PostgreSQL 成员事实。
 */
public final class PostgresTenantMemberCredentialRepository
        implements TenantMemberCredentialRepository {

    private final TenantMemberCredentialMapper mapper;

    public PostgresTenantMemberCredentialRepository(TenantMemberCredentialMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "成员映射器不能为空");
    }

    @Override
    public Optional<MemberAuthenticationProfile> findEnabled(
            String tenantCode,
            String loginName) {
        var row = mapper.findEnabled(
                Objects.requireNonNull(tenantCode, "租户编码不能为空"),
                Objects.requireNonNull(loginName, "登录名不能为空"));
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
                new EncodedPassword(row.getPasswordHash()),
                new PermissionGrantSet(row.getPermissionVersion(), permissions)));
    }
}
