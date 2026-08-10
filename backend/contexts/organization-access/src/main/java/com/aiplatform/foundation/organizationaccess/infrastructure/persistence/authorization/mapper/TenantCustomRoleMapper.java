package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper;

import org.apache.ibatis.annotations.Param;

/** 租户自定义角色聚合根及其权限集合的 MyBatis 原子写入映射器。 */
public interface TenantCustomRoleMapper {

    int countActiveName(
            @Param("tenantId") long tenantId,
            @Param("name") String name);

    int insertRole(
            @Param("roleId") long roleId,
            @Param("tenantId") long tenantId,
            @Param("name") String name,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdAt") java.time.Instant createdAt,
            @Param("disabledAt") java.time.Instant disabledAt);

    int insertPermission(
            @Param("roleId") long roleId,
            @Param("permissionCode") String permissionCode);
}
