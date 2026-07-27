package com.aiplatform.foundation.organizationaccess.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;

/**
 * 可认证成员事实的 MyBatis-Plus 映射器。
 */
public interface TenantMemberCredentialMapper extends BaseMapper<MembershipCredentialEntity> {

    AuthenticationRow findEnabled(
            @Param("tenantCode") String tenantCode,
            @Param("loginName") String loginName);

    List<String> findPermissionCodes(
            @Param("tenantId") UUID tenantId,
            @Param("membershipId") UUID membershipId);
}
