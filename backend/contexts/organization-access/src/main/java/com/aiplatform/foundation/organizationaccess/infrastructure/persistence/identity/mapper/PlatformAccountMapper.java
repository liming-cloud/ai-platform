package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.entity.SubjectIdentityEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.entity.PlatformAccountSummaryRow;
import org.apache.ibatis.annotations.Param;

/** 平台账户治理的受锁读取和乐观更新映射器。 */
public interface PlatformAccountMapper {

    SubjectIdentityEntity lock(@Param("subjectId") long subjectId);

    boolean hasActiveAccountAdministratorRole(@Param("subjectId") long subjectId);

    long countOtherActiveAccountAdministrators(@Param("subjectId") long subjectId);

    int insert(
            @Param("subjectId") long subjectId,
            @Param("loginName") String loginName,
            @Param("passwordHash") String passwordHash,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdAt") java.time.Instant createdAt);

    int update(
            @Param("subjectId") long subjectId,
            @Param("passwordHash") String passwordHash,
            @Param("status") String status,
            @Param("version") long version,
            @Param("expectedVersion") long expectedVersion);

    java.util.List<PlatformAccountSummaryRow> list(
            @Param("offset") long offset, @Param("limit") long limit);

    long count();
}
