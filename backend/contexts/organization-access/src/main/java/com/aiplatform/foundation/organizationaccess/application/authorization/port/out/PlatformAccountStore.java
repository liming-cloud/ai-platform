package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Optional;

/** 平台账户治理所需的锁定读取与乐观写入端口。 */
public interface PlatformAccountStore extends PlatformAccountDirectory {

    Optional<SubjectIdentity> lock(SubjectId subjectId);

    boolean hasActiveAccountAdministratorRole(SubjectId subjectId);

    long countOtherActiveAccountAdministrators(SubjectId subjectId);

    void update(SubjectIdentity identity);
}
