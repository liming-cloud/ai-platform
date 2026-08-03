package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PlatformAccessGrant;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformAccessGrantRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformAccessGrantMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection.PlatformAccessGrantRow;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 使用 PostgreSQL 读取有效平台租户治理授权。
 */
public final class PostgresPlatformAccessGrantRepository implements PlatformAccessGrantRepository {

    private final PlatformAccessGrantMapper mapper;

    public PostgresPlatformAccessGrantRepository(PlatformAccessGrantMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "平台授权映射器不能为空");
    }

    @Override
    public Optional<PlatformAccessGrant> findActiveBySubject(SubjectId subjectId) {
        var row = mapper.findActiveBySubject(
                Objects.requireNonNull(subjectId, "主体标识不能为空").value());
        if (row.isEmpty()) {
            return Optional.empty();
        }
        var first = row.getFirst();
        row.forEach(value -> ensureSameGrant(first, value));
        var permissions = row.stream()
                .map(PlatformAccessGrantRow::getPermissionCode)
                .map(PermissionCode::new)
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(new PlatformAccessGrant(
                new SubjectId(first.getSubjectId()), first.getPermissionVersion(), permissions));
    }

    private static void ensureSameGrant(
            PlatformAccessGrantRow expected, PlatformAccessGrantRow actual) {
        if (actual == null
                || !Objects.equals(expected.getSubjectId(), actual.getSubjectId())
                || !Objects.equals(expected.getPermissionVersion(), actual.getPermissionVersion())) {
            throw new IllegalStateException("平台授权投影不一致");
        }
    }
}
