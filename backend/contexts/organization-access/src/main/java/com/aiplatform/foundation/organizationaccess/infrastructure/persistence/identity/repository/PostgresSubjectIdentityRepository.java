package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus;
import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.SubjectIdentityMapper;
import java.util.Objects;
import java.util.Optional;

/**
 * 使用 PostgreSQL 读取可认证全局主体聚合。
 */
public final class PostgresSubjectIdentityRepository implements SubjectIdentityRepository {

    private final SubjectIdentityMapper mapper;

    public PostgresSubjectIdentityRepository(SubjectIdentityMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "主体映射器不能为空");
    }

    @Override
    public Optional<SubjectIdentity> findActiveByLogin(String loginName) {
        var row = mapper.findActiveByLogin(Objects.requireNonNull(loginName, "登录名不能为空"));
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(SubjectIdentity.reconstitute(
                new SubjectId(row.getSubjectId()),
                row.getLoginName(),
                new EncodedPassword(row.getPasswordHash()),
                SubjectIdentityStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getCreatedAt()));
    }
}
