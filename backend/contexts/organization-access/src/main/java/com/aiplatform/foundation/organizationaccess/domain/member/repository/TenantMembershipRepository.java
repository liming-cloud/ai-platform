package com.aiplatform.foundation.organizationaccess.domain.member.repository;

import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberAuthenticationProfile;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Optional;

/**
 * 按租户与已验证主体读取有效成员资格的仓储端口。
 */
@FunctionalInterface
public interface TenantMembershipRepository {

    Optional<MemberAuthenticationProfile> findEnabled(String tenantCode, SubjectId subjectId);
}
