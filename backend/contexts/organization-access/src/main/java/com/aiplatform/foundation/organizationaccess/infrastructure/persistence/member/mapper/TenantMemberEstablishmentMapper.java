package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 成员建立所需的受锁主体事实、唯一性检查与原子写入映射器。 */
public interface TenantMemberEstablishmentMapper {
    /** 锁定仍启用的全局主体；不读取其凭据摘要。 */
    Long lockActiveSubject(@Param("subjectId") long subjectId);
    /** 判断主体是否仍拥有活动平台管理员绑定。 */
    boolean hasActivePlatformAdministrator(@Param("subjectId") long subjectId);
    /** 检查主体是否已属于当前租户。 */
    int countMembership(@Param("tenantId") long tenantId, @Param("subjectId") long subjectId);
    /** 写入普通用户成员资格。 */
    int insert(@Param("membershipId") long membershipId, @Param("tenantId") long tenantId,
               @Param("subjectId") long subjectId, @Param("membershipRole") String membershipRole,
               @Param("status") String status, @Param("permissionVersion") long permissionVersion,
               @Param("version") long version, @Param("createdAt") Instant createdAt);
    /** 写入与普通用户成员资格一致的固定角色绑定。 */
    int insertDefaultRole(@Param("tenantId") long tenantId,
                          @Param("membershipId") long membershipId,
                          @Param("boundAt") Instant boundAt);
}
