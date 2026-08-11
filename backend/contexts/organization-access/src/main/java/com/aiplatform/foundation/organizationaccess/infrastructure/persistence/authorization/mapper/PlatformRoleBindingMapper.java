package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.entity.PlatformRoleBindingEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection.PlatformRoleAuthorizationRow;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.entity.SubjectIdentityEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 读取活动平台角色绑定认证事实的 MyBatis 映射器。 */
public interface PlatformRoleBindingMapper {

    List<PlatformRoleAuthorizationRow> findActiveAuthorizationBySubject(@Param("subjectId") long subjectId);

    SubjectIdentityEntity lockSubject(@Param("subjectId") long subjectId);

    boolean hasActiveBinding(
            @Param("subjectId") long subjectId,
            @Param("roleCode") String roleCode);

    PlatformRoleBindingEntity lockActiveBinding(
            @Param("subjectId") long subjectId,
            @Param("roleCode") String roleCode);

    long countOtherActiveAccountAdministrators(@Param("subjectId") long subjectId);

    int insertBinding(
            @Param("bindingId") long bindingId,
            @Param("subjectId") long subjectId,
            @Param("roleCode") String roleCode,
            @Param("createdAt") Instant createdAt);

    int revokeBinding(
            @Param("bindingId") long bindingId,
            @Param("version") long version,
            @Param("expectedVersion") long expectedVersion,
            @Param("revokedAt") Instant revokedAt);

    int advanceAuthorizationRevision(@Param("subjectId") long subjectId);

    int establishAuthorizationRevision(@Param("subjectId") long subjectId);
}
