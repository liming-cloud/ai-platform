package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleAuthorization;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformRoleBindingRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformRoleBindingMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection.PlatformRoleAuthorizationRow;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** 使用 PostgreSQL 从活动平台角色绑定派生认证权限。 */
public final class PostgresPlatformRoleBindingRepository implements PlatformRoleBindingRepository {

    private final PlatformRoleBindingMapper mapper;

    public PostgresPlatformRoleBindingRepository(PlatformRoleBindingMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "平台角色绑定映射器不能为空");
    }

    @Override
    public Optional<PlatformRoleAuthorization> findActiveAuthorizationBySubject(SubjectId subjectId) {
        var rows = mapper.findActiveAuthorizationBySubject(
                Objects.requireNonNull(subjectId, "主体标识不能为空").value());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var first = rows.getFirst();
        rows.forEach(row -> ensureSameAuthorization(first, row));
        var roles = rows.stream()
                .map(PlatformRoleAuthorizationRow::getRoleCode)
                .map(PlatformRole::from)
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(new PlatformRoleAuthorization(
                new SubjectId(first.getSubjectId()), first.getPermissionVersion(), roles));
    }

    private static void ensureSameAuthorization(
            PlatformRoleAuthorizationRow expected, PlatformRoleAuthorizationRow actual) {
        if (actual == null
                || !Objects.equals(expected.getSubjectId(), actual.getSubjectId())
                || !Objects.equals(expected.getPermissionVersion(), actual.getPermissionVersion())) {
            throw new IllegalStateException("平台角色认证投影不一致");
        }
    }
}
