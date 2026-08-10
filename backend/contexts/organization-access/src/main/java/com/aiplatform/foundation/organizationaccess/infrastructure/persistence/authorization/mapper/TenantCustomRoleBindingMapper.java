package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection.TenantCustomRoleRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 自定义角色绑定所需的受锁角色事实和绑定写入 MyBatis 映射器。 */
public interface TenantCustomRoleBindingMapper {

    /** 锁定当前租户内仍可绑定的自定义角色。 */
    TenantCustomRoleRow lockActiveRole(
            @Param("tenantId") long tenantId,
            @Param("customRoleId") long customRoleId);

    /** 读取受锁角色的已发布权限集合。 */
    List<String> findPermissionCodes(@Param("customRoleId") long customRoleId);

    /** 判断成员是否已经拥有该自定义角色绑定。 */
    int countBindings(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("customRoleId") long customRoleId);

    /** 写入不可变自定义角色绑定事实。 */
    int insertBinding(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("customRoleId") long customRoleId,
            @Param("boundAt") Instant boundAt);
}
