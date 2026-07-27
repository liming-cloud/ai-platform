package com.aiplatform.foundation.organizationaccess.domain.member.repository;

import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberAuthenticationProfile;
import java.util.Optional;

/**
 * 按租户和登录名读取可认证成员事实的仓储端口。
 */
@FunctionalInterface
public interface TenantMemberCredentialRepository {

    Optional<MemberAuthenticationProfile> findEnabled(
            String tenantCode,
            String loginName);
}
