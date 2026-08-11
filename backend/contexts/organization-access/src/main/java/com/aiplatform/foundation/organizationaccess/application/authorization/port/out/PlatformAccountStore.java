package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Optional;

/** 平台账户治理所需的锁定读取与乐观写入端口。 */
public interface PlatformAccountStore extends PlatformAccountDirectory {

    Optional<SubjectIdentity> lock(SubjectId subjectId);

    boolean hasActiveAccountAdministratorRole(SubjectId subjectId);

    long countOtherActiveAccountAdministrators(SubjectId subjectId);

    /**
     * 插入全局账户事实。
     *
     * @return 当且仅当数据库全局登录名唯一约束允许本次写入时为真。
     */
    boolean insert(SubjectIdentity identity);

    void update(SubjectIdentity identity);
}
