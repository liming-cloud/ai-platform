package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection.TenantCustomRoleRow;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.entity.TenantMembershipEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 自定义角色生命周期所需的锁、影响分析和原子写入 MyBatis 映射器。 */
public interface TenantCustomRoleLifecycleMapper {

    TenantCustomRoleRow lockActiveRole(
            @Param("tenantId") long tenantId,
            @Param("customRoleId") long customRoleId);

    List<String> findPermissionCodes(@Param("customRoleId") long customRoleId);

    List<TenantMembershipEntity> lockEnabledBoundMembers(
            @Param("tenantId") long tenantId,
            @Param("customRoleId") long customRoleId);

    long countEnabledAccessManagersAfterChange(
            @Param("tenantId") long tenantId,
            @Param("customRoleId") long customRoleId,
            @Param("retainsAccessManage") boolean retainsAccessManage);

    int updateRole(
            @Param("tenantId") long tenantId,
            @Param("customRoleId") long customRoleId,
            @Param("status") String status,
            @Param("version") long version,
            @Param("disabledAt") Instant disabledAt);

    int deletePermissions(@Param("customRoleId") long customRoleId);

    int insertPermission(
            @Param("customRoleId") long customRoleId,
            @Param("permissionCode") String permissionCode);

    int revisePermissionVersion(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("permissionVersion") long permissionVersion,
            @Param("version") long version);
}
