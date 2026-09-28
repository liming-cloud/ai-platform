package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantLoginAttemptEntity;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 租户登录失败观察的行锁和原子状态变更。 */
public interface TenantLoginAttemptMapper {
    Instant findLockedUntil(@Param("tenantId") long tenantId, @Param("subjectId") long subjectId);
    int ensureRow(@Param("tenantId") long tenantId, @Param("subjectId") long subjectId,
            @Param("now") Instant now);
    TenantLoginAttemptEntity lock(@Param("tenantId") long tenantId, @Param("subjectId") long subjectId);
    int replace(@Param("tenantId") long tenantId, @Param("subjectId") long subjectId,
            @Param("started") Instant started, @Param("failures") int failures,
            @Param("until") Instant until);
    int delete(@Param("tenantId") long tenantId, @Param("subjectId") long subjectId);
}
