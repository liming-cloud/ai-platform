package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.entity.SubjectIdentityEntity;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 新租户建立所需的受锁主体读取与最小原子写入 MyBatis 映射器。
 */
public interface TenantProvisioningMapper {

    SubjectIdentityEntity lockActiveInitialAdministrator(@Param("subjectId") long subjectId);

    boolean hasActivePlatformAdministrator(@Param("subjectId") long subjectId);

    int insertTenant(
            @Param("tenantId") long tenantId,
            @Param("tenantCode") String tenantCode,
            @Param("displayName") String displayName,
            @Param("createdAt") Instant createdAt);

    int insertLifecycle(
            @Param("tenantId") long tenantId,
            @Param("tenantCode") String tenantCode,
            @Param("displayName") String displayName,
            @Param("initialAdministratorId") long initialAdministratorId,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt);

    int insertInitialAdministrator(
            @Param("membershipId") long membershipId,
            @Param("tenantId") long tenantId,
            @Param("subjectId") long subjectId,
            @Param("role") String role,
            @Param("status") String status,
            @Param("permissionVersion") long permissionVersion,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt);

    int insertInitialAdministratorRoleBinding(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("roleCode") String roleCode,
            @Param("boundAt") Instant boundAt);

    int insertOutbox(
            @Param("eventId") long eventId,
            @Param("tenantId") long tenantId,
            @Param("status") String status,
            @Param("version") long version,
            @Param("occurredAt") Instant occurredAt);
}
