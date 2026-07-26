package com.aiplatform.foundation.organizationaccess.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 可认证成员事实的 MyBatis-Plus 映射器。
 */
public interface TenantMemberCredentialMapper extends BaseMapper<MembershipCredentialEntity> {

    @Select("""
            SELECT
                membership.tenant_id::text AS tenant_id,
                membership.subject_id::text AS subject_id,
                membership.membership_id::text AS membership_id,
                membership.password_hash,
                membership.permission_version
            FROM organization_access.tenant tenant
            INNER JOIN organization_access.membership_credential membership
                ON membership.tenant_id = tenant.tenant_id
            WHERE tenant.tenant_code = #{tenantCode}
              AND tenant.status = 'ENABLED'
              AND membership.login_name = #{loginName}
              AND membership.status = 'ENABLED'
            """)
    @Results({
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "subject_id", property = "subjectId"),
        @Result(column = "membership_id", property = "membershipId"),
        @Result(column = "password_hash", property = "passwordHash"),
        @Result(column = "permission_version", property = "permissionVersion")
    })
    AuthenticationRow findEnabled(
            @Param("tenantCode") String tenantCode,
            @Param("loginName") String loginName);

    @Select("""
            SELECT permission_code
            FROM organization_access.membership_permission
            WHERE tenant_id = #{tenantId}
              AND membership_id = #{membershipId}
            ORDER BY permission_code
            """)
    List<String> findPermissionCodes(
            @Param("tenantId") UUID tenantId,
            @Param("membershipId") UUID membershipId);
}
