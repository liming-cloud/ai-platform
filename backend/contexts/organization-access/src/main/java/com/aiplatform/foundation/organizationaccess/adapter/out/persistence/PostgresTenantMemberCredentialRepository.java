package com.aiplatform.foundation.organizationaccess.adapter.out.persistence;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.application.authentication.AuthenticatedMember;
import com.aiplatform.foundation.organizationaccess.application.port.out.TenantMemberCredentialRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
    public Optional<AuthenticatedMember> findEnabled(String tenantCode, String loginName) {
        var row = mapper.findEnabled(
                Objects.requireNonNull(tenantCode, "租户编码不能为空"),
                Objects.requireNonNull(loginName, "登录名不能为空"));
        if (row == null) {
            return Optional.empty();
        }
        var tenantId = UUID.fromString(row.getTenantId());
        var membershipId = row.getMembershipId();
        var permissions = mapper
                .findPermissionCodes(tenantId, membershipId)
                .stream()
                .map(PermissionCode::new)
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(new AuthenticatedMember(
                new TenantId(tenantId),
                new SubjectId(row.getSubjectId()),
                new MembershipId(membershipId),
                row.getPasswordHash(),
                row.getPermissionVersion(),
                permissions));
    }
}
