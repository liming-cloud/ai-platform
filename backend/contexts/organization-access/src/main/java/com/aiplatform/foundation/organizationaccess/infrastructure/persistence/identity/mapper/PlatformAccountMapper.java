package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.entity.SubjectIdentityEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.entity.PlatformAccountSummaryRow;
import com.baomidou.mybatisplus.core.metadata.IPage;
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

    /** 分页投影账户安全摘要；总数与 SQL 分页由框架在相同查询范围内生成。 */
    IPage<PlatformAccountSummaryRow> list(IPage<PlatformAccountSummaryRow> page);
}
