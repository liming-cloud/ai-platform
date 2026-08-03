package com.aiplatform.foundation.organizationaccess.domain.identity.repository;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import java.util.Optional;

/**
 * 读取可用于认证的全局主体聚合。
 */
@FunctionalInterface
public interface SubjectIdentityRepository {

    Optional<SubjectIdentity> findActiveByLogin(String loginName);
}
