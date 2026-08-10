package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authentication.projection.AuthenticationRow;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.entity.TenantMembershipEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 租户成员资格及其权限事实的 MyBatis-Plus 映射器。
 */
public interface TenantMembershipMapper extends BaseMapper<TenantMembershipEntity> {

    AuthenticationRow findEnabled(
            @Param("tenantCode") String tenantCode,
            @Param("subjectId") long subjectId);

    List<String> findPermissionCodes(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId);

    List<String> findBuiltInRoleCodes(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId);
}
