package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Optional;

/** 平台固定角色绑定写入所需的受锁账户与认证修订端口。 */
public interface PlatformRoleBindingStore {

    Optional<SubjectIdentity> lock(SubjectId subjectId);

    boolean hasActiveBinding(SubjectId subjectId, PlatformRole role);

    Optional<PlatformRoleBinding> lockActiveBinding(SubjectId subjectId, PlatformRole role);

    long countOtherActiveAccountAdministrators(SubjectId subjectId);

    void bind(PlatformRoleBinding binding);

    void revoke(PlatformRoleBinding binding);
}
