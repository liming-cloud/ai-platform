package com.aiplatform.foundation.organizationaccess.domain.authorization.repository;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleAuthorization;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Optional;

/** 只按主体读取活动平台角色绑定派生的认证事实。 */
@FunctionalInterface
public interface PlatformRoleBindingRepository {

    Optional<PlatformRoleAuthorization> findActiveAuthorizationBySubject(SubjectId subjectId);
}
