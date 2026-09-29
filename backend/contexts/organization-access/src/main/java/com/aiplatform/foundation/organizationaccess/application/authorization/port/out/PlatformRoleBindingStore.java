package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Optional;

/** 平台固定角色绑定写入所需的受锁账户与认证修订端口。 */
public interface PlatformRoleBindingStore {

    /** 撤销平台管理员资格前使用与账户停用相同的事务门禁。 */
    void lockAdministratorDisableGate();

    Optional<SubjectIdentity> lock(SubjectId subjectId);

    boolean hasActiveBinding(SubjectId subjectId, PlatformRole role);

    /** 返回受锁主体当前启用的租户成员资格数量。 */
    long countEnabledTenantMemberships(SubjectId subjectId);

    Optional<PlatformRoleBinding> lockActiveBinding(SubjectId subjectId, PlatformRole role);

    long countOtherActiveAccountAdministrators(SubjectId subjectId);

    void bind(PlatformRoleBinding binding);

    void revoke(PlatformRoleBinding binding);
}
