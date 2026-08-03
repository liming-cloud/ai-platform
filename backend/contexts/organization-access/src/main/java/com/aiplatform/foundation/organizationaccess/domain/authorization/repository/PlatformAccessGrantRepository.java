package com.aiplatform.foundation.organizationaccess.domain.authorization.repository;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PlatformAccessGrant;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Optional;

/**
 * 按已验证主体读取有效平台租户治理授权。
 */
@FunctionalInterface
public interface PlatformAccessGrantRepository {

    Optional<PlatformAccessGrant> findActiveBySubject(SubjectId subjectId);
}
