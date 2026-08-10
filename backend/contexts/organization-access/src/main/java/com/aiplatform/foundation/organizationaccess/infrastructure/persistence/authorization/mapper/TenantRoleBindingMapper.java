package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.entity.TenantMembershipEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 租户成员角色绑定所需的受锁成员读取和原子写入 MyBatis 映射器。
 */
public interface TenantRoleBindingMapper {

    TenantMembershipEntity lockEnabled(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId);

    int countBindings(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("roleCode") String roleCode);

    int insertBinding(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("roleCode") String roleCode,
            @Param("boundAt") java.time.Instant boundAt);

    int revisePermissionVersion(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("permissionVersion") long permissionVersion,
            @Param("version") long version);
}
