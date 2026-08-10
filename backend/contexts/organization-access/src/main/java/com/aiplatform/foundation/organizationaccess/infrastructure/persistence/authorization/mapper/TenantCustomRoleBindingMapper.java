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

    /** 删除当前租户成员的一项自定义角色绑定。 */
    int deleteBinding(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("customRoleId") long customRoleId);

    /** 返回删除指定绑定后目标成员是否仍拥有有效 `access:manage`。 */
    int countAccessManageAfterRevocation(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("revokedCustomRoleId") long revokedCustomRoleId);

    /** 返回同租户内除目标外当前拥有有效 `access:manage` 的启用成员数量。 */
    long countOtherEnabledAccessManagers(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId);
}
