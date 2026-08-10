package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.entity.TenantMembershipEntity;
import org.apache.ibatis.annotations.Param;

/** 成员状态变更所需的受锁成员事实、有效权限计数和原子更新映射器。 */
public interface TenantMemberStatusMapper {

    /** 锁定当前租户中的成员资格，无论其当前是否启用。 */
    TenantMembershipEntity lock(@Param("tenantId") long tenantId, @Param("membershipId") long membershipId);

    /** 判断成员的全部有效角色和遗留直授权限是否包含访问管理权限。 */
    int countAccessManage(@Param("tenantId") long tenantId, @Param("membershipId") long membershipId);

    /** 统计排除目标后仍启用且拥有访问管理权限的成员数量。 */
    long countOtherEnabledAccessManagers(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId);

    /** 以旧乐观版本条件更新成员状态和权限修订。 */
    int update(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("status") String status,
            @Param("permissionVersion") long permissionVersion,
            @Param("version") long version,
            @Param("expectedVersion") long expectedVersion);
}
